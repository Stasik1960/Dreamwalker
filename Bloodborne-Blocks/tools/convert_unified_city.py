"""Complete registry migration with durable per-region checkpoints.

Every cell keeps its coordinates. Existing collision carriers can overlap and
retain all owners; ordinary in-game placement rules are not changed here.
"""
from __future__ import annotations
import argparse, copy, hashlib, json, os, shutil
from collections import Counter
from pathlib import Path
import numpy as np
from convert_logical_world import (NS, PART, Chunk, as_tag_state, state_of,
    block_pos_long, hash_tree, list_region_files, region_dimension)
from upgrade_gallery_city import StreamingWorld, parse_state
from city_palette import helper_bindings
from world_io import (RegionFile, Tag, TAG_STRING, TAG_LONG, TAG_COMPOUND,
    TAG_LIST, compound, section_blocks, block_state_key)


def atomic_json(path, value):
    temporary=path.with_suffix(path.suffix+'.tmp')
    temporary.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
    os.replace(temporary,path)


def extract_city(source, output):
    """Keep the corrected city and world metadata, excluding the old gallery."""
    source,output=Path(source).resolve(),Path(output).resolve()
    if output.exists() or source==output or source in output.parents:
        raise ValueError('new output outside original world required')
    manifest=json.loads((source/'gallery-manifest.json').read_bytes())
    allowed={r['path'] for r in manifest['cityContext']}
    output.mkdir(parents=True)
    for relative in allowed:
        p=Path(relative)
        if p.is_absolute() or '..' in p.parts:raise ValueError('unsafe city context path')
        dest=output/p;dest.parent.mkdir(parents=True,exist_ok=True)
        shutil.copy2(source/p,dest)
    for p in source.rglob('*'):
        if not p.is_file():continue
        rel=p.relative_to(source)
        if p.suffix=='.mca' or rel.parts[0] in {'playerdata','advancements','stats'}:continue
        if rel.as_posix() in {'gallery-manifest.json','Review-Cases.md','City-Upgrade.md','Gallery-Index.md','Native-Markers.md'}:continue
        dest=output/rel;dest.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(p,dest)
    atomic_json(output/'city-source.json',{'source':source.name,'cityFiles':sorted(allowed)})
    return {'regions':len(list_region_files(output)),'files':len(allowed)}


def load_mapping(path):
    data=json.loads(Path(path).read_bytes()); result={}
    for text,target in data['states'].items():
        if any(target.get('rootOffset',target.get('root_offset',[0,0,0]))):
            raise ValueError('coordinate-shifting mappings require a different converter')
        ident=target['id'];ident=ident if ':' in ident else NS+ident
        result[parse_state(text)]=(ident,tuple(sorted((k,str(v)) for k,v in target['properties'].items())))
    return result


def combined_mapping(mapping_path, split_mapping_path=None):
    """Combine the required city mapping with the logical tree/bush split."""
    result = load_mapping(mapping_path)
    if split_mapping_path is None:
        return result
    for source, target in load_mapping(split_mapping_path).items():
        previous = result.setdefault(source, target)
        if previous != target:
            raise ValueError('conflicting split mapping for '+block_state_key(as_tag_state(source)))
    return result


def default_split_mapping(new_city, explicit=None):
    if explicit is not None:
        return Path(explicit).resolve()
    candidate = Path(new_city).resolve().parent/'logical'/'tree-bush-split-migration.json'
    return candidate if candidate.is_file() else None


def definitions(city):
    defaults={};states={PART:{''}}
    for scope in (city,city.parent/'logical'):
        for d in json.loads((scope/'definitions.json').read_bytes())['blocks']:
            defaults[NS+d['id']]=d.get('default',{})
            states[NS+d['id']]=set(d['states'])
    return defaults,states


def rewrite_helper(tag,dim,source,mapping,registered):
    data=compound(tag)
    if data.get('id',Tag(TAG_STRING,'')).value!=PART:return False
    old=helper_bindings(data);new=[]
    for root,owner in old:
        state=source.get(dim,root)
        if state is None or state[0]!=owner:raise ValueError('source helper owner missing')
        target=mapping.get(state,state)
        if target[0] not in registered:raise ValueError('helper refers to unmapped retired root')
        new.append((root,target[0]))
    new=sorted(set(new))
    if old==new:return False
    data['Root']=Tag(TAG_LONG,block_pos_long(*new[0][0]));data['Owner']=Tag(TAG_STRING,new[0][1])
    if len(new)==1:data.pop('Owners',None)
    else:data['Owners']=Tag(TAG_LIST,[Tag(TAG_COMPOUND,{'Root':Tag(TAG_LONG,block_pos_long(*root)),'Owner':Tag(TAG_STRING,owner)}) for root,owner in new],TAG_COMPOUND)
    return True


def rewrite_chunk(chunk,source,mapping,registered,counts):
    for section in chunk.root().get('sections',Tag(TAG_LIST,[],TAG_COMPOUND)).value:
        bs=compound(section).get('block_states')
        if not bs:continue
        palette=compound(bs)['palette'].value
        raw=[state_of(t,source.defaults) for t in palette]
        if not any(s[0].startswith(NS) for s in raw):continue
        _,indices=section_blocks(section);uses=Counter(indices);changed=False
        new=list(palette)
        for i,old in enumerate(raw):
            if not uses[i] or not old[0].startswith(NS):continue
            target=mapping.get(old,old)
            key=','.join(k+'='+v for k,v in target[1])
            if target[0] not in registered or key not in registered[target[0]]:
                raise ValueError('unmapped obsolete city state: '+block_state_key(as_tag_state(old)))
            counts['checkedOwnCells']+=uses[i]
            if target!=old:
                new[i]=as_tag_state(target);counts['replacedCells']+=uses[i];changed=True
        if changed:
            sy=int(compound(section)['Y'].value)
            chunk.loaded[sy]=({block_state_key(t):i for i,t in enumerate(new)},new,np.asarray(indices,dtype=np.int32))
            chunk.changed=True
    for tag in chunk.entities()[1]:
        if rewrite_helper(tag,chunk.dimension,source,mapping,registered):
            chunk.changed=True;counts['retargetedHelpers']+=1
    # Own scheduled block ticks must follow the new root ID as well.
    for key in ('block_ticks','TileTicks'):
        for tag in chunk.root().get(key,Tag(TAG_LIST,[],TAG_COMPOUND)).value:
            d=compound(tag);ident=d.get('i',Tag(TAG_STRING,''))
            if not ident.value.startswith(NS):continue
            point=tuple(int(d[a].value) for a in ('x','y','z'))
            state=source.get(chunk.dimension,point);target=mapping.get(state,state)
            if target is None:raise ValueError('tick outside source chunk')
            if state[0]!=ident.value:
                raise ValueError('stale own scheduled tick does not match its source block')
            if ident.value!=target[0]:ident.value=target[0];chunk.changed=True;counts['retargetedTicks']+=1
    if chunk.changed:counts['changedChunks']+=1


def convert(source,output,mapping_path,old_city,new_city,progress,split_mapping=None):
    source,output,mapping_path,old_city,new_city,progress=map(lambda p:Path(p).resolve(),(source,output,mapping_path,old_city,new_city,progress))
    split_mapping=default_split_mapping(new_city,split_mapping)
    if source==output or source in output.parents or output in source.parents:raise ValueError('source and output must be separate')
    progress.parent.mkdir(parents=True,exist_ok=True)
    fingerprint={'sourceHashes':hash_tree(source),'mappingSha256':hashlib.sha256(mapping_path.read_bytes()).hexdigest(),
                 'splitMappingSha256':hashlib.sha256(split_mapping.read_bytes()).hexdigest() if split_mapping else None,
                 'registrySha256':hashlib.sha256((new_city/'definitions.json').read_bytes()).hexdigest(),
                 'oldDefaults':{str(p):hashlib.sha256((p/'definitions.json').read_bytes()).hexdigest() for p in (old_city,old_city.parent/'logical')},
                 'converterSha256':hashlib.sha256(Path(__file__).read_bytes()).hexdigest(), 'schemaVersion':2}
    if progress.exists():
        state=json.loads(progress.read_bytes())
        if state['fingerprint']!=fingerprint:raise ValueError('resume inputs changed')
        for rel,h in state['completed'].items():
            if hashlib.sha256((output/rel).read_bytes()).hexdigest()!=h:raise ValueError('checkpoint region changed')
    else:
        if output.exists():raise ValueError('existing output without checkpoint')
        output.parent.mkdir(parents=True,exist_ok=True);shutil.copytree(source,output)
        state={'fingerprint':fingerprint,'completed':{},'counts':{},'status':'converting'};atomic_json(progress,state)
    defaults,_=definitions(old_city);_,registered=definitions(new_city)
    mapping=combined_mapping(mapping_path,split_mapping);original=StreamingWorld(source,defaults,cache_limit=32)
    counts=Counter(state['counts'])
    for path in list_region_files(source):
        rel=path.relative_to(source).as_posix()
        if rel in state['completed']:continue
        region=RegionFile.open(path);dim=region_dimension(source,path);rx,rz=map(int,path.stem.split('.')[1:3])
        delta=Counter()
        for stored in list(region.chunks()):
            nbt=stored.nbt();chunk=Chunk(dim,rx*32+stored.x,rz*32+stored.z,path,region,stored.x,stored.z,stored.timestamp,nbt)
            rewrite_chunk(chunk,original,mapping,registered,delta)
            if chunk.changed:
                chunk.finish();region.set_chunk(stored.x,stored.z,nbt,timestamp=stored.timestamp)
            delta['chunks']+=1
        target=output/rel;temporary=target.with_suffix('.mca.tmp');region.save(temporary);os.replace(temporary,target)
        counts.update(delta);state['counts']=dict(counts)
        state['completed'][rel]=hashlib.sha256(target.read_bytes()).hexdigest();atomic_json(progress,state)
        print(f'{rel}: replaced {counts["replacedCells"]:,} cells; regions {len(state["completed"])}',flush=True)
    if hash_tree(source)!=fingerprint['sourceHashes']:raise ValueError('source changed during conversion')
    state['status']='complete';atomic_json(progress,state);return state


if __name__=='__main__':
    p=argparse.ArgumentParser();sub=p.add_subparsers(dest='command',required=True)
    e=sub.add_parser('extract-city');e.add_argument('source',type=Path);e.add_argument('output',type=Path)
    c=sub.add_parser('convert')
    for name in ('source','output'):c.add_argument(name,type=Path)
    for name in ('mapping','old-city','new-city','progress'):c.add_argument('--'+name,required=True,type=Path)
    c.add_argument('--split-mapping',type=Path)
    a=p.parse_args()
    result=extract_city(a.source,a.output) if a.command=='extract-city' else convert(a.source,a.output,a.mapping,a.old_city,a.new_city,a.progress,a.split_mapping)
    print(json.dumps(result.get('counts',result),ensure_ascii=False))
