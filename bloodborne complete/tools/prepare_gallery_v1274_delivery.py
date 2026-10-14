"""Create a fresh v12.7.4 delivery copy; never change previous release worlds."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
source = (ROOT / "tools/prepare_gallery_v1271_delivery.py").read_text(encoding="utf8")
assert source.count("Dreamwalker BB v12.7.1 — галерея и город") == 1
source = source.replace("Dreamwalker BB v12.7.1 — галерея и город", "Dreamwalker BB v12.7.4 — галерея и город")
exec(compile(source, str(ROOT / "tools/prepare_gallery_v1271_delivery.py"), "exec"),
     {"__file__": __file__, "__name__": "__main__"})
