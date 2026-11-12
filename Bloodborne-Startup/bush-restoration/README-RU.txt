Восстановление кустов на карте Bloodborne v22 Compact Startup

Готовые JAR, карта и индекс координат опубликованы в релизе v22-bloodborne-bush-restoration.1.
Используйте JAR и карту из этого комплекта вместе. Распакуйте папку bloodborne
из архива карты в saves. Для сервера задайте level-name=bloodborne.
Версия игры и зависимости соответствуют v22 Compact Startup, Fabric 1.20.1.

Основная карта сопоставлена с bloodborne-blocks-fixed-2 из контрольной точки
native-grid-city-pre-map-20261024. Совпадения проверяются по исходной модели,
смещению, положению и UV каждого полигона. В обоих измерениях также проверяются
полные отпечатки моделей по повторяющимся совпадениям из основной карты.
Для совмещённых фрагментов каталог получает дополнительные варианты. Они
сохраняют исходную физику и каждый полигон, который не принадлежит кусту.
Если на исходной координате требуется сохранить чужой блок, запись куста
может стоять в соседней клетке. SourceShift сохраняет положение самой модели.
В отчёте релиза указаны обе координаты и точные изменения каталога.

Повторная сборка в PowerShell с Python 3.12+

1. Создайте рабочую папку вне дерева исходников и установите переменную
   $env:DREAMWALKER_BUSH_WORKDIR в её абсолютный путь.
2. Скачайте DreamWalker-v22-Seam-Repair-Compact-Startup.jar и
   Bloodborne-v22-Compact-Startup-world.zip из релиза
   v22-bloodborne-seam-repair-compact-startup.1. Положите JAR в рабочую папку,
   а мир распакуйте в worlds/compact-startup/bloodborne внутри неё.
3. Положите pre-map-manifest.json из native-grid-city-pre-map-20261024 и
   resume-data-003.zip из native-grid-city-resume-20261022 в рабочую папку.
4. Из папки Bloodborne-Startup/bush-restoration выполните команды по порядку.

   python restore_pre_conversion.py
   python audit_grass.py
   python plan_bush_repair.py
   python export_old_dimension_visuals.py
   python bush_stencils.py
   python complete_bush_repair.py
   python verify_bush_release.py

5. Постройте индекс и добавьте данные из исправленного мира.

   Push-Location $env:DREAMWALKER_BUSH_WORKDIR
   python <путь-к-скриптам>/build_coordinate_index.py Bloodborne-block-coordinates.sqlite pre_conversion=worlds/pre-conversion/bloodborne-blocks-fixed-2 compact_startup=worlds/compact-startup/bloodborne
   python <путь-к-скриптам>/package_coordinate_release.py
   Pop-Location

Команды поиска находятся в файле Координаты.txt в архиве индекса.
