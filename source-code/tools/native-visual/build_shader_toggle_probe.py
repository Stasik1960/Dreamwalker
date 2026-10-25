import hashlib,json,os,subprocess,zipfile
from pathlib import Path
P=Path(__file__).resolve().parents[2];T=P.parent.parent/'DW-visual-physics-20261024';A=T/'addons'
args=[json.loads(l) for l in (T/'runs/centre-makeup32-1/client.args').read_text().splitlines() if l.strip()]
loader=next(p for p in args[args.index('-cp')+1].split(os.pathsep) if 'fabric-loader-' in Path(p).name)
javac=Path('C:/Users/vakir/AppData/Roaming/.minecraft/runtime/java-runtime-gamma/windows/java-runtime-gamma/bin/javac.exe');classes=A/'toggle-classes';classes.mkdir(exist_ok=True)
with (A/'toggle-compile.log').open('w') as log:
 result=subprocess.run([str(javac),'-J-Xmx128M','-encoding','UTF-8','-cp',loader,'-d',str(classes),str(Path(__file__).with_name('ShaderToggleProbe.java'))],stdout=log,stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW);assert result.returncode==0
target=A/'shader-toggle-probe-qa.jar'
with zipfile.ZipFile(target,'w',zipfile.ZIP_DEFLATED) as z:
 z.writestr('fabric.mod.json',json.dumps({'schemaVersion':1,'id':'dw_shader_toggle_probe','version':'1.0','environment':'client','entrypoints':{'client':['dev.dreamwalker.probe.ShaderToggleProbe']},'depends':{'fabricloader':'>=0.16.10','bloodborne_dw':'*','iris':'*'}}))
 for path in classes.rglob('*.class'):z.write(path,path.relative_to(classes).as_posix())
(A/'toggle-build.json').write_text(json.dumps({'status':'BUILT_QA_ONLY','path':str(target),'sha256':hashlib.sha256(target.read_bytes()).hexdigest(),'sourceSha256':hashlib.sha256(Path(__file__).with_name('ShaderToggleProbe.java').read_bytes()).hexdigest()},indent=2)+'\n')
