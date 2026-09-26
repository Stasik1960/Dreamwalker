# Bloodborne Blocks 2.1.0-beta.3 — Grid Physics

Один основной мир: **Bloodborne-City-Beta3-Full-Grid.zip**. Мод для сервера и клиентов: **bloodborne-blocks-2.1.0-beta.3-mc1.20.1.jar** (Fabric 1.20.1, Java 17+).

Распакуйте папку мира в `saves` клиента либо выберите её как отдельный мир сервера. Замените прежний Bloodborne JAR на beta.3 на сервере и клиентах. Исходный MODDED backup не изменён.

Физика теперь явно описывается отдельными physical masks и локальными collision boxes. Все 2914 compatibility-блоков занимают ровно одну Minecraft-клетку; визуальные выступы не создают соседние helpers. Исправлена точка установки 25 старых native compatibility families. Модели, текстуры, UV, IDs и source mappings сохранены.

49 production logical families имеют ручные/authored решения и сохранены как PROTECTED. Среди них нет unprotected AUTO_SAFE/AMBIGUOUS logical families. Многоклеточные ручные объекты остаются многоклеточными. Неоднозначные/конфликтующие исходные сборки, не повышенные до logical объекта, представлены текущими одноклеточными compatibility states, а не старыми module IDs.

Из исходного MODDED ZIP заново преобразованы 21673 logical objects и 27527924 module cells. До конвертации удалены 15789 доказанно устаревших compatibility helpers. Посторонние блоки не принуждались к замене.

Проверены все 10009 чанков; неизвестных ID, старых modules, недопустимых states, исходных vanilla-carrier matches и orphan helpers — 0. Все 170 nonterrain files сохранены. Проверка остальных NBT/данных прошла. Второй проход меняет 0 объектов/клеток/helpers; все 186 файлов мира побайтно совпадают.

`check build` PASS, 37/37 GameTests на dedicated Fabric server PASS, 30/30 целевых Python tests PASS. Полный unittest discovery также запускался: 16 исторических тестов прежней палитры падают и на исходном checkpoint; их ожидания не переписывались. Единственная дополнительная временная ошибка Windows rename прошла при повторном отдельном запуске.

**Графический Minecraft client не запускался: визуальная игровая приёмка ещё не выполнена.**

Контрольные суммы и границы проверки: `proof.json`. Полные журналы, ledger, baseline failures и проверки мира: `Grid-Physics-Reports.zip`.
