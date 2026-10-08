"""Freeze append-only TEMP type numbers; orientations/art/profile share one number."""
import json
import re
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'src/architecture/resources/bloodborne_dw/debug_catalogue.json'
ARCH = [('prototype_double_door','Входная дверь'),('prototype_wall','Художественная ограда'),
        ('prototype_roof','Наклонный кровельный модуль'),('prototype_thin_window','Тонкое остекление'),
        ('prototype_tree','Плоское составное дерево'),('prototype_ladder','Вертикальная деревянная лестница'),
        ('prototype_wood_window','Деревянные панели окна')]
EXCLUDED = {'sawcleaver_false','sawcleaver_true','sawspear_false','sawspear_true','boomhammer_false','boomhammer_true','bullet','blood_puddle'}
def build():
    catalog = json.loads((ROOT/'src/rp/resources/assets/bloodborne_rp/catalog.json').read_text(encoding='utf8'))
    lang = json.loads((ROOT/'src/rp/resources/assets/bloodborne_rp/lang/en_us.json').read_text(encoding='utf8'))
    source = (ROOT/'src/rp/java/dev/dreamwalker/bloodbornerp/mob/MobRegistry.java').read_text(encoding='utf8')
    mobs = set(re.findall(r'"([a-z0-9_]+)"',source.split('IDS = List.of(',1)[1].split(');',1)[0]))
    entries=[]
    def add(number,kind,name,registry,aliases):
        entries.append({'temporaryId':f'{number:05d}','finalId':None,'kind':kind,'name':name,'registryId':registry,'aliases':aliases})
    for i,(path,name) in enumerate(ARCH,90001):
        add(i,'architecture',name,'bloodborne_dw:'+path,['bloodborne_dw:'+path])
    for i,(path,name) in enumerate([('builder_tool','Строительный инструмент'),('composite_builder','Инструмент цельных объектов')],90008):
        add(i,'technical_tool',name,'bloodborne_dw:'+path,['bloodborne_dw:'+path])
    rp=sorted(set(catalog)-EXCLUDED)
    assert len(rp)==94 and len(mobs)==18
    for i,path in enumerate(rp,91001):
        item=path+('_spawn_egg' if path in mobs else '_placer')
        add(i,'rp_mob' if path in mobs else 'rp_object',lang.get('entity.bloodborne_rp.'+path,path),
            'bloodborne_rp:'+path,['bloodborne_rp:'+path,'bloodborne_rp:'+item])
    for i,path in enumerate(['blood_vial','boom_hammer','saw_cleaver','saw_spear'],92001):
        add(i,'rp_item',lang.get('item.bloodborne_rp.'+path,path),'bloodborne_rp:'+path,['bloodborne_rp:'+path])
    document={'schemaVersion':1,'status':'IMMUTABLE_TEMPORARY_TYPE_NUMBERS_FINAL_UNASSIGNED',
        'policy':'Append new assignments explicitly; never renumber/reuse silently. finalId remains null until semantic/frequency acceptance.',
        'helperPolicy':'No independent number: resolve main-owner registry/UUID; source lamp light belongs to hunterlamp.',
        'entries':entries}
    if OUT.exists():
        old=json.loads(OUT.read_text(encoding='utf8'))
        before={row['registryId']:row for row in old['entries']}
        after={row['registryId']:row for row in entries}
        assert before==after, 'Existing frozen table differs; review explicit append/change without renumbering.'
    OUT.parent.mkdir(parents=True,exist_ok=True)
    OUT.write_text(json.dumps(document,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
    print(json.dumps({'entries':len(entries),'architecture':7,'rp_entity_types':94,'rp_item_families':4,'technical_tools':2,'final_ids_assigned':0}))
if __name__=='__main__':build()
