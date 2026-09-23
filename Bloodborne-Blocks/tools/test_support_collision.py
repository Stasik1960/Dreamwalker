import unittest

from support_collision import AMBIGUOUS_COLLISION, simplify


def cube(low, high):
    """Six quad polygons in the mesh polygon shape consumed by simplify."""
    x0, y0, z0 = low
    x1, y1, z1 = high
    faces = [((x0, y0, z0), (x0, y1, z0), (x0, y1, z1), (x0, y0, z1)),
             ((x1, y0, z0), (x1, y0, z1), (x1, y1, z1), (x1, y1, z0)),
             ((x0, y0, z0), (x1, y0, z0), (x1, y0, z1), (x0, y0, z1)),
             ((x0, y1, z0), (x0, y1, z1), (x1, y1, z1), (x1, y1, z0)),
             ((x0, y0, z0), (x0, y1, z0), (x1, y1, z0), (x1, y0, z0)),
             ((x0, y0, z1), (x1, y0, z1), (x1, y1, z1), (x0, y1, z1))]
    return [{"texture": "test", "vertices": [[*point, 0, 0] for point in face]} for face in faces]


class SupportCollisionTests(unittest.TestCase):
    def test_dominant_mass_ignores_thin_handle_and_distant_ornament(self):
        proposal = simplify(cube((0, 0, 0), (1, 1, 1)) + cube((1, .4, .4), (1.1, .6, .6)) + cube((4, 0, 0), (4.15, .15, .15)))
        self.assertEqual(proposal["policy"], "SIMPLE_BOX")
        self.assertEqual(proposal["boxes"], [[0.0, 0.0, 0.0, 1.0, 1.0, 1.0]])
        self.assertEqual(proposal["evidence"]["source_render_element_count"], None)

    def test_two_and_three_separated_substantial_masses_are_bounded(self):
        two = simplify(cube((0, 0, 0), (1, 1, 1)) + cube((2, 0, 0), (3, 1, 1)))
        three = simplify(cube((0, 0, 0), (1, 1, 1)) + cube((2, 0, 0), (3, 1, 1)) + cube((4, 0, 0), (5, 1, 1)))
        self.assertEqual((two["policy"], len(two["boxes"])), ("TWO_BOX", 2))
        self.assertEqual((three["policy"], len(three["boxes"])), ("THREE_BOX", 3))
        self.assertTrue(all(gap > 0 for gap in three["evidence"]["separation_gaps"]))

    def test_sparse_cross_planes_are_ambiguous(self):
        planes = [polygon for face in cube((0, 0, 0), (1, 1, 1))[:3] for polygon in [face]]
        self.assertEqual(simplify(planes)["status"], AMBIGUOUS_COLLISION)

    def test_triangle_aabb_cannot_prove_a_rectangular_face(self):
        triangle_cube = cube((0, 0, 0), (1, 1, 1))
        triangle_cube[0]["vertices"] = triangle_cube[0]["vertices"][:3]
        self.assertEqual(simplify(triangle_cube)["status"], AMBIGUOUS_COLLISION)

    def test_nonfinite_and_oversized_inputs_are_ambiguous(self):
        nonfinite = cube((0, 0, 0), (1, 1, 1))
        nonfinite[0]["vertices"][0][0] = float("nan")
        self.assertEqual(simplify(nonfinite)["status"], AMBIGUOUS_COLLISION)
        self.assertEqual(simplify([{"vertices": []}] * 513)["reason"], "input_polygon_budget_exceeded")

    def test_short_or_noniterable_vertices_fail_closed(self):
        short = cube((0, 0, 0), (1, 1, 1))
        short.append({"vertices": [[0], [1], [2], [3]]})
        self.assertEqual(simplify(short)["reason"], "malformed_vertex_input")
        self.assertEqual(simplify([{"vertices": 7}])["reason"], "malformed_vertex_input")

    def test_yaw_equivariance(self):
        source = cube((0, 0, 0), (1, 2, .5))
        rotated = [{"vertices": [[-vertex[2], vertex[1], vertex[0], vertex[3], vertex[4]] for vertex in polygon["vertices"]]}
                   for polygon in source]
        proposal = simplify(rotated)
        self.assertEqual(simplify(source)["boxes"], [[0.0, 0.0, 0.0, 1.0, 2.0, 0.5]])
        self.assertEqual(proposal["boxes"], [[-0.5, 0.0, 0.0, 0.0, 2.0, 1.0]])

    def test_explicit_tree_uses_central_substantial_vertical_trunk(self):
        mesh = cube((-.2, 0, -.2), (.2, 3, .2)) + cube((-1, 2.4, -1), (1, 4.4, 1))
        proposal = simplify(mesh, "tree")
        self.assertEqual(proposal["policy"], "TRUNK")
        self.assertEqual(proposal["boxes"], [[-0.2, 0.0, -0.2, 0.2, 3.0, 0.2]])

    def test_explicit_column_and_chimney_use_post_policy(self):
        mesh = cube((0, 0, 0), (.5, 3, .5))
        self.assertEqual(simplify(mesh, "column")["policy"], "POST")
        self.assertEqual(simplify(mesh, "chimney")["policy"], "POST")

    def test_never_emits_per_cell_geometry_and_fixture_evidence_is_readable(self):
        # Synthetic fixture: 27 render polygons simplify to one primitive.
        mesh = cube((0, 0, 0), (1, 1, 1)) * 3 + cube((1, .4, .4), (1.1, .6, .6))
        mesh += [{"vertices": [[5, 0, 0, 0, 0]]}] * 3  # render-only, no physical face
        proposal = simplify(mesh)
        self.assertLessEqual(len(proposal["boxes"]), 3)
        self.assertEqual(proposal["evidence"]["source_polygon_count"], 27)
        self.assertIn("solid volume", proposal["evidence"]["occupancy_ratio_definition"])

    def test_functional_kinds_are_excluded(self):
        self.assertEqual(simplify(cube((0, 0, 0), (1, 2, .2)), "functional_door")["status"], AMBIGUOUS_COLLISION)

    def test_four_equal_masses_are_ambiguous_not_silently_truncated(self):
        mesh = sum((cube((index * 2, 0, 0), (index * 2 + 1, 1, 1)) for index in range(4)), [])
        self.assertEqual(simplify(mesh)["reason"], "more_than_three_substantial_masses")

    def test_touching_masses_merge_only_when_their_bounds_are_filled(self):
        filled = simplify(cube((0, 0, 0), (1, 1, 1)) + cube((1, 0, 0), (2, 1, 1)))
        l_shape = simplify(cube((0, 0, 0), (1, 1, 1)) + cube((1, 0, 0), (2, 1, 1)) + cube((0, 0, 1), (1, 1, 2)))
        self.assertEqual((filled["policy"], filled["boxes"]), ("SIMPLE_BOX", [[0.0, 0.0, 0.0, 2.0, 1.0, 1.0]]))
        self.assertEqual(l_shape["reason"], "touching_substantial_masses_leave_empty_volume")

    def test_thin_generic_ornament_is_ambiguous_but_explicit_window_may_be_plane(self):
        plane = cube((0, 0, 0), (10, 10, .01))
        self.assertEqual(simplify(plane)["reason"], "thin_or_noncompact_mass_requires_explicit_window")
        self.assertEqual(simplify([plane[0]], "window")["reason"], "explicit_window_plane")


if __name__ == "__main__":
    unittest.main()
