"""Append an explicitly proposed lower-only art variant; retain original variant0."""
import copy
import hashlib
import json
from pathlib import Path
import zipfile
from prepare_tree_selection import tree_selection
ROOT=Path(__file__).resolve().parents[1]
PACK=Path('C:/Users/vakir/Downloads/bbmc_v15_resource (1).zip')
RES=ROOT/'src/architecture/resources'
def sha(data):return hashlib.sha256(data).hexdigest()
def fingerprint(data):return sha(json.dumps(data,sort_keys=True,separators=(',',':')).encode('utf8'))
def write(path,data):path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
def legal_elements(model):
    for element in model['elements']:
        for key in ['from','to']:
            assert len(element[key])==3 and all(-16<=value<=32 for value in element[key]), 'Vanilla model endpoints must be within[-16,32]'
        assert element['rotation']['angle']==0
        for face in element['faces'].values():
            assert face.get('rotation',0)==0 and len(face['uv'])==4
def main():
    path=RES/'bloodborne_dw/composite/prototype_tree.json';descriptor=json.loads(path.read_text(encoding='utf8'))
    original=descriptor['variants'][0]['poses']['closed'];parts=copy.deepcopy(original['parts'])
    lower=[part for part in parts if part['model']=='bloodborne_dw:tree_source/block/melon']
    upper=[part for part in parts if part not in lower]
    assert len(parts)==18 and len(lower)==2 and len(upper)==16
    assert [part['offset']for part in lower]==[[0,16,0],[0,64,0]]
    with zipfile.ZipFile(PACK)as source:
        raw=source.read('assets/minecraft/models/block/white_wool.json');authored=json.loads(raw)
        texture=source.read('assets/minecraft/textures/block/addon/tree_1.png')
    texture_path=RES/'assets/bloodborne_dw/textures/tree_source/block/addon/tree_1.png'
    assert texture_path.read_bytes()==texture
    model={'credit':'Source-authored white_wool two stem quads; proposed lower2x split into legal48-unit panels, no new pixels.',
           'texture_size':authored['texture_size'],'textures':{'0':'bloodborne_dw:tree_source/block/addon/tree_1','particle':'#0'},
           'elements':copy.deepcopy(authored['elements'][:2])}
    upper_model=copy.deepcopy(model)
    for element, upper_element in zip(model['elements'],upper_model['elements']):
        assert element['from'][1]==-16 and element['to'][1]==32 and element['rotation']['angle']==0
        # Vanilla side-face vertex0/3 is the top and uses Vmin. Keep reversed U
        # on east/south. Bottom uses atlasV232..256; top uses V208..232.
        for name, face in element['faces'].items():
            assert name in ('east','west','north','south') and face.get('rotation',0)==0
            assert face['uv'][1]==13 and face['uv'][3]==16
            face['uv'][1]=14.5
            upper_element['faces'][name]['uv'][3]=14.5
    legal_elements(model);legal_elements(upper_model)
    model_path=RES/'assets/bloodborne_dw/models/tree_proposal/lower_source_base.json'
    write(model_path,model)
    write(RES/'assets/bloodborne_dw/models/tree_proposal/lower_source_base_alt.json',{'parent':'bloodborne_dw:tree_proposal/lower_source_base'})
    upper_model_path=model_path.with_name('lower_source_base_upper.json');write(upper_model_path,upper_model)
    write(upper_model_path.with_name('lower_source_base_upper_alt.json'),{'parent':'bloodborne_dw:tree_proposal/lower_source_base_upper'})
    proposal=copy.deepcopy(original)
    proposal['parts']=[{'model':'bloodborne_dw:tree_proposal/lower_source_base','altModel':'bloodborne_dw:tree_proposal/lower_source_base_alt',
        'offset':[0,16,0],'yaw':0,'pitch':0,'pivot':[8,8,8]},
        {'model':'bloodborne_dw:tree_proposal/lower_source_base_upper','altModel':'bloodborne_dw:tree_proposal/lower_source_base_upper_alt',
        'offset':[0,64,0],'yaw':0,'pitch':0,'pivot':[8,8,8]}]+upper
    selection=tree_selection()
    # Preserve the original count in the report when this deterministic generator is rerun.
    previous_report=ROOT/'reports/TREE_LOWER_PROPOSAL.json'
    old_selection_count=len(original['selection'])
    if previous_report.is_file() and old_selection_count in (2,4):
        previous=json.loads(previous_report.read_text(encoding='utf8'))
        assert previous['variant0_art_fingerprint']==fingerprint(parts)
        old_selection_count=previous['selection_volumes_before']
    original['selection']=copy.deepcopy(selection);proposal['selection']=copy.deepcopy(selection)
    assert original['collision']==[{'from':[6,0,6],'to':[10,96,10]}]
    assert proposal['collision']==original['collision']
    descriptor['variants']=[descriptor['variants'][0],{'reviewStatus':'PROPOSED_LOWER_UV_ONLY_NOT_ACCEPTED','poses':{'closed':proposal}}]
    descriptor['sourceEvidence']['lowerProposal']={'variant':1,'status':'PROPOSED_LOWER_UV_ONLY_NOT_ACCEPTED','source':'minecraft:block/white_wool first2elements',
        'verticalStretch':2,'sourceAuthoredHeightBlocks':3,'existingLowerHeightBlocks':6,'upperPartsUnchanged':16,
        'newPixels':False,'variant0OriginalArtUnchanged':True,'legalPanels':2,'panelHeightUnits':48,
        'panelOffsetsY':[16,64],'atlasSplitPixelY':232,'nominalUvContinuousAtYUnits':48}
    write(path,descriptor)
    assert original['parts']==parts and proposal['parts'][2:]==upper and texture_path.read_bytes()==texture
    geometry_diffs=[]
    for index,(before,after)in enumerate(zip(authored['elements'][:2],model['elements'])):
        assert before['rotation']==after['rotation']
        assert [before['from'][i]for i in [0,2]]==[after['from'][i]for i in [0,2]] and [before['to'][i]for i in [0,2]]==[after['to'][i]for i in [0,2]]
        geometry_diffs.append({'element':index,'localGeometryExactSource':before['from']==after['from'] and before['to']==after['to'],
            'xz_equal':True,'intrinsic_rotation_equal':True,'bottom_panel_offset_y':16,'upper_panel_offset_y':64,
            'atlas_v_before_pixels':[208,256],'bottom_panel_v_pixels':[232,256],'upper_panel_v_pixels':[208,232],
            'uDirectionAndFaceFieldsPreserved':True,'nominal_uv_continuity':True})
    report={'schema':'dreamwalker-tree-lower-proposal-v1','status':'PROPOSED_LOWER_UV_ONLY_NOT_ACCEPTED','registry':'bloodborne_dw:prototype_tree',
        'temporary_type_id':'90005','variant0_art_fingerprint':fingerprint(parts),'variant1_upper_art_fingerprint':fingerprint(upper),
        'variant0_original_art_unchanged':True,'upper16_parts_models_positions_uv_unchanged':True,'source_model':'minecraft:block/white_wool',
        'source_model_sha256':sha(raw),'source_atlas_sha256':sha(texture),'atlas_byte_exact':True,'new_pixels':False,
        'source_elements_used':[0,1],'other13_source_elements_omitted':True,'geometry_diff':geometry_diffs,
        'source_authored_height_blocks':3,'existing_lower_height_blocks':6,'proposed_vertical_stretch':2,
        'vanilla_model_endpoints_legal':True,'lower_panels':2,'render_parts_variant1':18,
        'previous_invalid_model':'reports/input-history/tree-lower-invalid-model-v8/lower_source_base.json',
        'previous_invalid_client_failure':'reports/input-history/tree-lower-invalid-model-v8/FIRST_SET_CLIENT_V8.json',
        'reason':'Source map used two repeated melon small-trunk patches; source white_wool supplies authored main-tree base UV208..256. Extend its two planes only within original lower0..6 bounds, keeping accepted upper junctionY6 fixed.',
        'physical_volumes_before':1,'physical_volumes_after':1,'collision_unchanged':True,
        'selection_volumes_before':old_selection_count,'selection_volumes_after':4,'selection_is_separate_from_collision':True,
        'registered_id_unchanged':True,'variant1_acceptance':'PENDING_USER_REVIEW','source_map_not_modified':True,
        'regeneration_order':['python tools/prepare_tree_prototype.py','python tools/prepare_tree_lower_proposal.py'],
        'changed_assets':[model_path.relative_to(ROOT).as_posix(),
            (model_path.parent/'lower_source_base_alt.json').relative_to(ROOT).as_posix(),
            upper_model_path.relative_to(ROOT).as_posix(),upper_model_path.with_name('lower_source_base_upper_alt.json').relative_to(ROOT).as_posix(),path.relative_to(ROOT).as_posix()]}
    write(ROOT/'reports/TREE_LOWER_PROPOSAL.json',report)
    print(json.dumps({'status':report['status'],'upper_parts':16,'physical':1,'selection':4,'stretch':2}))
if __name__=='__main__':main()
