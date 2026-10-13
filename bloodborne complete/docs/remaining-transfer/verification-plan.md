# Проверки оставшегося переноса после V12

База: `8b770a03de21554a21c91700a1c1c6fe86c894c4`, текущая V12.
Рабочее хранилище — C:, миграция на E: остаётся на паузе. Перед работой
прочитаны `storage-mode.json`, `C-return-result.txt`; `Guard.ps1 -Mode Check`
завершён успешно. Все процессы проекта запускаются через `Guard.ps1 -Mode Operation`.

Исходную карту, её производные галереи и предыдущие выпуски не изменять.
Игровой стенд этого этапа — новый плоский мир под `bloodborne complete/build/`.
Не использовать `--gallery-input`, `--world-copy` исходной карты или генератор галереи.

## Авторские коллизии

Статус входа: **NOT_PROVIDED**. Полный поиск обычных файлов рабочего дерева,
включая ignored `build/`, не нашёл `dreamwalker-collisions.json` или готового
replacement-проекта. Два предоставленных ZIP в `Dreamwalker-Collision-Studio/`
также не содержат `dreamwalker-collisions.json`: архив редактора — приложение
с исходными моделями; ModelPacks — отдельный загрузчик с исходниками.
Проверены только данные и имена записей архивов; вложенные JS/JAR не запускались.

V12 `CollisionPack` читает проект, `CollisionReplacements` хранит его, но
действующая физика provider не использует. В этом этапе без реального экспорта
consumer не подключается; применение авторских коллизий не заявляется.
Новые физические формы должны быть описаны как формы нового переноса, а не как
восстановленная авторская разметка Collision Studio.

Для будущего экспорта сохраняется точный контракт
[COLLISION_IMPORT_HANDOFF.md](../COLLISION_IMPORT_HANDOFF.md): registry ID и полный
typed selector, `unitsPerBlock=16`, ZYX, `players_only`, частичная замена только
заданных состояний, различие отсутствующей записи и явного пустого состояния,
отдельные сидения, учёт instance transforms без повторного отражения X.
Наклонную OBB нельзя заменять увеличенным сплошным AABB. Физику NPC, выбор,
поддержку и размещение блоков нельзя менять player-only данными.

## Сохраняемые исключения и исправления

| Объект/механизм | Сохраняемый контракт |
| --- | --- |
| 90008/90009, `wall_builder` | Старый редактор, GUI, undo, recorder и редакторские пакеты остаются удалёнными; PNG сохранён. |
| 90013–90017 | Retired номера остаются зарезервированными; исключённые варианты ограды не возвращаются. |
| 90018/90019 | Скрытые лестничные aliases сохраняются для чтения/миграции старых данных в 90006; не предлагаются как новые объекты. |
| Угловая 90006 | Пустая физическая коллизия во всех контекстах и у старых contributors; видимый outline и обычное разрушение сохраняются. |
| Прямая 90006 | Существующая физика сохраняется. Прежнее требование подъёма угловой отменено. |
| 90003 `prototype_roof` | Корневая ячейка занята; обычные блоки разрешены в остальной выступающей геометрии. |
| 90002/90012 | Обычная ограда; отдельный диагональный столб не соединяется с wall/fence/pane/gate. |
| 90020 | Историческая третья визуальная модель и UV сохраняются. |
| RP 91013/91030 | Установка по нижней точке источника, сохранение Pos/UUID/offset без повторного смещения. |
| RP выбор/анимации | Обычная дальность, narrow-phase частей, препятствия и блокировка повторного перехода во время анимации. |
| V12 | Registry IDs, нумерация, NBT, RP, bb_tool, ALL-связки, lamp-пары, команды и счётчики сохраняются. |

Обнаруженные старые дефекты фиксируются отдельно; исправление в этом этапе не включается.

## Доступная проверочная инфраструктура

Java 17 существует по пути
`C:\Users\vakir\AppData\Roaming\.minecraft\runtime\java-runtime-gamma\windows\java-runtime-gamma`.
Python — `C:\Users\vakir\miniconda3\python.exe`. Точные Minecraft 1.20.1,
Fabric Loader 0.16.10, Fabric API 0.92.9+1.20.1 и GeckoLib 4.4.9 находятся в
локальных проверенных кешах. Предыдущее явное `eula=true` — `build/gametest-v12/eula.txt`.
На момент аудита работающих Java/Javaw клиентов/серверов нет.

`tools/run_v11_server.py --jar <new-jar> --accepted-eula-file build/gametest-v12/eula.txt
--run-name remaining-transfer-<unique> --report build/remaining-transfer/server.json
--hold-seconds 3600 --commands-file <setup.json>` создаёт свежий плоский мир,
доступный только на localhost, с новым свободным портом. Путь и порт записываются
в отчёт. Стенд не копирует карту; setup-команды ставят только новую тестовую сцену.
Mailbox `qa-live-commands.json` принимает ограниченный JSON-массив консольных
команд и сохраняет каждый фактически отправленный batch. Для штатного stop
создаётся `qa-normal-stop.request`; сначала выполнить `save-all flush`.

`tools/run_v11_client.py --jar <new-jar> --server 127.0.0.1 --port <report-port>
--username dwqa_new_a --run-name remaining-transfer-a-<unique>
--report build/remaining-transfer/client-a.json --qa-keybindings --launch`
запускает настоящий клиент с отдельным offline UUID, изолированными `mods/`
и `options.txt`, без изменения учётной записи и миров пользователя. Второй клиент
получает другое имя/run-name. `F8` = use, `F9` = attack, стрелки = движение,
`F10` = jump только в этих тестовых options. Проверки нативного input выполняет root.

Существующий отдельный `dreamwalker_gallery_author` JAR может работать только как
клиентский ScreenshotRecorder capture-addon: без server author marker он галерею
не создаёт. Его кадры требуют SHA-bound `client-capture-input.json`, передают только
camera/tp и сохраняют реальные framebuffer PNG. Это доказательство отображения,
но не установки, кликов или успешного взаимодействия.

Для этого этапа добавлен отдельный client-only QA addon `src/remainingqa`, который
не входит в production sources. Явная сборка:
`gradlew.bat -I tools/remaining-qa.init.gradle remapRemainingQaJar` через Guard.
Клиентский подготовщик принимает его через `--extra-mod build/libs/dreamwalker-remaining-transfer-qa-<version>.jar`.
После входа в тестовый мир addon пишет `remaining-transfer-client-qa.json`: каждый
`source_` part model должен существовать и быть успешно запечён, иметь quads и
не содержать sprite `minecraft:missingno`; каждый предлагаемый variant должен
иметь русский label и зарегистрированный builtin composite item renderer.
Такой PASS подтверждает ресурсную доступность/тип значка, но не ручную оценку
внешнего вида каждого объекта и не действие настоящего клиента.

Необязательный `remaining-transfer-qa-input.json` содержит точный
`productionJarSha256` и `frames`: `{fileName, camera:[x,y,z,yaw,pitch],
command:"tp @s x y z yaw pitch", waitTicks:40}`. Addon принимает только ограниченные
camera-команды и сохраняет реальные native PNG/`remaining-transfer-capture.json`;
он не ставит объекты, не нажимает предметы и не создаёт галерею.

## Матрица проверки новой реализации

| Проверка | Доказательство | Исходный статус |
| --- | --- | --- |
| Зависимости моделей/текстур, vanilla namespace | Полный обход parent/texture ссылок, entry/hash audit combined JAR; исходные vanilla assets не подменены | PASS artifact/preservation |
| Каталог и предметы | Каждый новый ID/variant предложен в Creative с русским именем и реальным item model; клиентский baked model/кадры | PASS server и native baked icons; кадры ожидаются |
| Установка одним предметом | Обычный registered `useOnBlock`, один owner, все восемь yaw и существенные варианты | PASS 1805×8 server; PASS native15 representatives, first4×8 |
| Копирование | pick-stack переносит variant/profile, новое размещение создаёт другой UUID без копии owner/links | PASS server и native15 doItemPick/copy |
| Соседи | Добавление/удаление vanilla-соседей сохраняет ID/state/owner нового объекта | PASS server |
| Двери/люки | Реальный production callback выбора/ПКМ открывает/закрывает; typed mechanism bridge проверяет точный UUID; pick переносит open art без связи со старым instance | PASS server и native openable representative |
| Коллизии/занятые ячейки/выбор | Проверка фактических source/state форм и contributions; нативный подход/outline/обычное разрушение | PASS server/default union; NOT_RUN native; author export NOT_PROVIDED |
| Удаление | Все contributions только удаляемого owner очищены; независимые блоки/объекты остаются | PASS server/native15; removed copy roots absent in save |
| Сохранение | Typed NBT round-trip, `save-all flush`, штатный stop, fresh-copy rejoin нового тестового мира | PASS typed NBT и actual client rejoin15; strict external-auth log FAIL сохранён |
| Регрессия V12 | Существующие GameTests; сравнение старых resources/model/textures/ID/state | PASS прежние 58 GameTest и preservation |
| Combined JAR | `build`/`remapJar`, checksum и точный набор dependencies; одинаковый JAR клиента/сервера | PASS build/artifact и localhost offline runtime |

Новые focused GameTests дополняют существующий серверный набор. Успешные mock/server
проверки не переименовываются в native client PASS. Результаты фиксируются отдельно
как PASS, FAIL или NOT_RUN с точным JAR SHA; изменение JAR инвалидирует относящиеся
к нему изменённые игровые случаи. Старые 58 GameTest/21 core/22 native V12 относятся
только к прежнему production SHA и не являются приёмкой нового переноса.

Пять focused GameTests распределяют случаи по server ticks (не более одного
case за tick), чтобы полный набор source-объектов не блокировал один tick тысячами
транзакций. При проверке соседей не заменяются собственные helper-ячейки объекта
и обязательная опора; такие операции являлись бы его намеренным разрушением.
Чистый neighbor callback проверяется во всех шести направлениях, а независимый
vanilla-сосед после записи возвращается к прежнему состоянию. Проверка новых
номеров допускает 90021–90999 и 93001+, оставляя RP диапазон 91000–92999 и
все прежние/retired номера зарезервированными; общий DebugCatalogue требует
уникальности всех номеров при старте.

Таблица кандидатов отдельно содержит записи `SOURCE_DEFECT`, включая
неразрешённые `#missing` texture aliases и отсутствующие текстуры. Такие входные
дефекты не исправляются догадкой и не превращаются в молчаливое исключение.
Клиентский audit остаётся строгим для каждого фактически импортированного
законченного part model: если unresolved alias попал в новый JAR, missingno sprite
приведёт к FAIL. Заблокированные исходные записи остаются в таблице соответствий.

Подтверждённый промежуточный результат для production JAR final5
`dc0e2c0f430119f83dc45fa94aa4f3bfbeea6295eef8d103b1cc739d96cfb7e2`:
сборка PASS, независимый `artifact-validation.json` PASS (1047 типов,
1805 предложенных вариантов, все исходные geometry/UV/PNG после разрешённого
namespace remap, 328 UV-lock wrappers и 8 явно объявленных emissive overlays),
`preservation-results.json` PASS. Серверный final5 GameTest PASS 63/63: прежние
58 и пять focused tests, включая фактическую установку каждого нового варианта
во всех восьми yaw, копирование, соседей, удаление, typed NBT и новый openable
instance bridge. Успех GameTest не подтверждает пользовательский ввод клиента.

Нативный addon разделяет три независимых отчёта: baked resources/items
`remaining-transfer-client-qa.json`, opt-in реальные interactionManager packets
и vanilla `doItemPick` в `remaining-transfer-client-network.json`, и read-only
реальный повторный вход после рестарта в `remaining-transfer-client-rejoin.json`.
Режим `VERIFY_REJOIN` получает exact ID/variant/rotation/open/UUID из первого
сетевого PASS, отправляет только camera `tp` и читает synced server state.
Обычный interaction marker при rejoin не повторяется. Кадры записываются
отдельно в `remaining-transfer-capture.json`; CAPTURE_COMPLETE требует ручной
оценки изображения и не присваивает ей PASS. Эти клиентские проверки пока
NOT_RUN для данного конечного SHA. Физический F8/мышь/F9 остаётся NOT_RUN:
автоматический Windows capture дважды завершился FrameArrived/timeout и не
даёт доказательства физического ввода.

После реального client bake обнаружена и исправлена только новая source tint
зависимость: исходная `assets/minecraft/textures/colormap/grass.png` теперь хранится
в private namespace и применяется только к точным новым ID `source_grass_block`,
`source_bricks_grass_block_0`, `source_potted_fern`. Vanilla и старые объекты
сохраняют прежний вид. Независимый artifact validator PASS для final6 JAR
`9797fb4bb4b6c4103b60b994fb0c4ad4f88c46bc7a19de42eb596e7f0c57e117`,
включая 237 exact-source PNG и SHA авторской палитры
`d34580a3adbdce78a83b2c77a9d2a590cfa28ba2c96a4407d99ff402b44f29c9`.
Нативный RGB audit вызывает реальный production renderer и захватывает vertex RGB:
три source объекта сверяются с независимо прочитанной палитрой, native biome
climate/blend/modifier; source_bricks и старая дверь служат белыми controls.
Сцена расширена до 15 представителей; source lily-pad/foliage не имеют активного
tinted consumer, и не получают выдуманную поддержку.

Серверный final6 дал 62/63: все пять новых tests PASS, один прежний v9 test
`survivalHoldingOrdinaryItemStillUsesLever` FAIL при немедленном связывании RP.
Сохранённый failed lever: x313.5,y-58,z1.5, chunk19, Links=[]; исходный helper
не проверяет getEntity после spawn, addLink result игнорируется. Неизменные
relative offsets указывают на door chunk20; отсутствие door в save само по себе
не доказывает spawn. Для повторной проверки добавлен только source-owned
`@BeforeBatch(v9_mechanisms)` preload x18..22,z-2..2 (25 test chunks), аналогичный
проверенному v12_groups border. Старые tests и production не редактировались;
final7 должен подтвердить гипотезу fixture/entity-publication. Эта инфраструктурная
проверка сохраняет production SHA final6. Повторный final7 подтвердил PASS 63/63
для того же JAR `9797fb4bb4b6c4103b60b994fb0c4ad4f88c46bc7a19de42eb596e7f0c57e117`:
старые 58 и новые пять tests. Исходный FAIL сохранён отдельно, не переписан в PASS.

Реальный native-final4 client для этого же SHA подтвердил baked QA PASS:
1482 part models, 1805 offered item icons и 7220 целых closed/open BASE/ALT poses.
Author RGB acceptance также PASS: три source consumer world vertices содержат
`0x6F7578` и белые untinted faces, item vertices — `0x777A7F` и белые faces,
что равно независимо вычисленным PNG/native climate/blend/modifier значениям.
Новый source_bricks control и старая prototype_double_door дают только
`0xFFFFFF` world/item. Actual client network PASS для 15 представителей:
первые четыре прошли восемь yaw, все — реальный doItemPick, копию с другим
UUID, обычный attack/remove и новую финальную установку; новая дверь — open/close
с сохранением UUID. Capture записал 30 framebuffer PNG. Независимый review
`verification-visual-01-08.json` подтверждает 16 front/side кадров первых восьми
случаев: отображение, placement на полу, alpha, различия сторон, отсутствие
missingno. Остальные кадры оценивает root. Вид source_1_spirelamp_001 как серого
кубоида соответствует исходным elements/UV/PNG; лампа не дорисовывалась догадкой.
Физический ввод по-прежнему NOT_RUN и не выводится из этих PASS.

Actual `VERIFY_REJOIN` client подтвердил PASS всех 15 сохранённых source roots
после настоящего stop/restart в проверенной копии нового тестового мира:
ID/variant/rotation/open и owner UUID совпадают с первым network PASS. Первый
saved-world proof также PASS: 15 roots, 94 carrier/ledger cells, 15 удалённых
copyRoot отсутствуют. Strict launcher server report при повторном входе остаётся
FAIL: три flagged log lines одного внешнего Yggdrasil key-fetch timeout
(`AuthenticationUnavailableException` → `SocketTimeoutException`). Это не три
независимых ошибки мода. Реальный localhost server (`127.0.0.1`, offline mode)
успешно запустился, принял клиента, завершился exit0 и сохранил все dimensions.
Отдельная квалифицированная runtime проверка должна перечислять эти точные
external lines и не объявлять внешнюю Mojang authentication/online-mode PASS.
