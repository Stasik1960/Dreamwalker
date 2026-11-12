"""Join source bush ownership to native fragment data and saved block states."""
from collections import Counter, defaultdict
from hashlib import sha256
import json
from pathlib import Path
import re
from zipfile import ZipFile

from probe_bush import BASE, JAR, NATIVE, material_counts, visual_entries
from world_io import RegionFile, block_state_key, compound, decode_nbt, section_blocks, _plain

OLD = BASE/'worlds/pre-conversion/bloodborne-blocks-fixed-2'
CURRENT = BASE/'worlds/compact-startup/bloodborne'
GRASS = re.compile(r'bloodborne_dw:source_addon_grass_[0-7]$')


def xyz(fields):
    return tuple(fields[k].value for k in 'xyz')


def read_old():
    roots = {}
    cells = defaultdict(dict)
    for path in sorted((OLD/'region').glob('r.*.*.mca')):
        for stored in RegionFile.open(path).chunks():
            raw = stored.raw_nbt()
            if b'bloodborne_dw:source_addon_grass_' not in raw:
                continue
            data = decode_nbt(raw)
            root = compound(data.root)
            for entity in root['block_entities'].value:
                fields = compound(entity)
                packed = fields.get('OwnerDataV1')
                if not packed or b'bloodborne_dw:source_addon_grass_' not in packed.value:
                    continue
                tags = compound(decode_nbt(packed.value).root)
                members = tags.get('owners')
                if not members:
                    continue
                pos = xyz(fields)
                for member in members.value:
                    member_data = compound(member)
                    owner = compound(member_data['owner'])
                    owner_id = owner['id'].value
                    if not GRASS.fullmatch(owner_id):
                        continue
                    origin = tuple(owner['root'].value)
                    root_data = member_data.get('rootData')
                    if root_data:
                        fields_data = compound(root_data)
                        props = _plain(fields_data['properties'])
                        payload = _plain(decode_nbt(fields_data['payload'].value).root)
                        owner_data = {'id': owner_id, 'properties': props, 'payload': payload}
                        assert roots.setdefault(origin, owner_data) == owner_data
                    cells[origin][pos] = len(members.value)
    print('Missing root data',len(cells.keys()-roots.keys()),flush=True)
    return roots, cells


def read_current(wanted, grass_cells):
    found = {}
    bush_visuals = {}
    bindings = Counter()
    for path in sorted((CURRENT/'region').glob('r.*.*.mca')):
        for stored in RegionFile.open(path).chunks():
            root = compound(stored.nbt().root)
            cx, cz = root['xPos'].value, root['zPos'].value
            wanted_here = wanted.get((cx, cz), set())
            visuals = dict(visual_entries(root))
            if visuals:
                bindings[compound(root['DreamwalkerVisuals'])['Catalog'].value] += 1
            hit = { (cx*16+(idx&15), sy*16+(idx>>8), cz*16+((idx>>4)&15)): value
                    for sy, row in visuals.items() for idx, value in row.items()
                    if value in grass_cells}
            if not wanted_here and not hit:
                continue
            bush_visuals.update(hit)
            entities = {xyz(compound(tag)): compound(tag)['id'].value
                        for tag in root['block_entities'].value}
            sections = {compound(s)['Y'].value: s for s in root['sections'].value}
            decoded = {}
            for pos in sorted(wanted_here | hit.keys()):
                x, y, z = pos
                sy = y//16
                index = ((y&15)<<8)|((z&15)<<4)|(x&15)
                if sy in sections and sy not in decoded:
                    decoded[sy] = section_blocks(sections[sy])
                if decoded.get(sy):
                    palette, indices = decoded[sy]
                    state = block_state_key(palette[indices[index]])
                else:
                    state = 'minecraft:air'
                found[pos] = [state, visuals.get(sy, {}).get(index), entities.get(pos)]
    return found, bush_visuals, bindings


def main():
    with ZipFile(JAR) as jar:
        raw_catalog = jar.read(NATIVE+'catalog.json')
        catalog = json.loads(raw_catalog)
        materials = material_counts(jar, catalog)
    grass_cells = {
        i: {'mesh': cell['mesh'], 'materials': dict(materials[cell['mesh']]), 'base': cell['baseState'],
            'outline': cell['outline'], 'collision': cell['collision'], 'fluid': cell['fluid']}
        for i, cell in enumerate(catalog['cells'])
        if any(name.endswith('/spirelamp_0046') for name in materials[cell['mesh']])
    }
    roots, cells = read_old()
    print('Old roots', len(roots), Counter(row['id'] for row in roots.values()), flush=True)
    wanted = defaultdict(set)
    for origin, positions in cells.items():
        for pos in {*positions, origin}:
            x, y, z = pos
            wanted[x//16, z//16].add(pos)
    found, visuals, bindings = read_current(wanted, grass_cells)
    out = BASE/'grass-audit.json'
    with out.open('w', encoding='utf-8') as stream:
        json.dump({'catalog_hash': sha256(raw_catalog).hexdigest(), 'bindings': dict(bindings),
                   'old': [[list(o), roots[o], [[list(p), n] for p,n in sorted(cells[o].items())]]
                           for o in sorted(roots)],
                   'found': [[list(p), *data] for p,data in sorted(found.items())],
                   'visuals': [[list(p), i] for p,i in sorted(visuals.items())],
                   'grass_cells': grass_cells}, stream, indent=1)
    print('Current cells with sprite', len(visuals),
          'inside historic footprint', sum(p in wanted[p[0]//16,p[2]//16] for p in visuals), flush=True)


if __name__ == '__main__':
    main()
