"""Count actual placements of finite wide-native base compositions, read-only."""
import collections,hashlib,json,sqlite3
from pathlib import Path
ROOT=Path(__file__).resolve().parents[3];PLAN=ROOT/'research/native-grid-plan'
key=lambda v:json.dumps(v,sort_keys=True,separators=(',',':'))
request=PLAN/'full/post-activation-new-wide-states.json'
wanted={key(s) for s in json.loads(request.read_text(encoding='utf-8-sig'))['states']}
compositions={};digest=hashlib.sha256()
with (PLAN/'compositions.jsonl').open('rb') as stream:
    for line in stream:
        digest.update(line);row=json.loads(line)
        if key(row.get('baseState')) in wanted:
            compositions[row['key']]={'state':row['baseState'],'mode':row.get('baseVisualMode','native')}
assert digest.hexdigest()=='3af36baa112a022b79b4c8834b0523e38be747a5072d7acdd8e17ea450184ec5'
unsafe={k:v for k,v in compositions.items() if v['mode'] in ('native','nativePlusSubstrateFragments')}
db=sqlite3.connect('file:'+(PLAN/'plan.sqlite').as_posix()+'?mode=ro',uri=True)
db.execute('PRAGMA query_only=ON');db.execute('BEGIN')
rows=db.execute('SELECT composition,count(*) FROM placements WHERE composition IN ('+','.join('?' for _ in unsafe)+') GROUP BY composition',tuple(unsafe)).fetchall()
db.close()
report={'status':'READ_ONLY_FINITE_WIDE_NATIVE_IMPACT','actualUnsafeCompositions':len(unsafe),'actualPlacements':sum(n for _,n in rows),
        'rows':[{'key':k,'placements':n,**unsafe[k]} for k,n in rows],'rawSha256':digest.hexdigest(),'worldReads':0,'mapWrites':0,'databaseWrites':0}
output=PLAN/'full/post-activation-new-wide-impact.json'
with output.open('x',encoding='utf-8') as stream:json.dump(report,stream,ensure_ascii=False,indent=2)
print(json.dumps({k:v for k,v in report.items() if k!='rows'}))
