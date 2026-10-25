"""Append standalone authored window/wall resources; preserve frozen old rows."""
import copy,hashlib,json,math,sys
from pathlib import Path
from PIL import Image
P=Path(__file__).resolve().parents[1];R=P/'src/architecture/resources';B=R/'bloodborne_dw';A=R/'assets/bloodborne_dw';D=P/'interaction-evidence';D.mkdir(exist_ok=True)
read=lambda p:json.loads(p.read_text(encoding='utf-8-sig'))
def write(p,v):p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(v,ensure_ascii=False,separators=(',',':')),encoding='utf-8')
old=read(B/'composite/source_spruce_trapdoor_bottom.json');models=[read(A/'models'/(v['poses']['closed']['parts'][0]['model'].split(':')[1]+'.json')) for v in old['variants']]
window='source_shutter_window';wall='source_shutter_window_surround'
source=Image.open(A/'textures/block/remaining/source/minecraft/block/addon/roof_3.png').convert('RGBA');image=source.copy()
pixels=image.load();before=source.crop((107,41,123,71));assert all(p[3]==255 for p in before.getdata())
for y in range(41,71):
 for x in range(107,123):pixels[x,y]=(*pixels[x,y][:3],80)
texture=A/'textures/interactions/shutter_glass.png';texture.parent.mkdir(exist_ok=True);image.save(texture)
wallvariants=[];windowposes={};wallparts=([3,5,6],[4,5,6,7]);winparts=([0,1,2,4],[0,1,2,3])
def box(a,b):return {'from':a,'to':b}
wallboxes=[box([-16,0,5],[-6,48,7]),box([22,0,5],[32,48,7]),box([-6,0,5],[22,2,7]),box([-6,40,5],[22,48,7])]
glass=copy.deepcopy(models[1]['elements'][8]);glass['faces']['south']=copy.deepcopy(glass['faces']['north']);glass['faces']['south']['uv']=[15.375,5.125,13.375,8.875]
glassmodel={'textures':{'1':'bloodborne_dw:interactions/shutter_glass','particle':'bloodborne_dw:interactions/shutter_glass'},'elements':[glass]};write(A/'models/interactions/shutter_glass.json',glassmodel)
frame=copy.deepcopy(models[0]['elements'][4]);frame['faces']['south']['uv']=[14.25,10.125,11.875,14]
atlas=Image.open(A/'textures/block/remaining/source/minecraft/block/addon/roof_1.png').convert('RGBA');mask=atlas.crop((95,81,114,112));bands=[]
for row in range(mask.height):
 opaque=[x for x in range(mask.width) if mask.getpixel((x,row))[3]>0]
 if opaque:
  left,right=min(opaque)-2,max(opaque)-1;lo,hi=23-row,24-row
  if bands and bands[-1][0]==left and bands[-1][2]==right and bands[-1][1]==hi:bands[-1][1]=lo
  else:bands.append([left,lo,right,hi])
def clip_element(e,lo,hi):
 out=copy.deepcopy(e);a=e['from'];b=e['to'];out['from']=lo;out['to']=hi
 for face,data in out['faces'].items():
  uv=e['faces'][face].get('uv')
  if uv is None:continue
  axis=0 if face in ('north','south','up','down') else 2;reverse=face in ('north','east');vaxis=2 if face in ('up','down') else 1
  u0=(lo[axis]-a[axis])/(b[axis]-a[axis]);u1=(hi[axis]-a[axis])/(b[axis]-a[axis]);v0=(lo[vaxis]-a[vaxis])/(b[vaxis]-a[vaxis]);v1=(hi[vaxis]-a[vaxis])/(b[vaxis]-a[vaxis])
  if reverse:u0,u1=1-u1,1-u0
  if face!='up':v0,v1=1-v1,1-v0
  data['uv']=[uv[0]+(uv[2]-uv[0])*u0,uv[1]+(uv[3]-uv[1])*v0,uv[0]+(uv[2]-uv[0])*u1,uv[1]+(uv[3]-uv[1])*v1]
 return out
def carve(elements):
 # The legacy decoration had a solid rear wall behind the painted pane.
 # Cut the same pixel-defined arched aperture through both wall faces.
 for x0,y0,x1,y1 in bands:
  result=[]
  for e in elements:
   a=e['from'];b=e['to'];lx=max(a[0],x0);rx=min(b[0],x1);by=max(a[1],y0);ty=min(b[1],y1)
   if lx>=rx or by>=ty:result.append(e);continue
   for lo,hi in (([a[0],a[1],a[2]],[lx,b[1],b[2]]),([rx,a[1],a[2]],[b[0],b[1],b[2]]),([lx,a[1],a[2]],[rx,by,b[2]]),([lx,ty,a[2]],[rx,b[1],b[2]])):
    if all(lo[i]<hi[i] for i in range(3)):result.append(clip_element(e,lo,hi))
  elements=result
 return elements
for i,model in enumerate(models):
 surround=copy.deepcopy(model);surround['elements']=carve([model['elements'][n] for n in wallparts[i]])
 sash=copy.deepcopy(model);sash['elements']=[model['elements'][n] for n in (0,1,2)]+[frame]
 write(A/f'models/interactions/shutter_wall_{i}.json',surround);write(A/f'models/interactions/shutter_{i}.json',sash)
 wallpose={'parts':[{'model':f'bloodborne_dw:interactions/shutter_wall_{i}','offset':[0,16,0]}],'collision':wallboxes,'selection':wallboxes}
 wallvariants.append({'poses':{'closed':wallpose}})
 # Thin authored leaves receive 1/4-pixel physical thickness. Conservative
 # bounds follow their source rotations; the glass and sill remain fixed.
 collisions=[]
 for n in (0,1,2):
  e=model['elements'][n];lo=e['from'][:];hi=e['to'][:]
  for axis in range(3):
   if lo[axis]==hi[axis]:lo[axis]-=.125;hi[axis]+=.125
  rot=e.get('rotation',{});origin=rot.get('origin',[0,0,0]);angle=math.radians(rot.get('angle',0));c=math.cos(angle);s=math.sin(angle);points=[]
  for bits in range(8):
   q=[hi[a] if bits&(1<<a) else lo[a] for a in range(3)];x=q[0]-origin[0];z=q[2]-origin[2]
   if rot.get('axis')=='y':q[0]=origin[0]+x*c+z*s;q[2]=origin[2]-x*s+z*c
   q[1]+=16;points.append(q)
  collisions.append(box([min(p[a] for p in points) for a in range(3)],[max(p[a] for p in points) for a in range(3)]))
 collisions.append(box([0,8,5.875],[16,38,6.125]))
 # Arched frame, two uprights plus the small cap. Avoid a solid rectangular
 # ghost around the opened shutters.
 collisions.extend([box([-2,9,4.75],[0,40,7]),box([16,9,4.75],[17,40,7]),box([0,38,4.75],[16,40,7])])
 windowposes['open' if i else 'closed']={'parts':[{'model':f'bloodborne_dw:interactions/shutter_{i}','offset':[0,16,0]},{'model':'bloodborne_dw:interactions/shutter_glass','offset':[0,16,0]}],'collision':collisions,'selection':collisions}
common={'schemaVersion':1,'units':16,'simplePhysicalCollision':True,'support':{'required':False},'essentialMask':[[0,0,0]]}
write(B/f'composite/{window}.json',{**common,'id':'bloodborne_dw:'+window,'displayName':'Узкое окно со ставнями','openable':True,'layer':'translucent','variants':[{'poses':windowposes}]})
write(B/f'composite/{wall}.json',{**common,'id':'bloodborne_dw:'+wall,'displayName':'Стена вокруг узкого окна','openable':False,'variants':wallvariants})
catalog=read(B/'remaining-catalogue.json')
for row in catalog['objects']:
 if row['path']=='source_spruce_trapdoor_bottom':row['conversionOnly']=True
catalog['objects']=[v for v in catalog['objects'] if v['path'] not in (window,wall)]
for name,title,count in ((window,'Узкое окно со ставнями',1),(wall,'Стена вокруг узкого окна',2)):
 catalog['objects'].append({'path':name,'collisionPolicy':'authored-exact','displayName':title,'variants':[{'variant':i,'name':title+(' · вид '+str(i+1) if count>1 else '')} for i in range(count)]})
 write(A/f'models/block/{name}_empty.json',{'textures':{'particle':'minecraft:block/stone'},'elements':[]});write(A/f'models/item/{name}.json',{'parent':'builtin/entity'});write(A/f'blockstates/{name}.json',{'variants':{'':{'model':f'bloodborne_dw:block/{name}_empty'}}})
write(B/'remaining-catalogue.json',catalog)
# No old descriptor, static row, mesh ID or texture changes. Append replacements
# with finite removal closures; this also keeps existing saved palettes valid.
staticpath=B/'static-architecture-v1274.json';static=read(staticpath)
if (D/'window-resources.json').exists():
 assert not (D/'map-apply.json').exists(),'Do not regenerate resources after applying the map'
 prior=read(D/'window-resources.json');static['poses']=static['poses'][:prior['originalPoseCount']];static['recipes']=static['recipes'][:prior['originalRecipeCount']]
originalposes=copy.deepcopy(static['poses']);originalrecipes=copy.deepcopy(static['recipes']);newposes={};posekeys=[]
for row in originalposes:
 if row['registryId']!='bloodborne_dw:source_spruce_trapdoor_bottom':continue
 nxt=copy.deepcopy(row);nxt['registryId']='bloodborne_dw:'+wall;nxt['key']='interaction_wall_'+row['key'];newposes[row['key']]=nxt['key'];posekeys.append(row['key']);static['poses'].append(nxt)
mapping={};active=set()
posemap={p['key']:p for p in originalposes}
def wall_visible(fragment):
 pose=posemap[fragment['pose']];shift=pose['shift'];mount=pose['mountY'];r=int(pose['properties']['rotation']);a=r*math.pi/4;c=math.cos(a);s=math.sin(a);points=[]
 # SourceShift is local and turns with the same source orientation.
 for bits in range(8):
  x=([-1,2][bool(bits&1)]-.5)+shift[0];z=([5/16,7/16][bool(bits&4)]-.5)+shift[2]
  points.append([.5+c*x-s*z,[0,3][bool(bits&2)]+shift[1]+mount,.5+s*x+c*z])
 off=[-v for v in fragment['origin']]
 return all(max(p[a] for p in points)>off[a]+1e-8 and min(p[a] for p in points)<off[a]+1-1e-8 for a in range(3))
def replacement(i):
 if i<0:return -1
 if i in mapping:return mapping[i]
 row=originalrecipes[i]
 if not any(f['pose'] in newposes for f in row['fragments']):mapping[i]=i;return i
 assert i not in active,'Cyclic old removal closure';active.add(i);nxt=copy.deepcopy(row);kept=[]
 for n,f in enumerate(nxt['fragments']):
  if f['pose'] in newposes:
   if not wall_visible(f):continue
   f['pose']=newposes[f['pose']]
  kept.append(n)
 if not kept:mapping[i]=-1;active.remove(i);return -1
 if not any(nxt['fragments'][n]['pose'] in newposes.values() for n in kept):
  dropped=next(n for n in range(len(row['fragments'])) if n not in kept);mapping[i]=replacement(row['remove'][dropped]);active.remove(i);return mapping[i]
 nxt['fragments']=[nxt['fragments'][n] for n in kept];nxt['resident']=kept.index(row['resident']) if row['resident'] in kept else -1
 nxt['key']='interaction_wall_'+row['key'];nxt['physicalFromPoses']=True;nxt['remove']=[replacement(row['remove'][n]) for n in kept];mapping[i]=len(static['recipes']);static['recipes'].append(nxt);active.remove(i);return mapping[i]
for i in range(len(originalrecipes)):replacement(i)
assert static['poses'][:len(originalposes)]==originalposes and static['recipes'][:len(originalrecipes)]==originalrecipes
write(staticpath,static)
write(D/'window-resources.json',{'status':'APPENDED','oldWindowId':old['id'],'windowId':'bloodborne_dw:'+window,'wallId':'bloodborne_dw:'+wall,'poses':posekeys,'recipeReplacement':{str(k):v for k,v in mapping.items() if k!=v},'originalPoseCount':len(originalposes),'originalRecipeCount':len(originalrecipes),'glassOriginalAlpha':[255],'glassNewAlpha':80,'glassChangedPixels':480,'originalResourcesUnmodified':True})
