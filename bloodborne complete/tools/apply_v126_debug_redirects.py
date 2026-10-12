"""Reserve all historic numbers; resolve retired NEW part labels to whole types."""
from pathlib import Path
import json
ROOT=Path(__file__).resolve().parents[1];RES=ROOT/'src/architecture/resources/bloodborne_dw'
def read(p):return json.loads(p.read_text(encoding='utf8'))
def main():
    debug=read(RES/'debug_catalogue.json');metadata=read(RES/'imported-assembly-v126.json');before=[(r['temporaryId'],r['registryId']) for r in debug['entries']]
    protected={r['registryId'] for r in read(ROOT/'docs/v12.4-dedup/baseline-content.json')['originalRows']}
    count=0
    for row in debug['entries']:
        target=metadata['hiddenPartRedirects'].get(row['registryId'])
        if target:
            assert row['registryId'].startswith('bloodborne_dw:source_') and row['registryId'] not in protected
            row['canonicalRegistryId']=target;count+=1
    assert count==7 and before==[(r['temporaryId'],r['registryId']) for r in debug['entries']]
    (RES/'debug_catalogue.json').write_text(json.dumps(debug,ensure_ascii=False,indent=2)+'\n',encoding='utf8',newline='\n');print('PASS:7 NEW part label redirects;1322number/registrybindingsunchanged')
if __name__=='__main__':main()
