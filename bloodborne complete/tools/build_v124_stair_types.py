"""Create functional states from actual imported tread geometry, restricted to NEW IDs.

The original descriptor snapshot makes repeated runs deterministic. This script
does not edit the shared working catalogue, original V12 resources or any world.
"""
from __future__ import annotations
import argparse
import copy
import hashlib
import itertools
import json
import math
import re
from pathlib import Path
from gallery_platform_plan import ModelGeometry, rotate
from inspect_imported_collision_semantics import element_boxes
from import_remaining_assets import implicit_uv
from simplify_imported_collisions import rotate_clip_boxes, exact_coalesce
from v124_stair_review import native_stair_shape

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT/'src/architecture/resources'
OUT = ROOT/'docs/v12.4-dedup/stair-review'
GROUPS = [
 ['90275'], ['90276'], ['90322','90323','90324'], ['90409'],
 ['90423','90424','90425','90426','90427','90428','90429','90430'],
 ['90437','90438','90439'], ['90493','90494','90495'],
 ['90523','90524','90525'], ['90550','90551','90552'], ['90686'],
 ['90692','90693','90694'], ['90701'], ['90760','90761','90762'],
 ['90804','90805','90806'], ['90862'], ['93004'], ['93028','93029','90980'],
 ['93081','93082','93083'], ['93094','93095','93096']]
SHAPES = ['straight','inner_left','inner_right','outer_left','outer_right']
FACING = {'east':0,'south':2,'west':4,'north':6}
MATERIALS = {'90275':'Бамбуковая мозаичная лестница','90276':'Бамбуковая лестница',
 '90322':'Кирпичная лестница','90409':'Вишнёвая лестница',
 '90423':'Каменная лестница из глубинного сланца','90437':'Булыжная лестница',
 '90493':'Лестница из тёмного узорного кирпича','90523':'Лестница из светлого полосатого камня',
 '90550':'Лестница из серого мелкого кирпича','90686':'Мангровая лестница',
 '90692':'Лестница из коричневого камня','90701':'Лестница из коричневого кирпича',
 '90760':'Лестница из серого тесаного камня','90804':'Лестница из тёмного зеленоватого камня',
 '90862':'Лестница из полосатого коричневого камня',
 '93004':'Лестница из камня с зелёными швами','93028':'Лестница из светлого серого камня',
 '93081':'Лестница из широкого серого кирпича','93094':'Гладкая каменная лестница'}

def load(p): return json.loads(p.read_text(encoding='utf-8-sig'))
def write(p,value):
    p.parent.mkdir(parents=True,exist_ok=True)
    p.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n',encoding='utf-8',newline='\n')
def occupancy(half,shape):
    broad=0 if half=='bottom' else 1; small=1-broad
    result={(x,broad,z) for x in range(2) for z in range(2)}
    if shape=='straight':result|={(1,small,z) for z in range(2)}
    elif shape.startswith('inner'):result|={(1,small,z) for z in range(2)}|{(0,small,0 if shape.endswith('left') else 1)}
    else:result.add((1,small,0 if shape.endswith('left') else 1))
    return result
def rotate_cells(values,turns):
    for _ in range(turns%4):values={(1-z,y,x) for x,y,z in values}
    return values
def pose_occupancy(pose,models):
    elements=element_boxes(pose,models,include_points=True)
    value=native_stair_shape(elements)
    return value, set(tuple(c) for c in value['occupancy']) if value else set()
def boxes_of_cells(cells):
    return exact_coalesce([{'from':[8*c for c in cell],'to':[8*(c+1) for c in cell]} for cell in cells])
def geometry_policy(pose,models):
    # The two/three useful tread boxes are enough for ascent and platform support.
    _,cells=pose_occupancy(pose,models)
    if not cells:raise ValueError('Generated functional stair is not a native tread shape')
    boxes=boxes_of_cells(cells)
    if not 2<=len(boxes)<=3:raise ValueError('Native stair exceeds simple cuboid budget')
    pose['collision']=boxes;pose['selection']=copy.deepcopy(boxes)
    pose['simpleCollisionByRotation']=[rotate_clip_boxes(boxes,r,crop=False) for r in range(8)]
    return pose
def rotated_pose(pose,turns):
    result=copy.deepcopy(pose)
    for part in result['parts']:
        part['yaw']=part.get('yaw',0)+90*turns
        offset=part.get('offset',[0,0,0])
        angle=math.radians(90*turns);c,s=round(math.cos(angle)),round(math.sin(angle))
        part['offset']=[c*offset[0]-s*offset[2],offset[1],s*offset[0]+c*offset[2]]
    return result
def inherited(models,identifier):
    doc=models.document(identifier)
    parent=inherited(models,doc['parent']) if doc.get('parent') and 'builtin/' not in doc['parent'] else {}
    return {**parent,**doc,'textures':{**parent.get('textures',{}),**doc.get('textures',{})}}
def clip_element(template,box):
    e=copy.deepcopy(template);oldfrom=e['from'];oldto=e['to'];e['from']=box['from'];e['to']=box['to'];e.pop('rotation',None)
    for side,face in e['faces'].items():
        uv=face.get('uv',implicit_uv(template,side))
        # Retain the actual authored face texture/UV frame and crop its projected
        # extent when a native tread is subdivided; no new texture is painted.
        axes={'up':(0,2),'down':(0,2),'north':(0,1),'south':(0,1),'west':(2,1),'east':(2,1)}[side]
        out=[]
        for d in (0,1):
            axis=axes[d];length=oldto[axis]-oldfrom[axis]
            begin=(box['from'][axis]-oldfrom[axis])/length if length else 0
            end=(box['to'][axis]-oldfrom[axis])/length if length else 1
            out.extend([uv[d]+(uv[d+2]-uv[d])*begin,uv[d]+(uv[d+2]-uv[d])*end])
        face['uv']=[out[0],out[2],out[1],out[3]]
    return e
def generated_pose(primary,half,shape,donor,models,apply):
    # A source-native straight/inner shape supplies the broad tread and raised
    # tread face templates. New connections reuse those templates and textures.
    part=donor['parts'][0]
    doc=inherited(models,part['model'])
    # Use a donor in its original east-facing, bottom pose. Native-only groups
    # have such a source pose; inherited standard templates are already baked.
    elements=copy.deepcopy(models.elements(part['model']))
    base=next((e for e in elements if e['from']==[0,0,0] and e['to']==[16,8,16]),None)
    raised=next((e for e in elements if e['from']==[8,8,0] and e['to']==[16,16,16]),None)
    if base is None or raised is None:raise ValueError('No native east-facing tread template for '+primary)
    cells=occupancy('bottom',shape);boxes=boxes_of_cells(cells)
    made=[]
    for box in boxes:
        if box['from'][1]==0 and box['to'][1]==8:made.append(clip_element(base,box))
        else:made.append(clip_element(raised,box))
    identifier=f'bloodborne_dw:block/imported_stairs/{primary}/{shape}'
    model={'credit':'Source-native tread templates retained for functional connection states',
        'textures':doc['textures'],'elements':made}
    if 'display' in doc:model['display']=doc['display']
    if apply:write(RES/f'assets/bloodborne_dw/models/block/imported_stairs/{primary}/{shape}.json',model)
    else:write(OUT/f'generated-models/{primary}/{shape}.json',model)
    # Make the generated model visible to the geometry resolver in review mode.
    models.document.cache_clear();models.elements.cache_clear();models.points.cache_clear()
    if not apply:
        models.roots=(OUT/'generated-resources',)+models.roots
        write(OUT/f'generated-resources/assets/bloodborne_dw/models/block/imported_stairs/{primary}/{shape}.json',model)
    pose={'parts':[{'model':identifier,'offset':[0,0,0]}]}
    if half=='top':pose['parts'][0]['pitch']=180
    # The pitch reverses north/south; mirror the corner shape about its facing.
    _,actual=pose_occupancy(pose,models)
    expected=occupancy(half,shape)
    for turns in range(4):
        if rotate_cells(actual,turns)==expected:return rotated_pose(pose,turns)
    raise ValueError('Generated geometry cannot represent '+half+' '+shape)

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--apply',action='store_true');args=parser.parse_args()
    OUT.mkdir(parents=True,exist_ok=True)
    cat=load(RES/'bloodborne_dw/debug_catalogue.json'); bynumber={row['temporaryId']:row['registryId'] for row in cat['entries']}
    membernums={num for group in GROUPS for num in group}|{'90586','90587'}
    snapshot=OUT/'original-functional-descriptors.json'
    if not snapshot.is_file():write(snapshot,{number:load(RES/'bloodborne_dw/composite'/f'{bynumber[number].split(":",1)[1]}.json') for number in sorted(membernums)})
    originals=load(snapshot)
    models=ModelGeometry(vanilla_jar=Path('C:/Users/vakir/.gradle/caches/fabric-loom/1.20.1/minecraft-client.jar'))
    fragment={'schemaVersion':1,'groups':[],'mappings':[],'catalogueObjects':[]};types={'schemaVersion':1,'stairTypes':[],'ladderTypes':[]};evidence=[]
    for group in GROUPS:
        primary=group[0];spec=copy.deepcopy(originals[primary]);path=spec['id'].split(':',1)[1]
        donors=[(number,index,var) for number in group if number!='90980' for index,var in enumerate(originals[number]['variants'])]
        eastbottom=next((var['poses']['closed'] for _,_,var in donors if pose_occupancy(var['poses']['closed'],models)[1]==occupancy('bottom','straight')),None)
        if eastbottom is None:eastbottom=next((var['poses']['closed'] for _,_,var in donors if pose_occupancy(var['poses']['closed'],models)[1]==occupancy('bottom','inner_right')),None)
        if eastbottom is None:raise ValueError('No authored east/bottom core '+primary)
        newvariants=[];donorrows=[]
        for half,shape in itertools.product(['bottom','top'],SHAPES):
            expected=occupancy(half,shape);pose=None;selected=None
            for number,index,var in donors:
                value,cells=pose_occupancy(var['poses']['closed'],models)
                if not value or value['half']!=half or value['shape']!=shape.split('_',1)[0]:continue
                for turns in range(4):
                    if rotate_cells(cells,turns)==expected:
                        pose=rotated_pose(var['poses']['closed'],turns);selected={'number':number,'oldVariant':index,'extraQuarterTurns':turns};break
                if pose:break
            if pose is None:pose=generated_pose(primary,half,shape,eastbottom,models,args.apply);selected={'generatedFromAuthoredNativeTreadTemplates':True}
            pose=geometry_policy(pose,models)
            newvariants.append({'poses':{'closed':pose},'visibleBounds':{'from':[0,0,0],'to':[16,16,16]},'functionalHalf':half,'functionalShape':shape})
            donorrows.append(selected)
        if primary=='93028':
            olddecor=originals['90980']['variants'][0]['poses']['closed']['parts'][0]
            doc=inherited(models,olddecor['model'])
            model={'credit':'The original90980 small rear trim remains an appearance state', 'textures':doc['textures'],'elements':copy.deepcopy(models.elements(olddecor['model'])[2:])}
            extraid='bloodborne_dw:block/imported_stairs/93028/original_trim'
            if args.apply:write(RES/'assets/bloodborne_dw/models/block/imported_stairs/93028/original_trim.json',model)
            else:
                write(OUT/'generated-resources/assets/bloodborne_dw/models/block/imported_stairs/93028/original_trim.json',model)
                models.roots=(OUT/'generated-resources',)+models.roots
            styles=[]
            for row in newvariants:
                decorated=copy.deepcopy(row);half=row['functionalHalf']
                part=copy.deepcopy(olddecor);part['model']=extraid
                if half=='top':part['pitch']=part.get('pitch',0)+180
                decorated['poses']['closed']['parts'].append(part)
                decorated['functionalTrim']=True
                decorated['visibleBounds']={'from':[-3,0,0],'to':[16,19,20]}
                styles.append(decorated)
            newvariants+=styles
        spec['variants']=newvariants;spec['displayName']=MATERIALS[primary];spec['essentialMask']=[[0,0,0]]
        spec['functionalStairStates']='rotation maps east/south/west/north and retained legacy45 poses; VARIANT0..4 encodes bottom straight/inner_left/inner_right/outer_left/outer_right, VARIANT5..9 top; neighbours choose native corners via atomic owner transitions'
        spec['simplePhysicalCollision']=True
        if args.apply:write(RES/'bloodborne_dw/composite'/f'{path}.json',spec)
        else:write(OUT/'generated-descriptors'/f'{path}.json',spec)
        members=[originals[number]['id'] for number in group]
        fragment['groups'].append({'primaryId':primary,'primaryRegistryId':spec['id'],'kind':'stairs','oneItem':True,'members':members})
        types['stairTypes'].append({'path':path,'id':primary,'name':spec['displayName']})
        fragment['catalogueObjects'].append({'path':path,'displayName':spec['displayName'],'variants':[{'variant':i,'name':spec['displayName']+(' · с накладками' if i>=10 else ''),'sourceModels':[part['model'] for part in row['poses']['closed']['parts']]} for i,row in enumerate(newvariants)]})
        for number,index,var in donors:
            oldshape,cells=pose_occupancy(var['poses']['closed'],models)
            if not oldshape:raise ValueError('A native group contains non-native geometry '+number)
            candidates=[]
            for newindex,newvar in enumerate(newvariants[:10]):
                half,shape=newvar['functionalHalf'],newvar['functionalShape']
                for turns in range(4):
                    if rotate_cells(occupancy(half,shape),turns)==cells:
                        candidates.append((newindex,turns,half,shape))
            if not candidates:raise ValueError('No visual-preserving state mapping '+number+' '+str(index))
            # A concrete source ref is only preferred when its real geometry agrees.
            ranked=[]
            for candidate in candidates:
                newindex,turns,half,shape=candidate
                refmatch=any(f'facing={facing},half={half},shape={shape}' in ref and FACING[facing]==turns*2 for facing in FACING for ref in var.get('sourceStateRefs',[]))
                ranked.append((not refmatch,turns,newindex,candidate))
            _,_,_,(newindex,turns,half,shape)=min(ranked)
            fragment['mappings'].append({'oldId':number,'oldRegistryId':originals[number]['id'],'oldVariant':index,
                'newId':primary,'newRegistryId':spec['id'],'newVariant':newindex,'rotationDelta':turns*2,
                'sourceShift':[0,0,0],'properties':({'trim':'false','connections':'manual'} if primary=='93028' else {'connections':'manual'})})
        if primary=='93028':
            fragment['mappings'].append({'oldId':'90980','oldRegistryId':originals['90980']['id'],'oldVariant':0,
                'newId':primary,'newRegistryId':spec['id'],'newVariant':0,'rotationDelta':0,'sourceShift':[0,0,0],
                'properties':{'trim':'true','connections':'manual'}})
        evidence.append({'primaryId':primary,'members':group,'canonicalPoseDonors':donorrows,'nativeGeometryMatch':'PASS','physicalBoxesPerPose':[len(v['poses']['closed']['collision']) for v in newvariants]})
    ladder=copy.deepcopy(originals['90586']);ladder['displayName']='Приставная каменная лестница';ladder['variants']=[copy.deepcopy(originals[n]['variants'][0]) for n in ['90586','90587']]
    ladder['functionalLadderHeightStates']='one: original16unit model/climbing zone; two: original32unit model/climbing zone; all8yaw and previous VerticalMount/SourceShift remain available'
    ladderpath=ladder['id'].split(':',1)[1]
    if args.apply:write(RES/'bloodborne_dw/composite'/f'{ladderpath}.json',ladder)
    else:write(OUT/'generated-descriptors'/f'{ladderpath}.json',ladder)
    types['ladderTypes'].append({'path':ladderpath,'id':'90586','name':ladder['displayName']})
    fragment['catalogueObjects'].append({'path':ladderpath,'displayName':ladder['displayName'],'variants':[{'variant':i,'name':ladder['displayName']+(' · высота2блока' if i==1 else ' · высота1блок'),'sourceModels':[part['model'] for part in row['poses']['closed']['parts']]} for i,row in enumerate(ladder['variants'])]})
    fragment['groups'].append({'primaryId':'90586','primaryRegistryId':ladder['id'],'kind':'ladder','oneItem':True,'members':[originals[n]['id'] for n in ['90586','90587']]})
    for index,number in enumerate(['90586','90587']):fragment['mappings'].append({'oldId':number,'oldRegistryId':originals[number]['id'],'oldVariant':0,'newId':'90586','newRegistryId':ladder['id'],'newVariant':index,'rotationDelta':0,'sourceShift':[0,0,0],'properties':{'height':'one' if index==0 else 'two'}})
    write(OUT/'functional-type-fragment.json',fragment);write(OUT/'functional-state-generation.json',{'status':'PASS','applied':args.apply,'groups':evidence,'ladderOriginalGeometryRetained':True,'workingNativeStairTypes':len(GROUPS),'workingVerticalLadderTypes':1,'mappingCount':len(fragment['mappings'])})
    if args.apply:write(RES/'bloodborne_dw/imported-stair-types.json',types)
    else:write(OUT/'imported-stair-types.json',types)

if __name__=='__main__':
    try:main()
    except BaseException:
        import traceback
        OUT.mkdir(parents=True,exist_ok=True);(OUT/'generation-error.txt').write_text(traceback.format_exc(),encoding='utf-8',newline='\n');raise
