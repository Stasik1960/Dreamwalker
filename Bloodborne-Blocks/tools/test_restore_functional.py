from __future__ import annotations

import gzip
import json
import unittest
from pathlib import Path

from restore_functional import CONNECTED, DOORS, FUNCTIONAL_IDS, LADDERS, OLD_APP_YAW, compile_functional

ROOT = Path(__file__).resolve().parents[1]


class RestoreFunctionalTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        with gzip.open(ROOT / "docs/production-authoring-inputs.json.gz", "rt", encoding="utf8") as stream:
            cls.compiled = compile_functional(json.load(stream))
        cls.blocks = {row["id"]: row for row in cls.compiled["blocks"]}
        cls.families = {row["id"]: row for row in cls.compiled["families"]}

    def test_restores_exactly_the_audited_twenty_one_families(self):
        self.assertEqual(set(self.blocks), set(FUNCTIONAL_IDS))
        self.assertEqual(set(self.families), set(FUNCTIONAL_IDS))
        self.assertTrue(all(not row.get("migration_disabled", False) for row in self.families.values()))
        self.assertTrue(all(mesh.startswith("functional_v2_") for row in self.blocks.values() for mesh in row["models"].values()))
        self.assertTrue(all(any(state["migration_source_pattern"] for state in family["states"].values())
                            for family in self.families.values()))

    def test_doors_keep_one_family_and_use_actual_vanilla_carriers(self):
        for ident, upper_y in DOORS.items():
            family = self.families[ident]
            self.assertNotIn("leaf", self.blocks[ident]["properties"])
            closed = family["states"]["facing=north,open=false,visual=base"]
            opened = family["states"]["facing=north,open=true,visual=base"]
            self.assertNotEqual(closed["collision_footprint"], opened["collision_footprint"])
            components = closed["migration_source_pattern"][0]["components"]
            self.assertEqual(components[0]['offset'],[0,1,0])
            self.assertEqual(components[1]["offset"], [0, upper_y+1, 0])
            self.assertNotIn("assembled", components[0]["properties"])
            self.assertNotIn("open", components[0]["properties"])
            self.assertEqual(closed["rotation"], 0)
            self.assertFalse(opened['migration_source_pattern'])
            self.assertTrue(all(not (b[0]<.5<b[3]) for b in opened['collision_footprint']['boxes']))


    def test_shutter_embedded_rule_uses_vanilla_trapdoor_and_open_collision(self):
        family = self.families["o_shuttered_window"]
        closed = family["states"]["embedded=true,facing=north,open=false,visual=base"]
        opened = family["states"]["embedded=true,facing=north,open=true,visual=base"]
        raw = closed["migration_source_pattern"][0]["components"][0]
        self.assertEqual(raw["id"], "minecraft:spruce_trapdoor")
        self.assertEqual(raw["properties"].get("open"), "false")
        self.assertNotIn("assembled", raw["properties"])
        self.assertNotEqual(closed["collision_footprint"], opened["collision_footprint"])
        self.assertTrue(any(pattern["components"][0]["properties"].get("open") == "true"
                            for pattern in opened["migration_source_pattern"]))

    def test_connected_ladders_and_server_behaviors_survive(self):
        for ident in CONNECTED:
            self.assertTrue(self.blocks[ident]["connection_family"])
            self.assertEqual(self.families[ident]["collision_policy"], "SIMPLE_BOX")
        curb = self.families["o_stone_curb"]["states"]["east=false,facing=north,north=false,south=false,visual=base,west=false"]
        self.assertAlmostEqual(curb["collision_footprint"]["boxes"][0][4], .25)
        for ident in LADDERS:
            self.assertEqual(self.blocks[ident]["behavior"], "ladder")
            self.assertEqual(self.families[ident]["placement_policy"], "WALL_ADJACENT")
        self.assertEqual(self.blocks["o_bench"]["behavior"], "bench")
        self.assertEqual(self.blocks["o_lantern"]["behavior"], "lantern")
        for ident in ("o_candles_0", "o_lanterns", "o_wall_lantern", "o_lantern", "o_lightning_rod", "o_oak_wood"):
            self.assertEqual(self.blocks[ident]["properties"]["lit"], ["false", "true"])
            for key, row in self.blocks[ident]["states"].items():
                self.assertEqual(row[2], 15 if "lit=true" in key else 0)

    def test_every_facing_state_uses_contract_rotation_metadata(self):
        for family in self.families.values():
            for key, state in family["states"].items():
                values = dict(piece.split("=", 1) for piece in key.split(","))
                if "facing" in values:
                    self.assertEqual(state["rotation"], ('north','east','south','west').index(values['facing'])*90, (family["id"], key))


if __name__ == "__main__":
    unittest.main()
