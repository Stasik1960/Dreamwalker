"""Source-only delta proof plus small temporary-SQLite crash fixtures."""
import ast
import copy
import hashlib
import io
import json
import unittest
from pathlib import Path

import migrate as m
import native_resume as n
import test_native_retention_members


def dump(node):return ast.dump(node,include_attributes=False)


def is_call(node,name):
    return isinstance(node,ast.Expr) and isinstance(node.value,ast.Call) and ast.unparse(node.value.func)==name


class NormalizeTail(ast.NodeTransformer):
    def visit_Expr(self,node):
        if is_call(node,'native_resume.checkpoint') or is_call(node,'native_resume.check_native_activation'):return None
        return self.generic_visit(node)

    def visit_Name(self,node):
        if node.id=='local_overlap_objects':return ast.parse('len(conflicts)',mode='eval').body
        return node

    def visit_Dict(self,node):
        node=self.generic_visit(node)
        pairs=[(k,v) for k,v in zip(node.keys,node.values) if not (k is None and isinstance(v,ast.IfExp) and 'nativeLocalRetention' in ast.unparse(v))]
        node.keys=[k for k,_ in pairs];node.values=[v for _,v in pairs]
        return node


def main():
    plan=m.DEFAULT_PLAN;archive=m.ROOT/'research/processes/20261022T222752-migrate.inputs/migrate.py'
    m.require(m.sha(archive)==n.FAILED_SOURCE,'Wrong actual failed source archive')
    old=ast.parse(archive.read_text(encoding='utf-8-sig'));new=ast.parse(Path(m.__file__).read_text(encoding='utf-8-sig'))
    oldf={s.name:s for s in old.body if isinstance(s,(ast.FunctionDef,ast.AsyncFunctionDef))}
    newf={s.name:s for s in new.body if isinstance(s,(ast.FunctionDef,ast.AsyncFunctionDef))}
    changed=[name for name in oldf if dump(oldf[name])!=dump(newf[name])]
    m.require(set(changed)=={'compose','parser','freeze_inputs'},'Unexpected existing function AST changes: '+str(changed))
    m.require(set(newf)-set(oldf)=={'compose_native_tail'},'Unexpected added migration functions')
    reconstructed=copy.deepcopy(newf['compose'])
    reconstructed.body=[node for node in reconstructed.body if not (isinstance(node,ast.Expr) and isinstance(node.value,ast.Call) and ast.unparse(node.value.func)=='require' and 'nativeLocalRetention' in ast.unparse(node)) and not is_call(node,'compose_native_tail')]
    tail=copy.deepcopy(newf['compose_native_tail'].body[1:]);filtered=[];skip_next=False
    for node in tail:
        if skip_next:
            m.require(is_call(node,'db.commit'),'Only the added finalize cursor commit may be removed from the proof');skip_next=False;continue
        if is_call(node,'native_resume.checkpoint') and any(isinstance(a,ast.Constant) and a.value=='FINALIZING' for a in node.value.args):skip_next=True;continue
        result=NormalizeTail().visit(node)
        if result is not None:filtered.append(result)
    # The one approved publication reorder: the exact needed-state output is
    # now durable before CURRENT generation commits. Move it back solely for
    # comparison with the historical complete tail; do not change its AST.
    needed_index=next(i for i,node in enumerate(filtered) if is_call(node,'atomic_json') and 'native-states-needed.json' in ast.unparse(node))
    needed=filtered.pop(needed_index)
    generation_index=next(i for i,node in enumerate(filtered) if is_call(node,'meta') and len(node.value.args)>1 and isinstance(node.value.args[1],ast.Constant) and node.value.args[1].value=='compositionGeneration')
    m.require(needed_index<=generation_index,'Required-state publication must precede CURRENT generation')
    m.require(is_call(filtered[generation_index+1],'db.commit'),'Generation commit boundary changed')
    filtered.insert(generation_index+2,needed)
    reconstructed.body.extend(filtered)
    m.require(dump(reconstructed)==dump(oldf['compose']),'Shared native/finalize tail differs beyond explicit finite guards/cursor/evidence binding')
    frozen=copy.deepcopy(newf['freeze_inputs'])
    frozen.body=[node for node in frozen.body if not (isinstance(node,ast.If) and "meta(db, 'nativeLocalRetention')"==ast.unparse(node.test))]
    m.require(dump(frozen)==dump(oldf['freeze_inputs']),'Freeze input delta exceeds the one new evidence binding')
    parser_text=Path(m.__file__).read_text(encoding='utf-8-sig')
    parser_lines=parser_text.splitlines(keepends=True)
    parser_text=''.join(line for line in parser_lines if "sub.add_parser('retain-native-local')" not in line and "sub.add_parser('resume-native-compose')" not in line)
    without_cli=next(s for s in ast.parse(parser_text).body if isinstance(s,ast.FunctionDef) and s.name=='parser')
    m.require(dump(without_cli)==dump(oldf['parser']),'Parser changes exceed the two exact new commands')
    oldother=[node for node in old.body if not isinstance(node,ast.FunctionDef)]
    newother=[node for node in new.body if not isinstance(node,ast.FunctionDef) and not (isinstance(node,ast.Import) and [a.name for a in node.names]==['native_resume'])]
    m.require([dump(node) for node in oldother]==[dump(node) for node in newother],'Other module-level AST changed')
    stream=io.StringIO();suite=unittest.defaultTestLoader.loadTestsFromModule(test_native_retention_members)
    result=unittest.TextTestRunner(stream=stream,verbosity=2).run(suite)
    m.require(result.wasSuccessful(),stream.getvalue())
    paths=[Path(m.__file__),Path(n.__file__),Path(test_native_retention_members.__file__),Path(__file__)]
    report={'schemaVersion':1,'status':'PASS_SOURCE_AND_ISOLATED_FIXTURES_ONLY','actualFailedSource':{'path':str(archive),'sha256':m.sha(archive)},
            'sourceFiles':{str(path):m.sha(path) for path in paths},'existingFunctionAstsUnchanged':len(oldf)-len(changed),
            'changedFunctions':changed,'sharedTailAstEqualAfterExplicitHooks':True,'otherModuleAstUnchanged':True,
            'allowedChanges':['Extract original native/finalize tail without selection changes','Explicit exact C retention activation guard','Commit native cursor with the existing batch commit','Bind finite retention receipt into generation/frozen inputs','Two explicit guarded CLI commands; initial compose refuses resetting a retained phase'],
            'fixtures':{'run':result.testsRun,'failures':len(result.failures),'errors':len(result.errors),'output':stream.getvalue()},
            'previousEightFixturesNotRepeated':{'path':str(plan/'full/native-resume-source-proof-v1.json'),'sha256':m.sha(plan/'full/native-resume-source-proof-v1.json')},
            'previousFourContinuationFixturesNotRepeated':{'path':str(plan/'full/native-resume-source-proof-v2.json'),'sha256':m.sha(plan/'full/native-resume-source-proof-v2.json')},
            'previousPublicationFixtureNotRepeated':{'path':str(plan/'full/native-resume-source-proof-v3.json'),'sha256':m.sha(plan/'full/native-resume-source-proof-v3.json')},
            'requiredStateOutputPublishedBeforeCurrentGenerationCommit':True,
            'activeDatabaseOpened':False,'worldReads':0,'mapWrites':0,'builds':0,'minecraftRuns':0}
    m.atomic_json(plan/'full/native-resume-source-proof.json',report)
    print(json.dumps({'status':report['status'],'fixtures':result.testsRun,'migrateSha256':m.sha(m.__file__),'nativeResumeSha256':m.sha(n.__file__)}))


if __name__=='__main__':main()
