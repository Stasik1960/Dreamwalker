# Локальная проверка осенней сборки

Общие проверки выполняются wrapper Gradle 8.8 и Java17; версия проекта закреплена
в build.gradle. Рабочие исходники и генераторы сохранены в src/ и tools/.
Ресурсы больших моделей можно читать напрямую через
`-I tools/verification-direct-resources.init.gradle`.

Для optional клиентской и серверной проверки init-скрипты читают локальный
`tools/autumn-client-dependencies.json`. Файл исключён из Git: пути зависят от ПК.
Вместо него опубликован `autumn-client-dependencies.example.json` с относительными
путями. Скопируйте пример в файл без .example и укажите свои пути к модам.
Архив Dependencies.zip находится внутри опубликованного полного комплекта.
В нём common и client-optional; их можно извлечь в local/autumn-dependencies/.

Для Loom development runtime вложенные библиотеки из jars Fabric-модов нужно
извлечь отдельно в build/autumn-client-deps/. Пример фиксирует их точные имена,
включая DiagonalBlocks и PuzzlesAccessAPI. В обычной игре Fabric Loader сам
загружает эти библиотеки из родительских JAR: отдельно ставить их нельзя.

Клиентский генератор prepare_autumn_client.py копирует мир в отдельный save.
Init-скрипт verification-autumn-client.init.gradle создаёт runAutumnClient;
dev-мод tools/autumn-capture автоматически снимает шесть кадров и завершает игру.
Его исходники не входят в основной JAR. verification-launch-server.init.gradle
создаёт runLaunchServerSmoke и работает только в build/launch-server-smoke/.
Для server smoke заранее подготовьте там копию Main-City и настройки владельца;
EULA принимает сам владелец. Production-сервер эти команды не затрагивают.

Галерея собирается `tools/build_launch_gallery.py` в новый
`build/autumn-launch/Gallery-Compact-Final`: между краями площадок три пустых блока.
`tools/assemble_launch_package.py` принимает `--gallery`, `--output` и
`--presentation`. Output должен быть новым каталогом вне входных данных;
по умолчанию это `../output/Bloodborne-Launch-Base-Compact`. Проверенная презентация
берётся из `../releases/Bloodborne-Blocks/launch-base-2026-10-01/Autumn-Variants.pptx`.
Повторно использовать старый output нельзя: сборщик отклонит его, чтобы прежние
файлы не попали в новый комплект. В ZIP галереи используется новый каталог
`Approval-Gallery-Compact`; его следует открывать отдельным миром.

Перед публикацией архив полного комплекта с корнем `Bloodborne-Launch-Base`
обрабатывается `tools/publish_launch_bundle.py SOURCE.zip NEW_PUBLIC.zip`.
Скрипт создаёт новую копию без профилей/истории игроков и чата; проверяет CRC,
контрольные суммы и сохранность остальных файлов мира. Публикуется эта копия.
