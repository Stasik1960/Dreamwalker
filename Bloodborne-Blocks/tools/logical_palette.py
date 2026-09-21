"""Deterministically audit v2 creative modules against logical-object rules.

The classifier is deliberately data-only.  In particular, module names and
``source`` fields are not semantic evidence: only exact state entries in the
v2 and logical migration tables can justify hiding an item.
"""
from __future__ import annotations

import json
from collections import defaultdict


def _state_key(properties):
    return ",".join("%s=%s" % pair for pair in sorted((properties or {}).items()))


def _blocks(document):
    return {block["id"]: block for block in document.get("blocks", []) if isinstance(block, dict) and "id" in block}


def _state_properties(state):
    if not state:
        return {}
    return dict(piece.split("=", 1) for piece in state.split(",") if "=" in piece)


def _components(value):
    """Return a canonical, non-empty v2 component array, else ``None``."""
    if not isinstance(value, list) or not value:
        return None
    answer = []
    for part in value:
        if not isinstance(part, dict) or not isinstance(part.get("id"), str) or not isinstance(part.get("offset"), list):
            return None
        answer.append({"id": part["id"], "properties": part.get("properties", {}), "offset": part["offset"]})
    return answer


def _fingerprint(components):
    return json.dumps(components, sort_keys=True, separators=(",", ":"))


def _rules(document):
    return document.get("rules", []) if isinstance(document, dict) else document


def _translated_subset(needle, haystack, translation):
    """Match every part with its exact relative offset, including duplicates."""
    remaining = list(haystack)
    for part in needle:
        shifted = dict(part)
        offset = part["offset"]
        if len(offset) != len(translation):
            return False
        shifted["offset"] = [offset[index] + translation[index] for index in range(len(offset))]
        try:
            remaining.remove(shifted)
        except ValueError:
            return False
    return True


def classify_palette(legacy, v2_definitions, v2_migration, sources, logical_rules):
    """Classify creative ``m_*`` entries and calculate conservatively safe hides.

    Inputs are decoded JSON documents (``v2_migration`` is its source-id map;
    ``logical_rules`` accepts either its document or its ``rules`` list).  The
    serialisable result is stable under input dictionary ordering.

    A module is hidden only if each dry, unassembled legacy state whose exact
    v2 expansion contains it has a logical rule with non-null components that
    exactly contains that expansion at the source/member anchor.  Provenance
    tags and v2 states not represented by legacy definitions are retained.
    """
    legacy_blocks = _blocks(legacy)
    v2_blocks = _blocks(v2_definitions)
    creative = sorted(ident for ident, block in v2_blocks.items()
                      if ident.startswith("m_") and block.get("creative") is True)

    # Every occurrence retains the full source state and the complete sibling
    # array.  Looking only at a module's own row would lose compound evidence.
    occurrences = defaultdict(list)
    source_states = defaultdict(list)
    for source_id, record in sorted((v2_migration or {}).items()):
        states = record.get("states", {}) if isinstance(record, dict) else {}
        for state, raw in sorted(states.items()):
            components = _components(raw)
            if components is None:
                continue
            occurrence = {"source_id": source_id, "properties": _state_properties(state),
                          "state": state, "components": components}
            source_states[source_id].append(occurrence)
            for part in components:
                if part["id"].startswith("m_"):
                    occurrences[part["id"]].append(occurrence)

    # A complete multi-source logical rule concatenates the root v2 array and
    # translated member arrays.  A source state is proven by an exact subset at
    # its known anchor, not by equality with that larger combined array.
    rule_index = defaultdict(list)
    for rule in _rules(logical_rules):
        if not isinstance(rule, dict):
            continue
        source = rule.get("source")
        target = rule.get("target")
        components = _components(rule.get("components"))
        if not isinstance(source, dict) or not isinstance(target, dict) or components is None:
            continue
        source_id = source.get("id")
        if not isinstance(source_id, str):
            continue
        target_id = target.get("id") if isinstance(target.get("id"), str) else None
        rule_index[(source_id, _state_key(source.get("properties", {})))].append((components, [0, 0, 0], target_id))
        for member in rule.get("members", []):
            if not isinstance(member, dict) or not isinstance(member.get("id"), str) or not isinstance(member.get("offset"), list):
                continue
            rule_index[(member["id"], _state_key(member.get("properties", {})))].append((components, member["offset"], target_id))

    def proof(row):
        matches = set()
        marker = (row["source_id"], _state_key(row["properties"]))
        for combined, offset, target_id in rule_index[marker]:
            if _translated_subset(row["components"], combined, offset) and target_id is not None:
                matches.add(target_id)
        return matches

    def reachable(source_id, state):
        return source_id in legacy_blocks and state.get("assembled") != "true" and state.get("waterlogged") != "true"

    # A legacy id can be hidden only when all of its own reachable v2 states
    # are exactly represented.  This intentionally excludes source ids with no
    # v2 expansion, even if another id happens to share their module.
    hidden_legacy = set()
    for source_id, block in sorted(legacy_blocks.items()):
        required = [_state_properties(state) for state in block.get("states", {})
                    if reachable(source_id, _state_properties(state))]
        by_state = {_state_key(row["properties"]): row for row in source_states[source_id]}
        if required and all(
                (row := by_state.get(_state_key(properties))) is not None
                and proof(row)
                for properties in required):
            hidden_legacy.add(source_id)

    modules = {}
    hidden_modules = set()
    for module in creative:
        module_occurrences = occurrences[module]
        provenance = sorted(set((sources or {}).get(module, [])))
        tags = sorted(value for value in provenance if value not in legacy_blocks)
        rows = [row for row in module_occurrences if reachable(row["source_id"], row["properties"])]
        # Assembled states are intentional composite placement and do not make
        # a dry palette item unsafe by themselves.  Waterlogged appearances are
        # also ignored only when their full v2 array duplicates a dry use; a
        # distinct waterlogged array is retained as potentially distinct art.
        dry_fingerprints = {_fingerprint(row["components"]) for row in rows}
        unknown_rows = [row for row in module_occurrences
                        if row["source_id"] not in legacy_blocks
                        or (row["properties"].get("waterlogged") == "true"
                            and _fingerprint(row["components"]) not in dry_fingerprints)]
        covered, remaining, logical_ids = [], [], set()
        for row in rows:
            entry = {"source_id": row["source_id"], "properties": row["properties"], "components": row["components"]}
            target_ids = proof(row)
            if target_ids:
                entry["logical_ids"] = sorted(target_ids)
                covered.append(entry); logical_ids.update(target_ids)
            else:
                entry["reason"] = "missing_exact_nonnull_logical_components"
                remaining.append(entry)
        if tags:
            classification = "service"
        elif not module_occurrences:
            classification = "unreferenced"
        else:
            widths = {len(row["components"]) for row in module_occurrences}
            sources_used = {(row["source_id"], row["state"]) for row in module_occurrences}
            classification = "standalone" if widths == {1} and len(sources_used) == 1 else "compound_part" if min(widths) > 1 else "variant"
        reasons = []
        if tags:
            reasons.append("technical_or_unknown_provenance")
        if unknown_rows:
            reasons.append("unknown_or_non_dry_source_usage")
        if remaining:
            reasons.append("incomplete_logical_coverage")
        safe = bool(rows) and not tags and not unknown_rows and not remaining
        if safe:
            hidden_modules.add(module)
        modules[module] = {
            "classification": classification, "provenance": provenance,
            "occurrences": [{"source_id": row["source_id"], "properties": row["properties"], "components": row["components"]}
                            for row in module_occurrences],
            "replacement_logical_ids": sorted(logical_ids),
            "coverage": {"reachable": len(rows), "covered": len(covered), "remaining": remaining,
                         "uncovered_reason": reasons}, "safe_hidden": safe,
        }
    return {"schemaVersion": 1, "modules": modules,
            "legacy": {"safe_hidden_ids": sorted(hidden_legacy)},
            "safe_hidden_ids": sorted(hidden_legacy | hidden_modules)}
