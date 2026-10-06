# Сохранённая RP-часть

Namespace: `bloodborne_rp`. Все зарегистрированные предметы доступны в креативе RP. Числовая архитектура находится в отдельной вкладке DW.

Три trick weapons: `saw_cleaver`, `saw_spear`, `boom_hammer`. Получение: `/give @s bloodborne_rp:saw_cleaver`. Смена формы — клавиша **R** по умолчанию, переназначается в управлении; две формы являются состоянием одного предмета. Решение о смене принимает сервер с cooldown, включая проверку руки и предмета. Vanilla HP игрока сохраняется.

Для управления фонарями администратором:

```mcfunction
/bbrp lamp list
/bbrp lamp register Центральный Ярнам
/bbrp lamp rename <uuid> Новое название
/bbrp lamp link <from-uuid> <to-uuid>
/bbrp lamp route open <from-uuid> <to-uuid>
/bbrp lamp route close <from-uuid> <to-uuid>
/bbrp lamp remove <uuid>
```

Регистрация выполняется рядом с hunter lamp. Travel вызывается через lamp UI по разрешённому маршруту; сервер проверяет источник/назначение, расстояние, измерение, загруженность, границу мира и cooldown. Декорации устанавливаются placement item в creative/OP и управляются `/bbrp object rotate|delete|lock|unlock|list|link|unlink`. Направляйте действие на нужный nearby объект.

## Мобы

Создание: `/summon bloodborne_rp:cleric_beast`; spawn egg: `/give @s bloodborne_rp:cleric_beast_spawn_egg`. Естественные biome spawn rules не добавлены.

| Entity ID | Spawn egg |
|---|---|
| `bloodborne_rp:cleric_beast` | `bloodborne_rp:cleric_beast_spawn_egg` |
| `bloodborne_rp:vicar_amelia` | `bloodborne_rp:vicar_amelia_spawn_egg` |
| `bloodborne_rp:blood_starved_beast` | `bloodborne_rp:blood_starved_beast_spawn_egg` |
| `bloodborne_rp:scourge_beast` | `bloodborne_rp:scourge_beast_spawn_egg` |
| `bloodborne_rp:executioner` | `bloodborne_rp:executioner_spawn_egg` |
| `bloodborne_rp:huntsman_a` | `bloodborne_rp:huntsman_a_spawn_egg` |
| `bloodborne_rp:huntsman_b` | `bloodborne_rp:huntsman_b_spawn_egg` |
| `bloodborne_rp:huntsman_c` | `bloodborne_rp:huntsman_c_spawn_egg` |
| `bloodborne_rp:huntsman_d` | `bloodborne_rp:huntsman_d_spawn_egg` |
| `bloodborne_rp:huntsman_wheelchair` | `bloodborne_rp:huntsman_wheelchair_spawn_egg` |
| `bloodborne_rp:large_huntsman` | `bloodborne_rp:large_huntsman_spawn_egg` |
| `bloodborne_rp:carrion_crow` | `bloodborne_rp:carrion_crow_spawn_egg` |
| `bloodborne_rp:giant_rat` | `bloodborne_rp:giant_rat_spawn_egg` |
| `bloodborne_rp:small_rat` | `bloodborne_rp:small_rat_spawn_egg` |
| `bloodborne_rp:rabid_dog` | `bloodborne_rp:rabid_dog_spawn_egg` |
| `bloodborne_rp:maneater_boar` | `bloodborne_rp:maneater_boar_spawn_egg` |
| `bloodborne_rp:brick_troll` | `bloodborne_rp:brick_troll_spawn_egg` |
| `bloodborne_rp:rotted_corpse` | `bloodborne_rp:rotted_corpse_spawn_egg` |

## Entity-декор

Это сущности, а не дополнительные цифровые block IDs. Их модели, текстуры и анимации сохранены.

| Entity ID | Устанавливаемый предмет |
|---|---|
| `bloodborne_rp:babycart` | `bloodborne_rp:babycart_placer` |
| `bloodborne_rp:boat` | `bloodborne_rp:boat_placer` |
| `bloodborne_rp:cabriolet_closed` | `bloodborne_rp:cabriolet_closed_placer` |
| `bloodborne_rp:cabriolet_open` | `bloodborne_rp:cabriolet_open_placer` |
| `bloodborne_rp:cage_obj_1` | `bloodborne_rp:cage_obj_1_placer` |
| `bloodborne_rp:cage_obj_2` | `bloodborne_rp:cage_obj_2_placer` |
| `bloodborne_rp:cage_obj_3` | `bloodborne_rp:cage_obj_3_placer` |
| `bloodborne_rp:carriage` | `bloodborne_rp:carriage_placer` |
| `bloodborne_rp:chair` | `bloodborne_rp:chair_placer` |
| `bloodborne_rp:chandelier_large` | `bloodborne_rp:chandelier_large_placer` |
| `bloodborne_rp:chandelier_small` | `bloodborne_rp:chandelier_small_placer` |
| `bloodborne_rp:chest` | `bloodborne_rp:chest_placer` |
| `bloodborne_rp:coffin_1` | `bloodborne_rp:coffin_1_placer` |
| `bloodborne_rp:coffin_2` | `bloodborne_rp:coffin_2_placer` |
| `bloodborne_rp:coffin_3` | `bloodborne_rp:coffin_3_placer` |
| `bloodborne_rp:coffin_stand` | `bloodborne_rp:coffin_stand_placer` |
| `bloodborne_rp:coffins_1` | `bloodborne_rp:coffins_1_placer` |
| `bloodborne_rp:coffins_2` | `bloodborne_rp:coffins_2_placer` |
| `bloodborne_rp:coffins_3` | `bloodborne_rp:coffins_3_placer` |
| `bloodborne_rp:coffins_4` | `bloodborne_rp:coffins_4_placer` |
| `bloodborne_rp:coffins_5` | `bloodborne_rp:coffins_5_placer` |
| `bloodborne_rp:cross_beast` | `bloodborne_rp:cross_beast_placer` |
| `bloodborne_rp:curtain_half` | `bloodborne_rp:curtain_half_placer` |
| `bloodborne_rp:curtains` | `bloodborne_rp:curtains_placer` |
| `bloodborne_rp:curtainsmall` | `bloodborne_rp:curtainsmall_placer` |
| `bloodborne_rp:dog_cage` | `bloodborne_rp:dog_cage_placer` |
| `bloodborne_rp:dog_cage1` | `bloodborne_rp:dog_cage1_placer` |
| `bloodborne_rp:dog_cage2` | `bloodborne_rp:dog_cage2_placer` |
| `bloodborne_rp:dog_cage3` | `bloodborne_rp:dog_cage3_placer` |
| `bloodborne_rp:door_1` | `bloodborne_rp:door_1_placer` |
| `bloodborne_rp:door_2` | `bloodborne_rp:door_2_placer` |
| `bloodborne_rp:door_empty` | `bloodborne_rp:door_empty_placer` |
| `bloodborne_rp:elevator` | `bloodborne_rp:elevator_placer` |
| `bloodborne_rp:furniture_1` | `bloodborne_rp:furniture_1_placer` |
| `bloodborne_rp:furniture_10` | `bloodborne_rp:furniture_10_placer` |
| `bloodborne_rp:furniture_11` | `bloodborne_rp:furniture_11_placer` |
| `bloodborne_rp:furniture_12` | `bloodborne_rp:furniture_12_placer` |
| `bloodborne_rp:furniture_13` | `bloodborne_rp:furniture_13_placer` |
| `bloodborne_rp:furniture_14` | `bloodborne_rp:furniture_14_placer` |
| `bloodborne_rp:furniture_15` | `bloodborne_rp:furniture_15_placer` |
| `bloodborne_rp:furniture_16` | `bloodborne_rp:furniture_16_placer` |
| `bloodborne_rp:furniture_2` | `bloodborne_rp:furniture_2_placer` |
| `bloodborne_rp:furniture_3` | `bloodborne_rp:furniture_3_placer` |
| `bloodborne_rp:furniture_4` | `bloodborne_rp:furniture_4_placer` |
| `bloodborne_rp:furniture_5` | `bloodborne_rp:furniture_5_placer` |
| `bloodborne_rp:furniture_6` | `bloodborne_rp:furniture_6_placer` |
| `bloodborne_rp:furniture_7` | `bloodborne_rp:furniture_7_placer` |
| `bloodborne_rp:furniture_8` | `bloodborne_rp:furniture_8_placer` |
| `bloodborne_rp:furniture_9` | `bloodborne_rp:furniture_9_placer` |
| `bloodborne_rp:furniture_books` | `bloodborne_rp:furniture_books_placer` |
| `bloodborne_rp:furniture_cabinet_pantry` | `bloodborne_rp:furniture_cabinet_pantry_placer` |
| `bloodborne_rp:furniture_pantry` | `bloodborne_rp:furniture_pantry_placer` |
| `bloodborne_rp:gate_empty` | `bloodborne_rp:gate_empty_placer` |
| `bloodborne_rp:hearse` | `bloodborne_rp:hearse_placer` |
| `bloodborne_rp:hook` | `bloodborne_rp:hook_placer` |
| `bloodborne_rp:horse_carcass` | `bloodborne_rp:horse_carcass_placer` |
| `bloodborne_rp:hunterlamp` | `bloodborne_rp:hunterlamp_placer` |
| `bloodborne_rp:ladder` | `bloodborne_rp:ladder_placer` |
| `bloodborne_rp:lamp_npc` | `bloodborne_rp:lamp_npc_placer` |
| `bloodborne_rp:lever_1` | `bloodborne_rp:lever_1_placer` |
| `bloodborne_rp:lever_2` | `bloodborne_rp:lever_2_placer` |
| `bloodborne_rp:long_table` | `bloodborne_rp:long_table_placer` |
| `bloodborne_rp:main_gate` | `bloodborne_rp:main_gate_placer` |
| `bloodborne_rp:npc_window` | `bloodborne_rp:npc_window_placer` |
| `bloodborne_rp:odeon_tomb` | `bloodborne_rp:odeon_tomb_placer` |
| `bloodborne_rp:ropes` | `bloodborne_rp:ropes_placer` |
| `bloodborne_rp:small_gate` | `bloodborne_rp:small_gate_placer` |
| `bloodborne_rp:smallcross` | `bloodborne_rp:smallcross_placer` |
| `bloodborne_rp:stairs` | `bloodborne_rp:stairs_placer` |
| `bloodborne_rp:statue_1` | `bloodborne_rp:statue_1_placer` |
| `bloodborne_rp:trapdoor` | `bloodborne_rp:trapdoor_placer` |
| `bloodborne_rp:tree1` | `bloodborne_rp:tree1_placer` |
| `bloodborne_rp:tree2` | `bloodborne_rp:tree2_placer` |
| `bloodborne_rp:tree3` | `bloodborne_rp:tree3_placer` |
| `bloodborne_rp:wheelchair` | `bloodborne_rp:wheelchair_placer` |
| `bloodborne_rp:wood_gate` | `bloodborne_rp:wood_gate_placer` |

Ресурсные `bullet` и `blood_puddle`, а также шесть weapon form assets присутствуют в каталоге ресурсов, но не считаются отдельными зарегистрированными mobs/decor blocks. Собственные stamina/economy/rally/HUD/auto-reset системы не включены.
