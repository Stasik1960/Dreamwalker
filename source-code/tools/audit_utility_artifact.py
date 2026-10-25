import hashlib,json,struct,zlib,zipfile
from pathlib import Path
P=Path(__file__).resolve().parents[1];D=P/'utility-evidence';old=P/'bench-release/assets/dreamwalker-bb-0.1.0-v12.7.4-visual-physics.3.jar';new=P/'build-native-visual/libs/dreamwalker-bb-fabric-1.20.1-0.1.0-v12.7.4-visual-physics.4.jar'
def sha(p):
 h=hashlib.sha256()
 with p.open('rb') as f:
  for b in iter(lambda:f.read(1<<20),b''):h.update(b)
 return h.hexdigest()
assert sha(old)=='5c47f820db5b6eddb79b5a46240d35e38e4029b10f5ef161ad8a1c41830dda3e'
with zipfile.ZipFile(old) as a,zipfile.ZipFile(new) as b:
 assets=[n for n in a.namelist() if n.startswith('assets/') and not n.endswith('/')];changed=[n for n in assets if a.read(n)!=b.read(n)];assert all(n in ('assets/bloodborne_dw/lang/en_us.json','assets/bloodborne_dw/lang/ru_ru.json') for n in changed),changed
 data=[n for n in a.namelist() if n.startswith('bloodborne_dw/')];assert all(a.read(n)==b.read(n) for n in data)
 assert not any('/utilityqa/' in n for n in b.namelist());assert json.loads(b.read('fabric.mod.json'))['version']=='0.1.0-v12.7.4-visual-physics.4'
 assert json.loads(b.read('assets/bloodborne_dw/models/block/dw_fake_water.json'))['textures']['particle']=='minecraft:block/water_still'
 assert not any(n.endswith('/dw_fake_water.png') for n in b.namelist())
 png=b.read('assets/bloodborne_dw/textures/block/dw_darkness.png');at=8;raw=b''
 while at<len(png):
  size=struct.unpack_from('>I',png,at)[0];kind=png[at+4:at+8];part=png[at+8:at+8+size];at+=12+size
  if kind==b'IDAT':raw+=part
 pixels=zlib.decompress(raw);assert pixels==b''.join(b'\0'+b'\0\0\0\xff'*16 for _ in range(16))
 result={'status':'PASS','jar':str(new),'sha256':sha(new),'baselineSha256':sha(old),'unchangedExistingAssets':len(assets)-len(changed),'changedExistingAssets':changed,'unchangedDataResources':len(data),'blackTextureBytes':len(png),'blackTextureRGBA':[0,0,0,255],'waterUsesVanillaTexture':True,'mapReconverted':False,'qaExcluded':True,'addedUtilityBlocks':['bloodborne_dw:dw_darkness','bloodborne_dw:dw_fake_water'],'greenPortalImplemented':False}
(D/'artifact-integrity.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
