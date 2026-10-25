"""Publish tested assets to a new GitHub release, preserving previous releases."""
import hashlib,json,os,subprocess,urllib.request,urllib.parse,urllib.error
from pathlib import Path
P=Path(__file__).resolve().parents[1];B=P/'utility-release';A=B/'assets';R=Path('C:/Users/vakir/.codex/worktrees/3f07/DW/audits/Dreamwalker-BB/2026-10-25-visual-diagnostics/github-publication');REPO='Stasik1960/Dreamwalker';ENV={**os.environ,'GIT_TERMINAL_PROMPT':'0','GCM_INTERACTIVE':'never'}
def sha(p):
 h=hashlib.sha256()
 with p.open('rb') as f:
  for data in iter(lambda:f.read(1<<20),b''):h.update(data)
 return h.hexdigest()
def git(*args):
 p=subprocess.run(['git',*args],cwd=R,env=ENV,capture_output=True,text=True,encoding='utf-8',errors='replace',timeout=180)
 if p.returncode:raise RuntimeError('Git '+args[0]+' failed: '+p.stderr[-500:])
 return p.stdout.strip()
manifest=json.loads((B/'ASSETS.json').read_text(encoding='utf-8'));assert manifest['status']=='VERIFIED_LOCAL_RELEASE_ASSETS';TAG=manifest['tag'];BR=manifest['branch'];assert json.loads((P/'utility-evidence/acceptance.json').read_text())['status']=='PASS'
for row in manifest['assets']:
 assert Path(row['name']).name==row['name'];p=A/row['name'];assert p.stat().st_size==row['bytes'] and sha(p)==row['sha256']
checkpoint=json.loads((P/'utility-evidence/publication.json').read_text());commit=checkpoint['commit'];assert checkpoint['status']=='READY';assert git('rev-parse','HEAD')==commit and not git('status','--porcelain');assert git('ls-remote','--heads','origin',BR).split()[0]==commit
assert (R/'diagnostics/2026-10-25-utility-blocks/ASSETS.json').read_bytes()==(B/'ASSETS.json').read_bytes()
tag=git('ls-remote','--tags','origin','refs/tags/'+TAG)
if not tag:git('tag',TAG,commit);git('push','origin','refs/tags/'+TAG)
else:assert tag.split()[0]==commit,'Do not replace an existing tag'
filled=subprocess.run(['git','credential','fill'],input='protocol=https\nhost=github.com\n\n',capture_output=True,text=True,env=ENV,timeout=30);token=dict(s.split('=',1) for s in filled.stdout.splitlines() if '=' in s).get('password');assert token,'GitHub credential unavailable'
class NoRedirect(urllib.request.HTTPRedirectHandler):
 def redirect_request(self,*args,**kwargs):raise RuntimeError('Refusing authenticated redirect')
HTTP=urllib.request.build_opener(NoRedirect())
def api(method,path,value=None,allow404=False):
 headers={'Authorization':'Bearer '+token,'Accept':'application/vnd.github+json','User-Agent':'Dreamwalker-Fix-Delivery','X-GitHub-Api-Version':'2022-11-28'};data=None
 if value is not None:data=json.dumps(value,ensure_ascii=False).encode();headers['Content-Type']='application/json'
 try:
  with HTTP.open(urllib.request.Request('https://api.github.com'+path,data=data,headers=headers,method=method),timeout=90) as response:return json.load(response)
 except urllib.error.HTTPError as e:
  if allow404 and e.code==404:return None
  raise RuntimeError('GitHub HTTP '+str(e.code)+' during '+method) from None
notes=(B/'release-notes.md').read_text(encoding='utf-8');release=api('GET','/repos/'+REPO+'/releases/tags/'+TAG,allow404=True)
if release is None:release=api('POST','/repos/'+REPO+'/releases',{'tag_name':TAG,'target_commitish':commit,'name':'Dreamwalker visual-physics.4 — служебные блоки и декоративная вода','body':notes,'draft':True,'prerelease':False,'make_latest':'false'})
assert release['tag_name']==TAG and release['target_commitish']==commit and release['body']==notes
receipt={'status':'DRAFT_OR_RECOVERED','releaseId':release['id'],'commit':commit,'tag':TAG,'branch':BR,'url':'https://github.com/'+REPO+'/releases/tag/'+TAG};(B/'remote.json').write_text(json.dumps(receipt,ensure_ascii=False,indent=2),encoding='utf-8')
upload=release['upload_url'].split('{',1)[0];assert upload.startswith('https://uploads.github.com/repos/'+REPO+'/releases/'+str(release['id'])+'/assets');existing={r['name']:r for r in release['assets']};verified=[]
for row in manifest['assets']:
 asset=existing.get(row['name'])
 if asset is None:
  assert release['draft'],'Do not append to an already published release';print('Uploading',row['name'],flush=True)
  headers={'Authorization':'Bearer '+token,'Content-Type':'application/octet-stream','Content-Length':str(row['bytes']),'User-Agent':'Dreamwalker-Fix-Delivery','Accept':'application/vnd.github+json'}
  with (A/row['name']).open('rb') as f:
   with HTTP.open(urllib.request.Request(upload+'?name='+urllib.parse.quote(row['name']),data=f,headers=headers,method='POST'),timeout=600) as response:asset=json.load(response)
 assert asset['size']==row['bytes'] and asset.get('digest')=='sha256:'+row['sha256'],'Remote digest differs; do not replace automatically'
 verified.append({**row,'url':asset['browser_download_url'],'remoteDigest':asset['digest']});(B/'upload-progress.json').write_text(json.dumps(verified,ensure_ascii=False,indent=2),encoding='utf-8')
release=api('GET','/repos/'+REPO+'/releases/'+str(release['id']));assert len(release['assets'])==len(verified)
if release['draft']:release=api('PATCH','/repos/'+REPO+'/releases/'+str(release['id']),{'draft':False,'prerelease':False,'make_latest':'true'})
assert not release['draft'];receipt.update({'status':'PUBLISHED_ALL_SHA256_VERIFIED','publishedAt':release['published_at'],'assets':verified});(B/'remote.json').write_text(json.dumps(receipt,ensure_ascii=False,indent=2),encoding='utf-8');print('Published',receipt['url'],flush=True)
