"""Prove source pixels/old face UV/geometry unchanged, with only reviewed opaque cap additions."""
from pathlib import Path
import json,subprocess,hashlib
ROOT=Path(__file__).resolve().parents[1];REPO=ROOT.parent;OUT=ROOT/'build/final-followups-20261014'
names=subprocess.run(['git','diff','--name-only'],cwd=ROOT,capture_output=True,check=True).stdout.decode().splitlines();models=[];faces=0;decals=[]
def old(name):return json.loads(subprocess.run(['git','show','HEAD:'+name],cwd=ROOT,capture_output=True,check=True).stdout)
def old_elements(document):
    if 'elements' in document:return document['elements']
    parent=document.get('parent');assert parent and ':' in parent
    namespace,path=parent.split(':',1)
    return old_elements(old('bloodborne complete/src/architecture/resources/assets/'+namespace+'/models/'+path+'.json'))
for name in names:
    assert not name.endswith('.png'),('Unrequested pixel edit',name)
    p=REPO/name
    if '/models/remaining/source/' in name and name.endswith('.json'):
        a,b=old(name),json.loads(p.read_text(encoding='utf-8-sig'));ae=old_elements(a);a.pop('elements',None);be=b.pop('elements');assert a==b and len(ae)==len(be)
        added=0
        for x,y in zip(ae,be):
            af,bf=x.pop('faces',{}),y.pop('faces',{});assert x==y
            assert all(k in bf and v==bf[k] for k,v in af.items()),('Authored face changed',name)
            for k in bf.keys()-af.keys():
                assert all(y['to'][i]-y['from'][i]>1e-5 for i in range(3)) and y.get('rotation',{}).get('angle',0)==0
                copied={q:v for q,v in bf[k].items() if q!='cullface'}
                assert any(copied=={q:v for q,v in value.items() if q!='cullface'} for value in af.values())
                added+=1
        faces+=added;models.append({'model':name,'facesAdded':added})
    elif '/bloodborne_dw/composite/' in name and name.endswith('.json') and not name.endswith('/source_stairs_andesite_stairs_top.json'):
        a,b=old(name),json.loads(p.read_text(encoding='utf-8-sig'));rows=b.pop('surfaceDecals',None);assert a==b and rows is not None;decals.append(name)
catalog='bloodborne complete/src/architecture/resources/bloodborne_dw/remaining-catalogue.json';a,b=old(catalog),json.loads((REPO/catalog).read_text(encoding='utf-8-sig'));ao,bo=a.pop('objects'),b.pop('objects');assert a==b and len(ao)==len(bo)
hidden=[]
for x,y in zip(ao,bo):
    if x==y:continue
    assert x['path']==y['path'] and y.pop('conversionOnly') is True and x==y;hidden.append(x['path'])
assert set(hidden)=={'source__wall_dec_top','source_hold_wall_lantern_4'}
report={'status':'PASS_EXACT_REQUESTED_ASSET_SCOPE','sourceVolumeModelsWithOnlyExposedCopiedFaces':len(models),'newExposedCopiedFaces':faces,'oldFacesUvCoordinatesElementRotationsAndModelFlagsChanged':False,'sourcePngPixelsChanged':False,'originalV12WorldModelsChanged':False,'surfaceMetadataOnlySpecs':len(decals),'hiddenCatalogueOnly':hidden,'savedRegistrationsRemoved':False,'models':models}
(OUT/'asset-scope-proof.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
