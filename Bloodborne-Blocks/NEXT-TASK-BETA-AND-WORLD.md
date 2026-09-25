> Latest city continuation: [compact compatibility and remaining model gap](docs/city-compat/README.md). Beta.2 code/artifacts are delivered; conservative/aggressive unknown cells are now 33/13. In-game testing was explicitly excluded by the user. Historical task below is preserved.

> Execution record, 2026-09-25: continue from [current HANDOFF](HANDOFF.md), not from this task's historical starting point. Beta, converter, both reported conversion copies, ALT kit and checkpoints were delivered. Automated QA passed; full-city beta compatibility remains UNRESOLVED (legacy registry IDs), and graphical acceptance was not run. The original requirements below are retained verbatim.

# Bloodborne-Blocks — beta fix, конвертация latest MODDED backup и ALT artist kit

Продолжаем существующий проект:

`Stasik1960/Dreamwalker/Bloodborne-Blocks`

Работай только от актуального checkpoint после cumulative patch **«Агония»**.

Не начинай проект заново.  
Не возвращайся к старым batch-02 / QA2 / functional-restoration checkpoint как к рабочей базе.  
Не запускай новый discovery.  
Не делай batch-03.  
Не выполняй глобальный re-authoring production palette.  
Не возвращай legacy registry layers.

## 0. Сначала восстанови точное текущее состояние

Обязательно прочитай:

- `Bloodborne-Blocks/HANDOFF.md`;
- `Bloodborne-Blocks/NEXT-TASK-BETA-AND-WORLD.md` — этот файл;
- актуальный `RESULTS.md` checkpoint «Агония»;
- production manifests / coverage manifests;
- Contract V2;
- current definitions / meshes / source mappings;
- текущие tests;
- `docs/handoff-20260925/Агония.docx`, если он лежит в репозитории;
- `docs/handoff-20260925/latest-client-qa/` и README к четырём последним скриншотам;
- все документы, на которые ссылается HANDOFF.

Проверь:

- branch;
- HEAD;
- git status;
- remote;
- Git LFS;
- что рабочая baseline соответствует именно checkpoint «Агония».

Ожидаемая baseline:

- 49 production families;
- 1660 BASE/ALT states;
- 702 meshes;
- старый composite `o_c003` не существует;
- `o_ladder_02` уже удалён;
- у статуй уже существует `hand_lantern=none|unlit|lit`;
- у `o_ladder_03` уже существует реализация stacking/climbing, но реальный client QA доказал, что она работает неправильно;
- у `o_shuttered_window` уже существует wall/placement logic, которую теперь нужно упростить;
- regression fingerprints, coverage gate и exact-duplicate checker уже существуют.

Если repo не соответствует этой baseline:
не начинай исправления вслепую. Сначала найди/восстанови корректный последний checkpoint.

### Роль `Агония.docx`

`Агония.docx` — historical semantic evidence уже реализованного checkpoint.

Не применяй его инструкции повторно поверх текущего состояния, если они уже отражены в `RESULTS.md` или противоречат более новым требованиям этого задания.

Если нужно понять, почему конкретный объект был split/merged/removed, используй `Агония.docx` как первичный human-review evidence.

При чтении документа:
изображение или группа изображений может находиться на одной странице, а относящийся к ним текст — на следующей. Читай документ последовательно, не постранично изолированно.

---

# 1. Защита от регрессий

Это точечный patch.

Перед правками используй существующий fingerprint/regression механизм.

Для всех production families, которые прямо НЕ затронуты этой задачей, должны остаться неизменными:

- registry schema;
- blockstate properties;
- render geometry;
- UV/textures;
- collision;
- selection;
- interaction/helper footprint;
- canonical anchor;
- placement;
- migration/source mapping;
- BASE/ALT;
- gameplay behavior.

Любое неожиданное изменение unrelated family = `REGRESSION FAIL`.

Не переписывай global palette ради текущего фикса.

Отдельно НЕ ТРОГАТЬ сохранённую неоднозначность C046/C561 vs CASES: она уже задокументирована в `RESULTS.md` и не относится к этой задаче.

---

# 2. Прошлые tests уже проходили, но client QA нашёл реальные баги

Предыдущий checkpoint имел BUILD SUCCESSFUL, Python tests, GameTests, orientation/resource QA.

Но последние четыре дефекта найдены реальной проверкой в клиенте.

Поэтому для каждого бага:

1. определить точную runtime-причину;
2. создать regression test, который падает на текущей buggy версии;
3. только потом исправлять;
4. после исправления test должен PASS.

Не создавать параллельную вторую систему, если нужная механика уже существует.

Последние четыре screenshots — authoritative visual evidence для этого задания.

---

# 3. `o_ladder_03` — починить существующую лестницу

Скриншот: `01-ladder03.png`.

Проблемы:

- лестницы не ставятся друг на друга;
- при climbing игрок стопорится почти сразу;
- visual/placement находится на неправильной стороне блока;
- нужно инвертировать положение лестницы относительно блока/стены;
- через плоскость нельзя свободно проходить;
- collision не должна занимать соседние не-owned клетки.

## 3.1 Placement / inversion

Сохранить один logical block/item.

Определи текущую wall normal и authored model offset.

Инвертировать placement/model position относительно support cell:
модель должна оказаться на противоположной стороне support plane по wall normal.

Не делать hardcoded NORTH-only offset.

NORTH / EAST / SOUTH / WEST должны быть эквивалентны.

Сверь intended visual depth с source resource pack.

Canonical master не должен случайно смещаться.

## 3.2 Continuous climbing

Найди фактический Minecraft/Fabric code path, определяющий climbing movement.

Вся вертикальная logical/interaction footprint одной секции `o_ladder_03` должна быть непрерывно climbable.

Недопустимо:
master climbable → helper/owned cell above non-climbable → игрок стопорится.

Helper cells должны корректно resolve owner/master и участвовать в climbable logic.

## 3.3 Collision

Нужно:

- игрок может взбираться;
- игрок не может просто пройти сквозь плоскость лестницы.

Использовать thin wall-plane collision.

Она:

- не мешает climbing;
- не выходит за physical owned cells;
- не блокирует соседние свободные клетки;
- корректно вращается во всех 4 facing.

## 3.4 Stacking

Должно реально работать размещение минимум 5 `o_ladder_03` подряд вверх.

Работать при клике:

- по master;
- по верхней helper/owned части предыдущей секции.

Следующий master должен вычисляться выше всей фактической interaction height текущей лестницы.

Helpers предыдущей секции не должны блокировать следующую.

Проверить:

- stack 5;
- climb через все 5 без остановки;
- break top;
- break middle;
- helper cleanup;
- повторную установку;
- все 4 facing.

---

# 4. `o_ladder_01` — полная walkable collision площадки

Скриншот: `02-ladder01-landing.png`.

Существующая collision уже была добавлена, но покрывает не весь визуальный настил.

Нужно:

- вся реально видимая горизонтальная поверхность deck должна иметь support collision;
- игрок должен стоять в любой точке, где визуально есть пол;
- railing/ornamental вертикальные элементы не требуют подробной collision;
- collision должна быть минимально простой, но полностью покрывать walkable deck.

Если площадка занимает несколько physical owned cells:
разделить collision на cell-local AABB.

Не использовать giant AABB, выступающий в соседние клетки.

---

# 5. Глобальный collision invariant

Пользовательское требование:

объекты, находящиеся около границы блока, не должны collision-объёмом занимать соседний block-space, если соседняя клетка не принадлежит physical/owned footprint объекта.

Render mesh может визуально выходить за master cell.

Collision — не должна.

Добавить/усилить validator:

`COLLISION_OUTSIDE_OWNED_CELLS = FAIL`

Для каждого collision AABB после rotation/facing:

- collision полностью лежит внутри union explicit physical owned cells;
- допускается epsilon;
- если object физически занимает несколько cells — collision делится между ними.

Не создавать helper/owned cells только потому, что artwork туда выступает.

`VISUAL_OVERHANG != PHYSICAL_OWNERSHIP`

Если validator обнаруживает старый unrelated defect:
не менять его автоматически без доказанной безопасной коррекции; зафиксировать отдельно.

---

# 6. Statue lantern — исправить существующую attachment system

Скриншот: `03-statue-lantern.png`.

Current state уже существует:

`hand_lantern=none|unlit|lit`

Не создавать новую parallel attachment system.

Реальный баг:
при попытке установить lantern на statue он появляется на мгновение и сразу исчезает.

## 6.1 Найти root cause

Проверить:

- ownership cleanup;
- state replacement;
- interaction return value;
- helper reconciliation;
- server/client sync;
- item consumption;
- tick/state restore.

Regression test:

attach lantern → tick / reconcile → lantern state остаётся установленным.

## 6.2 Positioning

Положение mounted lantern должно соответствовать исходному resource pack.

Использовать:

- source-resource-pack model transforms;
- source visual application;
- original vanilla source world только как positional/reference oracle.

Не угадывать позицию по screenshot, если source evidence позволяет восстановить точнее.

Для каждой production statue family authored:

- local hand attachment position;
- local rotation;
- facing transform.

## 6.3 Lit artwork

Для `hand_lantern=lit` использовать реальную включённую модель/текстуру фонаря из source resource pack, если она существует.

Найти exact source assets:

- unlit;
- lit.

Не имитировать включённое состояние только luminance/tint, если есть отдельный artwork.

Behavior:

- attach → intentional initial state;
- ПКМ `unlit ↔ lit`;
- unlit light = 0;
- lit light = согласованное значение;
- lit render = correct lit artwork.

## 6.4 Persistence / removal

- lantern не исчезает сам;
- lantern можно снять;
- item возвращается;
- breaking statue не теряет lantern;
- никаких permanent BlockEntity/BER без реальной необходимости.

Сделать QA specimens для всех production statues:

- none;
- unlit;
- lit.

---

# 7. `o_shuttered_window` — упростить до нормального placeable object

Скриншот: `04-shuttered-window.png`.

Последнее пользовательское решение отменяет старую идею:

- replacement backing wall;
- прозрачность блока за окном;
- автоматическое вырезание стены;
- mutation neighboring wall blocks.

Это больше не нужно.

## 7.1 Final placement

Окно:

- обычный самостоятельно устанавливаемый logical object;
- ставится в свободное место;
- сохраняет примерно текущую visual position/depth;
- не изменяет block behind;
- не заменяет wall block;
- не делает background block прозрачным;
- не мутирует соседние blocks.

## 7.2 Physical footprint

Строго:

- ширина = 1 block;
- высота = 2 blocks;
- thin depth допустим;
- никаких lateral owned/helper cells.

Physical/interaction footprint = вертикальная колонна 1×2.

Это нужно, чтобы по обе стороны от окна можно было свободно ставить обычные blocks.

## 7.3 Collision

Collision:

- закрывает окно по высоте 2 blocks;
- тонкая по depth;
- находится строго внутри этих двух owned cells;
- не выступает влево/вправо.

Если open/closed shutters сохраняются:

- вращать только shutters;
- master/anchor не вращать;
- не возвращать backing-wall behavior.

Добавить tests:

- normal placement;
- no background mutation;
- exact 1×2 footprint;
- left/right neighbor placement;
- collision confinement;
- open/closed if retained.

---

# 8. Re-test

Минимум:

### Ladder 03
- reproduce old climb-stop bug;
- full-height continuous climbing;
- stack 5;
- climb across all 5;
- inverted placement;
- N/E/S/W;
- collision;
- no collision bleed.

### Ladder 01
- full deck support;
- support points across entire visible walkable surface;
- no collision bleed.

### Statue lantern
- attach survives tick/reconciliation;
- correct statue/facing position;
- lit/unlit artwork;
- toggle;
- detach;
- statue break item preservation.

### Window
- ordinary placement;
- no wall/background mutation;
- exact 1×2 footprint;
- side cells remain placeable;
- collision confined to owned cells.

Затем:

- existing untouched-family regression;
- coverage gate;
- BASE/ALT;
- package/resource verification;
- Python tests;
- GameTests;
- `check build`.

Требуется `BUILD SUCCESSFUL`.

---

# 9. Beta release

После PASS выпустить beta.

Если beta-линии ещё нет:

`2.1.0-beta.1`

Если уже есть — следующую beta.

Обновить:

- mod version source;
- fabric.mod.json/version metadata;
- JAR filename;
- changelog;
- HANDOFF;
- SHA256 manifest.

Создать git checkpoint/tag.

Если graphical Minecraft client доступен:
провести client smoke test четырёх исправленных объектов.

Если client launch недоступен:
явно записать `GRAPHICAL_CLIENT_ACCEPTANCE_NOT_RUN`.

Не переходить к world conversion, если beta regression/coverage fail.

---

# 10. Final world conversion — input только latest MODDED backup

После beta.

INPUT WORLD =
последний backup мира, который УЖЕ содержит modded Bloodborne Blocks.

НЕ использовать original vanilla carrier world как input.

Original vanilla source world используется ТОЛЬКО как reference oracle:

- expected positions;
- source assemblies;
- original artwork origin;
- facing;
- anchor;
- legacy offset comparison.

Если latest modded backup отсутствует:

- не подменять его vanilla world;
- conversion stage = `BLOCKED_MODDED_BACKUP_MISSING`;
- beta и ALT stages продолжить.

Перед conversion:

- verify SHA256;
- immutable input copy;
- input никогда не менять in-place.

---

# 11. Что конвертировать в MODDED backup

Обновить existing old modded Bloodborne blocks до beta production palette:

- retired old Bloodborne IDs → canonical successors;
- semantic merges;
- semantic splits;
- helper footprint updates;
- anchor/placement updates;
- functional restored states;
- old `Агония` states → beta states;
- BASE/ALT states.

Не выполнять raw vanilla conversion повторно.

Сохранять:

- unrelated mod blocks;
- blocks других модов;
- player edits;
- inventories;
- entities;
- world metadata;
- block entities/ticks вне touched-set.

---

# 12. Сверка позиций с original vanilla source

Для изменяемых Bloodborne objects по возможности сравнивать:

- modded backup position;
- original reference position;
- expected master origin;
- facing;
- support plane;
- known legacy offset.

Если старый modded object имеет известный placement/anchor offset bug и original reference однозначно доказывает correct position:
разрешается исправить координаты.

Если доказательств недостаточно:
не угадывать.

Создать ledger:

- dimension;
- XYZ;
- old ID/state;
- new ID/state;
- original reference;
- delta;
- conflicting cells;
- decision;
- reason.

---

# 13. Две версии конвертированной карты

Создать две независимые копии одного latest MODDED input.

## 13A. CONSERVATIVE

Если object conversion доказан и безопасен:
применить.

Если:

- conflict;
- ambiguous mapping;
- foreign occupied cell;
- source mismatch;
- unsafe helper destination;
- uncertain split;
- questionable position correction;

оставить весь проблемный logical object/area ТОЧНО как в input backup.

Никаких частичных conversions объекта.

Сформировать conflict report + `/tp` coordinates.

## 13B. AGGRESSIVE / COMPLETE

Применить best-known canonical mapping для всех old Bloodborne objects, для которых mapping существует.

Можно обходить conservative conflict policy только в доказанном touched-set:

- old Bloodborne master;
- old Bloodborne owned/helper cells;
- new proven owned cells.

Нельзя уничтожать произвольные foreign blocks за пределами этого set.

Для forced cases записать:

- before;
- after;
- exact cells;
- reason.

Если mapping отсутствует:
`UNRESOLVED`, не скрывать.

---

# 14. World QA

Для обеих карт:

- region/NBT integrity;
- valid masters;
- no orphan helpers;
- current IDs exist in beta registry;
- unrelated block entities/ticks preserved;
- entities/inventories/world metadata preserved;
- converter idempotence: второй запуск = 0 изменений.

Conservative report:

- untouched conflict count;
- conflict coordinates/reasons.

Aggressive report:

- forced conflict count;
- unresolved count;
- coordinates/reasons.

Создать JSON + Markdown/HTML report.

---

# 15. ALT Artist Kit

После финальной beta сгенерировать свежий production-only ALT Art Kit.

Не использовать stale старый kit без проверки.

Архив:

`Bloodborne-Blocks-<beta>-ALT-Art-Kit.zip`

Не включать:

- retired;
- compatibility;
- deferred;
- legacy garbage.

Structure:

ALT-Art-Kit/
  README_RU.md
  manifest.json
  manifest.csv
  by-block/
    <logical-id>/
      info.json
      BASE_REFERENCE/
        models/
        textures/
      ALT_WORK/
        models/
        textures/

Для каждого production logical block:

- все реально используемые BASE JSON models;
- PNG textures;
- `.mcmeta`;
- model/variant mapping;
- canonical anchor;
- visual state info;
- runtime target paths.

В `ALT_WORK`:
подготовленная структура для рисования `visual=alt`.

Artist-friendly filenames должны однозначно соответствовать logical block/model.

Например:

`o_c001__tree_variant_1.json`
`o_c001__tree_variant_1.png`

Если model использует несколько textures:
manifest точно описывает зависимости.

Не менять runtime resource naming только ради artist archive.

Shared textures можно копировать локально в каждый by-block folder для удобства,
но manifest должен хранить original runtime path и SHA256.

Также создать:

`ALT-ResourcePack-Template.zip`

с точной runtime resource-pack structure для `visual=alt`.

ALT меняет только:

- model;
- texture;
- UV/render assets.

ALT НЕ меняет:

- collision;
- helpers;
- anchor;
- gameplay;
- migration.

---

# 16. Git / checkpoints

Минимум:

1. client-QA bug fixes + regression tests;
2. beta release;
3. modded-world converter;
4. converted world outputs + reports;
5. ALT Artist Kit + template;
6. final HANDOFF.

После каждого:

- commit;
- push;
- verify remote.

Большие world/JAR/ZIP использовать через existing Git LFS policy.

Не коммитить cache/temp.

---

# 17. Final HANDOFF

Верх `HANDOFF.md` должен содержать:

- beta version;
- beta commit/tag;
- beta JAR path + SHA256;
- exact results of 4 fixes;
- latest MODDED input path + SHA256;
- original vanilla reference path + SHA256;
- conservative world path + SHA256;
- conservative conflict count;
- aggressive world path + SHA256;
- forced conflict count;
- unresolved count;
- ALT Art Kit path + SHA256;
- ALT template path + SHA256;
- exact tests/GameTests/build result;
- graphical client verification status.

---

# 18. Финальный ответ

Кратко сообщить:

1. `o_ladder_03`
   - root cause;
   - stacking;
   - climbing;
   - inverted placement;
   - collision.

2. `o_ladder_01`
   - full deck collision.

3. statue lantern
   - root cause disappearance;
   - persistence;
   - source-pack hand position;
   - lit artwork;
   - toggle.

4. `o_shuttered_window`
   - normal placement;
   - no background mutation;
   - exact 1×2 footprint;
   - confined collision.

5. global collision-boundary result.

6. beta:
   - version;
   - JAR;
   - tests;
   - tag/commit.

7. world conversion:
   - exact MODDED input;
   - conservative output;
   - conflicts;
   - aggressive output;
   - forced/unresolved counts.

8. ALT:
   - Art Kit;
   - resource-pack template.

9. final commit SHA.

Не объявлять выполненным stage, который фактически blocked или не проверен.
