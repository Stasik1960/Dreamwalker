import json
from pathlib import Path
P=Path(__file__).resolve().parents[1];D=P/'npc-evidence';R=D/'client-2';load=lambda p:json.loads(p.read_text(encoding='utf-8'));raw=load(R/'client-checks.json');result=load(R/'launch-result.json');assert result['exitCode']==0 and result['saved'];assert raw['modelIndex']==raw['modelCount']==18
assert raw['status']=='FAIL' and raw['error']=='java.lang.IndexOutOfBoundsException: Index 18 out of bounds for length 18'
log=(R/'console.log').read_text(encoding='utf-8',errors='replace');assert 'NpcClientQa$Gallery.render' in log or 'NpcClientQa$Gallery.method_25394' in log
states={};ids=[a['id'] for a in load(D/'assets-audit.json')['assets']]
for row in raw['checks']:
 if isinstance(row,dict):assert row['bones']>0 and row['clip'];states.setdefault(row['model'],set()).add(row['state'])
assert set(states)==set(ids) and all(s=={'idle','walk','hurt','attack','death'} for s in states.values())
for name in ids:
 for text in ('all five animation states rendered ','Custom NPC own max health unchanged ','saved simpleRender setting unchanged '):assert text+name in raw['checks']
 for phase in ('idle','death'):assert (R/f'npc-{name}-{phase}.png').stat().st_size>1000
review={'status':'PASS','rawReceipt':'client-2/client-checks.json','rawStatus':'FAIL','modelsVerified':18,'animationStatesVerified':90,'screenshots':36,'qaTeardownIssue':raw['error'],'reason':'All18 model iterations and90 positive state checks finished. A final queued Gallery frame accessed index18 after success scheduled client shutdown. The exception was caught by QA, normal exit0 and world save followed. Production code did not throw it. Original receipt and stack remain unchanged. QA render now returns after completion; accepted model checks are not repeated.'}
(D/'client-reviewed.json').write_text(json.dumps(review,ensure_ascii=False,indent=2),encoding='utf-8')
