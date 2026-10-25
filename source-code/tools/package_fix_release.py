"""Package only after recorded game and visual acceptance; never reconvert a map."""
import hashlib,json,os,shutil,stat,zipfile
from pathlib import Path
P=Path(__file__).resolve().parents[1];D=P/'fix-evidence';B=P/'fix-release';A=B/'assets';A.mkdir(parents=True,exist_ok=True)
def read(path):return json.loads(path.read_text(encoding='utf-8-sig'))
def sha(path):
 h=hashlib.sha256()
 with path.open('rb') as f:
  for b in iter(lambda:f.read(1<<20),b''):h.update(b)
 return h.hexdigest()
for role in ('server-3','client-2','client-3','client-4'):
 assert read(D/role/('server-checks.json' if role.startswith('server') else 'client-checks.json'))['status']=='PASS'
 assert read(D/role/'launch-result.json')['exitCode']==0
assert read(D/'visual-review.json')['status']=='PASS'
integrity=read(D/'artifact-integrity.json');assert integrity['status']=='PASS' and integrity['markerModelOnlyDebugLineNumbersChanged']
jar=Path(integrity['jar']);assert sha(jar)==integrity['sha256'];assert read(P/'build-tools/gradle-remapJar.json')['exitCode']==0
trace=[json.loads(s) for s in (D/'client-3/light-trace.jsonl').read_text(encoding='utf-8').splitlines()];assert trace and all(0<=v<=1 for r in trace for v in r['brightness'])
O=Path('C:/Users/vakir/.codex/worktrees/3f07/DW/audits/Dreamwalker-BB/2026-10-25-visual-diagnostics');shutil.copy2(O.parent/'2026-10-25-compatibility-audit/REPORT-RU.txt',D/'COMPATIBILITY-AUDIT-RU.txt')
dst=D/'client-4/screenshots';dst.mkdir(exist_ok=True);shutil.copy2(P/'fix-profiles/client/screenshots/fixed-6.png',dst)
native=[json.loads(s) for s in (D/'client-4/indium-ao.jsonl').read_text().splitlines()];assert native and all(0<=r['normalizedAo']<=1 for r in native)
acceptance={'status':'PASS','version':'0.1.0-v12.7.4-visual-physics.2','jarSha256':sha(jar),'coreAcceptedJarSha256':integrity['acceptedCoreJarSha256'],'changedAfterCoreAcceptance':integrity['changedEntriesAfterCoreAcceptance'],'finalClientFollowup':['client-3','client-4'],'worldEdit':'server-3','fullPackClient':'client-2','finalAoBrightnessSamples':len(trace),'nativeOutOfRangeSamplesSaturated':len(native),'allEffectiveBrightnessNormalized':True,'unchangedExistingAssets':9448,'mapReconverted':False,'mapRelease':'native-grid-city-visual-physics-20261025','mapZipSha256':'da6d02e3e86e1780576359033f908a0a5cb22a255348939eae1637a44423c636','performanceBenchmark':False}
(D/'acceptance.json').write_text(json.dumps(acceptance,ensure_ascii=False,indent=2),encoding='utf-8')
report=D/'REPORT-RU.txt';s=report.read_text(encoding='utf-8').replace('Статус: ожидается адресная проверка последней правки AO; не финальный релиз.','Статус: PASS. Исправленный мод готов, карта совместима без переноса.').replace('Адресная проверка лестницы в client-4 ещё ожидается.','client-4 подтвердил устранение чёрных площадок лестницы с включённым AO.').replace('Последние правки касаются только клиентского AO; их проверяют client-3/4.','client-3/4 PASS проверили оба пути AO; client-3 также проверил точный предмет\nцельного объекта после ответа сервера. После основного прогона изменены только\nклиентские классы освещения и диагностики, что проверено по всем entry JAR.');report.write_text(s,encoding='utf-8')
name='dreamwalker-bb-0.1.0-v12.7.4-visual-physics.2.jar';shutil.copy2(jar,A/name)
def safe_files(folder):
 for root,dirs,files in os.walk(folder,followlinks=False):
  dirs[:]=[d for d in dirs if d not in ('.git','__pycache__') and not (Path(root,d).stat(follow_symlinks=False).st_file_attributes&stat.FILE_ATTRIBUTE_REPARSE_POINT)]
  for name in sorted(files):
   p=Path(root,name)
   if not p.is_symlink() and not p.stat(follow_symlinks=False).st_file_attributes&stat.FILE_ATTRIBUTE_REPARSE_POINT and p.suffix!='.pyc':yield p
source=[]
for folder in ('src','tools','gradle','validation'):source+=list(safe_files(P/folder))
source +=[P/n for n in ('README.md','.gitignore','build.gradle','settings.gradle','gradle.properties','gradlew','gradlew.bat')]
source +=[D/n for n in ('REPORT-RU.txt','HANDOFF-RU.txt','COMPATIBILITY-AUDIT-RU.txt','artifact-integrity.json','visual-repair-receipt.json','acceptance.json')]
source +=list(safe_files(D/'export/baked'))
source=sorted(set(source));manifest={str(p.relative_to(P)).replace('\\','/'):sha(p) for p in source}
with zipfile.ZipFile(A/'dreamwalker-visual-physics.2-sources.zip','w',zipfile.ZIP_DEFLATED,compresslevel=6) as z:
 for p in source:z.write(p,'DW-visual-physics.2/'+str(p.relative_to(P)).replace('\\','/'))
 z.writestr('DW-visual-physics.2/SOURCE-FILES.json',json.dumps(manifest,indent=2))
with zipfile.ZipFile(A/'dreamwalker-visual-physics.2-evidence.zip','w',zipfile.ZIP_DEFLATED,compresslevel=6) as z:
 for p in safe_files(D):
  if p.suffix in ('.json','.jsonl','.log','.txt','.png','.args'):z.write(p,'fix-evidence/'+str(p.relative_to(D)).replace('\\','/'))
 for p in safe_files(O/'screenshots'):
  if p.suffix=='.png':z.write(p,'original-screenshots/'+p.name)
for n in ('REPORT-RU.txt','HANDOFF-RU.txt'):shutil.copy2(D/n,A/n)
rows=[{'name':p.name,'bytes':p.stat().st_size,'sha256':sha(p)} for p in sorted(A.iterdir()) if p.is_file() and p.name not in ('SHA256SUMS.txt','ASSETS.json')]
(A/'SHA256SUMS.txt').write_text(''.join(r['sha256']+'  '+r['name']+'\n' for r in rows),encoding='utf-8');rows.append({'name':'SHA256SUMS.txt','bytes':(A/'SHA256SUMS.txt').stat().st_size,'sha256':sha(A/'SHA256SUMS.txt')})
bundle={'status':'VERIFIED_LOCAL_RELEASE_ASSETS','tag':'native-grid-city-visual-fixes-20261025','branch':'codex/visual-compat-fixes-20261025','jarSha256':sha(jar),'unchangedMapRelease':acceptance['mapRelease'],'assets':rows,'sourceFiles':len(source)};(B/'ASSETS.json').write_text(json.dumps(bundle,ensure_ascii=False,indent=2),encoding='utf-8')
notes='''Исправлены пропавшие базовые грани стены/мостовой, средняя кнопка и ошибки совместимости из аудита. Исправлено переполнение цвета при AO на выступающих моделях. Все 9448 прежних assets и точные коллизии/v12/цельные конструкции сохранены побайтно.

Проверено на исходном модпаке с Sodium 0.5.13 + Indium 1.0.36; Iris без шейдеров. Серверный WorldEdit, BEFORE/AFTER, защита v12, исключение no-collide, Reach, предпросмотр и клиентская Statistics → Items со skillet: PASS. Это функциональные проверки, не FPS-бенчмарк.

**Карта остаётся прежней:** используйте карту из [предыдущего релиза](https://github.com/Stasik1960/Dreamwalker/releases/tag/native-grid-city-visual-physics-20261025). Повторная конвертация не нужна. Замените мод на клиенте и сервере, оставив по одному JAR Dreamwalker.

Вложения: исправленный JAR, полные собираемые исходники, доказательства, отчёт, передача работы и SHA256SUMS. Тестовые моды в JAR не входят. Подробнее об ограничениях плоского освещения при AO OFF — в REPORT-RU.txt.
''';(B/'release-notes.md').write_text(notes,encoding='utf-8');print('Packaged',len(source),'source files and',len(rows),'assets')
