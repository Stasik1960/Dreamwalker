"""Assemble the verified local city/artist deliverables; never change world blocks."""
from __future__ import annotations

import argparse
import hashlib
import json
import re
import shutil
import zipfile
from pathlib import Path, PurePosixPath, PureWindowsPath

ROOT = Path(__file__).resolve().parents[1]
WORKSPACE = ROOT.parent
BUILD = ROOT / 'build/autumn-launch'
OUTPUT = WORKSPACE / 'output/Bloodborne-Launch-Base-Compact'
PRESENTATION = WORKSPACE / 'releases/Bloodborne-Blocks/launch-base-2026-10-01/Autumn-Variants.pptx'
GALLERY = BUILD / 'Gallery-Compact-Final'
RESOURCES = ROOT / 'src/main/resources'


def read_json(path):
    return json.loads(path.read_bytes())


def sha(path):
    digest = hashlib.sha256()
    with path.open('rb') as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b''):
            digest.update(block)
    return digest.hexdigest()


def gallery_navigation(manifest):
    navigation = manifest['navigation']
    if type(navigation.get('spacing')) is not int or navigation['spacing'] != 3:
        raise ValueError('gallery must have three-block gaps')
    if type(navigation.get('columns')) is not int or navigation['columns'] < 1:
        raise ValueError('invalid gallery columns')
    if not isinstance(navigation.get('firstPod'), str) or not re.fullmatch(r'/tp @s -?\d+ -?\d+ -?\d+', navigation['firstPod']):
        raise ValueError('invalid gallery entry command')
    rows, columns = manifest.get('specimens', []), navigation['columns']
    if not rows:
        raise ValueError('empty gallery')
    for row in rows:
        bounds = row.get('pad_bounds', [])
        if len(bounds) != 6 or any(type(value) is not int for value in bounds) or bounds[0] > bounds[3] or bounds[2] > bounds[5] or bounds[1] != 63 or bounds[4] != 63:
            raise ValueError('invalid gallery pad bounds')
    for start in range(0, len(rows), columns):
        shelf = rows[start:start + columns]
        if len({(row['pad_bounds'][2], row['pad_bounds'][5]) for row in shelf}) != 1:
            raise ValueError('gallery row has uneven pad depths')
        for left, right in zip(shelf, shelf[1:]):
            if right['pad_bounds'][0] - left['pad_bounds'][3] - 1 != 3:
                raise ValueError('gallery must have three-block gaps')
        if start + columns < len(rows):
            next_z = rows[start + columns]['pad_bounds'][2]
            if any(next_z - row['pad_bounds'][5] - 1 != 3 for row in shelf):
                raise ValueError('gallery must have three-block gaps')
    return navigation


def begin_output(output, gallery, presentation):
    output, gallery, presentation = (Path(p).resolve() for p in (output, gallery, presentation))
    if output.exists():
        raise ValueError('package output must be new')
    for source in (BUILD.resolve(), RESOURCES.resolve(), gallery):
        if output == source or output in source.parents or source in output.parents:
            raise ValueError('package output must be outside inputs')
    if not presentation.is_file():
        raise FileNotFoundError(presentation)
    navigation = gallery_navigation(read_json(gallery / 'gallery-manifest.json'))
    output.mkdir(parents=True)
    shutil.copy2(presentation, output / 'Autumn-Variants.pptx')
    return navigation


def archive_files(target, files):
    stage = target.with_suffix(target.suffix + '.building')
    with zipfile.ZipFile(stage, 'w', zipfile.ZIP_DEFLATED, compresslevel=2) as archive:
        names = set()
        for source, relative in files:
            name = relative.replace('\\', '/')
            if PurePosixPath(name).is_absolute() or PureWindowsPath(name).drive or '..' in PurePosixPath(name).parts or name in names:
                raise ValueError('unsafe or duplicate archive path: ' + name)
            names.add(name)
            archive.write(source, name)
    with zipfile.ZipFile(stage) as archive:
        bad = archive.testzip()
        if bad:
            raise ValueError('archive CRC failure: ' + bad)
    stage.replace(target)


def archive_folder(source, target, prefix):
    archive_files(target, [(p, prefix + '/' + p.relative_to(source).as_posix())
                           for p in sorted(source.rglob('*')) if p.is_file() and p.name != 'session.lock'])


def check_mod(jar):
    with zipfile.ZipFile(jar) as archive:
        metadata = json.loads(archive.read('fabric.mod.json'))
        if metadata['version'] != '2.1.0-repair-catalog.1':
            raise ValueError('unexpected mod version')
        for rel in ('bloodborne_blocks/debug-ids.json', 'bloodborne_blocks/city/document-final.json',
                    'bloodborne_blocks/city/definitions.json', 'bloodborne_blocks/city/geometry.json',
                    'bloodborne_blocks/city/owner-meshes.json.gz',
                    'bloodborne_blocks/creative-equivalence.json'):
            if archive.read(rel) != (RESOURCES / rel).read_bytes():
                raise ValueError('stale mod resource: ' + rel)
        if any('dev/dreamwalker/capture/' in p or '/gametest/' in p for p in archive.namelist()):
            raise ValueError('development capture/test code in release jar')


def prepare_artist_materials():
    kit = BUILD / 'Artist-Kit-Release'
    materials = kit / 'Palette-and-References'
    materials.mkdir(exist_ok=True)
    for name in ('palettes.json', 'Artist-Palette.md'):
        shutil.copy2(BUILD / 'Autumn-Variants' / name, materials / name)
    leaf = BUILD / 'Autumn-Variants/01-pale-gold/assets/bloodborne_blocks/textures/block/autumn/leaves.png'
    shutil.copy2(leaf, materials / 'leaves.png')
    refs = [
        ('codex-clipboard-82ecc6ea-1dc6-4bb6-b922-e84dd2379d87.png', 'Reference-current-city.png'),
        ('codex-clipboard-2d4fa9bb-a88a-4531-8f1f-085459337889.png', 'Reference-autumn-concept.png'),
    ]
    temp = Path.home() / 'AppData/Local/Temp'
    for name, dest in refs:
        if (temp / name).is_file():
            shutil.copy2(temp / name, materials / dest)
    (materials / 'Start-here.md').write_text('''# Начало работы художника

1322 состояния 940 собранных определений находятся в соседних папках. Найдите объект по
debug_id из artist-manifest.json и табличке галереи. Откройте файл .gltf или OBJ с MTL;
PNG и UV сохранены. Имена конкретных файлов смотрите в папке объекта.

Начните с крон деревьев, затем осветляйте основные PNG камня; после этого крыши и окна.
Палитры используют цветовые множители и не могут повысить яркость тёмного пикселя.
Новая листва — предварительный AI-эскиз с прозрачностью, SHA256: LEAF_HASH.
Она добавляется визуальными плоскостями без новой физики. Художник должен доработать
силуэт и плотность кроны. Reference-autumn-concept.png — предоставленный пользователем
ориентир; игровые результаты находятся в Screenshots комплекта и презентации.

Для PNG сохраняйте namespace и source из texture_references в artist-manifest.json. Один PNG
может использоваться несколькими объектами: перед заменой проверьте все его материалы.
Геометрию, UV, координаты опоры и масштаб изменяйте осознанно; физические коллизии
не зависят от OBJ/glTF. Импорт изменённой геометрии в мод остаётся отдельной операцией.
Сначала согласуйте один объект в галерее, затем распространяйте изменение материала.
'''.replace('LEAF_HASH', sha(leaf)), encoding='utf8')


def marker_pack():
    folder = BUILD / 'Aux-Legacy-Markers'
    model = folder / 'assets/minecraft/models/block/redstone_block.json'
    model.parent.mkdir(parents=True, exist_ok=True)
    model.write_text(json.dumps({'parent': 'minecraft:block/cube_all',
                                'textures': {'all': 'bloodborne_blocks:block/stone'}}, indent=2) + '\n', encoding='utf8')
    (folder / 'pack.mcmeta').write_text(json.dumps({'pack': {'pack_format': 15,
        'description': 'Необязательно: красные строительные блоки выглядят как камень'}}, ensure_ascii=False) + '\n', encoding='utf8')
    archive_files(OUTPUT / 'Resource-Packs/Aux-Legacy-Markers.zip',
                  [(p, p.relative_to(folder).as_posix()) for p in folder.rglob('*') if p.is_file()])
    census = read_json(BUILD / 'native-marker-census.json')
    text = ['# Красные блоки исходной карты', '',
            '144 minecraft:redstone_block сохранены на прежних координатах. Их назначение не доказано.',
            'Aux-Legacy-Markers.zip меняет вид всех redstone_block на каменный, включая новые блоки.',
            'Геометрия, питание редстоуном и данные не меняются. Пак необязательный.',
            'Игровые кадры презентации сняты без этого пака. Галерея содержит исходный город.', '',
            '| № | Координаты | Переход |', '|---|---|---|']
    for i, row in enumerate(census, 1):
        x, y, z = row['position']
        text.append(f'| {i} | {x}, {y}, {z} | `/tp @s {x} {y + 2} {z}` |')
    gallery = GALLERY
    (gallery / 'Native-Markers.md').write_text('\n'.join(text) + '\n', encoding='utf8')


def package_dependencies():
    paths = [Path(p) for p in read_json(ROOT / 'tools/autumn-client-dependencies.json')['paths']
             if 'build/autumn-client-deps/' not in p.replace('\\', '/')]
    api = Path.home() / '.gradle/caches/modules-2/files-2.1/net.fabricmc.fabric-api/fabric-api/0.92.9+1.20.1'
    api_jars = [p for p in api.rglob('*.jar') if not p.name.endswith('-sources.jar')]
    if len(api_jars) != 1:
        raise ValueError('exact Fabric API jar not available')
    paths += api_jars
    files, records = [], []
    for path in paths:
        if not path.is_file():
            raise FileNotFoundError(path)
        with zipfile.ZipFile(path) as archive:
            info = json.loads(archive.read('fabric.mod.json'))
        optional = info['id'] in ('sodium', 'indium')
        if info.get('environment') == 'client' and not optional:
            raise ValueError('unexpected client-only common dependency: ' + info['id'])
        group = 'client-optional' if optional else 'common'
        files.append((path, group + '/' + path.name))
        records.append({'file': group + '/' + path.name, 'id': info['id'],
                        'version': info['version'], 'sha256': sha(path)})
    manifest = OUTPUT / 'Dependencies.json'
    manifest.write_text(json.dumps({'minecraft': '1.20.1', 'loader': '0.16.10',
        'java': '17', 'files': records}, ensure_ascii=False, indent=2) + '\n', encoding='utf8')
    archive_files(OUTPUT / 'Dependencies.zip', files + [(manifest, 'Dependencies.json')])


def main():
    proof = read_json(BUILD / 'final-world-verification-release.json')
    if not proof['passed'] or proof['errorCount'] or proof['helpers']['orphans']:
        raise ValueError('release world verification did not pass')
    jar = ROOT / 'build/libs/bloodborne-blocks-2.1.0-repair-catalog.1.jar'
    check_mod(jar)
    navigation = begin_output(OUTPUT, GALLERY, PRESENTATION)
    (OUTPUT / 'Resource-Packs').mkdir(exist_ok=True)
    marker_pack()
    prepare_artist_materials()
    shutil.copy2(jar, OUTPUT / jar.name)
    for folder, name, prefix in ((BUILD / 'Main-City-Release', 'Main-City', 'Main-City'),
                                (GALLERY, 'Approval-Gallery', 'Approval-Gallery-Compact'),
                                (BUILD / 'Artist-Kit-Release', 'Artist-Kit', 'Artist-Kit')):
        archive_folder(folder, OUTPUT / (name + '.zip'), prefix)
        print('packaged ' + name, flush=True)
    for pack in sorted((BUILD / 'Autumn-Variants').glob('*.zip')):
        shutil.copy2(pack, OUTPUT / 'Resource-Packs' / pack.name)
    for name in ('Gallery-Index.md', 'Review-Cases.md', 'Native-Markers.md'):
        shutil.copy2(GALLERY / name, OUTPUT / name)
    shots = OUTPUT / 'Screenshots'
    shots.mkdir(exist_ok=True)
    for path in sorted((ROOT / 'build/autumn-client-verified/screenshots').glob('*-*.png')):
        shutil.copy2(path, shots / path.name)
    if len(list(shots.glob('*.png'))) != 6 or not (OUTPUT / 'Autumn-Variants.pptx').is_file():
        raise ValueError('six gameplay screenshots and finalized presentation required')
    package_dependencies()
    (OUTPUT / 'README.md').write_text('''# Bloodborne — рабочая основа города

Комплект от 01.10.2026 для Minecraft 1.20.1, Java 17, Fabric Loader 0.16.10.
Основой служит Ether 2.0.2-positions, не rc.4. Google Doc «Правки финал» использован.

## Установка локальной тестовой сборки

1. Положите bloodborne-blocks-2.1.0-repair-catalog.1.jar и содержимое common из
   Dependencies.zip в mods. Клиент и сервер используют одинаковые версии этих модов.
   Не оставляйте одновременно другой JAR Bloodborne. Внутренние вложенные библиотеки
   уже находятся в JAR зависимостей; отдельно устанавливать их не требуется.
2. Распакуйте Main-City.zip: папка Main-City — готовый мир. Для локального клиента
   положите её в saves. Для dedicated-тестирования используйте отдельную папку сервера
   и level-name=Main-City. Настройки авторизации действующей сборки сохраняются.
3. Клиенту установите и включите в «Настройки → Наборы ресурсов» один из трёх
   Resource-Packs: 01-pale-gold, 02-classic-amber, 03-copper-evening.
   Переключайте по одному. Sodium/Indium из client-optional
   необязательны и устанавливаются только на клиент. Результаты сняты с ними.
4. Approval-Gallery.zip распакуйте как новый отдельный мир Approval-Gallery-Compact.
   Не распаковывайте поверх старой галереи: в ней останутся старые удалённые площадки.
   Creative, полёт, разрешение
   команд. Откройте Gallery-Index.md для переходов к объектам и решениям.
   Первый переход: `FIRST_GALLERY_TP`. Между краями площадок ровно 3 свободных блока;
   размеры площадок учитывают модели и их служебные части. В ряду GALLERY_COLUMNS площадок.
   Город в галерее — копия на прежних координатах.

Пак Aux-Legacy-Markers необязателен: 144 красных блока выглядят каменными. Он меняет
вид redstone_block глобально. Перед удалением этих блоков изучите Native-Markers.md.

## Что подготовлено

Карта конвертирована под новые состояния. Применены 2534 доказанные сборки документа;
при восстановлении изменены 33 проверенные клетки. Дополнены 6 утраченных Yuushya,
теперь их 416. Все 6605351 сторонних блоков и 170 сторонних block entity сохранены.
68237 служебных частей имеют действительных владельцев, ошибок проверки нет.

Вид большинства конструкций сохранён: меняется их сборка и взаимодействие, а не
внешняя модель. Например, у `/tp @s -425 84 -371` объект с debug ID 03833 собран
из трёх деталей в одну конструкцию; у `/tp @s -466 67 -311` объект 03835 — из девяти.
Для проверки администратора: `/bloodborne debug id 03833`. Проверяйте разрушение
на отдельной копии мира. Основная карта без включённого осеннего пака сохраняет
исходную палитру; новая листва и оттенки находятся в клиентских ресурс-паках.

Пересечения физических коллизий допущены при офлайн-формировании мира. Обычная
установка вновь проверяет запрет пересечения; сломанный конфликтующий объект
обратно поставить нельзя, пока пересечение не устранено. В одной клетке остаётся
одно BlockState: общий носитель хранит дополнительные привязки там, где это поддержано.
Взаимоисключающие корни не затираются; недоказанная сборка остаётся прежними деталями.

3855 типов блоков получили стабильные ID 00001–03855. Команда для администратора:
`/bloodborne debug id 00001`; номер не заменяет Minecraft registry ID.
Творческий каталог скрывает только 243 доказанных дубля; 46453 записей ещё видимы,
поэтому его дальнейшее сокращение остаётся отдельной задачей.

## Утверждение и художественная работа

Галерея: 1665 экспонатов/указателей, 1022 действительных типа, 18 спорных решений
документа и 8432 места, где сборка не подтверждена или конфликтует с хранением.
Часть мест повторяет одну причину; это список экземпляров, а не число разных ошибок.
В ней отдельно отмечены неперенесённые пункты 2, 4, 16, 17, 18 документа.
Для пунктов 8, 10, 13–15 и 20 остаётся проверка дополнительных деталей/трактовок;
для 13 нынешнее открытие поворачивает собранную раму, правильные створки требуют доработки.

Artist-Kit.zip содержит 1322 состояния 940 определений: собранные OBJ+MTL, glTF,
PNG, UV, реальные ID и источники материалов. Начало — Palette-and-References/Start-here.md.
В архив включены палитры, предварительная листва и доступные исходные иллюстрации.

Презентация Autumn-Variants.pptx и Screenshots показывают реальные игровые результаты
без шейдеров. Тёплый камень и предварительная листва уже работают, но светлые фасады
и полноценные осенние кроны концепта требуют перерисовки PNG и моделей художником.
Для быстрого первого запуска рекомендуется 02-classic-amber после просмотра галереи.

## Проверено

`check build logicalGameTest`: успешны, 67/67 серверных GameTest. После исправления
перезагрузки палитр повторены itemModelSelectionCheck, test_build_launch_gallery,
jar/remapJar. Выполнен полный read-only проход итогового мира, собственные состояния
проверены по registry, сторонние блоки и NBT сопоставлены с исходником.
Копия города загружена клиентом с полным набором зависимостей; сохранена и снята
в шести кадрах. Headless-запуск загрузил мод и полный набор сторонних зависимостей;
открытие мира остановлено существующим eula=false. Полную проверку мира на dedicated
server ещё нужно выполнить на тестовом Fabric-сервере Java17 с common, после принятия
EULA владельцем: `java -Xmx6G -jar fabric-server-launch.jar nogui`, затем штатно `stop`.
Рабочую production-карту и сервер этот комплект автоматически не заменяет.
'''.replace('FIRST_GALLERY_TP', navigation['firstPod']).replace('GALLERY_COLUMNS', str(navigation['columns'])), encoding='utf8')
    files = [(p.relative_to(OUTPUT).as_posix(), p.stat().st_size, sha(p))
             for p in sorted(OUTPUT.rglob('*')) if p.is_file() and p.name != 'Files-SHA256.json']
    (OUTPUT / 'Files-SHA256.json').write_text(json.dumps({'files': [
        {'path': name, 'bytes': size, 'sha256': digest} for name, size, digest in files]},
        ensure_ascii=False, indent=2) + '\n', encoding='utf8')
    print(json.dumps({'output': str(OUTPUT), 'files': len(files),
                      'bytes': sum(row[1] for row in files)}), flush=True)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--gallery', type=Path, default=GALLERY)
    parser.add_argument('--output', type=Path, default=OUTPUT)
    parser.add_argument('--presentation', type=Path, default=PRESENTATION)
    args = parser.parse_args()
    GALLERY, OUTPUT = args.gallery.resolve(), args.output.resolve()
    PRESENTATION = args.presentation.resolve()
    main()
