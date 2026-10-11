"""Package the verified combined mod and complete sources without copying worlds."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import stat
import zipfile

ROOT = Path(__file__).resolve().parents[1]
VERSION = '0.1.0-v12.1-resource-transfer'

def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def ordinary(path):
    attrs = path.lstat()
    if stat.S_ISLNK(attrs.st_mode) or getattr(attrs, 'st_file_attributes', 0) & 0x400:
        raise ValueError('Reparse point forbidden: ' + str(path))

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    output = args.output.resolve()
    if not output.is_relative_to(ROOT.parent / 'releases') or output.exists():
        raise ValueError('Use a fresh release directory within this repository')
    jar = ROOT / f'build/libs/dreamwalker-bb-fabric-1.20.1-{VERSION}.jar'
    digest = sha(jar)
    for name in ('artifact-validation.json', 'preservation-results.json'):
        result = json.loads((ROOT / 'docs/remaining-transfer' / name).read_text(encoding='utf-8'))
        assert result['status'] == 'PASS', name
        assert result.get('productionJarSha256', result.get('jarSha256')) == digest, name
    output.mkdir(parents=True)
    shipped = output / jar.name
    shutil.copyfile(jar, shipped)
    assert sha(shipped) == digest
    source_zip = output / f'dreamwalker-bb-fabric-1.20.1-{VERSION}-full-source.zip'
    source_files = []
    for folder in ('src', 'tools', 'docs', 'gradle'):
        for current in (ROOT / folder).rglob('*'):
            ordinary(current)
            if current.is_file() and '__pycache__' not in current.parts:
                source_files.append(current)
    for name in ('build.gradle', 'settings.gradle', 'gradle.properties', 'gradlew', 'gradlew.bat', '.gitignore', 'README.md'):
        path = ROOT / name
        if path.is_file():
            ordinary(path)
            source_files.append(path)
    with zipfile.ZipFile(source_zip, 'w', compression=zipfile.ZIP_DEFLATED, compresslevel=6) as archive:
        for path in sorted(source_files):
            archive.write(path, 'bloodborne complete/' + path.relative_to(ROOT).as_posix())
    report = ROOT / 'docs/remaining-transfer/release-report.md'
    assert report.is_file()
    shutil.copyfile(report, output / 'REPORT.md')
    for name in ('model-correspondence.csv', 'state-correspondence.csv', 'object-verification.json',
                 'artifact-validation.json', 'preservation-results.json', 'runtime-verification.json',
                 'fixture-diagnosis.md', 'source-transparent-exclusions.json', 'source-audit.md'):
        path = ROOT / 'docs/remaining-transfer' / name
        if path.is_file():
            shutil.copyfile(path, output / name)
    manifest = {'baselineCommit': json.loads((ROOT / 'docs/remaining-transfer/baseline.json').read_text(encoding='utf-8'))['commit'],
                'version': VERSION, 'completeSourceFiles': len(source_files),
                'files': [{'name': path.name, 'bytes': path.stat().st_size, 'sha256': sha(path)}
                          for path in sorted(output.iterdir()) if path.is_file()],
                'sourceMapOrGalleryIncluded': False,
                'build': 'Java 17; ./gradlew build; combined architecture and RP in one production JAR'}
    (output / 'SHA256.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')

if __name__ == '__main__':
    main()
