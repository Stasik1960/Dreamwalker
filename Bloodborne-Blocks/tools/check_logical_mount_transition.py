"""Prove that the mount-state merge preserves every previously generated visual.

Baseline must be the pre-merge logical resource directory (for example the
previously verified build/resources/main/bloodborne_blocks/logical directory).
This checks experimental output, not original world data, and never writes it.
"""
import argparse
import gzip
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MOUNTS = {
    "o_lightning_rod": ("o_lightning_rod", "floor"),
    "o_lightning_rod_e1da8a7f": ("o_lightning_rod", "wall"),
    "o_lightning_rod_17688841": ("o_lightning_rod", "ceiling"),
    "o_oak_wood": ("o_oak_wood", "floor"),
    "o_oak_wood_d1a3ec8b": ("o_oak_wood", "wall"),
    "o_chain_0": ("o_chain_0", "floor"),
    "o_chain_0_6442d760": ("o_chain_0", "wall"),
}


def read(root, name):
    if name.endswith(".gz"):
        with gzip.open(root / name, "rt", encoding="utf-8") as stream:
            return json.load(stream)
    return json.loads((root / name).read_text(encoding="utf-8"))


def target(ident, props):
    if ident in MOUNTS:
        ident, face = MOUNTS[ident]
        props = {**props, "face": face}
    return ident, props


def key(props):
    return ",".join(f"{k}={v}" for k, v in sorted(props.items()))


def geometry(data, ident, state):
    value = data["blocks"][ident]["states"][state]
    return data["profiles"][value["ref"]] if "ref" in value else value


def require(condition, message):
    if not condition:
        raise AssertionError(message)


def without(data, *fields):
    return {name: value for name, value in data.items() if name not in fields}


def validate(before, after):
    old_definitions, new_definitions = read(before, "definitions.json"), read(after, "definitions.json")
    old = {row["id"]: row for row in old_definitions["blocks"]}
    new = {row["id"]: row for row in new_definitions["blocks"]}
    require(len(old) == len(old_definitions["blocks"]) and len(new) == len(new_definitions["blocks"]), "Duplicate object IDs")
    require(without(old_definitions, "blocks") == without(new_definitions, "blocks"), "Definition metadata changed")
    require(set(new) == {target(ident, {})[0] for ident in old}, "Unexpected object change")
    old_meshes, new_meshes = read(before, "meshes.json.gz"), read(after, "meshes.json.gz")
    old_geometry, new_geometry = read(before, "geometry.json"), read(after, "geometry.json")
    require(without(old_geometry, "blocks", "profiles") == without(new_geometry, "blocks", "profiles"), "Geometry metadata changed")
    require(set(new_geometry["blocks"]) == set(new), "Geometry object set differs")
    referenced_meshes, referenced_profiles = set(), set()
    mount_ids = {ident for ident, _ in MOUNTS.values()}
    for ident, definition in new.items():
        previous = old[ident]
        expected_properties = previous["properties"]
        expected_default = previous["default"]
        expected_states = set(previous["states"])
        if ident in mount_ids:
            expected_properties = {**expected_properties, "face": ["floor", "wall", "ceiling"]}
            expected_default = {**expected_default, "face": "floor"}
            expected_states = {key({"face": face, "facing": facing}) for face in ("floor", "wall", "ceiling")
                               for facing in ("north", "east", "south", "west")}
        require(definition["properties"] == expected_properties, (ident, "properties changed"))
        require(definition["default"] == expected_default, (ident, "default changed"))
        require(set(definition["states"]) == set(definition["models"]) == expected_states, (ident, "unexpected state set"))
        require(set(new_geometry["blocks"][ident]["states"]) == expected_states, (ident, "unexpected geometry states"))
        require(without(definition, "models", "states", "properties", "default") ==
                without(previous, "models", "states", "properties", "default"), (ident, "definition metadata changed"))
        require(without(new_geometry["blocks"][ident], "states") ==
                without(old_geometry["blocks"][ident], "states"), (ident, "geometry block metadata changed"))
        referenced_meshes.update(definition["models"].values())
        referenced_profiles.update(value["ref"] for value in new_geometry["blocks"][ident]["states"].values() if "ref" in value)
    require(set(new_meshes) == referenced_meshes, "Missing or unreferenced mesh entries")
    require(set(new_geometry["profiles"]) == referenced_profiles, "Missing or unreferenced geometry profiles")
    checked = 0
    for ident, definition in old.items():
        if ident in MOUNTS:
            require(without(definition, "id", "models", "states") ==
                    without(old[MOUNTS[ident][0]], "id", "models", "states"), (ident, "incompatible old mount metadata"))
        for state, mesh in definition["models"].items():
            new_id, props = target(ident, dict(piece.split("=", 1) for piece in state.split(",")))
            new_key = key(props)
            require(old_meshes[mesh] == new_meshes[new[new_id]["models"][new_key]], (ident, state, "mesh changed"))
            require(definition["states"][state] == new[new_id]["states"][new_key], (ident, state, "state metadata changed"))
            require(geometry(old_geometry, ident, state) == geometry(new_geometry, new_id, new_key), (ident, state, "geometry changed"))
            checked += 1
    old_migration, new_migration = read(before, "migration.json"), read(after, "migration.json")
    require(without(old_migration, "rules") == without(new_migration, "rules"), "Migration metadata changed")
    expected_rules = old_migration["rules"]
    for rule in expected_rules:
        new_id, props = target(rule["target"]["id"], rule["target"]["properties"])
        rule["target"] = {**rule["target"], "id": new_id, "properties": props}
    canonical = lambda rows: sorted(json.dumps(row, sort_keys=True) for row in rows)
    actual_rules = new_migration["rules"]
    require(canonical(expected_rules) == canonical(actual_rules), "Migration source/offset/member changed")
    return {"ok": True, "preservedStates": checked, "preservedRules": len(actual_rules),
            "objectsBefore": len(old), "objectsAfter": len(new)}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("baseline", type=Path)
    parser.add_argument("--current", type=Path, default=ROOT / "src/main/resources/bloodborne_blocks/logical")
    args = parser.parse_args()
    print(json.dumps(validate(args.baseline, args.current)))
