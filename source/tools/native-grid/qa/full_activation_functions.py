"""Finite FULL post-activation function supplement; never repeat source census.

snapshot reads only the completed SAME-plan derived bridge/placement tables.
produce reads bound files and immutable class/resource evidence, never SQLite.
Neither command opens a world, starts a JVM, exports geometry, or mutates a DB.
Unknown functions/flags/roles remain BLOCKED. Initial FULL evidence stays intact.
"""
from __future__ import annotations
import argparse
import collections
import copy
import hashlib
import importlib.util
import json
import sqlite3
import zipfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
FROZEN_PRODUCER_SHA = 'f42425c3b671905a99c7465daba2aaf50612066a73af2b65cb1ddae9042362a5'
COMPOSITE = 'dev.dreamwalker.bloodbornedw.composite.'
COMPACT_CLASSES = {COMPOSITE + 'ImportedCompactBlock' + suffix
                   for suffix in ('', '$Brick', '$Deepslate', '$Banked', '$SourceOptics')}
HOST_CLASSES = COMPACT_CLASSES | {COMPOSITE + name for name in
                                ('ImportedRootBlock', 'ImportedArtworkBlock', 'SourceOriginalTreeBlock',
                                 'ImportedStairBlock', 'ImportedStairBlock$Trimmed')}


def frozen():
    path = HERE / 'full_native_functions.py'
    require(sha(path) == FROZEN_PRODUCER_SHA, 'Original FULL producer bytes changed')
    spec = importlib.util.spec_from_file_location('frozen_full_native_functions', path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def require(value, message):
    if not value:
        raise ValueError(message)


def sha(path):
    digest = hashlib.sha256()
    with Path(path).open('rb') as stream:
        for block in iter(lambda: stream.read(1 << 20), b''):
            digest.update(block)
    return digest.hexdigest()


def read(path):
    return json.loads(Path(path).read_text(encoding='utf-8-sig'))


def canonical(value):
    return json.dumps(value, sort_keys=True, separators=(',', ':'))


def binding(path):
    path = Path(path).resolve(strict=True)
    return {'path': str(path), 'sha256': sha(path)}


def checked(path, digest):
    value = binding(path)
    require(value['sha256'] == digest, 'Explicit input SHA changed: ' + str(path))
    return value


def bound(value):
    require(sha(value['path']) == value['sha256'], 'Bound evidence changed: ' + str(value['path']))
    return read(value['path'])


def prior_gate(f, path, digest):
    proof = checked(path, digest)
    doc = read(path)
    require(doc['status'] == 'PASS' and doc['scope'] == 'FULL_CITY' and
            doc['sourceJarSha256'] == f.SOURCE_SHA, 'Need the accepted immutable FULL function gate')
    require(sha(doc['sourceJar']) == f.SOURCE_SHA, 'Original source JAR changed')
    source, required = f.inputs(doc['inputEvidence']['path'])
    require(sha(doc['inputEvidence']['path']) == doc['inputEvidence']['sha256'] and
            source['sourceBinding'] == doc['sourceBinding'] and source['coverage'] == doc['coverage'],
            'Accepted initial FULL source evidence/counters changed')
    states = {f.state_key(row['state']): row for row in doc['states']}
    require(len(states) == len(doc['states']) and set(required) <= set(states),
            'Prior FULL gate lost its complete initial exact-state coverage')
    return proof, doc, source, states


def current_imported_gate(f, path, digest, prior_ref, prior):
    reference, document, _, states = prior_gate(f, path, digest)
    require(document['sourceBinding'] == prior['sourceBinding'] and
            document['inputEvidence'] == prior['inputEvidence'] and
            document['coverage'] == prior['coverage'] and
            document.get('priorFullFunctionGate') == prior_ref and
            document.get('originalFullInputEvidenceUnchanged') is True,
            'Current imported gate must preserve the original FULL census and explicit prior gate')
    return reference, states


def user_static_permission(a, f, prior_ref, prior, old):
    require((a.user_static_permission is None) == (a.user_static_permission_sha256 is None),
            'User static permission requires both an explicit file and its SHA')
    if a.user_static_permission is None:
        return None, None, {}
    reference = checked(a.user_static_permission, a.user_static_permission_sha256)
    doc = read(reference['path'])
    require(doc.get('schemaVersion') == 1 and
            doc.get('status') == 'USER_AUTHORIZED_EXACT_STATIC_BAKE_NOT_GAMEPLAY' and
            doc.get('kind') == 'EXACT_FIVE_VANILLA_STATIC_STATES' and
            doc.get('sourceBinding') == prior['sourceBinding'] and
            doc.get('sourceJarSha256') == f.SOURCE_SHA and
            doc.get('priorFullFunctionGate') == prior_ref and
            prior_ref['sha256'] == '35394c6c93ae00202070a17fb362ce0e453c433008d10be02631800d6f0672e5',
            'User permission must bind the accepted prior gate, exact source and explicit five-state authority')
    authority = doc['userAuthority']
    checked(authority['path'], authority['sha256'])
    require(Path(authority['path']).stat().st_size > 0 and
            doc.get('preserve') == {'model': True, 'collision': True, 'luminance': True},
            'User authority must preserve exact appearance/collision/emission')
    expected = {f.state_key({'registryId': 'minecraft:light',
                            'properties': {'waterlogged': 'false', 'level': str(level)}}):
                (level, 'net.minecraft.class_6089', ['change_light_level', 'waterlogging'])
                for level in (1, 2, 4, 5)}
    expected[f.state_key({'registryId': 'minecraft:andesite_slab',
                          'properties': {'type': 'bottom', 'waterlogged': 'false'}})] = (
                              0, 'net.minecraft.class_2482', ['slab_merge', 'waterlogging'])
    require(isinstance(doc.get('states'), list) and len(doc['states']) == 5,
            'Only the five explicitly approved vanilla state tuples may change decision')
    permitted = {}
    for row in doc['states']:
        key = f.state_key(row['state'])
        require(key in expected and key not in permitted and
                row.get('previousDecision') == 'retain_local' and row.get('decision') == 'static_base' and
                row.get('lostInteractions') == expected[key][2] and
                (row.get('expectedLuminance') is None or
                 type(row['expectedLuminance']) is int and row['expectedLuminance'] == expected[key][0]) and
                key in old and old[key]['decision'] == 'retain_local' and
                not old[key].get('decisionProvisional'),
                'User permission widened the exact states, prior decision or accepted lost interactions')
        permitted[key] = {'permission': copy.deepcopy(row), 'luminance': expected[key][0],
                          'className': expected[key][1]}
    require(set(permitted) == set(expected), 'User permission omitted an approved exact state')
    return reference, doc, permitted


def snapshot(a, f):
    prior_ref, prior, source, old = prior_gate(f, a.prior_full_gate, a.prior_full_sha256)
    require((a.current_imported_full_gate is None) == (a.current_imported_full_sha256 is None),
            'Current imported FULL gate requires both a file and its explicit SHA')
    imported_ref, imported_states = prior_ref, old
    if a.current_imported_full_gate is not None:
        imported_ref, imported_states = current_imported_gate(
            f, a.current_imported_full_gate, a.current_imported_full_sha256, prior_ref, prior)
    raw_ref = checked(a.compositions, a.raw_sha256)
    coverage_ref = checked(a.coverage, a.coverage_sha256)
    needed_ref = checked(a.native_needed, a.native_needed_sha256)
    coverage = read(a.coverage)
    require(coverage['kind'] == 'ACTUAL_POST_ACTIVATION_NATIVE_INPUT_GATE' and
            coverage['scope'] == 'FULL_CITY' and coverage['sourceBinding'] == prior['sourceBinding'] and
            coverage['status'] in ('PASS', 'BLOCKED_FINITE_INPUTS') and
            coverage['nativeFunctionGate']['path'] == imported_ref['path'] and
            coverage['nativeFunctionGate']['sha256'] == imported_ref['sha256'] and
            coverage['baselineJar']['sha256'] == f.SOURCE_SHA,
            'Only the actual completed FULL post-activation coverage is input')
    require(coverage['compositionsSha256'] == raw_ref['sha256'] and
            coverage['nativeStatesNeededSha256'] == needed_ref['sha256'], 'Final raw/needed generation differs')
    require(all(coverage.get(key) == 0 for key in ('worldFilesRead', 'persistentDatabaseWrites', 'mapWrites')),
            'Post-activation checker exceeded its read-only scope')
    needed = {}
    for item in read(a.native_needed)['nativeStates']:
        state = {key: item[key] for key in ('registryId', 'properties')}
        key = f.state_key(state)
        require(key not in needed, 'Duplicate exact final native-needed state')
        needed[key] = state
    require(len(needed) == coverage['actualNeededStates'], 'Final native-needed coverage count differs')
    base_counts = collections.Counter()
    base_witnesses = collections.defaultdict(list)
    raw_rows = 0
    with Path(a.compositions).open(encoding='utf-8') as stream:
        for line in stream:
            row = json.loads(line)
            raw_rows += 1
            if row.get('baseState') is None:
                continue
            key = f.state_key(row['baseState'])
            require(key in needed, 'Actual raw base is absent from final native-needed input')
            base_counts[key] += 1
            if len(base_witnesses[key]) < 8:
                base_witnesses[key].append(row['key'])
    # The needed file contains actual compiled bases, not arbitrary desired states.
    require(set(base_counts) == set(needed), 'Native-needed and actual raw base-state tuples differ')
    database = Path(a.plan).resolve(strict=True) / 'plan.sqlite'
    db = sqlite3.connect('file:' + database.as_posix() + '?mode=ro', uri=True)
    try:
        db.execute('PRAGMA query_only=ON')
        db.execute('PRAGMA cache_size=-2048')
        db.execute('BEGIN')
        names = ('binding', 'scope', 'fullNativeFunctionGateRequired', 'fullNativeFunctionGate',
                 'compositionGeneration', 'applyStarted', 'offlineApplied')
        meta = {key: json.loads(value) for key, value in db.execute(
            'SELECT key,value FROM meta WHERE key IN (' + ','.join('?' for _ in names) + ')', names)}
        require(not meta.get('scope') and meta.get('fullNativeFunctionGateRequired') is True and
                not meta.get('applyStarted') and not meta.get('offlineApplied'), 'Snapshot only before FULL apply')
        require(meta['binding'] == prior['sourceBinding'] and
                meta['compositionGeneration'] == coverage['compositionGeneration'] and
                meta['compositionGeneration']['compositionsSha256'] == raw_ref['sha256'],
                'Derived bridge snapshot belongs to another CURRENT generation')
        gate = meta['fullNativeFunctionGate']
        require(gate['path'] == imported_ref['path'] and gate['sha256'] == imported_ref['sha256'] and
                gate['states'] == len(imported_states) and gate['candidateObjects'] == prior['coverage']['candidateObjects'],
                'The accepted FULL gate changed; no source-object recount is performed')
        if a.current_imported_full_gate is not None:
            require(coverage['nativeFunctionGate'] == gate,
                    'New coverage must bind the complete actual imported gate metadata')
        hosts = {}
        digest = hashlib.sha256()
        bridge_cells = 0
        sql = ('SELECT b.dim,b.x,b.y,b.z,b.host_state,b.retained_resident FROM bridge_cells b '
               'JOIN placements p ON p.dim=b.dim AND p.x=b.x AND p.y=b.y AND p.z=b.z '
               'ORDER BY b.host_state,b.dim,b.x,b.y,b.z')
        for dimension, x, y, z, text, resident in db.execute(sql):
            state = json.loads(text)
            key = f.state_key(state)
            require(resident in (0, 1), 'Unknown existing carrier resident flag')
            digest.update((canonical([dimension, x, y, z, state, resident]) + '\n').encode())
            row = hosts.setdefault(key, {'state': state, 'cells': 0, 'witnesses': []})
            row['cells'] += 1
            bridge_cells += 1
            if len(row['witnesses']) < 8:
                row['witnesses'].append({'dimension': dimension, 'position': [x, y, z], 'retainedResident': resident})
        require(bridge_cells == coverage['counts']['bridge_cells'],
                'Complete CURRENT bridge/placement intersection differs from final coverage')
        require(sha(a.compositions) == raw_ref['sha256'] and sha(a.native_needed) == needed_ref['sha256'] and
                sha(a.coverage) == coverage_ref['sha256'], 'Final files changed during the finite snapshot')
    finally:
        db.close()
    result = {'schemaVersion': 1, 'status': 'READ_ONLY_FINAL_ACTIVATION_FUNCTION_INPUTS_NOT_APPLY_GATE',
              'scope': 'FULL_CITY_POST_ACTIVATION', 'sourceBinding': prior['sourceBinding'],
              'priorFullFunctionGate': prior_ref, 'initialFullInputEvidence': prior['inputEvidence'],
              'initialCandidateObjects': prior['coverage']['candidateObjects'],
              'compositionGeneration': coverage['compositionGeneration'], 'compositions': raw_ref,
              'nativeStatesNeeded': needed_ref, 'finalCoverage': coverage_ref,
              'nativeBaseStates': [{'state': needed[key], 'compositionRows': base_counts[key],
                                    'witnessCompositionKeys': base_witnesses[key]} for key in sorted(needed)],
              'rawCompositionRows': raw_rows, 'bridgeHosts': [hosts[key] for key in sorted(hosts)],
              'bridgeCellCount': bridge_cells, 'bridgeRowsSha256': digest.hexdigest(),
              'sourceCapabilities': source['sourceCapabilities'], 'generator': binding(__file__),
              'sourceCensusRepeated': False, 'sourceObjectsOrCachedChunksRead': 0,
              'worldFilesRead': 0, 'worldsOpened': 0, 'persistentDatabaseWrites': 0, 'mapWrites': 0,
              'gameProcessesStarted': 0}
    if a.current_imported_full_gate is not None:
        checked(imported_ref['path'], imported_ref['sha256'])
        result['currentImportedFullFunctionGate'] = copy.deepcopy(gate)
    f.write_new(a.out, result)
    print(canonical({'status': result['status'], 'out': binding(a.out), 'bases': len(needed),
                     'hostStates': len(hosts), 'bridgeCells': bridge_cells, 'sourceCensusRepeated': False}))
    return 0


def observations(f, prior, a):
    known, proofs = {}, []
    for evidence in prior.get('nativeFlagEvidence', []):
        bound(evidence)
        if evidence['kind'] == 'ACTUAL_SOURCE_STATE_PROPERTIES_NOT_FUNCTION_PROOF':
            for key in ('request', 'helper', 'launch', 'binding'):
                require(sha(evidence[key]['path']) == evidence[key]['sha256'], 'Prior property evidence changed')
            rows, checked_proofs = f.property_flags([Path(evidence['path'])], [Path(evidence['request']['path'])], Path(evidence['helper']['path']))
            for key, row in rows.items():
                f.add_flags(known, json.loads(key), row['flags'], row['evidence'], row['resolvedState'], row['className'], row['hasBlockEntity'])
            proofs += checked_proofs
        elif evidence['kind'] == 'ACTUAL_COMPATIBLE_TARGET_FLAGS_NOT_SOURCE_FUNCTION_PROOF':
            require(sha(evidence['runtime']['path']) == evidence['runtime']['sha256'], 'Prior compatible target JAR changed')
            proofs += f.target_flags([Path(evidence['path'])], Path(evidence['runtime']['path']),
                                     Path(evidence['patch']['path']) if evidence.get('patch') else None, known)
        else:
            raise ValueError('Unknown prior observed-flag authority')
    if a.state_properties:
        rows, checked_proofs = f.property_flags(a.state_properties, a.query_request, a.properties_helper)
        for key, row in rows.items():
            f.add_flags(known, json.loads(key), row['flags'], row['evidence'], row['resolvedState'], row['className'], row['hasBlockEntity'])
        proofs += checked_proofs
    return known, proofs


class ExactSourceRules:
    """Only previously reviewed class branches, with exact current source data.

    Capabilities identify finite registry/class membership, never function flags.
    Every class branch requires exact primary bytecode; registry membership and
    finite recipe/spec metadata are verified separately from function policy.
    """
    def __init__(self, f, archive, policy_paths, known, caps):
        self.f, self.archive, self.known, self.caps = f, archive, known, caps
        self.code, self.resources, self.protected, self.protected_proofs, self.primary_hashes = {}, {}, {}, [], {}
        self.reviewed_passive = {}
        self.reviewed_static_classes = {}
        for value in known.values():
            if value['decision'] == 'static_base':
                self.reviewed_passive.setdefault((value['state']['registryId'], value.get('className')), value['policyEvidence'])
                if value.get('className') in (COMPOSITE + 'StaticArchitectureBlock', COMPOSITE + 'MaterialSlabs$Slab'):
                    self.reviewed_static_classes.setdefault(value['className'], value['policyEvidence'])
        for path in policy_paths:
            policy = read(path)
            for row in policy.get('primaryCode', []):
                self.code[row['className']] = row
            if policy.get('protectedV12Manifest'):
                reference = policy['protectedV12Manifest']
                document = bound(reference)
                self.protected_proofs.append(reference)
                for row in document['entries']:
                    self.protected[row['registryId']] = row
        require(self.protected_proofs, 'Prior protected v12 registry manifest must remain bound')
        self.compact = {row['registryId']: row for row in self.resource('bloodborne_dw/imported-compact-v127.json')['types']}
        self.legacy = self.resource('bloodborne_dw/static-architecture-v1274.json')
        self.slabs = {row['id']: row for row in self.resource('bloodborne_dw/restored-material-slabs.json')['materials']}
        self.fallback = self.resource('bloodborne_dw/source-fallback-carriers-v1271.json')
        self.used_code = {}

    def resource(self, name):
        payload = self.archive.read(name)
        self.resources[name] = hashlib.sha256(payload).hexdigest()
        return json.loads(payload)

    def require_code(self, names):
        if not all(name in self.code for name in names):
            return False
        for name in names:
            row = self.code[name]
            require(hashlib.sha256(self.archive.read(name.replace('.', '/') + '.class')).hexdigest() == row['jarClassSha256'] and
                    sha(row['bytecodeFile']) == row['bytecodeSha256'], 'Accepted exact class/primary bytecode changed')
            self.used_code[name] = row
        return True

    def derive(self, actual, observation):
        state = actual['state']
        identifier = state['registryId']
        cap = self.caps.get(identifier)
        if identifier in self.protected:
            if not cap or not cap.get('registryPresent'):
                return None, 'Protected literal entry lacks actual immutable registry/class membership'
            clazz = cap['className']
            entry = clazz.replace('.', '/') + '.class'
            self.primary_hashes[clazz] = hashlib.sha256(self.archive.read(entry)).hexdigest()
            return {'state': state, 'className': clazz, 'decision': 'retain_local',
                    'reason': 'Exact literal historic v12 registry entry: preserve original state/BE/model/payload/function. Static conversion and NativeGridBase insertion are forbidden for this original object.',
                    'protectedV12Evidence': self.protected_proofs, 'protectedEntry': self.protected[identifier]}, None
        if not cap or not cap.get('registryPresent') or observation is None or observation['resolvedState'] is None:
            return None, 'Missing exact registry membership or actual canonical state query'
        clazz = observation['className']
        if clazz != cap['className']:
            return None, 'Actual source query class differs from immutable finite registry evidence'
        properties = observation['resolvedState']['properties']
        row = {'state': state, 'className': clazz, 'actualCanonicalState': observation['resolvedState'],
               'actualSourcePropertiesEvidence': observation['evidence'], 'postActivationOnly': True}
        root_code = [clazz, COMPOSITE + 'CompositeRootBlock', COMPOSITE + 'CompositeRuntime', COMPOSITE + 'CompositeSpec']
        if actual['host'] and clazz in HOST_CLASSES and observation['hasBlockEntity'] is True:
            if clazz == COMPOSITE + 'ImportedStairBlock$Trimmed':
                root_code.append(COMPOSITE + 'ImportedStairBlock')
            if not self.require_code(root_code):
                return None, 'Actual owned-host class lacks accepted exact primary code evidence'
            if clazz in COMPACT_CLASSES:
                if properties.get('compact') != 'false':
                    return None, 'BE host does not use the reviewed noncompact owned branch'
                if not self.require_code([COMPOSITE + 'ImportedCompactBlock', COMPOSITE + 'ImportedArtworkBlock']):
                    return None, 'Noncompact branch lacks its exact parent bytecode'
            spec_name = 'bloodborne_dw/composite/' + identifier.split(':', 1)[1] + '.json'
            spec = self.resource(spec_name)
            require(spec.get('id') == identifier, 'Exact source specification ID changed')
            row.update(decision='bridge_host_only',
                       reason='Exact actual BE-bearing owned source state and reviewed primary class branch. Preserve its original registry/state/resident/typed payload/ledger and complete spec-driven whole function; never flatten it. Background admission still requires exact saved owner/BE proof, observed dry/nonemitting predicates and final TARGET bridge validation.',
                       sourceSpecSha256=self.resources[spec_name],
                       sourceSpecFunctions={key: spec.get(key) for key in ('openable', 'functionalDoorPhysics', 'importedClimbingBounds', 'support', 'source')},
                       sourceClassEvidence=root_code, retainedOwnObjectMustRemainWhole=True)
            return row, None
        # Two registered finite storage classes use their accepted class route
        # plus exact actual registry/recipe/definition evidence. Other branches
        # still require a previously reviewed exact registry route.
        reviewed_id = (identifier, clazz) in self.reviewed_passive
        reviewed_storage = clazz in self.reviewed_static_classes
        if actual['host'] or not (reviewed_id or reviewed_storage) or observation['hasBlockEntity'] is not False or not observation['flags']['fluidEmpty']:
            return None, 'No reviewed exact registry/finite storage-class route, or actual BE/fluid cannot be flattened'
        if clazz in COMPACT_CLASSES:
            names = root_code + [COMPOSITE + 'ImportedCompactBlock', COMPOSITE + 'ImportedArtworkBlock']
            if not self.require_code(names):
                return None, 'Compact passive branch lacks its accepted primary code'
            definition = self.compact.get(identifier)
            variant = int(properties.get('variant', '-1')) + 16 * int(properties.get('art_bank', '0'))
            spec_name = 'bloodborne_dw/composite/' + identifier.split(':', 1)[1] + '.json'
            spec = self.resource(spec_name)
            if not (definition and properties.get('compact') == 'true' and properties.get('profile') == 'base' and
                    int(properties.get('rotation', '-1')) in (0, 2, 4, 6) and variant in definition['poses'] and
                    len(definition['shapes'][str(variant)]) == 8 and
                    not any(spec.get(key) for key in ('openable', 'functionalDoorPhysics', 'importedClimbingBounds')) and
                    not spec.get('support', {}).get('required', False)):
                return None, 'Exact compact state/spec/variant is outside the previously reviewed passive branch'
            row.update(compactVariant=variant, compactDefinitionSha256=hashlib.sha256(canonical(definition).encode()).hexdigest(), sourceSpecSha256=self.resources[spec_name])
        elif clazz == COMPOSITE + 'StaticArchitectureBlock':
            names = [clazz, COMPOSITE + 'StaticArchitectureCatalog', COMPOSITE + 'StaticArchitectureCatalog$Pose', COMPOSITE + 'StaticArchitectureRuntime']
            if not self.require_code(names):
                return None, 'Static recipe branch lacks accepted exact primary code'
            bank = int(identifier.rsplit('_', 1)[1])
            require(identifier == 'bloodborne_dw:static_architecture_' + str(bank) and 0 <= bank < 256,
                    'State is outside the actual finite static bank registry')
            if set(properties) != {'part', 'waterlogged'} or properties['waterlogged'] != 'false' or not 0 <= int(properties['part']) < 128:
                return None, 'Actual resolved state is outside the exact dry 128-part static bank'
            requested = bank * 128 + int(properties['part'])
            recipe_index = min(requested, len(self.legacy['recipes']) - 1)
            recipe = self.legacy['recipes'][recipe_index]
            row.update(requestedRecipeIndex=requested, recipeIndex=recipe_index, recipeKey=recipe['key'],
                       recipeSha256=hashlib.sha256(canonical(recipe).encode()).hexdigest(),
                       actualRegisteredClass=copy.deepcopy(cap), sourceClassEvidence=names)
        elif clazz == COMPOSITE + 'MaterialSlabs$Slab':
            if not self.require_code([clazz, COMPOSITE + 'MaterialSlabs']) or identifier not in self.slabs or properties.get('waterlogged') != 'false':
                return None, 'Material slab lacks exact dry registered definition/primary evidence'
            if set(properties) != {'type', 'facing', 'waterlogged'} or properties['type'] not in ('bottom', 'top', 'double') or properties['facing'] not in ('north', 'east', 'south', 'west'):
                return None, 'Material slab is outside its actual resolved finite state properties'
            definition = self.slabs[identifier]
            row.update(materialSlabDefinition=definition,
                       materialSlabDefinitionSha256=hashlib.sha256(canonical(definition).encode()).hexdigest(),
                       actualRegisteredClass=copy.deepcopy(cap))
            if 'base' in definition:
                if definition['base'] not in self.slabs:
                    return None, 'Material overlay lacks its exact registered base definition'
                row['materialSlabBaseDefinition'] = self.slabs[definition['base']]
            if 'overlayRecipe' in definition:
                names = [COMPOSITE + 'StaticArchitectureCatalog', COMPOSITE + 'StaticArchitectureRuntime']
                index = definition['overlayRecipe']
                if type(index) is not int or not 0 <= index < len(self.legacy['recipes']) or not self.require_code(names):
                    return None, 'Material overlay lacks its exact native recipe/primary lifecycle evidence'
                recipe = self.legacy['recipes'][index]
                row.update(overlayRecipeIndex=index, overlayRecipeKey=recipe['key'],
                           overlayRecipeSha256=hashlib.sha256(canonical(recipe).encode()).hexdigest())
        elif clazz == COMPOSITE + 'SourceFallbackCarrierV1271':
            if not self.require_code([clazz]) or properties.get('profile') != 'base':
                return None, 'Fallback state lacks its accepted exact passive branch'
            requested = int(properties['source']) + 128 * int(properties['source_bank'])
            index = requested if requested < len(self.fallback['sourceStates']) else 0
            row.update(requestedDescriptorIndex=requested, sourceDescriptorIndex=index, sourceDescriptor=self.fallback['sourceStates'][index])
        else:
            return None, 'No reviewed exact behavior rule for this actual new class/state'
        row.update(decision='static_base',
                   reason='Exact new dry/no-BE state uses the accepted static registry or finite storage-class route, with actual registered class membership, identical reviewed primary bytecode and exact immutable state/spec/recipe metadata. Only the authorized consumed architectural editor/component lifecycle is replaced; no registry-name word supplies a function decision.')
        if reviewed_id:
            row['reviewedRegistryFunctionEvidence'] = self.reviewed_passive[(identifier, clazz)]
        else:
            row['reviewedFiniteStorageClassFunctionEvidence'] = self.reviewed_static_classes[clazz]
        return row, None


def produce(a, f):
    outputs = [a.out.resolve(), a.policy_out.resolve(), a.requests_out.resolve()]
    require(len(set(outputs)) == len(outputs) and all(not path.exists() for path in outputs),
            'Distinct fresh authoring outputs required; preserve prior FULL/3337 files')
    require(a.carrier_audit_dir is None or not a.carrier_audit_dir.exists(),
            'Fresh normalization-only observation directory required')
    prior_ref, prior, source, old = prior_gate(f, a.prior_full_gate, a.prior_full_sha256)
    permission_ref, permission_doc, permitted = user_static_permission(a, f, prior_ref, prior, old)
    snapshot_ref = checked(a.input, a.input_sha256)
    data = read(a.input)
    require(data['status'] == 'READ_ONLY_FINAL_ACTIVATION_FUNCTION_INPUTS_NOT_APPLY_GATE' and
            data['scope'] == 'FULL_CITY_POST_ACTIVATION' and data['sourceBinding'] == prior['sourceBinding'] and
            data['priorFullFunctionGate'] == prior_ref and data['initialFullInputEvidence'] == prior['inputEvidence'],
            'A provisional queued-host list cannot replace the actual FINAL derived snapshot')
    imported = data.get('currentImportedFullFunctionGate')
    if imported is not None:
        _, imported_states = current_imported_gate(f, imported['path'], imported['sha256'], prior_ref, prior)
        require(imported['states'] == len(imported_states) and
                imported['candidateObjects'] == prior['coverage']['candidateObjects'] and
                bound(data['finalCoverage'])['nativeFunctionGate'] == imported,
                'Rebased snapshot lost the actual imported gate/coverage metadata')
    require(data['sourceCensusRepeated'] is False and all(data.get(key) == 0 for key in
            ('sourceObjectsOrCachedChunksRead', 'worldFilesRead', 'worldsOpened', 'persistentDatabaseWrites', 'mapWrites', 'gameProcessesStarted')),
            'Final snapshot exceeded its finite saved-plan scope')
    for name in ('compositions', 'nativeStatesNeeded', 'finalCoverage'):
        require(sha(data[name]['path']) == data[name]['sha256'], 'Final activation input changed: ' + name)
    require(sha(a.source_jar) == f.SOURCE_SHA, 'Only immutable40ce supplies source function proof')
    policy_paths = [Path(proof['path']) for proof in prior['policyEvidence']] + list(a.policy)
    for proof in prior['policyEvidence']:
        require(sha(proof['path']) == proof['sha256'], 'Accepted prior exact policy changed')
    with zipfile.ZipFile(a.source_jar) as archive:
        known, policy_proofs = f.policies(policy_paths, data['sourceBinding'], archive)
        observed, flag_proofs = observations(f, prior, a)
        caps_doc = bound(source['sourceCapabilities'])
        caps = {row['registryId']: row for row in caps_doc['states']}
        rules = ExactSourceRules(f, archive, policy_paths, known, caps)
        required = {}
        for group, host in (('nativeBaseStates', False), ('bridgeHosts', True)):
            require(isinstance(data[group], list), 'Missing actual final derived state group')
            seen = set()
            for actual in data[group]:
                key = f.state_key(actual['state'])
                require(key not in seen, 'Repeated exact state in a final role group')
                seen.add(key)
                count = actual['cells' if host else 'compositionRows']
                require(type(count) is int and count > 0, 'Invalid finite final role count')
                entry = required.setdefault(key, {'state': actual['state'], 'host': False, 'base': False, 'roles': {}})
                entry['host' if host else 'base'] = True
                entry['roles']['bridgeHostCells' if host else 'nativeBaseCompositionRows'] = count
        require(required and sum(row['cells'] for row in data['bridgeHosts']) == data['bridgeCellCount'], 'Incomplete final bridge counts')
        rows = copy.deepcopy(old)
        missing_functions, missing_flags, role_conflicts, requests, added_policy = [], [], [], {}, []
        permission_overrides = []
        for key, actual in sorted(required.items()):
            observation = observed.get(key)
            policy = known.get(key)
            derived = None
            authorized = permitted.get(key)
            if authorized is not None:
                require(actual['base'] and not actual['host'] and policy is not None and
                        policy['decision'] == 'retain_local' and
                        policy.get('className') == authorized['className'] and
                        observation is not None and observation['className'] == authorized['className'] and
                        observation['hasBlockEntity'] is False and
                        observation['resolvedState'] == actual['state'] and
                        observation['evidence']['runtimeSha256'] == f.SOURCE_SHA and
                        observation['flags']['fluidEmpty'] is True and
                        observation['flags']['luminance'] == authorized['luminance'],
                        'Approved static bake needs the exact old policy and actual dry/no-BE/source40ce properties')
                policy = {'decision': 'static_base', 'className': authorized['className'],
                          'reason': 'Explicit user-authorized bake of this exact vanilla state. Preserve its exact model, collision and emission; the listed native interactions/callbacks are knowingly lost. This is permission to replace known behavior, not an absence-of-functions proof.',
                          'policyEvidence': permission_ref}
                permission_overrides.append({'state': copy.deepcopy(actual['state']),
                                             'previousFunctionRow': copy.deepcopy(old[key]),
                                             'previousExactPolicy': copy.deepcopy(known[key]),
                                             'decision': 'static_base', 'userStaticPermission': permission_ref,
                                             'userAuthority': permission_doc['userAuthority'],
                                             'lostInteractions': authorized['permission']['lostInteractions'],
                                             'preservationRequired': permission_doc['preserve'],
                                             'actualSourcePropertiesEvidence': observation['evidence'],
                                             'gameplayPass': False})
            elif policy is None:
                derived, reason = rules.derive(actual, observation)
                if derived is None:
                    missing_functions.append({**actual, 'specificReason': reason})
                    if observation is None or observation['resolvedState'] is None:
                        requests[key] = actual['state']
                else:
                    added_policy.append(derived)
                    policy = derived
            if policy is not None and observation is not None:
                require(observation['className'] is None or policy.get('className') in
                        (None, observation['className']),
                        'Actual source query class differs from the exact accepted function policy')
            row = copy.deepcopy(rows.get(key, {'state': actual['state']}))
            if key not in old or authorized is not None:
                if policy is None:
                    row.update(decision='retain_local', decisionProvisional=True,
                               reason='Unproved exact post-activation function; provisional local retention only, supplement BLOCKED')
                else:
                    row.update(decision=policy['decision'], reason=policy['reason'], className=policy.get('className'),
                               functionEvidence=policy.get('policyEvidence', {'derivedPolicyOutput': str(a.policy_out.resolve())}))
                    if authorized is not None:
                        row.update(previousDecision='retain_local', userStaticPermission=permission_ref,
                                   userAuthority=permission_doc['userAuthority'],
                                   lostInteractions=authorized['permission']['lostInteractions'],
                                   preservationRequired=permission_doc['preserve'],
                                   observedNativeFlags=observation['flags'],
                                   observedNativeFlagsEvidence=observation['evidence'],
                                   resolvedNativeState=observation['resolvedState'])
            elif policy is not None:
                require(row['decision'] == policy['decision'], 'Post-activation evidence attempted to rewrite an accepted exact function')
            row['actualPostActivationRoles'] = actual['roles']
            if actual['host']:
                if observation is None:
                    missing_flags.append(actual)
                    requests[key] = actual['state']
                else:
                    if observation['hasBlockEntity'] is False:
                        role_conflicts.append({'state': actual['state'], 'reason': 'An actual planned BE host has no BE in the exact source state query'})
                    row.update(observedNativeFlags=observation['flags'], observedNativeFlagsEvidence=observation['evidence'])
                    if observation['resolvedState'] is not None:
                        row['resolvedNativeState'] = observation['resolvedState']
                    if not observation['flags']['fluidEmpty'] or observation['flags']['luminance'] != 0:
                        role_conflicts.append({'state': actual['state'], 'reason': 'Actual wet/emitting carrier cannot use the existing dry/nonemitting bridge'})
                if row['decision'] != 'bridge_host_only':
                    role_conflicts.append({'state': actual['state'], 'reason': 'Required projected host is not exactly bridge_host_only; retain/unknown/passive is not bridge authorization'})
            if actual['base'] and row['decision'] != 'static_base':
                role_conflicts.append({'state': actual['state'], 'reason': 'An actually compiled native base lacks exact passive function permission'})
            rows[key] = row
        new_policy = {'schemaVersion': 1, 'status': 'PASS' if added_policy or permission_overrides else 'BLOCKED_NO_NEW_PROVED_SUBSET',
                      'scope': 'EXACT_PROVED_POST_ACTIVATION_STATES_ONLY', 'sourceBinding': data['sourceBinding'],
                      'sourceJar': str(a.source_jar.resolve()), 'sourceJarSha256': f.SOURCE_SHA,
                      'priorFullFunctionGate': prior_ref, 'postActivationInputEvidence': snapshot_ref,
                      'states': added_policy, 'primaryCode': [rules.used_code[name] for name in sorted(rules.used_code)],
                      'primaryClassHashes': rules.primary_hashes, 'protectedV12Manifest': rules.protected_proofs[0],
                      'sourceResources': rules.resources, 'sourceCapabilities': source['sourceCapabilities'],
                      'unresolvedStatesExcludedFromThisPolicy': [row['state'] for row in missing_functions],
                      'worldFilesRead': 0, 'mapWrites': 0, 'persistentDatabaseWrites': 0, 'gameProcessesStarted': 0}
        if permission_ref:
            new_policy.update(userStaticPermission=permission_ref,
                              userStaticPermissionOverrides=permission_overrides,
                              userPermissionIsNotPassiveCodeProof=True)
        for proof in policy_proofs:
            require(sha(proof['path']) == proof['sha256'], 'Exact function policy changed during supplement authoring')
    require(sha(a.input) == snapshot_ref['sha256'] and sha(a.prior_full_gate) == prior_ref['sha256'], 'Bound function inputs changed during authoring')
    if permission_ref:
        checked(permission_ref['path'], permission_ref['sha256'])
        checked(permission_doc['userAuthority']['path'], permission_doc['userAuthority']['sha256'])
    for name in ('compositions', 'nativeStatesNeeded', 'finalCoverage'):
        require(sha(data[name]['path']) == data[name]['sha256'], 'Final activation input changed during authoring: ' + name)
    f.write_new(a.policy_out, new_policy)
    for row in rows.values():
        if isinstance(row.get('functionEvidence'), dict) and 'derivedPolicyOutput' in row['functionEvidence']:
            row['functionEvidence'] = binding(a.policy_out)
    ready = not missing_functions and not missing_flags and not role_conflicts
    result = {**copy.deepcopy(prior), 'status': 'PASS' if ready else 'BLOCKED_POST_ACTIVATION_EXACT_FUNCTION_OR_FLAGS',
              'mode': 'APPENDED_FINAL_ACTIVATION_NATIVE_FUNCTION_DECISIONS_NOT_GAMEPLAY',
              'states': [rows[key] for key in sorted(rows)], 'decisions': dict(collections.Counter(row['decision'] for row in rows.values())),
              'requiredActualStates': len(rows), 'priorFullFunctionGate': prior_ref,
              'postActivationInputEvidence': snapshot_ref, 'postActivationCoverage': data['finalCoverage'],
              'postActivationRequiredStates': len(required), 'postActivationBridgeHostStates': len(data['bridgeHosts']),
              'policyEvidence': policy_proofs + ([binding(a.policy_out)] if added_policy else []),
              'nativeFlagEvidence': flag_proofs, 'missingFunctionProofs': missing_functions,
              'missingObservedHostFlags': missing_flags, 'postActivationRoleConflicts': role_conflicts,
              'originalFullInputEvidenceUnchanged': True, 'sourceCensusRepeated': False,
              'producer': binding(__file__), 'frozenInitialProducer': binding(HERE / 'full_native_functions.py'),
              'limits': 'Exact function supplement only. Initial census/counters remain original. Final raw/native/bridge inputs are bound separately. Does not approve BE ownership admission, shapes/models, TARGET bridge compatibility, light, gameplay, or apply.'}
    if permission_ref:
        result.update(userStaticPermission=permission_ref,
                      userStaticPermissionOverrides=permission_overrides,
                      userPermissionIsNotPassiveCodeProof=True)
    if imported is not None:
        checked(imported['path'], imported['sha256'])
        result['currentImportedFullFunctionGate'] = copy.deepcopy(imported)
    query = {'schemaVersion': 1, 'mode': 'FUTURE_EXACT_POST_ACTIVATION_STATE_PROPERTIES_REQUEST_NOT_EXECUTED',
             'sourceJarSha256': f.SOURCE_SHA, 'sourceBinding': data['sourceBinding'],
             'postActivationInputEvidence': snapshot_ref, 'states': [],
             'stateProperties': [requests[key] for key in sorted(requests)],
             'unknownFunctionProofsStillRequired': [row['state'] for row in missing_functions],
             'scope': 'Existing NativeGridPhysicsExport exact getters only on immutable40ce. Flags are not function proof. No source map/BE/shape/render/bridge validation.'}
    if a.carrier_audit_dir:
        require(not a.carrier_audit_dir.exists(), 'Fresh normalization-only observation directory required')
        groups = collections.defaultdict(list)
        for key, actual in required.items():
            if actual['host'] and rows[key]['decision'] == 'bridge_host_only' and key in observed:
                observation = observed[key]
                if (observation['hasBlockEntity'] is not False and
                        observation['flags']['fluidEmpty'] and observation['flags']['luminance'] == 0):
                    groups[observation['evidence']['runtimeSha256']].append({'status': 'PASS', 'input': {'state': actual['state']}, **observation['flags'], 'propertiesEvidence': observation['evidence']})
        for digest, values in groups.items():
            f.write_new(a.carrier_audit_dir / ('observed-flags-' + digest[:16] + '.json'),
                        {'schemaVersion': 1, 'status': 'PASS', 'kind': 'EXACT_OBSERVED_CARRIER_FLAGS_FOR_NORMALIZATION_ONLY',
                         'nativeGridBridgeValidated': False, 'runtimeSha256': digest, 'catalogSha256': None,
                         'worldReads': 0, 'worldWrites': 0, 'sourceMapOpened': False,
                         'postActivationInputEvidence': snapshot_ref, 'results': values,
                         'mandatoryNextGate': 'Actual final TARGET NativeGridBridge.validate; not bridge acceptance/gameplay'})
    require(sha(a.input) == snapshot_ref['sha256'] and sha(a.prior_full_gate) == prior_ref['sha256'], 'Bound function inputs changed during authoring')
    f.write_new(a.requests_out, query)
    f.write_new(a.out, result)
    print(canonical({'status': result['status'], 'out': binding(a.out), 'priorStates': len(old), 'finalStates': len(rows),
                     'addedProvedPolicies': len(added_policy), 'missingFunctions': len(missing_functions),
                     'missingHostFlags': len(missing_flags), 'roleConflicts': len(role_conflicts), 'queriesStarted': 0}))
    return 0 if ready else 2


def parser():
    p = argparse.ArgumentParser(description=__doc__)
    sub = p.add_subparsers(dest='command', required=True)
    for name in ('snapshot', 'produce'):
        command = sub.add_parser(name)
        command.add_argument('--prior-full-gate', type=Path, required=True)
        command.add_argument('--prior-full-sha256', required=True)
        command.add_argument('--out', type=Path, required=True)
    snap = sub.choices['snapshot']
    snap.add_argument('--current-imported-full-gate', type=Path)
    snap.add_argument('--current-imported-full-sha256')
    for name in ('plan', 'compositions', 'coverage', 'native-needed'):
        snap.add_argument('--' + name, type=Path, required=True)
    for name in ('raw-sha256', 'coverage-sha256', 'native-needed-sha256'):
        snap.add_argument('--' + name, required=True)
    prod = sub.choices['produce']
    prod.add_argument('--input', type=Path, required=True)
    prod.add_argument('--input-sha256', required=True)
    prod.add_argument('--source-jar', type=Path)
    prod.add_argument('--policy', type=Path, action='append', default=[])
    prod.add_argument('--state-properties', type=Path, action='append', default=[])
    prod.add_argument('--query-request', type=Path, action='append', default=[])
    prod.add_argument('--properties-helper', type=Path)
    prod.add_argument('--policy-out', type=Path, required=True)
    prod.add_argument('--requests-out', type=Path, required=True)
    prod.add_argument('--carrier-audit-dir', type=Path)
    prod.add_argument('--user-static-permission', type=Path)
    prod.add_argument('--user-static-permission-sha256')
    return p


if __name__ == '__main__':
    args = parser().parse_args()
    helper = frozen()
    if args.command == 'produce' and args.source_jar is None:
        args.source_jar = helper.SOURCE
    raise SystemExit(snapshot(args, helper) if args.command == 'snapshot' else produce(args, helper))
