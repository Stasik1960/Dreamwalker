import ast,json
from pathlib import Path
P=Path(__file__).resolve().parents[1];files=sorted((P/'tools').glob('*restor*.py'))
for p in files:ast.parse(p.read_text(encoding='utf-8-sig'),filename=str(p))
(P/'restoration-evidence/script-syntax.json').write_text(json.dumps({'status':'PASS','files':[p.name for p in files]},indent=2),encoding='utf-8')
