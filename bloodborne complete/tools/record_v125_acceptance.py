"""Final evidence gate; requires native world, actual reviewed pixels and tests."""
from pathlib import Path
import hashlib,json,shutil,subprocess,xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[1];DOC=ROOT/'docs/v12.5-city-review'
SHA='76690ecea281ecd5ac59d9b00493546af1978b0819bb2923da977b176f16b845'
JAR=ROOT/'build/libs/dreamwalker-bb-fabric-1.20.1-0.1.0-v12.5-city-review.jar'

def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def read(p):return json.loads(p.read_text(encoding='utf-8-sig'))
def write(p,d):p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(d,ensure_ascii=False,indent=2)+'\n',encoding='utf8',newline='\n')

def main():
    assert sha(JAR)==SHA
    evidence={};sources={
        'build':'build/remaining-transfer/v125-platform-support-2/result.json',
        'preservation':'docs/v12.5-city-review/preservation-audit.json',
        'resources':'docs/v12.5-city-review/resource-audit.json',
        'collisionOffline':'docs/v12.5-collisions/offline-proof.json',
        'sourcePoseUv':'docs/v12.5-city-review/citycandidate/appended-source-pose-vertex-uv-proof.json',
        'nativePixels':'docs/v12.5-city-review/native/visual-acceptance.json',
        'serverAuthor':'build/v125-flatgallery-author-2-report.json',
        'serverReopenAllChunks':'build/v125-flatgallery-production-reopen-1-report.json',
        'serverFinal':'build/v125-flatgallery-package-production-report.json',
        'savedFinal':'build/v125-gallery-final-disk.json',
        'savedClient1':'build/v125-gallery-client-1-disk.json',
        'savedClient2':'build/v125-gallery-client-2-disk.json',
        'savedClient3':'build/v125-gallery-client-3-disk.json'}
    for name,relative in sources.items():
        source=ROOT/relative;value=read(source);assert value['status'].startswith('PASS'),(name,value['status'])
        target=DOC/'validation'/f'{name}.json';target.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(source,target)
        evidence[name]={'path':str(target),'sha256':sha(target),'status':value['status']}
    xml=ROOT/'build/test-results/gametest/TEST-v125-platform-support-2.xml';tree=ET.parse(xml).getroot();tests=tree.findall('.//testcase')
    assert len(tests)==76 and not tree.findall('.//failure') and not tree.findall('.//error') and not tree.findall('.//skipped')
    shutil.copyfile(xml,DOC/'validation/gameTests.xml');evidence['gameTests']={'path':str(DOC/'validation/gameTests.xml'),'sha256':sha(xml),'status':'PASS'}
    disk=read(ROOT/sources['savedFinal']);visual=read(ROOT/sources['nativePixels']);server=read(ROOT/sources['serverFinal'])
    assert disk['productionJarSha256']==visual['productionJarSha256']==server['artifact_sha256']==SHA
    assert disk['verifiedExhibits']==5331 and disk['productionReopenCompared'] and disk['cityOwners']==4575 and disk['reviewPlatforms']==49
    assert visual['reviewedFrames']==102 and not server['extraMods'] and server['exit_code']==0 and not server['errors']
    author=read(ROOT/sources['serverAuthor'])['gallery']['result'];overlap=author['city']['initialOverlapRuntimeCheck']
    assert overlap['status']=='PASS_BREAK_THEN_NORMAL_REPLACEMENT_REJECTED_RESTORED_INITIAL_UUID' and overlap['scopeIsEmptyOutsideInitialPlacement']
    world=ROOT/'build/runtime-server-v125-flatgallery-package-production/isolated-smoke-world';m=read(world/'gallery-manifest.json')
    assert len(m['exhibits'])==654 and len(m['reviewPlatforms'])==49 and len(m['city']['sourceExceptions'])==122
    references=ROOT.parent/'Bloodborne-Blocks/reference-inputs/bbmc-v15-v16'
    expected={'bbmc_v15_resource.zip':'29b31e3744af5794ae95ebe4c5474e3b70dfb08c143355bce815c08ceef09a36','bbmc_v16_map.zip':'39e83f2941b768fd2ae9f84d8f57dc76aec78bd926dc5e38eab3059251d2bb08'}
    assert all(sha(references/name)==digest for name,digest in expected.items())
    result={'schema':'dreamwalker-v125-final-acceptance','status':'PASS','baselineCommit':'653ae307ba4c44392ee08969f3890321e95659bb','productionSourceCheckpoint':'e8d2204ccfbaeb8ca4b6aabd97ec8f5f07446bc1','qaCheckpoint':'d39412f2eeef0a2395441c77d231ad073931d9ae','productionJarSha256':SHA,'gameTestCount':76,'gameTestXml':'docs/v12.5-city-review/validation/gameTests.xml','galleryWorld':str(world),'counts':{'newWorkingBefore':572,'newWorkingAfter':551,'retiredStyleTypes':21,'reservedNumbers':1322,'preservedOldStateMappings':1281,'appendedSourceCellPoses':94,'mainPlatforms':654,'constructionReviewPlatforms':31,'doorReviewPlatforms':18,'cityOwners':4575,'savedExhibits':5331,'nativeFramebufferImages':102,'sourceInvisibleServices':448,'styleOmittedSourceCells':113,'originalSourceExceptions':9,'explicitSourceOmissions':122},'compactFootprint':[255,250],'originalV12':'PASS_PRESERVED','originalMapAndPack':'PASS_BYTE_UNCHANGED','sourceArchives':expected,'initialCityOverlap':overlap,'evidence':evidence,'checks':{'build':'PASS','gameTests':'PASS','modelAndTextureDependencies':'PASS','collisionShapeContainment':'PASS_OFFLINE','nativeMovementAndBlockPlacement':'PASS','galleryServerOpenSaveReopen':'PASS','galleryClientOpenSaveReopen':'PASS','nativePixelReview':'PASS_REPRESENTATIVE_MAIN_ALL49_PREVIEWS_CITY7','allSavedUuidStatePayloadAndOwnership':'PASS','physicalMouseKeyboard':'NOT_RUN','everyCityOwnerVisuallyInspected':'NOT_RUN','authorCollisionExport':'NOT_SUPPLIED_NOT_APPLIED'},'currentUnresolvedTestFailures':[],'historicalResolvedFailures':['First gallery attempt90482 terrain top-only source cube conflicted with floor; gallery-only central floor display fixed, production model/physics unchanged.','Initial verifier relative-path mistake corrected; all independent disk proofs PASS.','Back-face-only source views replaced with observed visible-side native supplements.'],'assemblyRegistrationsMerged':False,'newDoorOpeningBehaviorAdded':False,'qaAddonRequiredForGallery':False}
    write(DOC/'acceptance.json',result)
    lines=['Dreamwalker BB V12.5 — итоговый отчёт','',f'Combined JAR SHA256: {SHA}','Основа этапа: 653ae307ba4c44392ee08969f3890321e95659bb. Production-коммит перед сборкой: e8d2204ccfbaeb8ca4b6aabd97ec8f5f07446bc1. QA-коммит: d39412f2eeef0a2395441c77d231ad073931d9ae.','',
    'Из рабочего каталога и Creative удалено 21 ванильное или практически ванильное семейство. Рабочих новых типов было 572, стало 551. Список ID и визуальные основания: style-audit-RU.txt. Регистрации оставлены скрытыми для прежних сохранений; номера не переиспользуются. Оригинальные блоки V12, их модели, текстуры, состояния, поведение и физика сохранены.','',
    'У новых объектов уменьшены физические формы, занимаемые ячейки и обводка. Простые прямоугольники вписаны в видимую геометрию; открытое пространство около диагональных платформ и внутри арок освобождено. Для обычных маленьких твёрдых объектов используется одна коробка, для нужных проходов и крупных наклонных опор — небольшое число коробок (до12). Проходимые декорации сохраняют пустую физику и одну прямоугольную рамку выбора. Функциональные ступени и приставные лестницы сохраняют прежние рабочие формы.','',
    'На компактном стенде 255×250 блоков: 654 основных платформы, 31 образец составных конструкций, 18 платформ с предполагаемыми дверями и фрагмент города. Размер каждой платформы: визуальный размер +2 клетки; промежутки не менее2 блоков. Окна93116/93117 и93069/93066 показаны собранными. Мелкие детали рассмотрены по исходным моделям и соседству. Новые цельные предметы для сборок пока не создавались; открывание предполагаемым дверям не добавлялось. Это образцы для оценки пользователем. Исходные смещения и взаимное расположение частей сохранены.','',
    'Город: исходный участок [144,64,-1086]..[175,112,-1055], размер32×49×32,4575 модовых объектов. Для точного воспроизведения условных применений исходного ресурспака добавлены94 состояния в16 существующих новых типах, без изменения старых состояний и ресурсов. Это законченные применения в одной исходной ячейке, а не объединение отдельных оконных частей. Копирование сохранений сохраняет UUID/NBT/SourceShift и связи. Первоначальное пересечение разрешено только явно перечисленным экземплярам при переносе; удаление и обычная повторная установка снова соблюдают запрет пересечений — проверено в самом городском фрагменте. Исходная карта и ресурспак не изменены.','',
    'PASS: конечная сборка;76 обязательных GameTests; ресурсные зависимости; сохранность4259 моделей и559 текстур;3761 защищённых файлов V12,116 строк каталога,1322 зарезервированных номеров и1281 старых соответствий состояний. PASS: серверное и клиентское открытие/сохранение/повторное открытие; независимая проверка5331 объекта, их UUID, состояний, NBT, связей и геометрии;102 реально просмотренных native PNG. Для платформы90635 проверены падение/опора/движение игрока во всех8 поворотах; проверены освобождённые пустоты полуарк и обычная установка блоков в них. Сборка сама по себе не использовалась как игровой PASS.','FAIL: текущих неустранённых ошибок проверки нет. Неудачные ранние QA-попытки сохранены отдельно и объяснены в acceptance.json.','NOT_RUN: физическое управление мышью/клавиатурой; визуальная проверка каждого из4575 городских экземпляров по отдельности. Все49 образцов сборок/дверей и7 городских ракурсов просмотрены; остальные сохранения проверены программно.','',
    'Подтверждённые ограничения исходников:448 исходных полностью невидимых служебных ячеек оставлены воздухом.113 ячеек удалённых стилистических импортов,6 ранее намеренно удалённых jungle-trapdoor и2 удалённых состояния стены не восстановлены. Один potted_spruce_sapling имеет отсутствующее авторское разрешение texture alias#dirt — содержимое не угадывалось. Итого122 явных пропуска; координаты и основания: city.sourceExceptions в gallery-manifest.json. Участок обрезает продолжение зданий; отдельные исходные разрывы и навесные фрагменты сохранены.','',
    'Авторский экспорт коллизий отсутствует и не применялся. Контракт COLLISION_IMPORT_HANDOFF.md сохранён. Для будущего импорта по точным ID/состояниям описан приоритет авторской физики над текущими defaults, с учётом SourceShift и монтажа: docs/v12.5-collisions/AUTHOR_COLLISION_INTEGRATION.md.','',
    'Установка: Minecraft1.20.1,Fabric Loader0.16.10,Java17,Fabric API0.92.9+1.20.1,GeckoLib4.4.9. Замените прежний BB JAR конечным combined JAR, папку галереи распакуйте в saves. Исходный ресурспак и QA addon не нужны. /function dw_gallery:help — навигация; :assemblies, :doors, :city — переходы; :tool — выдача инструмента. Секции исходнойV12/новых/отсутствующих на карте/предметов разделены цветами.']
    (DOC/'REPORT-RU.txt').write_text('\n'.join(lines)+'\n',encoding='utf-8-sig')
    (DOC/'INSTALL-RU.txt').write_text('\n'.join(lines[-1:])+'\n',encoding='utf-8-sig')
    print(json.dumps({'status':'PASS','tests':76,'nativeFrames':102,'savedObjects':5331,'world':str(world)}))
if __name__=='__main__':main()
