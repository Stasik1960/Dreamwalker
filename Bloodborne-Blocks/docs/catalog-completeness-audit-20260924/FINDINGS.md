# Технические находки: семейства, исключения, регрессии

Актуальное состояние и числа — [SUMMARY.md](SUMMARY.md). Все ID ниже имеют namespace
`bloodborne_blocks`. Ни одно изменение мода по этим находкам не выполнено.

## 1. Фактический фильтр и registry

`tools/build_production_palette.py:72` выбирает:

```python
selected = set(families) - set(data['hidden']) - {'o_c282_a', 'o_c282_b', 'o_c008_4'}
selected.add('o_c282')
```

`families` — 47 сохранённых Contract V2, а не 298 старых logical definitions,
734 модели ресурспака или весь source world. В `manifest()` остальные non-V2 получают
UNRESOLVED с общей причиной, без индивидуальной геометрической экспертизы.
Поэтому категория `unresolved_ambiguous` здесь означает «отложено фильтром», а не
доказанную неоднозначность каждого объекта. 251 таких исключение — **regression_lost
по покрытию относительно предыдущего runtime**, намеренный результат фильтра, а не
случайная потеря файлов при ZIP-упаковке. Прежняя игровая корректность не доказана.

Common initializer `src/main/java/dev/dreamwalker/bloodborneblocks/BloodborneBlocks.java:57`
читает только logical definitions. В строках 125–134 регистрируются internal
`architecture_part`, 33 пары block/item и creative group. Source mapping теперь
offline evidence; его наличие в docs не возвращает удалённые carrier/modular IDs.

`tools/build_production_gallery.py:29` выбирает BASE/closed/default; строки 60–61
берут только production objects. Фактические MCA подтверждают все 33 master ID,
их координаты и properties из embedded `production-gallery.json`. Между выданным
JAR и live resources не обнаружено расхождения: 673 файла совпадают с учётом штатной
подстановки version и Loom refmap. Поэтому отсутствие старых семей здесь нельзя
объяснить «случайно выдали другую палитру».

## 2. Двери, ставни и ворота

| Семейство | Source-модели | Carrier cells в Yharnam | Сейчас / причина |
| --- | --- | ---: | --- |
| `o_acacia_door` | `aca_door_1`, `aca_door_2` | 69 | Нет Contract V2, UNRESOLVED, отсутствует в JAR/галерее |
| `o_birch_door` | `bir_door_1`, `bir_door_2` | 88 | Та же потеря покрытия; ещё 1 carrier cell в `eh_s2:world` |
| `o_dark_oak_door` | `dark_door_1`, `dark_door_2` | 185 | Та же потеря покрытия; ещё 189 carrier cells в gm_room |
| `o_shuttered_window` | `spruce_trapdoor_bottom`, `spruce_trapdoor_open` | 3 214 | Нет Contract V2; ещё 3 214 cells в gm_room и 3 в world |
| `o_c282` | `dark_door_1` | Использует тот же carrier universe, не все его экземпляры | PRODUCTION; в галерее 49,64,126, north/closed/BASE |
| `o_iron_gate` | `addon/gate_bottom`, `addon/gate_top` | 30 | PRODUCTION; в галерее 67,64,160, north/closed/BASE |

Числа клеток — **не количество собранных дверей/окон**. Старые exact assembly rules
в этом аудите не применялись. Наличие C282 из `dark_door_1` не доказывает замену всей
прежней семьи, включая `dark_door_2`, и тем более acacia/birch.

C282 менял семантику: цельная QA-композиция → отдельные `_a/_b` в Nightmare checkpoint
→ одна двустворчатая дверь в последнем production checkpoint. Последний переход
явно запрограммирован `production_door.py` и текущим генератором, это не случайная
потеря створок. `o_c282_a/_b` классифицированы fragment_merged, а не regression_lost.
Root/context не являются подвижными створками; source root относительно floor master
скорректирован отдельно. Старые in-city pane conflicts остаются ограничением,
а не основанием записывать успешно выполненную миграцию.

## 3. Все 21 прежнее non-static семейство вне production

| Прежний behavior | ID |
| --- | --- |
| door (3) | `o_acacia_door`, `o_birch_door`, `o_dark_oak_door` |
| shutter (1) | `o_shuttered_window` |
| connected (6) | `o_stone_railing`, `o_ornate_balustrade`, `o_carved_balustrade`, `o_stepped_balustrade`, `o_high_balustrade`, `o_stone_curb` |
| ladder (3) | `o_ladder_01`, `o_ladder_02`, `o_ladder_03` |
| lantern (6) | `o_candles_0`, `o_lanterns`, `o_wall_lantern`, `o_lantern`, `o_lightning_rod`, `o_oak_wood` |
| bench (2) | `o_bench`, `o_bench_rotate` |

Это фактические значения `behavior` в сохранённых definitions, не утверждение,
что названия точно соответствуют предметам или поведение было полностью рабочим.
Общая причина исключения всех 21 — отсутствие в V2 selection; индивидуальные
source links и counts приложены в MANIFEST. Дополнительно удалённые `_a/_b` C282
в эти 21 не входят: у них есть намеренный новый semantic successor.

## 4. Составные объекты и ранее заявленные исправления

| Семейство / решение | Фактический результат сейчас | Регрессия / ограничение |
| --- | --- | --- |
| C001 + C009 | Один `o_c001`, 6 tree variants, 4 facing, BASE/ALT | Геометрия сохранена; галерея выставляет лишь один вариант/ракурс |
| C002 | `o_c002`, цельная композиция с малыми могилами/камнями | В Nightmare recipe прямо **No split**, сохранены 66 elements; последней сборкой split не потерян — его не было |
| C003 | `o_c003`, OBJECT components 1–6, включая bag/barrel/books/cases | Сохранённое решение допускает составность; NOT_MENTIONED в Nightmare visual review |
| C008 | `o_c008_1`, `_2`, `_3`, `_5`; 6 source placements | Старый composite split; `_4` дубликат `_2` с facing −90°, а не пропавшая статуя |
| C1491 | `o_c1491_a`, `_b`, `_c` | Три самостоятельные могилы; геометрия сохранилась, исторический barrier conflict не объявлен решённым |
| C1962 | `o_c1962_a`, `_b` | Два самостоятельных мешка; retained geometry PASS |
| C1979 | `o_c1979_1` … `_5` | Группы **2+12 / 6+13 / 3+4+5 / 7+9 / 8+10**; 1/11 context. Это не split каждого component |
| C471 | `o_c471_a`, `_b` | Два шпиля; геометрия сохранена, лишний facing не нужен для yaw-symmetric artwork |
| C654 | `o_c654_a` A/A и `o_c654_b` B/B | Оба в registry/gallery; source migration только A, B manual-only. Старый смешанный A/B composite выведен |
| C028 / C1680 | Retained after authored removal of marked side elements | Current textured geometry совпадает с post-fix frozen baseline; не подтверждает визуальное качество самого recipe |
| C618 / wall deco | Retained after lift; `o_wall_deco_1` collision NONE | Исправленные meshes сохранены; неопределённость wall mount plane остаётся |
| C282 | Один whole double-door ID вместо двух leaf items | Намеренная поздняя коррекция, отдельно от потерь non-V2 doors |

Для всех live ID кроме намеренно перекомпилированного C282 проведено точное
сравнение последовательностей textured vertices с frozen authoring input во всех
состояниях. Нормализованы только записанные byte-identical texture aliases.
Результат: **32 семейства PASS**, отчёт `retained-geometry-check.json`.
Ни похожие названия, ни равные bounding boxes не использовались вместо этого сравнения.

Это позволяет сказать «cleanup не потерял текущие meshes этих семей», но не
«все исправления визуально правильны». Неверная изначальная граница композиции,
монтаж в окружении и collision не доказываются равенством полигонов.

## 5. Классификация всех отсутствий

В машинных отчётах отдельные множества, не одна смешанная численность:

- Historical families: 251 `unresolved_ambiguous` (фильтр non-V2),
  6 `fragment_merged`, 6 `requested_split`, 1 `duplicate_deduplicated`,
  1 `compatibility_removed`.
- Для split/merge отдельно записаны `semantic_successor_ids` и условный
  `migration_replacement` **из vanilla source**, не alias старого modded-world ID.
  Current old-logical migration пуст: совместимость со старыми derived saves ранее
  исключена из требований. C654 → A, B manual-only; C008_4 содержит facing transform.
- Pack models: отсутствующая live provenance классифицирована как `filtered_out`,
  `unresolved_ambiguous`, `not_present_in_source_world`, `other` с пояснениями по строкам.
  «Нет в source» относится к block applications/parents в terrain всех измерений,
  не к item/entity usage или нерасставленным ассетам ресурспака.
- Discovery candidates: 2 223 UNREVIEWED → filtered_out; 7 UNRESOLVED_VISUAL →
  unresolved_ambiguous. Кандидаты не объявлены финальными объектами автоматически.
- 491 runtime-state отсутствует **как отдельный образец галереи**: ALT →
  hidden_alt_variant; остальные ракурсы/open/формы/variants → filtered_out.
  В самом моде эти состояния не удалены, часть достижима взаимодействием.
- 503 carrier и 46 236 modular ID: целиком retired registry layers (`legacy_removed`).
  Их точные ID сохранены отдельно; по ним нельзя считать самостоятельные предметы.

Категория `interactive_family_skipped` не используется как выдуманная причина:
галерея **не пропускает все интерактивные объекты**, ведь C282/C618/ворота есть.
Старые двери потеряны раньше — на выборе production families.

## 6. Неподтверждённое и проблемное

- Все 33 live families: **VISUAL_ACCEPTANCE NOT_VERIFIED**. Наличие кода, серверного
  теста, JAR или успешно читаемого `level.dat` — разные уровни проверки.
- NOT_MENTIONED: `o_c003`, `o_c046`, `o_c1319`, `o_c474`, `o_c561`, `o_cases_0`,
  `o_iron_gate`, `o_iron_railing`. Общий production provenance завышает доказательства.
- Неопределённый mount plane: live `o_wall_deco_1`, `o_c654_a`, `o_c654_b`.
  Четвёртый из исторического отчёта, `o_c654`, уже retired.
- Исторические source fixtures: 8 SAFE_BLOCKED (C282 A–D, C654 A–C, C1491 A).
  Они проверяли предыдущий checkpoint; аудит не повторял конвертацию и не выдаёт
  устаревшие fixture-числа за текущий успешный city conversion.
- Image 9 fence: NEEDS_USER_DEBUG; registry ID по одному изображению не установлен.
- Complete semantic-object catalog отсутствует как авторитетный артефакт.
  Новый audit связывает имеющиеся уровни, но не придумывает новые смысловые границы.

## 7. Проверенные источники

- `docs/production-authoring-inputs.json.gz`, `pre-production-source-mapping.json.gz`;
  historical blockstates из Git baseline через read-only `git cat-file --batch`.
- `docs/production-logical-palette.json`, current `logical/definitions.json`,
  `contracts-v2.json`, `meshes.json.gz`, `production-resource-pruning.json`.
- `docs/manual-source-assemblies.json`, `nightmare-qa-report.json`,
  `NIGHTMARE-QA-ALT.md`, `nightmare-running-review-coverage.md`, `HANDOFF.md`.
- Выданный JAR и ready-gallery ZIP, реальные MCA/NBT, не только имена файлов.
- Original world/pack ZIP по SHA256; vanilla 1.20.1 client assets для parent-chain
  и blockstate resolution; weighted variants по фактическому XYZ, не по первой модели.

Для повторного чтения результаты уже сохранены: повторный scan/build не нужен.
