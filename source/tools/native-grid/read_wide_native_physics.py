"""Read only the finite new-wide export rows; no world or plan access."""
import hashlib,json,sqlite3
from pathlib import Path

ROOT=Path(__file__).resolve().parents[3]
PLAN=ROOT/'research/native-grid-plan'
request=PLAN/'full/post-activation-new-wide-states.json'
database=ROOT/'research/fragments.sqlite'
output=PLAN/'full/post-activation-new-wide-physics.json'
assert not output.exists()
digest=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
canonical=lambda v:json.dumps(v,sort_keys=True,separators=(',',':'))
db=sqlite3.connect('file:'+database.as_posix()+'?mode=ro',uri=True)
db.execute('PRAGMA query_only=ON');db.execute('BEGIN')
rows=[]
for state in json.loads(request.read_text(encoding='utf-8-sig'))['states']:
    raw=db.execute('SELECT data FROM native WHERE state=?',(canonical(state),)).fetchone()
    assert raw is not None,state
    data=json.loads(raw[0]);shapes={}
    for field in ('collision','outline','culling','climbing'):
        boxes=data[field]
        shapes[field]={'boxes':len(boxes),'minimum':[min(b[i] for b in boxes) for i in range(3)] if boxes else None,
                       'maximum':[max(b[i+3] for b in boxes) for i in range(3)] if boxes else None}
    rows.append({'state':state,'rowSha256':hashlib.sha256(raw[0].encode()).hexdigest(),'shapes':shapes,'optics':data['optics'],'fluid':data.get('fluid')})
db.close()
report={'status':'READ_ONLY_EXACT_NEW_WIDE_NATIVE_PHYSICS','states':rows,'request':{'path':str(request),'sha256':digest(request)},
        'fragments':{'path':str(database),'sha256':digest(database)},'worldReads':0,'planReads':0,'databaseWrites':0,'mapWrites':0}
with output.open('x',encoding='utf-8') as stream:json.dump(report,stream,ensure_ascii=False,indent=2)
print(json.dumps({'out':str(output),'sha256':digest(output),'rows':len(rows),'shapes':[{'state':r['state'],'collision':r['shapes']['collision']} for r in rows]}))
