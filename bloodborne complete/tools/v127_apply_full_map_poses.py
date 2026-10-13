"""Append exact authored conditional poses required by the entire source map.

Only NEW source_ descriptors/alias bank counts are written. Existing pose prefixes,
IDs, source model/PNG files and all original V12 bytes are invariant. This tool
does not write or convert any world. Its proof uses complete vertex/UV/RGBA faces.
"""
from __future__ import annotations
import argparse,collections,copy,hashlib,json,math,os,time,uuid
import numpy as np
from pathlib import Path
from v125_map_index import ROOT,MAP,PACK,sha,dump
from v125_map_door_projections import SourceVisuals
from v125_map_verify_extra_pose_uv import source_quads,signature
from v124_visual_audit import Visuals,rot
from import_remaining_assets import Source
from v125_map_resolve import rotation

RES=ROOT/'src/architecture/resources';OUT=ROOT/'docs/v12.7/conversion'
VANILLA=Path.home()/'.gradle/caches/fabric-loom/1.20.1/minecraft-client.jar'
def read(p):return json.loads(p.read_text(encoding='utf-8-sig'))
def write(p,d):
    data=(json.dumps(d,ensure_ascii=False,indent=2)+'\n').encode('utf8');temp=p.with_name(p.name+'.v127-'+uuid.uuid4().hex+'.tmp')
    temp.write_bytes(data)
    for attempt in range(10):
        try:os.replace(temp,p);break
        except OSError:
            if attempt==9:raise
            time.sleep(.1)
def box(lo,hi):return {'from':[round(float(x),8) for x in lo],'to':[round(float(x),8) for x in hi]}
def bounds_for_quads(quads):
    vertices=np.asarray([q[0] for q in quads]).reshape(-1,3);return vertices.min(0),vertices.max(0)
def simple_boxes(quads,offset,physical):
    if not physical:return [[] for _ in range(8)]
    # One conservative useful cuboid lies INSIDE a source model cuboid rather
    # than filling the union hull of a multi-arm wall. It may admit penetration.
    result=[]
    for yaw in range(8):
        best=None
        # Four opposing source faces with equal vertices are grouped by their
        # source axis-aligned element bounds. Use actual finite authored boxes,
        # never the enclosing multi-part model AABB (empty corners stay free).
        candidates=collections.defaultdict(list)
        for vertices,uv,texture,tint in quads:
            lo,hi=np.asarray(vertices).min(0),np.asarray(vertices).max(0)
            candidates[texture].append((lo,hi))
        # Source walls/fences generally have an axis-aligned central post. A
        # small interior support of the union is deliberately conservative.
        allv=np.asarray([q[0] for q in quads]).reshape(-1,3)
        low,high=allv.min(0),allv.max(0);width=np.maximum(high-low,0)
        center=(low+high)/2
        # Central physical stem: at most half a cell, within the visual bounds.
        # It uses one volume even when the visible walls branch into several arms.
        size=np.minimum(width,[8,16,8]);size[1]=width[1]
        if np.any(size<1e-7):result.append([]);continue
        # Keep this conservative base-box only if its center lies inside at
        # least one closed source element. Otherwise use the largest closed box.
        # Models use six cardinal quads per source cuboid; recover their original
        # convex vertices from consecutive six faces in the imported source.
        cuboids=[]
        for n in range(0,len(quads)-5):
            verts=np.asarray([q[0] for q in quads[n:n+6]]).reshape(-1,3)
            uniq=np.unique(np.round(verts,7),axis=0)
            if len(uniq)!=8:continue
            l,h=uniq.min(0),uniq.max(0)
            if np.prod(h-l)<1e-6:continue
            corners=np.asarray([[x,y,z] for x in (l[0],h[0]) for y in (l[1],h[1]) for z in (l[2],h[2])])
            if set(map(tuple,np.round(corners,7)))==set(map(tuple,uniq)):cuboids.append((l,h))
        if not cuboids:result.append([]);continue
        lo,hi=max(cuboids,key=lambda v:float(np.prod(v[1]-v[0])));center=(lo+hi)/2;width=hi-lo
        angle=math.radians(yaw*45);c,s=math.cos(angle),math.sin(angle)
        x,z=center[0]-8,center[2]-8;turned=np.array([8+c*x-s*z,center[1],8+s*x+c*z])
        if yaw%2:
            side=min(width[0],width[2])/math.sqrt(2);size=np.array([side,width[1],side])
        else:size=np.array([width[2] if yaw%4 else width[0],width[1],width[0] if yaw%4 else width[2]])
        turned+=np.asarray(offset);result.append([box(turned-size/2,turned+size/2)])
    return result
def main():
    p=argparse.ArgumentParser();p.add_argument('--apply',action='store_true');args=p.parse_args()
    proposals=read(OUT/'source-pose-proposals.json');source=SourceVisuals();native=Visuals(vanilla_jar=VANILLA);export=Source(PACK,VANILLA)
    catalogfile=RES/'bloodborne_dw/remaining-catalogue.json';aliasfile=RES/'bloodborne_dw/imported-type-aliases.json';catalog=read(catalogfile);aliases=read(aliasfile)
    objects={r['path']:r for r in catalog['objects']};groups={r['primaryRegistryId']:r for r in aliases['groups']};evidence=[];checks=[];newresources=[];sourceDefects=[]
    for group in proposals['groups']:
        reg=group['registryId'];path=reg.split(':')[1];file=RES/'bloodborne_dw/composite'/f'{path}.json';spec=read(file);before=copy.deepcopy(spec);oldcount=group['currentPoseCount']
        assert reg.startswith('bloodborne_dw:source_') and spec['simplePhysicalCollision']
        assert len(spec['variants'])>=oldcount,('Descriptor shrank',reg,len(spec['variants']),oldcount)
        physical=bool(spec['variants'][0]['poses']['closed']['collision'])
        applied=0
        for pose in group['poses']:
            index=oldcount+applied;apps=pose['sourceApplications']
            # A missing authored parent is a source defect, not permission to
            # fabricate the intended mesh. Its affected coordinates are removed
            # explicitly in the full plan; other conditional source poses proceed.
            try:
                models=[export.export_application_model(a) for a in apps] if args.apply else [v['model'] for v in pose['parts']]
            except FileNotFoundError as failure:
                sourceDefects.append({'registryId':reg,'sourceApplications':apps,'sourceStates':pose['sourceStates'],'sourceCellOccurrences':pose['count'],'status':'REMOVE_MISSING_AUTHORED_SOURCE_DEPENDENCY','reason':str(failure)})
                continue
            quads=source_quads(source,apps);lo,hi=bounds_for_quads(quads)
            offset=[0.,round(-float(lo[1]),8),0.];shift=[-float(x)/16 for x in offset];parts=[]
            for a in apps:
                model=models[len(parts)]
                parts.append({'model':model,'offset':offset,'yaw':a.get('y',0),'pitch':a.get('x',0)})
            visible=box(lo+offset,hi+offset);simple=simple_boxes(quads,offset,physical)
            closed={'parts':parts,'collision':simple[0],'selection':simple[0] or [copy.deepcopy(visible)],'simpleCollisionByRotation':simple}
            variant={'poses':{'closed':closed,'open':copy.deepcopy(closed)},'visibleBounds':visible,'sourceVariant':f'Исходное соединение {index+1}',
                'sourcePlacementOffset':offset,'localOrigin':offset,'sourceClosedApplications':apps,'sourceOpenApplications':[],
                'sourceStateRefs':[r['carrier']+'['+','.join(k+'='+v for k,v in sorted(r['properties'].items()))+']' for r in pose['sourceStates']],
                'v127FullSourceSingleCell':True}
            if index<len(spec['variants']):assert spec['variants'][index]==variant,('Applied source pose differs on resume',reg,index)
            else:spec['variants'].append(variant)
            if index>=len(objects[path]['variants']):objects[path]['variants'].append({'variant':index,'name':objects[path]['displayName']+f' · соединение {index+1}','sourceModels':[a['model'] for a in apps]})
            applied+=1
            if args.apply:
                native.cache.clear() if hasattr(native,'cache') else None
                actual=[q for part in parts for q in native.quads(part)];actual=[(v+np.asarray(shift)*16,uv,t,ti) for v,uv,t,ti in actual]
                expected=signature(quads,source);got=signature(actual,native)
                check={'registryId':reg,'mainId':group['mainId'],'logicalPose':index,'sourceCellOccurrences':pose['count'],'sourceApplications':apps,'sourceShift':shift,'sourceQuads':sum(expected.values()),'missingFaces':sum((expected-got).values()),'extraFaces':sum((got-expected).values()),'status':'PASS' if expected==got else 'FAIL'}
                for state in pose['sourceStates']:
                    yaw=state['yaw'];shiftworld=rotation(shift,yaw)
                    # Exact source reconstruction from the canonical app list.
                    rotated=[{**a,'y':(a.get('y',0)+yaw*45)%360} for a in apps]
                    original=signature(source_quads(source,rotated),source)
                    posed=[(rot(v,1,-yaw*45,[8,8,8])+np.asarray(shiftworld)*16,uv,t,ti) for part in parts for v,uv,t,ti in native.quads(part)]
                    match=signature(posed,native);assert original==match,('World source UV pose mismatch',reg,index,yaw)
                checks.append(check);assert check['status']=='PASS',check
        assert spec['variants'][:oldcount]==before['variants'][:oldcount]
        assert {k:v for k,v in spec.items() if k!='variants'}=={k:v for k,v in before.items() if k!='variants'}
        if reg not in groups and len(spec['variants'])>16:
            row={'primaryRegistryId':reg,'primaryId':group['mainId'],'members':[reg],'kind':'artwork','oneItem':True,'poseCount':len(spec['variants']),'reason':'V12.7 exact entire source-map one-cell conditional unions; original pose prefix preserved'};groups[reg]=row;aliases['groups'].append(row)
        if reg in groups:groups[reg]['poseCount']=len(spec['variants'])
        if args.apply:write(file,spec)
        evidence.append({'registryId':reg,'mainId':group['mainId'],'oldPosePrefixExact':True,'beforeSha256':sha(file) if not args.apply else hashlib.sha256(json.dumps(before,ensure_ascii=False,indent=2).encode()).hexdigest(),'beforePoses':oldcount,'afterPoses':len(spec['variants']),'appendedPoses':applied})
    if args.apply:write(catalogfile,catalog);write(aliasfile,aliases)
    dump(OUT/'source-dependency-defects.json',{'status':'CONFIRMED_SOURCE_AUTHOR_DEFECTS_NOT_GUESSED','rows':sourceDefects})
    dump(OUT/'appended-poses-proof.json',{'status':'PASS_EXACT_SOURCE_FACE_UV_RGBA_AND_OLD_POSE_PREFIX' if args.apply else 'PROPOSAL_NOT_APPLIED','groups':evidence,'poseChecks':checks,'appendedPoses':sum(e['appendedPoses'] for e in evidence),'sourceDefects':sourceDefects,'newRegistryIds':0,'originalV12Edits':0,'sourceMapConverted':False,'sourceArchivesUnchanged':{'map':sha(MAP),'pack':sha(PACK)},'authorCollisions':'NOT_SUPPLIED_NOT_APPLIED','nativeVerification':'NOT_RUN_BY_OFFLINE_PROOF'})
    print(json.dumps({'status':'PASS' if args.apply else 'PROPOSAL','groups':len(evidence),'poses':sum(e['appendedPoses'] for e in evidence)}))
if __name__=='__main__':main()
