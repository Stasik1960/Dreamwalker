"""One combined general-overlap source proof and only NEW affected fixtures."""
import ast
import io
import unittest
from pathlib import Path
import migrate as m
import native_certificates as c
import test_source_visual_reservations as tests


def dump(value):return ast.dump(value,include_attributes=False)
def functions(source):return {node.name:node for node in ast.parse(source).body if isinstance(node,ast.FunctionDef)}


def main():
    failed=m.ROOT/'research/processes/20261023T054432-migrate.json';failure=m.read(failed)
    m.require(failure['status']=='FAIL' and failure['args'][-1]=='resume-native-compose','Use the real unchanged054432 v5 source generation')
    archives={Path(row['archive']).name:row for row in failure['inputSources'].values()}
    expected={'migrate.py':'38dab8b724472418d49f91130b49fc954d3eb845829c83e817923371f91f5c56','native_resume.py':'a316616686d288cc5554441d2cc7d3fb6e747a2eef36e60e6ee6cbd379750987','native_certificates.py':'6e7e1cd4af4d26e2eb7122fa706c3a62ff4d393d56047f30d60d76a3246336f6'}
    for name,row in archives.items():m.require(m.sha(row['archive'])==row['sha256'] and (name not in expected or expected[name]==row['sha256']),'Actual failed source archive changed')
    changes={}
    for filename in expected:
        before=Path(archives[filename]['archive']).read_text(encoding='utf-8-sig');after=Path(m.__file__).with_name(filename).read_text(encoding='utf-8-sig')
        old,new=functions(before),functions(after);normalized=after
        m.require(set(old)<=set(new),'An existing consumer function was removed')
        if filename=='migrate.py':
            normalized=''.join(line for line in normalized.splitlines(keepends=True) if "sub.add_parser('correct-retained-functional-visual-protection')" not in line and not line.startswith('CREATE TABLE IF NOT EXISTS source_visual_'))
            hooks=["    visual_reservations=__import__('native_certificates').SourceVisualReservations(module_api(),db,exports)\n",
                   "    if meta(db,'nativeRetainedFunctionalVisualCorrection'):\n        __import__('native_certificates').validate_retained_functional_visual_corrections(module_api(),db,finalized=True)\n        gate_names.append('nativeRetainedFunctionalVisualCorrection')\n",
                   "    if meta(db,'sourceVisualReservationJournal'):\n        __import__('native_certificates').validate_source_visual_reservations(module_api(),db,finalized=True)\n        gates_journal=meta(db,'sourceVisualReservationJournal')\n",
                   "    if meta(db,'sourceVisualReservationJournal'):current['sourceVisualReservationJournal']=gates_journal\n"]
            for hook in hooks:m.require(normalized.count(hook)==1,'Unexpected overlap/freeze hook multiplicity');normalized=normalized.replace(hook,'')
            guards=[line for line in before.splitlines(keepends=True) if 'require(not db.execute("SELECT 1 FROM protected_cells p' in line]
            calls=[line for line in normalized.splitlines(keepends=True) if 'visual_reservations.clearance(' in line]
            m.require(len(guards)==len(calls)==2,'Only the two actual native visual guard callsites may change')
            for call,guard in zip(calls,guards):normalized=normalized.replace(call,guard,1)
        elif filename=='native_resume.py':
            for hook in ('    native_certificates.validate_retained_functional_visual_corrections(m,db,source_files_validator=check_sources)\n','    native_certificates.validate_source_visual_reservations(m,db,source_files_validator=check_sources)\n'):
                m.require(normalized.count(hook)==1,'Exactly one optional validation hook required');normalized=normalized.replace(hook,'')
        else:
            current="    m.require(old_files==proof['oldSourceFiles'],'The source upgrade must begin at the exact actual failed historical consumer')\n"
            previous="    m.require(receipt['sourceFiles']==old_files==proof['oldSourceFiles'],'The source upgrade must begin at the exact actual failed historical consumer')\n"
            m.require(normalized.count(current)==1,'Actual failed source equality must remain exact');normalized=normalized.replace(current,previous)
            hook="    m.require(receipt['sourceFiles'] in allowed,'The retention head source is not a reviewed source-lineage ancestor')\n"
            m.require(normalized.count(hook)==1,'The historical head must be a verified source ancestor');normalized=normalized.replace(hook,'')
        norm=functions(normalized);m.require(all(dump(old[name])==dump(norm[name]) for name in old),'An old algorithm/writer changed outside the explicit overlap/admission hooks: '+filename)
        def globals_only(source,after=False):
            rows=[]
            for node in ast.parse(source).body:
                if isinstance(node,ast.FunctionDef):continue
                if after and isinstance(node,ast.ClassDef) and node.name=='SourceVisualReservations':continue
                if after and isinstance(node,ast.Assign) and any(isinstance(target,ast.Name) and target.id.startswith('OVERLAP_') for target in node.targets):continue
                rows.append(dump(node))
            return rows
        m.require(globals_only(before)==globals_only(normalized,True),'Unapproved module/global/schema delta: '+filename)
        changes[filename]={'changed':sorted(name for name in old if dump(old[name])!=dump(new[name])),'newFunctions':sorted(set(new)-set(old)),
                           'allOldFunctionsIdenticalAfterExactHookNormalization':True}
    m.require(m.sha(Path(m.__file__).with_name('context_stage.py'))==archives['context_stage.py']['sha256'],'Accepted context/physics/function source changed')
    suite=unittest.TestSuite(unittest.defaultTestLoader.loadTestsFromTestCase(cls) for cls in (tests.GeneralVisualTests,tests.AncestorVisualUpgradeTests))
    stream=io.StringIO();result=unittest.TextTestRunner(stream=stream,verbosity=2).run(suite);m.require(result.wasSuccessful(),stream.getvalue())
    report={'schemaVersion':1,'status':'PASS_SOURCE_AND_TARGETED_FIXTURES_ONLY','kind':'NATIVE_CERTIFICATE_CONSUMER_SOURCE_UPGRADE','sourceFiles':c.source_files(m),
            'oldSourceFiles':{name:archives[filename]['sha256'] for name,filename in (('migrate','migrate.py'),('nativeResume','native_resume.py'),('nativeCertificates','native_certificates.py'))},
            'actualFailedRun':{'path':str(failed),'sha256':m.sha(failed)},'oldArchivedSources':archives,'functionDelta':changes,
            'priorUnadmittedV6Checkpoint':str(m.ROOT/'research/checkpoints/20261023-pre-general-overlap-v6'),
            'onlyNativeVisualOccupancyGuardsChanged':True,'exactCollisionTransferUnchanged':True,'oldV1ToV5WritersUnchanged':True,
            'normalActiveBindingUnchanged':True,'metadataSnapshotKeysUnchanged':True,'ancestorHeadUpgradeWithoutFakeRetentionCovered':True,
            'perInstanceNbtAdded':False,'protectedRowsDeleted':False,'newWorldOrOwnerCensus':False,
            'fixtureSourceSha256':m.sha(tests.__file__),'proofSourceSha256':m.sha(__file__),
            'fixtures':{'run':result.testsRun,'failures':len(result.failures),'errors':len(result.errors),'output':stream.getvalue()},
            'oldFixturesRepeated':False,'activeDatabaseOpened':False,'worldReads':0,'mapWrites':0,'builds':0,'minecraftRuns':0}
    output=m.DEFAULT_PLAN/'full/source-visual-reservations-source-upgrade-proof.json';m.atomic_json(output,report);print(report['status'],report['sourceFiles'])


if __name__=='__main__':main()
