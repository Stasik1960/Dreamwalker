"""Log controlled Python stages because Guard intentionally suppresses child output."""
import argparse,json,os,subprocess,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser();p.add_argument('--name',required=True);p.add_argument('script');p.add_argument('arguments',nargs=argparse.REMAINDER);a=p.parse_args()
target=(ROOT/a.script).resolve();assert target.is_relative_to(ROOT/'tools') and target.suffix=='.py'
out=ROOT/'build/v124-stages'/a.name;out.parent.mkdir(parents=True,exist_ok=True)
with out.with_suffix('.log').open('wb') as log:r=subprocess.run([sys.executable,str(target),*a.arguments],cwd=ROOT,env=dict(os.environ,PYTHONUTF8='1'),stdout=log,stderr=subprocess.STDOUT)
out.with_suffix('.json').write_text(json.dumps({'name':a.name,'script':a.script,'exitCode':r.returncode,'status':'PASS' if r.returncode==0 else 'FAIL'})+'\n',encoding='utf8')
sys.exit(r.returncode)
