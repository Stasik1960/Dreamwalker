from pathlib import Path
import shutil
base = Path('build/complete-accepted-repair/runtime-qa')
src = base / 'server'
out = base / 'server-attempt3'
world = Path('build/complete-accepted-repair/Bloodborne-City-2.1.0-rc.3-checkpoint')
if out.exists(): raise SystemExit('attempt3 already exists')
out.mkdir()
for name in ('fabric-server-launch.jar', 'eula.txt'):
    shutil.copy2(src / name, out / name)
for name in ('.fabric', 'libraries', 'versions'):
    shutil.copytree(src / name, out / name)
(out / 'mods').mkdir()
for name in ('fabric-api-0.92.9+1.20.1.jar', 'bloodborne-blocks-2.1.0-rc.3.jar'):
    shutil.copy2(src / 'mods' / name, out / 'mods' / name)
shutil.copytree(world, out / 'world')
(out / 'server.properties').write_text('online-mode=false\nserver-ip=127.0.0.1\nserver-port=25582\nlevel-name=world\nenable-command-block=true\nview-distance=4\nsimulation-distance=4\nmax-tick-time=60000\n')
runner = (base / 'run-rc3.py').read_text(encoding='utf8')
runner = runner.replace("QA = Path(__file__).resolve().parent / 'server'", "QA = Path(__file__).resolve().parent / 'server-attempt3'")
(base / 'run-attempt3.py').write_text(runner, encoding='utf8')
