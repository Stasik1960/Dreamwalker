"""Use one independent working save for targeted material before/after/reopen.

Optional matched frame/memory measurement was explicitly requested on resume.
No JFR, travel route, world conversion, or relight. Existing native libraries,
options and capture add-on are reused. Evidence is moved out
between processes; the normally saved world stays in the same directory.
"""
import argparse
import hashlib
import json
import shutil
import subprocess
import sys
import time
import traceback
from pathlib import Path

PROJECT = Path(__file__).absolute().parents[2]
ROOT = PROJECT.parent
OUT = ROOT / 'research/city-materials-20261019'
RUN = PROJECT / 'build/runtime-client-materials'
sys.path.insert(0, str(PROJECT / 'tools/collision-streaming-20261018'))
from prepare_route import active_files, ordinary, sha
sys.path.insert(0, str(PROJECT / 'tools/city-streaming-20261018'))
from world_io import read_nbt, write_nbt


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--case', required=True)
    ap.add_argument('--jar', required=True, type=Path)
    ap.add_argument('--frames', required=True, type=Path)
    ap.add_argument('--measure', action='store_true', help='One user-requested matched 20-second frame window per camera plus process memory')
    ap.add_argument('--addon', type=Path, help='Explicit separate diagnostic QA build; comparison cases retain the same frozen default')
    ap.add_argument('--ambient-occlusion', choices=['true','false'], help='Explicit visual-only branch control; matched performance cases keep their original settings')
    args = ap.parse_args()
    assert args.case.replace('-', '').isalnum()
    OUT.mkdir(parents=True, exist_ok=True)
    evidence = OUT / args.case
    evidence.mkdir(exist_ok=False)
    receipt = {'status': 'STARTING', 'case': args.case, 'workingSave': str(RUN / 'saves/prototype-fixture')}
    def save():
        (evidence / 'driver.json').write_text(json.dumps(receipt, indent=2) + '\n')
    save()
    try:
        previous = json.loads((ROOT / 'deliverables/collision-streaming-20261018/evidence/shapes-after-prepared.json').read_text())
        addon = args.addon.absolute() if args.addon else OUT / 'dreamwalker-gallery-material-probe.jar'
        if not RUN.exists():
            RUN.mkdir()
            world = RUN / 'saves/prototype-fixture'
            world.mkdir(parents=True)
            source = ROOT / 'deliverables/collision-streaming-20261018/map/world'
            copied = []
            for path in active_files(source):
                target = world / path.relative_to(source)
                target.parent.mkdir(parents=True, exist_ok=True)
                shutil.copyfile(path, target)
                assert sha(path) == sha(target)
                copied.append({'path': path.relative_to(source).as_posix(), 'sha256': sha(target)})
            level = world / 'level.dat'
            before = sha(level)
            nbt = read_nbt(level)
            inventory = nbt.root.value['Data'].value['Player'].value['Inventory'].value
            removed = [x for x in inventory if x.value.get('Slot') and x.value['Slot'].value == 103 and x.value['id'].value == 'minecraft:carved_pumpkin']
            inventory[:] = [x for x in inventory if x not in removed]
            write_nbt(level, nbt)
            (OUT / 'working-save-prepared.json').write_text(json.dumps({'source': str(source), 'workingSave': str(world),
                'files': copied, 'onlyQaInventoryEdit': {'pumpkinRemoved': len(removed), 'levelBefore': before, 'levelAfter': sha(level)},
                'excluded': 'session.lock and original dw-*history authoring snapshots; active world bytes copied exactly',
                'status': 'PASS_ONE_WORKING_SAVE'}, indent=2) + '\n')
            (RUN / 'mods').mkdir()
            (RUN / 'resourcepacks').mkdir()
            old = Path(previous['run_directory'])
            for item in ordinary(old / 'mods').glob('*.jar'):
                if item.name.startswith(('fabric-api-', 'geckolib-', 'mclib-')):
                    shutil.copyfile(ordinary(item), RUN / 'mods' / item.name)
            shutil.copyfile(addon, RUN / 'mods' / addon.name)
        else:
            prior = json.loads((OUT / 'last-case.json').read_text())
            launch = json.loads((OUT / prior['case'] / 'launch.json').read_text())
            assert launch.get('exitCode') == 0 and launch.get('normalSaveMessages'), 'Preserve failed native run for diagnosis'
            for name in ['client-capture-input.json', 'client-capture-output.json', 'launch-console.log']:
                assert not (RUN / name).exists(), 'Previous evidence must already be archived'
        # Remove only the prior QA production copy after a successful process.
        for path in ordinary(RUN / 'mods').glob('dreamwalker-gallery-*.jar'):
            ordinary(path).unlink()
        shutil.copyfile(ordinary(addon),RUN/'mods'/addon.name)
        for path in ordinary(RUN / 'mods').glob('dreamwalker-bb-*.jar'):
            ordinary(path).unlink()
        jar = ordinary(args.jar.absolute())
        shutil.copyfile(jar, RUN / 'mods' / jar.name)
        graphics = dict(previous['graphics'])
        if args.ambient_occlusion is not None:
            graphics['ao'] = args.ambient_occlusion
        (RUN / 'options.txt').write_text('\n'.join(k + ':' + v for k, v in graphics.items()) + '\n')
        command = [x for x in previous['command'] if not x.startswith(('-javaagent:', '-XX:StartFlightRecording=', '-XX:FlightRecorderOptions='))]
        command[command.index('--gameDir') + 1] = str(RUN)
        assert '-Xmx10G' in command
        frames = json.loads(args.frames.read_text(encoding='utf-8-sig'))
        for frame in frames:
            if 'probePixels' in frame:
                assert 1 <= len(frame['probePixels']) <= 8, 'Gallery capture permits 1..8 targeted pixels per frame'
            frame['fileName'] = args.case + '-' + frame['key'] + '.png'
            frame['measureFrameTimes'] = args.measure
            frame['travelFromPrevious'] = False
            frame['command'] = 'tp @s ' + ' '.join(str(float(x)) for x in frame['camera'])
            frame.setdefault('waitTicks', 100)
        marker = {'productionJarSha256': sha(jar), 'captureCase': args.case,
                  'setupCommands': ['gamemode spectator @s', 'gamerule doDaylightCycle false', 'time set 6000', 'weather clear'],
                  'frames': frames, 'stopAfterCapture': True}
        if args.measure:
            marker['measureStreaming'] = True
        marker_path = RUN / 'client-capture-input.json'
        marker_path.write_text(json.dumps(marker, indent=2) + '\n')
        prep = {'status': 'PREPARED', 'run_directory': str(RUN), 'artifact': str(jar), 'artifact_sha256': sha(jar),
                'command': command, 'extra_mods': [{'copied_name': addon.name, 'sha256': sha(addon)}],
                'gallery_capture': {'sha256': sha(marker_path)}, 'graphics': graphics,
                'optionsSha256': sha(RUN / 'options.txt'), 'scope': 'MATCHED_FRAMES_MEMORY_AND_TARGETED_MATERIALS' if args.measure else 'TARGETED_MATERIAL_CAPTURE_ONE_REUSED_SAVE_NO_PROFILER'}
        reset_path=OUT / (args.case+'-reset.json')
        if reset_path.exists():
            prep['matchedWorldReset']=json.loads(reset_path.read_text())
        prepared_path = evidence / 'prepared.json'
        prepared_path.write_text(json.dumps(prep, indent=2) + '\n')
        with (evidence / 'launcher.log').open('w') as log:
            child = subprocess.Popen([sys.executable, 'tools/launch_prepared_client.py', '--prepared-report', str(prepared_path),
                                    '--launch-report', str(evidence / 'launch.json'), '--wait', '--timeout', '1800'],
                                   cwd=PROJECT, stdout=log, stderr=subprocess.STDOUT, creationflags=subprocess.CREATE_NO_WINDOW)
            monitor=None
            if args.measure:
                for _ in range(120):
                    try:
                        started=json.loads((evidence/'launch.json').read_text())
                        break
                    except (OSError, ValueError):
                        time.sleep(.5)
                else:
                    raise RuntimeError('Native launch receipt missing')
                receipt.update(status='MEASURING_NATIVE',pid=started['pid'])
                save()
                monitor=subprocess.Popen([r'C:\Users\vakir\.cache\codex-runtimes\codex-primary-runtime\dependencies\native\powershell\pwsh.exe',
                    '-NoProfile','-File','tools/measure_native_client_memory.ps1','-ClientProcessId',str(started['pid']),
                    '-Output',str(evidence/'memory.jsonl'),'-CaptureOutput',str(RUN/'client-capture-output.json'),'-MaximumSeconds','1800'],
                    cwd=PROJECT, stdout=log, stderr=subprocess.STDOUT, creationflags=subprocess.CREATE_NO_WINDOW)
            child.wait()
            if monitor is not None:
                monitor.wait(timeout=30)
        launch = json.loads((evidence / 'launch.json').read_text())
        assert child.returncode == 0 and launch.get('exitCode') == 0 and launch.get('captureComplete') and launch.get('normalSaveMessages'), launch
        cap = json.loads((RUN / 'client-capture-output.json').read_text())
        assert cap['status'] == 'CAPTURE_COMPLETE' and len(cap['frames']) == len(frames)
        for frame in cap['frames']:
            assert frame['requiredClientBlockEvidence']['ready']
            assert frame['terrainRenderComplete'] and frame['dayTime'] == 6000
            if 'requiredClientChunkRadius' in frame:
                assert frame['requiredClientChunkEvidence']['delivered'] == (2 * frame['requiredClientChunkRadius'] + 1) ** 2
        for name in ['client-capture-input.json', 'client-capture-output.json', 'launch-console.log']:
            shutil.move(str(RUN / name), evidence / name)
        (evidence / 'screenshots').mkdir()
        for frame in cap['frames']:
            shutil.move(str(RUN / 'screenshots' / frame['fileName']), evidence / 'screenshots' / frame['fileName'])
        receipt.update(status='PASS_NATIVE_CAPTURE_SAVE_NORMAL_EXIT', frames=len(frames), pid=launch['pid'])
        (OUT / 'last-case.json').write_text(json.dumps({'case': args.case}) + '\n')
    except Exception:
        receipt.update(status='FAIL', error=traceback.format_exc())
        raise
    finally:
        save()


if __name__ == '__main__':
    main()
