"""Package the verified unified mod and its matching editable city gallery."""
from __future__ import annotations
import argparse
import hashlib
import json
import shutil
import zipfile
from pathlib import Path
from world_io import compound, decode_nbt

PROJECT = Path(__file__).resolve().parents[1]
VERSION = '2.1.0-unified-gallery.2'

def sha256(path):
    result = hashlib.sha256()
    with Path(path).open('rb') as source:
        for data in iter(lambda: source.read(1024 * 1024), b''):
            result.update(data)
    return result.hexdigest()

def package(world, jar, output):
    world, jar, output = (Path(p).resolve() for p in (world, jar, output))
    if output.exists():
        raise ValueError('release directory must be new')
    docs = PROJECT / 'docs/unified-models-gallery-2026-10-03'
    manifest = json.loads((world / 'editable-gallery-manifest.json').read_bytes())
    if manifest['schemaVersion'] != 2:
        raise ValueError('unsupported gallery contract')
    ids = [r['sourceBlockId'] for r in manifest['specimens']]
    numeric = [r['numericId'] for r in manifest['specimens']]
    if len(ids) != len(set(ids)) or len(numeric) != len(set(numeric)):
        raise ValueError('duplicate gallery IDs')
    if any(not value.isdigit() or len(value) > 5 for value in numeric):
        raise ValueError('invalid numeric ID')
    with zipfile.ZipFile(jar) as archive:
        metadata = json.loads(archive.read('fabric.mod.json'))
        if metadata['version'] != VERSION or archive.testzip():
            raise ValueError('unexpected mod version or broken JAR')
        registered = {Path(n).stem for n in archive.namelist()
            if n.startswith('assets/bloodborne_blocks/blockstates/') and n.endswith('.json')}
        if registered != set(ids) or registered != set(manifest['coverage']['registeredIds']):
            raise ValueError('JAR and gallery coverage differ')
        city_count = len(json.loads(archive.read('bloodborne_blocks/city/definitions.json'))['blocks'])
        logical_count = len(json.loads(archive.read('bloodborne_blocks/logical/definitions.json'))['blocks'])
        mapping_hash = hashlib.sha256(archive.read('bloodborne_blocks/city/unified-migration.json')).hexdigest()
        jar_members = set(archive.namelist())
    proofs = {}
    evidence = {}
    for name in ('registry', 'city', 'gallery', 'server', 'protected-model-assets', 'client', 'build'):
        path = docs / f'unified-{name}-proof.json'
        proof = json.loads(path.read_bytes())
        if not proof['passed']:
            raise ValueError(f'{name} verification did not pass')
        proofs[name] = sha256(path)
        evidence[name] = proof
    if evidence['registry']['newIds'] != city_count or evidence['registry']['mappingSha256'] != mapping_hash:
        raise ValueError('registry proof does not match the packaged JAR')
    for name in ('city', 'gallery'):
        counts = evidence[name]['registry']
        if (counts['activeIds'], counts['cityDefinitions'], counts['logicalDefinitions']) != (len(ids), city_count, logical_count):
            raise ValueError(f'{name} proof does not match the packaged registry')
    gallery_proof = evidence['gallery']
    if Path(gallery_proof['worlds']['gallery']).name != world.name or gallery_proof['manifest']['coverage'] != len(ids):
        raise ValueError('gallery proof does not match the packaged world')
    server_proof = evidence['server']
    expected_classes = {'unchanged': sum(r['editable'] for r in manifest['specimens']), 'service-unchanged': 1}
    if server_proof['world'] != world.name or server_proof['galleryAfterRestart']['classifications'] != expected_classes:
        raise ValueError('server proof does not match the packaged world')
    assets_proof = evidence['protected-model-assets']
    dependencies = assets_proof['modelDependencies']
    if assets_proof['restoredRegistryIds'] != 0 or assets_proof['count'] != len(dependencies) or not dependencies:
        raise ValueError('invalid protected model dependency proof')
    if any(f'assets/bloodborne_blocks/models/block/city/{ident}.json' not in jar_members for ident in dependencies):
        raise ValueError('JAR omits protected model dependencies')
    if any(evidence[name]['version'] != VERSION for name in ('client', 'build')):
        raise ValueError('client or build proof belongs to another version')
    with zipfile.ZipFile(jar) as archive:
        for path, digest in evidence['client']['verifiedResourcesSha256'].items():
            if hashlib.sha256(archive.read(path)).hexdigest() != digest:
                raise ValueError('JAR differs from client-verified resources: ' + path)
    data = compound(compound(decode_nbt((world / 'level.dat').read_bytes(), compressed='gzip').root)['Data'])
    if 'Player' in data:
        raise ValueError('release world contains player data')
    files = [p for p in sorted(world.rglob('*')) if p.is_file()]
    forbidden = {'playerdata', 'advancements', 'stats', 'session.lock', 'usercache.json'}
    if any(forbidden.intersection(p.relative_to(world).parts) for p in files):
        raise ValueError('release world contains personal or session files')
    hashes = {p.relative_to(world).as_posix(): sha256(p) for p in files}
    output.mkdir(parents=True)
    shutil.copy2(jar, output / jar.name)
    shutil.copy2(docs / 'README.md', output / 'README.md')
    world_zip = output / (world.name + '.zip')
    with zipfile.ZipFile(world_zip, 'w', zipfile.ZIP_DEFLATED, compresslevel=2) as archive:
        for path in files:
            archive.write(path, world.name + '/' + path.relative_to(world).as_posix())
    with zipfile.ZipFile(world_zip) as archive:
        if archive.testzip():
            raise ValueError('map ZIP CRC failed')
        for relative, digest in hashes.items():
            if hashlib.sha256(archive.read(world.name + '/' + relative)).hexdigest() != digest:
                raise ValueError('map payload differs from verified world')
    if hashes != {p.relative_to(world).as_posix(): sha256(p) for p in files}:
        raise ValueError('world changed while packaging')
    review_zip = output / 'Review-Materials.zip'
    with zipfile.ZipFile(review_zip, 'w', zipfile.ZIP_DEFLATED, compresslevel=2) as archive:
        for path in sorted(docs.rglob('*')):
            if path.is_file():
                archive.write(path, 'Review-Materials/' + path.relative_to(docs).as_posix())
    with zipfile.ZipFile(review_zip) as archive:
        if archive.testzip():
            raise ValueError('review ZIP CRC failed')
    result = {'schemaVersion': 1, 'version': VERSION, 'minecraft': '1.20.1',
        'registeredIds': len(ids), 'editableSpecimens': sum(r['editable'] for r in manifest['specimens']),
        'matchingModAndWorldRequired': True, 'worldFiles': hashes, 'proofFiles': proofs,
        'artifacts': {p.name: {'bytes': p.stat().st_size, 'sha256': sha256(p)}
            for p in sorted(output.iterdir()) if p.is_file()}}
    (output / 'package-manifest.json').write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    checksums = {name: value['sha256'] for name, value in result['artifacts'].items()}
    checksums['package-manifest.json'] = sha256(output / 'package-manifest.json')
    (output / 'SHA256.txt').write_text(''.join(digest + '  ' + name + '\n'
        for name, digest in checksums.items()), encoding='ascii')
    print(json.dumps({'output': str(output), 'registeredIds': len(ids),
        'artifacts': result['artifacts']}, ensure_ascii=False))
    return result

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--world', type=Path, required=True)
    parser.add_argument('--jar', type=Path, default=PROJECT / f'build/libs/bloodborne-blocks-{VERSION}.jar')
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    package(args.world, args.jar, args.output)
