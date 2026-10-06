# Выполненная проверка RP2 — 2026-10-06

Проверены `bloodborne_rp 1.0.0-rp.2` и одинаковая RP-часть `bloodborne_dw 1.0.0-complete.2`, с прежними версиями зависимостей. Полный список исправлений и принятых решений — [BUGFIX-20261006-RU.md](RP-BUGFIX-20261006-RU.md).

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
