"""Offline first phase for a FUTURE explicit full original-map conversion.

Default is read-only validation. --execute writes a NEW derived directory:
native compact palettes first, all owner/consumed cells AIR, no live source
entities/vanilla BE callbacks before the disabled runtime author installs owners.
The immutable original ZIP is never opened for writing. Nothing is executed
from source NBT, commands, signs, item text or archive files.
"""
from __future__ import annotations
import argparse,base64,collections,copy,gzip,json,os,sqlite3,time,uuid,zipfile,zlib
from pathlib import Path,PurePosixPath
import numpy as np
from v125_map_index import ROOT,MAP,PACK,token,sha
from v127_apply_full_map_poses import write as dump
from v127_prepare_full_conversion import export_runtime
from scan_original_map_states import dimension_for
from world_io import RegionFile,Tag,NbtFile,compound,encode_nbt,decode_nbt,pack_palette_indices,invalidate_chunk_lighting,TAG_BYTE,TAG_STRING,TAG_COMPOUND,TAG_LIST,TAG_LONG_ARRAY,TAG_INT,NbtError
O=ROOT/'docs/v12.7/conversion'
def state_tag(state):
    d={'Name':Tag(TAG_STRING,state['registryId'])}
    if state['properties']:d['Properties']=Tag(TAG_COMPOUND,{k:Tag(TAG_STRING,str(v)) for k,v in state['properties'].items()})
    return Tag(TAG_COMPOUND,d)
def untyped(value):
    kind=value['type'];data=value['value']
    if kind==10:data={k:untyped(v) for k,v in data.items()}
    elif kind==9:data=[untyped(v) for v in data]
    elif kind==7:data=bytes.fromhex(data)
    return Tag(kind,data,value.get('listType'))
def entities_export(dest):
    catalogue=json.loads((ROOT/'src/rp/resources/assets/bloodborne_rp/catalog.json').read_text(encoding='utf-8-sig'));rows=[];decisions=[]
    with gzip.open(O/'original-entities-block-entities.jsonl.gz','rt',encoding='utf8') as source:
        for line in source:
            row=json.loads(line)
            if row['category']=='block_entity':decisions.append({k:v for k,v in row.items() if k!='typedNbt'}|{'decision':'REMOVE_SOURCE_VANILLA_BLOCK_ENTITY_MECHANICS','typedSourceEvidence':'original-entities-block-entities.jsonl.gz'});continue
            original=untyped(row['typedNbt']);data=compound(original);id=row['id'];local=id.partition(':')[2]
            if id.startswith('bloodborne:') and local in catalogue:
                target='bloodborne_rp:'+local;typed=copy.deepcopy(original);fields=compound(typed);fields['id']=Tag(TAG_STRING,target);fields['SourceLegacyPayload']=Tag(TAG_COMPOUND,{'Schema':Tag(TAG_INT,1),'Original':copy.deepcopy(original)})
                # Exact source UUID, Pos DOUBLE and Rotation FLOAT are retained.
                # Existing port readers interpret source state/animation fields.
                raw=encode_nbt(NbtFile('',typed));uuidtag=data.get('UUID');uid=None
                if uuidtag and uuidtag.type==11:uid=str(uuid.UUID(bytes=b''.join((int(v)&0xffffffff).to_bytes(4,'big') for v in uuidtag.value)))
                rows.append({'dimension':row['dimension'],'position':row['sourcePosition'],'instanceUuid':uid,'registryId':target,'sourceId':id,'nbtBase64':base64.b64encode(raw).decode(),'sourceTypedNbtSha256':__import__('hashlib').sha256(encode_nbt(NbtFile('',original))).hexdigest()});decisions.append({k:v for k,v in row.items() if k!='typedNbt'}|{'decision':'DEFER_EXACT_KNOWN_RP_ENTITY_AFTER_OWNER_INSTALL','targetRegistryId':target,'sourceUuidRetained':True})
            else:decisions.append({k:v for k,v in row.items() if k!='typedNbt'}|{'decision':'REMOVE_UNKNOWN_OR_SYSTEM_ENTITY'})
    rows.sort(key=lambda r:(r['dimension'],int(r['position'][0])//16,int(r['position'][2])//16,r['instanceUuid'] or ''))
    dest.mkdir(parents=True,exist_ok=True)
    with gzip.open(dest/'entities.jsonl.gz','wt',encoding='utf8') as out:
        for row in rows:out.write(token(row)+'\n')
    dump(O/'source-entity-conversion-decisions.json',{'status':'PASS_EXACT_TYPED_SOURCE_ENTITY_TRANSLATION_PLAN','counts':dict(collections.Counter(r['decision'] for r in decisions)),'rows':decisions,'originalWorldConverted':False,'sourceProgramsExecuted':False,'nativeValidation':'NOT_RUN_FULL_SOURCE_BY_USER_REQUEST'})
    return rows
def runtime_bundle(plan,runtime):
    export_runtime(plan,runtime);rows=entities_export(runtime);p=runtime/'manifest.json';m=json.loads(p.read_text(encoding='utf-8-sig'));m['runtimeFiles']={f.name:sha(f) for f in runtime.glob('*.jsonl.gz')};m['deferredEntityCount']=len(rows);m['offlinePhaseRequiredBeforeServer']=True;dump(p,m);return m
def stage(plan,runtime,target,execute):
    plan=plan.resolve();runtime=runtime.resolve();target=target.resolve();con=sqlite3.connect(f'file:{plan}?mode=ro',uri=True);summary=json.loads(con.execute("SELECT data FROM metadata WHERE key='summary'").fetchone()[0]);assert summary['status']=='PASS_COMPLETE_SOURCE_PLAN',summary['status'];assert summary['sourceHashes']=={'map':sha(MAP),'pack':sha(PACK)},'Original source hashes differ'
    if not execute:print(token({'status':'PASS_READONLY_READY_TO_STAGE','sourceHashes':summary['sourceHashes'],'target':str(target),'planSha256':sha(plan),'worldWritten':False,'executeCommand':'Add --execute only after explicit user full-conversion instruction'}));return
    assert not target.exists(),'Derived output must be fresh and absent; never overwrite any source/save'
    assert target!=ROOT and ROOT not in target.parents[:0] # named explicit fresh target only
    assert target!=MAP.parent and not MAP.is_relative_to(target),'Cannot write into or over source archive directory'
    target.parent.mkdir(parents=True,exist_ok=True);target.mkdir();operation=str(uuid.uuid4());bundle=runtime_bundle(plan,runtime)
    decisions={i:json.loads(s) for i,s in con.execute('SELECT id,data FROM decisions')};changed=chunks=0;start=time.monotonic();be_removed=[];all_members=[]
    with zipfile.ZipFile(MAP,'r') as archive:
        for member in archive.namelist():
            pp=PurePosixPath(member)
            if pp.is_absolute() or '..' in pp.parts or any(':' in p for p in pp.parts):raise ValueError('Unsafe archive member '+member)
            if member.endswith('/'):continue
            destination=target.joinpath(*pp.parts);assert destination.is_relative_to(target);destination.parent.mkdir(parents=True,exist_ok=True);raw=archive.read(member);dim=dimension_for(member,'')
            if pp.parent.name=='entities' and pp.suffix=='.mca':
                # All recognized entities are restored from typed deferred rows.
                if len(raw)>=8192:
                    region=RegionFile(raw)
                    for stored in region.chunks():
                        nbt=stored.nbt();root=compound(nbt.root)
                        for key in ('Entities','entities'):
                            if key in root:root[key]=Tag(TAG_LIST,[],TAG_COMPOUND)
                        region.set_chunk(stored.x,stored.z,nbt,timestamp=stored.timestamp)
                    destination.write_bytes(region.to_bytes())
                else:all_members.append({'member':member,'decision':'REMOVE_SOURCE_ZERO_BYTE_ENTITY_REGION'})
                continue
            if pp.parent.name=='region' and pp.suffix=='.mca':
                region=RegionFile(raw)
                for stored in region.chunks():
                    nbt=stored.nbt();root=compound(nbt.root);root=compound(root['Level']) if 'Level' in root else root;cx,cz=int(root['xPos'].value),int(root['zPos'].value);chunk_changed=False
                    for section in (root.get('sections') or root.get('Sections')).value:
                        f=compound(section);sy=int(f['Y'].value);row=con.execute('SELECT source_palette,source_indices,decision_indices FROM sections WHERE dimension=? AND cx=? AND cz=? AND sy=?',(dim,cx,cz,sy)).fetchone()
                        if row is None:continue
                        sp,si,di=row;old=np.frombuffer(zlib.decompress(si),dtype='<u2');action=np.frombuffer(zlib.decompress(di),dtype='<u2')
                        if np.all(action==0):continue
                        sourcepalette=json.loads(sp);pids={};palette=[];ind=np.zeros(4096,dtype=np.uint16)
                        def pid(state):
                            key=token(state)
                            if key not in pids:pids[key]=len(palette);palette.append(state_tag(state))
                            return pids[key]
                        for did in np.unique(action):
                            d=decisions[int(did)];at=np.flatnonzero(action==did)
                            if d['action']=='KEEP':
                                for pi in np.unique(old[at]):
                                    _,reg,props=sourcepalette[int(pi)];ind[at[old[at]==pi]]=pid({'registryId':reg,'properties':props})
                            else:
                                assert d['action'] in {'OWNER','COMPACT','CONSUMED','REMOVE'},d
                                state={'registryId':d['registryId'],'properties':d['properties']} if d['action']=='COMPACT' else {'registryId':'minecraft:air','properties':{}}
                                ind[at]=pid(state)
                        bs={'palette':Tag(TAG_LIST,palette,TAG_COMPOUND)}
                        if len(palette)>1:bs['data']=Tag(TAG_LONG_ARRAY,pack_palette_indices(ind.tolist(),len(palette)))
                        f['block_states']=Tag(TAG_COMPOUND,bs);chunk_changed=True;changed+=1
                    for key in ('block_entities','TileEntities'):
                        if key in root:
                            for be in root[key].value:
                                fields=compound(be);be_removed.append({'dimension':dim,'position':[fields.get(a,Tag(TAG_INT,0)).value for a in ['x','y','z']],'id':fields.get('id',Tag(TAG_STRING,'missing')).value,'reason':'SOURCE_VANILLA_BLOCK_ENTITY_DATA_REMOVED_BEFORE_MOD_OWNER_INSTALL'})
                            root[key]=Tag(TAG_LIST,[],TAG_COMPOUND);chunk_changed=True
                    for key in ('Entities','entities'):
                        if key in root:root[key]=Tag(TAG_LIST,[],TAG_COMPOUND);chunk_changed=True
                    if chunk_changed:
                        invalidate_chunk_lighting(root);region.set_chunk(stored.x,stored.z,nbt,timestamp=stored.timestamp)
                    chunks+=1
                destination.write_bytes(region.to_bytes());print(token({'member':member,'chunks':chunks,'changedSections':changed}),flush=True)
            elif pp.name in {'session.lock','uid.dat'}:all_members.append({'member':member,'decision':'REMOVE_SOURCE_SESSION_ID'})
            else:destination.write_bytes(raw)
    marker={'schemaVersion':1,'status':'PASS_DERIVED_COPY_PREPARED','operationId':operation,'sourceHashes':summary['sourceHashes'],'targetRealPath':str(target),'sourcePlanSha256':sha(plan),'runtimeManifestSha256':sha(runtime/'manifest.json'),'runtimeDirectory':str(runtime),'changedSections':changed,'sourceChunks':chunks,'sourceBlockEntitiesRemoved':be_removed,'sourceMemberActions':all_members,'fullOwnerAuthoringComplete':False,'nextPhase':'Disabled FullMapConversionAuthor runs only against this explicitly staged copy','originalArchivesModified':False,'elapsedSeconds':round(time.monotonic()-start,2)};dump(target/'dw-derived-copy.json',marker);print(token(marker));con.close()
if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--plan',type=Path,default=ROOT/'build/v127-full-conversion-first.sqlite');p.add_argument('--runtime',type=Path,default=ROOT/'build/v127-runtime-conversion');p.add_argument('--output',type=Path,default=ROOT/'build/v127-derived-original');p.add_argument('--execute',action='store_true');p.add_argument('--export-runtime-only',action='store_true');a=p.parse_args()
    if a.export_runtime_only:runtime_bundle(a.plan.resolve(),a.runtime.resolve())
    else:stage(a.plan,a.runtime,a.output,a.execute)
