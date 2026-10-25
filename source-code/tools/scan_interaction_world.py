"""Read the actual user world, record its current manifest and exact repair inputs."""
import collections,hashlib,json,os,sys,time,zipfile
from pathlib import Path
import world_io as w
P=Path(__file__).resolve().parents[1];D=P/'interaction-evidence';W=Path('C:/Users/vakir/Desktop/bloodborne');R=P/'src/architecture/resources';D.mkdir(exist_ok=True)
def read(p):return json.loads(p.read_text(encoding='utf-8-sig'))
def write(p,v):p.write_text(json.dumps(v,ensure_ascii=False,indent=2),encoding='utf-8')
def sha(p):
 h=hashlib.sha256()
 with p.open('rb') as f:
  for b in iter(lambda:f.read(1<<20),b''):h.update(b)
 return h.hexdigest()
files=[]
for root,dirs,names in os.walk(W,followlinks=False):
 assert not Path(root).is_symlink();dirs[:]=[n for n in dirs if not (Path(root)/n).is_symlink()]
 for name in sorted(names):
  p=Path(root)/name;assert not p.is_symlink();files.append({'path':p.relative_to(W).as_posix(),'bytes':p.stat().st_size,'sha256':sha(p)})
write(D/'world-before.json',{'world':str(W),'files':files,'fileCount':len(files)})
archive=D/'bloodborne-before-interactions.zip'
if not archive.exists():
 with zipfile.ZipFile(archive,'w',zipfile.ZIP_DEFLATED,compresslevel=3) as z:
  for f in files:z.write(W/f['path'],'bloodborne/'+f['path'])
write(D/'backup.json',{'path':str(archive),'sha256':sha(archive),'bytes':archive.stat().st_size,'fileCount':len(files)})
resources=read(D/'window-resources.json');static=read(R/'bloodborne_dw/static-architecture-v1274.json');target=set(resources['poses']);mapping={int(k):v for k,v in resources['recipeReplacement'].items()};recipes=static['recipes'];poses={r['key']:r for r in static['poses']};changes=[];groups={};direct=[]
for path in sorted((W/'region').glob('r.*.mca')):
 region=w.RegionFile(path.read_bytes());rx,rz=map(int,path.stem.split('.')[1:])
 for lz in range(32):
  for lx in range(32):
   ch=region.get_chunk(lx,lz)
   if ch is None:continue
   root=ch.nbt().root.value
   for sec in root.get('sections',w.Tag(9,[],10)).value:
    bs=sec.value.get('block_states')
    if bs is None:continue
    candidates={};sy=sec.value['Y'].value
    for index,pal in enumerate(bs.value['palette'].value):
     name=pal.value['Name'].value
     if name==resources['oldWindowId']:direct.append({'chunk':[rx*32+lx,rz*32+lz],'section':sy,'palette':index})
     if name.startswith('bloodborne_dw:static_architecture_'):
      props=pal.value['Properties'].value;recipe=int(name.rsplit('_',1)[1])*128+int(props['part'].value)
      if recipe in mapping:candidates[index]=recipe
    if not candidates:continue
    palette,indices=w.section_blocks(sec)
    for cell,index in enumerate(indices):
     if index not in candidates:continue
     recipe=candidates[index];x=(rx*32+lx)*16+(cell&15);y=sy*16+(cell>>8);z=(rz*32+lz)*16+((cell>>4)&15);point=[x,y,z];raw=palette[index].value
     changes.append({'pos':point,'before':{'registryId':raw['Name'].value,'properties':{k:v.value for k,v in raw.get('Properties',w.Tag(10,{})).value.items()}},'afterRecipe':mapping[recipe]})
     for f in recipes[recipe]['fragments']:
      if f['pose'] not in target:continue
      origin=[point[a]+f['origin'][a] for a in range(3)];key=(f['pose'],*origin);groups[key]={'root':origin,'pose':f['pose'],'open':int(poses[f['pose']]['properties']['variant'])==1}
write(D/'window-map-plan.json',{'status':'PREPARED','world':str(W),'windows':list(groups.values()),'changes':changes,'directLegacyPaletteReferences':direct,'counts':{'windows':len(groups),'changedCells':len(changes),'directPaletteReferences':len(direct)}})
write(D/'plan.json',{'status':'IMPLEMENTING','version':'0.1.0-v12.7.4-visual-physics.7','tasks':['fix hit particles and opaque in-wall view','toggle player-only collision in Special','silent Special fallback','clockwise rotation matching schema','separate shutter windows and preserve user map','separate RP public physical bounds from rendering/query bounds'],'windowCount':len(groups),'baselineBackup':str(archive)})
