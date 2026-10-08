"""Refresh owned RP current-status prose from completed compact reports only.

No game launch, world decoding, ZIP validation, input mutation or source hashing.
Historical candidate11 reports and the saved document copies remain unchanged.
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SHA = 'ed43404d01b2b861a378bdb7dae6d57f36cccd6554461cfaeafbb8bccc82e1d8'


def load(path):
    return json.loads((ROOT / path).read_text(encoding='utf-8'))


def main():
    ordinary = []
    for profile in ['MINIMAL', 'FULL']:
        for phase in ['AUTHOR', 'GAMEPLAY_REOPEN', 'PRODUCTION_REOPEN']:
            name = f'reports/FIRST_SET_{profile}_{phase}_V9_RELEASE9.json'
            wrapper = load(name)
            if wrapper['artifact_sha256'] != SHA or wrapper['exit_code'] != 0 or wrapper['status'] != 'PASS':
                raise ValueError('Current ordinary wrapper is not completed PASS: ' + name)
            gameplay = wrapper.get('ordinary_gameplay_output') or {}
            raw = gameplay.get('result') or {}
            ordinary.append({'wrapper': name, 'status': wrapper['status'], 'exit_code': wrapper['exit_code'],
                             'artifact_sha256': SHA, 'profile': wrapper['profile'], 'phase': phase,
                             'root_jars_excluding_combined_artifact': len(wrapper['modset']),
                             'root_jars_including_combined_artifact': len(wrapper['modset']) + 1,
                             'qa_gameplay': {'path': gameplay.get('path'), 'sha256': gameplay.get('sha256'),
                                             'status': raw.get('status'), 'actualMovementProbes': raw.get('actualMovementProbes', [])}})
    independent_names = [f'reports/REVIEW_V9_{scope}_INDEPENDENT_{profile}_RELEASE9.json'
                         for profile in ['MINIMAL', 'FULL'] for scope in ['SCENE', 'GAMEPLAY']]
    independent = [{'path': name, 'status': load(name)['status']} for name in independent_names]
    if any(not row['status'].startswith('PASS_') for row in independent):
        raise ValueError('Current independent ordinary report did not PASS')
    full = load('reports/CLIENT_FULL_KAPPA_V9_RELEASE11.json')
    proof = load('reports/RP_DIAGNOSTICS_FULL_CLIENT_V9_RELEASE11.json')
    if full['artifact_sha256'] != SHA or full['exit_code'] != 0 or full['status'] != 'PASS_CLIENT_WORLD_LOAD_SAVE_NORMAL_EXIT':
        raise ValueError('Current full client did not complete normally')
    iris = full['client_review_output']['result']['irisRuntime']
    options = iris['effectiveOptions']
    if iris['status'] != 'PASS_ACTIVE_IRIS_RENDERING_PIPELINE' or not iris['frameCounterAdvanced'] or len(options) != 42 or not all(row['matches'] for row in options):
        raise ValueError('Actual strict Iris/42-options/counter proof missing')
    if proof['production_jar_sha256'] != SHA or proof['client_timing_retention']['status'] != 'PASS_CURRENT_ACCEPTED_WIRE_AND_FULL_BOUNDED_LOCAL_SAMPLED_TIMINGS':
        raise ValueError('Actual fixed client timing retention proof missing')
    source = load('reports/FIRST_SET_SOURCE_REVIEW_SCENE_V9_RELEASE9.json')
    if source['artifact_sha256'] != SHA or not source['status'].startswith('PASS_ARCHIVE_EXACT_PRODUCTION_REOPEN'):
        raise ValueError('Current source archive did not PASS')
    dedicated = []
    for mode in ['OFF', 'ON', 'REENTER']:
        name = f'reports/DIAGNOSTICS_SERVER_{mode}_V9_RELEASE9.json'
        if not (ROOT / name).exists():
            continue
        wrapper = load(name)
        if wrapper.get('status') != 'PASS' or wrapper.get('exit_code') != 0 or wrapper.get('artifact_sha256') != SHA:
            continue
        raw = wrapper['diagnostics_review']['result']
        dedicated.append({'wrapper': name, 'status': wrapper['status'], 'exit_code': 0,
                          'raw_status': raw['status'], 'mode': raw['mode'], 'default_seconds': raw['defaultSeconds'],
                          'session_id': raw.get('sessionId', 'NONE'), 'stop_reason': raw['diagnosticStatus']['stopReason'],
                          'actual_save': raw['actualSave'], 'balanced_target_state_equal': raw['balancedTargetStateBefore'] == raw['balancedTargetStateAfter'],
                          'reenter_persisted_identity_equality': raw.get('reenterPersistedIdentityEquality', 'NOT_APPLICABLE'),
                          'checks_passed': raw['checksPassed']})
    dedicated_note = ('Три current dedicated DIAG9 процесса фактически завершились normal exit0/PASS: OFF остался выключенным, ON запросил default60 и завершился AUTO_DURATION_EXPIRED, REENTER повторно загрузил saved world и остановлен OPERATOR_STOP после8s. Actual save/typed target equality подтверждены raw QA; reports/DIAGNOSTICS_SERVER_{OFF,ON,REENTER}_V9_RELEASE9.json. Новый общий independent default60/client/reentry gate ещё PENDING; minimal10/reenter10 выполняются отдельно.'
                      if len(dedicated) == 3 else 'Dedicated default60/minimal/reenter candidate12 пока ожидают отдельного общего gate и не наследуют старые PASS.')
    final = None
    final_path = 'reports/REVIEW_V9_DIAGNOSTICS_INDEPENDENT_RELEASE9.json'
    if (ROOT / final_path).exists():
        final = load(final_path)
        if (final['production_jar_sha256'] != SHA or final['status'] != 'PASS_CURRENT_ARTIFACT_DIAGNOSTICS_ACTUAL_RUNTIME_OFF_ON_REENTER_EXPORTS'
                or not final['client_timing_retention_required']
                or final['client_timing_retention']['status'] != 'PASS_CURRENT_WIRE_AND_LOCAL_RETENTION_BOTH_ACTUAL_CLIENT_RUNS'):
            raise ValueError('Current final diagnostics proof binding/retention differs')
        dedicated_note = ('Три dedicated DIAG9 процесса фактически завершились normal exit0/PASS: OFF остался выключенным, ON запросил default60 и завершился AUTO_DURATION_EXPIRED, REENTER повторно загрузил saved world и остановлен OPERATOR_STOP после8s. Actual save/typed target equality подтверждены. MIN10 и REENTER10 завершились normal world-save/exit0; общий independent gate reports/REVIEW_V9_DIAGNOSTICS_INDEPENDENT_RELEASE9.json фактически PASS на этом SHA, включая обязательное новое wire/local timing retention обоих клиентов, читаемые same-session ZIP, default60, сохранение и повторный вход.')
    ordinary_report = {'schema': 'dreamwalker-rp-v9-current-ordinary-runtime-v1',
                       'status': 'PASS_CURRENT_MINIMAL_FULL_AUTHOR_QA_AND_PRODUCTION_REOPEN',
                       'production_artifact_sha256': SHA, 'phases': ordinary, 'independent_reports': independent,
                       'scope': 'Actual remapped artifact in isolated worlds. Server movement probes use QA survival actors, not a human client. Physical JAR counts are separate from Fabric container counts.',
                       'manual_visual_gameplay': 'PENDING_USER_REVIEW', 'full_catalogue_acceptance': False}
    (ROOT / 'reports/RP_V9_ORDINARY_RUNTIME_RELEASE9.json').write_text(json.dumps(ordinary_report, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    outcomes_path = ROOT / 'reports/RP_V9_RUNTIME_OUTCOMES_CANDIDATE12.json'
    outcomes = load('reports/RP_V9_RUNTIME_OUTCOMES_CANDIDATE12.json')
    outcomes['ordinaryRuntime'] = {'status': ordinary_report['status'], 'report': 'reports/RP_V9_ORDINARY_RUNTIME_RELEASE9.json',
                                   'source_archive_report': 'reports/FIRST_SET_SOURCE_REVIEW_SCENE_V9_RELEASE9.json',
                                   'source_archive_sha256': source['archive_sha256'],
                                   'historicalCandidate11DoesNotGateNewArtifact': True}
    outcomes['actualFullClient'] = {'wrapper': 'reports/CLIENT_FULL_KAPPA_V9_RELEASE11.json', 'status': full['status'],
                                    'exit_code': 0, 'strictIrisStatus': iris['status'], 'effectiveOptionsMatched': 42,
                                    'frameCounterInitial': iris['initial']['frameCounter'], 'frameCounterFinal': iris['final']['frameCounter'],
                                    'loadedRpProbeStatus': proof['actual_loaded_rp_probe']['status'],
                                    'scope': 'One combined full109/Kappa run at6GiB; not separate plain-full runtime or4GiB proof.'}
    outcomes['currentDiagnosticBudgetFix'].update({'actualFixedTimingRetention': proof['client_timing_retention']['status'],
                                                  'actualClientReport': 'reports/RP_DIAGNOSTICS_FULL_CLIENT_V9_RELEASE11.json',
                                                  'verifierSyntheticTests': 14,
                                                  'dedicatedDefault60': 'PASS_ACTUAL_SERVER_OFF_ON_REENTER' if len(dedicated) == 3 else 'PENDING',
                                                  'dedicatedRuntimePhases': dedicated,
                                                  'minimalReenterAndIndependentFinalGate': final['status'] if final else 'PENDING_CURRENT_CANDIDATE12_RUNTIME',
                                                  'independentFinalReport': final_path if final else None})
    outcomes_path.write_text(json.dumps(outcomes, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    implementation = {'schema': 'dreamwalker-rp-diagnostics-current-implementation-evidence-v1',
                      'production_artifact_sha256': SHA, 'native_attempt': 12, 'native_tests': 105,
                      'rp_native_cases': 12, 'core_checks': 20, 'ordinary_report': 'reports/RP_V9_ORDINARY_RUNTIME_RELEASE9.json',
                      'source_archive_report': 'reports/FIRST_SET_SOURCE_REVIEW_SCENE_V9_RELEASE9.json',
                      'dedicated_actual_phases': dedicated,
                      'actual_full_client_report': 'reports/RP_DIAGNOSTICS_FULL_CLIENT_V9_RELEASE11.json',
                      'actual_full_client_timing_retention': proof['client_timing_retention'],
                      'matched_sequential_full_windows': proof['timings'],
                      'new_minimal_reentry_and_final_independent_gate': final['status'] if final else 'PENDING',
                      'final_independent_report': final_path if final else None,
                      'new_rp_item_placement_server_renderer_chain': 'NOT_PROBED',
                      'gpu_duration': 'NOT_MEASURED', 'all_mod_network_traffic': 'NOT_MEASURED',
                      'manual_visual_gameplay': 'PENDING_USER_REVIEW', 'full_catalogue_acceptance': False,
                      'sampling_scope': 'Bounded sampled invocation elapsed intervals; nested/window/session views overlap. They are not unique CPU or FPS shares.',
                      'historical_candidate11_reports_unchanged': True}
    if final:
        implementation.update({'final_diagnostics_coverage': final['coverage'], 'client_timing_retention_both_actual_runs': final['client_timing_retention'],
                               'dedicated_off_tick_samples': final['dedicated_server']['off']['tick_samples'],
                               'dedicated_on_tick_samples': final['dedicated_server']['on']['tick_samples'],
                               'own_server_partial_inclusive_hooks': final['dedicated_server']['on']['export']['measurements']['diagnosticsOwnSynchronousOverhead'],
                               'own_server_hook_scope': final['dedicated_server']['on']['export']['measurements']['diagnosticsOwnOverheadScope'],
                               'actual_minimal_matched_windows': final['client']['timings'], 'actual_reentry_matched_windows': final['client_reenter']['timings'],
                               'actual_joined_player_setup': {key: final[key]['actual_joined_player_setup'] for key in ['client', 'client_reenter']},
                               'bounded_client_persistence': {key: {name: final[key]['persistence'][name] for name in ['status', 'architecture_roots', 'full_typed_block_entities', 'scope']} for key in ['client', 'client_reenter']}})
    (ROOT / 'reports/RP_DIAGNOSTICS_V9_IMPLEMENTATION_CANDIDATE12.json').write_text(json.dumps(implementation, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    final_note = ''
    if final:
        metric_rows = []
        for key, label in [('off', 'Dedicated9 OFF'), ('on', 'Dedicated9 ON')]:
            row = final['dedicated_server'][key]['tick_samples']
            metric_rows.append(f"|{label}|{row['samples']}|{row['mean_ns']/1e6:.4f}|{row['p95_ns']/1e6:.4f}|{row['p99_ns']/1e6:.4f}|")
        for key, label in [('client', 'MIN10'), ('client_reenter', 'REENTER10')]:
            for row in final[key]['timings']:
                metric_rows.append(f"|{label} {row['label']}|{row['samples']}|{row['mean_ns']/1e6:.4f}|{row['p95_ns']/1e6:.4f}|{row['p99_ns']/1e6:.4f}|")
        own = final['dedicated_server']['on']['export']['measurements']['diagnosticsOwnSynchronousOverhead']
        final_note = '''### Итоговый diagnostics gate RELEASE9

`reports/REVIEW_V9_DIAGNOSTICS_INDEPENDENT_RELEASE9.json` связывает exact frozen12 с actual dedicated OFF9/ON9/REENTER9 и actual MIN10/REENTER10. Вошедший игрок был Survival; QA явно установил Creative до всех OFF/ON/OFF окон и после них вернул Survival.90010 genuine item context совпадает с COMMITTED UUID/operation и фактически выбранной renderer-моделью, затем обычный creative break удалил тот же owner. Cleanup восстановил native state/BE и удалил его ledger contribution. Bounded сохранение подтверждено для102 существующих roots,446 full typed BEs и18 RP identity/pose/roles/provenance, а также DW/RP saved graph data; разрешённые vanilla timers перечислены отдельно. Это не byte-equality всего мира.

MIN10 сохраняет19 локальных окон/59 sections/12572 sampled invocations,10829 raw retained/1743 raw dropped; REENTER10 —19 окон/58 sections/12355 invocations,10693 retained/1662 dropped. Все wire batches имеют actual timing rows, exact compact SHA и остаются внутри32 fields/16384 chars/60000 UTF-8 bytes; wire-omitted observations сохранены локально. Window/session/tail counts reconciled without double-counting. Same-session local client/server ZIP и Original state preservation прошли независимый gate. Новый RP placement ACK остаётся NOT_PROBED; текущая реальная цепочка90010 принадлежит архитектуре.

|Текущий процесс / окно|Выборок|Среднее ms|p95 ms|p99 ms|
|---|---:|---:|---:|---:|
''' + '\n'.join(metric_rows) + f'''

Dedicated ON−OFF mean difference {((final['dedicated_server']['on']['tick_samples']['mean_ns']-final['dedicated_server']['off']['tick_samples']['mean_ns'])/1e6):.4f}ms — наблюдение двух контролируемых запусков, не точная причинная стоимость recorder или объекта. Own server hooks записали{own['allCount']} inclusive/nested samples: allMean{own['allMeanNs']/1e6:.4f}ms, allMax{own['allMaxNs']/1e6:.4f}ms; bounded retained{own['retained']} имеют p95{own['p95Ns']/1e6:.4f}ms/p99{own['p99Ns']/1e6:.4f}ms, dropped{own['dropped']}. Сюда входят session/JFR start-stop и пересекающиеся API, поэтому это не unique CPU и не сумма независимых расходов. Client presentation intervals включают wait/scheduling; GPU NOT_MEASURED.

'''
    timeline_note = ''
    timeline_path = 'reports/RP_DIAGNOSTICS_FULL_CLIENT_TIMELINE_V9_RELEASE11.json'
    if (ROOT / timeline_path).exists():
        timeline = load(timeline_path)
        if timeline['production_artifact_sha256'] != SHA or timeline['session_id'] != proof['session']:
            raise ValueError('Actual full timeline binding differs')
        batch_rows = []
        for row in timeline['batch_timeline'][-5:]:
            batch_rows.append(f"|{row['batch']}|{row['frames']['measurements']}|{row['frames']['averageNs']/1e6:.4f}|{row['wire_timing_groups']}/{row['local_timing_groups']}|{row['this_flush_elapsed_ns_from_next_counter_or_final_export']/1e6:.4f}|")
        flush = timeline['own_batch_flush']
        timeline_note = '''`reports/RP_DIAGNOSTICS_FULL_CLIENT_TIMELINE_V9_RELEASE11.json` воспроизводимо читает именно проверенные ZIP bytes, сверяет session/currentSHA/PID и показывает все56 reporting windows. Последние пять всё ещё активных batch окон:

|Batch|Presentation frames|Средний кадр ms|Wire/local timing groups|Этот flush elapsed ms|
|---|---:|---:|---:|---:|
''' + '\n'.join(batch_rows) + f'''

Own flush56 intervals: allTotal{flush['all_total_ns']/1e6:.4f}ms, mean{flush['all_mean_ns']/1e6:.4f}ms, p95{flush['p95_ns']/1e6:.4f}ms/p99{flush['p99_ns']/1e6:.4f}ms. Counter captured before each flush; next counter/final export reconstruct its elapsed interval and total exactly. Early active batches2–10 have weighted frame mean67.9144ms; late active47–56 mean15.3355ms. These active populations are not matched OFF/ON windows or steady-state causal overhead measurements. GPU duration remains NOT_MEASURED.

'''
    shared = f'''## Текущая сборка candidate12

Production SHA256 `{SHA}`, `build/frozen-artifacts/v9-attempt-12/dreamwalker-bb-fabric-1.20.1-0.1.0-prototype.4.jar`. Native Attempt12:105/105, RP12/12, core20/20 PASS; fresh static RP package710/306/293 и production/QA13 separation PASS (`RP_PACKAGE_VERIFICATION_V9_RELEASE9.json`, `REVIEW_PACKAGE_VERIFICATION_V9_QA13_CANDIDATE12.json`). QA13 byte-identical5f5d. Изменён только клиентский диагностический batch budget/локальное сохранение выборок; модели/текстуры/анимации и RP physics не изменены.

Все шесть ordinary RELEASE9 процессов этого JAR завершились normal exit0/PASS: minimal/full AUTHOR → GAMEPLAY_REOPEN → PRODUCTION_REOPEN. Четыре независимые scene/gameplay проверки PASS. Полный профиль с Lithium0.11.2 снова прошёл настоящие рабочие плоскости NPC-окна/ворот, climbing RP-лестницы и native первую ступень stairs: `reports/RP_V9_ORDINARY_RUNTIME_RELEASE9.json`. Это серверные survival QA actors; вошедший клиентский игрок и ручное управление являются отдельными областями. Физически full QA-фазы имеют85 root JAR, production reopen84:83 выбранных зависимостей + combined JAR + отдельный QA лишь в QA-фазах; эти числа не являются Fabric container count.

Current source RELEASE9 прошёл AUTHOR, production REOPEN, persisted NO_OP replay, четыре независимые проверки и архив: `reports/FIRST_SET_SOURCE_REVIEW_SCENE_V9_RELEASE9.json`, SHA256 `{source['archive_sha256']}`. Это41 выбранный объект/110 source-member cells и сохранённые source/generated terrain files; полный город не конвертирован. Wood_window законно отказывает в открытии при пересечении нового физического объёма без изменения typed state/ledger/native surroundings; дверь действительно открывается и пропускает игрока. Исторические FINAL3/source8 bytes не заменены.

Обнаруженный candidate11 FULL9/FULL10 дефект полного mod metadata вытеснял wire timings и многократно пересериализовывал весь JSON. Candidate12 использует компактную network identity, заранее ограниченные секции с приоритетом ACK/timings и отдельный bounded `local-timings.json`; wire и local batch history совпадают. Pure actual-code budget regression и14 synthetic verifier checks PASS. Пересекающиеся per-window/session views нельзя складывать. Настоящий FULL11 проверил новое сохранение выборок. {dedicated_note}

### Actual полный клиент FULL11

`reports/CLIENT_FULL_KAPPA_V9_RELEASE11.json` завершился normal world-save/exit0/PASS на текущем SHA:109 выбранных dependency JAR + combined production + QA,111 физических root JAR, `-Xmx6G`. Это один объединённый full+Kappa запуск. `irisRuntime` фактически подтвердил enabled Kappa_v5.2.zip, отсутствие fallback, IrisRenderingPipeline, все42 effective options и рост frameCounter0→2978. Успех полного профиля при4GiB не доказан.10 загруженных RP-представителей прошли actual model/tracker/bone-pose checks; новая обычная RP item→server→model цепочка остаётся NOT_PROBED.

`reports/RP_DIAGNOSTICS_FULL_CLIENT_V9_RELEASE11.json` проверил настоящие client/integrated server ZIP одной сессии `{proof['session']}`: текущий SHA, читаемый UTF-8 summary,90010 ordinary item/server operation/actual renderer ACK, typed native/ledger cleanup и OFF/ON/OFF. Все56 wire batches имеют timings;56 полных локальных окон сохраняют60 session sections,19372 sampled invocations,16847 retained raw samples/2525 dropped raw samples при лимите512/section.10427 sampled invocations не поместились в wire, но остались в bounded local population. Их нельзя объявлять потерянными измерениями или складывать с session/window views. Full client ZIP и same-session integrated ZIP включаются в primary evidence автоматически. Этот optional полный клиент дополняет обязательный dedicated/minimal/reenter gate. Compact current evidence: `reports/RP_DIAGNOSTICS_V9_IMPLEMENTATION_CANDIDATE12.json`.

|FULL11 sequential окно|Выборок|Среднее кадра ms|p95 ms|p99 ms|Среднее render-thread CPU ms|
|---|---:|---:|---:|---:|---:|
|OFF_BEFORE|117|42.2314|66.3384|75.3506|38.3280|
|ON_IDENTICAL_CLEANED_SCENE|76|63.5106|103.0441|200.1263|58.7993|
|OFF_AFTER|301|16.5279|21.1826|26.6361|16.4037|

Это холодные последовательные окна одного запуска; позже при всё ещё активной записи batches52–56 показали средние17.7228,16.5010,14.9747,13.7014,14.7065ms. Наблюдаемое восстановление не доказывает причинную стоимость recorder, объектов или GPU. Собственные56 sendBatch flush интервалов дали115.9155ms cumulative elapsed за сессию; это wall interval, не CPU. Отдельный IO worker CPU counter281.25ms относится ко всему времени процесса и включает metadata/export/JFR close. Client/integrated server имеют один PID99652, поэтому их heap/GC/process CPU нельзя складывать. Actual collector-count deltas56 batch окон суммируются в36; JFR GCPhasePause имеет52 pause events/959.5637ms плюс отдельный partial tail1/18.9428ms. Cycle-count/duration и JFR pauses описывают пересекающуюся работу и не складываются. JVM thread-CPU clock даёт12 допустимых нулевых deltas в OFF_AFTER; это не нулевые presentation frames.

{timeline_note}{final_note}
GPU duration, all-mod traffic, unique per-object CPU/FPS attribution, mass-city performance и ручная visual/gameplay acceptance не проверены. Принятый RP tree1 и исходные PNG/geo/animation bytes не изменены.

## Исторические candidate11 результаты и описание неизменённого поведения

Ниже текст относится к **candidate11** и его исходному SHA8dd5, а не к текущим runtime gates. Историческая копия этого документа и raw reports сохранены отдельно; цифры FULL9/FULL10 получены дефектным recorder и не являются текущими FULL11 измерениями.

'''
    for name in ['docs/RP_REVIEW_V9.md', 'docs/RP_DIAGNOSTICS_V9.md']:
        path = ROOT / name
        text = path.read_text(encoding='utf-8')
        body = text[text.index('Основание'):]
        title = text.splitlines()[0]
        path.write_text(title + '\n\n' + shared + body, encoding='utf-8', newline='\n')
    print('PASS lightweight current12 ordinary/source/full11 owned RP status refresh; historical aliases unchanged.')


if __name__ == '__main__':
    main()
