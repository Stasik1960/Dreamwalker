"""Restore the pre-native city snapshot from its content-addressed GitHub volume."""
import hashlib
import json
from pathlib import Path
from zipfile import ZipFile

from probe_bush import BASE as root
manifest = root / 'pre-map-manifest.json'
assert hashlib.file_digest(manifest.open('rb'), 'sha256').hexdigest() == (
    '79acd3fca57285fe15dcc062d575f2dd9dfb594ba67602076857f80d2d0a29c0'
)
doc = json.loads(manifest.read_bytes())
prefix = ('/deliverables/block-fixes-2-20261020/map/bloodborne-blocks-fixed-2/')
target = root / 'worlds/pre-conversion/bloodborne-blocks-fixed-2'
target.mkdir(parents=True, exist_ok=False)
rows = [row for row in doc['files'] if prefix in row['path']]
assert rows
with ZipFile(root/'resume-data-003.zip') as archive:
    for row in rows:
        rel = Path(row['path'].split(prefix, 1)[1])
        out = (target/rel).resolve()
        assert out.is_relative_to(target)
        if rel.name == 'session.lock':
            continue
        out.parent.mkdir(parents=True, exist_ok=True)
        check = hashlib.sha256()
        with out.open('xb') as stream:
            for digest in row['chunks']:
                assert doc['chunks'][digest]['volume'] == 'resume-data-003.zip'
                data = archive.read('blobs/'+digest)
                assert hashlib.sha256(data).hexdigest() == digest
                check.update(data)
                stream.write(data)
        assert out.stat().st_size == row['bytes'] and check.hexdigest() == row['sha256']
print(target, len(rows))
