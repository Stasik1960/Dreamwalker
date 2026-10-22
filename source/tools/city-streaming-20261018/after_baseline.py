"""Continue the matched candidate only after the baseline saved normally."""
import json,subprocess,time,traceback
from pathlib import Path
P=Path(__file__).absolute().parents[2];OUT=P/'build/streaming-validation'
PY=Path(r'C:\Users\vakir\miniconda3\python.exe')
PS=Path(r'C:\Users\vakir\.cache\codex-runtimes\codex-primary-runtime\dependencies\native\powershell\pwsh.exe')
record={'status':'WAITING_BASELINE'}
def save(): (OUT/'candidate-pipeline.json').write_text(json.dumps(record,indent=2)+'\n')
def main():
 save();started=time.monotonic();path=OUT/'before1-launch.json'
 while time.monotonic()-started<1800:
  try:baseline=json.loads(path.read_text())
  except (OSError,ValueError):time.sleep(2);continue
  if 'exitCode' in baseline:break
  time.sleep(2)
 else:raise RuntimeError('Baseline did not finish')
 assert baseline['exitCode']==0 and baseline.get('captureComplete') and baseline.get('normalSaveMessages'),baseline
 cap=json.loads((Path(baseline['run_directory'])/'client-capture-output.json').read_text());assert cap['status']=='CAPTURE_COMPLETE' and len(cap['frames'])==3
 record['status']='PREPARING_CANDIDATE';save()
 with (OUT/'after1-prepare.log').open('w') as log:subprocess.run([str(PY),'tools/city-streaming-20261018/prepare_native.py','--case','after1','--candidate'],cwd=P,stdout=log,stderr=subprocess.STDOUT,check=True)
 record['status']='CANDIDATE_NATIVE';save()
 with (OUT/'after1-launcher.log').open('w') as log:
  child=subprocess.Popen([str(PY),'tools/launch_prepared_client.py','--prepared-report','build/streaming-validation/after1-prepared.json','--launch-report','build/streaming-validation/after1-launch.json','--wait','--timeout','1800'],cwd=P,stdout=log,stderr=subprocess.STDOUT)
  for _ in range(120):
   try:launch=json.loads((OUT/'after1-launch.json').read_text());break
   except (OSError,ValueError):time.sleep(.5)
  else:raise RuntimeError('Candidate launcher did not create receipt')
  monitor=subprocess.Popen([str(PS),'-NoProfile','-File','tools/measure_native_client_memory.ps1','-ClientProcessId',str(launch['pid']),'-Output','build/streaming-validation/after1-memory.jsonl','-CaptureOutput','build/runtime-client-streaming-after1/client-capture-output.json','-MaximumSeconds','1800'],cwd=P,stdout=log,stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW)
  code=child.wait();monitor.wait(timeout=15);assert code==0
 result=json.loads((OUT/'after1-launch.json').read_text());assert result['exitCode']==0 and result.get('captureComplete') and result.get('normalSaveMessages'),result
 record['status']='CANDIDATE_SAVED';save()
if __name__=='__main__':
 try:main()
 except Exception:record['status']='FAIL';record['error']=traceback.format_exc();save();raise
