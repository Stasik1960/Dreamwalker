"""Split completed bridge composition keys by their two observed host flags."""
import argparse
import copy
import os
import shutil
import sqlite3
import stat
import sys
from pathlib import Path

import migrate as m
import native_certificates as c
import native_resume as n
import retain_postfinal_original_sections as shared

sys.path.insert(0, str(m.PROJECT / 'tools/native-grid/compiler'))
import catalog_normalize_carriers as carrier


def ref(path):
    return {'path': str(m.checked(path)), 'sha256': m.sha(path)}


def counts(db):
    return {**n.counts(db), **{name: db.execute('SELECT count(*) FROM ' + name).fetchone()[0]
                              for name in ('placements', 'compositions', 'cleanup_owners')}}


def stable(db):
    return {key: m.meta(db, key) for key in ('binding', 'nativeComposeCursor', 'nativeLocalRetention',
            'sourceLocalRetentionJournal', 'sourceVisualReservationJournal', 'nativeResumeSourceBinding',
            'nativeResumeSourceTransition', 'poseFragments', 'nativeFragmentAppendLineage',
            'fullNativeFunctionGate', 'fullNativeFunctionDecisions', 'plannedBeSafety',
            'postfinalNativeVisualAmendment')}


def point(db, dim, at):
    rows = n.row_dict(db, 'SELECT p.*,b.host_state FROM placements p JOIN bridge_cells b '
                     'ON p.dim=b.dim AND p.x=b.x AND p.y=b.y AND p.z=b.z '
                     'WHERE p.dim=? AND p.x=? AND p.y=? AND p.z=?', (dim, *at))
    m.require(len(rows) == 1, 'Expected one exact existing bridge placement')
    return rows[0]


def publish(plan, receipt):
    """Publish only the committed aliases, using writable fsync handles."""
    expected = {str(plan / name) for name in ('compositions.jsonl', 'native-states-needed.json')}
    m.require(len(receipt['derivedFiles']) == 2 and {row['target'] for row in receipt['derivedFiles']} == expected,
              'Only the two exact derived plan aliases may be published')
    prepared = []
    for row in receipt['derivedFiles']:
        source, target = m.checked(row['proposed']['path']), m.checked(row['target'])
        temporary = target.with_suffix(target.suffix + '.carrier-split-writing')
        for path in (source, target, *([temporary] if temporary.exists() else [])):
            m.require(path.is_file() and not path.is_symlink() and
                      not getattr(path.stat(), 'st_file_attributes', 0) & stat.FILE_ATTRIBUTE_REPARSE_POINT,
                      'Derived publication requires regular non-reparse files')
        digest = row['proposed']['sha256']
        m.require(m.sha(source) == digest and m.sha(target) in (row['beforeSha256'], digest) and
                  (not temporary.exists() or m.sha(temporary) == digest), 'Derived publication before/proposed bytes changed')
        prepared.append((source, target, temporary, digest))
    for source, target, temporary, digest in prepared:
        if m.sha(target) == digest:
            continue
        if temporary.exists():
            with temporary.open('r+b') as stream:
                stream.flush()
                os.fsync(stream.fileno())
        else:
            with source.open('rb') as original, temporary.open('xb') as stream:
                shutil.copyfileobj(original, stream, 1024 * 1024)
                stream.flush()
                os.fsync(stream.fileno())
        m.require(m.sha(temporary) == digest, 'Staged carrier-split bytes changed')
        os.replace(temporary, target)
        m.require(m.sha(target) == digest, 'Published carrier-split bytes changed')


def run(a):
    plan, output = m.checked(a.plan), a.out.resolve()
    evidence = {'conflicts': {'path': str(m.checked(a.scope)), 'sha256': a.scope_sha256},
                'producer': ref(__file__), 'normalizer': ref(carrier.__file__), 'compositionHelper': ref(shared.__file__)}
    scope = c.bound(m.module_api(), evidence['conflicts'])
    m.require(scope['status'] == 'PASS_SOURCE_FACTS_NOT_ADMISSION' and
              scope['kind'] in ('SHARED_CARRIER_PREDICATE_CONFLICTS', 'UNQUALIFIED_CARRIER_PREDICATE_BINDINGS') and
              0 < scope['conflictKeys'] == len(scope['conflicts']) <= 4096 and 0 < scope['affectedPlacements'] <= 32768,
              'Exact bounded observed carrier conflicts are required')
    m.require(carrier.compiler_fingerprint() == carrier.ALGORITHM, 'Pinned normalizer/compiler changed')
    for index, value in enumerate(scope['audits']):
        m.require(m.sha(value['path']) == value['sha256'], 'Observed carrier audit changed')
        evidence['audit' + str(index)] = {'path': value['path'], 'sha256': value['sha256']}
    known, _ = carrier.audit_states([Path(value['path']) for value in scope['audits']])
    evidence['functionGate'] = {'path': scope['fullFunctionGate']['path'], 'sha256': scope['fullFunctionGate']['sha256']}
    gate = c.bound(m.module_api(), evidence['functionGate'])
    m.require(gate['status'] == 'PASS' and gate['scope'] == 'FULL_CITY' and gate['sourceBinding'] == scope['sourceBinding'],
              'The unchanged complete exact function gate is required')
    decisions = {carrier.state_key(row['state']): row['decision'] for row in gate['states']}
    directory = output.parent / (output.stem + '.artifacts')
    directory.mkdir(parents=True, exist_ok=True)
    receipt_path = directory / 'receipt.json'
    db = sqlite3.connect('file:' + (plan / 'plan.sqlite').as_posix() + '?mode=rw', uri=True)
    try:
        committed = m.meta(db, 'postfinalCarrierPredicateSplit')
        if committed and Path(committed['path']) == receipt_path:
            receipt = c.bound(m.module_api(), committed)
            m.require(a.execute and Path(committed['path']) == receipt_path and receipt['evidence'] == evidence and
                      receipt['sourceFiles'] == c.source_files(m.module_api()) and stable(db) == receipt['stableState'] and
                      counts(db) == receipt['afterCounts'] and m.meta(db, 'compositionGeneration') == receipt['afterGeneration'] and
                      m.meta(db, 'compositionsSha256') == receipt['afterGeneration']['compositionsSha256'],
                      'Committed predicate split no longer matches the exact completed generation')
            for value in evidence.values():
                m.require(m.sha(value['path']) == value['sha256'], 'Committed split evidence changed')
            publish(plan, receipt)
            print(m.canonical({'status': 'PASS_IDEMPOTENT_COMMITTED', 'receipt': committed}))
            return
        generation, _ = shared.preconditions(db, plan, scope['raw']['sha256'])
        c.active_binding(m.module_api(), db)
        before_state, before_counts = stable(db), counts(db)
        m.require(generation == scope['generation'] and before_state['binding'] == scope['sourceBinding'] and
                  all(before_state['fullNativeFunctionGate'][key] == evidence['functionGate'][key] for key in ('path', 'sha256')),
                  'Completed raw/source/function evidence differs from the actual conflict scope')
        prior_ref = before_state['postfinalNativeVisualAmendment']
        prior = c.bound(m.module_api(), prior_ref)
        m.require(prior['status'] == 'PASS', 'The accepted native visual amendment did not pass')
        prior_split_ref = committed
        if prior_split_ref:
            previous = c.bound(m.module_api(), prior_split_ref)
            m.require(previous['status'] == 'PASS' and previous['kind'] == 'COMMITTED_POSTFINAL_CARRIER_PREDICATE_SPLIT' and
                      previous['sourceBinding'] == before_state['binding'] and previous['afterGeneration'] == generation and
                      previous['afterCounts'] == before_counts and previous['priorNativeVisualAmendment'] == prior_ref,
                      'The prior committed predicate split is not this exact completed generation')
        else:
            previous = prior
            m.require(prior['afterGeneration'] == generation,
                      'The accepted native visual amendment is not this completed generation')
        before_raw = next(row['proposed'] for row in previous['derivedFiles'] if Path(row['target']) == plan / 'compositions.jsonl')
        m.require(m.sha(before_raw['path']) == before_raw['sha256'] == scope['raw']['sha256'],
                  'The immutable accepted pre-split raw composition bytes changed')
        changes, composition_changes, snapshots, clones = [], [], {}, {}
        for conflict in scope['conflicts']:
            old_key, before = conflict['key'], conflict['composition']
            encoded = db.execute('SELECT data FROM compositions WHERE key=?', (old_key,)).fetchone()
            m.require(encoded and m.json.loads(encoded[0]) == before and before.get('wrapper') == 'NativeGridBase' and
                      before.get('optics') is None and before.get('carrierPredicates') is None,
                      'Only the exact conflicting raw bridge representation may be split')
            for group in conflict['groups']:
                predicates = group['predicates']
                m.require(set(predicates) == set(carrier.FLAGS) and all(type(value) is bool for value in predicates.values()) and
                          set(group['hosts']) == {row['host_state'] for row in group['placements']},
                          'A conflict group lost its exact two observed booleans/hosts')
                after = {**copy.deepcopy(before), 'carrierPredicates': predicates}
                new_key = 'cell_' + m.digest(after)
                m.require({key: value for key, value in after.items() if key != 'carrierPredicates'} ==
                          {key: value for key, value in before.items() if key != 'carrierPredicates'},
                          'A carrier split changed geometry/physics/data ingredients')
                clones[new_key] = after
                composition_changes.append({'oldKey': old_key, 'newKey': new_key, 'before': before,
                                            'after': after, 'carrierPredicates': predicates})
                for saved in group['placements']:
                    dim, at = saved['dim'], tuple(saved[key] for key in ('x', 'y', 'z'))
                    token = (dim, *at)
                    m.require(token not in snapshots and point(db, dim, at) == saved and saved['composition'] == old_key,
                              'Conflicting bridge placement beforeimage changed/duplicated')
                    host = m.json.loads(saved['host_state'])
                    m.require(known.get(carrier.state_key(host)) == predicates and
                              decisions.get(carrier.state_key(host)) == 'bridge_host_only',
                              'A split carrier flag differs from its exact actual host audit/function decision')
                    snapshots[token] = saved
                    changes.append({'dimension': dim, 'position': list(at), 'oldComposition': old_key,
                                    'newComposition': new_key, 'hostState': host, 'carrierPredicates': predicates})
        m.require(len(changes) == scope['affectedPlacements'], 'The exact affected bridge placement set changed')
        db.execute('PRAGMA synchronous=FULL')
        db.execute('BEGIN IMMEDIATE')
        shared.preconditions(db, plan, scope['raw']['sha256'])
        m.require(stable(db) == before_state and counts(db) == before_counts and
                  m.meta(db, 'postfinalCarrierPredicateSplit') == prior_split_ref, 'Completed plan changed before the split')
        for token, saved in snapshots.items():
            m.require(point(db, token[0], token[1:]) == saved, 'A bridge beforeimage changed before the first write')
        for key, body in clones.items():
            db.execute('INSERT OR IGNORE INTO compositions VALUES(?,?,NULL)', (key, m.canonical(body)))
            m.require(m.json.loads(db.execute('SELECT data FROM compositions WHERE key=?', (key,)).fetchone()[0]) == body,
                      'A content-addressed carrier clone already has different data')
        for change in changes:
            result = db.execute('UPDATE placements SET composition=? WHERE dim=? AND x=? AND y=? AND z=? AND composition=?',
                                (change['newComposition'], change['dimension'], *change['position'], change['oldComposition']))
            m.require(result.rowcount == 1, 'The exact bridge placement was not split once')
            token = (change['dimension'], *change['position'])
            m.require(point(db, token[0], token[1:]) == {**snapshots[token], 'composition': change['newComposition']},
                      'The split changed a bridge/source placement field other than its composition key')
        old_keys = {row['key'] for row in scope['conflicts']}
        referenced = {row[0] for row in db.execute('SELECT DISTINCT composition FROM placements WHERE composition IN (' +
                      ','.join('?' for _ in old_keys) + ')', tuple(sorted(old_keys)))}
        for key in sorted(old_keys - referenced):
            db.execute('DELETE FROM compositions WHERE key=?', (key,))
        after_counts = counts(db)
        m.require(all(after_counts[key] == value for key, value in before_counts.items() if key != 'compositions') and
                  stable(db) == before_state and m.meta(db, 'postfinalCarrierPredicateSplit') == prior_split_ref,
                  'The metadata split changed unrelated source/queue/data work')
        files = shared.write_artifacts(db, plan, directory)
        m.require(m.sha(files[1]) == m.sha(plan / 'native-states-needed.json'),
                  'The carrier metadata split changed native physics/function input states')
        generation_after = {**generation, 'compositionsSha256': m.sha(files[0]),
                            'postfinalCarrierPredicateSplit': {'producer': evidence['producer'], 'conflicts': evidence['conflicts'],
                                                              'previousCompositionsSha256': generation['compositionsSha256']}}
        preview = {'schemaVersion': 1, 'status': 'POSTFINAL_CARRIER_PREDICATE_SPLIT_PREVIEW_NOT_COMMITTED',
                   'kind': 'FINITE_POSTFINAL_CARRIER_PREDICATE_SPLIT', 'sourceBinding': before_state['binding'],
                   'sourceFiles': c.source_files(m.module_api()), 'evidence': evidence, 'stableState': before_state,
                   'beforeGeneration': generation, 'afterGeneration': generation_after, 'beforeRaw': before_raw,
                   'priorNativeVisualAmendment': prior_ref, 'priorCarrierPredicateSplit': prior_split_ref,
                   'beforeCounts': before_counts, 'afterCounts': after_counts,
                   'changes': changes, 'compositionChanges': composition_changes, 'affectedPlacements': len(changes),
                   'cloneKeys': len(clones), 'retainedReferencedOldKeys': sorted(referenced),
                   'geometryPhysicalDataUnchanged': True, 'onlyObservedOpaqueAndSidedTransparencyChanged': True,
                   'nativeFunctionStatesUnchanged': True, 'processedRowsReset': 0, 'nativeQueueReplayed': False,
                   'worldReads': 0, 'mapWrites': 0, 'gameplayPass': False,
                   'derivedFiles': [{'target': str(plan / name), 'beforeSha256': m.sha(plan / name), 'proposed': ref(path)}
                                    for name, path in zip(('compositions.jsonl', 'native-states-needed.json'), files)]}
        for value in evidence.values():
            m.require(m.sha(value['path']) == value['sha256'], 'Split source/evidence changed before result')
        if not a.execute:
            c.immutable_json(m.module_api(), output, preview)
            db.rollback()
            print(m.canonical({'status': preview['status'], 'preview': ref(output), 'placements': len(changes), 'clones': len(clones)}))
            return
        m.require(a.expected_preview_sha256 and m.sha(output) == a.expected_preview_sha256 and m.read(output) == preview,
                  'Execution requires the exact reviewed split preview and unchanged beforeimages')
        receipt = {**preview, 'status': 'PASS', 'kind': 'COMMITTED_POSTFINAL_CARRIER_PREDICATE_SPLIT',
                   'preview': ref(output), 'commitAuthority': 'Only matching postfinalCarrierPredicateSplit meta commits this receipt'}
        c.immutable_json(m.module_api(), receipt_path, receipt)
        m.meta(db, 'compositionGeneration', generation_after)
        m.meta(db, 'compositionsSha256', generation_after['compositionsSha256'])
        m.meta(db, 'postfinalCarrierPredicateSplit', ref(receipt_path))
        db.commit()
        publish(plan, receipt)
        print(m.canonical({'status': 'PASS_COMMITTED', 'receipt': ref(receipt_path), 'placements': len(changes), 'clones': len(clones)}))
    except BaseException:
        db.rollback()
        raise
    finally:
        db.close()


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--plan', type=Path, default=m.DEFAULT_PLAN)
    p.add_argument('--scope', type=Path, required=True)
    p.add_argument('--scope-sha256', required=True)
    p.add_argument('--out', type=Path, required=True)
    modes = p.add_mutually_exclusive_group(required=True)
    modes.add_argument('--preview', action='store_true')
    modes.add_argument('--execute', action='store_true')
    p.add_argument('--expected-preview-sha256')
    run(p.parse_args())


if __name__ == '__main__':
    main()
