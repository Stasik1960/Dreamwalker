$ErrorActionPreference='Stop';$PSNativeCommandUseErrorActionPreference=$false
if(Get-Process -Name java,javaw -ErrorAction SilentlyContinue){throw 'Java still running'}
$env:JAVA_HOME='C:\Users\vakir\AppData\Roaming\.minecraft\runtime\java-runtime-gamma\windows\java-runtime-gamma'
$taskArgs=@('remapGalleryJar','remapDhPilotJar','--offline','--no-daemon','--max-workers=2','-I','tools\city-streaming-20261018\dh-gallery.init.gradle','-Pworldedit_api_jar=C:\Users\vakir\.codex\worktrees\3f07\DW\bloodborne complete\build\v127-dependencies\worldedit-mod-7.2.15.jar',('-Pdh_pilot_jar='+(Get-Location).Path+'\build\dh-city-pilot\dependencies\DistantHorizons-3.3.3-1.20.1-fabric-forge.jar'))
& '.\gradlew.bat' @taskArgs *> 'build\dh-city-pilot\build-pilot-ready.log'
@{exitCode=$LASTEXITCODE;end=[DateTime]::UtcNow.ToString('o');sourceSha256=(Get-FileHash -LiteralPath 'src\dhpilot\java\dev\dreamwalker\bloodbornedw\dh\CityDhPilot.java' -Algorithm SHA256).Hash.ToLowerInvariant()}|ConvertTo-Json|Set-Content -LiteralPath 'build\dh-city-pilot\build-pilot-ready-receipt.json'
