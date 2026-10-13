"""Light, read-only resource proof and native fixtures for all NEW stair states.

The proof does not replace native BakedModel captures or gameplay. Source
models/textures and the pre-fix production archive remain read-only.
"""
from __future__ import annotations
import argparse
import hashlib
import itertools
import json
from pathlib import Path
import zipfile
from build_v124_stair_types import GROUPS, SHAPES, occupancy, rotate_cells, pose_occupancy
from gallery_platform_plan import ModelGeometry
from inspect_imported_collision_semantics import element_boxes

ROOT=Path(__file__).resolve().parents[1]
RES=ROOT/'src/architecture/resources'
DOC=ROOT/'docs/v12.4-dedup/stair-review'
SIDES={'down':[0,4,5,1],'up':[3,7,6,2],'north':[4,0,2,6],
       'south':[1,5,7,3],'west':[0,1,3,2],'east':[5,4,6,7]}

def load(path):return json.loads(path.read_text(encoding='utf-8-sig'))
def write(path,value):
    path.parent.mkdir(parents=True,exist_ok=True)
    data=(json.dumps(value,ensure_ascii=False,indent=2)+'\n').encode('utf8')
    if path.is_file() and path.read_bytes()==data:return
    temporary=path.with_name(path.name+'.v124.tmp');temporary.write_bytes(data);temporary.replace(path)

def exterior_missing(pose,models,cells):
    planes=[]
    for part in pose['parts']:
        if part['model'].endswith('/original_trim'):continue
        boxes=element_boxes({'parts':[part]},models,include_points=True)
        for element,box in zip(models.elements(part['model']),boxes):
            for side in element.get('faces',{}):
                points=[box['points'][i] for i in SIDES[side]]
                low=[min(p[i] for p in points) for i in range(3)]
                high=[max(p[i] for p in points) for i in range(3)]
                axes=[i for i in range(3) if high[i]-low[i]<1e-6]
                if len(axes)==1:planes.append((axes[0],low,high))
    missing=[]
    for cell in sorted(cells):
        for axis,direction in itertools.product(range(3),(-1,1)):
            neighbor=list(cell);neighbor[axis]+=direction
            if tuple(neighbor) in cells:continue
            center=[4+8*c for c in cell];center[axis]+=4*direction
            if not any(a==axis and all(low[i]-1e-6<=center[i]<=high[i]+1e-6 for i in range(3))
                       for a,low,high in planes):
                missing.append({'cell':list(cell),'axis':axis,'direction':direction})
    return missing

def fixtures(originals,models):
    result=[]
    for group in GROUPS:
        primary=group[0];registry=originals[primary]['id']
        for pose in range(20 if primary=='93028' else 10):
            variant=pose%10;half='bottom' if variant<5 else 'top';shape=SHAPES[variant%5]
            expected=occupancy(half,shape);trim=pose>=10
            members=['90980'] if trim else [number for number in group if number!='90980']
            candidates=[]
            for number in members:
                for index,row in enumerate(originals[number]['variants']):
                    value,cells=pose_occupancy(row['poses']['closed'],models)
                    if not value:
                        # The trimmed original has four small authored extras.
                        if number=='90980':cells=occupancy('bottom','straight');value={'half':'bottom'}
                        else:continue
                    for turns in range(4):
                        actual=rotate_cells(cells,turns)
                        candidates.append((len(actual^expected),value['half']!=half,number!=primary,turns,number,index))
            distance,_,_,turns,old_number,old_index=min(candidates)
            properties={'connections':'manual'}
            if primary=='93028':properties['trim']='true' if trim else 'false'
            result.append({'kind':'FUNCTIONAL_NEW_STATE','reviewIndex':len(result),
                'groupId':primary,'oldId':old_number,'oldRegistryId':originals[old_number]['id'],
                'oldVariant':old_index,'oldRotation':turns*2,
                'mainId':primary,'mainRegistryId':registry,'variant':variant,
                'yawDeltaSteps':(-turns*2)&7,'sourceShiftDelta':[0,0,0],
                'properties':properties,'canonicalRenderPose':pose,
                'semanticHalf':half,'semanticShape':shape,
                'sameMaterialSource':True,'sameNormalizedPlacement':True,
                'intentionalShapeDifference':distance!=0,
                'expectedComparison':'Same authored material/atlas and complete render planes; half/angle silhouette differences are intentional' if distance else 'Same authored material and matching normalized silhouette',
                'nativeQaPriority':primary in ('93004','93028') or shape.startswith('inner'),
                'mustBindSavedMigrationFixture':False})
    return {'schemaVersion':1,'status':'PENDING_NATIVE_FUNCTIONAL_STAIR_CAPTURE',
        'scope':'All 19 NEW native stair types x ten functional states plus ten trim states of93028; every native after model must exist. Frozen before is the closest same-material source pose, not proof that a new half/angle is a clone.',
        'functionalStateCount':len(result),'nativeTypeCount':len(GROUPS),
        'fixtures':result}

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--production-jar',type=Path,required=True,help='Exact pre-fix build2 archive for physics/source comparisons')
    parser.add_argument('--output',type=Path,required=True)
    args=parser.parse_args()
    originals=load(DOC/'original-functional-descriptors.json')
    models=ModelGeometry(vanilla_jar=Path('C:/Users/vakir/.gradle/caches/fabric-loom/1.20.1/minecraft-client.jar'))
    rows=[];failures=[];changed_models=set();source_omissions=[]
    with zipfile.ZipFile(args.production_jar) as archive:
        old_manifest=json.loads(archive.read('bloodborne_dw/imported-type-aliases.json'))
        old_mappings={(m['oldRegistryId'],m['oldVariant']):m for m in old_manifest['mappings']}
        new_fragment=load(DOC/'functional-type-fragment.json')
        for mapping in new_fragment['mappings']:
            old=old_mappings.get((mapping['oldRegistryId'],mapping['oldVariant']))
            if old is None or any(old.get(k)!=v for k,v in mapping.items()):failures.append({'reason':'Logical mapping changed','mapping':mapping})
        protected=[name for name in archive.namelist() if not name.endswith('/') and
                   (name.startswith('assets/bloodborne_dw/models/remaining/source/')
                   or name.startswith('assets/bloodborne_dw/models/remaining/source_uvlock/')
                   or name.startswith('assets/bloodborne_dw/textures/block/remaining/source/'))]
        for name in protected:
            path=RES/name
            if not path.is_file() or path.read_bytes()!=archive.read(name):failures.append({'reason':'Frozen source model/PNG changed','path':name})
        for group in GROUPS:
            primary=group[0];path=originals[primary]['id'].split(':',1)[1]
            current=load(RES/'bloodborne_dw/composite'/f'{path}.json')
            before=json.loads(archive.read('bloodborne_dw/composite/'+path+'.json'))
            if len(current['variants'])!=len(before['variants']):failures.append({'reason':'Pose count changed','id':primary})
            for index,(row,old) in enumerate(zip(current['variants'],before['variants'])):
                pose=row['poses']['closed'];previous=old['poses']['closed']
                physical_same=all(pose.get(key)==previous.get(key) for key in ('collision','selection','simpleCollisionByRotation'))
                if not physical_same:failures.append({'reason':'Physical/outline descriptor changed','id':primary,'variant':index})
                if pose['collision']!=pose['selection'] or not 2<=len(pose['collision'])<=3 or any(len(rotation)>3 for rotation in pose['simpleCollisionByRotation']):failures.append({'reason':'Simplified collision/outline policy differs','id':primary,'variant':index})
                generated=any(part['model'].startswith('bloodborne_dw:block/imported_stairs/') and not part['model'].endswith('/original_trim') for part in pose['parts'])
                cells=occupancy(row['functionalHalf'],row['functionalShape'])
                missing=exterior_missing(pose,models,cells)
                if generated and missing:failures.append({'reason':'Generated body exterior face missing','id':primary,'variant':index,'missing':missing})
                if not generated and missing:source_omissions.append({'id':primary,'variant':index,'missingSourceAuthoredFaces':missing,'policy':'Original source faces retained; generated missing forms receive authored opposite-face material'})
                for part in pose['parts']:
                    if not part['model'].startswith('bloodborne_dw:block/imported_stairs/'):continue
                    name='assets/bloodborne_dw/models/'+part['model'].split(':',1)[1]+'.json'
                    if (RES/name).read_bytes()!=archive.read(name):changed_models.add(name)
                rows.append({'id':primary,'renderPose':index,'worldVariant':index%10,'trim':index>=10,
                    'generatedBody':generated,'exteriorBodyFaces':'PASS' if not missing else 'SOURCE_AUTHORED_OMISSIONS_RETAINED',
                    'collisionAndOutlineUnchangedFromBuild2':physical_same,'physicalBoxes':len(pose['collision'])})
    fixture_document=fixtures(originals,models)
    assert fixture_document['functionalStateCount']==200
    write(ROOT/'docs/v12.4-dedup/additional-functional-stair-review-fixtures.json',fixture_document)
    report={'schema':'dreamwalker-v124-stair-render-resource-proof','status':'PASS' if not failures else 'FAIL',
        'preFixProductionSha256':hashlib.sha256(args.production_jar.read_bytes()).hexdigest(),
        'nativeStates':len(rows),'generatedBodyStates':sum(row['generatedBody'] for row in rows),
        'changedGeneratedModelFiles':sorted(changed_models),'changedGeneratedMaterialIds':sorted({name.split('/')[-2] for name in changed_models}),
        'frozenSourceModelAndPngFilesByteUnchanged':len(protected),'logicalAliasMappingsUnchanged':len(new_fragment['mappings']),
        'physicalCollisionAndOutlineDescriptorStatesUnchanged':sum(row['collisionAndOutlineUnchangedFromBuild2'] for row in rows),
        'nativeFunctionalCaptureFixtures':fixture_document['functionalStateCount'],
        'sourceAuthoredFaceOmissionsRetained':source_omissions,'states':rows,'failures':failures,
        'gameplay':'NOT_RUN_BY_THIS_RESOURCE_AUDIT','nativeVisualVerification':'PENDING_FINAL_CLIENT_CAPTURE'}
    write(args.output,report)
    print(json.dumps({key:report[key] for key in ('status','nativeStates','generatedBodyStates','changedGeneratedMaterialIds','frozenSourceModelAndPngFilesByteUnchanged','logicalAliasMappingsUnchanged','physicalCollisionAndOutlineDescriptorStatesUnchanged','nativeFunctionalCaptureFixtures')},ensure_ascii=False))
    if failures:raise SystemExit(1)

if __name__=='__main__':main()
