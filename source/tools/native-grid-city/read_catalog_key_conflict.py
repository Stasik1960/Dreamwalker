"""Read exact persistent signature and mapping for one failed catalogue key."""
import argparse
import json
import sqlite3
import sys
from pathlib import Path
import migrate as m
import native_certificates as c
sys.path.insert(0, str(m.PROJECT / 'tools/native-grid/compiler'))
from catalog import digest
from export_cells import FragmentStore, state_key


def main():
    p = argparse.ArgumentParser(description=__doc__)
    for name in ('compositions', 'fragments', 'database', 'out'):
        p.add_argument('--' + name, type=Path, required=True)
    p.add_argument('--key', required=True)
    a = p.parse_args()
    row = None
    with a.compositions.open(encoding='utf-8-sig') as stream:
        for line in stream:
            found = json.loads(line)
            if found['key'] == a.key:
                row = found
                break
    m.require(row is not None, 'Actual failed composition key missing')
    store = FragmentStore(a.fragments)
    db = sqlite3.connect('file:' + a.database.resolve().as_posix() + '?mode=ro', uri=True)
    try:
        ordinary = [store.fragment(item['pose'], tuple(item['cell']))[0] for item in row.get('fragments', [])]
        visual = [store.fragment(item['pose'], tuple(item['cell']))[0] for item in row.get('substrateFragments', [])]
        visual += [store.native_fragment(state_key(item['state']), tuple(item['cell']))[0] for item in row.get('substrateNativeFragments', [])]
        base = row.get('baseState')
        signature = digest({'fragments': sorted(ordinary), 'substrateFragments': sorted(visual),
                            'baseVisualMode': row.get('baseVisualMode', 'native'), 'base': state_key(base) if base else None,
                            'optics': row.get('optics'), 'fluid': row.get('fluid'), 'fluidExplicit': 'fluid' in row,
                            'localFunction': row.get('localFunction'), 'salt': None})
        mapped = db.execute('SELECT cell FROM mapping WHERE key=?', (a.key,)).fetchone()
        ingredient = db.execute('SELECT cell FROM ingredients WHERE signature=?', (signature,)).fetchone()
        cells = []
        for label, value in (('existingMapping', mapped), ('actualIngredient', ingredient)):
            if value:
                cell = json.loads(db.execute("SELECT data FROM banks WHERE kind='cells' AND idx=?", (value[0],)).fetchone()[0])
                optics = json.loads(db.execute("SELECT data FROM banks WHERE kind='optics' AND idx=?", (cell['optics'],)).fetchone()[0])
                cells.append({'kind': label, 'index': value[0], 'cell': cell, 'optics': optics})
        report = {'status': 'READ_ONLY_EXACT_CATALOG_KEY_CONFLICT', 'composition': row,
                  'ingredientSignature': signature, 'cells': cells, 'databaseWrites': 0, 'worldReads': 0}
        c.immutable_json(m.module_api(), a.out.resolve(), report)
        print(m.canonical({'status': report['status'], 'cells': [v['index'] for v in cells]}))
    finally:
        db.close()
        store.close()


if __name__ == '__main__':
    main()
