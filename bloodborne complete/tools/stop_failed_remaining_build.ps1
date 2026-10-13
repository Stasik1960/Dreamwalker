param([Parameter(Mandatory=$true)][string]$RunName)
$ErrorActionPreference = 'Stop'
if ($RunName -notmatch '^[a-zA-Z0-9_-]+$') { throw 'Unsafe run name' }
$taskRoot = Split-Path -Parent $PSScriptRoot
$taskLog = Join-Path $taskRoot "build\remaining-transfer\$RunName\gradle.log"
if (-not ((Get-Content -LiteralPath $taskLog -Tail 25) -match '^BUILD FAILED')) { throw 'Build has not reported terminal failure' }
$taskWrappers = @(Get-CimInstance Win32_Process -Filter "name = 'java.exe'" | Where-Object {
    $_.CommandLine.Contains('org.gradle.wrapper.GradleWrapperMain') -and
    $_.CommandLine.Contains("-Pgametest_run_name=$RunName ") -and
    $_.CommandLine.Contains($taskRoot)
})
if ($taskWrappers.Count -gt 1) { throw 'Ambiguous failed build process' }
foreach ($taskWrapper in $taskWrappers) {
    $taskDaemons = @(Get-CimInstance Win32_Process -Filter "name = 'java.exe'" | Where-Object {
        $_.ParentProcessId -eq $taskWrapper.ProcessId -and $_.CommandLine.Contains('org.gradle.launcher.daemon.bootstrap.GradleDaemon')
    })
    foreach ($taskDaemon in $taskDaemons) { Stop-Process -Id $taskDaemon.ProcessId -Force }
}
