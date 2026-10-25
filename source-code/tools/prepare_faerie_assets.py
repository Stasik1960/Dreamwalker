import hashlib,json,shutil,stat
from pathlib import Path
from PIL import Image
P=Path(__file__).resolve().parents[1];D=P/'faerie-evidence';D.mkdir(exist_ok=True);A=P/'src/architecture/resources/assets/bloodborne_dw';source=P/'utility-evidence/portal-preview/faerie-mist-study.png'
if not source.exists():source=A/'textures/block/faerie_veil.png'
def write(p,value):p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
im=Image.open(source).convert('RGB');assert im.size==(256,8192)
# Remove only explicitly generated obsolete tiles, never recursively.
for tile in range(64):
 for folder,extension in [('textures/block/faerie_veil','.png'),('textures/block/faerie_veil','.png.mcmeta'),('models/block/faerie_veil','.json')]:
  path=A/folder/(f'tile_{tile:02d}'+extension)
  if path.exists():
   assert path.resolve().is_relative_to(A.resolve()) and not path.stat(follow_symlinks=False).st_file_attributes&stat.FILE_ATTRIBUTE_REPARSE_POINT
   path.unlink()
texture=A/'textures/block/faerie_veil.png'
if source.resolve()!=texture.resolve():shutil.copy2(source,texture)
write(texture.with_suffix('.png.mcmeta'),{'animation':{'frametime':2,'interpolate':True}})
write(A/'models/block/dw_faerie_veil.json',{'parent':'minecraft:block/cube_all','textures':{'all':'bloodborne_dw:block/faerie_veil'}})
write(A/'models/item/dw_faerie_veil.json',{'parent':'bloodborne_dw:block/dw_faerie_veil'})
write(A/'blockstates/dw_faerie_veil.json',{'variants':{'':{'model':'bloodborne_dw:block/dw_faerie_veil'}}})
for lang,name in [('ru_ru','Завеса фей'),('en_us','Faerie Veil')]:
 path=A/'lang'/(lang+'.json');data=json.loads(path.read_text(encoding='utf-8-sig'));data['block.bloodborne_dw.dw_faerie_veil']=name;write(path,data)
write(D/'texture-receipt.json',{'choice':3,'style':'faerie-mist','approvedSourceSha256':hashlib.sha256(source.read_bytes()).hexdigest(),'sourcePixels':[256,8192],'compositionBlocks':[8,8],'tilePixels':32,'frames':32,'cycleSeconds':3.2,'interpolated':True,'pngBytes':texture.stat().st_size,'rawFrameBytes':256*256*32*4,'spriteCount':1,'newBlockStates':1,'blockEntities':0,'particles':0,'blockTickers':0})
print('Prepared one shared approved animation:',texture.stat().st_size,'bytes')
