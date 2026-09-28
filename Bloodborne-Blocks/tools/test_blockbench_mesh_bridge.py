#!/usr/bin/env python3
import copy
import json
import gzip
import tempfile
from pathlib import Path
import unittest

from blockbench_mesh_bridge import bbmodel_to_mesh, mesh_digest, mesh_to_bbmodel, json_bytes
from export_blockbench_mesh_bundle import iter_meshes


class BlockbenchMeshBridgeTest(unittest.TestCase):
    def test_stream_rejects_truncation_and_accepts_pretty_json(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'meshes.gz'
            for value in ['{', '{"m":', '{"m":{},', '{"m":{},}', '{"m":{},"m":{}}', '{} garbage']:
                path.write_bytes(gzip.compress(value.encode()))
                with self.assertRaises((ValueError, json.JSONDecodeError)):
                    list(iter_meshes(path))
            path.write_bytes(gzip.compress(json.dumps({'m': {'polygons': []}}, indent=2).encode()))
            self.assertEqual([('m', {'polygons': []})], list(iter_meshes(path)))

    def test_editor_position_quantization_preserves_original(self):
        mesh = {'polygons': [{'texture': 'bloodborne_blocks:block/stone_bricks',
                 'vertices': [[1.478111, 0, 0, 0, 0], [1.654888, 0, 0, 16, 0], [1.654888, 1, 0, 16, 16]]}]}
        document = mesh_to_bbmodel('quantized', mesh)
        document = json.loads(json_bytes(document))
        bridge = document.pop('bloodborne_mesh_bridge')
        for vertex in document['elements'][0]['vertices'].values():
            vertex[:] = [round(value, 5) for value in vertex]
        _, imported = bbmodel_to_mesh(document, bridge)
        self.assertEqual(mesh_digest(mesh), mesh_digest(imported))

    def test_disk_roundtrip_preserves_more_than_ten_faces(self):
        mesh = {"polygons": [{"texture": "bloodborne_blocks:block/stone_bricks",
            "vertices": [[i, 0, 0, 0, 16], [i+1, 0, 0, 16, 16], [i, 1, 0, 0, 0]]}
            for i in range(12)]}
        _, restored = bbmodel_to_mesh(json.loads(json_bytes(mesh_to_bbmodel("many_faces", mesh))))
        self.assertEqual(mesh_digest(mesh), mesh_digest(restored))
        saved = json.loads(json_bytes(mesh_to_bbmodel('many_faces', mesh)))
        sidecar = saved.pop('bloodborne_mesh_bridge')
        self.assertIsInstance(saved['elements'][0]['faces']['face0']['texture'], int)
        _, restored = bbmodel_to_mesh(saved, sidecar)
        self.assertEqual(mesh_digest(mesh), mesh_digest(restored))
        saved['elements'][0]['rotation'] = [0, 90, 0]
        with self.assertRaisesRegex(ValueError, 'transform'):
            bbmodel_to_mesh(saved, sidecar)

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
