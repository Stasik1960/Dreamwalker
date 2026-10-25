"""Assemble verified local release files after acceptance; no Git/network/game work.

Root runs under Guard, after seal_final.py, install_desktop.py and package_sources.py.
Every output tree must be fresh. Failures leave inspectable partial files; no deletion.
"""
import argparse
import datetime
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import shutil
import stat
import zipfile
import zlib

P = Path(__file__).resolve().parents[2]
W = P.parent.parent
T = W / 'DW-visual-physics-20261024'
O = W / 'DW-userpack-optimization-20261024'
R = P.parent / 'research'
D = Path('C:/Users/vakir/Desktop')
B = T / 'publication'
REPOSITORY = 'Stasik1960/Dreamwalker'
BRANCH = 'codex/native-visual-physics-20261025'
TAG = 'native-grid-city-visual-physics-20261025'
RELEASE = 'https://github.com/' + REPOSITORY + '/releases/tag/' + TAG
SOURCE_STATUS = 'FULL_EFFECTIVE_SOURCE_RESOURCES_AND_MIGRATION_TOOLS'
SMALL_LIMIT = 16 * 1024 * 1024
FROZEN_RECORDER_SHA = '6b88d3caafa05e48a6bc89dff7b973241ba8e50f3e4e9bbb704514d1e908645b'


def require(condition, message):
    if not condition:
        raise RuntimeError(message)


def plain(path):
    path = Path(os.path.abspath(path))
    for item in (path, *path.parents):
        try:
            info = item.lstat()
        except FileNotFoundError:
            continue
        require(not stat.S_ISLNK(info.st_mode) and
                not getattr(info, 'st_file_attributes', 0) & 0x400,
                'Reparse/symlink is not permitted: ' + str(item))
    return path


def sha(path):
    plain(path)
    result = hashlib.sha256()
    with Path(path).open('rb') as stream:
        for data in iter(lambda: stream.read(1024 * 1024), b''):
            result.update(data)
    return result.hexdigest()


def read(path):
    plain(path)
    return json.loads(Path(path).read_text(encoding='utf-8-sig'))


def ref(path):
    return {'path': str(path), 'sha256': sha(path), 'bytes': Path(path).stat().st_size}


def write(path, value, replace=False):
    """Exclusive output, except the two explicitly allowed final-ready fields."""
    plain(path)
    data = value if isinstance(value, str) else json.dumps(value, ensure_ascii=False, indent=2) + '\n'
    Path(path).parent.mkdir(parents=True, exist_ok=True)
    if replace:
        temporary = Path(str(path) + '.assembly-new')
        write(temporary, data)
        os.replace(temporary, path)
    else:
        with Path(path).open('x', encoding='utf-8', newline='\n') as stream:
            stream.write(data)
            stream.flush()
            os.fsync(stream.fileno())


def safe_name(name):
    value = PurePosixPath(name)
    require(name and str(value) == name and not value.is_absolute() and '\\' not in name and
            ':' not in name and all(part not in ('', '.', '..') for part in value.parts),
            'Unsafe archive/manifest name: ' + repr(name))
    return name


def files_below(folder):
    """Never enter a link, including a link in any ancestor of the root."""
    plain(folder)
    require(Path(folder).is_dir(), 'Missing directory: ' + str(folder))
    result = {}
    for root, dirs, files in os.walk(folder, followlinks=False):
        dirs.sort()
        files.sort()
        for name in (*dirs, *files):
            plain(Path(root) / name)
        for name in files:
            path = Path(root) / name
            require(path.is_file(), 'Not a regular file: ' + str(path))
            result[path.relative_to(folder).as_posix()] = path
    return result


def bound_tree(folder, expected, prefix):
    require(isinstance(expected, dict) and expected, 'Missing tree manifest: ' + str(folder))
    actual = files_below(folder)
    require(set(actual) == set(expected), 'Tree file inventory changed: ' + str(folder))
    return {safe_name(prefix + '/' + safe_name(name)): (actual[name], digest)
            for name, digest in expected.items()}


def verify_zip(path, expected=None):
    """Verify every entry's CRC and SHA; callers may also supply source digests."""
    result = {}
    with zipfile.ZipFile(path) as archive:
        for info in archive.infolist():
            name = safe_name(info.filename.rstrip('/') if info.is_dir() else info.filename)
            if info.is_dir():
                continue
            require(name not in result, 'Duplicate ZIP entry: ' + name)
            require(not stat.S_ISLNK(info.external_attr >> 16), 'ZIP symlink: ' + name)
            digest = hashlib.sha256()
            crc = size = 0
            with archive.open(info) as stream:
                for data in iter(lambda: stream.read(1024 * 1024), b''):
                    digest.update(data)
                    crc = zlib.crc32(data, crc)
                    size += len(data)
            require(size == info.file_size and crc & 0xffffffff == info.CRC,
                    'ZIP CRC/size mismatch: ' + name)
            result[name] = digest.hexdigest()
    if expected is not None:
        require(result == expected, 'ZIP entries differ from verified source files: ' + str(path))
    return result


def zip_files(target, entries):
    plain(target)
    require(not os.path.lexists(target), 'Output exists: ' + str(target))
    require(entries, 'Empty archive: ' + str(target))
    digests = {}
    with zipfile.ZipFile(target, 'x', zipfile.ZIP_DEFLATED, compresslevel=6, allowZip64=True) as archive:
        for name, (path, expected) in sorted(entries.items()):
            safe_name(name)
            plain(path)
            before = path.stat()
            digest = hashlib.sha256()
            info = zipfile.ZipInfo.from_file(path, arcname=name)
            info.compress_type = zipfile.ZIP_DEFLATED
            with path.open('rb') as source, archive.open(info, 'w', force_zip64=True) as output:
                for data in iter(lambda: source.read(1024 * 1024), b''):
                    digest.update(data)
                    output.write(data)
            actual = digest.hexdigest()
            after = path.stat()
            require((before.st_size, before.st_mtime_ns) == (after.st_size, after.st_mtime_ns),
                    'Input changed while packaging: ' + str(path))
            require(expected is None or actual == expected, 'Input digest changed: ' + str(path))
            digests[name] = actual
    verify_zip(target, digests)
    return ref(target)


def copy_exact(source, target, expected=None):
    plain(source)
    plain(target)
    require(not os.path.lexists(target), 'Output exists: ' + str(target))
    digest = expected or sha(source)
    with source.open('rb') as incoming, target.open('xb') as outgoing:
        shutil.copyfileobj(incoming, outgoing, 1024 * 1024)
        outgoing.flush()
        os.fsync(outgoing.fileno())
    require(sha(target) == digest and sha(source) == digest, 'Copy/source mismatch: ' + str(source))
    return ref(target)


def source_archive(path, runtime):
    meta_path = path.with_suffix('.json')
    meta = read(meta_path)
    require(meta.get('status') == SOURCE_STATUS and meta.get('productionSha256') == runtime,
            'Source package receipt does not bind the accepted production')
    require(meta.get('bytes') == path.stat().st_size and meta.get('sha256') == sha(path),
            'Source archive differs from its receipt')
    require(plain(Path(meta['archive'])) == plain(path), 'Source receipt names another archive')
    digests = verify_zip(path)
    with zipfile.ZipFile(path) as archive:
        manifest = json.loads(archive.read('SOURCE-MANIFEST.json'))
    require(manifest.get('status') == SOURCE_STATUS and manifest.get('productionSha256') == runtime,
            'Internal source manifest binds another production')
    wanted = {'source/' + safe_name(name): digest for name, digest in manifest['files'].items()}
    require({name: value for name, value in digests.items() if name.startswith('source/')} == wanted,
            'Source archive manifest does not cover all source entries')
    require(set(digests) == set(wanted) | {'SOURCE-MANIFEST.json', 'BUILD-RU.txt'} and
            meta.get('files') == len(wanted), 'Unexpected/missing source ZIP entries')
    return meta_path, digests


def collect_evidence(notes):
    entries = {}
    excluded = {'usercache.json', 'ops.json', 'whitelist.json', 'banned-players.json',
                'banned-ips.json', 'launcher_accounts.json', 'accounts.json'}

    def add(path, name, required=False):
        plain(path)
        if not path.is_file():
            require(not required, 'Missing evidence: ' + str(path))
            notes.append('Optional evidence absent: ' + str(path))
            return
        if not required and path.stat().st_size > SMALL_LIMIT:
            notes.append('Large optional evidence omitted: ' + str(path))
            return
        require(name not in entries, 'Evidence name collision: ' + name)
        entries[safe_name(name)] = (path, None)

    # Only root report text/JSON, never root data trees, archives or accounts.
    plain(T)
    for path in sorted(T.iterdir()):
        plain(path)
        if path.is_file() and path.suffix.lower() in ('.json', '.txt', '.md') and path.name.lower() not in excluded:
            add(path, 'reports/' + path.name)
    for name in ('REPORT-RU.txt', 'HANDOFF-RU.txt', 'final-ready.json', 'desktop-install.json'):
        require('reports/' + name in entries, 'Required final report missing: ' + name)
    addons = T / 'addons'
    if addons.is_dir():
        for path in sorted(addons.iterdir()):
            plain(path)
            if path.is_file() and path.suffix.lower() in ('.json', '.txt', '.md', '.csv') and path.name.lower() not in excluded:
                add(path, 'reports/addons/' + path.name)
    # Bounded evidence categories. No profiles except explicit screenshot folders.
    runs = T / 'runs'
    if runs.is_dir():
        for run in sorted(runs.iterdir()):
            plain(run)
            if not run.is_dir():
                continue
            for root, dirs, files in os.walk(run, followlinks=False):
                for name in (*dirs, *files):
                    plain(Path(root) / name)
                dirs[:] = sorted(name for name in dirs if name.lower() not in
                                 ('.git', 'accounts', 'config', 'mods', 'shaderpacks', 'assets',
                                  'libraries', 'natives', 'world', 'region', 'entities', 'poi'))
                for name in sorted(files):
                    path = Path(root) / name
                    if path.name.lower() in excluded or path.suffix.lower() not in ('.json', '.log', '.csv', '.tsv', '.txt', '.png', '.jpg', '.jpeg'):
                        continue
                    add(path, 'runs/' + run.name + '/' + path.relative_to(run).as_posix())
            if run.name in ('full-1', 'full-2', 'full-3', 'reopen-1') or run.name.startswith(('sun-', 'centre-')):
                screenshots = T / 'profiles' / run.name / 'client' / 'screenshots'
                if screenshots.is_dir():
                    for relative, path in files_below(screenshots).items():
                        if path.suffix.lower() in ('.png', '.jpg', '.jpeg'):
                            add(path, 'screenshots/' + run.name + '/' + relative)
    analysis = T / 'chunk-analysis'
    if analysis.is_dir():
        for child in sorted(analysis.iterdir()):
            plain(child)
            if child.is_dir():
                add(child / 'summary.json', 'reports/chunk-analysis/' + child.name + '/summary.json')
    for name in ('sample_host_resources.py', 'summarize.py', 'route.json', 'final-route.json'):
        add(O / name, 'portable-qa/' + name)
    for source, name in (
        (T / 'build/libs/dreamwalker-native-grid-qa-0.1.0-v12.7.4-visual-physics.1.jar', 'qa/native-functional-qa.jar'),
        (O / 'profiles/final/client/mods/dreamwalker-userpack-route-qa.jar', 'qa/frozen-route-recorder.jar'),
    ):
        add(source, name, required=True)
    require(sha(entries['qa/frozen-route-recorder.jar'][0]) == FROZEN_RECORDER_SHA,
            'Frozen route recorder differs')
    for source, name in (
        (W / 'DW/audits/Dreamwalker-BB/2026-10-21-native-grid-city/START-RU.txt', 'contracts/START-RU.txt'),
        (R / 'preserved-assemblies.json', 'contracts/preserved-assemblies.json'),
        (R / 'native-grid-plan/full/native-functions-final.json', 'contracts/native-functions-final.json'),
        (T / 'CURRENT-CONTRACT-RU.txt', 'contracts/CURRENT-CONTRACT-RU.txt'),
    ):
        add(source, name, required=True)
    return entries


DOWNLOAD_SCRIPT = '''"""Download only the exact public assets recorded in ASSETS.json; no extraction."""
import argparse,hashlib,json,os,urllib.request
from pathlib import Path
a=argparse.ArgumentParser();a.add_argument('--out',type=Path,required=True);a=a.parse_args()
manifest=json.loads(Path(__file__).with_name('ASSETS.json').read_text(encoding='utf-8'))
for p in (a.out,*a.out.parents):
 if p.exists():assert not p.is_symlink() and not getattr(p.lstat(),'st_file_attributes',0)&0x400
a.out.mkdir(parents=True,exist_ok=True)
for row in manifest['assets']:
 name=row['name'];assert Path(name).name==name and '/' not in name and '\\\\' not in name
 path=a.out/name
 def digest(p):
  h=hashlib.sha256()
  with p.open('rb') as f:
   for b in iter(lambda:f.read(1<<20),b''):h.update(b)
  return h.hexdigest()
 if path.exists():
  assert path.is_file() and path.stat().st_size==row['bytes'] and digest(path)==row['sha256'];continue
 temporary=a.out/(name+'.part')
 with urllib.request.urlopen('https://github.com/Stasik1960/Dreamwalker/releases/download/native-grid-city-visual-physics-20261025/'+name,timeout=120) as incoming,temporary.open('xb') as output:
  while True:
   data=incoming.read(1<<20)
   if not data:break
   output.write(data)
 assert temporary.stat().st_size==row['bytes'] and digest(temporary)==row['sha256']
 assert not path.exists();os.replace(temporary,path)
 print(name+' SHA256 verified')
'''


def repository_tree(source, manifest, evidence, evidence_entries, notes):
    repo = B / 'repo'
    repo.mkdir(exist_ok=False)
    write(repo / 'REPORT-RU.txt', (T / 'REPORT-RU.txt').read_text(encoding='utf-8-sig'))
    write(repo / 'HANDOFF-RU.txt', (T / 'HANDOFF-RU.txt').read_text(encoding='utf-8-sig'))
    write(repo / 'ASSETS.json', manifest)
    write(repo / 'SHA256SUMS.txt', ''.join(row['sha256'] + '  ' + row['name'] + '\n' for row in manifest['assets']))
    write(repo / 'download_release.py', DOWNLOAD_SCRIPT)
    write(repo / 'README.md', '# Dreamwalker — готовая карта visual/physics.1\n\n'
          'Принятая локальная пара, исходники и проверенные настройки; выпуск: ' + RELEASE + '.\n\n'
          'Используйте **одну активную копию** `bloodborne-native-grid-city` и единственный matching Dreamwalker JAR на клиенте и сервере. '
          'Из архива modpacks перенесите client_mods/server_mods по ролям, а config/shaderpacks из Dreamwalker-*-settings — рядом с mods, не внутрь mods. '
          'Пользовательские аккаунты/options/клавиши архивами не заменяются. Шейдер по умолчанию выключен.\n\n'
          'Все пять файлов Release обязательны для полного комплекта; `ASSETS.json` и `SHA256SUMS.txt` фиксируют байты. '
          '`python download_release.py --out downloads` скачивает/проверяет файлы без распаковки. '
          'Мир ZIP имеет ровно один корень `bloodborne-native-grid-city`; дополнительные старые миры в поставку не включены.\n\n'
          '**Полный source ZIP авторитетен**: содержит effective assets, каталог/meshes, Java, QA и инструменты. '
          '`source-code/` здесь — небольшой обзор исходников, без больших ресурсов, не самостоятельная полная сборочная копия. '
          'Java17/Gradle и инструкции нового ПК: [HANDOFF-RU.txt](HANDOFF-RU.txt). Результаты/ограничения: [REPORT-RU.txt](REPORT-RU.txt).\n\n'
          'v12, двери, данные и согласованные цельные объекты сохранены по приёмочным контрактам. '
          'Не повторять готовую конвертацию/свет и не открывать новую карту старым модом. '
          'Точный визуальный scope и нерешённые ограничения описаны в отчётах; FPS не гарантируется на ином железе.\n\n'
          'История прежнего формата: https://github.com/' + REPOSITORY + '/releases/tag/native-grid-city-full-20261024 . '
          'Это predecessor, не инструкция подменять текущую пару старой.\n')
    for directory in ('REPORTS', 'TASK', 'source-code'):
        (repo / directory).mkdir(exist_ok=True)
    for name in ('DECISIONS-RU.md', 'CHUNK-LOADING-ANALYSIS-RU.txt', 'CURRENT-RU.txt',
                 'main-ready.json', 'final-ready.json', 'desktop-install.json', 'comparison.json',
                 'server-checks.json', 'integration-checks.json', 'full-metrics.json', 'full-resources.json',
                 'full-visual-review-after-reopen.json', 'addons/selection.json', 'addons/full-metrics.json',
                 'addons/full-visual-review.json', 'addons/sun-review.json'):
        path = T / name
        if path.is_file() and path.stat().st_size <= SMALL_LIMIT:
            (repo / 'REPORTS' / name).parent.mkdir(parents=True, exist_ok=True)
            copy_exact(path, repo / 'REPORTS' / name)
    write(repo / 'REPORTS/ASSEMBLY-NOTES.json', {'omittedOptionalInputs': notes, 'evidence': evidence,
          'noGameOrGitOrNetworkWork': True, 'desktopNotModified': True})
    for name in ('START-RU.txt', 'preserved-assemblies.json', 'CURRENT-CONTRACT-RU.txt'):
        input_name = 'contracts/' + name
        require(input_name in evidence_entries, 'Contract missing: ' + name)
        copy_exact(evidence_entries[input_name][0], repo / 'TASK' / name)
    write(repo / 'TASK/CONTRACT-REFERENCES.json', {name: ref(path)
          for name, (path, _) in evidence_entries.items() if name.startswith('contracts/')})
    with zipfile.ZipFile(source) as archive:
        for info in archive.infolist():
            name = info.filename
            if not name.startswith('source/'):
                continue
            relative = name[len('source/'):]
            selected = ((relative.endswith('.java') and relative.startswith(('src/', 'tools/', 'validation/frozen-recorder/'))) or
                        relative.startswith('tools/native-visual/') or relative.startswith('gradle/wrapper/') or
                        relative in ('build.gradle', 'settings.gradle', 'gradle.properties', 'gradlew', 'gradlew.bat',
                                     'tools/static-city-20261017/production-source-excludes.txt'))
            if not selected:
                continue
            require(info.file_size <= SMALL_LIMIT, 'Unexpected large code-review file: ' + name)
            target = repo / 'source-code' / safe_name(relative)
            plain(target)
            target.parent.mkdir(parents=True, exist_ok=True)
            with archive.open(info) as incoming, target.open('xb') as output:
                shutil.copyfileobj(incoming, output, 1024 * 1024)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source-archive', type=Path, required=True)
    args = parser.parse_args()
    ready_path = T / 'final-ready.json'
    desktop_path = T / 'desktop-install.json'
    ready = read(ready_path)
    desktop = read(desktop_path)
    ready_before = sha(ready_path)
    desktop_before = sha(desktop_path)
    require(ready.get('status') == 'FINAL_PAIR_ACCEPTED_LOCAL_DELIVERY_STAGED' and
            desktop.get('status') == 'DESKTOP_PAIR_AND_MODPACKS_INSTALLED_ALL_FILES_VERIFIED',
            'Final acceptance and Desktop installation must already exist')
    jar = plain(Path(ready['jar']['path']))
    runtime = ready['jar']['sha256']
    require(sha(jar) == runtime == desktop.get('jarSha256'), 'Accepted/Desktop JAR mismatch')
    require(ready.get('canonicalWorldUntouched') is True and desktop.get('originalFoldersPreserved') is True,
            'Required source/backup preservation facts missing')
    require(ready['selection'].get('defaultShadersEnabled') is False, 'Delivery requires default shaders OFF')
    for name in ('REPORT-RU.txt', 'HANDOFF-RU.txt'):
        plain(T / name)
        require((T / name).is_file() and (T / name).stat().st_size, 'Final document missing: ' + name)
    source = plain(args.source_archive)
    source_receipt, source_digests = source_archive(source, runtime)
    world = plain(Path(ready['world']))
    require(world != (T / 'world').absolute() and not world.is_relative_to(B),
            'Use the accepted staged world, never canonical/output world')
    installed = desktop['installedManifests']
    require(installed['bloodborne-native-grid-city'] == ready['worldFiles'] and
            installed['Dreamwalker-client-settings'] == ready['clientSettingsFiles'] and
            installed['Dreamwalker-server-settings'] == ready['serverSettingsFiles'],
            'Desktop receipt differs from accepted world/settings manifests')
    world_entries = bound_tree(world, ready['worldFiles'], 'bloodborne-native-grid-city')
    mod_entries = {}
    for name in ('client_mods', 'server_mods', 'Dreamwalker-client-settings', 'Dreamwalker-server-settings'):
        folder = plain(Path(desktop['folders'][name]))
        require(folder == plain(D / name), 'Desktop receipt names another folder: ' + name)
        mod_entries.update(bound_tree(folder, installed[name], name))
    require(not any('sildur' in name.lower() and name.lower().endswith('.zip') for name in mod_entries),
            'Sildur ARR shader ZIP is not permitted in publication assets')
    forbidden = {'.git', 'accounts', 'launcher_accounts.json', 'accounts.json', 'usercache.json',
                 'ops.json', 'whitelist.json', 'banned-players.json', 'banned-ips.json'}
    require(not any(any(part.lower() in forbidden for part in PurePosixPath(name).parts) or
                    PurePosixPath(name).suffix.lower() in ('.args', '.jfr', '.hprof')
                    for name in mod_entries), 'Account/launcher/diagnostic data in installed delivery folders')
    for folder in ('client_mods', 'server_mods'):
        lock = read(D / folder / 'DREAMWALKER-MODPACK-LOCK.json')
        require(lock.get('dreamwalkerSha256') == runtime, 'Installed modpack lock names another runtime')
        own = [row for row in lock['files'].values() if row.get('id') == 'bloodborne_dw']
        require(len(own) == 1 and own[0]['sha256'] == runtime, 'Modpack has no unique matching Dreamwalker')
    plain(B)
    for target in (B / 'assets', B / 'repo', B / 'DELIVERY-MANIFEST.json', B / 'release-notes.md', T / 'READY.json'):
        plain(target)
        require(not os.path.lexists(target), 'Fresh output required: ' + str(target))
    (B / 'assets').mkdir(parents=True)
    assets = B / 'assets'
    rows = []
    for source_path, name, expected in ((jar, jar.name, runtime),
                                        (source, 'dreamwalker-visual-physics-sources.zip', sha(source))):
        result = copy_exact(source_path, assets / name, expected)
        rows.append({'name': name, 'bytes': result['bytes'], 'sha256': result['sha256']})
    verify_zip(assets / jar.name)
    require(verify_zip(assets / 'dreamwalker-visual-physics-sources.zip') == source_digests,
            'Copied source ZIP entry mismatch')
    for name, entries in (('bloodborne-native-grid-city.zip', world_entries),
                          ('user-modpacks-and-settings.zip', mod_entries)):
        result = zip_files(assets / name, entries)
        rows.append({'name': name, 'bytes': result['bytes'], 'sha256': result['sha256']})
    # Source/Desktop facts are now actually verified. No evidence/self-hash cycle.
    accepted = dict(ready)
    accepted['sourcePackageAndDesktopInstallPending'] = False
    accepted['sourcePackage'] = {**ref(assets / 'dreamwalker-visual-physics-sources.zip'),
                                 'productionSha256': runtime, 'packageReceipt': ref(source_receipt)}
    require(sha(ready_path) == ready_before and sha(desktop_path) == desktop_before,
            'Acceptance/Desktop receipt changed during packaging')
    write(ready_path, accepted, replace=True)
    notes = []
    evidence_entries = collect_evidence(notes)
    evidence_entries['source-package/receipt.json'] = (source_receipt, None)
    evidence = zip_files(assets / 'dreamwalker-visual-physics-evidence.zip', evidence_entries)
    rows.append({'name': Path(evidence['path']).name, 'bytes': evidence['bytes'], 'sha256': evidence['sha256']})
    require(len(rows) == 5 and all(row['bytes'] < 2 * 1024 ** 3 for row in rows),
            'Publisher requires exactly five assets, each below 2 GiB; no automatic splitting')
    manifest = {'status': 'VERIFIED_LOCAL_RELEASE_ASSETS', 'runtimeSha256': runtime,
                'acceptanceSha256': sha(ready_path), 'desktopReceiptSha256': sha(desktop_path),
                'assets': rows}
    repository_tree(assets / 'dreamwalker-visual-physics-sources.zip', manifest, evidence, evidence_entries, notes)
    write(B / 'release-notes.md', '# Dreamwalker visual/physics.1 — 25.10.2026\n\n'
          'Готовая принятая карта, matching клиентский/серверный мод, полные исходники и проверенный пользовательский modpack с настройками. '
          'Шейдер по умолчанию выключен; подробные реальные измерения и границы проверки — в REPORT-RU.txt.\n\n'
          'Одна активная карта `bloodborne-native-grid-city`. Сохранены v12, служебные данные и согласованные цельные конструкции. '
          'Не повторять преобразование готовой карты. Полный source ZIP — авторитетный сборочный комплект; source-code в Git — обзор без больших assets.\n\n'
          'Скачайте все пять assets; SHA256 — в ASSETS.json/SHA256SUMS.txt ветки `' + BRANCH + '`. '
          'Архив evidence содержит принятые receipts, исходные контракты и выбранные кадры/метрики; архивы не содержат аккаунтов/launcher tokens, старых миров или гигабайтных JFR.\n\n'
          'История: https://github.com/' + REPOSITORY + '/releases/tag/native-grid-city-full-20261024 .\n')
    write(B / 'DELIVERY-MANIFEST.json', manifest)
    write(T / 'READY.json', {'status': 'READY_LOCAL_DELIVERY', 'runtimeSha256': runtime,
          'source': accepted['sourcePackage'], 'evidence': evidence,
          'localRefs': {'acceptance': ref(ready_path), 'desktop': ref(desktop_path),
                        'manifest': ref(B / 'DELIVERY-MANIFEST.json'), 'report': ref(T / 'REPORT-RU.txt'),
                        'handoff': ref(T / 'HANDOFF-RU.txt'), 'repository': str(B / 'repo')},
          'tag': TAG, 'branch': BRANCH, 'repository': REPOSITORY,
          'utc': datetime.datetime.now(datetime.timezone.utc).isoformat(),
          'publicationNotPerformed': True})
    print(json.dumps({'status': 'READY_LOCAL_DELIVERY', 'runtimeSha256': runtime,
                      'manifest': ref(B / 'DELIVERY-MANIFEST.json'), 'assets': len(rows)}, ensure_ascii=False))


if __name__ == '__main__':
    main()
