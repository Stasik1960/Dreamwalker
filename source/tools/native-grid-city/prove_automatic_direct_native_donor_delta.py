"""Native-donor extension only; accepted static fallback fixture not repeated."""
import ast
import copy
import io
import unittest
from pathlib import Path
import migrate as m
import native_certificates as c
import test_automatic_direct_native_donor as tests


def dump(node):return ast.dump(node,include_attributes=False)
def functions(module):return {row.name:row for row in module.body if isinstance(row,ast.FunctionDef)}
def contains(node,text):return any(isinstance(row,ast.Constant) and row.value==text for row in ast.walk(node))
def calls(node,name):return any(isinstance(row,ast.Attribute) and row.attr==name for row in ast.walk(node))


def main():
    directory=m.DEFAULT_PLAN/'full';archive=m.ROOT/'research/processes/20261023T074619-migrate.inputs'
    prior_ref={'path':str(directory/'automatic-direct-native-retention-source-upgrade-proof.json'),'sha256':'5daf745743d4ee292415b8b85f5d64ce40722277759b9ae7bde5ef8850691945'};prior=c.bound(m,prior_ref)
    expected={'migrate':'02c3d246c01d4602f6a96ca5acb46b079df10a2429b95a960a0fe88ceba823b3','nativeResume':'907d1e2bd14b514780769692d350a9308815da71d7f1bee8d4b7a3cc2bdca6fd','nativeCertificates':'463ec97792298df40594fd076dd0cf7ba214e447d3928c367e0af2c5a35439b9'}
    m.require(prior['status']=='PASS_SOURCE_AND_TARGETED_FIXTURES_ONLY' and prior['sourceFiles']==expected and prior['fixtures']['run']==1,'Accepted direct-static source/fixture changed')
    facts={name:{'path':str(directory/filename),'sha256':sha} for name,filename,sha in (
        ('retainedNative','074619-preserved-tree-member-demands.json','8d123a0ca95b77eabffdb3b04afaf08e8d7f16cf8d308269f0b398e18488e1a0'),
        ('directNativeDonor','074619-direct-native-E-demands.json','3e732d7dc6a640d53eff6502b06ffce41caf23236877197ee455c361398c6a2b'),
        ('directStaticContact','074619-direct-static-F-demands.json','3c5fb58c479b9f356b6de88e688f3ba371765e491f85556cddcaa041904ed9b0'))}
    for reference in facts.values():c.bound(m,reference)
    changes={};archived={}
    for filename,name in (('migrate.py','migrate'),('native_resume.py','nativeResume'),('native_certificates.py','nativeCertificates')):
        path=archive/filename;m.require(m.sha(path)==expected[name],'Actual failed source archive changed: '+filename);archived[name]={'path':str(path),'sha256':expected[name]}
        old=ast.parse(path.read_text(encoding='utf-8-sig'));current=ast.parse(Path(m.__file__).with_name(filename).read_text(encoding='utf-8-sig'));normalized=copy.deepcopy(current);before=functions(old);after=functions(current);norm=functions(normalized)
        changed={key for key in before if dump(before[key])!=dump(after[key])}
        if filename=='migrate.py':
            m.require(changed=={'compose_native_tail'},'Only native caller skip/pre-shortcut guard may change')
            old_loop=next(row for row in before['compose_native_tail'].body if isinstance(row,ast.While));new_loop=next(row for row in norm['compose_native_tail'].body if isinstance(row,ast.While))
            old_try=next(row for row in old_loop.body if isinstance(row,ast.Try));new_try=next(row for row in new_loop.body if isinstance(row,ast.Try));old_for=next(row for row in old_try.body if isinstance(row,ast.For));new_for=next(row for row in new_try.body if isinstance(row,ast.For))
            old_guard=next(row for row in old_for.body if isinstance(row,ast.If) and calls(row,'check_retained_native_member'));new_guard=next(row for row in new_for.body if isinstance(row,ast.If) and calls(row,'check_retained_native_member'));new_guard.test=copy.deepcopy(old_guard.test)
            transfer=next(row for row in new_for.body if isinstance(row,ast.For));skips=[row for row in transfer.body if isinstance(row,ast.If) and calls(row,'local_native_domains')]
            m.require(len(skips)==1 and len(skips[0].body)==1 and isinstance(skips[0].body[0],ast.Continue),'Only already committed original-native identity may skip re-activation');transfer.body.remove(skips[0])
        elif filename=='native_resume.py':
            m.require(changed=={'check_native_activation'},'Only fail-closed local-original native activation defense may change')
            fn=norm['check_native_activation'];m.require(isinstance(fn.body[0],ast.If) and contains(fn.body[0].test,'sourceLocalRetentionJournal'),'Activation defense must be conditional on the accepted journal');del fn.body[0]
        else:
            allowed={'check_retained_native_member','local_original_scope','automatic_direct_native_retention'}
            m.require(changed==allowed and set(before)==set(after),'Only the existing bounded local retention functions may change')
            for key in allowed:normalized.body[normalized.body.index(norm[key])]=copy.deepcopy(before[key])
            old_class=next(row for row in old.body if isinstance(row,ast.ClassDef) and row.name=='SourceVisualReservations');new_class=next(row for row in normalized.body if isinstance(row,ast.ClassDef) and row.name=='SourceVisualReservations');old_methods=functions(old_class);new_methods=functions(new_class)
            m.require(set(new_methods)-set(old_methods)=={'local_native_domains'},'Exactly one per-context native identity memo may be added')
            m.require({key for key in old_methods if dump(old_methods[key])!=dump(new_methods[key])}=={'owner_domain'},'Existing source domain semantics changed outside native original classification admission')
            old_owner=old_methods['owner_domain'];new_owner=new_methods['owner_domain'];m.require(len(new_owner.args.args)==len(old_owner.args.args)+1 and new_owner.args.args[-1].arg=='original_native' and isinstance(new_owner.args.defaults[-1],ast.Constant) and new_owner.args.defaults[-1].value is False,'Explicit native-original admission must default to the old strict behavior')
            new_owner.args=copy.deepcopy(old_owner.args)
            old_guard=next(row for row in old_owner.body if isinstance(row,ast.Expr) and contains(row,'Unknown/private retained mechanism'));new_guard=next(row for row in new_owner.body if isinstance(row,ast.Expr) and contains(row,'Unknown/private retained mechanism'));new_owner.body[new_owner.body.index(new_guard)]=copy.deepcopy(old_guard)
            new_class.body=[row for row in new_class.body if not isinstance(row,ast.FunctionDef) or row.name!='local_native_domains'];m.require(dump(old_class)==dump(new_class),'Root/member/payload/ledger/door invariants must remain exact')
        m.require(dump(old)==dump(normalized),'Other source/schema/transfer/finalization/old journal or certificate algorithm changed: '+filename)
        changes[filename]={'changedFunctions':sorted(changed),'oldUnrelatedAlgorithmsAstIdentical':True}
    suite=unittest.defaultTestLoader.loadTestsFromTestCase(tests.AutomaticNativeDonorTests);stream=io.StringIO();result=unittest.TextTestRunner(stream=stream,verbosity=2).run(suite);m.require(result.wasSuccessful(),stream.getvalue())
    failure=m.ROOT/'research/processes/20261023T074619-migrate.json';m.require(m.read(failure)['status']=='FAIL','Actual native donor failure evidence changed')
    report={'schemaVersion':1,'status':'PASS_SOURCE_AND_TARGETED_FIXTURES_ONLY','kind':'NATIVE_CERTIFICATE_CONSUMER_SOURCE_UPGRADE','sourceFiles':c.source_files(m),'oldSourceFiles':expected,'actualFailedRun':{'path':str(failure),'sha256':m.sha(failure)},'oldArchivedSources':archived,
            'priorAcceptedAutomaticStaticProof':prior_ref,'actualDirectContactFacts':facts,'delta':changes,'fixtures':{'run':result.testsRun,'failures':len(result.failures),'errors':len(result.errors),'output':stream.getvalue()},'fixtureSourceSha256':m.sha(tests.__file__),'proofSourceSha256':m.sha(__file__),
            'oldFixturesRepeated':False,'activeDatabaseOpened':False,'worldReads':0,'mapWrites':0,'builds':0,'minecraftRuns':0}
    output=directory/'automatic-direct-native-donor-source-upgrade-proof.json';m.atomic_json(output,report);print(report['status'],report['sourceFiles'])


if __name__=='__main__':main()
