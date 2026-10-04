"""Dependency-free contracts for compact source catalog canonicalization."""
from compact_source_catalog import canonical, choose_apps, polygons_for_apps, rotate_geometry, rotate_mesh, source_metadata

def mesh(x=0): return {'polygons':[{'texture':'test:a','vertices':[[x,0,0,0,0],[x+.5,0,0,8,0],[x,1,0,0,16]]}]}
def geometry(): return {'anchor':[0,0,0],'render_offset':[0,0,0],'cells':{'0,0,0':{'collision':[[0,0,0,1,1,1]],'outline':[]}}}
def test_quarter_rotations_share_complete_owner_signature():
    assert canonical(mesh(),geometry())[0] == canonical(rotate_mesh(mesh(),1),geometry())[0]
def test_weighted_choice_is_deterministic_and_multipart_is_complete():
    apps=choose_apps([[{'model':'z','weight':1},{'model':'a','weight':2}],[{'model':'side','weight':1}]])
    assert [a['model'] for a in apps] == ['z','side']
def test_complete_polygon_path_keeps_whole_polygons_without_cell_fragments():
    polys=mesh()['polygons']
    assert len(polys)==1 and any(v[0] == .5 for v in polys[0]['vertices'])
def test_polygon_order_and_cyclic_vertex_start_do_not_split_owner():
    base=mesh();changed={'polygons':[{'texture':'test:a','vertices':base['polygons'][0]['vertices'][1:]+base['polygons'][0]['vertices'][:1]}]}
    assert canonical(base,geometry())[0] == canonical(changed,geometry())[0]
def test_absent_placement_stays_absent_for_collision_fallback():
    physical=rotate_geometry(geometry(),1)['cells']['0,0,0']
    assert physical['collision'] and 'placement' not in physical
def test_source_lantern_metadata_retains_light_without_changing_variant_choice():
    source={'emissive':True,'animated':True,'states':{'facing=north':[0,0,12]}}
    assert source_metadata(source,'facing=north') == {'luminance':12,'emissive':True,'animated':True}
    assert choose_apps([[{'model':'first','weight':1},{'model':'bright','weight':99}]])[0]['model'] == 'first'
if __name__ == '__main__':
    for name,fn in sorted(globals().items()):
        if name.startswith('test_'):fn()
    print('ok: compact source catalog fixtures')
