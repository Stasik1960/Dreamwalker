"""Commit completed sources/evidence without historical release artifacts."""
from pathlib import Path
import subprocess,json
ROOT=Path(__file__).resolve().parents[1];REPO=ROOT.parent
def git(*args):return subprocess.check_output(['git',*args],cwd=REPO,text=True,encoding='utf8')
def main():
    changed=git('diff','--name-only','--','bloodborne complete').splitlines()
    untracked=git('ls-files','--others','--exclude-standard','--','bloodborne complete').splitlines()
    files=set(changed)|{p for p in untracked if '/src/' in p or '/tools/' in p and ('126' in p or '/gallery-v126/' in p) or '/docs/v12.6' in p}
    assert files and all(p.startswith('bloodborne complete/') and '/build/' not in p for p in files)
    subprocess.run(['git','add','--',*sorted(files)],cwd=REPO,check=True)
    subprocess.run(['git','commit','-m','Complete V12.6 native validation and compact delivery gallery'],cwd=REPO,check=True)
    print(json.dumps({'status':'PASS_FINAL_SOURCE_AND_EVIDENCE_COMMIT','commit':git('rev-parse','HEAD').strip(),'files':len(files)}))
if __name__=='__main__':main()
