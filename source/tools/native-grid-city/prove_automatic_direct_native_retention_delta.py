"""One affected integration fixture; bind accepted general/index evidence."""
import ast
import copy
import io
import unittest
from pathlib import Path
import migrate as m
import native_certificates as c
import test_automatic_direct_native_retention as tests


def dump(node):return ast.dump(node,include_attributes=False)
def contains(node,text):return any(isinstance(row,ast.Constant) and row.value==text for row in ast.walk(node))
def calls(node,name):return any(isinstance(row,ast.Attribute) and row.attr==name for row in ast.walk(node))
def functions(module):return {row.name:row for row in module.body if isinstance(row,ast.FunctionDef)}


def main():
    directory=m.DEFAULT_PLAN/'full';archive=m.ROOT/'research/processes/20261023T070751-migrate.inputs'
    expected={'migrate':'be1c70233fd9883757161c0a0c620acf1113004b5e374a60286aaa8211a24c5f','nativeResume':'71da350653fea36a94cec022c49b446c06515b806fe5a20a716cf2d3de97a482','nativeCertificates':'24e466c38b00c69496f5b3c93212c176b3bd1762e94248332aad3bd1ae181695'}
    prior_ref={'path':str(directory/'source-visual-reservations-owner-index-source-upgrade-proof.json'),'sha256':'3565f67e1ca461387146ecbc524ee167b9ac88fa03bdfbf621ed16f01ad905f1'};prior=c.bound(m,prior_ref)
    m.require(prior['sourceFiles']==expected and prior['fixtures']['run']==1 and prior['priorNewFixturesAcceptedWithoutRepeat']==6,'Accepted general/cache source evidence changed')
    fact_refs={name:{'path':str(directory/filename),'sha256':sha} for name,filename,sha in (
        ('retainedNative','070751-preserved-tree-member-demands.json','6a33cd923dd58eceae33454375276cdff74a6ad67dba1f2204834f77b5de9906'),
        ('directOwner','070751-direct-D-member-demands.json','8f92e96fcf6ea452b624ec2816ea5347f5f5340ffa75d1b90839021a523e3d30'))}
    for reference in fact_refs.values():c.bound(m,reference)
    changes={};archived={}
    for filename,name in (('migrate.py','migrate'),('native_resume.py','nativeResume'),('native_certificates.py','nativeCertificates')):
        old_path=archive/filename;m.require(m.sha(old_path)==expected[name],'Actual failed source archive changed: '+filename);archived[name]= {'path':str(old_path),'sha256':expected[name]}
        old=ast.parse(old_path.read_text(encoding='utf-8-sig'));current=ast.parse(Path(m.__file__).with_name(filename).read_text(encoding='utf-8-sig'));normalized=copy.deepcopy(current)
        old_functions=functions(old);new_functions=functions(current);norm=functions(normalized)
        if filename=='migrate.py':
            schema=next(row for row in normalized.body if isinstance(row,ast.Assign) and any(isinstance(target,ast.Name) and target.id=='SCHEMA' for target in row.targets))
            added='CREATE TABLE IF NOT EXISTS source_local_retentions(sequence INTEGER PRIMARY KEY,data TEXT NOT NULL,digest TEXT NOT NULL);\n'
            m.require(schema.value.value.count(added)==1,'Exactly one local journal table allowed');schema.value.value=schema.value.value.replace(added,'')
            tail=norm['compose_native_tail'];loop=next(row for row in tail.body if isinstance(row,ast.While));guarded=next(row for row in loop.body if isinstance(row,ast.Try))
            m.require(len(guarded.handlers)==1 and calls(guarded.handlers[0],'automatic_direct_native_retention') and calls(guarded.handlers[0],'rollback'),'Only rollback/direct-fallback/reload may wrap the existing batch')
            native_for=next(row for row in guarded.body if isinstance(row,ast.For))
            hooks=[row for row in native_for.body if isinstance(row,ast.If) and calls(row,'check_retained_native_member')]
            m.require(len(hooks)==1,'Retained physical guard must precede complete-native shortcut');native_for.body.remove(hooks[0])
            loop.body=[row for row in loop.body if row is not guarded]
            loop.body[2:2]=guarded.body
            freeze=norm['freeze_inputs'];freeze.body=[row for row in freeze.body if not isinstance(row,ast.If) or not contains(row.test,'sourceLocalRetentionJournal')]
        elif filename=='native_resume.py':
            fn=norm['validate_resume'];hooks=[row for row in fn.body if isinstance(row,ast.Expr) and calls(row,'validate_source_local_retentions')]
            m.require(len(hooks)==1,'Exactly one callback-plumbed local journal validation required');fn.body.remove(hooks[0])
        else:
            fn=norm['upgrade'];attempt=next(row for row in fn.body if isinstance(row,ast.Try));hooks=[row for row in attempt.body if isinstance(row,ast.Expr) and isinstance(row.value,ast.Call) and isinstance(row.value.func,ast.Name) and row.value.func.id=='attach_source_journal_original']
            m.require(len(hooks)==1,'Exactly one readonly old attachment before upgrade validation required');attempt.body.remove(hooks[0])
            old_class=next(row for row in old.body if isinstance(row,ast.ClassDef) and row.name=='SourceVisualReservations');new_class=next(row for row in normalized.body if isinstance(row,ast.ClassDef) and row.name=='SourceVisualReservations')
            new_class.body=[row for row in new_class.body if not isinstance(row,ast.FunctionDef) or row.name!='native_tree_envelope']
            methods=functions(new_class)
            envelope=methods['envelope'];m.require(isinstance(envelope.body[2],ast.Assign) and isinstance(envelope.body[3],ast.If) and calls(envelope.body[2],'native_tree_envelope'),'Only exact runtime-derived native footprint provider may precede raw geometry');del envelope.body[2:4]
            strict=methods['strict_original'];branch=next(row for row in strict.body if isinstance(row,ast.If));branch.body=[row for row in branch.body if not isinstance(row,ast.If) or not contains(row.test,'sourceLocalRetentionJournal')]
            class NormalizeReasons(ast.NodeTransformer):
                def visit_Tuple(self,node):
                    self.generic_visit(node);node.elts=[row for row in node.elts if not isinstance(row,ast.Name) or row.id!='LOCAL_ORIGINAL_REASON'];return node
                def visit_Compare(self,node):
                    self.generic_visit(node)
                    if len(node.ops)==1 and isinstance(node.ops[0],ast.NotIn) and isinstance(node.comparators[0],ast.Tuple) and len(node.comparators[0].elts)==1 and contains(node.comparators[0],'openable_both_states_local_helpers'):
                        node.ops=[ast.NotEq()];node.comparators=[node.comparators[0].elts[0]]
                    return node
            NormalizeReasons().visit(methods['clearance'])
            m.require(dump(old_class)==dump(new_class),'Previous positive visual/door/journal semantics changed outside exact native provider/local strict domain')
            normalized.body=[row for row in normalized.body if not isinstance(row,ast.Assign) or not any(isinstance(target,ast.Name) and target.id in ('LOCAL_ORIGINAL_REASON','NATIVE_TREE_FOOTPRINT_SHA') for target in row.targets)]
        m.require(all(dump(old_functions[name])==dump(norm[name]) for name in old_functions),'Existing function changed outside the explicit narrow hooks: '+filename)
        m.require([dump(row) for row in old.body if not isinstance(row,ast.FunctionDef)]==[dump(row) for row in normalized.body if not isinstance(row,ast.FunctionDef)],'Existing schema/class/module binding changed outside the exact delta: '+filename)
        changes[filename]={'changedFunctions':sorted(name for name in old_functions if dump(old_functions[name])!=dump(new_functions[name])),'newFunctions':sorted(set(new_functions)-set(old_functions)),'oldTransferAndCertificateAlgorithmsUnchanged':True}
    failure_ref=m.ROOT/'research/processes/20261023T070751-migrate.json';failure=m.read(failure_ref)
    m.require(failure['status']=='FAIL','Actual retained-native failure receipt required')
    suite=unittest.defaultTestLoader.loadTestsFromTestCase(tests.AutomaticDirectNativeRetentionTests);stream=io.StringIO();result=unittest.TextTestRunner(stream=stream,verbosity=2).run(suite);m.require(result.wasSuccessful(),stream.getvalue())
    report={'schemaVersion':1,'status':'PASS_SOURCE_AND_TARGETED_FIXTURES_ONLY','kind':'NATIVE_CERTIFICATE_CONSUMER_SOURCE_UPGRADE','sourceFiles':c.source_files(m),'oldSourceFiles':expected,
            'actualFailedRun':{'path':str(failure_ref),'sha256':m.sha(failure_ref)},'oldArchivedSources':archived,'priorAcceptedGeneralAndIndexProof':prior_ref,'actualBoundFacts':fact_refs,'delta':changes,
            'fixtures':{'run':result.testsRun,'failures':len(result.failures),'errors':len(result.errors),'output':stream.getvalue()},'fixtureSourceSha256':m.sha(tests.__file__),'proofSourceSha256':m.sha(__file__),
            'acceptedOldFixturesRepeated':False,'activeDatabaseOpened':False,'worldReads':0,'mapWrites':0,'builds':0,'minecraftRuns':0}
    output=directory/'automatic-direct-native-retention-source-upgrade-proof.json';m.atomic_json(output,report);print(report['status'],report['sourceFiles'])


if __name__=='__main__':main()
