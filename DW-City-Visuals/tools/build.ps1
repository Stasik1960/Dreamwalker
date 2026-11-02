param([ValidateSet('remapJar','build','remapVisualQaJar')][string]$Task='remapJar',[switch]$InsideGuard)
$ErrorActionPreference='Stop'
$moduleRoot=Split-Path -Parent $PSScriptRoot
$guard='C:\Users\vakir\CodexControl\Guard.ps1'
$result=Join-Path $moduleRoot 'build-result.json'
$log=Join-Path $moduleRoot 'build-console.log'
if(!$InsideGuard -and (Test-Path -LiteralPath $guard)) {
    $arguments='-NoProfile -ExecutionPolicy Bypass -File "'+$PSCommandPath+'" -InsideGuard -Task '+$Task
    & $guard -Mode Operation -Executable (Get-Process -Id $PID).Path -Arguments $arguments -WorkingDirectory $moduleRoot
    if(Test-Path -LiteralPath $log){Get-Content -LiteralPath $log}
    if(!(Test-Path -LiteralPath $result)){throw 'Build result missing'}
    exit (Get-Content -LiteralPath $result -Raw | ConvertFrom-Json).exitCode
}
Push-Location -LiteralPath $moduleRoot
try {
    $ErrorActionPreference='Continue'
    & (Join-Path $moduleRoot 'gradlew.bat') --no-daemon --max-workers=1 $Task *> $log
    $code=$LASTEXITCODE
    @{exitCode=$code;task=$Task}|ConvertTo-Json|Set-Content -LiteralPath $result -Encoding UTF8
    exit $code
} finally {Pop-Location}
