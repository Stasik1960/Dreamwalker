"""Read-only check of the shipped production JAR against current source resources."""
from __future__ import annotations

import argparse
import hashlib
import json
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RESOURCES = ROOT / 'src/main/resources'


def verify(path: Path) -> dict:
    with zipfile.ZipFile(path) as jar:
        names = jar.namelist()
        assert len(names) == len(set(names)), 'duplicate ZIP entries'
        assert jar.testzip() is None, 'JAR CRC failure'
        source = {p.relative_to(RESOURCES).as_posix(): p for p in RESOURCES.rglob('*') if p.is_file()}
        for name, file in source.items():
            if name == 'bloodborne_blocks.mixins.json':
                # Loom adds the remapped refmap; all authored settings must survive.
                built = json.loads(jar.read(name))
                assert built.pop('refmap') == 'bloodborne-blocks-refmap.json'
                assert built == json.loads(file.read_bytes()), 'changed authored mixin settings'
            else:
                assert jar.read(name) == file.read_bytes(), f'stale or missing resource: {name}'
        managed = ('assets/', 'data/', 'bloodborne_blocks/')
        extras = sorted(n for n in names if not n.endswith('/') and n.startswith(managed) and n not in source)
        assert not extras, f'obsolete packaged resources: {extras[:5]}'
        manifest = json.loads(jar.read('bloodborne_blocks/logical/production-palette.json'))
        production = {row['id'] for row in manifest['objects']}
        actual = {Path(n).stem for n in names if n.startswith('assets/bloodborne_blocks/blockstates/') and n.endswith('.json')}
        city={row['id'] for row in json.loads(jar.read('bloodborne_blocks/city/definitions.json'))['blocks']}
        assert actual == production | city | {'architecture_part'}, 'blockstate registry mismatch'
        prefix = 'dev/dreamwalker/bloodborneblocks/'
        for obsolete in ('PaletteAliases', 'LogicalItemMigration', 'LegacyItemSections', 'PaletteMigration'):
            assert prefix + obsolete + '.class' not in names, f'obsolete compatibility class: {obsolete}'
        for main in ('BloodborneBlocks', 'BloodborneClient', 'ArchitectureBlock', 'ArchitecturePartBlock', 'LogicalContractV2'):
            assert prefix + main + '.class' in names, f'missing runtime class: {main}'
        mixins = json.loads(jar.read('bloodborne_blocks.mixins.json'))
        refmap = mixins.get('refmap')
        assert refmap and refmap in names, 'missing remapped mixin refmap'
        assert not any('GameTests.class' in n for n in names), 'test classes leaked into mod'
        metadata = json.loads(jar.read('fabric.mod.json'))
        assert metadata['depends']['minecraft'] == '1.20.1'
        return {
            'result': 'PASS', 'artifact': path.name, 'bytes': path.stat().st_size,
            'sha256': hashlib.sha256(path.read_bytes()).hexdigest(),
            'source_resources_byte_compared': len(source) - 1,
            'loom_mixin_metadata_semantically_compared': 1, 'production_ids': len(production),
            'registry_with_helper': len(actual), 'obsolete_packaged_resources': len(extras),
            'refmap': refmap, 'crc': 'PASS',
            'scope': 'archive integrity and exact resource closure; not client visual acceptance',
        }


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('jar', type=Path)
    parser.add_argument('--report', type=Path)
    args = parser.parse_args()
    result = verify(args.jar)
    if args.report:
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(json.dumps(result, indent=2) + '\n', encoding='utf8')
    print(json.dumps(result))
