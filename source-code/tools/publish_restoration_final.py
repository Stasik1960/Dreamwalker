"""Publish verified artifacts; incomplete uploads remain an unpublished draft."""
import hashlib,json,os,subprocess,sys,urllib.request,urllib.parse,urllib.error
from pathlib import Path
P=Path(__file__).resolve().parents[1];D=P/'restoration-evidence';O=D/'final-artifacts';REPO='Stasik1960/Dreamwalker';TAG='native-grid-city-restoration-20261025'
manifest=json.loads((D/'artifacts.json').read_text());assert manifest['status']=='PREPARED'
assert json.loads((D/'ready-to-release.json').read_text())['status']=='READY'
env={**os.environ,'GIT_TERMINAL_PROMPT':'0','GCM_INTERACTIVE':'never'}
r=subprocess.run([sys.executable,'-X','utf8','-B',str(P/'tools/publish_restoration_checkpoint.py'),'--final'],env=env,cwd=P,capture_output=True,timeout=240);assert r.returncode==0,'Source checkpoint failed'
commit=json.loads((D/'publication.json').read_text())['commit']
filled=subprocess.run(['git','credential','fill'],input='protocol=https\nhost=github.com\n\n',capture_output=True,text=True,env=env,timeout=30);token=dict(s.split('=',1) for s in filled.stdout.splitlines() if '=' in s).get('password');assert token
class NoRedirect(urllib.request.HTTPRedirectHandler):
 def redirect_request(self,*args,**kwargs):raise RuntimeError('Authenticated redirects forbidden')
http=urllib.request.build_opener(NoRedirect())
def api(method,path,body=None,missing=False):
 data=None if body is None else json.dumps(body,ensure_ascii=False).encode();headers={'Authorization':'Bearer '+token,'Accept':'application/vnd.github+json','User-Agent':'Dreamwalker-Restoration','Content-Type':'application/json'}
 try:
  with http.open(urllib.request.Request('https://api.github.com'+path,data=data,headers=headers,method=method),timeout=90) as r:return json.load(r)
 except urllib.error.HTTPError as e:
  if missing and e.code==404:return None
  raise RuntimeError('GitHub HTTP '+str(e.code)) from None
release=api('GET','/repos/'+REPO+'/releases/tags/'+TAG,missing=True)
body=(D/'REPORT-RU.md').read_text(encoding='utf-8')+'\n\nПолные исходники, карта пользователя, журнал решений и инструкция продолжения приложены. SHA256 указан в SHA256SUMS.txt. Резервная карта до исправлений: отдельный prerelease restoration-backup.\n'
if release is None:release=api('POST','/repos/'+REPO+'/releases',{'tag_name':TAG,'target_commitish':commit,'name':'Dreamwalker city restoration — visual-physics.6','body':body,'draft':True,'prerelease':False,'make_latest':'false'})
assets={a['name']:a for a in release['assets']};receipt={'status':'UPLOADING','url':release['html_url'],'releaseId':release['id'],'commit':commit,'assets':[]}
for row in manifest['artifacts']:
 p=O/row['name'];h=hashlib.sha256()
 with p.open('rb') as f:
  for b in iter(lambda:f.read(1<<20),b''):h.update(b)
 assert h.hexdigest()==row['sha256'] and p.stat().st_size==row['bytes']
 a=assets.get(p.name)
 if a is None:
  with p.open('rb') as f:
   url=release['upload_url'].split('{')[0]+'?name='+urllib.parse.quote(p.name);headers={'Authorization':'Bearer '+token,'User-Agent':'Dreamwalker-Restoration','Content-Type':'application/octet-stream','Content-Length':str(p.stat().st_size)}
   with http.open(urllib.request.Request(url,data=f,headers=headers,method='POST'),timeout=900) as r:a=json.load(r)
 assert a['size']==p.stat().st_size and a.get('digest')=='sha256:'+h.hexdigest(),p.name
 receipt['assets'].append({'name':p.name,'sha256':h.hexdigest(),'bytes':p.stat().st_size,'url':a['browser_download_url']});(D/'final-release.json').write_text(json.dumps(receipt,ensure_ascii=False,indent=2),encoding='utf-8')
release=api('PATCH','/repos/'+REPO+'/releases/'+str(release['id']),{'draft':False,'prerelease':False,'make_latest':'true','body':body})
assert not release['draft'];receipt['status']='PUBLISHED';receipt['url']=release['html_url'];(D/'final-release.json').write_text(json.dumps(receipt,ensure_ascii=False,indent=2),encoding='utf-8')
