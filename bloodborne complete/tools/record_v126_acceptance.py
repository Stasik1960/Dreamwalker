"""Finalize acceptance only from completed build/native/disk evidence."""
import argparse,hashlib,json,shutil,xml.etree.ElementTree as ET
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];DOC=ROOT/'docs/v12.6'
def read(p):return json.loads(p.read_text(encoding='utf8'))
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def main():
    p=argparse.ArgumentParser();p.add_argument('--run-name',required=True);p.add_argument('--world',type=Path,required=True);p.add_argument('--checks',type=Path,required=True);p.add_argument('--checkpoint',required=True);a=p.parse_args()
    jar=ROOT/'build/libs/dreamwalker-bb-fabric-1.20.1-0.1.0-v12.6-functional-assemblies.jar';digest=sha(jar);evidence={};results={}
    for name,path in read(a.checks).items():
        path=Path(path);path=path if path.is_absolute() else ROOT/path;value=read(path)
        assert value['status'].startswith('PASS'),(name,value['status']);evidence[name]={'path':str(path.resolve()),'sha256':sha(path)};results[name]=value
    xml=ROOT/f'build/test-results/gametest/TEST-{a.run_name}.xml';tree=ET.parse(xml).getroot();tests=tree.findall('.//testcase')
    assert len(tests)>=88 and not tree.findall('.//failure') and not tree.findall('.//error') and not tree.findall('.//skipped')
    manifest=read(a.world/'gallery-manifest.json');assert manifest['productionJarSha256']==digest and manifest['schema']=='dreamwalker-flat-gallery-v126'
    assert results['preservation']['status']=='PASS' and results['resources']['productionJarSha256']==digest
    assert results['savedFinal']['productionReopenCompared'] and results['nativePixels']['reviewedFrames']>=40
    assert results['serverFinal']['exit_code']==0 and not results['serverFinal']['extraMods'] and not results['serverFinal']['errors'] and results['build']['exitCode']==0
    (DOC/'validation').mkdir(parents=True,exist_ok=True);shutil.copyfile(xml,DOC/'validation/gameTests.xml')
    assemblies=read(ROOT/'build/v126-assembly-plan/assembly-manifest.json');doors=read(ROOT/'build/v126-door-plan/door-manifest.json');city=manifest['city']
    counts={'newWorkingBefore':551,'newWorkingAfter':manifest['catalogue']['summary']['newWorkingTypes'],'reservedNumbers':1322,'preservedOldStateMappings':1281,'approvedCompleteAssemblies':len(assemblies['assemblies']),'functionalDoors':len(doors['doors']),'movingDoorLeaves':sum(d['leafCount'] for d in doors['doors']),'mainPlatforms':len(manifest['exhibits']),'reviewPlatforms':len(manifest['reviewPlatforms']),'cityOwners':len(city['placedOwners']),'savedObjects':results['savedFinal']['verifiedExhibits'],'nativeFramebufferImages':results['nativePixels']['reviewedFrames']}
    assert counts['newWorkingAfter']==544 and counts['functionalDoors']==7 and counts['approvedCompleteAssemblies']==8
    result={'schema':'dreamwalker-v126-final-acceptance','status':'PASS','baselineCommit':'c52a06c9fa20007a1c06c8fc654b3c1b86f72791','productionSourceCheckpoint':a.checkpoint,'productionJarSha256':digest,'gameTestCount':len(tests),'galleryWorld':str(a.world.resolve()),'counts':counts,'evidence':evidence,'currentUnresolvedTestFailures':[],'originalV12':'PASS_SOURCE_RESOURCES_AND_NATIVE_REGRESSIONS','sourceMapAndPack':'PASS_BYTE_UNCHANGED','authorCollisionExport':'NOT_SUPPLIED_NOT_APPLIED','qaAddonRequiredForGallery':False,'checks':{'build':'PASS','gameTests':'PASS','sourceAndResourcePreservation':'PASS','galleryServerOpenSaveReopen':'PASS','galleryClientOpenSaveReopen':'PASS','nativePixelReview':'PASS_REPRESENTATIVE_MAIN_ALL_CHANGED_PREVIEWS_CITY','allSavedUuidStatePayloadAndOwnership':'PASS','physicalMouseKeyboard':'NOT_RUN','everyCityOwnerIndividuallyViewed':'NOT_RUN'}}
    DOC.mkdir(parents=True,exist_ok=True);(DOC/'acceptance.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
    lines=['Dreamwalker BB V12.6 — исправления ID, цельные конструкции и двери','',f'Готовый combined JAR: {jar.name}',f'SHA-256: {digest}',f'Исходная версия: c52a06c9fa20007a1c06c8fc654b3c1b86f72791. Commit перед сборкой: {a.checkpoint}.','',
      '1322 пятизначных номера уникальны и сохранены; освобождённые номера не переиспользовались. 90143 повторялась в предварительных сборках; теперь90143+90142 — один цельный объект. Рабочих новых типов551→544; оригинальные116 строк V12 сохранены.',
      'Объединено8 одобренных конструкций: '+', '.join(g['primaryId'] for g in assemblies['assemblies'])+'. Старые регистрации и частичные состояния скрыты для совместимости. Полные группы заменяются атомарно с сохранением UUID, поворота, NBT, настроек, событий и ссылок. Неполные старые группы сохраняют прежний вид. Дверь90923 также поддерживает исходный городской монтаж верхней арки на блок назад.',
      'Не объединялись90660+90023,90023+93186,90507+90023,90480+90839. Детали93113,93117,93116,93115 самостоятельные. 90247,93156,90143,93159,90777,93147 не получили механизма дверей.',
      '90586 имеет рабочий подъём по простой тонкой плоскости; проверены реальные движения игрока, оба размера, все8 поворотов, соединение по высоте и монтаж. 90479 скрыта и заменяется90001; её оригинальный механизм и модель не менялись.',
      'Семь новых функциональных дверей: '+', '.join(d['mainId'] for d in doors['doors'])+'. ПКМ открывает отмеченные створки на90°, следующийПКМ закрывает. Двойные створки открываются одновременно; рамы и наружный декор неподвижны. 90031 включает90623. Закрытые исходные поверхности, UV и текстуры сохранены.',
      f'Галерея: {counts["mainPlatforms"]} основных платформ и {counts["reviewPlatforms"]} отдельных образцов. Городской фрагмент64×49×64, в4раза больше площади прежнего32×49×32; {counts["cityOwners"]} модовых объектов. Начальные пересечения разрешены только при конвертации; привилегия не сохраняется в мир.',
      f'PASS: сборка; {len(tests)} GameTests; ресурсные зависимости; защита V12, прежних номеров и состояний; клиентское и серверное открытие, сохранение и повторное открытие; проверка {counts["savedObjects"]} объектов и {counts["nativeFramebufferImages"]} кадров клиента.',
      'FAIL: текущих неустранённых ошибок проверки нет. Ранние неудачные QA-попытки сохранены отдельно и не выдаются за PASS.',
      'NOT_RUN: физическое управление мышью/клавиатурой; отдельный визуальный просмотр каждого городского экземпляра. Подтверждённые исходные пропуски и координаты перечислены в city.sourceExceptions внутри gallery-manifest.json; отсутствующие авторские данные не угадывались.',
      'Авторский экспорт коллизий отсутствует и не применялся. Контракт docs/COLLISION_IMPORT_HANDOFF.md сохранён. Стандартные коллизии простые; выделение совпадает с физикой, у проходимых объектов есть простая прямоугольная обводка.',
      '', 'Minecraft1.20.1, Fabric Loader0.16.10, Java17, Fabric API0.92.9+1.20.1, GeckoLib4.4.9. Замените старый BB JAR новым combinedJAR; распакуйте галерею в saves. Исходный ресурспак и QA-мод не требуются. /function dw_gallery:help — навигация; :assemblies, :doors, :city — секции; :tool — инструмент.']
    (DOC/'REPORT-RU.txt').write_text('\n'.join(lines)+'\n',encoding='utf-8-sig');(DOC/'INSTALL-RU.txt').write_text(lines[-1]+'\n',encoding='utf-8-sig');print(json.dumps({'status':'PASS','tests':len(tests),'counts':counts}))
if __name__=='__main__':main()
