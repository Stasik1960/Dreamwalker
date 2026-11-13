"""Export verified single-source Original Pick routes for city carrier visuals."""

import argparse
from collections import Counter
from contextlib import closing
from functools import lru_cache
import json
from pathlib import Path
import struct
from zipfile import ZipFile

from build_index import AIR, Meshes, NATIVE, Sources, sqlite_readonly


SOURCE = "bloodborne_dw:source_compatibility_fragment"
SLABS = {"bottom": "_half", "top": "_upper", "double": "_full"}


def quad_key(sprite, layer, vertices):
    # Sorting the complete XYZ+UV tuple preserves each vertex's UV assignment.
    points = tuple(sorted(tuple(round(value * 8192) for value in vertex[:5]) for vertex in vertices))
    return sprite, layer, points


def canonical_item(sources, raw, split_slabs):
    registry = raw["registryId"]
    if registry == SOURCE and registry in sources.item_models:
        properties = {name: value for name, value in sorted(raw["properties"].items()) if name != "rotation"}
        return registry, "", tuple(properties.items())
    item = sources.item(raw)
    if item is None or registry not in split_slabs:
        return item
    registry, legacy, values = item
    properties = dict(values)
    suffix = SLABS.get(properties.get("type"))
    if suffix is not None:
        properties.pop("type")
        if suffix == "_full":
            properties.pop("waterlogged", None)
        registry += suffix
    return registry, legacy, tuple(properties.items())


def export(plan, geometry, jar, meshes, output):
    sources = Sources(plan, geometry, jar, meshes, output.parent)
    slab_path = "bloodborne_dw/distinct-slabs.json"
    split_slabs = {"bloodborne_dw:" + name for name in json.loads(jar.read(slab_path))} if slab_path in jar.namelist() else set()
    compatibility_poses = {key for key, pose in sources.poses.items() if pose["registryId"] == SOURCE}
    bridge_visuals = {value for value, in plan.execute("SELECT DISTINCT type_index FROM bridge_cells WHERE type_index IS NOT NULL")}
    candidates = bridge_visuals.copy()
    for visual, raw in plan.execute("SELECT type_index,data FROM compositions WHERE type_index IS NOT NULL"):
        if visual not in candidates and any(pose in raw for pose in compatibility_poses):
            candidates.add(visual)

    @lru_cache(maxsize=4096)
    def source_geometry(ref):
        kind, identity, offset = ref
        query = {
            "fragment": "SELECT data FROM fragments WHERE pose=? AND x=? AND y=? AND z=?",
            "native_fragment": "SELECT data FROM native_fragments WHERE state=? AND x=? AND y=? AND z=?",
            "native": "SELECT data FROM native WHERE state=?",
        }[kind]
        raw = geometry.execute(query, (identity, *offset)).fetchone()
        if raw is None:
            return None
        return Counter(quad_key(quad["material"]["sprite"], quad["material"]["layer"], quad["vertices"])
                       for quad in json.loads(raw[0])["quads"])

    routes = {}
    rejected = set()
    for visual in sorted(candidates):
        baked = Counter()
        for data, offset in meshes.mesh_records(visual):
            material, = struct.unpack_from(">I", data, offset)
            vertices = [struct.unpack_from(">5f", data, offset + 11 + i * 32) for i in range(4)]
            baked[quad_key(meshes.materials[material], meshes.material_layers[material], vertices)] += 1
        if not baked:
            continue
        for key in sources.by_visual[visual]:
            raw, = plan.execute("SELECT data FROM compositions WHERE key=?", (key,)).fetchone()
            source = Counter()
            items = set()
            for raw_source, ref in sources.providers(json.loads(raw)):
                if raw_source["registryId"] in AIR:
                    continue
                part = source_geometry(ref) if ref is not None else None
                if part is None:
                    rejected.add(visual)
                    break
                if part:
                    source.update(part)
                    items.add(canonical_item(sources, raw_source, split_slabs))
            if visual in rejected or source != baked or len(items) != 1 or None in items:
                rejected.add(visual)
                break
            item, = items
            if visual in routes and routes[visual] != item:
                rejected.add(visual)
                break
            routes[visual] = item

    verified = {}
    for visual in sorted(routes.keys() - rejected):
        registry, legacy, properties = routes[visual]
        row = {"registryId": registry, "properties": dict(properties)}
        if legacy:
            row["legacyArt"] = legacy
        verified[str(visual)] = row
    print(f"Verified {len(verified)} of {len(candidates)} candidate visuals with exact mesh provenance")
    print(f"Verified {len((routes.keys() - rejected) & bridge_visuals)} of {len(bridge_visuals)} bridge visuals")
    return verified


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("plan", "fragments", "jar", "output"):
        parser.add_argument("--" + name, type=Path, required=True)
    args = parser.parse_args()
    with ZipFile(args.jar) as jar, closing(sqlite_readonly(args.plan)) as plan, closing(sqlite_readonly(args.fragments)) as geometry:
        meshes = Meshes(jar, jar.read(NATIVE + "meshes.bin"))
        routes = export(plan, geometry, jar, meshes, args.output)
    if not routes:
        raise ValueError("No city carrier provenance matched the supplied visual catalog")
    lines = [f"    {json.dumps(visual)}: {json.dumps(props, separators=(',', ':'))}" for visual, props in routes.items()]
    content = (f'{{\n  "schema": 2,\n  "catalogSha256": "{meshes.catalog}",\n  "visuals": {{\n'
               + ",\n".join(lines) + "\n  }\n}\n")
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(content, encoding="utf-8", newline="\n")


if __name__ == "__main__":
    main()
