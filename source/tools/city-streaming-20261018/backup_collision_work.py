"""Freeze the existing converted city before the collision/barrier follow-up."""
import hashlib
import json
import os
import shutil
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone
from pathlib import Path

PROJECT = Path(__file__).absolute().parents[2]
REPO = PROJECT.parent
SOURCE = PROJECT / 'build/native-city-mesh/fixtures/baseline/world'
OUT = REPO / 'deliverables/collision-streaming-20261018'

def ordinary(path):
    if path.is_symlink() or path.stat().st_file_attributes & 1024:
        raise RuntimeError('Reparse point refused: ' + str(path))

def sha(path):
    h = hashlib.sha256()
    with path.open('rb') as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b''):
            h.update(chunk)
    return h.hexdigest()

def main():
    ordinary(SOURCE)
    paths = []
    for parent, dirs, files in os.walk(SOURCE, followlinks=False):
        for name in dirs + files:
            ordinary(Path(parent) / name)
        paths.extend(Path(parent) / name for name in files)
    size = sum(p.stat().st_size for p in paths)
    if shutil.disk_usage('C:/').free < size * 2 + 8 * 2**30:
        raise RuntimeError('Insufficient C: space for independent backup and work copy')
    OUT.mkdir(exist_ok=False)
    evidence = OUT / 'evidence'
    evidence.mkdir()
    targets = [OUT / 'backup/world', OUT / 'map/world']
    for target in targets:
        target.mkdir(parents=True)
    initial = {'status': 'COPYING', 'recordedUtc': datetime.now(timezone.utc).isoformat(),
               'sourceWorld': str(SOURCE), 'backupWorld': str(targets[0]),
               'workingWorld': str(targets[1]), 'files': len(paths), 'bytes': size,
               'baseCommit': '28855213258c23c683cbe46e158e594dbb9caf3f'}
    receipt = evidence / 'map-backup.json'
    receipt.write_text(json.dumps(initial, indent=2) + '\n')
    def copy(source):
        rel = source.relative_to(SOURCE)
        digest = sha(source)
        for target in targets:
            dest = target / rel
            dest.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(source, dest)
            if source.stat().st_ino == dest.stat().st_ino or sha(dest) != digest:
                raise RuntimeError('Independent copy verification failed: ' + str(dest))
        if sha(source) != digest:
            raise RuntimeError('Source changed during copy: ' + str(source))
        return {'path': rel.as_posix(), 'bytes': source.stat().st_size, 'sha256': digest}
    with ThreadPoolExecutor(max_workers=8) as pool:
        records = sorted(pool.map(copy, paths), key=lambda row: row['path'])
    manifest = evidence / 'original-world-files.json'
    manifest.write_text(json.dumps(records, indent=2) + '\n')
    initial.update(status='PASS_INDEPENDENT_BACKUP_AND_WORK_COPY',
                   verifiedUtc=datetime.now(timezone.utc).isoformat(),
                   manifestSha256=sha(manifest),
                   baselineJarSha256=sha(REPO / 'deliverables/streaming-dh-20261018/game/dreamwalker-bb-streaming.4.jar'))
    receipt.write_text(json.dumps(initial, indent=2) + '\n')
    print(json.dumps(initial))

if __name__ == '__main__':
    main()
