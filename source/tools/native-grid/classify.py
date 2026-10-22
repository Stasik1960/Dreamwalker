"""Historic v12 evidence and per-type statue candidates. Never writes a world."""
import hashlib, json, subprocess
from pathlib import Path
P=Path(__file__).resolve().parents[2]
O=P.parent/'research'
HERE=Path(__file__).resolve().parent
REPO=Path(r'C:\Users\vakir\.codex\worktrees\3f07\DW')
blob='HEAD:releases/Dreamwalker-BB/v12/gallery-manifest.json'
raw=subprocess.check_output(['git','show',blob],cwd=REPO)
historic=json.loads(raw)
(O/'historic-v12-gallery-manifest.json').write_bytes(raw)
catalog=json.loads((P/'src/architecture/resources/bloodborne_dw/debug_catalogue.json').read_text(encoding='utf-8-sig'))
entries=catalog['entries']
byid={e['registryId']:e for e in entries}
protected={}
for e in historic['exhibits']:
    rid=e.get('registry','')
    if not rid or e.get('kind') not in ('architecture','block','rp_block','rp_object'):continue
    protected[rid]={'registryId':rid,'catalogueNumber':e['id'],'name':e['name'],'reason':'historic_v12_original','evidence':blob}
    for alias in byid.get(rid,{}).get('aliases',[]):
        protected.setdefault(alias,{'registryId':alias,'catalogueNumber':e['id'],'reason':'historic_v12_alias','evidenceRegistry':rid,'evidence':blob})
result={'schemaVersion':1,'evidence':blob,'evidenceSha256':hashlib.sha256(raw).hexdigest(),'entries':sorted(protected.values(),key=lambda e:e['registryId'])}
(HERE/'protected-v12-ids.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
words=['стату','скульпт','бюст','statue','sculpt','bust']
candidates=[]
for e in entries:
    text=' '.join(str(e.get(k,'')) for k in ('name','registryId','kind')).lower()
    if any(w in text for w in words):
        rid=e['registryId']
        candidates.append({'registryId':rid,'catalogueNumber':e.get('temporaryId'),'name':e.get('name'),'aliases':e.get('aliases',[]),'classification':'whole_statue' if any(w in e.get('name','').lower() for w in words[:3]) else 'unconfirmed_statue_candidate','protectedV12':rid in protected,'models':[],'components':[rid],'basis':'debug_catalogue labelled sculpture; model/gallery verification pending','evidence':'src/architecture/resources/bloodborne_dw/debug_catalogue.json'})
(HERE/'statue-ids.json').write_text(json.dumps({'schemaVersion':1,'status':'CANDIDATES_PENDING_MODEL_VERIFICATION','entries':candidates},ensure_ascii=False,indent=2),encoding='utf-8')
(O/'classification-discovery.json').write_text(json.dumps({'historicKinds':sorted(set(e.get('kind','') for e in historic['exhibits'])),'historicExhibits':len(historic['exhibits']),'protectedRegistryCount':len(protected),'currentCatalogueEntries':len(entries),'statueCandidates':candidates},ensure_ascii=False,indent=2),encoding='utf-8')
