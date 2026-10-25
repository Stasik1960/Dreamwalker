import hashlib,json,os,shutil,subprocess,sys,time,zipfile
from pathlib import Path
P=Path(__file__).resolve().parents[1];G=Path('C:/Users/vakir/AppData/Local/Limacina/project/DreamWalker');role=sys.argv[1];attempt=sys.argv[2];assert role in ('server','client');D=P/'faerie-evidence'/(role+'-'+attempt);D.mkdir(exist_ok=False);profile=P/'faerie-profiles'/role;mods=profile/'mods';mods.mkdir(parents=True,exist_ok=True)
production=P/'build-native-visual/libs/dreamwalker-bb-fabric-1.20.1-0.1.0-v12.7.4-visual-physics.5.jar';qa=P/'build-native-visual/libs/dreamwalker-faerie-qa-0.1.0-v12.7.4-visual-physics.5.jar';assert production.is_file() and qa.is_file()
for f in Path('C:/Users/vakir/Desktop').joinpath(role+'_mods').glob('*.jar'):
 with zipfile.ZipFile(f) as z:mid=json.loads(z.read('fabric.mod.json')).get('id') if 'fabric.mod.json' in z.namelist() else ''
 if mid in ('bloodborne_dw','worldedit'):continue
 if not (mods/f.name).exists():shutil.copy2(f,mods/f.name)
for f in (production,qa):shutil.copy2(f,mods/f.name)
if role=='server':
 game=profile;(game/'eula.txt').write_text('eula=true\n');(game/'server.properties').write_text('online-mode=false\nserver-ip=127.0.0.1\nserver-port=25589\nlevel-name=faerie-test\nlevel-type=minecraft:flat\ngenerator-settings={"layers":[{"block":"minecraft:air","height":1}],"biome":"minecraft:plains","lakes":false,"features":false}\nview-distance=2\nsimulation-distance=3\nspawn-protection=0\nmax-tick-time=180000\n',encoding='utf-8');heap='3G'
else:
 game=profile;heap='4G';(game/'saves').mkdir(exist_ok=True);world=game/'saves/dw-faerie-qa'
 if not world.exists():shutil.copytree(P/'faerie-profiles/server/faerie-test',world)
 (game/'options.txt').write_text('renderDistance:3\nsimulationDistance:3\nmaxFps:40\npauseOnLostFocus:false\nshowTutorial:false\n',encoding='utf-8');(game/'config').mkdir(exist_ok=True);(game/'config/iris.properties').write_text('enableShaders=false\n')
cp=[G/'0.19.3.jar',Path('C:/Users/vakir/.gradle/caches/fabric-loom/1.20.1/minecraft-extracted_server.jar') if role=='server' else G/'1.20.1.jar',*sorted((G/'libraries').rglob('*.jar'))]
args=['-Xms128M','-Xmx'+heap,'-XX:ActiveProcessorCount=2','-Dfabric.modsFolder='+str(mods),'-Ddw.faerie.qa.output='+str(D)]
if role=='server':args+=['-Ddw.faerie.qa.stop=true']
else:args+=['-Ddw.faerie.qa.verify=true','-Djava.library.path='+str(G/'natives'),'-Dorg.lwjgl.librarypath='+str(G/'natives')]
args+=['-cp',os.pathsep.join(map(str,cp)),'net.fabricmc.loader.impl.launch.knot.KnotServer' if role=='server' else 'net.fabricmc.loader.impl.launch.knot.KnotClient']
if role=='server':args+=['nogui']
else:args+=['--username','DWFaerieQA','--version','1.20.1','--gameDir',str(game),'--assetsDir',str(G/'assets'),'--assetIndex','5','--uuid','a0fef7d5375b3ed3a1f922530ed9ca36','--accessToken','0','--userType','legacy','--versionType','release','--width','1280','--height','720','--quickPlaySingleplayer','dw-faerie-qa']
arg=D/'launch.args';arg.write_text('\n'.join('"'+a.replace('\\','/').replace('"','\\"')+'"' for a in args)+'\n',encoding='utf-8');start=time.time()
with (D/'console.log').open('w',encoding='utf-8') as out:
 p=subprocess.Popen([str(P/'build-tools/jdk/bin/java.exe'),'@'+str(arg)],cwd=game,stdout=out,stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW);(D/'process.json').write_text(json.dumps({'pid':p.pid,'role':role,'heap':heap,'profile':str(profile),'productionSha256':hashlib.sha256(production.read_bytes()).hexdigest()},indent=2))
 savedAt=None
 while p.poll() is None:
  now=time.time()
  if now-start>(1200 if role=='client' else 600):p.terminate();break
  if role=='server' and (D/'server-checks.json').exists():
   if 'All dimensions are saved' in (D/'console.log').read_text(encoding='utf-8',errors='replace'):
    if savedAt is None:savedAt=now
    if now-savedAt>12:
     (D/'post-save-stop.json').write_text(json.dumps({'worldSaved':True,'reason':'Own test JVM remained after all dimensions saved','pid':p.pid},indent=2));p.terminate();break
  time.sleep(1)
 code=p.wait(timeout=30)
(D/'launch-result.json').write_text(json.dumps({'exitCode':code,'seconds':time.time()-start,'heap':heap},indent=2));print(role,'exit',code)
