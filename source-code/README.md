# Dreamwalker BB — visual-physics.5

Текущая версия: `0.1.0-v12.7.4-visual-physics.5`, добавлена «Завеса фей»
(`bloodborne_dw:dw_faerie_veil`) по выбранному варианту «Туман и светлячки».
Непрозрачный проходной блок, выделение/удаление только в креативе, без BE,
игровых частиц и тикера каждого блока. Одна общая анимированная текстура
образует композицию 8×8 блоков с выбором UV по координатам мира.
Проверки и текущий статус: `faerie-evidence/acceptance.json`, `REPORT-RU.txt`.
Основной JAR: `gradlew.bat remapJar` (JDK17). Отдельный QA: `remapFaerieQaJar`.
Карта прежняя; функции .4 и более ранние исправления сохранены.

## База visual-physics.4

Текущая версия: `0.1.0-v12.7.4-visual-physics.4`. DW-барьер, DW-свет, тьма и
декоративная вода выделяются/удаляются только в креативе. Вода не течёт, не
замедляет, не даёт плавать; поддерживает ванильные Waterloggable-хосты без
замены их типа/данных и использует текстуру воды из активного ресурспака.
Проверки и ограничения: `utility-evidence/REPORT-RU.txt`, `acceptance.json`.
Отдельный тестовый мод: `gradlew.bat remapUtilityQaJar`.
Карта прежняя. Зелёный природный портал ожидает выбора анимированного визуала.

## База visual-physics.3

Текущая версия: `0.1.0-v12.7.4-visual-physics.3`. Восстановлена посадка на
целые скамейки раннего Bloodborne-Blocks: ПКМ свободной рукой — сесть,
Shift — встать, три места. Старые объекты и статические скамьи готовой карты
поддерживаются без конвертации и изменений моделей/коллизий.
Статус приёмки: `bench-evidence/acceptance.json`; описание и продолжение работы:
`bench-evidence/REPORT-RU.txt`, `bench-evidence/HANDOFF-RU.txt`.
Сборка игрового JAR: `gradlew.bat --no-daemon remapJar` (JDK 17).
Отдельный тестовый мод: `gradlew.bat remapBenchQaJar`; в игровой JAR не входит.
Карта из `native-grid-city-visual-physics-20261025` остаётся совместимой.
Ветка исходников: `codex/bench-seating-20261025`.

## База visual-physics.2

Текущая версия: `0.1.0-v12.7.4-visual-physics.2`, Minecraft 1.20.1 Fabric.
Статус проверки и изменения описаны в `fix-evidence/REPORT-RU.txt` и
`fix-evidence/HANDOFF-RU.txt`. До появления итогового PASS это рабочие исходники.
Готовая карта из релиза `native-grid-city-visual-physics-20261025` совместима:
её повторная конвертация для этого исправления не нужна.

Для сборки нужен JDK 17: `gradlew.bat --no-daemon remapJar`.
Выходной JAR находится в `build-native-visual/libs/`.
Если Maven EngineHub недоступен: `python tools/prepare_fix_compile_api.py`, затем
`gradlew.bat --no-daemon -Pworldedit_api_jar=build-tools/worldedit-mod-7.2.15.jar remapJar`.
Этот WorldEdit используется только при компиляции. Для Windows без JDK предусмотрен
`python tools/bootstrap_fix_runtime.py` с проверкой SHA256 официального архива.
Тестовый мод собирается отдельно: `gradlew.bat remapFixQaJar` и в игровой JAR не входит.

Версии внутренних адаптеров: Sodium 0.5.13 вместе с Indium 1.0.36,
Lithium 0.11.4, опционально WorldEdit 7.2.15 и Reach Entity Attributes 2.4.0.
Остальные версии внутренних API требуют отдельной проверки адаптера.
Скрипты игровых профилей содержат пути этого ПК; перед повтором на другом ПК
их нужно адаптировать. Производственный мод от этих путей не зависит.

## История исходной базы (статический город .6)

Текущий исходный проект соответствует игровому модулю `bloodborne_dw` версии `0.1.0-v12.7.4-owner32-static-city.6`. Исходная база перенесена из комплекта `dw-city-handoff-20261017-r1` (commit `581e340b0baa4546635002249ed29ed4264c2855`) с сохранённым исправлением OwnerDataV1; поверх применена завершённая статическая миграция.

[Карта, мод, SHA256 и фактический статус](../releases/Dreamwalker-BB/v12.7.4-static-city.6-20261017/README.md). Это экспериментальный кандидат: игровая приёмка ещё не выполнена. Старые TASK/PROGRESS/docs остаются историческими материалами и не заменяют этот статус.

## Сборка

Нужны Git LFS, JDK 17 и доступ к закреплённым зависимостям Gradle. Из этой папки: `./gradlew --no-daemon remapJar sourcesJar` (Windows: `gradlew.bat`). Выходные файлы находятся в `build/libs/`. При необходимости локальный WorldEdit API передаётся как `-Pworldedit_api_jar=/path/to/worldedit-mod-7.2.15.jar`; он используется только для компиляции.

Активные source roots: `src/architecture/java`, `src/rp/java`, `src/architecture/resources` плюс ресурсы RP. Пути ресурсов сделаны относительными; локальные build/cache/world папки в исходники не включены. Версия соответствует опубликованному бинарному JAR. Сборки, игровые прогоны и тесты ради загрузки в main повторно не запускались; существующие результаты сборки и миграции приложены к выпуску.

## Инструмент миграции

Отдельные author и geometry-export sourceSets находятся в `tools/static-city-20261017`. Их можно собрать через `-I tools/static-city-20261017/portable-static.init.gradle remapStaticAuthorJar`. Инструмент не входит в игровой JAR. Его SHA-binding пересоздаётся для фактически собранного game JAR.

Скрипты в `tools/static-city-20261017/materials` сохранены как материалы разработки с исходной структурой восстановленного комплекта. Это не готовая команда повторной миграции из произвольного clone: пути, подготовка, operationId и SHA требуют явной адаптации к отдельной копии мира. Полная миграция данного кандидата уже завершена; повторять её не нужно.
