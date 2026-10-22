"""Textured orthographic inspection of candidate source meshes; no game/world."""
import functools, json, math
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw
P=Path(__file__).resolve().parents[2]
R=P/'src/architecture/resources'
H=Path(__file__).resolve().parent
O=P.parent/'research/statue-previews';O.mkdir(parents=True,exist_ok=True)
manifest=json.loads((H/'statue-ids.json').read_text(encoding='utf-8'))
catalog=json.loads((R/'bloodborne_dw/remaining-catalogue.json').read_text(encoding='utf-8'))
source_by_id={'bloodborne_dw:'+r['path']:r for r in catalog['objects']}
@functools.lru_cache(None)
def model(mid):
    ns,path=mid.split(':',1);p=R/'assets'/ns/'models'/(path+'.json')
    d=json.loads(p.read_text(encoding='utf-8'))
    if d.get('parent'):
        try:
            parent=model(d['parent']);d={'elements':parent.get('elements',[]),**parent,**d,'textures':{**parent.get('textures',{}),**d.get('textures',{})}}
        except FileNotFoundError:pass
    return d
@functools.lru_cache(None)
def texture(tid):
    ns,path=tid.split(':',1)
    return np.asarray(Image.open(R/'assets'/ns/'textures'/(path+'.png')).convert('RGBA'))
def quads(mid):
    d=model(mid);out=[]
    for e in d.get('elements',[]):
        a=np.asarray(e['from'],float);b=np.asarray(e['to'],float)
        corners=np.array([[a[0],a[1],a[2]],[b[0],a[1],a[2]],[b[0],b[1],a[2]],[a[0],b[1],a[2]],[a[0],a[1],b[2]],[b[0],a[1],b[2]],[b[0],b[1],b[2]],[a[0],b[1],b[2]]])
        if e.get('rotation'):
            rot=e['rotation'];axis={'x':0,'y':1,'z':2}[rot['axis']];v=np.zeros(3);v[axis]=1
            angle=math.radians(rot['angle']);origin=np.asarray(rot['origin']);p=corners-origin
            corners=p*math.cos(angle)+np.cross(v,p)*math.sin(angle)+np.outer(p@v,v)*(1-math.cos(angle))+origin
        idx={'north':[1,0,3,2],'south':[4,5,6,7],'west':[0,4,7,3],'east':[5,1,2,6],'down':[0,1,5,4],'up':[7,6,2,3]}
        for face,f in e.get('faces',{}).items():
            tid=f['texture'];seen=set()
            while tid.startswith('#') and tid not in seen:
                seen.add(tid);tid=d.get('textures',{}).get(tid[1:],'')
            try:tex=texture(tid)
            except (FileNotFoundError,ValueError):tex=np.full((16,16,4),[160,160,160,255],dtype=np.uint8)
            uv=f.get('uv',[0,0,16,16]);u0,v0,u1,v1=uv
            coords=np.array([[u0,v1],[u1,v1],[u1,v0],[u0,v0]])
            coords=np.roll(coords,f.get('rotation',0)//90,axis=0)
            out.append((corners[idx[face]],coords,tex))
    return out
def render(mid,size=240):
    qs=quads(mid)
    if not qs:return Image.new('RGB',(size,size),(90,90,90))
    yaw=math.radians(32);pitch=math.radians(15)
    right=np.array([math.cos(yaw),0,-math.sin(yaw)])
    up=np.array([-math.sin(yaw)*math.sin(pitch),math.cos(pitch),-math.cos(yaw)*math.sin(pitch)])
    depth=np.cross(right,up);mat=np.stack([right,-up,depth],axis=1)
    allp=np.concatenate([q[0]@mat for q in qs]);lo=allp.min(axis=0);hi=allp.max(axis=0)
    scale=(size-24)/max(*(hi-lo)[:2],1)
    canvas=np.full((size,size,3),[116,125,137],dtype=np.uint8);zbuf=np.full((size,size),-np.inf)
    for verts,uvs,tex in qs:
        pts=verts@mat;pts[:,:2]=(pts[:,:2]-(lo+hi)[:2]/2)*scale+size/2
        n=np.cross(verts[1]-verts[0],verts[2]-verts[0]);norm=np.linalg.norm(n)
        shade=.7+.3*abs(n@np.array([.3,.8,-.5])/norm) if norm else 1
        for tri in [[0,1,2],[0,2,3]]:
            p=pts[tri];uv=uvs[tri];xmin=max(0,int(p[:,0].min()));xmax=min(size-1,int(p[:,0].max())+1);ymin=max(0,int(p[:,1].min()));ymax=min(size-1,int(p[:,1].max())+1)
            if xmin>xmax or ymin>ymax:continue
            x,y=np.meshgrid(np.arange(xmin,xmax+1)+.5,np.arange(ymin,ymax+1)+.5)
            den=(p[1,1]-p[2,1])*(p[0,0]-p[2,0])+(p[2,0]-p[1,0])*(p[0,1]-p[2,1])
            if abs(den)<1e-10:continue
            w0=((p[1,1]-p[2,1])*(x-p[2,0])+(p[2,0]-p[1,0])*(y-p[2,1]))/den
            w1=((p[2,1]-p[0,1])*(x-p[2,0])+(p[0,0]-p[2,0])*(y-p[2,1]))/den;w2=1-w0-w1
            z=w0*p[0,2]+w1*p[1,2]+w2*p[2,2];u=w0*uv[0,0]+w1*uv[1,0]+w2*uv[2,0];v=w0*uv[0,1]+w1*uv[1,1]+w2*uv[2,1]
            colors=tex[np.clip((v*tex.shape[0]/16).astype(int),0,tex.shape[0]-1),np.clip((u*tex.shape[1]/16).astype(int),0,tex.shape[1]-1)]
            zb=zbuf[ymin:ymax+1,xmin:xmax+1];cb=canvas[ymin:ymax+1,xmin:xmax+1];mask=(w0>=-1e-8)&(w1>=-1e-8)&(w2>=-1e-8)&(z>zb)&(colors[:,:,3]>127)
            cb[mask]=np.clip(colors[:,:,:3][mask]*shade,0,255).astype(np.uint8);zb[mask]=z[mask]
    return Image.fromarray(canvas)
rows=[]
for e in manifest['entries']:
    spec=R/'bloodborne_dw/composite'/(e['registryId'].split(':',1)[1]+'.json')
    variants=[]
    if spec.is_file():
        for n,v in enumerate(json.loads(spec.read_text(encoding='utf-8'))['variants']):
            models=[p['model'] for p in v['poses']['closed']['parts']]
            variants.append({'variant':n,'models':models})
            for mid in models:
                if mid not in [r['model'] for r in rows]:rows.append({'number':len(rows),'catalogue':e['catalogueNumber'],'registryId':e['registryId'],'variant':n,'model':mid})
    e['variants']=variants;e['models']=sorted({m for v in variants for m in v['models']})
for start in range(0,len(rows),20):
    group=rows[start:start+20];sheet=Image.new('RGB',(1200,290*math.ceil(len(group)/5)),(235,235,235));draw=ImageDraw.Draw(sheet)
    for i,row in enumerate(group):
        x=i%5*240;y=i//5*290;sheet.paste(render(row['model']),(x,y));draw.text((x+3,y+242),str(row['number'])+' / '+row['catalogue']+' / v'+str(row['variant']),fill=(0,0,0));draw.text((x+3,y+259),row['model'].rsplit('/',2)[-2]+'/'+row['model'].rsplit('/',1)[-1],fill=(0,0,0))
    sheet.save(O/('sheet-'+str(start//20)+'.png'))
(O/'models.json').write_text(json.dumps(rows,ensure_ascii=False,indent=2),encoding='utf-8')
(H/'statue-ids.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2),encoding='utf-8')
