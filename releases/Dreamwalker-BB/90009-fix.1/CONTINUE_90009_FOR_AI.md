# Инструкция нейронке, продолжающей Dreamwalker BB 90009

## Последующее указание пользователя: всё уже в main

После подготовки этой инструкции пользователь прямо поручил «В main всё заливай». Выполнено: merge-коммит `527c922f984d5a5f409a3cc35f30f049dbe965ac` опубликован в main с сохранением существующих Model-Props/Collision Studio. На другом компьютере взять актуальный `origin/main`; `codex/bb-v10-90009-fix` — историческая рабочая ветка этой реализации. Ниже ограничения против самовольного main merge описывают прежнюю стадию: конкретное последнее указание пользователя разрешило эту публикацию и имеет приоритет. Main-публикация не означает приёмку v11 или новые игровые PASS; JAR/ZIP и код BB при merge не менялись. Тесты остаются частичными, все остатки ниже актуальны.

## 1. Что поручено сейчас и почему работа остановлена

Продолжить существующую реализацию, а не начинать заново. Исходная задача — инструмент90009, перечисленные правки объектов, полная галерея, реальные проверки и публикация. Производственные изменения уже внесены, сборка и значительная серверная проверка выполнены. Полная игровая приёмка НЕ завершена. Пользователь устал от затянувшихся проверок и 10 октября2026 поручил закончить их на этом компьютере, сохранить результат и передать другому человеку/нейронке на другом компьютере.

Последняя публикация до этой инструкции: `4f469703673b7553be113082d8be0f72ddd25041`; исходники/выдача впервые отправлены в `613bbca9b75a7c8d145469b0c41e4b49767f87be`. Читай новую HEAD рабочей ветки, не откатывайся к этим коммитам: последующие изменения могут уточнять отчёты. Подтверждение реально выполненного push и скачивания — `../releases/Dreamwalker-BB/90009-fix.1/evidence/GITHUB_PUBLICATION.json`.

Эта инструкция — передача контекста, не доказательство новых результатов и не новое разрешение управлять компьютером. На новой машине уточни у человека разрешение игровых запусков/занятия мыши и клавиатуры, EULA принимает сам пользователь. Здесь Minecraft сохранён и закрыт, Computer Use сброшен; НЕ запускать его снова ради документации.

## 2. Что читать, чтобы не повторять расследование

Все пути ниже относительно проекта `bloodborne complete`, кроме `../releases`.

1. Ближайшие AGENTS.md и `HANDOFF.md`: ограничения и краткий актуальный снимок.
2. `docs/REVIEW_90009_FIX_REPORT.md`: какие изменения внесены, что подтверждено, что ещё нет.
3. `reports/REVIEW_90009_TEST_MATRIX.csv`:32 сценария; отдельно статус проверки, состояние реализации и конкретный остаток.
4. `reports/REVIEW_90009_NATIVE_VISIBLE_20261010.json`: реальные клики, UUID, версии и F2. `REVIEW_90009_CLIENT_INPUT_OBSERVATIONS.json` — более ранние наблюдения, не безусловный PASS нынешней сборки.
5. `docs/REVIEW_90009_VISIBLE_CHECKS.md`: координаты и сценарии оставшегося игрового прохода.
6. `docs/REVIEW_90009_ORIGINAL_REQUEST.txt`: полное исходное задание, включая T01–T32. Требования сохраняются, но позднейшее указание пользователя остановить проверки/передать работу имеет приоритет над старым «не останавливайся».
7. `docs/REVIEW_V10_MENU_GUIDE.md`: актуальная русская памятка. `REVIEW_V10_INSTRUCTIONS.md` помогает с историей базы, но не заменяет текущую матрицу.

Старые TASK/PROGRESS/README могут описывать v9 или исторические поручения. Не выполнять их как новые задачи. Не разбирать заново всю историю ради уже найденной причины. Базу v10 и историческое остекление исследовали; используй результат и проверяй только новую неопределённость.

## 3. Правильный исходник, версии и артефакты

Репозиторий `https://github.com/Stasik1960/Dreamwalker.git`, рабочая ветка `codex/bb-v10-90009-fix`, база v10 `d5d78282b5df3d274a49be8e5b947bedc6bf3ecc`. Intended source — только `bloodborne complete`. Не править старый `Bloodborne-Blocks`, распакованный JAR, инспекционные копии и чужие моды вместо исходника. Сохранить чужую dirty работу; сначала git status, remote, branch. При необходимости отдельный worktree от актуальной рабочей ветки, НЕ от старой базы с потерей исправлений.

Стек: Minecraft1.20.1/Fabric Loader0.16.10/API0.92.9+1.20.1/Yarn1.20.1+build.10/GeckoLib4.4.9/mclib20/Java17/Gradle8.8/Loom1.6.12. Wrapper находится в проекте. На машине автора default Java21 и глобальный путь JDK были неподходящими; явно выбирать JDK17, не изменять глобальную конфигурацию без необходимости.

Выдача — `../releases/Dreamwalker-BB/90009-fix.1`:

- Ordinary combined `dreamwalker-bb-fabric-1.20.1-0.1.0-90009-fix.1.jar`, SHA256 `61b206c7bd2d253fbf1387b8d95c7e3663f192759ce4673c808798246e90a4fa`.
- Полный pristine `Dreamwalker_BB_Gallery_90009.zip`, SHA256 `ffafb1986e291a1db0f266dbb18383499535a9671dd175129a7e399831ec9a3e`.
- В evidence — XML, native logs/reports, реальные diagnostic ZIP, source provenance; F2 в screenshots.

Обычному клиенту не нужен review QA add-on, resource-pack или Kappa. Combined уже содержит RP: вторую копию RP не ставить. Нельзя выдать dev/sources JAR как пользовательский. Это НЕ принятая v11.

На новой машине можно клонировать именно ветку; в существующей копии сначала проверить dirty state, не делать reset/checkout поверх чужого. Получить Git LFS содержимое там, где оно действительно требуется; pointer не заменяет ресурсы. Эта папка выдачи хранится обычными Git blobs, не LFS. `.gitattributes` сохраняет байты файлов выдачи без нормализации строк, чтобы SHA манифеста совпадали на другой ОС.

## 4. Где в коде находятся уже внесённые изменения

| Задача | Основные файлы/каталоги |
|---|---|
| UUID выбор, серверные запросы, save/version/ACK | `src/architecture/java/dev/dreamwalker/bloodbornedw/tool/BuilderClient.java`, `BuilderServer.java`, `BuilderScreen.java` |
| Ввод/переназначение/HUD | рядом `BuilderControls.java`, `BuilderControlsScreen.java`; `composite/mixin/BuilderInputMixin.java`, `BuilderKeyboardMixin.java`, `BuilderMouseMixin.java` |
| Ограниченный per-player undo | `tool/BuilderUndoHistory.java`; `src/test/.../tool/BuilderUndoChecks.java` |
|90008 и retired aliases| `architecture/LegacyBuildingTool.java`, `CatalogueMigration.java`, `composite/mixin/CatalogueStackMigrationMixin.java`, каталог/Creative |
| Рычаги/правила/ограничения/события | `link/MechanismBuilder.java`, `MechanismRules.java`, `ObjectPolicies.java`, `ObjectPolicies` и вызовы фактических переходов RP |
| Фонари | BuilderServer/BuilderScreen и существующий RP lamp пакет; не создавать вторую независимую сеть |
| Собаки/nameplate | `src/rp/java/dev/dreamwalker/bloodbornerp/client/CatalogEntityModel.java`, `CatalogObjectRenderer.java` |
| Source-bottom91030/13, busy и ordinary parts | RP `object/PlacementObjectItem.java`, `RpObjectGeometry.java`, `RpObjectEntity.java`, `RpObjectSelection.java`; `architecture/RpCreativeAttackReach.java`, `composite/mixin/RpServerInteractionMixin.java` |
|90002/90012 и взаимные соединения| `architecture/wall/*`; `DiagonalPostConnectionMixin`, `DiagonalPostFenceMixin`, `DiagonalPostPaneMixin` |
|90003 исключение размещения| `architecture/PlacementPhysics.java`, composite runtime/shape checks |
|90006 динамическая физика| `architecture/PrototypeLadderBlock.java`, `PrototypeLadderItem.java`, SourceClone/CompositeRuntime |
|90020| `src/architecture/resources/assets/bloodborne_dw/models/base/v10_windows/window_03.json` |
| Галерея и доказательства диска | `src/review/.../review/CatalogueGalleryBootstrap.java`, `tools/gallery_world_check.py`, package/archive tools |

Это навигация, не предписание переписывать каждый файл. Сначала оставшийся пользовательский сценарий, потом точечная правка причины. Проверять разделение client/server, server authority/packet validation, main thread; не добавить клиентскую загрузку класса на dedicated server.

90020: исторические коммиты `731c83a322a16fbb3ac6c617f80e344649a24e4b` / `ef5550f6c65d163855c2e16422e1a3adafb37b68`; файл `Bloodborne-Blocks/src/main/resources/assets/bloodborne_blocks/models/block/hold/window_03.json`. SOUTH восстановлена `[9.25,7.25,14.75,12.75]`, NORTH `[0.125,10.25,5.625,15.75]`; старый встроенный −45° НЕ возвращён. Исторические визуалы — large/large, small/small, small/large на двух сторонах, не три новых рисунка. Визуальную приёмку всех planes ещё выполнить; если результат не соответствует требованию пользователя, расследовать конкретный недостаток, а не рисовать случайное новое окно.

## 5. Что можно считать доказательством сейчас

147 полных GameTests и14 focused содержат148 разных серверных сценариев (13 focused повторяют полный набор), ошибок0. TransactionChecks21 и BuilderUndoChecks пройдены. Их пакетные доказательства относятся к SHA `5066ed3b334b4103120932f9be8810d2548230ddbd94984e62780311fbb1793a`, не к прежнему v10. После этого изменены только два GUI-исходника: JAR diff ровно BuilderControlsScreen.class, BuilderScreen.class и generated BuilderScreen$1.class. На SHA61 выполнены отдельные relevant GUI проверки и ordinary dedicated reopen/save/exit0 exact ZIP,121 saved UUID. Полный147 не повторяли после GUI-only изменений; не писать, что повторяли.

Нативный ввод — настоящая мышь/клавиатура в обычном клиенте, не mock-player. Однако серверный GameTest с настоящими world/entities всё равно подтверждает внутреннюю логику, не полную игру человеком. `/data get` read-only подтверждает состояние, но не картинку анимации. `/tp` использовали для позиции/направления камеры, НЕ для замены тестируемых переходов фонарей.

Сборки последнего видимого прохода:

| SHA/профиль | Подтверждённое |
|---|---|
|5066, `runtime-client-shipped-archive-visible-1920-scale4-prepared`| actual entry Creative/tool/F3 и воспроизведённый overlap conflicts |
|89e4124456062277a08eb69e5b083e6e4f4190dd7ee6dcc36ed2cb623c9ea018, `runtime-client-controls-footer-visible-1920-scale4-01`| исправленный footer,6 вкладок, E-context, GUI60с; тогда найден focus bug |
|61b206…, `runtime-client-focus-dogs-visible-1920-scale4-01`| native clean-field focus retest; три hidden dog, две undo return;91009 independence; lever3 opening; actual lamp roundtrips; rename/LINK/direction; GUI60 linked ZIP |

Сеанс GUI61: `d3201bb7-ea23-406a-bb11-8f9634ec8871`, два архивa в evidence. Их CRC проверены, но полная интерпретация метрик/ограничений/ошибок ещё не выполнена. Server-only запись60с не равна dedicated server+client. Контролируемого нагрузочного сравнения нет, GPU-время не измерялось.

## 6. Что НЕ закрыто и с чего начать

Не путать «нет подтверждённого оставшегося бага» и «всё исправлено». Известные обнаруженные GUI ошибки исправлены/retested; остальные пункты либо имеют серверную проверку, либо частичную игровую проверку, но требуют приёмки. Единого общего PASS T01–T32 пока нет. Матрица намеренно оставляет30 статусов «Проверена внутренняя логика» и2 «Не проверено»; partial game результаты записаны отдельно.

Предпочтительный один комбинированный проход:

1. Подготовить новый изолированный профиль/копию pristine ZIP. Проверить точный SHA установленного JAR, first entry и defaults. Пройти Ctrl+wheel/Alt+wheel/Shift+ЛКМ/обычное колесо,20 undo, chat/fields, обе руки, реальный MMB перебор перекрывающихся объектов. Пользователь раньше согласился помогать с модификаторами; на новой машине договориться заново.
2. GUI1280×720 и1920×1080: обычный и увеличенный scale, шесть вкладок, длинные списки/UUID, конфликты, text focus, dirty forms/Save/Discard/Stay, resize/tab/server updates. Смена scale возможна без restart. Не подгонять scale1 ради PASS.
3. Все3 клетки: hidden→visible, каждый same-type независимый экземпляр, анимации. Оставить разные сохранённые состояния до общего перезахода. Уже проверенные91007/08 undo не нужно воспроизводить много раз без изменения.
4. Рычаги: дополнить фактические close всех3, lever2→Door1, затем создать новую simple связь без меню и только после этого ANY/ALL/flags/conflict/deletion/manual-restriction/pending-close. Сохранить70 тиков, дождаться клипа. Прямо установленный генератором стенд не доказывает native simple-link workflow.
5. События OP4: в настоящей форме добавить две безопасные команды, сохранить, реально открыть/закрыть, проверить порядок/one-shot/readback/edit/remove/ошибочную строку/отказ без прав/перезаход. Не использовать массовый kill: RP тоже entities. Для TP выполнить оставшиеся unlink/другая обратная линия/cooldown/unloaded/unsafe-arrival/cancel cleanup, не повторять уже выполненные круги по десятку раз.
6. Геометрия: windows обе стороны/3 planes;90002/12 angles/no-bottom/both-way neighbors;90003 обычный и модовый блок в выступающей части при intact root/chest/physics;90006 реальный walk/climb, две фактические опоры/динамика/height/yaw;91069/86 регрессии; ordinary parts/gaps/reach/occlusion Survival/Creative; busy spam рук/рычага/двух игроков; ordinary placement91030/13/source-bottom/height/reset; RP CustomName сохранён, nameplate нет, NPC имена не удалены.
7. Один общий save→quit→reenter подтверждает подготовленные persistent изменения. Dedicated полный server restart отдельно, не считать integrated reload равным двум сторонам сети.
8. Подключить настоящий client к isolated dedicated ordinary server, проверить OP4 и не-OP4, удалённую диагностику/client local ZIP. Два действительных разных игрока одновременно для T19/T30: shared UUID/version conflict и granular undo. Mock players не заменяют это.
9. Одна и та же сцена/позиция/render settings: tool/highlight/record OFF и ON, bounded60с записи, реальные CPU/tick/FPS/memory/GC/packets, ограничения/ошибки. Не вычислять «процент нагрузки блока» из FPS или GPU из CPU. Затем закрыть остатки по32 строкам.

Во время прохода вести один список новых ошибок; исправить их пакетно, собрать один раз, повторить затронутые сценарии. Не перезапускать игру на каждый клик, не заставлять человека терять контроль во время длительных фоновых операций. После видимого блока штатно сохранить/выйти или снять захват мыши, сбросить Computer Use и сообщить о возврате управления. Уведомлять о прогрессе, но не заканчивать ход одним обещанием, когда разрешённая работа ещё доступна.

## 7. Галерея, точки и камеры

Pristine ZIP:103 типов =11 архитектуры+69 RP+18 настоящих NPC+4 неразмещаемых предмета+90009; ещё18 на стенде, всего121. NPC NoAI/PersistenceRequired, не яйца. Platform bounds по физике, без неё — визуал; outward floor/ceil +2 каждой стороной, ближайший gap2. Выведенные90008/90013–17/90018–19 не предлагаются. В world24 файла, только session.lock исключён. Generated QA не нужен после authoring.

Координаты/полные UUID находятся в `evidence/catalogue-gallery-output.json`. Краткие корни/камеры:

| Экспонат | Root | Рабочая камера setup, не часть действия |
|---|---|---|
|91007/08/09 каталог|14/24/34,64,14|14.5,64,10.5 yaw15 pitch0;24.5 yaw−25;34.5 yaw0|
| same-type клетки stand|91/101/111,64,103|111.5,64,99.5 yaw0 для91009 |
|lever1/lever2|32/40,64,102|32.5,64,99 yaw0 pitch12 для lever1 |
|Door1/Door2/90001|48,64,102 /57,64,103 /120,64,101|Подобрать действительную видимую часть: root не обязательно центр модели |
|lamp A/B/C|66/74/82,64,102|A approach66.5,64,99.5 yaw10 pitch8; arrivals68.5/76.5/84.5,64,102.5 yaw100 pitch8 для взаимодействия |
|window90004/10/20|3/12/21,64,113|Исходные offset сохранены |
|fence/post/free ladder/supported|29/36/43/50,64,112|Проверять с обеих сторон |
|91030/91013|66,64,24 /62,64,14|Новое размещение предметом, а не только authored instance |

Локальный изменённый клиентский мир автора НЕ опубликован вместо pristine: каталог91009 hidden, stand91009 visible; A name=Галерея A — вход, line=Галерея — проверенная, A→B/A↔C. Draft both сохранён, но обратное направление B→A НЕ применено. Temporary E action/R menu/G pick, scale3. На новой pristine копии первоначально Галерея/A↔B/A↔C и defaultkeys. Нельзя искать отсутствующие изменения автора и называть это потерей сохранения: этот runtime не входит в ZIP.

## 8. Сборка/проверочные средства и ловушки переносимости

После разрешённых изменений, PowerShell в проекте:

```powershell
$taskJdk = 'АБСОЛЮТНЫЙ_ПУТЬ_К_JDK17'
$env:JAVA_HOME = $taskJdk
.\gradlew.bat --no-daemon "-Dorg.gradle.java.home=$taskJdk" compileJava remapJar
# При затронутой логике: выбрать relevant task, не всё по кругу.
.\gradlew.bat --no-daemon "-Dorg.gradle.java.home=$taskJdk" transactionCheck
.\gradlew.bat --no-daemon "-Dorg.gradle.java.home=$taskJdk" runRepairtest
# Комплексный GameTest — серверный запуск; только после согласования.
.\gradlew.bat --no-daemon "-Dorg.gradle.java.home=$taskJdk" -Pgametest_run_name=90009-next-final runGametest
```

Для документационных изменений сборка/игровой запуск не нужны. После code change — relevant check и final ordinary artifact. Если требуется full final доказательство нового серверного кода, выполнить один комплексный набор после стабилизации; старый SHA не автоматически доказательство нового.

Существующие tools (прочитай parser/guards перед использованием):

- `run_final_client.py`: подготовка isolated профиля без --launch не запускает игру; --fixture/--quick-play, --server, --width/--height/--gui-scale. Указать --java-home и --loader 0.16.10 явно: старый default loader0.19.5 не тот проверенный профиль. --cached-minimal требует локального Gradle кэша; чужой абсолютный default modset НЕ переносим.
- `run_final_server.py`: localhost/random port, --world-copy новой own build-копии, --accepted-eula-file, --hold-seconds, --test-operator (только точный offline DreamwalkerQA UUID). Скрытая консоль не делает настоящий клиент вводом без фокуса.
- Client harness сейчас жёстко задаёт DreamwalkerQA. Для второго настоящего клиента нужна другая легальная тестовая identity/UUID или контролируемая доработка параметра; не запускать два с одним именем и не выдавать их за двух игроков. Offline только изолированный localhost, не обход public server auth.
- `launch_prepared_client.py`: --prepared-report/--launch-report, точные SHA и protected existing profile; старые JSON содержат пути автора и не являются готовой командой для другого PC. Создать новый run-name, не обходить guard overwrite.
- `verify_90009_archive.py`: --package/--output/--report, новое own build output; checks unsafe entries/CRC/every file SHA. `gallery_world_check.py`: --world/--manifest/--report, typed121 disk proof. Проверка не заменяет visual/native input.
- `package_90009_review.py`: создаёт НОВУЮ папку выдачи, validates103/121 +2/gap2, не overwrite. `finalize_90009_package.py` — старый локальный refresh с собственными требованиями/current-proof и локальными build файлами; не запускать вслепую на clone.
- `freeze_90009_handoff.py` — одноразовый исторический freeze точного SHA5066→61, не универсальная сборка/упаковка. На нынешней папке guard закономерно откажет; НЕ запускать повторно/не ослаблять guards ради PASS.

Build/cache/runtime не входят в GitHub clone. XML/logs/ZIP доказательства уже перенесены в releases/evidence. Не считать отсутствующий build каталог потерей исходников, не пытаться запускать старый PID/окно. При новой сборке не обещать идентичный SHA до сравнения toolchain/bytes.

## 9. Сохранённые контракты и незатронутые свойства

Сохранять registry/пятизначныеID, author attribution, типизированный NBT, packet/save совместимость, модели/текстуры/физику вне конкретного задания.90011 не переделывать;91069/86 не заменить90006; SourceClone роли/приватная пара не удалить;91094 pulse-only32/91088 static, lever70/RP ladder48 сохранить. RP CustomName/NPC names не стирать. Не делать глобальную расширенную reach или AABB ради click. Нет новых масштабирования/XZ/комбинирования моделей: предложение объединять модели пользователь отозвал («не туда»).

Правила удаления:90008 hidden alias→90009/count/NBT/legacy mode;90013–17 и ошибочный old angle→90002(cardinal) или90012(diagonal) по фактическому yaw;90018/19→90006 variant0/совместимые данные. Loaded root transaction atomic, stamp только при success, без forced chunk scan. При отказе диагностика и отложить, а не удалить объект. Проверять alias resources/recipes по задаче, если это ещё не подтверждено, не удалять общие ресурсы.

Два обнаруженных setup-отказа rename lamp НЕ считать новыми багами без воспроизведения: rename требует действительного выбранного source и существующей line; top info ещё не означает source selected. Сначала LAMP_SOURCE действие в мире, затем выбрать существующую line, ввести новое имя, получить ACK. Wrong camera/mouse warp тоже не доказательство серверной ошибки. Но скрытый непонятный шаг интерфейса по исходному заданию может требовать usability исправления, если реальный пользователь вновь упирается в него.

## 10. Как завершить, не потеряв доказательства

Для каждого нового случая: T#, JAR SHA, profile/loader/JDK/resolution/scale, ID+UUID, исходное состояние, настоящий input, expected, server ACK/actual state, visual result, сохранение после реального reentry и ссылки на F2/log/ZIP. Сначала зафиксировать ошибку, затем причину, change и affected retest. Не переписывать историческое ERROR как будто его не было — дать resolution/current result.

Обновить fix report,32-row matrix, native observations, памятку, capabilities при фактическом изменении. Разрешённые статусы проверки: «Проверено в игре», «Проверена внутренняя логика», «Не проверено», «Ошибка». Не ставить первый статус всей строке за один частичный подпункт. Статус реализации отдельный: что внесено/что сохранено/что ещё не решено.

После production изменений отличимая новая версия/папка выдачи, exact ordinary JAR и relevant saved-world proof, текущие SHA/fingerprint/CRC и полноценный ZIP, не только generator. Source provenance хранит байтовые входы сборки автора, а не гарантию неизменного SHA после Git нормализации/другого JDK. Не менять старый принятый v10 или нынешний кандидат молча, не main merge/force. Commit/push отдельной ветки, проверить remote HEAD и скачать артефакты обратно/SHA; манифест/evidence сохранять побайтно. Полная завершённость только после соответствующих проверок и приёмки; незакрытые ограничения сообщить честно.
