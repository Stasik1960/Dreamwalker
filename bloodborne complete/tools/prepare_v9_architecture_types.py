"""Bounded V9 type split. Preserve V8 source geometry/UV and append TEMP numbers."""
import copy
import hashlib
import json
from pathlib import Path
import zipfile

ROOT=Path(__file__).resolve().parents[1]
RES=ROOT/'src/architecture/resources'
V8=ROOT/'build/delivery/v8/dreamwalker-bb-fabric-1.20.1-review-v8-full-source.zip'
PREFIX='dreamwalker-bb-fabric-1.20.1-full-source-review-v8/'
WALL_MATERIALS=[0,1,2,3,4,5,7]
RP_CANONICAL={'furniture_8':'furniture_1','furniture_3':'furniture_10','furniture_4':'furniture_11',
              'furniture_5':'furniture_12','furniture_6':'furniture_13','furniture_7':'furniture_14','furniture_9':'furniture_2'}

def sha(data): return hashlib.sha256(data).hexdigest()
def dump(path,value):
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n',encoding='utf8')

def main():
    with zipfile.ZipFile(V8) as z:
        original=json.loads(z.read(PREFIX+'src/architecture/resources/bloodborne_dw/composite/prototype_thin_window.json'))
        old_catalogue=json.loads(z.read(PREFIX+'src/architecture/resources/bloodborne_dw/debug_catalogue.json'))
    # The legacy three-form descriptor remains readable for old world data.
    legacy=RES/'bloodborne_dw/composite/prototype_thin_window.json'
    assert json.loads(legacy.read_text(encoding='utf8'))==original
    window=copy.deepcopy(original);window['id']='bloodborne_dw:prototype_glass_window_02'
    window['displayName']='Тонкое остекление · window02';window['variants']=[copy.deepcopy(original['variants'][1])]
    window['globalOrientations']=4;window['orientationStepDegrees']=90
    window['variantMeaning']='One independent window02 drawing. BASE/ALT, cardinal yaw and three mounts are states; legacy window03 is not an ordinary form.'
    window['mountingPolicy']='V9 ordinary cardinal poses, vertical/floor/ceiling; initial actual surface seating, then independent persistence without required support.'
    window['proposal']={'status':'V9_IMPLEMENTATION_AWAITING_RUNTIME_AND_USER_REVIEW','motion':'FIXED_GLAZING','mountingPolicy':window['mountingPolicy']}
    window['source']={'model':'minecraft:block/hold/window_02','numericIdsFrozen':False,'legacySourceDescriptor':'bloodborne_dw:prototype_thin_window','sourceGeometryAndUvUnchanged':True}
    window['compatibility']='V8 old90004/variant1 and variant2 picked items become this cardinal type; installed source window03 retains original pose in legacy registration.'
    dump(RES/'bloodborne_dw/composite/prototype_glass_window_02.json',window)
    dump(RES/'assets/bloodborne_dw/blockstates/prototype_glass_window_02.json',{'variants':{'':{'model':'bloodborne_dw:block/composite_empty'}}})
    dump(RES/'assets/bloodborne_dw/models/item/prototype_glass_window_02.json',{'parent':'builtin/entity'})
    table=copy.deepcopy(old_catalogue)
    table['status']='APPEND_ONLY_TEMP_TYPES_WITH_EXPLICIT_V8_COMPATIBILITY_FINAL_UNASSIGNED'
    table['policy']='Independent offered art has its own TEMP type. Pose/mount/BASE/ALT are states. Original numbers remain reserved; final numeric namespace IDs require an explicit later migration table.'
    rows=table['entries'];by_id={r['registryId']:r for r in rows}
    by_id['bloodborne_dw:prototype_thin_window']['name']='Тонкое остекление · window01'
    def add(number,registry,name):
        rows.append({'temporaryId':f'{number:05d}','finalId':None,'kind':'architecture','name':name,'registryId':'bloodborne_dw:'+registry,'aliases':['bloodborne_dw:'+registry]})
    add(90010,'prototype_glass_window_02','Тонкое остекление · window02')
    aliases=[{'registryId':'bloodborne_dw:prototype_thin_window','property':'variant','value':str(v),'canonicalRegistryId':'bloodborne_dw:prototype_glass_window_02','legacyWorldPosePreserved':True} for v in [1,2]]
    mappings=[{'oldId':'90004','oldRegistryId':'bloodborne_dw:prototype_thin_window','oldVariant':0,'newId':'90004','newRegistryId':'bloodborne_dw:prototype_thin_window','ordinary':True},
              {'oldId':'90004','oldRegistryId':'bloodborne_dw:prototype_thin_window','oldVariant':1,'newId':'90010','newRegistryId':'bloodborne_dw:prototype_glass_window_02','ordinary':True},
              {'oldId':'90004','oldRegistryId':'bloodborne_dw:prototype_thin_window','oldVariant':2,'newId':'90010','newRegistryId':'bloodborne_dw:prototype_glass_window_02','ordinary':False,'excludedForm':'window03 intrinsic-45; installed legacy art/UUID/pose retained; new picked/dropped/item art is cardinal window02'}]
    for number,material in enumerate(WALL_MATERIALS,90011):
        registry=f'prototype_wall_skin_{material}';add(number,registry,f'Ограда · исходный материал {material}')
        aliases.append({'registryId':'bloodborne_dw:prototype_wall','property':'material','value':str(material),'canonicalRegistryId':'bloodborne_dw:'+registry,'legacyWorldPosePreserved':True})
        mappings.append({'oldId':'90002','oldRegistryId':'bloodborne_dw:prototype_wall','oldMaterial':material,'newId':f'{number:05d}','newRegistryId':'bloodborne_dw:'+registry,'ordinary':True})
        dump(RES/f'assets/bloodborne_dw/blockstates/{registry}.json',json.loads((RES/'assets/bloodborne_dw/blockstates/prototype_wall.json').read_text(encoding='utf8')))
        dump(RES/f'assets/bloodborne_dw/models/item/{registry}.json',json.loads((RES/'assets/bloodborne_dw/models/item/prototype_wall.json').read_text(encoding='utf8')))
        loot=json.loads((RES/'data/bloodborne_dw/loot_tables/blocks/prototype_wall.json').read_text(encoding='utf8'))
        def replace_item(value):
            if isinstance(value,dict):
                if value.get('type')=='minecraft:item' and value.get('name')=='bloodborne_dw:prototype_wall':value['name']='bloodborne_dw:'+registry
                for child in value.values():replace_item(child)
            elif isinstance(value,list):
                for child in value:replace_item(child)
        replace_item(loot)
        dump(RES/f'data/bloodborne_dw/loot_tables/blocks/{registry}.json',loot)
        # The earlier development-only files were under assets/, where vanilla
        # never loads loot tables. Keep only the correct data namespace path.
        obsolete=RES/f'assets/bloodborne_dw/loot_tables/blocks/{registry}.json'
        if obsolete.is_file():obsolete.unlink()
    mappings.append({'oldId':'90002','oldRegistryId':'bloodborne_dw:prototype_wall','oldMaterial':6,'newId':'90002','newRegistryId':'bloodborne_dw:prototype_wall','ordinary':True})
    for variant,number in [(1,90018),(2,90019)]:
        registry=f'prototype_ladder_art_{variant}';add(number,registry,f'Вертикальная деревянная лестница · исполнение {variant+1}')
        aliases.append({'registryId':'bloodborne_dw:prototype_ladder','property':'variant','value':str(variant),'canonicalRegistryId':'bloodborne_dw:'+registry,'legacyWorldPosePreserved':True})
        mappings.append({'oldId':'90006','oldRegistryId':'bloodborne_dw:prototype_ladder','oldVariant':variant,'newId':f'{number:05d}','newRegistryId':'bloodborne_dw:'+registry,'ordinary':True})
    mappings.append({'oldId':'90006','oldRegistryId':'bloodborne_dw:prototype_ladder','oldVariant':0,'newId':'90006','newRegistryId':'bloodborne_dw:prototype_ladder','ordinary':True})
    for old,canonical in RP_CANONICAL.items():
        old_row=by_id['bloodborne_rp:'+old];canonical_row=by_id['bloodborne_rp:'+canonical]
        old_row['canonicalRegistryId']=canonical_row['registryId'];old_row['retiredNumberReserved']=True
        mappings.append({'oldId':old_row['temporaryId'],'oldRegistryId':old_row['registryId'],'newId':canonical_row['temporaryId'],'newRegistryId':canonical_row['registryId'],'ordinary':True,'compatibility':'Old entity registry,UUID,CustomName and instance links retained; old placement resolves canonical; retired TEMP never reused.'})
    table['stateAliases']=aliases
    tag_path=RES/'data/minecraft/tags/blocks/walls.json'
    tag=json.loads(tag_path.read_text(encoding='utf8'))
    for material in WALL_MATERIALS:
        name=f'bloodborne_dw:prototype_wall_skin_{material}'
        if name not in tag['values']:tag['values'].append(name)
    dump(tag_path,tag)
    pickaxe_path=RES/'data/minecraft/tags/blocks/mineable/pickaxe.json'
    pickaxe=json.loads(pickaxe_path.read_text(encoding='utf8'))
    for material in WALL_MATERIALS:
        name=f'bloodborne_dw:prototype_wall_skin_{material}'
        if name not in pickaxe['values']:pickaxe['values'].append(name)
    dump(pickaxe_path,pickaxe)
    original_numbers={r['registryId']:r['temporaryId'] for r in old_catalogue['entries']}
    assert all(original_numbers[r['registryId']]==r['temporaryId'] for r in rows if r['registryId'] in original_numbers)
    assert len({r['temporaryId'] for r in rows})==len(rows)==117
    dump(RES/'bloodborne_dw/debug_catalogue.json',table)
    dump(ROOT/'reports/V9_TYPE_ID_MAPPING.json',{'schema':'dreamwalker-v9-temp-type-compatibility-v1','status':'IMPLEMENTATION_MAPPING_PENDING_ACTUAL_RUNTIME_CHECKS',
        'baselineSourceArchiveSha256':sha(V8.read_bytes()),'sourceDescriptorSha256':sha(legacy.read_bytes()),'window02DescriptorSha256':sha((RES/'bloodborne_dw/composite/prototype_glass_window_02.json').read_bytes()),
        'entries':mappings,'oldNumbersReserved':True,'finalIdsAssigned':False,'finalArchitectureRegistryFormat':'bloodborne_dw:12345','ordinaryGlazingYawDegrees':[0,90,180,270],
        'mounts':['vertical','floor','ceiling'],'sourceException':'No source pose is silently straightened. Legacy window03 remains renderable only in installed saved/source state; pick/drop loses angle and migration privileges.',
        'uvOrTextureEdits':False,'wholeCityConversion':False})
    print(json.dumps({'catalogueRows':len(rows),'newRows':10,'unchangedOriginalNumbers':len(original_numbers),'glazingOrdinaryTypes':2,'legacyWindow03Ordinary':False}))
if __name__=='__main__':main()
