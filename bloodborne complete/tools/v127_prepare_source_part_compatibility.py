"""Append exact incomplete source parts as hidden art states of their existing NEW type."""
import collections,copy,json,math
import numpy as np
from pathlib import Path
from v125_map_index import ROOT,MAP,PACK,dump,sha,token
from v127_apply_full_map_poses import write,box,bounds_for_quads,simple_boxes
from v125_map_door_projections import SourceVisuals
from v125_map_verify_extra_pose_uv import source_quads,signature
from v124_visual_audit import Visuals
from import_remaining_assets import Source
from v127_full_map_resolver import CurrentResolver
R=ROOT/'src/architecture/resources';O=ROOT/'docs/v12.7/conversion'
def main():
    classes={r['sourceModel']:r for r in json.loads((ROOT/'docs/remaining-transfer/source-candidates.json').read_text(encoding='utf-8-sig'))['modelClassifications']}
    missing=json.loads((O/'protected-source-exact-matches.json').read_text(encoding='utf-8-sig'))['unmatched'];models=sorted({a['model'] for r in missing for a in r['sourceApplications']})
    native=Visuals(vanilla_jar=Path.home()/'.gradle/caches/fabric-loom/1.20.1/minecraft-client.jar');source=SourceVisuals();export=Source(PACK,Path.home()/'.gradle/caches/fabric-loom/1.20.1/minecraft-client.jar')
    collision=json.loads((R/'bloodborne_dw/imported-full-map-v127.json').read_text(encoding='utf-8-sig'));catalogpath=R/'bloodborne_dw/remaining-catalogue.json';catalog=json.loads(catalogpath.read_text(encoding='utf-8-sig'));objects={r['path']:r for r in catalog['objects']};manifest=R/'bloodborne_dw/imported-source-parts-v127.json';rows=json.loads(manifest.read_text(encoding='utf-8-sig'))['parts'] if manifest.exists() else []
    resolver=CurrentResolver();wanted=[(classes.get(model,{}).get('registryId',''),[{'model':model}]) for model in models]
    for candidate in resolver.current_whole:
        groups=collections.defaultdict(list)
        for a in candidate['applications']:groups[tuple(a.get('offset',[0,0,0]))].append({k:v for k,v in a.items() if k!='offset'})
        wanted.extend((candidate['registryId'],apps) for apps in groups.values())
    for reg,apps in wanted:
        if not reg.startswith('bloodborne_dw:source_') or not (R/'bloodborne_dw/composite'/f'{reg.split(":")[1]}.json').exists():continue
        p=R/'bloodborne_dw/composite'/f'{reg.split(":")[1]}.json';d=json.loads(p.read_text(encoding='utf-8-sig'))
        if any(resolver.match(v.get('sourceClosedApplications',[]),apps) is not None for v in d['variants']):continue
        quads=source_quads(source,apps);lo,hi=bounds_for_quads(quads);offset=[0.,round(-float(lo[1]),8),0.];parts=[{'model':export.export_application_model(a),'offset':offset,'yaw':a.get('y',0),'pitch':a.get('x',0)} for a in apps];index=len(d['variants']);assert index<16,reg
        physical=bool(d['variants'][0]['poses']['closed'].get('collision'));simple=simple_boxes(quads,offset,physical);visible=box(lo+offset,hi+offset);aim=[]
        for yaw in range(8):
            if simple[yaw]:aim.append(copy.deepcopy(simple[yaw]));continue
            points=np.asarray([q[0] for q in quads]).reshape(-1,3);a=math.pi*yaw/4;c,s=math.cos(a),math.sin(a);x=points[:,0]-8;z=points[:,2]-8;turned=np.column_stack((x*c-z*s+8,points[:,1],x*s+z*c+8))+offset;low,high=turned.min(0),turned.max(0)
            for axis in range(3):
                if high[axis]-low[axis]<.125:center=(high[axis]+low[axis])/2;low[axis]=center-.0625;high[axis]=center+.0625
            aim.append([box(low,high)])
        pose={'parts':parts,'collision':simple[0],'selection':aim[0]};v={'poses':{'closed':pose,'open':copy.deepcopy(pose)},'visibleBounds':visible,'sourcePlacementOffset':offset,'sourceClosedApplications':apps,'sourceOpenApplications':[], 'v127SourceOnlyCompatibilityPart':True,'sourceVariant':'Сохранённая неполная исходная часть'};d['variants'].append(v);write(p,d)
        actual=[(vertices-np.asarray(offset),uv,t,ti) for part in parts for vertices,uv,t,ti in native.quads(part)];assert signature(quads,source)==signature(actual,native)
        collision['objects'].append({'registryId':reg,'variant':index,'rotations':[{'collision':simple[yaw],'aim':aim[yaw]} for yaw in range(8)],'sourceVariant':v['sourceVariant']})
        objects[reg.split(':')[1]]['variants'].append({'variant':index,'name':d['displayName'],'sourceModels':[a['model'] for a in apps],'sourceOnlyCompatibility':True})
        rows.append({'registryId':reg,'variant':index,'mainId':next(r['temporaryId'] for r in json.loads((R/'bloodborne_dw/debug_catalogue.json').read_text(encoding='utf-8-sig'))['entries'] if r['registryId']==reg),'sourceApplications':apps,'defaultInstallationPose':0,'status':'PASS_EXACT_SOURCE_VERTEX_UV_RGBA_TINTINDEX'})
    write(R/'bloodborne_dw/imported-full-map-v127.json',collision);write(catalogpath,catalog);write(R/'bloodborne_dw/imported-source-parts-v127.json',{'schemaVersion':1,'parts':rows,'policy':'Exact incomplete source compatibility states only; no separate Creative item, hand pick returns normal complete installation, ordinary art cycle excludes these fragments.'});dump(O/'source-part-compatibility-proof.json',{'status':'PASS_EXACT_SOURCE_PART_STATES','rows':rows,'originalV12Changed':False,'sourceMapConverted':False});print(token({'parts':len(rows),'types':len({r['registryId'] for r in rows})}))
if __name__=='__main__':main()
