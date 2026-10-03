"""Reassemble supported old city fragments in a NEW approval-gallery copy.

Only the manifest's city region files are opened for mutation. Collision
intersection is permitted through the existing shared-carrier NBT format;
unrelated visual roots, foreign blocks, entities and ticks remain protected.
"""
from __future__ import annotations
import argparse, copy, hashlib, json, os, shutil, tempfile
from collections import Counter, defaultdict, OrderedDict
from collections.abc import Mapping, MutableMapping
from dataclasses import replace
from pathlib import Path
import numpy as np
from convert_logical_world import (World, Chunk, Expected, Rule, Output, PART, add,
                                  as_tag_state, make_state, state_of, hash_tree, list_region_files, region_dimension)
from world_io import Tag, TAG_LIST, TAG_COMPOUND, TAG_INT, RegionFile, compound, section_blocks, block_state_key
from city_palette import audit_helpers
from convert_document_world import carrier_bindings, add_owner_binding, source_patterns

ROOT=Path(__file__).resolve().parents[1]
CITY=ROOT/'src/main/resources/bloodborne_blocks/city'

class ChunkCache(Mapping):
    """Bounded NBT cache; modified chunks are encoded before eviction."""
    def __init__(self,world,limit=128):
        self.world=world;self.limit=limit;self.rows={};self.cache=OrderedDict()
        for path in list_region_files(world.root):
            region=RegionFile.open(path);world.by_region[path]=region;dim=region_dimension(world.root,path)
            rx,rz=map(int,path.stem.split('.')[1:3])
            for stored in region.chunks():
                key=(dim,rx*32+stored.x,rz*32+stored.z)
                if key in self.rows:raise ValueError('duplicate chunk descriptor')
                self.rows[key]=(path,region,stored)
    def __len__(self):return len(self.rows)
    def __iter__(self):return iter(self.rows)
    def flush(self,chunk):
        if chunk.changed:
            chunk.finish();chunk.region.set_chunk(chunk.stored_x,chunk.stored_z,chunk.nbt,timestamp=chunk.timestamp)
            self.world.dirty_regions.add(chunk.region_path);chunk.changed=False
    def __getitem__(self,key):
        if key in self.cache:
            result=self.cache.pop(key);self.cache[key]=result;return result
        path,region,stored=self.rows[key]
        # A previously modified chunk must be read from the updated region entry.
        if path in self.world.dirty_regions:
            stored=region.get_chunk(stored.x,stored.z)
        nbt=stored.nbt();data=compound(nbt.root)
        if 'Sections' in data:raise ValueError('legacy Sections require explicit migration')
        if tuple(int(data.get(a,Tag(TAG_INT,key[i+1])).value) for i,a in enumerate(('xPos','zPos')))!=key[1:]:raise ValueError('chunk coordinate mismatch')
        result=Chunk(key[0],key[1],key[2],path,region,stored.x,stored.z,stored.timestamp,nbt)
        if len(self.cache)>=self.limit:
            _,old=self.cache.popitem(last=False);self.flush(old)
        self.cache[key]=result;return result

class EntityView(MutableMapping):
    """Resolve helper tags from the current cached chunk, never detached NBT."""
    def __init__(self,world):self.world=world
    def __getitem__(self,key):
        dim,x,y,z=key;chunk=self.world.chunk(dim,x,z)
        if chunk is None:raise KeyError(key)
        index=getattr(chunk,'_gallery_entity_index',None)
        if index is None:
            index={}
            for tag in chunk.entities()[1]:
                data=compound(tag)
                if not all(a in data for a in ('x','y','z')):continue
                point=tuple(int(data[a].value) for a in ('x','y','z'))
                if point in index:raise ValueError('duplicate block entity coordinate')
                index[point]=tag
            chunk._gallery_entity_index=index
        return index[(x,y,z)]
    def __setitem__(self,key,value):
        # add_helper already inserted into its chunk; verify the view resolves it.
        dim,x,y,z=key;chunk=self.world.chunk(dim,x,z);rows=chunk.entities()[1]
        if not rows or rows[-1] is not value:raise ValueError('detached block entity write')
        index=getattr(chunk,'_gallery_entity_index',None)
        if index is not None:
            if (x,y,z) in index:raise ValueError('duplicate helper insertion')
            index[(x,y,z)]=value
    def __delitem__(self,key):raise TypeError('entity deletion is not supported')
    def __iter__(self):
        for chunk in self.world.chunks.values():
            for tag in chunk.entities()[1]:
                data=compound(tag)
                if all(a in data for a in ('x','y','z')):yield (chunk.dimension,*(int(data[a].value) for a in ('x','y','z')))
    def __len__(self):return sum(1 for _ in self)

class StreamingWorld(World):
    def __init__(self,root,defaults,cache_limit=128):
        self.root=Path(root);self.defaults=defaults;self.by_region={};self.dirty_regions=set();self._dimension_heights={}
        self.chunks=ChunkCache(self,cache_limit)
    def block_entities(self):return EntityView(self)
    def save(self):
        for chunk in list(self.chunks.cache.values()):self.chunks.flush(chunk)
        for path in self.dirty_regions:self.by_region[path].save(path)

def parse_state(text):
    name,_,tail=text.partition('[')
    return name,tuple(sorted(tuple(p.split('=',1)) for p in tail.rstrip(']').split(',') if p))

def migration_map(city):
    return {parse_state(k):(v['id'],tuple(sorted(v['properties'].items())))
            for k,v in json.loads((city/'migration.json').read_bytes())['states'].items()}

def translate_expected(expected,mapping):
    return replace(expected,state=mapping.get(expected.state,expected.state))

def initially_eligible(world,dim,origin,rule,reserved=None):
    if rule.preflight_error:return False
    if reserved and any(reserved.get((dim,add(origin,p.offset)),rule.transaction_id)!=rule.transaction_id for p in (rule.source,)+rule.members):return False
    if any(world.get(dim,add(origin,p.offset))!=p.state for p in (rule.source,)+rule.members+rule.required_context):return False
    if rule.variant_guards:
        from source_variant_rng import guards_match
        if not guards_match(rule.variant_guards,origin):return False
    return True

def spatial_order(row):
    index,dim,origin=row
    return dim,origin[0]//16,origin[2]//16,index,origin

def select_owner_patterns(rules):
    groups=defaultdict(list)
    for rule in rules:
        groups[tuple(sorted((p.offset,p.state) for p in (rule.source,)+rule.members))].append(rule)
    selected=[];choices={}
    for signature,options in sorted(groups.items()):
        winner=min(options,key=lambda r:(len(r.shape),r.source_reference or '',r.target))
        selected.append(winner)
        if len({r.target for r in options})>1:
            choices[winner.source_reference]={'reason':'identical_fragments_different_owner_physics',
                'selection':'smallest occupied-cell footprint, then lexical original state',
                'chosen':winner.target[0],
                'alternatives':[{'id':r.target[0],'source':r.source_reference,'cells':len(r.shape)}
                                for r in sorted(options,key=lambda r:r.source_reference or '')]}
    return selected,choices

def compile_city_rules(city):
    from modded_world_adapter import compile_modded_rules
    from atomic_owner_groups import compile_groups
    from source_mapping_archive import archive
    mapping=migration_map(city)
    rules,_,diagnostics=compile_modded_rules(city.parent/'logical')
    rules,atomic=compile_groups(rules,city.parent/'logical')
    translated=[]
    for r in rules:
        translated.append(replace(r,source=translate_expected(r.source,mapping),
            members=tuple(translate_expected(p,mapping) for p in r.members),
            required_context=tuple(translate_expected(p,mapping) for p in r.required_context),
            components=None))
    # Whole owners reproduce the frozen forward fragments, including their
    # original orientation and pivot. No raw vanilla world state is replaced.
    frozen=archive(ROOT); migration=frozen['v2\\migration.json']
    defaults={'bloodborne_blocks:'+d['id']:d.get('default',{})
              for d in frozen['v2\\definitions.json']['blocks']}
    owners=json.loads((city/'owner-runtime-mappings.json').read_bytes())['states']
    gaps=[];whole_rules=[];single_cells=0
    for raw,target in owners.items():
        ident,props=parse_state(raw);entry=migration.get(ident.split(':')[1])
        key=','.join(k+'='+str(v) for k,v in sorted({**entry['default'],**dict(props)}.items())) if entry else ''
        fragments=entry['states'].get(key) if entry else None
        if not isinstance(fragments,list):
            gaps.append({'source':raw,'reason':'no_frozen_fragment_pattern'});continue
        pieces=tuple(Expected(tuple(f['offset']),mapping.get(make_state(f,defaults),make_state(f,defaults))) for f in fragments)
        # Consuming native or already assembled states is never an owner fallback.
        if not pieces or any(not p.state[0].startswith('bloodborne_blocks:city_') for p in pieces):
            gaps.append({'source':raw,'reason':'not_old_city_fragments'});continue
        if len({p.offset for p in pieces})!=len(pieces):
            gaps.append({'source':raw,'reason':'duplicate_fragment_coordinates'});continue
        if len(pieces)==1 and len(target['shape'])==1:
            single_cells+=1;continue # Ordinary single-cell masonry is already current.
        whole_rules.append(Rule(len(whole_rules)+100000,pieces[0],
            (target['id'],tuple(sorted(target['properties'].items()))),(0,0,0),pieces[1:],None,
            frozenset(map(tuple,target['shape'])),source_mode='modded',source_reference=raw))
    whole_rules,choices=select_owner_patterns(whole_rules);translated.extend(whole_rules)
    # Explicit final-document patterns precede generic historical owner fallback.
    geometry=json.loads((city/'geometry.json').read_bytes())
    definitions={d['id']:d for d in json.loads((city/'definitions.json').read_bytes())['blocks']}
    mapping_text=json.loads((city/'migration.json').read_bytes())['states']
    provisional={}
    for original in json.loads((city/'document-final.json').read_bytes())['objects']:
        recipe=copy.deepcopy(original)
        if recipe['components'] and not all(c['verified'] for c in recipe['components']):
            if recipe['item']==17:
                provisional[recipe['id']]={'reason':'caption_is_helper_not_independent_source; preserve working lamp family'}
                continue
            for c in recipe['components']:
                if not c['verified']:
                    actual=mapping_text.get(c['source_state'])
                    if actual:c['id']=actual['id'];c['properties']=actual['properties']
                    c['verified']=True
            provisional[recipe['id']]={'reason':'provisional final-document source choice',
                'selection':'actual frozen caption state where available; otherwise only authored variant 0 and orientation',
                'components':recipe['components']}
        for facing,members in source_patterns(recipe,mapping_text):
            props={**definitions[recipe['id']]['default'],'facing':facing,'root_anchor':'canonical'}
            if 'face' in props:props['face']='wall'
            key=','.join(k+'='+v for k,v in sorted(props.items()))
            cells=geometry['blocks'][recipe['id']]['states'][key]['cells']
            pieces=tuple(Expected(off,state) for off,state in members)
            if not pieces:continue
            outputs=[]
            if recipe['item']==7:
                cap='owner_final_07_cap';capcells=geometry['blocks'][cap]['states'][key]['cells']
                # The cap has its own upper storage root; shape remains in place.
                outputs=[Output(('bloodborne_blocks:'+recipe['id'],tuple(sorted(props.items()))),(0,0,0),frozenset(tuple(map(int,p.split(','))) for p in cells)),
                         Output(('bloodborne_blocks:'+cap,tuple(sorted({**props,'root_anchor':'upper_1'}.items()))),(0,1,0),frozenset(tuple(map(int,p.split(','))) for p in geometry['blocks'][cap]['states']['facing='+facing+',root_anchor=upper_1']['cells']))]
            translated.insert(0,Rule(200000+len(translated),pieces[0],('bloodborne_blocks:'+recipe['id'],tuple(sorted(props.items()))),
                (0,0,0),pieces[1:],None,frozenset(tuple(map(int,p.split(','))) for p in cells),
                outputs=tuple(outputs),source_mode='modded',source_reference='document-final:'+str(recipe['item'])))
    return translated,{'logical':diagnostics,'atomic':atomic,'wholeOwnerGaps':gaps,
                       'ownerChoices':choices,'documentChoices':provisional,'singleCellMappingsAlreadyCurrent':single_cells}

def find_candidates(world,rules,progress=None,resume=None):
    inverse=defaultdict(list)
    found={(int(i),dim,tuple(point)) for i,dim,point in (resume or {}).get('found',[])}
    cursor=int((resume or {}).get('cursor',0))
    for index,rule in enumerate(rules):
        if rule.allowed_origins:
            for dim,origin in rule.allowed_origins:
                if any(world.get(dim,add(origin,p.offset))==p.state for p in (rule.source,)+rule.members):found.add((index,dim,origin))
        else:inverse[rule.source.state].append((index,rule.source.offset))
    for chunk_index,chunk_key in enumerate(world.chunks):
        if chunk_index<cursor:continue
        chunk=world.chunks[chunk_key]
        for section in chunk.root().get('sections',Tag(TAG_LIST,[],TAG_COMPOUND)).value:
            if 'block_states' not in compound(section):continue
            palette=compound(compound(section)['block_states'])['palette'].value
            states=[state_of(p,{}) for p in palette]
            relevant=[i for i,s in enumerate(states) if s in inverse]
            if not relevant:continue
            _,indices=section_blocks(section)
            sy=int(compound(section)['Y'].value)*16;array=np.asarray(indices)
            for rawindex in np.flatnonzero(np.isin(array,relevant)):
                i=int(rawindex);point=(chunk.x*16+(i&15),sy+(i>>8),chunk.z*16+((i>>4)&15))
                for index,offset in inverse[states[int(array[i])]]:
                    origin=tuple(point[a]-offset[a] for a in range(3))
                    if rules[index].accepts_origin(chunk.dimension,origin):found.add((index,chunk.dimension,origin))
        if chunk_index%1000==0:print(f'scan chunks={chunk_index}/{len(world.chunks)} candidate patterns={len(found)}',flush=True)
        if progress and (chunk_index+1)%1000==0:progress(chunk_index+1,found)
    # Atomic closures, explicit document assemblies and larger logical groups
    # must be attempted before their individual historical owner fallbacks.
    return sorted(found,key=lambda row:(not rules[row[0]].atomic_owner_group,
        not (rules[row[0]].source_reference or '').startswith('document-final:'),
        rules[row[0]].number>=100000,-len(rules[row[0]].members),spatial_order(row)))

def apply_rule(world,entities,dim,origin,rule,definitions,geometry=None,reserved=None):
    pieces=(rule.source,)+rule.members
    if any(world.get(dim,add(origin,p.offset))!=p.state for p in pieces):return 'incomplete_source'
    if rule.preflight_error:return rule.preflight_error
    if any(world.get(dim,add(origin,p.offset))!=p.state for p in rule.required_context):return 'required_context_changed'
    if rule.variant_guards:
        from source_variant_rng import guards_match
        if not guards_match(rule.variant_guards,origin):return 'different_source_weighted_visual'
    source={add(origin,p.offset) for p in pieces}
    if reserved and any(reserved.get((dim,p),rule.transaction_id)!=rule.transaction_id for p in source):return 'reserved_by_atomic_owner_group'
    outputs=rule.outputs or (Output(rule.target,rule.root_offset,rule.shape),)
    plans=[];taken=set()
    for output in outputs:
        props=dict(output.target[1]);root=add(origin,output.root_offset);shape=output.shape
        ident=output.target[0].split(':')[-1]
        # Document owners have authored upper_N profiles. Logical owners use
        # canonical/upper and retain their already compiled root exception.
        if geometry and ident in geometry['blocks'] and ident.startswith('owner_final_') and 'root_anchor' in props:
            previous=0 if props['root_anchor']=='canonical' else int(props['root_anchor'].split('_')[1])
            base=add(root,(0,-previous,0))
            for height in range(9):
                candidate=add(base,(0,height,0));old=world.get(dim,candidate)
                if candidate in taken or old is None:continue
                if candidate not in source and old!=(PART,()) and not old[0].endswith(':air'):continue
                if reserved and reserved.get((dim,candidate),rule.transaction_id)!=rule.transaction_id:continue
                _,reason=carrier_bindings(entities,dim,candidate,old,definitions)
                if reason:continue
                trial={**props,'root_anchor':'canonical' if height==0 else 'upper_'+str(height)}
                key=','.join(k+'='+v for k,v in sorted(trial.items()))
                value=geometry['blocks'][ident]['states'].get(key)
                if not value:continue
                value=geometry.get('profiles',{}).get(value.get('ref'),value)
                root=candidate;shape=frozenset(tuple(map(int,p.split(','))) for p in value['cells']);props=trial;break
            else:return 'storage_root_occupied'
        taken.add(root);plans.append((root,(output.target[0],tuple(sorted(props.items()))),shape))
    roots={r for r,s,c in plans}
    if len(roots)!=len(plans):return 'shared_storage_root'
    footprint=source|{add(r,o) for r,s,c in plans for o in c}
    low,high=world.build_height(dim)
    bindings={}
    planned=defaultdict(set)
    for root,state,cells in plans:
        if (0,0,0) not in cells:return 'geometry_has_no_storage_root'
        definition=definitions.get(state[0].split(':')[-1])
        props=','.join(k+'='+v for k,v in state[1])
        if not definition or props not in definition['states']:return 'target_not_registered'
        for offset in cells:
            point=add(root,offset)
            if point!=root:planned[point].add((root,state[0]))
    for point in footprint:
        if not low<=point[1]<high:return 'outside_build_height'
        old=world.get(dim,point)
        if old is None:return 'outside_city_chunks'
        guests,reason=carrier_bindings(entities,dim,point,old,definitions)
        if reason:return reason
        bindings[point]=guests
        if len(set(guests)|planned.get(point,set()))>16:return 'shared_owner_limit'
        if point in source:
            definition=definitions.get(old[0].split(':')[-1],{})
            if definition.get('whole_owner') or definition.get('logical'):return 'source_is_existing_owner'
            if not old[0].startswith('bloodborne_blocks:'):return 'foreign_source'
        elif point in roots:
            if old!=(PART,()) and not old[0].endswith(':air'):return 'storage_root_occupied'
            if reserved and reserved.get((dim,point),rule.transaction_id)!=rule.transaction_id:return 'reserved_by_atomic_owner_group'
        elif old!=(PART,()) and not old[0].endswith(':air') and old[0].split(':')[-1] not in definitions:
            return 'foreign_block_carrier'
    if world.ticks_at(dim,footprint):return 'scheduled_ticks'
    for point in source:
        world.set(dim,point,(PART,()) if bindings[point] else ('minecraft:air',()))
    for root,state,cells in plans:world.set(dim,root,state)
    for root,state,cells in plans:
        for offset in cells:
            point=add(root,offset)
            if point==root:continue
            if point not in roots and world.get(dim,point)[0].endswith(':air'):world.set(dim,point,(PART,()))
            add_owner_binding(world,entities,dim,point,root,state[0])
    return None

def city_files(manifest):
    rows=manifest.get('cityContext')
    if not isinstance(rows,list) or not rows:raise ValueError('manifest lacks frozen cityContext file list')
    paths=[]
    for row in rows:
        p=Path(row['path'])
        if p.is_absolute() or '..' in p.parts or p.as_posix()!=row['path']:raise ValueError('unsafe city path')
        paths.append(p)
    if len(set(paths))!=len(paths):raise ValueError('duplicate city path')
    return paths

def upgrade(source,output,report_path,city=CITY):
    source,output,report_path,city=(Path(p).resolve() for p in (source,output,report_path,city))
    if output.exists() or source==output or source in output.parents or output in source.parents:raise ValueError('output must be new and outside input')
    if any(p==report_path or p in report_path.parents for p in (source,output,city,city.parent/'logical')):raise ValueError('report must be outside worlds and resources')
    original=hash_tree(source);manifest=json.loads((source/'gallery-manifest.json').read_bytes());paths=city_files(manifest)
    output.parent.mkdir(parents=True,exist_ok=True);stage=Path(tempfile.mkdtemp(prefix='gallery-city-',dir=output.parent))
    report={'schemaVersion':1,'status':'staging','converted':[],'rejected':[]}
    try:
        view=stage/'city';view.mkdir()
        shutil.copy2(source/'level.dat',view/'level.dat')
        for p in paths:
            if hashlib.sha256((source/p).read_bytes()).hexdigest()!=next(r['sha256'] for r in manifest['cityContext'] if r['path']==p.as_posix()):raise ValueError('city context source hash changed: '+str(p))
            (view/p).parent.mkdir(parents=True,exist_ok=True);shutil.copy2(source/p,view/p)
        definitions={d['id']:d for scope in (city,city.parent/'logical') for d in json.loads((scope/'definitions.json').read_bytes())['blocks']}
        rules,diagnostics=compile_city_rules(city)
        from atomic_owner_groups import reservations
        reserved=reservations(rules)
        geometry=json.loads((city/'geometry.json').read_bytes())
        # Frozen asset evidence is large and not needed while the city is open.
        from source_mapping_archive import archive
        archive.cache_clear()
        import gc
        gc.collect()
        print(f'compiled {len(rules)} rules; loading city',flush=True)
        world=StreamingWorld(view,{})
        print(f'loaded {len(world.chunks)} city chunks; scanning',flush=True)
        entities=world.block_entities();candidates=find_candidates(world,rules)
        print(f'city chunks={len(world.chunks)} rules={len(rules)} candidate patterns={len(candidates)}',flush=True)
        report['compiler']=diagnostics;report['candidatePatterns']=len(candidates)
        initially_complete=set()
        for index,dim,origin in sorted(candidates,key=spatial_order):
            rule=rules[index]
            if initially_eligible(world,dim,origin,rule,reserved):initially_complete.add((index,dim,origin))
        print(f'initially eligible patterns={len(initially_complete)}',flush=True)
        consumed={};report['overlappingChoices']=[];report['ownerChoices']=[];report['documentChoices']=[]
        for i,(index,dim,origin) in enumerate(candidates):
            if i and i%10000==0:
                world.save()
                print(f'checkpoint {i}/{len(candidates)} converted={len(report["converted"])}',flush=True)
            rule=rules[index];reason=apply_rule(world,entities,dim,origin,rule,definitions,geometry,reserved)
            row={'dimension':dim,'position':list(origin),'target':rule.target[0],'source':rule.source_reference,
                 'tp':f'/execute in {dim} run tp @s {origin[0]} {origin[1]+2} {origin[2]}'}
            if reason=='incomplete_source':
                if (index,dim,origin) in initially_complete:
                    affected=sorted({consumed[(dim,add(origin,p.offset))] for p in (rule.source,)+rule.members if (dim,add(origin,p.offset)) in consumed})
                    report['overlappingChoices'].append({**row,'reason':'incomplete_after_prior_conversion',
                        'chosenOperations':affected,'chosenTargets':[report['converted'][n]['target'] for n in affected]})
                continue
            if reason:report['rejected'].append({**row,'reason':reason})
            else:
                choice=diagnostics.get('ownerChoices',{}).get(rule.source_reference)
                if choice:report['ownerChoices'].append({**row,**choice})
                choice=diagnostics.get('documentChoices',{}).get(rule.target[0].split(':')[-1])
                if choice:report['documentChoices'].append({**row,**choice})
                for p in (rule.source,)+rule.members:consumed[(dim,add(origin,p.offset))]=len(report['converted'])
                report['converted'].append(row)
            if i%10000==0:print(f'processed {i}/{len(candidates)} converted={len(report["converted"])} rejected={len(report["rejected"])}',flush=True)
        # Earlier failures can have been superseded by a subsequent successful
        # assembly. Only still-complete sources are current pending replacements.
        passes=[]
        for pass_number in range(100):
            pending=[];partial=Counter();additional=0
            for index,dim,origin in sorted(candidates,key=spatial_order):
                rule=rules[index]
                if world.get(dim,add(origin,rule.source.offset))!=rule.source.state:continue
                if all(world.get(dim,add(origin,p.offset))==p.state for p in (rule.source,)+rule.members):
                    reason=apply_rule(world,entities,dim,origin,rule,definitions,geometry,reserved)
                    row={'dimension':dim,'position':list(origin),'target':rule.target[0],
                        'source':rule.source_reference,'tp':f'/execute in {dim} run tp @s {origin[0]} {origin[1]+2} {origin[2]}'}
                    if reason is None:
                        report['converted'].append(row);additional+=1
                        choice=diagnostics.get('ownerChoices',{}).get(rule.source_reference)
                        if choice:report['ownerChoices'].append({**row,**choice})
                        choice=diagnostics.get('documentChoices',{}).get(rule.target[0].split(':')[-1])
                        if choice:report['documentChoices'].append({**row,**choice})
                    elif reason!='different_source_weighted_visual':pending.append({**row,'reason':reason})
                else:partial[rule.target[0]]+=1
            passes.append(additional)
            print(f'follow-up pass {pass_number+1}: converted={additional} remaining complete blocked={len(pending)}',flush=True)
            if additional==0:break
        else:raise ValueError('migration did not reach a fixed point')
        report['followUpPasses']=passes
        report['rejected']=pending;report['partialAnchorPatterns']=dict(partial)
        world.save();del world,entities,consumed,initially_complete
        reread=StreamingWorld(view,{});audit=audit_helpers(reread,city);del reread
        if not audit['ok']:raise ValueError('saved helper audit failed: '+json.dumps(audit['orphans'][:5]))
        result=stage/'gallery';shutil.copytree(source,result)
        for p in paths:shutil.copy2(view/p,result/p)
        manifest['cityContextOriginal']=manifest['cityContext']
        manifest['cityContext']=[{'path':p.as_posix(),'sha256':hashlib.sha256((result/p).read_bytes()).hexdigest()} for p in paths]
        report['counts']={'converted':len(report['converted']),'rejected':len(report['rejected']),
                          'reasons':dict(Counter(r['reason'] for r in report['rejected']))}
        manifest['cityUpgrade']={'sourceGallery':source.name,'counts':report['counts'],'cityNowDiffersFromStandalone':True}
        manifest['cityCases']=report['rejected']+report['overlappingChoices']+report['ownerChoices']+report['documentChoices']
        (result/'gallery-manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
        review=['# Обновление города в галерее','','Город пересобран по доступным точным правилам; отдельная Main-City не изменена.',
                'Площадки и промежутки в 3 блока сохранены. Таблица ведёт к изменениям и оставшимся полным исходным группам.','',
                '| Статус | Объект | Причина | Переход |','|---|---|---|---|']
        review.extend(f'| {status} | {r["target"]} | {r.get("reason",r["source"])} | `{r["tp"]}` |'
                      for status,key in [('заменён','converted'),('сохранён для проверки','rejected'),
                                         ('пересекающийся шаблон — проверить','overlappingChoices')] for r in report[key])
        review.extend(f'| решение по документу — проверить | {r["target"]} | {r["reason"]} | `{r["tp"]}` |' for r in report['documentChoices'])
        specimen_tps={r['id']:r.get('tp','') for r in manifest.get('specimens',[])}
        review.extend(['','## Временный выбор между одинаковыми исходными деталями','',
                       '| Город | Выбранная модель | Варианты в галерее |','|---|---|---|'])
        for row in report['ownerChoices']:
            for alt in row['alternatives']:alt['galleryTp']=specimen_tps.get(alt['id'].split(':')[-1],'')
            review.append(f'| `{row["tp"]}` | {row["chosen"]} | '+ '; '.join(f'{a["id"]}: `{a["galleryTp"]}`' for a in row['alternatives'])+' |')
        (result/'City-Upgrade.md').write_text('\n'.join(review)+'\n',encoding='utf8')
        report['helpers']={'checked':audit['checked'],'orphans':len(audit['orphans'])}
        excluded=set(p.as_posix() for p in paths)|{'gallery-manifest.json','City-Upgrade.md'}
        report['unchangedGalleryFiles']=[p for p,h in original.items() if p not in excluded and hashlib.sha256((result/p).read_bytes()).hexdigest()==h]
        if any(hashlib.sha256((result/p).read_bytes()).hexdigest()!=h for p,h in original.items() if p not in excluded):raise ValueError('exhibit or metadata file changed')
        if original!=hash_tree(source):raise ValueError('source mutated')
        report['status']='complete';report_path.parent.mkdir(parents=True,exist_ok=True)
        report_path.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
        os.replace(result,output)
        return report
    finally:shutil.rmtree(stage,ignore_errors=True)

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('source',type=Path);p.add_argument('output',type=Path)
    p.add_argument('--report',type=Path,required=True);p.add_argument('--city',type=Path,default=CITY)
    a=p.parse_args();r=upgrade(a.source,a.output,a.report,a.city);print(json.dumps(r['counts']))
