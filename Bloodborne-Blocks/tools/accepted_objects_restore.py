"""Addressed RC1 bridge for accepted transactions; writes use the existing engine.

The source of ownership is the pinned, independently checked repair ledger, not
the RC1 carrier ID.  The RC1 archive is the exact expected intermediate state.
No historical reconstruction, palette fallback, or proximity matching occurs.
"""
from __future__ import annotations

import gzip
import hashlib
import json
import os
import subprocess
import tempfile
from collections import Counter, defaultdict
from pathlib import Path

from convert_logical_world import (AIR_NAME, PART, Candidate, Expected, Output,
    Rule, World, add, apply, as_tag_state, block_pos_long, copy_source,
    definition_hashes, full_id, hash_tree, parse_geometry, safe_extract, unpack_pos_long,
    validate_paths, write_report)
from world_io import (TAG_COMPOUND, TAG_INT, TAG_LIST, TAG_LONG, TAG_STRING,
    Tag, block_state_key, compound, section_blocks)

ROOT = Path(__file__).resolve().parents[1]
RC1_SHA = '749853aeb19ba8ea823bf1f683476985b74e2fce2746143eeacc3225eb0ed0a9'
MODDED_SHA = 'c517dfeb52c4d13bdbe90e02a93ac00416354eb89313a9d1377f24823af6d0e9'
ACCEPTED_SHA = 'f69063bec68ac19642582824b0f13f5e084daebd6da9e6c6d0c5eed3a8c7352e'
ACCEPTED = ROOT / 'docs/catalog-city-continuation/final/first.json.gz'
RC1 = ROOT / 'releases/Bloodborne-Blocks/2.1.0-rc.1/Bloodborne-City-2.1.0-rc.1.zip'
MODDED = ROOT / 'reference-inputs/latest-modded-world.zip'
RC1_COMMIT = '90a4e051a71ecc7b3156802c54dbfd8219789469'
AIR = (AIR_NAME, ())


def sha(path):
    with Path(path).open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def tree_sha(files):
    return hashlib.sha256(json.dumps(files,sort_keys=True,separators=(',',':')).encode()).hexdigest()


def state(text):
    name, _, tail = text.partition('[')
    return name, tuple(sorted(tuple(p.split('=', 1)) for p in tail.rstrip(']').split(',') if p))


def text_state(value):
    return block_state_key(as_tag_state(value)) if value else None


def helper_tag(point, helper):
    fields = {'id': Tag(TAG_STRING, PART), **{k: Tag(TAG_INT, v) for k, v in zip(('x','y','z'), point)},
              'Root': Tag(TAG_LONG, helper['Root']), 'Owner': Tag(TAG_STRING, helper['Owner'])}
    if 'Owners' in helper:
        fields['Owners'] = Tag(TAG_LIST, [Tag(TAG_COMPOUND, {
            'Root': Tag(TAG_LONG, row['Root']), 'Owner': Tag(TAG_STRING, row['Owner'])
        }) for row in helper['Owners']], TAG_COMPOUND)
    return Tag(TAG_COMPOUND, fields)


def bindings(tag):
    if tag is None:
        return []
    data = compound(tag)
    if (data.get('id') != Tag(TAG_STRING, PART) or
            set(data) - {'id','x','y','z','Root','Owner','Owners','keepPacked'}):
        raise ValueError('foreign_or_custom_block_entity')
    rows = data.get('Owners')
    if rows is not None and (rows.type != TAG_LIST or rows.list_type != TAG_COMPOUND or
                            any(row.type != TAG_COMPOUND for row in rows.value)):
        raise ValueError('invalid_helper_owners_list')
    values = [compound(row) for row in rows.value] if rows is not None else [data]
    result = []
    for value in values:
        if value.get('Root') is None or value['Root'].type != TAG_LONG or value.get('Owner') is None or value['Owner'].type != TAG_STRING:
            raise ValueError('invalid_helper_binding')
        result.append((str(value['Owner'].value), unpack_pos_long(value['Root'].value)))
    if not result or len(result) > 16 or len(result) != len(set(result)):
        raise ValueError('invalid_helper_binding_count')
    if 'Owners' in data and (data.get('Root'), data.get('Owner')) != (values[0]['Root'], values[0]['Owner']):
        raise ValueError('inconsistent_primary_helper_binding')
    return result


def load_shapes(resources):
    from logical_contract_v2 import load_contracts
    geometry = parse_geometry(resources.parent / 'city', {})
    contracts, _ = load_contracts(resources)
    for family in contracts['families']:
        for key, value in family['states'].items():
            signature = state(full_id(family['id']) + '[' + key + ']')
            geometry[signature] = {tuple(p) for p in value['physical_footprint']['cells']}
    return {key: frozenset(set(cells) | {(0,0,0)}) for key,cells in geometry.items()}


def logical_definitions(resources):
    accepted={row['id'] for row in json.loads((resources/'production-palette.json').read_bytes())['objects']}
    if tree_sha(sorted(accepted))!='992db851356b7a9d40404997e8d457d7fbacfcc7eb2aa88dfb6ac0213eaf082c':
        raise ValueError('accepted_production_palette_changed')
    return {full_id(row['id']):row for row in json.loads((resources/'definitions.json').read_bytes())['blocks']
            if row.get('logical') and row['id'] in accepted}


def rc1_shapes():
    """The published runtime's exact helper masks, before TEST3 integration."""
    def read(name):
        return json.loads(subprocess.check_output(['git','show',RC1_COMMIT+':Bloodborne-Blocks/src/main/resources/bloodborne_blocks/'+name],cwd=ROOT))
    physical=read('logical/physical-footprints.json')
    result={state(full_id(family)+'['+key+']'):frozenset(tuple(p) for p in spec['cells'])|{(0,0,0)}
            for family,states in physical['families'].items() for key,spec in states.items()}
    city=read('city/geometry.json')
    for family,block in city['blocks'].items():
        for key,spec in block['states'].items():
            profile=city['profiles'].get(spec.get('ref'),spec)
            result[state(full_id(family)+'['+key+']')]=frozenset(tuple(map(int,p.split(','))) for p in profile['cells'])|{(0,0,0)}
    return result


def accepted_groups(ledger, reference_entities=None):
    """Compose only transactions sharing a touched cell (including later passes)."""
    parent = list(range(len(ledger)))
    def find(i):
        while parent[i] != i:
            parent[i] = parent[parent[i]]
            i = parent[i]
        return i
    seen = {}
    for i, entry in enumerate(ledger):
        for change in entry['changes']:
            key = entry['dimension'], tuple(change['position'])
            if key in seen:
                a,b = find(i),find(seen[key])
                if a != b: parent[max(a,b)] = min(a,b)
            seen[key] = i
    # An RC1 helper can connect two already-approved transactions. Join their
    # entire owners instead of letting either half remove the other's binding.
    for (dim,*point),tag in (reference_entities or {}).items():
        try: owners=bindings(tag)
        except ValueError: continue
        indices=[seen[(dim,p)] for p in [tuple(point),*(root for _,root in owners)] if (dim,p) in seen]
        for i in indices[1:]:
            a,b=find(indices[0]),find(i)
            if a!=b:parent[max(a,b)]=min(a,b)
    groups = defaultdict(list)
    for i,entry in enumerate(ledger): groups[find(i)].append((i,entry))
    return list(groups.values())


def compile_group(number, entries, original, shapes, reference=None):
    seeds=[(i,e) for i,e in entries if e.get('rc1ExistingOwner')]
    if seeds:
        regular=[(i,e) for i,e in entries if not e.get('rc1ExistingOwner')]
        group=compile_group(number,regular,original,shapes,reference) if regular else {
            'number':number,'dimension':seeds[0][1]['dimension'],'original':{},'desired':{},'helpers':{},'outputs':()}
        roots={add(group['origin'],o.root_offset):o.target for o in group['outputs']}
        for _,e in seeds:
            row=e['rc1ExistingOwner'];p=tuple(row['root']);old=state(row['before']);target=state(row['state'])
            if e['dimension']!=group['dimension'] or reference.get(e['dimension'],p)!=old or p in group['desired']:
                raise ValueError('existing_rc1_owner_source_mismatch')
            group['original'][p]=old;group['desired'][p]=target;roots[p]=target
        origin=min(group['desired'])
        group.update(origin=origin,entries=[i for i,_ in entries],seedRoots={tuple(e['rc1ExistingOwner']['root']) for _,e in seeds},
            id='accepted-rc1-'+hashlib.sha256(','.join(str(i) for i,_ in entries).encode()).hexdigest()[:20],
            outputs=tuple(Output(v,tuple(p[i]-origin[i] for i in range(3)),shapes[v]) for p,v in sorted(roots.items())))
        return group
    dim = entries[0][1]['dimension']
    before, desired, helpers = {}, {}, {}
    for index,entry in entries:
        if entry['dimension'] != dim or entry.get('decision') != 'converted' or entry.get('conflictingCells'):
            raise ValueError('invalid_accepted_transaction')
        for change in entry['changes']:
            point = tuple(change['position'])
            old, new = state(change['before']), state(change['after'])
            actual = desired.get(point, original.get(dim, point))
            if actual != old:
                raise ValueError(f'accepted_source_mismatch: {index} {dim} {point} {text_state(actual)} != {change["before"]}')
            before.setdefault(point, old)
            desired[point] = new
            helpers.pop(point, None)
        for helper in entry['helpers']:
            point = tuple(helper['position'])
            if point not in desired: raise ValueError('accepted_helper_outside_transaction')
            helpers[point] = helper_tag(point, helper)
    origin = min(desired)
    outputs = []
    for point,value in desired.items():
        if value in (AIR, (PART, ())): continue
        if value not in shapes: raise ValueError(f'accepted_target_state_missing: {text_state(value)}')
        outputs.append(Output(value, tuple(point[i]-origin[i] for i in range(3)), shapes[value]))
    if not outputs: raise ValueError('accepted_transaction_has_no_owner')
    # Derive the whole desired state and helper topology from current contracts.
    occupancy = defaultdict(list)
    for output in outputs:
        root = add(origin,output.root_offset)
        for offset in output.shape: occupancy[add(root,offset)].append((output.target[0],root))
    for point,owners in occupancy.items():
        roots = [o for o in owners if o[1] == point]
        if len(roots) > 1 or len(owners) > 16: raise ValueError('accepted_ownership_collision')
        target = next((o.target for o in outputs if add(origin,o.root_offset)==point), (PART,()))
        if desired.get(point) != target: raise ValueError(f'accepted_contract_footprint_mismatch: {point}')
        guests = sorted((o for o in owners if o[1] != point), key=lambda o:(o[1],o[0]))
        expected = None
        if guests:
            expected = {'Root':block_pos_long(*guests[0][1]),'Owner':guests[0][0]}
            if len(guests)>1: expected['Owners']=[{'Root':block_pos_long(*p),'Owner':owner} for owner,p in guests]
            expected = helper_tag(point,expected)
        if helpers.get(point) != expected: raise ValueError(f'accepted_helper_contract_mismatch: {point}')
    if set(helpers)-set(occupancy) or any(v != AIR for p,v in desired.items() if p not in occupancy):
        raise ValueError('accepted_independent_fragment')
    digest=hashlib.sha256(','.join(str(index) for index,_ in entries).encode()).hexdigest()[:20]
    return {'id':'accepted-rc1-'+digest,'number':number,'entries':[i for i,_ in entries],
            'dimension':dim,'origin':origin,'original':before,'desired':desired,
            'helpers':helpers,'outputs':tuple(outputs)}


def expand_rc1_dependencies(group, reference, entities, owner_helpers, old_shapes, shapes, definitions, approved_cells):
    """Close exact existing RC1 owner links; never include a nearby cell by distance.

    A neighbour is retained at its exact root/state. Only already registered
    logical objects may be dependencies; compatibility fragments stay foreign.
    """
    dim,origin=group['dimension'],group['origin']
    base=set(group['desired']); points=set(base)
    roots={add(origin,o.root_offset):o.target for o in group['outputs']}
    seeds=group.get('seedRoots',set())
    for root in seeds:points.update(add(root,o) for o in shapes[roots[root]])
    dependencies={}; consumed={}; errors=[]; details=[]; scanned=set()
    def old_owner(root,owner):
        current=reference.get(dim,root)
        if not current or current[0]!=owner or current not in old_shapes:
            raise ValueError('unproven_rc1_helper_owner')
        expected_helpers={add(root,p) for p in old_shapes[current] if p!=(0,0,0)}
        actual_helpers=owner_helpers.get((dim,owner,root),set())
        if (actual_helpers-expected_helpers) or ((root not in base or root in seeds) and actual_helpers!=expected_helpers):
            details.append({'root':list(root),'state':text_state(current),
                            'missingHelpers':[list(p) for p in sorted(expected_helpers-actual_helpers)],
                            'extraHelpers':[list(p) for p in sorted(actual_helpers-expected_helpers)]})
            raise ValueError('incomplete_rc1_dependency_owner')
        for missing in expected_helpers-actual_helpers:
            if missing not in base and (dim,missing) not in approved_cells and entities.get((dim,*missing)) is not None:
                raise ValueError('foreign_or_custom_block_entity')
            if missing in base or reference.get(dim,missing)==AIR:continue
            # Missing helpers of a target may be empty. An occupied position
            # must instead be another independently declared accepted member.
            if (dim,missing) in approved_cells:
                points.add(missing);continue
            details.append({'root':list(root),'missingHelper':list(missing),
                            'actual':text_state(reference.get(dim,missing))})
            raise ValueError('foreign_cell_in_incomplete_rc1_target_mask')
        for helper in actual_helpers:
            delta=tuple(helper[i]-root[i] for i in range(3))
            if helper==root or delta not in old_shapes[current]:
                raise ValueError('unproven_rc1_helper_footprint')
            points.add(helper)
        consumed[root]=current
        if root not in base and root not in dependencies:
            definition=definitions.get(owner)
            normalized=(owner,tuple(sorted({**(definition or {}).get('default',{}),**dict(current[1])}.items())))
            if definition is None or normalized not in shapes:
                details.append({'root':list(root),'state':text_state(current),'normalizedState':text_state(normalized)})
                raise ValueError('nonlogical_foreign_owner_dependency')
            dependencies[root]=(current,normalized); roots[root]=normalized; points.add(root)
            points.update(add(root,p) for p in shapes[normalized])
    try:
        while points-scanned:
            point=min(points-scanned);scanned.add(point)
            current=reference.get(dim,point)
            if current is None:raise ValueError('dependency_chunk_missing')
            entity=entities.get((dim,*point))
            if entity is not None:
                for owner,root in bindings(entity):old_owner(root,owner)
            if current[0].startswith('bloodborne_blocks:o_'):
                # Preserve an existing logical root reached by a new helper.
                old_owner(point,current[0])
            if point not in base and current!=AIR and current!=(PART,()) and point not in dependencies:
                raise ValueError('foreign_block_in_dependency_footprint')
    except ValueError as error:errors.append(str(error))
    occupancy=defaultdict(list)
    for root,target in roots.items():
        for offset in shapes[target]:occupancy[add(root,offset)].append((target[0],root))
    desired={p:AIR for p in points}; helpers={}
    for point,owners in occupancy.items():
        if point not in points:
            errors.append('dependency_footprint_outside_preflight');continue
        root_owners=[o for o in owners if o[1]==point]
        if len(root_owners)>1 or len(owners)>16:errors.append('dependency_owner_collision');continue
        desired[point]=roots.get(point,(PART,()))
        guests=sorted((o for o in owners if o[1]!=point),key=lambda o:(o[1],o[0]))
        if guests:
            row={'Root':block_pos_long(*guests[0][1]),'Owner':guests[0][0]}
            if len(guests)>1:row['Owners']=[{'Root':block_pos_long(*p),'Owner':owner} for owner,p in guests]
            helpers[point]=helper_tag(point,row)
    # Accepted roots/artwork remain exact; only helper topology can gain a
    # separately proven guest from the input city's existing logical owner.
    for p,v in group['desired'].items():
        if v not in (AIR,(PART,())) and desired.get(p)!=v:errors.append('accepted_root_changed_by_dependency')
    group.update(desired=desired,helpers=helpers,
                 outputs=tuple(Output(v,tuple(p[i]-origin[i] for i in range(3)),shapes[v]) for p,v in sorted(roots.items())),
                 dependencies=[{'root':list(p),'before':text_state(v[0]),'state':text_state(v[1])} for p,v in sorted(dependencies.items())],
                 bridgeHelpers=[list(p) for p in sorted(points-base)],
                 preflightErrors=sorted(set(errors)),preflightDetails=details)
    return group


def rc1_owner_entries(reference, shapes, definitions, old_shapes, accepted):
    """Known existing logical owners with a complete old contract are additional
    accepted occurrences. Keep the exact root/art; no carrier inference occurs.
    Only seed roots outside the original accepted member set; overlapping owner
    graphs are subsequently composed as a whole.
    """
    import numpy as np
    protected={(e['dimension'],tuple(c['position'])) for e in accepted for c in e['changes']}
    found=[]
    for chunk in reference.chunks.values():
        for section in chunk.root().get('sections',Tag(TAG_LIST,[],TAG_COMPOUND)).value:
            raw=compound(section).get('block_states')
            if raw is None:continue
            palette=compound(raw)['palette'].value
            candidates=[]
            for index,tag in enumerate(palette):
                before=state(block_state_key(tag));definition=definitions.get(before[0])
                if definition is None:continue
                target=(before[0],tuple(sorted({**definition.get('default',{}),**dict(before[1])}.items())))
                if before not in old_shapes or target not in shapes:raise ValueError('unknown_existing_logical_state')
                if before!=target or old_shapes[before]!=shapes[target]:candidates.append((index,before,target))
            if not candidates:continue
            _,indices=section_blocks(section);indices=np.asarray(indices)
            sy=int(compound(section)['Y'].value)
            for index,before,target in candidates:
                for flat in np.flatnonzero(indices==index):
                    flat=int(flat);p=(chunk.x*16+(flat&15),sy*16+(flat>>8),chunk.z*16+((flat>>4)&15))
                    if (chunk.dimension,p) not in protected:found.append((chunk.dimension,p,before,target))
    entries=[]
    for dim,p,before,target in sorted(found):
        # A seed contains only its root. expand_rc1_dependencies validates its
        # full old mask and composes its new footprint with linked owners.
        entries.append({'dimension':dim,'decision':'converted','conflictingCells':[],
            'rc1ExistingOwner':{'root':list(p),'before':text_state(before),'state':text_state(target)},
            'changes':[{'position':list(p),'before':text_state(before),'after':text_state(target)}], 'helpers':[]})
    return entries


def closed_groups(ledger, original, reference, entities, owner_helpers, shapes, definitions):
    batches=accepted_groups(ledger,entities)
    old_shapes=rc1_shapes()
    approved_cells={(e['dimension'],tuple(c['position'])) for e in ledger for c in e['changes']}
    # A preserved dependency can meet another accepted transaction. Merge only
    # their exact declared footprints and repeat until no owner is split.
    while True:
        groups=[expand_rc1_dependencies(compile_group(i,entries,original,shapes,reference),reference,
                    entities,owner_helpers,old_shapes,shapes,definitions,approved_cells) for i,entries in enumerate(batches)]
        parent=list(range(len(groups)));seen={};joined=False
        def find(i):
            while parent[i]!=i:parent[i]=parent[parent[i]];i=parent[i]
            return i
        for i,g in enumerate(groups):
            for point in g['desired']:
                key=g['dimension'],point
                if key in seen:
                    a,b=find(i),find(seen[key])
                    if a!=b:parent[max(a,b)]=min(a,b);joined=True
                seen[key]=i
        if not joined:return groups
        merged=defaultdict(list)
        for i,batch in enumerate(batches):merged[find(i)].extend(batch)
        batches=[sorted(batch) for batch in merged.values()]


def same_world_cells(world, entities, dim, states, wanted_entities):
    return all(world.get(dim,p)==value and entities.get((dim,*p))==wanted_entities.get(p)
               for p,value in states.items())


def plan_group(group, reference, world, ref_entities, entities, owner_helpers, retired):
    dim,desired = group['dimension'], group['desired']
    points=set(desired)
    if group.get('preflightErrors'):return None,group['preflightErrors'][0]
    if any((dim,*p) in retired and (desired[p]!=reference.get(dim,p) or group['helpers'].get(p)!=ref_entities.get((dim,*p)))
           for p in points): return None,'retired_cell_intersection'
    if world.ticks_at(dim,points): return None,'scheduled_tick_at_target'
    if same_world_cells(world,entities,dim,desired,group['helpers']): return None,'already_correct'
    before={p:reference.get(dim,p) for p in points}
    expected_entities={p:ref_entities[(dim,*p)] for p in points if (dim,*p) in ref_entities}
    if not same_world_cells(world,entities,dim,before,expected_entities): return None,'rc1_cells_or_nbt_changed'
    if any(value is None for value in before.values()): return None,'missing_destination_chunk'
    for point,value in before.items():
        try:
            for owner,root in bindings(expected_entities.get(point)):
                if root not in points or before.get(root,(None,))[0] != owner:
                    return None,'foreign_helper_owner'
        except ValueError as error: return None,str(error)
        if any(p not in points for p in owner_helpers.get((dim,value[0],point),())):
            return None,'rc1_owner_extends_outside_transaction'
    origin=group['origin']
    pieces=tuple(Expected(tuple(p[i]-origin[i] for i in range(3)),before[p]) for p in sorted(points))
    outputs=group['outputs']
    rule=Rule(group['number'],pieces[0],outputs[0].target,outputs[0].root_offset,
              pieces[1:],None,outputs[0].shape,transaction_id=group['id'],outputs=outputs,
              source_mode='modded',source_reference='pinned accepted repair transaction -> pinned RC1 intermediate',
              allowed_origins=((dim,origin),),atomic_owner_group=True,shared_physics=True)
    writes={p:v for p,v in desired.items() if v!=AIR}
    candidate=Candidate(rule,'modded',dim,origin,points,add(origin,outputs[0].root_offset),writes,
                        tuple((add(origin,o.root_offset),o.target) for o in outputs))
    return candidate,'restore'


def restore(source, output, *, resources, report_path, report_root, dry_run=False,
            reference=RC1, modded=MODDED, accepted=ACCEPTED, progress=False,
            expected_source_tree_sha256=None):
    source,output,resources=(Path(p).resolve() for p in (source,output,resources))
    report_path=Path(report_path or ROOT/'build/accepted-restore/restore.json').resolve()
    report_root=Path(report_root or ROOT/'build').resolve()
    validate_paths(source,output,resources,report_path,report_root)
    if output.exists():raise ValueError('output must be a new directory outside the source')
    for path,digest in ((reference,RC1_SHA),(modded,MODDED_SHA),(accepted,ACCEPTED_SHA)):
        if sha(path)!=digest: raise ValueError('accepted bridge input SHA-256 mismatch: '+str(path))
    if source.is_file():
        if sha(source)!=RC1_SHA:raise ValueError('source must be the pinned RC1 ZIP')
    elif source.is_dir():
        if not expected_source_tree_sha256 or tree_sha(hash_tree(source))!=expected_source_tree_sha256:
            raise ValueError('a repeat run requires the exact previous output tree SHA-256')
    else:raise ValueError('missing input world')
    evidence=json.loads(gzip.decompress(Path(accepted).read_bytes()))
    if evidence['source']['hashes']['archive']!=MODDED_SHA or evidence['counts']['forced']!=0:
        raise ValueError('untrusted accepted ledger')
    shapes=load_shapes(resources)
    retired_json=json.loads((ROOT/'docs/city-compat/missing-model-positions.json').read_bytes())
    if tree_sha(retired_json)!='18fba494229775f0d240b1afc6f61762328274e4e311bbf6ee51de9409edf0a0':
        raise ValueError('retirement_manifest_hash_mismatch')
    # The existing retirement list is schema checked by its original writer.
    retired=set()
    def visit(value):
        if isinstance(value,dict):
            p=value.get('pos')
            if isinstance(p,list) and len(p)==3: retired.add((value.get('dimension','minecraft:overworld'),*p))
            for v in value.values(): visit(v)
        elif isinstance(value,list):
            for v in value:visit(v)
    visit(retired_json)
    if len(retired)!=33: raise ValueError('retirement_coordinate_manifest_mismatch')
    output.parent.mkdir(parents=True,exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='accepted-restore-',dir=output.parent) as temporary:
        stage=Path(temporary)
        current_dir,source_hashes,kind=copy_source(source,stage/'current')
        ref_dir=safe_extract(Path(reference),stage/'reference')
        old_dir=safe_extract(Path(modded),stage/'modded')
        if progress: print('Loading pinned RC1 and immutable source',flush=True)
        original=World(old_dir,{})
        reference_world=World(ref_dir,{})
        world=World(current_dir,{})
        ref_entities=reference_world.block_entities(); entities=world.block_entities()
        retired_states=[]
        for dim,x,y,z in sorted(retired):
            before=reference_world.get(dim,(x,y,z))
            if world.get(dim,(x,y,z))!=before or entities.get((dim,x,y,z))!=ref_entities.get((dim,x,y,z)):
                raise ValueError('retired_position_differs_from_published_rc1')
            retired_states.append({'dimension':dim,'position':[x,y,z],'rc1State':text_state(before)})
        owner_helpers=defaultdict(set)
        for (dim,*p),tag in ref_entities.items():
            if compound(tag).get('id')!=Tag(TAG_STRING,PART):continue
            try:
                for owner,root in bindings(tag): owner_helpers[(dim,owner,root)].add(tuple(p))
            except ValueError as error:raise ValueError('invalid RC1 helper: '+str((dim,p))) from error
        definitions=logical_definitions(resources)
        seeds=rc1_owner_entries(reference_world,shapes,definitions,rc1_shapes(),evidence['ledger'])
        groups=closed_groups(evidence['ledger']+seeds,original,reference_world,ref_entities,owner_helpers,shapes,definitions)
        del original
        if progress:print(f'Compiled {len(groups)} complete accepted groups',flush=True)
        candidates=[]; rows=[]; families=defaultdict(Counter)
        for group in groups:
            candidate,result=plan_group(group,reference_world,world,ref_entities,entities,owner_helpers,retired)
            if candidate: candidates.append(candidate)
            rows.append({'transaction':group['id'],'acceptedEntries':[i for i in group['entries'] if i<len(evidence['ledger'])],
                         'rc1OwnerEntries':[i-len(evidence['ledger']) for i in group['entries'] if i>=len(evidence['ledger'])],
                         'dimension':group['dimension'],'origin':list(group['origin']),
                         'result':result,'rc1Dependencies':group.get('dependencies',[]),
                         'bridgeHelperCells':group.get('bridgeHelpers',[]),
                         'preflightErrors':group.get('preflightErrors',[]),
                         'preflightDetails':group.get('preflightDetails',[]),
                         'roots':[{'position':list(add(group['origin'],o.root_offset)),
                         'state':text_state(o.target)} for o in group['outputs']]})
            for o in group['outputs']:
                family=o.target[0].removeprefix('bloodborne_blocks:')
                families[family]['found']+=1
                families[family]['restored' if result=='restore' else 'alreadyCorrect' if result=='already_correct' else 'conflicts']+=1
        conflicts=sum(row['result'] not in ('restore','already_correct') for row in rows)
        # Preflight the complete requested set before creating a result world.
        # The existing engine performs all writes and emits the ordinary ledger.
        ledger=[] if dry_run or conflicts else apply(world,candidates)
        report={'format':'bloodborne-accepted-rc1-restore-v1','sourceMode':'accepted-rc1',
                'dryRun':dry_run,'source':{'path':str(source),'kind':kind,'hashes':source_hashes},
                'inputs':{'rc1Sha256':RC1_SHA,'moddedSha256':MODDED_SHA,'acceptedLedgerSha256':ACCEPTED_SHA},
                'retiredPositionsPreserved':retired_states,
                'resources':{'definitionsSha256':definition_hashes(resources)},
                'counts':{'acceptedTransactions':len(evidence['ledger']),'existingRc1Owners':len(seeds),'groups':len(groups),
                          'restored':len(candidates),'alreadyCorrect':sum(row['result']=='already_correct' for row in rows),
                          'conflicts':conflicts},'families':dict(sorted(families.items())),
                'transactions':rows,'existingRc1Owners':[{'dimension':e['dimension'],**e['rc1ExistingOwner']} for e in seeds],'ledger':ledger,
                'scope':'All complete accepted continuation transactions plus known RC1 production owners with changed accepted masks; not unproven historical occurrences.',
                'phase':'precheck' if dry_run else 'blocked' if conflicts else 'applied',
                'result':'FAIL' if conflicts else 'PRECHECK_PASS' if dry_run else 'PASS'}
        if not dry_run and not conflicts:
            world.save()
            report['outputFiles']=hash_tree(current_dir)
            report['outputTreeSha256']=tree_sha(report['outputFiles'])
            os.replace(current_dir,output)
        write_report(report_path,report)
        if progress:print(json.dumps(report['counts']),flush=True)
    return report
