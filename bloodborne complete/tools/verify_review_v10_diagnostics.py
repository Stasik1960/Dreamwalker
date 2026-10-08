"""Read-only current V10 diagnostics proof; reuses unchanged ZIP/retention gates.

V10 author GUI intentionally changes its fresh stand. This verifier therefore
does not call a V9 whole-stand equality gate on AUTHOR: persisted menu sections
are explicit, and ordinary scene/owner preservation has its separate verifier.
It never launches Minecraft, changes a world, or substitutes historical PASS.
"""
from __future__ import annotations
import argparse
import hashlib
import json
import re
import sys
import zipfile
from pathlib import Path
from verify_review_v9_diagnostics import (
    Verifier, checked_uuid, file_ref, gson_packet_text, world_stability,
)
from package_first_set import ROOT, digest, require, resolve, read, json_bytes

SCHEMA = 'dreamwalker-review-v10-diagnostics-proof-v1'
PASS = 'PASS_CURRENT_V10_ACTUAL_DIAGNOSTICS_OFF_ON_REENTER_LINKED_EXPORTS_AND_PERSISTED_GUI_SECTIONS'
INPUTS = ('jar', 'server_off_report', 'server_on_report', 'server_reenter_report', 'client_report', 'client_reenter_report')
CLIENT_PASS = 'PASS_ACTUAL_CLIENT_V10_MENUS_ORDINARY_RP_PARTS_HEIGHT_NATIVE_OVERLAP_AND_SAVE'
MENU_PASS = 'PASS_ACTUAL_CLIENT_MENUS_PACKETS_RULES_COMMANDS'


def native_registry(state):
    match = re.fullmatch(r'Block\{([a-z0-9_.-]+:[a-z0-9_./-]+)\}(?:\[[^\n]*\])?', state)
    require(match is not None, 'Actual native BlockState.toString registry syntax differs')
    return match.group(1)


def sorted_json(value):
    if isinstance(value, dict):
        return {key: sorted_json(value[key]) for key in sorted(value)}
    if isinstance(value, list):
        return [sorted_json(item) for item in value]
    return value


def persisted_sections(envelope):
    require(envelope.get('schema') == 'dw-v10-menus-persisted-state-v1', 'Actual bounded menu snapshot schema is absent')
    state, canonical = envelope.get('state'), envelope.get('canonicalJson')
    require(isinstance(state, dict) and isinstance(canonical, str) and 100 < len(canonical) < 65536,
            'Actual structured menu state/canonical population absent or unbounded')
    require(json.loads(canonical) == state and gson_packet_text(sorted_json(state)) == canonical
            and hashlib.sha256(canonical.encode('utf8')).hexdigest() == envelope.get('sha256'),
            'Actual recursively sorted Gson UTF8 menu state/hash is inconsistent')
    rule = state.get('rule', {})
    checked_uuid(rule.get('uuid'))
    require(rule.get('name') == 'QA All' and type(rule.get('order')) is int and rule['order'] > 0
            and rule.get('condition') == 'ALL' and rule.get('effect') == 'TOGGLE'
            and rule.get('incomplete') is True and rule.get('satisfied') is False,
            'Saved GUI ALL rule/order/removal state is absent')
    sources, targets = rule.get('sources'), rule.get('targets')
    require(isinstance(sources, list) and len(sources) == 2 and isinstance(targets, list) and len(targets) == 3,
            'Saved mandatory-source removal or mixed target membership differs')
    flags = state.get('sourceFlags', {})
    require(set(flags) == {'lever_1', 'lever_2', 'lever_3'}, 'Saved exact source flag identities absent')
    for key, row in flags.items():
        checked_uuid(row.get('uuid'))
        require(row.get('savedActive') is False and row.get('ruleMember') is (key != 'lever_3'),
                'Reset/persisted shared source flags or removed membership differs')
    require({row.get('uuid') for row in sources} == {flags[key]['uuid'] for key in ('lever_1', 'lever_2')}
            and all(row.get('savedActive') is False for row in sources), 'Rule source UUID/flag binding differs')
    actual_targets = state.get('targets', {})
    require(set(actual_targets) == {'architecture_door', 'rp_door', 'wood_gate'}, 'Exact persisted target policy set absent')
    for key, row in actual_targets.items():
        identity = row.get('identity', {})
        checked_uuid(identity.get('uuid'))
        require(identity in targets and isinstance(identity.get('root'), list) and len(identity['root']) == 3
                and row.get('open') is False and row.get('pendingEffect') is False,
                'Actual saved target UUID/state/root/pending binding differs: ' + key)
    door = actual_targets['architecture_door']
    command = 'scoreboard players add event_counter dw_v10_gui 1'
    require(door.get('leversOnly') is True and door.get('afterOpen') == [command] and door.get('afterClose') == [command]
            and state.get('harmlessCommandCounter') == 2 and actual_targets['wood_gate'].get('actualPulseIdle') is True,
            'Saved manual policy/ordered harmless transition commands or single-pulse idle differs')
    graph = state.get('lampGraph', {})
    nodes, lines = graph.get('nodes'), graph.get('lines')
    require(graph.get('schema') == 2 and isinstance(nodes, list) and len(nodes) == 3
            and isinstance(lines, list) and len(lines) == 1, 'Actual saved named-line graph population differs')
    require({row.get('name') for row in nodes} == {'QA A renamed', 'QA B', 'QA D'}, 'Saved readable lamp names differ')
    by_name = {row['name']: row for row in nodes}
    for row in nodes:
        checked_uuid(row.get('nodeUuid')); checked_uuid(row.get('entityUuid'))
        require(isinstance(row.get('position'), list) and len(row['position']) == 3, 'Precise saved lamp position absent')
    require(abs(by_name['QA B']['position'][1] - 64.125) < 1e-9, 'Actual GUI B instance shift was not retained')
    line = lines[0]; checked_uuid(line.get('lineUuid'))
    require(line.get('name') == 'QA Main' and len(line.get('connections', [])) == 2, 'Explicit saved Main pair set differs')
    directions = set()
    for connection in line['connections']:
        checked_uuid(connection.get('connectionUuid'))
        for start, end, enabled in [('a', 'b', 'aToB'), ('b', 'a', 'bToA')]:
            if connection[enabled]: directions.add((connection[start], connection[end]))
    a, b, d = (by_name[key]['nodeUuid'] for key in ('QA A renamed', 'QA B', 'QA D'))
    require(directions == {(a, b), (a, d), (d, a)}, 'One-way/default-two-way/selected-unlink semantics differ')
    for row in nodes:
        require(set(row.get('routes', [])) == {end for start, end in directions if start == row['nodeUuid']},
                'Saved compatibility outgoing routes differ from explicit named directions')
    removed = state.get('removedFixtures', {})
    checked_uuid(removed.get('lamp_C_uuid')); checked_uuid(removed.get('lever_3_uuid'))
    require(removed.get('lamp_C_still_registered') is False and removed.get('lever_3_still_rule_member') is False
            and removed['lamp_C_uuid'] not in {row['entityUuid'] for row in nodes}
            and removed['lever_3_uuid'] == flags['lever_3']['uuid'], 'Actual removed identity remains in persistent bindings')
    return {'sha256': envelope['sha256'], 'state': state,
            'scope': 'Actual bounded semantic rule/flags/target policies/ordered commands/lamp identities and graph; transient loaded/player/tickets excluded'}


class V10Verifier(Verifier):
    def server(self, report, mode):
        result = super().server(report, mode)
        marker = Path(result['run']) / 'review-diagnostics-input.json'
        document = read(marker)
        require(document.get('revision') == 'V10' and document.get('productionArtifactSha256') == self.sha
                and document.get('mode') == mode and document.get('seconds') == 60,
                'Dedicated diagnostics used another revision/artifact/mode marker')
        requested = document.get('targets', [])
        rows = result['raw'].get('targetStateRows', [])
        require(len(requested) == len(rows) == 15 and {r['name'] for r in requested} == {r['name'] for r in rows},
                'Actual V10 bounded15 initialized diagnostics targets differ')
        indexed = {row['name']: row for row in rows}
        for request in requested:
            actual = indexed[request['name']]
            require(actual.get('root') == request['root'], 'Dedicated actual target root differs')
            if request['kind'] == 'rp':
                require(actual.get('asset') == request['asset'], 'Dedicated actual RP asset differs')
            else:
                require(native_registry(actual.get('state', '')) == request['expectedRegistry'], 'Dedicated actual architecture art differs')
        self.retain(marker)
        result['v10_marker'] = file_ref(marker)
        result['legacy_read_only_tool_row_scope'] = 'Accepted actual RMB/no-block-mutation check; current tool opens a menu. It does not prove an exported GUI snapshot.'
        return result

    def client(self, report, mode):
        wrapper, run = self.wrapper(report, 'client')
        actual = self.raw(wrapper, run, 'client_review_output', Path(report).stem)
        require(actual.get('schema') == 'dw-review-v10-actual-client-v1' and actual.get('status') == CLIENT_PASS
                and actual.get('revision') == 'V10' and actual.get('mode') == mode
                and actual.get('actualIntegratedSave') is True and actual.get('productionJarSha256') == self.sha,
                'Actual V10 current client stage/save/revision/mode did not PASS')
        origins = actual.get('productionArtifactOrigins', [])
        require(origins and all(row.get('sha256') == self.sha for row in origins), 'Actual ordinary V10 production origins differ')
        marker = resolve(wrapper['qa_input']['source'], ROOT); document = read(marker)
        require(digest(marker) == wrapper['qa_input']['sha256'] and digest(marker) == actual.get('markerSha256')
                and document.get('productionJarSha256') == self.sha and document.get('guard') == 'ISOLATED_SAVED_REVIEW_CLIENT_ONLY'
                and document.get('revision') == 'V10' and document.get('menus', {}).get('mode') == mode
                and not document.get('diagnosticOnly'), 'Actual V10 marker guard/artifact/mode differs')
        options = document.get('diagnosticsReview', {})
        require(options.get('seconds') == 20 and options.get('windowTicks') == 100 and options.get('root') == [0, 64, 4],
                'Actual integrated diagnostics20s/window100/root is absent')
        menus = actual.get('menusActual', {})
        require(menus.get('status') == MENU_PASS and menus.get('mode') == mode and menus.get('artifactSha256') == self.sha
                and menus.get('directMutationEndpointsUsedAsGuiProof') is False,
                'Actual V10 widget/ordinary-packet menus failed or used editing endpoints')
        after = persisted_sections(menus.get('persistedStateAfter', {}))
        before = None
        if mode == 'REENTER':
            before = persisted_sections(menus.get('persistedStateBefore', {}))
            require(before['sha256'] == after['sha256'] and menus.get('savedSemanticStateBalancedAfterRemoteTravel') is True
                    and menus.get('ordinaryClientReentryActual') == 'PASS_DISTINCT_SAVED_WORLD_GUI'
                    and menus.get('remoteDestinationBeforePacketChunkLoaded') is False
                    and menus.get('remoteDestinationBeforePacketEntityLoaded') is False
                    and menus.get('savedRemoteDestinationReloadActual') == 'PASS_ORDINARY_GUI_SAVED_UUID_LOAD_ARRIVAL_AND_LEASE_RELEASE',
                    'Actual saved remote UUID load/travel/reentry/balanced persistent semantics absent')
        source = self.source_copy(wrapper); world = resolve(actual['actualWorldDirectory'], ROOT)
        require(world.is_relative_to(run) and world == run / 'saves/prototype-fixture', 'Actual V10 saved world lies outside its runtime')
        self.retain(marker)
        exports = self.client_exports(actual.get('diagnosticsActualClient', {}), run, Path(report).stem)
        return {'report': str(resolve(report, ROOT)), 'source': str(source), 'world': str(world),
                'v10_marker': file_ref(marker), 'persisted_before': before, 'persisted_after': after, **exports,
                'persistence_scope': 'Actual bounded menu semantic snapshot plus diagnostics temporary-root cleanup/linked exports. V10 AUTHOR intentionally edits fresh fixtures; no V9 whole-stand equality is claimed.'}


def run(args):
    require(re.fullmatch(r'[0-9a-f]{64}', args.artifact_sha) and digest(args.jar) == args.artifact_sha, 'Exact frozen ordinary V10 SHA/JAR required')
    verifier = V10Verifier(args)
    off, on, reenter = [verifier.server(getattr(args, field), mode) for field, mode in
                       [('server_off_report', 'DISABLED'), ('server_on_report', 'ENABLED'), ('server_reenter_report', 'REENTER')]]
    require(off['source'] == on['source'] and off['raw']['balancedTargetStateBefore'] == on['raw']['balancedTargetStateBefore'],
            'Actual dedicated OFF/ON did not copy the same exact baseline/state')
    require(resolve(reenter['source'], ROOT) == resolve(on['run'], ROOT) / 'isolated-smoke-world'
            and reenter['raw']['balancedTargetStateBefore'] == on['raw']['balancedTargetStateAfter']
            and reenter['raw']['sessionId'] != on['raw']['sessionId'], 'Actual dedicated REENTER did not use the saved ON world/new session')
    reenter['persistence'] = world_stability(resolve(reenter['source'], ROOT), resolve(reenter['run'], ROOT) / 'isolated-smoke-world')
    client, client_reenter = verifier.client(args.client_report, 'AUTHOR'), verifier.client(args.client_reenter_report, 'REENTER')
    require(client['session'] != client_reenter['session'] and client_reenter['source'] == client['world'],
            'Actual V10 client REENTER did not copy AUTHOR saved world/new session')
    require(client['persisted_after']['sha256'] == client_reenter['persisted_before']['sha256'],
            'Saved AUTHOR GUI policy/rule/flags/lamp identities did not survive actual client restart')
    return {'schema': SCHEMA, 'status': PASS, 'production_jar_sha256': args.artifact_sha,
            'inputs': {name: file_ref(getattr(args, name)) for name in INPUTS},
            'dedicated_server': {'off': off, 'on': on, 'reenter': reenter}, 'client': client, 'client_reenter': client_reenter,
            'evidence': verifier.evidence, 'primary': verifier.primary,
            'default60_automatic_expiry': 'PASS_ACTUAL_DEDICATED_DEFAULT60',
            'client_timing_retention_required': True,
            'client_timing_retention': {'status': 'PASS_CURRENT_WIRE_AND_LOCAL_RETENTION_BOTH_ACTUAL_V10_CLIENT_RUNS',
                                      'client': client['client_timing_retention'], 'client_reenter': client_reenter['client_timing_retention']},
            'off_on_comparison': 'Actual default60 dedicated whole-tick samples and client20s matched OFF/ON/OFF raw presentation intervals; observers present in both modes',
            'interpretation': {'gpu': 'NOT_MEASURED', 'exact_object_fps_percentage': 'NOT_MEASURED',
                               'whole_mod_network': 'NOT_MEASURED; only instrumented payloads',
                               'causal_overhead': 'NOT_ESTABLISHED_BY_RAW_OFF_ON; GC/JFR/cold initialization/shared process timing separately scoped',
                               'rp_ordinary_placement_model_chain': 'NOT_PROBED_BY_THIS_DIAGNOSTICS_STAGE; its acknowledgement chain covers90010 architecture only'},
            'manual_visual_acceptance': 'PENDING_USER_REVIEW', 'full_task_status': 'NOT_READY_FULL_TASK'}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in INPUTS: parser.add_argument('--' + name.replace('_', '-'), type=Path, required=True)
    parser.add_argument('--artifact-sha', required=True); parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    for name in INPUTS: setattr(args, name, resolve(getattr(args, name), ROOT))
    args.output = resolve(args.output, ROOT)
    require(args.output.is_relative_to((ROOT / 'reports').resolve()) and not args.output.exists(), 'Fresh project report path required; do not overwrite history')
    proof = run(args); args.output.write_bytes(json_bytes(proof))
    print(json.dumps({'status': proof['status'], 'artifact_sha256': args.artifact_sha, 'report': str(args.output)}))


if __name__ == '__main__':
    try: main()
    except (ValueError, KeyError, OSError, TypeError, zipfile.BadZipFile) as failure:
        print(json.dumps({'status': 'FAIL_CURRENT_V10_DIAGNOSTICS_PROOF', 'error': str(failure)}), file=sys.stderr)
        raise SystemExit(1)
