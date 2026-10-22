$ErrorActionPreference='Stop'
$taskEvidence=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..\..\research\city-materials-20261019\reopen-final2'))
$taskStarted=[DateTime]::UtcNow
while(([DateTime]::UtcNow-$taskStarted).TotalSeconds -lt 1200){
    $taskMemoryPath=Join-Path $taskEvidence 'memory.jsonl'
    if(Test-Path -LiteralPath $taskMemoryPath){
        $taskLast=Get-Content -LiteralPath $taskMemoryPath -Tail 1 | ConvertFrom-Json
        if($taskLast.withinMeasuredPresentationWindow){
            $taskLaunch=Get-Content -LiteralPath (Join-Path $taskEvidence 'launch.json') -Raw | ConvertFrom-Json
            $taskGpu=& 'C:\Windows\System32\nvidia-smi.exe' '--query-gpu=temperature.gpu,pstate,clocks.current.graphics,utilization.gpu,power.draw,memory.used' '--format=csv,noheader' 2>&1
            $taskResult=[ordered]@{utc=[DateTime]::UtcNow.ToString('o');memorySamplerRow=$taskLast;
                os=(Get-CimInstance Win32_OperatingSystem | Select-Object TotalVisibleMemorySize,FreePhysicalMemory,TotalVirtualMemorySize,FreeVirtualMemory);
                paging=(Get-CimInstance Win32_PerfFormattedData_PerfOS_Memory | Select-Object AvailableMBytes,PageReadsPersec,PagesInputPersec,PagesPersec,CommittedBytes);
                cpu=(Get-CimInstance Win32_Processor | Select-Object Name,LoadPercentage,CurrentClockSpeed,MaxClockSpeed);
                native=(Get-Process -Id ([int]$taskLaunch.pid) | Select-Object Id,CPU,WorkingSet64,PrivateMemorySize64);
                gpuFields='temperature.gpu,pstate,clocks.current.graphics,utilization.gpu,power.draw,memory.used';gpu=($taskGpu -join "`n");
                method='Single read-only host snapshot during the repeat window for an observed FPS anomaly; no settings changed.'}
            $taskResult | ConvertTo-Json -Depth 7 | Set-Content -LiteralPath (Join-Path $taskEvidence 'host-state-at-fps.json') -Encoding utf8
            exit 0
        }
    }
    Start-Sleep -Seconds 2
}
throw 'No measured repeat window was observed'
