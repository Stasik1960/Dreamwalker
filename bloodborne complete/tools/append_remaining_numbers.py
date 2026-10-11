"""Append free type numbers without changing any V12 row or retired number."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / 'src/architecture/resources/bloodborne_dw'
OUT = ROOT / 'docs/remaining-transfer'

def main():
    path = RES / 'debug_catalogue.json'
    catalogue = json.loads(path.read_text(encoding='utf-8'))
    additions = json.loads((RES / 'remaining-catalogue.json').read_text(encoding='utf-8'))['objects']
    used = {int(row['temporaryId']) for row in catalogue['entries']}
    rows = {row['registryId']: row for row in catalogue['entries']}
    reserved = set(range(90001, 90021)) | set(range(91000, 93001))
    candidates = iter(n for n in range(90021, 100000) if n not in used and n not in reserved)
    output = []
    for artwork in additions:
        registry = 'bloodborne_dw:' + artwork['path']
        if registry in rows:
            row = rows[registry]
            if row['kind'] != 'architecture' or not registry.startswith('bloodborne_dw:source_') or row['aliases'] != [registry]:
                raise SystemExit('Existing type conflicts: ' + registry)
            # These source_ rows belong to this unpublished transfer. Preserve
            # their reserved numbers while improving the new Russian labels.
            row['name'] = artwork['displayName']
        else:
            row = {'temporaryId': str(next(candidates)), 'finalId': None, 'kind': 'architecture',
                   'name': artwork['displayName'], 'registryId': registry, 'aliases': [registry]}
            catalogue['entries'].append(row)
            rows[registry] = row
        output.append({'temporaryId': row['temporaryId'], 'registryId': registry,
                       'name': artwork['displayName'], 'variants': artwork['variants']})
    path.write_text(json.dumps(catalogue, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    (OUT / 'new-types.json').write_text(json.dumps({'policy': '90021..90999 then93001+; V12 and RP ranges reserved', 'entries': output}, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')

if __name__ == '__main__':
    main()
