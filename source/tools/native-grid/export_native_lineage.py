"""Bind an additive addressed native export to immutable prior authoring rows."""
import argparse,hashlib,json,sqlite3
from pathlib import Path
from export_cells import canonical,state_key,read_lines,clipping_fingerprint
from export_merge_physics import read,require,sha
from export_merge_native_visual_fragments import normalize_native_visual,visual_clips,FIELDS

BASE='40cecfb7e5cf783a762e350fc1864518c39abff177f6f025cb94892a181fbf51'
HELPER='8ebbebae73d69c73ed31e40cce34c0d9535543e946f5359643caf773d919de20'
TABLES={'poses':('key','key'),'fragments':('pose,x,y,z','pose,x,y,z'),'native':('state','state'),'native_fragments':('state,x,y,z','state,x,y,z')}
def digest(value):return hashlib.sha256(canonical(value).encode()).hexdigest()
def write(path,value):path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
def inventory(path):
    db=sqlite3.connect('file:'+path.resolve().as_posix()+'?mode=ro',uri=True);db.execute('BEGIN');tables={}
    for table,(keys,order) in TABLES.items():
        columns=[row[1] for row in db.execute('PRAGMA table_info('+table+')')]
        keycols=keys.split(',');indices=[columns.index(k) for k in keycols]
        rows={}
        for row in db.execute('SELECT * FROM '+table+' ORDER BY '+order):rows[canonical([row[i] for i in indices])]=digest(list(row))
        tables[table]={'columns':columns,'count':len(rows),'rows':rows}
    metadata={k:v for k,v in db.execute('SELECT name,data FROM metadata') if k in ('sourceGeometrySha256','clippingAlgorithmSha256','nativeVisualClippingAlgorithmSha256')}
    db.close();return {'tables':tables,'protectedMetadata':metadata}

def visual_append(path,expected,fragments,current,current_sha):
    require(expected and sha(path)==expected,'Exact native visual-only append receipt SHA required')
    receipt=read(path)
    require(receipt['status']=='PASS' and receipt['mode']=='EXACT_NATIVE_VISUAL_FRAGMENTS_ONLY' and receipt['newNativeRows']==0 and all(receipt.get(k) is True for k in ('posesUnchanged','fragmentsUnchanged','existingNativeRowsUnchanged','existingNativeVisualFragmentsUnchanged','physicalAndOpticalFieldsEqual')),'Visual-only append changed protected native/physics/source rows')
    require(all(receipt['beforeCounts'][k]==receipt['afterCounts'][k]==current['tables'][k]['count'] for k in ('poses','fragments','native')) and receipt['afterCounts']['native_fragments']-receipt['beforeCounts']['native_fragments']==receipt['newNativeVisualFragments'],'Fragment-only append counts changed')
    for reference in (receipt['request'],receipt['nativeGeometry'],receipt['producer']):require(sha(reference['path'])==reference['sha256'],'Bound native visual append input/source changed')
    require(Path(receipt['producer']['path']).resolve()==Path(__file__).with_name('export_merge_native_visual_fragments.py').resolve(),'Unknown native fragment-only importer')
    request=read(receipt['request']['path']);binding=receipt['binding'];output=Path(receipt['nativeGeometry']['path']).parent
    launch=read(output/'launch-receipt.json');actual_binding=read(output/'binding.json')
    require(binding==actual_binding and binding['productionSha256']==launch['productionSha256']==BASE and binding['exporterSha256']==launch['exporterSha256']==HELPER and binding['requestSha256']==launch['requestSha256']==receipt['request']['sha256'] and launch['status']=='PASS' and launch['exitCode']==0 and launch['worldOpened'] is False,'Visual-only actual export binding/clean launch changed')
    require(not request.get('poses') and all(row.get('physicsOnly') is False for row in request['nativeStates']) and request['inputBindings']['fragmentBinding']['sha256']==receipt['beforeFragmentsSha256'] and Path(request['inputBindings']['fragmentBinding']['path']).resolve()==fragments.resolve(),'Fragment-only request/prefix changed')
    wanted={state_key(row) for row in request['nativeStates']};models=list(read_lines(receipt['nativeGeometry']['path']))
    require(len(wanted)==len(request['nativeStates'])==len(models)==receipt['nativeStates'] and {state_key(row['state']) for row in models}==wanted and receipt['clippingAlgorithmSha256']==clipping_fingerprint(),'Visual-only exact model/clipping coverage changed')
    for evidence in receipt['statePropertiesEvidence']:
        for reference in evidence.values():require(sha(reference['path'])==reference['sha256'],'Actual no-BE property evidence changed')
    recorded={state_key(row['state']):row for row in receipt['proofs']};require(set(recorded)==wanted,'Visual-only physical/source proof coverage changed')
    db=sqlite3.connect('file:'+fragments.resolve().as_posix()+'?mode=ro',uri=True);db.execute('BEGIN')
    try:
        for visual in models:
            normalize_native_visual(visual)
            key=state_key(visual['state']);stored=db.execute('SELECT data FROM native WHERE state=?',(key,)).fetchone()
            require(stored and hashlib.sha256(stored[0].encode()).hexdigest()==recorded[key]['existingNativeRowSha256'],'Fragment-only native physics row changed')
            native=json.loads(stored[0]);require(native.get('nativeVisualComplete') is False and all(native[field]==visual[field] for field in FIELDS) and native.get('fluid')==visual.get('fluid'),'Fragment-only native physical/optical equality changed')
            cells,_=visual_clips(visual);keys=set()
            for cell in cells:
                at=cell.pop('cell');row_key=canonical([key,*at]);keys.add(row_key)
                require(current['tables']['native_fragments']['rows'].get(row_key)==digest([key,*at,digest(cell),canonical(cell)]),'Final clipped native row differs from the actual visual export')
            require(keys=={encoded for encoded in current['tables']['native_fragments']['rows'] if json.loads(encoded)[0]==key},'Final actual visual state has missing/unbound extra clipped coordinates')
    finally:db.close()
    return wanted,{'path':str(path.resolve()),'sha256':expected,'request':receipt['request'],'nativeGeometry':receipt['nativeGeometry'],'nativeStates':len(wanted),'newNativeVisualFragments':receipt['newNativeVisualFragments'],'beforeFragmentsSha256':receipt['beforeFragmentsSha256'],'afterFragmentsSha256':receipt['afterFragmentsSha256'],'beforeCounts':receipt['beforeCounts'],'afterCounts':receipt['afterCounts'],'onlyExactActualClippedRows':True}
def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('mode',choices=('snapshot','verify'));parser.add_argument('--fragments',type=Path,required=True);parser.add_argument('--snapshot',type=Path,required=True);parser.add_argument('--generation',type=Path,required=True);parser.add_argument('--out',type=Path);parser.add_argument('--export',type=Path,action='append',default=[]);parser.add_argument('--request',type=Path,action='append',default=[]);parser.add_argument('--native-visual-append',type=Path,action='append',default=[]);parser.add_argument('--native-visual-append-sha256',action='append',default=[]);a=parser.parse_args()
    generation=read(a.generation)['compositionGeneration'];current=inventory(a.fragments);current_sha=sha(a.fragments)
    if a.mode=='snapshot':
        require(not a.snapshot.exists(),'An immutable pre-append snapshot already exists')
        require(generation['sourceFragments']['sha256']==current_sha,'Pre-append DB differs from the exact composition generation')
        report={'status':'SNAPSHOT','beforeFragmentsSha256':current_sha,'generation':{'path':str(a.generation.resolve()),'sha256':sha(a.generation),'compositionsSha256':generation['compositionsSha256']},**current,'databaseWrites':0,'mapWrites':0}
        write(a.snapshot,report);print(json.dumps({'status':'PASS','beforeFragmentsSha256':current_sha,'counts':{k:v['count'] for k,v in current['tables'].items()}}));return
    before=read(a.snapshot);require(a.out and len(a.export)==len(a.request)>0,'Exact paired exports/requests and output are required')
    require(before['generation']['compositionsSha256']==generation['compositionsSha256'],'Composition generation changed before append verification')
    require(before['protectedMetadata']==current['protectedMetadata'],'A source/clipping algorithm binding changed')
    changes={}
    for table,old in before['tables'].items():
        new=current['tables'][table];require(old['columns']==new['columns'],'An authoring table schema changed')
        require(all(new['rows'].get(k)==v for k,v in old['rows'].items()),'Prior exact rows changed in '+table)
        added=set(new['rows'])-set(old['rows']);changes[table]=added
        if table in ('poses','fragments'):require(not added,'A native-only append added SourcePose rows')
    wanted=set();bindings=[]
    for output,request in zip(a.export,a.request):
        binding=read(output/'binding.json');launch=read(output/'launch-receipt.json');merge=read(output/'merge-receipt.json')
        require(launch['status']=='PASS' and launch['exitCode']==0 and merge['status']=='PASS','A linked export/merge lacks a clean completed receipt')
        require(binding['productionSha256']==BASE,'Export uses a different immutable production JAR')
        require(binding.get('exporterSha256',binding.get('helperSha256'))==HELPER,'Export uses a different authoring helper')
        require(binding['requestSha256']==sha(request),'Exact request changed')
        document=read(request);requested=document.get('nativeStates',document.get('states',[]))
        require(not document.get('poses'),'Native-only lineage must not include source poses')
        keys={canonical([state_key(row)]) for row in requested};require(not wanted.intersection(keys),'Addressed export requests overlap');wanted.update(keys)
        bindings.append({'output':str(output.resolve()),'request':str(request.resolve()),'requestSha256':sha(request),'binding':binding,'launchReceiptSha256':sha(output/'launch-receipt.json'),'mergeReceiptSha256':sha(output/'merge-receipt.json'),'states':len(keys)})
    require(changes['native']==wanted,'Native additions differ from the exact finite requests')
    visual_proofs=[]
    require(len(a.native_visual_append)==len(a.native_visual_append_sha256),'Pair every ordered native visual append receipt with its exact SHA')
    if a.native_visual_append:
        allowed={json.loads(key)[0] for key in wanted};seen=set()
        for path,expected in zip(a.native_visual_append,a.native_visual_append_sha256):
            require(expected not in seen,'Duplicate native visual append receipt');seen.add(expected)
            extra,proof=visual_append(path,expected,a.fragments,current,current_sha)
            if visual_proofs:
                previous=visual_proofs[-1]
                require(proof['beforeFragmentsSha256']==previous['afterFragmentsSha256'] and proof['beforeCounts']==previous['afterCounts'],'Native visual append receipts are not a consecutive prefix chain')
            visual_proofs.append(proof);allowed.update(extra)
        require(visual_proofs[-1]['afterFragmentsSha256']==current_sha and visual_proofs[-1]['afterCounts']=={k:v['count'] for k,v in current['tables'].items()},'Last native visual append does not bind the actual final export store')
        require(all(json.loads(key)[0] in allowed for key in changes['native_fragments']),'New native visual fragments name an unrequested native state')
    result={'status':'PASS','mode':'STRICT_ADDITIVE_NATIVE_EXPORT_LINEAGE','beforeFragmentsSha256':before['beforeFragmentsSha256'],'afterFragmentsSha256':current_sha,'snapshot':{'path':str(a.snapshot.resolve()),'sha256':sha(a.snapshot)},'posesUnchanged':True,'fragmentsUnchanged':True,'existingNativeRowsUnchanged':True,'existingNativeVisualFragmentsUnchanged':True,'newNativeRows':len(changes['native']),'newNativeVisualFragments':len(changes['native_fragments']),'beforeCounts':{k:v['count'] for k,v in before['tables'].items()},'afterCounts':{k:v['count'] for k,v in current['tables'].items()},'exports':bindings,'mapReads':0,'mapWrites':0,'databaseWrites':0}
    if visual_proofs:
        result['nativeVisualAppends']=visual_proofs
        if len(visual_proofs)==1:result['nativeVisualAppend']=visual_proofs[0]
    write(a.out,result);print(json.dumps(result))
if __name__=='__main__':main()
