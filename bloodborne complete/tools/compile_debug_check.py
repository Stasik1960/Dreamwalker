"""Compile only catalogue/RP presentation changes with cached named Java17 APIs."""
from pathlib import Path
import hashlib
import json
import subprocess
ROOT=Path(__file__).resolve().parents[1]
def main():
    original=(ROOT/'build/review-client-api-check/compile.args').read_text(encoding='utf8').splitlines()
    cp=original[original.index('"-cp"')+1].strip('"')
    annotations=sorted(Path('C:/Users/vakir/.gradle/caches/modules-2/files-2.1/org.jetbrains/annotations').rglob('*.jar'))
    if not annotations:raise ValueError('Cached JetBrains compile annotations are absent')
    cp += ';'+str(annotations[-1])
    out=ROOT/'build/debug-catalogue-api-check';out.mkdir(parents=True,exist_ok=True)
    paths=sorted((ROOT/'src/architecture/java/dev/dreamwalker/bloodbornedw/debug').glob('*.java'))
    rp=ROOT/'src/rp/java/dev/dreamwalker/bloodbornerp'
    paths += [rp/p for p in ['object/PlacementObjectItem.java','object/RpObjectEntity.java','mob/RpMobEntity.java','mob/MobRegistry.java','weapon/TrickWeaponItem.java','content/BloodVialItem.java']]
    paths.append(ROOT/'src/gametest/java/dev/dreamwalker/bloodbornedw/gametest/DebugCatalogueGameTests.java')
    args=['--release','17','-encoding','UTF-8','-cp',cp,'-d',str(out)] + [str(path) for path in paths]
    argfile=out/'compile.args';argfile.write_text('\n'.join('"'+arg.replace('\\','/')+'"' for arg in args)+'\n',encoding='utf8')
    javac=Path('C:/Users/vakir/AppData/Roaming/.minecraft/runtime/java-runtime-gamma/windows/java-runtime-gamma/bin/javac.exe')
    result=subprocess.run([str(javac),'@'+str(argfile)],capture_output=True)
    log=result.stdout+result.stderr;(out/'compile.log').write_bytes(log)
    portable=ROOT/'reports/runtime/DEBUG_CATALOGUE_COMPILE.log';portable.parent.mkdir(parents=True,exist_ok=True);portable.write_bytes(log)
    report={'status':'PASS_ISOLATED_JAVA17' if result.returncode==0 else 'FAIL_ISOLATED_JAVA17',
        'sources':[{'path':path.relative_to(ROOT).as_posix(),'sha256':hashlib.sha256(path.read_bytes()).hexdigest()}for path in paths],
        'exit_code':result.returncode,'production_classes_modified':False,'gradle_or_game_started':False,
        'compile_log':str(portable),'compile_log_sha256':hashlib.sha256(log).hexdigest(),
        'native_tests':'Two declared native cases run by coordinated parent build; compile does not substitute runtime result.'}
    (ROOT/'reports/DEBUG_CATALOGUE_COMPILE.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf8')
    print(json.dumps({'status':'PASS_ISOLATED_JAVA17' if result.returncode==0 else 'FAIL_ISOLATED_JAVA17','sources':len(paths),'exit_code':result.returncode}))
    if result.returncode:
        print((result.stdout+result.stderr).decode('utf8',errors='replace'))
        raise SystemExit(result.returncode)
if __name__=='__main__':main()
