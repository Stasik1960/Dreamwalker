# Bloodborne Blocks — RELEASE_READY: NOT YET CERTIFIED

2026-09-27. Проверочный кандидат: **2.1.0-rc.1**. Runtime baseline —
`b086e88929a971a2abd184629b3e7a59225304e5`; процессная ветка —
`codex/bloodborne-release-audit`. Из repair runtime ничего не перенесено.
Проверяемая карта создана отдельно от неизменного MODDED-входа; это ещё не
production-сертификация без реального сервера и клиента.

| Gate | Фактический статус | Доказательство / ограничение |
|---|---|---|
| TEST_SCOPE_PASS | PASS | 202 Python tests / 36 запусков и Java/data checks; только объявленный [scope](release/evidence/rc1/test-scope.json) |
| STATIC_PASS | PASS | Локальные `check build checkReleaseVersion`; [лог](release/evidence/rc1/check-build-gametest.log), [package](release/evidence/rc1/package.json) |
| GAMETEST_PASS | PASS | 37/37, 0 failures; [свежий XML](release/evidence/rc1/TEST-logical-gametest.xml) |
| DEDICATED_RESTART_PASS | NOT_RUN | Нужен запуск итоговой карты на dedicated server и повторный запуск |
| CLIENT_VISUAL_PASS | NOT_RUN | Нужна визуальная проверка: startup/RAM/reload/FPS/TPS не измерены |
| FULL_CITY_OFFLINE_PASS | PASS | 23 ID / 33 клетки удалены в копии, затем 22 366 групп сконвертированы; полный census: 0 `m_*`, 0 unknown ID |
| RELEASE_READY | NOT_YET_CERTIFIED | Остались реальные dedicated-server и client gates |

GitHub Actions: **PASS**, [run 36336948341](https://github.com/Stasik1960/Dreamwalker/actions/runs/36336948341)
для commit `782473a7c57e8cd303de57223f9c3f3e075b5d26`: 202 Python tests / 36 запусков,
37/37 GameTests, build/version/package PASS. Результат относится к проверкам кода
и пакета; production gates выше остаются открытыми.
Workflow теперь получает полную Git-историю и устанавливает NumPy 1.26.4 /
Pillow 10.4.0; выполняет check/build/GameTests/version/package checks.

**BB-COMPOSITE-INPUT закрыт по явно одобренной политике удаления.** Для 23
`m_*` ID в 33 клетках не нашлось authoritative definitions, моделей, коллизий
или ownership/composition mapping. Вместо подстановки старой раскладки создана
новая копия: все 33 исходных состояния были сверены, проверены на отсутствие
block entity/tick и заменены на воздух. Затем карта переведена в текущий grid.
Полное доказательство и SHA приведены в
[retired-composites-world.json](release/evidence/rc1/retired-composites-world.json).

Старый [recovery](city-compat/RECOVERY.md) не использовался: он восстанавливает
предыдущую раскладку, а не неизвестные transient composites.

Следующие обязательные действия — dedicated-server загрузка и перезапуск с
итоговой картой, затем клиентская визуальная проверка. Их отсутствие не
означает успеха.

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
