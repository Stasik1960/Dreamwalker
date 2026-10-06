# Bloodborne DW

Проект — каталог `bloodborne complete`. Это Fabric-мод для Minecraft 1.20.1: Java 17, Loader 0.16.10, Fabric API 0.92.9+1.20.1 и GeckoLib Fabric 4.4.9. Из этого каталога сборка выполняется командой `./gradlew.bat check build` (Windows) или `bash ./gradlew check build` (Linux). Финальные файлы находятся в `../releases/bloodborne-dw/1.0.0-complete.1`: полный JAR, city/gallery world, source ZIP и ALT template.

Устанавливаются полный `bloodborne-dw` JAR, Fabric API и GeckoLib. Старый Bloodborne-RP заменяется complete; Bloodborne-Blocks может сосуществовать. В complete сохранены 18 мобов, 3 двухформенных оружия, путешествие между лампами и 76 entity-декоров. Стандартные HP остаются; stamina, economy и rally не добавляются.

Есть 580 native-family блоков с числовыми ID и creative items. ALT-пути: `assets/bloodborne_dw/models/alt/<ID>/<originalmodelpath>.json`, перечислены в `catalog.alt_model_paths`; при reload приоритет у resource pack. Слои семейства задаются в `alt_render_layers.json`. Полный art включён по запросу пользователя; права или лицензия этим не заявляются, см. `ASSET-NOTICE.md`.

Визуальные команды: `/bb debug`; `/bb visual <base|alt> look|all|area|selection|reset`. Reset удаляет все прежние правила и создаёт одно глобальное правило выбранного вида с указанным фильтром. ALL действует только в текущем измерении, включая незагруженные области; selection использует WorldEdit 7.2.15. Последнее подходящее правило имеет приоритет. Debug доступен игрокам; изменения вида требуют permission level 2.

```mcfunction
/bb debug
/bb visual alt look
/bb visual base look
/bb visual alt all
/bb visual alt all 00001 00005
/bb visual base reset
/bb visual alt area -80 330 -1104 140 370 -891 00001 00005
//sel cuboid
//pos1
//pos2
/bb visual alt selection
/bb visual base selection 00001
```

ID перечисляются через пробел; допустимы также `bloodborne_dw:00001`. У `area` обе крайние точки должны быть загружены. Без фильтра правило распространяется на все блоки complete. Для консоли сервера задайте нужное измерение через `execute in ... run bb visual ...`. Получить блок можно через `/give @s bloodborne_dw:00001`, а все 580 блоков доступны во вкладке Bloodborne DW.

Для установки распакуйте один из архивов мира прямо в каталог сохранения: `level.dat` должен находиться в корне этого каталога. Карта-галерея — отдельное сохранение с расширенной высотой; её spawn находится над городом. Для клиента и dedicated-сервера требуются три JAR: complete, Fabric API и GeckoLib. В локальной папке поставки они уже собраны в `mods`. WorldEdit необязателен и требуется только для команды `selection`. Forge, старые GeckoLib/Oculus/Rubidium 1.18.2 и исходный BBMC ресурс-пак не нужны.

ALT template пока повторяет BASE. Чтобы создать свой вид, замените нужные JSON из `models/alt/<ID>/...` и добавьте текстуры в собственный namespace; пример полного нового model JSON и декларации слоя приведён в [документе решений](docs/HUMAN-DECISIONS-RU.md). Подключите архив как обычный ресурс-пак или вложите эти пути в companion Fabric-мод. Перезагрузите ресурсы F3+T. ALT меняет изображение, а механика, состояния и коллизия остаются у исходного семейства.

Проверки релиза: 14 dedicated GameTest, 7 модульных тестов, обычный remapped Fabric-сервер с городом/галереей и повторной загрузкой, а также клиент с внешней полной ALT-моделью. Все 580 семейств не проходили ручной осмотр по пикселям. Подробности: [проверки](VALIDATION.md), [отчёт процесса](docs/PROCESS-RU.md), [решения и дефекты](docs/HUMAN-DECISIONS-RU.md), [работа с галереей](docs/GALLERY-EDITING-RU.md).
