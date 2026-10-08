"""Isolated current lamp+command+native API compilation; no Gradle or game process."""
from pathlib import Path
import hashlib,json,subprocess
ROOT=Path(__file__).resolve().parents[1]
def main():
    old=(ROOT/'build/review-client-api-check/compile.args').read_text(encoding='utf8').splitlines()
    cp=old[old.index('"-cp"')+1].strip('"')
    out=ROOT/'build/lamp-v10-api-check';out.mkdir(parents=True,exist_ok=True)
    base=ROOT/'src/rp/java/dev/dreamwalker/bloodbornerp'
    paths=[base/x for x in ['lamp/LampState.java','lamp/LampService.java','lamp/LampPolicy.java','lamp/LampEditor.java','client/lamp/LampClient.java','command/RpCommands.java']]
    paths.append(ROOT/'src/gametest/java/dev/dreamwalker/bloodbornerp/lamp/LampV10GameTests.java')
    args=['--release','17','-encoding','UTF-8','-proc:none','-sourcepath','','-cp',cp,'-d',str(out)]+[str(p)for p in paths]
    argfile=out/'compile.args';argfile.write_text('\n'.join('"'+s.replace('\\','/')+'"' for s in args)+'\n',encoding='utf8')
    javac=Path('C:/Users/vakir/AppData/Roaming/.minecraft/runtime/java-runtime-gamma/windows/java-runtime-gamma/bin/javac.exe')
    result=subprocess.run([str(javac),'@'+str(argfile)],capture_output=True)
    log=result.stdout+result.stderr;(out/'compile.log').write_bytes(log)
    saved=ROOT/'reports/runtime/LAMP_V10_ISOLATED_COMPILE.log';saved.parent.mkdir(parents=True,exist_ok=True);saved.write_bytes(log)
    report={'schema':'dreamwalker-lamp-v10-isolated-compile-v1','status':'PASS_ISOLATED_JAVA17_API' if result.returncode==0 else 'FAIL_ISOLATED_JAVA17_API','exit_code':result.returncode,'sources':[{'path':p.relative_to(ROOT).as_posix(),'sha256':hashlib.sha256(p.read_bytes()).hexdigest()}for p in paths],'compile_log':saved.relative_to(ROOT).as_posix(),'compile_log_sha256':hashlib.sha256(log).hexdigest(),'production_classes_modified':False,'gradle_or_game_started':False,'native':'NOT_RUN','actual_client_click_and_gui':'NOT_RUN'}
    (ROOT/'reports/LAMP_V10_ISOLATED_COMPILE.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf8')
    print(report['status']);print(log.decode('utf8',errors='replace'));raise SystemExit(result.returncode)
if __name__=='__main__':main()
