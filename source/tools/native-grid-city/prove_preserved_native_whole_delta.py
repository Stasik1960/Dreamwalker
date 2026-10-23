"""Source-only third transition from actual014259; new fixtures only."""
import ast
import io
import unittest
from pathlib import Path
import migrate as m
import native_certificates as c
import test_preserved_native_wholes


def functions(path):return {node.name:node for node in ast.parse(Path(path).read_text(encoding='utf-8-sig')).body if isinstance(node,ast.FunctionDef)}
def dump(node):return ast.dump(node,include_attributes=False)


def main():
    failed=m.ROOT/'research/processes/20261023T014259-migrate.json';receipt=m.read(failed)
    archives={Path(row['archive']).name:row for row in receipt['inputSources'].values()}
    expected={'migrate.py':'97ea32f8343e0ef896f3b380c650f19a48436f5021c74eeb83bd3b48d9d0710b',
              'native_resume.py':'68b5ce6535f6d9ebe3f5c23425fe47f26e323579261b418d0d32e7071b3bee06',
              'native_certificates.py':'cf492f5178df9d3e321e796806030c1c10c3099c82353663f7522971489dc741'}
    m.require(receipt['status']=='FAIL' and receipt['args'][-1]=='resume-native-compose','Actual014259 failure required')
    for name,row in archives.items():m.require(m.sha(row['archive'])==row['sha256'] and (name not in expected or expected[name]==row['sha256']),'Actual014259 archive changed')
    changes={}
    for filename,allowed in (('migrate.py',{'parser'}),('native_resume.py',{'validate_resume','checkpoint'}),('native_certificates.py',set())):
        old=functions(archives[filename]['archive']);new=functions(Path(m.__file__).with_name(filename))
        m.require(set(old)<=set(new),'Old consumer functions were removed')
        changed={name for name in old if dump(old[name])!=dump(new[name])}
        m.require(changed==allowed,'Unexpected existing function delta in '+filename+': '+str(changed))
        changes[filename]={'changed':sorted(changed),'new':sorted(set(new)-set(old)),'otherOldFunctionAstsUnchanged':len(old)-len(changed)}
        source=Path(m.__file__).with_name(filename).read_text(encoding='utf-8-sig')
        if filename=='migrate.py':
            normalized=''.join(line for line in source.splitlines(keepends=True) if "sub.add_parser('retain-preserved-native-whole-certificate')" not in line)
            m.require(dump(functions_from_source(normalized)['parser'])==dump(old['parser']),'Parser changed beyond the single explicit whole-fallback command')
        if filename=='native_resume.py':
            dispatch="        if scope.get('kind')=='FINITE_PRESERVED_NATIVE_WHOLE_CERTIFICATE_V1':\n            native_certificates.preserved_whole_proof_files(m,scope)\n            native_certificates.validate_preserved_whole_state(m,db,scope,preview)\n"
            checkpoint="        if phase=='FINALIZING' and m.meta(db,'nativeLocalRetention'):\n            validate_resume(m,db,Path(db.execute('PRAGMA database_list').fetchone()[2]).parent)\n"
            m.require(source.count(dispatch)==source.count(checkpoint)==1,'Whole dispatch/final boundary must be singular')
            normalized=functions_from_source(source.replace(dispatch,'').replace(checkpoint,''))
            m.require(all(dump(normalized[name])==dump(old[name]) for name in allowed),'Resume changed beyond exact new-kind dispatch and final-retention check')
        # Also check module-level bindings/imports; only appended new functions
        # are permitted in the certificate consumer, no changed constants.
        old_top=[node for node in ast.parse(Path(archives[filename]['archive']).read_text(encoding='utf-8-sig')).body if not isinstance(node,ast.FunctionDef)]
        new_top=[node for node in ast.parse(source).body if not isinstance(node,ast.FunctionDef)]
        m.require([dump(node) for node in old_top]==[dump(node) for node in new_top],'Module-level semantics changed: '+filename)
    stream=io.StringIO();suite=unittest.defaultTestLoader.loadTestsFromTestCase(test_preserved_native_wholes.PreservedWholeTests)
    result=unittest.TextTestRunner(stream=stream,verbosity=2).run(suite);m.require(result.wasSuccessful(),stream.getvalue())
    report={'schemaVersion':1,'status':'PASS_SOURCE_AND_TARGETED_FIXTURES_ONLY','kind':'NATIVE_CERTIFICATE_CONSUMER_SOURCE_UPGRADE',
            'sourceFiles':c.source_files(m),'oldSourceFiles':{name:archives[filename]['sha256'] for name,filename in (('migrate','migrate.py'),('nativeResume','native_resume.py'),('nativeCertificates','native_certificates.py'))},
            'actualFailedRun':{'path':str(failed),'sha256':m.sha(failed)},'oldArchivedSources':archives,'functionDelta':changes,
            'oldV1AndRetractionFunctionsAstIdentical':True,'composeNativeRootsTailAndFinalizationAstIdentical':True,
            'oneFinalizingRetentionValidationBeforeDerivedRows':True,'originalClassificationAndSourceBindingsUnchanged':True,
            'fixtureSourceSha256':m.sha(test_preserved_native_wholes.__file__),'proofSourceSha256':m.sha(__file__),
            'fixtures':{'run':result.testsRun,'failures':len(result.failures),'errors':len(result.errors),'output':stream.getvalue()},
            'priorFixturesNotRepeated':{'path':str(m.DEFAULT_PLAN/'full/native-retraction-source-proof.json'),'sha256':m.sha(m.DEFAULT_PLAN/'full/native-retraction-source-proof.json')},
            'activeDatabaseOpened':False,'worldReads':0,'mapWrites':0,'builds':0,'minecraftRuns':0}
    m.atomic_json(m.DEFAULT_PLAN/'full/preserved-native-whole-source-proof.json',report);print(report['status'],report['sourceFiles'])


def functions_from_source(source):return {node.name:node for node in ast.parse(source).body if isinstance(node,ast.FunctionDef)}


if __name__=='__main__':main()
