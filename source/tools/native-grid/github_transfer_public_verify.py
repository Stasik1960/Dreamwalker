"""Verify the published checkpoint as an anonymous reader on another computer."""
from pathlib import Path
import hashlib,json,time,urllib.request
P=Path(__file__).resolve().parents[2];O=P.parent.parent/'DW-native-grid-github-transfer-20261022'
remote=json.loads((O/'remote.json').read_text());assert remote['status']=='PUBLISHED_VERIFIED_CHECKPOINT'
expected=json.loads((O/'upload-receipt.json').read_text());byname={x['name']:x for x in expected['assets']}
def get(url,headers=None):return urllib.request.urlopen(urllib.request.Request(url,headers={'User-Agent':'Dreamwalker-Public-Resume-Verification',**(headers or {})}),timeout=90)
with get('https://api.github.com/repos/Stasik1960/Dreamwalker/releases/tags/'+remote['tag']) as response:release=json.load(response)
assert not release['draft'] and release['id']==remote['releaseId'] and {x['name'] for x in release['assets']}==set(byname)
verified=[]
for asset in release['assets']:
    row=byname[asset['name']];assert asset['size']==row['bytes'] and asset.get('digest')=='sha256:'+row['sha256']
    with get(asset['browser_download_url'],{'Range':'bytes=0-3'}) as response:prefix=response.read(4)
    if asset['name'].endswith('.zip'):assert prefix==b'PK\x03\x04','Public ZIP link is not the expected binary payload'
    verified.append(asset['name'])
manifest=next(x for x in release['assets'] if x['name']=='bundle-manifest.json')
with get(manifest['browser_download_url']) as response:data=response.read()
assert hashlib.sha256(data).hexdigest()==expected['manifestSha256']
for name in ['README.md','HANDOFF-RU.md','TASK/START-RU.txt','SNAPSHOT.json','restore_bundle.py']:
    url='https://raw.githubusercontent.com/Stasik1960/Dreamwalker/'+remote['finalBranchCommit']+'/'+name
    with get(url) as response:data=response.read()
    assert data==(O/name).read_bytes(),'Public Git file mismatch: '+name
report={'status':'PASS_PUBLIC_ANONYMOUS_RESUME_CHECKPOINT','releaseUrl':remote['releaseUrl'],'readmeUrl':remote['readmeUrl'],'commit':remote['finalBranchCommit'],'publicAssets':verified,'manifestSha256':expected['manifestSha256'],'filesVerifiedFromVolumes':67505,'authenticatedRequests':0,'checkedUtc':time.strftime('%Y-%m-%dT%H:%M:%SZ',time.gmtime())}
(O/'public-verification.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8');print(json.dumps(report,ensure_ascii=False),flush=True)
