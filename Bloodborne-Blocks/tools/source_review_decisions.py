"""Authoritative, append-only application of a reviewed source-assembly batch.

The discovery manifest is evidence, not an instruction to rediscover or merge
objects.  This module deliberately changes only the requested review rows and
records the original signature beside the human decision.
"""
from __future__ import annotations

import copy
import json
from pathlib import Path


DECISIONS = {
    "C001": {"kind": "SPLIT", "groups": [[1, 3, 5], [2, 4, 6]]},
    "C002": {"kind": "OBJECT", "groups": [[1, 2]]},
    "C003": {"kind": "OBJECT", "groups": [[1, 2, 3, 4, 5, 6]]},
    "C008": {"kind": "OBJECT", "groups": [[1, 2, 3, 4, 5, 6]]},
    "C009": {"kind": "SPLIT", "groups": [[1, 3, 5], [2, 4, 6]]},
    "C471": {"kind": "OBJECT", "groups": [[1, 2]]},
    "C046": {"kind": "OBJECT", "groups": [[1]], "context": [2, 3, 4]},
    "C1680": {"kind": "OBJECT", "groups": [[1, 2]]},
    "C474": {"kind": "OBJECT", "groups": [list(range(1, 17))]},
    "C1962": {"kind": "OBJECT", "groups": [[1, 2]]},
    "C1979": {"kind": "OBJECT", "groups": [[2, 3, 4, 5, 6, 7, 8, 9, 10, 12, 13]], "context": [1, 11]},
    "C028": {"kind": "OBJECT", "groups": [[1]]}, "C282": {"kind": "OBJECT", "groups": [[1]]},
    "C561": {"kind": "OBJECT", "groups": [[1]]}, "C618": {"kind": "OBJECT", "groups": [[1]]},
    "C654": {"kind": "OBJECT", "groups": [[1]]}, "C1319": {"kind": "OBJECT", "groups": [[1]]},
    "C1491": {"kind": "OBJECT", "groups": [[1]]},
}


def reviewed_authoring(manifest: dict) -> dict:
    """Return compact, deterministic authoring input without mutating evidence."""
    candidates = {row["review_id"]: row for row in manifest["candidates"]}
    families = []
    for review_id, decision in DECISIONS.items():
        row = candidates[review_id]
        for index, group in enumerate(decision["groups"]):
            suffix = chr(ord("a") + index) if decision["kind"] == "SPLIT" else ""
            families.append({
                "id": "o_" + review_id.lower() + ("_" + suffix if suffix else ""),
                "review_id": review_id, "component_numbers": group,
                "context_numbers": decision.get("context", []),
                "source_signatures": [pattern["exact_source_signature"] for pattern in row.get("source_patterns", [])],
            })
    return {"schemaVersion": 1, "batch": "batch-02", "authority": "manual-review", "families": families}


def persist_decisions(path: Path, *, source_manifest: Path) -> bool:
    """Persist decisions once; reject a silently different rerun."""
    manifest = json.loads(source_manifest.read_text(encoding="utf-8"))
    authoring = reviewed_authoring(manifest)
    text = json.dumps(authoring, ensure_ascii=False, indent=2) + "\n"
    if path.exists():
        if path.read_text(encoding="utf-8") != text:
            raise ValueError("reviewed decisions already exist; explicit manual edit required")
        return False
    path.write_text(text, encoding="utf-8")
    return True


def apply_history(path: Path) -> bool:
    """Write decisions/history to the authoritative aggregate without re-signing rows."""
    document = json.loads(path.read_text(encoding="utf-8"))
    changed = False
    for row in document.get("candidates", []):
        review_id = row.get("review_id")
        if review_id not in DECISIONS:
            continue
        decision = DECISIONS[review_id]
        record = {"event": "manual_review", "batch": "batch-02", "decision": copy.deepcopy(decision),
                  "preserved_exact_source_signature": row.get("exact_source_signature")}
        if row.get("user_decision") not in (None, decision):
            raise ValueError("refusing to replace an existing manual decision: " + review_id)
        if record not in row.setdefault("history", []):
            row["history"].append(record); changed = True
        if row.get("user_decision") != decision:
            row["user_decision"] = decision; row["status"] = "REVIEWED"; changed = True
    if changed:
        path.write_text(json.dumps(document, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return changed
