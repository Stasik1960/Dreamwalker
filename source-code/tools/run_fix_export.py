import ctypes,hashlib,json,os,shutil,subprocess,time,uuid,zipfile
from pathlib import Path
P=Path(__file__).resolve().parents[1];G=Path('C:/Users/vakir/AppData/Local/Limacina/project/DreamWalker');D=P/'fix-evidence/export';profile=P/'fix-profiles/export';(profile/'mods').mkdir(parents=True,exist_ok=True)
selected={}
for jar in (G/'mods').glob('*.jar'):
 with zipfile.ZipFile(jar) as z:
  try:mid=json.loads(z.read('fabric.mod.json'))['id']
  except KeyError:continue
 if mid in ('bloodborne_dw','fabric-api','geckolib','mclib'):
  if mid=='bloodborne_dw':assert hashlib.sha256(jar.read_bytes()).hexdigest()=='337ad3113ea02fd9e59b0646167d1ed51c769f048277e3a0858b4c89d2cfceb5'
  shutil.copy2(jar,profile/'mods'/jar.name);selected[mid]=jar.name
assert {'bloodborne_dw','fabric-api','geckolib'}<=selected.keys()
qa=P/'build-native-visual/libs/dreamwalker-native-grid-export-0.1.0-v12.7.4-visual-physics.2.jar';shutil.copy2(qa,profile/'mods'/qa.name)
(profile/'options.txt').write_text('renderDistance:2\nsimulationDistance:2\nmaxFps:30\nenableVsync:false\nfullscreen:false\ngamma:0.5\n',encoding='utf-8')
libraries=sorted((G/'libraries').rglob('*.jar'));cp=[G/'0.19.3.jar',G/'1.20.1.jar',*libraries];assert all(p.is_file() for p in cp);indexes=sorted((G/'assets/indexes').glob('*.json'));assert indexes
index=next((p.stem for p in indexes if p.stem=='5'),indexes[-1].stem)
args=['-Xms128M','-Xmx3G','-XX:ActiveProcessorCount=2','-Djava.library.path='+str(G/'natives'),'-Dorg.lwjgl.librarypath='+str(G/'natives'),'-Dfabric.modsFolder='+str(profile/'mods'),'-Ddw.nativegrid.export.request='+str(D/'request.json'),'-Ddw.nativegrid.export.output='+str(D/'baked'),'-cp',os.pathsep.join(map(str,cp)),'net.fabricmc.loader.impl.launch.knot.KnotClient','--username','DWFixQA','--version','1.20.1','--gameDir',str(profile),'--assetsDir',str(G/'assets'),'--assetIndex',index,'--uuid',uuid.uuid3(uuid.NAMESPACE_DNS,'DWFixQA').hex,'--accessToken','0','--userType','legacy','--versionType','release','--width','960','--height','540']
argfile=D/'launch.args';argfile.write_text('\n'.join('"'+a.replace('\\','/').replace('"','\\"')+'"' for a in args)+'\n',encoding='utf-8');start=time.time()
with (D/'console.log').open('w',encoding='utf-8') as out:
 p=subprocess.Popen([str(P/'build-tools/jdk/bin/java.exe'),'@'+str(argfile)],cwd=profile,stdout=out,stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW);(D/'process.json').write_text(json.dumps({'pid':p.pid,'profile':str(profile),'mods':selected,'heap':'3G'}));
 try:code=p.wait(timeout=600)
 except subprocess.TimeoutExpired:p.terminate();code=p.wait(timeout=30)
(D/'launch-result.json').write_text(json.dumps({'exitCode':code,'seconds':time.time()-start,'profile':str(profile)},indent=2));assert code==0 and json.loads((D/'baked/export-result.json').read_text())['status']=='PASS'
