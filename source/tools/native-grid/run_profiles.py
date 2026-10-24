"""Pinned real client + separate local server. Root invokes under Guard.

No copying/migration occurs here. Target must already be coherently written and lit.
"""
import argparse,ctypes,hashlib,json,os,re,shutil,subprocess,time,uuid,zipfile
from pathlib import Path
from qa.client_runtime_lineage import read_lineage
P=Path(__file__).resolve().parents[2];ROOT=P.parent
BASE=Path(r'C:\Users\vakir\.codex\worktrees\3f07\DW-city-mesh-20261018')
PROFILES=ROOT/'research/profiles'
JAVA=Path(r'C:\Users\vakir\AppData\Roaming\.minecraft\runtime\java-runtime-gamma\windows\java-runtime-gamma\bin\java.exe')
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def write(path,data):path.write_text(json.dumps(data,ensure_ascii=False,indent=2),encoding='utf-8')
def argfile(path,args):path.write_text('\n'.join('"'+s.replace('\\','/').replace('"','\\"')+'"' for s in args)+'\n',encoding='utf-8')
class Memory(ctypes.Structure):
    _fields_=[('length',ctypes.c_ulong),('load',ctypes.c_ulong),('total',ctypes.c_ulonglong),('available',ctypes.c_ulonglong),('totalPage',ctypes.c_ulonglong),('availablePage',ctypes.c_ulonglong),('virtual',ctypes.c_ulonglong),('availableVirtual',ctypes.c_ulonglong),('extended',ctypes.c_ulonglong)]
def available():
    m=Memory();m.length=ctypes.sizeof(m);ctypes.windll.kernel32.GlobalMemoryStatusEx(ctypes.byref(m));return m.available
def available_commit():
    m=Memory();m.length=ctypes.sizeof(m);ctypes.windll.kernel32.GlobalMemoryStatusEx(ctypes.byref(m));return m.availablePage
def reject_runtime_failure(out):
    for role in ('server','client'):
        path=out/(role+'-console.log')
        if not path.is_file():continue
        console=path.read_text(encoding='utf-8',errors='replace')
        for error in ('OutOfMemoryError','Failed to save','Exception stopping the server'):
            if error in console:raise RuntimeError(role+' reported '+error)
def functional_renderer(lock, indigo):
    rows=sorted(({key:row[key] for key in ('file','id','sha256')} for row in lock['mods']['client']
                 if row['variant']=='candidate' and row['id'] in ('sodium','indium')),key=lambda row:row['file'])
    assert len(rows)==2 and {row['id'] for row in rows}=={'sodium','indium'},'Frozen candidate renderer mod pair differs'
    mods=PROFILES/'candidate/client/mods';disabled=PROFILES/'candidate/disabled-functional-indigo'
    moves=[]
    for row in rows:
        assert Path(row['file']).name==row['file'],'Unsafe frozen renderer filename'
        live=mods/row['file'];held=disabled/row['file']
        assert live.is_file()!=held.is_file(),'Renderer mod must exist in exactly one known location: '+row['file']
        current=live if live.is_file() else held
        assert sha(current)==row['sha256'],'Frozen renderer mod SHA differs: '+row['file']
        target=held if indigo else live
        if current!=target:moves.append((current,target))
    if indigo:disabled.mkdir(parents=True,exist_ok=True)
    for current,target in moves:
        assert not target.exists(),'Renderer move would overwrite a file'
        current.rename(target)
    for row in rows:
        live=mods/row['file'];held=disabled/row['file']
        expected,absent=(held,live) if indigo else (live,held)
        assert expected.is_file() and sha(expected)==row['sha256'] and not absent.exists(),'Renderer configuration did not preserve the exact frozen mod'
    return {'backend':'fabric-indigo','excludedClientMods':rows} if indigo else None
def filtered(prior,profile,heap,role,out,world,route):
    args=[];i=0
    while i<len(prior):
        s=prior[i]
        if s.startswith(('-Ddw.','-Xms','-Xmx','-XX:StartFlightRecording','-XX:FlightRecorderOptions','-Dfabric.modsFolder=')):i+=1;continue
        if s in ('--server','--port','--quickPlaySingleplayer','--quickPlayMultiplayer','--quickPlayRealms'):i+=2;continue
        replacements={'--gameDir':str(profile),'--username':'DreamwalkerQA','--accessToken':'0','--width':'1280','--height':'720','--universe':str(world.parent),'--world':world.name}
        if s in replacements:args.extend([s,replacements[s]]);i+=2;continue
        args.append(s);i+=1
    args=['-Xms128M','-Xmx'+heap,'-XX:ActiveProcessorCount=2','-Dfabric.modsFolder='+str(profile/'mods'),*(['-Ddw.route.output='+str(out),'-Ddw.route.plan='+str(route)] if route else []),*args]
    if role=='client':args.extend(['--quickPlayMultiplayer','127.0.0.1:25579'])
    return args
def main():
    ap=argparse.ArgumentParser();ap.add_argument('--variant',choices=['baseline','candidate'],required=True);ap.add_argument('--production',type=Path,required=True);ap.add_argument('--world',type=Path,required=True);ap.add_argument('--route',type=Path,required=True);ap.add_argument('--out',type=Path,required=True);ap.add_argument('--timeout',type=int,default=1800)
    ap.add_argument('--functional-memory-reserve-gib',type=float,choices=[7.0],help='Saved FULL final route/reopen only; base4G/3G heaps and recorder remain pinned, plus9GiB available commit')
    ap.add_argument('--functional-client-heap',choices=['4G'],help='Explicit FULL functional observation only; requires memory override and client-only lineage, never a matched pair')
    ap.add_argument('--functional-renderer',choices=['indigo'],help='Saved FULL functional route/reopen only; temporarily retain frozen Sodium/Indium outside candidate client mods')
    ap.add_argument('--client-runtime-lineage',type=Path);ap.add_argument('--client-runtime-lineage-sha256')
    a=ap.parse_args()
    world=a.world.resolve();out=a.out.resolve();route=a.route.resolve();prod=a.production.resolve()
    assert out.is_relative_to(ROOT) and world.is_relative_to(ROOT) and (world/'level.dat').is_file()
    assert not (world/'NATIVE-GRID-INCOMPLETE.json').exists(),'Offline migration/light debt is incomplete; gameplay launch refused'
    assert (a.client_runtime_lineage is None)==(a.client_runtime_lineage_sha256 is None),'Client lineage requires its paired explicit SHA'
    lineage_ref=None;lineage=None;production_sha=sha(prod);evidence_runtime=production_sha
    if a.client_runtime_lineage is not None:
        assert a.variant=='candidate' and route in {(ROOT/'research/final-route/route.json').resolve(),(ROOT/'research/final-route/reopen-route.json').resolve()},'Client-only lineage is limited to the actual FULL final/reopen routes'
        lineage_ref={'path':str(a.client_runtime_lineage.resolve(strict=True)),'sha256':a.client_runtime_lineage_sha256}
        lineage=read_lineage(lineage_ref,{'path':str(prod),'sha256':production_sha},P/'src/architecture/java/dev/dreamwalker/bloodbornedw/composite/NativeGridClient.java')
        evidence_runtime=lineage['predecessor']['sha256']
    if a.variant=='baseline':assert sha(prod)=='40cecfb7e5cf783a762e350fc1864518c39abff177f6f025cb94892a181fbf51'
    else:
        marker=json.loads((world/'NATIVE-GRID-MIGRATION.json').read_text(encoding='utf-8'))
        assert marker['status']=='READY_FOR_GAMEPLAY_QA' and marker['runtime']['sha256']==evidence_runtime
        with zipfile.ZipFile(prod) as jar:assert hashlib.sha256(jar.read('assets/bloodborne_dw/native_grid/catalog.json')).hexdigest()==marker['catalog']['sha256']
    assert not out.exists(),'Fresh evidence output required'
    lock=json.loads((PROFILES/'profile-lock.json').read_text(encoding='utf-8'))
    reserve=lock['minimumAvailableGiBBeforePair'];commit=None
    client_heap=lock['clientHeap'];heap_override=None
    if a.functional_client_heap is not None:
        assert a.variant=='candidate' and a.functional_memory_reserve_gib==7.0 and lineage is not None,'Functional client heap requires the existing FULL memory admission and verified client-only lineage'
        assert route in {(ROOT/'research/final-route/route.json').resolve(),(ROOT/'research/final-route/reopen-route.json').resolve()},'Functional client heap is limited to saved FULL final/reopen routes'
        assert lock['clientHeap']=='3G' and lock['serverHeap']=='4G','Keep the frozen base heaps unchanged'
        client_heap=a.functional_client_heap;heap_override={'clientHeap':client_heap,'baseClientHeap':lock['clientHeap'],'serverHeap':lock['serverHeap']}
    if a.functional_renderer is not None:
        assert a.variant=='candidate' and lineage is not None and a.functional_memory_reserve_gib==7.0 and a.functional_client_heap=='4G','Indigo requires the exact existing FULL lineage/memory/client-heap overrides'
        assert route in {(ROOT/'research/final-route/route.json').resolve(),(ROOT/'research/final-route/reopen-route.json').resolve()},'Indigo is limited to saved FULL final/reopen routes'
    if a.functional_memory_reserve_gib is not None:
        assert a.variant=='candidate' and route in {(ROOT/'research/final-route/route.json').resolve(),(ROOT/'research/final-route/reopen-route.json').resolve()},'Functional memory admission cannot change an original matched-pair run'
        full_light=json.loads((Path(marker['plan'])/'relight-binding.json').read_text(encoding='utf-8'))
        assert full_light.get('fullCityFreshLight') is True and Path(full_light['target']).resolve()==world and full_light['runtime']['sha256']==evidence_runtime,'Functional admission requires the accepted full-city light binding'
        assert lock['serverHeap']=='4G' and lock['clientHeap']=='3G','Keep both frozen base heaps unchanged'
        reserve=a.functional_memory_reserve_gib;commit=available_commit()
        assert commit>=9*1024**3,'Functional pair needs9GiB available Windows commit; no JVM launched'
    free=available();assert free>=reserve*1024**3, f'Available RAM {free/1024**3:.2f}GiB is below the explicit launch reserve {reserve}GiB; no JVM launched'
    qa=P/'build/libs/dreamwalker-native-route-qa-0.1.0-v12.7.4-native-grid.1.jar'
    recorder_path=PROFILES/'benchmark-helper.json';recorder=json.loads(recorder_path.read_text(encoding='utf-8'))
    assert recorder['status']=='FROZEN_BEFORE_FIRST_PAIR' and recorder['sameHelperForBaselineAndCandidate'] and recorder['measurementPhasesUnchanged']
    assert sha(qa)==recorder['helper']['sha256'] and sha(PROFILES/'profile-lock.json')==recorder['profileLockSha256'],'Frozen recorder/profile changed before the matched pair'
    renderer_override=functional_renderer(lock,a.functional_renderer=='indigo') if a.variant=='candidate' else None
    out.mkdir(parents=True);client=PROFILES/a.variant/'client';server=PROFILES/a.variant/'server'
    if lineage is not None:assert sha(prod)==production_sha,'Candidate JAR changed after client lineage validation'
    for profile in [client,server]:
        shutil.copy2(prod,profile/'mods/dreamwalker-production.jar');shutil.copy2(qa,profile/'mods/dreamwalker-native-route-qa.jar')
        if lineage is not None:assert sha(profile/'mods/dreamwalker-production.jar')==production_sha,'Installed candidate JAR differs'
    ident=uuid.UUID(bytes=hashlib.md5(b'OfflinePlayer:DreamwalkerQA').digest(),version=3)
    write(server/'ops.json',[{'uuid':str(ident),'name':'DreamwalkerQA','level':4,'bypassesPlayerLimit':True}])
    for name in ['server','client']:(out/name).mkdir()
    s_prior=[json.loads(line) for line in (BASE/'bloodborne complete/build/runtime-server-rp-streaming7/native.args').read_text(encoding='utf-8').splitlines() if line.strip()]
    c_prior=json.loads((BASE/'research/graphical-streaming-20261019/after/prepared.json').read_text(encoding='utf-8'))['clientArgs']
    argfile(out/'server.args',filtered(s_prior,server,lock['serverHeap'],'server',out/'server',world,route))
    argfile(out/'client.args',filtered(c_prior,client,client_heap,'client',out/'client',world,route))
    binding={'variant':a.variant,'productionSha256':sha(prod),'qaSha256':sha(qa),'recorderLockSha256':sha(recorder_path),'profileLockSha256':sha(PROFILES/'profile-lock.json'),'routeSha256':sha(route),'world':str(world),'initialAvailableRAM':free,'separateJVMs':True,'admissionMinimumAvailableGiB':reserve,'frozenMinimumAvailableGiB':lock['minimumAvailableGiBBeforePair'],'functionalAdmissionOverride':a.functional_memory_reserve_gib is not None,'initialAvailableCommit':commit}
    if lineage is not None:
        binding.update(clientOnlyRuntimeLineage=lineage_ref,acceptedPredecessorProduction=lineage['predecessor'])
    if heap_override is not None:binding['functionalHeapOverride']=heap_override
    if renderer_override is not None:binding['functionalRendererOverride']=renderer_override
    write(out/'binding.json',binding);started=time.monotonic();sp=cp=None;runtime_failed=False
    with (out/'server-console.log').open('w',encoding='utf-8') as slog,(out/'client-console.log').open('w',encoding='utf-8') as clog:
        try:
            sp=subprocess.Popen([str(JAVA),'@'+str(out/'server.args')],cwd=server,stdin=subprocess.PIPE,stdout=slog,stderr=subprocess.STDOUT,text=True,creationflags=subprocess.CREATE_NO_WINDOW)
            write(out/'pids.json',{'server':sp.pid,'client':None})
            deadline=time.monotonic()+600
            while time.monotonic()<deadline:
                reject_runtime_failure(out)
                assert sp.poll() is None,'Server exited before startup completed'
                if 'Done (' in (out/'server-console.log').read_text(encoding='utf-8',errors='replace'):break
                time.sleep(1)
            else:raise RuntimeError('Server did not complete native startup within600s')
            cp=subprocess.Popen([str(JAVA),'@'+str(out/'client.args')],cwd=client,stdout=clog,stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW)
            write(out/'pids.json',{'server':sp.pid,'client':cp.pid})
            deadline=time.monotonic()+a.timeout
            while cp.poll() is None:
                reject_runtime_failure(out)
                assert sp.poll() is None,'Dedicated server exited before the client route completed'
                if time.monotonic()>=deadline:raise RuntimeError('Client route exceeded pinned process deadline')
                time.sleep(1)
            ccode=cp.wait();deadline=time.monotonic()+120
            while sp.poll() is None:
                reject_runtime_failure(out)
                if time.monotonic()>=deadline:raise RuntimeError('Dedicated server did not finish its normal save within120s')
                time.sleep(1)
            scode=sp.wait();reject_runtime_failure(out)
            c=json.loads((out/'client/client-route.json').read_text(encoding='utf-8'));s=json.loads((out/'server/server-route.json').read_text(encoding='utf-8'))
            assert ccode==scode==0 and c['status']=='PASS_NATIVE_GRAPHICAL_ROUTE' and s['status']=='PASS_SAVED',(ccode,scode,c.get('error'),s.get('error'))
            for name in ['server','client']:
                console=(out/(name+'-console.log')).read_text(encoding='utf-8',errors='replace')
                assert not any(error in console for error in ['OutOfMemoryError','Failed to save','Exception stopping the server']),name+' process reported a real runtime/save failure'
                if name=='server':assert 'All dimensions are saved' in console,'Server did not confirm completed normal save'
                if name=='client' and renderer_override is not None:
                    assert '[Indigo] Registering Indigo renderer!' in console,'Actual client did not register the Indigo renderer'
                    assert '[Indigo] Different rendering plugin detected; not applying Indigo.' not in console,'Indigo was rejected by a different actual renderer'
                    assert not re.search(r'(?m)^\s*-\s+(?:sodium|indium)\s',console),'An excluded renderer mod was actually loaded'
                    for row in renderer_override['excludedClientMods']:
                        held=PROFILES/'candidate/disabled-functional-indigo'/row['file']
                        assert not (client/'mods'/row['file']).exists() and held.is_file() and sha(held)==row['sha256'],'Indigo client mod beforeimages changed during the route'
            write(out/'receipt.json',{**binding,'status':'PASS','clientExitCode':ccode,'serverExitCode':scode,'seconds':time.monotonic()-started})
        except BaseException as e:
            runtime_failed='OutOfMemoryError' in str(e)
            write(out/'receipt.json',{**binding,'status':'FAIL','error':repr(e),'seconds':time.monotonic()-started});raise
        finally:
            if cp and cp.poll() is None:cp.terminate();cp.wait(timeout=30)
            if sp and sp.poll() is None:
                try:sp.stdin.write('stop\n');sp.stdin.flush();sp.wait(timeout=30 if runtime_failed else 90)
                except (OSError,subprocess.TimeoutExpired):sp.terminate();sp.wait(timeout=30)
if __name__=='__main__':main()
