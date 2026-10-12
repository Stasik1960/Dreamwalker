"""Capture bounded native QA diagnostics from an explicitly selected project JVM."""
from pathlib import Path
import subprocess,json,argparse
ROOT=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser();p.add_argument('--pid',type=int,required=True);p.add_argument('--name',required=True);a=p.parse_args()
assert a.name.replace('-','').isalnum()
jcmd=Path.home()/'AppData/Roaming/.minecraft/runtime/java-runtime-gamma/windows/java-runtime-gamma/bin/jcmd.exe'
out=ROOT/'build/v127-validation'/a.name;out.mkdir(exist_ok=True)
for command,name in [('GC.class_histogram -all','histogram'),('Thread.print','threads')]:
    try:
        r=subprocess.run([str(jcmd),str(a.pid),*command.split()],capture_output=True,text=True,timeout=55)
        (out/(name+'.txt')).write_text(r.stdout+'\n'+r.stderr,encoding='utf-8')
        print(json.dumps({'pid':a.pid,'command':command,'exitCode':r.returncode,'path':str(out/(name+'.txt'))}))
    except subprocess.TimeoutExpired:print(json.dumps({'pid':a.pid,'command':command,'status':'TIMEOUT'}))
