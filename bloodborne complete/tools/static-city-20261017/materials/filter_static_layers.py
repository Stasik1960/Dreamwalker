"""Retain whole components only when every cell has a supported homogeneous layer."""
import collections
import gzip
import hashlib
import json
import os
from pathlib import Path
import sys


root = Path(sys.argv[1]).resolve()
generated = root / ".handoff-local/work/catalog-expansion/generated"
read = lambda path: json.loads(path.read_text(encoding="utf-8-sig"))
proof_path = generated / "selection-proof.json"
proof = read(proof_path)
if proof.get("renderLayerFilterApplied"):
    raise RuntimeError("The supported-layer filter has already been applied")
catalog = read(generated / "static-architecture-v1274.json")
specs = root / "project/src/architecture/resources/bloodborne_dw/composite"
layers, definitions = {}, {}
for pose in catalog["poses"]:
    registry = pose["registryId"].split(":", 1)[1]
    if registry not in definitions:
        definitions[registry] = read(specs / (registry + ".json")).get("layer") == "translucent"
    layers[pose["key"]] = definitions[registry]
mixed = {index for index, recipe in enumerate(catalog["recipes"])
         if len({layers[fragment["pose"]] for fragment in recipe["fragments"]}) > 1}
stream = generated / "static-architecture-components.jsonl.gz"
pending = generated / "static-architecture-components.layer-filtered.pending"
if pending.exists():
    raise RuntimeError("Unfinished layer filter exists; preserve it for inspection")
counts, families, skipped = collections.Counter(), collections.Counter(), collections.Counter()
with gzip.open(stream, "rt", encoding="utf-8") as source, pending.open("xb") as raw:
    with gzip.GzipFile(fileobj=raw, filename="", mode="wb", mtime=0) as target:
        for line in source:
            component = json.loads(line)
            if any(cell["recipe"] in mixed for cell in component["cells"]):
                skipped["components"] += 1
                skipped["owners"] += len(component["owners"])
                skipped["cells"] += len(component["cells"])
                continue
            target.write(line.encode("utf-8"))
            counts["plannedComponents"] += 1
            counts["plannedOwners"] += len(component["owners"])
            counts["plannedCells"] += len(component["cells"])
            families.update(owner["registryId"] for owner in component["owners"])
            if any(17 <= owner["position"][0] >> 4 <= 18 and -42 <= owner["position"][2] >> 4 <= -41 for owner in component["owners"]):
                counts["pilotAreaComponents"] += 1
before = dict(proof)
proof.update(counts)
proof["plannedFamilyCounts"] = dict(families)
proof["ownerPlanCoverage"] = counts["plannedOwners"] / proof["preparedOwnerRows"]
proof["skippedOwnerCounts"]["MIXED_RENDER_LAYER_COMPONENT"] = skipped["owners"]
proof["renderLayerFilterApplied"] = True
proof["mixedLayerRecipesNotEmitted"] = len(mixed)
proof["renderLayerExcluded"] = dict(skipped)
proof["collisionBoxesInSharedCatalog"] = sum(len(row["collision"]) for row in catalog["recipes"])
proof["maximumCollisionBoxesInRecipe"] = max(len(row["collision"]) for row in catalog["recipes"])
with pending.open("rb") as handle:
    proof["streamSha256"] = hashlib.file_digest(handle, "sha256").hexdigest()
(generated / "selection-before-layer-filter.json").write_text(json.dumps(before, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
new_proof = generated / "selection-proof.layer-filtered.pending"
new_proof.write_text(json.dumps(proof, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
os.replace(pending, stream)
os.replace(new_proof, proof_path)
print(json.dumps({**counts, "mixedLayerRecipesNotEmitted": len(mixed), "excluded": dict(skipped),
                  "ownerPlanCoverage": proof["ownerPlanCoverage"], "collisionBoxes": proof["collisionBoxesInSharedCatalog"]}))
