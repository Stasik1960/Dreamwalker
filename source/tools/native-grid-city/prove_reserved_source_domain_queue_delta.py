"""Bind the actual V13 finalization failure and ONE new q1-domain fixture.

The reviewed patch is replayed only in memory to compare source ASTs. This
producer neither opens the project database nor applies any source changes.
"""
import ast
import io
import unittest
from pathlib import Path
import migrate as m
import native_certificates as c
import test_reserved_source_domain_queue as tests


OLD={'migrate':'38848f33d11c739a049bf0f2a28f5bea784dc5cb90a274e20a46b4cd70ac860a','nativeResume':'476879e9e4ce62c4d968dca6ca2d25acf83e6a69a4d6c40c7277598737ec5a1c','nativeCertificates':'72947b20a3e6c869c42c0f53518ee645e9f9ba700ea3777b5095e29c038ad738'}
CURRENT={'migrate':'c723e946f7d95e7edd4423c3f32e96c67a0af1c0f649b5e5f7ce90aef915c15f','nativeResume':'3f06112ec958bdeeeb1d981530f2928459910ce319b8c150d76c5e723cbc6639','nativeCertificates':'cfc8d38aaa00964391e002a46da8f8952aa9966de5a367d9e849c5fb36d8a3d7'}
FILES={'migrate.py':'migrate','native_resume.py':'nativeResume','native_certificates.py':'nativeCertificates'}
CONTEXT_SHA='f04362e1021287ac16f613d85ff85033d92f5b315c4bb71112df45b3f09b6d45'
DRAFT_SHA='5e3a4109da870252ca9ad1d031ad379f8ef2e00e8c7ddf9b511823d59df6a034'


def dump(node):return ast.dump(node,include_attributes=False)
def functions(module):return {node.name:node for node in module.body if isinstance(node,ast.FunctionDef)}


def reviewed_sources(archived,patch):
    """Interpret only this pinned three-file apply_patch as source strings."""
    expected={name:Path(row['path']).read_text(encoding='utf-8-sig') for name,row in archived.items()}
    active=None;hunk=[];seen=set()
    def flush():
        if not hunk:return
        while hunk and hunk[0] in ('',' '):hunk.pop(0)
        while hunk and hunk[-1] in ('',' '):hunk.pop()
        if not hunk:return
        old=[];new=[]
        for line in hunk:
            m.require(line=='' or line[0] in (' ','+','-'),'Unknown reviewed hunk syntax')
            if not line:old.append('');new.append('')
            else:
                if line[0] in (' ','-'):old.append(line[1:])
                if line[0] in (' ','+'):new.append(line[1:])
        before='\n'.join(old)+'\n';after='\n'.join(new)+'\n'
        m.require(active in expected and before.strip() and expected[active].count(before)==1,'Reviewed patch context must uniquely match the actual failed source: '+str(active))
        expected[active]=expected[active].replace(before,after,1);hunk.clear()
    for line in patch.splitlines():
        if line.startswith('*** Update File: '):
            flush();active=FILES.get(Path(line.split(': ',1)[1]).name);m.require(active is not None,'The review may change only the three native-phase sources');seen.add(active)
        elif line=='@@':flush()
        elif line.startswith('*** '):flush()
        else:m.require(active is not None,'Patch lacks an exact reviewed source');hunk.append(line)
    flush();m.require(seen==set(OLD),'Reviewed boundary patch must bind all three sources')
    return expected


def main():
    directory=m.DEFAULT_PLAN/'full';output=directory/'reserved-source-domain-queue-source-upgrade-proof.v14.json'
    m.require(not output.exists(),'Keep every accepted source and fixture proof immutable')
    prior_ref={'path':str(directory/'source-jar-reader-source-upgrade-proof.v13.json'),'sha256':'66e19c2e388f06510185b3e182c547ec5466102805041ba2ed9a978686d23a00'}
    prior=c.bound(m,prior_ref);m.require(prior['status']=='PASS_SOURCE_AND_TARGETED_FIXTURES_ONLY' and prior['sourceFiles']==OLD and prior['fixtures']['run']==1,'Accepted V13 source/fixture proof changed')
    transition_ref={'path':str(directory/'native-consumer-source-upgrades/4302f55f624920506dd727c918a31a81d42baa97f4d4fd53a090d5784f82a365/transition.json'),'sha256':'561e6257fc102ffd2fa4304a1344dc75ecff8ca0aa1dc7a4851ee77a6bab6d81'}
    transition=c.bound(m,transition_ref);m.require(transition['status']=='PASS' and transition['kind']=='NATIVE_CONSUMER_SOURCE_ONLY_TRANSITION' and transition['toSourceFiles']==OLD and transition['afterCounts']==transition['beforeCounts'] and transition['processedQueueRowsReset']==0,'Actual V13 admission transition changed')
    admitted_proof=c.bound(m,transition['sourceProof']);m.require(admitted_proof==prior,'V13 transition does not bind this accepted source proof')
    failed_ref={'path':str(m.ROOT/'research/processes/20261023T133636-migrate.json'),'sha256':'b87b449709d7be0451cf33cd6ebfc0284149c7d9a54fe79cad3cb2b35918a2a6'}
    failure=c.bound(m,failed_ref);m.require(failure['status']=='FAIL' and failure['exitCode']==1 and failure['args']==['--plan','../research/native-grid-plan','resume-native-compose'] and not failure.get('interrupted') and failure['seconds']>0,'Bind the actual natural V13 failed resume, not an older controlled stop')
    log_ref={'path':failure['log'],'sha256':m.sha(failure['log'])};message=Path(log_ref['path']).read_text(encoding='utf-8')
    m.require('REAL_OCCUPANCY: retained runtime/service/door domain has replacement work' in message and "217, 142, -648" in message and "checkpoint(module_api(),db,'FINALIZING')" in message,'Actual failure is not the saved-domain finalization boundary')
    archived={};actual_inputs={Path(row['archive']).name:row for row in failure['inputSources'].values()}
    for filename,name in FILES.items():
        row=actual_inputs[filename];m.require(row['sha256']==OLD[name] and m.sha(row['archive'])==OLD[name],'Actual V13 failed source archive changed')
        archived[name]={'path':row['archive'],'sha256':OLD[name]}
    context=actual_inputs['context_stage.py'];m.require(context['sha256']==CONTEXT_SHA and m.sha(context['archive'])==CONTEXT_SHA and m.sha(Path(m.__file__).with_name('context_stage.py'))==CONTEXT_SHA,'Accepted context/function source changed')
    diagnostic_ref={'path':str(directory/'reserved-queue-contacts.20261023.json'),'sha256':'fa1bd68b65ff151959c08061a422bd59ace54e32511dea91d3b19945c3f87a40'}
    diagnostic=c.bound(m,diagnostic_ref);m.require(diagnostic['status']=='READ_ONLY_DIAGNOSTIC' and diagnostic['databaseWrites']==0 and diagnostic['mapWrites']==0 and diagnostic['domainsInspected']==52 and diagnostic['conflictPoints']==2,'Actual saved-domain diagnostic changed')
    m.require({tuple(row['position']) for row in diagnostic['points']}=={(217,142,-648),(217,143,-648)} and all(row['sourceMatchesEverySavedPoint'] and len(row['queue'])==1 and row['queue'][0]['processed']==1 for row in diagnostic['points']),'Actual source-domain contacts must be exact unchanged completed work')
    draft_ref={'path':str(m.ROOT/'research/source-drafts/reserved-source-domain-queue.v14.apply-patch.txt'),'sha256':DRAFT_SHA};m.require(m.sha(draft_ref['path'])==DRAFT_SHA,'Reviewed source boundary patch changed')
    expected=reviewed_sources(archived,Path(draft_ref['path']).read_text(encoding='utf-8'));current=c.source_files(m);m.require(current==CURRENT,'Use only the actual root-applied reviewed V14 tuple');changes={}
    for filename,name in FILES.items():
        before=ast.parse(Path(archived[name]['path']).read_text(encoding='utf-8-sig'));actual=ast.parse(Path(m.__file__).with_name(filename).read_text(encoding='utf-8-sig'))
        m.require(dump(actual)==dump(ast.parse(expected[name])),'Production differs from the exact reviewed boundary-only patch: '+filename)
        old_functions,new_functions=functions(before),functions(actual);added=set(new_functions)-set(old_functions);changed={key for key in old_functions if key in new_functions and dump(old_functions[key])!=dump(new_functions[key])}
        wanted=({'automatic_source_zone_retention'},{'stage_reserved_source_domain_queues'}) if name=='nativeCertificates' else ({'compose_native_tail'},set()) if name=='migrate' else ({'resume'},set())
        m.require((changed,added)==wanted and not(set(old_functions)-set(new_functions)),'Only the saved-domain stage and required boundary integration may change')
        changes[filename]={'changedFunctions':sorted(changed),'addedFunctions':sorted(added),'allOtherAstUnchanged':True}
        if name=='nativeResume':m.require(dump(old_functions['checkpoint'])==dump(new_functions['checkpoint']) and dump(old_functions['validate_resume'])==dump(new_functions['validate_resume']),'The mandatory full finalization validator must remain unchanged')
    suite=unittest.defaultTestLoader.loadTestsFromTestCase(tests.ReservedSourceDomainQueueTests);m.require(suite.countTestCases()==1,'Run only ONE new affected completed-domain boundary fixture')
    stream=io.StringIO();result=unittest.TextTestRunner(stream=stream,verbosity=2).run(suite);m.require(result.wasSuccessful(),stream.getvalue());m.require(c.source_files(m)==current,'Production changed during the new affected fixture')
    report={'schemaVersion':1,'status':'PASS_SOURCE_AND_TARGETED_FIXTURES_ONLY','kind':'NATIVE_CERTIFICATE_CONSUMER_SOURCE_UPGRADE','sourceFiles':current,'oldSourceFiles':OLD,'actualFailedRun':failed_ref,'actualFailedLog':log_ref,'oldArchivedSources':archived,'acceptedV13Proof':prior_ref,'committedV13SourceTransition':transition_ref,'actualReadOnlyDiagnostic':diagnostic_ref,'sourceDraft':draft_ref,'failureEvidenceNature':'ACTUAL_NATURAL_FAILED_NATIVE_RESUME','naturalFailureClaimed':True,
            'delta':changes,'mandatoryFinalizationCheckpointAndFullValidatorAstUnchanged':True,'pendingNativePathAndFinalizingRestartUnchanged':True,'completedQueueRowsReset':0,'existingSourceZoneWriterAndJournalReused':True,'actualAtomicRepairRequiredBeforeUpgradePreview':True,
            'fixtures':{'run':result.testsRun,'failures':len(result.failures),'errors':len(result.errors),'output':stream.getvalue()},'fixtureSourceSha256':m.sha(tests.__file__),'proofSourceSha256':m.sha(__file__),'oldFixturesRepeated':False,'activeDatabaseOpened':False,'worldReads':0,'mapWrites':0,'builds':0,'minecraftRuns':0}
    m.atomic_json(output,report);print(report['status'],report['sourceFiles'])


if __name__=='__main__':main()
