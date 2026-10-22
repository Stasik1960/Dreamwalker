import hashlib,json,zipfile
from pathlib import Path
P=Path(__file__).absolute().parents[2];O=P/'build/streaming-validation'
version=dict(line.split('=',1) for line in (P/'gradle.properties').read_text().splitlines() if '=' in line)['mod_version']
old=P/'build/libs/dreamwalker-bb-fabric-1.20.1-0.1.0-v12.7.4-owner32-static-city.6-chunk-mesh.2.jar';new=P/'build/libs'/('dreamwalker-bb-fabric-1.20.1-'+version+'.jar')
def sha(data):return hashlib.sha256(data).hexdigest()
with zipfile.ZipFile(old) as a,zipfile.ZipFile(new) as b:
 names=sorted(name for name in a.namelist() if not name.endswith('/') and (name.startswith('assets/') or name.startswith('bloodborne_dw/') and name.endswith(('.json','.gz'))))
 assert set(names)=={name for name in b.namelist() if not name.endswith('/') and (name.startswith('assets/') or name.startswith('bloodborne_dw/') and name.endswith(('.json','.gz')))}
 changed=[name for name in names if sha(a.read(name))!=sha(b.read(name))]
 classes=['StaticArchitectureBrightnessMixin','StaticGrassMasonryBrightnessMixin','CompositeMeshLightMixin','CompositeMeshLightStorageMixin','CompositeMeshLightQueueMixin']
 checks=[]
 for name in classes:
  path='dev/dreamwalker/bloodbornedw/composite/mixin/'+name+'.class';checks.append({'class':name,'unchanged':a.read(path)==b.read(path),'sha256':sha(b.read(path))})
 path='dev/dreamwalker/bloodbornedw/composite/CompositeOwnerNbt.class';checks.append({'class':'CompositeOwnerNbt','unchanged':a.read(path)==b.read(path),'sha256':sha(b.read(path))})
 result={'status':'PASS' if not changed and all(row['unchanged'] for row in checks) else 'FAIL','baselineSha256':sha(old.read_bytes()),'candidateSha256':sha(new.read_bytes()),'assetAndCatalogFiles':len(names),'pngFiles':sum(n.endswith('.png') for n in names),'modelJsonFiles':sum('/models/' in n and n.endswith('.json') for n in names),'changedAssetFiles':changed,'lightingAndWireClasses':checks}
 (O/('unchanged-assets-'+version+'.json')).write_text(json.dumps(result,indent=2)+'\n');assert result['status']=='PASS',result
