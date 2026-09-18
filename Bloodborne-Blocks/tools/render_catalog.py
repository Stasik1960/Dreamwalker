"""Offline textured catalog, without launching Minecraft."""
from catalog_geometry import *
from PIL import Image, ImageDraw, ImageFont
import io

@functools.lru_cache(None)
def texture(name):
    ns,local=name.split(':') if ':' in name else ('minecraft',name)
    p=f'assets/{ns}/textures/{local}.png'
    source=(RES/p) if (RES/p).exists() else io.BytesIO(ZIP.read(p))
    im=Image.open(source).convert('RGBA')
    if im.height>im.width:im=im.crop((0,0,im.width,im.width))
    return np.array(im)

FACE_INDEX={'down':[0,4,5,1],'up':[3,7,6,2],'north':[4,0,2,6],'south':[1,5,7,3],'west':[0,1,3,2],'east':[5,4,6,7]}
CAM=rotation(0,20)@rotation(1,-25)

def draw_item(apps,size=112):
    polys=[]
    for app in apps:
        md=model(app['model']);textures=md.get('textures',{})
        for e in elements(app):
            verts=corners(e,app)@CAM.T
            for side,face in e.get('faces',{}).items():
                tex=face['texture'];seen=set()
                while tex.startswith('#'):
                    if tex in seen:break
                    seen.add(tex);tex=textures[tex[1:]]
                uv=face.get('uv',[0,0,16,16]);u,v,U,V=uv
                coords=np.array([[u,V],[U,V],[U,v],[u,v]],dtype=float)
                coords=np.roll(coords,-face.get('rotation',0)//90,axis=0)/16
                polys.append((verts[FACE_INDEX[side]],coords,texture(tex)))
    if not polys:return Image.new('RGBA',(size,size),(32,34,40,255))
    pts=np.concatenate([p[0] for p in polys]);lo=pts.min(axis=0);hi=pts.max(axis=0)
    s=(size-8)/max(hi[0]-lo[0],hi[1]-lo[1],.1);center=(lo+hi)/2
    canvas=np.zeros((size,size,4),dtype=np.uint8);canvas[:]=[32,34,40,255];depth=np.full((size,size),-np.inf)
    for vertices,uv,tex in polys:
        v=(vertices-center)*s;v[:,0]+=size/2;v[:,1]=size/2-v[:,1]
        normal=np.cross(vertices[1]-vertices[0],vertices[2]-vertices[0]);norm=np.linalg.norm(normal)
        shade=.68+.32*abs(normal[2]/norm) if norm>1e-8 else 1
        for ids in ([0,1,2],[0,2,3]):
            tri=v[ids];tuv=uv[ids];x0=max(0,int(np.floor(tri[:,0].min())));x1=min(size,int(np.ceil(tri[:,0].max()))+1);y0=max(0,int(np.floor(tri[:,1].min())));y1=min(size,int(np.ceil(tri[:,1].max()))+1)
            if x1<=x0 or y1<=y0:continue
            xx,yy=np.meshgrid(np.arange(x0,x1)+.5,np.arange(y0,y1)+.5)
            a,b,c=tri;den=(b[1]-c[1])*(a[0]-c[0])+(c[0]-b[0])*(a[1]-c[1])
            if abs(den)<1e-8:continue
            w0=((b[1]-c[1])*(xx-c[0])+(c[0]-b[0])*(yy-c[1]))/den
            w1=((c[1]-a[1])*(xx-c[0])+(a[0]-c[0])*(yy-c[1]))/den;w2=1-w0-w1
            z=w0*a[2]+w1*b[2]+w2*c[2];tx=w0*tuv[0,0]+w1*tuv[1,0]+w2*tuv[2,0];ty=w0*tuv[0,1]+w1*tuv[1,1]+w2*tuv[2,1]
            colors=tex[np.clip((ty*tex.shape[0]).astype(int),0,tex.shape[0]-1),np.clip((tx*tex.shape[1]).astype(int),0,tex.shape[1]-1)].copy()
            mask=(w0>=-1e-6)&(w1>=-1e-6)&(w2>=-1e-6)&(z>depth[y0:y1,x0:x1])&(colors[:,:,3]>32)
            colors[:,:,:3]=(colors[:,:,:3]*shade).astype(np.uint8)
            canvas[y0:y1,x0:x1][mask]=colors[mask];depth[y0:y1,x0:x1][mask]=z[mask]
    return Image.fromarray(canvas)

if __name__=='__main__':
    out=ROOT/'build/catalog';out.mkdir(parents=True,exist_ok=True)
    for batch in range(6):
        sheet=Image.new('RGB',(1400,1400),(32,34,40));draw=ImageDraw.Draw(sheet)
        for i,b in enumerate(DATA['blocks'][batch*100:(batch+1)*100]):
            x=(i%10)*140;y=(i//10)*140
            im=draw_item(default_apps(b));sheet.paste(im,(x+14,y))
            label=f'{batch*100+i}: '+b['id'];draw.text((x+2,y+113),label[:23],fill='white');draw.text((x+2,y+125),label[23:46],fill='white')
        sheet.save(out/f'catalog-{batch}.png')
        print('Rendered sheet',batch,flush=True)
