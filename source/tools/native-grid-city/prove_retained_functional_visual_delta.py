"""Only the new P correction fixtures and exact ancestor-head upgrade repair."""
import ast
import io
import unittest
from pathlib import Path
import migrate as m
import native_certificates as c
import test_retained_functional_visual


def dump(node):return ast.dump(node,include_attributes=False)
def functions(source):return {node.name:node for node in ast.parse(source).body if isinstance(node,ast.FunctionDef)}


def main():
    failed=m.ROOT/'research/processes/20261023T054432-migrate.json';failure=m.read(failed)
    m.require(failure['status']=='FAIL' and failure['args'][-1]=='resume-native-compose','Actual054432 native failure required')
    archives={Path(row['archive']).name:row for row in failure['inputSources'].values()}
    expected={'migrate.py':'38dab8b724472418d49f91130b49fc954d3eb845829c83e817923371f91f5c56','native_resume.py':'a316616686d288cc5554441d2cc7d3fb6e747a2eef36e60e6ee6cbd379750987','native_certificates.py':'6e7e1cd4af4d26e2eb7122fa706c3a62ff4d393d56047f30d60d76a3246336f6'}
    for name,row in archives.items():m.require(m.sha(row['archive'])==row['sha256'] and (name not in expected or row['sha256']==expected[name]),'Actual054432 source archive changed')
    prior={'path':str(m.DEFAULT_PLAN/'full/native-whole-template-fallback-admission-source-upgrade-proof.json'),'sha256':'0be3bf7124e54e2dc0c7c5c80dd3dab4cec24b6b74345ab8f85b3a5bc33419cc'};c.bound(m,prior)
    changes={}
    for filename,allowed in (('migrate.py',{'parser','freeze_inputs'}),('native_resume.py',{'validate_resume'}),('native_certificates.py',{'upgrade_preview'})):
        before=Path(archives[filename]['archive']).read_text(encoding='utf-8-sig');after=Path(m.__file__).with_name(filename).read_text(encoding='utf-8-sig')
        old,new=functions(before),functions(after);m.require(set(old)<=set(new),'Old function removed')
        changed={name for name in old if dump(old[name])!=dump(new[name])};m.require(changed==allowed,'Unexpected existing-function change: '+filename+' '+str(changed));normalized=after
        if filename=='migrate.py':
            normalized=''.join(line for line in normalized.splitlines(keepends=True) if "sub.add_parser('correct-retained-functional-visual-protection')" not in line)
            hook="    if meta(db,'nativeRetainedFunctionalVisualCorrection'):\n        __import__('native_certificates').validate_retained_functional_visual_corrections(module_api(),db,finalized=True)\n        gate_names.append('nativeRetainedFunctionalVisualCorrection')\n"
            m.require(normalized.count(hook)==1,'Exactly one optional P final gate required');normalized=normalized.replace(hook,'')
        if filename=='native_resume.py':
            hook='    native_certificates.validate_retained_functional_visual_corrections(m,db,source_files_validator=check_sources)\n'
            m.require(normalized.count(hook)==1,'Exactly one scoped P resume check required');normalized=normalized.replace(hook,'')
        if filename=='native_certificates.py':
            new="    m.require(old_files==proof['oldSourceFiles'],'The source upgrade must begin at the exact actual failed historical consumer')\n"
            old_line="    m.require(receipt['sourceFiles']==old_files==proof['oldSourceFiles'],'The source upgrade must begin at the exact actual failed historical consumer')\n"
            m.require(normalized.count(new)==1,'Only actual/proof preceding source equality may be adjusted');normalized=normalized.replace(new,old_line)
            hook="    m.require(receipt['sourceFiles'] in allowed,'The retention head source is not a reviewed source-lineage ancestor')\n"
            m.require(normalized.count(hook)==1,'Head must still be a verified lineage ancestor');normalized=normalized.replace(hook,'')
        normalized_functions=functions(normalized)
        m.require(all(dump(old[name])==dump(normalized_functions[name]) for name in old),'Old semantic/writer functions changed beyond explicit new hooks and ancestor admission')
        m.require([dump(node) for node in ast.parse(before).body if not isinstance(node,ast.FunctionDef)]==[dump(node) for node in ast.parse(after).body if not isinstance(node,ast.FunctionDef)],'Module bindings/imports changed')
        changes[filename]={'changed':sorted(changed),'new':sorted(set(new)-set(old)),'allOtherOldFunctionAstsUnchanged':True,'allOldAstsIdenticalAfterExactHookAndAdmissionNormalization':True}
    m.require(m.sha(Path(m.__file__).with_name('context_stage.py'))==archives['context_stage.py']['sha256'],'Accepted context source changed')
    stream=io.StringIO();suite=unittest.defaultTestLoader.loadTestsFromTestCase(test_retained_functional_visual.RetainedFunctionalVisualTests)
    result=unittest.TextTestRunner(stream=stream,verbosity=2).run(suite);m.require(result.wasSuccessful(),stream.getvalue())
    report={'schemaVersion':1,'status':'PASS_SOURCE_AND_TARGETED_FIXTURES_ONLY','kind':'NATIVE_CERTIFICATE_CONSUMER_SOURCE_UPGRADE','sourceFiles':c.source_files(m),
            'oldSourceFiles':{name:archives[filename]['sha256'] for name,filename in (('migrate','migrate.py'),('nativeResume','native_resume.py'),('nativeCertificates','native_certificates.py'))},
            'actualFailedRun':{'path':str(failed),'sha256':m.sha(failed)},'oldArchivedSources':archives,'functionDelta':changes,'priorAcceptedAdmissionProof':prior,
            'allOldV1ToV5WritersGuardsAndAlgorithmsAstUnchanged':True,'metadataSnapshotKeysUnchanged':True,'normalActiveBindingAndRequireSourceFilesUnchanged':True,
            'ancestorRetentionHeadUpgradeWithoutNoopReceiptCovered':True,'fixtures':{'run':result.testsRun,'failures':len(result.failures),'errors':len(result.errors),'output':stream.getvalue()},
            'fixtureSourceSha256':m.sha(test_retained_functional_visual.__file__),'proofSourceSha256':m.sha(__file__),'oldFixturesNotRepeated':True,'activeDatabaseOpened':False,'worldReads':0,'mapWrites':0,'builds':0,'minecraftRuns':0}
    m.atomic_json(m.DEFAULT_PLAN/'full/retained-functional-visual-source-upgrade-proof.json',report);print(report['status'],report['sourceFiles'])


if __name__=='__main__':main()
