"""Actual failed-source delta and new isolated lineage/certificate fixtures."""
import ast
import copy
import io
import unittest
from pathlib import Path
import migrate as m
import native_certificates as c
import test_native_certificates


def dump(node):return ast.dump(node,include_attributes=False)


def main():
    failed=m.ROOT/'research/processes/20261022T234516-migrate.json';receipt=m.read(failed)
    archived={Path(row['archive']).name:row for row in receipt['inputSources'].values()}
    for row in archived.values():m.require(m.sha(row['archive'])==row['sha256'],'Actual failed source archive changed')
    previous={name:ast.parse(Path(archived[name]['archive']).read_text(encoding='utf-8-sig')) for name in ('migrate.py','native_resume.py')}
    current={name:ast.parse(Path(m.__file__).with_name(name).read_text(encoding='utf-8-sig')) for name in previous}
    def functions(tree):return {node.name:node for node in tree.body if isinstance(node,ast.FunctionDef)}
    old,new=functions(previous['migrate.py']),functions(current['migrate.py'])
    changed=[name for name in old if dump(old[name])!=dump(new[name])]
    m.require(set(old)==set(new) and set(changed)=={'parser','freeze_inputs'},'Unexpected migrate function/selection/tail changes: '+str(changed))
    parser_text=Path(m.__file__).read_text(encoding='utf-8-sig')
    parser_text=''.join(line for line in parser_text.splitlines(keepends=True) if "sub.add_parser('upgrade-native-resume-binding')" not in line and "sub.add_parser('retain-native-certificate')" not in line)
    normalized_parser=functions(ast.parse(parser_text))['parser']
    m.require(dump(normalized_parser)==dump(old['parser']),'Parser delta exceeds the two explicit new commands')
    frozen=copy.deepcopy(new['freeze_inputs'])
    frozen.body=[node for node in frozen.body if not (isinstance(node,ast.If) and ast.unparse(node.test) in ("meta(db, 'nativeResumeSourceTransition')","meta(db, 'nativeResumeSourceBinding')"))]
    m.require(dump(frozen)==dump(old['freeze_inputs']),'Freeze delta exceeds explicit source-lineage binding')
    oldn,newn=functions(previous['native_resume.py']),functions(current['native_resume.py'])
    changedn=[name for name in oldn if dump(oldn[name])!=dump(newn[name])]
    m.require(set(oldn)==set(newn) and set(changedn)=={'validate_resume','apply_scope_delta'},'Unexpected legacy native-resume algorithm changes: '+str(changedn))
    m.require(dump(old['compose_native_tail'])==dump(new['compose_native_tail']) and dump(old['compose'])==dump(new['compose']),'Initial selection and complete native/finalization tail must remain AST identical')
    stream=io.StringIO();suite=unittest.defaultTestLoader.loadTestsFromModule(test_native_certificates)
    result=unittest.TextTestRunner(stream=stream,verbosity=2).run(suite)
    m.require(result.wasSuccessful(),stream.getvalue())
    report={'schemaVersion':1,'status':'PASS_SOURCE_AND_TARGETED_FIXTURES_ONLY','kind':'NATIVE_CERTIFICATE_CONSUMER_SOURCE_UPGRADE',
            'sourceFiles':c.source_files(m),'oldSourceFiles':{'migrate':archived['migrate.py']['sha256'],'nativeResume':archived['native_resume.py']['sha256']},
            'oldArchivedSources':archived,'actualFailedRun':{'path':str(failed),'sha256':m.sha(failed)},
            'migrateFunctionsChanged':changed,'legacyResumeFunctionsChanged':changedn,
            'migrateOtherFunctionAstsUnchanged':len(old)-len(changed),'legacyOtherFunctionAstsUnchanged':len(oldn)-len(changedn),
            'initialSelectionAndNativeFinalizeTailAstIdentical':True,'parserOnlyTwoCliAdditions':True,'freezeOnlyExplicitSourceLineage':True,
            'newModule':{'path':str(Path(c.__file__)),'sha256':m.sha(c.__file__)},
            'fixtures':{'run':result.testsRun,'failures':len(result.failures),'errors':len(result.errors),'output':stream.getvalue()},
            'priorFixturesNotRepeated':{'path':str(m.DEFAULT_PLAN/'full/native-resume-source-proof.json'),'sha256':m.sha(m.DEFAULT_PLAN/'full/native-resume-source-proof.json')},
            'proofSourceSha256':m.sha(__file__),'fixtureSourceSha256':m.sha(test_native_certificates.__file__),
            'activeDatabaseOpened':False,'worldReads':0,'mapWrites':0,'builds':0,'minecraftRuns':0}
    m.atomic_json(m.DEFAULT_PLAN/'full/native-certificate-source-proof.json',report)
    print(report['status'],report['sourceFiles'])


if __name__=='__main__':main()
