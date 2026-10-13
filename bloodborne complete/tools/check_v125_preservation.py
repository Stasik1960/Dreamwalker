"""Independent read-only V12.5 preservation, append-only pose and style checks."""
from pathlib import Path
from collections import Counter
import copy,hashlib,json,subprocess,traceback

ROOT=Path(__file__).resolve().parents[1]
REPO=ROOT.parent
DOC=ROOT/'docs/v12.5-city-review'
BASELINE='653ae307ba4c44392ee08969f3890321e95659bb'
PREFIX='bloodborne complete/'
RES='src/architecture/resources/'
COMPOSITE=RES+'bloodborne_dw/composite/'
JAVA='src/architecture/java/dev/dreamwalker/bloodbornedw/'
HOOK_LINES={
 JAVA+'architecture/RemainingArchitecture.java':[
  '        if (dev.dreamwalker.bloodbornedw.composite.ImportedStyleRetirements.retired(id)) return List.of();'],
 JAVA+'composite/CompositeSourceShift.java':[
  '        if(ImportedCollisionV125.supports(spec)){Shift shift=world(state,payload);double offset=payload.getDouble("MountY")+payload.getDouble(dev.dreamwalker.bloodbornedw.architecture.mount.VerticalMount.KEY);return ImportedCollisionV125.footprint(spec,state,offset,shift.x,shift.y,shift.z);}'],
 JAVA+'composite/CompositeSpec.java':[
  '        if(ImportedCollisionV125.supports(this))return ImportedCollisionV125.footprint(this,state,mountY,0,0,0);',
  '        if(ImportedCollisionV125.supports(this))return ImportedCollisionV125.physicalFootprint(this,state,mountY,shiftX,shiftY,shiftZ);']}
NEW_PRODUCTION={JAVA+'composite/ImportedStyleRetirements.java',JAVA+'composite/ImportedCollisionV125.java',
 RES+'bloodborne_dw/imported-style-retirements.json',RES+'bloodborne_dw/imported-collision-v125.json'}
TEST_FILES={'src/gametest/resources/fabric.mod.json','src/gametest/java/dev/dreamwalker/bloodbornedw/gametest/RemainingTransferGameTests.java'}
BUILD_FILES={'gradle.properties'}

def read(path):return json.loads(path.read_text(encoding='utf-8-sig'))
def sha(data):return hashlib.sha256(data).hexdigest()
def baseline_bytes(path):
 return subprocess.check_output(['git','show',BASELINE+':'+PREFIX+path],cwd=REPO)
def baseline_json(path):return json.loads(baseline_bytes(path))
def refs(value):
 if isinstance(value,dict):
  result=[v for k,v in value.items() if k in ('model','altModel') and isinstance(v,str)]
  for v in value.values():result.extend(refs(v))
  return result
 if isinstance(value,list):return [r for v in value for r in refs(v)]
 return []

def run():
 failures=[];counts=Counter();notes=[]
 def check(condition,code,detail=None):
  if not condition:failures.append({'code':code,'detail':detail})
 def same_top(before,after):return {k:v for k,v in before.items() if k!='variants'}=={k:v for k,v in after.items() if k!='variants'}
 proposal=read(DOC/'citycandidate/extra-canonical-city-poses.json')
 planned={g['primaryRegistryId'].split(':')[1]:g for g in proposal['groups']}
 check(len(planned)==16 and sum(g['newPoseCount'] for g in planned.values())==94,'POSE_PROPOSAL_COHORT')
 descriptor_paths={COMPOSITE+p+'.json' for p in planned}
 changed=subprocess.check_output(['git','diff',BASELINE,'--name-only','--','bloodborne complete'],cwd=REPO).decode().splitlines()
 changed=[p.removeprefix(PREFIX) for p in changed]
 allowed=set(HOOK_LINES)|descriptor_paths|TEST_FILES|BUILD_FILES|NEW_PRODUCTION|{'src/gametest/java/dev/dreamwalker/bloodbornedw/gametest/ImportedCollisionV125GameTests.java',RES+'bloodborne_dw/remaining-catalogue.json',RES+'bloodborne_dw/imported-type-aliases.json'}
 for p in changed:
  if p.startswith('src/') or p in BUILD_FILES:
   check(p in allowed,'UNAUTHORIZED_BASELINE_SOURCE_CHANGE',p)
 untracked=subprocess.check_output(['git','ls-files','--others','--exclude-standard','--','bloodborne complete/src'],cwd=REPO).decode().splitlines()
 for p in untracked:
  p=p.removeprefix(PREFIX)
  check(p in NEW_PRODUCTION or p=='src/gametest/java/dev/dreamwalker/bloodbornedw/gametest/ImportedCollisionV125GameTests.java','UNAUTHORIZED_NEW_PRODUCTION_SOURCE',p)
 for path,lines in HOOK_LINES.items():
  actual=(ROOT/path).read_text(encoding='utf-8-sig')
  for line in lines:
   check(actual.count(line)==1,'NEW_ONLY_GUARD_COUNT',{'path':path,'guard':line})
   actual=actual.replace(line+'\n','')
  check(actual==baseline_bytes(path).decode('utf8').replace('\r\n','\n'),'SHARED_CODE_CHANGED_BEYOND_EXACT_GUARDS',path)
  counts['exactGuardedSharedSourceFiles']+=1;counts['exactGuardedInsertionSites']+=len(lines)
 # Every existing NEW art state is byte-for-byte equivalent as parsed JSON;
 # only the reviewed source single-cell combinations can be appended.
 before_cat=baseline_json(RES+'bloodborne_dw/remaining-catalogue.json');after_cat=read(ROOT/RES/'bloodborne_dw/remaining-catalogue.json')
 before_objects={o['path']:o for o in before_cat['objects']};after_objects={o['path']:o for o in after_cat['objects']}
 check(before_objects.keys()==after_objects.keys() and len(after_objects)==1045,'REGISTERED_NEW_IDS_CHANGED')
 for path,g in planned.items():
  before=baseline_json(COMPOSITE+path+'.json');after=read(ROOT/COMPOSITE/(path+'.json'));old=len(before['variants'])
  check(sha(baseline_bytes(COMPOSITE+path+'.json'))==g['existingDescriptorSha256'],'PROPOSAL_BASELINE_DESCRIPTOR_SHA',path)
  check(old==g['existingPoseCount'] and len(after['variants'])==g['proposedTotalPoseCount'],'APPEND_POSE_COUNT',path)
  check(same_top(before,after) and after['variants'][:old]==before['variants'],'OLD_POSE_OR_TOP_METADATA_CHANGED',path)
  for index,p in enumerate(g['poses'],old):
   v=after['variants'][index];offset=p['floorNormalizationOffsetUnits'];raw=p['sourceRawVisibleBoundsUnits']
   visible={'from':[raw[i]+offset[i] for i in range(3)],'to':[raw[i+3]+offset[i] for i in range(3)]}
   expected={'parts':p['proposedParts'],'collision':[visible] if before['variants'][0]['poses']['closed']['collision'] else [],'selection':[visible]}
   check(v=={'poses':{'closed':expected,'open':copy.deepcopy(expected)},'visibleBounds':visible,
      'sourceVariant':f'Сочетание исходной ячейки {index+1}','sourcePlacementOffset':offset,'localOrigin':offset,
      'sourceClosedApplications':p['canonicalSourceApplications'],'sourceOpenApplications':[],
      'sourceStateRefs':p['sourcePropertyStates'],'v125SourceSingleCell':True},'APPENDED_POSE_DIFFERS_REVIEWED_SOURCE',{'id':path,'pose':index})
   for model in refs(v['poses']):
    namespace,local=model.split(':',1)
    check((ROOT/RES/f'assets/{namespace}/models/{local}.json').is_file(),'APPENDED_MODEL_MISSING',model)
   counts['validatedNewSourceSingleCellPoses']+=1
  counts['appendOnlyDescriptors']+=1;counts['protectedExistingPosesOnAppendedDescriptors']+=old
 for path,before in before_objects.items():
  after=after_objects[path]
  if path not in planned:check(before==after,'NONAPPENDED_CATALOGUE_ROW_CHANGED',path)
  else:
   old=len(before['variants']);g=planned[path]
   check(same_top(before,after) and after['variants'][:old]==before['variants'],'EXISTING_CATALOGUE_POSE_CHANGED',path)
   for index,p in enumerate(g['poses'],old):
    check(after['variants'][index]=={'variant':index,'name':after['displayName']+f' · сочетание {index+1}',
      'sourceModels':[a['model'] for a in p['canonicalSourceApplications']]},'APPENDED_CATALOGUE_POSE',path)
 check({k:v for k,v in before_cat.items() if k!='objects'}=={k:v for k,v in after_cat.items() if k!='objects'},'CATALOGUE_TOP_METADATA_CHANGED')
 old_manifest=baseline_json(RES+'bloodborne_dw/imported-type-aliases.json');manifest=read(ROOT/RES/'bloodborne_dw/imported-type-aliases.json')
 expected=copy.deepcopy(old_manifest);groups={g['primaryRegistryId']:g for g in expected['groups']}
 for path,g in planned.items():
  registry='bloodborne_dw:'+path
  if registry not in groups and g['proposedTotalPoseCount']>16:
   row={'primaryRegistryId':registry,'primaryId':g['primaryId'],'members':[registry],'kind':'artwork','oneItem':True,
     'poseCount':g['proposedTotalPoseCount'],'reason':'V12.5 append-only exact one-source-cell conditional art bank, no merged registrations'}
   expected['groups'].append(row);groups[registry]=row
  if registry in groups:groups[registry]['poseCount']=g['proposedTotalPoseCount']
 check(manifest==expected,'EXISTING_ALIAS_MAPPINGS_OR_UNREVIEWED_GROUP_CHANGED')
 counts['preservedStateMappings']=len(manifest['mappings'])
 canonical={member:g['primaryRegistryId'] for g in manifest['groups'] for member in g['members']}
 primaries={r for r in ['bloodborne_dw:'+p for p in after_objects] if canonical.get(r,r)==r}
 retire=read(ROOT/RES/'bloodborne_dw/imported-style-retirements.json');decisions=read(DOC/'style-decisions.json')
 debug=read(ROOT/RES/'bloodborne_dw/debug_catalogue.json');old_debug=baseline_json(RES+'bloodborne_dw/debug_catalogue.json')
 check(debug==old_debug,'NUMBERS_OR_DEBUG_CATALOGUE_CHANGED')
 counts['protectedReservedNumbers']=len(debug['entries'])
 original=read(ROOT/'docs/v12.4-dedup/baseline-content.json')['originalRows'];protected={r['registryId'] for r in original}
 check(len(protected)==116 and set(retire['protectedOriginalRegistryIds'])==protected,'ORIGINAL_V12_STYLE_PROTECTION')
 byid={r['registryId']:r for r in debug['entries']};check(all(byid[r['registryId']]==r for r in original),'ORIGINAL_V12_CATALOGUE_ROWS_CHANGED')
 retired={r['registryId'] for r in retire['retirements']};blocked={r for r in primaries|set(canonical) if canonical.get(r,r) in retired}
 check(len(primaries)==572 and len(retired)==21 and len(primaries-retired)==551 and len(blocked)==30,'STYLE_WORKING_COUNTS')
 check(retired<=primaries and not blocked&protected,'STYLE_RETIREMENT_NOT_NEW_ONLY')
 check(all(set(row['aliases'])=={r for r in after_objects_as_ids(after_objects) if canonical.get(r,r)==row['registryId']} for row in retire['retirements']),'STYLE_ALIAS_CLOSURE')
 check(decisions['status']=='PASS_COMPLETE_572_TYPES' and len(decisions['rows'])==572 and
  {r['registryId'] for r in decisions['rows'] if r['decision'].startswith('RETIRE')}==retired and
  decisions['retirementMetadataSha256']==sha((ROOT/RES/'bloodborne_dw/imported-style-retirements.json').read_bytes()),'STYLE_DECISION_LEDGER_BINDING')
 counts.update({'registeredNewTypes':1045,'workingNewBefore':572,'retiredWorkingTypes':21,'workingNewAfter':551,'hiddenMainAndAliasRegistrations':30,'protectedOriginalCatalogueRows':116})
 frozen=read(ROOT/'docs/v12.4-dedup/baseline-protected-hashes.json')['files']
 import check_v124_preservation as previous
 for path,digest in frozen.items():
  if path in previous.ALLOW:continue
  check((ROOT/path).is_file() and sha((ROOT/path).read_bytes())==digest,'FROZEN_ORIGINAL_V12_RESOURCE_OR_SOURCE_CHANGED',path)
  counts['unchangedFrozenV12Files']+=1
 contract='docs/COLLISION_IMPORT_HANDOFF.md'
 check((ROOT/contract).read_bytes()==baseline_bytes(contract),'AUTHOR_COLLISION_CONTRACT_CHANGED')
 check(sha((ROOT/RES/'bloodborne_dw/imported-stair-legacy-art.json').read_bytes())==sha(baseline_bytes(RES+'bloodborne_dw/imported-stair-legacy-art.json')),'PRESERVED_STAIR_APPEARANCE_KEYS_CHANGED')
 physics=read(ROOT/RES/'bloodborne_dw/imported-collision-v125.json')
 check(set(physics['objects'])==after_objects_as_ids(after_objects) and physics.get('authorCollisionExport')=='NOT_SUPPLIED_NOT_APPLIED','PHYSICS_EXACT_NEW_COHORT')
 check(physics['maxBoxesPerOrientation']==12 and physics['unitsPerBlock']==16,'PHYSICS_APPROVED_BUDGET')
 helper=(ROOT/JAVA/'composite/ImportedCollisionV125.java').read_text(encoding='utf8')
 check('return spec.simplePhysicalCollision && TABLES.objects.containsKey(spec.id.toString());' in helper,'PHYSICS_GUARD_INCOMPLETE')
 return {'schema':'dreamwalker-v125-independent-preservation-v1','status':'PASS' if not failures else 'FAIL',
  'baselineCommit':BASELINE,'counts':dict(counts),'changedTrackedSources':changed,'newProductionSources':untracked,
  'exactGuards':HOOK_LINES,'styleMetadataSha256':sha((ROOT/RES/'bloodborne_dw/imported-style-retirements.json').read_bytes()),
  'collisionMetadataSha256':sha((ROOT/RES/'bloodborne_dw/imported-collision-v125.json').read_bytes()),
  'collisionContractSha256':sha(baseline_bytes(contract)),'authorCollisionExport':'NOT_SUPPLIED_NOT_APPLIED',
  'failures':failures,'runtimeCompilationAndGameplay':'NOT_RUN_BY_READONLY_SOURCE_AUDIT'}

def after_objects_as_ids(objects):return {'bloodborne_dw:'+p for p in objects}
def main():
 try:result=run()
 except Exception:result={'status':'FAIL','baselineCommit':BASELINE,'failure':traceback.format_exc()}
 (DOC/'preservation-audit.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
 if result['status']!='PASS':raise SystemExit(1)
if __name__=='__main__':main()
