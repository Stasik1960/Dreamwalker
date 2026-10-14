"""Audit tiny normal offsets only for authored flat art that is coplanar.

This does not widen a rendered volume. Grid-aligned surface art and overlapping
flat layers receive a deterministic separation, while door models and angled
arches are excluded. Use --caps-plan to compose with a reviewed cap proposal.
"""
from pathlib import Path
import argparse,collections,copy,json,math
import final_v1274_surface_caps as cap

GAP=1/64                 # model units: exactly1/1024 block
LAYER_STEP=1/512         # model units: exactly1/8192 block
AXIS={'x':0,'y':1,'z':2}
MODEL_PREFIXES=('bloodborne_dw:remaining/source/','bloodborne_dw:remaining/source_uvlock/')

def rotate_vector(point,axis,degrees):
    p=list(point);c=math.cos(math.radians(degrees));s=math.sin(math.radians(degrees))
    if axis==0:p[1],p[2]=c*p[1]-s*p[2],s*p[1]+c*p[2]
    elif axis==1:p[0],p[2]=c*p[0]+s*p[2],-s*p[0]+c*p[2]
    else:p[0],p[1]=c*p[0]-s*p[1],s*p[0]+c*p[1]
    return p
def rotate_point(point,axis,degrees,pivot):
    p=rotate_vector([point[i]-pivot[i] for i in range(3)],axis,degrees)
    return [p[i]+pivot[i] for i in range(3)]
def element_point(element,point):
    r=element.get('rotation',{});angle=r.get('angle',0)
    if not angle:return list(point)
    axis=AXIS[r['axis']];pivot=r['origin'];p=rotate_point(point,axis,angle,pivot)
    if r.get('rescale'):
        scale=1/abs(math.cos(math.radians(angle)))
        for a in range(3):
            if a!=axis:p[a]=pivot[a]+(p[a]-pivot[a])*scale
    return p
def part_point(point,part):
    p=list(point);pivot=part.get('pivot',[8,8,8]);extra=part.get('extraPivot',[8,8,8])
    for axis,angle,origin in ((1,part.get('extraYaw',0),extra),(0,-part.get('pitch',0),pivot),(1,-part.get('yaw',0),pivot)):
        if angle:p=rotate_point(p,axis,angle,origin)
    return [p[i]+part.get('offset',[0,0,0])[i] for i in range(3)]
def part_normal(normal,part):
    p=list(normal)
    for axis,angle in ((1,part.get('extraYaw',0)),(0,-part.get('pitch',0)),(1,-part.get('yaw',0))):
        if angle:p=rotate_vector(p,axis,angle)
    return p
def rendered_faces(element):
    return [side for side in element.get('faces',{}) if cap.area(cap.face_rect(element,side))>cap.EPS]
def single_plane(element):
    sides=rendered_faces(element)
    axes={cap.SIDES[s][0] for s in sides}
    if len(axes)!=1:return None
    axis=next(iter(axes));planes={cap.face_plane(element,s) for s in sides}
    if len(planes)!=1:return None
    rotation=element.get('rotation',{})
    # Rotation about the plane's own normal keeps it axis aligned and commutes
    # with a normal translation. Door/arch rotations about another axis do not.
    if cap.rotated(element) and AXIS[rotation['axis']]!=axis:return None
    return axis,next(iter(planes)),sides
def polygon(element,side):
    axis,_=cap.SIDES[side];axes=[i for i in range(3) if i!=axis]
    p=[element_point(element,corner) for corner in cap.corners(element,side)]
    result=[(point[axes[0]],point[axes[1]]) for point in p]
    return result if signed_area(result)>0 else list(reversed(result))
def signed_area(p):return sum(p[i][0]*p[(i+1)%len(p)][1]-p[(i+1)%len(p)][0]*p[i][1] for i in range(len(p)))/2
def polygon_overlap(a,b):
    """Positive-area intersection of convex rendered face polygons."""
    result=list(a)
    for n,c in enumerate(b):
        d=b[(n+1)%len(b)]
        def cross(p):return (d[0]-c[0])*(p[1]-c[1])-(d[1]-c[1])*(p[0]-c[0])
        if not result:return False
        source=result;result=[]
        for i,q in enumerate(source):
            p=source[i-1];cp,cq=cross(p),cross(q);pin=cp>=-cap.EPS;qin=cq>=-cap.EPS
            if pin!=qin:
                t=cp/(cp-cq);result.append((p[0]+t*(q[0]-p[0]),p[1]+t*(q[1]-p[1])))
            if qin:result.append(q)
    return len(result)>=3 and abs(signed_area(result))>cap.EPS
def same_plane_face(element,side,axis,plane,sign):
    if cap.SIDES[side]!=(axis,sign) or abs(cap.face_plane(element,side)-plane)>cap.EPS:return False
    r=element.get('rotation',{})
    return not cap.rotated(element) or AXIS[r['axis']]==axis
def grid_uses(element,side,uses):
    axis,sign=cap.SIDES[side];normal=[0,0,0];normal[axis]=sign;point=cap.corners(element,side)[0]
    found=[]
    for use in uses:
        transformed=part_normal(normal,use['part']);world_axis=max(range(3),key=lambda a:abs(transformed[a]))
        if abs(abs(transformed[world_axis])-1)>1e-6:continue
        position=part_point(element_point(element,point),use['part'])[world_axis]
        if abs(position/16-round(position/16))<1e-6:found.append({'path':use['path'],'variant':use['variant'],'pose':use['pose'],'axis':world_axis,'planeModelUnits':cap.clean(position)})
    return found
def shift(element,axis,amount,sides):
    item=copy.deepcopy(element)
    item['from'][axis]=cap.clean(item['from'][axis]+amount);item['to'][axis]=cap.clean(item['to'][axis]+amount)
    item['faces']={s:copy.deepcopy(element['faces'][s]) for s in sides}
    assert all(abs((item['to'][a]-item['from'][a])-(element['to'][a]-element['from'][a]))<1e-8 for a in range(3))
    return item

def native_bounds_legal(element):
    """Vanilla ModelElement.Deserializer validates both endpoints in [-16,32]."""
    return all(-16<=value<=32 for key in ('from','to') for value in element[key])

def legal_normal_shift(element,axis,requested,sides):
    """Keep the tiny separation inward only when the native JSON limit forbids outward.

    The element is rendered as one plane even when its unused bbox is thick.
    Translate its complete bbox, preserving size, UV and model flags.
    """
    if not native_bounds_legal(element):raise ValueError('Original element exceeds native JSON boundaries')
    if native_bounds_legal(shift(element,axis,requested,sides)):return requested
    if native_bounds_legal(shift(element,axis,-requested,sides)):return -requested
    raise ValueError('No legal tiny normal translation inside native JSON boundaries')
def repair_layers(original,current,uses):
    if len(current)<len(original):raise ValueError('Original elements were removed')
    result=copy.deepcopy(current);extra=[];actions=[];accepted=[]
    for index,old in enumerate(original):
        info=single_plane(old)
        if info is None:continue
        actual=current[index]
        if {k:v for k,v in old.items() if k!='faces'}!={k:v for k,v in actual.items() if k!='faces'}:raise ValueError('Original flat geometry changed before layer repair')
        if any(actual.get('faces',{}).get(s)!=f for s,f in old.get('faces',{}).items()):raise ValueError('Original flat face UV changed')
        axis,plane,sides=info;groups=[]
        for side in sides:
            _,sign=cap.SIDES[side];poly=polygon(old,side);overlaps=[]
            for other_index,other in enumerate(original):
                if other_index==index:continue
                for os in rendered_faces(other):
                    if same_plane_face(other,os,axis,plane,sign) and polygon_overlap(poly,polygon(other,os)):overlaps.append({'element':other_index,'side':os})
            grid=grid_uses(old,side,uses)
            earlier=[r for r in accepted if r['axis']==axis and r['sign']==sign and abs(r['plane']-plane)<cap.EPS and polygon_overlap(poly,r['polygon'])]
            if not grid and not overlaps and not earlier:continue
            rank=max((r['rank']+1 for r in earlier),default=0)
            requested=sign*(GAP+rank*LAYER_STEP)
            delta=legal_normal_shift(actual,axis,requested,[side])
            groups.append((delta,side))
            accepted.append({'axis':axis,'sign':sign,'plane':plane,'polygon':poly,'rank':rank})
            actions.append({'element':index,'side':side,'originalPlane':plane,'normal':sign,'translationModelUnits':delta,
                            'translationBlocks':delta/16,'nativeBoundaryDirection':'outward' if delta==requested else 'inward',
                            'overlappingOriginalFaces':overlaps,'gridAlignedUses':grid,
                            'deterministicLayerRank':rank,'renderedVolumeStretched':False,'originalFaceUvTextureAndFlagsChanged':False})
        if not groups:continue
        # Preserve non-rendered degenerate faces in the first copy. Opposite
        # visible sides need separate plane elements to offset along each normal.
        unshifted=[s for s in actual.get('faces',{}) if s not in {side for _,side in groups}]
        replacements=[]
        if unshifted:
            item=copy.deepcopy(actual);item['faces']={s:copy.deepcopy(actual['faces'][s]) for s in unshifted};replacements.append(item)
        for delta,side in groups:replacements.append(shift(actual,axis,delta,[side]))
        result[index]=replacements[0];extra.extend(replacements[1:])
    result.extend(extra)
    return result,actions

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--caps-plan',type=Path);p.add_argument('--apply',action='store_true');p.add_argument('--expected-plan-sha256')
    p.add_argument('--output',type=Path,default=cap.ROOT/'build/gallery-followups-v1274/flat-layers')
    args=p.parse_args();out=args.output.resolve()
    if not out.is_relative_to((cap.ROOT/'build').resolve()):raise ValueError('Output stays under project build/')
    gallery=json.loads((cap.ROOT/'build/v1273-gallery-delivery/World/gallery-manifest.json').read_text(encoding='utf8'))
    paths=sorted({e.get('registry','').split(':')[-1] for e in gallery['exhibits'] if e.get('registry','').startswith('bloodborne_dw:source_') and e.get('gridId','').startswith(('new/','v12/'))})
    uses=collections.defaultdict(list);door_models=set()
    for path in paths:
        spec=json.loads((cap.RES/'bloodborne_dw/composite'/f'{path}.json').read_text(encoding='utf8'))
        for index,variant in enumerate(spec['variants']):
            for pose in ('closed','open'):
                for part in variant['poses'].get(pose,variant['poses']['closed'])['parts']:
                    name=part['model']
                    if not name.startswith(MODEL_PREFIXES):continue
                    if spec.get('openable') or 'door' in name:door_models.add(name)
                    uses[name].append({'path':path,'variant':index,'pose':pose,'part':part})
    cap_rows={};cap_report=None
    if args.caps_plan:
        cap_report=json.loads(args.caps_plan.read_text(encoding='utf8'));cap_rows={r['path']:r for r in cap_report['plans']}
    git=cap.GitObjects();plans=[];excluded=[];failures=[]
    try:
        for name in sorted(uses):
            if name in door_models:excluded.append({'model':name,'reason':'Door model; arches and leaf geometry are separate scope'});continue
            path=cap.model_path(name);old_raw=git.get(cap.BASELINE,path)
            if old_raw is None:excluded.append({'model':name,'reason':'No original baseline'});continue
            old=json.loads(old_raw);original=cap.resolve_elements(old,cap.BASELINE,git)
            cap_plan=cap_rows.get(path)
            input_path=args.caps_plan.parent/'proposed'/path.split('/assets/',1)[1] if cap_plan else cap.REPO/path
            raw=input_path.read_bytes()
            if cap_plan and cap.digest(raw)!=cap_plan['outputSha256']:raise ValueError('Reviewed cap proposal changed')
            current=json.loads(raw)
            try:
                if {k:v for k,v in old.items() if k!='elements'}!={k:v for k,v in current.items() if k!='elements'}:raise ValueError('Original model metadata changed')
                repaired,actions=repair_layers(original,current.get('elements',original),uses[name])
                if not actions:continue
                proposed=copy.deepcopy(current);proposed['elements']=repaired
                newline='\r\n' if b'\r\n' in raw else '\n'
                output=(json.dumps(proposed,ensure_ascii=False,indent=2)+'\n').replace('\n',newline).encode('utf8')
                plans.append({'path':path,'inputSha256':cap.digest(raw),'outputSha256':cap.digest(output),'sourceInput':'reviewedCapsProposal' if cap_plan else 'currentModel',
                              'splitPlaneElements':len(repaired)-len(current.get('elements',original)),'actions':actions,'output':output})
            except (ValueError,AssertionError) as error:failures.append({'path':path,'reason':str(error)})
    finally:git.close()
    public=[{k:v for k,v in r.items() if k!='output'} for r in plans];plan_sha=cap.digest(cap.canonical(public))
    if args.apply:
        if failures:raise ValueError('Unsafe source differences in audit')
        if args.expected_plan_sha256!=plan_sha:raise ValueError('Exact reviewed plan required: '+plan_sha)
        if any(cap.digest((cap.REPO/r['path']).read_bytes())!=r['inputSha256'] for r in plans):raise ValueError('Apply reviewed source cap repair first; model input differs')
        for row in plans:(cap.REPO/row['path']).write_bytes(row['output'])
    out.mkdir(parents=True,exist_ok=True)
    for row in plans:
        target=out/'proposed'/row['path'].split('/assets/',1)[1];target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(row['output'])
    report={'status':'APPLIED_FLAT_ART_REQUIRES_NATIVE_VALIDATION' if args.apply else 'AUDIT_ONLY_NO_PRODUCTION_CHANGES',
            'planSha256':plan_sha,'sourceCapPlanSha256':cap_report['planSha256'] if cap_report else None,
            'modelsExamined':len(uses),'modelsChanged':len(plans),'normalOffsetFaces':sum(len(r['actions']) for r in plans),
            'splitPlaneElements':sum(r['splitPlaneElements'] for r in plans),'baseGapBlocks':GAP/16,'layerStepBlocks':LAYER_STEP/16,
            'modelPngUvTexturesVolumeDimensionsAndDoorArchesEdited':False,'policy':'Only original rendered single-plane art; needs a placement-grid coincidence or original coplanar overlap. Normal translation, no volume stretch. Door models and non-axis-aligned rendered planes excluded.',
            'unsafeModels':failures,'excludedModels':excluded,'plans':public}
    (out/('apply-report.json' if args.apply else 'audit-report.json')).write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
    (out/'summary.json').write_text(json.dumps({k:v for k,v in report.items() if k not in ('plans','excludedModels')},ensure_ascii=False,indent=2)+'\n',encoding='utf8')
    if failures:raise SystemExit(2)

if __name__=='__main__':main()
