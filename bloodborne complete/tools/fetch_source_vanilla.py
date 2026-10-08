"""Acquire original renderer resources from Mojang's version manifest, verify SHA1."""
import hashlib, json, urllib.request
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
def fetch(url):
    with urllib.request.urlopen(url,timeout=30) as response: return response.read()
def main():
    out=ROOT/'inputs/extracted/vanilla-1.18.2'; out.mkdir(parents=True,exist_ok=True)
    manifest_url='https://piston-meta.mojang.com/mc/game/version_manifest_v2.json'
    manifest=json.loads(fetch(manifest_url))
    version=next(v for v in manifest['versions'] if v['id']=='1.18.2')
    metadata=fetch(version['url'])
    if hashlib.sha1(metadata).hexdigest()!=version['sha1']: raise ValueError('version manifest SHA1 mismatch')
    (out/'version.json').write_bytes(metadata)
    config=json.loads(metadata); provenance={'version':'1.18.2','manifest_url':manifest_url,'version_url':version['url'],'version_sha1':version['sha1'],'downloads':{}}
    for name in ['client','client_mappings']:
        spec=config['downloads'][name]; dest=out/('client.jar' if name=='client' else 'client-mappings.txt')
        data=dest.read_bytes() if dest.exists() else fetch(spec['url'])
        if hashlib.sha1(data).hexdigest()!=spec['sha1']: raise ValueError(name+' SHA1 mismatch')
        dest.write_bytes(data); provenance['downloads'][name]={**spec,'local_path':str(dest),'sha256':hashlib.sha256(data).hexdigest()}
    (ROOT/'reports/SOURCE_VANILLA.json').write_text(json.dumps(provenance,indent=2)+'\n',encoding='utf-8')
    print('Verified original vanilla 1.18.2 client assets and official mappings.')
if __name__=='__main__':main()
