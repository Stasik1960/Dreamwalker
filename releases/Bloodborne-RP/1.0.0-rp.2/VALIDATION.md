# Выполненная проверка RP2 — 2026-10-06

Проверены `bloodborne_rp 1.0.0-rp.2` и одинаковая RP-часть `bloodborne_dw 1.0.0-complete.2`, с прежними версиями зависимостей. Полный список исправлений и принятых решений — [BUGFIX-20261006-RU.md](docs/BUGFIX-20261006-RU.md).

- Standalone: `check build rpGameTest` с полными локальными ресурсами и Bloodborne-Blocks — **7 JUnit и 13/13 GameTests**, без ошибок. Проверки покрывают копирование/размещение/удаление, права, отказ стрелам и окружению, коллизии при настоящем перемещении, открытые двери/сундуки, точку прицела и стены, исходные исключения, люстры, Scale, legacy ConnectionId/EntityState, импульс рычага и сохранение его таймера. Сохранены прежние тесты оружия, монстров и фонарей.
- Complete: **8 JUnit и 18/18 GameTests**, включая пять проверок числовых блоков/ALT/WorldEdit и сосуществование с Bloodborne-Blocks.
- Готовый remapped complete JAR дополнительно запущен на обычном Fabric-сервере с копией уже выданной галереи и Bloodborne-Blocks. Два запуска завершились с кодом 0: блок галереи 00002 сохранил ID, тестовая RP-дверь с Scale 1.6 загрузилась повторно. SHA-256 проверенного JAR: `212b03c64e751c76f88c65783ca078f7f20aa9e415c06978db17a9fbd1541300`. В этой дополнительной проверке проверена одна платформа, а не повторный обход всех 580.
- Два отдельных headless запуска standalone `runServerSmoke` в новой тестовой папке сохранили и перечитали мир: `BBRP_RESTART phase1`, затем `BBRP_RESTART complete`, штатное завершение. Помимо прежних фонарей, маршрута, монстра, оружия и блока Bloodborne-Blocks проверены Scale 1.6, yaw 45°, Open, Locked двери и UUID-связь рычага. Использован изолированный loopback-профиль с тестовыми игроками, без production/Gravit.
- Реальный Fabric-клиент с полной геометрией открыл тестовый мир, сохранил активный idle LOOP Huntsman A после 12 секунд и на протяжении проверки. **960 тиков, 2823 отрисованных кадра, 24 снимка оружия**: три семейства × две формы × две руки × первое/третье лицо. Подтверждены Rabid Dog scale 0.6, дверь и масштабированный фонарь. Финальный запуск штатно завершился. Инструмент находится в `tools/visual-regression`; он не включается в production JAR.
- `verify_original_contracts.py`: 96 renderer-контрактов, 293 клипа, 242 привязки ресурсов и шесть display-форм сверены с исходным JAR 6.0. Оригинальный Forge-клиент отдельно загрузил моды/ресурсы в изолированной папке; его собственные ошибки Molang и отсутствующие звуки подтверждены логом. Оригинальный игровой мир не открывался, этот запуск ограничен 120 секундами. Полного сравнения двух игровых сессий нет.
- Независимый read-only review не обнаружил блокеров. Production RP-классы standalone и complete совпадают. Промежуточная combined-проверка 17/18 использовала устаревший скомпилированный обработчик повреждения; после пересборки 18/18. Временная Windows-блокировка JAR при одновременном чтении устранена повторной сборкой после завершения читателя.

Команды standalone (полный JDK17 в JAVA_HOME, `<BB.jar>` — локальный `bloodborne-blocks-2.1.0-repair-catalog.1.jar`):

```powershell
.\gradlew.bat -PprivateAssets=private-assets -PcoexistenceJar="<BB.jar>" check build rpGameTest
.\gradlew.bat -PprivateAssets=private-assets -PcoexistenceJar="<BB.jar>" -PsmokeRunDir=build/server-restart-rp2 -PrestartExpected=phase1 runServerSmoke
.\gradlew.bat -PprivateAssets=private-assets -PcoexistenceJar="<BB.jar>" -PsmokeRunDir=build/server-restart-rp2 -PrestartExpected=complete runServerSmoke
.\gradlew.bat -I tools/visual-regression/init.gradle -PprivateAssets=private-assets runRegression
python tools/verify_original_contracts.py --jar "<original.jar>" --javap "<JDK17>/bin/javap.exe" --assets private-assets --output build/original-parity-check.json
python tools/export_blockbench_assets.py --jar "<original.jar>" --ported-assets private-assets --output "<fresh-export-directory>"
```

Клиентский harness открывает только заранее подготовленную копию тестового мира `build/regression-client/saves/rp-regression-world`. После успешного `rpGameTest` можно скопировать его отдельный мир `build/gametest/world` туда; не используйте рабочую карту. Он создаёт тестовые объекты над миром. Для restart требуется новая тестовая папка с EULA, level-name=rp-smoke-world, loopback и свободным портом; проверенный мир намеренно не перезаписывается. Complete проверялся из своего каталога командой `./gradlew.bat -PcoexistenceJar="<BB.jar>" check build rpGameTest`.

Остаются границы проверки: полный бой всех 18 монстров вручную, продолжительная нагрузка, production-мир, GravitLauncher и Docker/Linux не проверялись. Коллизии соответствуют базовым box оригинала и не повторяют каждый куб геометрии; часть декора в оригинале намеренно не имеет коллизий. Подробности экспорта и различия механики указаны в отчёте.

## Историческая проверка RP1 — 2026-10-04

Проверен отдельный мод `bloodborne_rp 1.0.0-rp.1`, Minecraft 1.20.1, Fabric Loader 0.16.10, Fabric API 0.92.9+1.20.1, GeckoLib Fabric 4.4.9. Сборка выполнена полным Temurin JDK 17.0.20.1+1 через wrapper Gradle 8.8. Системная Java и версии соседних проектов не менялись.

Для совместимости использован существующий локальный `bloodborne-blocks-2.1.0-repair-catalog.1.jar`, SHA-256 `4fdaa5a7bc4584f5ba737773f23714408e7442706c9cb3d961998b01e9ad165d`. Его исходники и пользовательская рабочая ветка не изменялись.

## Результаты

- `check build`: успешная компиляция и сборка; **6 JUnit-тестов**, без ошибок. Проверены границы дистанции атак, имена/лимиты маршрутов, контекст и подбор анимаций.
- `rpGameTest` с обоими модами: **9 обязательных GameTests прошли**. Все 18 монстров и яйца зарегистрированы; здоровье и урон обычные Minecraft; frozen-NBT сохраняется. Проверены формы оружия, сохранение прочности/имени/зачарований/сторонних атрибутов и огненный Boom Hammer. Фонари проверены на успешный переход, закрытый маршрут, повтор токена, дистанцию, очистку при disconnect, лаву, огонь и низкий потолок. Проверены замки, связанный рычаг, отказ закрытия занятого прохода и ограниченное восстановление NBT.
- `runClientSmoke` с обоими модами и полными ресурсами: клиент запустился, выполнил загрузку ресурсов и штатно завершился. `BBRP_SMOKE complete ok models=119 animations=49 catalog=102 weapons=6`. Нет резервных моделей/текстур для записей каталога. Проверены все запрошенные пути и имена анимационных клипов в кэше GeckoLib. Числа 119/49 включают ресурсы GeckoLib; локально импортированы **101 geo, 32 animation JSON, 64 используемые текстуры, 400 OGG и sounds.json**.
- Два отдельных запуска `runServerSmoke`: первый сохранил мир и завершился, второй выдал `BBRP_RESTART complete`. После настоящего перезапуска сохранены две сущности фонарей с UUID, названия и маршрут, frozen-монстр с обычным HP, трансформированное оружие с именем/прочностью/NBT и логический блок `bloodborne_blocks:o_barrel`. Проверка использовала loopback, `online-mode=true`, без RCON; сервер не оставлен работать.
- Независимый read-only review выявил несовпадение ID фонаря, несогласованные лимиты, обход замков, неверные состояния анимации, рассинхрон коллизии, потерю узлов при удалении и опасное прибытие в огонь. Эти замечания исправлены; для основных сценариев добавлены проверки.

Первый эксперимент сохранения использовал технический `architecture_part` без владельца. Он сам удаляется по правилам Bloodborne-Blocks, поэтому проверка заменена на действительный логический root `o_barrel`; затем повторены обе фазы в новом тестовом мире. Это не регрессия нового мода.

## Команды

В каждой команде использован `-Dorg.gradle.java.home=<путь к полному JDK17>`. `<BB.jar>` — указанный выше локальный артефакт, `private-assets` получен импортёром из предоставленного JAR 6.0.

```powershell
.\gradlew.bat check build
.\gradlew.bat -PprivateAssets=private-assets -PcoexistenceJar="<BB.jar>" check build rpGameTest
.\gradlew.bat -PprivateAssets=private-assets -PcoexistenceJar="<BB.jar>" runClientSmoke
.\gradlew.bat -PprivateAssets=private-assets -PcoexistenceJar="<BB.jar>" -PrestartExpected=phase1 runServerSmoke
.\gradlew.bat -PprivateAssets=private-assets -PcoexistenceJar="<BB.jar>" -PrestartExpected=complete runServerSmoke
```

Для restart-smoke нужен новый локальный каталог `build/server-restart-verified`, EULA и `server.properties` с `level-name=rp-smoke-world`, loopback и свободным портом. После успешной второй фазы повторный запуск специально откажется изменять уже проверенный мир. Итог проверяется marker-файлом и логом, поскольку Minecraft может вернуть код 0 после обработанной серверной ошибки.

## Границы проверки

Клиентская проверка подтверждает запуск и загрузку геометрии/анимаций, но не заменяет визуальную проверку боя, положения оружия в руках и больших декораций внутри мира. Длительный игровой баланс, многопользовательская нагрузка и реальное одновременное поступление C2S-пакетов не проверялись. Игровые сетевые тесты вызывают тот же серверный endpoint с mock server players; очередь пакетов отдельно проверена чтением кода.

Окно и одноразовые контексты фонаря очищаются при disconnect/restart; его cooldown относится к текущей сессии. Внешний GravitLauncher, production-мир и Docker/Linux не запускались. Новый мод не затрагивает их авторизацию. Исходные Forge-миры, сущности и ItemStack-ID автоматически не мигрируют.

Сундук пока является открываемым механизмом без инвентаря/генерации добычи. Лифт, лестницы, транспорт и большинство декораций статичны. Файлы художественного пакета исправлены только в локальном импорте; исходные ZIP/JAR сохранены.
