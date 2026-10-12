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
HOSTS = {'meta.fabricmc.net', 'maven.fabricmc.net', 'piston-meta.mojang.com',
         'piston-data.mojang.com', 'launchermeta.mojang.com', 'launcher.mojang.com',
         'libraries.minecraft.net', 'repo.maven.apache.org'}


def ordinary(path):
    info = path.lstat()
    assert not stat.S_ISLNK(info.st_mode) and not getattr(info, 'st_file_attributes', 0) & 0x400, path


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


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, default=ROOT / 'build/v127-server-bootstrap')
    args = parser.parse_args()
    output = args.output.absolute()
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
        libraries.append(artifact(output, Path('libraries') / relative, url, checksum.lower(), cache))
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
    report = {'schema': 'dreamwalker-v127-official-server-bootstrap-v1',
              'status': 'PASS_PREPARED_NATIVE_NOT_RUN', 'minecraft': GAME, 'loader': LOADER,
              'installer': INSTALLER, 'javaRequired': 17, 'javaLaunched': False,
              'worldsOpened': False, 'sourceWorldOrArchivesWritten': False,
              'officialProfileUrl': profile_url, 'officialProfileSha256': hashlib.sha256(profile_raw).hexdigest(),
              'officialLauncherManifest': manifest, 'officialLauncherProperties': property_entries,
              'launcher': launcher, 'server': server, 'libraries': libraries,
              'minecraftManifestUrl': manifest_url, 'minecraftVersionUrl': version['url'],
              'minecraftVersionOfficialSha1': version['sha1']}
    write(output / 'bootstrap.json', report)
    print(json.dumps({key: report[key] for key in ('status', 'minecraft', 'loader', 'javaLaunched', 'worldsOpened')}, ensure_ascii=False), flush=True)


if __name__ == '__main__':
    main()
