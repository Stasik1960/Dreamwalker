"""Release only the new gallery after actual Minecraft reopen and save checks."""
from __future__ import annotations
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import stat
import zipfile

ROOT = Path(__file__).resolve().parents[1]
REPO = ROOT.parent


def read(path):
    return json.loads(Path(path).read_text(encoding='utf-8'))


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def dump(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')


def ordinary(path):
    info = path.lstat()
    assert not stat.S_ISLNK(info.st_mode) and not getattr(info, 'st_file_attributes', 0) & 0x400, path


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--world', type=Path, required=True)
    parser.add_argument('--production-jar', type=Path, required=True)
    parser.add_argument('--server-report', type=Path, required=True)
    parser.add_argument('--disk-server-report', type=Path, required=True)
    parser.add_argument('--disk-client-report', type=Path, required=True)
    parser.add_argument('--capture-report', type=Path, required=True)
    parser.add_argument('--client-report', type=Path, required=True)
    parser.add_argument('--visual-report', type=Path, required=True)
    args = parser.parse_args()
    world = args.world.resolve()
    assert world.is_relative_to((ROOT/'build').resolve()) and (world/'level.dat').is_file()
    manifest = read(world/'gallery-manifest.json')
    digest = sha(args.production_jar)
    assert digest == manifest['productionJarSha256']
    server = read(args.server_report)
    assert server['status'] == 'PASS' and server['exit_code'] == 0 and not server['errors']
    assert server['artifact_sha256'] == digest and not server.get('extraMods')
    disk_server, disk_client = read(args.disk_server_report), read(args.disk_client_report)
    for disk in (disk_server, disk_client):
        assert disk['status'] == 'PASS' and disk['productionReopenCompared']
        assert disk['productionJarSha256'] == digest and disk['verifiedExhibits'] == 1885
        assert disk['verifiedDiagnostics'] == 57 and disk['verifiedRussianIdLabels'] == 1942
    assert disk_server['world'] == str(world)
    capture, visual = read(args.capture_report), read(args.visual_report)
    assert capture['status'] == 'CAPTURE_COMPLETE' and capture['captureComplete']
    assert capture['productionJarSha256'] == digest and capture['capturedFrames'] == 24
    assert len(capture['frames']) == 24 and all(row['status'] == 'PASS' for row in capture['frames'])
    client = read(args.client_report)
    assert client['status'] == 'PASS'
    assert visual['status'] == 'PASS' and visual['productionJarSha256'] == digest
    assert visual['captureSha256'] == sha(args.capture_report) and visual['reviewedFrames'] == 24
    assert visual['reviewedFilenames'] == [row['fileName'] for row in capture['frames']]
    release = REPO/'releases/Dreamwalker-BB/v12.1-flat-gallery'
    release.mkdir(parents=True, exist_ok=True)
    target = release/'Dreamwalker-BB-V12.1-Flat-Gallery.zip'
    assert not target.exists(), 'Do not replace a released gallery silently'
    entries = []
    for path in sorted(world.rglob('*')):
        ordinary(path)
        if path.is_file() and path.name != 'session.lock':
            relative = path.relative_to(world).as_posix()
            entries.append({'path': relative, 'bytes': path.stat().st_size, 'sha256': sha(path)})
    with zipfile.ZipFile(target, 'w', compression=zipfile.ZIP_DEFLATED, compresslevel=6) as archive:
        for entry in entries:
            archive.write(world/entry['path'], 'Dreamwalker-BB-V12.1-Flat-Gallery/'+entry['path'])
    with zipfile.ZipFile(target) as archive:
        assert archive.testzip() is None
        assert len(archive.namelist()) == len(entries)
        for entry in entries:
            payload = archive.read('Dreamwalker-BB-V12.1-Flat-Gallery/'+entry['path'])
            assert len(payload) == entry['bytes'] and hashlib.sha256(payload).hexdigest() == entry['sha256']
    jar_target = release/args.production_jar.name
    shutil.copyfile(args.production_jar, jar_target)
    assert sha(jar_target) == digest
    for filename in ('GALLERY-INDEX.csv', 'README-RU.txt'):
        shutil.copyfile(world/filename, release/filename)
    evidence = release/'verification'
    evidence.mkdir(exist_ok=True)
    for name, disk in (('disk-server-after.json', disk_server), ('disk-client-after.json', disk_client)):
        compact = {key: value for key, value in disk.items() if key not in ('stableExhibits', 'diagnostics')}
        compact['fullReportTypedExhibitsSha256'] = hashlib.sha256(json.dumps(disk['stableExhibits'],sort_keys=True,ensure_ascii=False,separators=(',',':')).encode()).hexdigest()
        dump(evidence/name, compact)
    compact_server = {key: value for key, value in server.items() if key not in ('libraries', 'command', 'derived_world_copy')}
    compact_server['derivedWorldCopyByteVerification'] = server['derived_world_copy']['copy_byte_verification']
    dump(evidence/'server-reopen.json',compact_server)
    shutil.copyfile(args.capture_report,evidence/'client-capture.json')
    shutil.copyfile(args.client_report,evidence/'client-runtime.json')
    shutil.copyfile(args.visual_report,evidence/'visual-review.json')
    layout = ROOT/'docs/gallery-v12.1/layout-review.json'
    if layout.is_file():
        assert read(layout)['status'] == 'PASS'
        shutil.copyfile(layout,evidence/layout.name)
    catalogue_review = ROOT/'docs/gallery-v12.1/catalogue-final-review.json'
    assert read(catalogue_review)['status'] == 'PASS'
    shutil.copyfile(catalogue_review,evidence/catalogue_review.name)
    screenshots = evidence/'screenshots'
    screenshots.mkdir(exist_ok=True)
    for row in capture['frames']:
        source = args.capture_report.resolve().parent/'screenshots'/row['fileName']
        assert sha(source) == row['sha256']
        shutil.copyfile(source,screenshots/row['fileName'])
    original = REPO/'Bloodborne-Blocks/reference-inputs/bbmc-v15-v16'
    source_hashes = {name:sha(original/name) for name in ('bbmc_v15_resource.zip','bbmc_v16_map.zip')}
    assert source_hashes == {
        'bbmc_v15_resource.zip':'29b31e3744af5794ae95ebe4c5474e3b70dfb08c143355bce815c08ceef09a36',
        'bbmc_v16_map.zip':'39e83f2941b768fd2ae9f84d8f57dc76aec78bd926dc5e38eab3059251d2bb08'}
    source_zip = REPO/'releases/Dreamwalker-BB/v12.1-resource-transfer/dreamwalker-bb-fabric-1.20.1-0.1.0-v12.1-resource-transfer-full-source.zip'
    copied_source = release/source_zip.name
    shutil.copyfile(source_zip,copied_source)
    assert sha(copied_source) == sha(source_zip)
    generator_zip = release/'Dreamwalker-BB-V12.1-Flat-Gallery-generator-source.zip'
    generator_files = [
        'src/gallery/java/dev/dreamwalker/bloodbornedw/gallery/FlatGalleryAuthor.java',
        'src/gallery/java/dev/dreamwalker/bloodbornedw/gallery/FlatGalleryClientCapture.java',
        'tools/build_flat_gallery_addon.py','tools/prepare_gallery_catalogue.py',
        'tools/prepare_flat_gallery_input.py','tools/finalize_flat_gallery_world.py',
        'tools/gallery_platform_plan.py','tools/validate_gallery_platform_plan.py',
        'tools/review_flat_gallery_layout.py','tools/verify_flat_gallery.py',
        'tools/prepare_flat_gallery_capture.py','tools/collect_flat_gallery_client.py',
        'tools/package_flat_gallery.py','tools/run_v11_server.py','tools/run_v11_client.py',
        'tools/world_io.py','tools/audit_map_pack_absence.py',
        'tools/scan_original_map_states.py','tools/scan_original_map_models.py',
        'docs/gallery-platform-envelopes.json','docs/gallery-platform-validation.json']
    generator_files += [path.relative_to(ROOT).as_posix() for directory in ('docs/gallery-v12.1','docs/map-pack-absence') for path in sorted((ROOT/directory).glob('*')) if path.is_file()]
    with zipfile.ZipFile(generator_zip,'w',compression=zipfile.ZIP_DEFLATED,compresslevel=6) as archive:
        for relative in sorted(set(generator_files)):
            archive.write(ROOT/relative,relative)
    with zipfile.ZipFile(generator_zip) as archive:
        assert archive.testzip() is None
    result = {'status':'PASS','worldZip':str(target),'worldZipSha256':sha(target),
              'worldFiles':len(entries),'worldArchiveCrcAndEveryFileHash':'PASS',
              'worldEntries':entries,'productionJar':str(jar_target),'productionJarSha256':digest,
              'productionFullSource':str(copied_source),'productionFullSourceSha256':sha(copied_source),
              'galleryGeneratorSource':str(generator_zip),'galleryGeneratorSourceSha256':sha(generator_zip),
              'originalInputsUnchanged':source_hashes,'qaAddonRequired':False,
              'platforms':1942,'realExhibits':1885,'diagnostics':57,
              'sectionCounts':disk_server['sectionCounts'],'serverReopen':'PASS',
              'clientOpenAndSave':'PASS','allSavedObjectsBeforeAfter':'PASS',
              'visualReview':'PASS','physicalMouseKeyboard':'NOT_RUN'}
    dump(release/'release-verification.json',result)
    report = (
        'Dreamwalker BB V12.1 — новая плоская галерея\n\n'
        'Готовый мир: Dreamwalker-BB-V12.1-Flat-Gallery.zip. Распаковать одну папку из архива в saves.\n'
        'Minecraft 1.20.1, Java 17, Fabric Loader 0.16.10, Fabric API 0.92.9+1.20.1, GeckoLib 4.4.9.\n'
        'Combined JAR приложен: тот же проверенный v12.1-resource-transfer; для галереи он не изменялся. Исходный ресурспак и QA-дополнение не требуются.\n\n'
        'Полные исходники мода — архив full-source.zip; новые инструменты галереи и анализа — архив Flat-Gallery-generator-source.zip.\n\n'
        '1942 отдельные платформы: 1885 настоящих объектов/предлагаемых вариантов и 57 подписанных диагностических платформ.\n'
        'Голубые — исходная V12: 80. Зелёные — новые: 1682. Жёлтые — отсутствующие на карте: 120.\n'
        'Красные — ошибки: 60 (3 реальных варианта с исходным отсутствующим particle-ресурсом и 57 диагностик).\n'
        'Группы взаимоисключающие с приоритетом ошибка > отсутствие на карте > V12 > новые; происхождение сохранено в manifest.\n'
        'Сетка 16 колонок, 123 ряда, область 318×1207 блоков. Между краями соседних платформ ровно два пустых блока.\n'
        'У каждого настоящего объекта одна платформа; цельные составные модели сохранены вместе. BASE/закрытое состояние показано сначала.\n'
        'Полные русские названия, ID, варианты, координаты и команды перехода — GALLERY-INDEX.csv. Таблички читаются с обеих сторон.\n'
        '/function dw_gallery:help — помощь; dw_gallery:v12, dw_gallery:new, dw_gallery:absent, dw_gallery:error — переходы; dw_gallery:tool — BB-инструмент.\n\n'
        'PASS: реальный dedicated server открыл и штатно сохранил мир с production JAR без авторского QA-дополнения.\n'
        'PASS: настоящий клиент Minecraft открыл независимую копию, проверил состояния/UUID объектов в четырёх секциях, сохранил 24 снимка собственного кадра и штатно сохранил мир.\n'
        'PASS: после сервера и клиента все 1885 объектов, UUID, состояния, NBT владельцев/занимаемых ячеек совпали с исходным сохранением.\n'
        'PASS: прочитаны все 1942 подписанные платформы, 57 диагностических маркеров и расстояния сетки; проверены габариты и кадры.\n'
        'PASS: CRC архива и SHA-256 каждого файла в архиве совпадают с проверенной серверной копией.\n'
        'NOT_RUN: полный ручной цикл мышью/клавиатурой установки, взаимодействий и удаления для каждого экспоната; эта проверка открытия галереи его не заменяет.\n'
        'Исходные дефекты не исправлялись: без авторской модели соответствующий подиум содержит красный стеклянный маркер и объяснение, а не выдуманный объект.\n'
        'На этом этапе добавлены файлы генератора/проверок и отдельный мир; исходные v15/v16, существующие ID, модели и физика production JAR сохранены. Авторские коллизии без экспорта не применялись.\n\n'
        'Combined JAR SHA-256: '+digest+'\n'
        'Мир ZIP SHA-256: '+sha(target)+'\n'
        'Подробные проверки — verification/ и release-verification.json.\n')
    (release/'REPORT-RU.txt').write_text(report,encoding='utf-8')
    print(json.dumps({key:result[key] for key in ('status','worldZip','worldZipSha256','worldFiles','productionJar')},ensure_ascii=False))


if __name__ == '__main__':
    main()
