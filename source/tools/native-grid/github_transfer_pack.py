"""Build bounded deduplicated release volumes, preserving exact source evidence."""
from pathlib import Path
import hashlib,json,os,re,time,zipfile
P=Path(__file__).resolve().parents[2];ROOT=P.parent;R=ROOT/'research';O=ROOT.parent/'DW-native-grid-github-transfer-20261022'
OUT=O/'release-assets';OUT.mkdir(exist_ok=True)
selection=json.loads((R/'transfer/selection.json').read_text(encoding='utf-8'));assert not selection['missing'],selection['missing']
assert not (OUT/'bundle-manifest.json').exists(),'Fresh volume build required'
def sha(path):
    h=hashlib.sha256()
    with path.open('rb') as f:
        for data in iter(lambda:f.read(4*1024**2),b''):h.update(data)
    return h.hexdigest()
strong=re.compile(rb'(?:gh[pousr]_[A-Za-z0-9]{25,}|github_pat_[A-Za-z0-9_]{30,}|-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----)')
text_ext={'.json','.args','.properties','.env','.yml','.yaml','.txt','.ps1','.py','.java','.md','.gradle','.xml','.toml','.ini','.log'}
for row in selection['files']:
    q=Path(row['source'])
    assert q.name.lower() not in {'launcher_accounts.json','accounts.json','credentials.json','cookies.sqlite','id_rsa','id_ed25519','.env','.npmrc','.pypirc'},'Credential store selected: '+str(q)
    if q.suffix.lower() in text_ext and q.stat().st_size<=32*1024**2:
        data=q.read_bytes();assert not strong.search(data),'Credential pattern in '+str(q)
files=[];chunks={};volumes=[];z=None;volume_path=None;start=time.monotonic()
def close():
    global z
    if z:
        z.close();volumes.append({'name':volume_path.name,'bytes':volume_path.stat().st_size,'sha256':sha(volume_path)});z=None
def ensure_volume():
    global z,volume_path
    if z and z.fp.tell()>768*1024**2:close()
    if z is None:
        volume_path=OUT/('resume-data-%03d.zip'%(len(volumes)+1));assert not volume_path.exists()
        z=zipfile.ZipFile(volume_path,'w',compression=zipfile.ZIP_DEFLATED,compresslevel=1,allowZip64=True)
for i,row in enumerate(selection['files'],1):
    q=Path(row['source']);before=q.stat();assert before.st_size==row['bytes'] and before.st_mtime_ns==row['mtimeNs'],'Selection changed: '+str(q)
    h=hashlib.sha256();ids=[]
    with q.open('rb') as f:
        for data in iter(lambda:f.read(32*1024**2),b''):
            h.update(data);key=hashlib.sha256(data).hexdigest();ids.append(key)
            if key not in chunks:
                ensure_volume();z.writestr('blobs/'+key,data);chunks[key]={'bytes':len(data),'volume':volume_path.name}
    after=q.stat();assert before.st_size==after.st_size and before.st_mtime_ns==after.st_mtime_ns,'File changed during capture: '+str(q)
    files.append({'path':row['path'],'bytes':row['bytes'],'mtimeNs':row['mtimeNs'],'sha256':h.hexdigest(),'chunks':ids})
    if i%1000==0:
        progress={'status':'PACKING','filesDone':i,'filesTotal':len(selection['files']),'uniqueChunks':len(chunks),'closedVolumes':len(volumes),'seconds':time.monotonic()-start}
        (OUT/'pack-progress.json').write_text(json.dumps(progress),encoding='utf-8');print(json.dumps(progress),flush=True)
close()
manifest={'schemaVersion':1,'kind':'DREAMWALKER_RESUME_CHECKPOINT_NOT_FINAL_READY','sourceUserRoot':selection['sourceUserRoot'],'createdUtc':time.strftime('%Y-%m-%dT%H:%M:%SZ',time.gmtime()),'logicalBytes':sum(x['bytes'] for x in files),'uniqueChunkBytes':sum(x['bytes'] for x in chunks.values()),'fileCount':len(files),'volumes':volumes,'chunks':chunks,'files':files,'credentialsIncluded':False,'newMinecraftRuns':0,'worldWrites':0,'sourceSelectionSha256':sha(R/'transfer/selection.json')}
(OUT/'bundle-manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,separators=(',',':')),encoding='utf-8')
(OUT/'SHA256SUMS.txt').write_text('\n'.join(v['sha256']+'  '+v['name'] for v in volumes)+'\n'+sha(OUT/'bundle-manifest.json')+'  bundle-manifest.json\n',encoding='utf-8')
receipt={'status':'PACKED','fileCount':len(files),'logicalBytes':manifest['logicalBytes'],'uniqueBytes':manifest['uniqueChunkBytes'],'packedBytes':sum(v['bytes'] for v in volumes),'volumes':len(volumes),'seconds':time.monotonic()-start,'manifestSha256':sha(OUT/'bundle-manifest.json')}
(OUT/'pack-receipt.json').write_text(json.dumps(receipt,indent=2),encoding='utf-8');print(json.dumps(receipt),flush=True)
