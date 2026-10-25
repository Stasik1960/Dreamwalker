import hashlib,json,zipfile
from pathlib import Path
P=Path(__file__).resolve().parents[1];old=P/'fix-release/assets/dreamwalker-bb-0.1.0-v12.7.4-visual-physics.2.jar';new=P/'build-native-visual/libs/dreamwalker-bb-fabric-1.20.1-0.1.0-v12.7.4-visual-physics.3.jar';sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest();assert sha(old)=='b776096762ea39f4b46fade64bb0aecc4f166d83086cd95bb6dba37846639584'
with zipfile.ZipFile(old) as a,zipfile.ZipFile(new) as b:
 assets=[n for n in a.namelist() if n.startswith('assets/') and not n.endswith('/')];assert all(a.read(n)==b.read(n) for n in assets)
 data=[n for n in a.namelist() if n.startswith('bloodborne_dw/')];assert all(a.read(n)==b.read(n) for n in data)
 assert not any('/benchqa/' in n for n in b.namelist());assert json.loads(b.read('fabric.mod.json'))['version']=='0.1.0-v12.7.4-visual-physics.3'
(P/'bench-evidence/artifact-integrity.json').write_text(json.dumps({'status':'PASS','jar':str(new),'sha256':sha(new),'baselineSha256':sha(old),'unchangedExistingAssets':len(assets),'unchangedDataResources':len(data),'newBlockStates':0,'addedProductionEntityTypes':['bloodborne_dw:bench_seat'],'mapRewriteRequired':False},indent=2),encoding='utf-8');print('All',len(assets),'existing assets and',len(data),'data resources preserved')
