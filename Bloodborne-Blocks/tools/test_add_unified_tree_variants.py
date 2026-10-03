import copy
from add_unified_tree_variants import build, key, rotate_cells

def fixture():
    old={"old_id":"city_bush","old_state":"facing=north,variant=7","variant":"chosen","mesh":{"polygons":[{"texture":"tree","vertices":[[0,0,0,0,0],[1,0,0,1,0],[0,1,0,0,1]]}]},"geometry":{"cells":{"0,0,0":{"collision":[[0,0,0,1,1,1]],"outline":[[0,0,0,1,1,1]]}},"globalOutline":[0,0,0,1,1,1]},"contract":{},"footprint":{}}
    definition={"blocks":[{"id":"o_c001","properties":{"facing":["north","east","south","west"],"variant":["tree_old"],"visual":["base","alt"]},"states":{},"models":{}}]}
    return definition,{"blocks":{"o_c001":{"states":{}}}},{},{"families":[{"id":"o_c001","states":{}}]},{"families":{"o_c001":{}}},[old]

def test_appends_bush_states_without_new_logical_id():
    output,migration,added=build(*fixture());definitions,geometry,meshes,contracts,physical=output;d=definitions["blocks"][0]
    assert added==["bush_chosen"]
    assert d["id"]=="o_c001" and len(d["states"])==8
    assert set(d["models"])==set(geometry["blocks"]["o_c001"]["states"])==set(contracts["families"][0]["states"])
    assert all("0,0,0" in row["cells"] for row in geometry["blocks"]["o_c001"]["states"].values())
    assert migration["states"]["city_bush[facing=north,variant=7]"]["rootOffset"]==[0,0,0]

def test_rejects_collision_over_four_boxes():
    values=list(fixture());values[-1][0]["geometry"]["cells"]["0,0,0"]["collision"]=[[0,0,0,1,1,1]]*5
    try:build(*values)
    except ValueError as error:assert "collision" in str(error)
    else:raise AssertionError("expected collision validation")

def test_asymmetric_boxes_and_outline_rotate_with_the_bush():
    shape={'cells':{'0,0,0':{'collision':[[.1,0,.2,.3,1,.8]],'outline':[[.1,0,.2,.3,1,.8]]},'2,0,0':{'collision':[],'outline':[]}},'globalOutline':[0,0,0,3,1,1]}
    rotated=rotate_cells(shape,1)
    assert '0,0,2'in rotated['cells']
    assert all(abs(a-b)<1e-9 for a,b in zip(rotated['cells']['0,0,0']['collision'][0],[.2,0,.1,.8,1,.3]))
    assert rotated['globalOutline']==[0,0,0,1,1,3]

if __name__=='__main__':
    test_appends_bush_states_without_new_logical_id();test_rejects_collision_over_four_boxes();test_asymmetric_boxes_and_outline_rotate_with_the_bush();print('Unified tree/bush tests PASS')
