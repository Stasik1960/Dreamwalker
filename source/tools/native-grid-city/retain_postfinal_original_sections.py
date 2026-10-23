"""Finite post-compose original sections; root executes, no world/runtime writes.

The four migration consumers remain immutable. Collection/retraction below uses
their original-section workset, with honest completed-plan entry conditions.
Only changed outside-section cells are recomposed; the native queue is not run.
"""
import argparse
import copy
import os
import shutil
import sqlite3
from pathlib import Path

import migrate as m
import native_certificates as c
import native_resume as n


def reference(path):
    return {'path': str(m.checked(path)), 'sha256': m.sha(path)}


def read_bound(path, digest):
    m.require(m.sha(path) == digest, 'Explicit postfinal input changed')
    return m.read(path)


def preconditions(db, plan, expected_raw):
    generation = m.meta(db, 'compositionGeneration')
    cursor = m.meta(db, 'nativeComposeCursor')
    m.require(generation and generation['compositionsSha256'] == expected_raw ==
              m.sha(plan / 'compositions.jsonl') == m.meta(db, 'compositionsSha256'),
              'Completed composition generation/raw binding changed')
    m.require(cursor and cursor['phase'] == 'FINALIZING' and
              not db.execute('SELECT 1 FROM cell_queue WHERE processed=0 LIMIT 1').fetchone(),
              'Postfinal section retention requires completed native work')
    m.require(not m.meta(db, 'applyStarted') and not m.meta(db, 'frozenInputs') and
              not db.execute('SELECT 1 FROM placements WHERE type_index IS NOT NULL LIMIT 1').fetchone() and
              not db.execute("SELECT 1 FROM files WHERE phase='committed' LIMIT 1").fetchone() and
              not db.execute("SELECT 1 FROM regions WHERE phase='committed' LIMIT 1").fetchone(),
              'Postfinal retention is only before mapping, freezing and map writes')
    return copy.deepcopy(generation), copy.deepcopy(cursor)


def recompose(context, dim, at):
    """Same per-cell composition semantics as the frozen compose_native_tail."""
    db = context.db
    args = (dim, *at)
    original, _, _ = context.source_point(dim, at)
    roles = n.row_dict(db, 'SELECT * FROM whole_cells WHERE dim=? AND x=? AND y=? AND z=?', args)
    if roles:
        m.require(len(roles) == 1, 'Ambiguous unchanged whole role')
        row = roles[0]
        key = 'whole:' + row['template'] + ':' + m.canonical([row['dx'], row['dy'], row['dz']])
    else:
        bridge = db.execute('SELECT 1 FROM bridge_cells WHERE dim=? AND x=? AND y=? AND z=?', args).fetchone()
        protected_ids = set(context.classification().get('protectedRegistryIds', []))
        m.require(bridge or original['registryId'] not in protected_ids, 'Protected original cannot become a static base')
        fragments = [{'pose': p, 'cell': [x, y, z]} for p, x, y, z in db.execute(
            'SELECT pose,dx,dy,dz FROM fragments WHERE dim=? AND x=? AND y=? AND z=? ORDER BY pose,dx,dy,dz', args)]
        substrate = [{'pose': p, 'cell': [x, y, z]} for p, x, y, z in db.execute(
            'SELECT pose,dx,dy,dz FROM substrate_fragments WHERE dim=? AND x=? AND y=? AND z=? ORDER BY pose,dx,dy,dz', args)]
        substrate_native = [{'state': m.json.loads(s), 'cell': [x, y, z]} for s, x, y, z in db.execute(
            'SELECT state,dx,dy,dz FROM native_visual_fragments WHERE dim=? AND x=? AND y=? AND z=? ORDER BY state,dx,dy,dz', args)]
        owned_ids = {m.json.loads(row[0])['registryId'] for row in db.execute(
            'SELECT p.data FROM selected_objects o JOIN poses p ON p.key=o.pose WHERE o.dim=? AND o.x=? AND o.y=? AND o.z=?', args)}
        exact = any(db.execute('SELECT 1 FROM old.' + table +
                    ' r JOIN selected_objects o ON o.dim=r.dim AND o.uuid=r.uuid WHERE r.dim=? AND r.x=? AND r.y=? AND r.z=? LIMIT 1', args).fetchone()
                    for table in ('members', 'stored_contributions'))
        generic = original['registryId'].startswith('bloodborne_dw:static_architecture_') or original['registryId'] in (
            'bloodborne_dw:composite_cell', 'bloodborne_dw:composite_fragment',
            'bloodborne_dw:source_door_fragment', 'bloodborne_dw:source_compatibility_fragment')
        synthetic = db.execute("SELECT 1 FROM objects WHERE dim=? AND x=? AND y=? AND z=? AND kind='substrate' LIMIT 1", args).fetchone()
        carrier = (original['registryId'] in owned_ids or (generic and exact)) and not synthetic
        base = m.AIR if bridge else None if carrier or original['registryId'] in (
            'minecraft:air', 'minecraft:cave_air', 'minecraft:void_air') else original
        comp = {'fragments': fragments, 'baseState': base, 'optics': None, 'localFunction': 'none'}
        if bridge:
            comp['wrapper'] = 'NativeGridBase'
            predicates = m.context_stage.carrier_predicates(m.module_api(), db, dim, at)
            if predicates is not None:
                comp['carrierPredicates'] = predicates
        if base:
            key_state = m.canonical(base)
            saved = context.exports.execute('SELECT data FROM native WHERE state=?', (key_state,)).fetchone()
            native = m.json.loads(saved[0]) if saved else None
            transferred = db.execute('SELECT 1 FROM activated_native a JOIN objects o ON o.dim=a.dim AND o.uuid=a.uuid WHERE o.dim=? AND o.x=? AND o.y=? AND o.z=? LIMIT 1', args).fetchone()
            wide = db.execute('SELECT 1 FROM native_visual_roots WHERE dim=? AND x=? AND y=? AND z=?', args).fetchone()
            if transferred or wide or substrate or substrate_native:
                if native and native.get('nativeVisualComplete', True) and not wide:
                    cells = context.exports.execute('SELECT x,y,z FROM native_fragments WHERE state=?', (key_state,)).fetchall()
                    if not cells:
                        comp['baseVisualMode'] = 'nativePlusSubstrateFragments'
                    else:
                        substrate_native.append({'state': base, 'cell': [0, 0, 0]})
                        comp['baseVisualMode'] = 'substrateFragments'
                else:
                    comp['baseVisualMode'] = 'substrateFragments'
                comp['substrateFragments'] = substrate
                if substrate_native:
                    comp['substrateNativeFragments'] = substrate_native
        elif substrate or substrate_native:
            comp.update(baseState=m.AIR, baseVisualMode='substrateFragments', substrateFragments=substrate)
            if substrate_native:
                comp['substrateNativeFragments'] = substrate_native
        key = 'cell_' + m.digest(comp)
        db.execute('INSERT OR IGNORE INTO compositions VALUES(?,?,NULL)', (key, m.canonical(comp)))
    db.execute('INSERT INTO placements VALUES(?,?,?,?,?,?,?,NULL,?,0)',
               (dim, *at, at[0] >> 4, at[2] >> 4, key, m.canonical(original)))


def write_artifacts(db, plan, directory):
    """Stream only live composition keys; no native queue or source scan."""
    raw = directory / 'compositions.after.jsonl'
    needed = {}
    with raw.open('w', encoding='utf-8', newline='\n') as stream:
        for key, encoded in db.execute('SELECT key,data FROM compositions ORDER BY key'):
            row = m.json.loads(encoded)
            stream.write(m.canonical({'key': key, **row}) + '\n')
            base = row.get('baseState')
            if base:
                needed[m.canonical(base)] = {**base, 'physicsOnly': base['registryId'].startswith('bloodborne_dw:')}
        stream.flush()
        os.fsync(stream.fileno())
    native = directory / 'native-states-needed.after.json'
    m.atomic_json(native, {'schemaVersion': 1, 'nativeStates': [needed[key] for key in sorted(needed)]})
    return raw, native


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--plan', type=Path, default=m.DEFAULT_PLAN)
    parser.add_argument('--impact', type=Path, required=True)
    parser.add_argument('--impact-sha256', required=True)
    parser.add_argument('--physics', type=Path, required=True)
    parser.add_argument('--physics-sha256', required=True)
    parser.add_argument('--expected-raw-sha256', required=True)
    parser.add_argument('--append-proof', type=Path, required=True)
    parser.add_argument('--append-proof-sha256', required=True)
    parser.add_argument('--out', type=Path, required=True)
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument('--preview', action='store_true')
    mode.add_argument('--execute', action='store_true')
    parser.add_argument('--expected-preview-sha256')
    args = parser.parse_args()
    run(args)


def recover_publication(plan, receipt):
    """Only committed metadata authorizes publishing the proposed file bytes."""
    for item in receipt['derivedFiles']:
        proposed, target = Path(item['proposed']['path']), Path(item['target'])
        m.require(m.sha(proposed) == item['proposed']['sha256'], 'Proposed derived bytes changed')
        if target.exists() and m.sha(target) == item['proposed']['sha256']:
            continue
        m.require(target.exists() and m.sha(target) == item['beforeSha256'], 'Neither old nor proposed derived artifact is present')
        temporary = target.with_suffix(target.suffix + '.postfinal-writing')
        shutil.copyfile(proposed, temporary)
        with temporary.open('rb') as stream:
            os.fsync(stream.fileno())
        os.replace(temporary, target)


def check_new_event(context, event):
    """Affected-source and residual checks only; old accepted prefix is carried."""
    db = context.db
    zone = event['sourceZone']
    dim, minimum, maximum = zone['dimension'], zone['minimum'], zone['maximum']
    m.require(context.zone_snapshot(dim, minimum) == zone, 'Affected original section snapshot changed')
    args = (dim, minimum[0], maximum[0], minimum[1], maximum[1], minimum[2], maximum[2])
    for table in ('cell_queue', 'placements', 'fragments', 'whole_cells', 'substrate_fragments',
                  'native_visual_fragments', 'native_visual_roots', 'bridge_cells'):
        m.require(not db.execute('SELECT 1 FROM ' + table +
                  ' WHERE dim=? AND x BETWEEN ? AND ? AND y BETWEEN ? AND ? AND z BETWEEN ? AND ? LIMIT 1', args).fetchone(),
                  'Affected source section has replacement work: ' + table)
    for domain in event['originalDomains']:
        obj = domain['object']
        m.require(context.original_domain(obj['dim'], obj['uuid']) == domain, 'Affected full source owner/data changed')
        for row in domain['domain']:
            m.require(db.execute('SELECT 1 FROM protected_cells WHERE dim=? AND x=? AND y=? AND z=? AND uuid=?',
                      (obj['dim'], *row['position'], obj['uuid'])).fetchone(), 'Affected source member lacks protection')
    for cell in event.get('originalNativeCells', []):
        m.require(c.original_native_cell(context, cell['dimension'], cell['position'], cell['sourceState'], unreplaced=True) == cell,
                  'Affected wide source data changed')
    for key in event.get('zoneWideWorkBefore', {}):
        root = m.json.loads(key)
        m.require(not db.execute('SELECT 1 FROM native_visual_roots WHERE dim=? AND x=? AND y=? AND z=?', (dim, *root)).fetchone()
                  and not db.execute('SELECT 1 FROM native_visual_fragments WHERE dim=? AND rx=? AND ry=? AND rz=? LIMIT 1', (dim, *root)).fetchone(),
                  'Affected wide root has partial derived work')


def run(a):
    plan, output = m.checked(a.plan), a.out.resolve()
    evidence = {'impact': {'path': str(m.checked(a.impact)), 'sha256': a.impact_sha256},
                'physics': {'path': str(m.checked(a.physics)), 'sha256': a.physics_sha256},
                'strictAppend': {'path': str(m.checked(a.append_proof)), 'sha256': a.append_proof_sha256},
                'producer': reference(__file__)}
    impact = read_bound(a.impact, a.impact_sha256)
    physics = read_bound(a.physics, a.physics_sha256)
    append_proof = read_bound(a.append_proof, a.append_proof_sha256)
    m.require(append_proof['status'] == 'PASS' and append_proof['mode'] == 'STRICT_ADDITIVE_NATIVE_EXPORT_LINEAGE' and
              all(append_proof[key] is True for key in ('posesUnchanged', 'fragmentsUnchanged',
                  'existingNativeRowsUnchanged', 'existingNativeVisualFragmentsUnchanged')), 'Exact strict native append lineage required')
    m.require(impact['status'] == 'READ_ONLY_FINITE_WIDE_NATIVE_IMPACT' and
              impact['rawSha256'] == a.expected_raw_sha256 and
              physics['status'] == 'READ_ONLY_EXACT_NEW_WIDE_NATIVE_PHYSICS', 'Exact completed-plan wide inputs required')
    for doc in (impact, physics):
        m.require(all(doc[key] == 0 for key in ('worldReads', 'mapWrites', 'databaseWrites')), 'Wide input exceeded its read-only scope')
    physics_states = {m.canonical(row['state']) for row in physics['states']}
    m.require(impact['rows'] and len(impact['rows']) <= 4096 and
              all(m.canonical(row['state']) in physics_states and row['mode'] in ('native', 'nativePlusSubstrateFragments') for row in impact['rows']),
              'Impact must contain only exact exported native-wide base witnesses')
    directory = output.parent / (output.stem + '.artifacts')
    directory.mkdir(parents=True, exist_ok=True)
    receipt_path = directory / 'receipt.json'
    db = sqlite3.connect('file:' + (plan / 'plan.sqlite').as_posix() + '?mode=rw', uri=True)
    context = exports = None
    try:
        committed = m.meta(db, 'postfinalOriginalSections')
        if committed and Path(committed['path']) == receipt_path:
            receipt = c.bound(m.module_api(), committed)
            m.require(a.execute and receipt['evidence'] == evidence and receipt['sourceFiles'] == c.source_files(m.module_api()),
                      'Committed postfinal receipt belongs to another request/source')
            m.require(receipt['beforeGeneration']['compositionsSha256'] == a.expected_raw_sha256 and
                      m.meta(db, 'binding') == receipt['sourceBinding'] and
                      m.meta(db, 'compositionGeneration') == receipt['afterGeneration'] and
                      m.meta(db, 'compositionsSha256') == receipt['afterGeneration']['compositionsSha256'] and
                      m.meta(db, 'poseFragments') == receipt['afterGeneration']['sourceFragments'] and
                      m.sha(receipt['afterGeneration']['sourceFragments']['path']) == receipt['afterGeneration']['sourceFragments']['sha256'] and
                      m.meta(db, 'sourceLocalRetentionJournal') == receipt['afterJournal'] and
                      m.meta(db, 'nativeComposeCursor') == receipt['cursor'] and n.counts(db) == receipt['afterCounts'],
                      'Committed postfinal publication requires its exact current source/generation/journal/cursor/counts')
            recover_publication(plan, receipt)
            print(m.canonical({'status': 'PASS_IDEMPOTENT_COMMITTED', 'receipt': committed}))
            return
        old_generation, cursor = preconditions(db, plan, a.expected_raw_sha256)
        c.active_binding(m.module_api(), db)
        c.attach_source_journal_original(m.module_api(), db)
        source_files_before = c.source_files(m.module_api())
        fragments_ref = m.meta(db, 'poseFragments')
        m.require(Path(fragments_ref['path']).resolve() == Path(physics['fragments']['path']).resolve()
                  and m.sha(fragments_ref['path']) == fragments_ref['sha256'] == append_proof['afterFragmentsSha256']
                  and old_generation['sourceFragments']['sha256'] == append_proof['beforeFragmentsSha256'],
                  'Current complete native export store/accepted strict append changed')
        exports = sqlite3.connect('file:' + Path(fragments_ref['path']).as_posix() + '?mode=ro', uri=True)
        exports.execute('PRAGMA query_only=ON')
        exports.execute('BEGIN')
        for row in physics['states']:
            actual = exports.execute('SELECT data FROM native WHERE state=?', (m.canonical(row['state']),)).fetchone()
            m.require(actual and m.hashlib.sha256(actual[0].encode()).hexdigest() == row['rowSha256'],
                      'Strict native append changed an exact wide source geometry/physics row')
        context = c.SourceVisualReservations(m.module_api(), db, exports)
        keys = {row['key']: row for row in impact['rows']}
        placeholders = ','.join('?' for _ in keys)
        seeds = n.row_dict(db, 'SELECT * FROM placements WHERE composition IN (' + placeholders + ') ORDER BY dim,x,y,z', tuple(keys))
        m.require(len(seeds) == impact['actualPlacements'] and len(keys) == impact['actualUnsafeCompositions'], 'Finite actual seed placement counts changed')
        for key, witness in keys.items():
            row = db.execute('SELECT data FROM compositions WHERE key=?', (key,)).fetchone()
            m.require(row and m.json.loads(row[0])['baseState'] == witness['state'] and
                      m.json.loads(row[0]).get('baseVisualMode', 'native') == witness['mode'] and
                      sum(seed['composition'] == key for seed in seeds) == witness['placements'], 'Unsafe composition witness changed')
        sections = sorted({(seed['dim'], *(seed[key] // 16 * 16 for key in ('x', 'y', 'z'))) for seed in seeds})
        m.require(0 < len(sections) <= 64, 'Finite initial source-section bound exceeded')
        for seed in seeds:
            actual, _, _ = context.source_point(seed['dim'], tuple(seed[key] for key in ('x', 'y', 'z')))
            m.require(actual == keys[seed['composition']]['state'] == m.json.loads(seed['source_state']), 'Wide seed source/data binding changed')
        prefix = m.meta(db, 'sourceLocalRetentionJournal')
        prefix_rows = db.execute('SELECT sequence,data,digest FROM source_local_retentions ORDER BY sequence').fetchall()
        m.require(prefix and len(prefix_rows) == prefix['events'] and prefix_rows[-1][2] == prefix['head'] and
                  all(m.digest(m.json.loads(row[1])) == row[2] for row in prefix_rows), 'Accepted journal prefix bytes/head changed')
        before = n.counts(db)
        before_artifacts = []
        for name in ('compositions.jsonl', 'native-states-needed.json', 'full-native-input-coverage.json'):
            if name == 'full-native-input-coverage.json' and not (plan / name).exists():
                continue
            path, backup = plan / name, directory / ('before.' + name)
            digest = m.sha(path)
            if backup.exists():
                m.require(m.sha(backup) == digest, 'Archived before-artifact changed')
            else:
                shutil.copyfile(path, backup)
                m.require(m.sha(backup) == digest, 'Before-artifact copy changed bytes')
            before_artifacts.append(reference(backup))
        generation_archive = directory / 'before.composition-generation.json'
        if generation_archive.exists():
            m.require(m.read(generation_archive) == old_generation, 'Archived generation changed')
        else:
            m.atomic_json(generation_archive, old_generation)
        db.execute('PRAGMA synchronous=FULL')
        db.execute('BEGIN IMMEDIATE')
        preconditions(db, plan, a.expected_raw_sha256)
        events = [retain_section(context, dim, (x, y, z)) for dim, x, y, z in sections]
        for event in events:
            check_new_event(context, event)
        m.require(db.execute('SELECT sequence,data,digest FROM source_local_retentions WHERE sequence<=? ORDER BY sequence', (prefix['events'],)).fetchall() == prefix_rows,
                  'Accepted original journal prefix was rewritten')
        m.require(m.meta(db, 'nativeComposeCursor') == cursor and not db.execute('SELECT 1 FROM cell_queue WHERE processed=0 LIMIT 1').fetchone(),
                  'Native cursor/pending state changed during postfinal retention')
        db.execute("DELETE FROM compositions WHERE key NOT IN (SELECT DISTINCT composition FROM placements WHERE composition NOT LIKE 'whole:%')")
        proposed_raw, proposed_needed = write_artifacts(db, plan, directory)
        generation = {**old_generation, 'compositionsSha256': m.sha(proposed_raw), 'sourceFragments': fragments_ref,
                      'postfinalOriginalSections': {'producer': evidence['producer'], 'evidence': evidence,
                       'previousGeneration': old_generation, 'previousJournal': prefix,
                       'journal': m.meta(db, 'sourceLocalRetentionJournal')}}
        preview = {'schemaVersion': 1, 'status': 'POSTFINAL_ORIGINAL_SECTIONS_PREVIEW_NOT_COMMITTED',
                   'kind': 'FINITE_POSTFINAL_ORIGINAL_SECTIONS', 'sourceBinding': m.meta(db, 'binding'),
                   'sourceFiles': source_files_before, 'evidence': evidence, 'beforeGeneration': old_generation,
                   'archivedBeforeArtifacts': before_artifacts + [reference(generation_archive)],
                   'afterGeneration': generation, 'cursor': cursor, 'preservedJournalPrefix': prefix,
                   'afterJournal': m.meta(db, 'sourceLocalRetentionJournal'), 'sections': [list(row) for row in sections],
                   'seedPlacements': seeds, 'events': [m.digest(event) for event in events],
                   'eventCount': len(events), 'uniqueRetainedOwners': len({(row['object']['dim'], row['object']['uuid']) for event in events for row in event['originalDomains']}),
                   'beforeCounts': before, 'afterCounts': n.counts(db), 'sourceAndAffectedDataValidated': True,
                   'oldJournalRevalidated': False, 'oldJournalPrefixUnchanged': True,
                   'processedRowsReset': 0, 'nativeQueueReplayed': False, 'mapWrites': 0,
                   'derivedFiles': [{'target': str(plan / name), 'beforeSha256': m.sha(plan / name), 'proposed': reference(path)}
                                    for name, path in (('compositions.jsonl', proposed_raw), ('native-states-needed.json', proposed_needed))]}
        m.require(c.source_files(m.module_api()) == source_files_before and all(m.sha(ref['path']) == ref['sha256'] for ref in evidence.values()), 'Source/evidence changed before result')
        if not a.execute:
            if output.exists():
                m.require(m.read(output) == preview, 'Preview path already contains another result')
            else:
                m.atomic_json(output, preview)
            db.rollback()
            print(m.canonical({'status': preview['status'], 'preview': reference(output), 'sections': len(sections),
                               'uniqueOwners': preview['uniqueRetainedOwners'], 'counts': preview['afterCounts']}))
            return
        m.require(a.expected_preview_sha256 and m.sha(output) == a.expected_preview_sha256 and m.read(output) == preview,
                  'Execute requires the exact reviewed preview and identical current affected data')
        receipt = {**preview, 'status': 'PASS', 'kind': 'COMMITTED_POSTFINAL_ORIGINAL_SECTIONS',
                   'preview': reference(output), 'fullOldValidationRepeated': False,
                   'commitAuthority': 'Only matching postfinalOriginalSections SQLite meta makes this receipt authoritative'}
        if receipt_path.exists():
            m.require(m.read(receipt_path) == receipt, 'Uncommitted receipt differs')
        else:
            m.atomic_json(receipt_path, receipt)
        m.meta(db, 'compositionGeneration', generation)
        m.meta(db, 'compositionsSha256', generation['compositionsSha256'])
        m.meta(db, 'postfinalOriginalSections', reference(receipt_path))
        db.commit()
        recover_publication(plan, receipt)
        print(m.canonical({'status': 'PASS_COMMITTED', 'receipt': reference(receipt_path), 'sections': len(sections),
                           'owners': preview['uniqueRetainedOwners'], 'afterJournal': preview['afterJournal']}))
    except BaseException:
        db.rollback()
        raise
    finally:
        if context is not None:
            context.close()
        if exports is not None:
            exports.close()
        db.close()


point=c.point
original_native_cell=c.original_native_cell
LOCAL_ORIGINAL_REASON=c.LOCAL_ORIGINAL_REASON
source_files=c.source_files


def retain_section(context, dim, minimum):
    m,db=context.m,context.db
    m.require(db.in_transaction, 'Postfinal sections require caller transaction')
    minimum=tuple(minimum);maximum=tuple(value+15 for value in minimum)
    cells={(x,y,z) for x in range(minimum[0],minimum[0]+16) for y in range(minimum[1],minimum[1]+16) for z in range(minimum[2],minimum[2]+16)}
    incoming=None
    zone=context.zone_snapshot(dim,minimum);cursor=m.meta(db,'nativeComposeCursor');retention=m.meta(db,'nativeLocalRetention');head=m.meta(db,'sourceLocalRetentionJournal');before=n.counts(db)
    uids=set();wide=set();objects={};wide_records={};native_cells={};union=set(cells);sources={}
    def budget():
        m.require(len(uids)+len(wide)<=4096 and len(union)<=32768,'Original section complete-object budget exceeded: '+m.canonical({'section':[dim,*minimum],'owners':len(uids),'wideRoots':len(wide),'unionCells':len(union)}))
    def known(uid):
        rows=n.row_dict(db,'SELECT * FROM objects WHERE dim=? AND uuid=?',(dim,uid))
        m.require(len(rows)<=1,'Ambiguous source owner')
        if rows:uids.add(uid)
    def discover(points,section=False):
        for cell in points:
            saved=point(m,db,dim,cell)
            uids.update(row['uuid'] for row in saved['fragments']+saved['whole_cells']+saved['substrate_fragments'])
            wide.update((row['rx'],row['ry'],row['rz']) for row in saved['native_visual_fragments']);wide.update((row['x'],row['y'],row['z']) for row in saved['native_visual_roots'])
            if section:
                for row in saved['protected_cells']:known(row['uuid'])
            for table in ('stored_contributions','members'):
                for row in n.row_dict(db,'SELECT uuid FROM old.'+table+' WHERE dim=? AND x=? AND y=? AND z=?',(dim,*cell)):known(row['uuid'])
            original,be,_=context.source_point(dim,cell)
            for origin in context.native_origins(dim,cell,original):
                if section or origin['member']:
                    for obj in origin['object']:uids.add(obj['uuid'])
            for obj in n.row_dict(db,'SELECT * FROM objects INDEXED BY object_root WHERE dim=? AND x=? AND z=? AND y=?',(dim,cell[0],cell[2],cell[1])):uids.add(obj['uuid'])
            if be:
                # Recognised owner headers locate saved parts; private
                # payload bytes remain opaque and are never converted.
                try:
                    owners=list(m.be_owners(be))+([be['resident']] if 'resident' in be else [])
                    for owner in owners:known(m.owner_uuid(owner))
                except (KeyError,TypeError,ValueError,m.NbtError):pass
            budget()
    candidates=db.execute(
        'SELECT DISTINCT o.uuid FROM pose_cells AS p CROSS JOIN objects AS o INDEXED BY object_pose_zone '
        'WHERE o.pose=p.pose AND o.dim=? AND o.x BETWEEN ?-p.dx AND ?-p.dx '
        'AND o.z BETWEEN ?-p.dz AND ?-p.dz AND o.y BETWEEN ?-p.dy AND ?-p.dy LIMIT 4097',
        (dim,minimum[0],maximum[0],minimum[2],maximum[2],minimum[1],maximum[1]))
    uids.update(row[0] for row in candidates);budget()
    discover(cells,section=True)



    budget()
    exports=context.exports;close=exports is None
    if close:exports=sqlite3.connect('file:'+Path(context.bindings['poseFragments']['path']).as_posix()+'?mode=ro',uri=True)
    try:
        while uids-set(objects) or wide-set(wide_records):
            budget()
            for uid in sorted(uids-set(objects)):
                obj,members,_=context.original_members(dim,uid);source=context.original_domain(dim,uid,unreplaced=False)
                selected=n.row_dict(db,'SELECT * FROM selected_objects WHERE dim=? AND uuid=?',(dim,uid))
                fragments=n.row_dict(db,'SELECT * FROM fragments INDEXED BY fragment_owner WHERE dim=? AND uuid=? ORDER BY x,y,z LIMIT 4097',(dim,uid))
                roles=n.row_dict(db,'SELECT * FROM whole_cells WHERE dim=? AND uuid=? ORDER BY x,y,z LIMIT 4097',(dim,uid))
                activated=n.row_dict(db,'SELECT * FROM activated_native WHERE dim=? AND uuid=?',(dim,uid))
                substrate=n.row_dict(db,'SELECT * FROM substrate_fragments INDEXED BY substrate_owner WHERE dim=? AND uuid=? ORDER BY x,y,z LIMIT 4097',(dim,uid))
                cleanup=n.row_dict(db,'SELECT * FROM cleanup_owners WHERE dim=? AND uuid=?',(dim,uid))
                m.require(len(selected)<=1 and len(activated)<=1 and len(cleanup)<=1 and max(len(fragments),len(roles),len(substrate))<=4096,'Original object work is not complete and bounded')
                m.require(not selected or all(selected[0][key]==obj[key] for key in ('dim','uuid','pose','x','y','z','kind')),'Original selected identity changed')
                m.require(not (fragments or roles) or selected,'Original owner fragments lack their exact selected identity')
                m.require(all(row['pose']==obj['pose'] for row in fragments+substrate),'Original owner work has another pose')
                if activated:
                    expected={(obj['x']+dx,obj['y']+dy,obj['z']+dz,dx,dy,dz) for dx,dy,dz in db.execute('SELECT dx,dy,dz FROM pose_cells WHERE pose=?',(obj['pose'],))}
                    m.require(0<len(expected)<=4096 and expected=={(row['x'],row['y'],row['z'],row['dx'],row['dy'],row['dz']) for row in substrate},'Active native original requires complete exact derived work')
                else:m.require(not substrate,'Native derived work lost its activation')
                objects[uid]={'object':obj,'selected':selected,'fragments':fragments,'whole_cells':roles,'activated_native':activated,'substrate_fragments':substrate,'cleanup_owners':cleanup}
                sources[uid]=source;union.update(members);union.update((row['x'],row['y'],row['z']) for row in fragments+roles+substrate);budget()
                discover(members)
            for root in sorted(wide-set(wide_records)):
                original,_,_=context.source_point(dim,root);key=m.canonical(original)
                exported=exports.execute('SELECT data FROM native WHERE state=?',(key,)).fetchone()
                m.require(exported and m.json.loads(exported[0]).get('nativeVisualComplete',True),'Original wide source needs its bound complete native model')
                offsets=exports.execute('SELECT x,y,z FROM native_fragments WHERE state=? LIMIT 4097',(key,)).fetchall();m.require(0<len(offsets)<=4096,'Original wide source fragments are not complete/bounded')
                expected={(*tuple(root[i]+offset[i] for i in range(3)),*offset) for offset in offsets}
                roots=n.row_dict(db,'SELECT * FROM native_visual_roots WHERE dim=? AND x=? AND y=? AND z=?',(dim,*root))
                rows=n.row_dict(db,'SELECT * FROM native_visual_fragments WHERE dim=? AND rx=? AND ry=? AND rz=? ORDER BY x,y,z LIMIT 4097',(dim,*root))
                pending=incoming and incoming.get('kind')=='wide_native' and tuple(incoming['root'])==root
                m.require((roots==[{'dim':dim,'x':root[0],'y':root[1],'z':root[2],'state':key}] and expected=={(row['x'],row['y'],row['z'],row['dx'],row['dy'],row['dz']) for row in rows} and all(row['state']==key for row in rows)) or (pending and not roots and not rows),'Original wide native requires its exact complete committed rows or zero pending rows')
                native_cells[root]=original_native_cell(context,dim,root,original)
                wide_records[root]={'rootRows':roots,'fragments':rows,'sourceState':original,'exportRowSha256':m.hashlib.sha256(exported[0].encode()).hexdigest()}
                union.add(root);union.update(row[:3] for row in expected);budget();discover({root})
    finally:
        if close:exports.close()
    strict=set(cells);strict.update(tuple(row['position']) for source in sources.values() for row in source['domain']);strict.update(native_cells)
    plan=[]
    for cell in sorted(union):
        saved=point(m,db,dim,cell)
        remaining={'fragments':[row for row in saved['fragments'] if row['uuid'] not in uids],
                   'whole_cells':[row for row in saved['whole_cells'] if row['uuid'] not in uids],
                   'substrate_fragments':[row for row in saved['substrate_fragments'] if row['uuid'] not in uids],
                   'native_visual_fragments':[row for row in saved['native_visual_fragments'] if (row['rx'],row['ry'],row['rz']) not in wide],
                   'native_visual_roots':[row for row in saved['native_visual_roots'] if (row['x'],row['y'],row['z']) not in wide]}
        original,_,_=context.source_point(dim,cell);foreign=[]
        for origin in context.native_origins(dim,cell,original):
            if origin['uuid'] not in uids:foreign.extend(n.row_dict(db,'SELECT * FROM activated_native WHERE dim=? AND uuid=?',(dim,origin['uuid'])))
        remaining['foreignActivatedNative']=foreign;needed=any(remaining.values())
        m.require(cell not in strict or not needed,'Unsupported work remains on an original section/object member: '+str((dim,cell,remaining)))
        m.require(saved['queue'] in ([],[{'processed':0}],[{'processed':1}]) and (not needed or saved['queue']),'Original section has inconsistent queue work')
        if saved['queue'] or any(saved[key] for key in ('fragments','whole_cells','substrate_fragments','native_visual_fragments','native_visual_roots','bridge_cells')):
            plan.append({'position':list(cell),'before':saved,'remainingDemands':remaining,'retire':bool(saved['queue']) and not needed})
    placements_before={cell:point(m,db,dim,cell)['placements'] for cell in sorted(union)}
    for cell,rows in placements_before.items():
        m.require(db.execute('DELETE FROM placements WHERE dim=? AND x=? AND y=? AND z=?',(dim,*cell)).rowcount==len(rows),'Affected placement changed during postfinal retention')
    # All source snapshots and complete derived rows are bound above before
    # the first mutation; any failure rolls this entire fallback back.
    for uid,record in sorted(objects.items()):
        for table,key in (('fragments','fragments'),('whole_cells','whole_cells'),('substrate_fragments','substrate_fragments'),('selected_objects','selected'),('activated_native','activated_native'),('cleanup_owners','cleanup_owners')):
            m.require(db.execute('DELETE FROM '+table+' WHERE dim=? AND uuid=?',(dim,uid)).rowcount==len(record[key]),'Original object derived rows changed during atomic section fallback')
    for root,record in sorted(wide_records.items()):
        m.require(db.execute('DELETE FROM native_visual_fragments WHERE dim=? AND rx=? AND ry=? AND rz=?',(dim,*root)).rowcount==len(record['fragments']),'Original wide fragments changed')
        m.require(db.execute('DELETE FROM native_visual_roots WHERE dim=? AND x=? AND y=? AND z=?',(dim,*root)).rowcount==len(record['rootRows']),'Original wide root changed')
    for row in plan:
        args=(dim,*row['position'])
        if row['retire']:
            m.require(db.execute('DELETE FROM cell_queue WHERE dim=? AND x=? AND y=? AND z=? AND processed=?',(*args,row['before']['queue'][0]['processed'])).rowcount==1,'Retire only the exact undemanded original section work')
        if not any(row['remainingDemands'].values()):
            m.require(db.execute('DELETE FROM bridge_cells WHERE dim=? AND x=? AND y=? AND z=?',args).rowcount==len(row['before']['bridge_cells']),'Delete only the exact undemanded original section bridge')
    for cell in sorted(union-strict):
        if db.execute('SELECT 1 FROM cell_queue WHERE dim=? AND x=? AND y=? AND z=?',(dim,*cell)).fetchone():recompose(context,dim,cell)
    domains=[]
    for uid,source in sorted(sources.items()):
        actual=context.original_domain(dim,uid);m.require(actual==source,'Original object source/data changed before commit');domains.append(actual)
        for row in source['domain']:db.execute('INSERT OR IGNORE INTO protected_cells VALUES(?,?,?,?,?,?)',(dim,*row['position'],uid,LOCAL_ORIGINAL_REASON))
    for root,snapshot in sorted(native_cells.items()):
        m.require(original_native_cell(context,dim,root,snapshot['sourceState'],unreplaced=True)==snapshot,'Original wide root/data changed before commit');db.execute('INSERT OR IGNORE INTO protected_cells VALUES(?,?,?,?,?,?)',(dim,*root,snapshot['uuid'],LOCAL_ORIGINAL_REASON))
    m.require(context.zone_snapshot(dim,minimum)==zone and m.meta(db,'nativeComposeCursor')==cursor and m.meta(db,'nativeLocalRetention')==retention,'Original source section/cursor/history changed')
    event={'schemaVersion':1,'kind':'AUTOMATIC_DIRECT_ORIGINAL_NATIVE_MEMBER_RETENTION','sourceBinding':m.meta(db,'binding'),'sourceFiles':source_files(m),'previous':head,'sourceZone':zone,
           'originalDomains':domains,'originalNativeCells':list(native_cells.values()),'zoneObjectWorkBefore':objects,'zoneWideWorkBefore':{m.canonical(root):record for root,record in wide_records.items()},'pointPlan':plan,'beforeCounts':before,'afterCounts':n.counts(db),
           'cursor':cursor,'retentionHead':retention,'processedRowsReset':0,'ownerGraphTraversal':False,'mapWrites':0}
    event['postfinalOriginalSection']={'producer':reference(__file__),'priorGeneration':m.meta(db,'compositionGeneration'),'placementsBefore':[{ 'position':list(cell),'rows':rows} for cell,rows in placements_before.items() if rows]}
    sequence=(head['events'] if head else 0)+1;digest=m.digest(event);db.execute('INSERT INTO source_local_retentions VALUES(?,?,?)',(sequence,m.canonical(event),digest));m.meta(db,'sourceLocalRetentionJournal',{'schemaVersion':1,'events':sequence,'head':digest,'sourceBinding':event['sourceBinding']})
    return event


if __name__ == '__main__':
    main()

