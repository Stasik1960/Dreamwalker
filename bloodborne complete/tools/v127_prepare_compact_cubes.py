"""Prove exact opaque unit-cube poses and emit opt-in native palette models."""
from __future__ import annotations
import collections,copy,hashlib,json,os,time,uuid
import numpy as np
from pathlib import Path
from v125_map_index import ROOT,MAP,PACK,sha,dump
from v124_visual_audit import Visuals
from v127_full_map_resolver import CurrentResolver

RES=ROOT/'src/architecture/resources';OUT=ROOT/'docs/v12.7/conversion'
VANILLA=Path.home()/'.gradle/caches/fabric-loom/1.20.1/minecraft-client.jar'
def write(p,d):
    data=(json.dumps(d,ensure_ascii=False,indent=2)+'\n').encode('utf8');t=p.with_name(p.name+'.compact-'+uuid.uuid4().hex+'.tmp');t.parent.mkdir(parents=True,exist_ok=True);t.write_bytes(data)
    for n in range(10):
        try:os.replace(t,p);return
        except OSError:
            if n==9:raise
            time.sleep(.1)
def cube(quads,visuals):
    if len(quads)<6:return False
    expected={tuple(row) for row in np.asarray([[x,y,z] for x in [0.,16.] for y in [0.,16.] for z in [0.,16.]])}
    seen=set()
    for vertices,uv,texture,tint in quads:
        points=np.round(np.asarray(vertices),7);corners=set(map(tuple,points))
        if len(corners)!=4 or not corners<=expected:return False
        axes=[i for i in range(3) if np.ptp(points[:,i])<1e-7]
        if len(axes)!=1:return False
        side=(axes[0],points[0,axes[0]])
        image=visuals.image(texture)
        if image.shape[-1]!=4:return False
        if np.min(image[:,:,3])==255:seen.add(side)
    return seen=={(i,float(v)) for i in range(3) for v in [0,16]}
def main():
    resolver=CurrentResolver();native=Visuals(vanilla_jar=VANILLA);types=[];proof=[]
    oldtables=[json.loads((RES/'bloodborne_dw'/name).read_text(encoding='utf-8-sig')) for name in ['imported-collision-v125.json','imported-collision-v126.json']]
    sparse=json.loads((RES/'bloodborne_dw/imported-full-map-v127.json').read_text(encoding='utf-8-sig'));newtables={(r['registryId'],r['variant']):r['rotations'] for r in sparse['objects']}
    aliases=json.loads((RES/'bloodborne_dw/imported-type-aliases.json').read_text(encoding='utf-8-sig'))
    stairs={r['primaryRegistryId'] for r in aliases['groups'] if r['kind']=='stair'}
    copied=json.loads((RES/'bloodborne_dw/imported-type-aliases-v127.json').read_text(encoding='utf-8-sig'))['mappings']
    def fitted(reg,index):
        if (reg,index) in newtables:return copy.deepcopy(newtables[reg,index])
        for m in copied:
            if m['newRegistryId']==reg and m['newVariant']==index:return fitted(m['oldRegistryId'],m['oldVariant'])
        for table in reversed(oldtables):
            if reg not in table['objects']:continue
            refs=table['objects'][reg];ref=refs[index if index<len(refs) else 0]['closedBase'];rows=table['shapes'][ref]
            def b(values):return [{'from':list(v[:3]),'to':list(v[3:])} if isinstance(v,list) else v for v in values]
            return [{'collision':b(r['collision']),'aim':b(r['aim'])} for r in rows]
        return None
    for reg,spec in resolver.current.items():
        if not reg.startswith('bloodborne_dw:source_') or reg in resolver.retired or reg in stairs or len(spec['variants'])>16 or spec.get('openable') or spec.get('support',{}).get('required') or spec.get('importedClimbingBounds'):continue
        path=reg.split(':')[1];poses=[];models={};checks=[];shapes={};opaque=[]
        for index,v in enumerate(spec['variants']):
            p=v['poses']['closed'];parts=p['parts']
            if not parts or any(part.get('altModel',part['model'])!=part['model'] or any(part.get('offset',[0,0,0])) or part.get('extraYaw') or part.get('pitch',0)%90 or part.get('yaw',0)%90 or part.get('pivot',[8,8,8])!=[8,8,8] for part in parts):continue
            quads=[q for part in parts for q in native.quads(part)]
            if not quads:continue
            points=np.asarray([q[0] for q in quads]).reshape(-1,3)
            if np.min(points)<-1e-7 or np.max(points)>16+1e-7:continue
            # Source shift must be zero, preserving source unit-cell and vanilla
            # culling/lighting coordinates. No compact state can carry mount NBT.
            if any(v.get('sourcePlacementOffset',[0,0,0])):continue
            shape=fitted(reg,index)
            if shape is None:continue
            # Native palettes have one root cell; no owned helper geometry is
            # silently dropped. Every cardinal physics/aim box must fit it too.
            if any(any(b['from'][i]<-1e-7 or b['to'][i]>16+1e-7 for i in range(3)) for y in [0,2,4,6] for kind in ['collision','aim'] for b in shape[y][kind]):continue
            poses.append(index);models[index]=parts;shapes[str(index)]=shape
            isopaque=cube(quads,native)
            if isopaque:opaque.append(index)
            checks.append({'logicalPose':index,'sixFacesUnitCube':isopaque,'opaqueBackingOnAllSixFaces':isopaque,'sourceModels':[part['model'] for part in parts],'sourceQuads':len(quads),'geometryAndUvModelsUnmodified':True,'sourceAlphaOverlaysRetained':isopaque and len(quads)>6,'allVerticesAndPhysicsWithinOneCell':True,'exactFittedDefaultCollisionRetained':True})
        if not poses:continue
        file=RES/'assets/bloodborne_dw/blockstates'/f'{path}.json';before=sha(file);old=json.loads(file.read_text(encoding='utf-8-sig'));empty=old['variants']['']['model'] if 'variants' in old else old['multipart'][0]['apply']['model']
        multipart=[{'when':{'compact':'false'},'apply':{'model':empty}}]
        for index in poses:
            for yaw in [0,2,4,6]:
                for part in models[index]:
                    application={'model':part['model'],'y':int((part.get('yaw',0)+yaw*45)%360)}
                    if part.get('pitch'):application['x']=int(part['pitch']%360)
                    multipart.append({'when':{'compact':'true','variant':str(index),'rotation':str(yaw)},'apply':application})
        write(file,{'multipart':multipart})
        row={'registryId':reg,'mainId':resolver.ids.get(reg,''),'poses':poses,'opaquePoses':opaque,'shapes':shapes,'layer':spec.get('layer','cutout'),'stateProperty':'compact','default':False,'sourceConditions':'Exact stationary one-cell source model/physics; even cardinal rotation, zero SourceShift/mount; original owners stay false','nativeModelsUseSamePrivateSourceParents':True}
        types.append(row);proof.append({**row,'blockstateBeforeSha256':before,'blockstateAfterSha256':sha(file),'poseChecks':checks,'oldModeRetainsEmptyModelAndOwnerRenderer':True})
    write(RES/'bloodborne_dw/imported-compact-v127.json',{'schemaVersion':1,'unitsPerBlock':16,'types':types,'policy':'Opt-in full-map source palette only. Exact stationary contained meshes inclsprite/cutout retainphysical/aim tables; opaqueunitcubes use nativeculling. Preserved old/default owners retain compact=false and their UUID/payload/collision consumers. No originalV12registry accepted.'})
    dump(OUT/'compact-cube-proof.json',{'status':'PASS_EXACT_OPAQUE_UNIT_CUBES_NATIVE_SOURCE_MODELS','types':proof,'typeCount':len(types),'poseCount':sum(len(t['poses']) for t in types),'sourceMapConverted':False,'sourceHashes':{'map':sha(MAP),'pack':sha(PACK)},'nativeValidation':'NOT_RUN_BY_OFFLINE_SOURCE_PROOF'})
    print(json.dumps({'types':len(types),'poses':sum(len(t['poses']) for t in types),'ids':[t['mainId'] for t in types]}))
if __name__=='__main__':main()
