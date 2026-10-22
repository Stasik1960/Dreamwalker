param([string]$Case='shape-reuse-final')
$ErrorActionPreference='Stop'
$PSNativeCommandUseErrorActionPreference=$false
$taskProject=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$taskEvidence=[IO.Path]::GetFullPath((Join-Path $taskProject '..\deliverables\collision-streaming-20261018\evidence'))
if($Case -notmatch '^[a-zA-Z0-9_-]+$'){throw 'Invalid validation case'}
if(Get-Process -Name java,javaw -ErrorAction SilentlyContinue){throw 'Native route or another Java process is still running'}
$taskOutput=Join-Path $taskEvidence $Case
if(Test-Path -LiteralPath $taskOutput){throw 'Do not overwrite prior validation'}
New-Item -ItemType Directory -Path $taskOutput|Out-Null
$env:JAVA_HOME='C:\Users\vakir\AppData\Roaming\.minecraft\runtime\java-runtime-gamma\windows\java-runtime-gamma'
$taskArgs=@('--offline','--no-daemon','--max-workers=2','-I',(Join-Path $taskProject 'tools\city-streaming-20261018\shape-reuse.init.gradle'),'-Pworldedit_api_jar=C:\Users\vakir\.codex\worktrees\3f07\DW\bloodborne complete\build\v127-dependencies\worldedit-mod-7.2.15.jar',('-Pgametest_run_name='+$Case),'-Pgametest_heap_gb=2','-Pgametest_processor_count=2')
$taskReceipt=[ordered]@{status='RUNNING';case=$Case;startedUtc=[DateTime]::UtcNow.ToString('o');commands=@()}
function Save {$taskReceipt|ConvertTo-Json -Depth 12|Set-Content -LiteralPath (Join-Path $taskOutput 'receipt.json') -Encoding utf8}
function Run([string[]]$Tasks,[string]$Log){
 $taskCommandArgs=@($Tasks)+$taskArgs
 & (Join-Path $taskProject 'gradlew.bat') @taskCommandArgs *> (Join-Path $taskOutput $Log)
 $taskCode=$LASTEXITCODE
 $taskReceipt.commands+=@([ordered]@{arguments=$taskCommandArgs;exitCode=$taskCode;log=$Log});Save
 if($taskCode){throw ('Gradle failed: '+$Log)}
}
Set-Location -LiteralPath $taskProject
$taskMetadata=Join-Path $taskProject 'build\resources\gametest\fabric.mod.json'
$taskOriginal=$null
try{
 Save
 Run @('check','build','gametestClasses') 'build.log'
 $taskOriginal=[IO.File]::ReadAllBytes($taskMetadata)
 $taskMod=[Text.Encoding]::UTF8.GetString($taskOriginal)|ConvertFrom-Json
 $taskMod.entrypoints.'fabric-gametest'=@('dev.dreamwalker.bloodbornedw.gametest.CompositeShapeReuseGameTests','dev.dreamwalker.bloodbornedw.gametest.CompositeNbtSnapshotGameTests')
 [IO.File]::WriteAllText($taskMetadata,($taskMod|ConvertTo-Json -Depth 15),[Text.UTF8Encoding]::new($false))
 Run @('runGametest','-x','processGametestResources') 'gametest.log'
 $taskReport=Join-Path $taskProject ('build\test-results\gametest\TEST-'+$Case+'.xml')
 [xml]$taskXml=Get-Content -LiteralPath $taskReport -Raw
 $taskReceipt.tests=@($taskXml.SelectNodes('//testcase')|ForEach-Object{$_.GetAttribute('name')})
 if($taskReceipt.tests.Count -ne 6 -or @($taskXml.SelectNodes('//failure | //error | //skipped')).Count){throw 'Expected six passing focused native tests'}
 Copy-Item -LiteralPath $taskReport -Destination (Join-Path $taskOutput 'gametest.xml')
 $taskReceipt.status='PASS'
}catch{$taskReceipt.status='FAIL';$taskReceipt.error=$_.Exception.Message;throw}
finally{
 if($null -ne $taskOriginal){[IO.File]::WriteAllBytes($taskMetadata,$taskOriginal)}
 $taskReceipt.finishedUtc=[DateTime]::UtcNow.ToString('o');Save
}
