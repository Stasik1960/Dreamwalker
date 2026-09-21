"""Compare normalized resources with an explicit git-archive baseline (no world writes)."""
import argparse
import gzip
import json
import zipfile
from pathlib import Path


def verify(baseline, current):
    with zipfile.ZipFile(baseline) as archive:
        suffix = 'bloodborne_blocks/logical/definitions.json'
        names = [name for name in archive.namelist() if name.endswith(suffix)]
        if len(names) != 1:
            raise ValueError('Baseline must contain exactly one logical definition set')
        prefix = names[0].removesuffix('definitions.json')
        def old(name):
            data = archive.read(prefix + name)
            return json.loads(gzip.decompress(data) if name.endswith('.gz') else data)
        old_defs = old('definitions.json')['blocks']
        old_geometry, old_meshes, old_rules = old('geometry.json'), old('meshes.json.gz'), old('migration.json')['rules']
    def new(name):
        data = (current / name).read_bytes()
        return json.loads(gzip.decompress(data) if name.endswith('.gz') else data)
    new_defs = {row['id']: row for row in new('definitions.json')['blocks']}
    new_geometry, new_meshes, new_rules = new('geometry.json'), new('meshes.json.gz'), new('migration.json')['rules']
    def state_key(values):
        return ','.join(k + '=' + v for k, v in sorted(values.items()))
    def state_values(key):
        return dict(piece.split('=', 1) for piece in key.split(',') if piece)
    def profile(data, ident, key):
        value = data['blocks'][ident]['states'][key]
        return data['profiles'][value['ref']] if 'ref' in value else value
    def require(condition, message):
        if not condition:
            raise ValueError(message)
    checked = 0
    for before in old_defs:
        ident = before['id']
        require(ident in new_defs, 'Removed logical registry: ' + ident)
        after = new_defs[ident]
        for key, mesh in before['models'].items():
            target = state_key({**after['default'], **state_values(key)})
            require(target in after['models'], 'Removed old state: ' + ident + '[' + key + ']')
            require(old_meshes[mesh] == new_meshes[after['models'][target]], 'Changed old artwork: ' + ident + '[' + key + ']')
            require(profile(old_geometry, ident, key) == profile(new_geometry, ident, target), 'Changed old geometry: ' + ident + '[' + key + ']')
            require(before['states'][key] == after['states'][target], 'Changed state metadata: ' + ident)
            checked += 1
    fingerprints = set()
    for rule in new_rules:
        fingerprints.add(json.dumps(rule, sort_keys=True))
    for rule in old_rules:
        ident = rule['target']['id']
        normalized = {**rule, 'target': {**rule['target'], 'properties': {**new_defs[ident]['default'], **rule['target']['properties']}}}
        require(json.dumps(normalized, sort_keys=True) in fingerprints, 'Removed or changed old migration rule: ' + json.dumps(rule['source']))
    return {'preservedLogicalObjects': len(old_defs), 'preservedStates': checked, 'preservedRules': len(old_rules)}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('baseline', type=Path)
    parser.add_argument('--current', type=Path, default=Path(__file__).resolve().parents[1] / 'src/main/resources/bloodborne_blocks/logical')
    args = parser.parse_args()
    print(json.dumps(verify(args.baseline, args.current)))
