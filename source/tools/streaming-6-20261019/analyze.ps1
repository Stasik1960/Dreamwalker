param([ValidateSet('baseline','after')][string]$Case='baseline')
$ErrorActionPreference='Stop'
$taskProject=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$taskOut=[IO.Path]::GetFullPath((Join-Path $taskProject ('..\research\dedicated-streaming-20261019\'+$Case)))
$taskBin='C:\Users\vakir\AppData\Roaming\.minecraft\runtime\java-runtime-gamma\windows\java-runtime-gamma\bin'
$taskClasses=Join-Path $taskProject 'build\streaming6-analysis'
New-Item -ItemType Directory -Path $taskClasses -Force|Out-Null
& 'C:\Users\vakir\miniconda3\python.exe' (Join-Path $PSScriptRoot 'summarize.py') --prepare $Case *> (Join-Path $taskOut 'prepare-analysis.log')
if($LASTEXITCODE){throw 'Interval preparation failed'}
& (Join-Path $taskBin 'javac.exe') -J-Xmx256m --release 17 -d $taskClasses (Join-Path $taskProject 'tools\collision-streaming-20261018\JfrShapeSummary.java') *> (Join-Path $taskOut 'compile-analysis.log')
if($LASTEXITCODE){throw 'Analyzer compilation failed'}
& (Join-Path $taskBin 'java.exe') -Xmx512m -XX:ActiveProcessorCount=1 -cp $taskClasses JfrShapeSummary (Join-Path $taskOut 'server.jfr') (Join-Path $taskOut 'intervals.tsv') (Join-Path $taskOut 'target-analysis.json') *> (Join-Path $taskOut 'analysis.log')
@{exitCode=$LASTEXITCODE;jfrSha256=(Get-FileHash -LiteralPath (Join-Path $taskOut 'server.jfr')).Hash;utc=[DateTime]::UtcNow.ToString('o')}|ConvertTo-Json|Set-Content -LiteralPath (Join-Path $taskOut 'analysis-receipt.json') -Encoding UTF8
