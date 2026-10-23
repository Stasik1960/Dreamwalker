"""Explain the slow census query on empty TEMP schemas; no project DB opened."""
import json,sqlite3
db=sqlite3.connect(':memory:')
db.executescript('''
CREATE TEMP TABLE refs(dim TEXT,x INTEGER,y INTEGER,z INTEGER,PRIMARY KEY(dim,x,y,z));
CREATE INDEX temp.ref_chunk ON refs(dim,(x>>4),(z>>4));
CREATE TEMP TABLE source_roots(dim TEXT,x INTEGER,y INTEGER,z INTEGER,registry_id TEXT);
CREATE INDEX temp.source_root_pos ON source_roots(dim,x,y,z);
''')
query='''SELECT r.x,r.y,r.z,group_concat(DISTINCT s.registry_id) AS root_ids
FROM refs r LEFT JOIN source_roots s ON s.dim=r.dim AND s.x=r.x AND s.y=r.y AND s.z=r.z
WHERE r.dim=? AND r.x>>4=? AND r.z>>4=? GROUP BY r.x,r.y,r.z ORDER BY r.x,r.y,r.z'''
print(json.dumps({'kind':'QUERY_PLAN_DIAGNOSTIC_ONLY','worldReads':0,'projectDatabaseOpens':0,
    'plan':list(db.execute('EXPLAIN QUERY PLAN '+query,('minecraft:overworld',0,0)))},indent=2))
