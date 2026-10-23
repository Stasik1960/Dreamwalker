"""Only derived member-index delta; binds accepted general NEW6/P wire."""
import ast
import io
import unittest
from pathlib import Path
import migrate as m
import native_certificates as c
import test_source_visual_member_index as tests


def dump(value):return ast.dump(value,include_attributes=False)
def functions(source):return {row.name:row for row in ast.parse(source).body if isinstance(row,ast.FunctionDef)}


def main():
    directory=m.DEFAULT_PLAN/'full'
    previous_ref={'path':str(directory/'source-visual-reservations-source-upgrade-proof.json'),'sha256':'90c2249c2f5b56d8ffb3d7bcf0f32d61e054ffb05edfdf6331c3fbae14e481cb'};previous=c.bound(m,previous_ref)
    wire_ref={'path':str(directory/'source-visual-reservations-actual-P-wire.json'),'sha256':'dfa7196f10092431bcb721a58b5bce5a84a137d93b4316f06b7665e87fe36e1c'};wire=c.bound(m,wire_ref)
    plans_ref={'path':str(directory/'source-visual-member-query-plans.json'),'sha256':'1fd0ca3930abd7e0800e68f990d158f77f2782ae729bda5d4dabecaed7a50409'};c.bound(m,plans_ref)
    m.require(previous['status']=='PASS_SOURCE_AND_TARGETED_FIXTURES_ONLY' and previous['fixtures']['run']==6 and wire['databaseWrites']==0,'Prior actual general-rule acceptance changed')
    checkpoint=m.ROOT/'research/checkpoints/20261023-general-overlap-before-owner-index';changes={}
    for filename,name in (('migrate.py','migrate'),('native_resume.py','nativeResume'),('native_certificates.py','nativeCertificates')):
        path=checkpoint/filename;m.require(m.sha(path)==previous['sourceFiles'][name],'Accepted general source checkpoint changed')
        before=path.read_text(encoding='utf-8-sig');after=Path(m.__file__).with_name(filename).read_text(encoding='utf-8-sig');normalized=after
        if filename=='migrate.py':
            line='CREATE TABLE IF NOT EXISTS source_visual_owner_members(dim TEXT,uuid TEXT,sourceTable TEXT,x INTEGER,y INTEGER,z INTEGER,PRIMARY KEY(dim,uuid,sourceTable,x,y,z)) WITHOUT ROWID;\n'
            hook="    __import__('native_certificates').prepare_source_visual_member_index(module_api(),db)\n"
            m.require(normalized.count(line)==normalized.count(hook)==1,'Only the owner-position table and pre-queue preparation hook may be added');normalized=normalized.replace(line,'').replace(hook,'')
        elif filename=='native_resume.py':
            hook="        __import__('native_certificates').prepare_source_visual_member_index(m,db)\n";m.require(normalized.count(hook)==1,'Exactly one pre-native-resume cache boundary required');normalized=normalized.replace(hook,'')
        else:
            hook="        self.member_index=m.meta(db,'sourceVisualMemberIndex')\n        m.require(exports is None or self.member_index,'Live native composition requires its prepared owner-member index')\n"
            m.require(normalized.count(hook)==1,'Live index preparation must remain mandatory');normalized=normalized.replace(hook,'')
            new='            rows=self.owner_member_rows(dim,uid,table)\n';old="            rows=n.row_dict(db,'SELECT * FROM old.'+table+' WHERE dim=? AND uuid=? ORDER BY x,y,z LIMIT 4097',token)\n"
            m.require(normalized.count(new)==1,'Only the full original owner-row getter may change');normalized=normalized.replace(new,old)
            new="                details=m.canonical({'dimension':dim,'position':list(at),'incoming':incoming,'protected':row,'reason':str(error)})\n                if incoming.get('kind')=='legacy_native':message='Required native visual overhang meets a protected local object; retain that particular source root: '+str((dim,incoming['uuid'],tuple(at)))\n                else:message='Wide native visual transfer meets a protected local object: '+str((dim,tuple(at),incoming))\n                raise RuntimeError(message+'\\nProtected source contact requires a direct compatible/local fallback: '+details) from error\n"
            old="                raise RuntimeError('Protected source contact requires a direct compatible/local fallback: '+m.canonical({'dimension':dim,'position':list(at),'incoming':incoming,'protected':row,'reason':str(error)})) from error\n"
            m.require(normalized.count(new)==1,'Only the compatible actual-failure marker may change');normalized=normalized.replace(new,old)
        old,new=functions(before),functions(after);norm=functions(normalized)
        m.require(set(old)<=set(new) and all(dump(old[key])==dump(norm[key]) for key in old),'Existing functions changed outside exact cache hooks: '+filename)
        old_ast=ast.parse(before);new_ast=ast.parse(normalized)
        if filename=='native_certificates.py':
            old_class=next(row for row in old_ast.body if isinstance(row,ast.ClassDef) and row.name=='SourceVisualReservations')
            new_class=next(row for row in new_ast.body if isinstance(row,ast.ClassDef) and row.name=='SourceVisualReservations')
            added=[row for row in new_class.body if isinstance(row,ast.FunctionDef) and row.name=='owner_member_rows'];m.require(len(added)==1,'Exactly one indexed original-row getter added')
            new_class.body=[row for row in new_class.body if row not in added];m.require(dump(old_class)==dump(new_class),'General visual/door/journal predicates changed')
        m.require([dump(row) for row in old_ast.body if not isinstance(row,ast.FunctionDef)]==[dump(row) for row in new_ast.body if not isinstance(row,ast.FunctionDef)],'Other module/schema binding changed')
        changes[filename]={'changedFunctions':sorted(key for key in old if dump(old[key])!=dump(new[key])),'newFunctions':sorted(set(new)-set(old)),'onlyCacheBoundaryOrGetterChanged':True}
    suite=unittest.defaultTestLoader.loadTestsFromTestCase(tests.SourceVisualMemberIndexTests);stream=io.StringIO();result=unittest.TextTestRunner(stream=stream,verbosity=2).run(suite);m.require(result.wasSuccessful(),stream.getvalue())
    report={'schemaVersion':1,'status':'PASS_SOURCE_AND_TARGETED_FIXTURES_ONLY','kind':'NATIVE_CERTIFICATE_CONSUMER_SOURCE_UPGRADE','sourceFiles':c.source_files(m),'oldSourceFiles':previous['oldSourceFiles'],
            'actualFailedRun':previous['actualFailedRun'],'oldArchivedSources':previous['oldArchivedSources'],'priorGeneralProof':previous_ref,'priorActualPReadonlyWire':wire_ref,'actualBadPlannerEvidence':plans_ref,
            'acceptedGeneralSourceCheckpoint':str(checkpoint),'cacheDelta':changes,'generalOccupancyCollisionDoorJournalSemanticsUnchanged':True,'existingDirectFallbackFailureMarkerPreserved':True,'sourceOwnerFullRowsAndHashesPreserved':True,
            'originalDatabaseWrites':0,'nativeWorkOrCursorChanged':False,'oldFixturesRepeated':False,'priorNewFixturesAcceptedWithoutRepeat':6,
            'fixtures':{'run':result.testsRun,'failures':len(result.failures),'errors':len(result.errors),'output':stream.getvalue()},'fixtureSourceSha256':m.sha(tests.__file__),'proofSourceSha256':m.sha(__file__),
            'activeDatabaseOpened':False,'worldReads':0,'mapWrites':0,'builds':0,'minecraftRuns':0}
    output=directory/'source-visual-reservations-owner-index-source-upgrade-proof.json';m.atomic_json(output,report);print(report['status'],report['sourceFiles'])


if __name__=='__main__':main()
