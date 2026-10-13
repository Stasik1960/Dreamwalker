"""Exact hidden compatibility connection poses for approved retired map content.

Only existing reserved NEW canonical IDs and append-only descriptor poses change.
Historic alias manifests, Creative offers and original V12 files stay intact.
"""
import collections,copy,json
import numpy as np
from v125_map_index import ROOT,PACK,token,app_clean
from v127_apply_full_map_poses import write,bounds_for_quads,box,simple_boxes,VANILLA
from v127_full_map_resolver import CurrentResolver,pose_proposals
from v125_map_door_projections import SourceVisuals
from v125_map_verify_extra_pose_uv import source_quads,signature
from v124_visual_audit import Visuals
from import_remaining_assets import Source
R=ROOT/'src/architecture/resources';O=ROOT/'docs/v12.7/conversion'
def main():
    resolver=CurrentResolver();assert resolver.preserveRetired;gaps=json.loads((O/'full-conversion-known-gaps.json').read_text(encoding='utf-8-sig'))['rows'];counts=collections.Counter((r['sourceState'],token(r['sourceApplications'])) for r in gaps);source=SourceVisuals();native=Visuals(vanilla_jar=VANILLA);export=Source(PACK,VANILLA);groups={};checks=[];sparse=json.loads((R/'bloodborne_dw/imported-full-map-v127.json').read_text(encoding='utf-8-sig'))
    for (state,t),count in sorted(counts.items()):
        carrier,_,tail=state.partition('[');props=dict(p.split('=',1) for p in tail.rstrip(']').split(',') if p);reg=resolver.carriers[carrier];assert reg in resolver.retired,(state,reg);g=groups.setdefault(reg,{});g.setdefault(t,{'applications':json.loads(t),'states':[],'count':0});g[t]['states'].append(state);g[t]['count']+=count
    for reg,appsByToken in groups.items():
        p=R/'bloodborne_dw/composite'/f'{reg.split(":")[1]}.json';spec=json.loads(p.read_text(encoding='utf-8-sig'));start=len(spec['variants']);assert start+len(appsByToken)<=128,(reg,start,len(appsByToken));physical=bool(spec['variants'][0]['poses']['closed']['collision'])
        for entry in appsByToken.values():
            apps=entry['applications'];q=source_quads(source,apps);lo,hi=bounds_for_quads(q);offset=[0.,round(-float(lo[1]),8),0.];parts=[]
            for a in apps:parts.append({'model':export.export_application_model(a),'offset':offset,'yaw':a.get('y',0),'pitch':a.get('x',0)})
            physics=simple_boxes(q,offset,physical);visible=box(lo+offset,hi+offset);closed={'parts':parts,'collision':physics[0],'selection':physics[0] or [copy.deepcopy(visible)],'simpleCollisionByRotation':physics};pose=len(spec['variants']);variant={'poses':{'closed':closed,'open':copy.deepcopy(closed)},'visibleBounds':visible,'sourceVariant':'Исходное соединение '+str(pose+1),'sourcePlacementOffset':offset,'localOrigin':offset,'sourceClosedApplications':apps,'sourceOpenApplications':[],'sourceStateRefs':entry['states'],'v127FullSourceSingleCell':True,'hiddenRetiredSourceCompatibility':True};spec['variants'].append(variant)
            actual=[(v-np.asarray(offset),uv,t,ti) for part in parts for v,uv,t,ti in native.quads(part)];one=signature(q,source);two=signature(actual,native);assert one==two,(reg,pose,sum((one-two).values()),sum((two-one).values()));rotations=[]
            for yaw in range(8):rotations.append({'collision':physics[yaw],'aim':[copy.deepcopy(physics[yaw][0])] if physics[yaw] else [copy.deepcopy(visible)]})
            sparse['objects'].append({'registryId':reg,'variant':pose,'rotations':rotations});checks.append({'registryId':reg,'mainId':resolver.ids[reg],'variant':pose,'occurrences':entry['count'],'sourceApplications':apps,'sourceStates':entry['states'],'status':'PASS_EXACT_VERTEX_UV_RGBA_TINTINDEX','sourceShift':[-v/16 for v in offset]})
        write(p,spec);existing=next((g for g in sparse['groups'] if g['registryId']==reg),None)
        if existing:existing['poses']=len(spec['variants'])
        elif len(spec['variants'])>16:sparse['groups'].append({'registryId':reg,'poses':len(spec['variants']),'bankMax':7,'policy':'Approved hidden retired source connection poses; no Creative restoration'})
    write(R/'bloodborne_dw/imported-full-map-v127.json',sparse);write(O/'hidden-retired-source-connection-proof.json',{'status':'PASS_EXACT_SOURCE_COMPATIBILITY_APPENDS','types':len(groups),'poses':len(checks),'occurrences':sum(r['occurrences'] for r in checks),'checks':checks,'historicAliasManifestModified':False,'originalV12Modified':False,'worldWritten':False});print(token({'status':'PASS','types':len(groups),'poses':len(checks),'cells':sum(r['occurrences'] for r in checks)}))
if __name__=='__main__':main()
