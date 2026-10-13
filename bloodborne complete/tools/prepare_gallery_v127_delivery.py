"""Copy the tested new gallery into a clean delivery save, without QA players."""
import argparse, hashlib, json, shutil
from pathlib import Path
from package_v127_release import ordinary, files, sha
from world_io import read_nbt,write_nbt,compound,Tag
ROOT=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser();p.add_argument('--world',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args()
source=a.world.resolve();target=a.output.resolve()
assert source.is_relative_to(ROOT/'build') and target.is_relative_to(ROOT/'build') and not target.exists() and ordinary(source) and ordinary(target)
manifest=json.loads((source/'gallery-manifest.json').read_text(encoding='utf8'));assert manifest['schema']=='dreamwalker-flat-gallery-v127'
target.mkdir(parents=True);rows=[]
for path in files(source):
    relative=path.relative_to(source)
    if path.name=='session.lock' or relative.parts[0] in ('playerdata','stats','advancements'):continue
    destination=target/relative;destination.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(path,destination);assert sha(path)==sha(destination)
    rows.append({'path':relative.as_posix(),'sha256':sha(path)})
level=read_nbt(target/'level.dat');data=compound(level.root)['Data'].value
data.pop('Player',None);data['GameType']=Tag(3,1);data['allowCommands']=Tag(1,1)
for key,value in (('SpawnX',0),('SpawnY',64),('SpawnZ',-6)):data[key]=Tag(3,value)
data['LevelName']=Tag(8,'Dreamwalker BB V12.7 — актуальная галерея');write_nbt(target/'level.dat',level)
functions=target/'datapacks/dw_gallery_entry/data/dw_gallery/functions'
utility=next(r for r in manifest['exhibits'] if r['id']=='93230')
light=next(r for r in manifest['exhibits'] if r['id']=='93231')
for name,row in (('barrier',utility),('light',light)):
    (functions/(name+'.mcfunction')).write_text('function '+row['visitFunction']+'\ngive @s '+row['registry']+'\n',encoding='utf8')
(functions/'day.mcfunction').write_text('time set 6000\n',encoding='utf8')
(functions/'night.mcfunction').write_text('time set 13000\n',encoding='utf8')
(functions/'worldedit.mcfunction').write_text('tp @s -40.5 64 -40.5 0 0\ngive @s minecraft:wooden_axe\ntellraw @s {"text":"Площадка WorldEdit: экспонаты находятся отдельно. Пример: //set 93230; //replace 93230 93231; //undo.","color":"gold"}\n',encoding='utf8')
helpfile=functions/'help.mcfunction';helpfile.write_text(helpfile.read_text(encoding='utf8')+'tellraw @s {"text":"Служебные блоки: /function dw_gallery:utility; :barrier и :light также выдают предмет. Свет: :night / :day. Тестовая площадка: :worldedit.","color":"gold"}\n',encoding='utf8')
readme=target/'README-RU.txt';readme.write_text(readme.read_text(encoding='utf-8-sig')+'\nАктуальная галерея для финального JAR V12.7, SHA-256 b98c752b797ef07c5acf1d4852bda83eec52717fed798d4ec76860b4f6972850.\n644 основные платформы: 621 размещаемый предмет и 23 предмета в рамках. 22 дополнительных образца сборок/дверей; рядом проверенный городской фрагмент.\nDW-барьер 93230 и DW-свет 93231: невидимы без предмета в руке, рядом размещены их значки в рамках. /function dw_gallery:barrier или :light выдаёт предмет и переносит к образцу. :night / :day показывает освещение.\nWorldEdit 7.2.15 проверяется отдельно, не нужен для открытия мира. QA-мод, исходный ресурспак и моды пользовательской сборки не требуются.\n',encoding='utf-8-sig')
assert all(sha(source/r['path'])==r['sha256'] for r in rows),'Tested source changed'
record={'status':'PASS_DELIVERY_COPY_PREPARED_REQUIRES_FINAL_REOPEN','source':str(source),'world':str(target),'sourceUnchanged':True,'qaPlayersOmitted':True,'embeddedPlayerRemoved':True,'modObjectsModified':False,'copiedFiles':len(rows),'productionJarSha256':manifest['productionJarSha256']}
(target.parent/'delivery-preparation.json').write_text(json.dumps(record,indent=2)+'\n',encoding='utf8');print(json.dumps(record))
