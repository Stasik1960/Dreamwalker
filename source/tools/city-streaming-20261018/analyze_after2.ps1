$ErrorActionPreference='Stop';$PSNativeCommandUseErrorActionPreference=$false
$taskClasses=Join-Path (Get-Location) '..\research\city-streaming-and-materials-20261018\jfr-analysis-classes'
$taskCap=Get-Item -LiteralPath 'build\runtime-client-streaming-after2\client-capture-output.json'
$taskEnd=([DateTimeOffset]$taskCap.LastWriteTimeUtc).ToUnixTimeMilliseconds()
foreach($taskKind in @('initial','remaining')){
 $taskOut=Join-Path (Get-Location) ('build\streaming-validation\after2-profile-'+$taskKind)
 New-Item -ItemType Directory -Path $taskOut|Out-Null
 Copy-Item -LiteralPath ('build\runtime-client-streaming-after2\after2-'+$taskKind+'-profile.jfr') -Destination (Join-Path $taskOut 'streaming-confirmed.jfr')
 [IO.File]::WriteAllText((Join-Path $taskOut 'profile-phases.tsv'),('0'+"`t"+'TRAVELLING/after2-brick'+"`n"+$taskEnd+"`t"+'SAVE_AFTER_CAPTURE'+"`n"))
 & 'C:\Users\vakir\AppData\Roaming\.minecraft\runtime\java-runtime-gamma\windows\java-runtime-gamma\bin\java.exe' -Xmx1G -cp $taskClasses JfrResearchSummary $taskOut *> (Join-Path $taskOut 'analyzer.log')
 @{exitCode=$LASTEXITCODE}|ConvertTo-Json|Set-Content -LiteralPath (Join-Path $taskOut 'receipt.json')
}
& 'tools\city-streaming-20261018\build_pilot_ready.ps1'