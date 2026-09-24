"""Read-only inventory for the production-palette reconciliation decision."""
from __future__ import annotations

import argparse
import gzip
import hashlib
import json
from collections import Counter, defaultdict
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "src/main/resources/bloodborne_blocks"
LOGICAL = RES / "logical"
DOCS = ROOT / "docs"
STATUSES = ("KEEP", "MERGE", "SPLIT_SOURCE_ONLY", "SUPERSEDED", "DUPLICATE", "COMPATIBILITY_ONLY",
            "ORPHAN", "SUSPICIOUS_FRAGMENT", "NEEDS_SEMANTIC_REVIEW")
REFERENCE_SHA256 = "4353737d536677469d3b895e3515496ab64fab7b224e43428eb96e8c09724a51"
REFERENCE_COMMIT = "62346a"


def digest(value: Any) -> str:
    return hashlib.sha256(json.dumps(value, sort_keys=True, separators=(",", ":"), ensure_ascii=False).encode()).hexdigest()


def read(path: Path) -> Any:
    return json.loads(path.read_text(encoding="utf8"))


def file_hashes(path: Path, suffix: str) -> Counter[str]:
    return Counter(hashlib.sha256(item.read_bytes()).hexdigest() for item in path.rglob("*" ) if item.is_file() and item.suffix == suffix)


def load_current(root: Path = ROOT) -> tuple[list[dict], int, int, set[str], dict, dict, dict, dict]:
    logical = root / "src/main/resources/bloodborne_blocks/logical"
    definitions = read(logical / "definitions.json")["blocks"]
    catalog_a_count = len(read(root / "src/main/resources/bloodborne_blocks/definitions.json")["blocks"])
    modular_count = len(read(root / "src/main/resources/bloodborne_blocks/v2/definitions.json")["blocks"])
    hidden = set(read(logical / "hidden-items.json"))
    contracts = {row["id"]: row for row in read(logical / "contracts-v2.json")["families"]}
    with gzip.open(logical / "meshes.json.gz", "rt", encoding="utf8") as stream:
        meshes = json.load(stream)
    report = read(root / "docs/nightmare-qa-report.json")
    superseded = {row["old_id"]: row["outputs"] for row in report.get("families", []) if row["old_id"] != row["outputs"][0] or len(row["outputs"]) != 1}
    old_migrations = read(logical / "old-logical-migrations.json")
    return definitions, catalog_a_count, modular_count, hidden, contracts, meshes, superseded, old_migrations


def mesh_identity(mesh: dict) -> tuple[str, str]:
    polygons = mesh.get("polygons", [])
    textured = [{"texture": polygon.get("texture"), "vertices": polygon.get("vertices", [])} for polygon in polygons]
    # Meshes are authored per state; this is an exact content identity, not a semantic duplicate claim.
    return digest(textured), digest(sorted((polygon.get("texture"), len(polygon.get("vertices", []))) for polygon in polygons))


def yaw_canonical_identity(mesh: dict) -> str:
    """Exact textured XYZ/UV identity minimized over yaw around the block pivot.

    Deliberately does not translate meshes: anchors remain meaningful production data.
    """
    def point(vertex: list[float], yaw: int) -> tuple[float, ...]:
        x, y, z, *uv = vertex
        dx, dz = x - .5, z - .5
        if yaw == 90: dx, dz = -dz, dx
        elif yaw == 180: dx, dz = -dx, -dz
        elif yaw == 270: dx, dz = dz, -dx
        return tuple(round(value, 5) for value in (dx + .5, y, dz + .5, *uv))
    options = []
    for yaw in (0, 90, 180, 270):
        tokens = []
        for polygon in mesh.get("polygons", []):
            vertices = [point(vertex, yaw) for vertex in polygon.get("vertices", [])]
            rotations = [tuple(vertices[index:] + vertices[:index]) for index in range(len(vertices))]
            reverse = list(reversed(vertices)); rotations.extend(tuple(reverse[index:] + reverse[:index]) for index in range(len(reverse)))
            tokens.append((polygon.get("texture"), min(rotations) if rotations else ()))
        options.append(tuple(sorted(tokens)))
    return digest(min(options))


def status_for(ident: str, hidden: set[str], contracts: dict, superseded: dict) -> str:
    if ident in superseded:
        return "SUPERSEDED"
    if ident in hidden:
        return "COMPATIBILITY_ONLY"
    if contracts.get(ident, {}).get("authority") == "user":
        return "KEEP"
    return "NEEDS_SEMANTIC_REVIEW"


def build_inventory(root: Path = ROOT) -> dict:
    definitions, catalog_a_count, modular_count, hidden, contracts, meshes, superseded, old_migrations = load_current(root)
    visual_meshes = defaultdict(list)
    yaw_meshes = defaultdict(list)
    for mesh_id, mesh in meshes.items():
        exact, _topology = mesh_identity(mesh); yaw_canonical = yaw_canonical_identity(mesh)
        visual_meshes[exact].append(mesh_id)
        yaw_meshes[yaw_canonical].append(mesh_id)
    migration_targets = Counter(rule.get("target", {}).get("id") for rule in old_migrations.get("rules", []))
    rows = []
    for definition in sorted(definitions, key=lambda row: row["id"]):
        ident = definition["id"]
        contract = contracts.get(ident)
        states = []
        for state, mesh_id in sorted(definition.get("models", {}).items()):
            mesh = meshes.get(mesh_id, {"polygons": []})
            exact, topology_bucket = mesh_identity(mesh); yaw_canonical = yaw_canonical_identity(mesh)
            render = contract.get("states", {}).get(state, {}).get("render_mesh") if contract else None
            states.append({"state": state, "mesh": mesh_id, "polygon_count": len(mesh.get("polygons", [])),
                           "textured_uv_identity": exact, "topology_bucket": topology_bucket,
                           "yaw_canonical_identity": yaw_canonical,
                           "exact_duplicate_meshes": sorted(visual_meshes[exact]),
                           "yaw_canonical_duplicate_meshes": sorted(yaw_meshes[yaw_canonical]),
                           "render_offset": render.get("offset") if render else None})
        raw_patterns = [pattern for state in (contract or {}).get("states", {}).values()
                        for pattern in state.get("migration_source_pattern", [])]
        rows.append({"id": ident, "provisional_status": status_for(ident, hidden, contracts, superseded),
                     "manual_supersession_outputs": superseded.get(ident, []), "current_visible": ident not in hidden,
                     "compatibility_hidden": ident in hidden, "registry_item": "bloodborne_blocks:" + ident,
                     "contract_v2": ident in contracts, "contract_authority": contract.get("authority") if contract else None,
                     "placement_policy": contract.get("placement_policy") if contract else None,
                     "collision_policy": contract.get("collision_policy") if contract else None,
                     "mount": contract.get("support_plane") if contract else None,
                     "states": states, "base_alt": definition.get("properties", {}).get("visual") == ["base", "alt"],
                     "orientation_states": sum("facing=" in key for key in definition.get("states", {})),
                     "rng_guard_count": sum(len(pattern.get("variant_guards", [])) for pattern in raw_patterns),
                     "raw_pattern_count": len(raw_patterns), "referenced_old_migration_rules": migration_targets[ident],
                     "source_usage": "pending source-usage graph"})
    assets = root / "src/main/resources/assets/bloodborne_blocks"
    texture_hashes = file_hashes(assets / "textures", ".png")
    blockstate_count = sum(1 for item in (assets / "blockstates").glob("*.json"))
    model_count = sum(1 for item in (assets / "models").rglob("*.json"))
    geometry_identities = Counter(state["textured_uv_identity"] for row in rows for state in row["states"])
    state_mesh_references = sum(geometry_identities.values())
    return {"schema_version": 1, "reference": {"source_world_sha256": REFERENCE_SHA256, "commit": REFERENCE_COMMIT},
            "scope": {"logical_definitions": len(rows), "registry_components": {"catalog_a": catalog_a_count,
                      "modular": modular_count, "logical": len(rows), "helper": "PART",
                      "total_without_helper": catalog_a_count + modular_count + len(rows)},
                      "expected_jar_bytes": 97371528, "support_gallery_masters": 746},
            "resource_counts": {"blockstates": blockstate_count, "models": model_count,
                                "logical_state_mesh_references": state_mesh_references,
                                "unique_logical_state_textured_uv_meshes": len(geometry_identities),
                                "duplicate_logical_state_mesh_reference_entries": sum(count - 1 for count in geometry_identities.values()),
                                "textures": sum(texture_hashes.values()), "duplicate_texture_contents": sum(count - 1 for count in texture_hashes.values())},
            "status_counts": dict(sorted(Counter(row["provisional_status"] for row in rows).items())), "inventory": rows}


def write_inventory(output: Path, baseline: Path, root: Path = ROOT) -> dict:
    audit = build_inventory(root)
    text = json.dumps(audit, ensure_ascii=False, indent=2) + "\n"
    output.write_text(text, encoding="utf8")
    if not baseline.exists():
        baseline.write_text(text, encoding="utf8")
    return audit


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=DOCS / "production-palette-inventory.json")
    parser.add_argument("--baseline", type=Path, default=DOCS / "production-baseline.json")
    args = parser.parse_args()
    audit = write_inventory(args.output, args.baseline)
    print(json.dumps({"logical_definitions": audit["scope"]["logical_definitions"], "status_counts": audit["status_counts"]}))
