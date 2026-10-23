"""Restore a SHA-pinned continuation manifest, using each volume's own URL.

No network is used without --download. Existing differing files are never
overwritten; interrupted files remain explicit .delta-part files. Standard
library only, so this script can be downloaded separately on the new machine.
"""
import argparse
import hashlib
import json
import os
import re
import stat
import urllib.parse
import urllib.request
import zipfile
from pathlib import Path, PurePosixPath

CHUNK_BYTES = 32 * 1024 ** 2
VOLUME_BYTES = 768 * 1024 ** 2
HEX = re.compile(r'^[0-9a-f]{64}$')
DEVICES = {'CON', 'PRN', 'AUX', 'NUL'} | {p + str(n) for p in ('COM', 'LPT') for n in range(1, 10)}


def require(value, message):
    if not value:
        raise ValueError(message)


def sha(path):
    h = hashlib.sha256()
    with Path(path).open('rb') as stream:
        for data in iter(lambda: stream.read(4 * 1024 ** 2), b''):
            h.update(data)
    return h.hexdigest()


def read(path):
    require(Path(path).stat().st_size <= 512 * 1024 ** 2, 'Manifest/metadata budget exceeded')
    return json.loads(Path(path).read_text(encoding='utf-8-sig'))


def safe_relative(value):
    require(isinstance(value, str) and value and '\\' not in value, 'Unsafe relative path')
    path = PurePosixPath(value)
    require(not path.is_absolute() and path.as_posix() == value and all(
        part not in ('', '.', '..') and not part.endswith((' ', '.')) and
        part.split('.')[0].upper() not in DEVICES and not any(c in part for c in '<>:"|?*\0')
        for part in path.parts), 'Unsafe Windows-compatible path: ' + value)
    return path


def no_reparse(path):
    path = Path(os.path.abspath(path))
    for item in reversed((path, *path.parents)):
        try:
            info = item.lstat()
        except FileNotFoundError:
            continue
        require(not stat.S_ISLNK(info.st_mode) and not getattr(info, 'st_file_attributes', 0) & 1024,
                'Reparse/symlink traversal refused: ' + str(item))
    return path


def regular(path):
    path = no_reparse(path)
    require(stat.S_ISREG(path.lstat().st_mode), 'Expected regular file: ' + str(path))
    return path


def public_url(value):
    url = urllib.parse.urlsplit(value)
    require(url.scheme == 'https' and url.hostname and not url.username and not url.password and
            not url.query and not url.fragment, 'Expected explicit public HTTPS asset URL without credentials')
    return value


def save_new(path, value):
    path = no_reparse(path)
    require(not path.exists(), 'Fresh output required: ' + str(path))
    path.parent.mkdir(parents=True, exist_ok=True)
    no_reparse(path.parent)
    temporary = path.with_name(path.name + '.writing')
    with temporary.open('x', encoding='utf-8') as stream:
        json.dump(value, stream, ensure_ascii=False, separators=(',', ':'))
        stream.write('\n');stream.flush();os.fsync(stream.fileno())
    os.link(temporary, path);temporary.unlink()


def validate_manifest(doc, urls=True):
    require(doc.get('schemaVersion') in (1, 2), 'Unsupported manifest schema')
    volumes = {}
    for row in doc['volumes']:
        name = row['name']
        require(len(safe_relative(name).parts) == 1 and name not in volumes and HEX.fullmatch(row['sha256']) and
                type(row['bytes']) is int and row['bytes'] > 0, 'Invalid/repeated volume')
        if urls:public_url(row['url'])
        if row.get('origin') == 'delta':require(row['bytes'] <= VOLUME_BYTES, 'New ZIP exceeds 768MiB')
        volumes[name] = row
    chunks = doc['chunks']
    for key, row in chunks.items():
        require(HEX.fullmatch(key) and type(row['bytes']) is int and 0 < row['bytes'] <= CHUNK_BYTES and
                row['volume'] in volumes, 'Invalid content-addressed chunk')
    seen = set()
    for row in doc['files']:
        safe_relative(row['path']);key = row['path'].casefold()
        require(key not in seen and HEX.fullmatch(row['sha256']) and type(row['bytes']) is int and row['bytes'] >= 0 and
                type(row['mtimeNs']) is int and row['mtimeNs'] >= 0 and isinstance(row['chunks'], list), 'Invalid/repeated file')
        seen.add(key)
        require(all(part in chunks for part in row['chunks']) and sum(chunks[part]['bytes'] for part in row['chunks']) == row['bytes'],
                'File has missing chunks or inconsistent length')
    require(doc['fileCount'] == len(doc['files']) and doc['logicalBytes'] == sum(row['bytes'] for row in doc['files']), 'Manifest totals differ')
    for value in doc.get('tombstones', []):
        safe_relative(value);require(value.casefold() not in seen, 'Tombstone still exists in final manifest')
    return volumes


def obtain(row, folder, allow_download):
    target = no_reparse(folder / row['name'])
    if target.exists():
        regular(target)
        require(target.stat().st_size == row['bytes'] and sha(target) == row['sha256'], 'Existing asset differs: ' + row['name'])
        return target
    require(allow_download, 'Missing asset; provide --download or copy the exact volume: ' + row['name'])
    public_url(row['url'])
    temporary = no_reparse(target.with_name(target.name + '.download-part'))
    request = urllib.request.Request(row['url'], headers={'User-Agent': 'Dreamwalker-Delta-Restore'})
    with temporary.open('xb') as output, urllib.request.urlopen(request, timeout=90) as response:
        require(urllib.parse.urlsplit(response.geturl()).scheme == 'https', 'Insecure download redirect')
        count = 0
        for data in iter(lambda: response.read(4 * 1024 ** 2), b''):
            count += len(data);require(count <= row['bytes'], 'Downloaded volume exceeds declared size');output.write(data)
        output.flush();os.fsync(output.fileno())
    require(count == row['bytes'] and sha(temporary) == row['sha256'], 'Downloaded volume SHA/size mismatch')
    os.link(temporary, target);temporary.unlink()
    return target


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--manifest', type=Path, required=True);p.add_argument('--manifest-sha256', required=True)
    p.add_argument('--assets', type=Path, required=True);p.add_argument('--target', type=Path)
    p.add_argument('--verify-only', action='store_true');p.add_argument('--download', action='store_true')
    p.add_argument('--receipt', type=Path, required=True)
    a = p.parse_args()
    require(a.verify_only != (a.target is not None), 'Choose --verify-only OR --target')
    regular(a.manifest);require(HEX.fullmatch(a.manifest_sha256) and sha(a.manifest) == a.manifest_sha256, 'Pinned manifest SHA differs')
    doc = read(a.manifest);volumes = validate_manifest(doc)
    require(doc['schemaVersion'] == 2 and doc['kind'] == 'DREAMWALKER_CONTENT_ADDRESSED_DELTA_CONTINUATION', 'Expected delta continuation manifest')
    require(doc['deliveryStatus'] in ('CHECKPOINT', 'READY_FOR_DELIVERY') and
            doc['readyForDelivery'] is (doc['deliveryStatus'] == 'READY_FOR_DELIVERY') and
            bool(doc.get('deliveryAcceptance')) == doc['readyForDelivery'], 'Inconsistent checkpoint/delivery claim')
    assets = no_reparse(a.assets);assets.mkdir(parents=True, exist_ok=True)
    target = no_reparse(a.target) if a.target else None
    if target:
        target.mkdir(parents=True, exist_ok=True)
        for value in doc.get('tombstones', []):
            old = no_reparse(target.joinpath(*safe_relative(value).parts))
            require(not old.exists(), 'Authoritative overlay tombstone exists; restore to a fresh destination: ' + value)
    archives = {};verified = set();restored = 0
    try:
        for name, row in volumes.items():
            path = obtain(row, assets, a.download)
            archive = zipfile.ZipFile(path);archives[name] = archive
            entries = archive.infolist();names = set()
            for entry in entries:
                require(entry.filename not in names and entry.filename.startswith('blobs/') and
                        HEX.fullmatch(entry.filename[6:]) and not entry.is_dir() and
                        not stat.S_ISLNK(entry.external_attr >> 16), 'Unexpected/unsafe ZIP member')
                names.add(entry.filename)
                chunk = doc['chunks'].get(entry.filename[6:])
                require(chunk and chunk['volume'] == name and chunk['bytes'] == entry.file_size, 'ZIP chunk metadata differs')
            require(names == {'blobs/' + key for key, chunk in doc['chunks'].items() if chunk['volume'] == name}, 'ZIP chunk coverage differs')
            print('Verified volume ' + name, flush=True)
        def data_for(key):
            row = doc['chunks'][key];data = archives[row['volume']].read('blobs/' + key)
            require(len(data) == row['bytes'] and hashlib.sha256(data).hexdigest() == key, 'Chunk SHA/length differs')
            verified.add(key);return data
        # Admission for all destination files precedes creating any payload.
        if target:
            for row in doc['files']:
                out = no_reparse(target.joinpath(*safe_relative(row['path']).parts))
                if out.exists():
                    regular(out);info = out.stat()
                    require(info.st_nlink == 1 and info.st_size == row['bytes'] and sha(out) == row['sha256'],
                            'Refusing differing/hardlinked destination: ' + row['path'])
                require(not out.with_name(out.name + '.delta-part').exists(), 'Interrupted payload exists; inspect .delta-part before retry')
        for row in doc['files']:
            out = no_reparse(target.joinpath(*safe_relative(row['path']).parts)) if target else None
            existing = out is not None and out.exists()
            temporary = out.with_name(out.name + '.delta-part') if out else None
            if out and not existing:
                out.parent.mkdir(parents=True, exist_ok=True);no_reparse(out.parent)
            stream = temporary.open('xb') if out and not existing else None
            h = hashlib.sha256();count = 0
            try:
                for key in row['chunks']:
                    data = data_for(key);count += len(data);h.update(data)
                    if stream:stream.write(data)
                if stream:stream.flush();os.fsync(stream.fileno())
            finally:
                if stream:stream.close()
            require(count == row['bytes'] and h.hexdigest() == row['sha256'], 'File SHA/length differs: ' + row['path'])
            if out:
                no_reparse(out.parent)
                if existing:require(sha(out) == row['sha256'], 'Existing payload changed during restore')
                else:
                    # Hard-link publication fails if another file appeared; it
                    # never replaces a concurrent or differing destination.
                    os.link(temporary, out);temporary.unlink()
                os.utime(out, ns=(row['mtimeNs'], row['mtimeNs']))
                require(out.stat().st_mtime_ns == row['mtimeNs'], 'Destination filesystem cannot preserve exact mtimeNs')
                restored += 1
        for key in doc['chunks']:
            if key not in verified:data_for(key)
    finally:
        for archive in archives.values():archive.close()
    require(sha(a.manifest) == a.manifest_sha256, 'Manifest changed during restore')
    result = {'status': 'PASS_ALL_FILES_CHUNKS_AND_VOLUMES_VERIFIED' if a.verify_only else 'PASS_RESTORED_EXACT_FILES',
              'manifestSha256': a.manifest_sha256, 'files': doc['fileCount'], 'chunks': len(verified),
              'restored': restored, 'logicalBytes': doc['logicalBytes'], 'target': str(target) if target else None,
              'deliveryStatusFromBoundManifest': doc['deliveryStatus'], 'projectProcessesStarted': 0}
    save_new(a.receipt, result);print(json.dumps(result))


if __name__ == '__main__':main()
