"""Finite post-activation function/physics inputs from frozen existing rows only."""
import collections,hashlib,json,shutil,sqlite3,sys,zipfile
from pathlib import Path
from export_cells import canonical,state_key,read_lines
PROJECT=Path(__file__).resolve().parents[2];ROOT=PROJECT.parent
BASE=Path(r'C:\Users\vakir\.codex\worktrees\3f07\DW-city-mesh-20261018\deliverables\block-fixes-2-20261020\game\dreamwalker-bb-materials.1-streaming.7-blocks.2.jar')
BASE_SHA='40cecfb7e5cf783a762e350fc1864518c39abff177f6f025cb94892a181fbf51'
RAW='42fd57f6667fc38bea7c7672e21bcf90894471d18b3cbe0a6b3e317d5c2f36d5'
def read(path):return json.loads(Path(path).read_text(encoding='utf-8-sig'))
def sha(path):return hashlib.sha256(Path(path).read_bytes()).hexdigest()
def write(path,value):Path(path).write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
def require(test,message):
    if not test:raise ValueError(message)
def main():
    context=ROOT/'research/native-grid-plan/context-extension';folder=context/'activation-supplement';folder.mkdir(exist_ok=True)
    source=context/'final-state-coverage.json';frozen=folder/'final-coverage-before.json'
    if not frozen.exists():shutil.copy2(source,frozen)
    coverage=read(frozen);priorpath=ROOT/'research/native-functional-bases/functional-carrier-extension-frozen.json';prior=read(priorpath)
    raw=ROOT/'research/native-grid-plan/compositions.jsonl';require(sha(raw)==RAW==coverage['compositionsSha256'],'Actual composition generation changed')
    caps_binding=prior['sourceCapabilities'];require(sha(caps_binding['path'])==caps_binding['sha256'],'Registry capability evidence changed')
    caps={r['registryId']:r for r in read(caps_binding['path'])['states']};require(sha(BASE)==BASE_SHA,'Original JAR changed')
    with zipfile.ZipFile(BASE) as jar:
        compact_raw=jar.read('bloodborne_dw/imported-compact-v127.json');compact={r['registryId']:r for r in json.loads(compact_raw)['types']}
        legacy_raw=jar.read('bloodborne_dw/static-architecture-v1274.json');legacy=json.loads(legacy_raw)
        class_proofs={name:hashlib.sha256(jar.read('dev/dreamwalker/bloodbornedw/composite/'+name+'.class')).hexdigest() for name in ('StaticArchitectureBlock','StaticArchitectureCatalog$Pose','ImportedCompactBlock','ImportedArtworkBlock')}
    union={state_key(r['state']):r for r in prior['states']};added=[]
    for state in coverage['missingFunctionDecisions']:
        key=state_key(state);cap=caps[state['registryId']];clazz=cap['className'];p={**cap['defaultProperties'],**state['properties']}
        row={'state':state,'className':clazz,'decision':'static_base','postActivationProof':True,'actualDefaultCompletedProperties':p}
        if clazz=='dev.dreamwalker.bloodbornedw.composite.StaticArchitectureBlock':
            require(not cap['anyStateHasBlockEntity'] and p['waterlogged']=='false','Static recipe state is wet or owns a BE')
            index=int(state['registryId'].rsplit('_',1)[1])*128+int(p['part']);require(index<len(legacy['recipes']),'Unregistered exact static recipe')
            row.update(reason='Exact dry no-BE StaticArchitectureBlock recipe; same reviewed geometric class and authorized independent-cell lifecycle as the earlier finite static-state proof.',recipeIndex=index,recipeKey=legacy['recipes'][index]['key'],recipeSha256=hashlib.sha256(canonical(legacy['recipes'][index]).encode()).hexdigest())
        elif clazz.startswith('dev.dreamwalker.bloodbornedw.composite.ImportedCompactBlock'):
            require(p['compact']=='true' and int(p['rotation']) in (0,2,4,6),'Not an actual compact even-yaw state')
            variant=int(p['variant'])+16*int(p.get('art_bank',0));definition=compact[state['registryId']]
            require(variant in definition['poses'] and len(definition['shapes'][str(variant)])==8,'Exact compact variant not authorized by immutable finite metadata')
            row.update(reason='Exact ImportedCompactBlock compact=true/even-yaw variant present in immutable finite compact metadata. Class bypasses owned BE route for this exact state; no vanilla gameplay callbacks inherited from artwork aliases.',compactVariant=variant,compactDefinitionSha256=hashlib.sha256(canonical(definition).encode()).hexdigest())
        else:raise ValueError('New activated actual function class not reviewed: '+clazz)
        require(key not in union,'A reported missing function state already has an exact decision');union[key]=row;added.append(row)
    needed=read(ROOT/'research/native-grid-plan/native-states-needed.json')['nativeStates'];store=sqlite3.connect('file:'+(ROOT/'research/fragments.sqlite').as_posix()+'?mode=ro',uri=True)
    states={state_key(s):{'registryId':s['registryId'],'properties':s.get('properties',{})} for s in needed};comps=collections.defaultdict(list)
    for row in read_lines(raw):
        if row.get('baseState'):comps[state_key(row['baseState'])].append(row)
    missing=[];physics=[];visual=[];routes=[]
    for key,state in sorted(states.items()):
        if store.execute('SELECT 1 FROM native WHERE state=?',(key,)).fetchone():continue
        missing.append(state);clazz=caps[state['registryId']]['className'];rows=comps[key];require(rows,'Missing native state does not bind an actual composition')
        rowproof=[]
        for row in rows:
            refs=[]
            for ref in row.get('substrateFragments',[]):
                source_pose=store.execute('SELECT source FROM poses WHERE key=?',(ref['pose'],)).fetchone();fragment=store.execute('SELECT data FROM fragments WHERE pose=? AND x=? AND y=? AND z=?',(ref['pose'],*ref['cell'])).fetchone();require(source_pose and fragment,'Missing actual source-origin visual fragment')
                source_pose=json.loads(source_pose[0]);fragment=json.loads(fragment[0]);refs.append({'pose':ref['pose'],'cell':ref['cell'],'sourcePose':source_pose['sourcePose'],'quads':len(fragment['quads'])})
            rowproof.append({'key':row['key'],'baseVisualMode':row.get('baseVisualMode','native'),'substrateFragments':refs,'nativeFragmentRefs':row.get('substrateNativeFragments',[])})
        if clazz=='dev.dreamwalker.bloodbornedw.composite.StaticArchitectureBlock':
            require(all(r.get('baseVisualMode')=='substrateFragments' and (r.get('substrateFragments') or r.get('substrateNativeFragments')) for r in rows),'Physics-only cannot close a native visual route')
            physics.append({**state,'physicsOnly':True});route='physicsOnly; explicit proven static source-origin visual transfer remains intact'
        else:
            require(clazz.startswith('dev.dreamwalker.bloodbornedw.composite.ImportedCompactBlock'),'Unreviewed missing native renderer class')
            visual.append({**state,'physicsOnly':False});route='full actual native model required; current foreign substrate alone is not proof of own compact geometry'
        routes.append({'state':state,'className':clazz,'requiredExporter':route,'compositionRoutes':rowproof})
    gate={'schemaVersion':1,'status':'PASS','mode':'FINITE_POST_ACTIVATION_FUNCTION_SUPPLEMENT','states':[union[k] for k in sorted(union)],'baseGate':{'path':str(priorpath),'sha256':sha(priorpath),'states':len(prior['states'])},'postActivationCoverage':{'path':str(frozen),'sha256':sha(frozen),'compositionsSha256':RAW},'sourceBinding':prior['sourceBinding'],'sourceJarSha256':BASE_SHA,'sourceCapabilities':caps_binding,'addedStates':len(added),'addedDecisions':added,'primaryClassHashes':class_proofs,'compactResourceSha256':hashlib.sha256(compact_raw).hexdigest(),'staticResourceSha256':hashlib.sha256(legacy_raw).hexdigest(),'mapWrites':0,'worldFilesRead':0,'gameProcessesStarted':0,'databaseWrites':0}
    gate['sourceJar']=str(BASE)
    write(folder/'function-gate.json',gate)
    for name,natives in (('physics-request.json',physics),('visual-request.json',visual),('combined-asset-request.json',visual+physics)):
        write(folder/name,{'schemaVersion':1,'poses':[],'nativeStates':natives,'states':natives,'sourceJarSha256':BASE_SHA,'rawCompositionsSha256':RAW,'sourceBinding':prior['sourceBinding'],'finiteActualPostActivationCoverageSha256':sha(frozen)})
    report={'status':'FINITE_INPUTS_PREPARED_NOT_EXPORTED','actualNeededStates':len(states),'newFunctionStates':len(added),'functionGateStates':len(union),'genuinelyMissingNativeStates':len(missing),'physicsOnlyStates':len(physics),'fullVisualStates':len(visual),'rawCompositionsSha256':RAW,'routeProofs':routes,'outputs':{name:sha(folder/name) for name in ('function-gate.json','physics-request.json','visual-request.json','combined-asset-request.json')},'mapReads':0,'mapWrites':0,'databaseWrites':0,'exportsLaunched':0,'catalogueAppendBlocked':True}
    write(folder/'preparation.json',report);print(json.dumps({k:v for k,v in report.items() if k!='routeProofs'}))
if __name__=='__main__':main()
