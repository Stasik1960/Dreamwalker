"""Read only the finite amendment's actual mapped cells after FULL compilation.

The original wide/physics-only raw input is intentionally not compiled. Expected
physical ingredients come from its saved beforeimages and the unchanged source
fragment bank. Existing compiler functions explain final opaque-cube culling.
"""
import argparse
import hashlib
import json
import os
import sqlite3
import sys
from pathlib import Path
from types import SimpleNamespace

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'compiler'))
from catalog import (CatalogCompiler, EMPTY_OPTICS, SHAPE_FIELDS, canonical_boxes, compiler_fingerprint,
                     normalize_fluid, normalize_optics, opaque_cube, unique_quads)
from export_cells import FragmentStore, TextureOpacity, read_lines, state_key
from catalog_normalize_carriers import audit_states
from full_jar_compatibility import binding, checked, require, sha


PHYSICAL_KEYS = ('baseState', 'fragments', 'optics', 'fluid', 'localFunction',
                 'wrapper', 'carrierPredicates')
UNIT = [[0.0, 0.0, 0.0, 1.0, 1.0, 1.0]]


def canonical(value):
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(',', ':'))


def carrier_split(reference, amendment_ref, amendment, chain=()):
    identity = (str(Path(reference['path']).resolve()), reference['sha256'])
    require(len(chain) < 16 and identity not in chain, 'Carrier split chain must be bounded and noncyclic')
    split = checked(reference)
    require(split['status'] == 'PASS' and split['kind'] == 'COMMITTED_POSTFINAL_CARRIER_PREDICATE_SPLIT' and
            split['sourceBinding'] == amendment['sourceBinding'] and
            split['priorNativeVisualAmendment'] == amendment_ref and
            split['geometryPhysicalDataUnchanged'] is True and
            split['onlyObservedOpaqueAndSidedTransparencyChanged'] is True,
            'Exact committed carrier-only continuation of this amendment required')
    prior_cases, prior_refs = {}, []
    previous_ref = split.get('priorCarrierPredicateSplit')
    if previous_ref:
        previous, prior_cases, prior_refs = carrier_split(previous_ref, amendment_ref, amendment, (*chain, identity))
        require(split['beforeGeneration'] == previous['afterGeneration'], 'Carrier split generation chain is discontinuous')
    else:
        require(split['beforeGeneration'] == amendment['afterGeneration'], 'First split is not the exact visual amendment generation')
    references = [*prior_refs, reference, split['beforeRaw'], split['evidence']['conflicts']]
    binding(split['beforeRaw']['path'], split['beforeRaw']['sha256'])
    require(split['beforeRaw']['sha256'] == split['beforeGeneration']['compositionsSha256'] and
            split['afterGeneration']['sourceFragments'] == amendment['stableState']['poseFragments'],
            'Carrier split changed its original raw/geometry authority')
    facts = checked(split['evidence']['conflicts'])
    require(facts['status'] == 'PASS_SOURCE_FACTS_NOT_ADMISSION' and
            facts['kind'] in ('SHARED_CARRIER_PREDICATE_CONFLICTS', 'UNQUALIFIED_CARRIER_PREDICATE_BINDINGS') and
            facts['sourceBinding'] == split['sourceBinding'] and facts['generation'] == split['beforeGeneration'] and
            facts['raw']['sha256'] == split['beforeRaw']['sha256'], 'Exact pre-split bridge facts required')
    for ref in facts['audits']:
        binding(ref['path'], ref['sha256'])
        references.append(ref)
    observed, _ = audit_states([ref['path'] for ref in facts['audits']])
    bodies = {}
    require(0 < len(split['compositionChanges']) <= 4096, 'Finite carrier composition bound exceeded')
    for row in split['compositionChanges']:
        before, after, predicates = row['before'], row['after'], row['carrierPredicates']
        require(set(predicates) == {'opaque', 'sidedTransparency'} and
                all(type(value) is bool for value in predicates.values()) and
                before.get('wrapper') == 'NativeGridBase' and before.get('optics') is None and
                before.get('carrierPredicates') is None and after.get('carrierPredicates') == predicates and
                {key: value for key, value in before.items() if key != 'carrierPredicates'} ==
                {key: value for key, value in after.items() if key != 'carrierPredicates'} and
                all(row[key] == 'cell_' + hashlib.sha256(canonical(body).encode()).hexdigest()
                    for key, body in (('oldKey', before), ('newKey', after))),
                'Carrier clone changed geometry/optics/data or its exact content key')
        token = (row['oldKey'], canonical(predicates))
        require(token not in bodies, 'Duplicate carrier clone')
        bodies[token] = row
    expected = {}
    used = set()
    for conflict in facts['conflicts']:
        for group in conflict['groups']:
            clone_key = (conflict['key'], canonical(group['predicates']))
            require(clone_key in bodies and bodies[clone_key]['before'] == conflict['composition'],
                    'A carrier clone lost its exact accepted old composition')
            clone = bodies[clone_key]
            used.add(clone_key)
            for placement in group['placements']:
                token = (placement['dim'], placement['x'], placement['y'], placement['z'])
                host = json.loads(placement['host_state'])
                require(token not in expected and observed.get(state_key(host)) == group['predicates'],
                        'Duplicate placement or unobserved carrier host predicates')
                expected[token] = {'oldComposition': conflict['key'], 'newComposition': clone['newKey'],
                                   'hostState': host, 'carrierPredicates': group['predicates'],
                                   'before': clone['before'], 'after': clone['after']}
    require(set(bodies) == used and len(expected) == facts['affectedPlacements'] == len(split['changes']) and
            0 < len(expected) <= 32768, 'Carrier split scope differs from the finite observed conflicts')
    actual = {}
    for row in split['changes']:
        token = (row['dimension'], *row['position'])
        require(token not in actual and token in expected and
                all(row[key] == expected[token][key] for key in
                    ('oldComposition', 'newComposition', 'hostState', 'carrierPredicates')),
                'Carrier split placement/host differs from its bound observation')
        actual[token] = row
    require(set(actual) == set(expected), 'Carrier split placement scope is incomplete')
    require(not set(prior_cases).intersection(expected) and len(prior_cases) + len(expected) <= 32768,
            'Carrier split stages overlap or exceed the finite combined placement bound')
    return split, {**prior_cases, **expected}, references


def ingredients(row, store):
    base = row.get('baseState')
    native = ({'quads': [], **{name: [] for name in SHAPE_FIELDS},
               'optics': dict(EMPTY_OPTICS), 'fluid': None}
              if base is None or base['registryId'] in
              ('minecraft:air', 'minecraft:cave_air', 'minecraft:void_air')
              else store.native(state_key(base)))
    guests = [store.fragment(item['pose'], tuple(item['cell']))[1]
              for item in row.get('fragments', [])]
    shapes = {name: canonical_boxes([*native.get(name, []),
              *[box for guest in guests for box in guest.get(name, [])]])
              for name in SHAPE_FIELDS}
    optics = normalize_optics(row.get('optics') if row.get('optics') is not None
                              else native.get('optics'))
    if row.get('optics') is None:
        for name in ('opacity', 'luminance'):
            optics[name] = max([optics[name], *[guest.get('optics', {}).get(name, 0)
                                              for guest in guests]])
    fluid = normalize_fluid(row['fluid']) if 'fluid' in row else normalize_fluid(native.get('fluid'))
    if 'fluid' not in row and base is None:
        fluids = {json.dumps(normalize_fluid(guest['fluid']), sort_keys=True)
                  for guest in guests if guest.get('fluid') is not None}
        require(len(fluids) <= 1, 'Conflicting ordinary source fluids')
        if fluids:
            fluid = json.loads(next(iter(fluids)))
    function = row.get('localFunction')
    if function in (None, 'none'):
        function = {'kind': 'climb'} if shapes['climbing'] else None
    elif function == 'climb':
        function = {'kind': 'climb'}
    require(function in (None, {'kind': 'climb'}), 'Unknown physical local function')
    return shapes, optics, fluid, function


def final_quads(row, store, opacity):
    base, mode = row.get('baseState'), row.get('baseVisualMode', 'native')
    native = []
    if mode in ('native', 'nativePlusSubstrateFragments') and base and base['registryId'] not in (
            'minecraft:air', 'minecraft:cave_air', 'minecraft:void_air'):
        # Only this existing pure read helper is called; no compiler DB/init.
        native = CatalogCompiler._native_quads.__wrapped__(
            SimpleNamespace(store=store, opacity=opacity), state_key(base))
    substrate = [quad for item in row.get('substrateFragments', [])
                 for quad in store.fragment(item['pose'], tuple(item['cell']))[1].get('quads', [])]
    substrate += [quad for item in row.get('substrateNativeFragments', [])
                  for quad in store.native_fragment(state_key(item['state']), tuple(item['cell']))[1].get('quads', [])]
    if mode == 'substrateFragments':
        native = substrate
    elif mode == 'nativePlusSubstrateFragments':
        native = [*native, *substrate]
    else:
        require(mode == 'native' and not substrate, 'Unknown final visual route')
    return unique_quads([*native, *[quad for item in row.get('fragments', [])
                         for quad in store.fragment(item['pose'], tuple(item['cell']))[1].get('quads', [])]])


def main():
    p = argparse.ArgumentParser(description=__doc__)
    for name in ('amendment', 'frozen', 'plan', 'textures', 'out'):
        p.add_argument('--' + name, type=Path, required=True)
    p.add_argument('--amendment-sha256', required=True)
    p.add_argument('--frozen-sha256', required=True)
    p.add_argument('--carrier-predicate-split', type=Path)
    p.add_argument('--carrier-predicate-split-sha256')
    p.add_argument('--whole-original-retention', type=Path)
    p.add_argument('--whole-original-retention-sha256')
    a = p.parse_args()
    require(not a.out.exists(), 'Fresh finite compiled-cell evidence required')
    amendment_ref = binding(a.amendment, a.amendment_sha256)
    freeze_ref = binding(a.frozen, a.frozen_sha256)
    receipt, frozen = checked(amendment_ref), checked(freeze_ref)
    require(receipt['status'] == 'PASS' and receipt['kind'] == 'COMMITTED_POSTFINAL_NATIVE_VISUAL_AMENDMENT',
            'Exact committed amendment receipt required')
    require(bool(a.carrier_predicate_split) == bool(a.carrier_predicate_split_sha256),
            'Carrier split path and SHA must be supplied together')
    split_ref, split, split_cases, split_refs = None, None, {}, []
    if a.carrier_predicate_split:
        split_ref = binding(a.carrier_predicate_split, a.carrier_predicate_split_sha256)
        split, split_cases, split_refs = carrier_split(split_ref, amendment_ref, receipt)
    expected_generation = split['afterGeneration'] if split else receipt['afterGeneration']
    require(bool(a.whole_original_retention) == bool(a.whole_original_retention_sha256),
            'Whole retention path and SHA must be supplied together')
    whole_ref, whole, retired_points, whole_refs = None, None, set(), []
    if a.whole_original_retention:
        whole_ref = binding(a.whole_original_retention, a.whole_original_retention_sha256)
        whole = checked(whole_ref)
        require(whole['status'] == 'PASS' and whole['kind'] == 'COMMITTED_POSTFINAL_WHOLE_ORIGINAL_RETENTION' and
                whole['sourceBinding'] == receipt['sourceBinding'] and whole['beforeGeneration'] == expected_generation and
                whole['priorCarrierPredicateSplit'] == split_ref and
                all(whole[name] is True for name in ('objectsKindsUnchanged', 'allSourceDataUnchanged',
                    'onlyCompleteUndemandedDerivedWorkRetired', 'rawAndNativeNeededBytesUnchanged',
                    'ordinaryCompositionKeysUnchanged')) and
                {key: value for key, value in whole['afterGeneration'].items()
                 if key != 'postfinalWholeOriginalRetention'} == expected_generation,
                'Exact metadata-only original-whole continuation required')
        facts = checked(whole['evidence']['facts'])
        require(facts['status'] == 'PASS_SOURCE_FACTS_NOT_ADMISSION' and
                facts['kind'] == 'FINITE_POSTFINAL_WHOLE_ORIGINAL_DOMAIN_FACTS' and
                facts['sourceBinding'] == receipt['sourceBinding'] and
                facts['stableState']['compositionGeneration'] == expected_generation and
                not facts['directMemberDemandOwners'], 'Whole continuation lacks its exact finite original-domain facts')
        retired_points = {(owner['object']['dim'], *point['position'])
                          for owner in facts['owners'] for point in owner['points']}
        require(0 < len(retired_points) <= 32768 and retired_points ==
                {(row['dimension'], *row['position']) for row in whole['newEvent']['data']['pointPlan']} and
                whole['afterState']['sourceLocalRetentionJournal'] == whole['afterJournal'] and
                whole['newEvent']['digest'] == whole['afterJournal']['head'],
                'Whole continuation journal/retired-point scope differs')
        whole_refs = [whole_ref, whole['beforeRaw'], *whole['evidence'].values()]
        require(whole['beforeRaw']['sha256'] == expected_generation['compositionsSha256'] and
                len(whole['derivedFiles']) == 2 and
                {Path(row['target']).name for row in whole['derivedFiles']} ==
                {'compositions.jsonl', 'native-states-needed.json'} and
                all(row['beforeSha256'] == row['proposed']['sha256'] for row in whole['derivedFiles']),
                'Whole retirement changed ordinary raw/native-needed bytes')
        whole_refs.extend(row['proposed'] for row in whole['derivedFiles'])
        for reference in whole_refs:
            binding(reference['path'], reference['sha256'])
        expected_generation = whole['afterGeneration']
    require(frozen['status'] == 'FROZEN_RESOURCES_READY' and
            frozen['kind'] == 'COMPLETED_FULL_CATALOG_AUTHORING_HANDOFF', 'Actual FULL freeze required')
    append = checked(frozen['inputBinding'])
    require(append == frozen['inputBindings'] and append['sourceBinding'] == receipt['sourceBinding'],
            'FULL append source binding differs')
    require(append['compilerAlgorithmSha256'] == compiler_fingerprint(),
            'Use the exact compiler that produced the frozen FULL cells')
    inputs = append['inputs']
    require(inputs['compositions']['sha256'] == expected_generation['compositionsSha256'] and
            inputs['fragments'] == receipt['stableState']['poseFragments'], 'Final raw/fragment binding differs')
    normalizer = checked(inputs['normalizer_receipt'])
    require(normalizer['status'] == 'PASS' and normalizer['onlyOpaqueAndSidedTransparencyOverridden'] is True and
            normalizer['physicalGeometryFluidLocalFunctionUnchanged'] is True and
            normalizer['rawCompositionsSha256'] == inputs['compositions']['sha256'] and
            normalizer['normalizedCompositionsSha256'] == inputs['normalized']['sha256'],
            'Exact existing optical normalizer lineage required')
    catalog = checked(frozen['catalog'])
    db = sqlite3.connect('file:' + (a.plan.resolve() / 'plan.sqlite').as_posix() + '?mode=ro', uri=True)
    db.row_factory = sqlite3.Row
    db.execute('PRAGMA query_only=ON'); db.execute('BEGIN')
    store = FragmentStore(inputs['fragments']['path'])
    store.database.execute('BEGIN')
    failures, culling_changes, flags_changed, targets = [], [], 0, []
    try:
        meta = {key: json.loads(value) for key, value in db.execute(
            "SELECT key,value FROM meta WHERE key IN ('postfinalNativeVisualAmendment','compositionGeneration','postfinalCarrierPredicateSplit','postfinalWholeOriginalRetention','sourceLocalRetentionJournal')")}
        require(meta['postfinalNativeVisualAmendment'] == amendment_ref and
                meta['compositionGeneration'] == expected_generation and
                (split is None or meta.get('postfinalCarrierPredicateSplit') == split_ref),
                'Receipt/explicit continuation is not the current committed generation')
        require(whole is None or meta.get('postfinalWholeOriginalRetention') == whole_ref and
                meta.get('sourceLocalRetentionJournal') == whole['afterJournal'],
                'Original-whole receipt is not the current committed journal/generation')
        candidates = {(row['dimension'], *row['position']): row for row in receipt['beforeImages']}
        require(len(candidates) == len(receipt['beforeImages']) <= 32770 and receipt['affectedCells'] <= 32768,
                'Original finite amendment bound exceeded')
        for token, case in split_cases.items():
            if token not in candidates:
                candidates[token] = {'dimension': token[0], 'position': list(token[1:]),
                                     'composition': case['before'], 'point': {'placements': [case['oldComposition']]}}
        require(not retired_points.intersection(candidates),
                'Original-whole retirement intersects compiled-check targets; no missing-target exemption is allowed')
        for token, before in candidates.items():
            rows = list(db.execute('SELECT composition,type_index FROM placements WHERE dim=? AND x=? AND y=? AND z=?',
                                   (before['dimension'], *before['position'])))
            if not rows:
                require(not before['point']['placements'] and any(
                    row['dimension'] == before['dimension'] and row['position'] == before['position']
                    for row in receipt['exactOmissions']), 'An affected placement disappeared')
                continue
            require(len(rows) == 1 and rows[0]['type_index'] is not None, 'Import actual final types before this check')
            if token in split_cases:
                case = split_cases[token]
                hosts = list(db.execute('SELECT host_state FROM bridge_cells WHERE dim=? AND x=? AND y=? AND z=?', token))
                require(rows[0]['composition'] == case['newComposition'] and len(hosts) == 1 and
                        json.loads(hosts[0][0]) == case['hostState'], 'Current split placement/bridge host differs')
            targets.append((before, rows[0]['composition'], rows[0]['type_index']))
        require(0 < len(targets) <= 32768 and len(targets) <= len(candidates) <= 65538,
                'Finite amendment/split union bound exceeded')
        wanted = {key for _, key, _ in targets}
        raw, normalized = {}, {}
        for reference, destination in ((inputs['compositions'], raw), (inputs['normalized'], normalized)):
            binding(reference['path'], reference['sha256'])
            for row in read_lines(reference['path']):
                if row['key'] in wanted:
                    require(row['key'] not in destination, 'Duplicate affected composition key')
                    destination[row['key']] = row
            require(set(destination) == wanted, 'Final raw/normalized affected keys missing')
        mapped = {}
        for row in read_lines(frozen['mapping']['path']):
            if row['key'] in wanted:
                require(row['key'] not in mapped, 'Duplicate affected mapping key')
                mapped[row['key']] = row['typeIndex']
        require(set(mapped) == wanted, 'Affected final mapping keys missing')
        opacity = TextureOpacity(a.textures)
        for before, key, index in targets:
            require(mapped[key] == index and 0 <= index < len(catalog['cells']), 'Actual mapped cell differs')
            row, final = raw[key], normalized[key]
            old = before['composition']
            case = split_cases.get((before['dimension'], *before['position']))
            if case:
                require({name: value for name, value in row.items() if name != 'key'} == case['after'],
                        'Actual final raw carrier clone differs from the exact split receipt')
            if old is not None:
                require(all(old.get(name) == row.get(name) for name in PHYSICAL_KEYS
                            if name != 'carrierPredicates' or case is None),
                        'Amendment changed an existing beforeimage physical ingredient')
            shapes, optics, fluid, function = ingredients(old if old is not None else row, store)
            normalized_shapes, final_optics, normalized_fluid, normalized_function = ingredients(final, store)
            require(normalized_shapes == shapes and normalized_fluid == fluid and normalized_function == function,
                    'Normalization changed physical ingredients')
            changes = {name for name in optics if optics[name] != final_optics[name]}
            require(not changes or row.get('wrapper') == 'NativeGridBase' and
                    changes <= {'opaque', 'sidedTransparency'}, 'Unapproved optical change/emission/opacity debt')
            flags_changed += bool(changes)
            cell = catalog['cells'][index]
            identity = {'dimension': before['dimension'], 'position': before['position'], 'key': key, 'typeIndex': index}
            for name in ('collision', 'outline', 'climbing'):
                if catalog['shapes'][cell[name]] != shapes[name]:
                    failures.append({**identity, 'field': name})
            for name, actual, expected in (
                    ('optics', catalog['optics'][cell['optics']], final_optics),
                    ('fluid', cell.get('fluid'), fluid), ('localFunction', cell.get('localFunction'), function)):
                if actual != expected:
                    failures.append({**identity, 'field': name})
            actual_culling = catalog['shapes'][cell['culling']]
            if actual_culling != shapes['culling']:
                explained = actual_culling == UNIT and opaque_cube(final_quads(final, store, opacity))
                culling_changes.append({**identity, 'sourceIngredientCulling': shapes['culling'],
                                        'compiledCulling': actual_culling, 'opaqueCubeProof': explained})
                if not explained:
                    failures.append({**identity, 'field': 'culling'})
    finally:
        store.close(); db.close()
    references = [amendment_ref, freeze_ref, frozen['inputBinding'], frozen['catalog'], frozen['mapping'],
                  inputs['compositions'], inputs['normalized'], inputs['fragments'], inputs['normalizer_receipt'],
                  *split_refs, *whole_refs]
    for reference in references:
        binding(reference['path'], reference['sha256'])
    result = {'schemaVersion': 1, 'status': 'PASS_AFFECTED_COMPILED_INGREDIENTS_NOT_GAMEPLAY' if not failures else 'FAIL',
              'kind': 'FINITE_NATIVE_VISUAL_AMENDMENT_COMPILED_CELL_CHECK', 'amendment': amendment_ref,
              'carrierPredicateSplit': split_ref, 'splitMappedPoints': len(split_cases),
              'wholeOriginalRetention': whole_ref, 'disjointRetainedOriginalPoints': len(retired_points),
              'frozenFull': freeze_ref, 'sourceBinding': receipt['sourceBinding'], 'affectedMappedPoints': len(targets),
              'collisionOutlineClimbingFluidLocalFunctionExact': not failures, 'emissionAndOpacityExact': not failures,
              'normalizerObservedFlagPoints': flags_changed, 'cullingChanges': culling_changes,
              'rootCullingObservationRequired': bool(culling_changes), 'failures': failures,
              'beforeRawCompiled': False, 'beforeRawFailureWasExpected': True,
              'newOpticalDebtAuthorized': False, 'existingFullRelightStillRequired': True,
              'databaseWrites': 0, 'worldReads': 0, 'gameplayPass': False, 'producer': binding(__file__)}
    a.out.parent.mkdir(parents=True, exist_ok=True)
    with a.out.open('x', encoding='utf-8', newline='\n') as stream:
        stream.write(json.dumps(result, ensure_ascii=False, indent=2) + '\n'); stream.flush(); os.fsync(stream.fileno())
    require(not failures, 'Affected compiled physical/optical/culling differences: ' + str(len(failures)))
    print(json.dumps({'status': result['status'], 'points': len(targets), 'cullingChanges': len(culling_changes),
                      'report': binding(a.out)}, ensure_ascii=False))


if __name__ == '__main__':
    main()
