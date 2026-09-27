# Аудит production readiness — 2026-09-27

> Исторический снимок beta.3 до подготовки `2.1.0-rc.1`. Числа, SHA и выводы
> ниже относятся к этому снимку. Текущие gates — в [RELEASE-STATUS.md](../RELEASE-STATUS.md).
> Для rc.1 выбрана новая идентичность JAR: пять старых class differences
> сохранены как историческое расхождение, но эквивалентность beta.3 больше
> не является условием выпуска rc.1. Разрешение на конвертацию копии теперь
> дано пользователем при выполнении compatibility/whole-owner условий.

Релиз **заблокирован**. Ни `main`, ни TEST3, ни локальная repair-диагностика
не имеют полного набора production-доказательств. Текущее состояние находится
в [RELEASE-STATUS.md](../RELEASE-STATUS.md), машинная версия — в [status.json](status.json).

## Ветки и происхождение

`git fetch --prune` выполнен. Работа изолирована в ветке
`codex/bloodborne-release-audit`, от базового commit `b086e88929a971a2abd184629b3e7a59225304e5`.
Доказательства собраны до фиксации audit-коммита; базовый commit сам по себе не идентифицирует изменения аудита.
Точный набор исходников и ресурсов фиксируют отдельные SHA-манифесты.

| Линия | Commit | Gradle version | JAR / мир | Статус |
|---|---|---|---|---|
| `origin/main` | `b086e88929a971a2abd184629b3e7a59225304e5` | `2.1.0-beta.3` | `releases/Bloodborne-Blocks/2.1.0-beta.3-grid-physics/` | База аудита; production BLOCKED |
| `origin/archive/beta3-grid-fragmentation-broken` | `b086e88929a971a2abd184629b3e7a59225304e5` | `2.1.0-beta.3` | Те же beta.3 JAR / Full-Grid ZIP | Тот же commit; не отдельная стабильная версия |
| `origin/repair/composite-preserving-grid` | `3d07b4890b7612b390fc0d975990bfadd7402cfb` | `2.1.0-repair-test.3` | `Bloodborne-Blocks/releases/repair-test-3/` в исходном checkout | Только TEST3; в main не слита |
| Локальная repair WIP | `3d07b4890b7612b390fc0d975990bfadd7402cfb` + dirty source | `2.1.0-repair-catalog.1` | `Bloodborne-Blocks/releases/repair-catalog-city-1/` в исходном checkout | Каталог/GUI и отдельная проваленная диагностика города |
| `origin/codex/bloodborne-beta-world-20260925` | `419b85eeab56180f0e26272ffc2a2136f6a05a18` | `2.1.0-beta.2` | Исторические beta.2 пакеты в `releases/Bloodborne-Blocks/` | Не проверены как новый release candidate |
| tag `bloodborne-blocks-v2.1.0-beta.1` | `acc0e0dcae1ae895305b704b7c7e6cd145204d61` | `2.1.0-beta.1` | Исторические beta.1 пакеты | Не проверены как новый release candidate |
| `origin/build-groinsword-20260921`, `origin/build-groinsword-v101`…`v106` | `821ee3daea1601ca7798b051d3e5f1720e98e693` | `2.1.0-alpha.1` | Исторические alpha артефакты в `releases/Bloodborne-Blocks/` | Семь refs одного старого commit; не release candidate |

Проверенные байтовые SHA и версии JAR: [main/beta.3](evidence/published-artifacts.json),
[repair](evidence/repair-artifacts.json). Beta.1/beta.2 не объявлены повторно проверенными.
Снимок локальных repair-изменений сделан **во время этого аудита**, а не во время
старой сборки: [repair-wip-snapshot.json](evidence/repair-wip-snapshot.json).
Он не восполняет отсутствующую исходную аттестацию старого dirty build.
Полный список полученных remote refs, включая ветки других модов, сохранён
в [all-remote-refs.json](evidence/all-remote-refs.json); чужие проекты не изменялись.

## Сверка чисел

| Набор | Python unittest | GameTests (XML) | Что он не доказывает |
|---|---:|---:|---|
| Опубликованный beta.3 `check build` | 182 / 34 запуска | 37, failures 0 | Графику, restart, whole-owner city preservation |
| Beta.3 targeted converter suite | 30 / 1 запуск | Не отдельный запуск GameTests | Весь Python discovery |
| Beta.3 полный discovery | 318 / 1 запуск, 9 failures + 8 errors | Не отдельный запуск GameTests | PASS полного набора |
| Beta.3 baseline reproduction | 17 / 1 запуск, 9 failures + 7 errors | — | Исправление этих 16 старых тестов |
| TEST3 | 228 / 43 запуска | 56, failures 0 | Новый пересчёт полного города |
| Локальный catalog WIP | 231 / 44 запуска | 57, failures 0 | Готовность города; GUI в реальном клиенте |
| Свежий аудит main, полный Gradle запуск | 199 / 36 запусков | 37, failures 0 | Полный исторический discovery и runtime QA |
| Финальные узкие release-проверки | 20 / 2 запуска | Не перезапуск GameTests | Производственную готовность |

Полный свежий запуск включал 182 прежних + 15 первоначальных gate + 2 package
теста. После review последние два набора расширены до 16 + 4 и повторены отдельно.
199 и 20 не складываются: это частично перекрывающиеся запуски.
Script-based invariant checks и Java bootstrap checks не включены в unittest-суммы.
Первичные логи/XML и пересчитанные числа: [main](evidence/main-tests.json),
[история repair](evidence/historical-test-counts.json), [beta.3](evidence/published-tests.json).

17 исходных Python ошибок beta.3 и формулировка «16 historical» объясняются
одним transient Windows rename error, прошедшим изолированно, и отдельным
baseline-прогоном с оставшимися 16 ошибками. Это объясняет арифметику, но **не закрывает**
проверку legacy compatibility. Набор старых тестов нельзя объявлять полностью зелёным.

`checkedBlocks=1 003 216 896` и census `checked_cells=983 924 736` считают разные
вещи: `check_logical_world.py` добавляет 4096 на посещённую секцию, а
`check_grid_world.py` считает материализованные массивы секций. Разница —
19 292 160 ячеек = 4710 × 4096. Отдельного доказательства, что каждая из этих
секций именно air, нет; такая интерпретация не используется.

TEST3 ссылается на 6518 исторических protected failures и **не пересчитывает** их.
Локальные 1979 относятся к другому dirty repair-прогону; это не значение `main`
и не исправление, доставленное в `main`. Обе линии всё ещё не имеют нулевого полного gate.
Сохранены [локальный отчёт](evidence/repair-catalog-local/REPORT.md) и исходные числа.

355/3238 и 356/3238 имеют документированное объяснение strict shared-binding
против прежнего scalar checker (`REPAIR-TEST-1.md`). Но per-group diff и полные
payload двух hashed reports не найдены. Точная разница одной группы остаётся
**неразрешённым provenance blocker**; её нельзя округлять или выбирать удобное число.

## Совместимость и измерения до оптимизации

Runtime Java/resources не менялись. `git diff -- src/main` пуст.

| Показатель текущей базы | До | После |
|---|---:|---:|
| Logical families | 49 | 49 |
| Compatibility blocks | 2914 | 2914 |
| Все registry blocks, включая helper | 2964 | 2964 |
| Registry items | 2963 | 2963 |
| Logical + city states | 50276 | 50276 |
| States вместе с единственным helper state | 50277 | 50277 |
| Runtime resource files | 13969 | 13969 |
| Локально собранный JAR, байт | 34498188 | 34498188 |
| Startup / peak RAM / reload / FPS / TPS | NOT_MEASURED | NOT_MEASURED |

Logical states = 1644, city states = 48632. Bootstrap и серверный GameTest
подтверждают эти два набора; `architecture_part` зарегистрирован отдельно без item.
Показатель 13968 в старом package report исключал одну mixin-конфигурацию,
проверявшуюся семантически; полный набор ресурсов — 13969.
Опубликованный beta.3 JAR — 34 477 590 байт и другой SHA. Полное сравнение
нашло 11 598 отличий только в окончаниях строк, без добавленных/удалённых файлов,
и пять отличающихся `.class`. Method signatures совпадают; raw javap diff
содержит constant-pool/debug/offset различия, но остаточные opcode differences
не классифицированы полностью. Это отдельный открытый blocker, а не доказанная
эквивалентность двух JAR. [Сравнение](evidence/published-local-jar-comparison.json),
[сохранённые javap outputs](evidence/jar-class-comparison/index.json).
Время Gradle/GameTest запуска не подставляется вместо production startup/FPS/TPS.

В `logical/migration.json` пусты `rules` и `item_aliases`. Регистрация текущих
families и успешные тесты текущих NBT не доказывают загрузку старых registry IDs,
chunk NBT или ItemStack. Новый код ID не удаляет, но исходный main baseline уже
не даёт доказанного обратного пути. Compatibility result: **BLOCKED**.
До решения этой проблемы registry/resource pruning и оптимизация не выполняются.

## Граница этапа города и дальнейшая проверка

MODDED input: `reference-inputs/latest-modded-world.zip`, SHA-256
`c517dfeb52c4d13bdbe90e02a93ac00416354eb89313a9d1377f24823af6d0e9`.
Файл проверен чтением; новых world copies/conversions в этом задании нет.

Preflight остановлен до simulation: main `--full-grid` применяет cell-local
fallback. Он не предоставляет проверенный whole-owner graph/atomic transfer
для всех city owners. Нельзя представлять такой dry-run как требуемый полный
owner/physical/interaction/helper/foreign transaction plan. Owner graph и полный
transaction plan текущего production-кандидата: **NOT_RUN/BLOCKED**.

Сначала нужны стабильная совместимая база и закрытие provenance, затем
read-only owner/footprint/unknown audit зафиксированного MODDED-входа. Только после
fail-closed whole-owner transaction plan имеет смысл отдельно разрешать реальную
конвертацию копии. Нулевые fragmentation/protected/orphan/foreign/unknown/rejected
и нулевой второй проход должны быть подтверждены независимым verifier на той же
версии исходников/ресурсов. Старые offline-проходы этих новых доказательств не заменяют.

CI-файл подготовлен и его команды проверены локально; на момент сбора этого
evidence GitHub Actions remote run не выполнялся. Он проверяет Java 17 / Gradle 8.8, текущие Python/data/geometry/contracts,
GameTests, версии, runtime/source JAR, package closure и SHA. Это подготовка
автоматизации, не завершение фаз 2–4. Графический клиент и настоящий production
dedicated fresh/save/restart должны запускаться после совместимости города:
двери/окна/лестницы/сиденья/attachments, chunk boundaries, resource reload,
старый мир и performance остаются обязательными.

`release_gates.py` проверяет полноту, согласованность и хэши предоставленных
доказательств; сам не выполняет runtime-сценарии и не заменяет независимый review.
`schemaValid=true` означает корректный отчёт, а не `releaseReady=true`.
Историческое `knownReleaseBlockers: []` TEST3 не имеет полномочий на production;
его корректная интерпретация вынесена в отдельный scoped erratum.
