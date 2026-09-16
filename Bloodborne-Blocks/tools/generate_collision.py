"""Build bounded, cell-local collision for every visual state at 1/16-block resolution.

Transparent pixels are sampled only on thin surfaces; glass volumes remain solid.
Rotated elements are voxelized in their own coordinate system, not their AABB.
No giant coordinate-grid shapes are constructed at runtime.
"""
from catalog_geometry import *
from render_catalog import texture
import hashlib

POLICIES=json.loads((ROOT/'docs/block-policies.json').read_text())
GRID=16

@functools.lru_cache(None)
def alpha_area(name):
    # Summed alpha coverage lets a voxel see thin opaque details between its
    # sample centres, without turning the transparent holes into solid panels.
    alpha=texture(name)[:,:,3]>=64
    return np.pad(alpha.astype(np.int32).cumsum(0).cumsum(1),((1,0),(1,0)))

def texname(md,name):
    seen=set()
    while name.startswith('#'):
        if name in seen:raise ValueError('Texture cycle')
        seen.add(name);name=md['textures'][name[1:]]
    return name

@functools.lru_cache(None)
def voxels(appkey):
    app=json.loads(appkey);md=model(app['model']);out=set()
    for original in md.get('elements',[]):
        if not original.get('faces'):continue
        e=copy.deepcopy(original);elo=np.array(e['from'],dtype=float);ehi=np.array(e['to'],dtype=float)
        thin=int(np.argmin(ehi-elo));sample_alpha=(ehi-elo)[thin]<=2.001
        # Give planes a 1/16 hit surface; preserve volumetric models.
        for axis in range(3):
            if ehi[axis]-elo[axis]<1:
                middle=(ehi[axis]+elo[axis])/2;elo[axis]=middle-.5;ehi[axis]=middle+.5
        e['from']=elo.tolist();e['to']=ehi.tolist();pts=corners(e,app)
        low=np.floor(pts.min(axis=0)*GRID+1e-7).astype(int);high=np.ceil(pts.max(axis=0)*GRID-1e-7).astype(int)
        if np.prod(high-low)>4000000:raise ValueError('Unreasonably large model '+app['model'])
        indices=np.stack(np.meshgrid(*[np.arange(low[a],high[a]) for a in range(3)],indexing='ij'),axis=-1).reshape(-1,3)
        if not len(indices):continue
        mat,off=transform(e,app);local=((indices+.5)/GRID-off)@np.linalg.inv(mat).T
        inside=np.all((local>=elo-1e-5)&(local<=ehi+1e-5),axis=1)
        if sample_alpha:
            face_candidates=[s for s in ({0:('west','east'),1:('up','down'),2:('north','south')}[thin]) if s in e['faces']]
            opaque=np.zeros(len(local),dtype=bool)
            for side in face_candidates:
                face=e['faces'][side];name=texname(md,face['texture']);image=texture(name)
                if np.all(image[:,:,3]>=128):opaque|=True;break
                x,y,z=original['from'];X,Y,Z=original['to'];uv=face.get('uv',[0,0,16,16])
                if thin==0:a=(local[:,2]-z)/max(Z-z,1e-5);b=1-(local[:,1]-y)/max(Y-y,1e-5)
                elif thin==1:a=(local[:,0]-x)/max(X-x,1e-5);b=(local[:,2]-z)/max(Z-z,1e-5)
                else:a=(local[:,0]-x)/max(X-x,1e-5);b=1-(local[:,1]-y)/max(Y-y,1e-5)
                if side in ('north','east'):a=1-a
                for _ in range(face.get('rotation',0)//90):a,b=b,1-a
                u=(uv[0]+a*(uv[2]-uv[0]))/16;v=(uv[1]+b*(uv[3]-uv[1]))/16
                spans={0:(Z-z,Y-y),1:(X-x,Z-z),2:(X-x,Y-y)}[thin]
                if face.get('rotation',0)%180:spans=spans[::-1]
                ru=.5*abs(uv[2]-uv[0])/16/max(spans[0],1e-5)
                rv=.5*abs(uv[3]-uv[1])/16/max(spans[1],1e-5)
                H,W=image.shape[:2]
                x0=np.clip(np.floor((u-ru)*W).astype(int),0,W);x1=np.clip(np.ceil((u+ru)*W).astype(int),0,W)
                y0=np.clip(np.floor((v-rv)*H).astype(int),0,H);y1=np.clip(np.ceil((v+rv)*H).astype(int),0,H)
                area=alpha_area(name)
                opaque|=(area[y1,x1]-area[y0,x1]-area[y1,x0]+area[y0,x0])>0
            if face_candidates:inside&=opaque
        out.update(map(tuple,indices[inside].tolist()))
    return frozenset(out)

def merge_cell(points):
    grid=np.zeros((GRID,GRID,GRID),dtype=bool)
    for p in points:grid[p]=True
    boxes=[]
    while grid.any():
        x,y,z=np.argwhere(grid)[0];X=x+1;Y=y+1;Z=z+1
        while X<GRID and grid[X,y,z]:X+=1
        while Y<GRID and grid[x:X,Y,z].all():Y+=1
        while Z<GRID and grid[x:X,y:Y,Z].all():Z+=1
        grid[x:X,y:Y,z:Z]=False
        boxes.append([round(float(v)/GRID,6) for v in (x,y,z,X,Y,Z)])
    return boxes

def cells_from(occupied,collide):
    grouped={}
    for p in occupied:
        cell=tuple(v//GRID for v in p);local=tuple(v%GRID for v in p);grouped.setdefault(cell,[]).append(local)
    cells={}
    for c,points in sorted(grouped.items()):
        boxes=merge_cell(points);cells[','.join(map(str,c))]={'collision':boxes if collide else [],'outline':boxes}
    return cells

def generate():
    allblocks={};audit=[];intern={};changes=[]
    for num,b in enumerate(DATA['blocks']):
        path=ASSETS/'blockstates'/f'{b["id"]}.json';bs=json.loads(path.read_text());changed=False
        # A randomly chosen physical model cannot share one state collider.
        for k,v in list(bs.get('variants',{}).items()):
            if isinstance(v,list) and len({voxels(json.dumps(a,sort_keys=True)) for a in v})>1:
                bs['variants'][k]=v[0];changed=True;changes.append({'block':b['id'],'variant':k,'chosen':v[0]})
        if changed:path.write_text(json.dumps(bs,separators=(',',':'))+'\n')
        states={};maxcells=0;maxboxes=0;empty=0
        for statekey in b['states']:
            state=dict(x.split('=') for x in statekey.split(',') if x);apps=[v[0] for v in applications(bs,state)]
            signature=tuple(json.dumps(a,sort_keys=True) for a in apps)+(POLICIES[b['id']]['collision'],)
            if signature not in intern:
                occupied=frozenset().union(*(voxels(json.dumps(a,sort_keys=True)) for a in apps))
                cells=cells_from(occupied,POLICIES[b['id']]['collision']!='none')
                # Include click/render-only roots without invented physical cube collisions.
                anchor=[min(int(c.split(',')[a]) for c in cells) for a in range(3)] if cells else [0,0,0]
                render_offset=np.zeros(3)
                if apps:
                    a=apps[0];render_offset=rotation(1,-a.get('y',0))@rotation(0,-a.get('x',0))@np.array(model(a['model']).get('bloodborne_offset',[0,0,0]))
                intern[signature]={'cells':cells,'anchor':anchor,'render_offset':np.round(render_offset,6).tolist()}
            entry=intern[signature];states[statekey]=entry
            maxcells=max(maxcells,len(entry['cells']));maxboxes=max(maxboxes,max((len(c['collision']) for c in entry['cells'].values()),default=0));empty+=not entry['cells']
        allblocks[b['id']]={'states':states}
        audit.append({'id':b['id'],'behavior':b['kind'],'collision':POLICIES[b['id']]['collision'],'states':len(states),'max_cells':maxcells,'max_boxes_per_cell':maxboxes,'empty_visual_states':empty,'assembled':'assembled' in b['properties']})
        if num%50==0:print('Geometry',num,'/',len(DATA['blocks']),flush=True)
    profiles={};profile_ids={}
    for block in allblocks.values():
        for statekey,entry in block['states'].items():
            packed=json.dumps(entry,sort_keys=True,separators=(',',':'))
            if packed not in profile_ids:
                name='g'+str(len(profiles));profile_ids[packed]=name;profiles[name]=entry
            block['states'][statekey]={'ref':profile_ids[packed]}
    (RES/'bloodborne_blocks/geometry.json').write_text(json.dumps({'resolution':GRID,'profiles':profiles,'blocks':allblocks},separators=(',',':'))+'\n')
    (ROOT/'docs/collision-audit.json').write_text(json.dumps(audit,indent=2)+'\n')
    report=ROOT/'docs/stabilized-variants.json'
    if report.exists():changes=json.loads(report.read_text())+changes
    report.write_text(json.dumps(changes,indent=2)+'\n')
    print('Generated',len(allblocks),'blocks,',sum(len(b['states']) for b in allblocks.values()),'states;',len(profiles),'shared geometry profiles;',len(changes),'unstable variants stabilized.')

if __name__=='__main__':generate()
