"""Read-only geometry evidence for production-palette reconciliation."""
from __future__ import annotations

import gzip
import hashlib
import json
from collections import Counter, defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets/bloodborne_blocks"
LOGICAL = ROOT / "src/main/resources/bloodborne_blocks/logical"
OUT = ROOT / "docs/production-geometry-audit.json"


def digest(value): return hashlib.sha256(json.dumps(value, sort_keys=True, separators=(",", ":")).encode()).hexdigest()


def polygon_components(polygons):
    """Components by exact rounded shared polygon edge; no solid/occlusion inference."""
    edges = defaultdict(list)
    for index, polygon in enumerate(polygons):
        vertices = [tuple(round(value, 5) for value in vertex[:3]) for vertex in polygon.get("vertices", [])]
        for number, vertex in enumerate(vertices):
            other = vertices[(number + 1) % len(vertices)]
            edges[tuple(sorted((vertex, other)))].append(index)
    parents = list(range(len(polygons)))
    def find(value):
        while parents[value] != value:
            parents[value] = parents[parents[value]]; value = parents[value]
        return value
    for indices in edges.values():
        for index in indices[1:]:
            left, right = find(indices[0]), find(index)
            if left != right: parents[right] = left
    return {"polygon_count": len(polygons), "shared_edge_components": len({find(index) for index in range(len(polygons))}),
            "boundary_edges": sum(len(indices) == 1 for indices in edges.values()),
            "nonmanifold_edges": sum(len(indices) > 2 for indices in edges.values())}


def all_model_metrics():
    hashes, geometry_hashes = Counter(), Counter()
    base = alt = elements = cullfaces = orientation_variants = 0
    for path in (ASSETS / "models").rglob("*.json"):
        raw = path.read_bytes(); hashes[hashlib.sha256(raw).hexdigest()] += 1
        data = json.loads(raw)
        if "elements" in data:
            elements += 1; geometry_hashes[digest(data["elements"])] += 1
            cullfaces += sum("cullface" in face for element in data["elements"] for face in element.get("faces", {}).values())
        parts = path.parts
        base += "base" in parts; alt += "alt" in parts
    for path in (ASSETS / "blockstates").glob("*.json"):
        data = json.loads(path.read_bytes()); variants = data.get("variants", {})
        orientation_variants += sum("facing=" in key or "axis=" in key for key in variants)
    return {"model_json_files": sum(hashes.values()), "duplicate_model_json_entries": sum(value - 1 for value in hashes.values()),
            "element_geometry_models": sum(geometry_hashes.values()), "unique_element_geometries": len(geometry_hashes),
            "duplicate_element_geometry_entries": sum(value - 1 for value in geometry_hashes.values()),
            "logical_base_model_paths": base, "logical_alt_model_paths": alt,
            "blockstate_orientation_variant_entries": orientation_variants, "element_cullface_declarations": cullfaces}


def reviewed_candidates():
    definitions = {row["id"]: row for row in json.loads((LOGICAL / "definitions.json").read_text())["blocks"]}
    hidden = set(json.loads((LOGICAL / "hidden-items.json").read_text()))
    contracts = {row["id"]: row for row in json.loads((LOGICAL / "contracts-v2.json").read_text())["families"]}
    with gzip.open(LOGICAL / "meshes.json.gz", "rt", encoding="utf8") as stream: meshes = json.load(stream)
    # Existing visible V2 set has 35 families; whole C282 is the deliberate one-object
    # candidate replacing its two split leaves, yielding the requested tentative 34.
    ids = [ident for ident in contracts if ident not in hidden and ident not in {"o_c282_a", "o_c282_b"}]
    ids.append("o_c282")
    rows = []
    for ident in sorted(ids):
        definition = definitions[ident]
        states = []
        for key, mesh_id in sorted(definition["models"].items()):
            mesh = meshes[mesh_id]
            states.append({"state": key, "mesh": mesh_id, **polygon_components(mesh.get("polygons", []))})
        rows.append({"id": ident, "review_id": contracts.get(ident, {}).get("review_id", "C282-whole" if ident == "o_c282" else None),
                     "state_metrics": states})
    return rows


def build():
    rows = reviewed_candidates()
    return {"schema_version": 1, "scope": {"tentative_reviewed_production_candidates": len(rows),
             "definition": "visible Contract V2 minus o_c282_a/o_c282_b plus whole o_c282"},
            "all_model_json_metrics": all_model_metrics(), "candidate_mesh_connectivity": rows,
            "limits": ["Shared-edge components use exact rounded XYZ edges from authored logical meshes.",
                       "boundary_edges and nonmanifold_edges are mesh-topology evidence, not exposed world faces.",
                       "No source-world, chunk-neighbor, culling, or runtime occlusion claim is made."]}


if __name__ == "__main__":
    import argparse
    parser = argparse.ArgumentParser(description=__doc__); parser.add_argument("--output", type=Path, default=OUT)
    args = parser.parse_args(); args.output.write_text(json.dumps(build(), ensure_ascii=False, indent=2) + "\n", encoding="utf8")
