import ast,json,runpy
from pathlib import Path
P=Path(__file__).resolve().parents[1];D=P/'npc-evidence';O=D/'final-artifacts';archive=O/'dreamwalker-npc-full-sources-20261026.zip';backup=D/'npc-source-before-publisher-fix.zip'
for f in (P/'tools').glob('*npc*.py'):ast.parse(f.read_text(encoding='utf-8'),filename=f.name)
assert archive.resolve().parent==O.resolve() and backup.resolve().parent==D.resolve() and not backup.exists()
assert not (D/'final-release.json').exists(),'Do not repackage published assets'
archive.rename(backup);runpy.run_path(str(P/'tools/package_npc_final.py'),run_name='__main__')
