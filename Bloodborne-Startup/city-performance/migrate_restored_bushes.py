"""Convert isolated restored bushes and verified map decoration to static recipes."""

import argparse
from collections import Counter, defaultdict
from contextlib import nullcontext
from functools import lru_cache
import hashlib
import json
from pathlib import Path
import re
import struct
import sys
import time
import uuid
from zipfile import ZIP_DEFLATED, ZipFile

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "DW-City-Visuals/tools"))
from map_io.studio_save import HELPER, SHARD_DIR, ChunkEdit, OwnerShard, chunk_key, name, owner_uuid, properties, state_tag
from map_io.world_io import RegionFile, Tag, compound, decode_nbt, invalidate_chunk_lighting, read_nbt, section_blocks


CATALOG = "bloodborne_dw/static-architecture-v1274.json"
DIMENSIONS = {"overworld": Path(), "bloodborne_old": Path("dimensions/bloodborne_dw/bloodborne_old")}
SOURCE_ZIP = "Bloodborne-v22-Bush-Restoration-world.zip"
BUSH_IDS = {f"bloodborne_dw:source_addon_grass_{i}" for i in (0, 1, 2, 5, 6, 7)}
HISTORICAL_WORLD_HASH = "43a9e865e2cd8f419e57d41c3224a9086ac718e1b1294d47f3834c851a98e3e1"
DECORATION = {
    "bloodborne_dw:source_chiseled_nether_bricks": ("4", "translucent", {"minecraft:dried_kelp_block"}),
    "bloodborne_dw:source_brick_wall_side_tall": ("2", "cutout", {"minecraft:bricks", "minecraft:deepslate_gold_ore"}),
    "bloodborne_dw:source_polished_blackstone": ("0", "cutout", {"minecraft:polished_blackstone"}),
}
ZERO = (0, 0, 0)
BE_FIELDS = {"id", "x", "y", "z", "payload", "OwnerDataV1"}
LEDGER = "bloodborne_dw_composite_owners.dat"
OVERHANGS = "bloodborne_dw_studio_overhangs.dat"
UUID_PATTERN = re.compile(r"[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}$", re.I)


def require(condition, message):
    if not condition:
        raise ValueError(message)


def digest(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def add(a, b):
    return tuple(x + y for x, y in zip(a, b, strict=True))


def region_coords(pos):
    return pos[0] // 512, pos[2] // 512


def region_path(root, rx, rz):
    return root / "region" / f"r.{rx}.{rz}.mca"


def state(index):
    return state_tag(f"bloodborne_dw:static_architecture_{index // 128}", {"part": str(index % 128)})


def recipes_from(jar, include_decoration):
    optics_review = {identifier: ("0", "cutout", {"minecraft:dead_fire_coral_fan[waterlogged=false]"})
                     for identifier in BUSH_IDS}
    if include_decoration:
        optics_review.update(DECORATION)
    with ZipFile(jar) as archive:
        raw = archive.read(CATALOG)
        catalog = json.loads(raw)
        studio = archive.read("bloodborne_dw/studio-layouts-v22.json")
        optics = json.loads(archive.read("bloodborne_dw/imported-source-lighting-v127.json"))
        assemblies = json.loads(archive.read("bloodborne_dw/imported-assembly-v126.json"))
        aliases = json.loads(archive.read("bloodborne_dw/imported-type-aliases-v127.json"))
        functional = {part["registryId"] for recipe in assemblies["recipes"] for part in recipe["components"]}
        functional.add(assemblies["explicitDoorReplacement"]["oldRegistryId"])
        functional.update(row["oldRegistryId"] for row in aliases["mappings"])
        require(not (functional & optics_review.keys()), "Functional source family in static review")
        for identifier, (variant, layer, sources) in optics_review.items():
            matches = [row for row in optics["objects"] if row["registryId"] == identifier
                       and str(row["variant"]) == variant and not row["open"]]
            require(len(matches) == 1 and not matches[0]["stateProperties"]
                    and {source["sourceState"] for source in matches[0]["sources"]} == sources,
                    f"Source optics changed for {identifier}")
            require(identifier.encode() not in studio, f"Studio model override for {identifier}")
            descriptor = json.loads(archive.read("bloodborne_dw/composite/" + identifier.split(":")[1] + ".json"))
            require(not descriptor["openable"] and not descriptor["support"]["required"]
                    and descriptor["layer"] == layer and not descriptor.get("functionalDoorPhysics"), identifier)
        poses = {}
        for pose in catalog["poses"]:
            identifier = pose["registryId"]
            if identifier not in optics_review or pose["properties"]["variant"] != optics_review[identifier][0]:
                continue
            if identifier in DECORATION and tuple(pose["shift"]) != ZERO:
                continue
            if pose["mountY"] == 0:
                key = identifier, tuple(sorted(pose["properties"].items())), tuple(pose["shift"])
                require(key not in poses, f"Ambiguous pose {key}")
                poses[key] = pose["key"]
        by_pose = defaultdict(dict)
        pose_ids = {value: key[0] for key, value in poses.items()}
        assets = set(archive.namelist())
        for index, recipe in enumerate(catalog["recipes"]):
            if len(recipe["fragments"]) != 1 or recipe["remove"] != [-1]:
                continue
            fragment = recipe["fragments"][0]
            identifier = pose_ids.get(fragment["pose"])
            if identifier is None or not fragment["member"]:
                continue
            offset = tuple(-x for x in fragment["origin"])
            expected_opacity = 15 if identifier in DECORATION and offset == ZERO else 0
            if (recipe["opacity"] != expected_opacity or recipe["luminance"] or recipe.get("physicalFromPoses")
                    or recipe["resident"] != (0 if offset == ZERO else -1)):
                continue
            bank = f"assets/bloodborne_dw/blockstates/static_architecture_{index // 128}.json"
            require(bank in assets, f"Missing bank for {index}")
            by_pose[fragment["pose"]].setdefault(offset, []).append((index, recipe["collision"], recipe["selection"]))
        return hashlib.sha256(raw).hexdigest(), {key: by_pose[pose] for key, pose in poses.items()}


def data_fields(path):
    return compound(compound(read_nbt(path).root)["data"])


def protected_refs(world):
    positions = defaultdict(set)
    identities = set()

    def visit(tag):
        if tag.type == 10:
            fields = compound(tag)
            dimension, root = fields.get("Dimension"), fields.get("Root")
            if dimension and dimension.type == 8 and root and root.type == 4:
                positions[dimension.value].add(root.value)
            for child in fields.values():
                visit(child)
        elif tag.type == 9:
            for child in tag.value:
                visit(child)
        elif tag.type == 11 and len(tag.value) == 4:
            identities.add(tuple(tag.value))
        elif tag.type == 8:
            match = UUID_PATTERN.search(tag.value)
            if match:
                identities.add(struct.unpack(">4i", uuid.UUID(match.group()).bytes))

    for directory in DIMENSIONS.values():
        for path in (world / directory / "data").glob("bloodborne_dw_*.dat"):
            if path.name not in {LEDGER, OVERHANGS}:
                for tag in data_fields(path).values():
                    visit(tag)
    return identities, positions


def packed(pos):
    x, y, z = pos
    value = ((x & 0x3ffffff) << 38) | ((z & 0x3ffffff) << 12) | (y & 0xfff)
    return value if value < 1 << 63 else value - (1 << 64)


class Dimension:
    def __init__(self, root):
        self.root = root
        self.shard_files = {}
        for path in (root / SHARD_DIR).glob("*.nbt"):
            x, z = map(int, path.stem.removeprefix("owners-").split("."))
            self.shard_files[x, z] = path
        ledger = data_fields(root / "data" / LEDGER)
        index = {(z << 32) | (x & 0xffffffff) for x, z in self.shard_files}
        require(ledger["OwnerShardSchema"].value == 1 and not ledger["cells"].value
                and sorted(index) == sorted(ledger["OwnerShardChunks"].value), f"Invalid shard index at {root}")
        self.overhangs = set()
        rows = data_fields(root / "data" / OVERHANGS)["Rows"].value
        require(len(rows) % 7 == 0, f"Invalid Studio overhangs at {root}")
        for i in range(0, len(rows), 7):
            pos = tuple(rows[i:i + 3])
            self.overhangs.update((pos, add(pos, rows[i + 3:i + 6])))
        self.region = lru_cache(maxsize=6)(self.region)
        self.chunk = lru_cache(maxsize=48)(self.chunk)
        self.shard = lru_cache(maxsize=32)(self.shard)

    def region(self, rx, rz):
        return RegionFile.open(region_path(self.root, rx, rz))

    def chunk(self, cx, cz):
        record = self.region(cx // 32, cz // 32).get_chunk(cx % 32, cz % 32)
        require(record is not None, f"Missing saved chunk {cx, cz}")
        chunk = ChunkEdit(record)
        chunk.native = set()
        visuals = chunk.fields.get("DreamwalkerVisuals")
        if visuals:
            for tag in compound(visuals)["Sections"].value:
                row = compound(tag)
                chunk.native.update((row["Y"].value, value & 4095) for value in row["Entries"].value)
        chunk.ticks = {tuple(compound(tag)[axis].value for axis in "xyz")
                       for key in ("block_ticks", "fluid_ticks") for tag in chunk.fields[key].value}
        return chunk

    def shard(self, cx, cz):
        return OwnerShard(self.shard_files[cx, cz], cx, cz)

    def clear_cache(self):
        self.region.cache_clear()
        self.chunk.cache_clear()
        self.shard.cache_clear()


def same_box_list(tag, boxes):
    return [[value.value for value in row.value] for row in tag.value] == boxes


def standard_entity(fields, is_root):
    expected = BE_FIELDS | {"resident"} if is_root else BE_FIELDS
    return set(fields) - {"keepPacked"} == expected and fields.get("keepPacked", Tag(1, 0)) == Tag(1, 0)


class HistoricalWorld:
    def __init__(self, archive, dimension):
        self.archive = archive
        self.dimension = dimension
        self.region = lru_cache(maxsize=4)(self.region)
        self.chunk = lru_cache(maxsize=32)(self.chunk)

    def region(self, rx, rz):
        path = region_path(Path("bloodborne") / self.dimension, rx, rz).as_posix()
        try:
            return RegionFile(self.archive.read(path))
        except KeyError:
            return None

    def chunk(self, cx, cz):
        region = self.region(cx // 32, cz // 32)
        record = region.get_chunk(cx % 32, cz % 32) if region else None
        return ChunkEdit(record) if record else None

    def matches(self, root, state, fields):
        chunk = self.chunk(*chunk_key(root))
        if chunk is None or root not in chunk.entities or chunk.state(root) != state:
            return False
        old_fields = compound(chunk.entities[root])
        return (standard_entity(old_fields, True) and old_fields["resident"] == fields["resident"]
                and old_fields["payload"] == fields["payload"])


def candidate(dim, root, identifier, catalog, linked, protected):
    chunk = dim.chunk(*chunk_key(root))
    original = chunk.state(root)
    block_props = properties(original)
    if name(original) != identifier:
        return "different_root", None
    if block_props.get("open") != "false" or block_props.get("profile") != "base":
        return "different_root_properties", None
    be = chunk.entities.get(root)
    if be is None or not standard_entity(compound(be), True):
        return "unexpected_root_data", None
    fields = compound(be)
    resident = fields["resident"]
    owner = compound(resident)
    identity = owner_uuid(resident)
    if (set(owner) != {"id", "root", "uuid"} or owner["id"] != Tag(8, identifier)
            or owner["root"] != Tag(11, list(root)) or len(identity) != 4):
        return "unexpected_resident", None
    if identity in linked:
        return "linked_owner", None
    payload = fields["payload"]
    payload_fields = compound(payload)
    if (set(payload_fields) - {"ImportedTypeVersion", "SourceShift"}
            or payload_fields.get("ImportedTypeVersion") != Tag(3, 1)):
        return "unsupported_payload", None
    shift = payload_fields.get("SourceShift", Tag(9, [Tag(6, 0.0)] * 3, 6))
    if shift.type != 9 or shift.list_type != 6 or len(shift.value) != 3:
        return "unsupported_shift", None
    table = catalog.get((identifier, tuple(sorted(block_props.items())), tuple(tag.value for tag in shift.value)))
    if not table:
        return "uncatalogued_pose", None
    shard = dim.shard(*chunk_key(root))
    member = shard.members.get(identity)
    if member is None or shard.owners[member["ownerRef"].value] != resident:
        return "missing_membership", None
    shapes = {tuple(row["offset"].value): shard.shapes[row["shapeRef"].value]
              for row in map(compound, member["members"].value)}
    if len(shapes) != len(member["members"].value) or shapes.keys() != table.keys():
        return "different_footprint", None
    replacement = {}
    for offset, shape in shapes.items():
        pos = add(root, offset)
        if pos in dim.overhangs or packed(pos) in protected:
            return "referenced_cell", None
        member_chunk = dim.chunk(*chunk_key(pos))
        if member_chunk.cell(pos) in member_chunk.native or name(member_chunk.state(pos)).startswith("bloodborne_dw:native_"):
            return "native_carrier", None
        if pos in member_chunk.ticks:
            return "scheduled_tick", None
        if offset != ZERO and name(member_chunk.state(pos)) != HELPER:
            return "foreign_carrier", None
        member_shard = dim.shard(*chunk_key(pos))
        refs = member_shard.cells.get(pos, ())
        if len(refs) != 1:
            return "owner_overlap", None
        entry, = member_shard.expand(refs)
        data = compound(entry)
        if data["owner"] != resident or any(data[k] != compound(shape)[k] for k in ("collision", "selection")):
            return "different_owner_data", None
        root_data = compound(data["rootData"])
        if (set(root_data) != {"id", "properties", "payload"} or root_data["id"] != Tag(8, identifier)
                or compound(root_data["properties"]) != {k: Tag(8, v) for k, v in block_props.items()}
                or decode_nbt(root_data["payload"].value).root != payload):
            return "different_owner_payload", None
        member_be = member_chunk.entities.get(pos)
        if member_be is None:
            return "missing_cell_entity", None
        member_fields = compound(member_be)
        if (not standard_entity(member_fields, offset == ZERO)
                or member_fields["id"] != Tag(8, HELPER)
                or (offset != ZERO and member_fields["payload"] != Tag(10, {}))
                or properties(member_chunk.state(pos)) != (block_props if offset == ZERO else {})):
            return "protected_cell_entity", None
        local_owners = compound(decode_nbt(member_fields["OwnerDataV1"].value).root)
        if set(local_owners) != {"owners"} or local_owners["owners"].value != [entry]:
            return "different_cell_owner_data", None
        available = [index for index, collision, selection in table[offset]
                     if same_box_list(compound(shape)["collision"], collision)
                     and same_box_list(compound(shape)["selection"], selection)]
        if len(available) != 1:
            return "different_catalog_shape", None
        replacement[pos] = available[0]
    return "converted", (identity, replacement)


def historical_roots(dim):
    for coords in sorted(dim.shard_files):
        shard = dim.shard(*coords)
        for membership in shard.members.values():
            owner = compound(shard.owners[membership["ownerRef"].value])
            identifier = owner["id"].value
            if identifier in DECORATION:
                pos = owner["root"].value
                yield pos, pos, identifier


def plan(dim, provenance, catalog, linked, protected, history):
    outcomes = Counter()
    by_type = defaultdict(Counter)
    roots = {}
    source = list(provenance)
    if history is not None:
        source.extend(historical_roots(dim))
    for original, anchor, identifier in sorted(source, key=lambda row: (chunk_key(row[1]), row[1])):
        root = tuple(anchor)
        if original != anchor:
            outcomes["relocated_root"] += 1
            by_type[identifier]["relocated_root"] += 1
            continue
        result, value = candidate(dim, root, identifier, catalog, linked, protected)
        if value is not None and identifier in DECORATION:
            chunk = dim.chunk(*chunk_key(root))
            if not history.matches(root, chunk.state(root), compound(chunk.entities[root])):
                result, value = "different_historical_root", None
        outcomes[result] += 1
        by_type[identifier][result] += 1
        if value is not None:
            require(root not in roots, f"Duplicate restored root {root}")
            roots[root] = (identifier, *value)
    # Check all shards for unexpected copies of the selected owner identities.
    expected = {identity: set(cells) for _, identity, cells in roots.values()}
    require(len(expected) == len(roots), "Repeated selected bush owner")
    observed = defaultdict(set)
    memberships = defaultdict(set)
    for coords in sorted(dim.shard_files):
        shard = dim.shard(*coords)
        relevant = {i: key for i, owner in enumerate(shard.owners) if (key := owner_uuid(owner)) in expected}
        if not relevant:
            continue
        for pos, refs in shard.cells.items():
            for row in refs:
                key = relevant.get(row.value[0])
                if key is not None:
                    observed[key].add(pos)
        for key in shard.members.keys() & expected.keys():
            memberships[key].add(coords)
    for root, (identifier, identity, _) in list(roots.items()):
        if observed[identity] != expected[identity] or memberships[identity] != {chunk_key(root)}:
            del roots[root]
            outcomes["converted"] -= 1
            outcomes["unexpected_owner_reference"] += 1
            by_type[identifier]["converted"] -= 1
            by_type[identifier]["unexpected_owner_reference"] += 1
    dim.clear_cache()
    return outcomes, by_type, roots


def shard_contents(shard):
    cells = {pos: shard.expand(refs) for pos, refs in shard.cells.items() if refs}
    members = {key: (shard.owners[row["ownerRef"].value],
                     [(compound(tag)["offset"], shard.shapes[compound(tag)["shapeRef"].value]) for tag in row["members"].value])
               for key, row in shard.members.items()}
    return cells, members


def verify_region(before, after, edits):
    old_chunks = {(chunk.x, chunk.z): chunk for chunk in before.chunks()}
    new_chunks = {(chunk.x, chunk.z): chunk for chunk in after.chunks()}
    require(old_chunks.keys() == new_chunks.keys(), "Changed region chunk population")
    for key, original in old_chunks.items():
        current = new_chunks[key]
        if key not in edits:
            require(original == current, f"Unrelated chunk changed at {key}")
            continue
        require((original.timestamp, original.compression) == (current.timestamp, current.compression), "Chunk header changed")
        old, new = ChunkEdit(original), ChunkEdit(current)
        replacements = edits[key]
        require(new.entities == {pos: tag for pos, tag in old.entities.items() if pos not in replacements}, "Unrelated block entity changed")
        # Normal offline block edits invalidate only these derived caches.
        old.fields.pop("Heightmaps", None)
        invalidate_chunk_lighting(old.fields)
        for tag in (old, new):
            del tag.fields["block_entities"]
            del tag.fields["sections"]
        require(old.sections.keys() == new.sections.keys(), "Changed section count")
        expected = defaultdict(dict)
        for pos, index in replacements.items():
            height, local = old.cell(pos)
            expected[height][local] = state(index)
        for height, section in old.sections.items():
            new_section = new.sections[height]
            if height not in expected:
                require(section == new_section, "Unrelated section changed")
                continue
            old_palette, old_indices = section_blocks(section)
            new_palette, new_indices = section_blocks(new_section)
            require(all(new_palette[b] == expected[height].get(i, old_palette[a])
                        for i, (a, b) in enumerate(zip(old_indices, new_indices, strict=True))), "Unexpected block state change")
            del compound(section)["block_states"]
            del compound(new_section)["block_states"]
            require(section == new_section, "Unrelated section metadata changed")
        require(old.nbt == new.nbt, "Unrelated chunk NBT changed")


def apply(dim, roots):
    changes = defaultdict(dict)
    shard_cells, shard_roots = defaultdict(dict), defaultdict(dict)
    for root, (_, identity, cells) in roots.items():
        shard_roots[chunk_key(root)][identity] = root
        for pos, index in cells.items():
            require(pos not in shard_cells[chunk_key(pos)], f"Selected bushes overlap at {pos}")
            shard_cells[chunk_key(pos)][pos] = identity
            cx, cz = chunk_key(pos)
            changes[region_coords(pos)].setdefault((cx % 32, cz % 32), {})[pos] = index
    modified = set()
    for (rx, rz), edits in changes.items():
        path = region_path(dim.root, rx, rz)
        region = RegionFile.open(path)
        for (cx, cz), replacements in edits.items():
            chunk = ChunkEdit(region.get_chunk(cx, cz))
            for pos, index in replacements.items():
                chunk.set_state(pos, state(index))
                chunk.set_entity(pos, None)
            chunk.flush(region)
        before = RegionFile.open(path)
        region.save(path)
        verify_region(before, RegionFile.open(path), edits)
        modified.add(path)
    for coords, cells in shard_cells.items():
        shard = dim.shard(*coords)
        before_cells, before_members = shard_contents(shard)
        for pos, identity in cells.items():
            require(shard.remove(pos, {identity}) == 1, f"Unexpected owner at {pos}")
            del before_cells[pos]
        for identity in shard_roots[coords]:
            del shard.members[identity]
            del before_members[identity]
        shard.flush()
        reread = OwnerShard(shard.path, *coords)
        require(shard_contents(reread) == (before_cells, before_members), "Unrelated ownership data changed")
        modified.add(shard.path)
    dim.clear_cache()
    return modified


def extract(archive, target):
    with ZipFile(archive) as source:
        names = source.namelist()
        require(len(names) == len(set(names)) and all(
            Path(name).parts[0] == "bloodborne" and ".." not in Path(name).parts
            and "\\" not in name and ":" not in name and not name.endswith("/") for name in names), "Unexpected world archive layout")
        source.extractall(target)


def package(source_zip, target, world, changed):
    manifest = {"bloodborne/" + path.relative_to(world).as_posix(): path for path in world.rglob("*") if path.is_file()}
    with ZipFile(source_zip) as source, ZipFile(target, "x", ZIP_DEFLATED, compresslevel=4) as output:
        require(manifest.keys() == set(source.namelist()), "World file population changed")
        for filename, path in manifest.items():
            if path not in changed:
                with source.open(filename) as stream:
                    require(hashlib.file_digest(stream, "sha256").hexdigest() == digest(path), f"Unexpected file change at {filename}")
            output.write(path, filename)
    with ZipFile(target) as output:
        require(output.testzip() is None, "Archive CRC verification failed")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for option in ("world-zip", "bush-report", "jar", "output-world", "output-zip", "output-report"):
        parser.add_argument("--" + option, type=Path, required=True)
    parser.add_argument("--historical-world-zip", type=Path,
                        help="Verified October 27 map used to match original decorative roots")
    args = parser.parse_args()
    start = time.monotonic()
    provenance = json.loads(args.bush_report.read_bytes())
    source_hash = digest(args.world_zip)
    require(provenance["files"][SOURCE_ZIP]["sha256"] == source_hash, "Input world differs from the restoration release")
    history_hash = digest(args.historical_world_zip) if args.historical_world_zip else None
    require(history_hash is None or history_hash == HISTORICAL_WORLD_HASH, "Historical source archive differs from the October 27 map")
    require(not any(path.exists() for path in (args.output_world, args.output_zip, args.output_report)), "Choose unused output paths")
    catalog_hash, catalog = recipes_from(args.jar, history_hash is not None)
    args.output_world.mkdir()
    extract(args.world_zip, args.output_world)
    world = args.output_world / "bloodborne"
    linked, protected = protected_refs(world)
    report = {"inputWorldSha256": source_hash, "catalogSha256": catalog_hash,
              "sourceReportSha256": digest(args.bush_report), "historicalWorldSha256": history_hash,
              "editBehavior": "Converted parts use static block editing. Per-instance builder adjustments require replacing the part with the editable source object.",
              "dimensions": {}}
    changed = set()
    with ZipFile(args.historical_world_zip) if history_hash else nullcontext() as historical:
        for scope, directory in DIMENSIONS.items():
            dim = Dimension(world / directory)
            dimension_id = "minecraft:overworld" if scope == "overworld" else "bloodborne_dw:bloodborne_old"
            history = HistoricalWorld(historical, directory) if historical is not None else None
            counts, by_type, roots = plan(dim, provenance[scope]["restored"], catalog, linked, protected[dimension_id], history)
            cells_converted = sum(len(cells) for _, _, cells in roots.values())
            print(scope, dict(counts), "converted cells", cells_converted, flush=True)
            changed |= apply(dim, roots)
            report["dimensions"][scope] = {"outcomes": counts, "bySourceType": by_type, "blockEntitiesRemoved": cells_converted,
                                           "roots": [{"pos": root, "registryId": identifier,
                                                      "recipes": [{"pos": pos, "recipe": index} for pos, index in sorted(cells.items())]}
                                                     for root, (identifier, _, cells) in sorted(roots.items())]}
    require(changed, "No eligible restored bushes")
    package(args.world_zip, args.output_zip, world, changed)
    report["worldSha256"] = digest(args.output_zip)
    report["changedFiles"] = sorted(path.relative_to(world).as_posix() for path in changed)
    report["elapsedSeconds"] = round(time.monotonic() - start, 2)
    args.output_report.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(f"Verified {args.output_zip} in {report['elapsedSeconds']:.1f}s", flush=True)


if __name__ == "__main__":
    main()
