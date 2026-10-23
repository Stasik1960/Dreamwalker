"""One initial-section completeness fix after an honest controlled stop."""
import argparse
import ast
import copy
import io
import unittest
from pathlib import Path
import migrate as m
import native_certificates as c
import test_original_section_pending_crossing as tests


OLD={'migrate':'056efec209523e3a48d6e66dd3082300c843ea1666c008555d2a2ad20a23781c','nativeResume':'3a3f3f7b1fecef1320da43114ccfba459ab4b7d8e90487fc19f0002c8ea26ff1','nativeCertificates':'eac031fad7d2202b80a33f3a2a36e00209e5f2a94737849af28bae7a676880c9'}
QUERY=('SELECT DISTINCT o.uuid FROM pose_cells AS p CROSS JOIN objects AS o INDEXED BY object_pose_zone '
       'WHERE o.pose=p.pose AND o.dim=? AND o.x BETWEEN ?-p.dx AND ?-p.dx '
       'AND o.z BETWEEN ?-p.dz AND ?-p.dz AND o.y BETWEEN ?-p.dy AND ?-p.dy LIMIT 4097')


def dump(node):return ast.dump(node,include_attributes=False)
def functions(module):return {node.name:node for node in module.body if isinstance(node,ast.FunctionDef)}


def main():
    parser=argparse.ArgumentParser();parser.add_argument('--interruption-receipt',default=str(m.ROOT/'research/processes/20261023T104808-migrate.controlled-stop.json'));args=parser.parse_args()
    directory=m.DEFAULT_PLAN/'full';output=directory/'original-section-pending-crossing-source-upgrade-proof.v8.json';m.require(not output.exists(),'Keep the accepted pending-crossing proof immutable')
    prior_ref={'path':str(directory/'original-section-fallback-source-upgrade-proof.v7.json'),'sha256':'bf501971b0a00ab00d08034eca19a4c7cb488f43fae007229f006d81b6780ff6'};prior=c.bound(m,prior_ref);m.require(prior['status']=='PASS_SOURCE_AND_TARGETED_FIXTURES_ONLY' and prior['sourceFiles']==OLD and prior['fixtures']['run']==1,'Accepted original-section v7 proof changed')
    draft_ref={'path':str(m.ROOT/'research/source-drafts/original-section-pending-crossing.v8.apply-patch.txt'),'sha256':'ea2673cb9a69a0ed4ee079b547b14263e0f45ffbd353e604c372eab605928a4e'};m.require(m.sha(draft_ref['path'])==draft_ref['sha256'],'Reviewed initial-seed draft changed')
    archive=m.ROOT/'research/processes/20261023T104808-migrate.inputs';archived={};changes={};current_files=c.source_files(m)
    for filename,name in (('migrate.py','migrate'),('native_resume.py','nativeResume'),('native_certificates.py','nativeCertificates')):
        path=archive/filename;m.require(m.sha(path)==OLD[name],'Actual interrupted-run source archive changed: '+filename);archived[name]={'path':str(path),'sha256':OLD[name]}
        before=ast.parse(path.read_text(encoding='utf-8-sig'));current=ast.parse(Path(m.__file__).with_name(filename).read_text(encoding='utf-8-sig'));normalized=copy.deepcopy(current)
        if filename=='migrate.py':
            assignments=[node for node in normalized.body if isinstance(node,ast.Assign) and any(isinstance(target,ast.Name) and target.id=='SCHEMA' for target in node.targets)];m.require(len(assignments)==1 and isinstance(assignments[0].value,ast.Constant),'Unknown migration schema binding');line='CREATE INDEX IF NOT EXISTS object_pose_zone ON objects(pose,dim,x,z,y);\n';m.require(assignments[0].value.value.count(line)==1,'Exactly one derived pose/root range index must be added');assignments[0].value.value=assignments[0].value.value.replace(line,'');changes[filename]={'onlyDerivedIndexAdded':'object_pose_zone(pose,dim,x,z,y)'}
        elif filename=='native_certificates.py':
            old_funcs,new_funcs=functions(before),functions(current);m.require(set(old_funcs)==set(new_funcs) and {name for name in old_funcs if dump(old_funcs[name])!=dump(new_funcs[name])}=={'automatic_source_zone_retention'},'Only initial-section visual crossing discovery may change')
            writer=functions(normalized)['automatic_source_zone_retention'];body=next(node for node in writer.body if isinstance(node,ast.Try)).body;seed=ast.parse('discover(cells,section=True)').body[0];indices=[index for index,node in enumerate(body) if dump(node)==dump(seed)];m.require(len(indices)==1 and indices[0]>=3,'Original section discovery call changed');index=indices[0]
            expected=ast.parse('candidates=db.execute('+repr(QUERY)+',(dim,minimum[0],maximum[0],minimum[2],maximum[2],minimum[1],maximum[1]))\nuids.update(row[0] for row in candidates);budget()').body
            m.require([dump(node) for node in body[index-3:index]]==[dump(node) for node in expected],'Only the exact bounded initial visual-intersection query may seed crossing owners');del body[index-3:index];changes[filename]={'onlyInitialSectionBulkSeed':True,'pendingInstancesIncluded':True,'exactTranslatedPoseCells':True,'candidateLimit':4097,'outsidePhysicalClosureAstUnchanged':True,'validatorsAndWritersAstUnchanged':True}
        m.require(dump(before)==dump(normalized),'Unrelated source section/member/retention/queue/finalization behavior changed: '+filename)
    failure_path=m.ROOT/'research/processes/20261023T104808-migrate.json';failure=m.read(failure_path);stop_path=m.checked(args.interruption_receipt);stop=m.read(stop_path);stop_ref={'path':str(stop_path),'sha256':m.sha(stop_path)}
    m.require(failure.get('status')=='FAIL' and failure.get('exitCode')==1 and failure.get('kind')=='CONTROLLED_INTERRUPTION_RECEIPT' and failure.get('evidenceNature')=='ROOT_CONTROLLED_INTERRUPTION' and failure.get('interrupted') is True,'The source admission must bind the honest root-controlled interruption, not a claimed natural failure')
    m.require(stop.get('status')=='PASS' and stop.get('kind')=='CONTROLLED_NATIVE_RESUME_INTERRUPTION' and stop.get('evidenceNature')=='ROOT_CONTROLLED_INTERRUPTION' and stop.get('actualExitCode')==1 and stop.get('stopped') is True and stop.get('controlledInterruption') is True and stop.get('runId')=='20261023T104808-migrate','Controlled interruption companion evidence is incomplete')
    linked=failure.get('interruptionReceipt',{});m.require(linked.get('sha256')==stop_ref['sha256'] and m.checked(linked.get('path',''))==stop_path,'Actual interrupted run is not bound to this controlled stop receipt')
    arguments=failure.get('args',[]);m.require(len(arguments)==3 and arguments[0]=='--plan' and m.checked(arguments[1])==m.checked(m.DEFAULT_PLAN) and arguments[2]=='resume-native-compose','Controlled stop belongs to another actual plan/phase')
    rows={Path(row['archive']).name:row for row in failure.get('inputSources',{}).values()}
    for filename,name in (('migrate.py','migrate'),('native_resume.py','nativeResume'),('native_certificates.py','nativeCertificates')):m.require(filename in rows and rows[filename]['sha256']==OLD[name] and m.sha(rows[filename]['archive'])==OLD[name],'Interrupted source input receipt changed')
    suite=unittest.defaultTestLoader.loadTestsFromTestCase(tests.OriginalSectionPendingCrossingTests);m.require(suite.countTestCases()==1,'Run only one new pending-crossing fixture');stream=io.StringIO();result=unittest.TextTestRunner(stream=stream,verbosity=2).run(suite);m.require(result.wasSuccessful(),stream.getvalue());m.require(c.source_files(m)==current_files,'Production changed during the new affected fixture')
    report={'schemaVersion':1,'status':'PASS_SOURCE_AND_TARGETED_FIXTURES_ONLY','kind':'NATIVE_CERTIFICATE_CONSUMER_SOURCE_UPGRADE','sourceFiles':current_files,'oldSourceFiles':OLD,'actualFailedRun':{'path':str(failure_path),'sha256':m.sha(failure_path)},'oldArchivedSources':archived,'priorOriginalSectionProof':prior_ref,'sourceDraft':draft_ref,'controlledInterruptionEvidence':stop_ref,'failureEvidenceNature':'ROOT_CONTROLLED_INTERRUPTION','naturalFailureClaimed':False,'delta':changes,'unownedFutureWideRootsRemainLazy':True,
            'fixtures':{'run':result.testsRun,'failures':len(result.failures),'errors':len(result.errors),'output':stream.getvalue()},'fixtureSourceSha256':m.sha(tests.__file__),'proofSourceSha256':m.sha(__file__),'oldFixturesRepeated':False,'activeDatabaseOpened':False,'worldReads':0,'mapWrites':0,'builds':0,'minecraftRuns':0}
    m.atomic_json(output,report);print(report['status'],report['sourceFiles'])


if __name__=='__main__':main()
