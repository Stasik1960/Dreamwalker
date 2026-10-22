"""Save a source snapshot and durable resume cursor; never copy or mutate worlds/DB."""
from pathlib import Path
import datetime, hashlib, json, os, stat, zipfile
P=Path(__file__).resolve().parents[2]
R=P.parent/'research'
stamp=datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%SZ')
out=R/'checkpoints'/stamp
out.mkdir(parents=True,exist_ok=False)
def sha(data):return hashlib.sha256(data).hexdigest()
def write(path,value):path.write_text(json.dumps(value,ensure_ascii=False,indent=2),encoding='utf-8')
files=[]
excluded={'.git','.gradle','build','logs','crash-reports','__pycache__','.idea'}
archive=out/'current-sources.zip'
with zipfile.ZipFile(archive,'w',compression=zipfile.ZIP_DEFLATED,compresslevel=1) as z:
    for current,dirs,names in os.walk(P,followlinks=False):
        dirs[:]=sorted(d for d in dirs if d not in excluded and not (os.lstat(Path(current)/d).st_file_attributes & stat.FILE_ATTRIBUTE_REPARSE_POINT))
        for name in sorted(names):
            path=Path(current)/name
            if os.lstat(path).st_file_attributes & stat.FILE_ATTRIBUTE_REPARSE_POINT:continue
            data=path.read_bytes();rel=path.relative_to(P).as_posix()
            z.writestr(rel,data);files.append({'path':rel,'bytes':len(data),'sha256':sha(data)})
write(out/'source-manifest.json',files)
bindings=[]
for rel in ['progress.json','CONTRACT-V1.txt','native-grid-plan/progress.json','native-grid-plan/relight-binding.json','native-grid-plan/context-extended-relight-engine.json','native-grid-plan/context-extended-relight-engine.json.done.tsv','pilot/candidate/NATIVE-GRID-MIGRATION.json','pilot/dependency-repair/receipt.json','native-grid-plan/context-extension/in-place-binding.json','native-grid-plan/context-extension/frozen-catalog.json','function-qa/candidate-actual-footprint/launch-receipt.json','function-qa/candidate-actual-footprint/functions.json','profiles/profile-lock.json','profiles/benchmark-helper.json']:
    path=R/rel
    if path.is_file():
        data=path.read_bytes();dest=out/'cursor'/rel;dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes(data)
        bindings.append({'path':str(path),'sha256':sha(data),'bytes':len(data)})
start=Path(r'C:\Users\vakir\.codex\worktrees\3f07\DW\audits\Dreamwalker-BB\2026-10-21-native-grid-city\START-RU.txt')
data=start.read_bytes();(out/'START-RU.txt').write_bytes(data)
db=R/'native-grid-plan/plan.sqlite'
receipt={'status':'SAVED_CHECKPOINT_NOT_FINAL_READY','utc':stamp,'source':str(P),'sourceArchive':str(archive),'sourceArchiveSha256':sha(archive.read_bytes()),'sourceFiles':len(files),'sourceLogicalBytes':sum(f['bytes'] for f in files),'sourceManifestSha256':sha((out/'source-manifest.json').read_bytes()),'startSha256':sha(data),'oneExistingPlan':{'path':str(db),'bytes':db.stat().st_size,'modifiedNs':db.stat().st_mtime_ns,'copied':False},'cursorBindings':bindings,'worldsCopied':0,'worldWrites':0,'databaseWrites':0,'resume':'Continue existing plan; reconcile preserved-assemblies before full city. Pilot native light PASS554/554. Actual AIR door place/open/collision/close PASS, bridge QA source/manual yaw assertion unresolved; all original FAIL reports retained. No matched client/server pair launched; await9GiB available RAM at pinned4G/3G heaps. Do not restart conversion, replay passed unrelated checks, or promote before actual pilot gate. Task continues automatically.'}
write(out/'receipt.json',receipt)
(out/'RESUME-RU.txt').write_text('Контрольный снимок сохранён; задача продолжается. Это не READY.\nИспользовать существующий research/native-grid-plan/plan.sqlite, не создавать новый план.\nПилот записан, свет 554/554, pending0. Дверь в AIR: установка/ПКМ/коллизия/закрытие PASS.\nОткрыто: условие QA угла вручную установленной двери на фоне; сверка ранее согласованных цельных сборок; клиент+отдельный сервер при свободных9ГиБ.\nЗатем полный город, новый полный свет, маршрут/save/reopen, итоговый комплект.\nПодробности и хэши в receipt.json и cursor/progress.json.\n',encoding='utf-8')
write(R/'checkpoints/latest.json',{'path':str(out),'receiptSha256':sha((out/'receipt.json').read_bytes()),'status':receipt['status']})
print(json.dumps({'status':receipt['status'],'path':str(out),'sourceFiles':len(files),'archiveBytes':archive.stat().st_size,'archiveSha256':receipt['sourceArchiveSha256']},ensure_ascii=False))
