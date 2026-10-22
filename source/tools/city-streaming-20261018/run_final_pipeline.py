"""Controlled sequential QA: passing build -> fresh native route -> DH pilot."""
import argparse,datetime,json,subprocess,time,traceback
from pathlib import Path
P=Path(__file__).absolute().parents[2];O=P/'build/streaming-validation';D=P/'build/dh-city-pilot'
PY=r'C:\Users\vakir\miniconda3\python.exe';SCI=r'C:\Users\vakir\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe'
def main():
 ap=argparse.ArgumentParser();ap.add_argument('--validation',required=True);ap.add_argument('--native-case',required=True);ap.add_argument('--continue-saved-native',action='store_true');a=ap.parse_args()
 assert all(x.replace('-','').isalnum() for x in [a.validation,a.native_case]);case=a.native_case;receipt=O/(case+'-pipeline.json')
 if a.continue_saved_native:
  record=json.loads(receipt.read_text());assert json.loads((O/(case+'-driver.json')).read_text())['status']=='NATIVE_SAVED'
  record.setdefault('previousFailures',[]).append({'status':record['status'],'error':record.pop('error',None),'steps':record['steps']});record['steps']=[]
 else:
  assert not receipt.exists();record={'status':'WAITING_VALIDATION','steps':[]}
 def save():record['utc']=datetime.datetime.now(datetime.timezone.utc).isoformat();receipt.write_text(json.dumps(record,indent=2)+'\n')
 def run(name,python,args):
  record['status']=name;save();log=O/(case+'-pipeline-'+name.lower()+'.log')
  with log.open('w') as out:code=subprocess.run([python,*args],cwd=P,stdout=out,stderr=subprocess.STDOUT).returncode
  record['steps'].append({'name':name,'exitCode':code,'log':str(log)});save();assert code==0,name
 try:
  save();started=time.monotonic()
  while time.monotonic()-started<3600:
   try:validation=json.loads((O/a.validation/'receipt.json').read_text())
   except (OSError,ValueError):time.sleep(2);continue
   if validation['status']=='PASS':break
   if validation['status']=='FAIL':raise RuntimeError('Validation failed; no native launch')
   time.sleep(2)
  else:raise RuntimeError('Validation timeout')
  assert len(validation.get('tests',[]))==12
  run('AUDIT_ASSETS',PY,['tools/city-streaming-20261018/audit_unchanged_assets.py'])
  if not a.continue_saved_native:
   run('PREPARE_NATIVE',PY,['tools/city-streaming-20261018/prepare_native.py','--case',case,'--candidate'])
   run('NATIVE_ROUTE',PY,['tools/city-streaming-20261018/run_native_case.py','--case',case])
  run('ANALYZE_NATIVE',PY,['tools/city-streaming-20261018/analyze_native.py','--candidate-case',case])
  ps=r'C:\Users\vakir\.cache\codex-runtimes\codex-primary-runtime\dependencies\native\powershell\pwsh.exe'
  run('BUILD_DH_VISUAL',ps,['-NoProfile','-File','tools/city-streaming-20261018/build_pilot_ready.ps1'])
  scene=D/'tower-scene-portable';run('EXPORT_SCENE',SCI,['tools/city-streaming-20261018/export_dh_scene.py','--out',str(scene)])
  run('PREPARE_DH',PY,['tools/city-streaming-20261018/prepare_dh_native.py','--source-case',case,'--scene',str(scene)])
  run('NATIVE_DH',PY,['tools/city-streaming-20261018/launch_dh_native.py'])
  record['status']='NATIVE_DH_SAVED_REQUIRES_PIXEL_REVIEW'
 except Exception:record.update(status='FAIL',error=traceback.format_exc());raise
 finally:save()
if __name__=='__main__':main()
