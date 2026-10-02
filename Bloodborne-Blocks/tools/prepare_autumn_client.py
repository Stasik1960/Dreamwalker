"""Prepare a fresh disposable local game save for actual visual comparison."""
import shutil
from pathlib import Path
from fixture_level_metadata import create_level_metadata
from world_io import write_nbt

ROOT=Path(__file__).resolve().parents[1]
client=ROOT/'build/autumn-client-verified'
target=client/'saves/AutumnCityVerified'
if target.exists():
    raise SystemExit('Disposable client save already exists; refusing overwrite')
shutil.copytree(ROOT/'build/autumn-launch/Main-City-Release',target)
# Only the disposable copy receives vanilla metadata and a fresh local player.
write_nbt(target/'level.dat',create_level_metadata((-460,130,-60),level_name='Autumn visual comparison'))
client.joinpath('resourcepacks').mkdir(parents=True,exist_ok=True)
for path in (ROOT/'build/autumn-launch/Autumn-Variants').glob('*.zip'):
    shutil.copy2(path,client/'resourcepacks'/path.name)
(client/'options.txt').write_text('''version:3465
lang:ru_ru
renderDistance:16
simulationDistance:5
gamma:0.8
maxFps:60
enableVsync:false
fullscreen:false
overrideWidth:1600
overrideHeight:900
guiScale:2
bobView:false
fov:0.0
graphicsMode:1
ao:true
resourcePacks:["vanilla","fabric","file/02-classic-amber.zip"]
soundCategory_master:0.0
''',encoding='utf8')
print(target)
