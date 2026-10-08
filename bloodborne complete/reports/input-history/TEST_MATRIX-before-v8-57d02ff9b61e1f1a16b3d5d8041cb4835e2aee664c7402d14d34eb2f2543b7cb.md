# Матрица первого набора — v7

Production SHA256 `1c9e2788c79223072e89150c2fdbef159e6e226bb76a823cd1296ba9e43027f9`. Полное задание NOT_READY; приёмка пользователя PENDING.

| Проверка | Результат | Доказательство/граница |
|---|---|---|
| Размещение/45°/pick/remove/пересечения/опоры/NBT/rollback | PASS61/61 | FIRST_SET_GAMETEST_ATTEMPT_14.xml; настоящее движение/лучи/права/save-unload-reload |
| Транзакционное ядро | PASS19 | Gradle check/build log; Java17 |
| Исходная физика с Bloodborne6.0 | PASS измерений165/7 | SOURCE_PHYSICS_CLIENT_SWEPT_ACTUAL.json; actual original integrated client, не ручная игра |
| 68 новых объектов author/save/production reopen | PASS | SERVER_REVIEW_SCENE_AUTHOR/REOPEN_V7; FIRST_SET_REVIEW_SCENE_V7 |
| 41 исходных объектов/110members/15RP/2lights | PASS ограниченного scope | SERVER_SOURCE_REVIEW_*_V7; независимые SOURCE_REVIEW_*_V7; metadata/runtime differences отдельно |
| Повторный source вызов после сохранения | PASS no-op | Persisted UUID/signature/bindings, source/native/foreignBE сохранность |
| Минимальный ordinary client4GB, ресурсы/save/exit | PASS | CLIENT_FIRST_SET_MINIMAL_V7.json; actual baked models/sprites, vanilla atlas retention |
| Повторное открытие клиентского сохранения | PASS | CLIENT_FIRST_SET_REOPEN_V7.json; свежая derived copy |
| 83server/109client selected mods | PASS startup/save/exit | SERVER_FIRST_SET_FULL_V7; CLIENT_FIRST_SET_FULL_V7.json; не вся gameplay совместимость |
| ALT-only pack actual load/resources | PASS | CLIENT_FIRST_SET_ALT_VERTEX_V7.json; внешняя BASE архитектура не требуется |
| Shader client | PASS startup/resources/save/exit | CLIENT_FIRST_SET_SHADER_V7.json; визуальная/игровая приёмка NOT_RUN |
| RP ресурсы/registry namespace и standalone rejection | PASS | RP_PACKAGE_VERIFICATION/REVIEW_PACKAGE_VERIFICATION/SERVER_NEGATIVE_STANDALONE_RP_FIRST_SET_FINAL_V7 |
| Ручное изображение/Creative UI/проходы/лазание | NOT_RUN | Нужна пользовательская игра в комплекте |
| Два клиента/RP forms/levers/two-lamp travel | NOT_RUN | Требуется отдельный gameplay прогон |
| Полный каталог/город/пятизначные IDs/галерея | NOT_READY | TASK§8 gate; source defects/8legacy utility stacks остаются |

Спорные предложения: внешний hinge/open pose двери; твёрдый центр/боковое открывание окна; узкий ствол и проходимая крона/ветви дерева; собственная low-wall высота. См. docs/PROTOTYPE_REVIEW.md и объектные журналы. Исторические diagnostic JAR/сцены не устанавливать.
