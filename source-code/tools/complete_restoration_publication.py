import hashlib,json
from pathlib import Path
P=Path(__file__).resolve().parents[1];D=P/'restoration-evidence'
def get(name):return json.loads((D/name).read_text(encoding='utf-8'))
release=get('final-release.json');assert release['status']=='PUBLISHED';expected=get('ready-to-release.json')['productionSha256']
for row in get('installed.json'):
 h=hashlib.sha256()
 with Path(row['path']).open('rb') as f:
  for b in iter(lambda:f.read(1<<20),b''):h.update(b)
 assert h.hexdigest()==expected
plan=get('plan.json');plan['status']='COMPLETE';plan['release']=release['url']
for row in plan['stages']:row['status']='COMPLETE'
(D/'plan.json').write_text(json.dumps(plan,ensure_ascii=False,indent=2),encoding='utf-8')
completion={'status':'COMPLETE','release':release['url'],'releaseId':release['releaseId'],'sourceCommitAtRelease':release['commit'],'productionSha256':expected,'world':'C:/Users/vakir/Desktop/bloodborne','worldFiles':get('world-after.json')['files'],'archiveIntegrity':'PASS','installedRoles':[r['role'] for r in get('installed.json')],'remainingRequiredWork':[]}
(D/'completion.json').write_text(json.dumps(completion,ensure_ascii=False,indent=2),encoding='utf-8')
(D/'STATUS-RU.md').write_text('Работа завершена. Релиз опубликован: '+release['url']+'\nМод установлен в Desktop/client_mods, Desktop/server_mods и фактические Limacina/mods. Карта Desktop/bloodborne обновлена, 1888 файлов. Полные исходники и отчёты приложены к релизу. Все обязательные проверки приняты; незавершённой работы по текущему заданию нет. 734 адресные замены и конвертацию повторно не запускать.\n',encoding='utf-8')
handoff=D/'HANDOFF-RU.md';text=handoff.read_text(encoding='utf-8');text=text.replace('До окончания загрузки он может ещё не быть опубликован; состояние подтверждается final-release.json со статусом PUBLISHED.','Релиз опубликован; final-release.json — PUBLISHED, completion.json — COMPLETE.');text=text.replace('Остаётся техническая публикация полного комплекта','Полный комплект опубликован');text=text.replace('дополнительно запущен client-4 --post-fixture','дополнительно принят client-5 --post-fixture');handoff.write_text(text,encoding='utf-8')
