"""Read-only regression checks for the protected C002 and C1979 compositions."""
from __future__ import annotations

import gzip
import json
import unittest
from pathlib import Path

from nightmare_composition_recipes import _C1979


ROOT = Path(__file__).resolve().parents[1]
LOGICAL = ROOT / "src/main/resources/bloodborne_blocks/logical"
PROTECTED = ("o_c002", "o_c1979_1", "o_c1979_2", "o_c1979_3", "o_c1979_4", "o_c1979_5")


def read(path: Path):
    data = path.read_bytes()
    return json.loads(gzip.decompress(data) if path.suffix == ".gz" else data)


def texture_alias(texture: str, aliases: dict[str, str]) -> str:
    """Follow documented pruning aliases without accepting an alias cycle."""
    seen: set[str] = set()
    while texture in aliases:
        if texture in seen:
            raise AssertionError(f"texture alias cycle at {texture}")
        seen.add(texture)
        texture = aliases[texture]
    return texture


class PreservedSemanticsTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.required = read(ROOT / "docs/required-production-families.json")
        cls.frozen = read(ROOT / "docs/production-authoring-inputs.json.gz")
        cls.aliases = read(ROOT / "docs/production-resource-pruning.json")["texture_aliases"]
        cls.live_blocks = {row["id"]: row for row in read(LOGICAL / "definitions.json")["blocks"]}
        cls.live_contracts = {row["id"]: row for row in read(LOGICAL / "contracts-v2.json")["families"]}
        cls.live_meshes = read(LOGICAL / "meshes.json.gz")
        cls.frozen_blocks = {row["id"]: row for row in cls.frozen["definitions"]["blocks"]}
        cls.frozen_contracts = {row["id"]: row for row in cls.frozen["contracts"]["families"]}

    def test_all_protected_live_blocks_and_base_alt_states_exist(self):
        self.assertEqual(set(PROTECTED), set(self.frozen_blocks) & set(PROTECTED))
        for ident in PROTECTED:
            self.assertIn(ident, self.live_blocks)
            states = self.live_blocks[ident]["models"]
            self.assertEqual(set(self.frozen_blocks[ident]["models"]), set(states))
            self.assertTrue(any("visual=base" in state for state in states), ident)
            self.assertTrue(any("visual=alt" in state for state in states), ident)

    def test_textured_mesh_vertices_match_frozen_authoring_input(self):
        for ident in PROTECTED:
            for state, mesh_id in self.live_blocks[ident]["models"].items():
                frozen_mesh_id = self.frozen_blocks[ident]["models"][state]
                frozen_polygons = [row for row in self.frozen["meshes"][frozen_mesh_id]["polygons"] if "texture" in row]
                live_polygons = [row for row in self.live_meshes[mesh_id]["polygons"] if "texture" in row]
                self.assertEqual(len(frozen_polygons), len(live_polygons), f"{ident} {state}")
                for old, live in zip(frozen_polygons, live_polygons):
                    self.assertEqual(texture_alias(old["texture"], self.aliases), texture_alias(live["texture"], self.aliases),
                                     f"{ident} {state} texture")
                    self.assertEqual(len(old["vertices"]), len(live["vertices"]), f"{ident} {state} polygon")
                    for old_vertex, live_vertex in zip(old["vertices"], live["vertices"]):
                        self.assertEqual(len(old_vertex), len(live_vertex), f"{ident} {state} vertex")
                        for old_value, live_value in zip(old_vertex, live_vertex):
                            self.assertLessEqual(abs(old_value - live_value), 1e-6, f"{ident} {state} vertex changed")

    def test_source_patterns_and_explicit_semantic_groups_are_preserved(self):
        for ident in PROTECTED:
            frozen_states = self.frozen_contracts[ident]["states"]
            live_states = self.live_contracts[ident]["states"]
            self.assertEqual(set(frozen_states), set(live_states), ident)
            for state in frozen_states:
                self.assertEqual(frozen_states[state].get("migration_source_pattern", []),
                                 live_states[state].get("migration_source_pattern", []), f"{ident} {state}")
        actual_groups = {
            "C002": [[1, 2]],
            "C1979": [list(group) for group in _C1979.values()],
        }
        self.assertEqual(actual_groups, self.required["preserved_semantic_groups"])


if __name__ == "__main__":
    unittest.main()
