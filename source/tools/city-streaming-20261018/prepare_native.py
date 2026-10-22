"""Create a fresh owned QA runtime from the frozen city fixture, never user saves."""
import argparse,hashlib,json,shutil,sys,zipfile,traceback
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
P=Path(__file__).absolute().parents[2];OUT=P/'build/streaming-validation'
sys.path.insert(0,str(Path(__file__).absolute().parent))
from world_io import read_nbt,write_nbt
def sha(path):
 h=hashlib.sha256()
 with path.open('rb') as f:
  for b in iter(lambda:f.read(1024*1024),b''):h.update(b)
 return h.hexdigest()
def ordinary(path):
 if path.is_symlink() or path.stat().st_file_attributes&1024:raise ValueError('Reparse point refused: '+str(path))
 return path
def main():
 a=argparse.ArgumentParser();a.add_argument('--case',required=True);a.add_argument('--candidate',action='store_true');a.add_argument('--dh',action='store_true');args=a.parse_args()
 assert args.case.replace('-','').isalnum()
 previous=json.loads((P/'build/native-city-mesh/research-streaming-confirmed-prepared.json').read_text())
 old=Path(previous['run_directory']);run=P/('build/runtime-client-streaming-'+args.case);run.mkdir(exist_ok=False)
 baseline=P/'build/libs/dreamwalker-bb-fabric-1.20.1-0.1.0-v12.7.4-owner32-static-city.6-chunk-mesh.2.jar'
 version=dict(line.split('=',1) for line in (P/'gradle.properties').read_text().splitlines() if '=' in line)['mod_version']
 candidate=P/'build/libs'/('dreamwalker-bb-fabric-1.20.1-'+version+'.jar')
 addon=P/'build/libs'/('dreamwalker-gallery-author-'+version+'.jar')
 jar=candidate if args.candidate else baseline;digest=sha(jar)
 with zipfile.ZipFile(addon) as z:assert 'dev/dreamwalker/bloodbornedw/gallery/GalleryStreamingProbe.class' in z.namelist()
 (run/'mods').mkdir();(run/'resourcepacks').mkdir()
 for source in (old/'mods').glob('*.jar'):
  if source.name.startswith(('fabric-api-','geckolib-','mclib-')):shutil.copyfile(ordinary(source),run/'mods'/source.name)
 for source in [jar,addon]:shutil.copyfile(ordinary(source),run/'mods'/source.name)
 # Native libraries/assets/classpath remain immutable shared inputs in the old QA runtime.
 command=list(previous['command']);index=command.index('--gameDir')+1;command[index]=str(run)
 command=[x for x in command if not x.startswith(('-javaagent:','-XX:StartFlightRecording='))]
 options=(old/'options.txt').read_text();values=dict(line.split(':',1) for line in options.splitlines() if ':' in line)
 values.update(ao='false',gamma='0.5',resourcePacks='[]',incompatibleResourcePacks='[]',renderDistance='12',simulationDistance='12',hideGui='true',enableVsync='false',maxFps='260')
 (run/'options.txt').write_text('\n'.join(k+':'+v for k,v in values.items())+'\n')
 world=run/'saves/prototype-fixture';source=P/'build/native-city-mesh/fixtures/baseline/world';world.mkdir(parents=True)
 files=[];inputs=[]
 # Old authoring/history directories are evidence, not active Minecraft save input.
 for child in ordinary(source).iterdir():
  ordinary(child)
  if child.is_dir() and (child.name.startswith('dw-') and 'history' in child.name):continue
  if child.name=='session.lock':continue
  sources=sorted(child.rglob('*')) if child.is_dir() else [child]
  for path in sources:
   ordinary(path)
   if not path.is_file():continue
   inputs.append(path)
 def copy(path):
  relative=path.relative_to(source);target=world/relative;target.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(path,target)
  return {'path':relative.as_posix(),'bytes':path.stat().st_size,'sha256':sha(target)}
 with ThreadPoolExecutor(max_workers=8) as pool:files=list(pool.map(copy,inputs))
 level=world/'level.dat';before=sha(level);nbt=read_nbt(level);player=nbt.root.value['Data'].value['Player'].value;inventory=player['Inventory'].value
 removed=[x for x in inventory if x.value.get('Slot') and x.value['Slot'].value==103 and x.value['id'].value=='minecraft:carved_pumpkin'];inventory[:]=[x for x in inventory if x not in removed];write_nbt(level,nbt)
 coords=[('facade',[272.848,63.6296,-903.327,120.3,-3.5],[253,66,-915],'bloodborne_dw:source_bricks_bricks_r_0'),('tower',[650,110,-150,43.23,35],[603,70,-100],'bloodborne_dw:static_architecture_9'),('brick',[980,100,95,44.36,35],[940,28,129],'bloodborne_dw:static_architecture_14')]
 frames=[]
 for i,(key,camera,pos,state) in enumerate(coords):
  frame={'key':key,'fileName':args.case+'-'+key+'.png','camera':camera,'waitTicks':80,'measureFrameTimes':False,'travelFromPrevious':i>0,'requiredClientBlocks':[{'pos':pos,'expectedStateId':state}]}
  if i==0:frame['command']='tp @s '+' '.join(str(x) for x in camera)
  frames.append(frame)
 marker={'productionJarSha256':digest,'captureCase':args.case,'measureStreaming':True,'setupCommands':['gamemode spectator @s','gamerule doDaylightCycle false','time set 6000','weather clear'],'frames':frames,'stopAfterCapture':True}
 marker_path=run/'client-capture-input.json';marker_path.write_text(json.dumps(marker,indent=2)+'\n')
 report={'status':'PREPARED','run_directory':str(run),'artifact':str(jar),'artifact_sha256':digest,'command':command,'extra_mods':[{'copied_name':addon.name,'sha256':sha(addon)}],'gallery_capture':{'sha256':sha(marker_path)},'derived_world_copy':{'source':str(source),'files':files},'fixtureManifestSha256':hashlib.sha256(json.dumps(files,sort_keys=True).encode()).hexdigest(),'qaHeadRemoval':{'count':len(removed),'before':before,'after':sha(level)},'graphics':values,'scope':'Fresh isolated copy; all active Minecraft save files copied; archived authoring histories omitted; original world and user profile untouched'}
 (OUT/(args.case+'-prepared.json')).write_text(json.dumps(report,indent=2)+'\n')
if __name__=='__main__':
 try:main()
 except Exception:(OUT/'prepare-error.txt').write_text(traceback.format_exc());raise
