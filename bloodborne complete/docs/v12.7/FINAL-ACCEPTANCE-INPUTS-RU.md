# Явные входы финальной приёмки V12.7

Это карта входов для родительского исполнителя. Она не утверждает наличие
финальных файлов или PASS. После изменения JAR нативные отчёты и resource-аудит
получают новый собственный SHA; старые тесты остаются историей. Галерея и полный
город в этом этапе не создаются и не открываются.

`tools/record_v127_acceptance.py` требует ровно 15 основных JSON-отчётов:

| Аргумент | Явный путь | Необходимое доказательство |
| --- | --- | --- |
| `--build-report` | Выбранный Root точный `build/v124-stages/<final-build-name>.json` либо отдельный `docs/v12.7/final-build.json` | Настоящая окончательная сборка, exit0; при отсутствии собственного JAR SHA — точная аттестация файла |
| `--gametests-report` | Создаваемый Root `docs/v12.7/gametests-final.json` | PASS реальных GameTests, точный `sourceCommit` финального checkpoint и SHA выбранного XML |
| `--native-client-report` | `build/runtime-client-<final-capture-name>/v127-native-client-qa.json` | `PASS_ENGINE_CAPTURE`, финальный собственный `productionJarSha256`, все 17 кадров и настоящие действия клиента |
| `--native-reopen-report` | `build/runtime-client-<final-reopen-name>/v127-native-client-qa.json` | `PASS_ENGINE_REOPEN`, тот же собственный SHA и сохранённые UUID/состояния/команды/RP/light14 |
| `--production-server-report` | Явный `--report` финального `tools/run_v11_server.py` | PASS полного startup/save/stop без QA/author, собственный `artifact_sha256`, exit0 и `extraMods=[]` |
| `--converter-fixture-report` | `build/v127-original-level-fixture-final-validation.json` или выбранное новое имя | Реальные author + production-only reopen, исходная схема 2975 обновлена до 3465+, точные UUID/NBT/состояния/shard и отсутствие сохранённой привилегии |
| `--pixel-review-report` | Создаваемый Root `docs/v12.7/native-pixel-review-final.json` | Реальный осмотр PNG финального клиента, собственный `productionJarSha256` и хэши кадров |
| `--resources-report` | `docs/v12.7/resource-audit.json` | Повторный PASS combined JAR, финальный собственный SHA, `qaClassesExcluded=true`, `sourceResourcePackRequired=false` |
| `--preservation-report` | `docs/v12.7/preservation-audit.json` | PASS сохранности исходной V12, 116 защищённых строк; аттестация неизменного статического доказательства |
| `--alt-report` | `docs/v12.7/tool/alternate-audit.json` | PASS и пустой `missingVisualAlternatePoses`, плюс финальные игровые ALT-тесты; аттестация статического доказательства |
| `--collision-report` | `docs/v12.7/collision/target-map.json` | Обновлённый `PASS_ACTUAL_NATIVE_BINDINGS` из окончательного native snapshot; `authorExport=NOT_SUPPLIED_NOT_APPLIED` |
| `--export-report` | `docs/v12.7/visual-export/final-delivery-audit.json` | PASS точного Blockbench ZIP, 541 NEW рабочего типа, 0 оригинальных V12 exports, GUI `NOT_RUN`; аттестация |
| `--fullplan-report` | `docs/v12.7/conversion/full-conversion-plan.json` | `PASS_COMPLETE_SOURCE_PLAN`, 0 known visible gaps, точные исходные SHA и одобренная реализованная политика; полный мир не конвертирован |
| `--manual-placement-report` | `build/v127-manual-placement.json` | Обновлённый финальный PASS реальных обработчиков предметов; все cases.pass=true, число cases совпадает с workingPlacementItems; аттестация native dev-доказательства |
| `--worldedit-report` | `docs/v12.7/worldedit-native-validation.json` | Точное финальное WITH-WE доказательство из настоящего XML/лога/RP/linked-component proof; аттестация native dev-доказательства |

У таблицы коллизий прежний статус `PREPARED_PENDING_NATIVE_SNAPSHOT` не подходит.
Root обновляет её только после окончательного native снимка:

```text
python tools/audit_v127_collision_targets.py --native-snapshot build/gametest-<final-tests-name>/world/v127-collision-registry-properties.json
```

`worldedit-tests15-provisional.json`, native-readiness со словом `PREPARED`,
подготовка bootstrap и launcher `LAUNCHED_AWAITING_GAME_VERIFICATION` не являются
основным PASS. Отчёт запуска клиента не подменяет `v127-native-client-qa.json`.
Сохранённый клиентский мир — `saves/prototype-fixture`; production server открывает
его новую копию и сохраняет `isolated-smoke-world` в собственной папке runtime.

Дополнительные обязательные аргументы:

| Аргумент | Значение |
| --- | --- |
| `--production-jar` | `build/libs/dreamwalker-bb-fabric-1.20.1-0.1.0-v12.7-final.jar` |
| `--source-checkpoint` | Точный окончательный commit до final build/native run, без последующих изменений `src/tools/gradle` |
| `--gametests-xml` | `build/test-results/gametest/TEST-<final-tests-name>.xml`, настоящее число testcase и ноль failure/error/skipped |
| `--proof-bindings` | Создаваемый Root `docs/v12.7/final-proof-bindings.json` только после заморозки и проверки всех выбранных файлов |
| `--counts` | `docs/v12.7/final-counts.json` |
| `--runtime-plan` | `build/v127-runtime-conversion-final2` — явно, старый default упаковщика не использовать |
| `--source-plan` | `build/v127-full-conversion-first.sqlite` |
| `--source-style-policy` | `docs/v12.7/conversion/source-style-policy.json` |
| `--output` | `docs/v12.7/acceptance.json`, файл обязан отсутствовать |

Текущий полный SQLite: SHA-256
`270292357105bdcfe9a38dd931bb9a0b28f4eb11d36a74644a390dfb81a07d29`.
Runtime-final2 manifest: SHA-256
`af4853248d1ee116101110f3eedcaf7528fb9b1bb2e9fe26e3bf6b0ec3cebbd7`.
Политика `APPROVED_EXACT_VISIBLE_WITH_ENCLOSED_VANILLA`: SHA-256
`0a8c174ecfe0bbd10787815bd7ae39b0629b9162965bd93529669606ba3a8e05`.
Эти значения проверяются по файлам, а не принимаются по тексту документа.

Аттестация `PASS_FINAL_FROZEN_PROOF_BINDINGS` содержит итоговые JAR SHA,
`sourceCheckpoint`, пустой `currentUnresolvedTestFailures` и `proofs`. Для каждого
статического/dev-входа без собственного JAR SHA в `proofs` записан его путь
относительно проекта, SHA-256, тот же JAR SHA и checkpoint. Нельзя таким образом
подменить свежий клиентский/серверный/converter/resource/pixel отчёт. Перед записью
приёмки сам recorder проверяет отсутствие tracked/untracked изменений кода и
точные runtime-потоки. Упаковщик повторно хэширует сами исходные ZIP.

Сначала Root запускает полный набор перечисленных аргументов с `--validate-only`.
Только после его PASS тот же набор запускается без `--validate-only` для записи.
`--extra-proof` добавляется для конкретных окончательных PASS-доказательств:
освещение/границы/native offset/WorldEdit typed payload/связи/хранение и память.
Исторические отчёты с PREPARED/NOT_RUN/PARTIAL не добавляются этим флагом.

Для малого исходного-schema теста используются точные команды
[NATIVE-CONVERTER-FIXTURE-RU.md](NATIVE-CONVERTER-FIXTURE-RU.md). Официальные
Minecraft 1.20.1 / Loader 0.16.10 / capsule 1.0.1 копируются из
`build/v127-server-bootstrap`. Обязателен существующий принятый
`build/runtime-server-v126-flatgallery-author-2/eula.txt`; JAVA_HOME17 имеет
приоритет. Папки fixture, author и reopen для каждой ревизии создаются заново.

Окончательная приёмка сохраняет отдельные `NOT_RUN`: физическая мышь/клавиатура,
GUI Blockbench, полная конвертация и полный облёт оригинального города, его
освещение/AO после пересчёта. Авторский экспорт коллизий не предоставлен и не
применён. Подтверждённые 13 отличий opacity защищённой V12 и потенциальный
shape/light-face нюанс 90935 pose3 не превращаются в обещание полного совпадения
рендера города.
