import json,urllib.request,urllib.parse,hashlib,traceback
from pathlib import Path
P=Path(__file__).absolute().parents[2];R=P.parent/'research/city-streaming-and-materials-20261018/distant-horizons';O=P/'build/dh-city-pilot/reference';records=[]
try:
 for row in json.loads((R/'core-tree-3.3.3.json').read_text()):
  path=row['path']
  if row['type']!='blob' or not path.startswith('api/src/main/java/') or not any(s in path for s in ['/objects/render/','/interfaces/render/','/objects/data/','/interfaces/data/','/methods/events/sharedParameterObjects/']):continue
  dest=O/path;dest.parent.mkdir(parents=True,exist_ok=True)
  if dest.exists():continue
  old=R/'core-3.3.3'/path
  url='https://gitlab.com/api/v4/projects/jeseibel%2Fdistant-horizons-core/repository/files/'+urllib.parse.quote(path,safe='')+'/raw?ref=b02c66d778beea11a931291815d652cd8abaa7ad'
  data=old.read_bytes() if old.exists() else urllib.request.urlopen(url,timeout=45).read(200000)
  dest.write_bytes(data);records.append({'path':path,'url':url,'bytes':len(data),'sha256':hashlib.sha256(data).hexdigest()})
 (O.parent/'render-api-receipt.json').write_text(json.dumps(records,indent=2))
except Exception:(O.parent/'render-api-error.txt').write_text(traceback.format_exc());raise