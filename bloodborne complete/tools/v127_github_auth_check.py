"""Read-only GitHub credential availability; never print or persist credentials."""
from pathlib import Path
import subprocess,os,json,urllib.request,urllib.error
root=Path(__file__).resolve().parents[1]
env=dict(os.environ,GIT_TERMINAL_PROMPT='0',GCM_INTERACTIVE='Never')
r=subprocess.run(['git','credential','fill'],input='protocol=https\nhost=github.com\n\n',text=True,capture_output=True,cwd=root,env=env,timeout=45)
fields=dict(line.split('=',1) for line in r.stdout.splitlines() if '=' in line)
record={'credentialAvailable':bool(fields.get('password')),'gitCredentialExit':r.returncode,'publicationAttempted':False}
if fields.get('password'):
    request=urllib.request.Request('https://api.github.com/repos/Stasik1960/Dreamwalker',headers={'Authorization':'Bearer '+fields['password'],'Accept':'application/vnd.github+json','User-Agent':'Dreamwalker-release-preflight'})
    try:
        with urllib.request.urlopen(request,timeout=30) as response:data=json.load(response)
        record.update(repository=data.get('full_name'),permissions=data.get('permissions'),status='PASS_READONLY_AUTH')
    except urllib.error.HTTPError as e:record.update(status='AUTH_HTTP_ERROR',httpStatus=e.code)
else:record['status']='AUTH_NOT_AVAILABLE'
(root/'build/v127-github-auth.json').write_text(json.dumps(record,indent=2)+'\n')
print(json.dumps(record))
