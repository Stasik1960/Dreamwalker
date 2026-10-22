"""A short read-only committed SQLite snapshot, never a planner mutation."""
import json,sqlite3,time
from pathlib import Path
root=Path(__file__).resolve().parents[3]/'research'
path=root/'native-grid-plan'/'plan.sqlite'
db=sqlite3.connect('file:'+path.as_posix()+'?mode=ro',uri=True,timeout=3)
db.execute('BEGIN')
result={'utc':time.strftime('%Y-%m-%dT%H:%M:%SZ',time.gmtime()),'readOnly':True,'committedSnapshotOnly':True,'tables':{name:db.execute('SELECT count(*) FROM '+name).fetchone()[0] for name in ('selected_objects','fragments','cell_queue','placements','bridge_cells')}}
result['processedCells']=db.execute('SELECT count(*) FROM cell_queue WHERE processed=1').fetchone()[0]
result['nativeFunctionChunkQueryPlan']=[list(v) for v in db.execute('EXPLAIN QUERY PLAN SELECT DISTINCT x,y,z FROM fragments WHERE dim=? AND x>>4=? AND z>>4=?',('.',57,4))]
preflight=root/'native-grid-plan'/'functional-guest-preflight.json'
if preflight.exists():
    data=json.loads(preflight.read_text(encoding='utf-8'));owners={(v['dimension'],uid) for v in data['conflicts'] for uid in v['directlyConflictingOwners']}
    overlap=[{'dimension':dim,'uuid':uid} for dim,uid in sorted(owners) if db.execute('SELECT 1 FROM stage1_selected_objects WHERE dim=? AND uuid=?',(dim,uid)).fetchone()]
    result['functionalRetentions']={'objects':len(owners),'previouslyConvertedOverlap':len(overlap),'overlap':overlap}
db.close()
(root/'native-grid-plan'/'context-extension'/'committed-snapshot.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8')
