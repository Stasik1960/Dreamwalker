"""Freeze independently generated door anatomy for native GameTests.

The fixture is QA-only: production never reads it. Passage probes are derived
from the user's marked moving leaves, not the physical collision table.
"""
from pathlib import Path
import json

ROOT = Path(__file__).resolve().parents[1]
source = ROOT / 'build/v126-door-plan/door-manifest.json'
document = json.loads(source.read_text(encoding='utf-8-sig'))
result = {'schemaVersion': 1, 'source': 'independent source-mesh partition manifest', 'doors': []}
for row in document['doors']:
    models = [part for part in row['models'] if part['includeInOpen']]
    moving = [part for part in models if part['role'] != 'static']
    lo = [min(part['boundsUnits'][axis] for part in moving) for axis in range(3)]
    hi = [max(part['boundsUnits'][axis + 3] for part in moving) for axis in range(3)]
    normal = [1, 0, 0] if hi[2] - lo[2] > hi[0] - lo[0] else [0, 0, 1]
    centre = [(lo[0] + hi[0]) / 2, lo[1] + 0.8, (lo[2] + hi[2]) / 2]
    result['doors'].append({
        'mainId': str(row['mainId']), 'registryId': row['registryId'],
        'variant': row['variant'], 'leafCount': row['leafCount'],
        'sourceClosedParts': row['sourceClosedParts'],
        'leafHinges': row['leafHinges'],
        'models': [{key: part[key] for key in ('role', 'model', 'partOffset', 'boundsUnits')} for part in models],
        'passage': {'footCentreUnits': centre, 'crossingDistanceUnits': 64, 'normal': normal},
    })
destination = ROOT / 'src/gametest/resources/bloodborne_dw/v126-door-expectations.json'
destination.parent.mkdir(parents=True, exist_ok=True)
destination.write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
print(json.dumps({'status': 'PASS_QA_FIXTURE_WRITTEN_NATIVE_NOT_RUN', 'doors': len(result['doors']), 'file': str(destination)}, ensure_ascii=False))
