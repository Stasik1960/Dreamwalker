# Курсор продолжения — 2026-10-22

Последнее указание пользователя: «теперь загрузи всё на гитхаб вместе с оригинальной задачей, промежуточными отчётами, информацией о дальнейшей работе … чтобы с нового ПК в новом чате можно было просто указать ссылку, и ГПТ мог сразу продолжить … с текущего момента». Поэтому новые сборки/Minecraft/запись карты остановлены ради передачи. Цель исходного задания остаётся незавершённой: полный оптимизированный город + совместимый мод + полные исходники, настоящая клиентская приёмка и save/reopen. Пилот не является конечным результатом.

## Восстановление

1. Скачать `bundle-manifest.json`, `SHA256SUMS.txt` и **все** `resume-data-*.zip` из релиза, поместить рядом. Для проверки без записи: `python restore_bundle.py --assets <папка> --verify-only`. Для восстановления: `python restore_bundle.py --assets <папка> --target <папка-профиль>`.
2. Установить Python 3 с sqlite3/zlib и Git. Java 17.0.15, Gradle wrapper/caches и фактические Minecraft/Fabric зависимости включены; аккаунты Minecraft не включены. Никакой Minecraft-процесс для восстановления не нужен.
3. Manifest использует пути **относительно старого `C:\Users\vakir`**. Например `<target>/.codex/worktrees/3f07/DW-native-grid-city-20261022/source`. Для точного продолжения без изменения абсолютных ссылок можно восстановить именно в `C:\Users\vakir` на новом ПК, если каталог свободен и доступен. Скрипт отказывается перезаписывать отличающиеся файлы. В старые рабочие каталоги на текущем ПК восстановление не запускать.
4. При другом пути сначала создать явную проверяемую карту `C:\Users\vakir -> <target>`. Абсолютные пути есть в Python, JSON, JVM argfiles и SQLite meta. Старые доказательства и их SHA сохранять неизменными; path rebind оформлять отдельным receipt/lineage. Не менять строковые пути во всех файлах без разбора и не переписывать исходный immutable DB ради переезда. Новому агенту перед запуском нужно связать рабочие пути с восстановленными файлами, сохранив старые доказательства.
5. `CodexControl` — снимок старых локальных правил и Job.cs, не инструкция перезаписывать настройки Codex нового ПК. Guard проверяет наличие независимого `.git/HEAD` в `DW`, пути C:, диск и LocalC. `.git` не содержит снимок; новый независимый Git можно инициализировать в восстановленном DW без reset файлов, либо клонировать исходный репозиторий в отдельный каталог для истории. Перед рабочими процессами адаптировать Guard под новый ПК и зафиксировать это в receipt. Старый перенос E: **не возобновлять**.
6. Сначала читать `research/transfer/*-handoff-RU.txt`, `research/progress.json`, `research/native-grid-plan/progress.json`, проверять текущие JAR/catalog SHA, состояние WAL и marker. Не запускать старые stop/kill скрипты с жёстко записанными PID!

## Рабочие каталоги старого ПК

Общий корень `C:\Users\vakir\.codex\worktrees\3f07`:

- `DW-native-grid-city-20261022/source` — актуальные исходники, единое место сборок.
- `DW-native-grid-city-20261022/research` — все новые доказательства и состояние; **единственный активный** `native-grid-plan/plan.sqlite` (1,323,380,736 байт), `fragments.sqlite`, `catalog-state.sqlite`, каталоги геометрии/профилей/QA/пилота.
- `DW-ordinary-city-pilot-20261020/research/city-pilot/full-city-copy.ordinary-migration/plan.sqlite` — исходный старый read-only SQLite (6,793,261,056 байт), фактический chunk_cache внутри него. Нужны copy.json/planner-binding/snapshots и OLD/tools/ordinary-stage1 finite policies. Не использовать ordinary.partial как карту-источник.
- `DW-city-mesh-20261018/deliverables/block-fixes-2-20261020` — неизменяемая исходная пара blocks.2, карта 437,197,194 байт / 3,887 файлов, source overlay и исходные отчёты.
- `DW-city-mesh-20261018/bloodborne complete` — прежние рабочие исходники, baseline exporter classes и launch/runtime зависимости. Это не замена immutable JAR как источнику точной геометрии.
- `DW/audits/Dreamwalker-BB/2026-10-21-native-grid-city/START-RU.txt` — актуальное исходное задание. Копия в TASK/START-RU.txt репозитория.

## Что уже сделано и не надо повторять

Согласован контракт SourcePose -> CellFragment -> CellSignature -> CompiledCellType; статуи и согласованные сборки должны идти через WholeObjectTemplate, отдельные двери/2D деревья/v12 защищены. Ровно три субагента export_cells/native_runtime/city_migration, без каскада; root единолично запускает сборки/Minecraft/запись карты.

Переиспользован исходный план: 828,363 фактических UUID owners + 1,862,540 synthetic native units (это **не** owners). 3,104 позы; clipping настоящих quads/UV/winding, точные формы, конечная оптика, native activation и static storage реализованы. Static runtime не хранит персональные BE/UUID/NBT. Поправка компактного хранения state transitions устранила декартовы таблицы Lithium; доказательства JVM сохранены.

Текущий пилот записан: 107,610 выбранных owners, 214,921 placements, 3,663 bridge cells. 427 исходных чанков + 91 адресная зависимость = 518 terrain chunks; 498 source owner shards. Границы/source dependencies исправлены атомарно. 13,731 исходный native component / 18,565 offsets полностью покрыт. Старые 8 light results явно инвалидированы, не переиспользовать.

Фактический новый native light **PASS 554/554, pending0**, штатный save и exit. Candidate marker `READY_FOR_GAMEPLAY_QA`; это **не итоговый READY**. Namespace `research/native-grid-plan/context-extended-relight-engine.json` и `.done.tsv`. Receipt SHA `bc1f41be58d9cd53b7aa0074e909f7d79b25f45c04a67ff5e8af1815ad36fc13`.

Один 25-минутный strict bind прошёл все427 чанков, но упал в post-loop DIM-1 ledger comparison. Исправлена одна строка размерности, metadata recovery выполнен с подтверждёнными after hashes. Не повторять427 bind/repair/global scans. Все исходные FAIL/журналы сохранены. Последующий actual apply PASS и native light PASS.

## Точные текущие артефакты

Production JAR `source/build/libs/dreamwalker-bb-fabric-1.20.1-0.1.0-v12.7.4-native-grid.1.jar`:
`3477fa1f016126eb58ea294a37a0ba06ad711f7e137ae72b103919df45e35bd3`.

Каталог `source/src/architecture/resources/assets/bloodborne_dw/native_grid/catalog.json`:
`638d84a879a7823fd4ed866b2109fbe864cda4690383745fbc5a53bc940fa550`.
3,179 cells /25 banks /2,805 meshes /841 shapes /72 whole templates; compiler frozen `111b89688889fa961f2ae19bc5e0f0683d2ec4c9bfa8fbcf0b36117a5e96e13f`.

Immutable blocks.2 JAR `40cecfb7e5cf783a762e350fc1864518c39abff177f6f025cb94892a181fbf51`.
Готовый source asset exporter JAR `8ebbebae73d69c73ed31e40cce34c0d9535543e946f5359643caf773d919de20`.
QA helper `574670c88a74f4ebed280e7d41462f22ac84e208cb71ea1a9e5d089931acf815`.
Route recorder **заморожен до первой пары**, JAR `24ea32ff3b9d2b83011fe207ce58eb4a86c47df63ecf9327c756f7f9fcf8e676`.

## Следующие конкретные шаги

**1. Preserved assemblies — до полного переноса.** Полностью перечитать обновлённый START. `preserved-assemblies.json` ещё **не создан**. Обязательные9 recipes из source/src/architecture/resources/bloodborne_dw/imported-assembly-v126.json + hiddenPartRedirects + explicitDoorReplacement и прочие действительно подтверждённые прежние сборки. Не защищать все варианты семейства и не считать любой owner graph пользовательской сборкой.

Адресная начальная сверка `research/function-qa/preserved-assembly-addressed-input-audit.json` SHA `a99dc37619f3ac09b788952e0b884cb83b8f074331fbeac536b9405808c56582`: complete installed poses глобально348 ошибочно STATIC (93066/p1=19;90179/p1=236;90301/p1=46;90143/p1=16;90160/p2=3;90797/p2=28); 90923/p2=618 и /p1=30 уже preserve; 90031/p1=0. Эти complete9 poses **не выбраны/не активированы/не находятся во fragment refs пилота**. Ещё не закончены finite matching старых pose0 components и сверка других explicit реестров. Поэтому это пока не полный assembly gate. Если обнаружится затронутый пилот — только bounded correction existing plan/affected UUID/footprints, без source chunk/shard restore целиком и без повторной конвертации. v12 имеет приоритет. Для FULL нужен адресный pose-specific override, не повторный classify всего плана (он затрёт ранее решённые private payload cases). city_migration готовит интеграцию после точного manifest.

**2. Завершить только затронутый QA двери + не выполненные дерево/статую.** Первая `research/function-qa/candidate/functions.json` содержит83 PASS (точные окна/коллизии/v12/climbing/light/static pick/place/break) +1 FAIL; эти83 не повторять. Последний `candidate-actual-footprint` завершился FAIL170.437s со штатным save/exit и standRestored=true. Обычная установка двери/выбор/ПКМ/open collision/close PASS. Ошибка QA в дополнительной bridge fixture: `Manual door must retain the bound source art and yaw`. Actual source installation variant2, обычный picked item легально устанавливает variant0 по старому ImportedSourcePartsV127 installation policy. **Это не найденный production дефект.** Минимальный guard fix ещё НЕ применён. Проверить existing report installed state/payload и actual closed/open footprint; убрать неверное требование same VARIANT с доказательством old item policy, сохранить block/yaw bounds. SourceShift/MountY для вручную установленного предмета не копируются из исходной карты. Background type1713 имеет законно EMPTY collision/outline, но непустую визуальную модель; нельзя выдумывать для него коробку/ray. Все изменения QA-only; production3477 не менялся. Подробнее native-runtime handoff.

Полностью пустой stand [1007,224,95]..[1040,256,128], root[1024,225,111],16 обычных chunk loads перед каждым действием. Весь stand проверяется AIR/noBE/noOwner и восстанавливается. Не использовать старые source-privileged predicted footprints. Не загружать all prior successful checks снова. Source Java текущего QA SHA `8bf5da029687d4d60e93e46e56d1479f936a090bbd566a064147f786c5117ec2`. Последний план `candidate-actual-footprint-plan.json` SHA `87075dccc42d30ef6429e2bc9b9664b126bf6539a7a78b16d6e0a2119ba75354` уже использован: для нового попытки нужен fresh output, сохраняющий старые FAIL.

Root-only build: `python tools/native-grid/run_task.py tools/native-grid/build.py remapNativeGridQaJar`; затем `run_functions.py --plan <fresh-bound-plan>`. Все project processes через адаптированный Guard Operation. Не пересобирать frozen Route helper между baseline/candidate.

**3. Одна настоящая matched client + separate server пара пилота.** Ещё **ни разу не запускалась**. Frozen profile-lock SHA `285757deaeafd752468dc50a79b0bc4e78fa7ce23b444cf8089d69539fa161d0`: Java17.0.15, server4G/client3G, view6/sim4/fancy/AO/gamma0.5/60fps/noVsync/1280x720, actual Fabric0.19.5, одинаковые39client/36server dependencies (Sodium0.5.11/Indium1.0.36/Lithium0.11.4). Перед парой минимум9GiB реально свободной RAM, не снижать reserve и не менять heaps ради PASS. На старом16GiBПК свободно было5.8GiB; пользователь обещал дополнительно освободить, но до transfer фактическая пара не запускалась. На новомПК проверить ресурсы заново.

`run_profiles.py --variant baseline|candidate --production <exactJAR> --world <research/pilot/baseline|candidate> --route <research/pilot/route.json> --out <fresh-dir>`. Исторический baseline functional server OOM не выдавать за результат ещё не выполненной matched pair. Если actual baseline fails — сохранить FAIL и честно указать ограничения сравнения. Screenshot файлы реально открыть view_image, не делать заявлений о визуале по существованию PNG. Учитывать реальные delivered/rendered chunks, задержки и раздельные heaps. Не заводить серии новых стендов.

**4. После реального pilot gate — FULL автоматически.** Только после assemblies gate и принятого фактического пилота, statusPASS+denseBrickPilottrue+catalog638. Одна DB и одна полная рабочая копия source blocks.2. Архивировать pilot light namespace/queue/bindings с SHA до `migrate.py promote-city --pilot-gate ...`. Прочитать `source/tools/native-grid-city/FULL-CITY-HANDOFF-RU.txt` и exporter handoff. Fresh FULL native input/function gate обязателен, не260 pilot decisions. `full_native_inputs.py` использует уже сохранённый cache. Затем compose всего города, закрыть ТОЛЬКО реальные missing geometry/physics/optics, append-only catalog с frozen prefix, import mapping/cleanup audit, root build, actual bridge registry gate, clone immutable source один раз, full apply/resume и новый полный native light namespace с pending0. FULL ещё НЕ начинался.

Перед transfer исправлены **только authoring readiness helpers**, fixture PASS: explicit current baseline для catalog_prefix.py и FULL carrier predicate binding/observed flags в catalog_normalize_carriers.py. Они ещё не использованы для FULL. Их точные SHA/команды/fixture в export-cells handoff. Не брать устаревший sourceZIP062013 как актуальную версию этих двух файлов.

**5. Конечная приёмка и выдача.** После FULL/light — один обычный client route через кирпичный район, фасад/башню и удалённый район, save/exit, новое открытие/подключение. Финальные маршруты уже в research/final-route. Проверить реальные изображения/проход/функции/без многосекундных stalls/OOM/чёрных поверхностей; упаковка всей карты+JAR+исходников+инструкция+SHA. До этого READY=false.

## Важные ограничения

- Не возобновлять E: миграцию и не удалять другие проекты/копии. Не следовать reparse points при очистке.
- Не применять stale PID stop скрипты, не запускать checkpoint writers второй раз в существующие output directories.
- Не делать новые owner graphs/персональные BE для статичной декорации, не менять PNG/тёмный цвет ради света.
- Не повторять общий source scan/export/catalog research, strict427bind, dependency repair, доказательства storage или завершённый pilot light без затронувшего их изменения.
- Не менять исторические FAIL/OOM в PASS; исправленный следующий attempt получает новый путь и ссылку на предыдущий.
- Передача GitHub — сохранение незавершённого checkpoint, а не заявление о готовом оптимизированном городе.
