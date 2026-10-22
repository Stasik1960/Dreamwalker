"""Address only current finite functional-retention cells; no plan/map writes."""
import argparse,collections,hashlib,json,sqlite3,subprocess,sys,zipfile
from pathlib import Path
PROJECT=Path(__file__).resolve().parents[2];ROOT=PROJECT.parent
sys.path.insert(0,str(PROJECT/'tools'))
from world_io import Tag,compound,decode_nbt
from export_cells import canonical,state_key
BASE=Path(r'C:\Users\vakir\.codex\worktrees\3f07\DW-city-mesh-20261018\deliverables\block-fixes-2-20261020\game\dreamwalker-bb-materials.1-streaming.7-blocks.2.jar')
BASE_SHA='40cecfb7e5cf783a762e350fc1864518c39abff177f6f025cb94892a181fbf51'
def read(path):return json.loads(Path(path).read_text(encoding='utf-8-sig'))
def sha(path):return hashlib.sha256(Path(path).read_bytes()).hexdigest()
def require(test,message):
    if not test:raise ValueError(message)
def ro(path):
    db=sqlite3.connect('file:'+Path(path).absolute().as_posix()+'?mode=ro',uri=True);db.row_factory=sqlite3.Row;return db
def write(path,value):Path(path).write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--freeze-existing',type=Path);parser.add_argument('--frozen-preflight',type=Path);parser.add_argument('--out',type=Path);args=parser.parse_args()
    if args.freeze_existing:
        require(args.frozen_preflight and args.out,'Frozen input path and new output are required')
        result=read(args.freeze_existing);require(sha(args.frozen_preflight)==result['functionalPreflight']['sha256'],'Frozen source preflight bytes differ from reviewed finite cells')
        require(args.out!=args.freeze_existing and not args.out.exists(),'Preserve the original possibly imported report; write a fresh proof binding')
        result['functionalPreflight']['path']=str(args.frozen_preflight.absolute());result['priorReport']={'path':str(args.freeze_existing.absolute()),'sha256':sha(args.freeze_existing)}
        write(args.out,result);print(json.dumps({'status':'PASS_FROZEN_INPUT_BINDING','report':str(args.out),'sha256':sha(args.out),'sourcePreflightSha256':result['functionalPreflight']['sha256'],'states':len(result['states'])}));return
    out=ROOT/'research/native-functional-bases';prepath=ROOT/'research/native-grid-plan/functional-guest-preflight.json';pre=read(prepath);pre_sha=sha(prepath);original=read(out/'gate.json');evidence=read(out/'input-evidence.json')
    capability=evidence['sourceCapabilities'];require(sha(capability['path'])==capability['sha256'],'Actual registry capability binding changed')
    caps={row['registryId']:row for row in read(capability['path'])['states']};require(sha(BASE)==BASE_SHA,'Immutable baseline JAR changed')
    plan=ro(ROOT/'research/native-grid-plan/plan.sqlite');plan.execute('BEGIN');old=ro(Path(original['sourceBinding']['oldPlan'])/'plan.sqlite');frags=ro(ROOT/'research/fragments.sqlite')
    grouped=collections.defaultdict(list)
    for c in pre['conflicts']:
        require(c['specificReason']=='actual_native_state_missing_function_proof','Additional functional reason requires a separate diagnosis')
        grouped[state_key(c)].append(c)
    code=out/'carrier-extension-bytecode';code.mkdir(exist_ok=True);primary=[]
    with zipfile.ZipFile(BASE) as jar:
        for name in ('StaticArchitectureBlock','StaticArchitectureCatalog','StaticArchitectureCatalog$Pose','StaticArchitectureRuntime','CompositeCellBlock'):
            cls='dev.dreamwalker.bloodbornedw.composite.'+name;entry=cls.replace('.','/')+'.class';target=code/(name+'.txt')
            process=subprocess.run([r'C:\Users\vakir\Downloads\jdk-21.0.10-full\bin\javap.exe','-classpath',str(BASE),'-c','-p',cls],capture_output=True,text=True)
            require(process.returncode==0,'Primary bytecode could not be read: '+cls)
            target.write_text(process.stdout,encoding='utf-8');primary.append({'className':cls,'jarClassSha256':hashlib.sha256(jar.read(entry)).hexdigest(),'bytecodeFile':str(target),'bytecodeSha256':sha(target)})
        recipes_raw=jar.read('bloodborne_dw/static-architecture-v1274.json');legacy=json.loads(recipes_raw)
    new=[];missing=[];bridges=[];cached={};chunk_proof=[]
    static_cells=host_cells=covered=0
    for key,occurrences in sorted(grouped.items()):
        state=json.loads(key);cap=caps[state['registryId']];clazz=cap['className'];effective={**cap['defaultProperties'],**state['properties']}
        row={'state':state,'className':clazz,'cells':len(occurrences),'witnesses':[v['cell'] for v in occurrences[:8]],'actualDefaultCompletedProperties':effective}
        if clazz=='dev.dreamwalker.bloodbornedw.composite.StaticArchitectureBlock':
            require(not cap['anyStateHasBlockEntity'] and not cap['defaultHasBlockEntity'],'Static class unexpectedly owns a BE')
            require(effective['waterlogged']=='false','Wet StaticArchitecture state needs separate actual fluid/interaction proof')
            bank=int(state['registryId'].rsplit('_',1)[1]);part=int(effective['part']);index=bank*128+part
            require(0<=part<128 and index<len(legacy['recipes']),'Actual static recipe index not present in immutable resource')
            recipe=legacy['recipes'][index];static_cells+=len(occurrences)
            row.update(decision='static_base',reason='Exact actual class has no BE and uses finite recipe shapes/optics. Its only architecture interaction is existing finite component break/cleanup; replacing that architecture lifecycle with independent native cells is within the authorized migration. Dry actual state; no source door/climb/support/redstone/container behavior.',recipeIndex=index,recipeKey=recipe['key'],recipeSha256=hashlib.sha256(canonical(recipe).encode()).hexdigest(),legacyComponentLifecycleChangedByAuthorizedMigration=True,sourceRecipeFragments=recipe['fragments'])
            physical=frags.execute('SELECT data FROM native WHERE state=?',(key,)).fetchone()
            if physical:
                value=json.loads(physical[0]);require(value.get('fluid') is None,'Actual exported static state is unexpectedly wet')
                covered+=1;row['physicsCoverage']={'status':'EXACT_ACTUAL_NATIVE_EXPORT_PRESENT','recordSha256':hashlib.sha256(canonical(value).encode()).hexdigest(),'collision':value['collision'],'outline':value['outline'],'optics':value['optics'],'nativeVisualComplete':value.get('nativeVisualComplete',True),'visualRoute':'Existing exact SourcePose origin transfer; never use physicsOnly quads as full visuals'}
            else:
                row['physicsCoverage']={'status':'MISSING_EXACT_NATIVE_EXPORT','notSubstitutedByRecipeGuess':True};missing.append({**state,'physicsOnly':True})
        elif clazz=='dev.dreamwalker.bloodbornedw.composite.CompositeCellBlock':
            require(cap['anyStateHasBlockEntity'] and cap['defaultHasBlockEntity'],'Concrete helper BE capability missing');host_cells+=len(occurrences)
            row.update(decision='bridge_host_only',reason='Exact CompositeCellBlock creates CompositeBlockEntity and delegates use/pick/shapes to existing owned contributions. This state is never authorized as a passive native base; existing BE/owners/payload must survive through a proved bridge or the cell remains locally original.',physicsCoverage={'status':'OWNED_CONTRIBUTIONS_NOT_A_NATIVE_STATE_EXPORT','nativeExportForbiddenByHasBlockEntity':True})
            for occurrence in occurrences:
                pos=occurrence['cell'];chunk=(pos[0]//16,pos[2]//16)
                if chunk not in cached:
                    r=old.execute("SELECT region_path,region_hash,compression,payload FROM chunk_cache WHERE dim='.' AND x=? AND z=?",chunk).fetchone();require(r is not None,'Missing addressed cached helper chunk')
                    root=compound(decode_nbt(r['payload'],compressed={1:'gzip',2:'zlib',3:None}[r['compression']]).root)
                    cached[chunk]={tuple(compound(be)[k].value for k in ('x','y','z')):compound(be) for be in root.get('block_entities',Tag(9,[],10)).value}
                    chunk_proof.append({'chunk':list(chunk),'region':r['region_path'],'regionSha256':r['region_hash'],'cachedPayloadSha256':hashlib.sha256(r['payload']).hexdigest()})
                be=cached[chunk].get(tuple(pos));planned=plan.execute("SELECT host_state,retained_resident FROM bridge_cells WHERE dim=? AND x=? AND y=? AND z=?",(occurrence['dimension'],*pos)).fetchone()
                bridges.append({'cell':pos,'savedBE':be is not None,'savedBEId':be.get('id',Tag(8,'')).value if be else None,'savedBEKeys':sorted(be) if be else [],'existingPlannedBridge':dict(planned) if planned else None,'functionProofAloneAuthorizesRemoval':False})
        else:raise ValueError('Unreviewed actual class in exact retention list: '+clazz)
        new.append(row)
    require(sha(prepath)==pre_sha,'Functional preflight changed while addressed proof was prepared')
    union={state_key(row['state']):row for row in original['states']}
    for row in new:
        key=state_key(row['state']);require(key not in union or union[key]['decision']==row['decision'],'Finite gate extension conflicts with prior state decision');union[key]=row
    result={'schemaVersion':1,'status':'PASS','mode':'FINITE_ACTUAL_CLASS_FUNCTION_EXTENSION_NOT_COMPILE_GATE','states':[union[k] for k in sorted(union)],'baseGate':{'path':str(out/'gate.json'),'sha256':sha(out/'gate.json'),'states':len(original['states'])},'functionalPreflight':{'path':str(prepath),'sha256':pre_sha,'cells':pre['functionalGuestCells'],'directlyRetainedObjects':pre['directlyRetainedObjects']},'extensionStates':len(new),'staticArchitectureCells':static_cells,'requiredHelperHostCells':host_cells,'staticArchitectureStates':sum(r['decision']=='static_base' for r in new),'helperHostStates':sum(r['decision']=='bridge_host_only' for r in new),'exactStaticPhysicsExportsPresent':covered,'missingStaticPhysicsExports':len(missing),'existingPlannedHelperBridges':sum(b['existingPlannedBridge'] is not None for b in bridges),'helperCellEvidence':bridges,'cachedChunks':chunk_proof,'sourceJar':str(BASE),'sourceJarSha256':BASE_SHA,'sourceCapabilities':capability,'sourceBinding':original['sourceBinding'],'primaryCode':primary,'legacyStaticResourceSha256':hashlib.sha256(recipes_raw).hexdigest(),'mapWrites':0,'worldFilesRead':0,'gameProcessesStarted':0,'persistentDatabaseWrites':0,'catalogueWrites':0,'limits':'This proves only the finite class/state function decisions. Missing exact physics blocks compilation. CompositeCellBlock still requires existing BE bridge/ownership proof. Existing source component break/cleanup is replaced only under the authorized independent architecture-cell migration.'}
    write(out/'functional-carrier-extension.json',result);write(out/'functional-carrier-extension-physics-needed.json',{'schemaVersion':1,'states':missing,'sourcePreflightSha256':pre_sha,'sourceJarSha256':BASE_SHA,'reason':'Exact observed dry StaticArchitecture physics/optics only; no visual export and no map read'})
    print(json.dumps({k:v for k,v in result.items() if k in ('status','mode','extensionStates','staticArchitectureCells','requiredHelperHostCells','staticArchitectureStates','helperHostStates','exactStaticPhysicsExportsPresent','missingStaticPhysicsExports','existingPlannedHelperBridges')}))
if __name__=='__main__':main()
