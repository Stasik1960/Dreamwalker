import json,zipfile,runpy
from pathlib import Path
O=Path(__file__).resolve().parent
ns=runpy.run_path(str(O/'inspect_map.py'));at=ns['at'];out={}
with zipfile.ZipFile('C:/Users/vakir/AppData/Local/Limacina/project/DreamWalker/mods/dreamwalker-bb-0.1.0-v12.7.4-visual-physics.1.jar') as z:
 legacy=json.loads(z.read('bloodborne_dw/static-architecture-v1274.json'));print('Legacy keys',list(legacy),'recipe0',str(legacy['recipes'][0])[:500]);poses={p['key']:p for p in legacy['poses']}
 for label,i in [('balustrade',189),('lowerArchitecture',1591)]:
  r=legacy['recipes'][i];out[label]={'recipe':r,'poses':{str(f):poses[f] for f in []}};print(label,str(r)[:900])
  for f in r.get('fragments',[]):
   key=f.get('pose');out[label]['poses'][key]=poses[key];print('POSE',key,str(poses[key])[:900])
 for xyz in [(31,65,-692),(31,65,-694),(31,65,-697)]:out[str(xyz)]=at(*xyz);print(xyz,out[str(xyz)]['state'])
 fallback=json.loads(z.read('bloodborne_dw/source-fallback-carriers-v1271.json'));out['fallback113']={'sourceState':fallback['sourceStates'][113],'applications':fallback.get('sourceApplicationSets',[None]*114)[113]};print('fallback113',out['fallback113'])
(O/'legacy-targets.json').write_text(json.dumps(out,ensure_ascii=False,indent=2),encoding='utf-8')
