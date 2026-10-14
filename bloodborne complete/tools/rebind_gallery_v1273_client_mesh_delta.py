"""Preserve the accepted catalogue/city plan after a client-only neighbour-AO correction."""
from pathlib import Path
import hashlib,json,zipfile
ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'build/final-followups-20261014'
old=ROOT/'build/runtime-client-comparison-fresh-final-followups-perf-final-r5/mods/dreamwalker-bb-fabric-1.20.1-0.1.0-v12.7.3-final-checks.jar';new=ROOT/'build/libs'/old.name
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
with zipfile.ZipFile(old) as a,zipfile.ZipFile(new) as b:
    assert set(a.namelist())==set(b.namelist());delta=[]
    for name in a.namelist():
        if a.read(name)==b.read(name):continue
        if name.endswith('.json') and a.read(name).replace(b'\r\n',b'\n')==b.read(name).replace(b'\r\n',b'\n'):continue
        assert name.startswith('dev/dreamwalker/bloodbornedw/composite/CompositeWorldMesh') and name.endswith('.class'),('Non-client-mesh delta',name);delta.append(name)
    assert delta and sha(old)=='d94c45d330784cb964722eee9c9a9c597b9245af82b60743b1385f39e2c0ead5'
plan=ROOT/'build/v1273-gallery/input-final-inventory.json';text=plan.read_text(encoding='utf8');assert sha(old) in text;document=json.loads(text.replace(sha(old),sha(new)))
def refresh(value):
    if isinstance(value,dict):
        registry=value.get('registryId',value.get('registry'));descriptor='bloodborne_dw/composite/'+registry.split(':')[-1]+'.json' if registry else None
        if 'descriptorSha256' in value and descriptor:
            with zipfile.ZipFile(new) as archive:
                if descriptor in archive.namelist():value['descriptorSha256']=hashlib.sha256(archive.read(descriptor)).hexdigest()
        for child in value.values():refresh(child)
    elif isinstance(value,list):
        for child in value:refresh(child)
refresh(document)
target=ROOT/'build/v1273-gallery/input-final-client.json';assert not target.exists();target.write_text(json.dumps(document,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
(OUT/'client-mesh-only-production-delta.json').write_text(json.dumps({'status':'PASS_CLIENT_MESH_AND_SOURCE_LINE_ENDING_ONLY_DELTA','beforeJarSha256':sha(old),'productionJarSha256':sha(new),'changedClientClasses':delta,'serverClassesAndDecodedResourceValuesIdentical':True,'prior151ServerTestsApplyToIdenticalServerCodeAndResources':True,'prior550NativeInventoryPixelsApplyToIdenticalItemRendererAndResources':True,'reboundGalleryPlan':str(target),'sourcePreparationRegenerated':False},ensure_ascii=False,indent=2)+'\n',encoding='utf8')
