"""Bind actual QA, exact production/addon bytes and normal dedicated shutdown.

This is a read-only check of completed runs, not a replacement for running them.
Any unexpected server error, mismatched artifact, unfinished or failed QA rejects
the final PASS report. Original failed fixture evidence remains separately linked.
"""
from __future__ import annotations
import argparse
import hashlib
import json
import shutil
from pathlib import Path


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--jar', type=Path, required=True)
    p.add_argument('--server-report', type=Path, required=True)
    p.add_argument('--qa-report', type=Path, required=True)
    p.add_argument('--kind', choices=('physics', 'migration'), required=True)
    p.add_argument('--previous-qa-failure', type=Path, action='append', default=[])
    p.add_argument('--resolved-reason')
    p.add_argument('--history-manifest', type=Path)
    p.add_argument('--output', type=Path, required=True)
    a = p.parse_args()
    server = json.loads(a.server_report.read_text(encoding='utf-8'))
    qa = json.loads(a.qa_report.read_text(encoding='utf-8'))
    digest = sha(a.jar)
    assert server['artifact_sha256'] == digest, 'Completed run used another production JAR'
    assert server['status'] == 'PASS' and server['world_startup'] == 'PASS' and server['exit_code'] == 0, 'Server startup/save/normal shutdown failed'
    assert not server['errors'] and not server.get('termination'), 'Unexpected server error or forced termination'
    assert qa['status'] == 'PASS', 'Actual collision/migration QA did not pass'
    console = Path(server['evidence'])
    assert sha(console) == server['console_sha256'], 'Completed server log changed'
    addon = next(m for m in server['extraMods'] if m['id'] == 'dreamwalker_collision_audit')
    copied = Path(server['run_directory']) / addon['runtimePath']
    assert sha(copied) == addon['sha256'], 'QA runtime addon differs from launch evidence'
    if a.kind == 'physics':
        assert qa['newRegistryCount'] == 1045 and qa['offeredVariantCount'] == 1802 and qa['exactGeometryStates'] == 57664
        rows = qa['movementAndTransactions']
        assert len(rows) >= 48 and all(r['status'] == 'PASS' for r in rows)
        assert sum('climbingAndAscent' in r for r in rows) >= 10
        assert sum('stairAscentToLanding' in r for r in rows) == 2
        assert sum('nativeStairAscent' in r for r in rows) == 12
        assert sum('lowPotOrdinaryStep' in r for r in rows) == 2
        assert qa['realAuthorReplacement'] == 'NOT_APPLIED_EXPORT_NOT_SUPPLIED'
    else:
        assert qa['changedSavedNewObjects'] >= 4 and qa['unchangedV12Controls'] == 4
        assert sum(r['status'] == 'PASS_RETIRED_ROOT_AND_CACHED_PHYSICS_REMOVED' for r in qa['cases']) == 3
        assert qa['unchangedForeignCarriers'] >= 1 and qa['foreignInventoryAndCarrierNbt'].startswith('PASS_')
        assert server['derived_world_copy']['copy_byte_verification'] == 'PASS' and server['derived_world_copy']['source_unchanged_after_run']
    report = dict(qa)
    def portable(path):
        destination = a.output.parent / 'runtime-evidence' / (path.parent.name + '-' + path.name)
        destination.parent.mkdir(parents=True, exist_ok=True)
        if destination.exists(): assert sha(destination) == sha(path), 'Historical evidence must not be overwritten'
        else: shutil.copyfile(path, destination)
        return str(destination.relative_to(a.output.parent)).replace('\\', '/')
    report.update(productionJarSha256=digest, addonSha256=addon['sha256'], normalDedicatedServerShutdown='PASS', unexpectedServerErrorCount=0,
        dedicatedServerEvidence={'file': str(a.server_report.resolve()), 'sha256': sha(a.server_report), 'sourceBundleCopy': portable(a.server_report), 'console': str(console), 'consoleSha256': sha(console), 'consoleSourceBundleCopy': portable(console)},
        actualQaEvidence={'file': str(a.qa_report.resolve()), 'sha256': sha(a.qa_report), 'sourceBundleCopy': portable(a.qa_report)})
    report['expectedWarnings'] = server['expectedWarnings']
    if a.kind == 'migration':
        report['previousDerivedGallerySourceUnchanged'] = True
        # The native Windows JVM's old Russian level title can contain non-UTF8
        # bytes. Keep the original byte-bound console intact and use replacement
        # only for extracting these ASCII registry warning lines.
        report['retirementWarnings'] = [line for line in console.read_text(encoding='utf-8', errors='replace').splitlines() if 'source_jungle_trapdoor_bottom' in line or 'source_jungle_trapdoor_open' in line]
    if a.previous_qa_failure:
        assert a.resolved_reason, 'A concrete verified resolution is required'
        report['resolvedQaFixtureFailures'] = []
        for path in a.previous_qa_failure:
            prior = json.loads(path.read_text(encoding='utf-8'))
            assert prior['status'] == 'FAIL'
            report['resolvedQaFixtureFailures'].append({'file': str(path.resolve()), 'sha256': sha(path), 'failure': prior.get('failure'), 'resolution': a.resolved_reason})
    if a.history_manifest:
        history = json.loads(a.history_manifest.read_text(encoding='utf-8'))
        for category in ('resolvedProductionDefects', 'correctedQaFixtureAssumptions'):
            report[category] = []
            for row in history.get(category, []):
                if row.get('runtimeKind') != a.kind: continue
                assert row['verifiedResolution'], 'History entry requires a concrete resolution'
                evidence = []
                for value in row['evidence']:
                    path = Path(value)
                    evidence.append({'file': str(path.resolve()), 'sha256': sha(path), 'sourceBundleCopy': portable(path)})
                report[category].append({'failure': row['failure'], 'affectedArtifactSha256': row['affectedArtifactSha256'],
                    'evidence': evidence, 'verifiedResolution': row['verifiedResolution'],
                    'finalRetestArtifactSha256': digest, 'finalRetestStatus': qa['status']})
    a.output.parent.mkdir(parents=True, exist_ok=True)
    a.output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')


if __name__ == '__main__':
    main()
