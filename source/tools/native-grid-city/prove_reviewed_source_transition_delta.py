"""ONE V15 fixture and exact delta from the actual failed V14 admission.

The native failure remains V13/133636. V14's read-only admission failure and
its committed bootstrap repair are separate, hash-bound evidence. No project
database is opened and no earlier affected fixture is rerun.
"""
import ast
import io
import unittest
from pathlib import Path
import migrate as m
import native_certificates as c
import test_reviewed_source_transition as tests


ADMITTED={'migrate':'38848f33d11c739a049bf0f2a28f5bea784dc5cb90a274e20a46b4cd70ac860a','nativeResume':'476879e9e4ce62c4d968dca6ca2d25acf83e6a69a4d6c40c7277598737ec5a1c','nativeCertificates':'72947b20a3e6c869c42c0f53518ee645e9f9ba700ea3777b5095e29c038ad738'}
BASE={'migrate':'c723e946f7d95e7edd4423c3f32e96c67a0af1c0f649b5e5f7ce90aef915c15f','nativeResume':'3f06112ec958bdeeeb1d981530f2928459910ce319b8c150d76c5e723cbc6639','nativeCertificates':'cfc8d38aaa00964391e002a46da8f8952aa9966de5a367d9e849c5fb36d8a3d7'}
FILES={'migrate.py':'migrate','native_resume.py':'nativeResume','native_certificates.py':'nativeCertificates'}
CONTEXT_SHA='f04362e1021287ac16f613d85ff85033d92f5b315c4bb71112df45b3f09b6d45'
DRAFT_SHA='08012ececd81b47b702fc585f7d13cfaaa5e2c98fec0cbb4129b7a467fe008c2'


def dump(node):return ast.dump(node,include_attributes=False)
def functions(tree):return {node.name:node for node in tree.body if isinstance(node,ast.FunctionDef)}


def reviewed_sources(archived,text):
    """Apply only the reviewed two-file diff to strings, never source files."""
    expected={name:Path(row['path']).read_text(encoding='utf-8-sig') for name,row in archived.items()};active=None;hunk=[];seen=set()
    def flush():
        if not hunk:return
        while hunk and hunk[0] in ('',' '):hunk.pop(0)
        while hunk and hunk[-1] in ('',' '):hunk.pop()
        if not hunk:return
        old=[];new=[]
        for line in hunk:
            m.require(line=='' or line[0] in (' ','+','-'),'Unknown reviewed source hunk')
            if not line:old.append('');new.append('')
            else:
                if line[0] in (' ','-'):old.append(line[1:])
                if line[0] in (' ','+'):new.append(line[1:])
        before='\n'.join(old)+'\n';after='\n'.join(new)+'\n'
        m.require(active in expected and before.strip() and expected[active].count(before)==1,'Reviewed hunk must uniquely match the actual archived V14 source')
        expected[active]=expected[active].replace(before,after,1);hunk.clear()
    for line in text.splitlines():
        if line.startswith('*** Update File: '):
            flush();filename=Path(line.split(': ',1)[1]).name;m.require(filename in ('native_resume.py','native_certificates.py'),'Only the reviewed admission/validation sources may change');active=FILES[filename];seen.add(active)
        elif line=='@@':flush()
        elif line.startswith('*** '):flush()
        else:m.require(active is not None,'Reviewed patch lacks a source');hunk.append(line)
    flush();m.require(seen=={'nativeResume','nativeCertificates'},'Review must bind exactly the two changed sources');return expected


def main():
    directory=m.DEFAULT_PLAN/'full';output=directory/'reviewed-source-transition-source-upgrade-proof.v15.json';m.require(not output.exists(),'Keep every prior source/fixture proof immutable')
    prior_ref={'path':str(directory/'reserved-source-domain-queue-source-upgrade-proof.v14.json'),'sha256':'bac5d5e7e03581ab0550dea9eacd575140c54b4755fd0009d3909b5299b64691'}
    prior=c.bound(m,prior_ref);m.require(prior['status']=='PASS_SOURCE_AND_TARGETED_FIXTURES_ONLY' and prior['kind']=='NATIVE_CERTIFICATE_CONSUMER_SOURCE_UPGRADE' and prior['sourceFiles']==BASE and prior['oldSourceFiles']==ADMITTED and prior['fixtures']['run']==1,'Accepted V14 affected source proof changed')
    original_failure=c.bound(m,prior['actualFailedRun']);m.require(original_failure['status']=='FAIL' and original_failure['exitCode']==1 and original_failure['args']==['--plan','../research/native-grid-plan','resume-native-compose'] and Path(prior['actualFailedRun']['path']).name=='20261023T133636-migrate.json','The original failed native resume must stay V13/133636')
    for key,row in prior['oldArchivedSources'].items():m.require(row['sha256']==ADMITTED[key] and m.sha(row['path'])==ADMITTED[key],'The original admitted V13 source archive changed')
    admission_ref={'path':str(m.ROOT/'research/processes/20261023T155413-migrate.json'),'sha256':'33aab8b199f463de127b5ad76a13cfae2cc6213bf100e1cbfb1468e23bc06531'}
    admission=c.bound(m,admission_ref);arguments=admission['args'];m.require(admission['status']=='FAIL' and admission['exitCode']==1 and '--execute' not in arguments and 'upgrade-native-resume-binding' in arguments and prior_ref['sha256'] in arguments and '../research/processes/20261023T133636-migrate.json' in arguments,'Bind the actual failed read-only V14 admission separately')
    admission_log={'path':admission['log'],'sha256':m.sha(admission['log'])};m.require('Source-upgrade ancestor used another unreviewed consumer' in Path(admission_log['path']).read_text(encoding='utf-8'),'Actual source-admission failure reason changed')
    actual_inputs={Path(row['archive']).name:row for row in admission['inputSources'].values()};baseline={}
    for filename,name in FILES.items():
        row=actual_inputs[filename];m.require(row['sha256']==BASE[name] and m.sha(row['archive'])==BASE[name],'The actual failed V14 admission archive changed');baseline[name]={'path':row['archive'],'sha256':BASE[name]}
    context=actual_inputs['context_stage.py'];m.require(context['sha256']==CONTEXT_SHA and m.sha(context['archive'])==CONTEXT_SHA and m.sha(Path(m.__file__).with_name('context_stage.py'))==CONTEXT_SHA,'Accepted source/context semantics changed')
    repair_ref={'path':str(directory/'reserved-source-domain-queue-repair.v14.json'),'sha256':'0cf9833a22c9d198864360d2e1bbe2a672e7adef399b04d00a752e042287427b'}
    repair=c.bound(m,repair_ref);m.require(repair['status']=='PASS' and repair['kind']=='ATOMIC_RESERVED_SOURCE_DOMAIN_ORIGINAL_SECTION_REPAIR' and repair['sourceFiles']==BASE and repair['sourceProof']==prior_ref and repair['actualFailedRun']==prior['actualFailedRun'],'Committed bootstrap repair belongs to another source or failure')
    m.require(repair['previousAdmittedSourceBinding']['sourceFiles']==ADMITTED and repair['beforeCursor']==repair['afterCursor'] and repair['fullValidationBeforeCommit'] is True and repair['processedRowsReset']==0 and repair['mapWrites']==0,'Bootstrap repair changed the admitted cursor or lacked full precommit validation')
    observed_ref={'path':str(m.ROOT/'research/processes/20261023T155400-read_native_progress.log'),'sha256':'c4a0e027fc0d80398e8f2f3c2679e3cc6d09726d63b3c363a0176e2d1210aaa6'}
    observed=c.bound(m,observed_ref);m.require(observed['status']=='OBSERVATION_ONLY' and observed['databaseWrites']==0 and observed['pendingNativeCellExists'] is False and observed['meta']['nativeComposeCursor']==repair['afterCursor'] and observed['meta']['sourceLocalRetentionJournal']==repair['afterJournalHead'],'Independent committed progress does not bind this bootstrap event')
    m.require(repair['afterJournalHead']['events']==187 and repair['afterJournalHead']['head']=='d4dac0cc701ec609178a1710b6486d505eaf7e245b6b675c9f75b547485dca13','The committed bootstrap journal head changed')
    draft_ref={'path':str(m.ROOT/'research/source-drafts/reviewed-bootstrap-source-admission.v15.apply-patch.txt'),'sha256':DRAFT_SHA};m.require(m.sha(draft_ref['path'])==DRAFT_SHA,'Reviewed V15 source delta changed')
    expected=reviewed_sources(baseline,Path(draft_ref['path']).read_text(encoding='utf-8'));current=c.source_files(m);changes={}
    for filename,name in FILES.items():
        before=ast.parse(Path(baseline[name]['path']).read_text(encoding='utf-8-sig'));after=ast.parse(Path(m.__file__).with_name(filename).read_text(encoding='utf-8-sig'));m.require(dump(after)==dump(ast.parse(expected[name])),'Current source differs from the exact reviewed V15 patch: '+filename)
        old,new=functions(before),functions(after);added=set(new)-set(old);changed={key for key in old if key in new and dump(old[key])!=dump(new[key])}
        wanted=({'require_source_files','upgrade_preview','validate_source_visual_reservations'},{'reviewed_upgrade_source_files'}) if name=='nativeCertificates' else ({'validate_resume'},set()) if name=='nativeResume' else (set(),set())
        m.require((changed,added)==wanted and not(set(old)-set(new)),'An unrelated writer/guard/validator changed');changes[filename]={'changedFunctions':sorted(changed),'addedFunctions':sorted(added),'allOtherAstUnchanged':True}
        if name=='nativeResume':m.require(dump(old['checkpoint'])==dump(new['checkpoint']),'Mandatory finalization checkpoint changed')
    # The helper must accept the exact real V14 tuple before any synthetic
    # test runs. Its refs are direct, bound, and anchored to the V13 failure.
    reviewed={'sourceFiles':current,'oldSourceFiles':ADMITTED,'actualFailedRun':prior['actualFailedRun'],'reviewedIntermediateSourceProofs':[prior_ref]}
    m.require(c.reviewed_upgrade_source_files(m,reviewed,ADMITTED,prior['actualFailedRun'])==[ADMITTED,current,BASE],'The reviewed V14 bootstrap tuple is not admitted exactly')
    suite=unittest.defaultTestLoader.loadTestsFromTestCase(tests.ReviewedSourceTransitionTests);m.require(suite.countTestCases()==1,'Run only ONE new reviewed-transition/invocation fixture');stream=io.StringIO();result=unittest.TextTestRunner(stream=stream,verbosity=2).run(suite);m.require(result.wasSuccessful(),stream.getvalue());m.require(c.source_files(m)==current,'Production changed during the affected fixture')
    report={'schemaVersion':1,'status':'PASS_SOURCE_AND_TARGETED_FIXTURES_ONLY','kind':'NATIVE_CERTIFICATE_CONSUMER_SOURCE_UPGRADE','sourceFiles':current,'oldSourceFiles':ADMITTED,'actualFailedRun':prior['actualFailedRun'],'oldArchivedSources':prior['oldArchivedSources'],'reviewedIntermediateSourceProofs':[prior_ref],'actualSourceAdmissionFailure':admission_ref,'actualSourceAdmissionFailureLog':admission_log,'sourceReviewBaselineArchivedSources':baseline,'committedAtomicRepair':repair_ref,'independentCommittedJournalObservation':observed_ref,'acceptedV13Proof':prior['acceptedV13Proof'],'committedV13SourceTransition':prior['committedV13SourceTransition'],'sourceDraft':draft_ref,'failureEvidenceNature':'ACTUAL_NATURAL_FAILED_NATIVE_RESUME','admissionFailureEvidenceNature':'ACTUAL_READ_ONLY_SOURCE_UPGRADE_FAILURE','naturalFailureClaimed':True,
            'delta':changes,'directIntermediateSourceProofsOnly':True,'legacyTwoAndCurrentThreeKeyTransitionsPreserved':True,'mandatoryFullOriginalValidationOncePerInvocation':True,'allPostValidationContactAndJournalGuardsPreserved':True,'persistentValidationCacheAdded':False,
            'fixtures':{'run':result.testsRun,'failures':len(result.failures),'errors':len(result.errors),'output':stream.getvalue()},'fixtureSourceSha256':m.sha(tests.__file__),'proofSourceSha256':m.sha(__file__),'oldFixturesRepeated':False,'activeDatabaseOpened':False,'worldReads':0,'mapWrites':0,'builds':0,'minecraftRuns':0}
    m.atomic_json(output,report);print(report['status'],report['sourceFiles'])


if __name__=='__main__':main()
