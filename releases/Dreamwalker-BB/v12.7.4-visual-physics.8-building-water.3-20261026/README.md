# Dreamwalker BB .8-building-water.3 — вода внутри декора

Исправлен провал поверхности воды вокруг обломков и других пересекающихся
объектов. На указанном участке `X=-18, Y=1, Z=-1092` вода уже была сохранена
в ForcedWater, но Sodium рассчитывал её высоту из исходного состояния блока,
в котором жидкость отсутствует. Поэтому соседние углы поверхности проседали.

Новый рендер учитывает ту же сохранённую воду при расчёте высот и соседних
водных граней. Исправлены Sodium и ванильный fluid renderer. В местах с декором
получается обычная высота исходного блока воды 8/9 блока, либо 1 при воде сверху.

Карта и её формат не изменены. Внешний вид декора, его коллизии, NPC, инструмент,
текстуры и native-каталог сохранены. Повторная конвертация не нужна; галерея
остаётся валидной. Вопрос подсказки «Воздух» отложен по просьбе пользователя.

## Проверка

Обычный клиент рабочей сборки, Java 17, Xmx6G, 8 чанков, открыл свежую копию
Desktop/bloodborne. Проверены реальные вызовы расчёта высоты для
**615 разных ячеек** с принудительной водой; ни одной
неверной высоты. Проверка также вызвала настоящий ванильный renderer для той же
ячейки. Снимки с исходного ракурса пользователя и сверху подтвердили ровную
поверхность вокруг сохранённых обломков. Клиент штатно сохранился и завершился.
Это проверка исправления, не сравнительный тест FPS всего города.

## Установка

JAR .3 установлен в Limacina/DreamWalker/mods, Desktop/client_mods и
Desktop/server_mods. Старые JAR .2 сохранены в
`C:/Users/vakir/Desktop/Dreamwalker-backups/mods-before-water-surface-20261026`.
Рабочая карта осталась в `C:/Users/vakir/Desktop/bloodborne` без записи.
В mods должен оставаться один Dreamwalker BB JAR.

[Совместимая карта из предыдущего полного комплекта](https://github.com/Stasik1960/Dreamwalker/releases/download/native-grid-city-water-tools-npc-20261026/bloodborne-water-tools-npc-20261026.zip).
Повторно скачивать карту для этого исправления не требуется.

Полные исходники приложены. Сборка: JDK 17, `DW/gradlew.bat --no-daemon remapJar`.

## SHA256

`dreamwalker-bb-fabric-1.20.1-0.1.0-v12.7.4-visual-physics.8-building-water.3.jar`

`94259c2c5be7822b9b972c2a2e6fee1df2a8e60b726e49fccea2dd57fb3d76c1`

`dreamwalker-water-surface-full-sources-20261026.zip`

`320a76f7348f044fa6b9c2ad642e16306df007f8dbf273b3213974c686bc8d55`

`water-surface-fixed.png`

`a2296323b25543872ba3c0add4d6354835a334c838954305622e0c0add3168de`



## Загрузка комплекта

- [dreamwalker-bb-fabric-1.20.1-0.1.0-v12.7.4-visual-physics.8-building-water.3.jar](https://github.com/Stasik1960/Dreamwalker/releases/download/native-grid-city-water-surface-20261026/dreamwalker-bb-fabric-1.20.1-0.1.0-v12.7.4-visual-physics.8-building-water.3.jar)
- [dreamwalker-water-surface-full-sources-20261026.zip](https://github.com/Stasik1960/Dreamwalker/releases/download/native-grid-city-water-surface-20261026/dreamwalker-water-surface-full-sources-20261026.zip)
- [water-surface-fixed.png](https://github.com/Stasik1960/Dreamwalker/releases/download/native-grid-city-water-surface-20261026/water-surface-fixed.png)
- [REPORT-RU.md](https://github.com/Stasik1960/Dreamwalker/releases/download/native-grid-city-water-surface-20261026/REPORT-RU.md)
- [SHA256SUMS.txt](https://github.com/Stasik1960/Dreamwalker/releases/download/native-grid-city-water-surface-20261026/SHA256SUMS.txt)

Полные актуальные исходники находятся в приложенном ZIP; исходники из корня репозитория не являются заменой этому архиву.
