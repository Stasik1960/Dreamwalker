"""Reviewable simple physical defaults, restricted to post-V12 source_ objects.

Visual resources, picking, source transforms, IDs and structural reservations are
untouched. Native per-cell splitting is storage, not a detailed physical raster.
No Collision Studio export is present; these are defaults for a later replacement.
"""
from __future__ import annotations
import argparse
from collections import Counter
import copy
import gzip
import hashlib
import json
import math
from pathlib import Path
from gallery_platform_plan import ModelGeometry
from inspect_imported_collision_semantics import element_boxes

ROOT=Path(__file__).resolve().parents[1]
RES=ROOT/'src/architecture/resources'
OUT=ROOT/'docs/collision-simplification'
POLICY='simple-imported-physical-v1'
REMOVED={'source_jungle_trapdoor_bottom','source_jungle_trapdoor_open'}

def box(low,high):return {'from':list(low),'to':list(high)}
def union(boxes):
    return box([min(b['from'][i] for b in boxes) for i in range(3)],
               [max(b['to'][i] for b in boxes) for i in range(3)])
def volume(b):return math.prod(b['to'][i]-b['from'][i] for i in range(3))
def valid(b):return all(b['to'][i]-b['from'][i]>1e-8 for i in range(3))

def rotate_clip_boxes(boxes,rotation,mount_y=0,*,crop=True,shift=(0,0,0)):
    """Independent model-unit oracle of ImportedCollisionMath.transform."""
    if not boxes:return []
    angle=math.radians((rotation%8)*45);c,s=math.cos(angle),math.sin(angle)
    if rotation%2==0:c,s=round(c),round(s)
    transformed=[]
    for b in boxes:
        points=[(8+c*(x-8)-s*(z-8),8+s*(x-8)+c*(z-8))
                for x in (b['from'][0],b['to'][0]) for z in (b['from'][2],b['to'][2])]
        transformed.append(box([min(p[0] for p in points)+shift[0]*16,b['from'][1]+(mount_y+shift[1])*16,min(p[1] for p in points)+shift[2]*16],
                               [max(p[0] for p in points)+shift[0]*16,b['to'][1]+(mount_y+shift[1])*16,max(p[1] for p in points)+shift[2]*16]))
    if not crop:return transformed
    limits=union(transformed)
    for i in range(3):
        if -8<limits['from'][i]<0:limits['from'][i]=0
        if 16<limits['to'][i]<24:limits['to'][i]=16
    result=[]
    for b in transformed:
        candidate=box([max(b['from'][i],limits['from'][i]) for i in range(3)],
                      [min(b['to'][i],limits['to'][i]) for i in range(3)])
        if valid(candidate):result.append(candidate)
    return result

def exact_coalesce(values):
    """Only an exact rectangular union; small native stair blocks need <=3 boxes."""
    rows={tuple(b['from']+b['to']) for b in values}
    while True:
        before=len(rows)
        for axis in range(3):
            fixed=[i for i in range(6) if i not in (axis,axis+3)]
            groups={}
            for row in rows:groups.setdefault(tuple(row[i] for i in fixed),[]).append((row[axis],row[axis+3]))
            joined=set()
            for coordinates,intervals in groups.items():
                intervals.sort();lo,hi=intervals[0]
                for nlo,nhi in intervals[1:]+[(math.inf,math.inf)]:
                    if nlo<=hi:hi=max(hi,nhi)
                    else:
                        row=[0]*6
                        for i,v in zip(fixed,coordinates):row[i]=v
                        row[axis],row[axis+3]=lo,hi;joined.add(tuple(row));lo,hi=nlo,nhi
            rows=joined
        if len(rows)==before:return [box(row[:3],row[3:]) for row in sorted(rows)]

def transformed_manual_boxes(pose,values):
    """Exceptions use canonical already floor-anchored model coordinates.

    All reviewed exceptions have one offered variant and no intrinsic pitch;
    source yaw remains the item placement rotation, handled by runtime.
    """
    return copy.deepcopy(values)

# These are deliberately a few functional volumes, never all decorative pieces.
SPECIAL={
 'source_warped_gargoyle':{
  'kind':'stateful_frame_minimal_boxes','evidence':'warped_door source closed pose has two tall side volumes and an upper crown with a planar middle; open artwork folds into two vertically separated bands. Keep that purposeful empty space and separate original poses instead of one filled tall hull. At45-degree orientations closed side posts use inscribed ordinary squares so the passage is wide enough for a0.6-block player.',
  'boxes':[box([2,0,-10.5],[14,96,-2.5]),box([2,0,18.5],[14,96,26.5]),box([2,77,-2.5],[14,96,18.5])],
  'poseBoxes':{'open':[box([2,16,-10.5],[14,32,26.5]),box([2,48,-10.5],[14,69,26.5])]},'inscribedOddPosts':2},
 'source_acacia_fence_gate':{
  'kind':'open_arch_three_boxes','evidence':'acacia_fence_gate: two 48-unit jambs and a 41..48-unit lintel; central opening stays empty; carrier open=true is a static source artwork.',
  'boxes':[box([-14.5,0,8],[-7.5,48,16]),box([23.5,0,8],[30.5,48,16]),box([-14.5,41,8],[30.5,48,16])]},
 'source_deepslate_iron_ore':{
  'kind':'textured_arch_three_boxes','evidence':'spirelamp_0030 textured pointed arch on a 16x48x16 carrier: retain narrow side jambs and upper support instead of blocking the doorway with its whole carrier. At45-degree orientations the narrow side jambs have no physics, retaining only the upper header: otherwise a0.6-block axis-aligned player cannot fit this narrow rotated opening.',
  'boxes':[box([0,0,0],[2,48,16]),box([14,0,0],[16,48,16]),box([2,40,0],[14,48,16])],'omitOddPosts':2},
 'source_hold_ladder_01':{
  'kind':'stair_platform_three_boxes','evidence':'hold/ladder_01: lower tread top8, next top13 and landing top16; outer posts and handrail planes are decorative.',
  'boxes':[box([-3,0,-16],[19,8,-10]),box([-3,0,-10],[19,13,-4]),box([-3,0,-4],[19,16,32])]},
 'source_hold_wood_ladder_01':{
  'kind':'stair_platform_three_boxes','evidence':'hold/wood_ladder_01: treads at1.83883476 and6.83883476, broad landing at12.53883476; keep useful ascent and support, omit side rail and folded trim detail.',
  'boxes':[box([-5.5,0,-16],[21.5,1.83883476,-10]),box([-5.5,0,-10],[21.5,6.83883476,-4]),box([-5.5,0,-4],[21.5,12.53883476,31])]},
 'source_hold_ladder_02':{
  'kind':'vertical_ladder_two_rails','evidence':'hold/ladder_02: vertical textured rung plane with two rails; climbing is a separate opt-in zone, not many rung colliders.',
  'boxes':[box([-1,0,15],[1,16,17]),box([15,0,15],[17,16,17])],
  'climbingBounds':box([0,0,12],[16,16,20])},
 'source_hold_ladder_03':{
  'kind':'vertical_ladder_two_rails','evidence':'hold/ladder_03: two-cell vertical textured rung plane with two continuous rails; climbing uses the independently rotated two-cell zone.',
  'boxes':[box([-1,0,15],[1,32,17]),box([15,0,15],[17,32,17])],
  'climbingBounds':box([0,0,12],[16,32,20])},
 'source_potted_flowering_azalea_bush':{
  'kind':'pot_only_one_box','evidence':'source potted_flowering_azalea_bush: physical pot is5..11 by0..6, foliage uses crossed planes and a0.1-unit leaf cap; do not turn leaves into a solid crown.',
  'boxes':[box([5,0,5],[11,6,11])]},
}

def functional_native_stair(pose,physical):
    """Source-native 2/3-box stair geometry, not generic detailed/carved carriers."""
    if not 1<len(physical)<=3:return None
    boxes=[box(b['from'],b['to']) for b in physical]
    outer=union(boxes)
    if any(outer['to'][i]-outer['from'][i]>16+1e-6 for i in range(3)):return None
    if any(any(abs(v-round(v/8)*8)>1e-6 for v in b['from']+b['to']) for b in boxes):return None
    heights={b['to'][1] for b in boxes}
    if len(heights)!=2 or max(heights)-min(heights)!=8:return None
    return exact_coalesce(boxes)

def apply_policy(descriptor,models):
    result=copy.deepcopy(descriptor)
    path=result['id'].split(':',1)[1]
    if not path.startswith('source_') or path in REMOVED:raise ValueError('New surviving source_ objects only: '+path)
    rows=[]
    special=SPECIAL.get(path)
    for index,variant in enumerate(result['variants']):
        for pose_name,pose in variant['poses'].items():
            elements=element_boxes(pose,models,include_points=True)
            physical=[b for b in elements if b['physical']]
            evidence='Source elements with finite volume, all decorative detail replaced by one bounded rectangular volume.'
            kind='one_simple_box'
            if special:
                if len(result['variants'])!=1:raise ValueError('Reviewed exception variant changed: '+path)
                values=special.get('poseBoxes',{}).get(pose_name,special['boxes'])
                new=transformed_manual_boxes(pose,values);kind=special['kind'];evidence=special['evidence']
            elif not physical or not pose.get('collision'):
                new=[];kind='preserved_passable';evidence='Previously passable source artwork: zero-thickness faces and/or explicitly empty physical default remain passable; selection stays unchanged.'
            else:
                new=[union(physical)]
                if descriptor['displayName'].startswith('Ступенчатый модуль'):
                    stairs=functional_native_stair(pose,physical)
                    if stairs:
                        new=stairs;kind='native_stair_two_or_three_boxes';evidence='Actual source-native half-block treads require two heights for ascent; only two or three large rectangular boxes, no inclined raster or trim detail.'
            if any(not valid(b) for b in new):raise ValueError('Invalid simple bounds '+path)
            for b in new:
                visual=union(element_boxes(pose,models))
                if any(b['from'][i]<visual['from'][i]-1e-6 or b['to'][i]>visual['to'][i]+1e-6 for i in range(3)):
                    raise ValueError('Simple collision outside visual envelope: '+path)
            rotations=[]
            for yaw in range(8):
                if not new:rotations.append([]);continue
                angle=math.radians(yaw*45);c,s=math.cos(angle),math.sin(angle)
                if yaw%2==0:c,s=round(c),round(s)
                points=[(8+c*(p[0]-8)-s*(p[2]-8),p[1],8+s*(p[0]-8)+c*(p[2]-8))
                        for element in (physical if kind=='one_simple_box' else elements) for p in element['points']]
                actual=box([min(p[i] for p in points) for i in range(3)], [max(p[i] for p in points) for i in range(3)])
                if kind=='one_simple_box':oriented=[actual]
                else:
                    oriented=[]
                    for number,b in enumerate(rotate_clip_boxes(new,yaw,crop=False)):
                        if special and yaw%2:
                            if number<special.get('omitOddPosts',0):continue
                            if pose_name=='closed' and number<special.get('inscribedOddPosts',0):
                                original=new[number]
                                side=min(original['to'][0]-original['from'][0],original['to'][2]-original['from'][2])/math.sqrt(2)
                                center=[(b['from'][i]+b['to'][i])/2 for i in range(3)]
                                b=box([center[0]-side/2,b['from'][1],center[2]-side/2],
                                      [center[0]+side/2,b['to'][1],center[2]+side/2])
                        bounded=box([max(b['from'][i],actual['from'][i]) for i in range(3)],
                                    [min(b['to'][i],actual['to'][i]) for i in range(3)])
                        if valid(bounded):oriented.append(bounded)
                rotations.append([{side:[round(v,8) for v in b[side]] for side in ('from','to')} for b in oriented])
            before=len(pose.get('collision',[]));pose['collision']=new
            pose['simpleCollisionByRotation']=rotations
            rows.append({'id':result['id'],'variant':index,'pose':pose_name,'kind':kind,'beforeBoxes':before,'afterBoxes':len(new),'evidence':evidence,
                         'rotation0TrimmedBoxes':rotate_clip_boxes(rotations[0],0),
                         'rotation45TrimmedBoxes':rotate_clip_boxes(rotations[1],0)})
    result['simplePhysicalCollision']=True
    if special and 'climbingBounds' in special:result['importedClimbingBounds']=special['climbingBounds']
    result['collisionPolicy']=POLICY+': one ordinary bounded cuboid by default; reviewed doorway/arch, stair/landing and ladder exceptions use at most three useful boxes; passable source planes stay passable. Eight orientations use a few ordinary AABBs bounded by the actual transformed visual vertices, never strips or a second expanded rotated AABB. Six root-cell-face overflows strictly smaller than 0.5 blocks are cropped independently after instance yaw, vertical mount and SourceShift; long extents are retained. Selection and visuals unchanged. Not authored Collision Studio data.'
    return result,rows

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--apply',action='store_true');args=parser.parse_args()
    OUT.mkdir(parents=True,exist_ok=True)
    catalogue=json.loads((RES/'bloodborne_dw/remaining-catalogue.json').read_text(encoding='utf-8'))
    baseline=json.loads((ROOT/'docs/remaining-transfer/baseline.json').read_text(encoding='utf-8'))
    retired_path=RES/'bloodborne_dw/retired-source-objects.json'
    retired=json.loads(retired_path.read_text(encoding='utf-8')) if retired_path.exists() else {}
    models=ModelGeometry();rows=[];resources=[];objects=0
    history_path=OUT/'default-physical-before.jsonl.gz'
    original={}
    if history_path.exists():
        with gzip.open(history_path,'rt',encoding='utf-8') as previous:
            original={row['id']:row for row in (json.loads(line) for line in previous)}
    history=gzip.open(history_path,'wt',encoding='utf-8',newline='\n') if args.apply and not history_path.exists() else None
    try:
        for entry in catalogue['objects']:
            path=entry['path']
            if path in REMOVED:continue
            target=RES/'bloodborne_dw/composite'/f'{path}.json'
            relative=target.relative_to(ROOT).as_posix()
            if relative in baseline['implementation']:raise ValueError('Refusing V12 descriptor change: '+relative)
            data=target.read_bytes();descriptor=json.loads(data)
            simplified,details=apply_policy(descriptor,models)
            if descriptor['id'] in original:
                for detail in details:detail['beforeBoxes']=len(original[descriptor['id']]['variants'][detail['variant']]['poses'][detail['pose']])
            rows.extend(details);objects+=1
            if args.apply:
                if history:history.write(json.dumps({'id':descriptor['id'],'beforeSha256':hashlib.sha256(data).hexdigest(),
                    'variants':[{'poses':{name:pose['collision'] for name,pose in v['poses'].items()}} for v in descriptor['variants']]},ensure_ascii=False,separators=(',',':'))+'\n')
                serialized=(json.dumps(simplified,ensure_ascii=False,indent=2)+'\n').encode('utf-8')
                temporary=target.with_suffix('.json.collision-tmp')
                temporary.write_bytes(serialized)
                temporary.replace(target)
                resources.append({'file':relative,'beforeSha256':original.get(descriptor['id'],{}).get('beforeSha256',hashlib.sha256(data).hexdigest()),'afterSha256':hashlib.sha256(serialized).hexdigest()})
    finally:
        if history:history.close()
    report={'schemaVersion':1,'status':'PASS_GENERATED_DEFAULTS' if args.apply else 'PLAN',
            'policy':POLICY,'objects':objects,'poses':len(rows),'beforePhysicalBoxes':sum(r['beforeBoxes'] for r in rows),
            'afterPhysicalBoxes':sum(r['afterBoxes'] for r in rows),'maxBoxesPerPose':max(r['afterBoxes'] for r in rows),
            'kinds':dict(Counter(r['kind'] for r in rows)),'authorCollisionExport':'NOT_PROVIDED_NOT_APPLIED',
            'originalV12DescriptorsChanged':0,'visualSelectionSourcePartsChanged':0,
            'semanticExceptions':SPECIAL,'cases':rows,'resources':resources,
            'testStatus':'PENDING_INDEPENDENT_AND_GAME_CHECKS'}
    (OUT/'policy-report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(json.dumps({k:v for k,v in report.items() if k not in ('cases','resources','semanticExceptions')},ensure_ascii=False))

if __name__=='__main__':
    try:
        main()
        (OUT/'operation-status.json').write_text(json.dumps({'status':'PASS'})+'\n',encoding='utf-8')
    except Exception:
        import traceback
        OUT.mkdir(parents=True,exist_ok=True)
        (OUT/'operation-status.json').write_text(json.dumps({'status':'FAIL','traceback':traceback.format_exc()},ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
        raise
