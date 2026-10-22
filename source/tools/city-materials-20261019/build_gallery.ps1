param([string]$FrozenName='dreamwalker-gallery-material-probe.jar',[string]$LogPrefix='gallery-build')
$ErrorActionPreference='Stop'
$env:JAVA_HOME='C:\Users\vakir\AppData\Roaming\.minecraft\runtime\java-runtime-gamma\windows\java-runtime-gamma'
$taskEvidence=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..\..\research\city-materials-20261019'))
& '.\gradlew.bat' remapGalleryJar -x compileJava -x processResources --offline --no-daemon --max-workers=2 '-Pworldedit_api_jar=C:\Users\vakir\.codex\worktrees\3f07\DW\bloodborne complete\build\v127-dependencies\worldedit-mod-7.2.15.jar' *> (Join-Path $taskEvidence ($LogPrefix+'.log'))
if($LASTEXITCODE){throw 'Material QA build failed; see gallery-build.log'}
$taskVersion=(Get-Content -LiteralPath gradle.properties | Where-Object {$_ -like 'mod_version=*'}).Split('=',2)[1]
$taskJar=Join-Path (Get-Location) ('build\libs\dreamwalker-gallery-author-'+$taskVersion+'.jar')
$taskFrozen=Join-Path $taskEvidence $FrozenName
if(Test-Path -LiteralPath $taskFrozen){throw 'Preserve previous frozen QA JAR'}
Copy-Item -LiteralPath $taskJar -Destination $taskFrozen
@{status='PASS';jar=$taskFrozen;sha256=(Get-FileHash -LiteralPath $taskFrozen -Algorithm SHA256).Hash.ToLowerInvariant()}|ConvertTo-Json|Set-Content -LiteralPath (Join-Path $taskEvidence ($LogPrefix+'.json')) -Encoding utf8
