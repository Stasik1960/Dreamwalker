"""Prepare the existing three-view gallery capture on a byte-copied current city.

No conversion, migration, build, or game launch is performed. Published artifacts
are already verified by checkpoint/RESTORE-VERIFIED.json and are not rehashed.
"""
import argparse
import hashlib
import json
from pathlib import Path, PurePosixPath
import shutil
import zipfile

ROOT = Path(__file__).resolve().parents[1]
RELEASE = ROOT.parent / 'releases/Dreamwalker-BB/v12.7.4-static-city.6-render-fix.1-20261017'


def ordinary(path):
    if path.is_symlink() or getattr(path.lstat(), 'st_file_attributes', 0) & 1024:
        raise ValueError('Refusing a reparse point: ' + str(path))
    return path


def reuse_runtime(args, previous, marker_path, marker, jar, game_sha, addon, out):
    """Replace bounded runtime inputs after shutdown; preserve previous evidence."""
    import msvcrt
    run = Path(previous['run_directory']).resolve()
    if run.parent != ROOT/'build' or not run.name.startswith('runtime-client-'):
        raise ValueError('Reuse is limited to this isolated project runtime')
    world = ordinary(run/'saves/prototype-fixture')
    level_dat_source=None
    if args.restore_level_dat:
        level_dat_source=ordinary(Path(previous['derived_world_copy']['source'])/'level.dat')
        if args.reviewed_oom_stop_receipt:
            original=(out/'fixtures/baseline/world/level.dat').absolute()
            if level_dat_source.absolute()!=original:
                raise ValueError('Reviewed OOM fixture reset must use the original baseline fixture')
            for parent in level_dat_source.absolute().parents:
                ordinary(parent)
                if parent==out.absolute():break
            records=[row for row in previous['derived_world_copy']['files'] if row['path']=='level.dat']
            if (len(records)!=1 or level_dat_source.stat().st_size!=records[0]['bytes']
                or hashlib.sha256(level_dat_source.read_bytes()).hexdigest()!=records[0]['sha256']):
                raise ValueError('Original baseline level.dat must match the verified byte-copy manifest')
    elif args.restore_level_dat_from:
        level_dat_source=ordinary(args.restore_level_dat_from.absolute()).resolve()
        if (out/'history').resolve() not in level_dat_source.parents or level_dat_source.name!='saved-level.dat':
            raise ValueError('Paired restart metadata must be a saved-level.dat inside this native history')
        for parent in level_dat_source.parents:
            ordinary(parent)
            if parent==(out/'history').resolve():break
    ordinary(jar);ordinary(addon)
    with zipfile.ZipFile(addon) as source:
        if json.loads(source.read('fabric.mod.json')).get('id')!='dreamwalker_gallery_author':
            raise ValueError('Only the existing gallery add-on can be reused')
    if args.pilot and (len(args.pilot.split(','))!=4 or any(not item.lstrip('-').isdigit() for item in args.pilot.split(','))):
        raise ValueError('Pilot must be four integer X/Z bounds')
    if '-Xmx10G' not in previous['command']:
        raise ValueError('Retain the matched 10 GiB JVM profile')
    values=dict(line.split(':',1) for line in ordinary(run/'options.txt').read_text(encoding='utf8').splitlines() if ':' in line)
    for key,expected in {'renderDistance':'12','simulationDistance':'12','maxFps':'260','enableVsync':'false','graphicsMode':'1'}.items():
        if values.get(key)!=expected:
            raise ValueError('Matched graphics changed: '+key)
    # Minecraft holds this byte while the save is active. Refuse to reuse a
    # running world, rather than inspecting or stopping unrelated JVMs.
    with ordinary(world/'session.lock').open('r+b') as lock:
        lock.seek(0)
        msvcrt.locking(lock.fileno(), msvcrt.LK_NBLCK, 1)
        try:
            prior_marker = json.loads(ordinary(run/'client-capture-input.json').read_text(encoding='utf8'))
            prior_case = prior_marker.get('captureCase','unknown')
            if not prior_case.replace('-','').replace('_','').isalnum():
                raise ValueError('Unsafe prior capture case')
            launch=json.loads(ordinary(out/(prior_case+'-launch.json')).read_text(encoding='utf8'))
            capture = json.loads(ordinary(run/'client-capture-output.json').read_text(encoding='utf8'))
            reviewed_crash=None
            reviewed_oom=None
            if args.reviewed_oom_stop_receipt:
                selected=ordinary(args.reviewed_oom_stop_receipt.absolute()).resolve()
                if (out/'history').resolve() not in selected.parents:
                    raise ValueError('Reviewed OOM stop receipt must belong to this native history')
                stopped=json.loads(selected.read_text(encoding='utf8'))
                allowed_setup=['gamemode spectator @s','gamerule doDaylightCycle false','time set 6000','weather clear']
                if (stopped.get('status')!='OOM_FAILED_NO_NORMAL_SAVE'
                    or Path(stopped.get('runtime','')).resolve()!=run or stopped.get('pid')!=launch.get('pid')
                    or Path(launch.get('prepared_report','')).resolve()!=args.reuse_prepared.resolve()
                    or launch.get('artifact_sha256')!=previous.get('artifact_sha256')
                    or prior_marker.get('productionJarSha256')!=previous.get('artifact_sha256')
                    or launch.get('exitCode') in (None,0) or not (args.restore_level_dat_from or args.restore_level_dat)
                    or set(prior_marker)-{'productionJarSha256','captureCase','setupCommands','frames','stopAfterCapture'}
                    or prior_marker.get('setupCommands')!=allowed_setup
                    or any(frame.get('measureFrameTimes',True) for frame in prior_marker.get('frames',[]))
                    or any('command' in frame and not frame['command'].startswith('tp @s ') for frame in prior_marker.get('frames',[]))
                    or capture.get('temporaryOccluderCheck')
                    or 'OutOfMemoryError: Java heap space' not in ordinary(run/'launch-console.log').read_text(encoding='utf8',errors='replace')):
                    raise ValueError('Only the reviewed stopped nonauthoring route OOM may be recovered')
                import ctypes
                from ctypes import wintypes
                kernel=ctypes.WinDLL('kernel32',use_last_error=True)
                kernel.OpenProcess.argtypes=[wintypes.DWORD,wintypes.BOOL,wintypes.DWORD]
                kernel.OpenProcess.restype=wintypes.HANDLE
                kernel.GetExitCodeProcess.argtypes=[wintypes.HANDLE,ctypes.POINTER(wintypes.DWORD)]
                kernel.CloseHandle.argtypes=[wintypes.HANDLE]
                handle=kernel.OpenProcess(0x1000,False,int(stopped['pid']))
                if handle:
                    code=wintypes.DWORD()
                    try:
                        if not kernel.GetExitCodeProcess(handle,ctypes.byref(code)) or code.value==259:
                            raise ValueError('The reviewed QA PID must have stopped before reuse')
                    finally:kernel.CloseHandle(handle)
                elif ctypes.get_last_error() not in (87,1168):
                    raise ValueError('Unable to verify that the reviewed QA PID stopped')
                reviewed_oom=selected
            if args.reviewed_render_crash_report:
                selected=ordinary(args.reviewed_render_crash_report.absolute()).resolve()
                if selected.parent!=(run/'crash-reports').resolve():
                    raise ValueError('Reviewed crash must belong to this exact isolated runtime')
                with selected.open(encoding='utf8',errors='replace') as source:
                    crash_header=source.read(32768)
                if (launch.get('exitCode') in (None,0) or capture.get('frames')
                    or capture.get('temporaryOccluderCheck')
                    or prior_marker.get('nativeRoute') not in (None,'NONE')
                    or not args.restore_level_dat
                    or not all(text in crash_header for text in ('Batching chunks','StackOverflowError','StaticArchitectureRenderView.getBlockEntityRenderData'))):
                    raise ValueError('Only the reviewed pre-camera render-view recursion crash may be reused')
                reviewed_crash=selected
            if (launch.get('exitCode')!=0 or not launch.get('normalSaveMessages')) and not (reviewed_crash or reviewed_oom):
                raise ValueError('The prior client must have exited and saved normally before reuse')
            if not capture.get('captureComplete'):
                check=capture.get('temporaryOccluderCheck',{})
                reviewed_unplaced=(args.allow_unplaced_occluder_rejection
                    and len(capture.get('frames',[]))==len(prior_marker['frames'])
                    and not check.get('temporaryPlaced',False) and not check.get('snapshots')
                    and 'Occluder cell is not exact empty AIR' in check.get('failure',''))
                if not reviewed_unplaced and not (reviewed_crash or reviewed_oom):
                    raise ValueError('Retain/review incomplete native capture before reuse')
            extras=previous.get('extra_mods',[])
            if len(extras)!=1:
                raise ValueError('Only the existing single capture add-on may be replaced')
            ordinary(run/'mods'/extras[0]['copied_name'])
            ordinary(run/'mods'/Path(previous['artifact']).name)
            screenshot_names=[frame['fileName'] for frame in capture.get('frames',[])]
            screenshot_names.extend(row['fileName'] for row in capture.get('temporaryOccluderCheck',{}).get('snapshots',[]))
            for name in screenshot_names:
                if Path(name).name!=name or not name.endswith('.png'):
                    raise ValueError('Unsafe prior screenshot name')
                ordinary(run/'screenshots'/name)
            archive_name=args.prior_evidence_name or prior_case
            if not archive_name.replace('-','').replace('_','').isalnum():
                raise ValueError('Unsafe evidence archive name')
            archive = out/'history'/archive_name
            if archive.exists():
                raise ValueError('Prior native evidence archive already exists')
            archive.mkdir(parents=True)
            for name in ('client-capture-input.json','client-capture-output.json','launch-console.log'):
                ordinary(run/name).replace(archive/name)
            if reviewed_crash:
                reviewed_crash.replace(archive/reviewed_crash.name)
                for name in (prior_case+'-launch.json',prior_case+'-memory.jsonl'):
                    shutil.copy2(ordinary(out/name),archive/name)
                shutil.copy2(ordinary(args.reuse_prepared.resolve()),archive/(prior_case+'-prepared.json'))
            if reviewed_oom:
                shutil.copy2(ordinary(out/(prior_case+'-launch.json')),archive/(prior_case+'-launch.json'))
                shutil.copy2(ordinary(args.reuse_prepared.resolve()),archive/(prior_case+'-prepared.json'))
            shutil.copy2(ordinary(run/'options.txt'),archive/'options.txt')
            shutil.copy2(ordinary(world/'level.dat'),archive/'saved-level.dat')
            for name in screenshot_names:
                if Path(name).name!=name or not name.endswith('.png'):
                    raise ValueError('Unsafe prior screenshot name')
                source=ordinary(run/'screenshots'/name)
                (archive/'screenshots').mkdir(exist_ok=True)
                source.replace(archive/'screenshots'/name)
            prior_game=ordinary(run/'mods'/Path(previous['artifact']).name)
            # Preserve the actual previous JAR, not just a potentially rebuilt
            # source artifact at the same build/libs path.
            prior_game.replace(archive/prior_game.name)
            installed_addon=ordinary(run/'mods'/extras[0]['copied_name'])
            installed_addon.replace(archive/installed_addon.name)
            shutil.copyfile(ordinary(jar),run/'mods'/jar.name)
            shutil.copyfile(ordinary(addon),run/'mods'/addon.name)
            shutil.copyfile(marker_path,run/'client-capture-input.json')
            if level_dat_source:
                shutil.copy2(ordinary(world/'level.dat'),archive/'level.dat-before-reset')
                shutil.copyfile(level_dat_source,world/'level.dat')
            receipt=dict(previous)
            receipt.update({'artifact':str(jar),'artifact_sha256':game_sha,'status':'PREPARED',
                'gallery_capture':{'source':str(marker_path),'sha256':hashlib.sha256(marker_path.read_bytes()).hexdigest(),'frames':len(marker['frames'])},
                'extra_mods':[{'path':str(addon),'sha256':hashlib.sha256(addon.read_bytes()).hexdigest(),'copied_name':addon.name}],
                'reuse':{'priorPreparedReport':str(args.reuse_prepared.resolve()),'priorEvidence':str(archive),
                         'worldCopiedAgain':False,'levelDatResetFromOriginalFixture':args.restore_level_dat,
                         'levelDatRestoredFrom':str(level_dat_source) if level_dat_source else None,
                         'levelDatRestoredFromSha256':hashlib.sha256(level_dat_source.read_bytes()).hexdigest() if level_dat_source else None,
                         'levelDatAtStartSha256':hashlib.sha256((world/'level.dat').read_bytes()).hexdigest(),
                         'reviewedOOMStopReceipt':str(reviewed_oom) if reviewed_oom else None,
                         'reviewedRenderCrash':str(archive/reviewed_crash.name) if reviewed_crash else None}})
            receipt['command']=[item for item in receipt['command'] if not item.startswith('-Ddreamwalker.terrain.pilot=')]
            if args.pilot:
                receipt['command'].insert(2,'-Ddreamwalker.terrain.pilot='+args.pilot)
            prepared=out/(args.case+'-prepared.json')
            prepared.write_text(json.dumps(receipt,indent=2)+'\n',encoding='utf8')
            (run/'launch-command.json').write_text(json.dumps(receipt['command'],indent=2)+'\n',encoding='utf8')
            print(json.dumps({'status':'PREPARED_REUSED_RUNTIME_NO_LAUNCH','preparedReport':str(prepared),'runDirectory':str(run),'priorEvidence':str(archive)}))
        finally:
            lock.seek(0)
            msvcrt.locking(lock.fileno(),msvcrt.LK_UNLCK,1)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--case', required=True)
    parser.add_argument('--jar', type=Path, help='Candidate JAR; default is verified published baseline')
    parser.add_argument('--extract-world', action='store_true')
    parser.add_argument('--resource-pack', type=Path)
    parser.add_argument('--java-home', type=Path)
    parser.add_argument('--minecraft-home', type=Path)
    parser.add_argument('--gradle-home', type=Path)
    parser.add_argument('--reuse-prepared', type=Path, help='Reuse the stopped independently copied runtime; archive previous evidence')
    parser.add_argument('--gallery-addon', type=Path, help='Current separately built gallery add-on')
    reset=parser.add_mutually_exclusive_group()
    reset.add_argument('--restore-level-dat', action='store_true', help='Reset only level.dat from the original baseline fixture before a matched cold launch')
    reset.add_argument('--restore-level-dat-from', type=Path, help='Restore saved-level.dat from this native history for one paired restart comparison')
    parser.add_argument('--pilot', help='Existing terrain pilot property: minX,minZ,maxX,maxZ')
    parser.add_argument('--temporary-occluder-check', action='store_true', help='Opt in to the existing addressed final boundary/occlusion check')
    parser.add_argument('--travel-main-districts', action='store_true', help='Append native spectator movement through the main city districts after matched FPS views')
    parser.add_argument('--route-only', action='store_true', help='Only an unmeasured district starting frame and the explicit native district travel route')
    parser.add_argument('--view', choices=('north_facade','east_facade','district'), help='One addressed view, for the separate save/reopen check')
    parser.add_argument('--skip-fps', action='store_true', help='Capture the addressed reopen view without another performance window')
    parser.add_argument('--resident-warmup-frames', type=int, default=0, help='0..5 unmeasured north frames at 200 ticks each, then one measured north memory window')
    parser.add_argument('--allow-unplaced-occluder-rejection', action='store_true', help='Reviewed narrow retry: all camera frames saved, occluder rejected the non-AIR guard before any placement, normal native exit')
    parser.add_argument('--reviewed-render-crash-report', type=Path, help='Reviewed pre-camera Batching chunks/StackOverflowError recursion report from this runtime only; requires restoring original level.dat')
    parser.add_argument('--reviewed-oom-stop-receipt', type=Path, help='Reviewed stopped route-only OOM receipt; requires restoring known saved history metadata or verified original baseline level.dat')
    parser.add_argument('--prior-evidence-name', help='Explicit archive name for the reviewed failed attempt')
    args = parser.parse_args()
    if args.reviewed_oom_stop_receipt and (not args.reuse_prepared or not (args.restore_level_dat_from or args.restore_level_dat)):
        raise ValueError('Reviewed OOM recovery requires --reuse-prepared and one explicit level.dat restore source')
    if args.route_only and (not args.travel_main_districts or args.temporary_occluder_check or args.view not in (None,'district')):
        raise ValueError('Route-only needs --travel-main-districts, district start and no occluder')
    if not 0 <= args.resident_warmup_frames <= 5:
        raise ValueError('Resident warmup is bounded to 0..5 frames')
    if args.resident_warmup_frames and (args.view!='north_facade' or args.skip_fps):
        raise ValueError('Resident warmup requires one measured --view north_facade')
    if not args.reuse_prepared and any(value is None for value in (args.resource_pack,args.java_home,args.minecraft_home,args.gradle_home)):
        raise ValueError('New runtime preparation needs explicit local pack, Java, Minecraft and Gradle paths')
    if not args.case.replace('-', '').replace('_', '').isalnum():
        raise ValueError('Bounded simple capture case required')
    verified = json.loads((ROOT.parent / 'checkpoint/RESTORE-VERIFIED.json').read_text(encoding='utf8'))
    if verified.get('status') != 'VERIFIED':
        raise ValueError('Published checkpoint must already have a verification receipt')
    manifest = json.loads((RELEASE / 'MANIFEST.json').read_text(encoding='utf8'))
    summary = json.loads((RELEASE / 'evidence/EVIDENCE-SUMMARY.json').read_text(encoding='utf8'))
    game_record = next(row for row in manifest['artifacts'] if row['role'] == 'compatible production game JAR')
    jar = args.jar.resolve() if args.jar else RELEASE / game_record['file']
    game_sha = hashlib.sha256(jar.read_bytes()).hexdigest() if args.jar else manifest['gameSha256']
    out = ROOT / 'build/native-city-mesh'
    out.mkdir(parents=True, exist_ok=True)
    marker_path = out / (args.case + '-capture-input.json')
    command_path = out / (args.case + '-prepare-command.json')
    if marker_path.exists() or command_path.exists():
        raise ValueError('Preserve existing capture preparation')
    previous=json.loads(args.reuse_prepared.read_text(encoding='utf8')) if args.reuse_prepared else None
    if previous and args.extract_world:
        raise ValueError('A reused world must not be copied or extracted again')
    fixture_parent = out / 'fixtures' / args.case
    fixture = Path(previous['derived_world_copy']['source']) if previous else fixture_parent / 'world'
    if args.extract_world:
        if fixture_parent.exists():
            raise ValueError('Fresh independent fixture destination required')
        fixture_parent.mkdir(parents=True)
        archive_path = RELEASE / manifest['worldArchive']['file']
        with zipfile.ZipFile(archive_path) as archive:
            for entry in archive.infolist():
                relative = PurePosixPath(entry.filename)
                if relative.is_absolute() or '..' in relative.parts or not relative.parts or relative.parts[0] != 'world' or '\\' in entry.filename:
                    raise ValueError('Unsafe world archive entry: ' + entry.filename)
                target = fixture_parent.joinpath(*relative.parts)
                if entry.is_dir():
                    target.mkdir(parents=True, exist_ok=True)
                    continue
                target.parent.mkdir(parents=True, exist_ok=True)
                with archive.open(entry) as source, target.open('xb') as output:
                    shutil.copyfileobj(source, output, 1024 * 1024)
    if not (fixture / 'level.dat').is_file():
        raise ValueError('Extract the already converted checkpoint copy once with --extract-world')
    frames = []
    for row in summary['comparison']:
        key = row['view']
        if args.route_only and key!='district':
            continue
        if args.view and key!=args.view:
            continue
        camera = row['camera']
        frame = {'key': key, 'fileName': args.case + '-' + key + '.png',
                 'camera': camera, 'command': 'tp @s ' + ' '.join(f'{value:.6f}' for value in camera),
                 'waitTicks': 100, 'measureFrameTimes': not args.skip_fps and not args.route_only}
        if key == 'east_facade':
            frame['probePixel'] = [180, 307]
        if args.route_only:
            frame['requiredClientBlocks']=[{'pos':[299,133,-673],'expectedStateId':'bloodborne_dw:static_architecture_0'}]
        if args.resident_warmup_frames:
            frame['waitTicks']=200
            for index in range(1,args.resident_warmup_frames+1):
                warmup=dict(frame)
                warmup.update({'key':f'{key}_warmup{index}',
                               'fileName':f'{args.case}-{key}-warmup{index}.png',
                               'measureFrameTimes':False})
                frames.append(warmup)
        frames.append(frame)
    marker = {'productionJarSha256': game_sha, 'captureCase': args.case,
              'setupCommands': ['gamemode spectator @s', 'gamerule doDaylightCycle false', 'time set 6000', 'weather clear'],
              'frames': frames, 'stopAfterCapture': True}
    if args.travel_main_districts:
        route=[[288,132,-678,0,0],[40,167,-81,167.969,32.829],[650,110,-150,43.23,35],
               [980,100,95,44.36,35],[318,167,-622,138.58,37.68]]
        for index,camera in enumerate(route,1):
            required={1:[([299,133,-673],'static_architecture_0')],
                      2:[([33,148,-112],'source_raw_copper_block')],
                      3:[([603,70,-100],'static_architecture_9')],
                      4:[([940,28,129],'static_architecture_14'),([940,28,149],'static_architecture_14')],
                      5:[([299,133,-673],'static_architecture_0')]}[index]
            frames.append({'key':f'travel_{index}','fileName':f'{args.case}-travel-{index}.png',
                           'camera':camera,'waitTicks':100,'measureFrameTimes':False,'travelFromPrevious':True,
                           'requiredClientBlocks':[{'pos':pos,'expectedStateId':'bloodborne_dw:'+name} for pos,name in required]})
    if args.temporary_occluder_check:
        camera=[302.5,132,-671.7,110.4,1.84]
        frames.append({'key':'boundary_detail','fileName':args.case+'-boundary_detail.png','camera':camera,
                       'command':'tp @s '+' '.join(f'{value:.6f}' for value in camera),
                       'waitTicks':100,'measureFrameTimes':False})
        marker['nativeRoute']='NONE'
        marker['temporaryOccluderCheck']={'position':[300,133,-672],'root':[299,133,-673],
            'expectedRootBlock':'bloodborne_dw:static_architecture_0','expectedRootPart':105,
            'filePrefix':'boundary-occluder'}
    marker_path.write_text(json.dumps(marker, indent=2) + '\n', encoding='utf8')
    addon = args.gallery_addon.resolve() if args.gallery_addon else ROOT / 'build/libs' / ('dreamwalker-gallery-author-' + next(line.split('=',1)[1].strip() for line in (ROOT/'gradle.properties').read_text().splitlines() if line.startswith('mod_version=')) + '.jar')
    if previous:
        reuse_runtime(args,previous,marker_path,marker,jar,game_sha,addon,out)
        return
    # The preparation command is data for a normal subprocess invocation. No
    # shell interpolation, original profile/options, or old-machine binding.
    command = ['tools/run_final_client.py', '--jar', str(jar), '--fixture', str(fixture),
               '--run-name', 'city-mesh-' + args.case, '--report', str(out / (args.case + '-prepared.json')),
               '--cached-minimal', '--profile', 'minimal', '--loader', '0.19.5',
               '--java-home', str(args.java_home.resolve()), '--minecraft-home', str(args.minecraft_home.resolve()),
               '--gradle-home', str(args.gradle_home.resolve()), '--resource-pack', str(args.resource_pack.resolve()),
               '--extra-mod', str(addon), '--gallery-capture-input', str(marker_path), '--quick-play',
               '--heap-gb', '10', '--render-distance', '12', '--simulation-distance', '12',
               '--width', '1280', '--height', '720', '--fov', '70', '--graphics', 'fancy', '--max-fps', '260',
               '--unattended-no-focus-pause']
    command_path.write_text(json.dumps(command, indent=2) + '\n', encoding='utf8')
    print(json.dumps({'status': 'PREPARED_MATCHED_THREE_VIEW_INPUT_NO_LAUNCH', 'fixture': str(fixture),
                      'captureInput': str(marker_path), 'prepareCommand': str(command_path), 'addon': str(addon)}))


if __name__ == '__main__':
    main()
