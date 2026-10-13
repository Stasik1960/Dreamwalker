"""Prepare a small SHA-bound native acceptance plan for a fresh flat test world.

No source map/gallery is copied or changed. Setup creates vanilla floor and gives
items only. An optional SHA-bound network marker runs ordinary client interaction
packets and actual vanilla doItemPick; physical mouse/keyboard remains NOT_RUN.
Run through CodexControl/Guard.ps1 -Mode Operation after final import/build freeze.
"""
from __future__ import annotations
import argparse
import hashlib
import json
import math
from pathlib import Path
import re

ROOT=Path(__file__).resolve().parents[1]
DOC=ROOT/'docs/remaining-transfer'
RES=ROOT/'src/architecture/resources/bloodborne_dw/composite'

def write(path,value):path.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
def item(id,variant):return id+'{BlockStateTag:{variant:"'+str(variant)+'",profile:"base",open:"false"}}'
def placement_camera(root,yaw):
    angle=math.radians(yaw);forward=(-math.sin(angle),math.cos(angle))
    return [round(root[0]+.5-3*forward[0],4),root[1],round(root[2]+.5-3*forward[1],4),yaw,round(math.degrees(math.atan2(1.62,3)),4)]
def tp(name,camera):return 'tp '+name+' '+' '.join(str(round(value,4)) for value in camera)
def viewing_camera(root,bounds,side=False):
    center=[root[i]+(bounds['from'][i]+bounds['to'][i])/32 for i in range(3)]
    span=max((bounds['to'][i]-bounds['from'][i])/16 for i in range(3));distance=max(4,span*1.4)
    eye=[center[0]+(distance if side else distance*.45),center[1]+max(1.5,span*.45),center[2]-(distance*.45 if side else distance)]
    delta=[center[i]-eye[i] for i in range(3)];yaw=math.degrees(math.atan2(-delta[0],delta[2]));pitch=-math.degrees(math.atan2(delta[1],math.hypot(delta[0],delta[2])))
    return [round(eye[0],4),round(eye[1]-1.62,4),round(eye[2],4),round(yaw,4),round(pitch,4)]

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jar',type=Path,required=True)
    parser.add_argument('--output',type=Path,required=True)
    parser.add_argument('--username',default='dwqa_new_a')
    parser.add_argument('--second-name',default='dwqa_new_b')
    args=parser.parse_args()
    for name in (args.username,args.second_name):
        if not re.fullmatch('[A-Za-z0-9_]{3,16}',name):raise ValueError('Safe isolated offline player names required')
    output=args.output.resolve()
    if not output.is_relative_to((ROOT/'build').resolve()) or output.exists():raise ValueError('Use a fresh output directory under this project build/')
    progress=json.loads((DOC/'import-progress.json').read_text(encoding='utf-8'))
    if progress.get('status')!='COMPLETE':raise ValueError('Final importer has not finished')
    imports=json.loads((DOC/'imported-resources.json').read_text(encoding='utf-8'))
    jar=args.jar.resolve();jar_sha=hashlib.sha256(jar.read_bytes()).hexdigest()
    choices=[]
    for obj in imports['objects']:
        spec=json.loads((RES/(obj['path']+'.json')).read_text(encoding='utf-8'))
        for index,variant in enumerate(spec['variants']):
            bounds=variant['visibleBounds'];span=max((bounds['to'][i]-bounds['from'][i])/16 for i in range(3))
            choices.append({'object':obj,'spec':spec,'variant':index,'data':variant,'span':span})
    chosen=[];used=set();unavailable=[]
    def choose(role,predicate,preferred=()):
        available=[value for value in choices if predicate(value) and (value['object']['path'],value['variant']) not in used]
        if not available:unavailable.append({'role':role,'status':'NOT_AVAILABLE','reason':'No final successfully imported finished candidate matching this role'});return
        available.sort(key=lambda value:(0 if value['object']['path'] in preferred else 1,value['span'],len(value['data']['poses']['closed']['selection']),value['object']['path'],value['variant']))
        selected=available[0];used.add((selected['object']['path'],selected['variant']));chosen.append((role,selected))
    for role,term,preferred in [('bench','bench',()),('barrel','barrel',('source_addon_barrel_0',)),('books','books',('source_addon_books_0',)),('bag','bag',('source_addon_bag_0',)),('lamp','lamp',('source_1_spirelamp_001',)),('statue','statue',()),('roof','roof',())]:
        choose(role,lambda value,term=term:term in value['object']['path'],preferred)
    choose('whole_door',lambda value:value['spec']['openable'])
    choose('pitched_asymmetric',lambda value:any(application.get('x',0) for application in value['data'].get('sourceClosedApplications',[])))
    choose('uvlocked_group',lambda value:any(application.get('uvlock') and (application.get('x',0) or application.get('y',0)) for application in value['data'].get('sourceClosedApplications',[])))
    choose('emissive_overlay',lambda value:any(part['model'].startswith('bloodborne_dw:remaining/source_emissive/') for part in value['data']['poses']['closed']['parts']),('source_lantern_1',))
    choose('helper_only_selection',lambda value:not any(all(box['from'][i]<16 and box['to'][i]>0 for i in range(3)) for box in value['data']['poses']['closed']['selection']))
    choose('biome_tint',lambda value:value['object']['path']=='source_grass_block')
    choose('authored_grass_variant',lambda value:value['object']['path']=='source_bricks_grass_block_0')
    choose('authored_fern_tint',lambda value:value['object']['path']=='source_potted_fern')
    commands=['op '+args.username,'op '+args.second_name,'gamerule doDaylightCycle false','gamerule doWeatherCycle false','gamerule doMobSpawning false','gamerule doFireTick false','time set day','weather clear']
    cases=[];frames=[];inventory=[]
    for index,(role,value) in enumerate(chosen):
        obj,spec,variant=value['object'],value['spec'],value['variant'];root=[16+(index%5)*48,128,16+(index//5)*48]
        # Physical placement checks a halo around every source box; its chunk
        # must be available even before an operator/player approaches the pad.
        # Cover both original and copy stations under all eight object yaws.
        bounds=value['data']['visibleBounds']
        radius=math.ceil(max(math.hypot(x-8,z-8) for x in (bounds['from'][0],bounds['to'][0]) for z in (bounds['from'][2],bounds['to'][2]))/16)+2
        commands.append(f'forceload add {min(root[0]-5,root[0]-radius)} {min(root[2]-5,root[2]-radius)} {max(root[0]+25,root[0]+20+radius)} {max(root[2]+5,root[2]+radius)}')
        commands.append(f'fill {root[0]-5} 127 {root[2]-5} {root[0]+25} 127 {root[2]+5} minecraft:stone')
        stack=item(obj['registryId'],variant)
        inventory.append('item replace entity '+args.username+' '+('hotbar.'+str(index) if index<9 else 'inventory.'+str(index-9))+' with '+stack+' 1')
        camera=placement_camera(root,0)
        cases.append({'case':role,'registryId':obj['registryId'],'variant':variant,'russianName':obj['variants'][variant]['name'],'openable':spec['openable'],
                      'root':root,'copyRoot':[root[0]+20,root[1],root[2]],'visibleBounds':value['data']['visibleBounds'],
                      'sourceApplications':value['data'].get('sourceClosedApplications',[]),'equipCommand':'item replace entity '+args.username+' weapon.mainhand with '+stack+' 1',
                      'placementCameras':[tp(args.username,placement_camera(root,yaw)) for yaw in (0,90,180,270)],'initialCamera':tp(args.username,camera),
                      'readOnlyRootQuery':'data get block '+' '.join(str(n) for n in root),'expectedStates':['exact registry ID and variant','new independent owner UUID','rotation0/2/4/6 from actual item use','whole-object removal with no helpers','save/rejoin retains state'],
                      'nativePlacement':'NOT_RUN','nativePick':'NOT_RUN','nativeUse':'NOT_RUN' if spec['openable'] else 'NOT_APPLICABLE','nativeBreak':'NOT_RUN','rejoin':'NOT_RUN'})
        for side,label in ((False,'front'),(True,'side')):
            view=viewing_camera(root,value['data']['visibleBounds'],side)
            frames.append({'fileName':f'new-{index+1:02d}-{role}-{label}.png','camera':view,'command':tp('@s',view),'waitTicks':40})
    if cases:inventory.append(cases[0]['initialCamera'])
    plan={'schema':'dreamwalker-remaining-transfer-native-plan-v1','guard':'FRESH_FLAT_TEST_WORLD_ONLY','productionJarSha256':jar_sha,'productionJar':str(jar),'sourceMapOrGalleryUsed':False,
          'setupPlacesNewObjects':False,'username':args.username,'secondUsername':args.second_name,'cases':cases,'unavailableRoles':unavailable,'status':'PREPARED_NOT_GAMEPLAY_PASS',
          'instructions':'Physical F8/middle mouse/F9 acceptance is separate. Optional network marker uses actual client engine interaction packets and vanilla doItemPick, never physical Windows input. Setup supplies items and vanilla floor only; framebuffer images and NBT queries do not prove mouse/keyboard input.'}
    output.mkdir(parents=True);write(output/'setup.json',commands);write(output/'inventory-after-join.json',inventory);write(output/'native-plan.json',plan);write(output/'remaining-transfer-qa-input.json',{'productionJarSha256':jar_sha,'frames':frames})
    write(output/'remaining-transfer-network-input.json',{'productionJarSha256':jar_sha,'freshTestWorldOnly':True,'cases':cases,'manualMouseKeyboardInput':'NOT_RUN','method':'Ordinary client engine interaction packets and vanilla doItemPick; first four representatives all eight yaws, every case copy/removal and retained final source owner for server save/rejoin.'})
    lines=['# Нативная проверка нового переноса','',f'JAR SHA-256: `{jar_sha}`. Только новый плоский тестовый мир; карта/галерея не используются.','',
           '`setup.json` создаёт обычный каменный пол. После входа клиента отправить `inventory-after-join.json` через server mailbox: он выдаёт новые предметы. Новые объекты ещё не установлены. Не считать PREPARED игровым PASS.','',
           'Для каждого случая отправить equipCommand и initialCamera через localhost server mailbox. Затем настоящий F8 ставит объект предметом. Считать его ID/state/owner через readOnlyRootQuery; запрос не заменяет нажатие.','',
           'Проверить доступные повороты настоящими установками с yaw0/90/180/270, удаляя прежний экземпляр настоящим F9 между попытками. Вернуть yaw0 для кадров. Навести на видимую часть: обычная средняя кнопка мыши копирует предмет; поставить копию на copyRoot и подтвердить другой UUID, тот же variant/profile/open.','',
           'Для whole_door открыть и закрыть настоящим F8 без ванильного носителя в руке; root query должен показать open=true/false и неизменный owner UUID. Удалить через доступную видимую часть F9, проверить полное исчезновение helpers.','',
           'Необязательный remaining-transfer-network-input.json разрешает отдельный прогон CLIENT_NETWORK: настоящий MinecraftClientInteractionManager отправляет place/use/attack packets, настоящий MinecraftClient.doItemPick копирует art; synced server owner UUID подтверждает установку и независимую копию. Первые четыре представителя проходят восемь yaw, все представители — копирование/удаление; затем финальные объекты оставляются для save/rejoin. Камера использует реальный creative flying ability packet, чтобы не падать между проверками. Физический F8/мышь/F9 от этого не получает PASS.','',
           'После завершения сетевого прогона отдельный capture marker отправляет только tp камеры и сохраняет framebuffer PNG. Оценить PNG вручную; CAPTURE_COMPLETE не равен визуальному PASS. Без network marker addon только читает baked resources и, при capture marker, управляет камерой.','',
           'При повторном входе после server restart убрать прежний interaction network marker: он требует пустые свежие станции и не должен повторно ставить объекты. Для чтения сохранённых объектов tools/prepare_remaining_rejoin.py создаёт новый marker mode=VERIFY_REJOIN из первого CLIENT_NETWORK PASS: клиент только навигирует камерой и сверяет synced server ID/variant/rotation/open/UUID, пишет remaining-transfer-client-rejoin.json. Сохранить прежние reports, использовать новый client run-directory; baked/capture markers допустимы с тем же production SHA. Само сохранение или framebuffer не подтверждает полный server restart.','',
           'Для сохранения: save-all flush, штатно закрыть клиента и сервер; run_v11_server.py --world-copy допускается только для этого нового saved test-world под build/. Новый клиент должен видеть те же ID/variant/rotation/open/UUID. Статусы фиксировать отдельно PASS/FAIL/NOT_RUN.','',
           '| Случай | Новый ID / variant | Корень |','| --- | --- | --- |']
    for case in cases:lines.append(f"| {case['case']} | `{case['registryId']}` / {case['variant']} | `{case['root']}` |")
    (output/'native-guide.md').write_text('\n'.join(lines)+'\n',encoding='utf-8')
    print(json.dumps({'status':plan['status'],'cases':len(cases),'frames':len(frames),'unavailable':len(unavailable),'output':str(output),'jarSha256':jar_sha}))

if __name__=='__main__':main()
