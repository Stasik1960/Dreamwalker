"""Link completed FULL receipts into one delivery acceptance, without reruns.

File-only, no SQL/world scan/JVM/build/network. A missing or mismatched gate
produces BLOCKED, never a pilot substitution. Visual/document observations
must be separate explicit root review receipts, not inferred from counters.
Usage: --input <schema-linked.json> --input-sha256 <SHA> --out <fresh.json>.
See full_delivery_acceptance.schema.json and FULL-DELIVERY-ACCEPTANCE-RU.txt.
"""
import argparse
import datetime
import hashlib
import json
import math
import os
import re
from pathlib import Path
from prepare_foreign_guest_plan import derived_install_transport_view
from client_runtime_lineage import read_lineage

PROFILE = '285757deaeafd752468dc50a79b0bc4e78fa7ce23b444cf8089d69539fa161d0'
RECORDER = '24ea32ff3b9d2b83011fe207ce58eb4a86c47df63ecf9327c756f7f9fcf8e676'
ACCEPTED_RUNTIME = '99f450f814d187cf089a7fe523a5db5928cfafcb273c4ac1c4479f2df50bbde3'
SOURCE_RUNTIME = '40cecfb7e5cf783a762e350fc1864518c39abff177f6f025cb94892a181fbf51'
PATCH_STATUS = 'PASS_EXACT_FULL_GENERATED_AND_REVIEWED_RUNTIME_PATCH_NOT_GAMEPLAY'
PATCH_CLASSES = {
    'dev/dreamwalker/bloodbornedw/composite/StaticArchitectureRuntime.class',
    'dev/dreamwalker/bloodbornedw/composite/CompositeClient.class',
    'dev/dreamwalker/bloodbornedw/composite/mixin/StaticArchitectureBreakMixin.class',
    'dev/dreamwalker/bloodbornedw/composite/NativeGridRetainedWholeItems.class',
    'dev/dreamwalker/bloodbornedw/composite/NativeGridRetainedWholeItems$Whitelist.class',
}
ROOT = Path(__file__).resolve().parents[4]
SOURCE = ROOT / 'source'
KEYS = ('production raw functions coverage catalogFreeze jarCompatibility targetAudit frozenApply marker '
        'lightBinding light lightLaunch preservedCoverage pilotAcceptance pairComparison profile recorder '
        'foreignInstall foreignInstallPlan foreignReopen foreignReopenPlan finalRoute finalRoutePlan '
        'reopenRoute reopenRoutePlan visualReview deliveryReview').split()


def require(ok, message):
    if not ok:raise ValueError(message)


def sha(path):
    h = hashlib.sha256()
    with Path(path).open('rb') as stream:
        for data in iter(lambda: stream.read(1 << 20), b''):h.update(data)
    return h.hexdigest()


def read(path):return json.loads(Path(path).read_text(encoding='utf-8-sig'))


def gson_transport(value):
    """Match report Gson's omitted named nulls; null array entries survive."""
    if isinstance(value, dict):
        return {key: gson_transport(item) for key, item in value.items() if item is not None}
    if isinstance(value, list):
        return [gson_transport(item) for item in value]
    return value


class Evidence:
    def __init__(self, artifacts, client_lineage=None):
        self.artifacts = artifacts;self.seen = {};self.client_lineage_ref = client_lineage

    def file(self, reference):
        require(set(reference) >= {'path', 'sha256'}, 'Evidence requires path and explicit SHA')
        require(isinstance(reference['sha256'], str) and len(reference['sha256']) == 64 and
                all(ch in '0123456789abcdef' for ch in reference['sha256']), 'Evidence SHA must be lowercase SHA256')
        path = Path(reference['path']).resolve(strict=True)
        if str(path) not in self.seen:
            require(path.is_file() and sha(path) == reference['sha256'], 'Evidence SHA differs: ' + str(path))
            self.seen[str(path)] = reference['sha256']
        require(self.seen[str(path)] == reference['sha256'], 'Conflicting evidence SHA')
        return path

    def bound(self, reference):return read(self.file(reference))
    def get(self, key):return self.bound(self.artifacts[key])

    def sibling(self, reference, name):
        path = self.file(reference).parent / name
        ref = {'path': str(path.resolve(strict=True)), 'sha256': sha(path)}
        return self.bound(ref), ref

    def finish(self):
        for path, digest in self.seen.items():require(sha(path) == digest, 'Evidence changed during aggregation: ' + path)


def accepted_function_generation_lineage(e, snapshot, coverage):
    """Only the three already committed, SHA-pinned carrier/whole stages."""
    stages = (
        ('postfinal-carrier-predicate-split-preview.artifacts/receipt.json',
         '6133ff0b903af1efe9784d22bb299b0579419f79a8426b6947fd58f12329dfe5', 'postfinalCarrierPredicateSplit'),
        ('postfinal-qualified-carrier-predicates-preview.artifacts/receipt.json',
         '7db664701f4a67a13c20581c6c094a584460b9d19bb26ad99ed76bba61e83ed9', 'postfinalCarrierPredicateSplit'),
        ('postfinal-whole-original-retention-preview.artifacts/receipt.json',
         'ca999cffbca42f2a0fbee65084ffe7707d4398f0a2d21b46975ffb9e53d62a4c', 'postfinalWholeOriginalRetention'),
    )
    raw = e.artifacts['raw'];raw_path = Path(raw['path']).resolve()
    needed = snapshot['nativeStatesNeeded'];needed_path = Path(needed['path']).resolve()
    generation = snapshot['compositionGeneration'];prior_split = None;seen_points = set()
    require(Path(snapshot['compositions']['path']).resolve() == raw_path and
            snapshot['compositions']['sha256'] == generation['compositionsSha256'] and
            snapshot['sourceBinding'] == coverage['sourceBinding'] and
            needed['sha256'] == coverage['nativeStatesNeededSha256'], 'Function generation lineage starts with different source/raw/needed inputs')
    for name, digest, metadata_key in stages:
        reference = {'path': str(raw_path.parent / 'full' / name), 'sha256': digest}
        receipt = e.bound(reference);before = receipt['beforeGeneration'];after = receipt['afterGeneration']
        require(receipt['status'] == 'PASS' and receipt['sourceBinding'] == coverage['sourceBinding'] and
                before == generation and receipt.get('priorCarrierPredicateSplit') == prior_split and
                receipt['processedRowsReset'] == receipt['worldReads'] == receipt['mapWrites'] == 0 and
                receipt['nativeQueueReplayed'] is False, 'Committed function-generation stage/source/order differs')
        require({key: value for key, value in before.items() if key not in ('compositionsSha256', metadata_key)} ==
                {key: value for key, value in after.items() if key not in ('compositionsSha256', metadata_key)} and
                after[metadata_key]['previousCompositionsSha256'] == before['compositionsSha256'] and
                receipt['beforeRaw']['sha256'] == before['compositionsSha256'], 'Function-generation stage changed other semantic bindings')
        derived = receipt['derivedFiles']
        require(len(derived) == 2 and {Path(row['target']).resolve() for row in derived} == {raw_path, needed_path},
                'Function-generation stage has unexpected derived outputs')
        outputs = {Path(row['target']).resolve(): row for row in derived}
        require(outputs[raw_path]['beforeSha256'] == before['compositionsSha256'] and
                outputs[raw_path]['proposed']['sha256'] == after['compositionsSha256'] and
                outputs[needed_path]['beforeSha256'] == outputs[needed_path]['proposed']['sha256'] == needed['sha256'],
                'Function-generation raw/needed hash continuity differs')
        if metadata_key == 'postfinalCarrierPredicateSplit':
            require(receipt['kind'] == 'COMMITTED_POSTFINAL_CARRIER_PREDICATE_SPLIT' and
                    all(receipt[key] is True for key in ('geometryPhysicalDataUnchanged',
                        'onlyObservedOpaqueAndSidedTransparencyChanged', 'nativeFunctionStatesUnchanged')) and
                    receipt['affectedPlacements'] == len(receipt['changes']) and
                    receipt['cloneKeys'] == len(receipt['compositionChanges']) > 0,
                    'Carrier split did not preserve function/physical inputs')
            clones = {}
            for row in receipt['compositionChanges']:
                predicates = row['carrierPredicates']
                require(set(predicates) == {'opaque', 'sidedTransparency'} and
                        all(type(value) is bool for value in predicates.values()) and
                        row['before'].get('carrierPredicates') is None and row['after']['carrierPredicates'] == predicates and
                        {key: value for key, value in row['before'].items() if key != 'carrierPredicates'} ==
                        {key: value for key, value in row['after'].items() if key != 'carrierPredicates'},
                        'Carrier split changed more than the two observed predicates')
                pair = (row['oldKey'], row['newKey'])
                require(pair not in clones, 'Duplicate carrier clone mapping')
                clones[pair] = predicates
            for row in receipt['changes']:
                point = (row['dimension'], tuple(row['position']))
                require(point not in seen_points and clones.get((row['oldComposition'], row['newComposition'])) == row['carrierPredicates'],
                        'Carrier split placement mapping overlaps or differs from its clone')
                seen_points.add(point)
            prior_split = reference
        else:
            require(receipt['kind'] == 'COMMITTED_POSTFINAL_WHOLE_ORIGINAL_RETENTION' and
                    before['compositionsSha256'] == after['compositionsSha256'] and
                    all(receipt[key] is True for key in ('objectsKindsUnchanged', 'allSourceDataUnchanged',
                        'onlyCompleteUndemandedDerivedWorkRetired', 'rawAndNativeNeededBytesUnchanged',
                        'ordinaryCompositionKeysUnchanged')), 'Whole original retention changed function/raw/needed inputs')
        generation = after
    require(generation == coverage['compositionGeneration'] and generation['compositionsSha256'] == raw['sha256'],
            'Committed function-generation chain does not reach the current coverage/raw')
    return True


def native_audit(e, reference, runtime, catalog):
    audit = e.bound(reference);launch, _ = e.sibling(reference, 'launch-receipt.json');binding, _ = e.sibling(reference, 'binding.json')
    require(audit['status'] == 'PASS' and audit['nativeRegistryQueries'] is True and audit['pairsChecked'] > 0 and
            len(audit['results']) == audit['pairsChecked'] and all(row['status'] == 'PASS' for row in audit['results']), 'Actual final TARGET carrier audit incomplete')
    require(audit['runtimeSha256'] == runtime and audit['catalogSha256'] == catalog and
            audit['worldReads'] == audit['worldWrites'] == 0, 'Actual final TARGET audit binding differs')
    require(launch['status'] == 'PASS' and launch['exitCode'] == 0 and all(launch.get(key) == value for key, value in audit.items()) and
            launch['productionSha256'] == binding['productionSha256'] == runtime and binding['catalogSha256'] == catalog and
            binding['requestSha256'] == audit['bridgePlanSha256'] and binding['heap'] == '4G' and
            binding['actualCityOpened'] is False and binding['stateQueriesOnly'] is True, 'TARGET launcher/request not successful or bound')
    return audit


def foreign(e, report_key, plan_key, runtime, catalog, audit, item_proof, previous=None):
    report, plan = e.get(report_key), e.get(plan_key)
    launch, _ = e.sibling(e.artifacts[report_key], 'launch-receipt.json');binding, _ = e.sibling(e.artifacts[report_key], 'binding.json')
    require(report['status'] == launch['status'] == 'PASS' and launch['exitCode'] == 0 and report['normalServerThreadFinished'] is True and
            report['productionSha256'] == launch['productionSha256'] == binding['productionSha256'] == plan['productionSha256'] == runtime and
            report['planSha256'] == binding['planSha256'] == e.artifacts[plan_key]['sha256'], 'New FOREIGN phase lacks actual successful FULL execution')
    require(binding['targetProfileUsed'] is True and binding['authoringReferenceOnly'] is False and binding['heap'] == '4G' and
            binding['installedProductionSha256'] == runtime and plan.get('authoringReferenceOnly') is False and
            plan['variant'] == 'candidate' and binding['world'] == plan['targetWorldPath'] and
            launch['helperSha256'] == binding['helperSha256'] and report['checks'] and all(row['pass'] for row in report['checks']), 'FOREIGN target profile/checks differ')
    detail = report['foreignGuestFixture'];fixture = plan['foreignGuestFixture'];point = e.bound(fixture['pointProof'])
    require(gson_transport(detail['binding']) == gson_transport(fixture) and fixture['targetBridgeAudit'] == e.artifacts['targetAudit'] and
            point['status'] == 'PASS_SOURCE_COMPOSITION_AND_TYPE_BINDING_NOT_GAMEPLAY' and point['scope'] == 'FULL_CITY' and
            point['catalogSha256'] == catalog and point['targetBridgePlanSha256'] == audit['bridgePlanSha256'] and
            point['production']['sha256'] == runtime and point['frozenFullProof'] == e.artifacts['catalogFreeze'], 'FOREIGN point/actual FULL type binding differs')
    legacy = detail['retainedNativeWholeItem'];legacy_input = fixture['legacyNativeWholeItem']
    require(gson_transport(legacy['binding']) == gson_transport(legacy_input) and legacy_input['resourceProof'] == item_proof['reference'] and
            legacy_input['resource'] == item_proof['resource'] and
            gson_transport(legacy_input['visualNeighbourBackground']) == gson_transport(point['retainedNativeVisualBackground']) and
            point['exactPointCount'] == 3 and legacy['nativeSetupStateWrites'] == 2 and legacy['backgroundSetupStateWrites'] == 1 and
            legacy['independentSourcePartNotInstalled'] is True and legacy['nativeRoot'] == legacy['itemPlacementRoot'] and
            legacy['exactCanonicalPlacementPath'] == 'ImportedRootBlock / CompositeItem / CompositeRuntime.place' and
            plan['addressedQaScope']['repeatAcceptedGroups'] is False,
            'Affected old-native whole item/background case lacks its exact final resource/three-point/same-root binding')
    known = str((ROOT / 'research/native-bridge-authoring-server/empty-authoring').resolve())
    require(plan['isolatedAuthoringStand'] == binding['isolatedAuthoringStand'] ==
            {'kind': 'EXACT_FOREIGN_GUEST_ISOLATED_AUTHORING_STAND', 'world': known} and binding['world'] == known and
            binding['migrationMarkerRequired'] is False and binding['pointProof'] == fixture['pointProof'] and
            binding['targetBridgeAudit'] == e.artifacts['targetAudit'], 'FOREIGN isolated scope differs; a city marker cannot be bypassed')
    if previous is None:
        require(fixture['phase'] == 'install_save' and detail['standRetainedForReopen'] is True and detail['gameplayComplete'] is False,
                'FOREIGN setup is not an explicit save-before-reopen phase')
        require(legacy['standRetainedForReopen'] is True and legacy['gameplayComplete'] is False and
                len(legacy['savedCells']) == 3 and report['rawSetBlockCalls'] == 4, 'Native W/background install/save phase incomplete')
    else:
        previous_reference = fixture['previousReport']
        if previous_reference != e.artifacts['foreignInstall']:
            actual_install, executed_install = e.get('foreignInstall'), e.get('foreignInstallPlan')
            actual_ref = {'path': str(e.file(e.artifacts['foreignInstall'])),
                          'sha256': e.artifacts['foreignInstall']['sha256']}
            executed_ref = {'path': str(e.file(e.artifacts['foreignInstallPlan'])),
                            'sha256': e.artifacts['foreignInstallPlan']['sha256']}
            expected_view = derived_install_transport_view(actual_install, executed_install, actual_ref, executed_ref)
            require(e.bound(previous_reference) == expected_view,
                    'Derived previous report changed more than the two exact executed binding trees/provenance')
        require(fixture['phase'] == 'reopen_finish' and
                binding['world'] == previous['world'] and binding['helperSha256'] == previous['helperSha256'] and
                report['standRestored'] is True and detail['realReopenVerified'] is True and detail['gameplayComplete'] is True and
                detail['standRetainedForReopen'] is False, 'FOREIGN actual reopen/cleanup incomplete')
        require(legacy['realReopenVerified'] is True and legacy['gameplayComplete'] is True and legacy['standRetainedForReopen'] is False and
                len(legacy['reopenedCells']) == 3 and legacy['survivalDropCount'] == 1 and
                legacy['backgroundSurvivedNativePickBreakAndReinstallation'] is True and legacy['canonicalItem'] and
                legacy['actualInstalledCellsBeforeGuard'] and any(row['accessible'] for row in legacy['nativePartSearch']) and
                report['rawSetBlockCalls'] == 0 and report['inheritedSetupRawSetBlockCalls'] == 4,
                'Actual reopened native whole pick/drop/ordinary replacement/whole removal/background survival incomplete')
    return binding


def route(e, key, plan_key, runtime, world, reopened=False, client_lineage=None):
    receipt, plan = e.get(key), e.get(plan_key)
    binding, binding_ref = e.sibling(e.artifacts[key], 'binding.json')
    client, client_ref = e.sibling(e.artifacts[key], 'client/client-route.json')
    server, server_ref = e.sibling(e.artifacts[key], 'server/server-route.json')
    require(receipt['status'] == 'PASS' and receipt['clientExitCode'] == receipt['serverExitCode'] == 0 and
            client['status'] == 'PASS_NATIVE_GRAPHICAL_ROUTE' and server['status'] == 'PASS_SAVED' and
            server['normalServerThreadFinished'] is True, 'Actual route/new connection/save incomplete')
    require(binding['variant'] == 'candidate' and binding['productionSha256'] == runtime and binding['world'] == world and
            binding['separateJVMs'] is True and binding['qaSha256'] == RECORDER and binding['profileLockSha256'] == PROFILE and
            binding['recorderLockSha256'] == e.artifacts['recorder']['sha256'] and
            binding['routeSha256'] == e.artifacts[plan_key]['sha256'] == client['routePlanSha256'] and
            all(receipt.get(name) == value for name, value in binding.items()), 'Route runtime/world/profile/recorder/input differs')
    if client_lineage is not None:
        require(binding.get('clientOnlyRuntimeLineage') == e.client_lineage_ref and
                binding.get('acceptedPredecessorProduction') == client_lineage['predecessor'],
                'Actual candidate route lacks the exact client-only lineage')
    else:require('clientOnlyRuntimeLineage' not in binding, 'Unexpected route lineage without its explicit acceptance input')
    heap_override = binding.get('functionalHeapOverride')
    if heap_override is not None:
        require(heap_override == {'clientHeap': '4G', 'baseClientHeap': '3G', 'serverHeap': '4G'} and client_lineage is not None and
                e.file(e.artifacts[plan_key]) in {(ROOT / 'research/final-route/route.json').resolve(), (ROOT / 'research/final-route/reopen-route.json').resolve()} and
                binding.get('functionalAdmissionOverride') is True and binding.get('admissionMinimumAvailableGiB') == 7.0 and
                binding.get('frozenMinimumAvailableGiB') == 9 and binding.get('initialAvailableRAM', 0) >= 7 * 1024 ** 3 and
                binding.get('initialAvailableCommit', 0) >= 9 * 1024 ** 3,
                'Functional 4G client lacks its exact FULL lineage/base-profile/memory admission')
    renderer_override = binding.get('functionalRendererOverride')
    if renderer_override is not None:
        profile = e.get('profile')
        excluded = sorted(({name: row[name] for name in ('file', 'id', 'sha256')} for row in profile['mods']['client']
                           if row['variant'] == 'candidate' and row['id'] in ('sodium', 'indium')), key=lambda row: row['file'])
        require(len(excluded) == 2 and {row['id'] for row in excluded} == {'sodium', 'indium'} and
                renderer_override == {'backend': 'fabric-indigo', 'excludedClientMods': excluded} and
                heap_override is not None and client_lineage is not None,
                'Indigo override must name exactly the two frozen candidate mods and existing functional guards')
    names = plan['names'];phases = client['phases']
    require(len(names) == (1 if reopened else 11) and len(set(names)) == len(names) and
            [row['phase'] for row in phases] == names == [row['phase'] for row in server['phases']] and
            client['initialPositioningBeforeMeasurement'] is True, 'Full final/reopen route phase coverage differs')
    for index, phase in enumerate(phases):
        require(phase['serverPositionAcknowledged'] and phase['terrainRenderComplete'] and phase['renderedAtTarget'] and
                phase['frames'] > 0 and phase['durationNs'] > 0 and phase['requiredChunkEvidence'] and
                all(row['full'] and row['deliveryObserved'] for row in phase['requiredChunkEvidence']) and
                phase['requiredFullChunks'] == len(phase['requiredChunkEvidence']), 'Route has undelivered/unrendered phase')
        require(phase['camera'] == plan['cameras'][index] and phase['probe'] == plan['probes'][index] and
                not any('minecraft:' + name + '}' in phase['probeState'] for name in ('air', 'cave_air', 'void_air')), 'Camera/probe differs or is AIR')
        if index:
            distance = math.dist(plan['cameras'][index - 1][:3], plan['cameras'][index][:3])
            require(distance <= 4096 * .75 and phase['nativeMovementTicks'] > 0 and phase['maxStep'] <= .7501 and
                    phase['nativeDistance'] >= distance - .15, 'Ordinary between-camera movement not evidenced')
    extra = {'functionalHeapOverride': heap_override} if heap_override is not None else {}
    if renderer_override is not None:extra['functionalRendererOverride'] = renderer_override
    for role, heap in (('client', '4G' if heap_override is not None else '3G'), ('server', '4G')):
        path = e.file(e.artifacts[key]).parent / (role + '.args');ref = {'path': str(path), 'sha256': sha(path)};e.file(ref)
        args = [json.loads(line) for line in path.read_text(encoding='utf-8-sig').splitlines() if line.strip()]
        require([arg for arg in args if arg.startswith('-Xmx')] == ['-Xmx' + heap], 'Actual route heap widened')
        log = path.with_name(role + '-console.log');log_ref = {'path': str(log), 'sha256': sha(log)};e.file(log_ref)
        console = log.read_text(encoding='utf-8', errors='replace')
        require(not any(text in console for text in ('OutOfMemoryError', 'Failed to save', 'Exception stopping the server')), 'Actual route runtime/save failure')
        if role == 'server':require('All dimensions are saved' in console, 'Server save was not confirmed')
        if role == 'client':
            require('--gameDir' in args, 'Actual screenshot gameDir is missing');extra['gameDir'] = args[args.index('--gameDir') + 1]
            if renderer_override is not None:
                game_dir = (ROOT / 'research/profiles/candidate/client').resolve()
                require(Path(extra['gameDir']).resolve() == game_dir and '[Indigo] Registering Indigo renderer!' in console and
                        '[Indigo] Different rendering plugin detected; not applying Indigo.' not in console and
                        not re.search(r'(?m)^\s*-\s+(?:sodium|indium)\s', console),
                        'Actual final client backend is not Indigo without loaded Sodium/Indium')
                for row in renderer_override['excludedClientMods']:
                    require(Path(row['file']).name == row['file'] and not (game_dir / 'mods' / row['file']).exists(),
                            'Excluded renderer mod is present in actual gameDir')
                    e.file({'path': str(game_dir.parent / 'disabled-functional-indigo' / row['file']), 'sha256': row['sha256']})
        csv = e.file(e.artifacts[key]).parent / role / ('client-frames.csv' if role == 'client' else 'server-ticks.csv')
        csv_ref = {'path': str(csv), 'sha256': sha(csv)};require(e.file(csv_ref).stat().st_size > 60, 'Actual frame/tick evidence missing')
        extra[role + 'Measurements'] = csv_ref
    return {'receipt': e.artifacts[key], 'binding': binding_ref, 'client': client_ref, 'server': server_ref,
            'plan': e.artifacts[plan_key], 'phases': phases, 'initialUtc': client['initialPositioningUtc'], **extra}


def aggregate(e):
    delivered_runtime = e.artifacts['production']['sha256'];e.file(e.artifacts['production'])
    client_lineage = None;runtime = delivered_runtime
    if e.client_lineage_ref is not None:
        client_lineage = read_lineage(e.client_lineage_ref, e.artifacts['production'],
                                     SOURCE / 'src/architecture/java/dev/dreamwalker/bloodbornedw/composite/NativeGridClient.java')
        for reference in (e.client_lineage_ref, client_lineage['predecessor'], client_lineage['producer'],
                          client_lineage['source'], client_lineage['rootSourceReviewRef']):e.file(reference)
        e.file(e.bound(client_lineage['rootSourceReviewRef'])['predecessorSource'])
        runtime = client_lineage['predecessor']['sha256']
    freeze, compat, coverage, functions = (e.get(key) for key in ('catalogFreeze', 'jarCompatibility', 'coverage', 'functions'))
    require(freeze['status'] == 'FROZEN_RESOURCES_READY' and freeze['kind'] == 'COMPLETED_FULL_CATALOG_AUTHORING_HANDOFF', 'No actual FULL catalog freeze')
    catalog = freeze['catalog']['sha256'];e.bound(freeze['catalog']);e.file(freeze['meshes'])
    prefix, append = e.bound(freeze['prefixProof']), e.bound(freeze['inputBinding'])
    e.file(freeze['mapping']);e.bound(freeze['compilerReceipt'])
    require(append['status'] == prefix['status'] == 'PASS' and append['mode'] == 'EXPLICIT_FULL_APPEND_INPUT_BINDING' and
            freeze['inputBindings'] == append and append['inputs']['compositions'] == e.artifacts['raw'] and
            append['inputs']['final_coverage'] == e.artifacts['coverage'] and
            append['sourceProofs']['nativeFunctionGate']['sha256'] == e.artifacts['functions']['sha256'] and
            prefix['catalogSha256'] == catalog and prefix['meshesSha256'] == freeze['meshes']['sha256'] and
            freeze['bridgeProof']['allDryLuminanceZeroObservedPredicatesMatch'] is True,
            'FULL raw/function/coverage/compiler/frozen resource lineage differs')
    require(e.bound(freeze['bridgeProof'])['status'] == e.bound(freeze['statisticsProof'])['status'] == 'PASS', 'FULL frozen bridge/statistics proof missing')
    require(compat['status'] == PATCH_STATUS and not compat['failures'] and
            compat['accepted']['sha256'] == ACCEPTED_RUNTIME and compat['full']['sha256'] == runtime and
            compat['frozenFull'] == e.artifacts['catalogFreeze'] and compat['catalog']['sha256'] == catalog and
            compat['sourceBinding'] == append['sourceBinding'] and compat['prefixProof'] == freeze['prefixProof'] and
            compat['allRuntimeClassesByteExact'] is False and
            all(compat[key] is True for key in ('runtimeCompatibilityPassed', 'allUnchangedRuntimeClassesByteExact', 'allOldNonGeneratedResourcesByteExact', 'allTexturesByteExact', 'allOtherAcceptedEntriesByteExact')),
            'Actual FULL JAR compatibility not proved')
    e.file(compat['full'])
    if client_lineage is not None:
        require(compat['full'] == client_lineage['predecessor'], 'Predecessor FULL compatibility/JAR differs from client lineage')
    e.file(compat['accepted'])
    patch_ref = compat['reviewedRuntimePatch'];patch = e.bound(patch_ref)
    require(patch['status'] == 'ROOT_SOURCE_APPROVED_FOR_AFFECTED_QA_NOT_DELIVERY' and
            patch['kind'] == 'EXACT_RETAINED_WHOLE_ITEM_RUNTIME_SOURCE_PATCH' and patch['accepted'] == compat['accepted'] and
            patch['full'] == compat['full'] and set(patch['classes']) == PATCH_CLASSES, 'No exact finite root-approved runtime patch')
    root_review = e.bound(patch['rootReview']);delta = e.bound(patch['sourceDeltaProof'])
    require(root_review['status'] == 'ROOT_SOURCE_REVIEWED_FOR_AFFECTED_QA_NOT_DELIVERY' and
            root_review['kind'] == 'EXACT_RETAINED_WHOLE_ITEM_RUNTIME_ROOT_SOURCE_REVIEW' and
            root_review['runtimeSourceApprovedForAffectedQa'] is True and root_review['gameplayPass'] is False and
            root_review['readyForDelivery'] is False and root_review['reviewer'] and root_review['observations'] and
            set(root_review['allowedRemappedClassEntries']) == PATCH_CLASSES and root_review['sourceDeltaProof'] == patch['sourceDeltaProof'] and
            sorted((row['path'], row['sha256']) for row in root_review['sourceFiles']) ==
            sorted((row['path'], row['sha256']) for row in patch['sourceFiles']) and
            delta['status'] == 'PASS_SOURCE_TEXT_HOOK_DELTA_NOT_GAMEPLAY', 'Concrete root source approval/delta differs')
    for reference in patch['sourceFiles']:e.file(reference)
    for row in delta['files']:e.file({'path': row['beforePath'], 'sha256': row['beforeSha256']})
    resource = e.bound(patch['resource']);item = e.bound(patch['resourceProof'])
    require(item['status'] == 'PASS_EXACT_RETAINED_NATIVE_WHOLE_ITEM_RESOURCE_NOT_GAMEPLAY' and
            item['kind'] == 'FINITE_OBSERVED_RETAINED_NATIVE_WHOLE_ITEMS' and item['sourceBinding'] == append['sourceBinding'] and
            item['resource'] == patch['resource'] and item['finalCompositions'] == e.artifacts['raw'] and
            item['compositionGeneration']['compositionsSha256'] == e.artifacts['raw']['sha256'] and
            item['originalV12Excluded'] is True and item['gameplayPass'] is False and
            resource['schemaVersion'] == 1 and len(resource['poses']) == item['resourcePoseCount'] and
            {row['legacyPose'] for row in resource['poses']} == set(item['observedLegacyPoses']), 'Final observed retained native whole item resource missing')
    payloads = {**patch['classes'], 'assets/bloodborne_dw/native_grid/retained_whole_items.json': patch['resource']['sha256']}
    require(compat['generatedWhitelist']['separatelyApprovedExactRuntimePayloads'] == payloads and
            set(compat['changedApprovedRuntimeEntries'] + compat['addedApprovedRuntimeEntries']) == set(payloads), 'Compatibility did not bind exactly the reviewed five classes and one item resource')
    item_proof = {'reference': patch['resourceProof'], 'resource': patch['resource']}
    require(coverage['status'] == 'PASS' and coverage['scope'] == 'FULL_CITY' and
            coverage['kind'] == 'ACTUAL_POST_ACTIVATION_NATIVE_INPUT_GATE' and
            coverage['compositionsSha256'] == e.artifacts['raw']['sha256'] and
            all(coverage[key] == [] for key in ('missingFunctionDecisions', 'nonPassiveBaseStates', 'missingNativeExports', 'invalidPhysicalNativeRows', 'missingActivatedSourcePoseFootprints')),
            'Actual FULL postactivation input coverage incomplete')
    e.file(e.artifacts['raw'])
    require(functions['status'] == 'PASS' and functions['scope'] == 'FULL_CITY' and
            functions['sourceBinding'] == coverage['sourceBinding'] == append['sourceBinding'] and
            coverage['compositionGeneration']['compositionsSha256'] == e.artifacts['raw']['sha256'] and
            coverage['nativeFunctionGate']['sha256'] == e.artifacts['functions']['sha256'] and functions['sourceJarSha256'] == SOURCE_RUNTIME and
            functions['coverage']['candidateObjects'] > 0 and not functions['missingFunctionProofs'] and not functions['missingObservedHostFlags'] and
            not functions.get('postActivationRoleConflicts'), 'Fresh FULL function decisions incomplete')
    if 'postActivationInputEvidence' in functions:
        snapshot = e.bound(functions['postActivationInputEvidence'])
        require(((snapshot['compositions'] == e.artifacts['raw'] and snapshot['compositionGeneration'] == coverage['compositionGeneration']) or
                 accepted_function_generation_lineage(e, snapshot, coverage)) and
                snapshot['sourceBinding'] == coverage['sourceBinding'] and snapshot['nativeStatesNeeded']['sha256'] == coverage['nativeStatesNeededSha256'],
                'Postactivation function supplement used another composition generation')
    audit = native_audit(e, e.artifacts['targetAudit'], runtime, catalog)
    frozen, marker, light_binding, light = (e.get(key) for key in ('frozenApply', 'marker', 'lightBinding', 'light'))
    require(frozen['sourceBinding'] == coverage['sourceBinding'] and frozen['catalog']['sha256'] == catalog and
            frozen['pilotRegionBinding'] is None and frozen['pilotMetadataBinding'] is None and
            marker['status'] == 'READY_FOR_GAMEPLAY_QA' and marker['runtime']['sha256'] == runtime and marker['catalog']['sha256'] == catalog and
            marker['lightReceiptSha256'] == e.artifacts['light']['sha256'], 'Committed FULL map/runtime/light marker differs')
    world = light_binding['target'];require(str(e.file(e.artifacts['marker']).parent) == str(Path(world).resolve()), 'Marker is for another map')
    require(not (Path(world) / 'NATIVE-GRID-INCOMPLETE.json').exists() and Path(world).resolve() != Path(coverage['sourceBinding']['source']).resolve(), 'Map is incomplete or immutable original')
    for key in ('plannedBeSafety', 'cleanupBeAudit', 'fullNativeInputCoverageGate', 'preservedAssemblyGate'):
        require(e.bound(frozen['semanticGates'][key])['status'] == 'PASS', 'Actual apply semantic gate missing: ' + key)
    require(frozen['semanticGates']['fullNativeInputCoverageGate']['sha256'] == e.artifacts['coverage']['sha256'] and
            frozen['semanticGates']['fullNativeFunctionGate']['sha256'] == e.artifacts['functions']['sha256'] and
            frozen['semanticGates']['bridgeCarrierGate']['sha256'] == e.artifacts['targetAudit']['sha256'], 'Frozen apply accepted another FULL input/audit')
    bridge_request = e.bound(frozen['semanticGates']['bridgeHostStates'])
    require(frozen['semanticGates']['bridgeHostStates']['sha256'] == audit['bridgePlanSha256'] and
            bridge_request['catalogueSha256'] == catalog and
            len(bridge_request['pairs']) == audit['pairsChecked'] and
            [row['input'] for row in audit['results']] == bridge_request['pairs'], 'Actual TARGET audit does not cover the full frozen host/type request')
    launch = e.get('lightLaunch')
    require(light['status'] == launch['status'] == 'PASS' and launch['exitCode'] == 0 and light['nativeEngine'] is True and light['saveFlushed'] is True and
            light['pending'] == 0 and light['processed'] == light['queued'] == light_binding['queued'] and light['queued'] > 0 and
            light['world'] == world and light['runtimeSha256'] == light_binding['runtime']['sha256'] == launch['runtimeSha256'] == runtime and
            light['catalogSha256'] == light_binding['catalog']['sha256'] == catalog and light['queueSha256'] == light_binding['queueSha256'] == launch['queueSha256'] and
            light_binding['fullCityFreshLight'] is True and light_binding['savedPrefixReused'] is False, 'Actual FULL native light incomplete')
    assemblies = e.get('preservedCoverage')
    require(assemblies['status'] == 'PASS' and assemblies['kind'] == 'FINAL_FINITE_PRESERVED_ASSEMBLY_COVERAGE' and
            assemblies['evidenceLevel'] == 'SAVED_PLAN_COMMITTED_APPLY_AND_ACTUAL_FULL_RELIGHT_ASSEMBLIES' and
            assemblies['sourceBinding'] == coverage['sourceBinding'] and assemblies['runtime']['sha256'] == runtime and assemblies['catalog']['sha256'] == catalog and
            assemblies['committedApplyJournals']['frozenInputs'] == e.artifacts['frozenApply'] and assemblies['nativeLightReceipt'] == e.artifacts['light'] and
            assemblies['counts']['manifestEntries'] == 28 and assemblies['counts']['knownObjects'] == 3801 and
            assemblies['counts']['actualComponentGroupsMerged'] == 4 and assemblies['counts']['originalComponentOwnersBound'] == 8 and
            assemblies['retentionMembers'] is not None and assemblies['ownerGraphTraversal'] is False, 'Final whole/v12/retention coverage incomplete')
    for key in ('manifest', 'poseOverrides', 'acceptedPosePreview', 'componentOverrides'):e.file(assemblies[key])
    for key in ('gallery', 'protectedIds', 'classification'):e.file(assemblies['originalV12'][key])
    install = foreign(e, 'foreignInstall', 'foreignInstallPlan', runtime, catalog, audit, item_proof)
    foreign(e, 'foreignReopen', 'foreignReopenPlan', runtime, catalog, audit, item_proof, previous=install)
    profile, recorder = e.get('profile'), e.get('recorder')
    require(e.artifacts['profile']['sha256'] == PROFILE and profile['clientHeap'] == '3G' and profile['serverHeap'] == '4G' and
            recorder['status'] == 'FROZEN_BEFORE_FIRST_PAIR' and recorder['helper']['sha256'] == RECORDER and
            recorder['profileLockSha256'] == PROFILE and recorder['sameHelperForBaselineAndCandidate'] and recorder['measurementPhasesUnchanged'], 'Pinned profile/recorder changed')
    final = route(e, 'finalRoute', 'finalRoutePlan', delivered_runtime, world, client_lineage=client_lineage)
    reopen = route(e, 'reopenRoute', 'reopenRoutePlan', delivered_runtime, world, reopened=True, client_lineage=client_lineage)
    require(final.get('functionalRendererOverride') == reopen.get('functionalRendererOverride'),
            'FULL route and reopen used different renderer backends')
    require(e.artifacts['finalRoute']['path'] != e.artifacts['reopenRoute']['path'] and
            datetime.datetime.fromisoformat(reopen['initialUtc'].replace('Z', '+00:00')).timestamp() * 1e9 > e.file(final['server']).stat().st_mtime_ns,
            'Reopen must start after the previous server completed its normal save/exit')
    visual = e.get('visualReview')
    require(visual['status'] == 'PASS_ACTUAL_RENDERED_VIEWS' and visual['kind'] == 'ACTUAL_FULL_ROUTE_AND_REOPEN_OBSERVATION' and
            visual['runtimeSha256'] == delivered_runtime and visual['catalogSha256'] == catalog and visual['reviewer'] and
            visual['finalRouteReceipt'] == e.artifacts['finalRoute'] and visual['reopenRouteReceipt'] == e.artifacts['reopenRoute'] and
            all(visual[key] is True for key in ('actualImagesInspected', 'noNewVisibleDefects', 'movementUsable', 'noFrequentMultiSecondStalls', 'optimizationSupportedWithinReportedLimits')),
            'Separate actual rendered-image/movement observation missing')
    expected = [(run, phase) for run in (final, reopen) for phase in run['phases']]
    require(len(visual['views']) == len(expected), 'Not every final/reopen rendered view was reviewed')
    for view, (run, phase) in zip(visual['views'], expected):
        image = e.file({'path': view['path'], 'sha256': view['sha256']})
        require(view['phase'] == phase['phase'] and view['observations'] and image == (Path(run['gameDir']) / 'screenshots' / ('route-' + phase['phase'] + '.png')).resolve() and
                image.stat().st_mtime_ns >= phase['startEpochNs'], 'Reviewed frame is stale or for another phase')
        with image.open('rb') as stream:require(stream.read(8) == b'\x89PNG\r\n\x1a\n', 'Reviewed file is not a PNG')
    pilot, pair = e.get('pilotAcceptance'), e.get('pairComparison')
    require(pilot['status'] == 'PASS' and pilot['kind'] == 'ACTUAL_DENSE_PILOT_ACCEPTANCE_NOT_FINAL_CITY_READY' and
            pilot['runtimeSha256'] == ACCEPTED_RUNTIME and pilot['evidence']['comparison'] == e.artifacts['pairComparison'] and
            pair['candidate']['binding']['productionSha256'] == ACCEPTED_RUNTIME and
            pair['candidate']['binding']['profileLockSha256'] == PROFILE and pair['candidate']['binding']['qaSha256'] == RECORDER and
            pair['status'] in ('BASELINE_INCOMPLETE_CANDIDATE_ANALYZED_NOT_READINESS', 'ONE_PAIR_ANALYZED_NOT_READINESS') and pair['limitations'], 'Actual accepted practical pair provenance differs')
    incomplete = pair['status'].startswith('BASELINE_INCOMPLETE')
    if incomplete:require(pair['measuredDurationChangePercent'] is None and pair['baseline']['receipt']['status'] == 'FAIL', 'Incomplete baseline cannot provide a speedup percentage')
    functional_heap_changed = bool(final.get('functionalHeapOverride') or reopen.get('functionalHeapOverride'))
    functional_renderer_changed = final.get('functionalRendererOverride') is not None
    if functional_heap_changed:
        require(incomplete and pair['measuredDurationChangePercent'] is None,
                'Functional 4G client observations cannot be presented as a matched baseline speedup')
    if functional_renderer_changed:
        require(incomplete and pair['measuredDurationChangePercent'] is None,
                'Indigo functional observations cannot relabel the accepted Sodium pilot as a matched pair')
    delivery = e.get('deliveryReview')
    require(delivery['status'] == 'PASS_FINAL_DOCUMENT_AND_ARTIFACT_BINDING' and delivery['runtimeSha256'] == delivered_runtime and delivery['catalogSha256'] == catalog and
            delivery['reviewer'] and delivery['observations'] and delivery['mapRoot'] == world and
            delivery['runtimePatch'] == patch_ref and delivery['runtimeSourceReview'] == patch['rootReview'] and
            Path(delivery['sourceRoot']).resolve() == SOURCE and SOURCE.is_dir() and delivery['sourceTreeVerifiedCurrent'] is True and
            delivery['blockingDefects'] == [] and delivery['unresolvedRequiredFunctionalGaps'] == [] and
            all(delivery[key] is True for key in ('mapFolderVerifiedCurrent', 'matchingModMapAndSources', 'noCheckpointPresentedAsFinal', 'knownFailuresAndPerformanceLimitsDisclosed', 'oneItemAndWholeObjectBehaviorResolved')),
            'Practical map/mod/source/install handoff has not been explicitly reviewed')
    if client_lineage is not None:
        require(delivery.get('clientOnlyRuntimeLineage') == e.client_lineage_ref and
                delivery.get('acceptedPredecessorProduction') == client_lineage['predecessor'],
                'Delivery review lacks the exact client-only successor/predecessor binding')
    docs = {row['role']: row['binding'] for row in delivery['files']}
    require(len(docs) == len(delivery['files']) and {'productionJar', 'installInstructions', 'sourceHandoff'} <= set(docs) and
            docs['productionJar']['sha256'] == delivered_runtime, 'Delivery mod/install/source documents incomplete')
    for ref in docs.values():require(e.file(ref).stat().st_size > 0, 'Empty delivery artifact/document')
    return {'runtimeSha256': delivered_runtime, 'catalogSha256': catalog, 'mapRoot': world, 'sourceRoot': delivery['sourceRoot'],
            **({'functionalRendererOverride': final['functionalRendererOverride']} if functional_renderer_changed else {}),
            **({'acceptedPredecessorProduction': client_lineage['predecessor'],
                'oldServerEvidenceRuntimeSha256': runtime, 'newDeliveredRuntimeSha256': delivered_runtime,
                'clientOnlyRuntimeLineage': e.client_lineage_ref} if client_lineage is not None else {}),
            'sourceBinding': coverage['sourceBinding'], 'reviewedRuntimePatch': patch_ref, 'runtimeSourceReview': patch['rootReview'],
            'retainedWholeItemResource': patch['resource'], 'retainedWholeItemResourceProof': patch['resourceProof'], 'baselineCompleted': not incomplete,
            'pairedSpeedupPercent': None if incomplete else pair['measuredDurationChangePercent'],
            'practicalPerformanceLimits': pair['limitations'], 'performanceScope': 'Accepted dense-pilot pair on99f4 plus actual FULL final-route/reopen observations; no complete FULL before/after speedup, FPS percentage or remote-hosting guarantee.' + (' FULL functional client heap is explicitly 4G versus the frozen base 3G; these observations are not matched-pair performance evidence.' if functional_heap_changed else '') + (' FULL functional renderer is Fabric Indigo with the exact frozen Sodium/Indium pair retained outside client mods; this different backend is not a matched-pair comparison or an FPS percentage.' if functional_renderer_changed else ''),
            'finalViews': len(final['phases']), 'reopenViews': len(reopen['phases']), 'finalActualRoute': final, 'actualReopenRoute': reopen}


def main():
    p = argparse.ArgumentParser(description=__doc__);p.add_argument('--input', type=Path, required=True)
    p.add_argument('--input-sha256', required=True);p.add_argument('--out', type=Path, required=True);a = p.parse_args()
    require(not a.out.exists(), 'Fresh acceptance output required; preserve previous blocked/checkpoint evidence')
    e = None
    try:
        require(sha(a.input) == a.input_sha256, 'Acceptance input SHA changed');doc = read(a.input)
        require(doc['schemaVersion'] == 1 and doc['kind'] == 'FULL_NATIVE_GRID_DELIVERY_ACCEPTANCE_INPUT' and set(doc['artifacts']) == set(KEYS), 'Exact final acceptance input schema required')
        require(set(doc) <= {'schemaVersion', 'kind', 'artifacts', 'clientOnlyRuntimeLineage'}, 'Unexpected acceptance top-level field')
        require('clientOnlyRuntimeLineage' not in doc or isinstance(doc['clientOnlyRuntimeLineage'], dict),
                'An explicit client lineage field must contain its bound reference')
        e = Evidence(doc['artifacts'], doc.get('clientOnlyRuntimeLineage'));details = aggregate(e);e.finish();require(sha(a.input) == a.input_sha256, 'Acceptance input changed during reading')
        result = {'schemaVersion': 1, 'status': 'PASS', 'kind': 'FULL_NATIVE_GRID_CITY_DELIVERY_ACCEPTANCE', 'readyForDelivery': True, **details,
                  'evidence': doc['artifacts'], 'checkedFiles': e.seen, 'sourceInput': {'path': str(a.input.resolve()), 'sha256': a.input_sha256}}
    except (KeyError, ValueError, OSError, TypeError) as error:
        result = {'schemaVersion': 1, 'status': 'BLOCKED_FINAL_DELIVERY_GATES', 'kind': 'FULL_NATIVE_GRID_CITY_DELIVERY_ACCEPTANCE',
                  'readyForDelivery': False, 'error': str(error), 'sourceInput': {'path': str(a.input.resolve()), 'sha256': a.input_sha256}}
    result.update(projectProcessesStarted=0, sqlReads=0, worldScans=0, gameplayChecksRepeated=0, generatorSha256=sha(__file__))
    a.out.parent.mkdir(parents=True, exist_ok=True);temporary = a.out.with_name(a.out.name + '.writing')
    with temporary.open('x', encoding='utf-8') as stream:
        json.dump(result, stream, ensure_ascii=False, indent=2);stream.write('\n');stream.flush();os.fsync(stream.fileno())
    os.link(temporary, a.out);temporary.unlink();print(json.dumps({'status': result['status'], 'readyForDelivery': result['readyForDelivery'], 'out': str(a.out), 'sha256': sha(a.out)}))
    if not result['readyForDelivery']:raise SystemExit(2)


if __name__ == '__main__':main()
