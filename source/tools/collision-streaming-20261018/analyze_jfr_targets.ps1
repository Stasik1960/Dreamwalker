param([Parameter(Mandatory=$true)][ValidatePattern('^[a-z0-9-]+$')][string]$Case)
$ErrorActionPreference='Stop';$PSNativeCommandUseErrorActionPreference=$false
$taskProject=(Get-Location).Path
$taskTools=Join-Path $taskProject 'tools\collision-streaming-20261018'
$taskEvidence=Join-Path $taskProject '..\deliverables\collision-streaming-20261018\evidence'
$taskOut=Join-Path $taskEvidence ('jfr\'+$Case)
$taskBin='C:\Users\vakir\AppData\Roaming\.minecraft\runtime\java-runtime-gamma\windows\java-runtime-gamma\bin'
try {
 & 'C:\Users\vakir\miniconda3\python.exe' (Join-Path $taskTools 'prepare_jfr_targets.py') --case $Case *> (Join-Path $taskEvidence ($Case+'-jfr-prepare.log'))
 if($LASTEXITCODE -ne 0){throw 'JFR boundary preparation failed'}
 $taskClasses=Join-Path $taskTools 'jfr-target-classes';New-Item -ItemType Directory -Path $taskClasses -Force|Out-Null
 & (Join-Path $taskBin 'javac.exe') -J-Xmx256m --release 17 -d $taskClasses (Join-Path $taskTools 'JfrShapeSummary.java') *> (Join-Path $taskOut 'compile.log')
 if($LASTEXITCODE -ne 0){throw 'JFR target analyzer compilation failed'}
 $taskPrep=Get-Content -LiteralPath (Join-Path $taskEvidence ($Case+'-prepared.json')) -Raw|ConvertFrom-Json
 $taskJfr=Join-Path $taskPrep.run_directory 'streaming.jfr'
 & (Join-Path $taskBin 'java.exe') -Xmx512m -XX:ActiveProcessorCount=1 -cp $taskClasses JfrShapeSummary $taskJfr (Join-Path $taskOut 'intervals.tsv') (Join-Path $taskOut 'target-analysis.json') *> (Join-Path $taskOut 'analyzer.log')
 if($LASTEXITCODE -ne 0){throw 'JFR target analysis failed'}
 $taskAnalysis=Get-Content -LiteralPath (Join-Path $taskOut 'target-analysis.json') -Raw|ConvertFrom-Json
 if($taskAnalysis.status -ne 'JFR_PHASE_TARGET_ANALYSIS_COMPLETE'){throw 'JFR analysis status failed'}
 @{status='PASS';exitCode=0;javaHeapLimitBytes=536870912;activeProcessorCount=1;jfrSha256=(Get-FileHash -LiteralPath $taskJfr -Algorithm SHA256).Hash.ToLowerInvariant();analyzerSourceSha256=(Get-FileHash -LiteralPath (Join-Path $taskTools 'JfrShapeSummary.java') -Algorithm SHA256).Hash.ToLowerInvariant();eventsRead=$taskAnalysis.eventsRead;utc=[DateTime]::UtcNow.ToString('o')}|ConvertTo-Json|Set-Content -LiteralPath (Join-Path $taskOut 'receipt.json') -Encoding utf8
} catch {
 New-Item -ItemType Directory -Path $taskOut -Force|Out-Null
 @{status='FAIL';error=$_.Exception.ToString();utc=[DateTime]::UtcNow.ToString('o')}|ConvertTo-Json|Set-Content -LiteralPath (Join-Path $taskOut 'receipt.json') -Encoding utf8
 throw
}
