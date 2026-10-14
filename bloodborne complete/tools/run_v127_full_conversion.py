"""Launch a pinned, separately authorized conversion on an offline derived copy.

This never extracts/writes the original ZIP and never creates a gallery. Supply
the official Minecraft 1.20.1/Fabric 0.16.10 server launcher and pinned dependencies.
"""
import argparse
import json
import os
import re
from pathlib import Path
import shutil
import subprocess
import time
import zipfile
from package_v127_release import ROOT, files, ordinary, read, sha, SOURCE_HASHES
from prepare_v127_server_bootstrap import stage_bootstrap

IMPLEMENTED_STYLE = 'APPROVED_EXACT_VISIBLE_WITH_ENCLOSED_VANILLA'
IMPLEMENTED_STYLES = {IMPLEMENTED_STYLE, 'APPROVED_EXACT_VISIBLE_AND_HIDDEN_COMPACT'}


def validate_full_plan(world, plan, manifest, source_plan, style_policy, fixture_only):
    """Read-only gate before any server/profile writes; old exports fail closed."""
    prepared = read(world / 'dw-derived-copy.json')
    if fixture_only:
        assert manifest.get('fixtureOnly') is True, 'Fixture mode requires an explicitly bounded fixture plan'
        return {}
    assert not manifest.get('fixtureOnly', False), 'A fixture cannot stand in for the original world'
    assert not str(manifest.get('runtimeExecution', '')).startswith(('BLOCKED', 'PENDING')), 'Full plan execution is still blocked'
    source_plan, style_policy = source_plan.resolve(), style_policy.resolve()
    assert source_plan.is_relative_to(ROOT / 'build') and style_policy.is_relative_to(ROOT)
    assert ordinary(source_plan) and ordinary(style_policy)
    policy = read(style_policy)
    assert policy.get('status') in IMPLEMENTED_STYLES, 'Approved source-style preservation has not been fully implemented'
    assert manifest.get('sourceStylePolicy') == policy['status']
    assert manifest.get('sourceStylePolicySha256') == sha(style_policy), 'Stale source-style policy binding'
    assert manifest.get('sourcePlanSha256') == sha(source_plan), 'Stale source SQLite plan binding'
    assert prepared.get('sourcePlanSha256') == manifest['sourcePlanSha256'], 'Derived world was staged from another plan'
    assert prepared.get('runtimeManifestSha256') == sha(plan / 'manifest.json'), 'Derived world was staged against another runtime bundle'
    return {'sourcePlanPath': str(source_plan), 'sourcePlanSha256': manifest['sourcePlanSha256'],
            'sourceStylePolicyPath': str(style_policy), 'sourceStylePolicySha256': manifest['sourceStylePolicySha256'],
            'sourceStylePolicy': policy['status']}


def metadata(path):
    with zipfile.ZipFile(path) as archive:
        return json.loads(archive.read('fabric.mod.json'))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--derived-world', type=Path, required=True)
    parser.add_argument('--runtime-plan', type=Path, required=True)
    parser.add_argument('--runtime-directory', type=Path, required=True)
    parser.add_argument('--source-plan', type=Path, default=ROOT / 'build/v127-full-conversion-first.sqlite')
    parser.add_argument('--source-style-policy', type=Path, default=ROOT / 'docs/v12.7/conversion/source-style-policy.json')
    parser.add_argument('--production-jar', type=Path, required=True)
    parser.add_argument('--author-jar', type=Path, required=True)
    parser.add_argument('--fabric-api', type=Path, required=True)
    parser.add_argument('--geckolib', type=Path, required=True)
    parser.add_argument('--server-launcher', type=Path, required=True)
    parser.add_argument('--server-bootstrap', type=Path, help='Verified bundle prepared by prepare_v127_server_bootstrap.py')
    parser.add_argument('--accepted-eula-file', type=Path, required=True, help='Existing eula=true evidence; copied, never invented')
    java_home = os.environ.get('JAVA_HOME17') or os.environ.get('JAVA_HOME')
    parser.add_argument('--java-home', type=Path, default=Path(java_home) if java_home else None)
    parser.add_argument('--heap-gb', type=int, default=4)
    parser.add_argument('--timeout-hours', type=float, default=24)
    parser.add_argument('--authorize-full-conversion', action='store_true')
    parser.add_argument('--fixture-only', action='store_true')
    parser.add_argument('--resume', action='store_true')
    args = parser.parse_args()
    assert args.authorize_full_conversion or args.fixture_only, 'Full conversion remains disabled until explicitly requested'
    assert not (args.authorize_full_conversion and args.fixture_only)
    assert args.java_home and 2 <= args.heap_gb <= 12 and 0 < args.timeout_hours <= 72
    eula = args.accepted_eula_file.resolve()
    assert ordinary(args.accepted_eula_file) and eula.is_file()
    assert re.search(r'(?m)^\s*eula\s*=\s*true\s*$', eula.read_text(encoding='utf-8-sig')), 'Existing accepted EULA evidence required'
    java = args.java_home / 'bin' / ('java.exe' if os.name == 'nt' else 'java')
    version = subprocess.run([str(java), '-version'], capture_output=True, text=True, check=True).stderr
    assert 'version "17.' in version or 'openjdk 17.' in version, 'Java 17 is required'
    assert all(ordinary(path) for path in (args.derived_world, args.runtime_plan, args.runtime_directory)), 'Reparsed conversion destination/input rejected'
    world, plan, run = args.derived_world.resolve(), args.runtime_plan.resolve(), args.runtime_directory.resolve()
    assert world.is_relative_to(ROOT / 'build') and plan.is_relative_to(ROOT / 'build') and run.is_relative_to(ROOT / 'build')
    assert ordinary(world) and (world / 'level.dat').is_file() and world != run
    derived = read(world / 'dw-derived-copy.json')
    assert derived['status'] == 'PASS_DERIVED_COPY_PREPARED' and derived['sourceHashes'] == SOURCE_HASHES
    manifest = read(plan / 'manifest.json')
    assert manifest['status'] == 'PASS_COMPLETE_SOURCE_PLAN' and manifest['knownVisibleGaps'] == 0
    assert manifest['sourceHashes'] == derived['sourceHashes']
    for name, digest in manifest['runtimeFiles'].items():
        assert (plan / name).parent.resolve() == plan and sha(plan / name) == digest
    full_bindings = validate_full_plan(world, plan, manifest, args.source_plan, args.source_style_policy, args.fixture_only)
    sources = [args.production_jar.resolve(), args.author_jar.resolve(), args.fabric_api.resolve(), args.geckolib.resolve()]
    expected = ['bloodborne_dw', 'dreamwalker_full_conversion_author', 'fabric-api', 'geckolib']
    for source, mod_id in zip(sources, expected):
        assert ordinary(source) and metadata(source)['id'] == mod_id, mod_id
    with zipfile.ZipFile(sources[1]) as addon:
        assert json.loads(addon.read('dw-conversion-binding.json'))['productionJarSha256'] == sha(sources[0])
    assert not run.exists() or args.resume, 'Choose a fresh server runtime or explicitly resume the same operation'
    run.mkdir(parents=True, exist_ok=True)
    bootstrap_files = stage_bootstrap(args.server_bootstrap, run, args.server_launcher) if args.server_bootstrap else {}
    mods = run / 'mods'
    assert ordinary(mods), 'Reparsed isolated mod directory rejected'
    mods.mkdir(exist_ok=True)
    intended = {source.name: sha(source) for source in sources}
    existing = {path.name: sha(path) for path in mods.glob('*.jar')}
    assert not existing or existing == intended, 'Unexpected or changed mod in the isolated conversion server'
    for source in sources:
        destination = mods / source.name
        if not destination.exists():
            shutil.copyfile(source, destination)
        assert sha(destination) == intended[source.name]
    marker = {'schemaVersion': 1, 'mode': 'FIXTURE_NATIVE_TEST' if args.fixture_only else 'FULL_ORIGINAL_AFTER_USER_COMMAND',
              'explicitConversionAuthorized': True, 'offlinePreparationStatus': derived['status'],
              'operationId': derived['operationId'], 'targetWorldPath': str(world), 'runtimePlanPath': str(plan),
              'runtimeManifestSha256': sha(plan / 'manifest.json'), 'productionJarSha256': sha(sources[0])}
    marker.update(full_bindings)
    input_path = run / 'dw-full-conversion-input.json'
    if input_path.is_file():
        assert read(input_path) == marker, 'Resume marker belongs to another operation or JAR'
    input_path.write_text(json.dumps(marker, indent=2) + '\n', encoding='utf8')
    if (run / 'eula.txt').exists():
        assert sha(run / 'eula.txt') == sha(eula), 'Resume EULA evidence changed'
    else:
        shutil.copyfile(eula, run / 'eula.txt')
    (run / 'server.properties').write_text('server-ip=127.0.0.1\nserver-port=25595\nonline-mode=false\nmax-players=0\n'
                                          'enable-command-block=false\nspawn-protection=0\nview-distance=2\nsimulation-distance=2\n'
                                          f'level-name={world.name}\n', encoding='utf8')
    launch = args.server_launcher.resolve()
    assert ordinary(launch) and launch.is_file()
    command = [str(java), f'-Xmx{args.heap_gb}G', f'-Ddw.conversion.productionJar={sources[0]}',
               '-jar', str(launch), 'nogui', '--universe', str(world.parent), '--world', world.name]
    record = {'status': 'RUNNING', 'mode': marker['mode'], 'productionJarSha256': marker['productionJarSha256'],
              'runtimeManifestSha256': marker['runtimeManifestSha256'], 'derivedWorld': str(world),
              'originalArchivesWritten': False, 'galleryCreated': False, 'javaVersion': version.strip(),
              'modFiles': intended, 'serverLauncherSha256': sha(launch), 'bootstrapRuntimeFiles': bootstrap_files}
    record['acceptedEulaSha256'] = sha(eula)
    start = time.monotonic()
    with (run / 'conversion-console.log').open('wb') as log:
        process = subprocess.Popen(command, cwd=run, stdin=subprocess.PIPE, stdout=log, stderr=subprocess.STDOUT, text=True)
        next_update = start
        try:
            while process.poll() is None:
                now = time.monotonic()
                if now - start > args.timeout_hours * 3600:
                    (world / 'dw-conversion-stop.request').write_text('Bounded launcher timeout\n', encoding='utf8')
                    process.stdin.write('stop\n'); process.stdin.flush()
                    process.wait(timeout=180)
                    break
                if now >= next_update:
                    result_file = world / 'dw-conversion-result.json'
                    progress = read(result_file) if result_file.is_file() else {'status': 'STARTING'}
                    print(json.dumps({'status': progress['status'], 'phase': progress.get('phase'),
                                      'line': progress.get('line'), 'committed': progress.get('committed'),
                                      'elapsedSeconds': round(now - start)}, ensure_ascii=False), flush=True)
                    next_update = now + 30
                time.sleep(1)
        except BaseException:
            (world / 'dw-conversion-stop.request').write_text('Launcher interrupted\n', encoding='utf8')
            if process.poll() is None:
                process.stdin.write('stop\n'); process.stdin.flush()
                process.wait(timeout=180)
            raise
    result_file = world / 'dw-conversion-result.json'
    outcome = read(result_file) if result_file.is_file() else {'status': 'FAIL_NO_RUNTIME_REPORT'}
    record.update(exitCode=process.returncode, elapsedSeconds=round(time.monotonic() - start, 2), runtimeResult=outcome,
                  status='PASS_RUNTIME_CONVERTED_REOPEN_REQUIRED' if process.returncode == 0 and outcome['status'] == 'PASS_RUNTIME_CONVERTED_REOPEN_REQUIRED' else 'FAIL_OR_PAUSED_REVIEW_REQUIRED')
    (run / 'launch-report.json').write_text(json.dumps(record, indent=2, ensure_ascii=False) + '\n', encoding='utf8')
    print(json.dumps({'status': record['status'], 'derivedWorld': str(world)}))
    assert record['status'].startswith('PASS'), 'Conversion failed/paused; retain source row report and resume only after review'


if __name__ == '__main__':
    main()
