#requires -Version 7.2
<#
.SYNOPSIS
Restores one pinned Dreamwalker handoff into a new directory. Never starts Minecraft.
.DESCRIPTION
Independent ZIP assets, manifest SHA256, asset hashes, file hashes, no overwrite,
no reparse points, no archive links, no path traversal. No credentials are read.
For a private repository use -UseGh with your own existing GitHub CLI login.
#>
[CmdletBinding(DefaultParameterSetName='Url')]
param(
    [Parameter(Mandatory, ParameterSetName='Url')][uri]$ManifestUri,
    [Parameter(Mandatory, ParameterSetName='Local')][string]$ManifestPath,
    [Parameter(Mandatory, ParameterSetName='Gh')][switch]$UseGh,
    [Parameter(Mandatory, ParameterSetName='Gh')][string]$Repository,
    [Parameter(Mandatory, ParameterSetName='Gh')][string]$Tag,
    [Parameter(ParameterSetName='Gh')][string]$ManifestAssetName='MANIFEST.json',
    [Parameter(Mandatory)][ValidatePattern('^[a-fA-F0-9]{64}$')][string]$ManifestSha256,
    [Parameter(Mandatory)][string]$Destination,
    [string]$AssetDirectory,
    [string]$JavaHome17,
    [string]$PythonExecutable,
    [string]$GameDirectory,
    [switch]$KeepDownloads
)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest

function Assert-NoReparse([string]$Path) {
    $candidate=[IO.Path]::GetFullPath($Path)
    while ($candidate) {
        try {
            $attributes=[IO.File]::GetAttributes($candidate)
            if (($attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
                throw "Reparse point refused: $candidate"
            }
        } catch [IO.FileNotFoundException] {
            # A not-yet-created child is allowed; existing ancestors are still checked.
        } catch [IO.DirectoryNotFoundException] {
            # Missing descendants do not skip checks of existing parent directories.
        }
        $parent=[IO.Path]::GetDirectoryName($candidate)
        if ($parent -eq $candidate) { break }; $candidate=$parent
    }
}
function Assert-Relative([string]$Path) {
    if ([string]::IsNullOrWhiteSpace($Path) -or $Path -match '[\\:\x00-\x1f]' -or
        [IO.Path]::IsPathRooted($Path) -or $Path.StartsWith('/') -or $Path.Contains('//')) { throw "Unsafe archive path: $Path" }
    foreach ($part in $Path.TrimEnd('/').Split('/')) {
        if ($part -in @('', '.', '..') -or $part -match '[. ]$' -or
            $part -match '^(?i:CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(?:\.|$)' -or
            $part -match '[<>"|?*]') { throw "Unsafe Windows path component: $Path" }
    }
    if ($Path -match '^(?i:\.handoff-transfer|\.handoff-local)(?:/|$)') { throw 'Reserved local/transfer path in package' }
}
function Assert-Hash([string]$Path,[string]$Expected) {
    if ((Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash -ne $Expected) { throw "SHA256 mismatch: $Path" }
}
function Get-GhAsset([string]$Name,[string]$Directory,[string]$Repo,[string]$ReleaseTag) {
    & gh release download $ReleaseTag --repo $Repo --pattern $Name --dir $Directory
    if ($LASTEXITCODE -ne 0) { throw "gh download failed for $Name" }
    if (-not (Test-Path -LiteralPath (Join-Path $Directory $Name) -PathType Leaf)) { throw "gh returned no exact asset: $Name" }
}

$destinationRoot=[IO.Path]::GetFullPath($Destination)
Assert-NoReparse $destinationRoot
if (Test-Path -LiteralPath $destinationRoot) { throw 'Destination must not exist, even if it is empty. Choose a new folder.' }
$parentRoot=[IO.Path]::GetDirectoryName($destinationRoot)
if (-not (Test-Path -LiteralPath $parentRoot -PathType Container)) { throw 'Destination parent directory must already exist.' }
if ($PSCmdlet.ParameterSetName -eq 'Gh') {
    if ($Repository -notmatch '^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$' -or $Tag -notmatch '^[A-Za-z0-9_.-]+$' -or
        $ManifestAssetName -notmatch '^[A-Za-z0-9_.-]+\.json$') { throw 'Unsafe repository, tag, or manifest asset name' }
    if (-not (Get-Command gh -ErrorAction SilentlyContinue)) { throw 'Install GitHub CLI and authenticate as an account with release access, or use an accessible HTTPS manifest.' }
}
if ($PSCmdlet.ParameterSetName -eq 'Url' -and ($ManifestUri.Scheme -ne 'https' -or $ManifestUri.UserInfo)) { throw 'Manifest requires HTTPS without credentials in URL.' }
[IO.Directory]::CreateDirectory($destinationRoot) | Out-Null
$transfer=Join-Path $destinationRoot '.handoff-transfer'
[IO.Directory]::CreateDirectory($transfer) | Out-Null
$manifestDownload=Join-Path $transfer 'HANDOFF-MANIFEST.json'
try {
    switch ($PSCmdlet.ParameterSetName) {
        Url { Invoke-WebRequest -Uri $ManifestUri -OutFile $manifestDownload }
        Local { Assert-NoReparse $ManifestPath; [IO.File]::Copy([IO.Path]::GetFullPath($ManifestPath),$manifestDownload,$false) }
        Gh {
            Get-GhAsset $ManifestAssetName $transfer $Repository $Tag
            $ghManifest=Join-Path $transfer $ManifestAssetName
            if ($ghManifest -ne $manifestDownload) { [IO.File]::Move($ghManifest,$manifestDownload) }
        }
    }
    Assert-Hash $manifestDownload $ManifestSha256
    $manifest=Get-Content -LiteralPath $manifestDownload -Raw | ConvertFrom-Json -AsHashtable
    if ($manifest.schemaVersion -ne 1 -or $manifest.commit -notmatch '^[a-fA-F0-9]{40}$' -or
        $manifest.repository -notmatch '^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$' -or
        $manifest.tag -notmatch '^[A-Za-z0-9_.-]+$') { throw 'Invalid manifest version or immutable release identity.' }
    if ($UseGh -and ($manifest.repository -cne $Repository -or $manifest.tag -cne $Tag)) { throw 'Manifest repository/tag does not match the requested release.' }
    $fileRows=[Collections.Generic.Dictionary[string,object]]::new([StringComparer]::OrdinalIgnoreCase)
    [long]$unpackedBytes=0
    foreach ($row in $manifest.files) {
        Assert-Relative $row.path
        if ($row.path.EndsWith('/') -or $row.bytes -lt 0 -or $row.sha256 -notmatch '^[a-fA-F0-9]{64}$' -or $fileRows.ContainsKey($row.path)) { throw "Invalid/duplicate file manifest record: $($row.path)" }
        $fileRows.Add($row.path,$row); $unpackedBytes += [long]$row.bytes
    }
    if ($fileRows.Count -eq 0) { throw 'Empty file manifest' }
    $assetNames=[Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
    [long]$archiveBytes=0
    foreach ($asset in $manifest.assets) {
        if ($asset.name -notmatch '^[A-Za-z0-9_.-]+\.zip$' -or $asset.kind -ne 'zip' -or
            $asset.bytes -le 0 -or $asset.bytes -gt 1073741824 -or $asset.sha256 -notmatch '^[a-fA-F0-9]{64}$' -or
            -not $assetNames.Add($asset.name)) { throw 'Invalid/duplicate independent ZIP asset record' }
        $archiveBytes += [long]$asset.bytes
        $expectedUrl="https://github.com/$($manifest.repository)/releases/download/$($manifest.tag)/$($asset.name)"
        if ($asset.url -cne $expectedUrl) { throw "Asset is not bound to the pinned release: $($asset.name)" }
    }
    $drive=[IO.DriveInfo]::new([IO.Path]::GetPathRoot($destinationRoot))
    if ($drive.AvailableFreeSpace -lt ($unpackedBytes+$archiveBytes+1073741824)) { throw 'Insufficient free space for the full download plus extracted package and 1 GiB reserve.' }
    $restored=[Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
    foreach ($asset in $manifest.assets) {
        $archivePath=Join-Path $transfer $asset.name
        if ($AssetDirectory) {
            $localAsset=Join-Path ([IO.Path]::GetFullPath($AssetDirectory)) $asset.name
            Assert-NoReparse $localAsset
            [IO.File]::Copy($localAsset,$archivePath,$false)
        }
        elseif ($UseGh) { Get-GhAsset $asset.name $transfer $manifest.repository $manifest.tag }
        else { Invoke-WebRequest -Uri $asset.url -OutFile $archivePath }
        if ([IO.FileInfo]::new($archivePath).Length -ne [long]$asset.bytes) { throw "Asset size mismatch: $($asset.name)" }
        Assert-Hash $archivePath $asset.sha256
        $zip=[IO.Compression.ZipFile]::OpenRead($archivePath)
        try {
            foreach ($entry in $zip.Entries) {
                $name=$entry.FullName; Assert-Relative $name
                $unixType=(($entry.ExternalAttributes -shr 16) -band 0xF000)
                if ($unixType -notin @(0,0x8000,0x4000) -or ($entry.ExternalAttributes -band 0x400) -ne 0) { throw "Archive links/reparse entries refused: $name" }
                $target=[IO.Path]::GetFullPath((Join-Path $destinationRoot $name))
                if (-not $target.StartsWith($destinationRoot+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)) { throw "Archive path escaped destination: $name" }
                Assert-NoReparse $target
                if ($name.EndsWith('/')) {
                    if ($entry.Length -ne 0) { throw 'Nonempty archive directory entry' }
                    [IO.Directory]::CreateDirectory($target) | Out-Null; continue
                }
                if (-not $fileRows.ContainsKey($name) -or -not $restored.Add($name) -or $entry.Length -ne [long]$fileRows[$name].bytes) { throw "Unlisted, duplicate, or wrong-length archive file: $name" }
                [IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($target)) | Out-Null
                $inputStream=$entry.Open()
                $outputStream=[IO.File]::Open($target,[IO.FileMode]::CreateNew,[IO.FileAccess]::Write,[IO.FileShare]::None)
                try { $inputStream.CopyTo($outputStream) } finally { $outputStream.Dispose(); $inputStream.Dispose() }
            }
        } finally { $zip.Dispose() }
        if (-not $KeepDownloads) { Remove-Item -LiteralPath $archivePath }
    }
    if ($restored.Count -ne $fileRows.Count) { throw "Package incomplete: $($restored.Count)/$($fileRows.Count) files" }
    foreach ($name in $fileRows.Keys) {
        $target=Join-Path $destinationRoot $name; Assert-NoReparse $target
        if ([IO.FileInfo]::new($target).Length -ne [long]$fileRows[$name].bytes) { throw "Extracted size mismatch: $name" }
        Assert-Hash $target $fileRows[$name].sha256
    }
    if (Test-Path -LiteralPath (Join-Path $destinationRoot 'MANIFEST.json')) { throw 'Reserved root manifest already exists.' }
    [IO.File]::Copy($manifestDownload,(Join-Path $destinationRoot 'MANIFEST.json'),$false)
    $receipt=[ordered]@{status='VERIFIED';manifestSha256=$ManifestSha256.ToLowerInvariant();repository=$manifest.repository;tag=$manifest.tag;commit=$manifest.commit;files=$restored.Count;bytes=$unpackedBytes;utc=[datetime]::UtcNow.ToString('o');root=$destinationRoot}
    $receipt | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $transfer 'RESTORE-RECEIPT.json') -Encoding utf8NoBOM
    $configure=Join-Path $destinationRoot 'handoff/Configure-LocalPaths.ps1'
    if (-not (Test-Path -LiteralPath $configure -PathType Leaf)) { throw 'Verified package lacks handoff/Configure-LocalPaths.ps1' }
    & $configure -Root $destinationRoot -ManifestPath $manifestDownload -JavaHome17 $JavaHome17 -PythonExecutable $PythonExecutable -GameDirectory $GameDirectory
    Write-Host "Verified handoff restored: $destinationRoot"
    Write-Host 'Read START-HERE-RU.md and CONTINUE-RU.md. No world was launched or modified.'
} catch {
    $_.ToString() | Set-Content -LiteralPath (Join-Path $transfer 'RESTORE-FAILED.txt') -Encoding utf8NoBOM
    throw 'Restore failed. The new destination was left for inspection; do not use it as a verified package. Choose another new folder to retry. See .handoff-transfer/RESTORE-FAILED.txt.'
}
