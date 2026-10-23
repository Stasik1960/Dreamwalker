"""Add the lookup index required for efficient FULL whole-role type import."""
import argparse
import sqlite3
from pathlib import Path
import migrate as m
import native_certificates as c


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--plan', type=Path, required=True)
    p.add_argument('--out', type=Path, required=True)
    a = p.parse_args()
    plan = m.checked(a.plan)
    db = sqlite3.connect('file:' + (plan / 'plan.sqlite').as_posix() + '?mode=rw', uri=True)
    try:
        m.require(not m.meta(db, 'applyStarted') and not a.out.exists(), 'Only a fresh pre-apply index operation is allowed')
        generation = m.meta(db, 'compositionGeneration')
        m.require(generation and generation['compositionsSha256'] == m.sha(plan / 'compositions.jsonl'), 'Completed actual raw binding required')
        db.execute('PRAGMA temp_store=FILE')
        db.execute('BEGIN IMMEDIATE')
        existing = []
        for row in db.execute('PRAGMA index_list(placements)'):
            name = row[1]
            columns = list(db.execute('PRAGMA index_info("' + name.replace('"', '""') + '")'))
            if columns and columns[0][2] == 'composition':
                existing.append(name)
        if not existing:
            db.execute('CREATE INDEX placement_composition_lookup ON placements(composition)')
        m.require(db.total_changes == 0 and m.meta(db, 'compositionGeneration') == generation, 'Lookup index changed project rows or generation')
        db.commit()
        query = [list(row) for row in db.execute('EXPLAIN QUERY PLAN SELECT type_index FROM placements WHERE composition=?', ('whole:lookup-probe:[]',))]
        m.require(any('INDEX' in row[3] and 'composition=?' in row[3] for row in query), 'Whole-role lookup must use its index')
        report = {'status': 'PASS_LOOKUP_INDEX_ONLY', 'kind': 'FULL_PLACEMENT_COMPOSITION_LOOKUP_INDEX',
                  'existingIndices': existing, 'created': not existing, 'queryPlan': query,
                  'compositionGenerationSha256': generation['compositionsSha256'], 'projectRowsChanged': db.total_changes,
                  'geometryDataWorkUnchanged': True, 'mapWrites': 0}
        c.immutable_json(m.module_api(), a.out.resolve(), report)
        print(m.canonical(report))
    finally:
        db.close()


if __name__ == '__main__':
    main()
