"""Run one pinned Minecraft 1.20.1/Fabric 0.16.10 localhost server under build/.

Only current cached minimal dependencies, optional gallery authoring and a verified
derived-world copy are supported. Run through CodexControl/Guard.ps1 -Mode Operation.
"""
from __future__ import annotations
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import socket
import stat
import subprocess
import time
import urllib.parse
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[1]
USER = Path.home()
DEPENDENCIES = [('net.fabricmc.fabric-api', 'fabric-api', '0.92.9+1.20.1'),
                ('software.bernie.geckolib', 'geckolib-fabric-1.20.1', '4.4.9')]


def require(value, message):
    if not value:
        raise ValueError(message)


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def write_json(path, value):
    Path(path).parent.mkdir(parents=True, exist_ok=True)
    Path(path).write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf8')


def ordinary(path):
    info = Path(path).lstat()
    require(not stat.S_ISLNK(info.st_mode) and not (getattr(info, 'st_file_attributes', 0) & 0x400),
            'Reparse point cannot be copied: ' + str(path))


def files_without_links(directory):
    ordinary(directory)
    for current, directories, files in os.walk(directory, followlinks=False):
        for name in directories + files:
            ordinary(Path(current) / name)
        for name in files:
            if name != 'session.lock':
                yield Path(current) / name


def copy_verified(source, destination):
    ordinary(source)
    destination.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(source, destination)
    require(sha(source) == sha(destination), 'Copied file checksum differs: ' + str(source))


def fetch(url):
    parsed = urllib.parse.urlparse(url)
    require(parsed.scheme == 'https' and parsed.hostname in
            {'meta.fabricmc.net', 'maven.fabricmc.net', 'repo.maven.apache.org', 'libraries.minecraft.net'},
            'Library URL must use an official HTTPS repository: ' + url)
    with urllib.request.urlopen(url, timeout=30) as response:
        return response.read()


def commands_from(path):
    if not path:
        return []
    require(path.stat().st_size <= 65536, 'Console command input exceeds64KiB')
    commands = json.loads(path.read_text(encoding='utf8'))
    require(isinstance(commands, list) and len(commands) <= 128 and all(
        isinstance(c, str) and 0 < len(c) <= 2048 and not any(x in c for x in '\r\n\0') for c in commands),
        'Commands must be at most128 bounded single-line strings')
    return commands


def classify_log(log):
    optional_reach = re.compile(r'^\[[^\]]+\] \[main/WARN\]: Error loading class: '
        r'com/jamieswhiteshirt/reachentityattributes/ReachEntityAttributes '
        r'\(java\.lang\.ClassNotFoundException: com/jamieswhiteshirt/reachentityattributes/ReachEntityAttributes\)$')
    expected = [line for line in log.splitlines() if optional_reach.fullmatch(line)]
    errors = [line for line in log.splitlines() if ('/ERROR]' in line or 'Exception' in line)
              and not optional_reach.fullmatch(line)]
    return errors[:50], expected


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jar', type=Path, required=True)
    parser.add_argument('--accepted-eula-file', type=Path, required=True)
    parser.add_argument('--java-home', type=Path, default=USER / 'AppData/Roaming/.minecraft/runtime/java-runtime-gamma/windows/java-runtime-gamma')
    parser.add_argument('--loader', choices=('0.16.10',), default='0.16.10')
    parser.add_argument('--run-name', required=True)
    parser.add_argument('--report', type=Path, required=True)
    parser.add_argument('--extra-mod', type=Path, action='append', default=[])
    parser.add_argument('--gallery-input', type=Path)
    parser.add_argument('--world-copy', type=Path)
    parser.add_argument('--commands-file', type=Path)
    parser.add_argument('--hold-seconds', type=int, default=0)
    parser.add_argument('--gallery-timeout', type=int, default=180)
    parser.add_argument('--startup-timeout', type=int, default=180)
    parser.add_argument('--shutdown-timeout', type=int, default=90)
    args = parser.parse_args()
    require(re.fullmatch(r'[a-zA-Z0-9_-]+', args.run_name), 'Unsafe run name')
    require(0 <= args.hold_seconds <= 3600 and 1 <= args.gallery_timeout <= 300 and
            1 <= args.startup_timeout <= 600 and 1 <= args.shutdown_timeout <= 180, 'Run timeouts must be bounded')
    require(not (args.gallery_input and args.world_copy), 'Fresh gallery authoring and saved-world reopen are separate runs')
    jar = args.jar.resolve();ordinary(jar)
    artifact_sha = sha(jar)
    eula = args.accepted_eula_file.resolve()
    require(re.search(r'(?m)^\s*eula\s*=\s*true\s*$', eula.read_text(encoding='utf8')), 'Explicit existing eula=true evidence required')
    java = args.java_home.resolve() / 'bin' / ('java.exe' if os.name == 'nt' else 'java')
    version = subprocess.run([str(java), '-version'], capture_output=True, text=True, check=True).stderr.strip()
    require(re.search(r'version "17\.', version), 'Pinned runtime requires Java17')
    run = ROOT / 'build' / ('runtime-server-' + args.run_name)
    require(not run.exists(), 'Refusing to overwrite an existing isolated run: ' + str(run))
    mods = run / 'mods';mods.mkdir(parents=True)
    copy_verified(jar, mods / jar.name)
    modset = []
    for group, name, dep_version in DEPENDENCIES:
        cached = USER / '.gradle/caches/modules-2/files-2.1' / group / name / dep_version
        source = next((p for p in sorted(cached.glob('*/*.jar')) if not p.name.endswith(('-sources.jar', '-javadoc.jar'))), None)
        require(source is not None, 'Exact cached dependency missing: ' + name + ':' + dep_version)
        copy_verified(source, mods / source.name)
        modset.append({'path': str(source), 'sha256': sha(source), 'id': name, 'version': dep_version})
    for extra in args.extra_mod:
        extra = extra.resolve()
        require(not (mods / extra.name).exists(), 'Extra mod filename collides: ' + extra.name)
        copy_verified(extra, mods / extra.name)
        with zipfile.ZipFile(extra) as package:
            metadata = json.loads(package.read('fabric.mod.json'))
        modset.append({'path': str(extra), 'sha256': sha(extra), 'id': metadata['id'], 'version': metadata['version'], 'extra_mod': True})
    world_copy = None;source_world = None
    if args.world_copy:
        ordinary(args.world_copy)
        source_world = args.world_copy.resolve()
        require(source_world.is_relative_to((ROOT / 'build').resolve()) and (source_world / 'level.dat').is_file(),
                'Only this project saved derived world can be reopened')
        copied = []
        for source in sorted(files_without_links(source_world)):
            relative = source.relative_to(source_world)
            copy_verified(source, run / 'isolated-smoke-world' / relative)
            copied.append({'path': relative.as_posix(), 'bytes': source.stat().st_size, 'sha256': sha(source)})
        world_copy = {'source': str(source_world), 'files': copied, 'omitted_files': ['session.lock'], 'copy_byte_verification': 'PASS'}
    if args.gallery_input:
        marker = json.loads(args.gallery_input.read_text(encoding='utf8'))
        require(args.extra_mod and marker.get('revision') == 'V11' and
                marker.get('guard') == 'FRESH_ISOLATED_CATALOGUE_GALLERY_ONLY' and marker.get('productionJarSha256') == artifact_sha,
                'Gallery marker must bind this exact production JAR and author add-on')
        copy_verified(args.gallery_input, run / 'catalogue-gallery-input.json')
    bundled = USER / '.gradle/caches/fabric-loom/1.20.1/minecraft-server.jar'
    copy_verified(bundled, run / 'server.jar')
    metadata_url = f'https://meta.fabricmc.net/v2/versions/loader/1.20.1/{args.loader}/server/json'
    raw_metadata = fetch(metadata_url);metadata = json.loads(raw_metadata)
    (run / 'fabric-loader-profile.json').write_bytes(raw_metadata)
    libraries = []
    for library in metadata['libraries']:
        group, name, lib_version = library['name'].split(':')
        relative = Path(group.replace('.', '/')) / name / lib_version / (name + '-' + lib_version + '.jar')
        target = run / 'libraries' / relative;target.parent.mkdir(parents=True, exist_ok=True)
        candidates = [USER / 'AppData/Roaming/.minecraft/libraries' / relative]
        candidates += sorted((USER / '.gradle/caches/modules-2/files-2.1' / group / name / lib_version).glob('*/*.jar'))
        expected = library.get('sha256')
        candidate = next((p for p in candidates if p.is_file() and not p.name.endswith(('-sources.jar', '-javadoc.jar'))
                          and (expected is None or sha(p) == expected)), None)
        if candidate:
            copy_verified(candidate, target);provenance = str(candidate)
        else:
            provenance = library.get('url', 'https://maven.fabricmc.net/').rstrip('/') + '/' + relative.as_posix()
            target.write_bytes(fetch(provenance))
        require(expected is None or sha(target) == expected, 'Official loader library checksum differs: ' + library['name'])
        libraries.append({'coordinate': library['name'], 'path': 'libraries/' + relative.as_posix(), 'sha256': sha(target), 'source': provenance})
    (run / 'fabric-server-launcher.properties').write_text('serverJar=server.jar\n', encoding='utf8')
    copy_verified(eula, run / 'eula.txt')
    with socket.socket() as sock:
        sock.bind(('127.0.0.1', 0));port = sock.getsockname()[1]
    properties = {'server-ip': '127.0.0.1', 'server-port': str(port), 'online-mode': 'false', 'spawn-protection': '0',
                  'level-name': 'isolated-smoke-world', 'level-type': 'minecraft:flat', 'generate-structures': 'false',
                  'view-distance': '2', 'simulation-distance': '2', 'max-players': '2', 'enable-query': 'false', 'enable-rcon': 'false',
                  'gamemode': 'creative', 'op-permission-level': '4', 'difficulty': 'normal',
                  'generator-settings': json.dumps({'biome': 'minecraft:plains', 'layers': [{'block': 'minecraft:bedrock', 'height': 1},
                    {'block': 'minecraft:dirt', 'height': 126}, {'block': 'minecraft:grass_block', 'height': 1}],
                    'lakes': False, 'features': False, 'structure_overrides': []}, separators=(',', ':'))}
    (run / 'server.properties').write_text('\n'.join(k + '=' + v for k, v in properties.items()) + '\n', encoding='utf8')
    command = [str(java), '-Xmx3G', '-cp', os.pathsep.join(str(run / l['path']) for l in libraries),
               'net.fabricmc.loader.impl.launch.server.FabricServerLauncher', 'nogui']
    result = {'schema': 'dreamwalker-v11-server-v1', 'status': 'RUNNING', 'artifact': str(jar), 'artifact_sha256': artifact_sha,
              'minecraft': '1.20.1', 'loader': args.loader, 'java_version': version, 'modset': modset,
              'loader_metadata_source': metadata_url, 'loader_metadata_sha256': hashlib.sha256(raw_metadata).hexdigest(),
              'libraries': libraries, 'vanilla_server_sha256': sha(bundled), 'server_properties': properties,
              'eula_evidence': {'path': str(eula), 'sha256': sha(eula)}, 'run_directory': str(run), 'command': command,
              'derived_world_copy': world_copy, 'actualClientVisualAcceptance': 'NOT_RUN'}
    write_json(args.report, result)
    console = run / 'launch-console.log';started = time.monotonic();startup = False
    with console.open('w', encoding='utf8') as stream:
        process = subprocess.Popen(command, cwd=run, stdin=subprocess.PIPE, stdout=stream, stderr=subprocess.STDOUT,
                                   text=True, creationflags=subprocess.CREATE_NO_WINDOW if os.name == 'nt' else 0)
        try:
            while process.poll() is None and time.monotonic() - started < args.startup_timeout:
                if re.search(r'Done \([\d.]+s\)!', console.read_text(encoding='utf8', errors='replace')):
                    startup = True;result['startup_seconds'] = round(time.monotonic() - started, 3)
                    commands = ['list', *commands_from(args.commands_file)];result['commands'] = commands
                    for text in commands:
                        process.stdin.write(text + '\n')
                    process.stdin.flush()
                    if args.gallery_input:
                        deadline = time.monotonic() + args.gallery_timeout
                        while process.poll() is None and not (run / 'catalogue-gallery-output.json').is_file() and time.monotonic() < deadline:
                            time.sleep(1)
                    else:
                        deadline = time.monotonic() + (args.hold_seconds or 2)
                        while process.poll() is None and time.monotonic() < deadline and not (run / 'qa-normal-stop.request').is_file():
                            time.sleep(1)
                    if process.poll() is None:
                        process.stdin.write('stop\n');process.stdin.flush()
                    break
                time.sleep(1)
            try:
                process.wait(timeout=args.shutdown_timeout if startup else 5)
            except subprocess.TimeoutExpired:
                result['termination'] = 'Bounded startup/shutdown timeout';process.terminate();process.wait(timeout=10)
        finally:
            if process.poll() is None:
                process.kill();process.wait()
        result['exit_code'] = process.returncode
    log = console.read_text(encoding='utf8', errors='replace')
    errors, expected_warnings = classify_log(log)
    result.update({'elapsed_seconds': round(time.monotonic() - started, 3), 'evidence': str(console), 'console_sha256': sha(console),
                   'world_startup': 'PASS' if startup else 'FAIL', 'errors': errors, 'expectedWarnings': expected_warnings})
    result['status'] = 'PASS' if startup and process.returncode == 0 and 'Saving worlds' in log and not result['errors'] and not result.get('termination') else 'FAIL'
    if args.gallery_input:
        output = run / 'catalogue-gallery-output.json'
        if output.is_file():
            gallery = json.loads(output.read_text(encoding='utf8'))
            result['gallery'] = {'path': str(output), 'sha256': sha(output), 'result': gallery}
            if gallery.get('status') != 'PASS_AUTHORED_REQUIRES_PRODUCTION_REOPEN':
                result['status'] = 'FAIL_GALLERY_AUTHORING'
        else:
            result['status'] = 'FAIL_GALLERY_NO_OUTPUT'
    if world_copy:
        unchanged = all(sha(source_world / f['path']) == f['sha256'] for f in world_copy['files'])
        result['derived_world_copy']['source_unchanged_after_run'] = unchanged
        if not unchanged:
            result['status'] = 'FAIL_SOURCE_WORLD_CHANGED'
    write_json(args.report, result)
    print(json.dumps({k: result[k] for k in ('status', 'artifact_sha256', 'run_directory')}))
    if result['status'] != 'PASS':
        raise SystemExit(1)


if __name__ == '__main__':
    try:
        main()
    except Exception as error:
        # Guard hides child stdio, so setup failures must remain reviewable on disk.
        if '--report' in os.sys.argv:
            report_path = Path(os.sys.argv[os.sys.argv.index('--report') + 1])
            previous = json.loads(report_path.read_text(encoding='utf8')) if report_path.is_file() else {}
            previous.update({'schema': 'dreamwalker-v11-server-v1', 'status': 'FAIL', 'failure': repr(error)})
            write_json(report_path, previous)
        raise
