import unittest
from audit_production_geometry import polygon_components


class ProductionGeometryTests(unittest.TestCase):
    def test_shared_edge_components_do_not_claim_solid_exposure(self):
        polygons = [{"vertices": [[0, 0, 0], [1, 0, 0], [1, 1, 0]]},
                    {"vertices": [[1, 1, 0], [1, 0, 0], [2, 0, 0]]}]
        result = polygon_components(polygons)
        self.assertEqual(result["shared_edge_components"], 1)
        self.assertGreater(result["boundary_edges"], 0)


if __name__ == "__main__": unittest.main()
