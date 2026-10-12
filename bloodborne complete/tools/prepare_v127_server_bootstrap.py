"""Prepare pinned official server bootstrap inputs without launching Java.

Only writes a project build subdirectory; source worlds and archives are never
opened. JAVA_HOME17 is required by the separate launch runner, not this downloader.
"""
from pathlib import Path
import argparse
import hashlib
import json
import os
import re
import shutil
import stat
import urllib.parse
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[1]
GAME, LOADER, INSTALLER = '1.20.1', '0.16.10', '1.0.1'
INSTALLER_SOURCE_COMMIT = '73c80c6457ac0acf6412979dc3d23a0ba2aaf2da'
HOSTS = {'meta.fabricmc.net', 'maven.fabricmc.net', 'piston-meta.mojang.com',
         'piston-data.mojang.com', 'launchermeta.mojang.com', 'launcher.mojang.com',
         'libraries.minecraft.net', 'repo.maven.apache.org'}


def ordinary(path):
    for part in (path, *path.parents):
        info = part.lstat()
        assert not stat.S_ISLNK(info.st_mode) and not getattr(info, 'st_file_attributes', 0) & 0x400, part
        if part == ROOT:
            break


def checked_url(url):
    parsed = urllib.parse.urlparse(url)
    assert parsed.scheme == 'https' and parsed.hostname in HOSTS and not parsed.username and not parsed.password, url
    return url


def fetch(url):
    with urllib.request.urlopen(checked_url(url), timeout=40) as response:
        checked_url(response.geturl())
        return response.read()


def hashes(path):
    sha1, sha256 = hashlib.sha1(), hashlib.sha256()
    with path.open('rb') as stream:
        while data := stream.read(1024 * 1024):
            sha1.update(data)
            sha256.update(data)
    return {'sha1': sha1.hexdigest(), 'sha256': sha256.hexdigest(), 'bytes': path.stat().st_size}


def artifact(output, relative, url, expected_sha1=None, candidates=()):
    destination = output / relative
    assert destination.absolute().is_relative_to(output) and '..' not in relative.parts
    destination.parent.mkdir(parents=True, exist_ok=True)
    source = 'PREVIOUS_VERIFIED_DOWNLOAD'
    if destination.exists():
        ordinary(destination)
    else:
        chosen = next((path for path in candidates if path.is_file() and hashes(path)['sha1'] == expected_sha1), None) if expected_sha1 else None
        if chosen:
            ordinary(chosen)
            shutil.copyfile(chosen, destination)
            source = 'MATCHING_LOCAL_CACHE'
        else:
            partial = destination.with_name(destination.name + '.download')
            assert not partial.exists()
            with urllib.request.urlopen(checked_url(url), timeout=40) as response, partial.open('wb') as stream:
                checked_url(response.geturl())
                shutil.copyfileobj(response, stream, 1024 * 1024)
            partial.replace(destination)
            source = 'OFFICIAL_HTTPS_DOWNLOAD'
    result = hashes(destination)
    assert not expected_sha1 or result['sha1'] == expected_sha1, (relative, result)
    with zipfile.ZipFile(destination) as archive:
        assert archive.testzip() is None, destination
    return dict(path=relative.as_posix(), url=url, expectedOfficialSha1=expected_sha1,
                provenance=source, **result)


def write(path, document):
    path.write_text(json.dumps(document, ensure_ascii=False, indent=2) + '\n', encoding='utf8')


def maven_path(name):
    group, artifact_name, version = name.split(':')
    return Path(*group.split('.')) / artifact_name / version / f'{artifact_name}-{version}.jar'


def manifest_fields(data):
    """Unfold standard JAR manifest continuation lines."""
    lines = data.decode('utf8').replace('\r\n ', '').splitlines()
    return dict(line.split(': ', 1) for line in lines if ': ' in line)


def manifest_line(name, value):
    # Pinned artifact paths/main classes are ASCII; Java manifests limit lines
    # to 72 bytes, including CRLF. Continuations begin with a single space.
    raw = f'{name}: {value}'.encode('ascii')
    chunks = [raw[:70]]
    raw = raw[70:]
    while raw:
        chunks.append(b' ' + raw[:69])
        raw = raw[69:]
    return b'\r\n'.join(chunks) + b'\r\n'


def make_cached_launcher(output, profile, libraries):
    """Generate the manifest-only cache produced by official installer 1.0.1.

    See ServerInstaller.makeLaunchJar at the pinned source commit. Loader
    0.16.10 uses external verified libraries, not shaded classes. This generated
    cache is explicitly distinguished from the downloaded official capsule.
    """
    loader = next(row for row in libraries if row['path'].endswith(f'fabric-loader-{LOADER}.jar'))
    with zipfile.ZipFile(output / loader['path']) as archive:
        main = manifest_fields(archive.read('META-INF/MANIFEST.MF'))['Main-Class']
        assert main == 'net.fabricmc.loader.impl.launch.server.FabricServerLauncher', main
        assert main.replace('.', '/') + '.class' in archive.namelist()
    relative = Path('.fabric/server') / f'fabric-loader-server-{LOADER}-minecraft-{GAME}.jar'
    destination = output / relative
    destination.parent.mkdir(parents=True, exist_ok=True)
    ordinary(destination.parent)
    if destination.exists():
        ordinary(destination)
    classpath = ' '.join('../../' + row['path'] for row in libraries)
    manifest = (manifest_line('Manifest-Version', '1.0') + manifest_line('Main-Class', main)
                + manifest_line('Class-Path', classpath) + b'\r\n')
    properties = ('launch.mainClass=' + profile['mainClass'] + '\n').encode('ascii')
    with zipfile.ZipFile(destination, 'w', zipfile.ZIP_DEFLATED) as archive:
        for name, data in [('META-INF/MANIFEST.MF', manifest), ('fabric-server-launch.properties', properties)]:
            item = zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0))
            item.compress_type = zipfile.ZIP_DEFLATED
            archive.writestr(item, data)
    with zipfile.ZipFile(destination) as archive:
        assert archive.testzip() is None
        assert manifest_fields(archive.read('META-INF/MANIFEST.MF'))['Class-Path'].split() == classpath.split()
    return dict(path=relative.as_posix(), provenance='GENERATED_OFFICIAL_INSTALLER_CACHE_LAYOUT',
                sourceCommit=INSTALLER_SOURCE_COMMIT, **hashes(destination))


def stage_bootstrap(directory, runtime, launcher):
    """Stage only hashed server bootstrap inputs into a fresh/resumed runtime."""
    directory, runtime, launcher = (Path(os.path.abspath(path)) for path in (directory, runtime, launcher))
    assert directory.is_relative_to(ROOT / 'build') and runtime.is_relative_to(ROOT / 'build')
    ordinary(directory)
    report = json.loads((directory / 'bootstrap.json').read_text(encoding='utf8'))
    assert report['status'] == 'PASS_PREPARED_NATIVE_NOT_RUN'
    assert (report['minecraft'], report['loader'], report['installer']) == (GAME, LOADER, INSTALLER)
    assert launcher == directory / report['launcher']['path'], 'Use this bundle\'s pinned official capsule'
    ordinary(launcher)
    assert hashes(launcher)['sha256'] == report['launcher']['sha256']
    assert runtime != directory and not runtime.is_relative_to(directory), 'Bootstrap bundle must remain separate'
    runtime.mkdir(parents=True, exist_ok=True)
    ordinary(runtime)
    staged = {}
    for row in report['runtimeFiles']:
        source_relative, target_relative = Path(row['sourcePath']), Path(row['runtimePath'])
        assert not source_relative.is_absolute() and '..' not in source_relative.parts
        assert not target_relative.is_absolute() and '..' not in target_relative.parts
        source, destination = directory / source_relative, runtime / target_relative
        ordinary(source)
        assert hashes(source)['sha256'] == row['sha256'], source
        destination.parent.mkdir(parents=True, exist_ok=True)
        ordinary(destination.parent)
        if destination.exists():
            ordinary(destination)
            assert hashes(destination)['sha256'] == row['sha256'], 'Changed bootstrap input: ' + str(destination)
        else:
            shutil.copyfile(source, destination)
        assert hashes(destination)['sha256'] == row['sha256']
        staged[target_relative.as_posix()] = row['sha256']
    write(runtime / 'dw-server-bootstrap.json', {'status': 'PASS_STAGED_NATIVE_NOT_RUN',
          'bootstrapManifestSha256': hashes(directory / 'bootstrap.json')['sha256'],
          'officialCapsuleSha256': report['launcher']['sha256'], 'runtimeFiles': staged,
          'javaLaunched': False, 'worldsOpened': False})
    return staged


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, default=ROOT / 'build/v127-server-bootstrap')
    parser.add_argument('--stage-runtime', type=Path, help='Stage pinned dependencies only; never launch Java or open a world')
    args = parser.parse_args()
    output = Path(os.path.abspath(args.output))
    assert output.is_relative_to(ROOT / 'build')
    output.mkdir(parents=True, exist_ok=True)
    ordinary(output)
    profile_url = f'https://meta.fabricmc.net/v2/versions/loader/{GAME}/{LOADER}/server/json'
    profile_raw = fetch(profile_url)
    profile = json.loads(profile_raw)
    assert profile['id'] == f'fabric-loader-{LOADER}-{GAME}', profile['id']
    assert profile['mainClass'] in ('net.fabricmc.loader.impl.launch.knot.KnotServer',
                                    'net.fabricmc.loader.impl.launch.server.FabricServerLauncher'), profile['mainClass']
    (output / 'fabric-loader-profile.json').write_bytes(profile_raw)

    launcher_url = f'https://meta.fabricmc.net/v2/versions/loader/{GAME}/{LOADER}/{INSTALLER}/server/jar'
    launcher = artifact(output, Path('fabric-server-launch.jar'), launcher_url)
    with zipfile.ZipFile(output / launcher['path']) as archive:
        manifest = archive.read('META-INF/MANIFEST.MF').decode('utf8')
        property_entries = {'install.properties': archive.read('install.properties').decode('utf8')}
        assert 'net.fabricmc.installer.ServerLauncher' in manifest, manifest
        properties = '\n'.join(property_entries.values())
        assert f'fabric-loader-version={LOADER}' in properties and f'game-version={GAME}' in properties, property_entries
    libraries = []
    for library in profile['libraries']:
        relative = maven_path(library['name'])
        url = library.get('url', 'https://maven.fabricmc.net/').rstrip('/') + '/' + relative.as_posix()
        checksum = fetch(url + '.sha1').decode('ascii').strip().split()[0]
        assert re.fullmatch(r'[a-fA-F0-9]{40}', checksum), (library['name'], checksum)
        cache = list((Path.home() / '.gradle/caches/modules-2/files-2.1').glob(f"{library['name'].split(':')[0]}/{relative.parts[-3]}/{relative.parts[-2]}/*/{relative.name}"))
        row = artifact(output, Path('libraries') / relative, url, checksum.lower(), cache)
        for name, field in [('sha1', 'sha1'), ('sha256', 'sha256'), ('size', 'bytes')]:
            assert name not in library or row[field] == library[name], (library['name'], field)
        libraries.append(row)
    assert any(item['path'].endswith(f'fabric-loader-{LOADER}.jar') for item in libraries)
    manifest_url = 'https://piston-meta.mojang.com/mc/game/version_manifest_v2.json'
    versions_raw = fetch(manifest_url)
    versions = json.loads(versions_raw)
    version = next(row for row in versions['versions'] if row['id'] == GAME)
    version_raw = fetch(version['url'])
    assert hashlib.sha1(version_raw).hexdigest() == version['sha1']
    details = json.loads(version_raw)
    assert details['id'] == GAME
    (output / 'minecraft-version.json').write_bytes(version_raw)
    download = details['downloads']['server']
    server = artifact(output, Path('server.jar'), download['url'], download['sha1'],
                      [Path.home() / f'.gradle/caches/fabric-loom/{GAME}/minecraft-server.jar'])
    assert server['bytes'] == download['size']
    cached_launcher = make_cached_launcher(output, profile, libraries)
    runtime_files = [dict(sourcePath=row['path'], runtimePath=row['path'], sha256=row['sha256'])
                     for row in [*libraries, cached_launcher]]
    runtime_files.append(dict(sourcePath=server['path'], runtimePath=f'.fabric/server/{GAME}-server.jar', sha256=server['sha256']))
    report = {'schema': 'dreamwalker-v127-official-server-bootstrap-v1',
              'status': 'PASS_PREPARED_NATIVE_NOT_RUN', 'minecraft': GAME, 'loader': LOADER,
              'installer': INSTALLER, 'javaRequired': 17, 'javaLaunched': False,
              'worldsOpened': False, 'sourceWorldOrArchivesWritten': False,
              'officialProfileUrl': profile_url, 'officialProfileSha256': hashlib.sha256(profile_raw).hexdigest(),
              'officialLauncherManifest': manifest, 'officialLauncherProperties': property_entries,
              'launcher': launcher, 'server': server, 'libraries': libraries,
              'cachedLauncher': cached_launcher, 'runtimeFiles': runtime_files,
              'installerSourceUrl': f'https://github.com/FabricMC/fabric-installer/blob/{INSTALLER_SOURCE_COMMIT}/src/main/java/net/fabricmc/installer/server/ServerInstaller.java',
              'minecraftManifestUrl': manifest_url, 'minecraftVersionUrl': version['url'],
              'minecraftVersionOfficialSha1': version['sha1']}
    write(output / 'bootstrap.json', report)
    if args.stage_runtime:
        staged = stage_bootstrap(output, args.stage_runtime, output / launcher['path'])
        assert len(staged) == len(libraries) + 2
    print(json.dumps({key: report[key] for key in ('status', 'minecraft', 'loader', 'javaLaunched', 'worldsOpened')}, ensure_ascii=False), flush=True)


if __name__ == '__main__':
    main()
