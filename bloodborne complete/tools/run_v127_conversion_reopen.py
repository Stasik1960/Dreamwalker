"""Bounded production-only reopen of a tiny native converter fixture.

Fresh profile/copy, verified bootstrap/dependencies and already accepted EULA.
--prepare-only stages without Java. Original city and author world stay intact.
"""
import argparse
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import time
from package_v127_release import ROOT, ordinary, files, read, sha
from prepare_v127_server_bootstrap import stage_bootstrap
from run_v127_full_conversion import metadata
from run_v11_server import classify_log


def fingerprint(source):
    """Complete regular-file set; reject reparses instead of silently skipping."""
    rows = []
    for current, directories, names in os.walk(source, followlinks=False):
        base = Path(current)
        assert ordinary(base)
        assert all(ordinary(base / name) for name in directories + names), 'Reparse in source fixture'
        for name in sorted(names):
            path = base / name
            assert path.is_file()
            rows.append({'path': path.relative_to(source).as_posix(), 'bytes': path.stat().st_size, 'sha256': sha(path)})
    return sorted(rows, key=lambda row: row['path'])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ('world-copy', 'runtime-directory', 'production-jar', 'fabric-api', 'geckolib',
                 'server-bootstrap', 'server-launcher', 'accepted-eula-file', 'conversion-launch', 'report'):
        parser.add_argument('--' + name, type=Path, required=True)
    java_home = os.environ.get('JAVA_HOME17') or os.environ.get('JAVA_HOME')
    parser.add_argument('--java-home', type=Path, default=Path(java_home) if java_home else None)
    parser.add_argument('--heap-gb', type=int, default=4)
    parser.add_argument('--startup-timeout', type=int, default=600)
    parser.add_argument('--shutdown-timeout', type=int, default=120)
    parser.add_argument('--hold-seconds', type=int, default=5)
    parser.add_argument('--prepare-only', action='store_true')
    args = parser.parse_args()
    assert 2 <= args.heap_gb <= 12 and 10 <= args.startup_timeout <= 1200
    assert 10 <= args.shutdown_timeout <= 180 and 0 <= args.hold_seconds <= 30
    assert all(ordinary(path) for path in (args.world_copy, args.runtime_directory, args.report, args.accepted_eula_file, args.conversion_launch))
    source, run, report = args.world_copy.resolve(), args.runtime_directory.resolve(), args.report.resolve()
    assert source.is_relative_to(ROOT / 'build') and run.is_relative_to(ROOT / 'build') and report.is_relative_to(ROOT / 'build')
    assert source != run and not run.exists() and not report.exists()
    assert not source.is_relative_to(run) and not run.is_relative_to(source), 'Source and runtime must be separate, never nested'
    assert (source / 'level.dat').is_file()
    derived, converted = read(source / 'dw-derived-copy.json'), read(source / 'dw-conversion-result.json')
    assert derived.get('fixtureOnly') is True, 'This bounded helper cannot open the original city'
    assert converted['status'] == 'PASS_RUNTIME_CONVERTED_REOPEN_REQUIRED'
    production = args.production_jar.resolve(); digest = sha(production)
    assert converted['productionJarSha256'] == digest and converted['initialOverlapPrivilegeSerialized'] is False
    launch_report_path = args.conversion_launch.resolve()
    assert launch_report_path.is_relative_to(ROOT / 'build') and launch_report_path.is_file()
    launch_report_sha = sha(launch_report_path)
    author = read(launch_report_path)
    assert author['status'] == 'PASS_RUNTIME_CONVERTED_REOPEN_REQUIRED' and author['mode'] == 'FIXTURE_NATIVE_TEST'
    assert author['exitCode'] == 0 and author['productionJarSha256'] == digest
    assert Path(author['derivedWorld']).resolve() == source and author['runtimeResult'] == converted
    assert author['runtimeManifestSha256'] == converted['runtimeManifestSha256']
    assert derived['operationId'] == converted['operationId']
    source_before = fingerprint(source)
    eula = args.accepted_eula_file.resolve()
    assert re.search(r'(?m)^\s*eula\s*=\s*true\s*$', eula.read_text(encoding='utf-8-sig'))
    inputs = [production, args.fabric_api.resolve(), args.geckolib.resolve()]
    for path, expected in zip(inputs, ('bloodborne_dw', 'fabric-api', 'geckolib')):
        assert ordinary(path) and metadata(path)['id'] == expected
    run.mkdir(parents=True)
    bootstrap = stage_bootstrap(args.server_bootstrap, run, args.server_launcher)
    mods = run / 'mods'; mods.mkdir(); modset = {}
    for path in inputs:
        destination = mods / path.name; shutil.copyfile(path, destination)
        assert sha(destination) == sha(path)
        modset[path.name] = sha(path)
    target = run / 'isolated-smoke-world'; target.mkdir(); copied = []
    for path in files(source):
        if path.name == 'session.lock':
            continue
        relative = path.relative_to(source)
        destination = target / relative; destination.parent.mkdir(parents=True, exist_ok=True)
        before = sha(path); shutil.copyfile(path, destination)
        assert sha(path) == sha(destination) == before
        copied.append({'path': relative.as_posix(), 'bytes': path.stat().st_size, 'sha256': before})
    assert fingerprint(source) == source_before, 'Source file set/content changed during copy'
    assert sha(launch_report_path) == launch_report_sha, 'Stopped author report changed during copy'
    shutil.copyfile(eula, run / 'eula.txt')
    (run / 'server.properties').write_text('server-ip=127.0.0.1\nserver-port=25596\nonline-mode=false\nmax-players=0\n'
        'enable-command-block=false\nspawn-protection=0\nview-distance=2\nsimulation-distance=2\n'
        'level-name=isolated-smoke-world\n', encoding='utf8')
    record = {'status': 'PASS_PREPARED_NATIVE_NOT_RUN', 'artifact_sha256': digest,
        'artifact': str(production), 'run_directory': str(run), 'extraMods': [], 'world_startup': 'NOT_RUN',
        'sourceWorld': str(source), 'sourceWorldUnchanged': True, 'copiedFiles': copied,
        'bootstrapRuntimeFiles': bootstrap, 'modFiles': modset, 'acceptedEulaSha256': sha(eula),
        'acceptedAuthorLaunchReportSha256': launch_report_sha, 'sourceCompleteFingerprint': source_before,
        'originalCityOpened': False, 'galleryCreated': False, 'nativeJavaRun': 'NOT_RUN'}
    if not args.prepare_only:
        assert args.java_home, 'Java 17 must be explicitly configured'
        java = args.java_home / 'bin' / ('java.exe' if os.name == 'nt' else 'java')
        version = subprocess.run([str(java), '-version'], capture_output=True, text=True, check=True).stderr
        assert 'version "17.' in version or 'openjdk 17.' in version
        launch = args.server_launcher.resolve()
        command = [str(java), f'-Xmx{args.heap_gb}G', '-jar', str(launch), 'nogui',
                   '--universe', str(run), '--world', target.name]
        log_path = run / 'production-reopen-console.log'; start = time.monotonic(); started = False
        with log_path.open('wb') as log:
            process = subprocess.Popen(command, cwd=run, stdin=subprocess.PIPE, stdout=log, stderr=subprocess.STDOUT, text=True)
            try:
                while process.poll() is None and time.monotonic() - start < args.startup_timeout:
                    if 'Done (' in log_path.read_text(encoding='utf8', errors='replace'):
                        started = True; break
                    time.sleep(1)
                if started and args.hold_seconds:
                    time.sleep(args.hold_seconds)
                if process.poll() is None:
                    process.stdin.write('save-all flush\nstop\n'); process.stdin.flush()
                    process.wait(timeout=args.shutdown_timeout)
            finally:
                if process.poll() is None:
                    try:
                        process.stdin.write('stop\n'); process.stdin.flush(); process.wait(timeout=args.shutdown_timeout)
                    except Exception:
                        process.terminate(); process.wait(timeout=30)
        text = log_path.read_text(encoding='utf8', errors='replace')
        errors, optional = classify_log(text)
        assert fingerprint(source) == source_before, 'Source file set/content changed during production reopen'
        assert sha(production) == digest and sha(launch_report_path) == record['acceptedAuthorLaunchReportSha256'], 'Accepted author/JAR binding changed'
        record.update(status='PASS' if started and process.returncode == 0 and not errors else 'FAIL',
            world_startup='PASS' if started else 'FAIL', exit_code=process.returncode, javaVersion=version.strip(),
            consoleLogSha256=sha(log_path), errors=errors, optionalReachWarnings=optional, nativeJavaRun='RUN',
            elapsedSeconds=round(time.monotonic() - start, 2))
    report.parent.mkdir(parents=True, exist_ok=True)
    report.write_text(json.dumps(record, ensure_ascii=False, indent=2) + '\n', encoding='utf8')
    print(json.dumps({key: record[key] for key in ('status', 'artifact_sha256', 'run_directory', 'world_startup')}))
    assert args.prepare_only or record['status'] == 'PASS', 'Production reopen failed; retain exact console report'


if __name__ == '__main__':
    main()
