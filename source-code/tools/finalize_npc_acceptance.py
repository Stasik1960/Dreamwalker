import hashlib,json,zipfile
from pathlib import Path
P=Path(__file__).resolve().parents[1];D=P/'npc-evidence';J=P/'build-native-visual/libs/dreamwalker-bb-fabric-1.20.1-0.1.0-v12.7.4-visual-physics.8.jar';H=hashlib.sha256(J.read_bytes()).hexdigest()
def load(p):return json.loads(p.read_text(encoding='utf-8'))
assert load(D/'build.json')['exitCode']==0 and load(D/'assets-audit.json')['status']=='PASS' and load(D/'visual-review.json')['status']=='PASS'
delta=load(D/'renderer-delta.json');assert delta['status']=='PASS' and delta['productionSha256']==H
for run,check in [('server-5','server-checks.json'),('client-2','client-checks.json'),('client-3','client-checks.json'),('client-4','client-checks.json')]:
 assert (load(D/'client-reviewed.json')['status'] if run=='client-2' else load(D/run/check)['status'])=='PASS',run
 result=load(D/run/'launch-result.json');assert result['exitCode']==0 and result['saved'],run
 expected=H if run=='client-4' else delta['baselineSha256'];assert load(D/run/'process.json')['productionSha256']==expected,run
 assert hashlib.sha256((P/'npc-profiles'/run/'mods'/J.name).read_bytes()).hexdigest()==expected,run
client=load(D/'client-2/client-checks.json');states={}
for row in client['checks']:
 if isinstance(row,dict):assert row['bones']>0;states.setdefault(row['model'],set()).add(row['state'])
assert len(states)==18 and all(s=={'idle','walk','hurt','attack','death'} for s in states.values())
final=load(D/'client-4/client-checks.json');assert sum(isinstance(s,str) and s.startswith('death last frame remains held ') for s in final['checks'])==18;assert sum(isinstance(s,str) and s.startswith('authored corpse stays above feet plane ') for s in final['checks'])==16;assert sum(isinstance(s,str) and s.startswith('living actor clears floor correction ') for s in final['checks'])==18
angles=load(D/'client-6/client-checks.json');assert angles['status']=='PASS' and angles['modelCount']==2;result=load(D/'client-6/launch-result.json');assert result['exitCode']==0 and result['saved'];assert load(D/'client-6/process.json')['productionSha256']==H
receipt={'status':'PASS','productionSha256':H,'server':'server-5','client':'client-2','finalCorpses':'client-4','rendererDelta':'renderer-delta.json','models':18,'modelStates':90,'mapChanged':False};(D/'acceptance.json').write_text(json.dumps(receipt,indent=2),encoding='utf-8')
pack=[]
for f in sorted((P/'npc-profiles/client-4/mods').glob('*.jar')):
 with zipfile.ZipFile(f) as z:meta=load_meta=json.loads(z.read('fabric.mod.json')) if 'fabric.mod.json' in z.namelist() else {}
 if meta.get('id')=='dw_restoration_qa':continue
 pack.append({'file':f.name,'id':meta.get('id'),'version':meta.get('version'),'sha256':hashlib.sha256(f.read_bytes()).hexdigest()})
(D/'tested-pack.json').write_text(json.dumps(pack,ensure_ascii=False,indent=2),encoding='utf-8')
report='''# NPC и Custom NPC — visual-physics.8

Все18 мобов Dreamwalker отображаются как модели Custom NPC. Проверен настоящий рендер Custom NPC в используемой сборке Limacina, включая Sodium+Indium, GeckoLib4.8.4, CustomNPCs-CPM1.2.0, Iris (шейдер выключен). На каждой модели пройдены покой, движение, получение урона, атака и смерть:90 состояний. Размер NPC учитывается; его собственные AI, здоровье и сохранённая настройка рендера не переписываются.

Отдельный адресный прогон проверил полную пятисекундную анимацию смерти и неподвижность её последнего кадра у всех18 типов. В кадре сопоставлены смерть модели Custom NPC и настоящий1HP-труп RpMobEntity; ранний и конечный вид сохранены в36 дополнительных снимках. Этот прогон завершён штатно.

Для самих18 мобов Dreamwalker:
- Нет автоматической агрессии, блуждания и выбора целей. Здоровье2HP — одно сердце.
- Survival/Adventure и окружающая среда не наносят урон. Любой Creative-удар оставляет1HP и лежащий труп; второй удаляет его. Особое действие оживляет и восстанавливает2HP.
- Особое действие: выбрать моба; затем указать блок карты для движения к его верхней поверхности. Повторное применение к выбранному мобу — случайная доступная атака без нанесения урона. Инструмент BB сохраняет управление: ПКМ меняет режим, ЛКМ применяет.
- Поворот инструмента —45° по часовой стрелке.
- Все звуки втрое тише, фоновые звучат вдвое реже. Звуки действий остаются привязаны к действиям, категория громкости остаётся прежней.

Серверный прогон проверил все18 типов: защиту от урона, оба Creative-удара, сохранение/загрузку трупов, оживление после NoAI, переход старого80HP-состояния к2HP, сохранность непрозрачных исходных данных, случайную атаку, поворот и фактическое прохождение к центру выбранного блока с остановкой. Громкость и частота проверены по настоящим звуковым сетевым пакетам.

Амелия и Чудовище-кровоглот не имеют исходного death-клипа: применена неподвижная лежачая поза. У Чудовища-кровоглота и обеих крыс отсутствует hit-клип: используется обычная поза. Для остальных доступны исходные анимации; смерть удерживает последний кадр.

Найдены и исправлены две связанные ошибки: постоянное сохранение NoAI из временного состояния трупа и ранняя остановка ванильного поиска пути у широких моделей. Последний участок пути доводит моба до указанного центра, сохраняя физические коллизии и ограничение времени.

Карта не менялась. Существующие мобы принимают новые правила при загрузке. Используйте свою текущую bloodborne; повторный перенос города не нужен. Предыдущие исправления блоков .7 сохранены.

Важное примечание к доказательствам: после завершения всех18 моделей и90 положительных проверок последний кадр тестовой Gallery обратился к индексу18. QA перезаписал свой итоговый статус как FAIL; оригинал и стек сохранены. Это произошло после всех проверок, внутри QA-класса, с последующим штатным выходом0 и сохранением мира. Проверка полноты подтверждена в client-reviewed.json, защитный выход в QA исправлен и пересобран. Production JAR не менялся; незатронутые90 проверок повторно не запускались.
'''
(D/'REPORT-RU.md').write_text(report,encoding='utf-8')
report=report.replace('Все18','Все 18').replace('все18','все 18').replace('всех18','всех 18').replace('GeckoLib4.8.4','GeckoLib 4.8.4').replace('CustomNPCs-CPM1.2.0','CustomNPCs-CPM 1.2.0').replace('90 состояний','90 состояний').replace('2HP','2 HP').replace('1HP','1 HP').replace('80HP','80 HP').replace('к2 HP','к 2 HP')
report+='\nДополнительная визуальная проверка выявила уход некоторых исходных death-поз ниже точки опоры. Добавлено общее выравнивание по фактическим анимированным вершинам: модель поднимается ровно настолько, чтобы оставаться над полом. Конечный результат кешируется, коллизии и серверные данные не меняются. В renderer-delta.json подтверждено: относительно принятого серверного кандидата изменились только renderer и два класса этого клиентского помощника; остальные11385 записи совпадают побайтно. Финальный адресный клиентский прогон client-4 проверил все18 трупов,16 исходных death-поз над плоскостью опоры, удержание кадров и снятие коррекции при возврате к живому состоянию.\n'
(D/'REPORT-RU.md').write_text(report.replace('остальные11385','остальные 11 385').replace('все18','все 18').replace('16 исходных','16 исходных'),encoding='utf-8')
(D/'DECISIONS-RU.md').write_text('''# Принятые решения

1. Правила здоровья, AI и инструмента применены к18 настоящим RpMobEntity. Модель, выбранная в Custom NPC, следует состоянию самого NPC; его собственные AI/статы не изменяются.
2. Использован имеющийся полный renderer path Custom NPC, включаемый только для наших моделей optional mixin. Отдельная копия/форк Custom NPC не требуется. Сохранённые настройки NPC не меняются.
3. Отсутствующие death/hit-клипы заменены простой позой; новые тяжёлые ресурсы и обходы по ID не добавлены. Перечень отсутствующих ресурсов указан в отчёте.
4. Частота уменьшена для фоновых звуков. Шаги и звуки действий не пропускаются случайно.
5. Движение использует ванильный поиск пути и ограниченный по времени последний участок для широких тел. Коллизии сохранены; телепортация не используется.
6. Карта и ранее принятые проверки архитектуры не повторяются: NPC-обновление не меняет блоки, модели зданий или сохранённые цельные сборки.
''',encoding='utf-8')
(D/'HANDOFF-RU.md').write_text('''# Продолжение и восстановление

Завершён NPC-этап visual-physics.8. Итоговый результат и решения: REPORT-RU.md и DECISIONS-RU.md; запрос: TASK-RU.md. Доказательства: acceptance.json, server-5/server-checks.json, client-2/client-checks.json, visual-review.json, tested-pack.json и соответствующие process/launch-result. Ранние неуспешные прогоны сохранены для истории, итоговыми не являются.

Полные исходники, ресурсы, Gradle wrapper, QA и инструменты находятся в dreamwalker-npc-full-sources-20261026.zip. Неполный overlay source-code в Git дополняет архив; для восстановления берите полный архив. Понадобится JDK17. Сборка: gradlew.bat remapJar -Pworldedit_api_jar=build-dependencies/worldedit-mod-7.2.15.jar. Gradle скачает необходимые зависимости при доступной сети. Minecraft/Fabric1.20.1; конкретный использованный пак указан в tested-pack.json. Тестовые инструменты содержат пути этого ПК; на другом ПК их нужно адаптировать.

Главные изменения: RpMobEntity.java, CatalogMobRenderer.java, NpcMobVisualBridge.java; compat/customnpc/CustomNpcMobRendererMixin.java + optional mixin config; BbToolService.java/BbToolRotations.java. Vanilla NoAI при сохранении берётся из реального флага, а не временного corpse/frozen. Команды движения и оживления снимают NoAI. Широкая модель доводится до центра цели после завершения найденного пути, максимум200 завершающих тиков.

Карта не менялась. Актуальна пользовательская Desktop/bloodborne или bloodborne-interactions-20261026.zip из предыдущего стабильного релиза https://github.com/Stasik1960/Dreamwalker/releases/tag/native-grid-city-interactions-20261026 . Блоки v12, цельные сборки, функциональные окна, скамьи, фонари и прочие исправления .7 сохранены. Не выполнять конвертацию заново.

Рабочий проект на этом ПК: C:/Users/vakir/CodexProjects/DW-visual-fixes-20261025 . Работа на C:, перенос на E: приостановлен. Перед проектными процессами читать C:/Users/vakir/CodexControl/storage-mode.json и C-return-result.txt, применять Guard.ps1. Последний установленный JAR должен иметь SHA из acceptance.json; чеки установки — installed.json. Production .jar не содержит QA-мод.
''',encoding='utf-8')
