"""Real client/dedicated-server route using the existing frozen recorder.
No functional QA helper, build or world conversion runs during measurement.
"""
import argparse,hashlib,json,math,os,re,shutil,subprocess,sys,time,traceback,zipfile
from pathlib import Path
P=Path(__file__).resolve().parents[2];T=P.parent.parent/'DW-visual-physics-20261024';O=P.parent.parent/'DW-userpack-optimization-20261024'
sys.path.insert(0,str(P/'tools/native-grid'))
import run_profiles as driver
def read(path):return json.loads(path.read_text(encoding='utf-8-sig'))
def write(path,value):path.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
def identity(path):
    with zipfile.ZipFile(path) as jar:return json.loads(jar.read('fabric.mod.json'))['id']
def reference(path):return {'path':str(path),'sha256':driver.sha(path)}
def main():
    parser=argparse.ArgumentParser();parser.add_argument('--case',choices=('smoke','candidate','baseline','full','reopen','addon','sun','centre'),required=True);parser.add_argument('--attempt',default='1');parser.add_argument('--addon',choices=('none','iris-off','sildur','makeup','makeup32'),default='none');parser.add_argument('--fps',type=int,choices=(60,260));parser.add_argument('--shadow-chunks',type=int,choices=range(2,33));a=parser.parse_args()
    case=a.case;label=case+('-'+a.addon if case in ('addon','sun','centre') else '')+'-'+a.attempt;out=T/'runs'/label;assert not out.exists();out.mkdir(parents=True);shutil.copy2(__file__,out/'runner-source.py')
    if case=='baseline':
        source=Path('C:/Users/vakir/Desktop/bloodborne-native-grid-city');production=P.parent/'research/runtime-history/production-lazy-frapi-9b2edaa041331ff510df87ad1abd51b1de9a535f5768396bc04b6b5cf24d7d33.jar'
        assert driver.sha(production)=='9b2edaa041331ff510df87ad1abd51b1de9a535f5768396bc04b6b5cf24d7d33'
    else:source=T/'world';production=T/'build/libs/dreamwalker-bb-fabric-1.20.1-0.1.0-v12.7.4-visual-physics.1.jar'
    if case=='reopen':
        saved=read(T/'runs/full-1/receipt.json');assert saved['status']=='PASS' and saved['productionSha256']==driver.sha(production)
        source=T/'route-worlds/full-1'
    if case=='sun':
        saved=read(T/'runs/full-2/receipt.json');assert saved['status']=='PASS' and saved['productionSha256']==driver.sha(production) and saved['addon']==a.addon
        source=T/'route-worlds/full-2'
    qa=O/'profiles/final/client/mods/dreamwalker-userpack-route-qa.jar';assert driver.sha(qa)=='6b88d3caafa05e48a6bc89dff7b973241ba8e50f3e4e9bbb704514d1e908645b'
    route=T/('route-'+case+('-'+str(a.attempt) if case=='sun' else '')+'.json')
    if not route.exists():
        if case=='smoke':
            cameras=[];probes=[];names=[]
            for i,(x,y,z) in enumerate(((160,48,-1017),(271,48,-950),(133,75,-962))):
                cx,cy,cz=x+5.5,y+4.0,z+6.5;dx,dz=x+.5-cx,z+.5-cz;dy=y+.1-(cy+1.62)
                cameras.append([cx,cy,cz,math.degrees(math.atan2(-dx,dz)),-math.degrees(math.atan2(dy,math.hypot(dx,dz)))]);probes.append([x,y,z]);names.append('paving-books-'+str(i+1))
            plan={'schemaVersion':1,'stationaryTicks':80,'cameras':cameras,'probes':probes,'names':names,'purpose':'Finite visual diagnosis of reported paving near books, carriages and horse carcasses'}
        elif case=='centre':
            plan=read(O/'route.json')
        elif case=='sun':
            assert a.addon in ('sildur','makeup','makeup32')
            existing=read(T/'route-full.json');at=existing['names'].index('paving-low-2');camera=existing['cameras'][at];probe=existing['probes'][at]
            plan={'schemaVersion':1,'stationaryTicks':400,'cameras':[camera,camera],'probes':[probe,probe],'names':['sun-morning','sun-afternoon'],'purpose':'Same book/horse paving view at day times3000 and9000; screenshots after400 settled ticks.'}
        elif case=='reopen':
            original=read(O/'route.json')
            plan={'schemaVersion':1,'stationaryTicks':80,'cameras':original['cameras'][:1],'probes':original['probes'][:1],'names':['cold-reopen-facade'],'purpose':'Cold JVM server/client reopening the normally saved full-route world; not another city sweep.'}
        elif case=='full':
            plan=read(O/'final-route.json');paving=read(T/'route-smoke.json')
            for i,(x,y,z) in enumerate(paving['probes']):
                plan['cameras'].append([x+.5,y+3,z+.5,0,90]);plan['probes'].append([x,y,z]);plan['names'].append('paving-overhead-'+str(i+1))
            # Low views of every nearby book placement, including the less
            # central piles, diagnose angle-dependent ground/overlay defects.
            for i,row in enumerate(read(T/'paving-probe.json')['nearHorses']):
                x,y,z=row['pos'];plan['cameras'].append([x+.5,y+.6,z+2.5,180,45]);plan['probes'].append([x,y,z]);plan['names'].append('paving-low-'+str(i+1))
        else:
            plan=read(O/'route.json')
            paving=read(T/'route-smoke.json')
            for i,(x,y,z) in reversed(list(enumerate(paving['probes']))):
                plan['cameras'].insert(1,[x+.5,y+3,z+.5,0,90]);plan['probes'].insert(1,[x,y,z]);plan['names'].insert(1,'paving-overhead-'+str(i+1))
            # Two distant districts add actual architecture/streaming evidence
            # to the existing repeated statue/tree witnesses, in the same pair.
            plan['cameras'].extend([[650,110,-150,43.23,35],[980,100,95,44.36,35]])
            plan['probes'].extend([[603,70,-100],[940,28,129]])
            plan['names'].extend(['distant-tower','distant-brick'])
        write(route,plan)
    plan=read(route)
    assert type(plan['stationaryTicks']) is int and 80<=plan['stationaryTicks']<=400,'Recorder stationaryTicks must be 80..400'
    assert 1<=len(plan['cameras'])<=128 and len(plan['probes'])==len(plan['names'])==len(plan['cameras'])
    addon_files={};shader_options={}
    if a.addon!='none':
        downloads=read(T/'addons/downloads.json');prior=P.parent/'research/user-modpack-validation/profiles/pack-b/client/mods'
        for kind in ('indium',):
            jar=next(prior.glob(kind+'-*.jar'));assert identity(jar)==kind;addon_files[kind]=jar
        sodium=Path(downloads['sodium']['path']);assert identity(sodium)=='sodium' and driver.sha(sodium)==downloads['sodium']['sha256'];addon_files['sodium']=sodium
        iris=Path(downloads['iris']['path']);assert identity(iris)=='iris' and driver.sha(iris)==downloads['iris']['sha256'];addon_files['iris']=iris
        shader_key={'sildur':'sildurFast','makeup':'makeupLow','makeup32':'makeupNear'}.get(a.addon)
        if shader_key:
            shader=Path(downloads[shader_key]['path']);assert driver.sha(shader)==downloads[shader_key]['sha256']
            shader_options=read(T/'addons/minimal-options.json')['makeupLow' if a.addon=='makeup32' else shader_key]
    world=T/'route-worlds'/label;assert not world.exists();shutil.copytree(source,world)
    profiles=T/'profiles'/label;mods={};shared={}
    for role in ('client','server'):
        profile=profiles/role;(profile/'mods').mkdir(parents=True)
        shared[role]={}
        for jar in Path('C:/Users/vakir/Desktop') .joinpath(role+'_mods').glob('*.jar'):
            if identity(jar)=='bloodborne_dw':continue
            target=profile/'mods'/jar.name;shutil.copy2(jar,target);assert driver.sha(target)==driver.sha(jar);shared[role][jar.name]=driver.sha(target)
        shutil.copy2(production,profile/'mods/dreamwalker-production.jar');shutil.copy2(qa,profile/'mods/dreamwalker-userpack-route-qa.jar')
        if role=='client' and case=='sun':
            probe=read(T/'addons/toggle-build.json');assert driver.sha(Path(probe['path']))==probe['sha256'];shutil.copy2(probe['path'],profile/'mods/shader-toggle-probe-qa.jar')
        assert not any(identity(p)=='dw_native_grid_function_qa' for p in (profile/'mods').glob('*.jar'))
        original=O/'profiles/final'/role
        for name in ('config','defaultconfigs'):
            if (original/name).is_dir():shutil.copytree(original/name,profile/name)
        for name in (('options.txt',) if role=='client' else ('server.properties','ops.json','eula.txt')):
            if (original/name).is_file():shutil.copy2(original/name,profile/name)
        if role=='client' and addon_files:
            for jar in addon_files.values():shutil.copy2(jar,profile/'mods'/jar.name)
            sodium=read(P.parent/'research/user-modpack-validation/profiles/pack-b/client/config/sodium-options.json')
            # Source models can protrude from their section. Direction-bucket
            # culling assumes vanilla unit cells and is unsafe for that art.
            sodium['performance']['use_block_face_culling']=False
            write(profile/'config/sodium-options.json',sodium)
            (profile/'config/iris.properties').write_text('enableShaders='+str(a.addon!='iris-off').lower()+'\ndisableUpdateMessage=true\ncolorSpace=SRGB\n'+('maxShadowRenderDistance='+str(a.shadow_chunks)+'\n' if a.shadow_chunks is not None else '')+('shaderPack='+shader.name+'\n' if shader_options else ''),encoding='utf-8')
            if shader_options:
                (profile/'shaderpacks').mkdir();shutil.copy2(shader,profile/'shaderpacks'/shader.name)
                (profile/'shaderpacks'/(shader.name+'.txt')).write_text('\n'.join(k+'='+str(v).lower() for k,v in sorted(shader_options.items()))+'\n',encoding='utf-8')
        mods[role]={p.name:driver.sha(p) for p in (profile/'mods').glob('*.jar')}
    options=profiles/'client/options.txt';values=dict(line.split(':',1) for line in options.read_text(encoding='utf-8-sig').splitlines() if ':' in line)
    fps_limit=a.fps if a.fps is not None else 260 if case in ('baseline','candidate','addon','centre') else 60
    values.update({'maxFps':str(fps_limit),'enableVsync':'false','renderDistance':'6','simulationDistance':'5','graphicsMode':'1','ao':'true','pauseOnLostFocus':'false'})
    options.write_text('\n'.join(k+':'+v for k,v in values.items())+'\n',encoding='utf-8')
    properties=profiles/'server/server.properties';props=dict(line.split('=',1) for line in properties.read_text(encoding='utf-8-sig').splitlines() if '=' in line and not line.startswith('#'))
    props.update({'server-ip':'127.0.0.1','server-port':'25579','online-mode':'false','enable-rcon':'false','view-distance':'6','simulation-distance':'4'})
    properties.write_text('\n'.join(k+'='+v for k,v in props.items())+'\n',encoding='utf-8')
    free=driver.available()/2**30;commit=driver.available_commit()/2**30
    assert free>=7.5 and commit>=9,'Memory admission before two JVMs: need7.5GiB physical/9GiB commit, actual '+str((free,commit))
    for role in ('server','client'):
        prior=[json.loads(line) for line in (O/'actual-final'/(role+'.args')).read_text(encoding='utf-8-sig').splitlines() if line.strip()]
        args=driver.filtered(prior,profiles/role,'4G',role,out/role,world,route)
        if role=='client' and case=='sun':args[:0]=['-Ddw.shader.toggle.output='+str(out/'shader-toggle.json'),'-Ddw.shader.toggle.trigger='+str(profiles/'client/screenshots/route-sun-morning.png')]
        args=[arg for arg in args if not arg.startswith('-XX:ActiveProcessorCount=')];args.insert(0,'-XX:ActiveProcessorCount=16');driver.argfile(out/(role+'.args'),args)
    binding={'variant':case,'case':case,'productionSha256':driver.sha(production),'qaSha256':driver.sha(qa),'profileLockSha256':driver.sha(Path('C:/Users/vakir/Desktop/client_mods/DREAMWALKER-MODPACK-LOCK.json')),'routeSha256':driver.sha(route),'world':str(world),'source':str(source),'preparedMods':mods,'sharedMods':shared,'initialAvailableGiB':free,'initialAvailableCommitGiB':commit,'backend':'fabric-indigo','hostLogicalProcessors':16,'heapLimits':{'client':'4G','server':'4G'},'graphics':{'maxFps':fps_limit,'vsync':False,'width':1280,'height':720,'viewDistance':6,'simulationDistance':4,'fancy':True,'ao':True},'functionalQaExcluded':True}
    binding['graphics'].update({'clientSimulationDistanceOption':5,'serverSimulationDistance':4,'fpsOption260MeansUnlimited':fps_limit==260})
    binding['runnerSha256']=driver.sha(out/'runner-source.py')
    if addon_files:binding.update({'backend':'sodium-indium-iris','addon':a.addon,'shaderOptions':shader_options,'addonFiles':{key:reference(path) for key,path in addon_files.items()},'sodiumBlockFaceCulling':False})
    if shader_options:binding['shader']=reference(shader)
    if a.shadow_chunks is not None:binding['irisShadowDistanceChunks']=a.shadow_chunks
    write(out/'binding.json',binding);server=client=sampler=None;started=time.monotonic();sun_events=[]
    try:
        with (out/'server-console.log').open('w',encoding='utf-8') as slog,(out/'client-console.log').open('w',encoding='utf-8') as clog,(out/'sampler.log').open('w',encoding='utf-8') as hlog:
            server=subprocess.Popen([str(driver.JAVA),'@'+str(out/'server.args')],cwd=profiles/'server',stdin=subprocess.PIPE,stdout=slog,stderr=subprocess.STDOUT,text=True,creationflags=subprocess.CREATE_NO_WINDOW)
            write(out/'pids.json',{'server':server.pid,'client':None});deadline=time.monotonic()+600
            while 'Done (' not in (out/'server-console.log').read_text(encoding='utf-8',errors='replace'):
                driver.reject_runtime_failure(out);assert server.poll() is None,'Server exited before startup';assert time.monotonic()<deadline,'Server startup deadline';time.sleep(1)
            server.stdin.write('op DreamwalkerQA\n');server.stdin.flush()
            client=subprocess.Popen([str(driver.JAVA),'@'+str(out/'client.args')],cwd=profiles/'client',stdout=clog,stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW)
            write(out/'pids.json',{'server':server.pid,'client':client.pid})
            sampler=subprocess.Popen([sys.executable,'-B',str(O/'sample_host_resources.py'),'--pids',str(out/'pids.json'),'--out',str(out/'host-samples'),'--stop-file',str(out/'sampler-stop')],cwd=O,stdout=hlog,stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW)
            deadline=time.monotonic()+(5400 if case=='full' else 2400)
            while client.poll() is None:
                driver.reject_runtime_failure(out);assert server.poll() is None,'Server ended before client route';assert time.monotonic()<deadline,'Client route deadline';time.sleep(1)
                if case=='sun':
                    try:progress=read(out/'client/client-route.json')
                    except (FileNotFoundError,json.JSONDecodeError):continue
                    initial=not sun_events and progress.get('initialPositioningUtc')
                    afternoon=len(sun_events)==1 and (profiles/'client/screenshots/route-sun-morning.png').exists()
                    if initial or afternoon:
                        day=3000 if initial else 9000;server.stdin.write('time set '+str(day)+'\n');server.stdin.flush();sun_events.append({'dayTime':day,'runnerElapsedSeconds':time.monotonic()-started});write(out/'sun-commands.json',sun_events)
            assert client.returncode==0,'Client exited with '+str(client.returncode)
            deadline=time.monotonic()+150
            while server.poll() is None:
                driver.reject_runtime_failure(out);assert time.monotonic()<deadline,'Normal server save deadline';time.sleep(1)
            c=read(out/'client/client-route.json');s=read(out/'server/server-route.json')
            assert server.returncode==0 and c['status']=='PASS_NATIVE_GRAPHICAL_ROUTE' and s['status']=='PASS_SAVED'
            console=(out/'client-console.log').read_text(encoding='utf-8',errors='replace')
            if addon_files:assert all(re.search(r'^\s*- '+kind+r'\s',console,re.MULTILINE) for kind in ('sodium','iris','indium')),'Required renderer IDs missing from Fabric loaded-mod report'
            else:assert '[Indigo] Registering Indigo renderer!' in console
            if case=='sun':
                assert len(sun_events)==2,'Both sun positions must have been applied';binding['sunTimeCommands']=sun_events
                toggle=read(out/'shader-toggle.json');assert toggle['status']=='PASS_SHADER_ON_OFF_ON';binding['shaderToggle']=toggle
            write(out/'receipt.json',{**binding,'status':'PASS','clientExitCode':client.returncode,'serverExitCode':server.returncode,'seconds':time.monotonic()-started})
    except BaseException as failure:
        write(out/'receipt.json',{**binding,'status':'FAIL','error':str(failure),'seconds':time.monotonic()-started});(out/'runner-error.txt').write_text(traceback.format_exc(),encoding='utf-8');raise
    finally:
        (out/'sampler-stop').touch()
        if client is not None and client.poll() is None:client.terminate();client.wait(timeout=20)
        if server is not None and server.poll() is None:
            try:server.stdin.write('stop\n');server.stdin.flush();server.wait(timeout=60)
            except BaseException:server.terminate();server.wait(timeout=20)
        if sampler is not None:
            try:sampler.wait(timeout=10)
            except subprocess.TimeoutExpired:sampler.terminate();sampler.wait(timeout=10)
if __name__=='__main__':
    try:main()
    except BaseException:
        (T/'route-runner-last-error.txt').write_text(traceback.format_exc(),encoding='utf-8');raise
