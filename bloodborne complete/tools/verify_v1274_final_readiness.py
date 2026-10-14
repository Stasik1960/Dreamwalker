"""Bind unchanged prepared city decisions to the actual v12.7.4 production JAR."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
source = (ROOT / "tools/verify_v1273_final_readiness.py").read_text(encoding="utf8")
assert source.count("'v12.7.3-final-checks' in meta['version']") == 1
source = source.replace("'v12.7.3-final-checks' in meta['version']", "'v12.7.4-placement-render-fixes' in meta['version']")
exec(compile(source, str(ROOT / "tools/verify_v1273_final_readiness.py"), "exec"),
     {"__file__": __file__, "__name__": "__main__"})
