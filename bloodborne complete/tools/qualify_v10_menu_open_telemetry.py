"""Qualify actual bounded menu telemetry without inferring a production fix."""
from __future__ import annotations
import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--raw', type=Path, required=True)
    parser.add_argument('--wrapper', type=Path, required=True)
    parser.add_argument('--prior-raw', type=Path, required=True)
    parser.add_argument('--prior-wrapper', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    if args.output.exists():
        raise FileExistsError('Preserve existing evidence; use a fresh report path')
    raw, wrapper, prior, prior_wrapper = [json.loads(p.read_text('utf8')) for p in
                                        [args.raw, args.wrapper, args.prior_raw, args.prior_wrapper]]
    assert wrapper['status'] == 'PASS_CLIENT_WORLD_LOAD_SAVE_NORMAL_EXIT' and wrapper['exit_code'] == 0
    assert wrapper['artifact_sha256'] == prior_wrapper['artifact_sha256'] == raw['productionJarSha256']
    assert prior['status'].startswith('FAIL_') and raw['status'].startswith('PASS_')
    menus = raw['menusActual']
    assert menus['status'] == 'PASS_ACTUAL_CLIENT_MENUS_PACKETS_RULES_COMMANDS'
    traces = menus['menuOpenTelemetry']
    assert 0 < len(traces) <= 64
    rows = []
    for trace in traces:
        before = trace['beforeOrdinaryPacket']['server']
        observed = trace['itemUseAndFirstView']['actualServerItemUse']
        wanted = trace['expectedUuid']
        assert trace['outcome'] == 'PASS_EXACT_SERVER_VIEW_TARGET'
        assert observed['readThread'] == before['readThread'] == 'ACTUAL_SERVER_THREAD'
        assert observed['stage'] == 'ACTUAL_SERVER_USE_ITEM_CALLBACK_BEFORE_ITEM_USE'
        assert observed['expectedUuid'] == before['expectedUuid'] == wanted
        assert trace['itemUseAndFirstView']['observerAlwaysReturnsPASS'] is True
        candidates = observed['builderCandidates']
        before_candidates = before['builderCandidates']
        assert len(candidates) <= 32 and len(before_candidates) <= 32
        rows.append({
            'operation': trace['operation'], 'fixture': trace['fixture'], 'expectedUuid': wanted,
            'exactReceivedMenuIdentityPredicate': trace['outcome'],
            'ordinaryItemResult': trace['ordinaryItemResult'],
            'beforeServerTick': before['serverTick'], 'actualServerUseTick': observed['serverTick'],
            'actualServerCandidateContainsExpected': observed['expectedUuidInBuilderCandidates'],
            'actualServerFirstCandidateIsExpected': bool(candidates) and candidates[0]['uuid'] == wanted,
            'beforeFirstCandidateIsExpected': bool(before_candidates) and before_candidates[0]['uuid'] == wanted,
            'actualServerUseNanoMinusBeforeSnapshotNano': observed['nanoTime'] - before['nanoTime'],
            'actualServerExpectedRpLookup': observed['expectedEntityLookupLoaded'],
            'actualServerExpectedRpIndex': observed['expectedEntityInIndexedQuery'],
            'expectedKind': candidates[0]['kind'] if candidates else 'NO_CANDIDATE',
            'firstMismatchedViewObserved': 'firstMismatchedView' in trace['itemUseAndFirstView'],
            'timeoutObserved': 'timeout' in trace,
        })
    report = {
        'schema': 'dw-v10-actual-menu-open-telemetry-qualification-v1',
        'status': 'PASS_ACTUAL_MENU_OPEN_TELEMETRY_PRIOR_FAILURE_NOT_REPRODUCED_CAUSE_UNRESOLVED',
        'productionJarSha256': wrapper['artifact_sha256'],
        'actualClientStatus': raw['status'], 'actualMenusStatus': menus['status'],
        'completedMenuSteps': menus['completedSteps'],
        'openCount': len(rows), 'fixtureCounts': dict(Counter(row['fixture'] for row in rows)),
        'exactReceivedMenuUuidPredicatesPassed': len(rows),
        'actualServerUseCallbacksObserved': len(rows),
        'actualServerCandidatesContainExpectedCount': sum(row['actualServerCandidateContainsExpected'] for row in rows),
        'actualServerFirstCandidateExpectedCount': sum(row['actualServerFirstCandidateIsExpected'] for row in rows),
        'beforeServerFirstCandidateExpectedCount': sum(row['beforeFirstCandidateIsExpected'] for row in rows),
        'firstMismatchedViewsObserved': sum(row['firstMismatchedViewObserved'] for row in rows),
        'timeoutsObserved': sum(row['timeoutObserved'] for row in rows),
        'rows': rows,
        'priorAttempt': {
            'wholeClientStatus': prior['status'], 'menusStatus': prior['menusActual']['status'],
            'completedMenusSteps': prior['menusActual']['completedSteps'],
            'failedStep': prior['menusActual']['failedStep'],
            'expectedUuid': prior['menusActual']['lastFixtureReadiness']['expectedUuid'],
            'actualMenuViewTarget': 'NOT_RECORDED', 'actualServerAtOpenPoseAndCandidates': 'NOT_RECORDED',
            'priorFailureWasReproducedInCurrentAttempt': False,
        },
        'qualification': [
            'Same frozen production artifact; QA27 added observations, not a production correction.',
            'Extra server task submission and pure selection reads may fill lazy geometry caches and change scheduling. Their causal effect was not isolated.',
            'No first mismatched view or timeout was observed in this run. Missing telemetry in ATT2 prevents attribution to a specific stale/missing target, pose, lookup, mod, or timing condition.',
            'RP-index/entity lookup false for architectural block UUIDs is expected; candidate/received menu UUID remains the applicable identity proof.',
            'Passed exact UUID predicates are actual QA observations. This qualification does not replace independent whole-client, shader, persistence, or manual visual acceptance gates.',
        ],
        'evidence': [{'path': str(path.resolve()), 'sha256': digest(path)} for path in
                     [args.raw, args.wrapper, args.prior_raw, args.prior_wrapper]],
        'tool': {'path': str(Path(__file__).resolve()), 'sha256': digest(Path(__file__))},
        'gameOrBuildLaunched': False, 'sourceOrWorldChanged': False,
    }
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', 'utf8')
    print(json.dumps({'report': str(args.output.resolve()), 'sha256': digest(args.output),
                      'status': report['status'], 'opens': len(rows),
                      'firstCandidatesExpected': report['actualServerFirstCandidateExpectedCount'],
                      'firstMismatches': report['firstMismatchedViewsObserved']}))


if __name__ == '__main__':
    main()
