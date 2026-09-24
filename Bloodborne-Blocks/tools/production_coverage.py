"""Independent required-family gate; it reads inputs only and never regenerates them.

``definitions.json`` is the Java runtime registry input.  This verifies that
resource boundary, not a live Fabric registry; dedicated-server GameTests are
the separate live-registry check.
"""
from __future__ import annotations

import argparse, gzip, json
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_REQUIRED = ROOT / "docs/required-production-families.json"
DEFAULT_RESOURCES = ROOT / "src/main/resources/bloodborne_blocks/logical"
DEFAULT_MANIFEST = ROOT / "docs/production-logical-palette.json"


def _read(path: Path) -> Any:
    return json.loads(path.read_text(encoding="utf8"))


def required_ids(required: dict[str, Any]) -> set[str]:
    return set(required["retained_reviewed_ids"]) | {row["id"] for row in required["restorations"]} | set(required["c003_production_ids"])


def _fail(errors: list[str], condition: bool, message: str) -> None:
    if not condition: errors.append(message)


def validate(*, required_path: Path = DEFAULT_REQUIRED, resources: Path = DEFAULT_RESOURCES,
             manifest_path: Path = DEFAULT_MANIFEST, gallery: Path | None = None) -> dict[str, Any]:
    """Return a report, raising ``ValueError`` if required production coverage is absent."""
    required = _read(required_path)
    wanted = required_ids(required)
    errors: list[str] = []
    _fail(errors, len(wanted) == 56, f"required family union must be 56, got {len(wanted)}")
    _fail(errors, required.get("generator_policy", "").startswith("Read-only"), "required input is not handwritten/read-only")
    contracts = {row["id"]: row for row in _read(resources / "contracts-v2.json")["families"]}
    definitions = {row["id"]: row for row in _read(resources / "definitions.json")["blocks"]}
    manifest = _read(manifest_path); manifest_ids = {row["id"] for row in manifest.get("objects", []) if row.get("status") == "PRODUCTION"}
    for ident in sorted(wanted):
        _fail(errors, ident in contracts, f"missing Contract V2 family: {ident}")
        _fail(errors, ident in definitions, f"missing runtime definitions.json registry input: {ident}")
        _fail(errors, ident in manifest_ids, f"missing production manifest family: {ident}")
    forbidden = set(required["forbidden_production_ids"])
    _fail(errors, not (forbidden & manifest_ids), "forbidden production IDs: " + ", ".join(sorted(forbidden & manifest_ids)))
    for retired, successor in required["successors"].items():
        _fail(errors, successor["status"] in required["approved_statuses"], f"unapproved successor status: {retired}")
        _fail(errors, set(successor["ids"]) <= wanted, f"incomplete successor: {retired}")
    # Frozen evidence is read only: it proves the restoration inputs were not
    # guessed from a new world scan.
    mapping_path = ROOT / required["source_mapping"]
    census_path = ROOT / required["source_census"]
    missing_path = ROOT / "docs/catalog-completeness-audit-20260924/missing-families.json"
    _fail(errors, mapping_path.is_file() and census_path.is_file() and missing_path.is_file(), "missing frozen mapping, census, or audit missing-families")
    mapping = census = None
    if mapping_path.is_file():
        with gzip.open(mapping_path, "rt", encoding="utf8") as stream: mapping = json.load(stream)
    if census_path.is_file():
        with gzip.open(census_path, "rt", encoding="utf8") as stream: census = json.load(stream)
    if missing_path.is_file():
        missing = _read(missing_path)
        _fail(errors, bool(missing.get("historical_logical_families")), "audit missing-families has no historical evidence")
        deferred = required.get("remaining_audited_filtered_families", {})
        _fail(errors, deferred.get("status") == "INTENTIONALLY_DEFERRED", "remaining audited families need explicit deferred status")
        by_id = {row["id"]: row for row in missing["historical_logical_families"]}
        exclusions={row['id']:row for row in manifest.get('excluded',[])}
        for ident, row in by_id.items():
            if ident in manifest_ids: continue
            row=exclusions.get(ident,{})
            _fail(errors, row.get("status") in required["approved_statuses"] and bool(row.get("reason")),
                  f"historical family silently excluded or unapproved: {ident}")
    census_states: dict[str, list[dict[str, str]]] = {}
    if census is not None:
        for row in census["states"]: census_states.setdefault(row["id"], []).append(row.get("properties", {}))
    for restored in required["restorations"]:
        family = contracts.get(restored["id"], {})
        patterns = [pattern for state in family.get("states", {}).values() for pattern in state.get("migration_source_pattern", [])]
        _fail(errors, bool(patterns), f"restoration lacks raw source patterns: {restored['id']}")
        carriers = set(restored["source_carriers"])
        for carrier in carriers:
            _fail(errors, mapping is not None and carrier.removeprefix("minecraft:") in mapping["v2\\migration.json"],
                  f"frozen mapping lacks restoration carrier: {restored['id']} {carrier}")
            # Zero-use alternatives require a per-carrier approved reason;
            # another observed carrier must not silently excuse this one.
            present = carrier in census_states
            _fail(errors, present or bool(restored.get("unused_carriers", {}).get(carrier)),
                  f"cached census lacks restoration carrier: {restored['id']} {carrier}")
        for pattern in patterns:
            for component in pattern.get("components", []):
                _fail(errors, component.get("id", "").startswith("minecraft:"), f"non-vanilla pattern component: {restored['id']}")
                observed = census_states.get(component.get("id"), [])
                if observed:
                    allowed = {key: {props[key] for props in observed if key in props} for key in set().union(*(set(props) for props in observed))}
                    _fail(errors, set(component.get("properties", {})) <= set(allowed),
                          f"pattern property absent from cached census: {restored['id']} {component['id']}")
        _fail(errors, any(component.get("id") in carriers for pattern in patterns for component in pattern["components"]),
              f"restoration carrier absent from patterns: {restored['id']}")
        seen: dict[str, str] = {}
        for state, row in family.get("states", {}).items():
            for pattern in row.get("migration_source_pattern", []):
                if pattern.get("split_transaction"): continue
                signature = json.dumps({"components": pattern.get("components"), "guards": pattern.get("variant_guards", [])}, sort_keys=True)
                if signature in seen and seen[signature] != state: errors.append(f"duplicate raw pattern across target states: {restored['id']} {seen[signature]} / {state}")
                seen[signature] = state
    c003 = _read(ROOT / required["c003_decision"])
    _fail(errors, c003.get("migration_policy") == "INDEPENDENT_SOURCE_CELLS_AT_ORIGINAL_POSITIONS", "C003 migration policy changed")
    for ident in required["c003_production_ids"]:
        patterns = [pattern for state in contracts.get(ident, {}).get("states", {}).values() for pattern in state.get("migration_source_pattern", [])]
        _fail(errors, bool(patterns) and all(len(pattern.get("components", [])) == 1 and pattern["components"][0].get("offset") == [0, 0, 0] and not pattern.get("split_transaction") for pattern in patterns), f"C003 successor needs independent single-cell pattern: {ident}")
        _fail(errors, all(component.get("id") != "minecraft:o_c003" for pattern in patterns for component in pattern["components"]), f"C003 successor has obsolete raw source: {ident}")
    gallery_ids: set[str] | None = None
    if gallery is not None:
        metadata = _read(gallery / "production-gallery.json")
        rows = metadata.get("positions", []); gallery_ids = {row.get("id") for row in rows}
        _fail(errors, wanted <= gallery_ids, "gallery missing required IDs: " + ", ".join(sorted(wanted - gallery_ids)))
        _fail(errors, len({row.get("specimen_id", row.get("id")) for row in rows}) == len(rows), "gallery specimen IDs are not distinct")
        for ident in wanted:
            definition = definitions.get(ident, {})
            if definition.get("behavior") in {"door", "shutter", "gate"}:
                states = {row.get("properties", {}).get("open") for row in rows if row.get("id") == ident}
                _fail(errors, {"false", "true"} <= states, f"gallery lacks open/closed specimen: {ident}")
    links=[]
    for ident in sorted(wanted):
        f=contracts.get(ident,{})
        patterns=[{'target_state':k,'components':p['components'],'variant_guards':p.get('variant_guards',[])} for k,s in f.get('states',{}).items() for p in s.get('migration_source_pattern',[])]
        carrier_ids=sorted({c['id'] for p in patterns for c in p['components']})
        links.append({'required_logical_family':ident,'contract_v2':ident in contracts,'registry_id':'bloodborne_blocks:'+ident if ident in definitions else None,
            'source_assemblies':patterns,'source_carriers':[{'id':c,'cached_cells':sum(d['count'] for r in census['states'] if r['id']==c for d in r['dimensions'].values()),'status':'USED_IN_SOURCE' if c in census_states else 'NOT_USED_IN_SOURCE'} for c in carrier_ids],
            'migration_redirect':f.get('migration_redirect'),
            'gallery_specimens':[r.get('specimen_id',r['id']) for r in rows if r['id']==ident] if gallery is not None else ['canonical planned; artifact not yet validated']})
    _fail(errors, set(definitions)==wanted and set(contracts)==wanted and manifest_ids==wanted,'unexpected or missing production runtime IDs')
    report = {"result": "PASS" if not errors else "FAIL", "required_count": len(wanted), 'source_world_sha256':required['source_world_sha256'],'source_coverage':links,'exclusions':manifest.get('excluded',[]), "runtime_registry_boundary": "definitions.json feeds Java registry; live registry requires GameTests",
              "manifest_count": len(manifest_ids), "gallery_checked": gallery is not None, "gallery_count": None if gallery_ids is None else len(gallery_ids), "errors": errors}
    if errors: raise ValueError(json.dumps(report, ensure_ascii=False))
    return report


if __name__ == "__main__":
    parser = argparse.ArgumentParser(); parser.add_argument("--required", type=Path, default=DEFAULT_REQUIRED); parser.add_argument("--resources", type=Path, default=DEFAULT_RESOURCES)
    parser.add_argument("--manifest", type=Path, default=DEFAULT_MANIFEST); parser.add_argument("--gallery", type=Path); parser.add_argument("--output", type=Path)
    args = parser.parse_args(); report = validate(required_path=args.required, resources=args.resources, manifest_path=args.manifest, gallery=args.gallery)
    if args.output: args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf8")
    print(json.dumps({k:v for k,v in report.items() if k not in ('source_coverage','exclusions')}, ensure_ascii=False))
