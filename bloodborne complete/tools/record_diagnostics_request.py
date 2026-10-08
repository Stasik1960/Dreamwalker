"""Append the complete directly supplied diagnostics request without replacing prior tasks."""
from pathlib import Path
import hashlib,json
ROOT=Path(__file__).resolve().parents[1]
source=ROOT/'reports/user-diagnostics-request-2026-10-08/request.txt'
raw=source.read_bytes();request=raw.decode('utf8').replace('\r\n','\n').rstrip('\n')
marker='## Пользовательское дополнение: встроенная диагностика / 2026-10-08'
records=[]
for name in ['TASK.md','PROGRESS.md']:
    path=ROOT/name;before=path.read_bytes();text=before.decode('utf8').replace('\r\n','\n')
    if marker not in text:
        path.write_text(text.rstrip('\n')+'\n\n'+marker+'\n\n'+request+'\n',encoding='utf8')
    current=path.read_text(encoding='utf8').replace('\r\n','\n')
    assert request in current
    records.append({'path':name,'full_request_preserved':True,'sha256':hashlib.sha256(path.read_bytes()).hexdigest()})
manifest={'schema':'dreamwalker-direct-user-diagnostics-request-v1','status':'FULL_REQUEST_RECORDED_IMPLEMENTATION_AND_CURRENT_RUNTIME_PROOFS_PENDING',
    'source':'Direct user message, 2026-10-08','request_file':str(source),'request_sha256':hashlib.sha256(raw).hexdigest(),
    'request_bytes':len(raw),'documents':records,'prior_instructions_replaced':False}
(source.parent/'manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
print(json.dumps({'status':manifest['status'],'sha256':manifest['request_sha256']}))
