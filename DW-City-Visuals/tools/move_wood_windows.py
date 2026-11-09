"""Align installed 90007 models with their pre-Studio world positions."""

import argparse
from collections import Counter, defaultdict
from copy import deepcopy
from hashlib import sha256
import json
from math import isclose
import os
from pathlib import Path
from zipfile import ZipFile

from map_io.studio_geometry import ZERO, add, boxes_for, clip, layout_cells, read_models, rotate, selected, subtract
from map_io.studio_save import OwnerShard, SHARD_DIR, WorldEdit, chunk_key, coordinates, name, owner_uuid, properties, shape_tag
from map_io.world_io import NbtFile, RegionFile, Tag, compound, decode_nbt, encode_nbt, section_blocks
from migrate_studio_world import Migration, mount, shift, typed_vector
from update_city_followup_map import copy_world


WINDOW = "bloodborne_dw:prototype_wood_window"


def residents(world):
    result = {}
    for directory, subdirs, files in os.walk(world, followlinks=False):
        folder = Path(directory)
        subdirs[:] = [child for child in subdirs if not (folder / child).is_symlink()
                      and not (folder / child).is_junction()]
        if folder.name != "region":
            continue
        dimension = folder.parent.relative_to(world)
        for filename in files:
            path = folder / filename
            if path.suffix != ".mca" or path.is_symlink() or not path.stat().st_size:
                continue
            for chunk in RegionFile.open(path).chunks():
                if WINDOW.encode() not in chunk.raw_nbt():
                    continue
                fields = compound(chunk.nbt().root)
                sections = {compound(s)["Y"].value: s for s in fields["sections"].value}
                for tag in fields["block_entities"].value:
                    entry = compound(tag)
                    resident = entry.get("resident")
                    if resident is None or compound(resident)["id"].value != WINDOW:
                        continue
                    pos = coordinates(tag)
                    x, y, z = pos
                    palette, indices = section_blocks(sections[y // 16])
                    state = palette[indices[(y & 15) * 256 + (z & 15) * 16 + (x & 15)]]
                    uid = owner_uuid(resident)
                    if name(state) != WINDOW or uid in result:
                        raise ValueError(f"Invalid or duplicate resident {uid} at {pos}")
                    result[uid] = (dimension, pos, properties(state), entry["payload"])
    return result


def world_origin(pos, yaw, payload, local_offset=ZERO):
    return add(pos, add(rotate(add(shift(payload), local_offset), yaw), (0, mount(payload), 0)))


def stock_cells(recipe, props, payload, anchor):
    """Match the stock composite raster, then offset and split its cells."""
    yaw = int(props["rotation"])
    pose = recipe["variants"][int(props["variant"])]["poses"]["open" if props["open"] == "true" else "closed"]
    translation = add(rotate(subtract(shift(payload), anchor), yaw), (0, mount(payload), 0))
    cells = defaultdict(list)
    for key in ("collision", "selection"):
        for box in pose[key]:
            a, b = box["from"], box["to"]
            collider = {"rotation": ZERO, "center": [(x+y)/2 for x, y in zip(a, b)],
                        "size": [y-x for x, y in zip(a, b)]}
            for bounds in boxes_for(collider, ZERO, yaw, translation, grid=4):
                clip(bounds, cells)
    return cells.keys() | {ZERO}


class WindowEdits:
    def __init__(self, world):
        self.world = WorldEdit(world)
        self.shards = {}
        self.dynamic_roots, self.moved, self.static_changes = {}, {}, {}
        self.count = Counter()
        self.removals, self.additions = defaultdict(set), defaultdict(list)

    def shard(self, pos):
        cx, cz = key = chunk_key(pos)
        if key not in self.shards:
            self.shards[key] = OwnerShard(self.world.root / SHARD_DIR / f"owners-{cx}.{cz}.nbt", cx, cz)
        return self.shards[key]

    def change(self, uid, root, props, payload, cells, modes):
        chunk = self.world.at(root)
        entity = chunk.entities[root]
        owner = compound(entity)["resident"]
        shard = self.shard(root)
        if owner_uuid(owner) != uid:
            raise ValueError(f"Resident changed at {root}")
        for entry in shard.members[uid]["members"].value:
            self.removals[add(root, compound(entry)["offset"].value)].add(uid)
        state = chunk.state(root)
        data = Tag(10, {"id": Tag(8, WINDOW), "properties": Tag(10, {key: Tag(8, value) for key, value in props.items()}),
                        "payload": Tag(7, encode_nbt(NbtFile("", payload)))})
        shapes = {offset: shape_tag(modes["blocks"].get(offset, ()), modes["outline"].get(offset, ()))
                  for offset in cells}
        for offset, shape in shapes.items():
            self.additions[add(root, offset)].append((owner, data, shape))
        shard.set_members(owner, shapes)
        self.dynamic_roots[root] = (state, owner, payload, entity)
        self.moved[uid] = (root, root, WINDOW, WINDOW)

    def save(self):
        Migration.apply_dynamic(self, self.removals, self.additions, preserve_protected=True)
        self.world.flush()
        for shard in self.shards.values():
            shard.flush()

    def verify(self):
        changed = set(self.removals) | set(self.additions)
        fresh = WindowEdits(self.world.root)
        for pos in changed:
            shard = fresh.shard(pos)
            actual = {owner_uuid(compound(tag)["owner"]): tag for tag in shard.expand(shard.cells.get(pos, []))}
            for uid in self.removals.get(pos, ()):
                if uid in actual and not any(uid == owner_uuid(row[0]) for row in self.additions.get(pos, ())):
                    raise ValueError(f"Old owner coverage still at {pos}")
            for owner, data, shape in self.additions.get(pos, ()):
                uid = owner_uuid(owner)
                expected = Tag(10, {"owner": owner, "rootData": data, **compound(shape)})
                if actual.get(uid) != expected:
                    raise ValueError(f"Sharded footprint mismatch at {pos}")
            be = fresh.world.at(pos).entities.get(pos)
            if be is not None and "OwnerDataV1" in compound(be):
                nbt = decode_nbt(compound(be)["OwnerDataV1"].value)
                entries = compound(nbt.root)["owners"].value
                if {owner_uuid(compound(tag)["owner"]): tag for tag in entries} != actual:
                    raise ValueError(f"Block entity footprint mismatch at {pos}")
        return len(changed)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for arg in ("original", "world", "output", "jar", "report"):
        parser.add_argument("--" + arg, required=True, type=Path)
    args = parser.parse_args()
    with ZipFile(args.jar) as jar:
        layout_raw = jar.read("bloodborne_dw/studio-layouts-v22.json")
        model = read_models(json.loads(layout_raw))[WINDOW]
        recipe = json.loads(jar.read("bloodborne_dw/composite/prototype_wood_window.json"))
    before, current = residents(args.original), residents(args.world)
    plans, poses, deltas = [], Counter(), Counter()
    for uid, (dim, pos, props, payload) in current.items():
        if uid not in before:
            continue
        old_dim, old_pos, old_props, old_payload = before[uid]
        yaw = int(props["rotation"])
        if old_dim != dim or old_props["rotation"] != props["rotation"]:
            raise ValueError(f"Object changed dimension or angle at {pos}")
        pose = selected(model, props)
        anchor = model["anchor"]
        art = subtract(tuple(value / 16 for value in pose["modelOffset"]), anchor)
        expected = world_origin(old_pos, yaw, old_payload)
        delta = rotate(subtract(expected, world_origin(pos, yaw, payload, art)), -yaw)
        if all(isclose(value, 0, abs_tol=1e-8) for value in delta):
            continue
        local = tuple(round(value, 12) for value in add(shift(payload), delta))
        moved = deepcopy(payload)
        compound(moved)["SourceShift"] = typed_vector(local)
        cells, modes = layout_cells(pose, anchor, props, local, mount(payload))
        cells |= stock_cells(recipe, props, moved, anchor)
        plans.append((dim, uid, pos, props, moved, cells, modes))
        poses[(str(dim), props["open"], props["rotation"])] += 1
        deltas[tuple(round(v, 8) for v in delta)] += 1
        for opened in ("false", "true"):
            target_pose = selected(model, {**props, "open": opened})
            target_art = subtract(tuple(value / 16 for value in target_pose["modelOffset"]), anchor)
            if any(not isclose(a, b, abs_tol=1e-8)
                   for a, b in zip(world_origin(pos, yaw, moved, target_art), expected)):
                raise ValueError(f"{opened} geometry does not line up at {pos}")
    copy_world(args.world, args.output)
    dimensions = {}
    for dim, uid, pos, props, payload, cells, modes in plans:
        if dim not in dimensions:
            dimensions[dim] = WindowEdits(args.output / dim)
        dimensions[dim].change(uid, pos, props, payload, cells, modes)
    verified = {}
    for dim, edits in dimensions.items():
        edits.save()
        verified[str(dim)] = {"cells": edits.verify(), **edits.count}
    after = residents(args.output)
    expected_rows = {uid: (dim, pos, props, payload) for dim, uid, pos, props, payload, *_ in plans}
    if after != (current | expected_rows):
        raise ValueError("Resident positions, UUIDs, or unrelated payloads changed")
    result = {"status": "PASS", "changed": len(plans), "originalResidents": len(before),
              "inputResidents": len(current), "withoutOriginal": len(current.keys() - before.keys()),
              "poses": [[*key, count] for key, count in sorted(poses.items())],
              "localShiftBlocks": [[*key, count] for key, count in sorted(deltas.items())],
              "dimensions": verified, "layoutSha256": sha256(layout_raw).hexdigest()}
    args.report.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(result))


if __name__ == "__main__":
    main()
