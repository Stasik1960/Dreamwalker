import json,sys,zipfile
from pathlib import Path
import world_io as n
P=Path(__file__).resolve().parents[1];D=P/'interaction-evidence';role=sys.argv[1];W=P/'interaction-profiles'/role/'world' if role.startswith('preview-') else Path('C:/Users/vakir/Desktop/bloodborne');rows=[]
with zipfile.ZipFile(D/'bloodborne-before-interactions.zip') as archive:
 for member in archive.namelist():
  rel=Path(*Path(member).parts[1:])
  if len(rel.parts)!=2 or rel.parts[0]!='data' or rel.name=='bloodborne_dw_composite_owners.dat':continue
  target=W/rel;raw=archive.read(member);same=target.is_file() and raw==target.read_bytes();keys=[]
  if target.is_file() and not same and rel.suffix=='.dat':
   try:
    a=n.decode_nbt(raw,compressed='gzip' if raw[:2]==b'\x1f\x8b' else None).root;b=n.read_nbt(target).root;same=a==b
    if not same:keys=sorted(k for k in set(a.value)|set(b.value) if a.value.get(k)!=b.value.get(k))
   except Exception as error:keys=[type(error).__name__]
  rows.append({'path':rel.as_posix(),'equal':same,'changedTopKeys':keys})
(D/role/'global-data-audit.json').write_text(json.dumps({'status':'PASS' if all(r['equal'] for r in rows if r['path'].split('/')[-1].startswith('bloodborne_dw_')) else 'REVIEW_REQUIRED','rows':rows},indent=2),encoding='utf-8')
