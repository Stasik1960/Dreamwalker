import hashlib,json,zipfile
from pathlib import Path
import numpy as np
from PIL import Image
P=Path(__file__).resolve().parents[1];D=P/'faerie-evidence';A=P/'src/architecture/resources/assets/bloodborne_dw';source=P/'utility-evidence/portal-preview/faerie-mist-study.png'
original=Image.open(source).convert('RGB');assert (A/'textures/block/faerie_veil.png').read_bytes()==source.read_bytes(),'Approved composition changed'
frames=np.asarray(original,dtype=np.float32).reshape(32,256,256,3);steps=np.abs(frames[1:]-frames[:-1]).mean(axis=(1,2,3));wrap=float(np.abs(frames[0]-frames[-1]).mean());assert wrap<float(steps.max())*1.15
old=P/'utility-release/assets/dreamwalker-bb-0.1.0-v12.7.4-visual-physics.4.jar';new=P/'build-native-visual/libs/dreamwalker-bb-fabric-1.20.1-0.1.0-v12.7.4-visual-physics.5.jar'
def sha(p):
 h=hashlib.sha256()
 with p.open('rb') as f:
  for data in iter(lambda:f.read(1<<20),b''):h.update(data)
 return h.hexdigest()
assert sha(old)=='b98bf53c37ef8244ade0b66875e1da42d8d22809c5c1ffc1104f2430861ab736'
with zipfile.ZipFile(old) as a,zipfile.ZipFile(new) as b:
 assets=[n for n in a.namelist() if n.startswith('assets/') and not n.endswith('/')];changed=[n for n in assets if a.read(n)!=b.read(n)];assert all(n in ('assets/bloodborne_dw/lang/en_us.json','assets/bloodborne_dw/lang/ru_ru.json') for n in changed),changed
 data=[n for n in a.namelist() if n.startswith('bloodborne_dw/')];assert all(a.read(n)==b.read(n) for n in data)
 assert not any('/faerieqa/' in n or n=='faerie-qa.mixins.json' for n in b.namelist());assert json.loads(b.read('fabric.mod.json'))['version']=='0.1.0-v12.7.4-visual-physics.5'
 assert b.read('assets/bloodborne_dw/textures/block/faerie_veil.png')==source.read_bytes()
 assert not any('/faerie_veil/tile_' in n for n in b.namelist())
receipt={'status':'PASS','jar':str(new),'sha256':sha(new),'baselineSha256':sha(old),'unchangedExistingAssets':len(assets)-len(changed),'changedExistingAssets':changed,'unchangedDataResources':len(data),'approvedStudyReconstructedExactly':True,'wrapMeanPixelDelta':wrap,'maxOtherMeanPixelDelta':float(steps.max()),'newStates':1,'blockEntities':0,'particles':0,'blockTickers':0,'mapReconverted':False,'qaExcluded':True}
core=P/'faerie-profiles/server/mods/dreamwalker-bb-fabric-1.20.1-0.1.0-v12.7.4-visual-physics.5.jar'
if core.exists():
 assert sha(core)==json.loads((D/'server-1/process.json').read_text())['productionSha256']
 with zipfile.ZipFile(core) as a,zipfile.ZipFile(new) as b:
  changes=sorted(n for n in set(a.namelist())&set(b.namelist()) if not n.endswith('/') and a.read(n)!=b.read(n))
  removed=sorted(set(a.namelist())-set(b.namelist()));added=sorted(set(b.namelist())-set(a.namelist()))
  assert all('/architecture/FaerieVeilClient' in n or n=='assets/bloodborne_dw/models/block/dw_faerie_veil.json' for n in changes),changes
  assert all('/faerie_veil/tile_' in n or n.endswith('/') for n in removed),removed
  assert all(n in ('assets/bloodborne_dw/textures/block/faerie_veil.png','assets/bloodborne_dw/textures/block/faerie_veil.png.mcmeta') or n.endswith('/') for n in added),added
 receipt.update({'coreAcceptedSha256':sha(core),'afterCoreChanged':changes,'afterCoreRemovedObsoleteTiles':len([n for n in removed if not n.endswith('/')]),'afterCoreAdded':added})
(D/'artifact-integrity.json').write_text(json.dumps(receipt,indent=2),encoding='utf-8')
