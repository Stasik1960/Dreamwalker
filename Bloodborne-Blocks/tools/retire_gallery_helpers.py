"""Retire only legacy helper bindings proven obsolete by a completed migration.

Never repairs arbitrary orphan data: the original helper binding, original root
state, exact rule member and recorded successful operation must all agree.
"""
from __future__ import annotations
import gc, hashlib, json, shutil, tempfile, os
from pathlib import Path
from collections import Counter
from resume_gallery_upgrade import Checkpoints, read_json, stable, candidate, finish, write_json
from upgrade_gallery_city import CITY,StreamingWorld,compile_city_rules,city_files,hash_tree,add
from convert_logical_world import PART,block_pos_long
from city_palette import helper_bindings,audit_helpers
from world_io import compound,Tag,TAG_LIST,TAG_COMPOUND,TAG_LONG,TAG_STRING


def frozen_city_view(source,progress,paths,expected):
    baseline=Path(progress)/'original-city'
    if not baseline.exists():
        staged=Path(tempfile.mkdtemp(prefix='original-city-preparing-',dir=progress))
        shutil.copy2(Path(source)/'level.dat',staged/'level.dat')
        for p in paths:(staged/p).parent.mkdir(parents=True,exist_ok=True);shutil.copy2(Path(source)/p,staged/p)
        if expected!=hash_tree(staged):raise ValueError('baseline evidence changed while copying')
        os.replace(staged,baseline)
    if expected!=hash_tree(baseline):raise ValueError('baseline evidence changed')
    return baseline


def repair_chunk(world,baseline,key,provenance):
    """Keep valid guest owners and visual carriers; remove only retired links."""
    chunk=world.chunks[key];entities=world.block_entities();old_entities=baseline.block_entities()
    points=[tuple(int(compound(t)[a].value) for a in ('x','y','z'))
            for t in chunk.entities()[1] if compound(t).get('id',Tag(TAG_STRING,'')).value==PART]
    counts=Counter();examples=[]
    for point in points:
        data=compound(entities[(key[0],*point)]);bindings=helper_bindings(data);retired=[]
        for root,owner in bindings:
            current=world.get(key[0],root)
            if current and current[0]==owner:continue
            expected=provenance.get((key[0],root),set());original_state=baseline.get(key[0],root)
            if original_state not in expected or original_state[0]!=owner:raise ValueError('unproven missing helper owner: '+str((key[0],point,root,owner)))
            original=old_entities.get((key[0],*point))
            if original is None or compound(original).get('id',Tag(TAG_STRING,'')).value!=PART or (root,owner) not in helper_bindings(compound(original)):
                raise ValueError('retired binding is absent from original world')
            retired.append((root,owner))
        if not retired:continue
        kept=[p for p in bindings if p not in retired]
        # Resolve again after cross-chunk reads; do not mutate detached cached NBT.
        current_chunk=world.chunk(key[0],point[0],point[2]);tag=entities[(key[0],*point)];data=compound(tag)
        if kept:
            data['Root']=Tag(TAG_LONG,block_pos_long(*kept[0][0]));data['Owner']=Tag(TAG_STRING,kept[0][1])
            if len(kept)==1:data.pop('Owners',None)
            else:data['Owners']=Tag(TAG_LIST,[Tag(TAG_COMPOUND,{'Root':Tag(TAG_LONG,block_pos_long(*root)),'Owner':Tag(TAG_STRING,owner)}) for root,owner in kept],TAG_COMPOUND)
            current_chunk.changed=True;counts['sharedCarriersKept']+=1
        else:
            if world.get(key[0],point)==(PART,()):
                if world.ticks_at(key[0],{point}):raise ValueError('retired empty helper has scheduled ticks')
                world.set(key[0],point,('minecraft:air',()));counts['emptyHelpersRemoved']+=1
            else:counts['visualCarriersKept']+=1
            world.remove_entities(key[0],{point})
            if hasattr(current_chunk,'_gallery_entity_index'):del current_chunk._gallery_entity_index
        counts['bindingsRemoved']+=len(retired);counts['helperEntitiesChanged']+=1
        if len(examples)<3:examples.append({'dimension':key[0],'position':list(point),'retired':[{'root':list(r),'owner':o} for r,o in retired]})
    return counts,examples


def finish_recorded(source,output,report_path,progress,city=CITY):
    source,output,report_path,progress,city=(Path(p).resolve() for p in (source,output,report_path,progress,city))
    checkpoints=Checkpoints(progress);state=checkpoints.load()
    if not state:raise ValueError('completed migration checkpoint required')
    if (state['source'],state['output'],state['reportPath'])!=(str(source),str(output),str(report_path)) or state['sourceHashes']!=hash_tree(source):raise ValueError('checkpoint inputs changed')
    if state['phase']=='complete':
        if state['outputHashes']!=hash_tree(output):raise ValueError('completed output changed')
        write_json(report_path,state['report']);return state['report']
    if state['phase'] not in ('audit','cleanup','cleanup_audit','cleanup_finalize'):raise ValueError('only a completed fixed-point migration can be finalized')
    cleanup_hash=hashlib.sha256(Path(__file__).read_bytes()).hexdigest()
    if state['phase'] in ('cleanup','cleanup_audit','cleanup_finalize') and state['report'].get('legacyHelperCleanup',{}).get('implementationSha256')!=cleanup_hash:
        raise ValueError('cleanup implementation changed since checkpoint')
    if not state['report']['followUpPasses'] or state['report']['followUpPasses'][-1]!=0:raise ValueError('migration has not reached a fixed point')
    if output.exists() and state['phase']!='cleanup_finalize':raise ValueError('final output already exists')
    rules,diagnostics=compile_city_rules(city)
    fingerprint=hashlib.sha256()
    for name in ('upgrade_gallery_city.py','resume_gallery_upgrade.py'):fingerprint.update(Path(__file__).with_name(name).read_bytes())
    for rule in rules:fingerprint.update(json.dumps(stable(rule),sort_keys=True,separators=(',',':')).encode())
    fingerprint.update(json.dumps(diagnostics,sort_keys=True,separators=(',',':')).encode())
    definitions={d['id']:d for scope in (city,city.parent/'logical') for d in read_json(scope/'definitions.json')['blocks']}
    for p in (city/'geometry.json',city.parent/'logical/geometry.json'):fingerprint.update(p.read_bytes())
    fingerprint.update(json.dumps(definitions,sort_keys=True).encode())
    if fingerprint.hexdigest()!=state['rulesHash']:raise ValueError('recorded migration engine or resources changed')
    provenance={}
    if len(state['convertedKeys'])!=len(state['report']['converted']):raise ValueError('conversion provenance count mismatch')
    for operation,raw in enumerate(state['convertedKeys']):
        index,dim,origin=candidate(raw);rule=rules[index];row=state['report']['converted'][operation]
        if (row['dimension'],tuple(row['position']),row['target'])!=(dim,origin,rule.target[0]):raise ValueError('conversion provenance row mismatch')
        for piece in (rule.source,)+rule.members:provenance.setdefault((dim,add(origin,piece.offset)),set()).add(piece.state)
    from source_mapping_archive import archive
    archive.cache_clear();del definitions,rules;gc.collect()
    baseline_view=progress/'original-city'
    manifest=read_json(source/'gallery-manifest.json');paths=city_files(manifest)
    if state['phase']=='cleanup_finalize' and output.exists():
        if any(hashlib.sha256((output/p).read_bytes()).hexdigest()!=state['snapshotHashes'][p.as_posix()] for p in paths):raise ValueError('uncommitted final city differs from verified snapshot')
        excluded={p.as_posix() for p in paths}|{'gallery-manifest.json','City-Upgrade.md'}
        if any(hashlib.sha256((output/p).read_bytes()).hexdigest()!=h for p,h in state['sourceHashes'].items() if p not in excluded):raise ValueError('uncommitted final output changed exhibits')
        report=state['report'];report['counts']={'converted':len(report['converted']),'rejected':len(report['rejected']),
            'reasons':dict(Counter(r['reason'] for r in report['rejected']))}
        if read_json(output/'gallery-manifest.json')['cityUpgrade']['counts']!=report['counts']:raise ValueError('uncommitted final metadata mismatch')
        specimen_tps={r['id']:r.get('tp','') for r in manifest.get('specimens',[])}
        for row in report['ownerChoices']:
            for alt in row['alternatives']:alt['galleryTp']=specimen_tps.get(alt['id'].split(':')[-1],'')
        report['unchangedGalleryFiles']=[p for p in state['sourceHashes'] if p not in excluded];report['status']='complete'
        state['phase']='complete';state['outputHashes']=hash_tree(output);write_json(report_path,report);checkpoints.save(state);return report
    expected={p.as_posix():state['sourceHashes'][p.as_posix()] for p in paths}
    expected['level.dat']=state['sourceHashes']['level.dat']
    baseline_view=frozen_city_view(source,progress,paths,expected)
    view=Path(tempfile.mkdtemp(prefix='helper-working-',dir=progress))
    shutil.copytree(progress/state['citySnapshot'],view,dirs_exist_ok=True)
    world=StreamingWorld(view,{});baseline=StreamingWorld(baseline_view,{})
    if state['phase']=='audit':
        state['phase']='cleanup';state['cursor']=0
        state['report']['legacyHelperCleanup']={'counts':{},'examples':[],'implementationSha256':cleanup_hash,
            'proof':'exact successful rule member + original root state + original helper binding'}
        checkpoints.save(state)
    if state['phase']=='cleanup':
        cleanup=state['report']['legacyHelperCleanup'];counts=Counter(cleanup['counts']);keys=list(world.chunks)
        for n in range(state['cursor'],len(keys)):
            changed,examples=repair_chunk(world,baseline,keys[n],provenance);counts.update(changed)
            if changed and len(cleanup['examples'])<20:cleanup['examples'].extend(examples[:20-len(cleanup['examples'])])
            state['cursor']=n+1
            if (n+1)%1000==0:
                cleanup['counts']=dict(counts);checkpoints.save(state,world)
                print('legacy helper cleanup',n+1,len(keys),dict(counts),flush=True)
        cleanup['counts']=dict(counts);state['phase']='cleanup_audit';state['cursor']=0;checkpoints.save(state,world)
    del world,baseline,provenance;gc.collect()
    if state['phase']=='cleanup_audit':
        checked=audit_helpers(StreamingWorld(view,{}),city)
        if not checked['ok']:raise ValueError('cleaned saved helper audit failed: '+json.dumps(checked['orphans'][:5]))
        state['report']['helpers']={'checked':checked['checked'],'orphans':0};state['phase']='cleanup_finalize';checkpoints.save(state)
    if state['phase']=='cleanup_finalize':
        result=finish(source,output,report_path,manifest,paths,state,view,city);checkpoints.save(state);return result
    raise ValueError('unknown completion phase')
