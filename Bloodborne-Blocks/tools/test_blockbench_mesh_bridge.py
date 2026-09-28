#!/usr/bin/env python3
import copy
import unittest

from blockbench_mesh_bridge import bbmodel_to_mesh, mesh_digest, mesh_to_bbmodel


class BlockbenchMeshBridgeTest(unittest.TestCase):
    def test_round_trip_and_controlled_vertex_edit(self):
        mesh = {"polygons": [{"texture": "bloodborne_blocks:block/stone_bricks", "vertices": [[0, 0, 0, 0, 16], [1, 0, 0, 16, 16], [1, 1, 0, 16, 0], [0, 1, 0, 0, 0]]}]}
        document = mesh_to_bbmodel("example_mesh", mesh)
        mesh_id, imported = bbmodel_to_mesh(document)
        self.assertEqual("example_mesh", mesh_id)
        self.assertEqual(mesh_digest(mesh), mesh_digest(imported))
        document["textures"][0]["path"] = "../../textures/bloodborne_blocks/block/stone_bricks.png"
        document["textures"][0]["relative_path"] = document["textures"][0]["path"]
        _, imported_with_bundle_path = bbmodel_to_mesh(document)
        self.assertEqual(mesh_digest(mesh), mesh_digest(imported_with_bundle_path))
        edited = copy.deepcopy(document)
        edited["elements"][0]["vertices"]["v0"][1] = 2.0
        _, changed = bbmodel_to_mesh(edited)
        self.assertEqual(0.125, changed["polygons"][0]["vertices"][0][1])
        self.assertNotEqual(mesh_digest(mesh), mesh_digest(changed))


if __name__ == "__main__":
    unittest.main()
