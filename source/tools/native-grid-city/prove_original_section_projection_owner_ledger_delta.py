"""One owner-PK comparison fix, accepted fixtures carried without replay."""
import argparse
import ast
import copy
import sqlite3
from pathlib import Path
import migrate as m
import native_resume as n
import native_certificates as c


V9={'migrate':'38848f33d11c739a049bf0f2a28f5bea784dc5cb90a274e20a46b4cd70ac860a','nativeResume':'476879e9e4ce62c4d968dca6ca2d25acf83e6a69a4d6c40c7277598737ec5a1c','nativeCertificates':'c33b58a15e8d70a8f5f08723272e24ec609ade469f76a6f75de50ccac76775ee'}


def dump(node):return ast.dump(node,include_attributes=False)


def main():
    parser=argparse.ArgumentParser();parser.add_argument('--plan',type=Path,default=m.DEFAULT_PLAN);args=parser.parse_args();plan=m.checked(args.plan)
    output=plan/'full/original-section-projection-owner-ledger-source-upgrade-proof.v10.json';m.require(not output.exists(),'Preserve all earlier proof versions')
    prior_ref={'path':str(plan/'full/original-section-projection-supersession-source-upgrade-proof.v9.json'),'sha256':'54d6d82ef145f9a2e47f8567c68c3cf9df1b6b96568188e5a6e78dd0793becc9'};prior=c.bound(m,prior_ref)
    m.require(prior['status']=='PASS_SOURCE_AND_TARGETED_FIXTURES_ONLY' and prior['sourceFiles']==V9 and prior['fixtures']['run']==1 and not prior['fixtures']['errors'] and not prior['fixtures']['failures'],'Accepted v9 fixture/source proof changed')
    current=c.source_files(m);archive=m.ROOT/'research/processes/20261023T120240-migrate.inputs';archived={}
    for filename,name in (('migrate.py','migrate'),('native_resume.py','nativeResume'),('native_certificates.py','nativeCertificates')):
        path=archive/filename;m.require(m.sha(path)==V9[name],'Actual v9 admission-preview archive changed');archived[name]=n.ref(m,path)
        if name!='nativeCertificates':m.require(current[name]==V9[name],'Only the historical ledger comparison may change');continue
        before=ast.parse(path.read_text(encoding='utf-8-sig'));after=ast.parse(Path(c.__file__).read_text(encoding='utf-8-sig'));normalized=copy.deepcopy(after)
        helper=next(node for node in normalized.body if isinstance(node,ast.FunctionDef) and node.name=='projected_member_original_retention')
        expected=ast.parse("stored=n.row_dict(db,'SELECT * FROM old.stored_contributions WHERE dim=? AND x=? AND y=? AND z=? AND uuid=?',(*args,owner['uuid']))").body[0];replacement=ast.parse("stored=n.row_dict(db,'SELECT * FROM old.stored_contributions WHERE dim=? AND x=? AND y=? AND z=?',args)").body[0];matches=[index for index,node in enumerate(helper.body) if dump(node)==dump(expected)]
        # The statement is within the candidate-event loop, not a new branch.
        loop=next(node for node in helper.body if isinstance(node,ast.For));matches=[index for index,node in enumerate(loop.body) if dump(node)==dump(expected)];m.require(len(matches)==1,'Only the owner UUID exact-PK SQL and argument may change');loop.body[matches[0]]=replacement
        m.require(dump(before)==dump(normalized),'Unrelated validator/writer/source semantics changed')
    db=sqlite3.connect('file:'+(plan/'plan.sqlite').as_posix()+'?mode=ro',uri=True)
    try:
        db.execute('PRAGMA query_only=ON');c.attach_source_journal_original(m,db);db.execute('BEGIN')
        def snapshot():return {key:m.meta(db,key) for key in ('nativeComposeCursor','sourceLocalRetentionJournal','sourceVisualReservationJournal','nativeLocalRetention','nativeResumeSourceBinding')}
        saved=snapshot();binding=saved['nativeResumeSourceBinding'];m.require(binding,'Existing admitted source binding required');allowed=[binding['sourceFiles']]
        for transition in c.source_lineage(m,db,binding):allowed.extend((transition['fromSourceFiles'],transition['toSourceFiles']))
        def check_sources(files):m.require(files in allowed,'Actual affected owner used an unreviewed source lineage tuple')
        affected=[];seen=set();reference=saved['nativeLocalRetention'];receipts=set();projected_count=0
        while reference:
            m.require(reference['sha256'] not in receipts,'Historical retention receipt cycle');receipts.add(reference['sha256']);receipt=c.bound(m,reference);preview=c.bound(m,receipt['preview'])
            for case in preview['cases']:
                for owner in case['retainedOwners']:
                    key=m.canonical(owner)
                    if key in seen:continue
                    seen.add(key);missing=[]
                    for policy in owner.get('memberPolicies',[]):
                        if policy['policy']=='original':continue
                        projected_count+=1;at=policy['position'];point_args=(owner['dimension'],*at)
                        if db.execute('SELECT 1 FROM cell_queue WHERE dim=? AND x=? AND y=? AND z=?',point_args).fetchone():continue
                        rows=n.row_dict(db,'SELECT * FROM old.stored_contributions WHERE dim=? AND x=? AND y=? AND z=?',point_args);own=[row for row in rows if row['uuid']==owner['uuid']]
                        m.require(sorted(own,key=m.canonical)==sorted(policy['storedContributions'],key=m.canonical),'Actual historical owner-PK row changed')
                        missing.append({'position':at,'policy':policy['policy'],'ownStoredRows':len(own),'foreignStoredRows':len(rows)-len(own)})
                    if missing:
                        c.validate_owner_members(m,db,owner,source_files_validator=check_sources);affected.append({'dimension':owner['dimension'],'uuid':owner['uuid'],'missingProjectedCells':missing})
            reference=receipt.get('previousRetention')
        m.require(affected,'The affected actual historical projection case was not exercised');m.require(snapshot()==saved and db.total_changes==0 and db.execute('PRAGMA query_only').fetchone()==(1,),'Read-only affected validation changed journal/cursor/state');db.rollback()
    finally:db.close()
    m.require(c.source_files(m)==current,'Production changed during affected read-only check')
    report={'schemaVersion':1,'status':'PASS_SOURCE_AND_TARGETED_FIXTURES_ONLY','kind':'NATIVE_CERTIFICATE_CONSUMER_SOURCE_UPGRADE','sourceFiles':current,'oldSourceFiles':prior['oldSourceFiles'],'actualFailedRun':prior['actualFailedRun'],'oldArchivedSources':prior['oldArchivedSources'],'acceptedV9Proof':prior_ref,'acceptedPendingCrossingProof':prior['acceptedPendingCrossingProof'],'v9AdmissionPreviewArchivedSources':archived,'controlledInterruptionEvidence':prior['controlledInterruptionEvidence'],'failureEvidenceNature':prior['failureEvidenceNature'],'naturalFailureClaimed':False,
            'delta':{'onlyHistoricalStoredContributionsOwnerUuidFilter':True,'stateNormalizationChanged':False,'fullOriginalSourceDomainValidationUnchanged':True,'queueDemandBridgeChecksUnchanged':True,'fullJournalBeforeWriteRemainsMandatory':True},'fixtures':{**prior['fixtures'],'carriedFromPriorProof':prior_ref,'repeated':False},'newReadOnlyAffectedCheck':{'historicalReceipts':len(receipts),'projectedMembersInspected':projected_count,'owners':affected,'affectedOwners':len(affected),'affectedCells':sum(len(row['missingProjectedCells']) for row in affected),'foreignLedgerRowsPreserved':sum(point['foreignStoredRows'] for row in affected for point in row['missingProjectedCells']),'journalAndCursorBeforeAfterEqual':True,'sqliteChanges':0},'proofSourceSha256':m.sha(__file__),'oldFixturesRepeated':False,'activeDatabaseOpened':'READ_ONLY_QUERY_ONLY','worldReads':0,'mapWrites':0,'builds':0,'minecraftRuns':0}
    m.atomic_json(output,report);print(report['status'],report['sourceFiles'],report['newReadOnlyAffectedCheck'])


if __name__=='__main__':main()
