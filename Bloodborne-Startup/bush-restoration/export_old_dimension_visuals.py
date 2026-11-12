"""Export positioned native visuals with the atlas sprite used by source bushes."""
import json
from zipfile import ZipFile

from audit_grass import CURRENT
from probe_bush import BASE, JAR, NATIVE, material_counts, visual_entries
from world_io import RegionFile, compound


def main():
    with ZipFile(JAR) as jar:
        catalog = json.loads(jar.read(NATIVE + 'catalog.json'))
        materials = material_counts(jar, catalog)
    candidate_meshes = {i for i, names in enumerate(materials)
                        if any(name.endswith('/spirelamp_0046') for name in names)}
    candidates = {i for i, row in enumerate(catalog['cells']) if row['mesh'] in candidate_meshes}
    dimension = CURRENT / 'dimensions/bloodborne_dw/bloodborne_old/region'
    rows = []
    for path in sorted(dimension.glob('r.*.*.mca')):
        for stored in RegionFile.open(path).chunks():
            chunk = compound(stored.nbt().root)
            x, z = (chunk[k].value * 16 for k in ('xPos', 'zPos'))
            for sy, entries in visual_entries(chunk):
                for idx, value in entries.items():
                    if value in candidates:
                        rows.append([(x + (idx & 15), sy * 16 + (idx >> 8), z + ((idx >> 4) & 15)), value])
    (BASE / 'old-dimension-visuals.json').write_text(json.dumps(rows), encoding='utf-8')
    print('Positioned atlas candidates:', len(rows), flush=True)


if __name__ == '__main__':
    main()
