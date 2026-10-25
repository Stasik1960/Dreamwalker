import hashlib,json,os,shutil,subprocess,sys,time,zipfile
from pathlib import Path
P=Path(__file__).resolve().parents[1];G=Path('C:/Users/vakir/AppData/Local/Limacina/project/DreamWalker');attempt=sys.argv[1];D=P/'restoration-evidence'/('client-'+attempt);D.mkdir(exist_ok=False);game=P/'restoration-profiles'/('client-'+attempt);mods=game/'mods';mods.mkdir(parents=True);version='0.1.0-v12.7.4-visual-physics.6';production=P/f'build-native-visual/libs/dreamwalker-bb-fabric-1.20.1-{version}.jar';qa=P/f'build-native-visual/libs/dreamwalker-restoration-qa-{version}.jar'
for f in (G/'mods').glob('*.jar'):
 with zipfile.ZipFile(f) as z:mid=json.loads(z.read('fabric.mod.json')).get('id') if 'fabric.mod.json' in z.namelist() else ''
 if mid not in ('bloodborne_dw','worldedit'):shutil.copy2(f,mods/f.name)
for f in (production,qa):shutil.copy2(f,mods/f.name)
world=game/'saves/restoration-city';world.mkdir(parents=True)
with zipfile.ZipFile(P/'restoration-evidence/bloodborne-before-restoration.zip') as z:
 for n in z.namelist():
  if n.endswith('/'):continue
  rel=Path(*Path(n).parts[1:]);target=world/rel;assert target.resolve().is_relative_to(world.resolve());target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(z.read(n))
(game/'config').mkdir();(game/'config/iris.properties').write_text('enableShaders=false\n');(game/'options.txt').write_text('lang:ru_ru\nrenderDistance:3\nsimulationDistance:3\nmaxFps:30\nenableVsync:false\nfullscreen:false\ngamma:0.5\npauseOnLostFocus:false\nao:true\n',encoding='utf-8')
cp=[G/'0.19.3.jar',G/'1.20.1.jar',*sorted((G/'libraries').rglob('*.jar'))]
args=['-Xms128M','-Xmx4G','-XX:ActiveProcessorCount=2','-Dfabric.modsFolder='+str(mods),'-Ddw.restore.qa.output='+str(D),'-Djava.library.path='+str(G/'natives'),'-Dorg.lwjgl.librarypath='+str(G/'natives'),'-cp',os.pathsep.join(map(str,cp)),'net.fabricmc.loader.impl.launch.knot.KnotClient','--username','DWRestoreQA','--version','1.20.1','--gameDir',str(game),'--assetsDir',str(G/'assets'),'--assetIndex','5','--uuid','a0fef7d5375b3ed3a1f922530ed9ca36','--accessToken','0','--userType','legacy','--width','1280','--height','720','--quickPlaySingleplayer','restoration-city']
# Minecraft requires the standard 32-hex UUID.
args[args.index('--uuid')+1]='a0fef7d5375b3ed3a1f922530ed9ca36'.ljust(32,'0')
arg=D/'launch.args';arg.write_text('\n'.join('"'+a.replace('\\','/').replace('"','\\"')+'"' for a in args)+'\n',encoding='utf-8');start=time.time()
with (D/'console.log').open('w',encoding='utf-8') as out:
 p=subprocess.Popen([str(P/'build-tools/jdk/bin/java.exe'),'@'+str(arg)],cwd=game,stdout=out,stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW);(D/'process.json').write_text(json.dumps({'pid':p.pid,'heap':'4G','productionSha256':hashlib.sha256(production.read_bytes()).hexdigest(),'gameDir':str(game)},indent=2))
 while p.poll() is None:
  if time.time()-start>1200:p.terminate();break
  time.sleep(1)
 code=p.wait(timeout=30)
(D/'launch-result.json').write_text(json.dumps({'exitCode':code,'seconds':time.time()-start},indent=2))
