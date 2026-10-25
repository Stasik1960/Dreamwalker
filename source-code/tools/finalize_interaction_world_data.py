"""Remove only lifecycle tombstones produced while relocating the same window UUIDs.

All functional links, flags and counters must still be exactly the original NBT.
Runs after the test server has saved and stopped, before reopening the map.
"""
import json,stat,sys,zipfile
from pathlib import Path
import world_io as n
P=Path(__file__).resolve().parents[1];D=P/'interaction-evidence';role=sys.argv[1];out=D/role
W=P/'interaction-profiles'/role/'world' if role.startswith('preview-') else Path('C:/Users/vakir/Desktop/bloodborne')
run=json.loads((out/'map-apply.json').read_text());assert run['status']=='COMPLETE' and all(r['status']=='COMMITTED' for r in run['results'])
assert sum(r['windows'] for r in run['results'])==203
assert json.loads((out/'launch-result.json').read_text())['saved']
pid=json.loads((out/'process.json').read_text())['pid']
import ctypes
handle=ctypes.windll.kernel32.OpenProcess(0x1000,False,pid)
if handle:
 code=ctypes.c_ulong();ctypes.windll.kernel32.GetExitCodeProcess(handle,ctypes.byref(code));ctypes.windll.kernel32.CloseHandle(handle);assert code.value!=259,'Server must be stopped'
target=W/'data/bloodborne_dw_mechanism_links.dat';assert target.resolve().parent==(W/'data').resolve() and not target.stat().st_file_attributes&stat.FILE_ATTRIBUTE_REPARSE_POINT
with zipfile.ZipFile(D/'bloodborne-before-interactions.zip') as archive:
 member=next(s for s in archive.namelist() if s.endswith('/data/bloodborne_dw_mechanism_links.dat'));raw=archive.read(member)
before=n.decode_nbt(raw,compressed='gzip' if raw[:2]==b'\x1f\x8b' else None).root;after=n.read_nbt(target).root
a=before.value['data'].value;b=after.value['data'].value
assert {k:t for k,t in a.items() if k!='Removed'}=={k:t for k,t in b.items() if k!='Removed'},'Functional mechanism data changed'
old={v.value for v in a['Removed'].value};new={v.value for v in b['Removed'].value};allowed={'ARCHITECTURE|minecraft:overworld|'+entry['uuid'] for row in run['results'] for entry in row['installed']}
assert old<=new and new-old<=allowed,'Unexpected removed mechanism identity'
if after!=before:
 backup=out/'mechanism-links-before-finalize.dat'
 if not backup.exists():backup.write_bytes(target.read_bytes())
 staged=target.with_suffix('.interaction-tmp');staged.write_bytes(raw);staged.replace(target)
assert n.read_nbt(target).root==before
(out/'global-data-finalized.json').write_text(json.dumps({'status':'PASS','restoredLifecycleTombstones':len(new-old),'allFunctionalMechanismDataUnchanged':True},indent=2),encoding='utf-8')
