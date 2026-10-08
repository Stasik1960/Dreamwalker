"""Read-only audit of V9 ordinary mechanism/lamp stand and its actual server restart."""
from __future__ import annotations
import argparse, json
from pathlib import Path
from collections import Counter
from verify_review_v8_scene import ROOT, sha, typed_sha, require, read, resolve, identity
from verify_client_scene_persistence import full_rp_entities
from world_io import read_nbt, compound
from archive_first_set_scene import inventory


def stored(world, name):
    path = world / 'data' / (name + '.dat')
    require(path.is_file(), 'Missing persisted gameplay state: ' + name)
    return compound(read_nbt(path).root)['data']


def server_phase(path, artifact_sha):
    path = resolve(path); wrapper = read(path)
    require(wrapper.get('status') == 'PASS' and wrapper.get('exit_code') == 0
            and wrapper.get('artifact_sha256') == artifact_sha and not wrapper.get('termination'),
            'Gameplay server wrapper is not a normal PASS for the exact artifact')
    run_dir = resolve(wrapper['run_directory'])
    require(run_dir.is_relative_to((ROOT / 'build').resolve()), 'Gameplay server escaped isolated build area')
    require(len([p for p in (run_dir / 'mods').glob('*.jar') if sha(p) == artifact_sha]) == 1,
            'Exact production JAR is missing/ambiguous')
    return path, wrapper, run_dir / 'isolated-smoke-world'


def raw_gameplay(wrapper, marker, phase):
    proof = wrapper.get('ordinary_gameplay_output', {})
    require(proof and wrapper.get('ordinary_gameplay_output_ready_before_stop') is True,
            'Ordinary gameplay proof was not completed before native stop/save')
    require(sha(proof['path']) == proof['sha256'] and read(proof['path']) == proof['result'],
            'Primary gameplay output changed')
    value = proof['result']
    expected = ('PASS_V9_ORDINARY_GAMEPLAY_AUTHOR_REQUIRES_ACTUAL_RESTART' if phase == 'AUTHOR'
                else 'PASS_V9_ORDINARY_GAMEPLAY_AFTER_ACTUAL_RESTART')
    require(value.get('status') == expected and value.get('phase') == phase, 'Wrong gameplay phase/verdict')
    input_proof = wrapper.get('ordinary_gameplay_input', {})
    require(input_proof.get('sha256') == sha(marker) and read(marker).get('phase') == phase,
            'Gameplay marker differs from actual launch input')
    require(value.get('leverDelayTicks') == 70 and value.get('elapsedServerTicks', 0) >= 284,
            'Original lever delay or actual ticking interval was not proved')
    require(value.get('fixtureTicking') == 'BOUNDED_TEMPORARY_FORCED_CHUNKS_ORIGINAL_FORCED_FLAGS_RESTORED'
            and value.get('temporaryFixtureTickingTicketsRestored') is True,
            'Actual entity ticking scope or original forced-chunk flag restoration was not proved')
    operations=value.get('leverOperations',[])
    require(len(operations)==4 and all(row.get('nativeRemainingDelay')==70 for row in operations),
            'Four real lever operations did not begin with the original native70-tick delay')
    require([row['serverTick']-operations[0]['serverTick'] for row in operations]==[0,71,142,213],
            'Actual survival lever operations did not follow the bounded phase schedule')
    require(len(value.get('actualTargetStates',[]))==4,
            'Actual shared target states and native remaining delays were not recorded at all four phases')
    require(value.get('hunterlampTravel') == 'PASS_EXISTING_DIRECTED_NETWORK_BOTH_DIRECTIONS'
            and value.get('respawn') == 'UNCHANGED_NO_NEW_FEATURE', 'Existing lamp travel proof is missing')
    checks = value.get('checks', []); require(checks and all(row.get('pass') is True for row in checks),
                                             'A gameplay check failed/missing')
    names = {row['check'] for row in checks}
    required = {'originalLeverDelay69', 'firstLeverActualTargets', 'secondLeverUsesSharedActualDoor',
                'allOrdinaryTargetStatesRestored', 'outboundDirectedTravel', 'reverseDirectedTravelAfterCooldown',
                'noRespawnPointOrHealthChange', 'pulseOnlyLinkedWoodgateReturnsIdleWithoutFakeOpen',
                'heldWoodGateDoesNotQueueAnotherPulse', 'nativeLeverDelayIsOneAfter69EntityTicks',
                'bothNativeLeverDelaysFinishedBeforeSave'}
    for role in ['NpcWindow','WoodGate']:
        required |= {role+'ActualPlayerBlocksSide-1',role+'ActualPlayerBlocksSide1',role+'OutsideWorkingPlanePassable'}
    required |= {'ChandelierFloorNoDecorativeAabbPhysics','ChandelierFloorActualPlayerCrossesDecor',
                 'ChandelierCeilingNoDecorativeAabbPhysics','ChandelierCeilingActualPlayerCrossesDecor',
                 'RpLadderActualWorkingFootClimbsFarBelowOrigin','RpLadderWorldQueriesActualThinLowerPanel',
                 'RpStairsActualPlayerStepsFirstAuthoredTread','TwoSameCagesHaveIndependentFlagsAndUnchangedPhysicalCost'}
    if phase == 'REOPEN':
        required |= {'persistedExactLampNodeAndEntityIdentities', 'persistedBothDirectedRoutes',
                     'persistedLinksAndArchitectureOwner', 'woodGateRestartsIdleWithoutSavedRequest'}
    require(required <= names, 'Incomplete real gameplay checks: ' + str(sorted(required - names)))
    require(len(value.get('actualMovementProbes',[]))==10,'Ten bounded actual movement/query probes are missing')
    for movement in value['actualMovementProbes']:
        if 'actual' not in movement:continue  # The ladder working-foot row is a native climbing/collision query.
        flags=movement.get('playerFlags',{})
        require(all(flags.get(key) is False for key in ['noClip','spectator','creative','flying','allowFlying']),
                'Actual movement actor bypassed ordinary survival collision: '+movement.get('probe',''))
        require('directWorldEntityCollisionShapes' in movement and 'rpIndexCandidates' in movement,
                'Native swept collision/index diagnostics are missing')
        if movement.get('probe','').startswith(('NpcWindow-side-','WoodGate-side-')):
            asset='npc_window' if movement['probe'].startswith('NpcWindow') else 'wood_gate'
            require(movement['directWorldEntityCollisionShapes']>0 and any(row['asset']==asset and row['activePhysicalBoxes']>0 for row in movement['rpIndexCandidates']),
                    'Direct world collision query did not include the actual working RP plane')
    tread=next((row for row in value['actualMovementProbes'] if row.get('probe')=='RpStairs-first-native-step'),None)
    require(tread is not None and abs(tread['actual'][1]-.5)<1e-6
            and abs(tread['requested'][0]**2+tread['requested'][2]**2-.16)<1e-6
            and all(abs(tread['actual'][axis]-tread['requested'][axis])<1e-6 for axis in [0,2])
            and abs(tread['end'][1]-tread['firstTreadTopY'])<1e-6
            and tread['playerBodyForwardExtentFromFirstFront']<tread['firstTreadDepth']-1e-6,
            'The real first-tread sweep did not prove exactly one half-meter step without reaching the second riser')
    require(value.get('manual_visual_gameplay') == 'NOT_RUN', 'Server QA must not claim manual client gameplay')
    return value


def copy_proof(wrapper, source):
    proof = wrapper.get('derived_world_copy', {})
    require(proof and resolve(proof['source']) == source and proof.get('source_unchanged_after_run') is True
            and proof.get('copy_byte_verification') == 'PASS', 'Actual restart did not copy the unchanged saved source')
    for row in proof['files']:
        path = source / row['path']
        require(path.stat().st_size == row['bytes'] and sha(path) == row['sha256'],
                'Frozen source changed after restart: ' + row['path'])


def audit_saved(world, marker, authored):
    fields = compound(stored(world, 'bloodborne_dw_v9_gameplay_review'))
    require(fields['Schema'].value == 1, 'Gameplay state schema differs')
    ids = compound(fields['Data']); entities = full_rp_entities(world)
    role_rows = marker['rpObjects'] + marker['lamps'] + marker.get('visualObjects', [])
    visual = compound(ids['VisualObjects']); placed = {row['role']: row for row in authored['ordinaryItemPlacements']}
    require(set(placed) == {row['role'] for row in role_rows}, 'Actual ordinary RP item placement roles differ')
    roles = {}; evidence = []
    for request in role_rows:
        role = request['role']; key = identity(visual[role] if role in visual else ids[role]); roles[role] = key
        require(key in entities and placed[role]['uuid'] == key and placed[role]['itemPath'] == 'Item.useOnBlock',
                'Entity identity/item placement changed: ' + role)
        require(all(placed[role].get(field) == value for field, value in request.items()),
                'Ordinary RP placer request changed: ' + role)
        entity = compound(entities[key]); require(entity['id'].value == 'bloodborne_rp:' + request['asset'],
                                                 'Registry type changed: ' + role)
        if 'dogsVisible' in request:
            require(entity.get('DogsVisible') is not None and bool(entity['DogsVisible'].value) == request['dogsVisible'],
                    'Per-instance dog visibility changed: ' + role)
        if role in {'LeverA', 'LeverB', 'WoodGate'}:
            require(not entity['Open'].value and 'LeverPulseTicks' not in entity, 'Mechanism did not save idle: ' + role)
        if role == 'WoodGate': require('WoodGatePulseTicks' not in entity, 'Wood gate saved an invented queued animation')
        evidence.append({'role': role, 'uuid': key, 'registryId': entity['id'].value,
                         'dogsVisible': bool(entity['DogsVisible'].value) if 'DogsVisible' in entity else None,
                         'open': bool(entity['Open'].value), 'full_typed_nbt_sha256': typed_sha(entities[key])})
    expected_types = Counter('bloodborne_rp:' + row['asset'] for row in role_rows); expected_types['bloodborne_rp:tree1'] += 1
    require(Counter(compound(entity)['id'].value for entity in entities.values()) == expected_types,
            'Exact accepted RP control set differs (tree1 + declared ordinary stand)')
    graph = compound(stored(world, 'bloodborne_dw_mechanism_links')); require(graph['Schema'].value == 1, 'Mechanism graph schema')
    levers = {identity(compound(row)['Id']): compound(row) for row in graph['Levers'].value}
    targets = {}
    for row in graph['Targets'].value:
        target = compound(row); ref = compound(target['Ref']); key = ref['Kind'].value + '|' + ref['Dimension'].value + '|' + identity(ref['Instance'])
        require(key not in targets, 'Duplicate saved target reference')
        require(not target['Pending'].value and not target['UnresolvedFlip'].value and target['PendingPulses'].value == 0,
                'Stand finished with an undelivered effect instead of real final state')
        require(bool(target['Known'].value), 'Stand target never resolved its actual initial state')
        targets[key] = (target, ref)
    require(set(levers) == {roles['LeverA'], roles['LeverB']} and len(targets) == 6,
            'Exact two-lever/six-target graph changed')
    architecture = compound(ids['ArchitectureDoor']); architecture_key = 'ARCHITECTURE|' + architecture['Dimension'].value + '|' + identity(architecture['Instance'])
    for source_role, requested_roles in marker['links'].items():
        keys = []
        for role in requested_roles:
            keys.append(architecture_key if role == 'architectureDoorRoot' else 'RP|minecraft:overworld|' + roles[role])
        actual = [tag.value for tag in levers[roles[source_role]]['Targets'].value]
        require(set(actual) == set(keys) and len(actual) == len(keys), 'Saved outgoing targets changed: ' + source_role)
    require(architecture_key in targets and targets[architecture_key][1] == architecture,
            'Architectural stable owner UUID/root/registry reference changed')
    packed = architecture['Root'].value
    signed = lambda value, bits: value - (1 << bits) if value & (1 << (bits - 1)) else value
    root = (signed((packed >> 38) & ((1 << 26) - 1), 26), signed(packed & 4095, 12),
            signed((packed >> 12) & ((1 << 26) - 1), 26))
    roots, bes, _ = inventory(world)
    require(root in roots and root in bes, 'Linked architecture root is missing')
    resident = compound(compound(bes[root])['resident'])
    require(identity(resident['uuid']) == identity(architecture['Instance'])
            and resident['id'].value == architecture['Registry'].value and tuple(resident['root'].value) == root,
            'Link resolves to a replaced architectural owner')
    root_open = dict(pair.split('=', 1) for pair in roots[root].partition('[')[2].rstrip(']').split(','))['open']
    require(bool(targets[architecture_key][0]['Desired'].value) == (root_open == 'true'),
            'Saved desired architecture state differs from the actual installed door')
    for key, (target, ref) in targets.items():
        if ref['Kind'].value == 'RP':
            entity = compound(entities[identity(ref['Instance'])]); require(ref['Registry'].value == entity['id'].value.removeprefix('bloodborne_rp:'),
                                                                         'Target UUID now resolves to a different art')
            if target['PulseOnly'].value: require(ref['Registry'].value == 'wood_gate', 'Pulse-only target is not the actual wood gate')
            else: require(bool(target['Desired'].value) == bool(entity['Open'].value), 'Persisted desired state differs from actual RP state')
    lamps = compound(stored(world, 'bloodborne_rp_lamps')); require(lamps['Schema'].value == 1, 'Lamp state schema')
    nodes = {identity(compound(row)['Id']): compound(row) for row in lamps['Nodes'].value}
    node_roles = {'LampA': identity(ids['NodeA']), 'LampB': identity(ids['NodeB'])}
    require(set(nodes) == set(node_roles.values()), 'Exact registered two-node lamp stand changed')
    for request in marker['lamps']:
        role = request['role']; node = nodes[node_roles[role]]
        require(identity(node['Lamp']) == roles[role] and node['Name'].value == request['name']
                and node['Dimension'].value == 'minecraft:overworld', 'Saved lamp registration changed: ' + role)
        wanted = [node_roles[b] for a, b in marker['lampRoutes'] if a == role]
        require([identity(tag) for tag in node['Routes'].value] == wanted, 'Directed lamp routes changed: ' + role)
    return {'world': str(world), 'ordinaryRpItemCount': len(role_rows), 'totalRpEntityCount': len(entities),
            'roles': evidence, 'mechanismGraphTypedSha256': typed_sha(stored(world, 'bloodborne_dw_mechanism_links')),
            'lampNetworkTypedSha256': typed_sha(stored(world, 'bloodborne_rp_lamps')),
            'architectureReference': {key: tag.value if key != 'Instance' else identity(tag) for key, tag in architecture.items()},
            'leverCount': len(levers), 'targetCount': len(targets), 'lampNodeCount': len(nodes)}


def run(args):
    author_path, author, author_world = server_phase(args.author_report, args.artifact_sha)
    restart_path, restart, restart_world = server_phase(args.gameplay_restart_report, args.artifact_sha)
    server_path, production, production_world = server_phase(args.server_report, args.artifact_sha)
    require(not any(row.get('extra_mod') for row in production['modset']), 'Final production save still includes QA add-on')
    author_marker, restart_marker = map(resolve, [args.gameplay_input, args.gameplay_reopen_input])
    marker, again = read(author_marker), read(restart_marker); identical = dict(again); identical['phase'] = 'AUTHOR'
    require(marker == identical, 'Actual restart changed the stand request instead of replaying persisted identities')
    first = raw_gameplay(author, author_marker, 'AUTHOR'); second = raw_gameplay(restart, restart_marker, 'REOPEN')
    copy_proof(restart, author_world)
    source = resolve(production['derived_world_copy']['source']); require(source in {author_world, restart_world}, 'Production save is unrelated to current completed stand')
    copy_proof(production, source)
    before = audit_saved(author_world, marker, first); replayed = audit_saved(restart_world, marker, first); final = audit_saved(production_world, marker, first)
    for name in ['bloodborne_rp_lamps']:
        require(stored(author_world, name) == stored(restart_world, name) == stored(production_world, name), 'Actual restart changed stable directed lamp registrations')
    require(full_rp_entities(author_world) == full_rp_entities(restart_world) == full_rp_entities(production_world),
            'Saved RP type/UUID/full typed NBT changed during balanced restart operations or production save')
    require(stored(source, 'bloodborne_dw_mechanism_links') == stored(production_world, 'bloodborne_dw_mechanism_links'),
            'Production-only save changed exact current graph typed data')
    return {'schema': 'dreamwalker-review-v9-gameplay-independent-v1', 'status': 'PASS_REAL_GAMEPLAY_RESTART_IDENTITIES_LINKS_AND_LAMP_NETWORK',
            'production_jar_sha256': args.artifact_sha, 'author_report': str(author_path), 'author_report_sha256': sha(author_path),
            'gameplay_restart_report': str(restart_path), 'gameplay_restart_report_sha256': sha(restart_path),
            'production_reopen_report': str(server_path), 'production_reopen_report_sha256': sha(server_path),
            'gameplay_input': str(author_marker), 'gameplay_input_sha256': sha(author_marker),
            'gameplay_reopen_input': str(restart_marker), 'gameplay_reopen_input_sha256': sha(restart_marker),
            'author': before, 'actual_restart': replayed, 'production_save': final,
            'originalLeverDelayTicks': 70, 'actualSurvivalTravelBothDirections': 'PASS_EXISTING_SERVER_ENDPOINT_AND_LIST_PACKET',
            'manualClientGuiAcceptance': 'NOT_RUN', 'visualAcceptance': 'PENDING_USER_REVIEW', 'wholeCityClaim': False}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for option in ['author-report', 'gameplay-restart-report', 'server-report', 'gameplay-input', 'gameplay-reopen-input']:
        parser.add_argument('--' + option, type=Path, required=True)
    parser.add_argument('--artifact-sha', required=True); parser.add_argument('--report', type=Path, required=True); args = parser.parse_args()
    try: result = run(args)
    except Exception as error: result = {'schema': 'dreamwalker-review-v9-gameplay-independent-v1', 'status': 'FAIL', 'error': str(error), 'production_jar_sha256': args.artifact_sha}
    require(not args.report.exists(), 'Keep historical verifier reports; select a fresh output path')
    args.report.parent.mkdir(parents=True, exist_ok=True); args.report.write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n', encoding='utf8')
    print(json.dumps({'status': result['status'], 'report': str(args.report)}))
    if result['status'] == 'FAIL': raise SystemExit(1)


if __name__ == '__main__': main()
