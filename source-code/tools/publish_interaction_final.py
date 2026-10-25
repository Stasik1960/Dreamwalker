"""Upload a separate release as a draft, then publish after every digest matches."""
import hashlib,json,os,subprocess,sys,urllib.request,urllib.parse,urllib.error
from pathlib import Path
P=Path(__file__).resolve().parents[1];D=P/'interaction-evidence';O=D/'final-artifacts';REPO='Stasik1960/Dreamwalker';TAG='native-grid-city-interactions-20261026'
load=lambda p:json.loads(p.read_text(encoding='utf-8'))
manifest=load(D/'artifacts.json');assert manifest['status']=='PREPARED';assert load(D/'acceptance.json')['status']=='PASS';assert load(D/'archive-integrity.json')['status']=='PASS'
env={**os.environ,'GIT_TERMINAL_PROMPT':'0','GCM_INTERACTIVE':'never'}
subprocess.run([sys.executable,'-X','utf8','-B',str(P/'tools/publish_interaction_checkpoint.py')],env=env,cwd=P,check=True,capture_output=True,timeout=240)
commit=load(D/'publication.json')['commit']
filled=subprocess.run(['git','credential','fill'],input='protocol=https\nhost=github.com\n\n',capture_output=True,text=True,env=env,timeout=30);token=dict(s.split('=',1) for s in filled.stdout.splitlines() if '=' in s).get('password');assert token
class NoRedirect(urllib.request.HTTPRedirectHandler):
 def redirect_request(self,*args,**kwargs):raise RuntimeError('Authenticated redirects forbidden')
http=urllib.request.build_opener(NoRedirect())
def api(method,path,body=None,missing=False):
 data=None if body is None else json.dumps(body,ensure_ascii=False).encode();headers={'Authorization':'Bearer '+token,'Accept':'application/vnd.github+json','User-Agent':'Dreamwalker-Interactions','Content-Type':'application/json'}
 try:
  with http.open(urllib.request.Request('https://api.github.com'+path,data=data,headers=headers,method=method),timeout=90) as result:return json.load(result)
 except urllib.error.HTTPError as error:
  if missing and error.code==404:return None
  raise RuntimeError('GitHub HTTP '+str(error.code)) from None
saved=D/'final-release.json';release=None
if saved.exists():release=api('GET','/repos/'+REPO+'/releases/'+str(load(saved)['releaseId']),missing=True)
if release is None:release=api('GET','/repos/'+REPO+'/releases/tags/'+TAG,missing=True)
body=(D/'REPORT-RU.md').read_text(encoding='utf-8')+'\n\nПолная карта, точные исходники, решения и инструкция продолжения приложены. Контрольные суммы — SHA256SUMS.txt. Предыдущий релиз visual-physics.6 сохранён.\n'
if release is None:release=api('POST','/repos/'+REPO+'/releases',{'tag_name':TAG,'target_commitish':commit,'name':'Dreamwalker — интерактивные исправления visual-physics.7','body':body,'draft':True,'prerelease':False,'make_latest':'false'})
assert release['tag_name']==TAG or release['draft'] and release['tag_name'].startswith('untagged-')
assets={row['name']:row for row in release['assets']};receipt={'status':'UPLOADING','tag':TAG,'url':release['html_url'],'releaseId':release['id'],'commit':commit,'assets':[]}
for row in manifest['artifacts']:
 path=O/row['name'];digest=hashlib.sha256(path.read_bytes()).hexdigest();assert digest==row['sha256'] and path.stat().st_size==row['bytes'];asset=assets.get(path.name)
 if asset is None:
  with path.open('rb') as content:
   url=release['upload_url'].split('{')[0]+'?name='+urllib.parse.quote(path.name);headers={'Authorization':'Bearer '+token,'User-Agent':'Dreamwalker-Interactions','Content-Type':'application/octet-stream','Content-Length':str(path.stat().st_size)}
   with http.open(urllib.request.Request(url,data=content,headers=headers,method='POST'),timeout=900) as result:asset=json.load(result)
 assert asset['size']==path.stat().st_size and asset.get('digest')=='sha256:'+digest,path.name
 receipt['assets'].append({'name':path.name,'sha256':digest,'bytes':path.stat().st_size,'url':asset['browser_download_url']});saved.write_text(json.dumps(receipt,ensure_ascii=False,indent=2),encoding='utf-8')
release=api('PATCH','/repos/'+REPO+'/releases/'+str(release['id']),{'tag_name':TAG,'draft':False,'prerelease':False,'make_latest':'true','body':body,'target_commitish':commit})
assert not release['draft'] and release['tag_name']==TAG
links={row['name']:row for row in release['assets']}
for row in receipt['assets']:row['url']=links[row['name']]['browser_download_url'];assert links[row['name']].get('digest')=='sha256:'+row['sha256']
receipt['status']='PUBLISHED';receipt['url']=release['html_url'];saved.write_text(json.dumps(receipt,ensure_ascii=False,indent=2),encoding='utf-8')
