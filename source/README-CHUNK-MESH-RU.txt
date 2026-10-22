Dreamwalker BB: chunk-mesh.2, Minecraft 1.20.1 / Fabric / Java 17

Основа: render-fix.1, commit 2b208d4916a831cee7d931399ac673820b37cb03.
Карта уже конвертирована. Повторная конвертация, статическая миграция и
повторное удаление барьеров для этого JAR не требуются.

Установка
1. Сохранить резервную копию своего мира и заменить прежний Dreamwalker BB
   новым production JAR. Одновременно оставлять только один Dreamwalker JAR.
2. Использовать Minecraft 1.20.1, Fabric, Fabric API и GeckoLib 4.4.9.
   Проверенный минимальный профиль указан в RESULTS.json и отчёте поставки.
3. Сохранить исходный ресурс-пак bbmc_v15_resource.zip. Его pack_format старше
   Minecraft 1.20.1, поэтому нужно явно принять несовместимость при включении.
4. Не устанавливать dreamwalker-gallery-author в обычную игру: это отдельный
   вспомогательный мод только для контролируемых измерений на копии мира.

Сборка полного архива исходников
Архив включает Gradle wrapper, сборочную конфигурацию, все sourceSets,
настоящие ресурсы и production-source-excludes.txt. Один sources.jar не
содержит полный комплект ресурсов и файлов сборки.

Нужен JDK 17, JAVA_HOME должен указывать на него. Из корня проекта:
  gradlew.bat check build remapGalleryJar --no-daemon --max-workers=2
На Linux/macOS:
  chmod +x gradlew
  ./gradlew check build remapGalleryJar --no-daemon --max-workers=2

Зависимости разрешаются через Maven-репозитории из build.gradle. Если
WorldEdit Maven недоступен, передать собственный локальный API/mod JAR:
  gradlew.bat check build remapGalleryJar --no-daemon --max-workers=2 -Pworldedit_api_jar=deps/worldedit-mod-7.2.15.jar
WorldEdit используется как compileOnly; эта настройка не добавляет его в
production JAR. Параметр --offline подходит только для заполненного кэша.
Лимит сборки 3 ГиБ; лимит измерений игры остаётся 10 ГиБ, дальности 12/12.

Проверки
check запускает существующие проверки транзакций/отката. Исторический
BuilderUndoChecks исключён вместе с отсутствующим в этой версии production
BuilderUndoHistory; он не является проверкой активного renderer.
remapGalleryJar собирает отдельный QA-мод. Native capture и замеры используют
tools/prepare_city_mesh_capture.py, run_final_client.py,
launch_prepared_client.py, ensure_client_assets.py,
measure_native_client_memory.ps1. run_final_client.py также импортирует
digest/fetch из run_final_server.py. Для другого компьютера задавать пути
явно: --java-home, --minecraft-home, --gradle-home, --jar, --fixture,
--resource-pack; использовать --cached-minimal для того же минимального
профиля. Включённые launcher/asset helpers используют домашнюю папку
текущего пользователя; старые привязки к дискам и CodexControl не нужны.
QA выполняется только на независимой копии мира.

Renderer
Неподвижные source_* CompositeRootBlock используют существующие native
буферы секций. BlockEntity, ID и NBT сохраняют хранение и взаимодействия.
Внешний вид ImportedStairBlock также запекается по сохранённому состоянию
и NBT; соединения, физика и взаимодействие лестниц сохраняются. Изменения
состояния обновляют native mesh. Функциональные двери, climbing/support-
объекты и исключительные модели с выходящими за безопасную область
запросами остаются на BER.
Новый отдельный CPU/GPU mesh-кэш не создаётся. Прежние пределы кэшей
не увеличены. Выступающие модели расширяют section bounds; соседние
изменения и свет перестраивают затронутые секции.
Равные неизменяемые ownership-записи BlockEntity и shard/ledger используют
одни экземпляры после полного сравнения значений. При разборе сетевого
пакета повторные значения объединяются только внутри этого пакета;
новый постоянный кэш не создаётся. Формат NBT и игровые значения сохраняются.

Диагностические свойства (обычной игре не нужны):
  -Ddreamwalker.terrain.enabled=false — временно отключить новый owned path.
  -Ddreamwalker.terrain.pilot=minX,minZ,maxX,maxZ — ограничить его участком.
Без этих свойств механизм действует на весь город для подходящих объектов.

Подтверждённая чёрная поверхность spirelamp_0010 использовала SOLID для
прозрачных пикселей исходной PNG. Для 24 проверенных состояний fallback
палитры выбран CUTOUT. PNG, UV, tint и глобальное освещение не изменялись.
Результаты FPS, памяти и визуальных проверок см. в отчёте поставки.
