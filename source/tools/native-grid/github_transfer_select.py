"""Select project evidence and actual launch/build dependencies; omit account stores."""
from pathlib import Path
import json,os,stat
P=Path(__file__).resolve().parents[2];ROOT=P.parent;R=ROOT/'research';O=R/'transfer'
U=Path(r'C:\Users\vakir');W=U/'.codex/worktrees/3f07';BASE=W/'DW-city-mesh-20261018';OLD=W/'DW-ordinary-city-pilot-20261020'
selected={};missing=[];excluded=[]
def add(path):
    path=Path(path)
    if not path.exists():missing.append(str(path));return
    assert path.is_relative_to(U),str(path)
    if path.is_file():selected[str(path)]=path;return
    for current,dirs,names in os.walk(path,followlinks=False):
        dirs[:]=[d for d in dirs if d not in {'.git','__pycache__'} and not (os.lstat(Path(current)/d).st_file_attributes & stat.FILE_ATTRIBUTE_REPARSE_POINT)]
        for name in names:
            q=Path(current)/name
            if q==O/'selection.json' or q==R/'processes/latest.json' or (q.parent==R/'processes' and 'github_transfer_select' in q.name):continue
            if os.lstat(q).st_file_attributes & stat.FILE_ATTRIBUTE_REPARSE_POINT:excluded.append({'path':str(q),'reason':'reparse-point'});continue
            if name.endswith(('.lock','-shm')) or name=='session.lock':continue
            selected[str(q)]=q
add(ROOT)
add(BASE/'deliverables/block-fixes-2-20261020')
# Baseline sources and exact exporter build outputs, without unrelated test worlds.
baseline=BASE/'bloodborne complete'
for q in baseline.iterdir():
    if q.name not in {'.git','build','.gradle','logs','run','runs'}:add(q)
for rel in ['build/classes','build/resources','build/libs','build/runtime-server-rp-streaming7/native.args','build/runtime-server-rp-streaming7/mods','build/runtime-client-rp-streaming7/mods','build/runtime-client-city-mesh-baseline/libraries']:
    add(baseline/rel)
prepared=BASE/'research/graphical-streaming-20261019/after/prepared.json';add(prepared)
add(OLD/'research/city-pilot/full-city-copy.ordinary-migration')
add(OLD/'tools')
add(W/'DW/audits/Dreamwalker-BB/2026-10-21-native-grid-city')
for name in ['storage-mode.json','C-return-result.txt','Guard.ps1','Job.cs']:add(U/'CodexControl'/name)
add(W/'DW/bloodborne complete/build/v127-dependencies/worldedit-mod-7.2.15.jar')
# Offline dependency artifacts; no launcher account databases or credentials.
for rel in ['.gradle/caches/modules-2','.gradle/caches/fabric-loom','.gradle/wrapper/dists']:
    add(U/rel)
java=U/'AppData/Roaming/.minecraft/runtime/java-runtime-gamma/windows/java-runtime-gamma';add(java)
args=[json.loads(line) for line in (baseline/'build/runtime-server-rp-streaming7/native.args').read_text(encoding='utf-8').splitlines() if line.strip()]
args+=json.loads(prepared.read_text(encoding='utf-8'))['clientArgs']
path_arguments=[]
for i,arg in enumerate(args):
    if i and args[i-1] in ['--gameDir','--universe','--world','--username','--accessToken']:continue
    value=arg.split('=',1)[1] if arg.startswith('-D') and '=' in arg else arg
    for item in value.split(';'):
        if len(item)>3 and item[1]==':' and item[2] in '\\/':
            path=Path(item)
            if path.exists():add(path);path_arguments.append(str(path))
# Persist MTIMEs because the immutable cached source binding checks them.
files=[]
for q in sorted(selected.values(),key=str):
    s=q.stat();files.append({'source':str(q),'path':q.relative_to(U).as_posix(),'bytes':s.st_size,'mtimeNs':s.st_mtime_ns})
report={'schemaVersion':1,'sourceUserRoot':str(U),'files':files,'logicalBytes':sum(x['bytes'] for x in files),'fileCount':len(files),'missing':sorted(set(missing)),'reparseExclusions':excluded,'launchDependencyPaths':sorted(set(path_arguments)),'credentialsIncluded':False}
(O/'selection.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps({k:v for k,v in report.items() if k!='files'},ensure_ascii=False))
