"""Plan/apply only OPEN90986 derivatives from the reviewed repaired source.

Run after repair_single_door_90986.py and, for apply, after source caps apply.
CLOSED continues to reference its original source model; no spec/physics edit.
"""
from pathlib import Path
import argparse,copy,json
import final_v1274_surface_caps as cap

SOURCE=cap.PREFIX+'bloodborne_dw/models/remaining/source/minecraft/block/stairs/dark_prismarine_stairs_inner_right.json'
FRAME=cap.PREFIX+'bloodborne_dw/models/remaining/final_followups/door_90986_frame.json'
LEAF=cap.PREFIX+'bloodborne_dw/models/remaining/final_followups/door_90986_leaf.json'
LEFT=11/12;RIGHT=181/12;MOVING={0,3}

def slice_x(element,low,high):
    """Keep the same authored surfaces on the selected X interval."""
    item=copy.deepcopy(element);a,b=element['from'][0],element['to'][0]
    low=max(low,a);high=min(high,b)
    if high<low-cap.EPS:return None
    if abs(a-b)<cap.EPS:
        return item if low-cap.EPS<=a<=high+cap.EPS else None
    if high-low<=cap.EPS:return None
    if cap.rotated(element):raise ValueError('Moving source element is rotated')
    item['from'][0],item['to'][0]=low,high;faces={}
    for side,face in element.get('faces',{}).items():
        axis,_=cap.SIDES[side]
        if axis==0:
            if (side=='west' and abs(low-a)<cap.EPS) or (side=='east' and abs(high-b)<cap.EPS):faces[side]=copy.deepcopy(face)
        else:
            rect=cap.face_rect(item,side)
            faces[side]=cap.clipped_plane(element,side,face,rect)['faces'][side]
    item['faces']=faces
    return item if faces else None

def derive(source,provenance,edge_faces):
    original_count=75;origins={r['outputElement']:r['sourceElement'] for r in provenance}
    if len(source['elements'])!=original_count+len(provenance):raise ValueError('Cap provenance does not cover source extensions')
    frame=copy.deepcopy(source);leaf=copy.deepcopy(source)
    for d in (frame,leaf):d.pop('groups',None);d['elements']=[]
    partitions=[]
    for index,element in enumerate(source['elements']):
        origin=index if index<original_count else origins[index]
        if origin not in MOVING:
            frame['elements'].append(copy.deepcopy(element));continue
        a,b=element['from'][0],element['to'][0]
        if abs(a-b)<cap.EPS:
            (leaf if LEFT-cap.EPS<=a<=RIGHT+cap.EPS else frame)['elements'].append(copy.deepcopy(element));continue
        for low,high in ((a,min(b,LEFT)),(max(a,RIGHT),b)):
            item=slice_x(element,low,high)
            if item is not None:frame['elements'].append(item)
        central=slice_x(element,max(a,LEFT),min(b,RIGHT))
        if central is not None:leaf['elements'].append(central)
        partitions.append({'sourceElement':index,'originalOrigin':origin,'sourceInterval':[a,b],
                           'movingInterval':[max(a,LEFT),min(b,RIGHT)]})
    if len(leaf['elements'])<2:raise ValueError('Both original wooden face layers must remain in the moving leaf')
    leaf['elements'][0]['faces'].update(copy.deepcopy(edge_faces))
    return frame,leaf,partitions

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--caps-plan',type=Path,default=cap.ROOT/'build/gallery-followups-v1274/surface-caps/audit-report.json')
    p.add_argument('--apply',action='store_true');p.add_argument('--expected-plan-sha256')
    p.add_argument('--output',type=Path,default=cap.ROOT/'build/gallery-followups-v1274/door-90986-caps')
    args=p.parse_args();out=args.output.resolve()
    if not out.is_relative_to((cap.ROOT/'build').resolve()):raise ValueError('Output must remain under project build/')
    report=json.loads(args.caps_plan.read_text(encoding='utf8'));source_plan=next(r for r in report['plans'] if r['path']==SOURCE)
    proposed=args.caps_plan.parent/'proposed'/SOURCE.split('/assets/',1)[1]
    raw=proposed.read_bytes();assert cap.digest(raw)==source_plan['outputSha256'];source=json.loads(raw)
    leaf_path=cap.REPO/LEAF;current_leaf=json.loads(leaf_path.read_bytes())
    edges={side:face for side,face in current_leaf['elements'][0]['faces'].items() if side not in ('north','south')}
    assert set(edges)=={'west','east','up','down'}
    frame,leaf,partitions=derive(source,source_plan['addedPlanesProvenance'],edges)
    plans=[]
    for path,doc in ((FRAME,frame),(LEAF,leaf)):
        current=(cap.REPO/path).read_bytes();newline='\r\n' if b'\r\n' in current else '\n'
        output=(json.dumps(doc,ensure_ascii=False,indent=2)+'\n').replace('\n',newline).encode('utf8')
        plans.append({'path':path,'inputSha256':cap.digest(current),'outputSha256':cap.digest(output),'output':output,'elements':len(doc['elements'])})
    public=[{k:v for k,v in r.items() if k!='output'} for r in plans];plan_sha=cap.digest(cap.canonical(public))
    if args.apply:
        if args.expected_plan_sha256!=plan_sha:raise ValueError('Exact reviewed plan required: '+plan_sha)
        if cap.digest((cap.REPO/SOURCE).read_bytes())!=source_plan['outputSha256']:raise ValueError('Apply reviewed source caps first; current source differs')
        for row in plans:
            if cap.digest((cap.REPO/row['path']).read_bytes())!=row['inputSha256']:raise ValueError('Derivative changed since plan')
        for row in plans:(cap.REPO/row['path']).write_bytes(row['output'])
    out.mkdir(parents=True,exist_ok=True)
    for row in plans:(out/Path(row['path']).name).write_bytes(row['output'])
    result={'status':'APPLIED_OPEN_DERIVATIVES_PENDING_NATIVE_CHECK' if args.apply else 'AUDIT_ONLY_NO_PRODUCTION_CHANGES',
            'planSha256':plan_sha,'sourceCapPlanSha256':report['planSha256'],'sourceExpectedSha256':source_plan['outputSha256'],
            'closedSourceReferenceSpecPhysicsAndPngChanged':False,'movingOriginalIndices':[0,3],
            'fixedRepairedCapsAndPlanesPreserved':True,'newLeafEdgesPreservedFromDoorAuthor':True,'partitions':partitions,'plans':public}
    (out/'report.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf8')

if __name__=='__main__':main()
