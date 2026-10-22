"""One bounded pure-state bridge query; the city is never opened."""
import argparse,importlib.util,json,shutil,subprocess,time,zipfile
from pathlib import Path
from run_profiles import P,ROOT,BASE,PROFILES,JAVA,sha,write,argfile,filtered,available
parser=argparse.ArgumentParser();parser.add_argument('--request',type=Path,required=True);parser.add_argument('--production',type=Path,required=True);parser.add_argument('--out',type=Path,required=True);parser.add_argument('--storage-previous-runtime',type=Path);a=parser.parse_args()
request=a.request.resolve();production=a.production.resolve();out=a.out.resolve()
assert out.is_relative_to(ROOT) and not out.exists(),'Fresh authoring evidence required'
assert available()>=5*1024**3,'Pinned4G server plus reserve required; no JVM started'
out.mkdir(parents=True);profile=ROOT/'research/native-bridge-authoring-server';mods=profile/'mods';mods.mkdir(parents=True,exist_ok=True)
if a.storage_previous_runtime:
    spec=importlib.util.spec_from_file_location('storage_transition_proof',P/'tools/native-grid-city/migrate.py');migration=importlib.util.module_from_spec(spec);spec.loader.exec_module(migration)
    write(out/'storage-entry-proof.json',migration.storage_jar_proof(a.storage_previous_runtime,production))
    write(out/'storage-source-proof.json',migration.storage_source_proof(ROOT/'research/pending-lithium-storage/source-receipt.json'))
for jar in (PROFILES/'baseline/server/mods').glob('*.jar'):
    if not any(token in jar.name.lower() for token in ['fabric-api','geckolib','mclib','lithium']):continue
    if not (mods/jar.name).exists():shutil.copy2(jar,mods/jar.name)
helper=P/'build/libs/dreamwalker-native-grid-qa-0.1.0-v12.7.4-native-grid.1.jar'
shutil.copy2(production,mods/'dreamwalker-production.jar');shutil.copy2(helper,mods/'native-grid-qa.jar')
shutil.copy2(PROFILES/'baseline/server/eula.txt',profile/'eula.txt')
(profile/'server.properties').write_text('online-mode=false\nserver-ip=127.0.0.1\nserver-port=25580\nlevel-name=empty-authoring\nview-distance=2\nsimulation-distance=2\nlevel-type=minecraft:flat\ngenerate-structures=false\nspawn-animals=false\nspawn-monsters=false\nspawn-npcs=false\ndifficulty=peaceful\nmax-tick-time=120000\nsync-chunk-writes=true\n',encoding='utf-8')
world=profile/'empty-authoring';receipt=out/'bridge-carrier-gate.json'
prior=[json.loads(line) for line in (BASE/'bloodborne complete/build/runtime-server-rp-streaming7/native.args').read_text(encoding='utf-8').splitlines() if line.strip()]
args=filtered(prior,profile,'4G','server',out,world,None)
args=['-Ddw.nativegridqa.bridge.request='+str(request),'-Ddw.nativegridqa.bridge.output='+str(receipt),*args]
argfile(profile/'bridge.args',args)
with zipfile.ZipFile(production) as jar:
    import hashlib
    catalog_hash=hashlib.sha256(jar.read('assets/bloodborne_dw/native_grid/catalog.json')).hexdigest()
binding={'productionSha256':sha(production),'helperSha256':sha(helper),'requestSha256':sha(request),'catalogSha256':catalog_hash,'heap':'4G','emptyAuthoringWorld':str(world),'actualCityOpened':False,'stateQueriesOnly':True}
write(out/'binding.json',binding);started=time.monotonic()
with (out/'server-console.log').open('w',encoding='utf-8') as log:
    child=subprocess.Popen([str(JAVA),'@'+str(profile/'bridge.args')],cwd=profile,stdout=log,stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW)
    try:code=child.wait(timeout=600)
    except subprocess.TimeoutExpired:child.terminate();child.wait(timeout=30);raise
result=json.loads(receipt.read_text(encoding='utf-8')) if receipt.is_file() else {'status':'FAIL','error':'No bound bridge state gate receipt'}
console=(out/'server-console.log').read_text(encoding='utf-8',errors='replace')
if any(error in console for error in ['OutOfMemoryError','Failed to save','Exception stopping the server']):result={**result,'status':'FAIL','driverError':'Real memory or save failure'}
if 'All dimensions are saved' not in console:result={**result,'status':'FAIL','driverError':'Normal native save/exit unconfirmed'}
write(out/'launch-receipt.json',{**binding,**result,'exitCode':code,'seconds':time.monotonic()-started})
assert code==0 and result['status']=='PASS',result
