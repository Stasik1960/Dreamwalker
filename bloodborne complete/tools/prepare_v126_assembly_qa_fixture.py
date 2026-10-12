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
print(json.dumps({'status': 'PASS_QA_FIXTURE_WRITTEN_NATIVE_NOT_RUN', 'assemblies': len(result['assemblies']), 'file': str(destination)}, ensure_ascii=False))
