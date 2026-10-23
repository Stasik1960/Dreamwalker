"""Read-only saved-domain/queue contacts, never an acceptance or repair."""
import argparse
import sqlite3
import migrate as m
import native_certificates as c
import native_resume as n

p=argparse.ArgumentParser();p.add_argument('--out',type=m.Path,required=True);a=p.parse_args()
m.control_check();m.require(not a.out.exists(),'Use a fresh diagnostic output')
db=sqlite3.connect((m.DEFAULT_PLAN/'plan.sqlite').as_uri()+'?mode=ro',uri=True)
try:
    db.execute('PRAGMA query_only=ON');db.execute('BEGIN');c.attach_source_journal_original(m,db)
    context=c.SourceVisualReservations(m,db);points={};domains=0
    for dim,uid,encoded,digest in db.execute('SELECT * FROM source_visual_domains'):
        domain=m.json.loads(encoded);m.require(m.digest(domain)==digest,'Saved domain changed');domains+=1
        for point in domain['domain']:
            key=(dim,*point['position'])
            queue=n.row_dict(db,'SELECT * FROM cell_queue WHERE dim=? AND x=? AND y=? AND z=?',key)
            if not queue:continue
            row=points.setdefault(key,{'dimension':dim,'position':point['position'],'queue':queue,'savedDomains':[]})
            row['savedDomains'].append({'object':domain['object'],'root':domain['root'],'mechanism':domain['mechanism'],'expectedPoint':point,'domainDigest':digest})
    for key,row in points.items():
        row['work']={table:n.row_dict(db,'SELECT * FROM '+table+' WHERE dim=? AND x=? AND y=? AND z=?',key) for table in ('fragments','substrate_fragments','native_visual_roots','native_visual_fragments','whole_cells','bridge_cells','placements','protected_cells')}
        state,be,proof=context.source_point(key[0],key[1:]);row['actualSourcePoint']={'sourceState':state,'beDigest':context.source_be_digest(key[0],key[1:],be,proof),'chunk':proof}
        row['sourceMatchesEverySavedPoint']=all(row['actualSourcePoint']=={k:d['expectedPoint'][k] for k in ('sourceState','beDigest','chunk')} for d in row['savedDomains'])
        row['rootObjects']=n.row_dict(db,'SELECT * FROM objects WHERE dim=? AND x=? AND y=? AND z=?',key)
    m.require(db.total_changes==0,'RO diagnostic changed plan');report={'status':'READ_ONLY_DIAGNOSTIC','domainsInspected':domains,'conflictPoints':len(points),'points':list(points.values()),'cursor':m.meta(db,'nativeComposeCursor'),'localJournal':m.meta(db,'sourceLocalRetentionJournal'),'databaseWrites':0,'mapWrites':0}
finally:db.close()
m.atomic_json(a.out,report);print(m.canonical({'status':report['status'],'domains':domains,'conflictPoints':len(points)}))
