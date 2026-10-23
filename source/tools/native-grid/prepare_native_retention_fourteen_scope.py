"""Root-reviewed finite scope. Reads bound reports only; no plan/world access."""
import hashlib
import json
from run_profiles import ROOT, write

directory = ROOT / 'research/native-grid-plan/full'
pins = {
    'candidateReport': ('same-pose-overhang-candidates.json', '7be8b927a5ba8895ae22e6b017a730b96d5780f27c8a086e9846dbe6b8748c88'),
    'physicalPattern': ('native-overhang-C-source-proof.json', '019b1e247a216a953a05817d86d5c792d22605bac7e32a60517369fe94654840'),
    'typeGeometryProof': ('direct-decor-runtime-member-proof.json', '160bc13e47a9eb442bea517120ff3a31849554971b53f13b8fb71aacbc1dc337'),
    'physicalDecorProof': ('direct-decor-six-visual-only-proof.json', 'de8a995d38538156cf0350eedde0a77a09a539be2118d8001f55264a4cc453a4'),
}
documents = {}
refs = {}
sha = lambda path: hashlib.sha256(path.read_bytes()).hexdigest()
for name, (filename, expected) in pins.items():
    path = directory / filename
    assert sha(path) == expected, name
    documents[name] = json.loads(path.read_text(encoding='utf-8-sig'))
    refs[name] = {'path': str(path), 'sha256': expected}
report = documents['candidateReport']
six = documents['physicalDecorProof']
assert six['status'] == 'PASS' and six['summary']['exactCases'] == 6
assert six['summary']['sharedECellsOnDmembers'] == 0
types = {row['pose']: row for row in documents['typeGeometryProof']['types']}
cases = []
for candidate in report['candidates']:
    if candidate['classification'] != 'ISOLATED_PENDING_FINITE_CANDIDATE_FOR_ROOT_DECISION':
        continue
    assert len(candidate['directSelectedOwners']) == 1
    owner = candidate['directSelectedOwners'][0]
    assert owner['isolatedAllPendingDirectStaticOwner']
    obj = owner['object'][0]
    actual_members = {tuple(row['cell']) for row in types[obj['pose']]['runtimeCells']}
    visual = {(row['dx'], row['dy'], row['dz']) for row in owner['fragments']}
    assert len(actual_members) == 6 and actual_members <= visual
    cases.append({'nativeUuid': candidate['nativeObject']['uuid'],
                  'selectedOwnerUuid': owner['uuid'], 'policy': 'isolated_pending'})
assert len(cases) == 8
indexed = {row['nativeObject']['uuid']: row for row in report['candidates']}
for row in six['cases']:
    uid = row['nativeC']['uuid']
    candidate = indexed[uid]
    assert len(candidate['directSelectedOwners']) == 1
    assert candidate['directSelectedOwners'][0]['uuid'] == row['D']['uuid']
    assert row['runtimeMemberCount'] == 3
    cases.append({'nativeUuid': uid, 'selectedOwnerUuid': row['D']['uuid'],
                  'policy': 'visual_only_keep_members'})
assert len(cases) == 14 and len({row['nativeUuid'] for row in cases}) == 14
result = {'schemaVersion': 1, 'status': 'ROOT_APPROVED_FINITE_NATIVE_RETENTION_SCOPE',
          **refs, 'cases': cases}
output = directory / 'native-retention-fourteen-scope.json'
if output.exists():
    assert json.loads(output.read_text(encoding='utf-8-sig')) == result
else:
    write(output, result)
print(json.dumps({'status': result['status'], 'cases': len(cases),
                  'sha256': sha(output), 'databaseWrites': 0, 'mapWrites': 0}))
