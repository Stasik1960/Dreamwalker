"""One bounded functional check on the same matched disposable pilot."""
import argparse,json,shutil,subprocess,time,zipfile
from pathlib import Path
from run_profiles import P,ROOT,BASE,PROFILES,JAVA,sha,write,argfile,filtered,available
ap=argparse.ArgumentParser();ap.add_argument('--plan',type=Path,required=True);ap.add_argument('--authoring-reference',action='store_true');a=ap.parse_args()
planpath=a.plan.resolve();plan=json.loads(planpath.read_text(encoding='utf-8'));world=Path(plan['targetWorldPath']).resolve();out=Path(plan['output']).resolve();production=Path(plan['productionJar']).resolve();variant=plan['variant']
assert world.is_relative_to(ROOT) and out.is_relative_to(ROOT) and not out.exists()
assert available()>=5*1024**3,'Need pinned4G server plus native reserve; no JVM started'
assert sha(production)==plan['productionSha256'] and not (world/'NATIVE-GRID-INCOMPLETE.json').exists()
assert bool(plan.get('authoringReferenceOnly',False))==a.authoring_reference,'Reference scope must be explicit in both input and command'
if a.authoring_reference:assert variant=='baseline','The optimized candidate must pass the full target profile'
if variant=='candidate':
    marker=json.loads((world/'NATIVE-GRID-MIGRATION.json').read_text(encoding='utf-8'));assert marker['runtime']['sha256']==sha(production)
out.mkdir(parents=True);profile=ROOT/'research/function-qa'/('server-'+variant+('-reference' if a.authoring_reference else ''));mods=profile/'mods';mods.mkdir(parents=True,exist_ok=True)
excluded=[]
for jar in (PROFILES/variant/'server/mods').glob('*.jar'):
    if 'dreamwalker-production' in jar.name or 'native-route-qa' in jar.name:continue
    if a.authoring_reference:
        with zipfile.ZipFile(jar) as archive:identity=json.loads(archive.read('fabric.mod.json'))['id']
        if identity in ('worldedit','lithium'):
            excluded.append({'id':identity,'file':jar.name,'sha256':sha(jar)});continue
    if not (mods/jar.name).exists():shutil.copy2(jar,mods/jar.name)
if a.authoring_reference:assert {row['id'] for row in excluded}=={'worldedit','lithium'}
shutil.copy2(production,mods/'dreamwalker-production.jar')
helper=P/'build/libs/dreamwalker-native-grid-qa-0.1.0-v12.7.4-native-grid.1.jar';shutil.copy2(helper,mods/'native-grid-function-qa.jar')
for name in ['eula.txt','server.properties']:shutil.copy2(PROFILES/variant/'server'/name,profile/name)
prior=[json.loads(line) for line in (BASE/'bloodborne complete/build/runtime-server-rp-streaming7/native.args').read_text(encoding='utf-8').splitlines() if line.strip()]
args=filtered(prior,profile,'4G','server',out,world,None)
args=['-Ddw.nativegridqa.plan='+str(planpath),*args];argfile(profile/'functions.args',args);start=time.monotonic()
write(out/'binding.json',{'world':str(world),'productionSha256':sha(production),'installedProductionSha256':sha(mods/'dreamwalker-production.jar'),'helperSha256':sha(helper),'planSha256':sha(planpath),'heap':'4G','functionalNotBenchmark':True,'authoringReferenceOnly':a.authoring_reference,'excludedDependencies':excluded,'targetProfileUsed':not a.authoring_reference})
with (out/'server-console.log').open('w',encoding='utf-8') as log:
    child=subprocess.Popen([str(JAVA),'@'+str(profile/'functions.args')],cwd=profile,stdin=subprocess.PIPE,stdout=log,stderr=subprocess.STDOUT,text=True,creationflags=subprocess.CREATE_NO_WINDOW)
    write(out/'process.json',{'pid':child.pid,'arguments':str(profile/'functions.args'),'world':str(world)})
    deadline=time.monotonic()+1200;abort_reason=None
    while child.poll() is None:
        console=(out/'server-console.log').read_text(encoding='utf-8',errors='replace')
        if 'OutOfMemoryError' in console:abort_reason='Real JVM OutOfMemoryError'
        elif time.monotonic()>=deadline:abort_reason='Functional process exceeded pinned1200s bound'
        if abort_reason:
            try:
                child.stdin.write('stop\n');child.stdin.flush();child.wait(timeout=30)
            except (OSError,subprocess.TimeoutExpired):
                child.terminate();child.wait(timeout=30)
            write(out/'driver-stop.json',{'status':'FAIL','reason':abort_reason,'pid':child.pid,'ownChildOnly':True})
            break
        time.sleep(1)
    code=child.wait()
text=(out/'server-console.log').read_text(encoding='utf-8',errors='replace')
result=json.loads((out/'functions.json').read_text(encoding='utf-8')) if (out/'functions.json').is_file() else {'status':'FAIL','error':'No function completion report'}
errors=[]
if 'OutOfMemoryError' in text or 'Failed to save' in text or 'Exception stopping the server' in text:errors.append('OOM or failed native save in this process')
if 'All dimensions are saved' not in text:errors.append('Native save/exit completion unconfirmed')
if abort_reason:errors.append(abort_reason)
if errors:result={**result,'status':'FAIL','driverError':'; '.join(errors)}
write(out/'launch-receipt.json',{'status':result['status'],'exitCode':code,'seconds':time.monotonic()-start,'productionSha256':sha(production),'helperSha256':sha(helper),'functionalNotBenchmark':True,'authoringReferenceOnly':a.authoring_reference,'excludedDependencies':excluded,'error':result.get('error',result.get('driverError'))})
assert code==0 and result['status']=='PASS',result
