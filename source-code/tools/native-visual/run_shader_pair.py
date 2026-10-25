"""Sequentially run the two authorised shader candidates, using the same recorder."""
import json,subprocess,sys,time,traceback
from pathlib import Path
P=Path(__file__).resolve().parents[2];T=P.parent.parent/'DW-visual-physics-20261024';A=T/'addons'
def read(p):return json.loads(p.read_text(encoding='utf-8-sig'))
def write(value):(A/'pair-progress.json').write_text(json.dumps(value,indent=2)+'\n',encoding='utf-8')
try:
 gate=T/'runs/centre-iris-off-1/receipt.json';write({'status':'WAITING_BASELINE'})
 while not gate.exists():time.sleep(1)
 assert read(gate)['status']=='PASS','Baseline did not complete'
 for addon in ('sildur','makeup'):
  write({'status':'RUNNING','addon':addon})
  result=subprocess.run([sys.executable,'-B',str(Path(__file__).with_name('run_route.py')),'--case','centre','--addon',addon,'--attempt','1'],cwd=P,creationflags=subprocess.CREATE_NO_WINDOW)
  receipt=read(T/('runs/centre-'+addon+'-1/receipt.json'));assert result.returncode==0 and receipt['status']=='PASS',addon+' did not complete'
 write({'status':'BOTH_CANDIDATES_COMPLETED_REVIEW_PENDING'})
except BaseException:
 (A/'pair-error.txt').write_text(traceback.format_exc(),encoding='utf-8');write({'status':'STOPPED_ON_FAILURE'});raise
