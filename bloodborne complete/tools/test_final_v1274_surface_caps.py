"""Regression proofs: partial caps cannot cover original alpha art or change UV."""
import copy,unittest
import final_v1274_surface_caps as tool
import final_v1274_door_90986_caps as door_caps

# Independently written ordered native face corners, expressed as physical axes.
NATIVE_FACE_AXES={
    'up':((0,0),(0,1),(1,1),(1,0)),
    'down':((0,1),(0,0),(1,0),(1,1)),
    'north':((1,1),(1,0),(0,0),(0,1)),
    'south':((0,1),(0,0),(1,0),(1,1)),
    'west':((1,0),(0,0),(0,1),(1,1)),
    'east':((1,1),(0,1),(0,0),(1,0)),
}
def independent_uv(element,side,face,point):
    axis,_=tool.SIDES[side];axes=[a for a in range(3) if a!=axis]
    coordinates=[(point[a]-element['from'][a])/(element['to'][a]-element['from'][a]) for a in axes]
    # Bilinear weights for the four physical rectangular corners.
    result=[0.,0.];u0,v0,u1,v1=face['uv']
    for index,(x,y) in enumerate(NATIVE_FACE_AXES[side]):
        weight=(coordinates[0] if x else 1-coordinates[0])*(coordinates[1] if y else 1-coordinates[1])
        index=(index+face.get('rotation',0)//90)%4
        uv=((u0,v0),(u0,v1),(u1,v1),(u1,v0))[index]
        for a in (0,1):result[a]+=weight*uv[a]
    return result

class CapsTest(unittest.TestCase):
    def test_implicit_uv_becomes_exact_explicit_native_uv_on_cut_plane(self):
        cube={'from':[-6,2,-3],'to':[19,15,23],'faces':{}}
        expected={'up':lambda p:(p[0],p[2]),'down':lambda p:(p[0],16-p[2]),
                  'north':lambda p:(16-p[0],16-p[1]),'south':lambda p:(p[0],16-p[1]),
                  'west':lambda p:(p[2],16-p[1]),'east':lambda p:(16-p[2],16-p[1])}
        for side in tool.SIDES:
            rect=tool.face_rect(cube,side)
            clipped=(rect[0]+1,rect[1]+2,rect[2]-3,rect[3]-4)
            plane=tool.clipped_plane(cube,side,{'texture':'#stone'},clipped)
            axis,_=tool.SIDES[side];axes=[a for a in range(3) if a!=axis]
            point=list(plane['from'])
            for a in axes:point[a]=(plane['from'][a]+plane['to'][a])/2
            actual=independent_uv(plane,side,plane['faces'][side],point)
            for a,b in zip(actual,expected[side](point)):self.assertAlmostEqual(a,b,places=9)

    def test_rotated_flipped_uv_affine_crop_matches_native_sampling(self):
        cube={'from':[-6,2,-3],'to':[19,15,23],'faces':{},'shade':False}
        for side in tool.SIDES:
            full=tool.face_rect(cube,side);dx=full[2]-full[0];dy=full[3]-full[1]
            rect=(full[0]+dx*.17,full[1]+dy*.23,full[0]+dx*.79,full[1]+dy*.83)
            for rotation in (0,90,180,270):
                for uv in ([2,3,14,12],[14,3,2,12],[2,12,14,3],[14,12,2,3]):
                    face={'texture':'#opaque','uv':uv,'rotation':rotation,'tintindex':2}
                    plane=tool.clipped_plane(cube,side,face,rect)
                    axis,_=tool.SIDES[side];axes=[a for a in range(3) if a!=axis]
                    for s,t in ((0,0),(1,1),(.3,.7),(.8,.2)):
                        point=list(plane['from']);point[axes[0]]+=s*(plane['to'][axes[0]]-point[axes[0]])
                        point[axes[1]]+=t*(plane['to'][axes[1]]-point[axes[1]])
                        before=independent_uv(cube,side,face,point);after=independent_uv(plane,side,plane['faces'][side],point)
                        for a,b in zip(before,after):self.assertAlmostEqual(a,b,places=9,msg=(side,rotation,uv,point))
                    self.assertFalse(plane['shade']);self.assertEqual(plane['faces'][side]['tintindex'],2)

    def test_original_alpha_plane_reserved_and_remaining_uv_coverage_exact(self):
        old=[{'from':[0,0,0],'to':[16,16,4],'faces':{'north':{'texture':'#stone','uv':[0,0,16,16]},'west':{'texture':'#stone','uv':[0,0,16,16]}}},
             {'from':[4,4,4],'to':[12,12,4],'faces':{'south':{'texture':'#deliberately_fully_transparent','uv':[1,2,8,9]}}}]
        original=copy.deepcopy(old);current=copy.deepcopy(old)
        current[0]['faces']['south']={'texture':'#stone','uv':[16,0,0,16],'rotation':90}
        repaired,actions=tool.repair(old,current)
        self.assertEqual(old,original);self.assertNotIn('south',repaired[0]['faces'])
        self.assertEqual(repaired[1],old[1]);self.assertEqual(actions[0]['remainingArea'],192)
        pieces=repaired[2:];self.assertEqual(len(pieces),4)
        self.assertEqual(sum(tool.area(tool.face_rect(p,'south')) for p in pieces),192)
        for x in (1,5,9,13):
            for y in (1,5,9,13):
                covered=sum(p['from'][0]<x<p['to'][0] and p['from'][1]<y<p['to'][1] for p in pieces)
                self.assertEqual(covered,0 if 4<x<12 and 4<y<12 else 1)

    def test_occluding_volume_partial_area_and_deterministic_added_dedup(self):
        face={'texture':'#stone','uv':[0,0,16,16]}
        old=[{'from':[0,0,0],'to':[16,16,4],'faces':{'north':face,'west':face}},
             {'from':[4,4,3],'to':[12,12,7],'faces':{'north':face,'up':face}},
             {'from':[0,0,1],'to':[16,16,4],'faces':{'north':face,'east':face}}]
        current=copy.deepcopy(old);current[0]['faces']['south']=face;current[2]['faces']['south']=face
        repaired,actions=tool.repair(old,current)
        self.assertEqual([a['remainingArea'] for a in actions],[192,0])
        self.assertEqual(len(repaired),7)
        self.assertEqual(repaired,tool.repair(old,current)[0])
        self.assertEqual(tool.verify_uncovered_caps(old,repaired),4)
        provenance=tool.plane_provenance(len(old),actions)
        self.assertEqual([row['outputElement'] for row in provenance],[3,4,5,6])
        self.assertEqual({row['sourceElement'] for row in provenance},{0})

    def test_3d_bbox_with_only_up_is_not_closed_as_an_invented_solid(self):
        old=[{'from':[-16,-16,-16],'to':[32,0,32],
              'faces':{'up':{'texture':'#cutout_surface','uv':[0,0,16,16]}}}]
        current=copy.deepcopy(old)
        for side in ('north','south','east','west','down'):current[0]['faces'][side]=copy.deepcopy(old[0]['faces']['up'])
        repaired,actions=tool.repair(old,current)
        self.assertEqual(repaired,old)
        self.assertEqual({r['action'] for r in actions},{'removeAddedCapFromAuthoredSinglePlane'})
        self.assertEqual(len(actions),5)

    def test_virtual_bbox_of_single_surface_cannot_hide_another_surface(self):
        face={'texture':'#stone','uv':[0,0,16,16]}
        # Two authored perpendicular planes make the first element a reviewed
        # volumetric candidate; the second has a 3D bbox but only an UP plane.
        old=[{'from':[0,0,0],'to':[16,16,4],'faces':{'north':face,'west':face}},
             {'from':[4,4,3],'to':[12,12,7],'faces':{'up':face}}]
        current=copy.deepcopy(old);current[0]['faces']['south']=face
        repaired,actions=tool.repair(old,current)
        self.assertEqual(actions,[])
        self.assertEqual(repaired,current)

    def test_open_barrel_does_not_acquire_opaque_caps_behind_its_alpha_rim(self):
        rim={'texture':'#original_round_cutout','uv':[2,3,4,5]}
        wood={'texture':'#wood','uv':[0,0,16,16]}
        old=[{'from':[0,0,0],'to':[16,16,16],'faces':{'north':wood,'south':wood,'up':wood,'down':wood}},
             {'from':[0,0,0],'to':[0,16,16],'faces':{'west':rim}}]
        current=copy.deepcopy(old);current[0]['faces']['west']=wood;current[0]['faces']['east']=wood
        result,actions=tool.repair(old,current,preserve_original_opening=True)
        self.assertEqual(result,old)
        self.assertEqual({a['action'] for a in actions},{'removeAddedCapToPreserveOriginalBarrelCutout'})

    def test_original_faces_cannot_be_changed_by_repair(self):
        old=[{'from':[0,0,0],'to':[16,16,16],'faces':{'up':{'texture':'#original','uv':[0,0,16,16]}}}]
        current=copy.deepcopy(old);current[0]['faces']['up']['uv']=[1,0,16,16]
        with self.assertRaisesRegex(ValueError,'Original face/UV changed'):tool.repair(old,current)

    def test_derived_open_door_keeps_fixed_caps_and_moves_only_leaf_provenance(self):
        face={'texture':'#wood','uv':[0,0,16,16]}
        source={'elements':[{'from':[0,0,0],'to':[1,1,1],'faces':{'up':copy.deepcopy(face)}} for _ in range(75)]}
        for index in (0,3):source['elements'][index]={'from':[-9,-16,2],'to':[25,18 if index==0 else 6,14],
                                                    'rotation':{'angle':0,'axis':'y','origin':[8,0,8]},
                                                    'faces':{'north':copy.deepcopy(face),'south':copy.deepcopy(face)}}
        moving_plane={'from':[10,18,2],'to':[18,18,14],'faces':{'up':copy.deepcopy(face)}}
        fixed_plane={'from':[-12,32,-4],'to':[-10,32,20],'faces':{'up':copy.deepcopy(face)}}
        source['elements'] += [moving_plane,fixed_plane]
        edges={side:{'texture':'#own_border','uv':[1,2,3,4]} for side in ('up','down','east','west')}
        frame,leaf,_=door_caps.derive(source,[{'outputElement':75,'sourceElement':0},{'outputElement':76,'sourceElement':19}],edges)
        self.assertIn(fixed_plane,frame['elements']);self.assertNotIn(fixed_plane,leaf['elements'])
        self.assertEqual(leaf['elements'][0]['faces']['up'],edges['up'])
        moving_parts=[e for e in frame['elements']+leaf['elements'] if e['from'][1]==18 and e['to'][1]==18]
        self.assertEqual(len(moving_parts),2)
        for part in moving_parts:
            p=[(part['from'][0]+part['to'][0])/2,18,8]
            before=independent_uv(moving_plane,'up',face,p)
            after=independent_uv(part,'up',part['faces']['up'],p)
            for a,b in zip(before,after):self.assertAlmostEqual(a,b,places=9)

if __name__=='__main__':unittest.main()
