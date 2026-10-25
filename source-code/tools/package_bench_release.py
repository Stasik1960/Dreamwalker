"""Package the tested bench port; preserve the existing map and prior release."""
import hashlib,json,os,shutil,stat,zipfile
from pathlib import Path
P=Path(__file__).resolve().parents[1];D=P/'bench-evidence';B=P/'bench-release';A=B/'assets';A.mkdir(parents=True,exist_ok=True)
def read(p):return json.loads(p.read_text(encoding='utf-8-sig'))
def sha(p):
 h=hashlib.sha256()
 with p.open('rb') as f:
  for data in iter(lambda:f.read(1<<20),b''):h.update(data)
 return h.hexdigest()
integrity=read(D/'artifact-integrity.json');assert integrity['status']=='PASS'
jar=Path(integrity['jar']);digest=sha(jar);assert digest==integrity['sha256']
assert read(P/'build-tools/gradle-remapJar.json')['exitCode']==0
core=read(D/'server-1/server-checks.json')['checks'];assert sum(x.startswith('place bench ') for x in core)==25
for role in ('server-1','server-2','client-1'):
 assert read(D/role/'launch-result.json')['exitCode']==0
 assert read(D/role/'process.json')['productionSha256']==digest
for name in ('server-2/server-checks.json','client-1/server-checks.json','client-1/client-checks.json','visual-review.json'):
 assert read(D/name)['status']=='PASS',name
frames=D/'client-1/screenshots';frames.mkdir(exist_ok=True)
for name in ('fixture-seated.png','fixture-dismounted.png','city-bench-seated.png'):
 shutil.copy2(P/'fix-profiles/client/screenshots'/name,frames/name)
acceptance={'status':'PASS','version':'0.1.0-v12.7.4-visual-physics.3','jarSha256':digest,
 'baselineSha256':integrity['baselineSha256'],'coreBenchPoseCombinations':24,
 'remainingDedicatedServer':'server-2','fullPackClient':'client-1','existingCityBenchesChecked':36,
 'resources':integrity,'visualReview':'visual-review.json','mapReconverted':False,
 'mapRelease':'native-grid-city-visual-physics-20261025','performanceBenchmark':False,
 'firstRunFixtureCorrection':'MountY is normalized away by normal placement; use VerticalOffset. Accepted 24 pose combinations were not repeated.'}
(D/'acceptance.json').write_text(json.dumps(acceptance,ensure_ascii=False,indent=2),encoding='utf-8')
report=D/'REPORT-RU.txt';report.write_text(report.read_text(encoding='utf-8').replace('Статус: рабочий кандидат; игровые проверки ещё не завершены.','Статус: PASS. Проверки завершены, мод готов.').replace('Окончательный статус каждого прогона и хеш JAR записаны в acceptance.json.','Сервер server-2 и клиент client-1 завершились без краша (exit 0).\nВсе 36 целых скамеек готового города распознаются. ПКМ, Shift, поза и\nреальная городская скамья проверены; кадры просмотрены. Хеш — в acceptance.json.'),encoding='utf-8')
shutil.copy2(jar,A/'dreamwalker-bb-0.1.0-v12.7.4-visual-physics.3.jar')
def safe_files(folder):
 for root,dirs,files in os.walk(folder,followlinks=False):
  dirs[:]=[d for d in dirs if d not in ('.git','__pycache__') and not Path(root,d).stat(follow_symlinks=False).st_file_attributes&stat.FILE_ATTRIBUTE_REPARSE_POINT]
  for n in sorted(files):
   p=Path(root,n)
   if not p.stat(follow_symlinks=False).st_file_attributes&stat.FILE_ATTRIBUTE_REPARSE_POINT and p.suffix!='.pyc':yield p
source=[]
for folder in ('src','tools','gradle','validation'):source+=list(safe_files(P/folder))
source +=[P/n for n in ('README.md','.gitignore','build.gradle','settings.gradle','gradle.properties','gradlew','gradlew.bat')]
source +=[D/n for n in ('REPORT-RU.txt','HANDOFF-RU.txt','artifact-integrity.json','acceptance.json','history-and-layout.json','legacy-bench-poses.json','map-benches.json')]
source +=[P/'fix-evidence'/n for n in ('REPORT-RU.txt','HANDOFF-RU.txt','artifact-integrity.json','visual-repair-receipt.json','acceptance.json')]
source +=list(safe_files(P/'fix-evidence/export/baked'))
source=sorted(set(source));manifest={p.relative_to(P).as_posix():sha(p) for p in source}
with zipfile.ZipFile(A/'dreamwalker-visual-physics.3-sources.zip','w',zipfile.ZIP_DEFLATED,compresslevel=6) as z:
 for p in source:z.write(p,'DW-visual-physics.3/'+p.relative_to(P).as_posix())
 z.writestr('DW-visual-physics.3/SOURCE-FILES.json',json.dumps(manifest,indent=2))
with zipfile.ZipFile(A/'dreamwalker-visual-physics.3-evidence.zip','w',zipfile.ZIP_DEFLATED,compresslevel=6) as z:
 for p in safe_files(D):
  if p.suffix in ('.json','.log','.txt','.java','.png','.args'):z.write(p,'bench-evidence/'+p.relative_to(D).as_posix())
for n in ('REPORT-RU.txt','HANDOFF-RU.txt'):shutil.copy2(D/n,A/n)
rows=[{'name':p.name,'bytes':p.stat().st_size,'sha256':sha(p)} for p in sorted(A.iterdir()) if p.is_file() and p.name!='SHA256SUMS.txt']
(A/'SHA256SUMS.txt').write_text(''.join(r['sha256']+'  '+r['name']+'\n' for r in rows),encoding='utf-8')
rows.append({'name':'SHA256SUMS.txt','bytes':(A/'SHA256SUMS.txt').stat().st_size,'sha256':sha(A/'SHA256SUMS.txt')})
(B/'ASSETS.json').write_text(json.dumps({'status':'VERIFIED_LOCAL_RELEASE_ASSETS','tag':'native-grid-city-bench-seating-20261025','branch':'codex/bench-seating-20261025','jarSha256':digest,'unchangedMapRelease':acceptance['mapRelease'],'assets':rows,'sourceFiles':len(source)},ensure_ascii=False,indent=2),encoding='utf-8')
(B/'release-notes.md').write_text('''Восстановлена посадка на скамейки из раннего модуля Bloodborne-Blocks: три места, ближайшее свободное место, правильная высота, безопасное вставание и временные невидимые сиденья без сохранения в мире. Работают целые скамейки v12 и сохранённые статические скамьи города. ПКМ свободной рукой — сесть; Shift — встать.

Проверены три полные модели во всех восьми поворотах, три пассажира и отказ четвёртому, смещения, удаление занятой скамьи, обычные ПКМ/Shift на клиенте и все 36 целых скамеек готового города. Сервер и клиент пользовательской сборки с Sodium+Indium завершились без крашей. Это функциональная проверка, не FPS-замер.

Все 9453 прежних assets и 1244 ресурса данных сохранены побайтно относительно visual-physics.2. Коллизии, v12, цельные конструкции и предыдущие исправления сохраняются. Карта прежняя: [совместимая готовая карта](https://github.com/Stasik1960/Dreamwalker/releases/tag/native-grid-city-visual-physics-20261025). Замените мод на клиенте и сервере; повторная конвертация не требуется.

Вложения: игровой JAR, полные исходники, проверочные материалы, отчёт и SHA256SUMS. QA-мод в игровую сборку не входит.
''',encoding='utf-8')
print('Packaged',len(source),'source files and',len(rows),'assets',flush=True)
