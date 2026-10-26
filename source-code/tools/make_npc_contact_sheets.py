import json,sys
from pathlib import Path
from PIL import Image,ImageDraw,ImageFont
P=Path(__file__).resolve().parents[1];D=P/'npc-evidence';ids=[a['id'] for a in json.loads((D/'assets-audit.json').read_text(encoding='utf-8'))['assets']];font=ImageFont.truetype('C:/Windows/Fonts/consola.ttf',15)
corpses='--corpses' in sys.argv
run=next((a.split('=',1)[1] for a in sys.argv if a.startswith('--run=')),'client-3' if corpses else 'client-2')
for phase in (('early','final') if corpses else ('idle','death')):
 sheet=Image.new('RGB',(1920,624),'#26333c');draw=ImageDraw.Draw(sheet)
 for i,name in enumerate(ids):
  path=D/run/f'{"corpse" if corpses else "npc"}-{name}-{phase}.png';assert path.is_file(),path
  with Image.open(path) as im:tile=im.convert('RGB').resize((320,180))
  x=i%6*320;y=i//6*208;sheet.paste(tile,(x,y));draw.text((x+7,y+185),name,font=font,fill='white')
 sheet.save(D/f'contact-{"corpses-" if corpses else ""}{phase}.png')
