"""Preserve native-discovered NEW source appearances as states of existing groups."""
from pathlib import Path
import argparse,copy,hashlib,json
ROOT=Path(__file__).resolve().parents[1];D=ROOT/'docs/v12.4-dedup'
def read(p):return json.loads(p.read_text(encoding='utf-8'))
def apply_corrections(plan,corrections):
    groups={g['mainId']:g for g in plan['groups']}
    for correction in corrections['mappings']:
        group=groups[correction['mainId']]
        mapping=next(m for m in group['mappings'] if m['oldRegistryId']==correction['oldRegistryId'] and m['oldVariant']==correction['oldVariant'])
        if correction['action']=='EXACT_SOURCE_POSE_STATE':
            source_key=(mapping['oldRegistryId'],mapping['oldVariant'])
            index=next((i for i,r in enumerate(group['representatives']) if (r['registryId'],r['oldVariant'])==source_key),None)
            if index is None:
                index=len(group['representatives']);group['representatives'].append({'variant':index,'registryId':source_key[0],'oldVariant':source_key[1],'name':correction['sourceName']})
            mapping.update(variant=index,yawDeltaSteps=0,sourceShiftDelta=[0.,0.,0.],representativeRegistryId=source_key[0],representativeVariant=source_key[1])
        else:
            assert correction['action']=='EXACT_NATIVE_YAW_AND_PIVOT'
            mapping.update(yawDeltaSteps=correction['yawDeltaSteps'],sourceShiftDelta=correction['sourceShiftDelta'])
        mapping['nativeCorrectionReason']=correction['reason']
        group['newVariantCount']=len(group['representatives']);group['variantPropertyBudget16']=group['newVariantCount']<=16
        assert group['newVariantCount']<=128
    plan['nativeCorrections']=copy.deepcopy(corrections)
    plan['status']='NATIVE_DISCREPANCIES_CORRECTED_FINAL_ARTIFACT_RECHECK_REQUIRED'
    return plan
def main():
    p=argparse.ArgumentParser();p.add_argument('--plan',type=Path,default=D/'general-functional-groups.json');a=p.parse_args()
    baseline=read(D/'baseline-content.json')['descriptors'];capture=read(ROOT/'build/runtime-client-v124-comparison-final-1/v124-client-comparison-output.json')
    assert capture['productionJarSha256']=='c30cb62063656b5230c2c2a6e9338ac208a687d9df712f6bc6ced51a1598a728'
    corrections={'schemaVersion':1,'scope':'POST_ORIGINAL_V12_EXISTING_FUNCTIONAL_GROUPS_ONLY','nativeDiscrepancyJarSha256':capture['productionJarSha256'],
      'mappings':[
        {'mainId':'90088','oldRegistryId':'bloodborne_dw:source_addon_chains_7','oldVariant':0,'action':'EXACT_NATIVE_YAW_AND_PIVOT','yawDeltaSteps':1,'sourceShiftDelta':[-0.0167354346,0.,0.0845970869],
         'reason':'Native page6 reveals a180degree alpha/UV artwork flip. Source element yaw-45 and target yaw0 require added root rotation1; cyclic geometry symmetry must not discard the vertex-to-UV association.','nativeFrameSha256':capture['frames'][5]['sha256']},
        {'mainId':'90182','oldRegistryId':'bloodborne_dw:source_stairs_jungle_stairs_outer','oldVariant':0,'action':'EXACT_SOURCE_POSE_STATE',
         'sourceName':baseline['source_stairs_jungle_stairs_outer']['variants'][0].get('name','Исходная узкая декоративная плоскость'),
         'reason':'Native page12 shows different geometry: target source model has rescale:true at element angle0, which native FaceBakery expands using its45degree factor. Preserve the original source model as an additional state of90182.','nativeFrameSha256':capture['frames'][11]['sha256']},
        {'mainId':'90637','oldRegistryId':'bloodborne_dw:source_lever_on','oldVariant':0,'action':'EXACT_SOURCE_POSE_STATE',
         'sourceName':baseline['source_lever_on']['variants'][0].get('name','Рычаг, исходная каменная отделка'),
         'reason':'Native page38 has matching lever silhouette and placement, but different orientation of the dark stone-base markings. Preserve the original vertex-associated stone UV as another state of the same lever type.','nativeFrameSha256':capture['frames'][37]['sha256']},
        {'mainId':'90637','oldRegistryId':'bloodborne_dw:source_lever_on','oldVariant':1,'action':'EXACT_SOURCE_POSE_STATE',
         'sourceName':baseline['source_lever_on']['variants'][1].get('name','Рычаг, исходная обратная установка'),
         'reason':'Native page38 shows the same reverse-mounted lever, with the stone joint and mottled face pattern in another orientation. Retain exact source UV in a state without adding another placing item.','nativeFrameSha256':capture['frames'][37]['sha256']},
        {'mainId':'90545','oldRegistryId':'bloodborne_dw:source_quartz_pillar','oldVariant':0,'action':'EXACT_SOURCE_POSE_STATE',
         'sourceName':baseline['source_quartz_pillar']['variants'][0].get('name','Пьедестал, исходная отделка'),
         'reason':'Native page34 shows equal pedestal geometry, while the mottled top stone patch turns relative to the front edge. Exact source UV is retained as a state of the same functional pedestal.','nativeFrameSha256':capture['frames'][33]['sha256']},
        {'mainId':'93088','oldRegistryId':'bloodborne_dw:source_stone_button_pressed','oldVariant':1,'action':'EXACT_SOURCE_POSE_STATE',
         'sourceName':baseline['source_stone_button_pressed']['variants'][1].get('name','Тонкая каменная плита, исходная отделка'),
         'reason':'Native page54 shows the same thin stone slab with a different light/dark patch arrangement on its upper face. Preserve the source appearance as another slab state; the separate functional type remains removed.','nativeFrameSha256':capture['frames'][53]['sha256']},
        {'mainId':'90676','oldRegistryId':'bloodborne_dw:source_yellow_concrete','oldVariant':0,'action':'EXACT_SOURCE_POSE_STATE',
         'sourceName':baseline['source_yellow_concrete']['variants'][0].get('name','Обрамлённый каменный орнамент, исходная отделка'),
         'reason':'Native page40 has the same full-size framed triangular stone ornament, but vertex-associated stone mottling and face pattern orientation do not exactly match. Preserve the original material/UV state within the existing ornament type.','nativeFrameSha256':capture['frames'][39]['sha256']}
      ]}
    path=D/'native-mapping-corrections.json';path.write_text(json.dumps(corrections,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    plan=apply_corrections(read(a.plan),corrections);a.plan.write_text(json.dumps(plan,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
if __name__=='__main__':main()
