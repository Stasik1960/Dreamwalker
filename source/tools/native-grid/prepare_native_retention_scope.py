"""Root's explicit finite authorization for the eight inspected isolated cases."""
import hashlib, json
from pathlib import Path
from run_profiles import ROOT, write

directory = ROOT / 'research/native-grid-plan/full'
candidate = directory / 'same-pose-overhang-candidates.json'
physical = directory / 'native-overhang-C-source-proof.json'
sha = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()
candidate_sha = '7be8b927a5ba8895ae22e6b017a730b96d5780f27c8a086e9846dbe6b8748c88'
physical_sha = '019b1e247a216a953a05817d86d5c792d22605bac7e32a60517369fe94654840'
assert sha(candidate) == candidate_sha and sha(physical) == physical_sha
document = json.loads(candidate.read_text(encoding='utf-8-sig'))
cases = []
for item in document['candidates']:
    if item['classification'] != 'ISOLATED_PENDING_FINITE_CANDIDATE_FOR_ROOT_DECISION':
        continue
    assert item['syntheticLegacyUidMatches'] and len(item['directSelectedOwners']) == 1
    assert item['legacyRecipeIndex'] == 1161
    owner = item['directSelectedOwners'][0]
    assert owner['isolatedAllPendingDirectStaticOwner']
    cases.append({'nativeUuid': item['nativeObject']['uuid'], 'selectedOwnerUuid': owner['uuid']})
assert len(cases) == 8
assert any(case['nativeUuid'] == '[-533749318,1735200735,-92998311,375839603]' for case in cases)
result = {'schemaVersion': 1, 'status': 'ROOT_APPROVED_FINITE_ISOLATED_SCOPE',
          'candidateReport': {'path': str(candidate), 'sha256': candidate_sha},
          'physicalPattern': {'path': str(physical), 'sha256': physical_sha},
          'cases': cases}
output = directory / 'native-retention-isolated-scope.json'
if output.exists():
    assert json.loads(output.read_text(encoding='utf-8-sig')) == result
else:
    write(output, result)
print(json.dumps({'status': result['status'], 'cases': len(cases), 'sha256': sha(output), 'databaseWrites': 0, 'mapWrites': 0}))
