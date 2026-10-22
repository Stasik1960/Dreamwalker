param(
    [Parameter(Mandatory=$true)][int]$ClientProcessId,
    [Parameter(Mandatory=$true)][string]$Output,
    [string]$CaptureOutput,
    [int]$MaximumSeconds=1800
)
$ErrorActionPreference='Stop'
if($ClientProcessId -le 0 -or $MaximumSeconds -lt 1 -or $MaximumSeconds -gt 7200){throw 'Explicit bounded client measurement required.'}
if(Test-Path -LiteralPath $Output){throw 'Preserve the previous memory measurement.'}
$parent=Split-Path -Parent ([IO.Path]::GetFullPath($Output))
if(!(Test-Path -LiteralPath $parent)){New-Item -ItemType Directory -Path $parent | Out-Null}
$started=[DateTime]::UtcNow
$encoding=New-Object System.Text.UTF8Encoding($false)
$writer=New-Object System.IO.StreamWriter([IO.Path]::GetFullPath($Output),$false,$encoding)
try{
    while(([DateTime]::UtcNow-$started).TotalSeconds -lt $MaximumSeconds){
        $client=Get-Process -Id $ClientProcessId -ErrorAction SilentlyContinue
        if(!$client){break}
        $gpu=@(Get-CimInstance Win32_PerfFormattedData_GPUPerformanceCounters_GPUProcessMemory -ErrorAction SilentlyContinue | Where-Object {$_.Name -like "pid_${ClientProcessId}_*"})
        $capture=$null
        $captureWritten=$null
        if($CaptureOutput -and (Test-Path -LiteralPath $CaptureOutput)){
            try{
                $captureWritten=(Get-Item -LiteralPath $CaptureOutput).LastWriteTimeUtc
                $capture=Get-Content -LiteralPath $CaptureOutput -Raw -Encoding UTF8 | ConvertFrom-Json
            }catch{}
        }
        $row=[ordered]@{
            utc=[DateTime]::UtcNow.ToString('o');pid=$ClientProcessId
            elapsedSeconds=([DateTime]::UtcNow-$started).TotalSeconds
            workingSetBytes=$client.WorkingSet64;privateCommitBytes=$client.PrivateMemorySize64
            peakWorkingSetBytes=$client.PeakWorkingSet64
            captureStatus=if($capture){$capture.status}else{$null}
            captureFrame=if($capture){$capture.currentFrame}else{$null}
            capturePhaseWrittenUtc=if($captureWritten){$captureWritten.ToString('o')}else{$null}
            withinMeasuredPresentationWindow=if($capture -and $capture.status -eq 'MEASURING_FRAME_TIMES' -and $captureWritten){([DateTime]::UtcNow-$captureWritten).TotalSeconds -le 20}else{$false}
            dedicatedGpuBytes=if($gpu.Count){[long](($gpu|Measure-Object DedicatedUsage -Sum).Sum)}else{$null}
            sharedGpuBytes=if($gpu.Count){[long](($gpu|Measure-Object SharedUsage -Sum).Sum)}else{$null}
            gpuAdapters=@($gpu|Select-Object Name,DedicatedUsage,SharedUsage,TotalCommitted)
            method='Get-Process RAM; Windows GPUProcessMemory WDDM dedicated/shared counters. GPU dedicated usage is a per-process allocation/accounting measure, not exclusive physical board occupancy.'
        }
        $writer.WriteLine(($row|ConvertTo-Json -Depth 4 -Compress));$writer.Flush()
        Start-Sleep -Seconds 2
    }
}finally{$writer.Dispose()}
