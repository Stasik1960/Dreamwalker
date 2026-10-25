"""Check saved window owners and retention of all 67 legacy UUIDs."""
import json,sys,uuid,zipfile
from pathlib import Path
import world_io as n
P=Path(__file__).resolve().parents[1];D=P/'interaction-evidence';role=sys.argv[1];W=P/'interaction-profiles'/role/'world' if role.startswith('preview-') else Path('C:/Users/vakir/Desktop/bloodborne');run=json.loads((D/role/'map-apply.json').read_text());assert all(r['status']=='COMMITTED' for r in run['results'])
def identity(tag):return str(uuid.UUID(int=sum((v&0xffffffff)<<(96-32*i) for i,v in enumerate(tag.value))))
def cell(region,pos):
 x,y,z=pos;chunk=region.get_chunk(x//16%32,z//16%32);assert chunk is not None
 return next(be.value for be in chunk.nbt().root.value['block_entities'].value if tuple(be.value[k].value for k in ('x','y','z'))==tuple(pos))
legacy=json.loads((D/'legacy-window-data.json').read_text())['windows'];oldIds=set();cache={}
with zipfile.ZipFile(D/'bloodborne-before-interactions.zip') as archive:
 for row in legacy:
  p=row['pos'];rel=f'region/r.{p[0]//512}.{p[2]//512}.mca'
  if rel not in cache:cache[rel]=n.RegionFile(archive.read('bloodborne/'+rel))
  oldIds.add(identity(cell(cache[rel],p)['resident'].value['uuid']))
cache={};newIds=set()
for group in run['results']:
 for row in group['installed']:
  p=tuple(map(int,row['root'].split(',')));rel=f'region/r.{p[0]//512}.{p[2]//512}.mca'
  if rel not in cache:cache[rel]=n.RegionFile((W/rel).read_bytes())
  be=cell(cache[rel],p);resident=be['resident'].value;actual=identity(resident['uuid']);assert actual==row['uuid'];assert resident['id'].value=='bloodborne_dw:source_shutter_window';assert tuple(resident['root'].value)==p;newIds.add(actual)
assert len(oldIds)==67 and len(newIds)==203 and oldIds<=newIds
(D/role/'window-identities.json').write_text(json.dumps({'status':'PASS','savedIndependentWindows':203,'legacyUuidsPreserved':67,'allRootsAndRegistryIdsMatch':True},indent=2),encoding='utf-8')
