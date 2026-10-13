param([Parameter(Mandatory=$true)][string]$Report)
$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
$taskData = Get-Content -LiteralPath $Report -Raw -Encoding UTF8 | ConvertFrom-Json
$taskRun = [IO.Path]::GetFullPath($taskData.run_directory)
if (-not $taskRun.StartsWith((Join-Path $taskRoot 'build\runtime-client-remaining-'),[StringComparison]::OrdinalIgnoreCase)) { throw 'Not a remaining-transfer isolated client' }
$taskProcess = Get-CimInstance Win32_Process -Filter "ProcessId = $($taskData.pid)"
if ($taskProcess) {
    if ($taskProcess.Name -ne 'java.exe' -or -not $taskProcess.CommandLine.Contains($taskRun) -or -not $taskProcess.CommandLine.Contains('KnotClient')) { throw 'Client PID no longer matches isolated QA run' }
    Stop-Process -Id $taskProcess.ProcessId -Force
}
