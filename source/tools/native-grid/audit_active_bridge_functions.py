"""Read-only check of actual committed bridge hosts against the imported function gate."""
import hashlib
import json
import sqlite3
from run_profiles import ROOT, write

plan = ROOT / 'research/native-grid-plan'
db = sqlite3.connect('file:' + (plan / 'plan.sqlite').as_posix() + '?mode=ro', uri=True)
try:
    db.execute('PRAGMA query_only=ON')
    db.execute('BEGIN')
    metadata = {key: json.loads(value) for key, value in db.execute(
        "SELECT key,value FROM meta WHERE key IN ('binding','fullNativeFunctionGate','fullNativeFunctionDecisions')")}
    gate = metadata['fullNativeFunctionGate']
    assert hashlib.sha256(open(gate['path'], 'rb').read()).hexdigest() == gate['sha256']
    decisions = metadata['fullNativeFunctionDecisions']
    groups = []
    bad = []
    for state, count in db.execute('SELECT host_state,count(*) FROM bridge_cells b WHERE EXISTS(SELECT 1 FROM cell_queue q WHERE q.dim=b.dim AND q.x=b.x AND q.y=b.y AND q.z=b.z) GROUP BY host_state'):
        decision = decisions.get(state)
        row = {'state': json.loads(state), 'cells': count,
               'decision': decision.get('decision') if decision else None,
               'reason': decision.get('reason') if decision else None}
        groups.append(row)
        if row['decision'] != 'bridge_host_only':
            row['firstCells'] = [list(cell) for cell in db.execute(
                'SELECT b.dim,b.x,b.y,b.z,b.retained_resident FROM bridge_cells b WHERE host_state=? AND EXISTS(SELECT 1 FROM cell_queue q WHERE q.dim=b.dim AND q.x=b.x AND q.y=b.y AND q.z=b.z) ORDER BY b.dim,b.x,b.y,b.z LIMIT 8', (state,))]
            bad.append(row)
    result = {'schemaVersion': 1, 'status': 'PASS' if not bad else 'ACTUAL_UNSUPPORTED_COMMITTED_HOSTS',
              'kind': 'READ_ONLY_ACTUAL_QUEUED_HOST_FUNCTION_CHECK_NOT_TARGET_RUNTIME_AUDIT',
              'allCommittedBridgeCells': db.execute('SELECT count(*) FROM bridge_cells').fetchone()[0],
              'sourceBinding': metadata['binding'], 'functionGate': gate,
              'bridgeCells': sum(row['cells'] for row in groups), 'hostStates': len(groups),
              'unsupportedCells': sum(row['cells'] for row in bad), 'unsupportedHosts': bad,
              'worldReads': 0, 'databaseWrites': 0, 'mapWrites': 0}
finally:
    db.close()
output = plan / 'full/queued-bridge-function-check-before-generic-resume.json'
assert not output.exists(), 'Do not repeat or overwrite this addressed snapshot'
write(output, result)
print(json.dumps({key: result[key] for key in ('status','bridgeCells','hostStates','unsupportedCells')}))
