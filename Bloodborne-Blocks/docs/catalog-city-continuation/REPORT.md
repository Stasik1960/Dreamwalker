# Bloodborne Blocks: каталог, GUI и диагностика города

Статус: **каталог/GUI проверены; полная конвертация города FAIL**. Полностью готового мира пока нет. ZIP города является диагностической копией: его нельзя подменять рабочим сохранением или загружать/сохранять новым JAR, пока не устранены старые/неизвестные ID и composite failures.

Работа продолжена от TEST3, ветка `repair/composite-preserving-grid`, HEAD `3d07b4890b7612b390fc0d975990bfadd7402cfb`. Commit, push и перенос в main не выполнялись. Принятые logical/window ресурсы TEST3 не изменены; staging-проверка подтвердила сохранность всех ранее существовавших city definitions, geometry, meshes, mappings, reviewed wall и художественных ресурсов.

## Каталог и GUI

Дедупликация сравнивает установленную геометрию, разрешённые материалы, emissive/animation, физику, placement и все служебные состояния. Разные пути с одинаковым содержимым могут совпадать. Функционально разные и недоказанные пары остаются раздельными. BASE/ALT сохранены в registry и ресурсах; при будущем изменении встроенного art нужно повторно сгенерировать creative-equivalence proof. Внешний ресурс-пак сам по себе не пересчитывает статический каталог.

| Счётчик | Итог |
|---|---:|
| Registry BlockItem IDs | 3831 |
| Кандидаты художественных вариантов | 46673 |
| Видимые записи | 46430 |
| Доказанно скрытые эквиваленты | 243 |
| Основная вкладка: записи | 63 |
| Техническая вкладка: записи | 46367 |
| Зарегистрированные block states | 54016 |
| Исключённые placement states | 72 |
| Служебные состояния, свёрнутые в art-кандидатов | 7271 |

Служебный `architecture_part` не имеет BlockItem и исключён явно. Это отдельная registry-запись сверх указанного счётчика BlockItem IDs. Каждый кандидат покрыт собственной записью либо прямым доказанным redirect; циклы и цепочки запрещены. Runtime IDs и художественные ресурсы не удалялись. Четыре новых owner ID добавлены для доказанных исторических объектов с пустой storage-root, а не для оформления каталога.

GUI использует bounds конкретной выбранной baked-модели, центрирование и пропорциональное уменьшение до 84% слота. Кэш различает объекты моделей и обновляется при model reload. Мировые transforms, collision/outline и семь остальных режимов предмета сохранены. Математические проверки и выбор art PASS; графический клиент **NOT_RUN**.

## Свежая диагностика

Вход: `reference-inputs/latest-modded-world.zip`, SHA-256 `c517dfeb52c4d13bdbe90e02a93ac00416354eb89313a9d1377f24823af6d0e9`. Это подтверждённый MODDED backup; TEST3/beta.3 и vanilla-карта входом не служили. Результаты первой диагностики сохранены отдельно в `baseline/`; после конкретного исправления storage-root сделан новый проход с оригинального ZIP в отдельный каталог. Прерванные попытки в build не учитываются в итоговых числах.

`--atomic-owner-groups` теперь действительно включает проверенный shared-ownership путь. Preflight, root/root, foreign-block, membership, owner-limit и целостность транзакций сохранены. Independent fragment fallback и city-compat palette не включались.

| Единица измерения | До storage-root исправления | Финальный свежий проход |
|---|---:|---:|
| Ledger-транзакции | 26802 | 26804 |
| Восстановленные root-объекты | 50305 | 50365 |
| Восстановленные atomic groups | 3124 / 3238 | 3126 / 3238 |
| Отклонённые atomic groups | 114 | 112 |
| Уникальные целевые registry-семейства | 826 | 832 |

Root-объект — уникальная пара `(dimension, targetRoot)` итогового ledger. Семейство здесь — точный target registry ID; семантическая уникальность не заявляется. Группы, memberships, объекты и транзакции не складываются.

Причины оставшихся отказов групп:

| Причина | Групп |
|---|---:|
| `protected_owner_closure_incomplete` | 37 |
| `reserved_by_atomic_owner_group` | 1 |
| `shared_root_conflict` | 42 |
| `target_would_overwrite_foreign_block` | 29 |
| `whole_owner_runtime_mapping_missing` | 3 |

Protected/composite gate: **FAIL**, неполных protected-объектов **1979**, семейств с отказами **35**, недоказанных memberships **933**, foreign-conflict occurrences **0**. Это самостоятельные показатели проверки, не дополнительные группы. Отказы не означают разрешение перезаписывать чужие клетки; preservation ниже подтверждает отсутствие изменений вне ledger.

Helpers: **59270 проверено, 0 orphan**, PASS. Из отсутствующих в текущем registry исторических ID: **39996 ID / 27460260 блоков**. Неизвестные даже frozen legacy+v2 архиву: **23 ID / 33 блока**. Ещё 49 уже зарегистрированных ID остаются в converter unmatched (124315 блоков); наличие ID не считается доказательством корректного восстановления объекта. Полные списки приведены в `final/summary.json`.

Preservation: **PASS**; 170 нетеррейновых файлов сохранено; изменено 96812 клеток в 826 чанках, все изменения объяснены ledger. Независимый checker сравнил все 10 009 чанков / 1 003 216 896 блоковых ячеек с исходником и проверил resource fingerprints.

Второй проход: **0 конвертаций**, preservation PASS, 186 файлов побайтно совпадают, изменённых путей 0. Idempotence PASS не заменяет провал composite/registry gates.

## Проверки и оставшиеся ограничения

`check build logicalGameTest --max-workers=1`: PASS, 57 GameTest / 0 failures. В логах unittest: 231 тестов в 44 запусках; script-based invariant checks проходят дополнительно. Новый server GameTest покрывает все четыре пустых root, физику соседних клеток, ownership, rebuild и cleanup без orphan. Проверки выполнялись на Java 17 / Gradle 8.8, Minecraft 1.20.1, Yarn 1.20.1+build.10, Loader 0.16.10, Fabric API 0.92.9+1.20.1, Loom 1.6.12; версии зависимостей не обновлялись.

Точная Gradle-команда сохранена в `final/check-build.json`; все команды конвертации и сравнения — `final/*.command.json`, выходы — соседние `.log`. После синхронизации версии в fabric.mod.json выполнен дополнительный `assemble logicalGameTest --max-workers=1` — PASS, тот же набор из 57 тестов (не суммируется с первым запуском); команда и лог — `final/metadata-assemble.*`. Использованы `--offline --no-daemon`, существующий `tools/verification-direct-resources.init.gradle`, закреплённый JDK и bundled Python. Полные ledger/gate/residual proofs сохранены с gzip.

Очистка legacy IDs/ресурсов **не начата**, поскольку условие успешной полной конвертации не выполнено. Полный город не загружался в новый runtime: unresolved registry IDs сделали бы такую загрузку потенциально убыточной. Следующий шаг — конкретные оставшиеся группы из `final/residuals.json.gz`: доказать целые owner closures для membership/foreign/root конфликтов, отдельно доказать точную допустимую физику оставшихся неподдержанных owners. Без этих доказательств guards сохраняются. 23 неизвестных ID перечислены с количеством в summary; для их идентификации нужен соответствующий исходный mapping/art, существующий frozen архив их не содержит.

## Артефакты

- [bloodborne-blocks-2.1.0-repair-catalog.1.jar](../../releases/repair-catalog-city-1/bloodborne-blocks-2.1.0-repair-catalog.1.jar) — 37961697 байт, SHA-256 `4fdaa5a7bc4584f5ba737773f23714408e7442706c9cb3d961998b01e9ad165d`.
- [Bloodborne-City-Catalog-DIAGNOSTIC-NOT-READY.zip](../../releases/repair-catalog-city-1/Bloodborne-City-Catalog-DIAGNOSTIC-NOT-READY.zip) — 22821087 байт, SHA-256 `de2b79550ad706a15dde67863f5a7c398a79eb712565dce3a5dce2efbb7345b4`.
- [Bloodborne-Current-Art-Catalog-1.zip](../../releases/repair-catalog-city-1/Bloodborne-Current-Art-Catalog-1.zip) — 40410340 байт, SHA-256 `6819487a08f1f45b85f4b19318b5f62b7dae8f2ef79b188a6814dda4bce4c637`.

Экспорт art содержит все текущие JSON-модели и PNG/mcmeta, распакованные meshes, reference geometry/contracts/mappings, а также существующие ALT artist kit и template. Geometry/reference остаются данными для выравнивания, не местом изменения gameplay. Проверены каждый payload SHA-256, оба вложенных ZIP и побайтовое соответствие текущим assets. JAR ресурсы (17443 файлов) проверены против исходников; diagnostic world ZIP соответствует сохранённой проверенной копии.
