"""Read the converted world and verify every authored saved block and static fragment."""
import argparse
from collections import Counter
from functools import lru_cache
import gzip
import json
from pathlib import Path

from map_io.studio_geometry import ZERO, add, layout_cells, read_models, rotate, selected, subtract
from map_io.studio_save import SHARD_DIR, OwnerShard, WorldEdit, name, owner_uuid, properties, static_index
from map_io.world_io import RegionFile, Tag, compound, read_nbt
from migrate_studio_world import OLD_WINDOWS, WINDOW, mount, require, shift


def resident(catalogue, state):
    index = static_index(state)
    if index < 0:
        return None
    recipe = catalogue["recipes"][index]
    return recipe["fragments"][recipe["resident"]]["pose"] if recipe["resident"] >= 0 else None


def old_not_in_save(root):
    needles = tuple(value.encode() for value in OLD_WINDOWS)
    for path in (root / "region").glob("*.mca"):
        if path.stat().st_size:
            for chunk in RegionFile.open(path).chunks():
                require(not any(needle in chunk.raw_nbt() for needle in needles), f"Retired registry remains in {path}")
    for path in (root / SHARD_DIR).glob("*.nbt"):
        raw = gzip.decompress(path.read_bytes())
        require(not any(needle in raw for needle in needles), f"Retired owner remains in {path}")


def membership(root):
    result = {}
    for path in (root / SHARD_DIR).glob("owners-*.nbt"):
        x, z = map(int, path.stem.removeprefix("owners-").split("."))
        shard = OwnerShard(path, x, z)
        for uid, row in shard.members.items():
            owner = compound(shard.owners[row["ownerRef"].value])
            require(uid not in result, f"Owner registered twice in {root}")
            result[uid] = (owner, {tuple(member["offset"].value): shard.shapes[member["shapeRef"].value]
                                   for member in map(compound, row["members"].value)})
    return result


def boxes(tag):
    return sorted(tuple(round(value.value, 7) for value in box.value) for box in tag.value)


def expected_boxes(rows):
    return sorted(tuple(round(value, 7) for value in row) for row in rows)


def verify_dimension(original, migrated, dimension, inventory, models, catalogue, expected):
    counts = Counter()
    before = WorldEdit(original / dimension)
    after = WorldEdit(migrated / dimension)
    members = membership(migrated / dimension)
    pose_ids = {pose["key"]: index for index, pose in enumerate(catalogue["poses"])}
    extras_path = migrated / dimension / "data/bloodborne_dw_studio_overhangs.dat"
    raw = compound(compound(read_nbt(extras_path).root)["data"])["Rows"].value
    extra = {tuple(raw[index:index + 7]) for index in range(0, len(raw), 7)}

    @lru_cache(maxsize=None)
    def static_cells(key):
        stored = catalogue["poses"][pose_ids[key]]
        model = models[stored["registryId"]]
        pose = selected(model, stored["properties"])
        return layout_cells(pose, ZERO, stored["properties"], tuple(stored["shift"]), stored["mountY"])[0]

    for registry, objects in inventory["roots"].items():
        for kind, space, raw_pos, props, key in objects:
            if space != dimension or kind not in {"block", "static"}:
                continue
            pos = tuple(raw_pos)
            new_id = registry
            if registry in OLD_WINDOWS:
                new_id = WINDOW
                props = {**props, "open": str(registry.endswith("_open") or props["variant"] == "1").lower(), "variant": "0"}
                counts["replaced_90952"] += 1
            model = models.get(new_id)
            if model is None:
                continue
            pose = selected(model, props)
            anchor = model["anchor"]
            if pose is None and anchor == ZERO:
                continue
            yaw = int(props["rotation"])
            if kind == "block":
                src_entity = compound(before.at(pos).entities[pos])
                uid = owner_uuid(src_entity["resident"])
                owner, mask = members[uid]
                root = tuple(owner["root"].value)
                require(owner["id"].value == new_id, f"Wrong registry at {root}")
                state = after.at(root).state(root)
                require(name(state) == new_id and properties(state) == props, f"Wrong root state at {root}")
                entity = compound(after.at(root).entities[root])
                require(owner_uuid(entity["resident"]) == uid, f"Wrong saved resident at {root}")
                payload = entity["payload"]
                if anchor != ZERO:
                    if root == pos:
                        require(shift(payload) == add(shift(src_entity["payload"]), anchor), f"Wrong compensated anchor at {root}")
                        counts["dynamic_anchor_retained"] += 1
                    else:
                        require(root == add(pos, tuple(round(v) for v in rotate(anchor, yaw))), f"Wrong relocated anchor at {root}")
                        counts["dynamic_anchor_moved"] += 1
                if pose is not None:
                    cells, modes = layout_cells(pose, anchor, props, shift(payload), mount(payload))
                    require(cells <= mask.keys(), f"Missing authored membership at {root}")
                    for offset, shape in mask.items():
                        fields = compound(shape)
                        require(boxes(fields["collision"]) == expected_boxes(modes["blocks"].get(offset, ()))
                                and boxes(fields["selection"]) == expected_boxes(modes["outline"].get(offset, ())),
                                f"Wrong saved masks at {root}+{offset}")
                counts["dynamic_updated"] += 1
                continue
            root = pos
            actual = resident(catalogue, after.at(root).state(root))
            if actual != key and anchor != ZERO:
                moved = add(pos, tuple(round(v) for v in rotate(anchor, yaw)))
                if resident(catalogue, after.at(moved).state(moved)) == "studio_anchor_" + key:
                    root, actual = moved, "studio_anchor_" + key
                    counts["static_anchor_moved"] += 1
            if actual not in {key, "studio_anchor_" + key}:
                require(resident(catalogue, before.at(pos).state(pos)) != key, f"Lost static root {pos}")
                counts["orphan_static_ignored"] += 1
                continue
            if anchor != ZERO and root == pos:
                counts["static_anchor_retained"] += 1
            for offset in static_cells(actual):
                cell = add(root, offset)
                source = tuple(-v for v in offset)
                index = static_index(after.at(cell).state(cell))
                native = index >= 0 and any(part["pose"] == actual and tuple(part["origin"]) == source
                                           for part in catalogue["recipes"][index]["fragments"])
                require(native or (*cell, *source, pose_ids[actual]) in extra,
                        f"Missing static layout at {root}+{offset}")
            counts["static_updated"] += 1

    for key, count in counts.items():
        require(count == expected.get(key, 0), f"Audit total differs for {dimension} {key}: {count}")
    old_not_in_save(migrated / dimension)
    counts["overhang_links"] = len(extra)
    return dict(counts)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for option in ("original", "world", "inventory", "project", "static", "append", "migration", "output"):
        parser.add_argument("--" + option, required=True, type=Path)
    args = parser.parse_args()
    load = lambda path: json.loads(path.read_text(encoding="utf-8"))
    inventory, models = load(args.inventory), read_models(load(args.project))
    catalogue, appended = load(args.static), load(args.append)
    catalogue["poses"].extend(appended["poses"])
    catalogue["recipes"].extend(appended["recipes"])
    report = {"status": "PASS", "dimensions": {}}
    for dimension, values in load(args.migration)["dimensions"].items():
        report["dimensions"][dimension] = verify_dimension(args.original, args.world, dimension,
                                                          inventory, models, catalogue, values["counts"])
    args.output.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report))


if __name__ == "__main__":
    main()
