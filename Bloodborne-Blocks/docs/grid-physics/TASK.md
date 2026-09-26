Продолжи ТОЛЬКО проект Bloodborne Blocks в репозитории:

https://github.com/Stasik1960/Dreamwalker

Рабочая директория:
Bloodborne-Blocks/

Не работай с другими проектами/папками Dreamwalker.

ЗАДАЧА: Source-Grid Physics Reconciliation + полная повторная конвертация города.

Это НЕ очередной точечный фикс нескольких найденных объектов.
Последний пользовательский playtest выявил системную архитектурную проблему:
render-модель, helper/ownership footprint и collision всё ещё слишком сильно
связаны друг с другом. В результате декоративно выступающая геометрия может
резервировать соседние Minecraft-клетки, стены/колонны нельзя нормально ставить
блок-к-блоку, окно может физически занимать больше клеток, чем должно, и т.п.

Нужно исправить ВЕСЬ ЭТОТ КЛАСС ошибок по общему правилу.

Главный принцип задачи:

MINECRAFT GRID ЯВЛЯЕТСЯ ИСТОЧНИКОМ ИСТИНЫ ДЛЯ ФИЗИКИ.

Render mesh отвечает только за внешний вид.
Размер или выступы render mesh НЕ дают объекту права:
- резервировать соседнюю Minecraft-клетку;
- создавать там ArchitecturePart/helper;
- иметь там collision.

При этом НЕ надо принудительно превращать каждый logical object в 1 клетку.
Если текущий logical object действительно является склеенной конструкцией
из нескольких доказанных физических source-cells, он должен остаться одним
logical object/item и продолжить занимать эти несколько Minecraft-клеток.

Не угадывай semantic boundaries по внешнему виду.

======================================================================
0. ВОССТАНОВИ И ЗАФИКСИРУЙ ТЕКУЩЕЕ СОСТОЯНИЕ
======================================================================

Сначала:

- git fetch;
- git lfs pull;
- git status;
- branch;
- HEAD;
- remote;
- проверить LFS;
- прочитать HANDOFF.md;
- docs/city-compat/*;
- docs/agony-patch/*;
- docs/beta-client-qa/*;
- production manifests;
- Contract V2;
- current logical definitions/geometry;
- current city compatibility definitions/geometry;
- converter;
- текущие tests.

На момент постановки задачи ожидаемый main HEAD:
419b85eeab56180f0e26272ffc2a2136f6a05a18

Если main уже ушёл вперёд:
НЕ reset/revert автоматически.
Сначала проверь, что новые commits являются продолжением проекта и не
заменяют эту задачу. Работай от фактического latest main и запиши base HEAD.

Актуальные известные immutable inputs:

latest MODDED backup:
reference-inputs/latest-modded-world.zip
SHA256:
c517dfeb52c4d13bdbe90e02a93ac00416354eb89313a9d1377f24823af6d0e9

vanilla source-world — ТОЛЬКО reference/oracle:
SHA256:
4353737d536677469d3b895e3515496ab64fab7b224e43428eb96e8c09724a51

source resource pack:
SHA256:
0f2c3d64a1d60734ae0786d128b522ea6bbd175f26f5d164bed5522c46898308

Если в repo появился более новый явно подтверждённый MODDED backup,
используй его и зафиксируй SHA256.
НИКОГДА не подменяй MODDED input vanilla source-world.

Сделай checkpoint commit до рефактора, если текущее состояние ещё не имеет
чистого сохранённого checkpoint.

======================================================================
1. НЕ ДЕЛАЙ НОВЫЙ SEMANTIC DISCOVERY
======================================================================

Критическое ограничение.

НЕ надо смотреть на model/render и самостоятельно решать:

"эти пять блоков, наверное, одна колонна";
"это, наверное, новый забор";
"эти элементы надо объединить";
"этот объект надо разделить".

Границы уже существующего logical object бери из ТЕКУЩЕЙ базы мода:

- current family;
- migration_source_pattern;
- canonical anchor;
- existing source mapping;
- существующие review/manual decisions.

Старая vanilla-карта и resource pack нужны для ПРОВЕРКИ и восстановления
физической grid-маски уже существующего объекта, а не для нового discovery.

Никакого batch discovery.
Никакого нового catalog C/F.
Никакого глобального merge/split families.

======================================================================
2. СНАЧАЛА СОЗДАЙ PROTECTED SET
======================================================================

В проекте уже потрачено много ручной работы на конкретные объекты.
Их нельзя заново переосмысливать автоматикой.

Создай:

docs/grid-physics/protected-families.json

В PROTECTED должны попасть все families, для которых есть подтверждённое
ручное/пользовательское решение, в том числе определяемые через:

- authority=user;
- Nightmare review;
- Агония;
- beta client QA;
- targeted patch allowlists/scripts;
- explicit manually-authored physics.

Обязательно проверь среди прочего уже обработанные:

- C001 trees;
- C008 statues;
- C282 / reviewed doors;
- C003 successors;
- reviewed windows;
- ladders;
- landing;
- lantern;
- bench;
- sacks/books/cases corrections;
- остальные families, реально изменённые предыдущими manual review.

Этот список не ограничивается примерами выше:
построй его из фактических документов/allowlists/history.

Для PROTECTED:

- semantic identity не менять;
- registry IDs не менять;
- source patterns не менять;
- render mesh не менять;
- textures/UV не менять;
- BASE/ALT не менять;
- authored gameplay behavior не менять;
- уже утверждённый physical footprint автоматически не пересчитывать.

Создай frozen fingerprints BEFORE.

Если автоматический проход неожиданно меняет protected family:

PROTECTED_FAMILY_CHANGED = FAIL

Не "исправляй" её дальше.
Останови именно это изменение и сохрани прежний вариант.

======================================================================
3. ОТДЕЛИ PHYSICAL FOOTPRINT ОТ RENDER
======================================================================

Добавь/формализуй отдельный runtime/contract concept:

physical_footprint

Он НЕ равен автоматически:

- render bounds;
- selection bounds;
- interaction bounds;
- всем клеткам, которых визуально касается mesh.

У объекта должны концептуально быть отдельно:

1. render_mesh
2. selection / interaction
3. physical_footprint
4. collision_by_cell
5. migration_source_pattern
6. canonical anchor

Только physical_footprint имеет право:

- резервировать Minecraft cell;
- создавать ArchitecturePart/helper;
- блокировать placement другого блока в этой cell.

Render geometry может визуально выступать за physical footprint.
Это НОРМАЛЬНО и НЕ должно расширять ownership.

======================================================================
4. ГЛАВНЫЙ ИНВАРИАНТ COLLISION
======================================================================

Это опорная основа всей задачи.

Collision должна быть ЖЁСТКО ПРИВЯЗАНА К MINECRAFT CELLS.

Для каждой physical cell collision задаётся только в её local coordinates:

0 <= x <= 1
0 <= y <= 1
0 <= z <= 1

Collision одной physical cell не может залезать в соседнюю cell.

Если объект занимает несколько клеток:
collision разбивается по этим клеткам.

Запретить ситуацию:

render выступает в соседнюю клетку
→ collision автоматически выступает туда же.

Добавь обязательные validators:

COLLISION_OUTSIDE_PHYSICAL_FOOTPRINT = FAIL
COLLISION_OUTSIDE_LOCAL_CELL = FAIL

Существующий COLLISION_OUTSIDE_OWNED_CELLS недостаточен:
раньше сама owned-cell mask могла быть ошибочно слишком большой.

======================================================================
5. HELPERS / OWNERSHIP
======================================================================

ArchitecturePart/helper больше не создаётся для декоративного визуального
выступа.

Инвариант:

helper cells == physical_footprint - root/master cells

или, если runtime требует особый служебный root layout:

helper cells MUST be a strict subset of explicitly declared physical footprint.

Никаких helper cells вне physical footprint.

Добавь:

HELPER_OUTSIDE_PHYSICAL_FOOTPRINT = FAIL

Обычный visual overhang вообще не требует helper.

Это особенно важно для возможности поставить соседний Minecraft block.

======================================================================
6. КАК ОПРЕДЕЛЯТЬ PHYSICAL FOOTPRINT ДЛЯ НЕПРОВЕРЕННЫХ FAMILIES
======================================================================

НЕ угадывать по картинке.

Для каждой CURRENT unprotected logical family:

A. Возьми уже существующие migration_source_pattern.

B. Нормализуй их к canonical orientation/anchor.

C. Проверь реальные occurrences в immutable vanilla source-world.

D. Resource pack используй только как verification того, что source pattern
действительно соответствует текущему visual object.
Не используй визуальное сходство как основание для нового grouping.

E. Для каждой source cell получи её исходную vanilla COLLISION SHAPE,
не outline/render bounds.

Это даёт candidate physical mask.

Source cells с пустой vanilla collision не должны автоматически становиться
physical cells только потому, что несут часть модели.

Ручные/PROTECTED решения всегда имеют приоритет над этой автоматикой.

======================================================================
7. AUTO_SAFE / AMBIGUOUS
======================================================================

Каждую unprotected family классифицируй:

PROTECTED
AUTO_SAFE
AMBIGUOUS

AUTO_SAFE разрешено только если:

- existing source patterns однозначны;
- canonical physical mask одинакова для всех применимых patterns;
- реальные source-world occurrences не противоречат ей;
- mask включает root/anchor;
- mask не требует новых cells, которых нет в source assembly;
- result не меняет semantic family;
- result не требует visual inference;
- render/source fingerprints остаются прежними.

Если разные patterns/occurrences дают разные physical masks,
или provenance неполная:

AMBIGUOUS_PHYSICAL_FOOTPRINT

НЕ выбирай "более вероятный" вариант.
НЕ используй AI-интуицию.
НЕ делай union "на всякий случай".

Для runtime logical family оставь прежнее поведение и внеси её в report.

Но при КОНВЕРТАЦИИ КАРТЫ ambiguous object НЕ должен оставаться старым
legacy/vanilla/module блоком — см. раздел полной конвертации ниже.

======================================================================
8. CITY COMPATIBILITY LAYER
======================================================================

Не пытайся делать semantic discovery по тысячам compatibility blocks.

Для city compatibility действует простое правило:

1 compatibility block at XYZ
=
1 physical Minecraft cell at XYZ

Для него:

- helper count = 0;
- collision cell-local;
- никакого ownership соседних cells;
- visual model может выступать за cell;
- render mapping должен остаться прежним.

Это позволит массово исправить редактируемость старой городской архитектуры
без необходимости понимать, является ли каждый fragment колонной, стеной,
лепниной и т.п.

Не пересобирай 2914 compatibility families/states в новые semantic families.

======================================================================
9. GRID EDITABILITY TEST — ГЛАВНЫЙ ACCEPTANCE TEST
======================================================================

Добавь автоматический тест именно того, на что пожаловался playtest.

Для КАЖДОГО изменённого production logical state и representative city
compatibility state:

1. поставить block/object;
2. получить physical footprint;
3. найти все соседние Minecraft cells вокруг footprint;
4. для каждой cell, НЕ входящей в physical footprint, попробовать поставить
   обычный minecraft:stone.

Если stone нельзя поставить из-за:
- helper;
- ownership;
- collision;
- placement reservation;

то:

GRID_EDITABILITY_FAIL

Этот test обязан реально ловить ситуацию:

"нельзя поставить блок вплотную к стене/колонне, пока не сломаешь лепнину".

Не ограничивайся математической проверкой AABB.

======================================================================
10. ЗАБОРЫ, СТЕНЫ, RAILINGS — ОГРАНИЧЕННЫЙ CONNECTED PASS
======================================================================

После grid physics сделай отдельный маленький подпроход.

НЕ распознавай визуально новые fences/walls.

Работай ТОЛЬКО с families, которые уже однозначно отмечены текущими metadata
как:

- connected;
- fence;
- wall;
- railing;
- pane/balustrade equivalent;
- имеют existing connection_family / соответствующее behavior.

Для них:

- physical footprint должен оставаться grid-local;
- никаких helpers в соседних cells только ради соединения;
- neighbor connection вычисляется как normal Minecraft connection;
- removal/addition соседнего блока обновляет connection state.

Используй уже существующие authored connection model/state variants.

Если необходимого визуального connection variant НЕТ:
НЕ дорисовывай/не сочиняй его.
Оставь family в безопасном текущем rendering mode и внеси в report:

CONNECTED_MODEL_VARIANT_MISSING

Grid physics всё равно должна быть исправлена.

======================================================================
11. ЧТО ЭТА ЗАДАЧА ЗАПРЕЩАЕТ МЕНЯТЬ
======================================================================

НЕ выполнять в этой задаче:

- semantic merge/split families;
- переименование registry IDs;
- новый catalog/discovery;
- re-author render meshes;
- изменение текстур/UV;
- массовое исправление визуальных артефактов;
- переделку деревьев;
- переделку уже reviewed statues/doors/bench/etc.;
- новый ALT art pass;
- redesign C003;
- изменение C046/CASES ambiguity;
- перенос объектов "на глаз";
- создание Entity/BlockEntity renderer для решения физики;
- ticking rendering entities.

В частности проблема:
"8-блочная колонна состоит из 9 разных item IDs"
НЕ является задачей этого этапа.

После этого этапа она может всё ещё состоять из нескольких IDs,
но они не должны неправильно резервировать соседние Minecraft cells.

Semantic cleanup колонн будет отдельной будущей задачей.

======================================================================
12. СНАЧАЛА AUDIT, НО НЕ ОСТАНАВЛИВАЙСЯ ПОСЛЕ НЕГО
======================================================================

Перед применением создай:

docs/grid-physics/audit.json
docs/grid-physics/audit.md

Для всех production families и city compatibility layer покажи:

- ID;
- PROTECTED / AUTO_SAFE / AMBIGUOUS;
- current occupied/helper cell count;
- derived physical cell count;
- source pattern count;
- source occurrence evidence;
- collision cell count;
- reason;
- proposed change.

Это audit-first, но вся задача должна продолжиться автоматически.

НЕ спрашивай пользователя про каждую ambiguous family.

Дальше:

- применяй PROTECTED = unchanged;
- применяй AUTO_SAFE;
- AMBIGUOUS logical families не переосмысливай;
- city compatibility делай cell-local.

Если сам immutable input отсутствует/повреждён или hash не совпадает —
тогда STOP с конкретной ошибкой.

======================================================================
13. ЗАЩИТА ОТ РЕГРЕССИЙ
======================================================================

До изменений сохрани frozen fingerprints.

После изменений обязательно:

RENDER_HASH_CHANGED_UNEXPECTEDLY = FAIL
SOURCE_MAPPING_CHANGED_UNEXPECTEDLY = FAIL
PROTECTED_FAMILY_CHANGED = FAIL
COLLISION_OUTSIDE_PHYSICAL_FOOTPRINT = FAIL
HELPER_OUTSIDE_PHYSICAL_FOOTPRINT = FAIL
GRID_EDITABILITY_FAIL = FAIL

Для AUTO_SAFE render mesh / texture / source migration должны быть
байт-в-байт или семантически идентичны baseline.

Не "починяй" unrelated failing family автоматически.

======================================================================
14. BUILD / TESTS
======================================================================

Запусти минимум:

- все Python/unit tests;
- logical contract validators;
- production fingerprint verification;
- production coverage;
- grid physics audit/validators;
- GameTests;
- full `check build`;
- dedicated-server tests, если существующий workflow их использует.

Добавь GameTests для:

- single-cell object + placement stone со всех 6 сторон;
- multi-cell logical object + placement рядом с mask;
- helper creation/removal;
- breaking root removes only owned physical helpers;
- protected object regression;
- connected wall/fence neighbor add/remove;
- rotation of physical masks N/E/S/W;
- no collision outside local cells.

Не заявляй graphical acceptance, если Minecraft client реально не запускался.

======================================================================
15. ПОСЛЕ МОДА — ПОЛНАЯ КОНВЕРТАЦИЯ КАРТЫ
======================================================================

После успешного JAR/tests сразу создай НОВУЮ полностью конвертированную карту.

На этот раз НЕ делай Conservative/Aggressive пару.

Нужен ОДИН основной FULL output.

Критическое новое требование пользователя:

НЕ ДОЛЖНО БЫТЬ "МЯГКОЙ" ОБРАБОТКИ,
при которой подтверждённые Bloodborne blocks просто оставляются старыми
из-за reject/conflict.

Необходимо полностью заменить BLOODBORNE ARCHITECTURAL LAYER всей карты.

Это НЕ означает "перезаписать каждый посторонний Minecraft block".

Правило ownership:

если cell ДОКАЗАННО относится к Bloodborne layer через хотя бы одно из:

- current bloodborne_blocks state;
- historical Bloodborne module/carrier ID;
- exact existing city compatibility mapping;
- exact migration source match;
- existing verified Bloodborne owned/helper relation;

то эта cell должна получить current final representation.

Внутри этого доказанного Bloodborne set conversion является AUTHORITATIVE.

Не оставлять terminal states:

REJECTED_BLOODBORNE_LEFT_AS_LEGACY
UNKNOWN_BLOODBORNE_ID
OLD_MODULE_ID_LEFT
OLD_VANILLA_CARRIER_LEFT
ORPHAN_ARCHITECTURE_PART

Количество таких cells после full conversion должно быть ZERO.

Но:

НЕ расширяй Bloodborne-owned set по render bounds, proximity или визуальной
догадке.

Cells вне доказанного Bloodborne set сохраняй как есть:
- другие моды;
- обычные постройки;
- block entities;
- inventories;
- entities;
- player data;
- metadata.

======================================================================
16. КАК FULL CONVERSION ОБРАБАТЫВАЕТ AMBIGUOUS OBJECTS
======================================================================

Это важно для полной карты и позволяет не угадывать.

Если old/source object можно надёжно преобразовать в reviewed/AUTO_SAFE
logical family:
→ конвертировать в logical object.

Если semantic logical mapping/physical footprint неоднозначны:
НЕ оставлять старый carrier/module.

Вместо этого:
→ заменить каждую доказанную source cell на соответствующий current
   ONE-CELL CITY COMPATIBILITY block/state,
   сохраняющий её текущий визуал максимально точно.

Таким образом:

- вся карта реально переходит на текущий мод;
- никаких legacy old blocks;
- никакого AI guessing;
- ambiguous architecture остаётся редактируемой по клеткам;
- позже её можно semantic-upgrade отдельной задачей.

Это НЕ считается "мягкой обработкой".
Это explicit deterministic fallback.

======================================================================
17. КОНВЕРТАЦИОННЫЙ INPUT
======================================================================

Конвертацию делай ЗАНОВО от latest verified MODDED backup.

Не используй предыдущую Conservative/Aggressive converted карту как
исходник для накопительного re-convert, если immutable MODDED backup доступен.

Vanilla source world:
ТОЛЬКО reference oracle для:

- original XYZ;
- source blockstates;
- source collision;
- orientation;
- known assembly evidence.

Не копируй из vanilla world поверх пользовательского MODDED мира.

======================================================================
18. ПОСЛЕ FULL CONVERSION ОБЯЗАТЕЛЬНО ПРОВЕРЬ
======================================================================

Whole-world scan.

Требования:

- unknown Bloodborne IDs = 0;
- old module IDs = 0;
- old Bloodborne vanilla carrier cells that should migrate = 0;
- orphan helpers = 0;
- helpers outside physical footprint = 0;
- collision outside local physical cells = 0;
- unresolved Bloodborne cells = 0;
- rejected proven Bloodborne cells = 0.

Проверь ВСЕ chunks.

Сохрани все unrelated nonterrain files.

Проверь:

- entities;
- block entities;
- inventories;
- player data;
- dimensions;
- metadata;
- other-mod content outside owned Bloodborne set.

Повторно запусти converter на полученной карте.

Second pass обязан дать:

logical changes = 0
compatibility changes = 0
helper changes = 0
physics reconciliation changes = 0

То есть полная idempotence.

======================================================================
19. RELEASE
======================================================================

Если следующий свободный номер beta — beta.3, используй:

2.1.0-beta.3

Если beta.3 уже существует — следующий свободный beta.

Создай понятную release directory, например:

releases/Bloodborne-Blocks/2.1.0-beta.3-grid-physics/

Положи туда минимум:

- final JAR;
- FULL converted world ZIP;
- README.md;
- proof.json;
- Grid-Physics-Reports.zip.

Пример названия мира:

Bloodborne-City-Beta3-Full-Grid.zip

Не создавай Conservative/Aggressive варианты без необходимости.

В README кратко объясни:

- что физика теперь grid-based;
- render overhang не резервирует соседние cells;
- что было PROTECTED/AUTO_SAFE/AMBIGUOUS;
- ambiguous map cells получили one-cell compatibility fallback;
- вся доказанная Bloodborne map layer заменена;
- что client graphical acceptance был/не был выполнен.

В proof.json:

- input hashes;
- base HEAD;
- final HEAD;
- JAR hash;
- world hash;
- family counts;
- protected count;
- auto-safe count;
- ambiguous count;
- compatibility count;
- whole-world verification;
- second-pass zero counts;
- tests.

======================================================================
20. GIT / HANDOFF
======================================================================

Делай понятные checkpoint commits:

1. audit + frozen baseline/protected set;
2. grid physics runtime/contract;
3. AUTO_SAFE + compatibility normalization;
4. connected known families;
5. tests/build;
6. full world conversion/release;
7. final docs/handoff.

Не squash уже существующую историю.

Обнови:

Bloodborne-Blocks/HANDOFF.md

В финале push в main только после успешных tests/verification.

После push проверь remote HEAD.

======================================================================
21. КРИТИЧЕСКАЯ ФИЛОСОФИЯ ЗАДАЧИ
======================================================================

Не пытайся сделать систему "умнее", чем доказательства.

Приоритет:

1. user/manual reviewed decision;
2. existing current logical identity/source pattern;
3. exact source-world evidence;
4. deterministic compatibility fallback;
5. НИКОГДА не AI visual guess.

Эта задача должна исправить архитектурный КЛАСС grid/physics ошибок по всему
моду и всей карте, а не вручную чинить только замеченные скриншотами стены,
колонны или окно.

Главная проверка результата:

после фикса игрок должен иметь возможность редактировать город как обычную
Minecraft-постройку:

если соседняя Minecraft-cell не является явно physical частью объекта,
в неё можно поставить обычный блок независимо от того, насколько далеко туда
визуально выступает Bloodborne model.

ПРИ ЭТОМ ранее вручную исправленные и подтверждённые объекты не должны
регрессировать.