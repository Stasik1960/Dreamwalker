"""Rebind an already checked geometry plan only after proving an item-display-only JAR delta."""
from pathlib import Path
import hashlib,json,sys,zipfile
ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'build/final-followups-20261014'
old=ROOT/'build/runtime-client-final-followups-icons-r4b/mods/dreamwalker-bb-fabric-1.20.1-0.1.0-v12.7.3-final-checks.jar';new=ROOT/'build/libs'/old.name
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
with zipfile.ZipFile(old) as before,zipfile.ZipFile(new) as after:
    assert set(before.namelist())==set(after.namelist());differences=[]
    for name in before.namelist():
        if before.read(name)==after.read(name):continue
        assert name.startswith('assets/bloodborne_dw/models/item/') and name.endswith('.json'),('Non-inventory production difference',name)
        a,b=json.loads(before.read(name)),json.loads(after.read(name));a.pop('display',None);b.pop('display',None);assert a==b,('Non-display item difference',name);differences.append(name)
    repairs=json.loads((OUT/'native-proven-icon-repairs.json').read_text(encoding='utf8'))
    expected={'assets/bloodborne_dw/models/item/'+row['item'].split(':')[-1]+'.json' for row in repairs['rows']}
    assert 0<len(differences)<=len(expected) and set(differences).issubset(expected),('Unexpected set of actual native icon repairs',len(differences))
    assert sha(old)=='3b60c27f01a7bc00da33c090dee1ffb02b97ad5ac6b0a85518a3e286d4858064'
plan=ROOT/'build/v1273-gallery/input-final.json';text=plan.read_text(encoding='utf8');assert sha(old) in text
text=text.replace(sha(old),sha(new));target=ROOT/'build/v1273-gallery/input-final-inventory.json';assert not target.exists();target.write_text(text,encoding='utf8')
(OUT/'inventory-only-production-delta.json').write_text(json.dumps({'status':'PASS_EXACT_INVENTORY_DISPLAY_ONLY_DELTA','beforeJarSha256':sha(old),'productionJarSha256':sha(new),'changedEntries':differences,'changedNativeClasses':0,'changedWorldModelOrTextureOrDescriptorEntries':0,'prior150ServerTestsStillApplyToIdenticalCodeAndWorldResources':True,'reboundGalleryPlan':str(target),'sourcePreparationRegenerated':False},ensure_ascii=False,indent=2)+'\n',encoding='utf8')
