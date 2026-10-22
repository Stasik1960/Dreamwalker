"""Synthetic atomic replacement interruption and rejection cases; no real maps."""
import importlib.util, json, tempfile
from pathlib import Path

spec=importlib.util.spec_from_file_location('dependency_commit',Path(__file__).with_name('commit_pilot_dependency_files.py'));c=importlib.util.module_from_spec(spec);spec.loader.exec_module(c)
root=c.R/'pilot/dependency-commit-fixtures';root.mkdir(parents=True,exist_ok=True)
result={'status':'PASS','cases':[],'realWorldWrites':0}

def prepare(name):
    base=Path(tempfile.mkdtemp(prefix=name+'-',dir=root));repair=base/'repair';stage=repair/'staged';stage.mkdir(parents=True)
    worlds=[base/'baseline',base/'candidate']
    for w in worlds:w.mkdir()
    rows=[]
    for i,w in enumerate([worlds[0],worlds[1],worlds[1]]):
        target=w/('file%d.dat'%i);staged=stage/('file%d.dat'%i)
        if i<2:target.write_bytes(('before%d'%i).encode())
        staged.write_bytes(('after%d'%i).encode());rows.append({'target':str(target),'staged':str(staged),'beforeSha256':c.sha(target),'afterSha256':c.sha(staged)})
    plan=repair/'file-plan.json';c.atomic(plan,{'status':'STAGED','files':rows})
    return repair,worlds,rows,plan

def must_fail(fn,reason):
    try:fn()
    except RuntimeError as error:result['cases'].append({'case':reason,'status':'PASS','rejected':str(error)})
    else:raise AssertionError('Missing rejection: '+reason)

repair,worlds,rows,plan=prepare('replace-interruption')
must_fail(lambda:c.commit(plan,c.sha(plan),repair,worlds,interrupt_after=0),'interruption_after_replace_before_journal_commit')
assert c.sha(Path(rows[0]['target']))==rows[0]['afterSha256']
assert all((w/c.MARKER).is_file() for w in worlds)
# Simulate a second crash in a controller-owned PREPARED temporary copy.
identity={'planSha256':c.sha(plan),'number':1,**rows[1]};c.atomic(repair/'commit/0001.json',{**identity,'phase':'PREPARED'})
Path(rows[1]['target']+'.harness-writing').write_bytes(b'partial')
receipt=c.commit(plan,c.sha(plan),repair,worlds)
assert receipt['status']=='FILES_COMMITTED_NEEDS_ADMISSION'
assert all(c.sha(Path(v['target']))==v['afterSha256'] and Path(v['staged']).is_file() for v in rows)
assert all((w/c.MARKER).is_file() for w in worlds)
assert c.commit(plan,c.sha(plan),repair,worlds)==receipt
result['cases'].append({'case':'resume_partial_copy_new_file_and_repeated_commit','status':'PASS'})

repair,worlds,rows,plan=prepare('unexpected-later-file')
Path(rows[-1]['target']).write_bytes(b'external gameplay data')
must_fail(lambda:c.commit(plan,c.sha(plan),repair,worlds),'unexpected_later_target_rejected_before_first_write')
assert c.sha(Path(rows[0]['target']))==rows[0]['beforeSha256']

repair,worlds,rows,plan=prepare('outside-target')
document=c.read(plan);document['files'][0]['target']=str(repair.parent/'outside.dat');c.atomic(plan,document)
must_fail(lambda:c.commit(plan,c.sha(plan),repair,worlds),'target_outside_bound_worlds')

repair,worlds,rows,plan=prepare('changed-stage')
Path(rows[0]['staged']).write_bytes(b'changed')
must_fail(lambda:c.commit(plan,c.sha(plan),repair,worlds),'changed_staged_file')

repair,worlds,rows,plan=prepare('wrong-plan')
must_fail(lambda:c.commit(plan,'0'*64,repair,worlds),'unreviewed_file_plan_hash')

c.atomic(root/'receipt.json',result);print(json.dumps(result))
