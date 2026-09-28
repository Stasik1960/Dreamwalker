"""Independent original-census and current-owner coverage check.

No successful conversion ledger or writer planner is imported. A root alone
cannot pass: the current contract and every NBT owner edge must agree.
"""
from __future__ import annotations
import argparse
import gzip
import hashlib
import json
import tempfile
from collections import Counter, defaultdict
from pathlib import Path
from convert_logical_world import World, safe_extract
from world_io import TAG_COMPOUND, TAG_LIST, TAG_LONG, TAG_STRING, Tag, compound
from source_variant_rng import guards_match

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_RESOURCES = ROOT/'src/main/resources/bloodborne_blocks/logical'
ORACLE = ROOT/'docs/composite-grid-repair/protected-world-oracle.json'
PART = 'bloodborne_blocks:architecture_part'
SOURCE_SHA = '4353737d536677469d3b895e3515496ab64fab7b224e43428eb96e8c09724a51'
BASELINE = ROOT/'releases/Bloodborne-Blocks/2.1.0-rc.2/Bloodborne-City-2.1.0-rc.2.zip'
BASELINE_SHA = 'f4b9ef510e2aacade80bb11f95cd82fe17eaed56e118280e8e055dd4aecd133c'
STATUSES = ('alreadyCorrect', 'restored', 'trulyAbsent', 'superseded', 'unresolvedKnown', 'genuinelyUnknown')


def parse(value):
    name, _, props = value.partition('[')
    return name, dict(piece.split('=', 1) for piece in props.rstrip(']').split(',') if piece)


def text(value):
    if value is None or isinstance(value, str):
        return value
    name, props = value
    return name + ('[' + ','.join(k+'='+v for k,v in sorted(props)) + ']' if props else '')


def position(value):
    value &= (1 << 64)-1
    x, y, z = value >> 38, value & 4095, value >> 12 & 0x3ffffff
    return (x-(1 << 26) if x >= 1 << 25 else x,
            y-4096 if y >= 2048 else y, z-(1 << 26) if z >= 1 << 25 else z)


def read_bindings(tag):
    if tag is None:
        return ()
    data = compound(tag)
    if data.get('id') != Tag(TAG_STRING, PART) or set(data)-{'id','x','y','z','Root','Owner','Owners','keepPacked'}:
        raise ValueError('foreign_or_custom_block_entity')
    rows = data.get('Owners')
    if rows is not None and (rows.type != TAG_LIST or rows.list_type != TAG_COMPOUND):
        raise ValueError('invalid_owners_list')
    values = [compound(v) for v in rows.value] if rows is not None else [data]
    if not 1 <= len(values) <= 16:
        raise ValueError('invalid_owner_count')
    result = []
    for row in values:
        if row.get('Root') is None or row['Root'].type != TAG_LONG or row.get('Owner') is None or row['Owner'].type != TAG_STRING:
            raise ValueError('invalid_owner_types')
        result.append((row['Owner'].value, position(row['Root'].value)))
    if len(result) != len(set(result)):
        raise ValueError('duplicate_owner_binding')
    if (data.get('Root'), data.get('Owner')) != (values[0]['Root'], values[0]['Owner']):
        raise ValueError('inconsistent_primary_owner')
    return tuple(result)


class Runtime:
    def __init__(self, resources=DEFAULT_RESOURCES):
        self.definitions = {f'bloodborne_blocks:{d["id"]}': d for folder in (resources.parent/'city', resources)
            for d in json.loads((folder/'definitions.json').read_bytes())['blocks']}
        physical = json.loads((resources/'physical-footprints.json').read_bytes())['families']
        self.shapes = {(f'bloodborne_blocks:{ident}', key): {tuple(p) for p in spec['cells']} | {(0,0,0)}
            for ident, states in physical.items() for key, spec in states.items()}
        contracts = json.loads((resources/'contracts-v2.json').read_bytes())
        self.contracts = {f['id']: f for f in contracts['families']}
        from convert_logical_world import parse_geometry
        self.city_shapes=parse_geometry(resources.parent/'city',{})
        walls=json.loads((resources.parent/'city/reviewed-wall-family.json').read_bytes())
        self.wall_successors=walls['aliasStates']
        self.wall_aliases=set(walls['aliases'])
        wall='bloodborne_blocks:building_stone_brick_wall'
        geometry=json.loads((resources.parent/'city/geometry.json').read_bytes())
        self.wall_signatures={key:(model,geometry['blocks']['building_stone_brick_wall']['states'][key])
            for key,model in self.definitions[wall]['models'].items()}
        for family in contracts['families']:
            for key,spec in family['states'].items():
                shift=spec.get('technical_root_offset')
                if shift is not None:
                    if family['id'] not in {'o_bench','o_high_balustrade'} or shift != [0,1,0] or 'root_anchor=upper' not in key:
                        raise ValueError('unapproved_technical_root')
                    mask_key=('bloodborne_blocks:'+family['id'],key)
                    # The old canonical root cell belongs to retained context;
                    # the explicit upper-root exception must not reclaim it.
                    self.shapes[mask_key]={(x,y-1,z) for x,y,z in self.shapes[mask_key] if (x,y,z)!=(0,0,0)}
        # The user explicitly accepted placed rc.2 window orientations. Read
        # that immutable baseline, never infer correctness from current output.
        from composite_world_oracle import EvidenceReader
        if hashlib.sha256(BASELINE.read_bytes()).hexdigest() != BASELINE_SHA:
            raise ValueError('accepted_baseline_hash_mismatch')
        self.accepted_windows = {}
        reader = EvidenceReader(BASELINE)
        try:
            for row in json.loads(ORACLE.read_bytes())['occurrences']:
                for output in row['outputs']:
                    if output['family'] != 'o_shuttered_window':
                        continue
                    p = tuple(output['canonical_root'])
                    value = reader.state(row['dimension'],p)[1]
                    if value and parse(value)[0] == 'bloodborne_blocks:o_shuttered_window':
                        self.accepted_windows[(row['dimension'],p)] = value
        finally:
            reader.close()

    def normalized(self, value):
        if value is None:
            return None
        name, props = parse(text(value))
        return name, tuple(sorted({**self.definitions.get(name, {}).get('default', {}), **props}.items()))

    def shape(self, value):
        normalized = self.normalized(value)
        if normalized is None:
            return None
        name, props = normalized
        return self.shapes.get((name, ','.join(k+'='+v for k,v in props)), getattr(self,'city_shapes',{}).get(normalized))

    def equivalent_state(self, first, second):
        """Wall masks may encode the same installed pose with another facing.

        Require both the exact existing mesh and the entire geometry profile;
        visual similarity alone never establishes functional equivalence.
        """
        if first==second:return True
        wall='bloodborne_blocks:building_stone_brick_wall'
        if first is None or second is None or first[0]!=wall or second[0]!=wall:return False
        key=lambda value: ','.join(k+'='+v for k,v in value[1])
        signatures=getattr(self,'wall_signatures',{})
        a,b=signatures.get(key(first)),signatures.get(key(second))
        return a is not None and b is not None and a==b

    def current_target(self, row, output):
        """Resolve a frozen source occurrence against current migration patterns.

        The oracle's rendered target is deliberately ignored.  A target is
        usable only when one current state has the exact source cells at the
        row origin; ambiguity and missing patterns remain known unresolved.
        """
        if row.get('source_kind')=='rc2_reviewed_wall_alias':
            source=self.normalized(row['source_cells'][0]['state'])
            successor=self.wall_successors.get(source[0].split(':',1)[1]+'|'+','.join(k+'='+v for k,v in source[1]))
            if successor is None or output['family']!='building_stone_brick_wall':
                return None,'reviewed_wall_successor_missing'
            return 'bloodborne_blocks:building_stone_brick_wall[connection='+successor['connection']+',facing='+successor['facing']+']',None
        family = self.contracts.get(output['family'])
        if family is None:
            return None, 'current_family_missing'
        origin = tuple(row['origin'])
        source = {(tuple(c['position'][i] - origin[i] for i in range(3))):
                  self.normalized(c['state']) for c in row.get('source_cells', ())}
        matches = []
        for state, spec in family.get('states', {}).items():
            for pattern in spec.get('migration_source_pattern', ()):
                components = pattern.get('components', ())
                wanted = {tuple(c['offset']): self.normalized(c['id'] +
                    ('[' + ','.join(k+'='+v for k,v in sorted(c.get('properties', {}).items())) + ']'
                     if c.get('properties') else '')) for c in components}
                if len(wanted) != len(source) or wanted.keys() != source.keys():
                    continue
                if all(source[offset] == value for offset, value in wanted.items()):
                    if not guards_match(pattern.get('variant_guards', ()), origin):
                        continue
                    matches.append((state, pattern))
        if len(matches) != 1:
            return None, 'current_pattern_' + ('missing' if not matches else 'ambiguous')
        state, pattern = matches[0]
        props = dict(piece.split('=', 1) for piece in state.split(',') if piece)
        transaction = pattern.get('split_transaction')
        if transaction:
            declarations = [d for d in transaction.get('outputs', ())
                            if d.get('family') == output['family'] and
                            tuple(origin[i] + d['root_offset'][i] for i in range(3)) == tuple(output.get('canonical_root', ())) ]
            if len(declarations) != 1:
                return None, 'current_split_output_missing'
            props.update(declarations[0].get('properties', {}))
            root = tuple(origin[i] + declarations[0]['root_offset'][i] for i in range(3))
            if tuple(output.get('canonical_root', ())) != root:
                return None, 'current_canonical_root_mismatch'
        target = 'bloodborne_blocks:' + output['family'] + '[' + ','.join(k+'='+v for k,v in sorted(props.items())) + ']'
        if output['family'] == 'o_shuttered_window':
            target = getattr(self,'accepted_windows',{}).get((row['dimension'],tuple(output['canonical_root'])),target)
        return target, None


class Observed:
    def __init__(self, world):
        self.world = world
        self.entities = world.block_entities()
        self.owners = defaultdict(set)
        self.binding_errors = []
        for (dim, *p), tag in self.entities.items():
            if compound(tag).get('id') != Tag(TAG_STRING, PART):
                continue
            try:
                for owner, root in read_bindings(tag):
                    self.owners[(dim, owner, root)].add(tuple(p))
            except ValueError as exc:
                self.binding_errors.append({'dimension': dim, 'position': p, 'reason': str(exc)})

    def get(self, dim, p):
        return text(self.world.get(dim, tuple(p)))


def classify_output(output, dimension, observed, runtime):
    root = tuple(output['canonical_root'])
    expected = runtime.normalized(output['expected_logical_state'])
    actual = runtime.normalized(observed.get(dimension, root))
    errors = []
    if actual != expected and not (hasattr(runtime,'equivalent_state') and runtime.equivalent_state(actual,expected)):
        errors.append({'reason': 'logical_state_mismatch', 'actual': text(actual), 'expected': text(expected)})
    shape = runtime.shape(expected)
    if shape is None:
        errors.append({'reason': 'required_current_contract_missing'})
        return errors
    name = expected[0]
    wanted = {tuple(root[i]+d[i] for i in range(3)) for d in shape if d != (0,0,0)}
    actual_members = observed.owners.get((dimension, name, root), set())
    for p in sorted(wanted):
        try:
            owners = read_bindings(observed.entities.get((dimension, *p)))
            if (name, root) not in owners:
                raise ValueError('required_helper_binding_missing')
            block = observed.get(dimension, p)
            if block is None or block.split('[',1)[0] not in runtime.definitions and block != PART:
                raise ValueError('invalid_helper_carrier')
            for owner, owner_root in owners:
                existing = runtime.normalized(observed.get(dimension, owner_root))
                if existing is None or existing[0] != owner:
                    raise ValueError('orphan_helper_binding')
        except ValueError as exc:
            errors.append({'position': list(p), 'reason': str(exc)})
    if actual_members - wanted:
        errors.append({'reason': 'owner_has_extra_helpers', 'positions': [list(p) for p in sorted(actual_members-wanted)]})
    return errors


def audit_coverage(oracle, observed, runtime, *, before=None, supplemental=()):
    if oracle.get('source_sha256') != SOURCE_SHA:
        raise ValueError('source_census_hash_mismatch')
    rows = list(oracle['occurrences']) + list(supplemental)
    if before is not None and hasattr(runtime,'wall_aliases'):
        rows.extend(wall_candidates(before,runtime))
    counts = {ident: Counter({key: 0 for key in ('candidates',)+STATUSES}) for ident in oracle['families']}
    residuals, results = [], []
    for index, row in enumerate(rows):
        if 'outputs' not in row and 'family' in row and 'canonical_root' in row:
            row={**row,'outputs':[{'family':row['family'],'canonical_root':row['canonical_root']}]}
        for output in row['outputs']:
            family = output['family']
            family_counts = counts.setdefault(family, Counter({key: 0 for key in ('candidates',)+STATUSES}))
            family_counts['candidates'] += 1
            if hasattr(runtime, 'contracts'):
                target, target_error = runtime.current_target(row, output)
            else:  # lightweight test runtimes retain the legacy oracle path
                target, target_error = output['expected_logical_state'], None
            checked = dict(output)
            if target is not None:
                checked['expected_logical_state'] = target
            errors = ([{'reason': target_error}] if target_error else
                      classify_output(checked, row['dimension'], observed, runtime))
            result = 'unresolvedKnown' if errors else 'alreadyCorrect'
            if not errors and before is not None and classify_output(checked, row['dimension'], before, runtime):
                result = 'restored'
            family_counts[result] += 1
            item = {'occurrence': index, 'rule': row.get('rule'), 'family': family, 'dimension': row['dimension'],
                    'position': output['canonical_root'], 'result': result}
            results.append(item)
            if errors:
                residuals.append({**item, 'errors': errors})
    # Superseded matches are separate exact (rule, origin) records. A surviving
    # different rule at the same origin is never excluded.
    for row in oracle.get('superseded_matches', ()):
        for family in row['families']:
            counts[family]['superseded'] += 1
    total = Counter()
    for value in counts.values():
        total.update(value)
    return {'format': 'bloodborne-independent-complete-coverage-v1',
        'scope': 'all frozen source census outputs plus explicit supplemental candidates; no success-ledger selection',
        'candidateScopeComplete': False, 'uncoveredDiscoveryScopes': ['additional authored vegetation outside the pinned 3856-instance grass census',
            'authored gathered graves outside explicit C1491/C002', 'whole lantern post/bracket'],
        'counts': dict(total), 'families': {k: dict(v) for k,v in sorted(counts.items())},
        'results': results, 'residuals': residuals, 'helperBindingErrors': observed.binding_errors,
        'knownCensusCoverage': 'PASS' if total['candidates'] > 0 and not residuals and not observed.binding_errors else 'FAIL',
        'coverageCompleteness': 'FAIL'}


def wall_candidates(before,runtime):
    """All actually placed old wall aliases, independently of converter output."""
    import numpy as np
    from world_io import section_blocks,block_state_key
    for chunk in before.world.chunks.values():
        for section in chunk.root().get('sections',Tag(TAG_LIST,[],TAG_COMPOUND)).value:
            raw=compound(section).get('block_states')
            if raw is None:continue
            palette=compound(raw)['palette'].value
            selected={i:block_state_key(tag) for i,tag in enumerate(palette)
                if compound(tag)['Name'].value.removeprefix('bloodborne_blocks:') in runtime.wall_aliases}
            if not selected:continue
            _,indices=section_blocks(section);indices=np.asarray(indices);sy=int(compound(section)['Y'].value)
            for i,value in selected.items():
                for flat in np.flatnonzero(indices==i):
                    flat=int(flat);p=[chunk.x*16+(flat&15),sy*16+(flat>>8),chunk.z*16+((flat>>4)&15)]
                    yield {'source_kind':'rc2_reviewed_wall_alias','dimension':chunk.dimension,'origin':p,
                        'source_cells':[{'position':p,'state':value}],
                        'outputs':[{'family':'building_stone_brick_wall','canonical_root':p}]}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--oracle', type=Path, default=ORACLE)
    parser.add_argument('--current', type=Path, required=True)
    parser.add_argument('--before-world', type=Path)
    parser.add_argument('--current-contracts', type=Path, default=DEFAULT_RESOURCES)
    parser.add_argument('--supplemental', type=Path)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    with tempfile.TemporaryDirectory(prefix='accepted-coverage-') as directory:
        def load(path, name):
            root = safe_extract(path, Path(directory)/name) if path.is_file() else path
            return Observed(World(root, {}))
        print('Loading current world for independent coverage', flush=True)
        observed = load(args.current, 'current')
        before = load(args.before_world, 'before') if args.before_world else None
        supplemental = ()
        if args.supplemental:
            raw = args.supplemental.read_bytes()
            supplemental = json.loads(gzip.decompress(raw) if args.supplemental.suffix == '.gz' else raw)
            if isinstance(supplemental, dict):
                if supplemental.get('source_sha256') != SOURCE_SHA:
                    raise ValueError('supplemental_source_hash_mismatch')
                supplemental = supplemental['occurrences']
        report = audit_coverage(json.loads(args.oracle.read_bytes()), observed, Runtime(args.current_contracts), before=before, supplemental=supplemental)
        report['sourceCensusSha256'] = hashlib.sha256(args.oracle.read_bytes()).hexdigest()
        report['acceptedWindowBaselineSha256'] = BASELINE_SHA
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(report, ensure_ascii=False, sort_keys=True, separators=(',', ':'))+'\n', encoding='utf8')
        print(json.dumps(report['counts']), flush=True)


if __name__ == '__main__':
    main()
