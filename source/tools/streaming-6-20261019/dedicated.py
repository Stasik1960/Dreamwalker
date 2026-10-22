"""One dedicated pair in the existing single QA working save. Guard launches this.

The delivered cleaned map and independent backup are never written. Resetting
the owned QA save uses ordinary file operations only, with no reparse traversal.
"""
import argparse, ctypes, hashlib, json, os, shutil, subprocess, sys, time
from ctypes import wintypes
from pathlib import Path

PROJECT=Path(__file__).absolute().parents[2]
ROOT=PROJECT.parent
OUT=ROOT/'research/dedicated-streaming-20261019'
RUN=PROJECT/'build/runtime-server-streaming6'
WORLD=PROJECT/'build/runtime-client-materials/saves/prototype-fixture'
SOURCE=ROOT/'deliverables/collision-streaming-20261018/map/bloodborne'
JAVA=Path(r'C:\Program Files\BellSoft\LibericaJRE-21-Full\bin\java.exe')
sys.path.insert(0,str(PROJECT/'tools/collision-streaming-20261018'))
from prepare_route import active_files, ordinary, sha

def write(path,value):
    path.write_text(json.dumps(value,indent=2)+'\n',encoding='utf-8')

def manifest(folder):
    return [{'path':p.relative_to(folder).as_posix(),'bytes':p.stat().st_size,'sha256':sha(p)} for p in active_files(folder)]

def reset(case):
    for parent in [WORLD,*WORLD.parents]:
        ordinary(parent)
    assert WORLD.resolve()==WORLD.absolute() and WORLD.is_relative_to(PROJECT/'build/runtime-client-materials')
    rows=manifest(SOURCE)
    if (OUT/'source-map.json').exists():
        assert rows==json.loads((OUT/'source-map.json').read_text()),'Immutable delivered map changed'
    else:
        write(OUT/'source-map.json',rows)
    expected={r['path'] for r in rows}
    removed=[]
    for path in active_files(WORLD):
        relative=path.relative_to(WORLD).as_posix()
        if relative not in expected:
            assert path.resolve().is_relative_to(WORLD.resolve())
            ordinary(path).unlink();removed.append(relative)
    replaced=0
    for row in rows:
        target=WORLD/row['path'];source=SOURCE/row['path']
        assert target.resolve().is_relative_to(WORLD.resolve())
        if target.exists():ordinary(target)
        if not target.exists() or sha(target)!=row['sha256']:
            target.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(source,target);replaced+=1
        assert sha(target)==row['sha256']
    write(case/'reset.json',{'status':'PASS_EXACT_SAME_WORKING_SAVE_RESET','source':str(SOURCE),'target':str(WORLD),
        'manifestSha256':hashlib.sha256(json.dumps(rows,sort_keys=True).encode()).hexdigest(),'files':len(rows),'replaced':replaced,'removedExtras':removed})

class Memory(ctypes.Structure):
    _fields_=[('cb',wintypes.DWORD),('PageFaultCount',wintypes.DWORD),('PeakWorkingSetSize',ctypes.c_size_t),('WorkingSetSize',ctypes.c_size_t),('QuotaPeakPagedPoolUsage',ctypes.c_size_t),('QuotaPagedPoolUsage',ctypes.c_size_t),('QuotaPeakNonPagedPoolUsage',ctypes.c_size_t),('QuotaNonPagedPoolUsage',ctypes.c_size_t),('PagefileUsage',ctypes.c_size_t),('PeakPagefileUsage',ctypes.c_size_t),('PrivateUsage',ctypes.c_size_t)]

def main():
    ap=argparse.ArgumentParser();ap.add_argument('--case',required=True,choices=['baseline','after']);ap.add_argument('--jar',required=True,type=Path);a=ap.parse_args()
    OUT.mkdir(exist_ok=True,parents=True);case=OUT/a.case;case.mkdir(exist_ok=False)
    reset(case)
    RUN.mkdir(exist_ok=True);mods=RUN/'mods';mods.mkdir(exist_ok=True)
    for path in ordinary(mods).glob('dreamwalker-*.jar'):ordinary(path).unlink()
    for path in (PROJECT/'build/runtime-client-materials/mods').glob('*.jar'):
        if path.name.startswith(('fabric-api-','geckolib-','mclib-')):shutil.copyfile(ordinary(path),mods/path.name)
    jar=ordinary(a.jar.absolute());shutil.copyfile(jar,mods/jar.name)
    qa=next(p for p in (PROJECT/'build/libs').glob('dreamwalker-streaming-qa-*.jar') if not p.name.endswith('-dev.jar'))
    assert not qa.name.endswith('-dev.jar')
    frozen=OUT/'dreamwalker-streaming-qa.jar'
    if frozen.exists():assert sha(frozen)==sha(qa),'QA addon changed between cases'
    else:shutil.copyfile(qa,frozen)
    shutil.copyfile(frozen,mods/frozen.name)
    properties=Path(r'C:\Users\vakir\Desktop\server.properties').read_text()
    def setting(key,value):
        nonlocal properties
        lines=properties.splitlines();lines=[key+'='+value if line.startswith(key+'=') else line for line in lines]
        if not any(line.startswith(key+'=') for line in lines):lines.append(key+'='+value)
        properties='\n'.join(lines)+'\n'
    # Production pair settings plus loopback-only QA authentication/port.
    for key,value in {'view-distance':'8','simulation-distance':'6','server-ip':'127.0.0.1','server-port':'25569','online-mode':'false','level-name':WORLD.name,'spawn-protection':'0','allow-flight':'true'}.items():setting(key,value)
    (RUN/'server.properties').write_text(properties,encoding='utf-8');(RUN/'eula.txt').write_text('eula=true\n')
    old=json.loads((ROOT/'deliverables/collision-streaming-20261018/evidence/shapes-after-prepared.json').read_text())['command']
    cp=old[old.index('-cp')+1].split(';');cp=[p for p in cp if not Path(p).name.startswith('minecraft-client')]
    cp.append(r'C:\Users\vakir\.gradle\caches\fabric-loom\1.20.1\minecraft-extracted_server.jar')
    args=['-Xms128M','-Xmx9G','-XX:FlightRecorderOptions=stackdepth=128','-XX:NativeMemoryTracking=summary',
          '-Ddw.qa.output='+str(case),'-cp',';'.join(cp),'net.fabricmc.loader.impl.launch.knot.KnotServer',
          '--universe',str(WORLD.parent),'--world',WORLD.name,'nogui']
    argfile=RUN/'java.args';argfile.write_text('\n'.join('"'+s.replace('\\','/').replace('"','\\"')+'"' for s in args)+'\n')
    prep={'status':'PREPARED','java':str(JAVA),'args':args,'jarSha256':sha(jar),'qaSha256':sha(frozen),
          'mods':[{'name':p.name,'sha256':sha(p)} for p in sorted(mods.glob('*.jar'))],
          'serverPropertiesSha256':sha(RUN/'server.properties'),'desktopStartSha256':sha(Path(r'C:\Users\vakir\Desktop\start.sh')),
          'desktopPropertiesSha256':sha(Path(r'C:\Users\vakir\Desktop\server.properties')),'workingWorld':str(WORLD)}
    write(case/'prepared.json',prep)
    kernel=ctypes.WinDLL('kernel32',use_last_error=True);kernel.OpenProcess.argtypes=[wintypes.DWORD,wintypes.BOOL,wintypes.DWORD];kernel.OpenProcess.restype=wintypes.HANDLE
    psapi=ctypes.WinDLL('psapi',use_last_error=True);psapi.GetProcessMemoryInfo.argtypes=[wintypes.HANDLE,ctypes.c_void_p,wintypes.DWORD]
    started=time.time()
    with (case/'server-console.log').open('w',encoding='utf-8') as log,(case/'memory.jsonl').open('w') as memory:
        child=subprocess.Popen([str(JAVA),'@'+str(argfile)],cwd=RUN,stdin=subprocess.PIPE,stdout=log,stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW)
        prep.update(status='RUNNING',pid=child.pid);write(case/'prepared.json',prep)
        handle=kernel.OpenProcess(0x0410,False,child.pid)
        try:
            while child.poll() is None:
                m=Memory();m.cb=ctypes.sizeof(m)
                if psapi.GetProcessMemoryInfo(handle,ctypes.byref(m),ctypes.sizeof(m)):
                    memory.write(json.dumps({'utc':time.time(),'workingSet':m.WorkingSetSize,'peakWorkingSet':m.PeakWorkingSetSize,'privateBytes':m.PrivateUsage})+'\n');memory.flush()
                if time.time()-started>480:
                    child.stdin.write(b'stop\n');child.stdin.flush()
                    try:child.wait(timeout=40)
                    except subprocess.TimeoutExpired:child.kill()
                    raise RuntimeError('Dedicated QA deadline: preserve partial evidence')
                time.sleep(.5)
        finally:kernel.CloseHandle(handle)
    prep.update(status='EXITED',exitCode=child.returncode,seconds=time.time()-started);write(case/'prepared.json',prep)
    route=json.loads((case/'route.json').read_text())
    assert child.returncode==0 and route['status']=='PASS_ROUTE_NORMAL_SAVE',route
    prep['status']='PASS_NATIVE_DEDICATED_ROUTE';write(case/'prepared.json',prep)

if __name__=='__main__':main()
