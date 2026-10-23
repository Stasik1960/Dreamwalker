"""Resolve finite observed carrier predicates before the unchanged pinned compiler.

Only two observed host flags are overridden. Empty null bases on explicit visual
routes are spelled AIR after exact fluid/optics checks. Composition keys survive;
the receipt records raw/normalized lineage. No catalogue/plan database is edited.
"""
import argparse,hashlib,json,sqlite3,sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent.parent))
from export_cells import canonical,state_key,read_lines,unique_quads
from catalog import normalize_optics,normalize_fluid,EMPTY_OPTICS,compiler_fingerprint,opaque_cube

ALGORITHM='111b89688889fa961f2ae19bc5e0f0683d2ec4c9bfa8fbcf0b36117a5e96e13f'
SOURCE_JAR='40cecfb7e5cf783a762e350fc1864518c39abff177f6f025cb94892a181fbf51'
FLAGS=('opaque','sidedTransparency')
AIR={'registryId':'minecraft:air','properties':{}}
def require(value,message):
    if not value:raise ValueError(message)
def sha(path):return hashlib.sha256(Path(path).read_bytes()).hexdigest()
def ro(path):return sqlite3.connect('file:'+Path(path).absolute().as_posix()+'?mode=ro',uri=True)
def effective_optics(row,native,fragments):
    require(set(native['optics'])>=set(EMPTY_OPTICS),'Actual native export must contain every optical field')
    result=normalize_optics(native['optics'])
    if row.get('optics') is not None:
        require(set(row['optics'])>=set(EMPTY_OPTICS),'Existing full optical override is incomplete')
        result=normalize_optics(row['optics'])
    else:
        for name in ('opacity','luminance'):
            result[name]=max([result[name],*[f.get('optics',{}).get(name,0) for f in fragments]])
    return result
def override_only_predicates(optics,predicates):
    require(set(predicates)==set(FLAGS) and all(type(predicates[k]) is bool for k in FLAGS),'Carrier predicates must contain exactly two observed booleans')
    return {**optics,**predicates}
def materialize_observed_carrier(row,optics,predicates):
    return {**{k:v for k,v in row.items() if k!='carrierPredicates'},'optics':override_only_predicates(optics,predicates)}
def validate_air_nonoverridden_optics(optics):
    actual=normalize_optics(optics)
    require({k:v for k,v in actual.items() if k not in FLAGS}=={k:v for k,v in EMPTY_OPTICS.items() if k not in FLAGS},'Actual AIR export disagrees with non-overridden pinned compiler semantics; do not substitute defaults')

def canonicalize_empty_null_base(row,store):
    if row.get('baseState') is not None or row.get('baseVisualMode') not in ('substrateFragments','nativePlusSubstrateFragments'):
        return row,False
    key=row['key'];require(any(row.get(name) for name in ('substrateFragments','substrateNativeFragments')),'Null-base visual route has no explicit fragments: '+key)
    ordinary=[];visual=[]
    for name in ('fragments','substrateFragments'):
        for item in row.get(name,[]):
            found=store.execute('SELECT data FROM fragments WHERE pose=? AND x=? AND y=? AND z=?',(item['pose'],*item['cell'])).fetchone()
            require(found is not None,'Null-base route lacks an exact source fragment: '+key)
            (ordinary if name=='fragments' else visual).append(json.loads(found[0]))
    for item in row.get('substrateNativeFragments',[]):
        found=store.execute('SELECT data FROM native_fragments WHERE state=? AND x=? AND y=? AND z=?',(state_key(item['state']),*item['cell'])).fetchone()
        require(found is not None,'Null-base route lacks an exact native visual fragment: '+key);visual.append(json.loads(found[0]))
    require(normalize_fluid(row.get('fluid')) is None and all(normalize_fluid(fragment.get('fluid')) is None for fragment in ordinary),'Null-base AIR canonicalization would suppress an ordinary fluid: '+key)
    # The pinned compiler adds special optics only for a null-base opaque cube.
    # Preserve that distinction rather than inventing a new optical override.
    require(not opaque_cube(unique_quads([quad for fragment in ordinary+visual for quad in fragment.get('quads',[])])),'Null-base AIR canonicalization would change opaque-cube optics: '+key)
    return {**row,'baseState':dict(AIR)},True
def full_host_decision(decisions,key):
    require(key in decisions and decisions[key]=='bridge_host_only','Actual FULL bridge host lacks an exact bridge_host_only function decision: '+key)
def full_function_states(path,plan):
    metadata={key:json.loads(value) for key,value in plan.execute("SELECT key,value FROM meta WHERE key IN ('scope','fullNativeFunctionGateRequired','fullNativeFunctionGate','binding')")}
    full=not metadata.get('scope') and metadata.get('fullNativeFunctionGateRequired') is True
    if not full:
        require(path is None,'A FULL function gate was supplied outside the promoted FULL plan');return None,None
    require(path is not None,'FULL normalization requires --full-function-gate with fresh actual source inputs')
    binding=metadata.get('fullNativeFunctionGate');require(binding and Path(binding['path']).resolve()==path.resolve() and sha(path)==binding['sha256'],'FULL function gate differs from the imported exact decision evidence')
    doc=json.loads(path.read_text(encoding='utf-8-sig'));source=metadata['binding']
    require(doc.get('status')=='PASS' and doc.get('scope')=='FULL_CITY' and doc.get('mapWrites',0)==0 and doc.get('sourceBinding')==source,'FULL function gate belongs to another source/scope or did not pass')
    require(source.get('sourceHashesVerified') is True and sha(source['copyManifest'])==source['copyManifestSha256'],'Verified immutable source manifest binding changed')
    require(sha(doc['sourceJar'])==doc['sourceJarSha256']==SOURCE_JAR,'Actual immutable original source JAR binding changed')
    evidence=doc['inputEvidence'];require(sha(evidence['path'])==evidence['sha256'],'Fresh FULL function input evidence changed')
    actual=json.loads(Path(evidence['path']).read_text(encoding='utf-8-sig'));count=plan.execute("SELECT count(*) FROM objects WHERE kind IN ('static','whole')").fetchone()[0]
    require(actual.get('scope')=='FULL_CITY' and actual.get('sourceBinding')==source and actual.get('coverage',{}).get('candidateObjects')==count,'FULL actual function input scope/source/candidate coverage differs')
    coverage=actual['coverage'];require(actual.get('status')=='READ_ONLY_NATIVE_BASE_INPUT_EVIDENCE_NOT_APPLY_GATE' and coverage['candidateCells']==coverage['candidateCellsInspected'] and coverage['cachedChunksRequired']==coverage['cachedChunksInspected'] and coverage['missingCachedChunks']==[],'Fresh FULL source input evidence has incomplete actual coverage')
    require(isinstance(actual.get('nativeBaseCandidates'),list) and isinstance(actual.get('blockEntityHostCandidates'),list),'Fresh FULL source inputs must include exact base and BE-host groups')
    decisions={state_key(row['state']):row['decision'] for row in doc['states']}
    require(len(decisions)==len(doc['states'])==binding['states'],'Duplicate or missing FULL function decisions')
    required={state_key(row['state']) for group in ('nativeBaseCandidates','blockEntityHostCandidates') for row in actual[group]}
    require(required<=set(decisions),'FULL function decisions omit an actual native base or BE host')
    return decisions,{'path':str(path.resolve()),'sha256':sha(path),'inputEvidence':evidence,'sourceJarSha256':doc['sourceJarSha256'],'candidateObjects':count}
def audit_states(paths):
    known={};proof=[]
    for path in paths:
        doc=json.loads(Path(path).read_text(encoding='utf-8-sig'))
        require(doc['status']=='PASS' and doc['worldReads']==0 and doc['worldWrites']==0,'Only successful native worldless carrier audit may supply predicates')
        for row in doc['results']:
            require(row['status']=='PASS','Native carrier audit contains an unverified host state')
            key=state_key(row['input']['state']);value={k:row[k] for k in FLAGS}
            require(row['luminance']==0 and row['fluidEmpty'],'Observed carrier must remain dry and nonemitting')
            require(key not in known or known[key]==value,'Conflicting native predicate evidence for exact host state')
            known[key]=value
        proof.append({'path':str(Path(path).absolute()),'sha256':sha(path),'runtimeSha256':doc['runtimeSha256'],'catalogSha256':doc['catalogSha256']})
    return known,proof
def normalize(a):
    require(compiler_fingerprint()==ALGORITHM,'Pinned compiler changed before authoring normalization')
    start=sha(a.compositions);require(start==a.raw_sha256,'Raw final compositions differ from explicit handoff SHA')
    known,proof=audit_states(a.audit);plan=ro(a.plan/'plan.sqlite');plan.execute('BEGIN');store=ro(a.fragments)
    functions,function_proof=full_function_states(a.full_function_gate,plan)
    bridge_hosts={}
    for key,encoded in plan.execute('SELECT p.composition,b.host_state FROM bridge_cells b CROSS JOIN placements p WHERE p.dim=b.dim AND p.x=b.x AND p.y=b.y AND p.z=b.z'):
        bridge_hosts.setdefault(key,set()).add(encoded)
    total=changed=partial=derived=wrappers=0;host_keys=set();examples=[];null_base_keys=[];a.out.parent.mkdir(parents=True,exist_ok=True);temporary=a.out.with_suffix(a.out.suffix+'.writing')
    with temporary.open('w',encoding='utf-8',newline='\n') as output:
        for row in read_lines(a.compositions):
            row,canonicalized=canonicalize_empty_null_base(row,store)
            if canonicalized:null_base_keys.append(row['key'])
            total+=1;predicates=row.get('carrierPredicates')
            if predicates is not None:require(row.get('wrapper')=='NativeGridBase','Carrier predicates outside an existing BE bridge')
            if row.get('wrapper')=='NativeGridBase':
                wrappers+=1;base=row.get('baseState');require(base is not None,'Bridge background must name its exact base state')
                record=store.execute('SELECT data FROM native WHERE state=?',(state_key(base),)).fetchone()
                require(record is not None,'Missing exact actual native base physics/optics: '+state_key(base));native=json.loads(record[0])
                if base['registryId'] in ('minecraft:air','minecraft:cave_air','minecraft:void_air'):
                    validate_air_nonoverridden_optics(native['optics'])
                fragments=[]
                for item in row.get('fragments',[]):
                    found=store.execute('SELECT data FROM fragments WHERE pose=? AND x=? AND y=? AND z=?',(item['pose'],*item['cell'])).fetchone()
                    require(found is not None,'Missing exact regular source fragment');fragments.append(json.loads(found[0]))
                ordinary=effective_optics(row,native,fragments)
                fluid=normalize_fluid(row['fluid']) if 'fluid' in row else normalize_fluid(native.get('fluid'))
                require(fluid is None and ordinary['luminance']==0,'Bridge background is wet/emitting and must remain locally preserved')
                require(not any(f.get('fluid') is not None for f in fragments),'A source fluid cannot be suppressed by an AIR carrier')
                bindings=bridge_hosts.get(row['key'],set())
                require(bindings,'No actual bridge host-state binding for raw composition '+row['key'])
                expected=[]
                for encoded in bindings:
                    host=state_key(json.loads(encoded));require(host in known,'Unknown exact carrier host state: '+host)
                    if functions is not None:full_host_decision(functions,host)
                    host_keys.add(host);expected.append(known[host])
                require(all(value==expected[0] for value in expected),'One composition is shared by incompatible observed carrier predicates')
                if predicates is not None:
                    require(row.get('optics') is None,'Ambiguous full optics plus partial carrier predicates')
                    require(predicates==expected[0],'Raw carrier predicates differ from native observation')
                    partial+=1
                else:
                    if row.get('optics') is not None:require(all(ordinary[k]==expected[0][k] for k in FLAGS),'Existing explicit bridge optics no longer match its observed host')
                    derived+=1
                # Materialize the observed flags even without raw partial fields:
                # promoted FULL plans no longer have context carrierPredicates.
                # Otherwise pinned _native(AIR) would reinstate sided=true.
                final=override_only_predicates(ordinary,expected[0])
                row=materialize_observed_carrier(row,ordinary,expected[0]);changed+=1
                if len(examples)<16:examples.append({'key':row['key'],'ordinaryOptics':ordinary,'normalizedOptics':final})
            output.write(canonical(row)+'\n')
    require(sha(a.compositions)==start,'Raw compositions changed during normalization')
    temporary.replace(a.out)
    report={'status':'PASS','mode':'FINITE_AUTHORING_INPUT_NORMALIZER','rawCompositions':str(a.compositions.absolute()),'rawCompositionsSha256':start,'normalizedCompositions':str(a.out.absolute()),'normalizedCompositionsSha256':sha(a.out),'compilerAlgorithmSha256':ALGORITHM,'normalizerSha256':sha(__file__),'rows':total,'bridgeCompositions':wrappers,'normalizedPartialCarrierRows':partial,'materializedObservedCarrierRows':changed,'derivedObservedCarrierRows':derived,'canonicalizedNullBaseRows':len(null_base_keys),'canonicalizedNullBaseKeys':null_base_keys,'onlyEmptyNullNativeBasesCanonicalized':True,'fullFunctionGate':function_proof,'verifiedActualHostStates':[json.loads(k) for k in sorted(host_keys)],'nativeAuditBindings':proof,'examples':examples,'compositionKeysPreserved':True,'onlyOpaqueAndSidedTransparencyOverridden':True,'physicalGeometryFluidLocalFunctionUnchanged':True,'catalogueWrites':0,'databaseWrites':0,'mapReads':0,'mapWrites':0}
    a.receipt.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8');print(json.dumps({k:v for k,v in report.items() if k not in ('examples','nativeAuditBindings','verifiedActualHostStates')}))
def self_test(out):
    native={'optics':{'opacity':3,'luminance':0,'ambientOcclusion':.35,'transparent':False,'sidedTransparency':True,'opaque':True,'opaqueFullCube':True,'solidBlock':True,'suffocates':True,'blockVision':True}}
    row={'baseState':{'registryId':'test:actual'}};fragments=[{'optics':{'opacity':12,'luminance':0},'collision':[[0,0,0,1,1,1]],'fluid':None}]
    normal=effective_optics(row,native,fragments);final=override_only_predicates(normal,{'opaque':False,'sidedTransparency':False})
    assert final['opacity']==12 and final['luminance']==0 and final['ambientOcclusion']==.35
    assert {k:v for k,v in final.items() if k not in FLAGS}=={k:v for k,v in normal.items() if k not in FLAGS}
    assert fragments[0]['collision']==[[0,0,0,1,1,1]]
    emitting=effective_optics(row,native,[{'optics':{'luminance':7}}]);assert emitting['luminance']==7
    actual_air={**EMPTY_OPTICS,'sidedTransparency':False}
    validate_air_nonoverridden_optics(actual_air)
    air_final=override_only_predicates(effective_optics({}, {'optics':actual_air}, []), {'opaque':False,'sidedTransparency':False})
    assert {k:v for k,v in air_final.items() if k not in FLAGS}=={k:v for k,v in EMPTY_OPTICS.items() if k not in FLAGS}
    rejected_air=False
    try:validate_air_nonoverridden_optics({**actual_air,'opacity':1})
    except ValueError:rejected_air=True
    assert rejected_air
    rejected=False
    try:override_only_predicates(normal,{'opaque':False,'sidedTransparency':False,'opacity':0})
    except ValueError:rejected=True
    assert rejected
    result={'status':'PASS','checks':['new source opacity retained','actual native AO and all other flags retained','emission retained for subsequent guard rejection','geometry input unchanged','unobserved override fields rejected','actual AIR sidedTransparency may differ only in explicitly observed overridden flags','AIR non-overridden mismatch rejected'],'compilerAlgorithmSha256':compiler_fingerprint(),'noCompilerDatabaseInstantiated':True}
    out.parent.mkdir(parents=True,exist_ok=True);out.write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8');print(json.dumps(result))
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--compositions',type=Path);p.add_argument('--raw-sha256');p.add_argument('--fragments',type=Path);p.add_argument('--plan',type=Path);p.add_argument('--audit',type=Path,action='append');p.add_argument('--full-function-gate',type=Path);p.add_argument('--out',type=Path);p.add_argument('--receipt',type=Path);p.add_argument('--self-test',type=Path);a=p.parse_args()
    if a.self_test:self_test(a.self_test);return
    require(all(getattr(a,k) for k in ('compositions','raw_sha256','fragments','plan','audit','out','receipt')),'Final handoff paths/raw SHA/native audit are required');normalize(a)
if __name__=='__main__':main()
