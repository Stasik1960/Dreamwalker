$ErrorActionPreference='Stop'
$taskLaunch=Get-Content -LiteralPath 'build\streaming-validation\after2-launch.json' -Raw|ConvertFrom-Json
$taskCmd='C:\Users\vakir\AppData\Roaming\.minecraft\runtime\java-runtime-gamma\windows\java-runtime-gamma\bin\jcmd.exe'
& $taskCmd $taskLaunch.pid JFR.stop name=DWStreaming filename=after2-initial-profile.jfr *> 'build\streaming-validation\after2-jfr-corrected.txt'
& $taskCmd $taskLaunch.pid JFR.start name=DWStreaming2 settings=profile filename=after2-remaining-profile.jfr dumponexit=true maxsize=128m *>> 'build\streaming-validation\after2-jfr-corrected.txt'