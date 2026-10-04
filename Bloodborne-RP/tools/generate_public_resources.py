"""Generate original registry translations and vanilla-reference item models."""
import json
import pathlib

root=pathlib.Path(__file__).resolve().parents[1]/'src/main/resources/assets/bloodborne_rp'
catalog=json.loads((root/'catalog.json').read_text(encoding='utf-8'))
mobs='cleric_beast vicar_amelia blood_starved_beast scourge_beast executioner huntsman_a huntsman_b huntsman_c huntsman_d huntsman_wheelchair large_huntsman carrion_crow giant_rat small_rat rabid_dog maneater_boar brick_troll rotted_corpse'.split()
technical={'sawcleaver_false','sawcleaver_true','sawspear_false','sawspear_true','boomhammer_false','boomhammer_true','bullet','blood_puddle'}
lang={'itemGroup.bloodborne_rp.adventures':'Bloodborne RP','key.bloodborne_rp.transform_weapon':'Transform trick weapon','category.bloodborne_rp':'Bloodborne RP',
 'screen.bloodborne_rp.lamp.title':'Travel between lamps',
 'command.bloodborne_rp.player_required':'A player must run this command.',
 'command.bloodborne_rp.object_required':'Look at a nearby RP object.',
 'command.bloodborne_rp.done':'Object updated.',
 'command.bloodborne_rp.link_rejected':'Mechanism link rejected.',
 'command.bloodborne_rp.lamp_rejected':'Lamp operation rejected. Check the lamp ID and route.',
 'item.bloodborne_rp.blood_vial':'Blood Vial (optional healing)'}
models={}
for key,spec in catalog.items():
    lang['entity.bloodborne_rp.'+key]=spec['displayName']
    if key in mobs:
        lang['item.bloodborne_rp.'+key+'_spawn_egg']=spec['displayName']+' Spawn Egg'
        models[key+'_spawn_egg']={'parent':'minecraft:item/template_spawn_egg'}
    elif key not in technical:
        lang['item.bloodborne_rp.'+key+'_placer']=spec['displayName']+' (builder)'
        models[key+'_placer']={'parent':'minecraft:item/generated','textures':{'layer0':'minecraft:item/armor_stand'}}
for key,name in [('saw_cleaver','Saw Cleaver'),('saw_spear','Saw Spear'),('boom_hammer','Boom Hammer')]:
    lang['item.bloodborne_rp.'+key]=name
    # GeckoLib supplies geometry. These display transforms are independently chosen.
    models[key]={'parent':'builtin/entity','display':{
        'thirdperson_righthand':{'rotation':[0,90,0],'translation':[0,1,0],'scale':[1,1,1]},
        'thirdperson_lefthand':{'rotation':[0,-90,0],'translation':[0,1,0],'scale':[1,1,1]},
        'firstperson_righthand':{'rotation':[0,90,0],'translation':[1,1,0],'scale':[.8,.8,.8]},
        'gui':{'rotation':[30,135,0],'translation':[0,0,0],'scale':[.65,.65,.65]}}}
(root/'lang').mkdir(exist_ok=True)
(root/'lang/en_us.json').write_text(json.dumps(lang,indent=2)+'\n',encoding='utf-8')
for key,model in models.items():
    dest=root/'models/item'/f'{key}.json';dest.parent.mkdir(parents=True,exist_ok=True)
    dest.write_text(json.dumps(model,indent=2)+'\n',encoding='utf-8')
print(json.dumps({'translations':len(lang),'item_models':len(models),'copied_artwork':0}))
