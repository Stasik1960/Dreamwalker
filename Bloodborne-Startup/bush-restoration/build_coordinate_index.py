#!/usr/bin/env python3
"""Index every placed DreamWalker/Bloodborne block by dimension and XYZ."""
import argparse
import json
from pathlib import Path
import sqlite3
import struct
import zlib

from probe_bush import visual_entries
from world_io import (RegionFile, compound, child, count_palette_indices, block_state_key,
                      TAG_COMPOUND, TAG_LIST, TAG_LONG_ARRAY, _dimension_for_region)

NAMESPACES = {'bloodborne', 'bloodborne_dw', 'bloodborne_blocks', 'bloodborne_city',
              'bloodborne_rp', 'bloodbornerp', 'dreamwalker', 'dreamwalker_bb'}


def index_world(db, label, world):
    if not (world/'level.dat').is_file():
        raise ValueError(f'Not a Minecraft world: {world}')
    count = 0
    region_count = 0
    for path in sorted(world.glob('**/region/r.*.*.mca')):
        dimension = _dimension_for_region(world, path)
        region_count += 1
        for stored in RegionFile.open(path).chunks():
            root = compound(stored.nbt().root)
            if not root.get('sections'):
                continue
            cx, cz = root['xPos'].value, root['zPos'].value
            visual = dict(visual_entries(root))
            for section in root['sections'].value:
                states = child(section, 'block_states', TAG_COMPOUND)
                if not states:
                    continue
                palette = child(states, 'palette', TAG_LIST).value
                lookup = [block_state_key(tag) if compound(tag)['Name'].value.split(':', 1)[0]
                          in NAMESPACES else None for tag in palette]
                if not any(lookup):
                    continue
                packed = child(states, 'data', TAG_LONG_ARRAY)
                longs = packed.value if packed else []
                counts = count_palette_indices(longs, len(palette))
                seen = {lookup[i]: n for i, n in counts.items() if lookup[i] is not None}
                if not seen:
                    continue
                sy = compound(section)['Y'].value
                overlay = visual.get(sy, {})
                payload = struct.pack(f'>{len(longs)}q', *longs)
                section_id = db.execute('INSERT INTO sections(label,dimension,cx,sy,cz,palette,packed,visuals) VALUES(?,?,?,?,?,?,?,?)',
                    (label,dimension,cx,sy,cz,json.dumps(lookup,separators=(',',':')),zlib.compress(payload,1),
                     zlib.compress(json.dumps(overlay,separators=(',',':')).encode(),1) if overlay else None)
                ).lastrowid
                db.executemany('INSERT INTO section_states VALUES(?,?,?)',
                               [(state,section_id,n) for state,n in seen.items()])
                count += sum(seen.values())
        db.commit()
    print(f'{label}: {count:,} blocks in {region_count} regions', flush=True)
    db.execute('INSERT INTO sources VALUES (?,?,?,?)', (label, str(world), count, region_count))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('output', type=Path)
    parser.add_argument('worlds', nargs='+', help='label=world-root')
    args = parser.parse_args()
    pairs = [s.split('=', 1) for s in args.worlds]
    if any(len(pair) != 2 for pair in pairs) or len({pair[0] for pair in pairs}) != len(pairs):
        parser.error('Provide a distinct label=world-root for each input')
    with args.output.open('xb'):
        pass
    with sqlite3.connect(args.output) as db:
        db.executescript('''
            CREATE TABLE sections(id INTEGER PRIMARY KEY, label TEXT NOT NULL, dimension TEXT NOT NULL,
                cx INTEGER NOT NULL, sy INTEGER NOT NULL, cz INTEGER NOT NULL,
                palette TEXT NOT NULL, packed BLOB NOT NULL, visuals BLOB);
            CREATE TABLE section_states(state TEXT NOT NULL, section_id INTEGER NOT NULL, count INTEGER NOT NULL);
            CREATE TABLE sources(label TEXT PRIMARY KEY, world_root TEXT NOT NULL,
                blocks INTEGER NOT NULL, regions INTEGER NOT NULL);
        ''')
        for label, path in pairs:
            index_world(db, label, Path(path).resolve())
        db.executescript('''
            CREATE UNIQUE INDEX section_xyz ON sections(label, dimension, cx, cz, sy);
            CREATE INDEX by_state ON section_states(state, section_id);
        ''')
        assert db.execute('PRAGMA quick_check').fetchone() == ('ok',)


if __name__ == '__main__':
    main()
