"""Preserve the complete V8 user review and append it without replacing prior tasks."""
import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = Path('C:/Users/vakir/.codex/attachments/2241d3f4-3539-4c50-bc28-b8c46420a498/Вставленный текст.txt')
MARKER = '## Полное дополнение пользователя после review V8 / prototype.3'


def main():
    raw = SOURCE.read_bytes()
    request = raw.decode('utf8').replace('\r\n', '\n')
    destination = ROOT / 'reports/user-review-v8'
    destination.mkdir(parents=True, exist_ok=True)
    archived = destination / 'request.txt'
    if archived.exists():
        assert archived.read_bytes() == raw
    else:
        archived.write_bytes(raw)
    for name in ['TASK.md', 'PROGRESS.md']:
        path = ROOT / name
        before = path.read_text(encoding='utf8')
        if MARKER not in before:
            path.write_text(before + '\n\n' + MARKER + '\n\n' + request + '\n', encoding='utf8')
        assert request in path.read_text(encoding='utf8')
    status = {
        'schema': 'dreamwalker-user-review-v8-v1',
        'request_source': str(SOURCE), 'request_sha256': hashlib.sha256(raw).hexdigest(),
        'reviewed_production_sha256': '6cf52a3c91426e9ec3cf050d6949b94ebcc49e6168b6f497bb75aebacf174316',
        'request_archived_byte_exact': True, 'task_and_progress_complete_request_preserved': True,
        'status': 'V9_IMPLEMENTATION_IN_PROGRESS', 'next_version': '0.1.0-prototype.4',
        'review_acceptance': 'OTHER_REVIEWED_ARCHITECTURE_ACCEPTED_LISTED_EXCEPTIONS_REQUIRE_CORRECTION',
        'flat_tree_variant1': 'USER_ACCEPTED_ONLY_ORDINARY_EXECUTION',
        'full_rp_catalogue': 'NOT_ACCEPTED', 'full_task_status': 'NOT_READY_FULL_TASK',
        'mass_city_conversion': 'NOT_RUN_USER_GATE',
        'priority': 'Later explicit changes override earlier rules; complete prior task preserved',
    }
    (destination / 'manifest.json').write_text(json.dumps(status, ensure_ascii=False, indent=2) + '\n', encoding='utf8')
    progress = ROOT / 'PROGRESS.md'
    text = progress.read_text(encoding='utf8')
    heading = '## Рабочий этап V9 / prototype.4'
    if heading not in text:
        text += '\n\n' + heading + '\n\nV8 review SHA6cf52 получен от пользователя. Остальные проверенные архитектурные образцы приняты; исключения и новые общие правила полностью записаны выше. Реализация и проверки V9 ещё не завершены. Старые PASS относятся только к V8; к prototype.4 они не переносятся. Следующий небольшой комплект требует отдельного JAR/SHA, исходников, ID migration table, ordinary-item/RP/tool/collision/save/restart проверок. Полное задание NOT_READY_FULL_TASK; массовый город/галерея не выдаются за готовые.\n'
        progress.write_text(text, encoding='utf8')
    print(json.dumps({'status': status['status'], 'request_sha256': status['request_sha256'], 'complete_request_preserved': True}))


if __name__ == '__main__':
    main()
