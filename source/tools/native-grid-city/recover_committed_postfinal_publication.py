"""Publish exact committed derived bytes after an interrupted file-only step."""
import argparse
import os
import shutil
import sqlite3
import stat
from pathlib import Path
import migrate as m
import native_certificates as c
import native_resume as n


def regular(path):
    m.require(path.is_file() and not path.is_symlink() and
              not getattr(path.stat(), 'st_file_attributes', 0) & stat.FILE_ATTRIBUTE_REPARSE_POINT,
              'Expected a regular publication file: ' + str(path))


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--plan', type=Path, required=True)
    p.add_argument('--receipt', type=Path, required=True)
    p.add_argument('--receipt-sha256', required=True)
    p.add_argument('--out', type=Path, required=True)
    a = p.parse_args()
    plan = m.checked(a.plan)
    reference = {'path': str(m.checked(a.receipt)), 'sha256': a.receipt_sha256}
    receipt = c.bound(m.module_api(), reference)
    m.require(receipt['status'] == 'PASS' and receipt['kind'] == 'COMMITTED_POSTFINAL_NATIVE_VISUAL_AMENDMENT',
              'A committed native visual amendment receipt is required')
    db = sqlite3.connect('file:' + (plan / 'plan.sqlite').as_posix() + '?mode=ro', uri=True)
    try:
        db.execute('PRAGMA query_only=ON')
        db.execute('BEGIN')
        m.require(m.meta(db, 'postfinalNativeVisualAmendment') == reference and
                  m.meta(db, 'compositionGeneration') == receipt['afterGeneration'] and
                  m.meta(db, 'compositionsSha256') == receipt['afterGeneration']['compositionsSha256'] and
                  m.meta(db, 'binding') == receipt['sourceBinding'] and
                  c.source_files(m.module_api()) == receipt['sourceFiles'], 'Durable commit/source authority differs')
        for key, value in receipt['stableState'].items():
            m.require(m.meta(db, key) == value, 'Committed stable metadata changed: ' + key)
        actual_counts = {**n.counts(db), **{table: db.execute('SELECT count(*) FROM ' + table).fetchone()[0]
                         for table in ('placements', 'compositions', 'cleanup_owners')}}
        m.require(actual_counts == receipt['afterCounts'] and
                  not db.execute('SELECT 1 FROM cell_queue WHERE processed=0 LIMIT 1').fetchone(),
                  'Durable committed counts/pending state differ')
        geometry = receipt['afterGeneration']['sourceFragments']
        m.require(m.meta(db, 'poseFragments') == geometry and m.sha(geometry['path']) == geometry['sha256'],
                  'Committed geometry binding changed')
        for value in [receipt['preview'], *receipt['evidence'].values()]:
            m.require(m.sha(value['path']) == value['sha256'], 'Committed publication evidence changed')
        expected = {str(plan / name) for name in ('compositions.jsonl', 'native-states-needed.json')}
        m.require(len(receipt['derivedFiles']) == 2 and {item['target'] for item in receipt['derivedFiles']} == expected,
                  'Only the two exact committed plan aliases may be published')
        prepared = []
        for item in receipt['derivedFiles']:
            proposed, target = m.checked(item['proposed']['path']), m.checked(item['target'])
            regular(proposed)
            regular(target)
            digest = item['proposed']['sha256']
            temporary = target.with_suffix(target.suffix + '.postfinal-writing')
            m.require(m.sha(proposed) == digest and m.sha(target) in (item['beforeSha256'], digest),
                      'Neither accepted old nor committed new publication bytes are present')
            if temporary.exists():
                regular(temporary)
                m.require(m.sha(temporary) == digest, 'Interrupted temporary file contains another result')
            prepared.append((proposed, target, temporary, digest))
        # No file is changed until every durable authority and both byte sets pass.
        for proposed, target, temporary, digest in prepared:
            if m.sha(target) == digest:
                continue
            if temporary.exists():
                with temporary.open('r+b') as stream:
                    stream.flush()
                    os.fsync(stream.fileno())
            else:
                with proposed.open('rb') as source, temporary.open('xb') as stream:
                    shutil.copyfileobj(source, stream, 1024 * 1024)
                    stream.flush()
                    os.fsync(stream.fileno())
            m.require(m.sha(temporary) == digest, 'Staged publication bytes differ')
            os.replace(temporary, target)
            m.require(m.sha(target) == digest, 'Published bytes differ')
        result = {'schemaVersion': 1, 'status': 'PASS', 'kind': 'RECOVERED_COMMITTED_POSTFINAL_PUBLICATION',
                  'producerSha256': m.sha(__file__), 'commit': reference,
                  'durableCommitMetadataVerified': True, 'afterCounts': actual_counts,
                  'published': [{'path': str(target), 'sha256': digest} for _, target, _, digest in prepared],
                  'persistentDatabaseWrites': 0, 'mapWrites': 0, 'dataAmendmentRepeated': False}
        c.immutable_json(m.module_api(), a.out.resolve(), result)
        print(m.canonical(result))
    finally:
        db.close()


if __name__ == '__main__':
    main()
