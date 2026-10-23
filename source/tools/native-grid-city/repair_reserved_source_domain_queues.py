"""Root-executed atomic bootstrap of the existing saved-domain section rule."""
import argparse
import sqlite3
from pathlib import Path
import migrate as m
import native_resume as n
import native_certificates as c


def main():
    parser=argparse.ArgumentParser();parser.add_argument('--plan',type=Path,default=m.DEFAULT_PLAN);parser.add_argument('--source-proof',type=Path,required=True);parser.add_argument('--source-proof-sha256',required=True);parser.add_argument('--failed-run',type=Path,default=m.ROOT/'research/processes/20261023T133636-migrate.json');parser.add_argument('--out',type=Path);parser.add_argument('--execute',action='store_true');args=parser.parse_args()
    m.control_check();m.require(args.execute,'This reviewed bootstrap requires an explicit --execute');plan=m.checked(args.plan);out=m.checked(args.out or plan/'full/reserved-source-domain-queue-repair.v14.json')
    proof_ref={'path':str(m.checked(args.source_proof)),'sha256':args.source_proof_sha256};proof=c.bound(m,proof_ref);current=c.source_files(m)
    m.require(proof['status']=='PASS_SOURCE_AND_TARGETED_FIXTURES_ONLY' and proof['kind']=='NATIVE_CERTIFICATE_CONSUMER_SOURCE_UPGRADE' and proof['sourceFiles']==current,'Pin the exact root-reviewed new code/fixture proof before bootstrap')
    failure_ref=n.ref(m,args.failed_run);m.require(proof['actualFailedRun']==failure_ref,'Bootstrap must use the exact reviewed actual native failure')
    db=sqlite3.connect('file:'+(plan/'plan.sqlite').as_posix()+'?mode=rw',uri=True);context=None
    try:
        db.execute('PRAGMA synchronous=FULL');c.attach_source_journal_original(m,db);db.execute('BEGIN IMMEDIATE')
        failure,archives,old_files=c.failed_evidence(m,db,failure_ref);admitted=m.meta(db,'nativeResumeSourceBinding')
        m.require(old_files==proof['oldSourceFiles'],'Actual failure source generation changed')
        if out.exists():
            saved=m.read(out);m.require(saved['sourceProof']==proof_ref and saved['actualFailedRun']==failure_ref and saved['sourceFiles']==current and saved['sourceBinding']==m.meta(db,'binding'),'Existing bootstrap receipt belongs to another exact repair')
            after=saved['afterJournalHead'];committed=db.execute('SELECT digest FROM source_local_retentions WHERE sequence=?',(after['events'],)).fetchone()
            if committed==(after['head'],):db.rollback();print('ALREADY_COMMITTED',n.ref(m,out));return
            m.require(m.meta(db,'sourceLocalRetentionJournal')==saved['beforeJournalHead'] and m.meta(db,'nativeComposeCursor')==saved['beforeCursor'],'Interrupted bootstrap is not at its original atomic boundary')
        m.require(admitted and admitted['sourceFiles']==old_files,'The actual failed source is not the preceding admitted generation')
        allowed=[current,admitted['sourceFiles']]
        for transition in c.source_lineage(m,db,admitted):allowed.extend((transition['fromSourceFiles'],transition['toSourceFiles']))
        def check_sources(files):m.require(files in allowed,'Bootstrap used neither an admitted ancestor nor the exact reviewed new tuple')
        n.require_unfinalized(m,db);cursor=m.meta(db,'nativeComposeCursor');m.require(cursor and cursor['phase']=='NATIVE_TRANSFER' and not db.execute('SELECT 1 FROM cell_queue WHERE processed=0 LIMIT 1').fetchone(),'Bootstrap only repairs the completed, unfinalized native phase')
        before=n.counts(db);head=m.meta(db,'sourceLocalRetentionJournal');retention=m.meta(db,'nativeLocalRetention');semantic=n.snapshot_bindings(m,db);context=c.SourceVisualReservations(m,db)
        staged=c.stage_reserved_source_domain_queues(m,db,context);m.require(staged['sectionsRetained']>0,'No genuine saved-domain completed queue contact needs bootstrap')
        # This is the full normal gate, not a source-check or semantic bypass.
        # New event sourceFiles are allowed only by the exact reviewed proof.
        n.validate_resume(m,db,plan,source_files_validator=check_sources)
        after=n.counts(db);after_head=m.meta(db,'sourceLocalRetentionJournal')
        m.require(m.meta(db,'nativeComposeCursor')==cursor and m.meta(db,'nativeLocalRetention')==retention and m.meta(db,'nativeResumeSourceBinding')==admitted and n.snapshot_bindings(m,db)==semantic and c.source_files(m)==current,'Bootstrap changed cursor/retention/admitted source/semantic inputs')
        m.require(all(after[key]<=before[key] for key in before),'Bootstrap introduced new derived/native queue work')
        receipt={'schemaVersion':1,'status':'PASS','kind':'ATOMIC_RESERVED_SOURCE_DOMAIN_ORIGINAL_SECTION_REPAIR','sourceBinding':m.meta(db,'binding'),'sourceFiles':current,'sourceProof':proof_ref,'actualFailedRun':failure_ref,'actualArchivedSources':archives,'previousAdmittedSourceBinding':admitted,'allowedSourceFiles':allowed,
                 'beforeCursor':cursor,'afterCursor':m.meta(db,'nativeComposeCursor'),'beforeJournalHead':head,'afterJournalHead':after_head,'nativeLocalRetention':retention,'beforeCounts':before,'afterCounts':after,'staged':staged,'fullValidationBeforeCommit':True,'processedRowsReset':0,'mapWrites':0,'sourceProofRequiredForNewTuple':True,'receiptCommitAuthority':'The reported afterJournalHead sequence/digest must exist in committed source_local_retentions; this receipt file alone is not commit evidence.','repairSourceSha256':m.sha(__file__)}
        c.immutable_json(m,out,receipt);db.commit();m.require(db.execute('SELECT digest FROM source_local_retentions WHERE sequence=?',(after_head['events'],)).fetchone()==(after_head['head'],),'Bootstrap journal commit was not observed');print('COMMITTED',n.ref(m,out),{'contacts':len(staged['contacts']),'sectionsRetained':staged['sectionsRetained']})
    except BaseException:db.rollback();raise
    finally:
        if context:context.close()
        db.close()


if __name__=='__main__':main()
