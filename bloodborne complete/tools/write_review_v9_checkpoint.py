"""Write V9 review documents only after explicitly supplied current proofs pass.

Preparation/help never mutates project documents. --check-only evaluates the
same gates without writing. Historical reports, TASK/user texts and original
input files are read-only. Full-task and manual user acceptance remain pending.
"""
from __future__ import annotations
import argparse
import hashlib
import json
import re
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path
from package_first_set import ROOT, digest, require, resolve, read, json_bytes, verified_archive, directory_roots, zip_roots
from package_review_v9 import expected_native_cases


def relative(path):
    path = resolve(path, ROOT)
    return path.relative_to(ROOT).as_posix() if path.is_relative_to(ROOT) else str(path)


def gates(args):
    require(re.fullmatch(r'[0-9a-f]{64}', args.artifact_sha), 'Supply an actual frozen SHA256, not a placeholder')
    require(digest(args.jar) == args.artifact_sha, 'Explicit frozen production SHA differs from JAR bytes')
    from verify_review_v9_diagnostics import release_gate
    diagnostics = release_gate(args, args.artifact_sha)
    evidence = []

    def report(path, label):
        value = read(path)
        evidence.append({'label': label, 'path': relative(path), 'sha256': digest(path), 'status': value.get('status', value.get('package_status'))})
        return value

    def raw(value, key):
        wrapped = value[key]
        require(digest(resolve(wrapped['path'], ROOT)) == wrapped['sha256'], 'Wrapped actual JSON bytes changed: ' + key)
        actual = read(resolve(wrapped['path'], ROOT))
        require(actual == wrapped['result'], 'Wrapped actual JSON does not equal raw result: ' + key)
        return actual

    if args.diagnostics_report:
        report(args.diagnostics_report, 'actual current diagnostics on/off/reentry and linked local exports')

    def server(path, label, profile, production_only=False):
        value = report(path, label)
        require(value.get('artifact_sha256') == args.artifact_sha and value.get('status') == 'PASS' and value.get('exit_code') == 0 and 'termination' not in value,
                'Server did not complete normally with current JAR: ' + label)
        require(value['profile'] == profile and value.get('original_world_loaded') is False and value.get('rp_initialization_count') == 1,
                'Server profile, isolation or RP initialization differs: ' + label)
        require(digest(resolve(value['evidence'], ROOT)) == value['console_sha256'], 'Actual server console changed: ' + label)
        runtime = resolve(value['run_directory'], ROOT)
        require(runtime.is_relative_to((ROOT / 'build').resolve()), 'Server ran outside isolated build area')
        require(len([p for p in (runtime / 'mods').glob('*.jar') if digest(p) == args.artifact_sha]) == 1, 'Exact production JAR absent/duplicated in actual server')
        if production_only:
            require(not any(row.get('extra_mod') for row in value['modset']), 'Production-only reopen includes a QA addon')
            require(not value.get('ordinary_gameplay_input') and not value.get('review_scene_input') and not value.get('bounded_source_input'), 'Production-only reopen includes author/review input')
        return value

    author = server(args.author_report, 'minimal fresh ordinary AUTHOR', 'minimal')
    restart = server(args.gameplay_restart_report, 'minimal actual gameplay REOPEN', 'minimal')
    server(args.reopen_report, 'minimal production-only REOPEN', 'minimal', True)
    full_author = server(args.full_author_report, 'full modset fresh ordinary AUTHOR', 'full_server')
    full_restart = server(args.full_gameplay_restart_report, 'full modset actual gameplay REOPEN', 'full_server')
    server(args.full_server_report, 'full modset production-only REOPEN', 'full_server', True)
    for value in [author, full_author]:
        require(raw(value, 'review_scene_output')['status'] == 'PASS_SCENE_AUTHORED_REQUIRES_PRODUCTION_REOPEN', 'Fresh scene author was incomplete')
        require(raw(value, 'ordinary_gameplay_output')['status'] == 'PASS_V9_ORDINARY_GAMEPLAY_AUTHOR_REQUIRES_ACTUAL_RESTART', 'Ordinary gameplay author was incomplete')
    for value in [restart, full_restart]:
        require(raw(value, 'ordinary_gameplay_output')['status'] == 'PASS_V9_ORDINARY_GAMEPLAY_AFTER_ACTUAL_RESTART', 'Actual persisted ordinary gameplay was incomplete')

    scene = report(args.scene_report, 'exact production-only scene archive')
    proof = report(args.scene_verification, 'independent scene and gameplay verification')
    require(scene.get('production_jar_sha256') == proof.get('production_jar_sha256') == args.artifact_sha, 'Scene proof belongs to another JAR')
    require(scene['status'] == 'PACKAGED_VERIFIED_SCENE_PENDING_USER_REVIEW' and proof['status'] == 'PASS_SAVED_OWNERS_NATIVE_FIXTURES_AND_PRODUCTION_REOPEN', 'Scene/archive verification failed')
    from verify_review_v9_scene import run
    class VerificationArgs:
        pass
    check = VerificationArgs()
    for field in ['author_report', 'gameplay_restart_report', 'gameplay_input', 'gameplay_reopen_input', 'artifact_sha']:
        setattr(check, field, getattr(args, field))
    check.server_report = args.reopen_report
    check.scene_input = resolve(proof['scene_input'], ROOT)
    require(run(check) == proof, 'Independent minimal scene proof no longer matches current saved worlds')
    require(digest(args.scene_verification) == scene['verification_report_sha256'], 'Scene archive verification report changed')
    require(resolve(scene['verification_report'], ROOT) == args.scene_verification, 'Archive uses another scene verifier')
    scene_zip = resolve(scene['output'], ROOT)
    require(digest(scene_zip) == scene['sha256'], 'Current scene ZIP changed')
    verified_archive(scene_zip, scene['world_prefix'], scene['files'])
    require(len(zip_roots(scene_zip, scene['world_prefix'])) == proof['actual_root_count'], 'Archived roots differ from exact scene proof')
    check.author_report = args.full_author_report
    check.gameplay_restart_report = args.full_gameplay_restart_report
    check.server_report = args.full_server_report
    full_proof = run(check)
    if args.full_scene_verification:
        require(report(args.full_scene_verification, 'independent full-modset scene') == full_proof, 'Full scene proof uses another saved UUID/NBT chain')
    require(full_proof['actual_root_count'] == proof['actual_root_count'], 'Full modset construction differs from frozen scene input')

    def client(path, label, profile):
        value = report(path, label)
        require(value.get('artifact_sha256') == args.artifact_sha and value.get('status') == 'PASS_CLIENT_WORLD_LOAD_SAVE_NORMAL_EXIT' and value.get('exit_code') == 0 and 'termination' not in value,
                'Current client failed or did not exit normally: ' + label)
        require(value['profile'] == profile and value.get('integrated_save_messages_present') is True, 'Current client profile/save proof differs')
        runtime = resolve(value['run_directory'], ROOT)
        require(runtime.is_relative_to((ROOT / 'build').resolve()) and digest(runtime / 'launch-console.log') == value['console_sha256'], 'Actual client console changed/outside isolated area')
        actual = raw(value, 'client_review_output')
        require(actual.get('status', '').startswith('PASS_') and actual.get('normalStopRequested') is True and actual.get('guard') == 'ISOLATED_SAVED_REVIEW_CLIENT_ONLY', 'Actual client review is incomplete')
        require(actual.get('actualProductionOrigins') and all(row['sha256'] == args.artifact_sha for row in actual['actualProductionOrigins']), 'Client loaded another actual production JAR')
        marker_path = resolve(value['qa_input']['source'], ROOT)
        require(digest(marker_path) == value['qa_input']['sha256'], 'Client marker changed')
        marker = read(marker_path)
        require(marker['productionJarSha256'] == args.artifact_sha and not marker.get('diagnosticOnly'), 'Diagnostic/stale client cannot gate current checkpoint')
        require(value.get('derived_world_copy', {}).get('source_unchanged_after_run') is True, 'Client changed immutable input scene')
        require(directory_roots(resolve(actual['actualWorldDirectory'], ROOT)) == zip_roots(scene_zip, scene['world_prefix']), 'Actual client scene root states differ from archive')
        return actual

    client(args.client_report, 'ordinary minimal client', 'minimal')
    optional = {'full_client': 'NOT_RUN_CURRENT_ARTIFACT', 'shader': 'NOT_RUN_CURRENT_ARTIFACT', 'alt_client': 'NOT_RUN_CURRENT_ARTIFACT'}
    if args.full_client_report:
        client(args.full_client_report, 'ordinary full client', 'full_client')
        optional['full_client'] = 'PASS_CURRENT_PROFILE_WORLD_SAVE_NORMAL_EXIT'
    if args.shader_report:
        actual = client(args.shader_report, 'actual active shader client', 'full_client')
        iris = actual.get('irisRuntime', {})
        first, last = iris.get('initial', {}), iris.get('final', {})
        require(iris.get('status') == 'PASS_ACTIVE_IRIS_RENDERING_PIPELINE' and last.get('frameCounter', 0) > first.get('frameCounter', 0), 'Shader pipeline proof absent/counter did not advance')
        require(last.get('fallback') is False and last.get('shadersEnabled') is True and last.get('shaderMapPresent') is True and last.get('pipelineClass') == 'net.irisshaders.iris.pipeline.IrisRenderingPipeline', 'Client used shader fallback/no actual shader map')
        optional['shader'] = 'PASS_ACTIVE_IRIS_PIPELINE_NORMAL_SAVE_EXIT_MANUAL_VISUAL_PENDING'
    if args.alt_client_report:
        client(args.alt_client_report, 'ordinary ALT client', 'minimal')
        optional['alt_client'] = 'PASS_CURRENT_ALT_RESOURCE_MODE_WORLD_SAVE_NORMAL_EXIT'

    expected = expected_native_cases()
    xml = ET.parse(args.native_xml).getroot()
    cases = list(xml.iter('testcase'))
    names = [case.get('name', '').lower() for case in cases]
    require(len(names) == len(set(names)) and set(names) == expected, 'Native XML does not cover exact current registered methods')
    require(not list(xml.iter('failure')) and not list(xml.iter('error')) and not list(xml.iter('skipped')), 'Current native tests failed/errored/skipped')
    for suite in xml.iter('testsuite'):
        require(all(int(suite.get(k, '0')) == 0 for k in ['failures', 'errors', 'skipped']), 'Native XML summary failed')
    core_source = (ROOT / 'src/test/java/dev/dreamwalker/bloodbornedw/runtime/TransactionCoreTest.java').read_text(encoding='utf8')
    core_count = len(re.findall(r'\brun\("', core_source))
    log = args.build_log.read_text(encoding='utf8', errors='replace')
    require(re.search(r'PASS ' + str(core_count) + r' transaction core checks; Java 17\.', log) and 'BUILD SUCCESSFUL' in log and 'BUILD FAILED' not in log, 'Current Java17 core/build evidence incomplete')
    evidence += [{'label': 'exact registered native tests', 'path': relative(args.native_xml), 'sha256': digest(args.native_xml), 'status': 'PASS', 'count': len(cases), 'cases': sorted(expected)},
                 {'label': 'Java17 core/build', 'path': relative(args.build_log), 'sha256': digest(args.build_log), 'status': 'PASS', 'count': core_count}]

    rp = report(args.rp_report, 'current RP source/package bytes')
    require(rp.get('final_jar_sha256') == args.artifact_sha and rp.get('source_status') == rp.get('package_status') == 'PASS' and all(row['source_status'] == row['package_status'] == 'PASS' for row in rp['checks']), 'Current RP byte audit failed')
    package = report(args.package_report, 'current production/QA separation')
    require(package.get('production_sha256') == args.artifact_sha and package.get('status') == 'PASS' and package.get('production_contains_qa') is False, 'Current production/QA separation failed')
    require(digest(args.qa_jar) == package['qa_sha256'], 'Explicit QA JAR differs from actual reviewed addon')
    with zipfile.ZipFile(args.jar) as archive:
        metadata = json.loads(archive.read('fabric.mod.json'))
        require(metadata['id'] == 'bloodborne_dw' and 'bloodborne_rp' in metadata.get('provides', []) and 'prototype.4' in metadata['version'], 'Wrong combined current V9 metadata')
        require(not any(name.startswith(('dev/dreamwalker/bloodbornedw/review/', 'dev/dreamwalker/bloodbornedw/gametest/', 'dev/dreamwalker/bloodbornerp/lamp/V9GameplayReviewBootstrap')) for name in archive.namelist()), 'Review/GameTest code leaked into production')
        catalogue = json.loads(archive.read('bloodborne_dw/debug_catalogue.json'))
        require(catalogue == read(ROOT / 'src/architecture/resources/bloodborne_dw/debug_catalogue.json'), 'Built catalogue differs from current source')
        require(len({row['temporaryId'] for row in catalogue['entries']}) == len(catalogue['entries']) and all(row['finalId'] is None for row in catalogue['entries']), 'Temporary type numbers duplicate or silently became final IDs')
    type_audit = report(args.type_audit, 'current source art and append-only TEMP type audit')
    require(type_audit['status'] == 'PASS_READ_ONLY_APPEND_ONLY_TYPES_AND_EXACT_IMPORTED_ART' and type_audit['catalogueRowsIncludingReserved'] == len(catalogue['entries']), 'Current type/art audit failed')
    report(args.type_mapping, 'explicit type compatibility table; status retained without upgrade')
    object_audit = report(args.rp_object_audit, 'RP exact artwork/object static audit')
    require(object_audit['status'] == 'PASS_STATIC_RESOURCE_AND_EXACT_DUPLICATE_AUDIT', 'RP object/art audit failed')
    if args.geometry_report:
        report(args.geometry_report, 'geometry/microbenchmark scope; reported status retained')

    source = report(args.source_scene_report, 'current bounded source41 exact archive')
    require(source.get('artifact_sha256') == args.artifact_sha and source.get('status', '').startswith('PASS_ARCHIVE_EXACT_PRODUCTION_REOPEN'), 'Source41 archive is not current PASS')
    require(source.get('all_archived_file_bytes_verified') is True and source.get('reopened_world_unchanged') is True and source['counts']['objects'] == 41 and source['counts']['source_member_cells'] == 110, 'Source41 byte/ownership archive proof failed')
    require(resolve(source['input'], ROOT) == args.source_input and digest(args.source_input) == source['input_sha256'], 'Source41 prepared input changed')
    source_input = read(args.source_input)
    require(source_input.get('reviewRevision') == 'V9' and source_input.get('authoringGuard') == 'SOURCE_COPY_EXPLICIT_MEMBERS_ONLY' and len(source_input['objects']) == 41 and source_input['sourceMemberCount'] == 110, 'Historical/non-explicit source input cannot gate V9')
    require(digest(args.frozen_source) == source['source_fixture_sha256'], 'Frozen original coordinate fixture changed')
    source_zip = resolve(source['archive'], ROOT)
    require(digest(source_zip) == source['archive_sha256'], 'Source41 ZIP changed')
    verified_archive(source_zip, 'First-set-migrated-source-fixture/', source['file_manifest'])
    archived_gate_paths = {resolve(row['path'], ROOT) for row in source['evidence_gates']}
    for field in ['source_author_report', 'source_reopen_report', 'source_replay_report']:
        path = getattr(args, field)
        require(path in archived_gate_paths, 'Explicit source runtime wrapper is absent from archived proof chain: ' + field)
        value = server(path, field, 'minimal', field == 'source_reopen_report')
        if field != 'source_reopen_report':
            raw(value, 'bounded_source_output')
    for gate in source['evidence_gates']:
        path = resolve(gate['path'], ROOT)
        require(digest(path) == gate['sha256'], 'Source archive evidence changed after verification')
        value = report(path, 'source41 ' + path.stem)
        require(value.get('status', '').startswith('PASS') and not value.get('failures'), 'Current source independent/runtime evidence failed')
        for key in ['artifact_sha256', 'production_jar_sha256']:
            if key in value:
                require(value[key] == args.artifact_sha, 'Source proof uses another current artifact')
        if 'input_sha256' in value:
            require(value['input_sha256'] == source['input_sha256'], 'Source independent proof uses another prepared input')
    plan = resolve(source['migration_plan'], ROOT)
    require(digest(plan) == source['migration_plan_sha256'], 'Source migration plan changed')
    evidence.append({'label': 'exact source migration plan', 'path': relative(plan), 'sha256': digest(plan), 'status': 'PASS_EXACT_PLAN_BYTES'})
    for path in args.extra_evidence:
        value = report(path, 'additional scoped evidence')
        for key in ['artifact_sha256', 'production_jar_sha256', 'final_jar_sha256', 'production_sha256']:
            if key in value:
                require(value[key] == args.artifact_sha, 'Additional evidence uses another JAR: ' + str(path))

    inputs = read(ROOT / 'INPUTS.json')
    require(len(inputs['inputs']) == 8, 'Original eight input rows are missing')
    for row in inputs['inputs']:
        path = Path(row['path'])
        require(path.is_file() and path.stat().st_size == row['size'] and digest(path) == row['sha256'], 'Original input bytes changed: ' + row['name'])
    require(args.review_readme.is_file(), 'Current per-point Russian review document missing')
    return {'sha': args.artifact_sha, 'version': metadata['version'], 'evidence': evidence, 'native': len(cases), 'core': core_count,
            'scene': scene, 'proof': proof, 'full_proof': full_proof, 'source': source, 'optional': optional, 'catalogue_count': len(catalogue['entries']),
            'inputs': inputs, 'review_readme': relative(args.review_readme), 'review_readme_sha256': digest(args.review_readme), 'diagnostics': diagnostics}


def backup(path, history):
    path = ROOT / path
    data = path.read_bytes()
    saved = ROOT / 'reports/input-history' / (path.stem + '-before-v9-' + hashlib.sha256(data).hexdigest() + path.suffix)
    require(not saved.exists() or saved.read_bytes() == data, 'Existing historical document bytes differ')
    saved.parent.mkdir(parents=True, exist_ok=True)
    if not saved.exists():
        saved.write_bytes(data)
    history.append({'path': relative(path), 'previous_sha256': hashlib.sha256(data).hexdigest(), 'exact_previous_file': relative(saved)})


def render_documents(proof):
    scene, source, actual = proof['scene'], proof['source'], proof['proof']
    stats = {'architecture_roots': actual['actual_root_count'], 'composite_owners': len(actual['composite_owners']),
             'cached_root_and_helper_bes': actual['cached_root_and_helper_bes'], 'ledger_cells': actual['ledger_cell_count'],
             'native_wall_and_ladder_roots': len(actual['native_ladder_and_wall_roots']), 'native_fixtures': len(actual['native_fixtures']),
             'rp_entities': len(actual['rp_controls']), 'per_kind': actual['root_counts_by_kind']}
    optional = proof['optional']
    table = ('| Проверка | Текущий результат / граница |\n|---|---|\n'
             f'| Java17 / native GameTests / транзакции | PASS {proof["native"]}/{proof["native"]} + {proof["core"]}; точный текущий список методов проверен |\n'
             f'| Новая обычная установка → gameplay restart → production-only save | PASS {stats["architecture_roots"]} roots / {stats["composite_owners"]} UUID / {stats["native_fixtures"]} native fixtures / {stats["rp_entities"]} RP |\n'
             '| Полный выбранный server modset | PASS отдельное новое построение → actual gameplay restart → production-only reopen |\n'
             '| Рычаги и строительные инструменты | PASS реальные callbacks, исходная задержка70 ticks, UUID-связи, pending и повтор после actual restart |\n'
             '| Два Hunterlamp | PASS штатная регистрация/list packet, A→B и B→A survival endpoints после actual restart; ручной GUI не проверен |\n'
             '| Automatic lighting новых Hunterlamp | UNRESOLVED_NEW_LAMP_LIGHTING: original Forge activation создаёт light9; ordinary port не выполняет light writes; source2 technical cells отдельно сохранены |\n'
             '| Обычный минимальный клиент | PASS resources/world/save/normal exit; ручной вид и удобство PENDING_USER_REVIEW |\n'
             f'| Встроенная диагностика | {proof["diagnostics"]["status"]}; actual default60/off-on/saved reentry/server-client session ZIP; GPU/FPS attribution не заявлены |\n'
             f'| Полный обычный клиент | {optional["full_client"]} |\n'
             f'| Шейдеры | {optional["shader"]}; визуальная fidelity PENDING_USER_REVIEW |\n'
             f'| ALT клиент | {optional["alt_client"]} |\n'
             '| Исходный ограниченный фрагмент | PASS41 objects/110 members, production reopen и persisted no-op; не полный город |\n'
             f'| TEMP типы | {proof["catalogue_count"]} строк включая reserved; finalId null; прежние ID не переиспользованы |\n'
             '| Оригинальные входы и тексты | PASS8 SHA256 unchanged; TASK/user text не переписаны |\n'
             '| Полное задание | NOT_READY_FULL_TASK; полный семантический каталог/частотные final IDs/город/галерея не завершены |\n'
             '| Ручная приёмка / два клиента / производительность города | PENDING_USER_REVIEW / NOT_RUN / NOT_RUN |\n')
    heading = f'Production `{proof["version"]}`, SHA256 `{proof["sha"]}`.\n\n'
    limits = ('\nТехнические PASS относятся только к явно выбранному текущему JAR и перечисленным isolated worlds. '
              'Ранее принятые дерево и деревянные панели сохранены; новое оформление/физика/удобство ждут приёмки. '
              'Woodgate: исходный Forge reference физически empty; текущая простая solid leaf plane — явно предлагаемая gameplay физика, не утверждение тождества. '
              'Trapdoor91088 остаётся статическим исключением и не получает выдуманной функции. '
              'Native UP лестницы: TOP slab/STONE/предыдущая секция поддерживаются; BOTTOM slab и fence с дробной высотой основания явно отвергаются без расхода предмета. '
              'Дробный вертикальный монтаж native лестницы пока не реализован; боковая частичная опора проверяется отдельно. Массовая конвертация не запускалась.\n')
    readme = (f'DREAMWALKER BB — ПОВТОРНАЯ ПРИЁМКА V9 / {proof["version"]}\nProduction SHA256: {proof["sha"]}\n'
              'Исправленный набор PENDING_USER_REVIEW. Полное задание NOT_READY_FULL_TASK.\n\n'
              'В отдельной Minecraft1.20.1 / Fabric / Java17 установке оставьте один новый combined Dreamwalker JAR. '
              'Он уже содержит RP; удалите прежний Dreamwalker и отдельный bloodborne-rp из этой тестовой установки. '
              'Точные проверенные зависимости лежат в KIT/mods. Отдельный QA addon для обычной игры не нужен.\n\n'
              f'Распакуйте worlds/First-set-review-v9-scene.zip в saves. Сцена: {stats["architecture_roots"]} архитектурных roots, '
              f'{stats["native_fixtures"]} native fixtures, {stats["rp_entities"]} RP экземпляров. '
              f'Координаты и проверки: {proof["review_readme"]}, tools/first_set_scene_v9_input.json, tools/first_set_v9_gameplay_input.json.\n\n'
              'Самостоятельный рисунок выбирается отдельным предметом с собственным TEMP ID. Поворот/монтаж/BASE–ALT/открытие/видимость собак относятся к экземпляру. '
              'Старый VARIANT инструмента сохранён в NBT, но смена самостоятельного исполнения им отвергается. '
              'Middle pick и drop возвращают соответствующий тип.\n\n'
              'Строительный инструмент: ПКМ воздух переключает режим; Shift+ПКМ воздух отменяет выбранный рычаг. '
              'Режим LINK: выберите настоящий RP рычаг, затем фактический RP механизм или архитектурную дверь; '
              'CONNECTIONS показывает связи/pending; UNLINK разрывает выбранную связь; CANCEL отменяет выбор. '
              'Нужны Creative с правом изменять мир или OP2. Survival обычные дверь/рычаг/фонарь сохраняют gameplay взаимодействие. '
              'Рычаг использует исходную задержку70 ticks, woodgate — one-shot32 ticks без held/offhand очереди.\n\n'
              'Встроенная диагностика выключена по умолчанию. OP2: /bb diagnostics start (60s), status, mark, stop, export; '
              '/bb debug и diagnostics snapshot сохраняют снимок. Команды, локальные журналы/ZIP и пределы измерений: docs/DIAGNOSTICS_V9.md '
              '(в KIT — DIAGNOSTICS-V9.md). Проверены только явно перечисленные текущие off/on/reenter и item/model цепочки; '
              'GPU time и процент нагрузки объекта по FPS не вычисляются.\n\n'
              'Два Hunterlamp уже зарегистрированы с маршрутами A→B и B→A. Проверены реальные серверные list packets/travel endpoints после restart; '
              'ручное меню, клиентские клики и визуальное качество остаются для пользовательской приёмки. '
              'Respawn не изменён. Automatic light9 исходного Forge фонаря пока UNRESOLVED_NEW_LAMP_LIGHTING в новом ordinary port; '
              'две исходные technical light9 клетки source41 сохраняются отдельно. Рабочие travel и source-light preservation не доказывают automatic lighting новых фонарей.\n\n'
              f'Техника: native {proof["native"]}/{proof["native"]}, core {proof["core"]}, новая установка и повторные сохранения в minimal/full server. '
              'Точные текущие статусы клиента/шейдеров/source41: reports/REVIEW_V9_CHECKPOINT.json. '
              'Исторические FAIL/PASS и исходные задания сохранены с собственными SHA. Полный город/каталог/финальные номера не завершены.\n')
    docs = {'README.txt': readme, 'CHECK_MATRIX.md': '# Матрица повторной приёмки V9\n\n' + heading + table + limits,
            'TEST_MATRIX.md': '# Технические проверки review V9\n\n' + heading + table + '\nТочные methods, фазовые UUID/typed NBT/ledger, input hashes и границы QA записаны в reports/REVIEW_V9_CHECKPOINT.json и связанных primary reports.\n' + limits,
            'RELEASE-STATUS.md': '# Review V9: prototype.4\n\n' + heading + 'READY_FOR_REPEAT_USER_REVIEW / PENDING_USER_REVIEW. Полное задание NOT_READY_FULL_TASK.\n\n' + table + limits}
    checkpoint = {'schema': 'dreamwalker-review-v9-checkpoint-v1', 'status': 'READY_FOR_REPEAT_USER_REVIEW',
                  'production_version': proof['version'], 'production_jar_sha256': proof['sha'], 'user_review': 'PENDING_USER_REVIEW',
                  'prior_set_review': 'PARTIALLY_ACCEPTED', 'full_task_status': 'NOT_READY_FULL_TASK', 'mass_city_conversion': 'NOT_RUN_USER_ACCEPTANCE_GATE',
                  'native_tests': proof['native'], 'transaction_core_checks': proof['core'], 'ordinary_scene_roots': stats['architecture_roots'],
                  'ordinary_scene_archive': scene['output'], 'ordinary_scene_archive_sha256': scene['sha256'], 'actual_scene_counts': stats,
                  'bounded_source_objects': source['counts']['objects'], 'bounded_source_members': source['counts']['source_member_cells'],
                  'bounded_source_archive': source['archive'], 'bounded_source_archive_sha256': source['archive_sha256'],
                  'temporary_catalogue_types_including_reserved': proof['catalogue_count'], 'catalogue_final_ids': 'UNASSIGNED_NO_SILENT_RENUMBER',
                  'runtime_evidence': proof['evidence'], 'optional_current_verdicts': optional, 'per_point_report': proof['review_readme'],
                  'diagnostics': {'status': proof['diagnostics']['status'], 'report': next((row['path'] for row in proof['evidence'] if row['label'] == 'actual current diagnostics on/off/reentry and linked local exports'), None),
                                  'ordinary_item_model_chain': 'ACTUAL90010_ONLY; full RP new-placement model chain not implied', 'gpu_duration': 'NOT_MEASURED', 'performance_attribution': 'RAW_MATCHED_WINDOWS_NO_OBJECT_FPS_PERCENTAGE'},
                  'per_point_report_sha256': proof['review_readme_sha256'], 'source_original_inputs': 'PASS_ALL8_SHA256_UNCHANGED',
                  'manual_visual_gameplay': 'PENDING_USER_REVIEW', 'two_lamp_gameplay': 'PASS_REAL_SERVER_LIST_TRAVEL_AND_ACTUAL_RESTART_MANUAL_GUI_PENDING',
                  'new_lamp_automatic_lighting': 'UNRESOLVED_NEW_LAMP_LIGHTING_ORIGINAL_FORGE_ACTIVATION_LIGHT9_PORT_NO_LIGHT_WRITES',
                  'not_run': ['two-client synchronization', 'manual creative UI/menu/visual acceptance', 'full-city performance', 'full semantic catalogue/frequency final IDs/gallery', 'fractional-height native UP ladder mounting'],
                  'native_ladder_up_support': 'TOP_SLAB_STONE_OR_PRIOR_SECTION; FRACTIONAL_BOTTOM_SLAB_FENCE_ATOMICALLY_REFUSED_NO_ITEM_CONSUMPTION',
                  'source_woodgate_physics': 'ORIGINAL_FORGE_EMPTY_CURRENT_SIMPLE_SOLID_LEAF_PROPOSED', 'static_trapdoor91088': 'UNSUPPORTED_STATIC_EXCEPTION',
                  'historical_failures_retained': True, 'final_package_status': 'NOT_PACKAGED_BY_CHECKPOINT_WRITER'}
    return docs, checkpoint


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    required = ['jar', 'qa-jar', 'native-xml', 'build-log', 'author-report', 'gameplay-restart-report', 'reopen-report',
                'full-author-report', 'full-gameplay-restart-report', 'full-server-report', 'gameplay-input', 'gameplay-reopen-input',
                'scene-verification', 'scene-report', 'client-report', 'rp-report', 'package-report', 'type-audit', 'type-mapping',
                'rp-object-audit', 'source-scene-report', 'source-input', 'frozen-source', 'source-author-report', 'source-reopen-report',
                'source-replay-report', 'review-readme', 'report']
    for name in required:
        parser.add_argument('--' + name, type=Path, required=True)
    for name in ['full-scene-verification', 'full-client-report', 'shader-report', 'alt-client-report', 'geometry-report']:
        parser.add_argument('--' + name, type=Path)
    parser.add_argument('--diagnostics-report', type=Path, help='Required for final prototype.4 emission: independent current runtime/off-on/reentry/local-ZIP proof')
    parser.add_argument('--historical-check-without-diagnostics', action='store_true', help='Only --check-only; explicitly incomplete historical check, never final readiness')
    parser.add_argument('--artifact-sha', required=True)
    parser.add_argument('--extra-evidence', type=Path, action='append', default=[])
    parser.add_argument('--preserve-text', type=Path, action='append', default=[], help='Additional assignment/user text whose bytes must be preserved')
    parser.add_argument('--check-only', action='store_true')
    args = parser.parse_args()
    for name, value in vars(args).items():
        if isinstance(value, Path):
            setattr(args, name, resolve(value, ROOT))
    args.extra_evidence = [resolve(path, ROOT) for path in args.extra_evidence]
    args.preserve_text = [resolve(path, ROOT) for path in args.preserve_text]
    require(args.report.is_relative_to((ROOT / 'reports').resolve()), 'Checkpoint report must remain in current project reports')
    require(not args.report.exists(), 'Keep historical checkpoints; choose a fresh output or archive previous revision explicitly')
    protected = [ROOT / 'TASK.md', *args.preserve_text]
    protected_hashes = {str(path): digest(path) for path in protected}
    proof = gates(args)
    documents, checkpoint = render_documents(proof)
    if args.check_only:
        print(json.dumps({'status': 'PASS_V9_HISTORICAL_GATES_DIAGNOSTICS_NOT_CHECKED' if args.historical_check_without_diagnostics else 'PASS_V9_CHECKPOINT_GATES_READ_ONLY', 'production_jar_sha256': proof['sha'], 'native_tests': proof['native'], 'core_tests': proof['core'], 'scene_roots': proof['proof']['actual_root_count'], 'diagnostics': proof['diagnostics']['status'], 'user_review': 'PENDING_USER_REVIEW'}))
        return
    history = []
    for name in [*documents, 'INPUTS.json', 'PROGRESS.md']:
        backup(name, history)
    inputs = proof['inputs']
    original_rows = json_bytes(inputs['inputs'])
    target = inputs['target']
    target.setdefault('historical_checkpoints', []).append({'revision': target.get('first_set_evidence_revision'), 'production_version': target.get('production_version'),
                                                           'production_jar_sha256': target.get('production_jar_sha256'), 'original_metadata': next(row['exact_previous_file'] for row in history if row['path'] == 'INPUTS.json'),
                                                           'original_metadata_sha256': digest(ROOT / 'INPUTS.json'), 'review_result': target.get('user_visual_game_acceptance', 'HISTORICAL_STATUS_PRESERVED')})
    target.update({'build_status': 'PROTOTYPE_4_REMAPPED_REVIEW_V9_VERIFIED_FULL_TASK_NOT_READY', 'production_version': proof['version'],
                   'production_jar_sha256': proof['sha'], 'first_set_evidence_revision': 'v9-release', 'user_visual_game_acceptance': 'PENDING_USER_REVIEW',
                   'prior_set_review': 'PARTIALLY_ACCEPTED', 'full_task_status': 'NOT_READY_FULL_TASK', 'current_checkpoint': relative(args.report)})
    require(json_bytes(inputs['inputs']) == original_rows, 'Writer altered original input rows')
    documents['INPUTS.json'] = json_bytes(inputs).decode('utf8')
    old_progress = (ROOT / 'PROGRESS.md').read_bytes()
    append = (f'\n\n## Текущий checkpoint V9 / {proof["version"]}\n\nProduction SHA256 `{proof["sha"]}`. '
              f'Native{proof["native"]}/{proof["native"]}, core{proof["core"]}; minimal/full fresh ordinary AUTHOR → actual gameplay REOPEN → production-only REOPEN, '
              'рычаги/UUID/pending и два Hunterlamp подтверждены текущими explicit reports. Клиентские и source41 границы записаны отдельно в checkpoint. '
              'TASK, пользовательские тексты, входные8 строк и предыдущая история сохранены. READY_FOR_REPEAT_USER_REVIEW / PENDING_USER_REVIEW; '
              f'полное задание NOT_READY_FULL_TASK; массовой конвертации нет. Current JSON: {relative(args.report)}.\n').encode('utf8')
    for name, value in documents.items():
        (ROOT / name).write_text(value, encoding='utf8')
    (ROOT / 'PROGRESS.md').write_bytes(old_progress + append)
    require((ROOT / 'PROGRESS.md').read_bytes().startswith(old_progress), 'PROGRESS history was rewritten')
    require(all(digest(Path(path)) == value for path, value in protected_hashes.items()), 'TASK/user text was changed')
    require(json_bytes(read(ROOT / 'INPUTS.json')['inputs']) == original_rows, 'Original input row values changed')
    checkpoint['preserved_documents'] = history
    checkpoint['protected_text_sha256'] = protected_hashes
    checkpoint['current_document_sha256'] = {name: digest(ROOT / name) for name in [*documents, 'PROGRESS.md']}
    checkpoint['invocation'] = {name: str(value) if isinstance(value, Path) else [str(v) for v in value] if isinstance(value, list) else value for name, value in vars(args).items()}
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_bytes(json_bytes(checkpoint))
    print(json.dumps({'status': checkpoint['status'], 'production_jar_sha256': proof['sha'], 'native_tests': proof['native'], 'core_tests': proof['core'], 'report': str(args.report), 'user_review': 'PENDING_USER_REVIEW', 'full_task_status': 'NOT_READY_FULL_TASK'}))


if __name__ == '__main__':
    main()
