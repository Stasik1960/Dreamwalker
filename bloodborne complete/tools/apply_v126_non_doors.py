"""Explicit user-rejected candidates must not retain an old door mechanism."""
from pathlib import Path
import json
ROOT=Path(__file__).resolve().parents[1];RES=ROOT/'src/architecture/resources/bloodborne_dw'
def read(p):return json.loads(p.read_text(encoding='utf8'))
def main():
    byid={r['temporaryId']:r['registryId'] for r in read(RES/'debug_catalogue.json')['entries']};rows=[]
    for number in ['90247','93156','90143','93159','90777','93147']:
        registry=byid[number];assert registry.startswith('bloodborne_dw:source_');p=RES/'composite'/(registry.split(':')[1]+'.json');spec=read(p);before=spec.get('openable',False);spec['openable']=False
        p.write_text(json.dumps(spec,ensure_ascii=False,indent=2)+'\n',encoding='utf8',newline='\n');rows.append({'id':number,'registryId':registry,'wasOpenable':before,'openable':False,'visualPosesUnchanged':True})
    p=ROOT/'docs/v12.6/doors/explicit-non-doors.json';p.write_text(json.dumps({'status':'PASS_EXPLICIT_USER_NON_DOOR_FLAGS_NATIVE_NOT_RUN','objects':rows},ensure_ascii=False,indent=2)+'\n',encoding='utf8');print(json.dumps(rows))
if __name__=='__main__':main()
