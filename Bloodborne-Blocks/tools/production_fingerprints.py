"""Immutable, per-family production fingerprints for the Agony patch.

This tool reads production inputs only.  ``capture`` deliberately refuses to
replace a baseline; ``verify`` accepts changes only through an explicit
allowlist file supplied by the caller.
"""
from __future__ import annotations

import argparse
import gzip
import hashlib
import json
import subprocess
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_BASELINE = ROOT / "docs/agony-patch/baseline-fingerprints.json.gz"


def canonical(value: Any) -> bytes:
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode("utf-8")


def read(path: Path) -> Any:
    data = path.read_bytes()
    return json.loads(gzip.decompress(data) if path.suffix == ".gz" else data)


def write(path: Path, value: Any) -> None:
    data = canonical(value)
    if path.suffix == ".gz":
        with path.open("wb") as raw:
            with gzip.GzipFile(filename="", mode="wb", fileobj=raw, mtime=0) as stream:
                stream.write(data)
    else:
        path.write_bytes(data + b"\n")


def digest_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def file_hash(path: Path) -> str | None:
    if not path.is_file():
        return None
    data = path.read_bytes()
    # Git may check text out as CRLF on Windows; fingerprints describe the
    # repository payload, not the checkout's platform-specific line endings.
    if path.suffix in {".json", ".java"}:
        data = data.replace(b"\r\n", b"\n")
    return digest_bytes(data)


def required_ids(root: Path) -> set[str]:
    required = read(root / "docs/required-production-families.json")
    return (set(required["retained_reviewed_ids"]) | {row["id"] for row in required["restorations"]}
            | set(required["c003_production_ids"]))


def retired_ids(root: Path) -> set[str]:
    required = read(root / "docs/required-production-families.json")
    return set(required.get("forbidden_production_ids", [])) | set(required.get("successors", {}))


def aliases(root: Path) -> dict[str, str]:
    return read(root / "docs/production-resource-pruning.json").get("texture_aliases", {})


def resolve_alias(texture: str, mapping: dict[str, str]) -> str:
    seen: set[str] = set()
    while texture in mapping:
        if texture in seen:
            raise ValueError(f"texture alias cycle: {texture}")
        seen.add(texture)
        texture = mapping[texture]
    return texture


def texture_hash(root: Path, texture: str) -> dict[str, str | None]:
    if ":" not in texture:
        return {"id": texture, "path": None, "sha256": None}
    namespace, name = texture.split(":", 1)
    path = root / "src/main/resources/assets" / namespace / "textures" / f"{name}.png"
    return {"id": texture, "path": path.relative_to(root).as_posix(), "sha256": file_hash(path)}


def model_hash(root: Path, model: str) -> dict[str, str | None]:
    if ":" not in model:
        return {"id": model, "path": None, "sha256": None}
    namespace, name = model.split(":", 1)
    path = root / "src/main/resources/assets" / namespace / "models" / f"{name}.json"
    return {"id": model, "path": path.relative_to(root).as_posix(), "sha256": file_hash(path)}


def resource_hashes(root: Path, ident: str, visual_models: dict[str, str]) -> dict[str, Any]:
    asset = root / "src/main/resources/assets/bloodborne_blocks"
    data = root / "src/main/resources/data/bloodborne_blocks"
    return {
        "blockstate": file_hash(asset / "blockstates" / f"{ident}.json"),
        "item": file_hash(asset / "models/item" / f"{ident}.json"),
        "loot": file_hash(data / "loot_tables/blocks" / f"{ident}.json"),
        "visual_models": {state: model_hash(root, model) for state, model in sorted(visual_models.items())},
    }


def runtime_java_hashes(root: Path) -> dict[str, str]:
    base = root / "src/main/java"
    return {path.relative_to(root).as_posix(): file_hash(path)
            for path in sorted(base.rglob("*.java"))}


def display_names(root: Path, ident: str) -> dict[str, str | None]:
    key = f"block.bloodborne_blocks.{ident}"
    result: dict[str, str | None] = {}
    for path in sorted((root / "src/main/resources/assets/bloodborne_blocks/lang").glob("*.json")):
        result[path.stem] = read(path).get(key)
    return result


def family_record(root: Path, ident: str, definition: dict[str, Any], contract: dict[str, Any],
                  geometry: dict[str, Any], meshes: dict[str, Any], texture_aliases: dict[str, str]) -> dict[str, Any]:
    state_evidence: dict[str, Any] = {}
    for state, mesh_id in sorted(definition["models"].items()):
        mesh = meshes[mesh_id]
        textures = sorted({polygon["texture"] for polygon in mesh.get("polygons", []) if "texture" in polygon})
        state_evidence[state] = {
            "mesh_id": mesh_id,
            "mesh": mesh,
            "render_transform": contract["states"][state].get("render_mesh"),
            "geometry": geometry["states"].get(state),
            "visual_model": definition.get("visual_models", {}).get(state),
            "textures": [{"raw": texture, "resolved": resolve_alias(texture, texture_aliases),
                          "content": texture_hash(root, resolve_alias(texture, texture_aliases))} for texture in textures],
        }
    return {
        "core": {
            "id": ident,
            "definition": definition,
            "contract": contract,
            "geometry": geometry,
            "state_evidence": state_evidence,
            "resources": resource_hashes(root, ident, definition.get("visual_models", {})),
        },
        "display_names": display_names(root, ident),
    }


def source_commit(root: Path) -> str:
    return subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=root, text=True).strip()


def collect(root: Path = ROOT) -> dict[str, Any]:
    logical = root / "src/main/resources/bloodborne_blocks/logical"
    definitions = {row["id"]: row for row in read(logical / "definitions.json")["blocks"]}
    contracts = {row["id"]: row for row in read(logical / "contracts-v2.json")["families"]}
    geometry = read(logical / "geometry.json")["blocks"]
    meshes = read(logical / "meshes.json.gz")
    wanted = required_ids(root)
    if set(definitions) != wanted or set(contracts) != wanted or set(geometry) != wanted:
        raise ValueError(f"production inputs do not contain exactly the required family IDs ({len(wanted)})")
    missing_meshes = {mesh for row in definitions.values() for mesh in row["models"].values()} - set(meshes)
    if missing_meshes:
        raise ValueError("definitions reference missing meshes: " + ", ".join(sorted(missing_meshes)))
    mapping = aliases(root)
    return {
        "family_ids": sorted(wanted),
        "families": {ident: family_record(root, ident, definitions[ident], contracts[ident], geometry[ident], meshes, mapping)
                     for ident in sorted(wanted)},
        "runtime_java_sha256": runtime_java_hashes(root),
    }


def capture(*, root: Path = ROOT, output: Path = DEFAULT_BASELINE) -> dict[str, Any]:
    if output.exists():
        raise FileExistsError(f"immutable fingerprint baseline already exists: {output}")
    output.parent.mkdir(parents=True, exist_ok=True)
    baseline = {"schema_version": 1, "source_commit": source_commit(root), "fingerprints": collect(root)}
    baseline["sha256"] = digest_bytes(canonical(baseline))
    write(output, baseline)
    return baseline


def allowlist(path: Path | None, family_ids: set[str], retired: set[str]) -> tuple[set[str], set[str]]:
    if path is None:
        return set(), set()
    data = read(path)
    if set(data) - {"families", "display_names"}:
        raise ValueError("allowlist supports only families and display_names")
    families = set(data.get("families", [])); labels = set(data.get("display_names", []))
    if (not all(isinstance(value, str) for value in families | labels)
            or not families <= family_ids | retired or not labels <= family_ids | retired):
        raise ValueError("allowlist contains an unknown family ID")
    return families, labels


def verify(*, root: Path = ROOT, baseline_path: Path = DEFAULT_BASELINE,
           allowlist_path: Path | None = None) -> dict[str, Any]:
    baseline = read(baseline_path)
    if baseline.get("schema_version") != 1:
        raise ValueError("unsupported fingerprint baseline schema")
    payload = {key: value for key, value in baseline.items() if key != "sha256"}
    if baseline.get("sha256") != digest_bytes(canonical(payload)):
        raise ValueError("fingerprint baseline checksum does not match its contents")
    expected = baseline.get("fingerprints", {})
    actual = collect(root)
    expected_ids = set(expected.get("family_ids", [])); actual_ids = set(actual["family_ids"])
    removed, added = expected_ids - actual_ids, actual_ids - expected_ids
    allowed_families, allowed_labels = allowlist(allowlist_path, actual_ids, retired_ids(root))
    # A baseline family may disappear only when the caller explicitly accepts
    # that named, documented retirement.  Never infer approval from the
    # current palette and never allow additions through this mechanism.
    unapproved_removed = removed - allowed_families
    if unapproved_removed or added:
        raise ValueError(f"family IDs changed; removed={sorted(unapproved_removed)}, added={sorted(added)}")
    expected_runtime = expected.get("runtime_java_sha256", {})
    actual_runtime = actual["runtime_java_sha256"]
    runtime_changed = sorted({*expected_runtime, *actual_runtime}
                             - {path for path in expected_runtime if expected_runtime.get(path) == actual_runtime.get(path)})
    changed_core = [ident for ident in sorted(actual_ids)
                    if expected["families"].get(ident, {}).get("core") != actual["families"][ident]["core"]]
    changed_labels = [ident for ident in sorted(actual_ids)
                      if expected["families"].get(ident, {}).get("display_names") != actual["families"][ident]["display_names"]]
    forbidden_core = sorted(set(changed_core) - allowed_families)
    forbidden_labels = sorted(set(changed_labels) - allowed_labels - allowed_families)
    if forbidden_core or forbidden_labels:
        raise ValueError(f"unexpected fingerprint changes; families={forbidden_core}, display_names={forbidden_labels}")
    return {"result": "PASS", "source_commit": baseline["source_commit"], "baseline_sha256": baseline.get("sha256"),
            "allowed_families": sorted(allowed_families), "allowed_display_names": sorted(allowed_labels),
            "approved_retired_removals": sorted(removed),
            "changed_families": changed_core, "changed_display_names": changed_labels,
            "runtime_java_metadata": {"changed": runtime_changed, "baseline_sha256": digest_bytes(canonical(expected_runtime)),
                                      "current_sha256": digest_bytes(canonical(actual_runtime))}}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    actions = parser.add_mutually_exclusive_group(required=True)
    actions.add_argument("--capture", action="store_true")
    actions.add_argument("--verify", action="store_true")
    parser.add_argument("--baseline", type=Path, default=DEFAULT_BASELINE)
    parser.add_argument("--allowlist", type=Path)
    args = parser.parse_args()
    result = capture(output=args.baseline) if args.capture else verify(baseline_path=args.baseline, allowlist_path=args.allowlist)
    print(json.dumps(result if args.verify else {"source_commit": result["source_commit"], "sha256": result["sha256"]}, ensure_ascii=False))


if __name__ == "__main__":
    main()
