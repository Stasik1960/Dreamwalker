"""Make a fresh FULL_CITY function gate from a completed saved-data census.

Pure authoring: no SQLite/world access, JVM, build, discovery, or migration.
Prior pilot documents are exact-state policy evidence, never full-city coverage.
Unproved functions and missing actual host flags make the result BLOCKED.
"""
from __future__ import annotations
import argparse, copy, hashlib, json, os, sys, zipfile
from pathlib import Path

PROJECT = Path(__file__).resolve().parents[3]
ROOT = PROJECT.parent
SOURCE_SHA = '40cecfb7e5cf783a762e350fc1864518c39abff177f6f025cb94892a181fbf51'
BEFORE_ADAPTER_SHA = '3477fa1f016126eb58ea294a37a0ba06ad711f7e137ae72b103919df45e35bd3'
SOURCE = Path(r'C:\Users\vakir\.codex\worktrees\3f07\DW-city-mesh-20261018\deliverables\block-fixes-2-20261020\game\dreamwalker-bb-materials.1-streaming.7-blocks.2.jar')
DEFAULT_POLICY = ROOT/'research/native-grid-plan/context-extension/activation-supplement/function-gate.json'
DECISIONS = {'static_base', 'bridge_host_only', 'retain_local'}
FLAG_NAMES = ('opaque', 'sidedTransparency', 'luminance', 'fluidEmpty')


def require(ok, message):
    if not ok: raise ValueError(message)


def read(path): return json.loads(Path(path).read_text(encoding='utf-8-sig'))
def canonical(value): return json.dumps(value, sort_keys=True, separators=(',', ':'))


def sha(path):
    result = hashlib.sha256()
    with Path(path).open('rb') as stream:
        for block in iter(lambda: stream.read(1 << 20), b''): result.update(block)
    return result.hexdigest()


def bound(path): return {'path': str(Path(path).resolve()), 'sha256': sha(path)}


def write_new(path, value):
    path = Path(path)
    require(not path.exists(), 'Preserve existing evidence; choose a fresh output: '+str(path))
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(path.name+'.writing')
    require(not temporary.exists(), 'Unfinished authoring output exists: '+str(temporary))
    with temporary.open('w', encoding='utf-8') as stream:
        json.dump(value, stream, ensure_ascii=False, indent=2);stream.write('\n');stream.flush();os.fsync(stream.fileno())
    os.replace(temporary, path)


def state_key(state):
    require(isinstance(state, dict) and set(state)=={'registryId','properties'}, 'Exact state requires registryId/properties only')
    require(isinstance(state['registryId'], str) and ':' in state['registryId'] and isinstance(state['properties'], dict), 'Invalid exact registry/properties tuple')
    require(all(isinstance(k,str) and isinstance(v,str) for k,v in state['properties'].items()), 'Property names and values must be explicit strings')
    return canonical(state)


def inputs(path):
    data = read(path);coverage=data.get('coverage',{});binding=data.get('sourceBinding',{})
    require(data.get('schemaVersion')==1 and data.get('scope')=='FULL_CITY' and data.get('status')=='READ_ONLY_NATIVE_BASE_INPUT_EVIDENCE_NOT_APPLY_GATE', 'A pilot gate or context census cannot substitute for fresh FULL_CITY inputs')
    require(binding.get('sourceHashesVerified') is True and binding.get('originalPlanReadOnly') is True, 'Immutable source binding is not verified')
    require(sha(binding['copyManifest'])==binding['copyManifestSha256'], 'Immutable source copy manifest changed')
    require(sha(data['sourceCapabilities']['path'])==data['sourceCapabilities']['sha256'],'Bound registry/class capability input changed (capabilities alone are not function proof)')
    require(sha(data['inputGenerator']['path'])==data['inputGenerator']['sha256'] and sha(PROJECT/'tools/world_io.py')==data['inputGenerator']['worldIoSha256'],'Saved-data census generator/source reader binding changed')
    require(type(coverage.get('candidateObjects')) is int and coverage['candidateObjects']>0, 'Need actual full candidate-object count')
    require(coverage['candidateCells']==coverage['candidateCellsInspected'] and coverage['cachedChunksRequired']==coverage['cachedChunksInspected'] and coverage['missingCachedChunks']==[], 'Full census has incomplete cached-cell coverage')
    require(all(data.get(k)==0 for k in ('worldFilesRead','worldsOpened','mapWrites','persistentDatabaseWrites','gameProcessesStarted')), 'Input census exceeded its read-only saved-data scope')
    require(len(data['cachedChunkProofs'])==coverage['cachedChunksInspected'], 'Cached input proof count differs')
    required={};total=0
    for group in ('nativeBaseCandidates','blockEntityHostCandidates','sameRootSourcePoseCarriers'):
        require(isinstance(data.get(group),list), 'Missing explicit input group: '+group);seen=set()
        for row in data[group]:
            key=state_key(row['state']);require(key not in seen and type(row['cells']) is int and row['cells']>0,'Repeated state or invalid actual-cell count');seen.add(key);total+=row['cells']
            if group=='sameRootSourcePoseCarriers':continue
            old=required.get(key)
            if old:
                require(old['className']==row.get('className'),'Same exact state has conflicting observed classes')
                old['cells']+=row['cells'];old['host']=old['host'] or group=='blockEntityHostCandidates';old['witnesses']+=copy.deepcopy(row['witnesses'])
            else:required[key]={'state':copy.deepcopy(row['state']),'className':row.get('className'),'cells':row['cells'],'witnesses':copy.deepcopy(row['witnesses']),'host':group=='blockEntityHostCandidates'}
    require(total==coverage['candidateCells'], 'Input role groups omit or double count actual source cells')
    require(required, 'Actual full native base/host union is empty')
    return data,required


def policies(paths, source, archive):
    known={};proofs=[]
    for path in paths:
        policy_binding=bound(path);doc=read(path)
        require(doc.get('status')=='PASS' and doc.get('sourceJarSha256')==SOURCE_SHA and doc.get('sourceBinding')==source,'Exact-state policy has another immutable runtime/source binding: '+str(path))
        classes=[]
        for name,digest in doc.get('primaryClassHashes',{}).items():
            entry=(name if '.' in name else 'dev.dreamwalker.bloodbornedw.composite.'+name).replace('.','/')+'.class'
            require(hashlib.sha256(archive.read(entry)).hexdigest()==digest,'Policy primary class bytes differ: '+entry);classes.append({'entry':entry,'sha256':digest})
        for row in doc.get('primaryCode',[]):
            entry=row['className'].replace('.','/')+'.class'
            require(hashlib.sha256(archive.read(entry)).hexdigest()==row['jarClassSha256'],'Policy bytecode class differs: '+entry)
            require(sha(row['bytecodeFile'])==row['bytecodeSha256'],'Saved primary bytecode evidence changed')
            classes.append({'entry':entry,'sha256':row['jarClassSha256'],'bytecode':bound(row['bytecodeFile'])})
        require(classes, 'Exact function policy lacks bound original primary class evidence')
        external=[]
        for row in doc.get('externalPrimaryCode',[]):
            name=row['className'];require(name.startswith('net.minecraft.') and '/' not in name and '..' not in name,'External primary evidence is restricted to exact Minecraft classes')
            jar=row['jar'];require(sha(jar['path'])==jar['sha256'],'External Minecraft primary JAR changed')
            binding=row['runtimeBinding'];runtime=binding['minecraftJar'];arguments=binding['nativeArgs'];mapping=binding['intermediaryMappings']
            require(sha(runtime['path'])==runtime['sha256'] and sha(arguments['path'])==arguments['sha256'] and sha(mapping['path'])==mapping['sha256'],'Pinned baseline Minecraft runtime/arguments/mapping changed')
            command='\n'.join(json.loads(line) for line in Path(arguments['path']).read_text(encoding='utf-8-sig').splitlines() if line.strip()).replace('\\','/').lower()
            require(str(Path(runtime['path']).resolve()).replace('\\','/').lower() in command,'External code is not bound to the Minecraft JAR in the actual baseline arguments')
            entry=name.replace('.','/')+'.class'
            with zipfile.ZipFile(jar['path']) as minecraft:
                version=minecraft.read('version.json');require(json.loads(version)['id']=='1.20.1' and hashlib.sha256(version).hexdigest()==binding['versionJsonSha256'],'External code is not the pinned Minecraft1.20.1 version')
                require(hashlib.sha256(minecraft.read(entry)).hexdigest()==row['jarClassSha256'],'Exact external Minecraft class bytes changed')
            require(name.replace('.','/') in Path(mapping['path']).read_text(encoding='utf-8'),'Exact observed intermediary class is absent from the pinned runtime mapping')
            require(sha(row['bytecodeFile'])==row['bytecodeSha256'],'Saved external Minecraft bytecode evidence changed')
            external.append(copy.deepcopy(row))
        proof={**policy_binding,'sourceJarSha256':SOURCE_SHA,'primaryClassesReverified':classes,'externalPrimaryCodeReverified':external};proofs.append(proof)
        for row in doc['states']:
            key=state_key(row['state']);require(row['decision'] in DECISIONS and isinstance(row.get('reason'),str) and row['reason'].strip(),'Policy needs an exact state, decision and concrete reason')
            require(key not in known or known[key]['decision']==row['decision'],'Conflicting exact-state policy decisions: '+key)
            known[key]={**copy.deepcopy(row),'policyEvidence':dict(policy_binding)}
        require(sha(path)==policy_binding['sha256'],'Exact function policy changed during proof verification')
    return known,proofs


def flags(row):
    value={k:row[k] for k in FLAG_NAMES}
    require(all(type(value[k]) is bool for k in ('opaque','sidedTransparency','fluidEmpty')) and type(value['luminance']) is int and 0<=value['luminance']<=15,'Native flags must be actual typed query values')
    return value


def add_flags(known, state, value, proof, resolved=None, clazz=None, has_be=None):
    key=state_key(state)
    require(key not in known or known[key]['flags']==value,'Conflicting actual flags for one exact requested state')
    # Prefer source40ce property-only observations to compatible target evidence.
    if key not in known or proof['runtimeSha256']==SOURCE_SHA:
        known[key]={'flags':value,'evidence':proof,'resolvedState':resolved,'className':clazz,'hasBlockEntity':has_be}


def property_flags(paths, requests, helper):
    known={};proofs=[];request_by_sha={sha(p):Path(p) for p in requests}
    require(not paths or helper is not None,'Property observations require the immutable --properties-helper JAR used by the authoring launcher')
    helper_proof=bound(helper) if paths else None
    for path in paths:
        doc=read(path);folder=Path(path).parent;launch_path=folder/'launch-receipt.json';binding_path=folder/'binding.json'
        launch=read(launch_path);binding=read(binding_path)
        require(doc.get('schemaVersion')==1 and doc.get('status')=='PASS' and doc.get('kind')=='EXACT_STATE_PROPERTIES','Not a successful exact property-only query')
        require(doc.get('queryScope')=='EXACT_STATE_GETTERS_ONLY' and all(doc.get(k)==0 for k in ('worldReads','worldWrites','worldStatesWritten','blockEntitiesInstantiated','shapeQueries','renderQueries')) and doc.get('worldQueried') is False and doc.get('sourceMapOpened') is False and doc.get('bridgeValidationInvoked') is False,'State properties exceeded the exact getters-only query scope')
        require(doc.get('runtimeSha256')==binding.get('productionSha256')==launch.get('productionSha256')==SOURCE_SHA,'State property query did not use actual immutable40ce')
        require(binding.get('helperSha256')==helper_proof['sha256'],'Property query helper differs from the immutable actual authoring helper')
        require(launch.get('status')=='PASS' and launch.get('exitCode')==0,'Property authoring VM did not finish successfully')
        digest=doc['requestSha256'];require(digest in request_by_sha and digest==binding['requestSha256']==launch['requestSha256'],'Property query request bytes differ')
        requested=read(request_by_sha[digest]).get('stateProperties',[]);wanted={state_key(s) for s in requested};seen=set()
        require(len(wanted)==len(requested),'Property request repeats an exact state')
        proof={**bound(path),'runtimeSha256':SOURCE_SHA,'request':bound(request_by_sha[digest]),'helper':helper_proof,'launch':bound(launch_path),'binding':bound(binding_path),'kind':'ACTUAL_SOURCE_STATE_PROPERTIES_NOT_FUNCTION_PROOF'};proofs.append(proof)
        for row in doc['states']:
            key=state_key(row['requestedState']);require(key in wanted and key not in seen,'Unexpected or repeated property query state');seen.add(key)
            resolved=row['state'];state_key(resolved)
            require(resolved['registryId']==row['requestedState']['registryId'] and all(resolved['properties'].get(k)==v for k,v in row['requestedState']['properties'].items()),'Resolved query changed an explicitly supplied state property')
            require(type(row['hasBlockEntity']) is bool and isinstance(row['className'],str),'Actual class/BE predicate is missing')
            add_flags(known,row['requestedState'],flags(row),proof,resolved,row['className'],row['hasBlockEntity'])
        require(seen==wanted,'Property-only query omitted actual requested states')
    return known,proofs


def target_flags(paths, runtime, patch, known):
    if not paths:return []
    require(runtime is not None,'Compatible target audit needs an explicit immutable --target-jar');digest=sha(runtime)
    if digest!=BEFORE_ADAPTER_SHA:
        require(patch is not None,'Only the exact3477 target or its proven adapter-only patch may supply reused flags')
        sys.path.insert(0,str(PROJECT/'tools/native-grid'));import runtime_patch_receipt
        receipt=runtime_patch_receipt.validate(patch,runtime)
        require(receipt['before']['sha256']==BEFORE_ADAPTER_SHA,'Target flags use an unrelated runtime family')
    proofs=[]
    for path in paths:
        doc=read(path);require(doc.get('status')=='PASS' and doc.get('worldReads')==0 and doc.get('worldWrites')==0 and doc.get('nativeRegistryQueries') is True and doc['runtimeSha256']==digest,'Compatible target flags are not exact successful worldless observations')
        folder=Path(path).parent;launch_path=folder/'launch-receipt.json';binding_path=folder/'binding.json';launch=read(launch_path);binding=read(binding_path)
        require(launch.get('status')=='PASS' and launch.get('exitCode')==0 and launch.get('productionSha256')==binding.get('productionSha256')==digest,'Target callback flags need their successful actual launcher/runtime binding')
        require(doc.get('bridgePlanSha256')==binding.get('requestSha256')==launch.get('requestSha256') and isinstance(binding.get('requestSha256'),str),'Target observed flags differ from the successful launcher request')
        proof={**bound(path),'runtimeSha256':digest,'runtime':bound(runtime),'patch':bound(patch) if patch else None,'launch':bound(launch_path),'binding':bound(binding_path),'kind':'ACTUAL_COMPATIBLE_TARGET_FLAGS_NOT_SOURCE_FUNCTION_PROOF'};proofs.append(proof)
        for row in doc['results']:
            require(row['status']=='PASS','Target audit contains an unverified host')
            add_flags(known,row['input']['state'],flags(row),proof)
    return proofs


def produce(args):
    outputs=[Path(args.out).resolve()]+([Path(args.requests_out).resolve()] if args.requests_out else [])
    require(len(outputs)==len(set(outputs)) and all(not p.exists() for p in outputs),'Outputs must be distinct fresh authoring artifacts')
    require(args.carrier_audit_dir is None or not args.carrier_audit_dir.exists(),'Use a fresh derived-observation directory; never mix old flag artifacts')
    require(sha(args.source_jar)==SOURCE_SHA,'Function decisions must bind immutable original40ce')
    start=sha(args.input);data,required=inputs(args.input)
    with zipfile.ZipFile(args.source_jar) as archive:known,policy_proofs=policies(args.policy or [DEFAULT_POLICY],data['sourceBinding'],archive)
    observed,flag_proofs=property_flags(args.state_properties,args.query_request,args.properties_helper)
    flag_proofs+=target_flags(args.target_audit,args.target_jar,args.target_runtime_patch,observed)
    rows=[];missing_functions=[];missing_flags=[];query={};counts={k:0 for k in DECISIONS}
    for key,actual in sorted(required.items()):
        row={k:copy.deepcopy(v) for k,v in actual.items() if k!='host'};policy=known.get(key);observation=observed.get(key)
        if policy is None:
            missing_functions.append(copy.deepcopy(actual));row.update(decision='retain_local',reason='Exact observed state has no bound function decision; provisional local retention only, FULL gate blocked',decisionProvisional=True);query[key]=actual['state']
        else:
            require(actual['className'] is None or policy.get('className') in (None,actual['className']),'Actual census class differs from exact accepted policy')
            row.update(decision=policy['decision'],reason=policy['reason'],functionEvidence=policy['policyEvidence'])
            if actual['host'] and row['decision']=='static_base':
                missing_functions.append({**copy.deepcopy(actual),'specificReason':'Actual BE host conflicts with passive exact-state policy'});row.update(decision='retain_local',decisionProvisional=True,reason='Actual existing BE host cannot be flattened; passive policy rejected pending concrete local function proof')
        if actual['host']:
            row['actualRole']='existing_be_host_only'
            if observation is None:missing_flags.append(copy.deepcopy(actual));query[key]=actual['state']
            else:
                require(observation['hasBlockEntity'] is not False,'Actual property query contradicts the census BE-host role')
                require(actual['className'] is None or observation['className'] in (None,actual['className']),'Observed native host class differs from census class')
                row['observedNativeFlags']=observation['flags'];row['observedNativeFlagsEvidence']=observation['evidence']
                if observation['resolvedState'] is not None:row['resolvedNativeState']=observation['resolvedState']
                if row['decision']=='bridge_host_only' and (not observation['flags']['fluidEmpty'] or observation['flags']['luminance']!=0):
                    row.update(decision='retain_local',reason='Actual exact host is wet or emitting; existing dry/nonemitting bridge cannot encode its function/predicates; preserve this local host and its typed contents',observedBridgeRestriction=True)
        else:row['actualRole']='native_base'
        counts[row['decision']]+=1;rows.append(row)
    require(sha(args.input)==start,'Fresh FULL input evidence changed during authoring')
    ready=not missing_functions and not missing_flags
    report={'schemaVersion':1,'status':'PASS' if ready else 'BLOCKED_MISSING_EXACT_FUNCTION_OR_HOST_FLAGS','scope':'FULL_CITY','mode':'FRESH_ACTUAL_NATIVE_FUNCTION_DECISIONS_NOT_GAMEPLAY',
        'sourceBinding':data['sourceBinding'],'sourceJar':str(args.source_jar.resolve()),'sourceJarSha256':SOURCE_SHA,'inputEvidence':bound(args.input),'coverage':copy.deepcopy(data['coverage']),
        'states':rows,'decisions':counts,'requiredActualStates':len(required),'policyEvidence':policy_proofs,'nativeFlagEvidence':flag_proofs,'missingFunctionProofs':missing_functions,'missingObservedHostFlags':missing_flags,
        'sameRootSourcePoseCarriersAreInspectionOnly':True,'sameRootSourcePoseCarrierStates':len(data['sameRootSourcePoseCarriers']),
        'unknownPolicy':'BLOCK: no class/name/prefix/default-flag inference; exact unknown states remain provisional local retention until their real function is proved',
        'worldFilesRead':0,'worldReads':0,'worldWrites':0,'mapWrites':0,'persistentDatabaseWrites':0,'gameProcessesStarted':0,'producer':bound(__file__),
        'limits':'This is finite function and worldless native-flag evidence, not ownership/BE admission, visual/physics export, final post-activation coverage, target bridge acceptance, light or gameplay PASS. Existing typed BE/resident/retained owners still require the migrator own exact admission proof.'}
    requests={'schemaVersion':1,'mode':'FUTURE_EXACT_STATE_PROPERTIES_REQUEST_NOT_EXECUTED','sourceJarSha256':SOURCE_SHA,'inputEvidence':bound(args.input),'states':[],
        'stateProperties':[query[k] for k in sorted(query)],'worldlessApi':'NativeGridPhysicsExport dw.nativegrid.physics.request/output; run_native_physics.py on immutable40ce disposable empty authoring profile',
        'unknownFunctionProofsStillRequired':[r['state'] for r in missing_functions],
        'scope':'Properties only: no shapes, BE instances, world reads, source map, bridge validation or gameplay; flags do not prove absent functions.'}
    if args.requests_out:write_new(args.requests_out,requests)
    if args.carrier_audit_dir:
        # These are observations for pre-catalog normalization ONLY. They do
        # not call NativeGridBridge.validate or replace the post-catalog gate.
        groups={}
        decisions={state_key(row['state']):row['decision'] for row in rows}
        for key,actual in required.items():
            if actual['host'] and decisions[key]=='bridge_host_only' and key in observed:
                obs=observed[key];digest=obs['evidence']['runtimeSha256'];group=groups.setdefault(digest,[])
                group.append({'status':'PASS','input':{'state':actual['state']},**obs['flags'],'propertiesEvidence':obs['evidence']})
        for digest,values in groups.items():
            write_new(args.carrier_audit_dir/('observed-flags-'+digest[:16]+'.json'),{'schemaVersion':1,'status':'PASS','kind':'EXACT_OBSERVED_CARRIER_FLAGS_FOR_NORMALIZATION_ONLY',
                'nativeGridBridgeValidated':False,'runtimeSha256':digest,'catalogSha256':None,'worldReads':0,'worldWrites':0,'sourceMapOpened':False,'inputEvidence':bound(args.input),'results':values,
                'mandatoryNextGate':'Actual TARGET NativeGridBridge.validate on the final compiled catalog; this document is not bridge acceptance or gameplay evidence'})
    write_new(args.out,report)
    print(canonical({'status':report['status'],'scope':'FULL_CITY','candidateObjects':data['coverage']['candidateObjects'],'states':len(rows),'missingFunctions':len(missing_functions),'missingHostFlags':len(missing_flags),'out':str(args.out),'sha256':sha(args.out)}))
    return 0 if ready else 2


def parser():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--input',type=Path,required=True);p.add_argument('--out',type=Path,required=True)
    p.add_argument('--source-jar',type=Path,default=SOURCE);p.add_argument('--policy',type=Path,action='append')
    p.add_argument('--state-properties',type=Path,action='append',default=[]);p.add_argument('--query-request',type=Path,action='append',default=[])
    p.add_argument('--properties-helper',type=Path,help='Immutable actual exporter helper JAR whose SHA is recorded by the successful source query launcher')
    p.add_argument('--target-audit',type=Path,action='append',default=[]);p.add_argument('--target-jar',type=Path);p.add_argument('--target-runtime-patch',type=Path)
    p.add_argument('--requests-out',type=Path);p.add_argument('--carrier-audit-dir',type=Path)
    return p


if __name__=='__main__':sys.exit(produce(parser().parse_args()))
