"""Clip selected verified complete native models into visual-only fragments."""
from pathlib import Path
import argparse, json, sqlite3
from export_cells import canonical, clipping_fingerprint, clip_pose, digest, state_key


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--fragments', type=Path, required=True)
    parser.add_argument('--states', type=Path, required=True)
    parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args()
    document = json.loads(args.states.read_text(encoding='utf-8-sig'))
    requested = document if isinstance(document, list) else document.get('nativeStates', document.get('states', []))
    database = sqlite3.connect(args.fragments)
    database.execute('PRAGMA journal_mode=WAL')
    database.execute('CREATE TABLE IF NOT EXISTS native_fragments(state TEXT NOT NULL,x INTEGER NOT NULL,y INTEGER NOT NULL,z INTEGER NOT NULL,digest TEXT NOT NULL,data TEXT NOT NULL,PRIMARY KEY(state,x,y,z))')
    algorithm = clipping_fingerprint()
    proofs = []
    for requested_row in requested:
        state = requested_row.get('state', requested_row)
        key = state_key(state)
        raw = database.execute('SELECT data FROM native WHERE state=?', (key,)).fetchone()
        if raw is None:
            raise KeyError('Native model was not exported: '+key)
        native = json.loads(raw[0])
        if not native.get('nativeVisualComplete', True):
            raise ValueError('Physics-only native data cannot generate visual fragments: '+key)
        cells, proof = clip_pose({'key': 'nv_'+digest(state)[:24], 'quads': native['quads'], 'cells': []})
        with database:
            for cell in cells:
                coordinate = cell.pop('cell')
                encoded = canonical(cell)
                previous = database.execute('SELECT data FROM native_fragments WHERE state=? AND x=? AND y=? AND z=?', (key, *coordinate)).fetchone()
                if previous and previous[0] != encoded:
                    raise ValueError('Native visual clipping changed an existing fragment: '+key)
                database.execute('INSERT OR IGNORE INTO native_fragments VALUES (?,?,?,?,?,?)', (key, *coordinate, digest(cell), encoded))
        proofs.append({'state': state, **proof})
    database.execute('INSERT OR REPLACE INTO metadata VALUES (?,?)', ('nativeVisualClippingAlgorithmSha256', canonical(algorithm)))
    database.commit()
    database.execute('PRAGMA wal_checkpoint(TRUNCATE)')
    total = database.execute('SELECT COUNT(*) FROM native_fragments').fetchone()[0]
    database.close()
    args.out.write_text(json.dumps({'status': 'PASS', 'states': len(proofs), 'visualFragments': total, 'physicsCopiedFromSourceFragments': False, 'clippingAlgorithmSha256': algorithm, 'proofs': proofs}, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')


if __name__ == '__main__':
    main()
