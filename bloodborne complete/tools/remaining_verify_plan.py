"""Verify source coverage and frozen manifest without touching production assets."""
import hashlib, json, traceback
from collections import Counter
from pathlib import Path
from remaining_plan import art, EXISTING, RETIRED

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'docs/remaining-transfer'

def fingerprint(model):
    return hashlib.sha256(json.dumps(art(model),sort_keys=True,separators=(',',':')).encode()).hexdigest()

def main():
    candidate=json.loads((OUT/'source-candidates.json').read_text(encoding='utf-8'))
    inventory=json.loads((OUT/'source-inventory.json').read_text(encoding='utf-8'))
    alpha=json.loads((OUT/'source-alpha-audit.json').read_text(encoding='utf-8'))
    transparent={row['model'] for row in alpha['modelsWithOnlyTransparentSampledFaces']}
    models={row['model']:row for row in inventory['models']}
    objects=candidate['objects'];paths=[obj['path'] for obj in objects]
    assert len(paths)==len(set(paths))
    retired={name for name in models if name.removeprefix('minecraft:block/') in RETIRED}
    existing={name for name in models if name.removeprefix('minecraft:block/') in EXISTING}
    retired_fingerprints={fingerprint(models[name]):name for name in retired}
    existing_fingerprints={fingerprint(models[name]):name for name in existing}
    legacy_duplicates=[]; empty_whole=[]; used=set(); known_particle=[]
    for obj in objects:
        assert obj['path'].startswith('source_')
        assert len(obj['variants'])<=16
        assert [variant['variant'] for variant in obj['variants']]==list(range(len(obj['variants'])))
        assert obj['displayName'] and not obj['displayName'].startswith('Архитектурная деталь')
        for variant in obj['variants']:
            assert variant['closed']
            assert any(app['model'] not in transparent for app in variant['closed']), ('Fully transparent offered artwork',obj['path'],variant['variant'])
            visible=False
            for application in variant['closed']+variant.get('open',[]):
                name=application['model'];used.add(name)
                assert name in models, ('Missing candidate model',name)
                assert name not in retired, ('Retired art imported',name)
                assert name not in existing, ('Existing art duplicated into new type',name)
                issues=models[name]['resolved'].get('issues',[])
                if issues:
                    assert name in ('minecraft:block/jungle_trapdoor_bottom','minecraft:block/jungle_trapdoor_open')
                    assert all(issue['type']=='MISSING_TEXTURE' and issue['texture']=='block/texture' for issue in issues)
                    known_particle.append(name)
                assert application.get('x',0)%90==0 and application.get('y',0)%90==0
                assert len(application.get('offset',[0,0,0]))==3
                visible |= any(element.get('faces') for element in models[name]['resolved'].get('elements',[]))
                hashed=fingerprint(models[name])
                if hashed in retired_fingerprints:legacy_duplicates.append({'new':name,'retired':retired_fingerprints[hashed]})
                if hashed in existing_fingerprints:legacy_duplicates.append({'new':name,'existing':existing_fingerprints[hashed]})
            assert visible, ('Fully empty offered source state',obj['path'],variant['variant'])
    classified={row['sourceModel'] for row in candidate['modelClassifications']}
    assert set(models)<=classified
    report={'status':'PASS','manifestSha256':hashlib.sha256((OUT/'source-candidates.json').read_bytes()).hexdigest(),
            'objects':len(objects),'offeredStates':sum(len(obj['variants']) for obj in objects),'sourceModelsConsumed':len(used),
            'retiredArtConsumed':len(used & retired),'existingArtConsumedByNewTypes':len(used & existing),
            'exactVisualAliasesOfProtectedArt':legacy_duplicates,'knownSourceParticleDefects':sorted(set(known_particle)),
            'classificationCounts':dict(Counter(row['kind'] for row in candidate['modelClassifications'])),
            'sourceAlphaVisibilityCheck':'PASS','explicitTransparentHelperTypesExcluded':3,'reservedTransparentNumbers':['90535','90626','93191']}
    (OUT/'plan-verification.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')

if __name__=='__main__':
    try:main()
    except Exception:
        (OUT/'plan-verification.json').write_text(json.dumps({'status':'FAIL','traceback':traceback.format_exc()},indent=2),encoding='utf-8');raise
