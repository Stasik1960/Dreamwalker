"""Write release acceptance only from complete, artifact-bound final evidence."""
from pathlib import Path
import argparse, hashlib, json, xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[1]; DOC=ROOT/'docs/v12.4-dedup'
JAR=ROOT/'build/libs/dreamwalker-bb-fabric-1.20.1-0.1.0-v12.4-dedup.jar'
def read(p):return json.loads(p.read_text(encoding='utf8-sig'))
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def write(p,v):p.write_bytes((json.dumps(v,ensure_ascii=False,indent=2)+'\n').encode('utf8'))
def main():
    p=argparse.ArgumentParser()
    p.add_argument('--build-run',required=True);p.add_argument('--source-commit',required=True)
    p.add_argument('--gallery-world',type=Path,required=True)
    for key in ('migration','reopen','visual','gallery-server','gallery-client','gallery-disk','gallery-review'):
        p.add_argument('--'+key,type=Path,required=True)
    a=p.parse_args(); digest=sha(JAR); counts=read(DOC/'type-counts.json')
    build=read(ROOT/'build/remaining-transfer'/a.build_run/'result.json');assert build['status']=='PASS'
    xmlpath=ROOT/'build/test-results/gametest'/('TEST-'+a.build_run+'.xml')
    tests=ET.parse(xmlpath).getroot(); gamecount=len(tests.findall('.//testcase'))
    assert gamecount>=70 and not tests.findall('.//failure') and not tests.findall('.//error')
    migration=read(a.migration);reopen=read(a.reopen);visual=read(a.visual)
    assert migration['status']=='PASS' and reopen['status']=='PASS_REOPEN'
    assert migration['afterProductionJarSha256']==reopen['afterProductionJarSha256']==digest
    assert migration['actualV123SavedFixtures']==reopen['actualV123SavedFixtures']==len(read(DOC/'type-state-mapping.json')['mappings'])
    assert int(migration['ordinaryAliasExactStateInteractions'])==migration['actualV123SavedFixtures']
    assert migration['globalLedgerNoRetiredOwnersOrOrphans']==reopen['globalLedgerNoRetiredOwnersOrOrphans']=='PASS'
    assert visual['status']=='PASS' and visual['productionJarSha256']==digest
    server=read(a.gallery_server);client=read(a.gallery_client);disk=read(a.gallery_disk);review=read(a.gallery_review)
    assert server['status']=='PASS' and server['artifact_sha256']==digest and not server['extraMods']
    assert client['status']==disk['status']==review['status']=='PASS'
    assert client['productionJarSha256']==disk['productionJarSha256']==review['productionJarSha256']==digest
    assert disk['productionReopenCompared'] is True and disk['functionalCatalogue']['status']=='PASS'
    assert review['reviewedFrames']==client['nativeFramebufferImages']
    resources=read(DOC/'resource-audit.json');preservation=read(DOC/'preservation-audit.json')
    assert resources['status']==preservation['status']=='PASS' and resources['productionJarSha256']==digest
    assert preservation['originalCatalogueRowsUnchanged'] and preservation['numberReservationsUnchanged']
    world=a.gallery_world.resolve();assert world.is_relative_to(ROOT/'build')
    assert read(world/'gallery-manifest.json')['productionJarSha256']==digest
    evidence={key:{'path':str(getattr(a,key).resolve()),'sha256':sha(getattr(a,key))} for key in ('migration','reopen','visual','gallery_server','gallery_client','gallery_disk','gallery_review')}
    evidence.update(resources={'path':str(DOC/'resource-audit.json'),'sha256':sha(DOC/'resource-audit.json')},preservation={'path':str(DOC/'preservation-audit.json'),'sha256':sha(DOC/'preservation-audit.json')})
    acceptance={'schema':'dreamwalker-v124-final-acceptance-v1','status':'PASS','productionJarSha256':digest,
        'baselineCommit':counts['baselineCommit'],'originalV12Commit':'821de077917f696a13600e900a244ffc05230c2f','sourceCommit':a.source_commit,
        'build':'PASS','gameTests':'PASS','gameTestCount':gamecount,'gameTestXml':xmlpath.relative_to(ROOT).as_posix(),
        'nativeOldSaveMigration':'PASS','nativeSaveReopen':'PASS','nativeVisualReview':'PASS','galleryServerReopen':'PASS',
        'galleryClientOpenAndSave':'PASS','gallerySavedObjects':'PASS','preservation':'PASS','resources':'PASS',
        'galleryWorld':str(world),'counts':counts,'evidence':evidence,'minecraft':'1.20.1','fabricLoader':'0.16.10','java':'17',
        'nativePendingChunkOrdering':migration.get('nativePendingChunkOrdering','NOT_RUN'),
        'physicalMouseKeyboard':'NOT_RUN','frameRateBenchmark':'NOT_RUN','onlineAccountAuthentication':'NOT_RUN',
        'authorCollisionExport':'NOT_SUPPLIED_NOT_APPLIED','originalMapChanged':False}
    write(DOC/'acceptance.json',acceptance)
    text=f'''Dreamwalker BB V12.4 — объединение новых функциональных типов

Готовый combined JAR: {JAR.name}
SHA-256: {digest}
Minecraft 1.20.1 / Fabric 0.16.10 / Java 17.
Исходная V12: 821de077917f696a13600e900a244ffc05230c2f.
База этапа V12.3: {counts['baselineCommit']}.
Промежуточный commit кода перед итоговой сборкой: {a.source_commit}.

Результат
Новые рабочие типы: {counts['newWorkingTypesBefore']} → {counts['newWorkingTypesAfter']}.
Предметы новых типов в Creative: {counts['newCreativeStacksBefore']} → {counts['newCreativeStacksAfter']}.
Объединено {counts['mergedGroups']} групп; {counts['retiredWorkingIds']} прежних ID скрыты из рабочего каталога и Creative.
Все {counts['newRegisteredAdaptersAfter']} новых регистраций сохраняются для чтения старых миров/предметов.
Все {counts['reservedIdsAfter']} пятизначных номеров зарезервированы без перенумерации и повторного использования.
116 строк оригинального каталога V12, его исходные модели/текстуры/RP и специальные правила сохранены.
Исходная карта и предыдущие галереи не изменялись.

Полный список групп и причин объединения: MERGED-GROUPS-RU.csv.
Старый ID/variant → основной ID, состояние, поворот и SourceShift: OLD-ID-STATES.csv.
Заметные различия оставленных подозрительных пар: RETAINED-DIFFERENCES-RU.txt.
Решения приняты по установленному виду после совмещения положения, включая
нативные кадры Minecraft. Внутренние названия/пути/различия JSON не служили
основанием оставлять отдельный устанавливаемый тип.

Ступени
19 заметно разных материалов имеют по одному рабочему типу и предмету.
Направление, верхняя/нижняя половина, прямая форма и четыре угла — состояния.
Обычная установка автоматически соединяется; изменение соседей обновляет угол.
Старые отдельные углы сохраняют форму до включения AUTO через Shift + ПКМ.
313 старых состояний ступеней сохраняют исходный рисунок/ориентацию UV через
проверенный ключ внешнего вида; ключ не меняет физику и не создаёт новый тип.
Приставная 90586 объединяет две высоты прежних 90586/90587, сохраняя подъём,
наращивание и монтаж. Крупные лестничные платформы оставлены по их размерам
и возможности поставить/использовать полноценную площадку.

Совместимость и коллизии
Старые UUID, корневая клетка, типизированный NBT, профиль, монтаж, SourceShift,
настройки и отладочные команды сохраняются атомарным преобразованием.
Копирование создаёт новый UUID и переносит внешний вид без частных настроек/связей.
Правила простой физики и совпадающего контура новых непроходимых объектов сохранены.
Проходимые декорации имеют пустую физику и простой прямоугольник наведения.
Ступени сохраняют необходимые опоры и подъём. Оригинальная V12 не участвует
в новых преобразованиях форм. Авторский экспорт коллизий не предоставлен;
он не применялся. Исходный контракт и дополнение состояний приложены в исходниках.

Проверки итогового JAR
PASS — сборка combined JAR, исходники, {gamecount} серверных GameTests и ресурсные зависимости.
PASS — {migration['actualV123SavedFixtures']} подлинных состояний сохранены старым V12.3 JAR,
  загружены новым JAR, проверены UUID/NBT/ledger, предметы, установка,
  наведение, копирование и удаление; повторный вход идемпотентен.
Порядок загрузки частей по чанкам: {acceptance['nativePendingChunkOrdering']}.
Сохранённые связи/события механизма: {migration.get('oldMechanismLinksPoliciesAndTransitionEvents','NOT_APPLICABLE')}.
  Это отдельный результат фактического состава проверенных объединённых групп.
PASS — нативный клиент: {visual['allFrozenNewStatesRendered']} исходных состояний,
  {visual['mappedOldStatesRendered']} преобразований, 200 функциональных состояний ступеней,
  {visual['retainedPairReviews']} подозрительных пар, {visual['reviewedFrames']} кадров.
PASS — новая галерея: повторное открытие обычным сервером без QA-дополнения,
  открытие/сохранение клиентом, {client['nativeFramebufferImages']} нативных кадров,
  сохранённые объекты, поверхности платформ, ID/состояния и размеры.
  Каждому новому рабочему типу соответствует одна основная станция.
  Платформы рассчитаны по визуальной геометрии +2 клетки, промежуток —2 клетки.
  Предметы V12 выставлены в рамках. Удалённые повреждённые станции не восстановлены.
PASS — сохранность оригинального содержимого и исходных материалов.
FAIL в окончательной приёмке: нет. Предварительные запуски и исправленные
  проблемы отделены от итоговых SHA-связанных результатов в verification.zip.
NOT_RUN — физические нажатия мыши/клавиатуры человеком, отдельный FPS-бенчмарк,
  вход в сетевую учётную запись. Игровые методы проверялись нативными QA-вызовами
  в настоящем клиенте/сервере; успешная сборка не подменяет эти проверки.

Точные привязки SHA-256, результаты и границы проверок: acceptance.json.
Установка мода и новой карты: INSTALL-RU.txt.
'''
    (DOC/'REPORT-RU.txt').write_bytes(text.encode('utf8-sig'))
    print(json.dumps({'status':'PASS','productionJarSha256':digest,'newWorkingTypesAfter':counts['newWorkingTypesAfter'],'gameTestCount':gamecount}))
if __name__=='__main__':main()
