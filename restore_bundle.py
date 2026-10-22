"""Download, verify and restore the exact content-addressed Dreamwalker checkpoint."""
from pathlib import Path,PurePosixPath
import argparse,hashlib,json,os,sys,urllib.request,zipfile
RELEASE='https://github.com/Stasik1960/Dreamwalker/releases/download/native-grid-city-resume-20261022/'
def digest(path):
    h=hashlib.sha256()
    with path.open('rb') as f:
        for data in iter(lambda:f.read(4*1024**2),b''):h.update(data)
    return h.hexdigest()
def download(name,folder,expected=None):
    out=folder/name
    if out.is_file() and (expected is None or digest(out)==expected):return out
    temp=out.with_suffix(out.suffix+'.part')
    req=urllib.request.Request(RELEASE+name,headers={'User-Agent':'Dreamwalker-Resume-Restore'})
    with urllib.request.urlopen(req,timeout=90) as response,temp.open('wb') as f:
        for data in iter(lambda:response.read(4*1024**2),b''):f.write(data)
    if expected is not None and digest(temp)!=expected:raise RuntimeError('Download SHA mismatch: '+name)
    temp.replace(out);return out
def main():
    ap=argparse.ArgumentParser();ap.add_argument('--assets',type=Path,required=True);ap.add_argument('--target',type=Path);ap.add_argument('--verify-only',action='store_true');ap.add_argument('--download',action='store_true');a=ap.parse_args()
    assets=a.assets.resolve();assets.mkdir(parents=True,exist_ok=True)
    if a.download:download('bundle-manifest.json',assets)
    manifest=json.loads((assets/'bundle-manifest.json').read_text(encoding='utf-8'))
    if not a.verify_only and a.target is None:ap.error('--target or --verify-only required')
    target=a.target.resolve() if a.target else None
    for row in manifest['volumes']:
        if a.download:download(row['name'],assets,row['sha256'])
        path=assets/row['name'];assert path.stat().st_size==row['bytes'] and digest(path)==row['sha256'],'Volume mismatch: '+row['name']
        print('Verified volume',row['name'],flush=True)
    archives={v['name']:zipfile.ZipFile(assets/v['name']) for v in manifest['volumes']}
    try:
        for i,row in enumerate(manifest['files'],1):
            rel=PurePosixPath(row['path']);assert not rel.is_absolute() and '..' not in rel.parts and all(':' not in p for p in rel.parts),'Unsafe restore path'
            out=target.joinpath(*rel.parts) if target else None
            if out and out.exists():
                assert out.is_file() and out.stat().st_size==row['bytes'] and digest(out)==row['sha256'],'Refusing to overwrite differing file: '+str(out)
                continue
            h=hashlib.sha256();count=0
            if out:
                out.parent.mkdir(parents=True,exist_ok=True);temp=out.with_name(out.name+'.resume-part');f=temp.open('wb')
            else:f=None
            try:
                for chunk_id in row['chunks']:
                    chunk=manifest['chunks'][chunk_id];data=archives[chunk['volume']].read('blobs/'+chunk_id)
                    assert len(data)==chunk['bytes'] and hashlib.sha256(data).hexdigest()==chunk_id,'Chunk mismatch'
                    count+=len(data);h.update(data)
                    if f:f.write(data)
            finally:
                if f:f.close()
            assert count==row['bytes'] and h.hexdigest()==row['sha256'],'File mismatch: '+row['path']
            if out:temp.replace(out);os.utime(out,ns=(row['mtimeNs'],row['mtimeNs']))
            if i%1000==0:print(('Verified' if a.verify_only else 'Restored'),i,'/',len(manifest['files']),flush=True)
    finally:
        for z in archives.values():z.close()
    report={'status':'PASS_ALL_FILES_VERIFIED' if a.verify_only else 'PASS_RESTORED','files':len(manifest['files']),'logicalBytes':manifest['logicalBytes'],'target':str(target) if target else None,'note':'Historical absolute paths and SHA provenance are unchanged. Read HANDOFF-RU before launching any project process.'}
    (assets/('verification.json' if a.verify_only else 'restoration.json')).write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8');print(json.dumps(report,ensure_ascii=False))
if __name__=='__main__':main()
