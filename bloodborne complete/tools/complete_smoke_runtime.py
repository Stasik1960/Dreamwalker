"""Bounded production-JAR/world smoke on loopback only. No live server is touched."""
import argparse,hashlib,json,pathlib,queue,re,shutil,subprocess,threading,time,zipfile
import complete_world_io as w
ROOT=pathlib.Path(__file__).resolve().parents[1]
def run(java,folder,commands,label):
 log=folder/(label+'.log');lines=queue.Queue();deadline=time.monotonic()+210
 proc=subprocess.Popen([str(java),'-Xms512M','-Xmx3G','-jar','fabric-server-launch.jar','nogui'],cwd=folder,stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,encoding='utf-8',errors='replace',creationflags=getattr(subprocess,'CREATE_NO_WINDOW',0))
 def reader():
  with log.open('w',encoding='utf-8') as f:
   for line in proc.stdout:f.write(line);f.flush();lines.put(line)
 thread=threading.Thread(target=reader,daemon=True);thread.start();ready=False;sent=False;marker=False;send_after=0
 try:
  while time.monotonic()<deadline:
   if ready and not sent and time.monotonic()>=send_after:
    for cmd in commands:proc.stdin.write(cmd+'\n')
    proc.stdin.flush();sent=True
   try:line=lines.get(timeout=.5)
   except queue.Empty:
    if proc.poll() is not None:break
    continue
   if 'Done (' in line:
    ready=True;print(json.dumps({'phase':label,'started':True}),flush=True)
    if any('DW_GALLERY_BLOCK_' in cmd for cmd in commands):
     proc.stdin.write('forceload add -80 -1104 142 -891\n');proc.stdin.flush();send_after=time.monotonic()+15
   if 'DW_GALLERY_ROOT_OK' in line:marker=True
  if proc.poll() is None:proc.stdin.write('stop\n');proc.stdin.flush();proc.wait(timeout=30)
  if not ready or proc.returncode!=0:raise ValueError('Production smoke failed '+label+'; '+str(log))
  thread.join(timeout=5);text=log.read_text(encoding='utf-8');assert '[ERROR]' not in text,'Unexpected production error: '+str(log);markers={kind:sorted(set(re.findall('DW_GALLERY_'+kind+r'_(\d{5})_OK',text))) for kind in ('BLOCK','BE','SIGN')}
  return {'started':ready,'exit_code':proc.returncode,'log':str(log.relative_to(ROOT)),'gallery_root_marker':marker,'verified_gallery_markers':markers}
 finally:
  if proc.poll() is None:proc.kill();proc.wait(timeout=10)
def main():
 p=argparse.ArgumentParser();p.add_argument('--java',type=pathlib.Path,required=True);p.add_argument('--city',type=pathlib.Path,required=True);p.add_argument('--gallery',type=pathlib.Path,required=True);p.add_argument('--case-prefix',default='');a=p.parse_args();base=ROOT/'build/production-smoke';cache=pathlib.Path.home()/'.gradle/caches/modules-2/files-2.1';report={'jar_sha256':hashlib.sha256((ROOT/'build/libs/bloodborne-dw-1.0.0-complete.1.jar').read_bytes()).hexdigest()}
 for label,archive,port in [('city',a.city,25640),('gallery',a.gallery,25641)]:
  folder=base/(a.case_prefix+label)
  if folder.exists():raise ValueError('Fresh test directory required '+str(folder))
  folder.mkdir();shutil.copytree(base/'libraries',folder/'libraries');shutil.copy2(base/'server.jar',folder/'server.jar');shutil.copy2(base/'fabric-server-launch.jar',folder/'fabric-server-launch.jar');mods=folder/'mods';mods.mkdir()
  shutil.copy2(ROOT/'build/libs/bloodborne-dw-1.0.0-complete.1.jar',mods)
  for name in ('fabric-api-0.92.9+1.20.1.jar','geckolib-fabric-1.20.1-4.4.9.jar'):shutil.copy2(next(cache.rglob(name)),mods/name)
  shutil.copy2(pathlib.Path(r'C:\Users\vakir\Documents\ChatGPT\DW\Bloodborne-Blocks\build\libs\bloodborne-blocks-2.1.0-repair-catalog.1.jar'),mods)
  shutil.copy2(ROOT/'local-inputs/worldedit-mod-7.2.15.jar',mods)
  (folder/'eula.txt').write_text('eula=true\n');(folder/'server.properties').write_text('server-ip=127.0.0.1\nserver-port='+str(port)+'\nonline-mode=false\nlevel-name=world\nview-distance=2\nsimulation-distance=2\nspawn-protection=0\nmax-players=1\n')
  with zipfile.ZipFile(archive) as z:
   for n in z.namelist():
    path=pathlib.PurePosixPath(n)
    if path.is_absolute() or '..' in path.parts:raise ValueError('Unsafe ZIP')
   z.extractall(folder/'world')
   manifest=json.loads(z.read('gallery-manifest.json')) if 'gallery-manifest.json' in z.namelist() else None
  commands=['bb visual alt all 00001']
  checks=[];expected_be=[]
  if manifest:
   first=manifest['platforms'][0];x,y,z=first['root'];commands.append(f"execute if block {x} {y} {z} bloodborne_dw:{first['block_id']} run say DW_GALLERY_ROOT_OK")
   for platform in manifest['platforms']:
    id=platform['block_id'];x,y,z=platform['root'];checks.append(f'execute if block {x} {y} {z} bloodborne_dw:{id} run say DW_GALLERY_BLOCK_{id}_OK')
    sx,sy,sz=platform['label'];checks.append(f'execute if data block {sx} {sy} {sz} front_text.messages[0] run say DW_GALLERY_SIGN_{id}_OK')
    source=platform['source'].split(':',1)[1]
    if source in ('beehive','daylight_detector','lectern','beacon','end_gateway','end_portal','ender_chest'):
     expected_be.append(id);checks.append(f'execute if data block {x} {y} {z} {{id:"minecraft:{source}"}} run say DW_GALLERY_BE_{id}_OK')
   commands.extend(checks)
  commands.extend(['save-all flush','stop']);report[label]=run(a.java,folder,commands,label+'-first')
  if manifest:assert report[label]['gallery_root_marker'],'Loaded gallery root did not match manifest'
  if manifest:
   markers=report[label]['verified_gallery_markers'];assert len(markers['BLOCK'])==580 and len(markers['SIGN'])==580 and markers['BE']==sorted(expected_be),markers
  persistent=folder/'world/data/bloodborne_dw_visual_rules.dat';assert persistent.exists();rules=w.decode_nbt(persistent.read_bytes(),compressed='gzip').root.value['data'].value['rules'].value;assert len(rules)==1 and rules[0].value['visual'].value=='alt'
  report[label]['visual_rules_saved']=True
  report[label+'-restart']=run(a.java,folder,checks+['save-all flush','stop'],label+'-restart')
  if manifest:
   markers=report[label+'-restart']['verified_gallery_markers'];assert len(markers['BLOCK'])==580 and len(markers['SIGN'])==580 and markers['BE']==sorted(expected_be),markers
  assert w.decode_nbt(persistent.read_bytes(),compressed='gzip').root.value['data'].value['rules'].value==rules;report[label+'-restart']['visual_rules_preserved']=True
  out=ROOT/'docs/production-smoke-verification.json';out.write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report),flush=True)
if __name__=='__main__':main()
