"""Run prepared isolated original-version server, collect real physics, save/stop."""
import hashlib,json,subprocess,time
from pathlib import Path
from source_reference_names import ROOT,REF

def main():
    report_path=ROOT/'reports/SOURCE_REFERENCE.json'
    report=json.loads(report_path.read_text(encoding='utf-8'))
    assert report['status']=='PREPARED_NOT_RUN','Refuse to overwrite an executed reference'
    assert not (REF/'source-physics-output.json').exists()
    # Correctly label both tree members and surrounding foreign cells as context.
    probe=json.loads((REF/'probe-input.json').read_text(encoding='utf-8'))
    for row in probe['cells']:
        if row['purpose']=='source_architecture_tree_member':row['purpose']='source_architecture_tree_context'
    (REF/'probe-input.json').write_text(json.dumps(probe,indent=2)+'\n',encoding='utf-8')
    java=Path(report['java_home'])/'bin/java.exe'
    command=[str(java),'-Xmx3G','@libraries/net/minecraftforge/forge/1.18.2-40.2.0/win_args.txt','nogui']
    console=REF/'launch-console.log'
    start=time.monotonic()
    with console.open('w',encoding='utf-8') as stream:
        process=subprocess.Popen(command,cwd=REF,stdin=subprocess.PIPE,stdout=stream,stderr=subprocess.STDOUT,text=True)
        print('Original Forge reference PID '+str(process.pid),flush=True)
        report['status']='RUNNING';report['pid']=process.pid;report['command']=command
        report_path.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
        while process.poll() is None and time.monotonic()-start<240:
            if (REF/'source-physics-output.json').exists():
                process.stdin.write('save-all flush\nstop\n');process.stdin.flush()
                try:process.wait(timeout=60)
                except subprocess.TimeoutExpired:process.kill();process.wait();report['forced_termination']=True
                break
            time.sleep(1)
        if process.poll() is None:process.kill();process.wait();report['forced_termination']=True
    report['exit_code']=process.returncode;report['elapsed_seconds']=round(time.monotonic()-start,3)
    report['console_sha256']=hashlib.sha256(console.read_bytes()).hexdigest()
    result=REF/'source-physics-output.json'
    if result.exists():
        physics=json.loads(result.read_text(encoding='utf-8'))
        output=ROOT/'reports/SOURCE_PHYSICS_ACTUAL.json';output.write_text(json.dumps(physics,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
        report['physics_status']=physics['status'];report['physics_report_sha256']=hashlib.sha256(output.read_bytes()).hexdigest()
        report['status']='SOURCE_PHYSICS_MEASURED_SERVER_STOPPED' if physics['status']=='MEASURED_ACTUAL_SOURCE_SERVER' and process.returncode==0 else 'FAIL_SOURCE_PROBE'
    else:report['status']='FAIL_ORIGINAL_SERVER_START_OR_PROBE'
    report['manual_source_gameplay']='NOT_RUN';report['source_visual_reference']='NOT_RUN'
    report['console_evidence']=str(console)
    report_path.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(json.dumps({'status':report['status'],'exit_code':report['exit_code'],'elapsed_seconds':report['elapsed_seconds']}),flush=True)

if __name__=='__main__':main()
