# Candidate12: последовательность повторной проверки

Пока это план, а не результаты нового JAR. Candidate11 `8dd5…` остаётся историческим: его миры, обычные игровые действия, сохранение/выход и строгий Kappa-probe прошли. В полной сборке подтверждён дефект телеметрии: метаданные вытесняли все timings из18/18 пакетов. [Отчёт исключений](../reports/REVIEW_V9_CANDIDATE11_TELEMETRY_EXCLUSIONS.json) сохраняет настоящие PASS и сырые результаты, не превращая их в функциональные FAIL. Full10 действительно подтвердил активный Iris, счётчик0→662 и42 effective getters; эти результаты не проверяют будущий SHA.

После фактической сборки Candidate12 нужны новый SHA, чистый Native Attempt12/core20 и byte-identical QA13. Затем статические art/RP/QA-аудиты, FINAL4 из FINAL3 и свежий preload. Источник сохраняет41 объект/110 ячеек,158 non-member precondition entries и275 проверок всего manifest,7 движений/6 descriptor hashes; меняется production binding. Original input и прежние FINAL1–3 не перезаписываются.

Команды подготовки источника выполняются один раз после получения настоящего frozen12 JAR:

```powershell
python tools/prepare_source_review_v9_plan.py --from-plan reports/FIRST_SET_MIGRATION_PLAN_V9_FINAL3.json --output reports/FIRST_SET_MIGRATION_PLAN_V9_FINAL4.json --jar build/frozen-artifacts/v9-attempt-12/dreamwalker-bb-fabric-1.20.1-0.1.0-prototype.4.jar
python tools/prepare_source_review.py --name source-review-prepared-v9-final4 --plan reports/FIRST_SET_MIGRATION_PLAN_V9_FINAL4.json --report reports/SOURCE_REVIEW_PREPARATION_V9_FINAL4.json --physics-reference reports/SOURCE_PHYSICS_CLIENT_SWEPT_ACTUAL.json
python tools/verify_source_review_preparation.py --report reports/SOURCE_REVIEW_PREPARATION_V9_FINAL4.json --output reports/SOURCE_REVIEW_PRELOAD_INDEPENDENT_V9_FINAL4.json
```

[Binder](../tools/bind_review_v9_candidate12.py) требует настоящий `--expected-sha`, frozen12 JAR, QA13, Attempt12 XML/log, FINAL4/preload и существующий accepted EULA. До выполнения этих условий текущие binding не меняются. `--replace-current` явно разрешает замену только текущего command JSON; его полные прежние байты сохраняются в history. Binder сам не запускает игру, Gradle, world-валидатор или упаковку.

После binder очередность actual runtime:

1. Обычные minimal/full серверы: свежий suffix `release9`; отдельно AUTHOR, gameplay REOPEN, production-only REOPEN и четыре независимые проверки.
2. FINAL4 источник: `release9`; три серверные фазы, четыре проверки и точный архив. Деревянное source-окно по-прежнему обязано атомарно отказать новому твёрдому пересечению кирпичей; дверь обязана открыться и пропустить игрока.
3. Чистые diagnostics OFF/ON/REENTER: серверный `release9`, default60. Во время matched performance работы с декодированием миров/ZIP/хешированием не выполняются параллельно.
4. Минимальный клиент и запись после повторного входа: `release10`, diagnostic20s,100ticks в каждом окне, восстановление реального исходного режима/предмета/ячеек/ledger.
5. Строгий полный клиент Kappa: `release11`, heap6 ГБ,111 физических JAR =109 выбранных + production + QA; shader42 marker, diagnostic60s и прежние100ticks окон. Раннее ON-окно и сохранение после него помечаются своим фактическим временным охватом; последующие active batches относятся к более позднему участку сеанса. Успех6 ГБ не доказывает совместимость4 ГБ.

Новая проверка телеметрии должна подтвердить реальные собственные timings и счётчики отброшенных секций, обычный item→server ACK→renderer ACK с тем же UUID/операцией, целостное удаление временного объекта и локальные ZIP. Сокращение сериализаций не объявляется доказанным улучшением FPS до актуальных raw OFF/ON/OFF измерений. Все runtime PASS остаются PENDING до настоящих результатов. Ручная приёмка, полный каталог/город/галерея и финальные номера ещё не готовы.
