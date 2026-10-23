"""Read-only selected/native/retained owner facts; no mutation/admission.

The original selected-owner producer stays byte-identical for its published
results. Native mode adds only the named donor's complete indexed pose cells,
physical envelope, exact saved rows and direct point demands (plus supplied
contact-member points). It never follows another owner's footprint.
Retained mode needs no visual pose_cells and excludes no conversion demand.
"""
from __future__ import annotations
import argparse, base64, hashlib, importlib.util, json, sqlite3, sys, time
from pathlib import Path
sys.dont_write_bytecode = True
import probe_direct_selected_owner_demands as base
import probe_retained_native_member_demands as native


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--mode', choices=('selected', 'native', 'retained'), required=True)
    parser.add_argument('--plan', type=Path, default=base.ROOT/'research/native-grid-plan')
    parser.add_argument('--uuid', required=True)
    parser.add_argument('--dimension', default='.')
    parser.add_argument('--trigger-probe', type=Path)
    parser.add_argument('--target', type=int, nargs=3, help='One actual encountered point; no owner discovery')
    parser.add_argument('--out', type=Path, required=True)
    a = parser.parse_args()
    base.require(base.sha(base.__file__) == 'ef922b0dec955dbb8ccd2237996d188ae328d97d7b8570bfbc02d49e8b0598b3', 'Original selected-owner producer changed')
    if a.mode == 'selected':
        index = sys.argv.index('--mode'); del sys.argv[index:index+2]
        return base.main()
    base.require(not a.out.exists(), 'Preserve immutable output; choose a fresh --out')
    uid = json.dumps(json.loads(a.uuid), separators=(',', ':'))
    base.require(len(json.loads(uid)) == 4 and all(type(v) is int for v in json.loads(uid)), 'Expected exact four-int UUID')
    sys.path.insert(0, str(base.SOURCE/'tools/native-grid-city'))
    spec = importlib.util.spec_from_file_location('native_donor_probe_migrate', base.SOURCE/'tools/native-grid-city/migrate.py')
    m = importlib.util.module_from_spec(spec); sys.modules[spec.name] = m; spec.loader.exec_module(m)
    import native_certificates as c
    started = time.monotonic()
    db = sqlite3.connect('file:' + (a.plan/'plan.sqlite').resolve().as_posix() + '?mode=ro', uri=True)
    try:
        db.execute('PRAGMA cache_size=-2048'); db.execute('PRAGMA temp_store=FILE')
        binding = m.meta(db, 'binding')
        base.require(binding and binding.get('sourceHashesVerified') and binding.get('originalPlanReadOnly'), 'Verified source binding required')
        trigger = None
        if a.trigger_probe:
            trigger = json.loads(a.trigger_probe.read_text(encoding='utf-8-sig'))
            base.require(trigger['sourceBinding'] == binding and any(row['uuid'] == uid for row in trigger['directIncomingIdentities']), 'Not a direct incoming donor in the bound trigger facts')
            base.require(trigger.get('runtimeMemberCells'), 'Contact probe lacks complete exact runtime members')
        db.execute('ATTACH DATABASE ? AS old', ('file:' + (Path(binding['oldPlan'])/'plan.sqlite').resolve().as_posix() + '?mode=ro',))
        db.execute('PRAGMA query_only=ON'); db.execute('BEGIN')
        cache = c.SourceVisualReservations(m, db)
        base.require(cache.member_index, 'Committed fast owner-member index required')
        token = (a.dimension, uid)
        obj = base.rows(db, 'SELECT * FROM objects WHERE dim=? AND uuid=?', token)
        selected = base.rows(db, 'SELECT * FROM selected_objects WHERE dim=? AND uuid=?', token)
        allowed = ('preserve', 'whole') if a.mode == 'retained' else ('substrate',)
        base.require(len(obj) == 1 and obj[0]['kind'] in allowed and not selected, 'Expected exact unselected owner in '+str(allowed))
        obj = obj[0]; root = [obj[k] for k in ('x', 'y', 'z')]
        encoded = db.execute('SELECT data FROM poses WHERE key=?', (obj['pose'],)).fetchone()
        base.require(encoded is not None, 'Exact donor SourcePose missing')
        pose = json.loads(encoded[0]); envelope = cache.envelope(obj['pose'], encoded[0])
        cap, capref, source_spec, specref = cache.authority(pose)
        owned = {name: cache.owner_member_rows(a.dimension, uid, name) for name in ('stored_contributions', 'members')}
        offsets = [] if a.mode == 'retained' else db.execute('SELECT dx,dy,dz FROM pose_cells WHERE pose=? ORDER BY dx,dy,dz LIMIT 4097', (obj['pose'],)).fetchall()
        base.require(a.mode == 'retained' or 0 < len(offsets) <= 4096, 'Native donor has missing/over-budget complete visual footprint')
        saved = {tuple(root)} | {tuple(row[k] for k in ('x', 'y', 'z')) for value in owned.values() for row in value}
        members = saved | {tuple(root[i] + off[i] for i in range(3)) for off in envelope['cells']}
        visual = {tuple(root[i] + off[i] for i in range(3)) for off in offsets}
        contact = {tuple(at) for at in trigger['runtimeMemberCells']} if trigger else set()
        if a.target is not None: contact.add(tuple(a.target))
        positions = members | visual | contact
        base.require(len(members) <= 4096 and len(positions) <= 8192, 'One direct donor union exceeds the finite bound')
        points, needed, incoming = [], set(), set()
        own_substrate = []
        for at in sorted(positions):
            args = (a.dimension, *at); actual, be, chunk = cache.source_point(a.dimension, at)
            index = native.recipe_index(actual)
            if index is not None: needed.add(index)
            demands = {table: base.rows(db, 'SELECT * FROM '+table+' WHERE dim=? AND x=? AND y=? AND z=?', args) for table in base.DEMAND_TABLES}
            own_substrate.extend(row for row in demands['substrate_fragments'] if row['uuid'] == uid)
            residual = {table: [row for row in values if a.mode == 'retained' or table != 'substrate_fragments' or row['uuid'] != uid] for table, values in demands.items()}
            for table in ('fragments', 'substrate_fragments', 'whole_cells'):
                incoming.update(row['uuid'] for row in residual[table] if row['uuid'] != uid)
            be_row = None
            if be is not None:
                raw = m.encode_nbt(m.NbtFile('', m.Tag(10, be)))
                be_row = {'data': m.plain_tag(m.Tag(10, be)), 'encodedNbtBase64': base64.b64encode(raw).decode('ascii'), 'encodedNbtSha256': hashlib.sha256(raw).hexdigest()}
            points.append({'position': list(at), 'inDonorMemberEnvelope': at in members, 'inActualSavedMemberSet': at in saved,
                           'inDonorVisualFootprint': at in visual, 'inContactRuntimeMembers': at in contact,
                           'sourceState': actual, 'sourceBE': be_row, 'sourceChunk': chunk, 'recipeIndex': index,
                           'queue': base.rows(db, 'SELECT processed FROM cell_queue WHERE dim=? AND x=? AND y=? AND z=?', args),
                           'demands': demands, 'afterExcludingOnlyDonorSubstrate': residual,
                           'remainingExplicitDemandCount': sum(len(value) for value in residual.values()),
                           'bridge_cells': base.rows(db, 'SELECT * FROM bridge_cells WHERE dim=? AND x=? AND y=? AND z=?', args),
                           'protected_cells': base.rows(db, 'SELECT * FROM protected_cells WHERE dim=? AND x=? AND y=? AND z=?', args)})
        catalog = base.SOURCE/'src/architecture/resources/bloodborne_dw/static-architecture-v1274.json'
        base.require(base.sha(catalog) == native.CATALOG_SHA, 'Accepted native recipe source changed')
        recipes = {}
        if needed:
            for index, row in enumerate(native.records(catalog, 'recipes')):
                if index in needed:
                    recipes[index] = row
                    if len(recipes) == len(needed): break
        base.require(set(recipes) == needed, 'An exact actual recipe is missing')
        donor_keys = set()
        for point in points:
            recipe = recipes.get(point['recipeIndex']); origins = []
            for part in recipe['fragments'] if recipe else []:
                origin = [point['position'][i] + part['origin'][i] for i in range(3)]
                other = m.synthetic_uuid(a.dimension, origin, part['pose'])
                activated = base.rows(db, 'SELECT * FROM activated_native WHERE dim=? AND uuid=?', (a.dimension, other))
                origins.append({'uuid': other, 'legacyPose': part['pose'], 'root': origin, 'rawFragment': part,
                                'activatedNative': activated, 'foreignActivatedAfterExcludingDonor': bool(activated) and other != uid})
                if other == uid: donor_keys.add(part['pose'])
            point['completeOriginalRecipe'] = recipe; point['nativeRecipeOrigins'] = origins
            point['foreignActivatedAfterExcludingDonor'] = [row for row in origins if row['foreignActivatedAfterExcludingDonor']]
        canonical = [row for row in native.records(catalog, 'poses') if row['key'] in donor_keys]
        activated = base.rows(db, 'SELECT * FROM activated_native WHERE dim=? AND uuid=?', token)
        answer = {'schemaVersion': 1, 'kind': 'EXACT_DIRECT_NATIVE_DONOR_DEMAND_FACTS', 'status': 'SOURCE_FACTS_NOT_ADMISSION',
                  'sourceBinding': binding, 'nativeComposeCursor': m.meta(db, 'nativeComposeCursor'), 'triggerProbe': base.ref(a.trigger_probe) if a.trigger_probe else None,
                  'sourceVisualMemberIndex': cache.member_index, 'object': obj, 'selection': selected, 'root': root,
                  'poseData': pose, 'poseDataSha256': hashlib.sha256(encoded[0].encode()).hexdigest(), 'canonicalLegacyPoses': canonical,
                  'sourceClassCapability': {'reference': capref, 'row': cap}, 'sourceSpec': {'reference': specref, 'data': source_spec},
                  'runtimeFootprintEnvelope': envelope, 'conservativeMemberCells': [list(at) for at in sorted(members)],
                  'actualSavedMemberCells': [list(at) for at in sorted(saved)], 'ownSavedRows': owned,
                  'completeVisualCells': [list(at) for at in sorted(visual)], 'activatedNative': activated,
                  'ownSubstrateFragments': own_substrate, 'nativeWorkLookup': 'Every exact pose_cells coordinate plus root/envelope/saved/contact points; original position PK only',
                  'cleanupOwner': base.rows(db, 'SELECT * FROM cleanup_owners WHERE dim=? AND uuid=?', token), 'points': points,
                  'otherDirectDemandIdentities': [{'uuid': other, 'objectIdentity': base.rows(db, 'SELECT dim,uuid,pose,x,y,z,kind,reason FROM objects WHERE dim=? AND uuid=?', (a.dimension, other)),
                                                  'selectedIdentity': base.rows(db, 'SELECT * FROM selected_objects WHERE dim=? AND uuid=?', (a.dimension, other)), 'ownFootprintRead': False} for other in sorted(incoming)],
                  'mode': a.mode, 'targetPosition': a.target,
                  'targetInConservativeMemberEnvelope': tuple(a.target) in members if a.target is not None else None,
                  'targetInActualSavedMemberSet': tuple(a.target) in saved if a.target is not None else None,
                  'rootSource': next(point for point in points if point['position'] == root),
                  'summary': {'visualCells': len(visual), 'conservativeMembers': len(members), 'contactMembers': len(contact), 'queriedPoints': len(points),
                              'activatedDonor': bool(activated), 'ownSubstrateRows': len(own_substrate),
                              'memberPointsWithRemainingExplicitDemand': sum(point['inDonorMemberEnvelope'] and point['remainingExplicitDemandCount'] > 0 for point in points),
                              'pointsWithForeignActivationAfterExcludingDonor': sum(bool(point['foreignActivatedAfterExcludingDonor']) for point in points),
                              'q0': sum(any(row['processed'] == 0 for row in point['queue']) for point in points), 'q1': sum(any(row['processed'] == 1 for row in point['queue']) for point in points)},
                  'sourceFiles': [base.ref(Path(__file__)), base.ref(Path(base.__file__)), base.ref(Path(native.__file__)), base.ref(Path(m.__file__)), base.ref(Path(c.__file__))],
                  'nativeRecipeResource': base.ref(catalog), 'databaseWrites': 0, 'worldFilesRead': 0, 'ownerGraphTraversal': False,
                  'retentionAuthorized': False, 'elapsedSeconds': round(time.monotonic() - started, 3)}
        if a.mode == 'retained':
            answer['kind'] = 'EXACT_RETAINED_SOURCE_OWNER_FACTS'
            answer['conversionDemandsExcluded'] = []
    finally:
        db.close()
    a.out.parent.mkdir(parents=True, exist_ok=True)
    with a.out.open('x', encoding='utf-8', newline='\n') as stream:
        json.dump(answer, stream, ensure_ascii=False, indent=2); stream.write('\n')
    print(json.dumps({'status': answer['status'], 'output': base.ref(a.out), 'summary': answer['summary']}, ensure_ascii=False))


if __name__ == '__main__':
    main()
