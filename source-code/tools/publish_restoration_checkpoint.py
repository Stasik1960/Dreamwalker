"""Incremental source checkpoint and immutable backup of the user-edited world."""
import hashlib,json,os,shutil,subprocess,sys,urllib.request,urllib.parse,urllib.error
from pathlib import Path
P=Path(__file__).resolve().parents[1];D=P/'restoration-evidence';R=Path('C:/Users/vakir/.codex/worktrees/3f07/DW/audits/Dreamwalker-BB/2026-10-25-visual-diagnostics/github-publication');BR='codex/city-restoration-20261025';REPO='Stasik1960/Dreamwalker';env={**os.environ,'GIT_TERMINAL_PROMPT':'0','GCM_INTERACTIVE':'never'}
def git(*args):
 r=subprocess.run(['git',*args],cwd=R,env=env,capture_output=True,text=True,encoding='utf-8',errors='replace',timeout=180)
 if r.returncode:raise RuntimeError('Git '+args[0]+': '+r.stderr[-800:])
 return r.stdout.strip()
out=R/'diagnostics/2026-10-25-city-restoration';out.mkdir(parents=True,exist_ok=True);paths=[]
for f in D.iterdir():
 if f.is_file() and f.suffix in ('.json','.txt','.md','.gz') and f.stat().st_size<3000000:
  shutil.copy2(f,out/f.name);paths.append(str((out/f.name).relative_to(R)))
owned=['gradle.properties','build.gradle','src/architecture/java/dev/dreamwalker/bloodbornedw/composite/NativePlayerBreak.java','src/architecture/java/dev/dreamwalker/bloodbornedw/composite/CompositeRuntime.java','src/rp/java/dev/dreamwalker/bloodbornerp/object/RpObjectSelection.java']
if (D/'source-files.json').exists():owned+=json.loads((D/'source-files.json').read_text(encoding='utf-8'))
owned+=[str(f.relative_to(P)).replace('\\','/') for f in (P/'src/restorationqa').rglob('*') if f.is_file()]
owned+=[str(f.relative_to(P)).replace('\\','/') for f in (P/'tools').glob('*restor*.py')]
for rel in sorted(set(owned)):
 f=P/rel;assert f.is_file() and f.stat().st_size<99000000
 target=R/'source-code'/rel;target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(f,target);paths.append(str(target.relative_to(R)))
git('add','--',*paths)
if git('diff','--cached','--name-only'):git('commit','-m','Checkpoint user map restoration work and targeted regression QA')
commit=git('rev-parse','HEAD');git('push','origin','HEAD:refs/heads/'+BR);assert git('ls-remote','--heads','origin',BR).split()[0]==commit
(D/'publication.json').write_text(json.dumps({'status':'WORK_IN_PROGRESS','branch':BR,'commit':commit},indent=2),encoding='utf-8')
if '--backup' not in sys.argv:sys.exit(0)
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
tag='native-grid-city-restoration-backup-20261025';release=api('GET','/repos/'+REPO+'/releases/tags/'+tag,missing=True)
if release is None:release=api('POST','/repos/'+REPO+'/releases',{'tag_name':tag,'target_commitish':commit,'name':'User bloodborne map — backup before restoration','body':'Original user-edited Desktop/bloodborne, before any restoration. Work in progress: see branch '+BR+'. This backup is not the repaired deliverable.','draft':False,'prerelease':True,'make_latest':'false'})
assets={a['name']:a for a in release['assets']};receipt={'releaseId':release['id'],'url':release['html_url'],'assets':[]}
for name in ('bloodborne-before-restoration.zip','world-before.json'):
 p=D/name;h=hashlib.sha256()
 with p.open('rb') as f:
  for b in iter(lambda:f.read(1<<20),b''):h.update(b)
 a=assets.get(name)
 if a is None:
  with p.open('rb') as f:
   url=release['upload_url'].split('{')[0]+'?name='+urllib.parse.quote(name)
   headers={'Authorization':'Bearer '+token,'User-Agent':'Dreamwalker-Restoration','Content-Type':'application/octet-stream','Content-Length':str(p.stat().st_size)}
   with http.open(urllib.request.Request(url,data=f,headers=headers,method='POST'),timeout=600) as response:a=json.load(response)
 assert a['size']==p.stat().st_size and a.get('digest')=='sha256:'+h.hexdigest()
 receipt['assets'].append({'name':name,'sha256':h.hexdigest(),'url':a['browser_download_url']})
 (D/'remote-backup.json').write_text(json.dumps(receipt,ensure_ascii=False,indent=2),encoding='utf-8')
