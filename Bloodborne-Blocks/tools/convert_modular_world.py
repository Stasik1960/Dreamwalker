"""Convert a COPY of the supplied Anvil world to the v2 modular palette.

Two passes: palette-only bulk replacement (millions of cubes stay cheap), then
spatial expansion/composition of oversized architectural models. Original files
are never written; foreign blocks and foreign block-entity data are protected.
"""
from world_io import *
from build_modular_palette import Palette, OUT, RES, ROOT, ASSETS, FACING, rotate_polys, dump, statekey, read_meshes
import argparse, copy, shutil, collections, hashlib
import numpy as np

NS='bloodborne_blocks:'
AIR=Tag(TAG_COMPOUND,{'Name':Tag(TAG_STRING,'minecraft:air')})

def tag_state(ident,props=None):
    out={'Name':Tag(TAG_STRING,ident if ':' in ident else NS+ident)}
    if props:out['Properties']=Tag(TAG_COMPOUND,{k:Tag(TAG_STRING,str(v)) for k,v in props.items()})
    return Tag(TAG_COMPOUND,out)

def props(entry):return {k:v.value for k,v in compound(entry).get('Properties',Tag(TAG_COMPOUND,{})).value.items()}

def unpack_fast(states):
    pal=states['palette'].value
    if len(pal)==1:return np.zeros(4096,dtype=np.int32)
    bits=max(4,(len(pal)-1).bit_length());step=64//bits
    longs=np.array([x&((1<<64)-1) for x in states['data'].value],dtype=np.uint64)
    result=((longs[:,None]>>np.arange(0,step*bits,bits,dtype=np.uint64))&((1<<bits)-1)).ravel()[:4096].astype(np.int32)
    if result.max()>=len(pal):raise ValueError('Invalid palette index')
    return result

def set_section(states,palette,array):
    # Coalesce palette aliases and remove unused entries after composites.
    unique={};remap=[];pal=[]
    for entry in palette:
        key=block_state_key(entry)
        if key not in unique:unique[key]=len(pal);pal.append(entry)
        remap.append(unique[key])
    a=np.array(remap,dtype=np.int32)[array];used=np.unique(a);mapping={int(v):i for i,v in enumerate(used)}
    a=np.array([mapping.get(i,0) for i in range(len(pal))],dtype=np.int32)[a];pal=[pal[int(i)] for i in used]
    states['palette']=Tag(TAG_LIST,pal,TAG_COMPOUND)
    if len(pal)==1:states.pop('data',None)
    else:states['data']=Tag(TAG_LONG_ARRAY,pack_palette_indices(a.tolist(),len(pal)))

def load_palette():
    p=Palette();p.blocks={b['id']:b for b in json.loads((OUT/'definitions.json').read_text())['blocks']}
    p.meshes=read_meshes();geo=json.loads((OUT/'geometry.json').read_text());p.geometry=geo['blocks'];p.sources=json.loads((OUT/'sources.json').read_text())
    p.compacted=set(p.meshes)
    p.persisted=set(p.blocks)
    for block in p.geometry.values():
        block['states']={k:geo['profiles'][v['ref']] if 'ref' in v else v for k,v in block['states'].items()}
    names=json.loads((ASSETS/'lang/ru_ru.json').read_text(encoding='utf-8'))
    p.names={i:names['block.bloodborne_blocks.'+i] for i in p.blocks}
    migration=json.loads((OUT/'migration.json').read_text())
    p.public={part['id'] for spec in migration.values() for value in [spec['states'][statekey(spec['default'])]] if isinstance(value,list) for part in value}
    if (OUT/'public-items.json').exists():p.public.update(json.loads((OUT/'public-items.json').read_text()))
    return p

def load_component(p,ident,facing):
    turn=FACING.index(facing);polys=rotate_polys(p.meshes[ident]['polygons'],-turn)
    key='facing='+facing
    if key not in p.geometry[ident]['states']:key+=',waterlogged=false'
    boxes=p.geometry[ident]['states'][key]['cells']['0,0,0']['collision']
    return polys,boxes

def convert(source,target):
    source=source.resolve();target=target.resolve()
    if target==source or source in target.parents:raise ValueError('Output must be a separate world copy')
    if target.exists():raise ValueError('Output already exists; choose a new path')
    shutil.copytree(source,target)
    migration=json.loads((OUT/'migration.json').read_text());kept={i for i,spec in migration.items() if any(isinstance(v,dict) and v.get('keep') for v in spec['states'].values())}
    palette=load_palette();pending=collections.defaultdict(lambda:collections.defaultdict(list));stats=collections.Counter();conflicts=[]
    original_hashes={p.relative_to(source).as_posix():hashlib.sha256(p.read_bytes()).hexdigest() for p in source.rglob('*') if p.is_file()}
    def replacement(entry):
        name=compound(entry)['Name'].value
        if not name.startswith(NS):return None
        ident=name[len(NS):]
        if ident=='architecture_part':return 'helper'
        if ident not in migration:raise ValueError('Unknown legacy architecture '+ident)
        spec=migration[ident];s={**spec['default'],**props(entry)};key=statekey(s)
        if key not in spec['states']:raise ValueError('Unmapped world state '+ident+'['+key+']')
        return spec['states'][key]
    for path in sorted(target.glob('region/r.*.*.mca')):
        region=RegionFile.open(path)
        for number,stored in enumerate(list(region.chunks())):
            nbt=stored.nbt();root=compound(nbt.root);cx=root['xPos'].value;cz=root['zPos'].value;changed=False
            keep_helpers=set()
            entities=root.get('block_entities',Tag(TAG_LIST,[],TAG_COMPOUND))
            retained=[]
            for be in entities.value:
                bd=compound(be)
                if bd.get('id',Tag(TAG_STRING,'')).value==NS+'architecture_part':
                    owner=bd.get('Owner',Tag(TAG_STRING,'')).value.removeprefix(NS)
                    if owner in kept:
                        keep_helpers.add((bd['x'].value,bd['y'].value,bd['z'].value));retained.append(be);stats['retained_helper_entities']+=1
                    else:stats['removed_helper_entities']+=1;changed=True
                else:retained.append(be)
            if entities.value!=retained:root['block_entities']=Tag(TAG_LIST,retained,TAG_COMPOUND)
            for section in root.get('sections',Tag(TAG_LIST,[],TAG_COMPOUND)).value:
                sd=compound(section)
                if 'block_states' not in sd:continue
                states=compound(sd['block_states']);old=states['palette'].value
                if not any(compound(t)['Name'].value.startswith(NS) for t in old):continue
                transforms=[replacement(t) for t in old];new=[];complex_indices={};helpers=[]
                for i,(entry,change) in enumerate(zip(old,transforms)):
                    if change is None or isinstance(change,dict):new.append(entry)
                    elif change=='helper':new.append(AIR);helpers.append(i)
                    elif len(change)==1 and change[0]['offset']==[0,0,0]:
                        new.append(tag_state(change[0]['id'],change[0]['properties']));stats['bulk_palette_entries']+=1
                    else:new.append(AIR);complex_indices[i]=change
                array=unpack_fast(states);sy=sd['Y'].value
                if helpers and keep_helpers:
                    for i in np.flatnonzero(np.isin(array,helpers)):
                        pos=(cx*16+(int(i)&15),sy*16+(int(i)>>8),cz*16+((int(i)>>4)&15))
                        if pos in keep_helpers:
                            new.append(old[int(array[i])]);array[i]=len(new)-1
                if complex_indices:
                    for i in np.flatnonzero(np.isin(array,list(complex_indices))):
                        rootpos=(cx*16+(int(i)&15),sy*16+(int(i)>>8),cz*16+((int(i)>>4)&15))
                        components=complex_indices[int(array[i])];stats['expanded_legacy_roots']+=1
                        for component in components:
                            pos=tuple(rootpos[a]+component['offset'][a] for a in range(3))
                            pending[(pos[0]//16,pos[2]//16)][pos].append((component['id'],component['properties']['facing']))
                set_section(states,new,array);changed=True
            if changed:
                invalidate_chunk_lighting(root);root.pop('Heightmaps',None)
                # Old scheduled helper/block ticks cannot target replacement types.
                if 'block_ticks' in root:
                    root['block_ticks'].value=[t for t in root['block_ticks'].value if not compound(t).get('i',Tag(TAG_STRING,'')).value.startswith(NS)]
                region.set_chunk(stored.x,stored.z,nbt,timestamp=stored.timestamp);stats['rewritten_chunks']+=1
        region.save(path);print('Bulk',path.name,'expanded',stats['expanded_legacy_roots'],'pending cells',sum(map(len,pending.values())),flush=True)
    composite_cache={}
    for path in sorted(target.glob('region/r.*.*.mca')):
        region=RegionFile.open(path)
        for number,stored in enumerate(list(region.chunks())):
            nbt=stored.nbt();root=compound(nbt.root);key=(root['xPos'].value,root['zPos'].value)
            changes=pending.pop(key,{})
            if not changes:continue
            sections={compound(s)['Y'].value:s for s in root['sections'].value};loaded={}
            for pos,incoming in changes.items():
                y=pos[1]//16
                if y not in sections:
                    conflicts.append({'position':pos,'reason':'missing_section','pieces':incoming});continue
                section=sections[y];sd=compound(section)
                if y not in loaded:
                    if 'block_states' not in sd:sd['block_states']=Tag(TAG_COMPOUND,{'palette':Tag(TAG_LIST,[AIR],TAG_COMPOUND)})
                    states=compound(sd['block_states']);loaded[y]=(states,list(states['palette'].value),unpack_fast(states))
                states,pal,array=loaded[y];idx=(pos[1]%16)*256+(pos[2]%16)*16+pos[0]%16;current=pal[int(array[idx])];name=compound(current)['Name'].value
                pieces=list(incoming)
                if name.startswith(NS+'m_'):pieces.append((name[len(NS):],props(current).get('facing','north')))
                elif name not in ('minecraft:air','minecraft:cave_air','minecraft:void_air'):
                    conflicts.append({'position':pos,'reason':'protected_block','block':block_state_key(current),'pieces':incoming});continue
                # Old oversized models frequently extend inside solid masonry.
                # Preserve the existing opaque cube; baking hidden intersecting
                # pieces creates tens of thousands of pointless mesh variants.
                opaque=[p for p in reversed(pieces) if palette.blocks[p[0]]['full_cube']]
                if opaque:
                    stats['opaque_occluded_components']+=len(pieces)-1;pieces=[opaque[0]]
                ck=tuple(sorted(set(pieces)))
                if len(ck)==1:ident,facing=ck[0];result={'id':ident,'properties':{'facing':facing}}
                elif ck in composite_cache:result=composite_cache[ck]
                else:
                    polys=[];boxes=[]
                    for ident,facing in ck:
                        ps,bs=load_component(palette,ident,facing);polys.extend(ps);boxes.extend(bs)
                    if any(b==[0,0,0,1,1,1] for b in boxes):boxes=[[0,0,0,1,1,1]]
                    cat='ladder' if any(palette.blocks[i]['semantic']=='ladder' for i,f in ck) else palette.blocks[ck[0][0]]['semantic']
                    semantic={'category':cat,'ru':palette.names[ck[0][0]]+' — составная секция'}
                    result=palette.piece(polys,boxes,semantic,'world_composite',False,max(palette.blocks[i]['states']['facing=north'][2] for i,f in ck));composite_cache[ck]=result
                pal.append(tag_state(result['id'],result['properties']));array[idx]=len(pal)-1;stats['written_component_cells']+=1
            for y,(states,pal,array) in loaded.items():
                set_section(states,pal,array)
            invalidate_chunk_lighting(root);root.pop('Heightmaps',None)
            region.set_chunk(stored.x,stored.z,nbt,timestamp=stored.timestamp)
            if number%128==0:print('Compose progress',path.name,number,'types',len(composite_cache),'conflicts',len(conflicts),flush=True)
        region.save(path);print('Compose',path.name,'new composite types',len(composite_cache),flush=True)
    for chunk,positions in pending.items():
        for pos,pieces in positions.items():conflicts.append({'position':pos,'reason':'missing_chunk','pieces':pieces})
    palette.save()
    level=read_nbt(target/'level.dat');compound(compound(level.root)['Data'])['LevelName']=Tag(TAG_STRING,'Ether - Bloodborne 2.0');write_nbt(target/'level.dat',level)
    # Session locks and obsolete backup level metadata do not belong to a new copy.
    for filename in ('session.lock','level.dat_old'):
        p=target/filename
        if p.exists():p.unlink()
    report={'counts':dict(stats),'new_composite_types':len(composite_cache),'conflict_count':len(conflicts),'protected_conflicts':conflicts,'external_files_verified':False}
    dump(ROOT/'docs/world-conversion-v2.json',report)
    unchanged=[]
    for name,h in original_hashes.items():
        if name.startswith('region/') or name in ('level.dat','level.dat_old','session.lock'):continue
        if hashlib.sha256((target/name).read_bytes()).hexdigest()!=h:raise ValueError('Unexpected change outside regions: '+name)
        unchanged.append(name)
    report.update(unchanged_external_files=len(unchanged),external_files_verified=True,original_archive_untouched=True)
    dump(ROOT/'docs/world-conversion-v2.json',report)
    print('WORLD CONVERSION',dict(stats),'conflicts',len(conflicts),flush=True)

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('source',type=Path);parser.add_argument('target',type=Path);args=parser.parse_args();convert(args.source,args.target)
