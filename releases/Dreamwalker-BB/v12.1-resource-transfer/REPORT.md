# Dreamwalker BB V12.1 — оставшийся перенос ресурспака

База V12: commit `8b770a03de21554a21c91700a1c1c6fe86c894c4`, ветка
`codex/bb-v12-tool`. Ресурспак v15 и карта v16 сохранены побайтно; карта только
читалась для определения составных объектов. Галерея не создавалась и не менялась.

Combined JAR: `dreamwalker-bb-fabric-1.20.1-0.1.0-v12.1-resource-transfer.jar`.
SHA-256: **`9797fb4bb4b6c4103b60b994fb0c4ad4f88c46bc7a19de42eb596e7f0c57e117`**.
Среда: Minecraft **1.20.1**, **Java 17**, Fabric Loader **0.16.10**,
Fabric API **0.92.9+1.20.1**, GeckoLib **4.4.9**. Архитектура и RP собраны в один
мод; отдельный проверочный addon и gallery/GameTest-код в production JAR не входят.
Исходники передаются полным source ZIP с Gradle Wrapper, Java, ресурсами,
инструментами и отчётами. Сборка из каталога мода: `gradlew.bat build` под Java 17;
полный source ZIP содержит все исходники архитектуры и RP, а также подробные
таблицы, доказательства и отчёты в `docs/remaining-transfer`.

Добавлены **1047 самостоятельных типов / 1805 состояний**, включая законченные
неиспользуемые artworks, исходные pitch/UV-lock варианты, материалы и целые двери,
ворота, окна и фиксированные формы multipart. Каждому типу назначен отдельный
свободный пятизначный номер, варианты предложены предметами с явным состоянием.
Геометрия, UV, прозрачность, текстуры, dependencies и авторские element rotations
перенесены в собственный namespace; исходный ресурспак для мода не требуется.
Обычные ванильные блоки сохраняют штатные ресурсы. Таблица
[state-correspondence.csv](state-correspondence.csv) охватывает **3589 исходных
применений моделей**; [model-correspondence.csv](model-correspondence.csv) разделяет
готовые объекты, части, шаблоны, дубликаты, исключения и дефекты.

| Проверка конечного SHA | Результат | Доказательство |
| --- | --- | --- |
| Combined build и 21 transaction core check, Java 17 | PASS | `build/remaining-transfer/remaining-final-7/gradle.log` |
| GameTests: 63/63, из них 58 прежних и 5 новых | PASS | `build/test-results/gametest/TEST-remaining-final-7.xml` |
| Все 1805 новых состояний, 8 поворотов, установка, pick/UUID, NBT, соседи, удаление, двери | PASS, серверные GameTests | Тот же XML; не подменяет клиентский/ручной проход |
| Исходные модели, textures, UV wrappers, dependencies и сохранность V12 | PASS | [artifact-validation.json](artifact-validation.json), [preservation-results.json](preservation-results.json) |
| Клиентский bake: 1482 part models, 1805 предметов, 7220 BASE/ALT closed/open poses; vertex RGB исходной палитры | PASS | `docs/remaining-transfer/evidence/runtime/client-baked.json` |
| Клиентские use/pick/attack, копирование/UUID, удаление и открытие; 15 выбранных случаев | PASS | `docs/remaining-transfer/evidence/runtime/client-network.json` |
| 30 настоящих framebuffer PNG, front/side для 15 случаев | PASS, просмотрены | `docs/remaining-transfer/evidence/runtime/visual-review.json`; не полный визуальный проход 1805 вариантов |
| Сохранение, реальный перезапуск и повторный вход: 15 объектов, 94 carrier/ledger ячейки, UUID и typed NBT | PASS | `docs/remaining-transfer/evidence/runtime/saved-world-after.json`, `client-rejoin.json`; до/после полностью совпадают |
| Физический desktop input мышью/F8/F9 | **NOT_RUN** | Две попытки Windows capture завершились timeout |

Строгий отчёт повторного запуска сервера отдельно имеет **FAIL**: одна внешняя
попытка получения public key Mojang завершилась `Connect timed out` (три строки
ошибки одного события). Сам локальный сервер с `online-mode=false` загрузил мир,
принял клиента, завершился с exit 0 и штатно сохранил все измерения; клиентский
rejoin всех 15 прежних UUID/состояний прошёл. Узкая дополнительная проверка
`server-offline-runtime.json` сохраняет исходный FAIL и подтверждает только
локальную работу мода/мира. Доступность Mojang и online-аутентификация не
объявляются PASS; исходный launcher не менялся.

Все итоговые доказательства привязаны к SHA переданного JAR в
[runtime-verification.json](runtime-verification.json) и
[object-verification.json](object-verification.json). В таблице 3368 исходных
применений имеют PASS ресурсных, регистрационных, серверных и bake-проверок;
84 применения относятся к 15 проверенным клиентским случаям; 52 имеют
подтверждённое сохранение. Открытая поза двери проверена при use/copy, но
сохранение/rejoin выполнены в закрытой позе, поэтому её 32 OPEN-применения
имеют savedWorldCheck=NOT_RUN. Остальные
клиентские действия и физический ввод честно отмечены NOT_RUN. Эта выборочная
приёмка не означает визуальный/сетевой проход всех 1805 вариантов.

Существующие модели, текстуры, ID, состояния, RP и пользовательские исправления
проверены на сохранность. Общие изменения ограничены подключением новых объектов;
старые test methods и production-поведение не исправлялись. Два обнаруженных
ограничения загрузки удалённых старых fixtures устранены только новой
`@BeforeBatch` инфраструктурой отдельного GameTest мира; окончательный прогон
63/63 прошёл. История и доказательства: [fixture-diagnosis.md](fixture-diagnosis.md).

Намеренно удалённые ограды **90013–90017** и лестницы **90018/90019** не восстановлены.
Три исходных полностью прозрачных helpers явно исключены: glass, ladder и
glass-pane, суммарно 9 предварительно рассматривавшихся состояний. PNG alpha
всех их faces равен 0; номера **90535, 90626, 93191** оставлены в резерве.
[source-transparent-exclusions.json](source-transparent-exclusions.json) хранит
точные source PNG SHA; прозрачные части видимых целых остаются зависимостями.

Подтверждённые препятствия исходного содержимого остаются открытыми: **54 строки
дефектных моделей**, отсутствующие части цельной железной двери и parent высокой
diorite wall. Конкретно отсутствуют используемые textures `model_false/polished_diorite`,
`model_false/unlit_lantern`, `light_pressure_plate`, `model_false/window_4`, часть
моделей iron door/button/pressure-plate; есть неопределённые `#missing/#dirt/#plant`
и повреждённый `template_trapdoor_bottom.json`. Полные пути и затронутые состояния
записаны в таблицах; замены не придуманы. Две законченные jungle-trapdoor формы
перенесены с видимыми faces, но их исходный дефект только particle `block/texture`
отмечен отдельно. Отсутствующее авторское содержимое не объявляется перенесённым.

Авторский collision replacement export не предоставлен: его применение **не
заявляется**. Контракт точного ID/состояния сохранён. Новые default shapes получены
из source elements и отдельно проверены; это не реконструкция авторских коллизий.
Существующие исключения физики сохранены. Полный аудит:
[source-audit.md](source-audit.md); зафиксированный manifest SHA
`ba9979d997cc84876e3e681d71684ad20c7b07ee5e499d3fe1f8c78580b6ee67`.
