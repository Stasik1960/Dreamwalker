"""Compile frozen owner closures into the existing multi-output transaction engine.

The evidence is not a write ledger. It only declares ownership and exact input
states. Outputs come from the current runtime contracts. A missing output blocks
the entire connected component, including all its ordinary migration aliases.
"""
import gzip
import hashlib
import json
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
EVIDENCE = ROOT / 'docs/composite-grid-repair/historical-owner-closures.json.gz'
ORACLE = ROOT / 'docs/composite-grid-repair/protected-world-oracle.json'
POSITIVE = {'EXACT_HISTORICAL_OWNER_CLOSURE', 'HISTORICAL_OWNER_CLOSURE_WITH_OCCLUSION'}
DIM = 'minecraft:overworld'


def state(value):
    name, _, tail = value.partition('[')
    return name, tuple(sorted(tuple(p.split('=', 1)) for p in tail.rstrip(']').split(',') if p))


def connected_groups(evidence, oracle):
    """Union by source owner AND shared observed cell, never by proximity."""
    traces = [t for t in evidence['closures'] if t.get('result') in POSITIVE]
    parent = list(range(len(traces)))
    def find(i):
        while parent[i] != i:
            parent[i] = parent[parent[i]]
            i = parent[i]
        return i
    def union(a, b):
        a, b = find(a), find(b)
        if a != b: parent[max(a, b)] = min(a, b)
    seen = {}
    for i, trace in enumerate(traces):
        for kind, points in [('owner', [o['sourceRoot'] for o in trace['objects']]),
                             ('cell', [c['position'] for c in trace['cells']])]:
            for point in points:
                key = kind, tuple(point)
                if key in seen: union(i, seen[key])
                else: seen[key] = i
    # A protected object may consist of several historical source carriers.
    # Merge its closures even when the carriers do not share rendered cells.
    for row in oracle['occurrences']:
        indices = [seen[('owner', tuple(c['position']))] for c in row['source_cells']
                   if ('owner', tuple(c['position'])) in seen]
        for i in indices[1:]: union(indices[0], i)
    groups = {}
    for i, trace in enumerate(traces):
        group = groups.setdefault(find(i), {'objects': {}, 'cells': {}, 'occurrences': [], 'errors': []})
        for field, identity in [('objects', 'sourceRoot'), ('cells', 'position')]:
            for item in trace[field]:
                key = tuple(item[identity])
                previous = group[field].get(key)
                # Different traces can list subsets of owners for an occluded
                # cell, but never different observed states or owner geometry.
                if previous and (previous['state'] != item['state'] if field == 'objects'
                                 else previous['actual'] != item['actual']):
                    raise ValueError('OWNER_GRAPH_CONTRADICTORY_EVIDENCE')
                group[field][key] = item
    for row in oracle['occurrences']:
        indices = [seen[('owner', tuple(c['position']))] for c in row['source_cells']
                   if ('owner', tuple(c['position'])) in seen]
        if indices: groups[find(indices[0])]['occurrences'].append(row)
    return sorted(groups.values(), key=lambda g: min(g['objects']))


def compile_groups(rules, resources):
    from convert_logical_world import Expected, Output, Rule, add
    from logical_contract_v2 import direct_rules, load_contracts
    raw_bytes = EVIDENCE.read_bytes()
    progress = json.loads((EVIDENCE.parent / 'membership-proof-progress.json').read_bytes())
    if hashlib.sha256(raw_bytes).hexdigest() != progress['evidenceSha256']:
        raise ValueError('OWNER_GRAPH_EVIDENCE_HASH_MISMATCH')
    evidence = json.loads(gzip.decompress(raw_bytes))
    if sum(bool(r['historicalClosureProven']) for r in evidence['occurrences']) != progress['historicalClosureProven']:
        raise ValueError('OWNER_GRAPH_PROVEN_COUNT_MISMATCH')
    for trace in evidence['closures']:
        if trace.get('result') in POSITIVE and (trace.get('errors') or trace.get('moddedSha256') != progress['moddedSha256']):
            raise ValueError('OWNER_GRAPH_INVALID_POSITIVE_TRACE')
    oracle_bytes = ORACLE.read_bytes()
    if hashlib.sha256(oracle_bytes).hexdigest() != evidence['oracleSha256']:
        raise ValueError('OWNER_GRAPH_ORACLE_HASH_MISMATCH')
    oracle = json.loads(oracle_bytes)
    raw_rules, _ = direct_rules(resources)
    contracts, _ = load_contracts(resources)
    families = {f['id']: f for f in contracts['families']}
    runtime_path = resources.parent / 'city/owner-runtime-mappings.json'
    runtime = json.loads(runtime_path.read_bytes()) if runtime_path.exists() else {'states':{}}
    if runtime.get('states') and runtime.get('evidenceSha256') != progress['evidenceSha256']:
        raise ValueError('WHOLE_OWNER_RUNTIME_EVIDENCE_MISMATCH')
    runtime_definitions={b['id']:b for b in json.loads((resources.parent/'city/definitions.json').read_bytes())['blocks']}
    runtime_geometry=json.loads((resources.parent/'city/geometry.json').read_bytes())
    for mapping in runtime['states'].values():
        ident=mapping['id'].split(':')[1]
        definition=runtime_definitions.get(ident,{})
        if not definition.get('whole_owner') or mapping['properties']!={'facing':'north'}:
            raise ValueError('WHOLE_OWNER_RUNTIME_IDENTITY_MISMATCH')
        actual=runtime_geometry['blocks'][ident]['states']['facing=north']
        if {tuple(map(int,p.split(','))) for p in actual['cells']} != {tuple(p) for p in mapping['shape']}:
            raise ValueError('WHOLE_OWNER_RUNTIME_SHAPE_MISMATCH')
    compiled = list(rules)
    diagnostics = []
    for group in connected_groups(evidence, oracle):
        origin = min(group['cells'])
        relative = lambda p: tuple(p[i] - origin[i] for i in range(3))
        ident = 'historical-owner-' + hashlib.sha256(json.dumps(sorted(group['objects'])).encode()).hexdigest()[:20]
        outputs = []
        covered = set()
        errors = []
        for row in group['occurrences']:
            source_roots = {tuple(c['position']) for c in row['source_cells']}
            if not source_roots <= group['objects'].keys():
                errors.append({'reason': 'protected_owner_closure_incomplete', 'origin': row['origin']})
                continue
            if any(group['objects'][tuple(c['position'])]['state'] != c['state'] for c in row['source_cells']):
                errors.append({'reason': 'protected_source_state_mismatch', 'origin': row['origin']})
                continue
            raw = raw_rules[row['rule']]
            for output in raw.outputs or (Output(raw.target, raw.root_offset, raw.shape),):
                root = add(tuple(row['origin']), output.root_offset)
                target, shape = output.target, output.shape
                # The only two approved technical-root exceptions. Full source
                # closure and original root context must both be present.
                from explicit_root_exceptions import CASES, PRESERVED_OTHER
                family = target[0].split(':')[1]
                expected = CASES.get((family, root))
                if expected and dict(target[1]) == {**expected, 'root_anchor': 'canonical'}:
                    new_root = add(root, (0, 1, 0))
                    own_cells = {tuple(c) for p in source_roots for c in group['objects'][p]['cells']}
                    if new_root in own_cells and state(group['cells'].get(root, {}).get('actual', '')) == PRESERVED_OTHER[family]:
                        props = {**expected, 'root_anchor': 'upper'}
                        key = ','.join(k+'='+v for k,v in sorted(props.items()))
                        shifted = frozenset(tuple(c) for c in families[family]['states'][key]['physical_footprint']['cells'])
                        if not {add(c, (0,1,0)) for c in shifted} <= shape:
                            raise ValueError('ATOMIC_ROOT_EXCEPTION_EXPANDS_PHYSICS')
                        root, target, shape = new_root, (target[0], tuple(sorted(props.items()))), shifted
                outputs.append(Output(target, relative(root), shape))
            covered.update(source_roots)
        for point in sorted(group['objects'].keys() - covered):
            mapping = runtime['states'].get(group['objects'][point]['state'])
            if mapping:
                outputs.append(Output((mapping['id'],tuple(sorted(mapping['properties'].items()))),
                                      relative(point),frozenset(tuple(c) for c in mapping['shape'])))
                covered.add(point)
        unsupported = sorted(group['objects'].keys() - covered)
        if unsupported:
            errors.append({'reason': 'whole_owner_runtime_mapping_missing',
                           'owners': [{'root': p, 'state': group['objects'][p]['state']} for p in unsupported]})
        outputs = tuple(dict.fromkeys(outputs))
        pieces = tuple(Expected(relative(p), state(c['actual'])) for p,c in sorted(group['cells'].items()))
        if not pieces: raise ValueError('EMPTY_OWNER_GRAPH')
        # A blocked Rule is still a reservation; never fabricate a target.
        target = outputs[0].target if outputs else pieces[0].state
        first_shape = outputs[0].shape if outputs else frozenset({(0,0,0)})
        first_offset = outputs[0].root_offset if outputs else (0,0,0)
        compiled.append(Rule(len(compiled), pieces[0], target, first_offset,
            pieces[1:], None, first_shape, transaction_id=ident, outputs=outputs,
            source_mode='modded', source_reference='frozen historical owner graph',
            allowed_origins=((DIM, origin),), atomic_owner_group=True,
            preflight_error=errors[0]['reason'] if errors else None))
        diagnostics.append({'transaction': ident, 'origin': origin, 'owners': len(group['objects']),
            'cells': len(pieces), 'outputs': len(outputs), 'errors': errors,
            'occurrences': [{'rule': r['rule'], 'origin': r['origin']} for r in group['occurrences']]})
    return compiled, {'evidenceSha256': progress['evidenceSha256'], 'groups': diagnostics,
                      'historicalMembershipsProven': progress['historicalClosureProven']}


def reservations(rules):
    from convert_logical_world import add
    result = {}
    for rule in rules:
        if not rule.atomic_owner_group: continue
        for dim, origin in rule.allowed_origins:
            for piece in (rule.source,) + rule.members:
                key = dim, add(origin, piece.offset)
                if key in result and result[key] != rule.transaction_id:
                    raise ValueError('UNMERGED_SHARED_OWNER_GROUP')
                result[key] = rule.transaction_id
    return result
