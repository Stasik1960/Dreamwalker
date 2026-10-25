import hashlib,json,os,shutil,subprocess,sys,time,zipfile
from pathlib import Path
P=Path(__file__).resolve().parents[1];G=Path('C:/Users/vakir/AppData/Local/Limacina/project/DreamWalker');role=sys.argv[1];attempt=sys.argv[2];assert role in ('server','client');D=P/'bench-evidence'/(role+'-'+attempt);D.mkdir(exist_ok=False);profile=P/'bench-profiles'/role;mods=profile/'mods';mods.mkdir(parents=True,exist_ok=True)
production=P/'build-native-visual/libs/dreamwalker-bb-fabric-1.20.1-0.1.0-v12.7.4-visual-physics.3.jar';qa=P/'build-native-visual/libs/dreamwalker-bench-qa-0.1.0-v12.7.4-visual-physics.3.jar';assert production.is_file() and qa.is_file()
for f in Path('C:/Users/vakir/Desktop').joinpath(role+'_mods').glob('*.jar'):
 with zipfile.ZipFile(f) as z:
  mid=json.loads(z.read('fabric.mod.json')).get('id') if 'fabric.mod.json' in z.namelist() else ''
 if mid in ('bloodborne_dw','worldedit'):continue
 if not (mods/f.name).exists():shutil.copy2(f,mods/f.name)
for f in (production,qa):shutil.copy2(f,mods/f.name)
if role=='server':
 game=profile;(game/'eula.txt').write_text('eula=true\n');(game/'server.properties').write_text('online-mode=false\nserver-ip=127.0.0.1\nserver-port=25589\nlevel-name=bench-test\nlevel-type=minecraft:flat\ngenerator-settings={"layers":[{"block":"minecraft:air","height":1}],"biome":"minecraft:plains","lakes":false,"features":false}\nview-distance=2\nsimulation-distance=3\nspawn-protection=0\nmax-tick-time=180000\n',encoding='utf-8');heap='3G'
else:game=P/'fix-profiles/client';heap='4G'
cp=[G/'0.19.3.jar',Path('C:/Users/vakir/.gradle/caches/fabric-loom/1.20.1/minecraft-extracted_server.jar') if role=='server' else G/'1.20.1.jar',*sorted((G/'libraries').rglob('*.jar'))]
args=['-Xms128M','-Xmx'+heap,'-XX:ActiveProcessorCount=2','-Dfabric.modsFolder='+str(mods),'-Ddw.bench.qa.output='+str(D)]
if role=='server':args+=['-Ddw.bench.qa.stop=true']
else:args+=['-Djava.library.path='+str(G/'natives'),'-Dorg.lwjgl.librarypath='+str(G/'natives'),'-Ddw.bench.qa.roots='+str(P/'bench-evidence/map-benches.json')]
args+=['-cp',os.pathsep.join(map(str,cp)),'net.fabricmc.loader.impl.launch.knot.KnotServer' if role=='server' else 'net.fabricmc.loader.impl.launch.knot.KnotClient']
if role=='server':args+=['nogui']
else:args+=['--username','DWBenchQA','--version','1.20.1','--gameDir',str(game),'--assetsDir',str(G/'assets'),'--assetIndex','5','--uuid','dcaee3d11dba3bdba6bcad572b90e81e','--accessToken','0','--userType','legacy','--versionType','release','--width','1280','--height','720','--quickPlaySingleplayer','dw-fix-city']
arg=D/'launch.args';arg.write_text('\n'.join('"'+a.replace('\\','/').replace('"','\\"')+'"' for a in args)+'\n',encoding='utf-8');start=time.time()
with (D/'console.log').open('w',encoding='utf-8') as out:
 p=subprocess.Popen([str(P/'build-tools/jdk/bin/java.exe'),'@'+str(arg)],cwd=game,stdout=out,stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW);(D/'process.json').write_text(json.dumps({'pid':p.pid,'role':role,'heap':heap,'profile':str(profile),'productionSha256':hashlib.sha256(production.read_bytes()).hexdigest()},indent=2))
 try:code=p.wait(timeout=1200 if role=='client' else 600)
 except subprocess.TimeoutExpired:p.terminate();code=p.wait(timeout=30)
(D/'launch-result.json').write_text(json.dumps({'exitCode':code,'seconds':time.time()-start,'heap':heap},indent=2));print(role,'exit',code)
