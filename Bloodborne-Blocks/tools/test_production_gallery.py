import unittest

from build_production_gallery import canonical_properties, plan


class ProductionGalleryTests(unittest.TestCase):
    def test_canonical_state_uses_base_closed_placement_default(self):
        definition = {"id": "o_test", "default": {"facing": "north", "open": "true", "visual": "alt", "variant": "surface"},
                      "placement_properties": {"variant": "source_depth"}, "properties": {"open": ["false", "true"], "visual": ["base", "alt"]},
                      "states": {"facing=north,open=false,variant=source_depth,visual=base": {}}}
        self.assertEqual(canonical_properties(definition), ("facing=north,open=false,variant=source_depth,visual=base",
                         {"facing": "north", "open": "false", "visual": "base", "variant": "source_depth"}))

    def test_wall_origin_lifts_below_origin_helpers_above_pad(self):
        rows = [{"bounds": (0, -1.5, 0, 1, 1, 1), "footprint": [(0, 0, 0), (0, -1, 0)]}]
        plan(rows)
        self.assertEqual(rows[0]["position"][1], 66)
        self.assertGreater(min(rows[0]["position"][1] + c[1] for c in rows[0]["footprint"]), 63)

    def test_plan_keeps_root_and_pad_covering_helpers_and_render(self):
        rows = [{"bounds": (-2.2, 0, -1.2, 1.1, 2, 1.1), "footprint": [(0, 0, 0), (4, 0, 0)]}]
        plan(rows, columns=1)
        self.assertEqual(rows[0]["position"][1], 64)
        self.assertLessEqual(rows[0]["pad"][0], rows[0]["position"][0] + 4)
        self.assertGreaterEqual(rows[0]["pad"][2], rows[0]["position"][0] + 4)


if __name__ == "__main__": unittest.main()
