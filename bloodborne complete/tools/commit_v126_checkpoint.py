"""Save current V12.6 source progress before controlled build/validation."""
from pathlib import Path
import json,subprocess
ROOT=Path(__file__).resolve().parents[1];REPO=ROOT.parent
def git(*args):return subprocess.check_output(['git',*args],cwd=REPO,text=True,encoding='utf8')
def main():
    tracked=git('diff','--name-only','--','bloodborne complete').splitlines()
    new=git('ls-files','--others','--exclude-standard','--','bloodborne complete').splitlines()
    selected=set(tracked)|{p for p in new if '/src/' in p or '/tools/' in p and ('126' in p or '/gallery-v126/' in p) or '/docs/v12.6' in p}
    assert selected and all(p.startswith('bloodborne complete/') and '/build/' not in p for p in selected)
    # Only changed/new task sources; historical untracked releases/projections
    # are deliberately not attached to this implementation checkpoint.
    subprocess.run(['git','add','--',*sorted(selected)],cwd=REPO,check=True)
    subprocess.run(['git','commit','-m','Implement V12.6 approved assemblies, functional doors and compatibility'],cwd=REPO,check=True)
    record={'status':'PASS_SOURCE_CHECKPOINT','commit':git('rev-parse','HEAD').strip(),'files':len(selected),'buildAndNativeValidation':'NOT_RUN_AT_CHECKPOINT'}
    output=ROOT/'docs/v12.6/checkpoint.json';output.parent.mkdir(parents=True,exist_ok=True);output.write_text(json.dumps(record,indent=2)+'\n',encoding='utf8');print(json.dumps(record))
if __name__=='__main__':main()
