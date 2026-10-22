"""Prepare one matched native route from an explicitly selected immutable copy."""
import argparse, hashlib, json, os, shutil, sys
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

PROJECT = Path(__file__).absolute().parents[2]
EVIDENCE = PROJECT.parent / 'deliverables/collision-streaming-20261018/evidence'
sys.path.insert(0, str(PROJECT / 'tools/city-streaming-20261018'))
from world_io import read_nbt, write_nbt


def sha(path):
    h = hashlib.sha256()
    with path.open('rb') as f:
        for block in iter(lambda: f.read(1024 * 1024), b''):
            h.update(block)
    return h.hexdigest()


def ordinary(path):
    if path.is_symlink() or getattr(path.lstat(), 'st_file_attributes', 0) & 1024:
        raise ValueError('Reparse point refused: ' + str(path))
    return path


def active_files(folder):
    """Check each directory before descending; never traverse a reparse point."""
    for child in sorted(ordinary(folder).iterdir()):
        ordinary(child)
        if child.name == 'session.lock' or (child.is_dir() and child.name.startswith('dw-') and 'history' in child.name):
            continue
        if child.is_dir():
            yield from active_files(child)
        elif child.is_file():
            yield child


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--case', required=True)
    ap.add_argument('--jar', type=Path, required=True)
    ap.add_argument('--world-source', type=Path, required=True)
    ap.add_argument('--gallery-addon', type=Path, required=True)
    ap.add_argument('--brick-only', action='store_true', help='One addressed save/reopen view, no repeated district travel')
    args = ap.parse_args()
    assert args.case.replace('-', '').isalnum()
    EVIDENCE.mkdir(parents=True, exist_ok=True)
    report_path = EVIDENCE / (args.case + '-prepared.json')
    assert not report_path.exists(), 'Preserve earlier preparation'
    previous = json.loads((PROJECT / 'build/streaming-validation/after4-prepared.json').read_text())
    run = PROJECT / ('build/runtime-client-collision-' + args.case)
    run.mkdir(exist_ok=False)
    source = ordinary(args.world_source.absolute())
    jar, addon = ordinary(args.jar.absolute()), ordinary(args.gallery_addon.absolute())
    world = run / 'saves/prototype-fixture'
    world.mkdir(parents=True)

    def copy(path):
        relative = path.relative_to(source)
        target = world / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(path, target)
        return {'path': relative.as_posix(), 'bytes': target.stat().st_size, 'sha256': sha(target)}

    with ThreadPoolExecutor(max_workers=8) as pool:
        files = list(pool.map(copy, active_files(source)))
    # Apply the existing camera-only pumpkin removal identically in both runs.
    level = world / 'level.dat'
    before_level = sha(level)
    nbt = read_nbt(level)
    inventory = nbt.root.value['Data'].value['Player'].value['Inventory'].value
    removed = [x for x in inventory if x.value.get('Slot') and x.value['Slot'].value == 103 and x.value['id'].value == 'minecraft:carved_pumpkin']
    inventory[:] = [x for x in inventory if x not in removed]
    write_nbt(level, nbt)
    (run / 'mods').mkdir()
    (run / 'resourcepacks').mkdir()
    old = Path(previous['run_directory'])
    for item in ordinary(old / 'mods').glob('*.jar'):
        if item.name.startswith(('fabric-api-', 'geckolib-', 'mclib-')):
            shutil.copyfile(ordinary(item), run / 'mods' / item.name)
    for item in [jar, addon]:
        shutil.copyfile(item, run / 'mods' / item.name)
    graphics = dict(previous['graphics'])
    (run / 'options.txt').write_text('\n'.join(k + ':' + v for k, v in graphics.items()) + '\n')
    command = [x for x in previous['command'] if not x.startswith(('-javaagent:', '-XX:StartFlightRecording='))]
    command[command.index('--gameDir') + 1] = str(run)
    command.insert(1, '-XX:StartFlightRecording=settings=profile,filename=' + str(run / 'streaming.jfr') + ',dumponexit=true,maxsize=128m')
    assert '-Xmx10G' in command
    coords = [
        ('facade', [272.848, 63.6296, -903.327, 120.3, -3.5], [253, 66, -915], 'bloodborne_dw:source_bricks_bricks_r_0'),
        ('tower', [650, 110, -150, 43.23, 35], [603, 70, -100], 'bloodborne_dw:static_architecture_9'),
        ('brick', [980, 100, 95, 44.36, 35], [940, 28, 129], 'bloodborne_dw:static_architecture_14'),
    ]
    if args.brick_only:
        coords = coords[-1:]
    frames = []
    for i, (key, camera, pos, state) in enumerate(coords):
        frame = {'key': key, 'fileName': args.case + '-' + key + '.png', 'camera': camera, 'waitTicks': 80,
                 'measureFrameTimes': False, 'travelFromPrevious': i > 0, 'requiredClientChunkRadius': 8,
                 'requiredClientBlocks': [{'pos': pos, 'expectedStateId': state}]}
        if i == 0:
            frame['command'] = 'tp @s ' + ' '.join(str(float(x)) for x in camera)
        frames.append(frame)
    marker = {'productionJarSha256': sha(jar), 'captureCase': args.case, 'measureStreaming': True,
              'setupCommands': ['gamemode spectator @s', 'gamerule doDaylightCycle false', 'time set 6000', 'weather clear'],
              'frames': frames, 'stopAfterCapture': True}
    marker_path = run / 'client-capture-input.json'
    marker_path.write_text(json.dumps(marker, indent=2) + '\n')
    report = {'status': 'PREPARED', 'run_directory': str(run), 'artifact': str(jar), 'artifact_sha256': sha(jar),
              'command': command, 'extra_mods': [{'copied_name': addon.name, 'sha256': sha(addon)}],
              'gallery_capture': {'sha256': sha(marker_path)}, 'derived_world_copy': {'source': str(source), 'files': files},
              'fixtureManifestSha256': hashlib.sha256(json.dumps(files, sort_keys=True).encode()).hexdigest(),
              'qaHeadRemoval': {'count': len(removed), 'before': before_level, 'after': sha(level)}, 'graphics': graphics,
              'optionsSha256': sha(run / 'options.txt'), 'javaSha256': sha(Path(command[0])),
              'scope': 'Independent explicitly selected save copy. Authoring histories omitted; all active save files copied. Same route/options/QA/JFR profile for both performance JARs.',
              'mode': 'ONE_BRICK_VIEW_SAVE_REOPEN' if args.brick_only else 'MATCHED_THREE_DISTRICT_ROUTE'}
    report_path.write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps({'status': 'PREPARED', 'preparedReport': str(report_path), 'runDirectory': str(run), 'activeFiles': len(files)}))


if __name__ == '__main__':
    main()
