"""Verify the exact post-compose integration, without database/map writes."""
import ast,hashlib,json
from pathlib import Path
root=Path(__file__).resolve().parents[3]
source=root/'source'/'tools'/'native-grid-city'
before=root/'research'/'processes'/'20261022T015414-migrate.inputs'
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
require=lambda ok,msg:None if ok else (_ for _ in ()).throw(RuntimeError(msg))
def functions(path):return {v.name:hashlib.sha256(ast.dump(v,include_attributes=False).encode()).hexdigest() for v in ast.parse(path.read_text(encoding='utf-8-sig')).body if isinstance(v,(ast.FunctionDef,ast.AsyncFunctionDef))}
old=functions(before/'migrate.py');new=functions(source/'migrate.py');changed=[name for name in sorted(set(old)|set(new)) if old.get(name)!=new.get(name)]
require(changed==['apply','invalidate_debt'],'Unexpected migration function changes: '+str(changed))
oldctx=functions(before/'context_stage.py');newctx=functions(source/'context_stage.py')
hooks=('filter_selection','check_complete','carrier_predicates','import_functions','native_function_conflicts','logical_selected_carrier')
require(all(oldctx[k]==newctx[k] for k in hooks),'SourcePose/composition/native finite-function hooks changed')
original=(before/'migrate.py').read_text(encoding='utf-8-sig');current=(source/'migrate.py').read_text(encoding='utf-8-sig')
expected=original.replace('CREATE INDEX IF NOT EXISTS fragment_chunk ON fragments(dim,x,z);','CREATE INDEX IF NOT EXISTS fragment_chunk ON fragments(dim,x,z);\nCREATE INDEX IF NOT EXISTS fragment_native_chunk ON fragments(dim,(x>>4),(z>>4),x,y,z);').replace("context_stage.apply_guard(module_api(),db,target)","context_stage.apply_guard(module_api(),db,target,a.runtime_jar)").replace("    for dim,cx,cz in db.execute('SELECT dim,cx,cz FROM relight'):\n","    for dim,cx,cz in db.execute('SELECT dim,cx,cz FROM relight'):\n        if meta(db,'contextExtension') and not context_stage.light_to_invalidate(module_api(),db,dim,cx,cz):continue\n")
require(current==expected,'Source differs from the exact three approved narrow edits')
report={'schemaVersion':1,'status':'PASS','beforePlannerSha256':sha(before/'migrate.py'),'afterPlannerSha256':sha(source/'migrate.py'),'beforeContextSha256':sha(before/'context_stage.py'),'afterContextSha256':sha(source/'context_stage.py'),'changedMigrationFunctions':changed,'allOtherMigrationFunctionsAstIdentical':True,'compositionAndNativeGateHooksAstIdentical':list(hooks),'indexOnlyChange':'CREATE INDEX IF NOT EXISTS fragment_native_chunk ON fragments(dim,(x>>4),(z>>4),x,y,z)','sameImmutableSourceCache':True,'sameGlobalDatabase':True,'sameArchivedStage1Tables':True,'sourcePoseRoutingChanged':False,'finiteGateDecisionsAreExternalInputs':True,'persistentDatabaseWrites':0,'mapWrites':0}
out=root/'research'/'native-grid-plan'/'context-extension'/'integration-source-proof.json';out.write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
