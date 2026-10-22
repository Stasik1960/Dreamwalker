# Диагностика V10: фактические проверки и собственная стоимость

Текущий ordinary JAR — prototype.5/candidate9, SHA256 `ad7fb47b9f22594f05d8c9523030c2d35f13154be61ba44b1bacf964838fabe5`. [Обязательная независимая проверка](../reports/REVIEW_V10_DIAGNOSTICS_RELEASE5.json) прошла реальные dedicated OFF→ON→REENTER и MIN18→REENTER5ATT2: выключение по умолчанию, default60 auto-stop, явный stop/reenter, связанные session UUID/экспорты, реальная установка90010→server ACK→selected client model, bounded retained timings и persisted GUI-секции. [Полный клиент/Kappa](../reports/V10_CLIENT_FULL5_ATT3_ROSTER_PARTS_MIDDLE_DIAGNOSTICS_INDEPENDENT.json) и [42 фактических Iris-настройки](../reports/V10_CLIENT_FULL5_ATT3_STRICT_SHADER_INDEPENDENT.json) проверены отдельно. Это автоматическое доказательство указанной области, не ручная приёмка или завершение всего задания.

Все числа ниже извлечены воспроизводимым [наблюдателем](../tools/observe_review_v10_diagnostics_cost.py) в [неизменяемый cost JSON](../reports/V10_DIAGNOSTICS_RELEASE5_COST_OBSERVATION.json). JSON хранит exact SHA/пути исходных отчётов и ZIP, PID, размеры/лимиты кешей, очередей, выборок и source-групп. ZIP сервера и клиента связываются по session UUID; пользовательские команды и пути описаны в [инструкции](REVIEW_V10_INSTRUCTIONS.md#диагностический-zip).

## Что измерено

Dedicated OFF/ON стартовали отдельными процессами из одной pristine Author8 baseline с одинаковыми balanced operations; QA-наблюдатель включён в обоих режимах. ON реально завершился по `AUTO_DURATION_EXPIRED` через60.013s. REENTER использовал сохранённую ON-копию и остановлен оператором через8.3567s; это проверка повторной загрузки, а не второй60s performance-run. Whole-tick elapsed — время целого фактического tick, не стоимость отдельного типа.

| Dedicated workload | Tick samples | Среднее,ms | p95,ms | p99,ms |
|---|---:|---:|---:|---:|
| OFF, отдельный процесс60s | 1201 | 0.922832 | 1.2068 | 1.3790 |
| ON, auto-stop60s | 1208 | 1.078123 | 1.3023 | 7.1989 |
| REENTER, ручной stop8.3567s | 168 | 1.846781 | 5.9972 | 8.8039 |

Клиентские OFF/ON/OFF окна имеют одинаковые камеру, очищенную сцену и настройки; наблюдатель присутствует во всех окнах. Здесь приведён **presentation frame interval**, включающий VSYNC/ожидание/планирование. Это не CPU render-time, GPU-time или средний FPS, полученный простым обратным p95.

| Клиент/окно | Frames | Среднее,ms | p95,ms | p99,ms |
|---|---:|---:|---:|---:|
| MIN18 OFF_BEFORE | 594 | 8.402445 | 9.9056 | 10.4759 |
| MIN18 ON_IDENTICAL_CLEANED_SCENE | 594 | 8.405749 | 9.8115 | 10.5486 |
| MIN18 OFF_AFTER | 595 | 8.388655 | 9.6850 | 10.0446 |
| REENTER5ATT2 OFF_BEFORE | 593 | 8.426468 | 9.8615 | 10.5741 |
| REENTER5ATT2 ON_IDENTICAL_CLEANED_SCENE | 594 | 8.394380 | 9.8556 | 10.5941 |
| REENTER5ATT2 OFF_AFTER | 593 | 8.412403 | 9.9027 | 10.6534 |
| FULL5ATT3/Kappa OFF_BEFORE | 431 | 11.576769 | 16.4258 | 25.3187 |
| FULL5ATT3/Kappa ON_IDENTICAL_CLEANED_SCENE | 436 | 11.413892 | 16.2246 | 25.3091 |
| FULL5ATT3/Kappa OFF_AFTER | 440 | 11.325013 | 16.1868 | 24.5977 |

Различия этих средних описательные. Фон ОС и асинхронное состояние не контролировались полностью; причинный процент накладных расходов, процент FPS определённого блока и стоимость всех объектов из них не вычисляются.

## Собственная инструментальная работа

Dedicated ON наблюдал1685 inclusive API-hook durations: суммарно836.5804ms, максимум254.119ms. Суммарные/count/max счётчики охватывают эти1685 наблюдений; сырой bounded buffer сохранил1024, вытеснил661. p95/p99 относятся **только к retained-window**, не ко всей сессии. Hook-таймеры включают start/JFR/stop, record и metrics, могут быть вложенными/перекрываться. Поэтому это elapsed отдельных hooks, не уникальная CPU-стоимость; их нельзя складывать с whole-tick или экстраполировать на всё выполнение. Dedicated background IO CPU не измерен.

| Интегрированный клиент | Общий client/server PID | sendBatch elapsed за сессию,ms | IO thread CPU процессный накопленный,ms | server inclusive hooks sum,ms |
|---|---:|---:|---:|---:|
| MIN18 | 118036 | 18.2382 | 265.625 | 351.6129 |
| REENTER5ATT2 | 115888 | 19.9465 | 187.500 | 254.4991 |
| FULL5ATT3/Kappa | 91892 | 18.6816 | 312.500 | 858.1835 |

Каждая сессия доставила19 batches. sendBatch — измеренная часть работы, не все диагностические расходы и не CPU/GPU-time. IO-счётчик относится к процессу и может включать прежние экспорты; это не session-only delta. Server hook sums включают nested/start/JFR/stop и не являются дополнительным независимым CPU-компонентом. Model/animation/collision sections отбираются `FIRST_THEN_EVERY_32_CALLS`; ни elapsed, ни вложенные samples не означают100% покрытия всех вызовов. MIN18/REENTER local sections/raw samples сохраняются с явными drop/retained counts; wire/local views перекрываются и не складываются.

## Процессы, память, GC и source-группы

У интегрированных клиента и сервера один PID для каждой строки таблицы. Heap/GC/process CPU/shared caches нельзя складывать дважды. Dedicated ON — отдельный PID115772. Его60 retained process samples показывают heap1.352→1.442GB (`GB=10^9 bytes`); это выборки, не абсолютный пик. Последний доставленный full-client sample:5.761GB heap при6.442GB max (параметр `-Xmx6G`). Resident/RSS и GPU memory не измерены; эта цифра не доказательство всей памяти системы или максимального пика. Совместимость полного профиля при4GB этим прогоном не доказана.

JFR `jdk.GCPhasePause` служит источником фактических pause durations. В dedicated ON/REENTER не наблюдались события: статус `NOT_MEASURED_NO_SAMPLES`, а не доказанные нулевые паузы. MXBean collectionTime — накопленное время сборщиков с другими фазами, не точный stop-the-world pause-time.

При direct saved-world проверке ON→REENTER сохранены20 roots/406 typed BEs/13 RP fixtures; это размер проверенного стенда, не всего города. Последний live-index имеет12 loaded RP, поскольку remote D выгружен. Source-группы считаются отдельно:373 ledger contribution cells,19 ledger owners,344 technical helpers,21 foreign-overlay carriers,375 published snapshot cells/20 chunks;12 native roots найдены в529 известных loaded palettes без chunk forcing. Число ячеек, BE и кешей не равно числу самостоятельных типов/объектов. Последние queues показывают0 pending owner/snapshot tasks,0 lamp travel/tickets/contexts и bounded IO queue0/4; экспорт видит собственный активный IO-task1, не потерю задачи. JSON сохраняет точные остальные кеши и лимиты.

В каждом client-export есть один retained error key: не заявляется «все ресурсы без fallback». Известный90005 particle/sprite warning отличают от missing rendered face quads; artwork/model-вершины проверяются отдельно. Server/client errors и chain outcomes доступны в первичных ZIP, а отсутствие owner/error samples не расширяет scope до всех модов.

## Как понимать недоступные данные

`NOT_MEASURED` и `NOT_AVAILABLE` означают **«не измерено»**: измерение не выполнялось либо нужные данные недоступны. Это не нулевое потребление. GPU-time, точный процент FPS каждого блока, причинный overhead-процент и весь сетевой трафик не измерены. Измерены лишь явно инструментированные custom payload channels; vanilla/чужие каналы не включены автоматически. Ручная оценка графики/удобства, полный source-каталог1121 models, final numeric IDs и конвертация города остаются незавершёнными.
