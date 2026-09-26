Продолжи ТОЛЬКО проект Bloodborne Blocks:

https://github.com/Stasik1960/Dreamwalker
Рабочая директория:
Bloodborne-Blocks/

Это repair-задача после НЕУДАЧНОЙ beta.3 grid-physics.

НЕ ПРОДОЛЖАЙ beta.3.
НЕ пытайся "дочинить" уже раздробленный beta.3 world.
Нужно вернуться к последнему хорошему code/world-conversion baseline ДО beta.3,
сохранить накопленные ручные logical/composite решения и реализовать grid physics
правильно, без фрагментации объектов.

======================================================================
0. GIT: СОХРАНИ BETA.3 КАК ОТРИЦАТЕЛЬНЫЙ FIXTURE
======================================================================

Текущий broken beta.3 remote HEAD:

b086e88929a971a2abd184629b3e7a59225304e5

Последний baseline ДО beta.3:

419b85eeab56180f0e26272ffc2a2136f6a05a18

Сначала:

- git fetch;
- git lfs pull;
- git status;
- проверить remote/main;
- убедиться, что b086e889 действительно содержит beta.3 grid-physics.

Создай archive branch/tag на b086e889, например:

archive/beta3-grid-fragmentation-broken

Ничего не теряй: beta.3 нужна как regression evidence.

Затем создай рабочую repair-ветку ИМЕННО от:

419b85eeab56180f0e26272ffc2a2136f6a05a18

например:

repair/composite-preserving-grid

НЕ merge beta.3 в эту ветку.
НЕ cherry-pick целиком её runtime/converter commits.

Особенно НЕ переносить целиком:

783d9251a4367755df90cff4df58f50e2213a651
"Confine every city compatibility state to its own physical grid cell"

и:

0d853e6066fb3d53c647bae73f25110a3cb7bd73
"Reconcile verified legacy helpers before authoritative full city conversion"

Из beta.3 допустимо только вручную переиспользовать отдельные ИДЕИ/маленькие
фрагменты после проверки:
- explicit physical_footprint;
- cell-local collision validation;
- adjacency/editability tests.

Не переносить её one-cell flattening/fallback policy.

======================================================================
1. СНАЧАЛА ДОКАЖИ ПРИЧИНУ BETA.3, НЕ ПИШИ FIX СРАЗУ
======================================================================

Сделай read-only forensic diff:

baseline 419b85e
vs
broken beta.3 b086e889

Создай:

docs/composite-grid-repair/BETA3-FAILURE-ANALYSIS.md
docs/composite-grid-repair/fragmentation-diff.json

Нужно доказать путь преобразования нескольких конкретных объектов:
source/original -> MODDED input -> beta.2/baseline rules -> beta.3.

Уже подтверждённые пользовательским playtest regression fixtures:

A. C474 / шпиль.

Нормальный цельный предмет в моде:

bloodborne_blocks:o_c474
Source Review: C474
Semantic: Narrow timber post
пример master:
-563 85 -165
facing=south

Но beta.3 карта в том же архитектурном объекте содержит независимую секцию:

bloodborne_blocks:city_column_bc0c7c3a_0368
Position примерно:
-566 85 -165
Target: NON-LOGICAL
variant=14

Пользователю пришлось ломать выпирающие отдельные части, чтобы увидеть
block-основание.

Это критический FAIL:
известный цельный logical/composite object не должен превращаться в city_*
fragments.

B. Деревья.

Деревья C001 уже ранее были вручную review/fixed.
Их НЕЛЬЗЯ было пересобирать или дробить.

В broken beta.3 внутри визуально цельного дерева обнаруживались independent
NON-LOGICAL blocks, например:

bloodborne_blocks:white_wool
Position: -284 43 -71

и также наблюдались vanilla/native compatibility fragments вроде orange_wool.

Проверь source/baseline вокруг этих координат и докажи, к какому reviewed
object они относились.

Если это component C001:
после правильной конвертации он ОБЯЗАН принадлежать одному o_c001 object,
а не оставаться independent block.

C. Fragmented city architecture / fence-like structures.

Примеры beta.3:

bloodborne_blocks:city_column_db2a0dd4_0023
Position: -279 43 -49
NON-LOGICAL

bloodborne_blocks:city_column_db2a0dd4_0008
Position: -280 42 -50
NON-LOGICAL

Визуально это части одной архитектурной/ограждающей конструкции.

Также:

bloodborne_blocks:city_ladder_f0e98d82_0003
около:
-327 50 -115
-326 50 -115

и:

bloodborne_blocks:city_ornament_9a34ea84_0205
Position:
-332 77 -137

Это evidence того, что technical compatibility sections были выданы наружу
как самостоятельные конечные editor blocks.

D. Wall/lantern placement oracle.

Правильно уже стоящий экземпляр на карте:

Registry:
bloodborne_blocks:flower_pot
Position:
-540 41 -33

Target: NON-LOGICAL

F3 state:
assembled=false
facing=north

Пользователь вручную поставил визуально тот же lantern component:

Registry:
bloodborne_blocks:flower_pot
Position:
-538 35 -36

Target: NON-LOGICAL

F3 state:
assembled=true
facing=west

Новый экземпляр визуально ставится неправильно.

НЕ делать вывод "надо просто поставить assembled=false".
Сначала выяснить по baseline/source/resource-pack/current functional families,
какая именно composite identity и placement policy должна использоваться.

Правильно стоящий экземпляр карты — placement/orientation oracle.

======================================================================
2. ГЛАВНАЯ ОШИБКА BETA.3
======================================================================

Beta.3 применила неправильную архитектурную аксиому:

"каждый compatibility block = самостоятельная physical Minecraft cell"

и при full-grid fallback стала превращать неповышенные composite objects
в независимые city_* cells.

Это ЗАПРЕЩЕНО.

Особенно запрещено повторять:

p['cells'] = {'0,0,0': ...}
p['anchor'] = [0,0,0]
p['physical_footprint'] = [[0,0,0]]

ГЛОБАЛЬНО для всех city compatibility profiles.

Нельзя считать one-cell representation автоматически корректной.

Нельзя удалять historical helpers ПЕРЕД тем, как доказана composite/root identity.

Нельзя использовать:

ambiguous/composite -> N independent city_* blocks

как fallback.

======================================================================
3. НОВАЯ ПРАВИЛЬНАЯ АРХИТЕКТУРА
======================================================================

Нужно сохранить хорошую идею grid physics, но разделить ЧЕТЫРЕ вещи:

A. SOURCE / ASSEMBLY FOOTPRINT

Какие исходные vanilla/modular cells составляют ОДИН объект.
Используется конвертером атомарно.

B. LOGICAL / COMPOSITE IDENTITY

Один объект, один root/master, один item/interaction object.

C. PHYSICAL FOOTPRINT

Какие Minecraft cells объект реально физически занимает и резервирует.

D. RENDER MESH

Как далеко визуально выступает модель.

Они НЕ равны друг другу автоматически.

Главный принцип:

SOURCE ASSEMBLY может занимать много технических carrier cells.

RENDER может выходить далеко за physical footprint.

Но весь SOURCE ASSEMBLY должен атомарно конвертироваться в ОДИН composite/logical
object, если его identity уже известна.

После конвертации helpers разрешены только для PHYSICAL footprint,
а не для всех визуальных/source cells.

======================================================================
4. ОСНОВНОЙ INVARIANT: NO COMPOSITE FRAGMENTATION
======================================================================

Добавь обязательный глобальный gate:

NO_COMPOSITE_FRAGMENTATION

Если ДО изменения один объект имеет доказанную composite identity через хотя бы
одно из:

- existing production logical family;
- authority=user / review family;
- migration_source_pattern;
- source assembly catalog/signature;
- historical root + ArchitecturePart ownership;
- explicit old logical migration;
- existing reviewed mapping;

то ПОСЛЕ конвертации он не имеет права стать:

city_x + city_y + city_z
или
vanilla/native fragments
или
несколькими независимыми roots.

Он должен стать:

ONE ROOT
+
0..N physical helpers

Все source components должны быть consumed атомарно.

Это относится не только к примерам пользователя, а ко всей карте.

======================================================================
5. PROTECTED ДОЛЖЕН ЗАЩИЩАТЬ НЕ ТОЛЬКО ФАЙЛЫ, НО И МИР
======================================================================

В beta.3 ошибка была в том, что 49 production families были "PROTECTED"
по ресурсам, но converter всё равно не обязан был получить их на карте.

Это недостаточно.

Введи:

PROTECTED_WORLD_OBJECT_PRESERVED

Для ВСЕХ ранее reviewed/manual families:

- C001 trees;
- C008 statues;
- C474;
- C282;
- C1491;
- C1979;
- C654;
- C003 successors;
- doors;
- ladders;
- window;
- benches;
- lantern-related reviewed objects;
- Nightmare/Агония/client-QA families;
- остальные protected families из фактической истории;

нужно построить WORLD ORACLE.

Используй:

reference-inputs/source-world.zip

только как reference/oracle:

- coordinates;
- source blockstates;
- source assembly membership;
- orientation;
- canonical root relation.

Resource pack используется только для доказательства visual/source transform.

НЕ копируй vanilla world поверх MODDED world.

Создай:

docs/composite-grid-repair/protected-world-oracle.json

Для каждого подтверждённого occurrence:
- family;
- source cells;
- canonical root;
- orientation;
- expected current logical ID.

При конвертации latest MODDED backup:

если oracle-occurrence всё ещё состоит из Bloodborne-owned/legacy/module/current
architecture cells и не был заменён foreign content,
он ОБЯЗАН быть promoted в ожидаемый o_* logical object.

Нельзя отправлять его в city_* fallback.

Если внутри occurrence реально есть foreign/user edit:
НЕ стирай foreign block.
Запиши PROTECTED_ORACLE_FOREIGN_CONFLICT.

Но если все cells принадлежат Bloodborne layer:
не допускается никакой fragmentation.

======================================================================
6. НЕ ДЕЛАЙ НОВЫЙ SEMANTIC DISCOVERY
======================================================================

Не угадывай по визуалу.

Не решай самостоятельно:

"эти 5 секций похожи на одну колонну".

Используй только УЖЕ СУЩЕСТВУЮЩИЕ evidence:

- reviewed logical families;
- migration_source_patterns;
- source assembly catalog/signatures;
- old root/helper ownership;
- exact source-world patterns;
- existing historical geometry.

Если group identity нигде не доказана:
не придумывай её.

Но и не ломай доказанную multi-cell identity на independent cells.

======================================================================
7. ПРАВИЛЬНАЯ GRID PHYSICS
======================================================================

После определения composite identity:

PHYSICAL FOOTPRINT задаётся отдельно.

Collision обязана быть привязана к Minecraft cells.

Для каждой physical cell:

0 <= x <= 1
0 <= y <= 1
0 <= z <= 1

Добавь/сохрани:

COLLISION_OUTSIDE_LOCAL_CELL = FAIL
COLLISION_OUTSIDE_PHYSICAL_FOOTPRINT = FAIL
HELPER_OUTSIDE_PHYSICAL_FOOTPRINT = FAIL

Render mesh может выступать в соседние cells без reservation.

НО:

physical footprint != source assembly footprint

Source cell может быть техническим visual carrier и быть consumed при migration,
но после conversion не обязан становиться helper.

Для reviewed/manual families:
НЕ пересчитывай их approved semantics/placement/physics без конкретного доказанного
regression.

Для остальных existing composite roots:
используй historical collision/ownership как evidence.

Если невозможно безопасно уменьшить physical mask:
сохрани beta.2 physical behavior и пометь NEEDS_PHYSICS_REVIEW.

НЕ flatten object ради "редактируемости".

Лучше временно оставить лишний helper у одного ambiguous объекта,
чем уничтожить object identity.

======================================================================
8. РАЗРЕШЁННАЯ РЕДАКТИРУЕМОСТЬ
======================================================================

После preservation composite identity добавь уже хороший beta.3 invariant:

если соседняя cell НЕ входит в physical footprint,
в неё должен ставиться обычный minecraft:stone.

GRID_EDITABILITY_FAIL, если это невозможно.

Но этот тест выполняется ТОЛЬКО после:

NO_COMPOSITE_FRAGMENTATION
PROTECTED_WORLD_OBJECT_PRESERVED

Иначе раздробленный объект не может считаться успешным только потому,
что stone ставится рядом.

======================================================================
9. COMPATIBILITY FALLBACK: ПЕРЕДЕЛАТЬ
======================================================================

Старый beta.3 fallback:

known/ambiguous architecture
-> independent one-cell city compatibility blocks

ЗАПРЕЩЁН.

Новая иерархия:

1. Reviewed/protected exact object
   -> current logical o_*.

2. Existing exact logical source pattern
   -> current logical o_*.

3. Existing proven composite compatibility/root ownership / source assembly
   but no production semantic family
   -> ONE COMPOSITE COMPATIBILITY ROOT
      + physical helpers.

   Reuse existing evidence.
   Не придумывай semantic name.
   Это может быть internal compatibility object,
   но он должен оставаться единым объектом.

4. Только cell, которая ИСТОРИЧЕСКИ действительно была независимой
   и не принадлежит никакому known assembly/root,
   может стать independent one-cell city compatibility state.

Никакой известный multi-cell source assembly не может попадать в case 4.

======================================================================
10. C474 — ОБЯЗАТЕЛЬНЫЙ REGRESSION FIXTURE
======================================================================

Добавь конкретный тест.

В production существует цельный:

bloodborne_blocks:o_c474

review C474.

Источник/source-world должен определить его occurrence и components.

После conversion в районе пользовательского примера:

master/oracle около:
-563 85 -165

не должно оставаться связанных частей вроде:

bloodborne_blocks:city_column_bc0c7c3a_0368

как независимых fragments этого объекта.

Expected:

ONE o_c474 root
+
только его approved physical helpers.

Все source components consumed.

C474 fragmentation = HARD FAIL.

======================================================================
11. C001 TREE — ОБЯЗАТЕЛЬНЫЙ REGRESSION FIXTURE
======================================================================

C001 уже вручную исправлен.

Ни одна его source component не должна после conversion остаться:

white_wool
orange_wool
city_*
или другим independent compatibility fragment,

если occurrence точно соответствует reviewed C001.

Проверь пользовательские координаты, в том числе район:

-284 43 -71

и source/world evidence.

Добавь тест:

C001_REVIEWED_OBJECT_FRAGMENTED = FAIL

Не менять authored C001 render/variants/physics без необходимости.

======================================================================
12. FENCE / RAILING / WALL CONNECTION
======================================================================

Не распознавай новые fences по картинке.

Для уже известных connected logical families используй существующий
connection_family/behavior.

Требования:

- один composite root/item по существующей family;
- корректный facing/connection state;
- neighbor add/remove обновляет connections;
- helpers только в physical footprint;
- no fragmentation into city_column/city_plant/etc.

Если unreviewed city assembly похожа на fence, но identity не доказана:
не превращай её автоматически в новую fence family.

Оставь composite compatibility object и report.

======================================================================
13. LANTERN PLACEMENT ORACLE
======================================================================

Не исправляй по догадке.

Пользователь дал два экземпляра одного legacy/native registry:

ПРАВИЛЬНО СТОЯЩИЙ НА КАРТЕ:

bloodborne_blocks:flower_pot
Position:
-540 41 -33

F3:
assembled=false
facing=north

НЕПРАВИЛЬНО ПОСТАВЛЕННЫЙ ВРУЧНУЮ:

bloodborne_blocks:flower_pot
Position:
-538 35 -36

F3:
assembled=true
facing=west

Оба сейчас NON-LOGICAL.

Выясни через:
- beta.2 baseline;
- source-world;
- source pack;
- historical definition;
- existing o_lantern / o_wall_lantern / other reviewed families;

какая identity действительно соответствует этому visual object.

Не делай вывод по имени flower_pot.

Если exact mapping доказывает существующий logical lantern family:
конвертируй/используй её.

Если это historical composite compatibility object:
сохрани его composite identity.

Главное acceptance requirement:

при ручном placement на эквивалентную wall face новый экземпляр должен
воспроизводить:

- правильную ориентацию;
- anchor;
- render offset;
- assembly state;
- physical footprint;

как уже правильно стоящий oracle instance.

Добавь:

PLACEMENT_MATCHES_EXISTING_ORACLE = FAIL

если equivalent placement даёт другой transform/state без доказанной причины.

======================================================================
14. BUILD НОВОЙ КАРТЫ
======================================================================

Input ТОЛЬКО:

Bloodborne-Blocks/reference-inputs/latest-modded-world.zip

SHA256:
c517dfeb52c4d13bdbe90e02a93ac00416354eb89313a9d1377f24823af6d0e9

НЕ используй beta.3 world как input.
НЕ конвертируй beta.3 поверх неё.
НЕ используй Conservative/Aggressive beta.2 output как source, если immutable
latest MODDED backup доступен.

Vanilla source-world:
reference ONLY.

Нужно создать ОДИН новый repair FULL world.

Вся доказанная Bloodborne architecture должна перейти на current mod IDs.

Но:

"полная конвертация" НЕ означает "раздробить всё на one-cell IDs".

Наоборот:
полная конвертация обязана сохранять доказанную composite identity.

======================================================================
15. WORLD-WIDE ACCEPTANCE GATES
======================================================================

После conversion проверь ВСЕ 10009 chunks.

Обязательные gates:

UNKNOWN_BLOODBORNE_ID = 0
OLD_MODULE_ID = 0
ORPHAN_HELPER = 0

NO_COMPOSITE_FRAGMENTATION = PASS

PROTECTED_WORLD_OBJECT_PRESERVED = PASS

C474_FRAGMENTED = 0

C001_FRAGMENTED = 0

HELPER_OUTSIDE_PHYSICAL_FOOTPRINT = 0

COLLISION_OUTSIDE_LOCAL_CELL = 0

COLLISION_OUTSIDE_PHYSICAL_FOOTPRINT = 0

GRID_EDITABILITY_FAIL = 0
для tested/reconciled objects

PLACEMENT_ORACLE_FAIL = 0

Для каждой reviewed/protected family выдай:
- expected occurrences from source oracle;
- current logical occurrences;
- skipped because foreign edit;
- fragmented count.

fragmented count должен быть ZERO.

======================================================================
16. IDEMPOTENCE
======================================================================

Запусти converter второй раз на результате.

Должно быть:

logical changes = 0
composite compatibility changes = 0
helper changes = 0
physics changes = 0
promotion changes = 0

World files second pass должны быть byte-identical, кроме только тех файлов,
для которых Minecraft/world format объективно требует metadata mutation;
если такие есть — объясни.

======================================================================
17. TESTS
======================================================================

Добавь минимум:

1. reviewed logical source assembly -> one root, never city fragments;
2. C474 source assembly -> o_c474;
3. C001 source assembly -> o_c001;
4. source membership may be larger than physical footprint;
5. visual overhang does not reserve nonphysical cell;
6. stone places outside physical footprint;
7. root break removes only owned physical helpers;
8. rotation N/E/S/W preserves source/composite identity;
9. connected known fence add/remove;
10. lantern placement oracle;
11. no source component of a successful composite remains as independent city_*;
12. second pass zero.

Запусти:

- targeted Python tests;
- full check build;
- GameTests;
- production fingerprints;
- world preservation checker;
- full-world registry census.

Если historical tests падают уже на baseline:
документируй отдельно.
Не переписывай tests только чтобы получить зелёный результат.

======================================================================
18. ЧТО НЕЛЬЗЯ ТРОГАТЬ
======================================================================

НЕ делать:

- новый semantic discovery;
- новый batch catalog;
- mass merge/split по визуальной догадке;
- re-author textures/UV;
- менять reviewed render models без необходимости;
- менять C003 semantic decisions;
- менять C046/CASES ambiguity;
- ломать C001;
- делать every-city-block-one-cell глобальным правилом;
- сбрасывать anchors всех compatibility blocks в 0;
- удалять helpers до определения их owner/composite identity;
- использовать beta.3 world как truth.

======================================================================
19. RELEASE
======================================================================

Если repair проходит все gates, выпусти следующую beta,
например:

2.1.0-beta.4-composite-grid-repair

или следующий свободный beta номер.

В release:

- JAR;
- ONE repaired FULL world ZIP;
- README;
- proof.json;
- Composite-Grid-Repair-Reports.zip.

В README честно написать:

- beta.3 была признана broken из-за compatibility fragmentation;
- repair построен от pre-beta.3 baseline 419b85e;
- beta.3 использовалась только как negative regression fixture;
- composite identity теперь отделена от physical footprint;
- collision grid-local;
- client graphical acceptance NOT_RUN, если клиент реально не запускался.

======================================================================
20. ИНТЕГРАЦИЯ В MAIN
======================================================================

НЕ force-push.

Сначала полностью закончи и проверь repair branch.

После PASS:

на актуальном main сделай history-preserving revert именно beta.3 series
(8 commits от 04489712352fbbf206c37b24e87a435599206b88
до b086e88929a971a2abd184629b3e7a59225304e5),

так чтобы runtime/converter beta.3 больше не действовал.

Затем перенеси validated repair commits из repair branch.

НЕ merge beta.3 обратно через общий merge.

Перед push:
сравни resulting tree с repair branch и убедись, что broken one-cell flattening
не вернулся.

Push main только после всех PASS.

Remote HEAD verify.

======================================================================
21. ФИНАЛЬНЫЙ ОТЧЁТ
======================================================================

В финале дай:

- baseline SHA;
- broken beta.3 SHA;
- repair implementation SHA;
- final main SHA;
- JAR SHA256;
- world SHA256;
- количество protected occurrences;
- количество promoted logical objects;
- количество composite compatibility roots;
- количество genuine independent compatibility cells;
- fragmentation count;
- C474/C001 regression result;
- lantern oracle result;
- test summary;
- second-pass result.

Главный критерий результата:

НЕ "каждая модель занимает одну клетку".

Правильный критерий:

ОДИН ДОКАЗАННЫЙ ОБЪЕКТ ОСТАЁТСЯ ОДНИМ ОБЪЕКТОМ,
но его COLLISION/PHYSICAL FOOTPRINT строго привязаны к реальным Minecraft cells.

Render может выступать за эти cells.
Source assembly может быть больше physical footprint.
Это не повод дробить объект.