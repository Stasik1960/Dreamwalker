"""Prove only the new original-section delta and run its one affected fixture.

Root executes this through Guard. Actual failed-source archives and the accepted
v6 proof are inputs; no real plan, world, build or Minecraft process is opened.
"""
import ast
import copy
import io
import unittest
from pathlib import Path
import migrate as m
import native_certificates as c
import test_original_section_fallback as tests

OLD = {'migrate':'667498d986941b4ac6128ad40238e6e1953ff34345ae3466a3dfddb4e7328e99',
       'nativeResume':'3a3f3f7b1fecef1320da43114ccfba459ab4b7d8e90487fc19f0002c8ea26ff1',
       'nativeCertificates':'edd0e2e07869ff3f35458b49a9152bb40e1684eb49a0225b4d61b81791cdab88'}
NEW = {'migrate':'056efec209523e3a48d6e66dd3082300c843ea1666c008555d2a2ad20a23781c',
       'nativeResume':OLD['nativeResume'],
       'nativeCertificates':'eac031fad7d2202b80a33f3a2a36e00209e5f2a94737849af28bae7a676880c9'}
TOP_CHANGED = {'validate_source_visual_reservations','check_retained_native_member',
               'original_native_cell','validate_source_local_retentions'}
METHOD_CHANGED = {'original_domain','clearance'}
METHOD_ADDED = {'original_zones','zone_snapshot'}


def dump(node): return ast.dump(node, include_attributes=False)
def functions(module): return {row.name:row for row in module.body if isinstance(row,ast.FunctionDef)}
def expression(code): return ast.parse(code,mode='eval').body


def main():
    directory = m.DEFAULT_PLAN/'full'
    output = directory/'original-section-fallback-source-upgrade-proof.v7.json'
    m.require(not output.exists(), 'Preserve the first new-section source proof')
    prior_ref = {'path':str(directory/'common-original-local-fallback-source-upgrade-proof.v6.json'),
                 'sha256':'43290bc55242471c541a6a4f8d257db29419571077082580c5678a4c56678e72'}
    prior = c.bound(m,prior_ref)
    m.require(prior['status']=='PASS_SOURCE_AND_TARGETED_FIXTURES_ONLY' and prior['sourceFiles']==OLD
              and prior['fixtures']['run']==1, 'Accepted v6 source/fixture binding changed')
    m.require(c.source_files(m)==NEW, 'Production source changed after the reviewed zone freeze')
    archive = m.ROOT/'research/processes/20261023T101344-migrate.inputs'
    archived, changes = {}, {}
    for filename,name in (('migrate.py','migrate'),('native_resume.py','nativeResume'),('native_certificates.py','nativeCertificates')):
        path = archive/filename
        m.require(m.sha(path)==OLD[name], 'Actual 101344 source archive changed: '+filename)
        archived[name] = {'path':str(path),'sha256':OLD[name]}
        old = ast.parse(path.read_text(encoding='utf-8-sig'))
        current = ast.parse(Path(m.__file__).with_name(filename).read_text(encoding='utf-8-sig'))
        normalized = copy.deepcopy(current)
        if filename=='migrate.py':
            assignments = [node for node in normalized.body if isinstance(node,ast.Assign)
                           and any(isinstance(target,ast.Name) and target.id=='SCHEMA' for target in node.targets)]
            m.require(len(assignments)==1 and isinstance(assignments[0].value,ast.Constant), 'Unknown migration schema assignment')
            for line in ('CREATE INDEX IF NOT EXISTS substrate_owner ON substrate_fragments(dim,uuid);\n',
                         'CREATE INDEX IF NOT EXISTS native_visual_owner ON native_visual_fragments(dim,rx,ry,rz);\n'):
                m.require(assignments[0].value.value.count(line)==1, 'Only the two specified derived indexes may be added')
                assignments[0].value.value = assignments[0].value.value.replace(line,'')
            tail = functions(normalized)['compose_native_tail']
            calls = [node for node in ast.walk(tail) if isinstance(node,ast.Call)
                     and isinstance(node.func,ast.Attribute) and node.func.attr=='automatic_source_zone_retention']
            m.require(len(calls)==1, 'Only one existing failed-batch callback may switch to the zone handler')
            calls[0].func.attr = 'automatic_direct_native_retention'
            condition = dump(expression('visual_reservations.original_zones()')); removed=0
            for node in ast.walk(tail):
                if isinstance(node,ast.BoolOp):
                    keep=[value for value in node.values if dump(value)!=condition]
                    removed += len(node.values)-len(keep); node.values=keep
            m.require(removed==1, 'Only one zone-membership check may join existing native guarding')
            changes[filename]={'derivedIndexes':['substrate_owner','native_visual_owner'],
                               'onlyExistingCatchCallbackAndZoneGuardChanged':True}
        elif filename=='native_certificates.py':
            before,after=functions(old),functions(current)
            added=set(after)-set(before); removed=set(before)-set(after)
            changed={name for name in before.keys() & after.keys() if dump(before[name])!=dump(after[name])}
            m.require(added=={'automatic_source_zone_retention'} and not removed and changed==TOP_CHANGED,
                      'Unexpected certificate writer/validator changes outside the new zone integration')
            m.require(dump(before['automatic_direct_native_retention'])==dump(after['automatic_direct_native_retention']),
                      'Previously accepted precise writer must remain AST-identical')
            normalized.body=[node for node in normalized.body if not (isinstance(node,ast.FunctionDef) and node.name in added)]
            normalized.body=[copy.deepcopy(before[node.name]) if isinstance(node,ast.FunctionDef) and node.name in TOP_CHANGED else node for node in normalized.body]
            old_class=next(node for node in old.body if isinstance(node,ast.ClassDef) and node.name=='SourceVisualReservations')
            current_class=next(node for node in current.body if isinstance(node,ast.ClassDef) and node.name=='SourceVisualReservations')
            old_methods,new_methods=functions(old_class),functions(current_class)
            changed_methods={name for name in old_methods.keys() & new_methods.keys() if dump(old_methods[name])!=dump(new_methods[name])}
            m.require(set(new_methods)-set(old_methods)==METHOD_ADDED and not(set(old_methods)-set(new_methods))
                      and changed_methods==METHOD_CHANGED, 'Unrelated source-domain methods changed')
            original=new_methods['original_domain']
            m.require(original.args.args[-1].arg=='unreplaced' and dump(original.args.defaults[-1])==dump(expression('True')),
                      'Existing original-domain callers must keep strict default behavior')
            target=next(node for node in normalized.body if isinstance(node,ast.ClassDef) and node.name=='SourceVisualReservations')
            target.body=[copy.deepcopy(old_methods[node.name]) if isinstance(node,ast.FunctionDef) and node.name in METHOD_CHANGED else node
                         for node in target.body if not(isinstance(node,ast.FunctionDef) and node.name in METHOD_ADDED)]
            changes[filename]={'newFunctions':sorted(added),'necessaryValidatorIntegrations':sorted(changed),
                               'newMethods':sorted(METHOD_ADDED),'changedMethods':sorted(changed_methods),
                               'oldPreciseWriterAstUnchanged':True}
        m.require(dump(old)==dump(normalized), 'Unrelated source/native/visual/owner/queue/journal algorithm changed: '+filename)
    failure=m.ROOT/'research/processes/20261023T101344-migrate.json'
    m.require(m.sha(failure)=='a7168233a9a814071570d40e9828a1dd6c387f67c1bc0b759120d175bf807637'
              and m.read(failure)['status']=='FAIL', 'Actual failed 101344 resume receipt changed')
    suite=unittest.defaultTestLoader.loadTestsFromTestCase(tests.OriginalSectionFallbackTests)
    m.require(suite.countTestCases()==1, 'Run exactly the single new affected zone fixture')
    stream=io.StringIO(); result=unittest.TextTestRunner(stream=stream,verbosity=2).run(suite)
    m.require(result.wasSuccessful(),stream.getvalue())
    m.require(c.source_files(m)==NEW, 'Production sources changed during the affected fixture')
    report={'schemaVersion':1,'status':'PASS_SOURCE_AND_TARGETED_FIXTURES_ONLY',
            'kind':'NATIVE_CERTIFICATE_CONSUMER_SOURCE_UPGRADE','sourceFiles':NEW,'oldSourceFiles':OLD,
            'actualFailedRun':{'path':str(failure),'sha256':m.sha(failure)},'oldArchivedSources':archived,
            'priorCommonSourceProof':prior_ref,'delta':changes,
            'fixtures':{'run':result.testsRun,'failures':len(result.failures),'errors':len(result.errors),'output':stream.getvalue()},
            'fixtureSourceSha256':m.sha(tests.__file__),'proofSourceSha256':m.sha(__file__),
            'oldFixturesRepeated':False,'activeDatabaseOpened':False,'worldReads':0,'mapWrites':0,'builds':0,'minecraftRuns':0}
    m.atomic_json(output,report); print(report['status'],report['sourceFiles'])


if __name__=='__main__': main()
