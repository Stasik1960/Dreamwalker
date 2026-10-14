"""Keep the accepted typed-NBT proof; require the additional90986 OPEN exhibit."""
from pathlib import Path
import json
import sys

ROOT = Path(__file__).resolve().parents[1]
source = (ROOT / "tools/verify_gallery_v1273.py").read_text(encoding="utf8")
assert source.count("len(mainrows)==642 and len(groups)==23") == 1
source = source.replace("len(mainrows)==642 and len(groups)==23", "len(mainrows)==642 and len(groups)==24")
exec(compile(source, str(ROOT / "tools/verify_gallery_v1273.py"), "exec"),
     {"__name__": "__main__", "__file__": __file__})

manifest_path = Path(sys.argv[sys.argv.index("--manifest") + 1])
report_path = Path(sys.argv[sys.argv.index("--report") + 1])
manifest = json.loads(manifest_path.read_text(encoding="utf-8-sig"))
report = json.loads(report_path.read_text(encoding="utf-8-sig"))
registry = "bloodborne_dw:source_stairs_dark_prismarine_stairs_inner_right"
closed = [r for r in manifest["exhibits"] if r.get("registry") == registry]
opened = [r for r in manifest["reviewPlatforms"] if r.get("key") == "V1274-doors-90986-open"]
assert len(closed) == len(opened) == 1
assert closed[0]["placedState"]["open"] == "false"
assert len(opened[0]["components"]) == 1
assert opened[0]["components"][0]["registry"] == registry
assert opened[0]["components"][0]["placedState"]["open"] == "true"
assert report["allCachedRootStatesAndTypedPayloadsEqualResidents"]
report["singleCentralDoor90986"] = {
    "closedMainExhibits": 1, "openReviewExhibits": 1,
    "freshSavedOwnerAndHelperGeometryVerifiedByCurrentProductionSpec": True,
}
report_path.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf8")
