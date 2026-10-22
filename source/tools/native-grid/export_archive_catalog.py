"""Archive failed authoring bindings before an authorized finite catalog repair."""
from pathlib import Path
import argparse, hashlib, json, sqlite3, zipfile


def sha(path):
    value = hashlib.sha256()
    with Path(path).open('rb') as stream:
        for chunk in iter(lambda: stream.read(1 << 20), b''):
            value.update(chunk)
    return value.hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--resources', type=Path, required=True)
    parser.add_argument('--database', type=Path, required=True)
    parser.add_argument('--mapping', type=Path, required=True)
    parser.add_argument('--evidence', type=Path, required=True)
    parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args()
    if args.out.exists():
        raise ValueError('Use a fresh archive path; preserved failed evidence is immutable')
    args.out.mkdir(parents=True)
    database = sqlite3.connect('file:'+args.database.absolute().as_posix()+'?mode=ro', uri=True)
    backup = sqlite3.connect(args.out/'catalog-state.sqlite')
    database.backup(backup)
    backup.close()
    database.close()
    namespace = args.resources.parent
    files = [*args.resources.iterdir(), *[path for folder in ('blockstates', 'models/block', 'models/item') for path in (namespace/folder).glob('native_grid_*.json')]]
    records = []
    with zipfile.ZipFile(args.out/'failed-resources-and-mapping.zip', 'x', compression=zipfile.ZIP_DEFLATED) as archive:
        for path in sorted(files):
            if not path.is_file() or path.is_symlink():
                raise ValueError('Unexpected nonregular owned resource: '+str(path))
            relative = Path('assets/bloodborne_dw')/path.relative_to(namespace)
            archive.write(path, relative.as_posix())
            records.append({'name': relative.as_posix(), 'bytes': path.stat().st_size, 'sha256': sha(path)})
        for label, path in [('type-mapping.jsonl', args.mapping), ('actual-failed-carrier-gate.json', args.evidence)]:
            archive.write(path, label)
            records.append({'name': label, 'bytes': path.stat().st_size, 'sha256': sha(path)})
    report = {'status': 'FAILED_AUTHORING_BINDINGS_ARCHIVED', 'reason': 'Actual carrier sidedTransparency=false differs from prior AIR-derived background=true; no native map state had been installed', 'sourceDatabaseSha256': sha(args.database), 'databaseSnapshotSha256': sha(args.out/'catalog-state.sqlite'), 'archiveSha256': sha(args.out/'failed-resources-and-mapping.zip'), 'files': records, 'mapWrites': 0, 'runtimeGuardRelaxed': False}
    (args.out/'manifest.json').write_text(json.dumps(report, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
    print(json.dumps({'status': report['status'], 'resourceAndEvidenceFiles': len(records), 'archiveSha256': report['archiveSha256']}))


if __name__ == '__main__':
    main()
