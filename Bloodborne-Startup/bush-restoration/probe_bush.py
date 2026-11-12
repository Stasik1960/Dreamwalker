from collections import Counter
import json
import os
from pathlib import Path
import struct
import sys
from zipfile import ZipFile

SCRIPT_DIR = Path(__file__).resolve().parent
BASE = Path(os.environ.get('DREAMWALKER_BUSH_WORKDIR', SCRIPT_DIR)).resolve()
REPO = SCRIPT_DIR.parents[1] if SCRIPT_DIR.parent.name == 'Bloodborne-Startup' else SCRIPT_DIR
MAP_TOOLS = REPO / 'DW-City-Visuals/tools'
sys.path.insert(0, str(MAP_TOOLS / 'map_io'))
from world_io import RegionFile, compound, block_state_key, section_blocks

JAR = BASE / 'DreamWalker-v22-Seam-Repair-Compact-Startup.jar'
NATIVE = 'assets/bloodborne_dw/native_grid/'


def visual_entries(root):
    visual = root.get('DreamwalkerVisuals')
    if visual:
        for row in compound(visual)['Sections'].value:
            fields = compound(row)
            yield fields['Y'].value, {
                value & 4095: (value & 0xffffffff) >> 12
                for value in fields['Entries'].value
            }


def show_chunk(region, catalog, raw, x=101, z=-748):
    stored = region.get_chunk((x//16)%32, (z//16)%32)
    if not stored:
        print('Missing chunk')
        return
    root = compound(stored.nbt().root)
    sections = {compound(s)['Y'].value: section_blocks(s) for s in root['sections'].value}
    visuals = dict(visual_entries(root))
    print('chunk keys',list(root))
    for y in range(116, 122):
        for dz in (-1, 0, 1):
            for dx in (-1, 0, 1):
                if (x+dx)//16 != x//16 or (z+dz)//16 != z//16:
                    continue
                idx = ((y&15)<<8)|(((z+dz)&15)<<4)|((x+dx)&15)
                section = sections.get(y//16)
                if not section:
                    continue
                palette, indices = section
                state = block_state_key(palette[indices[idx]])
                cell_id = visuals.get(y//16, {}).get(idx)
                if cell_id is None:
                    print((x+dx,y,z+dz),state)
                    continue
                cell = catalog['cells'][cell_id]
                print((x+dx,y,z+dz),state,cell_id,cell['mesh'],cell['baseState'],raw[cell['mesh']])


def material_counts(jar, catalog):
    result = []
    with jar.open(NATIVE+'meshes.bin') as meshes:
        meshes.read(8)
        for mesh in catalog['meshes']:
            count, = struct.unpack('>I', meshes.read(4))
            assert count == mesh['quadCount']
            buf = meshes.read(139*count)
            result.append(Counter(catalog['materials'][material]['sprite'] for material in
                                  (struct.unpack_from('>I', buf, offset)[0]
                                   for offset in range(0, len(buf), 139))))
    return result


if __name__ == '__main__':
    with ZipFile(JAR) as jar:
        catalog = json.loads(jar.read(NATIVE+'catalog.json'))
        materials = material_counts(jar, catalog)
        for file in ('Bloodborne-v22-Compact-Startup-world.zip',
                     'bloodborne-with-bloodborne_old-20261027.zip',
                     'City-Followup-world.zip'):
            with ZipFile(BASE/file) as world:
                for region in world.namelist():
                    if region.endswith('/region/r.0.-2.mca') and (
                        'dimensions/' in region or region == 'bloodborne/region/r.0.-2.mca'
                    ):
                        print(file, region, flush=True)
                        show_chunk(RegionFile(world.read(region)), catalog, materials)
