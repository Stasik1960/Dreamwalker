"""Preserve V12.5 preview component states as independent migration fixtures."""
from pathlib import Path
import json

ROOT = Path(__file__).resolve().parents[1]
source = ROOT / 'build/v126-assembly-plan/assembly-manifest.json'
document = json.loads(source.read_text(encoding='utf-8-sig'))
result = {'schemaVersion': 1, 'baselineCommit': document['baselineCommit'], 'assemblies': []}
for row in document['assemblies']:
    result['assemblies'].append({
        'primaryId': row['primaryId'], 'primaryRegistryId': row['primaryRegistryId'],
        'installationPose': row['installationPose'], 'key': row['key'],
        'components': [{key: part[key] for key in ('registryId', 'mainId', 'properties', 'relativePosition', 'sourceShift')}
                       for part in row['components']],
    })
destination = ROOT / 'src/gametest/resources/bloodborne_dw/v126-assembly-expectations.json'
destination.parent.mkdir(parents=True, exist_ok=True)
destination.write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
city_source = ROOT / 'docs/v12.6/city/mount-depth-state-proposal.json'
city_destination = None
if city_source.is_file():
    city = json.loads(city_source.read_text(encoding='utf-8-sig'))
    recipe = city['sourceRecipe']
    reference = next(pair for pair in city['actualSourceChecks'] if pair['rotation'] == 0)
    lower, upper = reference['lowerSourcePosition'], reference['upperSourcePosition']
    actual_delta = [upper[axis] - lower[axis] for axis in range(3)]
    if actual_delta != [0, 2, -1]:
        raise ValueError(f'Actual source-city yaw0 pair has unexpected delta {actual_delta}')
    mounting = {'schemaVersion': 1, 'primaryId': recipe['primaryId'], 'primaryRegistryId': recipe['primaryRegistryId'],
                'installationPose': recipe['installationPose'], 'key': recipe['key'], 'referenceSourcePair': reference,
                'actualSourcePairCount': len(city['actualSourceChecks']), 'components': []}
    for index, part in enumerate(recipe['components']):
        mounting['components'].append({'registryId': part['registryId'], 'mainId': part['mainId'],
            'properties': {'variant': str(part['pose']), 'rotation': str(reference['rotation'] + part['componentYawDelta']),
                           'profile': 'base', 'open': 'false'},
            'relativePosition': [0, 0, 0] if index == 0 else actual_delta,
            'sourceShift': part['sourceShift']})
    city_destination = ROOT / 'src/gametest/resources/bloodborne_dw/v126-city-mounting-expectations.json'
    city_destination.write_text(json.dumps(mounting, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
print(json.dumps({'status': 'PASS_QA_FIXTURE_WRITTEN_NATIVE_NOT_RUN', 'assemblies': len(result['assemblies']),
                  'file': str(destination), 'cityMountingFile': str(city_destination) if city_destination else None}, ensure_ascii=False))
