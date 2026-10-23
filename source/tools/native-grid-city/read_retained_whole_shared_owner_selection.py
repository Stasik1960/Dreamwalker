"""Read current cleanup/selection for finite retained-whole source owner refs."""
import argparse
import sqlite3
from pathlib import Path

import migrate as m
import native_certificates as c
import native_resume as n


def state(db):
    return {key: m.meta(db, key) for key in ('binding', 'compositionGeneration',
            'sourceLocalRetentionJournal', 'postfinalWholeOriginalRetention')}


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--plan', type=Path, default=m.DEFAULT_PLAN)
    p.add_argument('--receipt-sha256', required=True)
    p.add_argument('--out', type=Path, required=True)
    a = p.parse_args()
    db = sqlite3.connect('file:' + (m.checked(a.plan) / 'plan.sqlite').as_posix() + '?mode=ro', uri=True)
    try:
        db.execute('PRAGMA query_only=ON')
        db.execute('BEGIN')
        before = state(db); reference = before['postfinalWholeOriginalRetention']
        m.require(reference and reference['sha256'] == a.receipt_sha256, 'Expected the exact committed whole-original receipt')
        receipt = c.bound(m.module_api(), reference)
        m.require(receipt['status'] == 'PASS' and receipt['kind'] == 'COMMITTED_POSTFINAL_WHOLE_ORIGINAL_RETENTION' and
                  receipt['sourceBinding'] == before['binding'] and receipt['afterGeneration'] == before['compositionGeneration'] and
                  receipt['afterJournal'] == before['sourceLocalRetentionJournal'], 'Committed whole-original generation/journal changed')
        facts_ref = receipt['evidence']['facts']; facts = c.bound(m.module_api(), facts_ref)
        m.require(facts['kind'] == 'FINITE_POSTFINAL_WHOLE_ORIGINAL_DOMAIN_FACTS' and
                  facts['status'] == 'PASS_SOURCE_FACTS_NOT_ADMISSION' and facts['sourceBinding'] == before['binding'],
                  'The accepted finite whole-source facts changed')
        references, identities, members = [], set(), set()
        for owner in facts['owners']:
            obj = owner['object']; dim = obj['dim']
            expected = {tuple(row['position']) for row in owner['originalDomain']['domain']}
            actual = {tuple(row['position']) for row in owner['points'] if row['sourceMember']}
            m.require(actual == expected, 'Source-owner reference scope omits a saved real member')
            for point in owner['points']:
                if not point['sourceMember']:
                    continue
                members.add((dim, *point['position']))
                for table in ('stored_contributions', 'members'):
                    for row in point['sourceOwnerRows'][table]:
                        m.require(row['dim'] == dim and [row[key] for key in ('x', 'y', 'z')] == point['position'],
                                  'Saved source-owner row belongs to another point')
                        token = (dim, row['uuid']); identities.add(token)
                        references.append({'dimension': dim, 'position': point['position'], 'retainedWholeUuid': obj['uuid'],
                                           'sourceTable': table, 'ownerUuid': row['uuid'], 'savedRowSha256': m.digest(row)})
        m.require(len(identities) <= 4096 and len(references) <= 32768, 'Finite shared-owner reference bound exceeded')
        owners = []
        retained = {(row['object']['dim'], row['object']['uuid']) for row in facts['owners']}
        for dim, uid in sorted(identities):
            selected = n.row_dict(db, 'SELECT * FROM selected_objects WHERE dim=? AND uuid=?', (dim, uid))
            cleanup = n.row_dict(db, 'SELECT * FROM cleanup_owners WHERE dim=? AND uuid=?', (dim, uid))
            owners.append({'dimension': dim, 'uuid': uid, 'retainedWholeOwner': (dim, uid) in retained,
                           'selectedObjects': selected, 'cleanupOwners': cleanup,
                           'object': n.row_dict(db, 'SELECT * FROM objects WHERE dim=? AND uuid=?', (dim, uid)),
                           'sourceReferences': [row for row in references if (row['dimension'], row['ownerUuid']) == (dim, uid)]})
        m.require(state(db) == before, 'Committed whole/journal generation changed during finite read')
        summary = {'sourceMemberCells': len(members), 'sourceOwnerReferences': len(references), 'uniqueSourceOwners': len(owners),
                   'selectedSourceOwners': sum(bool(row['selectedObjects']) for row in owners),
                   'cleanupSourceOwners': sum(bool(row['cleanupOwners']) for row in owners),
                   'foreignCleanupSourceOwners': sum(bool(row['cleanupOwners']) and not row['retainedWholeOwner'] for row in owners)}
        report = {'schemaVersion': 1, 'status': 'READ_ONLY_RETAINED_WHOLE_SHARED_OWNER_SELECTION_FACTS',
                  'retentionReceipt': reference, 'facts': facts_ref, 'sourceBinding': before['binding'],
                  'compositionGeneration': before['compositionGeneration'], 'journal': before['sourceLocalRetentionJournal'],
                  'owners': owners, 'summary': summary, 'producerSha256': m.sha(__file__),
                  'databaseWrites': 0, 'worldReads': 0, 'mapWrites': 0, 'ownerFootprintsRead': 0}
        c.immutable_json(m.module_api(), a.out.resolve(), report)
        print(m.canonical({'status': report['status'], 'out': str(a.out.resolve()), 'sha256': m.sha(a.out), 'summary': summary}))
    finally:
        db.close()


if __name__ == '__main__':
    main()
