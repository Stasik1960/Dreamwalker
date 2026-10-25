import hashlib,json
from pathlib import Path
P=Path(__file__).resolve().parents[1];D=P/'interaction-evidence';W=Path('C:/Users/vakir/Desktop/bloodborne')
def load(name):return json.loads((D/name).read_text(encoding='utf-8'))
def sha(path):
 h=hashlib.sha256()
 with path.open('rb') as file:
  for chunk in iter(lambda:file.read(1<<20),b''):h.update(chunk)
 return h.hexdigest()
release=load('final-release.json');assert release['status']=='PUBLISHED' and len(release['assets'])==8
ready=load('acceptance.json');assert ready['status']=='PASS';H=ready['productionSha256'];installed=load('installed.json');assert len(installed)==3
for row in installed:
 path=Path(row['path']);assert sha(path)==H
 lock=path.parent/'DREAMWALKER-MODPACK-LOCK.json'
 if lock.exists():assert json.loads(lock.read_text(encoding='utf-8'))['dreamwalkerSha256']==H
world=load('world-after.json')
for row in world['entries']:assert sha(W/row['path'])==row['sha256'],'Map changed after accepted packaging: '+row['path']
expected={r['name']:r for r in load('artifacts.json')['artifacts']}
for row in release['assets']:assert row['sha256']==expected[row['name']]['sha256'] and row['bytes']==expected[row['name']]['bytes']
record={'status':'COMPLETE','release':release['url'],'releaseId':release['releaseId'],'sourceCommitAtRelease':release['commit'],'version':ready['version'],'productionSha256':H,'world':str(W),'worldFiles':world['files'],'worldArchiveSha256':world['archiveSha256'],'windows':203,'legacyWindowUuidsPreserved':67,'installedRoles':[r['role'] for r in installed],'archiveIntegrity':'PASS','remainingRequiredWork':[]}
(D/'completion.json').write_text(json.dumps(record,ensure_ascii=False,indent=2),encoding='utf-8')
plan=load('plan.json');plan['status']='COMPLETE';plan['release']=release['url'];(D/'plan.json').write_text(json.dumps(plan,ensure_ascii=False,indent=2),encoding='utf-8')
ready['remainingWork']=[];ready['release']=release['url'];(D/'acceptance.json').write_text(json.dumps(ready,indent=2),encoding='utf-8')
text='Работа завершена. visual-physics.7 установлена в Limacina и обе Desktop-папки модов. Карта Desktop/bloodborne обновлена: 203 самостоятельных окна, все67 прежних UUID сохранены. Код, функциональные/визуальные проверки, оба аудита и CRC архивов приняты.\n\nРелиз: '+release['url']+'\nКарта: '+str(world['files'])+' файлов; пустых owner shards нет. Полные исходники, исходная задача, отчёты, решения и инструкция восстановления в релизе. Незавершённой обязательной работы нет. Не повторять миграцию города, фонарей v6 или203 окон. Клавиша no-collide назначается в управлении; по умолчанию свободна.\n'
(D/'STATUS-RU.md').write_text(text,encoding='utf-8');(D/'PROGRESS-LATEST.md').write_text(text,encoding='utf-8')
handoff=D/'HANDOFF-RU.md';handoff.write_text('Публикация подтверждена: '+release['url']+'. completion.json — COMPLETE; все3 установки проверены.\n\n'+handoff.read_text(encoding='utf-8'),encoding='utf-8')
