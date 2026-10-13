"""Append-only follow-up equivalences for visually reviewed practical clones.

The historical V12.4 alias manifest and its1281 source mappings remain exact.
Only current NEW primary models are combined; all current pose geometry is
copied into appended states of the chosen existing lower numbered primary.
"""
from __future__ import annotations
import copy, hashlib, json
from pathlib import Path
from v127_visual_export import ROOT,RES,OUT,write_json

GROUPS=[
    ([90328,90329],'Одна серая кирпичная кладка: одинаковые размер, ряды кладки и назначение; слабый сдвиг оттенка сохранён видом.'),
    ([90435,90776,90940],'Одна тонкая серая каменная плита: одинаковая высота и проходная поверхность; мелкая зернистость сохранена видами.'),
    ([90899,90903],'Одна замшелая кирпичная плита: одинаковые размер, форма и зелёная кладка; раскладка кладки сохранена видами.'),
    ([90320,90706],'Одна серая кирпичная полуплита: одинаковая форма и масштаб кирпича; незначительная раскладка швов сохранена видами.'),
    ([90421,90758],'Одна обычная серая полуплита с горизонтальным швом; небольшое различие крапа сохранено видами.'),
    ([90870,90871],'Одна белая свечная композиция: одинаковые основание, длина и основная форма; мелкие подвески сохранены видами.'),
]

def main():
    visual=json.loads((OUT/'visual-inventory.json').read_text(encoding='utf-8'));rows={r['id']:r for r in visual['objects']}
    baseline=RES/'bloodborne_dw/imported-type-aliases.json';baseline_bytes=baseline.read_bytes();before=json.loads(baseline_bytes)
    path=RES/'bloodborne_dw/imported-type-aliases-v127.json'
    assert not path.exists(),'Followup manifest already applied: do not append twice'
    catalogue_path=RES/'bloodborne_dw/remaining-catalogue.json';catalogue=json.loads(catalogue_path.read_text(encoding='utf-8'));names={r['path']:r for r in catalogue['objects']}
    groups,mappings,proofs=[],[],[]
    for numbers,reason in GROUPS:
        members=[rows[str(n)] for n in numbers];primary=members[0];registry=primary['registryId'];rootpath=registry.split(':')[1]
        descriptor=RES/'bloodborne_dw/composite'/f'{rootpath}.json';spec=json.loads(descriptor.read_text(encoding='utf-8'));old_primary=copy.deepcopy(spec)
        assert spec['simplePhysicalCollision'] and not spec['openable']
        original=len(spec['variants']);old_variants={registry:original}
        for row in members[1:]:
            raw=RES/'bloodborne_dw/composite'/f"{row['registryId'].split(':')[1]}.json";source=json.loads(raw.read_text(encoding='utf-8'))
            assert source['simplePhysicalCollision'] and not source['openable']
            old_variants[row['registryId']]=len(source['variants'])
            for i,variant in enumerate(source['variants']):
                target=len(spec['variants']);assert target<16,'Keep followup state budget within unbanked range'
                spec['variants'].append(copy.deepcopy(variant))
                name=copy.deepcopy(names[row['registryId'].split(':')[1]]['variants'][i]);name['variant']=target;names[rootpath]['variants'].append(name)
                mappings.append({'oldId':row['id'],'oldRegistryId':row['registryId'],'oldVariant':i,
                    'newId':primary['id'],'newRegistryId':registry,'newVariant':target,'rotationDelta':0,'sourceShift':[0.,0.,0.],
                    'properties':{},'v127ExactCurrentPoseCopied':True})
                proofs.append({'oldId':row['id'],'oldPose':i,'newId':primary['id'],'newPose':target,
                    'exactCurrentVariantSha256':hashlib.sha256(json.dumps(variant,sort_keys=True,separators=(',',':')).encode()).hexdigest(),
                    'originalYawMountSourceShiftUnchanged':True})
        assert spec['variants'][:original]==old_primary['variants']
        spec['v127PracticalClonePolicy']='Append-only current NEW equivalent types; original V12 untouched. Old registrations remain hidden save adapters. Historic V12.4 aliases compose through separate follow-up manifest.'
        write_json(descriptor,spec)
        groups.append({'primaryId':primary['id'],'primaryRegistryId':registry,'kind':'artwork','oneItem':True,
            'members':[r['registryId'] for r in members],'poseCount':len(spec['variants']),'reason':reason,'currentPoseCountsBefore':old_variants})
    write_json(catalogue_path,catalogue)
    overlay={'schemaVersion':1,'scope':'POST_ORIGINAL_V12_ONLY_FOLLOWUP_AFTER_V124','baselineCommit':'0c985dc28e4a38900cce3055ddb5658a71ed95e5',
        'historicalManifestSha256':hashlib.sha256(baseline_bytes).hexdigest(),'groups':groups,'mappings':mappings,
        'policy':'No registration removal, no numeric ID reuse. Current sourceposes copied exactly to primary states; historical mapping chains resolve in memory. Original V12 excluded.'}
    write_json(path,overlay)
    debug_path=RES/'bloodborne_dw/debug_catalogue.json';debug=json.loads(debug_path.read_text(encoding='utf-8'));numbers_before={r['temporaryId']:r['registryId'] for r in debug['entries']}
    redirects={member:group['primaryRegistryId'] for group in groups for member in group['members'] if member!=group['primaryRegistryId']}
    # Re-resolve earlier aliases by the followup group while leaving the reserved
    # raw registry identity/number intact; old aliases can have a direct canonical
    # selector that now names a followup-retired primary.
    for entry in debug['entries']:
        target=entry.get('canonicalRegistryId',entry['registryId'])
        if target in redirects:entry['canonicalRegistryId']=redirects[target]
    assert numbers_before=={r['temporaryId']:r['registryId'] for r in debug['entries']}
    write_json(debug_path,debug)
    assert baseline.read_bytes()==baseline_bytes
    result={'status':'PASS_APPEND_ONLY_REVIEWED_NEW_CLONES','workingNewBefore':544,'workingNewAfter':544-len(redirects),
        'groups':groups,'mappings':mappings,'stateVertexUVTextureCopies':proofs,'retiredCurrentTypes':len(redirects),
        'old1281MappingsExact':len(before['mappings'])==1281,'originalV12Unaffected':True}
    write_json(OUT/'practical-dedup.json',result)
    print(json.dumps({'status':result['status'],'groups':len(groups),'retiredCurrentTypes':len(redirects),'workingNewAfter':result['workingNewAfter'],'currentPoseCopies':len(mappings)}))

if __name__=='__main__':main()
