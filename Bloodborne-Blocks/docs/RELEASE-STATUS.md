# Bloodborne Blocks — RELEASE_READY: FAIL

2026-09-27. Проверочный кандидат: **2.1.0-rc.1**. Runtime baseline —
`b086e88929a971a2abd184629b3e7a59225304e5`; процессная ветка —
`codex/bloodborne-release-audit`. Из repair runtime ничего не перенесено.
Изменены CI, версия и документация; это не production-релиз.

| Gate | Фактический статус | Доказательство / ограничение |
|---|---|---|
| TEST_SCOPE_PASS | PASS | 202 Python tests / 36 запусков и Java/data checks; только объявленный [scope](release/evidence/rc1/test-scope.json) |
| STATIC_PASS | PASS | Локальные `check build checkReleaseVersion`; [лог](release/evidence/rc1/check-build-gametest.log), [package](release/evidence/rc1/package.json) |
| GAMETEST_PASS | PASS | 37/37, 0 failures; [свежий XML](release/evidence/rc1/TEST-logical-gametest.xml) |
| DEDICATED_RESTART_PASS | NOT_RUN | Остановка на compatibility blocker до загрузки legacy-мира |
| CLIENT_VISUAL_PASS | NOT_RUN | По той же причине; startup/RAM/reload/FPS/TPS не измерены |
| FULL_CITY_PASS | BLOCKED | Неизвестные текущие composite states нельзя заменять прежней раскладкой |
| RELEASE_READY | FAIL | Compatibility input отсутствует; последующие gates не выполнены |

GitHub Actions: **PASS**, [run 36336948341](https://github.com/Stasik1960/Dreamwalker/actions/runs/36336948341)
для commit `782473a7c57e8cd303de57223f9c3f3e075b5d26`: 202 Python tests / 36 запусков,
37/37 GameTests, build/version/package PASS. Результат относится к проверкам кода
и пакета; production gates выше остаются открытыми.
Workflow теперь получает полную Git-историю и устанавливает NumPy 1.26.4 /
Pillow 10.4.0; выполняет check/build/GameTests/version/package checks.

**Единственный непосредственный blocker: BB-COMPOSITE-INPUT.** Для 23 `m_*` ID
в 33 клетках неизменного MODDED-входа нет authoritative definitions, моделей,
коллизий и ownership/composition mapping. Точные ID и координаты сохранены в
[missing-model-positions.json](city-compat/missing-model-positions.json).

Старый [recovery](city-compat/RECOVERY.md) восстанавливает **предыдущую** раскладку
(30 helpers + 3 air). Он прямо не восстанавливает потерянные transient composites.
Проверка соответствия старой карте не доказывает сохранение нынешней геометрии
и коллизий. Поэтому `--recover-city` не применялся: подмена неизвестного
содержимого нарушает требование fail-closed.

Нужен исходный generated palette/model artifact, соответствующий
`Ether-Bloodborne-2.0.2-positions`, с данными всех 23 ID, либо детерминированные
composition inputs и версия генератора, воспроизводящие те же ID, геометрию и
ownership. Минимальный следующий шаг — сверить этот источник с 33 исходными
клетками и продолжить реальные legacy fixtures. Совместимость legacy-мира
**не подтверждена**. Отсутствие дальнейших проверок не означает их успех.

Артефакты локальной **проверочной сборки**, не production package:

- `build/libs/bloodborne-blocks-2.1.0-rc.1.jar` — SHA-256
  `dec76327fba9db0e699aff75168ce1d533e34bd5da5c7fca98437008d846505d`.
- `build/libs/bloodborne-blocks-2.1.0-rc.1-sources.jar` — SHA-256
  `fc1e6b7509c83241e3b226bb5d92ce8da8af62b17b4529ae987f3dfeb232d8a9`.
- MODDED ZIP не изменён: SHA-256
  `c517dfeb52c4d13bdbe90e02a93ac00416354eb89313a9d1377f24823af6d0e9`.

Linux CI [artifact](https://github.com/Stasik1960/Dreamwalker/actions/runs/36336948341/artifacts/10937273847)
содержит отдельную CI-сборку того же rc.1: JAR SHA-256
`a27a25b5a07bcf723b55d631004f62c52a5957e6feac3c1ba14c289252b8f7f6`.
Его SHA нельзя подменять SHA локального Windows JAR; Linux resource manifest и
sources JAR SHA записаны отдельно в `status.json.ci.package`.

rc.1 получает собственную версию: эквивалентность опубликованному beta.3 JAR
не заявляется. Пять class differences и старый repair delta 355/356 сохранены
как история; они не подменяют доказательства нового кандидата.

Точные команды, fingerprints и разделение TEST/full-city:
[status.json](release/status.json). Старое evidence не перезаписано:
[beta.3 status](release/evidence/status-beta3.json), [аудит](release/AUDIT.md).
См. [migration guide](release/MIGRATION.md), [воспроизведение](release/REPRODUCE.md)
и [отдельный комментарий агента](release/AGENT-COMMENTS.md).

Конвертация мира, merge в `main`, release tag и production-публикация не выполнялись.
