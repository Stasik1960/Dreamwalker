"""Root-executed, completed-generation source metadata adoption only.

Uses the existing source transition/lineage schema. No native queue, source
state, placement, journal, generation, world or runtime mutation is performed.
"""
import argparse
import os
import sqlite3
from pathlib import Path

import migrate as m
import native_certificates as c
import native_resume as n

V15 = {'migrate': 'c723e946f7d95e7edd4423c3f32e96c67a0af1c0f649b5e5f7ce90aef915c15f',
       'nativeResume': '54293b555fa02cf47a37be67f83a6d5d9ec40c3137db2ed8435df5daf8096e98',
       'nativeCertificates': '100a26987fad42863a8649261536f3e6cb192e61ad51decde4e656e6a3f5dc53'}
CONTEXT_SHA = 'f04362e1021287ac16f613d85ff85033d92f5b315c4bb71112df45b3f09b6d45'
FILENAMES = {'migrate': 'migrate.py', 'nativeResume': 'native_resume.py',
             'nativeCertificates': 'native_certificates.py', 'contextStage': 'context_stage.py'}


def ref(path):
    return {'path': str(m.checked(path)), 'sha256': m.sha(path)}


def bound(value):
    return c.bound(m.module_api(), value)


def stable_state(db):
    return {key: m.meta(db, key) for key in ('binding', 'compositionGeneration',
            'nativeComposeCursor', 'nativeLocalRetention', 'sourceLocalRetentionJournal',
            'sourceVisualReservationJournal', 'postfinalOriginalSections', 'poseFragments',
            'nativeFragmentAppendLineage')}


def snapshot(a, db):
    plan = m.checked(a.plan)
    state = stable_state(db)
    generation, cursor = state['compositionGeneration'], state['nativeComposeCursor']
    m.require(generation and cursor and cursor['phase'] == 'FINALIZING' and
              generation['compositionsSha256'] == m.meta(db, 'compositionsSha256') == m.sha(plan / 'compositions.jsonl') and
              not db.execute('SELECT 1 FROM cell_queue WHERE processed=0 LIMIT 1').fetchone(),
              'Final-stage source adoption requires a completed published generation')
    m.require(not m.meta(db, 'applyStarted') and not m.meta(db, 'frozenInputs') and
              not db.execute("SELECT 1 FROM files WHERE phase='committed' LIMIT 1").fetchone() and
              not db.execute("SELECT 1 FROM regions WHERE phase='committed' LIMIT 1").fetchone(),
              'Adopt before freezing/applying any full map')
    current = c.source_files(m.module_api())
    m.require(current['migrate'] == V15['migrate'] and current['nativeResume'] == V15['nativeResume'] and
              current['nativeCertificates'] != V15['nativeCertificates'] and
              m.sha(Path(m.__file__).with_name('context_stage.py')) == CONTEXT_SHA,
              'Only the reviewed strict-append comparator consumer delta is admissible')
    proof_ref = {'path': str(m.checked(a.source_proof)), 'sha256': a.source_proof_sha256}
    proof = bound(proof_ref)
    m.require(proof.get('schemaVersion') == 1 and proof['status'] == 'PASS_SOURCE_AND_TARGETED_FIXTURES_ONLY' and
              proof['kind'] == 'NATIVE_CERTIFICATE_CONSUMER_SOURCE_UPGRADE' and
              proof['oldSourceFiles'] == V15 and proof['sourceFiles'] == current and
              proof['sourceBinding'] == state['binding'] and proof['contextStageSha256'] == CONTEXT_SHA and
              proof['noNaturalFailureClaimed'] is True and proof['gameplayPass'] is False and
              proof['lineageBindingOnly'] is True and proof['noMapDataChange'] is True,
              'Exact completed-stage comparator source/probe proof required')
    old_binding = m.meta(db, 'nativeResumeSourceBinding')
    m.require(old_binding and old_binding['sourceFiles'] == V15 and
              old_binding['transition']['sha256'] == a.expected_old_transition_sha256 and
              proof['priorSourceTransition'] == old_binding['transition'] == m.meta(db, 'nativeResumeSourceTransition'),
              'The actual preceding source transition changed')
    # Read the exact old lineage without pretending the new live consumer is
    # already admitted; the walker checks every old archive/proof/ancestor.
    c.source_lineage(m.module_api(), db, old_binding)
    before = bound(proof['beforeMismatchProof'])
    after = bound(proof['afterSourceDomainProbe'])
    for doc, stage, files in ((before, 'BEFORE', V15), (after, 'AFTER', current)):
        m.require(doc['status'] == 'PASS' and doc['kind'] == 'FINITE_REAL_DOMAIN_STRICT_APPEND_COMPARATOR_PROBE' and
                  doc['stage'] == stage and doc['sourceFiles'] == files and doc['sourceBinding'] == state['binding'] and
                  doc['strictAppend'] == proof['strictAppend'], 'Actual before/after real-domain probe binding changed')
    m.require(before['reproducedBindingMismatch'] is True and after['checks'] ==
              {'realAcceptedDomain': True, 'changedTypedBeRejected': True,
               'changedOtherFieldRejected': True, 'wrongAppendRejected': True},
              'The actual comparator rejection/positive/negative checks are incomplete')
    raw = {'path': str(m.checked(plan / 'compositions.jsonl')), 'sha256': generation['compositionsSha256']}
    for doc in (proof, after):
        m.require(doc['completedGeneration'] == generation and doc['completedRaw'] == raw and
                  doc['cursor'] == cursor and doc['nativeFragmentAppendLineage'] ==
                  state['nativeFragmentAppendLineage'] == proof['strictAppend'] and
                  doc['lineageBindingOnly'] is True and doc['noMapDataChange'] is True,
                  'The actual completed-generation/lineage-only probe state changed')
    m.require(proof['actualFailedRun'] == proof['beforeMismatchProof'],
              'Failure slot must honestly bind the read-only mismatch, not an invented native failure')
    append = bound(proof['strictAppend'])
    m.require(append['status'] == 'PASS' and append['mode'] == 'STRICT_ADDITIVE_NATIVE_EXPORT_LINEAGE' and
              all(append[key] is True for key in ('posesUnchanged', 'fragmentsUnchanged',
                  'existingNativeRowsUnchanged', 'existingNativeVisualFragmentsUnchanged')) and
              append['afterFragmentsSha256'] == state['poseFragments']['sha256'] == m.sha(state['poseFragments']['path']),
              'Current exact strict append authority changed')
    if proof.get('postfinalOriginalSections'):
        m.require(state['postfinalOriginalSections'] == proof['postfinalOriginalSections'],
                  'Optional postfinal original-section evidence is not committed')
        bound(state['postfinalOriginalSections'])
    archives = {}
    for name, filename in FILENAMES.items():
        path = m.checked(a.before_sources / filename)
        m.require(m.sha(path) == (CONTEXT_SHA if name == 'contextStage' else V15[name]),
                  'Archive all actual old sources before patching the consumer')
        archives[filename] = {'archive': str(path), 'sha256': m.sha(path)}
    return {'schemaVersion': 1, 'status': 'NATIVE_SOURCE_UPGRADE_PREVIEW',
            'stage': 'COMPLETED_POSTFINAL_METADATA_ONLY', 'sourceBinding': state['binding'],
            'semanticBindings': n.snapshot_bindings(m.module_api(), db),
            'parentRetention': state['nativeLocalRetention'], 'cursor': cursor,
            'previousSourceTransition': old_binding['transition'], 'beforeCounts': n.counts(db),
            'fromSourceFiles': V15, 'toSourceFiles': current,
            'failedRun': proof['beforeMismatchProof'], 'failedArchives': archives,
            'sourceProof': proof_ref, 'completedState': state,
            'noNaturalFailureClaimed': True, 'databaseWrites': 0, 'mapWrites': 0,
            'producer': ref(__file__)}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--plan', type=Path, default=m.DEFAULT_PLAN)
    parser.add_argument('--source-proof', type=Path, required=True)
    parser.add_argument('--source-proof-sha256', required=True)
    parser.add_argument('--before-sources', type=Path, required=True)
    parser.add_argument('--expected-old-transition-sha256', required=True)
    parser.add_argument('--preview', type=Path, required=True)
    parser.add_argument('--execute', action='store_true')
    parser.add_argument('--expected-preview-sha256')
    a = parser.parse_args()
    plan = m.checked(a.plan)
    db = sqlite3.connect('file:' + (plan / 'plan.sqlite').as_posix() + ('?mode=rw' if a.execute else '?mode=ro'), uri=True)
    try:
        if not a.execute:
            db.execute('PRAGMA query_only=ON')
        else:
            db.execute('PRAGMA synchronous=FULL')
        db.execute('BEGIN IMMEDIATE' if a.execute else 'BEGIN')
        if a.execute:
            m.require(a.expected_preview_sha256 and m.sha(a.preview) == a.expected_preview_sha256, 'Exact reviewed metadata preview SHA required')
            active = m.meta(db, 'nativeResumeSourceBinding')
            if active and active['sourceFiles'] == c.source_files(m.module_api()):
                transition = c.active_binding(m.module_api(), db)
                m.require(transition['stage'] == 'COMPLETED_POSTFINAL_METADATA_ONLY' and
                          transition['preview']['sha256'] == a.expected_preview_sha256 and
                          transition['sourceProof']['sha256'] == a.source_proof_sha256 and
                          transition['completedState'] == stable_state(db) and transition['afterCounts'] == n.counts(db),
                          'Another adoption owns the current completed source state')
                db.rollback()
                print(m.canonical({'status': 'PASS_IDEMPOTENT', 'transition': active['transition']}))
                return
        preview = snapshot(a, db)
        if not a.execute:
            c.immutable_json(m.module_api(), a.preview, preview)
            db.rollback()
            print(m.canonical({'status': preview['status'], 'preview': ref(a.preview)}))
            return
        m.require(preview == m.read(a.preview), 'Completed state/source/proofs changed after preview')
        directory = plan / 'full/native-consumer-source-upgrades' / a.expected_preview_sha256
        archive = directory / 'after'
        archive.mkdir(parents=True, exist_ok=True)
        archived = {}
        for name, filename in FILENAMES.items():
            path, destination = Path(m.__file__).with_name(filename), archive / filename
            payload = path.read_bytes()
            if destination.exists():
                m.require(destination.read_bytes() == payload, 'After-source archive changed')
            else:
                with destination.open('wb') as stream:
                    stream.write(payload)
                    stream.flush()
                    os.fsync(stream.fileno())
            archived['after/' + name] = ref(destination)
            old = preview['failedArchives'][filename]
            archived['before/' + name] = {'path': old['archive'], 'sha256': old['sha256']}
        proof_copy = directory / 'source-proof.json'
        c.immutable_json(m.module_api(), proof_copy, bound(preview['sourceProof']))
        transition = {**preview, 'status': 'PASS', 'kind': 'NATIVE_CONSUMER_SOURCE_ONLY_TRANSITION',
                      'sourceProof': ref(proof_copy), 'preview': ref(a.preview), 'archivedSources': archived,
                      'afterCounts': n.counts(db), 'processedQueueRowsReset': 0, 'objectsChanged': 0, 'mapWrites': 0}
        path = directory / 'transition.json'
        c.immutable_json(m.module_api(), path, transition)
        m.require(stable_state(db) == preview['completedState'] and n.counts(db) == preview['beforeCounts'] == transition['afterCounts'],
                  'Completed source-only transition changed data/cursor/generation')
        before_changes = db.total_changes
        m.meta(db, 'nativeResumeSourceTransition', ref(path))
        m.meta(db, 'nativeResumeSourceBinding', {'schemaVersion': 1, 'sourceFiles': c.source_files(m.module_api()), 'transition': ref(path)})
        m.require(db.total_changes - before_changes == 2 and stable_state(db) == preview['completedState'] and
                  n.counts(db) == preview['beforeCounts'], 'Only the two admitted-source meta keys may change')
        # Normal consumer lineage acceptance is checked before durable commit.
        c.active_binding(m.module_api(), db)
        db.commit()
        print(m.canonical({'status': 'PASS_COMMITTED_METADATA_ONLY', 'transition': ref(path)}))
    except BaseException:
        db.rollback()
        raise
    finally:
        db.close()


if __name__ == '__main__':
    main()
