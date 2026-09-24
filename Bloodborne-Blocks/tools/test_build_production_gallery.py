from __future__ import annotations

import json
import unittest

from build_production_gallery import ALT_PROOF_IDS, FLOOR_Y, LADDER_SECTIONS, MANIFEST, LOGICAL, STATUE_IDS, functional_context, load_specimens, platform_block


class ProductionGallerySpecimenTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.specimens, cls.objects = load_specimens(MANIFEST)
        cls.definitions = {row["id"]: row for row in json.loads((LOGICAL / "definitions.json").read_text(encoding="utf8"))["blocks"]}

    def rows(self, ident: str) -> list[dict]:
        return [row for row in self.specimens if row["id"] == ident]

    def test_final_manifest_has_only_the_49_current_families(self):
        ids = {row["id"] for row in self.objects}
        self.assertEqual(len(ids), 49)
        self.assertFalse({"o_c561", "o_c1319", "o_c1962_a", "o_c1962_b", "o_bench_rotate", "o_ladder_02", "o_iron_railing"} & ids)

    def test_every_non_grouped_production_id_has_one_canonical_base_specimen(self):
        canonical = [row for row in self.specimens if row["role"] == "canonical_base"]
        expected = {row["id"] for row in self.objects} - {"o_ladder_01", "o_ladder_03"}
        self.assertEqual({row["id"] for row in canonical}, expected)
        self.assertEqual(len(canonical), len(expected))
        self.assertTrue(all(row["properties"].get("visual") == "base" for row in canonical if "visual" in row["properties"]))

    def test_all_six_interactive_families_have_closed_and_open_specimens(self):
        interactive = {row["id"] for row in self.definitions.values() if row.get("behavior") in ("door", "gate", "shutter")}
        self.assertEqual(len(interactive), 6)
        for ident in interactive:
            rows = self.rows(ident)
            self.assertEqual({row["properties"].get("open") for row in rows}, {"false", "true"})
            self.assertIn(ident + ":open", {row["specimen_id"] for row in rows})

    def test_statue_lantern_and_bench_functional_states_are_explicit(self):
        for ident in STATUE_IDS:
            rows = self.rows(ident)
            self.assertEqual({row["properties"].get("hand_lantern") for row in rows}, {"none", "lit"})
            self.assertIn(ident + ":hand_lantern_lit", {row["specimen_id"] for row in rows})
        self.assertEqual({row["properties"].get("lit") for row in self.rows("o_lantern")}, {"false", "true"})
        bench = {(row["properties"].get("facing"), row["properties"].get("diagonal")) for row in self.rows("o_bench")}
        self.assertEqual(bench, {(facing, diagonal) for facing in ("north", "east", "south", "west") for diagonal in ("false", "true")})

    def test_ladder_group_has_an_explicit_nonflush_landing_transition(self):
        sections = sorted((row for row in self.rows("o_ladder_03") if row["role"].startswith("ladder_section_")), key=lambda row: row["role"])
        self.assertEqual(len(sections), LADDER_SECTIONS)
        self.assertEqual([row["role"] for row in sections], [f"ladder_section_{number}" for number in range(1, LADDER_SECTIONS + 1)])
        for previous, current in zip(sections, sections[1:]):
            self.assertEqual(current["position"], (previous["position"][0], previous["position"][1] + 2, previous["position"][2]))
        landing = next(row for row in self.rows("o_ladder_01") if row["role"] == "ladder_landing")
        last = sections[-1]["position"]
        self.assertEqual(landing["position"], (last[0], last[1], last[2] - 2))
        # Authored mesh surfaces: landing deck y=.8125, final ladder y=1.0.
        self.assertEqual((last[1] + 1.0) - (landing["position"][1] + .8125), .1875)

    def test_functional_walls_leave_the_shutter_aperture_and_helpers_unblocked(self):
        for shutter in (row for row in self.rows("o_shuttered_window") if row["role"] in {"canonical_base", "open"}):
            context = functional_context(shutter)
            self.assertEqual(set(context.values()), {"minecraft:stone"})
            self.assertEqual(len(context), 16)
            opening = {(shutter["position"][0] + x, shutter["position"][1] + y, shutter["position"][2])
                       for x in range(-1, 2) for y in range(-1, 2)}
            footprint = {tuple(shutter["position"][index] + offset[index] for index in range(3))
                         for offset in shutter["footprint"]}
            self.assertTrue(all(point[2] == shutter["position"][2] for point in context))
            self.assertFalse(opening & set(context))
            self.assertFalse(footprint & set(context))
        ladders = [row for row in self.rows("o_ladder_03") if row["role"].startswith("ladder_section_")]
        landing = next(row for row in self.rows("o_ladder_01") if row["role"] == "ladder_landing")
        owned = {tuple(specimen["position"][index] + offset[index] for index in range(3))
                 for specimen in [*ladders, landing] for offset in specimen["footprint"]}
        for specimen in [*ladders, landing]:
            context = functional_context(specimen)
            self.assertTrue(context)
            self.assertFalse(owned & set(context))

    def test_platform_verification_keeps_a_functional_frame_stone_at_floor_level(self):
        shutter = next(row for row in self.rows("o_shuttered_window") if row["role"] == "canonical_base")
        frame_point = (shutter["position"][0] - 2, FLOOR_Y, shutter["position"][2])
        self.assertEqual(functional_context(shutter)[frame_point], "minecraft:stone")
        self.assertEqual(platform_block(shutter, frame_point), "minecraft:stone")

    def test_alt_proofs_are_extra_and_metadata_has_no_historical_ids(self):
        proof = [row for row in self.specimens if row["role"] == "alt_proof"]
        self.assertEqual({row["id"] for row in proof}, set(ALT_PROOF_IDS))
        self.assertTrue(all(row["properties"].get("visual") == "alt" for row in proof))
        manifest_ids = {row["id"] for row in self.objects}
        self.assertTrue(all(row["id"] in manifest_ids and row["id"].startswith("o_") for row in self.specimens))
        self.assertEqual(len({row["specimen_id"] for row in self.specimens}), len(self.specimens))


if __name__ == "__main__":
    unittest.main()
