import hashlib,json
from pathlib import Path
from PIL import Image
P=Path(__file__).resolve().parents[1];D=P/'faerie-evidence';D.mkdir(exist_ok=True);A=P/'src/architecture/resources/assets/bloodborne_dw';source=P/'utility-evidence/portal-preview/faerie-mist-study.png'
def write(p,value):p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
im=Image.open(source).convert('RGB');assert im.size==(256,8192);rows=[]
for tile in range(64):
 x=(tile%8)*32;y=(tile//8)*32;out=Image.new('RGB',(32,32*32))
 for frame in range(32):out.paste(im.crop((x,frame*256+y,x+32,frame*256+y+32)),(0,frame*32))
 name=f'tile_{tile:02d}';path=A/'textures/block/faerie_veil'/(name+'.png');path.parent.mkdir(parents=True,exist_ok=True);out.save(path,optimize=True)
 write(path.with_suffix('.png.mcmeta'),{'animation':{'frametime':2,'interpolate':True}})
 write(A/'models/block/faerie_veil'/(name+'.json'),{'parent':'minecraft:block/cube_all','textures':{'all':'bloodborne_dw:block/faerie_veil/'+name}})
 rows.append({'tile':tile,'bytes':path.stat().st_size,'sha256':hashlib.sha256(path.read_bytes()).hexdigest()})
write(A/'models/block/dw_faerie_veil.json',{'parent':'bloodborne_dw:block/faerie_veil/tile_00'})
write(A/'models/item/dw_faerie_veil.json',{'parent':'bloodborne_dw:block/dw_faerie_veil'})
write(A/'blockstates/dw_faerie_veil.json',{'variants':{'':{'model':'bloodborne_dw:block/dw_faerie_veil'}}})
for lang,name in [('ru_ru','Завеса фей'),('en_us','Faerie Veil')]:
 path=A/'lang'/(lang+'.json');data=json.loads(path.read_text(encoding='utf-8-sig'));data['block.bloodborne_dw.dw_faerie_veil']=name;write(path,data)
write(D/'texture-receipt.json',{'choice':3,'style':'faerie-mist','approvedSourceSha256':hashlib.sha256(source.read_bytes()).hexdigest(),'sourcePixels':[256,8192],'compositionBlocks':[8,8],'tilePixels':32,'frames':32,'cycleSeconds':3.2,'interpolated':True,'pngBytes':sum(r['bytes'] for r in rows),'rawFrameBytes':256*256*32*4,'spriteCount':64,'newBlockStates':1,'blockEntities':0,'particles':0,'blockTickers':0,'tiles':rows})
# QA-only static resource pack disables exactly these animations while preserving every pixel.
static=P/'faerie-evidence/static-pack/assets/bloodborne_dw/textures/block/faerie_veil';static.mkdir(parents=True,exist_ok=True)
write(static.parents[4]/'pack.mcmeta',{'pack':{'pack_format':15,'description':'QA only: frozen faerie veil frame'}})
for tile in range(64):
 name=f'tile_{tile:02d}';Image.open(A/'textures/block/faerie_veil'/(name+'.png')).crop((0,0,32,32)).save(static/(name+'.png'))
print('Prepared selected veil:',sum(r['bytes'] for r in rows),'PNG bytes, one state, no entities')
