import argparse, hashlib, json, os, pathlib, queue, shutil, subprocess, threading, time, zipfile
ROOT=pathlib.Path(__file__).parent
INSTALL=ROOT/'forge-server'
JAVA='java'
DOWNLOADS=ROOT/'inputs'
VANILLA_SERVER=ROOT/'vanilla-1.20.1-server.jar'

def run(name,gecko=True,mapfile=None,commands=None,reuse=False,timeout=110,no_mod=False,vanilla120=False):
    no_mod=no_mod or vanilla120
    target=(ROOT/name).resolve()
    if target.parent != ROOT.resolve():
        raise ValueError('Test name must resolve to one direct child of work-root')
    target.mkdir(exist_ok=reuse)
    scenario={'minecraft':'1.20.1' if vanilla120 else '1.18.2','bloodborne':not no_mod,'gecko':gecko and not no_mod}
    inputs={} if no_mod else {'bloodborne.jar':'Bloodborne_X_Minecraft_mod_6.0 (1).jar'}
    if gecko and not no_mod:inputs['geckolib.jar']='geckolib-forge-1.18-3.0.57 (1).jar'
    scenario['mod_sha256']={name:hashlib.sha256((DOWNLOADS/source).read_bytes()).hexdigest() for name,source in inputs.items()}
    scenario_file=target/'scenario.json'
    if reuse:
        if not scenario_file.exists() or json.loads(scenario_file.read_text(encoding='utf-8'))!=scenario:
            raise ValueError('Reuse requires the same recorded Minecraft/mod/Gecko scenario')
        expected=set(scenario['mod_sha256'])
        existing={p.name for p in (target/'mods').glob('*.jar')}
        if existing!=expected:raise ValueError('Existing mods do not match the recorded scenario')
        if any(hashlib.sha256((target/'mods'/name).read_bytes()).hexdigest()!=digest for name,digest in scenario['mod_sha256'].items()):
            raise ValueError('Existing mod hashes do not match the recorded scenario')
        if mapfile:raise ValueError('--reuse cannot also import another map')
    else:scenario_file.write_text(json.dumps(scenario,indent=2),encoding='utf-8')
    if not vanilla120 and not (target/'libraries').exists():
        def copy_library(source,dest):
            try:os.link(source,dest)
            except OSError:shutil.copy2(source,dest)
            return dest
        shutil.copytree(INSTALL/'libraries',target/'libraries',copy_function=copy_library)
    (target/'mods').mkdir(exist_ok=True)
    if not no_mod:shutil.copy2(DOWNLOADS/'Bloodborne_X_Minecraft_mod_6.0 (1).jar',target/'mods'/'bloodborne.jar')
    if gecko and not no_mod:shutil.copy2(DOWNLOADS/'geckolib-forge-1.18-3.0.57 (1).jar',target/'mods'/'geckolib.jar')
    (target/'eula.txt').write_text('eula=true\n',encoding='ascii')
    (target/'server.properties').write_text('server-ip=127.0.0.1\nserver-port=25587\nonline-mode=true\nenable-query=false\nenable-rcon=false\nmax-players=1\nview-distance=2\nsimulation-distance=2\nlevel-name=world\nlevel-type=minecraft:flat\ngenerate-structures=false\nspawn-protection=16\nallow-flight=false\nenable-command-block=false\n',encoding='ascii')
    if mapfile and not reuse:
        with zipfile.ZipFile(mapfile) as z:
            dat=next(n for n in z.namelist() if n.endswith('/level.dat') or n=='level.dat')
            prefix=dat[:-len('level.dat')]
            for n in z.namelist():
                if not n.startswith(prefix) or n.endswith('/'):continue
                rel=pathlib.PurePosixPath(n[len(prefix):])
                if rel.is_absolute() or '..' in rel.parts or any(':' in part or '\\' in part for part in rel.parts):raise ValueError(n)
                dest=target/'world'/pathlib.Path(*rel.parts)
                if not dest.resolve().is_relative_to((target/'world').resolve()):raise ValueError(n)
                dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes(z.read(n))
    cmd=[str(JAVA),'-Xms256M','-Xmx2G']+(['-jar',str(VANILLA_SERVER)] if vanilla120 else ['@libraries/net/minecraftforge/forge/1.18.2-40.2.21/'+('win_args.txt' if os.name=='nt' else 'unix_args.txt')])+['nogui']
    start=time.monotonic(); p=subprocess.Popen(cmd,cwd=target,stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,encoding='utf-8',errors='replace',creationflags=(subprocess.CREATE_NO_WINDOW if os.name=='nt' else 0))
    q=queue.Queue(); lines=[];ready=False;sent=False;timedout=False
    def reader():
        for line in p.stdout:q.put(line)
    t=threading.Thread(target=reader,daemon=True);t.start()
    log=target/('restart.log' if reuse else 'console.log')
    with log.open('w',encoding='utf-8') as f:
        while p.poll() is None or not q.empty():
            try:line=q.get(timeout=.2)
            except queue.Empty:line=None
            if line:
                lines.append(line);f.write(line);f.flush()
                if 'Done (' in line and 'For help' in line:
                    ready=True
                    if not sent:
                        for c in (commands or ['list','save-all flush']):p.stdin.write(c+'\n');p.stdin.flush();time.sleep(.3)
                        p.stdin.write('stop\n');p.stdin.flush();sent=True
            if time.monotonic()-start > timeout:
                timedout=True
                try:p.stdin.write('stop\n');p.stdin.flush();p.wait(timeout=12)
                except Exception:p.kill();p.wait()
                break
        t.join(timeout=2)
        while not q.empty():line=q.get();lines.append(line);f.write(line)
    result={'name':name,'minecraft':'1.20.1' if vanilla120 else '1.18.2','gecko':gecko and not no_mod,'bloodborne':not no_mod,'map':str(mapfile) if mapfile else None,'command':cmd,'ready':ready,'exit_code':p.poll(),'timeout':timedout,'elapsed_seconds':round(time.monotonic()-start,2),'log':str(log),'commands':commands or ['list','save-all flush'],'diagnostics':[s.strip() for s in lines if any(k in s for k in ['ERROR','Exception','Caused by:','NoClassDefFoundError','Invalid dist','Mod ID:','Done (','Stopping server'])][-40:]}
    (target/('restart-result.json' if reuse else 'result.json')).write_text(json.dumps(result,indent=2),encoding='utf-8')
    print(json.dumps(result,ensure_ascii=False,indent=2),flush=True)
    return result

if __name__=='__main__':
    p=argparse.ArgumentParser()
    p.add_argument('--work-root',type=pathlib.Path,required=True)
    p.add_argument('--java',type=pathlib.Path,required=True)
    p.add_argument('--inputs',type=pathlib.Path,required=True)
    p.add_argument('--forge-install',type=pathlib.Path)
    p.add_argument('--vanilla-server',type=pathlib.Path)
    p.add_argument('name');p.add_argument('--no-gecko',action='store_true');p.add_argument('--no-mod',action='store_true');p.add_argument('--vanilla-120',action='store_true');p.add_argument('--map');p.add_argument('--reuse',action='store_true');args=p.parse_args()
    ROOT=args.work_root.resolve();ROOT.mkdir(parents=True,exist_ok=True)
    JAVA=args.java;DOWNLOADS=args.inputs
    INSTALL=args.forge_install if args.forge_install else ROOT/'forge-server'
    VANILLA_SERVER=args.vanilla_server if args.vanilla_server else ROOT/'vanilla-1.20.1-server.jar'
    if args.reuse and not (ROOT/args.name/'world/level.dat').exists():
        p.error('--reuse requires an existing isolated test world')
    run(args.name,not args.no_gecko,args.map,reuse=args.reuse,no_mod=args.no_mod or args.vanilla_120,vanilla120=args.vanilla_120)
