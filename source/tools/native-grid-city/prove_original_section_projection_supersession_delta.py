"""One new validator fixture; bind accepted v8 without rerunning its fixture."""
import argparse
import ast
import copy
import io
import unittest
from pathlib import Path
import migrate as m
import native_certificates as c
import test_original_section_projection_supersession as tests


OLD={'migrate':'056efec209523e3a48d6e66dd3082300c843ea1666c008555d2a2ad20a23781c','nativeResume':'3a3f3f7b1fecef1320da43114ccfba459ab4b7d8e90487fc19f0002c8ea26ff1','nativeCertificates':'eac031fad7d2202b80a33f3a2a36e00209e5f2a94737849af28bae7a676880c9'}
V8={'migrate':'38848f33d11c739a049bf0f2a28f5bea784dc5cb90a274e20a46b4cd70ac860a','nativeResume':OLD['nativeResume'],'nativeCertificates':'41fca137bb1121db8b755b4acda7870f89de17d8435bcc8653ac1d25b3008dc9'}
QUERY=('SELECT DISTINCT o.uuid FROM pose_cells AS p CROSS JOIN objects AS o INDEXED BY object_pose_zone '
       'WHERE o.pose=p.pose AND o.dim=? AND o.x BETWEEN ?-p.dx AND ?-p.dx '
       'AND o.z BETWEEN ?-p.dz AND ?-p.dz AND o.y BETWEEN ?-p.dy AND ?-p.dy LIMIT 4097')


def dump(node):return ast.dump(node,include_attributes=False)
def functions(module):return {node.name:node for node in module.body if isinstance(node,ast.FunctionDef)}


def main():
    parser=argparse.ArgumentParser();parser.add_argument('--interruption-receipt',default=str(m.ROOT/'research/processes/20261023T104808-migrate.controlled-stop.json'));args=parser.parse_args()
    directory=m.DEFAULT_PLAN/'full';output=directory/'original-section-projection-supersession-source-upgrade-proof.v9.json';m.require(not output.exists(),'Keep prior proof versions immutable')
    prior_ref={'path':str(directory/'original-section-pending-crossing-source-upgrade-proof.v8.json'),'sha256':'1b8f80417faf2052e0d8cd3d9bfd4e02e432b4ec8bf86a39f0ec092f2821a696'};prior=c.bound(m,prior_ref)
    m.require(prior['status']=='PASS_SOURCE_AND_TARGETED_FIXTURES_ONLY' and prior['oldSourceFiles']==OLD and prior['sourceFiles']==V8 and prior['fixtures']['run']==1,'Accepted v8 source/fixture evidence changed')
    current_files=c.source_files(m);archive=m.ROOT/'research/processes/20261023T104808-migrate.inputs';archived={};delta={}
    for filename,name in (('migrate.py','migrate'),('native_resume.py','nativeResume'),('native_certificates.py','nativeCertificates')):
        path=archive/filename;m.require(m.sha(path)==OLD[name],'Actual controlled-run archived source changed');archived[name]={'path':str(path),'sha256':OLD[name]}
        before=ast.parse(path.read_text(encoding='utf-8-sig'));current=ast.parse(Path(m.__file__).with_name(filename).read_text(encoding='utf-8-sig'));normalized=copy.deepcopy(current)
        if filename=='migrate.py':
            m.require(current_files[name]==V8[name],'The accepted v8 migration/index source must remain byte-identical')
            assignments=[node for node in normalized.body if isinstance(node,ast.Assign) and any(isinstance(target,ast.Name) and target.id=='SCHEMA' for target in node.targets)];m.require(len(assignments)==1,'Unknown schema assignment');line='CREATE INDEX IF NOT EXISTS object_pose_zone ON objects(pose,dim,x,z,y);\n';m.require(assignments[0].value.value.count(line)==1,'Accepted v8 derived index changed');assignments[0].value.value=assignments[0].value.value.replace(line,'');delta[filename]={'unchangedFromAcceptedV8':True}
        elif filename=='native_resume.py':
            calls=[node for node in ast.walk(normalized) if isinstance(node,ast.Call) and isinstance(node.func,ast.Attribute) and isinstance(node.func.value,ast.Name) and node.func.value.id=='native_certificates' and node.func.attr=='validate_owner_members']
            expected=ast.parse('native_certificates.validate_owner_members(m,db,owner,source_files_validator=check_sources)').body[0].value
            m.require(len(calls)==1 and dump(calls[0])==dump(expected),'Only the reviewed source-admission callback may be plumbed');calls[0].keywords=[]
            resume=functions(current)['validate_resume'];m.require(any(isinstance(node,ast.Call) and isinstance(node.func,ast.Attribute) and node.func.attr=='validate_source_local_retentions' for node in ast.walk(resume)),'The full local journal validator must remain mandatory in resume')
            delta[filename]={'soleOwnerValidatorCallGetsReviewedSourceCallback':True,'fullJournalValidationUnchanged':True}
        else:
            old_funcs,new_funcs=functions(before),functions(current);m.require(set(new_funcs)-set(old_funcs)=={'projected_member_original_retention'} and not(set(old_funcs)-set(new_funcs)),'Only one journal-backed supersession helper may be added')
            m.require({key for key in old_funcs if dump(old_funcs[key])!=dump(new_funcs[key])}=={'automatic_source_zone_retention','validate_owner_members'},'No other existing native/source/certificate function may change')
            writer=functions(normalized)['automatic_source_zone_retention'];body=next(node for node in writer.body if isinstance(node,ast.Try)).body;seed=ast.parse('discover(cells,section=True)').body[0];indices=[index for index,node in enumerate(body) if dump(node)==dump(seed)];m.require(len(indices)==1 and indices[0]>=3,'Accepted section seed changed');index=indices[0]
            expected=ast.parse('candidates=db.execute('+repr(QUERY)+',(dim,minimum[0],maximum[0],minimum[2],maximum[2],minimum[1],maximum[1]))\nuids.update(row[0] for row in candidates);budget()').body
            m.require([dump(node) for node in body[index-3:index]]==[dump(node) for node in expected],'Accepted v8 seed query changed');del body[index-3:index]
            validator=functions(normalized)['validate_owner_members'];old_validator=old_funcs['validate_owner_members'];expected_args=copy.deepcopy(old_validator.args);expected_args.args.append(ast.arg(arg='source_files_validator'));expected_args.defaults.append(ast.Constant(value=None));m.require(dump(validator.args)==dump(expected_args),'Owner validation callback changed');validator.args=copy.deepcopy(old_validator.args)
            loop=next(node for node in validator.body if isinstance(node,ast.For));branch=next(node for node in loop.body if isinstance(node,ast.If));expected=ast.parse('if not queue and projected_member_original_retention(m,db,owner,at,policy,source_files_validator):continue').body[0]
            m.require(branch.orelse and dump(branch.orelse[0])==dump(expected),'Only the absent historical projected queue may receive supersession');del branch.orelse[0]
            normalized.body=[node for node in normalized.body if not(isinstance(node,ast.FunctionDef) and node.name=='projected_member_original_retention')]
            delta[filename]={'acceptedV8InitialSeedUnchanged':True,'oneNewExactCommittedOriginalDomainHelper':True,'queuePresentAndOriginalPolicyAstUnchanged':True,'allWritersAndOtherValidatorsAstUnchanged':True,'outsideWholeCrossingMemberSupported':True,'noQueueDemandOrBridgeRecreated':True}
        m.require(dump(before)==dump(normalized),'Unrelated old source/transfer/retention/finalization behavior changed: '+filename)
    failure_path=m.ROOT/'research/processes/20261023T104808-migrate.json';failure=m.read(failure_path);stop_path=m.checked(args.interruption_receipt);stop=m.read(stop_path);stop_ref={'path':str(stop_path),'sha256':m.sha(stop_path)}
    m.require(failure.get('status')=='FAIL' and failure.get('exitCode')==1 and failure.get('kind')=='CONTROLLED_INTERRUPTION_RECEIPT' and failure.get('evidenceNature')=='ROOT_CONTROLLED_INTERRUPTION' and failure.get('interrupted') is True,'Bind the honest controlled interruption, not a natural failure')
    m.require(stop.get('status')=='PASS' and stop.get('kind')=='CONTROLLED_NATIVE_RESUME_INTERRUPTION' and stop.get('evidenceNature')=='ROOT_CONTROLLED_INTERRUPTION' and stop.get('actualExitCode')==1 and stop.get('stopped') is True and stop.get('controlledInterruption') is True and stop.get('runId')=='20261023T104808-migrate','Controlled stop evidence changed')
    linked=failure.get('interruptionReceipt',{});m.require(linked.get('sha256')==stop_ref['sha256'] and m.checked(linked.get('path',''))==stop_path,'Interrupted run companion changed');m.require(prior['actualFailedRun']=={'path':str(failure_path),'sha256':m.sha(failure_path)} and prior['controlledInterruptionEvidence']==stop_ref,'Accepted v8 did not bind the same controlled source generation')
    rows={Path(row['archive']).name:row for row in failure.get('inputSources',{}).values()}
    for filename,name in (('migrate.py','migrate'),('native_resume.py','nativeResume'),('native_certificates.py','nativeCertificates')):m.require(filename in rows and rows[filename]['sha256']==OLD[name] and m.sha(rows[filename]['archive'])==OLD[name],'Actual archived input tuple changed')
    suite=unittest.defaultTestLoader.loadTestsFromTestCase(tests.OriginalSectionProjectionSupersessionTests);m.require(suite.countTestCases()==1,'Run only the new supersession fixture');stream=io.StringIO();result=unittest.TextTestRunner(stream=stream,verbosity=2).run(suite);m.require(result.wasSuccessful(),stream.getvalue());m.require(c.source_files(m)==current_files,'Production changed during the new affected fixture')
    report={'schemaVersion':1,'status':'PASS_SOURCE_AND_TARGETED_FIXTURES_ONLY','kind':'NATIVE_CERTIFICATE_CONSUMER_SOURCE_UPGRADE','sourceFiles':current_files,'oldSourceFiles':OLD,'actualFailedRun':{'path':str(failure_path),'sha256':m.sha(failure_path)},'oldArchivedSources':archived,'acceptedPendingCrossingProof':prior_ref,'controlledInterruptionEvidence':stop_ref,'failureEvidenceNature':'ROOT_CONTROLLED_INTERRUPTION','naturalFailureClaimed':False,'delta':delta,
            'fixtures':{'run':result.testsRun,'failures':len(result.failures),'errors':len(result.errors),'output':stream.getvalue()},'fixtureSourceSha256':m.sha(tests.__file__),'proofSourceSha256':m.sha(__file__),'oldFixturesRepeated':False,'activeDatabaseOpened':False,'worldReads':0,'mapWrites':0,'builds':0,'minecraftRuns':0}
    m.atomic_json(output,report);print(report['status'],report['sourceFiles'])


if __name__=='__main__':main()
