"""Read the existing finite native-root activation path for discovery inputs."""
import argparse
import json
import sqlite3
from pathlib import Path

import migrate as m
import native_certificates as c
import native_resume as n


def source(context, dim, at):
    state, be, chunk = context.source_point(dim, tuple(at))
    return {'state': state, 'hasBlockEntity': be is not None,
            'beDigest': context.source_be_digest(dim, tuple(at), be, chunk), 'chunk': chunk}


def stable(db):
    return {key: m.meta(db, key) for key in ('binding', 'compositionGeneration', 'compositionsSha256',
            'nativeComposeCursor', 'sourceLocalRetentionJournal', 'poseFragments', 'nativeResumeSourceBinding')}


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--plan', type=Path, default=m.DEFAULT_PLAN)
    p.add_argument('--discovery', type=Path, required=True)
    p.add_argument('--discovery-sha256', required=True)
    p.add_argument('--out', type=Path, required=True)
    a = p.parse_args()
    plan = m.checked(a.plan)
    m.require(m.sha(a.discovery) == a.discovery_sha256, 'Finite visual discovery changed')
    discovery = m.read(a.discovery)
    m.require(discovery['kind'] == 'FINITE_POSTFINAL_NATIVE_VISUAL_INPUT_DISCOVERY' and
              0 < len(discovery['missingRoots']) <= 64, 'Expected only bounded actual missing roots')
    db = sqlite3.connect('file:' + (plan / 'plan.sqlite').as_posix() + '?mode=ro', uri=True)
    exports = context = None
    try:
        db.execute('PRAGMA query_only=ON')
        db.execute('BEGIN')
        c.attach_source_journal_original(m.module_api(), db)
        before = stable(db)
        m.require(before['compositionGeneration'] == discovery['compositionGeneration'] and
                  before['poseFragments'] == discovery['stableState']['poseFragments'],
                  'The completed generation/cache differs from the actual discovery')
        exports = sqlite3.connect('file:' + Path(before['poseFragments']['path']).as_posix() + '?mode=ro', uri=True)
        exports.execute('PRAGMA query_only=ON')
        exports.execute('BEGIN')
        context = c.SourceVisualReservations(m.module_api(), db, exports)
        rows = []
        for witness in discovery['missingRoots']:
            dim, at = witness['dimension'], witness['position']
            actual, _, _ = context.source_point(dim, tuple(at))
            native_row = exports.execute('SELECT data FROM native WHERE state=?', (m.canonical(witness['state']),)).fetchone()
            native = json.loads(native_row[0]) if native_row else None
            resolved, error = [], None
            try:
                resolved = m.native_roots_at(db, dim, tuple(at), actual, native)
            except RuntimeError as failure:
                error = str(failure)
            origins = context.native_origins(dim, tuple(at), actual)
            identities = {(row['uuid'], row['object'][0]['pose']): row['object'][0]
                          for row in origins if len(row['object']) == 1}
            for uid, pose, x, y, z in resolved:
                found = n.row_dict(db, 'SELECT * FROM objects WHERE dim=? AND uuid=?', (dim, uid))
                if len(found) == 1:
                    identities[(uid, pose)] = found[0]
            owners = []
            for (uid, pose), obj in sorted(identities.items()):
                root = [obj[key] for key in ('x', 'y', 'z')]
                saved_pose = db.execute('SELECT data FROM poses WHERE key=?', (pose,)).fetchone()
                clips = []
                for x, y, z, digest, encoded in exports.execute(
                        'SELECT x,y,z,digest,data FROM fragments WHERE pose=? ORDER BY x,y,z LIMIT 4097', (pose,)):
                    data = json.loads(encoded)
                    m.require(m.digest(data) == digest, 'Existing exact SourcePose clip digest changed')
                    clips.append({'cell': [x, y, z], 'digest': digest, 'quadCount': len(data['quads'])})
                m.require(len(clips) <= 4096, 'Existing SourcePose clip bound exceeded')
                work = {}
                for table in ('selected_objects', 'cleanup_owners', 'activated_native', 'substrate_fragments', 'fragments'):
                    hint = ' INDEXED BY substrate_owner' if table == 'substrate_fragments' else ' INDEXED BY fragment_owner' if table == 'fragments' else ''
                    work[table] = n.row_dict(db, 'SELECT * FROM ' + table + hint + ' WHERE dim=? AND uuid=? LIMIT 4097', (dim, uid))
                    m.require(len(work[table]) <= 4096, 'Existing owner work bound exceeded')
                owners.append({'object': obj, 'pose': json.loads(saved_pose[0]) if saved_pose else None,
                               'poseDataSha256': m.hashlib.sha256(saved_pose[0].encode()).hexdigest() if saved_pose else None,
                               'exportedPose': n.row_dict(exports, 'SELECT * FROM poses WHERE key=?', (pose,)),
                               'rootSource': source(context, dim, root), 'rootPoint': c.point(m.module_api(), db, dim, root),
                               'work': work, 'exactVisualClips': clips,
                               'consumedClipCell': [at[i] - root[i] for i in range(3)]})
            rows.append({'witness': witness, 'actualSource': source(context, dim, at),
                         'point': c.point(m.module_api(), db, dim, at),
                         'nativeRootsAt': [list(row) for row in resolved], 'nativeRootsAtError': error,
                         'recipeOrigins': origins, 'owners': owners})
        m.require(stable(db) == before, 'Completed source/generation changed during finite probe')
        report = {'schemaVersion': 1, 'status': 'PASS_SOURCE_FACTS_NOT_ADMISSION',
                  'kind': 'FINITE_MISSING_LEGACY_NATIVE_ROOT_FACTS', 'producerSha256': m.sha(__file__),
                  'discovery': {'path': str(m.checked(a.discovery)), 'sha256': a.discovery_sha256},
                  'stableState': before, 'cells': rows, 'planWrites': 0, 'worldReads': 0, 'mapWrites': 0}
        c.immutable_json(m.module_api(), a.out.resolve(), report)
        print(m.canonical({'status': report['status'], 'out': str(a.out.resolve()), 'cells': len(rows),
                           'owners': sum(len(row['owners']) for row in rows),
                           'resolverErrors': sum(row['nativeRootsAtError'] is not None for row in rows)}))
    finally:
        if context is not None:
            context.close()
        if exports is not None:
            exports.close()
        db.close()


if __name__ == '__main__':
    main()
