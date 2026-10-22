import hashlib,json,os,sys,zipfile,math
from pathlib import Path
PROJECT=Path(__file__).resolve().parents[2]
ROOT=PROJECT.parent
RES=PROJECT/'src/architecture/resources'
OUT=ROOT/'research/block-fixes-20261020'
DELIVERY=ROOT/'deliverables/block-fixes-20261020'
BASE=ROOT/'deliverables/streaming-7-20261020/game/dreamwalker-bb-materials.1-streaming.7.jar'
SOURCE_WORLD=ROOT/'deliverables/collision-streaming-20261018/map/bloodborne'
JAVA_HOME=Path(r'C:\Users\vakir\AppData\Roaming\.minecraft\runtime\java-runtime-gamma\windows\java-runtime-gamma')
JAVA=JAVA_HOME/'bin/java.exe'
OUT.mkdir(parents=True,exist_ok=True)
def read(p):return json.loads(p.read_text(encoding='utf-8-sig'))
def write(p,v):
 p.parent.mkdir(parents=True,exist_ok=True)
 p.write_text(json.dumps(v,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
def log_to(name):
 sys.stdout=(OUT/(name+'.log')).open('w',encoding='utf8',buffering=1)
 sys.stderr=sys.stdout
def sha(p):
 h=hashlib.sha256()
 with p.open('rb') as f:
  for b in iter(lambda:f.read(1048576),b''):h.update(b)
 return h.hexdigest()
def ordinary(p):
 if getattr(p.lstat(),'st_file_attributes',0)&1024 or p.is_symlink():raise ValueError('Reparse point refused: '+str(p))
 return p
def files(p):
 for q in sorted(ordinary(p).iterdir()):
  ordinary(q)
  if q.is_dir():yield from files(q)
  elif q.is_file():yield q
def model_path(id):
 ns,name=id.split(':',1)
 return RES/'assets'/ns/'models'/(name+'.json')
def model(id):
 d=read(model_path(id))
 if 'parent' in d and model_path(d['parent']).exists():
  p=model(d['parent']);p.update({k:v for k,v in d.items() if k!='textures'});p['textures']={**p.get('textures',{}),**d.get('textures',{})};return p
 return d
def turn(p,angle,axis,pivot):
 a=math.radians(angle);c=math.cos(a);s=math.sin(a);v=[p[i]-pivot[i] for i in range(3)]
 i,j={'x':(1,2),'y':(2,0),'z':(0,1)}[axis]
 v[i],v[j]=c*v[i]-s*v[j],s*v[i]+c*v[j]
 return [v[k]+pivot[k] for k in range(3)]
def bounds(parts):
 points=[];tops=[]
 for part in parts:
  d=model(part['model'])
  for e in d.get('elements',[]):
   pp=[]
   for x in [e['from'][0],e['to'][0]]:
    for y in [e['from'][1],e['to'][1]]:
     for z in [e['from'][2],e['to'][2]]:
      p=[x,y,z]
      if e.get('rotation',{}).get('angle',0):p=turn(p,e['rotation']['angle'],e['rotation']['axis'],e['rotation']['origin'])
      if part.get('yaw'):p=turn(p,-part['yaw'],'y',part.get('pivot',[8,8,8]))
      if part.get('pitch'):p=turn(p,part['pitch'],'x',part.get('pivot',[8,8,8]))
      p=[p[i]+part.get('offset',[0,0,0])[i] for i in range(3)]
      pp.append(p);points.append(p)
   if 'up' in e.get('faces',{}):
    f=e['faces']['up'];texture=f['texture'];textures=d.get('textures',{})
    while texture.startswith('#'):texture=textures[texture[1:]]
    tops.append({'area':abs((e['to'][0]-e['from'][0])*(e['to'][2]-e['from'][2])), 'texture':texture,'face':f,'from':e['from'],'to':e['to'],'model':part['model'],'element':e,'part':part})
 if not points:return None,None
 return [[min(p[i] for p in points) for i in range(3)],[max(p[i] for p in points) for i in range(3)]],max(tops,key=lambda t:t['area']) if tops else None
