# Исходные дефекты и решения перед переносом

Статус: адресный анализ исходника, до переноса; это не результаты игровой приёмки. Основной ресурсный эталон — Minecraft 1.18.2 и предоставленный v15. Частоты ниже — исходные клетки, а не целые объекты.

Варианты без подходящего selector: 63 полных состояний, 10795 клеток. Нерешённые видимые ресурсы: 3 состояния, 227 клеток.

| Носитель | Клетки без variant | Пример Overworld |
|---|---:|---|
| minecraft:andesite_slab | 5219 | [-54, 62, -242] |
| minecraft:lime_stained_glass | 2239 | [-196, 51, -247] |
| minecraft:acacia_planks | 1715 | [-263, 73, -257] |
| minecraft:andesite_stairs | 698 | [61, 89, -798] |
| minecraft:red_sandstone_stairs | 638 | [-179, 76, -189] |
| minecraft:oak_stairs | 174 | [229, 62, -976] |
| minecraft:nether_brick_stairs | 104 | [-12, 76, -80] |
| minecraft:diorite_stairs | 3 | [28, 71, -81] |
| minecraft:mossy_stone_brick_stairs | 1 | [14, 133, -726] |
| minecraft:polished_blackstone_stairs | 1 | [53, 63, -44] |
| minecraft:red_nether_brick_stairs | 1 | [52, 53, -638] |
| minecraft:smooth_sandstone_stairs | 1 | [-105, 42, -1057] |
| minecraft:birch_stairs | 1 | [164, 72, -1110] |

Полные states, отсутствующие selectors и все сохранённые образцы: RESOURCE_AUDIT_USED_DEFECTS.json. Например, acacia_planks содержит selectors с facing, но фактический блок карты не имеет такого свойства; red_sandstone_stairs определяет только нижнее straight-остекление, тогда как верхние straight-клетки также установлены.

## Нерешённые видимые ссылки

- `minecraft:dandelion` — 6 клеток; образец {'dimension': 'minecraft:overworld', 'pos': [257, 48, -1007]}. Причина: minecraft:block/dandelion: undefined_texture_variable:missing; minecraft:block/dandelion_1: undefined_texture_variable:missing; minecraft:block/dandelion_2: undefined_texture_variable:missing; minecraft:block/dandelion_3: undefined_texture_variable:missing.
- `minecraft:diorite_wall[east=none,north=tall,south=none,up=true,waterlogged=false,west=none]` — 1 клеток; образец {'dimension': 'minecraft:overworld', 'pos': [5, 24, -1075], 'region': 'region/r.0.-3.mca'}. Причина: assets/minecraft/models/block/template_wall_tall.json.
- `minecraft:potted_spruce_sapling` — 220 клеток; образец {'dimension': 'minecraft:overworld', 'pos': [-163, 64, -230], 'region': 'region/r.-1.-1.mca'}. Причина: minecraft:block/potted_spruce_sapling: undefined_texture_variable:dirt.

Эти ссылки требуют подтверждённого авторского ресурса, однозначного технического исправления с журналом либо согласованного исключения/реконструкции. Нельзя выдавать стандартный missing texture за законченный объект и нельзя подставлять похожую модель по имени.

## Ресурсы для будущего каталога

10 отсутствующих model paths и 5 texture paths перечислены в RESOURCE_AUDIT.json с непосредственными referrers. Часть не выбрана существующими состояниями карты, но остаётся проблемой полного строительного каталога: нижняя/открытая iron_door, button_on, window_end_7, pressure_plate14/15, старые родительские шаблоны и пути model_false. Отсутствие текущих установок не означает допустимое удаление объекта.

Шаблон template_trapdoor_bottom содержит после корневого объекта только // комментарии. Сканер разбирает эквивалент без комментариев и сохраняет исходные байты; это не подтверждённая ошибка runtime Gson.

## Отдельные предупреждения

Пустой выбор multipart допускает авторский пустой визуал и учтён отдельно; он не назван отсутствующим variant. Нерешённые particle/неиспользуемые texture variables также отделены от поверхности. Их сырьевые записи нельзя скрывать, но число таких клеток не является числом missing-моделей.

## Существенные решения первого набора

- acacia_stairs: thin aca_door_1 и верхняя aca_door_2 выглядят дверной панелью/верхним обрамлением. Примеры [-93,59,-212] и верхняя часть [-93,61,-212]; ось/створки/ownership должны быть проверены. Название door_1 у другой семиэлементной фасадной модели не доказывает дверь.
- wood_window имеет неподвижный центральный элемент и боковые части с собственными pivot и ±22.5°. Не следует вращать весь фасад вместе со створками. Назначение отдельных панелей и желаемое открытие требуют игровой приёмки.
- Все 34 beehive honey_level=1 имеют точно соседнюю vanilla ladder: 28 north-секций [177,y33..60,-1108] → z+1, 6 west-секций [173,y60..65,-1105] → x+1. Две верхние площадки honey_level=0 стоят назад относительно секций; они не повторяют правило перемещения секции. SOURCE carrier/support и соседние блоки не удалены.
- Реальные занятые соседями клетки показаны для door_1 [-307,70,-221], тонкого окна [-121,53,-241] и wood_window [-32,37,-1120]. Маска, занимающая эти стены/ограды, требует явного представления пересечения; автоматическое удаление/сдвиг запрещены.
- 59 RP tree1/tree2/tree3 остаются RP-сущностями. Более поздняя bounded source проверка FIRST_SET_SOURCE_EVIDENCE.json доказала два отдельных 18-cell архитектурных дерева log/wool/melon с tree_1/tree_2 UV. V15 patch note не доказывает их отсутствие в фактической карте. Глобальное членство ещё не закреплено; docs/FIRST_SET_DECISIONS.md содержит точные примеры и пересечения.

Точные исходники и ограничения: PROTOTYPE_SOURCE_CANDIDATES.json; все 1121 model records: CATALOG_CANDIDATES.json; реальные клетки: RESOURCE_AUDIT_NEIGHBORHOODS.json; цепочка первых лестниц: RESOURCE_AUDIT_LADDER_CLOSURE.json.

## Отличия целевой версии

При подстановке стандартных ресурсов 1.20.1 отличаются 61 разрешённые model chains и 18 текстурных файлов. Это отдельный риск переноса, а не исходный дефект карты. Сохраняйте подтверждённые исходные зависимости; полные before/after: RESOURCE_AUDIT_TARGET_DIFF.json.

Четыре wood_ladder_01..04 имеют собственные элементы без внешних parent и одну исходную spirelamp_0095.png. Для этой конкретной цепочки целевая версия не меняет модели; это не обобщается на остальные 61 отличающиеся цепочки.
