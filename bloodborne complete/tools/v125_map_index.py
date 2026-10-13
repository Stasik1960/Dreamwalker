"""Readonly original v16 ZIP cells and exact source-XYZ v15 applications.

Only a derived SQLite cache/reports are written. No archive extraction, world
mutation, game process or guessed model choice is involved.
"""
from __future__ import annotations
import argparse, ast, collections, hashlib, itertools, json, math, sqlite3, time, zipfile
from pathlib import Path, PurePosixPath
import numpy as np
from scan_original_map_models import branches_for, raw_blockstates, normalized_model, weighted_indices
from scan_original_map_states import decode_indices, state_details, dimension_for, EXPECTED_MAP_SHA
from world_io import RegionFile, compound

# Import only the existing pure geometry functions. Importing the whole asset
# writer would unnecessarily require its Pillow renderer in this scan runtime.
_geometry_path=Path(__file__).with_name('import_remaining_assets.py')
_geometry_ast=ast.parse(_geometry_path.read_text(encoding='utf-8-sig'))
_geometry_nodes=[n for n in _geometry_ast.body if isinstance(n,ast.FunctionDef) and n.name in {'rotation','mv','rotate_point','transformed_corners'}]
_geometry_globals={'math':math,'itertools':itertools}
exec(compile(ast.Module(body=_geometry_nodes,type_ignores=[]),str(_geometry_path),'exec'),_geometry_globals)
transformed_corners=_geometry_globals['transformed_corners']

ROOT = Path(__file__).resolve().parents[1]
REF = ROOT.parent / 'Bloodborne-Blocks/reference-inputs/bbmc-v15-v16'
MAP = REF / 'bbmc_v16_map.zip'
PACK = REF / 'bbmc_v15_resource.zip'
OUT = ROOT / 'docs/v12.5-city-review/map'
CACHE = ROOT / 'build/v125-original-map.sqlite'
BASELINE = '653ae307ba4c44392ee08969f3890321e95659bb'
PACK_SHA = '29b31e3744af5794ae95ebe4c5474e3b70dfb08c143355bce815c08ceef09a36'

def sha(path): return hashlib.sha256(path.read_bytes()).hexdigest()
def dump(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
def token(value): return json.dumps(value, sort_keys=True, separators=(',', ':'))
def app_clean(value):
    return {k:v for k,v in value.items() if k not in {'weight'} and not (k in {'x','y','uvlock'} and not v)}
def models_metadata():
    inventory=json.loads((ROOT/'docs/remaining-transfer/source-inventory.json').read_text(encoding='utf-8-sig'))
    result={}
    for row in inventory['models']:
        points=[p for e in row['resolved'].get('elements',[]) if e.get('faces') for p in transformed_corners(e, {'model':row['model']})]
        bounds=[min(p[i] for p in points)/16 for i in range(3)]+[max(p[i] for p in points)/16 for i in range(3)] if points else None
        full=any(e.get('from')==[0,0,0] and e.get('to')==[16,16,16] and len(e.get('faces',{}))==6 and not e.get('rotation',{}).get('angle') for e in row['resolved'].get('elements',[]))
        result[row['model']]={'model':row['model'], 'bounds':bounds, 'sourceSha256':row['sha256'], 'elements':len(row['resolved'].get('elements',[])), 'fullCubeContext':full, 'hasGeometry':bool(points), 'issues':row['resolved'].get('issues',[]), 'textures':row.get('textures',[])}
    return result

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--cache',type=Path,default=CACHE);parser.add_argument('--output',type=Path,default=OUT);parser.add_argument('--finalize-existing-complete-cache',action='store_true');args=parser.parse_args()
    if args.finalize_existing_complete_cache:
        con=sqlite3.connect(args.cache);previous=json.loads((ROOT/'docs/map-pack-absence/model-usage.json').read_text())
        statecounts=collections.Counter();modelcounts=collections.Counter()
        for (rawmetric,) in con.execute('SELECT data FROM chunk_metrics'):
            row=json.loads(rawmetric);statecounts.update(row['carrierStateCounts']);modelcounts.update(row['modelCounts'])
        assert dict(statecounts)==previous['state_counts']
        assert {k:v for k,v in modelcounts.items() if v}=={k:v['count'] for k,v in previous['direct_models'].items()}
        progress=json.loads((args.output/'scan-progress.json').read_text())
        assert progress['regionsScanned']==progress['regionsTotal']==47 and progress['chunks']==previous['chunks_scanned'] and progress['sections']==previous['sections_scanned']
        original={p.name:sha(p) for p in [MAP,PACK]};assert original[MAP.name]==EXPECTED_MAP_SHA and original[PACK.name]==PACK_SHA
        result={'schemaVersion':1,'status':'PASS','baselineCommit':BASELINE,'readOnlyOriginalArchives':True,'noExtraction':True,'sourceHashes':original,'weightedSelection':'Exact original XYZ, validated Minecraft1.20.1 WeightedBakedModel + MultipartBakedModel independent groups','indexScope':'All actual non-full-cube finite model cells including tiny/zero-thickness helpers; full cube background retained in exhaustive chunk metrics. Structural adjacency is a candidate, never automatic logical-object merge.','chunks':progress['chunks'],'sections':progress['sections'],'indexedFiniteCells':con.execute('SELECT count(*) FROM cells').fetchone()[0],'observedStates':len(statecounts),'observedModels':sum(v>0 for v in modelcounts.values()),'applicationCombinations':con.execute('SELECT count(*) FROM applications').fetchone()[0],'cache':str(args.cache),'elapsedSeconds':progress['elapsedSeconds'],'sourceCountCrossCheck':'PASS','zeroCountOptionsOmittedLikeIndependentScanner':True,'models':models_metadata()}
        for sql in ['CREATE INDEX IF NOT EXISTS cells_xyz ON cells(dimension,x,y,z)','CREATE INDEX IF NOT EXISTS cells_apps ON cells(applications)','CREATE INDEX IF NOT EXISTS cells_states ON cells(state)']:con.execute(sql)
        for key,val in result.items():con.execute('INSERT OR REPLACE INTO metadata VALUES(?,?)',(key,token(val)))
        con.commit();con.close();dump(args.output/'original-map-index.json',result);dump(args.output/'scan-progress.json',{k:v for k,v in result.items() if k!='models'});return
    if args.cache.exists(): raise FileExistsError('Derived cache exists; inspect it instead of overwriting')
    args.cache.parent.mkdir(parents=True,exist_ok=True);args.output.mkdir(parents=True,exist_ok=True)
    original={p.name:sha(p) for p in [MAP,PACK]}
    assert original[MAP.name]==EXPECTED_MAP_SHA and original[PACK.name]==PACK_SHA
    raw, direct, affected=raw_blockstates(); meta=models_metadata()
    con=sqlite3.connect(args.cache);con.execute('PRAGMA journal_mode=OFF');con.execute('PRAGMA synchronous=OFF')
    con.executescript('CREATE TABLE states(id INTEGER PRIMARY KEY,state TEXT UNIQUE,name TEXT,properties TEXT); CREATE TABLE applications(id INTEGER PRIMARY KEY,apps TEXT UNIQUE,finite INTEGER,multipart INTEGER); CREATE TABLE cells(dimension TEXT,x INTEGER,y INTEGER,z INTEGER,state INTEGER,applications INTEGER); CREATE TABLE chunk_metrics(dimension TEXT,cx INTEGER,cz INTEGER,data TEXT,PRIMARY KEY(dimension,cx,cz)); CREATE TABLE metadata(key TEXT PRIMARY KEY,data TEXT);')
    stateids={};groups={};appids={};statecounts=collections.Counter();modelcounts=collections.Counter(); finitecount=0;chunks=0;sections=0;start=time.monotonic()
    with zipfile.ZipFile(MAP,'r') as archive:
        entries=sorted(n for n in archive.namelist() if n.endswith('.mca') and PurePosixPath(n).parent.name=='region')
        for rn,entry in enumerate(entries,1):
            dim=dimension_for(entry,''); region=RegionFile(archive.read(entry))
            for stored in region.chunks():
                root=compound(stored.nbt().root);root=compound(root['Level']) if 'Level' in root else root
                cx,cz=int(root['xPos'].value),int(root['zPos'].value);chunks+=1
                chunkmodels=collections.Counter();chunkstates=collections.Counter();finiteys=[];buffer=[]
                for section in (root.get('sections') or root.get('Sections')).value:
                    fields=compound(section);packed=fields.get('block_states')
                    if packed is None:continue
                    sections+=1;data=compound(packed);palette=data['palette'].value;desc=[state_details(v) for v in palette]
                    wanted=[n for n,(_,name,_) in enumerate(desc) if name in raw]
                    if not wanted:continue
                    indices=decode_indices(data.get('data').value if data.get('data') else [],len(palette));sy=int(fields['Y'].value)
                    for pi in wanted:
                        at=np.flatnonzero(indices==pi)
                        if not len(at):continue
                        key,name,props=desc[pi];statecounts[key]+=len(at);chunkstates[key]+=len(at)
                        if key not in stateids:
                            stateids[key]=len(stateids)+1;groups[key]=branches_for(raw[name],props)
                            con.execute('INSERT INTO states VALUES(?,?,?,?)',(stateids[key],key,name,token(props)))
                        xyz=np.column_stack((cx*16+(at&15),sy*16+(at>>8),cz*16+((at>>4)&15))).astype(np.int64)
                        selections=[];multipart='multipart' in raw[name]
                        for label,group in groups[key]:
                            choices=weighted_indices([o[0].get('weight',1) for o in group],xyz,multipart) if len(group)>1 else np.zeros(len(at),dtype=np.int16)
                            apps=[app_clean({**o[0],'model':normalized_model(o[0]['model'])}) for o in group];selections.append((choices,apps))
                            for oi,n in enumerate(np.bincount(choices,minlength=len(group))):
                                modelcounts[apps[oi]['model']]+=int(n);chunkmodels[apps[oi]['model']]+=int(n)
                        # No position rows for simple full-cube texture background. It is
                        # still included in full chunk metrics / independent count check.
                        possible=[a for _,apps in selections for a in apps]
                        eligible=any(meta.get(a['model'],{}).get('hasGeometry') and not meta.get(a['model'],{}).get('fullCubeContext') for a in possible)
                        if not eligible:continue
                        for n,pos in enumerate(xyz.tolist()):
                            apps=sorted([a[int(c[n])] for c,a in selections],key=token)
                            finite=any(meta.get(a['model'],{}).get('hasGeometry') and not meta.get(a['model'],{}).get('fullCubeContext') for a in apps)
                            if not finite:continue
                            tok=token(apps)
                            if tok not in appids:
                                appids[tok]=len(appids)+1;con.execute('INSERT INTO applications VALUES(?,?,?,?)',(appids[tok],tok,int(finite),int(multipart)))
                            buffer.append((dim,*pos,stateids[key],appids[tok]));finiteys.append(pos[1]);finitecount+=1
                con.executemany('INSERT INTO cells VALUES(?,?,?,?,?,?)',buffer)
                if chunkmodels:
                    cm={'modelCounts':dict(chunkmodels),'carrierStateCounts':dict(chunkstates),'finiteCells':len(buffer),'finiteYMin':min(finiteys) if finiteys else None,'finiteYMax':max(finiteys) if finiteys else None}
                    con.execute('INSERT INTO chunk_metrics VALUES(?,?,?,?)',(dim,cx,cz,token(cm)))
            con.commit();dump(args.output/'scan-progress.json',{'status':'RUNNING','region':entry,'regionsScanned':rn,'regionsTotal':len(entries),'chunks':chunks,'sections':sections,'indexedFiniteCells':finitecount,'elapsedSeconds':round(time.monotonic()-start,2)})
    previous=json.loads((ROOT/'docs/map-pack-absence/model-usage.json').read_text())
    assert dict(statecounts)==previous['state_counts'], 'Actual-cell counts must equal independent original map scan'
    assert {k:v for k,v in modelcounts.items() if v}=={k:v['count'] for k,v in previous['direct_models'].items()}, 'Exact weighted application counts must equal independent validated scan'
    assert chunks==previous['chunks_scanned'] and sections==previous['sections_scanned']
    assert original=={p.name:sha(p) for p in [MAP,PACK]}
    result={'schemaVersion':1,'status':'PASS','baselineCommit':BASELINE,'readOnlyOriginalArchives':True,'noExtraction':True,'sourceHashes':original,'weightedSelection':'Exact original XYZ, validated Minecraft1.20.1 WeightedBakedModel + MultipartBakedModel independent groups','indexScope':'All actual non-full-cube finite model cells including tiny/zero-thickness helpers; full cube background retained in exhaustive chunk metrics. Structural adjacency is a candidate, never automatic logical-object merge.','chunks':chunks,'sections':sections,'indexedFiniteCells':finitecount,'observedStates':len(statecounts),'observedModels':len(modelcounts),'applicationCombinations':len(appids),'cache':str(args.cache),'elapsedSeconds':round(time.monotonic()-start,2),'sourceCountCrossCheck':'PASS','models':meta}
    con.execute('CREATE INDEX cells_xyz ON cells(dimension,x,y,z)');con.execute('CREATE INDEX cells_apps ON cells(applications)');con.execute('CREATE INDEX cells_states ON cells(state)')
    for key,val in result.items():con.execute('INSERT INTO metadata VALUES(?,?)',(key,token(val)))
    con.commit();con.close();dump(args.output/'original-map-index.json',result);dump(args.output/'scan-progress.json',{k:v for k,v in result.items() if k!='models'})
    print(token({k:v for k,v in result.items() if k!='models'}))

if __name__=='__main__':
    try:main()
    except BaseException:
        import traceback
        OUT.mkdir(parents=True,exist_ok=True)
        (OUT/'scan-error.txt').write_text(traceback.format_exc(),encoding='utf-8')
        raise
