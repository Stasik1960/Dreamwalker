"""Finite completed-plan visual transfer in the existing clipped grid format.

Only actual preflight failures and passive native-base visual destinations seed
the workset. No owner graph, original section, native queue replay, world write,
new bridge, runtime format or physical source export is introduced.
"""
import argparse
import copy
import os
import sqlite3
from pathlib import Path

import migrate as m
import native_certificates as c
import native_resume as n
import retain_postfinal_original_sections as shared


def ref(path):
    return {'path': str(m.checked(path)), 'sha256': m.sha(path)}


def checked(path, digest):
    result = ref(path)
    m.require(result['sha256'] == digest, 'Explicit finite visual input changed')
    return result, m.read(path)


def omission_key(row):
    return m.canonical({key: row[key] for key in
                        ('dimension', 'position', 'root', 'state', 'cell', 'fragmentDigest', 'quadCount')})


def state_key(state):
    return m.canonical({'registryId': state['registryId'], 'properties': state.get('properties', {})})


def permission_authorities(permission):
    legacy = permission['status'] == 'USER_AUTHORIZED_EXACT_TWO_VISUAL_POLYGON_OMISSIONS'
    m.require(permission['schemaVersion'] == 1 and (legacy or
              permission['status'] == 'USER_AUTHORIZED_EXACT_VISUAL_POLYGON_OMISSIONS') and
              permission['additionalVisualOmissionsAuthorized'] is False and
              0 < len(permission['allowedOmissions']) <= 4096 and
              all(type(row['quadCount']) is int and row['quadCount'] > 0 for row in permission['allowedOmissions']) and
              all(value is True for value in permission['preserve'].values()),
              'Only exact explicitly authorized visual fragments may be omitted')
    refs = [permission['userAuthority'], permission['impact']]
    if legacy:
        m.require(len(permission['allowedOmissions']) == 2 and all(row['quadCount'] == 1 for row in permission['allowedOmissions']),
                  'The historical two-polygon permission changed its exact scope')
    else:
        prior = c.bound(m.module_api(), permission['priorPermission'])
        proposal = c.bound(m.module_api(), permission['additionalProposal'])
        m.require(prior['status'] == 'USER_AUTHORIZED_EXACT_TWO_VISUAL_POLYGON_OMISSIONS' and
                  prior['sourceBinding'] == permission['sourceBinding'] and prior['preserve'] == permission['preserve'] and
                  prior['impact'] == permission['impact'] and prior['additionalVisualOmissionsAuthorized'] is False and
                  proposal['kind'] == 'FINITE_ADDITIONAL_VISUAL_OMISSION_PROPOSAL' and
                  proposal['preserveAllDataV12WholeObjects'] is True and
                  all(proposal['currentPermission'][key] == permission['priorPermission'][key] for key in ('path', 'sha256')) and
                  permission['userAuthority'] == permission['additionalAuthority'],
                  'The additional human permission lost its exact prior/proposal/source authority')
        combined = prior['allowedOmissions'] + proposal['fragments']
        m.require(len(combined) == len(permission['allowedOmissions']) == permission['authorizedFragmentCount'] and
                  {omission_key(row) for row in combined} == {omission_key(row) for row in permission['allowedOmissions']} and
                  sum(row['quadCount'] for row in permission['allowedOmissions']) == permission['authorizedQuadCount'],
                  'The allowed omissions differ from the exact authorized prior/proposal union')
        refs += [permission['priorPermission'], permission['additionalProposal'], permission['additionalAuthority'],
                 prior['userAuthority'], prior['impact']]
    for value in refs:
        m.require(m.sha(value['path']) == value['sha256'], 'Exact user omission authority/evidence changed')
    return refs


def source_snapshot(context, dim, at):
    state, be, chunk = context.source_point(dim, at)
    return {'state': state, 'beDigest': context.source_be_digest(dim, at, be, chunk),
            'hasBlockEntity': be is not None, 'chunk': chunk}


def native_model(exports, state, allow_missing=False):
    key = state_key(state)
    saved = exports.execute('SELECT data FROM native WHERE state=?', (key,)).fetchone()
    m.require(saved, 'Exact actual native base physics is missing: ' + key)
    native = m.json.loads(saved[0])
    fragments = []
    for x, y, z, digest, encoded in exports.execute(
            'SELECT x,y,z,digest,data FROM native_fragments WHERE state=? ORDER BY x,y,z', (key,)):
        data = m.json.loads(encoded)
        m.require(m.digest(data) == digest, 'Exact native fragment digest changed')
        for quad in data['quads']:
            m.require(all(-1e-6 <= vertex[i] <= 1.000001 for vertex in quad['vertices'] for i in range(3)),
                      'Native visual fragment is not clipped to its destination cell')
        fragments.append({'cell': [x, y, z], 'digest': digest, 'quadCount': len(data['quads'])})
    complete = native.get('nativeVisualComplete', True)
    contained = all(-1e-6 <= vertex[i] <= 1.000001
                    for quad in native.get('quads', []) for vertex in quad['vertices'] for i in range(3))
    m.require(len(fragments) <= 4096 and (allow_missing or fragments or complete is True and contained),
              'Complete exact native visual fragment coverage is missing/bounded: ' +
              m.canonical({'state': state, 'nativeVisualComplete': complete, 'fragmentCount': len(fragments)}))
    return {'state': state, 'nativeRowSha256': m.hashlib.sha256(saved[0].encode()).hexdigest(),
            'native': native, 'fragments': fragments}


def original_scopes(db):
    """Read accepted finite journals once; never refresh old source domains."""
    zones, cells, hashes = [], set(), []
    head = m.meta(db, 'sourceLocalRetentionJournal')
    previous = None
    for sequence, encoded, digest in db.execute('SELECT sequence,data,digest FROM source_local_retentions ORDER BY sequence'):
        event = m.json.loads(encoded)
        m.require(sequence == len(hashes) + 1 and m.digest(event) == digest and event['previous'] == previous and
                  event['sourceBinding'] == m.meta(db, 'binding'), 'Accepted original-journal prefix changed')
        hashes.append(digest)
        zone = event.get('sourceZone')
        if zone:
            zones.append(zone)
        for domain in event['originalDomains']:
            cells.update((domain['object']['dim'], *row['position']) for row in domain['domain'])
        cells.update((row['dimension'], *row['position']) for row in event.get('originalNativeCells', []))
        previous = {'schemaVersion': 1, 'events': sequence, 'head': digest, 'sourceBinding': event['sourceBinding']}
    m.require(previous == head, 'Accepted original-journal head changed')
    for dim, _, encoded, digest in db.execute('SELECT * FROM source_visual_domains'):
        domain = m.json.loads(encoded)
        m.require(m.digest(domain) == digest, 'Accepted source-owned domain digest changed')
        cells.update((dim, *row['position']) for row in domain['domain'])
    for row in m.meta(db, 'nativeRetainedComponents') or []:
        cells.update((row['dimension'], *at) for at in row['mustRemainOriginalCells'])
    return zones, cells, {'head': head, 'eventDigests': hashes}


def original_contact(dim, at, zones, cells):
    return (dim, *at) in cells or any(zone['dimension'] == dim and
        all(zone['minimum'][i] <= at[i] <= zone['maximum'][i] for i in range(3)) for zone in zones)


def retained_contribution_root(context, dim, at, owner, state, policy):
    """Keep an untouched original owner/data while retaining its passive base."""
    db, uid = context.db, owner['uuid']
    root = tuple(owner[key] for key in ('x', 'y', 'z'))
    m.require(owner['kind'] == 'static' and root != tuple(at) and
              state.get('properties', {}).get('compact') == 'true' and policy.get('className') and
              policy.get('functionEvidence') and policy.get('decision') == 'static_base',
              'Retained contribution is not an exact passive compact non-root base')
    function = c.bound(m.module_api(), policy['functionEvidence'])
    m.require(function['status'] == 'PASS' and function['sourceBinding'] == m.meta(db, 'binding') and
              any(state_key(row['state']) == state_key(state) and row['decision'] == 'static_base' and
                  row.get('className') == policy['className'] for row in function['states']),
              'Retained compact base lacks its exact bound passive source-class proof')
    for table in ('selected_objects', 'cleanup_owners', 'activated_native', 'fragments', 'substrate_fragments'):
        hint = ' INDEXED BY fragment_owner' if table == 'fragments' else ' INDEXED BY substrate_owner' if table == 'substrate_fragments' else ''
        m.require(not db.execute('SELECT 1 FROM ' + table + hint + ' WHERE dim=? AND uuid=? LIMIT 1', (dim, uid)).fetchone(),
                  'Retained passive contribution owner has conversion/cleanup work: ' + table)
    point = c.point(m.module_api(), db, dim, root)
    root_state, be, _ = context.source_point(dim, root)
    root_composition = None
    if any(point[key] for key in ('queue', 'placements', 'fragments', 'substrate_fragments',
                                 'native_visual_roots', 'native_visual_fragments', 'whole_cells', 'bridge_cells')):
        m.require(point['queue'] == [{'processed': 1}] and len(point['placements']) == 1 and
                  len(point['bridge_cells']) == 1 and not any(point[key] for key in
                      ('substrate_fragments', 'native_visual_roots', 'native_visual_fragments', 'whole_cells')),
                  'Retained contribution root lacks a completed unchanged data bridge')
        bridge, placement = point['bridge_cells'][0], point['placements'][0]
        encoded = db.execute('SELECT data FROM compositions WHERE key=?', (placement['composition'],)).fetchone()
        root_composition = m.json.loads(encoded[0]) if encoded else None
        safety = m.meta(db, 'plannedBeSafety')
        m.require(be and 'resident' in be and m.owner_uuid(be['resident']) == uid and
                  bridge['retained_resident'] == 1 and m.json.loads(bridge['host_state']) == root_state and
                  m.json.loads(placement['source_state']) == root_state and root_composition and
                  root_composition.get('wrapper') == 'NativeGridBase' and root_composition.get('baseState') == m.AIR and
                  safety and safety.get('status') == 'PASS' and
                  m.meta(db, 'compositionGeneration').get('plannedBeSafety') == safety,
                  'Retained contribution root bridge changed its original resident/host or accepted representation')
    stored = {table: n.row_dict(db, 'SELECT * FROM old.' + table +
              ' WHERE dim=? AND x=? AND y=? AND z=? AND uuid=?', (dim, *root, uid))
              for table in ('stored_contributions', 'members')}
    owners = {m.owner_uuid(m.compound(item)['owner']) for item in m.be_owners(be)} if be else set()
    if be and 'resident' in be:
        owners.add(m.owner_uuid(be['resident']))
    m.require(uid in owners or any(stored.values()), 'Retained contribution lost its actual source-root data authority')
    return {'object': owner, 'root': list(root), 'source': source_snapshot(context, dim, root),
            'point': point, 'rootComposition': root_composition, 'ownRootSourceRows': stored, 'passivePolicy': policy}


def validate_plain(context, dim, at, before, decisions, exports, zones, originals, policies):
    m.require(not before['hasBlockEntity'], 'New visual destination has source BlockEntity data')
    state = before['state']
    m.require(not original_contact(dim, at, zones, originals), 'Visual destination intersects an accepted original domain/section')
    m.require(state['registryId'] not in context.classification().get('protectedRegistryIds', []),
              'Visual destination is an original protected v12 block')
    m.require(decisions.get(m.canonical(state), {}).get('decision') == 'static_base',
              'New actual source state lacks exact static-base function permission: ' + m.canonical(state))
    saved = exports.execute('SELECT data FROM native WHERE state=?', (m.canonical(state),)).fetchone()
    m.require(saved, 'New actual destination lacks exact native physics')
    native = m.json.loads(saved[0])
    m.require(native.get('fluid') in (None, 'empty', 'minecraft:empty'), 'New visual destination is not dry')
    for field in ('collision', 'outline', 'culling', 'climbing'):
        for box in native.get(field, []):
            m.require(len(box) == 6 and all(-1e-7 <= value <= 1.0000001 for value in box) and
                      all(box[i] < box[i + 3] for i in range(3)), 'New native physics is outside the existing cell format')
    # Saved data/retained whole roots are service authority, not empty visuals.
    refs = {table: n.row_dict(context.db, 'SELECT uuid FROM old.' + table +
            ' WHERE dim=? AND x=? AND y=? AND z=?', (dim, *at)) for table in ('stored_contributions', 'members')}
    retained = []
    for uid in sorted({row['uuid'] for rows in refs.values() for row in rows}):
        rows = n.row_dict(context.db, 'SELECT * FROM objects WHERE dim=? AND uuid=?', (dim, uid))
        m.require(len(rows) == 1, 'New visual destination source owner identity is missing/ambiguous')
        if rows[0]['kind'] not in ('whole', 'preserve') and context.db.execute(
                'SELECT 1 FROM selected_objects WHERE dim=? AND uuid=?', (dim, uid)).fetchone():
            continue
        m.require(not refs['members'], 'New visual destination contains actual retained source member data')
        retained.append(retained_contribution_root(context, dim, at, rows[0], state, policies.get(m.canonical(state), {})))
    return retained


def assemble(context, dim, at, old, activated_roots, legacy_cells):
    """Keep every old ingredient/physics field, add only explicit visual refs."""
    db = context.db
    if old is None:
        shared.recompose(context, dim, at)
        placement = n.row_dict(db, 'SELECT * FROM placements WHERE dim=? AND x=? AND y=? AND z=?', (dim, *at))[0]
        old = m.json.loads(db.execute('SELECT data FROM compositions WHERE key=?', (placement['composition'],)).fetchone()[0])
        actual, _, _ = context.source_point(dim, at)
        m.require(old.get('baseState') == actual or actual['registryId'] in ('minecraft:air', 'minecraft:cave_air', 'minecraft:void_air'),
                  'New destination must preserve its complete actual native substrate')
    comp = copy.deepcopy(old)
    pose_refs = comp.get('substrateFragments', [])
    pose_known = {m.canonical(row) for row in pose_refs}
    for pose, dx, dy, dz in db.execute(
            'SELECT pose,dx,dy,dz FROM substrate_fragments WHERE dim=? AND x=? AND y=? AND z=? ORDER BY pose,dx,dy,dz', (dim, *at)):
        row = {'pose': pose, 'cell': [dx, dy, dz]}
        if m.canonical(row) not in pose_known:
            pose_refs.append(row)
            pose_known.add(m.canonical(row))
    refs = comp.get('substrateNativeFragments', [])
    known = {m.canonical(row) for row in refs}
    for state, dx, dy, dz in db.execute(
            'SELECT state,dx,dy,dz FROM native_visual_fragments WHERE dim=? AND x=? AND y=? AND z=? ORDER BY state,dx,dy,dz', (dim, *at)):
        row = {'state': m.json.loads(state), 'cell': [dx, dy, dz]}
        if m.canonical(row) not in known:
            refs.append(row)
            known.add(m.canonical(row))
    mode = comp.get('baseVisualMode', 'native')
    legacy_base = False
    if (dim, *at) in legacy_cells:
        base = comp.get('baseState')
        saved_native = context.exports.execute('SELECT data FROM native WHERE state=?', (state_key(base),)).fetchone() if base else None
        legacy_base = base is None or saved_native and not m.json.loads(saved_native[0]).get('nativeVisualComplete', True)
    if (dim, *at) in activated_roots or legacy_base:
        comp['baseVisualMode'] = 'substrateFragments'
    elif mode == 'native':
        comp['baseVisualMode'] = 'nativePlusSubstrateFragments'
    comp['substrateFragments'] = pose_refs
    comp['substrateNativeFragments'] = refs
    # Native visual transfers contribute no source collision/outline/optics.
    for key in ('baseState', 'fragments', 'optics', 'fluid', 'localFunction', 'wrapper', 'carrierPredicates'):
        m.require(comp.get(key) == old.get(key), 'Visual transfer changed an existing physical/function ingredient')
    key = 'cell_' + m.digest(comp)
    db.execute('INSERT OR IGNORE INTO compositions VALUES(?,?,NULL)', (key, m.canonical(comp)))
    db.execute('UPDATE placements SET composition=?,type_index=NULL WHERE dim=? AND x=? AND y=? AND z=?', (key, dim, *at))
    return key


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--plan', type=Path, default=m.DEFAULT_PLAN)
    for name in ('preflight', 'permission', 'append-proof', 'visual-import'):
        parser.add_argument('--' + name, type=Path, required=True)
        parser.add_argument('--' + name + '-sha256', required=True)
    parser.add_argument('--out', type=Path, required=True)
    modes = parser.add_mutually_exclusive_group(required=True)
    modes.add_argument('--preview', action='store_true')
    modes.add_argument('--execute', action='store_true')
    modes.add_argument('--discover-missing-visuals', action='store_true')
    parser.add_argument('--expected-preview-sha256')
    run(parser.parse_args())


def counts(db):
    return {**n.counts(db), **{name: db.execute('SELECT count(*) FROM ' + name).fetchone()[0]
                             for name in ('placements', 'compositions', 'cleanup_owners')}}


def stable(db):
    return {key: m.meta(db, key) for key in ('binding', 'nativeComposeCursor', 'nativeLocalRetention',
            'sourceLocalRetentionJournal', 'sourceVisualReservationJournal', 'nativeResumeSourceBinding',
            'nativeResumeSourceTransition', 'poseFragments', 'nativeFragmentAppendLineage',
            'fullNativeFunctionGate', 'fullNativeFunctionDecisions', 'plannedBeSafety')}


def run(a):
    plan, output = m.checked(a.plan), a.out.resolve()
    evidence, documents = {}, {}
    for name in ('preflight', 'permission', 'append_proof', 'visual_import'):
        evidence[name], documents[name] = checked(getattr(a, name), getattr(a, name + '_sha256'))
    evidence['producer'] = ref(__file__)
    evidence['compositionHelper'] = ref(shared.__file__)
    preflight, permission, append, imported = (documents[name] for name in ('preflight', 'permission', 'append_proof', 'visual_import'))
    m.require(preflight['status'] == 'FAIL' and preflight['problemCount'] == len(preflight['problems']) and
              all(row['kind'] in ('native_visual_outside_single_cell', 'physics_only_native_used_as_visual') for row in preflight['problems']),
              'Only actual native visual/physics-only preflight failures may seed this amendment')
    for index, value in enumerate(permission_authorities(permission)):
        evidence['omissionAuthority' + str(index)] = {'path': value['path'], 'sha256': value['sha256']}
    m.require(append['status'] == 'PASS' and append['mode'] == 'STRICT_ADDITIVE_NATIVE_EXPORT_LINEAGE' and
              all(append[key] is True for key in ('posesUnchanged', 'fragmentsUnchanged',
                  'existingNativeRowsUnchanged', 'existingNativeVisualFragmentsUnchanged')) and
              imported['status'] == 'PASS' and imported['mode'] == 'EXACT_NATIVE_VISUAL_FRAGMENTS_ONLY',
              'Actual strict source-prefix and fragments-only visual import proofs required')
    m.require(imported['physicalAndOpticalFieldsEqual'] is True and imported['newNativeRows'] == 0 and
              all(imported[key] is True for key in ('posesUnchanged', 'fragmentsUnchanged',
                  'existingNativeRowsUnchanged', 'existingNativeVisualFragmentsUnchanged')),
              'The visual-only import must preserve every original physics/source/native row')
    m.require(m.sha(imported['nativeGeometry']['path']) == imported['nativeGeometry']['sha256'], 'Actual full native visual export changed')
    import_refs = append['nativeVisualAppends'] if 'nativeVisualAppends' in append else [append.get('nativeVisualAppend', evidence['visual_import'])]
    m.require(isinstance(import_refs, list) and 0 < len(import_refs) <= 4096,
              'The bound visual-import receipt chain is missing/bounded')
    import_documents = []
    for index, value in enumerate(import_refs):
        reference = {'path': value['path'], 'sha256': value['sha256']}
        document = c.bound(m.module_api(), reference)
        m.require(document['status'] == 'PASS' and document['mode'] == 'EXACT_NATIVE_VISUAL_FRAGMENTS_ONLY' and
                  document['physicalAndOpticalFieldsEqual'] is True and document['newNativeRows'] == 0 and
                  all(document[key] is True for key in ('posesUnchanged', 'fragmentsUnchanged',
                      'existingNativeRowsUnchanged', 'existingNativeVisualFragmentsUnchanged')) and
                  m.sha(document['nativeGeometry']['path']) == document['nativeGeometry']['sha256'],
                  'Every bound visual append must preserve all original physical/source rows')
        evidence['visualImport' + str(index)] = reference
        import_documents.append(document)
    m.require(any(value['path'] == evidence['visual_import']['path'] and
                  value['sha256'] == evidence['visual_import']['sha256'] for value in import_refs),
              'The explicit visual-import input is outside the bound append chain')
    directory = output.parent / (output.stem + '.artifacts')
    directory.mkdir(parents=True, exist_ok=True)
    receipt_path = directory / 'receipt.json'
    db = sqlite3.connect('file:' + (plan / 'plan.sqlite').as_posix() + '?mode=rw', uri=True)
    context = exports = None
    try:
        be_safety_ref = m.meta(db, 'plannedBeSafety')
        m.require(be_safety_ref and be_safety_ref.get('status') == 'PASS',
                  'The completed placement generation needs its accepted BlockEntity planning gate')
        be_safety = c.bound(m.module_api(), be_safety_ref)
        m.require(be_safety['status'] == 'PASS' and be_safety['conflicts'] == [],
                  'The bound completed BlockEntity planning gate did not pass')
        evidence['plannedBeSafety'] = be_safety_ref
        committed = m.meta(db, 'postfinalNativeVisualAmendment')
        if committed and Path(committed['path']) == receipt_path:
            receipt = c.bound(m.module_api(), committed)
            m.require(a.execute and receipt['evidence'] == evidence and stable(db) == receipt['stableState'] and
                      counts(db) == receipt['afterCounts'] and m.meta(db, 'compositionGeneration') == receipt['afterGeneration'] and
                      m.meta(db, 'compositionsSha256') == receipt['afterGeneration']['compositionsSha256'] and
                      m.sha(receipt['stableState']['poseFragments']['path']) == receipt['stableState']['poseFragments']['sha256'],
                      'Committed finite amendment no longer matches exact source/generation/data')
            shared.recover_publication(plan, receipt)
            print(m.canonical({'status': 'PASS_IDEMPOTENT_COMMITTED', 'receipt': committed}))
            return
        generation, _ = shared.preconditions(db, plan, preflight['compositionsSha256'])
        m.require(generation.get('plannedBeSafety') == be_safety_ref,
                  'The current BlockEntity planning gate differs from the completed generation')
        c.active_binding(m.module_api(), db)
        c.attach_source_journal_original(m.module_api(), db)
        before_state, before_counts = stable(db), counts(db)
        m.require(permission['sourceBinding'] == before_state['binding'] and
                  before_state['nativeFragmentAppendLineage'] == evidence['append_proof'] and
                  before_state['poseFragments']['sha256'] == append['afterFragmentsSha256'] ==
                  m.sha(before_state['poseFragments']['path']), 'Actual final source append/user permission binding changed')
        gate = c.bound(m.module_api(), before_state['fullNativeFunctionGate'])
        m.require(gate['status'] == 'PASS' and gate['scope'] == 'FULL_CITY' and gate['sourceBinding'] == before_state['binding'],
                  'Complete actual full-city exact function gate required')
        decisions = {m.canonical(row['state']): {'decision': row['decision'], 'reason': row.get('reason', 'actual_native_function_unproved')}
                     for row in gate['states']}
        policies = {m.canonical(row['state']): row for row in gate['states']}
        m.require(decisions == before_state['fullNativeFunctionDecisions'], 'Imported function decisions differ from their bound complete gate')
        exports = sqlite3.connect('file:' + Path(before_state['poseFragments']['path']).as_posix() + '?mode=ro', uri=True)
        exports.execute('PRAGMA query_only=ON')
        exports.execute('BEGIN')
        context = c.SourceVisualReservations(m.module_api(), db, exports)
        if a.discover_missing_visuals:
            context.classification()  # Verify bound authority outside the admission-only catch.
        models, import_rows = {}, {}
        for document in import_documents:
            for row in document['proofs']:
                key = state_key(row['state'])
                previous = import_rows.get(key)
                m.require(previous is None or (previous['existingNativeRowSha256'] == row['existingNativeRowSha256'] and
                          previous['cells'] == row['cells']), 'Repeated visual append changed the exact native state/model')
                import_rows[key] = row
        def model(state):
            key = state_key(state)
            if key not in models:
                result = native_model(exports, state, allow_missing=a.discover_missing_visuals)
                if not result['native'].get('nativeVisualComplete', True):
                    proof = import_rows.get(key)
                    if a.discover_missing_visuals and proof is None:
                        result['missingActualVisualProof'] = True
                    else:
                        m.require(proof and proof['existingNativeRowSha256'] == result['nativeRowSha256'] and
                                  proof['cells'] == len(result['fragments']) and proof['relativeError'] <= 2e-5 and
                                  set(proof['physicalAndOpticalFieldsEqual']) == {'collision', 'outline', 'culling', 'climbing', 'optics', 'fluid'} and
                                  (proof['hasBlockEntityEvidence'] == 'actual_bound_property_getter_false' or
                                   proof['hasBlockEntityEvidence'] == 'cached_false' and result['native'].get('hasBlockEntity') is False),
                                  'Physics-only base needs the actual complete model and every clipped visual offset')
                models[key] = result
            return models[key]
        problems = {row['key']: row for row in preflight['problems']}
        m.require(0 < len(problems) <= 4096 and len(problems) == len(preflight['problems']), 'Finite distinct preflight seed-key bound exceeded')
        placeholders = ','.join('?' for _ in problems)
        seeds = n.row_dict(db, 'SELECT * FROM placements WHERE composition IN (' + placeholders +
                          ') ORDER BY dim,cx,cz,y,x,z', tuple(problems))
        m.require(seeds and {row['composition'] for row in seeds} == set(problems), 'Actual preflight composition placements changed')
        compositions = {}
        def composition(placement):
            if placement is None:
                return None
            key = placement['composition']
            if a.discover_missing_visuals and key.startswith('whole:'):
                return {'dedicatedWholeRole': key}
            m.require(not key.startswith('whole:'), 'Finite visual transfer cannot alter a dedicated whole role')
            if key not in compositions:
                found = db.execute('SELECT data FROM compositions WHERE key=?', (key,)).fetchone()
                m.require(found, 'Existing placement composition disappeared')
                compositions[key] = m.json.loads(found[0])
            return compositions[key]
        roots, incoming, snapshots, omissions, missing_roots, blocked_contacts = {}, {}, {}, {}, {}, {}
        legacy_roots, legacy_incoming, legacy_models = {}, {}, {}
        missing_functions, missing_physics, legacy_dependency_issues, legacy_edges, discovery_budget = {}, {}, {}, {}, []
        allowed = {omission_key(row): row for row in permission['allowedOmissions']}
        m.require(len(allowed) == len(permission['allowedOmissions']), 'Duplicate exact omission identities')
        zones, original_cells, journal_prefix = original_scopes(db)
        def snapshot(dim, at):
            token = (dim, *at)
            if token not in snapshots:
                before = c.point(m.module_api(), db, dim, at)
                m.require(len(before['placements']) <= 1 and before['queue'] in ([], [{'processed': 1}]), 'Finite destination has pending/ambiguous work')
                snapshots[token] = {'point': before, 'source': source_snapshot(context, dim, at),
                                    'composition': composition(before['placements'][0] if before['placements'] else None)}
            return snapshots[token]
        def remember_missing(dim, at, state, info):
            missing_roots[(dim, *at)] = {'dimension': dim, 'position': list(at), 'state': state,
                                        'nativeRowSha256': info['nativeRowSha256'],
                                        'nativeVisualComplete': info['native'].get('nativeVisualComplete', True),
                                        'fragmentCount': len(info['fragments']),
                                        'actualCompleteVisualProofMissing': bool(info.get('missingActualVisualProof'))}
            m.require(len(roots) + len(missing_roots) <= 4096 and len(snapshots) <= 32768,
                      'Finite discovered native visual input bound exceeded')
        def record_legacy_issue(dim, at, state, resolved, error):
            expected = ('A consumed native helper belongs to a retained/protected local object:',
                        'Saved native recipe references an unrecorded synthetic root:',
                        'An automatically retained original native cannot be activated again',
                        'The exact retained legacy C root/member cannot be replaced or transferred',
                        'Legacy native SourcePose disappeared',
                        'Legacy native visual SourcePose is not in the existing exact cache',
                        'Legacy native transfer needs every cached SourcePose cell including its root',
                        'Legacy native transfer overlaps selected/cleanup ordinary owner work',
                        'Legacy native source root/member recipe no longer matches its synthetic identity',
                        'Actual original native helpers extend outside the complete cached visual transfer',
                        'Existing legacy activation has partial/unexpected own visual work',
                        'Finite combined native visual root bound exceeded before writes',
                        'Finite full legacy/native visual destination bound exceeded',
                        'Original native source helper candidate bound exceeded',
                        'UNKNOWN source occupancy: immutable cached chunk absent; no world fallback')
            if not str(error).startswith(expected):
                raise error
            legacy_dependency_issues[(dim, *at)] = {'dimension': dim, 'position': list(at),
                                                   'state': state, 'reason': str(error),
                                                   'resolvedRoots': [list(row) for row in resolved]}
            if str(error).startswith(('Finite ', 'Original native source helper candidate bound exceeded')):
                discovery_budget.append(str(error))
        def remember_contact(dim, at, saved, reason):
            source_rows = {table: n.row_dict(db, 'SELECT * FROM old.' + table +
                           ' WHERE dim=? AND x=? AND y=? AND z=?', (dim, *at))
                           for table in ('stored_contributions', 'members')}
            owners = []
            for uid in sorted({row['uuid'] for rows in source_rows.values() for row in rows}):
                owners.append({'uuid': uid,
                               'objects': n.row_dict(db, 'SELECT * FROM objects WHERE dim=? AND uuid=?', (dim, uid)),
                               'selected': n.row_dict(db, 'SELECT * FROM selected_objects WHERE dim=? AND uuid=?', (dim, uid)),
                               'cleanup': n.row_dict(db, 'SELECT * FROM cleanup_owners WHERE dim=? AND uuid=?', (dim, uid))})
            blocked_contacts[(dim, *at)] = {'dimension': dim, 'position': list(at), 'reason': reason,
                                           'source': saved['source'], 'beforePoint': saved['point'],
                                           'composition': saved['composition'], 'sourceOwnerRows': source_rows,
                                           'sourceOwners': owners,
                                           'incomingVisualFragments': list(incoming.get((dim, *at), {}).values()),
                                           'incomingSourcePoseFragments': list(legacy_incoming.get((dim, *at), {}).values())}
            # Only exact already-cached catalog dependencies may extend this
            # read-only frontier beyond a failed destination admission.
            state = (saved['composition'] or {}).get('baseState') if saved['point']['placements'] else saved['source']['state']
            if state and state['registryId'] not in ('minecraft:air', 'minecraft:cave_air', 'minecraft:void_air'):
                _, overlays = m.legacy_native_catalog()
                if state['registryId'].startswith('bloodborne_dw:static_architecture_') or state['registryId'] in overlays:
                    if discovery_budget:
                        return
                    resolved = []
                    try:
                        # Diagnostic only: missing function/physics must not hide
                        # already-cached catalog dependencies from the batch.
                        resolved = m.native_roots_at(db, dim, at, state, None)
                        legacy_edges[(dim, *at)] = {'dimension': dim, 'position': list(at), 'state': state,
                            'resolvedRoots': [list(row) for row in resolved],
                            'incomingNativeVisualFragments': list(incoming.get((dim, *at), {}).values()),
                            'incomingSourcePoseFragments': list(legacy_incoming.get((dim, *at), {}).values())}
                        for uid, pose, rx, ry, rz in resolved:
                            add_legacy(dim, uid, pose, (rx, ry, rz))
                    except RuntimeError as error:
                        record_legacy_issue(dim, at, state, resolved, error)
                    return
                raw = exports.execute('SELECT data FROM native WHERE state=?', (state_key(state),)).fetchone()
                if raw:
                    native = m.json.loads(raw[0])
                    wide = any(vertex[i] < -1e-6 or vertex[i] > 1.000001
                               for quad in native.get('quads', []) for vertex in quad['vertices'] for i in range(3))
                    if wide or not native.get('nativeVisualComplete', True):
                        info = model(state)
                        if not info['fragments'] or info.get('missingActualVisualProof'):
                            remember_missing(dim, at, state, info)
        def add_legacy(dim, uid, pose, root):
            identity = (dim, uid)
            if identity in legacy_roots:
                m.require(legacy_roots[identity]['object']['pose'] == pose, 'A legacy native identity changed its exact pose')
                return
            m.require(len(roots) + len(legacy_roots) + len(missing_roots) < 4096,
                      'Finite combined native visual root bound exceeded before writes')
            n.check_native_activation(m.module_api(), db, dim, uid)
            objects = n.row_dict(db, 'SELECT * FROM objects WHERE dim=? AND uuid=?', identity)
            m.require(len(objects) == 1 and objects[0]['kind'] == 'substrate' and objects[0]['pose'] == pose and
                      tuple(objects[0][key] for key in ('x', 'y', 'z')) == tuple(root), 'Legacy activation lost its exact source root')
            obj = objects[0]
            m.require(not db.execute('SELECT 1 FROM selected_objects WHERE dim=? AND uuid=?', identity).fetchone() and
                      not db.execute('SELECT 1 FROM cleanup_owners WHERE dim=? AND uuid=?', identity).fetchone() and
                      not db.execute('SELECT 1 FROM fragments INDEXED BY fragment_owner WHERE dim=? AND uuid=? LIMIT 1', identity).fetchone(),
                      'Legacy native transfer overlaps selected/cleanup ordinary owner work')
            encoded = db.execute('SELECT data FROM poses WHERE key=?', (pose,)).fetchone()
            m.require(encoded, 'Legacy native SourcePose disappeared')
            source_pose = m.json.loads(encoded[0])
            if pose not in legacy_models:
                exported = exports.execute('SELECT source,proof FROM poses WHERE key=?', (pose,)).fetchone()
                m.require(exported, 'Legacy native visual SourcePose is not in the existing exact cache')
                provenance, proof = map(m.json.loads, exported)
                m.require(provenance['key'] == pose and
                          all(provenance['sourcePose'].get(key) == value for key, value in source_pose.items()) and
                          proof['key'] == pose and proof['relativeError'] <= 2e-5,
                          'Legacy full visual cache is not the exact installed SourcePose')
                clips = []
                for dx, dy, dz, digest, data in exports.execute(
                        'SELECT x,y,z,digest,data FROM fragments WHERE pose=? ORDER BY x,y,z LIMIT 4097', (pose,)):
                    fragment = m.json.loads(data)
                    m.require(m.digest(fragment) == digest and all(-1e-6 <= vertex[i] <= 1.000001
                              for quad in fragment['quads'] for vertex in quad['vertices'] for i in range(3)),
                              'Existing SourcePose visual clip lost its digest/unit bounds')
                    clips.append({'cell': [dx, dy, dz], 'digest': digest, 'quadCount': len(fragment['quads'])})
                cells = {tuple(row['cell']) for row in clips}
                planned = {tuple(row) for row in db.execute('SELECT dx,dy,dz FROM pose_cells WHERE pose=?', (pose,))}
                m.require(0 < len(clips) <= 4096 and len(clips) == proof['cells'] and cells == planned and (0, 0, 0) in cells,
                          'Legacy native transfer needs every cached SourcePose cell including its root')
                legacy_models[pose] = {'source': provenance, 'proof': proof, 'clips': clips}
            info = legacy_models[pose]
            root_saved = snapshot(dim, tuple(root))
            m.require(not root_saved['source']['hasBlockEntity'] and any(row['uuid'] == uid and row['member'] and
                      tuple(row['root']) == tuple(root) for row in context.native_origins(dim, tuple(root), root_saved['source']['state'])),
                      'Legacy native source root/member recipe no longer matches its synthetic identity')
            members, member_proof = context.source_native_members(dim, obj, source_pose)
            full_cells = {tuple(root[i] + row['cell'][i] for i in range(3)) for row in info['clips']}
            m.require(members <= full_cells, 'Actual original native helpers extend outside the complete cached visual transfer')
            was_active = bool(db.execute('SELECT 1 FROM activated_native WHERE dim=? AND uuid=?', identity).fetchone())
            old_work = n.row_dict(db, 'SELECT * FROM substrate_fragments INDEXED BY substrate_owner WHERE dim=? AND uuid=?', identity)
            expected = [{'dim': dim, 'x': at[0], 'y': at[1], 'z': at[2], 'uuid': uid, 'pose': pose,
                         'dx': row['cell'][0], 'dy': row['cell'][1], 'dz': row['cell'][2]}
                        for row in info['clips'] for at in [tuple(root[i] + row['cell'][i] for i in range(3))]]
            m.require((was_active and sorted(map(m.canonical, old_work)) == sorted(map(m.canonical, expected))) or
                      (not was_active and not old_work), 'Existing legacy activation has partial/unexpected own visual work')
            legacy_roots[identity] = {'object': obj, 'root': list(root), 'poseDataSha256': m.hashlib.sha256(encoded[0].encode()).hexdigest(),
                                      'visualProof': info['proof'], 'actualSourceMembers': member_proof, 'wasActivated': was_active}
            for row in expected:
                token = (dim, row['x'], row['y'], row['z'])
                legacy_incoming.setdefault(token, {})[uid] = row
                snapshot(dim, token[1:])
                m.require(len(snapshots) <= 32768, 'Finite full legacy/native visual destination bound exceeded')
        def add_root(dim, at, state):
            token = (dim, *at)
            if token in roots:
                m.require(roots[token]['state'] == state, 'A native visual root has two source states')
                return
            if token in missing_roots:
                m.require(missing_roots[token]['state'] == state, 'A missing native visual root has two source states')
                return
            if a.discover_missing_visuals and discovery_budget:
                return
            if a.discover_missing_visuals and len(roots) + len(legacy_roots) + len(missing_roots) >= 4096:
                record_legacy_issue(dim, at, state, [], RuntimeError('Finite combined native visual root bound exceeded before writes'))
                return
            m.require(len(roots) + len(legacy_roots) + len(missing_roots) < 4096, 'Finite native visual root bound exceeded before writes')
            saved = snapshot(dim, at)
            raw_native = exports.execute('SELECT data FROM native WHERE state=?', (state_key(state),)).fetchone()
            actual_native = m.json.loads(raw_native[0]) if raw_native else None
            if actual_native and not actual_native.get('nativeVisualComplete', True) and state_key(state) not in import_rows and state == saved['source']['state']:
                _, overlays = m.legacy_native_catalog()
                catalog_state = state['registryId'].startswith('bloodborne_dw:static_architecture_') or state['registryId'] in overlays
                if not a.discover_missing_visuals or catalog_state:
                    resolved = []
                    try:
                        resolved = m.native_roots_at(db, dim, at, state, actual_native)
                        if resolved:
                            if a.discover_missing_visuals:
                                legacy_edges[token] = {'dimension': dim, 'position': list(at), 'state': state,
                                    'resolvedRoots': [list(row) for row in resolved],
                                    'incomingNativeVisualFragments': list(incoming.get(token, {}).values()),
                                    'incomingSourcePoseFragments': list(legacy_incoming.get(token, {}).values())}
                            for uid, pose, rx, ry, rz in resolved:
                                add_legacy(dim, uid, pose, (rx, ry, rz))
                            return
                    except RuntimeError as error:
                        if not a.discover_missing_visuals:
                            raise
                        record_legacy_issue(dim, at, state, resolved, error)
                        return
            info = model(state)
            m.require(saved['composition'] is None or saved['composition'].get('baseState') == state,
                      'Activate only the actual composed native base, never a carrier/AIR source substitute')
            if a.discover_missing_visuals and (not info['fragments'] or info.get('missingActualVisualProof')):
                remember_missing(dim, at, state, info)
                return
            m.require(info['fragments'], 'Native visual root activation requires clipped fragments: ' +
                      m.canonical({'state': state, 'nativeVisualComplete': info['native'].get('nativeVisualComplete', True),
                                   'fragmentCount': len(info['fragments'])}))
            roots[token] = {'state': state, 'modelRowSha256': info['nativeRowSha256']}
            for fragment in info['fragments']:
                target = tuple(at[i] + fragment['cell'][i] for i in range(3))
                record = {'dimension': dim, 'position': list(target), 'root': list(at), 'state': state,
                          'cell': fragment['cell'], 'fragmentDigest': fragment['digest'], 'quadCount': fragment['quadCount']}
                key = omission_key(record)
                if key in allowed:
                    omissions[key] = record
                    snapshot(dim, target)
                    continue
                token_at = (dim, *target)
                refs = incoming.setdefault(token_at, {})
                refs[(dim, *at)] = record
                snapshot(dim, target)
                m.require(len(snapshots) <= 32768, 'Finite native visual destination bound exceeded before writes')
        for seed in seeds:
            at = tuple(seed[key] for key in ('x', 'y', 'z'))
            actual = composition(seed)
            m.require(actual['baseState'] == problems[seed['composition']]['baseState'], 'Exact preflight base witness changed')
            add_root(seed['dim'], at, actual['baseState'])
        examined = set()
        while (set(incoming) | set(legacy_incoming)) - examined:
            for token in sorted((set(incoming) | set(legacy_incoming)) - examined, key=lambda row: (row[0], row[1] >> 4, row[3] >> 4, row[2], row[1], row[3])):
                examined.add(token)
                dim, *at = token
                saved = snapshot(dim, tuple(at))
                before = saved['point']
                if a.discover_missing_visuals and not before['placements']:
                    state = saved['source']['state']
                    key = state_key(state)
                    witness = {'dimension': dim, 'position': list(at)}
                    if decisions.get(m.canonical(state), {}).get('decision') != 'static_base':
                        missing_functions.setdefault(key, {'state': state, 'currentDecision': decisions.get(m.canonical(state)),
                                                           'permissionIssue': 'MISSING_EXACT_FUNCTION_DECISION' if m.canonical(state) not in decisions else 'EXISTING_NON_STATIC_FUNCTION_POLICY',
                                                           'positions': []})['positions'].append(witness)
                    if not exports.execute('SELECT 1 FROM native WHERE state=?', (key,)).fetchone():
                        missing_physics.setdefault(key, {'state': state, 'positions': []})['positions'].append(witness)
                try:
                    m.require(not before['whole_cells'] and not original_contact(dim, at, zones, original_cells),
                              'Finite native visual transfer intersects an original/whole source domain: ' + m.canonical(token))
                    if not before['placements']:
                        m.require(not before['protected_cells'], 'New visual destination has protected source reservation: ' + m.canonical(token))
                        retained = validate_plain(context, dim, tuple(at), saved['source'], decisions, exports, zones, original_cells, policies)
                        if retained:
                            saved['retainedContributionRoots'] = retained
                        base = saved['source']['state']
                        actual = None
                    else:
                        # The completed-generation gate already admitted this
                        # exact placement, including selected decorative BE cleanup.
                        actual = saved['composition']
                        base = actual.get('baseState')
                except RuntimeError as error:
                    if not a.discover_missing_visuals:
                        raise
                    remember_contact(dim, tuple(at), saved, str(error))
                    continue
                if base and base['registryId'] not in ('minecraft:air', 'minecraft:cave_air', 'minecraft:void_air') and token not in roots:
                    # Existing explicit source transfers keep their whole route;
                    # only a native-rendered base needs another visual root.
                    if actual is None or actual.get('baseVisualMode', 'native') in ('native', 'nativePlusSubstrateFragments'):
                        raw = exports.execute('SELECT data FROM native WHERE state=?', (state_key(base),)).fetchone()
                        m.require(raw, 'An actual native-rendered destination lacks exact physics/model data')
                        native = m.json.loads(raw[0])
                        wide = any(vertex[i] < -1e-6 or vertex[i] > 1.000001
                                   for quad in native.get('quads', []) for vertex in quad['vertices'] for i in range(3))
                        if wide or not native.get('nativeVisualComplete', True):
                            add_root(dim, tuple(at), base)
        if a.discover_missing_visuals:
            states = {state_key(row['state']): row['state'] for row in missing_roots.values()}
            needs_export = {state_key(row['state']) for row in missing_roots.values()
                            if row['nativeVisualComplete'] is not True}
            contacts_blocked = bool(blocked_contacts or legacy_dependency_issues or discovery_budget)
            report = {'schemaVersion': 1,
                      'status': ('BLOCKED_CONTACTS_AND_MISSING_VISUAL_INPUTS' if missing_roots else 'BLOCKED_CONTACTS')
                                if contacts_blocked else ('BLOCKED_MISSING_VISUAL_INPUTS' if missing_roots else
                                                          'PASS_VISUAL_INPUT_DISCOVERY_NOT_COMMITTED'),
                      'kind': 'FINITE_POSTFINAL_NATIVE_VISUAL_INPUT_DISCOVERY', 'evidence': evidence,
                      'sourceBinding': before_state['binding'], 'sourceFiles': c.source_files(m.module_api()),
                      'stableState': before_state, 'compositionGeneration': generation, 'beforeCounts': before_counts,
                      'seedKeys': sorted(problems), 'seedPlacementCount': len(seeds),
                      'knownNativeRootCount': len(roots), 'legacyNativeRootCount': len(legacy_roots), 'reachedCells': len(snapshots),
                      'missingRootCount': len(missing_roots), 'missingStateCount': len(states),
                      'missingRoots': [missing_roots[key] for key in sorted(missing_roots)],
                      'blockedContactCount': len(blocked_contacts),
                      'blockedContacts': [blocked_contacts[key] for key in sorted(blocked_contacts)],
                      'missingFunctionStateCount': len(missing_functions),
                      'missingFunctionStates': [missing_functions[key] for key in sorted(missing_functions)],
                      'missingNativePhysicsStateCount': len(missing_physics),
                      'missingNativePhysicsStates': [missing_physics[key] for key in sorted(missing_physics)],
                      'nativePhysicsStates': [{**missing_physics[key]['state'], 'physicsOnly': True} for key in sorted(missing_physics)],
                      'legacyDependencyIssueCount': len(legacy_dependency_issues),
                      'legacyDependencyIssues': [legacy_dependency_issues[key] for key in sorted(legacy_dependency_issues)],
                      'legacyActivationProvenance': [legacy_edges[key] for key in sorted(legacy_edges)],
                      'missingSourcePoseInputs': [row for row in legacy_dependency_issues.values() if row['reason'].startswith(
                          ('Legacy native SourcePose disappeared', 'Legacy native visual SourcePose is not in the existing exact cache',
                           'Legacy native transfer needs every cached SourcePose cell including its root'))],
                      'frontierBoundReached': bool(discovery_budget), 'frontierBoundReasons': discovery_budget,
                      'inputFrontierComplete': not discovery_budget and not missing_roots and not legacy_dependency_issues,
                      'nativeStates': [{**states[key], 'physicsOnly': False} for key in sorted(needs_export)],
                      'completeNativeStatesMissingClips': [states[key] for key in sorted(set(states) - needs_export)],
                      'authorizedOmissionsSeen': [omissions[key] for key in sorted(omissions)],
                      'unresolvedRootsActivated': 0,
                      'diagnosticExpansion': 'EXACT_ALREADY_CACHED_CATALOG_DEPENDENCIES_ONLY',
                      'blockedCatalogDependenciesExplored': bool(legacy_edges),
                      'ownerGraphTraversal': False, 'planWrites': 0, 'worldReads': 0, 'mapWrites': 0}
            m.require(stable(db) == before_state and counts(db) == before_counts,
                      'Completed inputs changed during finite visual discovery')
            for value in evidence.values():
                m.require(m.sha(value['path']) == value['sha256'], 'Finite source/evidence changed during discovery')
            c.immutable_json(m.module_api(), output, report)
            db.rollback()
            print(m.canonical({'status': report['status'], 'report': ref(output),
                               'missingRoots': len(missing_roots), 'missingStates': len(states),
                               'blockedContacts': len(blocked_contacts), 'nativeStates': len(needs_export),
                               'missingFunctions': len(missing_functions), 'missingNativePhysics': len(missing_physics),
                               'legacyDependencyIssues': len(legacy_dependency_issues),
                               'knownRoots': len(roots), 'reachedCells': len(snapshots)}))
            return
        m.require(set(omissions) == set(allowed), 'The actual finite workset must match exactly all listed authorized omissions')
        for saved in snapshots.values():
            for owner in saved.get('retainedContributionRoots', []):
                token = (owner['object']['dim'], *owner['root'])
                m.require(token not in roots and token not in incoming and token not in legacy_incoming,
                          'A retained contribution root is inside the proposed visual amendment')
        # Full beforeimages/source data are ready before the first plan mutation.
        db.execute('PRAGMA synchronous=FULL')
        db.execute('BEGIN IMMEDIATE')
        shared.preconditions(db, plan, preflight['compositionsSha256'])
        m.require(stable(db) == before_state and counts(db) == before_counts, 'Completed plan/source inputs changed before mutation')
        for token, saved in snapshots.items():
            dim, *at = token
            m.require(c.point(m.module_api(), db, dim, at) == saved['point'], 'Finite affected beforeimage changed')
        for (dim, uid), record in legacy_roots.items():
            pose = db.execute('SELECT data FROM poses WHERE key=?', (record['object']['pose'],)).fetchone()
            m.require(n.row_dict(db, 'SELECT * FROM objects WHERE dim=? AND uuid=?', (dim, uid)) == [record['object']] and
                      pose and m.hashlib.sha256(pose[0].encode()).hexdigest() == record['poseDataSha256'] and
                      bool(db.execute('SELECT 1 FROM activated_native WHERE dim=? AND uuid=?', (dim, uid)).fetchone()) == record['wasActivated'],
                      'Legacy source/activation changed before the finite amendment')
            n.check_native_activation(m.module_api(), db, dim, uid)
            db.execute('INSERT OR IGNORE INTO activated_native VALUES(?,?)', (dim, uid))
        for token, records in legacy_incoming.items():
            for record in records.values():
                row = tuple(record[key] for key in ('dim', 'x', 'y', 'z', 'uuid', 'pose', 'dx', 'dy', 'dz'))
                db.execute('INSERT OR IGNORE INTO substrate_fragments VALUES(?,?,?,?,?,?,?,?,?)', row)
                found = db.execute('SELECT * FROM substrate_fragments WHERE dim=? AND x=? AND y=? AND z=? AND uuid=?', row[:5]).fetchone()
                m.require(found == row, 'The full legacy visual transfer changed a source reference')
        for token, root in sorted(roots.items()):
            dim, *at = token
            expected = {'dim': dim, 'x': at[0], 'y': at[1], 'z': at[2], 'state': m.canonical(root['state'])}
            before = snapshots[token]['point']['native_visual_roots']
            m.require(not before or before == [expected], 'Existing native visual root has another source state')
            db.execute('INSERT OR IGNORE INTO native_visual_roots VALUES(?,?,?,?,?)', (dim, *at, expected['state']))
        for token, records in sorted(incoming.items()):
            dim, *at = token
            for record in records.values():
                root, offset = record['root'], record['cell']
                row = (dim, *at, *root, m.canonical(record['state']), *offset)
                db.execute('INSERT OR IGNORE INTO native_visual_fragments VALUES(?,?,?,?,?,?,?,?,?,?,?)', row)
                found = db.execute('SELECT * FROM native_visual_fragments WHERE dim=? AND x=? AND y=? AND z=? AND rx=? AND ry=? AND rz=?',
                                   (dim, *at, *root)).fetchone()
                m.require(found == row, 'Exact clipped-native source/reference changed')
        changed = set(incoming) | set(roots) | set(legacy_incoming)
        for token in sorted(changed, key=lambda row: (row[0], row[1] >> 4, row[3] >> 4, row[2], row[1], row[3])):
            dim, *at = token
            saved = snapshots[token]
            if not saved['point']['queue']:
                db.execute('INSERT INTO cell_queue VALUES(?,?,?,?,1)', (dim, *at))
            assemble(context, dim, tuple(at), saved['composition'], roots, legacy_incoming)
        for key, record in omissions.items():
            token = (record['dimension'], *record['position'])
            if token not in changed:
                m.require(c.point(m.module_api(), db, record['dimension'], record['position']) == snapshots[token]['point'],
                          'An omitted protected visual target was changed')
            m.require(not db.execute('SELECT 1 FROM native_visual_fragments WHERE dim=? AND x=? AND y=? AND z=? AND rx=? AND ry=? AND rz=?',
                      (record['dimension'], *record['position'], *record['root'])).fetchone(), 'An authorized omitted fragment remained in the plan')
        for token, saved in snapshots.items():
            dim, *at = token
            m.require(source_snapshot(context, dim, tuple(at)) == saved['source'], 'Affected source state/typed BE/data changed before commit')
            for owner in saved.get('retainedContributionRoots', []):
                m.require(retained_contribution_root(context, dim, at, owner['object'], saved['source']['state'], owner['passivePolicy']) == owner,
                          'Retained passive contribution root/data/work changed before commit')
            expected_substrate = {m.canonical(row) for row in saved['point']['substrate_fragments']}
            expected_substrate.update(m.canonical(row) for row in legacy_incoming.get(token, {}).values())
            m.require({m.canonical(row) for row in c.point(m.module_api(), db, dim, at)['substrate_fragments']} == expected_substrate,
                      'The finite amendment changed an unrelated original substrate source reference')
            for field in ('fragments', 'whole_cells', 'bridge_cells', 'protected_cells'):
                m.require(c.point(m.module_api(), db, dim, at)[field] == saved['point'][field], 'Visual transfer changed nonvisual source work: ' + field)
        for (dim, _), record in legacy_roots.items():
            root = record['root']
            placement = db.execute('SELECT composition FROM placements WHERE dim=? AND x=? AND y=? AND z=?', (dim, *root)).fetchone()
            composition_after = m.json.loads(db.execute('SELECT data FROM compositions WHERE key=?', (placement[0],)).fetchone()[0]) if placement else None
            m.require(composition_after and composition_after.get('baseVisualMode') == 'substrateFragments',
                      'The full legacy native root model was not suppressed after complete SourcePose transfer')
        m.require(stable(db) == before_state and not db.execute('SELECT 1 FROM cell_queue WHERE processed=0 LIMIT 1').fetchone(),
                  'Visual transfer changed source metadata/journal/cursor or created pending work')
        db.execute("DELETE FROM compositions WHERE key NOT IN (SELECT DISTINCT composition FROM placements WHERE composition NOT LIKE 'whole:%')")
        files = shared.write_artifacts(db, plan, directory)
        generation_after = {**generation, 'compositionsSha256': m.sha(files[0]), 'sourceFragments': before_state['poseFragments'],
                            'postfinalNativeVisualAmendment': {'producer': evidence['producer'], 'evidence': evidence,
                             'previousGeneration': generation, 'originalJournalUnchanged': before_state['sourceLocalRetentionJournal']}}
        preview = {'schemaVersion': 1, 'status': 'POSTFINAL_NATIVE_VISUAL_AMENDMENT_PREVIEW_NOT_COMMITTED',
                   'kind': 'FINITE_POSTFINAL_NATIVE_VISUAL_AMENDMENT', 'evidence': evidence,
                   'sourceBinding': before_state['binding'], 'sourceFiles': c.source_files(m.module_api()),
                   'stableState': before_state, 'beforeGeneration': generation, 'afterGeneration': generation_after,
                   'beforeCounts': before_counts, 'afterCounts': counts(db), 'originalJournalPrefix': journal_prefix,
                   'seedKeys': sorted(problems), 'seedPlacementCount': len(seeds), 'nativeRootCount': len(roots),
                   'legacySourcePoseRootCount': len(legacy_roots), 'legacySourcePoseRoots': list(legacy_roots.values()),
                   'affectedCells': len(changed), 'newCells': sum(not snapshots[token]['point']['placements'] for token in changed),
                   'roots': [{'dimension': token[0], 'position': list(token[1:]), **root} for token, root in sorted(roots.items())],
                   'beforeImages': [{'dimension': token[0], 'position': list(token[1:]), **value} for token, value in sorted(snapshots.items())],
                   'exactOmissions': [omissions[key] for key in sorted(omissions)], 'additionalOmissions': 0,
                   'omissionFragmentCount': len(omissions), 'omissionQuadCount': sum(row['quadCount'] for row in omissions.values()),
                   'existingPhysicalIngredientsUnchanged': True, 'sourceTypedDataUnchanged': True,
                   'compiledCullingEquivalenceClaimed': False, 'mandatoryAffectedCompileCheck': True,
                   'sourcePrefixUnchanged': True, 'ownerGraphTraversal': False, 'processedRowsReset': 0,
                   'nativeQueueReplayed': False, 'worldReads': 0, 'mapWrites': 0, 'gameplayPass': False,
                   'derivedFiles': [{'target': str(plan / name), 'beforeSha256': m.sha(plan / name), 'proposed': ref(path)}
                                    for name, path in zip(('compositions.jsonl', 'native-states-needed.json'), files)]}
        for value in evidence.values():
            m.require(m.sha(value['path']) == value['sha256'], 'Finite source/evidence changed before result')
        if not a.execute:
            c.immutable_json(m.module_api(), output, preview)
            db.rollback()
            print(m.canonical({'status': preview['status'], 'preview': ref(output), 'seedPlacements': len(seeds),
                               'roots': len(roots), 'cells': len(changed), 'newCells': preview['newCells'],
                               'omittedFragments': len(omissions), 'omittedQuads': preview['omissionQuadCount']}))
            return
        m.require(a.expected_preview_sha256 and m.sha(output) == a.expected_preview_sha256 and m.read(output) == preview,
                  'Execute requires exact reviewed preview and unchanged finite affected beforeimages')
        receipt = {**preview, 'status': 'PASS', 'kind': 'COMMITTED_POSTFINAL_NATIVE_VISUAL_AMENDMENT',
                   'preview': ref(output), 'commitAuthority': 'Only matching postfinalNativeVisualAmendment meta commits this receipt'}
        c.immutable_json(m.module_api(), receipt_path, receipt)
        m.meta(db, 'compositionGeneration', generation_after)
        m.meta(db, 'compositionsSha256', generation_after['compositionsSha256'])
        m.meta(db, 'postfinalNativeVisualAmendment', ref(receipt_path))
        db.commit()
        shared.recover_publication(plan, receipt)
        print(m.canonical({'status': 'PASS_COMMITTED', 'receipt': ref(receipt_path), 'cells': len(changed),
                           'omittedFragments': len(omissions), 'omittedQuads': preview['omissionQuadCount']}))
    except BaseException:
        db.rollback()
        raise
    finally:
        if context is not None:
            context.close()
        if exports is not None:
            exports.close()
        db.close()


if __name__ == '__main__':
    main()

