"""Package the locally verified map without rebuilding the compatible 2.0.1 mod."""
from pathlib import Path
import argparse
import hashlib
import json
import zipfile


ROOT = Path(__file__).resolve().parents[1]
NAME = 'Ether-Bloodborne-2.0.2'


def sha(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def package(world, delivery):
    world, delivery = world.resolve(), delivery.resolve()
    if not (world / 'level.dat').is_file():
        raise ValueError('Missing prepared world')
    if delivery == world or world in delivery.parents:
        raise ValueError('Delivery must be outside the world')
    if delivery == ROOT or ROOT in delivery.parents:
        raise ValueError('Private map delivery must be outside the Git project')
    report_path = delivery / 'restored-map-independent-verification.json'
    checks = json.loads(report_path.read_text(encoding='utf-8'))
    restoration = json.loads((delivery / 'position-restoration-verification.json').read_text(encoding='utf-8'))
    assert checks['passed'] and checks['errorCount'] == 0
    assert Path(checks['worlds']['after']).resolve() == world
    assert checks['counts']['after'].get('barriers', 0) == 0
    assert checks['counts']['after']['visibleLegacyObjects'] == restoration['counts']['legacy_roots_at_exact_source_state_and_position']
    files = {p.relative_to(world).as_posix(): p for p in sorted(world.rglob('*'))
             if p.is_file() and p.name != 'session.lock'}
    assert report_path.stat().st_mtime_ns >= max(p.stat().st_mtime_ns for p in files.values()), 'World changed after verification'
    expected = {relative: sha(path) for relative, path in files.items()}
    archive = delivery / (NAME + '-positions.zip')
    with zipfile.ZipFile(archive, 'x', compression=zipfile.ZIP_DEFLATED,
                         compresslevel=4, allowZip64=True) as output:
        for relative, path in files.items():
            output.write(path, NAME + '/' + relative)
    with zipfile.ZipFile(archive) as output:
        assert set(output.namelist()) == {NAME + '/' + relative for relative in files}
        for relative, digest in expected.items():
            # Reading checks ZIP CRC; SHA-256 also proves exact archived bytes.
            assert hashlib.sha256(output.read(NAME + '/' + relative)).hexdigest() == digest, relative
            assert sha(files[relative]) == digest, 'World changed during packaging: ' + relative
    digest = sha(archive)
    (delivery / (archive.name + '.sha256')).write_text(digest + '  ' + archive.name + '\n', encoding='ascii')
    manifest_path = delivery / 'SHA256.json'
    manifest = json.loads(manifest_path.read_text(encoding='utf-8'))
    manifest['files'][archive.name] = {'bytes': archive.stat().st_size, 'sha256': digest}
    manifest['current_map'] = archive.name
    manifest['compatible_mod'] = 'bloodborne-blocks-2.0.1-mc1.20.1.jar'
    for old in ('Ether-Bloodborne-2.0.zip', 'Ether-Bloodborne-2.0.1-lightfix.zip'):
        if old not in manifest['superseded_archives']:
            manifest['superseded_archives'].append(old)
    manifest['position_restoration'] = {
        'counts': restoration['counts'],
        'independent_verification_passed': True,
        'verification_report_sha256': sha(report_path),
        'preexisting_fluid_state_differences': checks['preexistingDifferenceCount'],
        'archive_files_verified': len(files),
        'minecraft_started': False,
    }
    manifest_path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(json.dumps({'archive': str(archive), 'filesVerified': len(files),
                      **manifest['files'][archive.name]}, ensure_ascii=False))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('world', type=Path)
    parser.add_argument('delivery', type=Path)
    args = parser.parse_args()
    package(args.world, args.delivery)
