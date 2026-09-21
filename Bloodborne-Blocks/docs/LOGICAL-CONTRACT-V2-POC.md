# Logical contract v2 — этапы 1–3, только пять семейств

База: `main` / `821ee3daea1601ca7798b051d3e5f1720e98e693`.
Исходный мир: `reference-inputs/source-world.zip`, SHA-256
`4353737d536677469d3b895e3515496ab64fab7b224e43428eb96e8c09724a51`.
Pack: `reference-inputs/source-resource-pack.zip`, SHA-256
`0f2c3d64a1d60734ae0786d128b522ea6bbd175f26f5d164bed5522c46898308`.
Оба архива — read-only. Исторический город/ledger не являются входом этих проверок.

## Где находится контракт

`src/main/resources/bloodborne_blocks/logical/contracts-v2.json` — исполняемый
контракт: 5 семейств, 36 существующих состояний. `logical-contract-v2.schema.json`
описывает формат; строгие Python/Java loaders дополнительно проверяют ссылки на
семейства/состояния/mesh, бюджет примитивов, покрытие collision явно заданными
interaction cells и точный allow-list пяти семейств.

Контракт — отдельный слой над прежними `definitions.json`, `geometry.json`,
`migration.json`, `meshes.json.gz`. Эти четыре файла не перегенерированы.
Остальные 251 семейство остаются на прежнем контракте. Новых registry ID нет.
`tools/build_contract_poc.py` воспроизводит **только** два новых resource JSON и
`docs/contract-poc-v2.json`. Он не запускает общий генератор палитры.

В каждом состоянии отдельно заданы:

- `render_mesh`: ID полного существующего mesh, его фактические bounds и offset;
- `selection_footprint`: один авторский AABB всего объекта;
- `collision_footprint`: авторские примитивы согласно policy/state;
- `interaction_footprint.cells`: явные физические/интерактивные ячейки, включая master;
- `migration_source_pattern`: точные исходные vanilla states с master-relative offsets.

Рендер bounds и model elements не используются для создания физики или helper.
Runtime клиппирует **авторские collision primitives**, а не model element AABB,
по явно разрешённым interaction cells. Бюджет считается до клиппирования: один
высокий ствол остаётся одним примитивом, хотя физика распределена по нескольким ячейкам.
Selection хранится независимо одним общим outline; helper возвращает outline master.

## Координаты и установка

Общий исполняемый численный контракт — `transform-v2.json`: четыре целочисленные
матрицы и 12 vectors, в том числе неосевой anchor `[2,-1,-3]`. Его исполняют
Python `logical_contract_v2.py` и Java `LogicalTransform`; это два адаптера одного
контракта, а не вызов Python из server tick.

```text
R90(x,y,z) = (-z,y,x)                 # offsets ячеек
master = placement_cell - R(anchor_cell)
point' = pivot + R(point - pivot)    # pivot = (0.5,0,0.5)
source_world_cell = master + source_pattern.offset
```

`placement_cell` — обычная Minecraft-ячейка установки после выбора стороны клика,
не координата поверхности. Сторона может выбрать соседнюю ячейку и ориентацию,
но не меняет logical anchor на min/max visual cell.
`FLOOR` задаёт ориентацию по игроку (против направления взгляда), без привязки origin
к визуальному minimum; `WALL_ADJACENT` на горизонтальной грани выбирает facing грани.
На вертикальной грани используется направление игрока. Mirror policy `ROTATE_ONLY`
переназначает направления/соединения, но не отражает вершины асимметричного mesh.

У дерева и ворот явно указан anchor cell `[0,-1,0]`: основание визуально ниже
исторического carrier/master, поэтому manual master находится на одну ячейку выше
ячейки установки. Это заданный anchor, не вычисление minimum модели. У остальных
трёх семейств anchor `[0,0,0]`. Все render offsets равны нулю, старый mesh не сдвигается.
Готовые state mesh, boxes и cells уже ориентированы; runtime не вращает их повторно.

## Пять представителей

Все точные координаты, mesh bounds, source patterns, helper cells и primitives
для **каждого состояния** находятся в `contracts-v2.json`. Сравнительные числа
и проверенные pack applications — в `contract-poc-v2.json`.

| Семейство | Было | Стало |
|---|---|---|
| `o_dead_tree_planter` | 46 visual-derived cells, 43 cell collision boxes | 16 interaction cells (15 helper), 2 примитива: основание и ствол; ветви без helper |
| `o_cases_0` | 12 cells, 53 cell collision boxes | только master, 1 простой collision box, 1 selection box; 71 полигон сохранён |
| `o_wall_deco_1` | 9 cells, 9 collision boxes | только master, NONE collision; mesh пересекает соседнюю боковую ячейку стены, которая остаётся отдельным блоком |
| `o_iron_gate` | физика из visual элементов | 2 авторские створки; закрыто 18 interaction cells, открыто 49; master фиксирован, переход проверяет только новую физику |
| `o_iron_railing` | детальная геометрия элементов определяла shape | policy FENCE: центральная стойка + активные рукава (1–5 примитивов), 2 interaction cells; 16 connected states сохранены |

Числа старых collision boxes считаются после старого per-cell clipping и поэтому
не являются числом исходных model elements. Новые числа — бюджет глобальных
примитивов. Collision policies поддерживают NONE(0), SIMPLE_BOX(1), TWO_BOX(2),
TRUNK(2), POST(1), FENCE/WALL(5), DOOR/GATE(2), STAIRS(3). Формы задаются явно по
состояниям; названия не означают автоматическую реконструкцию неизвестных объектов.

## Прямой matcher и честные ограничения ориентаций

`convert_logical_world.py --source-mode original-v2-poc` компилирует только
`migration_source_pattern` этих пяти семейств в существующий транзакционный engine.
Промежуточные `bloodborne_blocks:m_*` не требуются. Совпадение точное по ID,
properties и координатам; отсутствующие/другие свойства не угадываются.
Только consumed source cells и разрешённые interaction cells участвуют в записи.
Посторонние blocks, block entities и scheduled ticks на изменяемых ячейках блокируют
конвертацию. Visual-only ячейки не участвуют в destination conflict и не удаляются.
Checker читает `sourceMode` отчёта и проверяет ledger независимо от конвертера.
Хэши обоих новых контрактов включены в отчёт; смена контракта не проходит проверку
как будто это прежняя миграция.

- Дерево: `white_wool@(0,0,0) + orange_wool@(0,3,0) + magenta_wool@(0,6,0)`.
  У vanilla wool нет facing. Исходный pack использует yaw0 через стандартные
  blockstates; direct matcher создаёт только north. Manual имеет четыре facing.
- Чемоданы: `oxidized_cut_copper_stairs[half=bottom,shape=straight,waterlogged=false]`.
  Исходные facing south/west/north/east дают logical north/east/south/west.
- Настенный декор: `dead_tube_coral_wall_fan[facing=…,waterlogged=false]`, один carrier,
  совпадающий facing. Поддерживается отдельная соседняя стена, не включаемая в source pattern.
- Ворота: `stripped_jungle_log@(0,0,0) + stripped_acacia_log@(0,3,0)` с одинаковым axis.
  axis=z → north, axis=x → east. В исходнике нет отдельного состояния open или
  противоположного facing; эти состояния существуют для ручной установки/взаимодействия.
- Решётка: `iron_bars` с четырьмя connection booleans и waterlogged=false.
  Пока direct matcher принимает только варианты без south/west рукавов: у них в
  pack weighted alternatives. Все 16 состояний остаются работоспособными в runtime.
  Нельзя выдавать выбор первого weighted model за восстановление исходного вида.

Итого 15 однозначных direct patterns. Книги не выбраны для POC из-за двух случайных
моделей одного carrier; чемоданы — предусмотренная заданием детерминированная замена.
Никакого поиска повторяющихся неизвестных spatial patterns не добавлено.

## Проверка и воспроизведение

Java 17; wrapper Gradle 8.8; версии Minecraft/Fabric/Loom не менялись.

```powershell
# При необходимости задайте JAVA_HOME на JDK17 и путь к Python с numpy.
.\gradlew.bat -PbloodbornePython=C:/path/to/python.exe check build logicalGameTest
```

Для фактического локального прогона использован также
`-I build/verification/direct-resources.init.gradle`: исходный resources-каталог
включён напрямую в classpath/JAR, `processResources` отключён **только этим временным
init-script**. Это устраняет копирование 191624 файлов; компиляция, проверки,
упаковка всех ресурсов и remap JAR не отключаются. Стандартная команда выше —
штатный путь без локального ускорения; её длительный этап копирования был прерван,
поэтому завершённый результат ниже относится к команде с init-script.
Версии зависимостей не обновлялись.

`check` включает прежние Java/registry/geometry проверки, новый logicalContractV2Check,
`test_region_padding.py`, `test_contract_v2.py`, `test_logical_world.py`.
Новые тесты создают временные однокчанковые Anvil-фикстуры, не копию города.
Их краткий отчёт: `build/test-results/contract-poc-v2.json`; server GameTest XML:
`build/test-results/gametest/TEST-logical-gametest.xml`.
Обычные проверки не перезаписывают manifests в src/docs.

Полный read-only Anvil/NBT аудит правильного оригинала:

```powershell
python -B -X utf8 tools/audit_source_regions.py
```

`docs/source-region-audit-v2.json`: 631 .mca, 113898 декодированных chunks,
в том числе 384 terrain regions / 113588 terrain chunks, SHA до/после совпадает.
Missing final padding разрешён только после проверки реального EOF; повреждения
header, allocation overlap, payload truncation отклоняются. No-op bytes неизменны,
только изменённый **выходной** region выравнивается.

### Фактический итог проверки — 2026-09-21

На JDK 17 выполнено:

```powershell
.\gradlew.bat -I build/verification/direct-resources.init.gradle '-PbloodbornePython=C:/Users/vakir/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe' check build logicalGameTest
```

- `BUILD SUCCESSFUL`, exit code 0; финальный повторный прогон занял 2m 50s.
- Все 9 обязательных dedicated-server GameTest прошли, сервер штатно завершился.
- Все Java contract/geometry/registry/object/modular проверки прошли.
- Python: 8 тестов region reader, 4 группы contract-v2 (включая 15 direct patterns),
  прежний world-conversion regression suite — успешны. Повторная миграция fixture
  не меняет байты мира; независимая проверка ledger успешна.
- `tools/check_logical_resources.py`: 256 logical objects, 1324 states/meshes,
  87 textures, 1631 прежнее migration rule; ошибок ссылок не обнаружено.
- Исходные ZIP и четыре прежних logical manifest/mesh файла не изменены.
- JAR: `build/libs/bloodborne-blocks-2.1.0-alpha.1.jar`, 94803125 bytes,
  SHA-256 `b968263fa0656732d27caea75e0e5fbdbbd31707b3e3603a636518e587bd8e20`.
  Новые contract/transform и прежние mesh/definitions/geometry/migration внутри JAR
  побайтно совпадают с исходными resources; новые runtime classes присутствуют.
- Независимый read-only review после исправлений не выявил замечаний P1/P2.
  Неосевой anchor проверен общими Java/Python vectors, но у выбранных пяти
  семейств реальные anchors лежат на оси Y; это ограничение end-to-end покрытия.

Первый серверный прогон выявил ошибку самой UP/DOWN fixture (целевая ячейка была
занята грунтом). Fixture исправлена на подвешенную опору, итоговый повторный прогон
выше успешен. Интерактивная визуальная проверка в клиенте не проводилась: сохранение
визуала здесь проверено неизменностью полных meshes, а поведение — серверными тестами.

## Перед распространением на остальную палитру

1. Явно мигрировать старые schema1 logical saves со старыми helper-ячеями. Текущий
   POC не является разрешением открыть существующий converted city новой сборкой:
   старые `o_*` и helper NBT не содержат версии контракта; schema2 object placement
   проверен на новой fixture, а не как массовый in-place upgrade старых объектов.
2. Восстановить positional RNG weighted pack alternatives до включения оставшихся
   railing/books patterns. Не приписывать vanilla носителям несуществующие facing/open.
3. Подтвердить границы остальных объектов на правильном исходном мире. Этот этап
   не измеряет coverage города и не переносит исторические числа покрытия.
4. Section rendering крупных meshes не изменён: остаются старые ограничения
   culling/lighting при пересечении границ render section и загрузки соседних chunks.
5. Selection box общий, но vanilla raycast обращается к master/interaction cells:
   видимый кончик ветви без helper не становится отдельной интерактивной ячейкой.
6. Foreign visual overlap разрешён намеренно: если игрок поставит непрозрачный
   блок прямо через ветку/рельеф, визуальное пересечение не устраняется автоматикой.

Полная конвертация города, массовая регенерация, spatial discovery, section renderer,
commit и push этим этапом **не выполняются**.
