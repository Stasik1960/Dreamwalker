"""One bounded functional check on the same matched disposable pilot."""
import argparse,json,shutil,subprocess,time,zipfile
from pathlib import Path
from run_profiles import P,ROOT,BASE,PROFILES,JAVA,sha,write,argfile,filtered,available
ap=argparse.ArgumentParser();ap.add_argument('--plan',type=Path,required=True);ap.add_argument('--authoring-reference',action='store_true');a=ap.parse_args()
planpath=a.plan.resolve();plan=json.loads(planpath.read_text(encoding='utf-8'));world=Path(plan['targetWorldPath']).resolve();out=Path(plan['output']).resolve();production=Path(plan['productionJar']).resolve();variant=plan['variant']
assert world.is_relative_to(ROOT) and out.is_relative_to(ROOT) and not out.exists()
assert available()>=5*1024**3,'Need pinned4G server plus native reserve; no JVM started'
assert sha(production)==plan['productionSha256'] and not (world/'NATIVE-GRID-INCOMPLETE.json').exists()
runtime_patch=None
if 'runtimePatch' in plan:
    from runtime_patch_receipt import validate
    patch_binding=plan['runtimePatch'];patch_path=Path(patch_binding['path']).resolve()
    assert patch_path.is_relative_to(ROOT) and sha(patch_path)==patch_binding['sha256'],'Runtime patch evidence changed'
    runtime_patch=validate(patch_path,production)
assert bool(plan.get('authoringReferenceOnly',False))==a.authoring_reference,'Reference scope must be explicit in both input and command'
if a.authoring_reference:assert variant=='baseline','The optimized candidate must pass the full target profile'
isolated_stand=None
if 'isolatedAuthoringStand' in plan:
    isolated_stand=plan['isolatedAuthoringStand'];fixture=plan.get('foreignGuestFixture')
    known_empty=(ROOT/'research/native-bridge-authoring-server/empty-authoring').resolve()
    assert variant=='candidate' and not a.authoring_reference and not runtime_patch and isinstance(fixture,dict)
    assert isolated_stand=={'kind':'EXACT_FOREIGN_GUEST_ISOLATED_AUTHORING_STAND','world':str(known_empty)}
    assert world==known_empty and plan.get('addressedQaScope',{}).get('kind')=='ONE_FOREIGN_GUEST_FIXTURE_ONLY'
    assert fixture.get('phase') in ('install_save','reopen_finish') and 'continuation' not in plan
    def proof_file(value):
        path=Path(value['path']).resolve();assert path.is_relative_to(ROOT) and sha(path)==value['sha256'],'Isolated-stand proof changed'
        return json.loads(path.read_text(encoding='utf-8'))
    point=proof_file(fixture['pointProof']);audit=proof_file(fixture['targetBridgeAudit'])
    audit_folder=Path(fixture['targetBridgeAudit']['path']).resolve().parent
    audit_launch=json.loads((audit_folder/'launch-receipt.json').read_text(encoding='utf-8'));audit_binding=json.loads((audit_folder/'binding.json').read_text(encoding='utf-8'))
    assert point['status']=='PASS_SOURCE_COMPOSITION_AND_TYPE_BINDING_NOT_GAMEPLAY' and point['kind']=='EXACT_D_E_NONROOT_NATIVE_GRID_OVERLAY' and point['scope']=='FULL_CITY'
    assert audit['status']=='PASS' and audit['nativeRegistryQueries'] is True and audit['pairsChecked']>0
    assert audit['runtimeSha256']==plan['productionSha256'] and audit['catalogSha256']==point['catalogSha256'] and audit['bridgePlanSha256']==point['targetBridgePlanSha256']
    assert audit_launch['status']=='PASS' and audit_launch['exitCode']==0 and all(audit_launch.get(key)==value for key,value in audit.items())
    assert audit_launch['productionSha256']==audit_binding['productionSha256']==plan['productionSha256'] and audit_binding['catalogSha256']==point['catalogSha256']
    assert audit_binding['requestSha256']==audit['bridgePlanSha256'] and audit_binding['emptyAuthoringWorld']==str(known_empty) and audit_binding['actualCityOpened'] is False
    with zipfile.ZipFile(production) as archive:
        import hashlib
        assert hashlib.sha256(archive.read('assets/bloodborne_dw/native_grid/catalog.json')).hexdigest()==point['catalogSha256']
if variant=='candidate':
    if isolated_stand is None:
        marker=json.loads((world/'NATIVE-GRID-MIGRATION.json').read_text(encoding='utf-8'))
        if runtime_patch:
            assert marker['runtime']['sha256'] in (runtime_patch['before']['sha256'],runtime_patch['after']['sha256'])
            assert marker['catalog']['sha256']==runtime_patch['catalogSha256'],'The adapter-only patch cannot change migrated states or lighting resources'
        else:assert marker['runtime']['sha256']==sha(production)
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
if isolated_stand:
    bound=json.loads((out/'binding.json').read_text(encoding='utf-8'));bound['isolatedAuthoringStand']=isolated_stand;bound['migrationMarkerRequired']=False;bound['markerExemptionScope']='one exact FOREIGN fixture in the bound existing empty-authoring world only';bound['pointProof']=plan['foreignGuestFixture']['pointProof'];bound['targetBridgeAudit']=plan['foreignGuestFixture']['targetBridgeAudit'];write(out/'binding.json',bound)
if runtime_patch:
    bound=json.loads((out/'binding.json').read_text(encoding='utf-8'));bound['runtimePatch']=plan['runtimePatch'];bound['patchScope']=runtime_patch['scope'];write(out/'binding.json',bound)
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
