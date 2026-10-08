# Review V8: prototype.3

Версия `0.1.0-prototype.3`; SHA256 `6cf52a3c91426e9ec3cf050d6949b94ebcc49e6168b6f497bb75aebacf174316`.

Первый набор PARTIALLY_ACCEPTED; исправленная версия READY_FOR_REPEAT_USER_REVIEW / PENDING_USER_REVIEW. Полное задание NOT_READY_FULL_TASK. Деревянные панели, объёмное RP-дерево и верх плоского дерева сохранены. Нижний variant1 остаётся отдельно помеченным предложением. Массовая генерация и конвертация города ждут приёмки пользователя.

| Проверка | Текущий результат / граница |
|---|---|
| Java17 build / native GameTest / транзакции | PASS76/76 +19; Attempt20 |
| Новая сцена, save, production-only reopen | PASS91 roots/29 composite UUID/695 unique helpers/132 native fixtures |
| Реальная новая установка в83 выбранных server-mod JAR и reopen | PASS91; строгие UUID/typed NBT/ledger/native fixtures |
| Минимальный ordinary client | PASS resources/world/save/exit0; actual mounted vertices/item models |
|109 выбранных client-mod JAR + Kappa5.2/settings42 | PASS startup/resources/active pipeline/effective options/save/exit0; один совместный прогон |
| ALT-only resourcepack | PASS load/source models/world/save/exit0 |
| Ограниченный исходный фрагмент | PASS41 objects/110 members/15 RP/2 lights; save/reopen/persisted no-op |
| Исходные данные | PASS8 original input SHA unchanged;707 RP source resources byte-exact |
| Пятизначные номера |107 TEMP assignments; finalId null; helper affiliation; debug aliases |
| Физика / selection / helpers / состояния | Отдельные before/after counts +1280 native cases; actual scene counts отдельно |
| Измерение CPU | Ограниченный same-JVM paired microbenchmark; не FPS/TPS/heap/город |
| Ручные вид/удобство/Creative UI/игровая приёмка | PENDING_USER_REVIEW; PASS техники не заменяет её |
| Два клиента / все RP формы, рычаги, два фонаря | NOT_RUN |
| Полный каталог, окончательные ID, конвертация города и галерея | NOT_READY_FULL_TASK; массовая работа ждёт приёмки |

Исходный полный мир/установка/конфигурации/шейдеры пользователя не изменены. Все игровые прогоны и derived copies находятся под build/. В source checkpoint сохранены код, инструменты, документы, raw результаты и предыдущие неудачные проверки.
