"""Save a completed authoring handoff: legacy phase2 or explicit FULL proofs."""
import argparse,json,sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent.parent))
from export_merge_physics import require,sha,read
from export_cells import read_lines
ROOT=Path(__file__).resolve().parents[4];R=ROOT/'research';C=R/'native-grid-plan/context-extension';RES=ROOT/'source/src/architecture/resources/assets/bloodborne_dw/native_grid'
def phase2():
    prefix=read(C/'prefix-after-append.json');stats=read(C/'catalog-stats.json');bridge=read(C/'catalog-bridge-proof.json');inputs=read(C/'append-input-binding.json');mapping=R/'type-mapping.jsonl'
    require(all(doc['status']=='PASS' for doc in (prefix,stats,bridge,inputs)),'A completed pipeline proof did not pass')
    require(prefix['catalogSha256']==stats['catalogSha256']==bridge['catalogSha256']==sha(RES/'catalog.json'),'Frozen catalogue binding differs')
    require(prefix['meshesSha256']==stats['meshesSha256']==sha(RES/'meshes.bin'),'Frozen mesh binding differs')
    require(prefix['mappingSha256']==sha(mapping),'Frozen type mapping changed')
    rows={row['key']:row['typeIndex'] for row in read_lines(mapping)};current={r['key'] for r in read_lines(C/'compositions.normalized.jsonl')}
    require(current<=rows.keys(),'A final actual composition has no frozen type mapping')
    report={'status':'FROZEN_RESOURCES_READY','kind':'COMPLETED_PHASE2_CATALOG_AUTHORING_HANDOFF','catalog':{'path':str(RES/'catalog.json'),'sha256':prefix['catalogSha256']},'meshes':{'path':str(RES/'meshes.bin'),'sha256':prefix['meshesSha256']},'mapping':{'path':str(mapping),'sha256':prefix['mappingSha256'],'allKeys':len(rows),'finalActualCompositionKeys':len(current)},'inputBindings':inputs,'prefixProof':{'path':str(C/'prefix-after-append.json'),'sha256':sha(C/'prefix-after-append.json')},'resourceStatistics':stats,'bridgeProof':{'path':str(C/'catalog-bridge-proof.json'),'sha256':sha(C/'catalog-bridge-proof.json'),'wrapperTypes':bridge['uniqueBackgroundCellTypes'],'allDryLuminanceZeroObservedPredicatesMatch':True},'compilerReceiptSha256':sha(RES/'compiler-receipt.json'),'productionBuildExecutedByThisStep':False,'newJarNativeBridgeAuditStillRequired':True,'mapReads':0,'mapWrites':0,'databaseWrites':0,'resourceWrites':0}
    out=C/'frozen-catalog.json';out.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8');print(json.dumps({'status':report['status'],'receipt':str(out),'sha256':sha(out),'allMappingKeys':len(rows),'finalActualCompositionKeys':len(current)}))

def mapping_coverage(path,normalized,cell_count):
    """One bounded merge over existing sorted files; no preflight is rerun."""
    wanted=iter(read_lines(normalized));current=next(wanted,None);previous=None;total=covered=0
    for row in read_lines(path):
        key=row['key'];index=row['typeIndex'];require(previous is None or previous<key,'Mapping keys are not unique and sorted');previous=key
        require(type(index)is int and 0<=index<cell_count,'Mapped type is outside the actual catalogue');total+=1
        if current is not None:
            require(key<=current['key'],'A final actual composition lacks its compiled mapping: '+current['key'])
            if key==current['key']:covered+=1;current=next(wanted,None)
    require(current is None,'A final actual composition lacks its compiled mapping')
    return total,covered
def full(a):
    from catalog_append_gate import ALGORITHM,bound,checked_proof,save_new
    from catalog import compiler_fingerprint
    from catalog_prefix import KINDS
    names=('append_inputs','prefix_proof','stats','bridge_proof')
    require(all(getattr(a,k) is not None and getattr(a,k+'_sha256') for k in names),'FULL freeze requires explicit completed proof paths and SHA256')
    require(all(getattr(a,k) is not None for k in ('resources','mapping','database','out')),'FULL resources/mapping/SAME database/output paths are required')
    artifacts={name:bound(getattr(a,name),getattr(a,name+'_sha256')) for name in names}
    inputs=read(a.append_inputs);prefix=read(a.prefix_proof);stats=read(a.stats);bridge=read(a.bridge_proof)
    require(inputs['status']=='PASS' and inputs['mode']=='EXPLICIT_FULL_APPEND_INPUT_BINDING','The actual FULL append input binding did not pass')
    require(all(doc['status']=='PASS' for doc in (prefix,stats,bridge)),'A completed FULL output proof did not pass')
    require(inputs['compilerAlgorithmSha256']==compiler_fingerprint()==ALGORITHM,'Pinned compiler changed during FULL append')
    require(Path(inputs['resources']).resolve()==a.resources.resolve() and Path(inputs['mapping']).resolve()==a.mapping.resolve() and Path(inputs['catalogDatabase']).resolve()==a.database.resolve() and a.database.is_file(),'Freeze must bind the actual existing compiler output paths and SAME database')
    old_binding=inputs['prefixSnapshot'];checked_proof(old_binding['manifest']);old=read(old_binding['manifest']['path'])
    require(prefix['priorCatalogSha256']==old['catalogSha256'] and prefix['priorMeshesSha256']==old['meshesSha256'] and prefix['priorMappingSha256']==old['mappingSha256'],'Existing strict prefix proof belongs to another frozen baseline')
    require(all(prefix.get(k)is True for k in ('allPriorIndexedRowsExact','meshPayloadPrefixExact','priorRealCellMarkersExact')) and prefix['previousCounts']==old['counts'] and prefix['existingMappingKeysPreserved']==old['mappingKeys'],'Strict prior indexed/mesh/marker/mapping prefix did not pass')
    # Consume the completed prefix proof once, without calling its verifier again.
    for proof in inputs['inputs'].values():checked_proof(proof)
    for proof in inputs['sourceProofs'].values():checked_proof(proof)
    catalog_path=a.resources/'catalog.json';mesh_path=a.resources/'meshes.bin';catalog_sha=sha(catalog_path);mesh_sha=sha(mesh_path);mapping_sha=sha(a.mapping)
    require(prefix['catalogSha256']==stats['catalogSha256']==bridge['catalogSha256']==catalog_sha,'FULL frozen catalogue/output proof binding differs')
    require(prefix['meshesSha256']==stats['meshesSha256']==mesh_sha and prefix['mappingSha256']==mapping_sha,'FULL frozen mesh/mapping proof binding differs')
    doc=read(catalog_path);counts={kind:len(doc[kind]) for kind in KINDS}
    require(prefix['currentCounts']==counts,'Strict output proof counts differ from actual catalogue bytes')
    for field,kind in (('cells','cells'),('wholeObjects','wholeObjects'),('sharedMeshes','meshes'),('sharedShapes','shapes'),('sharedOptics','optics'),('materials','materials')):
        require(stats[field]==counts[kind],'Derived actual output statistics differ: '+field)
    require(stats['meshBytes']==mesh_path.stat().st_size and stats['catalogBytes']==catalog_path.stat().st_size,'Runtime resource byte counts differ')
    compiler=read(a.resources/'compiler-receipt.json');require(compiler['status']=='PASS' and compiler['compilerAlgorithmSha256']==ALGORITHM and compiler['banks']==counts and compiler['catalogSha256']==catalog_sha and compiler['meshesSha256']==mesh_sha,'Actual compiler receipt/bank counts differ')
    normalized=inputs['inputs']['normalized'];require(bridge['compositionsSha256']==normalized['sha256'] and bridge['wrapperCompositions']==inputs['derivedCounts']['bridgeCompositions'],'Bridge background proof belongs to another actual FULL composition set')
    require(bridge['allCompiledBackgroundsFluidEmpty'] is True and bridge['allCompiledBackgroundsLuminanceZero'] is True,'A FULL compiled bridge background is wet/emitting')
    if bridge['wrapperCompositions']:require(bridge.get('allCompiledBackgroundsMatchObservedCarrierFlags')is True,'Actual per-host observed carrier correspondence must pass before freeze')
    whole_rows=list(read_lines(inputs['inputs']['whole_objects']['path']));whole_keys={row['key'] for row in whole_rows};compiled_keys=[row['key'] for row in doc['wholeObjects']]
    require(len(whole_keys)==len(whole_rows)==counts['wholeObjects']==inputs['derivedCounts']['wholeTemplates'] and len(set(compiled_keys))==len(compiled_keys) and set(compiled_keys)==whole_keys,'Actual compiled whole-template keys/counts differ from the accepted finite input')
    all_keys,current=mapping_coverage(a.mapping,normalized['path'],counts['cells']);require(current==inputs['derivedCounts']['compositions'],'Actual final mapped composition count differs')
    report={'schemaVersion':1,'status':'FROZEN_RESOURCES_READY','kind':'COMPLETED_FULL_CATALOG_AUTHORING_HANDOFF','catalog':{'path':str(catalog_path.resolve()),'sha256':catalog_sha},'meshes':{'path':str(mesh_path.resolve()),'sha256':mesh_sha},'mapping':{'path':str(a.mapping.resolve()),'sha256':mapping_sha,'allKeys':all_keys,'finalActualCompositionKeys':current},'catalogDatabase':str(a.database.resolve()),'inputBinding':artifacts['append_inputs'],'inputBindings':inputs,'prefixProof':artifacts['prefix_proof'],'resourceStatistics':stats,'statisticsProof':artifacts['stats'],'bridgeProof':{**artifacts['bridge_proof'],'wrapperTypes':bridge['uniqueBackgroundCellTypes'],'allDryLuminanceZeroObservedPredicatesMatch':True},'compilerReceipt':bound(a.resources/'compiler-receipt.json'),'derivedCounts':counts,'newJarNativeBridgeAuditStillRequired':True,'productionBuildExecutedByThisStep':False,'mapReads':0,'mapWrites':0,'databaseWrites':0,'resourceWrites':0,'checkersExecuted':0,'prefixSnapshotCreated':False}
    save_new(a.out,report);print(json.dumps({'status':report['status'],'receipt':str(a.out),'sha256':sha(a.out),'derivedCounts':counts,'allMappingKeys':all_keys,'finalActualCompositionKeys':current}))
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--mode',choices=('phase2','full'),default='phase2')
    for name in ('append_inputs','prefix_proof','stats','bridge_proof'):
        p.add_argument('--'+name.replace('_','-'),type=Path);p.add_argument('--'+name.replace('_','-')+'-sha256')
    for name in ('resources','mapping','database','out'):p.add_argument('--'+name.replace('_','-'),type=Path)
    a=p.parse_args()
    if a.mode=='phase2':
        require(not any(v is not None for k,v in vars(a).items() if k!='mode'),'Explicit FULL proof paths require --mode full');phase2()
    else:full(a)
if __name__=='__main__':main()
