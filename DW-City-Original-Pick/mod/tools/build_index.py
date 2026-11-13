"""Build the Bloodborne pick table from the final native plan and the current save."""
from __future__ import annotations

import argparse
import base64
import hashlib
import itertools
import json
import math
import mmap
import os
import re
import shutil
import sqlite3
import struct
import sys
from collections import Counter, defaultdict
from contextlib import ExitStack
from functools import lru_cache
from pathlib import Path
from zipfile import ZipFile

sys.path.insert(0, str(Path(__file__).resolve().parent))
from world_io import RegionFile, palette_bits

CHUNK_MAGIC = 0x44575043
POSE_MAGIC = 0x44575031
ROUTE_MAGIC = 0x44575052
ROUTES_PER_FILE = 64
NATIVE = "assets/bloodborne_dw/native_grid/"
STATIC = "bloodborne_dw:static_architecture_"
TECHNICAL = (
    "native_", "static_architecture_", "composite_", "source_fallback_carrier",
    "source_invisible_carrier", "source_compatibility_fragment", "source_door_fragment",
)
AIR = {"minecraft:air", "minecraft:cave_air", "minecraft:void_air", "minecraft:light",
       "minecraft:water", "minecraft:lava", "minecraft:barrier"}
REGION_NAME = re.compile(r"r\.(-?\d+)\.(-?\d+)\.mca")
SAMPLES = {
    (93, 124, -708): "bloodborne_dw:source_addon_wall_deco_3",
    (447, 44, -838): "bloodborne_dw:source_black_wool",
    (445, 44, -831): "bloodborne_dw:source_cyan_glazed_terracotta",
    (445, 43, -835): "bloodborne_dw:source_gray_wool",
}


def sqlite_readonly(path):
    db = sqlite3.connect(path.resolve().as_uri() + "?mode=ro&immutable=1", uri=True)
    db.execute("PRAGMA query_only=ON")
    db.execute("PRAGMA cache_size=-16384")
    db.execute("PRAGMA temp_store=FILE")
    return db


def stable_json(value):
    return json.dumps(value, sort_keys=True, ensure_ascii=False, separators=(",", ":"))


def pack_utf(value):
    # Registry keys, state property values and legacy stair keys are ASCII.
    encoded = value.encode("ascii")
    return struct.pack(">H", len(encoded)) + encoded


def cell_key(x, y, z):
    if not -2048 <= y < 2048:
        raise ValueError(f"Unsupported world height {y}")
    return ((y + 2048) << 8) | ((z & 15) << 4) | (x & 15)


def signature(vertices):
    return tuple(sorted(tuple(round(value * 8192) for value in vertex[:3]) for vertex in vertices))


def quantized(vertices):
    return tuple(max(0, min(65535, round(value * 65535))) for vertex in vertices for value in vertex[:3])


class Meshes:
    def __init__(self, jar, binary):
        visual_layout = json.loads(jar.read(NATIVE + "visual_physics.json"))
        data = jar.read(NATIVE + "catalog.json")
        self.catalog = hashlib.sha256(data).hexdigest()
        if visual_layout["catalogSha256"] != self.catalog:
            raise ValueError("The JAR visual table and catalog have different fingerprints")
        self.physics = visual_layout["cellToPhysics"]
        self.compatible = {self.catalog, *visual_layout.get("compatibleCatalogs", [])}
        catalog = json.loads(data)
        del data
        self.meshes = [row["mesh"] for row in catalog["cells"]]
        self.counts = [row["quadCount"] for row in catalog["meshes"]]
        self.offsets = [0, *itertools.accumulate(self.counts)]
        self.materials = [row["sprite"] for row in catalog["materials"]]
        self.material_layers = [row["layer"] for row in catalog["materials"]]
        del catalog
        self.binary = binary
        repairs = json.loads(jar.read(NATIVE + "visual-repairs.json"))
        if repairs["catalogSha256"] != self.catalog:
            raise ValueError("The visual repair table has a different catalog")
        self.repairs = {group["mesh"]: base64.b64decode(group["quads"]) for group in repairs["groups"]}
        if any(len(data) % 139 for data in self.repairs.values()):
            raise ValueError("Invalid repaired mesh geometry")
        if binary[:8] != b"NGQ1" + struct.pack(">I", len(self.counts)):
            raise ValueError("Unsupported native mesh encoding")
        expected = 8 + 4 * len(self.counts) + 139 * self.offsets[-1]
        if len(binary) != expected:
            raise ValueError("Incomplete native mesh archive")

    def mesh(self, visual):
        return self.meshes[visual] if 0 <= visual < len(self.meshes) else None

    def mesh_records(self, visual):
        mesh = self.mesh(visual)
        if mesh is None:
            return
        start = 8 + 4 * mesh + 139 * self.offsets[mesh]
        count = struct.unpack_from(">I", self.binary, start)[0]
        if count != self.counts[mesh]:
            raise ValueError(f"Unexpected quad count for mesh {mesh}")
        data = self.repairs.get(mesh)
        if data is None:
            data, first = self.binary, start + 4
        else:
            first, count = 0, len(data) // 139
        for offset in range(first, first + 139 * count, 139):
            yield data, offset

    def final_quads(self, visual):
        for data, offset in self.mesh_records(visual):
            material = struct.unpack_from(">I", data, offset)[0]
            vertices = [struct.unpack_from(">fff", data, offset + 11 + i * 32) for i in range(4)]
            yield signature(vertices), self.materials[material], quantized(vertices)


class RouteWriter:
    def __init__(self, root):
        self.root = root
        self.parts = []
        self.ids = {}

    def add(self, candidates):
        if len(candidates) > 64:
            raise ValueError("A city cell has too many distinct source items")
        out = bytearray([len(candidates)])
        for pose, quads in candidates:
            if len(quads) > 2048:
                raise ValueError("A source model has too many quads in one cell")
            out.extend(struct.pack(">HH", pose, len(quads)))
            for quad in sorted(quads):
                out.extend(struct.pack(">12H", *quad))
        digest = hashlib.sha256(out).digest()
        existing = self.ids.get(digest)
        if existing is not None:
            return existing
        index = len(self.ids)
        self.ids[digest] = index
        self.parts.append(bytes(out))
        if len(self.parts) == ROUTES_PER_FILE:
            self.flush(index // ROUTES_PER_FILE)
        return index

    def flush(self, file):
        if self.parts:
            folder = self.root / "routes"
            folder.mkdir(exist_ok=True)
            with (folder / f"{file}.bin").open("xb") as stream:
                stream.write(struct.pack(">IB", ROUTE_MAGIC, len(self.parts)))
                stream.writelines(self.parts)
            self.parts.clear()


class Sources:
    def __init__(self, plan, geometry, jar, meshes, out):
        self.plan, self.geometry, self.jar, self.meshes = plan, geometry, jar, meshes
        self.poses = {key: json.loads(data) for key, data in plan.execute("SELECT key,data FROM poses")}
        self.item_models = {
            f"{parts[1]}:{parts[4][:-5]}"
            for name in jar.namelist()
            if (parts := name.split("/", 4))[:1] == ["assets"]
            and len(parts) == 5 and parts[2:4] == ["models", "item"] and name.endswith(".json")
        }
        self.item_ids = {}
        self.routes = RouteWriter(out)
        self.stats = Counter()
        self.unavailable = Counter()
        self.cache = {}
        self.static_poses = None
        self.static_recipes = None
        self.static_routes = {}
        self.fragment_pose = {}
        for key, raw in self.poses.items():
            signature = self.pose_signature(raw)
            if signature in self.fragment_pose:
                self.fragment_pose[signature] = None
            else:
                self.fragment_pose[signature] = key
        self.by_visual = defaultdict(list)
        for visual, key in plan.execute("SELECT type_index,key FROM compositions ORDER BY key"):
            self.by_visual[visual].append(key)
        self.visual_consensus = {}
        self.version = json.loads(jar.read("fabric.mod.json"))["version"]

    def item(self, raw):
        registry = raw.get("registryId")
        if registry in AIR or not registry:
            return None
        namespace, name = registry.split(":", 1)
        if namespace == "bloodborne_dw" and name.startswith(TECHNICAL):
            return None
        if registry not in self.item_models and namespace != "minecraft":
            self.unavailable[registry] += 1
            return None
        props = dict(raw.get("properties", {}))
        if namespace == "bloodborne_dw":
            # The held source item takes its orientation from the player on placement.
            props.pop("rotation", None)
        # Avoid a different identity for two equivalent encodings of one source.
        canonical = (registry, raw.get("legacyStairArt", ""), tuple(sorted(props.items())))
        return canonical

    @lru_cache(maxsize=6144)
    def source_quads(self, kind, identity, offset=()):
        if kind == "fragment":
            query = "SELECT data FROM fragments WHERE pose=? AND x=? AND y=? AND z=?"
        elif kind == "native_fragment":
            query = "SELECT data FROM native_fragments WHERE state=? AND x=? AND y=? AND z=?"
        else:
            query = "SELECT data FROM native WHERE state=?"
        row = self.geometry.execute(query, (identity, *offset)).fetchone()
        if row is None:
            return None
        return frozenset((signature(quad["vertices"]), quad["material"]["sprite"], quantized(quad["vertices"]))
                         for quad in json.loads(row[0]).get("quads", [])
                         if len(quad["vertices"]) == 4)

    @staticmethod
    def pose_signature(raw):
        return (raw["registryId"], tuple(sorted(raw.get("properties", {}).items())),
                tuple(raw.get("shift", [0, 0, 0])), raw.get("mountY", 0), raw.get("legacyStairArt", ""))

    def static_sources(self, index):
        if self.static_poses is None:
            data = json.loads(self.jar.read("bloodborne_dw/static-architecture-v1274.json"))
            self.static_poses = {entry["key"]: entry for entry in data["poses"]}
            self.static_recipes = [tuple((p["pose"], tuple(-value for value in p["origin"]))
                                         for p in row.get("fragments", [])) for row in data["recipes"]]
        if index >= len(self.static_recipes):
            return
        for key, offset in self.static_recipes[index]:
            pose = self.static_poses[key]
            geometry = self.fragment_pose.get(self.pose_signature(pose))
            yield pose, ("fragment", geometry, offset) if geometry is not None else None

    def static_route(self, index):
        if index not in self.static_routes:
            self.static_routes[index] = self.compile_providers(self.static_sources(index), None)
        return self.static_routes[index]

    def providers(self, composition):
        for field in ("fragments", "substrateFragments"):
            for ref in composition.get(field, []):
                source = self.poses.get(ref["pose"])
                if source:
                    yield source, ("fragment", ref["pose"], tuple(ref["cell"]))
        for ref in composition.get("substrateNativeFragments", []):
            yield ref["state"], ("native_fragment", stable_json(ref["state"]), tuple(ref["cell"]))
        base = composition.get("baseState")
        if not base or composition.get("baseVisualMode") == "substrateFragments":
            return
        if base["registryId"].startswith(STATIC):
            bank = int(base["registryId"].removeprefix(STATIC))
            yield from self.static_sources(bank * 128 + int(base["properties"]["part"]))
        else:
            yield base, ("native", stable_json(base), ())

    def item_id(self, pose):
        if pose not in self.item_ids:
            number = len(self.item_ids)
            if number >= 65_536:
                raise ValueError("Too many source item poses")
            self.item_ids[pose] = number
        return self.item_ids[pose]

    def route(self, key, visual):
        binding = (key, visual)
        if binding not in self.cache:
            row = self.plan.execute("SELECT data FROM compositions WHERE key=?", (key,)).fetchone()
            self.cache[binding] = None if row is None else self.compile(json.loads(row[0]), visual)
        return self.cache[binding]

    def compile(self, composition, visual):
        return self.compile_providers(self.providers(composition), visual)

    def compile_providers(self, providers, visual):
        by_item = defaultdict(list)
        for raw, provider in providers:
            source = self.item(raw)
            if source is not None:
                by_item[source].append(provider)
        if not by_item:
            return None
        if len(by_item) == 1:
            return -1 - self.item_id(next(iter(by_item)))

        # A geometry-less contributing box may be physical only. The original
        # export distinguishes those from visible quads, including overhangs.
        exact, positions = defaultdict(set), defaultdict(set)
        unresolved, visible = set(), []
        matches = defaultdict(set)
        for source, refs in by_item.items():
            shapes = set()
            unknown = False
            for ref in refs:
                data = self.source_quads(*ref) if ref is not None else None
                if data is None:
                    unknown = True
                else:
                    shapes.update(data)
            if not shapes and not unknown:
                continue
            pose = self.item_id(source)
            visible.append(pose)
            if unknown:
                unresolved.add(pose)
            for key, sprite, quad in shapes:
                if visual is None:
                    matches[pose].add(quad)
                else:
                    exact[(key, sprite)].add(pose)
                    positions[key].add(pose)
        if len(visible) < 2:
            return -1 - visible[0] if visible else None

        if visual is not None:
            for position, sprite, quad in self.meshes.final_quads(visual):
                for pose in exact.get((position, sprite), positions.get(position, ())):
                    matches[pose].add(quad)
        candidates = [(pose, matches[pose] if pose not in unresolved else set()) for pose in visible]
        self.stats["geometryRoutes" if all(quads for _, quads in candidates) else "choiceRoutes"] += 1
        return self.routes.add(candidates)

    def positions(self, cx, cz):
        return {(x, y, z): (visual, key) for x, y, z, visual, key in self.plan.execute(
            "SELECT x,y,z,type_index,composition FROM placements WHERE dim='.' AND cx=? AND cz=?", (cx, cz))}

    def source_route(self, visual, at_position):
        if visual < 0:
            result = self.static_route(-1 - visual)
            return result, "staticRecipe" if result is not None else "noStaticSource"
        if at_position is not None:
            original, key = at_position
            if visual == original:
                result = self.route(key, visual)
                return result, "exactPosition" if result is not None else "noSourceItem"
            if self.meshes.mesh(visual) is not None and self.meshes.mesh(visual) == self.meshes.mesh(original):
                result = self.route(key, visual)
                return result, "sameMeshAtPosition" if result is not None else "noSourceItem"
        keys = self.by_visual.get(visual, [])
        if not keys:
            return None, "missingProvenance"
        if len(keys) == 1:
            result = self.route(keys[0], visual)
            return result, "unambiguousVisual" if result is not None else "noSourceItem"
        if visual not in self.visual_consensus:
            results = {self.route(key, visual) for key in keys}
            self.visual_consensus[visual] = next(iter(results)) if len(results) == 1 else None
        result = self.visual_consensus[visual]
        return result, "visualConsensus" if result is not None else "ambiguousProvenance"

    def write_poses(self, output):
        with output.open("xb") as stream:
            stream.write(struct.pack(">I", POSE_MAGIC))
            stream.write(pack_utf(self.meshes.catalog))
            stream.write(pack_utf(self.version))
            stream.write(struct.pack(">I", len(self.item_ids)))
            for registry, legacy, props in self.item_ids:
                stream.write(pack_utf(registry))
                stream.write(pack_utf(legacy))
                stream.write(bytes([len(props)]))
                for key, value in props:
                    stream.write(pack_utf(key) + pack_utf(value))
        if self.routes.parts:
            self.routes.flush((len(self.routes.ids) - 1) // ROUTES_PER_FILE)


def region_files(world):
    roots = [("minecraft", "overworld", world / "region"),
             ("minecraft", "the_nether", world / "DIM-1" / "region"),
             ("minecraft", "the_end", world / "DIM1" / "region")]
    custom = world / "dimensions"
    if custom.is_dir():
        for directory, subdirs, _ in os.walk(custom, followlinks=False):
            path = Path(directory)
            subdirs[:] = [name for name in sorted(subdirs) if not is_link(path / name)]
            if path.name == "region":
                parts = path.relative_to(custom).parts
                if len(parts) >= 3:
                    roots.append((parts[0], "/".join(parts[1:-1]), path))
                subdirs.clear()
    for namespace, dimension, folder in roots:
        if not folder.is_dir() or is_link(folder):
            continue
        for entry in sorted(folder.iterdir()):
            match = REGION_NAME.fullmatch(entry.name)
            if match and entry.is_file() and not is_link(entry):
                yield namespace, dimension, entry, int(match[1]), int(match[2])


def is_link(path):
    return bool(getattr(path.lstat(), "st_file_attributes", 0) & 1024) or path.is_symlink()


def section_states(root):
    return {section.value["Y"].value: section.value.get("block_states")
            for section in root["sections"].value}


def native_physics(palette):
    indices = []
    prefix = "bloodborne_dw:native_physics_"
    for tag in palette:
        state = tag.value
        name = state["Name"].value
        if name.startswith(prefix):
            indices.append(int(name.removeprefix(prefix)) * 128 + int(state["Properties"].value["part"].value))
        else:
            indices.append(-1)
    return indices


def chunk_entries(root, meshes, stats):
    metadata = root.get("DreamwalkerVisuals")
    if metadata is None:
        return
    data = metadata.value
    if data["Schema"].value != 1 or data["Catalog"].value not in meshes.compatible:
        stats["unmatchedCatalogChunks"] += 1
        return
    states = section_states(root)
    for section in data["Sections"].value:
        sy = section.value["Y"].value
        state = states.get(sy)
        if state is None:
            continue
        palette = native_physics(state.value["palette"].value)
        if all(p < 0 for p in palette):
            continue
        longs = state.value.get("data")
        bits = palette_bits(len(palette))
        per_long = 64 // bits
        mask = (1 << bits) - 1
        for packed in section.value["Entries"].value:
            visual, cell = packed >> 12, packed & 4095
            physical = palette[0] if longs is None else palette[
                (longs.value[cell // per_long] >> ((cell % per_long) * bits)) & mask]
            if physical < 0:
                continue
            if not 0 <= visual < len(meshes.physics) or meshes.physics[visual] != physical:
                stats["staleVisual"] += 1
                continue
            yield cell & 15, 16 * sy + (cell >> 8), (cell >> 4) & 15, visual


def static_entries(root):
    for sy, state in section_states(root).items():
        if state is None:
            continue
        palette = []
        for tag in state.value["palette"].value:
            name = tag.value["Name"].value
            if name.startswith(STATIC):
                part = int(tag.value["Properties"].value["part"].value)
                palette.append(-1 - (int(name.removeprefix(STATIC)) * 128 + part))
            else:
                palette.append(None)
        if all(value is None for value in palette):
            continue
        longs = state.value.get("data")
        bits = palette_bits(len(palette))
        per_long, mask = 64 // bits, (1 << bits) - 1
        if longs is None:
            selected = ((cell, palette[0]) for cell in range(4096))
        else:
            selected = ((cell, palette[(longs.value[cell // per_long] >> ((cell % per_long) * bits)) & mask])
                        for cell in range(4096))
        for cell, visual in selected:
            if visual is not None:
                yield cell & 15, 16 * sy + (cell >> 8), (cell >> 4) & 15, visual


def build(plan, geometry, jar, meshes, world, out):
    source = Sources(plan, geometry, jar, meshes, out)
    summary = Counter()
    by_dimension = defaultdict(Counter)
    missed = Counter()
    sample_rows = {}
    for namespace, dimension, path, rx, rz in region_files(world):
        dimension_stats = by_dimension[f"{namespace}:{dimension}"]
        for chunk in RegionFile.open(path).chunks():
            cx, cz = rx * 32 + chunk.x, rz * 32 + chunk.z
            chunk_root = chunk.nbt().root.value
            native = list(chunk_entries(chunk_root, meshes, summary))
            placed = source.positions(cx, cz) if native and (namespace, dimension) == ("minecraft", "overworld") else {}
            rows = []
            for lx, y, lz, visual in itertools.chain(native, static_entries(chunk_root)):
                pos = (16 * cx + lx, y, 16 * cz + lz)
                dimension_stats["native" if visual >= 0 else "staticFragments"] += 1
                if visual >= 0:
                    mesh = meshes.mesh(visual)
                    if mesh is None or meshes.counts[mesh] == 0:
                        dimension_stats["noVisualSurface"] += 1
                        continue
                ref, reason = source.source_route(visual, placed.get(pos))
                if ref is None:
                    dimension_stats["unmatched"] += 1
                    missed[(reason, visual)] += 1
                    continue
                dimension_stats["indexed"] += 1
                dimension_stats[reason] += 1
                if ref >= 0:
                    dimension_stats["multipleSources"] += 1
                rows.append((cell_key(*pos), visual, ref))
                if (namespace, dimension) == ("minecraft", "overworld") and pos in SAMPLES:
                    sample_rows[",".join(map(str, pos))] = {"visual": visual, "route": ref, "expected": SAMPLES[pos]}
            if rows:
                dest = out / "chunks" / namespace / dimension
                dest.mkdir(parents=True, exist_ok=True)
                with (dest / f"{cx}.{cz}.bin").open("xb") as stream:
                    stream.write(struct.pack(">II", CHUNK_MAGIC, len(rows)))
                    for row in sorted(rows):
                        stream.write(struct.pack(">iii", *row))
                dimension_stats["chunks"] += 1
        print(f"{namespace}:{dimension} {path.name}: {dimension_stats['indexed']} indexed", flush=True)
    source.write_poses(out / "poses.bin")
    for data in by_dimension.values():
        summary.update(data)
    summary.update(source.stats)
    return {
        "status": "PASS", "catalog": meshes.catalog, "map": str(world),
        "counts": dict(summary), "dimensions": {key: dict(value) for key, value in by_dimension.items()},
        "sourceItemPoses": len(source.item_ids), "routes": len(source.routes.ids), "examples": sample_rows,
        "unmatchedVisuals": [{"reason": reason, "visual": visual, "cells": count}
                             for (reason, visual), count in missed.most_common(24)],
        "unavailableRegistries": source.unavailable.most_common(24),
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("world", "plan", "fragments", "jar", "output", "audit", "mesh-cache"):
        parser.add_argument("--" + name, type=Path, required=True)
    args = parser.parse_args()
    if args.output.exists() or args.audit.exists():
        raise ValueError("Choose a fresh output directory and audit path")
    args.output.mkdir(parents=True)
    with ExitStack() as stack:
        jar = stack.enter_context(ZipFile(args.jar))
        if not args.mesh_cache.exists():
            with jar.open(NATIVE + "meshes.bin") as source, args.mesh_cache.open("xb") as target:
                shutil.copyfileobj(source, target, length=1024 * 1024)
        if args.mesh_cache.stat().st_size != jar.getinfo(NATIVE + "meshes.bin").file_size:
            raise ValueError("The cached mesh stream is incomplete")
        mesh_file = stack.enter_context(args.mesh_cache.open("rb"))
        binary = stack.enter_context(mmap.mmap(mesh_file.fileno(), 0, access=mmap.ACCESS_READ))
        plan = sqlite_readonly(args.plan)
        geometry = sqlite_readonly(args.fragments)
        stack.callback(plan.close)
        stack.callback(geometry.close)
        report = build(plan, geometry, jar, Meshes(jar, binary), args.world, args.output)
    with args.audit.open("x", encoding="utf-8") as audit:
        json.dump(report, audit, indent=2, ensure_ascii=False)
        audit.write("\n")
    print(json.dumps(report, ensure_ascii=False))


if __name__ == "__main__":
    main()
