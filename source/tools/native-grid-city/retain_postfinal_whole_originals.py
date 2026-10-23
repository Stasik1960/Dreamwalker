"""Retain complete, source-bound whole owners with undemanded compiled roles.

This completed-plan amendment never runs the native queue or touches the map.
Only the finite owners/points in the reviewed source facts may be changed.
"""
import argparse
import base64
import sqlite3
from pathlib import Path

import migrate as m
import native_certificates as c
import native_resume as n
import retain_postfinal_original_sections as shared
import split_postfinal_carrier_predicates as publication
import read_postfinal_whole_original_domains as collector


def ref(path):
    return {'path': str(m.checked(path)), 'sha256': m.sha(path)}


def stable(db):
    return {**collector.stable(db), **{key: m.meta(db, key) for key in
            ('nativeLocalRetention', 'sourceVisualReservationJournal', 'nativeResumeSourceTransition',
             'nativeFragmentAppendLineage', 'fullNativeFunctionGate', 'plannedBeSafety')}}


def source_equal(context, dim, saved):
    at = tuple(saved['position'])
    state, be, chunk = context.source_point(dim, at)
    encoded = base64.b64encode(m.encode_nbt(m.NbtFile('', m.Tag(10, be)))).decode('ascii') if be is not None else None
    m.require(state == saved['sourceState'] and chunk == saved['sourceChunk'] and
              context.source_be_digest(dim, at, be, chunk) == saved['sourceBeDigest'] and
              encoded == saved['sourceBeNbtBase64'], 'Affected source state/typed BE/cache payload changed')
    for table in ('stored_contributions', 'members'):
        m.require(n.row_dict(context.db, 'SELECT * FROM old.' + table + ' WHERE dim=? AND x=? AND y=? AND z=?',
                  (dim, *at)) == saved['sourceOwnerRows'][table], 'Affected source owner ledger/member data changed')


def validate_new(context, facts, domains):
    """Validate only the new complete originals; carry the accepted prefix."""
    db = context.db
    for saved, domain in zip(facts['owners'], domains):
        obj = saved['object']; dim, uid = obj['dim'], obj['uuid']
        m.require(context.original_domain(dim, uid) == domain == saved['originalDomain'],
                  'Full original root/member/data/payload changed during whole retention')
        for row in domain['domain']:
            m.require(db.execute('SELECT 1 FROM protected_cells WHERE dim=? AND x=? AND y=? AND z=? AND uuid=?',
                      (dim, *row['position'], uid)).fetchone(), 'Retained complete source member lacks protection')
        for point in saved['points']:
            source_equal(context, dim, point)
            now = c.point(m.module_api(), db, dim, point['position'])
            m.require(all(not now[key] for key in ('queue', 'placements', 'fragments', 'whole_cells',
                      'substrate_fragments', 'native_visual_fragments', 'native_visual_roots', 'bridge_cells')),
                      'A retired whole role/source member still has replacement work')
            expected = {m.canonical(row) for row in point['point']['protected_cells']}
            for retained in domains:
                identity = retained['object']
                if identity['dim'] == dim and any(row['position'] == point['position'] for row in retained['domain']):
                    expected.add(m.canonical({'dim': dim, 'x': point['position'][0], 'y': point['position'][1],
                                             'z': point['position'][2], 'uuid': identity['uuid'], 'reason': c.LOCAL_ORIGINAL_REASON}))
            m.require({m.canonical(row) for row in now['protected_cells']} == expected,
                      'Whole retention changed existing source protection provenance')


def run(a):
    plan, output = m.checked(a.plan), a.out.resolve()
    evidence = {'facts': {'path': str(m.checked(a.facts)), 'sha256': a.facts_sha256},
                'authority': {'path': str(m.checked(a.authority)), 'sha256': a.authority_sha256},
                'producer': ref(__file__), 'collector': ref(collector.__file__),
                'publication': ref(publication.__file__), 'compositionHelper': ref(shared.__file__)}
    facts = c.bound(m.module_api(), evidence['facts'])
    m.require(m.sha(a.authority) == a.authority_sha256 and facts['producer'] == evidence['collector'] and
              facts['status'] == 'PASS_SOURCE_FACTS_NOT_ADMISSION' and
              facts['kind'] == 'FINITE_POSTFINAL_WHOLE_ORIGINAL_DOMAIN_FACTS' and
              0 < len(facts['owners']) <= 64 and not facts['directMemberDemandOwners'],
              'Reviewed authority and finite complete whole-source facts are required')
    directory = output.parent / (output.stem + '.artifacts')
    directory.mkdir(parents=True, exist_ok=True)
    receipt_path = directory / 'receipt.json'
    db = sqlite3.connect('file:' + (plan / 'plan.sqlite').as_posix() + '?mode=rw', uri=True)
    context = exports = None
    try:
        c.attach_source_journal_original(m.module_api(), db)
        fragment_ref = m.meta(db, 'poseFragments')
        m.require(m.sha(fragment_ref['path']) == fragment_ref['sha256'], 'Exact current source geometry binding changed')
        exports = sqlite3.connect('file:' + Path(fragment_ref['path']).as_posix() + '?mode=ro', uri=True)
        exports.execute('PRAGMA query_only=ON')
        exports.execute('BEGIN')
        context = c.SourceVisualReservations(m.module_api(), db, exports)
        committed = m.meta(db, 'postfinalWholeOriginalRetention')
        if committed:
            receipt = c.bound(m.module_api(), committed)
            m.require(a.execute and Path(committed['path']) == receipt_path and receipt['evidence'] == evidence and
                      receipt['sourceFiles'] == c.source_files(m.module_api()) and stable(db) == receipt['afterState'] and
                      publication.counts(db) == receipt['afterCounts'], 'Committed whole-original amendment state changed')
            for value in evidence.values():
                m.require(m.sha(value['path']) == value['sha256'], 'Committed whole-original evidence changed')
            validate_new(context, facts, receipt['originalDomains'])
            publication.publish(plan, receipt)
            print(m.canonical({'status': 'PASS_IDEMPOTENT_COMMITTED', 'receipt': committed}))
            return
        generation, cursor = shared.preconditions(db, plan, facts['stableState']['compositionsSha256'])
        c.active_binding(m.module_api(), db)
        m.require(collector.stable(db) == facts['stableState'] and facts['sourceFiles'] == c.source_files(m.module_api()),
                  'The completed source/generation differs from the finite whole facts')
        before_state, before_counts = stable(db), publication.counts(db)
        prior_split_ref = before_state['postfinalCarrierPredicateSplit']
        prior_split = c.bound(m.module_api(), prior_split_ref)
        m.require(prior_split['status'] == 'PASS' and prior_split['kind'] == 'COMMITTED_POSTFINAL_CARRIER_PREDICATE_SPLIT' and
                  prior_split['afterGeneration'] == generation and prior_split['sourceBinding'] == before_state['binding'],
                  'The exact accepted carrier-split generation must precede whole retention')
        before_raw = next(row['proposed'] for row in prior_split['derivedFiles'] if Path(row['target']) == plan / 'compositions.jsonl')
        m.require(m.sha(before_raw['path']) == before_raw['sha256'] == generation['compositionsSha256'],
                  'The accepted immutable raw composition prefix changed')
        head = m.meta(db, 'sourceLocalRetentionJournal')
        prefix = db.execute('SELECT sequence,data,digest FROM source_local_retentions ORDER BY sequence').fetchall()
        m.require(head and len(prefix) == head['events'] and prefix[-1][2] == head['head'], 'Accepted original-journal prefix changed')
        seeds = {(row['object']['dim'], row['object']['uuid']) for row in facts['owners']}
        m.require(len(seeds) == len(facts['owners']), 'Duplicate retained whole owner')
        points, domains = {}, []
        for owner in facts['owners']:
            obj = owner['object']; dim, uid = obj['dim'], obj['uuid']; own = owner['ownWork']
            m.require(obj['kind'] == 'whole' and collector.work(db, dim, uid) == own and
                      len(own['selected_objects']) == 1 and own['selected_objects'][0]['kind'] == 'whole' and
                      own['whole_cells'] and not any(own[table] for table in
                      ('fragments', 'activated_native', 'substrate_fragments')),
                      'Only complete exact selected whole-role work may be restored')
            m.require(context.original_domain(dim, uid, unreplaced=False) == owner['originalDomain'],
                      'The complete original whole source/data changed before retention')
            for table in ('stored_contributions', 'members'):
                m.require(context.owner_member_rows(dim, uid, table) == owner['ownSavedRows'][table],
                          'Complete original owner data/ledger changed')
            expected = {tuple(row['position']) for row in owner['originalDomain']['domain']}
            expected.update(tuple(row[key] for key in ('x', 'y', 'z')) for row in own['whole_cells'])
            m.require({tuple(row['position']) for row in owner['points']} == expected,
                      'Facts omit part of the complete source/member/whole-role union')
            for saved in owner['points']:
                at = tuple(saved['position']); token = (dim, *at)
                source_equal(context, dim, saved)
                actual = c.point(m.module_api(), db, dim, at)
                m.require(actual == saved['point'] and actual['queue'] in ([], [{'processed': 1}]),
                          'Affected completed whole point/queue changed')
                remaining = {key: actual[key] for key in ('fragments', 'substrate_fragments',
                             'native_visual_fragments', 'native_visual_roots')}
                remaining['whole_cells'] = [row for row in actual['whole_cells'] if (dim, row['uuid']) not in seeds]
                m.require(not any(remaining.values()),
                          'A complete original whole role still has an independent demand: ' + m.canonical(token))
                m.require(not db.execute('SELECT 1 FROM activated_native a JOIN objects o ON o.dim=a.dim AND o.uuid=a.uuid '
                          'WHERE o.dim=? AND o.x=? AND o.z=? AND o.y=? LIMIT 1', (dim, at[0], at[2], at[1])).fetchone() and
                          all(not db.execute('SELECT 1 FROM activated_native WHERE dim=? AND uuid=?',
                              (dim, origin['uuid'])).fetchone() for origin in saved['nativeOrigins']),
                          'Original whole point has an independent active native source')
                entry = {'dimension': dim, 'position': list(at), 'before': actual,
                         'remainingDemands': remaining, 'retire': bool(actual['queue'])}
                if token in points:
                    m.require(points[token] == entry, 'Overlapping original whole point facts disagree')
                points[token] = entry
        m.require(len(points) <= 32768, 'Complete original whole point bound exceeded')
        db.execute('PRAGMA synchronous=FULL')
        db.execute('BEGIN IMMEDIATE')
        shared.preconditions(db, plan, generation['compositionsSha256'])
        m.require(stable(db) == before_state and publication.counts(db) == before_counts and
                  not m.meta(db, 'postfinalWholeOriginalRetention'), 'Completed plan changed before whole retention')
        for token, saved in points.items():
            m.require(c.point(m.module_api(), db, token[0], token[1:]) == saved['before'], 'Affected beforeimage changed before first write')
        for owner in facts['owners']:
            obj = owner['object']; token = (obj['dim'], obj['uuid'])
            m.require(collector.work(db, *token) == owner['ownWork'], 'Complete whole own-work changed before first write')
            for table in ('whole_cells', 'selected_objects', 'cleanup_owners'):
                m.require(db.execute('DELETE FROM ' + table + ' WHERE dim=? AND uuid=?', token).rowcount == len(owner['ownWork'][table]),
                          'Retire only the complete exact whole selection/roles/cleanup')
        for token, saved in sorted(points.items()):
            m.require(db.execute('DELETE FROM placements WHERE dim=? AND x=? AND y=? AND z=?', token).rowcount == len(saved['before']['placements']),
                      'Retire only exact affected whole placements')
            m.require(db.execute('DELETE FROM bridge_cells WHERE dim=? AND x=? AND y=? AND z=?', token).rowcount == len(saved['before']['bridge_cells']),
                      'Retire only exact undemanded whole bridges')
            if saved['retire']:
                m.require(db.execute('DELETE FROM cell_queue WHERE dim=? AND x=? AND y=? AND z=? AND processed=1', token).rowcount == 1,
                          'Retire only the exact undemanded completed row; never reset')
        context.close()
        context = c.SourceVisualReservations(m.module_api(), db, exports)
        for owner in facts['owners']:
            obj = owner['object']; dim, uid = obj['dim'], obj['uuid']
            domain = context.original_domain(dim, uid)
            m.require(domain == owner['originalDomain'], 'Whole source/root/helpers/data changed during derived retirement')
            domains.append(domain)
            for cell in domain['domain']:
                db.execute('INSERT OR IGNORE INTO protected_cells VALUES(?,?,?,?,?,?)',
                           (dim, *cell['position'], uid, c.LOCAL_ORIGINAL_REASON))
        validate_new(context, facts, domains)
        m.require(stable(db) == before_state and db.execute('SELECT sequence,data,digest FROM source_local_retentions ORDER BY sequence').fetchall() == prefix,
                  'Accepted source/journal prefix changed during whole retention')
        event = {'schemaVersion': 1, 'kind': 'AUTOMATIC_DIRECT_ORIGINAL_NATIVE_MEMBER_RETENTION',
                 'sourceBinding': before_state['binding'], 'sourceFiles': c.source_files(m.module_api()), 'previous': head,
                 'originalDomains': domains, 'originalNativeCells': [], 'pointPlan': list(points.values()),
                 'selectedOwnersBefore': [row['ownWork']['selected_objects'][0] for row in facts['owners']],
                 'wholeRolesBefore': [item for row in facts['owners'] for item in row['ownWork']['whole_cells']],
                 'beforeCounts': before_counts, 'afterCounts': publication.counts(db), 'cursor': cursor,
                 'retentionHead': before_state['nativeLocalRetention'], 'processedRowsReset': 0,
                 'ownerGraphTraversal': False, 'mapWrites': 0, 'postfinalWholeOriginalRetention': evidence}
        digest = m.digest(event); sequence = head['events'] + 1
        db.execute('INSERT INTO source_local_retentions VALUES(?,?,?)', (sequence, m.canonical(event), digest))
        after_head = {'schemaVersion': 1, 'events': sequence, 'head': digest, 'sourceBinding': before_state['binding']}
        m.meta(db, 'sourceLocalRetentionJournal', after_head)
        files = shared.write_artifacts(db, plan, directory)
        m.require(m.sha(files[0]) == generation['compositionsSha256'] and
                  m.sha(files[1]) == m.sha(plan / 'native-states-needed.json'),
                  'Whole-role retirement must preserve all ordinary raw/native-needed bytes')
        generation_after = {**generation, 'compositionsSha256': m.sha(files[0]),
                            'postfinalWholeOriginalRetention': {'facts': evidence['facts'], 'authority': evidence['authority'],
                                                               'previousCompositionsSha256': generation['compositionsSha256'],
                                                               'afterJournal': after_head}}
        after_counts = publication.counts(db)
        expected_counts = {**before_counts, 'selected_objects': before_counts['selected_objects'] - len(facts['owners']),
                           'whole_cells': before_counts['whole_cells'] - sum(len(row['ownWork']['whole_cells']) for row in facts['owners']),
                           'placements': before_counts['placements'] - sum(len(row['before']['placements']) for row in points.values()),
                           'cell_queue': before_counts['cell_queue'] - sum(row['retire'] for row in points.values()),
                           'bridge_cells': before_counts['bridge_cells'] - sum(len(row['before']['bridge_cells']) for row in points.values()),
                           'cleanup_owners': before_counts['cleanup_owners'] - sum(len(row['ownWork']['cleanup_owners']) for row in facts['owners'])}
        # n.counts deliberately omits protection rows; verify their exact addition separately.
        m.require(after_counts == expected_counts, 'Whole original retention changed unrelated table counts')
        after_state = {**before_state, 'compositionGeneration': generation_after,
                       'compositionsSha256': generation_after['compositionsSha256'], 'sourceLocalRetentionJournal': after_head}
        preview = {'schemaVersion': 1, 'status': 'POSTFINAL_WHOLE_ORIGINAL_RETENTION_PREVIEW_NOT_COMMITTED',
                   'kind': 'FINITE_POSTFINAL_WHOLE_ORIGINAL_RETENTION', 'sourceBinding': before_state['binding'],
                   'sourceFiles': c.source_files(m.module_api()), 'evidence': evidence,
                   'beforeState': before_state, 'afterState': after_state, 'beforeGeneration': generation,
                   'afterGeneration': generation_after, 'beforeCounts': before_counts, 'afterCounts': after_counts,
                   'priorCarrierPredicateSplit': prior_split_ref, 'beforeRaw': before_raw,
                   'priorJournalPrefix': {'head': head, 'rowsDigest': m.digest(prefix)}, 'afterJournal': after_head,
                   'originalDomains': domains, 'newEvent': {'sequence': sequence, 'digest': digest, 'data': event},
                   'wholeOwnersRetained': len(facts['owners']), 'wholeRoleRowsRemoved': sum(len(row['ownWork']['whole_cells']) for row in facts['owners']),
                   'sourceMemberCells': sum(len(row['domain']) for row in domains), 'affectedPoints': len(points),
                   'objectsKindsUnchanged': True, 'allSourceDataUnchanged': True, 'onlyCompleteUndemandedDerivedWorkRetired': True,
                   'rawAndNativeNeededBytesUnchanged': True, 'ordinaryCompositionKeysUnchanged': True,
                   'processedRowsReset': 0, 'nativeQueueReplayed': False, 'worldReads': 0, 'mapWrites': 0,
                   'derivedFiles': [{'target': str(plan / name), 'beforeSha256': m.sha(plan / name), 'proposed': ref(path)}
                                    for name, path in zip(('compositions.jsonl', 'native-states-needed.json'), files)]}
        for value in evidence.values():
            m.require(m.sha(value['path']) == value['sha256'], 'Whole-original source/evidence changed before result')
        if not a.execute:
            c.immutable_json(m.module_api(), output, preview)
            db.rollback()
            print(m.canonical({'status': preview['status'], 'preview': ref(output), 'owners': len(domains), 'points': len(points)}))
            return
        m.require(a.expected_preview_sha256 and m.sha(output) == a.expected_preview_sha256 and m.read(output) == preview,
                  'Execute only the exact reviewed whole-original preview and unchanged source/beforeimages')
        receipt = {**preview, 'status': 'PASS', 'kind': 'COMMITTED_POSTFINAL_WHOLE_ORIGINAL_RETENTION', 'preview': ref(output)}
        c.immutable_json(m.module_api(), receipt_path, receipt)
        m.meta(db, 'compositionGeneration', generation_after)
        m.meta(db, 'compositionsSha256', generation_after['compositionsSha256'])
        m.meta(db, 'postfinalWholeOriginalRetention', ref(receipt_path))
        m.require(stable(db) == after_state, 'Unexpected completed metadata change before whole-original commit')
        db.commit()
        publication.publish(plan, receipt)
        print(m.canonical({'status': 'PASS_COMMITTED', 'receipt': ref(receipt_path), 'owners': len(domains), 'points': len(points)}))
    except BaseException:
        db.rollback()
        raise
    finally:
        if context is not None:
            context.close()
        if exports is not None:
            exports.close()
        db.close()


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--plan', type=Path, default=m.DEFAULT_PLAN)
    p.add_argument('--facts', type=Path, required=True)
    p.add_argument('--facts-sha256', required=True)
    p.add_argument('--authority', type=Path, required=True)
    p.add_argument('--authority-sha256', required=True)
    p.add_argument('--out', type=Path, required=True)
    modes = p.add_mutually_exclusive_group(required=True)
    modes.add_argument('--preview', action='store_true')
    modes.add_argument('--execute', action='store_true')
    p.add_argument('--expected-preview-sha256')
    run(p.parse_args())


if __name__ == '__main__':
    main()
