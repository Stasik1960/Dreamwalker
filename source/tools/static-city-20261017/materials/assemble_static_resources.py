"""Install generated shared definitions and native bank aliases into the build overlay."""
import hashlib
import json
import math
from pathlib import Path
import shutil
import sys


root = Path(sys.argv[1]).resolve()
work = root / ".handoff-local/work"
generated = work / "catalog-expansion/generated"
proof = json.loads((generated / "selection-proof.json").read_text(encoding="utf-8"))
if proof["status"] != "DERIVED_SHARED_CATALOG_AND_STATIC_STREAM_NATIVE_ELIGIBILITY_PENDING" or not proof["baseRecipeIdsPreserved"]:
    raise RuntimeError("A complete generated catalog preserving old recipe IDs is required")
overlay = work / "source/resource-overlay"
catalog = overlay / "bloodborne_dw/static-architecture-v1274.json"
catalog.parent.mkdir(parents=True, exist_ok=True)
shutil.copyfile(generated / "static-architecture-v1274.json", catalog)
if hashlib.sha256(catalog.read_bytes()).hexdigest() != proof["catalogSha256"]:
    raise RuntimeError("New resource overlay copy differs from generated catalog")
template = (root / "project/src/architecture/resources/assets/bloodborne_dw/blockstates/static_architecture_0.json").read_bytes()
variants = json.loads(template)["variants"]
if set(variants) != {"part=" + str(i) for i in range(128)}:
    raise RuntimeError("Unexpected preserved bank model template")
count = math.ceil(proof["recipeCount"] / 128)
paths = []
for index in range(count):
    target = overlay / f"assets/bloodborne_dw/blockstates/static_architecture_{index}.json"
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_bytes(template)
    if target.read_bytes() != template:
        raise RuntimeError("New native bank model copy differs")
    paths.append(target.relative_to(overlay).as_posix())
result = {"status": "GENERATED_RESOURCES_INSTALLED_IN_ISOLATED_OVERLAY", "catalogSha256": proof["catalogSha256"],
          "poseCount": proof["poseCount"], "recipeCount": proof["recipeCount"], "nativeBanks": count,
          "bankTemplateSha256": hashlib.sha256(template).hexdigest(), "bankAliases": paths,
          "existingModelsAndTexturesChanged": False, "originalResourcesChanged": False}
(work / "source/RESOURCE-OVERLAY.json").write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print(json.dumps({key: result[key] for key in ("status", "poseCount", "recipeCount", "nativeBanks")}))
