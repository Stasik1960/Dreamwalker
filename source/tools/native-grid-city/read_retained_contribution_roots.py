"""Finite read-only root facts for actual recorded admission contacts."""
import argparse
import sqlite3
from pathlib import Path
import migrate as m
import native_certificates as c
import native_resume as n


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--plan', type=Path, required=True)
    p.add_argument('--discovery', type=Path, required=True)
    p.add_argument('--discovery-sha256', required=True)
    p.add_argument('--out', type=Path, required=True)
    a = p.parse_args()
    m.require(m.sha(a.discovery) == a.discovery_sha256, 'Discovery changed')
    discovery = m.read(a.discovery)
    m.require(discovery['kind'] == 'FINITE_POSTFINAL_NATIVE_VISUAL_INPUT_DISCOVERY' and
              len(discovery['blockedContacts']) <= 64, 'Finite actual contacts required')
    plan = m.checked(a.plan)
    db = sqlite3.connect('file:' + (plan / 'plan.sqlite').as_posix() + '?mode=ro', uri=True)
    exports = context = None
    try:
        db.execute('PRAGMA query_only=ON')
        db.execute('BEGIN')
        c.attach_source_journal_original(m.module_api(), db)
        fragments = m.meta(db, 'poseFragments')
        m.require(fragments == discovery['stableState']['poseFragments'] and
                  m.meta(db, 'compositionGeneration') == discovery['compositionGeneration'], 'Completed inputs changed')
        exports = sqlite3.connect('file:' + Path(fragments['path']).as_posix() + '?mode=ro', uri=True)
        exports.execute('PRAGMA query_only=ON')
        context = c.SourceVisualReservations(m.module_api(), db, exports)
        rows = []
        for contact in discovery['blockedContacts']:
            for saved_owner in contact['sourceOwners']:
                for obj in saved_owner['objects']:
                    dim, uid = obj['dim'], obj['uuid']
                    at = tuple(obj[key] for key in ('x', 'y', 'z'))
                    m.require(n.row_dict(db, 'SELECT * FROM objects WHERE dim=? AND uuid=?', (dim, uid)) == [obj], 'Actual contact owner changed')
                    point = c.point(m.module_api(), db, dim, at)
                    state, be, chunk = context.source_point(dim, at)
                    compositions = []
                    for placement in point['placements']:
                        data = db.execute('SELECT data FROM compositions WHERE key=?', (placement['composition'],)).fetchone()
                        compositions.append({'key': placement['composition'], 'data': m.json.loads(data[0]) if data else None})
                    work = {table: n.row_dict(db, 'SELECT * FROM ' + table + ' WHERE dim=? AND uuid=?', (dim, uid))
                            for table in ('selected_objects', 'cleanup_owners', 'activated_native')}
                    rows.append({'contact': {'dimension': contact['dimension'], 'position': contact['position']},
                                 'object': obj, 'point': point, 'compositions': compositions, 'work': work,
                                 'source': {'state': state, 'beDigest': context.source_be_digest(dim, at, be, chunk),
                                            'blockEntity': m.plain_tag(m.Tag(10, be)) if be else None, 'chunk': chunk},
                                 'rootSourceRows': {table: n.row_dict(db, 'SELECT * FROM old.' + table + ' WHERE dim=? AND x=? AND y=? AND z=?', (dim, *at))
                                                    for table in ('stored_contributions', 'members')}})
        report = {'schemaVersion': 1, 'status': 'PASS_SOURCE_FACTS_NOT_ADMISSION',
                  'kind': 'FINITE_RETAINED_CONTRIBUTION_ROOT_FACTS',
                  'discovery': {'path': str(m.checked(a.discovery)), 'sha256': a.discovery_sha256},
                  'producerSha256': m.sha(__file__), 'plannedBeSafety': m.meta(db, 'plannedBeSafety'),
                  'rows': rows, 'planWrites': 0, 'worldReads': 0, 'mapWrites': 0}
        c.immutable_json(m.module_api(), a.out.resolve(), report)
        print(m.canonical({'status': report['status'], 'out': str(a.out.resolve()), 'roots': len(rows)}))
    finally:
        if context is not None:
            context.close()
        if exports is not None:
            exports.close()
        db.close()


if __name__ == '__main__':
    main()
