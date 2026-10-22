"""Root-only atomic file-plan commit; admission/light reset remain separate."""
import argparse, hashlib, json, os, shutil
from pathlib import Path

P=Path(__file__).resolve().parents[2];R=P.parent/'research'
REPAIR=R/'pilot/dependency-repair'
MARKER='NATIVE-GRID-HARNESS-REPAIR-INCOMPLETE.json'

def sha(path):
    if not path.exists():return None
    h=hashlib.sha256()
    with path.open('rb') as stream:
        for part in iter(lambda:stream.read(1024*1024),b''):h.update(part)
    return h.hexdigest()

def require(value,message):
    if not value:raise RuntimeError(message)

def read(path):return json.loads(path.read_text(encoding='utf-8-sig'))

def atomic(path,value):
    path.parent.mkdir(parents=True,exist_ok=True);temporary=path.with_name(path.name+'.writing')
    reject_reparse(temporary)
    with temporary.open('w',encoding='utf-8') as stream:
        json.dump(value,stream,ensure_ascii=False,indent=2);stream.flush();os.fsync(stream.fileno())
    os.replace(temporary,path)

def reject_reparse(path):
    # Inspect the literal path BEFORE resolve; never traverse a junction.
    for item in (path,*path.parents):
        if item.exists() or item.is_symlink():
            require(not (item.lstat().st_file_attributes & 0x400) if hasattr(item.lstat(),'st_file_attributes') else not item.is_symlink(),'Reparse point is not an allowed commit path: '+str(item))

def checked(path,parent):
    path=Path(path);require(path.is_absolute(),'Absolute file-plan paths required')
    reject_reparse(path);path=path.resolve()
    require(path.is_relative_to(parent.resolve()),'File-plan path escapes its bound directory')
    return path

def commit(plan_path,expected_sha,repair=REPAIR,worlds=None,interrupt_after=None):
    repair=repair.resolve();worlds=[p.resolve() for p in (worlds or [R/'pilot/baseline',R/'pilot/candidate'])]
    plan_path=checked(plan_path,repair);payload=plan_path.read_bytes();require(hashlib.sha256(payload).hexdigest()==expected_sha,'Exact reviewed file plan changed')
    plan=json.loads(payload.decode('utf-8-sig'));require(plan.get('status')=='STAGED' and plan.get('files'),'A nonempty STAGED file plan is required')
    stage=repair/'staged';records=[];seen=set()
    for row in plan['files']:
        raw=Path(row['target']);reject_reparse(raw);target=raw.resolve()
        require(raw.is_absolute() and any(target.is_relative_to(w) and target!=w for w in worlds),'Target escapes the two bound pilot worlds')
        require(target not in seen,'Duplicate file-plan target');seen.add(target)
        staged=checked(row['staged'],stage)
        require(staged.is_file() and sha(staged)==row['afterSha256'],'Staged file is absent or changed')
        before=row['beforeSha256'];after=row['afterSha256']
        require(before is None or isinstance(before,str) and len(before)==64,'Invalid before hash')
        require(isinstance(after,str) and len(after)==64,'Invalid after hash')
        records.append({'target':str(target),'staged':str(staged),'beforeSha256':before,'afterSha256':after})
    require(all(any(Path(row['target']).is_relative_to(w) for row in records) for w in worlds),'Both pilot worlds must be in the same repair plan')
    control=repair/'commit';control.mkdir(parents=True,exist_ok=True);reject_reparse(control)
    intent={'schemaVersion':1,'plan':str(plan_path),'planSha256':expected_sha,'controllerSha256':sha(Path(__file__)),'worlds':[str(w) for w in worlds],'files':records}
    intent_path=control/'intent.json'
    if intent_path.exists():require(read(intent_path)==intent,'A different file plan/controller cannot reuse this commit cursor')
    else:
        require(all(sha(Path(row['target']))==row['beforeSha256'] for row in records),'A target changed before the first repair write')
        require(not any((control/('%04d.json'%n)).exists() for n in range(len(records))),'Unowned file journals exist without an intent')
        atomic(intent_path,intent)
    # Validate the entire cursor before changing either world on every resume.
    for number,row in enumerate(records):
        target=Path(row['target']);journal=control/('%04d.json'%number);current=sha(target)
        identity={'planSha256':expected_sha,'number':number,**row}
        temporary=target.with_name(target.name+'.harness-writing');reject_reparse(temporary)
        if journal.exists():
            saved=read(journal);require(all(saved.get(k)==v for k,v in identity.items()),'File journal belongs to another operation')
            require(saved.get('phase') in ('PREPARED','COMMITTED'),'Unknown file journal phase')
            require(current==row['afterSha256'] or saved['phase']=='PREPARED' and current==row['beforeSha256'],'Unexpected target bytes in repair cursor')
        else:
            require(current==row['beforeSha256'] and not temporary.exists(),'Unexpected target or unowned temporary file before journal creation')
    for world in worlds:
        marker=world/MARKER;value={'status':'DO_NOT_OPEN','filePlanSha256':expected_sha,'intent':str(intent_path)}
        if marker.exists():require(read(marker)==value,'A different repair owns the pilot maintenance marker')
        else:atomic(marker,value)
    for number,row in enumerate(records):
        target=Path(row['target']);staged=Path(row['staged']);reject_reparse(target)
        journal=control/('%04d.json'%number);current=sha(target)
        identity={'planSha256':expected_sha,'number':number,**row}
        if journal.exists():
            saved=read(journal);require(all(saved.get(k)==v for k,v in identity.items()),'File journal belongs to another operation')
            require(saved.get('phase') in ('PREPARED','COMMITTED'),'Unknown file journal phase')
            if current==row['afterSha256']:
                atomic(journal,{**identity,'phase':'COMMITTED'});continue
            require(saved['phase']=='PREPARED' and current==row['beforeSha256'],'Committed or interrupted target has unexpected bytes')
        else:
            require(current==row['beforeSha256'],'Unjournalled target no longer has its exact before bytes')
            atomic(journal,{**identity,'phase':'PREPARED'})
        target.parent.mkdir(parents=True,exist_ok=True);temporary=target.with_name(target.name+'.harness-writing');reject_reparse(temporary)
        # A PREPARED journal owns this exact temporary path. Re-copying it also
        # recovers a process interruption halfway through the previous copy.
        with staged.open('rb') as source,temporary.open('wb') as out:
            shutil.copyfileobj(source,out,1024*1024);out.flush();os.fsync(out.fileno())
        require(sha(temporary)==row['afterSha256'] and sha(target)==row['beforeSha256'],'Staged/target bytes changed before atomic replacement')
        os.replace(temporary,target)
        if interrupt_after is not None and number==interrupt_after:raise RuntimeError('SYNTHETIC_INTERRUPTION_AFTER_REPLACE')
        require(sha(target)==row['afterSha256'],'Atomic target has unexpected after bytes')
        atomic(journal,{**identity,'phase':'COMMITTED'})
    require(all(sha(Path(row['target']))==row['afterSha256'] for row in records),'Final committed file bytes changed')
    receipt={'schemaVersion':1,'status':'FILES_COMMITTED_NEEDS_ADMISSION','filePlan':{'path':str(plan_path),'sha256':expected_sha},'intent':{'path':str(intent_path),'sha256':sha(intent_path)},'planSha256':expected_sha,'controllerSha256':intent['controllerSha256'],'intentSha256':sha(intent_path),'filesCommitted':len(records),'worlds':intent['worlds'],'maintenanceMarkersKept':True,'geometryOrLightReadinessClaimed':False}
    atomic(repair/'file-commit-receipt.json',receipt);return receipt

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--plan',type=Path,default=REPAIR/'file-plan.json');parser.add_argument('--plan-sha256',required=True);args=parser.parse_args()
    print(json.dumps(commit(args.plan,args.plan_sha256),ensure_ascii=False))
