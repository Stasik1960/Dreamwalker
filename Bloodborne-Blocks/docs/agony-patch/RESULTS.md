# Агония — cumulative patch поверх functional restoration

База: `3d78c873278451756b2d950606163ce5f93d5933`, 56 production families.
Результат: **49 families**, 1660 BASE/ALT states, 702 meshes. Это не полный
resource-pack каталог. 227 deferred families не возвращались.

## Идентификация и изменения

Все 11 страниц DOCX прочитаны последовательно, включая комментарии после
перенесённых изображений. Точное сопоставление: [PATCH-PLAN.md](PATCH-PLAN.md).

| Группа | Current IDs / результат |
|---|---|
| Верхняя площадка | `o_ladder_01`: симметричные края/UV, одна простая collision поверхности; отдельный item, не climbable |
| Кусок ограждения | `o_iron_railing`: удалён из production; исходные состояния явно `CONTEXT_ONLY`, не потребляются |
| Подсвечники | `o_candles_0`: 19 опорных групп, видимое основание каждой на y=0; source XYZ/anchor/patterns неизменны |
| C561/C046 | `o_c561` удалён, authored survivor `o_c046`; все пять review source records сохранены; CASES не объединён |
| Мешки | `o_c1962_a`/`o_c1962_b` удалены; один internal `o_bag`, provenance **C1962_a**, четыре weighted model variants |
| Книги | `o_c1319` удалён; survivor `o_books`, C003 component 2, два weighted model variants |
| Две двери | На обоих скриншотах **тот же `o_acacia_door`** с `open=false`; второй registry ID не найден. Остальные двери/C282 не удалены |
| Скамьи | `o_bench_rotate` → `o_bench`; facing × diagonal = восемь baked поз; ближайшее свободное из трёх мест |
| Два дерева | Оба — **`o_c001`, `tree_254d81d6bf3e`**, BASE/ALT; шесть действительно разных вариантов сохранены |

C003 остаётся разобранным: компоненты **1/3/6 → BARREL** (независимые
размещения), **2 → BOOKS**, **4 → SACK**, **5 → CASES**. Composite `o_c003`
не возвращён. `visual=base|alt` независимо от model variant и не меняет физику.

## Размещение и поведение

- `o_shuttered_window`: clicked wall cell заменяется только при безопасной
  проверке. Соседние клетки полного 3×3 проёма должны быть свободны заранее;
  автоматического вырезания стены нет. BlockEntity, bedrock, чужие helpers,
  жидкость/занятый проём отклоняются без расхода предмета. В survival подходящий
  инструмент нужен в другой руке; правильный loot возвращается, инструмент
  получает один износ. Четыре facing, неподвижная рама, authored open/closed
  створки и прозрачная двусторонняя маска вместо непрозрачного backing cube.
- Все `o_c008_1/2/3/5`: `hand_lantern=none|unlit|lit`. `o_lantern` используется
  по root/helper статуи и становится частью baked модели, а не независимым
  блоком. ПКМ пустой рукой переключает 0/15; Shift+ПКМ снимает. Survival
  возвращает один предмет, при разрушении сохраняется lit state. Creative
  не производит лишний предмет при снятии. Именованные/нестандартные NBT
  фонари отклоняются без потери данных: компактный blockstate не хранит такой NBT.
  Новых BlockEntity или BER для фонарей нет.
- `o_ladder_02` удалён как ненужный самостоятельный объект; raw source оставлен
  непотребляемым `CONTEXT_ONLY`. Полноценный successor не придуман без evidence.
- `o_ladder_03`: wall support, одна тонкая collision plane, два owned cells.
  Следующая секция ставится через два блока по фактической interaction height,
  включая клик по helper. Проверяются пять секций, четыре направления,
  подъём, непроходимость поперёк, удаление середины/верха и повторная установка.
- Площадка в галерее находится на 0.1875 ниже верха последней лестничной
  секции. Между плоскостью лестницы и краем deck — 0.875 по горизонтали:
  это короткий переход/прыжок, **не** обещание бесшовной плоской дорожки.
- Скамья: anchors лежат на высоте plank y=1.0. Player offset −0.35,
  vehicle offset −0.40 и hip pivot +0.75 дают pelvis y=1.0. Смещение
  синхронизировано DataTracker с клиентом. Seat entities существуют только
  при занятии; максимум три игрока, удаление/выход очищает их.

## Source mapping: важная неоднозначность

C046/C561 имеют одинаковый authored mesh, но **отличаются от нынешнего
`o_cases_0`**. При этом сохранённые vanilla carriers у них неразличимы:
`oxidized_cut_copper_stairs`, straight/bottom → `addon/cases_0`.
Frozen census даёт те же 77 occurrences. Уже в baseline у C046 был redirect
в CASES, а compiled raw patterns C046 и C561 были пусты.

Поэтому C561 семантически объединён с C046 для палитры, все пять точных
review observations сохранены; **raw source по-прежнему ведёт в C003 CASES**.
Два конкурирующих matcher не созданы. Это явная сохранённая неоднозначность,
а не заявление об автоматическом распознавании C046 по отсутствующему признаку.
Изменение этого выбора потребует отдельного решения пользователя, не нового
сканирования города. CASES и остальные unrelated данные не менялись.

## Проверки и артефакты

Итог: **148 Python tests, 32/32 GameTests, BUILD SUCCESSFUL**.
`check logicalGameTest levelMetadataCheck build` выполнены на Java 17,
wrapper Gradle 8.8, с `verification-direct-resources.init.gradle` и
`level-metadata-check.init.gradle`. Support/orientation/resource gates — PASS;
6640 orientation checks. В JAR сверены все 1869 исходных resources
(разрешена только штатная вставка ссылки Loom на сгенерированный mixin refmap).
Названия изменены у **26 RU / 46 EN** записей живой палитры.
Cached source usage удаляемого ограждения — **758 cells**, ladder_02 —
**199 cells**, по четырём точным исходным состояниям каждого. Они остаются
явным непотребляемым контекстом, а не исчезают из source evidence.

Команды воспроизведения после подготовки Python с numpy/Pillow и JAVA_HOME=JDK17:

```powershell
.\gradlew.bat --offline -I tools/verification-direct-resources.init.gradle `
  '-PbloodbornePython=<python.exe>' check logicalGameTest build
<python.exe> tools/production_fingerprints.py --verify --allowlist docs/agony-patch/allowlist.json
<python.exe> tools/build_production_gallery.py --verify build/production-gallery-saves/production-agony-gallery-ready-20260924
<python.exe> tools/production_coverage.py --gallery build/production-gallery-saves/production-agony-gallery-ready-20260924
```

Сохранены [полный итоговый log](check-build.log) и [GameTest XML](gametests.xml).

- [Immutable baseline](baseline-fingerprints.json.gz),
  [untouched regression](untouched-regression.json): **37 untouched families**;
  display names разрешены отдельно от геометрии/поведения/источников.
- [Независимый coverage](coverage.json): source census/mapping → semantic
  decision → Contract V2 → registry input → gallery. Семь retired IDs имеют
  явные решения; статусы, successors и frozen source evidence сверяются между
  собой, проверяются негативными mutation tests.
- [Exact duplicate checker — baseline](exact-duplicates.json) и
  [current 49-family recommendations](current-exact-duplicates.json).
  Три оставшихся предложения C1979_1/2, C471_a/b, C1979_4/5 не применены:
  автоматический merge не отменяет сохранённые semantic decisions.
- [Названия RU/EN](../production-display-names.md),
  [offline preview целевых объектов](preview.png),
  [alpha/UV evidence](visual-qa.json),
  [штатный LevelStorage](level-metadata.txt),
  [итог машинных проверок, числа тестов и SHA-256](release-proof.json).
- Галерея: **49 canonical предметов, 73 образца**, 580 helpers. Есть настоящие
  wall/window specimens, четыре статуи с/без фонарей, standalone lit/unlit,
  пять секций лестницы с площадкой, восемь скамей, closed/open двери,
  dedup survivors и BASE/ALT proof. Retired IDs отсутствуют.

Полный source world не сканировался и не конвертировался; исходные ZIP не
изменены. Визуальная проверка — offline mesh previews и alpha analysis;
GameTests выполняются на Fabric dedicated server. Графический клиент для
итоговой игровой приёмки не запускался — эта часть остаётся человеку.

## Работа агентов

- Scout-разведка: идентификация текущих runtime путей и дубликатов по frozen
  evidence; без изменения production данных.
- `functional_restore_builder`: fingerprints/gate, exact checker, восемь
  направлений/три места/высота и client sync скамьи, surface GameTests.
- `agony_attachments_builder`: attachment runtime/tests, display names,
  функциональная gallery, alpha/UV audit и исправление её проверки.
- `agony_final_review`: независимая read-only проверка безопасности replacement,
  permissions/NBT, successor integrity и неоднозначности C046/CASES.
- Главный агент: полное чтение DOCX, semantic решения, ограниченный patch compiler,
  geometry/placement, интеграция, дополнительные safety fixes, проверки и выпуск.
