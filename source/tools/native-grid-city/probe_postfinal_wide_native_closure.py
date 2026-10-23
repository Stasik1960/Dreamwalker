"""One root-run finite native-wide fixed point and original-journal contact read.

Reuses the completed impact. Follows only exact native-state clipped offsets,
never any owner footprint. The existing source-local journal is read once.
"""
import argparse
import hashlib
import sqlite3
from collections import deque
from pathlib import Path
import migrate as m
import native_certificates as c

IMPACT = 'e2d081073054fcd15f1342377eaf6b9efa95a096476905f1bb23b0012d139425'
RAW = '3af36baa112a022b79b4c8834b0523e38be747a5072d7acdd8e17ea450184ec5'
GATE = '5bf80db39e36abf0ccd20a9b711d05d175e4a39e87cec6b590873aa4bc4bad66'


def ref(path): return {'path': str(path.resolve()), 'sha256': m.sha(path)}


def rows(db, sql, args=()):
    cursor = db.execute(sql, args); names = [item[0] for item in cursor.description]
    return [dict(zip(names, value)) for value in cursor]


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--plan', type=Path, default=m.DEFAULT_PLAN)
    p.add_argument('--out', type=Path, required=True)
    a = p.parse_args(); full = a.plan / 'full'
    impact_path = full / 'postfinal-wide-native-transfer-impact.json'
    gate_path = full / 'native-functions-post-activation-final.json'
    classification_path = m.PROJECT / 'tools/native-grid/classification.json'
    m.require(not a.out.exists() and m.sha(impact_path) == IMPACT and m.sha(gate_path) == GATE, 'Fresh output/exact prior impact and function gate required')
    impact, gate = m.read(impact_path), m.read(gate_path)
    decisions = {m.canonical(row['state']): row for row in gate['states']}
    m.require(gate['status'] == 'PASS' and len(decisions) == len(gate['states']) == 5402, 'Exact 5402 function gate changed')
    protected = set(m.read(classification_path)['protectedRegistryIds'])
    db = sqlite3.connect((a.plan.resolve() / 'plan.sqlite').as_uri() + '?mode=ro', uri=True)
    store = context = None
    try:
        db.execute('PRAGMA query_only=ON'); db.execute('PRAGMA cache_size=-2048'); db.execute('PRAGMA temp_store=FILE'); db.execute('BEGIN')
        head = m.meta(db, 'sourceLocalRetentionJournal'); binding = m.meta(db, 'binding')
        m.require(head and head['events'] == 187 and binding == impact['sourceBinding'] == gate['sourceBinding'] and m.meta(db, 'compositionGeneration') == impact['compositionGeneration'] and m.sha(a.plan / 'compositions.jsonl') == RAW, 'The same stopped generation/head187 is required')
        fragment_binding = m.meta(db, 'poseFragments'); m.require(fragment_binding == impact['fragmentBinding'], 'Exact export binding changed')
        store = sqlite3.connect(Path(fragment_binding['path']).resolve().as_uri() + '?mode=ro', uri=True)
        store.execute('PRAGMA query_only=ON'); store.execute('PRAGMA cache_size=-2048'); store.execute('BEGIN')
        points = {(row['dimension'], *row['position']): dict(row) for row in impact['points']}
        pending = deque(points); visited = set(); models = {}; wide_roots = {}; blockers = []
        def source_context():
            nonlocal context
            if context is None:
                c.attach_source_journal_original(m.module_api(), db)
                context = c.SourceVisualReservations(m.module_api(), db, store)
            return context
        while pending:
            point = pending.popleft()
            if point in visited: continue
            visited.add(point); row = points[point]; comp = row.get('composition')
            # The two approved protected contacts remain completely original;
            # no native activation or background is proposed at those points.
            if row.get('protected_cells') or row.get('whole_cells'): continue
            base = comp.get('baseState') if row['placements'] and comp is not None else row['sourceState']
            if base is None: continue
            key = m.canonical(base); decision = decisions.get(key)
            if decision is None or decision['decision'] != 'static_base':
                if not row['placements']:
                    blockers.append({'dimension': point[0], 'position': list(point[1:]), 'kind': 'new_base_requires_function_or_existing_BE_bridge_admission', 'state': base, 'decision': decision['decision'] if decision else None, 'hasBlockEntity': row.get('hasBlockEntity')})
                continue
            if base['registryId'] in protected:
                blockers.append({'dimension': point[0], 'position': list(point[1:]), 'kind': 'protected_registry_base', 'state': base}); continue
            if key not in models:
                saved = store.execute('SELECT data FROM native WHERE state=?', (key,)).fetchone()
                if not saved:
                    models[key] = {'state': base, 'exists': False}; continue
                native = m.json.loads(saved[0]); wide = any(v[axis] < -1e-6 or v[axis] > 1.000001 for q in native.get('quads', []) for v in q['vertices'] for axis in range(3))
                cells = []
                if wide:
                    for x, y, z, digest, data in store.execute('SELECT x,y,z,digest,data FROM native_fragments WHERE state=? ORDER BY x,y,z', (key,)):
                        m.require(m.digest(m.json.loads(data)) == digest, 'Native clipping row changed')
                        cells.append({'cell': [x, y, z], 'digest': digest})
                models[key] = {'state': base, 'exists': True, 'nativeRowSha256': hashlib.sha256(saved[0].encode()).hexdigest(), 'nativeVisualComplete': native.get('nativeVisualComplete', True), 'wide': wide, 'collision': native.get('collision'), 'outline': native.get('outline'), 'clippedCells': cells}
            model = models[key]
            if not model['exists']:
                blockers.append({'dimension': point[0], 'position': list(point[1:]), 'kind': 'missing_exact_native_export', 'state': base}); continue
            if not model['nativeVisualComplete']:
                if not row['placements'] or not comp or comp.get('baseVisualMode') not in ('substrateFragments', 'nativePlusSubstrateFragments'):
                    blockers.append({'dimension': point[0], 'position': list(point[1:]), 'kind': 'physics_only_without_explicit_visual_route', 'state': base})
                continue
            if not model['wide']: continue
            if not model['clippedCells']:
                blockers.append({'dimension': point[0], 'position': list(point[1:]), 'kind': 'wide_native_missing_clips', 'state': base}); continue
            existing = rows(db, 'SELECT * FROM native_visual_roots WHERE dim=? AND x=? AND y=? AND z=?', point)
            wide_roots[point] = {'dimension': point[0], 'root': list(point[1:]), 'state': base, 'existingNativeVisualRoots': existing, 'clippedCells': model['clippedCells']}
            for fragment in model['clippedCells']:
                target = (point[0], *(point[i+1] + fragment['cell'][i] for i in range(3)))
                if target in points: continue
                if len(points) >= 4096:
                    blockers.append({'kind': 'finite_native_point_bound_exceeded', 'at': list(target)}); pending.clear(); break
                placement = rows(db, 'SELECT * FROM placements WHERE dim=? AND x=? AND y=? AND z=?', target)
                added = {'dimension': target[0], 'position': list(target[1:]), 'placements': placement, 'incomingClosureFrom': {'root': list(point[1:]), 'state': base, **fragment}}
                if placement:
                    added['sourceState'] = m.json.loads(placement[0]['source_state'])
                    saved = db.execute('SELECT data FROM compositions WHERE key=?', (placement[0]['composition'],)).fetchone()
                    added['composition'] = m.json.loads(saved[0]) if saved else None
                else:
                    state, be, proof = source_context().source_point(target[0], target[1:])
                    added.update(sourceState=state, sourceChunk=proof, hasBlockEntity=be is not None, blockEntityOuterKeys=sorted(be) if be is not None else [])
                for table in ('cell_queue', 'bridge_cells', 'protected_cells', 'whole_cells', 'native_visual_roots', 'native_visual_fragments'):
                    added[table] = rows(db, 'SELECT * FROM ' + table + ' WHERE dim=? AND x=? AND y=? AND z=?', target)
                points[target] = added; pending.append(target)
        # No original source/world validator is run: compare the finite points
        # only to the bound committed snapshots in one journal pass.
        contacts = []; previous = None; events = zones = domains = 0
        by_dimension = {}
        for point in points: by_dimension.setdefault(point[0], set()).add(point[1:])
        for sequence, encoded, digest in db.execute('SELECT sequence,data,digest FROM source_local_retentions ORDER BY sequence'):
            event = m.json.loads(encoded); events += 1
            m.require(sequence == events and m.digest(event) == digest and event['previous'] == previous and event['sourceBinding'] == binding, 'Committed source-local journal chain changed')
            previous = {'schemaVersion': 1, 'events': sequence, 'head': digest, 'sourceBinding': binding}
            zone = event.get('sourceZone')
            if zone:
                zones += 1; low, high = zone['minimum'], zone['maximum']
                for at in by_dimension.get(zone['dimension'], ()):
                    if all(low[i] <= at[i] <= high[i] for i in range(3)):
                        contacts.append({'sequence': sequence, 'eventDigest': digest, 'kind': 'sourceZone', 'dimension': zone['dimension'], 'position': list(at), 'minimum': low, 'maximum': high})
            for domain in event['originalDomains']:
                domains += 1; obj = domain['object']; candidates = by_dimension.get(obj['dim'], set())
                for member in domain['domain']:
                    if tuple(member['position']) in candidates:
                        contacts.append({'sequence': sequence, 'eventDigest': digest, 'kind': 'originalDomain', 'dimension': obj['dim'], 'position': member['position'], 'uuid': obj['uuid'], 'pose': obj['pose'], 'domainDigest': m.digest(domain), 'sourceState': member.get('sourceState')})
            for member in event.get('originalNativeCells', []):
                if tuple(member['position']) in by_dimension.get(member['dimension'], ()):
                    contacts.append({'sequence': sequence, 'eventDigest': digest, 'kind': 'originalNativeCell', 'dimension': member['dimension'], 'position': member['position'], 'uuid': member.get('uuid')})
        m.require(previous == head and db.total_changes == store.total_changes == 0, 'Head/read-only journal proof changed')
        for point, row in points.items():
            for table in ('protected_cells', 'whole_cells'):
                if row.get(table): blockers.append({'dimension': point[0], 'position': list(point[1:]), 'kind': table + '_contact', 'rows': row[table]})
        groups = {}
        for row in impact['points']:
            if row['placements']: continue
            key = m.canonical(row['sourceState']); d = decisions.get(key)
            group = groups.setdefault(key, {'state': row['sourceState'], 'positions': [], 'decision': d.get('decision') if d else None, 'className': d.get('className') if d else None, 'functionEvidence': d.get('functionEvidence') if d else None, 'protectedRegistryId': row['sourceState']['registryId'] in protected})
            group['positions'].append(row['position'])
        report = {'schemaVersion': 1, 'status': 'READ_ONLY_FINITE_NATIVE_CLOSURE_AND_ORIGINAL_CONTACTS_NOT_ADMISSION', 'sourceBinding': binding, 'sourceLocalRetentionJournal': head, 'inputs': {'impact': ref(impact_path), 'functionGate': ref(gate_path), 'classification': ref(classification_path)}, 'initialNewDestinationGroups': list(groups.values()), 'nativeModels': list(models.values()), 'wideRoots': list(wide_roots.values()), 'points': list(points.values()), 'originalJournalContacts': contacts, 'blockers': blockers, 'counts': {'initialPoints': len(impact['points']), 'closurePoints': len(points), 'additionalCoordinates': len(points)-len(impact['points']), 'wideRoots': len(wide_roots), 'journalEvents': events, 'journalZones': zones, 'journalOriginalDomains': domains, 'journalContacts': len(contacts)}, 'databaseWrites': 0, 'worldFilesRead': 0, 'ownerFootprintsFollowed': 0, 'producer': ref(Path(__file__))}
        a.out.parent.mkdir(parents=True, exist_ok=True); m.atomic_json(a.out, report)
        print(m.canonical({'status': report['status'], 'out': ref(a.out), 'counts': report['counts']}))
    finally:
        if context is not None: context.close()
        if store is not None: store.close()
        db.close()


if __name__ == '__main__': main()
