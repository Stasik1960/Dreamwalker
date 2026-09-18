#!/usr/bin/env python3
"""Read-only verification of a position-restored Bloodborne world copy.

The verifier compares the restored world with both the untouched input world
and the pre-restoration v2 world.  It never writes either world; only the JSON
report path is created.
"""

from __future__ import annotations

import argparse
import collections
import hashlib
import json
from dataclasses import dataclass, field
from pathlib import Path

import numpy as np

from world_io import (
    RegionFile,
    Tag,
    TAG_COMPOUND,
    TAG_LIST,
    TAG_STRING,
    block_state_key,
    compound,
    lighting_cache_errors,
    section_blocks,
)
from convert_modular_world import unpack_fast


ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "src/main/resources"
NS = "bloodborne_blocks:"
OLD_NS = "bloodborne:"
HELPER = NS + "architecture_part"
AIR = {"minecraft:air", "minecraft:cave_air", "minecraft:void_air"}


class ErrorLog:
    def __init__(self, limit: int):
        self.limit = limit
        self.counts = collections.Counter()
        self.examples = []
        self.equivalent_technical_cells = collections.Counter()
        self.technical_states = {}
        self.module_definitions = {}
        self.technical_positions_by_chunk = collections.defaultdict(list)
        self.before_technical_states = {}
        self.preexisting_differences = []

    def add(self, kind: str, **details):
        self.counts[kind] += 1
        if len(self.examples) < self.limit:
            self.examples.append({"kind": kind, **details})

    @property
    def total(self):
        return sum(self.counts.values())


@dataclass
class Inventory:
    role: str
    legacy_filter: set[str] | None = None
    regions: set[str] = field(default_factory=set)
    chunks: dict[str, set[tuple[int, int]]] = field(default_factory=lambda: collections.defaultdict(set))
    counts: collections.Counter = field(default_factory=collections.Counter)
    legacy_counts: collections.Counter = field(default_factory=collections.Counter)
    legacy_entries: dict[tuple[str, int, int, int], Tag] = field(default_factory=dict)
    nonhelper_entities: dict[tuple[str, int, int, int], list[Tag]] = field(default_factory=lambda: collections.defaultdict(list))
    all_entities: dict[tuple[str, int, int, int], list[Tag]] = field(default_factory=lambda: collections.defaultdict(list))
    helper_blocks: set[tuple[str, int, int, int]] = field(default_factory=set)
    helper_entities: dict[tuple[str, int, int, int], list[Tag]] = field(default_factory=lambda: collections.defaultdict(list))


def dimension_for(world: Path, region_path: Path) -> str:
    relative = region_path.relative_to(world).as_posix()
    if relative.startswith("region/"):
        return "minecraft:overworld"
    if relative.startswith("DIM-1/"):
        return "minecraft:the_nether"
    if relative.startswith("DIM1/"):
        return "minecraft:the_end"
    if "/dimensions/" in "/" + relative:
        parts = relative.split("/region/", 1)[0].split("dimensions/", 1)[1].split("/")
        if len(parts) >= 2:
            return parts[0] + ":" + "/".join(parts[1:])
    return relative.rsplit("/region/", 1)[0] or "unknown"


def region_paths(world: Path) -> dict[str, Path]:
    return {path.relative_to(world).as_posix(): path for path in sorted(world.glob("**/region/r.*.*.mca"))}


def properties(entry: Tag) -> dict[str, str]:
    values = compound(entry).get("Properties")
    return {} if values is None else {key: str(value.value) for key, value in compound(values).items()}


def state_key(values: dict[str, str]) -> str:
    return ",".join(f"{key}={values[key]}" for key in sorted(values))


def is_legacy(name: str) -> bool:
    if not name.startswith(NS):
        return False
    ident = name[len(NS):]
    return ident != "architecture_part" and not ident.startswith("m_")


def is_foreign_non_air(name: str) -> bool:
    return name not in AIR and not name.startswith(NS) and not name.startswith(OLD_NS)


def decoded_sections(root: dict) -> dict[int, tuple[list[Tag], np.ndarray, list[str]]]:
    result = {}
    for section_tag in root.get("sections", Tag(TAG_LIST, [], TAG_COMPOUND)).value:
        section = compound(section_tag)
        block_states=section.get('block_states')
        if block_states is None:
            continue
        fields=compound(block_states)
        palette=fields['palette'].value
        indices=unpack_fast(fields)
        result[int(section.get("Y", Tag(1, 0)).value)] = (
            palette,
            np.asarray(indices, dtype=np.int32),
            [block_state_key(entry) for entry in palette],
        )
    return result


def load_technical_conversions(errors):
    # Keep established lit / waterlogged architecture. These are exact recorded
    # conversions in the SAME cell, not permissions to move foreign objects.
    files=[('world-overlap-repair-v2','resolved'),('world-ornaments-v2','changes'),
           ('world-water-v2','changes'),('world-relocation-v2','changes'),
           ('world-relocation-extra-v2','changes'),('world-relocation-final-v2','changes'),
           ('world-static-grid-v2','changes'),('world-static-technical-v2','resolved'),
           ('world-static-safe-v2','resolved'),('world-static-water-v2','changes'),
           ('world-static-relocation-v2','changes'),('world-functional-repair-v2','changes')]
    for file,key in files:
        d=json.loads((ROOT/'docs'/f'{file}.json').read_text(encoding='utf-8'))
        if not d.get('applied'):continue
        for row in d.get(key,[]):
            pos=tuple(row['position']);old=row['oldState'];new=row['newState']
            if old.startswith(('minecraft:light[','minecraft:water[')):
                errors.technical_states.setdefault(pos,{'source':old,'states':set()})
            spec=errors.technical_states.get(pos)
            if spec is not None and new.startswith(NS+'m_'):
                spec['states'].add(new)
    errors.module_definitions={b['id']:b for b in json.loads((RES/'bloodborne_blocks/v2/definitions.json').read_text())['blocks']}
    for pos in errors.technical_states:
        errors.technical_positions_by_chunk[(pos[0] // 16, pos[2] // 16)].append(pos)


def equivalent_technical(region,pos,expected,actual,errors):
    if not region.startswith('region/'):
        return False
    spec=errors.technical_states.get(pos)
    if spec is None or spec['source']!=expected or actual not in spec['states']:
        return False
    ident=actual.split('[',1)[0].removeprefix(NS)
    definition=errors.module_definitions.get(ident)
    if definition is None:return False
    raw=actual.split('[',1)[1].rstrip(']') if '[' in actual else ''
    values=dict(p.split('=') for p in raw.split(',') if p)
    raw_source=expected.split('[',1)[1].rstrip(']')
    source_values=dict(p.split('=') for p in raw_source.split(',') if p)
    if expected.startswith('minecraft:water['):
        valid=source_values.get('level')=='0' and values.get('waterlogged')=='true'
        kind='source_water_preserved_as_waterlogged_module'
        # Earlier waterlogging replaced flowing water with a source. That is
        # not exact fluid-state equivalence. Retain and report this pre-existing
        # difference only if the recorded module is unchanged from before.
        if (not valid and 0 < int(source_values.get('level', '-1')) < 16
                and values.get('waterlogged') == 'true'
                and errors.before_technical_states.get(pos) == actual):
            errors.preexisting_differences.append({
                'kind': 'flowing_water_previously_waterlogged',
                'region': region, 'position': list(pos),
                'original': expected, 'beforeAndAfter': actual,
            })
            return True
    else:
        emission=definition['states'].get(state_key(values),[0,0,-1])[2]
        valid=emission>=int(source_values.get('level','15')) and (source_values.get('waterlogged')!='true' or values.get('waterlogged')=='true')
        kind='light_preserved_as_emitting_module'
    if valid:errors.equivalent_technical_cells[kind]+=1
    return valid


def state_at(sections, pos: tuple[int, int, int]) -> str:
    decoded = sections.get(pos[1] // 16)
    if decoded is None:
        return "minecraft:air"
    _, indices, keys = decoded
    index = (pos[1] % 16) * 256 + (pos[2] % 16) * 16 + pos[0] % 16
    return keys[int(indices[index])]


def block_pos(cx: int, sy: int, cz: int, index: int) -> tuple[int, int, int]:
    return cx * 16 + (index & 15), sy * 16 + (index >> 8), cz * 16 + ((index >> 4) & 15)


def entity_position(data: dict) -> tuple[int, int, int] | None:
    if not all(axis in data for axis in ("x", "y", "z")):
        return None
    return tuple(int(data[axis].value) for axis in ("x", "y", "z"))


def inspect_chunk(inv: Inventory, dimension: str, root: dict, errors: ErrorLog, *, check_light: bool):
    cx = int(root["xPos"].value)
    cz = int(root["zPos"].value)
    sections = decoded_sections(root)
    if inv.role == 'before' and dimension == 'minecraft:overworld':
        for pos in errors.technical_positions_by_chunk.get((cx, cz), []):
            errors.before_technical_states[pos] = state_at(sections, pos)
    inv.counts["chunksRead"] += 1
    inv.counts["sectionsRead"] += len(sections)
    if check_light:
        for issue in lighting_cache_errors(root):
            errors.add("invalid_starlight_cache", dimension=dimension, chunk=[cx, cz], issue=list(issue))
    for sy, (palette, indices, _) in sections.items():
        values, frequencies = np.unique(indices, return_counts=True)
        for raw_palette_index, raw_count in zip(values, frequencies):
            palette_index = int(raw_palette_index)
            count = int(raw_count)
            entry = palette[palette_index]
            name = str(compound(entry)["Name"].value)
            if name == "minecraft:barrier":
                inv.counts["barriers"] += count
            if is_legacy(name):
                ident = name[len(NS):]
                inv.legacy_counts[ident] += count
                if inv.legacy_filter is not None and ident not in inv.legacy_filter:
                    continue
                for raw_index in np.flatnonzero(indices == palette_index):
                    pos = block_pos(cx, sy, cz, int(raw_index))
                    inv.legacy_entries[(dimension, *pos)] = entry
            elif name == HELPER:
                inv.counts["helperBlocks"] += count
                for raw_index in np.flatnonzero(indices == palette_index):
                    pos = block_pos(cx, sy, cz, int(raw_index))
                    inv.helper_blocks.add((dimension, *pos))
    for entity in root.get("block_entities", Tag(TAG_LIST, [], TAG_COMPOUND)).value:
        data = compound(entity)
        pos = entity_position(data)
        entity_id = str(data.get("id", Tag(TAG_STRING, "")).value)
        if pos is None:
            errors.add("block_entity_missing_position", role=inv.role, dimension=dimension, id=entity_id)
            continue
        key = (dimension, *pos)
        inv.all_entities[key].append(entity)
        if entity_id in {HELPER, OLD_NS + "architecture_part"}:
            inv.helper_entities[key].append(entity)
        else:
            inv.nonhelper_entities[key].append(entity)
    inv.counts["blockEntities"] += len(root.get("block_entities", Tag(TAG_LIST, [], TAG_COMPOUND)).value)


def read_region(path: Path, role: str, relative: str, inv: Inventory, errors: ErrorLog):
    try:
        region = RegionFile.open(path)
        chunks = list(region.chunks())
    except Exception as error:
        errors.add("region_read_failed", role=role, region=relative, error=repr(error))
        return None, {}
    inv.counts["regionsRead"] += 1
    mapping = {(chunk.x, chunk.z): chunk for chunk in chunks}
    inv.chunks[relative].update(mapping)
    return region, mapping


def inspect_before(world: Path, errors: ErrorLog) -> Inventory:
    inv = Inventory("before")
    paths = region_paths(world)
    inv.regions = set(paths)
    for relative, path in paths.items():
        _, chunks = read_region(path, "before", relative, inv, errors)
        dimension = dimension_for(world, path)
        for local, stored in chunks.items():
            try:
                inspect_chunk(inv, dimension, compound(stored.nbt().root), errors, check_light=False)
            except Exception as error:
                errors.add("chunk_read_failed", role="before", region=relative, localChunk=list(local), error=repr(error))
    return inv


def inspect_pair(original: Path, after: Path, errors: ErrorLog, retained_ids: set[str]) -> tuple[Inventory, Inventory]:
    # Original masonry alone contains millions of legacy palette blocks which
    # became modules. Only surviving IDs need per-position records in memory.
    inventories = Inventory("original", legacy_filter=retained_ids), Inventory("after")
    path_maps = region_paths(original), region_paths(after)
    inventories[0].regions = set(path_maps[0])
    inventories[1].regions = set(path_maps[1])
    for relative in sorted(set(path_maps[0]) | set(path_maps[1])):
        loaded = []
        for world, role, paths, inv in zip((original, after), ("original", "after"), path_maps, inventories):
            if relative not in paths:
                errors.add("region_missing", role=role, region=relative)
                loaded.append({})
            else:
                _, chunks = read_region(paths[relative], role, relative, inv, errors)
                loaded.append(chunks)
        original_chunks, after_chunks = loaded
        if set(original_chunks) != set(after_chunks):
            errors.add("chunk_set_differs_from_original", region=relative,
                       missingAfter=len(set(original_chunks) - set(after_chunks)),
                       extraAfter=len(set(after_chunks) - set(original_chunks)))
        for local in sorted(set(original_chunks) | set(after_chunks)):
            roots = []
            for index, (world, role, chunks, inv) in enumerate(zip((original, after), ("original", "after"), loaded, inventories)):
                stored = chunks.get(local)
                if stored is None:
                    roots.append(None)
                    continue
                try:
                    root = compound(stored.nbt().root)
                    inspect_chunk(inv, dimension_for(world, path_maps[index][relative]), root, errors, check_light=role == "after")
                    roots.append(root)
                except Exception as error:
                    errors.add("chunk_read_failed", role=role, region=relative, localChunk=list(local), error=repr(error))
                    roots.append(None)
            if roots[0] is not None and roots[1] is not None:
                compare_chunk_blocks(relative, roots[0], roots[1], errors)
    return inventories


def compare_chunk_blocks(region: str, original_root: dict, after_root: dict, errors: ErrorLog):
    ocx, ocz = int(original_root["xPos"].value), int(original_root["zPos"].value)
    acx, acz = int(after_root["xPos"].value), int(after_root["zPos"].value)
    if (ocx, ocz) != (acx, acz):
        errors.add("chunk_coordinates_changed", region=region, original=[ocx, ocz], after=[acx, acz])
        return
    original_sections = decoded_sections(original_root)
    after_sections = decoded_sections(after_root)
    for sy, (palette, indices, _) in original_sections.items():
        for palette_index, entry in enumerate(palette):
            state = block_state_key(entry)
            name = str(compound(entry)["Name"].value)
            if not is_foreign_non_air(name) or name == "minecraft:barrier":
                continue
            positions = np.flatnonzero(indices == palette_index)
            for raw_index in positions:
                pos = block_pos(ocx, sy, ocz, int(raw_index))
                actual = state_at(after_sections, pos)
                if actual != state:
                    if equivalent_technical(region,pos,state,actual,errors):continue
                    errors.add("foreign_block_not_restored", region=region, position=list(pos), expected=state, actual=actual)


def compare_region_and_chunk_sets(inventories: list[Inventory], errors: ErrorLog):
    original, before, after = inventories
    if before.regions != after.regions:
        errors.add("region_set_changed_from_before", missingAfter=sorted(before.regions - after.regions)[:20],
                   extraAfter=sorted(after.regions - before.regions)[:20])
    if original.regions != after.regions:
        errors.add("region_set_changed_from_original", missingAfter=sorted(original.regions - after.regions)[:20],
                   extraAfter=sorted(after.regions - original.regions)[:20])
    for relative in sorted(before.regions | after.regions):
        if before.chunks.get(relative, set()) != after.chunks.get(relative, set()):
            errors.add("chunk_set_changed_from_before", region=relative,
                       before=len(before.chunks.get(relative, set())), after=len(after.chunks.get(relative, set())))


def compare_legacy(original: Inventory, before: Inventory, after: Inventory, errors: ErrorLog):
    for ident in sorted(set(before.legacy_counts) | set(after.legacy_counts)):
        if before.legacy_counts[ident] != after.legacy_counts[ident]:
            errors.add("visible_legacy_count_changed", id=ident,
                       before=before.legacy_counts[ident], after=after.legacy_counts[ident])
    for key, entry in after.legacy_entries.items():
        expected = original.legacy_entries.get(key)
        if expected is None:
            errors.add("legacy_root_not_at_original_position", dimension=key[0], position=list(key[1:]), actual=block_state_key(entry))
        elif block_state_key(expected) != block_state_key(entry):
            errors.add("legacy_root_state_differs_from_original", dimension=key[0], position=list(key[1:]),
                       expected=block_state_key(expected), actual=block_state_key(entry))


def multiset_tags_equal(left: list[Tag], right: list[Tag]) -> bool:
    remaining = list(right)
    for value in left:
        try:
            index = remaining.index(value)
        except ValueError:
            return False
        remaining.pop(index)
    return not remaining


def compare_nonhelper_entities(original: Inventory, after: Inventory, errors: ErrorLog):
    keys = set(original.nonhelper_entities) | set(after.nonhelper_entities)
    for key in sorted(keys):
        expected = original.nonhelper_entities.get(key, [])
        actual = after.nonhelper_entities.get(key, [])
        if not multiset_tags_equal(expected, actual):
            errors.add("nonhelper_block_entity_changed", dimension=key[0], position=list(key[1:]),
                       expectedCount=len(expected), actualCount=len(actual),
                       expectedIds=[str(compound(tag).get("id", Tag(TAG_STRING, "")).value) for tag in expected],
                       actualIds=[str(compound(tag).get("id", Tag(TAG_STRING, "")).value) for tag in actual])


def unpack_block_pos(value: int) -> tuple[int, int, int]:
    value &= (1 << 64) - 1
    x = value >> 38
    z = (value >> 12) & 0x3FFFFFF
    y = value & 0xFFF
    if x >= 1 << 25:
        x -= 1 << 26
    if z >= 1 << 25:
        z -= 1 << 26
    if y >= 1 << 11:
        y -= 1 << 12
    return x, y, z


def verify_helpers(after: Inventory, definitions: dict, geometry: dict, errors: ErrorLog):
    profiles = geometry.get("profiles", {})
    for key in sorted(after.helper_blocks):
        all_entities = after.all_entities.get(key, [])
        helper_entities = after.helper_entities.get(key, [])
        if len(all_entities) != 1 or len(helper_entities) != 1:
            errors.add("helper_block_entity_cardinality", dimension=key[0], position=list(key[1:]),
                       allBlockEntities=len(all_entities), helperBlockEntities=len(helper_entities))
            continue
        data = compound(helper_entities[0])
        if "Root" not in data or "Owner" not in data:
            errors.add("helper_binding_missing", dimension=key[0], position=list(key[1:]))
            continue
        root_pos = unpack_block_pos(int(data["Root"].value))
        root_key = (key[0], *root_pos)
        root_entry = after.legacy_entries.get(root_key)
        owner = str(data["Owner"].value)
        if root_entry is None:
            errors.add("helper_root_missing", dimension=key[0], position=list(key[1:]), root=list(root_pos), owner=owner)
            continue
        root_name = str(compound(root_entry)["Name"].value)
        if root_name != owner:
            errors.add("helper_owner_mismatch", dimension=key[0], position=list(key[1:]), root=list(root_pos), owner=owner, rootState=block_state_key(root_entry))
            continue
        ident = root_name[len(NS):]
        definition = definitions.get(ident)
        block_geometry = geometry.get("blocks", {}).get(ident)
        if definition is None or block_geometry is None:
            errors.add("helper_root_geometry_missing", dimension=key[0], position=list(key[1:]), root=list(root_pos), id=ident)
            continue
        values = {**definition.get("default", {}), **properties(root_entry)}
        root_state_key = state_key(values)
        geometry_state = block_geometry.get("states", {}).get(root_state_key)
        if geometry_state is None:
            errors.add("helper_root_state_geometry_missing", dimension=key[0], position=list(key[1:]), root=list(root_pos), id=ident, state=root_state_key)
            continue
        if "ref" in geometry_state:
            geometry_state = profiles.get(geometry_state["ref"])
        if geometry_state is None:
            errors.add("helper_profile_missing", dimension=key[0], position=list(key[1:]), root=list(root_pos), id=ident)
            continue
        offset = tuple(key[index + 1] - root_pos[index] for index in range(3))
        offset_key = ",".join(str(value) for value in offset)
        if offset_key not in geometry_state.get("cells", {}):
            errors.add("helper_offset_not_in_geometry", dimension=key[0], position=list(key[1:]), root=list(root_pos), id=ident, state=root_state_key, offset=list(offset))
    for key, entities in after.helper_entities.items():
        if key not in after.helper_blocks:
            errors.add("helper_entity_without_block", dimension=key[0], position=list(key[1:]), count=len(entities))


def external_hashes(world: Path) -> dict[str, str]:
    result = {}
    for path in world.rglob("*"):
        if not path.is_file():
            continue
        relative = path.relative_to(world)
        if relative.as_posix() == "session.lock" or "region" in relative.parts:
            continue
        result[relative.as_posix()] = hashlib.sha256(path.read_bytes()).hexdigest()
    return result


def compare_external(before: Path, after: Path, errors: ErrorLog) -> int:
    old = external_hashes(before)
    new = external_hashes(after)
    for relative in sorted(set(old) | set(new)):
        if relative not in new:
            errors.add("external_file_missing", path=relative)
        elif relative not in old:
            errors.add("external_file_added", path=relative)
        elif old[relative] != new[relative]:
            errors.add("external_file_changed", path=relative)
    return len(old)


def validate_world_path(path: Path, label: str):
    if not path.is_dir() or not (path / "level.dat").is_file():
        raise ValueError(f"{label} is not a Minecraft world: {path}")


def verify(original: Path, before: Path, after: Path, report_path: Path, max_errors: int = 200) -> dict:
    original, before, after, report_path = (path.resolve() for path in (original, before, after, report_path))
    for label, path in (("original", original), ("before", before), ("after", after)):
        validate_world_path(path, label)
    if report_path == ROOT or ROOT in report_path.parents:
        raise ValueError("report must be outside the Git working tree")
    errors = ErrorLog(max_errors)
    load_technical_conversions(errors)
    before_inventory = inspect_before(before, errors)
    original_inventory, after_inventory = inspect_pair(original, after, errors, set(before_inventory.legacy_counts))
    compare_region_and_chunk_sets([original_inventory, before_inventory, after_inventory], errors)
    compare_legacy(original_inventory, before_inventory, after_inventory, errors)
    compare_nonhelper_entities(original_inventory, after_inventory, errors)
    definitions = {row["id"]: row for row in json.loads((RES / "bloodborne_blocks/definitions.json").read_text(encoding="utf-8-sig"))["blocks"]}
    geometry = json.loads((RES / "bloodborne_blocks/geometry.json").read_text(encoding="utf-8-sig"))
    verify_helpers(after_inventory, definitions, geometry, errors)
    external_count = compare_external(before, after, errors)
    if after_inventory.counts["barriers"]:
        errors.add("barriers_remain", count=after_inventory.counts["barriers"])
    counts = {
        role: {
            **dict(sorted(inv.counts.items())),
            "regionFiles": len(inv.regions),
            "chunkEntries": sum(len(value) for value in inv.chunks.values()),
            "visibleLegacyObjects": sum(inv.legacy_counts.values()),
            "visibleLegacyIds": len(inv.legacy_counts),
            "nonhelperBlockEntities": sum(len(value) for value in inv.nonhelper_entities.values()),
            "helperBlockEntities": sum(len(value) for value in inv.helper_entities.values()),
        }
        for role, inv in (("original", original_inventory), ("before", before_inventory), ("after", after_inventory))
    }
    counts["externalFilesCompared"] = external_count
    counts['equivalentTechnicalCells']=dict(errors.equivalent_technical_cells)
    result = {
        "format": "bloodborne-restored-position-verification-v1",
        "readOnly": True,
        "passed": errors.total == 0,
        "worlds": {"original": str(original), "before": str(before), "after": str(after)},
        "counts": counts,
        "legacyCountsBefore": dict(sorted(before_inventory.legacy_counts.items())),
        "legacyCountsAfter": dict(sorted(after_inventory.legacy_counts.items())),
        "errorCount": errors.total,
        "errorCounts": dict(sorted(errors.counts.items())),
        "errorExamplesCappedAt": max_errors,
        "errors": errors.examples,
        "preexistingDifferenceCount": len(errors.preexisting_differences),
        "preexistingDifferences": errors.preexisting_differences,
    }
    report_path.parent.mkdir(parents=True, exist_ok=True)
    report_path.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"passed": result["passed"], "counts": counts, "errorCount": errors.total,
                      "report": str(report_path)}, ensure_ascii=False))
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("original", type=Path)
    parser.add_argument("before", type=Path)
    parser.add_argument("after", type=Path)
    parser.add_argument("report", type=Path)
    parser.add_argument("--max-error-examples", type=int, default=200)
    args = parser.parse_args()
    if args.max_error_examples < 1:
        parser.error("--max-error-examples must be positive")
    result = verify(args.original, args.before, args.after, args.report, args.max_error_examples)
    raise SystemExit(0 if result["passed"] else 1)


if __name__ == "__main__":
    main()
