"""Exhaustive pose/physics preservation proof for the registry migration."""
from pathlib import Path
import hashlib,json,sqlite3,sys
import numpy as np
from build_unified_registry import (ROOT,MeshStore,stream_members,serial,key,full,props,FACING,rotate_mesh,protected)

def close(a,b):
    if isinstance(a,(int,float))and isinstance(b,(int,float)):return abs(a-b)<=1e-6
    if isinstance(a,list)and isinstance(b,list):return len(a)==len(b)and all(close(x,y)for x,y in zip(a,b))
    if isinstance(a,dict)and isinstance(b,dict):return a.keys()==b.keys()and all(close(a[k],b[k])for k in a)
    return a==b

def read(p):return json.loads(p.read_bytes())
def profile(g,i,s):
    v=g['blocks'][i]['states'][s];return g.get('profiles',{}).get(v.get('ref'),v)

def verify(root=ROOT):
    source=root/'build/unified-source/city';stage=root/'build/unified-generated'
    old=read(source/'definitions.json');new=read(stage/'definitions.json');newdefs={d['id']:d for d in new['blocks']}
    mapping=read(stage/'unified-migration.json')['states'];og=read(source/'geometry.json');ng=read(stage/'geometry.json')
    meshes=MeshStore(source,root/'build/unified-model-cache.sqlite')
    db=sqlite3.connect(root/'build/unified-proof-meshes.sqlite');db.execute('CREATE TABLE IF NOT EXISTS meshes(id TEXT PRIMARY KEY,payload BLOB)');db.execute('DELETE FROM meshes')
    for ident,value in stream_members(stage/'owner-meshes.json.gz'):db.execute('INSERT INTO meshes VALUES(?,?)',(ident,serial(value)))
    db.commit();seen=set();artchecks=0;maximum=0.;geochecks=0;count=0;protected_count=0
    for d in old['blocks']:
        if protected(d):
            assert newdefs[d['id']]==d,('protected definition changed',d['id']);protected_count+=1
        for s,values in d['states'].items():
            name=full(d['id'],s);assert name in mapping,('missing state',name)
            target=mapping[name];ident=target['id'].split(':')[-1];ns=key(target['properties']);nd=newdefs[ident]
            assert ns in nd['states']and values==nd['states'][ns],('invalid target state',name)
            assert target.get('rootOffset',[0,0,0])==[0,0,0]
            # Coordinates do not change: compare actual saved-facing profiles.
            assert close(profile(og,d['id'],s).get('cells'),profile(ng,ident,ns).get('cells')),('physics changed',name,ns)
            geochecks+=1
            if d.get('models'):
                mid=d['models'][s];newmid=nd['models'][ns];turn=FACING.index(target['properties'].get('facing','north'))if nd.get('unified')else 0
                stamp=(mid,newmid,turn)
                if stamp not in seen:
                    a=meshes.get(mid);row=db.execute('SELECT payload FROM meshes WHERE id=?',(newmid,)).fetchone();assert row,('missing mesh',newmid)
                    b=rotate_mesh(json.loads(row[0]),turn)
                    assert len(a['polygons'])==len(b['polygons']),('polygon count',name)
                    for ap,bp in zip(a['polygons'],b['polygons']):
                        assert {k:v for k,v in ap.items()if k!='vertices'}=={k:v for k,v in bp.items()if k!='vertices'},('material/face behavior',name)
                        av,bv=np.asarray(ap['vertices']),np.asarray(bp['vertices']);assert av.shape==bv.shape,('vertex shape',name)
                        error=float(np.max(np.abs(av-bv)));maximum=max(maximum,error);assert error<=1e-6,('render pose changed',name,error)
                    artchecks+=1;seen.add(stamp)
            count+=1
    assert count==len(mapping),('extra mapping states',count,len(mapping))
    result={'passed':True,'oldIds':len(old['blocks']),'newIds':len(new['blocks']),'mappedStates':count,'physicsChecks':geochecks,'uniqueRenderChecks':artchecks,'maxVertexError':maximum,'protectedDefinitions':protected_count,'allCoordinatesRetained':True,'canonicalMeshFacingApplied':True,'sourceSha256':hashlib.sha256((source/'definitions.json').read_bytes()).hexdigest(),'mappingSha256':hashlib.sha256((stage/'unified-migration.json').read_bytes()).hexdigest()}
    (root/'build/unified-registry-proof.json').write_bytes(serial(result)+b'\n');print(json.dumps(result));db.close();meshes.db.close();return result
if __name__=='__main__':verify()
