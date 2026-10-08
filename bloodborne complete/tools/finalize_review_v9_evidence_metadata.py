"""Record completed current runtime proofs without modifying original inputs or requests."""
from pathlib import Path
import hashlib
import json

ROOT = Path(__file__).resolve().parents[1]
SHA = 'ed43404d01b2b861a378bdb7dae6d57f36cccd6554461cfaeafbb8bccc82e1d8'


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def read(path):
    return json.loads((ROOT / path).read_text(encoding='utf8'))


def write(path, value):
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf8')


def main():
    jar = ROOT / 'build/frozen-artifacts/v9-attempt-12/dreamwalker-bb-fabric-1.20.1-0.1.0-prototype.4.jar'
    assert digest(jar) == SHA
    scene = read('tools/first_set_scene_v9_input.json')
    historical = read('reports/REVIEW_V9_SCENE_INPUT_METADATA_RELEASE5.json')
    inputs = historical['currentInputs']
    assert all(digest(ROOT / row['path']) == row['sha256'] for row in inputs)
    assert (scene['architectureRootCount'], scene['nativeFixtureCount'],
            scene['ordinaryRpItemCount'], scene['rpSceneEntityCount'], scene['reviewStationCount']) == (102, 132, 17, 18, 48)
    reports = []
    for profile in ['MINIMAL', 'FULL']:
        for phase in ['AUTHOR', 'GAMEPLAY_REOPEN', 'PRODUCTION_REOPEN']:
            path = ROOT / f'reports/FIRST_SET_{profile}_{phase}_V9_RELEASE9.json'
            report = json.loads(path.read_text(encoding='utf8'))
            assert report['status'] == 'PASS' and report['artifact_sha256'] == SHA
            reports.append({'path': path.relative_to(ROOT).as_posix(), 'sha256': digest(path), 'status': report['status']})
    target = ROOT / 'reports/REVIEW_V9_SCENE_INPUT_METADATA_RELEASE9.json'
    assert not target.exists(), 'Preserve prior metadata; do not silently overwrite it'
    write(target, {
        'schema': 'dreamwalker-review-v9-input-metadata-revision-v1',
        'status': 'PASS_UNCHANGED_REVIEW_INPUTS_BOUND_TO_SIX_CURRENT_ACTUAL_SERVER_PHASES',
        'production_jar_sha256': SHA,
        'historical_preparation': {'path': 'reports/REVIEW_V9_SCENE_INPUT_METADATA_RELEASE5.json',
                                   'sha256': digest(ROOT / 'reports/REVIEW_V9_SCENE_INPUT_METADATA_RELEASE5.json')},
        'currentInputs': inputs, 'actual_runtime_reports': reports,
        'architectureRoots': 102, 'nativeFixtures': 132, 'ordinaryRpItemFixtures': 17,
        'rpSceneEntityCount': 18, 'reviewStations': 48,
        'inputs_modified_by_this_step': False, 'production_or_qa_modified': False,
        'user_review': 'PENDING_USER_REVIEW', 'full_task_status': 'NOT_READY_FULL_TASK',
        'newLampLighting': historical['newLampLighting'],
    })
    diagnostic_path = ROOT / 'reports/REVIEW_V9_DIAGNOSTICS_INDEPENDENT_RELEASE9.json'
    diagnostic = json.loads(diagnostic_path.read_text(encoding='utf8'))
    assert diagnostic['status'] == 'PASS_CURRENT_ARTIFACT_DIAGNOSTICS_ACTUAL_RUNTIME_OFF_ON_REENTER_EXPORTS'
    assert diagnostic['production_jar_sha256'] == SHA and diagnostic['client_timing_retention_required']
    request_dir = ROOT / 'reports/user-diagnostics-request-2026-10-08'
    request = request_dir / 'request.txt'
    assert digest(request) == '22c4ae75e6ec2d2d50e47461e41c9655c39e16c548a17d10cc742126b6895f93'
    request_text = request.read_text(encoding='utf8').replace('\r\n', '\n').rstrip('\n')
    assert all(request_text in (ROOT / name).read_text(encoding='utf8').replace('\r\n', '\n') for name in ['TASK.md', 'PROGRESS.md'])
    manifest_path = request_dir / 'manifest.json'
    previous = manifest_path.read_bytes()
    manifest = json.loads(previous)
    assert manifest['status'] == 'FULL_REQUEST_RECORDED_IMPLEMENTATION_AND_CURRENT_RUNTIME_PROOFS_PENDING'
    history = request_dir / ('manifest-at-request-insertion-' + hashlib.sha256(previous).hexdigest() + '.json')
    assert not history.exists()
    history.write_bytes(previous)
    progress_path = ROOT / 'PROGRESS.md'
    before = progress_path.read_bytes()
    addition = '''

## Встроенная диагностика: текущая сборка V9 / prototype.4 проверена

Передаваемый production JAR: SHA256 `ed43404d01b2b861a378bdb7dae6d57f36cccd6554461cfaeafbb8bccc82e1d8`.
Все восемь пунктов дополнения сохранены дословно выше и в TASK.md; прежние требования не заменены.
Добавлены ограниченный журнал с причинами и счётчиками повторов, OP2 start/status/mark/stop/export с фильтрами,
снимки без изменения объекта через debug и инструмент, события операций, связанные клиентские/серверные ZIP,
FPS/кадры, TPS/тики, память/GC, счётчики объектов/helpers/кэшей/очередей/собственного трафика и выборочные таймеры.
Запись выключена по умолчанию; default60 автоматически завершён в настоящем запуске этого SHA.
Native105/105 и core20 прошли. Шесть ordinary серверных фаз RELEASE9, три исходных SOURCE FINAL4/RELEASE9,
три диагностических серверных фазы RELEASE9 и клиенты MIN10, запись после повторного входа REENTER10,
FULL Kappa11 (6GB, 42 проверенные настройки Iris) завершились штатно с сохранением.
Независимый отчёт: reports/REVIEW_V9_DIAGNOSTICS_INDEPENDENT_RELEASE9.json; дополнительная полная сборка:
reports/RP_DIAGNOSTICS_FULL_CLIENT_V9_RELEASE11.json. Проверены actual item90010 → принятый сервером тип/UUID/root
→ выбранная клиентом модель, удаление временного объекта и точное восстановление native/helper состояния.

Минимальный клиент: средние wall frame OFF/ON/OFF = 8.408/9.105/8.400ms; повторный вход = 8.467/9.197/8.403ms.
Полная сборка с Kappa: ранние OFF/ON/OFF = 42.231/63.511/16.528ms, поздние активные пакеты показывают восстановление
времени кадра при продолжающейся записи. Это наблюдаемые окна, не точная причинная стоимость записи и не GPU.
Полный 60s сеанс: 56 wire пакетов, 115.916ms суммарного elapsed времени flush; локально сохранены 19,372 выборочных
вызова (16,847 raw samples, 2,525 отброшено ограничением). Не переданные по сети вызовы явно посчитаны и
сохранены локально; окна и общие группы перекрываются, поэтому их времена не складываются.
Подробные actual измерения, собственные затраты и недоступные поля записаны в диагностических документах/ZIP.

GPU-время, точный процент FPS конкретного объекта и весь трафик Minecraft не измерены. Ordinary RP placement
→ server → render chain не проверена; это отдельно от проверки ресурсов и уже загруженных RP-объектов.
Ручная визуальная/физическая приёмка PENDING_USER_REVIEW, full-city performance и массовая конвертация NOT_RUN,
полное задание NOT_READY_FULL_TASK. Новое освещение Hunterlamp и дробные верхние крепления native лестницы
остаются указанными ограничениями. Следующий результат — небольшой проверяемый комплект V9, не готовый город.
'''.encode('utf8')
    progress_path.write_bytes(before + addition)
    assert progress_path.read_bytes().startswith(before)
    manifest.update({
        'status': 'IMPLEMENTED_CURRENT_ARTIFACT_ACTUAL_RUNTIME_AND_INDEPENDENT_EXPORTS_VERIFIED_REVIEW_PENDING',
        'production_jar_sha256': SHA,
        'documents_sha256_scope': 'Existing documents rows retain hashes at request insertion; historical bytes are preserved separately.',
        'request_insertion_manifest': history.relative_to(ROOT).as_posix(),
        'request_insertion_manifest_sha256': digest(history),
        'current_diagnostics_evidence': {'path': diagnostic_path.relative_to(ROOT).as_posix(),
                                          'sha256': digest(diagnostic_path), 'status': diagnostic['status']},
        'user_review': 'PENDING_USER_REVIEW', 'full_task_status': 'NOT_READY_FULL_TASK',
    })
    write(manifest_path, manifest)
    print(json.dumps({'status': 'PASS_CURRENT_SCENE_AND_DIAGNOSTICS_METADATA_HISTORY_PRESERVED',
                      'production_jar_sha256': SHA, 'request_sha256': digest(request)}))


if __name__ == '__main__':
    main()
