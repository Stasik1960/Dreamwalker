[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$BaseJar,
    [string]$JdkHome = $env:JAVA_HOME,
    [string]$GradleCache,
    [string]$MinecraftJar,
    [string]$FabricApiJar,
    [string]$FabricLoaderJar,
    [string[]]$VanillaClasspath,
    [string[]]$AdditionalClasspath,
    [string]$OutputPath = (Join-Path $PSScriptRoot 'build/MorePlayerModels-Fabric-1.20.1-1.0.6-dw.4.jar')
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version 3
$baseVersion = '1.0.6-dw.3'
$version = '1.0.6-dw.4'
$baseHash = '9D49536AD3BB099EB716942A98A00ADCA731232F484F2137F83EBBDCD7D6ADBC'
$fabricApiVersion = '0.92.12+1.20.1'
$intermediaryVersion = '1.20.1-net.fabricmc.yarn.1_20_1.1.20.1+build.10-v2'

function Resolve-InputFile([string]$Path, [string]$Description) {
    $item = Get-Item -LiteralPath $Path -ErrorAction SilentlyContinue
    if (!$item -or $item.PSIsContainer) {
        throw "$Description not found at $Path"
    }
    return $item.FullName
}

function Find-CachedJar([string]$Group, [string]$Artifact, [string]$Version) {
    $artifactPath = Join-Path $GradleCache "modules-2/files-2.1/$Group/$Artifact/$Version"
    if (Test-Path -LiteralPath $artifactPath -PathType Container) {
        $file = Get-ChildItem -LiteralPath $artifactPath -File -Recurse -Filter "$Artifact-$Version.jar" |
            Sort-Object FullName |
            Select-Object -First 1
        if ($file) {
            return $file.FullName
        }
    }
    throw "Missing cached dependency $Group/$Artifact/$Version at $artifactPath"
}

function Find-JdkTool([string]$Name) {
    if ($JdkHome) {
        $binary = Join-Path $JdkHome "bin/$Name"
        if ($env:OS -eq 'Windows_NT') {
            $binary += '.exe'
        }
        return Resolve-InputFile $binary 'JDK tool'
    }
    $tool = Get-Command -Name $Name -CommandType Application -ErrorAction SilentlyContinue |
        Select-Object -First 1
    if (!$tool) {
        throw "JDK tool $Name was not found. Set -JdkHome or JAVA_HOME to a JDK 17 or newer."
    }
    return $tool.Source
}

function Resolve-Classpath([string[]]$Paths) {
    foreach ($path in $Paths) {
        Resolve-InputFile $path 'Compile classpath JAR'
    }
}

function Copy-FabricModules([string]$Path, [string]$Destination) {
    $archive = [IO.Compression.ZipFile]::OpenRead($Path)
    try {
        foreach ($module in @('fabric-api-base', 'fabric-lifecycle-events-v1', 'fabric-networking-api-v1')) {
            $pattern = '^META-INF/jars/' + [regex]::Escape($module) + '-[^/]+\.jar$'
            $entries = @($archive.Entries | Where-Object { $_.FullName -match $pattern })
            if ($entries.Count -ne 1) {
                throw "Expected one $module in $Path. Found $($entries.Count)."
            }
            $target = Join-Path $Destination "$module.jar"
            [IO.Compression.ZipFileExtensions]::ExtractToFile($entries[0], $target)
            $target
        }
    } finally {
        $archive.Dispose()
    }
}

function Clear-BuildStage([string]$StagePath, [string]$OutputDirectory) {
    $resolved = (Resolve-Path -LiteralPath $StagePath).ProviderPath
    $parent = [IO.Directory]::GetParent($resolved).FullName
    if ($resolved -ne $StagePath -or $parent -ne $OutputDirectory) {
        throw "Unexpected temporary build path $resolved"
    }
    $pending = [System.Collections.Generic.Stack[string]]::new()
    $pending.Push($resolved)
    while ($pending.Count -gt 0) {
        $item = Get-Item -LiteralPath ($pending.Pop()) -Force
        if ($item.Attributes -band [IO.FileAttributes]::ReparsePoint) {
            throw "Temporary build directory contains a reparse point at $($item.FullName)"
        }
        if ($item.PSIsContainer) {
            foreach ($child in (Get-ChildItem -LiteralPath $item.FullName -Force)) {
                $pending.Push($child.FullName)
            }
        }
    }
    Remove-Item -LiteralPath $resolved -Recurse -Force
}

function Assert-NoReparseAncestors([string]$Path) {
    for ($directory = [IO.DirectoryInfo]::new($Path); $directory; $directory = $directory.Parent) {
        if ($directory.Exists -and ($directory.Attributes -band [IO.FileAttributes]::ReparsePoint)) {
            throw "The build directory is redirected at $($directory.FullName). Choose another output path."
        }
    }
}

$BaseJar = Resolve-InputFile $BaseJar "MorePlayerModels $baseVersion input JAR"
if ((Get-FileHash -LiteralPath $BaseJar -Algorithm SHA256).Hash -ne $baseHash) {
    throw "The input JAR does not match the supported DreamWalker $baseVersion build (SHA-256 $baseHash)."
}
if (!$GradleCache) {
    $gradleHome = $env:GRADLE_USER_HOME
    if (!$gradleHome) {
        $profileRoot = $env:USERPROFILE
        if (!$profileRoot) {
            $profileRoot = $env:HOME
        }
        $gradleHome = Join-Path $profileRoot '.gradle'
    }
    $GradleCache = Join-Path $gradleHome 'caches'
}
if (!$MinecraftJar) {
    $MinecraftJar = Join-Path $GradleCache "fabric-loom/minecraftMaven/net/minecraft/minecraft-merged-intermediary/$intermediaryVersion/minecraft-merged-intermediary-$intermediaryVersion.jar"
}
$MinecraftJar = Resolve-InputFile $MinecraftJar 'Minecraft 1.20.1 Loom intermediary JAR (use -MinecraftJar to override)'
if (!$FabricApiJar) {
    $FabricApiJar = Find-CachedJar 'net.fabricmc.fabric-api' 'fabric-api' $fabricApiVersion
}
$FabricApiJar = Resolve-InputFile $FabricApiJar 'Fabric API 1.20.1 JAR'
if (!$FabricLoaderJar) {
    # Only API annotations are needed at compile time. Runtime uses dw.3's loader dependency.
    $FabricLoaderJar = Find-CachedJar 'net.fabricmc' 'fabric-loader' '0.16.10'
}
$FabricLoaderJar = Resolve-InputFile $FabricLoaderJar 'Fabric Loader compile-time JAR'
if (!$VanillaClasspath) {
    $VanillaClasspath = @(
        Find-CachedJar 'io.netty' 'netty-buffer' '4.1.82.Final'
        Find-CachedJar 'io.netty' 'netty-common' '4.1.82.Final'
        Find-CachedJar 'com.mojang' 'brigadier' '1.1.8'
        Find-CachedJar 'com.mojang' 'datafixerupper' '6.0.8'
        Find-CachedJar 'com.mojang' 'logging' '1.1.1'
        Find-CachedJar 'org.slf4j' 'slf4j-api' '2.0.1'
        Find-CachedJar 'com.google.guava' 'guava' '31.1-jre'
        Find-CachedJar 'com.google.code.gson' 'gson' '2.10'
        Find-CachedJar 'it.unimi.dsi' 'fastutil' '8.5.9'
    )
}
$java = Find-JdkTool 'java'
$javac = Find-JdkTool 'javac'
$sourceDir = Join-Path $PSScriptRoot 'src'
if (!(Test-Path -LiteralPath $sourceDir -PathType Container)) {
    throw "Missing Java sources directory $sourceDir"
}
$sources = @(Get-ChildItem -LiteralPath $sourceDir -Recurse -File -Filter '*.java' |
    Sort-Object FullName |
    Select-Object -ExpandProperty FullName)
if (!$sources.Count) {
    throw "No Java sources in $sourceDir"
}
$packager = Resolve-InputFile (Join-Path $PSScriptRoot 'tools/PackagePatchedJar.java') 'JAR packaging helper'
$OutputPath = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($OutputPath)
if ($OutputPath -eq $BaseJar -or (Test-Path -LiteralPath $OutputPath -PathType Container)) {
    throw 'Choose a separate output file instead of the input JAR or a directory.'
}
$outputParent = [IO.Path]::GetDirectoryName($OutputPath)
Assert-NoReparseAncestors $outputParent
$outputDir = [IO.Directory]::CreateDirectory($outputParent).FullName
$stage = [IO.Directory]::CreateDirectory((Join-Path $outputDir ('.mpm-build-' + [guid]::NewGuid()))).FullName
try {
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $fabricModules = @(Copy-FabricModules $FabricApiJar $stage)
    $dependencies = @(Resolve-Classpath $VanillaClasspath)
    if ($AdditionalClasspath) {
        $dependencies += @(Resolve-Classpath $AdditionalClasspath)
    }
    $classpath = (@($BaseJar, $MinecraftJar, $FabricLoaderJar) + $fabricModules + $dependencies) -join [IO.Path]::PathSeparator
    $classes = [IO.Directory]::CreateDirectory((Join-Path $stage 'classes')).FullName
    & $javac --release 17 -encoding UTF-8 -proc:none -implicit:none -classpath $classpath -d $classes @sources
    if ($LASTEXITCODE -ne 0) {
        throw "javac exited with code $LASTEXITCODE"
    }
    $patched = Join-Path $stage 'patched.jar'
    & $java $packager $BaseJar $classes $patched $baseVersion $version
    if ($LASTEXITCODE -ne 0) {
        throw "JAR packaging exited with code $LASTEXITCODE"
    }
    Move-Item -LiteralPath $patched -Destination $OutputPath -Force
    Get-FileHash -LiteralPath $OutputPath -Algorithm SHA256
} finally {
    Clear-BuildStage $stage $outputDir
}
