"""Restore authored positions from relocation ledgers, never relocate to a free cell.

Only a NEW copy is written. Run after remove_world_barriers.py. Registry, models,
player data and the supplied original world are not modified.
"""
import argparse
import collections
import copy
import hashlib
import json
import shutil
from pathlib import Path
from audit_modular_interactions import geometry_state, reserved_door_sibling, scan_roots
from relocate_modular_conflicts import WorldEditor, parse_state, props_of, pack_block_pos, unpack_block_pos
from convert_modular_world import AIR, tag_state, RES, ROOT
from world_io import *

STAGES = ['world-relocation-v2', 'world-relocation-extra-v2', 'world-relocation-final-v2',
          'world-static-relocation-v2', 'world-functional-repair-v2']
HELPER = 'bloodborne_blocks:architecture_part'


def sha(path):
    with path.open('rb') as f:
        return hashlib.file_digest(f, 'sha256').hexdigest()


def entry(text):
    name, properties = parse_state(text)
    return AIR if name == 'minecraft:barrier' else tag_state(name, properties)


def helper(pos, root, owner):
    return Tag(TAG_COMPOUND, {'id':Tag(TAG_STRING, HELPER),
        **{k:Tag(TAG_INT, v) for k,v in zip(('x','y','z'),pos)},
        'Root':Tag(TAG_LONG, pack_block_pos(root)), 'Owner':Tag(TAG_STRING, owner)})


def restore(source, original, target, report):
    source, original, target = source.resolve(), original.resolve(), target.resolve()
    if not (source/'level.dat').exists() or not (original/'level.dat').exists():
        raise ValueError('Two source worlds and a separate NEW target are required')
    if any(p in target.parents or target in p.parents for p in (source,original)):
        raise ValueError('World folders must be separate')
    before = {p.relative_to(source):sha(p) for p in source.rglob('*') if p.is_file()}
    if target.exists():
        # A failed preflight never commits. Reuse only a byte-identical scratch
        # copy; a changed or unrelated existing world is always rejected.
        present={p.relative_to(target):sha(p) for p in target.rglob('*') if p.is_file()}
        if present!=before:
            raise ValueError('Existing target is not an unchanged source copy')
    else:
        shutil.copytree(source, target)
    world = WorldEditor(target)
    stats = collections.Counter()
    ledgers = [json.loads((ROOT/'docs'/f'{name}.json').read_text(encoding='utf-8')) for name in STAGES]
    for name, d in reversed(list(zip(STAGES,ledgers))):
        assert d['applied'], name
        changes = {tuple(r['position']):r for r in d['changes']}
        if name in STAGES[-2:]:
            selected = set(changes)
        else:
            # Preserve later cell-local architecture. Undo movement cells and
            # synthetic helpers, not unrelated fills added in the same batch.
            selected = set()
            sources = set()
            for obj in d['movedObjects']:
                for cell in obj['cells']:
                    selected.update((tuple(cell['oldPosition']),tuple(cell['newPosition'])))
                    sources.add(tuple(cell['oldPosition']))
                    actual = world.state(tuple(cell['newPosition']))
                    assert block_state_key(actual) == block_state_key(entry(cell['state'])), (name,cell)
                selected.update(tuple(h['position']) for h in obj.get('syntheticBlockEntities',[]))
            selected.intersection_update(changes)
        # Validate the complete patch before applying any part of the stage.
        skip=set()
        for pos in selected:
            actual = world.state(pos)
            if actual is not None and block_state_key(actual)==block_state_key(entry(changes[pos]['newState'])):
                continue
            if name in STAGES[:3] and actual is not None and compound(actual)['Name'].value.startswith('bloodborne_blocks:m_'):
                if pos in sources and changes[pos]['oldState']!=HELPER:
                    # Later composition filled the evacuated root cell. The
                    # authored object must regain its original cell here.
                    stats['later_root_infill_replaced']+=1
                    continue
                if changes[pos]['newState']==HELPER or changes[pos]['oldState']==HELPER:
                    skip.add(pos)
                    continue
            raise AssertionError((name,pos,None if actual is None else block_state_key(actual),changes[pos]))
        selected-=skip
        for pos in selected:
            world.set_state(pos, entry(changes[pos]['oldState']))
        stats['reversed_movement_events'] += len(d['movedObjects'])
        stats['inverse_patch_cells'] += len(selected)
        print('Reversed', name, len(selected), 'cells', flush=True)

    # Block-entity payloads are preserved; reverse coordinate-only moves in the
    # same batch order as the original writer, and verify against source NBT.
    original_entities = {}
    for path in sorted(original.glob('region/r.*.*.mca')):
        for stored in RegionFile.open(path).chunks():
            for be in compound(stored.nbt().root).get('block_entities',Tag(TAG_LIST,[],TAG_COMPOUND)).value:
                bd = compound(be)
                if bd['id'].value != HELPER:
                    original_entities[tuple(bd[k].value for k in ('x','y','z'))] = be
    actual_entities = []
    helper_positions = set()
    for path in sorted(source.glob('region/r.*.*.mca')):
        for stored in RegionFile.open(path).chunks():
            root = compound(stored.nbt().root)
            for be in root.get('block_entities',Tag(TAG_LIST,[],TAG_COMPOUND)).value:
                bd = compound(be);pos = tuple(bd[k].value for k in ('x','y','z'))
                if bd['id'].value == HELPER:
                    helper_positions.add(pos)
                else:
                    actual_entities.append((pos,copy.deepcopy(be)))
    restored_entities = {}
    for pos, be in actual_entities:
        oldpos = pos
        for d in reversed(ledgers):
            reverse = {tuple(r['to']):tuple(r['from']) for r in d.get('entityMoves',[])}
            oldpos = reverse.get(oldpos,oldpos)
        for k,v in zip(('x','y','z'),oldpos):
            compound(be)[k].value = v
        assert oldpos not in restored_entities, ('duplicate entity',oldpos)
        assert be == original_entities.get(oldpos), ('foreign entity payload mismatch',oldpos)
        restored_entities[oldpos] = be
        stats['block_entities_restored'] += oldpos != pos
        world.remove_entities(pos)
    assert set(restored_entities) == set(original_entities)
    for pos,be in restored_entities.items():
        world.remove_entities(pos)
        world.add_entity(pos,be)

    # Clear obsolete invisible links first. Include helpers restored by inverse
    # patches (their old BEs no longer exist) as well as those in the current map.
    for d in ledgers:
        for row in d['changes']:
            if row['oldState'] == HELPER or row['newState'] == HELPER:
                helper_positions.add(tuple(row['position']))
    for pos in helper_positions:
        for be in world.entities_at(pos):
            if compound(be)['id'].value != HELPER:
                break
        else:
            world.remove_entities(pos)
        state = world.state(pos)
        if state is not None and compound(state)['Name'].value == HELPER:
            world.set_state(pos,AIR)
            stats['old_helper_cells_cleared'] += 1

    definitions = {b['id']:b for b in json.loads((RES/'bloodborne_blocks/definitions.json').read_text())['blocks']}
    geometry = json.loads((RES/'bloodborne_blocks/geometry.json').read_text())
    # Only the touched chunk cache differs from source. Scan source roots, then
    # merge the cache so the audit/ownership pass sees the planned map exactly.
    roots = {pos:(ident,entry_) for pos,ident,entry_ in scan_roots(source,definitions)}
    for pos in list(roots):
        state = world.state(pos)
        ident = compound(state)['Name'].value.removeprefix('bloodborne_blocks:')
        if ident not in definitions or ident == 'architecture_part':
            del roots[pos]
        else:
            roots[pos]=(ident,state)
    for d in ledgers:
        for row in d['changes']:
            pos = tuple(row['position']);state = world.state(pos)
            ident = compound(state)['Name'].value.removeprefix('bloodborne_blocks:')
            if ident in definitions and ident != 'architecture_part':
                roots[pos]=(ident,state)

    original_world = WorldEditor(original)
    mismatch=[]
    for pos,(ident,state) in roots.items():
        original_state = original_world.state(pos)
        if original_state is None or block_state_key(original_state) != block_state_key(state):
            mismatch.append((pos,block_state_key(state),None if original_state is None else block_state_key(original_state)))
    assert not mismatch, ('restored root differs from original',len(mismatch),mismatch[:8])
    stats['legacy_roots_at_exact_source_state_and_position'] = len(roots)
    requests=collections.defaultdict(list)
    occupied=collections.Counter()
    for pos,(ident,state) in roots.items():
        values={**definitions[ident]['default'],**props_of(state)}
        authored,_=geometry_state(geometry,ident,values)
        # Exact state equality above includes assembled=true objects that were
        # already present in the source. Their original offset is retained too.
        for key,cell in authored.get('cells',{}).items():
            offset=tuple(map(int,key.split(',')))
            if offset==(0,0,0) or reserved_door_sibling(definitions[ident],values,offset):
                continue
            at=tuple(pos[i]+offset[i] for i in range(3))
            there=world.state(at)
            if there is None:
                occupied['outside_existing_world']+=1
                continue
            if compound(there)['Name'].value not in {'minecraft:air','minecraft:cave_air','minecraft:void_air'}:
                # A wall / another authored object already owns this cell. Never
                # move a visible object or carve the building to fit a helper.
                occupied[compound(there)['Name'].value.split(':')[0]]+=1
                continue
            collision_volume=sum((b[3]-b[0])*(b[4]-b[1])*(b[5]-b[2]) for b in cell.get('collision',[]))
            requests[at].append((-collision_volume,sum(x*x for x in offset),pos,ident))
    for pos,claims in requests.items():
        _,_,owner,ident=min(claims)
        assert not world.entities_at(pos), ('unexpected block entity under helper',pos)
        world.set_state(pos,tag_state(HELPER))
        world.add_entity(pos,helper(pos,owner,'bloodborne_blocks:'+ident))
        stats['valid_helpers_written']+=1
        stats['shared_helper_cells']+=len(claims)>1
    regions,chunks=world.commit()
    stats.update({'changed_regions':regions,'changed_chunks':chunks})
    for rel,digest in before.items():
        assert sha(source/rel)==digest, ('source modified',rel)
        if not (rel.suffix=='.mca' and rel.parent.name=='region'):
            assert sha(target/rel)==digest, ('non-region modified',rel)
            stats['non_region_files_unchanged']+=1
    result={'counts':dict(stats),'occupied_helper_requests_preserving_visible_blocks':dict(occupied),
            'source_unchanged':True,'models_changed':False,'minecraft_started':False}
    report.write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(json.dumps(result),flush=True)


if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__)
    for name in ('source','original','target','report'):p.add_argument(name,type=Path)
    a=p.parse_args();restore(a.source,a.original,a.target,a.report)
