"""Bridge proven whole source groups to the present city, never replay a map.

Frozen membership evidence is independent of successful write ledgers. Every
remaining source cell must still have its exact current-city representation.
Already accepted owners can explain a consumed historical cell only when their
complete current root/helper topology is present. Historical omitted contexts
are immutable, including their NBT; they never enter the consumed source set.
"""
from __future__ import annotations
import gzip
import hashlib
import json
from collections import defaultdict
from pathlib import Path

from atomic_owner_groups import EVIDENCE, POSITIVE, connected_groups
from logical_contract_v2 import direct_rules
from convert_logical_world import Output, add

CONTEXT_RESULT = 'HISTORICAL_OWNER_CLOSURE_WITH_PRESERVED_CONTEXT'
PROOF_MANIFEST = Path(__file__).resolve().parents[1]/'docs/accepted-restore/complete-owner-evidence.json'


def load_additional_proof(path):
    """Only reviewed, pinned evidence bytes can extend the frozen source graph."""
    raw = Path(path).read_bytes()
    manifest = json.loads(PROOF_MANIFEST.read_bytes())
    if hashlib.sha256(raw).hexdigest() not in {row['sha256'] for row in manifest['proofs']}:
        raise ValueError('additional_owner_proof_hash_not_approved')
    rows = json.loads(gzip.decompress(raw) if str(path).endswith('.gz') else raw)
    if not isinstance(rows, list):
        raise ValueError('additional_owner_proof_must_be_trace_list')
    from composite_world_oracle import SOURCE_SHA
    from audit_remaining_membership_meshes import MODDED_SHA
    for row in rows:
        if row.get('result') in {'UNRESOLVED','REFER_TO_PROVEN_CLOSURE'}:
            continue
        if row.get('result') not in POSITIVE | {CONTEXT_RESULT}:
            raise ValueError('unsupported_owner_proof_result')
        if row.get('errors') or row.get('sourceSha256') != SOURCE_SHA or row.get('moddedSha256') != MODDED_SHA:
            raise ValueError('additional_owner_proof_identity_mismatch')
    return rows


def proven_candidates(world, resources, shapes, defs, entities, owners, extra_paths=()):
    from complete_accepted_restore import (ORACLE, SOURCE_SHA, AIR, complete_current_owner,
        current_alias, normalized, sha, state, text_state, tree_sha, census_rule_index, census_rule, occurrence_outputs,
        wall_successors, source_census)
    from accepted_objects_restore import MODDED_SHA
    oracle_bytes = ORACLE.read_bytes()
    oracle = json.loads(oracle_bytes)
    frozen = json.loads(gzip.decompress(EVIDENCE.read_bytes()))
    progress = json.loads((EVIDENCE.parent/'membership-proof-progress.json').read_bytes())
    if (sha(EVIDENCE) != progress['evidenceSha256'] or
            hashlib.sha256(oracle_bytes).hexdigest() != frozen['oracleSha256']):
        raise ValueError('owner_bridge_frozen_evidence_mismatch')
    oracle = source_census()
    traces = list(frozen['closures'])
    proof_hashes = {str(EVIDENCE.relative_to(ORACLE.parents[2])): sha(EVIDENCE)}
    for path in map(Path, extra_paths):
        rows = load_additional_proof(path)
        traces.extend(rows)
        proof_hashes[str(path)] = sha(path)
    accepted = POSITIVE | {CONTEXT_RESULT}
    for trace in traces:
        if trace.get('result') not in accepted:
            continue
        if (trace.get('errors') or trace.get('sourceSha256') != SOURCE_SHA or
                trace.get('moddedSha256') != MODDED_SHA):
            raise ValueError('owner_bridge_invalid_positive_trace')
        if trace.get('result') in POSITIVE and any(c.get('preservedOmissions') for c in trace['cells']):
            raise ValueError('preserved_context_requires_explicit_proof_kind')
    groups = connected_groups({'closures': traces}, oracle, accepted_results=accepted)
    raw, _ = direct_rules(resources)
    raw_index = census_rule_index(raw)
    mapping = json.loads((resources.parent/'city/migration.json').read_bytes())['states']
    runtime = json.loads((resources.parent/'city/owner-runtime-mappings.json').read_bytes())['states']
    functional_walls = wall_successors(resources,defs)
    occurrence_ids = {(r['rule'], tuple(r['origin'])): i for i,r in enumerate(oracle['occurrences'])}
    seeds, residuals = [], []
    for group in groups:
        dim = 'minecraft:overworld'
        outputs, covered, completed_sources, errors, occurrence_numbers = {}, set(), set(), [], []
        predecessor_cells = set()
        def append_output(root, target):
            if root in outputs and outputs[root] != target:
                errors.append({'reason': 'source_group_root_collision', 'position': list(root)})
            else:
                outputs[root] = target
        for row in group['occurrences']:
            source_roots = {tuple(c['position']) for c in row['source_cells']}
            occurrence_numbers.append(occurrence_ids[(row['rule'], tuple(row['origin']))])
            if not source_roots <= group['objects'].keys():
                errors.append({'reason': 'protected_source_group_incomplete', 'origin': row['origin']})
                continue
            if any(group['objects'][tuple(c['position'])]['state'] != c['state'] for c in row['source_cells']):
                errors.append({'reason': 'source_group_state_mismatch', 'origin': row['origin']})
                continue
            rule = census_rule(row, raw_index)
            targets = occurrence_outputs(row, rule, defs)
            # Two explicit accepted technical-root exceptions, never a search
            # for a nearby available position.
            from explicit_root_exceptions import CASES
            for p, target in list(targets.items()):
                family = target[0].split(':', 1)[1]
                expected = CASES.get((family, p))
                if expected and dict(target[1]) == {**expected, 'root_anchor': 'canonical'}:
                    upper = (target[0], tuple(sorted({**expected, 'root_anchor': 'upper'}.items())))
                    new_root = add(p, (0,1,0))
                    if complete_current_owner(world, entities, owners, dim, new_root, upper, shapes, defs):
                        del targets[p]
                        targets[new_root] = upper
            for p, target in targets.items():
                append_output(p, target)
            if all(complete_current_owner(world, entities, owners, dim, p, t, shapes, defs) for p,t in targets.items()):
                completed_sources.update(source_roots)
            covered.update(source_roots)
        for p, obj in group['objects'].items():
            if p in covered:
                continue
            successor = runtime.get(obj['state'])
            if successor is None:
                errors.append({'reason': 'whole_dependency_successor_missing', 'position': list(p), 'state': obj['state']})
                continue
            predecessor = normalized((successor['id'], tuple(sorted(successor['properties'].items()))), defs)
            target = functional_walls.get(predecessor,predecessor)
            append_output(p, target)
            if complete_current_owner(world, entities, owners, dim, p, predecessor, shapes, defs):
                predecessor_cells.update(add(p,d) for d in shapes[predecessor])
                completed_sources.add(p)
            elif complete_current_owner(world, entities, owners, dim, p, target, shapes, defs):
                completed_sources.add(p)
        ident = 'source-group-' + tree_sha(sorted(group['objects']))[:20]
        if errors:
            residuals.append({'id': ident, 'occurrences': occurrence_numbers, 'errors': errors})
            continue
        current_output_cells = set(predecessor_cells)
        all_outputs_correct = True
        for p, target in outputs.items():
            if complete_current_owner(world, entities, owners, dim, p, target, shapes, defs):
                current_output_cells.update(add(p,d) for d in shapes[target])
            else:
                all_outputs_correct = False
        if all_outputs_correct:
            continue
        consume, preserve = set(), {}
        # Reconstruct the contributing owners independently of trace order;
        # connected traces can list only a subset on a shared cell.
        contributors = defaultdict(set)
        for root, obj in group['objects'].items():
            for point in obj['cells']:
                contributors[tuple(point)].add(root)
        for p, cell in group['cells'].items():
            before = normalized(world.get(dim, p), defs)
            expected = current_alias(state(cell['actual']), mapping, defs) if cell['actual'] else None
            if cell.get('preservedOmissions'):
                # A preserved legacy context may already have its own accepted
                # logical successor. close_group validates its current owner;
                # regardless of identity, this cell/NBT is never removable.
                preserve[p] = (world.get(dim,p), entities.get((dim,*p)))
                continue
            if p in current_output_cells:
                # Complete outputs are reconstructed through their declared
                # roots. A helper can be hosted by a foreign logical root;
                # being inside the footprint never authorizes consuming it.
                continue
            if before == expected:
                consume.add(p)
            elif before == AIR and contributors[p] <= completed_sources:
                consume.add(p)
            else:
                errors.append({'reason': 'current_city_source_mismatch', 'position': list(p),
                    'expected': text_state(expected), 'actual': text_state(before)})
        if errors:
            residuals.append({'id': ident, 'occurrences': occurrence_numbers, 'errors': errors})
            continue
        seeds.append({'ids': [ident], 'occurrences': occurrence_numbers, 'dimension': dim,
            'consume': consume, 'outputs': outputs, 'preserve': preserve,
            'evidence': 'pinned complete source-owner proof + exact current-city state + current contracts'})
    return seeds, residuals, proof_hashes
