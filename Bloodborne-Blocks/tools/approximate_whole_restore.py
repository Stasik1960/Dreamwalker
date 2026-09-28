"""Explicitly opted-in approximate restoration on a NEW pinned rc.3 city copy.

Uses existing authored contracts and atomic owner transactions. Geometry matching
is intentionally relaxed, not NBT/tick/owner validation. Never a default mode.
"""
import argparse
import gzip
import json
import os
import tempfile
from collections import Counter
from pathlib import Path

from complete_accepted_restore import (ROOT, RESOURCES, AIR, PART, state, normalized,
    definitions, load_shapes, owner_index, complete_current_owner, source_census,
    census_rule_index, census_rule, occurrence_outputs, closed_groups, sha, tree_sha)
from convert_logical_world import (World, copy_source, hash_tree, validate_paths, write_report,
    Expected, Output, Rule, Candidate, add, apply, definition_hashes)
from logical_contract_v2 import direct_rules
from modded_world_adapter import compose_rule
from source_mapping_archive import archive
from inspect_historical_wall_meshes import historical_art, carrier_cells

RC3_SHA = '111253971789feea9c016c693c4122a7faee6952c11102c092d73f3475df6150'
# Two source recipes compete for the same root. In approximate mode retain both
# whole models with explicit, reproducible displacements, never duplicate roots.
RELOCATIONS = {('o_books',(-374,73,-291)):(0,1,0),
               ('o_grass_4',(-318,77,-134)):(0,2,0),
               ('o_grass_7',(-158,35,-60)):(0,0,-2),
               ('o_stone_railing',(-560,98,-9)):(0,1,0)}


def census():
    rows = source_census()['occurrences']
    pending = json.loads(gzip.decompress((ROOT/'docs/accepted-restore/known-pending-vegetation.json.gz').read_bytes()))
    for row in pending['occurrences']:
        ident = 'o_grass_'+str(row['weighted_index'])
        rows.append({**row, 'rule':'source-grass-'+str(row['weighted_index']),
                     'outputs':[{'family':ident, 'canonical_root':row['canonical_root'],
                         'expected_logical_state':f'bloodborne_blocks:{ident}[facing=north,visual=base]'}]})
    return rows


def plan(world, resources=RESOURCES):
    shapes, defs = load_shapes(resources), definitions(resources)
    entities = world.block_entities()
    owners = owner_index(entities)
    raw, _ = direct_rules(resources)
    index = census_rule_index(raw)
    frozen = archive()
    migration = frozen['v2\\migration.json']
    defaults = {'bloodborne_blocks:'+d['id']:d.get('default',{}) for d in frozen['v2\\definitions.json']['blocks']}
    seeds, correct, blocked = [], [], []
    footprint_cache = {}
    with historical_art() as jar:
        def footprint(component, position):
            cache_key = component.state, position
            if cache_key not in footprint_cache:
                ident = component.state[0].split(':',1)[1]
                props = dict(component.state[1])
                entry = migration.get(ident, {})
                props = {**entry.get('default',{}), **props}
                pieces = entry.get('states',{}).get(','.join(k+'='+v for k,v in sorted(props.items())))
                if isinstance(pieces,list):
                    cells = {tuple(p['offset']) for p in pieces}
                elif isinstance(pieces,dict) and pieces.get('keep'):
                    cells = set(carrier_cells(jar,ident,props,position))
                else:
                    raise ValueError('source footprint unavailable: '+ident)
                footprint_cache[cache_key] = cells | {(0,0,0)}
            return footprint_cache[cache_key]
        for number,row in enumerate(census()):
            dim, origin = row['dimension'],tuple(row['origin'])
            rule = census_rule(row,index)
            outputs = occurrence_outputs(row,rule,defs)
            outputs = {add(p,RELOCATIONS.get((t[0].split(':',1)[1],p),(0,0,0))):t for p,t in outputs.items()}
            if all(complete_current_owner(world,entities,owners,dim,p,t,shapes,defs) for p,t in outputs.items()):
                correct.append(number)
                continue
            try:
                projected = {add(add(origin,c.offset),d) for c in (rule.source,)+rule.members for d in footprint(c,add(origin,c.offset))}
            except (ValueError,KeyError) as exc:
                blocked.append({'occurrence':number,'reason':str(exc)})
                continue
            # Approximation scope: old authored footprint and new contract only.
            # No radius flood-fill or deletion of arbitrary neighboring owners.
            target = {add(p,d) for p,t in outputs.items() for d in shapes[t]}
            consume = {p for p in projected if (world.get(dim,p) or ('',()))[0].startswith('bloodborne_blocks:')}
            consume.update(target)
            foreign = [p for p in consume if (world.get(dim,p) or ('minecraft:air',()))[0].split(':',1)[0] not in ('minecraft','bloodborne_blocks')]
            seeds.append({'ids':[f'approximate-census-{number}'],'occurrences':[number],
                'dimension':dim,'consume':consume,'outputs':outputs,
                'preflightErrors':['other_mod_block_in_target: '+str(p) for p in foreign],
                'evidence':'USER_APPROVED_APPROXIMATION_2026_09_29: authored footprint, exact geometry comparison waived'})
    groups = closed_groups(seeds,world,entities,owners,shapes,defs,preserve_single_cells=True)
    candidates, transactions = [], []
    for number,g in enumerate(groups):
        dim = g['dimension']
        # close_group refuses arbitrary block entities, ticks, stale owner links,
        # root collisions and excessive shared ownership even in this mode.
        ident = 'approximate-'+tree_sha(sorted(g['ids']))[:20]
        transactions.append({'transaction':ident,'occurrences':g['occurrences'],
            'result':'blocked' if g['errors'] else 'ready','errors':g['errors']})
        if g['errors']:
            continue
        origin = min(g['desired'])
        rel = lambda p: tuple(p[i]-origin[i] for i in range(3))
        pieces = tuple(Expected(rel(p),world.get(dim,p)) for p in sorted(g['desired']))
        outputs = tuple(Output(v,rel(p),shapes[v]) for p,v in sorted(g['outputs'].items()))
        rule = Rule(number,pieces[0],outputs[0].target,outputs[0].root_offset,pieces[1:],None,outputs[0].shape,
            transaction_id=ident,outputs=outputs,source_mode='modded',source_reference=g['evidence'],
            allowed_origins=((dim,origin),),atomic_owner_group=True,shared_physics=True)
        candidates.append(Candidate(rule,'modded',dim,origin,set(g['desired']),add(origin,outputs[0].root_offset),
            {p:v for p,v in g['desired'].items() if v != AIR},tuple(g['outputs'].items())))
    return candidates, {'sourceOccurrences':len(census()),'alreadyCorrect':len(correct),
        'relocations':[{'family':f,'sourceRoot':list(p),'offset':list(d)} for (f,p),d in RELOCATIONS.items()],
        'preflightBlocked':blocked,'transactions':transactions,'readyGroups':len(candidates),
        'blockedReasons':dict(Counter(e for g in groups for e in g['errors']))}


def run(source, output, report, apply_changes=False):
    source,output,report = (Path(p).resolve() for p in (source,output,report))
    validate_paths(source,output,RESOURCES,report,ROOT/'build')
    if output.exists() or not source.is_file() or sha(source) != RC3_SHA:
        raise ValueError('requires unchanged rc.3 ZIP and a new output path')
    output.parent.mkdir(parents=True,exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='approximate-',dir=output.parent) as directory:
        copied, source_hashes, kind = copy_source(source,Path(directory)/'world')
        world = World(copied,{})
        print('Planning explicitly approved approximate replacements',flush=True)
        candidates, result = plan(world)
        result.update(format='bloodborne-approximate-whole-restore-v1',approximate=True,
            sourceSha256=RC3_SHA,resources=definition_hashes(RESOURCES),releaseReady=False)
        result['ledger'] = apply(world,candidates) if apply_changes else []
        if apply_changes:
            for entry in result['ledger']:
                entry['decision'] = 'user_approved_approximate'
                entry['reason'] = 'exact_geometry_matching_waived; NBT_and_owner_guards_retained'
            world.save()
            result['outputFiles'] = hash_tree(copied)
            result['outputTreeSha256'] = tree_sha(result['outputFiles'])
            os.replace(copied,output)
        write_report(report,result)
        print(json.dumps({k:v for k,v in result.items() if k in ('sourceOccurrences','alreadyCorrect','readyGroups','blockedReasons')}),flush=True)
        return result


def repeat_check(output, first_report):
    """Run the actual planner/writer again on a separately named copy."""
    first=json.loads(Path(first_report).read_bytes())
    if hash_tree(output)!=first['outputFiles']:
        raise ValueError('first-pass tree changed before repeat')
    second=Path(output).with_name(Path(output).name+'-repeat')
    if second.exists():raise ValueError('repeat output already exists')
    copied,_,_=copy_source(Path(output),second)
    world=World(copied,{})
    candidates,summary=plan(world)
    ledger=apply(world,candidates)
    world.save()
    identical=hash_tree(copied)==first['outputFiles']
    result={'result':'PASS' if not ledger and identical else 'FAIL',
        'transactions':len(ledger),'byteIdentical':identical,'summary':summary,
        'outputTreeSha256':tree_sha(hash_tree(copied))}
    write_report(Path(first_report).with_name(Path(first_report).stem+'-repeat.json'),result)
    print(json.dumps({k:v for k,v in result.items() if k!='summary'}),flush=True)
    return result


if __name__ == '__main__':
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('source',type=Path);p.add_argument('output',type=Path);p.add_argument('--report',type=Path,required=True)
    p.add_argument('--accept-approximate',action='store_true',help='explicitly allow approximate art replacement on a new copy')
    args=p.parse_args();run(args.source,args.output,args.report,args.accept_approximate)
