"""Exhaust every source face's actual UV pixel rectangle; source ZIP read-only.

For animated textures every frame is considered, preventing deletion of a
temporarily blank blinking sprite. UV outside the sprite is conservatively
kept, never guessed. Reports only fully invisible model faces.
"""
import json,numpy as np
from v125_map_index import ROOT,token,sha
from v125_map_door_projections import SourceVisuals
from v127_apply_full_map_poses import write
v=SourceVisuals();rows=[];failures=[]
for model in sorted(v.models):
    try:quads=v.quads({'model':model});faces=[]
    except Exception as e:failures.append({'model':model,'reason':str(e)});continue
    if not quads:continue
    zero=True
    for vertices,uv,texture,tint in quads:
        area=float(np.linalg.norm(np.cross(vertices[1]-vertices[0],vertices[3]-vertices[0])))
        if area<1e-8:continue
        if np.min(uv)<0 or np.max(uv)>16:zero=False;break
        try:image=v.image(texture)
        except Exception as e:failures.append({'model':model,'texture':texture,'reason':str(e)});zero=False;break
        h,w=image.shape[:2];ns,local=texture.split(':',1) if ':' in texture else ('minecraft',texture);meta=v.prefix+'assets/'+ns+'/textures/'+local+'.png.mcmeta';animation=None
        if meta in v.pack.namelist():animation=json.loads(v.pack.read(meta)).get('animation')
        fw=int(animation.get('width',w)) if animation else w;fh=int(animation.get('height',fw)) if animation else h
        lo=uv.min(axis=0);hi=uv.max(axis=0);xs=np.arange(int(np.floor(lo[0]*fw/16)),max(int(np.floor(lo[0]*fw/16))+1,int(np.ceil(hi[0]*fw/16))))%fw;ys=np.arange(int(np.floor(lo[1]*fh/16)),max(int(np.floor(lo[1]*fh/16))+1,int(np.ceil(hi[1]*fh/16))))%fh
        maximum=0
        for yy in range(0,h,fh):
            for xx in range(0,w,fw):maximum=max(maximum,int(image[(ys[:,None]+yy)%h,(xs[None,:]+xx)%w,3].max()))
        faces.append({'texture':texture,'uv':uv.tolist(),'maximumAlphaOverAllFrames':maximum,'area':area})
        if maximum:zero=False;break
    if zero:rows.append({'model':model,'status':'ALL_RENDERED_SOURCE_UV_PIXELS_ALPHA_ZERO','faceEvidence':faces})
write(ROOT/'docs/v12.7/conversion/source-zero-uv-models.json',{'status':'PASS_EXHAUSTIVE_SOURCE_UV_ALPHA_AUDIT','sourceModelCount':len(v.models),'zeroModelCount':len(rows),'rows':rows,'sourceDependencyFailures':failures,'policy':'Whole original source image/geometry unchanged; only independently invisible source carriers may be removed by plan, with exact coordinate runs.'})
print(token({'models':len(v.models),'zero':len(rows),'failures':len(failures),'oakHeader':next((r for r in rows if r['model']=='minecraft:block/addon/oak_door_2'),None)}))
