"""Bind exact append inputs; legacy phase2 defaults or explicit FULL artifacts."""
import argparse,hashlib,itertools,json,re,sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent.parent))
from export_merge_physics import require,sha,read
from export_cells import read_lines,state_key
from catalog import compiler_fingerprint
ROOT=Path(__file__).resolve().parents[4];SOURCE=ROOT/'source';R=ROOT/'research';C=R/'native-grid-plan/context-extension'
RAW='3333ec6d9f6bab318971fd5fcd182813937a3eb17bfb077dcd889b57c828d087'
FINAL='b52da3beb2e14b40777ae058e145ba5f7a379b8c4524b10af6f11823ac18b8c1'
FRAG='96b0ac0fb72481b6c59c83167e5649a494da3a6ccafedccf3907b219bc424bca'
ALGORITHM='111b89688889fa961f2ae19bc5e0f0683d2ec4c9bfa8fbcf0b36117a5e96e13f'
def phase2():
    final=read(C/'final-state-coverage.json');normal=read(C/'normalizer-receipt.json');rawpath=R/'native-grid-plan/compositions.jsonl';normalized=C/'compositions.normalized.jsonl'
    require(sha(C/'final-state-coverage.json')==FINAL and final['status']=='PASS','Authorized exact final coverage changed or failed')
    require(final['actualNeededStates']==248 and final['functionDecisions']==260,'Authorized exact state coverage differs')
    require(final['compositionsSha256']==sha(rawpath)==RAW,'Authorized final raw composition changed')
    require(final['fragmentBinding']['sha256']==sha(R/'fragments.sqlite')==FRAG,'Authorized exact fragment database changed')
    require(normal['status']=='PASS' and normal['rawCompositionsSha256']==RAW and normal['normalizedCompositionsSha256']==sha(normalized),'Final normalizer lineage differs')
    require(normal['normalizerSha256']==sha(Path(__file__).with_name('catalog_normalize_carriers.py')),'Authoring normalizer changed after final materialization')
    require(normal['compositionKeysPreserved'] and normal['onlyOpaqueAndSidedTransparencyOverridden'] and normal['physicalGeometryFluidLocalFunctionUnchanged'],'Normalized authoring semantics changed')
    require(normal['bridgeCompositions']==normal['normalizedPartialCarrierRows']==64,'Final actual carrier coverage is incomplete')
    require(compiler_fingerprint()==normal['compilerAlgorithmSha256']==ALGORITHM,'Pinned compiler fingerprint changed')
    bindings={}
    for key in ('nativeFunctionGate','fragmentAppendLineage'):
        proof=final[key];require(sha(proof['path'])==proof['sha256'] and read(proof['path'])['status']=='PASS','Final source proof changed: '+key);bindings[key]=proof
    for name,wanted in (('preflight-raw-final.json',RAW),('preflight-normalized.json',sha(normalized))):
        proof=read(C/name);require(proof['status']=='PASS' and proof['problemCount']==0 and proof['compositionsSha256']==wanted,'Final preflight differs: '+name)
        bindings[name]={'path':str(C/name),'sha256':sha(C/name)}
    whole=R/'whole-objects.jsonl';require(len(whole.read_text(encoding='utf-8').splitlines())==72,'Expected exact whole templates changed')
    result={'status':'PASS','mode':'EXACT_AUTHORIZED_PHASE2_APPEND_INPUT_BINDING','rawCompositionsSha256':RAW,'normalizedCompositionsSha256':sha(normalized),'finalCoverageSha256':FINAL,'fragmentsSha256':FRAG,'normalizerReceiptSha256':sha(C/'normalizer-receipt.json'),'compilerAlgorithmSha256':ALGORITHM,'wholeObjectsSha256':sha(whole),'wholeTemplates':72,'proofs':bindings,'catalogueWrites':0,'databaseWrites':0,'mapWrites':0}
    (C/'append-input-binding.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf-8');print(json.dumps(result))

def bound(path,expected=None):
    path=Path(path).resolve();require(path.is_file(),'Missing explicit artifact: '+str(path));actual=sha(path)
    if expected is not None:
        require(re.fullmatch(r'[0-9a-f]{64}',expected or '') and actual==expected,'Explicit artifact SHA differs: '+str(path))
    return {'path':str(path),'sha256':actual}
def checked_proof(proof):
    return bound(proof['path'],proof['sha256'])
def save_new(path,document):
    require(not path.exists(),'Use a fresh receipt; preserve prior evidence: '+str(path));path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(document,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
def full(a):
    names=('compositions','normalized','final_coverage','fragments','normalizer_receipt','preflight_raw','preflight_normalized','whole_objects','whole_receipt','native_states_needed')
    require(all(getattr(a,k) is not None and getattr(a,k+'_sha256') for k in names),'FULL requires explicit paths and SHA256 for every actual input artifact')
    require(all(getattr(a,k) is not None for k in ('plan','database','resources','mapping','prefix_snapshot','prefix_manifest_sha256','out')),'FULL plan/database/resources/mapping/frozen-prefix/output paths are required')
    require(a.compositions.resolve()==(a.plan/'compositions.jsonl').resolve() and a.native_states_needed.resolve()==(a.plan/'native-states-needed.json').resolve(),'Raw/native-needed inputs must belong to the one explicit full plan')
    require(a.database.resolve()==(R/'catalog-state.sqlite').resolve() and a.database.is_file(),'Append must use the SAME existing catalog-state.sqlite')
    inputs={name:bound(getattr(a,name),getattr(a,name+'_sha256')) for name in names}
    prefix=bound(a.prefix_snapshot/'manifest.json',a.prefix_manifest_sha256);old=read(prefix['path'])
    require(old['status']=='PREFIX_FROZEN' and old['compilerAlgorithmSha256']==ALGORITHM,'Explicit prior prefix is not the frozen compiler contract')
    # Bind the already frozen baseline; do not repeat snapshot/prefix verification.
    require(bool(a.completed_prefix_proof)==bool(a.completed_prefix_proof_sha256),'Completed prefix proof path/SHA must be paired')
    completed_prefix=None
    expected_output=old
    if a.completed_prefix_proof:
        completed_prefix=bound(a.completed_prefix_proof,a.completed_prefix_proof_sha256)
        done=read(completed_prefix['path'])
        require(done['status']=='PASS' and all(done.get(k)is True for k in ('allPriorIndexedRowsExact','meshPayloadPrefixExact','priorRealCellMarkersExact')) and
                done['previousCounts']==old['counts'] and done['existingMappingKeysPreserved']==old['mappingKeys'] and
                all(done['prior'+k[0].upper()+k[1:]]==old[k] for k in ('catalogSha256','meshesSha256','mappingSha256')),
                'Completed output must consume the exact accepted post-append prefix proof')
        expected_output=done
    require(sha(a.resources/'catalog.json')==expected_output['catalogSha256'] and sha(a.resources/'meshes.bin')==expected_output['meshesSha256'] and sha(a.mapping)==expected_output['mappingSha256'],'Current resources/mapping differ from the explicit frozen output binding')
    final=read(a.final_coverage);normal=read(a.normalizer_receipt);whole=read(a.whole_receipt)
    require(final['status']=='PASS' and final['scope']=='FULL_CITY' and final['kind']=='ACTUAL_POST_ACTIVATION_NATIVE_INPUT_GATE','Only completed actual FULL coverage may authorize these inputs')
    require(all(final.get(name)==[] for name in ('missingFunctionDecisions','nonPassiveBaseStates','missingNativeExports','invalidPhysicalNativeRows','missingActivatedSourcePoseFootprints')),'Actual FULL coverage has unresolved finite inputs')
    require(final['compositionsSha256']==inputs['compositions']['sha256'] and final['compositionGeneration']['compositionsSha256']==inputs['compositions']['sha256'],'Final coverage belongs to another raw composition generation')
    require(final['fragmentBinding']['sha256']==inputs['fragments']['sha256'] and Path(final['fragmentBinding']['path']).resolve()==a.fragments.resolve(),'Final coverage belongs to another exact fragment bank')
    require(final['nativeStatesNeededSha256']==inputs['native_states_needed']['sha256'],'Final actual native-needed input changed')
    native=read(a.native_states_needed)['nativeStates'];required={state_key(row) for row in native}
    require(len(required)==len(native)==final['actualNeededStates'],'Derived exact native-state count differs from the completed gate')
    function=checked_proof(final['nativeFunctionGate']);functions=read(function['path']);decisions={state_key(row['state']):row['decision'] for row in functions['states']}
    require(functions['status']=='PASS' and functions['scope']=='FULL_CITY' and functions['sourceBinding']==final['sourceBinding'],'Fresh FULL function source/scope differs')
    require(len(decisions)==len(functions['states'])==final['functionDecisions'] and all(decisions.get(key)=='static_base' for key in required),'Exact actual native function coverage differs')
    source=bound(functions['sourceJar'],functions['sourceJarSha256']);evidence=checked_proof(functions['inputEvidence'])
    source_proofs={'nativeFunctionGate':function,'sourceJar':source,'inputEvidence':evidence}
    if completed_prefix:source_proofs['completedPrefixProof']=completed_prefix
    historical=final['compositionGeneration']['sourceFragments']['sha256']
    if historical!=inputs['fragments']['sha256']:
        lineage=checked_proof(final['fragmentAppendLineage']);receipt=read(lineage['path'])
        require(receipt['status']=='PASS' and receipt['beforeFragmentsSha256']==historical and receipt['afterFragmentsSha256']==inputs['fragments']['sha256'] and all(receipt.get(k)is True for k in ('posesUnchanged','fragmentsUnchanged','existingNativeRowsUnchanged')),'Strict historical-to-current fragment lineage is incomplete')
        source_proofs['fragmentAppendLineage']=lineage
    require(normal['status']=='PASS' and normal['rawCompositionsSha256']==inputs['compositions']['sha256'] and normal['normalizedCompositionsSha256']==inputs['normalized']['sha256'],'FULL raw/normalized receipt lineage differs')
    require(normal['normalizerSha256']==sha(Path(__file__).with_name('catalog_normalize_carriers.py')) and normal['compilerAlgorithmSha256']==compiler_fingerprint()==ALGORITHM,'Reviewed authoring normalizer/pinned compiler changed')
    require(all(normal.get(k)is True for k in ('compositionKeysPreserved','onlyOpaqueAndSidedTransparencyOverridden','physicalGeometryFluidLocalFunctionUnchanged')),'FULL normalizer altered unrelated authoring semantics')
    nf=normal.get('fullFunctionGate');require(nf and Path(nf['path']).resolve()==Path(function['path']) and nf['sha256']==function['sha256'],'Normalizer did not consume this exact fresh FULL function gate')
    for index,proof in enumerate(normal['nativeAuditBindings']):source_proofs['observedCarrierAudit.'+str(index)]=checked_proof(proof)
    null_base_keys=normal.get('canonicalizedNullBaseKeys');require(isinstance(null_base_keys,list) and all(isinstance(key,str) for key in null_base_keys) and len(null_base_keys)==len(set(null_base_keys))==normal.get('canonicalizedNullBaseRows') and normal.get('onlyEmptyNullNativeBasesCanonicalized') is True,'Exact null-base canonicalization receipt is missing or inconsistent')
    null_base_keys=set(null_base_keys);seen_null_bases=set()
    raw_count=wrappers=partial=0;last=None
    for raw,normalized in itertools.zip_longest(read_lines(a.compositions),read_lines(a.normalized)):
        require(raw is not None and normalized is not None and raw['key']==normalized['key'],'Raw/normalized composition keys or row counts differ')
        require(last is None or last<raw['key'],'Actual composition keys must remain unique and sorted');last=raw['key'];raw_count+=1
        if raw['key'] in null_base_keys:
            require(raw.get('baseState') is None and raw.get('baseVisualMode') in ('substrateFragments','nativePlusSubstrateFragments') and any(raw.get(name) for name in ('substrateFragments','substrateNativeFragments')) and normalized.get('baseState')=={'registryId':'minecraft:air','properties':{}},'Listed null-base row is not the exact explicit AIR canonicalization')
            raw={**raw,'baseState':{'registryId':'minecraft:air','properties':{}}};seen_null_bases.add(raw['key'])
        if raw.get('wrapper')=='NativeGridBase':
            wrappers+=1;partial+=raw.get('carrierPredicates') is not None
            require({k:v for k,v in raw.items() if k not in ('optics','carrierPredicates')}=={k:v for k,v in normalized.items() if k!='optics'} and isinstance(normalized.get('optics'),dict),'Bridge normalization changed geometry/physics/keys or omitted full optics')
        else:require(raw==normalized,'Non-bridge composition changed during normalization')
    require(seen_null_bases==null_base_keys,'Normalizer receipt lists absent null-base composition keys')
    require(raw_count==normal['rows'] and wrappers==normal['bridgeCompositions']==normal['materializedObservedCarrierRows'] and partial==normal['normalizedPartialCarrierRows'] and wrappers-partial==normal['derivedObservedCarrierRows'],'Derived actual FULL row/carrier counts differ')
    for name,expected in (('preflight_raw',inputs['compositions']['sha256']),('preflight_normalized',inputs['normalized']['sha256'])):
        proof=read(inputs[name]['path']);require(proof['status']=='PASS' and proof['problemCount']==0 and proof['compositionsSha256']==expected,'Existing final preflight differs: '+name)
        require(proof['counts']['compositions']==raw_count,'Preflight composition count differs from actual input bytes')
    require(whole['status']=='PREPARED_NOT_COMPILED' and whole['sourceBinding']==final['sourceBinding'] and whole['composition']['sha256']==inputs['compositions']['sha256'],'Whole continuation belongs to another FULL source/generation')
    require(checked_proof(whole['extendedWholeObjects'])==inputs['whole_objects'] and whole['old72BytePrefixExact'] is True,'Whole continuation output/prefix differs')
    base=checked_proof(whole['baseWholeObjects']);base_bytes=Path(base['path']).read_bytes();whole_bytes=a.whole_objects.read_bytes()
    require(whole_bytes.startswith(base_bytes),'Existing whole request byte-prefix changed')
    requests=list(read_lines(a.whole_objects));keys=[row['key'] for row in requests];prior=sum(1 for _ in read_lines(base['path']))
    require(len(keys)==len(set(keys))==whole['totalCount'] and prior==whole['previousCount']==old['counts']['wholeObjects'] and len(keys)-prior==whole['addedPoses']==len(whole['selectedConfirmedTemplates']),'Derived whole-template counts/key uniqueness differ')
    require(whole['compilerAlgorithmSha256']==ALGORITHM,'Whole continuation uses another compiler contract')
    require(whole['fragmentBankUnchanged']['sha256']==inputs['fragments']['sha256'],'Whole continuation must bind the final actual source fragment bank')
    source_proofs['baseWholeObjects']=base
    source_proofs['preservedAssemblies']=checked_proof(whole['manifest'])
    # No checker/preflight/prefix is rerun; the exact completed artifacts above are consumed once.
    for name,proof in inputs.items():bound(proof['path'],proof['sha256'])
    result={'schemaVersion':1,'status':'PASS','mode':'EXPLICIT_FULL_APPEND_INPUT_BINDING','inputs':inputs,'sourceBinding':final['sourceBinding'],'sourceProofs':source_proofs,'prefixSnapshot':{'path':str(a.prefix_snapshot.resolve()),'manifest':prefix,'catalogSha256':old['catalogSha256'],'meshesSha256':old['meshesSha256'],'mappingSha256':old['mappingSha256'],'counts':old['counts']},'catalogDatabase':str(a.database.resolve()),'resources':str(a.resources.resolve()),'mapping':str(a.mapping.resolve()),'compilerAlgorithmSha256':ALGORITHM,'canonicalizedNullBaseKeys':sorted(seen_null_bases),'derivedCounts':{'compositions':raw_count,'bridgeCompositions':wrappers,'partialCarrierRows':partial,'canonicalizedNullBaseRows':len(seen_null_bases),'actualNeededStates':len(required),'functionDecisions':len(decisions),'wholeTemplates':len(keys),'priorWholeTemplates':prior,'addedWholeTemplates':len(keys)-prior},'catalogueWrites':0,'databaseWrites':0,'mapWrites':0,'checkersExecuted':0,'prefixSnapshotCreated':False}
    save_new(a.out,result);print(json.dumps({'status':result['status'],'receipt':str(a.out),'sha256':sha(a.out),'derivedCounts':result['derivedCounts']}))
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--mode',choices=('phase2','full'),default='phase2')
    for name in ('compositions','normalized','final_coverage','fragments','normalizer_receipt','preflight_raw','preflight_normalized','whole_objects','whole_receipt','native_states_needed'):
        p.add_argument('--'+name.replace('_','-'),type=Path);p.add_argument('--'+name.replace('_','-')+'-sha256')
    for name in ('plan','database','resources','mapping','prefix_snapshot','out'):p.add_argument('--'+name.replace('_','-'),type=Path)
    p.add_argument('--prefix-manifest-sha256')
    p.add_argument('--completed-prefix-proof',type=Path);p.add_argument('--completed-prefix-proof-sha256');a=p.parse_args()
    if a.mode=='phase2':
        require(not any(v is not None for k,v in vars(a).items() if k!='mode'),'Explicit FULL paths/hashes require --mode full');phase2()
    else:full(a)
if __name__=='__main__':main()
