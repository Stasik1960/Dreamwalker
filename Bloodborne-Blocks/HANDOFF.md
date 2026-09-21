# Current technical snapshot — 2026-09-21

Актуальная точка входа: [TECHNICAL-SNAPSHOT-2026-09-21.md](docs/TECHNICAL-SNAPSHOT-2026-09-21.md).
Текущие manifests: 256 семейств / 1324 состояния / 1631 правило. Последняя
конвертация `ether-current-20260921`: 59792 принятых правила, из них 36547
с несколькими исходными позициями; это не полная визуальная приёмка.
Полный ledger опубликован через LFS. Пользователь отдельно разрешил публикацию
полной конвертированной карты. Ниже — исторический checkpoint предыдущего этапа;
его цифры и ограничения публикации не описывают новый snapshot.

# Historical checkpoint: logical objects 2.1.0-alpha.1 — 2026-09-21

Продолжается текущий пользовательский план структурной нормализации; не создавать
мод заново и не повторять исходную модульную миграцию. Ниже сохранена история
1.2.x, она не описывает актуальный результат. 21 сентября пользователь разрешил
commit/push текущего результата на GitHub. Это не разрешение на запуск Minecraft,
изменение оригинала мира или публикацию копий мира/приватных ledgers.

- Актуальное состояние и честная матрица всех 23 пунктов:
  `docs/LOGICAL-OBJECTS-STATUS.md`. Не считать все пункты завершёнными.
- Текущий генератор: 76 логических объектов / 388 состояний / 546 точных правил
  миграции. Все 503 legacy и 46236 v2 ID сохранены. Три группы ориентаций используют
  `face` + `facing`; удалены только четыре экспериментальных дублирующих o_* ID.
- Валидированная копия: `build/logical-world/Ether-Bloodborne-logical-v3-mounts`;
  отчёт `build/logical-world/Ether-logical-v3-mounts-report.json`. 24375 преобразований,
  независимая полная проверка карты пройдена. Повтор в `*-mounts-repeat` дал 0
  преобразований и побайтово идентичные 186 файлов. Оригинал не изменён.
- Исходник конвертации: локальный `Ether-Bloodborne-2.0.2-positions.zip`,
  SHA256 `c517dfeb52c4d13bdbe90e02a93ac00416354eb89313a9d1377f24823af6d0e9`.
- Четыре Java data/registry проверки и Python регрессионные проверки пройдены.
  Gradle: BUILD SUCCESSFUL in 20m 29s, 11 tasks executed. Проверка mixin/refmap
  готового JAR и `check_packaged_resources.py` пройдены (включая Python -O).
  Новый JAR `build/libs/bloodborne-blocks-2.1.0-alpha.1.jar`, 93588297 байт.
  SHA256 `939a472f3cbfc3bd3052a60655d5392cfda91d278015cf4971d43fe404fb41c4`.
- Полный внешний аудит ресурсов пройден: 96170 моделей / 46816 blockstates,
  отсутствующих concrete model/parent/texture и активных texture-переменных — 0.
  51 неиспользуемый шаблонный alias отмечен отдельно в `build/external-resource-audit.json`.
- Незавершённые семейства перечислены в матрице статуса. Для столба №10
  дополнительно запрошены координаты или targeted ID; похожие модели не признаны
  доказанным соответствием. Minecraft не запускался, игровые взаимодействия
  не проверены. Визуальные офлайн-листы — `build/logical-preview-mounts`.
- Java17: `build/toolchain/jdk-17.0.20.1` либо установленный JDK 17 через JAVA_HOME.
  Wrapper8.8, версии Fabric/Yarn/Loom неизменны. Python с NumPy/Pillow.
  Ванильный 1.20.1 client JAR для генератора задаётся через BLOODBORNE_VANILLA_JAR;
  обычно он находится в локальном Gradle cache `caches/fabric-loom/1.20.1/`.
- Ресурсы текущей упаковки заморожены; staging сравнен полностью (190143 файла).
  Выполненная команда: `.\gradlew.bat check build -x processResources --no-daemon --console=plain`.
  Для чистой сборки использовать обычный `check build` без исключения.
- Большие ledgers, координаты и копии мира остаются под ignored `build/`.
  Старые каталоги validated/idempotence/second-pass/v3-final — диагностическая история.

## Historical record (superseded)

# Bloodborne Architecture 1.2.0 — 2026-09-17

Рабочие исходники: эта папка. Fabric 1.20.1 / Yarn build.10 / Java 17 /
Gradle wrapper 8.8 / Loom 1.6.12. Не запускать Minecraft: пользователь запретил.

Последний запрос: убрать каменный мусор spruce_button, убрать физику кустов,
упростить формы, объединить повёрнутые дубликаты с обновлением карты,
починить установку/лестницы, добавить сидение на лавках, оптимизировать.

Реализовано:
- Сохранены 503 registry ID; 476 видимых предметов после скрытия пустых,
  removed spruce_button и 16 доказанных aliases. Старые ID не удалены из реестра.
- Коллизии 22562 коробки вместо 171307; outline 20287 вместо 181863.
  Один outline-box на клетку; физика максимум 12. Кусты/ветви без столкновений.
- 240 generic ID получили facing; 42269 состояний, 2644 общих профиля.
  Lookup geometry по BlockState кешируется без сортировки строки на каждом hit.
- Старые 18968 визуальных состояний проверены против ad80f42: не смещены.
  Удаление отображения spruce_button — намеренное исключение.
- 16 aliases /112 состояний проверены по текстурированным граням и offset.
- Placement: рабочая копия ItemStack, единый финальный state до preflight,
  принудительная позиция root без скрытого сдвига ItemPlacementContext,
  нормализация поворота/assembled/воды/настоящих slab/stairs. Тонкие новые
  модели не создают phantom cells ниже пола.
- LivingEntity mixin только для игроков/лестниц, проверяет близость к плоскости.
  Sectional ladder — waxed_exposed_cut_copper_stairs shape=straight (оба half).
- Лавка nether_brick_stairs: серверное одиночное seat entity, без сохранения,
  очистка при dismount/logout/удалении, client-only EmptyEntityRenderer.
- /bloodborne update 32 preview|apply: мусор, aliases, helper repair; только
  загруженный куб radius1–32, конфликты пропускаются, чужие блоки не затираются.
  Старый /bloodborne repair оставлен. Мир пользователя не открыт/не менялся.

Проверки: geometryCheck на всех состояниях, проход двери 0.6x1.8 во всех
4 направлениях, soft collision, лимиты форм, непрерывность ladder cells;
validate_architecture.py (4254 JSON,497 bounded items); check_migration.py;
Gradle --offline build SUCCESS; ZIP/metadata/resources/refmap/SHA256 проверены.
Клиент/сервер/мир/FPS/реальные события взаимодействий не запускались.

Готовый JAR: ../releases/Bloodborne-Blocks/bloodborne-blocks-1.2.0-mc1.20.1.jar
Размер: 4028055 байт. SHA256: d28afcf8bf01a945387c53d46cbf761f2994b51f37929d3735f97d77466f8536

Публикация Bloodborne 1.2.0 в origin/main разрешена пользователем; исходники, готовый JAR и README подготовлены для общего коммита.
Главный README, README мода и releases README обновлены. Отчёт:
docs/OPTIMIZATION-1.2.0-RU.md. Старые отчёты относятся к старым версиям.
Не включать соседний .gradle-local в коммит.

Историческая сборка: JAVA_HOME указывал на локальный JDK 17;
GRADLE_USER_HOME — на отдельный локальный cache; ./gradlew.bat --offline build.
Для Python требовались NumPy/Pillow. Пути конкретной рабочей машины опущены.
Генераторы читают Minecraft JAR только как ZIP. prepare_architecture.py НЕ
перезапускать. optimize_architecture.py идемпотентен; затем generate_collision.py,
find_aliases.py, check_migration.py, validate_architecture.py. Для обычной сборки
все ресурсы уже готовы; повторная генерация не нужна.

Hotfix 1.2.1: исправлен IllegalClassLoadError при старте: DecorativeClimbMixin перенесён в отдельный пакет .mixin, FunctionalFurniture.climbablePos доступен публично. build и geometryCheck прошли; tools/check_mixin_package.py проверяет готовый JAR, изоляцию entrypoints и refmap. Minecraft не запускался.
