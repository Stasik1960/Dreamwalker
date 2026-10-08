"""New isolated review input. V8 inputs, archives and evidence remain byte-for-byte historical."""
import argparse
import copy
import hashlib
import json
from pathlib import Path
from collections import Counter


def review_metadata(root,scene,gameplay):
    """Rebuild public V9 station descriptions from the actual requested roots."""
    rows=scene['objects'];by_root={tuple(row['root']):row for row in rows}
    catalogue=json.loads((root/'src/architecture/resources/bloodborne_dw/debug_catalogue.json').read_text(encoding='utf8'))
    ids={row['registryId'].removeprefix('bloodborne_dw:'):row['temporaryId'] for row in catalogue['entries'] if row.get('registryId','').startswith('bloodborne_dw:')}
    stations=[]
    def architecture(name,category,members,**details):
        if not members:return
        kinds=list(dict.fromkeys(row['kind'] for row in members))
        stations.append({'name':name,'category':category,'placementKind':'architecture','roots':[row['root'] for row in members],
                         'registryTypes':{kind:ids[kind] for kind in kinds},**details})
    def kind_rows(kind):return [row for row in rows if row['kind']==kind]
    architecture('Одна центральная створка: BASE, ALT и открытая','door',kind_rows('prototype_double_door'),
                 review='Обычный ПКМ открывает центральную створку; рамка остаётся неподвижной. Проверить с двух сторон.')
    architecture('Принятое деревянное окно','accepted_wood_window',kind_rows('prototype_wood_window'),preserveAccepted=True)
    for kind,title in [('prototype_thin_window','Стекло90004: окно01'),('prototype_glass_window_02','Стекло90010: окно02')]:
        members=kind_rows(kind)
        architecture(title,'glazing',members,mountModes=sorted({row['mount'] for row in members}),ordinaryDegrees=[0,90,180,270],
                     support='После установки не требует сохранять стену/пол/потолок. Рисунок выбирается отдельным предметом.',
                     placementModes=[{'root':row['root'],'mount':row['mount'],'mountFace':row['mountFace'],'sneaking':row.get('placementSneaking',False)} for row in members])
    architecture('Вертикальное стекло на полном полу и BOTTOM slab','glazing_partial_support',
                 [row for row in rows if row.get('placementSneaking')],contacts=['full floor','bottom slab topY64.5'],
                 placement='Shift+UP сохраняет вертикальную плоскость; высота берётся от фактической поверхности. Старый window03 здесь не выдаётся.')
    architecture('Кровля: две противоположные полосы по пять модулей','roof_join',kind_rows('prototype_roof'),
                 layout='5x2; визуальные yaw0/4; независимый физический куб1x1x1 на сетке каждого root.',visualAcceptance='PENDING_USER_REVIEW')
    architecture('Принятое плоское дерево: единственный обычный тип90005','composite_tree',kind_rows('prototype_tree'),
                 acceptedUpperPartsPreserved=True,acceptedLowerPartsPreserved=True,ordinaryVariants=[0],foreignChest=[37,64,38])
    # Keep only genuine unchanged AUTO topology groups, replacing artwork/type descriptions.
    for old in scene.get('reviewStations',[]):
        if old.get('category')!='wall_auto':continue
        members=[by_root[tuple(pos)] for pos in old['roots'] if tuple(pos) in by_root]
        details={key:copy.deepcopy(value) for key,value in old.items() if key not in {'name','category','roots'}}
        architecture(old['name'],'wall_auto',members,**details)
    for skin in (0,1,2,3,4,5,7):
        kind=f'prototype_wall_skin_{skin}'
        architecture(f'Ограда{ids[kind]}: отдельный рисунок{skin}','wall_art',kind_rows(kind),independentItem=True,
                     review='Соединения AUTO; рисунок выбирается своим предметом, а не VARIANT инструмента.')
    ladder_kinds={'prototype_ladder','prototype_ladder_art_1','prototype_ladder_art_2'}
    for yaw in range(8):
        architecture(f'Обычная лестница: yaw{yaw*45}°, три разных предмета90006/90018/90019','ladder',
                     [row for row in rows if row['kind'] in ladder_kinds and row['root'][2]==52 and row['yaw']==yaw],
                     ordinaryOnly=True,sourceClone=False,helpersPerSection=0,playerPhysicalBoxesPerSection=0 if yaw%2 else 1,
                     nonPlayerPhysicalBoxesPerSection=1,selectionBoxesPerSection=1,
                     review='Кардинальная секция на боковой опоре; диагональная в углу двух стен не блокирует игрока. Middle pick сохраняет самостоятельный тип.')
    architecture('Боковая частичная опора и обычный middle pick','ladder_partial_support',
                 [row for row in rows if row['kind'] in ladder_kinds and row['root'][2]==48],
                 playerViewDegrees=-135,flatWallFallback='NORTH',pickFrom=[8,64,48],
                 support='Это боковая опора. Native UP на дробной высоте BOTTOM slab/fence отдельно отвергается без расхода предмета.')
    for kind in ['prototype_ladder','prototype_ladder_art_1','prototype_ladder_art_2']:
        architecture(f'Самостоящий UP стек{ids[kind]}: три независимые секции','ladder_freestanding',
                     [row for row in rows if row['kind']==kind and row.get('placementFace')=='up'],
                     initialFoundation='STONE/full-height TOP slab/previous section',removeIntermediate='Оставшиеся секции сохраняются; нет зависимого разрушения всего стека.')
    stations.append({'name':'Принятое объёмное RP дерево','category':'accepted_rp_tree','placementKind':'rp_entity',
                     'roots':[[46,64,38]],'asset':'tree1','preserveAccepted':True})
    stations.append({'name':'Два рычага и реальные механизмы','category':'rp_mechanism_links','placementKind':'rp_entity',
                     'roots':[row['root'] for row in gameplay['rpObjects']],'roles':[row['role'] for row in gameplay['rpObjects']],
                     'architectureDoorRoot':gameplay['architectureDoorRoot'],'links':copy.deepcopy(gameplay['links']),
                     'review':'LINK/CONNECTIONS/UNLINK/CANCEL; исходная задержка70 ticks; общий RPdoor двух рычагов; save/restart не теряет UUID связи.'})
    stations.append({'name':'Два зарегистрированных Hunterlamp с маршрутами в обе стороны','category':'hunterlamp_network','placementKind':'rp_entity',
                     'roots':[row['root'] for row in gameplay['lamps']],'roles':['LampA','LampB'],'routes':copy.deepcopy(gameplay['lampRoutes']),
                     'review':'Обычное survival меню/travel A→B и B→A после restart; respawn не изменён. Automatic light9 исходного Forge фонаря в новом ordinary port пока UNRESOLVED_NEW_LAMP_LIGHTING.'})
    for row in gameplay['visualObjects']:
        details={key:copy.deepcopy(value) for key,value in row.items() if key not in {'role','root'}}
        stations.append({'name':row['role'],'category':'rp_visual_gameplay','placementKind':'rp_entity','roots':[row['root']],**details})
    scene['reviewStations']=stations
    scene['objectCountsByKind']=dict(sorted(Counter(row['kind'] for row in rows).items()))
    scene['architectureRootCount']=len(rows)
    scene['ordinaryRpItemCount']=sum(len(gameplay[group]) for group in ['rpObjects','lamps','visualObjects'])
    scene['rpSceneEntityCount']=scene['ordinaryRpItemCount']+1
    scene['nativeFixtureCount']=len(scene['nativeBlocks'])
    scene['reviewStationCount']=len(stations)
    scene['playerInstructions']=('Spawn0,64,0. V9:102 архитектурных roots,17 обычных RP item fixtures плюс принятое RP tree1. '
        'Двери/принятые деревянные окна z8; два самостоятельных стекла90004/90010 x22/28 в трёх монтажах, вертикальные floor/slab controls x40/46. '
        'Ограды AUTO x-38..-7,z16..25; разные рисунки — отдельные предметы90011..17 на z29/36. '
        'Крыша5x2 z30/33; только принятое плоское дерево x36,z38 и foreign chest x37; RP tree1 x46,z38. '
        'Три ladder item типа90006/18/19: восемь ориентаций z52, боковые частичные опоры z48, независимые UP стеки x8/14/20,z60. '
        'Два рычага/door/chest/gate z-16, два зарегистрированных Hunterlamp x60/80,z-10; cages/chandeliers/NPC/stairs/RP ladder/woodgate z80. '
        'Поворот/монтаж/BASE-ALT/видимость собак — состояния; смена самостоятельного рисунка инструментом не предлагается. '
        'Обычные gameplay и права строительного редактора раздельны; технические проверки не заменяют ручную визуальную приёмку.')
    mentioned={tuple(pos) for station in stations if station['placementKind']=='architecture' for pos in station['roots']}
    assert mentioned==set(by_root),'Public stations must cover exactly the real architecture roots'
    assert sum(scene['objectCountsByKind'].values())==len(rows)


def generate(root: Path):
    previous_path = root / 'tools/first_set_scene_input.json'
    previous_bytes = previous_path.read_bytes()
    scene = json.loads(previous_bytes)
    scene['sceneId'] = 'first-set-review-v9'
    scene['previousInputProvenance'] = {'path': 'tools/first_set_scene_input.json', 'sha256': hashlib.sha256(previous_bytes).hexdigest(), 'status': 'HISTORICAL_READ_ONLY'}
    rows = []
    removed = []
    for old in scene['objects']:
        row = copy.deepcopy(old)
        kind = row['kind']
        row.pop('temporaryId',None)
        if kind == 'prototype_wall':
            material = int(row.get('art',{}).get('material','6'))
            if material != 6:
                row['kind'] = f'prototype_wall_skin_{material}'
            row['art'] = {'connections':'auto'}
        if kind == 'prototype_thin_window':
            if row['variant'] == 2:
                removed.append({'oldRoot': row['root'], 'reason': 'window_03 is the rotated window_02 source form; excluded from ordinary issuance'})
                continue
            if row['variant'] == 1:
                row['kind'] = 'prototype_glass_window_02'
            row['variant'] = 0
            row['yaw'] = row['yaw'] // 2 * 2
            row['independentSupport'] = True
            row['ordinaryQuarterTurnsOnly'] = True
        if kind == 'prototype_tree':
            if row['variant'] == 0:
                removed.append({'oldRoot': row['root'], 'reason': 'old unaccepted flat tree art excluded; accepted V8 variant1 becomes the sole ordinary type'})
                continue
            row['variant'] = 0
            row['acceptedArt'] = 'V8 variant1; sole ordinary tree'
        if kind == 'prototype_ladder':
            if 'playerYawOverride' in row:
                row['yawOverride'] = row.pop('playerYawOverride')
            variant = row['variant']
            if variant:
                row['kind'] = f'prototype_ladder_art_{variant}'
            row['variant'] = 0
            row['ordinaryCorner'] = bool(row['yaw'] % 2)
        if kind == 'prototype_roof':
            row['physicsPolicy'] = 'single grid-aligned mounted 1x1x1 cube; render/selection independent'
        rows.append(row)
    for art in range(3):
        kind = 'prototype_ladder' if art == 0 else f'prototype_ladder_art_{art}'
        for y in range(64, 67):
            rows.append({'kind': kind, 'root': [8+art*6,y,60], 'yaw': 0, 'variant': 0, 'profile': 'base', 'placementFace': 'up', 'independentSection': True, 'station': f'freestanding-ladder-art-{art}'})
    for skin in (0,1,2,3,4,5,7):
        rows.append({'kind': f'prototype_wall_skin_{skin}', 'root': [-40+skin*4,64,36], 'yaw': 0, 'variant': 0, 'profile': 'base', 'station': f'independent-wall-skin-{skin}', 'art': {'connections':'auto'}})
    scene['objects'] = rows
    scene['excludedOrdinaryControls'] = removed
    scene['fills'].append({'from': [-44,63,-24], 'to': [90,63,0], 'block': 'minecraft:smooth_stone'})
    scene['fills'].append({'from': [-80,63,63], 'to': [90,63,96], 'block': 'minecraft:smooth_stone'})
    # The accepted tree receives the chest preservation control at its own root side.
    for row in scene['nativeBlocks']:
        if row.get('diamondCount') and row['pos'] == [25,64,38]:
            row['pos'] = [37,64,38]
    # A decorative unregistered lamp is replaced by the separate real network stand.
    scene['commands'] = [command for command in scene.get('commands', []) if not command.startswith('summon bloodborne_rp:hunterlamp ')]
    gameplay = {
        'schemaVersion': 1, 'phase': 'AUTHOR', 'allowedLevelName': scene['allowedLevelName'],
        'productionGuard': 'QA_ONLY_SEPARATE_FRESH_OR_REOPENED_REVIEW_WORLD',
        'foundationFills': [{'from': [-44,63,-24], 'to': [90,63,0]}, {'from':[-80,63,70],'to':[90,63,96]}],
        'architectureDoorRoot': [-12,64,8],
        'rpObjects': [
            {'role':'LeverA','asset':'lever_1','root':[-38,64,-16]},
            {'role':'LeverB','asset':'lever_2','root':[-30,64,-16]},
            {'role':'RpDoor','asset':'door_1','root':[-18,64,-16]},
            {'role':'Chest','asset':'chest','root':[0,64,-16]},
            {'role':'Gate','asset':'small_gate','root':[14,64,-16]},
        ],
        'lamps': [
            {'role':'LampA','asset':'hunterlamp','root':[60,64,-10],'name':'V9 · Лампа A'},
            {'role':'LampB','asset':'hunterlamp','root':[80,64,-10],'name':'V9 · Лампа B'},
        ],
        'visualObjects': [
            {'role':'CageHidden','asset':'cage_obj_1','root':[-70,64,80],'dogsVisible':False},
            {'role':'CageVisibleTwin','asset':'cage_obj_1','root':[-58,64,80],'dogsVisible':True},
            {'role':'CageTwo','asset':'cage_obj_2','root':[-46,64,80],'dogsVisible':True},
            {'role':'CageThree','asset':'cage_obj_3','root':[-34,64,80],'dogsVisible':True},
            {'role':'ChandelierFloor','asset':'chandelier_small','root':[-18,64,80]},
            {'role':'ChandelierCeiling','asset':'chandelier_small','root':[-8,74,80],'clicked':[-8,74,80],'mountFace':'down'},
            {'role':'NpcWindow','asset':'npc_window','root':[4,64,80]},
            {'role':'RpStairs','asset':'stairs','root':[22,64,80],'yawOverride':270},
            {'role':'RpLadder','asset':'ladder','root':[42,64,80]},
            {'role':'WoodGate','asset':'wood_gate','root':[70,64,80]},
        ],
        'nativeSupports': [{'pos':[-8,74,80],'block':'minecraft:stone'}],
        'links': {'LeverA':['RpDoor','Chest','Gate','architectureDoorRoot','RpLadder','WoodGate'], 'LeverB':['RpDoor']},
        'lampRoutes': [['LampA','LampB'], ['LampB','LampA']],
        'manualClientInteraction': 'NOT_RUN; stand remains available for ordinary user review',
        'newLampLighting': 'UNRESOLVED_NEW_LAMP_LIGHTING; original Forge activation creates light9; ordinary port automatic lighting NOT_RUN; two migrated technical light9 cells separately preserved',
    }
    scene['gameplayStand'] = copy.deepcopy(gameplay)
    scene['reviewStatus'] = 'PREPARED_INPUT_REQUIRES_CURRENT_NATIVE_AUTHOR_REOPEN_AND_CLIENT_PROOFS'
    review_metadata(root,scene,gameplay)
    return scene, gameplay


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--root', type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument('--scene-output', type=Path)
    parser.add_argument('--gameplay-output', type=Path)
    args = parser.parse_args()
    root = args.root.resolve()
    scene, gameplay = generate(root)
    scene_out = args.scene_output or root / 'tools/first_set_scene_v9_input.json'
    game_out = args.gameplay_output or root / 'tools/first_set_v9_gameplay_input.json'
    for path, value in ((scene_out,scene),(game_out,gameplay)):
        path.parent.mkdir(parents=True,exist_ok=True)
        path.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
    reopen = copy.deepcopy(gameplay)
    reopen['phase'] = 'REOPEN'
    reopen_out = game_out.with_name(game_out.stem+'_reopen'+game_out.suffix)
    reopen_out.write_text(json.dumps(reopen,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
    print(json.dumps({'status':'PREPARED_NOT_RUN','sceneInput':str(scene_out),'architectureRoots':len(scene['objects']),'nativeFixtures':len(scene['nativeBlocks']),'gameplayAuthorInput':str(game_out),'gameplayReopenInput':str(reopen_out),'ordinaryRpItemFixtures':len(gameplay['rpObjects'])+len(gameplay['lamps'])+len(gameplay['visualObjects']),'historicalV8Modified':False},ensure_ascii=False))


if __name__ == '__main__':
    main()
