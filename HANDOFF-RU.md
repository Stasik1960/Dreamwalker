# Dreamwalker: итоговая передача карты, modpack и исходников

Карта, native light и исходная финальная11+1 приёмка завершены. Пользовательское центральное A/B сравнение завершено; выбран A, полный маршрут A принят. Actual результат контрольного `actual-reopen-pack-a` записывается в пользовательский отчёт. Завершённые игровые проверки заново не запускать. Окончательная готовность комплекта подтверждается `DELIVERY-ACCEPTANCE.json`, не одним механическим route PASS.

## Восстановление и источники

Ветка `codex/native-grid-city-full-20261024`, tag `native-grid-city-full-20261024`. Manifest релиза: https://github.com/Stasik1960/Dreamwalker/releases/download/native-grid-city-full-20261024/bundle-manifest.json ; SHA256 `290a6891c9e83c70e63bba37e5da555da09a01bccb4b7383dcb74cc88ebea2d2`. Published `FINAL-SNAPSHOT.json` SHA256 `{{FINAL_PUBLISHED_SNAPSHOT_SHA256}}`.

Прямая база FINAL delta — опубликованный FULL-LIGHT checkpoint: manifest SHA256 `75e1ddf017d7ec04733e047c42ed55239be641755d518040074e4db99491565a`, snapshot SHA256 `fbd5284aa8212ec91c4671cb7b6532e8ee2abffc480fb7ab493518c62e1f8e1b`, follow-up Git `57d04c71fc77024eb50cb186f89a32bf5e2a7e45`. Восстановить все тома по URL/SHA текущего manifest существующим `github_delta_restore.py`; PRE-MAP не подставлять как непосредственную базу.

Capture включает7 overlays: `research/`; `source/tools/`; `source/src/nativegridrelight/`; `source/build/libs/`; `source/src/architecture/java/dev/dreamwalker/bloodbornedw/composite/`; `DW-native-grid-final-freeze-20261024/userpack-recorder-candidate/`; `DW-native-grid-final-freeze-20261024/userpack-candidate/`. Последние две F-папки сохраняют исходники/locks дополнительного QA. Полный release-restored проект — `C:/Users/vakir/.codex/worktrees/3f07/DW-native-grid-city-20261022`, а не частичное Git mirror. Source ZIP: `research/delivery/full-current-sources.delivery.zip`, SHA256 `6bda26166864d142aef6fe770e664ba91c796f94e48e965ac7d7b17cf3eac063`.

Мир: `research/city/bloodborne-native-grid-city`. Production: `research/runtime-history/production-lazy-frapi-9b2edaa041331ff510df87ad1abd51b1de9a535f5768396bc04b6b5cf24d7d33.jar`, SHA256 `9b2edaa041331ff510df87ad1abd51b1de9a535f5768396bc04b6b5cf24d7d33`.

## Что принято

- FULL apply2153693 placements; catalog freeze `research/native-grid-plan/full/frozen-catalog.whole-retained-final.json`, SHA256 `16493e1a3e79554f72e68c192c4834600e8d152b98ec970d6c556d5eef69c71b`.
- Native light4370/4370,4 original assembly merges saved; accept-relight PASS.
- Final preserved coverage `research/native-grid-plan/full/preserved-assembly-final-coverage.json`, SHA256 `142f79644b6c3f74ffbf9fce1e2449f9c0c1524d8c8a9c064ab7dd9d740588b8`:28 entries/76 poses/3801 objects,277 compiled whole/3520 local original/4 merged runtime groups,4 actual groups/8 source owners, retentionMembers checked.
- Исходные Indigo11+1:881.109s и594.453s, нормальное сохранение/exit0; root осмотрел12 actual PNG. `research/delivery/full-route-visual-review.json` и `full-route-metrics.json` относятся к этой отдельной приёмке.
- Пользовательские desktop37 client/36 server JAR сохранены неизменными; отдельные копии/профили, matching9b2, Java17/GPU/4G4G. Исходные11+1 не повторялись ради пользовательского этапа.

## Центральное A/B и выбор

Восемь одинаковых стабильных окон каждого варианта. A — userpack/Fabric Indigo без Sodium/Indium; B — тот же pack +client Sodium0.5.11/Indium1.0.36 из frozen набора. A2 `actual-centre-a-decimal` PASS737.906s:4746 frames,avg59.74494FPS,1%low43.22992,p95/p99/max20.58135/21.54351/29.1445ms,0 кадров >250ms/>1s/>3s. B `actual-centre-b` mechanical PASS754.844s:4747 frames,avg59.75099FPS,1%low45.96347,p95/p99/max19.22951/20.533684/25.5163ms,также0 длинных кадров в стабильных окнах.

Root осмотрел A8 views и B8 contacts+2 full PNG: B не отображает проверенные statue/tree в обоих раундах, поэтому визуально исключён. Его немного меньшие tails не являются выигрышем при эквивалентном изображении. Авторитет выбора — `research/user-modpack-validation/centre-selection.json`, SHA256 `5aa73e3633311c17b4982b0c7702a66857975a1562e47096daf816884bd427e2`, selectedCase pack-a/backend fabric-indigo.

Boot server A158.477s/B158.518s; client PID→initial position A379.975s/B407.295s; first stable window ещё+17.425s/+13.212s. Это фактические длительности, не причинный вывод о модах. A0 с scientific yaw не дал измерительных кадров и сохранён как unmeasured; исправлен только tiny yaw→0 в DATA, helper/JAR не менялись. Старые измерения не повторять ради JFR: его profile recording уже есть.

## Полный пользовательский маршрут A

`actual-full-city-pack-a/receipt.json` PASS0/0,1948.812s:92 фазы,3712/3712 целевых содержательных колонок доставлены,7865 уникальных доставленных с соседями. Generated39485 не равно набору содержательного покрытия. Стабильные окна:59.82295FPS,1%low39.1917,p99/max23.61914/34.6567ms,0 кадров >250ms.

Весь измеряемый маршрут с движением:1383.12867s,64081 frames,p95/p99/max32.671/165.01254/2043.4025ms;226 кадров >250ms,1>1s,0>3s. Raw FPS не нормализовался. Движение13863.984m/18519 ticks/0 chunkWaitTicks. Сервер27924 tick intervals,p99/max58.32748/755.7925ms,3>250ms/0>1s; tick work p99/max21.517511/75.4325ms.

Это реальная доставка целевых колонок и92 изображения, не ручная проверка каждого полигона/каждой копии. Часть камер показывает подложку или перекрытия. Нельзя обещать полную визуальную проверку всех generated chunks или неизменные60FPS при streaming.

## Железо, нагрузка и память

Ryzen7 5800H,RTX3070 Laptop,16GiB RAM; Java17,16 logical CPU host-default без прежнего2cap. На одном ноутбуке одновременно server4G/client4G, Xms128M,1280×720/fancy+AO/view6/sim4/cap60. Frozen base profile2857/client3G и recorder24ea не переписываются; пользовательский QA6b88 — отдельный помощник/профиль, не замена исходного benchmark.

1Hz observation полного маршрута: system available RAM min349274112 bytes/mean858659334; machine CPU mean42.34%,p95 90.918%,max100%. Client working set max7229407232 bytes,server4962447360 bytes. Working set не heap; shared pages и несовпадающие по времени максимумы нельзя просто суммировать. Нагрузка относится ко всей машине, не доказывает вклад конкретного процесса или причину каждого stutter. A/B JFR CPU — отдельный итог в пользовательском отчёте, без повторного запуска измерений.

Старый failed before-map baseline не переписывается. Новый centre A/B — отдельный пользовательский comparator; весь маршрут пройден только выбранным A. Нет доказанного процента ускорения исходной миграции или гарантии удалённого хостинга.

## Сохранность и точные ограничения

Исходные v12, данные, двери и согласованные самостоятельные цельные объекты сохранены; два бюста [223,71,-925]/[-62,113,-733] original-format. Разрешены только10 visual quads/4 fragment records, последние8 в [30,23,-16]/[171,1,-897]; authority `user-exact-ten-visual-polygons-permission.json`, SHA256 `eebdc3da17a415d7a155806aead19aa57b72eec024936e6b46316dfa947baf3a`. Пять vanilla bakes — сухой свет1/2/4/5 и сухая нижняя andesite slab: вид/коллизия/излучение сохраняются, перечисленные регулировка света/слияние/заливка водой могут быть потеряны. Исходные overlaps допускаются однократно; ручное восстановление после break может штатно не пройти.

Не смешивать два проверенных типа с двумя уникальными экземплярами:40 исходных размещений точного `source_hold_statue` v1;159 элементов `source_original_tree` v6/rotation6 — не159 целых деревьев. Не утверждается, что все их копии пропадали. Подтверждённый дефект B — пустые проверенные statue/tree views. Подозрение о пропавшем tower facade отозвано: probe603,70,-100/static9part108/recipe1260/p850… — source_raw_gold_block v0, ожидаемый двухплоскостной roof_5 шпиль, bounds[603,69,-100]..[604,71,-99], не структурный фасад. Он виден и соответствует source. Карта ради renderer не переписывалась.

Delivered9b2 связан с ac56 proof `research/native-client-cache/runtime-lineage.json`, SHA256 `1064213edab85b28ac315a04b30504aea4509a1c69a450ee7dd396833392c0d8`: изменены ровно3 NativeGridClient classes/lazy optional FRAPI cache, остальные payload/resources/server-common/metadata exact. Старые map/light/TARGET/FOREIGN/preserved receipts остаются ac56, не relabel/rerun. Подробные OOM/abort/Guard interruption истории сохранены в reports, не повторяются в пользовательском отчёте.

## Новый ПК: Git bootstrap и build API

До restore отключить auto-worktree-deletion. Manifest не содержит `.git`. `research/delivery/original-independent-git-bootstrap.zip`, SHA256 `db489dfd7442149f55330b63f61706f93dabd234953d71379537493fd52f0955`, содержит настоящие objects/HEAD/branch/shallow metadata, не account config/hooks/index/LFS cache. Проверить SHA; извлечь ZIP в `C:/Users/vakir/.codex/worktrees/3f07/DW` ТОЛЬКО если `DW/.git` отсутствует. Не применять Force и не перезаписывать неизвестный существующий репозиторий. Реальный branch `codex/dreamwalker-bb-fabric-1.20.1`, commit `2b208d4916a831cee7d931399ac673820b37cb03`, tree `b8a3ccf52c858db3a007de489ee4056625d707a7`; затем Guard Check. Не создавать fake HEAD и не обходить Guard.

После Git bootstrap, только для сборки source: неизменённый build.py ожидает API `C:/Users/vakir/.codex/worktrees/3f07/DW/bloodborne complete/build/v127-dependencies/worldedit-mod-7.2.15.jar`. Восстановленный источник — `DW-native-grid-city-20261022/research/profiles/candidate/server/mods/worldedit-mod-7.2.15.jar`, SHA256 `17db6b3e94f52d25426684663e1e1846823cbb7907f1c365ac329e5bc7bfaf2c`. Проверить source hash; копировать в destination только если отсутствует. Если destination существует — проверить hash и не перезаписывать неизвестный файл. Это historical compile API, не новый игровой мод. Exact instructions: `research/delivery/reports/storage-recovery-20261024/compile-api-restoration.json`.

LocalC/E paused. Причина прежнего исчезновения DW не доказана; пользователь подтвердил auto-deletion OFF, C-only Git recovery/Guard сохранены. Не все старые LFS release archives были в local cache; восстановление опирается на все URL/SHA manifest, текущие map/mod/source/dependencies сохранены.

## Краткий append-index для продолжения GPT

Все пути ниже относительно `research/user-modpack-validation/`:
- `centre-a-metrics.json` SHA053d1b62c063c662fe5a338917dff9921f851386dbe149666ed1351171a28c92.
- `centre-b-metrics.json` SHA06e9e5e672fe8fc54fca0d27963689e1d543a2b4f875098aa0532b199417060f.
- `centre-selection.json` SHA5aa73e3633311c17b4982b0c7702a66857975a1562e47096daf816884bd427e2.
- `full-city-lock.json` SHA7083a21a74a45b5fd1056a1c6d8ac580359eb513b425e70267f028a257b45987.
- `actual-full-city-pack-a/receipt.json` SHAe8425e29d96a771efafaca3666818ede422f6a652a8be22793292ac87b19cf3f.
- `full-city-metrics.json` SHA3a5b769baee29087c833cf8b4b835bb717ce105b6101ed068defbf5b13a5bea1.
- `full-city-resource-summary.json` SHA96fadd5f2db0fb0dc45175b7957a8b2ee022ae02c2211990892382f053997b03.
- `actual-reopen-pack-a/` — actual контрольный процесс, не перезапускать; результат в пользовательском отчёте.

При будущей работе читать START/USER-PRIORITIES/актуальные RESUME/current-operation. Дополнительный reopen и извлечение уже записанного JFR завершены; итоговые reviews, source package и aggregate включены в полную поставку. Никаких новых игровых прогонов ради документации или уже записанного JFR.

Дополнительный фактический reopen A: PASS, клиент/сервер exit0, 628.141 с; нормальное сохранение, статуя и окружение видимы после нового входа. При входе худший кадр 1,02 с, худший интервал серверного тика 1,36 с.
Receipt SHA256 0efcb40ba83f1de7ced8ddaacac9543af52ec6a46457f4e8583e702e76677610; метрики: research/user-modpack-validation/reopen-metrics.json.
JFR CPU: Средняя загрузка всей системы в записях клиента/сервера: A 48.3/43.9%, B 44.1/39.7%. Это разные полные записи, не синхронные окна и не поправка FPS. Сводка research/user-modpack-validation/centre-jfr-cpu-summary.json, SHA256 93f45f6a72f43788111280ab0f4099531ad62ddb2b9123e6a10b6520c567efc0.
Ограничения визуального осмотра и тёмные/перекрытые ракурсы отдельно сохранены в research/delivery/user-modpack-visual-observations.json; подтверждённых критичных дефектов выбранного A не установлено.
Исторические F proof/build helpers и шесть малых candidate-папок сохранены с relativeF layout в research/delivery/final-workbench/. Проверить каждую SHA по capture.json и восстановить необходимые пути в DW-native-grid-final-freeze-20261024 только если они отсутствуют либо уже совпадают по SHA. Не перезаписывать неизвестные файлы. Отвергнутый legacy-carrier-render-candidate не применять. Это восстановление доказательств, не повод повторять игровые проверки.

Финальный file-only проверяющий код уточнён: исходные function gate13d2 и snapshot13026 остались неизменными, допустимая цепочка из трёх уже принятых carrier/whole квитанций проверяется по точным SHA, before/after generations и неизменности функций/физических данных. Старый BLOCKED и прежний исходный архив сохранены с relocation.json в research/delivery/reports/aggregate-before-lineage-fix/. Мод, карта и игровые проверки ради этой правки не менялись.
