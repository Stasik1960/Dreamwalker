"""Append bounded content-addressed ZIPs to an immutable published checkpoint.

inventory only proposes stat seals; it does NOT approve quiescence or READY.
pack requires a separate explicit root-supplied freeze binding and never runs
builds, games, SQL, network or upload. All outputs must be outside every overlay.
"""
import argparse
import copy
import ctypes
import hashlib
import json
import os
import re
import stat
import time
import urllib.parse
import zipfile
from pathlib import Path
from github_delta_restore import (CHUNK_BYTES, VOLUME_BYTES, HEX, require, sha, read,
                                  safe_relative, no_reparse, regular, public_url,
                                  save_new, validate_manifest)

CREDENTIAL_NAMES = {'launcher_accounts.json', 'accounts.json', 'credentials.json', 'cookies.sqlite',
                    'id_rsa', 'id_ed25519', '.git-credentials', '.netrc', '.npmrc', '.pypirc'}
STRONG = re.compile(rb'(?:gh[pousr]_[A-Za-z0-9]{25,}|github_pat_[A-Za-z0-9_]{30,}|-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----)')
TOKEN = re.compile(rb'(?i)(?:"accessToken"\s*:\s*"([^"\r\n]*)"|"--accessToken"\s*[,\r\n ]+"([^"\r\n]*)")')
PLAIN_TOKEN = re.compile(rb'(?i)--accessToken[= \t]+([^ \t\r\n]+)')
CAPTURE_RUN = re.compile(r'^\d{8}T\d{6}-github_delta_pack\.(?:log|json)$')


def bound(path, digest=None):
    path = regular(path);value = {'path': str(path), 'sha256': sha(path)}
    if digest is not None:require(value['sha256'] == digest, 'Explicit evidence SHA differs: ' + str(path))
    return value


def seal(path):
    value = regular(path).stat()
    return {'bytes': value.st_size, 'mtimeNs': value.st_mtime_ns, 'ctimeNs': value.st_ctime_ns,
            'device': value.st_dev, 'inode': value.st_ino}


def lock_is_closed(path):
    """Read-only exclusive-open probe; stale lock files are omitted, live fail."""
    if os.name == 'nt':
        kernel = ctypes.WinDLL('kernel32', use_last_error=True)
        kernel.CreateFileW.argtypes = (ctypes.c_wchar_p, ctypes.c_uint32, ctypes.c_uint32,
                                      ctypes.c_void_p, ctypes.c_uint32, ctypes.c_uint32, ctypes.c_void_p)
        kernel.CreateFileW.restype = ctypes.c_void_p
        handle = kernel.CreateFileW(str(path), 0x80000000, 0, None, 3, 0x80, None)
        require(handle != ctypes.c_void_p(-1).value, 'Live/inaccessible lock file; stop its writer: ' + str(path))
        kernel.CloseHandle.argtypes = (ctypes.c_void_p,);kernel.CloseHandle(handle)
    else:
        import fcntl
        with path.open('rb') as stream:
            try:fcntl.flock(stream.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
            except OSError as error:raise ValueError('Live lock file: ' + str(path)) from error


def roots(a):
    user = no_reparse(a.source_user_root)
    values = [no_reparse(path) for path in (a.root, a.audit, *getattr(a, 'extra_overlay', []))]
    require(all(path.is_dir() and path.is_relative_to(user) and path != user for path in values), 'Overlay roots must be concrete directories below the user root')
    require(all(not path.is_relative_to(other) and not other.is_relative_to(path)
                for index, path in enumerate(values) for other in values[:index]), 'Overlay roots overlap')
    return user, values


def outside(path, values):
    path = no_reparse(path)
    require(all(not path.is_relative_to(root) for root in values), 'Capture metadata/output must be outside authoritative overlays')
    return path


def inventory(a):
    user, values = roots(a);files = [];excluded = []
    for root in values:
        for current, directories, names in os.walk(root, followlinks=False):
            parent = Path(current)
            kept = []
            for name in sorted(directories):
                path = parent / name;info = path.lstat()
                reason = 'internal-git-or-python-cache' if name in ('.git', '__pycache__') else 'credential-store' if name.lower() in (
                    '.ssh', '.aws', '.azure', '.gnupg') else 'reparse-point' if (
                    stat.S_ISLNK(info.st_mode) or getattr(info, 'st_file_attributes', 0) & 1024) else None
                if reason:excluded.append({'path': path.relative_to(user).as_posix(), 'reason': reason})
                else:kept.append(name)
            directories[:] = kept
            for name in sorted(names):
                path = parent / name;relative = path.relative_to(user).as_posix();info = path.lstat();reason = None
                if stat.S_ISLNK(info.st_mode) or getattr(info, 'st_file_attributes', 0) & 1024:reason = 'reparse-point'
                elif name == '.git':reason = 'internal-git-or-python-cache'
                elif name.lower() in CREDENTIAL_NAMES or name.lower().startswith('.env'):reason = 'credential-store'
                elif path.parent == values[0] / 'research/processes' and (name == 'latest.json' or CAPTURE_RUN.fullmatch(name)):
                    reason = 'delta-capture-own-run-task-receipt'
                elif name.endswith(('-wal', '-shm', '-journal')):
                    raise ValueError('SQLite WAL/SHM requires a closed checkpoint before capture: ' + str(path))
                elif name.endswith(('.lock', '.lck')) or name == 'session.lock':
                    lock_is_closed(path);reason = 'closed-session-or-build-lock'
                if reason:
                    excluded.append({'path': relative, 'reason': reason});continue
                safe_relative(relative);files.append({'source': str(path), 'path': relative, **seal(path)})
    files.sort(key=lambda row: row['path'].casefold());seen = set()
    for row in files:
        require(row['path'].casefold() not in seen, 'Case-colliding overlay paths');seen.add(row['path'].casefold())
    return {'schemaVersion': 1, 'kind': 'PROPOSED_GITHUB_DELTA_STAT_INVENTORY_NOT_QUIESCENCE_APPROVAL',
            'sourceUserRoot': str(user), 'overlayRoots': [str(path) for path in values], 'files': files,
            'excluded': sorted(excluded, key=lambda row: row['path']), 'fileCount': len(files),
            'logicalBytes': sum(row['bytes'] for row in files), 'worldWrites': 0, 'projectProcessesStarted': 0}


def same_inventory(left, right, root):
    """Ignore only this capture wrapper's volatile journal names, not seals."""
    def stable(value):
        result = dict(value)
        processes = (root / 'research/processes').relative_to(Path(value['sourceUserRoot'])).as_posix()
        def capture_journal(row):
            parent, _, name = row['path'].rpartition('/')
            return (row['reason'] == 'delta-capture-own-run-task-receipt' and parent == processes
                    and (name == 'latest.json' or CAPTURE_RUN.fullmatch(name)))
        result['excluded'] = [row for row in value['excluded'] if not capture_journal(row)]
        return result
    return stable(left) == stable(right)


def scan(data, path):
    require(not STRONG.search(data), 'Credential pattern detected; values suppressed: ' + str(path))
    if path.suffix.lower() in ('.json', '.args'):
        matches = list(TOKEN.finditer(data))
        if path.suffix.lower() == '.args':matches += list(PLAIN_TOKEN.finditer(data))
        for match in matches:
            token = next(value for value in match.groups() if value is not None).strip().strip(b'"\'')
            safe = token.lower() in (b'', b'0', b'dummy', b'offline', b'null', b'none',
                                    b'${auth_access_token}', b'${access_token}') or (token.startswith(b'<') and token.endswith(b'>'))
            require(safe, 'Non-placeholder accessToken detected; value suppressed: ' + str(path))


class Volumes:
    def __init__(self, folder, prefix, url):
        self.folder, self.prefix, self.url = folder, prefix, url
        self.archive = None;self.path = None;self.rows = [];self.members = 0

    def close(self):
        if self.archive:
            self.archive.close();self.archive = None
            require(self.path.stat().st_size <= VOLUME_BYTES, 'New ZIP exceeded strict 768MiB bound')
            with self.path.open('r+b') as stream:os.fsync(stream.fileno())
            self.rows.append({'name': self.path.name, 'bytes': self.path.stat().st_size,
                              'sha256': sha(self.path), 'url': self.url + urllib.parse.quote(self.path.name), 'origin': 'delta'})

    def put(self, key, data):
        # Conservative DEFLATE upper bound + central directory; exact checked
        # on close. A maximum-size chunk always fits in a fresh volume.
        overhead = len(data) + len(data) // 100 + 65536 + (self.members + 1) * 256
        if self.archive and self.archive.fp.tell() + overhead > VOLUME_BYTES:self.close()
        if not self.archive:
            self.path = self.folder / (self.prefix + '-%03d.zip' % (len(self.rows) + 1))
            self.archive = zipfile.ZipFile(self.path, 'x', compression=zipfile.ZIP_DEFLATED, compresslevel=1)
            self.members = 0
        info = zipfile.ZipInfo('blobs/' + key, date_time=(1980, 1, 1, 0, 0, 0))
        info.compress_type = zipfile.ZIP_DEFLATED;info.external_attr = 0o100400 << 16
        self.archive.writestr(info, data, compress_type=zipfile.ZIP_DEFLATED, compresslevel=1);self.members += 1
        return self.path.name


def pack(a):
    user, values = roots(a);out = outside(a.out, values)
    require(not out.exists() and not out.is_relative_to(Path(a.base_manifest).absolute().parent), 'Fresh separate delta output directory required')
    base_ref = bound(a.base_manifest, a.base_manifest_sha256);base = read(a.base_manifest)
    validate_manifest(base, urls=False)
    require(Path(base['sourceUserRoot']) == user and base.get('credentialsIncluded') is False,
            'Original manifest user root/credential exclusion differs')
    snapshot_ref = bound(a.base_snapshot, a.base_snapshot_sha256);published = read(a.base_snapshot)
    assets = {row['name']: row for row in published['assets']}
    require(assets['bundle-manifest.json']['sha256'] == base_ref['sha256'], 'Published checkpoint references a different old manifest')
    old_volumes = []
    for row in base['volumes']:
        asset = assets[row['name']]
        require(asset['sha256'] == row['sha256'] and asset['bytes'] == row['bytes'], 'Published old volume binding differs')
        old_volumes.append({**copy.deepcopy(row), 'url': public_url(asset['url']), 'origin': 'base'})
    freeze_ref = bound(outside(a.freeze_binding, values), a.freeze_sha256);freeze = read(a.freeze_binding)
    require(freeze.get('schemaVersion') == 1 and freeze.get('kind') == 'EXPLICIT_QUIESCENT_GITHUB_DELTA_FREEZE' and
            freeze.get('status') == 'PASS' and freeze.get('quiescent') is True and freeze.get('projectWritersStopped') is True,
            'An explicit root-approved quiescent freeze is required, not an inventory proposal')
    inv_ref = freeze['inventory'];bound(outside(inv_ref['path'], values), inv_ref['sha256']);inv = read(inv_ref['path'])
    require(same_inventory(inv, inventory(a), values[0]), 'Source inventory/stat seals changed after the root freeze')
    capture_files = list(inv['files'])
    for reference in (freeze_ref, inv_ref):
        metadata = regular(reference['path'])
        require(metadata.is_relative_to(user), 'Freeze metadata must be below the same user root for portable restoration')
        capture_files.append({'source': str(metadata), 'path': metadata.relative_to(user).as_posix(), **seal(metadata)})
    require(len({row['path'].casefold() for row in capture_files}) == len(capture_files), 'Duplicate frozen metadata/overlay path')
    delivery = freeze.get('deliveryStatus');acceptance = None
    require(delivery in ('CHECKPOINT', 'READY_FOR_DELIVERY'), 'Explicit checkpoint/delivery status required')
    if delivery == 'READY_FOR_DELIVERY':
        acceptance = freeze['acceptance'];bound(acceptance['path'], acceptance['sha256']);accepted = read(acceptance['path'])
        require(accepted.get('kind') == 'FULL_NATIVE_GRID_CITY_DELIVERY_ACCEPTANCE' and accepted.get('status') == 'PASS' and
                accepted.get('readyForDelivery') is True, 'READY requires actual final aggregate acceptance')
        require(any(Path(row['source']) == Path(acceptance['path']) for row in inv['files']), 'Final acceptance must itself be captured')
    else:require(not freeze.get('acceptance'), 'Checkpoint must not imply a delivery acceptance')
    url = public_url(a.new_release_url.rstrip('/') + '/')
    require(all(url != row['url'].rsplit('/', 1)[0] + '/' for row in old_volumes),
            'A fresh release URL is required; never replace the published old manifest/assets')
    require(re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9_.-]{0,70}', a.prefix), 'Safe unique volume prefix required')
    require(not any(row['name'].startswith(a.prefix + '-') for row in old_volumes), 'New volume namespace collides with old volumes')
    prefixes = [path.relative_to(user).as_posix() + '/' for path in values]
    old_files = {row['path']: row for row in base['files']}
    old_case = {path.casefold() for path in old_files}
    require(all(row['path'].casefold() not in old_case for row in capture_files[len(inv['files']):]),
            'Fresh freeze metadata namespace required; do not replace an old external input')
    final = {path: copy.deepcopy(row) for path, row in old_files.items()
             if not any(path.casefold().startswith(prefix.casefold()) for prefix in prefixes)}
    chunks = copy.deepcopy(base['chunks']);added = 0;reused = 0
    out.mkdir();writer = Volumes(out, a.prefix, url)
    try:
        for row in capture_files:
            path = Path(row['source']);expected = {key: row[key] for key in ('bytes', 'mtimeNs', 'ctimeNs', 'device', 'inode')}
            require(seal(path) == expected, 'Source changed before capture: ' + str(path))
            ids = [];h = hashlib.sha256();tail = b''
            read_size = min(CHUNK_BYTES, max(1, expected['bytes']))
            with path.open('rb') as stream:
                for data in iter(lambda: stream.read(read_size), b''):
                    scan(tail + data, path);tail = data[-1024:]
                    h.update(data);key = hashlib.sha256(data).hexdigest();ids.append(key)
                    if key in chunks:
                        require(chunks[key]['bytes'] == len(data), 'Content-addressed chunk length collision');reused += 1
                    else:
                        volume = writer.put(key, data);chunks[key] = {'bytes': len(data), 'volume': volume};added += 1
            require(seal(path) == expected, 'Source changed during capture: ' + str(path))
            final[row['path']] = {'path': row['path'], 'bytes': row['bytes'], 'mtimeNs': row['mtimeNs'], 'sha256': h.hexdigest(), 'chunks': ids}
        writer.close()
    finally:
        if writer.archive:writer.archive.close()
    require(same_inventory(inv, inventory(a), values[0]), 'Source files changed/appeared/disappeared while packing; output is not accepted')
    for reference in (base_ref, snapshot_ref, freeze_ref, inv_ref):bound(reference['path'], reference['sha256'])
    if acceptance:bound(acceptance['path'], acceptance['sha256'])
    files = sorted(final.values(), key=lambda row: row['path'].casefold())
    final_case = {path.casefold() for path in final}
    tombstones = sorted(path for path in old_files if path.casefold() not in final_case)
    result = {'schemaVersion': 2, 'kind': 'DREAMWALKER_CONTENT_ADDRESSED_DELTA_CONTINUATION',
              'deliveryStatus': delivery, 'readyForDelivery': delivery == 'READY_FOR_DELIVERY', 'deliveryAcceptance': acceptance,
              'sourceUserRoot': str(user), 'createdUtc': time.strftime('%Y-%m-%dT%H:%M:%SZ', time.gmtime()),
              'fileCount': len(files), 'logicalBytes': sum(row['bytes'] for row in files),
              'uniqueChunkBytes': sum(row['bytes'] for row in chunks.values()), 'files': files, 'chunks': chunks,
              'volumes': old_volumes + writer.rows, 'tombstones': tombstones,
              'authoritativeOverlayRoots': prefixes, 'overlayExclusions': inv['excluded'],
              'baseManifest': base_ref, 'publishedBaseSnapshot': snapshot_ref,
              'sourceFreeze': {'binding': freeze_ref, 'document': freeze, 'inventory': inv_ref},
              'frozenMetadataIncludedAsFiles': 2,
              'delta': {'newChunks': added, 'reusedChunkOccurrences': reused, 'newVolumes': len(writer.rows),
                        'newUploadBytes': sum(row['bytes'] for row in writer.rows), 'oldVolumesReuploaded': 0},
              'credentialsIncluded': False, 'projectProcessesStarted': 0, 'worldWrites': 0,
              'tools': {'packSha256': sha(__file__), 'restoreSha256': sha(Path(__file__).with_name('github_delta_restore.py'))}}
    validate_manifest(result)
    manifest_path = out / 'bundle-manifest.json';save_new(manifest_path, result)
    with (out / 'SHA256SUMS.txt').open('x', encoding='utf-8') as stream:
        stream.write(''.join(row['sha256'] + '  ' + row['name'] + '\n' for row in writer.rows))
        stream.write(sha(manifest_path) + '  bundle-manifest.json\n');stream.flush();os.fsync(stream.fileno())
    receipt = {'status': 'PACKED_DELTA_NOT_UPLOADED', 'deliveryStatus': delivery, 'manifestSha256': sha(manifest_path),
               'files': len(files), 'tombstones': len(tombstones), **result['delta'], 'networkUsed': False}
    save_new(out / 'pack-receipt.json', receipt);print(json.dumps(receipt))


def main():
    p = argparse.ArgumentParser(description=__doc__);sub = p.add_subparsers(dest='command', required=True)
    for command in ('inventory', 'pack'):
        child = sub.add_parser(command)
        for name in ('root', 'audit', 'source-user-root', 'out'):child.add_argument('--' + name, type=Path, required=True)
        child.add_argument('--extra-overlay', type=Path, action='append', default=[],
                           help='Additional authoritative overlay directory; repeat for disjoint roots')
    child = sub.choices['pack']
    for name in ('base-manifest', 'base-snapshot', 'freeze-binding'):child.add_argument('--' + name, type=Path, required=True)
    for name in ('base-manifest-sha256', 'base-snapshot-sha256', 'freeze-sha256', 'new-release-url', 'prefix'):
        child.add_argument('--' + name, required=True)
    a = p.parse_args()
    if a.command == 'inventory':
        _, values = roots(a);out = outside(a.out, values);save_new(out, inventory(a))
        print(json.dumps({'status': 'PROPOSED_INVENTORY_ONLY', 'path': str(out), 'sha256': sha(out), 'quiescenceApproved': False}))
    else:pack(a)


if __name__ == '__main__':main()
