$ErrorActionPreference='Stop'
& 'C:\Users\vakir\.codex\dreamwalker\Assert-Workspace.ps1'
if (!$?) { throw 'Guard failed' }
. 'C:\Users\vakir\.codex\dreamwalker\Initialize-Environment.ps1'
$root='D:\DreamWalker\.local'
$work=Join-Path $root 'state-startup-step2'
$loom=Join-Path $root 'gradle\caches\fabric-loom'
$modules=Join-Path $root 'gradle\caches\modules-2\files-2.1'
$runtime=Join-Path $root 'dw-fix-20261028\runtime\libraries'
$mc=(Get-ChildItem "$loom\minecraftMaven\net\minecraft\minecraft-merged" -Recurse -Filter 'minecraft-merged-1.20.1-net.fabricmc.yarn*.jar' | Select-Object -First 1).FullName
$guava=Join-Path $runtime 'com\google\guava\guava\31.1-jre\guava-31.1-jre.jar'
$mixin=(Get-ChildItem "$runtime\net\fabricmc\sponge-mixin" -Recurse -Filter '*.jar' | Select-Object -First 1).FullName
New-Item -ItemType Directory -Force -Path "$work\classes" | Out-Null
$mcLibs=Get-ChildItem "$runtime\com\mojang" -Recurse -Filter '*.jar' | Select-Object -ExpandProperty FullName
$source=Get-ChildItem "$work\src" -Recurse -Filter '*.java' | Select-Object -ExpandProperty FullName
& javac -proc:none --release 17 -cp (@($mc,$guava,$mixin)+$mcLibs -join ';') -d "$work\classes" $source
if ($LASTEXITCODE -ne 0) { throw 'javac failed' }
& jar --create --file "$work\named.jar" -C "$work\classes" dev
if ($LASTEXITCODE -ne 0) { throw 'jar failed' }
$tiny=(Get-ChildItem "$modules\net.fabricmc\tiny-remapper\0.10.3" -Recurse -Filter 'tiny-remapper-0.10.3.jar' | Select-Object -First 1).FullName
$mappingio=(Get-ChildItem "$modules\net.fabricmc\mapping-io\0.5.1" -Recurse -Filter 'mapping-io-0.5.1.jar' | Select-Object -First 1).FullName
$asm=Get-ChildItem "$runtime\org\ow2\asm" -Recurse -Filter 'asm*-9.10.1.jar' | Select-Object -ExpandProperty FullName
$mappings=Join-Path $loom '1.20.1\net.fabricmc.yarn.1_20_1.1.20.1+build.10-v2\mappings.tiny'
Remove-Item -LiteralPath "$work\intermediary.jar" -ErrorAction SilentlyContinue
& java '-Xmx600m' -cp (@($tiny,$mappingio)+$asm -join ';') net.fabricmc.tinyremapper.Main "$work\named.jar" "$work\intermediary.jar" $mappings named intermediary $mc
if ($LASTEXITCODE -ne 0) { throw 'remap failed' }



