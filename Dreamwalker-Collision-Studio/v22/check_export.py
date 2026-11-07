"""Validate a V22 Studio handoff against its own release catalogue before code changes."""

import argparse
import json
import math
from pathlib import Path
from zipfile import ZipFile


MAX_JSON_BYTES = 16 * 1024 * 1024
MODES = {"players", "blocks", "outline", "both"}
SEAT = [9.6, 1.6, 9.6]


def read_document(path, member):
    if path.suffix.lower() == ".zip":
        with ZipFile(path) as archive:
            info = archive.getinfo(member)
            if info.file_size > MAX_JSON_BYTES:
                raise ValueError(f"Large JSON member: {member}")
            return json.loads(archive.read(info))
    if path.stat().st_size > MAX_JSON_BYTES:
        raise ValueError(f"Large JSON file: {path.name}")
    return json.loads(path.read_text(encoding="utf-8-sig"))


def vector(value, positive=False):
    return (isinstance(value, list) and len(value) == 3
            and all(type(component) in (int, float) and math.isfinite(component)
                    and abs(component) < 4096 and (not positive or component > 0)
                    for component in value))


def require(condition, message):
    if not condition:
        raise ValueError(message)


def validate(project, catalogue):
    require(project.get("schema") == "dreamwalker.player-collision-project"
            and project.get("schemaVersion") == 4 and project.get("unitsPerBlock") == 16,
            "Unexpected project schema")
    require(project.get("source") == catalogue.get("source"), "Different release catalogue")
    policy = project.get("policy", {})
    require(policy.get("scope") == "per_collider" and policy.get("operation") == "replace_per_exact_state"
            and policy.get("instanceScope") == "new_placements_only", "Invalid replacement policy")
    require(project.get("coordinates", {}).get("rotationOrder") == "ZYX", "Unknown rotation order")
    known = {row["id"]: row["states"] for row in catalogue["models"]}
    models = set()
    counts = {"models": 0, "states": 0, "unreviewed": 0, "colliders": 0, "seats": 0}
    for model in project["models"]:
        identifier = model["registryId"]
        require(identifier in known and identifier not in models, f"Unknown or duplicate model: {identifier}")
        models.add(identifier)
        counts["models"] += 1
        states = set()
        for state in model["states"]:
            key = state["key"]
            label = f"{identifier} {key}"
            require(key in known[identifier] and key not in states, f"Unknown or duplicate state: {label}")
            states.add(key)
            require(state["selector"] == known[identifier][key] and state["replace"] is True,
                    f"Invalid state selector: {label}")
            require(vector(state["modelOffset"]) and type(state.get("reviewed")) is bool,
                    f"Invalid state metadata: {label}")
            counts["states"] += 1
            counts["unreviewed"] += not state["reviewed"]
            ids = set()
            for volume in state["colliders"] + state["seats"]:
                name = volume["id"]
                require(isinstance(name, str) and name and name not in ids, f"Duplicate volume in {label}")
                ids.add(name)
                require(vector(volume["rotation"]), f"Invalid volume rotation in {label}")
            for volume in state["colliders"]:
                require(volume["mode"] in MODES and vector(volume["center"]) and vector(volume["size"], positive=True),
                        f"Invalid collider in {label}")
                counts["colliders"] += 1
            for seat in state["seats"]:
                require(vector(seat["surfaceCenter"]) and seat["size"] == SEAT
                        and seat.get("front") == [0, 0, -1] and seat.get("capacity") == 1
                        and seat.get("trigger") == "right_click_surface", f"Invalid seat in {label}")
                counts["seats"] += 1
    require(counts["states"] > 0, "No release state was edited")
    return counts


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("project", type=Path)
    parser.add_argument("studio", type=Path)
    args = parser.parse_args()
    print(json.dumps(validate(read_document(args.project, "dreamwalker-collisions.json"),
                              read_document(args.studio, "catalogue.json"))))
