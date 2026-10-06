# Проверка версии 1.0.0-complete.1

Фактически выполнено 2026-10-06. Java 17.0.20.1, Gradle wrapper 8.8, Loom 1.6.12, Yarn 1.20.1+build.10, Fabric Loader 0.16.10, API 0.92.9+1.20.1, GeckoLib Fabric 4.4.9. Версии не обновлялись в ходе проверки.

## Сборка и native/RP тесты

Из папки `bloodborne complete`, с JAVA_HOME на Java 17:

```powershell
.\gradlew.bat check build runGametest `
  '-PcoexistenceJar=C:\Users\vakir\Documents\ChatGPT\DW\Bloodborne-Blocks\build\libs\bloodborne-blocks-2.1.0-repair-catalog.1.jar' `
  '-PworldEditJar=local-inputs/worldedit-mod-7.2.15.jar' --no-daemon
```

Результат: BUILD SUCCESSFUL, 7 unit tests, 14/14 GameTests. Доказательство: `build/final-release-validation-command.log` и XML в `build/test-results`; сводка включена в release validation evidence. `coexistenceJar` и `worldEditJar` — необязательные локальные зависимости для проверок, они не нужны для обычной сборки проекта.

GameTests проверяют все 580 native family defaults/schema, BASE/ALT collision каждой комбинации state, jump multiplier, loot identity, Stainable color; actual fence connection, двери, numeric plant/pot, покрытые path/farmland, powder рядом с водой, sponge, сохранение redstone tick. RP-тесты проверяют оружие, AI/HP, декорации/фонари и travel constraints. С реальным WorldEdit проверены отказ для незавершённого выделения без записи правила и успех для cuboid с проверкой внутри/снаружи.

## Клиент и внешние ресурсы

```powershell
.\gradlew.bat runClientSmoke '-PsmokeWorld=DWGallerySmoke' '-PsmokeAlt' `
  '-PcoexistenceJar=C:\Users\vakir\Documents\ChatGPT\DW\Bloodborne-Blocks\build\libs\bloodborne-blocks-2.1.0-repair-catalog.1.jar' --no-daemon
```

Тест использует временную копию галереи `build/client-smoke/saves/DWGallerySmoke` и build-only ресурс-пак `DW-ALT-test.zip`, выбранный в options.txt. Пак заменяет leaf-модели 00001 полной gold cube model и объявляет ALT solid для всех семейств. Его нет в поставке; публикуемый ALT template повторяет BASE.

`build/final-world-client-command.log`: BUILD SUCCESSFUL; `DW_SMOKE catalog=580 wrapped=32156`; `DW_ALT_LAYER_SMOKE preserved_base_alpha=490`; `DW_WORLD_SMOKE externalAlt=true ticks=160`. Проверена загрузка реального мира, команда ALT/S2C, baked resource replacement и сохранение BASE alpha. RP: models=119, animations=49, catalog=102, weapons=6, fallbackModels=0, fallbackTextures=0. Screenshot: [qa-gallery-alt.png](docs/qa-gallery-alt.png).

Существующие ошибки исходных blockstate selectors вызывают missing-variant warnings; они посчитаны отдельно и не названы ошибками, внесёнными новым кодом. Ручной pixel review всех 580 семейств не выполнен.

## Проверка исходной конверсии

Использован Python с numpy и Pillow. Команды после создания свежего конвертированного архива:

```powershell
python tools/complete_verify_world.py 'C:\Users\vakir\Downloads\bbmc_v16_map (1).zip' build/complete-world/Bloodborne-DW-v16-attempt2.zip --catalog src/main/resources/assets/bloodborne_dw/catalog.json --report docs/world-verification.json
python tools/complete_verify_entities.py 'C:\Users\vakir\Downloads\bbmc_v16_map (1).zip' build/complete-world/Bloodborne-DW-v16-attempt2.zip --output docs/entity-verification.json
python tools/complete_validate_assets.py --vanilla-client 'C:\Users\vakir\.gradle\caches\fabric-loom\1.20.1\minecraft-client.jar'
```

Все команды завершились успешно. `world-verification.json`: 35 890 terrain chunks, 861 360 paletted sections, 58 252 513 converted cells, errors=[], valid=true. Сравнён полный typed NBT после только разрешённых изменений: palette Names/visual, лампы и lighting cache; индексы, координаты, свойства, block entities/inventories, heightmaps и biomes сохранены.

`entity-verification.json`: 11 entity region files, 160 entity chunks, 264 сущности, полный typed NBT и level.dat; errors=[], valid=true. Разрешены только namespace migration, documented RP mob freeze и совместимость Open/Locked ключей. Для unrelated сущностей ничего не исключено из сравнения. Сохраняются UUID/позиции/пассажиры/порядок/прочие поля/временные метки.

`asset-reference-verification.json`: 580 states/items/loot, 6093 models с ALT wrappers, 192 native tag files. Неожиданных отсутствующих model/texture/data refs нет. Пять групп исходных texture defects перечислены отдельно. Source missing models и 93 undefined occupied states отражены в `input-missing-usage.json`, `input-model-closure.json` и `asset-import-report.json`.

## Финальные metadata и обычный Fabric-server

Финализатор выполнялся отдельно для city/gallery; [city-finalization.json](docs/city-finalization.json) и [gallery-finalization.json](docs/gallery-finalization.json) подтверждают byte identity всех 76 MCA-файлов каждого входного мира. Изменяются level/datapack metadata и manifest. Некорректные source automatic tags отключены; manual encounter functions сохранены. Исходные ZIP не изменены.

Готовый remapped JAR, а не dev classpath, установлен в обычный Fabric-server 1.20.1 вместе с API, GeckoLib, Bloodborne-Blocks и WorldEdit. На временных копиях финальных worlds выполнено:

```powershell
python tools/complete_smoke_runtime.py --java '<Java17>\bin\java.exe' --city build/publish-worlds/Bloodborne-DW-v16.zip --gallery build/publish-worlds/Bloodborne-DW-v16-gallery.zip --case-prefix publish-
```

В `build/production-smoke` предварительно подготовлены обычные Fabric server.jar/launcher/libraries. Helper работает только на 127.0.0.1 в ограниченных по времени процессах, посылает save-all/stop и завершает процесс при таймауте. Его пути локальных test dependencies следует адаптировать при запуске на другой машине. Никакой production deploy или смены Gravit auth не было.

Результат в [production-smoke-verification.json](docs/production-smoke-verification.json): все четыре load/save/restart процесса exit code 0, ошибок datapack нет, global ALT persistent state сохранён. В обоих gallery runs подтверждены 580 roots, 580 sign BlockEntities с front_text и все семь provider types. Чанки галереи специально загружались в тестовой копии перед проверкой; исходный release ZIP не перезаписан результатом теста.

## Воспроизводимость и пределы

Полная исходная папка содержит ресурсы и собирается без повторного импорта приватных файлов. Java-only sources JAR дополняется полным source ZIP. Для повторной конверсии мира нужны два исходных ZIP пользователя; SHA закреплены. Для импорта есть `docs/input-map-analysis.json`, closure и frozen catalog. Генераторы требуют новый output и не перезаписывают исходные архивы. Checksums release/local поставок находятся рядом с артефактами.

Git attributes сохраняют bytes ресурсов и frozen catalog без автоматической смены концов строк. `git diff --cached --check` для написанного кода/документов проходит; полный вызов также отмечает исходную пустую строку в конце `pumpkinblur.png.mcmeta`. Этот исходный файл оставлен неизменным: это форматирование metadata, не ошибка модели или сборки.

Независимый integration review завершён без остающихся замечаний после исправлений и документирования исключений. Linux/Docker runtime, GravitLauncher, большой multiplayer, performance при 4096 overlapping rules и ручной осмотр каждого состояния не проверялись. ALT художественного набора и обработчика будущих ручных composite-правок пока нет — сейчас подготовлены extension points и исходная галерея.
