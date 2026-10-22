Dreamwalker BB streaming.5 — коллизии и подгрузка

База: 0.1.0-v12.7.4-owner32-static-city.6-chunk-mesh.3-streaming.4,
commit 28855213258c23c683cbe46e158e594dbb9caf3f.
Итоговые файлы и измерения: ../deliverables/collision-streaming-20261018/.

При установке заменить прежний Dreamwalker JAR файлом game/dreamwalker-bb-streaming.5.jar,
оставив один экземпляр мода. Minecraft 1.20.1 / Fabric / Java 17, прежние зависимости.
Готовая конвертированная карта находится в map/world, независимая резервная копия —
в backup/world. Исходный ZIP не используется как игровая карта. Конвертация заново
не нужна. Исправление освещения, DH/LOD и изменение текстур в этот выпуск не входят.

Равные формы, списки боксов и фрагментов конечного каталога разделяются напрямую
между рецептами. Временные словари его инициализации не сохраняются. У динамического
lookup коллизий слабые ключи; здоровый одиночный владелец использует сохранённый
неизменяемый footprint без промежуточного объединяющего списка. При вычислении union
пропускаются только точные повторы боксов; исходные списки/номера/фрагменты не правятся.
Для повторов OwnerDataV1 используется существующий временный пул одного чанка,
причём совпадение байтов проверяется перед повторным разбором. Payload каждого BE
по-прежнему копируется, UUID и resident-авторитет не заменяются общими значениями.

Сборка локально выполняется через C:\Users\vakir\CodexControl\Guard.ps1 -Mode Operation.
Предварительно прочитать действующие AGENTS.md, storage-mode.json и C-return-result.txt.
Среда этой работы: Java 17 из .minecraft/runtime/java-runtime-gamma; Gradle wrapper 8.8,
Fabric Loom 1.6.12; зависимости уже в локальном Gradle cache. Параметры сборки:
  gradlew.bat build --offline --no-daemon --max-workers=2
  -Pworldedit_api_jar=C:\Users\vakir\.codex\worktrees\3f07\DW\bloodborne complete\build\v127-dependencies\worldedit-mod-7.2.15.jar
Аргумент с путём передавать одной строкой с корректными кавычками.

Повторяемые инструменты (запускать через Guard, не одновременно с игровым замером):
  tools/collision-streaming-20261018/validate_shapes.ps1 — сборка и затронутые проверки;
  prepare_route.py / run_route.py / summarize_route.py — сопоставимый маршрут;
  prepare_reopen.py — одно повторное открытие сохранённого мира без повторения маршрута;
  audit_assets.py — сравнение ресурсов и неизменённых классов с точным JAR streaming.4;
  tools/city-streaming-20261018/audit_source_barriers.py — координаты из исходного ZIP;
  verify_source_barrier_absence.py — проверка оставшихся гостей и authoritative shards.

Барьеры: все 14855 исходных minecraft:barrier уже отсутствуют в исходной готовой карте.
В этой доработке удалено 0. Совместные helper-ячейки сохранены, их нельзя массово
удалять. Точное подтверждение происхождения и состояния — evidence/barriers/.

Результаты времени и памяти читать в REPORT-RU.txt и evidence/route-comparison.json.
Дедупликация каталога при запуске сама по себе не доказывает ускорение новых чанков.
