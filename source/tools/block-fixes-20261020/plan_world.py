from common import *
from collections import Counter,defaultdict
sys.path.insert(0,str(PROJECT/'tools'))
from world_io import *

def source_variant(p):return int(p['properties'].get('variant','0'))+16*int(p['properties'].get('art_bank','0'))
def state(entry):
 d=compound(entry);return d['Name'].value,{k:v.value for k,v in d.get('Properties',Tag(10,{})).value.items()}
def index(id,props):return int(id.rsplit('_',1)[1])*128+int(props.get('part','0'))
def main():
 log_to('plan-world')
 selected=read(OUT/'selected-materials.json');types={(t['registryId'],t['variant']):t for t in selected['slabs']};native=defaultdict(list)
 for m in selected['materials']:native[m['source'],m['variant']].append(m)
 static=read(RES/'bloodborne_dw/static-architecture-v1274.json');poses={p['key']:p for p in static['poses']}
 targetposes={p['key'] for p in poses.values() if (p['registryId'],source_variant(p)) in types}
 remap={}
 for i,r in enumerate(static['recipes']):
  ri=i
  while ri>=0:
   rr=static['recipes'][ri];j=next((j for j,f in enumerate(rr['fragments']) if f['pose'] in targetposes),None)
   if j is None:break
   ri=rr['remove'][j]
  if ri!=i:remap[str(i)]=ri
 write(OUT/'static-slab-remap.json',remap)
 audit=read(OUT/'world-audit.json');components={tuple(c) for c in audit['slabComponents'] if c[-1] in targetposes}
 # The patterned manhole has a raised rim and was deliberately absent from
 # the general zero-thickness audit. Discover its exact native states here.
 missingposes={key for key in targetposes if poses[key]['registryId'] in ['bloodborne_dw:source_slabs_stone_slab_manwhole','bloodborne_dw:source_slabs_stone_slab_manwhole_top']}
 relevant={i for i,r in enumerate(static['recipes']) if any(f['pose'] in missingposes for f in r['fragments'])}
 if relevant:
  for path in sorted(SOURCE_WORLD.rglob('*.mca')):
   if path.parent.name!='region':continue
   region=RegionFile.open(path);dim=str(path.parent.parent.relative_to(SOURCE_WORLD))
   for c in region.chunks():
    root=compound(c.nbt().root);cx=root['xPos'].value;cz=root['zPos'].value
    for sec in root.get('sections',Tag(9,[],10)).value:
     bs=section_blocks(sec)
     if not bs:continue
     palette,indices=bs;chosen={}
     for j,entry in enumerate(palette):
      id,props=state(entry)
      if id.startswith('bloodborne_dw:static_architecture_') and index(id,props) in relevant:chosen[j]=[f for f in static['recipes'][index(id,props)]['fragments'] if f['pose'] in missingposes]
     if not chosen:continue
     sy=compound(sec)['Y'].value
     for at,pi in enumerate(indices):
      for f in chosen.get(pi,[]):components.add((dim,cx*16+(at&15)+f['origin'][0],sy*16+(at>>8)+f['origin'][1],cz*16+((at>>4)&15)+f['origin'][2],f['pose']))
   print('FORCED_SCAN',path.relative_to(SOURCE_WORLD),flush=True)
 placements={};duplicates=0;odd=[]
 for dim,x,y,z,key in sorted(components):
  p=poses[key];typ=types[p['registryId'],source_variant(p)];yaw=int(p['properties'].get('rotation','0'));angle=math.pi*yaw/4;c=math.cos(angle);s=math.sin(angle)
  if yaw%2:odd.append([dim,x,y,z,key])
  sx,sy,sz=p['shift'];sx,sz=c*sx-s*sz,s*sx+c*sz
  top=typ['bounds'][1][1]/16+sy+p.get('mountY',0);top=round(top*2)/2
  yy=math.floor(top-1e-6);slabtype='bottom' if abs(top-yy-.5)<1e-6 else 'top'
  for m in native[p['registryId'],source_variant(p)]:
   tx,tz=m['cell'];cx=.5+c*tx-s*tz+sx;cz=.5+s*tx+c*tz+sz
   X=x+round(cx-.5);Z=z+round(cz-.5);pos=(dim,X,y+yy,Z)
   priority=abs(tx)+abs(tz)
   row={'position':list(pos),'id':m['id'],'type':slabtype,'facing':['north','east','south','west'][round(yaw/2)%4],'component':[dim,x,y,z,key],'priority':priority}
   if pos in placements:duplicates+=1
   if pos not in placements or priority<placements[pos]['priority']:placements[pos]=row
 byregion=defaultdict(list)
 for pos in placements:dim,x,y,z=pos;byregion[dim,x>>9,z>>9].append(pos)
 categories=Counter();conflicts=Counter();samples=[]
 for (dim,rx,rz),positions in sorted(byregion.items()):
  region=RegionFile.open(SOURCE_WORLD/dim/'region'/f'r.{rx}.{rz}.mca');bychunk=defaultdict(list)
  for pos in positions:bychunk[(pos[1]>>4,pos[3]>>4)].append(pos)
  for (cx,cz),pp in bychunk.items():
   chunk=region.get_chunk(cx&31,cz&31);assert chunk is not None
   root=compound(chunk.nbt().root);secs={compound(s)['Y'].value:section_blocks(s) for s in root['sections'].value}
   for pos in pp:
    _,x,y,z=pos;bs=secs.get(y>>4);id,props=('minecraft:air',{}) if bs is None else state(bs[0][bs[1][((y&15)<<8)|((z&15)<<4)|(x&15)]])
    old=id
    if 'source_invisible' in id:id='minecraft:air'
    if id.startswith('bloodborne_dw:static_architecture_'):
     ri=index(id,props);new=remap.get(str(ri),ri)
     if new<0:id='minecraft:air'
     else:
      r=static['recipes'][new];id='static';isfull=r['collision']==[[0.0,0.0,0.0,1.0,1.0,1.0]]
      placements[pos]['remainingRecipe']=new
      kind='static-full' if isfull else 'static-partial'
      descriptions=','.join(poses[f['pose']]['registryId'] for f in r['fragments'])
      conflicts[kind+' '+descriptions]+=1
      if len(samples)<40:samples.append({'position':pos,'recipe':new,'collision':r['collision'],'fragments':r['fragments']})
    if id=='minecraft:air':categories['air_after_removal']+=1
    elif id=='static':categories[kind]+=1
    else:categories[id]+=1;conflicts[id]+=1
    placements[pos]['old']=old
  print('LOOKUP',dim,rx,rz,flush=True)
 write(OUT/'slab-placements.json',list(placements.values()))
 write(OUT/'world-plan.json',{'components':len(components),'placements':len(placements),'duplicates':duplicates,'oddRotationComponents':odd,'destinations':dict(categories),'conflicts':dict(conflicts),'samples':samples,'targetPoses':sorted(targetposes),'residentConflicts':sum(1 for row in placements.values() if row.get('remainingRecipe',-1)>=0 and static['recipes'][row['remainingRecipe']]['resident']>=0)})
 print('PLAN',len(components),len(placements),dict(categories));print('CONFLICTS',conflicts.most_common(25));print('PASS')
if __name__=='__main__':main()
