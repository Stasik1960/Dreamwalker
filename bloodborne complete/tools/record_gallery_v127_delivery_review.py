"""Record completed pixel review and disclose known lighting in the delivery."""
import hashlib,json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
frames=[]
for name in ('v127-new-gallery-client5','v127-new-gallery-client6-reopen'):
    run=ROOT/'build'/('runtime-client-'+name)
    report=json.loads((run/'v127-gallery-benchmark.json').read_text(encoding='utf8'))
    assert report['status'].startswith('PASS_ENGINE_GALLERY_BENCHMARK') and len(report['frames'])==11
    for row in report['frames']:
        image=run/'screenshots'/row['fileName']
        assert hashlib.sha256(image.read_bytes()).hexdigest()==row['sha256']
        frames.append({'run':name,'path':str(image.relative_to(ROOT)),**row,'rootPixelReview':'COMPLETED'})
finding={'status':'FAIL_KNOWN_LIGHTING_RENDER_REGRESSION','description':'Часть новых моделей почернела: рендер применяет свет из непрозрачной клетки корня ко всей модели. Причина локализована по коду и сохранению; исправление не внесено. Галерея передаётся по просьбе пользователя для поиска других проблем.','diagnosis':'docs/v12.7-gallery/black-blocks-diagnosis.json','defaultNewArchitectureSamplesWithZeroRootSky':169,'newArchitectureSamples':539,'originalTexturesComparedByteIdentical':236,'additionalDiagnosticAB':'NOT_RUN_QA_ADDON_LAUNCH_FAILED','fullResourcePackVisualParity':'NOT_CONFIRMED'}
value={'status':'PASS_ROOT_REVIEWED_22_NATIVE_FRAMES','productionJarSha256':report['productionJarSha256'],'meaning':'The review was performed; this is not a claim that every visual is correct.','frames':frames,'visualFindings':[finding],'purpose':'ISSUE_DISCOVERY_GALLERY_WITH_DISCLOSED_KNOWN_DEFECT'}
(ROOT/'build/v127-new-gallery-pixel-review.json').write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
readme=ROOT/'build/v127-new-gallery-delivery/world/README-RU.txt'
text=readme.read_text(encoding='utf-8-sig')
marker='ИЗВЕСТНАЯ ПРОБЛЕМА ДЛЯ ПРОВЕРКИ'
assert marker not in text
text+='\n'+marker+'\nЧасть новых объектов выглядит чёрной из-за ошибки выборки света при рендере в текущем моде V12.7. Причина найдена; исправление не внесено. Карта выдана по просьбе пользователя для поиска остальных проблем. Это не повреждение файлов текстур. Установка дополнительного исходного ресурспака не требуется.\n'
readme.write_text(text,encoding='utf-8-sig')
print(json.dumps({'status':value['status'],'frames':len(frames),'knownVisualDefect':finding['status']},ensure_ascii=False))
