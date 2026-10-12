"""Root/subagent controlled Python-only release safety smoke check; no game/publication."""
import ast
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import zipfile
from package_v127_release import ROOT, archive, sha, validate
from run_v127_full_conversion import IMPLEMENTED_STYLE, validate_full_plan


def main():
    names = ['package_v127_release.py', 'publish_v127_release.py',
             'build_v127_conversion_addon.py', 'run_v127_full_conversion.py',
             'validate_v127_conversion_fixture.py', 'prepare_v127_server_bootstrap.py',
             'prepare_v127_conversion_fixture.py', 'run_v127_conversion_reopen.py',
             'record_v127_acceptance.py']
    for name in names:
        ast.parse((ROOT / 'tools' / name).read_text(encoding='utf8'), filename=name)
        subprocess.run([sys.executable, str(ROOT / 'tools' / name), '--help'], cwd=ROOT,
                       stdout=subprocess.DEVNULL, stderr=subprocess.PIPE, check=True)
    directory = Path(tempfile.mkdtemp(prefix='release-scaffold-', dir=ROOT / 'build'))
    first = directory / 'payload.bin'
    with first.open('wb') as stream:
        for _ in range(40):
            stream.write(bytes(range(256)) * 256)
    second = directory / 'readme.txt'
    second.write_text('Тест потоковой упаковки\n', encoding='utf8')
    output = directory / 'example.zip'
    result = archive(output, [(first, 'source/payload.bin'), (second, 'source/readme.txt')])
    with zipfile.ZipFile(output) as zipped:
        rows = json.loads(zipped.read('ARCHIVE-CONTENTS.json'))['files']
    assert len(rows) == 2 and rows[0]['sha256'] == sha(first) and result['entrySHA256'] == 'PASS'
    refused = False
    try:
        validate(directory / 'missing-acceptance.json', directory)
    except AssertionError:
        refused = True
    assert refused, 'Packaging must reject missing acceptance before creating a release'
    world, plan = directory / 'world', directory / 'runtime'
    world.mkdir(); plan.mkdir()
    prepared = {'status': 'PASS_DERIVED_COPY_PREPARED'}
    marker = world / 'dw-derived-copy.json'
    manifest_path = plan / 'manifest.json'
    source, policy = directory / 'source.sqlite', directory / 'policy.json'
    source.write_bytes(b'fixture SQLite hash input only; not an executable source plan')
    policy.write_text(json.dumps({'status': 'APPROVED_PENDING_IMPLEMENTATION'}), encoding='utf8')
    manifest = {'sourceStylePolicy': IMPLEMENTED_STYLE, 'sourcePlanSha256': sha(source),
                'sourceStylePolicySha256': sha(policy)}
    manifest_path.write_text(json.dumps(manifest), encoding='utf8')
    prepared.update(sourcePlanSha256=sha(source), runtimeManifestSha256=sha(manifest_path))
    marker.write_text(json.dumps(prepared), encoding='utf8')
    full_refused = False
    try:
        validate_full_plan(world, plan, manifest, source, policy, False)
    except AssertionError:
        full_refused = True
    assert full_refused, 'A pre-existing runtime bundle must fail when preservation is pending'
    policy.write_text(json.dumps({'status': IMPLEMENTED_STYLE}), encoding='utf8')
    manifest['sourceStylePolicySha256'] = sha(policy)
    manifest_path.write_text(json.dumps(manifest), encoding='utf8')
    prepared['runtimeManifestSha256'] = sha(manifest_path)
    marker.write_text(json.dumps(prepared), encoding='utf8')
    validate_full_plan(world, plan, manifest, source, policy, False)
    source.write_bytes(b'changed source plan')
    changed_refused = False
    try:
        validate_full_plan(world, plan, manifest, source, policy, False)
    except AssertionError:
        changed_refused = True
    assert changed_refused, 'A changed source SQLite must fail before launch'
    assert validate_full_plan(world, plan, {'fixtureOnly': True}, source, policy, True) == {}
    record = {'status': 'PASS_PYTHON_SCAFFOLD_SAFETY', 'pythonScriptsParsed': names,
              'streamedArchiveBytes': output.stat().st_size, 'entryCRCAndSHA': 'PASS',
              'missingAcceptanceRefused': True, 'javaOrMinecraftLaunched': False,
              'pendingPreservationPolicyRefused': full_refused, 'changedSourcePlanRefused': changed_refused,
              'boundedFixtureOnlyException': 'PASS',
              'worldConverted': False, 'githubMutationAttempted': False,
              'nativeConversionRunnerCompileAndFixture': 'NOT_RUN_BY_THIS_CHECK'}
    (ROOT / 'build/v127-release-scaffold-checks.json').write_text(json.dumps(record, indent=2) + '\n', encoding='utf8')
    print(json.dumps(record))


if __name__ == '__main__':
    main()
