"""Stage the source-proven rear-mounted cap state of the same complete door90923.

The twelve actual city lower/upper pairs share canonical relative[0,2,-1].
Only derived descriptor/model/proposal files are written. Production and prior
door59 resources/proofs are preserved; root applies after inspection.
"""
from __future__ import annotations
import collections, copy, hashlib, json
import numpy as np
from v125_map_index import ROOT, MAP, PACK, dump, sha, token
from v125_map_resolve import rotation
from v125_map_door_projections import SourceVisuals
from v125_map_verify_extra_pose_uv import source_quads, signature
from v124_visual_audit import Visuals, rot
import build_v126_door_models as doors
from v126_city_aggregation import pose, shift, yaw, vertical, RES, SRC, VANILLA

DEST=ROOT/'build/v126-city-mounteddoor'
DOCS=ROOT/'docs/v12.6/city'
REGISTRY='bloodborne_dw:source_smooth_quartz_top'
LOWER='bloodborne_dw:source_crimson_top'


def main():
    DEST.mkdir(parents=True,exist_ok=True);DOCS.mkdir(parents=True,exist_ok=True)
    data=json.loads(SRC.read_text());bypos={tuple(r['sourcePosition']):r for r in data['owners']};pairs=[]
    for lower in data['owners']:
        if lower['registryId']!=LOWER or pose(lower)!=0:continue
        worlddelta=rotation([0,2,-1],yaw(lower));xyz=tuple(lower['sourcePosition'][i]+round(worlddelta[i]) for i in range(3))
        upper=bypos.get(xyz)
        if upper is None or upper['registryId']!=REGISTRY or pose(upper)!=0 or yaw(upper)!=yaw(lower):continue
        actual=[upper['sourcePosition'][i]-lower['sourcePosition'][i] for i in range(3)]
        assert all(abs(a-b)<1e-8 for a,b in zip(rotation(actual,-yaw(lower)),[0,2,-1]))
        assert shift(lower)==shift(upper)==[0.,-1.,0.] and vertical(lower)==vertical(upper)==0.
        pairs.append((lower,upper))
    assert len(pairs)==12 and collections.Counter(yaw(a) for a,b in pairs)=={0:3,2:3,4:3,6:3}
    sourcefile=RES/'bloodborne_dw/composite/source_smooth_quartz_top.json';oldspec=json.loads(sourcefile.read_text())
    assert len(oldspec['variants']) in {2,3};spec=copy.deepcopy(oldspec);variant=copy.deepcopy(spec['variants'][1])
    closed=copy.deepcopy(variant['poses']['closed']['parts']);assert len(closed)==2
    closed[1]['offset']=[closed[1].get('offset',[0,0,0])[i]+[0,0,-16][i] for i in range(3)]
    variant['poses']['closed']['parts']=closed
    variant['displayName']='Деревянная дверь с высокой каменной аркой · верх отнесён назад'
    variant['sourceAssemblyV126Montage']='CITY_90458_90923_CANONICAL_RELATIVE_0_2_MINUS1'
    variant['sourcePlacementOffset']=[0,16,0]
    lower,upper=pairs[0];applications=[]
    for component,relative in [(lower,[0,0,0]),(upper,[0,2,-1])]:
        for app in component['sourceApplications']:
            app=copy.deepcopy(app);app.pop('weight',None)
            y=(app.get('y',0)-yaw(lower)*45)%360
            if y:app['y']=y
            else:app.pop('y',None)
            if any(relative):app['offset']=[v*16 for v in relative]
            applications.append(app)
    variant['sourceClosedApplications']=applications;variant['sourceOpenApplications']=[]
    if len(spec['variants'])==2:spec['variants'].append(variant)
    else:spec['variants'][2]=variant
    staged=DEST/'source_smooth_quartz_top.json';dump(staged,spec)
    selected={'mainId':'90923','registryId':REGISTRY,'resourceSuffix':'90923_mount_back',
        'sourceDescriptorFile':str(staged),'sourceDescriptorSha256':sha(staged),'sourceVariant':2,'spec':spec,'parts':closed}
    visual=Visuals(vanilla_jar=VANILLA);semanticsources=doors.load_sources()
    woodrows,gaterows,gatemetadata=doors.semantic_configuration(semanticsources,visual)
    originaldoorproof=ROOT/'docs/v12.6/doors/door-model-staging-proof.json';oldproofsha=sha(originaldoorproof)
    oldmanifest=ROOT/'build/v126-door-plan/door-manifest.json';oldmanifestsha=sha(oldmanifest)
    oldmodelhashes={m['file']:sha(__import__('pathlib').Path(m['file'])) for d in json.loads(oldmanifest.read_text())['doors'] for m in d['models']}
    doors.DEST=DEST;doors.DOCS=DOCS/'mounted-door'
    row=doors.generate(selected,visual,woodrows,gaterows)
    variant['poses']['open']={'parts':row['openParts'],'collision':[],'selection':[]}
    spec['variants'][2]=variant;dump(staged,spec)
    row['sourceDescriptorSha256']=sha(staged)
    source=SourceVisuals();checks=[]
    for lower,upper in pairs:
        expected=[]
        for component in [lower,upper]:
            delta=np.asarray([component['sourcePosition'][i]-lower['sourcePosition'][i] for i in range(3)])*16
            expected.extend((v+delta,uv,t,ti) for v,uv,t,ti in source_quads(source,component['sourceApplications']))
        worldshift=rotation(shift(lower),yaw(lower));actual=[]
        for part in closed:
            actual.extend((rot(v,1,-yaw(lower)*45,[8,8,8])+np.asarray(worldshift)*16,uv,t,ti) for v,uv,t,ti in visual.quads(part))
        a,b=signature(expected,source),signature(actual,visual)
        checks.append({'lowerSourcePosition':lower['sourcePosition'],'upperSourcePosition':upper['sourcePosition'],
            'retainedAnchorUuid':lower['instanceUuid'],'consumedUpperUuid':upper['instanceUuid'],'rotation':yaw(lower),
            'canonicalRelativePosition':rotation([upper['sourcePosition'][i]-lower['sourcePosition'][i] for i in range(3)],-yaw(lower)),
            'sourceQuads':sum(a.values()),'completeQuads':sum(b.values()),
            'missingSourceSignatures':sum((a-b).values()),'extraCompleteSignatures':sum((b-a).values()),
            'status':'PASS_EXACT_SOURCE_VERTICES_UV_RGBA_TINT' if a==b else 'FAIL_SOURCE_CLOSED_EQUIVALENCE'})
    assert all(r['status'].startswith('PASS') for r in checks)
    recipe={'key':'CITY-90458-90923-DEPTH-MINUS1','primaryId':'90923','primaryRegistryId':REGISTRY,'installationPose':2,
        'displayName':'Деревянная дверь с высокой каменной аркой · верх отнесён назад',
        'components':[{'mainId':'90458','registryId':LOWER,'pose':0,'relativePosition':[0,0,0],
                       'sourceShift':[0.,-1.,0.],'componentYawDelta':0},
                      {'mainId':'90923','registryId':REGISTRY,'pose':0,'relativePosition':[0,2,-1],
                       'sourceShift':[0.,-1.,0.],'componentYawDelta':0}],
        'closedSourceVertexUvRgbaProof':'PASS_ALL12_ACTUAL_SOURCE_PAIRS_FOUR_CARDINAL_YAWS',
        'oldVariantIndicesPreserved':True,'sameFunctionalType':'90923 existing primary, defaultinstallationPose1; mounteddepth ispose2, no new numeric ID'}
    manifest={'schemaVersion':1,'status':'PASS_STAGED_SOURCE_MOUNT_DEPTH_STATE_NATIVE_NOT_RUN','scope':'NEW_POST_ORIGINAL_V12_ONLY',
        'doors':[row],'modelResources':len(row['models']),'leafCount':row['leafCount'],
        'closedSourceQuads':row['closedSourceQuads'],'partitionedClosedQuads':row['partitionedClosedQuads'],
        'productionMutation':False,'original90001Modified':False,'nativeValidation':'NOT_RUN','authorCollisionExport':'NOT_PROVIDED_NOT_APPLIED'}
    dump(DEST/'door-manifest.json',manifest)
    assert sha(originaldoorproof)==oldproofsha and sha(oldmanifest)==oldmanifestsha
    assert all(sha(__import__('pathlib').Path(path))==digest for path,digest in oldmodelhashes.items())
    proposal={'schemaVersion':1,'status':'PASS_SOURCE_DERIVED_MOUNT_DEPTH_STATE_READY_FOR_ROOT_APPLICATION',
        'primaryId':'90923','registryId':REGISTRY,'defaultInstallationPose':1,'additionalInstallationPose':2,
        'canonicalCapRelativePosition':[0,2,-1],'existingSourcePairCount':len(pairs),
        'actualPairsByRotation':dict(collections.Counter(r['rotation'] for r in checks)),
        'sourceRecipe':recipe,'stagedDescriptorFile':str(staged),'stagedDescriptorSha256':sha(staged),
        'stagedDoorManifestFile':str(DEST/'door-manifest.json'),'stagedDoorManifestSha256':sha(DEST/'door-manifest.json'),
        'actualSourceChecks':checks,'openModelProof':row,'oldDoor59ResourcesAndProofUnchanged':True,
        'sourceCityDataSha256':sha(SRC),'mapSha256':sha(MAP),'packSha256':sha(PACK),
        'productionApplication':'PENDING_ROOT','nativeValidation':'NOT_RUN'}
    dump(DOCS/'mount-depth-state-proposal.json',proposal)
    print(json.dumps({k:proposal[k] for k in ['status','existingSourcePairCount','actualPairsByRotation','additionalInstallationPose','stagedDescriptorSha256','oldDoor59ResourcesAndProofUnchanged']}))

if __name__=='__main__':main()
