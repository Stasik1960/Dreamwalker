"""Seal matching code, layout and built candidate before touching a map copy."""
import datetime,hashlib,json,zipfile
from pathlib import Path
P=Path(__file__).resolve().parents[2];T=P.parent.parent/'DW-visual-physics-20261024'
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
build=json.loads((T/'build-latest.json').read_text(encoding='utf-8'));assert build['status']=='PASS'
jar=T/'build/libs/dreamwalker-bb-fabric-1.20.1-0.1.0-v12.7.4-visual-physics.1.jar';assert jar.is_file()
archive=T/'checkpoint-before-map.zip';receipt=T/'checkpoint-before-map.json'
assert not archive.exists() and not receipt.exists() and not (T/'world').exists()
paths=[]
for folder in ('src/architecture/java','src/rp/java','src/nativevisual/resources','src/nativegridqa','tools/native-visual'):
    paths.extend(p for p in (P/folder).rglob('*') if p.is_file())
paths.extend(P/name for name in ('build.gradle','gradle.properties','src/architecture/resources/fabric.mod.json','src/architecture/resources/native-grid.mixins.json','src/architecture/resources/native-visual.accesswidener','src/architecture/resources/assets/minecraft/atlases/blocks.json'))
files={str(p.relative_to(P)).replace('\\','/'):sha(p) for p in paths}
with zipfile.ZipFile(archive,'x',compression=zipfile.ZIP_DEFLATED,compresslevel=6) as z:
    for path in paths:z.write(path,str(path.relative_to(P)).replace('\\','/'))
with zipfile.ZipFile(archive) as z:
    assert all(hashlib.sha256(z.read(name)).hexdigest()==digest for name,digest in files.items())
result={'status':'IMPLEMENTATION_CHECKPOINT_BEFORE_MAP_REMAP_NOT_GAMEPLAY','utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'archive':{'path':str(archive),'sha256':sha(archive)},'sourceFiles':files,'candidate':{'path':str(jar),'sha256':sha(jar)},'layout':json.loads((T/'layout-report.json').read_text()),'baseFullSources':str(P.parent/'research/delivery/full-current-sources.delivery.zip'),'sourceWorld':'C:/Users/vakir/Desktop/bloodborne-native-grid-city','worldWrites':0,'next':'Run convert_ready_world.py, then finite save/reopen and matched client route. Continue automatically.'}
receipt.write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print(json.dumps({k:v for k,v in result.items() if k!='sourceFiles'},ensure_ascii=False))
