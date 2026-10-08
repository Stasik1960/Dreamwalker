"""Write the current review checkpoint only from completed RELEASE evidence."""
import hashlib
import json
import re
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SHA = '6cf52a3c91426e9ec3cf050d6949b94ebcc49e6168b6f497bb75aebacf174316'
VERSION = '0.1.0-prototype.3'


def read(name):
    return json.loads((ROOT / name).read_text(encoding='utf8'))


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def write(name, text):
    path = ROOT / name
    if path.exists() and name in {'README.txt', 'RELEASE-STATUS.md', 'CHECK_MATRIX.md', 'TEST_MATRIX.md'}:
        data = path.read_bytes()
        history = ROOT / ('reports/input-history/' + path.stem + '-before-v8-' + hashlib.sha256(data).hexdigest() + path.suffix)
        history.parent.mkdir(parents=True, exist_ok=True)
        if not history.exists():
            history.write_bytes(data)
    path.write_text(text, encoding='utf8')


def main():
    jar = ROOT / 'build/libs/dreamwalker-bb-fabric-1.20.1-0.1.0-prototype.3.jar'
    assert digest(jar) == SHA
    cases = ET.parse(ROOT / 'reports/FIRST_SET_GAMETEST_ATTEMPT_20.xml').getroot().findall('.//testcase')
    assert len(cases) == 76 and not any(case.find('failure') is not None or case.find('error') is not None for case in cases)
    log = (ROOT / 'reports/FIRST_SET_GAMETEST_ATTEMPT_20.log').read_text(encoding='utf8')
    assert 'BUILD SUCCESSFUL' in log and 'PASS 19 transaction core checks' in log
    reports = [
        'FIRST_SET_NEW_SCENE_AUTHOR_V8_RELEASE', 'FIRST_SET_NEW_SCENE_REOPEN_V8_RELEASE',
        'FIRST_SET_FULL_AUTHOR_V8_RELEASE', 'FIRST_SET_NEW_SCENE_FULL_SERVER_V8_RELEASE',
        'FIRST_SET_CLIENT_V8_RELEASE', 'FIRST_SET_FULL_CLIENT_SHADER_V8_RELEASE', 'FIRST_SET_ALT_CLIENT_V8_RELEASE',
        'SERVER_SOURCE_REVIEW_AUTHOR_V8_RELEASE', 'SERVER_SOURCE_REVIEW_REOPEN_V8_RELEASE', 'SERVER_SOURCE_REVIEW_REPLAY_V8_RELEASE',
    ]
    evidence = []
    for stem in reports:
        path = ROOT / ('reports/' + stem + '.json'); value = read(path.relative_to(ROOT))
        assert value['artifact_sha256'] == SHA and value['status'].startswith('PASS') and value['exit_code'] == 0 and not value.get('termination'), stem
        evidence.append({'path': str(path.relative_to(ROOT)), 'sha256': digest(path), 'status': value['status']})
    scene = read('reports/REVIEW_V8_SCENE_ARCHIVE_RELEASE.json')
    source = read('reports/FIRST_SET_SOURCE_REVIEW_SCENE_V8_RELEASE.json')
    assert scene['production_jar_sha256'] == source['artifact_sha256'] == SHA
    assert scene['root_count'] == 91 and source['all_archived_file_bytes_verified']
    catalogue = read('src/architecture/resources/bloodborne_dw/debug_catalogue.json')
    assert len(catalogue['entries']) == 107 and all(row['finalId'] is None for row in catalogue['entries'])
    metrics = read('reports/COMPOSITE_V8_GEOMETRY_METRICS.json')
    checkpoint = {
        'schema': 'dreamwalker-review-v8-checkpoint-v1', 'status': 'READY_FOR_REPEAT_USER_REVIEW',
        'production_version': VERSION, 'production_jar_sha256': SHA,
        'user_review': 'PENDING_USER_REVIEW', 'prior_set_review': 'PARTIALLY_ACCEPTED',
        'full_task_status': 'NOT_READY_FULL_TASK', 'mass_city_conversion': 'NOT_RUN_USER_ACCEPTANCE_GATE',
        'accepted_objects_preserved': ['wooden window appearance and reviewed behaviour', 'volumetric RP tree1', 'flat tree upper16 art parts'],
        'flat_tree_lower_variant1': 'PROPOSED_SOURCE_UV_STRETCH_2X_NOT_ACCEPTED',
        'native_tests': 76, 'transaction_core_checks': 19, 'ordinary_scene_roots': 91,
        'ordinary_scene_archive': scene['output'], 'ordinary_scene_archive_sha256': scene['sha256'],
        'bounded_source_objects': 41, 'bounded_source_members': 110, 'bounded_source_archive': source['archive'],
        'bounded_source_archive_sha256': source['archive_sha256'], 'temporary_catalogue_types': 107,
        'catalogue_final_ids': 'UNASSIGNED_NO_SILENT_RENUMBER', 'runtime_evidence': evidence,
        'per_point_report': 'docs/REVIEW_V8_ACCEPTANCE.md',
        'separate_geometry_and_microbenchmark_report': 'reports/COMPOSITE_V8_GEOMETRY_METRICS.json',
        'geometry_report_sha256': digest(ROOT / 'reports/COMPOSITE_V8_GEOMETRY_METRICS.json'),
        'actual_scene_counts': read('reports/REVIEW_V8_SCENE_COUNTS_RELEASE.json'),
        'manual_visual_gameplay': 'PENDING_USER_REVIEW',
        'not_run': ['two-client synchronisation', 'all RP forms/levers/two-lamp travel', 'full-city performance', 'full semantic catalogue/frequency IDs/gallery'],
        'source_original_inputs': 'PASS_ALL8_SHA256_UNCHANGED',
        'historical_failures_retained': ['client6d78 invalid lower JSON/inventory callback', 'full construction7f70 Lithium NBT key order', 'native18 open-root-only selection fixture', 'native15 genuine ladder context NPE and stale geometry fixtures'],
    }
    (ROOT / 'reports/REVIEW_V8_CHECKPOINT.json').write_text(json.dumps(checkpoint, ensure_ascii=False, indent=2) + '\n', encoding='utf8')
    table = '''| Проверка | Текущий результат / граница |
|---|---|
| Java17 build / native GameTest / транзакции | PASS76/76 +19; Attempt20 |
| Новая сцена, save, production-only reopen | PASS91 roots/29 composite UUID/695 unique helpers/132 native fixtures |
| Реальная новая установка в83 выбранных server-mod JAR и reopen | PASS91; строгие UUID/typed NBT/ledger/native fixtures |
| Минимальный ordinary client | PASS resources/world/save/exit0; actual mounted vertices/item models |
|109 выбранных client-mod JAR + Kappa5.2/settings42 | PASS startup/resources/active pipeline/effective options/save/exit0; один совместный прогон |
| ALT-only resourcepack | PASS load/source models/world/save/exit0 |
| Ограниченный исходный фрагмент | PASS41 objects/110 members/15 RP/2 lights; save/reopen/persisted no-op |
| Исходные данные | PASS8 original input SHA unchanged;707 RP source resources byte-exact |
| Пятизначные номера |107 TEMP assignments; finalId null; helper affiliation; debug aliases |
| Физика / selection / helpers / состояния | Отдельные before/after counts +1280 native cases; actual scene counts отдельно |
| Измерение CPU | Ограниченный same-JVM paired microbenchmark; не FPS/TPS/heap/город |
| Ручные вид/удобство/Creative UI/игровая приёмка | PENDING_USER_REVIEW; PASS техники не заменяет её |
| Два клиента / все RP формы, рычаги, два фонаря | NOT_RUN |
| Полный каталог, окончательные ID, конвертация города и галерея | NOT_READY_FULL_TASK; массовая работа ждёт приёмки |
'''
    write('CHECK_MATRIX.md', '# Матрица повторной приёмки V8\n\nProduction SHA256 `' + SHA + '`. Версия `' + VERSION + '`.\n\n' + table + '\nТочные текущие пути/SHA — reports/REVIEW_V8_CHECKPOINT.json и package CHECK-MATRIX.json. Исторические V7 и предыдущие кандидаты V8 сохраняются с собственными hash; их PASS не засчитывается текущему JAR.\n')
    write('TEST_MATRIX.md', '# Проверки review V8\n\n' + table + '\nМетод/границы: reports/COMPOSITE_V8_GEOMETRY_METRICS.json, reports/NBT_FULL_MOD_CANONICAL_RUNTIME_V8.json, reports/REVIEW_V8_FULL_SCENE_INDEPENDENT_RELEASE.json и source independent RELEASE reports. Неполные ручные проверки перечислены в docs/REVIEW_V8_ACCEPTANCE.md.\n')
    write('RELEASE-STATUS.md', '# Review V8: prototype.3\n\nВерсия `' + VERSION + '`; SHA256 `' + SHA + '`.\n\nПервый набор PARTIALLY_ACCEPTED; исправленная версия READY_FOR_REPEAT_USER_REVIEW / PENDING_USER_REVIEW. Полное задание NOT_READY_FULL_TASK. Деревянные панели, объёмное RP-дерево и верх плоского дерева сохранены. Нижний variant1 остаётся отдельно помеченным предложением. Массовая генерация и конвертация города ждут приёмки пользователя.\n\n' + table + '\nИсходный полный мир/установка/конфигурации/шейдеры пользователя не изменены. Все игровые прогоны и derived copies находятся под build/. В source checkpoint сохранены код, инструменты, документы, raw результаты и предыдущие неудачные проверки.\n')
    write('README.txt', f'''DREAMWALKER BB — ПОВТОРНАЯ ПРИЁМКА V8 / {VERSION}
Production SHA256: {SHA}
Полное задание NOT_READY_FULL_TASK; исправленный набор PENDING_USER_REVIEW.

1. В отдельной Minecraft1.20.1/Fabric/Java17 установке замените прежний
   Dreamwalker JAR одним новым prototype.3. Combined JAR уже содержит RP.
   Уберите standalone bloodborne-rp и старый Complete из этой тестовой установки.
   Loader0.19.5/Fabric API0.92.9+1.20.1/GeckoLib4.4.9 проверены;
   оригинальные два dependency JAR лежат в KIT/mods. QA addon для игры не нужен.

2. Распакуйте worlds/First-set-review-v8-scene.zip в saves.
   Новая площадка:91 архитектурный объект,132 native fixtures,28 станций.
   Точные координаты/причины/повторные проверки: REVIEW-INSTRUCTIONS.md.
   Архитектуре исходный resource pack не требуется.
   Optional source41 fixture сохраняет остальные vanilla carriers: для их
   исходного оформления подключите original bbmc_v15_resource (1).zip.
   Original Source-coordinate-fixture требует отдельного Forge1.18.2 эталона.

3. Инструмент: ПКМ в воздухе выбирает ROTATE/VARIANT/PROFILE/POSE/MOUNT;
   ПКМ объекта применяет действие. Shift+ROTATE переключает BASE/ALT.
   Нужны Creative с правом менять мир или OP2. Пустая рука/обычный предмет
   не меняют оформление деревянных панелей. Входная дверь открывается обычным
   ПКМ; ограда соединяется сама. /bb debug и /bloodborne debug показывают TEMP ID.
   Middle pick сохраняет художественный вариант. Числа TEMP900xx/910xx/920xx
   ещё не окончательные частотные ID и не номера экземпляров/UUID.

4. Двери/деревянные окна z8; стекло:стена z8,пол z18,потолок z25;
   AUTO ограды x-38..-7,z16..29; крыша5x2 z30/33; плоские деревья x24/36,z38;
   принятый RP tree1 x46,z38; лестницы z52 и неполные опоры z48.
   Нижний tree variant1 — непринятое предложение исходной UV с растяжениемY2x.
   Сравните с variant0; верхние16 частей у обоих сохранены.

5. Повторите пункты1–11 отзыва: внешний вид/UV, обе стороны двери/проход,
   AUTO соседства/верх ограды, вариант/три режима стекла, нижний стык дерева,
   обычную установку/middle pick/лазание лестницы, права инструмента,
   выбор/удаление соседних владельцев и save/restart. Сообщите результат по ID.
   Технические PASS76+19 и клиентская загрузка не заменяют вашу приёмку.

KIT содержит полный source checkpoint. Все старые FAIL/PASS помечены своим
SHA; рабочий мир пользователя и полная конвертация города не затронуты.
''')
    progress = ROOT / 'PROGRESS.md'; old = progress.read_text(encoding='utf8')
    old = re.sub(r'Ветка `codex/dreamwalker-bb-fabric-1\.20\.1`\.[^\n]*', 'Ветка `codex/dreamwalker-bb-fabric-1.20.1`. Полное задание **NOT_READY_FULL_TASK**. Первый набор **PARTIALLY_ACCEPTED**; corrected V8 **READY_FOR_REPEAT_USER_REVIEW / PENDING_USER_REVIEW**. Каталог/город/галерея ещё не завершены.', old, count=1)
    old += f'\n\n## Текущий checkpoint V8 / {VERSION}\n\nProduction SHA256 `{SHA}`. Native76/76, core19, minimal client, full83 новое построение/reopen, full109+Kappa42, ALT и source41 save/reopen/no-op подтверждены текущими RELEASE отчётами. Полный отзыв пользователя выше сохранён без сокращения. Таблица/координаты: docs/REVIEW_V8_ACCEPTANCE.md; current JSON: reports/REVIEW_V8_CHECKPOINT.json. Нижний tree variant1, сторона петель, исправленные объекты и изменения исходной физики ждут пользовательской приёмки; массовая конвертация не запускалась.\n'
    progress.write_text(old, encoding='utf8')
    print(json.dumps({'status': checkpoint['status'], 'sha256': SHA, 'native_tests': 76, 'user_review': checkpoint['user_review']}))


if __name__ == '__main__':
    main()
