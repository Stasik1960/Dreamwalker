"""Clip only v12.7.3 added caps; preserve original v12.7.2 art and geometry.

Default is a read-only audit with proposed JSON under build/. Apply requires the
reviewed plan digest. Original alpha/cutout planes are reserved regardless of
their pixel opacity: an opaque repair must not replace intended transparency.
"""
from __future__ import annotations
import argparse, collections, copy, functools, hashlib, json, math, subprocess
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
REPO=ROOT.parent
RES=ROOT/'src/architecture/resources'
PREFIX='bloodborne complete/src/architecture/resources/assets/'
BASELINE='57c22e2a'
ADDED_REVISION='38d83cfb'
EPS=1e-8
SIDES={'west':(0,-1),'east':(0,1),'down':(1,-1),'up':(1,1),'north':(2,-1),'south':(2,1)}
# FaceBakery/CubeFace vertex order. Each tuple uses 0=from and 1=to.
CORNERS={
    'down':((0,0,1),(0,0,0),(1,0,0),(1,0,1)),
    'up':((0,1,0),(0,1,1),(1,1,1),(1,1,0)),
    'north':((1,1,0),(1,0,0),(0,0,0),(0,1,0)),
    'south':((0,1,1),(0,0,1),(1,0,1),(1,1,1)),
    'west':((0,1,0),(0,0,0),(0,0,1),(0,1,1)),
    'east':((1,1,1),(1,0,1),(1,0,0),(1,1,0)),
}

def digest(data):return hashlib.sha256(data).hexdigest()
def canonical(value):return json.dumps(value,ensure_ascii=False,sort_keys=True,separators=(',',':')).encode('utf8')
def clean(value):
    value=round(float(value),12)
    return int(value) if value.is_integer() else value
def area(rect):return max(0,rect[2]-rect[0])*max(0,rect[3]-rect[1])
def intersection(a,b):
    c=(max(a[0],b[0]),max(a[1],b[1]),min(a[2],b[2]),min(a[3],b[3]))
    return c if c[2]>c[0]+EPS and c[3]>c[1]+EPS else None
def subtract(rect,cut):
    overlap=intersection(rect,cut)
    if overlap is None:return [rect]
    x0,y0,x1,y1=rect;a,b,c,d=overlap
    return [r for r in ((x0,y0,a,y1),(c,y0,x1,y1),(a,y0,c,b),(a,d,c,y1)) if area(r)>EPS]
def uncovered(rect,cuts):
    result=[rect]
    for cut in cuts:
        result=[piece for remaining in result for piece in subtract(remaining,cut)]
    return sorted(result)
def rotated(element):return abs(element.get('rotation',{}).get('angle',0))>EPS
def authored_parallel_surface(element):
    """A bbox is not proof of volume when all authored faces share one axis."""
    axes={SIDES[side][0] for side in element.get('faces',{})}
    return len(axes)==1
def authored_single_plane(element):
    return authored_parallel_surface(element) and len({face_plane(element,side) for side in element.get('faces',{})})==1
def occluding_authored_volume(element):
    return all(element['to'][k]-element['from'][k]>EPS for k in range(3)) and len({SIDES[s][0] for s in element.get('faces',{})})>=2
def face_rect(element,side):
    axis,sign=SIDES[side];others=[i for i in range(3) if i!=axis]
    return (element['from'][others[0]],element['from'][others[1]],element['to'][others[0]],element['to'][others[1]])
def face_plane(element,side):
    axis,sign=SIDES[side]
    return element['to'][axis] if sign>0 else element['from'][axis]
def corners(element,side):
    return [tuple(element['to'][axis] if high else element['from'][axis] for axis,high in enumerate(c)) for c in CORNERS[side]]
def default_uv(element,side):
    x,y,z=element['from'];X,Y,Z=element['to']
    return {'down':[x,16-Z,X,16-z],'up':[x,z,X,Z],
            'north':[16-X,16-Y,16-x,16-y],'south':[x,16-Y,X,16-y],
            'west':[z,16-Y,Z,16-y],'east':[16-Z,16-Y,16-z,16-y]}[side]
def corner_uvs(element,side,face):
    uv=face.get('uv',default_uv(element,side));rotation=face.get('rotation',0)
    if rotation not in (0,90,180,270):raise ValueError('Unsupported native face rotation')
    return [(uv[0] if (i+rotation//90)%4 in (0,1) else uv[2],
             uv[1] if (i+rotation//90)%4 in (0,3) else uv[3]) for i in range(4)]
def sample_uv(element,side,face,point):
    """Affine sample from geometric corner0 and its two adjacent corners."""
    positions=corners(element,side);uv=corner_uvs(element,side,face)
    origin=positions[0]
    fractions=[]
    for neighbor in (1,3):
        axis=next(i for i in range(3) if abs(positions[neighbor][i]-origin[i])>EPS)
        fractions.append((point[axis]-origin[axis])/(positions[neighbor][axis]-origin[axis]))
    return tuple(uv[0][i]+fractions[0]*(uv[1][i]-uv[0][i])+fractions[1]*(uv[3][i]-uv[0][i]) for i in range(2))
def clipped_plane(element,side,face,rect):
    """An uncovered face rectangle with native orientation/flipped UV intact."""
    axis,_=SIDES[side];others=[i for i in range(3) if i!=axis]
    low=list(element['from']);high=list(element['to']);plane=face_plane(element,side)
    low[axis]=high[axis]=plane
    for i,v in enumerate(others):low[v]=rect[i];high[v]=rect[i+2]
    result={k:copy.deepcopy(v) for k,v in element.items() if k not in ('from','to','faces','rotation')}
    result.update({'from':list(map(clean,low)),'to':list(map(clean,high))})
    new_face=copy.deepcopy(face);values=[None]*4;rotation=face.get('rotation',0)//90
    for i,point in enumerate(corners(result,side)):
        u,v=sample_uv(element,side,face,point);index=(i+rotation)%4
        for slot,value in ((0 if index in (0,1) else 2,u),(1 if index in (0,3) else 3,v)):
            if values[slot] is not None and abs(values[slot]-value)>1e-7:raise AssertionError('Non-affine UV crop')
            values[slot]=value
    new_face['uv']=list(map(clean,values));result['faces']={side:new_face}
    return result

class GitObjects:
    def __init__(self):
        self.process=subprocess.Popen(['git','cat-file','--batch'],cwd=REPO,stdin=subprocess.PIPE,stdout=subprocess.PIPE)
        self.cache={}
    def get(self,revision,name):
        key=(revision,name)
        if key in self.cache:return self.cache[key]
        self.process.stdin.write((revision+':'+name+'\n').encode());self.process.stdin.flush()
        header=self.process.stdout.readline().decode().rstrip('\n')
        if header.endswith(' missing'):value=None
        else:
            size=int(header.rsplit(' ',1)[1]);value=self.process.stdout.read(size)
            assert self.process.stdout.read(1)==b'\n'
        self.cache[key]=value;return value
    def close(self):
        self.process.stdin.close();self.process.wait(timeout=10)

def model_path(name):
    namespace,path=name.split(':',1)
    return PREFIX+namespace+'/models/'+path+'.json'
def resolve_elements(doc,revision,git,seen=()):
    if 'elements' in doc:return copy.deepcopy(doc['elements'])
    parent=doc.get('parent')
    if not parent or parent.startswith('builtin:'):return []
    if ':' not in parent:parent='minecraft:'+parent
    if parent in seen:raise ValueError('Parent cycle')
    raw=git.get(revision,model_path(parent))
    if raw is None:raise ValueError('Unavailable original model parent '+parent)
    return resolve_elements(json.loads(raw),revision,git,seen+(parent,))

def blockers(element_index,element,side,original_elements,accepted):
    axis,sign=SIDES[side];plane=face_plane(element,side);result=[];rotations=[]
    for index,other in enumerate(original_elements):
        if rotated(other):
            rotations.append(index);continue
        if index!=element_index and occluding_authored_volume(other):
            # The neighbouring volume crosses/extends beyond this face plane.
            covered=other['from'][axis]<=plane+EPS and other['to'][axis]>=plane-EPS
            beyond=other['to'][axis]>plane+EPS if sign>0 else other['from'][axis]<plane-EPS
            if covered and beyond:result.append(('originalVolume',index,face_rect(other,side)))
        for other_side in other.get('faces',{}):
            other_axis,other_sign=SIDES[other_side]
            if other_axis==axis and other_sign==sign and abs(face_plane(other,other_side)-plane)<EPS:
                # Reserve even fully transparent/cutout original faces.
                result.append(('originalCoplanarFace',index,face_rect(other,other_side)))
    for added_side,added_plane,rect in accepted:
        if added_side==side and abs(added_plane-plane)<EPS:result.append(('earlierAddedCoplanarFace',None,rect))
    return result,rotations

def repair(original_elements,current_elements,*,preserve_original_opening=False):
    if len(original_elements)!=len(current_elements):raise ValueError('Original element count changed')
    result=copy.deepcopy(current_elements);accepted=[];actions=[];generated=[]
    for index,(old,current) in enumerate(zip(original_elements,current_elements)):
        geometry_old={k:v for k,v in old.items() if k!='faces'};geometry_current={k:v for k,v in current.items() if k!='faces'}
        if geometry_old!=geometry_current:raise ValueError('Original element metadata/coordinates changed')
        old_faces=old.get('faces',{});new_faces=current.get('faces',{})
        if any(new_faces.get(side)!=face for side,face in old_faces.items()):raise ValueError('Original face/UV changed')
        for side in sorted(new_faces.keys()-old_faces.keys()):
            if rotated(current) or any(current['to'][a]-current['from'][a]<=EPS for a in range(3)):
                raise ValueError('Added cap was not an unrotated volume face')
            if preserve_original_opening:
                result[index]['faces'].pop(side)
                actions.append({'element':index,'side':side,'originalAddedArea':area(face_rect(current,side)),
                                'remainingArea':0,'removedArea':area(face_rect(current,side)),
                                'action':'removeAddedCapToPreserveOriginalBarrelCutout','pieces':[],
                                'originalV15OpeningAndSilhouettePreserved':True})
                continue
            if authored_single_plane(old):
                # Some original pack elements have a 3D bbox but only one
                # visible plane. Closing their bbox invents sides/undersides.
                result[index]['faces'].pop(side)
                actions.append({'element':index,'side':side,'originalAddedArea':area(face_rect(current,side)),
                                'remainingArea':0,'removedArea':area(face_rect(current,side)),
                                'action':'removeAddedCapFromAuthoredSinglePlane','pieces':[],
                                'originalFaces':list(old_faces),'originalBboxIsNotVisibleVolume':True})
                continue
            rect=face_rect(current,side);cuts,rotations=blockers(index,current,side,original_elements,accepted)
            relevant=[(kind,other,cut) for kind,other,cut in cuts if intersection(rect,cut)]
            pieces=uncovered(rect,[r[2] for r in relevant]);before=area(rect);after=sum(map(area,pieces))
            if abs(before-after)<EPS:
                accepted.append((side,face_plane(current,side),rect));continue
            result[index]['faces'].pop(side)
            for piece in pieces:
                generated.append(clipped_plane(current,side,new_faces[side],piece));accepted.append((side,face_plane(current,side),piece))
            actions.append({'element':index,'side':side,'originalAddedArea':before,'remainingArea':after,'removedArea':before-after,
                            'action':'removeRedundantAddedFace' if not pieces else 'clipAddedFaceToUncoveredRectangles',
                            'pieces':pieces,'blockers':[{'kind':kind,'element':other,'rect':cut} for kind,other,cut in relevant],
                            'rotatedOriginalElementsExcludedFromAxisAlignedSubtraction':rotations})
            # Coverage and overlap proofs use the same partition, not its source face.
            if after< -EPS or after>before+EPS:raise AssertionError('Area grew')
            for i,piece in enumerate(pieces):
                if any(intersection(piece,cut) for _,_,cut in relevant):raise AssertionError('Clipped cap still occluded')
                if any(intersection(piece,q) for q in pieces[:i]):raise AssertionError('Clipped pieces overlap')
    result.extend(generated)
    # Original art, flags, coordinates and UV remain exactly intact.
    for index,old in enumerate(original_elements):
        actual=result[index]
        assert {k:v for k,v in old.items() if k!='faces'}=={k:v for k,v in actual.items() if k!='faces'}
        assert all(actual.get('faces',{}).get(side)==face for side,face in old.get('faces',{}).items())
    verify_uncovered_caps(original_elements,result)
    return result,actions

def verify_uncovered_caps(original_elements,result):
    """Verify the complete repaired output, including unchanged added caps."""
    accepted=[];count=0
    for index,element in enumerate(result):
        old_faces=original_elements[index].get('faces',{}) if index<len(original_elements) else {}
        for side in sorted(element.get('faces',{}).keys()-old_faces.keys()):
            rect=face_rect(element,side)
            cuts,_=blockers(index,element,side,original_elements,accepted)
            if any(intersection(rect,cut) for _,_,cut in cuts):raise AssertionError('Final cap overlaps an original blocker or another added cap')
            accepted.append((side,face_plane(element,side),rect));count+=1
    return count

def plane_provenance(original_count,actions):
    """Derived functional models can keep a clipped cap with its source part."""
    result=[]
    for action in actions:
        if action['action']!='clipAddedFaceToUncoveredRectangles':continue
        for piece in action['pieces']:
            result.append({'outputElement':original_count+len(result),'sourceElement':action['element'],
                           'sourceFace':action['side'],'rect':piece})
    return result

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apply',action='store_true')
    parser.add_argument('--expected-plan-sha256')
    parser.add_argument('--output',type=Path,default=ROOT/'build/gallery-followups-v1274/surface-caps')
    args=parser.parse_args();out=args.output.resolve()
    if not out.is_relative_to((ROOT/'build').resolve()):raise ValueError('Reports/proposals must stay under project build/')
    changed=subprocess.run(['git','diff',BASELINE+'..'+ADDED_REVISION,'--name-only','--',PREFIX+'bloodborne_dw/models/'],cwd=REPO,capture_output=True,check=True).stdout.decode().splitlines()
    names=sorted(name for name in changed if '/models/remaining/source/' in name or '/models/remaining/source_uvlock/' in name)
    git=GitObjects();plans=[];excluded=[];failures=[];cap_count=0
    try:
        for name in sorted(changed):
            if '/models/remaining/final_followups/door_90960_' in name and git.get(BASELINE,name) is None:
                excluded.append({'path':name,'reason':'Generated v12.7.3 door90960 copy has no original baseline; separate door scope'})
        for name in names:
            original_raw=git.get(BASELINE,name)
            if original_raw is None:
                excluded.append({'path':name,'reason':'No pre-v12.7.3 baseline; generated door copies are separate scope'});continue
            current_path=(REPO/name).resolve()
            if not current_path.is_relative_to(RES.resolve()):raise ValueError('Model escapes resource directory')
            raw=current_path.read_bytes();original=json.loads(original_raw);current=json.loads(raw)
            try:
                if {k:v for k,v in original.items() if k!='elements'}!={k:v for k,v in current.items() if k!='elements'}:raise ValueError('Original model flags/textures/parent changed')
                old_elements=resolve_elements(original,BASELINE,git);new_elements=current.get('elements',old_elements)
                cap_count+=sum(len(e.get('faces',{}).keys()-o.get('faces',{}).keys()) for o,e in zip(old_elements,new_elements))
                original_barrel=name.endswith('/models/remaining/source/minecraft/block/blue_orchid.json')
                repaired,actions=repair(old_elements,new_elements,preserve_original_opening=original_barrel)
                if not actions:continue
                document=copy.deepcopy(current);document['elements']=repaired
                newline='\r\n' if b'\r\n' in raw else '\n'
                proposed=(json.dumps(document,ensure_ascii=False,indent=2)+'\n').replace('\n',newline).encode('utf8')
                provenance=plane_provenance(len(old_elements),actions)
                assert len(provenance)==len(repaired)-len(old_elements)
                plans.append({'path':name,'inputSha256':digest(raw),'outputSha256':digest(proposed),'addedPlaneElements':len(repaired)-len(old_elements),'addedPlanesProvenance':provenance,'actions':actions,'proposed':proposed})
            except (ValueError,AssertionError) as error:failures.append({'path':name,'reason':str(error)})
    finally:git.close()
    public=[{k:v for k,v in row.items() if k!='proposed'} for row in plans]
    plan_sha=digest(canonical(public))
    if args.apply:
        if failures:raise ValueError('Audit contains unsafe models; review before applying')
        if args.expected_plan_sha256!=plan_sha:raise ValueError('Apply requires exact reviewed --expected-plan-sha256 '+plan_sha)
        if any(digest((REPO/row['path']).read_bytes())!=row['inputSha256'] for row in plans):raise ValueError('Model changed since audit')
        for row in plans:(REPO/row['path']).write_bytes(row['proposed'])
    out.mkdir(parents=True,exist_ok=True)
    for row in plans:
        relative=row['path'].split('/assets/',1)[1];proposal=out/'proposed'/relative
        proposal.parent.mkdir(parents=True,exist_ok=True);proposal.write_bytes(row['proposed'])
    counts=collections.Counter(action['action'] for row in plans for action in row['actions'])
    report={'status':'APPLIED_REQUIRES_NATIVE_VALIDATION' if args.apply else 'AUDIT_ONLY_NO_PRODUCTION_CHANGES',
            'baseline':BASELINE,'addedRevision':ADDED_REVISION,'planSha256':plan_sha,'modelsExamined':len(names),
            'addedFacesExamined':cap_count,'modelsChanged':len(plans),'actionCounts':dict(counts),
            'addedPlaneElements':sum(row['addedPlaneElements'] for row in plans),'originalFacesUvCoordinatesModelFlagsAndPngEdited':False,
            'authoredSinglePlanePolicy':'Remove only newly added caps when all original faces share a single geometric plane; bbox dimensions do not imply a rendered volume. Parallel-only/no-face bboxes are not volume occluders.',
            'confirmedBarrelCutoutPolicy':'90308 blue_orchid restores all original v15 face omissions; cap additions must not fill rounded alpha-mask corners or the open hoop structure.',
            'rotatedOriginalElementsPolicy':'Excluded from axis-aligned volume/coplanar subtraction; recorded on clipped faces. Cross-part/owner intersections are separate renderer scope.',
            'excludedNewModels':excluded,'unsafeModels':failures,'plans':public}
    (out/('apply-report.json' if args.apply else 'audit-report.json')).write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
    (out/'summary.json').write_text(json.dumps({k:v for k,v in report.items() if k not in ('plans','excludedNewModels')},ensure_ascii=False,indent=2)+'\n',encoding='utf8')
    if failures:raise SystemExit(2)

if __name__=='__main__':main()
