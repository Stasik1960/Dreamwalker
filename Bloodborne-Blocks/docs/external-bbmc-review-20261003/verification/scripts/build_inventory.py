"""Build publication-ready factual inventories; never copy third-party payloads."""
import argparse
import collections
import csv
import hashlib
import json
import re
import shutil
import zipfile
from pathlib import Path

SCRATCH = None
parser = argparse.ArgumentParser()
parser.add_argument('--audit-root', type=Path, required=True)
parser.add_argument('--output', type=Path, required=True)
parser.add_argument('--inputs', type=Path, required=True)
args = parser.parse_args()
SCRATCH = args.audit_root
out = args.output
out.mkdir(parents=True, exist_ok=True)

def read(path):
    return json.loads(path.read_text(encoding='utf-8'))

def write(name, data):
    (out/name).write_text(json.dumps(data, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')

def table(headers, rows):
    def cell(value):
        return str(value).replace('|', '\\|').replace('\n', '<br>')
    return '\n'.join(['| '+' | '.join(headers)+' |', '| '+' | '.join(['---']*len(headers))+' |'] +
                     ['| '+' | '.join(cell(x) for x in row)+' |' for row in rows])

resources = read(SCRATCH/'resources/resource-analysis.json')
maps = read(SCRATCH/'maps-fast/map-analysis.json')
assert maps['valid'] and len(maps['archives']) == 6
packs = resources['audits'][:4]
comparisons = []
for a,b in zip(packs, packs[1:]):
    ah,bh=a['asset_hashes'],b['asset_hashes']
    changes=[]
    for path in sorted(ah.keys() | bh.keys()):
        kind='added' if path not in ah else 'removed' if path not in bh else 'changed' if ah[path]!=bh[path] else 'unchanged'
        if kind!='unchanged':
            changes.append({'path':path,'change':kind,'before_sha256':ah.get(path),'after_sha256':bh.get(path)})
    comparisons.append({'from':Path(a['file']).name,'to':Path(b['file']).name,'changes':changes})
assert len(comparisons[-1]['changes']) == 2
write('resource-deltas-full.json',comparisons)
with (out/'resource-deltas-full.csv').open('w',encoding='utf-8',newline='') as f:
    writer=csv.writer(f);writer.writerow(['from','to','change','path','before_sha256','after_sha256'])
    for c in comparisons:
        for d in c['changes']:
            writer.writerow([c['from'],c['to'],d['change'],d['path'],d['before_sha256'],d['after_sha256']])

decompiled=SCRATCH/'mod/src/com/potomy/bloodborne'
entities=[];items=[];sounds=[]
for filename,field,kind in [('core/init/EntityInit.java','ENTITIES','entity'),
                           ('core/init/EntityInit.java','EGGS','item'),
                           ('core/init/ItemInit.java','ITEMS','item'),
                           ('core/init/SoundInit.java','SOUNDS','sound')]:
    path=decompiled/filename
    if not path.exists():
        if kind=='sound':
            continue
        raise FileNotFoundError(path)
    for n,line in enumerate(path.read_text(encoding='utf-8').splitlines(),1):
        m=re.search((r'SoundInit\.build\("([^"]+)"' if kind=='sound' else r'\b'+field+r'\.register\("([^"]+)"'),line)
        if not m:continue
        row={'id':'bloodborne:'+m.group(1),'source':filename,'line':n}
        if kind=='entity':
            category=re.search(r'MobCategory\.(\w+)',line)
            cls=re.search(r'\(\s*(\w+)::new',line)
            row.update(category=category.group(1) if category else None,implementation=cls.group(1) if cls else None)
            row['map_counts']={a['file']:a['entity_ids'].get(row['id'],0) for a in maps['archives']}
            entities.append(row)
        elif kind=='item':
            cls=re.search(r'new (\w+)\(',line)
            row['implementation']=cls.group(1) if cls else None
            row['spawn_egg']=field=='EGGS';items.append(row)
        else:sounds.append(row)
assert len(entities)==98, len(entities)
assert len(items)==121, len(items)
assert len({e['id'] for e in entities})==98
assert len({e['id'] for e in items})==121
assert len(sounds)==51, len(sounds)
known={e['id'] for e in entities}
assert all(k in known for a in maps['archives'] for k in a['entity_ids'] if k.startswith('bloodborne:'))

jar=args.inputs/'Bloodborne_X_Minecraft_mod_6.0 (1).jar'
with zipfile.ZipFile(jar) as z:
    assets=sorted(n for n in z.namelist() if n.startswith('assets/') and not n.endswith('/'))
    animations=[]
    for p in assets:
        if '/animations/' in p and p.endswith('.json'):
            data=json.loads(z.read(p)); animations.append({'path':p,'animation_names':sorted(data.get('animations',{}))})
    geo=[p for p in assets if '/geo/' in p and p.endswith('.json')]
    textures=[p for p in assets if '/textures/' in p and p.endswith('.png')]
    audio=[p for p in assets if '/sounds/' in p and p.endswith('.ogg')]
assert len(geo)==101 and len(animations)==32 and len(textures)==95 and len(audio)==400
inventory={'coverage':{'registered_entities':98,'registered_items':121,'ordinary_items':103,'spawn_eggs':18,'geo_files':101,'animation_files':32,'textures':95,'ogg_files':400},
           'entities':entities,'items':items,'sounds':sounds,'geo_paths':geo,'animations':animations,'texture_paths':textures,'sound_paths':audio}
write('mod-inventory-full.json',inventory)

entitygroups=collections.defaultdict(list)
for row in entities:
    name=row['id'].split(':')[1]
    if row['category']=='MONSTER':group='Мобы и клетки, зарегистрированные MONSTER'
    elif name in ('bullet','damage_hitbox','target_dummy'):group='Технические игровые сущности'
    elif name.startswith(('door','gate','main_gate','small_gate','wood_gate','trapdoor','lever','elevator','stairs','ladder')):group='Проёмы, механизмы и вертикальный переход'
    elif name.startswith(('furniture','chair','long_table','chest','curtain','ropes','hook')):group='Мебель, интерьер и подвесной реквизит'
    elif any(x in name for x in ('lamp','lantern','chandelier')):group='Освещение и фонари'
    elif name.startswith(('tree','statue')):group='Растительность и статуи'
    elif name.startswith(('coffin','odeon','cross','smallcross','blood_puddle','horse_carcass')):group='Погребальный и атмосферный реквизит'
    elif name.startswith(('hearse','carriage','cabriolet','boat','babycart','wheelchair')):group='Транспорт и коляски'
    else:group='Другие декорации и служебные объекты'
    entitygroups[group].append(row)

parts=['# Полный технический перечень материалов BBMC\n',
       'Дата: 2026-10-03. Это перечень наблюдаемых ID/путей и результатов анализа, а не обещание исправной игровой механики. '
       'Главное мнение и приоритеты: [OPINION.md](OPINION.md). Полный список всех изменений assets: '
       '[CSV](resource-deltas-full.csv), [JSON](resource-deltas-full.json).\n',
       '## Зарегистрированные сущности — все 98\n',
       'Количество в таблицах — размещения во всём сохранённом мире; MONSTER включает три cage utility, '
       'а MISC включает технические объекты. Название класса или анимации само по себе не доказывает функциональность.\n']
v15=next(a for a in maps['archives'] if a['file']=='bbmc_v15_map.zip')
v16=next(a for a in maps['archives'] if a['file']=='bbmc_v16_map (1).zip')
for group,rows in entitygroups.items():
    parts+=['### '+group+'\n',table(['ID','Класс','v15','v16'],[
        ['`'+r['id']+'`',r['implementation'],v15['entity_ids'].get(r['id'],0),v16['entity_ids'].get(r['id'],0)] for r in rows])+'\n']
parts+=['## Зарегистрированные предметы — все 121\n',
        '18 spawn eggs и 103 остальных предмета. В первоначальной краткой сводке 103 были ошибочно названы числом с яйцами; '
        'исчерпывающий разбор двух DeferredRegister исправляет этот подсчёт. Разные ID раскрытого оружия — формы одного оружия, '
        'а не шесть независимых семейств.\n',
        table(['ID','Класс / назначение','Источник'],[['`'+r['id']+'`',r['implementation'],r['source']+':'+str(r['line'])] for r in items])+'\n',
        '## Все файлы анимаций — 32\n',
        table(['Путь','Названия анимаций'],[['`'+a['path']+'`',', '.join('`'+n+'`' for n in a['animation_names'])] for a in animations])+'\n',
        'Все 101 geo-путь, 95 texture-путей и 400 OGG-путей: [mod-inventory-full.json](mod-inventory-full.json). '
        'Payload этих авторских файлов не включён.\n',
        '## Все зарегистрированные sound events — 51\n',
        table(['ID','Источник'],[['`'+r['id']+'`',r['source']+':'+str(r['line'])] for r in sounds])+'\n',
        '## Все новые и изменённые модели по версиям ресурспака\n',
        'Имена групп технические: нельзя надёжно назвать каждую модель без полного визуального bake. '
        'Старые неизменные paths и SHA-256 тоже сохранены в [resource-analysis.json](resource-analysis.json).\n']
for c in comparisons:
    parts+=['### '+c['from']+' → '+c['to']+'\n']
    models=[d for d in c['changes'] if '/models/' in d['path'] and d['path'].endswith('.json')]
    grouped=collections.defaultdict(list)
    for d in models:
        rel=d['path'].split('/models/',1)[1]
        grouped[str(Path(rel).parent).replace('\\','/')].append(d)
    parts.append('Новых/изменённых/удалённых model paths: '+str(len(models))+'.\n')
    for group,rows in sorted(grouped.items()):
        parts+=['#### '+group+' ('+str(len(rows))+')\n',
                '\n'.join('- `'+r['path']+'` — '+r['change'] for r in rows)+'\n']
parts+=['## Все реально размещённые сущности по картам\n']
for a in maps['archives']:
    custom=sum(v for k,v in a['entity_ids'].items() if k.startswith('bloodborne:'))
    parts+=['### '+a['file']+'\n',
            'Minecraft '+str(a['level']['minecraft_version'])+'; '+str(a['containers']['region']['decoded_chunks'])+
            ' terrain-чанков; '+str(custom)+' Bloodborne-размещений; '+str(sum(a['entity_ids'].values()))+' всех сущностей.\n',
            table(['ID','Количество'],[['`'+k+'`',v] for k,v in sorted(a['entity_ids'].items())])+'\n' if a['entity_ids'] else 'Сохранённых сущностей нет.\n']
parts+=['Полные block ID, state counts и block entity counts для всех шести миров: [map-analysis.json](map-analysis.json). '
        'Они включают землю и технические carriers; это не перечень семантически разных декоративных объектов.\n']
(out/'FULL-INVENTORY.md').write_text('\n'.join(parts),encoding='utf-8')
print(json.dumps({'entities':len(entities),'items':len(items),'animation_files':len(animations),
                  'resource_delta_entries':sum(len(c['changes']) for c in comparisons),'v15_entities':sum(v15['entity_ids'].values()),
                  'v16_entities':sum(v16['entity_ids'].values()),'entity_groups':{k:len(v) for k,v in entitygroups.items()}},ensure_ascii=False))
