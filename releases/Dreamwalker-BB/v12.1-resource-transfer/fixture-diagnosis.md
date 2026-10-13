# Диагностика размещения старого GameTest fixture

`remaining-final-4` завершил все проверки новых объектов, но старый
`blockedToolCloseKeepsIntentAndFlagsUntilSafeWithoutRepeatReversal` остановился
при инициализации `door_1`, до проверки механического поведения. Ошибка:
`real fixture spawn door_1 at BlockPos{x=225,y=-58,z=1}`; лог
`build/remaining-transfer/remaining-final-4/gradle.log`, строки 245–270.
В `remaining-final-3` та же старая проверка прошла.

Старая `MechanismRulesGameTests.object` загружает только `world.getChunk(root)`
и сразу требует одновременно `spawnEntity(rp)` и `getEntity(UUID)==rp`.
Относительное положение двери `(9,2,1)` даёт origin `(216,-60,0)`.
Origin расположен в chunk `(13,0)`, дверь — уже в соседнем `(14,0)` за пределом
маленького `EMPTY_STRUCTURE`. Mock player создаётся возле общего spawn мира,
поэтому не гарантирует tracking удалённого fixture.

Независимая проверка mapped bytecode Minecraft 1.20.1 подтверждает различие:
`ServerEntityManager.addEntity` добавляет сущность в section/UUID set и может
вернуть `true` при `shouldTrack=false`; запись в `EntityIndex` появляется лишь
в `startTracking`. `ServerWorld.getEntity(UUID)` читает именно этот lookup.
Загрузка `WorldChunk` сама по себе не гарантирует немедленный entity tracking.
Это объясняет наблюдаемое падение строгого fixture assertion при изменении
раскладки общего набора; сама ветка старого поведения ещё не была выполнена.
Результаты чтения находятся в `build/remaining-transfer/fixture-diagnosis/`:
`ServerEntityManager.txt`, `ServerWorld.txt`, `TestRunner.txt`,
`TestFunctions.txt`, `status.json`. Скрипт `tools/remaining_diagnose_fixture.py`
только читает JAR и сохранённые тестовые регионы. Сохранённый lever в chunk
`(13,0)` подтверждает origin; отсутствие сохранённой двери не используется как
доказательство конкретного результата `spawnEntity`.

TestRunner сначала расставляет структуры, затем вызывает `@BeforeBatch`, затем
запускает тесты. Новый принадлежащий этому этапу hook в
`RemainingTransferGameTests.preloadExistingFixtureBorder` заранее загружает и
удерживает 25 соседних тестовых chunks `(12..16,-2..2)` перед `v12_groups`.
Старые test methods, RP-объекты, production-код, модели и исходная карта не
изменяются. Результат повторной проверки должен оцениваться по отдельному
последующему GameTest отчёту, а не выводиться из этой диагностики.

В `remaining-final-6` похожее ограничение проявилось в другом старом batch:
`MechanismLinksGameTests.survivalHoldingOrdinaryItemStillUsesLever` не открыл
дверь после 70 ручных вызовов `lever.tick()`. В его неизменённом helper нет
загрузки chunk и проверки `getEntity`; результат `lever.addLink(door)` также не
проверяется. Сохранённый failed lever находится в `(313.5,-58,1.5)`, chunk
`(19,0)`, и имеет пустой список `Links`. Относительные позиции этого test дают
origin `(312,-60,0)` и дверь `(321.5,-58,1.5)` в соседнем chunk `(20,0)`.
Первый hook `(12..16,-2..2)` этого batch не покрывает. Игнорируемый результат
link и необходимость world lookup объясняют, почему отсутствие доступного
target может выглядеть как ошибка delay; изменения старой 70-tick логики не
обоснованы. Предложена только новая инфраструктура `@BeforeBatch` для
`v9_mechanisms`: загрузить border `(18..22,-2..2)` до запуска fixtures.
Положительный результат этой гипотезы требует нового полного прогона с тем же
production SHA; прошлый `remaining-final-5` PASS не подменяет final6 FAIL.

Итоговый `remaining-final-7` с обоими новыми border hooks завершился **PASS**:
63/63 GameTests, 0 failure, в том числе обе ранее остановившиеся старые проверки.
Старые test methods и RP production-код не менялись. Доказательство:
`build/test-results/gametest/TEST-remaining-final-7.xml`,
`build/remaining-transfer/remaining-final-7/result.json`; production SHA
`9797fb4bb4b6c4103b60b994fb0c4ad4f88c46bc7a19de42eb596e7f0c57e117`.
