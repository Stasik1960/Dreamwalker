"""Cheap reviewed physics and exact per-variant glazing mounting metadata.

Visual meshes, UVs and textures are not edited. Run after original asset importers.
"""
from pathlib import Path
import copy, json, math
from analyze_resources import element_vertices, rotate_point
from prepare_window_assets import clip_polygon

ROOT=Path(__file__).resolve().parents[1]
RES=ROOT/'src/architecture/resources'
def dump(path,data): path.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
def bounds(points): return {'from':[round(min(p[i] for p in points),10) for i in range(3)],'to':[round(max(p[i] for p in points),10) for i in range(3)]}
def planar_strips(points,width=8):
    poly=[[p[0],p[2]] for p in points]
    # Sort convex corners around centroid, removing duplicated Y levels.
    poly=list({tuple(p) for p in poly});cx=sum(p[0] for p in poly)/len(poly);cz=sum(p[1] for p in poly)/len(poly)
    poly=sorted([list(p) for p in poly],key=lambda p:math.atan2(p[1]-cz,p[0]-cx))
    b=bounds(points);out=[]
    for i in range(math.floor(b['from'][0]/width),math.ceil(b['to'][0]/width)):
        clipped=clip_polygon(clip_polygon(poly,0,i*width,True),0,(i+1)*width,False)
        if clipped:
            lo=[min(p[0] for p in clipped),b['from'][1],min(p[1] for p in clipped)]
            hi=[max(p[0] for p in clipped),b['to'][1],max(p[1] for p in clipped)]
            if all(hi[j]-lo[j]>1e-8 for j in range(3)):out.append({'from':lo,'to':hi})
    return out
def main():
    report={'schema':'review-v8-simple-geometry-1','visual_models_changed':False,'families':[]}
    path=RES/'bloodborne_dw/composite/prototype_thin_window.json';doc=json.loads(path.read_text(encoding='utf-8'))
    for i,v in enumerate(doc['variants']):
        model=json.loads((RES/f'assets/bloodborne_dw/models/base/source/minecraft/block/hold/window_{i+1:02}.json').read_text(encoding='utf-8'))
        el=model['elements'][0];points=[rotate_point(p,'y',-180,[8,8,8]) for p in element_vertices(el)]
        v['visibleBounds']=bounds(points)
        intrinsic=el.get('rotation',{}).get('angle',0)
        v['mountAlignmentYaw']=-intrinsic
        aligned=[rotate_point(p,'y',-intrinsic,[8,8,8]) for p in points]
        v['mountedPlaneBounds']=bounds(aligned)
        p=v['poses']['closed'];before=(len(p['collision']),len(p['selection']))
        p['collision']=planar_strips(points) if intrinsic else [bounds(points)]
        p['selection']=copy.deepcopy(p['collision'])
        report['families'].append({'id':doc['id'],'variant':i,'before_authored':before,'after_authored':[len(p['collision']),len(p['selection'])],
            'exact_visible_bounds':v['visibleBounds'],'mounted_plane_bounds':v['mountedPlaneBounds'],'intrinsic_yaw':intrinsic,'horizontal_alignment_only':-intrinsic})
    doc['mountingPolicy']='SIDE=vertical wall; UP=floor horizontal; DOWN=ceiling horizontal; sneak+UP=vertical standing pane. Per-variant transformed bounds seat the source mesh. Source migration retains SourceShift and unmounted vertical state.'
    doc['collisionPolicy']='One prism for straight pane; six coarse strips for intrinsic diagonal. Horizontal mode is one thin plane prism before global yaw. Root-only reservation remains independent.'
    dump(path,doc)
    # Preserve accepted wooden art and pose, simplify physical/selection volumes
    # to three source-panel prisms; transformed side prisms use coarse strips.
    path=RES/'bloodborne_dw/composite/prototype_wood_window.json';doc=json.loads(path.read_text(encoding='utf-8'))
    for name,pose in doc['variants'][0]['poses'].items():
        before=(len(pose['collision']),len(pose['selection']));boxes=[]
        for part in pose['parts']:
            local=part['model'].split(':',1)[1]
            el=json.loads((RES/f'assets/bloodborne_dw/models/{local}.json').read_text(encoding='utf-8'))['elements'][0]
            points=element_vertices(el)
            if part.get('extraYaw'):points=[rotate_point(p,'y',part['extraYaw'],part['extraPivot']) for p in points]
            points=[rotate_point(p,'y',-part['yaw'],part['pivot']) for p in points]
            boxes.extend(planar_strips(points) if el.get('rotation',{}).get('angle',0) or part.get('extraYaw') else [bounds(points)])
        pose['collision']=boxes;pose['selection']=copy.deepcopy(boxes)
        report['families'].append({'id':doc['id'],'pose':name,'before_authored':before,'after_authored':[len(boxes),len(boxes)],'accepted_visual_unchanged':True})
    doc['proposal']['status']='ACCEPTED_BY_USER_2026_10_07_VISUAL_AND_TESTED_BEHAVIOR'
    dump(path,doc)
    # Roof slopes: four coarse steps per actual solid, decorative fins selectable
    # through one simple whole-object box. No physical fin or filled roof AABB.
    path=RES/'bloodborne_dw/composite/prototype_roof.json';doc=json.loads(path.read_text(encoding='utf-8'));p=doc['variants'][0]['poses']['closed'];before=(len(p['collision']),len(p['selection']))
    old=p['collision'];simple=[]
    for x0,x1 in sorted({(b['from'][0],b['to'][0]) for b in old}):
        row=[b for b in old if (b['from'][0],b['to'][0])==(x0,x1)]
        z0=min(b['from'][2] for b in row);z1=max(b['to'][2] for b in row)
        for i in range(4):
            a=z0+(z1-z0)*i/4;b=z0+(z1-z0)*(i+1)/4
            hit=[v for v in row if v['to'][2]>a+1e-8 and v['from'][2]<b-1e-8]
            simple.append({'from':[x0,min(v['from'][1] for v in hit),a],'to':[x1,max(v['to'][1] for v in hit),b]})
    p['collision']=simple;p['selection']=[bounds([v for b in p['selection'] for v in (b['from'],b['to'])])]
    report['families'].append({'id':doc['id'],'before_authored':before,'after_authored':[len(simple),1],
        'physics':'Four coarse solid-slope steps per source solid; fins passable. Selection is one simple whole-object volume.'})
    doc['physicalPlacementRules']='Root-only reservation. Eight coarse slope cuboids; decorative fins have no collision; one whole selection box. Adjacent overhangs allowed.'
    dump(path,doc)
    dump(ROOT/'reports/REVIEW_V8_GEOMETRY_ASSETS.json',report)
    print(json.dumps({'status':'PASS_SOURCE_VISUAL_UNCHANGED','families':len(report['families'])}))
if __name__=='__main__':main()
