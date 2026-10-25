import json
from pathlib import Path
P=Path(__file__).resolve().parents[1];D=P/'restoration-evidence'
def get(name):return json.loads((D/name).read_text(encoding='utf-8'))
for name in ('server-4/checks.json','client-3/client-probe.json','client-5/post-fixture.json','final-map-audit.json','frozen-resources-audit.json','visual-review.json'):assert get(name)['status']=='PASS',name
H=get('apply-process.json')['productionSha256']
for name in ('server-4/process.json','client-3/process.json','client-5/process.json'):assert get(name)['productionSha256']==H
ready={'status':'READY','productionSha256':H,'server':'server-4','client':'client-3','postFixture':'client-5','inputWorld':'C:/Users/vakir/Desktop/bloodborne','sourceProject':str(P),'remainingRequiredWork':'Install verified JAR, package full sources and publish final draft release'}
(D/'ready-to-release.json').write_text(json.dumps(ready,ensure_ascii=False,indent=2),encoding='utf-8')
world=get('world-after.json');audit=get('final-map-audit.json');resource=get('frozen-resources-audit.json')
report=f'''# Dreamwalker visual-physics.6 — отчёт об исправлениях

Обработана именно вручную изменённая карта пользователя `C:\\Users\\vakir\\Desktop\\bloodborne`. Новый перенос города с нуля не выполнялся. Мод: `0.1.0-v12.7.4-visual-physics.6`, SHA256 `{H}`.

- Исправлено восстановление компактных блоков после разрушения: общий путь для всех таких состояний, creative/survival и отмена события BEFORE проверены.
- Собраны 172 полных фонарных столба в 35 точных вариантах. 562 фонаря восстановлены отдельными объектами; в модель столба они не включены. Исходные блоки деталей остались в моде. Все734 замены прошли без исключений и откатов, с проверкой точной совокупной коллизии и соседних данных; у столбов совпала непрозрачность ячеек.
- Фонарь звучит стандартным звуком Minecraft и покачивается после касания или взаимодействия пустой рукой. Качание затухает за2.6с, повторный звук ограничен35тиками. Нет постоянного тикера каждого фонаря и нет частиц. Физика сохраняет исходную неподвижную форму.
- `//set hand` сохраняет NativeVisualIndex и художественный BlockStateTag. Проверены визуалы14/25636, вариант14, undo/redo, а также обычные setBlocks для декорации, скамьи и тьмы.
- Исправлена причина исчезавшего стекла окна `[-25,-56,-920]`: непрозрачная рамка отделена от настоящего translucent-стекла. Общая классификация учитывает реальную альфу активной текстуры при загрузке ресурсов. Стекло видно в клиенте с трёх проверенных ракурсов.
- У Tree1/RP-наведения отбрасывается широкое предварительное попадание, если точный луч промахнулся. Проверены76RP-моделей:23 выступавших за модель резервных collider статической декорации ограничены её визуальными границами. Авторские формы, анимированные и функциональные объекты сохранены. У8механизмов оставлены прежние функциональные габариты, включая45блоков лифта; их изменение вместе с механикой в эту работу не включалось.
- Удалено2132 доказанно пустых исходных файла. После сохранения дополнительно убраны7 вновь созданных пустых файлов entities. Итог: **{world['files']} файлов вместо3934**. Пустой owner shard удаляется вместе с индексной ссылкой; массовая запись пустых shards прекращена. Minecraft может заново создавать отдельные технические файлы своих регионов.

Сверка с исходной копией: изменены только **{audit['changedCells']}** клетки в зарегистрированных зонах замены, вне их изменений нет. Данные **{audit['protectedBlockEntities']} прежних block entity и {audit['rpEntities']} RP-объектов** совпали с исходными. Потерь native-визуалов и chunks нет; пустых owner shards и битых ссылок нет. **{resource['checkedResources']}** старых ресурсов моделей, текстур и физики совпали по SHA256. Одобренные цельные конструкции и ручные правки сохранены.

Проверки: отдельный сервер с WorldEdit7.2.15 (heap3ГиБ), затем клиент на модах фактической Limacina-сборки с Sodium+Indium вместе, Iris/shaders off (heap4ГиБ). Карта повторно открылась, получено событие качания, выход клиента0. Дополнительная свободная тестовая площадка проверяет новый столб и независимое удаление одного фонаря. JVM запускались последовательно; это функциональные проверки при ограниченной памяти, без новых замеров FPS.

Исходная карта до изменений сохранена в отдельном резервном prerelease. Текущая пара карта/мод, полные исходники, исходное задание, preserved-assemblies.json, кэш, планы, решения и результаты проверок сохранены для продолжения на другом ПК. Инструкция — HANDOFF-RU.md, точные файлы — SHA256SUMS.txt.
'''
for before,after in [('Все734','Все 734'),('за2.6с','за 2,6 с'),('ограничен35тиками','ограничен 35 тиками'),('визуалы14/25636','визуалы 14/25636'),('вариант14','вариант 14'),('Проверены76RP','Проверены 76 RP'),('моделей:23','моделей: 23'),('У8механизмов','У 8 механизмов'),('включая45блоков','включая 45 блоков'),('Удалено2132','Удалено 2132'),('убраны7','убраны 7'),('вместо3934','вместо 3934'),('WorldEdit7.2.15','WorldEdit 7.2.15'),('heap3ГиБ','heap 3 ГиБ'),('heap4ГиБ','heap 4 ГиБ'),('клиента0','клиента 0')]:report=report.replace(before,after)
(D/'REPORT-RU.md').write_text(report,encoding='utf-8')
handoff=D/'HANDOFF-RU.md';text=handoff.read_text(encoding='utf-8');text=text.replace('Только эта дополнительная визуальная сверка и финальная упаковка/установка/публикация остаются до статуса READY.','Дополнительная проверка client-5 и визуальный осмотр приняты; ready-to-release.json — READY. Упаковка, установка и публикация подтверждаются artifacts.json, installed.json и final-release.json.');handoff.write_text(text,encoding='utf-8')
plan=get('plan.json');plan['status']='VERIFIED_AWAITING_FINAL_PUBLICATION'
for stage in plan['stages']:stage['status']='VERIFIED' if stage['id']<6 else 'VERIFIED_AWAITING_FINAL_PUBLICATION'
(D/'plan.json').write_text(json.dumps(plan,ensure_ascii=False,indent=2),encoding='utf-8')
(D/'STATUS-RU.md').write_text('Все игровые исправления и проверки приняты. 172 столба и 562 фонаря заменены, карту повторно не переносить. Данные карты и frozen-ресурсы сверены. client-3 проверяет фактическую сборку пользователя; client-5 — свободную тестовую площадку. Остаётся техническая публикация полного комплекта, подтверждаемая final-release.json: PUBLISHED. Карта Desktop/bloodborne содержит 1888 файлов. Production SHA256: '+H+'\n',encoding='utf-8')
accept=get('acceptance.json');accept['status']='VERIFIED';accept['postFixture']=get('client-5/post-fixture.json');(D/'acceptance.json').write_text(json.dumps(accept,ensure_ascii=False,indent=2),encoding='utf-8')
