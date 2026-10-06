"""Verified offline palette migration. Original ZIPs and unrelated NBT stay intact."""
import argparse,collections,hashlib,json,pathlib,zipfile
import numpy as np
import complete_world_io as w
ROOT=pathlib.Path(__file__).resolve().parents[1]
SOURCE_SHA='39e83f2941b768fd2ae9f84d8f57dc76aec78bd926dc5e38eab3059251d2bb08'
def digest(data):return hashlib.sha256(data).hexdigest()
def indices(fields,size):
    if size==1 and 'data' not in fields:return np.zeros(4096,dtype=np.int32)
    bits=max(4,(size-1).bit_length());per=64//bits
    words=np.asarray(fields['data'].value,dtype=np.int64).view(np.uint64)
    if len(words)!=(4096+per-1)//per:raise ValueError('Invalid block palette length')
    data=((words[:,None]>>(np.arange(per,dtype=np.uint64)*bits))&((1<<bits)-1)).reshape(-1)[:4096].astype(np.int32)
    if np.any(data>=size):raise ValueError('Invalid block palette index')
    return data

def edges(a,b,result):
    use=(a>0)&(b>0)&(a!=b)
    if not np.any(use):return
    x=a[use].astype(np.int64);y=b[use].astype(np.int64)
    keys=np.minimum(x,y)*100000+np.maximum(x,y)
    pairs,counts=np.unique(keys,return_counts=True)
    for pair,count in zip(pairs,counts):result[int(pair)]+=int(count)

def neighbors(array,key,cache,result):
    for axis in range(3):
        a=[slice(None)]*3;b=[slice(None)]*3;a[axis]=slice(None,-1);b[axis]=slice(1,None)
        edges(array[tuple(a)],array[tuple(b)],result)
    for axis,delta in [(0,(0,1,0)),(1,(0,0,1)),(2,(1,0,0))]:
        for sign in (-1,1):
            neighbor=tuple(v+sign*d for v,d in zip(key,delta))
            if neighbor in cache:edges(np.take(array,0 if sign<0 else -1,axis),cache[neighbor][axis,-sign],result)
    cache[key]={(a,s):np.take(array,0 if s<0 else -1,a).copy() for a in range(3) for s in (-1,1)}

def entities(tag,asset_ids,mob_ids,counts):
    if tag.type==w.TAG_LIST:
        for t in tag.value:entities(t,asset_ids,mob_ids,counts)
    elif tag.type==w.TAG_COMPOUND:
        fields=tag.value;source=fields.get('id')
        if source and source.type==w.TAG_STRING and source.value.startswith('bloodborne:'):
            suffix=source.value.split(':',1)[1]
            if suffix not in asset_ids:raise ValueError('Unknown original entity/item '+source.value)
            source.value='bloodborne_rp:'+suffix;counts[suffix]+=1
            if suffix in mob_ids:
                for k in ('Attributes','Health','ForgeCaps','Brain'):fields.pop(k,None)
                fields['bloodborne_rp_frozen']=w.Tag(w.TAG_BYTE,1)
            for old,new in [('IsOpen','Open'),('isOpen','Open'),('is_open','Open'),('IsLocked','Locked'),('isLocked','Locked')]:
                if old in fields and new not in fields:fields[new]=fields[old]
        for t in list(fields.values()):entities(t,asset_ids,mob_ids,counts)

def convert(source,catalog_path,output):
    if output.exists():raise ValueError('Refusing to overwrite '+str(output))
    if digest(source.read_bytes())!=SOURCE_SHA:raise ValueError('Unexpected v16 SHA-256')
    catalog=json.loads(catalog_path.read_text(encoding='utf-8'));mapping={b['source']:b['id'] for b in catalog['blocks']}
    assets=json.loads((ROOT/'src/main/resources/assets/bloodborne_rp/catalog.json').read_text(encoding='utf-8'))
    mob_ids={'huntsman_a','huntsman_b','huntsman_c','huntsman_d','huntsman_e','huntsman_f','huntsman_g','huntsman_h','huntsman_i','huntsman_j','scourge_beast','cleric_beast','blood_starved_beast','maneater_boar','carrion_crow','brick_troll','church_servant','church_giant'}
    report={'schema_version':1,'source_sha256':SOURCE_SHA,'catalog_sha256':digest(catalog_path.read_bytes()),'converted_cells':0,'terrain_changed_chunks':0,'entity_changed_chunks':0,'max_nonair_y':-64,'frequencies':{},'entities':{},'adjacency':{},'paths':{}}
    frequencies=collections.Counter();entity_counts=collections.Counter();adjacency=collections.Counter();cache={}
    output.parent.mkdir(parents=True,exist_ok=True);stage=output.with_suffix('.incomplete.zip')
    if stage.exists():raise ValueError('Staging already exists: '+str(stage))
    with zipfile.ZipFile(source) as inp,zipfile.ZipFile(stage,'w',zipfile.ZIP_DEFLATED,compresslevel=6) as out:
        bad=inp.testzip()
        if bad:raise ValueError('ZIP CRC failure '+bad)
        for info in inp.infolist():
            name=info.filename.replace('\\','/');path=pathlib.PurePosixPath(name)
            if path.is_absolute() or '..' in path.parts or any(':' in p for p in path.parts):raise ValueError('Unsafe ZIP path')
            data=inp.read(info);before=data
            if data and path.suffix=='.mca' and path.parent.name in ('region','entities'):
                reg=w.RegionFile(data);changed=0
                for c in reg.chunks():
                    nbt=c.nbt();r=w.compound(nbt.root);dirty=False
                    if path.parent.name=='region':
                        cx=r['xPos'].value;cz=r['zPos'].value
                        for st in r.get('sections',w.Tag(w.TAG_LIST,[],w.TAG_COMPOUND)).value:
                            sec=w.compound(st)
                            if 'block_states' not in sec:continue
                            bs=w.compound(sec['block_states']);pal=bs['palette'].value
                            ix=indices(bs,len(pal));names=[w.compound(e)['Name'].value for e in pal]
                            occupied=np.asarray([n not in ('minecraft:air','minecraft:cave_air','minecraft:void_air') for n in names])[ix]
                            if np.any(occupied):report['max_nonair_y']=max(report['max_nonair_y'],sec['Y'].value*16+int(np.flatnonzero(occupied)[-1])//256)
                            values=np.asarray([int(mapping.get(n,'0')) for n in names],dtype=np.int32)
                            if np.any(values[ix]):neighbors(values[ix].reshape(16,16,16),(cx,sec['Y'].value,cz),cache,adjacency)
                            amounts=np.bincount(ix,minlength=len(pal))
                            for i,t in enumerate(pal):
                                e=w.compound(t);old=names[i]
                                if old in mapping:
                                    e['Name']=w.Tag(w.TAG_STRING,'bloodborne_dw:'+mapping[old]);props=e.setdefault('Properties',w.Tag(w.TAG_COMPOUND,{}));w.compound(props)['visual']=w.Tag(w.TAG_STRING,'base')
                                    frequencies[mapping[old]]+=int(amounts[i]);report['converted_cells']+=int(amounts[i]);dirty=True
                                elif old=='bloodborne:hunter_lamp_light_source':
                                    e['Name']=w.Tag(w.TAG_STRING,'minecraft:light');e['Properties']=w.Tag(w.TAG_COMPOUND,{'level':w.Tag(w.TAG_STRING,'15'),'waterlogged':w.Tag(w.TAG_STRING,'false')});dirty=True
                                elif old.startswith('bloodborne:'):raise ValueError('Unknown old block '+old)
                    else:
                        original_nbt=w.encode_nbt(nbt);entities(nbt.root,set(assets),mob_ids,entity_counts);dirty=w.encode_nbt(nbt)!=original_nbt
                    if dirty:
                        if path.parent.name=='region':w.invalidate_chunk_lighting(r);report['terrain_changed_chunks']+=1
                        else:report['entity_changed_chunks']+=1
                        reg.set_chunk(c.x,c.z,nbt,timestamp=c.timestamp);changed+=1
                data=reg.to_bytes();print(json.dumps({'region':name,'changed_chunks':changed}),flush=True)
            elif path.name=='level.dat':
                nbt=w.decode_nbt(data,compressed='gzip');d=w.compound(w.compound(nbt.root)['Data']);d['LevelName']=w.Tag(w.TAG_STRING,'bloodborne-dw BBMC v16');data=w.encode_nbt(nbt,compressed='gzip')
            report['paths'][name]={'before':digest(before),'after':digest(data),'changed':before!=data}
            entry=zipfile.ZipInfo(name,(1980,1,1,0,0,0));entry.compress_type=zipfile.ZIP_DEFLATED;out.writestr(entry,data)
    report['frequencies']=dict(frequencies);report['entities']=dict(entity_counts);report['adjacency']={f'{p//100000:05d},{p%100000:05d}':c for p,c in sorted(adjacency.items())};report['output_sha256']=digest(stage.read_bytes())
    output.with_suffix('.conversion.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8');stage.rename(output)
    print(json.dumps({'output':str(output),'cells':report['converted_cells'],'max_y':report['max_nonair_y'],'adjacency_pairs':len(adjacency)}));return report

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('world',type=pathlib.Path);p.add_argument('--catalog',type=pathlib.Path,required=True);p.add_argument('--output',type=pathlib.Path,required=True);a=p.parse_args();convert(a.world,a.catalog,a.output)
if __name__=='__main__':main()
