from common import *
import shutil,subprocess,time
def main():
 log_to('run-native-checks')
 out=OUT/(sys.argv[1] if len(sys.argv)>1 else 'native');out.mkdir(exist_ok=True);server=out/'server';server.mkdir(exist_ok=True);mods=server/'mods';mods.mkdir(exist_ok=True)
 for p in (PROJECT/'build/runtime-server-rp-streaming7/mods').glob('*.jar'):
  if any(s in p.name.lower() for s in ['fabric-api','geckolib','mclib']):shutil.copyfile(p,mods/p.name)
 production=DELIVERY/'game/dreamwalker-bb-materials.1-streaming.7-blocks.2.jar';shutil.copyfile(production,mods/'dreamwalker-bb-blocks.jar')
 qa=PROJECT/'build/libs/dreamwalker-block-repair-checks-0.1.0-v12.7.4-owner32-static-city.6-chunk-mesh.3-materials.1-streaming.7-blocks.2.jar';assert qa.is_file();shutil.copyfile(qa,mods/'checks.jar')
 (server/'eula.txt').write_text('eula=true\n');(server/'server.properties').write_text('online-mode=false\nserver-ip=127.0.0.1\nserver-port=25892\nview-distance=2\nsimulation-distance=2\nlevel-type=minecraft:flat\ngenerator-settings={"layers":[{"block":"minecraft:bedrock","height":1}],"biome":"minecraft:plains"}\nmax-tick-time=180000\nspawn-protection=0\n',encoding='utf8')
 prev=read(ROOT/'research/graphical-streaming-20261019/after/prepared.json')['serverArgs'];cp=prev[prev.index('-cp')+1]
 args=['-Xms128M','-Xmx3G','-XX:ActiveProcessorCount=2','-Dfabric.modsFolder='+str(mods),'-Ddw.blockqa.output='+str(out),'-cp',cp,'net.fabricmc.loader.impl.launch.knot.KnotServer','--universe',str(server),'--world','test-world','nogui']
 argsfile=server/'launch.args';argsfile.write_text('\n'.join('"'+s.replace('\\','/').replace('"','\\"')+'"' for s in args)+'\n',encoding='utf8')
 start=time.time();child=None
 try:
  with (out/'console.log').open('w',encoding='utf8') as log:
   child=subprocess.Popen([str(JAVA),'@'+str(argsfile)],cwd=server,stdout=log,stderr=subprocess.STDOUT,stdin=subprocess.PIPE,creationflags=subprocess.CREATE_NO_WINDOW)
   child.wait(timeout=360)
  report=read(out/'checks.json');assert report['status']=='PASS',report.get('error')
  write(out/'receipt.json',{'status':'PASS','exitCode':child.returncode,'seconds':time.time()-start,'checks':len(report['checks']),'productionJarSha256':sha(production),'qaSha256':sha(qa)})
  print('PASS',len(report['checks']))
 except BaseException as failure:
  write(out/'receipt.json',{'status':'FAIL','error':str(failure),'seconds':time.time()-start});raise
 finally:
  if child and child.poll() is None:
   try:child.stdin.write(b'stop\n');child.stdin.flush();child.wait(timeout=30)
   except Exception:child.kill();child.wait(timeout=10)
if __name__=='__main__':main()
