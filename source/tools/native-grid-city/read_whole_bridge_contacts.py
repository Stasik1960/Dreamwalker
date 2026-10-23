"""Read owners of the finite compiled whole/BE predicate contacts."""
import argparse
import json
import sqlite3
from pathlib import Path
import migrate as m
import native_certificates as c
import native_resume as n


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--plan', type=Path, required=True)
    p.add_argument('--report', type=Path, required=True)
    p.add_argument('--report-sha256', required=True)
    p.add_argument('--out', type=Path, required=True)
    a = p.parse_args()
    proof = c.bound(m.module_api(), {'path': str(a.report.resolve()), 'sha256': a.report_sha256})
    db = sqlite3.connect('file:' + (m.checked(a.plan) / 'plan.sqlite').as_posix() + '?mode=ro', uri=True)
    try:
        db.execute('PRAGMA query_only=ON')
        db.execute('BEGIN')
        m.require(m.meta(db, 'compositionGeneration') == proof['finiteStateCorrespondence']['plan']['compositionGeneration'], 'Actual report generation changed')
        rows = []
        for issue in proof['issues']:
            m.require(issue['kind'] == 'compiled_background_differs_from_exact_host_flags' and issue['key'].startswith('whole:'), 'Only exact whole-role flag contacts are allowed')
            found = n.row_dict(db, 'SELECT p.dim,p.x,p.y,p.z,p.composition,w.uuid,w.template,w.dx,w.dy,w.dz,'
                              'o.pose,o.kind,o.x AS root_x,o.y AS root_y,o.z AS root_z,b.host_state '
                              'FROM placements p JOIN whole_cells w ON w.dim=p.dim AND w.x=p.x AND w.y=p.y AND w.z=p.z '
                              'JOIN objects o ON o.dim=w.dim AND o.uuid=w.uuid '
                              'JOIN bridge_cells b ON b.dim=p.dim AND b.x=p.x AND b.y=p.y AND b.z=p.z '
                              'WHERE p.composition=?', (issue['key'],))
            m.require(found, 'Bound whole contact disappeared')
            rows.extend(found)
        owners = {}
        for row in rows:
            token = (row['dim'], row['uuid'])
            if token not in owners:
                owners[token] = {'dimension': row['dim'], 'uuid': row['uuid'], 'pose': row['pose'],
                                 'root': [row['root_x'], row['root_y'], row['root_z']], 'kind': row['kind'],
                                 'wholeCells': db.execute('SELECT count(*) FROM whole_cells WHERE dim=? AND uuid=?', token).fetchone()[0]}
        result = {'status': 'READ_ONLY_WHOLE_CARRIER_CONTACT_OWNERS', 'sourceBinding': m.meta(db, 'binding'),
                  'generation': m.meta(db, 'compositionGeneration'), 'report': {'path': str(a.report.resolve()), 'sha256': a.report_sha256},
                  'owners': list(owners.values()), 'contacts': rows, 'worldReads': 0, 'databaseWrites': 0}
        c.immutable_json(m.module_api(), a.out.resolve(), result)
        print(m.canonical({'status': result['status'], 'owners': len(owners), 'contacts': len(rows)}))
    finally:
        db.close()


if __name__ == '__main__':
    main()
