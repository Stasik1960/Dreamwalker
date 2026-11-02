"""Index every active-profile asset so model and texture overrides are served."""
from pathlib import Path
import argparse
import json

ROOT = Path(__file__).resolve().parents[1]

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    resources = ROOT / 'src/main/resources'
    output = resources / 'assets/bloodborne_dw/visuals/resource-index.json'
    index = {}
    for mode in ('original', 'alt'):
        profile = resources / 'dw_city_visuals' / mode
        paths = []
        for path in profile.rglob('*'):
            if not path.is_file():
                continue
            relative = path.relative_to(profile).as_posix()
            if not relative.startswith('assets/'):
                continue
            if not path.resolve().is_relative_to(profile.resolve()) or '..' in path.relative_to(profile).parts:
                raise RuntimeError('Unsafe profile asset: ' + str(path))
            paths.append(relative)
        index[mode] = sorted(paths)
    if args.check:
        current = json.loads(output.read_text(encoding='utf8'))
        for mode, paths in index.items():
            missing = set(paths) - set(current.get(mode, []))
            stale = set(current.get(mode, [])) - set(paths)
            if missing or stale:
                raise SystemExit(f'{mode}: unindexed={sorted(missing)}, stale={sorted(stale)}')
        print('Profile resource index complete')
    else:
        output.write_text(json.dumps(index, indent=2) + '\n', encoding='utf8')
        print('Profile resources indexed:', {k:len(v) for k,v in index.items()})

if __name__ == '__main__':
    main()
