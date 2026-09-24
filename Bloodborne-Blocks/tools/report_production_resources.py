"""Write bounded, reproducible production-palette reporting artifacts.

This intentionally reads the immutable pre-prune snapshots but never rewrites
them.  The provenance graph is a compact index: raw source patterns and world
coordinates remain in the authoritative production manifest.
"""

from __future__ import annotations

import argparse
import gzip
import hashlib
import json
from collections import Counter
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
DOCS = ROOT / "docs"
RESOURCES = ROOT / "src" / "main" / "resources"
ASSETS = RESOURCES / "assets" / "bloodborne_blocks"
LOGICAL = RESOURCES / "bloodborne_blocks" / "logical"
PRODUCTION_STAGE = ROOT / "build" / "production-resource-stage"


def load(path: Path) -> Any:
    return json.loads(path.read_text(encoding="utf-8"))


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def files_under(path: Path, suffix: str | None = None) -> list[Path]:
    return sorted(p for p in path.rglob("*") if p.is_file() and (suffix is None or p.suffix == suffix))


def content_counts(paths: list[Path]) -> dict[str, int]:
    unique = len({sha256(path.read_bytes()) for path in paths})
    return {"files": len(paths), "unique_content": unique, "duplicate_content_entries": len(paths) - unique}


def model_metrics() -> dict[str, int]:
    models = files_under(ASSETS / "models", ".json")
    element_hashes: list[str] = []
    cullfaces = 0
    for path in models:
        data = load(path)
        if "elements" in data:
            element_hashes.append(sha256(json.dumps(data["elements"], sort_keys=True, separators=(",", ":")).encode()))
            cullfaces += sum(1 for element in data["elements"] for face in element.get("faces", {}).values() if "cullface" in face)
    return {
        **content_counts(models),
        "element_geometry_models": len(element_hashes),
        "unique_element_geometries": len(set(element_hashes)),
        "duplicate_element_geometry_entries": len(element_hashes) - len(set(element_hashes)),
        "element_cullface_declarations": cullfaces,
    }


def logical_mesh_metrics() -> dict[str, int]:
    definitions = load(LOGICAL / "definitions.json")["blocks"]
    blocks = definitions.values() if isinstance(definitions, dict) else definitions
    # Runtime definitions compact states into offsets; ``models`` is the state
    # to mesh mapping used by the resource compiler.
    mesh_refs = [mesh for block in blocks for mesh in block["models"].values()]
    with gzip.open(LOGICAL / "meshes.json.gz", "rt", encoding="utf-8") as source:
        meshes = json.load(source)
    missing = sorted(set(mesh_refs) - set(meshes))
    if missing:
        raise ValueError(f"states reference missing meshes: {missing[:5]}")
    identities = [sha256(json.dumps(meshes[key], sort_keys=True, separators=(",", ":")).encode()) for key in mesh_refs]
    return {
        "state_mesh_references": len(mesh_refs),
        "unique_referenced_mesh_ids": len(set(mesh_refs)),
        "duplicate_referenced_mesh_id_entries": len(mesh_refs) - len(set(mesh_refs)),
        "unique_referenced_mesh_content": len(set(identities)),
        "duplicate_referenced_mesh_content_entries": len(identities) - len(set(identities)),
        "mesh_catalog_entries": len(meshes),
    }


def current_geometry_units(models: dict[str, int]) -> dict[str, int]:
    definitions = load(LOGICAL / "definitions.json")["blocks"]
    blocks = definitions.values() if isinstance(definitions, dict) else definitions
    visual_paths = [path for block in blocks for path in block.get("visual_models", {}).values()]
    blockstates = [load(path) for path in files_under(ASSETS / "blockstates", ".json")]
    orientation_variants = sum(len(data.get("variants", {})) for data in blockstates)
    return {
        "model_json_files": models["files"],
        "duplicate_model_json_entries": models["duplicate_content_entries"],
        "element_geometry_models": models["element_geometry_models"],
        "unique_element_geometries": models["unique_element_geometries"],
        "duplicate_element_geometry_entries": models["duplicate_element_geometry_entries"],
        "element_cullface_declarations": models["element_cullface_declarations"],
        "logical_base_model_paths": sum(1 for path in visual_paths if "/base/" in path),
        "logical_alt_model_paths": sum(1 for path in visual_paths if "/alt/" in path),
        "blockstate_orientation_variant_entries": orientation_variants,
    }


def review_nodes(manifest: dict[str, Any]) -> tuple[list[dict[str, str]], dict[str, list[str]]]:
    owners: dict[str, list[str]] = {}
    for item in manifest["objects"]:
        for review in item.get("source_reviews", []):
            owners.setdefault(review, []).append(item["id"])
    return ([{"id": review} for review in sorted(owners)], {key: sorted(value) for key, value in owners.items()})


def compact_patterns(item: dict[str, Any]) -> list[dict[str, Any]]:
    return [
        {"id": f"pattern:{pattern['signature']}", "signature": pattern["signature"], "occurrences": pattern["count"]}
        for pattern in item.get("source_patterns", [])
    ]


def historical_review_token(identifier: str) -> str | None:
    # Identifier syntax is retained historic evidence, not a semantic mapping.
    if not identifier.startswith("o_c"):
        return None
    tail = identifier[3:].split("_", 1)[0]
    return f"C{tail}" if tail.isdigit() else None


def provenance_graph(manifest: dict[str, Any], usage: dict[str, Any]) -> dict[str, Any]:
    reviews, review_outputs = review_nodes(manifest)
    production = []
    patterns = []
    edges = []
    for item in manifest["objects"]:
        production.append({
            "id": item["id"], "status": item["status"], "semantic_label": item["semantic_label"],
            "source_reviews": item.get("source_reviews", []), "source_occurrences": item.get("source_occurrences", 0),
            "occurrence_scope": item.get("occurrence_scope"), "provenance": item.get("provenance"),
        })
        for review in item.get("source_reviews", []):
            edges.append({"from": f"review:{review}", "to": f"production:{item['id']}", "relation": "manual_review_selects"})
        for pattern in compact_patterns(item):
            patterns.append(pattern)
            edges.append({"from": pattern["id"], "to": f"production:{item['id']}", "relation": "exact_source_pattern"})
            for review in item.get("source_reviews", []):
                edges.append({"from": pattern["id"], "to": f"review:{review}", "relation": "reviewed_pattern"})

    excluded = []
    for item in manifest["excluded"]:
        node = {"id": item["id"], "status": item["status"], "reason": item["reason"]}
        token = historical_review_token(item["id"])
        if token in review_outputs:
            node["review_associated_production_ids"] = review_outputs[token]
            for output in review_outputs[token]:
                edges.append({
                    "from": f"excluded:{item['id']}", "to": f"production:{output}",
                    "relation": "historical_review_association", "basis": "manifest source_reviews and exclusion record",
                })
        excluded.append(node)

    return {
        "schema_version": 1,
        "scope": "compact provenance index; raw patterns and coordinates remain only in production-logical-palette.json",
        "inputs": {"manifest": "docs/production-logical-palette.json", "source_usage": "docs/production-source-usage-summary.json"},
        "source_usage": usage,
        "nodes": {"patterns": patterns, "reviews": reviews, "production": production, "excluded": excluded},
        "edges": edges,
    }


def comparison(manifest: dict[str, Any], baseline: dict[str, Any], geometry: dict[str, Any]) -> dict[str, Any]:
    models = model_metrics()
    blockstates = content_counts(files_under(ASSETS / "blockstates", ".json"))
    textures = content_counts(files_under(ASSETS / "textures", ".png"))
    resource_files = files_under(RESOURCES)
    stage_files = files_under(PRODUCTION_STAGE)
    excluded_counts = dict(sorted(Counter(row["status"] for row in manifest["excluded"]).items()))
    before_geometry = geometry["all_model_json_metrics"]
    after_geometry = current_geometry_units(models)
    return {
        "schema_version": 1,
        "inputs": {"baseline": "docs/production-baseline.json", "geometry_audit": "docs/production-geometry-audit.json", "manifest": "docs/production-logical-palette.json"},
        "notes": [
            "Before and after all-model metrics use the same JSON-file/element-geometry units.",
            "The baseline field unique_textured_uv_geometry is a historical logical-state mesh metric, not a count of model JSON geometries.",
            "JAR metrics are populated only when --jar names an existing build artifact.",
            "The original removal-plan path list was overwritten during apply; initial removed count 195143 is retained as task provenance, not recalculated here.",
        ],
        "registry": {"logical_production": len(manifest["objects"]), "helper": "PART", "registry_total_with_helper": len(manifest["objects"]) + 1},
        "manifest_exclusions": {"total": len(manifest["excluded"]), "by_status": excluded_counts},
        "resources_after": {
            "blockstates": blockstates, "models": models, "textures": textures,
            "production_stage_files": len(stage_files), "production_stage_bytes": sum(path.stat().st_size for path in stage_files),
            "source_resource_files": len(resource_files), "source_resource_bytes": sum(path.stat().st_size for path in resource_files),
            "assets_bytes": sum(path.stat().st_size for path in files_under(ASSETS)),
            "logical_bytes": sum(path.stat().st_size for path in files_under(LOGICAL)),
            "logical_state_meshes": logical_mesh_metrics(),
        },
        "same_unit_comparison": {
            "all_model_json_geometry": {"before": before_geometry, "after": after_geometry},
            "logical_state_meshes": {
                "before": {"unique_textured_uv_geometry": baseline["resource_counts"]["unique_textured_uv_geometry"], "duplicate_textured_uv_geometry_entries": baseline["resource_counts"]["duplicate_textured_uv_geometry_entries"]},
                "after": logical_mesh_metrics(),
            },
        },
        "jar": {"before_expected_bytes": baseline["scope"]["expected_jar_bytes"], "jar_after_bytes": None, "status": "PENDING_PRODUCTION_JAR_BUILD"},
    }


def write_json(path: Path, data: Any) -> None:
    path.write_text(json.dumps(data, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def review_reconciliation(manifest: dict[str, Any]) -> dict[str, Any]:
    with gzip.open(DOCS / 'production-authoring-inputs.json.gz', 'rt', encoding='utf8') as stream:
        frozen = json.load(stream)
    before = {block['id'] for block in frozen['definitions']['blocks']}
    excluded = {item['id']: item for item in manifest['excluded']}
    requested = {
        'C001/C009': (['C001', 'C009'], 'One complete tree, source/RNG variants remain states'),
        'C008': (['C008'], 'Four distinct statues; six source placements; orientation copies share ID'),
        'C1491': (['C1491'], 'Three independent graves'),
        'C1962': (['C1962'], 'Two independent sacks'),
        'C1979': (['C1979'], 'Five groups from manual red boxes, not source-carrier splits'),
        'C471': (['C471'], 'Two independent spires'),
        'C282': (['C282'], 'One double door; two outer-hinged leaves, no leaf items'),
        'C654': (['C654'], 'Separate A/A and B/B windows; raw source replacement selects A'),
    }
    rows = []
    for review, (reviews, meaning) in requested.items():
        prefixes = ['o_' + r.lower() for r in reviews]
        old = sorted(i for i in before if any(i == p or i.startswith(p + '_') for p in prefixes))
        if review == 'C001/C009':
            old += ['o_dead_tree_planter']
        final = sorted(item['id'] for item in manifest['objects'] if set(reviews) & set(item['source_reviews']))
        rows.append({'review': review, 'requested_final_objects': meaning, 'before_logical_ids': old,
                     'obsolete_ids': [i for i in old if i in excluded],
                     'duplicate_ids': [i for i in old if excluded.get(i, {}).get('status') == 'REMOVED_DUPLICATE'],
                     'final_production_ids': final,
                     'exclusion_reasons': {i: excluded[i]['status'] for i in old if i in excluded}})
    return {'schema_version': 1, 'authority': 'latest user correction supersedes prior Nightmare split where different', 'reviews': rows}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jar', type=Path)
    args = parser.parse_args()
    manifest = load(DOCS / "production-logical-palette.json")
    usage = load(DOCS / "production-source-usage-summary.json")
    baseline = load(DOCS / "production-baseline.json")
    geometry = load(DOCS / "production-geometry-audit.json")
    result = comparison(manifest, baseline, geometry)
    if args.jar:
        artifact = args.jar.resolve()
        result['jar'].update({
            'path': artifact.relative_to(ROOT.parent).as_posix(),
            'jar_after_bytes': artifact.stat().st_size,
            'sha256': sha256(artifact.read_bytes()),
            'status': 'BUILT',
        })
    write_json(DOCS / "production-resource-comparison.json", result)
    write_json(DOCS / "production-provenance-graph.json", provenance_graph(manifest, usage))
    write_json(DOCS / "production-review-reconciliation.json", review_reconciliation(manifest))


if __name__ == "__main__":
    main()
