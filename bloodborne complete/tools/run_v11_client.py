"""Prepare an isolated Minecraft1.20.1/Fabric0.16.10 client; --launch opens the game.

Only production plus current cached dependencies are installed. A project-build
world is byte-copied, and Mojang libraries/assets are checksum-verified. Run through
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
from run_v11_server import DEPENDENCIES, ROOT, USER, copy_verified, fetch, files_without_links, ordinary, require, sha, write_json

WORLD_NAME = 'prototype-fixture'


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


def prepare(args, run, artifact_sha):
    mods = run / 'mods';mods.mkdir(parents=True)
    copy_verified(args.jar.resolve(), mods / args.jar.name)
    modset = []
    for group, name, version in DEPENDENCIES:
        source = next((p for p in sorted((USER / '.gradle/caches/modules-2/files-2.1' / group / name / version).glob('*/*.jar'))
                       if not p.name.endswith(('-sources.jar', '-javadoc.jar'))), None)
        require(source is not None, 'Missing exact cached dependency: ' + name + ':' + version)
        copy_verified(source, mods / source.name)
        modset.append({'coordinate': group + ':' + name + ':' + version, 'source': str(source), 'sha256': sha(source)})
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
    fixture = args.fixture.resolve();ordinary(args.fixture)
    require(fixture.is_relative_to((ROOT / 'build').resolve()) and (fixture / 'level.dat').is_file(),
            'Client fixture must be a saved derived world under this project build folder')
    copied = []
    for source in sorted(files_without_links(fixture)):
        relative = source.relative_to(fixture);copy_verified(source, run / 'saves' / WORLD_NAME / relative)
        copied.append({'path': relative.as_posix(), 'bytes': source.stat().st_size, 'sha256': sha(source)})
    options = 'autoJump:false\nfullscreen:false\nrenderDistance:8\nsimulationDistance:5\ngamma:1.0\ntutorialStep:none\npauseOnLostFocus:false\n'
    options += 'guiScale:' + str(args.gui_scale) + '\n'
    (run / 'options.txt').write_text(options, encoding='utf8')
    username = 'DreamwalkerQA';identity = str(uuid.UUID(bytes=hashlib.md5(('OfflinePlayer:' + username).encode()).digest(), version=3))
    command = [str(java), '-Xmx' + str(args.heap_gb) + 'G', '-Djava.library.path=' + str(natives), '-cp', os.pathsep.join(classpath + [str(client)]),
               'net.fabricmc.loader.impl.launch.knot.KnotClient', '--username', username, '--uuid', identity, '--accessToken', '0', '--userType', 'legacy',
               '--version', '1.20.1', '--gameDir', str(run), '--assetsDir', str(assets), '--assetIndex', asset_index.stem,
               '--width', str(args.width), '--height', str(args.height)]
    if args.quick_play:
        command += ['--quickPlaySingleplayer', WORLD_NAME]
    result = {'schema': 'dreamwalker-v11-client-v1', 'status': 'PREPARED', 'artifact': str(args.jar.resolve()), 'artifact_sha256': artifact_sha,
              'minecraft': '1.20.1', 'loader': '0.16.10', 'java_version': java_version, 'run_directory': str(run),
              'width': args.width, 'height': args.height, 'guiScale': args.gui_scale, 'quickPlay': args.quick_play,
              'cachedProjectDependencies': modset, 'libraries': libraries, 'loader_metadata_url': loader_url,
              'loader_metadata_sha256': hashlib.sha256(loader_bytes).hexdigest(), 'assets': str(assets),
              'asset_index_sha1': hashlib.sha1(asset_index.read_bytes()).hexdigest(), 'isolated_asset_provenance': asset_provenance,
              'offline_username': username, 'offline_uuid': identity, 'fixture': str(fixture), 'worldName': WORLD_NAME,
              'derived_world_copy': {'source': str(fixture), 'files': copied, 'omitted_files': ['session.lock'], 'copy_byte_verification': 'PASS'},
              'command': command, 'clientVisualAcceptance': 'NOT_RUN'}
    result['preparedFiles'] = prepared_files(run)
    write_json(run / 'client-profile.json', result)
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jar', type=Path, required=True)
    parser.add_argument('--fixture', type=Path, required=True)
    parser.add_argument('--run-name', required=True)
    parser.add_argument('--report', type=Path, required=True)
    parser.add_argument('--java-home', type=Path, default=USER / 'AppData/Roaming/.minecraft/runtime/java-runtime-gamma/windows/java-runtime-gamma')
    parser.add_argument('--loader', choices=('0.16.10',), default='0.16.10')
    parser.add_argument('--quick-play', action='store_true')
    parser.add_argument('--launch', action='store_true')
    parser.add_argument('--width', type=int, default=1280)
    parser.add_argument('--height', type=int, default=720)
    parser.add_argument('--gui-scale', type=int, choices=(0, 2, 3, 4), default=0)
    parser.add_argument('--heap-gb', type=int, choices=range(2, 9), default=4)
    args = parser.parse_args()
    require(re.fullmatch(r'[a-zA-Z0-9_-]+', args.run_name), 'Unsafe client run name')
    require(640 <= args.width <= 3840 and 360 <= args.height <= 2160, 'Invalid game window dimensions')
    artifact = args.jar.resolve();ordinary(artifact);artifact_sha = sha(artifact)
    run = ROOT / 'build' / ('runtime-client-' + args.run_name)
    if run.exists():
        require(args.launch, 'Refusing to overwrite an existing prepared/game run')
        result = json.loads((run / 'client-profile.json').read_text(encoding='utf8'))
        require(result.get('schema') == 'dreamwalker-v11-client-v1' and result.get('status') == 'PREPARED'
                and result['artifact_sha256'] == artifact_sha and result['fixture'] == str(args.fixture.resolve()),
                'Existing prepared run does not match this artifact/fixture or was already launched')
        for row in result['preparedFiles']:
            path = run / row['path'];require(path.resolve().is_relative_to(run.resolve()), 'Prepared file leaves run directory')
            ordinary(path);require(sha(path) == row['sha256'], 'Prepared client file changed: ' + row['path'])
    else:
        result = prepare(args, run, artifact_sha)
    if args.launch:
        with (run / 'launch-console.log').open('w', encoding='utf8') as stream:
            process = subprocess.Popen(result['command'], cwd=run, stdout=stream, stderr=subprocess.STDOUT,
                                       creationflags=subprocess.CREATE_NO_WINDOW if os.name == 'nt' else 0)
        result.update({'pid': process.pid, 'status': 'LAUNCHED_AWAITING_GAME_VERIFICATION'})
        write_json(run / 'client-profile.json', result)
    source = Path(result['derived_world_copy']['source'])
    result['derived_world_copy']['source_unchanged_at_launch'] = all(sha(source / row['path']) == row['sha256'] for row in result['derived_world_copy']['files'])
    require(result['derived_world_copy']['source_unchanged_at_launch'], 'Fixture source changed during preparation/launch')
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
