"""Bounded read-only diagnostic of bridge metadata in existing original zones."""
import json
import sqlite3
import migrate as m
import native_resume as n
database=m.DEFAULT_PLAN/'plan.sqlite'
db=sqlite3.connect(database.as_uri()+'?mode=ro',uri=True,timeout=3)
try:
    db.execute('PRAGMA query_only=ON');db.execute('BEGIN');points={};zones=0
    for sequence,encoded in db.execute('SELECT sequence,data FROM source_local_retentions ORDER BY sequence'):
        event=json.loads(encoded);zone=event.get('sourceZone')
        if not zone:continue
        zones+=1;lo=zone['minimum'];hi=zone['maximum'];dim=zone['dimension']
        for bridge in n.row_dict(db,'SELECT * FROM bridge_cells WHERE dim=? AND x BETWEEN ? AND ? AND y BETWEEN ? AND ? AND z BETWEEN ? AND ?', (dim,lo[0],hi[0],lo[1],hi[1],lo[2],hi[2])):
            key=(dim,bridge['x'],bridge['y'],bridge['z'])
            if key not in points:
                demands={table:bool(db.execute('SELECT 1 FROM '+table+' WHERE dim=? AND x=? AND y=? AND z=? LIMIT 1',key).fetchone()) for table in ('fragments','substrate_fragments','native_visual_fragments','whole_cells','native_visual_roots','cell_queue','placements')}
                points[key]={'bridge':bridge,'currentDemands':demands,'originalZoneEvents':[]}
            before=[row for row in event['pointPlan'] if row['position']==list(key[1:])]
            points[key]['originalZoneEvents'].append({'sequence':sequence,'originalDomains':len(event.get('originalDomains',[])),'beforePoint':before})
    rows=list(points.values());print(json.dumps({'status':'READ_ONLY_DIAGNOSTIC','zoneEventsInspected':zones,'bridgePoints':len(rows),'orphanOnly':all(not any(row['currentDemands'].values()) for row in rows),'points':rows,'cursor':m.meta(db,'nativeComposeCursor'),'localJournal':m.meta(db,'sourceLocalRetentionJournal'),'databaseWrites':0,'mapWrites':0},ensure_ascii=False,indent=2))
finally:db.close()
