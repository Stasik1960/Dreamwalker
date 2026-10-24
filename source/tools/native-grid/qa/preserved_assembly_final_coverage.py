"""Final finite assembly coverage from the accepted plan and saved receipts.

Run ONCE after FULL import/apply/accepted native light. Never opens a world,
discovers an owner, creates a temporary SQL table, or changes the plan. This is
an artifact/plan coverage receipt, not another gameplay or world-census test.
"""
import argparse, collections, hashlib, json, sqlite3, sys, traceback
from pathlib import Path

sys.dont_write_bytecode = True
SOURCE = Path(__file__).resolve().parents[3]
RESEARCH = SOURCE.parent / 'research'
sys.path.insert(0, str(SOURCE / 'tools/native-grid-city'))
import migrate as m
import assembly_overrides as overrides
import assembly_components as components
import native_certificates as certificates

OBJECT_FIELDS = ('dim', 'uuid', 'pose', 'x', 'y', 'z', 'kind', 'reason', 'source_status')
SHA_CACHE = {}

def require(ok, message):
    if not ok:
        raise ValueError(message)

def sha(path):
    path = Path(path).resolve()
    if path not in SHA_CACHE:
        SHA_CACHE[path] = m.sha(path)
    return SHA_CACHE[path]

def ref(path):
    return {'path': str(Path(path).resolve()), 'sha256': sha(path)}

def bound(value):
    require(value and sha(value['path']) == value['sha256'], 'Changed bound evidence: ' + str(value))
    return m.read(value['path'])

def one(db, table, dimension, position):
    return db.execute('SELECT * FROM ' + table + ' WHERE dim=? AND x=? AND y=? AND z=?',
                      (dimension, *position)).fetchone()

def present(db, table, identity):
    suffix = ' INDEXED BY fragment_owner' if table == 'fragments' else ''
    return bool(db.execute('SELECT 1 FROM ' + table + suffix + ' WHERE dim=? AND uuid=? LIMIT 1', identity).fetchone())

def retention_chain(db, binding):
    """Only the already committed finite chain; no discovery/proof regeneration."""
    current = m.meta(db, 'nativeLocalRetention')
    receipts, natives, owners, seen = [], {}, {}, set()
    while current:
        require(current['sha256'] not in seen, 'Cyclic retention receipt chain')
        seen.add(current['sha256'])
        receipt = bound(current)
        require(receipt['status'] == 'PASS' and receipt['kind'] == 'EXPLICIT_FINITE_NATIVE_RETENTION'
                and receipt['sourceBinding'] == binding, 'Unaccepted finite retention receipt')
        scope, preview = bound(receipt['scope']), bound(receipt['preview'])
        require(scope.get('applyAuthorization') is not True and preview['sourceBinding'] == binding,
                'Retention lost its immutable source/accepted-preview authority')
        # The first explicitly admitted eighteen-case scope predates the DATA
        # certificate label; its committed receipt and exact preview are the
        # authority. Later certificates explicitly carry applyAuthorization=false.
        if (scope.get('kind') or '').startswith('FINITE_'):
            require(scope.get('applyAuthorization') is False and scope.get('ownerGraphTraversal') is False,
                    'A later data certificate has no finite/non-authorizing contract')
        receipts.append(current)
        for case in preview['cases']:
            for native in [case['retainedNative']] + case.get('retainedAdditionalNative', []):
                key = (native['dimension'], native['uuid'])
                require(key not in natives, 'Duplicate retained native identity')
                natives[key] = {'row': native, 'receipt': current, 'scope': receipt['scope']}
            for owner in case['retainedOwners']:
                key = (owner['dimension'], owner['uuid'])
                require(key not in owners or owners[key]['row'] == owner, 'Conflicting retained owner records')
                owners[key] = {'row': owner, 'receipt': current, 'scope': receipt['scope'],
                               'reason': case.get('policy', 'explicit_finite_original_retention')}
        current = receipt.get('previousRetention')
    declared = {(row['dimension'], row['uuid']) for row in m.meta(db, 'nativeRetainedComponents') or []}
    require(set(natives) == declared, 'Committed native retention identities differ from plan metadata')
    return receipts, natives, owners

def whole_template_fallback_chain(db, binding):
    """Optional committed W-only chain; its ordinary C is never retained here."""
    current = m.meta(db, 'nativeWholeTemplateFallback')
    receipts, owners, seen = [], {}, set()
    while current:
        require(current['sha256'] not in seen, 'Cyclic whole-template fallback chain')
        seen.add(current['sha256'])
        receipt = bound(current)
        require(receipt['status'] == 'PASS_SOURCE_ONLY'
                and receipt['kind'] == 'EXACT_NATIVE_WHOLE_TEMPLATE_FALLBACK'
                and receipt['sourceBinding'] == binding, 'Unaccepted W-only fallback receipt')
        document, preview = bound(receipt['certificate']), bound(receipt['preview'])
        source = bound(document['sourceProof'])
        require(document['kind'] == 'FINITE_NATIVE_WHOLE_TEMPLATE_FALLBACK_V1'
                and document['status'] == source['status'] == 'PASS'
                and document['sourceBinding'] == source['sourceBinding'] == preview['sourceBinding'] == binding
                and document.get('applyAuthorization') is False
                and document.get('ownerGraphTraversal') is False,
                'W-only fallback lost its exact finite source/accepted-preview authority')
        require(all(source[key] == document[key] for key in ('native', 'whole', 'keepNativeQueue', 'pointDemands')),
                'W-only source proof differs from its committed certificate')
        owner = document['whole']
        identity = (owner['dimension'], owner['uuid'])
        require(owner['object']['kind'] == owner['selected']['kind'] == 'whole'
                and owner['object']['uuid'] == owner['uuid']
                and owner['object']['pose'] == owner['pose']
                and owner['root'] == [owner['object'][key] for key in ('x', 'y', 'z')]
                and not owner['fragments'] and owner['runtimeMemberCells']
                and {tuple(row['position']) for row in owner['physicalMembers']} ==
                    {tuple(at) for at in owner['runtimeMemberCells']}, 'Incomplete exact original W identity/members')
        actual = db.execute('SELECT * FROM objects WHERE dim=? AND uuid=?', identity).fetchone()
        encoded = db.execute('SELECT data FROM poses WHERE key=?', (owner['pose'],)).fetchone()
        require(actual is not None and dict(actual) == owner['object'] and encoded is not None
                and hashlib.sha256(encoded[0].encode()).hexdigest() == owner['poseDataSha256'],
                'Committed W-only fallback changed original kind/pose/root')
        require(not any(present(db, table, identity) for table in
                        ('selected_objects', 'whole_cells', 'fragments', 'activated_native', 'cleanup_owners')),
                'Original W-only fallback acquired conversion/cleanup work')
        require(identity not in owners, 'Duplicate W-only fallback identity')
        owners[identity] = {'row': owner, 'receipt': current, 'scope': receipt['certificate'],
                            'reason': 'EXACT_NATIVE_WHOLE_TEMPLATE_FALLBACK'}
        receipts.append(current)
        current = receipt.get('previousFallback')
    return receipts, owners


def source_local_originals(db, binding, frozen):
    """Read committed snapshots; run the full RO validator after finite guards."""
    journal = m.meta(db, 'sourceLocalRetentionJournal')
    if not journal:
        require(not frozen.get('sourceLocalRetentionJournal'), 'Frozen original journal disappeared')
        return None, {}
    require(journal['sourceBinding'] == binding
            and frozen.get('sourceLocalRetentionJournal') == journal,
            'Automatic original journal differs from the committed apply')
    generation = m.meta(db, 'compositionGeneration')
    require(generation and generation['compositionsSha256'] == m.meta(db, 'compositionsSha256'),
            'Automatic original coverage has no final composition generation')
    # The current composer binds this journal in frozenInputs, not in its raw
    # compositionGeneration. Accept that actual schema; check the latter only
    # if a future admitted composer explicitly supplies the existing binding.
    if 'sourceLocalRetentionJournal' in generation:
        require(generation['sourceLocalRetentionJournal'] == journal,
                'Original journal changed after final composition')
    if 'nativeConsumerSourceFiles' in frozen:
        require(frozen['nativeConsumerSourceFiles'] == certificates.source_files(m),
                'Final coverage uses different admitted native consumer sources')
    owners, events = {}, []
    for sequence, encoded, digest in db.execute('SELECT sequence,data,digest FROM source_local_retentions ORDER BY sequence'):
        event = json.loads(encoded)
        events.append({'sequence': sequence, 'digest': digest, 'sourceFiles': event['sourceFiles']})
        for domain in event['originalDomains']:
            obj = domain['object']; identity = (obj['dim'], obj['uuid'])
            require(identity not in owners or owners[identity]['domain'] == domain,
                    'Conflicting committed original snapshots for one owner')
            if identity not in owners:
                owners[identity] = {'domain': domain, 'events': [],
                                    'reason': 'AUTOMATIC_DIRECT_ORIGINAL_NATIVE_MEMBER_RETENTION'}
            owners[identity]['events'].append({'sequence': sequence, 'digest': digest})
    return {'head': journal, 'compositionGeneration': generation,
            'events': events, 'originalOwners': len(owners)}, owners


def projected_original_with_bound_append(db, owner, at, policy):
    """Existing consumer predicate with its admitted strict-append comparator.

    Keep the frozen writer untouched. Only the historical export binding may
    differ through SourceVisualReservations.domain_equal's existing proof.
    """
    journal = m.meta(db, 'sourceLocalRetentionJournal')
    if not journal or policy['policy'] not in ('nonroot_guest_overlay', 'root_existing_be_background'):
        return False
    head = db.execute('SELECT digest FROM source_local_retentions WHERE sequence=?', (journal['events'],)).fetchone()
    require(journal.get('schemaVersion') == 1 and journal['sourceBinding'] == m.meta(db, 'binding')
            and head == (journal['head'],), 'Original section journal head changed')
    for sequence, encoded, digest in db.execute('SELECT sequence,data,digest FROM source_local_retentions WHERE instr(data,?)>0 ORDER BY sequence DESC', (m.canonical(owner['uuid']),)):
        event = json.loads(encoded); zone = event.get('sourceZone')
        if not zone or zone['dimension'] != owner['dimension']:
            continue
        domains = [row for row in event['originalDomains'] if row['object'] == owner['object']
                   and row['mechanism'] == 'source_original_snapshot' and any(point['position'] == list(at) for point in row['domain'])]
        if not domains:
            continue
        require(len(domains) == 1 and 1 <= sequence <= journal['events'] and m.digest(event) == digest
                and event['sourceBinding'] == journal['sourceBinding']
                and event['kind'] == 'AUTOMATIC_DIRECT_ORIGINAL_NATIVE_MEMBER_RETENTION'
                and event['processedRowsReset'] == 0 and event['ownerGraphTraversal'] is False,
                'Projected original section authority changed')
        plans = [row for row in event['pointPlan'] if row['position'] == list(at)]
        require(len(plans) == 1 and plans[0]['retire'] is True
                and plans[0]['before']['queue'] in ([{'processed': 0}], [{'processed': 1}])
                and not any(plans[0]['remainingDemands'].values()),
                'Projected background was not explicitly retired as undemanded original work')
        certificates.require_source_files(m, db, event['sourceFiles'])
        domain = domains[0]; context = certificates.SourceVisualReservations(m, db)
        try:
            if domain.get('runtimeFootprintEnvelope'):
                context.__dict__.setdefault('_saved_envelopes', {})[owner['object']['pose']] = domain['runtimeFootprintEnvelope']
            require(context.domain_equal(context.original_domain(owner['dimension'], owner['uuid']), domain),
                    'Projected member original source/data domain changed')
            current = certificates.point(m, db, owner['dimension'], at)
            require(not any(current[key] for key in ('queue', 'fragments', 'substrate_fragments', 'native_visual_fragments', 'whole_cells', 'native_visual_roots', 'bridge_cells', 'placements')),
                    'Original projected member acquired replacement work/carrier')
            args = (owner['dimension'], *at)
            require(db.execute('SELECT 1 FROM protected_cells WHERE dim=? AND x=? AND y=? AND z=? AND uuid=? AND reason=?',
                               (*args, owner['uuid'], certificates.LOCAL_ORIGINAL_REASON)).fetchone(),
                    'Original projected member protection disappeared')
            state, _, _ = context.source_point(owner['dimension'], at)
            stored = certificates.n.row_dict(db, 'SELECT * FROM old.stored_contributions WHERE dim=? AND x=? AND y=? AND z=? AND uuid=?', (*args, owner['uuid']))
            require(state == policy['sourceState'] and sorted(stored, key=m.canonical) == sorted(policy['storedContributions'], key=m.canonical),
                    'Historical projected source state/ledger changed')
            return True
        finally:
            context.close()
    return False


def check_members(db, owner, catalog, bridge_gate):
    identity = (owner['dimension'], owner['uuid'])
    require(not present(db, 'selected_objects', identity) and not present(db, 'cleanup_owners', identity)
            and not present(db, 'fragments', identity), 'Retained whole owner entered selection/cleanup/fragments')
    policies = {tuple(row['position']): row for row in owner.get('memberPolicies', [])}
    counts = collections.Counter()
    for at in owner['runtimeMemberCells']:
        policy = policies.get(tuple(at), {'policy': 'original'})
        mode = policy['policy']
        placement, bridge = one(db, 'placements', identity[0], at), one(db, 'bridge_cells', identity[0], at)
        queue = one(db, 'cell_queue', identity[0], at)
        if placement is None and mode in ('nonroot_guest_overlay', 'root_existing_be_background'):
            # The existing consumer proves when a later committed source
            # section retired this historical projected background. Its SQL
            # contracts use tuples; this report otherwise reads sqlite Rows.
            factory = db.row_factory
            try:
                db.row_factory = None
                original = projected_original_with_bound_append(db, owner, at, policy)
            finally:
                db.row_factory = factory
            if original:
                counts['original_source_section'] += 1
                continue
        counts[mode] += 1
        if mode == 'original':
            require(placement is None and bridge is None and queue is None,
                    'Strict original member has replacement work: ' + str((identity, at)))
            require(db.execute('SELECT 1 FROM protected_cells WHERE dim=? AND x=? AND y=? AND z=? AND uuid=?',
                               (identity[0], *at, identity[1])).fetchone(), 'Strict original member lost protection')
            continue
        require(mode in ('nonroot_guest_overlay', 'root_existing_be_background') and placement is not None
                and queue is not None and queue['processed'] == 1, 'Projected member has no completed exact placement')
        index = placement['type_index']
        require(type(index) is int and 0 <= index < len(catalog['cells']), 'Projected member has no compiled type')
        if mode == 'root_existing_be_background':
            host, resident = policy['sourceState'], 1
        elif policy.get('existingBeBridge'):
            host, resident = policy['existingBeBridge']['hostState'], policy['existingBeBridge']['retainedResident']
        else:
            host = None
        if host is None:
            require(bridge is None, 'Plain foreign guest acquired an unproved BE bridge')
        else:
            require(bridge is not None and bridge['type_index'] == index
                    and json.loads(bridge['host_state']) == host and bridge['retained_resident'] == resident,
                    'Projected member carrier/type differs from immutable policy')
            require(bridge_gate is not None, 'Projected BE member lacks the final actual carrier audit')
            cell = catalog['cells'][index]
            require(not cell.get('fluid') and catalog['optics'][cell['optics']].get('luminance', 0) == 0,
                    'Existing BE background must satisfy the accepted dry/nonemitting contract')
    return dict(counts)

def run(args):
    plan = args.plan.resolve()
    require(not args.out.exists(), 'Preserve the first final receipt; use a fresh output path')
    require(args.out.resolve().is_relative_to(RESEARCH), 'Write only a research report, never source/world/database files')
    db = overrides.open_plan(plan)
    db.row_factory = sqlite3.Row
    try:
        db.execute('PRAGMA query_only=ON')
        db.execute('PRAGMA cache_size=-2048')
        db.execute('PRAGMA old.cache_size=-2048')
        db.execute('BEGIN')
        binding = m.meta(db, 'binding')
        require(not m.meta(db, 'scope') and m.meta(db, 'fullNativeFunctionGateRequired') is True,
                'This final coverage is FULL-only, never a cropped pilot')
        require(m.meta(db, 'applyStarted') is True and m.meta(db, 'offlineApplied') is True
                and m.meta(db, 'relightComplete') is True, 'Complete FULL apply and accept native light first')
        applied = m.meta(db, 'preservedAssemblyOverrides')
        override_receipt = bound(applied['receipt'])
        preview = bound(override_receipt['acceptedPreview'])
        require(override_receipt['status'] == 'PASS' and override_receipt['sourceBinding'] == binding
                and applied['manifest'] == ref(args.manifest) == override_receipt['manifest']
                and preview['status'] == 'FINITE_PREVIEW_PASS', 'Accepted exact-pose assembly lineage differs')
        manifest, entries, matches = overrides.load_manifest(db, args.manifest, args.evidence_bindings)
        expected_count = sum(row['objects'] for row in override_receipt['changes'])
        require((len(entries), len(matches), expected_count) ==
                (preview['entryCount'], preview['matchedPoseCount'], preview['matchedObjects']) == (28, 76, 3801),
                'The accepted 28-entry / 76-pose / 3801-object finite scope changed')
        require(override_receipt['evidenceBindings'] == ref(args.evidence_bindings), 'Staged historical evidence binding differs')
        frozen = m.meta(db, 'frozenInputs')
        require(frozen and frozen == m.read(plan / 'frozen-inputs.json') and frozen['sourceBinding'] == binding,
                'Actual apply lost its frozen source/input journal')
        catalog_binding, runtime_binding = m.meta(db, 'catalog'), m.meta(db, 'runtime')
        require(catalog_binding == frozen['catalog'], 'Current catalog differs from the committed apply')
        catalog_path = args.catalog.resolve() if args.catalog else Path(catalog_binding['path'])
        require(ref(catalog_path)['sha256'] == catalog_binding['sha256'], 'Final actual catalog hash differs')
        catalog = m.read(catalog_path)
        require(sha(catalog_path.with_name('meshes.bin')) == catalog_binding['meshesSha256'], 'Final meshes changed')
        templates = {row['key']: row for row in catalog['wholeObjects']}
        require(len(templates) == len(catalog['wholeObjects']), 'Duplicate whole template keys')
        require(sha(runtime_binding['path']) == runtime_binding['sha256'], 'Applied runtime JAR changed')
        for key in ('plannedBeSafety', 'cleanupBeAudit', 'fullNativeInputCoverageGate', 'preservedAssemblyGate'):
            gate = frozen['semanticGates'][key]
            require(bound(gate).get('status') == 'PASS', 'Required accepted apply gate failed: ' + key)
        require(not db.execute("SELECT 1 FROM files WHERE phase!='committed' LIMIT 1").fetchone()
                and not db.execute("SELECT 1 FROM regions WHERE phase!='committed' LIMIT 1").fetchone(),
                'An offline file/region journal remains incomplete')
        journals = {'files': dict(db.execute('SELECT kind,count(*) FROM files GROUP BY kind')),
                    'regions': db.execute('SELECT count(*) FROM regions').fetchone()[0],
                    'frozenInputs': ref(plan / 'frozen-inputs.json')}
        light_binding_path = plan / 'relight-binding.json'
        light_binding = m.read(light_binding_path)
        light_path = args.light_receipt or Path(light_binding['engineReceipt'])
        light = m.read(light_path)
        require(light_binding['target'] == m.meta(db, 'target') and light_binding['catalog'] == catalog_binding
                and light_binding['runtime'] == runtime_binding and light.get('status') == 'PASS'
                and light.get('nativeEngine') is True and light.get('saveFlushed') is True
                and light.get('world') == light_binding['target'] and light.get('pending') == 0
                and light.get('runtimeSha256') == runtime_binding['sha256']
                and light.get('catalogSha256') == catalog_binding['sha256']
                and light.get('queueSha256') == light_binding['queueSha256']
                and light.get('queued') == light.get('processed') == light_binding['queued'], 'Actual FULL light receipt differs')
        assembly_phase = components.accept_full_light(db, light_binding, light)
        component_ref = m.meta(db, 'preservedAssemblyComponents')
        component_receipt = bound(component_ref)
        component_doc, component_rows, anchors = components.load(db, component_receipt['componentBindings']['path'])
        require(component_receipt['status'] == 'PASS' and component_receipt['sourceBinding'] == binding
                and component_receipt['poseOverrides'] == applied['receipt']
                and component_receipt['manifest'] == applied['manifest']
                and components.fingerprint(component_rows) == component_receipt['matchedObjectsAfterSha256']
                and len(anchors) == 4 and len(component_rows) == 8
                and not components.conflicts(db, component_rows, compiled=True), 'Four original component assemblies lost their bound source/plan preservation')
        assembly_anchors = {row['groupKey']: row for row in anchors}
        receipts, retained_native, retained_owners = retention_chain(db, binding)
        fallback_receipts, fallback_owners = whole_template_fallback_chain(db, binding)
        require(not (retained_owners.keys() & fallback_owners.keys()),
                'A W-only fallback duplicates a separately retained original owner')
        retained_owners.update(fallback_owners)
        source_local_journal, source_local_owners = source_local_originals(db, binding, frozen)
        preflight = bound(frozen['semanticGates']['functionalGuestPreflight'])
        require(preflight['status'] == 'PASS' and preflight['graphClosure'] is False,
                'Functional local preservation lost its committed finite preflight')
        functional_decisions = {m.canonical(detail) for detail in preflight['conflicts']}
        issue_rows = collections.defaultdict(list)
        for row in db.execute("SELECT * FROM issues WHERE kind IN ('native_local_retention','whole_local_overlap','protected_local_overlap','missing_exported_footprint','functional_guest')"):
            detail = json.loads(row['detail'])
            owners = detail.get('directlyConflictingOwners', [])
            require(isinstance(owners, list) and all(isinstance(uid, str) and uid for uid in owners),
                    'Functional preservation owner list has invalid identities')
            if owners:
                require(row['kind'] == 'functional_guest' and detail.get('dimension') == row['dim']
                        and m.canonical(detail) in functional_decisions,
                        'Functional owner-list decision differs from its frozen preflight')
            identities = set(owners)
            if detail.get('uuid'):
                identities.add(detail['uuid'])
            for uid in identities:
                issue_rows[(row['dim'], uid)].append({'kind': row['kind'], 'detail': detail})
        objects, fingerprint, actual_by_pose = {}, hashlib.sha256(), collections.Counter()
        for pose in sorted(matches):
            data = db.execute('SELECT data FROM poses WHERE key=?', (pose,)).fetchone()[0]
            require(hashlib.sha256(data.encode()).hexdigest() == matches[pose]['poseDataSha256'], 'Exact saved SourcePose bytes changed')
            for row in db.execute('SELECT ' + ','.join(OBJECT_FIELDS) + ' FROM objects INDEXED BY object_pose WHERE pose=? ORDER BY dim,uuid', (pose,)):
                key = (row['dim'], row['uuid'])
                require(key not in objects, 'Duplicate known assembly identity')
                objects[key] = dict(row)
                actual_by_pose[pose] += 1
                fingerprint.update((m.canonical(tuple(row)) + '\n').encode())
        require(len(objects) == expected_count and fingerprint.hexdigest() == override_receipt['matchedObjectsAfterSha256'],
                'Accepted finite assembly identities/kinds/reasons changed since override import')
        for row in override_receipt['matchedPoses']:
            require(actual_by_pose[row['pose']] == sum(row['objectsByIntendedKind'].values()), 'Known per-pose owner population changed')
        # One pass over the already materialized finite role table, not the world
        # or the city owner population; no unindexed dim-only query per owner.
        roles = collections.defaultdict(list)
        for row in db.execute('SELECT * FROM whole_cells ORDER BY dim,x,y,z'):
            if (row['dim'], row['uuid']) in objects:
                roles[(row['dim'], row['uuid'])].append(dict(row))
        historic = m.read(args.historic_v12)
        protected = m.read(args.protected_v12)
        require(protected['evidenceSha256'] == sha(args.historic_v12) and historic['revision'] == 'V12', 'Original V12 gallery authority differs')
        originals = {row['registry'] for row in historic['exhibits'] if row.get('registry') and row.get('kind') in ('architecture', 'block', 'rp_block', 'rp_object')}
        protected_rows = {row['registryId']: row for row in protected['entries']}
        require(all(protected_rows.get(rid, {}).get('reason') == 'historic_v12_original' for rid in originals), 'An original gallery ID lost protection')
        require(all(row['reason'] == 'historic_v12_original' or (row['reason'] == 'historic_v12_alias' and row['evidenceRegistry'] in originals)
                    for row in protected_rows.values()), 'A later imported family was confused with original V12 authority')
        classification = bound(frozen['classification'])
        require(set(protected_rows) <= set(classification['protectedRegistryIds']), 'An original V12 ID/alias lost classifier priority')
        bridge_gate = m.meta(db, 'bridgeCarrierGate')
        if bridge_gate:
            value = bound(bridge_gate)
            require(value['status'] == 'PASS' and value['runtimeSha256'] == runtime_binding['sha256']
                    and value['catalogSha256'] == catalog_binding['sha256'], 'Actual final carrier audit binding differs')
        results, counts = [], collections.Counter()
        for identity, obj in sorted(objects.items()):
            pose = obj['pose']; root = [obj[k] for k in ('x', 'y', 'z')]
            pose_data = json.loads(db.execute('SELECT data FROM poses WHERE key=?', (pose,)).fetchone()[0])
            protected_original = pose_data['registryId'] in protected_rows
            require(not present(db, 'fragments', identity) and not present(db, 'activated_native', identity),
                    'A whole assembly became independent architecture/native substrate: ' + str(identity))
            selected = db.execute('SELECT * FROM selected_objects WHERE dim=? AND uuid=?', identity).fetchone()
            result = {'dimension': identity[0], 'uuid': identity[1], 'pose': pose, 'root': root,
                      'assemblyKeys': matches[pose]['entries'], 'originalV12IdentityRequired': protected_original}
            if selected:
                require(selected['kind'] == obj['kind'] == 'whole' and not protected_original, 'Selected static/original-V12 assembly is forbidden')
                template_key = (m.meta(db, 'wholeTemplates') or {}).get(pose)
                require(template_key in templates, 'Selected whole has no actual compiled template')
                template = templates[template_key]
                expected = {tuple(v['offset']): v['cell'] for v in template['cells']}
                require(len(expected) == len(template['cells']) == len(roles[identity]) and (0, 0, 0) in expected
                        and template['baseCell'] == expected[(0, 0, 0)], 'Whole role set/base is incomplete')
                seen = set()
                for role in roles[identity]:
                    at = [role[k] for k in ('x', 'y', 'z')]; offset = tuple(role[k] for k in ('dx', 'dy', 'dz'))
                    require(offset not in seen and role['template'] == template_key and at == [root[i] + offset[i] for i in range(3)], 'Wrong or duplicate whole role identity')
                    seen.add(offset); index = expected[offset]
                    placement = one(db, 'placements', identity[0], at)
                    key = 'whole:' + template_key + ':' + m.canonical(list(offset))
                    require(0 <= index < len(catalog['cells']) and placement is not None
                            and placement['composition'] == key and placement['type_index'] == index
                            and one(db, 'bridge_cells', identity[0], at) is None, 'Actual whole placement/type mapping differs from its finite compiled role')
                require(seen == set(expected) and present(db, 'cleanup_owners', identity), 'Selected whole cleanup or complete role mapping missing')
                result.update(route='COMPILED_INDEPENDENT_WHOLE', template=template_key, roles=len(seen), baseCell=template['baseCell'])
            else:
                require(not roles[identity] and not present(db, 'cleanup_owners', identity), 'Local original whole has roles or destructive cleanup')
                finite = retained_owners.get(identity)
                local = source_local_owners.get(identity)
                reasons = issue_rows.get(identity, [])
                require(obj['kind'] == 'preserve' or finite or local or reasons, 'Unselected whole lacks a concrete local preservation decision')
                require(obj['reason'] or finite or local or reasons, 'Local preservation has no reason')
                placement, bridge = one(db, 'placements', identity[0], root), one(db, 'bridge_cells', identity[0], root)
                if placement:
                    require(not protected_original and bridge is not None and bridge['type_index'] == placement['type_index']
                            and bridge['retained_resident'] == 1 and json.loads(bridge['host_state']) ==
                            {'registryId': pose_data['registryId'], 'properties': pose_data['properties']}
                            and bridge_gate is not None, 'Local original root is overwritten or has an unproved carrier')
                if protected_original:
                    require(obj['kind'] == 'preserve' and placement is None and bridge is None, 'Original V12 registry/state must remain strict original')
                result.update(route='LOCAL_ORIGINAL_WHOLE', reason=finite['reason'] if finite else local['reason'] if local else obj['reason'], issues=reasons)
                if local:
                    # Full original state/BE/chunk snapshots and saved ledger
                    # digests stay authoring evidence, never runtime resources.
                    result['sourceLocalOriginal'] = local
                if identity in component_rows:
                    anchor = assembly_anchors[component_rows[identity]['groupKey']]
                    result.update(route='LOCAL_ORIGINAL_ASSEMBLY_RUNTIME_MERGED',
                                  reason='EXACT_ORIGINAL_RECIPE_ACTUALLY_MERGED_AND_SAVED_AS_ONE_PRIMARY',
                                  groupKey=anchor['groupKey'], actualAssemblyPhase=assembly_phase,
                                  primaryOwnerUuid=anchor['primaryOwnerUuid'],
                                  originalComponentOwnerRetired=identity[1] in anchor['retiredOwnerUuids'],
                                  currentMainRegistryId=anchor['expectedRegistryId'],
                                  installationPose=anchor['expectedInstallationPose'])
                if finite:
                    result.update(retention=finite['receipt'], memberPolicyCounts=check_members(db, finite['row'], catalog, bridge_gate))
            counts[result['route']] += 1
            results.append(result)
        retention_members = None
        if args.check_retention_members:
            policy_counts = collections.Counter()
            for identity, item in retained_native.items():
                require(not present(db, 'selected_objects', identity) and not present(db, 'activated_native', identity)
                        and not present(db, 'cleanup_owners', identity), 'Retained native entered activation/cleanup')
                for at in item['row']['mustRemainOriginalCells']:
                    require(one(db, 'placements', identity[0], at) is None and one(db, 'bridge_cells', identity[0], at) is None
                            and one(db, 'cell_queue', identity[0], at) is None, 'Strict retained native physical member has a placement')
                    require(db.execute('SELECT 1 FROM protected_cells WHERE dim=? AND x=? AND y=? AND z=? AND uuid=?',
                                       (identity[0], *at, identity[1])).fetchone(), 'Retained native member protection missing')
            for item in retained_owners.values():
                policy_counts.update(check_members(db, item['row'], catalog, bridge_gate))
            retention_members = {'nativeComponents': len(retained_native), 'originalOwners': len(retained_owners), 'memberPolicyCounts': dict(policy_counts)}
        if source_local_journal:
            # Fail fast on finite report/schema errors. The same mandatory
            # complete source/data validator still runs before any PASS in
            # this unchanged query-only BEGIN transaction.
            print(json.dumps({'status': 'FINITE_COVERAGE_GUARDS_COMPLETE_VALIDATING_ORIGINAL_JOURNAL',
                              'knownObjects': len(results), 'journalEvents': source_local_journal['head']['events']}), flush=True)
            certificates.validate_source_local_retentions(m, db, finalized=True)
        covered_entries = {key for result in results for key in result['assemblyKeys']}
        entry_coverage = [{'assemblyKey': key, 'knownObjects': sum(key in row['assemblyKeys'] for row in results),
                           'boundDecisionSources': entry['decisionSources'], 'preservation': entry['preservation']}
                          for key, entry in sorted(entries.items())]
        mapping = ref(args.mapping) if args.mapping else None
        return {'schemaVersion': 1, 'status': 'PASS', 'kind': 'FINAL_FINITE_PRESERVED_ASSEMBLY_COVERAGE',
                'evidenceLevel': 'SAVED_PLAN_COMMITTED_APPLY_AND_ACTUAL_FULL_RELIGHT_ASSEMBLIES',
                'sourceBinding': binding, 'manifest': ref(args.manifest), 'poseOverrides': applied['receipt'],
                'acceptedPosePreview': override_receipt['acceptedPreview'], 'componentOverrides': component_ref,
                'catalog': catalog_binding, 'runtime': runtime_binding, 'mapping': mapping,
                'lightBinding': ref(light_binding_path), 'nativeLightReceipt': ref(light_path), 'actualFourAssemblyPhase': assembly_phase,
                'counts': {'manifestEntries': len(entries), 'matchedPoses': len(matches), 'knownObjects': len(results),
                           'manifestEntriesWithKnownObjects': len(covered_entries), 'actualComponentGroupsMerged': len(anchors),
                           'originalComponentOwnersBound': len(component_rows), **dict(counts)},
                'entries': entry_coverage, 'objects': results, 'matchedObjectsAfterSha256': fingerprint.hexdigest(),
                'originalV12': {'gallery': ref(args.historic_v12), 'protectedIds': ref(args.protected_v12),
                               'originalGalleryRegistryIds': sorted(originals), 'explicitHistoricAliases': sorted(set(protected_rows) - originals),
                               'classification': frozen['classification'], 'scope': 'EXISTING_GALLERY_POLICY_AND_ACCEPTED_APPLY_JOURNALS_NO_NEW_WORLD_CENSUS',
                               'noNewWorldInstanceEnumeration': True,
                               'laterImportVersionNumbersAreNotOriginalV12Authority': True},
                'committedApplyJournals': journals, 'retentionReceipts': receipts,
                'wholeTemplateFallbackReceipts': fallback_receipts, 'retentionMembers': retention_members,
                **({'sourceLocalRetentionJournal': source_local_journal} if source_local_journal else {}),
                'ownerGraphTraversal': False, 'newCensus': False, 'worldFilesRead': 0, 'worldQueries': 0,
                'databaseWrites': 0, 'mapWrites': 0, 'gameplayChecksRun': False, 'gameProcessesStarted': 0,
                'generator': ref(__file__)}
    finally:
        db.close()

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--plan', type=Path, default=RESEARCH / 'native-grid-plan')
    parser.add_argument('--manifest', type=Path, default=RESEARCH / 'preserved-assemblies.json')
    parser.add_argument('--evidence-bindings', type=Path, default=RESEARCH / 'preserved-assembly-evidence-bindings.json')
    parser.add_argument('--historic-v12', type=Path, default=RESEARCH / 'historic-v12-gallery-manifest.json')
    parser.add_argument('--protected-v12', type=Path, default=SOURCE / 'tools/native-grid/protected-v12-ids.json')
    parser.add_argument('--catalog', type=Path, help='Optional explicit actual FULL catalog; must match plan.meta.catalog')
    parser.add_argument('--mapping', type=Path, help='Optional provenance of the final compiler mapping; whole-role mapping is checked directly against catalog and placements')
    parser.add_argument('--light-receipt', type=Path, help='Default is the exact engineReceipt from the actual full relight binding')
    parser.add_argument('--check-retention-members', action='store_true', help='Also check every finite C/D strict member and projected guest/BE carrier in the committed retention chain')
    parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args()
    try:
        report = run(args)
        args.out.parent.mkdir(parents=True, exist_ok=True)
        with args.out.open('x', encoding='utf-8') as stream:
            stream.write(json.dumps(report, ensure_ascii=False, indent=2) + '\n')
        print(m.canonical({'status': report['status'], 'out': str(args.out), 'sha256': sha(args.out), 'counts': report['counts']}))
    except BaseException:
        error = args.out.with_name(args.out.stem + '.error.json')
        if error.resolve().is_relative_to(RESEARCH) and not error.exists():
            error.parent.mkdir(parents=True, exist_ok=True)
            error.write_text(json.dumps({'status': 'FAIL', 'error': traceback.format_exc(), 'worldFilesRead': 0,
                                         'databaseWrites': 0, 'mapWrites': 0}, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
        raise

if __name__ == '__main__':
    main()
