"""Import ONLY three proven vertical ladder segment visuals for the prototype.

Keep source vertices, UV, rotations, faces and animation bytes. Rebase only
resource identifiers into bloodborne_dw. No final registry numbering, no city
generation and no reconstruction of missing geometry.
"""
from __future__ import annotations
import copy, hashlib, json, zipfile
from pathlib import Path
from record_inputs import sha256

ROOT=Path(__file__).resolve().parents[1]
PACK=Path('C:/Users/vakir/Downloads/bbmc_v15_resource (1).zip')
RES=ROOT/'src/architecture/resources'
NS='bloodborne_dw'
def dump(path,value):
    path.parent.mkdir(parents=True,exist_ok=True); path.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
def split(value):
    return value.split(':',1) if ':' in value else ('minecraft',value)
def main():
    historical=ROOT/'reports/PROTOTYPE_RESOURCES_CARDINAL_HISTORY.json'
    current=ROOT/'reports/PROTOTYPE_RESOURCES.json'
    if current.exists() and not historical.exists(): historical.write_bytes(current.read_bytes())
    imported=[]; textures=set(); models=set()
    with zipfile.ZipFile(PACK) as z:
        names=set(z.namelist())
        def texture(value):
            if value.startswith('#'): return value
            ns,path=split(value); source=f'assets/{ns}/textures/{path}.png'
            if source not in names: return value
            target=f'assets/{NS}/textures/base/source/{ns}/{path}.png'
            if source not in textures:
                textures.add(source)
                for suffix in ['', '.mcmeta']:
                    if source+suffix in names:
                        data=z.read(source+suffix); dest=RES/(target+suffix); dest.parent.mkdir(parents=True,exist_ok=True); dest.write_bytes(data)
                        imported.append({'kind':'texture','source':source+suffix,'target':target+suffix,'source_sha256':hashlib.sha256(data).hexdigest(),'byte_identical':True})
            return f'{NS}:base/source/{ns}/{path}'
        def model(value):
            ns,path=split(value); source=f'assets/{ns}/models/{path}.json'
            if source not in names: return value
            target=f'assets/{NS}/models/base/source/{ns}/{path}.json'
            if source in models: return f'{NS}:base/source/{ns}/{path}'
            models.add(source); data=z.read(source); original=json.loads(data); new=copy.deepcopy(original)
            if 'parent' in new: new['parent']=model(new['parent'])
            new['textures']={k:texture(v) for k,v in new.get('textures',{}).items()}
            # A sole source texture defines unambiguous particles; no geometry change.
            particle_added='particle' not in new['textures'] and len(new['textures'])==1
            if particle_added: new['textures']['particle']='#'+next(iter(new['textures']))
            for element in new.get('elements',[]):
                for face in element.get('faces',{}).values():
                    if 'texture' in face: face['texture']=texture(face['texture'])
            # Prove that identifier rebasing did not change visible source geometry.
            check=copy.deepcopy(new)
            for i,e in enumerate(check.get('elements',[])):
                for face_name,face in e.get('faces',{}).items():
                    face['texture']=original['elements'][i]['faces'][face_name]['texture']
            assert check.get('elements')==original.get('elements'),source+' geometry changed'
            dump(RES/target,new)
            imported.append({'kind':'model','source':source,'target':target,'source_sha256':hashlib.sha256(data).hexdigest(),
                'source_elements_uv_rotations_preserved':True,'particle_alias_added':particle_added})
            return f'{NS}:base/source/{ns}/{path}'
        source_models=[f'minecraft:block/hold/wood_ladder_{n:02}' for n in range(2,5)]
        base_models=[model(x) for x in source_models]
        variants={}
        for i,base in enumerate(base_models):
            alt=f'{NS}:alt/prototype_ladder/{i}'
            dump(RES/f'assets/{NS}/models/alt/prototype_ladder/{i}.json',{'parent':base})
            for facing,angle in [('north',0),('east',90),('south',180),('west',270)]:
                for profile,selected in [('base',base),('alt',alt)]:
                    for water in ['false','true']:
                        for diagonal in ['false','true']:
                            for source_clone in ['false','true']:
                                variants[f'facing={facing},variant={i},profile={profile},waterlogged={water},diagonal={diagonal},source_clone={source_clone}']={'model':selected,**({'y':angle} if angle else {})}
        dump(RES/f'assets/{NS}/blockstates/prototype_ladder.json',{'variants':variants})
        item_overrides=[]
        for profile in ['base','alt']:
            for i in range(3):
                if profile=='base' and i==0: continue
                name=f'item/prototype_ladder_{profile}_{i}'
                selected=base_models[i] if profile=='base' else f'{NS}:alt/prototype_ladder/{i}'
                dump(RES/f'assets/{NS}/models/{name}.json',{'parent':selected})
                item_overrides.append({'predicate':{f'{NS}:variant':i/2.0, f'{NS}:profile':0 if profile=='base' else 1},'model':f'{NS}:{name}'})
        dump(RES/f'assets/{NS}/models/item/prototype_ladder.json',{'parent':base_models[0],'overrides':item_overrides})
        lang_path=RES/f'assets/{NS}/lang/ru_ru.json'
        language=json.loads(lang_path.read_text(encoding='utf-8')) if lang_path.exists() else {}
        language.update({
            'block.bloodborne_dw.prototype_ladder':'Вертикальная деревянная лестница — прототип',
            'item.bloodborne_dw.builder_tool':'Инструмент поворота — прототип',
            'itemGroup.bloodborne_dw.prototypes':'Dreamwalker: прототипы',
            'itemGroup.bloodborne_dw.architecture':'Dreamwalker: прототипы',
            'message.bloodborne_dw.prototype':'Экспериментальный ID. Каталог ещё не закреплён.'})
        dump(lang_path,language)
        dump(RES/f'data/minecraft/tags/blocks/climbable.json',{'replace':False,'values':[f'{NS}:prototype_ladder']})
        dump(RES/f'data/{NS}/loot_tables/blocks/prototype_ladder.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':f'{NS}:prototype_ladder'}]}]})
        dump(RES/'bloodborne_dw/prototype-ladder.json',{
            'schemaVersion':1,'format':'dreamwalker-prototype-ladder-v1','units':16,'northCollision':[[0,0,13.19785,16,16,16]],
            'northOutline':[[0,0,13.19785,16,16,16]],'sourceModels':source_models,'placement_mask':[[0,0,0]],
            'diagonal_physics':{'rotation_degrees':45,'pivot':[8,8,8],'local_clip':[0,0,0,16,16,16],'representation':'one_cached_coarse_axis_aligned_box',
                'backing':'Two real touching vertical faces. Ordinary sections require one centered 2x4-pixel contact pad on either vertical half; top/bottom slabs and suitable stair faces work. Source installations retain original full-face backing checks.'},
            'render_mount':{'both_ordinary_and_source_clone':True,'post_cardinal_clockwise_yaw':180,'north_translation_model_units':[0,0,16],'optional45_applied_after_mount':True,'source_elements_uv_display_unchanged':True},
            'note':'One ordinary ladder cell with no helpers; authored rail protrusions do not reserve neighbors. Cardinal collision remains thin; diagonals deliberately use one coarse rectangle. Visual, collision, selection and support are distinct contracts.'})
        dump(RES/f'assets/{NS}/models/item/builder_tool.json',{'parent':'minecraft:item/handheld','textures':{'layer0':'minecraft:item/stick'}})
        dump(RES/f'assets/{NS}/blockstates/source_ladder_backing.json',{'variants':{'':{'model':f'{NS}:block/source_ladder_backing'}}})
        dump(RES/f'assets/{NS}/models/block/source_ladder_backing.json',{'textures':{'particle':f'{NS}:base/source/minecraft/block/addon/spirelamp_0095'},'elements':[]})
        example=ROOT/'build/prototype/ALT-example.zip'; example.parent.mkdir(parents=True,exist_ok=True)
        with zipfile.ZipFile(example,'w',zipfile.ZIP_DEFLATED) as out:
            out.writestr('pack.mcmeta',json.dumps({'pack':{'pack_format':15,'description':'Dreamwalker ladder ALT test: per-object texture and model replacement'}}))
            # Texture change is confined to ALT ladder wrapper #2. BASE unchanged.
            out.writestr(f'assets/{NS}/models/alt/prototype_ladder/0.json',json.dumps({'parent':base_models[0],'textures':{'2':'minecraft:block/red_wool'}}))
            texture_source='assets/minecraft/textures/block/addon/spirelamp_0095.png'
            out.writestr(f'assets/{NS}/textures/alt/prototype_ladder/test.png',z.read(texture_source))
            # Model replacement uses a supplied real alternate ladder geometry.
            out.writestr(f'assets/{NS}/models/alt/prototype_ladder/1.json',json.dumps({'parent':base_models[2]}))
        audit={'schema':'dreamwalker-prototype-resources-v1','status':'PROTOTYPE_NOT_ACCEPTED','source_pack_sha256':sha256(PACK),
            'registry_id':'bloodborne_dw:prototype_ladder','numeric_ids_frozen':False,'source_models':source_models,
            'yaw_states':8,'state_count':192,'ordinary_state_count':96,'source_clone_state_count':96,'source_clone':'Same ladder ID; separate owned fixed legacy full-cube backing role with UUID and opaque original typed NBT. Cardinal baked art receives proven 180 turn and canonical [0,0,1] block shift before optional diagonal turn. Ordinary items strip clone technical data.',
            'diagonal_render':'Complete already-baked cardinal model rotated 45 degrees once by DiagonalBakedModel; UVs and nested source rotations retained; cull faces cleared.',
            'transform':{'translation':[0,0,0],'pivot_before':[8,8,8],'pivot_after':[8,8,8],'world_position_compensation':[0,0,0]},
            'imports':imported,'alt_example_sha256':sha256(example),'checks':{'offline_geometry_preservation':'PASS','minecraft_runtime_render':'NOT_RUN'}}
        dump(ROOT/'reports/PROTOTYPE_RESOURCES.json',audit)
        print('Imported three vertical ladder variant models with exact geometry/UV; no source namespace overrides.')
if __name__=='__main__': main()
