"""Read-only review diagnostics for root-owned gallery tooling and input payloads."""
from collections import Counter,defaultdict
from pathlib import Path
import json,hashlib
ROOT=Path(__file__).resolve().parents[1];DOC=ROOT/'docs/v12.5-city-review'
def read(p):return json.loads(p.read_text(encoding='utf-8-sig'))
def main():
 city=read(DOC/'citycandidate/city-final-source-data.json')
 platform_files=[DOC/'assemblies/gallery-review-platforms-final.json',DOC/'doors/gallery-door-platforms-final.json']
 entries=[('city',c) for c in city['owners']]
 groups=[]
 for p in platform_files:
  for g in read(p)['platforms']:
   groups.append(g);entries.extend((g['key'],c) for c in g['components'])
 owners=defaultdict(list);uuids=defaultdict(list);native=[];payload_findings=[]
 for key,c in entries:
  root=key,tuple(c['relativePosition']);owners[root].append(c['registryId'])
  if c.get('instanceUuid'):uuids[c['instanceUuid']].append((key,c['registryId']))
  if not c['registryId'].startswith('bloodborne_dw:source_'):
   native.append({'group':key,'registryId':c['registryId'],'relativePosition':c['relativePosition'],
    'properties':c['properties'],'payload':c.get('payload',{}),'mountY':c.get('mountY',0),'sourceShift':c.get('sourceShift')})
  payload=c.get('payload',{})
  if 'sourceShift' not in c and any(payload.get('SourceShift',[0,0,0])):
   payload_findings.append({'group':key,'registryId':c['registryId'],'issue':'AUTHOR_IGNORES_PAYLOAD_ONLY_SOURCE_SHIFT'})
  if payload.get('VerticalOffset',0) and c.get('mountY',0):
   payload_findings.append({'group':key,'registryId':c['registryId'],'issue':'CHECK_DOUBLE_MOUNT_Y_VS_PERSISTENT_VERTICAL_OFFSET','payload':payload,'mountY':c['mountY']})
  if c['properties'].get('profile','base')!='base' or c['properties'].get('open','false')!='false':
   payload_findings.append({'group':key,'registryId':c['registryId'],'issue':'OPEN_OR_ALT_STATE_IN_INPUT','properties':c['properties']})
 result={'status':'READONLY_DIAGNOSTICS','reviewPlatforms':len(groups),'cityOwners':len(city['owners']),
  'reviewComponents':len(entries)-len(city['owners']),'cityPassThroughBlocks':len(city['passThroughBlocks']),
  'nativeComponents':native,'payloadFindings':payload_findings,
  'coincidentOwnerRoots':[{'group':k[0],'position':list(k[1]),'registryIds':v} for k,v in owners.items() if len(v)>1],
  'repeatedCityUuids':{k:v for k,v in uuids.items() if len(v)>1},
  'sourceFiles':{str(p.relative_to(ROOT)):hashlib.sha256(p.read_bytes()).hexdigest() for p in platform_files+[DOC/'citycandidate/city-final-source-data.json']}}
 (DOC/'style-gallery-tool-review-input-diagnostics.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
if __name__=='__main__':main()
