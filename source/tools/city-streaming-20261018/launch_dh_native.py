import hashlib,json,subprocess,traceback,zipfile
from pathlib import Path
P=Path(__file__).absolute().parents[2];D=P/'build/dh-city-pilot'
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def main():
 prep=json.loads((D/'native-prepared.json').read_text());run=Path(prep['run_directory']);assert run.parent==P/'build' and run.name.startswith('runtime-client-streaming-') and prep['status']=='PREPARED'
 assert not (run/'launch-console.log').exists() and not (D/'native-launch.json').exists()
 assert sha(Path(prep['artifact']))==prep['artifact_sha256'] and sha(run/'mods'/Path(prep['artifact']).name)==prep['artifact_sha256']
 assert sha(run/'client-capture-input.json')==prep['markerSha256']
 for mod in prep['mods']:
  path=Path(mod['file']);assert path.parent==run/'mods' and sha(path)==mod['sha256']
  with zipfile.ZipFile(path) as z:assert json.loads(z.read('fabric.mod.json'))['id']==mod['id']
 command=prep['command'];assert Path(command[0]).name=='java.exe' and Path(command[command.index('--gameDir')+1])==run
 result={'status':'LAUNCHING','artifactSha256':prep['artifact_sha256']}
 def save():(D/'native-launch.json').write_text(json.dumps(result,indent=2)+'\n')
 try:
  with (run/'launch-console.log').open('w') as console:
   child=subprocess.Popen(command,cwd=run,stdin=subprocess.DEVNULL,stdout=console,stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW);result.update(status='NATIVE_RUNNING',pid=child.pid);save()
   ps=r'C:\Users\vakir\.cache\codex-runtimes\codex-primary-runtime\dependencies\native\powershell\pwsh.exe'
   monitor=subprocess.Popen([ps,'-NoProfile','-File','tools/measure_native_client_memory.ps1','-ClientProcessId',str(child.pid),'-Output','build/dh-city-pilot/native-memory.jsonl','-CaptureOutput',str(run/'client-capture-output.json'),'-MaximumSeconds','1800'],cwd=P,stdout=console,stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW)
   code=child.wait(timeout=1800);monitor.wait(timeout=15)
  cap=json.loads((run/'client-capture-output.json').read_text());log=(run/'launch-console.log').read_text(encoding='utf8',errors='replace');result.update(exitCode=code,captureStatus=cap['status'],normalSaveMessages='All dimensions are saved' in log)
  assert code==0 and cap['status']=='CAPTURE_COMPLETE' and len(cap['frames'])==3 and result['normalSaveMessages'],result
  settings=json.loads((run/'dreamwalker-dh-pilot-settings-result.json').read_text());assert settings['accepted'] and not settings['effectiveDistantGeneration'],settings
  assert cap['frames'][-1]['dhPilot']['renderPasses']>0 and cap['frames'][-1]['dhPilot']['activeTiles']>0
  result['status']='PASS_NATIVE_DH_RENDER_REQUIRES_PIXEL_REVIEW'
 except Exception:result.update(status='FAIL',error=traceback.format_exc());raise
 finally:save()
if __name__=='__main__':main()
