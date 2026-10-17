#requires -Version 7.2
[CmdletBinding()]
param(
    [string]$Root=([IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))),
    [string]$JavaHome17,
    [string]$Version='0.1.0-v12.7.4-owner32-rollback-static-architecture.5-packed-owner-nbt.1',
    [switch]$Offline,
    [switch]$SkipTransactionChecks
)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$rootPath=[IO.Path]::GetFullPath($Root)
$configPath=Join-Path $rootPath '.handoff-local/local-paths.json'
if (-not (Test-Path -LiteralPath $configPath)) { throw 'Run Configure-LocalPaths.ps1 after a verified restoration first.' }
$config=Get-Content -LiteralPath $configPath -Raw | ConvertFrom-Json -AsHashtable
if (-not $JavaHome17) { $JavaHome17=$config.javaHome17 }
if (-not $JavaHome17) { throw 'Supply -JavaHome17 with a Java 17 JDK path.' }
$JavaHome17=[IO.Path]::GetFullPath($JavaHome17)
$releasePath=Join-Path $JavaHome17 'release'
if (-not (Test-Path -LiteralPath (Join-Path $JavaHome17 'bin/javac.exe')) -or
    -not (Test-Path -LiteralPath $releasePath) -or
    (Get-Content -LiteralPath $releasePath -Raw) -notmatch '(?m)^JAVA_VERSION="17(?:\.|"|\+)') { throw 'Java 17 JDK required.' }
if ($Version -notmatch '^[A-Za-z0-9_.+-]+$') { throw 'Invalid mod version.' }
$projectPath=$config.paths.project
$worldEditPath=$config.paths.worldEditApiJar
if (-not (Test-Path -LiteralPath (Join-Path $projectPath 'gradlew.bat')) -or -not (Test-Path -LiteralPath $worldEditPath)) { throw 'Project wrapper or packaged WorldEdit compile API is missing.' }
$buildLog=Join-Path $rootPath ('.handoff-local/build-'+[datetime]::UtcNow.ToString('yyyyMMdd-HHmmss')+'.log')
$buildArgs=@('--no-daemon','--max-workers=1',"-Dorg.gradle.java.home=$JavaHome17",'-Dorg.gradle.jvmargs=-Xmx2G -XX:ActiveProcessorCount=2 -Dfile.encoding=UTF-8',"-Pmod_version=$Version","-Pworldedit_api_jar=$worldEditPath",'remapJar','sourcesJar')
if ($Offline) { $buildArgs=@('--offline')+$buildArgs }
if (-not $SkipTransactionChecks) { $buildArgs+=@('transactionCheck') }
$priorJava=$env:JAVA_HOME; $priorPath=$env:PATH; $priorGradle=$env:GRADLE_USER_HOME
try {
    $env:JAVA_HOME=$JavaHome17
    $env:PATH=(Join-Path $JavaHome17 'bin')+[IO.Path]::PathSeparator+$env:PATH
    $env:GRADLE_USER_HOME=$config.gradleUserHome
    Push-Location -LiteralPath $projectPath
    try {
        & (Join-Path $projectPath 'gradlew.bat') @buildArgs 2>&1 | Tee-Object -FilePath $buildLog
        $buildExit=$LASTEXITCODE
    } finally { Pop-Location }
    if ($buildExit -ne 0) { throw "Gradle failed with exit $buildExit. See $buildLog" }
    $jar=Join-Path $projectPath "build/libs/dreamwalker-bb-fabric-1.20.1-$Version.jar"
    if (-not (Test-Path -LiteralPath $jar)) { throw 'Gradle returned success but the requested remapped game JAR is absent.' }
    [ordered]@{status='BUILT_NOT_GAMEPLAY_VERIFIED';version=$Version;jar=$jar;sha256=(Get-FileHash -LiteralPath $jar -Algorithm SHA256).Hash.ToLowerInvariant();bytes=(Get-Item -LiteralPath $jar).Length;log=$buildLog;transactionChecks=(-not $SkipTransactionChecks);utc=[datetime]::UtcNow.ToString('o')} |
        ConvertTo-Json -Depth 6 | Set-Content -LiteralPath ($buildLog+'.json') -Encoding utf8NoBOM
    Write-Host "Built: $jar"
    Write-Host 'This build is from the current .5 source snapshot. Reusing a .4 version label does not reconstruct .4 sources. Use the packaged immutable .4 JAR/source archive for its baseline.'
} finally {
    $env:JAVA_HOME=$priorJava; $env:PATH=$priorPath; $env:GRADLE_USER_HOME=$priorGradle
}
