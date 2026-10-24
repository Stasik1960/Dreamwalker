"""Archive the complete current source tree after actual FULL JAR compatibility.

Packaging only: no build, SQL, world reads or gameplay acceptance. Root stops
source writers before running this once. The source ZIP and manifest are fresh
outputs outside the source tree; existing checkpoints are never overwritten.
"""
import argparse
import hashlib
import json
import os
import stat
from pathlib import Path
from zipfile import ZIP_DEFLATED, ZipFile
from qa.client_runtime_lineage import read_lineage

SOURCE = Path(__file__).resolve().parents[2]
EXCLUDED = {'.git', '.gradle', 'build', 'logs', 'crash-reports', '__pycache__', '.idea'}
STATUS = 'PASS_EXACT_FULL_GENERATED_AND_REVIEWED_RUNTIME_PATCH_NOT_GAMEPLAY'


def require(value, message):
    if not value:
        raise ValueError(message)


def digest(stream):
    result = hashlib.sha256()
    for block in iter(lambda: stream.read(1 << 20), b''):
        result.update(block)
    return result.hexdigest()


def sha(path):
    with Path(path).open('rb') as stream:
        return digest(stream)


def bound(reference):
    path = Path(reference['path']).resolve(strict=True)
    require(sha(path) == reference['sha256'], 'Changed binding: ' + str(path))
    return json.loads(path.read_text(encoding='utf-8-sig'))


def inventory():
    files = {}
    for current, directories, names in os.walk(SOURCE, followlinks=False):
        directories[:] = sorted(name for name in directories if name not in EXCLUDED)
        for name in [*directories, *sorted(names)]:
            path = Path(current) / name
            info = path.lstat()
            require(not stat.S_ISLNK(info.st_mode) and not getattr(info, 'st_file_attributes', 0) & 0x400,
                    'Reparse source entry refused: ' + str(path))
            if name in directories:
                continue
            require(stat.S_ISREG(info.st_mode), 'Non-regular source entry: ' + str(path))
            files[path.relative_to(SOURCE).as_posix()] = (info.st_size, info.st_mtime_ns)
    return files


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--compatibility', type=Path, required=True)
    parser.add_argument('--compatibility-sha256', required=True)
    parser.add_argument('--out', type=Path, required=True)
    parser.add_argument('--manifest', type=Path, required=True)
    parser.add_argument('--client-runtime-lineage', type=Path)
    parser.add_argument('--client-runtime-lineage-sha256')
    args = parser.parse_args()
    reference = {'path': str(args.compatibility.resolve(strict=True)), 'sha256': args.compatibility_sha256}
    compatibility = bound(reference)
    require(compatibility['status'] == STATUS and compatibility['runtimeCompatibilityPassed'] is True and
            not compatibility['failures'], 'Actual successful FULL JAR compatibility required')
    patch = bound(compatibility['reviewedRuntimePatch'])
    for source in patch['sourceFiles']:
        require(Path(source['path']).resolve().is_relative_to(SOURCE) and sha(source['path']) == source['sha256'],
                'Current runtime sources differ from the reviewed FULL JAR')
    freeze = bound(compatibility['frozenFull'])
    resources = SOURCE / 'src/architecture/resources/assets/bloodborne_dw/native_grid'
    for name, reference_file in (('catalog.json', freeze['catalog']), ('meshes.bin', freeze['meshes']),
                                 ('retained_whole_items.json', patch['resource'])):
        require(sha(resources / name) == reference_file['sha256'], 'Current generated source resource changed: ' + name)
    require(sha(compatibility['full']['path']) == compatibility['full']['sha256'], 'Final production JAR changed')
    require((args.client_runtime_lineage is None) == (args.client_runtime_lineage_sha256 is None),
            'Client lineage requires its paired explicit SHA')
    client_lineage = None;client_ref = None;production = compatibility['full']
    if args.client_runtime_lineage is not None:
        client_ref = {'path': str(args.client_runtime_lineage.resolve(strict=True)), 'sha256': args.client_runtime_lineage_sha256}
        pinned = bound(client_ref)
        client_lineage = read_lineage(client_ref, pinned['candidate'],
                                     SOURCE / 'src/architecture/java/dev/dreamwalker/bloodbornedw/composite/NativeGridClient.java')
        require(client_lineage['predecessor'] == compatibility['full'], 'Source package predecessor compatibility differs')
        production = client_lineage['candidate']
    output, manifest = args.out.resolve(), args.manifest.resolve()
    temporary = output.with_name(output.name + '.writing')
    require(output != manifest and all(not path.is_relative_to(SOURCE) and not path.exists()
                                     for path in (output, manifest, temporary)), 'Fresh outputs outside source required')
    output.parent.mkdir(parents=True, exist_ok=True)
    manifest.parent.mkdir(parents=True, exist_ok=True)
    before, rows = inventory(), []
    with ZipFile(temporary, 'x', compression=ZIP_DEFLATED, compresslevel=1) as archive:
        for relative in sorted(before):
            path = SOURCE / relative
            with path.open('rb') as stream:
                actual_sha = digest(stream)
            archive.write(path, relative)
            rows.append({'path': relative, 'bytes': before[relative][0], 'sha256': actual_sha})
    with temporary.open('r+b') as stream:
        os.fsync(stream.fileno())
    require(inventory() == before, 'Source inventory changed during packaging')
    with ZipFile(temporary) as archive:
        require(archive.namelist() == [row['path'] for row in rows], 'Archive source inventory differs')
        for row in rows:
            with archive.open(row['path']) as stream:
                require(digest(stream) == row['sha256'], 'Archive source bytes differ: ' + row['path'])
    require(inventory() == before and sha(args.compatibility) == args.compatibility_sha256,
            'Source or compatibility changed before sealing')
    if client_lineage is not None:
        require(read_lineage(client_ref, production,
                             SOURCE / 'src/architecture/java/dev/dreamwalker/bloodbornedw/composite/NativeGridClient.java') == client_lineage,
                'Client-only runtime lineage changed while packaging')
    os.link(temporary, output)
    temporary.unlink()
    result = {'schemaVersion': 1, 'status': 'PACKAGED_AND_VERIFIED_CURRENT_SOURCES_NOT_DELIVERY_ACCEPTANCE',
              'kind': 'COMPLETE_CURRENT_SOURCE_ARCHIVE', 'sourceRoot': str(SOURCE),
              'archive': {'path': str(output), 'sha256': sha(output), 'bytes': output.stat().st_size},
              'runtime': production, 'compatibility': reference,
              **({'acceptedPredecessorProduction': compatibility['full'], 'clientOnlyRuntimeLineage': client_ref}
                 if client_lineage is not None else {}),
              'runtimePatch': compatibility['reviewedRuntimePatch'], 'frozenFull': compatibility['frozenFull'],
              'excludedBuildAndCacheDirectoryNames': sorted(EXCLUDED), 'sourceFiles': len(rows),
              'sourceLogicalBytes': sum(row['bytes'] for row in rows), 'files': rows,
              'generatorSha256': sha(__file__), 'builds': 0, 'sqlReads': 0, 'worldReads': 0,
              'gameplayChecksRepeated': 0, 'readyForDelivery': False}
    with manifest.open('x', encoding='utf-8', newline='\n') as stream:
        json.dump(result, stream, ensure_ascii=False, indent=2)
        stream.write('\n')
        stream.flush()
        os.fsync(stream.fileno())
    print(json.dumps({'status': result['status'], 'archive': result['archive'],
                      'manifestSha256': sha(manifest), 'sourceFiles': len(rows)}))


if __name__ == '__main__':
    main()
