This helper is restricted to the original QA client-2 PID 128096. It installed no class transformer and did no hotswap. Cached Yarn mappings confirmed MinecraftClient.scheduleStop as method_1592. The native render thread wrote the snapshot, called scheduleStop, and the integrated server saved all dimensions. Saved player NBT coordinates/rotation are recorded separately, with angle modulo360 for comparison. The PID is now stopped; do not rerun this incident-specific helper against a recycled PID.

Original controlled command (from bloodborne complete):

```powershell
& 'C:\Users\vakir\CodexControl\Guard.ps1' -Mode Operation -Executable 'C:\Users\vakir\miniconda3\python.exe' -Arguments 'build\toolshelper\client2-camera-agent\run_attach.py' -WorkingDirectory 'C:\Users\vakir\.codex\worktrees\3f07\DW\bloodborne complete'
```

The source runner compiled the two Java files with Java17 javac -J-Xmx512m --add-modules jdk.attach, built a JAR with Agent-Class: qa.attach.CameraProbeAgent and false redefine/retransform flags, then invoked Java -Xmx512m --add-modules jdk.attach qa.attach.AttachClientProbe 128096. The first attempt failed before Attach because Windows did not expose commandLine via ProcessHandle. Its original report/log remain alongside the successful report.

The snapshot is later than the capture timeout. It proves the live client state and clean close; it cannot establish the camera pose at the original timeout.
