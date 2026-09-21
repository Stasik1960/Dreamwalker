"""Synthetic regression tests for logical palette classification."""
from __future__ import annotations

import unittest

from logical_palette import classify_palette


def block(ident, states, creative=False):
    return {"id": ident, "creative": creative, "states": states}


def part(ident, **properties):
    return {"id": ident, "properties": properties, "offset": [0, 0, 0]}


def rule(source, properties, components, target="o_object"):
    return {"source": {"id": source, "properties": properties},
            "target": {"id": target, "properties": {}}, "components": components}


class LogicalPaletteTests(unittest.TestCase):
    def test_single_module_exact_state_proof_hides_module_and_legacy(self):
        legacy = {"blocks": [block("old", {"assembled=false": [0, 0, 0]})]}
        v2 = {"blocks": [block("m_one", {}, True)]}
        migration = {"old": {"states": {"assembled=false": [part("m_one", facing="north")]}}}
        result = classify_palette(legacy, v2, migration, {"m_one": ["old"]},
                                  {"rules": [rule("old", {"assembled": "false"}, [part("m_one", facing="north")])]})
        self.assertEqual(result["modules"]["m_one"]["classification"], "standalone")
        self.assertEqual(result["safe_hidden_ids"], ["m_one", "old"])
        self.assertEqual(result["modules"]["m_one"]["replacement_logical_ids"], ["o_object"])

    def test_compound_partial_and_null_components_remain_visible(self):
        legacy = {"blocks": [block("old", {"assembled=false": [], "assembled=false,facing=east": []})]}
        v2 = {"blocks": [block("m_a", {}, True), block("m_b", {}, True)]}
        migration = {"old": {"states": {"assembled=false": [part("m_a"), part("m_b")],
                                         "assembled=false,facing=east": [part("m_a"), part("m_b")]}}}
        result = classify_palette(legacy, v2, migration, {"m_a": ["old"], "m_b": ["old"]},
                                  {"rules": [rule("old", {"assembled": "false"}, [part("m_a"), part("m_b")]),
                                             rule("old", {"assembled": "false", "facing": "east"}, None)]})
        self.assertEqual(result["modules"]["m_a"]["classification"], "compound_part")
        self.assertFalse(result["modules"]["m_a"]["safe_hidden"])
        self.assertIn("incomplete_logical_coverage", result["modules"]["m_a"]["coverage"]["uncovered_reason"])
        self.assertEqual(result["legacy"]["safe_hidden_ids"], [])

    def test_shared_module_and_technical_tags_are_conservative(self):
        legacy = {"blocks": [block("old_a", {"assembled=false": []}), block("old_b", {"assembled=false": []})]}
        v2 = {"blocks": [block("m_shared", {}, True), block("m_service", {}, True), block("m_unused", {}, True)]}
        migration = {"old_a": {"states": {"assembled=false": [part("m_shared")]}}}
        rules = {"rules": [rule("old_a", {"assembled": "false"}, [part("m_shared")])]}
        result = classify_palette(legacy, v2, migration,
                                  {"m_shared": ["old_a", "old_b"], "m_service": ["world_composite"], "m_unused": []}, rules)
        self.assertTrue(result["modules"]["m_shared"]["safe_hidden"])
        self.assertEqual(result["modules"]["m_service"]["classification"], "service")
        self.assertFalse(result["modules"]["m_service"]["safe_hidden"])
        self.assertEqual(result["modules"]["m_unused"]["classification"], "unreferenced")

    def test_assembled_and_duplicate_waterlogged_occurrences_do_not_block_hide(self):
        legacy = {"blocks": [block("old", {"assembled=false": [], "assembled=true": [], "waterlogged=true": []})]}
        v2 = {"blocks": [block("m_one", {}, True)]}
        migration = {"old": {"states": {"assembled=false": [part("m_one")], "assembled=true": [part("m_one")],
                                         "waterlogged=true": [part("m_one")]}}}
        result = classify_palette(legacy, v2, migration, {"m_one": ["old"]},
                                  {"rules": [rule("old", {"assembled": "false"}, [part("m_one")])]})
        self.assertTrue(result["modules"]["m_one"]["safe_hidden"])

    def test_distinct_waterlogged_art_remains_visible(self):
        legacy = {"blocks": [block("old", {"assembled=false": [], "waterlogged=true": []})]}
        v2 = {"blocks": [block("m_one", {}, True), block("m_water", {}, True)]}
        migration = {"old": {"states": {"assembled=false": [part("m_one")],
                                         "waterlogged=true": [part("m_one"), part("m_water")]}}}
        result = classify_palette(legacy, v2, migration, {"m_one": ["old"]},
                                  {"rules": [rule("old", {"assembled": "false"}, [part("m_one")])]})
        self.assertFalse(result["modules"]["m_one"]["safe_hidden"])
        self.assertIn("unknown_or_non_dry_source_usage", result["modules"]["m_one"]["coverage"]["uncovered_reason"])

    def test_member_array_is_proven_inside_complete_compound_rule(self):
        legacy = {"blocks": [block("root", {"assembled=false": []}), block("member", {"assembled=false": []})]}
        v2 = {"blocks": [block("m_root", {}, True), block("m_member", {}, True)]}
        migration = {"root": {"states": {"assembled=false": [part("m_root")]},},
                     "member": {"states": {"assembled=false": [part("m_member")]}}}
        compound = rule("root", {"assembled": "false"}, [part("m_root"), {"id": "m_member", "properties": {}, "offset": [2, 0, 0]}])
        compound["members"] = [{"id": "member", "properties": {"assembled": "false"}, "offset": [2, 0, 0]}]
        result = classify_palette(legacy, v2, migration, {"m_member": ["member"]}, {"rules": [compound]})
        self.assertTrue(result["modules"]["m_member"]["safe_hidden"])
        self.assertIn("o_object", result["modules"]["m_member"]["replacement_logical_ids"])

    def test_legacy_id_needs_every_dry_definition_state(self):
        legacy = {"blocks": [block("old", {"assembled=false": [], "assembled=false,facing=east": []})]}
        v2 = {"blocks": [block("m_one", {}, True)]}
        migration = {"old": {"states": {"assembled=false": [part("m_one")]}}}
        result = classify_palette(legacy, v2, migration, {"m_one": ["old"]},
                                  {"rules": [rule("old", {"assembled": "false"}, [part("m_one")])]})
        self.assertNotIn("old", result["safe_hidden_ids"])


if __name__ == "__main__":
    unittest.main()
