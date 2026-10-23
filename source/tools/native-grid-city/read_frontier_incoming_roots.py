"""Read only the native roots directly causing recorded missing-helper contacts."""
import argparse
import sqlite3
from pathlib import Path
import migrate as m
import native_certificates as c
import native_resume as n


def main():
    p = argparse.ArgumentParser(description=__doc__)
    for name in ('frontier', 'initial'):
        p.add_argument('--' + name, type=Path, required=True)
        p.add_argument('--' + name + '-sha256', required=True)
    p.add_argument('--plan', type=Path, required=True)
    p.add_argument('--out', type=Path, required=True)
    a = p.parse_args()
    docs = {}
    for name in ('frontier', 'initial'):
        path = getattr(a, name)
        m.require(m.sha(path) == getattr(a, name + '_sha256'), 'Finite evidence changed')
        docs[name] = m.read(path)
    wanted = {(row['dimension'], *row['position']) for row in docs['initial']['missingRoots']}
    roots = {}
    for edge in docs['frontier']['legacyActivationProvenance']:
        if (edge['dimension'], *edge['position']) in wanted:
            for row in edge['incomingNativeVisualFragments']:
                roots.setdefault((row['dimension'], *row['root']), []).append(row)
    m.require(0 < len(roots) <= 64, 'Finite direct root scope required')
    db = sqlite3.connect('file:' + (m.checked(a.plan) / 'plan.sqlite').as_posix() + '?mode=ro', uri=True)
    exports = context = None
    try:
        db.execute('PRAGMA query_only=ON')
        db.execute('BEGIN')
        c.attach_source_journal_original(m.module_api(), db)
        m.require(m.meta(db, 'compositionGeneration') == docs['frontier']['compositionGeneration'], 'Generation changed')
        exports = sqlite3.connect('file:' + Path(m.meta(db, 'poseFragments')['path']).as_posix() + '?mode=ro', uri=True)
        exports.execute('PRAGMA query_only=ON')
        context = c.SourceVisualReservations(m.module_api(), db, exports)
        rows = []
        for token, incoming in sorted(roots.items()):
            dim, *at = token
            point = c.point(m.module_api(), db, dim, at)
            state, be, chunk = context.source_point(dim, tuple(at))
            comps = [m.json.loads(db.execute('SELECT data FROM compositions WHERE key=?', (row['composition'],)).fetchone()[0]) for row in point['placements']]
            rows.append({'dimension': dim, 'position': at, 'incoming': incoming, 'point': point,
                         'compositions': comps, 'source': {'state': state, 'hasBlockEntity': be is not None,
                         'beDigest': context.source_be_digest(dim, tuple(at), be, chunk), 'chunk': chunk}})
        report = {'schemaVersion': 1, 'status': 'PASS_SOURCE_FACTS_NOT_ADMISSION',
                  'kind': 'FINITE_DIRECT_INCOMING_NATIVE_ROOT_FACTS', 'producerSha256': m.sha(__file__),
                  'frontier': {'path': str(m.checked(a.frontier)), 'sha256': a.frontier_sha256},
                  'initial': {'path': str(m.checked(a.initial)), 'sha256': a.initial_sha256},
                  'rows': rows, 'planWrites': 0, 'worldReads': 0, 'mapWrites': 0}
        c.immutable_json(m.module_api(), a.out.resolve(), report)
        print(m.canonical({'status': report['status'], 'roots': len(rows), 'out': str(a.out.resolve())}))
    finally:
        if context is not None:
            context.close()
        if exports is not None:
            exports.close()
        db.close()


if __name__ == '__main__':
    main()
