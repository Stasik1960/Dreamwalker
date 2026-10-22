$ErrorActionPreference='Stop'
$taskLaunch=Get-Content -LiteralPath 'build\streaming-validation\after2-launch.json' -Raw|ConvertFrom-Json
$taskJfr=Join-Path (Get-Location) 'build\streaming-validation\after2-investigation.jfr'
& 'C:\Users\vakir\AppData\Roaming\.minecraft\runtime\java-runtime-gamma\windows\java-runtime-gamma\bin\jcmd.exe' $taskLaunch.pid JFR.start name=DWStreaming settings=profile ('filename='+$taskJfr) dumponexit=true maxsize=128m *> 'build\streaming-validation\after2-jfr-start.txt'