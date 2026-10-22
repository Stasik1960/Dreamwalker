"""Archive the final source delta from the successful composition generation."""
import ast,hashlib,json
from pathlib import Path
root=Path(__file__).resolve().parents[3];source=Path(__file__).parent;before=root/'research/processes/20261022T021511-migrate.inputs'
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
def functions(path):return {v.name:hashlib.sha256(ast.dump(v,include_attributes=False).encode()).hexdigest() for v in ast.parse(path.read_text(encoding='utf-8-sig')).body if isinstance(v,(ast.FunctionDef,ast.AsyncFunctionDef))}
entries={}
for name in ('migrate.py','context_stage.py'):
    old=functions(before/name);new=functions(source/name)
    entries[name]={'beforeSha256':sha(before/name),'afterSha256':sha(source/name),'changedFunctions':[key for key in sorted(set(old)|set(new)) if old.get(key)!=new.get(key)],'unchangedFunctions':[key for key in sorted(set(old)&set(new)) if old[key]==new[key]]}
assert entries['migrate.py']['changedFunctions']==['apply','compose','freeze_inputs','parser','preflight_functional_guests','promote_city'],'Unexpected migration function changes'
assert entries['context_stage.py']['changedFunctions']==['_binding','_comparison_ids','_comparison_state','_final_input_gate','_states_digest','apply_guard','bind_copy','full_apply_gate','import_full_functions','leave_pilot_context','native_function_conflicts','require_full_functions'],'Unexpected context function changes'
hooks=('filter_selection','check_complete','carrier_predicates','import_functions','logical_selected_carrier')
old=functions(before/'context_stage.py');new=functions(source/'context_stage.py')
assert all(old[key]==new[key] for key in hooks),'SourcePose or context classification changed'
native_proof=root/'research/native-functional-bases/static-waterlogged-default-proof.json'
assert sha(native_proof)=='f3f1037db3f808b0422578a2381934e11f39817a4de6ef531303b2ca2708f61c'
report={'schemaVersion':1,'status':'PASS','entries':entries,'unchangedSourcePoseAndContextSelectionFunctions':list(hooks),'permittedChanges':['post-compose final native-input receipt consumption','guarded future full-city promotion and fresh full-city gate','comparison-only finite StaticArchitecture missing-waterlogged-false serialization normalization'],'comparisonNormalizationProof':{'path':str(native_proof),'sha256':sha(native_proof)},'sourceHashes':{p:sha(source/p) for p in ('migrate.py','context_stage.py','audit_context_states.py','patch_contained_native_route.py')},'mapWrites':0,'databaseWrites':0}
out=root/'research/native-grid-plan/context-extension/post-compose-source-proof.json';out.write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8');print(json.dumps(report))
