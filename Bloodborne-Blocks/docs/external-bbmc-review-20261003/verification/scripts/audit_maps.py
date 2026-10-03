"""Read-only ZIP/Anvil audit; actual palette counts and semantic terrain deltas."""
import argparse, collections, hashlib, json, math, re, sys, time, zipfile
from pathlib import Path
import numpy as np
cli=argparse.ArgumentParser()
cli.add_argument('--inputs',type=Path,required=True)
cli.add_argument('--output',type=Path,required=True)
cli.add_argument('--project',type=Path,default=Path(__file__).resolve().parents[4])
options=cli.parse_args()
ROOT=options.inputs;OUT=options.output;OUT.mkdir(parents=True,exist_ok=True)
sys.path.insert(0,str(options.project/'tools'))
from world_io import RegionFile, decode_nbt, block_state_key
FILES=['bbmc_v1_map (2).zip','bbmc_v13_map.zip','bbmc_v14_map.zip','bbmc_v15_map.zip','bbmc_v16_map (1).zip','VladraCastle (1).zip']
CHUNK_CACHE={}; SECTION_CACHE={}; STATE_IDS={}; INTERNAL=[]
def sha(b):return hashlib.sha256(b).hexdigest()
def value(d,k,default=None):return d[k].value if k in d else default
def ids(entities):
    result=collections.Counter();nested=0
    for e in entities:
        d=e.value;result[value(d,'id','<missing>')]+=1;children=value(d,'Passengers',[])
        if children:
            extra,_=ids(children);result.update(extra);nested+=sum(extra.values())
    return result,nested
def section(palette,data):
    keys=tuple(block_state_key(p) for p in palette)
    if not keys:raise ValueError('empty block palette')
    cachekey=(keys,sha(np.asarray(data,dtype=np.int64).tobytes()))
    if cachekey in SECTION_CACHE:return SECTION_CACHE[cachekey]
    if len(keys)==1 and not data:
        counts={keys[0]:4096};semantic=sha(('uniform:'+keys[0]).encode())
    else:
        bits=max(4,(len(keys)-1).bit_length());per=64//bits;expected=math.ceil(4096/per)
        if len(data)!=expected:raise ValueError(f'{len(data)} longs != expected {expected}')
        words=np.asarray(data,dtype=np.int64).view(np.uint64)
        idx=((words[:,None]>>(np.arange(per,dtype=np.uint64)*np.uint64(bits)))&np.uint64((1<<bits)-1)).reshape(-1)[:4096].astype(np.int32)
        if int(idx.max())>=len(keys):raise ValueError('palette index outside range')
        freq=np.bincount(idx,minlength=len(keys));counts={k:int(n) for k,n in zip(keys,freq) if n}
        if len(counts)==1:semantic=sha(('uniform:'+next(iter(counts))).encode())
        else:
            for k in keys:
                if k not in STATE_IDS:STATE_IDS[k]=len(STATE_IDS)
            lookup=np.asarray([STATE_IDS[k] for k in keys],dtype=np.uint32);semantic=sha(lookup[idx].tobytes())
    nonair=sum(n for k,n in counts.items() if k.split('[',1)[0] not in ('minecraft:air','minecraft:cave_air','minecraft:void_air'))
    result=(counts,semantic,nonair);SECTION_CACHE[cachekey]=result;return result
def analyze_chunk(c,kind):
    digest=sha(bytes([c.compression])+c.compressed_payload);cachekey=(kind,digest)
    if cachekey in CHUNK_CACHE:return digest,CHUNK_CACHE[cachekey]
    d=decode_nbt(c.raw_nbt()).root.value
    if 'Level' in d:d=d['Level'].value
    entities,nested=ids(value(d,'Entities',value(d,'entities',[])));be,_=ids(value(d,'block_entities',value(d,'TileEntities',[])))
    states=collections.Counter();palette_names=set();chunksign=[];sections=0;nonair=0;commands=collections.Counter()
    if kind=='region':
        for sec in value(d,'sections',value(d,'Sections',[])):
            s=sec.value
            if 'block_states' in s:
                bs=s['block_states'].value;palette=value(bs,'palette',[]);data=value(bs,'data',[])
            elif 'Palette' in s:palette=value(s,'Palette',[]);data=value(s,'BlockStates',[])
            else:continue
            sections+=1;counts,semantic,present=section(palette,data);states.update(counts);nonair+=present
            palette_names.update(value(p.value,'Name') for p in palette)
            if present:chunksign.append((value(s,'Y'),semantic))
        for item in value(d,'block_entities',value(d,'TileEntities',[])):
            command=value(item.value,'Command','').lstrip('/')
            if command:commands[command.split(None,1)[0]]+=1
    info={'states':dict(states),'palette_names':sorted(palette_names),'entities':dict(entities),'nested_entities':nested,'block_entities':dict(be),'sections':sections,'nonair':nonair,'terrain_hash':sha(json.dumps(sorted(chunksign)).encode()),'version':value(d,'DataVersion'),'commands':dict(commands)}
    CHUNK_CACHE[cachekey]=info;return digest,info
def run(fn):
    p=ROOT/fn;out={'file':fn,'bytes':p.stat().st_size,'sha256':sha(p.read_bytes()),'errors':[]};internal={}
    with zipfile.ZipFile(p) as z:
        names=z.namelist();out['zip_crc_failure']=z.testzip();out['zip_entries']=len(names)
        out['unsafe_paths']=[n for n in names if n.startswith(('/', '\\')) or '..' in n.replace('\\','/').split('/')]
        out['duplicate_paths']=[n for n,c in collections.Counter(names).items() if c>1]
        level=next(n for n in names if n.endswith('level.dat'));prefix=level[:-len('level.dat')]
        d=decode_nbt(z.read(level),compressed='gzip').root.value;d=d['Data'].value
        out['level']={k:value(d,k) for k in ['DataVersion','LevelName','GameType','Difficulty','SpawnX','SpawnY','SpawnZ','WasModded','allowCommands'] if k in d}
        out['level']['minecraft_version']=value(d['Version'].value,'Name') if 'Version' in d else None
        gamerules=value(d,'GameRules',{});out['gamerules']={k:t.value for k,t in gamerules.items() if k in ['doMobSpawning','doDaylightCycle','randomTickSpeed','mobGriefing','doFireTick','doTileDrops','doEntityDrops']}
        datapacks=value(d,'DataPacks',{});out['enabled_datapacks']=[t.value for t in value(datapacks,'Enabled',[])] if datapacks else []
        out['embedded_datapack_files']=[n[len(prefix):] for n in names if n.startswith(prefix+'datapacks/') and not n.endswith('/')]
        out['contains_playerdata']=any('/playerdata/' in '/'+n for n in names) or 'Player' in d
        out['containers']={};states=collections.Counter();palettes=set();entityids=collections.Counter();beids=collections.Counter();versions=collections.Counter();cmds=collections.Counter();sectioncount=nonair=0
        regions=[n for n in names if re.search(r'(?:^|/)(?:region|entities|poi)/r\.-?\d+\.-?\d+\.mca$',n)];out['region_hashes']={};out['empty_container_placeholders']=[]
        for i,rn in enumerate(regions):
            rel=rn[len(prefix):];m=re.match(r'(.*?)(region|entities|poi)/r\.(-?\d+)\.(-?\d+)\.mca$',rel)
            if not m:out['errors'].append({'path':rel,'error':'unrecognized container path'});continue
            dim,kind,rx,rz=m.groups();rx=int(rx);rz=int(rz)
            container=out['containers'].setdefault(kind,{'region_files':0,'chunks':0,'decoded_chunks':0});container['region_files']+=1
            raw=z.read(rn);out['region_hashes'][rel]=sha(raw)
            if not raw:
                out['empty_container_placeholders'].append(rel);continue
            try:rf=RegionFile(raw)
            except Exception as e:out['errors'].append({'path':rel,'error':str(e)});continue
            try:
                for c in rf.chunks():
                    container['chunks']+=1;coord=f'{dim or "overworld/"}{kind}/{rx*32+c.x},{rz*32+c.z}'
                    try:digest,info=analyze_chunk(c,kind)
                    except Exception as e:out['errors'].append({'path':rel,'chunk':[rx*32+c.x,rz*32+c.z],'error':str(e)});continue
                    container['decoded_chunks']+=1;versions[str(info['version'])]+=1;entityids.update(info['entities']);beids.update(info['block_entities']);cmds.update(info['commands'])
                    if kind=='region':states.update(info['states']);palettes.update(info['palette_names']);sectioncount+=info['sections'];nonair+=info['nonair']
                    internal[coord]={'payload_hash':digest,'terrain_hash':info['terrain_hash'] if kind=='region' else None}
            except Exception as e:out['errors'].append({'path':rel,'error':str(e)})
            if i%8==0:print(f'{fn}: {i+1}/{len(regions)} containers, {sum(v["chunks"] for v in out["containers"].values())} chunks, errors={len(out["errors"])}',flush=True)
        blocks=collections.Counter()
        for state,n in states.items():blocks[state.split('[',1)[0]]+=n
        namespaces=collections.Counter()
        for block,n in blocks.items():namespaces[block.split(':',1)[0]]+=n
        out.update({'block_state_counts':dict(states),'block_counts':dict(blocks),'block_namespaces':dict(namespaces),'actual_unique_states':len(states),'actual_unique_blocks':len(blocks),'palette_unique_blocks':len(palettes),'unused_palette_blocks':sorted(palettes-blocks.keys()),'entity_ids':dict(entityids),'block_entity_ids':dict(beids),'commands_by_first_word':dict(cmds),'chunk_data_versions':dict(versions),'explicit_block_sections':sectioncount,'explicit_section_cells':sectioncount*4096,'nonair_cells':nonair})
    INTERNAL.append(internal);return out
def compare(a,b,ia,ib):
    ak=set(ia);bk=set(ib);common=ak&bk;region_a=a['region_hashes'];region_b=b['region_hashes']
    return {'from':a['file'],'to':b['file'],'common_container_chunks':len(common),'added_container_chunks':len(bk-ak),'removed_container_chunks':len(ak-bk),'changed_chunk_payloads':sum(ia[k]['payload_hash']!=ib[k]['payload_hash'] for k in common),'semantic_terrain_changed_chunks':sum(ia[k]['terrain_hash']!=ib[k]['terrain_hash'] for k in common if '/region/' in k or k.startswith('region/')),'terrain_added_chunks':sum('/region/' in k or k.startswith('region/') for k in bk-ak),'terrain_removed_chunks':sum('/region/' in k or k.startswith('region/') for k in ak-bk),'identical_region_files':sum(region_a[k]==region_b[k] for k in region_a.keys()&region_b.keys()),'entity_count_deltas':{k:b['entity_ids'].get(k,0)-a['entity_ids'].get(k,0) for k in a['entity_ids'].keys()|b['entity_ids'].keys() if a['entity_ids'].get(k,0)!=b['entity_ids'].get(k,0)}}
def fully_valid(archives):
    return len(archives)==len(FILES) and all(a['zip_crc_failure'] is None and not a['errors'] and not a['unsafe_paths'] and not a['duplicate_paths'] and all(c['chunks']==c['decoded_chunks'] for c in a['containers'].values()) for a in archives)
if __name__=='__main__':
    started=time.monotonic();archives=[]
    for fn in FILES:
        a=run(fn);archives.append(a);(OUT/'progress.json').write_text(json.dumps({'complete_archives':archives},ensure_ascii=False),encoding='utf-8');print('COMPLETE',fn,a['containers'],'entities',sum(a['entity_ids'].values()),'errors',len(a['errors']),flush=True)
    deltas=[compare(archives[i],archives[i+1],INTERNAL[i],INTERNAL[i+1]) for i in range(4)]
    summary=[{k:a[k] for k in ['file','level','containers','actual_unique_blocks','actual_unique_states','nonair_cells','entity_ids','block_namespaces']} for a in archives]
    valid=fully_valid(archives)
    out={'valid':valid,'coverage':('All ZIP entries CRC checked; all terrain/entity/POI chunk records structurally validated and NBT decoded; actual block indices decoded, unused palette entries excluded.' if valid else 'Audit incomplete or validation failed; inspect per-archive errors/checks before using counts.')+' Missing sections are implicit air, explicit cell count is not total world volume. Semantic deltas count coordinates with changed nonair terrain state arrangement, excluding time/light/entity metadata; they are not individual changed-block counts.','archives':archives,'deltas':deltas,'summary':summary,'elapsed_seconds':round(time.monotonic()-started,2),'cache_unique_chunks':len(CHUNK_CACHE),'cache_unique_sections':len(SECTION_CACHE)}
    (OUT/'map-analysis.json').write_text(json.dumps(out,ensure_ascii=False,indent=2),encoding='utf-8');print('DONE',out['elapsed_seconds'],flush=True)
