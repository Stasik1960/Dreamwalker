"""Bind accepted V12 validation and one new read-only source-JAR fixture."""
import argparse
import ast
import copy
import io
import unittest
from pathlib import Path
import migrate as m
import native_certificates as c
import test_source_jar_reader as tests


BEFORE='1bab39299b139998fcd2c80367048b4f5fa2dba5c6420a54836d6de53998dac8'


def dump(node):return ast.dump(node,include_attributes=False)


def main():
    parser=argparse.ArgumentParser();parser.add_argument('--prior-proof-sha256',default='4979406956de20006d47c2b93779df5227656d56d728d9cafd2368102175a332');args=parser.parse_args()
    output=m.DEFAULT_PLAN/'full/source-jar-reader-source-upgrade-proof.v13.json';m.require(not output.exists(),'Preserve all accepted source/fixture evidence')
    prior_ref={'path':str(m.DEFAULT_PLAN/'full/source-be-digest-cache-source-upgrade-proof.v12.json'),'sha256':args.prior_proof_sha256};prior=c.bound(m,prior_ref);m.require(prior['status']=='PASS_SOURCE_AND_TARGETED_FIXTURES_ONLY' and prior['sourceFiles']['nativeCertificates']==BEFORE and prior['fixtures']['run']==1,'Accepted V12 source/fixture proof changed')
    preview_ref={'path':str(m.DEFAULT_PLAN/'full/source-be-digest-cache-upgrade-preview.v12.json'),'sha256':'bd466e7bd28a634472ed50dff848178f054e5dec28aa5f9f9ddfafe56d0e0013'};preview=c.bound(m,preview_ref)
    run_ref={'path':str(m.ROOT/'research/processes/20261023T124808-migrate.json'),'sha256':'147738c320c942ea0271cab10d89e866327a55bd009981fa2282529ff3298593'};run=c.bound(m,run_ref)
    m.require(preview['status']=='NATIVE_SOURCE_UPGRADE_PREVIEW' and preview['toSourceFiles']==prior['sourceFiles'] and preview['fromSourceFiles']==prior['oldSourceFiles'] and preview['failedRun']==prior['actualFailedRun'],'The accepted V12 preview belongs to another source generation')
    m.require(run['status']=='PASS' and run['exitCode']==0 and '--execute' not in run['args'] and float(run['seconds'])>0 and prior_ref['sha256'] in run['args'],'Bind the actual successful read-only full-validation preview')
    current=c.source_files(m);m.require(all(current[key]==prior['sourceFiles'][key] for key in ('migrate','nativeResume')),'Only the owned source-JAR reader may change')
    baseline=m.ROOT/'research/source-drafts/before-source-jar-reader/native_certificates.py';m.require(m.sha(baseline)==BEFORE,'Archived V12 source baseline changed');before=ast.parse(baseline.read_text(encoding='utf-8-sig'));after=ast.parse(Path(c.__file__).read_text(encoding='utf-8-sig'));normalized=copy.deepcopy(after)
    old_class=next(node for node in before.body if isinstance(node,ast.ClassDef) and node.name=='SourceVisualReservations');new_class=next(node for node in normalized.body if isinstance(node,ast.ClassDef) and node.name=='SourceVisualReservations');old={node.name:node for node in old_class.body if isinstance(node,ast.FunctionDef)};new={node.name:node for node in new_class.body if isinstance(node,ast.FunctionDef)}
    m.require(set(new)-set(old)=={'close','__del__'} and not(set(old)-set(new)) and {key for key in old if dump(old[key])!=dump(new[key])}=={'authority'},'Only authority reader reuse and owned lifecycle methods may change')
    expected=ast.parse("def close(self):\n owned=self.__dict__.pop('_source_jar',None)\n if owned is not None:owned[1].close()\ndef __del__(self):\n try:self.close()\n except Exception:pass").body
    m.require(dump(new['close'])==dump(expected[0]) and dump(new['__del__'])==dump(expected[1]),'Reader cleanup must be idempotent/partial-safe and own only its JAR handle')
    token=ast.parse('token not in self.__dict__',mode='eval').body;branch=next(node for node in new['authority'].body if isinstance(node,ast.If) and dump(node.test)==dump(token))
    expected=ast.parse("if '_source_jar' not in self.__dict__:self._source_jar=(jarref,m.zipfile.ZipFile(jar))\narchive=self._source_jar\nm.require(archive[0]==jarref,'Source JAR reader belongs to another bound resource')\nraw=archive[1].read(entry)").body
    m.require([dump(node) for node in branch.body[:4]]==[dump(node) for node in expected],'The same bound read-only JAR must serve each exact source entry');branch.body[:4]=ast.parse('with m.zipfile.ZipFile(jar) as archive:raw=archive.read(entry)').body
    new_class.body=[node for node in new_class.body if not(isinstance(node,ast.FunctionDef) and node.name in ('close','__del__'))];m.require(dump(before)==dump(normalized),'Other binding/hash/decoder/guard/writer/validator behavior changed')
    suite=unittest.defaultTestLoader.loadTestsFromTestCase(tests.SourceJarReaderTests);m.require(suite.countTestCases()==1,'Run only the new source-JAR fixture');stream=io.StringIO();result=unittest.TextTestRunner(stream=stream,verbosity=2).run(suite);m.require(result.wasSuccessful(),stream.getvalue());m.require(c.source_files(m)==current,'Production changed during the affected fixture')
    report={'schemaVersion':1,'status':'PASS_SOURCE_AND_TARGETED_FIXTURES_ONLY','kind':'NATIVE_CERTIFICATE_CONSUMER_SOURCE_UPGRADE','sourceFiles':current,'oldSourceFiles':prior['oldSourceFiles'],'actualFailedRun':prior['actualFailedRun'],'oldArchivedSources':prior['oldArchivedSources'],'acceptedV12Proof':prior_ref,'acceptedV12ReadOnlyPreview':preview_ref,'actualPreviewPassReceipt':run_ref,'committedAtomicRepair':prior['committedAtomicRepair'],'controlledInterruptionEvidence':prior['controlledInterruptionEvidence'],'failureEvidenceNature':prior['failureEvidenceNature'],'naturalFailureClaimed':False,'readerBaselineSource':{'path':str(baseline),'sha256':BEFORE},
            'delta':{'onlyAuthorityContextJarReaderReuseAndOwnedClose':True,'centralDirectoryOncePerContext':True,'resourceJarAndSpecByteBindingsAstUnchanged':True,'missingEntryAndMalformedJsonBehaviorUnchanged':True,'idempotentExplicitCloseAndPartialSafeDestructor':True,'noSelfCycleOrBorrowedHandleClose':True,'allOtherSourceAstUnchanged':True},'fixtures':{'run':result.testsRun,'failures':len(result.failures),'errors':len(result.errors),'output':stream.getvalue()},'fixtureSourceSha256':m.sha(tests.__file__),'proofSourceSha256':m.sha(__file__),'oldFixturesRepeated':False,'activeDatabaseOpened':False,'worldReads':0,'mapWrites':0,'builds':0,'minecraftRuns':0}
    m.atomic_json(output,report);print(report['status'],report['sourceFiles'])


if __name__=='__main__':main()
