# Передача CHECKPOINT: FULL карта и свет завершены

**Не готовая поставка, readyForDelivery не заявляется.** Сохранены полная карта, свет, проверка сохранности объектов и актуальные исходники. Final11-view route, отдельный reopen,12 actual PNG и final aggregate ещё не выполнены. После публикации checkpoint продолжать работу при достаточной памяти, не повторяя принятые этапы.

## Где находится комплект

Ветка `codex/native-grid-city-full-20261024`, tag checkpoint `native-grid-city-full-light-20261024`, release https://github.com/Stasik1960/Dreamwalker/releases/tag/native-grid-city-full-light-20261024 . Manifest: https://github.com/Stasik1960/Dreamwalker/releases/download/native-grid-city-full-light-20261024/bundle-manifest.json ; SHA256 `75e1ddf017d7ec04733e047c42ed55239be641755d518040074e4db99491565a`. Итоговый tag `native-grid-city-full-20261024` ещё не создан; его нельзя принимать за опубликованную готовую карту.

Восстановить ВСЕ тома существующим `github_delta_restore.py` по URL/SHA текущего manifest. База PRE-MAP: manifest SHA256 `79acd3fca57285fe15dcc062d575f2dd9dfb594ba67602076857f80d2d0a29c0`, SNAPSHOT SHA256 `8e009263a790d244fd7f35b9f688263919dd54f4bb8b95d40994ffcb8af1c8a6`. Нужные старые тома уже перечислены в новом manifest; вручную пересобирать цепь архивов не требуется.

Этот delta включает четыре overlays: `research/`, `source/tools/`, `source/src/nativegridrelight/`, `source/build/libs/`. Остальной полный проект берётся из связанной базы. Актуальная authority — release-restored `C:/Users/vakir/.codex/worktrees/3f07/DW-native-grid-city-20261022`, а не неполное Git source mirror. Предпочтительно восстановить тот же user root `C:/Users/vakir`: JSON/SQLite absolute bindings автоматически не переписываются. LocalC; перенос на E: остаётся приостановленным.

Карта: `research/city/bloodborne-native-grid-city`.
Мод: `research/runtime-history/production-full-ac56c8643cf46c245793191c0e5c099d95f755160b0a7109b2d6d6f5091ed0aa.jar`, SHA256 `ac56c8643cf46c245793191c0e5c099d95f755160b0a7109b2d6d6f5091ed0aa`. Minecraft1.20.1 Fabric и совпадающие зависимости сохранённого профиля.
Текущий source ZIP: `research/delivery/full-current-sources.final.zip`, SHA256 `f49a7c4947414159d3507f78b65bcbd730bea03abfdaaa96badd6987bf122d97`,238066951 bytes,170277 файлов, actual packaging PASS. Предыдущие source ZIP — история.

## Что принято, не повторять

- FULL compose/native queue завершены: 2153693 placements,167511 actual compositions. Catalog freeze `research/native-grid-plan/full/frozen-catalog.whole-retained-final.json`, SHA256 `16493e1a3e79554f72e68c192c4834600e8d152b98ec970d6c556d5eef69c71b`.
- FULL clone PASS; actual apply `20261023T235536` PASS:2153693 placements.
- Native light `20261024T005447` PASS:4370/4370;4 original assembly merges сохранены; accept-relight `20261024T011405` PASS.
- Final preserved coverage actual PASS,593s: `research/native-grid-plan/full/preserved-assembly-final-coverage.json`, SHA256 `142f79644b6c3f74ffbf9fce1e2449f9c0c1524d8c8a9c064ab7dd9d740588b8`.28 entries/76 poses/3801 known objects:277 compiled whole,3520 local original,4 merged runtime groups.4 actual groups/8 source owners, retentionMembers проверены.
- FULL JAR compatibility/TARGET и адресные install/save + отдельный reopen приняты до checkpoint; их exact reports сохранены. Старые223+83+5+17 функциональных групп, плотный пилот, census/plan и незатронутые QA не повторять. Честные старые FAIL/OOM не удалять из истории.

## Только оставшийся путь

Прочитать START и USER-PRIORITIES, затем верхнюю текущую запись восстановленных `research/RESUME-CURRENT-RU.txt`/`research/current-operation.json`. Позднейшие прямые указания пользователя имеют приоритет. Исторические вложенные next/activeProcess не являются новой командой.

При достаточной памяти: готовый11-view final route на уже сохранённом FULL мире → обычный save/exit обеих JVM → отдельный новый connection/reopen того же мира → root осмотр12 actual PNG и движения → visual/delivery reviews → final aggregate → финальный delta и публикация. Existing continuation находится в `research/source-drafts/FINAL-GAMEPLAY-CONTINUATION-RU.txt`; пропустить уже завершённые clone/apply/light/coverage. Не создавать новую карту и не мигрировать сохранённую повторно. Сейчас нет route/reopen/PNG/READY; отсутствие этих результатов нельзя заменить source PASS или снимками ожидаемых путей.

Default pinned reserve —9GiB physical available. Для этого functional final route/reopen разрешён отдельный admission: ≥7.5GiB physical available И ≥9GiB Windows available commit. Server heap4G/client heap3G, frozen recorder/profile остаются прежними. Текущие6.2GiB physical available ниже допуска; вопрос освобождения памяти ещё не разрешён. Реальные RAM/commit/admission будут записаны в actual bindings. Heap budget/snapshot не является process RSS; допуск не делает среду сопоставимой с прежним benchmark.

Baseline не завершил измерение, поэтому before/after FPS и процент ускорения не доказаны. Старый dense-pilot pair и будущий FULL functional route описывать раздельно. Не обещать производительность удалённого хостинга на основании этих данных.

## Сохранность и точные допуски

Исходные v12, функциональные данные, двери с необходимыми рабочими ячейками и ранее согласованные самостоятельные цельные объекты сохраняются. Не путать поздние import-v12.x номера с authority исходной v12. Локальные original16³ исключения сохраняют необходимые конструкции и данные, не весь район.

Два бюста [223,71,-925] и [-62,113,-733] оставлены в исходном формате с полными необходимыми частями/данными; новые whole templates для них не разрабатываются. Authority: `USER-AMENDMENT-20261024-RETAIN-TWO-WHOLE-BUSTS-RU.txt`.

Допустимы только10 decorative visual quads /4 exact fragment records; последние8 находятся в [30,23,-16] и [171,1,-897]. Коллизия, излучение и данные сохраняются. Authority: `research/native-grid-plan/full/user-exact-ten-visual-polygons-permission.json`, SHA256 `eebdc3da17a415d7a155806aead19aa57b72eec024936e6b46316dfa947baf3a`.

Пять vanilla bakes: сухой invisible light1/2/4/5 и одна сухая нижняя andesite slab. Вид/коллизии/излучение сохраняются; изменение уровня света, слияние полуплиты и заливка водой могут быть потеряны. Authority: `research/native-grid-plan/full/user-vanilla-static-permission-20261023.json`.

Исходное взаимное наложение коллизий разрешено при первом переносе. Ручная установка после break может штатно отклонить попытку восстановить его; коллизии ради этого не отключены. Все исходные authority и подробная история находятся в снимке; повторять расследования не требуется.

Процессы/сборки/Minecraft/запись мира выполняет root через Guard после чтения storage-mode.json/C-return-result.txt. Сохранение этого checkpoint — не пауза задания и не готовая поставка: после появления ресурсов продолжить недостающие final gates.

Фактически опубликованный checkpoint: https://github.com/Stasik1960/Dreamwalker/releases/tag/native-grid-city-full-light-20261024
Все тома: FULL-LIGHT-SNAPSHOT.json, SHA256 fbd5284aa8212ec91c4671cb7b6532e8ee2abffc480fb7ab493518c62e1f8e1b. Для следующей delta использовать этот snapshot и текущий manifest 75e1ddf017d7ec04733e047c42ed55239be641755d518040074e4db99491565a.
Внутри восстановленного research публикация ещё может быть помечена PREPARING: это срез до отправки. Фактическая публикация подтверждена этим Git snapshot; выполненные этапы не повторять. Игровая приёмка остаётся незавершённой.
