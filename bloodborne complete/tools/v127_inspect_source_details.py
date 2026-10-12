import json,zipfile,sqlite3,collections
from pathlib import Path,PurePosixPath
import numpy as np
from v125_map_index import ROOT,MAP,PACK
from v125_map_door_projections import SourceVisuals
v=SourceVisuals();model='minecraft:block/addon/oak_door_2';rows=[]
for vertices,uv,texture,tint in v.quads({'model':model}):
    image=v.image(texture);h,w=image.shape[:2];lo=uv.min(axis=0);hi=uv.max(axis=0)
    # Exhaust the entire displayed UV rectangle, including atlas-wrap edges.
    xs=np.arange(int(np.floor(lo[0]*w/16)),max(int(np.floor(lo[0]*w/16))+1,int(np.ceil(hi[0]*w/16))))%w
    ys=np.arange(int(np.floor(lo[1]*h/16)),max(int(np.floor(lo[1]*h/16))+1,int(np.ceil(hi[1]*h/16))))%h
    alpha=image[ys[:,None],xs[None,:],3];area=float(np.linalg.norm(np.cross(vertices[1]-vertices[0],vertices[3]-vertices[0])))
    rows.append({'texture':texture,'uv':uv.tolist(),'alpha':[int(alpha.min()),int(alpha.max())],'geometricArea':area})
with zipfile.ZipFile(MAP) as z:levels=[n for n in z.namelist() if PurePosixPath(n).name=='level.dat'];special=[n for n in z.namelist() if 'datapacks/' in n or PurePosixPath(n).name.lower() in {'resources.zip','resource.zip'}]
print(json.dumps({'model':model,'faces':rows,'levelFiles':levels,'sourcePackDatapackMembers':special[:12]},ensure_ascii=False))
