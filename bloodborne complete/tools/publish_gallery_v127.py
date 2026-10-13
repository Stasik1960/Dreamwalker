"""Publish the accepted gallery separately; never replace the V12.7 mod release."""
import argparse, json, subprocess, urllib.parse
from pathlib import Path
from publish_v127_release import API, credentials, git, public_sha
from package_v127_release import ROOT,REPO,sha,read
p=argparse.ArgumentParser();p.add_argument('--package',type=Path,required=True);p.add_argument('--publish',action='store_true');a=p.parse_args()
package=a.package.resolve();assert package.is_relative_to(REPO/'releases/Dreamwalker-BB')
m=read(package/'gallery-release-manifest.json');assert m['status']=='PASS_GALLERY_ACCEPTED' and m['productionJarSha256']=='b98c752b797ef07c5acf1d4852bda83eec52717fed798d4ec76860b4f6972850'
for row in m['files']:assert sha(package/row['name'])==row['sha256'] and (package/row['name']).stat().st_size==row['bytes']
target=git('rev-parse','HEAD');tag='bb-v12.7-gallery';assert git('branch','--show-current')=='codex/bb-v12-tool'
assert not git('status','--porcelain','--untracked-files=no')
assets=sorted(p for p in package.iterdir() if p.is_file());value={'status':'PASS_GALLERY_PUBLICATION_PREFLIGHT','targetCommit':target,'tag':tag,'assets':[{'name':p.name,'bytes':p.stat().st_size,'sha256':sha(p)} for p in assets]}
if not a.publish:print(json.dumps(value));raise SystemExit(0)
old=subprocess.run(['git','rev-parse','--verify','refs/tags/'+tag+'^{}'],cwd=REPO,text=True,capture_output=True)
if old.returncode==0:assert old.stdout.strip()==target
else:subprocess.run(['git','tag','-a',tag,target,'-m','Verified V12.7 catalogue gallery with WorldEdit and native measurements'],cwd=REPO,check=True)
subprocess.run(['git','push','origin','HEAD:refs/heads/codex/bb-v12-tool','refs/tags/'+tag],cwd=REPO,check=True)
assert git('ls-remote','origin','refs/heads/codex/bb-v12-tool').split()[0]==target
api=API(credentials());prefix='/repos/Stasik1960/Dreamwalker';release=api.request('GET',prefix+'/releases/tags/'+tag,allow404=True)
marker='Gallery ZIP SHA-256: `'+m['galleryZipSha256']+'`'
if release is None:
    body=marker+'\n\n'+(package/'REPORT-RU.txt').read_text(encoding='utf-8-sig')+'\n\n[Готовый мод V12.7](https://github.com/Stasik1960/Dreamwalker/releases/tag/bb-v12.7-final)'
    release=api.request('POST',prefix+'/releases',{'tag_name':tag,'target_commitish':target,'name':'Dreamwalker BB V12.7 — актуальная галерея','body':body,'draft':True,'prerelease':False})
assert marker in release['body']
verified=[]
for path in assets:
    matches=[x for x in api.request('GET',prefix+f"/releases/{release['id']}/assets?per_page=100") if x['name']==path.name];assert len(matches)<=1
    if matches:asset=matches[0]
    else:
        print(json.dumps({'status':'UPLOADING_GALLERY_ASSET','name':path.name}),flush=True)
        asset=api.upload(release['upload_url'].split('{',1)[0],path)
    digest=sha(path);assert asset['state']=='uploaded' and asset['size']==path.stat().st_size and asset['digest']=='sha256:'+digest
    verified.append({'name':path.name,'sha256':digest,'bytes':path.stat().st_size,'url':asset['browser_download_url']})
if release['draft']:release=api.request('PATCH',prefix+f"/releases/{release['id']}",{'draft':False})
for row in verified:
    digest,size=public_sha(row['url']);assert digest==row['sha256'] and size==row['bytes'];row['publicDownloadSHA256']='PASS';print(json.dumps({'status':'PASS_PUBLIC_GALLERY_DOWNLOAD','name':row['name']}),flush=True)
value.update(status='PASS_GALLERY_PUBLISHED_AND_PUBLIC_DOWNLOAD_VERIFIED',releaseUrl=release['html_url'],assets=verified)
(ROOT/'build/v127-gallery-publication.json').write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n',encoding='utf8');print(json.dumps({'status':value['status'],'releaseUrl':value['releaseUrl']}))
