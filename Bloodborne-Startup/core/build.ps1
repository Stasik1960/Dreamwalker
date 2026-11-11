$ErrorActionPreference = 'Stop'
& 'C:\Users\vakir\.codex\dreamwalker\Assert-Workspace.ps1'
if (!$?) { throw 'Workspace guard failed' }
. 'C:\Users\vakir\.codex\dreamwalker\Initialize-Environment.ps1'
$work = 'D:\DreamWalker\.local\model-startup-seam\core'
$runtime = Get-Content -Raw 'D:\DreamWalker\.local\dw-fix-20261028\runtime\runtime.json' | ConvertFrom-Json
$minecraft = 'D:\DreamWalker\.local\state-startup-step2\qa\seam-step2\.fabric\remappedJars\minecraft-1.20.1-0.19.3\client-intermediary.jar'
$base = 'D:\DreamWalker\.local\state-startup-step2\Seam-Repair-Startup-Step2.jar'
New-Item -ItemType Directory -Force "$work\classes" | Out-Null
$source = Get-ChildItem "$work\src" -Recurse -Filter '*.java' | Select-Object -ExpandProperty FullName
& javac -J-Xmx256m -proc:none --release 17 -cp ((@($minecraft, $base) + $runtime.classpath) -join ';') -d "$work\classes" $source
if ($LASTEXITCODE -ne 0) { throw 'Compilation failed' }

