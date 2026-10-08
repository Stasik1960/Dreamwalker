"""Run the native1.20.1 JSON parser and side-face UV APIs without starting a game."""
import hashlib
import json
from pathlib import Path
import subprocess

ROOT=Path(__file__).resolve().parents[1]
JAVA=Path('C:/Users/vakir/AppData/Roaming/.minecraft/runtime/java-runtime-gamma/windows/java-runtime-gamma/bin')
def sha(data):return hashlib.sha256(data).hexdigest()
def arguments(path,rows):path.write_text('\n'.join('"'+row.replace('\\','/')+'"' for row in rows)+'\n',encoding='utf8')
def main():
    old=(ROOT/'build/review-client-api-check/compile.args').read_text(encoding='utf8').splitlines()
    cp=old[old.index('"-cp"')+1].strip('"')
    out=ROOT/'build/tree-lower-json-probe';out.mkdir(parents=True,exist_ok=True)
    source=ROOT/'tools/java/TreeLowerModelProbe.java'
    models=[ROOT/'reports/input-history/tree-lower-invalid-model-v8/lower_source_base.json',
        ROOT/'src/architecture/resources/assets/bloodborne_dw/models/tree_proposal/lower_source_base.json',
        ROOT/'src/architecture/resources/assets/bloodborne_dw/models/tree_proposal/lower_source_base_upper.json']
    compile_args=out/'compile.args';arguments(compile_args,['--release','17','-encoding','UTF-8','-proc:none','-cp',cp,'-d',str(out),str(source)])
    compile_result=subprocess.run([str(JAVA/'javac.exe'),'@'+str(compile_args)],capture_output=True)
    if compile_result.returncode:
        print((compile_result.stdout+compile_result.stderr).decode('utf8',errors='replace'));raise SystemExit(compile_result.returncode)
    run_args=out/'run.args';arguments(run_args,['-Xmx256m','-cp',str(out)+';'+cp,'TreeLowerModelProbe']+[str(path) for path in models])
    result=subprocess.run([str(JAVA/'java.exe'),'@'+str(run_args)],capture_output=True,timeout=45)
    log=compile_result.stdout+compile_result.stderr+result.stdout+result.stderr
    portable=ROOT/'reports/runtime/TREE_LOWER_NATIVE_JSON_UV_PROBE.log';portable.parent.mkdir(parents=True,exist_ok=True);portable.write_bytes(log)
    report={'schema':'dreamwalker-pure-native-tree-lower-json-uv-v1','status':'PASS_NATIVE_JSON_AND_SOURCE_UV_DIRECTION' if result.returncode==0 else 'FAIL_NATIVE_JSON_UV_PROBE',
        'javaRelease':17,'sourceSha256':sha(source.read_bytes()),'exitCode':result.returncode,
        'models':[{'path':str(path),'sha256':sha(path.read_bytes())} for path in models],
        'historicalInvalidJsonRequiredToFail':True,'gameOrWorldStarted':False,'mainQaClassesModified':False,
        'log':str(portable),'logSha256':sha(log),
        'scope':'Native JsonUnbakedModel.deserialize accepts both legal panels; native CubeFace/ModelElementTexture prove side-faceV top-to-bottom direction. No sprite bake/render or manual acceptance claim.'}
    (ROOT/'reports/TREE_LOWER_NATIVE_JSON_UV_PROBE.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf8')
    print(json.dumps({'status':report['status'],'exitCode':result.returncode}));print(log.decode('utf8',errors='replace'))
    if result.returncode:raise SystemExit(result.returncode)
if __name__=='__main__':main()
