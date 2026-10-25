import hashlib,json
from pathlib import Path
P=Path(__file__).resolve().parents[1];D=P/'interaction-evidence';V='0.1.0-v12.7.4-visual-physics.7';J=P/f'build-native-visual/libs/dreamwalker-bb-fabric-1.20.1-{V}.jar'
def load(name):return json.loads((D/name).read_text(encoding='utf-8'))
H=hashlib.sha256(J.read_bytes()).hexdigest()
for file in ('server-1/server-checks.json','client-1/client-checks.json','final-binary-delta.json','preview-2/map-audit.json','preview-2/global-data-audit.json','client-3/client-checks.json','visual-review.json','apply-1/map-audit.json','apply-1/global-data-audit.json','apply-1/global-data-finalized.json','real-apply-preflight.json'):assert load(file)['status']=='PASS',file
for role in ('preview-2','client-3','apply-1'):assert load(role+'/process.json')['productionSha256']==H
assert load('client-3/launch-result.json')['exitCode']==0
assert load('apply-1/launch-result.json')['saved']
run=load('apply-1/map-apply.json');assert run['status']=='COMPLETE' and all(r['status']=='COMMITTED' for r in run['results']) and sum(r['windows'] for r in run['results'])==203
record={'status':'PASS','version':V,'productionSha256':H,'server':'server-1','finalClient':'client-3','acceptedFullClientVisuals':'client-2','preview':'preview-2','apply':'apply-1','windows':203,'groups':run['groups'],'unaffectedServerClasses':'final-binary-delta.json','allRequiredCodeAndMapChecksComplete':True,'remainingWork':['install accepted jar','package and publish verified release']}
(D/'acceptance.json').write_text(json.dumps(record,indent=2),encoding='utf-8')
audit=load('apply-1/map-audit.json');url='https://github.com/Stasik1960/Dreamwalker/releases/tag/native-grid-city-interactions-20261026'
report=f'''# Dreamwalker — интерактивные исправления .7

Готовая версия мода: `{V}`. Карта пользователя `C:/Users/vakir/Desktop/bloodborne` обновлена на месте. Заменены все203 узких окна [30952], в199 транзакционных группах; город целиком повторно не конвертировался.

* Исправлен краш при ударах/разрушении феи и тьмы: движку больше не возвращается пустая форма для частиц. Изнутри блоков мир не просвечивает. Выделение и разрушение по-прежнему только Creative.
* «Особое действие» BB переключает коллизии игроков у феи и тьмы. По умолчанию они проходимы; предметы и прочие сущности остаются без коллизий. Неподдерживаемые блоки игнорируются молча, ALT больше не переключается этим режимом.
* Добавлен шестой режим BB «Поворот по часовой стрелке»: 45° или90° согласно схеме объекта. Прежние номера режимов сохранены.
* Новая назначаемая клавиша применяет `bb no-collide` к блоку в основной руке. В управлении Minecraft, категория Dreamwalker BB. По умолчанию клавиша свободна. Сообщения: «Коллизии отключены», «Неподходящий блок»; при сбросе режима — «Режим без коллизий сброшен». Серверные права команды сохранены.
* Окно отделено от стены: одно окно, закрытые/открытые ставни по ПКМ, прозрачное стекло с обеих сторон. Причины прежней непрозрачности — alpha255 у стекла и сплошная стена за ним. Исправлены обе; внешняя кладка сохраняется. Старые типы остаются зарегистрированы для совместимости.
* Информационный габарит v12 приведён к объединению реальных коллизий игроков. Физические части, рендер и его дальность не изменены. Это не срезание геометрии деревьев или клеток.

Проверка: отдельный сервер, настоящий клиент с Sodium+Indium, затем полная фактическая Limacina-сборка с её настройками, сохранение и повторное открытие перенесённой копии, окно по координатам пользователя. Проверены частицы, клавиша и уведомления, режимы игры, повороты, переключение коллизий, ПКМ окна и PNG с обеих сторон. Шейдеры в функциональном прогоне выключены; производительность заново не замерялась.

Итоговый аудит основной карты: {audit['changedCells']} изменённых ячеек внутри{audit['allowedCells']} разрешённых; {audit['protectedBlockEntities']} защищённых блоковых записей, {audit['checkedRpEntities']} повторно сериализованных RP-записей. Ошибок, посторонних изменений и пустых owner shards —0. Глобальные связи/правила/группы/команды сохранены. Все67 прежних UUID цельных окон перенесены новым окнам. Ранее принятые фонари и цельные конструкции v6 не переделывались.

SHA256 JAR: `{H}`. Подробные решения — DECISIONS-RU.md. Полная карта, точные исходники, отчёты и контрольные суммы публикуются отдельным релизом: {url}. Устанавливать эту карту вместе с модом .7 на клиенте и сервере. Предыдущий стабильный релиз .6 сохранён.
'''
(D/'REPORT-RU.md').write_text(report,encoding='utf-8')
handoff=f'''# Восстановление Dreamwalker visual-physics.7

Все изменения кода и карты проверены и приняты. Production SHA256 `{H}`. Основная карта: `C:/Users/vakir/Desktop/bloodborne`. Проверки — acceptance.json; публикацию подтверждают final-release.json со статусом PUBLISHED и completion.json в GitHub diagnostics/2026-10-25-interactions. Этот архив исходников создан перед загрузкой релиза, поэтому сам receipt публикации смотрите в репозитории.

Итоговый релиз: {url}. Скачайте JAR .7, bloodborne-interactions-20261026.zip и dreamwalker-interactions-full-sources-20261026.zip; сверяйте SHA256SUMS.txt. Source ZIP содержит полный рабочий проект DW: исходники, все native meshes, ресурсы, gradle, инструменты, QA, доказательства и исходную задачу в history/TASK. Неполный Git-каталог source-code не заменяет полный архив. Ветка источников: codex/interaction-fixes-20261025 репозитория Stasik1960/Dreamwalker.

На новом ПК: распаковать source ZIP, установить JDK17, запустить gradlew.bat -Pworldedit_api_jar=build-dependencies/worldedit-mod-7.2.15.jar restorationBuild. Первый запуск требует обычной загрузки зависимостей Gradle/Fabric; в текущем окружении используются caches. Пользователю ставится только production JAR, не QA. Все203 окна уже заменены: мигратор повторно не запускать; повторной конвертации города и фонарей нет.

Рабочий исходный каталог этого ПК C:/Users/vakir/CodexProjects/DW-visual-fixes-20261025; доказательства interaction-evidence, тестовые копии interaction-profiles. Исходный пользовательский архив bloodborne-before-interactions.zip SHA256 9df2e6b5053dd86eb2bac093a372daf82afef2d6028d840ec89e8f5c75466087; его1888 файлов совпадают с публичным bloodborne-restored-20261025.zip предыдущего v6. Поэтому старый стабильный релиз также сохраняет полный вход этой задачи.

Прочесть TASK-RU.md, REPORT-RU.md, DECISIONS-RU.md. Принятые проверки: server1, client1 и final-binary-delta; preview2/map-audit, global-data; client2 с полной Limacina-сборкой и визуальный осмотр; apply1 и оба итоговых аудита. Старые незатронутые проверки v6 повторять не требуется. Мигратор и постобработка глобальных tombstones находятся только в QA/инструментах и в production автоматически не запускаются. Все существующие функциональные данные проверялись перед записью и после сохранения.

Активный диск C:, старый перенос E: PAUSED. На этом ПК читать CodexControl/storage-mode.json, C-return-result.txt и использовать Guard.ps1 Check/Operation. Не возобновлять E: и не удалять проекты при очистке. Не следовать reparse points. Пользователь разрешил самостоятельные разумные компромиссы; сохранение v12, ручных правок и согласованных цельных конструкций обязательно.

Клавиша no-collide назначается пользователем в управлении; по умолчанию свободна. Новый Special не переключает ALT; для поворота есть шестой режим. Окна переключаются обычным ПКМ. Известных незавершённых изменений кода/карты по текущей задаче после acceptance нет; техническое завершение установки/публикации фиксируется в completion.json.
'''
(D/'HANDOFF-RU.md').write_text(handoff,encoding='utf-8')
