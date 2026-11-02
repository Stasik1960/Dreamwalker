"""Generate small login fingerprints offline; never hash asset trees during play/login."""
from pathlib import Path
import hashlib
import json

W = Path(__file__).resolve().parents[1]
RES = W / 'src/main/resources'
OUTPUT = RES / 'assets/bloodborne_dw/visuals/profile-fingerprints.json'
BASELINE = '1f2e71c6203a9b3794d9c3cf6cf6cbe6e0a93a750584dc9df967bd2cd166e621'


def add(digest, name, data):
    name = name.encode('utf-8')
    digest.update(len(name).to_bytes(4, 'big'))
    digest.update(name)
    digest.update(len(data).to_bytes(8, 'big'))
    digest.update(data)


def fingerprints():
    index = json.loads((RES / 'assets/bloodborne_dw/visuals/resource-index.json').read_bytes())
    shared = hashlib.sha256()
    add(shared, 'baseline', BASELINE.encode('ascii'))
    paths = sorted(p for side in ('main', 'client') for p in (W / 'src' / side).rglob('*.java'))
    paths += sorted(p for p in RES.rglob('*') if p.is_file() and p != OUTPUT
                    and not p.relative_to(RES).as_posix().startswith('dw_city_visuals/'))
    for path in paths:
        data = path.read_bytes()
        if path.suffix in ('.java', '.json', '.fsh', '.vsh', '.txt'):
            data = data.replace(b'\r\n', b'\n')
        add(shared, path.relative_to(W).as_posix(), data)
    result = {'schema': 1}
    for mode in ('original', 'alt'):
        digest = shared.copy()
        add(digest, 'mode', mode.encode('ascii'))
        for name in sorted(index[mode]):
            if not name.startswith('assets/') or '..' in name or '\\' in name:
                raise ValueError(f'Invalid profile path: {name}')
            add(digest, name, (RES / 'dw_city_visuals' / mode / name).read_bytes())
        result[mode] = digest.hexdigest()
    return result


if __name__ == '__main__':
    OUTPUT.write_text(json.dumps(fingerprints(), indent=2) + '\n', encoding='utf-8')
    print('City visual profile fingerprints generated')
