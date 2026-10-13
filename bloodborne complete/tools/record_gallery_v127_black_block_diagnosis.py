"""Persist the read-only cause analysis; distinguish the failed QA launch."""
import hashlib,json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
run=ROOT/'build/runtime-client-v127-black-blocks-probe'
read=lambda p:json.loads(p.read_text(encoding='utf8'))
native=read(run/'v127-gallery-benchmark.json')
saved=read(ROOT/'build/v127-black-blocks-saved-light.json')
resources=read(ROOT/'build/v127-black-blocks-resource-proof.json')
assert resources['comparedOriginalTextures']==resources['identicalOriginalTextures']==236
black=next(r for r in saved['rows'] if r['id']=='90247')
assert black['savedRootLight']['sky']==0 and black['neighborLight']['up']['sky']==15
assert not native['frames'] and (run/'crash-reports/crash-2026-10-13_19.50.32-client.txt').is_file()
previous=read(ROOT/'build/runtime-client-v127-new-gallery-client6-reopen/v127-gallery-benchmark.json')
assert previous['status'].startswith('PASS_ENGINE_GALLERY_BENCHMARK') and len(previous['frames'])==11
prod=ROOT/'build/libs/dreamwalker-bb-fabric-1.20.1-0.1.0-v12.7-final.jar'
assert hashlib.sha256(prod.read_bytes()).hexdigest()==resources['productionJarSha256']
out=ROOT/'docs/v12.7-gallery';out.mkdir(parents=True,exist_ok=True)
evidence={'status':'PASS_CAUSE_LOCALIZED_CODE_AND_SAVED_LIGHT','productionJarSha256':resources['productionJarSha256'],'diagnosticScope':'Read-only native bytecode, source history, saved light arrays, original pixels and prior native gallery frames. No production patch, source-world edits, gallery packaging or publication.',
    'cause':'V12.7 copied native source-carrier opacity into NEW roots. A fully opaque carrier has no light inside its root cell. The vanilla BER dispatcher samples that cell; CompositeClient.renderParts applies the one dark packed light to every face of the entire external model.',
    'regressionCommit':'a1ceb186db8e35103c238118ba7f3aaf89725749','sourceCode':['src/architecture/java/dev/dreamwalker/bloodbornedw/composite/CompositeRootBlock.java:32','src/architecture/java/dev/dreamwalker/bloodbornedw/composite/ImportedSourceLightingV127.java','src/architecture/java/dev/dreamwalker/bloodbornedw/composite/CompositeClient.java:161'],
    'savedMainPlatformCounts':saved['countsByOriginAndRootSky'],'savedCase90247':black,'sourceTextureComparison':{k:v for k,v in resources.items() if k!='textures'},'previousNativeGalleryFrames':previous['frames'],
    'additionalLightOnlyAB':'NOT_RUN','diagnosticAddonLaunch':'FAIL_QA_ADDON_INJECTION_REQUIREMENT_1_OF_2_BEFORE_WORLD_OPEN','qaFailureEvidence':str((run/'crash-reports/crash-2026-10-13_19.50.32-client.txt').relative_to(ROOT)),
    'requiredCorrection':'Sample world light at visible model surfaces/parts for NEW objects instead of the opaque root interior. Preserve night/dark rooms, torch lighting, source emission/opacity, geometry/UV/collisions, all original V12 behavior. Do not fix by unconditional full-bright or by deleting all opacity.',
    'originalV12Modified':False,'productionModified':False,'worldPackaged':False,'githubPublished':False,'pixelReview':'Root reviewed the previously completed native gallery captures. No diagnostic after-image exists; do not claim a visually tested production fix.'}
(out/'black-blocks-diagnosis.json').write_text(json.dumps(evidence,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
text='''Причина почернения блоков V12.7

CONFIRMED: регрессия согласования светонепроницаемости и рендера новых объектов.
В коммите a1ceb186db8e35103c238118ba7f3aaf89725749 корни новых объектов получили
светонепроницаемость исходных ванильных носителей вместо прежнего нуля.
Внутри полностью непрозрачной клетки уровень света равен 0. Стандартный
диспетчер рендера берёт свет именно в позиции корня, а CompositeClient
использует это одно значение для всех граней и частей внешней модели.

В реально сохранённой и повторно открытой галерее у 90247 свет в корне 0,
в соседней верхней клетке дневной свет 15. Код переносит непрозрачность
ванильного stripped_oak_log (15). У контрольных 90083 и 93113 свет в корне 15.
Проверены точный путь рендера Minecraft 1.20.1 и история изменения мода.
Все 236 сопоставленных файлов исходных текстур совпадают побайтно.
В сохранённой галерее 169 из 539 новых архитектурных образцов имеют свет 0
в корне; это число образцов в данном состоянии, не всех возможных состояний.

Исправлять нужно выбор освещения у видимых поверхностей новых моделей.
Постоянное максимальное освещение испортит ночь и тёмные помещения.
Обнуление всей непрозрачности
тоже не является корректным исправлением: это изменит распространение света.

Мод, оригинальные блоки V12 и исходная карта не изменены.
Карта не упакована и не опубликована. Диагностический аддон не предназначен
для установки пользователю. Точные данные: black-blocks-diagnosis.json.

Дополнительное сравнение до/после: NOT_RUN. Запуск временного тестового
аддона: FAIL, ошибочное требование двух внедрений Mixin, выполнено одно.
Это ошибка диагностического аддона; он упал до открытия мира. Повторный
запуск не выполнялся, чтобы не расширять проверки. Исправление самого
мода и его визуальная проверка остаются отдельной незавершённой работой.
'''
(out/'BLACK-BLOCKS-DIAGNOSIS.txt').write_text(text,encoding='utf-8-sig')
print(json.dumps({'status':evidence['status'],'report':str(out/'BLACK-BLOCKS-DIAGNOSIS.txt'),'productionModified':False,'packaged':False},ensure_ascii=False))
