"""Select a bounded dense original-city fragment; export raw clip + exact apps.

No original world write/extraction. The dense source block palette includes air;
all original occupied cells are kept. Typed block entity data is inert evidence.
"""
from __future__ import annotations
import argparse, collections, gzip, hashlib, json, math, sqlite3, time, zipfile
from pathlib import Path,PurePosixPath
import numpy as np
from v125_map_index import ROOT,MAP,PACK,CACHE,BASELINE,OUT,dump,sha,token
from v125_map_resolve import Resolver,source_uuid
from scan_original_map_models import branches_for,raw_blockstates,normalized_model,weighted_index
from scan_original_map_states import decode_indices,state_details,dimension_for
from world_io import RegionFile,compound

DEST=ROOT/'docs/v12.5-city-review/citycandidate'
def typed(value):
    if value.type==10:return {'type':10,'value':{k:typed(v) for k,v in value.value.items()}}
    if value.type==9:return {'type':9,'listType':value.list_type,'value':[typed(v) for v in value.value]}
    if value.type==7:return {'type':7,'encoding':'hex','value':value.value.hex()}
    return {'type':value.type,'value':value.value}

def read_box(box):
    x0,y0,z0,x1,y1,z1=box;sx=x1-x0+1;sy=y1-y0+1;sz=z1-z0+1
    palette=['minecraft:air'];pids={'minecraft:air':0};dense=np.zeros(sx*sy*sz,dtype=np.uint16);states={};blocks=[];bes=[];chunks=0;statuses=collections.Counter()
    with zipfile.ZipFile(MAP,'r') as archive:
        for rx in range(x0//512,x1//512+1):
            for rz in range(z0//512,z1//512+1):
                name=f'region/r.{rx}.{rz}.mca'
                if name not in archive.namelist():continue
                for stored in RegionFile(archive.read(name)).chunks():
                    cx,cz=rx*32+stored.x,rz*32+stored.z
                    if cx*16>x1 or cx*16+15<x0 or cz*16>z1 or cz*16+15<z0:continue
                    root=compound(stored.nbt().root);root=compound(root['Level']) if 'Level' in root else root;chunks+=1;statuses[root.get('Status').value if root.get('Status') else 'unknown']+=1
                    for section in (root.get('sections') or root.get('Sections')).value:
                        f=compound(section);by=int(f['Y'].value)*16
                        if by>y1 or by+15<y0 or 'block_states' not in f:continue
                        bs=compound(f['block_states']);p=bs['palette'].value;desc=[state_details(v) for v in p];ind=decode_indices(bs.get('data').value if bs.get('data') else [],len(p))
                        for index,choice in enumerate(ind):
                            x=cx*16+(index&15);y=by+(index>>8);z=cz*16+((index>>4)&15)
                            if not(x0<=x<=x1 and y0<=y<=y1 and z0<=z<=z1):continue
                            key,carrier,props=desc[int(choice)]
                            if key not in pids:pids[key]=len(palette);palette.append(key);states[key]={'name':carrier,'properties':props}
                            dense[(y-y0)*sx*sz+(z-z0)*sx+(x-x0)]=pids[key]
                            if carrier not in {'minecraft:air','minecraft:cave_air','minecraft:void_air'}:blocks.append((x,y,z,key,carrier,props))
                    for be in (root.get('block_entities') or root.get('TileEntities') or type('N',(),{'value':[]})()).value:
                        b=compound(be);pos=[b[k].value for k in ['x','y','z']]
                        if all(box[i]<=pos[i]<=box[i+3] for i in range(3)):bes.append({'sourcePosition':pos,'data':typed(be),'handling':'INERT_TYPED_DATA_NOT_EXECUTED; command/function/program fields require explicit sanitization before native world placement'})
    return {'boundsInclusive':box,'dimensions':[sx,sy,sz],'palette':palette,'paletteStates':states,'denseIndices':dense.tolist(),'layout':'(y-y0)*sizeX*sizeZ+(z-z0)*sizeX+(x-x0); X fastest then Z then Y','blocks':blocks,'blockEntities':bes,'chunks':chunks,'chunkStatuses':dict(statuses)}

def selected_apps(raw,carrier,props,xyz):
    return [{**g[weighted_index([o[0].get('weight',1) for o in g],xyz,'multipart' in raw[carrier])][0],'model':normalized_model(g[weighted_index([o[0].get('weight',1) for o in g],xyz,'multipart' in raw[carrier])][0]['model'])} for _,g in branches_for(raw[carrier],props)]

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--output',type=Path,default=DEST);args=parser.parse_args();args.output.mkdir(parents=True,exist_ok=True)
    con=sqlite3.connect(f'file:{CACHE}?mode=ro',uri=True);index=json.loads((OUT/'original-map-index.json').read_text());assert index['status']=='PASS'
    meta=index['models'];metrics={(x,z):json.loads(d) for dim,x,z,d in con.execute('SELECT dimension,cx,cz,data FROM chunk_metrics') if dim=='minecraft:overworld'}
    finite={m for m,v in meta.items() if v['hasGeometry'] and not v['fullCubeContext']}
    # Candidate ranking uses actual authored finite models, not default texture
    # RNG variety of ground stone. Dense vertical construction must be present.
    eligible=[p for p,v in metrics.items() if v['finiteCells']>=80 and v['finiteYMax'] is not None and v['finiteYMax']-v['finiteYMin']>=12]
    shortlist=[]
    for span in [2,3,4]:
        rows=[]
        for cx,cz in eligible:
            modelset=set();n=0;miny=999;maxy=-999
            for dx in range(span):
                for dz in range(span):
                    row=metrics.get((cx+dx,cz+dz),{})
                    if not row:continue
                    modelset.update(m for m,c in row['modelCounts'].items() if c and m in finite);n+=row['finiteCells']
                    if row['finiteYMin'] is not None:miny=min(miny,row['finiteYMin']);maxy=max(maxy,row['finiteYMax'])
            # More unique models is first priority; excessive finite repetition
            # cannot push a plain giant wall ahead of a diverse street scene.
            rows.append({'spanChunks':span,'sizeXZ':span*16,'x0':cx*16,'z0':cz*16,'coarseFiniteModels':len(modelset),'coarseFiniteCells':n,'coarseY':[miny,maxy]})
        rows.sort(key=lambda r:(-r['coarseFiniteModels'],r['coarseFiniteCells'],r['x0'],r['z0']))
        selected=[]
        for row in rows:
            if any(abs(row['x0']-r['x0'])<span*16 and abs(row['z0']-r['z0'])<span*16 for r in selected):continue
            selected.append(row)
            if len(selected)==3:break
        shortlist.extend(selected)
    dump(args.output/'coarse-candidates.json',{'schemaVersion':1,'status':'CANDIDATE_SOURCE_SCAN_ONLY','baselineCommit':BASELINE,'mapSha256':sha(MAP),'candidatePolicy':'All occupied chunk-anchored32/48/64 windows ranked by finite original model diversity; three nonoverlapping finalists per size. Full source cells then rescanned in a street/building Y band.','candidates':shortlist})
    raw,_,_=raw_blockstates();resolver=Resolver();candidate_results=[];clips={}
    for no,row in enumerate(shortlist):
        x0,z0,size=row['x0'],row['z0'],row['sizeXZ']
        ycounts=collections.Counter(dict(con.execute('SELECT y,count(*) FROM cells WHERE dimension=? AND x BETWEEN ? AND ? AND z BETWEEN ? AND ? AND y BETWEEN 20 AND 150 GROUP BY y',('minecraft:overworld',x0,x0+size-1,z0,z0+size-1))))
        # Keep the dominant authored street floor and entire immediately rising
        # facade; excludes ground mass below the actual review surface.
        floor=min(y for y,c in ycounts.items() if c>=max(ycounts.values())*.6) if ycounts else 60
        box=[x0,floor-1,z0,x0+size-1,min(floor+47,180),z0+size-1]
        fullclip=read_box(box)
        for band in [49,25,17,13,9]:
            top=box[1]+band-1
            clip={**fullclip,'boundsInclusive':[box[0],box[1],box[2],box[3],top,box[5]],'dimensions':[size,band,size],'denseIndices':fullclip['denseIndices'][:size*size*band],'blocks':[b for b in fullclip['blocks'] if b[1]<=top],'blockEntities':[b for b in fullclip['blockEntities'] if b['sourcePosition'][1]<=top]}
            allmodels=collections.Counter();resolved=0;unresolved=0;affected=0;propscount=collections.Counter()
            for x,y,z,key,carrier,props in clip['blocks']:
                if carrier not in raw:continue
                affected+=1;apps=selected_apps(raw,carrier,props,(x,y,z));allmodels.update(a['model'] for a in apps)
                result=resolver.resolve(apps)
                if result['resolution'].startswith('EXACT'):resolved+=1;propscount[result['mainId']]+=1
                else:unresolved+=1
            result={**row,'candidateId':f'CITY-{size}-{no+1:02d}-H{band}','sourceBoundsInclusive':clip['boundsInclusive'],'sizeXYZ':clip['dimensions'],'nonAirCells':len(clip['blocks']),'affectedCarrierCells':affected,'exactSingleCellOwners':resolved,'sourceCellsRequiringWholeOrProtectedV12Resolution':unresolved,'selectedSourceModels':len(allmodels),'finiteSelectedSourceModels':len(set(allmodels)&finite),'exactCanonicalTypes':len(propscount),'sourceBlockEntities':len(clip['blockEntities']),'sourceChunks':clip['chunks'],'sourceYBandBasis':{'dominantFiniteLowerBand':floor,'finiteYHistogram':dict(sorted(ycounts.items()))},'modelCounts':dict(allmodels),'rootBudgetUpperBound':affected,'budgetWithin12000':affected<=12000,'status':'SOURCE_EVIDENCE_CANDIDATE_NOT_NATIVE_VERIFIED'}
            candidate_results.append(result);clips[result['candidateId']]=clip
        dump(args.output/'candidate-progress.json',{'status':'RUNNING','completed':len(candidate_results),'total':len(shortlist),'last':{k:v for k,v in result.items() if k not in ['modelCounts','sourceYBandBasis']}})
    valid=[r for r in candidate_results if r['budgetWithin12000'] and r['exactSingleCellOwners']>=100]
    chosen=max(valid or candidate_results,key=lambda r:(r['finiteSelectedSourceModels'],r['exactCanonicalTypes'],-r['affectedCarrierCells']))
    report={'schemaVersion':1,'status':'PASS_READONLY_CANDIDATE_SELECTION','baselineCommit':BASELINE,'mapSha256':sha(MAP),'packSha256':sha(PACK),'chosenCandidateId':chosen['candidateId'],'chosenBoundsInclusive':chosen['sourceBoundsInclusive'],'candidateSelection':'Maximum actually selected finite source-model diversity among scanned32/48/64 candidates within12000affected-cell conservative root budget. Full volume retained, no density skipping. Native appearance/location review still required.','candidates':candidate_results}
    dump(args.output/'city-candidate.json',report)
    clip=clips[chosen['candidateId']];owners=[];unresolvedrows=[];passthrough=[];applications=[];origin=chosen['sourceBoundsInclusive'][:3]
    for x,y,z,key,carrier,props in clip.pop('blocks'):
        pos=[x,y,z];relative=[pos[i]-origin[i] for i in range(3)]
        if carrier not in raw:passthrough.append({'sourcePosition':pos,'relativePosition':relative,'sourceState':key,'registryId':carrier,'properties':props});continue
        apps=selected_apps(raw,carrier,props,(x,y,z));target=resolver.resolve(apps)
        base={'sourcePosition':pos,'relativePosition':relative,'sourceState':key,'sourceCarrier':carrier,'sourceProperties':props,'sourceApplications':apps}
        if target['resolution'].startswith('EXACT'):owners.append({**base,**target,'instanceUuid':source_uuid('minecraft:overworld',pos)})
        else:unresolvedrows.append({**base,**target})
        applications.append(base)
    source={'schemaVersion':1,'status':'READONLY_SOURCE_EXPORT_WITH_EXPLICIT_UNRESOLVED_ROWS','baselineCommit':BASELINE,'candidateId':chosen['candidateId'],'dimension':'minecraft:overworld','mapSha256':sha(MAP),'packSha256':sha(PACK),'sourceOrigin':origin,'rawBlocks':clip,'owners':owners,'unresolvedSourceCells':unresolvedrows,'passThroughBlocks':passthrough,'sourceApplications':applications,'entitiesPolicy':'No live NPC/entity copy. Block entity NBT is typed inert data; source code/commands/events are not executed by this export.','normalizationPolicy':'Inverse sourcePlacementOffset in old local axes; then V12.4 inverse rotationDelta plus canonical sourceShift; actual source render yaw preserved. No gallery floor seating.'}
    out=args.output/'city-source-data.json.gz'
    with out.open('wb') as f:
        with gzip.GzipFile(fileobj=f,mode='wb',mtime=0) as g:g.write(json.dumps(source,ensure_ascii=False,separators=(',',':')).encode())
    dump(args.output/'city-source-data-summary.json',{'status':source['status'],'candidateId':chosen['candidateId'],'sourceBoundsInclusive':chosen['sourceBoundsInclusive'],'owners':len(owners),'unresolvedSourceCells':len(unresolvedrows),'passThroughBlocks':len(passthrough),'rawPaletteStates':len(clip['palette']),'rawDenseCells':len(clip['denseIndices']),'dataFile':str(out),'dataSha256':sha(out),'dataBytes':out.stat().st_size,'mapSha256After':sha(MAP),'packSha256After':sha(PACK)})

if __name__=='__main__':
    try:main()
    except BaseException:
        import traceback
        DEST.mkdir(parents=True,exist_ok=True);(DEST/'city-error.txt').write_text(traceback.format_exc(),encoding='utf-8');raise
