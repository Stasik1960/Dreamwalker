"""Read-only authored/generator alternate inventory; never invent source assets."""
from pathlib import Path
import csv
import hashlib
import json

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / 'src/architecture/resources/bloodborne_dw'
OUT = ROOT / 'docs/v12.7/tool'
OUT.mkdir(parents=True, exist_ok=True)

def load(path):
    return json.loads(path.read_text(encoding='utf-8-sig'))

catalogue = load(RES / 'debug_catalogue.json')['entries']
numbers = {row['registryId']: row['temporaryId'] for row in catalogue}
redirected = {row['registryId'] for row in catalogue if row.get('canonicalRegistryId', row['registryId']) != row['registryId']}
styles = {row['registryId'] for row in load(RES / 'imported-style-retirements.json')['retirements']}
aliases = load(RES / 'imported-type-aliases.json')
for group in aliases['groups']:
    redirected.update(member for member in group['members'] if member != group['primaryRegistryId'])
redirected.update(load(RES / 'imported-assembly-v126.json')['hiddenPartRedirects'])
registered_new = {'bloodborne_dw:' + row['path'] for row in load(RES / 'remaining-catalogue.json')['objects']}
followup_path = RES / 'imported-type-aliases-v127.json'
if followup_path.exists():
    for group in load(followup_path)['groups']:
        redirected.update(member for member in group['members'] if member != group['primaryRegistryId'])

rows = []
for path in sorted((RES / 'composite').glob('*.json')):
    document = load(path)
    if 'variants' not in document or 'id' not in document:
        continue
    registry = document['id']
    active = registry in numbers and registry not in redirected and registry not in styles and (not registry.startswith('bloodborne_dw:source_') or registry in registered_new)
    for variant, row in enumerate(document['variants']):
        poses = row['poses']
        for state in ('closed', 'open'):
            pose = poses.get(state, poses['closed'])
            parts = pose['parts']
            authored = any(part.get('altModel', part['model']) != part['model'] for part in parts)
            generated = registry.startswith('bloodborne_dw:source_') and not authored
            rows.append({'id': numbers.get(registry, ''), 'registryId': registry,
                         'offeredType': active, 'variant': variant, 'open': state == 'open',
                         'partCount': len(parts),
                         'alternate': 'AUTHORED_MODEL_EXACT' if authored else 'GENERATED_VERTEX_TONE' if generated else 'EXISTING_BASE_FALLBACK',
                         'baseModels': ';'.join(part['model'] for part in parts),
                         'altModels': ';'.join(part.get('altModel', part['model']) for part in parts),
                         'sourceDescriptorSha256': hashlib.sha256(path.read_bytes()).hexdigest()})

active_rows = [row for row in rows if row['offeredType']]
unavailable = [row for row in active_rows if row['alternate'] == 'EXISTING_BASE_FALLBACK']
native_aliases = []
models = ROOT / 'src/architecture/resources/assets/bloodborne_dw/models'
for path in sorted((models / 'block/wall/alt').glob('*.json')):
    expected = {'parent': 'bloodborne_dw:block/wall/base/' + path.stem}
    assert load(path) == expected, f'Original native authored ALT overrides require explicit preservation: {path}'
    native_aliases.append({'model': 'bloodborne_dw:' + path.relative_to(models).as_posix()[:-5],
                           'baseParent': expected['parent'], 'evidence': 'EXACT_BASE_PARENT_NO_OVERRIDES',
                           'sha256': hashlib.sha256(path.read_bytes()).hexdigest()})
for path in sorted((models / 'alt/prototype_ladder').glob('*.json')):
    document = load(path)
    assert list(document) == ['parent'] and document['parent'].startswith('bloodborne_dw:base/source/minecraft/block/hold/')
    native_aliases.append({'model': 'bloodborne_dw:' + path.relative_to(models).as_posix()[:-5],
                           'baseParent': document['parent'], 'evidence': 'EXACT_BASE_PARENT_NO_OVERRIDES',
                           'sha256': hashlib.sha256(path.read_bytes()).hexdigest()})
report = {'schemaVersion': 1, 'status': 'PASS' if not unavailable else 'FAIL',
          'runtimeProfileCoverage': 'VERIFY_WITH_BbAlternateV127GameTests_NOT_STATIC_JSON_PRESENCE',
          'activeCompositeTypeCount': len({row['registryId'] for row in active_rows}),
          'activeCompositePoseCountClosedAndOpen': len(active_rows),
          'authoredAlternatePosesExact': sum(row['alternate'] == 'AUTHORED_MODEL_EXACT' for row in active_rows),
          'generatedAlternatePoses': sum(row['alternate'] == 'GENERATED_VERTEX_TONE' for row in active_rows),
          'missingVisualAlternatePoses': unavailable,
          'originalNativeAlternateModels': native_aliases,
          'originalNativeAlternatePolicy': 'GENERATED_RGB_TONE_ONLY_ON_ALT_RENDER_WRAPPERS; immutable source model files and BASE quads unchanged; existing states, mounting, physical shape and IDs preserved',
          'generatedPolicy': {'scope': 'NEW_SOURCE_TYPES_ONLY', 'RGBMultiplier': [.60, .67, .72],
                              'alpha': 'UNCHANGED', 'UV': 'UNCHANGED', 'geometry': 'UNCHANGED',
                              'physics': 'UNCHANGED', 'authoredAlternates': 'UNCHANGED_ENTIRE_POSE',
                              'sourceArt': 'NO_SOURCE_MODEL_OR_TEXTURE_MUTATIONS'},
          'rpPlacementObjects': {'profileField': 'BbAltVisual', 'nbtType': 'BYTE',
                                 'basePersistence': 'NEW_FIELD_OMITTED_WHEN_BASE',
                                 'physicsSelector': 'EXISTING_TYPED_SELECTOR_UNCHANGED',
                                 'alternate': 'GENERATED_VERTEX_TONE', 'sameRGBMultiplier': [.60, .67, .72]},
          'nativeUtilityMarkers': {'dw_barrier': 'BASE_OR_ALT_MARKER_ONLY', 'dw_light': 'BASE_OR_ALT_MARKER_ONLY'},
          'scope': 'All offered building blocks and RP placement objects; exclude weapons, ordinary items, mobs and other namespaces'}
with (OUT / 'ALT-COVERAGE.tsv').open('w', encoding='utf-8-sig', newline='') as file:
    writer = csv.DictWriter(file, fieldnames=list(rows[0]), delimiter='\t')
    writer.writeheader()
    writer.writerows(rows)
(OUT / 'alternate-audit.json').write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
print(json.dumps({key: report[key] for key in ('status','activeCompositeTypeCount','activeCompositePoseCountClosedAndOpen','authoredAlternatePosesExact','generatedAlternatePoses')}, ensure_ascii=False))
if unavailable:
    raise SystemExit('Active original composite poses missing source alternate: ' + ','.join(row['registryId'] for row in unavailable))
