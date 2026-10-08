"""Compile the QA-only source guard against cached named Java17 APIs; no Gradle/game."""
from pathlib import Path
import hashlib
import json
import subprocess

ROOT=Path(__file__).resolve().parents[1]
def main():
    old=(ROOT/'build/source-review-api-check/compile.args').read_text(encoding='utf8').splitlines()
    cp=str(ROOT/'build/source-review-api-check')+';'+old[old.index('"-cp"')+1].strip('"')
    out=ROOT/'build/source-review-v8-guard-api-check';out.mkdir(parents=True,exist_ok=True)
    source=ROOT/'src/review/java/dev/dreamwalker/bloodbornedw/review/SourceReviewBootstrap.java'
    arguments=['--release','17','-encoding','UTF-8','-proc:none','-sourcepath','','-cp',cp,'-d',str(out),str(source)]
    argfile=out/'compile.args';argfile.write_text('\n'.join('"'+arg.replace('\\','/')+'"' for arg in arguments)+'\n',encoding='utf8')
    compiler=Path('C:/Users/vakir/AppData/Roaming/.minecraft/runtime/java-runtime-gamma/windows/java-runtime-gamma/bin/javac.exe')
    result=subprocess.run([str(compiler),'@'+str(argfile)],capture_output=True)
    log=result.stdout+result.stderr;(out/'compile.log').write_bytes(log)
    portable=ROOT/'reports/runtime/SOURCE_REVIEW_V8_GUARD_COMPILE.log';portable.parent.mkdir(parents=True,exist_ok=True);portable.write_bytes(log)
    report={'status':'PASS_ISOLATED_JAVA17_QA_ONLY' if result.returncode==0 else'FAIL_ISOLATED_JAVA17_QA_ONLY',
        'exit_code':result.returncode,'source':str(source),'source_sha256':hashlib.sha256(source.read_bytes()).hexdigest(),
        'compiler':str(compiler),'source_release':17,'production_classes_modified':False,'gradle_or_game_started':False,
        'compile_log':str(portable),'compile_log_sha256':hashlib.sha256(log).hexdigest(),
        'limits':'Named API compilation only; ordinary remapped QA/server execution remains a separate parent run.'}
    (ROOT/'reports/SOURCE_REVIEW_V8_GUARD_COMPILE.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf8')
    print(json.dumps({key:report[key] for key in ('status','exit_code','production_classes_modified')}))
    if result.returncode:print(log.decode('utf8',errors='replace'));raise SystemExit(result.returncode)
if __name__=='__main__':main()
