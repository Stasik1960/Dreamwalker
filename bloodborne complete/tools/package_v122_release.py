"""Package the exact mod and new gallery only after genuine runtime checks.

Run through Guard.ps1 -Mode Operation. Historical releases and source inputs
are never overwritten. Every archived world/source file is byte-verified.
"""
from __future__ import annotations
import argparse
import csv
import hashlib
import json
from pathlib import Path
import shutil
import stat
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[1]
REPO = ROOT.parent
VERSION = '0.1.0-v12.2-simple-collisions'


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


def csv_rows(path):
    with path.open(encoding='utf-8-sig', newline='') as stream:
        return list(csv.DictReader(stream))


def archive_checked(target, files, prefix):
    entries = []
    for path, relative in files:
        ordinary(path)
        entries.append({'path': prefix + relative, 'bytes': path.stat().st_size, 'sha256': sha(path)})
    with zipfile.ZipFile(target, 'w', compression=zipfile.ZIP_DEFLATED, compresslevel=6) as archive:
        for (path, _), entry in zip(files, entries):
            archive.write(path, entry['path'])
    with zipfile.ZipFile(target) as archive:
        assert archive.testzip() is None and len(archive.namelist()) == len(entries)
        for entry in entries:
            content = archive.read(entry['path'])
            assert len(content) == entry['bytes'] and hashlib.sha256(content).hexdigest() == entry['sha256']
    return entries


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--world', type=Path, required=True)
    parser.add_argument('--jar', type=Path, required=True)
    for name in ('server', 'disk-server', 'disk-client', 'capture', 'client', 'visual', 'physics', 'migration', 'preservation', 'resources', 'build'):
        parser.add_argument('--'+name+'-report', type=Path, required=True)
    parser.add_argument('--gametest-xml', type=Path, required=True)
    args = parser.parse_args()
    world = args.world.resolve()
    assert world.is_relative_to((ROOT/'build').resolve()) and (world/'level.dat').is_file()
    manifest = read(world/'gallery-manifest.json'); digest = sha(args.jar)
    assert manifest['schema'] == 'dreamwalker-flat-gallery-v122' and manifest['productionJarSha256'] == digest
    assert not any(r['section'] == 'error' or r['kind'] == 'diagnostic' for r in manifest['exhibits'])
    building = [r for r in manifest['exhibits'] if r['kind'] != 'item_frame']
    frames = [r for r in manifest['exhibits'] if r['kind'] == 'item_frame']
    assert len(building) == 1882 and len(frames) == manifest['framedCreativeItems'] == 23
    assert manifest['offeredCreativeStacks'] == manifest['cataloguePlaced'] == len(manifest['exhibits']) == 1905
    reports = {}
    for name in ('server', 'disk_server', 'disk_client', 'capture', 'client', 'visual', 'physics', 'migration', 'preservation', 'resources', 'build'):
        path = getattr(args, name+'_report')
        report = read(path)
        assert report['status'] == ('CAPTURE_COMPLETE' if name == 'capture' else 'PASS'), (name, report.get('failures', report.get('errors')))
        reports[name] = report
        declared = [report[key] for key in ('productionJarSha256','jarSha256','artifact_sha256') if key in report]
        assert declared and all(value == digest for value in declared), (name, 'Missing or stale exact JAR binding', declared, digest)
    server = reports['server']
    assert server['exit_code'] == 0 and not server['errors'] and not server.get('extraMods')
    assert reports['disk_server']['world'] == str(world)
    manifest_digest = sha(world/'gallery-manifest.json')
    for name in ('disk_server', 'disk_client'):
        disk = reports[name]
        assert disk['schema'] == 'dreamwalker-flat-gallery-v122-disk-verification' and disk['manifestSha256'] == manifest_digest
        assert disk['productionReopenCompared'] and disk['verifiedExhibits'] == len(manifest['exhibits'])
        assert disk['verifiedDiagnostics'] == 0 and disk['savedVisualSupport']['status'] == 'PASS'
        assert disk['savedVisualSupport']['architectureModelAndSavedMounts'] == 1813 and disk['savedVisualSupport']['rpModelAndSavedPoses'] == 69 and disk['savedVisualSupport']['attachedFramedItems'] == 23
        assert disk['savedVisualSupport']['savedRussianIdLabelsBothSides'] == 1905
    capture, visual = reports['capture'], reports['visual']
    assert capture['captureComplete'] and capture['capturedFrames'] == capture['expectedFrames'] == len(capture['frames']) == 37
    assert all(r['status'] == 'PASS' for r in capture['frames'])
    assert visual['captureSha256'] == sha(args.capture_report)
    assert visual['reviewedFilenames'] == [r['fileName'] for r in capture['frames']]
    client = reports['client']
    assert client['schema'] == 'dreamwalker-flat-gallery-v122-native-client-verification'
    assert client['evidence']['nativeCapture']['sha256'] == sha(args.capture_report) and client['evidence']['savedNbtAfterClient']['sha256'] == sha(args.disk_client_report)
    profile_path = Path(client['evidence']['clientProfile']['path'])
    assert sha(profile_path) == client['evidence']['clientProfile']['sha256']
    profile = read(profile_path)
    assert profile['artifact_sha256'] == digest and Path(profile['derived_world_copy']['source']).resolve() == world
    capture_input_path = Path(profile['run_directory'])/'client-capture-input.json'
    capture_input = read(capture_input_path)
    assert sha(capture_input_path) == capture['inputSha256'] and capture_input['manifestSha256'] == manifest_digest and capture_input['productionJarSha256'] == digest
    assert len(capture_input['frames']) == 37
    assert all(all(actual[key] == value for key, value in expected.items()) for expected, actual in zip(capture_input['frames'], capture['frames'])), 'Native capture differs from the bound reviewed sample plan'
    mandatory_keys = {'bloodborne_dw:source_addon_bag_2[variant=0]', 'bloodborne_dw:source_stairs_exposed_cut_copper_bottom_outer1[variant=0]',
        'bloodborne_dw:source_addon_floor_dec_0[variant=0]', 'bloodborne_dw:source_addon_button_10[variant=0]', 'bloodborne_dw:source_addon_chains_0[variant=0]',
        'framed:bloodborne_dw:bb_tool', 'framed:bloodborne_rp:boom_hammer', 'framed:bloodborne_rp:cleric_beast_spawn_egg'}
    assert mandatory_keys <= {row['key'] for row in capture['frames']}, 'Mandatory repaired-label/planar/framed samples missing'
    assert Path(reports['disk_client']['world']).resolve() == (Path(profile['run_directory'])/'saves/prototype-fixture').resolve()
    assert profile['derived_world_copy']['copy_byte_verification'] == 'PASS'
    assert all(sha(world/row['path']) == row['sha256'] for row in profile['derived_world_copy']['files']), 'Packaged source gallery changed after actual client validation'
    current_world_files = {path.relative_to(world).as_posix() for path in world.rglob('*') if path.is_file() and path.name != 'session.lock'}
    assert current_world_files == {row['path'] for row in profile['derived_world_copy']['files']}, 'World file inventory changed after actual client validation'
    xml = ET.parse(args.gametest_xml).getroot()
    assert not xml.findall('.//failure') and not xml.findall('.//error')
    assert sha(args.gametest_xml) == reports['build']['gameTestXmlSha256'] and len(xml.findall('.//testcase')) == reports['build']['gameTestCount'] == 63
    supplemental = [ROOT/'docs/v12.2-simplified-collisions'/name for name in
        ('baseline.json', 'retirement.json', 'new-selector-registry.csv', 'new-exact-selectors.jsonl.gz', 'correspondence-v122.csv', 'collision-policy.csv', 'boundary-math.json')]
    supplemental += [ROOT/'docs/collision-simplification'/name for name in
        ('policy-report.json', 'functional-defaults-check.json', 'operation-status.json', 'final-policy-integrity.json')]
    for path in supplemental:
        assert path.is_file(), ('Missing required release evidence', path)
        ordinary(path)
    policy = read(ROOT/'docs/collision-simplification/final-policy-integrity.json')
    assert policy['status'] == 'PASS' and policy['jarSha256'] == policy['expectedJarSha256'] == digest
    assert policy['importedObjects'] == 1045 and policy['variants'] == 1802 and policy['poses'] == 1803 and not policy['failuresCount']
    boundary_path = ROOT/'docs/v12.2-simplified-collisions/boundary-math.json'
    boundary = read(boundary_path)
    assert boundary['status'] == 'PASS' and sha(boundary_path) == policy['strictBoundaryEvidence']['reportSha256']
    assert all(sha(ROOT/relative) == source_digest for relative, source_digest in boundary['sources'].items()), 'Boundary evidence source changed'
    preservation = reports['preservation']
    selectors = preservation['futureAuthorCompatibility']
    assert selectors['allRegistryIds'] == 1045 and selectors['allExactStates'] == 57664 and not selectors['runtimeReplacementApplied']
    assert sha(ROOT/'docs/v12.2-simplified-collisions'/selectors['file']) == selectors['fileSha256']
    registry_rows = csv_rows(ROOT/'docs/v12.2-simplified-collisions/new-selector-registry.csv')
    assert len(registry_rows) == 1045 and sum(int(row['variants']) for row in registry_rows) == 1802 and sum(int(row['exactSelectors']) for row in registry_rows) == 57664
    assert len({row['registryId'] for row in registry_rows}) == 1045
    correspondence = csv_rows(ROOT/'docs/v12.2-simplified-collisions/correspondence-v122.csv')
    physical_policy = csv_rows(ROOT/'docs/v12.2-simplified-collisions/collision-policy.csv')
    assert correspondence and len(physical_policy) == 1803
    assert all(row['v122ArtifactSha256'] == digest for row in correspondence + physical_policy), 'Transfer/policy table belongs to another JAR'
    assert {row['registryId'] for row in physical_policy} == {row['registryId'] for row in registry_rows}
    screenshot_files = []
    for row in capture['frames']:
        path = args.capture_report.resolve().parent/'screenshots'/row['fileName']
        ordinary(path)
        assert sha(path) == row['sha256']
        screenshot_files.append(path)
    original = REPO/'Bloodborne-Blocks/reference-inputs/bbmc-v15-v16'
    source_hashes = {name:sha(original/name) for name in ('bbmc_v15_resource.zip','bbmc_v16_map.zip')}
    assert source_hashes == {'bbmc_v15_resource.zip':'29b31e3744af5794ae95ebe4c5474e3b70dfb08c143355bce815c08ceef09a36', 'bbmc_v16_map.zip':'39e83f2941b768fd2ae9f84d8f57dc76aec78bd926dc5e38eab3059251d2bb08'}
    release = REPO/'releases/Dreamwalker-BB/v12.2-simple-collisions'
    assert not release.exists(), 'Use a fresh release; never overwrite a published result'
    release.mkdir(parents=True)
    shipped = release/args.jar.name
    shutil.copyfile(args.jar, shipped); assert sha(shipped) == digest
    evidence = release/'verification'; evidence.mkdir()
    for name, report in reports.items():
        compact = {key:value for key,value in report.items() if key not in ('stableExhibits','diagnostics','libraries','command','derived_world_copy','changedResourceEntries')}
        if 'stableExhibits' in report:
            compact['typedExhibitSnapshotSha256'] = hashlib.sha256(json.dumps(report['stableExhibits'], sort_keys=True, ensure_ascii=False, separators=(',',':')).encode()).hexdigest()
        if 'derived_world_copy' in report:
            compact['derivedWorldCopyByteVerification'] = report['derived_world_copy']['copy_byte_verification']
        dump(evidence/(name+'.json'), compact)
    shutil.copyfile(args.gametest_xml, evidence/'gametest.xml')
    shutil.copyfile(capture_input_path, evidence/'native-capture-input.json')
    for path in supplemental:
        shutil.copyfile(path, evidence/path.name)
    shots = evidence/'screenshots'; shots.mkdir()
    for path in screenshot_files:
        shutil.copyfile(path, shots/path.name)
    for filename in ('GALLERY-INDEX.csv','README-RU.txt'):
        shutil.copyfile(world/filename, release/filename)
    zip_path = release/'Dreamwalker-BB-V12.2-Flat-Gallery.zip'
    world_files = []
    for path in sorted(world.rglob('*')):
        ordinary(path)
        if path.is_file() and path.name != 'session.lock': world_files.append((path,path.relative_to(world).as_posix()))
    world_entries = archive_checked(zip_path, world_files, 'Dreamwalker-BB-V12.2-Flat-Gallery/')
    result = {'status':'PASS', 'version':VERSION, 'productionJarSha256':digest,
        'worldZip':str(zip_path), 'worldZipSha256':sha(zip_path), 'worldArchiveCrcAndEveryFileHash':'PASS',
        'worldFiles':len(world_entries), 'worldEntries':world_entries, 'buildingVariants':len(building),
        'framedItems':len(frames), 'platforms':len(manifest['exhibits']), 'sectionCounts':reports['disk_server']['sectionCounts'],
        'serverReopen':'PASS', 'clientOpenAndSave':'PASS', 'typedSavedObjectsBeforeAfter':'PASS', 'visualReview':'PASS',
        'simplifiedPhysics':'PASS', 'oldNewObjectsMigration':'PASS', 'protectedV12Preservation':'PASS',
        'gameTests':len(xml.findall('.//testcase')), 'nativeMovementFixtures':len(reports['physics']['movementAndTransactions']),
        'nativeVisualFramesReviewed':len(capture['frames']), 'originalInputsUnchanged':source_hashes,
        'retiredReservedIds':['93148','93149'], 'authorCollisionExport':'NOT_SUPPLIED_NOT_APPLIED',
        'completeManualMouseKeyboardCycleEveryVariant':'NOT_RUN', 'qaAddonRequired':False}
    dump(release/'release-verification.json', result)
    report_text = ('Dreamwalker BB V12.2 — упрощённые коллизии новых блоков и новая галерея\n\n'
        'Minecraft 1.20.1, Java 17, Fabric Loader 0.16.10; Fabric API 0.92.9+1.20.1, GeckoLib 4.4.9.\n'
        'Новый combined JAR заменяет предыдущий BB JAR; исходный ресурспак и QA-дополнения не нужны.\n'
        'Полные актуальные исходники — новый full-source.zip. Мир распаковать одной папкой в saves.\n\n'
        'Упрощены физические формы 1045 новых типов: по умолчанию один прямоугольный объём; до трёх коробок только для функциональных проходов, дверей/лестниц и опор. Проходимый декор сохранён.\n'
        'Выступы менее 0.5 блока обрезаются отдельно по каждой из шести граней исходной клетки после поворота и смещений; большие многоклеточные объекты сохраняют размер.\n'
        'Геометрия, UV, текстуры, выделение, варианты и номера сохранившихся объектов не изменены. Все объекты и предметы исходной V12 сохранены; изменения общих методов включаются только для новых source_ объектов.\n'
        'При загрузке уже установленных новых объектов старые физические формы в NBT обновляются атомарно, с сохранением UUID, состояния и смещений.\n'
        'Удалены только повреждённые новые типы 93148/93149 — три варианта. Номера зарезервированы навсегда; 57 прежних красных диагностик не были блоками мода.\n\n'
        f'Новая галерея: {len(building)} строительных вариантов, {len(frames)} предметов в рамках, {len(manifest["exhibits"])} платформ. Секции: '+json.dumps(result['sectionCounts'],ensure_ascii=False)+'\n'
        'Платформы: ceil(видимая ширина/длина модели)+2 клетки, сетки по размерам до 16 колонок, между краями 2 клетки. Нижняя видимая грань стоит на поверхности. Только плоские модели нулевой толщины подняты на 1/1024 клетки против мерцания.\n'
        'Навигация /function dw_gallery:help; полный список и команды перехода — GALLERY-INDEX.csv.\n\n'
        f'PASS: combined build, {result["gameTests"]}/{result["gameTests"]} GameTests, зависимости, сохранность V12/визуальных данных, все 57 664 состояния физических форм и {result["nativeMovementFixtures"]} выборочных игровых сценариев в отдельном серверном мире.\n'
        'PASS: миграция сохранённых новых объектов, production-сервер открыл новую галерею и сохранил её; настоящий клиент открыл её, снял кадры и штатно сохранил мир.\n'
        f'PASS: все объекты, UUID, состояния и типизированные данные сохранений сверены до/после; площадки и опора проверены, просмотрено {result["nativeVisualFramesReviewed"]} настоящих клиентских кадров. Архив мира совпадает побайтно с проверенной серверной копией.\n'
        'NOT_RUN: ручной полный цикл мышью/клавиатурой для каждого варианта.\n'
        'Авторский экспорт коллизий отсутствует: авторские коллизии не применялись. Подготовлены точные ID/селекторы, существующий контракт сохранён; фактическая встройка будет отдельным этапом после экспорта.\n'
        'Исходная карта v16, ресурспак v15 и прежняя галерея сохранены без изменений.\n\n'
        'JAR SHA-256: '+digest+'\nМир ZIP SHA-256: '+sha(zip_path)+'\nПодробные проверки — verification/ и release-verification.json.\n')
    (release/'REPORT-RU.txt').write_text(report_text, encoding='utf-8')
    source_files = []
    for folder in ('src','tools','docs','gradle'):
        for path in sorted((ROOT/folder).rglob('*')):
            ordinary(path)
            if path.is_file() and '__pycache__' not in path.parts: source_files.append((path,path.relative_to(ROOT).as_posix()))
    for name in ('build.gradle','settings.gradle','gradle.properties','gradlew','gradlew.bat','.gitignore','README.md','README.txt','RELEASE-STATUS.md'):
        path=ROOT/name
        if path.is_file(): source_files.append((path,name))
    source_zip = release/f'dreamwalker-bb-fabric-1.20.1-{VERSION}-full-source.zip'
    source_entries = archive_checked(source_zip, source_files, 'bloodborne complete/')
    result.update(productionFullSource=str(source_zip),productionFullSourceSha256=sha(source_zip),completeSourceFiles=len(source_entries),sourceArchiveEveryFileHash='PASS')
    dump(release/'release-verification.json', result)
    dump(release/'SHA256.json',{'version':VERSION,'files':[{'name':p.name,'bytes':p.stat().st_size,'sha256':sha(p)} for p in sorted(release.iterdir()) if p.is_file()]})
    print(json.dumps({k:result[k] for k in ('status','worldZip','productionJarSha256','buildingVariants','framedItems')},ensure_ascii=False))


if __name__ == '__main__':
    main()
