"""Read-only actual-contact probe; no SCHEMA connection or journal writes."""
import argparse
import migrate as m
import context_stage
import native_certificates as c


def main():
    parser=argparse.ArgumentParser();parser.add_argument('--plan',required=True);parser.add_argument('--diagnostic',required=True);parser.add_argument('--diagnostic-sha256',required=True);parser.add_argument('--out',required=True);args=parser.parse_args()
    m.require(m.sha(args.diagnostic)==args.diagnostic_sha256,'Actual source-contact diagnostic hash changed');diagnostic=m.read(args.diagnostic)
    db=context_stage.attached_readonly(m,args.plan)
    try:
        db.execute('PRAGMA query_only=ON');db.execute('PRAGMA cache_size=-2048');db.execute('BEGIN');before=db.total_changes
        m.require(diagnostic['sourceBinding']==m.meta(db,'binding'),'Another original source binding')
        obj=diagnostic['nativeObject'];dim=obj['dim'];cell=diagnostic['conflictCell'];at=cell['position']
        m.require(c.n.row_dict(db,'SELECT * FROM objects WHERE dim=? AND uuid=?',(dim,obj['uuid']))==[obj],'Actual failed incoming object changed')
        incoming={'kind':'legacy_native','uuid':obj['uuid'],'pose':obj['pose'],'root':[obj[k] for k in ('x','y','z')]}
        context=c.SourceVisualReservations(m,db);contacts=[]
        for row in c.n.row_dict(db,'SELECT * FROM protected_cells WHERE dim=? AND x=? AND y=? AND z=? ORDER BY uuid',(dim,*at)):
            domain=context.owner_domain(dim,row['uuid']);decision=context.contact(dim,at,row,domain)
            contacts.append({'domain':domain,'decision':decision})
        m.require(contacts and db.total_changes==before==0,'Read-only wire probe cannot admit or change journal/source rows')
        report={'schemaVersion':1,'status':'PASS_READ_ONLY_ACTUAL_VISUAL_RESERVATION_ONLY','sourceBinding':m.meta(db,'binding'),'sourceFiles':c.source_files(m),
                'diagnostic':{'path':str(m.checked(args.diagnostic)),'sha256':args.diagnostic_sha256},'incoming':incoming,'position':at,'contacts':contacts,
                'databaseWrites':0,'journalWrites':0,'schemaWrites':0,'worldReads':0,'mapWrites':0,'gameplayPass':False}
        m.atomic_json(args.out,report);print(report['status'])
    finally:db.close()


if __name__=='__main__':main()
