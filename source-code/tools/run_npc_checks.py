import hashlib,json,os,shutil,subprocess,sys,time,zipfile
from pathlib import Path
P=Path(__file__).resolve().parents[1];G=Path('C:/Users/vakir/AppData/Local/Limacina/project/DreamWalker');D=P/'npc-evidence';role=sys.argv[1];attempt=sys.argv[2];out=D/(role+'-'+attempt);out.mkdir(parents=True,exist_ok=False);profile=P/'npc-profiles'/(role+'-'+attempt);mods=profile/'mods';mods.mkdir(parents=True);version='0.1.0-v12.7.4-visual-physics.8'
production=P/f'build-native-visual/libs/dreamwalker-bb-fabric-1.20.1-{version}.jar';qa=P/f'build-native-visual/libs/dreamwalker-restoration-qa-{version}.jar';assert production.is_file() and qa.is_file();assert json.loads((D/'build.json').read_text())['exitCode']==0
if role=='apply':
 preflight=json.loads((D/'real-apply-preflight.json').read_text());assert preflight['status']=='PASS' and preflight['productionSha256']==hashlib.sha256(production.read_bytes()).hexdigest()
client=role=='client';fullMap='--map' in sys.argv;sourceMods=Path('C:/Users/vakir/Desktop/server_mods') if role in ('preview','apply') else G/'mods'
for f in sourceMods.glob('*.jar'):
 with zipfile.ZipFile(f) as z:mid=json.loads(z.read('fabric.mod.json')).get('id') if 'fabric.mod.json' in z.namelist() else ''
 if (client and mid!='bloodborne_dw') or (role in ('preview','apply') and mid not in ('bloodborne_dw','worldedit')) or (fullMap and mid!='bloodborne_dw') or (role not in ('preview','apply') and not fullMap and mid in ('fabric-api','geckolib','sodium','indium','modernfix') and (role=='client' or mid not in ('sodium','indium'))):shutil.copy2(f,mods/f.name)
for f in (production,qa):shutil.copy2(f,mods/f.name)
client=role=='client';apply=role in ('preview','apply');world=profile/'world'
if apply:
 if role=='preview':shutil.copytree(Path('C:/Users/vakir/Desktop/bloodborne'),world)
 else:world=Path('C:/Users/vakir/Desktop/bloodborne')
elif client:shutil.copytree(P/'npc-profiles/server-1/world',profile/'saves/interactions')
(profile/'eula.txt').write_text('eula=true\n');(profile/'server.properties').write_text('online-mode=false\nserver-ip=127.0.0.1\nserver-port=25589\nlevel-name='+world.as_posix()+'\nlevel-type=minecraft:flat\ngenerator-settings={"layers":[{"block":"minecraft:air","height":1}],"biome":"minecraft:plains","lakes":false,"features":false}\nview-distance=2\nsimulation-distance=3\nspawn-protection=0\nmax-tick-time=180000\n',encoding='utf-8')
cp=[G/'0.19.3.jar',G/'1.20.1.jar' if client else Path('C:/Users/vakir/.gradle/caches/fabric-loom/1.20.1/minecraft-extracted_server.jar'),*sorted((G/'libraries').rglob('*.jar'))]
heap=os.environ.get('DW_NPC_QA_HEAP','3584M' if client else '3G');args=['-Xms128M','-Xmx'+heap,'-XX:ActiveProcessorCount=4','-Dfabric.modsFolder='+str(mods),'-Ddw.npc.qa.output='+str(out)]
if apply:args+=['-Ddw.interaction.apply.plan='+str(D)]
elif not client:args+=['-Ddw.npc.qa.server=true']
if fullMap:args+=['-Ddw.npc.qa.map='+str(D/'preview-2/map-apply.json')]
if '--resume' in sys.argv:args+=['-Ddw.npc.qa.resume=true']
if client:
 if client:shutil.copytree(G/'config',profile/'config')
 else:(profile/'config').mkdir()
 (profile/'config/iris.properties').write_text('enableShaders=false\n');(profile/'options.txt').write_text('lang:ru_ru\nrenderDistance:3\nsimulationDistance:3\nmaxFps:30\nenableVsync:false\nfullscreen:false\npauseOnLostFocus:false\n',encoding='utf-8');args+=['-Djava.library.path='+str(G/'natives'),'-Dorg.lwjgl.librarypath='+str(G/'natives')]
args+=['-cp',os.pathsep.join(map(str,cp)),'net.fabricmc.loader.impl.launch.knot.KnotClient' if client else 'net.fabricmc.loader.impl.launch.knot.KnotServer']
if client:args+=['--username','DWNpcQA','--version','1.20.1','--gameDir',str(profile),'--assetsDir',str(G/'assets'),'--assetIndex','5','--uuid','a0fef7d5375b3ed3a1f922530ed9ca360','--accessToken','0','--userType','legacy','--width','1280','--height','720','--quickPlaySingleplayer','interactions']
else:args+=['nogui']
arg=out/'launch.args';arg.write_text('\n'.join('"'+a.replace('\\','/').replace('"','\\"')+'"' for a in args)+'\n',encoding='utf-8');start=time.time();saved=None
with (out/'console.log').open('w',encoding='utf-8') as log:
 proc=subprocess.Popen([str(P/'build-tools/jdk/bin/java.exe'),'@'+str(arg)],cwd=profile,stdout=log,stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW);(out/'process.json').write_text(json.dumps({'pid':proc.pid,'heap':heap,'role':role,'profile':str(profile),'productionSha256':hashlib.sha256(production.read_bytes()).hexdigest()},indent=2))
 while proc.poll() is None:
  text=(out/'console.log').read_text(encoding='utf-8',errors='replace')
  if 'Failed to start the minecraft server' in text or time.time()-start>(1200 if client else 900):proc.terminate();break
  if not client and 'All dimensions are saved' in text:
   if saved is None:saved=time.time()
   if time.time()-saved>12:proc.terminate();break
  time.sleep(1)
 code=proc.wait(timeout=30)
for f in (profile/'screenshots').glob('*.png'):shutil.copy2(f,out/f.name)
(out/'launch-result.json').write_text(json.dumps({'exitCode':code,'seconds':time.time()-start,'saved':saved is not None or 'All dimensions are saved' in (out/'console.log').read_text(encoding='utf-8',errors='replace')},indent=2))
