"""Audit native visual bounds before putting a complete block into one cell."""
from pathlib import Path
import argparse, json, sqlite3


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--fragments', type=Path, required=True)
    parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args()
    database = sqlite3.connect('file:'+str(args.fragments.absolute()).replace('\\', '/')+'?mode=ro', uri=True)
    outside = []
    for state, encoded in database.execute('SELECT state,data FROM native'):
        row = json.loads(encoded)
        vertices = [v for q in row.get('quads', []) for v in q['vertices']]
        if not vertices:
            continue
        low = [min(v[i] for v in vertices) for i in range(3)]
        high = [max(v[i] for v in vertices) for i in range(3)]
        if min(low) < -1e-6 or max(high) > 1.000001:
            outside.append({'state': json.loads(state), 'min': low, 'max': high, 'quadCount': len(row['quads'])})
    database.close()
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(json.dumps({'status': 'PASS', 'nativeVisualsOutsideSingleCell': len(outside), 'states': outside}, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')


if __name__ == '__main__':
    main()
