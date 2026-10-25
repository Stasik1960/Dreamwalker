"""Run finite real-server create/reopen checks on an isolated copy and profile."""
import json,os,shutil,subprocess,sys,time,traceback,zipfile
from pathlib import Path
P=Path(__file__).resolve().parents[2];T=P.parent.parent/'DW-visual-physics-20261024';O=P.parent.parent/'DW-userpack-optimization-20261024'
sys.path.insert(0,str(P/'tools/native-grid'))
import run_profiles as driver
def read(p):return json.loads(p.read_text(encoding='utf-8-sig'))
def write(p,n):p.write_text(json.dumps(n,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
def jar_id(p):
    with zipfile.ZipFile(p) as z:return json.loads(z.read('fabric.mod.json'))['id']
def main():
    assert read(T/'map-remap.json')['status']=='OFFLINE_REMAP_AND_REOPEN_VERIFIED_NOT_GAMEPLAY'
    production=T/'build/libs/dreamwalker-bb-fabric-1.20.1-0.1.0-v12.7.4-visual-physics.1.jar'
    qa=T/'build/libs/dreamwalker-native-grid-qa-0.1.0-v12.7.4-visual-physics.1.jar'
    assert jar_id(production)=='bloodborne_dw' and jar_id(qa)=='dw_native_grid_function_qa'
    integration='--integration' in sys.argv
    world=T/('integration-world' if integration else 'qa-world');profile=T/('profiles/integration-server' if integration else 'profiles/qa-server');receipt=T/('integration-checks.json' if integration else 'server-checks.json');state=T/'qa-state.json'
    assert not receipt.exists()
    if not world.exists():shutil.copytree(T/'world',world)
    (profile/'mods').mkdir(parents=True,exist_ok=True)
    expected={}
    for source in Path('C:/Users/vakir/Desktop/server_mods').glob('*.jar'):
        if jar_id(source)=='bloodborne_dw':continue
        target=profile/'mods'/source.name
        if not target.exists():shutil.copy2(source,target)
        assert driver.sha(target)==driver.sha(source);expected[target.name]=driver.sha(target)
    for source,name in ((production,'dreamwalker-production.jar'),(qa,'dreamwalker-visual-qa.jar')):
        shutil.copy2(source,profile/'mods'/name);expected[name]=driver.sha(source)
    assert {p.name:driver.sha(p) for p in (profile/'mods').glob('*.jar')}==expected
    original=O/'profiles/final/server'
    for name in ('config','defaultconfigs'):
        if (original/name).is_dir() and not (profile/name).exists():shutil.copytree(original/name,profile/name)
    props={}
    for line in (original/'server.properties').read_text(encoding='utf-8-sig').splitlines():
        if '=' in line and not line.startswith('#'):k,v=line.split('=',1);props[k]=v
    props.update({'server-ip':'127.0.0.1','server-port':'25579','online-mode':'false','enable-rcon':'false','view-distance':'6','simulation-distance':'4','sync-chunk-writes':'true'})
    (profile/'server.properties').write_text('\n'.join(k+'='+v for k,v in props.items())+'\n',encoding='utf-8');(profile/'eula.txt').write_text('eula=true\n')
    prior=[json.loads(line) for line in (O/'actual-final/server.args').read_text(encoding='utf-8-sig').splitlines() if line.strip()]
    results=[];resume='--resume' in sys.argv
    inherited=None
    if resume:
        prior_report=read(T/'checks-create/report.json');prior_pid=read(T/'checks-create/pid.json')
        assert prior_report.get('exactCellPhysicsChecked')==156287 and prior_report['error']=='java.lang.AssertionError: afterBreak drops exact former visual despite cleared chunk entry'
        assert prior_pid['productionSha256']==driver.sha(production),'Physics reuse requires unchanged exact production JAR'
        inherited={'path':str(T/'checks-create/report.json'),'sha256':driver.sha(T/'checks-create/report.json')}
    for phase in (('integration',) if integration else ('resume-create','reopen') if resume else ('create','reopen')):
        out=T/('checks-'+phase);assert not out.exists();out.mkdir()
        free=driver.available()/2**30;assert free>=5, 'Need at least 5 GiB free RAM before server QA, actual '+str(free)
        args=driver.filtered(prior,profile,'4G','server',out,world,None)
        args=[arg for arg in args if not arg.startswith('-XX:ActiveProcessorCount=')]
        args[:0]=['-XX:ActiveProcessorCount=16','-Ddw.nativevisualqa.output='+str(out),'-Ddw.nativevisualqa.state='+str(state),'-Ddw.nativevisualqa.phase='+phase]
        if integration:
            args=[arg for arg in args if not arg.startswith('-Ddw.nativevisualqa.')]
            args.insert(0,'-Ddw.nativevisual.integration.output='+str(out))
        if inherited:args.insert(0,'-Ddw.nativevisualqa.inheritedPhysics='+inherited['path'])
        driver.argfile(out/'server.args',args)
        started=time.monotonic();process=None
        with (out/'server-console.log').open('w',encoding='utf-8') as log:
            process=subprocess.Popen([str(driver.JAVA),'@'+str(out/'server.args')],cwd=profile,stdin=subprocess.PIPE,stdout=log,stderr=subprocess.STDOUT,text=True,creationflags=subprocess.CREATE_NO_WINDOW)
            write(out/'pid.json',{'pid':process.pid,'productionSha256':driver.sha(production),'qaSha256':driver.sha(qa)})
            try:
                while process.poll() is None:
                    if time.monotonic()-started>900:raise TimeoutError('Finite server QA exceeded 900s')
                    time.sleep(1)
            except BaseException:
                if process.poll() is None:
                    try:process.stdin.write('stop\n');process.stdin.flush();process.wait(timeout=60)
                    except BaseException:process.terminate();process.wait(timeout=15)
                raise
        report=read(out/'report.json') if (out/'report.json').exists() else {'status':'MISSING'}
        result={'phase':phase,'exitCode':process.returncode,'seconds':time.monotonic()-started,'report':report,'log':str(out/'server-console.log')}
        write(out/'receipt.json',result);results.append(result)
        assert process.returncode==0 and report['status']=='PASS' and report.get('normalServerThreadFinished') is True,'Server phase failed: '+phase
        driver.reject_runtime_failure(out)
    write(receipt,{'status':'PASS_HELPER_AND_WHOLE_INTEGRATION' if integration else 'PASS_TWO_REAL_SERVER_VMS_SAVE_REOPEN','productionSha256':driver.sha(production),'qaSha256':driver.sha(qa),'world':str(world),'sourceWorldUnchanged':True,'inheritedExactPhysicsProof':inherited,'phases':results})
if __name__=='__main__':
    try:main()
    except BaseException:
        (T/'server-checks-runner-error.txt').write_text(traceback.format_exc(),encoding='utf-8');raise
