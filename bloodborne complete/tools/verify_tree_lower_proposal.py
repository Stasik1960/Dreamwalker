"""Independent source/V7 verification of the optional, unaccepted flat-tree base."""
import copy
import hashlib
import json
from pathlib import Path
import zipfile

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / 'src/architecture/resources'
CHECKPOINT = ROOT / 'build/delivery/v7/dreamwalker-bb-fabric-1.20.1-v7-full-source.zip'
PREFIX = 'dreamwalker-bb-fabric-1.20.1-source-v7/'
PACK = Path('C:/Users/vakir/Downloads/bbmc_v15_resource (1).zip')
def sha(data): return hashlib.sha256(data).hexdigest()
def fp(data): return sha(json.dumps(data, sort_keys=True, separators=(',', ':')).encode('utf8'))
def main():
    delivered = json.loads((CHECKPOINT.parent / 'dreamwalker-bb-fabric-1.20.1-v7-delivery-manifest.json').read_text(encoding='utf8'))
    assert sha(CHECKPOINT.read_bytes()) == delivered['source_archive']['archive_sha256']
    descriptor_path = RES / 'bloodborne_dw/composite/prototype_tree.json'
    descriptor = json.loads(descriptor_path.read_text(encoding='utf8'))
    with zipfile.ZipFile(CHECKPOINT) as checkpoint, zipfile.ZipFile(PACK) as source:
        before = json.loads(checkpoint.read(PREFIX + 'src/architecture/resources/bloodborne_dw/composite/prototype_tree.json'))
        original = before['variants'][0]['poses']['closed']
        retained = descriptor['variants'][0]['poses']['closed']
        proposed = descriptor['variants'][1]['poses']['closed']
        assert len(before['variants']) == 1 and len(descriptor['variants']) == 2
        assert descriptor['id'] == 'bloodborne_dw:prototype_tree'
        assert descriptor['variants'][1]['reviewStatus'] == 'PROPOSED_LOWER_UV_ONLY_NOT_ACCEPTED'
        assert retained['parts'] == original['parts'] and len(original['parts']) == 18
        lower = [part for part in original['parts'] if part['model'].endswith('/melon')]
        upper = [part for part in original['parts'] if part not in lower]
        assert len(lower) == 2 and len(upper) == 16
        assert proposed['parts'][2:] == upper and len(proposed['parts']) == 18
        # Outside the declared variant/selection/proposal fields, the V7 descriptor is identical.
        remainder = copy.deepcopy(descriptor)
        remainder['variants'] = copy.deepcopy(before['variants'])
        del remainder['sourceEvidence']['lowerProposal']
        assert remainder == before
        for pose in [retained, proposed]:
            assert pose['collision'] == original['collision'] == [{'from': [6, 0, 6], 'to': [10, 96, 10]}]
            # Independent literal contract: lower planes follow the narrow source
            # stem, upper planes follow the accepted crown. No invisible lower wings.
            expected_selection = [{'from': [-16, 0, 7.875], 'to': [32, 96, 8.125]},
                {'from': [7.875, 0, -16], 'to': [8.125, 96, 32]},
                {'from': [-64, 96, 7.875], 'to': [80, 288, 8.125]},
                {'from': [7.875, 96, -64], 'to': [8.125, 288, 80]}]
            assert pose['selection'] == expected_selection
            assert {key: value for key, value in pose.items() if key not in ('parts', 'selection')} == {
                key: value for key, value in original.items() if key not in ('parts', 'selection')}
        for part, suffix, offset in zip(proposed['parts'][:2], ['', '_upper'], [16,64]):
            assert part == {'model': 'bloodborne_dw:tree_proposal/lower_source_base'+suffix,
                'altModel': 'bloodborne_dw:tree_proposal/lower_source_base'+suffix+'_alt', 'offset': [0, offset, 0],
                'yaw': 0, 'pitch': 0, 'pivot': [8, 8, 8]}
        model_path = RES / 'assets/bloodborne_dw/models/tree_proposal/lower_source_base.json'
        model = json.loads(model_path.read_text(encoding='utf8'))
        source_model_bytes = source.read('assets/minecraft/models/block/white_wool.json')
        authored = json.loads(source_model_bytes)
        upper_model_path=model_path.with_name('lower_source_base_upper.json')
        upper_model=json.loads(upper_model_path.read_text(encoding='utf8'))
        previous_path=ROOT/'reports/input-history/tree-lower-invalid-model-v8/lower_source_base.json'
        previous=json.loads(previous_path.read_text(encoding='utf8'))
        previous_manifest=json.loads((previous_path.parent/'manifest.json').read_text(encoding='utf8'))
        previous_row=next(row for row in previous_manifest['files'] if row['archived']==str(previous_path.relative_to(ROOT)).replace('/','\\') or Path(row['archived']).name==previous_path.name)
        assert sha(previous_path.read_bytes())==previous_row['sha256']
        assert len(authored['elements']) == 15 and len(model['elements']) == len(upper_model['elements']) == 2
        affine_samples=0
        for old, invalid, bottom, top in zip(authored['elements'][:2], previous['elements'], model['elements'], upper_model['elements']):
            expected_invalid=copy.deepcopy(old);expected_invalid['from'][1]=0;expected_invalid['to'][1]=96
            assert invalid==expected_invalid
            for panel,interval in [(bottom,[14.5,16]),(top,[13,14.5])]:
                expected=copy.deepcopy(old)
                for face in expected['faces'].values():
                    assert face.get('rotation',0)==0
                    face['uv'][1],face['uv'][3]=interval
                assert panel==expected, 'Local source geometry/rotation/XZ/face fields unchanged; only declared V interval may split'
                assert all(-16<=v<=32 for key in ['from','to'] for v in panel[key])
            # All source side faces use Vmin at top (native CubeFace/texture API probe).
            # Compare the old intended affine surface against both legal panels,
            # including every outer vertex and the new seam atY48,V14.5(pixel232).
            for name,face in invalid['faces'].items():
                for world_y in [0,12,24,36,48,60,72,84,96]:
                    old_v=face['uv'][3]+(face['uv'][1]-face['uv'][3])*world_y/96
                    candidates=[(bottom,0)] if world_y<48 else [(top,48)] if world_y>48 else [(bottom,0),(top,48)]
                    for panel,world_base in candidates:
                        uv=panel['faces'][name]['uv']
                        new_v=uv[3]+(uv[1]-uv[3])*(world_y-world_base)/48
                        for across in [0,0.25,0.5,0.75,1]:
                            old_u=face['uv'][0]+(face['uv'][2]-face['uv'][0])*across
                            new_u=uv[0]+(uv[2]-uv[0])*across
                            assert (old_u,old_v)==(new_u,new_v)
                            affine_samples+=1
        assert model['texture_size'] == authored['texture_size'] == [256, 256]
        assert model['textures'] == {'0': 'bloodborne_dw:tree_source/block/addon/tree_1', 'particle': '#0'}
        alt_path = model_path.with_name('lower_source_base_alt.json')
        assert json.loads(alt_path.read_text(encoding='utf8')) == {'parent': 'bloodborne_dw:tree_proposal/lower_source_base'}
        upper_alt_path=model_path.with_name('lower_source_base_upper_alt.json')
        assert json.loads(upper_alt_path.read_text(encoding='utf8')) == {'parent':'bloodborne_dw:tree_proposal/lower_source_base_upper'}
        assert upper_model['textures']==model['textures'] and upper_model['texture_size']==model['texture_size']
        source_texture = source.read('assets/minecraft/textures/block/addon/tree_1.png')
        assert (RES / 'assets/bloodborne_dw/textures/tree_source/block/addon/tree_1.png').read_bytes() == source_texture
        for old in original['parts']:
            for key in ['model', 'altModel']:
                namespace, local = old[key].split(':', 1)
                path = RES / 'assets' / namespace / 'models' / (local + '.json')
                assert checkpoint.read(PREFIX + path.relative_to(ROOT).as_posix()) == path.read_bytes()
        # Every RP resource, including accepted volumetric tree1 geometry/texture, stays byte-exact V7.
        rp_resources = list((ROOT / 'src/rp/resources').rglob('*'))
        rp_files = [path for path in rp_resources if path.is_file()]
        for path in rp_files:
            assert checkpoint.read(PREFIX + path.relative_to(ROOT).as_posix()) == path.read_bytes()
        rp_catalog = json.loads((ROOT / 'src/rp/resources/assets/bloodborne_rp/catalog.json').read_text(encoding='utf8'))
        rp_tree = rp_catalog['tree1']
        accepted = {key: sha((ROOT / 'src/rp/resources/assets/bloodborne_rp' / rp_tree[key]).read_bytes()) for key in ('model', 'texture')}
    report = {'schema': 'dreamwalker-tree-lower-independent-verification-v1',
        'status': 'PASS_BOUNDED_PROPOSAL_ONLY_USER_ACCEPTANCE_PENDING',
        'checkpoint_sha256': sha(CHECKPOINT.read_bytes()), 'source_pack_sha256': sha(PACK.read_bytes()),
        'descriptor_sha256': sha(descriptor_path.read_bytes()), 'model_sha256': sha(model_path.read_bytes()),
        'alt_model_sha256': sha(alt_path.read_bytes()), 'source_white_wool_sha256': sha(source_model_bytes),
        'upper_model_sha256':sha(upper_model_path.read_bytes()),'upper_alt_model_sha256':sha(upper_alt_path.read_bytes()),
        'historical_invalid_model_sha256':sha(previous_path.read_bytes()),'vanilla_json_endpoint_bounds':'PASS_ALL_FROM_TO_WITHIN_MINUS16_PLUS32',
        'legal_panel_decomposition':{'local_y':[-16,32],'part_offsets_y':[16,64],'world_y':[0,48,96],
            'nominal_atlas_y_bottom_to_top_pixels':[256,232,208],'affine_uv_samples_exact':affine_samples,
            'seam_y_modelunits':48,'seam_atlas_pixel_y':232,'accepted_upper_starts_y':96,
            'geometry_and_nominal_uv_interpolation_exact_prior_intent':True,
            'limits':'OldY96 JSON never baked successfully. This verifies intended geometry/nominalUV; actual sprite filtering and client rendering need ordinary client evidence.'},
        'source_tree_atlas_sha256': sha(source_texture), 'original_variant0_art_exact_v7': True,
        'original_variant0_art_fingerprint': fp(original['parts']), 'accepted_upper16_exact_v7': True,
        'accepted_upper16_fingerprint': fp(upper), 'rp_resources_exact_v7': len(rp_files),
        'accepted_volumetric_rp_tree_unchanged': accepted,
        'changes': 'Variant1 lower-only authored white_wool planes split into two legal48-unit panels with half-V ranges; same prior intended6m art and2xstretch, four selection prisms unchanged.',
        'selection_descriptor_volumes': 4, 'selection_contract': expected_selection,
        'physical_collision': 'Unchanged one0.25×0.25×6m trunk; selection does not enlarge physical collision or placement essential mask.',
        'proposal_status': 'PROPOSED_LOWER_UV_ONLY_NOT_ACCEPTED', 'manual_lower_art_acceptance': 'PENDING_USER_REVIEW',
        'new_pixels': False, 'source_map_modified': False, 'runtime_render_probe': 'PENDING_PARENT_BUILD_CLIENT'}
    output = ROOT / 'reports/TREE_LOWER_PROPOSAL_INDEPENDENT.json'
    output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf8')
    print(json.dumps({key: report[key] for key in ('status', 'original_variant0_art_exact_v7', 'accepted_upper16_exact_v7', 'rp_resources_exact_v7')}))
if __name__ == '__main__': main()
