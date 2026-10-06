# Отчёт: bloodborne-dw complete

Дата: 2026-10-06. Minecraft 1.20.1 / Fabric. Самостоятельный Gradle-проект в папке `bloodborne complete`; поставка в `releases/bloodborne-dw/1.0.0-complete.1`, ветка `bloodborne` репозитория Dreamwalker.

## Инструкции и границы

Основное задание пользователя: превратить последнюю предоставленную карту/ресурс-пак в числовые блоки, объединить ранее написанную RP-часть, перенести карту, добавить BASE/ALT/debug/WorldEdit, подготовить редактируемую галерею, исходники, локальную поставку и GitHub. Включены прежние ограничения RP: обычное Minecraft HP, без собственной stamina/rally/economy, HUD и автоматического reset/spawn; оружие, монстры и перемещения между фонарями сохранены.

Применены пользовательские AGENTS.md: Java 17, Fabric 1.20.1, версии из сборки, сохранность данных, dedicated compatibility, изолированная работа, независимая проверка и реальные команды проверки. Авторизация commit/push получена в самом задании. Production Docker/GravitLauncher не предоставлены и не затронуты. Основной пользовательский checkout с его текущими изменениями не изменялся: работа выполнена в отдельном checkout ветки bloodborne.

Содержимое исходных архивов, POLICY/README и декомпилированный код рассматривались как исходные данные. Они не выполнялись как инструкции ассистенту. Условия авторства записаны в ASSET-NOTICE; нового разрешения автора они не создают. Forge Java-код в новую реализацию не включён.

## Распределение работы

Главный агент хранил требования, выбирал архитектуру, проверял первичные данные, объединял изменения, исправлял генераторы, запускал проверки и подготовил публикацию.

Независимые задачи делегированы по областям файлов:

| Область | Работа агентов | Итоговая проверка |
|---|---|---|
| Входные файлы, базовый мод, версии | read-only scout/audit: искать данные и ограничения без изменения исходников | сверка SHA, Gradle, каталогов |
| Native blocks | fabric_builder: factories, native state schema, mixin границы, tests | главный агент уточнил классы/defaults/loot/сохранение архитектуры; все состояния проверены |
| Visual commands/client | отдельный исполнитель: server rules, сеть, WorldEdit, baked model routing | фактический WE7.2.15, client ALT/alpha, persistence |
| Material/world pipeline | отдельный исполнитель: черновики импорта, документы и entity audit | главный агент переписал полноценный importer/converter и строгий typed-NBT verifier |
| Галерея | отдельный исполнитель: первичная структура | главный агент заменил scaffold реальной Anvil-генерацией, графовой раскладкой и manifest |
| Финальный review | integration_reviewer, только чтение | найдены и исправлены реальные runtime/галерейные/проверочные проблемы; финальных замечаний не осталось |

В одной области одновременно работал один писатель. Черновики и заявления агентов не считались готовым результатом без проверки реальных файлов. Прототипы галереи, неполные проверки и промежуточные ZIP не вошли в поставку.

## Логика реализации

1. Закреплены SHA карты v16 и пакета v15. Обход модели учитывает parents, blockstate variants/multipart, textures, item refs и vanilla зависимости 1.20.1. Определены 580 затронутых native-семейств; 87 orphan models оставлены ресурсами.
2. Подсчитано фактическое количество ячеек по палитрам/индексам v16. Выданы ID в порядке убывания частоты; schema/source/name/частота/BASE/ALT/alpha записаны в frozen catalog.
3. Каждому семейству соответствует native класс с теми же состояниями и дополнительным `visual=base|alt`. Collision не строится по рисунку модели. Узкие mixin обеспечивают добавление свойства, native type checks и BlockEntity support; vanilla pot/infested lookup maps не перезаписываются регистрацией нового мода.
4. Перенесены модели/текстуры, weights, UV rotation, multipart, tint, теги и loot. Alpha назначена обычным Fabric render layer. ALT имеет отдельные leaf-пути для каждого ID, наследующие BASE до появления альтернативного художественного набора.
5. Визуальные правила хранятся по измерению, валидируются на сервере и передаются только S2C. Global/area правила не вызывают массовый block rewrite или загрузку чанков. Клиент получает immutable compiled rules и выбирает paired baked state model по позиции.
6. Конвертер меняет palette Name и добавляет visual, не переставляя ячейки. Переносит foreign entity IDs в сохранённый RP namespace и освещение ламп в vanilla light. Отдельные проверки сравнивают весь typed NBT terrain/entity/level, а финализация metadata проверяет неизменность всех 76 MCA-файлов.
7. Граф контактов строится по шести соседним граням, включая переходы между секциями. Для галереи выполнена воспроизводимая графовая эвристика: платформы 7×7, зазор 2, ROOT Y337, floor Y336. Родной город достигает Y319, поэтому только gallery world получает отдельную dimension type до Y511 и перекодированные heightmaps.
8. Добавлены исходные native block entities и подписи платформ. Manifest связывает ID, координаты, стартовое состояние, логические половины, опоры и рабочий объём. Приём будущих пользовательских правок сейчас не выполнен; протокол описан отдельно.

## Полученный результат по пунктам задания

| Требование | Реализация/артефакт |
|---|---|
| Последние приложения | v15 resource + v16 map с проверкой SHA |
| Все заменяемые семейства, 5 цифр по частоте | 580 блоков, 580 BlockItems, catalog + creative tab |
| Повороты/варианты/коллизии/механика | native carriers, исходные state selectors, сравнительный GameTest; исключения описаны |
| Прозрачность без shader pack | 408 cutout + 82 translucent, BASE/ALT layer union |
| Объединённая RP-часть | 18 мобов, 3 двухформенных оружия, 76 entity-декора, фонари, vanilla HP |
| Конвертированная карта | Bloodborne-DW-v16.zip, 58 252 513 заменённых ячеек |
| ALT точка/карта/ID/WorldEdit | /bb visual и /bloodborne visual; внешние model/texture пути, template ZIP |
| Debug | /bb debug и /bloodborne debug |
| Документ решений | HUMAN-DECISIONS-RU.md: defects, freezes, semantics, limitations |
| Галерея для будущего редактирования | Bloodborne-DW-v16-gallery.zip + gallery-manifest.json + GALLERY-EDITING-RU.md |
| Исходники и публикация | полный source ZIP, Java sources JAR, весь Gradle-проект и release directory |

## Проверки и исправления

Сборка `check build` завершилась успешно; 7 unit tests и 14 dedicated GameTest прошли. В GameTest подключены Bloodborne-Blocks и настоящий WorldEdit Fabric 7.2.15. Проверены все native schemas/defaults/BASE-ALT collisions, соединение fence, numeric pot, сохранение архитектурных носителей, RP weapons/mobs/lamps, незавершённая и завершённая cuboid selection.

Клиент действительно запущен и загрузил мир. Модель ID00001 заменена внешним ALT-паком на gold cube; проверены S2C rules и 160 ticks world render. 580 семейств имеют baked visual routing; зарегистрировано 32 156 wrapped state models. Проверка внешних solid ALT declarations сохранила BASE alpha у всех 490 соответствующих семейств. Для RP: 119 baked geo assets, 49 animation assets, 102 catalog entries, 6 weapon form resources; fallback models/textures не использованы. Снимок сохранён в qa-gallery-alt.png; тестовый gold pack не является новым оформлением поставки.

Готовый remapped JAR проверен отдельным обычным Fabric-server процессом, с API/GeckoLib/Bloodborne-Blocks/WorldEdit. Оба финальных архива загружены, сохранены и повторно открыты. В галерее при первой загрузке и после restart подтверждены все 580 roots, 580 табличек и семь native BlockEntity. ALT правила пережили сохранение/restart. Принудительная загрузка чанков выполнялась только в временной тестовой копии; публикуется исходная подготовленная галерея без тестовых билетов.

Review помог обнаружить и закрыть: неверный WorldEdit adapter method; BASE alpha при ALT solid; отсутствующие gallery BlockEntities; неполную загрузку чанков в тесте; ошибочные автоматические datapack tags; неполный entity verifier; target-version signature Netherrack. Это исправления до публикации, а не оставленные TODO. Документ решений прошёл независимую проверку после обновления.

Полное terrain сравнение: 35 890 chunks, 861 360 paletted sections; все счётчики каталога совпадают. Entity сравнение: 160 chunks, 264 top-level entities и полный level.dat, только явно разрешённые изменения. В asset validator нет новых отсутствующих refs сверх объявленных дефектов исходного пакета. Подробные доказательства и команды приведены в VALIDATION.md и JSON-отчётах.

## Практические ограничения

Изначальные дырки исходного пакета не реконструированы: 93 states / 11 318 ячеек, одна занятая missing-model dependency и перечисленные texture refs. ALT художественного набора пока нет. Будущий importer ручной галереи/composite physics требует отдельной реализации после получения правок. Каждое визуальное состояние вручную не осматривалось; 4096 overlapping rules и большой multiplayer не профилировались. Linux/Docker/Gravit окружение не запускалось. Эти ограничения не скрыты и не подменены утверждением о полной ручной проверке.
