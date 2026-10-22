import subprocess,time,traceback
from run import prepare
from common import *

def main():
    wait_foreign();case=OUT/'retention';p=prepare(case,BASE)
    args=[s.replace('-Xmx4G','-Xmx9G') for s in p['serverArgs']]
    args.insert(0,'-XX:StartFlightRecording=name=retention,settings=profile,maxsize=128m,dumponexit=true,filename='+str(case/'server/startup.jfr'))
    argsfile(SERVER/'retention.args',args)
    sampler=Sampler();child=None
    try:
        with (case/'server/console.log').open('w') as log,(case/'memory.jsonl').open('w') as memory:
            child=subprocess.Popen([str(JAVA),'@'+str(SERVER/'retention.args')],cwd=SERVER,stdin=subprocess.PIPE,stdout=log,stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW)
            p.update(serverPid=child.pid,status='WAITING_DEDICATED_START');write(case/'prepared.json',p);started=time.time();ready=None
            while ready is None or time.time()-ready<15:
                if child.poll() is not None:raise RuntimeError('Diagnostic server startup failed')
                memory.write(json.dumps(sampler.sample([('server',child.pid)]))+'\n');memory.flush()
                if ready is None and 'Done (' in (case/'server/console.log').read_text(errors='replace'):ready=time.time()
                if time.time()-started>300:raise RuntimeError('Diagnostic startup deadline')
                time.sleep(.5)
            with (case/'server/live-histogram.txt').open('w') as result:
                hist=subprocess.run([str(JAVA_HOME/'bin/jcmd.exe'),str(child.pid),'GC.class_histogram'],stdout=result,stderr=subprocess.STDOUT,timeout=90,creationflags=subprocess.CREATE_NO_WINDOW)
            memory.write(json.dumps(sampler.sample([('server',child.pid)]))+'\n');memory.flush()
            with (case/'server/jfr-dump.txt').open('w') as result:subprocess.run([str(JAVA_HOME/'bin/jcmd.exe'),str(child.pid),'JFR.dump','name=retention','filename='+str(case/'server/startup.jfr')],stdout=result,stderr=subprocess.STDOUT,timeout=60,creationflags=subprocess.CREATE_NO_WINDOW)
            p.update(status='PASS_RETAINED_HEAP_DIAGNOSTIC',histogramExitCode=hist.returncode);write(case/'prepared.json',p)
    finally:
        if child and child.poll() is None:
            try:child.stdin.write(b'stop\n');child.stdin.flush();child.wait(timeout=60)
            except Exception:child.kill();child.wait(timeout=15)
        sampler.close()

if __name__=='__main__':
    try:main()
    except BaseException:(OUT/'retention-error.txt').write_text(traceback.format_exc());raise
