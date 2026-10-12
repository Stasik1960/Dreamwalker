"""Find exact source-to-existing render states; no production writes."""
import collections,json,copy,hashlib,itertools
import numpy as np
from pathlib import Path
from v125_map_index import ROOT,dump,token
from v125_map_door_projections import SourceVisuals
from v125_map_verify_extra_pose_uv import source_quads,signature
from v124_visual_audit import Visuals,rot
from v125_map_resolve import rotation
R=ROOT/'src/architecture/resources/bloodborne_dw';O=ROOT/'docs/v12.7/conversion'
def planar_equal(one,one_visual,two,two_visual):
    """Exact affine UV + union coverage, insensitive to splitting a planar face."""
    def groups(quads,visual):
        result=collections.defaultdict(list)
        for v,uv,texture,tint in quads:
            v=np.asarray(v);lo,hi=v.min(0),v.max(0);axes=[i for i in range(3) if hi[i]-lo[i]>1e-6]
            if len(axes)!=2:return None
            fixed=next(i for i in range(3) if i not in axes);p=v[:,axes]
            if set(map(tuple,np.round(p,6)))!=set(itertools.product(*[(round(lo[i],6),round(hi[i],6)) for i in axes])):return None
            a=np.column_stack((p,np.ones(4)));coeff=np.linalg.lstsq(a,np.asarray(uv),rcond=None)[0]
            if np.max(np.abs(a@coeff-np.asarray(uv)))>1e-6:return None
            rgba=visual.image(texture);key=(fixed,round(lo[fixed],6),tuple(np.round(coeff.reshape(-1),6)),rgba.shape,hashlib.sha256(rgba.tobytes()).hexdigest(),tint)
            result[key].append(tuple([round(lo[i],6) for i in axes]+[round(hi[i],6) for i in axes]))
        return result
    a,b=groups(one,one_visual),groups(two,two_visual)
    if a is None or b is None or set(a)!=set(b):return False
    for key in a:
        rects=a[key]+b[key];xs=sorted({v[i] for v in rects for i in (0,2)});ys=sorted({v[i] for v in rects for i in (1,3)})
        for x0,x1 in zip(xs,xs[1:]):
            for y0,y1 in zip(ys,ys[1:]):
                x,y=(x0+x1)/2,(y0+y1)/2
                if any(v[0]<x<v[2] and v[1]<y<v[3] for v in a[key])!=any(v[0]<x<v[2] and v[1]<y<v[3] for v in b[key]):return False
    return True
def main():
    doc=json.loads((O/'finite-cell-resolutions.json').read_text(encoding='utf-8-sig'));source=SourceVisuals();native=Visuals(vanilla_jar=Path.home()/'.gradle/caches/fabric-loom/1.20.1/minecraft-client.jar')
    wanted={token(r['sourceApplications']):r for r in doc['rows'] if r['decision']['action']=='UNRESOLVED_VISIBLE'}
    for model in ['aca_door_1']:
        apps=[{'model':'minecraft:block/'+model},{'model':'minecraft:block/aca_door_2','offset':[0,32,0]}]
        wanted[token(apps)]={'sourceApplications':apps,'sourceState':'EXPLICIT_COMPLETE_SOURCE_ACACIA_DOOR_PAIR','count':0,'wholeRecipe':True}
    candidates=[]
    for p in (R/'composite').glob('*.json'):
        d=json.loads(p.read_text(encoding='utf-8-sig'))
        if d['id'].startswith('bloodborne_dw:source_'):continue
        for vi,v in enumerate(d['variants']):
            for opened,pose in v['poses'].items():
                quads=[q for part in pose['parts'] if not part.get('emissive') for q in native.quads(part)]
                if not quads:continue
                candidates.append((d['id'],vi,opened=='open',quads))
    rows=[];unmatched=[]
    for key,r in wanted.items():
        try:expected=source_quads(source,r['sourceApplications']);es=signature(expected,source)
        except (KeyError,FileNotFoundError) as error:
            unmatched.append({'sourceApplications':r['sourceApplications'],'sourceState':r['sourceState'],'count':r['count'],'sourceDefect':repr(error)});continue
        ep=np.asarray([q[0] for q in expected]).reshape(-1,3);matches=[]
        for reg,vi,opened,q in candidates:
            if len(q)!=len(expected) and (len(q)>100 or len(expected)>100):continue
            for yaw in range(8):
                actual=[(rot(v,1,-yaw*45,[8,8,8]),uv,t,ti) for v,uv,t,ti in q];ap=np.asarray([v for v,*_ in actual]).reshape(-1,3);shift=(ep.min(0)+ep.max(0)-ap.min(0)-ap.max(0))/2
                posed=[(v+shift,uv,t,ti) for v,uv,t,ti in actual]
                exact=signature(posed,native)==es
                if not exact and not planar_equal(expected,source,posed,native):continue
                local=rotation((shift/16).tolist(),-yaw);props={'variant':str(vi),'rotation':str(yaw),'profile':'base','open':str(opened).lower()}
                if 'window' in reg:props['mount']='vertical'
                payload={'ImportedTypeVersion':1}
                if any(abs(x)>1e-9 for x in local):payload['SourceShift']=local
                matches.append({'action':'OWNER','registryId':reg,'properties':props,'payload':payload,'sourceShift':local,'resolution':'EXACT_EXISTING_RENDER_VERTEX_UV_RGBA_STATE' if exact else 'EXACT_EXISTING_PLANAR_FACE_UNION_AFFINE_UV_RGBA_STATE','mountY':0.})
        if matches:
            matches.sort(key=lambda x:(x['properties']['open']=='true',x['registryId'],int(x['properties']['variant']),int(x['properties']['rotation'])))
            rows.append({'sourceApplications':r['sourceApplications'],'sourceApplicationToken':key,'decision':matches[0],'allExactStates':matches,'faces':sum(es.values()),'status':'PASS_EXACT_VERTEX_UV_RGBA_TINTINDEX'})
        else:unmatched.append({'sourceApplications':r['sourceApplications'],'sourceState':r['sourceState'],'count':r['count']})
    dump(O/'protected-source-exact-matches.json',{'status':'PASS_EXACT_MATCHES_NO_PRODUCTION_CHANGES','rows':rows,'unmatched':unmatched,'matchedApplicationCombinations':len(rows),'unmatchedApplicationCombinations':len(unmatched)})
    print(json.dumps({'matched':len(rows),'unmatched':len(unmatched),'models':collections.Counter(a['model'] for r in unmatched for a in r['sourceApplications'])}))
if __name__=='__main__':main()
