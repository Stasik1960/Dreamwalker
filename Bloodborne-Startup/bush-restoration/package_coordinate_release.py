"""Add the repaired world and verified root locations to the coordinate release."""
import json
from contextlib import closing
from pathlib import Path
import shutil
import sqlite3
from tempfile import TemporaryDirectory
from zipfile import ZIP_DEFLATED, ZipFile

from build_coordinate_index import index_world
from probe_bush import BASE, SCRIPT_DIR


DB = 'Bloodborne-block-coordinates.sqlite'
REPORT = BASE / 'Bloodborne-v22-Bush-Restoration-report.json'
WORLD = BASE / 'worlds/bush-restoration-release/bloodborne'
OUTPUT = BASE / 'Bloodborne-v22-Bush-Restoration-coordinates.zip'
DIMENSIONS = {'overworld': 'minecraft:overworld', 'bloodborne_old': 'bloodborne_dw:bloodborne_old'}


def main():
    report = json.loads(REPORT.read_bytes())
    audit = json.loads((BASE / 'grass-audit.json').read_bytes())
    with TemporaryDirectory(dir=BASE) as temp:
        target = Path(temp) / DB
        shutil.copyfile(BASE / DB, target)
        with closing(sqlite3.connect(target)) as db, db:
            db.executescript('''
                DROP TABLE IF EXISTS bush_roots;
                DROP TABLE IF EXISTS bush_cells;
                DROP TABLE IF EXISTS bush_repair_edits;
                CREATE TABLE bush_roots(x INT,y INT,z INT,state TEXT,source_data TEXT,
                    status TEXT,reason TEXT, PRIMARY KEY(x,y,z));
                CREATE TABLE bush_cells(x INT,y INT,z INT,root_x INT,root_y INT,root_z INT,
                    old_owners INT, PRIMARY KEY(root_x,root_y,root_z,x,y,z));
                CREATE INDEX bush_cell_xyz ON bush_cells(x,y,z);
                CREATE TABLE bush_release_anchors(
                    dimension TEXT NOT NULL, x INTEGER NOT NULL, y INTEGER NOT NULL, z INTEGER NOT NULL,
                    anchor_x INTEGER, anchor_y INTEGER, anchor_z INTEGER, state TEXT,
                    status TEXT NOT NULL, reason TEXT,
                    PRIMARY KEY(dimension,x,y,z));
                CREATE INDEX release_anchor_xyz ON bush_release_anchors(dimension,anchor_x,anchor_y,anchor_z);
            ''')
            for pos, owner, parts in audit['old']:
                props = ','.join(f'{k}={v}' for k, v in sorted(owner['properties'].items()))
                state = owner['id'] + '[' + props + ']'
                db.execute('INSERT INTO bush_roots VALUES(?,?,?,?,?,?,?)',
                           (*pos, state, json.dumps(owner['payload']), 'historical', None))
                db.executemany('INSERT INTO bush_cells VALUES(?,?,?,?,?,?,?)',
                               [(*member, *pos, count) for member, count in parts])
            for scope, dimension in DIMENSIONS.items():
                result = report.get(scope, {})
                for pos, anchor, state in result.get('restored', ()):
                    db.execute('INSERT INTO bush_release_anchors VALUES(?,?,?,?,?,?,?,?,?,?)',
                               (dimension, *pos, *anchor, state, 'restored', None))
                    if scope == 'overworld':
                        db.execute("UPDATE bush_roots SET status='restored' WHERE x=? AND y=? AND z=?", pos)
                for pos, reason in result.get('unresolved', ()):
                    db.execute('INSERT INTO bush_release_anchors VALUES(?,?,?,?,?,?,?,?,?,?)',
                               (dimension, *pos, None, None, None, None, 'unresolved', reason))
                    if scope == 'overworld':
                        db.execute("UPDATE bush_roots SET status='unresolved',reason=? WHERE x=? AND y=? AND z=?",
                                   (reason, *pos))
            index_world(db, 'bush_restoration', WORLD)
            db.execute("UPDATE sources SET world_root=label")
            assert db.execute('PRAGMA quick_check').fetchone() == ('ok',)
            expected = sum(len(report.get(scope, {}).get('restored', ())) for scope in DIMENSIONS)
            assert db.execute("SELECT count(*) FROM bush_release_anchors WHERE status='restored'").fetchone() == (expected,)
        with ZipFile(OUTPUT, 'x', ZIP_DEFLATED, compresslevel=4) as archive:
            archive.write(target, DB)
            for name in ('query_coordinates.py', 'Координаты.txt'):
                archive.write(SCRIPT_DIR / name, name)
        with ZipFile(OUTPUT) as archive:
            assert archive.testzip() is None
        print(OUTPUT, OUTPUT.stat().st_size, flush=True)


if __name__ == '__main__':
    main()
