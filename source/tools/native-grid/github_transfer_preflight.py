"""Read-only transfer inventory and noninteractive GitHub credential probe. No secrets logged."""
from pathlib import Path
import json,os,subprocess,urllib.request,stat
P=Path(__file__).resolve().parents[2];ROOT=P.parent;R=ROOT/'research';O=R/'transfer';O.mkdir(exist_ok=True)
def tree(path):
    total=count=0;largest=[]
    for current,dirs,names in os.walk(path,followlinks=False):
        dirs[:]=[d for d in dirs if not (os.lstat(Path(current)/d).st_file_attributes & stat.FILE_ATTRIBUTE_REPARSE_POINT)]
        for n in names:
            p=Path(current)/n;s=p.stat().st_size;total+=s;count+=1
            if s>20*1024**2:largest.append({'path':str(p),'bytes':s})
    return {'path':str(path),'bytes':total,'files':count,'largeFiles':sorted(largest,key=lambda x:-x['bytes'])}
inventory=[]
for parent in (ROOT,R):
    for p in sorted(parent.iterdir()):
        if p.is_dir() and not p.is_symlink() and not (parent==ROOT and p.name=='research'):inventory.append(tree(p))
(O/'inventory.json').write_text(json.dumps(inventory,ensure_ascii=False,indent=2),encoding='utf-8')
env=dict(os.environ,GIT_TERMINAL_PROMPT='0',GCM_INTERACTIVE='never')
result=subprocess.run(['git','credential','fill'],input='protocol=https\nhost=github.com\n\n',text=True,capture_output=True,env=env,timeout=30,creationflags=subprocess.CREATE_NO_WINDOW)
credential=dict(line.split('=',1) for line in result.stdout.splitlines() if '=' in line)
report={'credentialAvailable':bool(credential.get('password')),'credentialExit':result.returncode,'repository':'Stasik1960/Dreamwalker'}
if credential.get('password'):
    req=urllib.request.Request('https://api.github.com/repos/Stasik1960/Dreamwalker',headers={'Authorization':'Bearer '+credential['password'],'Accept':'application/vnd.github+json','User-Agent':'Dreamwalker-Resume-Transfer'})
    with urllib.request.urlopen(req,timeout=30) as response:repo=json.load(response)
    report.update({'apiStatus':'PASS','repositoryUrl':repo['html_url'],'visibility':repo['visibility'],'permissions':repo.get('permissions',{})})
(O/'github-access.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps(report,ensure_ascii=False))
print(json.dumps([{'path':x['path'],'GiB':round(x['bytes']/1024**3,3),'files':x['files']} for x in inventory],ensure_ascii=False))
