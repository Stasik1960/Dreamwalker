"""Bind one reviewed native-export append in a completed, unapplied plan.

Only nativeFragmentAppendLineage is written. This does not retain sections,
replay a queue, validate old owner journals, or modify source/map data.
"""
import argparse
import sqlite3
from pathlib import Path
import migrate as m
import native_certificates as c

def reference(path):
    return {'path': str(Path(path).resolve(strict=True)), 'sha256': m.sha(path)}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--plan', type=Path, default=m.DEFAULT_PLAN)
    parser.add_argument('--append-proof', type=Path, required=True)
    parser.add_argument('--append-proof-sha256', required=True)
    parser.add_argument('--previous-proof-sha256', help='Explicit already bound direct proof SHA, required for a continued visual-only append')
    parser.add_argument('--out', type=Path, required=True)
    parser.add_argument('--execute', action='store_true', required=True)
    args = parser.parse_args()
    m.require(not args.out.exists(), 'Fresh lineage-binding observation output required')
    append_ref = reference(args.append_proof)
    m.require(append_ref['sha256'] == args.append_proof_sha256, 'Need the explicitly SHA-bound reviewed strict native append')
    proof = m.read(args.append_proof)
    m.require(proof['status'] == 'PASS' and proof['mode'] == 'STRICT_ADDITIVE_NATIVE_EXPORT_LINEAGE' and
              all(proof.get(key) is True for key in ('posesUnchanged', 'fragmentsUnchanged', 'existingNativeRowsUnchanged', 'existingNativeVisualFragmentsUnchanged')),
              'The append must preserve all prior source, physical and visual rows')
    plan = args.plan.resolve(strict=True)
    db = sqlite3.connect('file:' + (plan / 'plan.sqlite').as_posix() + '?mode=rw', uri=True)
    try:
        db.execute('PRAGMA synchronous=FULL')
        db.execute('BEGIN IMMEDIATE')
        generation = m.meta(db, 'compositionGeneration')
        cursor = m.meta(db, 'nativeComposeCursor')
        fragments = m.meta(db, 'poseFragments')
        source = m.meta(db, 'binding')
        files = c.source_files(m.module_api())
        m.require(generation and cursor and cursor['phase'] == 'FINALIZING' and
                  not db.execute('SELECT 1 FROM cell_queue WHERE processed=0 LIMIT 1').fetchone() and
                  not m.meta(db, 'applyStarted') and not m.meta(db, 'frozenInputs') and
                  generation['compositionsSha256'] == m.meta(db, 'compositionsSha256') == m.sha(plan / 'compositions.jsonl'),
                  'Lineage binding requires the exact completed unapplied generation')
        historical = generation['sourceFragments']
        m.require(historical['path'] == fragments['path'] and
                  historical['sha256'] == proof['beforeFragmentsSha256'] and
                  fragments['sha256'] == proof['afterFragmentsSha256'] == m.sha(fragments['path']),
                  'The accepted before/current export identities or actual bytes changed')
        previous = m.meta(db, 'nativeFragmentAppendLineage')
        replacing = previous is not None and previous != append_ref
        if replacing:
            m.require(args.previous_proof_sha256 and previous['sha256'] == args.previous_proof_sha256,
                      'Explicit accepted previous append proof SHA required')
            old = c.bound(m.module_api(), previous)
            m.require(old['status'] == 'PASS' and old['mode'] == proof['mode'] and
                      all(old.get(key) is True for key in ('posesUnchanged', 'fragmentsUnchanged', 'existingNativeRowsUnchanged', 'existingNativeVisualFragmentsUnchanged')) and
                      old['beforeFragmentsSha256'] == proof['beforeFragmentsSha256'] and old['snapshot'] == proof['snapshot'] and
                      old['beforeCounts'] == proof['beforeCounts'] and old['newNativeRows'] == proof['newNativeRows'] and
                      all(old['afterCounts'][key] == proof['afterCounts'][key] for key in ('poses', 'fragments', 'native')),
                      'A replacement must retain the same strict original prefix and every native/source row')
            old_chain = old.get('nativeVisualAppends') or [old['nativeVisualAppend']]
            chain = proof.get('nativeVisualAppends', [])
            identity = lambda row: (str(Path(row['path']).resolve()), row['sha256'])
            m.require(len(chain) > len(old_chain) and list(map(identity, chain[:len(old_chain)])) == list(map(identity, old_chain)),
                      'The new direct proof must preserve every earlier visual append receipt')
            continuation = chain[len(old_chain):]; expected_sha = old['afterFragmentsSha256']; expected_counts = old['afterCounts']; added = 0
            for link in continuation:
                receipt = c.bound(m.module_api(), {'path': link['path'], 'sha256': link['sha256']})
                m.require(receipt['status'] == 'PASS' and receipt['mode'] == 'EXACT_NATIVE_VISUAL_FRAGMENTS_ONLY' and receipt['newNativeRows'] == 0 and
                          all(receipt.get(key) is True for key in ('posesUnchanged', 'fragmentsUnchanged', 'existingNativeRowsUnchanged', 'existingNativeVisualFragmentsUnchanged', 'physicalAndOpticalFieldsEqual')) and
                          receipt['beforeFragmentsSha256'] == expected_sha == link['beforeFragmentsSha256'] and receipt['beforeCounts'] == expected_counts == link['beforeCounts'] and
                          receipt['afterFragmentsSha256'] == link['afterFragmentsSha256'] and receipt['afterCounts'] == link['afterCounts'],
                          'Only the explicit consecutive fragment-only continuation may replace the bound direct proof')
                expected_sha, expected_counts = receipt['afterFragmentsSha256'], receipt['afterCounts']; added += receipt['newNativeVisualFragments']
            m.require(expected_sha == proof['afterFragmentsSha256'] and expected_counts == proof['afterCounts'] and
                      proof['newNativeVisualFragments'] == old['newNativeVisualFragments'] + added,
                      'Continued direct proof does not bind the final native fragment prefix')
        changed = db.total_changes
        update = previous != append_ref
        if update:
            m.meta(db, 'nativeFragmentAppendLineage', append_ref)
        m.require(db.total_changes - changed == update and
                  m.meta(db, 'compositionGeneration') == generation and
                  m.meta(db, 'nativeComposeCursor') == cursor and m.meta(db, 'binding') == source and
                  c.source_files(m.module_api()) == files and reference(args.append_proof) == append_ref,
                  'Lineage binding changed more than its one metadata key or source/evidence changed')
        db.commit()
        m.require(m.meta(db, 'nativeFragmentAppendLineage') == append_ref, 'Committed lineage binding is absent')
        report = {'schemaVersion': 1, 'status': 'PASS',
                  'kind': 'COMPLETED_NATIVE_FRAGMENT_APPEND_LINEAGE_METADATA_ONLY',
                  'sourceBinding': source, 'sourceFiles': files,
                  'completedGeneration': generation, 'completedRaw': reference(plan / 'compositions.jsonl'),
                  'cursor': cursor, 'previousLineage': previous, 'nativeFragmentAppendLineage': append_ref,
                  'poseFragments': fragments, 'metadataRowsChanged': int(update),
                  'onlyMetadataKey': 'nativeFragmentAppendLineage', 'lineageBindingOnly': True,
                  'noMapDataChange': True, 'queueRowsChanged': 0, 'ownerDataChanged': 0,
                  'mapWrites': 0, 'worldReads': 0, 'minecraftRuns': 0,
                  'fullValidatorsReplayed': False, 'gameplayPass': False,
                  'generator': reference(__file__)}
        args.out.parent.mkdir(parents=True, exist_ok=True)
        m.atomic_json(args.out, report)
        print(m.canonical({'status': report['status'], 'out': reference(args.out),
                           'metadataRowsChanged': report['metadataRowsChanged'], 'mapWrites': 0}))
    except BaseException:
        db.rollback()
        raise
    finally:
        db.close()


if __name__ == '__main__':
    main()
