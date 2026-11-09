"""Apply a verified Collision Studio project to a disposable copy of Bloodborne."""
import argparse
from collections import Counter, defaultdict
from copy import deepcopy
from functools import lru_cache
from hashlib import sha256
from itertools import product
import json
from math import cos, pi, sin
from pathlib import Path

from map_io.studio_geometry import ZERO, add, clip, layout_cells, point, read_models, rotate, selected, subtract
from map_io.studio_save import (AIR, HELPER, SHARD_DIR, OwnerShard, WorldEdit, chunk_key,
                                name, owner_uuid, properties, rewrite_be, shape_tag, state_tag, static_index, uuid_order)
from map_io.world_io import NbtFile, RegionFile, Tag, compound, decode_nbt, encode_nbt, read_nbt, write_nbt

OLD_WINDOWS = {"bloodborne_dw:source_spruce_trapdoor_bottom", "bloodborne_dw:source_spruce_trapdoor_open"}
WINDOW = "bloodborne_dw:source_shutter_window"
EMPTY = Tag(10, {})


def require(condition, reason):
    if not condition:
        raise ValueError(reason)


def vector(tag):
    return tuple(item.value for item in tag.value)


def typed_vector(values):
    return Tag(9, [Tag(6, value) for value in values], 6)


def shift(payload):
    fields = compound(payload)
    return vector(fields["SourceShift"]) if "SourceShift" in fields else ZERO


def mount(payload):
    return sum(compound(payload).get(key, Tag(6, 0)).value for key in ("MountY", "VerticalOffset"))


def stable_json(value):
    return json.dumps(value, ensure_ascii=False, separators=(",", ":")).encode("utf-8")


def window_stock(descriptor, props, payload):
    """The target window is authored-exact, outside the fitted collision cohort."""
    pose = descriptor["variants"][0]["poses"]["open" if props["open"] == "true" else "closed"]
    yaw = int(props["rotation"])
    offset = add(rotate(shift(payload), yaw), (0, mount(payload), 0))
    transformed = {}
    for mode in ("collision", "selection"):
        boxes = []
        for box in pose[mode]:
            corners = [add(point(p, yaw), offset) for p in product(*zip(
                (v / 16 for v in box["from"]), (v / 16 for v in box["to"])))]
            axis = tuple(zip(*corners))
            boxes.append((*map(min, axis), *map(max, axis)))
        transformed[mode] = boxes
    physical = transformed["collision"]
    low = [min(b[i] for b in physical) for i in range(3)]
    high = [max(b[i + 3] for b in physical) for i in range(3)]
    low = [0 if -.5 < v < 0 else v for v in low]
    high = [1 if 1 < v < 1.5 else v for v in high]
    cells = defaultdict(list)
    for b in physical:
        cropped = tuple(max(x, y) for x, y in zip(b[:3], low)) + tuple(min(x, y) for x, y in zip(b[3:], high))
        if all(x < y for x, y in zip(cropped[:3], cropped[3:])):
            clip(cropped, cells)
    for box in transformed["selection"]:
        clip(box, cells)
    return cells.keys() | {ZERO}


class StaticCatalogue:
    def __init__(self, raw):
        self.data = json.loads(raw)
        self.base = self.data["recipes"]
        self.poses = {pose["key"]: pose for pose in self.data["poses"]}
        self.extra = []
        self.new_poses = []

    @lru_cache(maxsize=None)
    def move_pose(self, key, anchor):
        original = self.poses[key]
        row = deepcopy(original)
        row["key"] = "studio_anchor_" + key
        row["shift"] = subtract(row["shift"], anchor)
        world_delta = rotate(anchor, int(row["properties"]["rotation"]))
        row["reviewedVisualBounds"] = [subtract(bound, world_delta) for bound in row["reviewedVisualBounds"]]
        self.poses[row["key"]] = row
        self.new_poses.append(row)
        return row["key"]

    @staticmethod
    def packed(fragment):
        return fragment["pose"], tuple(fragment["origin"]), fragment["member"]

    @lru_cache(maxsize=None)
    def bake(self, base, replacements, added):
        if not replacements and not added:
            return base
        lookup = dict(replacements)
        previous = self.base[base] if base >= 0 else None
        kept = tuple(self.packed(row) for row in previous["fragments"]) if previous else ()
        fragments = tuple(lookup.get(item, item) for item in kept) + added
        require(0 < len(fragments) <= 32 and len(set(fragments)) == len(fragments), "Static fragment budget or duplicate")
        residents = [index for index, part in enumerate(fragments) if part[1] == ZERO]
        require(len(residents) <= 1, "Two static roots claim a cell")
        removal = []
        for index, _ in enumerate(kept):
            next_base = previous["remove"][index]
            child = {self.packed(row) for row in self.base[next_base]["fragments"]} if next_base >= 0 else set()
            removal.append(self.bake(next_base, tuple(pair for pair in replacements if pair[0] in child), added))
        for index in range(len(added)):
            removal.append(self.bake(base, replacements, added[:index] + added[index + 1:]))
        result = {key: previous[key] if previous else default
                  for key, default in (("collision", []), ("selection", []), ("opacity", 0), ("luminance", 0))}
        if previous and previous.get("physicalFromPoses"):
            result["physicalFromPoses"] = True
        result.update({"key": "studio_" + sha256(stable_json([base, replacements, added])).hexdigest(),
                       "resident": residents[0] if residents else -1, "remove": removal,
                       "fragments": [{"pose": key, "origin": delta, "member": member} for key, delta, member in fragments]})
        index = len(self.base) + len(self.extra)
        require(index < 65536, "Static recipe limit")
        self.extra.append(result)
        return index


class Migration:
    def __init__(self, args, inventory, catalogue, dimension):
        self.args = args
        self.root = args.world / dimension
        self.dimension = dimension
        self.dimension_id = "minecraft:overworld" if dimension == "." else ":".join(Path(dimension).parts[1:])
        self.world = WorldEdit(self.root)
        self.inv = inventory
        self.models = read_models(json.loads(args.project.read_text(encoding="utf-8")))
        self.static = catalogue
        self.window = json.loads(args.window.read_bytes())
        self.shards = {}
        self.dynamic_roots = {}
        self.static_roots = {}
        self.targets = set()
        self.moved = {}
        self.count = Counter()
        self.rows = Counter()
        self.static_changes = {}
        self.foreign_static = Counter()
        self.foreign_rows = []

    def shard(self, pos):
        cx, cz = key = chunk_key(pos)
        if key not in self.shards:
            self.shards[key] = OwnerShard(self.root / SHARD_DIR / f"owners-{cx}.{cz}.nbt", cx, cz)
        return self.shards[key]

    def stock_root(self, pos):
        chunk = self.world.at(pos)
        tag = chunk.entities.get(pos)
        require(tag is not None and "resident" in compound(tag), f"Missing resident at {pos}")
        return tag, compound(tag)["resident"]

    def dynamic(self):
        affected = defaultdict(set)
        next_entries = defaultdict(list)
        selected_roots = []
        for identifier, objects in self.inv["roots"].items():
            for kind, dimension, location, props, _ in objects:
                if kind != "block" or dimension != self.dimension or identifier not in (self.models.keys() | OLD_WINDOWS):
                    continue
                pos = tuple(location)
                current = self.world.at(pos).state(pos)
                require(name(current) == identifier and properties(current) == props, f"Input block changed at {pos}")
                if identifier in OLD_WINDOWS:
                    opened = identifier.endswith("_open") or props["variant"] == "1"
                    next_id, props = WINDOW, {**props, "variant": "0", "open": str(opened).lower()}
                    self.count["replaced_90952"] += 1
                else:
                    next_id = identifier
                model = self.models.get(next_id)
                pose = selected(model, props)
                anchor = model["anchor"] if model else ZERO
                if pose is not None or anchor != ZERO:
                    selected_roots.append((pos, next_id, props, pose, anchor, identifier))
        reserved = {pos for pos, *_ in selected_roots}
        for pos, next_id, props, pose, anchor, original_id in selected_roots:
            old_be, owner = self.stock_root(pos)
            uid = owner_uuid(owner)
            fields = compound(old_be)
            payload = deepcopy(fields["payload"])
            shard = self.shard(pos)
            old = shard.members.pop(uid, None)
            require(old is not None, f"Missing owner membership at {pos}")
            shard.dirty = True
            old_shapes = {tuple(compound(row)["offset"].value): shard.shapes[compound(row)["shapeRef"].value]
                          for row in old["members"].value}
            root = pos
            yaw = int(props["rotation"])
            if anchor != ZERO:
                target = add(pos, tuple(round(v) for v in rotate(anchor, yaw)))
                if yaw % 2 == 0 and target not in reserved and target not in self.targets and self.free_root(target) \
                        and set(fields) <= {"id", "x", "y", "z", "resident", "payload", "owners", "OwnerDataV1"}:
                    root = target
                    self.count["dynamic_anchor_moved"] += 1
                else:
                    compound(payload)["SourceShift"] = typed_vector(add(shift(payload), anchor))
                    self.count["dynamic_anchor_retained"] += 1
            require(root not in self.targets, f"Repeated resident at {root}")
            self.targets.add(root)
            old_cells = {add(pos, delta): shape for delta, shape in old_shapes.items()}
            if pose is not None:
                if original_id in OLD_WINDOWS:
                    stock = window_stock(self.window, props, payload)
                else:
                    stock = {subtract(cell, root) for cell in old_cells}
                cells, modes = layout_cells(pose, anchor, props, shift(payload), mount(payload))
                shapes = {cell: shape_tag(modes["blocks"].get(cell, ()), modes["outline"].get(cell, ())) for cell in cells | stock}
            else:
                shapes = {subtract(cell, root): shape for cell, shape in old_cells.items()}
                shapes.setdefault(ZERO, shape_tag((), ()))
            next_owner = Tag(10, {"id": Tag(8, next_id), "root": Tag(11, list(root)), "uuid": compound(owner)["uuid"]})
            data = Tag(10, {"id": Tag(8, next_id), "properties": Tag(10, {key: Tag(8, value) for key, value in props.items()}),
                            "payload": Tag(7, encode_nbt(NbtFile("", payload)))})
            for cell in old_cells:
                affected[cell].add(uid)
            for offset, shape in shapes.items():
                next_entries[add(root, offset)].append((next_owner, data, shape))
            self.shard(root).set_members(next_owner, shapes)
            self.dynamic_roots[root] = (state_tag(next_id, props), next_owner, payload, old_be)
            self.moved[uid] = (pos, root, original_id, next_id)
            self.count["dynamic_updated"] += 1
            self.rows[next_id] += 1
        self.count["dynamic_cells"] = len(affected.keys() | next_entries.keys())
        return affected, next_entries

    def free_root(self, pos):
        try:
            chunk = self.world.at(pos)
        except (OSError, ValueError):
            return False
        kind = name(chunk.state(pos))
        entity = chunk.entities.get(pos)
        if kind in AIR:
            return entity is None
        if kind != HELPER or entity is None:
            return False
        fields = compound(entity)
        return "resident" not in fields and compound(fields["payload"]) == {} and set(fields) <= {
            "id", "x", "y", "z", "payload", "owners", "OwnerDataV1"}

    def plan_static(self):
        desired = defaultdict(set)
        relocated = {}
        masks = {}
        for identifier, objects in self.inv["roots"].items():
            model = self.models.get(identifier)
            if model is None:
                continue
            for kind, dimension, coords, props, key in objects:
                if kind != "static" or dimension != self.dimension:
                    continue
                pose = selected(model, props)
                if pose is None:
                    continue
                pos = tuple(coords)
                actual = self.world.at(pos).state(pos)
                base = static_index(actual)
                resident = self.static.base[base]["resident"] if base >= 0 else -1
                if resident < 0 or self.static.base[base]["fragments"][resident]["pose"] != key:
                    self.count["orphan_static_ignored"] += 1
                    continue
                root, next_key = pos, key
                if model["anchor"] != ZERO:
                    yaw = int(props["rotation"])
                    target = add(pos, tuple(round(v) for v in rotate(model["anchor"], yaw)))
                    if yaw % 2 == 0 and target not in self.targets and self.free_static_root(target):
                        root = target
                        next_key = self.static.move_pose(key, model["anchor"])
                        relocated[(key, pos)] = (next_key, root)
                        self.targets.add(root)
                        self.count["static_anchor_moved"] += 1
                    else:
                        self.count["static_anchor_retained"] += 1
                self.static_roots[root] = next_key
                catalogued = self.static.poses[next_key]
                if next_key not in masks:
                    masks[next_key] = layout_cells(pose, ZERO, props, tuple(catalogued["shift"]), catalogued["mountY"])[0]
                cells = masks[next_key]
                for offset in cells:
                    desired[add(root, offset)].add((next_key, tuple(-v for v in offset)))
                self.count["static_updated"] += 1
                self.rows[identifier] += 1

        for cell, base, original in self.world.static_cells():
            record = self.static.base[base]
            changes = []
            needed = desired.pop(cell, set())
            for fragment in record["fragments"]:
                old = self.static.packed(fragment)
                key, offset, member = old
                moved = relocated.get((key, add(cell, offset)))
                if moved:
                    next_key, root = moved
                    next_offset = subtract(root, cell)
                    changes.append((old, (next_key, next_offset, member)))
                    needed.discard((next_key, next_offset))
                else:
                    needed.discard((key, offset))
            if changes or needed:
                self.add_static(cell, base, tuple(sorted(changes)), needed, properties(original).get("waterlogged", "false"))
        for cell, needed in desired.items():
            if cell in self.dynamic_roots:
                self.foreign_static["dynamic-root"] += len(needed)
                self.foreign_rows.extend((cell, key, offset) for key, offset in sorted(needed))
                continue
            current = self.world.at(cell).state(cell)
            if name(current) in AIR:
                self.add_static(cell, -1, (), needed, "false")
            else:
                self.foreign_static[name(current)] += len(needed)
                self.foreign_rows.extend((cell, key, offset) for key, offset in sorted(needed))
        self.count["static_cells_changed"] = len(self.static_changes)
        print(self.dimension, "static plan", len(self.static_changes),
              "native neighbours", sum(self.foreign_static.values()), flush=True)

    def free_static_root(self, pos):
        try:
            chunk = self.world.at(pos)
        except (OSError, ValueError):
            return False
        state = chunk.state(pos)
        index = static_index(state)
        return pos not in chunk.entities and (name(state) in AIR or index >= 0 and self.static.base[index]["resident"] == -1)

    def add_static(self, cell, base, transformations, needed, wet):
        added = tuple((key, offset, False) for key, offset in sorted(needed))
        index = self.static.bake(base, transformations, added)
        self.static_changes[cell] = state_tag(f"bloodborne_dw:static_architecture_{index // 128}",
                                              {"part": str(index % 128), "waterlogged": wet})

    def apply_dynamic(self, removals, additions):
        old_roots = {old for old, _, _, _ in self.moved.values()}
        for cell in sorted(removals.keys() | additions.keys()):
            shard = self.shard(cell)
            removed = shard.remove(cell, removals.get(cell, set()))
            require(removed == len(removals.get(cell, ())), f"Saved membership/guest mismatch at {cell}")
            for owner, data, shape in additions.get(cell, ()):
                shard.add(cell, owner, data, shape)
            refs = shard.cells.get(cell, ())
            refs = sorted(refs, key=lambda ref: uuid_order(owner_uuid(shard.owners[ref.value[0]])))
            if shard.dirty:
                shard.cells[cell] = refs
            entries = shard.expand(refs)
            chunk = self.world.at(cell)
            original = chunk.state(cell)
            block = name(original)
            be = chunk.entities.get(cell)
            if cell in self.dynamic_roots:
                next_state, owner, payload, source_be = self.dynamic_roots[cell]
                chunk.set_state(cell, next_state)
                chunk.set_entity(cell, rewrite_be(cell, source_be, owner, payload, entries))
            elif be is not None and compound(be)["id"].value == HELPER:
                fields = compound(be)
                resident = None if cell in old_roots else fields.get("resident")
                payload = EMPTY if cell in old_roots else fields["payload"]
                if entries or resident is not None:
                    if cell in old_roots and block != HELPER:
                        chunk.set_state(cell, state_tag(HELPER))
                    chunk.set_entity(cell, rewrite_be(cell, be, resident, payload, entries))
                else:
                    require(set(fields) <= {"id", "x", "y", "z", "resident", "payload", "owners", "OwnerDataV1"},
                            f"Cannot erase protected carrier payload at {cell}")
                    chunk.set_state(cell, state_tag("minecraft:air"))
                    chunk.set_entity(cell, None)
            elif block in AIR and entries and cell not in self.static_changes:
                chunk.set_state(cell, state_tag(HELPER))
                chunk.set_entity(cell, rewrite_be(cell, None, None, EMPTY, entries))
                self.count["dynamic_helpers_added"] += 1
            elif entries and static_index(self.static_changes.get(cell, original)) >= 0:
                self.count["dynamic_guest_on_static"] += 1
        for cell, state in self.static_changes.items():
            self.world.at(cell).set_state(cell, state)

    def rp_entities(self):
        wanted = {registry: model for registry, model in self.models.items() if registry.startswith("bloodborne_rp:")}
        for path in (self.root / "entities").glob("r.*.*.mca"):
            if not path.stat().st_size:
                continue
            region = RegionFile.open(path)
            for chunk in region.chunks():
                raw = chunk.raw_nbt()
                if b"bloodborne_rp:" not in raw:
                    continue
                nbt = chunk.nbt()
                edited = False
                for entity in map(compound, compound(nbt.root)["Entities"].value):
                    registry = entity["id"].value
                    model = wanted.get(registry)
                    if model is None:
                        continue
                    self.count["rp_updated_by_runtime"] += 1
                    self.rows[registry] += 1
                    if model["anchor"] == ZERO:
                        continue
                    yaw, pitch = vector(entity["Rotation"])
                    pitch = pitch * pi / 180
                    yaw = (yaw - 90) * pi / 180
                    x, y, z = model["anchor"]
                    scale = entity.get("Scale", Tag(5, 1.)).value * self.args.rp_anchor_asset_scale
                    x, y = (x * cos(pitch) - y * sin(pitch)) * scale, (x * sin(pitch) + y * cos(pitch)) * scale
                    z *= scale
                    delta = (x * cos(yaw) + z * sin(yaw), y, -x * sin(yaw) + z * cos(yaw))
                    entity["Pos"] = typed_vector(add(vector(entity["Pos"]), delta))
                    self.count["rp_anchor_moved"] += 1
                    edited = True
                if edited:
                    region.set_chunk(chunk.x, chunk.z, nbt, compression=chunk.compression, timestamp=chunk.timestamp)
            if region._dirty:
                region.save(path)

    def save_references(self):
        # Root-bearing mechanism and BB-tool records keep the original UUID.
        def packed(pos):
            x, y, z = pos
            raw = ((x & 0x3ffffff) << 38) | ((z & 0x3ffffff) << 12) | (y & 0xfff)
            return raw if raw < 1 << 63 else raw - (1 << 64)

        def update(tag):
            if tag.type == 9:
                return sum(update(child) for child in tag.value)
            if tag.type != 10:
                return 0
            fields = compound(tag)
            for key in ("Instance", "UUID"):
                uid = fields.get(key)
                if uid is None or uid.type != 11 or tuple(uid.value) not in self.moved:
                    continue
                old, root, old_id, new_id = self.moved[tuple(uid.value)]
                if ((old != root or old_id != new_id) and fields.get("Root", Tag(4, -1)).value == packed(old)
                        and fields.get("Registry", Tag(8, "")).value == old_id
                        and fields.get("Dimension", Tag(8, "")).value == self.dimension_id):
                    fields["Root"] = Tag(4, packed(root))
                    fields["Registry"] = Tag(8, new_id)
                    return 1
            return sum(update(child) for child in fields.values())

        for path in (self.args.world / "data").glob("bloodborne_dw_*.dat"):
            if path.name == "bloodborne_dw_composite_owners.dat":
                continue
            nbt = read_nbt(path)
            count = update(nbt.root)
            if count:
                write_nbt(path, nbt)
                self.count["links_updated"] += count

    def flush(self):
        self.world.flush()
        for shard in self.shards.values():
            shard.flush()
        # The index and its files form one saved structure.
        ledger_path = self.root / "data/bloodborne_dw_composite_owners.dat"
        ledger = read_nbt(ledger_path)
        data = compound(compound(ledger.root)["data"])
        chunks = set(data["OwnerShardChunks"].value)
        for (x, z), shard in self.shards.items():
            if shard.dirty:
                chunks.add((z << 32) | (x & 0xffffffff))
        data["OwnerShardChunks"] = Tag(12, sorted(chunks))
        write_nbt(ledger_path, ledger)
        if self.foreign_rows:
            ids = {name: index for index, name in enumerate(self.static.poses)}
            rows = [value for (x, y, z), key, (ox, oy, oz) in sorted(self.foreign_rows)
                    for value in (x, y, z, ox, oy, oz, ids[key])]
            write_nbt(self.root / "data/bloodborne_dw_studio_overhangs.dat",
                      NbtFile("", Tag(10, {"DataVersion": Tag(3, 3465),
                            "data": Tag(10, {"Rows": Tag(11, rows)})})))
        return {"counts": dict(self.count), "modelPlacements": dict(self.rows),
                "staticForeignCells": dict(self.foreign_static)}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for flag in ("world", "inventory", "project", "static", "window", "append", "report"):
        parser.add_argument("--" + flag, type=Path, required=True)
    parser.add_argument("--rp-anchor-asset-scale", type=float, required=True)
    args = parser.parse_args()
    require(args.world.resolve() != Path("C:/Users/vakir/Desktop/bloodborne").resolve(), "Use a disposable copy")
    require(not args.report.exists(), "Choose a new audit output path")
    inventory = json.loads(args.inventory.read_text(encoding="utf-8"))
    require(sha256((args.world / "level.dat").read_bytes()).hexdigest() == inventory["worldLevelSha256"],
            "World snapshot differs from inventoried world")
    raw = args.static.read_bytes()
    catalogue = StaticCatalogue(raw)
    dimensions = sorted({row[1] for objects in inventory["roots"].values() for row in objects})
    reports = {}
    for dimension in dimensions:
        migration = Migration(args, inventory, catalogue, dimension)
        removals, additions = migration.dynamic()
        migration.plan_static()
        migration.apply_dynamic(removals, additions)
        migration.rp_entities()
        migration.save_references()
        reports[dimension] = migration.flush()
        print(dimension, reports[dimension]["counts"], flush=True)
    supplement = {"baseSha256": sha256(raw).hexdigest(), "baseRecipes": len(catalogue.base),
                  "poses": catalogue.new_poses, "recipes": catalogue.extra}
    args.append.write_bytes(stable_json(supplement) + b"\n")
    report = {"status": "MIGRATED", "dimensions": reports, "staticRecipesAdded": len(catalogue.extra),
              "staticPosesAdded": len(catalogue.new_poses), "archiveSha256": inventory["archiveSha256"]}
    args.report.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
