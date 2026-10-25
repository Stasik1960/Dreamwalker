import hashlib,json,urllib.request
from pathlib import Path
P=Path(__file__).resolve().parents[2];T=P.parent.parent/'DW-visual-physics-20261024';out=T/'addons/licenses';out.mkdir(exist_ok=True)
urls={'MakeUp-LGPL-3.0.txt':'https://raw.githubusercontent.com/javiergcim/MakeUpUltraFast/master/LICENSE','GPL-3.0.txt':'https://www.gnu.org/licenses/gpl-3.0.txt'};manifest={}
for name,url in urls.items():
 data=urllib.request.urlopen(urllib.request.Request(url,headers={'User-Agent':'Dreamwalker-Distribution'}),timeout=40).read();assert 5000<len(data)<100000
 assert (b'LESSER GENERAL PUBLIC LICENSE' if name.startswith('MakeUp') else b'GNU GENERAL PUBLIC LICENSE') in data
 target=out/name
 if target.exists():assert target.read_bytes()==data
 else:target.write_bytes(data)
 manifest[name]={'url':url,'sha256':hashlib.sha256(data).hexdigest(),'bytes':len(data)}
(out/'SOURCES.json').write_text(json.dumps(manifest,indent=2)+'\n',encoding='utf-8')
