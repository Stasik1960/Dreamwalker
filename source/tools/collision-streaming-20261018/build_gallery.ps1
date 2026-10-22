$ErrorActionPreference='Stop'
$env:JAVA_HOME='C:\Users\vakir\AppData\Roaming\.minecraft\runtime\java-runtime-gamma\windows\java-runtime-gamma'
$taskEvidence=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..\..\deliverables\collision-streaming-20261018\evidence'))
& '.\gradlew.bat' remapGalleryJar -x compileJava -x processResources --offline --no-daemon --max-workers=2 '-Pworldedit_api_jar=C:\Users\vakir\.codex\worktrees\3f07\DW\bloodborne complete\build\v127-dependencies\worldedit-mod-7.2.15.jar' *> (Join-Path $taskEvidence 'gallery-build.log')
if($LASTEXITCODE){throw 'QA gallery build failed; see gallery-build.log'}
$taskVersion=(Get-Content -LiteralPath gradle.properties | Where-Object {$_ -like 'mod_version=*'}).Split('=',2)[1]
$taskJar=Join-Path (Get-Location) ('build\libs\dreamwalker-gallery-author-'+$taskVersion+'.jar')
$taskFrozen=Join-Path $taskEvidence 'dreamwalker-gallery-route-probe.jar'
if(Test-Path -LiteralPath $taskFrozen){throw 'Preserve previous frozen gallery JAR'}
Copy-Item -LiteralPath $taskJar -Destination $taskFrozen
@{status='PASS';jar=$taskFrozen;sha256=(Get-FileHash -LiteralPath $taskFrozen -Algorithm SHA256).Hash.ToLowerInvariant()}|ConvertTo-Json|Set-Content -LiteralPath (Join-Path $taskEvidence 'gallery-build.json') -Encoding utf8
