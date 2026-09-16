# Bloodborne Architecture 1.1.0 — 2026-09-16

Рабочие исходники: эта папка, Fabric 1.20.1 / Yarn build.10 / Java17 / Gradle8.8 / Loom1.6.12.

Последний запрос: исправить все формы/установку, двери и старые ошибки до публикации. Пользователь запрещает запуск Minecraft на своём ПК. GitHub НЕ публиковать без последующего указания. Пользовательский мир не открыт и не менялся.

Готовый JAR: ../releases/Bloodborne-Blocks/bloodborne-blocks-1.1.0-mc1.20.1.jar (4 414 874 байта).
SHA256: 000a67b449c0aa478b184a5462d706dc8066da4f048327ea9e79d91f080aa5ac.

Сделано: 503 ID, 18968 состояний, 2778 профилей / 18 710 локальных клеток; реальные коллизии моделей с альфа-покрытием; связанные части крупных объектов; новое краевое основание assembled=true, старые assembled=false;3 открывающихся модельных дверей; 15 настоящих типов ступеней; 10 пустых дефолтов скрыты из вкладки. Исходные 13 936 состояний сохранены. Ограниченные изменения старой палитры: 99 случайных физических вариантов закреплены, смещения растений отключены. Читай README и docs/COLLISION-1.1.0-RU.md.

Проверки: geometryCheck успешно на всех состояниях и проходе 0.6 × 1.8 во всех 4 направлениях дверей; validate_architecture.py успешно (3 846 JSON, 497 ограниченных item-моделей); Gradle build SUCCESS; версия/classes/resources внутриJAR сверены. Runtime игра/сервер/мир НЕ запускались, FPS и сетевые события не проверялись.

Старая карта требует `/bloodborne repair 16 preview` и `/bloodborne repair 16 apply` (см. README). Радиус 1–32, только загруженные чанки; конфликты пропускаются, постройки не перемещаются. Работа в копии мира. Автоматическая перестройка города не сделана: карта не предоставлена.

Для сборки: JAVA_HOME=C:/Program Files/Java/jdk-17; GRADLE_USER_HOME=C:/temp/bloodborne-gradle; gradlew.bat build. Python с NumPy/Pillow: C:/Users/Admin/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe. Генераторы читают Minecraft client JAR как ZIP, без запуска. prepare_architecture.py — одноразовый мигратор, повторно не запускать.
