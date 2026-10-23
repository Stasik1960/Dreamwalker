"""One immutable-source digest-cache fixture; accepted validations stay bound."""
import argparse
import ast
import copy
import io
import unittest
from pathlib import Path
import migrate as m
import native_certificates as c
import test_source_be_digest_cache as tests


BEFORE='60048781ac7b61b84bceac4204b30f04384e48ac658dfdd768978e14581538aa'
AFTER='1bab39299b139998fcd2c80367048b4f5fa2dba5c6420a54836d6de53998dac8'


def dump(node):return ast.dump(node,include_attributes=False)


def main():
    parser=argparse.ArgumentParser();parser.add_argument('--prior-proof',type=Path,default=m.DEFAULT_PLAN/'full/original-section-orphan-bridge-source-upgrade-proof.v11.json');parser.add_argument('--prior-proof-sha256',required=True);args=parser.parse_args()
    output=m.DEFAULT_PLAN/'full/source-be-digest-cache-source-upgrade-proof.v12.json';m.require(not output.exists(),'Preserve earlier source/fixture evidence')
    prior_ref={'path':str(m.checked(args.prior_proof)),'sha256':args.prior_proof_sha256};prior=c.bound(m,prior_ref)
    m.require(prior['status']=='PASS_SOURCE_AND_TARGETED_FIXTURES_ONLY' and prior['sourceFiles']['nativeCertificates']==BEFORE and prior['atomicRepairExecuted'] is True,'Bind the completed v11 source proof and actual committed orphan repair')
    repair=c.bound(m,prior['newAffectedAtomicWriterCheck']);m.require(repair['status']=='PASS' and repair['fullValidationBeforeCommit'] is True and repair['sourceFiles']==prior['sourceFiles'],'Prior complete source validation/repair changed')
    current=c.source_files(m);m.require(current['migrate']==prior['sourceFiles']['migrate'] and current['nativeResume']==prior['sourceFiles']['nativeResume'] and current['nativeCertificates']==AFTER,'Only the reviewed five-call cached-source digest change is allowed')
    baseline=m.ROOT/'research/source-drafts/before-source-be-digest-cache/native_certificates.py';m.require(m.sha(baseline)==BEFORE,'Archived pre-cache source changed')
    before=ast.parse(baseline.read_text(encoding='utf-8-sig'));after=ast.parse(Path(c.__file__).read_text(encoding='utf-8-sig'));normalized=copy.deepcopy(after)
    old_class=next(node for node in before.body if isinstance(node,ast.ClassDef) and node.name=='SourceVisualReservations');new_class=next(node for node in normalized.body if isinstance(node,ast.ClassDef) and node.name=='SourceVisualReservations')
    old_methods={node.name:node for node in old_class.body if isinstance(node,ast.FunctionDef)};new_methods={node.name:node for node in new_class.body if isinstance(node,ast.FunctionDef)}
    m.require(set(new_methods)-set(old_methods)=={'source_be_digest'} and not(set(old_methods)-set(new_methods)),'Only one local source-aware BE digest helper may be added')
    m.require({name for name in old_methods if dump(old_methods[name])!=dump(new_methods[name])}=={'zone_snapshot','owner_domain','original_domain'},'No source decoder, original be_digest, writer, guard or validator may change')
    calls=0
    for name in ('zone_snapshot','owner_domain','original_domain'):
        method=new_methods[name]
        for node in ast.walk(method):
            if isinstance(node,ast.Call) and isinstance(node.func,ast.Attribute) and isinstance(node.func.value,ast.Name) and node.func.value.id=='self' and node.func.attr=='source_be_digest':
                m.require(len(node.args)==4 and not node.keywords,'Source digest needs exact dimension/position/fields/proof');node.func.attr='be_digest';node.args=[node.args[2]];calls+=1
        if name=='original_domain':
            roots=[node for node in method.body if isinstance(node,ast.Assign) and len(node.targets)==1 and isinstance(node.targets[0],ast.Tuple) and [item.id for item in node.targets[0].elts]==['state','be','root_chunk']]
            m.require(len(roots)==1,'Root source proof must stay explicit');roots[0].targets[0].elts[2].id='_'
    m.require(calls==5,'Exactly five immutable source digest calls may be memoized');new_class.body=[node for node in new_class.body if not(isinstance(node,ast.FunctionDef) and node.name=='source_be_digest')]
    m.require(dump(before)==dump(normalized),'Unrelated source/runtime/retention/queue/finalization semantics changed')
    suite=unittest.defaultTestLoader.loadTestsFromTestCase(tests.SourceBeDigestCacheTests);m.require(suite.countTestCases()==1,'Run only the new cached-source digest fixture');stream=io.StringIO();result=unittest.TextTestRunner(stream=stream,verbosity=2).run(suite);m.require(result.wasSuccessful(),stream.getvalue());m.require(c.source_files(m)==current,'Production changed during the affected fixture')
    report={'schemaVersion':1,'status':'PASS_SOURCE_AND_TARGETED_FIXTURES_ONLY','kind':'NATIVE_CERTIFICATE_CONSUMER_SOURCE_UPGRADE','sourceFiles':current,'oldSourceFiles':prior['oldSourceFiles'],'actualFailedRun':prior['actualFailedRun'],'oldArchivedSources':prior['oldArchivedSources'],'acceptedV11Proof':prior_ref,'committedAtomicRepair':prior['newAffectedAtomicWriterCheck'],'controlledInterruptionEvidence':prior['controlledInterruptionEvidence'],'failureEvidenceNature':prior['failureEvidenceNature'],'naturalFailureClaimed':False,'cacheBaselineSource':{'path':str(baseline),'sha256':BEFORE},
            'delta':{'onlySourceAwareBeDigestHelperAndFiveCalls':True,'sourceDecoderAndBeDigestAstUnchanged':True,'allWritersGuardsValidatorsAstUnchanged':True,'boundedDigestStrings':4096,'key':'cachedPayloadSha256/dimension/block-position','requiresCurrentDecodedBeMapObjectAndMatchingProof':True,'immutableInternalDecodedSourceFieldsReadOnlyContract':True,'arbitraryMutableCopiesAndMocksAlwaysRehashed':True,'sourceAndJournalBytesUnchanged':True},'fixtures':{'run':result.testsRun,'failures':len(result.failures),'errors':len(result.errors),'output':stream.getvalue()},'fixtureSourceSha256':m.sha(tests.__file__),'proofSourceSha256':m.sha(__file__),'oldFixturesRepeated':False,'activeDatabaseOpened':False,'worldReads':0,'mapWrites':0,'builds':0,'minecraftRuns':0}
    m.atomic_json(output,report);print(report['status'],report['sourceFiles'])


if __name__=='__main__':main()
