"""Preserve exact production-build evidence and audit release source selection.

No compilation, JVM, Minecraft, world, report acceptance or release packaging edits.
"""
import hashlib
import json
from pathlib import Path
import shutil
import stat
import subprocess
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
REPO = ROOT.parent
DOCS = ROOT/'docs/v12.3-outline-icons'
BUILD = DOCS/'build'
SOURCES = [
    ROOT/'build/remaining-transfer/v123-main1/result.json',
    ROOT/'build/remaining-transfer/v123-main1/gradle.log',
    ROOT/'build/test-results/gametest/TEST-v123-main1.xml',
]
sha = lambda path: hashlib.sha256(path.read_bytes()).hexdigest()


def ordinary(path):
    for value in (path, *path.parents):
        info = value.lstat()
        assert not stat.S_ISLNK(info.st_mode) and not getattr(info, 'st_file_attributes', 0) & 0x400, value


def main():
    BUILD.mkdir(parents=True, exist_ok=True)
    rows = []
    for source in SOURCES:
        ordinary(source)
        target = BUILD/source.name
        digest = sha(source)
        if target.exists():
            ordinary(target)
            assert sha(target) == digest, 'Refusing to replace older different evidence: '+str(target)
        else:
            shutil.copyfile(source, target)
        assert sha(source) == sha(target) == digest
        rows.append(dict(source=source.relative_to(ROOT).as_posix(), copy=target.relative_to(ROOT).as_posix(),
                         bytes=target.stat().st_size, sha256=digest, byteVerification='PASS'))
    result = json.loads(SOURCES[0].read_text(encoding='utf8'))
    xml = ET.parse(SOURCES[2]).getroot()
    testcase_count = len(xml.findall('.//testcase'))
    assert result['status'] == 'PASS' and result['exitCode'] == 0
    assert testcase_count == 63 and not xml.findall('.//failure') and not xml.findall('.//error')
    manifest = dict(schema='dreamwalker-v123-production-build-evidence-v1', status='PASS',
                    productionBuildStatus=result['status'], nativeGameTestCount=testcase_count,
                    nativeGameTestFailures=0, nativeGameTestErrors=0, files=rows,
                    scope='Exact copies of the production build result/log and native GameTest XML only; no QA addon evidence is included here.')
    (BUILD/'build-evidence-manifest.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2)+'\n', encoding='utf8')
    # Reproduce the existing packaging script's selection without changing it.
    raw = subprocess.check_output(['git', 'ls-files', '-z', '-co', '--exclude-standard', '--', 'bloodborne complete/'], cwd=REPO)
    selected = {REPO/path for path in raw.decode('utf8').split('\0') if path and not path.startswith('bloodborne complete/build/')}
    selected.update(path for path in DOCS.rglob('*') if path.is_file())
    relpaths = {path.relative_to(ROOT).as_posix() for path in selected}
    unwanted = sorted(path for path in relpaths if path.startswith(('build/', '.gradle/', '.git/', 'out/', 'run/')) or '/__pycache__/' in path or path.endswith(('.pyc', '.class')))
    required = {
        'build.gradle', 'gradle.properties', 'settings.gradle', 'gradlew', 'gradlew.bat',
        'gradle/wrapper/gradle-wrapper.jar', 'gradle/wrapper/gradle-wrapper.properties',
        'src/architecture/java/dev/dreamwalker/bloodbornedw/composite/ImportedSelectionShapes.java',
        'src/architecture/java/dev/dreamwalker/bloodbornedw/composite/CompositeRuntime.java',
        'src/rp/java/dev/dreamwalker/bloodbornerp/client/RpPlacementItemRenderer.java',
        'src/rp/java/dev/dreamwalker/bloodbornerp/client/BloodborneRpClient.java',
        'src/architecture/resources/bloodborne_dw/remaining-catalogue.json',
        'src/architecture/resources/fabric.mod.json', 'docs/COLLISION_IMPORT_HANDOFF.md',
    }
    missing = sorted(required-relpaths)
    inventory = dict(schema='dreamwalker-v123-source-selection-review-v1', status='PASS' if not unwanted and not missing else 'FAIL',
                     sourceSelectionMethod='Exact current package_v123_release.py git cached+untracked nonignored files, excluding bloodborne complete/build/, plus explicit new revision docs.',
                     selectedFiles=len(relpaths), selectedBytes=sum(path.stat().st_size for path in selected),
                     requiredSourceAndWrapperFilesChecked=len(required), missingRequiredFiles=missing,
                     unwantedBuildCacheCompiledEntries=unwanted,
                     scope='Read-only review before packaging. Actual final ZIP/JAR validation must run separately after creation.')
    (DOCS/'source-selection-review.json').write_text(json.dumps(inventory, ensure_ascii=False, indent=2)+'\n', encoding='utf8')
    print(json.dumps({'build':manifest['status'], 'gameTests':testcase_count, 'sourceSelection':inventory}, ensure_ascii=False))
    if inventory['status'] != 'PASS':
        raise SystemExit(1)


if __name__ == '__main__':
    main()
