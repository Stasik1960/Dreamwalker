"""Build the immutable-resource creative-catalog equivalence map.

Only catalog presentation is described here.  The generator never changes a
definition, mesh, geometry, physical footprint, or world resource.  An art
entry is an explicit ``variant x visual`` selection; all of its non-art states
must have the same complete evidence before it can redirect to another entry.
"""
from __future__ import annotations

import argparse
import gzip
import hashlib
import json
from collections import defaultdict
from pathlib import Path
from typing import Any

from logical_contract_v2 import load_contracts
from production_fingerprints import aliases, canonical
from creative_model_evidence import ModelEvidence

ROOT = Path(__file__).resolve().parents[1]
RESOURCES = ROOT / "src/main/resources/bloodborne_blocks"
OUTPUT = RESOURCES / "creative-equivalence.json"
REPORT = ROOT / "docs/catalog-city-continuation/catalog-equivalence-report.json.gz"
ART_AXES = ("variant", "visual")
TEXT_HASH_POLICY = "Source JSON/mcmeta hashes normalize CRLF to LF; binary assets retain exact bytes."
APPROVED_LOGICAL_CROSS_ID = {frozenset(pair) for pair in (("o_c1979_1", "o_c1979_2"), ("o_c471_a", "o_c471_b"), ("o_c1979_4", "o_c1979_5"))}


def read(path: Path) -> Any:
    raw = path.read_bytes()
    return json.loads(gzip.decompress(raw) if path.suffix == ".gz" else raw)


def write(path: Path, value: Any) -> None:
    data = canonical(value)
    path.parent.mkdir(parents=True, exist_ok=True)
    if path.suffix == ".gz":
        with path.open("wb") as raw, gzip.GzipFile(filename="", mode="wb", fileobj=raw, mtime=0) as stream:
            stream.write(data)
    else:
        path.write_bytes(data + b"\n")


def sha(value: Any) -> str:
    return hashlib.sha256(canonical(value)).hexdigest()


def state_props(key: str) -> dict[str, str]:
    return dict(part.split("=", 1) for part in key.split(",") if part)


def art_key(ident: str, props: dict[str, str]) -> str:
    # Both axes are present in the runtime lookup key; an absent axis is an
    # explicit empty value so native/null-model IDs cannot accidentally merge.
    return "|".join((ident, *(f"{axis}={props.get(axis, '')}" for axis in ART_AXES)))


def logical_behavior(definition: dict[str, Any]) -> dict[str, Any]:
    return {key: value for key, value in definition.items()
            if key not in {"id", "models", "visual_models", "states", "default", "creative"}}


def file_sha(path: Path) -> str | None:
    if not path.is_file():
        return None
    data = path.read_bytes()
    return hashlib.sha256(data.replace(b'\r\n', b'\n') if path.suffix in {'.json', '.mcmeta'} else data).hexdigest()


def physical_state(geometry: dict[str, Any] | None, ident: str, state: str) -> Any:
    if not geometry:
        return None
    value = geometry.get("blocks", {}).get(ident, {}).get("states", {}).get(state)
    seen: set[str] = set()
    while isinstance(value, dict) and set(value) == {"ref"}:
        reference = value["ref"]
        if reference in seen:
            raise ValueError(f"geometry profile cycle: {reference}")
        seen.add(reference)
        value = geometry.get("profiles", {}).get(reference)
    return value


def collect() -> tuple[dict[str, Any], list[dict[str, Any]], dict[str, str]]:
    logical = RESOURCES / "logical"
    city = RESOURCES / "city"
    logical_data, city_data = read(logical / "definitions.json"), read(city / "definitions.json")
    logical_defs = {row["id"]: row for row in logical_data["blocks"]}
    city_defs = {row["id"]: row for row in city_data["blocks"]}
    contracts, _ = load_contracts(logical)
    contracts_by_id = {row["id"]: row for row in contracts["families"]}
    logical_meshes = read(logical / "meshes.json.gz")
    city_meshes = read(city / "meshes.json.gz")
    # The reviewed dynamic wall owns its meshes in the dedicated owner sidecar.
    city_meshes = {**city_meshes, **read(city / "owner-meshes.json.gz")}
    logical_geometry = read(logical / "geometry.json")
    city_geometry = read(city / "geometry.json")
    texture_aliases = aliases(ROOT)
    selected = [("logical", row) for row in logical_defs.values() if row.get("creative")]
    # The technical tab contains every city registry ID, including native
    # compatibility definitions.  Native models have no proof schema and are
    # deliberately distinct rather than guessed equivalent.
    selected += [("city", row) for row in city_defs.values()]
    selected.sort(key=lambda row: row[1]["id"])
    all_meshes = {**logical_meshes, **city_meshes}
    resolvers = {scope: ModelEvidence(ROOT / "src/main/resources", all_meshes, texture_aliases, data.get("emissive_textures"))
                 for scope, data in (("logical", logical_data), ("city", city_data))}
    entries: list[dict[str, Any]] = []
    exclusions = {"source_depth": [], "service_architecture_part": [{"id": "architecture_part", "reason": "service block has no BlockItem"}]}
    visual_proofs: dict[tuple, str] = {}
    for scope, definition in selected:
        ident = definition["id"]
        resolver = resolvers[scope]
        if definition.get("models") is None:
            key = art_key(ident, {})
            evidence = {"native_model_absent": True, "id": ident, "behavior": logical_behavior(definition)}
            entries.append({"key": key, "id": ident, "art": {axis: "" for axis in ART_AXES}, "states": sorted(definition.get("states", {})),
                            "proof": sha(evidence), "evidence": evidence, "scope": scope, "default": definition.get("default", {}), "excluded": None})
            continue
        geometry = logical_geometry if scope == "logical" else city_geometry
        contract = contracts_by_id.get(ident)
        grouped: dict[str, list[str]] = defaultdict(list)
        for state in sorted(definition["models"]):
            grouped[art_key(ident, state_props(state))].append(state)
        for key, states in sorted(grouped.items()):
            props = state_props(states[0])
            forced = definition.get("placement_properties", {})
            if any(forced.get(axis) is not None and forced[axis] != props.get(axis) for axis in ART_AXES):
                exclusions["source_depth"].append({"id": ident, "art": {axis: props.get(axis, "") for axis in ART_AXES},
                                                    "state_keys": states, "reason": "placement_properties forces art axis"})
                continue
            concrete = []
            for state in states:
                mesh_id = definition["models"][state]
                contract_state = contract["states"].get(state) if contract else None
                visual_model = definition.get("visual_models", {}).get(state)
                emissive = bool(definition.get("emissive"))
                reorder = definition.get("layer") != "translucent" and any(ident in pair for pair in APPROVED_LOGICAL_CROSS_ID)
                visual_key = (scope, visual_model, mesh_id, emissive, reorder)
                if visual_key not in visual_proofs:
                    visual_proofs[visual_key] = sha(resolver.visual(visual_model, mesh_id, emissive, reorder))
                # The art value itself names the candidate and must not be
                # folded back into its proof.  Concrete source state keys are
                # retained in the report entry, while every service state is
                # still compared through the remaining fields below.
                concrete.append({"visual_model_sha256": visual_proofs[visual_key],
                                 "geometry": physical_state(geometry, ident, state),
                                 "physical": contract_state.get("physical_footprint") if contract_state else None,
                                 "collision": contract_state.get("collision_footprint") if contract_state else None,
                                 "outline": contract_state.get("selection_footprint") if contract_state else None,
                                 "render": ({key: value for key, value in contract_state.get("render_mesh", {}).items() if key != "id"}
                                            if contract_state else None),
                                 "luminance": definition["states"].get(state, [None, None, None])[2],
                                 "service": {axis: value for axis, value in sorted(state_props(state).items()) if axis not in ART_AXES}})
            evidence = {"behavior": logical_behavior(definition), "placement": contract.get("placement_policy") if contract else None,
                        "semantic": definition.get("semantic"), "source": definition.get("source"), "states": concrete}
            entries.append({"key": key, "id": ident, "art": {axis: props.get(axis, "") for axis in ART_AXES},
                            "states": states, "proof": sha(evidence), "evidence": evidence,
                            "scope": scope, "default": definition.get("default", {}), "excluded": None})
    source = {"logical": {name: file_sha(logical / name) for name in ("definitions.json", "geometry.json", "physical-footprints.json", "contracts-v2.json", "meshes.json.gz")},
              "city": {name: file_sha(city / name) for name in ("definitions.json", "geometry.json", "meshes.json.gz", "owner-meshes.json.gz")},
              "texture_aliases": sha(texture_aliases), "appearance_assets": dict(sorted({key: value for resolver in resolvers.values() for key, value in resolver.source_hashes.items()}.items())),
              "textHashPolicy": TEXT_HASH_POLICY}
    return source, entries, texture_aliases, exclusions


def build() -> tuple[dict[str, Any], dict[str, Any]]:
    source, entries, _, exclusions = collect()
    buckets: dict[str, list[dict[str, Any]]] = defaultdict(list)
    for entry in entries:
        buckets[entry["proof"]].append(entry)
    redirects: dict[str, str] = {}
    reports = []
    for proof, group in sorted(buckets.items()):
        # Logical functional/id-specific behavior is part of the evidence, so
        # only complete equality reaches this point.  Prefer main, base, the
        # definition default art, then stable registry ID.
        def rank(row: dict[str, Any]) -> tuple[Any, ...]:
            default = row["default"]
            return (0 if row["scope"] == "logical" else 1, 0 if row["art"].get("visual") == "base" else 1,
                    0 if all(row["art"].get(axis, "") == str(default.get(axis, "")) for axis in ART_AXES) else 1, row["id"], row["key"])
        for row in sorted(group, key=lambda item: item["key"]):
            compatible = [candidate for candidate in group if candidate["scope"] == row["scope"] and
                          (candidate["scope"] == "city" or candidate["id"] == row["id"] or
                           frozenset((candidate["id"], row["id"])) in APPROVED_LOGICAL_CROSS_ID)]
            canonical_row = min(compatible, key=rank)
            if row is canonical_row:
                continue
            redirects[row["key"]] = canonical_row["key"]
            reports.append({"source": {"id": row["id"], "art": row["art"], "state_keys": row["states"]},
                            "canonical": {"id": canonical_row["id"], "art": canonical_row["art"], "key": canonical_row["key"]},
                            "reason": "complete variant×visual evidence equality", "proof_sha256": proof})
    visible = len(entries) - len(redirects)
    summary = {"registryIds": len({entry["id"] for entry in entries}), "candidateArt": len(entries), "visibleEntries": visible,
               "hiddenEquivalent": len(redirects), "redirects": len(redirects), "excluded": {key: len(value) for key, value in exclusions.items()}}
    runtime = {"schemaVersion": 1, "redirects": dict(sorted(redirects.items())), "summary": summary}
    report = {"schemaVersion": 1, "sourceFingerprints": source, "summary": summary,
              "redirects": reports, "exclusions": exclusions,
              "limitations": ["All registered logical and city catalog candidates are scoped; city proof is bounded to exact hashes.",
                              "Native definitions with models=null remain distinct and receive no redirect.",
                              "Dynamic building_stone_brick_wall is never compared with static city owner aliases."]}
    return runtime, report


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="fail if generated files are stale")
    args = parser.parse_args()
    runtime, report = build()
    expected_runtime = canonical(runtime) + b"\n"
    expected_report = canonical(report)
    actual_report = gzip.decompress(REPORT.read_bytes()) if REPORT.exists() else b""
    if args.check:
        if not OUTPUT.is_file() or OUTPUT.read_bytes() != expected_runtime or actual_report != expected_report:
            raise SystemExit("creative equivalence generated files are stale; run build_creative_equivalence.py")
    else:
        OUTPUT.write_bytes(expected_runtime)
        write(REPORT, report)
    print(json.dumps(runtime["summary"], sort_keys=True))


if __name__ == "__main__":
    main()
