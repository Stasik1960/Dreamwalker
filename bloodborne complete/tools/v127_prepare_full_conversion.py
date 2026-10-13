"""Prepare every source cell for a later explicit full conversion, never write a world.

The derived SQLite plan stores compressed per-section decisions rather than a
58-million-object Python list. Exact Minecraft weighted choices use ORIGINAL XYZ.
All source ZIPs remain opened read-only. Runtime output is a bounded JSONL stream.
"""
from __future__ import annotations
import argparse,collections,copy,gzip,hashlib,json,sqlite3,time,zipfile,zlib,uuid,os
from pathlib import Path,PurePosixPath
import numpy as np
from v125_map_index import ROOT,MAP,PACK,CACHE,token,app_clean,sha
from v127_apply_full_map_poses import write as dump
from v125_map_resolve import source_uuid,rotation
from v127_full_map_resolver import CurrentResolver
from scan_original_map_models import branches_for,raw_blockstates,normalized_model,weighted_indices
from scan_original_map_states import decode_indices,state_details,dimension_for
from world_io import RegionFile,compound,NbtError
O=ROOT/'docs/v12.7/conversion';RES=ROOT/'src/architecture/resources'
PLAN=ROOT/'build/v127-full-conversion.sqlite'

def whole_pass(r,source,out):
    apps={i:json.loads(s) for i,s in source.execute('SELECT id,apps FROM applications')};consumed={};whole=[];attempts=0
    recipes=list(r.current_whole)
    # Match the newly exact whole original tree before legacy partial mappings.
    recipes.sort(key=lambda c:(c['registryId']!='bloodborne_dw:source_original_tree',-len(c['applications']),c['registryId'],c['variant']))
    for candidate in recipes:
        groups=collections.defaultdict(list)
        for a in candidate['applications']:groups[tuple(a.get('offset',[0,0,0]))].append({k:v for k,v in a.items() if k!='offset'})
        first=groups.get((0,0,0));
        if not first:continue
        accepted={i:r.match(first,a) for i,a in apps.items() if r.match(first,a) is not None}
        for aid,yaw in accepted.items():
            for dim,x,y,z in source.execute('SELECT dimension,x,y,z FROM cells WHERE applications=? ORDER BY dimension,x,y,z',(aid,)):
                anchor=(dim,x,y,z)
                if anchor in consumed:continue
                attempts+=1;cells=[];okay=True
                for offset,expected in groups.items():
                    delta=rotation([v/16 for v in offset],yaw)
                    if any(abs(v-round(v))>1e-7 for v in delta):okay=False;break
                    p=(dim,x+round(delta[0]),y+round(delta[1]),z+round(delta[2]))
                    row=source.execute('SELECT applications FROM cells WHERE dimension=? AND x=? AND y=? AND z=?',p).fetchone()
                    if row is None or p in consumed or r.match(expected,apps[row[0]])!=yaw:okay=False;break
                    cells.append(p)
                if not okay:continue
                target=r.target(candidate,yaw);target['action']='OWNER'
                if target['registryId'] in {'bloodborne_dw:source_original_tree','bloodborne_dw:source_large_grille_gate'}:
                    target.setdefault('payload',{})['SourceCarrierContextVersion']=1
                uuid=source_uuid(dim,[x,y,z],'whole:'+target['registryId']);index=len(whole)
                row={'dimension':dim,'position':[x,y,z],'decision':target,'instanceUuid':uuid,'consumedSourceCells':[list(p[1:]) for p in cells],'sourceApplications':candidate['applications']}
                whole.append(row)
                for p in cells:consumed[p]=index
                out.execute('INSERT INTO whole_objects VALUES(?,?,?,?,?,?)',(index,dim,x,y,z,token(row)))
    out.executemany('INSERT INTO consumed VALUES(?,?,?,?,?)',[(d,x,y,z,i) for (d,x,y,z),i in consumed.items()]);out.commit()
    return consumed,whole,attempts

def prepare(path):
    path=path.resolve()
    resume=path.exists()
    O.mkdir(parents=True,exist_ok=True);r=CurrentResolver();source=sqlite3.connect(f'file:{CACHE}?mode=ro',uri=True);out=sqlite3.connect(path)
    if not resume:out.executescript('PRAGMA journal_mode=OFF;PRAGMA synchronous=OFF;CREATE TABLE decisions(id INTEGER PRIMARY KEY,data TEXT UNIQUE);CREATE TABLE sections(dimension TEXT,cx INT,cz INT,sy INT,source_palette TEXT,source_indices BLOB,decision_indices BLOB,PRIMARY KEY(dimension,cx,cz,sy));CREATE TABLE chunks(dimension TEXT,cx INT,cz INT,member TEXT,source_nbt_sha256 TEXT,PRIMARY KEY(dimension,cx,cz));CREATE TABLE whole_objects(id INT PRIMARY KEY,dimension TEXT,x INT,y INT,z INT,data TEXT);CREATE TABLE consumed(dimension TEXT,x INT,y INT,z INT,whole_id INT,PRIMARY KEY(dimension,x,y,z));CREATE TABLE metadata(key TEXT PRIMARY KEY,data TEXT);')
    if resume:
        consumed={(d,x,y,z):i for d,x,y,z,i in out.execute('SELECT * FROM consumed')};whole=[json.loads(d) for (d,) in out.execute('SELECT data FROM whole_objects ORDER BY id')];attempts=0
    else:consumed,whole,attempts=whole_pass(r,source,out)
    print(token({'wholeObjects':len(whole),'consumed':len(consumed),'wholeAttempts':attempts,'resume':resume}),flush=True)
    compactdoc=json.loads((RES/'bloodborne_dw/imported-compact-v127.json').read_text(encoding='utf-8-sig'));compact={t['registryId']:set(t['poses']) for t in compactdoc['types']}
    raw,_,_=raw_blockstates();state_groups={};classification={};decisions={};decision_values=[];actions=collections.Counter();reasons=collections.Counter();types=collections.Counter();sourcecounts=collections.Counter();chunks=sections=0;start=time.monotonic();unresolved=[];samples=collections.defaultdict(list)
    consumed_chunks=collections.defaultdict(lambda:collections.defaultdict(dict))
    for (dim,x,y,z),wid in consumed.items():consumed_chunks[(dim,x>>4,z>>4)][y>>4][((y&15)<<8)|((z&15)<<4)|(x&15)]=wid
    def decision_id(d):
        key=token(d)
        if key not in decisions:decisions[key]=len(decisions);decision_values.append(d);out.execute('INSERT INTO decisions VALUES(?,?)',(decisions[key],key))
        return decisions[key]
    if resume:
        for i,rawdecision in out.execute('SELECT id,data FROM decisions ORDER BY id'):decisions[rawdecision]=i;decision_values.append(json.loads(rawdecision))
    existing_chunks={(d,x,z) for d,x,z in out.execute('SELECT dimension,cx,cz FROM chunks')} if resume else set()
    default=decision_id({'action':'KEEP','reason':'SOURCE_UNAFFECTED_VANILLA_STATE'})
    removal_temp=ROOT/'build'/('v127-removal-'+uuid.uuid4().hex+'.jsonl.gz');removals=gzip.open(removal_temp,'wt',encoding='utf8')
    with zipfile.ZipFile(MAP) as archive:
        members=sorted(n for n in archive.namelist() if n.endswith('.mca') and PurePosixPath(n).parent.name=='region')
        for rn,member in enumerate(members,1):
            dim=dimension_for(member,'');region=RegionFile(archive.read(member))
            for stored in region.chunks():
                nbt=stored.nbt();root=compound(nbt.root);root=compound(root['Level']) if 'Level' in root else root;cx,cz=int(root['xPos'].value),int(root['zPos'].value);chunks+=1
                if (dim,cx,cz) in existing_chunks:continue
                # NBT canonical stream isn't needed: the source stored compressed
                # chunk record is hashed and the ZIP hash protects all sectors.
                out.execute('INSERT INTO chunks VALUES(?,?,?,?,?)',(dim,cx,cz,member,hashlib.sha256(stored.compressed_payload).hexdigest()))
                for section in (root.get('sections') or root.get('Sections')).value:
                    f=compound(section);bs=f.get('block_states')
                    if bs is None:continue
                    data=compound(bs);palette=data['palette'].value;desc=[state_details(v) for v in palette];ind=decode_indices(data.get('data').value if data.get('data') else [],len(palette));sy=int(f['Y'].value);sections+=1;dest=np.full(4096,default,dtype=np.uint16)
                    for pi,(state,carrier,props) in enumerate(desc):
                        at=np.flatnonzero(ind==pi)
                        if not len(at):continue
                        sourcecounts[state]+=len(at)
                        if not carrier.startswith('minecraft:'):
                            d={'action':'REMOVE','reason':'UNKNOWN_SOURCE_REGISTRY_OR_SYSTEM_SERVICE','sourceState':state,'sourceRegistryId':carrier,'sourceApplications':[]};di=decision_id(d);dest[at]=di;actions['REMOVE']+=len(at);reasons[d['reason']]+=len(at)
                            for i in at:removals.write(token({'dimension':dim,'position':[cx*16+(int(i)&15),sy*16+(int(i)>>8),cz*16+((int(i)>>4)&15)],'sourceState':state,'reason':d['reason']})+'\n')
                            continue
                        if carrier not in raw:
                            if carrier in {'minecraft:barrier','minecraft:structure_void','minecraft:light','minecraft:command_block','minecraft:chain_command_block','minecraft:repeating_command_block','minecraft:structure_block','minecraft:jigsaw'}:
                                d={'action':'REMOVE','reason':'USER_REQUEST_SYSTEM_OR_INVISIBLE_CARRIER','sourceState':state};di=decision_id(d);dest[at]=di;actions['REMOVE']+=len(at);reasons[d['reason']]+=len(at)
                                for i in at:removals.write(token({'dimension':dim,'position':[cx*16+(int(i)&15),sy*16+(int(i)>>8),cz*16+((int(i)>>4)&15)],'sourceState':state,'reason':d['reason']})+'\n')
                            else:actions['KEEP']+=len(at)
                            continue
                        if state not in state_groups:state_groups[state]=branches_for(raw[carrier],props)
                        xyz=np.column_stack((cx*16+(at&15),sy*16+(at>>8),cz*16+((at>>4)&15))).astype(np.int64);selections=[]
                        for _,group in state_groups[state]:
                            choices=weighted_indices([o[0].get('weight',1) for o in group],xyz,'multipart' in raw[carrier]) if len(group)>1 else np.zeros(len(at),dtype=np.int16)
                            apps=[app_clean({**o[0],'model':normalized_model(o[0]['model'])}) for o in group];selections.append((choices,apps))
                        choicearray=np.column_stack([c for c,_ in selections]) if selections else np.zeros((len(at),0),dtype=np.int16)
                        combinations=np.unique(choicearray,axis=0) if selections else [()]
                        for combination in combinations:
                            local=np.flatnonzero(np.all(choicearray==combination,axis=1)) if selections else np.arange(len(at));positions=at[local]
                            apps=sorted([a[int(combination[k])] for k,(_,a) in enumerate(selections)],key=token);key=(state,token(apps))
                            if key not in classification:
                                d=r.classify(apps,carrier,props)
                                if d['action']=='OWNER':
                                    reg=d['registryId'];p=d['properties'];pose=int(p.get('variant',0))+16*int(p.get('art_bank',0))
                                    if reg in compact and pose in compact[reg] and not d.get('sourceNativeModelOffsetState') and not any(d.get('sourceShift',[0,0,0])) and int(p.get('rotation',0))%2==0 and not d.get('payload',{}).get('ImportedStairLegacyArt'):
                                        d['action']='COMPACT';p['compact']='true';d['payload']={}
                                    elif not reg.startswith('bloodborne_dw:prototype_wall'):p['compact']='false' if reg in compact else p.get('compact','')
                                    if p.get('compact')=='':p.pop('compact',None)
                                d['sourceState']=state;d['sourceApplications']=apps;classification[key]=d
                            d=classification[key];di=decision_id(d);dest[positions]=di
                            touched=consumed_chunks.get((dim,cx,cz),{}).get(sy,{})
                            if touched:
                                for i in positions:
                                    if int(i) in touched:dest[i]=decision_id({'action':'CONSUMED','wholeObject':touched[int(i)],'reason':'EXACT_COMPLETE_SOURCE_CONSTRUCTION'})
                            actual=collections.Counter(map(int,dest[positions]));
                            for id,count in actual.items():
                                value=decision_values[id];action=value['action'];actions[action]+=count;reason=value.get('reason',value.get('resolution',''));reasons[reason]+=count
                                if action in {'OWNER','COMPACT'}:types[value['registryId']]+=count
                            if d['action'] in {'REMOVE','UNRESOLVED_VISIBLE','WHOLE_CANDIDATE'}:
                                for n,i in enumerate(positions):
                                    if int(dest[i])!=di:continue
                                    pos=xyz[local[n]].tolist();row={'dimension':dim,'position':pos,'sourceState':state,'reason':d.get('reason',d['action']),'sourceApplications':apps}
                                    if d['action']=='REMOVE':removals.write(token(row)+'\n')
                                    else:unresolved.append(row)
                                    if len(samples[row['reason']])<8:samples[row['reason']].append(row)
                    out.execute('INSERT INTO sections VALUES(?,?,?,?,?,?,?)',(dim,cx,cz,sy,token(desc),zlib.compress(np.asarray(ind,dtype='<u2').tobytes(),1),zlib.compress(dest.astype('<u2').tobytes(),1)))
            out.commit();dump(O/'full-plan-progress.json',{'status':'RUNNING','regions':rn,'regionsTotal':len(members),'chunks':chunks,'sections':sections,'counts':dict(actions),'elapsedSeconds':round(time.monotonic()-start,2)});print(token({'region':rn,'chunks':chunks,'sections':sections,'actions':dict(actions)}),flush=True)
    removals.close();os.replace(removal_temp,O/'source-removal-coordinates.jsonl.gz')
    summary={'schemaVersion':1,'status':'PASS_COMPLETE_SOURCE_PLAN' if not unresolved else 'FAIL_KNOWN_VISIBLE_SOURCE_GAPS','sourceHashes':{'map':sha(MAP),'pack':sha(PACK)},'chunks':chunks,'sections':sections,'allPaletteCells':sum(sourcecounts.values()),'counts':dict(actions),'reasonCounts':dict(reasons),'targetTypeCounts':dict(types),'wholeObjects':len(whole),'consumedSourceCells':len(consumed),'knownVisibleGaps':len(unresolved),'gapSamples':unresolved[:200],'removalCoordinateFile':'source-removal-coordinates.jsonl.gz','removalCoordinateSha256':sha(O/'source-removal-coordinates.jsonl.gz'),'sourceResolutionContextCount':len(classification),'decisionDictionaryCount':len(decisions),'planFile':str(path.relative_to(ROOT)),'planBytes':path.stat().st_size,'sourceArchivesModified':False,'fullWorldConverted':False,'RAMPolicy':'One region/chunk/section at a time; compressed uint16 source+action arrays and shared decisions; compact palettes allocate no UUID/BE/owner ledger. No 58m per-cell object list.','initialOverlapPrivilege':'Only initial derived owner UUID, in nonserialized SourceConversionScope; normal pick/break/rebuild has no import exception.','nativeValidation':'NOT_RUN_FULL_ORIGINAL_CONVERSION_BY_USER_REQUEST','elapsedSeconds':round(time.monotonic()-start,2)}
    out.execute('INSERT OR REPLACE INTO metadata VALUES(?,?)',('summary',token(summary)));out.commit();out.close();source.close();dump(O/'full-conversion-plan.json',summary);dump(O/'full-conversion-known-gaps.json',{'status':'PASS_NO_GAPS' if not unresolved else 'FAIL','rows':unresolved});print(token(summary))

def export_runtime(path,dest):
    con=sqlite3.connect(f'file:{path}?mode=ro',uri=True);summary=json.loads(con.execute("SELECT data FROM metadata WHERE key='summary'").fetchone()[0]);assert summary['status']=='PASS_COMPLETE_SOURCE_PLAN','Never export a runtime plan with unresolved visible author content'
    assert summary.get('sourceStylePolicy') not in {'PENDING_USER_DECISION','APPROVED_PENDING_IMPLEMENTATION'},'Never export executable runtime data before the known source-style preservation decision'
    decisions={i:json.loads(d) for i,d in con.execute('SELECT id,data FROM decisions')};dest.mkdir(parents=True,exist_ok=True)
    with gzip.open(dest/'sections.jsonl.gz','wt',encoding='utf8') as out,gzip.open(dest/'owners.jsonl.gz','wt',encoding='utf8') as ownerout:
        for dim,cx,cz,sy,sp,si,di in con.execute('SELECT * FROM sections ORDER BY dimension,cx,cz,sy'):
            index=np.frombuffer(zlib.decompress(di),dtype='<u2');sourceindex=np.frombuffer(zlib.decompress(si),dtype='<u2');sourcepalette=json.loads(sp);palette=[];pids={};values=np.zeros(4096,dtype='<u2');owners=[]
            if np.all(index==0):continue
            for did in np.unique(index):
                d=decisions[int(did)];at=np.flatnonzero(index==did);action=d['action']
                if action=='KEEP':
                    for pi in np.unique(sourceindex[at]):
                        state,reg,props=sourcepalette[int(pi)];tok=token({'registryId':reg,'properties':props});pids.setdefault(tok,len(pids));values[at[sourceindex[at]==pi]]=pids[tok]
                else:
                    native={'registryId':d['registryId'],'properties':d['properties']} if action in {'COMPACT','VANILLA'} else {'registryId':'minecraft:air','properties':{}}
                    tok=token(native);pids.setdefault(tok,len(pids));values[at]=pids[tok]
                    if action=='OWNER':
                        for i in at:
                            p=[cx*16+(int(i)&15),sy*16+(int(i)>>8),cz*16+((int(i)>>4)&15)];row={'dimension':dim,'position':p,'instanceUuid':source_uuid(dim,p),'registryId':d['registryId'],'properties':d['properties'],'payload':d['payload']}
                            if d.get('sourceNativeModelOffsetState'):row.update(sourceNativeModelOffsetState=d['sourceNativeModelOffsetState'],sourceNativeModelOffsetPosition=p)
                            owners.append(row);ownerout.write(token(row)+'\n')
            palette=[None]*len(pids)
            for key,i in pids.items():palette[i]=json.loads(key)
            out.write(token({'dimension':dim,'chunk':[cx,cz],'sectionY':sy,'palette':palette,'indices':values.tolist()})+'\n')
    with gzip.open(dest/'whole-objects.jsonl.gz','wt',encoding='utf8') as out:
        for (raw,) in con.execute('SELECT data FROM whole_objects ORDER BY dimension,x,y,z'):
            row=json.loads(raw);d=row.pop('decision');out.write(token({**row,'registryId':d['registryId'],'properties':d['properties'],'payload':d['payload']})+'\n')
    dump(dest/'manifest.json',{**summary,'runtimeFiles':{p.name:sha(p) for p in dest.glob('*.jsonl.gz')},'sourcePlanSha256':sha(path),'sourceStylePolicySha256':sha(O/'source-style-policy.json')})

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--plan',type=Path,default=PLAN);p.add_argument('--export-runtime',type=Path);a=p.parse_args()
    if a.export_runtime:export_runtime(a.plan,a.export_runtime)
    else:prepare(a.plan)
