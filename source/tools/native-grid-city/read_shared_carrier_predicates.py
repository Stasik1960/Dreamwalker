"""Read-only finite conflicts between shared composition keys and observed hosts."""
import argparse
import collections
import json
import sqlite3
import sys
from pathlib import Path
import migrate as m
import native_certificates as c
import native_resume as n
sys.path.insert(0, str(m.PROJECT / 'tools/native-grid/compiler'))
import catalog_normalize_carriers as normalizer


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--plan', type=Path, required=True)
    p.add_argument('--compositions', type=Path, required=True)
    p.add_argument('--raw-sha256', required=True)
    p.add_argument('--full-function-gate', type=Path, required=True)
    p.add_argument('--audit', type=Path, action='append', required=True)
    p.add_argument('--out', type=Path, required=True)
    p.add_argument('--include-unqualified', action='store_true')
    a = p.parse_args()
    m.require(m.sha(a.compositions) == a.raw_sha256, 'Actual raw changed')
    known, proofs = normalizer.audit_states(a.audit)
    db = sqlite3.connect('file:' + (m.checked(a.plan) / 'plan.sqlite').as_posix() + '?mode=ro', uri=True)
    try:
        db.execute('PRAGMA query_only=ON')
        db.execute('BEGIN')
        m.require(m.meta(db, 'compositionsSha256') == a.raw_sha256, 'Current raw metadata differs')
        decisions, function_proof = normalizer.full_function_states(a.full_function_gate, db)
        grouped = collections.defaultdict(lambda: collections.defaultdict(list))
        rows = n.row_dict(db, 'SELECT p.*,b.host_state FROM bridge_cells b CROSS JOIN placements p '
                            'WHERE p.dim=b.dim AND p.x=b.x AND p.y=b.y AND p.z=b.z', ())
        for row in rows:
            key = normalizer.state_key(json.loads(row['host_state']))
            m.require(key in known, 'Actual host flags missing')
            normalizer.full_host_decision(decisions, key)
            grouped[row['composition']][m.canonical(known[key])].append(row)
        conflicts = []
        for key, groups in sorted(grouped.items()):
            encoded = db.execute('SELECT data FROM compositions WHERE key=?', (key,)).fetchone()
            if encoded is None and key.startswith('whole:'):
                continue
            m.require(encoded, 'Shared raw composition missing')
            composition = json.loads(encoded[0])
            if a.include_unqualified:
                if composition.get('wrapper') != 'NativeGridBase' or composition.get('carrierPredicates') is not None:
                    continue
            elif len(groups) < 2:
                continue
            conflicts.append({'key': key, 'composition': composition,
                              'groups': [{'predicates': json.loads(flags),
                                          'hosts': sorted({row['host_state'] for row in places}),
                                          'placements': sorted(places, key=lambda row: (row['dim'], row['x'], row['y'], row['z']))}
                                         for flags, places in sorted(groups.items())]})
        report = {'schemaVersion': 1, 'status': 'PASS_SOURCE_FACTS_NOT_ADMISSION',
                  'kind': 'UNQUALIFIED_CARRIER_PREDICATE_BINDINGS' if a.include_unqualified else 'SHARED_CARRIER_PREDICATE_CONFLICTS', 'producerSha256': m.sha(__file__),
                  'sourceBinding': m.meta(db, 'binding'), 'generation': m.meta(db, 'compositionGeneration'),
                  'raw': {'path': str(m.checked(a.compositions)), 'sha256': a.raw_sha256},
                  'fullFunctionGate': function_proof, 'audits': proofs, 'bridgePlacements': len(rows),
                  'conflictKeys': len(conflicts), 'affectedPlacements': sum(len(g['placements']) for row in conflicts for g in row['groups']),
                  'conflicts': conflicts, 'planWrites': 0, 'mapWrites': 0, 'worldReads': 0}
        c.immutable_json(m.module_api(), a.out.resolve(), report)
        print(m.canonical({key: report[key] for key in ('status', 'conflictKeys', 'affectedPlacements', 'bridgePlacements')}))
    finally:
        db.close()


if __name__ == '__main__':
    main()
