"""Build a finite static-geometry request from existing prepared decisions.

This reads only the immutable decision dictionary and its already-created
frequency proof. It never opens a world, decodes section arrays, recomputes a
source choice, or reads the owner streams. Poses are deduplicated by the exact
registry/properties/payload signature used by the existing pilot exporter.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import math
import sqlite3
from pathlib import Path
from typing import Any


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def read_json(path: Path) -> Any:
    with path.open("r", encoding="utf-8-sig") as source:
        return json.load(source)


def normalized(value: Any) -> Any:
    """Match the pilot signature, including canonical positive floating zero."""
    if isinstance(value, float):
        if not math.isfinite(value):
            raise ValueError("Non-finite value in prepared pose")
        return 0.0 if value == 0 else value
    if isinstance(value, list):
        return [normalized(item) for item in value]
    if isinstance(value, dict):
        return {key: normalized(item) for key, item in value.items()}
    return value


def signature(decision: dict[str, Any]) -> tuple[str, str, dict[str, Any]]:
    payload = dict(decision.get("payload", {}))
    shift = normalized(payload.get("SourceShift", decision.get("sourceShift", [0.0, 0.0, 0.0])))
    if not isinstance(shift, list) or len(shift) != 3:
        raise ValueError("Prepared pose has malformed SourceShift")
    if any(float(value) != 0.0 for value in shift):
        payload["SourceShift"] = shift
    else:
        payload.pop("SourceShift", None)
    mount_y = normalized(payload.get("MountY", decision.get("mountY", 0.0)))
    if float(mount_y) != 0.0:
        payload["MountY"] = mount_y
    else:
        payload.pop("MountY", None)
    data = normalized(
        {
            "payload": payload,
            "properties": decision["properties"],
            "registryId": decision["registryId"],
        }
    )
    canonical = json.dumps(data, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
    key = "p" + hashlib.sha256(canonical.encode("utf-8")).hexdigest()[:20]
    return key, canonical, data


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source-plan", type=Path, required=True)
    parser.add_argument("--frequency-proof", type=Path, required=True)
    parser.add_argument("--preserve-catalog", type=Path, required=True,
                        help="Existing 41-pose static-architecture-v1274.json")
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()

    plan = args.source_plan.resolve(strict=True)
    proof_path = args.frequency_proof.resolve(strict=True)
    catalog_path = args.preserve_catalog.resolve(strict=True)
    output = args.output.absolute()
    if output.exists():
        raise FileExistsError(f"Refusing to replace request: {output}")
    if not output.parent.is_dir():
        raise NotADirectoryError(f"Request parent does not exist: {output.parent}")

    plan_digest = sha256(plan)
    proof_digest = sha256(proof_path)
    catalog_digest = sha256(catalog_path)
    proof = read_json(proof_path)
    if proof.get("status") != "PASS_READ_ONLY_COMPLETE_PREPARED_DECISION_COUNTS":
        raise ValueError("Decision frequency proof is not complete/pass")
    if proof.get("sourcePlanSha256AtAudit") != plan_digest:
        raise ValueError("Frequency proof belongs to a different source-plan.sqlite")
    frequencies = proof.get("decisionCellCounts")
    if not isinstance(frequencies, dict):
        raise ValueError("Frequency proof has no decisionCellCounts")

    catalog = read_json(catalog_path)
    preserved_rows = catalog.get("poses")
    if not isinstance(preserved_rows, list) or len(preserved_rows) != 41:
        raise ValueError("Expected the existing 41-pose static catalog")
    preserved_keys = [row.get("key") for row in preserved_rows]
    if len(set(preserved_keys)) != 41 or not all(isinstance(key, str) for key in preserved_keys):
        raise ValueError("Existing catalog pose keys are missing or duplicated")

    connection = sqlite3.connect(f"{plan.as_uri()}?mode=ro&immutable=1", uri=True)
    try:
        connection.execute("BEGIN")
        poses: dict[str, dict[str, Any]] = {}
        owner_decisions = 0
        active_owner_decisions = 0
        zero_frequency_owner_decisions = 0
        out_of_scope_owner_decisions = 0
        out_of_scope_owner_cells = 0
        for decision_id, raw in connection.execute("SELECT id,data FROM decisions ORDER BY id"):
            decision = json.loads(raw)
            if decision.get("action") != "OWNER":
                continue
            owner_decisions += 1
            if str(decision_id) not in frequencies:
                raise ValueError(f"Frequency proof is missing decision {decision_id}")
            count = int(frequencies[str(decision_id)])
            if count < 0:
                raise ValueError(f"Negative saved frequency for decision {decision_id}")
            if count == 0:
                zero_frequency_owner_decisions += 1
                continue
            active_owner_decisions += 1

            registry_id = decision.get("registryId")
            properties = decision.get("properties")
            payload = decision.get("payload", {})
            if not isinstance(registry_id, str) or not registry_id.startswith("bloodborne_dw:source_"):
                out_of_scope_owner_decisions += 1
                out_of_scope_owner_cells += count
                continue
            if not isinstance(properties, dict) or not isinstance(payload, dict):
                raise ValueError(f"OWNER decision {decision_id} has malformed pose data")
            # Functional families cannot be identified safely from plan text.
            # Closed/base rows are passed to native registered definitions; the
            # exporter records stairs, ladders, thin windows, supports and other
            # functional specs as explicit exclusions.
            if properties.get("open", "false") != "false" or properties.get("profile", "base") != "base":
                continue

            key, canonical, data = signature(decision)
            current = poses.get(key)
            if current is None:
                current = {
                    "key": key,
                    "canonical": canonical,
                    "payload": data["payload"],
                    "properties": data["properties"],
                    "registryId": data["registryId"],
                    "decisionIds": [],
                    "savedCellCount": 0,
                }
                poses[key] = current
            elif current["canonical"] != canonical:
                raise ValueError(f"Truncated pose-key collision: {key}")
            current["decisionIds"].append(int(decision_id))
            current["savedCellCount"] += count
    finally:
        connection.close()

    missing = [key for key in preserved_keys if key not in poses]
    if missing:
        raise ValueError(f"Existing catalog poses are absent from active prepared decisions: {missing}")

    for key, row in poses.items():
        row["preservedPose"] = key in set(preserved_keys)

    # Keep original IDs and their stable catalog order first. All additional
    # active immutable poses follow by descending saved frequency.
    ordered = [poses[key] for key in preserved_keys]
    ordered.extend(
        sorted(
            (row for key, row in poses.items() if key not in set(preserved_keys)),
            key=lambda row: (-row["savedCellCount"], row["registryId"], row["key"]),
        )
    )

    report = {
        "schemaVersion": 1,
        "status": "PASS_EXISTING_PREPARED_DECISIONS_ONLY",
        "sourcePlanSha256": plan_digest,
        "decisionFrequencyProofSha256": proof_digest,
        "preservedCatalogSha256": catalog_digest,
        "preservedPoseCount": len(preserved_keys),
        "poseCount": len(ordered),
        "ownerDecisionCount": owner_decisions,
        "activeOwnerDecisionCount": active_owner_decisions,
        "zeroFrequencyOwnerDecisionCount": zero_frequency_owner_decisions,
        "outOfScopeOwnerDecisionCount": out_of_scope_owner_decisions,
        "outOfScopeOwnerCellCount": out_of_scope_owner_cells,
        "ordering": "PRESERVED_41_IN_CATALOG_ORDER_THEN_DESCENDING_SAVED_CELL_FREQUENCY",
        "sourceWorldOpened": False,
        "sectionArraysDecoded": False,
        "runtimeOwnerStreamsRead": False,
        "poses": ordered,
    }
    with output.open("x", encoding="utf-8", newline="\n") as target:
        json.dump(report, target, ensure_ascii=False, indent=2)
        target.write("\n")


if __name__ == "__main__":
    main()
