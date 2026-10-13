from pathlib import Path
import subprocess,json,hashlib,zipfile
root=Path(__file__).resolve().parent
java=Path('C:/Users/vakir/AppData/Roaming/.minecraft/runtime/java-runtime-gamma/windows/java-runtime-gamma/bin')
mapping=Path('C:/Users/vakir/.gradle/caches/fabric-loom/1.20.1/net.fabricmc.yarn.1_20_1.1.20.1+build.10-v2/mappings.tiny')
lines=mapping.read_text().splitlines(); current=None; found={}
for line in lines:
    fields=line.split('\t')
    if line.startswith('c\t'):current=fields[2]
    if current=='net/minecraft/class_310' and len(fields)>5 and fields[-1]=='scheduleStop':found['scheduleStop']=fields[-2]
assert found['scheduleStop']=='method_1592'
assert any('\tfield_7479\tflying' in row for row in lines)
assert any('\tmethod_4490\tgetHandle' in row for row in lines)
out=root.parents[1]/'gallery-v122/client2-failed-camera-probe.json'
assert not out.exists()
classes=root/'classes';classes.mkdir(exist_ok=True)
report={'status':'RUNNING','expectedTargetPid':128096,'mappingSha256':hashlib.sha256(mapping.read_bytes()).hexdigest(),'confirmedMapping':found}
def write(): (root/'attach-report.json').write_text(json.dumps(report,indent=2)+'\n')
write()
with (root/'tool.log').open('w') as log:
    try:
        subprocess.run([str(java/'javac.exe'),'-J-Xmx512m','--add-modules','jdk.attach','-d',str(classes),str(root/'CameraProbeAgent.java'),str(root/'AttachClientProbe.java')],check=True,stdout=log,stderr=subprocess.STDOUT)
        jar=root/'camera-probe-agent.jar'
        with zipfile.ZipFile(jar,'w') as z:
            z.writestr('META-INF/MANIFEST.MF','Manifest-Version: 1.0\nAgent-Class: qa.attach.CameraProbeAgent\nCan-Redefine-Classes: false\nCan-Retransform-Classes: false\n\n')
            for p in classes.rglob('*.class'):z.write(p,str(p.relative_to(classes)).replace('\\','/'))
        report['agentSha256']=hashlib.sha256(jar.read_bytes()).hexdigest()
        subprocess.run([str(java/'java.exe'),'-Xmx512m','--add-modules','jdk.attach','-cp',str(jar),'qa.attach.AttachClientProbe','128096',str(jar),str(out)],check=True,stdout=log,stderr=subprocess.STDOUT)
        report.update(status='PASS_ATTACH_API',snapshotFile=str(out),snapshotExists=out.exists())
    except Exception as e:report.update(status='FAIL_ATTACH_API',failure=str(e))
write()
