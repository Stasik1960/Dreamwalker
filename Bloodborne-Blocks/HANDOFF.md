# Bloodborne-Blocks — актуальный handoff, 2026-09-24

## Текущий checkpoint: production palette cleanup

После полного read-only анализа правильного source-world и сверки последних
corrections в runtime осталось **33 logical blocks/items + 1 internal helper**,
524 BASE/ALT states. Это не новый discovery/batch-03 и не конвертация города.
Пользователь подтвердил: сохранять требуется только правильный vanilla source world;
старые logical QA saves не требуют совместимости. Оригинальные ZIP не изменены.

- Удалены старые 503 carrier IDs и 46 236 modular IDs из runtime. Из 298 logical
  definitions исключены 6 fragments, 6 superseded, 1 duplicate, 1 compatibility-only
  и 251 UNRESOLVED. Последние не объявлены мусором: evidence сохранён вне runtime.
- C001/C009 — один целый `o_c001`; ветви не существуют как отдельные items.
- C008 — 4 distinct IDs / 6 source placements; `_4` заменён `_2` с facing −90°,
  доказано сравнением всех 66 текстурированных полигонов во всех направлениях.
- C282 — один master/item, две створки вокруг внешних hinges; root/чужой context
  не вращается. Отдельные leaf items удалены. Пересечение с исходными panes остаётся
  SAFE_BLOCKED, без частичного изменения/затирания окружения.
- Полный Yharnam scan: 33 regions, 21 503 chunks, 27 119 750 carrier cells.
  52/64 исторических exact patterns найдены; 1856 matches — не число конверсий.
- Новая галерея: 33 canonical specimens, 93 helpers, 327 белых support cells.
  JAR: 97 371 528 → **850 307 bytes**. Model JSON: 101 361 → 558.
- `check build logicalGameTest`: BUILD SUCCESSFUL, 91 Python tests, 19 GameTests,
  2096 orientation checks. Новый мир принят штатным LevelStorage 1.20.1.
  Все 673 source resources сверены с JAR; визуальная приёмка клиентом ещё нужна.

Начать здесь: [PRODUCTION-PALETTE.md](docs/PRODUCTION-PALETTE.md),
[authoritative manifest](docs/production-logical-palette.json),
[QA evidence](docs/production-checks/README.md),
[JAR и галерея](../releases/Bloodborne-Blocks/README.md).
Для генерации использовать `tools/build_production_palette.py`; прежние carrier,
modular и QA generators относятся к историческим checkpoint ниже, не к live registry.
Следующий шаг — визуальная приёмка компактной палитры, не массовая конвертация города.

## История: общая support-plane нормализация Contract V2

Добавлен общий generation/build-time проход, без исключений по C-ID:
`tools/normalize_support_contracts.py`. Проверяются все **47 V2 families /
740 состояний**, включая BASE/ALT и скрытые совместимые версии. Это не
нормализация остальных 251 non-V2 logical families и не batch-03.

- 13 families имели mesh ниже пола; все 13 автоматически подняты (3 доступные,
  10 скрытых совместимых). Master, raw mesh и migration source patterns сохранены.
- У `o_dead_tree_planter` и `o_iron_gate` были helpers ниже опоры; новые
  footprints их не требуют. Старые лишние helpers удаляются существующей
  очередью проверки ownership при загрузке; неизвестные прежние блоки пола
  автоматически не восстанавливаются.
- Коллизии уже были простыми: **1020 → 1020** global primitives по всем
  состояниям; обычный декор максимум **2**. Новый консервативный fallback
  проверяет основную массу, ограничен тремя boxes и не копирует render cells.
- Четыре wall-family без точной mount plane оставлены неизменными:
  `o_c654`, `o_wall_deco_1`, `o_c654_a`, `o_c654_b`. Отдельное предупреждение
  о collision/render volume у функциональной `o_c282_b`; gameplay не изменён.
- Новый мир: `build/support-gallery-saves/support-plane-gallery-20260924` —
  746 образцов, включая все 740 V2 states; скрытые версии подписаны отдельно.
  Проверены 704 FLOOR specimens и 7778 цельных белых клеток опоры.
- `check build`: **BUILD SUCCESSFUL**, 106 Python unit tests и data/Java QA.
  Dedicated GameTests: **28/28**; orientation: **2960** checks. В готовом JAR
  проверены все **195798** resources, классы, refmap и CRC. Новый мир принят
  штатным LevelStorage 1.20.1; это не визуальная приёмка клиентом.

Описание и ограничения: [SUPPORT-NORMALIZATION.md](docs/SUPPORT-NORMALIZATION.md).
Полный audit: [JSON](docs/support-normalization-audit.json) /
[Markdown](docs/support-normalization-audit.md). Данные до нормализации сохранены
в `docs/support-normalization-baseline.json.gz` для воспроизводимой проверки.
Новые артефакты: [Support-Normalization](../releases/Bloodborne-Blocks/Support-Normalization/).
Команды и итог QA: [support-checks](docs/support-checks/README.md).

Оригинальная карта не изменена, весь город не конвертировался. Старая галерея
и прежний JAR сохранены. Нужна последующая визуальная приёмка нового мира
клиентом; серверные тесты и LevelStorage её не заменяют.

## История: NightmareRunning QA + BASE/ALT

Продолжение QA2 по пятнадцати скриншотам NightmareRunning. Не новый discovery
и не batch-03. Модели/семантические границы исправлены, накоплен один новый JAR;
визуальная приёмка владельцем ещё требуется. Оригинальная карта не изменялась,
полная конвертация города не выполнялась.

- C008 → пять статуй (#3/#6 один ID); C1491 → три могилы; C1962 → два мешка;
  C1979 → пять групп по красным рамкам; C471 → два шпиля; C282 → две двери;
  C654 → окна A/A и B/B. Для миграции прежнего C654 пользователь выбрал A.
- Исправлены противоположные ветви цельного C001; полный C002 поднят без потери
  малых могил/камней; у C028/C1680 удалены отмеченные боковые детали;
  C618 и `o_wall_deco_1` подняты. Неизвестный забор: **NEEDS_USER_DEBUG**,
  ничего не удалять по предположению.
- 286 доступных logical families, 1610 BASE + 1610 ALT states; одна пара
  `visual=base|alt` без новых registry IDs. 12 старых скрытых IDs сохранены
  для совместимости. Runtime bake/resource-pack override, не BER/ticking.
- Команды `/bloodborne visual get|base|alt|toggle`, `pos1`, `pos2`,
  `region base|alt|toggle`: permission 2, загруженные клетки, максимум 32768,
  только visual, разрешение helper→master и дедупликация.
- Atomic raw-source split converter сохраняет контекст. **20 реальных source
  examples преобразуются, 8 SAFE_BLOCKED** (C282 A–D, C654 A–C, C1491 A):
  столкновения новых helpers с исходными panes/stairs/barrier/другим окружением.
  Эти восемь не считать успешно мигрированными и не обходить защиту.
- Новая галерея: 286 families / 1160 образцов, включая шесть BASE/ALT proof
  specimens; нейтральные отдельные площадки и таблички. Старая галерея сохранена.
- ALT starter kit содержит все 1610 post-fix ALT-моделей, PNG и animation
  metadata, manifest с anchors/slots; изначально внешний вид равен BASE.

Артефакты: [Nightmare-QA-ALT](../releases/Bloodborne-Blocks/Nightmare-QA-ALT/).
Полное описание, точные ID, ограничения и команды:
[NIGHTMARE-QA-ALT.md](docs/NIGHTMARE-QA-ALT.md).
Проверки: [nightmare-checks](docs/nightmare-checks/).
Финальные проверки: 77 Python unit tests, 25/25 dedicated-server GameTests,
2960 orientation checks; `check` и `build` завершились BUILD SUCCESSFUL.
Штатный Minecraft 1.20.1 LevelStorage распознал новую галерею. ALT kit проверен
реальным Java resolver: все 1610 моделей и 2123 пути текстур. Подробные команды
и границы проверки: [verification log](docs/nightmare-checks/README.md).
Внутренняя версия JAR `2.1.0-alpha.1`: заменить прежний, не ставить оба.

Следующий шаг: ручная проверка в Minecraft и debug неизвестного забора.
Восемь `NOT_MENTIONED` остаются непроверенными визуально — см.
[coverage](docs/nightmare-running-review-coverage.md). Не продолжать batch-03
или конвертацию города без следующего задания. Штатный LevelStorage и серверные
GameTests не заменяют фактический client resource reload/визуальный осмотр.

## История: batch-02 QA2 — цельные деревья и debug

Исправлены только три запрошенные области: деревья C001/C009, диагностика
наведённого объекта, читаемая синтетическая галерея. Batch-03 и массовая
конвертация города не запускались. Исходная карта и Catalog B discovery/C-ID
не менялись. Предыдущее SPLIT-решение дерева теперь историческое, не текущее.

- Один целый tree block/item `bloodborne_blocks:o_c001`: шесть вариантов
  полного mesh, четыре facing, anchor в основании центрального ствола.
  Один TRUNK box; девять внутренних helpers только по стволу, ветви свободны.
- Старые пять tree-ID сохранены для загрузки существующих размещений, скрыты
  из creative; их предметы/pick перенаправляются на целое дерево. Это только
  inventory aliases: уже размещённые старые logical fragments не заменяются
  по одному (это создало бы дубликаты деревьев).
- Проверенная raw-source граница — 16 carriers. Старые шестиклеточные C001/C009
  предложения захватывали крылья двух соседних деревьев. Новая разметка основана
  на ограниченном чтении 13 chunks у четырёх сохранённых примеров: восемь деревьев,
  две точные source-layout схемы, шесть RNG-guarded правил. Неполные сборки
  сохраняются; недоказанные raw rotations/mirrors не угадываются.
- `/bloodborne debug target`, alias `/bloodborne debug`: permission 2, навести
  прицел, выполнить команду, нажать отчёт для копирования. Разрешает helper в
  master; показывает Review/Logical ID, позицию, state, anchor и policies.
  Source patterns — возможные patterns текущего state, не сохранённая provenance.
- Новая галерея `build/reviewed-batch-02-gallery-qa2`: 104 образца, 21 активная
  V2-family, все варианты дерева × четыре facing. Белый бетон выбран потому,
  что `smooth_sandstone` является carrier. Старые миры не перезаписывались.
  `level.dat` принят штатным Minecraft 1.20.1 LevelStorage summary loader.

Брать для следующего QA:

Фактическая проверка QA2: `check build logicalGameTest qa2Checkpoint` —
**BUILD SUCCESSFUL**, **17/17 GameTests**, **1008 orientation checks**.
Прошли 28 прежних non-tree source examples и 18 tree checks (шесть правил,
восемь полных реальных деревьев, четыре пары соседних деревьев). Все 191 919
ресурсов release JAR сверены с исходниками; packaged mixin check PASS.
Полные небольшие результаты сохранены в `docs/qa2-checks/`.

- [JAR QA2](../releases/Bloodborne-Blocks/bloodborne-blocks-2.1.0-alpha.1-batch-02-qa2-20260923-mc1.20.1.jar)
- [ZIP новой галереи](../releases/Bloodborne-Blocks/reviewed-batch-02-gallery-qa2.zip)
- [Архитектура, команды, проверки и ограничения](docs/REVIEWED-BATCH-02-QA2.md)
- [Машинный отчёт checkpoint](docs/reviewed-batch-02-qa2-checkpoint.json)

Внутренняя версия JAR остаётся `2.1.0-alpha.1`: заменять предыдущий файл, не
устанавливать оба. Далее — визуальная проверка владельцем и отдельные баг-репорты
по объектам с debug-выводом. Не продолжать автоматическую нормализацию или
конвертацию города без следующего задания. Клиентская визуальная приёмка не
подменяется офлайн-рендером, GameTests или проверкой списка миров.

## История: ручные решения Catalog B batch-02 до QA2

18 подтверждённых C-карточек скомпилированы в **20 новых logical families**
(C001/C009 дают по два самостоятельных объекта). С пятью исходными POC теперь
**25 Contract V2 families**, все 25 асимметричны и имеют `facing`; один также
использует connection states. Это интеграционный checkpoint, **не завершение
нормализации всего города**. Batch-03 не реализовывался, массовая конвертация
и новый discovery не запускались. Исходные ZIP не изменялись.

- Authoritative решения и история: `docs/manual-source-assemblies.json`;
  frozen batch-02 snapshot, C-ID, source signatures и discovery сохранены.
- Реализованы C001, C002, C003, C008, C009, C471, C046, C1680, C474, C1962,
  C1979, C028, C282, C561, C618, C654, C1319, C1491.
- Раздельные render/selection/collision/migration contracts; helpers только
  из явно заданного interaction footprint, не из mesh. C046 2/3/4 и C1979 1/11
  остаются CONTEXT. Деревья имеют TRUNK, C474 — POST, C1979 — два опорных box.
- Orientation QA: **912 проверок PASS** (mesh с UV/текстурой, collision,
  selection, helpers, неизменность master при четырёх yaw).
- Проверены **43 эффективных reviewed exact rules**, идемпотентность,
  foreign block entities/ticks, границы чанков/секций и независимый ledger checker.
  **36/36 реальных source examples PASS**, включая co-located SPLIT и реальные
  занятые клетки в helper destinations. Это не скан всей карты.
- Серверные GameTests: **11/11 PASS**; накопительная `check build logicalGameTest`
  завершена с **BUILD SUCCESSFUL**. Ресурсы/registry/geometry checks проходят.
  Клиентская визуальная приёмка ещё нужна; автоматические тесты её не заменяют.
- Review tooling совместим с authoritative overlay и последующими batch;
  существующие source-drift проверки сохранены. Дополнительные 44 Catalog A/B
  tests и portable verifier проходят; immutable snapshot не перерисовывался.

Подробности, воспроизведение, ограничения миграции и чек-лист:
[REVIEWED-BATCH-02-CHECKPOINT.md](docs/REVIEWED-BATCH-02-CHECKPOINT.md).
Новая галерея: `build/reviewed-batch-02-gallery-ready` (100 образцов, 25 × 4 yaw).
Ограниченный migration fixture: `build/reviewed-batch-02/migrated-copy`.
Тестовые миры ignored и не коммитятся; генераторы сохранены.

**Следующий шаг — визуальная проверка владельцем.** Не применять batch-03
и не конвертировать город автоматически до её результатов.

## История: Catalog B v2 / интерфейс ручной проверки

## Последнее обновление: только интерфейс ручной проверки

Portable [batch-02](docs/manual-review/source-assemblies/batch-02/index.html)
повторно отрисован из существующего manifest, **без нового сканирования мира**.
Все 18 карточек и их C-ID сохранены: две отдельные секции — 11
`MULTI-CELL ASSEMBLIES` и 7 `SINGLE-MODEL / PALETTE VARIANTS`.
На 15 карточках `POSSIBLY_INCOMPLETE` сверху заметное предупреждение проверить
CONTEXT; в их примерах ответа нет предложения `OBJECT` по умолчанию.
Каждая карточка объясняет число source cells, число точных source patterns и
границу; `COMPLETE` явно не означает ручное подтверждение объекта.
Несколько patterns отмечаются как «Объединено исходных вариантов»;
`C005 → VARIANT_OF C001` виден непосредственно на C001.

Добавлены форматы ручного ответа `VARIANTS: Cxxx+Cyyy` (внешние варианты одного
смыслового предмета) и `STATE_VARIANTS: Cxxx+Cyyy` (состояния/формы).
Это только инструкции человеку: никакие решения, aliases или состояния
runtime автоматически не применяются. Discovery, signatures, candidates,
manifest semantics, мод, Contract V2, JAR и карта **не изменены**.
Полный manifest и portable snapshot остались побайтово прежними; snapshot SHA-256:
`664b19334e5356a8413b2e664b306af582a16d92bab226e52a1e1d92ad539ba6`.

Выполнены 22 Catalog B tests (включая проверки интерфейса и неизменности данных),
19 Catalog A tests, portable verifier: 18 карточек, 162 choice previews,
251 файл, все относительные ссылки и snapshot/registry соответствуют.
Contact sheet просмотрен. Тестовые сканы используют только синтетические fixtures,
не исходную карту. JAR не собирался.

Команда этого UI-only обновления (из `Bloodborne-Blocks`):

```powershell
python -B -X utf8 tools/source_assembly_review.py --render-only --output build/source-assembly-review-ui --publish docs/manual-review/source-assemblies/batch-02
```

## Данные предыдущего полного discovery v2 (не перезапускался при UI-обновлении)

Изменён **только review pipeline**. Java, runtime resources, Contract V2, JAR,
Catalog A/Fxxx и исходный мир не менялись. Ручные решения не применялись.

- [Новая партия B / batch-02](docs/manual-review/source-assemblies/batch-02/index.html):
  **18 canonical-карточек**; мини-preview каждого точного source pattern и все
  независимые weighted/unweighted alternatives. Primary preview — схема, не
  результат Minecraft positional RNG. Историческая batch-01 сохранена.
- Полностью прочитаны **33 terrain regions / 21 503 chunks** `eh_s2:yharnam` из
  правильного `source-world.zip`; `entities` и `poi` не считаются terrain.
  Найдено **27 119 750 carrier cells**, **2 605 observed source states**.
- **3 349 exact patterns**: 2 605 single-cell inventory templates + 744 multi-cell
  proposals. После geometry-dedup: **2 241 canonical candidates** (1 570 single,
  671 multi). Ещё **7 UNRESOLVED_VISUAL** сохранены явно, не выброшены.
- **C005 → VARIANT_OF C001**. Нумерация прежних Cxxx и история не потеряны;
  у C001 три exact source patterns. Все Cxxx остаются гипотезами, не contracts.
- Отклонено 601 spatial cluster: 526 oversized (>24 cells), 75 extended bounds.
  Их source states остаются в single-cell inventory. За пределами партии:
  2 223 canonical + 7 unresolved candidates.

Основные данные: `docs/manual-source-assemblies.json`,
`docs/source-assembly-coverage.json`, `docs/source-assembly-carrier-index.json.gz`,
`docs/source-assembly-exact-patterns.json.gz`,
`docs/source-assembly-single-cell-candidates.json.gz`.
Подробная семантика счётчиков/сигнатур: [CATALOG-B-V2.md](docs/CATALOG-B-V2.md).
SQLite index — только воспроизводимый ignored cache, **не публикуется**.

```powershell
# Из Bloodborne-Blocks; Python + numpy/Pillow, vanilla client 1.20.1 в Loom cache.
python -B -X utf8 tools/source_assembly_review.py --no-render
python -B -X utf8 tools/source_assembly_review.py --render-only --output build/source-assembly-review-v2-final --publish docs/manual-review/source-assemblies/batch-02
python -B -X utf8 -m unittest discover -s tools -p 'test_source_assembly*.py'
python -B -X utf8 -m unittest discover -s tools -p 'test_manual_review*.py'
python -B -X utf8 tools/verify_source_assembly_catalog.py
```

Не перепубликовывать batch-01 новым генератором. `--previous` нужен только для
явного воспроизведения исторического baseline; обычный запуск использует текущий
manifest. Новые ID append-only; обнаруженное расщепление прежней canonical family
или auto-merge ID с ручным решением останавливает генерацию до явного review.

Проверка: 21 узкий Catalog B test + 19 Catalog A tests; повторная генерация inventory была побайтово
идентична; исходные ZIP защищены SHA-256; preview PNG просмотрены. Сборка JAR и
конвертация мира в этом проходе не запускались. Независимое review выполнено;
исправлены асимметричный tolerance/pairing, пропажа unresolved и риск сокрытия решений.
Geometry hash стабилизирует floating-point шум четверть-оборотов и включает
совместную orientation/позицию всех alternatives, а не только их multiset.
Повторное независимое review этих исправлений — без замечаний.
Portable QA: 251 файл, 18 карточек, все 162 independent choice previews на месте;
относительные ссылки и соответствие batch snapshot полному registry проверены.
SHA-256 manifest при повторной генерации:
`2ece1aa77299b8701038fdfe89dbb5fca898c32019e37b99b483711130ab623b`.

Следующий шаг: ручная проверка batch-02, особенно `POSSIBLY_INCOMPLETE` и смешанных
connected proposals. Не выводить семантическую границу из соседства, category hint
или одинакового набора source models. Не применять C-карточки к runtime до
отдельного разрешения. Сборка остаётся накопительной после 20–30 подтверждённых
и реализованных families.

---

## Исторический checkpoint 2026-09-21 (ниже не текущие команды/ограничения)

Актуальный ограниченный checkpoint, 2026-09-21. **Не переделывать массово мод до
ручной разметки. Не запускать полный discovery, world migration или section renderer.**
Существующий пятисемейный POC сохранён; Catalog A/Fxxx не заменён Catalog B/Cxxx.

- Catalog A: 256 предварительных logical families, стабильные Fxxx,
  `docs/manual-families.json`. Первая партия 25 + 3 дополнительных POC-эталона:
  [portable Catalog A](docs/manual-review/families/batch-01/index.html).
- Catalog B: **12 Cxxx**, пространственные кандидаты непосредственно из source
  blocks/states/XYZ, не из границ Fxxx. [Открыть первую партию](docs/manual-review/source-assemblies/batch-01/index.html)
  или [contact sheet](docs/manual-review/source-assemblies/batch-01/batch-01-contact.png).
- Правильный read-only мир: `reference-inputs/source-world.zip`, SHA-256
  `4353737d536677469d3b895e3515496ab64fab7b224e43428eb96e8c09724a51`.
  Нужен `git lfs pull` при новом checkout. Не использовать исторические converted worlds.
- Read-only pack: `reference-inputs/source-resource-pack.zip`, SHA-256
  `0f2c3d64a1d60734ae0786d128b522ea6bbd175f26f5d164bed5522c46898308`.
- Manifest B: [docs/manual-source-assemblies.json](docs/manual-source-assemblies.json).
  Область поиска и seed-carrier IDs: `docs/source-assembly-scope.json`.
  Проверен только `ether/dimensions/eh_s2/yharnam/region/r.-1.-1.mca`.
  Число похожих экземпляров и rotations относятся **только к этому региону**.

## Фактические границы этого checkpoint

Кандидаты — ограниченные connected clusters выбранных source carrier IDs по
26-соседству. Для явно перечисленных tree carriers дополнительно проверяется
горизонтальный радиус 1 и вертикальный разрыв до 6 ячеек. Отсеиваются одиночные
ячейки, >24 cells, чрезмерные размеры и касание непросмотренного terrain region.
Приоритет — несколько разных исходных носителей, а не single-model items.
Это **гипотеза пространственной группы**, не доказательство одной вещи: соседние
могилы/статуи/мешки могут требовать SPLIT или CONTEXT. Части за пределами seed-ID
списка могут отсутствовать. Окружение отдельное, shell 1 cell вокруг source bounds,
не автоматически часть prefab. Ни одного Cxxx пользователь ещё не подтвердил.

Сигнатура включает source ID, полные properties и relative XYZ, нормализует четыре
Y-поворота, включая facing/axis/rotation/connection keys. Mirrors не нормализуются.
`similar_count` — точные полные carrier clusters modulo этих поворотов. Это не
гарантия визуальной эквивалентности переопределённого ресурспаком vanilla axis.
ID выдаются append-only по spatial fingerprint; регенерация не меняет существующие
номера компонентов/решения. При изменении алгоритма старые записи не удалять:
явно сверять и связывать replacement, не перезаписывать вручную принятые границы.

Preview строится только из исходных ZIP models/textures и vanilla 1.20.1 fallback,
с source blockstate transforms/UV. Это offline rasterizer, не section renderer и
не Minecraft screenshot. Weighted/unweighted alternatives показаны первым
вариантом с предупреждением: vanilla positional RNG ещё не восстановлен.
Изображения не доказывают состояние collision/placement; runtime тут не менялся.

## Команды продолжения (из Bloodborne-Blocks)

Python 3 + numpy + Pillow; локальный vanilla client JAR 1.20.1 в стандартном Loom
cache. Для rendering допустим `BLOODBORNE_VANILLA_JAR`. Не коммитить зависимости/cache.

```powershell
# Открыть уже опубликованный HTML можно вообще без Python/Minecraft.
# Только повторный render, без сканирования мира:
python -B -X utf8 tools/source_assembly_review.py --render-only --publish docs/manual-review/source-assemblies/batch-01
# Повтор ограниченного region scan, сохранение стабильных ID:
python -B -X utf8 tools/source_assembly_review.py --publish docs/manual-review/source-assemblies/batch-01
python -B -X utf8 -m unittest discover -s tools -p 'test_source_assembly*.py'
python -B -X utf8 -m unittest discover -s tools -p 'test_manual_review*.py'
```

Проверено: source SHA до/после совпадает; все 12 entries имеют >1 source cell;
исходные модели/текстуры разрешаются без missing fallback; создан portable review
package с относительными ссылками; PNG просмотрены. Узкие тесты проверяют
rotation fingerprints, stable IDs/authority, spatial gap policy, source transforms,
HTML/PNG/alternative warnings и инструменты Catalog A. POC ранее проверен сборкой
и GameTest — подробности `docs/LOGICAL-CONTRACT-V2-POC.md`; в этом проходе новая
сборка/JAR/запуск Minecraft **не выполнялись**.
В финальном прогоне: 5 Catalog B + 19 Catalog A + 4 contract-v2 + 8 region-reader
unit tests — OK; `tools/test_logical_world.py` — OK (6 converted, 15 rejected,
2 synthetic dimensions). Это маленькие fixtures, не конвертация исходного города.

Что НЕ сделано: полный city discovery/coverage, семантическое подтверждение Cxxx,
автоматический parser/apply решений Catalog B, формирование runtime contracts по
Cxxx, world conversion, section renderer, точный RNG-preview. Catalog A имеет
отдельный `decide`; не применять его к Cxxx.

Следующий шаг: дать пользователю первую партию B, получить OBJECT/SPLIT/CONTEXT и
сохранить ответы как authoritative в **отдельном B manifest** с историей. Затем
реализовать лишь подтверждённые конструкции и direct migration tests. Не выводить
семантическую границу из одного соседства или названия PNG. Сборка накопительная
после 20–30 подтверждённых и реализованных families, не после каждой разметки.

Формат ответов и ограничения: [инструкция партии B](docs/manual-review/source-assemblies/batch-01/README.md).
Ниже сохранены исторические записи; они не отменяют ограничения этого checkpoint.

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
