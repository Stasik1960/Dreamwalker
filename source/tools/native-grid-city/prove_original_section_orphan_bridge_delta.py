"""Bind the one bridge-writer fix and an independently audited actual repair."""
import argparse
import ast
import copy
from pathlib import Path
import migrate as m
import native_certificates as c


V10={'migrate':'38848f33d11c739a049bf0f2a28f5bea784dc5cb90a274e20a46b4cd70ac860a','nativeResume':'476879e9e4ce62c4d968dca6ca2d25acf83e6a69a4d6c40c7277598737ec5a1c','nativeCertificates':'d8446cf59e2e74fb281a020e2da81105a35e663da4f19816f3cee507d0110c6b'}


def dump(node):return ast.dump(node,include_attributes=False)


def main():
    parser=argparse.ArgumentParser();parser.add_argument('--plan',type=Path,default=m.DEFAULT_PLAN);parser.add_argument('--cleanup-receipt',type=Path);args=parser.parse_args();plan=m.checked(args.plan)
    output=plan/'full/original-section-orphan-bridge-source-upgrade-proof.v11.json';m.require(not output.exists(),'Preserve accepted proof versions')
    prior_ref={'path':str(plan/'full/original-section-projection-owner-ledger-source-upgrade-proof.v10.json'),'sha256':'f4a49ef270ab32ccfbe054957d20a57ccc4fd3695040488c523734df5564e358'};prior=c.bound(m,prior_ref)
    m.require(prior['status']=='PASS_SOURCE_AND_TARGETED_FIXTURES_ONLY' and prior['sourceFiles']==V10 and prior['newReadOnlyAffectedCheck']['sqliteChanges']==0,'Accepted v10 evidence changed')
    current=c.source_files(m);archive=m.ROOT/'research/processes/20261023T120802-migrate.inputs';archived={}
    for filename,name in (('migrate.py','migrate'),('native_resume.py','nativeResume'),('native_certificates.py','nativeCertificates')):
        path=archive/filename;m.require(m.sha(path)==V10[name],'Actual v10 admission-preview archive changed');archived[name]={'path':str(path),'sha256':V10[name]}
        if name!='nativeCertificates':m.require(current[name]==V10[name],'Only the section bridge writer may change');continue
        before=ast.parse(path.read_text(encoding='utf-8-sig'));after=ast.parse(Path(c.__file__).read_text(encoding='utf-8-sig'));normalized=copy.deepcopy(after)
        old_writer=next(node for node in before.body if isinstance(node,ast.FunctionDef) and node.name=='automatic_source_zone_retention');writer=next(node for node in normalized.body if isinstance(node,ast.FunctionDef) and node.name=='automatic_source_zone_retention')
        old_body=next(node for node in old_writer.body if isinstance(node,ast.Try)).body;body=next(node for node in writer.body if isinstance(node,ast.Try)).body
        expected=ast.parse("for row in plan:\n args=(dim,*row['position'])\n if row['retire']:\n  m.require(db.execute('DELETE FROM cell_queue WHERE dim=? AND x=? AND y=? AND z=? AND processed=?',(*args,row['before']['queue'][0]['processed'])).rowcount==1,'Retire only the exact undemanded original section work')\n if not any(row['remainingDemands'].values()):\n  m.require(db.execute('DELETE FROM bridge_cells WHERE dim=? AND x=? AND y=? AND z=?',args).rowcount==len(row['before']['bridge_cells']),'Delete only the exact undemanded original section bridge')").body[0]
        indices=[index for index,node in enumerate(body) if dump(node)==dump(expected)];m.require(len(indices)==1,'Only exact undemanded bridge deletion independent of queue retirement is allowed')
        original=[node for node in old_body if isinstance(node,ast.For) and isinstance(node.iter,ast.Name) and node.iter.id=='plan'];m.require(len(original)==1,'Unknown old section mutation loop');body[indices[0]]=copy.deepcopy(original[0])
        m.require(dump(before)==dump(normalized),'Other queue/native/source/validator behavior changed')
    reference=None
    if args.cleanup_receipt:
        reference={'path':str(m.checked(args.cleanup_receipt)),'sha256':m.sha(args.cleanup_receipt)};cleanup=c.bound(m,reference)
        # Optional executed repair evidence never replaces a historical journal.
        m.require(cleanup['status']=='PASS' and cleanup['kind']=='AUDITED_ORIGINAL_ZONE_ORPHAN_BRIDGE_CLEANUP' and cleanup['sourceFiles']==current,'Actual cleanup did not use this exact writer/validator generation')
        m.require(cleanup['fullValidationBeforeCommit'] is True and cleanup['deletedBridgeRows']>0 and cleanup['mapWrites']==0 and cleanup['processedRowsReset']==0,'Cleanup did not prove atomic original-data preservation')
        m.require(cleanup['beforeCursor']==cleanup['afterCursor'] and cleanup['beforeJournalHeads']==cleanup['afterJournalHeads'],'Cleanup changed accepted journal/cursor history')
        before_counts=cleanup['beforeCounts'];after_counts=cleanup['afterCounts'];m.require(set(before_counts)==set(after_counts) and all(after_counts[key]==before_counts[key]-(cleanup['deletedBridgeRows'] if key=='bridge_cells' else 0) for key in before_counts),'Cleanup changed more than the exact orphan bridge rows')
        m.require(cleanup['allDeletedOrphanOnly'] is True and cleanup['allOriginalSnapshotsValidated'] is True,'Cleanup included a demanded carrier or unverified original source')
    m.require(c.source_files(m)==current,'Production changed during source proof')
    report={'schemaVersion':1,'status':'PASS_SOURCE_AND_TARGETED_FIXTURES_ONLY','kind':'NATIVE_CERTIFICATE_CONSUMER_SOURCE_UPGRADE','sourceFiles':current,'oldSourceFiles':prior['oldSourceFiles'],'actualFailedRun':prior['actualFailedRun'],'oldArchivedSources':prior['oldArchivedSources'],'acceptedV10Proof':prior_ref,'acceptedPendingCrossingProof':prior['acceptedPendingCrossingProof'],'v10AdmissionPreviewArchivedSources':archived,'controlledInterruptionEvidence':prior['controlledInterruptionEvidence'],'failureEvidenceNature':prior['failureEvidenceNature'],'naturalFailureClaimed':False,
            'delta':{'onlyZoneWriterOrphanBridgeDeletion':True,'queueRetirementPolicyAstUnchanged':True,'allOtherSourceAstUnchanged':True,'allValidatorsStrictAndUnchanged':True,'actualAndBridgeUnchanged':True},'fixtures':{**prior['fixtures'],'carriedFromPriorProof':prior_ref,'repeated':False},'newAffectedAtomicWriterCheck':reference,'atomicRepairExecuted':reference is not None,'actualRepairRequiredBeforeUpgradePreview':True,'proofSourceSha256':m.sha(__file__),'oldFixturesRepeated':False,'activeDatabaseOpened':False,'worldReads':0,'mapWrites':0,'builds':0,'minecraftRuns':0}
    m.atomic_json(output,report);print(report['status'],report['sourceFiles'])


if __name__=='__main__':main()
