"""Read-only proof of invisible retired source unit cubes with six opaque sides.

Only the actual source geometry and used RGBA establish opaque backing. Native
carrier opacity or a registry/model name is never evidence. Unknown KEEP cells
are conservatively unproven. At most nine neighboring chunks are held in RAM.
Writes a derived replacement bitset SQLite/coordinate report; never the plan.
"""
import collections,functools,gzip,hashlib,itertools,json,sqlite3,time,zlib
from pathlib import Path
import numpy as np
from v127_visual_export import ROOT,write_json
from v125_map_door_projections import SourceVisuals
from v125_map_verify_extra_pose_uv import source_quads

PLAN=ROOT/'build/v127-full-conversion-first.sqlite'
DEST=ROOT/'docs/v12.7/conversion'
OUT=ROOT/'build/v127-enclosed-fillers.sqlite'
def token(v):return json.dumps(v,sort_keys=True,separators=(',',':'))
def opaque_backing(visuals,apps):
    """Conservative exact full face proof; all frames must be opaque."""
    quads=source_quads(visuals,apps);found=set();evidence=[]
    points=np.asarray([q[0] for q in quads]).reshape(-1,3) if quads else np.empty((0,3))
    bounds={'allVerticesInsideUnitCell':bool(len(points) and np.all(points>=-1e-6) and np.all(points<=16+1e-6)),
        'minimumModelUnits':points.min(0).tolist() if len(points) else None,'maximumModelUnits':points.max(0).tolist() if len(points) else None,'sourceQuadCount':len(quads)}
    for vertices,uv,texture,tint in quads:
        vertices=np.round(vertices,6);lo,hi=vertices.min(0),vertices.max(0);axes=[i for i in range(3) if hi[i]-lo[i]>1e-6]
        if len(axes)!=2:continue
        fixed=next(i for i in range(3) if i not in axes);height=float(lo[fixed])
        if height not in (0.,16.) or any(lo[i]!=0 or hi[i]!=16 for i in axes):continue
        expected=set(itertools.product(*[(0.,16.) if i in axes else (height,) for i in range(3)]))
        if set(map(tuple,vertices))!=expected:continue
        image=visuals.image(texture)
        # Requiring every PNG pixel alpha255 is stricter than its used UVs and
        # remains safe for atlases and animated frame strips without guessing.
        if not np.all(image[...,3]==255):continue
        if not np.all(np.isfinite(uv)) or uv.min()<-1e-6 or uv.max()>16+1e-6:continue
        found.add((fixed,int(height)));evidence.append({'axis':fixed,'side':int(height),'texture':texture,'rgbaSha256':hashlib.sha256(image.tobytes()).hexdigest(),'shape':list(image.shape),'uv':uv.tolist()})
    return len(found)==6,evidence,bounds
def runs(indices):
    edges=np.flatnonzero(np.diff(indices)>1)+1
    return [[int(p[0]),len(p)] for p in np.split(indices,edges)] if len(indices) else []
def main():
    started=time.monotonic();source=SourceVisuals()
    old=json.loads((DEST/'retired-source-vanilla-equivalence.json').read_text(encoding='utf-8'))
    retired={token([r['sourceState'],r['sourceApplications']]):r for r in old['sourceApplicationCases']}
    db=sqlite3.connect(f'file:{PLAN.resolve()}?mode=ro',uri=True);db.execute('BEGIN');db.execute('PRAGMA cache_size=-2048')
    decisions={int(i):json.loads(data) for i,data in db.execute('SELECT id,data FROM decisions')}
    summary=json.loads(db.execute("SELECT data FROM metadata WHERE key='summary'").fetchone()[0])
    dictionary_sha=hashlib.sha256(token(decisions).encode()).hexdigest();max_id=max(decisions);opaque=np.zeros(max_id+1,dtype=np.bool_);candidate=np.zeros_like(opaque);proofs=[];errors=[]
    for did,d in decisions.items():
        apps=d.get('sourceApplications');state=d.get('sourceState')
        if not apps or not state:continue
        retired_key=token([state,apps]);is_retired=retired_key in retired
        try:
            full,evidence,bounds=opaque_backing(source,apps);opaque[did]=full;candidate[did]=is_retired and full and bounds['allVerticesInsideUnitCell']
            if full:proofs.append({'decisionId':did,'sourceState':state,'sourceApplications':apps,'retiredCandidate':bool(candidate[did]),'candidateEntireGeometryContainedInUnitCell':bounds['allVerticesInsideUnitCell'],'allSourceVertexBounds':bounds,'fullUnitCubeOpaqueFaces':evidence})
        except Exception as e:
            if is_retired:errors.append({'decisionId':did,'sourceState':state,'error':repr(e)})
    # Every side is supported by exact original source surfaces. No rendered
    # target model, future converter or source native BlockState is consulted.
    @functools.lru_cache(maxsize=9)
    def chunk(dim,cx,cz):
        result={}
        for sy,blob in db.execute('SELECT sy,decision_indices FROM sections WHERE dimension=? AND cx=? AND cz=?',(dim,cx,cz)):
            ids=np.frombuffer(zlib.decompress(blob),dtype='<u2').reshape(16,16,16)
            result[int(sy)]=opaque[ids]
        return result
    missing=np.zeros((16,16,16),dtype=np.bool_)
    out=sqlite3.connect(OUT);out.executescript('DROP TABLE IF EXISTS sections;DROP TABLE IF EXISTS metadata;CREATE TABLE sections(dimension TEXT,cx INTEGER,cz INTEGER,sy INTEGER,replacement_bits BLOB,replacements INTEGER,PRIMARY KEY(dimension,cx,cz,sy));CREATE TABLE metadata(key TEXT PRIMARY KEY,data TEXT);')
    out.execute('PRAGMA journal_mode=OFF');out.execute('PRAGMA synchronous=OFF')
    coordinate=DEST/'enclosed-vanilla-filler-coordinate-runs.jsonl.gz';temp=coordinate.with_name(coordinate.name+'.v127-write.tmp')
    candidate_cells=0;enclosed_cells=0;sections=0;written_sections=0;retained_by_state=collections.Counter();replaced_by_state=collections.Counter();examples=[];max_resident=0
    query=db.execute('SELECT dimension,cx,cz,sy,decision_indices FROM sections ORDER BY dimension,cx,cz,sy')
    with gzip.open(temp,'wt',encoding='utf-8',compresslevel=3) as coords:
        for dim,cx,cz,sy,blob in query:
            sections+=1;ids=np.frombuffer(zlib.decompress(blob),dtype='<u2').reshape(16,16,16);wanted=candidate[ids]
            if not np.any(wanted):continue
            candidate_cells+=int(wanted.sum());own=chunk(dim,cx,cz);back=own.get(sy,missing);valid=wanted.copy()
            valid[:,1:,:]&=back[:,:-1,:];valid[:,0,:]&=chunk(dim,cx,cz-1).get(sy,missing)[:,-1,:]
            valid[:,:-1,:]&=back[:,1:,:];valid[:,-1,:]&=chunk(dim,cx,cz+1).get(sy,missing)[:,0,:]
            valid[:,:,1:]&=back[:,:,:-1];valid[:,:,0]&=chunk(dim,cx-1,cz).get(sy,missing)[:,:,-1]
            valid[:,:,:-1]&=back[:,:,1:];valid[:,:,-1]&=chunk(dim,cx+1,cz).get(sy,missing)[:,:,0]
            valid[1:,:,:]&=back[:-1,:,:];valid[0,:,:]&=own.get(sy-1,missing)[-1,:,:]
            valid[:-1,:,:]&=back[1:,:,:];valid[-1,:,:]&=own.get(sy+1,missing)[0,:,:]
            max_resident=max(max_resident,chunk.cache_info().currsize)
            for did in np.unique(ids[wanted]):
                retired_count=int(np.count_nonzero(wanted&(ids==did)));replaced_count=int(np.count_nonzero(valid&(ids==did)));state=decisions[int(did)]['sourceState'];retained_by_state[state]+=retired_count-replaced_count;replaced_by_state[state]+=replaced_count
            if not valid.any():continue
            flat=np.flatnonzero(valid.reshape(-1));count=len(flat);enclosed_cells+=count;written_sections+=1
            out.execute('INSERT INTO sections VALUES(?,?,?,?,?,?)',(dim,cx,cz,sy,zlib.compress(np.packbits(valid.reshape(-1),bitorder='little').tobytes(),6),count))
            # The existing source palette state is retained as the suitable
            # vanilla filler, preserving its native light opacity and material.
            for did in np.unique(ids[valid]):
                at=np.flatnonzero((valid&(ids==did)).reshape(-1));state=decisions[int(did)]['sourceState']
                coords.write(token({'dimension':dim,'chunk':[cx,cz],'sectionY':sy,'sourceState':state,'vanillaReplacementState':state,'decisionId':int(did),'count':len(at),'linearRuns':runs(at),'proof':'SIX_ADJACENT_SOURCE_FULL_UNIT_OPAQUE_FACE_BACKINGS'})+'\n')
                if len(examples)<20:
                    i=int(at[0]);examples.append({'sourceState':state,'position':[cx*16+(i&15),sy*16+(i>>8),cz*16+((i>>4)&15)],'dimension':dim})
            if written_sections%20000==0:print(token({'sectionsScanned':sections,'replacementSections':written_sections,'enclosedCells':enclosed_cells,'seconds':round(time.monotonic()-started,1)}),flush=True)
    temp.replace(coordinate);db.close();assert max_resident<=9
    proof={'schemaVersion':1,'status':'PASS_READ_ONLY_OPAQUE_SOURCE_ENCLOSED_FILLER_PROOF','sourceHashes':summary['sourceHashes'],'inputDecisionDictionarySha256':dictionary_sha,'inputPlanCounts':summary['counts'],
        'allSectionsScanned':sections,'candidateSourceExactUnitCubeCells':candidate_cells,'provenEnclosedVanillaFillerCells':enclosed_cells,'visibleOrUnprovenCandidateCellsKeptExact':candidate_cells-enclosed_cells,
        'retiredSourceCellsTotal':old['retiredOccurrenceCount'],'nonUnitCubeRetiredCellsKeptExact':old['retiredOccurrenceCount']-candidate_cells,'replacementSections':written_sections,
        'vanillaReplacementStates':dict(replaced_by_state),'retainedExactStates':dict(retained_by_state),'coordinateRunFile':str(coordinate.relative_to(ROOT)),
        'derivedReplacementMaskDatabase':str(OUT.relative_to(ROOT)),'maskFormat':'zlib-compressed4096-bit little-endian numpy packbits; index=y*256+z*16+x; setbits retain original vanilla source palette state. Derived report only.',
        'opaqueSourceDecisionProofs':proofs,'sourceDependencyErrors':errors,'examples':examples,'maxResidentChunkMasks':max_resident,'configuredChunkMaskCacheLimit':9,'worldOrPlanModified':False,
        'policy':'Candidate ENTIRE source geometry must be contained in0..16, with exact complete opaque rendered face backing onall6sides. All6immediate source neighbors require fullunitopaquebacking; their extra protruding geometry is allowed because their unit faces still cover the candidate. Every referenced PNGpixel/allanimatedframes alpha255. Actual weightedsource applicationcases are assessed separately. Missing/unknownKEEP data conservatively fails. No native carrier-opacity/name shortcut.',
        'elapsedSeconds':round(time.monotonic()-started,2)}
    for k,v in proof.items():out.execute('INSERT INTO metadata VALUES(?,?)',(k,token(v)))
    out.commit();out.close();write_json(DEST/'enclosed-vanilla-filler-proof.json',proof)
    print(json.dumps({k:v for k,v in proof.items() if k not in ('opaqueSourceDecisionProofs','examples')},ensure_ascii=False,indent=2),flush=True)
if __name__=='__main__':main()
