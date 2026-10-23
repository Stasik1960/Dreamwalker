"""Source-only one-row correction delta from actual024801; new fixtures only."""
import ast
import io
import unittest
from pathlib import Path
import migrate as m
import native_certificates as c
import test_native_visual_protection


def dump(node):return ast.dump(node,include_attributes=False)
def functions(source):return {node.name:node for node in ast.parse(source).body if isinstance(node,ast.FunctionDef)}


def main():
    failed=m.ROOT/'research/processes/20261023T024801-migrate.json';failure=m.read(failed)
    archives={Path(row['archive']).name:row for row in failure['inputSources'].values()}
    expected={'migrate.py':'d6be0d24bc652015f1523d59c24528b0f1992cdd42a3a8d540e735a7f75caa8d',
              'native_resume.py':'086456be82275b89daa247b08d4df87c2eaa5bb0c4d105a1f40f07927bfa079b',
              'native_certificates.py':'ccf635ede2e1d6f41de2b0872e9600e075e02c6cf95ad11bd9606cbd78a5e1b0'}
    m.require(failure['status']=='FAIL' and failure['args'][-1]=='resume-native-compose','Actual024801 failed native resume required')
    for name,row in archives.items():m.require(m.sha(row['archive'])==row['sha256'] and (name not in expected or row['sha256']==expected[name]),'Actual024801 source archive changed')
    changes={}
    for filename,allowed in (('migrate.py',{'parser','freeze_inputs'}),('native_resume.py',{'validate_resume'}),('native_certificates.py',set())):
        before=Path(archives[filename]['archive']).read_text(encoding='utf-8-sig');after=Path(m.__file__).with_name(filename).read_text(encoding='utf-8-sig')
        old,new=functions(before),functions(after);m.require(set(old)<=set(new),'Old function removed')
        changed={name for name in old if dump(old[name])!=dump(new[name])};m.require(changed==allowed,'Unexpected old function delta: '+filename+' '+str(changed))
        changes[filename]={'changed':sorted(changed),'new':sorted(set(new)-set(old)),'allOtherOldFunctionAstsUnchanged':True}
        normalized=after
        if filename=='migrate.py':
            normalized=''.join(line for line in normalized.splitlines(keepends=True) if "sub.add_parser('correct-native-visual-protection')" not in line)
            hook="    if meta(db,'nativeVisualProtectionCorrection'):\n        __import__('native_certificates').validate_visual_protection_corrections(module_api(),db,finalized=True)\n        gate_names.append('nativeVisualProtectionCorrection')\n"
            m.require(normalized.count(hook)==1,'Final apply correction gate must be singular');normalized=normalized.replace(hook,'')
        if filename=='native_resume.py':
            hook='    native_certificates.validate_visual_protection_corrections(m,db)\n'
            m.require(normalized.count(hook)==1,'Resume/finalizing correction check must be singular');normalized=normalized.replace(hook,'')
        normalized_functions=functions(normalized)
        m.require(all(dump(normalized_functions[name])==dump(old[name]) for name in old),'Existing functions changed beyond explicit CLI/independent gates: '+filename)
        old_top=[node for node in ast.parse(before).body if not isinstance(node,ast.FunctionDef)]
        new_top=[node for node in ast.parse(after).body if not isinstance(node,ast.FunctionDef)]
        m.require([dump(node) for node in old_top]==[dump(node) for node in new_top],'Module bindings/imports changed: '+filename)
    stream=io.StringIO();suite=unittest.defaultTestLoader.loadTestsFromTestCase(test_native_visual_protection.VisualProtectionTests)
    result=unittest.TextTestRunner(stream=stream,verbosity=2).run(suite);m.require(result.wasSuccessful(),stream.getvalue())
    report={'schemaVersion':1,'status':'PASS_SOURCE_AND_TARGETED_FIXTURES_ONLY','kind':'NATIVE_CERTIFICATE_CONSUMER_SOURCE_UPGRADE',
            'sourceFiles':c.source_files(m),'oldSourceFiles':{name:archives[filename]['sha256'] for name,filename in (('migrate','migrate.py'),('nativeResume','native_resume.py'),('nativeCertificates','native_certificates.py'))},
            'actualFailedRun':{'path':str(failed),'sha256':m.sha(failed)},'oldArchivedSources':archives,'functionDelta':changes,
            'composeNativeRootsAtVisualGuardsAndNativeTailAstIdentical':True,'allOldRetentionAndRetractionFunctionAstsIdentical':True,'metadataSnapshotKeysUnchanged':True,
            'fixtureSourceSha256':m.sha(test_native_visual_protection.__file__),'proofSourceSha256':m.sha(__file__),
            'fixtures':{'run':result.testsRun,'failures':len(result.failures),'errors':len(result.errors),'output':stream.getvalue()},
            'priorFixturesNotRepeated':{'path':str(m.DEFAULT_PLAN/'full/preserved-native-whole-source-proof.json'),'sha256':m.sha(m.DEFAULT_PLAN/'full/preserved-native-whole-source-proof.json')},
            'activeDatabaseOpened':False,'worldReads':0,'mapWrites':0,'builds':0,'minecraftRuns':0}
    m.atomic_json(m.DEFAULT_PLAN/'full/native-visual-protection-source-upgrade-proof.json',report);print(report['status'],report['sourceFiles'])


if __name__=='__main__':main()
