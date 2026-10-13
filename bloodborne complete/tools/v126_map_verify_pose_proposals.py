"""Readonly vertex/UV/RGBA proof of every proposed city conditional pose."""
from __future__ import annotations
import argparse, json
from pathlib import Path
import numpy as np
from v125_map_index import ROOT, MAP, PACK, dump, sha
from v125_map_door_projections import SourceVisuals
from v124_visual_audit import Visuals, rot
from v125_map_verify_extra_pose_uv import signature, source_quads
from v125_map_resolve import rotation

DEST=ROOT/'docs/v12.6-city-review/map/citycandidate'


def main():
    p=argparse.ArgumentParser();p.add_argument('--candidate',type=int,default=4);args=p.parse_args()
    file=DEST/f'extra-canonical-city-poses-{args.candidate:02d}.json'
    proposal=json.loads(file.read_text());source=SourceVisuals()
    native=Visuals(vanilla_jar=Path('C:/Users/vakir/.gradle/caches/fabric-loom/1.20.1/minecraft-client.jar'))
    checks=[];occurrences=[]
    for group in proposal['groups']:
        for pose in group['poses']:
            parts=pose['proposedParts'];shift=pose['sourceShiftCompensationBlocks']
            target=[(v+np.asarray(shift)*16,uv,t,ti) for part in parts for v,uv,t,ti in native.quads(part)]
            expected=source_quads(source,pose['canonicalSourceApplications'])
            a,b=signature(expected,source),signature(target,native)
            checks.append({'registryId':group['primaryRegistryId'],'mainId':group['primaryId'],
                'proposedLogicalPoseIndex':pose['proposedLogicalPoseIndex'],
                'status':'PASS_EXACT_SOURCE_VERTICES_UV_RGBA_TINTINDEX' if a==b else 'FAIL_SOURCE_VERTICES_UV_RGBA_TINTINDEX',
                'sourceQuads':sum(a.values()),'canonicalQuads':sum(b.values()),
                'missingSurfaceSignatures':sum((a-b).values()),'extraSurfaceSignatures':sum((b-a).values()),
                'sourceApplications':pose['canonicalSourceApplications'],'sourceShift':shift})
            for occurrence in pose['sourceOccurrences']:
                yaw=occurrence['canonicalRotation'];worldshift=rotation(shift,yaw)
                posed=[(rot(v,1,-yaw*45,[8,8,8])+np.asarray(worldshift)*16,uv,t,ti)
                    for part in parts for v,uv,t,ti in native.quads(part)]
                actual=source_quads(source,occurrence['sourceApplications'])
                c,d=signature(actual,source),signature(posed,native)
                occurrences.append({'sourcePosition':occurrence['sourcePosition'],'sourceState':occurrence['sourceState'],
                    'registryId':group['primaryRegistryId'],'proposedLogicalPoseIndex':pose['proposedLogicalPoseIndex'],
                    'rotation':yaw,'status':'PASS_EXACT_SOURCE_WORLDPOSE_VERTICES_UV_RGBA' if c==d else 'FAIL_SOURCE_WORLDPOSE_VERTICES_UV_RGBA',
                    'missingSurfaceSignatures':sum((c-d).values()),'extraSurfaceSignatures':sum((d-c).values())})
    failures=[r for r in checks+occurrences if r['status'].startswith('FAIL')]
    report={'schemaVersion':1,'status':'PASS_SOURCE_GEOMETRY_UV_RGBA_PROOF' if not failures else 'FAIL_SOURCE_GEOMETRY_UV_RGBA_PROOF',
        'proposalSha256':sha(file),'mapSha256':sha(MAP),'packSha256':sha(PACK),
        'proposedPoseCount':len(checks),'actualSourceOccurrenceCount':len(occurrences),
        'surfaceSignaturesChecked':sum(r['sourceQuads'] for r in checks),'failures':failures,
        'poseChecks':checks,'sourceOccurrenceChecks':occurrences,
        'sourceUvLockMethod':'Actual original source faces transformed in memory using established uvlocked_face routine; independently read existing private UV-wrapper model files. All vertices/UV association/full PNG RGBA/tint indices compare exactly.',
        'nativeValidation':'NOT_RUN_BY_OFFLINE_PROOF','productionPoseApplication':'PENDING_ROOT_APPEND_AND_FINAL_DESCRIPTOR_PROOF'}
    dump(DEST/'proposed-source-pose-vertex-uv-proof.json',report)
    print(json.dumps({k:report[k] for k in ['status','proposedPoseCount','actualSourceOccurrenceCount','surfaceSignaturesChecked']}))
    assert not failures, ('Proposed source geometry mismatch',len(failures))

if __name__=='__main__':main()
