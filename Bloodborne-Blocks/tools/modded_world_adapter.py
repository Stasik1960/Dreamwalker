"""Compile frozen modular and legacy carrier states for the existing converter.

The latest modded backup contains the old generated modular registry and old
Bloodborne carrier IDs, not raw vanilla carrier blocks.  This adapter composes
both immutable archived schemas with current reviewed Contract V2 rules.  It
produces ordinary :class:`convert_logical_world.Rule` objects; scanning,
overlap rejection, ownership checks and writes stay in the established engine.
"""
from __future__ import annotations

import gzip
import hashlib
import json
from collections import Counter
from pathlib import Path
from typing import Any, Iterable


ROOT = Path(__file__).resolve().parents[1]
ARCHIVE_PATH = ROOT / "docs/pre-production-source-mapping.json.gz"
BASELINE_PATH = ROOT / "docs/beta-client-qa/baseline-fingerprints.json.gz"


def _state_key(properties: dict[str, str]) -> str:
    return ",".join(f"{key}={value}" for key, value in sorted(properties.items()))


def _inventory_counts(path: Path | None) -> tuple[dict[tuple[str, tuple[tuple[str, str], ...]], int], str | None]:
    if path is None:
        return {}, None
    raw = path.read_bytes()
    data = json.loads(raw)
    result: dict[tuple[str, tuple[tuple[str, str], ...]], int] = {}
    for key, count in data.get("blocks", {}).get("stateCounts", {}).items():
        name, _, suffix = key.partition("[")
        props = suffix[:-1] if suffix.endswith("]") else ""
        values = tuple(sorted(tuple(part.split("=", 1)) for part in props.split(",") if part))
        result[(name, values)] = int(count)
    return result, hashlib.sha256(raw).hexdigest()


def _fragment_state(fragment: dict[str, Any], old_defaults: dict[str, dict[str, str]]):
    from convert_logical_world import make_state
    return make_state(fragment, old_defaults)


def compose_rule(raw_rule, migration: dict[str, Any], old_defaults: dict[str, dict[str, str]]):
    """Return exact archived modular pieces for one reviewed raw rule.

    Missing archived source states are returned as diagnostics rather than
    guessed.  Conflicting fragments at one coordinate also fail closed.
    """
    from convert_logical_world import Expected, add

    raw_pieces = (raw_rule.source,) + raw_rule.members
    by_offset = {}
    missing = []
    for piece in raw_pieces:
        source_id = piece.state[0].split(":", 1)[-1]
        entry = migration.get(source_id)
        merged = dict(piece.state[1])
        if entry is None:
            missing.append({"rule": raw_rule.number, "target": raw_rule.target[0],
                            "source": source_id, "state": _state_key(merged), "reason": "archive_block_missing"})
            continue
        merged = {**{str(k): str(v) for k, v in entry.get("default", {}).items()}, **merged}
        state_key = _state_key(merged)
        fragments = entry.get("states", {}).get(state_key)
        if fragments is None:
            missing.append({"rule": raw_rule.number, "target": raw_rule.target[0],
                            "source": source_id, "state": state_key, "reason": "archive_state_missing"})
            continue
        if isinstance(fragments, dict) and fragments.get("keep") is True:
            by_offset[piece.offset] = (piece.state[0], tuple(sorted(merged.items())))
            continue
        if not isinstance(fragments, list):
            missing.append({"rule": raw_rule.number, "target": raw_rule.target[0],
                            "source": source_id, "state": state_key, "reason": "invalid_archive_mapping"})
            continue
        for fragment in fragments:
            offset = add(piece.offset, tuple(int(value) for value in fragment["offset"]))
            state = _fragment_state(fragment, old_defaults)
            previous = by_offset.get(offset)
            if previous is not None and previous != state:
                missing.append({"rule": raw_rule.number, "target": raw_rule.target[0],
                                "offset": list(offset), "reason": "archive_fragment_overlap"})
            by_offset[offset] = state
    if missing:
        return None, missing
    return tuple(Expected(offset, state) for offset, state in sorted(by_offset.items())), []


def compose_legacy_rule(raw_rule, definitions: dict[str, dict[str, Any]]):
    """Translate one raw assembly to exact old Bloodborne carrier states.

    The old mod copied vanilla carrier properties into Bloodborne IDs and added
    properties such as ``assembled`` and ``open``.  Those added properties are
    taken only from the frozen definition defaults, and the resulting complete
    key must occur in that definition's archived ``states`` table.
    """
    from convert_logical_world import Expected

    pieces = []
    missing = []
    for piece in (raw_rule.source,) + raw_rule.members:
        carrier = piece.state[0].split(":", 1)[-1]
        definition = definitions.get(carrier)
        if definition is None:
            missing.append({"rule": raw_rule.number, "target": raw_rule.target[0],
                            "source": carrier, "reason": "legacy_definition_missing"})
            continue
        properties = {**{str(k): str(v) for k, v in definition.get("default", {}).items()},
                      **dict(piece.state[1])}
        state_key = _state_key(properties)
        if state_key not in definition.get("states", {}):
            missing.append({"rule": raw_rule.number, "target": raw_rule.target[0],
                            "source": carrier, "state": state_key,
                            "reason": "legacy_state_missing"})
            continue
        state = ("bloodborne_blocks:" + carrier, tuple(sorted(properties.items())))
        pieces.append(Expected(piece.offset, state, piece.shape))
    if missing:
        return None, missing
    return tuple(pieces), []


def _embedded_window_rules(resources: Path, start: int):
    """Compile the removed ``embedded`` property from immutable beta evidence."""
    if not BASELINE_PATH.is_file():
        return [], [{"reason": "embedded_window_baseline_missing", "path": str(BASELINE_PATH)}]
    from convert_logical_world import Expected, Rule, make_state

    baseline = json.load(gzip.open(BASELINE_PATH, "rt", encoding="utf-8"))["fingerprints"]["families"]
    old = baseline["o_shuttered_window"]["core"]["contract"]
    current = {row["id"]: row for row in json.loads((resources / "contracts-v2.json").read_text(encoding="utf-8"))["families"]}[
        "o_shuttered_window"]
    defaults = {"bloodborne_blocks:o_shuttered_window": {}}
    rules = []
    for old_key, old_state in sorted(old["states"].items()):
        properties = dict(part.split("=", 1) for part in old_key.split(",") if part)
        properties.pop("embedded", None)
        target_key = _state_key(properties)
        target_state = current["states"].get(target_key)
        if target_state is None:
            continue
        source_props = {**properties, "embedded": dict(part.split("=", 1) for part in old_key.split(","))["embedded"]}
        source = Expected((0, 0, 0), make_state({"id": "o_shuttered_window", "properties": source_props}, defaults),
                          frozenset(tuple(cell) for cell in old_state["interaction_footprint"]["cells"]))
        target = make_state({"id": "o_shuttered_window", "properties": properties}, defaults)
        rules.append(Rule(start + len(rules), source, target, (0, 0, 0), (), None,
                          frozenset(tuple(cell) for cell in target_state["interaction_footprint"]["cells"]),
                          source_mode="modded", source_reference="frozen beta embedded-window Contract V2"))
    return rules, []


def compile_modded_rules(resources: Path, inventory_path: Path | None = None):
    from convert_logical_world import Rule, load_defaults
    from logical_contract_v2 import direct_rules
    from source_mapping_archive import archive

    frozen = archive(ROOT)
    migration = frozen["v2\\migration.json"]
    old_defaults = {
        "bloodborne_blocks:" + row["id"]: {str(k): str(v) for k, v in row.get("default", {}).items()}
        for row in frozen["v2\\definitions.json"]["blocks"]
    }
    legacy_definitions = {row["id"]: row for row in frozen["definitions.json"]["blocks"]}
    legacy_defaults = {
        "bloodborne_blocks:" + ident: {str(k): str(v) for k, v in row.get("default", {}).items()}
        for ident, row in legacy_definitions.items()
    }
    raw_rules, defaults = direct_rules(resources)
    compiled = []
    compiled_legacy = []
    gaps = []
    legacy_gaps = []
    for raw_rule in raw_rules:
        pieces, missing = compose_rule(raw_rule, migration, old_defaults)
        gaps.extend(missing)
        if pieces:
            compiled.append((raw_rule, pieces))
        legacy_pieces, legacy_missing = compose_legacy_rule(raw_rule, legacy_definitions)
        legacy_gaps.extend(legacy_missing)
        if legacy_pieces:
            compiled_legacy.append((raw_rule, legacy_pieces))

    inventory_counts, inventory_sha = _inventory_counts(inventory_path)
    static_frequency = Counter(piece.state for _, pieces in compiled for piece in pieces)
    rules = []
    anchor_rows = []
    for raw_rule, pieces in compiled:
        def rank(piece):
            # With an inventory, absent means zero and therefore this exact
            # assembly cannot exist.  Static fan-out remains the tie breaker.
            count = inventory_counts.get(piece.state, 0) if inventory_counts else static_frequency[piece.state]
            return count, static_frequency[piece.state], piece.offset, piece.state
        anchors = [piece for piece in pieces if piece.state[0].startswith("bloodborne_blocks:m_")]
        if not anchors:
            gaps.append({"rule": raw_rule.number, "target": raw_rule.target[0],
                         "reason": "no_modded_anchor_raw_only_rule"})
            continue
        source = min(anchors, key=rank)
        members = tuple(piece for piece in pieces if piece is not source)
        rules.append(Rule(len(rules), source, raw_rule.target, raw_rule.root_offset, members, None,
                          raw_rule.shape, raw_rule.supersedes_targets, raw_rule.variant_guards,
                          raw_rule.transaction_id, raw_rule.outputs, "modded",
                          f"frozen v2/migration.json + Contract V2 raw rule {raw_rule.number}"))
        anchor_rows.append({"rule": len(rules) - 1, "target": raw_rule.target[0],
                            "state": source.state[0], "offset": list(source.offset),
                            "inventoryCount": inventory_counts.get(source.state) if inventory_counts else None})

    legacy_anchor_rows = []
    for raw_rule, pieces in compiled_legacy:
        def legacy_rank(piece):
            count = inventory_counts.get(piece.state, 0) if inventory_counts else 0
            return count, piece.offset, piece.state
        source = min(pieces, key=legacy_rank)
        members = tuple(piece for piece in pieces if piece is not source)
        rules.append(Rule(len(rules), source, raw_rule.target, raw_rule.root_offset, members, None,
                          raw_rule.shape, raw_rule.supersedes_targets, raw_rule.variant_guards,
                          raw_rule.transaction_id, raw_rule.outputs, "modded",
                          f"frozen definitions.json legacy carriers + Contract V2 raw rule {raw_rule.number}"))
        legacy_anchor_rows.append({"rule": len(rules) - 1, "target": raw_rule.target[0],
                                   "state": source.state[0], "offset": list(source.offset),
                                   "inventoryCount": inventory_counts.get(source.state) if inventory_counts else None})

    embedded, embedded_gaps = _embedded_window_rules(resources, len(rules))
    rules.extend(embedded)
    gaps.extend(embedded_gaps)
    current_ids = {"bloodborne_blocks:architecture_part"} | {
        "bloodborne_blocks:" + row["id"]
        for row in json.loads((resources / "definitions.json").read_text(encoding="utf-8"))["blocks"]
    }
    incompatible_inventory = Counter()
    for state, count in inventory_counts.items():
        name = state[0]
        if not name.startswith("bloodborne_blocks:") or name in current_ids:
            continue
        category = ("modular" if name.startswith("bloodborne_blocks:m_") else
                    "legacyCarrier" if name.split(":", 1)[1] in legacy_definitions else
                    "otherRetired")
        incompatible_inventory[category] += count
    diagnostics = {
        "archiveSha256": hashlib.sha256(ARCHIVE_PATH.read_bytes()).hexdigest(),
        "inventorySha256": inventory_sha,
        "rawRules": len(raw_rules),
        "compiledRawRules": len(compiled),
        "compiledLegacyCarrierRules": len(compiled_legacy),
        "embeddedWindowRules": len(embedded),
        "mappingGaps": gaps,
        "legacyCarrierMappingGaps": legacy_gaps,
        "anchors": anchor_rows,
        "legacyCarrierAnchors": legacy_anchor_rows,
        "estimatedAnchorHits": sum(row["inventoryCount"] or 0 for row in anchor_rows) if inventory_counts else None,
        "maximumAnchorHits": max((row["inventoryCount"] or 0 for row in anchor_rows), default=0) if inventory_counts else None,
        "estimatedLegacyCarrierAnchorHits": sum(row["inventoryCount"] or 0 for row in legacy_anchor_rows) if inventory_counts else None,
        "maximumLegacyCarrierAnchorHits": max((row["inventoryCount"] or 0 for row in legacy_anchor_rows), default=0) if inventory_counts else None,
        "inventoryRegistryIncompatibleBlocks": sum(incompatible_inventory.values()) if inventory_counts else None,
        "inventoryRegistryIncompatibleByCategory": dict(sorted(incompatible_inventory.items())) if inventory_counts else None,
        "retiredLogicalMappings": "NOT_IMPLEMENTED_NOT_PRESENT_IN_INPUT; frozen pre-Agony evidence exists, but inspected latest modded input reports zero o_* states",
    }
    # Old defaults are required when a palette entry omits a property; current
    # production defaults then override only IDs that still exist.
    return rules, {**old_defaults, **legacy_defaults, **load_defaults(resources)}, diagnostics
