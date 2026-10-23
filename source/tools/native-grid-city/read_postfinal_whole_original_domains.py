"""Read complete original whole owners and only their direct member demands."""
import argparse
import base64
import sqlite3
from pathlib import Path

import migrate as m
import native_certificates as c
import native_resume as n
import retain_postfinal_original_sections as shared


def stable(db):
    return {key: m.meta(db, key) for key in ('binding', 'compositionGeneration', 'compositionsSha256',
            'nativeComposeCursor', 'sourceLocalRetentionJournal', 'poseFragments',
            'nativeResumeSourceBinding', 'postfinalNativeVisualAmendment', 'postfinalCarrierPredicateSplit')}


def work(db, dim, uid):
    answer = {}
    for table in ('selected_objects', 'fragments', 'whole_cells', 'activated_native', 'substrate_fragments', 'cleanup_owners'):
        hint = ' INDEXED BY fragment_owner' if table == 'fragments' else ' INDEXED BY substrate_owner' if table == 'substrate_fragments' else ''
        answer[table] = n.row_dict(db, 'SELECT * FROM ' + table + hint + ' WHERE dim=? AND uuid=? LIMIT 4097', (dim, uid))
        m.require(len(answer[table]) <= 4096, 'Complete original-owner work exceeds the finite bound')
    return answer


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--plan', type=Path, default=m.DEFAULT_PLAN)
    p.add_argument('--scope', type=Path, required=True)
    p.add_argument('--scope-sha256', required=True)
    p.add_argument('--out', type=Path, required=True)
    a = p.parse_args()
    plan = m.checked(a.plan)
    scope_ref = {'path': str(m.checked(a.scope)), 'sha256': a.scope_sha256}
    scope = c.bound(m.module_api(), scope_ref)
    m.require(scope['status'] == 'READ_ONLY_WHOLE_CARRIER_CONTACT_OWNERS' and 0 < len(scope['owners']) <= 64,
              'Expected only bounded actual whole-carrier owners')
    db = sqlite3.connect('file:' + (plan / 'plan.sqlite').as_posix() + '?mode=ro', uri=True)
    context = exports = None
    try:
        db.execute('PRAGMA query_only=ON')
        db.execute('BEGIN')
        c.attach_source_journal_original(m.module_api(), db)
        before = stable(db)
        shared.preconditions(db, plan, scope['generation']['compositionsSha256'])
        m.require(before['compositionGeneration'] == scope['generation'] and before['binding'] == scope['sourceBinding'],
                  'Completed generation/source differs from the exact whole-contact scope')
        exports = sqlite3.connect('file:' + Path(before['poseFragments']['path']).as_posix() + '?mode=ro', uri=True)
        exports.execute('PRAGMA query_only=ON')
        exports.execute('BEGIN')
        context = c.SourceVisualReservations(m.module_api(), db, exports)
        owners, direct = [], set()
        seeds = {(row['dimension'], row['uuid']) for row in scope['owners']}
        m.require(len(seeds) == len(scope['owners']), 'Duplicate whole-owner scope')
        for witness in scope['owners']:
            dim, uid = witness['dimension'], witness['uuid']
            obj, members, _ = context.original_members(dim, uid)
            m.require(obj['kind'] == witness['kind'] == 'whole' and obj['pose'] == witness['pose'] and
                      [obj[key] for key in ('x', 'y', 'z')] == witness['root'], 'Exact whole source identity changed')
            domain = context.original_domain(dim, uid, unreplaced=False)
            own = work(db, dim, uid)
            m.require(len(own['selected_objects']) == 1 and own['selected_objects'][0]['kind'] == 'whole' and
                      len(own['whole_cells']) == witness['wholeCells'],
                      'Complete selected whole-role scope changed')
            positions = set(members) | {tuple(row[key] for key in ('x', 'y', 'z'))
                                       for table in ('fragments', 'whole_cells', 'substrate_fragments') for row in own[table]}
            m.require(len(positions) <= 8192, 'Whole source/member/role union exceeds the finite bound')
            points = []
            for at in sorted(positions):
                state, be, chunk = context.source_point(dim, at)
                current = c.point(m.module_api(), db, dim, at)
                saved = {table: n.row_dict(db, 'SELECT * FROM old.' + table + ' WHERE dim=? AND x=? AND y=? AND z=?',
                                         (dim, *at)) for table in ('stored_contributions', 'members')}
                if at in members:
                    for table in ('fragments', 'whole_cells', 'substrate_fragments'):
                        direct.update((dim, row['uuid']) for row in current[table] if (dim, row['uuid']) not in seeds)
                points.append({'position': list(at), 'sourceMember': at in members, 'sourceState': state,
                               'sourceBeDigest': context.source_be_digest(dim, at, be, chunk), 'sourceChunk': chunk,
                               'sourceBeNbtBase64': base64.b64encode(m.encode_nbt(m.NbtFile('', m.Tag(10, be)))).decode('ascii') if be is not None else None,
                               'sourceOwnerRows': saved, 'nativeOrigins': context.native_origins(dim, at, state), 'point': current})
            owners.append({'object': obj, 'originalDomain': domain, 'ownWork': own,
                           'ownSavedRows': {table: context.owner_member_rows(dim, uid, table)
                                            for table in ('stored_contributions', 'members')}, 'points': points})
        direct_rows = [{'dimension': dim, 'uuid': uid,
                        'object': n.row_dict(db, 'SELECT * FROM objects WHERE dim=? AND uuid=?', (dim, uid)),
                        'selected': n.row_dict(db, 'SELECT * FROM selected_objects WHERE dim=? AND uuid=?', (dim, uid)),
                        'cleanup': n.row_dict(db, 'SELECT * FROM cleanup_owners WHERE dim=? AND uuid=?', (dim, uid)),
                        'ownFootprintRead': False} for dim, uid in sorted(direct)]
        m.require(stable(db) == before and m.sha(a.scope) == a.scope_sha256, 'Source/generation/scope changed during finite collection')
        report = {'schemaVersion': 1, 'status': 'PASS_SOURCE_FACTS_NOT_ADMISSION',
                  'kind': 'FINITE_POSTFINAL_WHOLE_ORIGINAL_DOMAIN_FACTS', 'scope': scope_ref,
                  'sourceBinding': before['binding'], 'stableState': before, 'sourceFiles': c.source_files(m.module_api()),
                  'producer': {'path': str(Path(__file__).resolve()), 'sha256': m.sha(__file__)},
                  'owners': owners, 'directMemberDemandOwners': direct_rows,
                  'summary': {'owners': len(owners), 'wholeRoleRows': sum(len(row['ownWork']['whole_cells']) for row in owners),
                              'sourceMemberCells': sum(len(row['originalDomain']['domain']) for row in owners),
                              'directMemberDemandOwners': len(direct_rows)},
                  'ownerGraphTraversal': False, 'databaseWrites': 0, 'worldReads': 0, 'mapWrites': 0}
        c.immutable_json(m.module_api(), a.out.resolve(), report)
        print(m.canonical({'status': report['status'], 'out': str(a.out.resolve()), 'sha256': m.sha(a.out), 'summary': report['summary']}))
    finally:
        if context is not None:
            context.close()
        if exports is not None:
            exports.close()
        db.close()


if __name__ == '__main__':
    main()
