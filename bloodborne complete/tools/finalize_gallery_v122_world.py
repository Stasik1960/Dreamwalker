"""Native navigation/index in a fresh V12.2 gallery, with no source-map writes."""
from __future__ import annotations
import argparse
import csv
import json
import math
from pathlib import Path
import re
from world_io import read_nbt,write_nbt,compound,Tag

ROOT=Path(__file__).resolve().parents[1]

def dump(path,value):
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n',encoding='utf8')

def main():
    p=argparse.ArgumentParser();p.add_argument('--world',type=Path,required=True);p.add_argument('--manifest',type=Path,required=True);a=p.parse_args()
    world=a.world.resolve();assert world.is_relative_to((ROOT/'build').resolve()) and (world/'level.dat').is_file()
    manifest=json.loads(a.manifest.read_text(encoding='utf8'));assert manifest['schema']=='dreamwalker-flat-gallery-v122' and manifest['status']=='PASS_AUTHORED_REQUIRES_PRODUCTION_REOPEN'
    rows=manifest['exhibits'];assert not any(row['section']=='error' for row in rows)
    manifest['floorY']=63;manifest['worldName']='Dreamwalker-BB-V12.2-Flat-Gallery'
    level=read_nbt(world/'level.dat');data=compound(compound(level.root)['Data'])
    data['allowCommands'],data['GameType']=Tag(1,1),Tag(3,1);data['LevelName']=Tag(8,'Dreamwalker BB V12.2 · Галерея')
    data['SpawnX'],data['SpawnY'],data['SpawnZ']=Tag(3,0),Tag(3,64),Tag(3,-6)
    pack=world/'datapacks/dw_gallery_entry';functions=pack/'data/dw_gallery/functions';visits=functions/'visit';visits.mkdir(parents=True,exist_ok=True)
    dump(pack/'pack.mcmeta',{'pack':{'pack_format':15,'description':'Dreamwalker BB V12.2: отдельная плоская тестовая галерея'}})
    dump(pack/'data/minecraft/tags/functions/tick.json',{'values':['dw_gallery:entry']})
    (functions/'entry.mcfunction').write_text('execute as @a[tag=!dw_gallery_v122_entered] run gamemode creative @s\nexecute as @a[tag=!dw_gallery_v122_entered] run tp @s 0.5 64 -5.5 0 0\nexecute as @a[tag=!dw_gallery_v122_entered] run tellraw @s {"text":"Галерея BB V12.2: /function dw_gallery:help. Секции: v12, new, absent, items.","color":"gold"}\ntag @a[tag=!dw_gallery_v122_entered] add dw_gallery_v122_entered\n',encoding='utf8')
    enabled=compound(data['DataPacks'])['Enabled'].value
    if 'file/dw_gallery_entry' not in [tag.value for tag in enabled]:enabled.append(Tag(8,'file/dw_gallery_entry'))
    write_nbt(world/'level.dat',level)
    first={};keys=set();index=[]
    for number,row in enumerate(rows,1):
        prefix='item' if row['kind']=='item_frame' else 'rp' if row['kind']=='rp_object' else 'block'
        numeric=row['id'] or 'unassigned_'+re.sub('[^a-z0-9_]','_',row['item'].split(':')[1])
        key=f'{prefix}_{numeric}_v{row["variant"]}'
        if key in keys:key+=f'_{number:04d}'
        assert re.fullmatch('[a-z0-9_]+',key);keys.add(key)
        bounds=row['bounds'];center=[(bounds[i]+bounds[i+3])/2 for i in range(3)];span=max(bounds[i+3]-bounds[i] for i in range(3));distance=max(4,span*1.2)
        camera=[round(center[0],5),round(max(64.01,center[1]+max(1,span*.2)-1.62),5),round(center[2]-distance,5),0]
        camera.append(round(-math.degrees(math.atan2(center[1]-(camera[1]+1.62),center[2]-camera[2])),5))
        row['visitFunction']='dw_gallery:visit/'+key;row['camera']=camera
        info={'text':f'{row["name"]} | ID {row["id"] or "не назначен"} | {row["registry"]} | вариант {row["variant"]} | {row["section"]}', 'color':'gold'}
        (visits/(key+'.mcfunction')).write_text('tp @s '+' '.join(map(str,camera))+'\ntellraw @s '+json.dumps(info,ensure_ascii=False)+'\n',encoding='utf8')
        first.setdefault(row['section'],row)
        index.append([row['section'],row['id'],row['name'],row['registry'],row['variant'],row['kind'],row['representation'],row['gridId'],*row['root'],'/function '+row['visitFunction']])
    for section,row in first.items():(functions/(section+'.mcfunction')).write_text('function '+row['visitFunction']+'\n',encoding='utf8')
    help_text='Секции: /function dw_gallery:v12, :new, :absent, :items. Платформы: визуальный размер модели с округлением до клеток +2 клетки, между краями 2 блока. Объекты стоят на поверхности. Предметы V12 находятся в рамках; яйца не создают мобов. Повреждённых красных объектов нет. Полный список — GALLERY-INDEX.csv. /function dw_gallery:tool выдаёт инструмент BB.'
    (functions/'help.mcfunction').write_text('tellraw @s '+json.dumps({'text':help_text,'color':'gold'},ensure_ascii=False)+'\n',encoding='utf8')
    (functions/'tool.mcfunction').write_text('give @s bloodborne_dw:bb_tool\n',encoding='utf8')
    with (world/'GALLERY-INDEX.csv').open('w',encoding='utf-8-sig',newline='')as f:
        writer=csv.writer(f);writer.writerow(['section','id','name','registry','variant','kind','representation','gridId','rootX','rootY','rootZ','visitCommand']);writer.writerows(index)
    dump(a.manifest,manifest);dump(world/'gallery-manifest.json',manifest)
    counts={section:sum(row['section']==section for row in rows) for section in first}
    text=('Dreamwalker BB V12.2 — отдельная плоская тестовая галерея\n\n'
          'Minecraft 1.20.1, Fabric Loader 0.16.10, Java 17; Fabric API 0.92.9+1.20.1 и GeckoLib 4.4.9.\n'
          'Используйте новый combined JAR V12.2 вместо предыдущего BB JAR. Исходный ресурспак и QA author/addon не нужны.\n'
          'Скопируйте папку Dreamwalker-BB-V12.2-Flat-Gallery в saves. Creative, команды включены.\n'
          '/function dw_gallery:help — навигация.\n\n'
          'Секции: голубые платформы — V12, зелёные — новые, жёлтые — отсутствующие на исходной карте, синие — предметы V12 в рамках.\n'
          'Количество: '+json.dumps(counts,ensure_ascii=False)+'\n'
          'Каждый предлагаемый строительный вариант — на отдельной платформе. Все остальные реально предлагаемые Creative предметы — в рамках, без создания мобов из яиц.\n'
          'Размер платформ: ceil(ширина/длина текущей визуальной модели)+2 клетки. Группы одинакового размера образуют сетки до 16 колонок, промежутки ровно 2 блока.\n'
          'Нижняя грань фактической видимой геометрии строительного объекта совпадает с Y=64 поверхности платформы. Сохранены штатные VerticalOffset/SourceShift/Pos, геометрия V12 не изменялась.\n'
          'Исключение: 43 исходные плоскости нулевой толщины лежат на поверхности с техническим подъёмом 1/1024 блока от z-fighting. Подъём сохранён только в новом мире, исходная модель и физика не меняются; точный список — planarExhibits в manifest.\n'
          'Архитектура показана в BASE и закрытых состояниях. Старые RP-объекты сохраняют свои начальные состояния (лестница RP изначально выдвинута). При произвольном повороте, открытии или ALT объект может выйти за платформу текущей позы.\n'
          'Все красные повреждённые/диагностические станции предыдущей галереи исключены. Исходная v16 и прежняя галерея не изменялись.\n'
          'GALLERY-INDEX.csv — полный список и команды перехода, gallery-manifest.json — визуальные габариты/состояния/UUID/размещение.\n\n'
          'SHA-256 combined JAR: '+manifest['productionJarSha256']+'\n')
    (world/'README-RU.txt').write_text(text,encoding='utf8')

if __name__=='__main__':main()
