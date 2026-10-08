"""Static source camera candidates, not actual ray/player/runtime proof.

Uses existing frozen default-placement data and unchanged source resources.
Native obstruction is limited explicitly to the author pad and added stone.
"""
from __future__ import annotations
import argparse
import json
import math
from pathlib import Path
from audit_rp_motion_envelopes import static_cubes, motions, cube_envelope, bounds, union
from audit_v10_rp_fixture_supports import world_box, digest, CUSTOM

ROOT = Path(__file__).resolve().parents[1]


def overlap(a,b):
    return all(min(a[i+3],b[i+3])-max(a[i],b[i])>1e-5 for i in range(3))


def center(box):
    return tuple((box[i]+box[i+3])/2 for i in range(3))


def inside(point,box):
    return all(box[i]<=point[i]<box[i+3] for i in range(3))


def exposed(box,stone):
    mid=center(box);points=[mid]
    for axis in range(3):
        for fraction in (.1,.9):
            point=list(mid);point[axis]=box[axis]+(box[axis+3]-box[axis])*fraction;points.append(tuple(point))
    return [point for point in points if not inside(point,stone)]


def ray_time(start,end,box):
    """Segment slab intersection; endpoint ties are not earlier obstruction."""
    low,high=0.,1.
    for axis in range(3):
        delta=end[axis]-start[axis]
        if abs(delta)<1e-12:
            if start[axis]<box[axis] or start[axis]>box[axis+3]:return None
            continue
        first,last=(box[axis]-start[axis])/delta,(box[axis+3]-start[axis])/delta
        if first>last:first,last=last,first
        low,high=max(low,first),min(high,last)
        if low>high:return None
    return low if high>=0 and low<=1 else None


def directions(distance):
    return ((0,0,-distance),(0,0,distance),(-distance,0,0),(distance,0,0),(0,distance,0))


def first_candidate(selection,physical,native,stone,distances,check_body):
    counts={'candidates':0,'sourcePhysicalRejected':0,'nativeBodyRejected':0,'nativeRayRejected':0}
    for box in selection:
        for point in exposed(box,stone):
            for distance in distances:
                for direction in directions(distance):
                    counts['candidates']+=1
                    eye=tuple(point[i]+direction[i] for i in range(3));feet=(eye[0],eye[1]-1.62,eye[2])
                    actor=(feet[0]-.3,feet[1],feet[2]-.3,feet[0]+.3,feet[1]+1.8,feet[2]+.3)
                    source_overlap=any(overlap(actor,b) for b in physical);native_overlap=any(overlap(actor,b) for b in native)
                    if check_body and source_overlap:counts['sourcePhysicalRejected']+=1;continue
                    if check_body and native_overlap:counts['nativeBodyRejected']+=1;continue
                    if any((t:=ray_time(eye,point,b)) is not None and t*t*distance*distance<distance*distance-1e-6 for b in native):counts['nativeRayRejected']+=1;continue
                    return {'eye':eye,'feet':feet,'standingActorBox':actor,'sourcePoint':point,'distance':distance,
                            'physicalBodyOverlap':source_overlap,'nativeBodyOverlap':native_overlap,
                            'withinOrdinaryReach5':distance<=5},counts
    return None,counts


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--fixture-report',type=Path,required=True);p.add_argument('--jar-sha256',required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args()
    if a.output.exists():raise FileExistsError('Refuse to overwrite evidence')
    fixtures=json.loads(a.fixture_report.read_text('utf8'));resources=ROOT/'src/rp/resources/assets/bloodborne_rp';catalogue=json.loads((resources/'catalog.json').read_text('utf8'))
    motion_path=Path(fixtures['motionAudit'])
    if not motion_path.is_absolute():motion_path=ROOT/motion_path
    if digest(motion_path)!=fixtures['motionAuditSha256']:raise ValueError('Referenced source-motion evidence changed')
    source_motion=json.loads(motion_path.read_text('utf8'));source_rows={row['asset']:row for row in source_motion['assets']}
    if digest(resources/'catalog.json')!=source_motion['catalogueSha256']:raise ValueError('Source resource catalogue changed')
    floor=[(x,99,z,x+1,100,z+1) for x in range(-66,-61) for z in range(62,67)]
    rows=[]
    for fixture in fixtures['assets']:
        asset=fixture['asset'];spec=catalogue[asset]
        if digest(resources/spec['model'])!=source_rows[asset]['modelSha256'] or digest(resources/spec['animation'])!=source_rows[asset]['animationSha256']:raise ValueError('Retained source model/animation bytes changed: '+asset)
        lookup,cubes=static_cubes(json.loads((resources/spec['model']).read_text('utf8')))
        local=[b for _,b in cubes]
        if fixture['defaultMotionEnvelopeActive'] and asset!='ladder':
            animation=json.loads((resources/spec['animation']).read_text('utf8'));motion=motions(spec,animation)
            local += [value for name,raw in cubes if (value:=cube_envelope(name,raw,lookup,motion,True)[0]) is not None]
        if asset=='ladder':local=[(-.75,-12,-1.8125,.75,.875,-1.25),(-.75,-29.5625,-1.8125,.75,-12,-1.25),(-11/16,11/16,-24/16,11/16,12/16,24/16)]
        elif asset in CUSTOM:local=[union(local)]
        selection=[world_box(b,fixture['origin'],spec['scale']) for b in local]
        stone=(-64,100,64,-63,101,65)
        if not any(exposed(b,stone) for b in selection):stone=(-63,100,64,-62,101,65)
        native=floor+[stone];physical=fixture['physicalBoxes']
        old,old_counts=first_candidate(selection,physical,native,stone,(2,),False)
        proposed,counts=first_candidate(selection,physical,native,stone,(2,3,4),True)
        rows.append({'asset':asset,'sourceModel':spec['model'],'sourceModelSha256':digest(resources/spec['model']),
                     'sourceAnimation':spec['animation'],'sourceAnimationSha256':digest(resources/spec['animation']),
                     'defaultSelectionBoxCount':len(selection),'nativeStone':stone,'physicalBoxCount':len(physical),
                     'originalRayOnlyFirstCandidate':old,'originalSearchCounts':old_counts,
                     'proposedCollisionFreeCandidate':proposed,'proposedSearchCounts':counts,
                     'staticCandidateAvailable':proposed is not None,
                     'nativeFirstRayDoesNotLoadChunks':'Static calculation only; actual native shape/ray/context must be read by QA.'})
    report={'schema':'dw-v10-static-source-rp-part-camera-feasibility-v1','status':'STATIC69_SOURCE_CANDIDATES_NOT_RUNTIME_RAY_OR_PLAYER_PROOF',
            'productionJarSha256':a.jar_sha256,'defaultFixtureReport':str(a.fixture_report),'defaultFixtureReportSha256':digest(a.fixture_report),
            'population':len(rows),'sourceModelsAndAnimationBytesUnchanged':True,
            'candidateDistanceOrder':[2,3,4],'fiveDirections':['−Z','+Z','−X','+X','+Y'],
            'standingActorAssumption':{'width':.6,'height':1.8,'eyeHeight':1.62},
            'nativeScope':'Exactly source-authored25 STONE pad cells X−66..−62,Z62..66,Y99 plus one ordinarily placedSTONE root/east; no whole-world/nativeVoxelShape query fabricated.',
            'firstRayOnlyCameraBodyConflictAssets':[x['asset'] for x in rows if x['originalRayOnlyFirstCandidate'] and (x['originalRayOnlyFirstCandidate']['physicalBodyOverlap'] or x['originalRayOnlyFirstCandidate']['nativeBodyOverlap'])],
            'noCollisionFreeCandidateAssets':[x['asset'] for x in rows if not x['staticCandidateAvailable']],
            'candidateIterationsBeforeFirstFeasibleTotal':sum(x['proposedSearchCounts']['candidates'] for x in rows),
            'largestSearches':sorted([{'asset':x['asset'],**x['proposedSearchCounts']} for x in rows],key=lambda x:x['candidates'],reverse=True)[:8],
            'sourceFileInputs':{str(path.relative_to(ROOT)).replace('\\','/'):digest(path) for path in [Path(__file__),ROOT/'tools/audit_v10_rp_fixture_supports.py',ROOT/'tools/audit_rp_motion_envelopes.py',resources/'catalog.json']},
            'assets':rows,
            'limits':['Source part boxes are conservative rest-cube AABBs; cage animation envelopes remain conservative, not pixel-perfect meshes.',
                      'No ordinary input/packet/nativeVoxelShape/entity-index ray/actual player motion was performed. Only one target RP and declared native pad/stone are modeled.',
                      'Native ray endpoint ties and finite slabs are approximated numerically; actual Minecraft ray and exact UUID are required independently.',
                      'Actor has standing dimensions. Sneaking/swimming/other poses require actual dimensions.',
                      'Search counters describe this offline candidate enumeration, not CPU/GPU/FPS or a full-world workload.',
                      'Current MIN10 18architecture and7canonicalpartialPASS are retained; wholeclient failed and does not satisfy final package gates.']}
    a.output.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n','utf8')
    print(json.dumps({'report':str(a.output.resolve()),'sha256':digest(a.output),'population':len(rows),'rayOnlyBodyConflictAssets':report['firstRayOnlyCameraBodyConflictAssets'],'noFeasible':report['noCollisionFreeCandidateAssets'],'iterations':report['candidateIterationsBeforeFirstFeasibleTotal'],'largestSearches':report['largestSearches']},ensure_ascii=False))


if __name__=='__main__':main()
