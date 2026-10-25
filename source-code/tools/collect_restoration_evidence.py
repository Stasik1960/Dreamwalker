import hashlib,json,shutil
from pathlib import Path
P=Path(__file__).resolve().parents[1];D=P/'restoration-evidence'
for attempt in (3,4,5):
 folder=D/f'client-{attempt}';folder.mkdir(exist_ok=True)
 for f in (P/f'restoration-profiles/client-{attempt}/screenshots').glob('*.png'):
  target=folder/f.name;shutil.copy2(f,target);assert hashlib.sha256(f.read_bytes()).digest()==hashlib.sha256(target.read_bytes()).digest()
def get(p):return json.loads((D/p).read_text(encoding='utf-8')) if (D/p).is_file() else None
result={'status':'AWAITING_FINAL_VISUAL_REVIEW','productionSha256':get('apply-process.json')['productionSha256'],'serverRegression':'server-3/checks.json (24 passed; test Actor corrected in server-4)','serverFollowup':get('server-4/checks.json')['status'],'clientUserPack':get('client-3/client-probe.json')['status'],'mapAudit':get('final-map-audit.json')['status'],'frozenResources':get('frozen-resources-audit.json')['status'],'postFixture':get('client-5/post-fixture.json') or get('client-4/post-fixture.json')}
(D/'acceptance.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
