"""Read-only final combined JAR verification against immutable released V12.4."""
from pathlib import Path
from collections import Counter
import argparse,hashlib,json,subprocess,traceback,zipfile
import check_v125_preservation as preservation

ROOT=preservation.ROOT;DOC=preservation.DOC
BASELINE_JAR=ROOT.parent/'releases/Dreamwalker-BB/v12.4-dedup/dreamwalker-bb-fabric-1.20.1-0.1.0-v12.4-dedup.jar'
BASELINE_SHA='4c9e202cff6ae5acd17386a30547726948a0d0d9babf4e529c84ec5c51b0c640'
VANILLA=Path('C:/Users/vakir/.gradle/caches/fabric-loom/1.20.1/minecraft-client.jar')
NEW_METADATA={'bloodborne_dw/imported-style-retirements.json','bloodborne_dw/imported-collision-v125.json'}
NEW_CLASSES={'dev/dreamwalker/bloodbornedw/composite/ImportedStyleRetirements','dev/dreamwalker/bloodbornedw/composite/ImportedCollisionV125'}
HOOK_CLASSES={p.split('/java/',1)[1][:-5] for p in preservation.HOOK_LINES}
SOURCE_RESOURCE_ROOTS=['src/architecture/resources','src/rp/resources']

def sha(data):return hashlib.sha256(data).hexdigest()
def read(path):return json.loads(path.read_text(encoding='utf-8-sig'))
def canonical(ref):return ref if ':' in ref else 'minecraft:'+ref
def model_path(ref):
 namespace,local=canonical(ref).split(':',1);return f'assets/{namespace}/models/{local}.json'
def texture_path(ref):
 namespace,local=canonical(ref).split(':',1);return f'assets/{namespace}/textures/{local}.png'
def references(value,keys=('model','altModel')):
 if isinstance(value,dict):
  return [v for k,v in value.items() if k in keys and isinstance(v,str)]+[r for v in value.values() for r in references(v,keys)]
 if isinstance(value,list):return [r for v in value for r in references(v,keys)]
 return []

def audit(args):
 final_path=args.jar.resolve();baseline_path=args.baseline_jar.resolve();counts=Counter();failures=[];notes=[]
 def check(condition,code,detail=None):
  if not condition:failures.append({'code':code,'detail':detail})
 before_sha=sha(baseline_path.read_bytes());final_sha=sha(final_path.read_bytes())
 check(before_sha==BASELINE_SHA,'BASELINE_JAR_SHA',before_sha)
 check(final_path!=baseline_path,'BASELINE_JAR_OVERWRITE')
 source_audit=preservation.run()
 check(source_audit['status']=='PASS','INDEPENDENT_SOURCE_PRESERVATION',source_audit.get('failures',source_audit))
 with zipfile.ZipFile(final_path) as current,zipfile.ZipFile(baseline_path) as baseline,zipfile.ZipFile(VANILLA) as vanilla:
  for label,archive in [('final',current),('baseline',baseline)]:
   duplicates=[p for p,n in Counter(archive.namelist()).items() if n>1]
   check(not duplicates,'DUPLICATE_ZIP_ENTRIES',{'jar':label,'entries':duplicates})
   check(archive.testzip() is None,'ZIP_CRC',label)
  final={p:current.read(p) for p in current.namelist() if not p.endswith('/')}
  old={p:baseline.read(p) for p in baseline.namelist() if not p.endswith('/')}
  proposal=read(DOC/'citycandidate/extra-canonical-city-poses.json')
  appended={f"bloodborne_dw/composite/{g['primaryRegistryId'].split(':')[1]}.json" for g in proposal['groups']}
  metadata_changed={'bloodborne_dw/remaining-catalogue.json','bloodborne_dw/imported-type-aliases.json'}
  changed=[]
  for path,data in old.items():
   if path not in final:check(False,'REMOVED_BASELINE_ENTRY',path);continue
   if final[path]==data:
    if path.startswith('assets/') and '/models/' in path:counts['unchangedBaselineModels']+=1
    if path.startswith('assets/') and '/textures/' in path:counts['unchangedBaselineTextures']+=1
    if path.startswith('assets/minecraft/'):counts['unchangedVanillaNamespaceAssets']+=1
    if path.startswith('bloodborne_dw/composite/'):counts['unchangedBaselineDescriptors']+=1
    if path.endswith('.class'):counts['unchangedBaselineClasses']+=1
    continue
   changed.append(path)
   if path.startswith('assets/'):check(False,'BASELINE_ART_ASSET_CHANGED',path)
   elif path in appended or path in metadata_changed:counts['approvedChangedResourceEntries']+=1
   elif path.endswith('.class'):
    stem=path[:-6].split('$',1)[0];check(stem in HOOK_CLASSES,'UNDOCUMENTED_CHANGED_BASELINE_CLASS',path)
    if stem in HOOK_CLASSES:counts['guardedSharedHookClassEntries']+=1
   elif path=='fabric.mod.json':
    before=json.loads(data);after=json.loads(final[path]);version=after.pop('version',None);before.pop('version',None)
    check(before==after and isinstance(version,str) and bool(version),'MOD_METADATA_CHANGED_BEYOND_VERSION',path)
    notes.append({'code':'MOD_VERSION_ONLY','version':version})
   elif path=='META-INF/MANIFEST.MF':
    def without_version(raw):return [line for line in raw.decode().splitlines() if not line.startswith(('Implementation-Version:','Specification-Version:'))]
    check(without_version(data)==without_version(final[path]),'MANIFEST_CHANGED_BEYOND_VERSION',path)
   else:check(False,'UNEXPECTED_CHANGED_RESOURCE',path)
  for path in sorted(final.keys()-old.keys()):
   if path in NEW_METADATA:counts['approvedNewMetadata']+=1
   elif path.endswith('.class'):
    stem=path[:-6].split('$',1)[0];check(stem in NEW_CLASSES or '$' in path and stem in HOOK_CLASSES,'UNDOCUMENTED_NEW_CLASS',path)
    counts['newApprovedProductionClassEntries']+=1
   else:check(False,'UNDOCUMENTED_NEW_RESOURCE',path)
  forbidden=('v124qa/','v125qa/','v123qa/','gametest/','gallery/','v124-qa-input','v125-qa-input','v124-baseline-content','v125-baseline-content','v124-visual-comparison')
  leaked=[p for p in final if any(s in p for s in forbidden)]
  check(not leaked,'QA_RESOURCES_IN_PRODUCTION',leaked)
  # The artifact must match each reviewed current production source resource.
  for resource_root in SOURCE_RESOURCE_ROOTS:
   for file in (ROOT/resource_root).rglob('*'):
    if not file.is_file():continue
    path=file.relative_to(ROOT/resource_root).as_posix()
    if path=='fabric.mod.json':continue
    check(path in final,'SOURCE_RESOURCE_NOT_PACKAGED',str(file.relative_to(ROOT)))
    if path not in final:continue
    raw=file.read_bytes()
    if final[path]!=raw:
     loom_ok=False
     if path=='bloodborne_dw.composite.mixins.json':
      packed=json.loads(final[path]);source=json.loads(raw);refmap=packed.pop('refmap',None)
      if packed==source and refmap==json.loads(old[path]).get('refmap') and refmap in final and final[refmap]==old.get(refmap):
       mappings=json.loads(final[refmap]);package=source['package'].replace('.','/')
       declared={package+'/'+v for v in source.get('mixins',[])+source.get('client',[])+source.get('server',[])}
       actual=set(mappings.get('mappings',{}))
       loom_ok=bool(actual) and actual<=declared and all(v+'.class' in final for v in declared)
       if loom_ok:counts['verifiedUnchangedLoomRefmapTransforms']+=1
     check(loom_ok,'JAR_DIFFERS_CURRENT_RESOURCE',path)
    counts['reviewedSourceResourcesPackaged']+=1
  debug_path='bloodborne_dw/debug_catalogue.json'
  check(final[debug_path]==old[debug_path],'NUMBER_RESERVATIONS_OR_DEBUG_CATALOGUE_CHANGED')
  check(final['bloodborne_dw/imported-stair-legacy-art.json']==old['bloodborne_dw/imported-stair-legacy-art.json'],'LEGACY_STAIR_APPEARANCE_KEYS_CHANGED')
  manifest=json.loads(final['bloodborne_dw/imported-type-aliases.json']);before_manifest=json.loads(old['bloodborne_dw/imported-type-aliases.json'])
  check(manifest['mappings']==before_manifest['mappings'],'SAVED_STATE_MAPPING_CHANGED')
  styles=json.loads(final['bloodborne_dw/imported-style-retirements.json']);catalogue=json.loads(final['bloodborne_dw/remaining-catalogue.json'])
  registered={'bloodborne_dw:'+r['path'] for r in catalogue['objects']}
  canonical_types={member:g['primaryRegistryId'] for g in manifest['groups'] for member in g['members']}
  primaries={r for r in registered if canonical_types.get(r,r)==r};retired={r['registryId'] for r in styles['retirements']}
  check(len(registered)==1045 and len(primaries)==572 and len(retired)==21 and len(primaries-retired)==551,'PACKAGED_STYLE_COUNTS')
  check(not set(styles['protectedOriginalRegistryIds'])&retired,'PACKAGED_V12_RETIREMENT')
  counts.update({'registeredNewIds':len(registered),'workingNewTypesBefore':len(primaries),'retiredNewWorkingTypes':len(retired),'workingNewTypesAfter':len(primaries-retired),'preservedSavedStateMappings':len(manifest['mappings'])})
  # Resolve real rendered pose parts (not provenance sourceApplications).
  # Parent inheritance and #texture bindings are checked recursively in final
  # JAR and vanilla. Existing unused aliases are recorded only for byte-equal
  # baseline models; visible face failures never receive that exception.
  resolved={};resolving=set();reachable=set();textures=set();unused=[];templates=[]
  vanilla_names=set(vanilla.namelist())
  def bytes_for(path):
   if path in final:return final[path]
   if path in vanilla_names:return vanilla.read(path)
   raise ValueError('Missing dependency '+path)
  def resolved_model(ref):
   ref=canonical(ref)
   if ref in resolved:return resolved[ref]
   if ref.startswith('minecraft:builtin/'):
    # ModelLoader provides generated/entity markers in memory, without a
    # corresponding assets/minecraft/models/builtin/*.json file.
    resolved[ref]={'textures':{},'elements':[],'virtualBuiltin':ref}
    return resolved[ref]
   if ref in resolving:raise ValueError('Cyclic model parent '+ref)
   resolving.add(ref)
   try:
    model=json.loads(bytes_for(model_path(ref)));base={};parent=model.get('parent')
    if parent and not canonical(parent).startswith('minecraft:builtin/'):base=resolved_model(parent)
    result=dict(base);result.update(model);result['textures']={**base.get('textures',{}),**model.get('textures',{})}
    resolved[ref]=result;return result
   finally:resolving.discard(ref)
  def texture(ref,bindings):
   seen=set()
   while ref.startswith('#'):
    if ref in seen:raise ValueError('Cyclic texture alias '+ref)
    seen.add(ref)
    if ref[1:] not in bindings:raise ValueError('Unbound texture alias '+ref)
    ref=bindings[ref[1:]]
   path=texture_path(ref);bytes_for(path);textures.add(path)
  def concrete(ref):
   ref=canonical(ref)
   if ref in reachable:return
   reachable.add(ref)
   try:
    model=resolved_model(ref);bindings=model.get('textures',{})
    for element in model.get('elements',[]):
     for face in element.get('faces',{}).values():texture(face['texture'],bindings)
    for key,value in bindings.items():
     try:texture(value,bindings)
     except ValueError as error:
      path=model_path(ref)
      if 'Unbound texture alias' not in str(error) or path not in old or final.get(path)!=old[path]:raise
      unused.append({'model':ref,'binding':key,'value':value,'reason':str(error),'status':'UNCHANGED_BASELINE_UNUSED_INPUT_DEFECT','visibleFaces':'PASS','baselineModelSha256':sha(old[path])})
   except Exception as error:check(False,'VISIBLE_MODEL_DEPENDENCY_CLOSURE',{'model':ref,'reason':str(error)})
  roots=references(json.loads(final['bloodborne_dw/imported-stair-legacy-art.json']))
  for path,raw in final.items():
   if path.startswith('bloodborne_dw/composite/') and path.endswith('.json'):
    for variant in json.loads(raw)['variants']:roots.extend(references(variant['poses']))
   elif path.startswith('assets/') and '/models/item/' in path and path.endswith('.json'):
    _,namespace,_,*parts=path.split('/');roots.append(namespace+':'+ '/'.join(parts)[:-5])
   elif path.startswith('assets/') and '/blockstates/' in path and path.endswith('.json'):roots.extend(references(json.loads(raw),('model',)))
  for ref in sorted(set(roots)):concrete(ref)
  for path in final:
   if not path.startswith('assets/') or '/models/' not in path or not path.endswith('.json'):continue
   _,namespace,_,*parts=path.split('/');ref=namespace+':'+ '/'.join(parts)[:-5]
   if ref in reachable:continue
   try:
    model=resolved_model(ref);bindings=model.get('textures',{})
    for value in bindings.values():
     if not value.startswith('#'):texture(value,bindings)
    for element in model.get('elements',[]):
     for face in element.get('faces',{}).values():
      try:texture(face['texture'],bindings)
      except ValueError as error:
       if 'Unbound texture alias' in str(error):templates.append({'model':ref,'parameter':face['texture']})
       else:raise
   except Exception as error:check(False,'UNUSED_TEMPLATE_PARENT_OR_LITERAL_TEXTURE_CLOSURE',{'model':ref,'reason':str(error)})
  counts.update({'renderedModelRoots':len(reachable),'resolvedModelDependencies':len(resolved),'resolvedTextureDependencies':len(textures),
    'unchangedBaselineUnusedTextureInputDefects':len(unused),'unusedParameterizedTemplateFaces':len(templates)})
  check(sha(final_path.read_bytes())==final_sha and sha(baseline_path.read_bytes())==before_sha,'JAR_INPUT_MUTATED_DURING_AUDIT')
  return {'schema':'dreamwalker-v125-independent-final-resource-audit-v1','status':'PASS' if not failures else 'FAIL',
   'productionJar':str(final_path),'productionJarSha256':final_sha,'baselineJar':str(baseline_path),'baselineJarSha256':before_sha,
   'baselineCommit':preservation.BASELINE,'counts':dict(counts),'approvedChangedEntries':changed,'failures':failures,'notes':notes,
   'preservedUnusedTextureInputs':unused,'sourcePreservationStatus':source_audit['status'],'collisionContractSha256':source_audit['collisionContractSha256'],
   'qaClassesExcluded':not leaked,'authorCollisionExport':'NOT_SUPPLIED_NOT_APPLIED','nativeGameplay':'NOT_RUN_BY_READONLY_JAR_AUDIT'}

def main():
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--jar',type=Path,required=True)
 parser.add_argument('--baseline-jar',type=Path,default=BASELINE_JAR);parser.add_argument('--report',type=Path,default=DOC/'resource-audit.json')
 args=parser.parse_args()
 try:result=audit(args)
 except Exception:result={'status':'FAIL','productionJar':str(args.jar),'failure':traceback.format_exc()}
 args.report.parent.mkdir(parents=True,exist_ok=True);args.report.write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
 if result['status']!='PASS':raise SystemExit(1)
if __name__=='__main__':main()
