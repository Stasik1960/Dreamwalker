"""Root's exact eighteen-case decision; retain only the four named extra E owners."""
import hashlib
import json
from run_profiles import ROOT, write

directory = ROOT / 'research/native-grid-plan/full'
sha = lambda path: hashlib.sha256(path.read_bytes()).hexdigest()
base_path = directory / 'native-retention-fourteen-scope.json'
assert sha(base_path) == '866d064a487e7e51e52737de26e47ba89edc6037f9acd5db1309dfff9513efba'
scope = json.loads(base_path.read_text(encoding='utf-8-sig'))
assert len(scope['cases']) == 14
proof_path = directory / 'direct-decor-four-E-carrier-gap-proof.json'
proof_sha = 'fe4983a629b909747d6e59ce4277485d70d93069aae859a12ba1ef9d8de56196'
assert sha(proof_path) == proof_sha
proof = json.loads(proof_path.read_text(encoding='utf-8-sig'))
assert proof['summary'] == {'fourExactE': 4, 'EuniqueSourcePoses': 1,
                           'unsupportedNonBeMemberCells': 4,
                           'existingBeBridgeMemberCellsProven': 8,
                           'isolatedPendingFiniteERetentionCandidates': 4}
assert not proof['ownerGraphTraversal']
for row in proof['cases']:
    assert row['finiteERetentionWholeClippedFootprintIsolatedAndPending']
    assert row['independentSelectedOwnerUuidsNotTraversed'] == []
    counts = row['EcountsAfterExcludingOnlyExactDandE']
    assert counts['pendingCells'] == 18
    assert all(counts[key] == 0 for key in (
        'independentSelectedFragmentCells', 'independentSelectedRuntimeMemberCells',
        'substrate_fragmentsCells', 'native_visual_fragmentsCells',
        'whole_cellsCells', 'native_visual_rootsCells', 'placementsCells'))
    assert row['EfragmentCount'] == 18
    assert row['EruntimeMemberOffsets'] == [[0, 0, 0], [0, 1, 0]]
    assert row['E']['pose'] == 'sp_81609f1671a4127e237c4d3d'
    scope['cases'].append({'nativeUuid': row['nativeC']['uuid'],
                           'selectedOwnerUuid': row['D']['uuid'],
                           'policy': 'isolated_pair_keep_members',
                           'additionalSelectedOwnerUuid': row['E']['uuid']})
assert len(scope['cases']) == 18
assert len({row['nativeUuid'] for row in scope['cases']}) == 18
owners = [row['selectedOwnerUuid'] for row in scope['cases']]
owners += [row['additionalSelectedOwnerUuid'] for row in scope['cases']
           if 'additionalSelectedOwnerUuid' in row]
assert len(owners) == len(set(owners)) == 22
scope['fourDecorProof'] = {'path': str(proof_path), 'sha256': proof_sha}
output = directory / 'native-retention-eighteen-scope.json'
if output.exists():
    assert json.loads(output.read_text(encoding='utf-8-sig')) == scope
else:
    write(output, scope)
print(json.dumps({'status': scope['status'], 'nativeCases': 18,
                  'selectedOwnersRetained': 22, 'sha256': sha(output),
                  'databaseWrites': 0, 'mapWrites': 0}))
