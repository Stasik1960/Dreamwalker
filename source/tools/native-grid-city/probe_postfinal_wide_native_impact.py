"""Root-run read-only impact of the exact already-exported 28 wide natives.

One current placements scan selects only the 27 bound composition keys. All
other lookups are exact affected coordinates; cached NBT is read only where a
placement has no saved source_state. No owner/native footprint is followed.
"""
import argparse
import hashlib
import sqlite3
from pathlib import Path
import migrate as m
import native_certificates as c

RAW = '3af36baa112a022b79b4c8834b0523e38be747a5072d7acdd8e17ea450184ec5'
IMPACT = '233ed29ac945b5d608c315e8d465f6e893f12e6058ce624ebbf5941174f7217e'
PHYSICS = '347cfc063db44696a2ca523d13dd7a7b1aa919915332706bcd0ff62257cce3c9'
APPEND = '81ed45bd6a73b779cffc0fa5693c8b05fd4975cc87cbfedf251e25f3c2029334'


def reference(path):
    return {'path': str(path.resolve()), 'sha256': m.sha(path)}


def rows(db, sql, args=()):
    cursor = db.execute(sql, args)
    names = [item[0] for item in cursor.description]
    return [dict(zip(names, row)) for row in cursor]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--plan', type=Path, default=m.DEFAULT_PLAN)
    parser.add_argument('--out', type=Path, required=True)
    a = parser.parse_args()
    m.require(not a.out.exists(), 'Use a fresh read-only impact output')
    full = a.plan / 'full'
    paths = {name: full / filename for name, filename in (
        ('impact', 'post-activation-new-wide-impact.json'),
        ('physics', 'post-activation-new-wide-physics.json'),
        ('strictAppend', 'post-activation-native-export-lineage.json'))}
    for name, expected in (('impact', IMPACT), ('physics', PHYSICS), ('strictAppend', APPEND)):
        m.require(m.sha(paths[name]) == expected, 'Bound finite input changed: ' + name)
    impact, physics, append = (m.read(paths[name]) for name in ('impact', 'physics', 'strictAppend'))
    wanted = {row['key']: row for row in impact['rows']}
    m.require(len(wanted) == impact['actualUnsafeCompositions'] == 27 and impact['actualPlacements'] == 28 and impact['rawSha256'] == RAW, 'The exact finite wide input changed')
    physical = {m.canonical(row['state']): row for row in physics['states']}
    db = sqlite3.connect((a.plan.resolve() / 'plan.sqlite').as_uri() + '?mode=ro', uri=True)
    store = context = None
    try:
        db.execute('PRAGMA query_only=ON'); db.execute('PRAGMA cache_size=-2048'); db.execute('PRAGMA temp_store=FILE'); db.execute('BEGIN')
        generation = m.meta(db, 'compositionGeneration'); fragments = m.meta(db, 'poseFragments')
        m.require(generation and generation['compositionsSha256'] == m.meta(db, 'compositionsSha256') == m.sha(a.plan / 'compositions.jsonl') == RAW, 'Await unchanged completed raw generation')
        m.require(not m.meta(db, 'applyStarted') and not m.meta(db, 'frozenInputs') and not db.execute('SELECT 1 FROM placements WHERE type_index IS NOT NULL LIMIT 1').fetchone(), 'Read this finite impact before catalog import/apply')
        m.require(append['status'] == 'PASS' and append['afterFragmentsSha256'] == fragments['sha256'] and all(append.get(key) is True for key in ('posesUnchanged', 'fragmentsUnchanged', 'existingNativeRowsUnchanged', 'existingNativeVisualFragmentsUnchanged')), 'Current exports lack their exact additive lineage')
        store = sqlite3.connect(Path(fragments['path']).resolve().as_uri() + '?mode=ro', uri=True)
        store.execute('PRAGMA query_only=ON'); store.execute('PRAGMA cache_size=-2048'); store.execute('BEGIN')
        models, compositions = {}, {}
        for key, item in wanted.items():
            saved = db.execute('SELECT data FROM compositions WHERE key=?', (key,)).fetchone()
            m.require(saved, 'Bound composition disappeared: ' + key)
            comp = m.json.loads(saved[0]); state = m.canonical(item['state'])
            m.require(comp.get('baseState') == item['state'] and comp.get('baseVisualMode', 'native') == item['mode'] == 'native', 'Bound wide base/route changed')
            compositions[key] = comp
            if state in models: continue
            native = store.execute('SELECT data FROM native WHERE state=?', (state,)).fetchone()
            m.require(native and state in physical and hashlib.sha256(native[0].encode()).hexdigest() == physical[state]['rowSha256'], 'Exact native physical/model row differs')
            data = m.json.loads(native[0]); clipped = []
            m.require(data.get('nativeVisualComplete') is True, 'A real complete native model is required')
            for x, y, z, digest, encoded in store.execute('SELECT x,y,z,digest,data FROM native_fragments WHERE state=? ORDER BY x,y,z', (state,)):
                cell = m.json.loads(encoded)
                m.require(m.digest(cell) == digest and all(-1e-6 <= vertex[axis] <= 1.000001 for quad in cell.get('quads', []) for vertex in quad['vertices'] for axis in range(3)), 'Missing/corrupt or uncontained exact clipped geometry')
                clipped.append({'cell': [x, y, z], 'digest': digest, 'quads': len(cell.get('quads', []))})
            m.require(clipped, 'Wide model has no already-exported clipped cells')
            models[state] = {'state': item['state'], 'nativeRowSha256': physical[state]['rowSha256'], 'physical': physical[state], 'fragments': clipped}
        root_rows = rows(db, 'SELECT * FROM placements WHERE composition IN (' + ','.join('?' for _ in wanted) + ') ORDER BY dim,x,y,z', tuple(wanted))
        m.require(len(root_rows) == 28 and all(sum(row['composition'] == key for row in root_rows) == item['placements'] for key, item in wanted.items()), 'The actual bound 28 root placements changed')
        roots, incoming = [], {}
        for row in root_rows:
            state = m.canonical(wanted[row['composition']]['state']); root = [row[k] for k in ('x', 'y', 'z')]
            roots.append({'placement': row, 'composition': compositions[row['composition']], 'state': m.json.loads(state), 'nativeFragments': models[state]['fragments']})
            incoming.setdefault((row['dim'], *root), [])
            for fragment in models[state]['fragments']:
                point = (row['dim'], *(root[i] + fragment['cell'][i] for i in range(3)))
                incoming.setdefault(point, []).append({'root': root, 'state': m.json.loads(state), 'cell': fragment['cell'], 'fragmentDigest': fragment['digest']})
        points, blockers = [], []
        for point, sources in sorted(incoming.items()):
            dim, x, y, z = point; at = (x, y, z)
            placement = rows(db, 'SELECT * FROM placements WHERE dim=? AND x=? AND y=? AND z=?', point)
            snapshot = {'dimension': dim, 'position': list(at), 'incoming': sources, 'placements': placement}
            for table in ('cell_queue', 'bridge_cells', 'protected_cells', 'whole_cells', 'native_visual_roots', 'native_visual_fragments'):
                snapshot[table] = rows(db, 'SELECT * FROM ' + table + ' WHERE dim=? AND x=? AND y=? AND z=?', point)
            if placement:
                snapshot['sourceState'] = m.json.loads(placement[0]['source_state'])
                saved = db.execute('SELECT data FROM compositions WHERE key=?', (placement[0]['composition'],)).fetchone()
                snapshot['composition'] = m.json.loads(saved[0]) if saved else None
                snapshot['blockEntityObservation'] = 'NOT_REDECODED_EXISTING_PLACEMENT_SOURCE_STATE'
            else:
                if context is None:
                    c.attach_source_journal_original(m.module_api(), db)
                    context = c.SourceVisualReservations(m.module_api(), db, store)
                state, be, proof = context.source_point(dim, at)
                snapshot.update(sourceState=state, sourceChunk=proof, hasBlockEntity=be is not None,
                                blockEntityOuterKeys=sorted(be) if be is not None else [], blockEntityObservation='EXACT_CACHED_SOURCE_NO_EXISTING_PLACEMENT')
                blockers.append({'dimension': dim, 'position': list(at), 'kind': 'new_destination_requires_exact_base_and_occupancy_admission', 'hasBlockEntity': be is not None})
            for table in ('protected_cells', 'whole_cells'):
                if snapshot[table]: blockers.append({'dimension': dim, 'position': list(at), 'kind': table + '_contact', 'rows': snapshot[table]})
            points.append(snapshot)
        m.require(db.total_changes == store.total_changes == 0, 'Read-only impact modified a database')
        report = {'schemaVersion': 1, 'status': 'READ_ONLY_EXACT_WIDE_NATIVE_TRANSFER_IMPACT_NOT_ADMISSION', 'sourceBinding': m.meta(db, 'binding'), 'compositionGeneration': generation, 'inputs': {key: reference(path) for key, path in paths.items()}, 'rawSha256': RAW, 'fragmentBinding': fragments, 'roots': roots, 'models': list(models.values()), 'points': points, 'blockers': blockers, 'counts': {'roots': len(roots), 'uniqueStates': len(models), 'affectedCoordinates': len(points), 'existingPlacements': sum(bool(p['placements']) for p in points), 'newDestinations': sum(not p['placements'] for p in points), 'bridges': sum(bool(p['bridge_cells']) for p in points), 'protectedContacts': sum(bool(p['protected_cells']) for p in points), 'wholeContacts': sum(bool(p['whole_cells']) for p in points)}, 'databaseWrites': 0, 'worldFilesRead': 0, 'mapWrites': 0, 'ownerFootprintsFollowed': 0, 'producer': reference(Path(__file__))}
        a.out.parent.mkdir(parents=True, exist_ok=True); m.atomic_json(a.out, report)
        print(m.canonical({'status': report['status'], 'out': reference(a.out), 'counts': report['counts']}))
    finally:
        if context is not None: context.close()
        if store is not None: store.close()
        db.close()


if __name__ == '__main__':
    main()
