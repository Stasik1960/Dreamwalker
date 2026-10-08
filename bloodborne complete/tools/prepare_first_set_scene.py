"""Write bounded review V8 authoring input; creates no world and launches nothing."""
import hashlib
import json
from collections import Counter
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]


def previous_scene():
    native={}
    def stone(pos):
        key=tuple(pos)
        if key in native and native[key]["block"]!="minecraft:stone_bricks":raise ValueError("Native fixture overlap")
        native[key]={"pos":list(pos),"block":"minecraft:stone_bricks"}
    objects=[]
    def obj(kind,root,yaw=0,variant=0,profile="base",**extra):
        objects.append(dict(kind=kind,root=root,yaw=yaw,variant=variant,profile=profile,**extra))
    for x,profile,opened in [(-12,"base",False),(-6,"alt",False),(0,"base",True)]:
        obj("prototype_double_door",[x,64,8],profile=profile,open=opened)
    obj("prototype_wood_window",[8,64,8],open=False)
    obj("prototype_wood_window",[14,64,8],profile="alt",open=True)
    for variant in range(3):obj("prototype_thin_window",[22+variant*6,66,8],variant=variant)
    obj("prototype_roof",[-12,64,26]);obj("prototype_roof",[-8,64,26],yaw=1,profile="alt")
    obj("prototype_tree",[24,64,32]);obj("prototype_tree",[26,64,32],profile="alt")
    native[(25,64,32)]={"pos":[25,64,32],"block":"minecraft:chest","diamondCount":7,"preserve":True}
    # Same wall registry ID, eight source materials, explicit low/tall topology.
    for material in range(8):
        x=-36+material*4
        for offset in range(3):
            art={"material":str(material),"course":"low","connections":"manual","rotation":"0","post":"true",
                 "east":"true" if offset<2 else "false","west":"true" if offset>0 else "false","north":"false","south":"false"}
            obj("prototype_wall",[x+offset,64,22],art=art)
        art={"material":str(material),"course":"tall","connections":"manual","rotation":"0","post":"true",
             "east":"true","west":"true","north":"false","south":"false"}
        obj("prototype_wall",[x+1,65,22],profile="alt",art=art)
    backing=[[(0,1)],[(0,1),(-1,0)],[(-1,0)],[(-1,0),(0,-1)],[(0,-1)],[(0,-1),(1,0)],[(1,0)],[(1,0),(0,1)]]
    for yaw in range(8):
        x=-36+yaw*8
        for dx,dz in backing[yaw]:
            for y in range(64,68):stone([x+dx,y,48+dz])
        for variant in range(3):obj("prototype_ladder",[x,64+variant,48],yaw=yaw,variant=variant,profile="alt" if yaw%2 else "base")
    commands=["gamerule doMobSpawning false","gamerule doDaylightCycle false","gamerule spawnRadius 0",
              "time set day","weather clear","defaultgamemode creative","setworldspawn 0 64 0",
              "summon bloodborne_rp:tree1 40 64 35","summon bloodborne_rp:hunterlamp 3 64 3"]
    return {"schemaVersion":1,"sceneId":"first-set-isolated-v1","authoringGuard":"FRESH_FLAT_ISOLATED_WORLD_ONLY",
            "allowedLevelName":"isolated-smoke-world","fills":[{"from":[-44,63,-3],"to":[50,63,62],"block":"minecraft:smooth_stone"}],
            "nativeBlocks":list(native.values()),"objects":objects,"commands":commands,
            "reviewStatus":"PROPOSAL_NOT_ACCEPTED","productionJarContainsQa":False,
            "playerInstructions":"spawn 0,64,0; first object row z8; walls z22; roofs z26; composite trees x24/26,z32; ladders z48; RP controls x40,z35 and x3,z3"}


def scene():
    native={};objects=[];stations=[]
    catalogue=json.loads((ROOT/'src/architecture/resources/bloodborne_dw/debug_catalogue.json').read_text(encoding='utf8'))
    numbers={row['registryId']:row['temporaryId'] for row in catalogue['entries']}
    def fixture(pos,block='minecraft:stone_bricks',**extra):
        key=tuple(pos);value={'pos':list(pos),'block':block,**extra}
        if key in native and native[key]!=value:raise ValueError('Conflicting native fixture at '+str(key))
        native[key]=value
    def obj(kind,root,yaw=0,variant=0,profile='base',**extra):
        objects.append(dict(kind=kind,root=list(root),yaw=yaw,variant=variant,profile=profile,
                            temporaryId=numbers['bloodborne_dw:'+kind],**extra))
    def station(name,category,roots,**extra):
        stations.append(dict(name=name,category=category,roots=[list(p) for p in roots],**extra))
    doors=[]
    for x,profile,opened in [(-12,'base',False),(-6,'alt',False),(0,'base',True)]:
        root=[x,64,8];obj('prototype_double_door',root,profile=profile,open=opened);doors.append(root)
    station('Одна центральная створка: BASE, ALT, открытая','door',doors,
            review='Outer authored frame stays fixed; only the designated central leaf moves; inspect passage from both sides.')
    obj('prototype_wood_window',[8,64,8],open=False);obj('prototype_wood_window',[14,64,8],profile='alt',open=True)
    station('Принятое деревянное окно','accepted_wood_window',[[8,64,8],[14,64,8]],preserveAccepted=True)
    for variant in range(3):
        x=22+variant*6;wall=[x,64,8];floor=[x,64,18];ceiling=[x,66,25]
        for root,mount,face in [(wall,'vertical','north'),(floor,'floor','up'),(ceiling,'ceiling','down')]:
            obj('prototype_thin_window',root,variant=variant,mount=mount,mountFace=face,glazingMounted=True)
        for dx in range(-1,2):
            for y in range(64,67):fixture([x+dx,y,9])
            for dz in range(-1,2):fixture([x+dx,67,25+dz])
        station(f'Тонкое остекление: исходная форма{variant+1}, три плоскости','glazing',[wall,floor,ceiling],
                mountModes=['vertical','floor','ceiling'],variant=variant,
                mountHeights='GlazingMount.seat derives MountY/SourceShift from transformed bounds and contacted surface; no guessed compensation.')
    upright=[]
    for root,variant,support in [([40,64,18],0,None),([46,65,18],1,'bottom'),([52,65,18],2,'top')]:
        obj('prototype_thin_window',root,variant=variant,mount='vertical',mountFace='up',glazingMounted=True,placementSneaking=True)
        if support:fixture([root[0],64,root[2]],'minecraft:stone_slab',properties={'type':support,'waterlogged':'false'})
        upright.append(root)
    station('Вертикальная плоскость на полу и двух высотах плиты','glazing_partial_support',upright,
            contacts=['full floor','bottom slab topY64.5','top slab topY65'],placement='Sneak+UP keeps vertical plane; height comes from contacted surface.')
    roof=[]
    for z,yaw in [(30,0),(33,4)]:
        for x in range(-16,-11):
            for y in [64,65]:fixture([x,y,z])
            root=[x,66,z];obj('prototype_roof',root,yaw=yaw);roof.append(root)
    station('Кровля: две противоположные полосы по пять модулей','roof_join',roof,
            layout='5x2 opposite yaw0/4; real supports below each root',acceptance='DEFERRED_REVIEW_NOT_ACCEPTED')
    obj('prototype_tree',[24,64,38],variant=0);obj('prototype_tree',[36,64,38],variant=1,profile='alt')
    fixture([25,64,38],'minecraft:chest',diamondCount=7,preserve=True)
    station('Плоское дерево: original control и локальная нижняя правка','composite_tree',[[24,64,38],[36,64,38]],
            variants=['original0','proposed1'],acceptedUpperPartsPreserved=True,foreignChest=[25,64,38])
    station('Принятое объёмное RP дерево','accepted_rp_tree',[[46,64,38]],preserveAccepted=True)
    def walls(name,coords,**details):
        for pos in coords:obj('prototype_wall',pos,art={'material':'6','connections':'auto'})
        station(name,'wall_auto',coords,**details)
    walls('Один элемент',[[-38,64,16]])
    walls('Прямая линия',[[x,64,16] for x in range(-32,-27)])
    walls('Угол',[[-24,64,16],[-23,64,16],[-22,64,16],[-22,64,17],[-22,64,18]])
    walls('Т-соединение',[[-16,64,16],[-17,64,16],[-15,64,16],[-16,64,17],[-16,64,18]])
    walls('Крест',[[-10,64,18],[-10,64,17],[-10,64,19],[-11,64,18],[-9,64,18]])
    walls('Полный сосед',[[-38,64,23]],neighbor=[-37,64,23]);fixture([-37,64,23])
    mixed=[[-30,64,23],[-31,64,23],[-29,64,23],[-30,64,22],[-30,64,24]]
    walls('Смешанные LOW/TALL под частичным блоком сверху',mixed,above=[-30,65,23])
    fixture([-30,65,23],'minecraft:polished_deepslate_wall',properties={'north':'low','east':'none','south':'none','west':'none','up':'true','waterlogged':'false'})
    fixture([-30,65,22],'minecraft:stone')
    walls('Съёмный полный сосед',[[-18,64,23]],removeForReview=[-17,64,23],review='Break/add ordinary stone neighbor; AUTO updates without builder.');fixture([-17,64,23])
    walls('Собственный столб сверху',[[-9,64,25],[-8,64,25],[-7,64,25],[-8,65,25]],review='Place/remove upper wall to observe lower post changes.')
    for x,material in [(-38,0),(-34,2),(-30,7)]:obj('prototype_wall',[x,64,29],art={'material':str(material),'connections':'auto'})
    station('Другие материалы того же item/type','wall_art',[[-38,64,29],[-34,64,29],[-30,64,29]],singleRegistryAndItem=True)
    backing=[[(0,1)],[(0,1),(-1,0)],[(-1,0)],[(-1,0),(0,-1)],[(0,-1)],[(0,-1),(1,0)],[(1,0)],[(1,0),(0,1)]]
    for yaw in range(8):
        x=-36+yaw*8;column=[]
        for dx,dz in backing[yaw]:
            for y in range(64,68):fixture([x+dx,y,52+dz])
        for variant in range(3):
            root=[x,64+variant,52];obj('prototype_ladder',root,yaw=yaw,variant=variant,profile='alt' if yaw%2 else 'base',placementPathHint='ordinary Item.useOnBlock');column.append(root)
        station(f'Обычная лестница: yaw{yaw*45}°, три независимые секции','ladder',column,
                ordinaryOnly=True,sourceClone=False,physicalBoxesPerSection=1,helpersPerSection=0)
    for root,block,properties,pick in [([8,64,48],'minecraft:stone_slab',{'type':'top'},None),
                                      ([12,64,48],'minecraft:stone_slab',{'type':'bottom'},[8,64,48]),
                                      ([16,64,48],'minecraft:stone_brick_stairs',{'facing':'north','half':'bottom','shape':'straight'},None)]:
        fixture([root[0],root[1],root[2]+1],block,properties=properties)
        extra={'placementPathHint':'ordinary Item.useOnBlock','itemOrigin':'middle_pick' if pick else 'ordinary_inventory_item','playerYawOverride':-135}
        if pick:extra['pickFrom']=pick
        obj('prototype_ladder',root,variant=0,**extra)
    station('Лестница на частичных опорах и middle pick','ladder_partial_support',[[8,64,48],[12,64,48],[16,64,48]],
            playerViewDegrees=-135,flatWallFallback='NORTH',pickFrom=[8,64,48])
    positions=[tuple(row['root']) for row in objects]
    assert len(positions)==len(set(positions)) and not set(positions)&set(native),'Root/native fixture overlap'
    assert all(63<=p[1]<=100 and abs(p[0])<=96 and abs(p[2])<=96 for p in positions+list(native))
    commands=['gamerule doMobSpawning false','gamerule doDaylightCycle false','gamerule spawnRadius 0',
              'time set day','weather clear','defaultgamemode creative','setworldspawn 0 64 0',
              'summon bloodborne_rp:tree1 46 64 38','summon bloodborne_rp:hunterlamp 3 64 3']
    return {'schemaVersion':1,'sceneId':'first-set-review-v8','authoringGuard':'FRESH_FLAT_ISOLATED_WORLD_ONLY',
            'allowedLevelName':'isolated-smoke-world','fills':[{'from':[-44,63,-3],'to':[58,63,62],'block':'minecraft:smooth_stone'}],
            'nativeBlocks':list(native.values()),'objects':objects,'commands':commands,'reviewStations':stations,
            'objectCountsByKind':dict(sorted(Counter(row['kind'] for row in objects).items())),
            'reviewStatus':'PROPOSAL_NOT_ACCEPTED','productionJarContainsQa':False,'sourceCityLoaded':False,'sourceCityModified':False,
            'actualCreativeUiAcceptance':'PENDING_USER_REVIEW',
            'playerInstructions':'Spawn0,64,0; single-leaf doors/accepted wood windows z8; glazing vertical z8, floor z18, ceiling z25; AUTO walls x-38..-7,z16..29; roof5x2 z30/33; original/proposed flat tree x24/36,z38; accepted RP tree x46,z38; ladders z52 and partial supports z48. See reviewStations for actions.'}


def main():
    document=scene()
    output=ROOT/'tools/first_set_scene_input.json'
    if output.exists() and json.loads(output.read_text(encoding='utf8')).get('sceneId')!='first-set-review-v8':
        data=output.read_bytes();previous=hashlib.sha256(data).hexdigest();history=ROOT/f'reports/input-history/first_set_scene_input-before-v8-{previous}.json';history.parent.mkdir(parents=True,exist_ok=True)
        if history.exists():assert history.read_bytes()==data
        else:history.write_bytes(data)
    output.write_text(json.dumps(document,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
    # Passed to run_final_server after the add-on authors/saves the complete
    # composite roots+helpers. No architecture family is created by setblock.
    commands=["save-all flush"]
    (ROOT/'tools/first_set_scene_commands.json').write_text(json.dumps(commands,indent=2)+'\n',encoding='utf8')
    print(json.dumps({'sceneId':document['sceneId'],'objects':len(document['objects']),'nativeFixtures':len(document['nativeBlocks']),
                      'counts':document['objectCountsByKind'],'reviewStations':len(document['reviewStations']),'sceneInput':str(output)}))


if __name__=='__main__':main()
