"""Refresh review documents and attach exact shipped-archive reopen proof, without changing JAR/ZIP."""
from __future__ import annotations
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[1]


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def read(path):
    return json.loads(path.read_text(encoding='utf8'))


def test_names(path):
    root = ET.parse(path).getroot()
    if any(list(root.iter(name)) for name in ('failure', 'error', 'skipped')):
        raise ValueError('Failed/skipped tests in ' + str(path))
    return {node.attrib['name'] for node in root.iter('testcase')}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--package', type=Path, required=True)
    parser.add_argument('--extraction-report', type=Path, required=True)
    parser.add_argument('--native-report', type=Path, required=True)
    parser.add_argument('--disk-report', type=Path, required=True)
    parser.add_argument('--java-home', type=Path, required=True)
    parser.add_argument('--evidence', type=Path, action='append', default=[])
    parser.add_argument('--diagnostics-run-report', type=Path)
    args = parser.parse_args()
    package = args.package.resolve()
    if not package.is_relative_to((ROOT.parent / 'releases').resolve()):
        raise ValueError('Only the explicitly packaged worktree release is accepted')
    manifest = read(package / 'manifest.json')
    jar, world = manifest['jar'], manifest['world']
    if sha(package / jar['file']) != jar['sha256'] or sha(package / world['file']) != world['sha256']:
        raise ValueError('Do not refresh a modified JAR or world archive')
    extraction, native, disk = map(read, (args.extraction_report, args.native_report, args.disk_report))
    if extraction['status'] != 'PASS_EXACT_SHIPPED_ARCHIVE_EXTRACTION' or extraction['worldZipSha256'] != world['sha256']:
        raise ValueError('Exact shipped ZIP extraction proof is required')
    if native['status'] != 'PASS' or native['exit_code'] != 0 or native['artifact_sha256'] != jar['sha256']:
        raise ValueError('Ordinary shipped JAR must save the extracted world normally')
    if native['derived_world_copy']['source'] != extraction['fixture'] or not native['derived_world_copy']['source_unchanged_after_run']:
        raise ValueError('Native reopen must copy the exact unchanged ZIP extraction')
    if disk['status'] != 'PASS_SAVED_GALLERY_DISK' or Path(disk['world']).resolve() != Path(native['run_directory']).resolve() / 'isolated-smoke-world' or disk['verifiedExhibits'] != 121:
        raise ValueError('Saved121 UUID proof must belong to that native reopen')
    full = test_names(package / 'evidence/TEST-90009-full-07.xml')
    focused = test_names(package / 'evidence/TEST-catalogue.xml')
    if len(full) != 147 or len(focused) != 14 or len(full | focused) != 148:
        raise ValueError('Full147 and focused14 reports must contain148 distinct checks')
    undo = subprocess.run([str(args.java_home / 'bin/java.exe'), '-cp', str(ROOT / 'build/classes/java/main')+';'+str(ROOT / 'build/classes/java/test'),
                           'dev.dreamwalker.bloodbornedw.tool.BuilderUndoChecks'], capture_output=True, text=True, check=True)
    if undo.stdout.strip() != 'BuilderUndoChecks: passed':
        raise ValueError('Unexpected pure undo check output')
    copies = [(ROOT / 'docs/REVIEW_V10_MENU_GUIDE.md', package / 'Управление_90009.md'),
              (ROOT / 'docs/REVIEW_90009_FIX_REPORT.md', package / 'Отчёт_исправлений.md'),
              (ROOT / 'docs/REVIEW_90009_VISIBLE_CHECKS.md', package / 'План_видимых_проверок.md'),
              (ROOT / 'reports/REVIEW_90009_TEST_MATRIX.csv', package / 'evidence/REVIEW_90009_TEST_MATRIX.csv'),
              (ROOT / 'reports/REVIEW_90009_CLIENT_INPUT_OBSERVATIONS.json', package / 'evidence/REVIEW_90009_CLIENT_INPUT_OBSERVATIONS.json')]
    diagnostic_summary = None
    if args.diagnostics_run_report:
        recording = read(args.diagnostics_run_report)
        run = Path(recording['run_directory']).resolve()
        if not run.is_relative_to((ROOT / 'build').resolve()) or recording['status'] != 'PASS' or recording['exit_code'] != 0 or recording['artifact_sha256'] != jar['sha256']:
            raise ValueError('Ordinary diagnostics must belong to this exact shipped JAR')
        archives = list((run / 'dreamwalker-diagnostics').glob('*.zip'))
        if len(archives) != 1:
            raise ValueError('Expected one actual diagnostic ZIP')
        with zipfile.ZipFile(archives[0]) as archive:
            if archive.testzip():
                raise ValueError('Diagnostic ZIP CRC differs')
            session = json.loads(archive.read('session.json'))
            environment = json.loads(archive.read('environment.json'))
            marks = json.loads(archive.read('marks.json'))
            errors = json.loads(archive.read('errors.json'))
            if session['enabled'] or session['secondsRequested'] != 60 or session['stopReason'] != 'AUTO_DURATION_EXPIRED' or not 60e9 <= session['elapsedNs'] < 75e9:
                raise ValueError('Default60-second recording did not really expire normally')
            if environment['productionArtifactSha256'] != jar['sha256'] or errors or not any(row['note'] == 'shipped_archive_native60' for row in marks):
                raise ValueError('Diagnostic session JAR, mark or error state differs')
            if any('review' in row['id'] or 'gametest' in row['id'] for row in environment['modVersions']):
                raise ValueError('Ordinary console run must not contain QA/GameTest mods')
        diagnostic_summary = {'status':'PASS_NATIVE_SERVER_CONSOLE_DEFAULT60_AUTO_STOP_MARK_EXPORT',
                              'sessionId':session['sessionId'], 'secondsRequested':60, 'elapsedSeconds':session['elapsedNs']/1e9,
                              'serverTickSamples':session['ticks']['allCount'], 'stopReason':session['stopReason'],
                              'diagnosticZipSha256':sha(archives[0]), 'jarSha256':jar['sha256'], 'errors':0,
                              'clientTelemetry':session['clientTelemetry'], 'scope':'Actual ordinary dedicated server console; not client GUI, linked client ZIP or performance comparison'}
        copies.extend([(args.diagnostics_run_report, package / 'evidence' / args.diagnostics_run_report.name),
                       (archives[0], package / 'evidence' / archives[0].name)])
    for source in [args.extraction_report, args.native_report, args.disk_report] + args.evidence:
        copies.append((source, package / 'evidence' / source.name))
    for source, destination in copies:
        shutil.copyfile(source, destination)
        if sha(source) != sha(destination):
            raise ValueError('Copied evidence differs')
    inputs = []
    for relative in ('src/architecture/java', 'src/architecture/resources', 'src/rp/java', 'src/rp/resources', 'gradle/wrapper'):
        inputs.extend(path for path in (ROOT / relative).rglob('*') if path.is_file())
    inputs.extend(ROOT / name for name in ('build.gradle', 'settings.gradle', 'gradle.properties', 'gradlew', 'gradlew.bat'))
    production_sources = [{'path': path.relative_to(ROOT).as_posix(), 'sha256': sha(path)} for path in sorted(inputs, key=lambda path:path.relative_to(ROOT).as_posix())]
    provenance = {'schemaVersion': 1, 'productionInputFingerprintAlgorithm': 'SHA256 of UTF8 canonical JSON sorted source path/sha256 rows',
                  'productionInputFingerprint': hashlib.sha256(json.dumps(production_sources, ensure_ascii=False, sort_keys=True, separators=(',', ':')).encode('utf8')).hexdigest(),
                  'productionInputs': production_sources, 'productionJarSha256': jar['sha256'],
                  'nativeGameTests': {'fullCount': len(full), 'focusedCount': len(focused), 'distinctCount': len(full | focused), 'failures': 0},
                  'transactionChecks': 21, 'pureUndoChecks': undo.stdout.strip(),
                  'clientScope': 'Partial real observations only; user ended testing today and requested GitHub handoff for another computer',
                  'publication': 'NOT_YET_PUSHED'}
    if diagnostic_summary:
        (package / 'evidence/native-server-diagnostics60.json').write_text(json.dumps(diagnostic_summary, ensure_ascii=False, indent=2)+'\n', encoding='utf8')
    (package / 'evidence/production-source-provenance.json').write_text(json.dumps(provenance, ensure_ascii=False, indent=2)+'\n', encoding='utf8')
    manifest['world']['archiveExtractedOrdinaryReopen'] = {'status':'PASS', 'normalExitCode':0, 'verifiedSavedExhibits':121,
                                                        'jarSha256':jar['sha256'], 'worldZipSha256':world['sha256']}
    manifest['nativeGameTests']['distinctCount'] = len(full | focused)
    manifest['nativeGameTests']['focusedCount'] = len(focused)
    manifest['productionInputFingerprint'] = provenance['productionInputFingerprint']
    manifest['publication'] = 'NOT_YET_PUSHED'
    manifest['visibleTests'] = 'PARTIAL; user ended testing today; continue on another computer per HANDOFF.md'
    if diagnostic_summary:
        manifest['nativeServerDiagnostics60'] = diagnostic_summary
    manifest['evidenceFiles'] = [{'file':path.relative_to(package).as_posix(), 'sha256':sha(path)}
                               for path in sorted(package.rglob('*')) if path.is_file() and path.name != 'manifest.json']
    (package / 'manifest.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2)+'\n', encoding='utf8')
    print(json.dumps({'status':'PASS_REVIEW_PACKAGE_WITH_NATIVE_ARCHIVE_REOPEN', 'jarSha256':jar['sha256'],
                      'worldZipSha256':world['sha256'], 'distinctNativeChecks':148, 'publication':'NOT_YET_PUSHED'}))


if __name__ == '__main__':
    main()
