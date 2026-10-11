from pathlib import Path
import sys,json,hashlib,shutil
root=Path(__file__).resolve().parent
project=root.parents[2]
sys.path.insert(0,str(project/'tools'))
from world_io import read_nbt,compound
runtime=project/'build/runtime-client-flatgallery-v122-client-2'
output=project/'docs/gallery-v12.2/history/client2-camera-agent'
output.mkdir(parents=True,exist_ok=True)
def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
probe=project/'build/gallery-v122/client2-failed-camera-probe.json'
snap=json.loads(probe.read_text())
assert snap['status']=='PASS_SNAPSHOT' and snap['scheduleStopInvoked'] is True and snap['pid']==128096 and snap['thread']=='Render thread'
log=runtime/'logs/latest.log'
assert 'ThreadedAnvilChunkStorage: All dimensions are saved' in log.read_text(encoding='utf8',errors='replace')
capture=runtime/'client-capture-output.json'
capture_report=json.loads(capture.read_text(encoding='utf8'))
assert capture_report['status']=='FAIL_CAPTURE'
player=next((runtime/'saves/prototype-fixture/playerdata').glob('*.dat'))
nbt=compound(read_nbt(player).root)
position=[float(t.value) for t in nbt['Pos'].value]
rotation=[float(t.value) for t in nbt['Rotation'].value]
assert all(abs(a-b)<1e-6 for a,b in zip(position,snap['playerXYZ']))
rotation_delta=[(rotation[0]-snap['playerYawPitch'][0]+180)%360-180,rotation[1]-snap['playerYawPitch'][1]]
report={'status':'PASS_NATIVE_SNAPSHOT_AND_NORMAL_SHUTDOWN','targetPid':128096,'expectedQaGameDirectory':str(runtime),
 'productionJarSha256':'ab2c7806fe0bfce94cdd081924fe9a643b087caa9b8b1ca7b17fcb35f3874c32',
 'snapshot':snap,'playerSavedNbt':{'file':str(player),'sha256':sha(player),'position':position,'rotation':rotation,'positionMatchesRenderThreadSnapshot':'PASS','rotationDeltaModulo360':rotation_delta,'rotationMatchesRenderThreadSnapshot':'PASS' if all(abs(d)<1e-4 for d in rotation_delta) else 'DIFFERS'},
 'normalShutdownAllDimensionsSaved':True,'noForceTermination':True,'noTransformersOrHotswap':True,
 'originalCaptureStatus':capture_report['status'],'originalCaptureFailure':capture_report.get('failure'),'originalSuccessfulCapturedFrames':len(capture_report['frames']),
 'inferenceLimit':'The snapshot was sampled after capture failure; the later pose mismatch cannot establish the original cause of that failure.',
 'resolvedInitialToolAssumption':'Windows ProcessHandle.info().commandLine() was unavailable; CIM had already verified the exact PID/gameDir. Restricting the PID and verifying the loaded QA class before scheduleStop allowed normal Attach API completion.',
 'evidence':[]}
paths=[root/n for n in ['CameraProbeAgent.java','AttachClientProbe.java','run_attach.py','preserve_evidence.py','attach-report.json','attach-report-attempt1.json','tool.log','tool-attempt1.log']]+[probe,log,capture,runtime/'launch-console.log',runtime/'client-profile.json']
for p in paths:
 destination=output/p.name
 if destination.exists():assert sha(destination)==sha(p)
 else:shutil.copyfile(p,destination)
 report['evidence'].append({'file':p.name,'sha256':sha(p)})
(output/'normal-close-evidence.json').write_text(json.dumps(report,indent=2,ensure_ascii=False)+'\n',encoding='utf8')
(output/'README.md').write_text('''This helper is restricted to the original QA client-2 PID 128096. It installed no class transformer and did no hotswap. Cached Yarn mappings confirmed MinecraftClient.scheduleStop as method_1592. The native render thread wrote the snapshot, called scheduleStop, and the integrated server saved all dimensions. Saved player NBT coordinates/rotation are recorded separately, with angle modulo360 for comparison. The PID is now stopped; do not rerun this incident-specific helper against a recycled PID.

Original controlled command (from bloodborne complete):

```powershell
& 'C:\\Users\\vakir\\CodexControl\\Guard.ps1' -Mode Operation -Executable 'C:\\Users\\vakir\\miniconda3\\python.exe' -Arguments 'build\\toolshelper\\client2-camera-agent\\run_attach.py' -WorkingDirectory 'C:\\Users\\vakir\\.codex\\worktrees\\3f07\\DW\\bloodborne complete'
```

The source runner compiled the two Java files with Java17 javac -J-Xmx512m --add-modules jdk.attach, built a JAR with Agent-Class: qa.attach.CameraProbeAgent and false redefine/retransform flags, then invoked Java -Xmx512m --add-modules jdk.attach qa.attach.AttachClientProbe 128096. The first attempt failed before Attach because Windows did not expose commandLine via ProcessHandle. Its original report/log remain alongside the successful report.

The snapshot is later than the capture timeout. It proves the live client state and clean close; it cannot establish the camera pose at the original timeout.
''',encoding='utf8')
print(json.dumps({'status':report['status'],'output':str(output),'savedPlayerPos':position,'savedPlayerRotation':rotation}))
