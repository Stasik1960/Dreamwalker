"""Author one ACTUAL failed old-native HoldStatue contact, without changing SQL.

Supports only the accepted visual-protection correction and W-only template
fallback DATA formats. Unknown or conflicting source facts produce BLOCKED.
Run through Guard only after the root confirms that its plan writer is idle.
This module is inert on import. It never opens an original world or runs Java.
"""
import argparse, ast, collections, copy, gzip, hashlib, importlib.util, json
import re, sqlite3, sys, traceback, zlib
from pathlib import Path

sys.dont_write_bytecode = True
SOURCE = Path(__file__).resolve().parents[3]
RESEARCH = SOURCE.parent / 'research'
HISTORY = RESEARCH / 'native-grid-plan/full'
SHARED = HISTORY / 'prove-native-overhang-002609-batch.py'
SHARED_SHA = '5ec2e72dd9c87e1fa41f5223a8cf4bc0325a085a57bfe43bcf0b4ea5e0952c4e'
REPLAY = HISTORY / 'locate-042052-dedicated-whole-role-conflict.py'
REPLAY_SHA = '0ac0d2a22e21432e08110d444510ea2e4dda5a6785664931d3acff1d8cefef9e'
TYPE = HISTORY / 'native-overhang-024801-native-whole-semantics.json'
TYPE_SHA = 'f87ac2f4cc131da6fb92c43bf29d822770285269a54d68cf8c03f9d1fdeab393'
CAPABILITIES_SHA = '4631f03fe354733a230dab9d5bbffc339a3cbf3fefb9362544199569685dc461'
COVERAGE = {'nativeFunctions': True, 'carrierAudit': True, 'affectedGameplayQa': True}
WORK = ('fragments', 'substrate_fragments', 'native_visual_fragments', 'whole_cells', 'native_visual_roots')


def require(condition, message):
    if not condition:
        raise ValueError(message)


def digest(path):
    result = hashlib.sha256()
    with Path(path).open('rb') as stream:
        for block in iter(lambda: stream.read(1 << 20), b''):
            result.update(block)
    return result.hexdigest()


def ref(path):
    return {'path': str(Path(path).resolve()), 'sha256': digest(path)}


def read(path):
    return json.loads(Path(path).read_text(encoding='utf-8-sig'))


def bound(value):
    require(digest(value['path']) == value['sha256'], 'Changed immutable evidence: ' + str(value))
    return read(value['path'])


def emit(path, value):
    with path.open('x', encoding='utf-8', newline='\n') as stream:
        json.dump(value, stream, ensure_ascii=False, indent=2)
        stream.write('\n')
    return ref(path)


def module(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    result = importlib.util.module_from_spec(spec)
    sys.modules[name] = result
    spec.loader.exec_module(result)
    return result


def geometry(pose):
    for path in (RESEARCH / 'geometry-final/source-geometry.jsonl', RESEARCH / 'geometry-supplement/source-geometry.jsonl'):
        with path.open(encoding='utf-8') as stream:
            for line in stream:
                if pose in line:
                    row = json.loads(line)
                    if row.get('key') == pose:
                        return row, {'path': str(path), 'rowUtf8WithoutNewlineSha256': hashlib.sha256(line.rstrip('\r\n').encode()).hexdigest()}
    raise ValueError('Missing exact exported pose, no model/shape substitute: ' + pose)


def cached_source(b, p, old, dimension, positions, decode_be=True):
    """Selective palettes and only the explicitly supplied BE compounds."""
    states, bes, proofs = {}, {}, []
    for cx, cz in sorted({(at[0] >> 4, at[2] >> 4) for at in positions}):
        wanted = {at for at in positions if (at[0] >> 4, at[2] >> 4) == (cx, cz)}
        saved = old.execute('SELECT region_path,region_hash,compression,payload FROM chunk_cache WHERE dim=? AND x=? AND z=?', (dimension, cx, cz)).fetchone()
        require(saved is not None, 'Saved source chunk absent; original-world fallback forbidden')
        raw = {1: gzip.decompress, 2: zlib.decompress, 3: lambda value: value}[saved['compression']](saved['payload'])
        rr = p._Reader(raw)
        require(rr.unpack('>B') == 10, 'Saved chunk compound required')
        rr.string()
        sections, decoded = {}, 0
        while True:
            kind = rr.unpack('>B')
            if kind == 0:
                break
            name = rr.string()
            if name == 'sections':
                for section in p._read_payload(rr, kind).value:
                    blocks = p.section_blocks(section)
                    if blocks is not None:
                        palette, indices = blocks
                        sections[p.compound(section)['Y'].value] = ([p.state(value) for value in palette], indices)
            elif name == 'block_entities':
                require(kind == 9 and rr.unpack('>B') == 10, 'Saved BE compound list required')
                count = rr.unpack('>i')
                require(0 <= count <= 65536, 'Saved BE list exceeds the accepted finite parser bound')
                for _ in range(count):
                    start, xyz = rr.pos, {}
                    while True:
                        child = rr.unpack('>B')
                        if child == 0:
                            break
                        key = rr.string()
                        if key in ('x', 'y', 'z'):
                            require(child == 3, 'Saved BE integer position required')
                            xyz[key] = rr.unpack('>i')
                        else:
                            p.skip(rr, child)
                    at = tuple(xyz[key] for key in ('x', 'y', 'z'))
                    if at in wanted and decode_be:
                        data = raw[start:rr.pos]
                        tag = p._read_payload(p._Reader(data), 10)
                        value, fields = b.val(tag), p.compound(tag)
                        bes[at] = {'exists': True, 'id': value.get('id'), 'resident': value.get('resident'), 'payload': value.get('payload', {}), 'outerFields': sorted(fields), 'privateOuterFields': sorted(set(fields) - b.NORMAL), 'typedNBT': b.typed(tag), 'typedNbtSha256': hashlib.sha256(b.canonical(b.typed(tag)).encode()).hexdigest(), 'rawCompoundPayloadSha256': hashlib.sha256(data).hexdigest()}
                        decoded += 1
            else:
                p.skip(rr, kind)
        for at in wanted:
            section = sections.get(at[1] >> 4)
            states[at] = {'registryId': 'minecraft:air', 'properties': {}} if section is None else section[0][section[1][(at[0] & 15) + 16 * (at[2] & 15) + 256 * (at[1] & 15)]]
        proofs.append({'dimension': dimension, 'chunk': [cx, cz], 'boundRegion': saved['region_path'], 'boundRegionSha256': saved['region_hash'], 'cachedCompressedPayloadSha256': hashlib.sha256(saved['payload']).hexdigest(), 'queriedPositions': [list(at) for at in sorted(wanted)], 'addressedBeCompoundsDecoded': decoded})
    return states, bes, proofs


def point(b, db, dimension, at):
    args = (dimension, *at)
    result = {'dimension': dimension, 'position': list(at), 'queue': b.rows(db, 'SELECT processed FROM cell_queue WHERE dim=? AND x=? AND y=? AND z=?', args), 'fragments': b.rows(db, 'SELECT uuid,pose FROM fragments WHERE dim=? AND x=? AND y=? AND z=?', args)}
    for table in b.TABLES:
        result[table] = b.rows(db, 'SELECT * FROM ' + table + ' WHERE dim=? AND x=? AND y=? AND z=?', args)
    return result


def actual_contact(args, b, m, db, run, log):
    """The no-UID whole-role error reuses the exact already reviewed replay."""
    if 'Native overhang would overlap a dedicated whole-object role' in log:
        if args.location_proof:
            location_ref, location = ref(args.location_proof), read(args.location_proof)
        else:
            require(digest(REPLAY) == REPLAY_SHA, 'Accepted bounded replay helper changed')
            replay = module('hold_statue_actual_pending_replay', REPLAY)
            # Only this research author's input/output configuration is changed.
            # No migrate/native_roots_at function, object kind or SQL is patched.
            replay.RUN, replay.OUT = args.failed_run.resolve(), args.out_dir / 'location.json'
            replay.main()
            location_ref, location = ref(replay.OUT), read(replay.OUT)
        require(location['status'] == 'LOCATED_EXACT_CURRENT_PENDING_BATCH_WHOLE_ROLE_CONFLICT'
                and location['failedRun'] == ref(args.failed_run)
                and location['sourceBinding'] == m.meta(db, 'binding')
                and location['nativeComposeCursor'] == m.meta(db, 'nativeComposeCursor'), 'Bound replay is not the actual current failed contact')
        c = location['actualConflict']
        return 'whole_template', c['nativeObject'], c['wholeObject'], tuple(c['targetPosition']), tuple(c['pendingCell'][1:]), location_ref
    marker = 'Required native visual overhang meets a protected local object; retain that particular source root: '
    errors = [line.split(marker, 1)[1] for line in log.splitlines() if line.startswith('RuntimeError: ') and marker in line]
    require(len(errors) == 1, 'Unsupported or ambiguous actual failure; do not infer a future contact')
    dimension, uid, at = ast.literal_eval(errors[0])
    require(isinstance(uid, str) and len(json.loads(uid)) == 4 and len(at) == 3, 'Actual failed native identity format differs')
    c = b.rows(db, 'SELECT * FROM objects WHERE dim=? AND uuid=?', (dimension, uid))
    protected = b.rows(db, "SELECT * FROM protected_cells WHERE dim=? AND x=? AND y=? AND z=? AND reason!='openable_both_states_local_helpers'", (dimension, *at))
    require(len(c) == 1 and len(protected) == 1 and protected[0]['reason'] == 'whole_local_overlap', 'Actual target has extra/unsupported protected owners')
    w = b.rows(db, 'SELECT * FROM objects WHERE dim=? AND uuid=?', (dimension, protected[0]['uuid']))
    require(len(w) == 1, 'Actual target whole owner absent')
    value = {'schemaVersion': 1, 'status': 'LOCATED_ACTUAL_FAILED_PROTECTED_CONTACT', 'sourceBinding': m.meta(db, 'binding'), 'failedRun': ref(args.failed_run), 'nativeComposeCursor': m.meta(db, 'nativeComposeCursor'), 'nativeObject': c[0], 'wholeObject': w[0], 'targetPosition': list(at), 'protectionRow': protected[0], 'futureIdentityScan': False}
    return 'visual_row', c[0], w[0], tuple(at), None, emit(args.out_dir / 'location.json', value)


def shared_whole_native_removal(b, resource, member):
    """Exact finite without(W) closure; raw fragments only, no owner lookup."""
    original = member['recipe']
    matches = member['matchingNativeFragments']
    require(len(matches) == 1, 'Shared W recipe has no unique whole-member match')
    index = original['fragments'].index(matches[0])
    remaining = [row for i, row in enumerate(original['fragments']) if i != index]
    next_index = original['remove'][index]
    residual = None if next_index < 0 else b.catalog_row(resource, next_index)
    resident = original['resident']
    expected_resident = -1 if resident in (-1, index) else resident - (resident > index)
    require((not remaining and next_index == -1) or
            (remaining and residual is not None and residual['fragments'] == remaining
             and residual['resident'] == expected_resident),
            'Shared W without recipe does not preserve every other raw fragment/resident')
    state = member['sourceState']
    require(state['registryId'].startswith('bloodborne_dw:static_architecture_')
            and set(state['properties']) <= {'part', 'waterlogged'}
            and state['properties'].get('waterlogged', 'false') in ('true', 'false'),
            'Unknown base/wet semantics for shared W recipe')
    wet = state['properties'].get('waterlogged', 'false') == 'true'
    base = ({'registryId': 'minecraft:water' if wet else 'minecraft:air',
             'properties': {'level': '0'} if wet else {}} if next_index < 0 else
            {'registryId': 'bloodborne_dw:static_architecture_' + str(next_index // 128),
             'properties': {'part': str(next_index % 128), 'waterlogged': 'true' if wet else 'false'}})
    return {'position': member['position'], 'sourceState': state,
            'originalRecipeIndex': member['recipeIndex'], 'completeOriginalRecipe': original,
            'removedWholeFragmentIndex': index, 'removedWholeFragment': matches[0],
            'remainingRawFragments': remaining, 'expectedResidualResident': expected_resident,
            'residualRecipeIndex': next_index, 'completeResidualRecipe': residual,
            'derivedBaseStateAfterWholeRemoval': base, 'materialSlabOverlay': False,
            'waterloggedPropertyPresent': 'waterlogged' in state['properties'],
            'wetValueAccordingToExistingWithoutGuard': wet, 'wetValuePreserved': True,
            'allOtherRawFragmentsPreservedExactly': True, 'residentPreservedWithIndexAdjustment': True,
            'extraOwnerLookup': False, 'extraFootprintRead': False,
            'wholePhysicalStatesChangedByVisualCorrection': False}


def collect(args, b, p, prod, m, db, old, route, c, w, target, seed, location_ref):
    dimension = c['dim']
    require(c['kind'] == 'substrate' and w['kind'] == 'whole' and w['dim'] == dimension, 'Only unchanged ordinary C plus confirmed whole W are supported')
    objects, subjects, geometries, candidates = {'native': c, 'whole': w}, {}, {}, {}
    for label, obj in objects.items():
        encoded = db.execute('SELECT data FROM poses WHERE key=?', (obj['pose'],)).fetchone()[0]
        data = json.loads(encoded)
        if label == 'whole':
            require(data['registryId'] == 'bloodborne_dw:source_hold_statue', 'Unknown whole class/type; this helper is HoldStatue-only')
        g, gr = geometry(obj['pose'])
        geometries[label] = g
        root = [obj[key] for key in ('x', 'y', 'z')]
        candidates[label] = sorted({tuple(root[i] + row['cell'][i] for i in range(3)) for row in g['cells']})
        require(0 < len(candidates[label]) <= 4096 and tuple(root) in candidates[label], 'Incomplete exact exported runtime cells')
        own = {}
        for table in ('selected_objects', 'fragments', 'whole_cells', 'activated_native', 'cleanup_owners'):
            suffix = ' INDEXED BY fragment_owner' if table == 'fragments' else ''
            order = ' ORDER BY x,y,z' if table in ('fragments', 'whole_cells') else ''
            own[table] = b.rows(db, 'SELECT * FROM ' + table + suffix + ' WHERE dim=? AND uuid=?' + order + ' LIMIT 4097', (dimension, obj['uuid']))
            require(len(own[table]) <= 4096, 'Exact own row bound exceeded')
        require(not own['fragments'] and not own['activated_native'] and not own['cleanup_owners'], 'C/W already has incompatible conversion/cleanup work')
        offsets = [[row[key] for key in ('dx', 'dy', 'dz')] for row in db.execute('SELECT dx,dy,dz FROM pose_cells WHERE pose=?', (obj['pose'],))]
        require(0 < len(offsets) <= 4096, 'Exact visual cells missing')
        subjects[label] = {'object': obj, 'poseData': data, 'poseDataSha256': hashlib.sha256(encoded.encode()).hexdigest(), 'root': root, 'runtimeMemberCells': [list(at) for at in candidates[label]], 'visualOffsets': offsets, 'ownPlanRows': own, 'sourceGeometry': {**gr, 'sourcePose': g['sourcePose'], 'models': g['models'], 'runtimeCells': [{key: value for key, value in row.items() if key != 'quads'} for row in g['cells']]}}
        for table in ('stored_owners', 'be_owners'):
            records = b.rows(old, 'SELECT * FROM ' + table + ' WHERE dim=? AND uuid=?', (dimension, obj['uuid']))
            require(not records, 'Actual C/W is not the proved synthetic stateless-native owner route')
            subjects[label][table] = records
    require(not subjects['native']['ownPlanRows']['selected_objects'], 'C unexpectedly selected as a whole/static owner')
    selected = subjects['whole']['ownPlanRows']['selected_objects']
    require((route == 'whole_template' and len(selected) == 1 and selected[0]['kind'] == 'whole') or (route == 'visual_row' and not selected and not subjects['whole']['ownPlanRows']['whole_cells']), 'Actual W selection does not match either proven route')
    cvisual = {tuple(subjects['native']['root'][i] + off[i] for i in range(3)) for off in subjects['native']['visualOffsets']}
    require(target in cvisual and not cvisual & set(candidates['whole']), 'Actual C visual/physical intersects true W members; manual finite retention required')
    roles = subjects['whole']['ownPlanRows']['whole_cells']
    positions = set(candidates['native']) | set(candidates['whole']) | cvisual | {tuple(row[key] for key in ('x', 'y', 'z')) for row in roles} | {target}
    if seed is not None:
        positions.add(seed)
    require(len(positions) <= 8192, 'One exact C/W point union exceeds accepted bounds')
    points = {at: point(b, db, dimension, at) for at in sorted(positions)}
    requested = set(candidates['native']) | set(candidates['whole']) | {target}
    states, bes, cache = cached_source(b, p, old, dimension, requested)
    resource = SOURCE / 'src/architecture/resources/bloodborne_dw/static-architecture-v1274.json'
    recipes = {}
    def recipe(at):
        state = states[at]
        require(state['registryId'].startswith('bloodborne_dw:static_architecture_'), 'Unknown actual native class/carrier; no FOREIGN inference')
        index = int(state['registryId'].rsplit('_', 1)[1]) * 128 + int(state['properties']['part'])
        if index not in recipes:
            recipes[index] = b.catalog_row(resource, index)
        return index, recipes[index]
    for label, subject in subjects.items():
        root = subject['root']
        _, root_recipe = recipe(tuple(root))
        keys = {row['pose'] for row in root_recipe['fragments'] if row['origin'] == [0, 0, 0]}
        actual = [row for row in prod.records(resource, 'poses') if row['key'] in keys and all(row[key] == subject['poseData'][key] for key in ('registryId', 'properties', 'shift', 'mountY'))]
        require(len(actual) == 1, 'Actual canonical native root is not uniquely the supplied exact SourcePose')
        subject['legacyPose'], subject['legacyPoseKey'] = actual[0], actual[0]['key']
        subject['physicalMembers'] = []
        for at in candidates[label]:
            index, r = recipe(at)
            matches = [row for row in r['fragments'] if row['pose'] == actual[0]['key'] and [at[i] + row['origin'][i] for i in range(3)] == root]
            require(len(matches) == 1 and matches[0]['member'] is True and at not in bes, 'Native whole member/recipe mismatch or BE/ledger route; manual review required')
            require(label != 'whole' or len(r['fragments']) == 1 or route == 'visual_row',
                    'Dedicated whole-template fallback still requires pure W recipes; no extra-owner traversal')
            stored = b.rows(old, 'SELECT * FROM stored_contributions WHERE dim=? AND x=? AND y=? AND z=? AND uuid=?', (dimension, *at, subject['object']['uuid']))
            members = b.rows(old, 'SELECT * FROM members WHERE dim=? AND x=? AND y=? AND z=? AND uuid=?', (dimension, *at, subject['object']['uuid']))
            require(not stored and not members, 'Actual native C/W has a saved owner ledger; no FOREIGN inference')
            subject['physicalMembers'].append({'position': list(at), 'sourceState': states[at], 'recipeIndex': index, 'recipe': r, 'matchingNativeFragments': matches, 'sourceBE': {'exists': False}, 'storedContributions': stored, 'oldMembers': members, 'pointDemand': points[at]})
        root_member = next(row for row in subject['physicalMembers'] if row['position'] == root)
        resident = root_member['recipe']['resident']
        require(resident >= 0 and root_member['recipe']['fragments'][resident]['pose'] == actual[0]['key'] and root_member['recipe']['fragments'][resident]['origin'] == [0, 0, 0], 'Canonical full-model resident root not preserved')
        subject['sourcePayloadStorage'] = 'canonical_static_catalog_pose_no_instance_BE'
        if label == 'whole':
            subject['sharedWholeNativeRemovalProofs'] = [
                shared_whole_native_removal(b, resource, row)
                for row in subject['physicalMembers'] if len(row['recipe']['fragments']) > 1]
        if label == 'native':
            subject['sharedOriginalRecipeRows'] = [
                {'position': row['position'], 'sourceState': row['sourceState'], 'recipeIndex': row['recipeIndex'],
                 'completeOriginalRecipe': row['recipe'], 'exactConvertingFragments': row['matchingNativeFragments'],
                 'otherOriginalFragments': [part for part in row['recipe']['fragments'] if part not in row['matchingNativeFragments']],
                 'additionalOwnerLookup': False, 'additionalFootprintRead': False, 'retentionAdmission': False,
                 'activationAuthority': 'Unchanged native_roots_at/compose; C and any other original native components remain unretained'}
                for row in subject['physicalMembers'] if len(row['recipe']['fragments']) > 1]
    target_index, target_recipe = recipe(target)
    wkey, wroot = subjects['whole']['legacyPoseKey'], subjects['whole']['root']
    require(not [row for row in target_recipe['fragments'] if row['pose'] == wkey and [target[i] + row['origin'][i] for i in range(3)] == wroot] and target not in bes, 'Target contains actual W physical origin or BE')
    classification_path = SOURCE / 'tools/native-grid/classification.json'
    capabilities_path = Path(m.meta(db, 'binding')['oldPlan']).parents[2] / 'tools/ordinary-stage1/source-capabilities.json'
    require(digest(capabilities_path) == CAPABILITIES_SHA, 'Accepted immutable source class capability authority changed')
    classes = [row for row in read(capabilities_path)['states'] if row['registryId'] == 'bloodborne_dw:source_hold_statue']
    require(len(classes) == 1 and classes[0]['className'] == 'dev.dreamwalker.bloodbornedw.composite.ImportedRootBlock', 'Unknown source HoldStatue class; class capability is not a functional proof')
    subjects['whole']['sourceClassProof'] = {'capabilities': ref(capabilities_path), 'row': classes[0], 'functionalPassClaimed': False}
    ids_path, templates_path = SOURCE / 'tools/native-grid/statue-ids.json', RESEARCH / 'whole-objects.jsonl'
    classification, ids = read(classification_path), read(ids_path)
    cp = [row for row in classification['poses'] if row['key'] == w['pose']]
    entry = [row for row in ids['entries'] if row['registryId'] == 'bloodborne_dw:source_hold_statue']
    requests = [json.loads(line) for line in templates_path.read_text(encoding='utf-8').splitlines() if line and w['pose'] in line]
    require(len(cp) == len(entry) == len(requests) == 1 and cp[0]['route'] == 'whole' and cp[0]['reason'] == w['reason'] and entry[0]['classification'] == 'whole_statue' and entry[0]['protectedV12'] is False and subjects['whole']['poseData']['registryId'] not in classification['protectedRegistryIds'], 'Missing exact confirmed non-v12 whole authority')
    require(any(row['variant'] == int(subjects['whole']['poseData']['properties']['variant']) for row in entry[0]['variants']), 'Unknown exact HoldStatue variant')
    manifest_path = RESEARCH / 'preserved-assemblies.json'
    matches = [row for row in read(manifest_path)['poseMatches'] if row['pose'] == w['pose']]
    require(not matches, 'This pose has separate preserved-assembly authority; manual priority review required')
    authority = {'classification': ref(classification_path), 'exactPoseRows': cp, 'statueIds': ref(ids_path), 'statueIdEntry': entry[0], 'wholeTemplates': ref(templates_path), 'exactWholeTemplateRequests': requests, 'originalV12RegistryIdsExcluded': True, 'preservedAssemblies': ref(manifest_path), 'exactManifestPoseMatches': matches}
    whole_points = set(candidates['whole'])
    after = []
    for at, value in points.items():
        rem = {key: [row for row in value[key] if not (key == 'whole_cells' and row['uuid'] == w['uuid'])] for key in WORK}
        after.append({'position': list(at), 'queue': value['queue'], 'remainingDemands': rem, 'remainingDemandCount': sum(len(value) for value in rem.values()), 'bridgeCells': value['bridge_cells'], 'protectedCells': value['protected_cells'], 'placements': value['placements']})
    for at in cvisual:
        value = points[at]
        require(not value['bridge_cells'] and not value['placements'], 'Unexpected existing carrier/placement at C visual footprint')
        require(route != 'whole_template' or not value['protected_cells'], 'Dedicated whole fallback cannot release a protected visual row')
        require(all(row['uuid'] == w['uuid'] and row['reason'] == 'whole_local_overlap' for row in value['protected_cells']), 'Another protected owner or strict reason; do not traverse')
        require(all(row['uuid'] == w['uuid'] for row in value['whole_cells']), 'Another dedicated whole owner; do not traverse')
    for value in after:
        if tuple(value['position']) in whole_points:
            require(value['remainingDemandCount'] == 0 and not value['bridgeCells'] and not value['placements'], 'Independent work on W physical member blocks this route')
            if route == 'visual_row':
                require(not value['queue'] and any(row['uuid'] == w['uuid'] and row['reason'] == 'whole_local_overlap' for row in value['protectedCells']), 'Original unselected W physical member not strictly protected and untouched')
    return {'schemaVersion': 1, 'status': 'READ_ONLY_EXACT_C_AND_DEDICATED_WHOLE_W_FACTS_NOT_ADMISSION', 'sourceBinding': m.meta(db, 'binding'), 'failedRun': ref(args.failed_run), 'failedLog': ref(Path(read(args.failed_run)['log'])), 'locationProof': location_ref, 'nativeComposeCursor': m.meta(db, 'nativeComposeCursor'), **subjects, 'wholeAuthority': authority, 'legacyCatalog': ref(resource), 'pointDemands': list(points.values()), 'postExclusionOnlyWWholeRoles': after, 'actualTarget': {'position': list(target), 'sourceState': states[target], 'recipeIndex': target_index, 'recipe': target_recipe, 'sourceBE': {'exists': False}, 'pointDemand': points[target]}, 'targetStatueRecipeOrigins': [], 'cachedSourceProofs': cache, 'ownerGraphTraversal': False, 'applyAuthorization': False, 'databaseWrites': 0, 'worldFilesRead': 0}


def semantics(facts, facts_ref):
    require(digest(TYPE) == TYPE_SHA and facts['legacyCatalog']['sha256'] == read(TYPE)['catalogResource']['sha256'], 'Frozen shared native type/method resource changed')
    w = facts['whole']
    return {'schemaVersion': 1, 'status': 'PASS_SOURCE_REVIEW_NOT_EXECUTION', 'kind': 'EXACT_HOLD_STATUE_NATIVE_WHOLE_SOURCE_DERIVATION', 'sourceBinding': facts['sourceBinding'], 'statueUuid': w['object']['uuid'], 'pose': w['object']['pose'], 'legacyPose': w['legacyPoseKey'], 'runtimeMemberCells': w['runtimeMemberCells'], 'wholeModelAndMemberLifecyclePreserved': True, 'itemPickDropGapUnresolved': True, 'gameplayPass': False, 'sourceDataProof': facts_ref, 'genericTypeMethodProof': ref(TYPE), 'exactActualMembers': [{'position': row['position'], 'sourceState': row['sourceState'], 'recipeIndex': row['recipeIndex'], 'matchingNativeFragments': row['matchingNativeFragments']} for row in w['physicalMembers']], 'sharedWholeNativeRemovalProofs': w.get('sharedWholeNativeRemovalProofs', []), 'derivation': 'Same canonical origin0 full-model resident and complete actual member/origin recipes, under the unchanged reviewed StaticArchitectureClient/Runtime methods; shared visual-row W members additionally bind exact without(W) residual fragments/resident/base/wet behavior. No new type review or gameplay pass is claimed.', 'applyAuthorization': False, 'databaseReads': 0, 'databaseWrites': 0, 'worldFilesRead': 0}


def native_guard(args, b, p, m, db, old, facts, facts_ref, prune):
    require(all(next(row for row in facts['pointDemands'] if row['position'] == list(at))['queue'] == [{'processed': 0}] for at in prune), 'Only q0 pruning is supported')
    states, _, cache = cached_source(b, p, old, facts['native']['object']['dim'], set(prune), decode_be=False)
    fb = m.meta(db, 'poseFragments')
    exports = b.ro(fb['path'])
    result = []
    try:
        doc, overlays = m.legacy_native_catalog()
        for at in prune:
            dimension = facts['native']['object']['dim']
            state = states[at]
            saved = exports.execute('SELECT data FROM native WHERE state=?', (m.canonical(state),)).fetchone()
            native = json.loads(saved[0]) if saved else None
            index, legacy, resolved = None, {}, []
            if not (native and native.get('nativeVisualComplete', True)):
                if state['registryId'].startswith('bloodborne_dw:static_architecture_'):
                    index = int(state['registryId'].rsplit('_', 1)[1]) * 128 + int(state['properties'].get('part', 0))
                elif state['registryId'] in overlays:
                    index = overlays[state['registryId']]
            recipe = doc['recipes'][index] if index is not None else None
            if recipe is not None:
                for fragment in recipe['fragments']:
                    root = tuple(at[i] + fragment['origin'][i] for i in range(3))
                    uid = m.synthetic_uuid(dimension, root, fragment['pose'])
                    rows = b.rows(db, 'SELECT * FROM objects WHERE dim=? AND uuid=?', (dimension, uid))
                    require(len(rows) == 1, 'Guard recipe identity absent')
                    obj = rows[0]
                    if obj['kind'] == 'whole' and db.execute("SELECT 1 FROM selected_objects WHERE dim=? AND uuid=? AND kind='whole'", (dimension, uid)).fetchone():
                        require(uid == facts['whole']['object']['uuid'], 'Another selected whole origin; no owner traversal')
                        continue
                    require(obj['kind'] == 'substrate', 'Other retained/protected origin at prune; no exception or traversal')
                    resolved.append((uid, obj['pose'], *root))
                    legacy[(uid, obj['pose'], root)] = {'legacyPoseKey': fragment['pose'], 'member': fragment['member']}
            roots = [tuple(row) for row in m.native_roots_at(db, dimension, at, state, native)]
            if recipe is not None:
                require(roots == resolved, 'Unchanged native resolver disagrees with exact recipe origins')
            records = []
            for uid, pose, x, y, z in roots:
                obj = b.rows(db, 'SELECT * FROM objects WHERE dim=? AND uuid=?', (dimension, uid))[0]
                activated = b.rows(db, 'SELECT * FROM activated_native WHERE dim=? AND uuid=?', (dimension, uid))
                require(not activated, 'Independent activated native demand at prune; no B/F traversal')
                row = {'uuid': uid, 'pose': pose, 'root': [x, y, z], 'objectKind': obj['kind'], 'reason': obj['reason'], 'activatedNative': activated, 'allowedPreservedNative': False}
                row.update(legacy.get((uid, pose, (x, y, z)), {}))
                records.append(row)
            result.append({'dimension': dimension, 'position': list(at), 'sourceState': state, 'queue': [{'processed': 0}], 'nativeExport': {'exists': bool(saved), 'storedDataSha256': hashlib.sha256(saved[0].encode()).hexdigest() if saved else None, 'nativeVisualComplete': native.get('nativeVisualComplete', True) if native else None}, 'legacyRecipeIndex': index, 'legacyRecipe': recipe, 'nativeRoots': records, 'foreignActivated': [], 'nativeRootsAtError': None, 'sourceRecipeResolutionPassed': False, 'queuePruningSafe': True})
    finally:
        exports.close()
    return {'schemaVersion': 1, 'kind': 'EXACT_NATIVE_WHOLE_TEMPLATE_FALLBACK_QUEUE_GUARD', 'status': 'PASS', 'sourceBinding': facts['sourceBinding'], 'failedRun': facts['failedRun'], 'nativeUuid': facts['native']['object']['uuid'], 'wholeOwnerUuid': facts['whole']['object']['uuid'], 'wholeProbe': facts_ref, 'points': result, 'blocked': [], 'fragmentBinding': fb, 'sourceRecipeResource': facts['legacyCatalog'], 'nativeRootsAlgorithm': ref(SOURCE / 'tools/native-grid-city/migrate.py'), 'cachedSourceProofs': cache, 'queuePolicy': 'q0_only_no_q1_retirement', 'originalNativeRootsAtUnmodified': True, 'applyAuthorization': False, 'ownerGraphTraversal': False, 'databaseWrites': 0, 'worldFilesRead': 0}


def strict_history(db, m, binding, dimension, target):
    chain, matches, seen = [], [], set()
    current = m.meta(db, 'nativeLocalRetention')
    latest = current
    while current:
        require(current['sha256'] not in seen, 'Cyclic committed retention ancestry')
        seen.add(current['sha256'])
        receipt = bound(current)
        require(receipt['status'] == 'PASS' and receipt['sourceBinding'] == binding, 'Unaccepted strict-original ancestry')
        preview = bound(receipt['preview'])
        for case in preview['cases']:
            for native in [case['retainedNative']] + case.get('retainedAdditionalNative', []):
                if native['dimension'] == dimension and list(target) in native['mustRemainOriginalCells']:
                    matches.append({'receipt': current, 'native': native['uuid']})
            for owner in case['retainedOwners']:
                policy = {tuple(row['position']): row['policy'] for row in owner.get('memberPolicies', [])}
                if owner['dimension'] == dimension and list(target) in owner['runtimeMemberCells'] and policy.get(target, 'original') == 'original':
                    matches.append({'receipt': current, 'owner': owner['uuid']})
        chain.append({'receipt': current, 'preview': receipt['preview'], 'scope': receipt['scope']})
        current = receipt.get('previousRetention')
    require(not matches, 'Historical strict-original target: no visual correction')
    return {'schemaVersion': 1, 'status': 'READ_ONLY_COMMITTED_RETENTION_ANCESTRY_CHECK_NOT_ADMISSION', 'sourceBinding': binding, 'targetPosition': list(target), 'latestCommittedReceipt': latest, 'strictOriginalTargetMatches': matches, 'receiptChain': chain, 'ownerGraphTraversal': False, 'databaseWrites': 0, 'worldFilesRead': 0}


def prepare(args):
    require(args.out_dir.resolve().is_relative_to(RESEARCH) and not args.out_dir.exists(), 'Use one fresh research-only output directory')
    args.out_dir.mkdir(parents=True)
    require(digest(SHARED) == SHARED_SHA, 'Accepted readonly helper functions changed')
    b = module('hold_statue_readonly_common', SHARED)
    p = module('hold_statue_saved_parser', SOURCE / 'tools/native-grid/qa/full_native_inputs.py')
    prod = module('hold_statue_record_reader', SOURCE / 'tools/native-grid/prepare_retained_whole_items.py')
    sys.path.insert(0, str(SOURCE / 'tools/native-grid-city'))
    m = module('hold_statue_unmodified_migrate', SOURCE / 'tools/native-grid-city/migrate.py')
    run = read(args.failed_run)
    require(run['status'] == 'FAIL', 'Only an actual stopped failed invocation is supported')
    log = Path(run['log']).read_text(encoding='utf-8')
    db, old = b.ro(args.plan / 'plan.sqlite'), None
    try:
        binding = m.meta(db, 'binding')
        original = Path(binding['oldPlan']) / 'plan.sqlite'
        st = original.stat()
        require(st.st_size == binding['oldDatabaseBytes'] and st.st_mtime_ns == binding['oldDatabaseMtimeNs'], 'Bound immutable source cache changed')
        old = b.ro(original)
        route, c, w, target, seed, location_ref = actual_contact(args, b, m, db, run, log)
        facts = collect(args, b, p, prod, m, db, old, route, c, w, target, seed, location_ref)
        facts_ref = emit(args.out_dir / 'facts.json', facts)
        semantics_ref = emit(args.out_dir / 'native-whole-semantics.json', semantics(facts, facts_ref))
        if args.facts_only:
            return {'status': 'FACTS_ONLY_NOT_ADMISSION', 'route': route, 'facts': facts_ref, 'semantics': semantics_ref}
        if route == 'whole_template':
            keep_positions = {seed, tuple(facts['native']['root'])}
            require(None not in keep_positions, 'Actual replay seed required')
            require(seed in {tuple(at) for at in facts['native']['runtimeMemberCells']}
                    and seed in {tuple(facts['native']['root'][i] + off[i] for i in range(3)) for off in facts['native']['visualOffsets']},
                    'Actual kept seed requires complete visual and original native member proof')
            snapshots = {tuple(row['position']): row for row in facts['pointDemands']}
            require(all(snapshots[at]['queue'] == [{'processed': 0}] for at in keep_positions), 'Keep actual native seed/root q0; never insert or reset a bit')
            keep = [{'dimension': c['dim'], 'position': list(at), 'reason': 'exact_pending_native_activation', 'nativeUuid': c['uuid']} for at in sorted(keep_positions)]
            post = {tuple(row['position']): row for row in facts['postExclusionOnlyWWholeRoles']}
            prune = sorted(at for at in snapshots if at not in keep_positions and snapshots[at]['queue'] and post[at]['remainingDemandCount'] == 0)
            guard_ref = emit(args.out_dir / 'queue-native-guard.json', native_guard(args, b, p, m, db, old, facts, facts_ref, prune))
            native = {key: facts['native'][key] for key in ('object', 'poseDataSha256', 'root', 'runtimeMemberCells', 'physicalMembers')}
            native.update(dimension=c['dim'], uuid=c['uuid'], pose=c['pose'], actualRootState=next(row['sourceState'] for row in native['physicalMembers'] if row['position'] == native['root']), visualCells=[[native['root'][i] + off[i] for i in range(3)] for off in facts['native']['visualOffsets']])
            whole = {key: facts['whole'][key] for key in ('object', 'poseDataSha256', 'root', 'runtimeMemberCells', 'physicalMembers')}
            whole.update(dimension=w['dim'], uuid=w['uuid'], pose=w['pose'], selected=facts['whole']['ownPlanRows']['selected_objects'][0], wholeCells=facts['whole']['ownPlanRows']['whole_cells'], fragments=[], wholeAuthority=facts['wholeAuthority'])
            fields = {'native': native, 'whole': whole, 'keepNativeQueue': keep, 'pointDemands': facts['pointDemands']}
            proof = {'schemaVersion': 1, 'status': 'PASS', 'sourceBinding': binding, 'failedRun': facts['failedRun'], 'locationProof': location_ref, 'sourceFacts': facts_ref, 'nativeWholeSemantics': semantics_ref, 'queueNativeGuard': guard_ref, **copy.deepcopy(fields)}
            certificate = {'schemaVersion': 1, 'kind': 'FINITE_NATIVE_WHOLE_TEMPLATE_FALLBACK_V1', 'status': 'PASS', 'sourceBinding': binding, 'failedRun': facts['failedRun'], 'locationProof': location_ref, 'queueNativeGuard': guard_ref, **fields}
        else:
            dimension = c['dim']
            at = facts['actualTarget']['pointDemand']
            require(at['queue'] == [] or at['queue'] == [{'processed': 0}], 'Visual correction cannot reset completed work')
            require(not at['whole_cells'] and not at['bridge_cells'] and not at['placements'] and len(at['protected_cells']) == 1, 'Exact singleton visual reservation required')
            history_ref = emit(args.out_dir / 'strict-original-history.json', strict_history(db, m, binding, dimension, target))
            statute = {**facts['whole'], 'uuid': w['uuid'], 'pose': w['pose'], 'members': facts['whole']['physicalMembers']}
            recipe_ref = emit(args.out_dir / 'source-recipe-proof.json', {'schemaVersion': 1, 'status': 'READ_ONLY_EXACT_NATIVE_STATUE_MEMBER_AND_VISUAL_PROOF_NOT_ADMISSION', 'sourceBinding': binding, 'statue': statute, 'actualTarget': facts['actualTarget'], 'targetStatueRecipeOrigins': []})
            native = {'dimension': dimension, 'uuid': c['uuid'], 'pose': c['pose'], 'poseDataSha256': facts['native']['poseDataSha256'], 'root': facts['native']['root']}
            whole = {key: facts['whole'][key] for key in ('object', 'poseDataSha256', 'root', 'runtimeMemberCells', 'physicalMembers')}
            whole.update(pose=w['pose'])
            fields = {'native': native, 'retainedWhole': whole, 'protectionRow': at['protected_cells'][0], 'pointSnapshot': at}
            proof = {'schemaVersion': 1, 'status': 'PASS_SOURCE_CORRECTION_ONLY', 'sourceBinding': binding, 'sourceRecipeProof': recipe_ref, 'nativeWholeSemantics': semantics_ref, 'historicalStrictOriginalCheck': history_ref, **copy.deepcopy(fields)}
            certificate = {'schemaVersion': 1, 'kind': 'FINITE_NATIVE_VISUAL_PROTECTION_CORRECTION_V1', 'status': 'PASS_SOURCE_CORRECTION_ONLY', 'sourceBinding': binding, 'failedRun': facts['failedRun'], 'nativeWholeSemantics': semantics_ref, 'historicalStrictOriginalCheck': history_ref, **fields}
        shared = {'finalCoverageRequired': COVERAGE, 'itemPickDropGapUnresolved': True, 'gameplayPass': False, 'applyAuthorization': False, 'ownerGraphTraversal': False}
        proof.update(shared)
        certificate.update(shared)
        certificate['sourceProof'] = emit(args.out_dir / 'source-proof.json', proof)
        return {'status': 'PASS_SOURCE_DATA_READY_NOT_EXECUTED', 'route': route, 'facts': facts_ref, 'certificate': emit(args.out_dir / 'certificate.json', certificate), 'sourceProof': certificate['sourceProof'], 'nativeWholeSemantics': semantics_ref, **shared}
    finally:
        if old is not None:
            old.close()
        db.close()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--failed-run', type=Path, required=True)
    parser.add_argument('--plan', type=Path, default=RESEARCH / 'native-grid-plan')
    parser.add_argument('--out-dir', type=Path, required=True)
    parser.add_argument('--location-proof', type=Path, help='Reuse a source-bound already located actual no-UID whole-role failure')
    parser.add_argument('--facts-only', action='store_true', help='Freeze only exact facts/semantics; never emit an admission certificate')
    args = parser.parse_args()
    try:
        result = prepare(args)
        result.update(generator=ref(__file__), databaseWrites=0, worldFilesRead=0, mapWrites=0, sourceEdits=0, gameProcessesStarted=0)
        emit(args.out_dir / 'result.json', result)
        print(json.dumps(result, ensure_ascii=False))
    except BaseException:
        if args.out_dir.resolve().is_relative_to(RESEARCH) and args.out_dir.exists() and not (args.out_dir / 'blocked.json').exists():
            emit(args.out_dir / 'blocked.json', {'status': 'BLOCKED_MANUAL_FINITE_REVIEW_REQUIRED', 'error': traceback.format_exc(), 'failedRun': ref(args.failed_run), 'ownerGraphTraversal': False, 'databaseWrites': 0, 'worldFilesRead': 0, 'mapWrites': 0, 'sourceEdits': 0, 'gameProcessesStarted': 0})
        raise


if __name__ == '__main__':
    main()
