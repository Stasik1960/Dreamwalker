"""Read-only comparison of post-V12 rendered geometry, materials and poses.

Files and names are never equality criteria. Face positions, UV-sampled pixels,
source element rotations and CompositeClient part transforms determine equality.
Translation and eight yaw states are normalized. Candidate evidence is not a
substitute for the native client comparison and interaction checks in acceptance.
"""
from __future__ import annotations
import argparse, hashlib, json, math, sys, io
from collections import defaultdict, Counter
from functools import lru_cache
from pathlib import Path
import numpy as np
from PIL import Image
from gallery_platform_plan import ModelGeometry, rotate
from import_remaining_assets import implicit_uv

ROOT=Path(__file__).resolve().parents[1]
RES=ROOT/'src/architecture/resources'
OUT=ROOT/'docs/v12.4-dedup'

def sha(v): return hashlib.sha256(v).hexdigest()
def rot(points,axis,degrees,origin):
    angle=math.radians(degrees); c,s=math.cos(angle),math.sin(angle)
    if abs(c)<1e-9:c=0.
    if abs(s)<1e-9:s=0.
    if abs(abs(c)-1)<1e-9:c=round(c)
    if abs(abs(s)-1)<1e-9:s=round(s)
    a=np.asarray(points,dtype=np.float64)-np.asarray(origin)
    one,two=(axis+1)%3,(axis+2)%3
    x,y=a[...,one].copy(),a[...,two].copy()
    a[...,one]=c*x-s*y;a[...,two]=s*x+c*y
    return a+np.asarray(origin)

class Visuals(ModelGeometry):
    @lru_cache(maxsize=None)
    def textures(self,identifier):
        d=self.document(identifier); p=d.get('parent','')
        t=dict(self.textures(p)) if p and not p.startswith(('builtin/','minecraft:builtin/')) else {}
        t.update(d.get('textures',{}));return t

    @lru_cache(maxsize=None)
    def image(self,identifier):
        namespace,path=identifier.split(':',1) if ':' in identifier else ('minecraft',identifier)
        relative='assets/'+namespace+'/textures/'+path+'.png'
        for root in self.roots:
            file=root/relative
            if file.is_file():return np.asarray(Image.open(file).convert('RGBA'))
        if self.vanilla and relative in self.vanilla.namelist():
            return np.asarray(Image.open(io.BytesIO(self.vanilla.read(relative))).convert('RGBA'))
        raise FileNotFoundError(identifier)

    def texture(self,identifier,name):
        t=self.textures(identifier);seen=set()
        while name.startswith('#'):
            if name in seen:raise ValueError('Texture cycle')
            seen.add(name);name=t[name[1:]]
        return name

    def quads(self,part):
        identifier=part['model']; result=[]
        for e in self.elements(identifier):
            low,high=e['from'],e['to'];x,y,z=low;X,Y,Z=high
            # Minecraft quad vertex order; UV corners travel with each vertex.
            faces={'north':[(X,Y,z),(X,y,z),(x,y,z),(x,Y,z)],
                   'south':[(x,Y,Z),(x,y,Z),(X,y,Z),(X,Y,Z)],
                   'west':[(x,Y,z),(x,y,z),(x,y,Z),(x,Y,Z)],
                   'east':[(X,Y,Z),(X,y,Z),(X,y,z),(X,Y,z)],
                   'up':[(x,Y,z),(x,Y,Z),(X,Y,Z),(X,Y,z)],
                   'down':[(x,y,Z),(x,y,z),(X,y,z),(X,y,Z)]}
            for side,face in e.get('faces',{}).items():
                v=np.asarray(faces[side],dtype=float)
                r=e.get('rotation')
                if r:
                    axis='xyz'.index(r['axis']);o=np.asarray(r['origin']);angle=r['angle']
                    if r.get('rescale'):
                        # Native FaceBakery chooses the22.5 factor only for
                        # abs(angle)==22.5; all other true-rescale elements use
                        # the45 factor, including authored angle0 elements.
                        scale_angle=22.5 if abs(angle)==22.5 else 45
                        scale=np.full(3,1/math.cos(math.radians(scale_angle)));scale[axis]=1
                        v=(v-o)*scale+o
                    v=rot(v,axis,angle,o)
                v=rot(v,1,part.get('extraYaw',0),part.get('extraPivot',[8,8,8]))
                v=rot(v,0,-part.get('pitch',0),part.get('pivot',[8,8,8]))
                v=rot(v,1,-part.get('yaw',0),part.get('pivot',[8,8,8]))
                v+=np.asarray(part.get('offset',[0,0,0]))
                uv=face.get('uv',implicit_uv(e,side));u,w,U,W=uv
                coords=np.array([(u,w),(u,W),(U,W),(U,w)])
                coords=np.roll(coords,-int(face.get('rotation',0)/90),axis=0)
                texture=self.texture(identifier,face['texture'])
                result.append((v,coords,texture,face.get('tintindex',-1)))
        return result

    @lru_cache(maxsize=None)
    def sampled(self,texture,uv):
        image=self.image(texture);h,w=image.shape[:2]
        uv=np.asarray(uv).reshape(4,2)
        # Sampling compares pixels actually displayed; different atlas paths and
        # atlas layouts may therefore still be equal.
        a=np.linspace(.01,.99,8)
        rows=[]
        for s in a:
            for t in a:
                p=(1-s)*(1-t)*uv[0]+(1-s)*t*uv[1]+s*t*uv[2]+s*(1-t)*uv[3]
                rows.append(image[int(p[1]*h/16)%h,int(p[0]*w/16)%w])
        return sha(np.asarray(rows,dtype=np.uint8).tobytes())

def pose_data(visuals,spec,row):
    return [q for p in row['poses']['closed']['parts'] for q in visuals.quads(p)]

def signature(visuals,quads,yaw):
    vertices=rot(np.asarray([q[0] for q in quads]),1,yaw,[8,8,8])
    low=vertices.reshape(-1,3).min(axis=0);vertices-=low
    rows=[]
    for v,(_,uv,texture,tint) in zip(vertices,quads):
        coords=[tuple(round(float(n),3) for n in point) for point in v]
        alternatives=[tuple(coords[i:]+coords[:i]) for i in range(4)]
        # Geometry alone can be symmetric while transparent texture artwork
        # is not. Rotate UV association with the canonical vertex cycle.
        cycle=min(range(4),key=lambda i:alternatives[i])
        material=visuals.sampled(texture,tuple(np.roll(uv,-cycle,axis=0).reshape(-1)))
        rows.append((alternatives[cycle],material,tint))
    return sha(repr(sorted(rows)).encode()), low.tolist(), (vertices.reshape(-1,3).max(axis=0)).tolist()

def render(visuals,quads,yaw,size=64):
    """Textured orthographic diagnostic projection of actual rendered surfaces.

    This is evidence for candidate selection, explicitly not a native screenshot.
    Geometry and UVs are unchanged; translation and image fit are normalized.
    """
    vertices=rot(np.asarray([q[0] for q in quads]),1,yaw,[8,8,8])
    lo=vertices.reshape(-1,3).min(axis=0);hi=vertices.reshape(-1,3).max(axis=0)
    vertices-=(lo+hi)/2
    vertices=rot(vertices,1,35,[0,0,0]);vertices=rot(vertices,0,25,[0,0,0])
    span=max(float(np.ptp(vertices[...,0])),float(np.ptp(vertices[...,1])),1.)
    vertices[...,0]=size/2+vertices[...,0]*(size-6)/span
    vertices[...,1]=size/2-vertices[...,1]*(size-6)/span
    target=np.zeros((size,size,4),dtype=np.uint8);depth=np.full((size,size),np.inf)
    for xyz,(_,uv,texture,tint) in zip(vertices,quads):
        image=visuals.image(texture);h,w=image.shape[:2]
        for indices in [(0,1,2),(0,2,3)]:
            points=xyz[list(indices)];coords=uv[list(indices)]
            xmin=max(0,int(np.floor(points[:,0].min())));xmax=min(size-1,int(np.ceil(points[:,0].max())))
            ymin=max(0,int(np.floor(points[:,1].min())));ymax=min(size-1,int(np.ceil(points[:,1].max())))
            if xmin>xmax or ymin>ymax:continue
            X,Y=np.meshgrid(np.arange(xmin,xmax+1)+.5,np.arange(ymin,ymax+1)+.5)
            a,b,c=points;den=(b[1]-c[1])*(a[0]-c[0])+(c[0]-b[0])*(a[1]-c[1])
            if abs(den)<1e-8:continue
            aa=((b[1]-c[1])*(X-c[0])+(c[0]-b[0])*(Y-c[1]))/den
            bb=((c[1]-a[1])*(X-c[0])+(a[0]-c[0])*(Y-c[1]))/den;cc=1-aa-bb
            z=aa*a[2]+bb*b[2]+cc*c[2]
            U=aa*coords[0,0]+bb*coords[1,0]+cc*coords[2,0]
            V=aa*coords[0,1]+bb*coords[1,1]+cc*coords[2,1]
            pixels=image[(V*h/16).astype(int)%h,(U*w/16).astype(int)%w]
            old=depth[ymin:ymax+1,xmin:xmax+1]
            mask=(aa>=-1e-8)&(bb>=-1e-8)&(cc>=-1e-8)&(z<old)&(pixels[...,3]>=64)
            if not mask.any():continue
            old[mask]=z[mask]
            plane=target[ymin:ymax+1,xmin:xmax+1];plane[mask]=pixels[mask]
    return target

def near_review(visuals,result):
    rows=result['inventory'];by_key={(r['registryId'],r['variant']):r for r in rows}
    specs={};quads={};images={};pairs=[];seen=set()
    def get(key):
        if key not in quads:
            registry,variant=key;path=registry.split(':',1)[1]
            if path not in specs:specs[path]=json.loads((RES/'bloodborne_dw/composite'/(path+'.json')).read_text(encoding='utf-8-sig'))
            quads[key]=pose_data(visuals,specs[path],specs[path]['variants'][variant])
        return quads[key]
    def projected(key):
        if key not in images:images[key]=np.asarray([render(visuals,get(key),yaw*45) for yaw in range(8)])
        return images[key]
    # Texture content is a candidate bucket, never a reason to retain a type.
    buckets=[g['members'] for g in result['materialReviewGroups']]
    for bucket in buckets:
        unique={r['appearanceSignature']:r for r in bucket};members=list(unique.values())
        for i,a in enumerate(members):
            A=np.sort(a['sizeModelUnits']);A[A<.01]=.01
            for b in members[i+1:]:
                if a['registryId']==b['registryId']:continue
                B=np.sort(b['sizeModelUnits']);B[B<.01]=.01
                if np.max(np.abs(A-B)/np.maximum(A,B))>.15:continue
                ka=(a['registryId'],a['variant']);kb=(b['registryId'],b['variant'])
                pair=tuple(sorted([ka,kb]));
                if pair in seen:continue
                seen.add(pair)
                one,two=projected(ka),projected(kb);best=None
                for shift in range(8):
                    other=np.roll(two,shift,axis=0)
                    mask=(one[...,3]>0)|(other[...,3]>0);intersection=(one[...,3]>0)&(other[...,3]>0)
                    union=int(mask.sum());silhouette=1-int(intersection.sum())/max(1,union)
                    rgb=np.abs(one[...,:3].astype(float)-other[...,:3].astype(float))
                    appearance=float(rgb[mask].mean()/255) if mask.any() else 0
                    metric=(silhouette+appearance*.6,silhouette,appearance,shift)
                    if best is None or metric<best:best=metric
                if best[0]<.24:
                    pairs.append({'a':a,'b':b,'difference':round(best[0],5),'silhouetteDifference':round(best[1],5),
                                  'pixelDifference':round(best[2],5),'yawOffset':best[3]*45,
                                  'decision':'MERGE_REVIEW' if best[0]<.11 else 'SIGNIFICANT_SHAPE_REVIEW'})
        print('near review bucket',len(bucket),'images',len(images),'pairs',len(pairs),flush=True)
    pairs.sort(key=lambda p:p['difference'])
    out={'status':'DIAGNOSTIC_PROJECTIONS_REQUIRE_NATIVE_CONFIRMATION','renderMethod':'Original transformed faces + nearest texture RGBA, alpha threshold64, eight isometric64pxviews; normalizeposition, retaindimensions inpairfilter.',
         'summary':{'projectedStates':len(images),'testedPairs':len(seen),'nearPairs':len(pairs)},'pairs':pairs}
    (OUT/'near-visual-candidates.json').write_text(json.dumps(out,ensure_ascii=False,indent=2),encoding='utf-8')
    thumbnails=[]
    for index,p in enumerate(pairs):
        canvas=Image.new('RGBA',(8*128,110),'#273037')
        for object_index,label in enumerate(['a','b']):
            r=p[label];key=(r['registryId'],r['variant']);array=projected(key)
            for yaw in range(8):canvas.alpha_composite(Image.fromarray(array[yaw]),(yaw*128+object_index*64,15))
        from PIL import ImageDraw
        ImageDraw.Draw(canvas).text((4,83),f"{p['a']['id']}:{p['a']['variant']} / {p['b']['id']}:{p['b']['variant']}  diff {p['difference']:.4f}  {p['decision']}",fill='white')
        file=OUT/'projections'/f"{index:03d}-{p['a']['id']}-{p['b']['id']}.png";file.parent.mkdir(exist_ok=True)
        canvas.convert('RGB').save(file);p['diagnosticImage']=str(file.relative_to(ROOT)).replace('\\','/')
    (OUT/'near-visual-candidates.json').write_text(json.dumps(out,ensure_ascii=False,indent=2),encoding='utf-8')
    print('near done',out['summary'],flush=True)

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--render-candidates',action='store_true');parser.add_argument('--near-only',action='store_true');parser.add_argument('--review-sheets',action='store_true');args=parser.parse_args()
    OUT.mkdir(parents=True,exist_ok=True)
    if args.review_sheets:
        data=json.loads((OUT/'near-visual-candidates.json').read_text(encoding='utf-8'))
        pairs=[p for p in data['pairs'] if not any('stairs' in p[k]['registryId'] or 'assembly_' in p[k]['registryId'] for k in ['a','b'])]
        for start in range(0,len(pairs),40):
            chunk=pairs[start:start+40];canvas=Image.new('RGB',(1024,55*math.ceil(len(chunk)/2)), '#273037')
            for i,p in enumerate(chunk):
                im=Image.open(ROOT/p['diagnosticImage']).resize((512,55));canvas.paste(im,((i%2)*512,(i//2)*55))
            canvas.save(OUT/f'near-review-sheet-{start//40+1:02d}.png')
        print('sheets',len(pairs));return
    if args.near_only:
        visuals=Visuals(vanilla_jar=Path.home()/'.gradle/caches/fabric-loom/1.20.1/minecraft-client.jar')
        near_review(visuals,json.loads((OUT/'visual-candidates.json').read_text(encoding='utf-8')));return
    catalogue=json.loads((RES/'bloodborne_dw/debug_catalogue.json').read_text(encoding='utf-8-sig'))
    by_registry={e['registryId']:e for e in catalogue['entries'] if e['registryId'].startswith('bloodborne_dw:source_') and e.get('kind')=='architecture'}
    remaining=json.loads((RES/'bloodborne_dw/remaining-catalogue.json').read_text(encoding='utf-8-sig'))
    visuals=Visuals(vanilla_jar=Path.home()/'.gradle/caches/fabric-loom/1.20.1/minecraft-client.jar'); inventory=[];same=defaultdict(list); material=defaultdict(list)
    for i,item in enumerate(remaining['objects']):
        registry='bloodborne_dw:'+item['path']
        if registry not in by_registry:continue
        spec=json.loads((RES/'bloodborne_dw/composite'/ (item['path']+'.json')).read_text(encoding='utf-8-sig'))
        if not spec.get('simplePhysicalCollision'):continue
        for variant,row in enumerate(spec['variants']):
            quads=pose_data(visuals,spec,row)
            if not quads:raise ValueError('Empty visible object: '+registry)
            values=[signature(visuals,quads,y*45) for y in range(8)]
            best=min(range(8),key=lambda y:values[y][0]);sig,origin,size=values[best]
            textures=Counter(q[2] for q in quads)
            palette=sorted({sha(visuals.image(t).tobytes()) for t in textures})
            record={'id':by_registry[registry]['temporaryId'],'registryId':registry,'variant':variant,
                    'name':item['variants'][variant]['name'],'canonicalYaw':best*45,'normalizingTranslation':[-v for v in origin],
                    'sizeModelUnits':size,'faceCount':len(quads),'appearanceSignature':sig,
                    'textureContentHashes':palette,'openable':spec.get('openable',False),
                    'climbable':spec.get('climbable',False),'sourceModels':item['variants'][variant].get('sourceModels',[])}
            inventory.append(record);same[sig].append(record);material[tuple(palette)].append(record)
        if i%100==0:print('audited',i,'objects',len(inventory),'states',flush=True)
    exact=[rows for rows in same.values() if len({r['registryId'] for r in rows})>1]
    candidate=[]
    for palette,rows in material.items():
        if len({r['registryId'] for r in rows})>1:
            candidate.append({'members':rows,'kind':'SAME_VISIBLE_TEXTURE_CONTENT_REVIEW_GEOMETRY'})
    result={'schemaVersion':1,'baselineCommit':'27bf67e','status':'CANDIDATES_REQUIRE_GAME_REVIEW',
            'method':'Actual transformed visible faces and UV-sampled RGBA pixels; normalize translation and eight yaw states; filenames and names do not define equality.',
            'summary':{'newObjects':len({r['registryId'] for r in inventory}),'states':len(inventory),'appearanceGroups':len(exact),'materialReviewGroups':len(candidate)},
            'sameAppearanceGroups':exact,'materialReviewGroups':candidate,'inventory':inventory}
    (OUT/'visual-candidates.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps(result['summary']),flush=True)

if __name__=='__main__':
    import traceback
    OUT.mkdir(parents=True,exist_ok=True)
    with (OUT/'visual-audit-console.txt').open('w',encoding='utf-8') as stream:
        sys.stdout=sys.stderr=stream
        try:main()
        except BaseException:
            traceback.print_exc();raise
