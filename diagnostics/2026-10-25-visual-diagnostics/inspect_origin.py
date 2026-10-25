import json,zipfile
from pathlib import Path
O=Path(__file__).resolve().parent
with zipfile.ZipFile(O/'published-source.zip') as z:
 names=[n for n in z.namelist() if ('export' in n.lower() or 'compile' in n.lower() or 'plan' in n.lower()) and ('native' in n.lower() or n.startswith('qa/'))];print('CANDIDATES',*names[:55],sep='\n')
 for n in z.namelist():
  if '/tools/native-grid/' in n and n.endswith(('.py','.java','.md')):
   p=O/'recovered'/n;assert p.resolve().is_relative_to((O/'recovered').resolve());p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(z.read(n))
with zipfile.ZipFile('C:/Users/vakir/AppData/Local/Limacina/project/DreamWalker/mods/dreamwalker-bb-0.1.0-v12.7.4-visual-physics.1.jar') as z:
 for n in z.namelist():
  if n.endswith('/models/remaining/source/minecraft/block/cobblestone.json') or n.endswith('/models/block/restored_slabs/restored_slab_9c3f5c1f30a2b9bb_bottom.json') or n.endswith('/blockstates/source_brick_carrier.json'):print('JAR',n,z.read(n)[:4000])
 for name in ('spirelamp_0011','spirelamp_0030','spirelamp_0066'):
  n=f'assets/bloodborne_dw/textures/block/remaining/source/minecraft/block/addon/{name}.png';p=O/(name+'.png');p.write_bytes(z.read(n))
src=Path('C:/Users/vakir/AppData/Local/Temp/codex-clipboard-e7263aa4-8378-4e2e-94e3-6d81cef9aa85.png');(O/'screenshots/11.png').write_bytes(src.read_bytes())
