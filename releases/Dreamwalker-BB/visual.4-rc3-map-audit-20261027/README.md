# Dreamwalker visual.4-rc3: мод, актуальная карта и аудит

Проверенная установленная ревизия `0.1.0-v12.7.4-visual-physics.8-building-water.3-stutter.1-visual.4-rc3` для Minecraft1.20.1/Fabric.

В комплекте свежий согласованный снимок Desktop/bloodborne от `2026-10-27T17:42:26.228986+00:00`, включая побочное измерение `bloodborne_dw:bloodborne_old` и datapack переходов. Архив содержит папку `bloodborne`; распакуйте её в saves. Команды перехода: `/function bloodborne_old:enter` и `/function bloodborne_old:return`.

Исправлены пустые формы выделения примерно 203 тыс. городских фрагментов, запись модели при no-collide, наложение UV в 88 размещениях и ошибочное отсечение грани в 9 размещениях. Принятая оптимизация, каталог, физические коллизии и код NPC сохранены. Новая конвертация не требуется.

Реальные проверки:24 одинаковых ракурса на 12 чанках; 6 обычных и 4 составных объекта; 6 отказов размещения; сохранение и повторное открытие. p95 кадра 57.5→55.7 мс,p99 72.2→66.8 мс. Один парный прогон не доказывает прирост. Существующая пауза FullGC около 1.4 с остаётся в обеих версиях.

Все окна и угловые тени исправленными не объявляются. В полном аудите сохранены исходные результаты, ограничения и неудачные QA-попытки. Исторические замеры выделены отдельно.

## Скачать

- [dreamwalker-bb-fabric-1.20.1-0.1.0-v12.7.4-visual-physics.8-building-water.3-stutter.1-visual.4-rc3.jar](https://github.com/Stasik1960/Dreamwalker/releases/download/dreamwalker-visual4-rc3-map-audit-20261027/dreamwalker-bb-fabric-1.20.1-0.1.0-v12.7.4-visual-physics.8-building-water.3-stutter.1-visual.4-rc3.jar)
- [bloodborne-with-bloodborne_old-20261027.zip](https://github.com/Stasik1960/Dreamwalker/releases/download/dreamwalker-visual4-rc3-map-audit-20261027/bloodborne-with-bloodborne_old-20261027.zip)
- [dreamwalker-visual4-audit-and-patch-sources-20261027.zip](https://github.com/Stasik1960/Dreamwalker/releases/download/dreamwalker-visual4-rc3-map-audit-20261027/dreamwalker-visual4-audit-and-patch-sources-20261027.zip)
- [REPORT-RU.txt](https://github.com/Stasik1960/Dreamwalker/releases/download/dreamwalker-visual4-rc3-map-audit-20261027/REPORT-RU.txt)
- [manifest.json](https://github.com/Stasik1960/Dreamwalker/releases/download/dreamwalker-visual4-rc3-map-audit-20261027/manifest.json)
- [SHA256SUMS.txt](https://github.com/Stasik1960/Dreamwalker/releases/download/dreamwalker-visual4-rc3-map-audit-20261027/SHA256SUMS.txt)

В mods должна быть одна версия Dreamwalker. Установите JAR из этого комплекта. Исходники исправлений и QA входят в архив аудита; это не полный автономный проект сборки. Старые исходники корня репозитория не являются актуальной заменой приложенному JAR. Контрольные суммы приведены в SHA256SUMS.txt.
