param([string]$Case='streaming-first',[switch]$Tests,[switch]$DhPilot)
$ErrorActionPreference='Stop';$PSNativeCommandUseErrorActionPreference=$false
$taskProject=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
if($Case -notmatch '^[a-zA-Z0-9_-]+$'){throw 'Invalid QA case'}
$taskOutput=Join-Path $taskProject ('build\streaming-validation\'+$Case)
if(Test-Path -LiteralPath $taskOutput){throw 'QA case already exists'}
if(Get-Process -Name java,javaw -ErrorAction SilentlyContinue){throw 'Another Java process is running'}
New-Item -ItemType Directory -Path $taskOutput|Out-Null
$env:JAVA_HOME='C:\Users\vakir\AppData\Roaming\.minecraft\runtime\java-runtime-gamma\windows\java-runtime-gamma'
$taskArgs=@('--offline','--no-daemon','--max-workers=2','-I',(Join-Path $PSScriptRoot 'streaming.init.gradle'),'-Pworldedit_api_jar=C:\Users\vakir\.codex\worktrees\3f07\DW\bloodborne complete\build\v127-dependencies\worldedit-mod-7.2.15.jar',('-Pgametest_run_name='+$Case),'-Pgametest_heap_gb=2','-Pgametest_processor_count=2')
if($DhPilot){$taskArgs+=('-Pdh_pilot_jar='+$taskProject+'\build\dh-city-pilot\dependencies\DistantHorizons-3.3.3-1.20.1-fabric-forge.jar')}
$taskReceipt=[ordered]@{case=$Case;status='RUNNING';start=[DateTime]::UtcNow.ToString('o');commands=@()}
$taskMetadata=Join-Path $taskProject 'build\resources\gametest\fabric.mod.json';$taskOriginal=$null
function Save { $taskReceipt|ConvertTo-Json -Depth 12|Set-Content -LiteralPath (Join-Path $taskOutput 'receipt.json') -Encoding utf8 }
function Run([string[]]$Tasks,[string]$Log){
 $taskCommandArgs=@($Tasks)+$taskArgs
 & (Join-Path $taskProject 'gradlew.bat') @taskCommandArgs *> (Join-Path $taskOutput $Log)
 $taskCode=$LASTEXITCODE
 $taskReceipt.commands+=@([ordered]@{arguments=$taskCommandArgs;exitCode=$taskCode;log=$Log});Save
 if($taskCode){throw ('Gradle failed; '+$Log)}
}
Set-Location -LiteralPath $taskProject
try {
 Save
 Run @('check','build','remapGalleryJar','gametestClasses') 'compile.log'
 if($DhPilot){Run @('remapDhPilotJar') 'dh-pilot.log'}
 if($Tests){
  $taskOriginal=[IO.File]::ReadAllBytes($taskMetadata)
  $taskMod=[Text.Encoding]::UTF8.GetString($taskOriginal)|ConvertFrom-Json
  $taskMod.entrypoints.'fabric-gametest'=@('dev.dreamwalker.bloodbornedw.gametest.CompositeAsyncShapeGameTests','dev.dreamwalker.bloodbornedw.gametest.CompositeNbtSnapshotGameTests','dev.dreamwalker.bloodbornedw.gametest.CompositeStreamingGameTests')
  [IO.File]::WriteAllText($taskMetadata,($taskMod|ConvertTo-Json -Depth 15),[Text.UTF8Encoding]::new($false))
  Run @('runGametest','-x','processGametestResources') 'gametest.log'
  $taskReport=Join-Path $taskProject ('build\test-results\gametest\TEST-'+$Case+'.xml')
  [xml]$taskXml=Get-Content -LiteralPath $taskReport -Raw
  $taskReceipt.tests=@($taskXml.SelectNodes('//testcase')|ForEach-Object{$_.GetAttribute('name')})
  if($taskReceipt.tests.Count -ne 12 -or @($taskXml.SelectNodes('//failure | //error | //skipped')).Count){throw 'Expected twelve passing tests'}
  Copy-Item -LiteralPath $taskReport -Destination (Join-Path $taskOutput 'gametest.xml')
 }
 $taskReceipt.status='PASS'
}catch{$taskReceipt.status='FAIL';$taskReceipt.error=$_.Exception.Message;throw}
finally{if($null -ne $taskOriginal){[IO.File]::WriteAllBytes($taskMetadata,$taskOriginal)};$taskReceipt.end=[DateTime]::UtcNow.ToString('o');Save}
