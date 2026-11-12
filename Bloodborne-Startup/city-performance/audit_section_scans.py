"""Count saved sections for which the patched orphan scan can skip cell iteration."""

import argparse
import json
from collections import Counter
from pathlib import Path
import sys
from zipfile import ZipFile

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / 'bloodborne complete' / 'tools'))
from world_io import RegionFile, compound


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('jar', type=Path)
    parser.add_argument('world', type=Path)
    args = parser.parse_args()
    with ZipFile(args.jar) as jar:
        recipes = json.loads(jar.read('bloodborne_dw/static-architecture-v1274.json'))['recipes']
        materials = json.loads(jar.read('bloodborne_dw/restored-material-slabs.json'))['materials']
    slabs = {row['id']: row['overlayRecipe'] for row in materials if row.get('overlayRecipe', -1) >= 0}
    singleton = {i for i, row in enumerate(recipes) if row['resident'] == 0 and len(row['fragments']) == 1
                 and row['fragments'][0]['origin'] == [0, 0, 0]}

    for path in (args.world, args.world / 'dimensions/bloodborne_dw/bloodborne_old'):
        counts = Counter()
        for region in sorted((path / 'region').glob('r.*.*.mca')):
            for chunk in RegionFile.open(region).chunks():
                for section in compound(chunk.nbt().root)['sections'].value:
                    block_states = compound(section).get('block_states')
                    if block_states is None:
                        continue
                    indices = set()
                    for state in compound(block_states)['palette'].value:
                        fields = compound(state)
                        name = fields['Name'].value
                        if name.startswith('bloodborne_dw:static_architecture_'):
                            bank = int(name.rsplit('_', 1)[1])
                            part = int(compound(fields['Properties'])['part'].value)
                            indices.add(bank * 128 + part)
                        elif name in slabs:
                            indices.add(slabs[name])
                    if indices:
                        counts['scanned_before'] += 1
                        counts['skipped_after'] += indices <= singleton
        print(path.name, dict(counts), flush=True)


if __name__ == '__main__':
    main()
