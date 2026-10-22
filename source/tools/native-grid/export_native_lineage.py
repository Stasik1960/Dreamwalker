"""Bind an additive addressed native export to immutable prior authoring rows."""
import argparse,hashlib,json,sqlite3
from pathlib import Path
from export_cells import canonical,state_key
from export_merge_physics import read,require,sha

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
def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('mode',choices=('snapshot','verify'));parser.add_argument('--fragments',type=Path,required=True);parser.add_argument('--snapshot',type=Path,required=True);parser.add_argument('--generation',type=Path,required=True);parser.add_argument('--out',type=Path);parser.add_argument('--export',type=Path,action='append',default=[]);parser.add_argument('--request',type=Path,action='append',default=[]);a=parser.parse_args()
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
    result={'status':'PASS','mode':'STRICT_ADDITIVE_NATIVE_EXPORT_LINEAGE','beforeFragmentsSha256':before['beforeFragmentsSha256'],'afterFragmentsSha256':current_sha,'snapshot':{'path':str(a.snapshot.resolve()),'sha256':sha(a.snapshot)},'posesUnchanged':True,'fragmentsUnchanged':True,'existingNativeRowsUnchanged':True,'existingNativeVisualFragmentsUnchanged':True,'newNativeRows':len(changes['native']),'newNativeVisualFragments':len(changes['native_fragments']),'beforeCounts':{k:v['count'] for k,v in before['tables'].items()},'afterCounts':{k:v['count'] for k,v in current['tables'].items()},'exports':bindings,'mapReads':0,'mapWrites':0,'databaseWrites':0}
    write(a.out,result);print(json.dumps(result))
if __name__=='__main__':main()
