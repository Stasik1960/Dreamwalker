# Ресурспак v15: блоки и варианты, отсутствующие в исходном мире v16

Проверка: **PASS**. Исходный commit анализа: `e40cf0151c972df693cc4fb5a8306323c93f8756`.

Прочитаны все **47 регионов / 35,890 чанков** исходного ZIP. Найдены **484 ID / 3852 реально использованных состояний**. Измерение: `minecraft:overworld`; иных terrain-регионов в ZIP нет. Мир имеет DataVersion 2975 (1.18.2). Проверены все сохранённые чанки, включая неполностью сгенерированные.

Карта только прочитана. Содержимое ZIP не извлекалось и не изменялось; SHA до и после совпали. Считались положительные индексы ячеек, а не простое наличие записи в палитре.

## Итоговые списки

- **14 полностью отсутствующих носителей** из 246 файлов blockstates самого ресурспака.
- Дополнительно **93 отсутствующих ванильных носителей**, затронутых наследованием моделей или текстурами пакета. Они выделены отдельно: их blockstates JSON в ZIP нет.
- **107 законченных самостоятельных исходных моделей** отсутствуют и напрямую, и среди родительских зависимостей; совпадающая используемая визуальная модель также не найдена.
- Дополнительно **8 законченных составных вариантов** (2 типов), у которых все видимые исходные части отсутствуют. Они тоже включены в главный CSV.
- **602 отсутствующих веток состояний** у носителей, которые в других состояниях на карте присутствуют. Это включает повороты/связи/позы одного объекта, поэтому число не равно числу новых объектов.

Краткий список для просмотра: [absent-objects-short.csv](absent-objects-short.csv). Главная таблица с причинами: [absent-objects.csv](absent-objects.csv). ID носителей: [absent-blocks.csv](absent-blocks.csv). Все отсутствующие исходные модельные пути, включая служебные части и дефекты: [absent-models.csv](absent-models.csv). Состояния: [absent-states.csv](absent-states.csv).

Русские названия декоративных объектов взяты из текущего каталога мода; это каталожные подписи, а точный исходный идентификатор дан рядом. Русское название ванильного носителя не обязательно описывает его декоративную модель.

## Полностью отсутствующие носители из blockstates ресурспака

| ID носителя | Русское название носителя |
|---|---|
| `minecraft:bookshelf` | Книжная полка |
| `minecraft:dead_tube_coral` | Мёртвый трубчатый коралл |
| `minecraft:green_candle` | Зелёная свеча |
| `minecraft:heavy_weighted_pressure_plate` | Большегрузная весовая пластина |
| `minecraft:large_amethyst_bud` | Большой аметистовый бутон |
| `minecraft:medium_amethyst_bud` | Средний аметистовый бутон |
| `minecraft:oak_pressure_plate` | Дубовая нажимная плита |
| `minecraft:powder_snow` | Рыхлый снег |
| `minecraft:powder_snow_cauldron` | Котёл с рыхлым снегом |
| `minecraft:red_candle` | Красная свеча |
| `minecraft:small_amethyst_bud` | Малый аметистовый бутон |
| `minecraft:spruce_pressure_plate` | Еловая нажимная плита |
| `minecraft:stone_pressure_plate` | Каменная нажимная плита |
| `minecraft:stripped_birch_log` | Обтёсанное берёзовое бревно |

## Законченные самостоятельные модели, отсутствующие в мире

| № мода | Каталожное название | Исходная модель | Причина |
|---|---|---|---|
| 90021 | Светильник · вариант 2 | `minecraft:block/1_spirelamp_001` | Нет blockstate-привязки |
| 90022 | Объёмная декоративная деталь · вариант 2 | `minecraft:block/1_wall_001` | Нет blockstate-привязки |
| 90030 | Плита из рыжих досок | `minecraft:block/acacia_planks` | На карте не выбрана |
| 90042 | Мешок · вариант 5 | `minecraft:block/addon/bag_4` | Нет blockstate-привязки |
| 90043 | Мешок · вариант 6 | `minecraft:block/addon/bag_5` | Нет blockstate-привязки |
| 90051 | Бочка · вариант 7 | `minecraft:block/addon/barrel_6` | Нет blockstate-привязки |
| 90053 | Бочка · вариант 9 | `minecraft:block/addon/barrel_8` | Нет blockstate-привязки |
| 90059 | Книги | `minecraft:block/addon/books_test` | Нет blockstate-привязки |
| 90060 | Дверная секция · верхняя | `minecraft:block/addon/brich_trapdoor_top` | Нет blockstate-привязки |
| 90061 | Кирпичная деталь · секция | `minecraft:block/addon/brick_side` | Нет blockstate-привязки |
| 90063 | Составное декоративное украшение | `minecraft:block/addon/brown_terracotta` | Нет blockstate-привязки |
| 90085 | Цепь · вариант 2 | `minecraft:block/addon/chain_1` | Нет blockstate-привязки |
| 90109 | Ступенчатый модуль | `minecraft:block/addon/cyan_terracotta/polished_blackstone_stairs1` | Нет blockstate-привязки |
| 90110 | Ступенчатый модуль | `minecraft:block/addon/cyan_terracotta/polished_blackstone_stairs2` | Нет blockstate-привязки |
| 90111 | Ступенчатый модуль | `minecraft:block/addon/cyan_terracotta/polished_blackstone_stairs3` | Нет blockstate-привязки |
| 90112 | Ступенчатый модуль | `minecraft:block/addon/cyan_terracotta/polished_blackstone_stairs4` | Нет blockstate-привязки |
| 90113 | Ступенчатый модуль | `minecraft:block/addon/cyan_terracotta/polished_blackstone_stairs5` | Нет blockstate-привязки |
| 90114 | Ступенчатый модуль | `minecraft:block/addon/cyan_terracotta/polished_blackstone_stairs6` | Нет blockstate-привязки |
| 90115 | Ступенчатый модуль | `minecraft:block/addon/cyan_terracotta/polished_blackstone_stairs7` | Нет blockstate-привязки |
| 90116 | Ступенчатый модуль | `minecraft:block/addon/cyan_terracotta/polished_blackstone_stairs8` | Нет blockstate-привязки |
| 90117 | Ступенчатый модуль | `minecraft:block/addon/cyan_terracotta/polished_blackstone_stairs9` | Нет blockstate-привязки |
| 90119 | Ящик с бутылками · вариант 1 | `minecraft:block/addon/dead_tube_coral_0` | На карте не выбрана |
| 90157 | Светильник | `minecraft:block/addon/lamp` | Нет blockstate-привязки |
| 90159 | Объёмная декоративная деталь · вариант 1 | `minecraft:block/addon/light_gray_glazed_terracotta_0` | Нет blockstate-привязки |
| 90162 | Фонарь | `minecraft:block/addon/mini_lantern` | Нет blockstate-привязки |
| 90177 | Объёмная декоративная деталь · открытая | `minecraft:block/addon/openspace` | Нет blockstate-привязки |
| 90180 | Пилястра · вариант 3 | `minecraft:block/addon/pilaster_2` | Нет blockstate-привязки |
| 90181 | Объёмная декоративная деталь · вариант 1 | `minecraft:block/addon/pink_glazed_terracotta_0` | Нет blockstate-привязки |
| 90186 | Кровельный модуль · секция | `minecraft:block/addon/roof_spike_side` | Нет blockstate-привязки |
| 90217 | Архитектурная плита · вариант 9 | `minecraft:block/addon/slabs_8` | Нет blockstate-привязки |
| 90225 | Статуя · вариант 3 | `minecraft:block/addon/statue_2` | На карте не выбрана |
| 90226 | Статуя · вариант 4 | `minecraft:block/addon/statue_3` | На карте не выбрана |
| 90227 | Статуя · вариант 5 | `minecraft:block/addon/statue_4` | На карте не выбрана |
| 90228 | Статуя · вариант 6 | `minecraft:block/addon/statue_5` | На карте не выбрана |
| 90235 | Объёмная декоративная деталь · вариант 1 | `minecraft:block/addon/test_upperward_0` | Нет blockstate-привязки |
| 90244 | Надгробие · вариант 9 | `minecraft:block/addon/tombstone_gathered_8` | Нет blockstate-привязки |
| 90246 | Настенный орнамент · вариант 3 | `minecraft:block/addon/wall_deco_2` | Нет blockstate-привязки |
| 90264 | Деревянное украшение · вариант 4 | `minecraft:block/addon/wood_obj_3` | Нет blockstate-привязки |
| 90265 | Деревянный ящик | `minecraft:block/addon/wooden_box` | Нет blockstate-привязки |
| 90266 | Декоративная панель | `minecraft:block/allium` | На карте не выбрана |
| 90287 | Составное декоративное украшение | `minecraft:block/black_carpet` | На карте не выбрана |
| 90300 | Высокий ажурный шпиль | `minecraft:block/blackstone` | На карте не выбрана |
| 90306 | Декоративная панель | `minecraft:block/blue_glazed_terracotta` | Нет blockstate-привязки |
| 90319 | Книги | `minecraft:block/bookshelf` | На карте не выбрана |
| 90416 | Глина · текстура BB | `minecraft:block/clay` | На карте не выбрана |
| 90423 | Ступенчатый модуль | `minecraft:block/cobbled_deepslate_stairs` | Нет blockstate-привязки |
| 90427 | Ступенчатый модуль · внешний угол | `minecraft:block/cobbled_deepslate_stairs_outer` | Нет blockstate-привязки |
| 90450 | Кирпичная кладка | `minecraft:block/cracked_stone_bricks` | На карте не выбрана |
| 90453 | Декоративная напольная деталь | `minecraft:block/crimson_hyphae` | Нет blockstate-привязки |
| 90481 | Составное декоративное украшение | `minecraft:block/daylight_detector` | Нет blockstate-привязки |
| 90482 | Пятнистая серая кладка | `minecraft:block/dead_brain_coral_block` | На карте не выбрана |
| 90484 | Объёмная декоративная деталь | `minecraft:block/dead_bubble_coral_fan` | Нет blockstate-привязки |
| 90517 | Фигура охотника · вариант 1 | `minecraft:block/dummy/hunter_0` | Нет blockstate-привязки |
| 90518 | Фигура охотника · вариант 2 | `minecraft:block/dummy/hunter_1` | Нет blockstate-привязки |
| 90519 | Объёмная декоративная деталь | `minecraft:block/e` | Нет blockstate-привязки |
| 90558 | Гравий · текстура BB | `minecraft:block/gravel` | На карте не выбрана |
| 90582 | Скамья · вариант 4 | `minecraft:block/hold/bench_3` | Нет blockstate-привязки |
| 90584 | Дымовая труба | `minecraft:block/hold/chimney` | Нет blockstate-привязки |
| 90600 | Готический вимперг · вариант 2 | `minecraft:block/hold/wimperge_1` | На карте не выбрана |
| 90601 | Готический вимперг · вариант 3 | `minecraft:block/hold/wimperge_2` | На карте не выбрана |
| 90602 | Готический вимперг · вариант 4 | `minecraft:block/hold/wimperge_3` | На карте не выбрана |
| 90604 | Деревянный ящик | `minecraft:block/hold/wooden_box_x1` | Нет blockstate-привязки |
| 90625 | Настил из тёмных досок; Настил из тёмных досок · наклон 90° | `minecraft:block/jungle_wood` | На карте не выбрана |
| 90627 | Светильник · вариант 2 | `minecraft:block/lamp_1` | На карте не выбрана |
| 90628 | Светильник · вариант 3 | `minecraft:block/lamp_2` | На карте не выбрана |
| 90629 | Светильник · вариант 4 | `minecraft:block/lamp_3` | На карте не выбрана |
| 90630 | Светильник · вариант 5 | `minecraft:block/lamp_4` | На карте не выбрана |
| 90635 | Объёмная декоративная деталь | `minecraft:block/lapis_ore` | Нет blockstate-привязки |
| 90636 | Каменный шпиль на основании | `minecraft:block/lectern` | На карте не выбрана |
| 90642 | Объёмная декоративная деталь | `minecraft:block/light_blue_glazed_terracotta` | Нет blockstate-привязки |
| 90660 | Декоративная каменная поверхность · цветная | `minecraft:block/lightblue_concrete_powder` | Нет blockstate-привязки |
| 90665 | Широкая оконная решётка | `minecraft:block/lime_stained_glass` | На карте не выбрана |
| 90673 | Переплетённые сухие ветви | `minecraft:block/lime_wool` | На карте не выбрана |
| 90678 | Объёмная декоративная деталь | `minecraft:block/magenta_glazed_terracotta` | Нет blockstate-привязки |
| 90717 | Объёмная декоративная деталь · секция | `minecraft:block/oak_fence_side` | Нет blockstate-привязки |
| 90719 | Высокая декоративная стойка | `minecraft:block/oak_pressure_plate` | На карте не выбрана |
| 90720 | Высокая декоративная стойка | `minecraft:block/oak_pressure_plate_on` | На карте не выбрана |
| 90755 | Густое сплетение сухих ветвей | `minecraft:block/pink_wool` | На карте не выбрана |
| 90790 | Тонкая золотистая напольная пластина · вариант 2 | `minecraft:block/pressure_plate/light_weighted_pressure_plate_1` | На карте не выбрана |
| 90792 | Тонкая золотистая напольная пластина · вариант 4 | `minecraft:block/pressure_plate/light_weighted_pressure_plate_3` | На карте не выбрана |
| 90841 | Свечи | `minecraft:block/red_candle` | На карте не выбрана |
| 90842 | Свечи · вариант 3 | `minecraft:block/red_candle_2` | На карте не выбрана |
| 90843 | Свечи · вариант 4 | `minecraft:block/red_candle_3` | На карте не выбрана |
| 90844 | Объёмная декоративная деталь | `minecraft:block/red_carpet` | На карте не выбрана |
| 90849 | Объёмная декоративная деталь | `minecraft:block/red_glazed_terracotta` | Нет blockstate-привязки |
| 90853 | Кирпичная деталь · секция | `minecraft:block/red_nether_brick_wall_side_tall` | Нет blockstate-привязки |
| 90854 | Кирпичная деталь · секция, вариант 2 | `minecraft:block/red_nether_brick_wall_side_tall_1` | Нет blockstate-привязки |
| 90860 | Объёмная декоративная деталь | `minecraft:block/red_sand` | На карте не выбрана |
| 90862 | Ступенчатый модуль | `minecraft:block/red_sandstone_stairs` | Нет blockstate-привязки |
| 90887 | Объёмная декоративная деталь · секция | `minecraft:block/sandstone_wall_side_tall` | Нет blockstate-привязки |
| 90888 | Объёмная декоративная деталь · секция, вариант 2 | `minecraft:block/sandstone_wall_side_tall_1` | Нет blockstate-привязки |
| 90918 | Составное декоративное украшение | `minecraft:block/small_amethyst_bud` | На карте не выбрана |
| 90949 | Объёмная декоративная деталь | `minecraft:block/spruce_pressure_plate` | На карте не выбрана |
| 90950 | Объёмная декоративная деталь | `minecraft:block/spruce_pressure_plate_on` | На карте не выбрана |
| 90962 | Ступенчатый модуль · внешний угол, вариант 4 | `minecraft:block/stairs/axed_exposed_cut_copper_stairs_outer_3` | Нет blockstate-привязки |
| 90987 | Ступенчатый модуль · внутренний угол, правая | `minecraft:block/stairs/dark_prismarine_stairs_inner_right1` | На карте не выбрана |
| 93032 | Ступенчатый модуль · внутренний угол, верхняя; Ступенчатый модуль · внутренний угол, верхняя · наклон 180° | `minecraft:block/stairs/polished_diorite_stairs_top_inner` | На карте не выбрана |
| 93045 | Ступенчатый модуль | `minecraft:block/stairs/smooth_sandstone` | Нет blockstate-привязки |
| 93049 | Надгробие · вариант 3 | `minecraft:block/stairs/tombstone_2` | На карте не выбрана |
| 93065 | Ступенчатый модуль · внутренний угол, верхняя, левая | `minecraft:block/stairs/waxed_oxidized_cut_copper_stairs_inner_left_top` | На карте не выбрана |
| 93097 | Настил из широких тёмных досок; Настил из широких тёмных досок · наклон 90° | `minecraft:block/stripped_acacia_wood` | На карте не выбрана |
| 93099 | Настил из коротких тёмных досок; Настил из коротких тёмных досок · наклон 90° | `minecraft:block/stripped_jungle_wood` | На карте не выбрана |
| 93100 | Настил из неровных тёмных досок; Настил из неровных тёмных досок · наклон 90° | `minecraft:block/stripped_oak_wood` | На карте не выбрана |
| 93101 | Декоративная напольная деталь | `minecraft:block/stripped_warped_stem` | Нет blockstate-привязки |
| 93119 | Объёмная декоративная деталь | `minecraft:block/waxed_oxidized_cut_copper` | Нет blockstate-привязки |
| 93141 | Широкий раскидистый сухой куст | `minecraft:block/yellow_wool` | На карте не выбрана |
| 93151 | Высокая декоративная стойка · секция | `minecraft:block/ywlloe_stained_glass_pane_noside_alt` | Нет blockstate-привязки |

## Цельные составные варианты с полностью отсутствующими частями

| № мода | Название | Вариант | Исходные части |
|---|---|---|---|
| 93155 | Готическое окно · левая секция | `bloodborne_dw:source_gothic_window_left[variant=0]` | `minecraft:block/window_bottom_left;minecraft:block/window_top_left` |
| 93229 | Стеклянная панель · жёлтая · Столб | `bloodborne_dw:source_assembly_yellow_stained_glass_pane[variant=0]` | `minecraft:block/yellow_stained_glass_pane_noside;minecraft:block/yellow_stained_glass_pane_noside_alt;minecraft:block/yellow_stained_glass_pane_post` |
| 93229 | Стеклянная панель · жёлтая · Конец | `bloodborne_dw:source_assembly_yellow_stained_glass_pane[variant=1]` | `minecraft:block/yellow_stained_glass_pane_noside;minecraft:block/yellow_stained_glass_pane_noside_alt;minecraft:block/yellow_stained_glass_pane_post;minecraft:block/yellow_stained_glass_pane_side` |
| 93229 | Стеклянная панель · жёлтая · Прямая | `bloodborne_dw:source_assembly_yellow_stained_glass_pane[variant=2]` | `minecraft:block/yellow_stained_glass_pane_noside;minecraft:block/yellow_stained_glass_pane_noside_alt;minecraft:block/yellow_stained_glass_pane_post;minecraft:block/yellow_stained_glass_pane_side;minecraft:block/yellow_stained_glass_pane_side_alt` |
| 93229 | Стеклянная панель · жёлтая · Угол | `bloodborne_dw:source_assembly_yellow_stained_glass_pane[variant=3]` | `minecraft:block/yellow_stained_glass_pane_noside;minecraft:block/yellow_stained_glass_pane_noside_alt;minecraft:block/yellow_stained_glass_pane_post;minecraft:block/yellow_stained_glass_pane_side` |
| 93229 | Стеклянная панель · жёлтая · Тройник | `bloodborne_dw:source_assembly_yellow_stained_glass_pane[variant=4]` | `minecraft:block/yellow_stained_glass_pane_noside;minecraft:block/yellow_stained_glass_pane_post;minecraft:block/yellow_stained_glass_pane_side;minecraft:block/yellow_stained_glass_pane_side_alt` |
| 93229 | Стеклянная панель · жёлтая · Крест | `bloodborne_dw:source_assembly_yellow_stained_glass_pane[variant=5]` | `minecraft:block/yellow_stained_glass_pane_post;minecraft:block/yellow_stained_glass_pane_side;minecraft:block/yellow_stained_glass_pane_side_alt` |
| 93229 | Стеклянная панель · жёлтая · Южный конец | `bloodborne_dw:source_assembly_yellow_stained_glass_pane[variant=6]` | `minecraft:block/yellow_stained_glass_pane_noside;minecraft:block/yellow_stained_glass_pane_noside_alt;minecraft:block/yellow_stained_glass_pane_post;minecraft:block/yellow_stained_glass_pane_side_alt` |

Это отдельное достаточное доказательство отсутствия целого объекта. Комбинации уже присутствующих частей не объявлялись отсутствующими на основании частоты отдельных моделей; отсутствующие raw-состояния приведены отдельно в `absent-states.csv`.


## Служебные части, дубликаты и дефекты

| Класс исходной модели | Отсутствующих путей |
|---|---:|
| `ASSEMBLY_PART` | 2 |
| `EMPTY_OR_PARENT_HELPER` | 2 |
| `EXACT_DUPLICATE` | 10 |
| `FINISHED_UNUSED_ART` | 68 |
| `MULTIPART_SERVICE_PART` | 5 |
| `RESOURCE_OR_ITEM` | 4 |
| `SOURCE_DEFECT` | 49 |
| `STANDALONE_REFERENCED_ART` | 73 |
| `TEMPLATE_OR_INVENTORY_HELPER` | 3 |
| `UNCHANGED_VANILLA` | 30 |

Служебные multipart/assembly-части, пустые или прозрачные helpers, шаблоны, предметные ресурсы, намеренные исключения и повреждённые исходники не включены в главный список самостоятельных законченных объектов. Они сохранены в полной таблице. Для точного дубликата с присутствующим аналогом стоит `ABSENT_PATH_VISUAL_DUPLICATE_PRESENT`: отсутствует этот JSON-путь, но его визуальный аналог присутствует.

## Метод и границы проверки

Variant-условия сопоставлены с подмножеством NBT Properties; лишние свойства не мешают совпадению. Multipart учитывает OR/AND и альтернативы через |. Все weighted-применения рассчитаны по сохранённым XYZ и позиционному Java RNG; наличие носителя не приравнено к наличию каждой случайной модели. Родительские модели учитываются отдельно. Отчёт описывает логическое применение моделей, независимо от закрытости соседями и видимости камерой.

Проверялись размещённые terrain-блоки. Предметы в сундуках, CIT, entity-held/falling blocks и block-entity NBT не являются terrain-размещениями и здесь не подсчитывались. Отсутствие в сохранённом мире не предсказывает будущую генерацию. Игровая проверка, пересоздание галереи и изменения мода для этой аналитической задачи не выполнялись.

Ресурспак SHA-256: `29b31e3744af5794ae95ebe4c5474e3b70dfb08c143355bce815c08ceef09a36`.  
Карта SHA-256: `39e83f2941b768fd2ae9f84d8f57dc76aec78bd926dc5e38eab3059251d2bb08`.

Доказательства: [world-states.json](world-states.json), [model-usage.json](model-usage.json), [summary.json](summary.json). В `all-carriers.csv` и `all-models.csv` сохранены также присутствующие строки и координатные примеры.
