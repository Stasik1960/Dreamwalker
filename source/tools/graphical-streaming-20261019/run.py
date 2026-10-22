import argparse,hashlib,json,os,shutil,subprocess,sys,time,traceback,uuid,zipfile
from common import *

def prepare(case,jar,qa=None,clientProperties=(),clientHeap='5G',serverHeap='9G',validationTickLimit=None):
    qa=qa or OUT/'dreamwalker-native-route-qa.jar'
    case.mkdir(exist_ok=False);(case/'client').mkdir();(case/'server').mkdir()
    rows=manifest(SOURCE)
    if (OUT/'source-map.json').exists():assert rows==json.loads((OUT/'source-map.json').read_text(encoding='utf-8'))
    else:write(OUT/'source-map.json',rows)
    WORLD.mkdir(parents=True,exist_ok=True)
    assert WORLD.resolve()==WORLD.absolute() and WORLD.is_relative_to(PROJECT/'build/runtime-server-rp-streaming7')
    for p in [WORLD,*WORLD.parents]:ordinary(p)
    expected={r['path'] for r in rows}
    for p in files(WORLD):
        if p.relative_to(WORLD).as_posix() not in expected:
            assert p.resolve().is_relative_to(WORLD.resolve());ordinary(p).unlink()
    for row in rows:
        dest=WORLD/row['path'];source=SOURCE/row['path']
        if dest.exists():ordinary(dest)
        if not dest.exists() or sha(dest)!=row['sha256']:
            dest.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(source,dest)
        assert sha(dest)==row['sha256']
    write(case/'world-input.json',{'source':str(SOURCE),'workingWorld':str(WORLD),'fileCount':len(rows),'manifestSha256':hashlib.sha256(json.dumps(rows,sort_keys=True).encode()).hexdigest()})
    mods=[]
    for side,run in [('client',CLIENT),('server',SERVER)]:
        run.mkdir(parents=True,exist_ok=True)
        for p in [run,*run.parents]:ordinary(p)
        assert run.resolve()==run.absolute()
        moddir=run/'mods';moddir.mkdir(parents=True,exist_ok=True)
        if not (OUT/(side+'-mod-input.json')).exists():
            source_rows=[]
            for p in ordinary(KIT/(side+'-mods')).glob('*.jar'):
                ordinary(p)
                with zipfile.ZipFile(p) as archive:
                    metadata=json.loads(archive.read('fabric.mod.json')) if 'fabric.mod.json' in archive.namelist() else {}
                before=sha(p)
                if metadata.get('id') not in ['bloodborne_dw','bloodborne_complete']:
                    shutil.copyfile(p,moddir/p.name);assert sha(moddir/p.name)==before and sha(p)==before
                    source_rows.append({'side':side,'source':str(p),'name':p.name,'sha256':before,'modId':metadata.get('id')})
            write(OUT/(side+'-mod-input.json'),source_rows)
        else:
            for m in json.loads((OUT/(side+'-mod-input.json')).read_text(encoding='utf-8')):assert sha(moddir/m['name'])==m['sha256']
        for p in ordinary(moddir).glob('dreamwalker-bb-*.jar'):ordinary(p).unlink()
        shutil.copyfile(ordinary(jar),moddir/jar.name)
        shutil.copyfile(qa,moddir/'dreamwalker-native-route-qa.jar')
        for p in moddir.glob('*.jar'):mods.append({'side':side,'name':p.name,'sha256':sha(p)})
    old=json.loads((ROOT/'deliverables/collision-streaming-20261018/evidence/shapes-after-prepared.json').read_text())
    graphics=dict(old['graphics']);graphics.update(renderDistance='8',simulationDistance='6',maxFps='120',enableVsync='false',pauseOnLostFocus='false',skipMultiplayerWarning='true',joinedFirstServer='true')
    (CLIENT/'options.txt').write_text('\n'.join(k+':'+v for k,v in graphics.items())+'\n',encoding='utf-8')
    (CLIENT/'screenshots').mkdir(exist_ok=True)
    assert not list((CLIENT/'screenshots').glob('route-*.png')),'Archive previous owned screenshots first'
    cp=old['command'][old['command'].index('-cp')+1]
    client_args=[s for s in old['command'][1:] if not s.startswith(('-XX:StartFlightRecording=','-XX:FlightRecorderOptions=','-Xmx','-javaagent:'))]
    client_args[client_args.index('--gameDir')+1]=str(CLIENT)
    client_args[client_args.index('--quickPlaySingleplayer')]='--quickPlayMultiplayer';client_args[client_args.index('--quickPlayMultiplayer')+1]='127.0.0.1:25571'
    client_args=['-Xms128M','-Xmx'+clientHeap,'-XX:FlightRecorderOptions=stackdepth=128','-Ddw.route.output='+str(case/'client'),*clientProperties,*client_args]
    servercp=';'.join([p for p in cp.split(';') if not Path(p).name.startswith('minecraft-client')]+[r'C:\Users\vakir\.gradle\caches\fabric-loom\1.20.1\minecraft-extracted_server.jar'])
    server_args=['-Xms128M','-Xmx'+serverHeap,'-XX:FlightRecorderOptions=stackdepth=128','-Ddw.route.output='+str(case/'server'),'-cp',servercp,'net.fabricmc.loader.impl.launch.knot.KnotServer','--universe',str(SERVER),'--world','bloodborne','nogui']
    argsfile(CLIENT/'native.args',client_args);argsfile(SERVER/'native.args',server_args)
    props=(ROOT/'deliverables/streaming-6-20261019/server/server.properties').read_text(encoding='utf-8')
    settings={'server-ip':'127.0.0.1','server-port':'25571','online-mode':'false','allow-flight':'true','spawn-protection':'0','level-name':'bloodborne'}
    if validationTickLimit is not None:settings['max-tick-time']=str(validationTickLimit)
    lines=props.splitlines()
    for key,value in settings.items():
        if any(s.startswith(key+'=') for s in lines):lines=[key+'='+value if s.startswith(key+'=') else s for s in lines]
        else:lines.append(key+'='+value)
    (SERVER/'server.properties').write_text('\n'.join(lines)+'\n',encoding='utf-8');(SERVER/'eula.txt').write_text('eula=true\n')
    offline=bytearray(hashlib.md5(b'OfflinePlayer:DreamwalkerQA').digest());offline[6]=(offline[6]&15)|48;offline[8]=(offline[8]&63)|128
    write(SERVER/'ops.json',[{'uuid':str(uuid.UUID(bytes=bytes(offline))),'name':'DreamwalkerQA','level':4,'bypassesPlayerLimit':True}])
    prepared={'status':'PREPARED','case':case.name,'java':str(JAVA),'jar':str(jar),'jarSha256':sha(jar),'qaSha256':sha(qa),'clientArgs':client_args,'serverArgs':server_args,'graphics':graphics,'optionsSha256':sha(CLIENT/'options.txt'),'serverPropertiesSha256':sha(SERVER/'server.properties'),'mods':mods}
    write(case/'prepared.json',prepared);return prepared

def main():
    ap=argparse.ArgumentParser();ap.add_argument('--case',choices=['baseline','after','validation-tail'],required=True);ap.add_argument('--jar',type=Path,required=True);a=ap.parse_args()
    wait_foreign();case=OUT/a.case
    prepared=prepare(case,a.jar.absolute(),OUT/'dreamwalker-native-tail-validation.jar',('-Ddw.route.validationTail=true',),clientHeap='4G',serverHeap='9G',validationTickLimit=180000) if a.case=='validation-tail' else prepare(case,a.jar.absolute())
    wait_foreign()
    sampler=Sampler();server=client=None;logs=[];started=time.time()
    try:
        serverlog=(case/'server/console.log').open('w',encoding='utf-8');logs.append(serverlog)
        server=subprocess.Popen([str(JAVA),'@'+str(SERVER/'native.args')],cwd=SERVER,stdin=subprocess.PIPE,stdout=serverlog,stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW)
        prepared.update(status='SERVER_STARTING',serverPid=server.pid);write(case/'prepared.json',prepared)
        with (case/'memory.jsonl').open('w') as memory:
            while True:
                if server.poll() is not None:raise RuntimeError('Dedicated server exited during startup')
                memory.write(json.dumps(sampler.sample([('server',server.pid)]))+'\n');memory.flush()
                if 'Done (' in (case/'server/console.log').read_text(encoding='utf-8',errors='replace'):break
                if time.time()-started>240:raise RuntimeError('Dedicated server startup deadline')
                time.sleep(.5)
            wait_foreign([server.pid])
            clientlog=(case/'client/console.log').open('w',encoding='utf-8');logs.append(clientlog)
            client=subprocess.Popen([str(JAVA),'@'+str(CLIENT/'native.args')],cwd=CLIENT,stdin=subprocess.DEVNULL,stdout=clientlog,stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW)
            prepared.update(status='NATIVE_CLIENT_RUNNING',clientPid=client.pid);write(case/'prepared.json',prepared)
            while client.poll() is None:
                memory.write(json.dumps(sampler.sample([('server',server.pid),('client',client.pid)]))+'\n');memory.flush()
                if server.poll() is not None:raise RuntimeError('Server exited while native client was running')
                if time.time()-started>1200:raise RuntimeError('Native route deadline; preserve partial evidence')
                time.sleep(.5)
            report=json.loads((case/'client/client-route.json').read_text(encoding='utf-8'))
            assert client.returncode==0 and report['status']=='PASS_NATIVE_GRAPHICAL_ROUTE',report
            while server.poll() is None:
                memory.write(json.dumps(sampler.sample([('server',server.pid)]))+'\n');memory.flush()
                if time.time()-started>1320:raise RuntimeError('Server unload/save deadline')
                time.sleep(.5)
        assert server.returncode==0 and json.loads((case/'server/server-route.json').read_text(encoding='utf-8'))['status']=='PASS_SAVED'
        (case/'screenshots').mkdir()
        for path in ordinary(CLIENT/'screenshots').glob('route-*.png'):shutil.move(str(ordinary(path)),case/'screenshots'/path.name)
        assert len(list((case/'screenshots').glob('*.png')))==(2 if a.case=='validation-tail' else 3)
        prepared.update(status='PASS_NATIVE_GRAPHICAL_DEDICATED_ROUTE',clientExit=client.returncode,serverExit=server.returncode,wallSeconds=time.time()-started);write(case/'prepared.json',prepared)
    except BaseException:
        prepared.update(status='FAIL',error=traceback.format_exc());write(case/'prepared.json',prepared);raise
    finally:
        # Only child process handles created by this driver are ever stopped.
        if server and server.poll() is None:
            try:server.stdin.write(b'stop\n');server.stdin.flush();server.wait(timeout=45)
            except Exception:server.kill();server.wait(timeout=15)
        if client and client.poll() is None:
            # An unresponsive owned client has no local world; keep its partial
            # evidence. Never enumerate another Java process for termination.
            client.kill();client.wait(timeout=15)
        sampler.close()
        for log in logs:log.close()

if __name__=='__main__':
    try:main()
    except BaseException:
        OUT.mkdir(parents=True,exist_ok=True);(OUT/'run-error.txt').write_text(traceback.format_exc(),encoding='utf-8');raise
