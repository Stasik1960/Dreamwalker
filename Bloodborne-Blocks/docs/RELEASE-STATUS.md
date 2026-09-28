# Bloodborne Blocks 2.1.0-rc.3 — checkpoint

Source commit: `9f32ff5ad2c2c8ecace8533749794da3d12aa36b`. Пара JAR + полная новая копия rc.2:
[каталог](../../releases/Bloodborne-Blocks/README.md). Рабочая ветка
`codex/bloodborne-complete-accepted-repair`; в main комплект не объединён,
release tag не создан. Известные остатки не позволяют объявить полное завершение.

| Gate | Статус | Доказательство / граница |
|---|---|---|
| STATIC_PASS | PASS | check/build, Java/data checks, 333 Python unittest tests / 54 запусков; проверка упакованного JAR, sources и полной карты |
| GAMETEST_PASS | PASS | 59/59, ноль failures/errors/skips |
| SUBSET_PASS | PASS | 989 whole-owner групп; независимые terrain/NBT/non-terrain и repeat проверки |
| COVERAGE_COMPLETENESS_PASS | FAIL | 4 453 known residuals; дополнительный source scope неполон |
| DEDICATED_RESTART_PASS | BLOCKED | Отсутствует полный исходный modpack и настоящая player movement/use приёмка |
| CLIENT_VISUAL_PASS | NOT_RUN | Настоящий клиент, reload/restart, interactions, FPS/RAM не проверены |
| FULL_CITY_PASS | FAIL | Полнота применения ещё не подтверждена |
| RELEASE_READY | BLOCKED | Разрешён checkpoint рабочей ветки, не полный выпуск |

Два чистых запуска упакованного JAR, save-all flush, stop/restart и те же пять точных контрольных block states: PASS. От запуска процесса до Done: 44.391 / 40.266 s. Peak working set: 2533613568 / 2536697856 bytes. Native server Done: 10.307 / 8.565 s. Короткий no-player debug: 20.73 / 20.72 ticks/s (403/404 ticks; это округлённое окно измерения около номинальных 20 TPS). [Логи и manifest](complete-accepted-repair/dedicated/result.json).
Серверная QA работает на отдельной копии с Bloodborne + Fabric API; отсутствующие
сторонние моды не получают runtime PASS. TPS без игроков не означает production
производительность. Публикуемый ZIP не берётся из сохранённого QA-мира.

Независимый census: 36 795 кандидатов; 29 194 уже correct, 3 148 restored,
4 453 unresolved known. 7 207 изменённых клеток, 415 изменённых чанков;
9 594 чанка и 170 non-terrain файлов побайтно сохранены. Unknown/invalid IDs,
orphan helpers, потери чужих roots/block entities: 0. Второй настоящий проход:
0 изменений и все 186 файлов побайтно идентичны.

Принятый runtime и физика 2 914 rc.1 compatibility-блоков сохранены с явными
open-window/grass/retained-wall дополнениями. 23 прежних неизвестных ID не
восстанавливались; ни одна из 33 retired-позиций не менялась этим проходом.

- JAR SHA-256: `a1f306e9e7447090bd07818194eb1c7a579fbd1740cc9f5adbb2d61e8e528213`.
- Новый город SHA-256: `111253971789feea9c016c693c4122a7faee6952c11102c092d73f3475df6150`.
- Входной rc.2 SHA-256: `f4b9ef510e2aacade80bb11f95cd82fe17eaed56e118280e8e055dd4aecd133c`.

GitHub CI: **PASS**, [run 36444685868](https://github.com/Stasik1960/Dreamwalker/actions/runs/36444685868) для
`95892241bc7fb58f24de509c2d44e26c98ed78c2`: 333 Python tests / 54 запусков, 59/59 GameTests,
Linux build и независимая перепроверка опубликованной пары JAR+карта.
[CI evidence](complete-accepted-repair/ci.json) ·
[полный лог](complete-accepted-repair/ci-36444685868.log.gz).
Все три опубликованных файла скачаны заново; SHA-256 совпали:
[проверка скачивания](complete-accepted-repair/remote-download-verification.json).
Финальный commit отчёта меняет только документацию/доказательства;
проверенные runtime/resources, JAR и карта не изменены.

[status.json](release/status.json) · [delivery](complete-accepted-repair/delivery.json) ·
[сборка](complete-accepted-repair/final-check.log) · [package proof](complete-accepted-repair/package-check.json) ·
[GameTest XML](complete-accepted-repair/TEST-logical-gametest.xml) ·
[таблица семейств/координаты](complete-accepted-repair/REPORT.md) ·
[комментарии агента](complete-accepted-repair/AGENT-COMMENTS.md) ·
[миграция/rollback](complete-accepted-repair/MIGRATION.md).
