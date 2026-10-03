import copy
from add_unified_tree_variants import build, key, rotate_cells

def fixture():
    old={"old_id":"city_bush","old_state":"facing=north,variant=7","variant":"chosen","mesh":{"polygons":[{"texture":"tree","vertices":[[0,0,0,0,0],[1,0,0,1,0],[0,1,0,0,1]]}]},"geometry":{"cells":{"0,0,0":{"collision":[[0,0,0,1,1,1]],"outline":[[0,0,0,1,1,1]]}},"globalOutline":[0,0,0,1,1,1]},"contract":{},"footprint":{}}
    definition={"blocks":[{"id":"o_c001","properties":{"facing":["north","east","south","west"],"variant":["tree_old"],"visual":["base","alt"]},"default":{"facing":"north","variant":"tree_old","visual":"base"},"states":{},"models":{}}]}
    return definition,{"blocks":{"o_c001":{"states":{}}}},{},{"families":[{"id":"o_c001","states":{}}]},{"families":{"o_c001":{}}},[old]

def test_splits_bush_into_a_facing_only_logical_id():
    output,migration,added,_=build(*fixture());definitions,geometry,meshes,contracts,physical=output
    tree=next(row for row in definitions["blocks"] if row["id"]=="o_c001")
    bush=next(row for row in definitions["blocks"] if row["id"]=="o_dry_bush")
    assert added==["o_dry_bush"]
    assert tree["properties"]["variant"]==["tree_old"] and tree["placement_properties"]=={"variant":"tree_old"} and not tree["states"]
    assert "variant" not in bush["properties"] and "variant" not in bush.get("placement_properties",{}) and len(bush["states"])==8
    assert set(bush["models"])==set(geometry["blocks"]["o_dry_bush"]["states"])==set(next(row for row in contracts["families"] if row["id"]=="o_dry_bush")["states"])
    assert all("0,0,0" in row["cells"] for row in geometry["blocks"]["o_dry_bush"]["states"].values())
    assert migration["states"]["bloodborne_blocks:city_bush[facing=north,variant=7]"]["rootOffset"]==[0,0,0]

def test_moves_existing_mixed_bush_without_discarding_its_contract_or_mesh():
    values=list(fixture());definition,geometry,meshes,contracts,physical,_=values
    state="facing=west,variant=bush_asset_e,visual=base"
    definition["blocks"][0]["states"][state]=[0,0,0];definition["blocks"][0]["models"][state]="old_bush_mesh";definition["blocks"][0]["visual_models"]={state:"bloodborne_blocks:block/logical/o_c001/base/facing_west_variant_bush_asset_e"}
    original_shape={"cells":{"0,0,0":{"collision":[],"outline":[]}}};geometry["blocks"]["o_c001"]["states"][state]=original_shape
    meshes["old_bush_mesh"]={"polygons":[]}
    contracts["families"][0]["states"][state]={"marker":"preserved"};physical["families"]["o_c001"][state]={"marker":"preserved"}
    values[-1]=[]
    output,migration,_,removed=build(*values);definitions,geometry,meshes,contracts,physical=output
    tree=next(row for row in definitions["blocks"] if row["id"]=="o_c001");bush=next(row for row in definitions["blocks"] if row["id"]=="o_dry_bush")
    target="facing=west,visual=base"
    assert state not in tree["states"] and "bush_asset_e" not in tree["properties"]["variant"]
    assert bush["models"][target]=="old_bush_mesh" and bush["visual_models"][target].startswith("bloodborne_blocks:block/logical/o_dry_bush/")
    assert geometry["blocks"]["o_dry_bush"]["states"][target]==original_shape
    assert next(row for row in contracts["families"] if row["id"]=="o_dry_bush")["states"][target]["marker"]=="preserved"
    assert physical["families"]["o_dry_bush"][target]["marker"]=="preserved"
    assert migration["states"][f"bloodborne_blocks:o_c001[{state}]"]["id"]=="bloodborne_blocks:o_dry_bush" and removed==[(state,"bloodborne_blocks:block/logical/o_c001/base/facing_west_variant_bush_asset_e")]

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
    test_splits_bush_into_a_facing_only_logical_id();test_moves_existing_mixed_bush_without_discarding_its_contract_or_mesh();test_rejects_collision_over_four_boxes();test_asymmetric_boxes_and_outline_rotate_with_the_bush();print('Unified tree/bush tests PASS')
