"""Inert exact selected-owner facts for a stopped migration's direct contact.

Root runs via Guard. Reads only this owner's indexed fragments, saved members,
conservative existing runtime envelope and direct point demands. It neither
opens world files nor follows incoming owner footprints or changes the plan.
"""
from __future__ import annotations
import argparse, base64, hashlib, importlib.util, json, sqlite3, sys, time
from pathlib import Path

sys.dont_write_bytecode = True
SOURCE = Path(__file__).resolve().parents[3]
ROOT = SOURCE.parent
DEMAND_TABLES = ('fragments', 'substrate_fragments', 'whole_cells',
                 'native_visual_fragments', 'native_visual_roots', 'placements')


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


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--plan', type=Path, default=ROOT/'research/native-grid-plan')
    parser.add_argument('--uuid', required=True)
    parser.add_argument('--dimension', default='.')
    parser.add_argument('--trigger-probe', type=Path)
    parser.add_argument('--out', type=Path, required=True)
    a = parser.parse_args()
    require(not a.out.exists(), 'Preserve immutable output; choose a fresh --out')
    uid = json.dumps(json.loads(a.uuid), separators=(',', ':'))
    require(len(json.loads(uid)) == 4 and all(type(v) is int for v in json.loads(uid)), 'Expected exact four-int UUID')
    sys.path.insert(0, str(SOURCE/'tools/native-grid-city'))
    spec = importlib.util.spec_from_file_location('direct_owner_probe_migrate', SOURCE/'tools/native-grid-city/migrate.py')
    m = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = m
    spec.loader.exec_module(m)
    import native_certificates as c
    started = time.monotonic()
    db = sqlite3.connect('file:' + (a.plan/'plan.sqlite').resolve().as_posix() + '?mode=ro', uri=True)
    try:
        db.execute('PRAGMA cache_size=-2048'); db.execute('PRAGMA temp_store=FILE')
        binding = m.meta(db, 'binding')
        require(binding and binding.get('sourceHashesVerified') and binding.get('originalPlanReadOnly'), 'Verified source binding required')
        if a.trigger_probe:
            trigger = json.loads(a.trigger_probe.read_text(encoding='utf-8-sig'))
            require(trigger['sourceBinding'] == binding and any(row['uuid'] == uid for row in trigger['directIncomingIdentities']), 'This is not a direct incoming owner in the bound trigger facts')
        db.execute('ATTACH DATABASE ? AS old', ('file:' + (Path(binding['oldPlan'])/'plan.sqlite').resolve().as_posix() + '?mode=ro',))
        db.execute('PRAGMA query_only=ON'); db.execute('BEGIN')
        cache = c.SourceVisualReservations(m, db)
        require(cache.member_index, 'Committed fast owner-member index required')
        token = (a.dimension, uid)
        obj = rows(db, 'SELECT * FROM objects WHERE dim=? AND uuid=?', token)
        selected = rows(db, 'SELECT * FROM selected_objects WHERE dim=? AND uuid=?', token)
        require(len(obj) == len(selected) == 1 and obj[0]['kind'] == selected[0]['kind'] == 'static', 'Expected one exact directly selected static owner')
        obj = obj[0]; root = [obj[k] for k in ('x', 'y', 'z')]
        encoded = db.execute('SELECT data FROM poses WHERE key=?', (obj['pose'],)).fetchone()
        require(encoded is not None, 'Actual SourcePose missing')
        pose = json.loads(encoded[0])
        envelope = cache.envelope(obj['pose'], encoded[0])
        owned = {name: cache.owner_member_rows(a.dimension, uid, name) for name in ('stored_contributions', 'members')}
        fragments = rows(db, 'SELECT * FROM fragments INDEXED BY fragment_owner WHERE dim=? AND uuid=? ORDER BY x,y,z LIMIT 4097', token)
        require(0 < len(fragments) <= 4096, 'Selected static owner has missing/over-budget exact fragments')
        saved = {tuple(root)} | {tuple(row[k] for k in ('x', 'y', 'z')) for value in owned.values() for row in value}
        members = saved | {tuple(root[i] + offset[i] for i in range(3)) for offset in envelope['cells']}
        positions = members | {tuple(row[k] for k in ('x', 'y', 'z')) for row in fragments}
        require(len(members) <= 4096 and len(positions) <= 8192, 'One direct owner point bound exceeded')
        points, foreign = [], set()
        for at in sorted(positions):
            args = (a.dimension, *at)
            actual, be, chunk = cache.source_point(a.dimension, at)
            be_row = None
            if be is not None:
                raw = m.encode_nbt(m.NbtFile('', m.Tag(10, be)))
                be_row = {'data': m.plain_tag(m.Tag(10, be)), 'fields': sorted(be),
                          'encodedNbtBase64': base64.b64encode(raw).decode('ascii'),
                          'encodedNbtSha256': hashlib.sha256(raw).hexdigest()}
            demands = {table: rows(db, 'SELECT * FROM ' + table + ' WHERE dim=? AND x=? AND y=? AND z=?', args) for table in DEMAND_TABLES}
            # The proposed simple local retain removes only this selected D's
            # fragments. It does not silently retract a native/whole component.
            residual = {table: [row for row in value if table != 'fragments' or row['uuid'] != uid] for table, value in demands.items()}
            for table in ('fragments', 'substrate_fragments', 'whole_cells'):
                foreign.update(row['uuid'] for row in residual[table] if row['uuid'] != uid)
            points.append({'position': list(at), 'inConservativeMemberEnvelope': at in members,
                           'inActualSavedMemberSet': at in saved, 'sourceState': actual,
                           'sourceBE': be_row, 'sourceChunk': chunk,
                           'queue': rows(db, 'SELECT processed FROM cell_queue WHERE dim=? AND x=? AND y=? AND z=?', args),
                           'demands': demands, 'afterExcludingOnlyDFragments': residual,
                           'remainingDemandCount': sum(len(value) for value in residual.values()),
                           'bridge_cells': rows(db, 'SELECT * FROM bridge_cells WHERE dim=? AND x=? AND y=? AND z=?', args),
                           'protected_cells': rows(db, 'SELECT * FROM protected_cells WHERE dim=? AND x=? AND y=? AND z=?', args)})
        incoming = [{'uuid': other, 'objectIdentity': rows(db, 'SELECT dim,uuid,pose,x,y,z,kind,reason FROM objects WHERE dim=? AND uuid=?', (a.dimension, other)),
                     'selectedIdentity': rows(db, 'SELECT * FROM selected_objects WHERE dim=? AND uuid=?', (a.dimension, other)), 'ownFootprintRead': False} for other in sorted(foreign)]
        answer = {'schemaVersion': 1, 'kind': 'EXACT_DIRECT_SELECTED_OWNER_DEMAND_FACTS', 'status': 'SOURCE_FACTS_NOT_ADMISSION',
                  'sourceBinding': binding, 'nativeComposeCursor': m.meta(db, 'nativeComposeCursor'),
                  'triggerProbe': ref(a.trigger_probe) if a.trigger_probe else None,
                  'sourceVisualMemberIndex': cache.member_index, 'object': obj, 'selection': selected,
                  'poseData': pose, 'poseDataSha256': hashlib.sha256(encoded[0].encode()).hexdigest(), 'root': root,
                  'runtimeFootprintEnvelope': envelope, 'actualSavedMemberCells': [list(at) for at in sorted(saved)],
                  'conservativeMemberCells': [list(at) for at in sorted(members)], 'ownSavedRows': owned,
                  'completeOwnFragments': fragments, 'activatedNative': rows(db, 'SELECT * FROM activated_native WHERE dim=? AND uuid=?', token),
                  'cleanupOwner': rows(db, 'SELECT * FROM cleanup_owners WHERE dim=? AND uuid=?', token),
                  'points': points, 'otherDirectDemandIdentities': incoming,
                  'summary': {'fragments': len(fragments), 'savedMembers': len(saved), 'conservativeMembers': len(members), 'queriedPoints': len(points),
                              'memberPointsWithOtherDemand': sum(point['inConservativeMemberEnvelope'] and point['remainingDemandCount'] > 0 for point in points),
                              'visualOnlyPointsWithOtherDemand': sum(not point['inConservativeMemberEnvelope'] and point['remainingDemandCount'] > 0 for point in points),
                              'q0': sum(any(row['processed'] == 0 for row in point['queue']) for point in points),
                              'q1': sum(any(row['processed'] == 1 for row in point['queue']) for point in points)},
                  'sourceFiles': [ref(Path(__file__)), ref(Path(m.__file__)), ref(Path(c.__file__))],
                  'databaseWrites': 0, 'worldFilesRead': 0, 'ownerGraphTraversal': False,
                  'retentionAuthorized': False, 'elapsedSeconds': round(time.monotonic() - started, 3)}
    finally:
        db.close()
    a.out.parent.mkdir(parents=True, exist_ok=True)
    with a.out.open('x', encoding='utf-8', newline='\n') as stream:
        json.dump(answer, stream, ensure_ascii=False, indent=2); stream.write('\n')
    print(json.dumps({'status': answer['status'], 'output': ref(a.out), 'summary': answer['summary']}, ensure_ascii=False))


if __name__ == '__main__':
    main()
