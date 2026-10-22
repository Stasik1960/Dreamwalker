"""One asset-only export against the immutable blocks.2 JAR. No world opened."""
import argparse,ctypes, hashlib,json,os,shutil,subprocess,time
from pathlib import Path
P=Path(__file__).resolve().parents[2];ROOT=P.parent
BASE=Path(r'C:\Users\vakir\.codex\worktrees\3f07\DW-city-mesh-20261018')
parser=argparse.ArgumentParser();parser.add_argument('--request',type=Path,default=ROOT/'research/export-request.json');parser.add_argument('--out',type=Path,default=ROOT/'research/geometry-final');options=parser.parse_args()
O=options.out.resolve();assert O.is_relative_to(ROOT) and not O.exists(),'Fresh asset evidence output required';O.mkdir(parents=True)
client=O/'client';mods=client/'mods';mods.mkdir(parents=True,exist_ok=True)
JAVA=Path(r'C:\Users\vakir\AppData\Roaming\.minecraft\runtime\java-runtime-gamma\windows\java-runtime-gamma\bin\java.exe')
production=BASE/'deliverables/block-fixes-2-20261020/game/dreamwalker-bb-materials.1-streaming.7-blocks.2.jar'
exporter=P/'build/libs/dreamwalker-native-grid-export-0.1.0-v12.7.4-native-grid.1.jar'
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
assert sha(production)=='40cecfb7e5cf783a762e350fc1864518c39abff177f6f025cb94892a181fbf51'
for jar in (BASE/'bloodborne complete/build/runtime-server-rp-streaming7/mods').glob('*.jar'):
    if any(t in jar.name.lower() for t in ('fabric-api','geckolib','mclib')):shutil.copy2(jar,mods/jar.name)
shutil.copy2(production,mods/'dreamwalker-production.jar');shutil.copy2(exporter,mods/'native-grid-export.jar')
prior=json.loads((BASE/'research/graphical-streaming-20261019/after/prepared.json').read_text(encoding='utf-8'))['clientArgs']
args=[];i=0
while i<len(prior):
    s=prior[i]
    if s.startswith(('-Ddw.','-Xms','-Xmx','-XX:StartFlightRecording','-XX:FlightRecorderOptions')):i+=1;continue
    if s in ('--server','--port','--quickPlaySingleplayer','--quickPlayMultiplayer','--quickPlayRealms'):i+=2;continue
    if s in ('--gameDir','--username','--accessToken','--width','--height'):
        args.extend([s,{'--gameDir':str(client),'--username':'NativeGridExport','--accessToken':'0','--width':'960','--height':'540'}[s]]);i+=2;continue
    args.append(s);i+=1
args=['-Xms128M','-Xmx2G','-XX:ActiveProcessorCount=2','-Dfabric.modsFolder='+str(mods),'-Ddw.nativegrid.export.request='+str(options.request.resolve()),'-Ddw.nativegrid.export.output='+str(O),*args]
argfile=client/'launch.args';argfile.write_text('\n'.join('"'+s.replace('\\','/').replace('"','\\"')+'"' for s in args)+'\n',encoding='utf-8')
binding={'productionSha256':sha(production),'exporterSha256':sha(exporter),'requestSha256':sha(options.request.resolve()),'heap':'2G','worldOpened':False}
(O/'binding.json').write_text(json.dumps(binding,indent=2),encoding='utf-8')
start=time.monotonic()
with (O/'client.log').open('w',encoding='utf-8') as log:
    child=subprocess.Popen([str(JAVA),'@'+str(argfile)],cwd=client,stdout=log,stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW)
    try:code=child.wait(timeout=480)
    except subprocess.TimeoutExpired:child.terminate();child.wait(timeout=30);raise
result=json.loads((O/'export-result.json').read_text(encoding='utf-8')) if (O/'export-result.json').is_file() else {'status':'FAIL','error':'No export completion report'}
console=(O/'client.log').read_text(encoding='utf-8',errors='replace')
if any(error in console for error in ['OutOfMemoryError','Java heap space','GC overhead limit exceeded','unable to create native thread']):result={**result,'status':'FAIL','driverError':'Real memory failure in asset process'}
(O/'launch-receipt.json').write_text(json.dumps({**binding,**result,'exitCode':code,'seconds':time.monotonic()-start},indent=2),encoding='utf-8')
assert code==0 and result['status']=='PASS',result
