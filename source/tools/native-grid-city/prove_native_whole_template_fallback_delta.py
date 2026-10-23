"""Exact W-only source delta and only its new temporary fixtures."""
import ast
import io
import unittest
from pathlib import Path
import migrate as m
import native_certificates as c
import test_native_whole_template_fallback


def dump(node):return ast.dump(node,include_attributes=False)
def functions(source):return {node.name:node for node in ast.parse(source).body if isinstance(node,ast.FunctionDef)}


def main():
    failed=m.ROOT/'research/processes/20261023T042052-migrate.json';failure=m.read(failed)
    archives={Path(row['archive']).name:row for row in failure['inputSources'].values()}
    expected={'migrate.py':'7326d4cee598af6dccc2e2979e4cfc3fd88c36fe10107f60450d1e28e9bcc861',
              'native_resume.py':'5321d259e943f1f5a8a42d7f41b353b7bf38bb6d3a2bed9395c877d00a996f3f',
              'native_certificates.py':'a931ed7c7eb73850ee4eb089ead0daee558ebc35164f185c4889531d7a75239a'}
    m.require(failure['status']=='FAIL' and failure['args'][-1]=='resume-native-compose','Actual042052 native failure required')
    for name,row in archives.items():m.require(m.sha(row['archive'])==row['sha256'] and (name not in expected or row['sha256']==expected[name]),'Actual042052 source archive changed')
    m.require(m.sha(Path(m.__file__).with_name('context_stage.py'))==archives['context_stage.py']['sha256'],'Accepted context source changed')
    changes={}
    for filename,allowed in (('migrate.py',{'parser','freeze_inputs'}),('native_resume.py',{'validate_resume'}),('native_certificates.py',set())):
        before=Path(archives[filename]['archive']).read_text(encoding='utf-8-sig');after=Path(m.__file__).with_name(filename).read_text(encoding='utf-8-sig')
        old,new=functions(before),functions(after);m.require(set(old)<=set(new),'Old function removed')
        changed={name for name in old if dump(old[name])!=dump(new[name])};m.require(changed==allowed,'Unexpected old-function change: '+filename+' '+str(changed))
        changes[filename]={'changed':sorted(changed),'new':sorted(set(new)-set(old)),'allOtherOldFunctionAstsUnchanged':True}
        normalized=after
        if filename=='migrate.py':
            normalized=''.join(line for line in normalized.splitlines(keepends=True) if "sub.add_parser('fallback-native-whole-template')" not in line)
            hook="    if meta(db,'nativeWholeTemplateFallback'):\n        __import__('native_certificates').validate_whole_template_fallbacks(module_api(),db,finalized=True)\n        gate_names.append('nativeWholeTemplateFallback')\n"
            m.require(normalized.count(hook)==1,'Final whole fallback check must be singular');normalized=normalized.replace(hook,'')
        if filename=='native_resume.py':
            hook='    native_certificates.validate_whole_template_fallbacks(m,db)\n'
            m.require(normalized.count(hook)==1,'Resume/finalizing fallback check must be singular');normalized=normalized.replace(hook,'')
        normalized_functions=functions(normalized)
        m.require(all(dump(normalized_functions[name])==dump(old[name]) for name in old),'Existing bodies changed beyond explicit independent hooks: '+filename)
        old_top=[dump(node) for node in ast.parse(before).body if not isinstance(node,ast.FunctionDef)]
        new_top=[dump(node) for node in ast.parse(after).body if not isinstance(node,ast.FunctionDef)]
        m.require(old_top==new_top,'Module imports/bindings changed: '+filename)
    stream=io.StringIO();suite=unittest.defaultTestLoader.loadTestsFromTestCase(test_native_whole_template_fallback.WholeTemplateFallbackTests)
    result=unittest.TextTestRunner(stream=stream,verbosity=2).run(suite);m.require(result.wasSuccessful(),stream.getvalue())
    prior=m.DEFAULT_PLAN/'full/native-visual-protection-source-upgrade-proof.json'
    report={'schemaVersion':1,'status':'PASS_SOURCE_AND_TARGETED_FIXTURES_ONLY','kind':'NATIVE_CERTIFICATE_CONSUMER_SOURCE_UPGRADE',
            'sourceFiles':c.source_files(m),'oldSourceFiles':{name:archives[filename]['sha256'] for name,filename in (('migrate','migrate.py'),('nativeResume','native_resume.py'),('nativeCertificates','native_certificates.py'))},
            'actualFailedRun':{'path':str(failed),'sha256':m.sha(failed)},'oldArchivedSources':archives,'functionDelta':changes,
            'composeNativeRootsAtVisualGuardsAndNativeTailAstIdentical':True,'allOldRetentionRetractionWholeAndCorrectionFunctionAstsIdentical':True,'metadataSnapshotKeysUnchanged':True,
            'fixtureSourceSha256':m.sha(test_native_whole_template_fallback.__file__),'proofSourceSha256':m.sha(__file__),
            'fixtures':{'run':result.testsRun,'failures':len(result.failures),'errors':len(result.errors),'output':stream.getvalue()},
            'priorFixturesNotRepeated':{'path':str(prior),'sha256':m.sha(prior)},'activeDatabaseOpened':False,'worldReads':0,'mapWrites':0,'builds':0,'minecraftRuns':0}
    m.atomic_json(m.DEFAULT_PLAN/'full/native-whole-template-fallback-source-upgrade-proof.json',report);print(report['status'],report['sourceFiles'])


if __name__=='__main__':main()
