# Независимый обзор встройки нового переноса

База V12: `8b770a03de21554a21c91700a1c1c6fe86c894c4`.
Обзор выполняет отдельный агент; общие production-файлы при обзоре не изменялись.
После финального alpha-аудита активны 1047 новых registry-объектов,
1805 предлагаемых состояний, 0 blocked при импорте и 2 сохранённых исходных
particle-дефекта. Итоговые численные данные берутся из SHA-bound отчётов после
последней сборки; этот документ не является игровым PASS.

## Сохранность старого поведения

В прочитанном diff изменения общих Java-механизмов ограничены registration,
именами/предложением новых variants и разрешением новых openable объектов.
У `CompositeRuntime.use`, `notifyOpenTransition` и `CompositeMechanismBridge`
добавлено условие `RemainingArchitecture.opens(block)`. Ветка прежней
`prototype_double_door` и последующие проверки UUID/root/registry/policy/transaction
сохранены. Новые aliases не используются для подмены существующих объектов.

`RemainingArchitecture.creativeVariants` возвращает копию неизменного старого
ItemStack для любого ID вне нового каталога. Новые variants имеют явный
`BlockStateTag.variant`; существующий `CompositeItem` ограничивает variant числом
реальных poses, переносит только art и создаёт новый UUID. В pick остаются variant,
profile и open; owner/payload/links не копируются. Имя нового variant берётся из
русского каталога; прежняя ветка имени сохранена.

Focused GameTests проверяют эти новые пути отдельно: все новые variants и восемь
yaw (полный финальный прогон ещё ожидается); typed NBT; pick и новый UUID; допустимые изменения независимых
соседей; server native break; двери через selection ray и production use callback;
точный bridge UUID и отказ старому instance после переиспользования позиции.
Реальный клиентский input и полный server restart проверяются отдельно.

В первоначальной версии preservation-validator обнаружен ложный запрет всего
`assets/minecraft/*`: V12 уже содержит `assets/minecraft/atlases/blocks.json`.
Root получил замечание и исправил проверку: прежний atlas должен сохранить точные
bytes, новые vanilla model/blockstate/texture overrides не добавляются.

## Ресурсы и преобразования

Importer копирует исходные JSON elements/faces/UV/rotations/display и PNG/mcmeta
без переработки; меняются namespace-ссылки зависимостей. Uniform floor translation
хранится в descriptor part offset и `sourcePlacementOffset/localOrigin`, а не
вносится в исходную модель. Closed/open получают одинаковый placement offset,
что сохраняет взаимное положение поз при переключении.

Для `uvlock` при cardinal source X/Y создаётся отдельный private wrapper,
родитель которого сохраняет исходную модель. Wrapper меняет только face UV/rotation;
его source application записан явно. Проверять только наличие wrapper недостаточно:
добавлен независимый `tools/validate_remaining_artifacts.py`, сравнивающий все
исходные fields/PNG bytes/provenance SHA, цепочки parents/visible texture aliases и
UV-lock по integer cardinal face frames. Он также проверяет попадание точных
ресурсов в конечный JAR. Скрипт не импортирует код генератора.

Две модели jungle trapdoor содержат исходный отсутствующий unused particle
`block/texture`, при этом видимые faces ссылаются на существующую `#0`.
Importer сохраняет исходную particle-ссылку и записывает SOURCE_PARTICLE_DEFECT.
Независимый validator не заменяет её: точное raw-сравнение обязательно, visible
faces проходят обычную полную dependency-проверку, particle-дефект сообщается
отдельно. Нативный baked-model audit проверяет реальные visible quads/sprites.

У v15 `assets/minecraft/optifine/emissive.properties` явно указан
`suffix.emissive=_e`. Восемь новых объектов получили отдельные overlays только
для исходных faces, чья resolved texture имеет такой PNG в ресурспаке. Overlay
оставляет координаты, element rotation и UV; заменяет face texture точной маской
и явно задаёт `shade=false`. Validator независимо восстанавливает ожидаемый
набор faces из raw source, проверяет mask provenance, точные bytes и совпадение
descriptor transform с основным part. При source uvlock используется независимая
проверка UV, описанная выше.

Единственная renderer-ветка выбирает максимальный lightmap, когда ID объекта
начинается с `source_` и выбранный model path — `remaining/source_emissive/`.
Прочие parts получают прежний `light`; старые descriptors не имеют ни такого ID,
ни model path. RenderLayer, transforms, tint и основной source part сохранены.
World light не добавляется: настройки нестандартного OptiFine `LightPower` не
выдаются за ванильный block luminance. Реальный вид прозрачной маски/анимации и
совпадающих плоскостей всё ещё требует клиентских кадров.

## Риски, требующие измерения

1. Существующий `CompositeRootBlock` eagerly создаёт 512 BlockState на registry ID
   (`rotation8 × variant16 × open2 × profile2`), даже для объекта с одним variant.
   Для 1047 новых roots это 536064 дополнительных states. Compile PASS не измеряет
   память Minecraft и client model mapping. Нужны реальный старт сервера/клиента
   с используемым heap и анализ фактических OOM/errors. При доказанной проблеме
   допустима отдельная оптимизация только новых roots; свойства старых объектов
   менять нельзя. Автоматическая перенумерация/переиспользование old IDs не допустимы.
2. Floor normalization перемещает только Y. Некоторые исходные visuals могут
   целиком выступать в X/Z из зарезервированного root cell. Root остаётся занятым,
   хотя его собственный outline пуст. Это не автоматически доказывает поломку:
   видимые helper parts сохраняют owner и дают выбор/pick/целое удаление. Validator
   перечисляет такие случаи (`noSelectionInReservedRootCell`), чтобы выбрать
   настоящий нативный тест доступной видимой части, копирования и полного удаления.
3. Новые default collisions выводятся из исходных отдельных elements; наклонные
   объёмы получают SAT-filtered voxel approximation. Они не являются авторскими
   Collision Studio OBB. Размещение и движение в узких/наклонных местах требуют
   игрового подтверждения; успешная геометрическая math-проверка не объявляет
   отсутствие застревания или идеальное соответствие физики.

Из подтверждённого на момент обзора: compile-only сборка production + focused
GameTests + отдельного QA addon и 21 transaction checks завершена root успешно.
Независимый artifact-validator завершён PASS для промежуточного JAR SHA
`162ba8647e04509648da675b6e82e861eea0d3ec9cf6350ed779e4e6739cb844`: все 1050
объектов/1814 состояний, 1188 exact source models, 328 UV wrappers и 8 overlays.
Отчёт обнаружил 15 состояний без selection в root и максимум 18432 cuboids.

Первый полный новый GameTest-прогон с `-Xmx2G` завершился FAIL: после успешного
старта сервер получил `OutOfMemoryError: Java heap space` при сохранении chunk
palette (`build/remaining-transfer/remaining-final-1/gradle.log`, 15:05:17).
Crash указывает 5 MiB свободных из 2048 MiB; стек не изолирует registry states как
причину. Этот прогон не является игровым PASS. Root разрешил exact union coalescing
только новых descriptor boxes: требуется независимая проверка равенства объёмов,
новая сборка/SHA и повторение GameTests; source models/UV/old runtime не меняются.
Результаты предыдущего artifact PASS нельзя автоматически привязывать к новому
JAR. Нативные проверки сохраняют NOT_RUN до фактических SHA-bound отчётов.

Для исторического промежуточного SHA `0f8030e2bb9786fb0edfe7af2b3cecebf14e3a3a08d1b2f64c872104d4263250`
независимые проверки artifacts и preservation завершились PASS. Exact union proof
проверил 2149 изменённых наборов collision/selection: 871996 → 64808 cuboids,
полное равенство X slabs/Y sweep/Z interval unions без tolerance или оценки
объёма; текущие descriptor arrays и hashes привязаны к отчёту. Всего с неизменными
наборами число boxes уменьшилось с 874124 до 66936. Source models/UV не изменялись.
Этот SHA предшествует исправлению aliases новых emissive overlays, составным
названиям новых вариантов и последующему клиентскому color consumer. Его PASS
не переносится на финальный JAR: актуальные доказательства привязывает
`release-report.md` и таблица соответствий к своему точному SHA.

Независимый alpha proof подтвердил, что все pixels видимых texture PNG у семи
моделей glass/ladder/glass-pane имеют alpha=0. Три полностью невидимых helpers
не предлагаются как строительные объекты; это конкретное исходное содержимое,
а не придуманная replacement-art. Девять состояний явно записаны в exclusions;
номера 90535/90626/93191 остаются reserved append-only каталогом. Проверены три
целых PNG, поэтому вывод не зависит от предположений о UV sampling/animation.

Первый preservation-validator также ошибочно проверял ZIP directory markers
`assets/minecraft/` как resources и сравнивал Loom-postprocessed mixin JSON с raw
source. Исправление ограничено validator: `is_dir()` пропускается, `.mixins.json`
сравниваются с точными bytes опубликованного V12 JAR; исходный source SHA всё ещё
неизменен. Mixin SHA `fb329216f9525a8b4f8a98fdee150e8705e9c1422d29a676df3cef020ed8b448`
и vanilla atlas SHA `4db525c28222e5115d6e83df78cf700c50228801ee62172f0cebe310f304de3b`
совпадают в старом и новом JAR. Старые модели/textures/states/RP проверены raw bytes.

Второй GameTest-прогон на том же 2 GiB heap не получил OOM и подтвердил все 1805
variants × четыре yaw, прежние 58 тестов и новые двери. Два far-fixture теста
первоначально отклонили установку из-за unloaded neighboring physical cell.
`PlacementPhysics.conflict` намеренно читает также соседние carriers для выступов
vanilla fences/walls. Тестовый preload был исправлен: closed/open footprint плюс
одна ячейка halo и forced chunks только нового тестового мира. Production chunk
checks не изменялись; окончательный полный PASS требует повторного прогона.

## Нативный стенд

`tools/prepare_remaining_runtime.py` готовит только небольшой новый плоский стенд
из пятнадцати ролей: скамья, бочка, книги, мешок, светильник, статуя, кровля, цельная
дверь, pitched/asymmetric state, UV-locked group, emissive mask, helper-only selection,
authored grass block, authored grass variant и tinted potted fern.
Выбор делается из финального
успешного imported manifest. Новые objects не устанавливаются setup-командами.
Физический F8/мышь/F9 остаётся отдельной приёмкой NOT_RUN; opt-in network marker
проверяет actual interactionManager packets и vanilla doItemPick с серверным
подтверждением owner UUID. `inventory-after-join.json` отправляется после входа игрока,
а `setup.json` до входа создаёт только vanilla floor/op/gamerules.

План, opt-in client/network QA и native guide привязываются к production SHA
своего прогона. Root не получил физический desktop input: два Windows capture
завершились timeout, поэтому физическая мышь/F8/F9 остаётся **NOT_RUN**.
Отдельный opt-in QA вызывает настоящий клиентский interactionManager,
use/pick/attack и vanilla `doItemPick`, принимает серверные подтверждения UUID
и затем снимает framebuffer. Его отчёт может доказывать именно автоматизированный
client/network проход, а не физические нажатия. Scene
не является галереей; source map/gallery не читаются и не изменяются. Camera-addon
сохраняет настоящие framebuffer PNG, но кадры и NBT queries сами по себе не
подтверждают нажатия или полный визуальный проход каталога.

Клиентский bake выявил два source service post models (cyan/yellow stained pane)
с particle only и без elements. Оба предложенных assembly variant 0 содержат
эти посты и четыре непустых noside/noside_alt models, каждый с одним element.
QA исключение ограничено двумя точными пустыми служебными ресурсами; missingno
или пустой законченный pose не получает PASS. Каждый closed/open BASE/ALT pose
должен иметь суммарно больше нуля baked quads. Восемь emissive overlays теперь
содержат собственные texture aliases и face refs `#remaining_emissive_N`;
независимый validator разрешает эту точную карту и сравнивает выбранные исходные
faces/geometry/UV и mask dependency. Исторический artifact validation PASS для промежуточного SHA
`dc0e2c0f430119f83dc45fa94aa4f3bfbeea6295eef8d103b1cc739d96cfb7e2`.

Последняя production проверка относится к SHA
`9797fb4bb4b6c4103b60b994fb0c4ad4f88c46bc7a19de42eb596e7f0c57e117`:
artifact/preservation/union PASS, 21 core и final7 63 GameTest PASS.
Новая зависимость source grass palette копируется byte-exact в private namespace,
на три точных source ID; vanilla palette/BlockColors и остальные old/new ID
не меняются. Реальный client renderer RGB capture подтвердил точную авторскую
палитру/native biome blending для всех трёх source world/item controls и белый
цвет старой двери и нового source_bricks. Native model/item/whole-pose bake PASS,
client network PASS15 и actual restart/rejoin PASS15 с прежними owner UUID.
30 representative PNG оценены отдельно; первые 16 — в
`verification-visual-01-08.json`, остальные 14 — parent visual review.

Физический F8/мышь/F9 NOT_RUN, авторский Collision Studio export NOT_PROVIDED.
Strict server reopen report FAIL сохранён из-за одного external Mojang Yggdrasil
key-fetch timeout (три flagged lines), несмотря на фактическую успешную локальную
игру, exit0 и штатное сохранение. Qualified runtime acceptance относится только
к localhost offline тесту и не подтверждает внешнюю authentication/online mode.
