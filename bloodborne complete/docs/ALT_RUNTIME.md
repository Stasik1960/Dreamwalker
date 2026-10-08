# Проверка ALT в клиенте

`tools/verify_alt_runtime.py --revision V7` воспроизводит адресную проверку без запуска новой игры. `reports/ALT_RUNTIME_V7.json` связывает точный production JAR, реальные wrapper/output/console/marker SHA и неизменённые bytes `ALT-example.zip` с наблюдениями обычного клиента.

Рабочий приоритет паков: `[vanilla, fabric, file/ALT-example.zip]`. Поздний пак переопределяет ранний. Исторический `CLIENT_FIRST_SET_ALT_V7.json` загрузил мир, но фактический порядок `[vanilla, file/ALT-example.zip, fabric]` отменял примерные override; наблюдаемых red_wool моделей было0. Его world-load PASS не считается применением ALT. Исправленный `CLIENT_FIRST_SET_ALT_ORDERED_V7.json` штатно загрузил мир, сохранился и завершился кодом0.

В исправленном запуске все32 состояния лестницы ALT variant0 (4 стороны × diagonal2 × source_clone2 × waterlogged2) имеют фактический baked quad sprite `minecraft:block/red_wool`. BASE-состояния в том же запуске и все192 состояния контрольного клиента без внешнего ALT-пака имеют0 red_wool. Это реальная ресурсная проверка замены текстуры.

Override ALT variant1 содержит parent исходного `wood_ladder_04`; исходный production ALT variant1 содержит parent `wood_ladder_03`. Проверены bytes override, parent04 с авторской геометрией и полная локальная PNG-зависимость. Историческая ORDERED-проверка подтверждала непустые модели/текстуры без извлечения вершин.

Дополнительный штатный запуск `CLIENT_FIRST_SET_ALT_VERTEX_V7.json` записал `quadVertexSha256` всех192 состояний лестницы. Проверка `tools/verify_alt_runtime.py --revision V7 --client-report reports/CLIENT_FIRST_SET_ALT_VERTEX_V7.json --require-vertex-comparison` дала PASS_ACTUAL_ALT_PACK_TEXTURE_AND_MODEL_REPLACEMENT: все32 ALT variant1 соответствующие позы имеют точно тот же fingerprint сырых baked vertices и sprite, что BASE variant2, и отличаются от BASE variant1. Сторона, diagonal, source_clone и waterlogged совпадают в каждой паре. Хеш включает шесть cull-face buckets и general quads с seed0, declaredFace/tint/shade/sprite и raw vertex int data; имя класса/модели/состояния в него не входит. Подробные32 сравнения и точные хеши wrapper/output/console сохранены в `ALT_RUNTIME_V7.json`. Production JAR не менялся; QA-addon расширен только ограниченной ресурсной диагностикой.

Примерный `textures/alt/prototype_ladder/test.png` присутствует в ZIP, но модели его не используют. Он не объявляется применённой текстурой. Автоматическая проверка ресурсов не заменяет пользовательскую визуальную/игровую приёмку; её статус PENDING_USER_REVIEW.
