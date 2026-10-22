"""Offline, bounded district proxy from native palette cells and finite shapes.

No Minecraft, chunk migration, heightmap filling, world writes, or DH internals.
The pilot approximates shared selection boxes on a half-block grid; it is not
a complete visual-model voxelizer. All limitations are emitted in its report.
"""
import argparse,gzip,hashlib,io,json,math,struct,sys,traceback,zipfile
from collections import Counter,defaultdict
from functools import lru_cache
from pathlib import Path
import numpy as np
from PIL import Image
P=Path(__file__).absolute().parents[2]
sys.path.insert(0,str(Path(__file__).absolute().parent))
from world_io import RegionFile,section_blocks,read_nbt
SCALE=2;YMIN=-64;YMAX=320
def sha(data):return hashlib.sha256(data).hexdigest()
def main():
 ap=argparse.ArgumentParser();ap.add_argument('--world',type=Path,default=P/'build/native-city-mesh/fixtures/baseline/world');ap.add_argument('--out',type=Path,default=P/'build/dh-city-pilot/tower-scene');ap.add_argument('--bounds',default='560,-176,688,-48')
 ap.add_argument('--materials',type=Path,default=Path(__file__).absolute().parent/'static-pose-materials.json')
 ap.add_argument('--resource-pack',type=Path,default=P/'build/runtime-client-city-mesh-baseline/resourcepacks/bbmc_v15_resource.zip')
 ap.add_argument('--minecraft-jar',type=Path,default=P/'build/runtime-client-city-mesh-baseline/minecraft-client.jar')
 version=dict(line.split('=',1) for line in (P/'gradle.properties').read_text().splitlines() if '=' in line)['mod_version']
 ap.add_argument('--mod-jar',type=Path,default=P/'build/libs'/('dreamwalker-bb-fabric-1.20.1-'+version+'.jar'));a=ap.parse_args()
 x0,z0,x1,z1=map(int,a.bounds.split(','));assert all(x%16==0 for x in [x0,z0,x1,z1]) and 0<x1-x0<=128 and 0<z1-z0<=128
 out=a.out;out.mkdir(parents=True,exist_ok=False)
 catalog_bytes=(P/'src/architecture/resources/bloodborne_dw/static-architecture-v1274.json').read_bytes();catalog=json.loads(catalog_bytes)
 materials=json.loads(a.materials.read_text(encoding='utf8'));assert materials['catalogSha256']==sha(catalog_bytes),'Material table/catalog mismatch'
 poses={r['poseKey']:r for r in materials['poses']}
 source_variants={}
 for row in json.loads((P/'src/architecture/resources/bloodborne_dw/remaining-catalogue.json').read_text(encoding='utf8'))['objects']:
  for variant in row['variants']:source_variants[('bloodborne_dw:'+row['path'],int(variant['variant']))]=variant.get('sourceModels',[])
 packpath=a.resource_pack;pack=zipfile.ZipFile(packpath)
 vanilla=zipfile.ZipFile(a.minecraft_jar)
 modpath=a.mod_jar;modassets=zipfile.ZipFile(modpath)
 missing=Counter();fallbacks=Counter();counts=Counter();files=[];tile_records=[];mask_cache={};colors={};color_report={}
 def asset(path):
  for archive in (pack,modassets,vanilla):
   try:return archive.read(path)
   except KeyError:pass
  missing[path]+=1;return None
 @lru_cache(None)
 def model(name):
  ns,path=name.split(':',1) if ':' in name else ('minecraft',name)
  data=asset('assets/'+ns+'/models/'+path+'.json')
  if data is None:return {'textures':{},'elements':[]}
  doc=json.loads(data);parent=model(doc['parent']) if doc.get('parent') and not doc['parent'].startswith('builtin/') else {}
  return {**parent,**doc,'textures':{**parent.get('textures',{}),**doc.get('textures',{})}}
 @lru_cache(None)
 def pixels(name):
  ns,path=name.split(':',1) if ':' in name else ('minecraft',name)
  data=asset('assets/'+ns+'/textures/'+path+'.png')
  return np.array(Image.open(io.BytesIO(data)).convert('RGBA')) if data else None
 def texture_color(name,uv=None):
  data=pixels(name)
  if data is None:return None
  height,width,_=data.shape
  if uv:
   u0,v0,u1,v1=uv;xs=sorted((max(0,min(width,int(math.floor(u0/16*width)))),max(0,min(width,int(math.ceil(u1/16*width))))));ys=sorted((max(0,min(height,int(math.floor(v0/16*height)))),max(0,min(height,int(math.ceil(v1/16*height))))))
   if xs[1]>xs[0] and ys[1]>ys[0]:data=data[ys[0]:ys[1],xs[0]:xs[1]]
  alpha=data[:,:,3].astype(np.float64);weight=alpha.sum()
  return (data[:,:,:3]*alpha[:,:,None]).sum(axis=(0,1))/weight if weight else None
 def pose_color(key):
  if key in colors:return colors[key]
  pose=poses[key];total=np.zeros(3);weight=0.;used=[]
  for name in pose['sourcePartModels']:
   m=model(name);textures=m.get('textures',{})
   for element in m.get('elements',[]):
    lo=element['from'];hi=element['to'];dimensions=[abs(hi[i]-lo[i]) for i in range(3)]
    for face,row in element.get('faces',{}).items():
     texture=row['texture'];visited=set()
     while texture.startswith('#') and texture not in visited:visited.add(texture);texture=textures.get(texture[1:],'')
     if not texture or texture.startswith('#'):continue
     default_uv={'down':[lo[0],16-hi[2],hi[0],16-lo[2]],'up':[lo[0],lo[2],hi[0],hi[2]],'north':[16-hi[0],16-hi[1],16-lo[0],16-lo[1]],'south':[lo[0],16-hi[1],hi[0],16-lo[1]],'west':[lo[2],16-hi[1],hi[2],16-lo[1]],'east':[16-hi[2],16-hi[1],16-lo[2],16-lo[1]]}
     rgb=texture_color(texture,row.get('uv',default_uv[face]));axis={'west':0,'east':0,'up':1,'down':1,'north':2,'south':2}[face];area=np.prod([dimensions[i] for i in range(3) if i!=axis])
     if rgb is not None and area>0:total+=rgb*area;weight+=area;used.append(texture)
  if not weight:
   values=[texture_color(name) for name in pose['textures']];values=[x for x in values if x is not None]
   rgb=np.mean(values,axis=0) if values else np.array([100,100,100]);fallbacks['poseTextureFallback']+=1
  else:rgb=total/weight
  quantized=np.clip(np.round(rgb/8)*8,0,255).astype(int);code=(255<<24)|(int(quantized[0])<<16)|(int(quantized[1])<<8)|int(quantized[2]);colors[key]=code
  color_report[key]={'sourceRgb':[round(float(x),3) for x in rgb],'lodRgb':quantized.tolist(),'sourceTextures':sorted(set(used or pose['textures']))};return code
 def template(name,props):
  key=(name,tuple(sorted(props.items())))
  if key in mask_cache:return mask_cache[key]
  if name.startswith('bloodborne_dw:static_architecture_'):
   index=int(name.rsplit('_',1)[1])*128+int(props.get('part','0'));recipe=catalog['recipes'][min(index,len(catalog['recipes'])-1)];boxes=recipe['selection'];keys=[f['pose'] for f in recipe['fragments']]
   rgb=np.array([[(pose_color(k)>>n)&255 for n in (16,8,0)] for k in keys]).mean(axis=0).round().astype(int);color=(255<<24)|(int(rgb[0])<<16)|(int(rgb[1])<<8)|int(rgb[2]);kind='static'
  elif name in ['minecraft:air','minecraft:cave_air','minecraft:void_air','minecraft:structure_void','minecraft:barrier','minecraft:light','bloodborne_dw:composite_cell']:
   mask_cache[key]=(None,0,'invisible');return mask_cache[key]
  elif name.startswith('bloodborne_dw:'):
   fallbacks['omittedDynamic:'+name]+=1;mask_cache[key]=(None,0,'dynamic');return mask_cache[key]
  else:
   ns,path=name.split(':',1);rgb=texture_color(ns+':block/'+path)
   if rgb is None:rgb=np.array([90,95,90]);fallbacks['nativeColor:'+name]+=1
   rgb=np.clip(np.round(rgb/8)*8,0,255).astype(int);color=(255<<24)|(int(rgb[0])<<16)|(int(rgb[1])<<8)|int(rgb[2]);boxes=[[0,0,0,1,1,1]];kind='nativeApproximation'
  mask=np.zeros((SCALE,SCALE,SCALE),dtype=bool)
  for box in boxes:
   a,b,c,d,e,f=box
   # Conservative subcell intersection retains thin trim. It can thicken a
   # model by <0.5 block; it never fills the whole height below a roof.
   lo=[max(0,min(SCALE,math.floor(q*SCALE+1e-6))) for q in (a,b,c)];hi=[max(0,min(SCALE,math.ceil(q*SCALE-1e-6))) for q in (d,e,f)]
   mask[lo[0]:hi[0],lo[1]:hi[1],lo[2]:hi[2]]=True
  mask_cache[key]=(mask,color,kind);return mask_cache[key]
 def owner_color(data):
  row=data.value;name=row['id'].value;props={k:v.value for k,v in row['properties'].value.items()};variant=int(props.get('art_bank','0'))*16+int(props.get('variant','0'));key='owner:'+name+':'+str(variant)
  if key not in poses:
   models=source_variants.get((name,variant),[])
   if not models:
    matches=[p for p in poses.values() if p.get('registryId')==name]
    models=matches[0]['sourcePartModels'] if matches else []
    fallbacks['ownerColor:'+name]+=1
   poses[key]={'registryId':name,'sourcePartModels':models,'textures':[]}
  return pose_color(key)
 def saved_owners(grid,cx,cz):
  path=a.world/'data/bloodborne_dw_owner_shards'/f'owners-{cx}.{cz}.nbt'
  if not path.exists():return
  raw_bytes=path.read_bytes();files.append({'file':str(path),'sha256':sha(raw_bytes)})
  doc=read_nbt(path).root.value;assert doc['Schema'].value==1 and doc['ChunkX'].value==cx and doc['ChunkZ'].value==cz
  dictionary=bool(doc.get('Dictionary').value) if doc.get('Dictionary') else False
  datas=doc['dataTable'].value if dictionary else [];shapes=doc['shapeTable'].value if dictionary else [];data_colors={}
  for value in doc['cells'].value:
   row=value.value;x,y,z=row['pos'].value;assert x>>4==cx and z>>4==cz
   if not YMIN<=y<YMAX:continue
   if dictionary:
    refs=[tuple(r.value) for r in row['refs'].value]
    contributions=[]
    for _,d,s in refs:
     if d not in data_colors:data_colors[d]=owner_color(datas[d])
     contributions.append((shapes[s].value['selection'].value,data_colors[d]))
   else:contributions=[(entry.value['selection'].value,owner_color(entry.value['rootData'])) for entry in row['owners'].value]
   counts['savedOwnerCells']+=1
   for boxes,color in contributions:
    counts['savedOwnerContributions']+=1
    for box in boxes:
     a0,b0,c0,d0,e0,f0=[v.value for v in box.value]
     lo=[max(0,min(SCALE,math.floor(q*SCALE+1e-6))) for q in (a0,b0,c0)];hi=[max(0,min(SCALE,math.ceil(q*SCALE-1e-6))) for q in (d0,e0,f0)]
     xa=(x&15)*SCALE;ya=(y-YMIN)*SCALE;za=(z&15)*SCALE
     grid[xa+lo[0]:xa+hi[0],ya+lo[1]:ya+hi[1],za+lo[2]:za+hi[2]]=color
 def merge(grid):
  # Run-length Y segments, then merge identical ranges along X and Z.
  rows=defaultdict(list)
  for x in range(32):
   for z in range(32):
    col=grid[x,:,z];changes=np.flatnonzero(np.r_[True,col[1:]!=col[:-1],True])
    for lo,hi in zip(changes[:-1],changes[1:]):
     color=int(col[lo])
     if color:rows[(z,int(lo),int(hi),color)].append(x)
  spans=defaultdict(list)
  for (z,y0,y1,color),xs in rows.items():
   start=last=xs[0]
   for x in xs[1:]+[999]:
    if x!=last+1:spans[(start,last+1,y0,y1,color)].append(z);start=x
    last=x
  boxes=[]
  for (xa,xb,ya,yb,color),zs in spans.items():
   start=last=zs[0]
   for z in zs[1:]+[999]:
    if z!=last+1:boxes.append((xa/SCALE,ya/SCALE+YMIN,start/SCALE,xb/SCALE,yb/SCALE+YMIN,(last+1)/SCALE,color));start=z
    last=z
  return boxes
 raw=io.BytesIO();raw.write(struct.pack('>4sI',b'DWC1',((x1-x0)//16)*((z1-z0)//16)))
 regions={};total_voxels=0;total_boxes=0;gap_columns=0
 for cx in range(x0//16,x1//16):
  for cz in range(z0//16,z1//16):
   path=a.world/'region'/f'r.{cx>>5}.{cz>>5}.mca'
   if path not in regions:regions[path]=RegionFile.open(path);files.append({'file':str(path),'sha256':sha(path.read_bytes())})
   chunk=regions[path].get_chunk(cx&31,cz&31);assert chunk is not None,(cx,cz)
   root=chunk.nbt().root.value;grid=np.zeros((32,(YMAX-YMIN)*SCALE,32),dtype=np.uint32)
   for section in root['sections'].value:
    values=section_blocks(section)
    if values is None:continue
    palette,indices=values;templates=[]
    for entry in palette:
     row=entry.value;props={k:v.value for k,v in row['Properties'].value.items()} if 'Properties' in row else {};templates.append(template(row['Name'].value,props))
    sy=section.value['Y'].value*16
    for i,p in enumerate(indices):
     mask,color,kind=templates[p]
     if mask is None:continue
     counts[kind]+=1;x=i&15;z=(i>>4)&15;y=sy+(i>>8)
     if not YMIN<=y<YMAX:continue
     view=grid[x*SCALE:(x+1)*SCALE,(y-YMIN)*SCALE:(y+1-YMIN)*SCALE,z*SCALE:(z+1)*SCALE];view[mask]=color
   saved_owners(grid,cx,cz)
   occupied=grid!=0;total_voxels+=int(occupied.sum())
   for x in range(32):
    for z in range(32):
     ys=np.flatnonzero(occupied[x,:,z])
     if len(ys)>1 and np.any(~occupied[x,ys[0]:ys[-1]+1,z]):gap_columns+=1
   boxes=merge(grid);total_boxes+=len(boxes);assert len(boxes)<=32768 and total_boxes<=250000
   reconstructed=np.zeros_like(grid)
   for xa,ya,za,xb,yb,zb,color in boxes:reconstructed[round(xa*SCALE):round(xb*SCALE),round((ya-YMIN)*SCALE):round((yb-YMIN)*SCALE),round(za*SCALE):round(zb*SCALE)]=color
   assert np.array_equal(reconstructed,grid),'Merged box export changed occupancy or colors'
   raw.write(struct.pack('>iii',cx,cz,len(boxes)))
   for box in boxes:raw.write(struct.pack('>6fI',*box))
   tile_records.append({'chunkX':cx,'chunkZ':cz,'boxes':len(boxes),'occupiedVoxels':int(occupied.sum())})
   (out/'progress.json').write_text(json.dumps({'tiles':len(tile_records),'boxes':total_boxes}))
 compressed=gzip.compress(raw.getvalue(),compresslevel=6,mtime=0);(out/'city-boxes.bin.gz').write_bytes(compressed)
 report={'schema':1,'worldDirectory':str(a.world.absolute()),'dataSha256':sha(compressed),'catalogSha256':sha(catalog_bytes),'readOnlyDhTerrain':True,'bounds':[x0,YMIN,z0,x1,YMAX,z1],'voxelWidth':1/SCALE,'tiles':tile_records,'totalBoxes':total_boxes,'rawBytes':raw.tell(),'compressedBytes':len(compressed),'occupiedVoxels':total_voxels,'columnsWithInteriorAirGaps':gap_columns,'cellCounts':dict(counts),'fallbacks':dict(fallbacks),'missingAssets':dict(missing),'sourceRegionHashes':files,'resourcePackSha256':sha(packpath.read_bytes()),'exporterSha256':sha(Path(__file__).read_bytes()),'limitations':['Pilot only: shared and saved-owner selection boxes approximate visual model geometry on a 0.5 block grid.','Native non-air cells are full-cube approximations. Sharded saved dynamic owner geometry is frozen at export time, not animated or interactive.','Per-pose UV/face-area-weighted texture averages, rounded to 8 RGB levels, merged within each cell recipe. No texture detail, tint or face-specific color. Unresolved owner colors are listed.','Static frozen district; exports must be rebuilt after edits. Read-only DH terrain is enabled only while this explicit pilot scene is bound.','No Minecraft chunks are instantiated to build or display this proxy.']}
 (out/'scene.json').write_text(json.dumps(report,indent=2)+'\n');(out/'colors.json').write_text(json.dumps(color_report,indent=2)+'\n')
if __name__=='__main__':
 try:main()
 except Exception:
  target=P/'build/dh-city-pilot/export-error.txt';target.parent.mkdir(parents=True,exist_ok=True);target.write_text(traceback.format_exc());raise
