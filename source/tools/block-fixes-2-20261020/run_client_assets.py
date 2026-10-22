from common import *
import shutil,subprocess,time
small='--assets-only' in sys.argv
log_to('client-assets-wrapper');out=OUT/('native-assets-only' if small else 'client-assets');out.mkdir(exist_ok=True);client=out/'client';client.mkdir(exist_ok=True);mods=client/'mods';mods.mkdir(exist_ok=True)
for p in (PROJECT/'build/runtime-server-rp-streaming7/mods').glob('*.jar'):
 if any(s in p.name.lower() for s in (['fabric-api'] if small else ['fabric-api','geckolib','mclib'])):shutil.copyfile(p,mods/p.name)
prod=DELIVERY/'game/dreamwalker-bb-materials.1-streaming.7-blocks.2.jar'
if not small:shutil.copyfile(prod,mods/'dreamwalker-bb-blocks.jar')
qa=out/'asset-harness.jar' if small else PROJECT/'build/libs/dreamwalker-block-repair-checks-0.1.0-v12.7.4-owner32-static-city.6-chunk-mesh.3-materials.1-streaming.7-blocks.2.jar';shutil.copyfile(qa,mods/'checks.jar')
prev=read(ROOT/'research/graphical-streaming-20261019/after/prepared.json')['clientArgs'];args=[];i=0
while i<len(prev):
 s=prev[i]
 if s.startswith(('-Ddw.','-Xms','-Xmx')):i+=1;continue
 if s=='--gameDir':args.extend([s,str(client)]);i+=2;continue
 if s=='--username':args.extend([s,'BlockRepairQA']);i+=2;continue
 args.append(s);i+=1
args=['-Xms128M','-Xmx2G' if small else '-Xmx4G','-XX:ActiveProcessorCount=2','-Dfabric.modsFolder='+str(mods),'-Ddw.blockqa.assetCheck=true','-Ddw.blockqa.hidden=true','-Ddw.blockqa.output='+str(out),*args]
argfile=client/'launch.args';argfile.write_text('\n'.join('"'+s.replace('\\','/').replace('"','\\"')+'"' for s in args)+'\n',encoding='utf8')
start=time.time();child=None
try:
 with (out/'console.log').open('w',encoding='utf8') as log:
  child=subprocess.Popen([str(JAVA),'@'+str(argfile)],cwd=client,stdout=log,stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW);child.wait(timeout=360)
 report=read(out/'client-assets.json');assert report['status']=='PASS',report.get('error')
 write(out/'receipt.json',{'status':'PASS','seconds':time.time()-start,'exitCode':child.returncode,'jarSha256':sha(prod),'qaSha256':sha(qa),'states':report['states'],'faces':report['faces']});print('PASS',report['states'],report['faces'])
except BaseException as failure:write(out/'receipt.json',{'status':'FAIL','error':str(failure),'seconds':time.time()-start});raise
finally:
 if child and child.poll() is None:child.kill();child.wait(timeout=10)
