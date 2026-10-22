$ErrorActionPreference='Stop'
$env:JAVA_HOME='C:\Users\vakir\AppData\Roaming\.minecraft\runtime\java-runtime-gamma\windows\java-runtime-gamma'
$taskEvidence=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..\..\research\city-materials-20261019'))
& '.\gradlew.bat' remapJar sourceFlatLightCheck -I tools/city-materials-20261019/materials.init.gradle --offline --no-daemon --max-workers=2 '-Pworldedit_api_jar=C:\Users\vakir\.codex\worktrees\3f07\DW\bloodborne complete\build\v127-dependencies\worldedit-mod-7.2.15.jar' *> (Join-Path $taskEvidence 'build.log')
$taskCode=$LASTEXITCODE
@{exitCode=$taskCode;utc=[DateTime]::UtcNow.ToString('o')}|ConvertTo-Json|Set-Content -LiteralPath (Join-Path $taskEvidence 'build-receipt.json') -Encoding utf8
if($taskCode){throw 'Material build/check failed; see build.log'}
$taskVersion=(Get-Content -LiteralPath gradle.properties | Where-Object {$_ -like 'mod_version=*'}).Split('=',2)[1]
$taskJar=Join-Path (Get-Location) ('build\libs\dreamwalker-bb-fabric-1.20.1-'+$taskVersion+'.jar')
@{status='PASS_BUILD_AND_FOCUSED_COORDINATE_CHECKS';version=$taskVersion;jar=$taskJar;sha256=(Get-FileHash -LiteralPath $taskJar -Algorithm SHA256).Hash.ToLowerInvariant()}|ConvertTo-Json|Set-Content -LiteralPath (Join-Path $taskEvidence 'built-jar.json') -Encoding utf8
