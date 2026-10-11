"""Read-only actual render geometry review for post-V12 stair candidates."""
from __future__ import annotations
import hashlib
import json
import itertools
import io
import math
from collections import Counter, defaultdict
from functools import lru_cache
from pathlib import Path
from gallery_platform_plan import ModelGeometry
from inspect_imported_collision_semantics import element_boxes

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT/'src/architecture/resources'
OUT = ROOT/'docs/v12.4-dedup/stair-review'

@lru_cache(None)
def inherited(identifier):
    doc = MODELS.document(identifier)
    parent = inherited(doc['parent']) if doc.get('parent') and 'builtin/' not in doc['parent'] else {}
    return {**parent, **doc, 'textures': {**parent.get('textures', {}), **doc.get('textures', {})}}

def resolved_texture(textures, value):
    seen = set()
    while value.startswith('#'):
        if value in seen: raise ValueError('Texture cycle '+value)
        seen.add(value)
        value = textures[value[1:]]
    namespace, path = value.split(':', 1) if ':' in value else ('minecraft', value)
    p = RES/f'assets/{namespace}/textures/{path}.png'
    data = p.read_bytes() if p.is_file() else MODELS.vanilla.read(f'assets/{namespace}/textures/{path}.png')
    return value, hashlib.sha256(data).hexdigest()

def native_stair_shape(elements):
    if not elements or len(elements) > 3:
        return None
    for e in elements:
        if not e['physical'] or any(abs(x-round(x/8)*8) > 1e-6 or x < -1e-6 or x > 16+1e-6 for p in e['points'] for x in p):
            return None
    occupancy = {cell for cell in itertools.product(range(2), repeat=3)
        if any(all(e['from'][i]-1e-6 < 4+8*cell[i] < e['to'][i]+1e-6 for i in range(3)) for e in elements)}
    lower = {(x,0,z) for x in range(2) for z in range(2)}
    upper = {(x,1,z) for x in range(2) for z in range(2)}
    if not(lower.issubset(occupancy) or upper.issubset(occupancy)):
        return None
    shape = {5:'outer', 6:'straight', 7:'inner'}.get(len(occupancy))
    if not shape: return None
    return {'half':'bottom' if lower.issubset(occupancy) else 'top', 'shape':shape,
        'occupancy':sorted(occupancy)}

def main():
    global MODELS
    MODELS = ModelGeometry(vanilla_jar=Path('C:/Users/vakir/.gradle/caches/fabric-loom/1.20.1/minecraft-client.jar'))
    catalogue = json.loads((RES/'bloodborne_dw/debug_catalogue.json').read_text(encoding='utf-8-sig'))
    nums = {row['registryId']: row['temporaryId'] for row in catalogue['entries']}
    rows = []
    for p in sorted((RES/'bloodborne_dw/composite').glob('source_*.json')):
        spec = json.loads(p.read_text(encoding='utf-8-sig'))
        if not ('stairs' in spec['id'] or 'ladder' in spec['id']): continue
        variants = []
        for index, var in enumerate(spec['variants']):
            pose = var['poses']['closed']
            elements = element_boxes(pose, MODELS, include_points=True)
            textures = set()
            for part in pose['parts']:
                doc = inherited(part['model'])
                for element in MODELS.elements(part['model']):
                    for face in element.get('faces', {}).values():
                        textures.add(resolved_texture(doc.get('textures', {}), face['texture']))
            pts = [pt for e in elements for pt in e['points']]
            bounds = [round(min(pt[i] for pt in pts), 7) for i in range(3)] + [round(max(pt[i] for pt in pts), 7) for i in range(3)] if pts else [0]*6
            variants.append({'variant': index, 'bounds': bounds, 'elementCount': len(elements),
                'boxes': [{k:v for k,v in e.items() if k not in ('points','model')} for e in elements],
                'physical': pose.get('collision', []), 'sourceStateRefs': var.get('sourceStateRefs', []),
                'parts': pose['parts'], 'textures': [{'id': t, 'sha256': h} for t,h in sorted(textures)],
                'textureHashSet': sorted({h for _,h in textures}), 'nativeStair': native_stair_shape(elements)})
        rows.append({'id': spec['id'], 'number': nums.get(spec['id']), 'name': spec['displayName'], 'variants': variants})
    groups = defaultdict(list)
    native_groups = defaultdict(list)
    for row in rows:
        for v in row['variants']:
            groups[tuple(v['textureHashSet'])].append([row['number'], row['id'], v['variant']])
            if v['nativeStair']:
                native_groups[tuple(v['textureHashSet'])].append([row['number'],row['id'],v['variant'],v['nativeStair']])
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT/'actual-render-geometry.json').write_text(json.dumps({'rows': rows, 'textureAppearanceCandidates': [{'hashes': list(k), 'members': v} for k,v in groups.items()], 'nativeStairGroups': [{'hashes':list(k),'members':v} for k,v in native_groups.items()]}, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
    (OUT/'native-stair-summary.txt').write_text('\n'.join(','.join(sorted({m[0] for m in members}))+' :: '+str(Counter(m[3]['shape'] for m in members))+' :: '+members[0][1] for members in native_groups.values())+'\n', encoding='utf-8')
    render_review(rows)
    print(json.dumps({'namedCandidates':len(rows),'appearanceCandidateGroups':len(groups),'variants':sum(len(r['variants']) for r in rows),'output':str(OUT/'actual-render-geometry.json')}))
    for hashes, members in groups.items():
        print(','.join(m[0] for m in members), ' :: ', str(hashes)[:32], ' :: ', members[0][1])

def render_review(rows):
    import numpy as np
    from PIL import Image, ImageDraw
    from gallery_platform_plan import rotate
    from import_remaining_assets import implicit_uv
    side_index={'down':[0,4,5,1], 'up':[3,7,6,2], 'north':[4,0,2,6], 'south':[1,5,7,3], 'west':[0,1,3,2], 'east':[5,4,6,7]}
    @lru_cache(None)
    def texture(value):
        ns, name = value.split(':',1) if ':' in value else ('minecraft',value)
        path=RES/f'assets/{ns}/textures/{name}.png'
        data=path.read_bytes() if path.is_file() else MODELS.vanilla.read(f'assets/{ns}/textures/{name}.png')
        im=Image.open(io.BytesIO(data)).convert('RGBA')
        if im.height>im.width:im=im.crop((0,0,im.width,im.width))
        return np.array(im)
    def image(var,size=154):
        polys=[]
        for part in var['parts']:
            doc=inherited(part['model'])
            boxes=element_boxes({'parts':[part]},MODELS,include_points=True)
            for e,points in zip(MODELS.elements(part['model']),boxes):
                verts=np.array([rotate(rotate(p,1,-25,[8,8,8]),0,20,[8,8,8]) for p in points['points']])
                for side,face in e.get('faces',{}).items():
                    value,_=resolved_texture(doc['textures'],face['texture'])
                    u,v,U,V=face.get('uv',implicit_uv(e,side))
                    uv=np.array([[u,V],[U,V],[U,v],[u,v]],dtype=float)
                    uv=np.roll(uv,-face.get('rotation',0)//90,axis=0)/16
                    polys.append((verts[side_index[side]],uv,texture(value)))
        canvas=np.zeros((size,size,4),dtype=np.uint8);canvas[:]=[32,34,40,255]
        depth=np.full((size,size),-np.inf)
        if not polys:return Image.fromarray(canvas)
        pts=np.concatenate([p[0] for p in polys]);low=pts.min(axis=0);hi=pts.max(axis=0)
        scale=min(6.5,(size-12)/max(hi[0]-low[0],hi[1]-low[1],.1));center=(low+hi)/2
        for vertices,uv,tex in polys:
            vv=(vertices-center)*scale;vv[:,0]+=size/2;vv[:,1]=size/2-vv[:,1]
            normal=np.cross(vertices[1]-vertices[0],vertices[2]-vertices[0]);norm=np.linalg.norm(normal)
            shade=.7+.3*abs(normal[2]/norm) if norm>1e-8 else 1
            for ids in ([0,1,2],[0,2,3]):
                tri=vv[ids];tuv=uv[ids];x0=max(0,int(np.floor(tri[:,0].min())));x1=min(size,int(np.ceil(tri[:,0].max()))+1);y0=max(0,int(np.floor(tri[:,1].min())));y1=min(size,int(np.ceil(tri[:,1].max()))+1)
                if x1<=x0 or y1<=y0:continue
                xx,yy=np.meshgrid(np.arange(x0,x1)+.5,np.arange(y0,y1)+.5);a,b,c=tri;den=(b[1]-c[1])*(a[0]-c[0])+(c[0]-b[0])*(a[1]-c[1])
                if abs(den)<1e-8:continue
                w0=((b[1]-c[1])*(xx-c[0])+(c[0]-b[0])*(yy-c[1]))/den;w1=((c[1]-a[1])*(xx-c[0])+(a[0]-c[0])*(yy-c[1]))/den;w2=1-w0-w1
                z=w0*a[2]+w1*b[2]+w2*c[2];tx=w0*tuv[0,0]+w1*tuv[1,0]+w2*tuv[2,0];ty=w0*tuv[0,1]+w1*tuv[1,1]+w2*tuv[2,1]
                colors=tex[np.clip((ty*tex.shape[0]).astype(int),0,tex.shape[0]-1),np.clip((tx*tex.shape[1]).astype(int),0,tex.shape[1]-1)].copy()
                mask=(w0>=-1e-6)&(w1>=-1e-6)&(w2>=-1e-6)&(z>depth[y0:y1,x0:x1])&(colors[:,:,3]>32)
                colors[:,:,:3]=(colors[:,:,:3]*shade).astype(np.uint8);canvas[y0:y1,x0:x1][mask]=colors[mask];depth[y0:y1,x0:x1][mask]=z[mask]
        return Image.fromarray(canvas)
    for batch in range(math.ceil(len(rows)/48)):
        sheet=Image.new('RGB',(8*160,6*190),(32,34,40));draw=ImageDraw.Draw(sheet)
        for i,row in enumerate(rows[batch*48:(batch+1)*48]):
            x=i%8*160;y=i//8*190;sheet.paste(image(row['variants'][0]),(x+3,y))
            draw.text((x+4,y+155),row['number']+' '+row['id'].replace('bloodborne_dw:source_','')[:17],fill='white')
            draw.text((x+4,y+170),','.join(str(n) for n in row['variants'][0]['bounds'])[:24],fill='white')
        sheet.save(OUT/f'actual-stair-models-{batch}.png')

if __name__ == '__main__':
    try:
        main()
    except BaseException:
        import traceback
        OUT.mkdir(parents=True, exist_ok=True)
        (OUT/'audit-error.txt').write_text(traceback.format_exc(), encoding='utf-8')
        raise
