# Latest client QA — authoritative requirements

Эти четыре скриншота являются последним ручным client QA и новее `RESULTS.md`.

## 01-ladder03.png — `bloodborne_blocks:o_ladder_03`
- не ставится нормально друг на друга;
- climbing почти сразу стопорится;
- position относительно support block нужно инвертировать;
- thin collision должна оставаться внутри owned cells.

## 02-ladder01-landing.png — `bloodborne_blocks:o_ladder_01`
- collision не покрывает весь видимый настил;
- через часть площадки проваливаешься;
- нужна support collision по всей walkable deck area.

## 03-statue-lantern.png — statue `o_c008_1`
- `hand_lantern` state уже существует;
- при реальной установке lantern мгновенно исчезает;
- исправить существующую attachment system;
- position/rotation брать из source resource pack;
- lit state должен использовать lit source artwork.

## 04-shuttered-window.png — `bloodborne_blocks:o_shuttered_window`
- отменить прежнюю backing-wall replacement/transparency механику;
- обычный placeable logical object;
- сохранить текущую visual position/depth;
- physical footprint строго 1×2;
- lateral neighboring cells должны оставаться свободными.

