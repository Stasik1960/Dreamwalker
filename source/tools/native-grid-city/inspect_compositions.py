"""Addressed plan-only diagnosis of native bases; no map writes or rescans."""
import argparse,importlib.util,json,sqlite3
from pathlib import Path
spec=importlib.util.spec_from_file_location('migration',Path(__file__).with_name('migrate.py'))
m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
a=argparse.ArgumentParser();a.add_argument('--id',required=True);args=a.parse_args()
db=m.connect(m.DEFAULT_PLAN);rows=[]
for dim,x,y,z,key,source in db.execute("SELECT dim,x,y,z,composition,source_state FROM placements WHERE json_extract(source_state,'$.registryId')=?",(args.id,)):
    composition=db.execute('SELECT data FROM compositions WHERE key=?',(key,)).fetchone()
    members=db.execute('SELECT c.uuid,o.pose,o.kind,o.reason FROM old.members c LEFT JOIN objects o ON o.dim=c.dim AND o.uuid=c.uuid WHERE c.dim=? AND c.x=? AND c.y=? AND c.z=?',(dim,x,y,z)).fetchall()
    stored=db.execute('SELECT c.uuid,o.pose,o.kind,o.reason FROM old.stored_contributions c LEFT JOIN objects o ON o.dim=c.dim AND o.uuid=c.uuid WHERE c.dim=? AND c.x=? AND c.y=? AND c.z=?',(dim,x,y,z)).fetchall()
    roots=db.execute('SELECT o.uuid,p.data,o.kind,o.reason FROM objects o JOIN poses p ON p.key=o.pose WHERE o.dim=? AND o.x=? AND o.y=? AND o.z=?',(dim,x,y,z)).fetchall()
    rows.append({'dimension':dim,'cell':[x,y,z],'source':json.loads(source),'composition':json.loads(composition[0]) if composition else key,'members':members,'stored':stored,'roots':roots})
out=m.ROOT/'research'/'composition-diagnosis.json';m.atomic_json(out,{'registryId':args.id,'matches':len(rows),'samples':rows[:20]});print(out)
