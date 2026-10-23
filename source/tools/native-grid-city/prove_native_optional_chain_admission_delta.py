"""Compose prior accepted W-only proof with only the concrete admission repair."""
import ast
import io
import unittest
from pathlib import Path
import migrate as m
import native_certificates as c
import test_native_source_upgrade_chains


def dump(node):return ast.dump(node,include_attributes=False)
def functions(source):return {node.name:node for node in ast.parse(source).body if isinstance(node,ast.FunctionDef)}


def main():
    prior_path=m.DEFAULT_PLAN/'full/native-whole-template-fallback-source-upgrade-proof.json'
    prior_ref={'path':str(prior_path),'sha256':'47d8887f5c864db22141f5bf9fec2acf34e331742d3930e137b88abd4a210033'};prior=c.bound(m,prior_ref)
    m.require(prior['status']=='PASS_SOURCE_AND_TARGETED_FIXTURES_ONLY' and prior['fixtures']['run']==7 and not prior['fixtures']['failures'] and not prior['fixtures']['errors'],'Accepted seven W-only fixtures changed')
    m.require(m.sha(Path(m.__file__).with_name('test_native_whole_template_fallback.py'))==prior['fixtureSourceSha256'],'Accepted W-only fixture source changed')
    failed=m.ROOT/'research/processes/20261023T045807-migrate.json';failure=m.read(failed)
    m.require(failure['status']=='FAIL' and 'upgrade-native-resume-binding' in failure['args'],'Concrete source-admission failure required')
    archives={Path(row['archive']).name:row for row in failure['inputSources'].values()}
    names={'migrate.py':'migrate','native_resume.py':'nativeResume','native_certificates.py':'nativeCertificates'}
    changes={}
    for filename,name in names.items():
        row=archives[filename];m.require(m.sha(row['archive'])==row['sha256']==prior['sourceFiles'][name],'Accepted pre-admission source archive changed')
        before=Path(row['archive']).read_text(encoding='utf-8-sig');after=Path(m.__file__).with_name(filename).read_text(encoding='utf-8-sig')
        old,new=functions(before),functions(after);m.require(set(old)==set(new),'Admission plumbing cannot add/remove writer algorithms')
        changed={key for key in old if dump(old[key])!=dump(new[key])}
        expected={'validate_resume'} if filename=='native_resume.py' else {'validate_visual_protection_corrections','validate_whole_template_fallbacks'} if filename=='native_certificates.py' else set()
        m.require(changed==expected,'Unexpected admission delta: '+filename+' '+str(changed));normalized=after
        if filename=='native_resume.py':
            for function in ('validate_visual_protection_corrections','validate_whole_template_fallbacks'):
                hook='    native_certificates.'+function+'(m,db,source_files_validator=check_sources)\n'
                m.require(normalized.count(hook)==1,'Exactly one scoped chain callback required');normalized=normalized.replace(hook,'    native_certificates.'+function+'(m,db)\n')
        if filename=='native_certificates.py':
            for function in ('validate_visual_protection_corrections','validate_whole_template_fallbacks'):
                signature='def '+function+'(m,db,finalized=False,source_files_validator=None):\n'
                m.require(normalized.count(signature)==1,'Optional callback signature must be singular');normalized=normalized.replace(signature,'def '+function+'(m,db,finalized=False):\n')
            line='    check_sources=source_files_validator or (lambda files:require_source_files(m,db,files))\n'
            m.require(normalized.count(line)==2,'Both chain defaults must keep ordinary strict source validation');normalized=normalized.replace(line,'')
            for suffix in ('visual_correction_proof(m,document);check_correction_whole(m,db,document,finalized)','whole_template_fallback_proof(m,document);whole_template_fallback_state(m,db,document,finalized)'):
                line="        check_sources(receipt['sourceFiles']);"+suffix+'\n'
                m.require(normalized.count(line)==1,'Only source tuple admission may change');normalized=normalized.replace(line,"        require_source_files(m,db,receipt['sourceFiles']);"+suffix+'\n')
        normalized_functions=functions(normalized)
        m.require(all(dump(old[key])==dump(normalized_functions[key]) for key in old),'Semantic writer/validation body changed beyond callback plumbing')
        m.require([dump(node) for node in ast.parse(before).body if not isinstance(node,ast.FunctionDef)]==[dump(node) for node in ast.parse(after).body if not isinstance(node,ast.FunctionDef)],'Module bindings/imports changed')
        changes[filename]={'changed':sorted(changed),'allOtherOldFunctionAstsUnchanged':True,'allFunctionAstsIdenticalAfterExactCallbackNormalization':True}
    m.require(m.sha(Path(m.__file__).with_name('context_stage.py'))==archives['context_stage.py']['sha256'],'Accepted context source changed')
    stream=io.StringIO();suite=unittest.defaultTestLoader.loadTestsFromTestCase(test_native_source_upgrade_chains.SourceUpgradeChainTests)
    result=unittest.TextTestRunner(stream=stream,verbosity=2).run(suite);m.require(result.wasSuccessful(),stream.getvalue())
    report={'schemaVersion':1,'status':'PASS_SOURCE_AND_TARGETED_FIXTURES_ONLY','kind':'NATIVE_CERTIFICATE_CONSUMER_SOURCE_UPGRADE',
            'sourceFiles':c.source_files(m),'oldSourceFiles':prior['oldSourceFiles'],'actualFailedRun':prior['actualFailedRun'],'oldArchivedSources':prior['oldArchivedSources'],
            'priorAcceptedWholeFallbackProof':prior_ref,'concreteAdmissionFailure':{'path':str(failed),'sha256':m.sha(failed)},'admissionFailedSourceArchives':archives,'admissionFunctionDelta':changes,
            'wholeFallbackProofStatePreviewWriterAstsUnchanged':True,'bothOptionalChainSemanticGuardsUnchangedAfterSourceCallbackNormalization':True,
            'normalActiveBindingAndRequireSourceFilesAstsUnchanged':True,'metadataSnapshotKeysUnchanged':True,'priorSevenFixturesNotRepeated':True,
            'fixtures':{'run':result.testsRun,'failures':len(result.failures),'errors':len(result.errors),'output':stream.getvalue()},
            'fixtureSourceSha256':m.sha(test_native_source_upgrade_chains.__file__),'proofSourceSha256':m.sha(__file__),
            'activeDatabaseOpened':False,'worldReads':0,'mapWrites':0,'builds':0,'minecraftRuns':0}
    m.atomic_json(m.DEFAULT_PLAN/'full/native-whole-template-fallback-admission-source-upgrade-proof.json',report);print(report['status'],report['sourceFiles'])


if __name__=='__main__':main()
