import ctypes,json,os,subprocess,sys,time
from pathlib import Path
P=Path(__file__).resolve().parents[1];G=Path('C:/Users/vakir/AppData/Local/Limacina/project/DreamWalker');role=sys.argv[1];attempt=sys.argv[2] if len(sys.argv)>2 else '1';assert role in ('server','client');profile=P/'fix-profiles'/role;D=P/'fix-evidence'/(role+'-'+attempt);D.mkdir(exist_ok=False)
heap='3G' if role=='server' else '4G'
cp=[G/'0.19.3.jar',Path('C:/Users/vakir/.gradle/caches/fabric-loom/1.20.1/minecraft-extracted_server.jar') if role=='server' else G/'1.20.1.jar',*sorted((G/'libraries').rglob('*.jar'))];assert all(p.is_file() for p in cp)
args=['-Xms128M','-Xmx'+heap,'-XX:ActiveProcessorCount=2','-Dfabric.modsFolder='+str(profile/'mods'),'-Ddw.fix.qa.output='+str(D)]
if role=='server':args+=['-Ddw.fix.qa.server.stop=true','-Ddw.fix.qa.only.we=true']
else:args+=['-Ddw.fix.qa.skip.we=true','-Djava.library.path='+str(G/'natives'),'-Dorg.lwjgl.librarypath='+str(G/'natives'),'-Ddw.source.light.output='+str(D/'light-trace.jsonl')]
args+=['-cp',os.pathsep.join(map(str,cp)),'net.fabricmc.loader.impl.launch.knot.KnotServer' if role=='server' else 'net.fabricmc.loader.impl.launch.knot.KnotClient']
if role=='server':args+=['nogui']
else:args+=['--username','DWFixQA','--version','1.20.1','--gameDir',str(profile),'--assetsDir',str(G/'assets'),'--assetIndex','5','--uuid','52cb9ccf410e3fe69e39564ec8344c55','--accessToken','0','--userType','legacy','--versionType','release','--width','1280','--height','720','--quickPlaySingleplayer','dw-fix-city']
argfile=D/'launch.args';argfile.write_text('\n'.join('"'+a.replace('\\','/').replace('"','\\"')+'"' for a in args)+'\n',encoding='utf-8');start=time.time()
with (D/'console.log').open('w',encoding='utf-8') as out:
 p=subprocess.Popen([str(P/'build-tools/jdk/bin/java.exe'),'@'+str(argfile)],cwd=profile,stdout=out,stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW);(D/'process.json').write_text(json.dumps({'pid':p.pid,'role':role,'heap':heap,'profile':str(profile)},indent=2));
 try:code=p.wait(timeout=1200 if role=='client' else 600)
 except subprocess.TimeoutExpired:p.terminate();code=p.wait(timeout=30)
(D/'launch-result.json').write_text(json.dumps({'exitCode':code,'seconds':time.time()-start,'heap':heap},indent=2));print(role,'exit',code)
