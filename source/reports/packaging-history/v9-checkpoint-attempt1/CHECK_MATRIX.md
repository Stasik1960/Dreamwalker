# Матрица повторной приёмки V9

Production `0.1.0-prototype.4`, SHA256 `ed43404d01b2b861a378bdb7dae6d57f36cccd6554461cfaeafbb8bccc82e1d8`.

| Проверка | Текущий результат / граница |
|---|---|
| Java17 / native GameTests / транзакции | PASS 105/105 + 20; точный текущий список методов проверен |
| Новая обычная установка → gameplay restart → production-only save | PASS 102 roots / 24 UUID / 132 native fixtures / 18 RP |
| Полный выбранный server modset | PASS отдельное новое построение → actual gameplay restart → production-only reopen |
| Рычаги и строительные инструменты | PASS реальные callbacks, исходная задержка70 ticks, UUID-связи, pending и повтор после actual restart |
| Два Hunterlamp | PASS штатная регистрация/list packet, A→B и B→A survival endpoints после actual restart; ручной GUI не проверен |
| Automatic lighting новых Hunterlamp | UNRESOLVED_NEW_LAMP_LIGHTING: original Forge activation создаёт light9; ordinary port не выполняет light writes; source2 technical cells отдельно сохранены |
| Обычный минимальный клиент | PASS resources/world/save/normal exit; ручной вид и удобство PENDING_USER_REVIEW |
| Встроенная диагностика | PASS_CURRENT_ARTIFACT_DIAGNOSTICS_ACTUAL_RUNTIME_OFF_ON_REENTER_EXPORTS; actual default60/off-on/saved reentry/server-client session ZIP; GPU/FPS attribution не заявлены |
| Полный обычный клиент | PASS_CURRENT_PROFILE_WORLD_SAVE_NORMAL_EXIT |
| Шейдеры | PASS_ACTIVE_IRIS_PIPELINE_NORMAL_SAVE_EXIT_MANUAL_VISUAL_PENDING; визуальная fidelity PENDING_USER_REVIEW |
| ALT клиент | NOT_RUN_CURRENT_ARTIFACT |
| Исходный ограниченный фрагмент | PASS41 objects/110 members, production reopen и persisted no-op; не полный город |
| TEMP типы | 117 строк включая reserved; finalId null; прежние ID не переиспользованы |
| Оригинальные входы и тексты | PASS8 SHA256 unchanged; TASK/user text не переписаны |
| Полное задание | NOT_READY_FULL_TASK; полный семантический каталог/частотные final IDs/город/галерея не завершены |
| Ручная приёмка / два клиента / производительность города | PENDING_USER_REVIEW / NOT_RUN / NOT_RUN |

Технические PASS относятся только к явно выбранному текущему JAR и перечисленным isolated worlds. Ранее принятые дерево и деревянные панели сохранены; новое оформление/физика/удобство ждут приёмки. Woodgate: исходный Forge reference физически empty; текущая простая solid leaf plane — явно предлагаемая gameplay физика, не утверждение тождества. Trapdoor91088 остаётся статическим исключением и не получает выдуманной функции. Native UP лестницы: TOP slab/STONE/предыдущая секция поддерживаются; BOTTOM slab и fence с дробной высотой основания явно отвергаются без расхода предмета. Дробный вертикальный монтаж native лестницы пока не реализован; боковая частичная опора проверяется отдельно. Массовая конвертация не запускалась.
