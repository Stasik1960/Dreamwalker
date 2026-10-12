"""Find exact source-to-existing render states; no production writes."""
import collections,json,copy
import numpy as np
from pathlib import Path
from v125_map_index import ROOT,dump,token
from v125_map_door_projections import SourceVisuals
from v125_map_verify_extra_pose_uv import source_quads,signature
from v124_visual_audit import Visuals,rot
from v125_map_resolve import rotation
R=ROOT/'src/architecture/resources/bloodborne_dw';O=ROOT/'docs/v12.7/conversion'
def main():
    doc=json.loads((O/'finite-cell-resolutions.json').read_text(encoding='utf-8-sig'));source=SourceVisuals();native=Visuals(vanilla_jar=Path.home()/'.gradle/caches/fabric-loom/1.20.1/minecraft-client.jar')
    wanted={token(r['sourceApplications']):r for r in doc['rows'] if r['decision']['action']=='UNRESOLVED_VISIBLE'}
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
            if len(q)!=len(expected):continue
            for yaw in range(8):
                actual=[(rot(v,1,-yaw*45,[8,8,8]),uv,t,ti) for v,uv,t,ti in q];ap=np.asarray([v for v,*_ in actual]).reshape(-1,3);shift=ep.mean(0)-ap.mean(0)
                if signature([(v+shift,uv,t,ti) for v,uv,t,ti in actual],native)!=es:continue
                local=rotation((shift/16).tolist(),-yaw);props={'variant':str(vi),'rotation':str(yaw),'profile':'base','open':str(opened).lower()}
                if 'window' in reg:props['mount']='vertical'
                payload={'ImportedTypeVersion':1}
                if any(abs(x)>1e-9 for x in local):payload['SourceShift']=local
                matches.append({'action':'OWNER','registryId':reg,'properties':props,'payload':payload,'sourceShift':local,'resolution':'EXACT_EXISTING_RENDER_VERTEX_UV_RGBA_STATE','mountY':0.})
        if matches:
            matches.sort(key=lambda x:(x['properties']['open']=='true',x['registryId'],int(x['properties']['variant']),int(x['properties']['rotation'])))
            rows.append({'sourceApplications':r['sourceApplications'],'sourceApplicationToken':key,'decision':matches[0],'allExactStates':matches,'faces':sum(es.values()),'status':'PASS_EXACT_VERTEX_UV_RGBA_TINTINDEX'})
        else:unmatched.append({'sourceApplications':r['sourceApplications'],'sourceState':r['sourceState'],'count':r['count']})
    dump(O/'protected-source-exact-matches.json',{'status':'PASS_EXACT_MATCHES_NO_PRODUCTION_CHANGES','rows':rows,'unmatched':unmatched,'matchedApplicationCombinations':len(rows),'unmatchedApplicationCombinations':len(unmatched)})
    print(json.dumps({'matched':len(rows),'unmatched':len(unmatched),'models':collections.Counter(a['model'] for r in unmatched for a in r['sourceApplications'])}))
if __name__=='__main__':main()
