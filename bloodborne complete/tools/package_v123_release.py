"""Package the verified outline/icon mod; maps and historical releases stay frozen."""
import hashlib
import json
from pathlib import Path
import shutil
import stat
import subprocess
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[1]
REPO = ROOT.parent
VERSION = '0.1.0-v12.3-outline-icons'
DOCS = ROOT / 'docs/v12.3-outline-icons'
DEST = REPO / 'releases/Dreamwalker-BB/v12.3-outline-icons'
JAR = ROOT / f'build/libs/dreamwalker-bb-fabric-1.20.1-{VERSION}.jar'
sha = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()
read = lambda p: json.loads(p.read_text(encoding='utf8'))

def ordinary(path):
    info = path.lstat()
    assert not stat.S_ISLNK(info.st_mode) and not getattr(info, 'st_file_attributes', 0) & 0x400, path

def archive(target, files):
    entries = []
    with zipfile.ZipFile(target, 'w', zipfile.ZIP_DEFLATED, compresslevel=6) as z:
        for path, name in files:
            ordinary(path)
            entries.append({'path': name, 'bytes': path.stat().st_size, 'sha256': sha(path)})
            z.write(path, name)
    with zipfile.ZipFile(target) as z:
        assert z.testzip() is None
        for entry in entries:
            assert hashlib.sha256(z.read(entry['path'])).hexdigest() == entry['sha256']
    return entries

def main():
    digest = sha(JAR)
    preservation = read(DOCS / 'preservation-and-resource-audit.json')
    acceptance = read(DOCS / 'acceptance.json')
    assert preservation['status'] == acceptance['status'] == 'PASS'
    assert preservation['artifactSha256'] == acceptance['productionJarSha256'] == digest
    assert acceptance['build'] == acceptance['gameTests'] == acceptance['nativeServer'] == acceptance['nativeClient'] == 'PASS'
    xml = ROOT / acceptance['gameTestXml']
    tests = ET.parse(xml).getroot()
    assert len(tests.findall('.//testcase')) == 63 and not tests.findall('.//failure') and not tests.findall('.//error')
    assert not DEST.exists(), 'A release must never overwrite earlier evidence'
    DEST.mkdir(parents=True)
    shutil.copyfile(JAR, DEST / JAR.name)
    assert sha(DEST / JAR.name) == digest
    tracked = subprocess.check_output(['git', 'ls-files', '-z', '-co', '--exclude-standard', '--', 'bloodborne complete/'], cwd=REPO).decode('utf8').split('\0')
    # New raw QA evidence may intentionally be ignored by normal source patterns.
    sources = {REPO / p for p in tracked if p and not p.startswith('bloodborne complete/build/')}
    sources.update(path for path in DOCS.rglob('*') if path.is_file())
    sources = sorted(sources)
    source_zip = DEST / f'dreamwalker-bb-fabric-1.20.1-{VERSION}-full-source.zip'
    source_entries = archive(source_zip, [(p, 'bloodborne complete/' + p.relative_to(ROOT).as_posix()) for p in sources])
    evidence_zip = DEST / 'verification.zip'
    evidence_entries = archive(evidence_zip, [(p, p.relative_to(DOCS).as_posix()) for p in sorted(DOCS.rglob('*')) if p.is_file()])
    for name in ('REPORT-RU.txt', 'INSTALL-RU.txt', 'acceptance.json'):
        shutil.copyfile(DOCS / name, DEST / name)
        assert sha(DEST / name) == sha(DOCS / name)
    record = {
        'schema': 'dreamwalker-v123-outline-icons-release-v1', 'status': 'PASS',
        'version': VERSION, 'productionJarSha256': digest,
        'files': [{'name': p.name, 'bytes': p.stat().st_size, 'sha256': sha(p)} for p in (DEST / JAR.name, source_zip, evidence_zip, DEST / 'REPORT-RU.txt', DEST / 'INSTALL-RU.txt', DEST / 'acceptance.json')],
        'sourceFiles': len(source_entries), 'evidenceFiles': len(evidence_entries),
        'baselineCommit': '772804c157bd28e0122a21f255b64c82d4ec5a43',
        'mapsChanged': False, 'authorCollisionExport': 'NOT_SUPPLIED_NOT_APPLIED',
        'performanceBenchmark': 'NOT_RUN',
    }
    (DEST / 'release-manifest.json').write_text(json.dumps(record, ensure_ascii=False, indent=2) + '\n', encoding='utf8')
    print(json.dumps(record, ensure_ascii=False))

if __name__ == '__main__':
    main()
