"""Package four accepted blocks and unapproved portal art studies separately."""
import hashlib,json,os,shutil,stat,zipfile
from pathlib import Path
P=Path(__file__).resolve().parents[1];D=P/'utility-evidence';B=P/'utility-release';A=B/'assets';A.mkdir(parents=True,exist_ok=True)
def read(p):return json.loads(p.read_text(encoding='utf-8-sig'))
def sha(p):
 h=hashlib.sha256()
 with p.open('rb') as f:
  for data in iter(lambda:f.read(1<<20),b''):h.update(data)
 return h.hexdigest()
integrity=read(D/'artifact-integrity.json');assert integrity['status']=='PASS';jar=Path(integrity['jar']);digest=sha(jar);assert digest==integrity['sha256']
assert read(P/'build-tools/gradle-remapJar.json')['exitCode']==0
for role in ('server-1','client-1'):
 assert read(D/role/'server-checks.json')['status']=='PASS';assert read(D/role/'process.json')['productionSha256']==digest
assert read(D/'server-1/post-save-stop.json')['worldSaved'] and 'All dimensions are saved' in (D/'server-1/console.log').read_text(encoding='utf-8')
assert read(D/'client-1/launch-result.json')['exitCode']==0 and read(D/'client-1/client-checks.json')['status']=='PASS'
assert read(D/'visual-review.json')['status']=='PASS'
frames=D/'client-1/screenshots';frames.mkdir(exist_ok=True)
for name in ('faux-waterlogged.png','utility-overview.png','faux-water-inside.png'):shutil.copy2(P/'utility-profiles/client/screenshots'/name,frames/name)
accept={'status':'PASS','version':'0.1.0-v12.7.4-visual-physics.4','jarSha256':digest,'baselineSha256':integrity['baselineSha256'],'dedicatedServerChecks':len(read(D/'server-1/server-checks.json')['checks']),'dedicatedServer':'server-1','serverProcessEndedAfterConfirmedSave':True,'serverExitCode':read(D/'server-1/launch-result.json')['exitCode'],'reloadedWorldAndRealClient':'client-1','clientExitCode':0,'creativeOnlySelectionAndBreak':True,'waterPhysicsDisabled':True,'waterloggedChestDataPreserved':True,'rendering':'Sodium + Indium, Iris shaders off','mapReconverted':False,'mapRelease':'native-grid-city-visual-physics-20261025','greenPortalImplemented':False,'portalChoiceRequired':True,'performanceBenchmark':False}
(D/'acceptance.json').write_text(json.dumps(accept,ensure_ascii=False,indent=2),encoding='utf-8')
shutil.copy2(jar,A/'dreamwalker-bb-0.1.0-v12.7.4-visual-physics.4.jar')
def safe_files(folder):
 for root,dirs,files in os.walk(folder,followlinks=False):
  dirs[:]=[n for n in dirs if n not in ('.git','__pycache__') and not Path(root,n).stat(follow_symlinks=False).st_file_attributes&stat.FILE_ATTRIBUTE_REPARSE_POINT]
  for name in sorted(files):
   p=Path(root,name)
   if not p.stat(follow_symlinks=False).st_file_attributes&stat.FILE_ATTRIBUTE_REPARSE_POINT and p.suffix!='.pyc':yield p
source=[]
for folder in ('src','tools','gradle','validation'):source+=list(safe_files(P/folder))
source +=[P/n for n in ('README.md','.gitignore','build.gradle','settings.gradle','gradle.properties','gradlew','gradlew.bat')]
source +=[D/n for n in ('TASK-RU.txt','REPORT-RU.txt','HANDOFF-RU.txt','artifact-integrity.json','acceptance.json')]
source +=[P/'bench-evidence'/n for n in ('REPORT-RU.txt','HANDOFF-RU.txt','artifact-integrity.json','acceptance.json')]
source +=[P/'fix-evidence'/n for n in ('REPORT-RU.txt','HANDOFF-RU.txt','artifact-integrity.json','visual-repair-receipt.json','acceptance.json')]
source +=list(safe_files(P/'fix-evidence/export/baked'));source=sorted(set(source));manifest={p.relative_to(P).as_posix():sha(p) for p in source}
with zipfile.ZipFile(A/'dreamwalker-visual-physics.4-sources.zip','w',zipfile.ZIP_DEFLATED,compresslevel=6) as z:
 for p in source:z.write(p,'DW-visual-physics.4/'+p.relative_to(P).as_posix())
 z.writestr('DW-visual-physics.4/SOURCE-FILES.json',json.dumps(manifest,indent=2))
with zipfile.ZipFile(A/'dreamwalker-visual-physics.4-evidence.zip','w',zipfile.ZIP_DEFLATED,compresslevel=6) as z:
 for p in safe_files(D):
  if p.suffix in ('.json','.txt','.log','.args','.png','.gif'):z.write(p,'utility-evidence/'+p.relative_to(D).as_posix())
for name in ('REPORT-RU.txt','HANDOFF-RU.txt'):shutil.copy2(D/name,A/name)
shutil.copy2(D/'portal-preview/portal-animation-options.zip',A/'portal-animation-options.zip')
rows=[{'name':p.name,'bytes':p.stat().st_size,'sha256':sha(p)} for p in sorted(A.iterdir()) if p.is_file() and p.name!='SHA256SUMS.txt']
(A/'SHA256SUMS.txt').write_text(''.join(r['sha256']+'  '+r['name']+'\n' for r in rows),encoding='utf-8');rows.append({'name':'SHA256SUMS.txt','bytes':(A/'SHA256SUMS.txt').stat().st_size,'sha256':sha(A/'SHA256SUMS.txt')})
(B/'ASSETS.json').write_text(json.dumps({'status':'VERIFIED_LOCAL_RELEASE_ASSETS','tag':'native-grid-city-utility-blocks-20261025','branch':'codex/utility-blocks-20261025','jarSha256':digest,'unchangedMapRelease':accept['mapRelease'],'assets':rows,'sourceFiles':len(source)},ensure_ascii=False,indent=2),encoding='utf-8')
(B/'release-notes.md').write_text('''DW-барьер, DW-свет, тьма и фальшивая вода выделяются и удаляются только в креативе. Исправлено выделение прежних барьера/света. DW-свет излучает уровень 14. Чёрный непрозрачный блок без собственной коллизии перекрывает свет; его PNG занимает 81 байт.

Декоративная вода использует ванильную текстуру/ресурспак, не течёт, не замедляет, не даёт плавать и не поднимает предметы. Поддерживает Waterloggable-хосты без замены их типа и BlockEntity; проверены полуплита, ступени, забор, панель, стена и сундук с содержимым. Первый удар в креативе удаляет только воду. Свободно совмещается с выступающими коллизиями мода, сохраняя данные и реальные корни.

Выделенный сервер и клиент пользовательской сборки с Sodium+Indium: PASS. Проверены настоящее освещение, обе очередности пересечения, игровое водонаполнение/удаление, повторное открытие мира, исходная синхронизация чанка и изменения по сети, отсутствие водного состояния у игрока. Остаточный процесс выделенного стенда закрыт после подтверждённого сохранения всех измерений; это отмечено в отчёте. FPS в этой проверке не замерялся.

Скамейки и прежние исправления сохранены. Все 1244 ресурса данных и прежние текстуры/модели не изменены (добавлены новые блоки и строки перевода). [Карта прежняя и совместима](https://github.com/Stasik1960/Dreamwalker/releases/tag/native-grid-city-visual-physics-20261025). Мод обновить на клиенте и сервере.

Зелёный портал пока не добавлен: `portal-animation-options.zip` содержит три анимированных варианта для выбора пользователя. Это предварительный визуал, не готовый зарегистрированный блок.
''',encoding='utf-8')
print('Packaged',len(source),'source files and',len(rows),'assets',flush=True)
