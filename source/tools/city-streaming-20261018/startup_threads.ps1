$ErrorActionPreference='Stop'
$taskLaunch=Get-Content -LiteralPath 'build\streaming-validation\before1-launch.json' -Raw|ConvertFrom-Json
& 'C:\Users\vakir\AppData\Roaming\.minecraft\runtime\java-runtime-gamma\windows\java-runtime-gamma\bin\jcmd.exe' $taskLaunch.pid Thread.print *> 'build\streaming-validation\before1-startup-threads.txt'