[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)]
    [ValidateSet('decoder','client','socket')]
    [string]$Mode,
    [Parameter(Mandatory=$true)]
    [string]$MpmJar,
    [Parameter(Mandatory=$true)]
    [string]$ClientDir,
    [string]$JdkHome = $env:JAVA_HOME,
    [string]$LoomCache = (Join-Path $env:USERPROFILE '.gradle\caches\fabric-loom'),
    [string]$FabricLoaderJar,
    [string]$Python = 'python',
    [switch]$Baseline,
    [switch]$PacketLimit
)
$ErrorActionPreference = 'Stop'
$mpm = (Resolve-Path -LiteralPath $MpmJar).ProviderPath
if (!$JdkHome) { throw 'Set JAVA_HOME or pass -JdkHome to a JDK 21 installation.' }
$ClientDir = (Resolve-Path -LiteralPath $ClientDir).ProviderPath
$java = Join-Path $JdkHome 'bin\java.exe'
$build = [IO.Directory]::CreateDirectory((Join-Path $PSScriptRoot 'build')).FullName

if ($Mode -eq 'socket') {
    if (!$FabricLoaderJar) { $FabricLoaderJar = Join-Path $ClientDir '0.19.3.jar' }
    $FabricLoaderJar = (Resolve-Path -LiteralPath $FabricLoaderJar).ProviderPath
    $peer = Join-Path $PSScriptRoot 'SocketQa.py'
    $GameServerLib = Join-Path $LoomCache '1.20.1\minecraft-extracted_server.jar'
    $SocketArgs = @(
        '-u', $peer, '--jar', $mpm, '--client', $ClientDir,
        '--java', $java, '--vanilla', $GameServerLib,
        '--fabric-loader', $FabricLoaderJar, '--run-root', (Join-Path $build 'socket')
    )
    if ($Baseline) { $SocketArgs += '--baseline' }
    if ($PacketLimit) { $SocketArgs += '--packet-limit' }
    & $Python @SocketArgs
    exit $LASTEXITCODE
}

$intermediaryCache = Join-Path $LoomCache 'minecraftMaven\net\minecraft\minecraft-merged-intermediary'
$mc = Get-ChildItem -LiteralPath $intermediaryCache -Recurse -File -Filter 'minecraft-merged-intermediary-1.20.1*.jar' |
    Sort-Object FullName |
    Select-Object -First 1 -ExpandProperty FullName
if (!$mc) { throw "Minecraft 1.20.1 intermediary JAR missing at $intermediaryCache." }
$libs = @(Get-ChildItem -LiteralPath (Join-Path $ClientDir 'libraries') -Recurse -File -Filter '*.jar' |
    Where-Object { $_.Name -notmatch '-natives' } |
    Select-Object -ExpandProperty FullName)
$classPath = (@($mpm,$mc) + $libs) -join ';'

if ($Mode -eq 'decoder') {
    & $java -cp $classPath (Join-Path $PSScriptRoot 'ReproduceDw3.java')
    exit $LASTEXITCODE
}

Add-Type -AssemblyName System.IO.Compression.FileSystem
$api = Get-ChildItem -LiteralPath (Join-Path $ClientDir 'mods') -File -Filter 'fabric-api-*.jar' |
    Sort-Object FullName |
    Select-Object -First 1 -ExpandProperty FullName
if (!$api) { throw 'Fabric API JAR missing in the specified client mods folder.' }
$network = Join-Path $build 'fabric-networking-api-v1.jar'
$archive = [IO.Compression.ZipFile]::OpenRead($api)
try {
    $entry = $archive.Entries | Where-Object { $_.FullName -match '^META-INF/jars/fabric-networking-api-v1-[^/]+\.jar$' } | Select-Object -First 1
    if (!$entry) { throw 'Fabric networking module missing in Fabric API JAR.' }
    [IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $network, $true)
} finally {
    $archive.Dispose()
}
$classPath += ";$network"
$clientTest = Join-Path $PSScriptRoot 'client-test'
$classes = [IO.Directory]::CreateDirectory((Join-Path $build 'client')).FullName
$javac = Join-Path $JdkHome 'bin\javac.exe'
& $javac -proc:none -cp $classPath -d $classes (Join-Path $clientTest 'ClientFramingQa.java') (Join-Path $clientTest 'net\fabricmc\fabric\api\client\networking\v1\ClientPlayNetworking.java')
if ($LASTEXITCODE -ne 0) { throw "Harness javac exited with code $LASTEXITCODE." }
& $java -cp "$classes;$classPath" ClientFramingQa noppes.mpm.packets.client.ClientProfileTransport send
exit $LASTEXITCODE
