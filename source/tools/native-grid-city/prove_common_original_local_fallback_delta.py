"""Only unchanged protected-object kind restriction; one affected fixture."""
import ast
import copy
import io
import unittest
from pathlib import Path
import migrate as m
import native_certificates as c
import test_common_original_local_fallback as tests


def dump(node):return ast.dump(node,include_attributes=False)
def functions(module):return {row.name:row for row in module.body if isinstance(row,ast.FunctionDef)}


def main():
    directory=m.DEFAULT_PLAN/'full';archive=m.ROOT/'research/processes/20261023T100518-migrate.inputs'
    prior_ref={'path':str(directory/'common-original-local-fallback-source-upgrade-proof.v5.json'),'sha256':'3f462b9a8d8f62d33bb65ed862ab291d72f6594451cef59e072f97cf486df028'};prior=c.bound(m,prior_ref)
    expected={'migrate':'667498d986941b4ac6128ad40238e6e1953ff34345ae3466a3dfddb4e7328e99','nativeResume':'3a3f3f7b1fecef1320da43114ccfba459ab4b7d8e90487fc19f0002c8ea26ff1','nativeCertificates':'e001bafefe14137b54834d52da1fe1cd62fd59a941bc7e6cbe1958686b2e9f45'}
    m.require(prior['sourceFiles']==expected and prior['fixtures']['run']==1 and prior['status']=='PASS_SOURCE_AND_TARGETED_FIXTURES_ONLY','Accepted common-original v5 source/fixture proof changed')
    archived={};changes={}
    for filename,name in (('migrate.py','migrate'),('native_resume.py','nativeResume'),('native_certificates.py','nativeCertificates')):
        path=archive/filename;m.require(m.sha(path)==expected[name],'Actual failed source archive changed: '+filename);archived[name]={'path':str(path),'sha256':expected[name]}
        old=ast.parse(path.read_text(encoding='utf-8-sig'));current=ast.parse(Path(m.__file__).with_name(filename).read_text(encoding='utf-8-sig'));normalized=copy.deepcopy(current)
        if filename=='native_certificates.py':
            before=functions(old);after=functions(current);changed={key for key in before if dump(before[key])!=dump(after[key])};m.require(set(before)==set(after) and changed=={'unchanged_protected_contact'},'Only unchanged protected-object identity kind restriction may change')
            writer=functions(normalized)['unchanged_protected_contact'];matches=[node for node in ast.walk(writer) if isinstance(node,ast.Call) and dump(node.func)==dump(ast.parse('m.require',mode='eval').body) and len(node.args)==2 and isinstance(node.args[1],ast.Constant) and node.args[1].value=='Unknown already-retained protected identity'];m.require(len(matches)==1 and dump(matches[0].args[0])==dump(ast.parse('len(obj)==1',mode='eval').body),'Unique exact protected-object identity must remain mandatory')
            matches[0].args[0]=ast.parse("len(obj)==1 and obj[0]['kind'] in ('static','whole','preserve')",mode='eval').body
            changes[filename]={'changedFunctions':sorted(changed),'onlyProtectedObjectKindWhitelistRemoved':True,'uniqueIdentityPoseSourceDataAndExcludedWorkGuardsUnchanged':True}
        m.require(dump(old)==dump(normalized),'Unrelated visual clearance/native/static/source-data/queue/journal/cursor/finalization algorithm changed: '+filename)
    suite=unittest.defaultTestLoader.loadTestsFromTestCase(tests.CommonOriginalFallbackTests);stream=io.StringIO();result=unittest.TextTestRunner(stream=stream,verbosity=2).run(suite);m.require(result.wasSuccessful(),stream.getvalue())
    failure=m.ROOT/'research/processes/20261023T100518-migrate.json';m.require(m.read(failure)['status']=='FAIL','Actual retained-native protected-contact failed resume changed')
    report={'schemaVersion':1,'status':'PASS_SOURCE_AND_TARGETED_FIXTURES_ONLY','kind':'NATIVE_CERTIFICATE_CONSUMER_SOURCE_UPGRADE','sourceFiles':c.source_files(m),'oldSourceFiles':expected,'actualFailedRun':{'path':str(failure),'sha256':m.sha(failure)},'oldArchivedSources':archived,'priorCommonSourceProof':prior_ref,'delta':changes,
            'fixtures':{'run':result.testsRun,'failures':len(result.failures),'errors':len(result.errors),'output':stream.getvalue()},'fixtureSourceSha256':m.sha(tests.__file__),'proofSourceSha256':m.sha(__file__),'oldFixturesRepeated':False,'activeDatabaseOpened':False,'worldReads':0,'mapWrites':0,'builds':0,'minecraftRuns':0}
    output=directory/'common-original-local-fallback-source-upgrade-proof.v6.json';m.atomic_json(output,report);print(report['status'],report['sourceFiles'])


if __name__=='__main__':main()
