"""Source-only delta from actual004453 and new retraction fixtures, no city DB."""
import ast
import io
import unittest
from pathlib import Path
import migrate as m
import native_certificates as c
import test_native_retractions


def functions(path):return {node.name:node for node in ast.parse(Path(path).read_text(encoding='utf-8-sig')).body if isinstance(node,ast.FunctionDef)}
def dump(node):return ast.dump(node,include_attributes=False)


def main():
    failed=m.ROOT/'research/processes/20261023T004453-migrate.json';receipt=m.read(failed);archives={Path(row['archive']).name:row for row in receipt['inputSources'].values()}
    for row in archives.values():m.require(m.sha(row['archive'])==row['sha256'],'Actual004453 archive changed')
    changes={}
    for filename,allowed in (('migrate.py',{'parser'}),('native_resume.py',{'validate_resume'}),('native_certificates.py',{'active_binding','require_source_files','upgrade_preview','upgrade'})):
        old=functions(archives[filename]['archive']);new=functions(Path(m.__file__).with_name(filename));changed={name for name in old if dump(old[name])!=dump(new[name])}
        m.require(changed==allowed,'Unexpected old function changes in '+filename+': '+str(changed));changes[filename]={'changed':sorted(changed),'new':sorted(set(new)-set(old)),'otherOldFunctionAstsUnchanged':len(old)-len(changed)}
        if filename=='migrate.py':
            normalized=''.join(line for line in Path(m.__file__).read_text(encoding='utf-8-sig').splitlines(keepends=True) if "sub.add_parser('retain-native-retraction-certificate')" not in line)
            parsed={node.name:node for node in ast.parse(normalized).body if isinstance(node,ast.FunctionDef)}
            m.require(dump(parsed['parser'])==dump(old['parser']),'Parser changed beyond the one explicit new command')
    stream=io.StringIO();suite=unittest.defaultTestLoader.loadTestsFromTestCase(test_native_retractions.RetractionTests);result=unittest.TextTestRunner(stream=stream,verbosity=2).run(suite)
    m.require(result.wasSuccessful(),stream.getvalue())
    report={'schemaVersion':1,'status':'PASS_SOURCE_AND_TARGETED_FIXTURES_ONLY','kind':'NATIVE_CERTIFICATE_CONSUMER_SOURCE_UPGRADE','sourceFiles':c.source_files(m),
            'oldSourceFiles':{'migrate':archives['migrate.py']['sha256'],'nativeResume':archives['native_resume.py']['sha256'],'nativeCertificates':archives['native_certificates.py']['sha256']},
            'actualFailedRun':{'path':str(failed),'sha256':m.sha(failed)},'oldArchivedSources':archives,'functionDelta':changes,
            'oldV1DataPoliciesAndRetainAstIdentical':True,'initialComposeNativeTailFinalizationAstIdentical':True,
            'fixtureSourceSha256':m.sha(test_native_retractions.__file__),'proofSourceSha256':m.sha(__file__),
            'fixtures':{'run':result.testsRun,'failures':len(result.failures),'errors':len(result.errors),'output':stream.getvalue()},
            'priorFixturesNotRepeated':{'path':str(m.DEFAULT_PLAN/'full/native-certificate-source-proof.json'),'sha256':m.sha(m.DEFAULT_PLAN/'full/native-certificate-source-proof.json')},
            'activeDatabaseOpened':False,'worldReads':0,'mapWrites':0,'builds':0,'minecraftRuns':0}
    m.atomic_json(m.DEFAULT_PLAN/'full/native-retraction-source-proof.json',report);print(report['status'],report['sourceFiles'])


if __name__=='__main__':main()
