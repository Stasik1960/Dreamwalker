"""Flat offsets must remove coplanarity without stretching volume or changing UV."""
import copy,unittest
import final_v1274_flat_layers as layer
import final_v1274_surface_caps as cap

FACE={'texture':'#original','uv':[16,3,1,15],'rotation':90,'tintindex':1}
USE=[{'path':'fixture','variant':0,'pose':'closed','part':{'offset':[0,0,0]}}]

class FlatLayersTest(unittest.TestCase):
    def test_normalized_floor_plane_clears_support_and_keeps_uv_and_size(self):
        old=[{'from':[-16,16.05,-16],'to':[32,16.05,32],'faces':{'up':copy.deepcopy(FACE)}}]
        uses=copy.deepcopy(USE);uses[0]['part']['offset']=[0,-16.05,0]
        output,actions=layer.repair_layers(old,old,uses)
        self.assertEqual(len(actions),1)
        self.assertAlmostEqual(output[0]['to'][1]-16.05,1/64,places=11)
        self.assertEqual(output[0]['faces'],old[0]['faces'])
        self.assertEqual(output[0]['to'][1]-output[0]['from'][1],0)
        self.assertEqual(output[0]['from'][::2],old[0]['from'][::2])

    def test_real_volume_untouched_but_single_visible_plane_with_bbox_can_move(self):
        volume={'from':[0,0,0],'to':[16,16,16],'faces':{s:copy.deepcopy(FACE) for s in cap.SIDES}}
        virtual={'from':[20,-16,0],'to':[30,0,16],'faces':{'up':copy.deepcopy(FACE)}}
        output,actions=layer.repair_layers([volume,virtual],[volume,virtual],USE)
        self.assertEqual(output[0],volume);self.assertEqual(len(actions),1)
        for axis in range(3):self.assertEqual(output[1]['to'][axis]-output[1]['from'][axis],virtual['to'][axis]-virtual['from'][axis])

    def test_overlapping_layers_have_deterministic_depth_order(self):
        old=[{'from':[0,0,0],'to':[16,0,16],'faces':{'up':copy.deepcopy(FACE)}},
             {'from':[8,0,8],'to':[24,0,24],'faces':{'up':copy.deepcopy(FACE)}}]
        output,actions=layer.repair_layers(old,old,USE)
        self.assertEqual([a['deterministicLayerRank'] for a in actions],[0,1])
        self.assertEqual(output[0]['from'][1],layer.GAP)
        self.assertEqual(output[1]['from'][1],layer.GAP+layer.LAYER_STEP)
        self.assertEqual(output,layer.repair_layers(old,old,USE)[0])

    def test_opposite_faces_split_and_move_outward_without_uv_repaint(self):
        old=[{'from':[0,0,0],'to':[16,0,16],'faces':{'up':copy.deepcopy(FACE),'down':copy.deepcopy(FACE)}}]
        output,actions=layer.repair_layers(old,old,USE)
        self.assertEqual(len(output),2)
        positions={next(iter(e['faces'])):e['from'][1] for e in output}
        self.assertEqual(positions,{'up':layer.GAP,'down':-layer.GAP})
        for element in output:
            self.assertEqual(element['from'][1],element['to'][1])
            self.assertEqual(next(iter(element['faces'].values())),FACE)

    def test_rotation_about_normal_allowed_tilted_arch_plane_untouched(self):
        flat={'from':[0,0,0],'to':[16,0,16],'faces':{'up':copy.deepcopy(FACE)},
              'rotation':{'axis':'y','angle':45,'origin':[8,0,8],'rescale':True}}
        tilted=copy.deepcopy(flat);tilted['rotation']['axis']='x'
        self.assertIsNotNone(layer.single_plane(flat));self.assertIsNone(layer.single_plane(tilted))
        output,actions=layer.repair_layers([tilted],[tilted],USE)
        self.assertEqual(output,[tilted]);self.assertEqual(actions,[])
        output,_=layer.repair_layers([flat],[flat],USE)
        before=layer.element_point(flat,[2,0,3]);after=layer.element_point(output[0],[2,layer.GAP,3])
        self.assertAlmostEqual(after[1]-before[1],layer.GAP)
        self.assertEqual(after[::2],before[::2])

    def test_existing_non_coplanar_float_surface_is_not_changed(self):
        old=[{'from':[0,9,0],'to':[16,9,16],'faces':{'up':copy.deepcopy(FACE)}}]
        use=copy.deepcopy(USE);use[0]['part']['offset']=[0,-8,0]
        output,actions=layer.repair_layers(old,old,use)
        self.assertEqual(output,old);self.assertEqual(actions,[])

    def test_rendered_polygon_overlap_uses_rotated_plane_not_unrotated_bbox(self):
        a=[(0,0),(4,0),(4,4),(0,4)]
        touching=[(4,0),(5,0),(5,4),(4,4)]
        overlap=[(3,1),(5,1),(5,3),(3,3)]
        self.assertFalse(layer.polygon_overlap(a,touching));self.assertTrue(layer.polygon_overlap(a,overlap))

    def test_all_six_boundary_normals_reverse_only_tiny_shift(self):
        for side,(axis,sign) in cap.SIDES.items():
            for bbox_depth in (0,8):
                with self.subTest(side=side,bbox_depth=bbox_depth):
                    old={'from':[0,0,0],'to':[16,16,16],'shade':False,'faces':{side:copy.deepcopy(FACE)}}
                    plane=32 if sign>0 else -16
                    old['from'][axis]=plane-bbox_depth if sign>0 else plane
                    old['to'][axis]=plane if sign>0 else plane+bbox_depth
                    output,actions=layer.repair_layers([old],[old],USE)
                    self.assertEqual(len(actions),1)
                    self.assertEqual(actions[0]['translationModelUnits'],-sign*layer.GAP)
                    self.assertEqual(actions[0]['nativeBoundaryDirection'],'inward')
                    self.assertTrue(layer.native_bounds_legal(output[0]))
                    self.assertEqual(output[0]['faces'],old['faces'])
                    self.assertEqual(output[0]['shade'],old['shade'])
                    for a in range(3):
                        self.assertEqual(output[0]['to'][a]-output[0]['from'][a],old['to'][a]-old['from'][a])
                        if a!=axis:self.assertEqual((output[0]['from'][a],output[0]['to'][a]),(old['from'][a],old['to'][a]))

    def test_inside_native_boundaries_retains_outward_and_layer_order(self):
        for side,(axis,sign) in cap.SIDES.items():
            with self.subTest(side=side):
                old={'from':[0,0,0],'to':[16,16,16],'faces':{side:copy.deepcopy(FACE)}}
                old['from'][axis]=old['to'][axis]=16 if sign>0 else 0
                requested=sign*(layer.GAP+layer.LAYER_STEP)
                self.assertEqual(layer.legal_normal_shift(old,axis,requested,[side]),requested)

    def test_cannot_silently_accept_invalid_or_untranslatable_bbox(self):
        old={'from':[-16,0,0],'to':[32,16,16],'faces':{'west':copy.deepcopy(FACE)}}
        with self.assertRaises(ValueError):layer.legal_normal_shift(old,0,-layer.GAP,['west'])
        old['from'][0]=-16.01
        with self.assertRaises(ValueError):layer.legal_normal_shift(old,0,-layer.GAP,['west'])

if __name__=='__main__':unittest.main()
