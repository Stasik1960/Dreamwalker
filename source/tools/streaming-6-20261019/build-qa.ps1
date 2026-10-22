$ErrorActionPreference='Stop'
$env:JAVA_HOME='C:\Users\vakir\AppData\Roaming\.minecraft\runtime\java-runtime-gamma\windows\java-runtime-gamma'
& '.\gradlew.bat' remapStreamingQaJar -I tools/streaming-6-20261019/qa.init.gradle -x compileJava -x processResources --offline --no-daemon --max-workers=2 '-Pworldedit_api_jar=C:\Users\vakir\.codex\worktrees\3f07\DW\bloodborne complete\build\v127-dependencies\worldedit-mod-7.2.15.jar' *> (Join-Path $PSScriptRoot 'build-qa.log')
@{exitCode=$LASTEXITCODE;utc=[DateTime]::UtcNow.ToString('o')}|ConvertTo-Json|Set-Content -LiteralPath (Join-Path $PSScriptRoot 'build-qa.json') -Encoding UTF8
