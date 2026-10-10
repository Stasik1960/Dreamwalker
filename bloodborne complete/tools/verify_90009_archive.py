"""Verify the shipped gallery ZIP and extract only its declared files to a new isolated fixture."""
from __future__ import annotations
import argparse
import hashlib
import json
from pathlib import Path, PurePosixPath
import zipfile

ROOT = Path(__file__).resolve().parents[1]


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--package', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--report', type=Path, required=True)
    args = parser.parse_args()
    package, output = args.package.resolve(), args.output.resolve()
    if not package.is_relative_to((ROOT.parent / 'releases').resolve()):
        raise ValueError('Only the explicitly packaged worktree release is accepted')
    if not output.is_relative_to((ROOT / 'build').resolve()) or output.exists():
        raise ValueError('Extraction requires a new isolated build directory')
    manifest = json.loads((package / 'manifest.json').read_text(encoding='utf8'))
    jar, world = manifest['jar'], manifest['world']
    if sha(package / jar['file']) != jar['sha256'] or sha(package / world['file']) != world['sha256']:
        raise ValueError('Ordinary JAR or world ZIP differs from manifest')
    expected = {'Dreamwalker_BB_Gallery_90009/' + row['path']: row for row in world['files']}
    if len(expected) != len(world['files']):
        raise ValueError('Duplicate declared world path')
    fixture = output / 'isolated-smoke-world'
    with zipfile.ZipFile(package / world['file']) as archive:
        if set(archive.namelist()) != set(expected) or len(archive.infolist()) != len(expected) or archive.testzip():
            raise ValueError('ZIP file roster or CRC differs')
        if sum(info.file_size for info in archive.infolist()) > 1024**3:
            raise ValueError('Isolated gallery unexpectedly exceeds 1 GiB')
        for info in archive.infolist():
            relative = PurePosixPath(expected[info.filename]['path'])
            if relative.is_absolute() or any(part in ('..', '.', '') or ':' in part or '\\' in part for part in relative.parts):
                raise ValueError('Unsafe archive entry: ' + info.filename)
            row = expected[info.filename]
            payload = archive.read(info)
            if len(payload) != row['bytes'] or hashlib.sha256(payload).hexdigest() != row['sha256']:
                raise ValueError('Archived file differs: ' + info.filename)
            destination = fixture.joinpath(*relative.parts)
            destination.parent.mkdir(parents=True, exist_ok=True)
            destination.write_bytes(payload)
            if sha(destination) != row['sha256']:
                raise ValueError('Extracted file differs: ' + info.filename)
    if not (fixture / 'level.dat').is_file() or (fixture / 'session.lock').exists():
        raise ValueError('Saved world entry or omitted session lock is invalid')
    report = {'schemaVersion': 1, 'status': 'PASS_EXACT_SHIPPED_ARCHIVE_EXTRACTION',
              'package': str(package), 'fixture': str(fixture), 'jarSha256': jar['sha256'],
              'worldZipSha256': world['sha256'], 'verifiedFiles': len(expected),
              'crcAndEveryExtractedFileSha256': 'PASS', 'nativeReopen': 'REQUIRES_SEPARATE_PRODUCTION_RUN'}
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(report, ensure_ascii=False, indent=2)+'\n', encoding='utf8')
    print(json.dumps(report))


if __name__ == '__main__':
    main()
