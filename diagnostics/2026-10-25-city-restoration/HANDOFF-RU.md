# Продолжение текущего восстановления

Статус: работа продолжается. Не путать резервный релиз с готовой исправленной картой.

Проект: `C:\Users\vakir\CodexProjects\DW-visual-fixes-20261025`. Доказательства: `restoration-evidence`. Ветка GitHub: `codex/city-restoration-20261025`, репозиторий `Stasik1960/Dreamwalker`. Зеркало публикации находится в `DW/audits/Dreamwalker-BB/2026-10-25-visual-diagnostics/github-publication`; не сбрасывать исходный DW с массовыми старыми удалениями.

Прочитать TASK-RU.md, DECISIONS-RU.md и plan.json. Не перезаписывать исходную резервную копию. `world-before.json` содержит точный манифест всех 3934 исходных файлов. `cleanup-result.json`: уже удалено 2132 пустых файла (455464 байта), индекс owner shards сокращён 3694→1638. Первую очистку повторно не запускать.

Мод целевой версии `0.1.0-v12.7.4-visual-physics.6`. Исходная `.5` опубликована ранее, но в реальной папке Limacina пользователь продолжал использовать `.2`. После проверки установить новую сборку в Limacina/mods и Desktop/client_mods, Desktop/server_mods, сохранив старые DW JAR отдельно. Остальные моды не менять.

Последовательность оставшейся работы:

1. `tools/run_fix_gradle.py restorationBuild` — журнал и результат в build-tools/gradle-restorationBuild.*. Исправлена тестовая ошибка доступа к package-private конструктору ledger; тест использует отражение без расширения production API.
2. `tools/run_restoration_profile.py 2` — отдельный сервер с WorldEdit7.2.15, heap3G. Предыдущий server-1 PASS: creative/survival/cancel compact removal, setBlocks/undo/redo, precise RP miss. Новые проверки: настоящий parser hand, native art14/25636 и вариант14, цельный столб/фонарь/удаление пустого shard, v12-bounds.json.
3. После успеха `tools/run_restoration_apply.py` — непосредственно Desktop/bloodborne, без WorldEdit, heap3G. План post-replacement-plan.json (172) и lantern-replacement-plan.json (562). map-apply.json сохраняет прогресс и индивидуальные результаты. Не повторять успешно выполненные замены. При аварии сначала прочитать отчёт/фактический мир, затем создать адресный остаточный план.
4. `tools/run_restoration_client.py 3 --repaired` — отдельная копия уже исправленной карты, actual Limacina моды + новая DW, Sodium/Indium вместе, без серверного WorldEdit. Heap4G, shaders off. Скриншоты пяти ракурсов столба/окна, получение sway-пакета. Запускать только после остановки сервера/Gradle. Предыдущий client-2 загрузился без краша, стекло видно, 399с весь запуск; это промежуточная проверка, не финальная.
5. Проверить сохранность пользовательских/v12 данных, сохранение/повторное открытие, остаточные пустые файлы, затем пакетировать полные исходники/мод/пользовательскую карту/отчёты. Опубликовать новый финальный релиз с SHA256 и ссылками. Не загружать native meshes.bin как Git blob: он входит в полную source ZIP.

Все процессы — через Guard.ps1 -Mode Operation. Guard не передаёт stdout/exit дочернего процесса: проверять JSON receipts и журналы. Чтение storage-mode.json/C-return-result.txt и Guard -Mode Check обязательно. Никаких работ на E:.
