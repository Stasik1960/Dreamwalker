"""Freeze/verify the exact prior catalogue prefix without modifying any database."""
import argparse,hashlib,json,re,shutil,sqlite3,struct
from pathlib import Path
from catalog import compiler_fingerprint

KINDS=('materials','shapes','optics','meshes','cells','wholeObjects')
CATALOG='4306e64f6f7bbbf47b4c0fe498d3201b16045e2b590309a087c4c85771867afe'
MESHES='4b6ec5938dfbbb6d3b2134053fc8ee285ff008163036c06642cce118769a72a5'
MAPPING='fca0a45c7b782e6b93bac93802fd2c958af17e4f987be94a25a4776d03fde6f6'
ALGORITHM='111b89688889fa961f2ae19bc5e0f0683d2ec4c9bfa8fbcf0b36117a5e96e13f'
def read(path):return json.loads(Path(path).read_text(encoding='utf-8-sig'))
def sha(path):return hashlib.sha256(Path(path).read_bytes()).hexdigest()
def write(path,data):Path(path).write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
def require(test,message):
    if not test:raise ValueError(message)
def mapping(path):
    result={}
    for line in Path(path).read_text(encoding='utf-8').splitlines():
        r=json.loads(line);require(r['key'] not in result,'Duplicate mapping key');result[r['key']]=r['typeIndex']
    return result
def ro(path):return sqlite3.connect('file:'+Path(path).absolute().as_posix()+'?mode=ro',uri=True)
def baseline_hashes(a):
    supplied=[getattr(a,'baseline_'+kind+'_sha256',None) for kind in ('catalog','meshes','mapping')]
    require(not any(supplied) or all(supplied),'Explicit baseline requires catalogue, mesh and mapping hashes together')
    if not any(supplied):return CATALOG,MESHES,MAPPING
    require(all(re.fullmatch(r'[0-9a-f]{64}',value) for value in supplied),'Explicit baseline hashes must be exact lowercase SHA256 values')
    return tuple(supplied)
def db_rows(path):
    with ro(path) as db:
        require(json.loads(db.execute("SELECT data FROM metadata WHERE name='compilerAlgorithmSha256'").fetchone()[0])==ALGORITHM,'Pinned DB compiler fingerprint differs')
        return {'banks':[{'kind':k,'idx':i,'digest':d,'dataSha256':hashlib.sha256(data).hexdigest()} for k,i,d,data in db.execute('SELECT kind,idx,digest,data FROM banks ORDER BY kind,idx')],'ingredients':dict(db.execute('SELECT signature,cell FROM ingredients')),'mapping':dict(db.execute('SELECT key,cell FROM mapping'))}
def existing_marker_paths(resources,cell_count):
    namespace=resources.parent;banks=(cell_count+127)//128
    return [namespace/'models/block'/('native_grid_%s_%s.json'%(i//128,i%128)) for i in range(cell_count)]+[namespace/'blockstates'/('native_grid_%s.json'%i) for i in range(banks)]+[namespace/'models/item'/('native_grid_%s.json'%i) for i in range(banks)]
def snapshot(a):
    require(not a.snapshot.exists(),'Prefix snapshot must be new; never overwrite its evidence')
    catalog_hash,mesh_hash,mapping_hash=baseline_hashes(a)
    require(sha(a.resources/'catalog.json')==catalog_hash,'Explicit frozen catalogue baseline differs')
    require(sha(a.resources/'meshes.bin')==mesh_hash,'Explicit frozen mesh baseline differs')
    require(sha(a.mapping)==mapping_hash,'Explicit frozen mapping baseline differs')
    require(compiler_fingerprint()==ALGORITHM,'Compiler algorithm changed before prefix freeze')
    doc=read(a.resources/'catalog.json');banks=db_rows(a.database)
    a.snapshot.mkdir(parents=True)
    for name in ('catalog.json','meshes.bin','compiler-receipt.json'):shutil.copy2(a.resources/name,a.snapshot/name)
    shutil.copy2(a.mapping,a.snapshot/'type-mapping.jsonl');write(a.snapshot/'db-prefix.json',banks)
    marks={p.relative_to(a.resources.parent).as_posix():sha(p) for p in existing_marker_paths(a.resources,len(doc['cells']))}
    write(a.snapshot/'marker-hashes.json',marks)
    result={'status':'PREFIX_FROZEN','schemaVersion':1,'catalogSha256':catalog_hash,'meshesSha256':mesh_hash,'mappingSha256':mapping_hash,'compilerAlgorithmSha256':ALGORITHM,'counts':{k:len(doc[k]) for k in KINDS},'mappingKeys':len(mapping(a.mapping)),'protectedRealCellMarkers':len(marks),'unusedPartMarkersNotProtected':((len(doc['cells'])+127)//128)*128-len(doc['cells']),'files':{p.name:sha(p) for p in a.snapshot.iterdir() if p.is_file()},'sourceDatabaseModified':False,'sourceResourcesModified':False}
    write(a.snapshot/'manifest.json',result);return result
def verify(a):
    old=read(a.snapshot/'catalog.json');new=read(a.resources/'catalog.json');manifest=read(a.snapshot/'manifest.json')
    for name,digest in manifest['files'].items():require(sha(a.snapshot/name)==digest,'Snapshot evidence changed: '+name)
    require(compiler_fingerprint()==ALGORITHM,'Compiler algorithm changed during append')
    require((old['schemaVersion'],old['bankSize'])==(new['schemaVersion'],new['bankSize']),'Catalogue schema/bank size changed')
    for kind in KINDS:require(new[kind][:len(old[kind])]==old[kind],'Prior indexed '+kind+' prefix changed')
    before=(a.snapshot/'meshes.bin').read_bytes();after=(a.resources/'meshes.bin').read_bytes()
    require(before[:4]==after[:4]==b'NGQ1','Mesh magic differs')
    require(struct.unpack('>i',after[4:8])[0]==len(new['meshes']),'New mesh header count differs')
    require(after[8:len(before)]==before[8:],'Prior mesh-record payload prefix changed')
    previous=mapping(a.snapshot/'type-mapping.jsonl');current=mapping(a.mapping)
    for key,index in previous.items():require(current.get(key)==index,'Prior composition mapping changed: '+key)
    saved_db=read(a.snapshot/'db-prefix.json');new_db=db_rows(a.database);lookup={(r['kind'],r['idx']):r for r in new_db['banks']}
    for row in saved_db['banks']:require(lookup.get((row['kind'],row['idx']))==row,'Prior persistent bank entry changed')
    for table in ('mapping','ingredients'):
        for key,index in saved_db[table].items():require(new_db[table].get(key)==index,'Prior DB '+table+' meaning changed')
    for rel,digest in read(a.snapshot/'marker-hashes.json').items():require(sha(a.resources.parent/rel)==digest,'Prior real-cell marker changed: '+rel)
    result={'status':'PASS','schemaVersion':1,'priorCatalogSha256':manifest['catalogSha256'],'priorMeshesSha256':manifest['meshesSha256'],'priorMappingSha256':manifest['mappingSha256'],'catalogSha256':sha(a.resources/'catalog.json'),'meshesSha256':sha(a.resources/'meshes.bin'),'mappingSha256':sha(a.mapping),'previousCounts':manifest['counts'],'currentCounts':{k:len(new[k]) for k in KINDS},'existingMappingKeysPreserved':len(previous),'allPriorIndexedRowsExact':True,'meshPayloadPrefixExact':True,'meshCountHeaderMayIncrease':True,'priorRealCellMarkersExact':True,'databaseReadOnly':True}
    if a.out:write(a.out,result)
    return result
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('mode',choices=('snapshot','verify'));p.add_argument('--snapshot',type=Path,required=True);p.add_argument('--resources',type=Path,required=True);p.add_argument('--database',type=Path,required=True);p.add_argument('--mapping',type=Path,required=True);p.add_argument('--out',type=Path)
    for kind in ('catalog','meshes','mapping'):p.add_argument('--baseline-'+kind+'-sha256',help='Snapshot only: exact explicit frozen baseline; all three required together')
    a=p.parse_args()
    require(a.mode=='snapshot' or not any(getattr(a,'baseline_'+k+'_sha256') for k in ('catalog','meshes','mapping')),'Verification binds the immutable snapshot manifest, not new baseline arguments')
    print(json.dumps(snapshot(a) if a.mode=='snapshot' else verify(a),ensure_ascii=False))
if __name__=='__main__':main()
