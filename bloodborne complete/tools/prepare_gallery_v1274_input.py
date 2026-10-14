"""Reuse accepted v12.7 city preparation and show the new single central door.

No source world scan, weighted model selection or city preparation is rebuilt.
The reviewed v12.7.3 adapter refreshes current JAR metadata; the only additional
review exhibit is90986 OPEN. All previous fixes and catalogue exclusions remain.
"""
from pathlib import Path
import hashlib
import json
import sys

manual_optics = "--manual-decorative-optics" in sys.argv
if manual_optics:
    sys.argv.remove("--manual-decorative-optics")

ROOT = Path(__file__).resolve().parents[1]
source = (ROOT / "tools/prepare_gallery_v1273_input.py").read_text(encoding="utf8")


def replace(old, new):
    global source
    assert source.count(old) == 1, old
    source = source.replace(old, new)


replace("'v12.7.3-final-checks' in meta['version']", "'v12.7.4-placement-render-fixes' in meta['version']")
replace("displayRevision='V12.7.3'", "displayRevision='V12.7.4'")
replace("marker['reviewPlatforms'].append(template)\n            for group", """marker['reviewPlatforms'].append(template)
            central=copy.deepcopy(template)
            central.update(key='V1274-doors-90986-open',primaryId='90986',id='90986',registry='bloodborne_dw:source_stairs_dark_prismarine_stairs_inner_right',russianLabel='Дверь с полукруглой аркой · открыта',open=True,reviewPoseIndex=0)
            central['components'][0].update(registryId=central['registry'],mainId='90986',properties={'variant':'0','rotation':'0','profile':'base','open':'true'},descriptor='src/architecture/resources/bloodborne_dw/composite/source_stairs_dark_prismarine_stairs_inner_right.json',sourceShift=[0,0,0],mountY=0)
            marker['reviewPlatforms'].append(central)
            marker['finalFollowups']['newCentralSingleDoorId']='90986'
            marker['finalFollowups']['freshSavedCollisionGeometryRequired']=True
            for group""")
exec(compile(source, str(ROOT / "tools/prepare_gallery_v1273_input.py"), "exec"),
     {"__name__": "__main__", "__file__": __file__})

output = Path(sys.argv[sys.argv.index("--output") + 1])
marker = json.loads(output.read_text(encoding="utf8"))
city_hash = lambda: hashlib.sha256(json.dumps(marker["city"], sort_keys=True, separators=(",", ":"), ensure_ascii=False).encode("utf8")).hexdigest()
city_before = city_hash()
cleared_planar = []
for row in marker["catalogue"]["exhibits"]:
    # These two reviewed original horizontal planes now have separate +/-
    # normal-offset faces. Seat the new lower face on the platform; the upper
    # face is already2/1024 above it. The author retains its strict zero-height
    # guard for every exhibit that still requests extra gallery clearance.
    if row["registry"] not in {"bloodborne_dw:source_addon_button_10", "bloodborne_dw:source_addon_button_3"}:
        continue
    bounds = marker["localVisualBounds"][row["key"]]
    assert row["kind"] == "architecture" and row["origin"] != "V12"
    assert row.get("surfaceClearance") == 1/1024
    assert bounds[1] == -1/1024 and bounds[4] == 1/1024
    del row["surfaceClearance"]
    cleared_planar.append({"id": row["id"], "registry": row["registry"], "localVisualBounds": bounds,
                           "newRenderedHeight": 2/1024, "galleryExtraClearance": 0})
assert {r["id"] for r in cleared_planar} == {"90065", "90071"}
marker["galleryPreparation"]["reviewedPlanarLayerSeating"] = {
    "ids": cleared_planar, "strictZeroThicknessAuthorGuardRetained": True,
    "reason": "These two reviewed +/- normal-offset surfaces already stand2/1024 above the new lower face seated on the platform. Only their obsolete extra gallery clearance is removed.",
    "cityPayloadAndGeometryUnchangedByGallerySeating": True,
}
main_count = review_count = 0
if manual_optics:
    for row in marker["catalogue"]["exhibits"]:
        if row["origin"] != "V12" and row["kind"] == "architecture" and row["registry"].startswith("bloodborne_dw:source_") and row.get("state", {}).get("compact", "false") != "true":
            row["manualDecorativeOptics"] = True
            main_count += 1
    for group in marker["reviewPlatforms"]:
        for component in group["components"]:
            if component["registryId"].startswith("bloodborne_dw:source_") and component["properties"].get("compact", "false") != "true":
                component.setdefault("payload", {})["DecorativePlacementOptics"] = 1
                review_count += 1
assert city_hash() == city_before
marker["galleryPreparation"]["manualDecorativeOptics"] = {
    "enabled": manual_optics, "nbtKey": "DecorativePlacementOptics", "nbtType": "INT", "value": 1,
    "mainExhibits": main_count, "reviewComponents": review_count,
    "cityPayloadAndGeometryUnchanged": True, "citySha256BeforeAndAfter": city_before,
}
marker["nativeFollowupViewRequests"] = [
    {"key": "V1274-OPEN-90986-FRONT", "exhibitKey": "V1274-doors-90986-open", "view": "front"},
    {"key": "V1274-OPEN-90986-BACK", "exhibitKey": "V1274-doors-90986-open", "view": "back"},
    {"key": "V1274-CLOSED-90986-FRONT", "mainId": "90986", "view": "front"},
    {"key": "V1274-CLOSED-90986-BACK", "mainId": "90986", "view": "back"},
    {"key": "V1274-90308-LEFT", "mainId": "90308", "view": "left"},
    {"key": "V1274-90308-RIGHT", "mainId": "90308", "view": "right"},
]
output.write_text(json.dumps(marker, ensure_ascii=False, indent=2) + "\n", encoding="utf8")
output.with_suffix(".preparation.json").write_text(json.dumps(marker["galleryPreparation"], ensure_ascii=False, indent=2) + "\n", encoding="utf8")
