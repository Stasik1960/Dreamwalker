"""Publish the already accepted local delivery; never replace an older release."""
import argparse,hashlib,json,os,subprocess,urllib.request,urllib.parse,urllib.error
from pathlib import Path
P=Path(__file__).resolve().parents[2];T=P.parent.parent/'DW-visual-physics-20261024';B=T/'publication';REPO='Stasik1960/Dreamwalker';BRANCH='codex/native-visual-physics-20261025';TAG='native-grid-city-visual-physics-20261025'
ENV=dict(os.environ,GIT_TERMINAL_PROMPT='0',GCM_INTERACTIVE='never')
class NoRedirect(urllib.request.HTTPRedirectHandler):
 def redirect_request(self,*args,**kwargs):raise RuntimeError('Authenticated GitHub request redirected; refusing credential forwarding')
HTTP=urllib.request.build_opener(NoRedirect())
def read(path):return json.loads(path.read_text(encoding='utf-8-sig'))
def write(path,value):path.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
def sha(path):
 h=hashlib.sha256()
 with path.open('rb') as f:
  for data in iter(lambda:f.read(1<<20),b''):h.update(data)
 return h.hexdigest()
def git(*args,cwd=None):
 r=subprocess.run(['git',*args],cwd=cwd or B/'repo',env=ENV,text=True,capture_output=True,timeout=1200,creationflags=subprocess.CREATE_NO_WINDOW)
 assert r.returncode==0,'Git '+args[0]+' failed';return r.stdout.strip()
def main():
 a=argparse.ArgumentParser();a.add_argument('mode',choices=('prepare','upload','publish'));mode=a.parse_args().mode
 manifest=read(B/'DELIVERY-MANIFEST.json');ready=read(T/'READY.json');assert manifest['status']=='VERIFIED_LOCAL_RELEASE_ASSETS' and ready['status']=='READY_LOCAL_DELIVERY'
 assert ready['runtimeSha256']==manifest['runtimeSha256'];assert read(T/'desktop-install.json')['status']=='DESKTOP_PAIR_AND_MODPACKS_INSTALLED_ALL_FILES_VERIFIED'
 assert manifest['acceptanceSha256']==sha(T/'final-ready.json') and manifest['desktopReceiptSha256']==sha(T/'desktop-install.json')
 assert read(T/'desktop-install.json')['jarSha256']==ready['runtimeSha256']==read(T/'final-ready.json')['jar']['sha256']
 assert not any(Path(row['name']).name!=row['name'] for row in manifest['assets'])
 for row in manifest['assets']:
  path=B/'assets'/row['name'];assert path.stat().st_size==row['bytes'] and sha(path)==row['sha256'];assert row['bytes']<2*1024**3
 filled=subprocess.run(['git','credential','fill'],input='protocol=https\nhost=github.com\n\n',text=True,capture_output=True,env=ENV,timeout=30,creationflags=subprocess.CREATE_NO_WINDOW)
 credential=dict(line.split('=',1) for line in filled.stdout.splitlines() if '=' in line);token=credential.get('password');assert token,'GitHub credential unavailable'
 def api(method,path,value=None):
  data=None if value is None else json.dumps(value,ensure_ascii=False).encode('utf-8');headers={'Authorization':'Bearer '+token,'Accept':'application/vnd.github+json','User-Agent':'Dreamwalker-Final-Delivery','X-GitHub-Api-Version':'2022-11-28'}
  if data is not None:headers['Content-Type']='application/json'
  try:
   with HTTP.open(urllib.request.Request('https://api.github.com'+path,data=data,headers=headers,method=method),timeout=90) as response:return json.load(response)
  except urllib.error.HTTPError as error:raise RuntimeError('GitHub HTTP '+str(error.code)+' during '+method) from None
 if mode=='prepare':
  assert not (B/'remote.json').exists() and not (B/'repo/.git').exists(),'Inspect previous prepare outcome before retry'
  remote='https://github.com/'+REPO+'.git';assert not git('ls-remote','--heads',remote,'refs/heads/'+BRANCH)
  assert not git('ls-remote','--tags',remote,'refs/tags/'+TAG),'Existing tag must never be reused'
  git('init','--initial-branch='+BRANCH);original=P.parent.parent/'DW'
  for key in ('user.name','user.email'):git('config',key,git('config',key,cwd=original))
  git('config','core.autocrlf','false');git('remote','add','origin',remote)
  git('add','--force','README.md','HANDOFF-RU.txt','REPORT-RU.txt','REPORTS','TASK','source-code','SHA256SUMS.txt','download_release.py','ASSETS.json')
  git('commit','-m','Deliver native visual/physics city, runtime sources and tested renderer settings');commit=git('rev-parse','HEAD');assert not git('status','--porcelain');git('push','--set-upstream','origin',BRANCH);git('tag',TAG,commit);git('push','origin','refs/tags/'+TAG)
  tag_object=api('GET','/repos/'+REPO+'/git/ref/tags/'+TAG)['object'];assert tag_object['type']=='commit' and tag_object['sha']==commit
  notes=(B/'release-notes.md').read_text(encoding='utf-8')
  release=api('POST','/repos/'+REPO+'/releases',{'tag_name':TAG,'target_commitish':commit,'name':'Dreamwalker: готовая карта и visual/physics.1 —25.10.2026','body':notes,'draft':True,'prerelease':False,'make_latest':'false'})
  assert release['tag_name']==TAG and release['target_commitish']==commit and release['body']==notes
  write(B/'remote.json',{'releaseId':release['id'],'commit':commit,'tag':TAG,'branch':BRANCH,'releaseUrl':'https://github.com/'+REPO+'/releases/tag/'+TAG,'status':'DRAFT_LOCAL_ACCEPTED_PAIR','readySha256':sha(T/'READY.json'),'manifestSha256':sha(B/'DELIVERY-MANIFEST.json'),'notesSha256':sha(B/'release-notes.md')})
 elif mode=='upload':
  remote=read(B/'remote.json');verify_binding(remote,api);release=api('GET','/repos/'+REPO+'/releases/'+str(remote['releaseId']));assert release['draft'] and release['tag_name']==TAG and release['target_commitish']==remote['commit'] and release['body']==(B/'release-notes.md').read_text(encoding='utf-8')
  upload_url=release['upload_url'].split('{',1)[0];assert upload_url.startswith('https://uploads.github.com/repos/'+REPO+'/releases/'+str(remote['releaseId'])+'/assets')
  existing={x['name']:x for x in release['assets']};verified=[]
  for row in manifest['assets']:
   asset=existing.get(row['name'])
   if asset is None:
    path=B/'assets'/row['name'];headers={'Authorization':'Bearer '+token,'Accept':'application/vnd.github+json','User-Agent':'Dreamwalker-Final-Delivery','Content-Type':'application/octet-stream','Content-Length':str(row['bytes'])}
    print('Uploading '+row['name']+' ('+str(row['bytes'])+' bytes)',flush=True)
    with path.open('rb') as f:
     request=urllib.request.Request(upload_url+'?name='+urllib.parse.quote(row['name']),data=f,headers=headers,method='POST')
     with HTTP.open(request,timeout=600) as response:asset=json.load(response)
   assert asset['size']==row['bytes'] and asset.get('digest')=='sha256:'+row['sha256'],'Remote asset hash mismatch; do not replace automatically'
   verified.append({**row,'id':asset['id'],'remoteDigest':asset['digest'],'url':asset['browser_download_url']});write(B/'upload-progress.json',{'status':'UPLOADING_VERIFIED','complete':len(verified),'total':len(manifest['assets']),'assets':verified})
  write(B/'upload-receipt.json',{'status':'ALL_REMOTE_SHA256_VERIFIED','assets':verified})
 else:
  remote=read(B/'remote.json');verify_binding(remote,api);uploaded=read(B/'upload-receipt.json');assert uploaded['status']=='ALL_REMOTE_SHA256_VERIFIED'
  release=api('GET','/repos/'+REPO+'/releases/'+str(remote['releaseId']));assert release['tag_name']==TAG and release['target_commitish']==remote['commit'] and release['body']==(B/'release-notes.md').read_text(encoding='utf-8')
  actual={x['name']:x for x in release['assets']};assert len(actual)==len(manifest['assets'])
  for row in manifest['assets']:assert actual[row['name']]['size']==row['bytes'] and actual[row['name']].get('digest')=='sha256:'+row['sha256']
  if release['draft']:
   release=api('PATCH','/repos/'+REPO+'/releases/'+str(remote['releaseId']),{'draft':False,'prerelease':False,'make_latest':'true','body':(B/'release-notes.md').read_text(encoding='utf-8')})
  assert not release['draft'];remote.update({'status':'PUBLISHED_READY_DELIVERY_ALL_SHA256_VERIFIED','publishedAt':release['published_at'],'assetCount':len(actual),'manifestSha256':sha(B/'DELIVERY-MANIFEST.json')});write(B/'remote.json',remote)
  print(json.dumps(remote,ensure_ascii=False),flush=True)
def verify_binding(remote,api):
 assert remote['tag']==TAG and remote['branch']==BRANCH and git('rev-parse','HEAD')==remote['commit'] and not git('status','--porcelain')
 assert git('symbolic-ref','--short','HEAD')==BRANCH
 assert git('remote','get-url','--all','origin')==git('remote','get-url','--push','--all','origin')=='https://github.com/'+REPO+'.git'
 for kind,name in (('heads',BRANCH),('tags',TAG)):
  obj=api('GET','/repos/'+REPO+'/git/ref/'+kind+'/'+name)['object'];assert obj['type']=='commit' and obj['sha']==remote['commit']
 assert remote['readySha256']==sha(T/'READY.json') and remote['manifestSha256']==sha(B/'DELIVERY-MANIFEST.json') and remote['notesSha256']==sha(B/'release-notes.md')
if __name__=='__main__':main()
