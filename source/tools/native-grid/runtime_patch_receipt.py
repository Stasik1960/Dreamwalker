"""Prove the exact adapter-only production delta; this is never gameplay PASS."""
import argparse, hashlib, json, zipfile
from pathlib import Path

CLASS = 'dev/dreamwalker/bloodbornedw/composite/FabricCompositeWorld'
CATALOG = 'assets/bloodborne_dw/native_grid/catalog.json'
STATUS = 'PASS_EXACT_BRIDGE_CLASS_DIFF_NOT_GAMEPLAY'


def require(ok, message):
    if not ok:
        raise RuntimeError(message)


def sha(path):
    digest = hashlib.sha256()
    with Path(path).open('rb') as stream:
        for block in iter(lambda: stream.read(1 << 20), b''):
            digest.update(block)
    return digest.hexdigest()


def read(path):
    return json.loads(Path(path).read_text(encoding='utf-8-sig'))


def inventory(path):
    with zipfile.ZipFile(path) as archive:
        names = [entry.filename for entry in archive.infolist() if not entry.is_dir()]
        require(len(names) == len(set(names)), 'Duplicate JAR entries refused')
        return {name: hashlib.sha256(archive.read(name)).hexdigest() for name in names}


def describe(before, after):
    old, new = inventory(before), inventory(after)
    require(old.keys() == new.keys(), 'Adapter patch must not add/remove resources or classes')
    changed = [name for name in sorted(old) if old[name] != new[name]]
    require(CLASS + '.class' in changed, 'The actual bridge adapter class did not change')
    require(all(name == CLASS + '.class' or name.startswith(CLASS + '$') and name.endswith('.class') for name in changed),
            'Production changed outside the one reviewed adapter: ' + repr(changed))
    stable = hashlib.sha256()
    for name in sorted(old):
        if name not in changed:
            stable.update((name + '\0' + old[name] + '\n').encode())
    return {'schemaVersion': 1, 'status': STATUS,
            'scope': 'NEW_NATIVEGRIDBASE_FINAL_DETACH_ONLY',
            'before': {'path': str(Path(before).resolve()), 'sha256': sha(before)},
            'after': {'path': str(Path(after).resolve()), 'sha256': sha(after)},
            'catalogSha256': old[CATALOG], 'allResourcesExact': True,
            'allNonAdapterClassesExact': True,
            'changedEntries': [{'entry': name, 'beforeSha256': old[name], 'afterSha256': new[name]} for name in changed],
            'unchangedEntries': len(old) - len(changed),
            'unchangedEntryInventorySha256': stable.hexdigest(),
            'gameplayAccepted': False, 'mapWrites': 0}


def validate(path, production):
    bound = read(path)
    require(bound.get('status') == STATUS and bound.get('gameplayAccepted') is False, 'Unknown patch evidence')
    require(Path(bound['after']['path']).resolve() == Path(production).resolve(), 'Patch binds another production path')
    actual = describe(bound['before']['path'], production)
    require(actual == bound, 'Actual production JAR delta differs from immutable patch evidence')
    return bound


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--before', type=Path, required=True)
    parser.add_argument('--after', type=Path, required=True)
    parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args()
    require(not args.out.exists(), 'Preserve existing patch evidence; choose a fresh output')
    result = describe(args.before, args.after)
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(json.dumps({'status': result['status'], 'changedEntries': result['changedEntries'], 'out': str(args.out), 'sha256': sha(args.out)}))


if __name__ == '__main__':
    main()
