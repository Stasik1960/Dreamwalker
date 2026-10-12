"""Verify both read-only supplied source archives against the task baseline."""
from pathlib import Path
import hashlib,json
ROOT=Path(__file__).resolve().parents[1]; REF=ROOT.parent/'Bloodborne-Blocks/reference-inputs/bbmc-v15-v16'
expected={'bbmc_v15_resource.zip':'29b31e3744af5794ae95ebe4c5474e3b70dfb08c143355bce815c08ceef09a36',
          'bbmc_v16_map.zip':'39e83f2941b768fd2ae9f84d8f57dc76aec78bd926dc5e38eab3059251d2bb08'}
rows=[]
for name,digest in expected.items():
    path=REF/name; h=hashlib.sha256()
    with path.open('rb') as stream:
        for block in iter(lambda:stream.read(4*1024*1024),b''):h.update(block)
    actual=h.hexdigest();rows.append({'file':str(path),'bytes':path.stat().st_size,'sha256':actual,'status':'PASS' if actual==digest else 'FAIL'})
result={'schema':'dreamwalker-v124-reference-input-preservation','status':'PASS' if all(r['status']=='PASS' for r in rows) else 'FAIL','sourceMapChanged':False if all(r['status']=='PASS' for r in rows) else 'UNKNOWN','files':rows}
(ROOT/'docs/v12.4-dedup/reference-input-preservation.json').write_bytes((json.dumps(result,indent=2)+'\n').encode('utf8'))
assert result['status']=='PASS',result
print(result['status'])
