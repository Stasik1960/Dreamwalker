# Dreamwalker BB — восстановленное здание и вода, 26.10.2026

Готовая карта оставлена в `C:/Users/vakir/Desktop/bloodborne`.
Платформа со зданием и деревьями перенесена из `ether.zip / eh_s2:yharnam`
со смещением `(+554, -8, -793)` в район **X −130, Z −900**.
Очищены её точный контур и зазор 2 блока. Весь город заново не конвертировался.

Для запуска нужен приложенный **visual-physics.8-building-water.1.jar**
на клиенте и сервере: он включает готовую версию с NPC. Старые `.7` и `.8`
без расширения для этого комплекта карты не подходят. В `mods` должен
оставаться один Dreamwalker BB JAR; остальные моды рабочей сборки сохраняются.

## Результат

- Записано 731,934 обычных блочных изменений и установлено 19,848 объектов постройки; проверены все владельцы и смещения.
- Вода присутствует во всех 5037 ячейках плит 90899: 4602 обычных и 435 составных. Из обычных одна плита двойная; вода хранится внутри её полного объёма, без уменьшения геометрии.
- Мокрые составные состояния повторяют прежние меши, коллизии, оптику и соседний декор. Добавлено 322 визуальных и 33 физических мокрых варианта, без индивидуальных BlockEntity.
- Ещё 18 невидимых конечных состояний сохраняют половинчатые опоры и полностью прозрачные панели исходного ресурс-пака. Это служебные состояния, а не новые модели города.
- NPC, их классы, текстуры, модели и анимации побайтно совпадают с опубликованной `.8`. Исходные текстуры и рендер не переделывались.

## Проверки

- Мир сохранён сервером и повторно открыт обычным клиентом с контрольным модулем, Xmx6G, дистанция 8 чанков.
- Все 5037 ячеек содержат Minecraft water; у всех мокрых native-ячеек коллизии совпали с фактическим сухим представлением.
- Сохранены 115 контрольных владельцев разных семейств; проверены 18 классов невидимых коллизий относительно ванильных плит и панелей.
- Структурное сравнение 39 485 чанков не нашло изменений блоков/визуальных индексов вне участка и затопляемых плит. Обновление SHA заголовков native-каталога ожидаемо.
- Четыре финальных снимка проверены. Счётчик FPS при захвате: [28, 29, 30, 30] при ограничении 30 FPS; это контрольный просмотр, не сравнительный тест производительности всего города.
- Полный архив карты и архив исходников проверены на CRC; SHA256 файлов приведены ниже и в SHA256SUMS.txt.

## Резервная копия и установка

Полная прежняя карта: `C:\Users\vakir\Desktop\Dreamwalker-backups\bloodborne-before-building-water-20261026`.
Совместимый JAR установлен в фактически используемую папку Limacina/DreamWalker/mods,
а также Desktop/client_mods и Desktop/server_mods; прежние JAR сохранены отдельно.
Из публичного ZIP исключены личные playerdata, статистика/достижения, чат и
личный Player из level.dat. Локальная карта и её резервная копия сохраняют эти данные.
Архив содержит папку bloodborne, которую можно распаковать в saves или назначить
серверным миром. Внешний ресурс-пак для этих модовых блоков не требуется.
В моде изменены только два класса совместимости native-каталога и конечные
данные/ресурсы дополнительного физического банка; исходные индексы сохранены.

База с NPC: https://github.com/Stasik1960/Dreamwalker/releases/tag/native-grid-city-npc-20261026

## SHA256

`dreamwalker-bb-fabric-1.20.1-0.1.0-v12.7.4-visual-physics.8-building-water.1.jar`

`c7682a615921ff73b3baa5d516feb8832ded87a2cbace704bce2242b98ea256b`

`bloodborne-building-water-20261026.zip`

`70a48b27e406ee805da7e11f21633df65f0b3a7f72422f78c4fe81494845fd5b`

`dreamwalker-building-water-full-sources-20261026.zip`

`729aa669b9975de610dda8b02579a00bd138213761138d7a8857f83ddd8316c8`

`building-restored-20261026.png`

`90b0cc73e226bf7049da46b903de82e35a028ccc71d76ff360e46ab04d7c780e`



## Загрузка комплекта

- [dreamwalker-bb-fabric-1.20.1-0.1.0-v12.7.4-visual-physics.8-building-water.1.jar](https://github.com/Stasik1960/Dreamwalker/releases/download/native-grid-city-building-water-20261026/dreamwalker-bb-fabric-1.20.1-0.1.0-v12.7.4-visual-physics.8-building-water.1.jar)
- [bloodborne-building-water-20261026.zip](https://github.com/Stasik1960/Dreamwalker/releases/download/native-grid-city-building-water-20261026/bloodborne-building-water-20261026.zip)
- [dreamwalker-building-water-full-sources-20261026.zip](https://github.com/Stasik1960/Dreamwalker/releases/download/native-grid-city-building-water-20261026/dreamwalker-building-water-full-sources-20261026.zip)
- [building-restored-20261026.png](https://github.com/Stasik1960/Dreamwalker/releases/download/native-grid-city-building-water-20261026/building-restored-20261026.png)
- [REPORT-RU.md](https://github.com/Stasik1960/Dreamwalker/releases/download/native-grid-city-building-water-20261026/REPORT-RU.md)
- [SHA256SUMS.txt](https://github.com/Stasik1960/Dreamwalker/releases/download/native-grid-city-building-water-20261026/SHA256SUMS.txt)

Полные актуальные исходники находятся в приложенном ZIP; исходники из корня репозитория не являются заменой этому архиву.
