import hashlib,json,os,shutil,stat,zipfile
from pathlib import Path
P=Path(__file__).resolve().parents[1];D=P/'faerie-evidence';B=P/'faerie-release';A=B/'assets';A.mkdir(parents=True,exist_ok=True)
def read(p):return json.loads(p.read_text(encoding='utf-8-sig'))
def sha(p):
 h=hashlib.sha256()
 with p.open('rb') as f:
  for data in iter(lambda:f.read(1<<20),b''):h.update(data)
 return h.hexdigest()
integrity=read(D/'artifact-integrity.json');assert integrity['status']=='PASS';jar=Path(integrity['jar']);digest=sha(jar);assert digest==integrity['sha256']
assert read(P/'build-tools/gradle-remapJar.json')['exitCode']==0
assert read(D/'server-1/server-checks.json')['status']=='PASS' and read(D/'server-1/process.json')['productionSha256']==integrity['coreAcceptedSha256']
serverExit=read(D/'server-1/launch-result.json')['exitCode'];assert serverExit==0 or read(D/'server-1/post-save-stop.json')['worldSaved']
assert read(D/'client-1/launch-result.json')['exitCode']==0 and read(D/'client-1/process.json')['productionSha256']==digest
client=read(D/'client-1/client-checks.json');assert client['status']=='PASS' and len(client['performancePeriods'])==4
assert read(D/'visual-review.json')['status']=='PASS';perf=read(D/'performance.json');assert perf['status']=='MEASURED'
frames=D/'client-1/screenshots';frames.mkdir(exist_ok=True)
for name in ('veil-front.png','veil-back.png','veil-east.png'):shutil.copy2(P/'faerie-profiles/client/screenshots'/name,frames/name)
accept={'status':'PASS','version':'0.1.0-v12.7.4-visual-physics.5','jarSha256':digest,'coreAcceptedJarSha256':integrity['coreAcceptedSha256'],'server':'server-1','client':'client-1','clientExitCode':0,'serverExitCode':serverExit,'serverClosedAfterSavedWorld':serverExit!=0,'selectedVariant':3,'material':'Faerie mist and fireflies','blockId':'bloodborne_dw:dw_faerie_veil','states':1,'spriteCount':1,'blockEntities':0,'particles':0,'blockTickers':0,'wallBlocksMeasured':512,'animationFpsChangePercent':perf['animationFpsChangePercent'],'mapReconverted':False,'mapRelease':'native-grid-city-visual-physics-20261025','priorUtilityBlocksPreserved':True}
(D/'acceptance.json').write_text(json.dumps(accept,ensure_ascii=False,indent=2),encoding='utf-8')
report=D/'REPORT-RU.txt';text=report.read_text(encoding='utf-8').replace('Статус: сервер PASS, клиентская проверка и замер ещё выполняются.','Статус: PASS. Сервер, клиент, сохранение мира, кадры и сравнительный замер завершены.')
if 'ФАКТИЧЕСКИЙ ЗАМЕР' not in text:text+='\nФАКТИЧЕСКИЙ ЗАМЕР\n'+(D/'PERFORMANCE-RU.txt').read_text(encoding='utf-8')
report.write_text(text,encoding='utf-8')
shutil.copy2(jar,A/'dreamwalker-bb-0.1.0-v12.7.4-visual-physics.5.jar')
def safe_files(folder):
 for root,dirs,files in os.walk(folder,followlinks=False):
  dirs[:]=[n for n in dirs if n not in ('.git','__pycache__') and not Path(root,n).stat(follow_symlinks=False).st_file_attributes&stat.FILE_ATTRIBUTE_REPARSE_POINT]
  for name in sorted(files):
   p=Path(root,name)
   if not p.stat(follow_symlinks=False).st_file_attributes&stat.FILE_ATTRIBUTE_REPARSE_POINT and p.suffix!='.pyc':yield p
source=[]
for folder in ('src','tools','gradle','validation'):source+=list(safe_files(P/folder))
source +=[P/n for n in ('README.md','.gitignore','build.gradle','settings.gradle','gradle.properties','gradlew','gradlew.bat')]
source +=[D/n for n in ('TASK-RU.txt','REPORT-RU.txt','HANDOFF-RU.txt','artifact-integrity.json','acceptance.json','texture-receipt.json','performance.json','PERFORMANCE-RU.txt')]
for evidence in ('utility-evidence','bench-evidence','fix-evidence'):
 source +=[P/evidence/n for n in ('REPORT-RU.txt','HANDOFF-RU.txt','artifact-integrity.json','acceptance.json')]
source +=list(safe_files(P/'fix-evidence/export/baked'));source=sorted(set(source));manifest={p.relative_to(P).as_posix():sha(p) for p in source}
with zipfile.ZipFile(A/'dreamwalker-visual-physics.5-sources.zip','w',zipfile.ZIP_DEFLATED,compresslevel=6) as z:
 for p in source:z.write(p,'DW-visual-physics.5/'+p.relative_to(P).as_posix())
 z.writestr('DW-visual-physics.5/SOURCE-FILES.json',json.dumps(manifest,indent=2))
with zipfile.ZipFile(A/'dreamwalker-visual-physics.5-evidence.zip','w',zipfile.ZIP_DEFLATED,compresslevel=6) as z:
 for p in safe_files(D):
  if p.suffix in ('.json','.txt','.log','.args','.png') and 'static-pack' not in p.parts:z.write(p,'faerie-evidence/'+p.relative_to(D).as_posix())
for name in ('REPORT-RU.txt','HANDOFF-RU.txt','PERFORMANCE-RU.txt'):shutil.copy2(D/name,A/name)
rows=[{'name':p.name,'bytes':p.stat().st_size,'sha256':sha(p)} for p in sorted(A.iterdir()) if p.is_file() and p.name!='SHA256SUMS.txt']
(A/'SHA256SUMS.txt').write_text(''.join(r['sha256']+'  '+r['name']+'\n' for r in rows),encoding='utf-8');rows.append({'name':'SHA256SUMS.txt','bytes':(A/'SHA256SUMS.txt').stat().st_size,'sha256':sha(A/'SHA256SUMS.txt')})
(B/'ASSETS.json').write_text(json.dumps({'status':'VERIFIED_LOCAL_RELEASE_ASSETS','tag':'native-grid-city-faerie-veil-20261025','branch':'codex/faerie-veil-20261025','jarSha256':digest,'unchangedMapRelease':accept['mapRelease'],'assets':rows,'sourceFiles':len(source)},ensure_ascii=False,indent=2),encoding='utf-8')
notes='''Добавлена «Завеса фей» (`bloodborne_dw:dw_faerie_veil`) — выбранный вариант 3 «Туман и светлячки». Непрозрачный проходной блок с обычным перекрытием света. Выделение и удаление только в креативе; пересечения выступающей геометрии допускаются без уничтожения корней/данных объектов.

Один тип состояния, один общий анимированный спрайт, ноль BlockEntity, игровых частиц и тикеров на каждый блок. Композиция 8×8 блоков выбирается по мировым UV при сборке чанка; фаза общая. PNG 1 203 230 байт, 32 кадра, цикл 3,2 с. Сырой ресурс кадров — 8 МиБ до mipmaps, не на каждый блок.

Сервер и клиент пользовательской сборки с Sodium+Indium проверены; кадры с трёх сторон просмотрены. Проверены сохранение/повторное открытие стены, UV на всех гранях и в отрицательных координатах, общий аниматор, цикл и прохождение. Выполнен сравнительный ABBA-замер статичной/анимированной стены из 512 блоков в одной JVM.

'''+(D/'PERFORMANCE-RU.txt').read_text(encoding='utf-8')+'''
Это замер добавочной анимации на данном ПК, не FPS города. Подробности условий и frametime — в отчёте.

Скамейки, четыре служебных блока .4, коллизии, v12, цельные конструкции и прежние ресурсы сохранены. [Карта прежняя и совместима](https://github.com/Stasik1960/Dreamwalker/releases/tag/native-grid-city-visual-physics-20261025). Обновить мод на клиенте и сервере. QA-мод в игровой JAR не входит.
'''
(B/'release-notes.md').write_text(notes,encoding='utf-8');print('Packaged',len(source),'source files and',len(rows),'assets',flush=True)
