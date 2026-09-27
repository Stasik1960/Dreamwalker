# Bloodborne Blocks — RELEASE_READY: FAIL

2026-09-27. Версия **2.1.0-rc.1**, ветка `codex/bloodborne-release-audit`.
Controlled retirement выполнен и независимо проверен. Полная конвертация
известных объектов остаётся неполной; release-карта и production-релиз не готовы.
Runtime baseline: `b086e88929a971a2abd184629b3e7a59225304e5`.
Проверки новых инструментов выполнены от parent commit
`b6e757067c46343447cea423f67b7bb84ed9c231` с точными изменениями из
[source manifest](release/evidence/retirement-rc1/verification/source-snapshot.json).
Java и runtime resources rc.1 не менялись; экспериментальный repair runtime не переносился.

| Gate | Статус | Доказательство / ограничение |
|---|---|---|
| TEST_SCOPE_PASS | PASS | 219 Python tests / 38 запусков и Java/data checks; [scope](release/evidence/retirement-rc1/verification/test-scope.json) |
| STATIC_PASS | PASS | Свежие `check build checkReleaseVersion`; [лог](release/evidence/retirement-rc1/verification/check-build-gametest.log), [package](release/evidence/retirement-rc1/verification/package.json) |
| GAMETEST_PASS | PASS | 37/37; [свежий XML](release/evidence/retirement-rc1/verification/TEST-logical-gametest.xml) |
| DEDICATED_RESTART_PASS | NOT_RUN | Нет полностью конвертированного мира, допустимого для rc.1 |
| CLIENT_VISUAL_PASS | NOT_RUN | Fresh/reload/restart/interactions/chunk-boundary QA не проводился |
| FULL_CITY_PASS | FAIL | 112 отклонённых атомарных групп; 1 979 неполных защищённых объектов; 39 996 ID вне registry проверенного repair runtime |
| RELEASE_READY | FAIL | World/runtime gates не пройдены; публикация запрещена |

## Controlled retirement

**BB-COMPOSITE-INPUT закрыт** после independent verifier PASS. Пользователь
явно разрешил удалить только 23 отсутствующих composite ID в 33 точных клетках.
Это **intentional retirement**, а не восстановление: все 33 клетки заменены
только на `minecraft:air`; допускаются визуальные пустоты. Старый `--recover-city`
не применялся. Дополнительных удалений, изменений соседних блоков или helpers нет.

- Неизменный исходный ZIP SHA-256:
  `c517dfeb52c4d13bdbe90e02a93ac00416354eb89313a9d1377f24823af6d0e9`.
- Новая копия `Bloodborne-MODDED-retired-composites-rc1.zip` SHA-256:
  `aed638e52143522abfbb3c63b85f377726727a5af039f532417ae0c6772504d1`.
- Проверены все 10 009 chunks; изменены ровно 33 клетки / 21 chunk / 4 regions.
  Нецелевой typed NBT неизменён; все 170 nonterrain-файлов побайтно идентичны.
- Все 23 ID отсутствуют, unknown palette IDs по исходным frozen/current схемам — 0;
  orphan helpers — 0 из 15 759 проверенных. Helper removals — 0.
- Повторный запуск — 0 изменений; все 186 файлов и ledger побайтно одинаковы.

[Все 23 ID и 33 координаты](release/RETIREMENT.md),
[ledger old state → air](release/evidence/retirement-rc1/retirement-ledger.json),
[независимый proof](release/evidence/retirement-rc1/summary.json).

**Архив после retirement остаётся legacy-картой до конвертации.** 43 634 его
ID отсутствуют в rc.1 registry. Helper proof использует исходную legacy geometry;
он явно отмечен `notRuntimeCompatibilityProof: true`. Загружать эту копию с rc.1 нельзя.

## Полная конвертация

Существующий whole-owner engine из отдельной локальной repair-линии запущен на
очищенном архиве, с записью в ещё одну новую копию. Никакого поклеточного fallback
для известных объектов, recovery или ослабления foreign-block guards не было.

26 804 транзакции применены; из 3 238 атомарных групп 3 126 конвертированы,
112 отклонены. Более строгая независимая проверка даёт 137 неполных групп и
1 979 неполных защищённых объектов; 933 технические принадлежности клеток
не доказаны. Это отдельная проблема известных объектов, а не возобновлённый
запрет на согласованное удаление 23 ID.

В диагностическом результате остаются 39 996 palette IDs вне target registry.
Проверка 59 270 helpers нашла 0 orphans; посторонние изменения вне ledger не
найдены, все 170 nonterrain-файлов сохранены. Второй проход меняет 0 клеток,
все 186 файлов побайтно совпадают. Все 33 retired клетки остаются воздухом.
Но это не устраняет fragmentation/registry FAIL и не доказывает нулевой protected loss.

Новый blocker **BB-WHOLE-OWNER**: нужны достоверные текущие owner/composition
bindings для известных неполных групп, затем безопасное исправление root/foreign
конфликтов и трёх runtime mappings. Дополнительное удаление клеток не разрешено.
Подробные причины и границы исторического oracle:
[FULL-CITY-DIAGNOSTIC.md](release/FULL-CITY-DIAGNOSTIC.md),
[свежие отчёты и команды](release/evidence/retirement-rc1/atomic/summary.json).

## Артефакты и оставшийся QA

Локальная проверочная сборка, **не release package**:

- `build/libs/bloodborne-blocks-2.1.0-rc.1.jar`, SHA-256
  `dec76327fba9db0e699aff75168ce1d533e34bd5da5c7fca98437008d846505d`.
- `build/libs/bloodborne-blocks-2.1.0-rc.1-sources.jar`, SHA-256
  `fc1e6b7509c83241e3b226bb5d92ce8da8af62b17b4529ae987f3dfeb232d8a9`.

Настоящие dedicated fresh/save/restart/load и client fresh/reload/restart,
place/break/use дверей, окон, лестниц, сидений, attachments, переходы чанков —
NOT_RUN на итоговом городе. Startup time, peak RAM, reload time, FPS и TPS —
NOT_MEASURED. GameTests не подменяют эти проверки.

Новый GitHub CI run для этих изменений **не запускался**: push до обязательных
PASS запрещён пользователем. Локальные команды CI прошли. Предыдущий
[run 36336948341](https://github.com/Stasik1960/Dreamwalker/actions/runs/36336948341)
был успешен для `782473a7c57e8cd303de57223f9c3f3e075b5d26`, но не проверяет новые
retirement tools и не является текущим CI proof.

Merge в main, push, release tag и публикация **не выполнялись**.
Полный machine-readable статус: [status.json](release/status.json).
[Migration guide](release/MIGRATION.md) требует backup старого мира и предупреждает
об intentional retirement. [Комментарии агента](release/AGENT-COMMENTS.md) записаны отдельно.
Историческое evidence сохранено, а не переписано как результат текущего запуска.
