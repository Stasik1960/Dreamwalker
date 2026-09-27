# REPAIR TEST 3 — окно, два каталога и клеточная проверка

Готовый комплект: [releases/repair-test-3](../../releases/repair-test-3/README-RU.md).
Ветка `repair/composite-preserving-grid`; main и полный город не изменены.
База сравнения TEST2: `f80a7fe3bd87bd47ba649997fee5ebee95ab8ba2`.
Исходный WIP сохранён checkpoint-коммитом `a43c1131fe478a337118edbef0dcbaeb459eb017`.
Окончательные требования: [CONTINUE-TEST3.md](input/CONTINUE-TEST3.md);
они отменяют прежний критерий отсутствия декоративного пересечения ставней.

## Окно

`o_shuttered_window`: один предмет, прежний root, единственный helper `root.up()`.
Во всех 16 состояниях collision — единичный AABB `[0,0,0,1,1,1]` внутри каждой
клетки. Общий outline/selection `[0,0,0,1,2,1]` не захватывает декоративные края
в соседних клетках. Задняя стена остаётся независимыми обычными блоками.

Исходные meshes, UV, масштаб и раскрытие 22.5° побайтно сохранены от TEST2.
Вертикальная нормализация вычисляет единую `deltaY=0-(rawSillMinY+offsetY)`.
Raw низ подоконника -0.875; offset кандидата уже +0.875, поэтому новая delta=0.
Python-тест независимо измеряет реальные полигоны во всех позах: итоговый низ y=0.
Горизонтальный offset `-.75*facing`, anchor и pivot не меняются. Допустимый
декоративный клиппинг открытых ставней не является физическим конфликтом.

Реальные BlockItem-действия проверены в обоих порядках для lime_wool,
stone_bricks, oak_planks, glass × четыре направления × BASE/ALT × closed/open.
Проверяются снятие/возврат двух backing cells, сохранность бокового стекла и
нижней опоры, повторная установка, удаление через root/helper и NBT read/write.
Общие runtime, shared ownership, ограда и конвертер не изменены.

## Два каталога

| Вкладка | BlockItem IDs | Художественные записи |
| --- | ---: | ---: |
| Основная: production manifest + canonical wall | 50 | 139 |
| Исторические и технические | 3777 | 46530 |
| Объединение | 3827 | 46669 |

Основная содержит ровно 49 production IDs и `building_stone_brick_wall`.
Техническая: 863 целых owner IDs / 863 записи; 49 native carriers / 49 записей;
2865 city IDs / 45618 записей. Это не 3827 полноценных строительных типов.
Aliases доступны в технической; `architecture_part` остаётся служебным без
BlockItem. Художественные variant и BASE/ALT перечисляются, facing/open/lit/
connection/root_anchor не размножают каталог. Существующая принудительная
политика `o_c654_a: source_depth → surface` сохранена.

Фактическое содержимое обеих зарегистрированных вкладок сравнивается с registry,
StateManager и строительным manifest. Пересечений и пропусков нет. Проверена
установка выбранных художественных вариантов. Item preview использует готовые
baked models; отдельный bootstrap-тест проверяет реальный обработчик выбора.

## Общая клеточная проверка

Расширены существующие `sync_reviewed_geometry.py` и `normalize_support_contracts.py`.
Один splitter проверяет явную маску до разбиения; каждый AABB делится только
по разрешённым клеткам с сохранением объёма. Перекрывающиеся исходные AABB
сохраняют объединение. Положительные объёмы снаружи, неизвестные маски и
защищённые состояния не исправляются автоматически. Пустая collision не
удаляет клетки. Identity, состав, selection и artwork не задают новые клетки.

Охват: 49 logical families / 1788 states; 3778 city IDs / 52212 states,
28866 общих profiles и 3580 inline states. Все 54000 состояний проверены
структурно; это проверка ресурсов, не исторического состава целого города.
Ошибок 0; текущие формы уже cell-local, дополнительных изменений 0.
Отдельная разрешённая правка окна затронула 16 состояний.

Консервативно защищены 48 остальных logical families / 1772 states и
67 IDs ограды/aliases / 392 states. Для оставшихся 3711 city IDs / 51820 states
нет независимого разрешения ремонтировать физическую маску: они проверены
структурно и оставлены неизменными. Точные ID, state keys и причины:
[collision-grid-report.json.gz](collision-grid-report.json.gz);
краткие показатели: [collision-grid-summary.json](collision-grid-summary.json).
Синтетические тесты подтверждают lossless split, overlapping union, отказ
для внешнего положительного sliver, protected/unproven masks и сохранение
пустого helper. Новых runtime-контрактов, фрагментов и Block/Item IDs не создано.

## Маленький мир и фактические проверки

Пять прежних исходных образцов (C001/C474/C618/ограда/shared) и 24 оконные
позиции сохранены. Входной MODDED ZIP не изменялся. 5 ограниченных транзакций,
47 проверенных helper-привязок, 0 orphan и неизвестных IDs. Повторный проход:
0 изменений, файлы побайтно совпадают. Полный город не конвертировался.

Targeted Gradle run — PASS, 62.0 сек.; затем один полный
`check build logicalGameTest` — PASS, 389.9 сек.
228 Python unittest cases / 43 suites; Java/bootstrap
проверки; 56/56 dedicated-server GameTests. Сохранены C001/C474/C618,
лестницы, двери, ограда и shared-ownership regressions. Сервер завершён.
Упакованные runtime-ресурсы сверены побайтно с исходниками; исходный artwork
окна и city geometry/definitions совпадают с TEST2. Независимый
[read-only review](INDEPENDENT-REVIEW.md) не нашёл блокеров.

Команды (Windows, JAVA_HOME указывает на JDK 17):

```powershell
.\gradlew.bat --offline --no-daemon --console=plain -I tools/verification-direct-resources.init.gradle "-PbloodbornePython=<Python с numpy/Pillow>" "-Dorg.gradle.java.installations.paths=<JDK17>" test_window_test3 test_collision_grid logicalContractV2Check geometryCheck itemModelSelectionCheck compileGametestJava
.\gradlew.bat --offline --no-daemon --console=plain -I tools/verification-direct-resources.init.gradle "-PbloodbornePython=<Python с numpy/Pillow>" "-Dorg.gradle.java.installations.paths=<JDK17>" check build logicalGameTest
python -B -X utf8 tools/build_repair_test_kit.py build/repair-test3-final-20260927 --edition 3
```

Точные пути, результаты и параметры: [verification.json](verification.json).
Лог: [check-build-gametest.log](check-build-gametest.log).
Графический клиент, GUI вкладок и полный перезапуск приложения НЕ выполнялись.
NBT encode/decode не заменяет проверку реального restart. Для пользовательской
приёмки доступны JAR, ZIP мира, README и SHA256. Предыдущие файлы кандидата
сохранены в `build/repair-test3-candidate/checkpoint-a43c1131-artifacts/`;
его прежние распакованные saves не перезаписывались.

## Большой город: старые метрики НЕ пересчитаны

Сохранённые 6518 protected failures; 355/3238 прошедших atomic groups
(остаток 2883); более ранние 933 неподтверждённых memberships относятся к
разным пересекающимся сущностям. Это не число уникальных сломанных моделей.
TEST3 не является исправлением всей карты; прежний whole-world status FAIL
остаётся историческим. После пользовательской приёмки — копия последнего
MODDED-бэкапа, существующий атомарный конвертер, один свежий полный проход
и разбор остатка. Этот этап сейчас не выполнялся.
