"""Read-only/isolated export checks; never stages or launches the full source world."""
import ast,base64,gzip,json,uuid
from pathlib import Path
from v127_stage_full_conversion import ROOT,MAP,PACK,fresh_target,entities_export,sha
from world_io import decode_nbt,compound
from v127_apply_full_map_poses import write
before={'map':sha(MAP),'pack':sha(PACK)};rejected=[]
for path in [ROOT,ROOT.parent,MAP.parent,MAP.parent/'derived-world',Path('E:/paused-copy-derived')]:
    try:fresh_target(path)
    except (AssertionError,ValueError):rejected.append(str(path))
    else:raise AssertionError('Unsafe destination accepted: '+str(path))
assert fresh_target(ROOT/'build/v127-unused-readonly-validation').is_relative_to(ROOT/'build')
for name in ['v127_stage_full_conversion.py','v127_prepare_full_conversion.py','v127_finalize_full_conversion.py']:ast.parse((ROOT/'tools'/name).read_text(encoding='utf-8-sig'))
destination=ROOT/'build'/('v127-entity-export-validation-'+uuid.uuid4().hex);rows=entities_export(destination);ids=set()
for row in rows:
    assert row['instanceUuid'] not in ids;ids.add(row['instanceUuid']);nbt=compound(decode_nbt(base64.b64decode(row['nbtBase64'])).root)
    assert nbt['id'].value==row['registryId'] and [v.value for v in nbt['Pos'].value]==row['exactSourcePosition']
    source=compound(nbt['SourceLegacyPayload']);assert source['Schema'].value==1 and source['Schema'].type==3
assert len(rows)==262
after={'map':sha(MAP),'pack':sha(PACK)};assert before==after
write(ROOT/'docs/v12.7/conversion/offline-stage-validation.json',{'status':'PASS_READONLY_SAFETY_TYPED_ENTITY_EXPORT','unsafeDestinationsRejected':rejected,'knownEntities':len(rows),'entityUuidDuplicates':0,'typedPosAndLegacyEnvelopeExact':True,'sourceHashesBefore':before,'sourceHashesAfter':after,'originalWorldConverted':False,'worldDirectoryWritten':False,'fullStageExecute':'NOT_RUN_BY_USER_REQUEST','serverRunnerValidation':'SEPARATE_NATIVE_SMALL_FIXTURE'})
print(json.dumps({'status':'PASS','knownEntities':len(rows),'unsafeDestinationsRejected':len(rejected),'fullOriginalWorldWritten':False}))
