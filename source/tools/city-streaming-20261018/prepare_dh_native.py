"""Reuse only a normally saved owned QA runtime, preserving its route evidence."""
import argparse,hashlib,json,msvcrt,shutil,subprocess,zipfile
from pathlib import Path
P=Path(__file__).absolute().parents[2];O=P/'build/streaming-validation';D=P/'build/dh-city-pilot'
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def ordinary(p):
 if p.is_symlink() or p.stat().st_file_attributes&1024:raise ValueError('Reparse point: '+str(p))
 return p
def main():
 ap=argparse.ArgumentParser();ap.add_argument('--source-case',default='after2');ap.add_argument('--scene',type=Path,default=D/'tower-scene-final');a=ap.parse_args();case=a.source_case;assert case.replace('-','').isalnum()
 # A visual-only adapter revision may be prepared while the main mod's
 # native route is running. Rebuild it only now, after that JVM saved/exited.
 source_sha=sha(P/'src/dhpilot/java/dev/dreamwalker/bloodbornedw/dh/CityDhPilot.java');build_receipt=D/'build-pilot-ready-receipt.json'
 built=json.loads(build_receipt.read_text()) if build_receipt.exists() else {}
 if built.get('sourceSha256')!=source_sha or built.get('exitCode')!=0:
  ps=r'C:\Users\vakir\.cache\codex-runtimes\codex-primary-runtime\dependencies\native\powershell\pwsh.exe'
  with (D/'prepare-adapter-build.log').open('w') as log:subprocess.run([ps,'-NoProfile','-File','tools/city-streaming-20261018/build_pilot_ready.ps1'],cwd=P,stdout=log,stderr=subprocess.STDOUT,check=True)
  built=json.loads(build_receipt.read_text());assert built.get('exitCode')==0 and built.get('sourceSha256')==source_sha,built
 prep=json.loads((O/(case+'-prepared.json')).read_text());launch=json.loads((O/(case+'-launch.json')).read_text());assert launch['exitCode']==0 and launch.get('captureComplete') and launch.get('normalSaveMessages')
 run=Path(prep['run_directory']).absolute();assert run.parent==P/'build' and run.name=='runtime-client-streaming-'+case
 world=ordinary(run/'saves/prototype-fixture');cap=json.loads((run/'client-capture-output.json').read_text());assert cap['status']=='CAPTURE_COMPLETE'
 archive=O/(case+'-evidence');archive.mkdir(exist_ok=False)
 with ordinary(world/'session.lock').open('r+b') as lock:
  lock.seek(0);msvcrt.locking(lock.fileno(),msvcrt.LK_NBLCK,1)
  try:
   originals=[run/name for name in ['client-capture-output.json','client-capture-input.json','launch-console.log','options.txt']]+[run/'screenshots'/row['fileName'] for row in cap['frames']]
   records=[]
   for path in originals:ordinary(path);target=archive/path.name;shutil.copyfile(path,target);records.append({'file':path.name,'sha256':sha(target)})
   (archive/'manifest.json').write_text(json.dumps(records,indent=2)+'\n')
   (run/'client-capture-output.json').rename(run/('client-capture-output-'+case+'.json'));(run/'launch-console.log').rename(run/('launch-console-'+case+'.log'))
   old=prep['extra_mods'][0];old_path=ordinary(run/'mods'/old['copied_name']);assert sha(old_path)==old['sha256'];old_path.rename(archive/old_path.name)
   version=dict(line.split('=',1) for line in (P/'gradle.properties').read_text().splitlines() if '=' in line)['mod_version']
   jars=[P/'build/libs'/('dreamwalker-gallery-author-'+version+'.jar'),P/'build/libs'/('dreamwalker-dh-city-pilot-'+version+'.jar'),D/'dependencies/DistantHorizons-3.3.3-1.20.1-fabric-forge.jar']
   extras=[]
   for jar in jars:
    with zipfile.ZipFile(ordinary(jar)) as z:mod_id=json.loads(z.read('fabric.mod.json'))['id']
    assert mod_id in ['dreamwalker_gallery_author','dreamwalker_dh_city_pilot','distanthorizons']
    target=run/'mods'/jar.name;assert not target.exists();shutil.copyfile(jar,target);extras.append({'file':str(target),'sha256':sha(target),'id':mod_id})
   data=world/'dreamwalker-dh-pilot';data.mkdir(exist_ok=False);source=a.scene;provenance=json.loads((source/'scene.json').read_text())
   shutil.copyfile(source/'city-boxes.bin.gz',data/'city-boxes.bin.gz');shutil.copyfile(source/'scene.json',data/'export-provenance.json')
   minimal={key:provenance[key] for key in ['schema','dataSha256','catalogSha256','readOnlyDhTerrain','bounds','totalBoxes','exporterSha256']};minimal['worldDirectory']=str(world)
   (data/'scene.json').write_text(json.dumps(minimal,indent=2)+'\n')
   (run/'dreamwalker-dh-pilot-qa.json').write_text(json.dumps({'disableDistantGeneration':True})+'\n')
   camera=[760,140,-112,90,10];frames=[]
   for key,rd,active,measure in [('reference12',12,False,True),('native6',6,False,False),('dh6',6,True,True)]:
    # Decimal coordinates prevent command-parser block centering (+0.5).
    frame={'key':key,'fileName':'dh-pilot-'+key+'.png','camera':camera,'command':'tp @s '+' '.join(str(float(value)) for value in camera),'renderDistance':rd,'simulationDistance':rd,'dhPilotEnabled':active,'waitTicks':200,'measureFrameTimes':measure,'travelFromPrevious':False}
    if rd==12:frame['requiredClientBlocks']=[{'pos':[603,70,-100],'expectedStateId':'bloodborne_dw:static_architecture_9'}]
    else:frame['assertUnloadedClientChunks']=[[37,-7]]
    if active:frame['requireDhPilot']=True
    frames.append(frame)
   marker={'productionJarSha256':prep['artifact_sha256'],'captureCase':'dh-pilot','measureStreaming':True,'setupCommands':['gamemode spectator @s','gamerule doDaylightCycle false','time set 6000','weather clear'],'frames':frames,'stopAfterCapture':True}
   marker_path=run/'client-capture-input.json';marker_path.write_text(json.dumps(marker,indent=2)+'\n')
   result={'status':'PREPARED','run_directory':str(run),'artifact':prep['artifact'],'artifact_sha256':prep['artifact_sha256'],'command':prep['command'],'mods':extras,'markerSha256':sha(marker_path),'sceneSha256':sha(data/'scene.json'),'dataSha256':sha(data/'city-boxes.bin.gz'),'sourceCase':case,'referenceEvidence':str(archive),'scope':'Owned saved QA runtime; camera-only tests; native shader/lighting code unchanged; DH API city proxy compared off/on at six chunks'}
   (D/'native-prepared.json').write_text(json.dumps(result,indent=2)+'\n')
  finally:lock.seek(0);msvcrt.locking(lock.fileno(),msvcrt.LK_UNLCK,1)
if __name__=='__main__':main()
