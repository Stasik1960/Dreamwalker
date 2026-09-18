"""Offline textured polyhedron clipping. Runtime only draws pre-baked cell meshes.

All coordinates are block-local; UVs use the Minecraft 0..16 texture convention.
Closed cuboids are clipped against cell planes, including textured end caps.
No world or Minecraft process is started by this module.
"""
from catalog_geometry import *
from render_catalog import texture, FACE_INDEX
import hashlib

SIDES={'west':(0,0),'east':(0,1),'down':(1,0),'up':(1,1),'north':(2,0),'south':(2,1)}

def texname(md,name):
    seen=set()
    while name.startswith('#'):
        if name in seen:raise ValueError('cyclic texture '+name)
        seen.add(name);name=md['textures'][name[1:]]
    return name if ':' in name else 'minecraft:'+name

def implicit_uv(e,side):
    x,y,z=e['from'];X,Y,Z=e['to']
    return {'down':[x,16-Z,X,16-z],'up':[x,z,X,Z],
            'north':[16-X,16-Y,16-x,16-y],'south':[x,16-Y,X,16-y],
            'west':[z,16-Y,Z,16-y],'east':[16-Z,16-Y,16-z,16-y]}[side]

def polygon(points,uv,tex):
    return {'texture':tex,'vertices':np.column_stack((points,uv)).round(7).tolist()}

def face_uv(face,side,e,app):
    u,v,U,V=face.get('uv',implicit_uv(e,side));angle=face.get('rotation',0)
    if app.get('uvlock'):
        normal=np.zeros(3);axis,high=SIDES[side];normal[axis]=1 if high else -1
        variant=rotation(1,-app.get('y',0))@rotation(0,-app.get('x',0));turned=variant@normal
        target=next(s for s,(a,h) in SIDES.items() if abs(turned[a]-(1 if h else -1))<1e-6)
        def F(uv):
            a,b=uv
            p={'north':[16-a,16-b,0],'south':[a,16-b,16],'west':[0,16-b,a],
               'east':[16,16-b,16-a],'up':[a,16,b],'down':[a,0,16-b]}[side]
            x,y,z=variant@(np.array(p)-8)+8
            return np.array({'north':[16-x,16-y],'south':[x,16-y],'west':[z,16-y],
                'east':[16-z,16-y],'up':[x,z],'down':[x,16-z]}[target])
        a=F([u,v]);b=F([U,V]);origin=F([0,0]);mat=np.column_stack((F([1,0])-origin,F([0,1])-origin))
        if np.sign(U-u)!=np.sign(b[0]-a[0]):a[0],b[0]=b[0],a[0]
        if np.sign(V-v)!=np.sign(b[1]-a[1]):a[1],b[1]=b[1],a[1]
        direction=mat@np.array([math.cos(math.radians(angle)),math.sin(math.radians(angle))])
        angle=(-round(math.degrees(math.atan2(direction[1],direction[0]))/90)*90)%360
        u,v=a;U,V=b
    return np.roll(np.array([[u,V],[U,V],[U,v],[u,v]],float),-angle//90,axis=0)

def alpha_patch(poly):
    im=texture(poly['texture']);uv=np.array(poly['vertices'])[:,3:5]/16
    low=np.floor(uv.min(axis=0)*[im.shape[1],im.shape[0]]).astype(int)
    high=np.ceil(uv.max(axis=0)*[im.shape[1],im.shape[0]]).astype(int)
    low=np.maximum(0,np.minimum(low,[im.shape[1]-1,im.shape[0]-1]));high=np.maximum(low+1,np.minimum(high,[im.shape[1],im.shape[0]]))
    return im[low[1]:high[1],low[0]:high[0],3]

def element_polys(e,app,md):
    """Seal omitted solid faces. Extrude artwork, including its alpha silhouette."""
    e=copy.deepcopy(e);lo=np.array(e['from'],float);hi=np.array(e['to'],float)
    thin=int(np.argmin(hi-lo));planar=hi[thin]-lo[thin]<1e-5
    if not e.get('faces'):return []
    if planar:
        # A real sliver instead of a one-sided zero-thickness quad.
        hi[thin]+=0.25;lo[thin]-=0.25;e['from']=lo.tolist();e['to']=hi.tolist()
    pts=corners(e,app);faces=e['faces'];out=[]
    candidates=[s for s in faces if SIDES[s][0]==thin]
    if planar and candidates:
        side=candidates[0];f=faces[side];tex=texname(md,f['texture']);im=texture(tex)
        indices=FACE_INDEX[side];front=pts[indices];u,v,U,V=f.get('uv',implicit_uv(e,side))
        uv=face_uv(f,side,e,app)
        # Give the back the same artwork with correct mirrored world winding.
        vector=pts[FACE_INDEX[next(s for s in SIDES if SIDES[s]==(thin,1-SIDES[side][1]))]].mean(axis=0)-front.mean(axis=0)
        out.append(polygon(front,uv,tex));out.append(polygon((front+vector)[::-1],uv[::-1],tex))
        # Opaque silhouette edge strips (bounded by texture resolution), no full
        # rectangular walls around a transparent bush/window sprite.
        # Keep the detailed artwork on the two broad faces. Side-wall geometry
        # uses an 8x8 silhouette budget; plants must not cost hundreds of quads.
        W=min(8,max(1,int(abs(U-u)/16*im.shape[1])));H=min(8,max(1,int(abs(V-v)/16*im.shape[0])))
        def at(a,b):return front[2]*(1-a)*(1-b)+front[3]*a*(1-b)+front[1]*(1-a)*b+front[0]*a*b
        def tu(a,b):return uv[2]*(1-a)*(1-b)+uv[3]*a*(1-b)+uv[1]*(1-a)*b+uv[0]*a*b
        mask=np.zeros((H,W),bool)
        for j in range(H):
            for i in range(W):
                uu,vv=tu((i+.5)/W,(j+.5)/H)/16
                mask[j,i]=im[min(im.shape[0]-1,max(0,int(vv*im.shape[0]))),min(im.shape[1]-1,max(0,int(uu*im.shape[1]))),3]>=64
        for j,i in np.argwhere(mask):
            for di,dj,a,b in ((-1,0,(i/W,(j+1)/H),(i/W,j/H)),(1,0,((i+1)/W,j/H),((i+1)/W,(j+1)/H)),(0,-1,(i/W,j/H),((i+1)/W,j/H)),(0,1,((i+1)/W,(j+1)/H),(i/W,(j+1)/H))):
                I,J=i+di,j+dj
                if 0<=I<W and 0<=J<H and mask[J,I]:continue
                p,q=at(*a),at(*b);color=tu((i+.5)/W,(j+.5)/H)
                out.append(polygon(np.array([p,q,q+vector,p+vector]),np.tile(color,(4,1)),tex))
        return out
    for side in SIDES:
        f=faces.get(side)
        if f is None:
            opp=next(s for s in SIDES if SIDES[s]==(SIDES[side][0],1-SIDES[side][1]))
            donor=opp if opp in faces else next(iter(faces))
            f=copy.deepcopy(faces[donor]);f.setdefault('uv',implicit_uv(e,donor))
        tex=texname(md,f['texture']);u,v,U,V=f.get('uv',implicit_uv(e,side))
        uv=face_uv(f,side,e,app)
        out.append(polygon(pts[FACE_INDEX[side]],uv,tex))
    return out

def clean_vertices(vertices):
    out=[]
    for v in vertices:
        if not out or np.linalg.norm(v[:3]-out[-1][:3])>1e-7:out.append(v)
    if len(out)>1 and np.linalg.norm(out[0][:3]-out[-1][:3])<1e-7:out.pop()
    changed=True
    while changed and len(out)>3:
        changed=False
        for i in range(len(out)):
            a,b,c=out[i-1],out[i],out[(i+1)%len(out)]
            if np.linalg.norm(np.cross(b[:3]-a[:3],c[:3]-b[:3]))<1e-10:
                out.pop(i);changed=True;break
    return out

def tile_uv(poly):
    """Split repeated texture coordinates before mapping into an atlas sprite.

    Clamping stretches the edge texel; wrapping vertices alone crosses the atlas.
    Clipping into whole texture tiles preserves the authored repeat instead.
    """
    vertices=np.array(poly['vertices']);low=np.floor(vertices[:,3:5].min(axis=0)/16+1e-8).astype(int)
    high=np.ceil(vertices[:,3:5].max(axis=0)/16-1e-8).astype(int);high=np.maximum(high,low+1)
    result=[]
    for u,v in itertools.product(range(low[0],high[0]),range(low[1],high[1])):
        p=poly
        for axis,tile in ((3,u),(4,v)):
            for value,above in ((tile*16,True),((tile+1)*16,False)):
                p=clip(p,axis,value,above)[0]
                if p is None:break
            if p is None:break
        if p is not None:
            a=np.array(p['vertices']);a[:,3:5]-=[u*16,v*16];a[:,3:5]=a[:,3:5].clip(0,16)
            result.append({'texture':p['texture'],'vertices':a.round(6).tolist()})
    return result

def clip(poly,axis,value,keep_above):
    out=[];cuts=[];verts=[np.array(v,float) for v in poly['vertices']]
    for a,b in zip(verts,verts[1:]+verts[:1]):
        da=(a[axis]-value)*(1 if keep_above else -1);db=(b[axis]-value)*(1 if keep_above else -1)
        if da>=-1e-8:out.append(a)
        if (da>1e-8 and db<-1e-8) or (da<-1e-8 and db>1e-8):
            p=a+(b-a)*da/(da-db);p[axis]=value;out.append(p);cuts.append(p)
    out=clean_vertices(out)
    return ({**poly,'vertices':[v.tolist() for v in out]} if len(out)>=3 else None),cuts

def clip_solid(polys,axis,value,above):
    out=[];cuts=[]
    points=[np.array(v,float) for p in polys for v in p['vertices']]
    crossing=any(v[axis]<value-1e-8 for v in points) and any(v[axis]>value+1e-8 for v in points)
    for poly in polys:
        p,c=clip(poly,axis,value,above)
        if p:out.append(p)
        cuts.extend(c)
    if crossing:cuts.extend(v for v in points if abs(v[axis]-value)<1e-8)
    # A cap closes solid cut faces; thin alpha artwork is split without caps.
    unique={tuple(np.round(v[:3],7)):v for v in cuts}
    if crossing and len(unique)>=3:
        vertices=np.array(list(unique.values()));center=vertices[:,:3].mean(axis=0)
        axes=[a for a in range(3) if a!=axis]
        order=np.argsort(np.arctan2(vertices[:,axes[1]]-center[axes[1]],vertices[:,axes[0]]-center[axes[0]]))
        vertices=np.array(clean_vertices(list(vertices[order])));normal=np.cross(vertices[1,:3]-vertices[0,:3],vertices[2,:3]-vertices[0,:3])
        if normal[axis]*(1 if above else -1)>0:vertices=vertices[::-1]
        def alignment(p):
            a=np.array(p['vertices']);normal=np.cross(a[1,:3]-a[0,:3],a[2,:3]-a[0,:3]);length=np.linalg.norm(normal)
            return normal[axis]*(-1 if above else 1)/length if length>1e-9 else -2
        donor=max(polys,key=alignment);dv=np.array(donor['vertices']);duv=dv[:,3:5]
        # Most props use a texture atlas. New end caps must sample their material
        # patch, never stretch the entire atlas (bricks, metal and books at once).
        basis=np.column_stack((dv[:,axes],np.ones(len(dv))))
        affine=np.linalg.lstsq(basis,duv,rcond=None)[0]
        projected=np.column_stack((vertices[:,axes],np.ones(len(vertices))))@affine
        span=np.maximum(projected.max(axis=0)-projected.min(axis=0),1e-9)
        vertices[:,3:5]=duv.min(axis=0)+(projected-projected.min(axis=0))/span*(duv.max(axis=0)-duv.min(axis=0))
        out.append({'texture':donor['texture'],'vertices':vertices.tolist(),'cap_axis':axis})
    return out

def split_element(polys,closed=True):
    if not polys:return {}
    pts=np.array([v[:3] for p in polys for v in p['vertices']]);lo=np.floor(pts.min(axis=0)+1e-6).astype(int);hi=np.ceil(pts.max(axis=0)-1e-6).astype(int)
    hi=np.maximum(hi,lo+1);out={}
    for cell in itertools.product(*(range(lo[a],hi[a]) for a in range(3))):
        clipped=polys
        for a in range(3):
            for value,above in ((cell[a],True),(cell[a]+1,False)):
                if closed:clipped=clip_solid(clipped,a,value,above)
                else:clipped=[r for p in clipped if (r:=clip(p,a,value,above)[0])]
                if not clipped:break
        if not clipped:continue
        result=[]
        for poly in clipped:
            v=np.array(poly['vertices']);v[:,:3]-=cell
            if not any(np.linalg.norm(np.cross(v[i,:3]-v[0,:3],v[i+1,:3]-v[0,:3]))>1e-8 for i in range(1,len(v)-1)):continue
            result.extend(tile_uv({'texture':poly['texture'],'vertices':v.round(6).tolist()}))
        if result:out[cell]=result
    return out

@functools.lru_cache(None)
def cells_for_app(appkey):
    app=json.loads(appkey);md=model(app['model']);out={}
    for e in md.get('elements',[]):
        planar=any(abs(b-a)<1e-5 for a,b in zip(e['from'],e['to']))
        for c,ps in split_element(element_polys(e,app,md),not planar).items():
            ps=[p for p in ps if np.any(alpha_patch(p)>0)]
            if ps:out.setdefault(c,[]).extend(ps)
    return out

def rotate_polys(polys,turns):
    mat=rotation(1,turns*90);out=[]
    for p in polys:
        v=np.array(p['vertices']);v[:,:3]=(v[:,:3]-.5)@mat.T+.5
        out.append({'texture':p['texture'],'vertices':v.round(6).tolist()})
    return out

def digest(polys):
    # Cyclic vertex order and duplicate faces do not change a model.
    items=[]
    for p in polys:
        vertices=[tuple(round(float(x),5)+0.0 for x in v) for v in p['vertices']]
        rotations=[vertices[i:]+vertices[:i] for i in range(len(vertices))]
        items.append((p['texture'],min(rotations)))
    serial=json.dumps(sorted(set((a,tuple(b)) for a,b in items)),separators=(',',':'))
    return hashlib.sha256(serial.encode()).hexdigest()[:16]
