param([Parameter(Mandatory=$true)][int]$ClientPid)
$ErrorActionPreference='Stop'
$taskProject=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..\..'))
$taskEvidence=Join-Path (Split-Path -Parent $taskProject) 'deliverables\collision-streaming-20261018\evidence'
$taskOutput=Join-Path $taskEvidence 'reopen-camera-repair'
if(Test-Path -LiteralPath $taskOutput){throw 'Preserve previous live repair evidence'}
New-Item -ItemType Directory -Path $taskOutput|Out-Null
$taskBin='C:\Users\vakir\AppData\Roaming\.minecraft\runtime\java-runtime-gamma\windows\java-runtime-gamma\bin'
& (Join-Path $taskBin 'javac.exe') --release 17 --add-modules jdk.attach -d $taskOutput (Join-Path $PSScriptRoot 'ReopenQaCameraAgent.java') (Join-Path $PSScriptRoot 'AttachReopenQaCamera.java') *> (Join-Path $taskOutput 'compile.log')
if($LASTEXITCODE){throw 'QA attach helper compile failed'}
$taskManifest=Join-Path $taskOutput 'MANIFEST.MF'
[IO.File]::WriteAllText($taskManifest,"Manifest-Version: 1.0`r`nAgent-Class: ReopenQaCameraAgent`r`n`r`n",[Text.UTF8Encoding]::new($false))
$taskJar=Join-Path $taskOutput 'reopen-qa-camera-agent.jar'
& (Join-Path $taskBin 'jar.exe') --create --file $taskJar --manifest $taskManifest -C $taskOutput ReopenQaCameraAgent.class *> (Join-Path $taskOutput 'jar.log')
if($LASTEXITCODE){throw 'QA attach helper packaging failed'}
$taskRuntime=Join-Path $taskProject 'build\runtime-client-collision-shapes-reopen'
$taskReceipt=Join-Path $taskOutput 'repair-receipt.json'
$taskArguments=$taskRuntime+'|a179fce5acf607bbcdf86202ddbb20e33928f0a5c6936b5e6ef7de7be14ecbaf|'+$taskReceipt
& (Join-Path $taskBin 'java.exe') --add-modules jdk.attach -Xmx128m -cp $taskOutput AttachReopenQaCamera $ClientPid $taskJar $taskArguments *> (Join-Path $taskOutput 'attach.log')
if($LASTEXITCODE){throw 'QA attach helper failed; inspect attach.log/repair-receipt.json'}
if(!(Test-Path -LiteralPath $taskReceipt)){throw 'Live repair receipt missing'}
$taskResult=Get-Content -LiteralPath $taskReceipt -Raw|ConvertFrom-Json
if($taskResult.status -ne 'PASS_QA_CAMERA_EXPECTATION_ONLY'){throw 'Live QA camera repair refused'}
