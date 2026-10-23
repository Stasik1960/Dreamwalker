"""One bound real-domain before/after probe; root runs, read-only plan/old DB.

Before archives the actual four V15 consumers and reproduces exactly one
poseFragments-only mismatch. After proves only the reviewed draft changed and
checks the same real domain plus narrow negative comparisons. No full gate,
world, queue replay, or accepted fixture is run.
"""
import argparse
import ast
import copy
import shutil
import sqlite3
from pathlib import Path
from unittest.mock import patch
import migrate as m
import native_certificates as c

BASE = {'migrate': 'c723e946f7d95e7edd4423c3f32e96c67a0af1c0f649b5e5f7ce90aef915c15f',
        'nativeResume': '54293b555fa02cf47a37be67f83a6d5d9ec40c3137db2ed8435df5daf8096e98',
        'nativeCertificates': '100a26987fad42863a8649261536f3e6cb192e61ad51decde4e656e6a3f5dc53'}
FILES = {'migrate.py': 'migrate', 'native_resume.py': 'nativeResume',
         'native_certificates.py': 'nativeCertificates'}
CONTEXT_SHA = 'f04362e1021287ac16f613d85ff85033d92f5b315c4bb71112df45b3f09b6d45'


def ref(path):
    return {'path': str(Path(path).resolve(strict=True)), 'sha256': m.sha(path)}


def read_reference(path, digest):
    m.require(m.sha(path) == digest, 'Explicit probe evidence changed')
    return m.read(path)


def patched_source(before, draft):
    active = False
    hunk = []
    def flush(text):
        if not hunk:
            return text
        old = '\n'.join(line[1:] for line in hunk if line.startswith((' ', '-'))) + '\n'
        new = '\n'.join(line[1:] for line in hunk if line.startswith((' ', '+'))) + '\n'
        m.require(old.strip() and text.count(old) == 1, 'Draft hunk does not uniquely match archived V15')
        hunk.clear()
        return text.replace(old, new, 1)
    for line in draft.splitlines():
        if line.startswith('*** Update File: '):
            m.require(not active and Path(line.split(': ', 1)[1]).name == 'native_certificates.py', 'Only one certificate source may change')
            active = True
        elif line == '@@' or line.startswith('*** End Patch'):
            before = flush(before)
        elif line.startswith('*** '):
            continue
        else:
            m.require(active and line and line[0] in ' +- ', 'Unexpected reviewed patch syntax')
            hunk.append(line)
    return flush(before)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--plan', type=Path, default=m.DEFAULT_PLAN)
    parser.add_argument('--phase', choices=('before', 'after'), required=True)
    parser.add_argument('--append-proof', type=Path, required=True)
    parser.add_argument('--append-proof-sha256', required=True)
    parser.add_argument('--draft', type=Path, required=True)
    parser.add_argument('--draft-sha256', required=True)
    parser.add_argument('--before-proof', type=Path)
    parser.add_argument('--before-proof-sha256')
    parser.add_argument('--proof-out', type=Path)
    parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args()
    m.require(not args.out.exists(), 'Fresh probe output required')
    lineage = read_reference(args.append_proof, args.append_proof_sha256)
    m.require(lineage['status'] == 'PASS' and
              lineage['mode'] == 'STRICT_ADDITIVE_NATIVE_EXPORT_LINEAGE' and
              all(lineage.get(key) is True for key in ('posesUnchanged', 'fragmentsUnchanged', 'existingNativeRowsUnchanged', 'existingNativeVisualFragmentsUnchanged')), 'Need the exact accepted strict append')
    m.require(m.sha(args.draft) == args.draft_sha256, 'Reviewed draft changed')
    draft_ref = ref(args.draft)
    context_ref = ref(Path(m.__file__).with_name('context_stage.py'))
    m.require(context_ref['sha256'] == CONTEXT_SHA, 'Context source changed')
    current = c.source_files(m.module_api())
    before = None
    if args.phase == 'before':
        m.require(current == BASE and not hasattr(c.SourceVisualReservations, 'domain_equal'), 'Before probe needs exact archived V15 source')
        archive = args.out.parent / (args.out.stem + '.sources')
        archive.mkdir(parents=True, exist_ok=False)
        archived = {}
        for name in (*FILES, 'context_stage.py'):
            target = archive / name
            shutil.copyfile(Path(m.__file__).with_name(name), target)
            archived[name] = ref(target)
    else:
        m.require(args.before_proof and args.before_proof_sha256, 'After phase requires the exact real before probe')
        m.require(args.proof_out and not args.proof_out.exists() and args.proof_out.resolve() != args.out.resolve(), 'After phase requires a fresh separate source-upgrade proof')
        before = read_reference(args.before_proof, args.before_proof_sha256)
        m.require(before['status'] == 'PASS' and before['kind'] == 'FINITE_REAL_DOMAIN_STRICT_APPEND_COMPARATOR_PROBE' and before['stage'] == 'BEFORE' and before['reproducedBindingMismatch'] is True and before['sourceFiles'] == BASE and before['sourceDraft'] == draft_ref and before['strictAppend'] == ref(args.append_proof), 'Before probe belongs to another reviewed source/append')
        archived = before['archivedSources']
        for name, reference in archived.items():
            m.require(m.sha(reference['path']) == reference['sha256'], 'Archived V15 source changed')
            if name != 'native_certificates.py':
                m.require(m.sha(Path(m.__file__).with_name(name)) == reference['sha256'], 'An unrelated consumer changed')
        expected_text = patched_source(Path(archived['native_certificates.py']['path']).read_text(encoding='utf-8-sig'), args.draft.read_text(encoding='utf-8-sig'))
        actual_text = Path(c.__file__).read_text(encoding='utf-8-sig')
        m.require(ast.dump(ast.parse(expected_text), include_attributes=False) == ast.dump(ast.parse(actual_text), include_attributes=False), 'Current source differs from the exact comparator-only draft')
    db = sqlite3.connect('file:' + (args.plan.resolve() / 'plan.sqlite').as_posix() + '?mode=ro', uri=True)
    context = None
    try:
        db.execute('PRAGMA query_only=ON')
        db.execute('BEGIN')
        c.attach_source_journal_original(m.module_api(), db)
        context = c.SourceVisualReservations(m.module_api(), db)
        prior_transition = m.meta(db, 'nativeResumeSourceTransition')
        m.require(prior_transition and m.meta(db, 'nativeResumeSourceBinding')['sourceFiles'] == BASE,
                  'Probe requires the same still-admitted V15 source generation')
        if before:
            sequence = before['selectedEvent']['sequence']
            saved = db.execute('SELECT data,digest FROM source_local_retentions WHERE sequence=?', (sequence,)).fetchone()
            m.require(saved and saved[1] == before['selectedEvent']['digest'], 'Selected committed event changed')
            event = m.json.loads(saved[0])
            expected = before['expectedDomain']
            m.require(expected in event['originalDomains'], 'Selected real original domain changed')
        else:
            expected = None
            for sequence, encoded, digest in db.execute('SELECT sequence,data,digest FROM source_local_retentions ORDER BY sequence'):
                event = m.json.loads(encoded)
                for domain in event['originalDomains']:
                    if domain['mechanism'] == 'source_original_snapshot' and domain.get('rootBeDigest') is not None and domain['bindings']['poseFragments']['sha256'] == lineage['beforeFragmentsSha256']:
                        expected, saved = domain, (encoded, digest)
                        break
                if expected is not None:
                    break
            m.require(expected is not None, 'No accepted historical original snapshot with typed root BE was found')
        m.require(m.digest(event) == saved[1] and event['sourceBinding'] == m.meta(db, 'binding'), 'Selected event/source digest changed')
        obj = expected['object']
        if expected.get('runtimeFootprintEnvelope'):
            context.__dict__.setdefault('_saved_envelopes', {})[obj['pose']] = expected['runtimeFootprintEnvelope']
        actual = context.original_domain(obj['dim'], obj['uuid'])
        normalized = copy.deepcopy(expected)
        normalized['bindings']['poseFragments'] = actual['bindings']['poseFragments']
        m.require(actual != expected and normalized == actual and
                  actual['bindings']['poseFragments']['sha256'] == lineage['afterFragmentsSha256'] and
                  actual['bindings']['poseFragments']['path'] == expected['bindings']['poseFragments']['path'], 'Mismatch is not exclusively the accepted poseFragments append')
        report = {'schemaVersion': 1, 'sourceFiles': current, 'oldSourceFiles': BASE,
                  'sourceBinding': m.meta(db, 'binding'), 'archivedSources': archived,
                  'priorSourceTransition': prior_transition,
                  'sourceDraft': draft_ref, 'strictAppend': ref(args.append_proof),
                  'selectedEvent': {'sequence': sequence, 'digest': saved[1]},
                  'expectedDomain': expected, 'actualDomain': actual,
                  'differingFields': ['bindings.poseFragments.sha256'],
                  'rawEqualityRejected': True, 'databaseWrites': 0, 'worldReads': 0,
                  'mapWrites': 0, 'minecraftRuns': 0, 'oldTestsRepeated': False,
                  'naturalFailureClaimed': False, 'noNaturalFailureClaimed': True, 'gameplayPass': False}
        if args.phase == 'before':
            report.update(status='PASS', kind='FINITE_REAL_DOMAIN_STRICT_APPEND_COMPARATOR_PROBE',
                          stage='BEFORE', reproducedBindingMismatch=True)
        else:
            generation = m.meta(db, 'compositionGeneration')
            cursor = m.meta(db, 'nativeComposeCursor')
            lineage_binding = m.meta(db, 'nativeFragmentAppendLineage')
            m.require(generation and cursor and cursor['phase'] == 'FINALIZING' and
                      not db.execute('SELECT 1 FROM cell_queue WHERE processed=0 LIMIT 1').fetchone() and
                      generation['compositionsSha256'] == m.meta(db, 'compositionsSha256') == m.sha(args.plan / 'compositions.jsonl') and
                      lineage_binding == ref(args.append_proof),
                      'After probe requires the exact completed generation and explicit append-only lineage binding')
            report.update(completedGeneration=generation, completedRaw=ref(args.plan / 'compositions.jsonl'),
                          cursor=cursor, nativeFragmentAppendLineage=lineage_binding,
                          lineageBindingOnly=True, noMapDataChange=True)
            m.require(context.domain_equal(actual, expected), 'Real accepted original domain failed the append comparator')
            bad_be = copy.deepcopy(actual)
            bad_be['rootBeDigest'] = '0' * 64 if actual['rootBeDigest'] != '0' * 64 else '1' * 64
            bad_other = copy.deepcopy(actual)
            bad_other['root'] = [actual['root'][0] + 1, *actual['root'][1:]]
            m.require(not context.domain_equal(bad_be, expected) and not context.domain_equal(bad_other, expected), 'Comparator accepted changed typed BE or original frame')
            real_meta = m.meta
            def wrong_append(handle, name, *values):
                if handle is db and name == 'nativeFragmentAppendLineage' and not values:
                    return {'path': str(args.append_proof.resolve()), 'sha256': '0' * 64}
                return real_meta(handle, name, *values)
            rejected = False
            try:
                with patch.object(m, 'meta', side_effect=wrong_append):
                    rejected = not context.domain_equal(actual, expected)
            except RuntimeError:
                rejected = True
            m.require(rejected, 'Wrong append authority was accepted')
            report.update(status='PASS', kind='FINITE_REAL_DOMAIN_STRICT_APPEND_COMPARATOR_PROBE', stage='AFTER',
                          beforeMismatchProof=ref(args.before_proof), actualRealDomainPassed=True,
                          mutatedTypedBeRejected=True, changedOtherDomainFieldRejected=True,
                          wrongAppendRejected=True, exactReviewedAstDeltaOnly=True,
                          checks={'realAcceptedDomain': True, 'changedTypedBeRejected': True,
                                  'changedOtherFieldRejected': True, 'wrongAppendRejected': True},
                          fixtures={'run': 1, 'failures': 0, 'errors': 0})
        m.require(c.source_files(m.module_api()) == current and m.sha(args.draft) == draft_ref['sha256'] and m.sha(args.append_proof) == args.append_proof_sha256, 'Probe source/evidence changed')
        args.out.parent.mkdir(parents=True, exist_ok=True)
        m.atomic_json(args.out, report)
        if args.phase == 'after':
            postfinal = m.meta(db, 'postfinalOriginalSections')
            m.require(prior_transition == before['priorSourceTransition'], 'Prior source transition changed')
            if postfinal:
                c.bound(m.module_api(), postfinal)
            proof = {'schemaVersion': 1, 'status': 'PASS_SOURCE_AND_TARGETED_FIXTURES_ONLY',
                     'kind': 'NATIVE_CERTIFICATE_CONSUMER_SOURCE_UPGRADE',
                     'sourceBinding': report['sourceBinding'], 'oldSourceFiles': BASE,
                     'sourceFiles': current, 'priorSourceTransition': prior_transition,
                     'beforeMismatchProof': ref(args.before_proof), 'actualFailedRun': ref(args.before_proof),
                     'failureEvidenceNature': 'EXPECTED_READ_ONLY_DOMAIN_EQUALITY_REJECTION_NOT_NATIVE_PROCESS_FAILURE',
                     'afterSourceDomainProbe': ref(args.out), 'strictAppend': ref(args.append_proof),
                     'completedGeneration': generation, 'completedRaw': report['completedRaw'],
                     'cursor': cursor, 'nativeFragmentAppendLineage': lineage_binding,
                     'lineageBindingOnly': True, 'noMapDataChange': True,
                     'contextStageSha256': CONTEXT_SHA,
                     'comparatorBaseline': archived['native_certificates.py'],
                     'oldArchivedSources': {key: archived[name] for name, key in FILES.items()},
                     'sourceDraft': draft_ref, 'probeSource': ref(__file__),
                     'noNaturalFailureClaimed': True, 'naturalFailureClaimed': False,
                     'exactReviewedAstDeltaOnly': True, 'fixtures': report['fixtures'],
                     'oldTestsRepeated': False, 'databaseWrites': 0, 'worldReads': 0,
                     'mapWrites': 0, 'minecraftRuns': 0, 'gameplayPass': False}
            if postfinal:
                proof['postfinalOriginalSections'] = postfinal
            args.proof_out.parent.mkdir(parents=True, exist_ok=True)
            m.atomic_json(args.proof_out, proof)
        print(m.canonical({'status': report['status'], 'out': ref(args.out),
                           'realDomainsRead': 1, 'databaseWrites': 0, 'gameplayPass': False}))
    finally:
        if context is not None:
            context.close()
        db.close()


if __name__ == '__main__':
    main()
