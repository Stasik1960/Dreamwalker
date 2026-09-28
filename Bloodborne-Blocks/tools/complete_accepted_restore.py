"""Complete-repair adapter for the published rc.2 city.

Candidates come from the original source census, never the successful repair
ledger. Only exact existing recipes and complete current owner transactions are
admitted. This adapter deliberately reports unsupported occurrences as known
residuals; a successful subset is not a completeness gate.
"""
from __future__ import annotations

import argparse
import gzip
import hashlib
import json
import os
import re
import tempfile
from collections import Counter, defaultdict
from dataclasses import replace
from functools import lru_cache
from pathlib import Path

from accepted_objects_restore import (AIR, bindings, helper_tag, load_shapes,
    sha, state, text_state, tree_sha)
from convert_logical_world import (PART, Candidate, Expected, Output, Rule, World,
    add, apply, block_pos_long, copy_source, definition_hashes, hash_tree,
    validate_paths, write_report)
from logical_contract_v2 import direct_rules
from modded_world_adapter import compile_modded_rules
from world_io import TAG_COMPOUND, TAG_LIST, TAG_STRING, Tag, compound, section_blocks, block_state_key

ROOT = Path(__file__).resolve().parents[1]
RESOURCES = ROOT / 'src/main/resources/bloodborne_blocks/logical'
RC2 = ROOT / 'releases/Bloodborne-Blocks/2.1.0-rc.2/Bloodborne-City-2.1.0-rc.2.zip'
RC2_SHA = 'f4b9ef510e2aacade80bb11f95cd82fe17eaed56e118280e8e055dd4aecd133c'
RC2_TREE_SHA = 'b859d1e7e9ecc7df03937ff50ad987dcb249d43c4faa5041c4fea47ddc62cbd8'
# A repeated pass in the same process can consume only a tree this process
# just produced from the pinned source. A caller's self-hash is not provenance.
_PRODUCED_TREES = set()
ORACLE = ROOT / 'docs/composite-grid-repair/protected-world-oracle.json'
SOURCE_SHA = '4353737d536677469d3b895e3515496ab64fab7b224e43428eb96e8c09724a51'
BUSH_CENSUS = ROOT / 'docs/accepted-restore/bush-source-census.json.gz'
BUSH_CENSUS_SHA = '809a6f426dd373a9562867e700a1167cac478d958bae7d118fde98bf3829b744'


def source_census():
    oracle = json.loads(ORACLE.read_bytes())
    raw = BUSH_CENSUS.read_bytes()
    if hashlib.sha256(raw).hexdigest() != BUSH_CENSUS_SHA:
        raise ValueError('bush_source_census_hash_mismatch')
    bushes = json.loads(gzip.decompress(raw))
    if oracle['source_sha256'] != SOURCE_SHA or bushes['source_sha256'] != SOURCE_SHA:
        raise ValueError('original_source_census_mismatch')
    # Synthetic census identifiers are labels, never indexes into raw rules.
    oracle['occurrences'].extend({**row, 'rule': 'source-grass-0'} for row in bushes['occurrences'])
    return oracle


def definitions(resources):
    return {"bloodborne_blocks:" + b['id']: b for directory in (resources.parent/'city', resources)
            for b in json.loads((directory/'definitions.json').read_bytes())['blocks']}


def normalized(value, defs):
    if value is None:
        return None
    return value[0], tuple(sorted({**defs.get(value[0], {}).get('default', {}), **dict(value[1])}.items()))


def current_alias(value, mapping, defs):
    entry = mapping.get(text_state(value))
    if entry:
        return normalized((entry['id'], tuple(sorted(entry.get('properties', {}).items()))), defs)
    return normalized(value, defs)


def owner_index(entities):
    owners = defaultdict(set)
    for (dim, *point), tag in entities.items():
        if compound(tag).get('id') == Tag(TAG_STRING, PART):
            for owner, root in bindings(tag):
                owners[(dim, owner, root)].add(tuple(point))
    return owners


def census_rule_index(raw):
    result = defaultdict(list)
    for rule in raw:
        signature = frozenset((piece.offset, piece.state) for piece in (rule.source,) + rule.members)
        result[signature].append(rule)
    return result


def census_rule(row, index):
    from source_variant_rng import guards_match
    origin = tuple(row['origin'])
    signature = frozenset((tuple(c['position'][i]-origin[i] for i in range(3)), state(c['state'])) for c in row['source_cells'])
    wanted = {o['family'] for o in row['outputs']}
    matches = [rule for rule in index.get(signature, ()) if guards_match(rule.variant_guards, origin) and
        {o.target[0].split(':',1)[1] for o in (rule.outputs or (Output(rule.target,rule.root_offset,rule.shape),))} == wanted]
    if len(matches) != 1:
        raise ValueError('current_census_recipe_missing_or_ambiguous: '+str(row['rule'])+' '+str(origin))
    return matches[0]


@lru_cache(None)
def accepted_window_states():
    """User explicitly accepted rc.2 placed-window orientations: pin that input.

    A stale census rule ordinal is never an orientation migration. This small
    exception is read from the immutable delivered ZIP, not from mutable output.
    """
    from composite_world_oracle import EvidenceReader
    if sha(RC2) != RC2_SHA:
        raise ValueError('accepted_window_baseline_changed')
    reader = EvidenceReader(RC2)
    result = {}
    try:
        for row in json.loads(ORACLE.read_bytes())['occurrences']:
            for output in row['outputs']:
                if output['family'] != 'o_shuttered_window':
                    continue
                p = tuple(output['canonical_root'])
                value = reader.state(row['dimension'], p)[1]
                if value and value.split('[',1)[0] == 'bloodborne_blocks:o_shuttered_window':
                    result[(row['dimension'], p)] = state(value)
    finally:
        reader.close()
    return result


def occurrence_outputs(row, rule, defs):
    dim, origin = row['dimension'], tuple(row['origin'])
    outputs = {add(origin,o.root_offset): o.target for o in
        (rule.outputs or (Output(rule.target,rule.root_offset,rule.shape),))}
    for p,target in list(outputs.items()):
        if target[0] == 'bloodborne_blocks:o_shuttered_window':
            previous = accepted_window_states().get((dim,p))
            if previous is not None:
                outputs[p] = normalized(previous,defs)
    return outputs


def complete_current_owner(world, entities, owners, dim, root, value, shapes, defs):
    if normalized(world.get(dim, root), defs) != value or value not in shapes:
        return False
    expected = {add(root, d) for d in shapes[value] if d != (0, 0, 0)}
    if owners.get((dim, value[0], root), set()) != expected:
        return False
    try:
        for p in {root} | expected:
            tag = entities.get((dim, *p))
            incoming = bindings(tag)
            if p != root and ((value[0], root) not in incoming or
                    world.get(dim, p) is None or not world.get(dim, p)[0].startswith('bloodborne_blocks:')):
                return False
            for name, other in incoming:
                existing = normalized(world.get(dim, other), defs)
                delta = tuple(p[i]-other[i] for i in range(3))
                if existing is None or existing[0] != name or delta not in shapes.get(existing, ()):
                    return False
    except ValueError:
        return False
    return True


def recipe_candidates(world, resources, shapes, defs, entities, owners):
    """Use every original census occurrence, including failed old conversions."""
    oracle = source_census()
    raw, _ = direct_rules(resources)
    raw_index = census_rule_index(raw)
    adapted, _, _ = compile_modded_rules(resources)
    by_raw = defaultdict(list)
    mapping = json.loads((resources.parent/'city/migration.json').read_bytes())['states']
    from source_mapping_archive import archive
    from modded_world_adapter import compose_legacy_rule, compose_rule
    frozen = archive()
    migration = frozen['v2\\migration.json']
    legacy = {d['id']: d for d in frozen['definitions.json']['blocks']}
    old_defaults = {'bloodborne_blocks:' + d['id']: d.get('default', {}) for d in frozen['v2\\definitions.json']['blocks']}
    whole_owners = json.loads((resources.parent/'city/owner-runtime-mappings.json').read_bytes())['states']
    @lru_cache(None)
    def complete_component_representations(source):
        # Choose representations of an ENTIRE authored source carrier, never
        # a different fallback for individual cells of that carrier.
        single = replace(raw[0], source=Expected((0, 0, 0), source), members=(), outputs=())
        choices = []
        direct, _ = compose_legacy_rule(single, legacy)
        modular, _ = compose_rule(single, migration, old_defaults)
        for pieces in (direct, modular):
            if pieces:
                values = []
                for piece in pieces:
                    value = piece.state
                    if value[0].startswith('minecraft:'):
                        carrier = value[0].split(':', 1)[1]
                        if carrier not in legacy:
                            break
                        value = ('bloodborne_blocks:' + carrier, tuple(sorted({**legacy[carrier].get('default', {}), **dict(value[1])}.items())))
                    values.append((piece.offset, current_alias(value, mapping, defs)))
                else:
                    choices.append(tuple(values))
        owner = whole_owners.get(text_state(source))
        if owner:
            choices.append((((0, 0, 0), normalized((owner['id'], tuple(sorted(owner['properties'].items()))), defs)),))
        return tuple(dict.fromkeys(choices))
    for rule in adapted:
        match = re.search(r'raw rule (\d+)$', rule.source_reference or '')
        if match:
            by_raw[int(match.group(1))].append(rule)
    seeds, residuals, census = [], [], []
    for number, row in enumerate(oracle['occurrences']):
        dim, origin = row['dimension'], tuple(row['origin'])
        rule = census_rule(row, raw_index)
        outputs = occurrence_outputs(row, rule, defs)
        if all(complete_current_owner(world, entities, owners, dim, p, t, shapes, defs) for p, t in outputs.items()):
            census.append({'occurrence': number, 'result': 'complete_logical_owners_present', 'roots': [list(p) for p in outputs]})
            continue
        if all(normalized(world.get(dim, p), defs) == t for p, t in outputs.items()):
            seeds.append({'ids': [f'census-{number}'], 'occurrences': [number], 'dimension': dim,
                'consume': set(outputs), 'outputs': outputs,
                'evidence': 'known current canonical roots with incomplete helper topology'})
            continue
        exact = []
        for candidate in by_raw[rule.number]:
            cells = {add(origin, piece.offset): current_alias(piece.state, mapping, defs)
                     for piece in (candidate.source,) + candidate.members}
            if all(normalized(world.get(dim, p), defs) == v for p, v in cells.items()):
                exact.append(cells)
        if not exact:
            mixed = {}
            for component in (rule.source,) + rule.members:
                base = add(origin, component.offset)
                matches = []
                for representation in complete_component_representations(component.state):
                    cells = {add(base, offset): value for offset, value in representation}
                    if all(normalized(world.get(dim, p), defs) == v for p, v in cells.items()):
                        matches.append(cells)
                if not matches or len({frozenset(c.items()) for c in matches}) != 1:
                    break
                chosen = matches[0]
                if any(p in mixed and mixed[p] != v for p, v in chosen.items()):
                    break
                mixed.update(chosen)
            else:
                exact.append(mixed)
        if not exact:
            residuals.append({'occurrence': number, 'rule': row['rule'], 'dimension': dim,
                'origin': list(origin), 'families': sorted({o['family'] for o in row['outputs']}),
                'reason': 'known_recipe_requires_complete_current_source_proof'})
            continue
        # Different source representations must agree on the consumed cells.
        choices = {frozenset(cells.items()) for cells in exact}
        if len(choices) != 1:
            residuals.append({'occurrence': number, 'dimension': dim, 'origin': list(origin),
                              'reason': 'ambiguous_complete_current_representation'})
            continue
        seeds.append({'ids': [f'census-{number}'], 'occurrences': [number], 'dimension': dim,
                      'consume': set(exact[0]), 'outputs': outputs,
                      'evidence': 'original census + frozen exact source recipe + city migration'})
    return seeds, residuals, census


def wall_successors(resources, defs):
    """Exact same-art aliases only; mixed unsupported art is not approximated."""
    city = resources.parent/'city'
    family = json.loads((city/'reviewed-wall-family.json').read_bytes())
    geometry = json.loads((city/'geometry.json').read_bytes())
    def profile(ident, key):
        spec = geometry['blocks'][ident]['states'][key]
        return geometry['profiles'].get(spec.get('ref'), spec)
    target = defs['bloodborne_blocks:' + family['id']]
    signatures = defaultdict(list)
    for key, model in target['models'].items():
        signatures[(model, tree_sha(profile(target['id'], key)))].append(key)
    result = {}
    for ident in family['aliases']:
        definition = defs['bloodborne_blocks:' + ident]
        for key, model in definition['models'].items():
            matches = signatures.get((model, tree_sha(profile(ident, key))), [])
            if matches:
                # Several orientations can render the same symmetric artwork.
                chosen = min(matches, key=lambda s: ('facing=north' not in s, s))
                result[state('bloodborne_blocks:' + ident + '[' + key + ']')] = state(
                    'bloodborne_blocks:' + target['id'] + '[' + chosen + ']')
    return result


def alias_candidates(world, resources, defs):
    import numpy as np
    aliases = wall_successors(resources, defs)
    aliases_manifest = json.loads((resources.parent/'city/reviewed-wall-family.json').read_bytes())['aliases']
    wanted_ids = {'bloodborne_blocks:' + ident for ident in aliases_manifest}
    seeds, residuals = [], []
    for chunk in world.chunks.values():
        for section in chunk.root().get('sections', Tag(TAG_LIST, [], TAG_COMPOUND)).value:
            raw = compound(section).get('block_states')
            if raw is None:
                continue
            selected = {i: v for i, t in enumerate(compound(raw)['palette'].value)
                        if (v := normalized(state(block_state_key(t)), defs))[0] in wanted_ids}
            if not selected:
                continue
            _, indices = section_blocks(section)
            indices = np.asarray(indices)
            sy = int(compound(section)['Y'].value)
            for index, value in selected.items():
                for offset in np.flatnonzero(indices == index):
                    p = (chunk.x*16 + int(offset & 15), sy*16 + int(offset >> 8), chunk.z*16 + int((offset >> 4) & 15))
                    target = aliases.get(value)
                    if target is None:
                        residuals.append({'dimension': chunk.dimension, 'position': list(p), 'state': text_state(value),
                            'family': 'building_stone_brick_wall', 'reason': 'reviewed_alias_has_no_exact_functional_state'})
                        continue
                    seeds.append({'ids': [f'wall-{chunk.dimension}-{p}'], 'occurrences': [],
                        'dimension': chunk.dimension, 'consume': {p}, 'outputs': {p: target},
                        'evidence': 'reviewed wall alias; identical model and complete physical profile'})
    return seeds, residuals


def close_group(seed, world, entities, owners, shapes, defs):
    """Close actual NBT owner edges, preserving every foreign owner as a whole."""
    dim = seed['dimension']
    consume = set(seed['consume'])
    outputs = dict(seed['outputs'])
    replace_roots = set(seed.get('replaceRoots', ())) | set(outputs)
    points = consume | {add(p, d) for p, v in outputs.items() for d in shapes[v]}
    scanned, closed = set(), set()
    errors = list(seed.get('preflightErrors', ()))
    def include(root, name, replacing=False):
        before = world.get(dim, root)
        value = normalized(before, defs)
        if value is None or value[0] != name or value not in shapes:
            raise ValueError('unproven_current_owner')
        if root in closed:
            return
        closed.add(root)
        actual = owners.get((dim, name, root), set())
        expected = {add(root, d) for d in shapes[value] if d != (0, 0, 0)}
        # Source carriers may have had helpers intentionally removed by rc.1;
        # only their actual bindings are consumed. A preserved dependency must
        # be complete, otherwise rebuilding it would mutate foreign data.
        if actual - expected or (not replacing and actual != expected):
            raise ValueError('incomplete_current_dependency_owner')
        points.update(actual)
        points.add(root)
        if replacing:
            consume.update(actual)
        else:
            if root in outputs and outputs[root] != value:
                raise ValueError('foreign_root_output_collision')
            outputs[root] = value
            points.update(add(root, d) for d in shapes[value])
    try:
        for point in list(consume):
            before = world.get(dim, point)
            if before and normalized(before, defs) in shapes:
                semantic_root = before[0].startswith(('bloodborne_blocks:o_', 'bloodborne_blocks:owner_'))
                include(point, before[0], not semantic_root or point in replace_roots)
        while points - scanned:
            p = min(points - scanned)
            scanned.add(p)
            before = world.get(dim, p)
            if before is None:
                raise ValueError('missing_destination_chunk')
            entity = entities.get((dim, *p))
            if entity is not None:
                for name, root in bindings(entity):
                    include(root, name, root in replace_roots)
            if p in consume or before == AIR or before == (PART, ()):
                continue
            value = normalized(before, defs)
            if value in shapes and (shapes[value] != {(0, 0, 0)} or value[0].startswith(('bloodborne_blocks:o_', 'bloodborne_blocks:owner_'))):
                include(p, value[0])
            elif outputs.get(p) != value:
                raise ValueError('foreign_block_in_target_footprint: '+str(p)+' '+str(text_state(before)))
        if world.ticks_at(dim, points):
            raise ValueError('scheduled_tick_at_target')
        min_y, max_y = world.build_height(dim)
        if any(not min_y <= p[1] < max_y for p in points):
            raise ValueError('target_outside_dimension_build_height')
    except ValueError as exc:
        errors.append(str(exc))
    occupancy = defaultdict(list)
    for root, value in outputs.items():
        for offset in shapes[value]:
            occupancy[add(root, offset)].append((value[0], root))
    desired = {p: AIR for p in points}
    helpers = {}
    for p, members in occupancy.items():
        if p not in points:
            errors.append('output_outside_preflight')
            continue
        if len(members) > 16:
            errors.append('too_many_shared_owners')
        desired[p] = outputs.get(p, (PART, ()))
        guests = sorted({(name, root) for name, root in members if root != p}, key=lambda v: (v[1], v[0]))
        if guests:
            row = {'Root': block_pos_long(*guests[0][1]), 'Owner': guests[0][0]}
            if len(guests) > 1:
                row['Owners'] = [{'Root': block_pos_long(*r), 'Owner': n} for n, r in guests]
            helpers[p] = helper_tag(p, row)
    shared_contexts = []
    for p, (before, entity) in seed.get('preserve', {}).items():
        if p not in desired or (desired[p] == before and helpers.get(p) == entity):
            continue
        # Historical omission evidence cannot consume its foreign context.
        # Separately proven complete current logical roots may nevertheless
        # host a new guest through the existing shared-owner representation.
        # Preserve the raw state and every existing binding; arbitrary NBT,
        # ordinary terrain and incomplete owners cannot use this path.
        value = normalized(before, defs)
        if (p not in consume and p in closed and value == normalized(desired[p], defs) and
                complete_current_owner(world, entities, owners, dim, p, value, shapes, defs)):
            try:
                if not set(bindings(entity)) <= set(bindings(helpers.get(p))):
                    raise ValueError('preserved_binding_removed')
                desired[p] = before
                shared_contexts.append(p)
                continue
            except ValueError:
                pass
        errors.append('preserved_context_would_change: '+str(p))
    for p in points:
        old_entity = entities.get((dim, *p))
        if old_entity is not None and 'keepPacked' in compound(old_entity):
            errors.append('helper_has_preserved_keepPacked_metadata: '+str(p))
    return {**seed, 'consume': consume, 'outputs': outputs, 'desired': desired,
            'helpers': helpers, 'sharedPreservedContexts': shared_contexts, 'errors': sorted(set(errors))}


def closed_groups(seeds, world, entities, owners, shapes, defs):
    while True:
        groups = [close_group(s, world, entities, owners, shapes, defs) for s in seeds]
        parent = list(range(len(groups)))
        def find(i):
            while parent[i] != i:
                parent[i] = parent[parent[i]]
                i = parent[i]
            return i
        seen = {}
        for i, group in enumerate(groups):
            for p in group['desired']:
                key = group['dimension'], p
                if key in seen:
                    a, b = find(i), find(seen[key])
                    parent[max(a, b)] = min(a, b)
                seen[key] = i
        if all(find(i) == i for i in range(len(groups))):
            return groups
        merged = {}
        for i, seed in enumerate(seeds):
            target = merged.setdefault(find(i), {'ids': [], 'occurrences': [], 'dimension': seed['dimension'],
                'consume': set(), 'outputs': {}, 'preserve': {}, 'replaceRoots': set(), 'preflightErrors': [], 'evidence': 'union of exact recipes and current owner edges'})
            target['ids'].extend(seed['ids'])
            target['occurrences'].extend(seed['occurrences'])
            target['consume'].update(seed['consume'])
            target['preserve'].update(seed.get('preserve', {}))
            target['replaceRoots'].update(seed.get('replaceRoots', ()))
            target['preflightErrors'].extend(seed.get('preflightErrors', ()))
            for p, value in seed['outputs'].items():
                if p in target['outputs'] and target['outputs'][p] != value:
                    target['preflightErrors'].append('conflicting_exact_recipe_roots: '+str(p)+' '+str(text_state(target['outputs'][p]))+' vs '+str(text_state(value)))
                else:
                    target['outputs'][p] = value
        seeds = list(merged.values())


def run(source, output, *, resources=RESOURCES, report_path=None, dry_run=True,
        expected_source_tree_sha256=None, progress=False, owner_proofs=()):
    source, output, resources = map(lambda p: Path(p).resolve(), (source, output, resources))
    report_path = Path(report_path or ROOT/'build/complete-accepted-repair/plan.json').resolve()
    validate_paths(source, output, resources, report_path, ROOT/'build')
    if output.exists():
        raise ValueError('output must be a new world name')
    if source.is_file():
        if sha(source) != RC2_SHA:
            raise ValueError('source must be the published rc.2 ZIP')
    else:
        digest = tree_sha(hash_tree(source))
        if digest not in {RC2_TREE_SHA} | _PRODUCED_TREES:
            raise ValueError('directory source is not pinned rc2 or an output of this process')
        if expected_source_tree_sha256 is not None and digest != expected_source_tree_sha256:
            raise ValueError('repeat source integrity mismatch')
    shapes, defs = load_shapes(resources), definitions(resources)
    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='complete-repair-', dir=output.parent) as temporary:
        copied, source_hashes, kind = copy_source(source, Path(temporary)/'current')
        if progress:
            print('Loading new rc.2 working copy', flush=True)
        world = World(copied, {})
        entities = world.block_entities()
        owners = owner_index(entities)
        seeds, residuals, census = recipe_candidates(world, resources, shapes, defs, entities, owners)
        aliases, alias_residuals = alias_candidates(world, resources, defs)
        from complete_owner_bridge import proven_candidates
        proven, proof_residuals, proof_hashes = proven_candidates(world, resources, shapes, defs, entities, owners, owner_proofs)
        if progress:
            print('Exact recipes', len(seeds), 'wall aliases', len(aliases), 'proven groups',len(proven),'known residuals', len(residuals), flush=True)
        groups = closed_groups(seeds + aliases + proven, world, entities, owners, shapes, defs)
        candidates, transactions = [], []
        for number, g in enumerate(groups):
            dim = g['dimension']
            correct = all(world.get(dim, p) == v and entities.get((dim, *p)) == g['helpers'].get(p) for p, v in g['desired'].items())
            result = 'blocked' if g['errors'] else 'already_correct' if correct else 'ready'
            ident = 'complete-accepted-' + tree_sha(sorted(g['ids']))[:20]
            transactions.append({'transaction': ident, 'sources': sorted(g['ids']), 'occurrences': g['occurrences'],
                'dimension': dim, 'result': result, 'errors': g['errors'],
                'sharedPreservedContexts': [list(p) for p in g['sharedPreservedContexts']],
                'roots': [{'position': list(p), 'state': text_state(v)} for p, v in sorted(g['outputs'].items())]})
            if result != 'ready':
                continue
            origin = min(g['desired'])
            rel = lambda p: tuple(p[i]-origin[i] for i in range(3))
            pieces = tuple(Expected(rel(p), world.get(dim, p)) for p in sorted(g['desired']))
            outputs = tuple(Output(v, rel(p), shapes[v]) for p, v in sorted(g['outputs'].items()))
            rule = Rule(number, pieces[0], outputs[0].target, outputs[0].root_offset, pieces[1:], None, outputs[0].shape,
                transaction_id=ident, outputs=outputs, source_mode='modded', source_reference=g['evidence'],
                allowed_origins=((dim, origin),), atomic_owner_group=True, shared_physics=True)
            candidates.append(Candidate(rule, 'modded', dim, origin, set(g['desired']), add(origin, outputs[0].root_offset),
                {p: v for p, v in g['desired'].items() if v != AIR}, tuple(g['outputs'].items())))
        ledger = [] if dry_run else apply(world, candidates)
        report = {'format': 'bloodborne-complete-accepted-repair-v1', 'source': {'kind': kind, 'hashes': source_hashes},
            'sourceCensusSha256': sha(ORACLE), 'bushSourceCensusSha256': sha(BUSH_CENSUS), 'resources': definition_hashes(resources),
            'ownerProofHashes': proof_hashes, 'ownerProofResiduals': proof_residuals,
            'counts': {'originalOccurrences': len(census)+len(residuals)+len(seeds), 'logicalRootsPresent': len(census),
                'exactRecipeCandidates': len(seeds), 'wallAliasOccurrences': len(aliases)+len(alias_residuals),
                'provenSourceGroups': len(proven), 'sourceProofResiduals': len(proof_residuals),
                'wallAliases': len(aliases), 'wallAliasResiduals': len(alias_residuals), 'readyGroups': len(candidates),
                'blockedGroups': sum(bool(g['errors']) for g in groups), 'knownResiduals': len(residuals)},
            'census': census, 'knownResiduals': residuals, 'wallAliasResiduals': alias_residuals, 'transactions': transactions, 'ledger': ledger,
            'subsetResult': 'PRECHECK' if dry_run else 'APPLIED', 'coverageCompleteness': 'NOT_VERIFIED',
            'releaseReady': False}
        if not dry_run:
            world.save()
            report['outputFiles'] = hash_tree(copied)
            report['outputTreeSha256'] = tree_sha(report['outputFiles'])
            os.replace(copied, output)
            _PRODUCED_TREES.add(report['outputTreeSha256'])
        write_report(report_path, report)
        if progress:
            print(json.dumps(report['counts']), flush=True)
        return report


def run_with_repeat(source, output, *, repeat_check=False, **kwargs):
    if repeat_check and kwargs.get('dry_run',True):
        raise ValueError('repeat check requires an applied new copy')
    first = run(source,output,**kwargs)
    if repeat_check:
        report_path = Path(kwargs.get('report_path') or ROOT/'build/complete-accepted-repair/plan.json')
        second_output = Path(output).with_name(Path(output).name+'-second-pass')
        second_report = report_path.with_name(report_path.stem+'-second.json')
        repeat_args = {**kwargs, 'report_path':second_report,
            'expected_source_tree_sha256':first['outputTreeSha256']}
        second = run(output,second_output,**repeat_args)
        same = first['outputFiles'] == second['outputFiles']
        first['secondPass'] = {'report':str(second_report),'transactions':len(second['ledger']),
            'byteIdentical':same,'outputTreeSha256':second['outputTreeSha256'],
            'result':'PASS' if not second['ledger'] and same else 'FAIL'}
        write_report(report_path,first)
        if first['secondPass']['result'] != 'PASS':
            raise ValueError('complete repair is not idempotent; both new copies retained for diagnosis')
    return first


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('source', type=Path)
    parser.add_argument('output', type=Path)
    parser.add_argument('--report', type=Path)
    parser.add_argument('--apply', action='store_true')
    parser.add_argument('--progress', action='store_true')
    parser.add_argument('--source-tree-sha256')
    parser.add_argument('--owner-proof', type=Path, action='append', default=[])
    parser.add_argument('--repeat-check', action='store_true', help='apply again to another new copy and compare every byte')
    args = parser.parse_args()
    run_with_repeat(args.source, args.output, report_path=args.report, dry_run=not args.apply,
        expected_source_tree_sha256=args.source_tree_sha256, progress=args.progress, owner_proofs=args.owner_proof,
        repeat_check=args.repeat_check)
