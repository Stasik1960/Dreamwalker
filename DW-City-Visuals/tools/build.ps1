param([string]$Task = 'remapJar')
$ErrorActionPreference = 'Stop'
& 'C:\Users\vakir\.codex\dreamwalker\Assert-Workspace.ps1'
if (-not $?) { throw 'Workspace guard failed' }
. 'C:\Users\vakir\.codex\dreamwalker\Initialize-Environment.ps1'
Set-Location -LiteralPath 'D:\DreamWalker'
$overlayRoot = Split-Path -Parent $PSScriptRoot
& 'D:\DreamWalker\.local\tools\gradle-8.10.2\bin\gradle.bat' -p $overlayRoot --project-cache-dir (Join-Path $overlayRoot 'gradle-project-cache') --offline --no-daemon --max-workers=1 $Task
exit $LASTEXITCODE
