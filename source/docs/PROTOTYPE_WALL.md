# Ограда после замечаний к v7

Исправление пунктов2/8/9 текущего review: один `bloodborne_dw:prototype_wall`, временный номер типа **90002**, один основной предмет в creative. Модель и исходные материалы сохранены. Финальный dedicated запуск Attempt20 прошёл все9 wall GameTests в общей группе76 native tests; также прошли19 core checks и build. Production SHA256: `6cf52a3c91426e9ec3cf050d6949b94ebcc49e6168b6f497bb75aebacf174316`. Автор новой сцены с83 выбранными модами создал91 объект и штатно завершился с exit0; production reopen и client/resource проверка финального артефакта остаются отдельными этапами. Принятия пользователем всего набора пока нет.

В v7 четыре стороны были Boolean и имели общую высоту `course`; AUTO соединял только собственное семейство. Поэтому ограда не могла вести себя как Minecraft WallBlock и показывать LOW/TALL одновременно. Теперь блок наследует настоящий `WallBlock`, использует его правила горизонтальных соседей, покрытия сверху и стойки. Стороны `north/east/south/west` независимо принимают `none/low/tall`, центральная стойка называется `up`. Полные подходящие faces, vanilla walls, собственные стены, panes и ориентированные fence gates участвуют в штатных соединениях. Добавление/удаление соседей автоматически обновляет форму и не меняет материал/профиль.

Обычные cardinal установки используют AUTO. Сохранены восемь ориентаций: диагональная обычная поза и поворот tool на45° фиксируют выбранную форму как MANUAL. Ручная поза не перенаправляет live grid links при изменении окружающих блоков. `/bb wall connections auto` возвращает yaw0 и пересчитывает сеточные соединения. Ручные параметры остаются вариантами этого же типа, отдельные building items для них не добавлены.

BASE/ALT и material0..7 сохраняются при pick/drop/save. Оригинальные post/LOW используют исходную фиксированную текстуру; восемь TALL материалов сохраняют веса `[20,1,1,1,5,1,1,1]` и выбираются один раз при новой установке. ALT по умолчанию наследует BASE; оба профиля допускают ресурсное переопределение. Присесть+ПКМ wall tool меняет профиль, присесть+ПКМ по нижней стороне — материал, обычный ПКМ —45°. Ручное изменение требует creative либо OP2 и разрешений на участок. Обладание tool само по себе не даёт прав. Пустая рука и обычный предмет не меняют оформление. Административные `/bb wall material`, `visual`, `course`, `rotate`, `connections` требуют OP2; `course` — helper, задающий высоту присутствующим ручным сторонам, общего block-property больше нет.

Геометрия рендера осталась исходной: post `[4,0,4]..[12,16,12]`, LOW `[5,0,0]..[11,14,8]`, TALL `[5,0,0]..[11,16,8]`, единицы1/16. Импорт15 JSON и8 PNG сохраняет closure Minecraft1.18.2 и оригинального пака. Все40 primitive wrapper JSON побайтно совпадают с v7. Поворот диагонали применяется один раз в исходных JSON, затем native quarter-turn; runtime не копирует/перестраивает quad-геометрию.

Физика и selection теперь отдельно упрощены согласно пункту9. Cardinal collision — максимум5 простых прямоугольников на обычной для Minecraft wall высоте1.5. Диагональный collision — один грубый AABB внутри X/Z корневой клетки. Он может закрывать пустые визуальные углы в этой клетке; это принятая здесь coarse аппроксимация, не точный контур. Outline — один общий selection AABB с исходной художественной высотой≤1. Пустая недопустимая форма без сторон и стойки даёт пустые shapes и не устанавливается предметом. Shapes кешируются; ни helper-клеток, ни BlockEntity у ограды нет. Bounding box не резервирует соседние клетки и не удаляет постороннюю декорацию.

Опора вычисляется независимо от этих AABB по реальному верхнему contact face под центральной ножкой. Верхняя плита подходит; seating на нижней плите пока не реализован и отклоняется без расхода/изменения предмета. Занятая root-клетка и реальные пересечения с entity проверяются при установке. Удаление опоры удаляет один wall и роняет один предмет с его оформлением.

| Измерение | v7 | Исправление |
|---|---:|---:|
| Registry-типы / основные creative wall-стеки |1 /16|1 /1|
| Зарегистрированные blockstates |32768|82944|
| Максимум native decomposed collision boxes |101|5 cardinal /1 diagonal|
| Максимум outline boxes |101|1|
| Helper-клетки / BlockEntity |0 /0|0 /0|
| Shared primitive JSON / native bakes per reload |40 /160|40 /160|
| Visual appearance wrappers |4608|17152|

Рост состояний связан с независимыми трёхзначными сторонами: `3^4 ×up2 ×water2 ×material8 ×rotation8 ×profile2 ×mode2 =82944`. Physics не строится для каждой комбинации материала/воды/профиля/режима: collision использует256 geometry cache keys, outline1296. Отдельный общий `course` удалён. Новый provider не возвращается к592-condition multipart, вызвавшему OOM в v7: существующие160 immutable native bakes остаются общими, appearance wrappers выбирают≤5 частей. Water/mode и material без TALL не создают новую геометрию.

`reports/WALL_REVIEW_CAUSE_FIX_COUNTS.json` содержит воспроизводимые native `getBoundingBoxes()` гистограммы и сырые timings. На одинаковых512 геометрических рецептах сумма collision boxes снизилась15704→904. В одном процессе на том же named1.20.1, mover и max-offset, после5 warmup rounds, семь чередующихся500000-query `VoxelShapes.calculateMaxOffset` измерений дали примерно48–49ms до и21–22ms после. Это локальный микробенч движения по shapes, не FPS, heap или скорость server ticks. Raw construction timings не служат сравнительным performance-выводом: старый вариант запускается первым и включает начальную инициализацию библиотеки.

`reports/WALL_SHARED_MODEL_AUDIT.json` проверяет82944 сочетания, включая51200 mixed LOW/TALL; каждая выбранная сторона независимо сопоставлена с сохранённым исходным selector. Model ID/native rotations/UV-lock/weights/bytes совпадают, mismatch0. Доказательства v7 OOM/parent-link исправлений и старые counts сохранены в historical отчётах; прежний V7 client PASS не выдаётся за проверку новой схемы.

Девять GameTests используют настоящий BlockItem: single/line/corner/T/cross, полный сосед и vanilla wall, добавление/удаление соседей, верхний полный/частичный блок и верхняя собственная стойка, одновременные LOW/TALL, права и tool edits, pick/drop/palette NBT, восемь yaw, top-slab support, занятый root и удаление опоры. В1296 geometry recipes дополнительно проверяются реальные box counts, cache identity и отсутствие соседних helper-клеток. Actual Attempt20:9/9 PASS; XML/log hashes и отдельные testcase rows сохранены в `WALL_REVIEW_CAUSE_FIX_COUNTS.json`. Raw Attempt16/17/19 logs и XML сохранены как история.

В Attempt15 восемь wall-тестов прошли, а один ожидал LOW/TALL от неверно выбранной верхней ступени. Нативная область повышения NORTH имеет длину9/16; половина ступени глубиной8/16 не покрывает её целиком, поэтому все LOW были правильным результатом. Исправлен тестовый верхний блок: обычный vanilla polished-deepslate wall с `up=true,north=low`, подключённый к камню с северной стороны. Его native collision покрывает только NORTH probe. Это проверяет независимые LOW/TALL без изменения штатной логики блока.

Сравнение остальных composite объектов опубликовано отдельно в `COMPOSITE_V8_GEOMETRY_METRICS.json/.md`. Current Attempt20: collision proxy16.260→11.229ms, selection proxy79.304→75.615ms, за100000 прогретых запросов в одном процессе. Selection median снизилась4.7%; шесть из семи после-раундов быстрее и один медленнее. Это cache lookup+`calculateMaxOffset` на shapes, не реальный selection raycast или FPS. Текущий flat-tree outline имеет4 простых crossed bounds: узкий нижний ствол0..6blocks и широкую верхнюю крону6..18blocks. Helper-кандидаты максимум377→449 относительно v7; это возможные клетки геометрии, не установленные owned helpers.

Attempt16 с прежними двумя широкими плоскостями показал регрессию selection24.2% и helper-кандидаты593. Результат целиком сохранён в `COMPOSITE_V8_GEOMETRY_METRICS_ATTEMPT_16_HISTORICAL.json/.md`; Attempt17 и19 сохранены отдельно аналогичными historical отчётами. Сравнивать независимые процессы16/17/19/20 как контролируемый timing эксперимент нельзя. Эти composite измерения не подменяют отдельный локальный wall benchmark. Whole-set performance и пользовательское принятие не заявлены.

Full-mod автор прежнего кандидата7f70 остановился на горизонтальном стекле из-за encoded NBT snapshot mismatch. Actual Lithium0.11.2 probe нового кандидата показал, что typed NBT после copy равен, raw порядок ключей отличается, а canonical bytes и typed round trip совпадают. В том же83-mod profile и с тем же scene input новый кандидат установил все91 объекта. Доказательство и точная область прежней ошибки сохранены в `NBT_FULL_MOD_CANONICAL_RUNTIME_V8.json/.md`; это не изменение художественного материала или правил соединения ограды.

Source proof `[154,58,-976]`:

```mcfunction
setblock ~ ~ ~ bloodborne_dw:prototype_wall[connections=manual,rotation=0,north=low,east=low,south=low,west=none,up=true,material=0,profile=base,waterlogged=false]
```

Новая тестовая сцена использует обычную установку одним creative предметом на каменной платформе; не подменяет доказательство autoconnection заранее заданными setblock-формами. Исходный мир и immutable v7 не переписываются этой работой. Старые picked MANUAL items с Boolean-сторонами/post/course нормализуются при установке; автоматической миграции старых world palette состояний здесь нет.
