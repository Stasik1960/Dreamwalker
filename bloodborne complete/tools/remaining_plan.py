"""Classify every source model and propose only source-backed offered artworks."""
from __future__ import annotations
import copy, csv, hashlib, json, re, traceback
from pathlib import Path
from collections import Counter, defaultdict

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'docs/remaining-transfer'
EXISTING = {'aca_door_1':('90001','prototype_double_door'),'aca_door_2':('90001','prototype_double_door'),
            'stairs/jungle_stairs_roof':('90003','prototype_roof'),'wood_window':('90007','prototype_wood_window'),
            'hold/window_01':('90004','prototype_thin_window'),'hold/window_02':('90010','prototype_glass_window_02'),
            'hold/window_03':('90020','prototype_glass_window_03'),'hold/wood_ladder_02':('90006','prototype_ladder'),
            'polished_deepslate_wall_post':('90002','prototype_wall'),'polished_deepslate_wall_side':('90002','prototype_wall'),
            'polished_deepslate_wall_side_tall_0':('90011','prototype_wall_skin_0'),'polished_deepslate_wall_side_tall_1':('90012','prototype_wall_skin_1'),
            'polished_deepslate_wall_side_tall_6':('90002','prototype_wall')}
for value in ('oak_log','spruce_log','birch_log','birch_log_1','jungle_log','acacia_log','dark_oak_log','white_wool','orange_wool','magenta_wool','light_blue_wool','cyan_wool'):
    EXISTING[value] = ('90005','prototype_tree')
RETIRED = {'hold/wood_ladder_03':'90018','hold/wood_ladder_04':'90019',**{f'polished_deepslate_wall_side_tall_{n}':str(90011+n) for n in (2,3,4,5,7)}}
ASSEMBLY_PARTS = {'bir_door_1','bir_door_2','dark_door_1','dark_door_2',
                  'window_bottom_right','window_top_right','window_bottom_left','window_top_left','addon/gate_bottom','addon/gate_top'}
for base in ('iron_door','warped_door','darkoak_door'):
    ASSEMBLY_PARTS.update('addon/'+base+'_'+part for part in ('bottom','top','open_bottom','open_top'))
ASSEMBLY_PARTS.update(('addon/oak_door_1','addon/oak_door_2'))

LEXICON = [('bench','Скамья'),('books','Книги'),('bag','Мешок'),('barrel','Бочка'),('wooden_box','Деревянный ящик'),('box','Ящик'),
           ('tombstone','Надгробие'),('statue','Статуя'),('gargoyle','Горгулья'),('lantern','Фонарь'),('lamp','Светильник'),
           ('spire','Готический шпиль'),('chimney','Дымовая труба'),('pilaster','Пилястра'),('floor_panels','Напольная панель'),
           ('floor_dec','Напольный орнамент'),('wall_deco','Настенный орнамент'),('wall_dec','Настенный орнамент'),
           ('wimperge','Готический вимперг'),('ladder','Лестница'),('roof','Кровельный модуль'),('chain','Цепь'),('candle','Свечи'),('candel','Свечи'),
           ('shattered_glass','Осколки стекла'),('grass','Пучок травы'),('bloodviles','Пузырьки крови'),('hat','Шляпа охотника'),
           ('hunter','Фигура охотника'),('bricks','Кирпичная кладка'),('brick','Кирпичная деталь'),('window','Оконная секция'),('gate','Секция ворот'),('door','Дверная секция'),
           ('slab','Архитектурная плита'),('stairs','Ступенчатый модуль'),('log_vertical','Вертикальное деревянное украшение'),('wood_obj','Деревянное украшение')]

MATERIALS={'dirt':'Земля','dirt_path':'Дорожная земля','farmland':'Грядка','farmland_moist':'Влажная грядка','gravel':'Гравий','clay':'Глина','ice':'Лёд','packed_ice':'Плотный лёд','blue_ice':'Синий лёд','honey_block':'Медовый блок'}
COLORS={'black':'чёрный','blue':'синий','brown':'коричневый','cyan':'бирюзовый','gray':'серый','green':'зелёный','light_blue':'голубой','light_gray':'светло-серый','lime':'лаймовый','magenta':'пурпурный','orange':'оранжевый','pink':'розовый','purple':'фиолетовый','red':'красный','white':'белый','yellow':'жёлтый'}
PANE_COLORS={'black':'чёрная','blue':'синяя','brown':'коричневая','cyan':'бирюзовая','gray':'серая','green':'зелёная','light_blue':'голубая','light_gray':'светло-серая','lime':'лаймовая','magenta':'пурпурная','orange':'оранжевая','pink':'розовая','purple':'фиолетовая','red':'красная','white':'белая','yellow':'жёлтая','glass_pane':'прозрачная'}

def art(model):
    value = model['resolved']; elements = copy.deepcopy(value['elements'])
    for element in elements:
        element.pop('name',None)
        for face in element.get('faces',{}).values():
            tex = face.get('texture','')
            face['texture'] = value['textures'].get(tex[1:], tex) if tex.startswith('#') else tex
    return {'elements':elements,'ambientocclusion':value.get('ambientocclusion',True)}

def path_name(model):
    return 'source_' + re.sub('[^a-z0-9_]', '_', model.removeprefix('minecraft:block/'))

def label(model, semantic):
    short = model['model'].removeprefix('minecraft:block/')
    for word, translation in LEXICON:
        if word in short:
            result = translation
            break
    else:
        carriers = [entry.split('[',1)[0].removeprefix('minecraft:') for entry in model['used_by']]
        result = next((semantic[name]['ru'] for name in carriers if name in semantic), '')
        if not result and short in MATERIALS:
            result=MATERIALS[short]+' · текстура BB'
        if not result and short.endswith('_concrete_powder'):
            result='Декоративная каменная поверхность · '+COLORS.get(short.removesuffix('_concrete_powder'),'цветная')
        if not result:
            elements=model['resolved']['elements']
            minimum=[min(el['from'][axis] for el in elements) for axis in range(3)]
            maximum=[max(el['to'][axis] for el in elements) for axis in range(3)]
            size=[maximum[axis]-minimum[axis] for axis in range(3)]
            if max(size)==0:result='Декоративная плоскость'
            elif size[1]>2*max(size[0],size[2]):result='Высокая декоративная стойка'
            elif min(size[0],size[2])<=max(size[0],size[2],size[1])*.15:result='Декоративная панель'
            elif size[1]<=max(size[0],size[2])*.2:result='Декоративная напольная деталь'
            elif len(elements)>8:result='Составное декоративное украшение'
            else:result='Объёмная декоративная деталь'
    pose=[]
    for word, translation in [('inner','внутренний угол'),('outer','внешний угол'),('bottom','нижняя'),('top','верхняя'),('left','левая'),('right','правая'),('post','столб'),('side','секция'),('mossy','с мхом'),('open','открытая')]:
        if word in short:
            pose.append(translation)
    suffix = re.search(r'(?:_|/)(\d+)$', short)
    if suffix:
        pose.append('вариант '+str(int(suffix[1])+1))
    if pose:
        result += ' · ' + ', '.join(pose)
    return result

def main():
    inventory=json.loads((OUT/'source-inventory.json').read_text(encoding='utf-8'))
    semantic=json.loads((ROOT.parent/'Bloodborne-Blocks/docs/semantic-catalog-v2.json').read_text(encoding='utf-8'))
    objects=[]; classifications=[]; artwork={}; models={row['model']:row for row in inventory['models']}
    multipart_rows = defaultdict(list)
    for state in inventory['states']:
        if state['state'].startswith('multipart:'):
            multipart_rows[state['source']].append(state)
    multipart_helpers={model['model'] if ':' in model['model'] else 'minecraft:'+model['model'] for rows in multipart_rows.values() for state in rows for model in state['models']}
    for row in inventory['models']:
        key=row['model'];short=key.removeprefix('minecraft:block/'); resolved=row['resolved']
        info={'sourceModel':key,'sourceStates':row['used_by'],'registryId':'','numericId':'','missing':'','verification':'NOT_RUN'}
        if not key.startswith('minecraft:block/'):
            info.update(kind='RESOURCE_OR_ITEM',status='OUTSIDE_BUILDING_BLOCK_OBJECTS',missing='Item/CIT or dependency, not a placeable block object')
        elif short in EXISTING:
            number,path=EXISTING[short];info.update(kind='EXISTING_OBJECT_OR_PART',status='PRESERVED_V12',registryId='bloodborne_dw:'+path,numericId=number,missing='Fresh gameplay verification not claimed; source comparison does not alter existing object')
        elif short in RETIRED:
            info.update(kind='INTENTIONAL_EXCLUSION',status='DO_NOT_RESTORE',numericId=RETIRED[short],missing='Explicit V11/V12 retirement remains authoritative')
        elif re.search(r'(?:^|/)(?:template_|dummy/e_slab)',short) or short.endswith('_inventory'):
            info.update(kind='TEMPLATE_OR_INVENTORY_HELPER',status='DEPENDENCY_ONLY',missing='No independent object; consumed by descendants or item icon')
        elif short in ASSEMBLY_PARTS:
            info.update(kind='ASSEMBLY_PART',status='AWAITING_SOURCE_ASSEMBLY_EVIDENCE',missing='Must group whole authored split object before offering')
        elif key in multipart_helpers and all('[multipart:' in state for state in row['used_by']):
            info.update(kind='MULTIPART_SERVICE_PART',status='DEPENDENCY_OF_WHOLE_OBJECT',missing='Covered by complete fixed source condition unions; no separate partial item')
            if resolved.get('issues'):
                info.update(status='BLOCKED_SOURCE_PART',missing=json.dumps(resolved['issues'],ensure_ascii=False,separators=(',',':')))
        elif resolved.get('issues') and short not in ('jungle_trapdoor_bottom','jungle_trapdoor_open'):
            info.update(kind='SOURCE_DEFECT',status='BLOCKED_SOURCE',missing=json.dumps(resolved['issues'],ensure_ascii=False,separators=(',',':')))
        elif not resolved.get('elements') or not any(el.get('faces') for el in resolved['elements']):
            info.update(kind='EMPTY_OR_PARENT_HELPER',status='DEPENDENCY_ONLY',missing='No finished visible geometry')
        elif not resolved.get('affected'):
            info.update(kind='UNCHANGED_VANILLA',status='KEEP_VANILLA',missing='No authored pack visual override')
        else:
            visual=art(row); fingerprint=hashlib.sha256(json.dumps(visual,sort_keys=True,separators=(',',':')).encode()).hexdigest()
            if fingerprint in artwork:
                original=artwork[fingerprint];info.update(kind='EXACT_DUPLICATE',status='ALIAS_TO_NEW_OBJECT',registryId='bloodborne_dw:'+original['path'],missing='Exact resolved elements/UV/textures/AO alias')
                original['variants'][0]['sourceAliases'].append(key)
            else:
                path=path_name(key);name=label(row,semantic)
                obj={'path':path,'displayName':name,'variants':[{'variant':0,'name':name,'sourceModels':[key], 'sourceAliases':[], 'closed':[{'model':key}], 'sourceStates':row['used_by']}], 'sourceClassification':'STANDALONE_REFERENCED_ART' if row['used_by'] else 'FINISHED_UNUSED_ART','artFingerprint':fingerprint}
                if resolved.get('issues'):
                    obj['sourceIssues']=resolved['issues']
                objects.append(obj);artwork[fingerprint]=obj
                info.update(kind=obj['sourceClassification'],status='PLANNED_NEW_OBJECT',registryId='bloodborne_dw:'+path,missing='Registration, item/icon, dependency import, placement and gameplay verification')
                if resolved.get('issues'):
                    info.update(status='PLANNED_NEW_WITH_SOURCE_PARTICLE_DEFECT',missing='Visible source geometry complete; original unused particle texture block/texture absent. No replacement is invented.')
        classifications.append(info)
    def whole(path,name,closed,opened=None,evidence=None,variants=None):
        if variants is None:
            variants=[{'variant':0,'name':name,'sourceModels':[app['model'] for app in closed],'closed':closed}]
            if opened:
                variants[0]['open']=opened
        refs=set(app['model'] for variant in variants for app in variant['closed']+variant.get('open',[]))
        blocked=[]
        for key in refs:
            if key not in models:
                blocked.append({'type':'MISSING_MODEL','model':key})
            else:
                blocked += models[key]['resolved'].get('issues',[])
        if blocked:
            for info in classifications:
                if info['sourceModel'] in refs and info['kind'] in ('ASSEMBLY_PART','MULTIPART_SERVICE_PART'):
                    info.update(status='BLOCKED_SOURCE_ASSEMBLY',missing=json.dumps(blocked,ensure_ascii=False,separators=(',',':')))
            return
        obj={'path':path,'displayName':name,'variants':variants,'sourceClassification':'WHOLE_SOURCE_ASSEMBLY','evidence':evidence or {}}
        objects.append(obj)
        for info in classifications:
            if info['sourceModel'] in refs and info['kind'] in ('ASSEMBLY_PART','MULTIPART_SERVICE_PART'):
                info.update(status='PLANNED_WHOLE_OBJECT',registryId='bloodborne_dw:'+path,missing='Whole-object registration, dependency import and gameplay checks')
                info.setdefault('wholeObjectPaths',[]).append(path)
    map_evidence=json.loads((OUT/'source-assembly-evidence.json').read_text(encoding='utf-8'))
    def pair(path,name,lower,upper,offset,source):
        matches=[row for row in map_evidence['vertical_pairs'] if row['lowerState'].startswith('minecraft:'+source+'[') and row['upperState'].startswith('minecraft:'+source+'[') and row['offset']==[0,offset,0] and 'half=bottom' in row['lowerState'] and 'half=top' in row['upperState']]
        whole(path,name,[{'model':'minecraft:block/'+lower},{'model':'minecraft:block/'+upper,'offset':[0,offset,0]}],evidence={'kind':'SOURCE_MAP_MATCHED_HALF_PAIR','matches':matches})
    pair('source_birch_grille_door','Решётчатая дверь с каменным завершением','bir_door_1','bir_door_2',48,'birch_stairs')
    pair('source_dark_arch_door','Арочная дверь в кирпичной раме','dark_door_1','dark_door_2',32,'dark_oak_stairs')
    pair('source_gothic_window_right','Готическое окно · правая секция','window_bottom_right','window_top_right',32,'warped_stairs')
    whole('source_gothic_window_left','Готическое окно · левая секция',[{'model':'minecraft:block/window_bottom_left'},{'model':'minecraft:block/window_top_left','offset':[0,32,0]}],evidence={'kind':'UNUSED_FINISHED_AUTHORED_COUNTERPART','join':'Source lower Y0..16 and upper Y-16..22 join at source right-window proven +32 placement; preserved left authored geometry'})
    whole('source_large_grille_gate','Большие решётчатые ворота',[{'model':'minecraft:block/addon/gate_bottom'},{'model':'minecraft:block/addon/gate_top','offset':[0,48,0]}],evidence={'kind':'SOURCE_MAP_MATCHED_GATE_PAIR','matches':[row for row in map_evidence['vertical_pairs'] if row['lowerState'].startswith('minecraft:stripped_jungle_log[axis=x]') and row['upperState'].startswith('minecraft:stripped_acacia_log[axis=x]') and row['offset']==[0,48,0]]})
    # Vanilla door property names are carriers only. Authored meshes and map evidence
    # determine whether they are doors, sculptures, or independent trim.
    for short in ('addon/darkoak_door_bottom','addon/darkoak_door_top'):
        row=models.get('minecraft:block/'+short)
        if row:
            path=path_name(row['model']);name='Каменная скульптура · '+('нижняя форма' if short.endswith('bottom') else 'верхняя форма')
            whole(path,name,[{'model':row['model']}],evidence={'kind':'FINISHED_INDEPENDENT_SCULPTURE','note':'Source meshes are sculpture forms, not complementary door leaves; map lower forms repeat at +32 rather than lower/upper pairing'})
    whole('source_warped_gargoyle','Высокая каменная горгулья',[{'model':'minecraft:block/addon/warped_door_bottom'},{'model':'minecraft:block/addon/warped_door_top','offset':[0,48,0]}],[{'model':'minecraft:block/addon/warped_door_open_bottom'},{'model':'minecraft:block/addon/warped_door_open_top','offset':[0,48,0]}],evidence={'kind':'SOURCE_MAP_MATCHED_LOWER_UPPER_PAIR','matches':[row for row in map_evidence['vertical_pairs'] if row['lowerState'].startswith('minecraft:warped_door[') and row['upperState'].startswith('minecraft:warped_door[') and row['offset']==[0,48,0] and 'half=lower' in row['lowerState'] and 'half=upper' in row['upperState']]})
    whole('source_oak_prison_door','Решётчатая тюремная дверь',[{'model':'minecraft:block/addon/oak_door_1'},{'model':'minecraft:block/addon/oak_door_2','offset':[0,16,0]}],evidence={'kind':'SOURCE_MAP_MATCHED_LOWER_UPPER_PAIR','matches':[row for row in map_evidence['vertical_pairs'] if row['lowerState'].startswith('minecraft:oak_door[') and row['upperState'].startswith('minecraft:oak_door[') and row['offset']==[0,16,0] and 'half=lower' in row['lowerState'] and 'half=upper' in row['upperState']]})
    whole('source_iron_door','Железная дверь',[{'model':'minecraft:block/addon/iron_door_bottom'},{'model':'minecraft:block/addon/iron_door_top','offset':[0,16,0]}],[{'model':'minecraft:block/addon/iron_door_open_bottom'},{'model':'minecraft:block/addon/iron_door_open_top','offset':[0,16,0]}],evidence={'kind':'SOURCE_MISSING_MODEL_BLOCKED'})

    def matches(condition,props):
        if 'OR' in condition:
            return any(matches(value,props) for value in condition['OR'])
        if 'AND' in condition:
            return all(matches(value,props) for value in condition['AND'])
        return all(str(props.get(key,'')).lower() in str(value).lower().split('|') for key,value in condition.items())
    for carrier,rows in sorted(multipart_rows.items()):
        if carrier=='minecraft:polished_deepslate_wall':
            continue # Existing wall plus retired art is authoritative.
        conditions=[json.loads(row['state'].split(':',2)[2]) for row in rows]
        flattened=[]
        def flatten(value):
            for key,item in value.items():
                if key in ('OR','AND'):
                    for part in item:flatten(part)
                else:flattened.append((key,str(item)))
        for value in conditions:flatten(value)
        iswall=any('tall' in value or 'low' in value for key,value in flattened)
        formats=[]
        for height in (('low','tall') if iswall else ('true',)):
            for title,active in [('Столб',()),('Конец',('north',)),('Прямая',('north','south')),('Угол',('north','east')),('Тройник',('north','east','south')),('Крест',('north','east','south','west'))]:
                props={direction:(height if direction in active else ('none' if iswall else 'false')) for direction in ('north','east','south','west')}
                props.update({'up':'true','down':'true','snowy':'false','waterlogged':'false'})
                formats.append((title+(' · высокий' if height=='tall' else ''),props))
        if iswall:
            props={'north':'tall','south':'tall','east':'none','west':'none','up':'false','down':'true','waterlogged':'false'}
            formats.append(('Прямая без столба',props))
        else:
            formats.append(('Южный конец',{'north':'false','south':'true','east':'false','west':'false','up':'true','down':'true','snowy':'false','waterlogged':'false'}))
        alternatives={app['model'] for row in rows if len(row['models'])>1 for app in row['models']}
        profiles=[None]+sorted(alternatives)
        for profile in profiles:
            variants=[];signatures=set()
            for name,props in formats:
                apps=[]
                for row,condition in zip(rows,conditions):
                    if not matches(condition,props) or not row['models']:continue
                    selected=next((app for app in row['models'] if app['model']==profile),row['models'][0])
                    apps.append({key:value for key,value in selected.items() if key!='weight'})
                if not apps:continue
                if any(app['model'] not in models or models[app['model']]['resolved'].get('issues') for app in apps):continue
                if not any(any(element.get('faces') for element in models[app['model']]['resolved'].get('elements',[])) for app in apps):continue
                signature=json.dumps(apps,sort_keys=True)
                if signature in signatures:continue
                signatures.add(signature);variants.append({'variant':len(variants),'name':name,'sourceModels':sorted({app['model'] for app in apps}),'closed':apps,'sourceProperties':props})
            if not variants:continue
            path='source_assembly_'+carrier.removeprefix('minecraft:')+('_'+re.sub('[^a-z0-9_]','_',profile.removeprefix('minecraft:block/')) if profile else '')
            display=semantic.get(carrier.removeprefix('minecraft:'),{}).get('ru','Составная архитектурная секция')
            if 'glass_pane' in carrier:display='Стеклянная панель · '+PANE_COLORS.get(carrier.removeprefix('minecraft:').replace('_stained_glass_pane',''),'цветная')
            if carrier=='minecraft:iron_bars':display='Художественная металлическая ограда'
            whole(path,display,[],variants=variants,evidence={'kind':'EXACT_MULTIPART_CONDITION_UNIONS','carrier':carrier,'profileModel':profile,'weightedPolicy':'Every authored weighted option retained as a deterministic offered profile; random Cartesian products are compositions, not independent authored artworks'})

    # Preserve every meaningful source pitch and UV-lock pose as an offered state.
    path_objects={obj['path']:obj for obj in objects}
    classification_by_model={info['sourceModel']:info for info in classifications}
    for state in inventory['states']:
        if state['state'].startswith('multipart:'):continue
        for source_app in state['models']:
            model=source_app['model'];model=model if ':' in model else 'minecraft:'+model
            info=classification_by_model.get(model)
            if not info or info['kind'] not in ('STANDALONE_REFERENCED_ART','EXACT_DUPLICATE'):continue
            obj=path_objects.get(info['registryId'].removeprefix('bloodborne_dw:'))
            if not obj:continue
            app={'model':obj['variants'][0]['sourceModels'][0]}
            if source_app.get('x',0):app['x']=source_app['x']
            if source_app.get('uvlock',False):
                app['uvlock']=True
                if source_app.get('y',0):app['y']=source_app['y']
            elif source_app.get('y',0)%90:app['y']=source_app['y']
            if len(app)==1:continue
            if any(variant['closed']==[app] for variant in obj['variants']):continue
            if len(obj['variants'])>=16:raise ValueError('Source pose variant limit exceeded '+obj['path'])
            name=obj['displayName']+' · '+('наклон '+str(app.get('x',0))+'°' if app.get('x',0) else 'закреплённые UV '+str(app.get('y',0))+'°')
            obj['variants'].append({'variant':len(obj['variants']),'name':name,'sourceModels':[model],'closed':[app],'sourceStates':[state['source']+'['+state['state']+']']})
    known=set(models)
    for state in inventory['states']:
        for application in state['models']:
            key=application['model'];key=key if ':' in key else 'minecraft:'+key
            if key not in known:
                classifications.append({'sourceModel':key,'sourceStates':[state['source']+'['+state['state']+']'],'registryId':'','numericId':'','kind':'SOURCE_DEFECT','status':'BLOCKED_SOURCE','missing':'Referenced model absent from v15 and vanilla 1.20.1','verification':'NOT_RUN'})
                known.add(key)
    summary={'objects':len(objects),'offeredStates':sum(len(obj['variants']) for obj in objects),'classificationCounts':dict(Counter(row['kind'] for row in classifications)),'statusCounts':dict(Counter(row['status'] for row in classifications))}
    result={'schemaVersion':1,'baselineCommit':inventory['summary']['baseline_commit'],'packSha256':inventory['summary']['pack_sha256'],'summary':summary,'objects':objects,'modelClassifications':classifications}
    (OUT/'source-candidates.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
    types_path=OUT/'new-types.json'
    types=json.loads(types_path.read_text(encoding='utf-8')).get('entries',[]) if types_path.exists() else []
    numbers={row['registryId']:row['temporaryId'] for row in types}
    offered=defaultdict(list)
    for obj in objects:
        for variant in obj['variants']:
            for application in variant['closed']+variant.get('open',[]):
                offered[application['model']].append({'registryId':'bloodborne_dw:'+obj['path'],'variant':variant['variant'],'application':application})
            if len(variant['closed'])==1:
                for source in variant.get('sourceModels',[]):
                    offered[source].append({'registryId':'bloodborne_dw:'+obj['path'],'variant':variant['variant'],'application':variant['closed'][0]})
            for alias in variant.get('sourceAliases',[]):
                offered[alias].append({'registryId':'bloodborne_dw:'+obj['path'],'variant':variant['variant'],'application':variant['closed'][0]})
    source_state_rows=[]
    for state in inventory['states']:
        for index,application in enumerate(state['models']):
            model=application['model'];model=model if ':' in model else 'minecraft:'+model
            info=classification_by_model.get(model,{})
            targets=[]
            for target in offered.get(model,[]):
                candidate=target['application']
                if state['state'].startswith('multipart:') or (candidate.get('x',0)==application.get('x',0) and
                   bool(candidate.get('uvlock',False))==bool(application.get('uvlock',False)) and
                   (not application.get('uvlock') or candidate.get('y',0)==application.get('y',0))):
                    targets.append({key:value for key,value in target.items() if key!='application'})
            legacy=info.get('registryId','') if info.get('status')=='PRESERVED_V12' else ''
            status='SOURCE_BRANCH_REPRESENTED' if targets else info.get('status','BLOCKED_SOURCE')
            if targets and state['state'].startswith('multipart:'):
                status='MULTIPART_PART_PRESENT_IN_WHOLE_CONDITIONS'
            source_state_rows.append({'sourceObject':state['source'],'sourceState':state['state'],'choice':index,
                                     'sourceModel':model,'x':application.get('x',0),'y':application.get('y',0),'uvlock':bool(application.get('uvlock',False)),
                                     'weight':application.get('weight',1),'target':legacy or ';'.join(sorted({target['registryId']+'[variant='+str(target['variant'])+']' for target in targets})),
                                     'numericId':info.get('numericId','') or ';'.join(sorted({numbers.get(target['registryId'],'UNASSIGNED') for target in targets})),
                                     'classification':info.get('kind','SOURCE_DEFECT'),'missing':info.get('missing','Referenced source model unavailable'),
                                     'status':status,'resourceCheck':'NOT_RUN','serverCheck':'NOT_RUN','clientCheck':'NOT_RUN'})
    (OUT/'state-correspondence.json').write_text(json.dumps(source_state_rows,ensure_ascii=False,indent=2),encoding='utf-8')
    with (OUT/'state-correspondence.csv').open('w',encoding='utf-8-sig',newline='') as stream:
        writer=csv.DictWriter(stream,fieldnames=list(source_state_rows[0]));writer.writeheader();writer.writerows(source_state_rows)
    with (OUT/'model-correspondence.csv').open('w',encoding='utf-8-sig',newline='') as stream:
        fields=['sourceModel','sourceStates','registryId','numericId','kind','missing','status','verification'];writer=csv.DictWriter(stream,fieldnames=fields);writer.writeheader()
        for info in classifications:
            writer.writerow({key:(';'.join(info[key]) if key=='sourceStates' else info.get(key,'')) for key in fields})
    (OUT/'plan-summary.json').write_text(json.dumps(summary,ensure_ascii=False,indent=2),encoding='utf-8')

if __name__=='__main__':
    try:
        main();(OUT/'plan-run-status.txt').write_text('PASS',encoding='utf-8')
    except Exception:
        (OUT/'plan-run-status.txt').write_text(traceback.format_exc(),encoding='utf-8');raise
