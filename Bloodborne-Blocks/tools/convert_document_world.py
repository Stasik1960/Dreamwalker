"""Import explicit final-document assemblies into a copied compatible city.

Collision intersection is deliberately not a rejection here. Carrier capacity,
complete source membership, foreign NBT and distinct storage roots still are.
Every unresolved occurrence is retained in place and listed for the gallery.
"""
from __future__ import annotations
import argparse, copy, json, os, shutil, tempfile
from collections import Counter
from pathlib import Path
import numpy as np
from convert_logical_world import World, PART, add, state_of, as_tag_state, block_pos_long
from world_io import Tag, TAG_LIST, TAG_COMPOUND, TAG_LONG, TAG_STRING, compound, section_blocks, block_state_key
from city_palette import audit_helpers, helper_bindings

ROOT=Path(__file__).resolve().parents[1]
CITY=ROOT/'src/main/resources/bloodborne_blocks/city'


def props_key(props):return ','.join(k+'='+v for k,v in sorted(props.items()))


def expected(component):return (component['id'],tuple(sorted(component['properties'].items())))


def turn_offset(offset,n):
    x,y,z=offset;return [((x,z),(-z,x),(-x,-z),(z,-x))[n][0],y,((x,z),(-z,x),(-x,-z),(z,-x))[n][1]]


def source_patterns(recipe,mapping):
    result=[]
    components=recipe['components']
    if not components or not all(c['verified'] for c in components):return result
    directions=['north','east','south','west']
    for turn in range(4):
        members=[]
        for c in components:
            state=expected(c)
            if turn:
                text=c['source_state'];name,_,tail=text.partition('[')
                raw=dict(v.split('=',1) for v in tail.rstrip(']').split(',')) if tail else {}
                if raw.get('facing') not in directions:break
                raw['facing']=directions[(directions.index(raw['facing'])+turn)%4]
                key=name+'['+props_key(raw)+']';target=mapping.get(key)
                if target is None:break
                state=target['id'],tuple(sorted(target['properties'].items()))
            members.append((tuple(turn_offset(c['offset'],turn)),state))
        if len(members)==len(components):result.append((directions[turn],members))
    return result


def roots_for_state(world,state,offset):
    key=block_state_key(as_tag_state(state))
    for chunk in world.chunks.values():
        for section in chunk.root().get('sections',Tag(TAG_LIST,[],TAG_COMPOUND)).value:
            bs=compound(section).get('block_states')
            if not bs:continue
            palette=compound(bs)['palette'].value
            selected={i for i,t in enumerate(palette) if block_state_key(t)==key}
            if not selected:continue
            sy=int(compound(section)['Y'].value)*16
            cached=chunk.loaded.get(sy//16)
            if cached:
                palette,indices=cached[1:3]
                selected={i for i,t in enumerate(palette) if block_state_key(t)==key}
            else:_,indices=section_blocks(section)
            array=np.asarray(indices)
            for i in np.flatnonzero(np.isin(array,list(selected))):
                i=int(i);p=(chunk.x*16+(i&15),sy+(i>>8),chunk.z*16+((i>>4)&15))
                yield chunk.dimension,tuple(p[j]-offset[j] for j in range(3))


def carrier_bindings(entities,dimension,point,old,definitions):
    entity=entities.get((dimension,*point))
    if entity is None:return (None,'helper_without_binding') if old==(PART,()) else ([],None)
    data=compound(entity)
    if data.get('id',Tag(TAG_STRING,'')).value!=PART:return None,'foreign_block_entity'
    try:bindings=helper_bindings(data)
    except ValueError:return None,'helper_without_binding'
    supported=old==(PART,())
    if old[0].startswith('bloodborne_blocks:'):
        definition=definitions.get(old[0].split(':',1)[1],{})
        supported=supported or bool(definition.get('logical') or definition.get('whole_owner') or definition.get('city_compat'))
    return (bindings,None) if supported else (None,'entity_without_supported_carrier')


def choose_outputs(world,dimension,origin,recipe,definitions,geometry,source,entities):
    outputs=[]
    ids=[recipe['id']]+(['owner_final_07_cap'] if recipe['item']==7 else [])
    roots=set()
    for ident in ids:
        d=definitions[ident];props=dict(d['default']);props['facing']=recipe['_facing']
        if 'face' in props:props['face']='wall'
        for height in range(9):
            root=add(origin,(0,height,0))
            old=world.get(dimension,root)
            if root in roots:continue
            bindings,reason=carrier_bindings(entities,dimension,root,old,definitions)
            if reason:return None,reason
            # Guest bindings do not make an existing visual root disposable.
            if root not in source and (old is None or (old!=(PART,()) and not old[0].endswith(':air'))):continue
            if (dimension,*root) in entities and root in source:continue
            if len(bindings)>=16:return None,'shared_owner_limit'
            trial={**props,'root_anchor':'canonical' if height==0 else 'upper_'+str(height)}
            key=props_key(trial);g=geometry['blocks'][ident]['states'][key]
            cells={tuple(map(int,p.split(','))) for p in g['cells']}
            outputs.append((root,("bloodborne_blocks:"+ident,tuple(sorted(trial.items()))),cells))
            roots.add(root);break
        else:return None,'storage_root_unavailable'
    roots={p for p,s,c in outputs}
    planned={}
    for root,state,cells in outputs:
        for offset in cells:
            point=add(root,offset)
            low,high=world.build_height(dimension)
            if not low<=point[1]<high:return None,'outside_build_height'
            old=world.get(dimension,point)
            if old is None:return None,'outside_loaded_world'
            bindings,reason=carrier_bindings(entities,dimension,point,old,definitions)
            if reason:return None,reason
            if point in source or point in roots or old[0].endswith(':air'):continue
            if old==(PART,()):
                if len(bindings)>=16:return None,'shared_owner_limit'
            elif old[0].startswith('bloodborne_blocks:') and old[0].split(':')[1] in definitions:
                if bindings and len(bindings)>=16:return None,'shared_owner_limit'
            else:return None,'foreign_block_carrier'
            if offset!=(0,0,0):planned.setdefault(point,set()).add((root,state[0]))
    for root,state,cells in outputs:
        for offset in cells:
            if offset!=(0,0,0):planned.setdefault(add(root,offset),set()).add((root,state[0]))
    for point,owners in planned.items():
        old=world.get(dimension,point);bindings,reason=carrier_bindings(entities,dimension,point,old,definitions)
        if reason:return None,reason
        if len(set(bindings)|owners)>16:return None,'shared_owner_limit'
    return outputs,None


def add_owner_binding(world,entities,dimension,point,root,owner):
    """Attach one generated owner without replacing an imported guest carrier."""
    if point==root:return
    key=(dimension,*point);existing=entities.get(key)
    if existing is None:
        world.add_helper(dimension,point,root,owner)
        entities[key]=world.chunks[(dimension,point[0]//16,point[2]//16)].entities()[1][-1]
        return
    data=compound(existing)
    if data.get('id',Tag(TAG_STRING,'')).value!=PART:raise ValueError('foreign_block_entity')
    bindings=sorted(set(helper_bindings(data)+[(root,owner)]))
    if len(bindings)>16:raise ValueError('shared_owner_limit')
    first_root,first_owner=bindings[0]
    data['Root']=Tag(TAG_LONG,block_pos_long(*first_root));data['Owner']=Tag(TAG_STRING,first_owner)
    if len(bindings)==1:data.pop('Owners',None)
    else:data['Owners']=Tag(TAG_LIST,[Tag(TAG_COMPOUND,{'Root':Tag(TAG_LONG,block_pos_long(*entry_root)),'Owner':Tag(TAG_STRING,entry_owner)}) for entry_root,entry_owner in bindings],TAG_COMPOUND)
    world.chunks[(dimension,point[0]//16,point[2]//16)].changed=True


def convert(source,output,report_path,city=CITY):
    source,output,report_path,city=(Path(p).resolve() for p in (source,output,report_path,city))
    if output.exists() or source==output or source in output.parents:raise ValueError('output must be new and outside input')
    if any(report_path==p or p in report_path.parents for p in (source,output,city,city.parent/'logical')):raise ValueError('report must be outside worlds and migration resources')
    data=json.loads((city/'document-final.json').read_bytes());recipes=data['objects']
    defs={d['id']:d for d in json.loads((city/'definitions.json').read_bytes())['blocks']}
    defs.update({d['id']:d for d in json.loads((city.parent/'logical/definitions.json').read_bytes())['blocks']})
    geo=json.loads((city/'geometry.json').read_bytes())
    mapping=json.loads((city/'migration.json').read_bytes())['states']
    output.parent.mkdir(parents=True,exist_ok=True)
    stage=Path(tempfile.mkdtemp(prefix='document-import-',dir=output.parent));staging=stage/'world'
    report={'schemaVersion':1,'converted':[],'unresolved':[],'galleryChoices':[],'status':'staging'}
    try:
        shutil.copytree(source,staging)
        world=World(staging,{})
        entities=world.block_entities()
        for recipe in recipes:
            if not recipe['components']:continue
            report['galleryChoices'].extend({'item':recipe['item'],**choice} for choice in recipe['choices'])
            patterns=source_patterns(recipe,mapping)
            if not patterns:
                report['unresolved'].append({'item':recipe['item'],'position':recipe['origin'],'reason':'source_caption_variant_unproven'})
                continue
            for facing,members in patterns:
                # Materialize only matching caption anchor positions, never every
                # city cell. Completed earlier assemblies no longer match.
                candidates=list(roots_for_state(world,members[0][1],members[0][0]))
                for dimension,origin in candidates:
                    if any(world.get(dimension,add(origin,offset))!=state for offset,state in members):continue
                    source_cells={add(origin,offset) for offset,state in members}
                    row={'item':recipe['item'],'id':recipe['id'],'dimension':dimension,'position':list(origin),'facing':facing}
                    if any((dimension,*p) in entities for p in source_cells):
                        report['unresolved'].append({**row,'reason':'source_has_persistent_owner'});continue
                    if world.ticks_at(dimension,source_cells):
                        report['unresolved'].append({**row,'reason':'source_has_scheduled_ticks'});continue
                    recipe['_facing']=facing
                    outputs,reason=choose_outputs(world,dimension,origin,recipe,defs,geo,source_cells,entities)
                    if reason:
                        report['unresolved'].append({**row,'reason':reason});continue
                    footprint={add(root,offset) for root,state,cells in outputs for offset in cells}
                    if world.ticks_at(dimension,footprint):
                        report['unresolved'].append({**row,'reason':'target_has_scheduled_ticks'});continue
                    # Commit only after all outputs and guest carriers passed.
                    for p in source_cells:world.set(dimension,p,('minecraft:air',()))
                    roots={p for p,s,c in outputs}
                    for root,state,cells in outputs:world.set(dimension,root,state)
                    for root,state,cells in outputs:
                        for offset in cells:
                            if offset==(0,0,0):continue
                            p=add(root,offset)
                            if p not in roots and world.get(dimension,p)[0].endswith(':air'):world.set(dimension,p,(PART,()))
                            add_owner_binding(world,entities,dimension,p,root,state[0])
                    for root,state,cells in outputs:
                        for offset in cells:
                            p=add(root,offset);chunk=world.chunks[(dimension,p[0]//16,p[2]//16)]
                            for entity in chunk.entities()[1]:
                                data=compound(entity)
                                if tuple(int(data[a].value) for a in ('x','y','z'))==p:
                                    entities[(dimension,*p)]=entity;break
                    report['converted'].append({**row,'roots':[{'position':list(p),'state':block_state_key(as_tag_state(s))} for p,s,c in outputs]})
            print(f'document {recipe["item"]}: {len(report["converted"])} assemblies',flush=True)
        world.save()
        del world
        reread=World(staging,{})
        audit=audit_helpers(reread,city)
        report['helpers']=audit
        report['counts']={'converted':len(report['converted']),'unresolved':len(report['unresolved']),
                          'reasons':dict(Counter(r['reason'] for r in report['unresolved']))}
        if not audit['ok']:raise ValueError('saved helper audit failed: '+json.dumps(audit['orphans'][:5]))
        report['status']='complete'
        report_path.parent.mkdir(parents=True,exist_ok=True)
        report_path.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
        os.replace(staging,output)
        return report
    finally:shutil.rmtree(stage,ignore_errors=True)


if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('source',type=Path);p.add_argument('output',type=Path);p.add_argument('--report',type=Path,required=True);p.add_argument('--city',type=Path,default=CITY)
    a=p.parse_args();r=convert(a.source,a.output,a.report,a.city);print(json.dumps(r['counts']))
