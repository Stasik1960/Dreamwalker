"""Freeze ONE independent wall prototype's original 1.18.2 resource closure.

No source archive is changed, no catalog IDs assigned, no world conversion.
Post/low faces and all eight ordered tall materials remain source-authored.
"""
from __future__ import annotations
import copy, hashlib, json, zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PACK = Path('C:/Users/vakir/Downloads/bbmc_v15_resource (1).zip')
VANILLA = ROOT/'inputs/extracted/vanilla-1.18.2/client.jar'
RES = ROOT/'src/architecture/resources'
NS = 'bloodborne_dw'

def sha(data): return hashlib.sha256(data).hexdigest()
def file_sha(path):
    h=hashlib.sha256()
    with path.open('rb') as stream:
        for data in iter(lambda:stream.read(1048576),b''):h.update(data)
    return h.hexdigest()
def dump(path,value):
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
def split(value):return value.split(':',1) if ':' in value else ('minecraft',value)

def main():
    before={str(path.resolve()):file_sha(path) for path in (PACK,VANILLA)}
    manifest=[]; models={}; images=set()
    with zipfile.ZipFile(PACK) as pack, zipfile.ZipFile(VANILLA) as vanilla:
        pn,vn=set(pack.namelist()),set(vanilla.namelist())
        def read(path):
            if path in pn:return pack.read(path),'source_pack'
            if path in vn:return vanilla.read(path),'source_vanilla_1.18.2'
            raise ValueError('Missing original wall dependency: '+path)
        def tex(value):
            if value.startswith('#'):return value
            ns,name=split(value); source=f'assets/{ns}/textures/{name}.png'
            target=f'assets/{NS}/textures/wall/source/{ns}/{name}.png'
            if source not in images:
                images.add(source)
                for suffix in ('','.mcmeta'):
                    if suffix and source+suffix not in pn|vn:continue
                    data,origin=read(source+suffix);path=RES/(target+suffix);path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(data)
                    manifest.append({'kind':'texture','source':source+suffix,'origin':origin,'target':target+suffix,'source_sha256':sha(data),'byte_identical':True})
            return f'{NS}:wall/source/{ns}/{name}'
        def model(value):
            ns,name=split(value);source=f'assets/{ns}/models/{name}.json'
            target=f'assets/{NS}/models/block/wall/source/{ns}/{name}.json'
            ident=f'{NS}:block/wall/source/{ns}/{name}'
            if source in models:return ident
            data,origin=read(source);original=json.loads(data);models[source]=original
            output=copy.deepcopy(original)
            if 'parent' in output:output['parent']=model(output['parent'])
            for key,value in output.get('textures',{}).items():output['textures'][key]=tex(value)
            for element in output.get('elements',[]):
                for face in element.get('faces',{}).values():
                    if 'texture' in face:face['texture']=tex(face['texture'])
            # Identifiers alone change; all author geometry/UV/display data stays exact.
            restored=copy.deepcopy(output)
            if 'parent' in original:restored['parent']=original['parent']
            if 'textures' in original:restored['textures']=original['textures']
            for i,element in enumerate(restored.get('elements',[])):
                for name,face in element.get('faces',{}).items():
                    if 'texture' in face:face['texture']=original['elements'][i]['faces'][name]['texture']
            assert restored==original,source+' changed beyond identifier rebasing'
            dump(RES/target,output)
            manifest.append({'kind':'model','source':source,'origin':origin,'target':target,'source_sha256':sha(data),'identifier_rebase_only':True})
            return ident
        source_models=['minecraft:block/polished_deepslate_wall_post','minecraft:block/polished_deepslate_wall_side']+[f'minecraft:block/polished_deepslate_wall_side_tall_{i}' for i in range(8)]
        ids=[model(name) for name in source_models]
        inventory=model('minecraft:block/wall_inventory')
        source_state=json.loads(pack.read('assets/minecraft/blockstates/polished_deepslate_wall.json'))
        tall_rules=[part for part in source_state['multipart'] if any(value=='tall' for value in part.get('when',{}).values())]
        weights=[a.get('weight',1) for a in tall_rules[0]['apply']]
        assert weights==[20,1,1,1,5,1,1,1]
        assert all([a.get('weight',1) for a in part['apply']]==weights for part in tall_rules)
        def elements(name):
            source=f'assets/{split(name)[0]}/models/{split(name)[1]}.json'
            value=models[source]
            return value['elements'] if 'elements' in value else elements(value['parent'])
        def boxes(name):
            result=[]
            for value in elements(name):
                assert not value.get('rotation'), 'Source wall rotation needs explicit shape derivation'
                box=value['from']+value['to'];assert all(0<=n<=16 for n in box)
                result.append(box)
            return result
        geometry={'schemaVersion':1,'units':16,'post':boxes(source_models[0]),'low':boxes(source_models[1]),'tall':boxes(source_models[2]),
                  'tallWeights':weights,'collisionPolicy':'Cached simple cardinal rectangles at native wall1.5 height; diagonal one coarse root-footprint AABB. Separate one-box authored-height outline; no neighbor reservation.',
                  'profiles':'BASE and ALT have identical geometry; ALT wrappers fall back to BASE unless explicitly replaced.'}
        assert all(boxes(name)==geometry['tall'] for name in source_models[2:])
        dump(RES/f'{NS}/prototype-wall.json',geometry)
        wrappers=[]
        for profile in ('base','alt'):
            for name,ident,source_model in zip(['post','low']+[f'tall_{i}' for i in range(8)],ids,source_models):
                parent=ident if profile=='base' else f'{NS}:block/wall/base/{name}'
                dump(RES/f'assets/{NS}/models/block/wall/{profile}/{name}.json',{'parent':parent})
                wrappers.append(f'{NS}:block/wall/{profile}/{name}')
                diagonal={'parent':f'{NS}:block/wall/base/{name}'}
                if profile=='base':
                    diagonal['elements']=copy.deepcopy(elements(source_model))
                    for element in diagonal['elements']:
                        element['rotation']={'axis':'y','angle':-45,'origin':[8,8,8]}
                        # Diagonal faces do not meet cardinal neighboring cell planes.
                        for face in element.get('faces',{}).values():face.pop('cullface',None)
                else:diagonal['parent']=f'{NS}:block/wall/base/{name}_diagonal'
                dump(RES/f'assets/{NS}/models/block/wall/{profile}/{name}_diagonal.json',diagonal)
                wrappers.append(f'{NS}:block/wall/{profile}/{name}_diagonal')
        # The client BlockStateResolver supplies bounded shared authored parts.
        # A 592-condition multipart ×32768 states exhausts a normal4G client.
        # This single fallback is also harmless if inspected without the plugin.
        dump(RES/f'assets/{NS}/blockstates/prototype_wall.json',{'variants':{'':{'model':f'{NS}:block/wall/shared'}}})
        dump(RES/f'assets/{NS}/models/block/wall/shared.json',{'parent':f'{NS}:block/wall/base/post'})
        overrides=[]
        for profile in ('base','alt'):
            for course in ('low','tall'):
                for material in range(8):
                    name=f'wall/{profile}/{course}_{material}'
                    texture=tex(f'minecraft:block/polished_deepslate_wall_{1 if course=="low" else material}')
                    parent=inventory if profile=='base' else f'{NS}:item/wall/base/{course}_{material}'
                    data={'parent':parent}
                    if profile=='base':data['textures']={'wall':texture}
                    dump(RES/f'assets/{NS}/models/item/{name}.json',data)
                    overrides.append({'predicate':{f'{NS}:wall_profile':float(profile=='alt'),f'{NS}:wall_course':float(course=='tall'),f'{NS}:wall_material':material/7},'model':f'{NS}:item/{name}'})
        dump(RES/f'assets/{NS}/models/item/prototype_wall.json',{'parent':f'{NS}:item/wall/base/low_0','overrides':overrides})
        dump(RES/f'assets/{NS}/models/item/wall_builder.json',{'parent':'minecraft:item/stone_hoe'})
        dump(RES/f'data/{NS}/loot_tables/blocks/prototype_wall.json',{'type':'minecraft:block','pools':[{'rolls':1,'conditions':[{'condition':'minecraft:survives_explosion'}], 'entries':[{'type':'minecraft:item','name':f'{NS}:prototype_wall'}]}]})
        tag_path=RES/'data/minecraft/tags/blocks/mineable/pickaxe.json'
        old_tag=json.loads(tag_path.read_text(encoding='utf-8')) if tag_path.exists() else {'replace':False,'values':[]}
        if f'{NS}:prototype_wall' not in old_tag['values']: old_tag['values'].append(f'{NS}:prototype_wall')
        dump(tag_path,old_tag)
        walls_tag=RES/'data/minecraft/tags/blocks/walls.json'
        old_walls=json.loads(walls_tag.read_text(encoding='utf-8-sig')) if walls_tag.exists() else {'replace':False,'values':[]}
        if f'{NS}:prototype_wall' not in old_walls['values']:old_walls['values'].append(f'{NS}:prototype_wall')
        dump(walls_tag,old_walls)
    after={str(path.resolve()):file_sha(path) for path in (PACK,VANILLA)}
    assert before==after
    for row in manifest:
        if row['kind']=='texture':assert file_sha(RES/row['target'])==row['source_sha256']
    report={'schema':'dreamwalker-wall-asset-import-v1','status':'PASS','registry_id':'bloodborne_dw:prototype_wall','final_numeric_id':None,
            'source_hashes_before':before,'source_hashes_after':after,'source_coordinate_proof':[154,58,-976],
            'source_blockstate':source_state,'tall_ordered_weights':weights,'resource_closure':manifest,'wrapper_models':wrappers,'runtime_geometry':geometry,
            'connection_policy':'Actual WallBlock native AUTO: independent NONE/LOW/TALL sides, UP, own/vanilla walls, suitable full faces/panes/fence gates and above coverage. Explicit45-degree recipe remains MANUAL. Additive vanilla walls tag.',
            'profile_policy':'ALT wrappers explicitly inherit BASE; no distinct ALT source artwork was supplied.',
            'client_model_policy':{'loader':'Fabric BlockStateResolver + DelegatingUnbakedModel visual keys; no vanilla multipart condition product',
                'blockstate_count':82944,'primitive_resource_ids':40,'primitive_baked_quarter_uv_variants':160,'maximum_visual_appearances':17152,
                'state_ignored_for_render':['waterlogged','connections','material when no TALL side'],
                'rotation':'Existing authored diagonal intrinsic−45 once; source quarter turns and LOW UV lock via native Baker.',
                'geometry':'Shared immutable native primitive quads; per-appearance selected≤5parts; no quad clones.'},
            'scope':'Only wall prototype resource closure. No global catalog generation, final numbering or world conversion.', 'game_validation':'NOT_RUN'}
    dump(ROOT/'reports/WALL_ASSET_IMPORT.json',report)
    print(json.dumps({'wall_asset_import':'PASS','models':sum(r['kind']=='model' for r in manifest),'textures':sum(r['kind']=='texture' for r in manifest),'weights':weights,'numeric_ids':0}))
if __name__=='__main__':main()
