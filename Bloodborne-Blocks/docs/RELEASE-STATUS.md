# Bloodborne Blocks — RELEASE_READY: FAIL

2026-09-27. Production-кандидат **не выбран**: стабильная совместимая линия пока
не подтверждена. `origin/main` используется только как база аудита и совпадает
с `origin/archive/beta3-grid-fragmentation-broken`.

Рабочая ветка: `codex/bloodborne-release-audit`.
Базовый commit проверенных запусков: `b086e88929a971a2abd184629b3e7a59225304e5`.
Audit/CI/docs фиксируются отдельно; точный снимок проверенных исходников указан ниже.
Gradle / fabric.mod.json / VERSION: `2.1.0-beta.3`. Runtime-код и ресурсы не менялись.

| Gate | Статус | Доказательство / ограничение |
|---|---|---|
| TEST_SCOPE_PASS | PASS | Текущие ограниченные code/data/contract fixtures; [scope](release/evidence/test-scope.json) |
| STATIC_PASS | PASS | `check build`, версии и полный package check; [лог](release/evidence/main-checks/check-build-gametest.log), [финальный package log](release/evidence/main-checks/release-checks.log) |
| GAMETEST_PASS | PASS | 37/37, 0 failures; [свежий XML](release/evidence/main-checks/TEST-logical-gametest.xml) и тот же лог |
| DEDICATED_RESTART_PASS | NOT_RUN | Production fresh/save/restart со старым совместимым миром не выполнялся |
| CLIENT_VISUAL_PASS | NOT_RUN | Нет реального client fresh/reload/restart/interaction acceptance |
| FULL_CITY_PASS | BLOCKED | Нет доказанного whole-owner converter/compatibility baseline; новый world dry-run и conversion не запускались |
| RELEASE_READY | FAIL | Обязательные gates и provenance blockers остаются открытыми |

Все gates, точные выполненные команды и scoped blockers: [status.json](release/status.json).
`STATIC_PASS` описывает код и пакет; не означает проверку карты. TEST3 в эти
production-доказательства не переносится: [scope erratum](release/test3-scope-erratum.json).

Артефакты текущей **проверочной сборки**, не release package:

- JAR: `build/libs/bloodborne-blocks-2.1.0-beta.3.jar`, 34 498 188 байт,
  SHA-256 `31a9c5a21a33b4999e05e8faf74238e62af2e6ddd2f96f9712bb73566280482b`.
- Sources: `build/libs/bloodborne-blocks-2.1.0-beta.3-sources.jar`, 60 512 байт,
  SHA-256 `891e0335604bab64d3002e3c80278909f62d790f80887b0f27e3671f1ee8fe44`.
- MODDED-вход: `reference-inputs/latest-modded-world.zip`,
  SHA-256 `c517dfeb52c4d13bdbe90e02a93ac00416354eb89313a9d1377f24823af6d0e9`.
- [Source snapshot](release/evidence/source-snapshot.json):
  `88ae1f953fcf339d415b1d8831511d6f66be22cafedfbba2ecf9ea545dbced51`.
- [Resource manifest](release/evidence/resource-manifest.json):
  `8a93772aeabb82c8682e757840a9ffb960296fac30797ca3679c76170801a9a1`.

Главные blockers: старые ID/ItemStack/NBT не имеют доказанной миграции; полный город
не прошёл whole-owner проверку; restart/client/performance не проверены; точная
историческая разница 355/356 групп и пять class-file различий опубликованного
JAR остаются неразрешёнными. 6518 TEST3-history и 1979 dirty-repair diagnostic —
разные проваленные прогоны, ни один не принадлежит свежему аудиту `main`.

Счётчики до/после, таблица веток и объяснение расхождений: [AUDIT.md](release/AUDIT.md).
Команды и дальнейшие проверки: [REPRODUCE.md](release/REPRODUCE.md).
Отдельные выводы агента: [AGENT-COMMENTS.md](release/AGENT-COMMENTS.md).
Публикация audit-коммита в отдельной ветке не является выпуском мода.
Merge, release и новая массовая конвертация не выполнялись.
