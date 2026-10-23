"""One finite NTFS fixture for the new delta transfer, never the game worlds."""
from pathlib import Path
from types import SimpleNamespace
import hashlib
import json
import os
import shutil
import sys
import time
import zipfile

ROOT = Path(__file__).resolve().parents[4]
sys.dont_write_bytecode = True
sys.path.insert(0, str(ROOT / 'source/tools/native-grid'))
import github_delta_pack as pack
import github_delta_restore as restore


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open('xb') as stream:
        stream.write(data)
    return path


def document(path, value):
    return write(path, (json.dumps(value, ensure_ascii=False, separators=(',', ':')) + '\n').encode('utf-8'))


def restore_to(manifest, assets, target, receipt):
    previous = sys.argv
    try:
        sys.argv = ['github_delta_restore.py', '--manifest', str(manifest),
                    '--manifest-sha256', restore.sha(manifest), '--assets', str(assets),
                    '--target', str(target), '--receipt', str(receipt)]
        restore.main()
    finally:
        sys.argv = previous


def main():
    fixture = ROOT / 'research/transfer' / ('delta-hybrid-' + time.strftime('%Y%m%dT%H%M%S', time.gmtime()))
    fixture.mkdir(parents=True, exist_ok=False)
    user = fixture / 'user'
    current = user / 'project'
    audit = user / 'audit'
    old = user / 'old-assets'
    current.mkdir(parents=True)
    audit.mkdir()
    old.mkdir()
    originals = {
        'project/unchanged.txt': b'unchanged old reusable chunk\n',
        'project/changed.txt': b'old content\n',
        'project/removed.txt': b'old removed overlay path\n',
        'project/empty.txt': b'',
        'external/cache.bin': bytes(range(256)) * 257,
    }
    volume = old / 'fixture-base-001.zip'
    chunks = {}
    files = []
    timestamp = 1_792_700_000_123_456_700
    with zipfile.ZipFile(volume, 'x', compression=zipfile.ZIP_DEFLATED) as archive:
        for relative, data in originals.items():
            digest = hashlib.sha256(data).hexdigest()
            ids = [digest] if data else []
            if data and digest not in chunks:
                archive.writestr('blobs/' + digest, data)
                chunks[digest] = {'bytes': len(data), 'volume': volume.name}
            files.append({'path': relative, 'bytes': len(data), 'mtimeNs': timestamp,
                          'sha256': digest, 'chunks': ids})
    base = {'schemaVersion': 1, 'sourceUserRoot': str(user), 'credentialsIncluded': False,
            'volumes': [{'name': volume.name, 'bytes': volume.stat().st_size, 'sha256': restore.sha(volume)}],
            'chunks': chunks, 'files': files, 'fileCount': len(files),
            'logicalBytes': sum(row['bytes'] for row in files)}
    base_path = document(old / 'bundle-manifest.json', base)
    snapshot = {'assets': [
        {**base['volumes'][0], 'url': 'https://github.com/Stasik1960/Dreamwalker/releases/download/fixture-base/' + volume.name},
        {'name': base_path.name, 'bytes': base_path.stat().st_size, 'sha256': restore.sha(base_path)},
    ]}
    snapshot_path = document(user / 'base-snapshot.json', snapshot)
    write(current / 'unchanged.txt', originals['project/unchanged.txt'])
    write(current / 'changed.txt', b'new current content\n')
    write(current / 'empty.txt', b'')
    write(audit / 'START-RU.txt', 'Исходное задание и текущая работа.\n'.encode('utf-8'))
    args = SimpleNamespace(source_user_root=user, root=current, audit=audit)
    inventory = document(user / 'inventory.json', pack.inventory(args))
    freeze = document(user / 'freeze.json', {
        'schemaVersion': 1, 'kind': 'EXPLICIT_QUIESCENT_GITHUB_DELTA_FREEZE',
        'status': 'PASS', 'quiescent': True, 'projectWritersStopped': True,
        'inventory': {'path': str(inventory), 'sha256': restore.sha(inventory)},
        'deliveryStatus': 'CHECKPOINT', 'acceptance': None,
        'syntheticFixtureOnly': True,
    })
    args.out = user / 'delta-assets'
    args.base_manifest = base_path
    args.base_manifest_sha256 = restore.sha(base_path)
    args.base_snapshot = snapshot_path
    args.base_snapshot_sha256 = restore.sha(snapshot_path)
    args.freeze_binding = freeze
    args.freeze_sha256 = restore.sha(freeze)
    args.new_release_url = 'https://github.com/Stasik1960/Dreamwalker/releases/download/fixture-delta/'
    args.prefix = 'fixture-delta'
    old_seal = pack.seal(volume)
    old_hash = restore.sha(volume)
    pack.pack(args)
    manifest = args.out / 'bundle-manifest.json'
    captured = restore.read(manifest)
    assert captured['tombstones'] == ['project/removed.txt']
    assert captured['delta']['oldVolumesReuploaded'] == 0
    assert captured['delta']['reusedChunkOccurrences'] >= 1
    assert captured['deliveryStatus'] == 'CHECKPOINT' and captured['readyForDelivery'] is False
    assert captured['files'][0] and not (user / 'external/cache.bin').exists()
    assert all(row['bytes'] <= restore.VOLUME_BYTES for row in captured['volumes'] if row['origin'] == 'delta')
    shutil.copyfile(volume, args.out / volume.name)
    target = user / 'restored'
    # One exact preexisting file exercises matching-file admission in the same restore.
    write(target / 'project/unchanged.txt', originals['project/unchanged.txt'])
    restore_to(manifest, args.out, target, fixture / 'restore-pass.json')
    assert not (target / 'project/removed.txt').exists()
    assert (target / 'project/changed.txt').read_bytes() == b'new current content\n'
    assert (target / 'external/cache.bin').read_bytes() == originals['external/cache.bin']
    for row in captured['files']:
        restored = target.joinpath(*restore.safe_relative(row['path']).parts)
        assert restore.sha(restored) == row['sha256'] and restored.stat().st_mtime_ns == row['mtimeNs']
    differing = user / 'refused-target'
    write(differing / 'project/changed.txt', b'user data must survive\n')
    refused = False
    try:
        restore_to(manifest, args.out, differing, fixture / 'must-not-exist.json')
    except ValueError as error:
        refused = 'Refusing differing/hardlinked destination' in str(error)
    assert refused and (differing / 'project/changed.txt').read_bytes() == b'user data must survive\n'
    assert not (differing / 'audit/START-RU.txt').exists()
    assert not (fixture / 'must-not-exist.json').exists()
    # New changed source after freeze must be rejected before an output directory exists.
    with (current / 'changed.txt').open('ab') as stream:
        stream.write(b'mutation after the explicit freeze\n')
    args.out = user / 'rejected-delta-assets'
    mutation_refused = False
    try:
        pack.pack(args)
    except ValueError as error:
        mutation_refused = 'inventory/stat seals changed' in str(error)
    assert mutation_refused and not args.out.exists()
    assert restore.sha(volume) == old_hash and pack.seal(volume) == old_seal
    report = {
        'status': 'PASS_ONE_NEW_FINITE_DELTA_RESTORE_FIXTURE',
        'fixture': str(fixture), 'syntheticOnly': True,
        'checks': ['hybrid old-chunk reuse plus new overlay and tombstone',
                   'one exact matching destination plus fresh payload and exact mtimeNs',
                   'different destination rejected before any payload publication',
                   'post-freeze source mutation rejected before output creation',
                   'old ZIP bytes and stat seal unchanged'],
        'packSourceSha256': restore.sha(pack.__file__),
        'restoreSourceSha256': restore.sha(restore.__file__),
        'manifestSha256': restore.sha(manifest),
        'realProjectMapFilesRead': 0, 'realProjectMapWrites': 0,
        'networkUsed': False, 'minecraftRuns': 0, 'readyForDelivery': False,
    }
    document(fixture / 'fixture-result.json', report)
    print(json.dumps(report, ensure_ascii=False), flush=True)


if __name__ == '__main__':
    main()
