from pathlib import Path
import sqlite3,json
ROOT=Path(__file__).resolve().parents[3]
old=ROOT.parent/'DW-ordinary-city-pilot-20261020'/'research'/'city-pilot'/'full-city-copy.ordinary-migration'/'plan.sqlite'
db=sqlite3.connect('file:'+str(ROOT/'research'/'native-grid-plan'/'plan.sqlite').replace('\\','/')+'?mode=ro',uri=True)
db.execute('ATTACH DATABASE ? AS old',('file:'+str(old).replace('\\','/')+'?mode=ro',))
queries=["SELECT c.dim,c.x,c.y,c.z,c.uuid,o.reason FROM old.stored_contributions c JOIN objects o ON o.dim=c.dim AND o.uuid=c.uuid WHERE o.kind='preserve'", "SELECT c.dim,c.x,c.y,c.z,c.uuid,o.reason FROM old.members c JOIN objects o ON o.dim=c.dim AND o.uuid=c.uuid WHERE o.kind='preserve'", "SELECT p.data FROM selected_objects o JOIN poses p ON p.key=o.pose WHERE o.dim='.' AND o.x=925 AND o.y=23 AND o.z=78", "SELECT 1 FROM old.members m JOIN selected_objects o ON o.dim=m.dim AND o.uuid=m.uuid WHERE m.dim='.' AND m.x=925 AND m.y=23 AND m.z=78 LIMIT 1"]
(ROOT/'research'/'migration-query-plans.json').write_text(json.dumps([{'query':q,'plan':db.execute('EXPLAIN QUERY PLAN '+q).fetchall()} for q in queries],indent=2),encoding='utf-8')
(ROOT/'research'/'migration-local-case.json').write_text(json.dumps({'object':db.execute("SELECT o.*,p.data FROM objects o JOIN poses p ON p.key=o.pose WHERE o.uuid='[1312750229,1270559806,2064153878,-2144170692]'").fetchall(),'issues':db.execute('SELECT * FROM issues').fetchall()},indent=2),encoding='utf-8')
