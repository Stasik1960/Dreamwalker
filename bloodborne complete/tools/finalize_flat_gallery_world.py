"""Add native navigation to the newly authored gallery, not to an input map."""
from __future__ import annotations
import argparse
import csv
import copy
import hashlib
import json
import math
from pathlib import Path
import re
import shutil
from world_io import read_nbt, write_nbt, compound, Tag, RegionFile

ROOT = Path(__file__).resolve().parents[1]


def dump(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--world', type=Path, required=True)
    parser.add_argument('--manifest', type=Path, required=True)
    args = parser.parse_args()
    world = args.world.resolve()
    assert world.is_relative_to((ROOT/'build').resolve()) and (world/'level.dat').is_file()
    manifest = json.loads(args.manifest.read_text(encoding='utf-8'))
    assert manifest['status'] == 'PASS_AUTHORED_REQUIRES_PRODUCTION_REOPEN'
    manifest['floorY'] = 63
    manifest['worldName'] = 'Dreamwalker-BB-V12.1-Flat-Gallery'
    signs_updated = 0
    for region_path in sorted((world/'region').glob('r.*.*.mca')):
        region = RegionFile.open(region_path)
        changed_region = False
        for chunk in list(region.chunks()):
            nbt = chunk.nbt()
            changed_chunk = False
            for entity_tag in compound(nbt.root).get('block_entities', Tag(9, [])).value:
                entity = compound(entity_tag)
                if entity.get('id', Tag(8, '')).value == 'minecraft:sign' and 'front_text' in entity:
                    entity['back_text'] = copy.deepcopy(entity['front_text'])
                    signs_updated += 1
                    changed_chunk = True
            if changed_chunk:
                region.set_chunk(chunk.x, chunk.z, nbt, timestamp=chunk.timestamp)
                changed_region = True
        if changed_region:
            region.save(region_path)
    assert signs_updated >= len(manifest['exhibits']), signs_updated
    manifest['labelsBothSides'] = True
    manifest['signsWithBothSides'] = signs_updated
    level = read_nbt(world/'level.dat')
    data = compound(compound(level.root)['Data'])
    data['allowCommands'], data['GameType'] = Tag(1,1), Tag(3,1)
    data['LevelName'] = Tag(8,'Dreamwalker BB V12.1 · Галерея')
    data['SpawnX'], data['SpawnY'], data['SpawnZ'] = Tag(3,0), Tag(3,64), Tag(3,-6)
    pack = world/'datapacks/dw_gallery_entry'
    functions = pack/'data/dw_gallery/functions'
    visits = functions/'visit'
    visits.mkdir(parents=True,exist_ok=True)
    dump(pack/'pack.mcmeta', {'pack': {'pack_format':15,'description':'Dreamwalker BB: навигация по отдельной плоской галерее'}})
    dump(pack/'data/minecraft/tags/functions/tick.json', {'values':['dw_gallery:entry']})
    (functions/'entry.mcfunction').write_text('execute as @a[tag=!dw_gallery_entered] run gamemode creative @s\nexecute as @a[tag=!dw_gallery_entered] run tp @s 0.5 64 -5.5 0 0\nexecute as @a[tag=!dw_gallery_entered] run tellraw @s {"text":"Галерея BB: /function dw_gallery:help. Секции: v12, new, absent, error.","color":"gold"}\ntag @a[tag=!dw_gallery_entered] add dw_gallery_entered\n',encoding='utf-8')
    enabled = compound(data['DataPacks'])['Enabled'].value
    if 'file/dw_gallery_entry' not in [tag.value for tag in enabled]:
        enabled.append(Tag(8,'file/dw_gallery_entry'))
    write_nbt(world/'level.dat',level)
    index=[];keys=set();first_section={}
    for number,row in enumerate(manifest['exhibits'],1):
        numeric = row['id'] or f'diagnostic_{number:04d}'
        prefix = 'rp' if row['kind']=='rp_object' else 'error' if row['kind']=='diagnostic' else 'block'
        key = f'{prefix}_{numeric}_v{row["variant"]}'
        if key in keys:key += f'_{number:04d}'
        assert re.fullmatch('[a-z0-9_]+',key) and key not in keys
        keys.add(key)
        x,z,w,d = row['platform']
        bounds=row['bounds'];center=[(bounds[i]+bounds[i+3])/2 for i in range(3)]
        spans=[bounds[i+3]-bounds[i] for i in range(3)]
        distance=max(4,max(spans)*1.2)
        camera=[round(center[0],4),round(center[1]+max(1,max(spans)*.2)-1.62,4),round(center[2]-distance,4),0]
        dy=center[1]-(camera[1]+1.62);dz=center[2]-camera[2]
        camera.append(round(-math.degrees(math.atan2(dy,dz)),4))
        row['visitFunction']='dw_gallery:visit/'+key;row['camera']=camera
        info={'text':f'{row["name"]} | ID {row["id"] or "отсутствует"} | {row["registry"] or "нет законченного объекта мода"} | вариант {row["variant"]} | {row["section"]}', 'color':'gold'}
        lines=['tp @s '+' '.join(map(str,camera)), 'tellraw @s '+json.dumps(info,ensure_ascii=False)]
        if row['sourceIssues']:
            lines.append('tellraw @s '+json.dumps({'text':json.dumps(row['sourceIssues'],ensure_ascii=False),'color':'red'},ensure_ascii=False))
        (visits/(key+'.mcfunction')).write_text('\n'.join(lines)+'\n',encoding='utf-8')
        first_section.setdefault(row['section'],row)
        index.append([row['section'],row['id'],row['name'],row['registry'],row['variant'],row['kind'],row['representation'],*row['root'],'/function '+row['visitFunction']])
    for section,row in first_section.items():
        (functions/(section+'.mcfunction')).write_text('function '+row['visitFunction']+'\n',encoding='utf-8')
    (functions/'help.mcfunction').write_text('tellraw @s '+json.dumps({'text':'Секции: /function dw_gallery:v12, :new, :absent, :error. Каждый предложенный вариант — отдельная платформа. Между краями ровно 2 блока. ID и полные названия — в GALLERY-INDEX.csv. Красные диагностические подиумы без ID не являются выдуманными блоками. /function dw_gallery:tool выдаёт инструмент BB V12.','color':'gold'},ensure_ascii=False)+'\n',encoding='utf-8')
    (functions/'tool.mcfunction').write_text('give @s bloodborne_dw:bb_tool\n',encoding='utf-8')
    with (world/'GALLERY-INDEX.csv').open('w',encoding='utf-8-sig',newline='') as stream:
        writer=csv.writer(stream);writer.writerow(['section','id','name','registry','variant','kind','representation','rootX','rootY','rootZ','visitCommand']);writer.writerows(index)
    dump(args.manifest,manifest);dump(world/'gallery-manifest.json',manifest)
    text=('Dreamwalker BB V12.1 — отдельная плоская галерея\n\n'
          'Minecraft 1.20.1, Fabric Loader 0.16.10, Java 17.\n'
          'Нужны combined JAR Dreamwalker BB 0.1.0-v12.1-resource-transfer, Fabric API 0.92.9+1.20.1 и Geckolib 4.4.9. Исходный ресурспак не нужен. QA author/addon не нужен.\n'
          'Скопируйте папку Dreamwalker-BB-V12.1-Flat-Gallery в saves своего клиента Fabric.\n'
          'Режим Creative, команды включены. /function dw_gallery:help — помощь.\n\n'
          'Голубая секция V12: 80 объектов (11 архитектурных и 69 канонических объектов RP).\n'
          'Зелёная секция новых: 1682 варианта. Жёлтая: 120 вариантов, отсутствующих на исходной карте.\n'
          'Красная: 3 установленных варианта с исходным дефектом particle-текстуры и 57 подписанных диагностических подиумов.\n'
          'Порядок групп: ошибка > отсутствие на карте > V12 > новые. Происхождение сохранено в manifest; каждый реальный объект/предложенный вариант представлен ровно один раз.\n'
          'Сетка: 16 колонок, выровненные ряды; между краями платформ ровно 2 блока. Платформы учитывают габариты моделей и их открытых поз.\n'
          'Галерея показывает BASE и закрытые состояния. Повороты, открывание и ALT можно проверить самим с инструментом BB; произвольный поворот может выйти за выделенную площадку.\n'
          'В диагностических подиумах не подставлено отсутствующее авторское содержимое. Это красный стеклянный маркер и пояснение; /function соответствующей платформы печатает полную причину в чат.\n'
          'Удалённые ID и служебные части не восстановлены. Мобы, оружие и инструменты не являются строительными блоками; BB-инструмент доступен командой /function dw_gallery:tool.\n'
          'Карта v16 и ресурспак v15 не изменялись. Эта галерея создана с нуля отдельно.\n\n'
          'GALLERY-INDEX.csv — полный список и команды перехода; gallery-manifest.json — точные координаты, состояния и UUID.\n'
          'SHA-256 combined JAR: '+manifest['productionJarSha256']+'\n')
    (world/'README-RU.txt').write_text(text,encoding='utf-8')


if __name__ == '__main__':
    main()
