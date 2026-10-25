import hashlib,json,os,shutil,subprocess,sys,time,zipfile
from pathlib import Path
P=Path(__file__).resolve().parents[1];G=Path('C:/Users/vakir/AppData/Local/Limacina/project/DreamWalker');attempt=sys.argv[1];D=P/'restoration-evidence'/('server-'+attempt);D.mkdir(exist_ok=False,parents=True);profile=P/'restoration-profiles'/('server-'+attempt);mods=profile/'mods';mods.mkdir(parents=True)
version='0.1.0-v12.7.4-visual-physics.6';production=P/f'build-native-visual/libs/dreamwalker-bb-fabric-1.20.1-{version}.jar';qa=P/f'build-native-visual/libs/dreamwalker-restoration-qa-{version}.jar';assert production.is_file() and qa.is_file()
for f in Path('C:/Users/vakir/Desktop/server_mods').glob('*.jar'):
 with zipfile.ZipFile(f) as z:mid=json.loads(z.read('fabric.mod.json')).get('id') if 'fabric.mod.json' in z.namelist() else ''
 if mid not in ('bloodborne_dw','worldedit'):shutil.copy2(f,mods/f.name)
for f in (production,qa,P/'fix-profiles/server/mods/worldedit-mod-7.2.15.jar'):shutil.copy2(f,mods/f.name)
(profile/'eula.txt').write_text('eula=true\n');(profile/'server.properties').write_text('online-mode=false\nserver-ip=127.0.0.1\nserver-port=25589\nlevel-name=restoration-test\nlevel-type=minecraft:flat\ngenerator-settings={"layers":[{"block":"minecraft:air","height":1}],"biome":"minecraft:plains","lakes":false,"features":false}\nview-distance=2\nsimulation-distance=3\nspawn-protection=0\nmax-tick-time=180000\n',encoding='utf-8')
cp=[G/'0.19.3.jar',Path('C:/Users/vakir/.gradle/caches/fabric-loom/1.20.1/minecraft-extracted_server.jar'),*sorted((G/'libraries').rglob('*.jar'))]
args=['-Xms128M','-Xmx3G','-XX:ActiveProcessorCount=2','-Dfabric.modsFolder='+str(mods),'-Ddw.restore.qa.server=true','-Ddw.restore.qa.output='+str(D),'-cp',os.pathsep.join(map(str,cp)),'net.fabricmc.loader.impl.launch.knot.KnotServer','nogui']
if '--followup' in sys.argv:args.insert(0,'-Ddw.restore.qa.followup=true')
arg=D/'launch.args';arg.write_text('\n'.join('"'+a.replace('\\','/').replace('"','\\"')+'"' for a in args)+'\n',encoding='utf-8');start=time.time()
with (D/'console.log').open('w',encoding='utf-8') as out:
 p=subprocess.Popen([str(P/'build-tools/jdk/bin/java.exe'),'@'+str(arg)],cwd=profile,stdout=out,stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW);(D/'process.json').write_text(json.dumps({'pid':p.pid,'heap':'3G','productionSha256':hashlib.sha256(production.read_bytes()).hexdigest(),'profile':str(profile)},indent=2));saved=None
 while p.poll() is None:
  if 'Failed to start the minecraft server' in (D/'console.log').read_text(encoding='utf-8',errors='replace'):p.terminate();break
  if time.time()-start>600:p.terminate();break
  if (D/'checks.json').exists() and 'All dimensions are saved' in (D/'console.log').read_text(encoding='utf-8',errors='replace'):
   if saved is None:saved=time.time()
   if time.time()-saved>12:p.terminate();break
  time.sleep(1)
 code=p.wait(timeout=30)
(D/'launch-result.json').write_text(json.dumps({'exitCode':code,'seconds':time.time()-start,'saved':saved is not None},indent=2))
