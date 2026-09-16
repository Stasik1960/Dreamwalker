"""Resolve every actual blockstate/model, not the vanilla block used as a palette ID."""
import copy, functools, itertools, json, math, os, zipfile
from pathlib import Path
import numpy as np

ROOT=Path(__file__).resolve().parents[1]
RES=ROOT/'src/main/resources'
ASSETS=RES/'assets/bloodborne_blocks'
VANILLA=Path(os.environ.get('BLOODBORNE_VANILLA_JAR',r'C:\temp\bloodborne-gradle\caches\fabric-loom\1.20.1\minecraft-client.jar'))
ZIP=zipfile.ZipFile(VANILLA)
DATA=json.loads((RES/'bloodborne_blocks/definitions.json').read_text(encoding='utf-8-sig'))

@functools.lru_cache(None)
def model(name):
    ns,local=name.split(':') if ':' in name else ('minecraft',name)
    p=f'assets/{ns}/models/{local}.json'
    if name.startswith('builtin/') or local.startswith('builtin/'):return {}
    if (RES/p).exists():d=json.loads((RES/p).read_text(encoding='utf-8-sig'))
    else:d=json.loads(ZIP.read(p))
    base=copy.deepcopy(model(d['parent'])) if 'parent' in d else {}
    textures={**base.get('textures',{}),**d.get('textures',{})}
    base.update(d);base['textures']=textures
    return base

def condition(cond,state):
    if 'OR' in cond:return any(condition(x,state) for x in cond['OR'])
    if 'AND' in cond:return all(condition(x,state) for x in cond['AND'])
    return all(state.get(k) in str(v).split('|') for k,v in cond.items())

def applications(blockstate,state):
    out=[]
    for key,value in blockstate.get('variants',{}).items():
        if condition(dict(x.split('=') for x in key.split(',') if x),state):
            out.append(value if isinstance(value,list) else [value])
    for part in blockstate.get('multipart',[]):
        if condition(part.get('when',{}),state):
            value=part['apply'];out.append(value if isinstance(value,list) else [value])
    return out

def rotation(axis,angle):
    a=math.radians(angle);c=math.cos(a);s=math.sin(a)
    r=np.eye(3);i=(axis+1)%3;j=(axis+2)%3
    r[i,i]=c;r[i,j]=-s;r[j,i]=s;r[j,j]=c
    return r

def transform(element,app):
    mat=np.eye(3);offset=np.zeros(3)
    if 'rotation' in element:
        r=element['rotation'];axis='xyz'.index(r['axis']);mat=rotation(axis,r['angle'])
        if r.get('rescale'):
            scale=np.ones(3);scale[[i for i in range(3) if i!=axis]]=1/math.cos(math.radians(r['angle']))
            mat=mat@np.diag(scale)
        origin=np.array(r['origin']);offset=origin-mat@origin
    # Minecraft variant rotations are clockwise, around model centre.
    variant=rotation(1,-app.get('y',0))@rotation(0,-app.get('x',0))
    render_offset=np.array(model(app['model']).get('bloodborne_offset',[0,0,0]))
    return variant@mat/16,(variant@offset+8-variant@np.array([8,8,8]))/16+variant@render_offset

def corners(element,app):
    mat,off=transform(element,app)
    return np.array(list(itertools.product(*zip(element['from'],element['to']))))@mat.T+off

def elements(app):return model(app['model']).get('elements',[])

def default_apps(b):
    bs=json.loads((ASSETS/'blockstates'/f'{b["id"]}.json').read_text())
    return [choices[0] for choices in applications(bs,b['default'])]

def write_catalog():
    rows=[]
    for b in DATA['blocks']:
        apps=default_apps(b);pts=[p for a in apps for e in elements(a) for p in corners(e,a)]
        if pts:
            pts=np.array(pts);lo=pts.min(axis=0).tolist();hi=pts.max(axis=0).tolist()
        else:lo=hi=[0,0,0]
        rows.append({'id':b['id'],'kind':b['kind'],'models':[a['model'] for a in apps], 'min':lo,'max':hi,'states':len(b['states'])})
    (ROOT/'docs/catalog.json').write_text(json.dumps(rows,indent=2)+'\n')
    print('Catalog:',len(rows),'objects')
    return rows

if __name__=='__main__':write_catalog()
