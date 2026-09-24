"""Independent invariants for every generated family/state plus synthetic defects."""
import copy
import gzip
import json
import unittest

from normalize_support_contracts import (RES, ROOT, EPSILON, FUNCTIONAL, TRANSFORM, bounds,
    digest, mount, normalize, plane_for_state, points, source_identity, union_volume)
from logical_contract_v2 import load_contracts, rotate_box, rotate_cell, master_origin
from test_support_collision import cube


PRODUCTION_INPUTS = ROOT / 'docs' / 'production-authoring-inputs.json.gz'
# These state-mesh identities intentionally differ from the frozen all-palette
# evidence. C282 is a user-directed semantic correction; the remainder are
# production mesh deduplication outputs. Their non-mesh source identities still
# must agree with the frozen authoritative input.
PRODUCTION_MESH_IDENTITY_EXCEPTIONS = {
    'o_c001', 'o_c008_1', 'o_c008_2', 'o_c008_3', 'o_c008_5', 'o_c1962_b',
    'o_c1979_1', 'o_c1979_2', 'o_c1979_3', 'o_c1979_4', 'o_c1979_5', 'o_c282',
    'o_c561', 'o_c618',
}


def fixture(low=(0,-.25,0), high=(1,1,1), *, policy='FLOOR', collision_policy='SIMPLE_BOX'):
    meshes, states = {}, {}
    for yaw, facing in zip((0,90,180,270), ('north','east','south','west')):
        polygons = cube(low, high)
        for polygon in polygons:
            for vertex in polygon['vertices']:
                rotated = rotate_cell((vertex[0]-.5,vertex[1],vertex[2]-.5),yaw,TRANSFORM)
                vertex[:3] = [rotated[i]+(.5,0,.5)[i] for i in range(3)]
        ident = 'mesh_'+facing
        meshes[ident] = {'polygons':polygons}
        states['facing='+facing] = {'rotation':yaw,
            'render_mesh':{'id':ident, 'bounds':bounds(points(meshes[ident], (0,0,0))), 'offset':[0,0,0]},
            'selection_footprint':{'boxes':[rotate_box([*low,*high],yaw,TRANSFORM)]},
            'collision_footprint':{'boxes':[[.1,0,.1,.9,1,.9]]},
            'interaction_footprint':{'cells':[[0,0,0]]},
            'migration_source_pattern':[{'components':[{'id':'minecraft:stone','properties':{},'offset':[3,-7,1]}]}]}
    family = {'id':'o_test','canonical_anchor':{'cell':[0,0,0],'pivot':[.5,0,.5]},
              'placement_policy':policy,'collision_policy':collision_policy,'states':states}
    return {'families':[family]}, meshes


class SupportNormalizationTests(unittest.TestCase):
    def test_floor_correction_is_yaw_invariant_idempotent_and_preserves_source(self):
        data, meshes = fixture()
        identity = copy.deepcopy(source_identity(data['families'][0]))
        normalized, report = normalize(data, meshes)
        self.assertEqual(source_identity(normalized['families'][0]), identity)
        for state in normalized['families'][0]['states'].values():
            self.assertEqual(state['render_mesh']['offset'], [0,.25,0])
            self.assertAlmostEqual(min(v[1] for v in points(meshes[state['render_mesh']['id']],state['render_mesh']['offset'])),0)
            self.assertTrue(all(c[1]>=0 for c in state['interaction_footprint']['cells']))
        again, _ = normalize(normalized, meshes)
        self.assertEqual(again, normalized)
        self.assertEqual(digest(again), digest(normalized))
        self.assertEqual(report['summary']['automatically_corrected_families'],1)

    def test_already_above_floor_is_never_lowered(self):
        data, meshes = fixture(low=(0,.2,0))
        result, report = normalize(data,meshes)
        for state in result['families'][0]['states'].values():
            self.assertEqual(state['render_mesh']['offset'],[0,0,0])
        self.assertEqual(report['summary']['automatically_corrected_families'],0)

    def test_explicit_wall_plane_uses_rotated_pivot_not_floor_y(self):
        data, meshes = fixture(low=(0,-.25,.8),high=(1,1,1.4),policy='WALL_ADJACENT')
        data['families'][0]['support_plane'] = {'normal':[0,0,-1], 'distance':-1, 'reason':'explicit back support z=1'}
        result, _ = normalize(data,meshes)
        for state in result['families'][0]['states'].values():
            expected = rotate_cell((0,0,-.4), state['rotation'],TRANSFORM)
            for actual,wanted in zip(state['render_mesh']['offset'],expected):
                self.assertAlmostEqual(actual,wanted)
            normal,distance = plane_for_state(data['families'][0]['support_plane'],state)
            verts = points(meshes[state['render_mesh']['id']],state['render_mesh']['offset'])
            self.assertAlmostEqual(min(sum(normal[i]*v[i] for i in range(3))-distance for v in verts),0)
            self.assertEqual(min(v[1] for v in verts),-.25)

    def test_ambiguous_mount_does_not_shift_render_or_gameplay(self):
        data, meshes = fixture(policy='WALL_ADJACENT')
        result, report = normalize(data,meshes)
        self.assertEqual(result,data)
        self.assertEqual(report['summary']['ambiguous_mount_families'],1)
        self.assertEqual(report['families'][0]['status'],'WARN')

    def test_conflicting_floor_plane_fails_instead_of_bypassing_floor_gate(self):
        data, meshes = fixture()
        data['families'][0]['support_plane'] = {
            'normal':[0,0,-1], 'distance':-1, 'reason':'conflicts with FLOOR'}
        result, report = normalize(data,meshes)
        self.assertEqual(result,data)
        self.assertEqual(report['summary']['fail_families'],1)
        self.assertEqual(report['families'][0]['status'],'FAIL')
        self.assertTrue(all('AMBIGUOUS_MOUNT_POLICY' in row['findings']
                            for row in report['families'][0]['states']))

    def test_render_and_helper_below_floor_are_independent(self):
        data,meshes = fixture(low=(0,.2,0))
        for state in data['families'][0]['states'].values():
            state['collision_footprint']['boxes'] = [[.1,-1,.1,.9,1,.9]]
            state['interaction_footprint']['cells'].append([0,-1,0])
        result,report = normalize(data,meshes)
        self.assertEqual(report['summary']['automatically_corrected_families'],0)
        self.assertEqual(report['summary']['families_with_old_helpers_below'],1)
        for state in result['families'][0]['states'].values():
            self.assertEqual(state['collision_footprint']['boxes'],[[.1,0,.1,.9,1,.9]])
            self.assertEqual(state['interaction_footprint']['cells'],[[0,0,0]])

    def test_future_excessive_collision_synthetic_27_to_one(self):
        data, meshes = fixture(low=(0,0,0))
        for state in data['families'][0]['states'].values():
            state['collision_footprint']['boxes'] *= 27
        result, report = normalize(data,meshes)
        self.assertEqual(result['families'][0]['collision_policy'],'SIMPLE_BOX')
        for state in report['families'][0]['states']:
            self.assertEqual((state['old_collision_boxes'],state['new_collision_boxes']),(27,1))
        self.assertEqual(report['summary']['fail_families'],0)

    def test_future_missing_policy_gets_proven_main_mass_not_empty_collision(self):
        data, meshes = fixture(low=(0,0,0))
        del data['families'][0]['collision_policy']
        result, report = normalize(data,meshes)
        self.assertEqual(result['families'][0]['collision_policy'],'SIMPLE_BOX')
        self.assertEqual(report['summary']['fail_families'],0)
        self.assertTrue(all(len(state['collision_footprint']['boxes'])==1
                            for state in result['families'][0]['states'].values()))

    def test_unknown_collision_with_open_art_is_fail_not_silent_none(self):
        data,meshes = fixture(collision_policy='UNKNOWN')
        for mesh in meshes.values():
            mesh['polygons'] = mesh['polygons'][:2]
        result, report = normalize(data,meshes)
        self.assertEqual(result['families'][0]['collision_policy'],'UNKNOWN')
        self.assertEqual(report['summary']['fail_families'],1)

    def test_fallback_refuses_non_equivalent_facing_geometry(self):
        data, meshes = fixture(low=(0,0,0),collision_policy='UNKNOWN')
        _, changed = fixture(low=(0,0,0),high=(2,1,1))
        meshes['mesh_east'] = changed['mesh_east']
        result, report = normalize(data,meshes)
        self.assertEqual(result['families'][0]['collision_policy'],'UNKNOWN')
        self.assertEqual(report['summary']['fail_families'],1)
        self.assertTrue(all('equivalent canonical physical masses' in row['collision_ambiguous_reason']
                            for row in report['families'][0]['states']))

    def test_union_volume_does_not_double_count_overlaps(self):
        self.assertEqual(union_volume([[0,0,0,1,1,1],[.5,0,0,1.5,1,1]]),1.5)

    def test_every_current_family_state_and_source_identity(self):
        data,transform = load_contracts(RES)
        with gzip.open(RES/'meshes.json.gz','rt',encoding='utf8') as stream:
            meshes = json.load(stream)
        with gzip.open(PRODUCTION_INPUTS,'rt',encoding='utf8') as stream:
            frozen = json.load(stream)
        frozen_by_id = {family['id']:family for family in frozen['contracts']['families']}
        manifest = json.loads((ROOT/'docs/production-logical-palette.json').read_text(encoding='utf8'))
        expected_ids = {row['id'] for row in manifest['objects']}
        self.assertEqual({family['id'] for family in data['families']},expected_ids)
        self.assertTrue(PRODUCTION_MESH_IDENTITY_EXCEPTIONS <= expected_ids)
        normalized, report = normalize(data,meshes)
        self.assertEqual(data, normalized)
        self.assertEqual(report['summary']['fail_families'],0)
        self.assertEqual(report['summary']['below_support_families'],0)
        checks = 0
        for family in data['families']:
            frozen_identity = source_identity(frozen_by_id[family['id']])
            current_identity = source_identity(family)
            if family['id'] not in PRODUCTION_MESH_IDENTITY_EXCEPTIONS:
                self.assertEqual(current_identity,frozen_identity)
            else:
                # Preserve all non-mesh source authority while allowing only
                # the declared production C282/dedup mesh substitutions.
                self.assertNotEqual(current_identity,frozen_identity)
                self.assertEqual({key:value for key,value in current_identity.items() if key!='states'},
                                 {key:value for key,value in frozen_identity.items() if key!='states'})
            for state in family['states'].values():
                if family['collision_policy'] not in FUNCTIONAL:
                    self.assertLessEqual(len(state['collision_footprint']['boxes']),3)
                if family['placement_policy']!='FLOOR':
                    continue
                render = state['render_mesh']
                vertices = points(meshes[render['id']],render['offset'])
                min_y = min(v[1] for v in vertices)
                self.assertGreaterEqual(min_y,-EPSILON)
                self.assertTrue(all(c[1]>=0 for c in state['interaction_footprint']['cells']))
                for yaw in (0,90,180,270):
                    self.assertAlmostEqual(min(rotate_cell(v,yaw,transform)[1] for v in vertices),min_y)
                    root = (100,64,-50)
                    anchor = rotate_cell(family['canonical_anchor']['cell'],yaw,transform)
                    placement = tuple(root[i]+anchor[i] for i in range(3))
                    self.assertEqual(master_origin(placement,family['canonical_anchor']['cell'],yaw,transform),root)
                    checks += 1
        self.assertGreater(checks,0)


if __name__=='__main__':
    unittest.main()
