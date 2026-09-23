#!/usr/bin/env python3
"""Offline, fail-closed inverse conversion of Bloodborne logical objects.

This tool deliberately depends only on :mod:`world_io`, the standard library and
numpy.  In particular it never imports the palette/model generators (those load
the complete asset graph and, on some machines, a vanilla jar).  It accepts an
unmodified world directory or zip and always creates a *new* output directory.
"""
from __future__ import annotations

import argparse
import copy
import hashlib
import json
import os
import shutil
import tempfile
import sys
import zipfile
from collections import defaultdict
from dataclasses import dataclass, field
from pathlib import Path, PurePosixPath
from typing import Any, Callable, Iterable

import numpy as np

from world_io import (TAG_BYTE, TAG_COMPOUND, TAG_INT, TAG_LIST, TAG_LONG,
                      TAG_LONG_ARRAY, TAG_STRING, NbtFile, RegionFile, Tag,
                      block_state_key, compound, invalidate_chunk_lighting,
                      pack_palette_indices, read_nbt, section_blocks)

NS = "bloodborne_blocks:"
AIR_NAME = "minecraft:air"
AIR_NAMES = {"minecraft:air", "minecraft:cave_air", "minecraft:void_air"}
PART = NS + "architecture_part"
TOOLS = Path(__file__).resolve().parent
DEFAULT_RESOURCES = TOOLS.parent / "src/main/resources/bloodborne_blocks/logical"
VANILLA_DIMENSION_HEIGHTS = {
    "minecraft:overworld": (-64, 320),
    "minecraft:the_nether": (0, 256),
    "minecraft:the_end": (0, 256),
}


def full_id(value: str) -> str:
    return value if ":" in value else NS + value


def short_id(value: str) -> str:
    return value.split(":", 1)[-1]


def state_tag(name: str, properties: dict[str, str]) -> Tag:
    data: dict[str, Tag] = {"Name": Tag(TAG_STRING, full_id(name))}
    if properties:
        data["Properties"] = Tag(TAG_COMPOUND, {k: Tag(TAG_STRING, str(v)) for k, v in sorted(properties.items())})
    return Tag(TAG_COMPOUND, data)


def state_of(entry: Tag, defaults: dict[str, dict[str, str]]) -> tuple[str, tuple[tuple[str, str], ...]]:
    data = compound(entry)
    name = str(data["Name"].value)
    values = dict(defaults.get(name, {}))
    props = data.get("Properties")
    if props is not None:
        values.update({key: str(value.value) for key, value in compound(props).items()})
    return name, tuple(sorted(values.items()))


def make_state(spec: dict[str, Any], defaults: dict[str, dict[str, str]]) -> tuple[str, tuple[tuple[str, str], ...]]:
    name = full_id(str(spec["id"]))
    values = dict(defaults.get(name, {}))
    values.update({str(k): str(v) for k, v in spec.get("properties", {}).items()})
    return name, tuple(sorted(values.items()))


def as_tag_state(value: tuple[str, tuple[tuple[str, str], ...]]) -> Tag:
    return state_tag(value[0], dict(value[1]))


def block_pos_long(x: int, y: int, z: int) -> int:
    """Minecraft BlockPos.asLong(), returned as a signed NBT long."""
    value = ((x & 0x3FFFFFF) << 38) | ((z & 0x3FFFFFF) << 12) | (y & 0xFFF)
    return value - (1 << 64) if value >= (1 << 63) else value


def unpack_pos_long(value: int) -> tuple[int, int, int]:
    value &= (1 << 64) - 1
    x, y, z = value >> 38, value & 0xFFF, (value >> 12) & 0x3FFFFFF
    return (x - (1 << 26) if x >= (1 << 25) else x,
            y - (1 << 12) if y >= (1 << 11) else y,
            z - (1 << 26) if z >= (1 << 25) else z)


def region_dimension(world: Path, path: Path) -> str:
    rel = path.relative_to(world).as_posix()
    if rel.startswith("region/"):
        return "minecraft:overworld"
    if rel.startswith("DIM-1/"):
        return "minecraft:the_nether"
    if rel.startswith("DIM1/"):
        return "minecraft:the_end"
    marker = "dimensions/"
    if marker in rel and "/region/" in rel:
        pieces = rel.split(marker, 1)[1].split("/region/", 1)[0].split("/")
        if len(pieces) >= 2:
            return pieces[0] + ":" + "/".join(pieces[1:])
    return rel.rsplit("/region/", 1)[0]


def list_region_files(world: Path) -> list[Path]:
    return sorted(world.glob("**/region/r.*.*.mca"))


def hash_tree(root: Path) -> dict[str, str]:
    return {p.relative_to(root).as_posix(): hashlib.sha256(p.read_bytes()).hexdigest()
            for p in sorted(root.rglob("*")) if p.is_file()}


def safe_extract(source: Path, staging: Path) -> Path:
    """Extract a world zip without accepting zip-slip paths or symlinks."""
    with zipfile.ZipFile(source) as archive:
        roots: set[str] = set()
        for info in archive.infolist():
            name = PurePosixPath(info.filename)
            if name.is_absolute() or ".." in name.parts or not name.parts:
                raise ValueError(f"unsafe zip member: {info.filename!r}")
            if info.external_attr >> 16 & 0o170000 == 0o120000:
                raise ValueError(f"symlink zip member is not accepted: {info.filename!r}")
            roots.add(name.parts[0])
        archive.extractall(staging)
    # Archives normally contain one world directory.  A flat archive is also
    # useful in tests, provided it contains level.dat.
    if (staging / "level.dat").is_file():
        return staging
    candidates = [p for p in staging.iterdir() if p.is_dir() and (p / "level.dat").is_file()]
    if len(candidates) != 1:
        raise ValueError("zip must contain exactly one world directory (or a flat world)")
    return candidates[0]


def copy_source(source: Path, staging: Path) -> tuple[Path, dict[str, str], str]:
    source = source.resolve()
    if source.is_dir():
        if not (source / "level.dat").is_file():
            raise ValueError("source directory is not a Java world (missing level.dat)")
        copied = staging / source.name
        shutil.copytree(source, copied)
        return copied, hash_tree(source), "directory"
    if source.is_file() and zipfile.is_zipfile(source):
        copied = safe_extract(source, staging)
        return copied, {"archive": hashlib.sha256(source.read_bytes()).hexdigest()}, "zip"
    raise ValueError("source must be a Java world directory or ZIP archive")


def load_defaults(resources: Path) -> dict[str, dict[str, str]]:
    """Read only definition JSON; no asset or generated-code imports."""
    result: dict[str, dict[str, str]] = {}
    for path in (resources.parent / "definitions.json", resources / "definitions.json"):
        if not path.is_file():
            continue
        data = json.loads(path.read_text(encoding="utf-8"))
        for block in data.get("blocks", []):
            ident = full_id(str(block["id"]))
            result[ident] = {str(k): str(v) for k, v in block.get("default", {}).items()}
    return result


def load_kinds(resources: Path) -> dict[str, str]:
    result: dict[str, str] = {}
    for path in (resources.parent / "definitions.json", resources / "definitions.json"):
        if not path.is_file():
            continue
        for block in json.loads(path.read_text(encoding="utf-8")).get("blocks", []):
            if isinstance(block.get("kind"), str):
                result[full_id(str(block["id"]))] = block["kind"]
    return result


def definition_hashes(resources: Path) -> dict[str, str | None]:
    result = {"legacy": hashlib.sha256((resources.parent / "definitions.json").read_bytes()).hexdigest() if (resources.parent / "definitions.json").is_file() else None,
            "logical": hashlib.sha256((resources / "definitions.json").read_bytes()).hexdigest() if (resources / "definitions.json").is_file() else None,
            "legacyGeometry": hashlib.sha256((resources.parent / "geometry.json").read_bytes()).hexdigest() if (resources.parent / "geometry.json").is_file() else None}
    for name in ("contracts-v2.json", "transform-v2.json"):
        if (resources / name).is_file():
            result[name] = hashlib.sha256((resources / name).read_bytes()).hexdigest()
    return result


def parse_geometry(resources: Path, defaults: dict[str, dict[str, str]]) -> dict[tuple[str, tuple[tuple[str, str], ...]], set[tuple[int, int, int]]]:
    raw = json.loads((resources / "geometry.json").read_text(encoding="utf-8"))
    blocks = raw.get("blocks", raw)
    profiles = raw.get("profiles", {})
    result: dict[tuple[str, tuple[tuple[str, str], ...]], set[tuple[int, int, int]]] = {}
    for raw_ident, block in blocks.items():
        ident = full_id(str(raw_ident))
        for key, state in block.get("states", {}).items():
            props = {} if not key else dict(item.split("=", 1) for item in key.split(",") if "=" in item)
            signature = (ident, tuple(sorted({**defaults.get(ident, {}), **props}.items())))
            resolved = profiles.get(state.get("ref"), {}) if isinstance(state, dict) and "ref" in state else state
            cells = resolved.get("cells", {}) if isinstance(resolved, dict) else {}
            parsed: set[tuple[int, int, int]] = set()
            for point in cells:
                try:
                    x, y, z = (int(v) for v in point.split(","))
                except ValueError as exc:
                    raise ValueError(f"invalid geometry cell {ident}/{key}: {point!r}") from exc
                parsed.add((x, y, z))
            if parsed:
                if signature in result and result[signature] != parsed:
                    raise ValueError(f"ambiguous geometry for {block_state_key(as_tag_state(signature))}")
                result[signature] = parsed
    return result


@dataclass(frozen=True)
class Expected:
    offset: tuple[int, int, int]
    state: tuple[str, tuple[tuple[str, str], ...]]
    shape: frozenset[tuple[int, int, int]] = field(default_factory=frozenset)


@dataclass(frozen=True)
class Rule:
    number: int
    source: Expected
    target: tuple[str, tuple[tuple[str, str], ...]]
    root_offset: tuple[int, int, int]
    members: tuple[Expected, ...]
    components: tuple[Expected, ...] | None
    shape: frozenset[tuple[int, int, int]]
    supersedes_targets: frozenset[str] = field(default_factory=frozenset)
    variant_guards: tuple[dict, ...] = ()
    transaction_id: str | None = None
    outputs: tuple["Output", ...] = ()


@dataclass(frozen=True)
class Output:
    """One logical object produced by an explicitly authored split transaction."""
    target: tuple[str, tuple[tuple[str, str], ...]]
    root_offset: tuple[int, int, int]
    shape: frozenset[tuple[int, int, int]]


def vector(value: Any, label: str) -> tuple[int, int, int]:
    if not isinstance(value, list) or len(value) != 3 or not all(isinstance(v, int) for v in value):
        raise ValueError(f"{label} must be three integer offsets")
    return tuple(value)  # type: ignore[return-value]


def parse_rules(resources: Path, source_mode: str = "legacy") -> tuple[list[Rule], dict[str, dict[str, str]]]:
    if source_mode in ("original-v2-poc", "original-v2"):
        from logical_contract_v2 import direct_rules
        return direct_rules(resources,poc_only=source_mode == 'original-v2-poc')
    if source_mode != "legacy":
        raise ValueError("unknown source mode")
    migration = json.loads((resources / "migration.json").read_text(encoding="utf-8"))
    if migration.get("schemaVersion") != 1 or not isinstance(migration.get("rules"), list):
        raise ValueError("logical/migration.json must be schemaVersion 1 with a rules array")
    defaults = load_defaults(resources)
    kinds = load_kinds(resources)
    geometry = parse_geometry(resources, defaults)
    # Only the five opt-in v2 families override target occupation. Historical
    # source geometry stays unchanged, so only owned old helpers are consumed.
    if (resources / "contracts-v2.json").is_file():
        from logical_contract_v2 import load_contracts
        contracts, _ = load_contracts(resources)
        for family in contracts["families"]:
            for key, state in family["states"].items():
                target = make_state({"id": family["id"], "properties": dict(p.split("=", 1) for p in key.split(",") if p)}, defaults)
                geometry[target] = {tuple(c) for c in state["interaction_footprint"]["cells"]}
    legacy_geometry = parse_geometry(resources.parent, defaults) if (resources.parent / "geometry.json").is_file() else {}
    def legacy_shape(state: tuple[str, tuple[tuple[str, str], ...]]) -> frozenset[tuple[int, int, int]]:
        shape = set(legacy_geometry.get(state, ()))
        if kinds.get(state[0]) == "door":
            half = dict(state[1]).get("half")
            if half == "lower":
                shape.discard((0, 1, 0))
            elif half == "upper":
                shape.discard((0, -1, 0))
        return frozenset(shape)
    rules: list[Rule] = []
    for number, raw in enumerate(migration["rules"]):
        if not isinstance(raw, dict) or not isinstance(raw.get("source"), dict) or not isinstance(raw.get("target"), dict):
            raise ValueError(f"rule {number} needs source and target")
        source_state = make_state(raw["source"], defaults)
        source = Expected((0, 0, 0), source_state, legacy_shape(source_state))
        members = tuple(Expected(vector(item.get("offset"), f"rule {number} member offset"), state,
                                 legacy_shape(state))
                        for item in raw.get("members", []) for state in (make_state(item, defaults),))
        if raw.get("components") is None:
            components = None
        elif isinstance(raw["components"], list) and raw["components"]:
            components = tuple(Expected(vector(item.get("offset"), f"rule {number} component offset"), make_state(item, defaults))
                               for item in raw["components"])
        else:
            raise ValueError(f"rule {number} components must be a non-empty list or null")
        target = make_state(raw["target"], defaults)
        raw_supersedes = raw.get("supersedes_targets")
        if raw_supersedes is None:
            supersedes_targets = frozenset()
        else:
            if (not isinstance(raw_supersedes, list) or not raw_supersedes or
                    any(not isinstance(value, str) for value in raw_supersedes)):
                raise ValueError(f"rule {number} supersedes_targets must be a non-empty list of logical target ids")
            supersedes_targets = frozenset(full_id(value) for value in raw_supersedes)
            if len(supersedes_targets) != len(raw_supersedes):
                raise ValueError(f"rule {number} supersedes_targets must be unique")
            if any(not value.startswith(NS + "o_") for value in supersedes_targets):
                raise ValueError(f"rule {number} supersedes_targets must name logical targets")
        shape = geometry.get(target)
        if not shape:
            raise ValueError(f"rule {number} target has no representable geometry")
        # GeometryRuntime always reserves the logical root itself in addition
        # to parsed geometry cells.  Open objects legitimately have no mesh in
        # 0,0,0, so absence from ``cells`` is not a reason to move the root.
        shape = set(shape)
        shape.add((0, 0, 0))
        rules.append(Rule(number, source, target, vector(raw.get("offset"), f"rule {number} offset"),
                          members, components, frozenset(shape), supersedes_targets))
    known_targets = {rule.target[0] for rule in rules}
    for rule in rules:
        if not rule.supersedes_targets <= known_targets:
            raise ValueError(f"rule {rule.number} supersedes_targets names an unknown logical target")
    rules.extend(parse_old_logical_c654_rules(resources, defaults, geometry, len(rules)))
    return rules, defaults


def parse_old_logical_c654_rules(resources: Path, defaults: dict[str, dict[str, str]],
                                 geometry: dict[tuple[str, tuple[tuple[str, str], ...]], set[tuple[int, int, int]]],
                                 start: int) -> list[Rule]:
    """Compile the explicit C654 compatibility table, never a generic logical alias.

    Every generated row carries its frozen old helper footprint. Consequently
    only helpers demonstrably owned by the exact old root can be removed by
    the normal candidate preflight; the converter never depends on docs.
    """
    path = resources / "old-logical-migrations.json"
    if not path.is_file():
        return []
    raw = json.loads(path.read_text(encoding="utf-8"))
    if raw.get("schemaVersion") != 1 or not isinstance(raw.get("rules"), list):
        raise ValueError("old-logical-migrations.json must be schemaVersion 1 with rules")
    result: list[Rule] = []
    seen: set[str] = set()
    for index, item in enumerate(raw["rules"]):
        if not isinstance(item, dict) or item.get("kind") != "c654_to_a" or set(item) != {"kind", "source", "target", "root_offset", "source_shape"}:
            raise ValueError(f"old logical rule {index} must be an explicit c654_to_a mapping")
        source, target = item["source"], item["target"]
        if not isinstance(source, dict) or not isinstance(target, dict) or source.get("id") != "o_c654" or target.get("id") != "o_c654_a":
            raise ValueError(f"old logical rule {index} has unsupported IDs")
        source_props, target_props = source.get("properties"), target.get("properties")
        if not isinstance(source_props, dict) or not isinstance(target_props, dict):
            raise ValueError(f"old logical rule {index} needs state properties")
        source_key = ",".join(f"{key}={value}" for key, value in sorted(source_props.items()))
        if "visual" in source_props or set(source_props) != {"facing", "variant"} or source_key in seen:
            raise ValueError(f"old logical rule {index} is duplicate or has an invalid old C654 state")
        seen.add(source_key)
        if target_props.get("facing") != source_props.get("facing") or target_props.get("variant") != source_props.get("variant") or target_props.get("visual") != "base" or set(target_props) != {"facing", "variant", "visual"}:
            raise ValueError(f"old logical rule {index} must preserve C654 facing/variant and target visual=base")
        if vector(item["root_offset"], f"old logical rule {index} root_offset") != (0, 0, 0):
            raise ValueError(f"old logical rule {index} must preserve the old C654 root")
        cells = item["source_shape"]
        if not isinstance(cells, list) or not cells or len(cells) > 512:
            raise ValueError(f"old logical rule {index} has invalid frozen source_shape")
        source_shape = frozenset(vector(cell, f"old logical rule {index} source_shape") for cell in cells)
        if (0, 0, 0) not in source_shape or len(source_shape) != len(cells):
            raise ValueError(f"old logical rule {index} source_shape must be unique and include root")
        source_state, target_state = make_state(source, defaults), make_state(target, defaults)
        shape = geometry.get(target_state)
        if not shape:
            raise ValueError(f"old logical rule {index} target has no current geometry")
        target_shape = frozenset(set(shape) | {(0, 0, 0)})
        result.append(Rule(start + len(result), Expected((0, 0, 0), source_state, source_shape), target_state,
                           (0, 0, 0), (), None, target_shape))
    if len(seen) != 8:
        raise ValueError("old logical C654 table must map all four facings and two variants exactly once")
    return result


@dataclass
class Chunk:
    dimension: str
    x: int
    z: int
    region_path: Path
    region: RegionFile
    stored_x: int
    stored_z: int
    timestamp: int
    nbt: NbtFile
    changed: bool = False
    sections: dict[int, Tag] = field(default_factory=dict)
    loaded: dict[int, tuple[dict[str, int], list[Tag], np.ndarray]] = field(default_factory=dict)

    def root(self) -> dict[str, Tag]:
        return compound(self.nbt.root)

    def _section(self, sy: int) -> Tag:
        if not self.sections:
            items = self.root().get("sections", Tag(TAG_LIST, [], TAG_COMPOUND)).value
            self.sections = {int(compound(value)["Y"].value): value for value in items}
        section = self.sections.get(sy)
        if section is None:
            if not -128 <= sy <= 127:
                raise ValueError(f"section Y {sy} cannot be represented as TAG_Byte")
            section = Tag(TAG_COMPOUND, {"Y": Tag(TAG_BYTE, sy), "block_states": Tag(TAG_COMPOUND, {"palette": Tag(TAG_LIST, [state_tag(AIR_NAME, {})], TAG_COMPOUND)})})
            self.root().setdefault("sections", Tag(TAG_LIST, [], TAG_COMPOUND)).value.append(section)
            self.sections[sy] = section
        return section

    def _loaded(self, sy: int) -> tuple[dict[str, int], list[Tag], np.ndarray]:
        current = self.loaded.get(sy)
        if current is not None:
            return current
        section = self._section(sy)
        states = compound(section).get("block_states")
        if states is None:
            states = Tag(TAG_COMPOUND, {"palette": Tag(TAG_LIST, [state_tag(AIR_NAME, {})], TAG_COMPOUND)})
            compound(section)["block_states"] = states
        values = section_blocks(section)
        if values is None:
            palette, indices = [state_tag(AIR_NAME, {})], [0] * 4096
        else:
            palette, indices = values
        copied = list(palette)
        result = ({block_state_key(item): i for i, item in enumerate(copied)}, copied, np.asarray(indices, dtype=np.int32))
        self.loaded[sy] = result
        return result

    @staticmethod
    def _index(x: int, y: int, z: int) -> int:
        return (y & 15) * 256 + (z & 15) * 16 + (x & 15)

    def get(self, x: int, y: int, z: int, defaults: dict[str, dict[str, str]]) -> tuple[str, tuple[tuple[str, str], ...]]:
        if not self.sections:
            items = self.root().get("sections", Tag(TAG_LIST, [], TAG_COMPOUND)).value
            self.sections = {int(compound(value)["Y"].value): value for value in items}
        section = self.sections.get(y // 16)
        # Reads must not manufacture an all-air section.  A later write can
        # create it through _loaded(), but a failed candidate must leave NBT
        # structurally untouched even when another write changes this chunk.
        if section is None or compound(section).get("block_states") is None:
            return AIR_NAME, ()
        palmap, palette, values = self._loaded(y // 16)
        return state_of(palette[int(values[self._index(x, y, z)])], defaults)

    def set(self, x: int, y: int, z: int, state: tuple[str, tuple[tuple[str, str], ...]]) -> None:
        palmap, palette, values = self._loaded(y // 16)
        tag = as_tag_state(state)
        key = block_state_key(tag)
        index = palmap.get(key)
        if index is None:
            index = len(palette)
            palmap[key] = index
            palette.append(tag)
        values[self._index(x, y, z)] = index
        self.changed = True

    def entities(self, *, create: bool = False) -> tuple[str, list[Tag]]:
        root = self.root()
        key = "block_entities" if "block_entities" in root or "TileEntities" not in root else "TileEntities"
        if key not in root:
            if create:
                root[key] = Tag(TAG_LIST, [], TAG_COMPOUND)
                return key, root[key].value
            return key, []
        return key, root[key].value

    def finish(self) -> None:
        if not self.changed:
            return
        for sy, (_, palette, values) in self.loaded.items():
            # Compact duplicate/unused palette entries while preserving tag types.
            unique: dict[str, int] = {}
            compact: list[Tag] = []
            remap: list[int] = []
            for entry in palette:
                key = block_state_key(entry)
                if key not in unique:
                    unique[key] = len(compact)
                    compact.append(entry)
                remap.append(unique[key])
            remapped = np.asarray(remap, dtype=np.int32)[values]
            used = sorted(set(int(v) for v in remapped))
            change = {old: new for new, old in enumerate(used)}
            final = np.asarray([change[int(v)] for v in remapped], dtype=np.int32)
            states = compound(self._section(sy))["block_states"].value
            states["palette"] = Tag(TAG_LIST, [compact[i] for i in used], TAG_COMPOUND)
            if len(used) == 1:
                states.pop("data", None)
            else:
                states["data"] = Tag(TAG_LONG_ARRAY, pack_palette_indices(final.tolist(), len(used)))
        root = self.root()
        invalidate_chunk_lighting(root)
        root.pop("Heightmaps", None)


def _height_from_definition(definition: dict[str, Tag] | dict[str, Any]) -> tuple[int, int] | None:
    """Return the half-open build range only from an explicit dimension type."""
    min_y, height = definition.get("min_y"), definition.get("height")
    min_y = min_y.value if isinstance(min_y, Tag) else min_y
    height = height.value if isinstance(height, Tag) else height
    if type(min_y) is not int or type(height) is not int or height <= 0:
        return None
    return min_y, min_y + height


def _datapack_dimension_height(root: Path, type_id: str) -> tuple[int, int] | None:
    if ":" not in type_id:
        return None
    namespace, path = type_id.split(":", 1)
    if not namespace or not path or any(part in ("", ".", "..") for part in path.split("/")):
        return None
    relative = PurePosixPath("data") / namespace / "dimension_type" / (path + ".json")
    matches: list[dict[str, Any]] = []
    datapacks = root / "datapacks"
    if datapacks.is_dir():
        for pack in datapacks.iterdir():
            if pack.is_dir():
                candidate = pack.joinpath(*relative.parts)
                if candidate.is_file():
                    try:
                        matches.append(json.loads(candidate.read_text(encoding="utf-8")))
                    except (OSError, UnicodeDecodeError, json.JSONDecodeError):
                        return None
            elif pack.suffix.lower() == ".zip":
                try:
                    with zipfile.ZipFile(pack) as archive:
                        if relative.as_posix() in archive.namelist():
                            matches.append(json.loads(archive.read(relative.as_posix()).decode("utf-8")))
                except (OSError, zipfile.BadZipFile, UnicodeDecodeError, json.JSONDecodeError):
                    return None
    # Multiple packs defining the same type are an ambiguous effective
    # registry, even when their JSON happens to agree; do not guess precedence.
    return _height_from_definition(matches[0]) if len(matches) == 1 else None


def dimension_build_height(root: Path, dimension: str) -> tuple[int, int]:
    """Resolve a dimension's build range; unknown custom dimensions are unsafe."""
    if dimension in VANILLA_DIMENSION_HEIGHTS:
        return VANILLA_DIMENSION_HEIGHTS[dimension]
    level = root / "level.dat"
    if not level.is_file():
        raise ValueError(f"unknown dimension build height: {dimension}")
    try:
        data = compound(compound(read_nbt(level).root)["Data"])
        settings = compound(data["WorldGenSettings"])
        entry = compound(compound(settings["dimensions"])[dimension])
    except (KeyError, ValueError, OSError):
        raise ValueError(f"unknown dimension build height: {dimension}") from None
    type_tag = entry.get("type")
    if type_tag is None:
        raise ValueError(f"unknown dimension build height: {dimension}")
    if type_tag.type == TAG_COMPOUND:
        height = _height_from_definition(compound(type_tag))
    elif type_tag.type == TAG_STRING:
        height = VANILLA_DIMENSION_HEIGHTS.get(str(type_tag.value)) or _datapack_dimension_height(root, str(type_tag.value))
    else:
        height = None
    if height is None:
        raise ValueError(f"unknown dimension build height: {dimension}")
    return height


class World:
    def __init__(self, root: Path, defaults: dict[str, dict[str, str]]):
        self.root, self.defaults = root, defaults
        self.chunks: dict[tuple[str, int, int], Chunk] = {}
        self.by_region: dict[Path, RegionFile] = {}
        self._dimension_heights: dict[str, tuple[int, int]] = {}
        for path in list_region_files(root):
            region = RegionFile.open(path)
            self.by_region[path] = region
            dimension = region_dimension(root, path)
            for stored in region.chunks():
                nbt = stored.nbt()
                data = compound(nbt.root)
                if "Sections" in data:
                    raise ValueError(f"legacy uppercase Sections in chunk {path}; explicit world-format migration is required")
                x = int(data.get("xPos", Tag(TAG_INT, 0)).value)
                z = int(data.get("zPos", Tag(TAG_INT, 0)).value)
                key = (dimension, x, z)
                if key in self.chunks:
                    raise ValueError(f"duplicate chunk {key}")
                self.chunks[key] = Chunk(dimension, x, z, path, region, stored.x, stored.z, stored.timestamp, nbt)

    def build_height(self, dimension: str) -> tuple[int, int]:
        if dimension not in self._dimension_heights:
            self._dimension_heights[dimension] = dimension_build_height(self.root, dimension)
        return self._dimension_heights[dimension]

    def chunk(self, dim: str, x: int, z: int) -> Chunk | None:
        return self.chunks.get((dim, x // 16, z // 16))

    def get(self, dim: str, pos: tuple[int, int, int]) -> tuple[str, tuple[tuple[str, str], ...]] | None:
        chunk = self.chunk(dim, pos[0], pos[2])
        return None if chunk is None else chunk.get(*pos, self.defaults)

    def set(self, dim: str, pos: tuple[int, int, int], state: tuple[str, tuple[tuple[str, str], ...]]) -> None:
        chunk = self.chunk(dim, pos[0], pos[2])
        if chunk is None:
            raise ValueError(f"missing destination chunk {dim} {pos}")
        chunk.set(*pos, state)

    def block_entities(self) -> dict[tuple[str, int, int, int], Tag]:
        result: dict[tuple[str, int, int, int], Tag] = {}
        for chunk in self.chunks.values():
            _, entities = chunk.entities()
            for entry in entities:
                data = compound(entry)
                if all(key in data for key in ("x", "y", "z")):
                    point = (chunk.dimension, int(data["x"].value), int(data["y"].value), int(data["z"].value))
                    if point in result:
                        raise ValueError(f"duplicate block entity at {point}")
                    result[point] = entry
        return result

    def ticks_at(self, dim: str, positions: set[tuple[int, int, int]]) -> bool:
        for x, _, z in positions:
            chunk = self.chunk(dim, x, z)
            if chunk is None:
                return True
            for key in ("block_ticks", "TileTicks", "fluid_ticks", "LiquidTicks"):
                tag = chunk.root().get(key)
                if tag is None or tag.type != TAG_LIST:
                    continue
                for tick in tag.value:
                    data = compound(tick)
                    if all(field in data for field in ("x", "y", "z")) and (int(data["x"].value), int(data["y"].value), int(data["z"].value)) in positions:
                        return True
        return False

    def remove_entities(self, dim: str, positions: set[tuple[int, int, int]]) -> None:
        for x, _, z in positions:
            chunk = self.chunk(dim, x, z)
            if chunk is None:
                continue
            key, entries = chunk.entities()
            if not entries:
                continue
            retained = [entry for entry in entries if (int(compound(entry).get("x", Tag(TAG_INT, -10**9)).value), int(compound(entry).get("y", Tag(TAG_INT, -10**9)).value), int(compound(entry).get("z", Tag(TAG_INT, -10**9)).value)) not in positions]
            if len(retained) != len(entries):
                chunk.root()[key] = Tag(TAG_LIST, retained, TAG_COMPOUND)
                chunk.changed = True

    def add_helper(self, dim: str, pos: tuple[int, int, int], root: tuple[int, int, int], owner: str) -> None:
        chunk = self.chunk(dim, pos[0], pos[2])
        if chunk is None:
            raise ValueError(f"missing helper chunk {dim} {pos}")
        key, entries = chunk.entities(create=True)
        entries.append(Tag(TAG_COMPOUND, {
            "id": Tag(TAG_STRING, PART), "x": Tag(TAG_INT, pos[0]), "y": Tag(TAG_INT, pos[1]), "z": Tag(TAG_INT, pos[2]),
            "Root": Tag(TAG_LONG, block_pos_long(*root)), "Owner": Tag(TAG_STRING, owner),
        }))
        chunk.root()[key] = Tag(TAG_LIST, entries, TAG_COMPOUND)
        chunk.changed = True

    def save(self) -> None:
        for chunk in self.chunks.values():
            chunk.finish()
        for path, region in self.by_region.items():
            changed = False
            for chunk in self.chunks.values():
                if chunk.region_path == path and chunk.changed:
                    region.set_chunk(chunk.stored_x, chunk.stored_z, chunk.nbt, timestamp=chunk.timestamp)
                    changed = True
            if changed:
                region.save(path)


@dataclass
class Candidate:
    rule: Rule
    mode: str
    dimension: str
    origin: tuple[int, int, int]
    source: set[tuple[int, int, int]]
    target_root: tuple[int, int, int]
    writes: dict[tuple[int, int, int], tuple[str, tuple[tuple[str, str], ...]]]
    output_roots: tuple[tuple[tuple[int, int, int], tuple[str, tuple[tuple[str, str], ...]]], ...] = ()
    stale: set[tuple[int, int, int]] = field(default_factory=set)
    reason: str | None = None

    @property
    def touched(self) -> set[tuple[int, int, int]]:
        return self.source | set(self.writes) | self.stale

    def owner_at(self, point: tuple[int, int, int]) -> tuple[str, tuple[int, int, int]] | None:
        outputs = self.rule.outputs or (Output(self.rule.target, self.rule.root_offset, self.rule.shape),)
        for output in outputs:
            root = add(self.origin, output.root_offset)
            if point in {add(root, offset) for offset in output.shape}:
                return output.target[0], root
        return None


def add(a: tuple[int, int, int], b: tuple[int, int, int]) -> tuple[int, int, int]:
    return a[0] + b[0], a[1] + b[1], a[2] + b[2]


def owned_part(current: tuple[str, tuple[tuple[str, str], ...]] | None, entity: Tag | None, owner_name: str, root_pos: tuple[int, int, int]) -> bool:
    data = compound(entity) if entity is not None else {}
    entity_id, owner, root = data.get("id"), data.get("Owner"), data.get("Root")
    return (current == (PART, ()) and entity_id is not None and entity_id.type == TAG_STRING and entity_id.value == PART and
            owner is not None and owner.type == TAG_STRING and owner.value == owner_name and
            root is not None and root.type == TAG_LONG and root.value == block_pos_long(*root_pos))


def candidates(world: World, rules: list[Rule], progress: Callable[[str], None] | None = None) -> tuple[list[Candidate], dict[tuple[str, tuple[int, int, int]], tuple[str, tuple[tuple[str, str], ...]]], list[dict[str, Any]]]:
    inverse: dict[tuple[str, tuple[tuple[str, str], ...]], list[tuple[Rule, str, tuple[int, int, int]]]] = defaultdict(list)
    for rule in rules:
        inverse[rule.source.state].append((rule, "legacy", rule.source.offset))
        if rule.components:
            for component in rule.components:
                inverse[component.state].append((rule, "v2", component.offset))
    found: dict[tuple[str, tuple[int, int, int]], tuple[str, tuple[tuple[str, str], ...]]] = {}
    unmatched: dict[tuple[str, tuple[str, tuple[tuple[str, str], ...]]], dict[str, Any]] = {}
    result: dict[tuple[int, str, str, tuple[int, int, int]], Candidate] = {}
    if progress:
        progress(f"scan start: chunks={len(world.chunks)}, inverseStates={len(inverse)}")
    for chunk in world.chunks.values():
        for sy, section in ((int(compound(s)["Y"].value), s) for s in chunk.root().get("sections", Tag(TAG_LIST, [], TAG_COMPOUND)).value):
            values = section_blocks(section)
            if values is None:
                continue
            palette, indices = values
            states = [state_of(value, world.defaults) for value in palette]
            index_array = np.asarray(indices)
            relevant = [i for i, value in enumerate(states) if value in inverse]
            for index in np.flatnonzero(np.isin(index_array, relevant)):
                i = int(index)
                pos = (chunk.x * 16 + (i & 15), sy * 16 + (i >> 8), chunk.z * 16 + ((i >> 4) & 15))
                state = states[indices[i]]
                found[(chunk.dimension, pos)] = state
                for rule, mode, offset in inverse.get(state, ()):
                    origin = (pos[0] - offset[0], pos[1] - offset[1], pos[2] - offset[2])
                    key = (rule.number, mode, chunk.dimension, origin)
                    result.setdefault(key, Candidate(rule, mode, chunk.dimension, origin, set(), (0, 0, 0), {}))
            palette_counts = np.bincount(index_array, minlength=len(states))
            for palette_index, state in enumerate(states):
                if not state[0].startswith(NS + "m_") or state in inverse:
                    continue
                count = int(palette_counts[palette_index])
                if not count:
                    continue
                key = (chunk.dimension, state)
                group = unmatched.setdefault(key, {"dimension": chunk.dimension, "state": block_state_key(as_tag_state(state)), "count": 0, "samples": []})
                group["count"] += count
                if len(group["samples"]) < 4:
                    for index in np.flatnonzero(index_array == palette_index)[:4 - len(group["samples"])]:
                        i = int(index)
                        group["samples"].append([chunk.x * 16 + (i & 15), sy * 16 + (i >> 8), chunk.z * 16 + ((i >> 4) & 15)])
    if progress:
        progress(f"scan complete: inverseBlocks={len(found)}, candidates={len(result)}, unmatchedModuleGroups={len(unmatched)}")
    entities = world.block_entities()
    owned_parts: dict[tuple[str, str, int], set[tuple[int, int, int]]] = defaultdict(set)
    for (dim, x, y, z), entity in entities.items():
        data = compound(entity)
        owner, root = data.get("Owner"), data.get("Root")
        if owner is not None and root is not None and owner.type == TAG_STRING and root.type == TAG_LONG:
            if owned_part(world.get(dim, (x, y, z)), entity, str(owner.value), unpack_pos_long(int(root.value))):
                owned_parts[(dim, str(owner.value), int(root.value))].add((x, y, z))
    for item in result.values():
        pieces = ((item.rule.source,) + item.rule.members) if item.mode == "legacy" else item.rule.components or ()
        actual: set[tuple[int, int, int]] = set()
        for piece in pieces:
            point = add(item.origin, piece.offset)
            if world.get(item.dimension, point) != piece.state:
                item.reason = "partial_or_wrong_group"
                break
            actual.add(point)
        if item.reason:
            continue
        if len(actual) != len(pieces):
            item.reason = "duplicate_component_offsets"
            continue
        if item.rule.variant_guards:
            from source_variant_rng import guards_match
            if not guards_match(item.rule.variant_guards,item.origin):
                item.reason = 'different_source_weighted_visual'
                continue
        item.source = actual
        outputs = item.rule.outputs or (Output(item.rule.target, item.rule.root_offset, item.rule.shape),)
        item.target_root = add(item.origin, outputs[0].root_offset)
        item.output_roots = tuple((add(item.origin, output.root_offset), output.target) for output in outputs)
        item.writes = {}
        for output in outputs:
            root = add(item.origin, output.root_offset)
            for offset in output.shape:
                point = add(root, offset)
                if point in item.writes:
                    item.reason = "overlapping_transaction_outputs"
                    break
                item.writes[point] = (PART, ()) if offset != (0, 0, 0) else output.target
            if item.reason:
                break
        if item.reason:
            continue
        if len(item.writes) != sum(len(output.shape) for output in outputs):
            item.reason = "duplicate_target_geometry"
            continue
        try:
            min_y, max_y = world.build_height(item.dimension)
        except ValueError:
            item.reason = "unknown_dimension_build_height"
            continue
        if any(point[1] < min_y or point[1] >= max_y for point in item.touched):
            item.reason = "target_outside_dimension_build_height"
            continue
        source_owned: set[tuple[int, int, int]] = set()
        if item.mode == "legacy":
            for piece in pieces:
                piece_root = add(item.origin, piece.offset)
                for point in owned_parts.get((item.dimension, piece.state[0], block_pos_long(*piece_root)), ()):
                    helper_offset = (point[0] - piece_root[0], point[1] - piece_root[1], point[2] - piece_root[2])
                    if helper_offset != (0, 0, 0) and helper_offset in piece.shape:
                        source_owned.add(point)
            item.stale.update(source_owned - set(item.writes))
        # Existing helper cells may be replaced only if they explicitly already
        # belong to exactly this logical root/owner.  All other BEs are foreign.
        for point in item.touched:
            if world.chunk(item.dimension, point[0], point[2]) is None:
                item.reason = "missing_destination_chunk"
                break
        if item.reason:
            continue
        for point in item.touched:
            entity = entities.get((item.dimension, *point))
            current = world.get(item.dimension, point)
            owner = item.owner_at(point)
            owned = (owner is not None and owned_part(current, entity, owner[0], owner[1])) or point in source_owned
            if point in item.writes and point not in item.source and current is not None and current[0] not in AIR_NAMES and not owned:
                item.reason = "target_would_overwrite_foreign_block"
                break
            if entity is not None and not owned:
                item.reason = "foreign_block_entity"
                break
        if item.reason:
            continue
        # Remove only provably stale parts: their Owner and packed Root match.
        for root, target in item.output_roots or ((item.target_root, item.rule.target),):
            for point in owned_parts.get((item.dimension, target[0], block_pos_long(*root)), ()):
                if point not in item.writes:
                    if world.get(item.dimension, point) != (PART, ()):
                        item.reason = "owned_helper_state_mismatch"
                        break
                    item.stale.add(point)
            if item.reason:
                break
        if item.reason:
            continue
        if world.ticks_at(item.dimension, item.touched):
            item.reason = "scheduled_tick_at_changed_position"
    return list(result.values()), found, list(unmatched.values())


def unresolved_components(items: Iterable[Candidate], found: dict[tuple[str, tuple[int, int, int]], tuple[str, tuple[tuple[str, str], ...]]], rules: Iterable[Rule]) -> list[dict[str, Any]]:
    component_states = {component.state for rule in rules for component in rule.components or ()}
    consumed = {(candidate.dimension, point) for candidate in items if candidate.reason is None for point in candidate.source}
    return [{"dimension": dim, "position": list(point), "state": block_state_key(as_tag_state(state)), "reason": "unresolved_v2_component"}
            for (dim, point), state in found.items() if state in component_states and (dim, point) not in consumed]


def supersedes(large: Candidate, small: Candidate) -> bool:
    """Return true only for an explicitly declared, fully-contained fallback."""
    return (large.dimension == small.dimension and
            small.rule.target[0] in large.rule.supersedes_targets and
            small.rule.target != large.rule.target and
            small.source < large.source and small.touched <= large.touched)


def reject_overlaps(items: list[Candidate], stats: dict[str, int] | None = None) -> None:
    # Different legacy aliases can describe the same already-converted v2
    # object.  Keep one only when every affected block and resulting write is
    # exactly identical; anything else remains an ambiguous, fail-closed
    # overlap.
    unique: list[Candidate] = []
    effects: set[tuple[Any, ...]] = set()
    for item in items:
        if item.reason is not None:
            unique.append(item)
            continue
        effect = (item.dimension, frozenset(item.source), frozenset(item.touched), frozenset(item.writes.items()))
        if effect not in effects:
            effects.add(effect)
            unique.append(item)
    items[:] = unique
    # A declared assembly can eclipse only a strictly-contained legacy target.
    # Every other shared write/source remains a symmetric fail-closed conflict.
    eligible = [item for item in items if item.reason is None]
    users: dict[tuple[str, tuple[int, int, int]], list[int]] = defaultdict(list)
    for index, item in enumerate(eligible):
        for point in item.touched:
            users[(item.dimension, point)].append(index)
    pairs: set[tuple[int, int]] = set()
    rejected: set[int] = set()
    dominance: set[tuple[int, int]] = set()
    for owners in users.values():
        for left_index, left in enumerate(owners):
            for right in owners[left_index + 1:]:
                pair = (left, right) if left < right else (right, left)
                if pair in pairs:
                    continue
                pairs.add(pair)
                left_item, right_item = eligible[pair[0]], eligible[pair[1]]
                if supersedes(left_item, right_item):
                    dominance.add(pair)
                elif supersedes(right_item, left_item):
                    dominance.add((pair[1], pair[0]))
                else:
                    rejected.update(pair)
    if stats is not None:
        stats.update({"eligible": len(eligible), "touchedPositions": len(users), "pairChecks": len(pairs)})
    for index in rejected:
        eligible[index].reason = "ambiguous_overlap_or_double_consumption"
    # Do this only after all ordinary conflicts have rejected their candidates:
    # an obstructed/conflicting assembly never consumes its old fallback.
    for large, small in dominance:
        if eligible[large].reason is None and eligible[small].reason is None:
            eligible[small].reason = "superseded_by_complete_assembly"


def apply(world: World, items: Iterable[Candidate]) -> list[dict[str, Any]]:
    ledger: list[dict[str, Any]] = []
    for item in items:
        if item.reason is not None:
            continue
        old: dict[tuple[int, int, int], tuple[str, tuple[tuple[str, str], ...]] | None] = {point: world.get(item.dimension, point) for point in item.touched}
        for point in item.source | item.stale:
            if point not in item.writes:
                world.set(item.dimension, point, (AIR_NAME, ()))
        for point, state in item.writes.items():
            world.set(item.dimension, point, state)
        world.remove_entities(item.dimension, item.touched)
        helpers = []
        for point, state in item.writes.items():
            if state[0] == PART:
                owner = item.owner_at(point)
                if owner is None:
                    raise ValueError("helper has no transaction output owner")
                world.add_helper(item.dimension, point, owner[1], owner[0])
                helpers.append({"position": list(point), "id": PART, "Root": block_pos_long(*owner[1]), "Owner": owner[0]})
        ledger.append({
            "rule": item.rule.number, "mode": item.mode, "dimension": item.dimension,
            "origin": list(item.origin), "targetRoot": list(item.target_root),
            **({"transaction": item.rule.transaction_id,
                "outputs": [{"targetRoot": list(root), "target": block_state_key(as_tag_state(target))}
                            for root, target in item.output_roots]} if item.rule.transaction_id else {}),
            "source": [list(point) for point in sorted(item.source)],
            "staleRemoved": [list(point) for point in sorted(item.stale)],
            "changes": [{"position": list(point), "before": block_state_key(as_tag_state(old[point])) if old[point] else None,
                         "after": block_state_key(as_tag_state(item.writes.get(point, (AIR_NAME, ()))))} for point in sorted(item.touched)],
            "helpers": helpers,
        })
    return ledger


def default_report_path(output: Path) -> Path:
    return TOOLS.parent / "build/logical-world" / (output.name + ".json")


def is_within(path: Path, parent: Path) -> bool:
    try:
        path.relative_to(parent)
        return True
    except ValueError:
        return False


def validate_paths(source: Path, output: Path, resources: Path, report: Path, report_root: Path) -> None:
    if output == source or (source.is_dir() and is_within(output, source)):
        raise ValueError("output must be a new directory outside the source")
    if resources == source or (source.is_dir() and (is_within(resources, source) or is_within(source, resources))):
        raise ValueError("source and resources must be disjoint")
    if is_within(output, resources) or is_within(resources, output):
        raise ValueError("output and resources must be disjoint")
    if report == source or (source.is_dir() and is_within(report, source)):
        raise ValueError("report must be outside the source")
    if is_within(report, output) or is_within(output, report):
        raise ValueError("report and output must be disjoint")
    if is_within(report, resources):
        raise ValueError("report must be outside logical resources and definitions")
    if report in (resources / "migration.json", resources / "geometry.json", resources / "definitions.json", resources.parent / "definitions.json"):
        raise ValueError("report must not overwrite a logical resource")
    if report == report_root or not is_within(report, report_root):
        raise ValueError(f"report must live below {report_root}")


def write_report(report_path: Path, report: dict[str, Any]) -> None:
    report_path.parent.mkdir(parents=True, exist_ok=True)
    temporary = None
    try:
        with tempfile.NamedTemporaryFile("w", encoding="utf-8", dir=report_path.parent, prefix=report_path.name + ".", suffix=".tmp", delete=False) as handle:
            temporary = Path(handle.name)
            json.dump(report, handle, ensure_ascii=False, indent=2, sort_keys=True)
            handle.write("\n")
        os.replace(temporary, report_path)
    finally:
        if temporary is not None and temporary.exists():
            temporary.unlink()


def convert(source: Path, output: Path, *, resources: Path = DEFAULT_RESOURCES,
            report_path: Path | None = None, dry_run: bool = False, progress: bool = False,
            report_root: Path | None = None, source_mode: str = "legacy") -> dict[str, Any]:
    def status(message: str) -> None:
        if progress:
            print(f"logical-world: {message}", file=sys.stderr, flush=True)

    source, output, resources = source.resolve(), output.resolve(), resources.resolve()
    if output.exists():
        raise ValueError("output must be a new directory outside the source")
    if not (resources / "migration.json").is_file() or not (resources / "geometry.json").is_file():
        raise ValueError(f"missing logical resources under {resources}")
    report_path = (report_path or default_report_path(output)).resolve()
    report_root = (report_root or (TOOLS.parent / "build")).resolve()
    validate_paths(source, output, resources, report_path, report_root)
    rules, defaults = parse_rules(resources, source_mode)
    definitions = definition_hashes(resources)
    status(f"rules parsed: rules={len(rules)}")
    with tempfile.TemporaryDirectory(prefix="logical-world-", dir=str(output.parent)) as temporary:
        stage = Path(temporary)
        copied, source_hashes, source_kind = copy_source(source, stage)
        status(f"source copied: kind={source_kind}, files={len(source_hashes)}")
        status("world loading")
        world = World(copied, defaults)
        status(f"world loaded: chunks={len(world.chunks)}")
        items, found, unmatched_modules = candidates(world, rules, status if progress else None)
        reject_overlaps(items)
        unresolved = unresolved_components(items, found, rules)
        status(f"candidates resolved: accepted={sum(item.reason is None for item in items)}, rejected={sum(item.reason is not None for item in items)}, unresolvedV2={len(unresolved)}")
        ledger = apply(world, items)
        report = {
            "format": "bloodborne-logical-world-conversion-v1", "dryRun": dry_run, "sourceMode": source_mode,
            "source": {"path": str(source), "kind": source_kind, "hashes": source_hashes},
            "resources": {"path": str(resources), "migrationSha256": hashlib.sha256((resources / "migration.json").read_bytes()).hexdigest(),
                          "geometrySha256": hashlib.sha256((resources / "geometry.json").read_bytes()).hexdigest(), "definitionsSha256": definitions},
            "counts": {"rules": len(rules), "converted": len(ledger), "rejected": sum(item.reason is not None for item in items), "unresolvedV2": len(unresolved), "unmatchedModules": sum(group["count"] for group in unmatched_modules)},
            "ledger": ledger,
            "rejected": [{"rule": item.rule.number, "mode": item.mode, "dimension": item.dimension,
                          "origin": list(item.origin), "reason": item.reason} for item in items if item.reason],
            "unresolved": unresolved,
            "unmatchedModules": unmatched_modules,
            "reportNotes": {"unresolvedV2": "Unconsumed positions whose exact state is a component of an inverse rule, after alias deduplication and overlap rejection.",
                            "unmatchedModules": "Aggregated non-inverse bloodborne_blocks:m_* states; these rows are inventory statistics, not conversion failures."},
        }
        if not dry_run:
            world.save()
            # The copied world is moved only after successful conversion.  The
            # original source was only read, including when supplied as a zip.
            os.replace(copied, output)
        write_report(report_path, report)
        status(f"complete: converted={len(ledger)}")
    return report


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path, help="offline world directory or ZIP")
    parser.add_argument("output", type=Path, help="new output directory")
    parser.add_argument("--resources", type=Path, default=DEFAULT_RESOURCES, help="logical resource directory")
    parser.add_argument("--report", type=Path, help="JSON report path (must be below build/)")
    parser.add_argument("--report-root", type=Path, help="permitted root for --report (defaults to this project's build directory)")
    parser.add_argument("--dry-run", action="store_true", help="scan and report without creating output")
    parser.add_argument("--progress", action="store_true", help="write conversion phases and counts to stderr")
    parser.add_argument("--source-mode", choices=("legacy", "original-v2-poc", "original-v2"), default="legacy",
                        help="original-v2 matches reviewed Contract V2 vanilla patterns; original-v2-poc restricts to original five")
    args = parser.parse_args()
    report = convert(args.source, args.output, resources=args.resources, report_path=args.report, dry_run=args.dry_run, progress=args.progress, report_root=args.report_root, source_mode=args.source_mode)
    print(json.dumps(report["counts"], ensure_ascii=False))


if __name__ == "__main__":
    main()
