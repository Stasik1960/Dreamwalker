"""Inventory authored layouts against the actual save and previous game release."""
from argparse import ArgumentParser
from collections import Counter, defaultdict
from hashlib import sha256
import json
import os
from pathlib import Path
from zipfile import ZipFile

from map_io.world_io import RegionFile, Tag, compound, section_blocks

parser = ArgumentParser(description=__doc__)
for option in ("world", "prior-jar", "archive", "output"):
    parser.add_argument("--" + option, type=Path, required=True)
args = parser.parse_args()
WORLD, JAR, ARCHIVE = args.world, args.prior_jar, args.archive
EXTRA = {
    "bloodborne_dw:source_spruce_trapdoor_bottom",
    "bloodborne_dw:source_spruce_trapdoor_open",
    "bloodborne_dw:source_shutter_window_surround",
}
EMPTY = Tag(9, [], 0)

with ZipFile(ARCHIVE) as archive:
    document = json.loads(archive.read("dreamwalker-collisions.json"))
models = {row["registryId"]: row for row in document["models"]}
watch = models.keys() | EXTRA
with ZipFile(JAR) as jar:
    static = json.loads(jar.read("bloodborne_dw/static-architecture-v1274.json"))
    poses = {pose["key"]: pose for pose in static["poses"] if pose["registryId"] in watch}
    static_cells = {
        index: [(poses[f["pose"]], f["origin"]) for f in row["fragments"] if f["pose"] in poses]
        for index, row in enumerate(static["recipes"])
    }
    grid = json.loads(jar.read("assets/bloodborne_dw/native_grid/catalog.json"))
    native = {index: row["baseState"] for index, row in enumerate(grid["cells"])
              if (row.get("baseState") or {}).get("registryId") in watch}

counter = defaultdict(Counter)
unique = defaultdict(set)
roots = defaultdict(list)
used_static = Counter()
chunks = Counter()
needle = tuple(reg.encode() for reg in watch) + (b"bloodborne_dw:static_architecture_", b"DreamwalkerVisuals")

def state_summary(registry, properties):
    return ",".join(f"{key}={value}" for key, value in sorted(properties.items()))

def record(kind, registry, props, position, dimension, misc=None):
    key = state_summary(registry, props)
    counter[registry][kind + ":" + key] += 1
    packed = (kind, dimension, tuple(position), key)
    if packed not in unique[registry]:
        unique[registry].add(packed)
        roots[registry].append([kind, dimension, list(position), props, misc])

for directory, subdirs, filenames in os.walk(WORLD, followlinks=False):
    current = Path(directory)
    subdirs[:] = [name for name in subdirs if not ((current/name).is_symlink() or (current/name).is_junction())]
    if current.name not in ("region", "entities"):
        continue
    dimension = current.parent.relative_to(WORLD).as_posix()
    for filename in filenames:
        path = current / filename
        if path.suffix != ".mca" or not path.stat().st_size or path.is_symlink():
            continue
        for chunk in RegionFile.open(path).chunks():
            raw = chunk.raw_nbt()
            if not any(value in raw for value in needle):
                continue
            nbt = compound(chunk.nbt().root)
            if current.name == "entities":
                for tag in nbt.get("Entities", EMPTY).value:
                    entity = compound(tag)
                    registry = entity["id"].value
                    if registry not in watch:
                        continue
                    opened = entity.get("Open")
                    record("rp", registry, {"open": str(bool(opened.value)).lower()} if opened else {},
                           [coordinate.value for coordinate in entity["Pos"].value], dimension, entity["UUID"].value)
                continue
            xbase, zbase = nbt["xPos"].value * 16, nbt["zPos"].value * 16
            chunks[(nbt["xPos"].value, nbt["zPos"].value)] = 1
            for section in nbt.get("sections", EMPTY).value:
                values = section_blocks(section)
                if not values:
                    continue
                palette, indices = values
                block_states = {}
                for i, item in enumerate(palette):
                    fields = compound(item)
                    identifier = fields["Name"].value
                    if identifier in watch or identifier.startswith("bloodborne_dw:static_architecture_"):
                        props = {key: value.value for key, value in compound(fields.get("Properties", Tag(10, {}))).items()}
                        block_states[i] = (identifier, props)
                if not block_states:
                    continue
                ybase = compound(section)["Y"].value * 16
                for local, index in enumerate(indices):
                    if index not in block_states:
                        continue
                    identifier, props = block_states[index]
                    pos = (xbase+(local&15), ybase+(local>>8), zbase+((local>>4)&15))
                    if identifier in watch:
                        record("block", identifier, props, pos, dimension)
                    else:
                        recipe = int(identifier.rsplit("_", 1)[1]) * 128 + int(props["part"])
                        used_static[recipe] += 1
                        for pose, origin in static_cells[recipe]:
                            root = tuple(a + b for a, b in zip(pos, origin))
                            record("static", pose["registryId"], pose["properties"], root, dimension, pose["key"])
            if not native or "DreamwalkerVisuals" not in nbt:
                continue
            for section in compound(nbt["DreamwalkerVisuals"]).get("Sections", EMPTY).value:
                values = compound(section)
                ybase = values["Y"].value * 16
                for entry in values["Entries"].value:
                    recipe, local = (entry & 0xffffffff) >> 12, entry & 4095
                    if recipe not in native:
                        continue
                    state = native[recipe]
                    record("grid", state["registryId"], state.get("properties", {}),
                           (xbase+(local&15), ybase+(local>>8), zbase+((local>>4)&15)), dimension, recipe)

result = {
    "archiveSha256": sha256(ARCHIVE.read_bytes()).hexdigest(),
    "worldLevelSha256": sha256((WORLD / "level.dat").read_bytes()).hexdigest(),
    "models": len(models), "states": sum(len(m["states"]) for m in models.values()),
    "axes": {registry: dict(Counter(record[0] for record in roots[registry])) for registry in sorted(watch)},
    "stateCounts": counter, "roots": roots, "staticRecipesUsed": used_static,
    "recipeCount": len(static["recipes"]), "poseCount": len(static["poses"]),
    "chunks": sorted(chunks),
}
args.output.write_text(json.dumps(result, indent=2), encoding="utf8")
for key, row in result["axes"].items():
    if row:
        print(key, row)
print("Static recipes:", len(used_static), "/", len(static["recipes"]))
