import argparse,json,subprocess,time,traceback
from pathlib import Path
P=Path(__file__).absolute().parents[2];O=P/'build/streaming-validation'
PY=Path(r'C:\Users\vakir\miniconda3\python.exe');PS=Path(r'C:\Users\vakir\.cache\codex-runtimes\codex-primary-runtime\dependencies\native\powershell\pwsh.exe')
def main():
 ap=argparse.ArgumentParser();ap.add_argument('--case',required=True);a=ap.parse_args();case=a.case;assert case.replace('-','').isalnum()
 prep=json.loads((O/(case+'-prepared.json')).read_text());assert prep['status']=='PREPARED';run=Path(prep['run_directory']);launch_path=O/(case+'-launch.json');assert not launch_path.exists()
 record={'status':'STARTING'};receipt=O/(case+'-driver.json')
 def save():receipt.write_text(json.dumps(record,indent=2)+'\n')
 try:
  save()
  with (O/(case+'-launcher.log')).open('w') as log:
   child=subprocess.Popen([str(PY),'tools/launch_prepared_client.py','--prepared-report',str(O/(case+'-prepared.json')),'--launch-report',str(launch_path),'--wait','--timeout','1800'],cwd=P,stdout=log,stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW)
   for _ in range(120):
    try:launch=json.loads(launch_path.read_text());break
    except (OSError,ValueError):time.sleep(.5)
   else:raise RuntimeError('Native launch receipt missing')
   record.update(status='NATIVE_RUNNING',pid=launch['pid']);save()
   monitor=subprocess.Popen([str(PS),'-NoProfile','-File','tools/measure_native_client_memory.ps1','-ClientProcessId',str(launch['pid']),'-Output',str(O/(case+'-memory.jsonl')),'-CaptureOutput',str(run/'client-capture-output.json'),'-MaximumSeconds','1800'],cwd=P,stdout=log,stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW)
   code=child.wait();monitor.wait(timeout=15);assert code==0
  result=json.loads(launch_path.read_text());assert result.get('exitCode')==0 and result.get('captureComplete') and result.get('normalSaveMessages'),result
  record['status']='NATIVE_SAVED'
 except Exception:record.update(status='FAIL',error=traceback.format_exc());raise
 finally:save()
if __name__=='__main__':main()
