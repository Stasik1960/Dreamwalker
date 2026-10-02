"""Read-only streaming comparison of native blocks/NBT and saved owner states."""
from __future__ import annotations
import argparse,json
from collections import Counter
from pathlib import Path
import numpy as np
from world_io import RegionFile,Tag,TAG_INT,TAG_LIST,TAG_COMPOUND,compound,section_blocks,block_state_key
from city_palette import audit_helpers
from convert_logical_world import World,PART
ROOT=Path(__file__).resolve().parents[1]
CITY=ROOT/'src/main/resources/bloodborne_blocks/city'
NS='bloodborne_blocks:'
AIR={'minecraft:air','minecraft:cave_air','minecraft:void_air'}


def registered_states(city):
    result={PART:{''}}
    for scope in (city,city.parent/'logical'):
        for row in json.loads((scope/'definitions.json').read_bytes())['blocks']:
            result[NS+row['id']]=set(row['states'])
    return result


def region_paths(world):
    return {p.relative_to(world).as_posix():p for p in world.glob('**/region/r.*.*.mca')}


def run(source,target,report,city=CITY):
    source,target,report,city=(Path(p).resolve() for p in (source,target,report,city))
    if not source.is_dir() or not target.is_dir():raise ValueError('source and target worlds must exist')
    if any(report==p or p in report.parents for p in (source,target,city,city.parent/'logical')):raise ValueError('report must be outside worlds and registry resources')
    sr,tr=region_paths(source),region_paths(target)
    states=registered_states(city);errors=[];error_count=0
    stats={'regions':len(sr),'chunks':0,'foreignBlocks':0,'yuushyaBlocks':0,'foreignBlockEntities':0,'ownBlocks':0}
    def error(kind,**extra):
        nonlocal error_count
        error_count+=1
        if len(errors)<200:errors.append({'kind':kind,**extra})
    def read_chunks(path,relative,side):
        rx,rz=map(int,path.stem.split('.')[1:3]);result={}
        for stored in RegionFile.open(path).chunks():
            root=compound(stored.nbt().root);expected=(rx*32+stored.x,rz*32+stored.z)
            actual=tuple(int(root.get(k,Tag(TAG_INT,expected[i])).value) for i,k in enumerate(('xPos','zPos')))
            if actual!=expected:error('chunk_coordinate_mismatch',region=relative,side=side,expected=expected,actual=actual)
            result[expected]=root
        return result
    def foreign_entities(root,relative,side):
        result={};seen=set()
        for tag in root.get('block_entities',Tag(TAG_LIST,[],TAG_COMPOUND)).value:
            data=compound(tag);point=tuple(int(data[a].value) for a in ('x','y','z'))
            if point in seen:error('duplicate_block_entity_coordinate',region=relative,side=side,position=point)
            seen.add(point)
            if not data['id'].value.startswith(NS):result[point]=tag
        return result
    def sections(root):
        return {int(compound(s)['Y'].value):s for s in root.get('sections',Tag(TAG_LIST,[],TAG_COMPOUND)).value if 'block_states' in compound(s)}
    if set(sr)!=set(tr):error('region_set_mismatch',sourceOnly=sorted(set(sr)-set(tr)),targetOnly=sorted(set(tr)-set(sr)))
    for rel in sorted(set(sr)&set(tr)):
        a,b=read_chunks(sr[rel],rel,'source'),read_chunks(tr[rel],rel,'target')
        if set(a)!=set(b):error('chunk_set_mismatch',region=rel)
        for coords in sorted(set(a)&set(b)):
            stats['chunks']+=1
            ae,be=foreign_entities(a[coords],rel,'source'),foreign_entities(b[coords],rel,'target')
            stats['foreignBlockEntities']+=len(ae)
            for pos in set(ae)|set(be):
                if ae.get(pos)!=be.get(pos):error('foreign_block_entity_changed',region=rel,position=pos)
            sa,sb=sections(a[coords]),sections(b[coords])
            for sy in set(sa)|set(sb):
                names={};foreign_names=['minecraft:air']
                def read(section,target_side):
                    if section is None:return np.zeros(4096,dtype=np.int32)
                    palette,indices=section_blocks(section);counts=Counter(indices);remap=[]
                    for index,entry in enumerate(palette):
                        data=compound(entry);ident=data['Name'].value;key=block_state_key(entry)
                        if target_side and counts[index] and ident.startswith(NS):
                            stats['ownBlocks']+=counts[index]
                            prop=','.join(k+'='+v.value for k,v in sorted(compound(data.get('Properties',Tag(TAG_COMPOUND,{}))).items()))
                            if ident not in states or prop not in states[ident]:error('invalid_own_state',region=rel,chunk=coords,section=sy,state=key)
                        if ident.startswith(NS) or ident in AIR:remap.append(0)
                        else:
                            if key not in names:names[key]=len(foreign_names);foreign_names.append(key)
                            remap.append(names[key])
                            if not target_side:
                                stats['foreignBlocks']+=counts[index]
                                if ident.startswith('yuushya:'):stats['yuushyaBlocks']+=counts[index]
                    return np.asarray(remap,dtype=np.int32)[np.asarray(indices,dtype=np.int32)]
                av,bv=read(sa.get(sy),False),read(sb.get(sy),True)
                differing=np.flatnonzero(av!=bv)
                for index in differing:
                    index=int(index);position=(coords[0]*16+(index&15),sy*16+(index>>8),coords[1]*16+((index>>4)&15))
                    error('foreign_block_changed',region=rel,position=position,source=foreign_names[int(av[index])],target=foreign_names[int(bv[index])])
        print(f'verified {rel}: {stats["chunks"]} chunks',flush=True)
    # Strict owner membership checks on saved Anvil data, including shared carriers.
    world=World(target,{})
    helpers=audit_helpers(world,city)
    for row in helpers['orphans']:error('invalid_helper',**row)
    result={'schemaVersion':3,'readOnly':True,'passed':error_count==0,'stats':stats,'helpers':{'checked':helpers['checked'],'orphans':len(helpers['orphans'])},'errorCount':error_count,'errors':errors}
    report.parent.mkdir(parents=True,exist_ok=True)
    report.write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    return result


if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('source',type=Path);p.add_argument('target',type=Path);p.add_argument('--report',type=Path,required=True)
    a=p.parse_args();r=run(a.source,a.target,a.report);print(json.dumps({'passed':r['passed'],**r['stats'],'errors':r['errorCount']}));raise SystemExit(0 if r['passed'] else 1)
