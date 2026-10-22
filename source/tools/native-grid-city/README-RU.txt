Native grid: один дисковый план, пилот и полный город

Все команды запускает root через CodexControl/Guard.ps1 -Mode Operation.
Рабочий каталог: DW-native-grid-city-20261022/source.
Default plan: ../research/native-grid-plan/plan.sqlite.
Исходный old plan.sqlite всегда подключён mode=ro. Карта blocks-fixed-2 неизменна.
Нельзя открывать целевой мир между записью регионов, очисткой ledgers и штатным светом.

Подготовка без модификации карты (выполнена):
python tools/native-grid-city/migrate.py export-poses --verify-source
python tools/native-grid-city/migrate.py prepare
python tools/native-grid-city/migrate.py classify --classification tools/native-grid/classification.json

Экспорт source-geometry/native-geometry выполняет root asset-only клиентом.
После exporter export_cells.py:
python tools/native-grid-city/migrate.py import-fragments --fragments ../research/fragments.sqlite
python tools/native-grid-city/migrate.py import-door-footprints --footprints ../research/geometry-supplement/door-footprints.json
python tools/native-grid-city/migrate.py compose --bounds 912 1007 64 159 --additional-owners ../research/function-qa/satellites.json

compose пишет compositions.jsonl и native-states-needed.json в plan directory.
Для недостаточно экспортированных actual native states нужен адресный physicsOnly
экспорт. Синтетические source candidates существующих native states не являются
старыми BE UUID: изолированные native cells сохраняются. При замене их видимого
корня все его SourcePose visual fragments переносятся до границ ячеек; физика и
оптика основы берутся из фактического исходного native state. Это не owner graph.

Компилятор exporter (native-grid/compiler/catalog.py) читает compositions.jsonl,
fragments.sqlite и whole-objects.jsonl; пишет catalogue/meshes и mapping JSONL.
python tools/native-grid-city/migrate.py import-types --mapping ../research/type-mapping.jsonl --catalog src/architecture/resources/assets/bloodborne_dw/native_grid/catalog.json

Root-only создание маленького пилота после планирования:
python tools/native-grid-city/migrate.py clone --target ../research/pilot-map --bounds 912 1007 64 159 --context-radius 7 --extra-chunks ../research/function-qa/satellites.json
Копируются исходные 6x6 чанков, context7 вокруг основной области, необходимые
точные footprints и satellite chunks с соседним световым бордюром1. Камеры
основного клиентского маршрута остаются в context7; для отдельной камеры у
сателлита нужен согласованный расширенный контекст. Shards сохраняют типы NBT.
Hash before для cropped пилотных регионов фиксируется в плане явно.
При раннем клонировании до import-types региональные before-hashes нужно связать
с уже завершённой candidate-копией после появления regions:
python tools/native-grid-city/migrate.py bind-pilot-copy --target <candidate-map>

Пилотный bootstrap: исходный Spawn=285,34,-842. Принудительный Spawn в тяжёлом
brick районе вызывает штатную предварительную загрузку441чанка до подключения.
Одна согласованная коррекция уже выполнена root через конкретный dry-run план:
python tools/native-grid-city/pilot_bootstrap.py
python tools/native-grid-city/pilot_bootstrap.py --execute ../research/pilot/bootstrap-repair/plan.json
Повторный dry-run не заменяет существующий план. Execute восстанавливает только
точно перечисленные level.dat/level.dat_old и фильтрует OwnerShardChunks3694→407
до реально скопированных shards, сохраняя все другие поля/данные. Три созданных
неудачным bootstrap файла помещаются в отдельный evidence, без удаления.
Все482 исходных файла проверяются, регионы/407shards не меняются. После этого:
python tools/native-grid-city/migrate.py bind-pilot-copy --target ../research/pilot/candidate --harness-receipt ../research/pilot/bootstrap-repair/receipt.json
Этот scoped-pilot receipt явно связывает globalledger beforehash. Immutable
copyManifest не изменяется; полный городской проход не принимает эту подмену.
Source-copy receipt содержит SHA каждого cropped региона. Реальные пустые
0-byte entities/POI MCA копируются пустыми; непустой короткий header отвергается.
Прерванную собственную copy можно возобновить тем же clone: внешняя per-file
ведомость и exact original/cropped source hashes защищают уже записанные файлы.
Посторонние файлы и содержимое не принимаются за незавершённую копию.

NativeGridBase — разрешённый точечный мост только в уже существующем известном
CompositeBE с нужным сохранённым владельцем. Обычная архитектура в таком месте
по-прежнему представлена shared catalogue index, без нового UUID/owner. Его int
лежит во внешнем поле NativeGridBase; собственный payload/resident сохранённого
объекта не переписывается. Настоящие чужие контейнеры/неизвестные BE не оборачиваются.
Смешанные несовместимые скульптуры сохраняются как конкретный прежний объект,
а окружение может пользоваться мостом. Whole-template роли в мост не помещаются.
Ресурс bridge_carriers.json содержит только реально встреченные registry IDs.
После import-types bridge-host-states.json перечисляет точные carrier/base пары.
До apply native registry QA обязан доказать dry/luminance0/opaque/sidedTransparency
совместимость всех пар, с SHA фактически загруженных runtime/catalogue/plan:
python tools/native-grid-city/migrate.py accept-bridge-carriers --receipt <native bridge-carrier-gate.json>
Без этого доказательства apply не модифицирует карту.

После готовности JAR и итогового review мигратора root:
python tools/native-grid-city/migrate.py apply --target ../research/pilot-map --runtime-jar <candidate.jar>

Apply потоково читает по одному chunk record, пишет отдельный region temp,
fsync журнала before/after -> atomic replace -> SQLite commit. При сбое нужно
повторить ту же команду: после-crash after hash подтверждает уже выполненную
замену. Light debt подтверждён на диске ДО замены. Shards очищаются только после
записи ВСЕХ требуемых регионов. Удаляются selected owner contributions/membership
и BE; retained owner/root/helper никогда не удаляется по совпадению семейства.

Полный долг native light, включая существующих соседей, инвалидируется OFFLINE.
Heightmaps остаются на диске до штатного пересчёта; это не доказательство света.
Применение заканчивается OFFLINE_APPLIED_RELIGHT_PENDING, не READY.

Штатный отдельный Fabric сервер запускается с тем же candidate.jar и отдельным
native-grid-relight helper JAR (source set src/nativegridrelight; НЕ production).
JVM properties:
-Dnativegrid.relight.queue=<absolute plan dir>/relight-queue.tsv
-Dnativegrid.relight.receipt=<absolute plan dir>/relight-engine.json
-Dnativegrid.relight.batch=2
-Dnativegrid.relight.stop=true
Runner проверяет hash реально загруженного production JAR, catalogue, мира и
очереди. Native FULL -> initializeLight -> light, настоящие Heightmaps, save flush,
fsync done cursor. По умолчанию не более двух целевых ticket chunks одновременно;
обычный сервер может иметь дополнительные dependency/spawn chunks. Его RAM,
профиль и spawn tickets задаёт root одинаково с целевыми тестами.
python tools/native-grid-city/migrate.py accept-relight --receipt ../research/native-grid-plan/relight-engine.json
Только совпавшие queue/runtime/catalog hashes, processed=queued, pending=0 и
saveFlushed=true снимают incomplete marker. Статус READY_FOR_GAMEPLAY_QA ещё
требует клиентский маршрут, функции, save/reopen и оценку производительности.

Если штатный свет действительно упал OOM и исправлены только два разрешённых
storage класса, root может явно связать runtime без переписывания карты:
python tools/native-grid-city/migrate.py rebind-storage-runtime --old-runtime-jar <preserved-old.jar> --runtime-jar <new.jar> --receipt <fresh-native-55-carrier-gate.json>
Требуются source-receipt.json с двумя исходниками до/после, неизменный light
helper и точное совпадение ВСЕХ остальных файлов JAR, включая catalog/meshes.
Уже записанные строки native done.tsv принимаются только по сохранённому
saveFlushed=true receipt, с точной очередью и числом строк. Меняется только
runtime SHA заголовка; не создаётся фиктивных processed. Старые журналы, OOM log,
bindings и frozen inputs сохраняются в plan/runtime-transitions; intent допускает
resume между атомарными metadata заменами и SQLite commit. Карта и её afterhash
журналы не переписываются. Это PASS перехода binding, а не PASS освещения/игры.

После успешного плотного brick pilot root сохраняет gate JSON:
{"status":"PASS","denseBrickPilot":true,"catalogSha256":"<pilot catalogue SHA256>"}
python tools/native-grid-city/migrate.py promote-city --pilot-gate <gate.json>
В ТОМ ЖЕ DB сохраняется только малая pilot_* выборка и pilot-journals; глобальные
2.69m source units не копируются. Новый compose без --bounds готовит весь город.
Компилятор может дописывать только новые индексы: full apply доказывает полное
равенство старого prefix materials/shapes/optics/meshes/cells/wholeObjects.
Full map root копирует clone без --bounds, затем apply с тем же gate.

Статус/сводка:
python tools/native-grid-city/migrate.py status
python tools/native-grid-city/migrate.py audit-units
python tools/native-grid-city/report_applied_journals.py
Последний отчёт читает только SQLite/journals, без повторного чтения карты.
Shard removedOwners считает записи словарей (UUID может повторяться), тогда как
cleanupUniqueOwners — число различных выбранных UUID. Это разные величины.
Read-only audit_function_metadata.py проверяет конечные внешние игровые
словари из исходной карты, не меняя план. Перед полным compose native основы
с самостоятельными игровыми функциями должны сохраняться локально либо иметь
отдельное доказанное правило; отсутствия BlockEntity для этого недостаточно.
Прогресс, exact source binding и ошибки сохраняются в plan directory. Сборки,
игра, реальные карты и full-apply pilot gate принадлежат только root.
