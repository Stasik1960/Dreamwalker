DREAMWALKER BB — ПОВТОРНАЯ ПРИЁМКА V9 / 0.1.0-prototype.4
Production SHA256: ed43404d01b2b861a378bdb7dae6d57f36cccd6554461cfaeafbb8bccc82e1d8
Исправленный набор PENDING_USER_REVIEW. Полное задание NOT_READY_FULL_TASK.

В отдельной Minecraft1.20.1 / Fabric / Java17 установке оставьте один новый combined Dreamwalker JAR. Он уже содержит RP; удалите прежний Dreamwalker и отдельный bloodborne-rp из этой тестовой установки. Точные проверенные зависимости лежат в KIT/mods. Отдельный QA addon для обычной игры не нужен.

Распакуйте worlds/First-set-review-v9-scene.zip в saves. Сцена: 102 архитектурных roots, 132 native fixtures, 18 RP экземпляров. Координаты и проверки: docs/REVIEW_V9_README.md, tools/first_set_scene_v9_input.json, tools/first_set_v9_gameplay_input.json.

Самостоятельный рисунок выбирается отдельным предметом с собственным TEMP ID. Поворот/монтаж/BASE–ALT/открытие/видимость собак относятся к экземпляру. Старый VARIANT инструмента сохранён в NBT, но смена самостоятельного исполнения им отвергается. Middle pick и drop возвращают соответствующий тип.

Строительный инструмент: ПКМ воздух переключает режим; Shift+ПКМ воздух отменяет выбранный рычаг. Режим LINK: выберите настоящий RP рычаг, затем фактический RP механизм или архитектурную дверь; CONNECTIONS показывает связи/pending; UNLINK разрывает выбранную связь; CANCEL отменяет выбор. Нужны Creative с правом изменять мир или OP2. Survival обычные дверь/рычаг/фонарь сохраняют gameplay взаимодействие. Рычаг использует исходную задержку70 ticks, woodgate — one-shot32 ticks без held/offhand очереди.

Встроенная диагностика выключена по умолчанию. OP2: /bb diagnostics start (60s), status, mark, stop, export; /bb debug и diagnostics snapshot сохраняют снимок. Команды, локальные журналы/ZIP и пределы измерений: docs/DIAGNOSTICS_V9.md (в KIT — DIAGNOSTICS-V9.md). Проверены только явно перечисленные текущие off/on/reenter и item/model цепочки; GPU time и процент нагрузки объекта по FPS не вычисляются.

Два Hunterlamp уже зарегистрированы с маршрутами A→B и B→A. Проверены реальные серверные list packets/travel endpoints после restart; ручное меню, клиентские клики и визуальное качество остаются для пользовательской приёмки. Respawn не изменён. Automatic light9 исходного Forge фонаря пока UNRESOLVED_NEW_LAMP_LIGHTING в новом ordinary port; две исходные technical light9 клетки source41 сохраняются отдельно. Рабочие travel и source-light preservation не доказывают automatic lighting новых фонарей.

Техника: native 105/105, core 20, новая установка и повторные сохранения в minimal/full server. Точные текущие статусы клиента/шейдеров/source41: reports/REVIEW_V9_CHECKPOINT.json. Исторические FAIL/PASS и исходные задания сохранены с собственными SHA. Полный город/каталог/финальные номера не завершены.
