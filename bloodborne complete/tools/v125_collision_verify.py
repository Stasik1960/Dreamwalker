"""Independent resource/domain/geometry preservation checks, no Minecraft launch."""
import hashlib
import itertools
import json
from pathlib import Path
import subprocess
import numpy as np
from gallery_platform_plan import ModelGeometry
from simplify_imported_collisions import rotate_clip_boxes
from v125_collision_geometry import box_solids, geometry_key, inside, oriented, solid_parts

ROOT=Path(__file__).resolve().parents[1]
RES=ROOT/'src/architecture/resources'
OUT=ROOT/'docs/v12.5-collisions'
BASE='653ae307ba4c44392ee08969f3890321e95659bb'
def read(path):return json.loads(path.read_text(encoding='utf8'))
def digest(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def samples(box):
    low,high=np.asarray(box['from']),np.asarray(box['to'])
    return np.asarray([low+(high-low)*np.asarray(p) for p in itertools.product((0.,.25,.5,.75,1.),repeat=3)])
def old_domains(pose,yaw):
    return pose['simpleCollisionByRotation'][yaw] if 'simpleCollisionByRotation' in pose else rotate_clip_boxes(pose['collision'],yaw,crop=False)
def in_domains(points,boxes):
    result=np.zeros(len(points),dtype=bool)
    for box in boxes:result|=np.all((points>=np.asarray(box['from'])-1e-6)&(points<=np.asarray(box['to'])+1e-6),axis=1)
    return result
def prior(path):
    return subprocess.check_output(['git','show',BASE+':bloodborne complete/'+path],cwd=ROOT).decode('utf8').replace('\r\n','\n')

def main():
    preview=ROOT/'build/v125-collision-preview.json';table=read(preview)
    report=read(OUT/'generation-report.json');cases=report['cases']
    assert digest(preview)==report['metadataSha256']
    cat=read(RES/'bloodborne_dw/remaining-catalogue.json');ids={'bloodborne_dw:'+row['path'] for row in cat['objects']}
    assert len(ids)==1045 and set(table['objects'])==ids and 'bloodborne_dw:source_ladder_backing' not in ids
    models=ModelGeometry();descriptors={};solids_cache={};domain_cache={};geometry_checked=set();failures=[];empty=[];counts={}
    native={row['primaryRegistryId'].split(':',1)[1] for row in read(ROOT/'docs/v12.4-dedup/stair-review/functional-type-fragment.json')['groups'] if row['kind']=='stairs'}
    functional={'source_hold_ladder_01','source_hold_wood_ladder_01','source_hold_ladder_02','source_hold_ladder_03','source_warped_gargoyle','source_acacia_fence_gate','source_deepslate_iron_ore','source_potted_flowering_azalea_bush'}
    for registry,variants in table['objects'].items():
        spec=read(RES/'bloodborne_dw/composite'/ (registry.split(':',1)[1]+'.json'));assert spec['id']==registry and spec['simplePhysicalCollision'];assert len(spec['variants'])==len(variants);descriptors[registry]=spec
    for case in cases:
        registry,index,yaw=case['registryId'],case['variant'],case['rotation'];spec=descriptors[registry];poses=spec['variants'][index]['poses'];pose=poses.get(case['pose'],poses['closed'])
        selector=case['pose']+case['profile'].title();row=table['shapes'][table['objects'][registry][index][selector]][yaw];boxes=row['collision'];assert len(boxes)<=12 and len(row['aim'])<=1
        assert all(np.all(np.asarray(box['to'])>box['from']) and np.all(np.isfinite(box['from']+box['to'])) for box in boxes+row['aim'])
        path=registry.split(':',1)[1]
        if not pose['collision']:
            assert not boxes;counts['preservedPassable']=counts.get('preservedPassable',0)+1;continue
        if not boxes:empty.append({key:case[key] for key in ('registryId','variant','pose','profile','rotation','policy')});continue
        domain_key=(registry,index,case['pose'],yaw)
        if domain_key not in domain_cache:domain_cache[domain_key]=old_domains(pose,yaw)
        domains=domain_cache[domain_key]
        signature=(json.dumps(boxes,sort_keys=True),json.dumps(domains,sort_keys=True))
        if path!='source_allium' and signature not in geometry_checked:
            geometry_checked.add(signature)
            for box in boxes:
                if not np.all(in_domains(samples(box),domains)):failures.append({'kind':'OUTSIDE_PRIOR_PHYSICAL_DOMAIN','case':case});break
        if (path in native or path in functional) and yaw%2==0:
            assert boxes==domains;counts['functionalCardinalExact']=counts.get('functionalCardinalExact',0)+1
        if case['policy'] in ('MAJOR_CONCAVE_SEGMENTS','INSCRIBED_PLATFORM_OR_ROTATED_SOLID','NATIVE_RESCALE_PANEL_SOURCE_SUPPORTS'):
            source_key=(registry,index,case['pose'],case['profile'],yaw)
            if source_key not in solids_cache:solids_cache[source_key]=oriented(solid_parts(pose,models,case['profile'],panel_tolerance=1.),yaw)
            source=solids_cache[source_key];signature=('SOURCE',geometry_key(source),json.dumps(boxes,sort_keys=True))
            if signature not in geometry_checked:
                geometry_checked.add(signature)
                for box in boxes:
                    points=samples(box)
                    if not np.all(inside(points,source)):
                        failures.append({'kind':'FITTED_BOX_SAMPLE_OUTSIDE_SOURCE_UNION','case':case,'outsideSamples':int(np.count_nonzero(~inside(points,source)))});break
    # The NEW gate consists of precisely three added top guards. Legacy routes
    # below them must remain exact baseline source, including protected V12.
    hooks={
      'src/architecture/java/dev/dreamwalker/bloodbornedw/composite/CompositeSpec.java':[
        '        if(ImportedCollisionV125.supports(this))return ImportedCollisionV125.footprint(this,state,mountY,0,0,0);\n',
        '        if(ImportedCollisionV125.supports(this))return ImportedCollisionV125.physicalFootprint(this,state,mountY,shiftX,shiftY,shiftZ);\n'],
      'src/architecture/java/dev/dreamwalker/bloodbornedw/composite/CompositeSourceShift.java':[
        '        if(ImportedCollisionV125.supports(spec)){Shift shift=world(state,payload);double offset=payload.getDouble("MountY")+payload.getDouble(dev.dreamwalker.bloodbornedw.architecture.mount.VerticalMount.KEY);return ImportedCollisionV125.footprint(spec,state,offset,shift.x,shift.y,shift.z);}\n']}
    for path,guards in hooks.items():
        actual=(ROOT/path).read_text(encoding='utf8').replace('\r\n','\n')
        for guard in guards:assert actual.count(guard)==1;actual=actual.replace(guard,'')
        assert actual==prior(path),path+' changed outside exact NEW guards'
    changed=subprocess.check_output(['git','diff','--name-only',BASE,'--','src/architecture/resources/assets/bloodborne_dw/models/remaining','src/architecture/resources/bloodborne_dw/imported-stair-legacy-art.json'],cwd=ROOT,text=True).strip()
    assert not changed,'Source model/313 legacy-art data changed: '+changed
    result={'schemaVersion':1,'status':'PASS_OFFLINE_RESOURCE_AND_GEOMETRY' if not failures and not empty else 'FAIL_OFFLINE_RESOURCE_AND_GEOMETRY',
      'baselineCommit':BASE,'registeredNewTypes':1045,'workingNewTypes':551,'poseVariants':sum(map(len,table['objects'].values())),
      'selectorsChecked':len(cases),'metadataSha256':digest(preview),'resourceBytes':preview.stat().st_size,'tables':len(table['shapes']),
      'samplePattern':'5x5x5 points per unique fitted cuboid/domain; offline fitting also checks each analysis-cell corner and centre',
      'counts':counts,'emptyFormerlySolidOrientations':empty,'failures':failures,
      'originalV12LegacySourceBelowThreeGuards':'BYTE_EQUAL_AFTER_REMOVING_EXACT_NEW_GUARDS','sourceModelsAnd313LegacyArt':'UNCHANGED',
      'nativeRescaleRule':'abs_angle_22.5_uses_22.5_factor_all_other_angles_including_zero_use_45_factor',
      'priorDomainExceptions':['source_allium: old carrier AABB differs from native rescaled visible panels; up to3 source-contained support boxes'],
      'runtimeMemoryBounds':{'metadataLoadedOnce':True,'cacheKeys':4096,'cachedCells':65536,'cachedBoxReferences':262144,'runtimeRaster':False,'cacheWorldReferences':False},
      'authorCollisionExport':'NOT_SUPPLIED_NOT_APPLIED','nativeGameTests':'NOT_RUN','clientChecks':'NOT_RUN'}
    (OUT/'offline-proof.json').write_bytes((json.dumps(result,ensure_ascii=False,indent=2)+'\n').encode('utf8'))
    print(json.dumps({key:value for key,value in result.items() if key not in ('failures','emptyFormerlySolidOrientations')}));print(json.dumps({'emptyFormerlySolidOrientations':len(empty),'failures':len(failures)}))
    assert not failures and not empty

if __name__=='__main__':main()
