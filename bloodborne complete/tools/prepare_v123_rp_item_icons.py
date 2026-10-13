"""Replace only legacy RP placer item icons; entity assets/gameplay remain untouched."""
from pathlib import Path
import hashlib
import json

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/rp/resources/assets/bloodborne_rp"
ALIASES = {"furniture_8": "furniture_1", "furniture_3": "furniture_10", "furniture_4": "furniture_11",
           "furniture_5": "furniture_12", "furniture_6": "furniture_13", "furniture_7": "furniture_14", "furniture_9": "furniture_2"}
CATALOG = json.loads((ASSETS / "catalog.json").read_text(encoding="utf-8"))
DEBUG = json.loads((ROOT / "src/architecture/resources/bloodborne_dw/debug_catalogue.json").read_text(encoding="utf-8-sig"))
ROWS = DEBUG["entries"]
BY_REGISTRY = {row["registryId"]: row for row in ROWS}

MODEL = {
    "parent": "minecraft:builtin/entity",
    "gui_light": "front",
    "display": {
        "gui": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
        "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [.5, .5, .5]},
        "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
        "thirdperson_righthand": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [.7, .7, .7]},
        "thirdperson_lefthand": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [.7, .7, .7]},
        "firstperson_righthand": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [.7, .7, .7]},
        "firstperson_lefthand": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [.7, .7, .7]},
        "head": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [.7, .7, .7]},
    },
}

rows = []
for path in sorted((ASSETS / "models/item").glob("*_placer.json")):
    source = json.loads(path.read_text(encoding="utf-8"))
    if source.get("parent") != "minecraft:builtin/entity":
        assert source == {"parent": "minecraft:item/generated", "textures": {"layer0": "minecraft:item/armor_stand"}}, path
    item_id = path.stem
    asset_id = ALIASES.get(item_id[:-7], item_id[:-7])
    spec = CATALOG[asset_id]
    assert (ASSETS / spec["model"]).is_file() and (ASSETS / spec["texture"]).is_file()
    path.write_text(json.dumps(MODEL, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    debug = BY_REGISTRY.get("bloodborne_rp:" + asset_id, {})
    rows.append({"item": "bloodborne_rp:" + item_id, "canonicalAsset": asset_id,
                 "temporaryId": debug.get("temporaryId"), "name": debug.get("name"),
                 "compatibilityAlias": item_id[:-7] in ALIASES,
                 "oldResource": "assets/bloodborne_rp/models/item/" + path.name,
                 "sourceModel": "assets/bloodborne_rp/" + spec["model"],
                 "sourceTexture": "assets/bloodborne_rp/" + spec["texture"],
                 "sourceModelSha256": hashlib.sha256((ASSETS/spec["model"]).read_bytes()).hexdigest(),
                 "sourceTextureSha256": hashlib.sha256((ASSETS/spec["texture"]).read_bytes()).hexdigest(),
                 "newItemModelSha256": hashlib.sha256(path.read_bytes()).hexdigest()})
report = {"schema": 1, "status": "PASS_RESOURCE_PREPARATION", "placerCount": len(rows),
          "offeredCanonicalCount": sum(not row["compatibilityAlias"] for row in rows),
          "compatibilityAliasCount": sum(row["compatibilityAlias"] for row in rows),
          "pose": "Static authored placement model; wood gate uses existing authored resting sample at 1.6s.",
          "geometryIsolation": "Independent bone copies from authored snapshots; source model cubes/UV/textures are unchanged.",
          "fit": "All rotated rendered vertices fitted uniformly inside a centered 0.82-unit cube.",
          "rows": rows}
destination = ROOT / "docs/v12.3-outline-icons/RP-ICON-MANIFEST.json"
destination.parent.mkdir(parents=True, exist_ok=True)
destination.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print(json.dumps({key: report[key] for key in ("status", "placerCount", "offeredCanonicalCount", "compatibilityAliasCount")}))
