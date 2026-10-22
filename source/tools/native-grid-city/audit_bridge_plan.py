"""Small read-only summary while game/build work runs independently."""
import importlib.util,json,sqlite3
from pathlib import Path
spec=importlib.util.spec_from_file_location('migration',Path(__file__).with_name('migrate.py'));m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
m.control_check();db=sqlite3.connect('file:'+(m.DEFAULT_PLAN/'plan.sqlite').as_posix()+'?mode=ro',uri=True)
report={'schemaVersion':1,'bridgeCells':db.execute('SELECT count(*) FROM bridge_cells').fetchone()[0],
        'retainedResidentRoots':db.execute('SELECT count(*) FROM bridge_cells WHERE retained_resident=1').fetchone()[0],
        'helperCarriers':db.execute('SELECT count(*) FROM bridge_cells WHERE retained_resident=0').fetchone()[0],
        'hosts':[{'state':json.loads(state),'retainedResident':bool(root),'cells':count} for state,root,count in db.execute('SELECT host_state,retained_resident,count(*) FROM bridge_cells GROUP BY host_state,retained_resident')],
        'wholeRoles':db.execute('SELECT count(*) FROM whole_cells').fetchone()[0],
        'protectedContacts':[{'dimension':dim,'cell':[x,y,z],'owner':uid,'pose':pose,'ownerReason':reason,'ownerRoot':[rx,ry,rz],'protectionReason':protection} for dim,x,y,z,uid,pose,reason,rx,ry,rz,protection in db.execute("SELECT b.dim,b.x,b.y,b.z,p.uuid,o.pose,o.reason,o.x,o.y,o.z,p.reason FROM bridge_cells b JOIN protected_cells p ON p.dim=b.dim AND p.x=b.x AND p.y=b.y AND p.z=b.z JOIN objects o ON o.dim=p.dim AND o.uuid=p.uuid WHERE o.kind='preserve'")],
        'localIssues':[{'kind':kind,'objects':count} for kind,count in db.execute('SELECT kind,count(*) FROM issues GROUP BY kind')],
        'newStaticUuids':0,'newBlockEntities':0}
m.atomic_json(m.ROOT/'research'/'bridge-plan-summary.json',report);db.close()
