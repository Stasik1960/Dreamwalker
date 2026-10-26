# Dreamwalker BB — вода, инструменты и звуки NPC, 26.10.2026

Готовая карта оставлена в `C:/Users/vakir/Desktop/bloodborne`.
Нужен **visual-physics.8-building-water.2.jar на клиенте и сервере**, один
Dreamwalker BB JAR в каждой папке mods. Основа — прежний комплект NPC8 и
building-water.1; весь город заново не конвертировался.

## Что изменено

- Над зелёным полом размещена вода в **3435 ячейках**: 2403 новых обычных
  minecraft:water, 990 пересечений с сохранённым декором, 42 уже водных ячейки.
  Найдены все варианты семейства из примера: fallback 117–125 и соответствующие
  native-модели. Старое затопление плит 90899 сохранено.
- Пересечения хранят воду отдельно от неизменённых блоков в необязательном
  массиве ForcedWater существующих данных чанка. Новые BlockEntity, модели,
  визуальные индексы и копии текстур не создавались. Эта вода в занятой ячейке
  сохранена статически; это целевое затопление, а не новый универсальный
  редактор жидкостей. В пустоте используется обычная ванильная вода.
- Выбор фей и тьмы исправлен: выбранный ранее NPC и пересекающаяся архитектура
  больше не перехватывают действие. Переключение сообщает «Коллизии включены»
  или «Коллизии отключены» после успешного изменения.
- Режимы инструмента разделены: «Особое действие: блоки» и
  «Особое действие: NPC». Старые номера режимов 0–5 сохранены, NPC добавлен
  под номером 6.
- У RP-мобов удалены события звуков пространства minecraft, включая
  стандартные зомби. Собственные звуки сохранены. Их idle воспроизводится
  один раз на шесть попыток вместо одной на две — втрое реже.
- Восстановлены **9354 ячейки пола на Y=-64** из карты до переноса здания.
  Удалены десять групп обычных деревьев с листвой и четыре сухих модельных
  дерева: 265 объектов-владельцев, плюс 2001 обычная ячейка листвы/деревьев.
  Здание, ограда, прочий декор и чужие владельцы коллизий сохранены.

## Проверки

- Сервер выполнил изменения и штатно сохранил карту. Клиент рабочей сборки,
  Xmx6G и дальность 8 чанков, открыл её и прошёл проверки всех 3435 водных ячеек,
  инструмента, коллизий и звуков. Sparse-вода дошла до Sodium fluid renderer
  в 667 наблюдавшихся ячейках.
- После обнаружения на снимке четырёх оставшихся сухих деревьев удалены их
  61 составная часть; выполнены отдельное сохранение и конечный просмотр
  только площадки. Проверки воды/звуков по кругу не повторялись.
- Расчёт света новых водных участков сохранён из проверенного запуска
  для 65 чанков при полном совпадении состояний блоков. Игровые тестовые
  объекты и персональные настройки QA в рабочую карту не переносились.
- Сравнение 39 485 чанков проверило границы изменений, сохранение чужих
  владельцев и их данных. Модели, текстуры и native-каталог JAR побайтно
  прежние. Новая вода добавляет обычную нагрузку от прозрачной поверхности;
  сравнительный тест FPS всего города не проводился.
- Контрольный запуск и конечный просмотр завершились без ошибок наших
  изменений. Предел проверки — фактически установленная рабочая сборка;
  это не гарантия совместимости с любыми другими модами.

## Установка и резервная копия

Карта: `C:\Users\vakir\Desktop\bloodborne`.
Полная резервная копия перед заменой: `C:\Users\vakir\Desktop\Dreamwalker-backups\bloodborne-before-water-tools-npc-20261026`.
Новый JAR установлен в Limacina/DreamWalker/mods, Desktop/client_mods и
Desktop/server_mods. Старые JAR сохранены отдельно рядом с резервной картой.

Архив карты содержит папку bloodborne. В публичный архив не включены личные
playerdata, статистика, достижения, переписка и Player из level.dat;
локальная карта и резервная копия сохраняют их. Полные актуальные исходники
находятся в приложенном архиве, а не в старом корне GitHub-репозитория.
Для сборки: JDK 17, `DW/gradlew.bat --no-daemon remapJar`.

## SHA256

`dreamwalker-bb-fabric-1.20.1-0.1.0-v12.7.4-visual-physics.8-building-water.2.jar`

`d8bdf27d58a8281f3d632a4cfccda0e7615baf69dee980579596167d2c243c51`

`bloodborne-water-tools-npc-20261026.zip`

`780742d2c87fd2666f72291e2d94f82f9d9e1c8543c4df862905c0843db575fc`

`dreamwalker-water-tools-npc-full-sources-20261026.zip`

`a171e37abedf3f08ab507417bcbf3b09393c2ca7bbd15852085986c94fb3569f`

`green-floor-water.png`

`57a5374902e07dfa9b2d0c13bcd95bc8f1e967157fa75f00e374d70ff2dc1db4`

`platform-without-trees.png`

`5f3eb62e5f9f8f793d7bd2482b0866e12bae7c36bbfbc784bbe1981277d9e7cb`



## Загрузка комплекта

- [dreamwalker-bb-fabric-1.20.1-0.1.0-v12.7.4-visual-physics.8-building-water.2.jar](https://github.com/Stasik1960/Dreamwalker/releases/download/native-grid-city-water-tools-npc-20261026/dreamwalker-bb-fabric-1.20.1-0.1.0-v12.7.4-visual-physics.8-building-water.2.jar)
- [bloodborne-water-tools-npc-20261026.zip](https://github.com/Stasik1960/Dreamwalker/releases/download/native-grid-city-water-tools-npc-20261026/bloodborne-water-tools-npc-20261026.zip)
- [dreamwalker-water-tools-npc-full-sources-20261026.zip](https://github.com/Stasik1960/Dreamwalker/releases/download/native-grid-city-water-tools-npc-20261026/dreamwalker-water-tools-npc-full-sources-20261026.zip)
- [green-floor-water.png](https://github.com/Stasik1960/Dreamwalker/releases/download/native-grid-city-water-tools-npc-20261026/green-floor-water.png)
- [platform-without-trees.png](https://github.com/Stasik1960/Dreamwalker/releases/download/native-grid-city-water-tools-npc-20261026/platform-without-trees.png)
- [REPORT-RU.md](https://github.com/Stasik1960/Dreamwalker/releases/download/native-grid-city-water-tools-npc-20261026/REPORT-RU.md)
- [SHA256SUMS.txt](https://github.com/Stasik1960/Dreamwalker/releases/download/native-grid-city-water-tools-npc-20261026/SHA256SUMS.txt)

Полные актуальные исходники находятся в приложенном ZIP; исходники из корня репозитория не являются заменой этому архиву.
