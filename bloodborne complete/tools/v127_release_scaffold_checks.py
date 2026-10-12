"""Root/subagent controlled Python-only release safety smoke check; no game/publication."""
import ast
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import zipfile
from package_v127_release import ROOT, archive, sha, validate


def main():
    names = ['package_v127_release.py', 'publish_v127_release.py',
             'build_v127_conversion_addon.py', 'run_v127_full_conversion.py']
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
    record = {'status': 'PASS_PYTHON_SCAFFOLD_SAFETY', 'pythonScriptsParsed': names,
              'streamedArchiveBytes': output.stat().st_size, 'entryCRCAndSHA': 'PASS',
              'missingAcceptanceRefused': True, 'javaOrMinecraftLaunched': False,
              'worldConverted': False, 'githubMutationAttempted': False,
              'nativeConversionRunnerCompileAndFixture': 'NOT_RUN_BY_THIS_CHECK'}
    (ROOT / 'build/v127-release-scaffold-checks.json').write_text(json.dumps(record, indent=2) + '\n', encoding='utf8')
    print(json.dumps(record))


if __name__ == '__main__':
    main()
