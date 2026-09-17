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

Сборка: JAVA_HOME=C:/Program Files/Java/jdk-17;
GRADLE_USER_HOME=C:/temp/bloodborne-gradle; ./gradlew.bat --offline build.
Sandbox требует эскалацию для записи в существующий внешний Gradle cache.
Python NumPy/Pillow: C:/Users/Admin/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe.
Генераторы читают Minecraft JAR только как ZIP. prepare_architecture.py НЕ
перезапускать. optimize_architecture.py идемпотентен; затем generate_collision.py,
find_aliases.py, check_migration.py, validate_architecture.py. Для обычной сборки
все ресурсы уже готовы; повторная генерация не нужна.
