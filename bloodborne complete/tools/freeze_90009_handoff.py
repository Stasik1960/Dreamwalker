"""Freeze already observed evidence for handoff; does not launch Minecraft or tests."""
from pathlib import Path
import hashlib
import json
import shutil
import zipfile

ROOT = Path(__file__).resolve().parents[1]
PACKAGE = ROOT.parent / 'releases/Dreamwalker-BB/90009-fix.1'
OLD = '5066ed3b334b4103120932f9be8810d2548230ddbd94984e62780311fbb1793a'
CURRENT = '61b206c7bd2d253fbf1387b8d95c7e3663f192759ce4673c808798246e90a4fa'


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def read(path):
    return json.loads(path.read_text(encoding='utf8'))


def save(path, value):
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2)+'\n', encoding='utf8')


def main():
    manifest = read(PACKAGE / 'manifest.json')
    target = PACKAGE / manifest['jar']['file']
    source = ROOT / 'build/libs' / target.name
    if sha(target) != OLD or manifest['jar']['sha256'] != OLD or sha(source) != CURRENT:
        raise ValueError('Only the exact old owned candidate and current built JAR are accepted')
    with zipfile.ZipFile(target) as before, zipfile.ZipFile(source) as after:
        if set(before.namelist()) != set(after.namelist()):
            raise ValueError('Unexpected changed JAR inventory')
        changed = sorted(name for name in before.namelist() if before.read(name) != after.read(name))
    expected = sorted('dev/dreamwalker/bloodbornedw/tool/'+name+'.class' for name in ('BuilderControlsScreen', 'BuilderScreen', 'BuilderScreen$1'))
    if changed != expected:
        raise ValueError('Changes extend beyond the two GUI sources and their generated inner class: '+repr(changed))
    extraction = read(ROOT / 'build/reports/gallery-shipped-archive-extraction-01.json')
    native = read(ROOT / 'build/reports/final-focus-archive-native60-01.json')
    disk = read(ROOT / 'build/reports/final-focus-archive-saved-disk-01.json')
    world = PACKAGE / manifest['world']['file']
    if sha(world) != manifest['world']['sha256'] or extraction['worldZipSha256'] != sha(world):
        raise ValueError('Pristine gallery ZIP differs')
    if native['status'] != 'PASS' or native['exit_code'] != 0 or native['artifact_sha256'] != CURRENT:
        raise ValueError('Missing already completed ordinary current-JAR reopen')
    if native['derived_world_copy']['source'] != extraction['fixture'] or not native['derived_world_copy']['source_unchanged_after_run']:
        raise ValueError('Native source is not the exact ZIP extraction')
    if disk['status'] != 'PASS_SAVED_GALLERY_DISK' or disk['verifiedExhibits'] != 121 or Path(disk['world']).resolve() != Path(native['run_directory']).resolve()/'isolated-smoke-world':
        raise ValueError('Disk evidence does not belong to this ordinary reopen')
    copies = {
        'HANDOFF.md':'HANDOFF.md',
        'docs/REVIEW_90009_FIX_REPORT.md':'Отчёт_исправлений.md',
        'docs/REVIEW_90009_VISIBLE_CHECKS.md':'План_видимых_проверок.md',
        'docs/REVIEW_V10_MENU_GUIDE.md':'Управление_90009.md',
        'reports/REVIEW_90009_NATIVE_VISIBLE_20261010.json':'evidence/REVIEW_90009_NATIVE_VISIBLE_20261010.json',
        'reports/REVIEW_90009_TEST_MATRIX.csv':'evidence/REVIEW_90009_TEST_MATRIX.csv',
        'build/reports/final-focus-archive-native60-01.json':'evidence/final-focus-archive-native60-01.json',
        'build/reports/final-focus-archive-saved-disk-01.json':'evidence/final-focus-archive-saved-disk-01.json',
        'build/focus-preserving-build.log':'evidence/focus-preserving-build.log',
    }
    for profile in ('runtime-client-shipped-archive-visible-1920-scale4-prepared', 'runtime-client-controls-footer-visible-1920-scale4-01', 'runtime-client-focus-dogs-visible-1920-scale4-01'):
        run = ROOT / 'build' / profile
        for path in (run/'screenshots').glob('*.png'):
            copies[path.relative_to(ROOT).as_posix()] = 'screenshots/'+path.name
        if (run/'logs/latest.log').is_file():
            copies[(run/'logs/latest.log').relative_to(ROOT).as_posix()] = 'evidence/'+profile+'-latest.log'
    run = ROOT / 'build/runtime-client-focus-dogs-visible-1920-scale4-01'
    for folder in ('diagnostics','dreamwalker-diagnostics'):
        for path in (run/folder).glob('*.zip'):
            with zipfile.ZipFile(path) as archive:
                if archive.testzip():
                    raise ValueError('Existing diagnostic evidence CRC differs')
            copies[path.relative_to(ROOT).as_posix()] = 'evidence/'+path.name
    for path in (Path(native['run_directory'])/'dreamwalker-diagnostics').glob('*.zip'):
        copies[path.relative_to(ROOT).as_posix()] = 'evidence/'+path.name
    for src, dst in copies.items():
        destination = PACKAGE / dst
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(ROOT/src, destination)
        if sha(ROOT/src) != sha(destination):
            raise ValueError('Evidence copy differs')
    shutil.copyfile(source, target)
    manifest['jar'].update(sha256=CURRENT, bytes=target.stat().st_size)
    manifest['world']['archiveExtractedOrdinaryReopen'] = dict(status='PASS', normalExitCode=0, verifiedSavedExhibits=121, jarSha256=CURRENT, worldZipSha256=sha(world))
    manifest['nativeGameTests']['artifactSha256'] = OLD
    manifest['nativeGameTests']['scope'] = '148 distinct internal server checks on prior SHA5066; only two client GUI sources and one generated inner class changed afterward; not a new full run on SHA61'
    manifest['publication'] = 'PREPARED_FOR_USER_REQUESTED_GITHUB_HANDOFF'
    manifest['visibleTests'] = 'PARTIAL; user ended testing today; next person must finish per HANDOFF.md'
    manifest['guiOnlyChangedJarEntries'] = changed
    manifest['currentServerReopenReport'] = 'evidence/final-focus-archive-native60-01.json'
    provenance = read(PACKAGE/'evidence/production-source-provenance.json')
    inputs = []
    for relative in ('src/architecture/java','src/architecture/resources','src/rp/java','src/rp/resources','gradle/wrapper'):
        inputs.extend(path for path in (ROOT/relative).rglob('*') if path.is_file())
    inputs.extend(ROOT/name for name in ('build.gradle','settings.gradle','gradle.properties','gradlew','gradlew.bat'))
    rows = [dict(path=path.relative_to(ROOT).as_posix(), sha256=sha(path)) for path in sorted(inputs, key=lambda path:path.relative_to(ROOT).as_posix())]
    fingerprint = hashlib.sha256(json.dumps(rows, ensure_ascii=False, sort_keys=True, separators=(',',':')).encode('utf8')).hexdigest()
    provenance.update(productionInputs=rows, productionInputFingerprint=fingerprint, productionJarSha256=CURRENT, publication=manifest['publication'], clientScope=manifest['visibleTests'])
    provenance['nativeGameTests']['artifactSha256'] = OLD
    provenance['guiOnlyChangedJarEntries'] = changed
    save(PACKAGE/'evidence/production-source-provenance.json', provenance)
    manifest['productionInputFingerprint'] = fingerprint
    manifest['evidenceFiles'] = [dict(file=path.relative_to(PACKAGE).as_posix(),sha256=sha(path)) for path in sorted(PACKAGE.rglob('*')) if path.is_file() and path.name != 'manifest.json']
    save(PACKAGE/'manifest.json',manifest)
    print(json.dumps(dict(status='FROZEN_PARTIAL_HANDOFF', jarSha256=sha(target), worldZipSha256=sha(world), changedJarEntries=changed, preservedFiles=len(manifest['evidenceFiles']))))


if __name__ == '__main__':
    main()
