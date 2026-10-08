"""Extract actual bounded V10 diagnostic costs; never infer causal FPS/GPU cost."""
import argparse
import hashlib
import json
from pathlib import Path
import zipfile


def read(path):
    return json.loads(Path(path).read_text(encoding='utf-8'))


def evidence(path):
    path = Path(path)
    return {'path': str(path.resolve()), 'bytes': path.stat().st_size,
            'sha256': hashlib.sha256(path.read_bytes()).hexdigest()}


def member(path, name):
    with zipfile.ZipFile(path) as archive:
        info = archive.getinfo(name)
        if info.file_size > 4 * 1024 * 1024:
            raise ValueError('Not a bounded diagnostic JSON member: ' + name)
        return json.loads(archive.read(name))


def duration_ms(row):
    return {key.replace('_ns', '_ms'): (value / 1_000_000 if isinstance(value, (int, float)) else value)
            for key, value in row.items() if key in
            ('mean_ns', 'median_ns', 'p95_ns', 'p99_ns', 'min_ns', 'max_ns')} | {
                'samples': row['samples'], 'unit': 'ms'}


def memory(samples):
    keys = ('heapUsedBytes', 'heapCommittedBytes', 'heapMaxBytes',
            'gcCollectionCountProcessCumulative', 'gcMxCollectionTimeMsProcessCumulative')
    return {key: {'first': samples[0].get(key), 'last': samples[-1].get(key),
                  'minimumRetained': min(x[key] for x in samples if key in x),
                  'maximumRetained': max(x[key] for x in samples if key in x)}
            for key in keys if samples and any(key in x for x in samples)}


def server_export(export):
    session, measurements = export['session'], export['measurements']
    samples = measurements.get('processSamples', [])
    return {'session': session['sessionId'], 'processId': session['environment']['processId'],
            'requestedSeconds': session['secondsRequested'], 'actualElapsedSeconds': session['elapsedNs']/1e9,
            'stopReason': session['stopReason'],
            'ownInclusiveApiHookDurationsNs': measurements['diagnosticsOwnSynchronousOverhead'],
            'ownScope': measurements['diagnosticsOwnOverheadScope'],
            'ioThreadCpu': measurements['diagnosticsIoThreadCpuNs'],
            'actualGcPauseSource': measurements['gcActualPausesStatus'],
            'actualGcPauseDurationsNs': measurements['gcActualPauses'],
            'processSampleCount': len(samples), 'processMemorySamples': memory(samples),
            'latestSampledCachesAndQueues': {key: value for key, value in (samples[-1] if samples else {}).items()
                if isinstance(value, dict) or key.startswith('diagnosticIo')},
            'processSamplingScope': 'Retained process samples, usually once per second; extrema are not exhaustive peaks. MX collection duration is not STW pause duration.',
            'export': {'path': export['path'], 'sha256': export['sha256']}}


def client_export(client_path, server_path, windows, retention=None):
    summary = member(client_path, 'summary.json')
    runtime = member(client_path, 'runtime.json')
    batches = member(client_path, 'client-batches.json')
    server_session = member(server_path, 'session.json')
    measurements = member(server_path, 'measurements.json')
    if runtime['productionArtifactSha256'] != server_session['environment']['productionArtifactSha256']:
        raise ValueError('Client and integrated server artifact identities differ')
    result = {'session': summary['session'], 'clientProcessId': runtime['processId'],
        'integratedServerProcessId': server_session['environment']['processId'],
        'sameJvmProcess': runtime['processId'] == server_session['environment']['processId'],
        'presentationWindows': windows,
        'clientFlushElapsedTotalMs': summary['flushOverheadNs']/1e6,
        'clientFlushScope': 'Cumulative elapsed sendBatch work in this session; not CPU/GPU time and not all diagnostic work.',
        'clientIoThreadCpuMsProcessCumulative': summary['ioThreadCpuNsProcessCumulative']/1e6,
        'clientIoScope': 'IO worker process-cumulative counter; may include earlier exports in this process. Not session-only.',
        'deliveredBatchCount': len(batches), 'clientRetainedErrorKeys': summary['errorKeys'],
        'queuesAtExport': {key: summary[key] for key in ('ioQueueSize','ioQueueCapacity','ioActiveTasks','ioRejectedTasks')},
        'lastDeliveredMemory': batches[-1].get('memory') if batches else 'NOT_MEASURED',
        'integratedServerOwnInclusiveApiHookDurationsNs': measurements['diagnosticsOwnSynchronousOverhead'],
        'integratedServerOwnScope': measurements['diagnosticsOwnOverheadScope'],
        'processResourceScope': 'Client/server share a PID in integrated play: do not add heap, GC, caches or process CPU twice.',
        'localTimingRetention': retention or 'Already checked by bound actual-client independent report',
        'clientExport': evidence(client_path), 'serverExport': evidence(server_path)}
    if summary['session'] != server_session['sessionId']:
        raise ValueError('Client/server export session identities differ')
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--proof', type=Path, required=True)
    parser.add_argument('--full-wrapper', type=Path, required=True)
    parser.add_argument('--full-independent', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    if args.output.exists():
        raise ValueError('Preserve historical cost observations; use a fresh report path')
    proof, full, full_proof = read(args.proof), read(args.full_wrapper), read(args.full_independent)
    artifact_sha = proof['production_jar_sha256']
    if not (proof['status'].startswith('PASS_CURRENT_V10_ACTUAL_DIAGNOSTICS') and
            full['status'] == 'PASS_CLIENT_WORLD_LOAD_SAVE_NORMAL_EXIT' and full['exit_code'] == 0 and
            full['artifact_sha256'] == full_proof['productionJarSha256'] == artifact_sha and
            full_proof['status'].startswith('PASS_CURRENT_ACTUAL_CLIENT_ROSTER_PARTS_MIDDLE_KEY')):
        raise ValueError('Require actual current artifact independent and normal full-client proofs')
    report = {'schema': 'dw-v10-diagnostics-cost-observation-v1',
        'status': 'OBSERVED_CURRENT_ACTUAL_DIAGNOSTIC_COSTS_NO_CAUSAL_OVERHEAD_PERCENTAGE',
        'productionJarSha256': artifact_sha,
        'evidence': [evidence(x) for x in (args.proof, args.full_wrapper, args.full_independent)],
        'dedicatedServer': {}, 'integratedClients': {},
        'scopes': {'dedicated': 'Separate OFF60/ON60 processes use the exact same pristine Author8 baseline and balanced operations; REENTER uses ON saved copy and explicit short stop.',
            'frames': 'Matched camera/cleaned scene/settings OFF/ON/OFF presentation intervals with observers in all windows; variance is descriptive, not causal diagnostics cost.',
            'sampledTimers': 'FIRST_THEN_EVERY_32_CALLS; nested/inclusive sampled elapsed times cannot be summed to unique CPU or extrapolated to total rendering cost.',
            'gc': 'JFR actual pause durations only when observed; zero observed events means NOT_MEASURED_NO_SAMPLES. MXBean collection duration includes other phases.',
            'background': 'No intended Minecraft/Gradle overlap during dedicated windows. OS/background load uncontrolled.',
            'gpu': 'NOT_MEASURED', 'exactBlockFpsPercentage': 'NOT_MEASURED',
            'wholeNetworkTraffic': 'NOT_MEASURED; only instrumented custom payload channels',
            'manualVisualAcceptance': 'PENDING_USER_REVIEW'}}
    for mode, row in proof['dedicated_server'].items():
        result = {'wholeTick': duration_ms(row['tick_samples']), 'report': row['report']}
        if 'export' in row:
            result |= server_export(row['export'])
        report['dedicatedServer'][mode] = result
    for key in ('client', 'client_reenter'):
        row = proof[key]
        report['integratedClients'][key] = client_export(row['client_export']['path'], row['server_export']['path'],
            [duration_ms(x) | {'label': x['label']} for x in row['timings']], row['client_timing_retention'])
    full_raw = full['client_review_output']['result']['diagnosticsActualClient']
    windows = [{key: row[key] for key in ('label','measurements','averageNs','p95Ns','p99Ns','retainedSamples','droppedSamples')}
               for row in full_raw['matchedPresentationWindows']]
    report['integratedClients']['fullKappa'] = client_export(full_raw['clientExport'], full_raw['serverExport'], windows)
    report['integratedClients']['fullKappa']['windowUnit'] = 'ns'
    report['wholeTaskStatus'] = 'NOT_READY_FULL_TASK; measured runtime checks do not replace manual review'
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
    print(json.dumps({'status': report['status'], 'path': str(args.output),
                     'dedicatedOffMeanMs': report['dedicatedServer']['off']['wholeTick']['mean_ms'],
                     'dedicatedOnMeanMs': report['dedicatedServer']['on']['wholeTick']['mean_ms'],
                     'integratedClients': list(report['integratedClients'])}))


if __name__ == '__main__':
    main()
