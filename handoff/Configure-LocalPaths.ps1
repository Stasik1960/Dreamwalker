#requires -Version 7.2
[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$Root,
    [string]$ManifestPath,
    [string]$JavaHome17,
    [string]$PythonExecutable,
    [string]$GameDirectory
)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$rootPath=[IO.Path]::GetFullPath($Root).TrimEnd([IO.Path]::DirectorySeparatorChar)
if (-not (Test-Path -LiteralPath $rootPath -PathType Container)) { throw 'Restored package root does not exist.' }
if (-not $ManifestPath) { $ManifestPath=Join-Path $rootPath '.handoff-transfer/HANDOFF-MANIFEST.json' }
$manifest=Get-Content -LiteralPath $ManifestPath -Raw | ConvertFrom-Json -AsHashtable
if ($manifest.schemaVersion -ne 1) { throw 'Unsupported manifest version.' }
$localRoot=Join-Path $rootPath '.handoff-local'
if (Test-Path -LiteralPath $localRoot) { throw 'Local configuration already exists. Preserve it and choose a separate new restoration, or rename only this local configuration deliberately.' }
$absolute=[ordered]@{}
foreach ($entry in $manifest.paths.GetEnumerator()) {
    if ($entry.Value -isnot [string]) { continue }
    $relative=[string]$entry.Value
    if ([IO.Path]::IsPathRooted($relative) -or $relative -match '[\\:]|(^|/)\.\.?(/|$)') { throw "Manifest path must be portable: $($entry.Key)" }
    $resolved=[IO.Path]::GetFullPath((Join-Path $rootPath $relative))
    if (-not $resolved.StartsWith($rootPath+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)) { throw 'Path escaped package root.' }
    $absolute[$entry.Key]=$resolved
}
if (-not $JavaHome17) {
    foreach ($candidate in @($env:JAVA_HOME17,$env:JAVA_HOME)) {
        if ($candidate -and (Test-Path -LiteralPath (Join-Path $candidate 'bin/javac.exe'))) { $JavaHome17=$candidate; break }
    }
}
if ($JavaHome17) {
    $JavaHome17=[IO.Path]::GetFullPath($JavaHome17)
    if (-not (Test-Path -LiteralPath (Join-Path $JavaHome17 'bin/javac.exe') -PathType Leaf)) { throw 'JavaHome17 must identify a JDK, including bin/javac.exe.' }
    $release=Join-Path $JavaHome17 'release'
    if (-not (Test-Path -LiteralPath $release) -or (Get-Content -LiteralPath $release -Raw) -notmatch '(?m)^JAVA_VERSION="17(?:\.|"|\+)') { throw 'A Java 17 JDK is required.' }
}
if ($PythonExecutable) {
    $PythonExecutable=[IO.Path]::GetFullPath($PythonExecutable)
    if (-not (Test-Path -LiteralPath $PythonExecutable -PathType Leaf)) { throw 'Python executable does not exist.' }
}
if ($GameDirectory) {
    $GameDirectory=[IO.Path]::GetFullPath($GameDirectory)
    if (-not (Test-Path -LiteralPath $GameDirectory -PathType Container)) { throw 'Specify your own installed game directory, not the former author path.' }
}
[IO.Directory]::CreateDirectory($localRoot) | Out-Null
$config=[ordered]@{
    schemaVersion=1
    packageRoot=$rootPath
    repository=$manifest.repository
    tag=$manifest.tag
    sourceCommit=$manifest.commit
    paths=$absolute
    javaHome17=$JavaHome17
    pythonExecutable=$PythonExecutable
    gameDirectory=$GameDirectory
    workDirectory=(Join-Path $localRoot 'work')
    gradleUserHome=(Join-Path $localRoot 'gradle-cache')
    runtime=[ordered]@{minecraft='1.20.1';fabricLoaderInstalledBaseline='0.20.0';fabricLoaderCompile='0.16.10';heapMaxMiB=10240;renderDistance=12;simulationDistance=12}
    restrictions=@('Do not reconvert the source city or rebuild preparation.','Do not write to completedWorld or the restored packedWorld; create an independent working copy.','NBT packing is not static architecture migration.','Legacy controllers remain archival; rebind versions, SHA pins and local process policy before any use.','No old C:/E: or CodexControl storage binding is active here.')
}
$config | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath (Join-Path $localRoot 'local-paths.json') -Encoding utf8NoBOM
@'
Local paths are in local-paths.json. No source file, preparation manifest,
historical receipt, saved world, or SHA-bound tool was rewritten.

Build-Mod.ps1 is the supported portable build entry point.
Legacy Python controllers/build wrappers under project/build retain their
historical path/Guard/version/SHA assumptions as evidence. They are NOT ready
to run on a new machine. Before a required static pilot, use this configuration
to adapt a separate tool copy, retain original SHA pins and receipt, and explicitly
bind the intended .4 or .5 game JAR. Do not bypass lifecycle/safety gates blindly.

In particular, do not run old run_ordinary_city_pair*_8g.py as acceptance of the
full Limacina profile. It used a smaller test profile/view distance. Do not run
pack_city_owner_nbt.py again simply to restore this handoff: the packed world and
its reverse-proof/backup data are already supplied. That historical packer has
C:-only/installed-saves gates and would require a scoped, documented portable
adapter if a concrete later operation really needs it.

Configure did not start Java, Minecraft, a server, conversion, or migration.
The completed-world baseline and packed working snapshot are not a claim of
playability at 10 GiB heap and distance 12/12. Read HANDOFF-STATE.json.
'@ | Set-Content -LiteralPath (Join-Path $localRoot 'README.txt') -Encoding utf8NoBOM
Write-Host "Local path configuration written: $localRoot"
if (-not $JavaHome17) { Write-Host 'Before building, supply a Java 17 JDK to Build-Mod.ps1 -JavaHome17.' }
