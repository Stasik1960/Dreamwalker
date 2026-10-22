"""Request only missing exact native-state queries from a saved palette list."""
from pathlib import Path
import argparse, hashlib, json, sqlite3
from export_cells import state_key


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--request', type=Path, required=True)
    parser.add_argument('--fragments', type=Path, required=True)
    parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args()
    document = json.loads(args.request.read_text(encoding='utf-8-sig'))
    requested = document if isinstance(document, list) else document.get('nativeStates', document.get('states', []))
    database = sqlite3.connect('file:'+str(args.fragments.absolute()).replace('\\', '/')+'?mode=ro', uri=True)
    missing = {}
    present = 0
    for row in requested:
        key = state_key(row)
        if database.execute('SELECT 1 FROM native WHERE state=?', (key,)).fetchone():
            present += 1
        else:
            missing[key] = {**row, 'physicsOnly': True}
    database.close()
    result = {'schemaVersion': 1, 'states': [missing[key] for key in sorted(missing)], 'alreadyExported': present, 'missing': len(missing), 'requestSha256': hashlib.sha256(args.request.read_bytes()).hexdigest(), 'source': 'Actual cached state palettes only; no game map opened'}
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(json.dumps(result, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
    print(json.dumps({'alreadyExported': present, 'missing': len(missing)}))


if __name__ == '__main__':
    main()
