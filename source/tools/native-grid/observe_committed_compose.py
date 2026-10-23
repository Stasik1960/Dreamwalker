"""Record only committed authoring counters, without writing/opening any world."""
import argparse, datetime, hashlib, json, sqlite3
from pathlib import Path
from run_profiles import ROOT, write

parser = argparse.ArgumentParser()
parser.add_argument('--run', required=True)
args = parser.parse_args()
assert Path(args.run).name == args.run
plan = ROOT / 'research/native-grid-plan'
db = sqlite3.connect('file:' + (plan / 'plan.sqlite').as_posix() + '?mode=ro', uri=True)
try:
    db.execute('PRAGMA query_only=ON')
    db.execute('BEGIN')
    counts = {table: db.execute('SELECT count(*) FROM ' + table).fetchone()[0]
              for table in ('selected_objects', 'fragments', 'whole_cells', 'bridge_cells',
                            'cell_queue', 'activated_native', 'placements', 'compositions')}
    selected = dict(db.execute('SELECT kind,count(*) FROM selected_objects GROUP BY kind'))
    templates = db.execute('SELECT count(DISTINCT template) FROM whole_cells').fetchone()[0]
finally:
    db.close()
report = {'status': 'COMMITTED_COUNTERS_ONLY_NOT_ACCEPTANCE', 'run': args.run,
          'observedUtc': datetime.datetime.now(datetime.timezone.utc).isoformat(),
          'counts': counts, 'selectedKinds': selected, 'wholeTemplates': templates,
          'persistentDatabaseWrites': 0, 'worldFilesRead': 0, 'worldWrites': 0}
write(plan / 'full/compose-committed-snapshot.json', report)
cursor_path = ROOT / 'research/current-operation.json'
cursor = json.loads(cursor_path.read_text(encoding='utf-8-sig'))
assert Path(cursor['processReceipt']).stem == args.run
cursor['committedCounters'] = report
write(cursor_path, cursor)
print(json.dumps(report))
