"""Recommendation-only exact duplicate evidence from the immutable Agony baseline."""
from __future__ import annotations

import argparse
import hashlib
import json
from collections import defaultdict
from pathlib import Path
from typing import Any

from production_fingerprints import DEFAULT_BASELINE, ROOT, canonical, collect, read

DEFAULT_OUTPUT = ROOT / "docs/agony-patch/exact-duplicates.json"
DEFAULT_CURRENT_OUTPUT = ROOT / "docs/agony-patch/current-exact-duplicates.json"


def digest(value: Any) -> str:
    return hashlib.sha256(canonical(value)).hexdigest()


def rotated(x: float, z: float, turns: int) -> tuple[float, float]:
    return ((x, z), (1 - z, x), (1 - x, 1 - z), (z, 1 - x))[turns]


def cyclic(vertices: list[tuple[float, ...]]) -> tuple[tuple[float, ...], ...]:
    return min(tuple(vertices[index:] + vertices[:index]) for index in range(len(vertices)))


def mesh_signature(evidence: dict[str, Any], *, normalize_translation: bool = True) -> dict[str, Any]:
    """Canonicalize cardinal yaw only; UVs and texture-content hashes remain exact."""
    texture_hashes = {row["raw"]: row["content"]["sha256"] for row in evidence["textures"]}
    polygons = [row for row in evidence["mesh"].get("polygons", []) if "texture" in row]
    candidates = []
    for turns in range(4):
        transformed = []
        coordinates = []
        for polygon in polygons:
            vertices = []
            for vertex in polygon["vertices"]:
                x, z = rotated(float(vertex[0]), float(vertex[2]), turns)
                coordinates.append((x, float(vertex[1]), z))
                vertices.append((round(x, 9), round(float(vertex[1]), 9), round(z, 9), *tuple(round(float(value), 9) for value in vertex[3:])))
            transformed.append((texture_hashes.get(polygon["texture"]), cyclic(vertices)))
        translation = (0.0, 0.0, 0.0)
        if normalize_translation and coordinates:
            translation = tuple(round(min(point[index] for point in coordinates), 9) for index in range(3))
            shifted = []
            for texture, vertices in transformed:
                shifted.append((texture, tuple(tuple(round(vertex[index] - translation[index], 9) if index < 3 else vertex[index]
                                                       for index in range(len(vertex))) for vertex in vertices)))
            transformed = shifted
        payload = sorted((texture, vertices) for texture, vertices in transformed)
        candidates.append((canonical(payload), turns, translation, payload))
    encoded, turns, translation, _payload = min(candidates, key=lambda row: row[0])
    return {"signature": hashlib.sha256(encoded).hexdigest(), "canonical_yaw_quarter_turns": turns,
            "translation_normalized": normalize_translation, "translation": list(translation),
            "textured_polygon_count": len(polygons)}


def behavior_schema(core: dict[str, Any]) -> dict[str, Any]:
    definition = core["definition"]
    excluded = {"id", "models", "visual_models", "states"}
    return {key: value for key, value in definition.items() if key not in excluded}


def state_rows(families: dict[str, Any]) -> list[dict[str, Any]]:
    rows = []
    for ident, record in sorted(families.items()):
        core = record["core"]
        schema = behavior_schema(core)
        for state, evidence in sorted(core["state_evidence"].items()):
            rows.append({"id": ident, "state": state, "behavior_schema": schema,
                         "evidence": mesh_signature(evidence), "visual_slot": state.split("visual=")[-1] if "visual=" in state else None})
    return rows


def exact_recommendations(rows: list[dict[str, Any]]) -> list[dict[str, Any]]:
    indexed: dict[str, list[dict[str, Any]]] = defaultdict(list)
    for row in rows:
        indexed[row["evidence"]["signature"]].append(row)
    recommendations = []
    for signature, matches in sorted(indexed.items()):
        families = sorted({row["id"] for row in matches})
        if len(families) < 2:
            continue
        by_behavior: dict[str, list[dict[str, Any]]] = defaultdict(list)
        for row in matches:
            by_behavior[digest(row["behavior_schema"])].append(row)
        for schema_hash, compatible in sorted(by_behavior.items()):
            compatible_families = sorted({row["id"] for row in compatible})
            if len(compatible_families) < 2:
                continue
            recommendations.append({"kind": "EXACT_GEOMETRY_AND_BEHAVIOR_SCHEMA", "recommendation": "manual review only; never merge automatically",
                                    "signature": signature, "behavior_schema_sha256": schema_hash,
                                    "families": compatible_families,
                                    "states": [{key: row[key] for key in ("id", "state", "visual_slot", "evidence")} for row in compatible]})
    return recommendations


def vertex_error(left: dict[str, Any], right: dict[str, Any]) -> dict[str, Any]:
    def values(evidence: dict[str, Any]):
        hashes = {row["raw"]: row["content"]["sha256"] for row in evidence["textures"]}
        return sorted((hashes.get(polygon["texture"]), tuple(round(float(value), 9) for vertex in polygon["vertices"] for value in vertex))
                      for polygon in evidence["mesh"].get("polygons", []) if "texture" in polygon)
    a, b = values(left), values(right)
    compatible = len(a) == len(b) and [row[0] for row in a] == [row[0] for row in b]
    # Even structurally different book variants get a bounded numeric diagnostic.
    # The result remains explicitly non-comparable, so this never becomes an
    # approximate identity or a merge recommendation.
    errors = [abs(x - y) for (_, first), (_, second) in zip(a, b)
              for x, y in zip(first, second)]
    result = {"comparable": compatible, "min_error": min(errors, default=0.0), "max_error": max(errors, default=0.0),
              "method": "sorted raw textured vertices and UVs; diagnostic only, no approximate identity"}
    if not compatible:
        result["reason"] = "textured polygon count or texture content differs; errors cover only aligned sorted values"
    return result


def representative(core: dict[str, Any], contains: str = "visual=base") -> dict[str, Any]:
    key = next((key for key in sorted(core["state_evidence"]) if contains in key and "facing=north" in key),
               next(iter(sorted(core["state_evidence"]))))
    return {"state": key, "evidence": core["state_evidence"][key]}


def near_comparisons(families: dict[str, Any]) -> list[dict[str, Any]]:
    comparisons = []
    books = families.get("o_books", {}).get("core")
    if books:
        variants = [key for key in sorted(books["state_evidence"]) if "facing=north" in key and "visual=base" in key]
        for first, second in zip(variants, variants[1:]):
            comparisons.append({"kind": "BOOKS_VARIANT_NEAR_TRANSLATION", "recommendation": "not an identity; never merge automatically",
                                "left": first, "right": second, "error": vertex_error(books["state_evidence"][first], books["state_evidence"][second])})
    if "o_bench" in families and "o_bench_rotate" in families:
        left, right = representative(families["o_bench"]["core"]), representative(families["o_bench_rotate"]["core"])
        comparisons.append({"kind": "BENCH_45_DEGREE_COMPARISON", "recommendation": "not an identity; never merge automatically",
                            "left": left["state"], "right": right["state"], "error": vertex_error(left["evidence"], right["evidence"])})
    return comparisons


def tree_report(families: dict[str, Any], rows: list[dict[str, Any]]) -> dict[str, Any]:
    trees = [row for row in rows if row["id"] == "o_c001"]
    variants: dict[str, set[str]] = defaultdict(set)
    for row in trees:
        part = next((piece for piece in row["state"].split(",") if piece.startswith("variant=")), "variant=unknown")
        variants[part].add(row["evidence"]["signature"])
    return {"family": "o_c001", "variant_count": len(variants), "canonical_yaw_exact_signatures": {key: sorted(value) for key, value in sorted(variants.items())},
            "reducible_by_cardinal_yaw_exact": len({tuple(sorted(value)) for value in variants.values()}) < len(variants),
            "recommendation": "diagnostic only; do not edit tree variants"}


def analyse_fingerprints(fingerprints: dict[str, Any], *, input_kind: str, source_commit: str | None,
                         baseline_sha256: str | None) -> dict[str, Any]:
    """Analyse supplied fingerprints without writing or recapturing any inputs."""
    families = fingerprints["families"]
    rows = state_rows(families)
    return {"schema_version": 1, "input": input_kind, "source_commit": source_commit, "baseline_sha256": baseline_sha256,
            "family_count": len(families), "state_count": len(rows),
            "normalization": {"cardinal_yaw": "exact rotations about (0.5, 0.5) in XZ", "translation": "minimum XYZ translation removed and explicitly recorded", "approximation": "none"},
            "exact_recommendations": exact_recommendations(rows), "near_comparisons": near_comparisons(families), "trees": tree_report(families, rows),
            "note": "Read-only evidence and recommendations only. It performs no production edits, merges, or fingerprint capture."}


def analyse(baseline: dict[str, Any]) -> dict[str, Any]:
    if baseline.get("schema_version") != 1:
        raise ValueError("unsupported fingerprint baseline schema")
    return analyse_fingerprints(baseline["fingerprints"], input_kind="immutable_baseline",
                                source_commit=baseline["source_commit"], baseline_sha256=baseline["sha256"])


def analyse_current(root: Path = ROOT) -> dict[str, Any]:
    """Read the current production palette through collect; never alter the baseline."""
    return analyse_fingerprints(collect(root), input_kind="current_production_palette",
                                source_commit=None, baseline_sha256=None)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--baseline", type=Path, default=DEFAULT_BASELINE)
    parser.add_argument("--current", action="store_true", help="analyse current production inputs read-only")
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    output = args.output or (DEFAULT_CURRENT_OUTPUT if args.current else DEFAULT_OUTPUT)
    if output.exists():
        raise FileExistsError(f"diagnostic already exists: {output}")
    output.parent.mkdir(parents=True, exist_ok=True)
    result = analyse_current() if args.current else analyse(read(args.baseline))
    output.write_bytes(canonical(result) + b"\n")
    print(json.dumps({"family_count": result["family_count"], "state_count": result["state_count"], "recommendations": len(result["exact_recommendations"])}, ensure_ascii=False))


if __name__ == "__main__":
    main()
