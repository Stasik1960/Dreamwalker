"""A small LGPL shader-source variant: nearest shadow projection is32 metres."""
import hashlib,json,re,zipfile
from pathlib import Path
P=Path(__file__).resolve().parents[2];T=P.parent.parent/'DW-visual-physics-20261024';A=T/'addons'
j=json.loads((A/'downloads.json').read_text(encoding='utf-8'));original=j['makeupLow'];source=Path(original['path']);assert hashlib.sha256(source.read_bytes()).hexdigest()==original['sha256']
target=source.with_name('MakeUp-UltraFast-9.6-Dreamwalker-32m.zip');changed=0
with zipfile.ZipFile(source) as z,zipfile.ZipFile(target,'w',zipfile.ZIP_DEFLATED,compresslevel=6) as out:
 for entry in z.infolist():
  data=z.read(entry.filename)
  if entry.filename=='shaders/lib/config.glsl':
   text=data.decode('utf-8-sig');text,changed=re.subn(r'(const\s+float\s+shadowDistance\s*=\s*)75\.0(\s*;)',r'\g<1>32.0\2',text);assert changed==3;data=text.encode('utf-8')
  out.writestr(entry,data)
 out.writestr('DREAMWALKER-MODIFICATION.txt','Based on MakeUp UltraFast9.6, LGPL-3.0-or-later.\nSource: '+str(original['sourceUrl'])+'\nOnly the three75.0m nearest-distance constants in shaders/lib/config.glsl are32.0m. All shader source and upstream licence files are included.\n')
j['makeupNear']={**original,'path':str(target),'sha256':hashlib.sha256(target.read_bytes()).hexdigest(),'version':original['version']+'-Dreamwalker32m','upstreamSha256':original['sha256'],'modified':True,'changes':'Three nearest shadowDistance constants75.0 to32.0 in config.glsl. Middle/far settings and all textures/geometry unchanged. Full source retained in ZIP.'}
j['makeupNear']['upstream']={'url':j['makeupNear'].pop('url'),'version':original['version'],'sha256':original['sha256']}
(A/'downloads.json').write_text(json.dumps(j,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
