"""Offline first phase for a FUTURE explicit full original-map conversion.

Default is read-only validation. --execute writes a NEW derived directory:
native compact palettes first, all owner/consumed cells AIR, no live source
entities/vanilla BE callbacks before the disabled runtime author installs owners.
The immutable original ZIP is never opened for writing. Nothing is executed
from source NBT, commands, signs, item text or archive files.
"""
from __future__ import annotations
import argparse,base64,collections,copy,gzip,json,os,sqlite3,time,uuid,zipfile,zlib,stat,math
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
                rows.append({'dimension':row['dimension'],'position':[math.floor(v) for v in row['sourcePosition']],'exactSourcePosition':row['sourcePosition'],'instanceUuid':uid,'registryId':target,'sourceId':id,'nbtBase64':base64.b64encode(raw).decode(),'sourceTypedNbtSha256':__import__('hashlib').sha256(encode_nbt(NbtFile('',original))).hexdigest()});decisions.append({k:v for k,v in row.items() if k!='typedNbt'}|{'decision':'DEFER_EXACT_KNOWN_RP_ENTITY_AFTER_OWNER_INSTALL','targetRegistryId':target,'sourceUuidRetained':True})
            else:decisions.append({k:v for k,v in row.items() if k!='typedNbt'}|{'decision':'REMOVE_UNKNOWN_OR_SYSTEM_ENTITY'})
    rows.sort(key=lambda r:(r['dimension'],int(r['position'][0])//16,int(r['position'][2])//16,r['instanceUuid'] or ''))
    dest.mkdir(parents=True,exist_ok=True)
    with gzip.open(dest/'entities.jsonl.gz','wt',encoding='utf8') as out:
        for row in rows:out.write(token(row)+'\n')
    dump(O/'source-entity-conversion-decisions.json',{'status':'PASS_EXACT_TYPED_SOURCE_ENTITY_TRANSLATION_PLAN','counts':dict(collections.Counter(r['decision'] for r in decisions)),'rows':decisions,'originalWorldConverted':False,'sourceProgramsExecuted':False,'nativeValidation':'NOT_RUN_FULL_SOURCE_BY_USER_REQUEST'})
    return rows
def runtime_bundle(plan,runtime):
    # Never leave partially rewritten streams looking like an accepted plan.
    with sqlite3.connect(f'file:{plan.resolve()}?mode=ro',uri=True) as con:
        summary=json.loads(con.execute("SELECT data FROM metadata WHERE key='summary'").fetchone()[0]);assert summary.get('sourceStylePolicy')!='PENDING_USER_DECISION','Runtime export is blocked while known visible source-style preservation is awaiting a user decision'
    ordinary_ancestors(runtime);runtime=runtime.absolute();assert not runtime.exists(),'Runtime export must use a fresh absent directory'
    temp=runtime.with_name(runtime.name+'.export-'+uuid.uuid4().hex);export_runtime(plan,temp);rows=entities_export(temp);p=temp/'manifest.json';m=json.loads(p.read_text(encoding='utf-8-sig'));m['runtimeFiles']={f.name:sha(f) for f in temp.glob('*.jsonl.gz')};m['deferredEntityCount']=len(rows);m['offlinePhaseRequiredBeforeServer']=True;dump(p,m);os.replace(temp,runtime);return m
def ordinary_ancestors(path):
    """Reject links/reparse points before resolve can hide the caller's path."""
    path=path.absolute()
    for part in [path,*path.parents]:
        try:info=part.lstat()
        except FileNotFoundError:continue
        if stat.S_ISLNK(info.st_mode) or getattr(info,'st_file_attributes',0)&getattr(stat,'FILE_ATTRIBUTE_REPARSE_POINT',0x400):raise ValueError('Refusing reparse/symlink path: '+str(part))
def fresh_target(path):
    ordinary_ancestors(path);path=path.absolute()
    assert path.drive.upper()!='E:','Paused E migration/copies cannot be used'
    assert not path.exists(),'Derived output must be fresh and absent; never overwrite any source/save'
    assert path!=ROOT and not ROOT.is_relative_to(path),'Cannot replace workspace or an ancestor'
    assert not path.is_relative_to(MAP.parent) and not MAP.parent.is_relative_to(path),'Cannot use immutable source directory or its ancestor'
    return path.resolve()
def source_changed(con,decisions,dim,x,y,z):
    row=con.execute('SELECT decision_indices FROM sections WHERE dimension=? AND cx=? AND cz=? AND sy=?',(dim,x>>4,z>>4,y>>4)).fetchone()
    if row is None:return False
    values=np.frombuffer(zlib.decompress(row[0]),dtype='<u2');action=decisions[int(values[((y&15)<<8)|((z&15)<<4)|(x&15)])]['action']
    return action!='KEEP'
def stage(plan,runtime,target,execute):
    ordinary_ancestors(plan);ordinary_ancestors(runtime);target=fresh_target(target);plan=plan.resolve();runtime=runtime.resolve();con=sqlite3.connect(f'file:{plan}?mode=ro',uri=True);summary=json.loads(con.execute("SELECT data FROM metadata WHERE key='summary'").fetchone()[0]);assert summary['status']=='PASS_COMPLETE_SOURCE_PLAN',summary['status'];assert summary['sourceHashes']=={'map':sha(MAP),'pack':sha(PACK)},'Original source hashes differ'
    if not execute:print(token({'status':'READONLY_SOURCE_STYLE_POLICY_PENDING' if summary.get('sourceStylePolicy')=='PENDING_USER_DECISION' else 'PASS_READONLY_READY_TO_STAGE','sourceHashes':summary['sourceHashes'],'sourceStylePolicy':summary.get('sourceStylePolicy'),'target':str(target),'planSha256':sha(plan),'worldWritten':False,'executeCommand':'Add --execute only after explicit user full-conversion instruction and final source-style decision'}));return
    assert summary.get('sourceStylePolicy')!='PENDING_USER_DECISION','Full conversion is blocked while the known visible source-style preservation policy awaits a user decision'
    if runtime.exists():
        bundle=json.loads((runtime/'manifest.json').read_text(encoding='utf-8-sig'));assert bundle['status']=='PASS_COMPLETE_SOURCE_PLAN' and bundle['sourcePlanSha256']==sha(plan),'Stale runtime bundle'
        for name,digest in bundle['runtimeFiles'].items():assert PurePosixPath(name).name==name and sha(runtime/name)==digest,'Runtime stream hash mismatch'
    else:bundle=runtime_bundle(plan,runtime)
    target.parent.mkdir(parents=True,exist_ok=True);target.mkdir();operation=str(uuid.uuid4())
    decisions={i:json.loads(s) for i,s in con.execute('SELECT id,data FROM decisions')};changed=chunks=0;start=time.monotonic();be_removed=[];all_members=[];ticks_removed=[]
    with zipfile.ZipFile(MAP,'r') as archive:
        levels=[PurePosixPath(n) for n in archive.namelist() if PurePosixPath(n).name=='level.dat'];assert len(levels)==1,'Ambiguous original world root';prefix=levels[0].parent
        for member in archive.namelist():
            pp=PurePosixPath(member)
            if pp.is_absolute() or '..' in pp.parts or any(':' in p for p in pp.parts):raise ValueError('Unsafe archive member '+member)
            if member.endswith('/'):continue
            try:relative=pp.relative_to(prefix)
            except ValueError:all_members.append({'member':member,'decision':'OMIT_ARCHIVE_WRAPPER_OUTSIDE_WORLD_ROOT'});continue
            if any(p.lower() in {'datapacks','resourcepacks'} for p in relative.parts) or pp.name.lower() in {'resources.zip','resource.zip'}:
                all_members.append({'member':member,'decision':'REMOVE_SOURCE_EXECUTABLE_DATAPACK_OR_AUTO_RESOURCE_PACK','sha256':__import__('hashlib').sha256(archive.read(member)).hexdigest()});continue
            destination=target.joinpath(*relative.parts);assert destination.is_relative_to(target);destination.parent.mkdir(parents=True,exist_ok=True);raw=archive.read(member);dim=dimension_for(member,'')
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
                    for key in ('block_ticks','fluid_ticks','TileTicks','LiquidTicks'):
                        if key not in root:continue
                        kept=[]
                        for tick in root[key].value:
                            fields=compound(tick);position=[int(fields[a].value) for a in ('x','y','z')]
                            if source_changed(con,decisions,dim,*position):ticks_removed.append({'dimension':dim,'position':position,'kind':key,'reason':'OBSOLETE_SOURCE_CARRIER_TICK_REMOVED','typedSource':fields.get('i',fields.get('id',Tag(TAG_STRING,''))).value});chunk_changed=True
                            else:kept.append(tick)
                        root[key]=Tag(TAG_LIST,kept,TAG_COMPOUND)
                    if chunk_changed:
                        invalidate_chunk_lighting(root);region.set_chunk(stored.x,stored.z,nbt,timestamp=stored.timestamp)
                    chunks+=1
                destination.write_bytes(region.to_bytes());print(token({'member':member,'chunks':chunks,'changedSections':changed}),flush=True)
            elif pp.name in {'level.dat','level.dat_old'}:
                nbt=decode_nbt(gzip.decompress(raw));fields=compound(nbt.root);data=compound(fields['Data']);previous=copy.deepcopy(data.get('DataPacks'));data['DataPacks']=Tag(TAG_COMPOUND,{'Enabled':Tag(TAG_LIST,[Tag(TAG_STRING,'vanilla'),Tag(TAG_STRING,'fabric')],TAG_STRING),'Disabled':Tag(TAG_LIST,[],TAG_STRING)});destination.write_bytes(gzip.compress(encode_nbt(nbt),mtime=0));all_members.append({'member':member,'decision':'DISABLE_SOURCE_DATAPACKS_BEFORE_DERIVED_LOAD','sourcePackNames':[] if previous is None else [t.value for t in compound(previous).get('Enabled',Tag(TAG_LIST,[],TAG_STRING)).value]})
            elif pp.name in {'session.lock','uid.dat'}:all_members.append({'member':member,'decision':'REMOVE_SOURCE_SESSION_ID'})
            else:destination.write_bytes(raw)
    marker={'schemaVersion':1,'status':'PASS_DERIVED_COPY_PREPARED','operationId':operation,'sourceHashes':summary['sourceHashes'],'targetRealPath':str(target),'sourcePlanSha256':sha(plan),'runtimeManifestSha256':sha(runtime/'manifest.json'),'runtimeDirectory':str(runtime),'changedSections':changed,'sourceChunks':chunks,'sourceBlockEntitiesRemoved':be_removed,'sourceMemberActions':all_members,'obsoleteCarrierTicksRemoved':ticks_removed,'fullOwnerAuthoringComplete':False,'nextPhase':'Disabled FullMapConversionAuthor runs only against this explicitly staged copy','originalArchivesModified':False,'elapsedSeconds':round(time.monotonic()-start,2)};dump(target/'dw-derived-copy.json',marker);print(token(marker));con.close()
if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--plan',type=Path,default=ROOT/'build/v127-full-conversion-first.sqlite');p.add_argument('--runtime',type=Path,default=ROOT/'build/v127-runtime-conversion');p.add_argument('--output',type=Path,default=ROOT/'build/v127-derived-original');p.add_argument('--execute',action='store_true');p.add_argument('--export-runtime-only',action='store_true');a=p.parse_args()
    if a.export_runtime_only:runtime_bundle(a.plan.resolve(),a.runtime.resolve())
    else:stage(a.plan,a.runtime,a.output,a.execute)
