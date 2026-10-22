"""Addressed state-only native queries on one disposable flat authoring server."""
import argparse,json,shutil,subprocess,time
from pathlib import Path
from run_profiles import P,ROOT,BASE,PROFILES,JAVA,sha,write,argfile,filtered
ap=argparse.ArgumentParser();ap.add_argument('--request',type=Path,required=True);ap.add_argument('--out',type=Path,required=True);a=ap.parse_args()
request=a.request.resolve();out=a.out.resolve();assert out.is_relative_to(ROOT) and not out.exists()
out.mkdir(parents=True);profile=ROOT/'research/native-physics-minimal-server';mods=profile/'mods';mods.mkdir(parents=True,exist_ok=True)
for jar in (PROFILES/'baseline/server/mods').glob('*.jar'):
    if not any(name in jar.name.lower() for name in ['fabric-api','geckolib','mclib']):continue
    if not (mods/jar.name).exists():shutil.copy2(jar,mods/jar.name)
production=BASE/'deliverables/block-fixes-2-20261020/game/dreamwalker-bb-materials.1-streaming.7-blocks.2.jar'
helper=P/'build/libs/dreamwalker-native-grid-export-0.1.0-v12.7.4-native-grid.1.jar'
shutil.copy2(production,mods/'dreamwalker-production.jar');shutil.copy2(helper,mods/'native-grid-export.jar')
shutil.copy2(PROFILES/'baseline/server/eula.txt',profile/'eula.txt')
(profile/'server.properties').write_text('online-mode=false\nserver-ip=127.0.0.1\nserver-port=25580\nlevel-name=empty-authoring\nview-distance=2\nsimulation-distance=2\nlevel-type=minecraft:flat\ngenerate-structures=false\nspawn-animals=false\nspawn-monsters=false\nspawn-npcs=false\ndifficulty=peaceful\nmax-tick-time=120000\nsync-chunk-writes=true\n',encoding='utf-8')
world=profile/'empty-authoring'
prior=[json.loads(line) for line in (BASE/'bloodborne complete/build/runtime-server-rp-streaming7/native.args').read_text(encoding='utf-8').splitlines() if line.strip()]
args=filtered(prior,profile,'2G','server',out,world,None)
args=['-Ddw.nativegrid.physics.request='+str(request),'-Ddw.nativegrid.physics.output='+str(out),*args]
argfile(profile/'physics.args',args);start=time.monotonic()
write(out/'binding.json',{'productionSha256':sha(production),'helperSha256':sha(helper),'requestSha256':sha(request),'emptyAuthoringWorld':str(world),'actualSourceMapOpened':False,'heap':'2G'})
with (out/'server-console.log').open('w',encoding='utf-8') as log:
    child=subprocess.Popen([str(JAVA),'@'+str(profile/'physics.args')],cwd=profile,stdout=log,stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW)
    try:code=child.wait(timeout=600)
    except subprocess.TimeoutExpired:child.terminate();child.wait(timeout=30);raise
result=json.loads((out/'physics-result.json').read_text(encoding='utf-8')) if (out/'physics-result.json').is_file() else {'status':'FAIL','error':'No native query completion receipt'}
text=(out/'server-console.log').read_text(encoding='utf-8',errors='replace')
if 'OutOfMemoryError' in text:result={'status':'FAIL','error':'Real OOM in this process; callback completion is insufficient'}
write(out/'launch-receipt.json',{**result,'exitCode':code,'seconds':time.monotonic()-start,'productionSha256':sha(production),'requestSha256':sha(request)})
assert code==0 and result['status']=='PASS',result
