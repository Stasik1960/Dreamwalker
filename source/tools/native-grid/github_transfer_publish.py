"""Publish the authorized resume branch/release; credentials stay only in memory."""
from pathlib import Path
import argparse,hashlib,json,os,shutil,subprocess,time,urllib.request,urllib.error,urllib.parse
from concurrent.futures import ThreadPoolExecutor,as_completed
P=Path(__file__).resolve().parents[2];ROOT=P.parent;O=ROOT.parent/'DW-native-grid-github-transfer-20261022';A=O/'release-assets'
REPO='Stasik1960/Dreamwalker';BRANCH='codex/native-grid-city-resume-20261022';TAG='native-grid-city-resume-20261022'
env=dict(os.environ,GIT_TERMINAL_PROMPT='0',GCM_INTERACTIVE='never')
def write(path,value):path.write_text(json.dumps(value,ensure_ascii=False,indent=2),encoding='utf-8')
def sha(p):
    h=hashlib.sha256()
    with p.open('rb') as f:
        for data in iter(lambda:f.read(4*1024**2),b''):h.update(data)
    return h.hexdigest()
def git(*args,cwd=O):
    r=subprocess.run(['git',*args],cwd=cwd,env=env,text=True,capture_output=True,timeout=900,creationflags=subprocess.CREATE_NO_WINDOW)
    if r.returncode:raise RuntimeError('git '+args[0]+' failed: '+r.stderr[-1500:])
    return r.stdout.strip()
filled=subprocess.run(['git','credential','fill'],input='protocol=https\nhost=github.com\n\n',text=True,capture_output=True,env=env,timeout=30,creationflags=subprocess.CREATE_NO_WINDOW)
credential=dict(line.split('=',1) for line in filled.stdout.splitlines() if '=' in line);token=credential.get('password');assert token,'No local GitHub credential'
def api(method,path,body=None):
    url=path if path.startswith('https://') else 'https://api.github.com'+path
    headers={'Authorization':'Bearer '+token,'Accept':'application/vnd.github+json','User-Agent':'Dreamwalker-Checkpoint-Transfer','X-GitHub-Api-Version':'2022-11-28'}
    data=json.dumps(body).encode() if body is not None else None
    if data is not None:headers['Content-Type']='application/json'
    try:
        with urllib.request.urlopen(urllib.request.Request(url,data=data,headers=headers,method=method),timeout=90) as response:return json.load(response)
    except urllib.error.HTTPError as e:raise RuntimeError('GitHub HTTP '+str(e.code)+' '+e.read(500).decode('utf-8',errors='replace')) from None
ap=argparse.ArgumentParser();ap.add_argument('mode',choices=['prepare','upload','first-volume','closed-volumes','finalize']);mode=ap.parse_args().mode
if mode=='prepare':
    assert not (O/'remote.json').exists(),'Inspect existing remote receipt before retry'
    assert not git('ls-remote','--heads','https://github.com/'+REPO+'.git','refs/heads/'+BRANCH),'Resume branch already exists; do not overwrite'
    git('init','--initial-branch='+BRANCH)
    original=ROOT.parent/'DW';name=git('config','user.name',cwd=original);email=git('config','user.email',cwd=original)
    git('config','user.name',name);git('config','user.email',email);git('config','core.autocrlf','false');git('remote','add','origin','https://github.com/'+REPO+'.git')
    git('add','--force','README.md','HANDOFF-RU.md','AGENTS.md','TASK','handoff','restore_bundle.py','source','.gitignore','git-preparation.json')
    git('commit','-m','Preserve native-grid city sources, task and exact continuation handoff')
    commit=git('rev-parse','HEAD');git('push','--set-upstream','origin',BRANCH)
    release=api('POST','/repos/'+REPO+'/releases',{'tag_name':TAG,'target_commitish':commit,'name':'Native-grid city: полный checkpoint для продолжения — 2026-10-22','body':'Незавершённая работа, не READY. Исходное задание, актуальные исходники, промежуточные PASS/FAIL/OOM, исходная карта blocks.2, текущий пилот, обе SQLite базы с кэшем и журналы. Начать с README и HANDOFF-RU.md ветки '+BRANCH+'. Скачать ВСЕ resume-data ZIP, bundle-manifest.json и SHA256SUMS; restore_bundle.py проверяет и восстанавливает точные байты/mtime. Сначала завершить preserved assemblies и адресный QA двери; затем одна matched pair и полный город. Не конвертировать заново.','draft':True,'prerelease':True,'make_latest':'false'})
    receipt={'status':'DRAFT_CREATED','branch':BRANCH,'commit':commit,'tag':TAG,'releaseId':release['id'],'releaseUrl':'https://github.com/'+REPO+'/releases/tag/'+TAG,'readmeUrl':'https://github.com/'+REPO+'/blob/'+BRANCH+'/README.md'}
    write(O/'remote.json',receipt);print(json.dumps(receipt,ensure_ascii=False),flush=True)
elif mode in ('upload','first-volume','closed-volumes'):
    receipt=json.loads((O/'remote.json').read_text());release=api('GET','/repos/'+REPO+'/releases/'+str(receipt['releaseId']))
    if mode in ('first-volume','closed-volumes'):
        assert (A/'resume-data-002.zip').exists(),'First volume is not yet closed'
        expected=[];number=1
        while (A/('resume-data-%03d.zip'%(number+1))).exists():
            path=A/('resume-data-%03d.zip'%number);expected.append({'name':path.name,'bytes':path.stat().st_size,'sha256':sha(path)});number+=1
            if mode=='first-volume':break
    else:
        manifest=json.loads((A/'bundle-manifest.json').read_text())
        expected=[*manifest['volumes'],{'name':'bundle-manifest.json','bytes':(A/'bundle-manifest.json').stat().st_size,'sha256':sha(A/'bundle-manifest.json')},{'name':'SHA256SUMS.txt','bytes':(A/'SHA256SUMS.txt').stat().st_size,'sha256':sha(A/'SHA256SUMS.txt')}]
    existing={x['name']:x for x in release['assets']};uploaded=[]
    def upload_one(row):
        path=A/row['name'];assert path.stat().st_size==row['bytes'] and sha(path)==row['sha256']
        if row['name'] in existing:
            asset=existing[row['name']];assert asset['size']==row['bytes'] and asset.get('digest')=='sha256:'+row['sha256'],'Existing remote asset differs; refusing overwrite'
        else:
            url=release['upload_url'].split('{',1)[0]+'?name='+urllib.parse.quote(row['name'])
            headers={'Authorization':'Bearer '+token,'Accept':'application/vnd.github+json','User-Agent':'Dreamwalker-Checkpoint-Transfer','Content-Type':'application/octet-stream','Content-Length':str(row['bytes'])}
            print('Uploading '+row['name']+' '+str(row['bytes'])+' bytes',flush=True)
            with path.open('rb') as data:
                req=urllib.request.Request(url,data=data,headers=headers,method='POST')
                with urllib.request.urlopen(req,timeout=180) as response:asset=json.load(response)
            assert asset['size']==row['bytes'] and asset.get('digest')=='sha256:'+row['sha256'],'Remote asset digest mismatch'
        return {'name':row['name'],'bytes':asset['size'],'sha256':row['sha256'],'id':asset['id'],'url':asset['browser_download_url'],'remoteDigest':asset.get('digest')}
    with ThreadPoolExecutor(max_workers=2 if mode=='upload' else 1) as pool:
        for future in as_completed([pool.submit(upload_one,row) for row in expected]):
            uploaded.append(future.result());uploaded.sort(key=lambda x:x['name'])
            write(O/'upload-progress.json',{'status':'UPLOADING','complete':len(uploaded),'total':len(expected),'assets':uploaded});print('Verified remote SHA256 '+uploaded[-1]['name'],flush=True)
    write(O/('upload-'+mode+'-receipt.json' if mode!='upload' else 'upload-receipt.json'),{'status':'PASS_ALL_REMOTE_ASSET_DIGESTS','releaseId':receipt['releaseId'],'assets':uploaded,'manifestSha256':sha(A/'bundle-manifest.json') if mode=='upload' else None})
else:
    receipt=json.loads((O/'remote.json').read_text());uploaded=json.loads((O/'upload-receipt.json').read_text());verified=json.loads((A/'verification.json').read_text())
    assert uploaded['status']=='PASS_ALL_REMOTE_ASSET_DIGESTS' and verified['status']=='PASS_ALL_FILES_VERIFIED'
    body='''Снимок незавершённой работы для продолжения на новом ПК. Это НЕ READY и не готовая оптимизированная карта.

Начать с [README](https://github.com/Stasik1960/Dreamwalker/blob/codex/native-grid-city-resume-20261022/README.md), затем [HANDOFF-RU](https://github.com/Stasik1960/Dreamwalker/blob/codex/native-grid-city-resume-20261022/HANDOFF-RU.md) и [исходного задания](https://github.com/Stasik1960/Dreamwalker/blob/codex/native-grid-city-resume-20261022/TASK/START-RU.txt).

Скачать ВСЕ resume-data-*.zip, bundle-manifest.json и SHA256SUMS.txt. restore_bundle.py в ветке проверяет SHA256, точные байты каждого файла и восстанавливает mtime. Включены текущие исходники/ресурсы/мод, исходная карта blocks.2, обе карты пилота, обе SQLite базы с кэшем города, журналы и все промежуточные PASS/FAIL/OOM, геометрия, каталоги, зависимости и исторические решения по цельным сборкам/v12. Аккаунты и секреты не переносятся.

Следующий шаг: закончить preserved-assemblies и адресный QA двери; затем одна реальная matched client/server pair и полный город/свет/маршрут/save/reopen. Не конвертировать город заново и не повторять пройденные незатронутые проверки.
'''
    release=api('PATCH','/repos/'+REPO+'/releases/'+str(receipt['releaseId']),{'draft':False,'prerelease':True,'make_latest':'false','body':body})
    assert not release['draft'] and len(release['assets'])==len(uploaded['assets'])
    canonical={x['name']:x for x in release['assets']}
    for row in uploaded['assets']:
        row['url']=canonical[row['name']]['browser_download_url']
        assert '/releases/download/'+TAG+'/' in row['url'],'Public asset URL has not acquired its final tag'
    receipt.update({'status':'PUBLISHED_VERIFIED_CHECKPOINT','assetCount':len(release['assets']),'publishedAt':release['published_at']})
    shutil.copy2(A/'SHA256SUMS.txt',O/'SHA256SUMS.txt')
    snapshot={'status':'TRANSFER_CHECKPOINT_VERIFIED_NOT_FINAL_READY','remote':receipt,'fileVerification':verified,'assets':uploaded['assets'],'manifestSha256':uploaded['manifestSha256'],'credentialsIncluded':False,'remaining':'preserved assemblies, affected door QA, real matched pair, full city/light, final route/save/reopen'}
    write(O/'SNAPSHOT.json',snapshot);git('add','README.md','SHA256SUMS.txt','SNAPSHOT.json','restore_bundle.py','handoff/gallery-manifest-v12.json','restore-fixture-receipt.json','history-append-receipt.json');git('commit','-m','Record verified release data hashes and continuation snapshot');commit=git('rev-parse','HEAD');git('push','origin',BRANCH)
    receipt.update({'finalBranchCommit':commit});write(O/'remote.json',receipt);print(json.dumps(receipt,ensure_ascii=False),flush=True)
