import ast,hashlib,json,shutil
from pathlib import Path
P=Path(__file__).resolve().parents[1];D=P/'interaction-evidence';checked=[]
for path in (P/'tools').glob('*interaction*.py'):ast.parse(path.read_text(encoding='utf-8-sig'),filename=path.name);checked.append(path.name)
dest=D/'user-input';dest.mkdir(exist_ok=True);rows=[]
for name in ('4182d908-9b02-4155-a81f-c5274a6506bc','a99e040b-bcdb-4a1d-a370-8af6fcb53471','788975c7-3ac0-40bc-b909-b479464498d7','13d0f251-66bd-4f95-a484-943b9791745a','5fa8aa17-82f4-4c24-8156-c7eddcbac157'):
 source=Path('C:/Users/vakir/AppData/Local/Temp')/('codex-clipboard-'+name+'.png')
 if source.is_file():shutil.copy2(source,dest/source.name);rows.append({'file':source.name,'sha256':hashlib.sha256(source.read_bytes()).hexdigest()})
(D/'handoff-assets.json').write_text(json.dumps({'scriptSyntax':'PASS','checkedScripts':checked,'preservedScreenshots':rows},indent=2),encoding='utf-8')
