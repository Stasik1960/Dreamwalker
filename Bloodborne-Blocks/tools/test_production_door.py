from __future__ import annotations

import copy
import unittest

from production_door import compile_c282


def face(vertices, texture="minecraft:block/door"):
    return {"texture": texture, "vertices": [list(vertex) for vertex in vertices], "source_component": 1, "source_element": 0}


def source_cube():
    # Tagged application geometry after the existing y=270 source application.
    return [
        face([[.3125, -1, -1, 0, 0], [.5625, -1, -1, 6, 0], [.5625, 2, -1, 6, 6], [.3125, 2, -1, 0, 6]]),
        face([[.5625, -1, -1, 0, 0], [.5625, -1, 2, .5, 0], [.5625, 2, 2, .5, 6], [.5625, 2, -1, 0, 6]]),
        face([[.5625, -1, 2, 0, 0], [.3125, -1, 2, 6, 0], [.3125, 2, 2, 6, 6], [.5625, 2, 2, 0, 6]]),
        face([[.3125, -1, 2, 0, 0], [.3125, -1, -1, .5, 0], [.3125, 2, -1, .5, 6], [.3125, 2, 2, 0, 6]]),
    ]


def input_definition():
    return {"id": "o_c282", "logical": True, "creative": True, "behavior": "door", "properties": {"facing": ["north"]}, "states": {}, "models": {}}


def input_contract():
    return {"id": "o_c282", "review_id": "C282", "authority": "user", "canonical_anchor": {"cell": [0, 0, 0], "pivot": [.5, 0, .5]}, "placement_policy": "FLOOR", "mirror_policy": "ROTATE_ONLY", "rotations": [0, 90, 180, 270], "collision_policy": "DOOR", "review_source_patterns": [{"exact_source_signature": "source-a"}], "states": {"facing=north,hinge=left,open=false,placement_height=source_height": {"migration_source_pattern": [{"exact_source_signature": "source-a", "components": [{"id": "minecraft:dark_oak_stairs", "offset": [0, 0, 0]}]}]}}}


class ProductionDoorTests(unittest.TestCase):
    def setUp(self):
        self.source = source_cube()
        self.result = compile_c282(input_definition(), input_contract(), self.source)

    def test_closed_art_keeps_uv_and_no_leaf_items(self):
        definition = self.result["definition"]
        self.assertNotIn("hinge", definition["properties"])
        self.assertEqual(definition["id"], "o_c282")
        self.assertEqual(len(definition["states"]), 8)
        closed = self.result["meshes"][definition["models"]["facing=north,open=false"]]["polygons"]
        self.assertTrue(any(vertex[3:] == [6, 6] for polygon in closed for vertex in polygon["vertices"]))
        self.assertEqual(self.source, source_cube())

    def test_outer_leaf_pivots_are_invariant_for_every_facing(self):
        for facing in ("north", "east", "south", "west"):
            mesh = self.result["meshes"][self.result["definition"]["models"][f"facing={facing},open=true"]]
            pivots = mesh["leaf_pivots"]
            self.assertEqual(pivots["left"], (-1.0, .4375))
            self.assertEqual(pivots["right"], (2.0, .4375))
            self.assertTrue(mesh["collective"])

    def test_open_has_two_boxes_and_clear_central_passage(self):
        state = self.result["contract"]["states"]["facing=north,open=true"]
        self.assertEqual(len(state["collision_footprint"]["boxes"]), 2)
        left, right = state["collision_footprint"]["boxes"]
        self.assertLess(left[3], right[0])
        self.assertTrue(all(cell[1] >= 0 for cell in state["interaction_footprint"]["cells"]))

    def test_every_vertex_moves_about_outer_hinge_not_object_center(self):
        # Independent scalar equations, including UV invariance and all facings.
        def local(v,turns):
            x,z=v[0]-.5,v[2]-.5
            for _ in range((4-turns)%4):x,z=-z,x
            return x+.5,z+.5
        def polygons(facing,opened):
            ident=self.result['definition']['models'][f'facing={facing},open={str(opened).lower()}']
            return self.result['meshes'][ident]['polygons']
        north=polygons('north',False)
        # The two lists have equal size for this symmetric single-panel fixture.
        half=len(north)//2
        for turns,facing in enumerate(('north','east','south','west')):
            for index,(closed,opened) in enumerate(zip(polygons(facing,False),polygons(facing,True))):
                hx=-1 if index<half else 2;hz=.4375;sign=1 if index<half else -1
                for a,b in zip(closed['vertices'],opened['vertices']):
                    ax,az=local(a,turns);bx,bz=local(b,turns)
                    self.assertAlmostEqual(hx-sign*(az-hz),bx)
                    self.assertAlmostEqual(hz+sign*(ax-hx),bz)
                    self.assertEqual(a[1],b[1]);self.assertEqual(a[3:],b[3:])

    def test_source_identity_is_retained_only_for_closed_state(self):
        closed = self.result["contract"]["states"]["facing=north,open=false"]
        opened = self.result["contract"]["states"]["facing=north,open=true"]
        self.assertEqual(closed["migration_source_pattern"][0]["exact_source_signature"], "source-a")
        self.assertEqual(opened["migration_source_pattern"], [])


if __name__ == "__main__":
    unittest.main()
