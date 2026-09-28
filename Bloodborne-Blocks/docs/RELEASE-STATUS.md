# Bloodborne Blocks 2.1.0-rc.2 — комплект для проверки

Принятые TEST3 и настоящее локальное продолжение repair объединены с полным
городом rc.1. [Единый каталог JAR + карта](../../releases/Bloodborne-Blocks/README.md).
Source commit runtime: `fcff3c5004520778b79c45c887b2ba1a715e99d0`; accepted checkpoint:
`41c20ee8b730c2d1567b2ab5a3d579d51bdbea1a`. Графическая приёмка не выполнена.

| Проверка | Статус | Доказательство / граница |
|---|---|---|
| STATIC_PASS | PASS | `check build checkReleaseVersion`, 285 Python tests / 49 запусков в Linux CI плюс Java/data checks |
| GAMETEST_PASS | PASS | Свежие 57/57, 0 failures |
| ACCEPTED_RUNTIME_PASS | PASS | 30 Java файлов, 49 logical-контрактов, TEST3 и owner/GUI продолжение; физика 2 914 RC1 compatibility-блоков / 28 866 профилей сохранена |
| ACCEPTED_CITY_PASS | PASS | 6 507 групп восстановлены; 20 474 уже корректны; 0 целевых конфликтов и остаточных фрагментов |
| WHOLE_WORLD_COMPATIBILITY | PASS | 10 009 чанков; 0 unknown/invalid/helper errors; 170 non-terrain файлов побайтно неизменны |
| SECOND_PASS | PASS | 0 изменений; все 186 файлов побайтно идентичны |
| PACKAGED_DEDICATED_SMOKE | PASS | Упакованный JAR + Fabric API: запуск, save-all flush, stop, повторный запуск/сохранение/stop; контрольные чанки |
| DEDICATED_RESTART_PASS (полная сборка) | BLOCKED | Сторонние моды отсутствовали в QA; их runtime-данные на тестовой копии пропускались |
| CLIENT_VISUAL_PASS | NOT_RUN | Ручной client startup/reload/restart, interactions, FPS/RAM не измерены |
| FULL_CITY_PASS (глобальная история) | BLOCKED / вне задачи | Неподтверждённые исторические сборки не реконструировались |
| RELEASE_READY | BLOCKED | Разрешён проверочный комплект, без production-сертификации и release tag |

Dedicated world startup: 6,989 / 5,371 секунды (лог `Done`), localhost only.
36 контрольных чанков принудительно загружены и сохранены после restart;
замер без игроков: 21,06 секунды / 429 ticks / 20,37 TPS. Peak RAM не измерен.
Это не оценка производительности production и не визуальная проверка.
Публикуемый архив не проходил через сервер QA: данные остальных модов в нём
побайтно сохранены. Для игры необходима остальная исходная сборка модов.
GitHub CI: **PASS**, [run 36415497358](https://github.com/Stasik1960/Dreamwalker/actions/runs/36415497358)
для `ae8150f7250f7810ffb836ae9eb922a67e3ead55`: 285 Python tests / 49 запусков,
57/57 GameTests, сборка и независимая перепроверка поставляемого ZIP.
[Результат и хеши CI](accepted-restore/ci.json), [полный лог](accepted-restore/ci-36415497358.log.gz).
В каталоге остаётся именно JAR, проверенный packaged dedicated smoke; Linux CI
проверил и его, и собственную сборку. Финальный commit публикации меняет только
документацию/доказательства, без изменения проверенного кода или артефактов.

- JAR SHA-256: `1ce7f8a57dbbeaf6c0d913daf6e02a4ad1fe63fceac78cdd26773dd5615d1f56`.
- Город SHA-256: `f4b9ef510e2aacade80bb11f95cd82fe17eaed56e118280e8e055dd4aecd133c`.
- Входной rc.1 SHA-256: `749853aeb19ba8ea823bf1f683476985b74e2fce2746143eeacc3225eb0ed0a9`.

Точные команды, source manifest, хэши и результаты:
[status.json](release/status.json), [delivery](accepted-restore/delivery.json),
[итоговый лог](accepted-restore/final-check.log),
[XML](accepted-restore/TEST-logical-gametest.xml),
[полная таблица/координаты](accepted-restore/REPORT.md),
[комментарии агента](accepted-restore/AGENT-COMMENTS.md).

23 неизвестных composite ID не восстанавливались. Все 33 прежние retired-позиции
сохранены как в rc.1, включая 20 уже занятых клеток. Исходные ZIP не изменены.
Точка реквизита `(-332,77,-137)` не имеет готового доказательства целой стопки;
оставлена без изменения. 34 подтверждённые стопки восстановлены в других точках.
Перед обновлением обязателен backup: [migration guide](accepted-restore/MIGRATION.md).

Старый rc.1 **не содержит принятых repair-исправлений**. Его прежний
[status](release/evidence/status-rc1.json) сохранён как история.
