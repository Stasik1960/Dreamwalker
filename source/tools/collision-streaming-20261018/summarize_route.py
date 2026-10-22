"""Compare the one matched before/after route; preserve delivered-chunk denominators."""
import argparse, json, math
from pathlib import Path

PROJECT = Path(__file__).absolute().parents[2]
EVIDENCE = PROJECT.parent / 'deliverables/collision-streaming-20261018/evidence'


def stats(values):
    a = sorted(values)
    if not a:
        return {'count': 0}
    result = {'count': len(a), 'averageMs': sum(a) / len(a) / 1e6, 'maxMs': a[-1] / 1e6}
    for pct in [50, 95, 99]:
        result['p' + str(pct) + 'Ms'] = a[math.ceil(len(a) * pct / 100) - 1] / 1e6
    for ms in [50, 100, 250, 500, 1000]:
        pauses = [x for x in a if x > ms * 1e6]
        result['over' + str(ms) + 'ms'] = len(pauses)
        result['durationOver' + str(ms) + 'msSeconds'] = sum(pauses) / 1e9
        result['excessOver' + str(ms) + 'msSeconds'] = sum(x - ms * 1e6 for x in pauses) / 1e9
    return result


def read_case(case):
    prep = json.loads((EVIDENCE / (case + '-prepared.json')).read_text())
    launch = json.loads((EVIDENCE / (case + '-launch.json')).read_text())
    assert launch['exitCode'] == 0 and launch['captureComplete'] and launch['normalSaveMessages']
    cap = json.loads((Path(prep['run_directory']) / 'client-capture-output.json').read_text())
    assert cap['status'] == 'CAPTURE_COMPLETE' and len(cap['frames']) == 3
    mem = [json.loads(x) for x in (EVIDENCE / (case + '-memory.jsonl')).read_text().splitlines()]
    phases = cap['streamingPhases']
    assert len(phases) == 3 and [x['phase'] for x in phases] == ['facade', 'tower', 'brick']
    for p in phases:
        assert p['presentation']['count'] < p['presentation']['sampleLimit'], 'Presentation sample cap invalidates full-route comparison'
    frames = []
    for f in cap['frames']:
        assert f['requiredClientChunkEvidence']['expected'] == f['requiredClientChunkEvidence']['delivered'] == 289
        assert f['requiredClientBlockEvidence']['ready']
        frames.append({k: f.get(k) for k in ['key', 'actualCamera', 'travel', 'nativeMemory', 'loadedChunks', 'requiredClientChunkEvidence', 'requiredClientBlockEvidence']})

    def peak(key, rows):
        return max((x[key] for x in rows if x.get(key) is not None), default=None)

    keys = ['workingSetBytes', 'privateCommitBytes', 'dedicatedGpuBytes', 'sharedGpuBytes']
    travel_mem = [x for x in mem if x.get('captureFrame') in ['tower', 'brick']]
    travel = phases[1:]
    loads = sum(p['chunkDelivery']['loadCallbacks'] for p in travel)
    timings = stats([x for p in travel for x in p['presentation']['rawNs']])
    return prep, {
        'jarSha256': prep['artifact_sha256'], 'fixtureManifestSha256': prep['fixtureManifestSha256'],
        'javaSha256': prep['javaSha256'], 'optionsSha256': prep['optionsSha256'],
        'gallerySha256': prep['extra_mods'][0]['sha256'],
        'initialTeleportAndSettleSeconds': phases[0]['durationMs'] / 1000,
        'travelAndEqualDeliveryWaitSeconds': sum(p['durationMs'] for p in travel) / 1000,
        'travelPresentation': timings, 'travelServerWorldTicks': stats([x for p in travel for x in p['serverWorldTicks']['rawNs']]),
        'deliveredChunks': {'loadCallbacks': loads, 'unloadCallbacks': sum(p['chunkDelivery']['unloadCallbacks'] for p in travel),
                            'sumUniquePerPhase': sum(p['chunkDelivery']['uniqueDeliveredChunks'] for p in travel),
                            'note': 'Unique is within each phase; same chunk crossing phase boundaries can contribute twice to the sum.'},
        'over100msPresentationSecondsPer1000Deliveries': timings['durationOver100msSeconds'] * 1000 / loads if loads else None,
        'peakMemoryWholeRun': {k: peak(k, mem) for k in keys},
        'peakMemoryTravel': {k: peak(k, travel_mem) for k in keys}, 'frames': frames,
        'phases': [{**{k: v for k, v in p.items() if k not in ['presentation', 'serverWorldTicks']},
                    'presentation': stats(p['presentation']['rawNs']), 'serverWorldTicks': stats(p['serverWorldTicks']['rawNs'])} for p in phases],
        'jfr': str(Path(prep['run_directory']) / 'streaming.jfr'),
    }


def normalized_command(command):
    result = list(command)
    result[result.index('--gameDir') + 1] = '<MATCHED_RUNTIME>'
    return ['-XX:StartFlightRecording=<MATCHED_PROFILE_UNIQUE_FILENAME>' if x.startswith('-XX:StartFlightRecording=') else x for x in result]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--before', default='shapes-before')
    ap.add_argument('--after', default='shapes-after')
    ap.add_argument('--single-case', help='Summarize a saved case without claiming a comparison')
    args = ap.parse_args()
    if args.single_case:
        _, summary = read_case(args.single_case)
        output = EVIDENCE / (args.single_case + '-summary.json')
        output.write_text(json.dumps(summary, indent=2) + '\n')
        print(json.dumps({k: summary[k] for k in ['travelAndEqualDeliveryWaitSeconds', 'travelPresentation', 'deliveredChunks', 'peakMemoryTravel']}))
        return
    a, before = read_case(args.before)
    b, after = read_case(args.after)
    assert a['derived_world_copy']['files'] == b['derived_world_copy']['files'], 'World inputs differ'
    assert a['qaHeadRemoval'] == b['qaHeadRemoval'], 'Camera metadata differs'
    assert a['graphics'] == b['graphics'] and a['optionsSha256'] == b['optionsSha256'], 'Settings differ'
    assert a['javaSha256'] == b['javaSha256'], 'Java differs'
    assert a['extra_mods'] == b['extra_mods'], 'QA instrumentation differs'
    assert normalized_command(a['command']) == normalized_command(b['command']), 'Java arguments differ'
    report = {'status': 'PASS_ONE_MATCHED_NATIVE_ROUTE', 'scope': 'One facade->tower->brick pass per JAR, two native one-block/tick travel legs plus identical FULL-delivery wait gates. All 289 exact addressed FULL chunks required at every endpoint; renderer complete and 100 stable resident-count ticks. Startup/resource bake/final save are outside phase timing. Teleport/settle reported separately. CHUNK_LOAD callbacks are actual accepted client deliveries, not resident count.',
              'limitations': ['One paired observation, no statistical confidence interval.', 'OS file cache was not flushed; baseline runs first.', 'JFR profile was identical on both runs and includes startup; timing phases bound the route.', 'ServerWorld.tick timings exclude other server-loop work.', 'Performance input uses identical untouched backup maps to isolate code. Confirmed barrier cleanup is verified separately on the deliverable map.'],
              'runs': {args.before: before, args.after: after}, 'matchedActiveFiles': len(a['derived_world_copy']['files'])}
    output = EVIDENCE / 'route-comparison.json'
    output.write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps({'status': report['status'], 'report': str(output), 'beforeSeconds': before['travelAndEqualDeliveryWaitSeconds'], 'afterSeconds': after['travelAndEqualDeliveryWaitSeconds'], 'beforeDelivered': before['deliveredChunks']['loadCallbacks'], 'afterDelivered': after['deliveredChunks']['loadCallbacks'], 'beforeMaxPauseMs': before['travelPresentation']['maxMs'], 'afterMaxPauseMs': after['travelPresentation']['maxMs']}))


if __name__ == '__main__':
    main()
