"""Read indexed progress and pending existence; never opens a writable plan."""
import datetime
import json
import sqlite3
import sys
from collections import Counter
from pathlib import Path

research = Path(__file__).resolve().parents[3] / 'research'
database = research / 'native-grid-plan' / 'plan.sqlite'
with sqlite3.connect(database.as_uri() + '?mode=ro', uri=True, timeout=2) as db:
    db.execute('PRAGMA query_only=ON')
    db.execute('BEGIN')
    values = {key: json.loads(value) for key, value in db.execute(
        "SELECT key,value FROM meta WHERE key IN "
        "('nativeComposeCursor','sourceLocalRetentionJournal','compositionGeneration')")}
    pending_exists = db.execute('SELECT 1 FROM cell_queue WHERE processed=0 LIMIT 1').fetchone() is not None
    placement_count = db.execute('SELECT count(*) FROM placements').fetchone()[0] if '--placements' in sys.argv else None
    zone_activity = None
    if '--sections' in sys.argv:
        zones = list(db.execute("SELECT sequence,json_extract(data,'$.sourceZone.dimension'),"
                                "json_extract(data,'$.sourceZone.minimum') FROM source_local_retentions "
                                "WHERE json_type(data,'$.sourceZone')='object' ORDER BY sequence"))
        counts = Counter((dim, minimum) for _, dim, minimum in zones)
        zone_activity = {'decisions': len(zones), 'uniqueSections': len(counts),
                         'mostRepeated': [{'dimension': key[0], 'minimum': json.loads(key[1]), 'decisions': count}
                                          for key, count in counts.most_common(8)]}
report = {'status': 'OBSERVATION_ONLY', 'observedUtc': datetime.datetime.now(datetime.timezone.utc).isoformat(),
          'databaseWrites': 0, 'meta': values, 'pendingNativeCellExists': pending_exists}
if zone_activity is not None:
    report['zoneActivity'] = zone_activity
if placement_count is not None:
    report['committedPlacements'] = placement_count
output = research / 'native-progress-live.json'
temporary = output.with_suffix('.tmp')
temporary.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
temporary.replace(output)
print(json.dumps(report, ensure_ascii=False))
