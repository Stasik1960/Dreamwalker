"""Compile and run pure boundary verification with pinned Java17, under Guard."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess

ROOT=Path(__file__).resolve().parents[1]
JAVA=Path('C:/Users/vakir/AppData/Roaming/.minecraft/runtime/java-runtime-gamma/windows/java-runtime-gamma/bin')

def main():
    p=argparse.ArgumentParser();p.add_argument('--output',type=Path,required=True);a=p.parse_args()
    out=a.output.resolve();assert out.is_relative_to((ROOT/'build').resolve()) and not out.exists();out.mkdir(parents=True)
    sources=[ROOT/'src/architecture/java/dev/dreamwalker/bloodbornedw/composite/ImportedCollisionMath.java',ROOT/'src/test/java/dev/dreamwalker/bloodbornedw/composite/ImportedCollisionMathChecks.java']
    compile=subprocess.run([str(JAVA/'javac.exe'),'--release','17','-encoding','UTF-8','-d',str(out/'classes'),*[str(s) for s in sources]],cwd=ROOT,capture_output=True,text=True)
    run=None
    if compile.returncode==0:run=subprocess.run([str(JAVA/'java.exe'),'-cp',str(out/'classes'),'dev.dreamwalker.bloodbornedw.composite.ImportedCollisionMathChecks'],cwd=ROOT,capture_output=True,text=True)
    result={'schema':'dreamwalker-v122-six-face-boundary-checks-v1','status':'PASS' if run and run.returncode==0 else 'FAIL','sources':{str(s.relative_to(ROOT)).replace('\\','/'):hashlib.sha256(s.read_bytes()).hexdigest() for s in sources},'compileExitCode':compile.returncode,'compileOutput':compile.stdout+compile.stderr,'runExitCode':run.returncode if run else None,'runOutput':run.stdout+run.stderr if run else None}
    (out/'report.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    if result['status']!='PASS':raise SystemExit(1)

if __name__=='__main__':main()
