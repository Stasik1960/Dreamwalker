"""Inert, exact-UID preserved-native member/demand probe for a stopped resume.

Root runs this once through Guard. It reads the existing fast owner-member
index and cached source only. No classification, selection, queue or journal is
changed; directly observed incoming owners are identities, not new footprints.
"""
from __future__ import annotations
import argparse, hashlib, importlib.util, json, re, sqlite3, sys, time
from pathlib import Path

sys.dont_write_bytecode = True
SOURCE = Path(__file__).resolve().parents[3]
ROOT = SOURCE.parent
TREE_PROOF_SHA = '7de51902eb54e31e9473fec40274dfb3587f3b67a82065629c9c89b5f0cf6724'
CATALOG_SHA = '3a9d3325e31818484689e2cc4d22fa63c6168338fa721ee3315935ddee9124f2'
TABLES = ('fragments', 'substrate_fragments', 'whole_cells',
          'native_visual_fragments', 'native_visual_roots', 'bridge_cells',
          'protected_cells', 'placements')


def require(value, message):
    if not value:
        raise RuntimeError(message)


def sha(path):
    value = hashlib.sha256()
    with Path(path).open('rb') as stream:
        for block in iter(lambda: stream.read(1 << 20), b''):
            value.update(block)
    return value.hexdigest()


def ref(path):
    return {'path': str(Path(path).resolve()), 'sha256': sha(path)}


def rows(db, query, args=()):
    cursor = db.execute(query, args)
    names = [column[0] for column in cursor.description]
    return [dict(zip(names, row)) for row in cursor]


def records(path, name):
    """Stream the accepted pretty-printed catalogue; never materialize it all."""
    active, item = False, None
    with path.open(encoding='utf-8') as stream:
        for raw in stream:
            line = raw.rstrip('\r\n')
            if not active:
                active = line == '  "' + name + '": ['
                continue
            if line == '  ],':
                return
            if line == '    {':
                item = []
            if item is not None:
                item.append(line)
                if line in ('    },', '    }'):
                    yield json.loads('\n'.join(item).rstrip(','))
                    item = None
    require(active and item is None, 'Unknown accepted catalogue layout')


def same_pose(a, b):
    return (a['registryId'] == b['registryId']
            and a.get('properties', {}) == b.get('properties', {})
            and a.get('shift', [0, 0, 0]) == b.get('shift', [0, 0, 0])
            and a.get('mountY', 0) == b.get('mountY', 0))


def recipe_index(state):
    rid = state['registryId']
    if rid.startswith('bloodborne_dw:static_architecture_'):
        return int(rid.rsplit('_', 1)[1]) * 128 + int(state['properties']['part'])
    return None


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--plan', type=Path, default=ROOT/'research/native-grid-plan')
    parser.add_argument('--failed-run', type=Path, required=True)
    parser.add_argument('--uuid', required=True, help='Exact canonical SQLite UUID string')
    parser.add_argument('--dimension', default='.')
    parser.add_argument('--tree-proof', type=Path, default=ROOT/'research/native-functional-bases/unknown-native-tree-footprints.json')
    parser.add_argument('--out', type=Path, required=True)
    a = parser.parse_args()
    require(not a.out.exists(), 'Preserve immutable output; choose a fresh --out')
    uid = json.dumps(json.loads(a.uuid), separators=(',', ':'))
    require(len(json.loads(uid)) == 4 and all(type(v) is int for v in json.loads(uid)), 'Expected exact four-int UUID')
    run = json.loads(a.failed_run.read_text(encoding='utf-8-sig'))
    log_path = a.failed_run.with_suffix('.log')
    log = log_path.read_text(encoding='utf-8-sig')
    require('A consumed native helper belongs to a retained/protected local object:' in log
            and uid in log, 'Failed log does not name this consumed preserved native')
    sys.path.insert(0, str(SOURCE/'tools/native-grid-city'))
    spec = importlib.util.spec_from_file_location('retained_member_probe_migrate', SOURCE/'tools/native-grid-city/migrate.py')
    m = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = m
    spec.loader.exec_module(m)
    import native_certificates as c
    started = time.monotonic()
    db = sqlite3.connect('file:' + (a.plan/'plan.sqlite').resolve().as_posix() + '?mode=ro', uri=True)
    try:
        db.execute('PRAGMA cache_size=-2048')
        db.execute('PRAGMA temp_store=FILE')
        binding = m.meta(db, 'binding')
        require(binding and binding.get('sourceHashesVerified') and binding.get('originalPlanReadOnly'), 'Verified source binding required')
        old = Path(binding['oldPlan'])/'plan.sqlite'
        db.execute('ATTACH DATABASE ? AS old', ('file:' + old.resolve().as_posix() + '?mode=ro',))
        db.execute('PRAGMA query_only=ON')
        db.execute('BEGIN')
        cache = c.SourceVisualReservations(m, db)
        require(cache.member_index, 'Committed fast source_visual_owner_members index required')
        own = rows(db, 'SELECT * FROM objects WHERE dim=? AND uuid=?', (a.dimension, uid))
        require(len(own) == 1 and own[0]['kind'] == 'preserve', 'Actual preserved object not found')
        obj = own[0]
        encoded = db.execute('SELECT data FROM poses WHERE key=?', (obj['pose'],)).fetchone()
        require(encoded is not None, 'Actual SourcePose missing')
        pose = json.loads(encoded[0])
        root = [obj[k] for k in ('x', 'y', 'z')]
        owned = {name: cache.owner_member_rows(a.dimension, uid, name)
                 for name in ('stored_contributions', 'members')}
        proof = json.loads(a.tree_proof.read_text(encoding='utf-8-sig'))
        require(sha(a.tree_proof) == TREE_PROOF_SHA and proof['status'] == 'PASS', 'Accepted exact tree footprint proof changed')
        matched = [row for row in proof['poses'] if same_pose(row['legacyPose'], pose)]
        require(len(matched) <= 1, 'Ambiguous exact tree footprint authority')
        known = matched[0] if matched else None
        expected = {tuple(root)}
        expected.update(tuple(row[k] for k in ('x', 'y', 'z')) for values in owned.values() for row in values)
        if known:
            expected.update(tuple(root[i] + offset[i] for i in range(3)) for offset in known['cells'])
        require(len(expected) <= 4096, 'Actual source member bound exceeded')
        pending = rows(db, 'SELECT dim,x,y,z FROM cell_queue WHERE processed=0 ORDER BY dim,(x>>4),(z>>4),y,x,z LIMIT 1024')
        pending_indices = {(row['dim'], row['x'], row['y'], row['z']): index for index, row in enumerate(pending)}
        points, needed, native_keys = [], set(), set()
        for at in sorted(expected):
            actual, be, chunk = cache.source_point(a.dimension, at)
            index = recipe_index(actual)
            if index is not None:
                needed.add(index)
            args = (a.dimension, *at)
            demands = {table: rows(db, 'SELECT * FROM ' + table + ' WHERE dim=? AND x=? AND y=? AND z=?', args) for table in TABLES}
            for table in ('fragments', 'substrate_fragments', 'whole_cells'):
                native_keys.update(row['uuid'] for row in demands[table] if row['uuid'] != uid)
            points.append({'position': list(at), 'sourceState': actual,
                           'sourceBE': None if be is None else {'id': m.plain_tag(be.get('id', m.Tag(8, ''))), 'fields': sorted(be), 'digest': cache.be_digest(be)},
                           'sourceChunk': chunk, 'recipeIndex': index,
                           'queue': rows(db, 'SELECT processed FROM cell_queue WHERE dim=? AND x=? AND y=? AND z=?', args),
                           'currentPending1024Index': pending_indices.get(args), 'demands': demands})
        catalog = SOURCE/'src/architecture/resources/bloodborne_dw/static-architecture-v1274.json'
        require(sha(catalog) == CATALOG_SHA, 'Accepted native recipe source changed')
        recipes = {}
        for index, value in enumerate(records(catalog, 'recipes')):
            if index in needed:
                recipes[index] = value
                if len(recipes) == len(needed):
                    break
        require(set(recipes) == needed, 'An exact actual recipe is missing')
        complete = bool(known)
        for point in points:
            recipe = recipes.get(point['recipeIndex'])
            origins = []
            if recipe:
                for part in recipe['fragments']:
                    origin = [point['position'][i] + part['origin'][i] for i in range(3)]
                    origins.append({'legacyPose': part['pose'], 'root': origin,
                                    'uuid': m.synthetic_uuid(a.dimension, origin, part['pose']),
                                    'member': part.get('member', False), 'rawFragment': part})
            point['completeOriginalRecipe'] = recipe
            point['nativeOrigins'] = origins
            point['actualTMemberMatches'] = [origin for origin in origins if origin['uuid'] == uid and origin['root'] == root and origin['member']]
            if known:
                complete = complete and len(point['actualTMemberMatches']) == 1 and point['sourceBE'] is None
        incoming = [{'uuid': other,
                     'object': rows(db, 'SELECT * FROM objects WHERE dim=? AND uuid=?', (a.dimension, other)),
                     'selected': rows(db, 'SELECT * FROM selected_objects WHERE dim=? AND uuid=?', (a.dimension, other)),
                     'ownFootprintRead': False} for other in sorted(native_keys)]
        answer = {'schemaVersion': 1, 'kind': 'EXACT_PRESERVED_NATIVE_MEMBER_DEMAND_PROBE',
                  'status': 'READ_ONLY_EXACT_MEMBERS_COMPLETE' if complete else 'BLOCKED_MISSING_OR_MISMATCHED_RUNTIME_AUTHORITY',
                  'sourceBinding': binding, 'failedRun': ref(a.failed_run), 'failedLog': ref(log_path),
                  'nativeComposeCursor': m.meta(db, 'nativeComposeCursor'),
                  'sourceVisualMemberIndex': cache.member_index, 'object': obj, 'poseData': pose,
                  'poseDataSha256': hashlib.sha256(encoded[0].encode()).hexdigest(), 'root': root,
                  'ownSavedRows': owned, 'runtimeFootprintAuthority': {'proof': ref(a.tree_proof), 'exactRow': known},
                  'runtimeMemberCells': [list(at) for at in sorted(expected)] if complete else None,
                  'queriedMemberCandidateCells': [list(at) for at in sorted(expected)], 'points': points,
                  'directIncomingIdentities': incoming, 'nativeRecipeResource': ref(catalog),
                  'sourceFiles': [ref(Path(__file__)), ref(Path(m.__file__)), ref(Path(c.__file__))],
                  'summary': {'queriedPoints': len(points), 'exactRuntimeAuthorityFound': known is not None,
                              'pendingRuntimePoints': sum(bool(point['queue']) and point['queue'][0]['processed'] == 0 for point in points),
                              'pendingFirst1024MemberCandidates': sum(point['currentPending1024Index'] is not None for point in points),
                              'incomingIdentityCount': len(incoming), 'ownerGraphTraversal': False},
                  'databaseWrites': 0, 'worldFilesRead': 0, 'gameProcessesStarted': 0,
                  'retentionAuthorized': False, 'elapsedSeconds': round(time.monotonic() - started, 3)}
    finally:
        db.close()
    a.out.parent.mkdir(parents=True, exist_ok=True)
    with a.out.open('x', encoding='utf-8', newline='\n') as stream:
        json.dump(answer, stream, ensure_ascii=False, indent=2)
        stream.write('\n')
    print(json.dumps({'status': answer['status'], 'output': ref(a.out), 'summary': answer['summary']}, ensure_ascii=False))


if __name__ == '__main__':
    main()
