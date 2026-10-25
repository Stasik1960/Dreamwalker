import json,sys
from pathlib import Path
P=Path(__file__).resolve().parents[1];R=P/'src/architecture/resources';D=P/'interaction-evidence';D.mkdir(exist_ok=True)
sys.stdout=(D/'inspect.txt').open('w',encoding='utf-8')
def read(p):return json.loads(p.read_text(encoding='utf-8-sig'))
debug=read(R/'bloodborne_dw/debug_catalogue.json')
print('DEBUG',list(debug) if isinstance(debug,dict) else len(debug))
def rows(value):
 if isinstance(value,dict):
  if any(str(n)=='30952' for n in value.values() if not isinstance(n,(list,dict))):print('WINDOW ID',value)
  for v in value.values():rows(v)
 elif isinstance(value,list):
  for v in value:rows(v)
rows(debug)
static=read(R/'bloodborne_dw/static-architecture-v1274.json');recipe=static['recipes'][510]
print('RECIPE510',recipe)
poses={p['key']:p for p in static['poses']}
for fragment in recipe['fragments']:
 print('POSE',poses[fragment['pose']])
for path in (R/'bloodborne_dw/composite').glob('*.json'):
 doc=read(path)
 if 'заслонк' in doc.get('displayName','').lower():
  print('DESCRIPTOR',path.name,'name',doc['displayName'],'keys',list(doc),'variants',len(doc['variants']))
  (D/path.name).write_text(json.dumps(doc,ensure_ascii=False,indent=2),encoding='utf-8')
  print('PARTS',*[v['poses']['closed']['parts'] for v in doc['variants']])
  for v in doc['variants']:
   for part in v['poses']['closed']['parts']:
    model=read(R/'assets/bloodborne_dw/models'/(part['model'].split(':')[1]+'.json'))
    print('MODEL',part['model'],'textures',model.get('textures'),'parent',model.get('parent'),'elementCount',len(model.get('elements',[])))
    for i,e in enumerate(model.get('elements',[])):
     print('ELEMENT',i,e.get('name'),e.get('from'),e.get('to'),e.get('rotation'),{f:(d.get('texture'),d.get('uv')) for f,d in e.get('faces',{}).items()})
     if part['model'].endswith('spruce_trapdoor_open') and i in (3,8):
      from PIL import Image
      face=e['faces']['north'];texture=model['textures'][face['texture'][1:]];im=Image.open(R/'assets/bloodborne_dw/textures'/(texture.split(':')[1]+'.png')).convert('RGBA');uv=face['uv'];box=tuple(round(n*im.width/16) for n in uv);crop=im.crop(box);crop.resize((crop.width*8,crop.height*8),Image.Resampling.NEAREST).save(D/f'pane-{i}-preview.png');print('PANE',i,'BOX',box,'COLORS',crop.getcolors())
