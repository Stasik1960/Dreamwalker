"""Read original ZIP; inventory actual blocks, all region kinds and mod NBT.

No palette-frequency approximation and no Minecraft DataVersion rewriting.
Coordinates use Java Anvil layout y*256+z*16+x. Source bytes stay untouched.
"""
from __future__ import annotations
import argparse, gzip, hashlib, json, re, time, zipfile
from collections import Counter, defaultdict
from pathlib import Path
import numpy as np
from world_io import RegionFile, Tag, compound, decode_nbt, block_state_key, count_palette_indices, section_blocks
from record_inputs import sha256

ROOT=Path(__file__).resolve().parents[1]
AIR={'minecraft:air','minecraft:cave_air','minecraft:void_air'}
def plain(tag):
    if tag is None: return None
    if tag.type==10: return {k:plain(v) for k,v in tag.value.items()}
    if tag.type==9: return [plain(x) for x in tag.value]
    if isinstance(tag.value,bytes): return {'byte_length':len(tag.value),'sha256':hashlib.sha256(tag.value).hexdigest()}
    return tag.value
def dimension(path):
    if path.startswith('DIM-1/'): return 'minecraft:the_nether'
    if path.startswith('DIM1/'): return 'minecraft:the_end'
    if path.startswith('dimensions/'):
        parts=path.split('/')
        return parts[1]+':'+ '/'.join(parts[2:-2])
    return 'minecraft:overworld'
def find_mod_references(tag,path,refs):
    if tag.type==10:
        for k,v in tag.value.items(): find_mod_references(v,path+'/'+k,refs)
    elif tag.type==9:
        for i,v in enumerate(tag.value): find_mod_references(v,path+'/'+str(i),refs)
    elif tag.type==8 and re.match(r'^[a-z0-9_.-]+:[a-z0-9_./-]+$',tag.value):
        if not tag.value.startswith('minecraft:'): refs[tag.value].append(path)
def main():
    parser=argparse.ArgumentParser(); parser.add_argument('--world',default='C:/Users/vakir/Downloads/bbmc_v16_map (1).zip')
    parser.add_argument('--pack',default='C:/Users/vakir/Downloads/bbmc_v15_resource (1).zip')
    parser.add_argument('--output',type=Path,default=ROOT/'reports/WORLD_AUDIT.json'); args=parser.parse_args()
    started=time.perf_counter(); before=sha256(args.world)
    with zipfile.ZipFile(args.pack) as p:
        carriers={n.split('/')[1]+':'+n.split('/blockstates/',1)[1][:-5] for n in p.namelist() if '/blockstates/' in n and n.endswith('.json')}
    states=Counter(); samples=defaultdict(list); per_dim=defaultdict(Counter); versions=Counter(); regions=Counter(); chunks=Counter()
    entity_counts=Counter(); entities=[]; be_counts=Counter(); be_samples=[]; refs=defaultdict(list); nbt_files=[]; errors=[]
    bounds={}; max_arch_y={}; ticks=Counter(); files=[]
    with zipfile.ZipFile(args.world) as z:
        names=z.namelist()
        level=next(n for n in names if n=='level.dat' or n.endswith('/level.dat'))
        prefix=level[:-len('level.dat')]
        level_nbt=decode_nbt(z.read(level),compressed='gzip'); level_data=compound(compound(level_nbt.root)['Data'])
        level_summary={k:plain(level_data.get(k)) for k in ['DataVersion','Version','WorldGenSettings','DataPacks','SpawnX','SpawnY','SpawnZ','Time','DayTime','GameRules']}
        for n in sorted(names):
            info=z.getinfo(n)
            if info.is_dir(): continue
            rel=n[len(prefix):] if n.startswith(prefix) else n
            data=z.read(n)
            files.append({'path':rel,'size':len(data),'sha256':hashlib.sha256(data).hexdigest()})
            if n.endswith('.dat') or n.endswith('.dat_old'):
                if rel=='uid.dat':
                    nbt_files.append({'path':rel,'format':'non_NBT_UUID_bytes','size':len(data)}); continue
                try:
                    nbt=decode_nbt(data,compressed='gzip' if data.startswith(b'\x1f\x8b') else None)
                    find_mod_references(nbt.root,rel,refs)
                    nbt_files.append({'path':rel,'root_keys':list(compound(nbt.root)),
                        'data':plain(nbt.root) if '/data/' in '/'+rel and len(data)<50000 else None})
                except Exception as e: errors.append({'path':rel,'kind':'NBT','error':str(e)})
            if not n.endswith('.mca'): continue
            kind=Path(rel).parent.name; dim=dimension(rel); regions[kind]+=1
            try:
                region=RegionFile(data)
                for stored in region.chunks():
                    root=compound(stored.nbt().root); chunks[kind]+=1
                    versions[str(root.get('DataVersion',Tag(3,-1)).value)]+=1
                    find_mod_references(Tag(10,root),rel+f'/chunk[{stored.x},{stored.z}]',refs)
                    if kind=='entities':
                        for ent in root.get('Entities',Tag(9,[],10)).value:
                            d=compound(ent); eid=d.get('id',Tag(8,'<missing>')).value; entity_counts[eid]+=1
                            entities.append({'dimension':dim,'region':rel,'id':eid,'uuid':plain(d.get('UUID')),
                                'pos':plain(d.get('Pos')),'data':plain(ent) if not eid.startswith('minecraft:') else None})
                        continue
                    if kind!='region': continue
                    legacy=root.get('Level'); root=compound(legacy) if legacy else root
                    cx=int(root.get('xPos',Tag(3,stored.x)).value); cz=int(root.get('zPos',Tag(3,stored.z)).value)
                    for key in ['block_ticks','fluid_ticks','TileTicks','LiquidTicks']:
                        ticks[key]+=len(root.get(key,Tag(9,[],10)).value)
                    for be in root.get('block_entities',root.get('TileEntities',Tag(9,[],10))).value:
                        b=compound(be); eid=b.get('id',Tag(8,'<missing>')).value; be_counts[eid]+=1
                        if len(be_samples)<200 or not eid.startswith('minecraft:'):
                            be_samples.append({'dimension':dim,'data':plain(be)})
                    for section in root.get('sections',root.get('Sections',Tag(9,[],10))).value:
                        fields=compound(section); sy=int(fields['Y'].value); container=fields.get('block_states')
                        if not container: continue
                        c=compound(container); palette=c['palette'].value; packed=c.get('data',Tag(12,[])).value
                        local=count_palette_indices(packed,len(palette)); keys=[block_state_key(x) for x in palette]
                        interested=[]
                        for pi,amount in local.items():
                            key=keys[pi]; states[key]+=amount; per_dim[dim][key]+=amount
                            if key.split('[',1)[0] in carriers: interested.append(pi)
                        if not interested: continue
                        _,indices=section_blocks(section); arr=np.asarray(indices,dtype=np.int32)
                        for pi in interested:
                            key=keys[pi]; positions=np.flatnonzero(arr==pi)
                            if not len(positions): continue
                            ys=sy*16+(positions//256); high=int(ys.max()); max_arch_y[dim]=max(high,max_arch_y.get(dim,-100000))
                            xx=cx*16+(positions%16); zz=cz*16+(positions//16)%16
                            lo=[int(xx.min()),int(ys.min()),int(zz.min())]; hi=[int(xx.max()),high,int(zz.max())]
                            previous=bounds.get(dim)
                            bounds[dim]={'min':lo,'max':hi} if not previous else {'min':[min(a,b) for a,b in zip(previous['min'],lo)],'max':[max(a,b) for a,b in zip(previous['max'],hi)]}
                            for index in positions[:max(0,12-len(samples[key]))]:
                                samples[key].append({'dimension':dim,'pos':[cx*16+int(index)%16,sy*16+int(index)//256,cz*16+(int(index)//16)%16]})
            except Exception as e:
                errors.append({'path':rel,'kind':'region','error':str(e)})
    after=sha256(args.world)
    if before!=after: raise RuntimeError('Original world archive changed during audit')
    result={'schema':'dreamwalker-world-audit-v1','source':{'path':args.world,'sha256_before':before,'sha256_after':after},
        'method':'all region/entities/poi payloads decoded; occupied 4096-cell section indices counted, not palette occurrences',
        'level':level_summary,'regions':dict(regions),'chunks':dict(chunks),'data_versions':dict(versions),
        'state_count':len(states),'blocks_total':sum(states.values()),'states':[{'state':s,'count':n,'samples':samples.get(s,[])} for s,n in sorted(states.items())],
        'dimensions':{d:{'block_cells':sum(c.values()),'state_count':len(c),'architecture_carrier_bounds':bounds.get(d),'max_architecture_carrier_y':max_arch_y.get(d)} for d,c in sorted(per_dim.items())},
        'counts_are_logical_objects':False,'source_carriers':sorted(carriers),'entities':entities,'entity_counts':dict(sorted(entity_counts.items())),
        'block_entity_counts':dict(sorted(be_counts.items())),'block_entity_samples':be_samples,'scheduled_ticks':dict(ticks),
        'mod_references':{k:{'occurrences':len(v),'paths':v[:100]} for k,v in sorted(refs.items())},'nbt_files':nbt_files,
        'datapack_files':[n for n in names if '/datapacks/' in '/'+n], 'files':files,'errors':errors,'elapsed_seconds':round(time.perf_counter()-started,3)}
    args.output.parent.mkdir(parents=True,exist_ok=True); args.output.write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    input_path=ROOT/'INPUTS.json'
    if input_path.exists():
        inputs=json.loads(input_path.read_text(encoding='utf-8')); inputs['world'].update({'source_minecraft':level_summary['Version'],'data_version':level_summary['DataVersion'],'dimensions':sorted(per_dim),'audit_sha256':sha256(args.output)})
        input_path.write_text(json.dumps(inputs,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(json.dumps({'output':str(args.output),'states':len(states),'chunks':dict(chunks),'entities':sum(entity_counts.values()),'errors':len(errors),'seconds':result['elapsed_seconds']}))
if __name__=='__main__': main()
