"""Bind the unchanged accepted preparation to the actual final-followups JAR."""
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
source=(ROOT/'tools/verify_v1271_final_readiness.py').read_text(encoding='utf8')
assert source.count("'v12.7.1' in meta['version']")==1
source=source.replace("'v12.7.1' in meta['version']","'v12.7.3-final-checks' in meta['version']")
exec(compile(source,str(ROOT/'tools/verify_v1271_final_readiness.py'),'exec'),{'__file__':__file__,'__name__':'__main__'})
