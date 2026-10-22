import hashlib,json,traceback,urllib.parse,urllib.request,zipfile
from pathlib import Path
P=Path(__file__).absolute().parents[2];ROOT=P.parent
OUT=P/'build/dh-city-pilot';OUT.mkdir(parents=True,exist_ok=True)
REF=ROOT/'research/city-streaming-and-materials-20261018/distant-horizons'
records=[]
def fetch(url):
    with urllib.request.urlopen(urllib.request.Request(url,headers={'User-Agent':'Dreamwalker-city-pilot/1.0'}),timeout=60) as r: data=r.read(80_000_001)
    if len(data)>80_000_000:raise ValueError('Bounded dependency fetch')
    records.append({'url':url,'bytes':len(data),'sha256':hashlib.sha256(data).hexdigest()});return data
def run():
    versions=json.loads((REF/'versions-1.20.1-fabric.json').read_text());v=next(v for v in versions if v['id']=='6UnEfsRQ');f=next(f for f in v['files'] if f['primary'])
    target=OUT/'dependencies'/f['filename'];target.parent.mkdir(exist_ok=True)
    if not target.exists():target.write_bytes(fetch(f['url']))
    raw=target.read_bytes();assert hashlib.sha512(raw).hexdigest()==f['hashes']['sha512']
    with zipfile.ZipFile(target) as z: metadata=json.loads(z.read('fabric.mod.json'))
    selected=[];tree=json.loads((REF/'core-tree-3.3.3.json').read_text())
    for row in tree:
        path=row['path']
        if row['type']=='blob' and path.startswith('api/src/main/java/') and (('/abstractEvents/' in path and any(k in path for k in ['Chunk','Data','Level','Render','Color','Texture','Init'])) or '/interfaces/factories/' in path or '/interfaces/override/' in path or '/interfaces/world/' in path or path.endswith('/DhApi.java')):selected.append(path)
    api='https://gitlab.com/api/v4/projects/jeseibel%2Fdistant-horizons-core/repository/files/'
    for path in selected:
        dest=OUT/'reference'/path;dest.parent.mkdir(parents=True,exist_ok=True)
        old=REF/'core-3.3.3'/path
        if dest.exists():continue
        dest.write_bytes(old.read_bytes() if old.exists() else fetch(api+urllib.parse.quote(path,safe='')+'/raw?ref=b02c66d778beea11a931291815d652cd8abaa7ad'))
    (OUT/'dependency-receipt.json').write_text(json.dumps({'versionId':v['id'],'version':v['version_number'],'jar':str(target),'sha256':hashlib.sha256(raw).hexdigest(),'sha512':hashlib.sha512(raw).hexdigest(),'modMetadata':metadata,'sourceFiles':selected,'requests':records},indent=2)+'\n')
try:run()
except Exception:(OUT/'fetch-error.txt').write_text(traceback.format_exc());raise
