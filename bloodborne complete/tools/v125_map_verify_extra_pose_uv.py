"""Full source union vertices/UV/textureRGBA for approved94one-block poses.

Uses original source-ZIP elements, independent in-memory native UV-lock transform,
and actual appended descriptor/private model dependencies. No asset/world edits.
"""
import collections,copy,json,hashlib
import numpy as np
from pathlib import Path
from v125_map_index import ROOT,MAP,PACK,BASELINE,dump,sha
from v125_map_door_projections import SourceVisuals
from v124_visual_audit import Visuals,rot
from import_remaining_assets import uvlocked_face
from v125_map_resolve import rotation
DEST=ROOT/'docs/v12.5-city-review/citycandidate'

def dump(path,value):path.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n',encoding='utf-8',newline='\n')
RES=ROOT/'src/architecture/resources'

def signature(quads,visuals):
    result=collections.Counter()
    for vertices,uv,texture,tint in quads:
        # Native/model paths may differ while referenced authored PNG pixels
        # remain byte-equivalent. Preserve each UV/vertex association exactly.
        points=tuple(sorted(tuple(round(float(x),6) for x in np.concatenate((p,u))) for p,u in zip(vertices,uv)))
        rgba=visuals.image(texture);result[(points,rgba.shape,hashlib.sha256(rgba.tobytes()).hexdigest(),tint)]+=1
    return result
def source_quads(source,apps):
    result=[]
    for a in apps:
        model=a['model']
        if a.get('uvlock') and (a.get('x',0) or a.get('y',0)):
            virtual='proof:uvlocked/'+model.replace(':','/')+f'_x{a.get("x",0)}_y{a.get("y",0)}'
            if virtual not in source.models:
                doc=copy.deepcopy(source.document(model));doc['elements']=copy.deepcopy(source.elements(model))
                for e in doc['elements']:e['faces']={side:uvlocked_face(f,side,e,a) for side,f in e.get('faces',{}).items()}
                source.models[virtual]=doc
            model=virtual
        result.extend(source.quads({'model':model,'yaw':a.get('y',0),'pitch':a.get('x',0),'offset':a.get('offset',[0,0,0])}))
    return result
def main():
    proposal=json.loads((DEST/'extra-canonical-city-poses.json').read_text());source=SourceVisuals();native=Visuals(vanilla_jar=Path('C:/Users/vakir/.gradle/caches/fabric-loom/1.20.1/minecraft-client.jar'));checks=[];occurrences=[]
    for group in proposal['groups']:
        reg=group['primaryRegistryId'];file=RES/'bloodborne_dw/composite'/f'{reg.split(":",1)[1]}.json';spec=json.loads(file.read_text())
        for p in group['poses']:
            index=p['proposedLogicalPoseIndex'];variant=spec['variants'][index];assert variant['sourceClosedApplications']==p['canonicalSourceApplications']
            target=[q for part in variant['poses']['closed']['parts'] if not part.get('emissive') for q in native.quads(part)];shift=p['sourceShiftCompensationBlocks'];target=[(v+np.array(shift)*16,uv,t,ti) for v,uv,t,ti in target];original=source_quads(source,p['canonicalSourceApplications']);a,b=signature(original,source),signature(target,native)
            checks.append({'registryId':reg,'mainId':group['primaryId'],'logicalPoseIndex':index,'status':'PASS_EXACT_SOURCE_VERTICES_UV_RGBA_TINTINDEX' if a==b else 'FAIL_SOURCE_VERTICES_UV_RGBA_TINTINDEX','sourceQuads':sum(a.values()),'canonicalQuads':sum(b.values()),'missingSurfaceSignatures':sum((a-b).values()),'extraSurfaceSignatures':sum((b-a).values()),'descriptorSha256':sha(file),'sourceApplications':p['canonicalSourceApplications'],'sourceShift':shift})
            for occurrence in p['sourceOccurrences']:
                yaw=occurrence['canonicalRotation'];worldshift=rotation(shift,yaw)
                posed=[(rot(v,1,-yaw*45,[8,8,8])+np.array(worldshift)*16,uv,t,ti) for part in variant['poses']['closed']['parts'] if not part.get('emissive') for v,uv,t,ti in native.quads(part)]
                expected=source_quads(source,occurrence['sourceApplications']);u,v=signature(expected,source),signature(posed,native)
                occurrences.append({'sourcePosition':occurrence['sourcePosition'],'sourceState':occurrence['sourceState'],'registryId':reg,'logicalPoseIndex':index,'rotation':yaw,'status':'PASS_EXACT_SOURCE_WORLDPOSE_VERTICES_UV_RGBA' if u==v else 'FAIL_SOURCE_WORLDPOSE_VERTICES_UV_RGBA','missingSurfaceSignatures':sum((u-v).values()),'extraSurfaceSignatures':sum((v-u).values())})
    failures=[r for r in checks+occurrences if r['status'].startswith('FAIL')]
    dump(DEST/'appended-source-pose-vertex-uv-proof.json',{'schemaVersion':1,'status':'PASS_SOURCE_GEOMETRY_UV_RGBA_PROOF' if not failures else 'FAIL_SOURCE_GEOMETRY_UV_RGBA_PROOF','baselineCommit':BASELINE,'mapSha256':sha(MAP),'packSha256':sha(PACK),'proposalSha256':sha(DEST/'extra-canonical-city-poses.json'),'appliedPoseCount':len(checks),'actualSourceOccurrenceCount':len(occurrences),'surfaceSignaturesChecked':sum(r['sourceQuads'] for r in checks),'failures':failures,'poseChecks':checks,'sourceOccurrenceChecks':occurrences,'sourceUvLockMethod':'Source-originalelements/facesUVlockcopiedonlyinmemoryviaexistingbytecode-verifiedcardinalUVlocktransform; actualappendedprivateUVwrappermodelsreadindependently. Rawv15andallproductionresourcesreadonly.','notNativeAcceptance':'No game/modelbaker/clientframe/lighting/occlusion/worldbiometint/effect test. Everyquadvertex/UVassociation+fullRGBAtexturecontent+tintindex matched offline; genuine native city QA remains required.'})

if __name__=='__main__':
    try:main()
    except BaseException:
        import traceback
        (DEST/'appended-pose-proof-error.txt').write_text(traceback.format_exc(),encoding='utf-8');raise
