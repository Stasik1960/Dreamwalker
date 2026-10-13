"""Prepare an isolated Minecraft1.20.1/Fabric0.16.10 client; --launch opens the game.

Production and current cached dependencies are installed, with an explicitly requested
project-build gallery QA add-on/input optional. Either a project-build
world is byte-copied or this real client joins a localhost server. Libraries/assets
are checksum-verified. Separate --username and --run-name give independent clients. Run through
CodexControl/Guard.ps1 -Mode Operation; no user accounts/options/worlds are changed.
"""
from __future__ import annotations
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import uuid
import zipfile
from ensure_client_assets import ensure_assets
from run_v11_server import DEPENDENCIES, ROOT, USER, copy_verified, copy_extra_mods, extra_mod_inputs, fetch, files_without_links, ordinary, require, sha, write_json

WORLD_NAME = 'prototype-fixture'


def offline_identity(username):
    require(re.fullmatch(r'[A-Za-z0-9_]{3,16}', username), 'Offline QA name must use3..16 ASCII letters/digits/underscores')
    return str(uuid.UUID(bytes=hashlib.md5(('OfflinePlayer:' + username).encode()).digest(), version=3))


def connection_arguments(args):
    if args.server:
        require(args.server in ('127.0.0.1', 'localhost') and args.port is not None and 1 <= args.port <= 65535,
                'Multiplayer QA requires an explicit localhost host and valid port')
        return ['--quickPlayMultiplayer', args.server + ':' + str(args.port)]
    require(args.fixture is not None and args.port is None, 'Singleplayer requires a fixture and no multiplayer port')
    return ['--quickPlaySingleplayer', WORLD_NAME] if args.quick_play else []


def applies(rules):
    allowed = not rules
    for rule in rules:
        operating_system = rule.get('os', {})
        if operating_system.get('name', 'windows') != 'windows' or operating_system.get('arch', 'amd64') not in ('amd64', 'x86_64') or rule.get('features'):
            continue
        allowed = rule['action'] == 'allow'
    return allowed


def checksum(path, expected_sha1=None, expected_sha256=None):
    return path.is_file() and (not expected_sha1 or hashlib.sha1(path.read_bytes()).hexdigest() == expected_sha1) and (not expected_sha256 or sha(path) == expected_sha256)


def prepared_files(run):
    return [{'path': p.relative_to(run).as_posix(), 'bytes': p.stat().st_size, 'sha256': sha(p)}
            for p in sorted(files_without_links(run)) if p.name not in ('client-profile.json', 'launch-console.log')]


def qa_inputs(args, artifact_sha):
    records = {'qaAddon': None, 'captureInput': None}
    if args.qa_addon:
        ordinary(args.qa_addon);addon = args.qa_addon.resolve()
        require(addon.parent == (ROOT / 'build/libs').resolve() and addon.suffix == '.jar',
                'Only a QA JAR directly from project build/libs is allowed')
        with zipfile.ZipFile(addon) as archive:
            metadata = json.loads(archive.read('fabric.mod.json'))
        require(metadata.get('id') == 'dreamwalker_gallery_author', 'Only the separate gallery QA add-on is allowed')
        records['qaAddon'] = {'source': str(addon), 'sha256': sha(addon), 'modId': metadata['id']}
    if args.capture_input:
        require(args.qa_addon is not None, '--capture-input requires --qa-addon')
        ordinary(args.capture_input);marker = args.capture_input.resolve()
        require(marker.is_relative_to((ROOT / 'build').resolve()) and marker.stat().st_size <= 1048576,
                'Capture input must be a small explicit project-build JSON file')
        data = json.loads(marker.read_text(encoding='utf-8-sig'))
        require(data.get('productionJarSha256') == artifact_sha and isinstance(data.get('frames'), list)
                and 1 <= len(data['frames']) <= 1000, 'Capture input must bind this production JAR and explicit camera frames')
        records['captureInput'] = {'source': str(marker), 'sha256': sha(marker), 'frames': len(data['frames'])}
    return records


def options_text(args):
    options = 'version:3465\nautoJump:false\nfullscreen:false\nrenderDistance:' + str(args.render_distance) + '\nsimulationDistance:5\ngamma:1.0\ntutorialStep:none\npauseOnLostFocus:false\n'
    options += 'guiScale:' + str(args.gui_scale) + '\nmaxFps:' + str(args.max_fps) + '\n'
    if args.qa_keybindings:
        for action, key in {'use': 'f8', 'attack': 'f9', 'forward': 'up', 'back': 'down', 'left': 'left', 'right': 'right', 'jump': 'f10'}.items():
            options += 'key_key.' + action + ':key.keyboard.' + key + '\n'
    return options


def prepare(args, run, artifact_sha, qa, extras):
    mods = run / 'mods';mods.mkdir(parents=True)
    copy_verified(args.jar.resolve(), mods / args.jar.name)
    if qa['qaAddon']:
        addon = Path(qa['qaAddon']['source']);copy_verified(addon, mods / addon.name)
        require(sha(mods / addon.name) == qa['qaAddon']['sha256'], 'QA JAR changed during preparation')
    if qa['captureInput']:
        copy_verified(Path(qa['captureInput']['source']), run / 'client-capture-input.json')
        require(sha(run / 'client-capture-input.json') == qa['captureInput']['sha256'], 'Capture input changed during preparation')
    modset = []
    for group, name, version in DEPENDENCIES:
        source = next((p for p in sorted((USER / '.gradle/caches/modules-2/files-2.1' / group / name / version).glob('*/*.jar'))
                       if not p.name.endswith(('-sources.jar', '-javadoc.jar'))), None)
        require(source is not None, 'Missing exact cached dependency: ' + name + ':' + version)
        copy_verified(source, mods / source.name)
        modset.append({'coordinate': group + ':' + name + ':' + version, 'source': str(source), 'sha256': sha(source)})
    extra_mods = copy_extra_mods(extras, mods)
    java = args.java_home.resolve() / 'bin/java.exe'
    java_version = subprocess.run([str(java), '-version'], capture_output=True, text=True, check=True).stderr.strip()
    require(re.search(r'version "17\.', java_version), 'Client requires actual Java17')
    loom = USER / '.gradle/caches/fabric-loom/1.20.1'
    minecraft = json.loads((loom / 'minecraft-info.json').read_text(encoding='utf8'))
    require(minecraft.get('id') == '1.20.1', 'Minecraft metadata must be pinned to1.20.1')
    client = run / 'minecraft-client.jar';copy_verified(loom / 'minecraft-client.jar', client)
    require(checksum(client, minecraft['downloads']['client']['sha1']), 'Mojang client JAR checksum mismatch')
    loader_url = 'https://meta.fabricmc.net/v2/versions/loader/1.20.1/0.16.10/profile/json'
    loader_bytes = fetch(loader_url);loader = json.loads(loader_bytes)
    (run / 'fabric-loader-profile.json').write_bytes(loader_bytes)
    libraries = [];classpath = [];natives = run / 'natives';natives.mkdir()
    for library in minecraft['libraries'] + loader['libraries']:
        coordinate = library['name']
        if not applies(library.get('rules', [])) or coordinate.endswith(('natives-windows-arm64', 'natives-windows-x86')):
            continue
        artifact = library.get('downloads', {}).get('artifact')
        group, name, version = coordinate.split(':')[:3]
        if artifact:
            relative = Path(artifact['path']);url = artifact['url'];expected_sha1 = artifact['sha1']
        else:
            relative = Path(group.replace('.', '/')) / name / version / (name + '-' + version + '.jar')
            url = library.get('url', 'https://maven.fabricmc.net/').rstrip('/') + '/' + relative.as_posix();expected_sha1 = None
        destination = run / 'libraries' / relative;destination.parent.mkdir(parents=True, exist_ok=True)
        expected_sha256 = library.get('sha256')
        candidates = [USER / 'AppData/Roaming/.minecraft/libraries' / relative]
        candidates += sorted((USER / '.gradle/caches/modules-2/files-2.1' / group / name / version).glob('*/*.jar'))
        source = next((p for p in candidates if not p.name.endswith(('-sources.jar', '-javadoc.jar')) and checksum(p, expected_sha1, expected_sha256)), None)
        if source:
            copy_verified(source, destination);provenance = str(source)
        else:
            destination.write_bytes(fetch(url));provenance = url
        require(checksum(destination, expected_sha1, expected_sha256), 'Minecraft/loader library checksum mismatch: ' + coordinate)
        classpath.append(str(destination));libraries.append({'coordinate': coordinate, 'source': provenance, 'sha256': sha(destination)})
        if coordinate.endswith('natives-windows'):
            with zipfile.ZipFile(destination) as native_jar:
                for entry in native_jar.namelist():
                    if entry.lower().endswith('.dll'):
                        (natives / Path(entry).name).write_bytes(native_jar.read(entry))
    assets, asset_index, asset_provenance = ensure_assets(minecraft, ROOT, USER)
    fixture = None;fixture_record = None
    if args.fixture:
        fixture = args.fixture.resolve();ordinary(args.fixture)
        require(fixture.is_relative_to((ROOT / 'build').resolve()) and (fixture / 'level.dat').is_file(),
                'Client fixture must be a saved derived world under this project build folder')
        copied = []
        for source in sorted(files_without_links(fixture)):
            relative = source.relative_to(fixture);copy_verified(source, run / 'saves' / WORLD_NAME / relative)
            copied.append({'path': relative.as_posix(), 'bytes': source.stat().st_size, 'sha256': sha(source)})
        fixture_record = {'source': str(fixture), 'files': copied, 'omitted_files': ['session.lock'], 'copy_byte_verification': 'PASS'}
    (run / 'options.txt').write_text(options_text(args), encoding='utf8')
    username = args.username;identity = offline_identity(username)
    command = [str(java), '-Xmx' + str(args.heap_gb) + 'G', '-Djava.library.path=' + str(natives), '-cp', os.pathsep.join(classpath + [str(client)]),
               'net.fabricmc.loader.impl.launch.knot.KnotClient', '--username', username, '--uuid', identity, '--accessToken', '0', '--userType', 'legacy',
               '--version', '1.20.1', '--gameDir', str(run), '--assetsDir', str(assets), '--assetIndex', asset_index.stem,
               '--width', str(args.width), '--height', str(args.height)]
    command += connection_arguments(args)
    result = {'schema': 'dreamwalker-v11-client-v1', 'status': 'PREPARED', 'artifact': str(args.jar.resolve()), 'artifact_sha256': artifact_sha,
              'minecraft': '1.20.1', 'loader': '0.16.10', 'java_version': java_version, 'run_directory': str(run),
              'width': args.width, 'height': args.height, 'guiScale': args.gui_scale, 'maxFps': args.max_fps, 'quickPlay': bool(args.quick_play or args.server),
              'renderDistance': args.render_distance, 'simulationDistance': 5, 'qaKeybindings': args.qa_keybindings,
              'cachedProjectDependencies': modset, 'libraries': libraries, 'loader_metadata_url': loader_url,
              'extraMods': extra_mods,
              'loader_metadata_sha256': hashlib.sha256(loader_bytes).hexdigest(), 'assets': str(assets),
              'asset_index_sha1': hashlib.sha1(asset_index.read_bytes()).hexdigest(), 'isolated_asset_provenance': asset_provenance,
              'offline_username': username, 'offline_uuid': identity, 'fixture': str(fixture) if fixture else None,
              'worldName': WORLD_NAME if fixture else None, 'server': {'host': args.server, 'port': args.port} if args.server else None,
              'derived_world_copy': fixture_record,
              'command': command, 'clientVisualAcceptance': 'NOT_RUN'}
    result.update(qa)
    result['preparedFiles'] = prepared_files(run)
    write_json(run / 'client-profile.json', result)
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jar', type=Path, required=True)
    target = parser.add_mutually_exclusive_group(required=True)
    target.add_argument('--fixture', type=Path)
    target.add_argument('--server', choices=('127.0.0.1', 'localhost'), help='Join a real isolated localhost dedicated server')
    parser.add_argument('--port', type=int)
    parser.add_argument('--username', default='DreamwalkerQA', help='Use a different3..16 character offline QA name for each real client')
    parser.add_argument('--run-name', required=True)
    parser.add_argument('--report', type=Path, required=True)
    parser.add_argument('--java-home', type=Path, default=USER / 'AppData/Roaming/.minecraft/runtime/java-runtime-gamma/windows/java-runtime-gamma')
    parser.add_argument('--loader', choices=('0.16.10',), default='0.16.10')
    parser.add_argument('--quick-play', action='store_true')
    parser.add_argument('--launch', action='store_true')
    parser.add_argument('--width', type=int, default=1280)
    parser.add_argument('--height', type=int, default=720)
    parser.add_argument('--gui-scale', type=int, choices=(0, 2, 3, 4), default=0)
    parser.add_argument('--max-fps', type=int, choices=range(10, 121), default=30)
    parser.add_argument('--render-distance', type=int, choices=range(2, 33), default=5)
    parser.add_argument('--qa-keybindings', action='store_true', help='Only isolated options: use F8, attack F9, arrows movement, jump F10')
    parser.add_argument('--qa-addon', type=Path, help='Explicit separate dreamwalker_gallery_author JAR from project build/libs')
    parser.add_argument('--extra-mod', type=Path, action='append', default=[], help='Explicit regular compatibility JAR under project build; no production or QA replacements')
    parser.add_argument('--capture-input', type=Path, help='Explicit production-bound camera marker from project build')
    parser.add_argument('--heap-gb', type=int, choices=range(2, 9), default=4)
    args = parser.parse_args()
    offline_identity(args.username);connection_arguments(args)
    require(re.fullmatch(r'[a-zA-Z0-9_-]+', args.run_name), 'Unsafe client run name')
    require(640 <= args.width <= 3840 and 360 <= args.height <= 2160, 'Invalid game window dimensions')
    artifact = args.jar.resolve();ordinary(artifact);artifact_sha = sha(artifact)
    qa = qa_inputs(args, artifact_sha)
    extras = extra_mod_inputs(args.extra_mod)
    run = ROOT / 'build' / ('runtime-client-' + args.run_name)
    if run.exists():
        require(args.launch, 'Refusing to overwrite an existing prepared/game run')
        result = json.loads((run / 'client-profile.json').read_text(encoding='utf8'))
        require(result.get('schema') == 'dreamwalker-v11-client-v1' and result.get('status') == 'PREPARED'
                and result['artifact_sha256'] == artifact_sha and result['fixture'] == (str(args.fixture.resolve()) if args.fixture else None)
                and result.get('offline_username') == args.username
                and result.get('qaAddon') == qa['qaAddon'] and result.get('captureInput') == qa['captureInput']
                and [{k: v for k, v in record.items() if k not in ('copyByteVerification', 'copiedSha256')}
                     for record in result.get('extraMods', [])] == extras
                and result.get('qaKeybindings') == args.qa_keybindings and result.get('renderDistance') == args.render_distance
                and result.get('server') == ({'host': args.server, 'port': args.port} if args.server else None),
                'Existing prepared run does not match this artifact/target/identity or was already launched')
        for row in result['preparedFiles']:
            path = run / row['path'];require(path.resolve().is_relative_to(run.resolve()), 'Prepared file leaves run directory')
            ordinary(path);require(sha(path) == row['sha256'], 'Prepared client file changed: ' + row['path'])
    else:
        result = prepare(args, run, artifact_sha, qa, extras)
    if result['derived_world_copy']:
        source = Path(result['derived_world_copy']['source'])
        result['derived_world_copy']['source_unchanged_at_launch'] = all(sha(source / row['path']) == row['sha256'] for row in result['derived_world_copy']['files'])
        require(result['derived_world_copy']['source_unchanged_at_launch'], 'Fixture source changed during preparation/launch')
    if args.launch:
        with (run / 'launch-console.log').open('w', encoding='utf8') as stream:
            process = subprocess.Popen(result['command'], cwd=run, stdout=stream, stderr=subprocess.STDOUT,
                                       creationflags=subprocess.CREATE_NO_WINDOW if os.name == 'nt' else 0)
        result.update({'pid': process.pid, 'status': 'LAUNCHED_AWAITING_GAME_VERIFICATION'})
        write_json(run / 'client-profile.json', result)
    write_json(args.report, result)
    print(json.dumps({'status': result['status'], 'run_directory': str(run), 'report': str(args.report), 'pid': result.get('pid')}))


if __name__ == '__main__':
    try:
        main()
    except Exception as error:
        if '--report' in os.sys.argv:
            write_json(Path(os.sys.argv[os.sys.argv.index('--report') + 1]),
                       {'schema': 'dreamwalker-v11-client-v1', 'status': 'FAIL', 'failure': repr(error)})
        raise
