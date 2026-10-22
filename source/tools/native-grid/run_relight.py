"""Native target server relight, durable per-batch cursor; no game client."""
import argparse,json,shutil,subprocess,time
from pathlib import Path
from run_profiles import P,ROOT,BASE,PROFILES,JAVA,sha,write,argfile,filtered,available
ap=argparse.ArgumentParser();ap.add_argument('--plan',type=Path,default=ROOT/'research/native-grid-plan');ap.add_argument('--batch',type=int,default=2);a=ap.parse_args()
plan=a.plan.resolve();binding=json.loads((plan/'relight-binding.json').read_text(encoding='utf-8'))
engine_receipt=Path(binding.get('engineReceipt',str(plan/'relight-engine.json'))).resolve()
engine_cursor=Path(binding.get('engineDoneCursor',str(engine_receipt)+'.done.tsv')).resolve()
assert engine_receipt.is_relative_to(plan) and engine_cursor==Path(str(engine_receipt)+'.done.tsv'),'Native receipt/cursor namespace must be bound to this plan'
world=Path(binding['target']);production=Path(binding['runtime']['path']);assert sha(production)==binding['runtime']['sha256']
assert available()>=5*1024**3,'Pinned relight launch needs5GiB available, no JVM started'
profile=ROOT/'research/relight-server';mods=profile/'mods';mods.mkdir(parents=True,exist_ok=True)
for jar in (PROFILES/'candidate/server/mods').glob('*.jar'):
    if 'dreamwalker-production' in jar.name or 'native-route-qa' in jar.name:continue
    target=mods/jar.name
    if not target.exists():shutil.copy2(jar,target)
    else:assert sha(jar)==sha(target)
shutil.copy2(production,mods/'dreamwalker-production.jar')
helper=P/'build/libs/dreamwalker-native-grid-relight-0.1.0-v12.7.4-native-grid.1.jar';shutil.copy2(helper,mods/'native-grid-relight.jar')
for name in ['eula.txt','server.properties']:shutil.copy2(PROFILES/'candidate/server'/name,profile/name)
prior=[json.loads(line) for line in (BASE/'bloodborne complete/build/runtime-server-rp-streaming7/native.args').read_text(encoding='utf-8').splitlines() if line.strip()]
args=filtered(prior,profile,'4G','server',plan,world,None)
args=['-Dnativegrid.relight.queue='+str(plan/'relight-queue.tsv'),'-Dnativegrid.relight.batch='+str(a.batch),'-Dnativegrid.relight.receipt='+str(engine_receipt),*args]
argfile(profile/'relight.args',args)
before=time.time();start=time.monotonic()
write(plan/'relight-launch-binding.json',{'runtimeSha256':sha(production),'helperSha256':sha(helper),'queueSha256':sha(plan/'relight-queue.tsv'),'world':str(world),'heap':'4G','batch':a.batch,'nativeEngine':True,'engineReceipt':str(engine_receipt),'engineDoneCursor':str(engine_cursor)})
with (plan/'relight-server-console.log').open('w',encoding='utf-8') as log:
    child=subprocess.Popen([str(JAVA),'@'+str(profile/'relight.args')],cwd=profile,stdin=subprocess.PIPE,stdout=log,stderr=subprocess.STDOUT,text=True,creationflags=subprocess.CREATE_NO_WINDOW)
    write(plan/'relight-process.json',{'pid':child.pid,'started':before,'world':str(world)})
    while child.poll() is None:
        console=(plan/'relight-server-console.log').read_text(encoding='utf-8',errors='replace')
        if 'OutOfMemoryError' in console:
            try:
                child.stdin.write('stop\n');child.stdin.flush();child.wait(timeout=30)
            except (OSError,subprocess.TimeoutExpired):
                child.terminate();child.wait(timeout=30)
            write(plan/'relight-launch-receipt.json',{'status':'FAIL','exitCode':child.returncode,'seconds':time.monotonic()-start,'runtimeSha256':sha(production),'error':'Real JVM OutOfMemoryError; own child stopped, native light debt remains unresolved'})
            raise RuntimeError('Relight JVM reported OutOfMemoryError; pending light is not accepted')
        time.sleep(1)
    code=child.wait()
result=json.loads(engine_receipt.read_text(encoding='utf-8'))
assert engine_receipt.stat().st_mtime>=before,'No fresh engine completion receipt'
console=(plan/'relight-server-console.log').read_text(encoding='utf-8',errors='replace')
assert not any(error in console for error in ['OutOfMemoryError','Failed to save','Exception stopping the server']),'Relight process reported a real runtime/save failure'
assert 'All dimensions are saved' in console,'Relight server did not confirm completed normal save'
write(plan/'relight-launch-receipt.json',{'status':result['status'],'exitCode':code,'seconds':time.monotonic()-start,'runtimeSha256':sha(production),'queueSha256':sha(plan/'relight-queue.tsv')})
assert code==0 and result['status']=='PASS' and result['pending']==0 and result['saveFlushed'],result
