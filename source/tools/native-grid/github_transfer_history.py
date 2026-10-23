"""Append the small historical decision evidence without repacking existing volumes."""
from pathlib import Path
import hashlib,json,os,re,stat,subprocess,time,zipfile
P=Path(__file__).resolve().parents[2];ROOT=P.parent;W=ROOT.parent;U=W.parents[2];O=W/'DW-native-grid-github-transfer-20261022';A=O/'release-assets'
assert U==Path(r'C:\Users\vakir')
manifest_path=A/'bundle-manifest.json';assert manifest_path.exists(),'Wait for the main capture to finish'
manifest=json.loads(manifest_path.read_text(encoding='utf-8'));assert 'supplementalEvidence' not in manifest
before=hashlib.sha256(manifest_path.read_bytes()).hexdigest();existing={x['path'] for x in manifest['files']};stage=O/'history-staging';stage.mkdir(exist_ok=True)
def git_bytes(spec):return subprocess.run(['git','-C',str(W/'DW'),'show',spec],capture_output=True,check=True,creationflags=subprocess.CREATE_NO_WINDOW).stdout
protected=git_bytes('HEAD:releases/Dreamwalker-BB/v12/gallery-manifest.json');expected='c74e8323d6b7106e78b2ae6853750a80497e91af83256bf2460ed6406c605710';assert hashlib.sha256(protected).hexdigest()==expected
protected_path=stage/'gallery-manifest-v12.json';protected_path.write_bytes(protected)
rows=[]
audits=W/'DW/audits/Dreamwalker-BB'
for current,dirs,names in os.walk(audits,followlinks=False):
    dirs[:]=[d for d in dirs if not (os.lstat(Path(current)/d).st_file_attributes & stat.FILE_ATTRIBUTE_REPARSE_POINT)]
    for name in names:
        q=Path(current)/name;rel=q.relative_to(U).as_posix()
        if rel not in existing:rows.append((q,rel))
rows.append((protected_path,'.codex/worktrees/3f07/DW/releases/Dreamwalker-BB/v12/gallery-manifest.json'))
recovered=[];unavailable=[]
sources=json.loads((audits/'2026-10-14-final-city/sources-manifest.json').read_text(encoding='utf-8'))
for item in sources:
    q=Path(item['path'])
    if not q.is_relative_to(W/'DW') or q.suffix.lower() not in {'.json','.txt','.csv','.md'} or item.get('bytes',0)>32*1024**2:continue
    rel=q.relative_to(U).as_posix()
    if rel in existing or any(r[1]==rel for r in rows):continue
    if q.exists():data=q.read_bytes()
    else:
        result=subprocess.run(['git','-C',str(W/'DW'),'show','HEAD:'+q.relative_to(W/'DW').as_posix()],capture_output=True,creationflags=subprocess.CREATE_NO_WINDOW)
        if result.returncode:unavailable.append({'path':str(q),'expectedSha256':item['sha256'],'reason':'Absent locally and in current Git HEAD'});continue
        data=result.stdout
    if hashlib.sha256(data).hexdigest()!=item['sha256']:
        unavailable.append({'path':str(q),'expectedSha256':item['sha256'],'reason':'Current bytes differ from historical audit source hash'});continue
    staged=stage/'audit-sources'/q.relative_to(W/'DW');staged.parent.mkdir(parents=True,exist_ok=True);staged.write_bytes(data);rows.append((staged,rel));recovered.append({'path':rel,'sha256':item['sha256']})
commit='ef5550f6c65d163855c2e16422e1a3adafb37b68'
names=subprocess.run(['git','-C',str(W/'DW'),'ls-tree','-r','--name-only',commit,'--','Bloodborne-Blocks'],capture_output=True,text=True,check=True,creationflags=subprocess.CREATE_NO_WINDOW).stdout.splitlines()
early=[]
for name in names:
    if Path(name).suffix not in {'.java','.gradle','.properties'} or '/build/' in name:continue
    data=git_bytes(commit+':'+name);q=stage/'early'/name;q.parent.mkdir(parents=True,exist_ok=True);q.write_bytes(data)
    target='.codex/worktrees/3f07/DW-native-grid-city-20261022/research/transfer/historical-reference/'+commit+'/'+name
    rows.append((q,target));early.append({'gitPath':name,'sha256':hashlib.sha256(data).hexdigest()})
number=len(manifest['volumes'])+1;volume=A/('resume-data-%03d.zip'%number);assert not volume.exists();added=[]
with zipfile.ZipFile(volume,'w',compression=zipfile.ZIP_DEFLATED,compresslevel=1) as z:
    for q,rel in rows:
        data=q.read_bytes();key=hashlib.sha256(data).hexdigest()
        assert not re.search(rb'(?:gh[pousr]_[A-Za-z0-9]{25,}|github_pat_[A-Za-z0-9_]{30,}|-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----)',data),'Credential pattern in history evidence'
        if data and key not in manifest['chunks']:
            z.writestr('blobs/'+key,data);manifest['chunks'][key]={'bytes':len(data),'volume':volume.name}
        row={'path':rel,'bytes':len(data),'mtimeNs':q.stat().st_mtime_ns,'sha256':key,'chunks':[key] if data else []};manifest['files'].append(row);added.append(row)
v={'name':volume.name,'bytes':volume.stat().st_size,'sha256':hashlib.sha256(volume.read_bytes()).hexdigest()};manifest['volumes'].append(v)
manifest['fileCount']=len(manifest['files']);manifest['logicalBytes']=sum(x['bytes'] for x in manifest['files']);manifest['uniqueChunkBytes']=sum(x['bytes'] for x in manifest['chunks'].values())
manifest['supplementalEvidence']={'parentManifestSha256':before,'reason':'Previously agreed assembly decisions and exact historic v12 evidence','addedFiles':len(added),'auditRoot':str(audits),'historicalV12ManifestSha256':expected,'recoveredExactAuditSources':recovered,'unavailableHistoricalSources':unavailable,'earlyNativePrincipleCommit':commit,'earlyFiles':early}
manifest_path.write_text(json.dumps(manifest,ensure_ascii=False,separators=(',',':')),encoding='utf-8')
after=hashlib.sha256(manifest_path.read_bytes()).hexdigest();(A/'SHA256SUMS.txt').write_text('\n'.join(x['sha256']+'  '+x['name'] for x in manifest['volumes'])+'\n'+after+'  bundle-manifest.json\n',encoding='utf-8')
receipt={'status':'PASS_HISTORY_APPENDED_NO_EXISTING_PAYLOAD_CHANGE','addedFiles':len(added),'volume':v,'parentManifestSha256':before,'manifestSha256':after,'historicV12EvidenceSha256':expected,'earlyReferenceFiles':len(early)}
(O/'history-append-receipt.json').write_text(json.dumps(receipt,indent=2),encoding='utf-8');(O/'handoff/gallery-manifest-v12.json').write_bytes(protected);print(json.dumps(receipt),flush=True)
