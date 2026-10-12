"""Name and navigation for the newly authored compact gallery only."""
import argparse,csv,json,math,re
from pathlib import Path
from world_io import read_nbt,write_nbt,compound,Tag
ROOT=Path(__file__).resolve().parents[1]
def dump(p,d):p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(d,ensure_ascii=False,indent=2)+'\n',encoding='utf8',newline='\n')
def readme_text(m, navigation):
    """Describe the authored world using its manifest, not planning-era counts."""
    summary=m['catalogue']['summary'];city=m['city'];reviews=m['reviewPlatforms']
    assemblies=sum(row['section']=='assemblies' for row in reviews)
    doors=sum(row['section']=='doors' for row in reviews)
    owners=len(city['placedOwners']);services=len(city['sourceInvisibleServices'])
    exceptions=city['sourceExceptions']
    style_omissions=sum(row['status']=='EXPLICIT_V126_STYLE_RETIREMENT_SOURCE_CELL_OMITTED_NOT_RESTORED' for row in exceptions)
    source_omissions=len(exceptions)-style_omissions
    bounds=city['rawBlocks']['boundsInclusive'];size=city['rawBlocks']['dimensions']
    coordinate=lambda values:'['+', '.join(map(str,values))+']'
    surface_tiles=[row for row in m['exhibits'] if row.get('surfaceTileReplacesPlatformCell')]
    paragraphs=[
        'Dreamwalker BB V12.6 — компактная галерея и фрагмент города',
        'Minecraft 1.20.1, Fabric Loader 0.16.10, Java 17, Fabric API 0.92.9+1.20.1, GeckoLib 4.4.9. Установите combined JAR V12.6 вместо предыдущего BB JAR. Исходный ресурспак и QA addon не нужны. Папку мира поместите в saves. /function dw_gallery:help — навигация.',
        navigation,
        f"Основных платформ: {len(m['exhibits'])}. Рабочих новых типов: {summary['newWorkingTypes']}. Из рабочего каталога убрано {len(m['styleRetirements'])} практически ванильных семейств; старые регистрации сохранены для совместимости, номера не переиспользуются. Готовые блоки оригинального V12 не изменены. Обводка новых непроходимых объектов совпадает с упрощённой физикой; проходимые декорации имеют одну простую рамку для выбора. Это базовые простые коллизии: авторский экспорт не предоставлен и не применялся.",
        f'{assemblies} составных раскладок и {doors} кандидатов в двери показывают реальные исходные детали и соседство либо законченные неиспользованные модели. Предметы пока остаются отдельными; сборки предназначены для оценки. Новые свойства дверям не добавлены.',
        f'Город: исходные координаты {coordinate(bounds[:3])} .. {coordinate(bounds[3:])}, размер {" × ".join(map(str,size))}. Размещено {owners} модовых корней. Оригинальная карта читается и не изменяется; в новом мире копируется выбранный участок без NPC. Корни сохраняют исходные относительные координаты, повороты и SourceShift. Начальные пересечения разрешены однократно при конвертации; обычная повторная установка после удаления подчиняется запретам пересечения коллизий. {services} служебных невидимых исходных ячеек заменены воздухом после проверки прозрачности. Не восстановлены {style_omissions} ячеек удалённых ванильных импортов и {source_omissions} ячеек с исходным дефектом или ранее намеренно удалённым объектом/вариантом — всего {len(exceptions)} явно учтённых пропусков. Точные координаты и основания указаны в city.sourceExceptions файла gallery-manifest.json. Границы фрагмента обрезают продолжение зданий.',
    ]
    if surface_tiles:
        ids=', '.join(str(row['id']) for row in surface_tiles)
        paragraphs.append(f'Особый способ показа применяется только к {len(surface_tiles)} новым типам грунта/покрытия (ID: {ids}). Их исходная модель рисует только верхнюю грань полного физического куба. Такой куб занимает центральную клетку самой платформы на Y=63, а видимая верхняя грань лежит на Y=64. Маркер surfaceTileReplacesPlatformCell в gallery-manifest.json фиксирует этот способ размещения. Модель и физика сохранены; исключение не применяется к оригинальному V12. После удаления куб можно установить обычным способом, нажав на землю под освободившейся клеткой. Для этого показа разрешение пересечения коллизий не используется.')
    return '\n\n'.join(paragraphs)+'\n'
def main():
    p=argparse.ArgumentParser();p.add_argument('--world',type=Path,required=True);p.add_argument('--manifest',type=Path,required=True);p.add_argument('--capture-input',type=Path,action='append',default=[]);a=p.parse_args();world=a.world.resolve()
    assert world.is_relative_to((ROOT/'build').resolve()) and (world/'level.dat').is_file()
    m=json.loads(a.manifest.read_text(encoding='utf-8-sig'));assert m['schema']=='dreamwalker-flat-gallery-v126'
    level=read_nbt(world/'level.dat');data=compound(compound(level.root)['Data']);data['LevelName']=Tag(8,'Dreamwalker BB V12.6 · Галерея и город');data['allowCommands']=Tag(1,1);data['GameType']=Tag(3,1)
    pack=world/'datapacks/dw_gallery_entry';fun=pack/'data/dw_gallery/functions';visits=fun/'visit';visits.mkdir(parents=True,exist_ok=True)
    dump(pack/'pack.mcmeta',{'pack':{'pack_format':15,'description':'Dreamwalker BB V12.6: компактная галерея, сборки, двери, город'}});dump(pack/'data/minecraft/tags/functions/tick.json',{'values':['dw_gallery:entry']})
    entry='execute as @a[tag=!dw_gallery_v126_entered] run gamemode creative @s\nexecute as @a[tag=!dw_gallery_v126_entered] run tp @s 0.5 64 -5.5 0 0\nexecute as @a[tag=!dw_gallery_v126_entered] run tellraw @s {"text":"BB V12.6: /function dw_gallery:help. Окна: :assemblies, двери: :doors, город: :city.","color":"gold"}\ntag @a[tag=!dw_gallery_v126_entered] add dw_gallery_v126_entered\n'
    (fun/'entry.mcfunction').write_text(entry,encoding='utf8',newline='\n')
    enabled=compound(data['DataPacks'])['Enabled'].value
    if 'file/dw_gallery_entry' not in [t.value for t in enabled]:enabled.append(Tag(8,'file/dw_gallery_entry'))
    write_nbt(world/'level.dat',level);m['worldName']='Dreamwalker-BB-V12.6-Compact-Gallery';m['floorY']=63
    captured={}
    for file in a.capture_input:
        capture=json.loads(file.read_text(encoding='utf8'));assert capture['productionJarSha256']==m['productionJarSha256']
        for frame in capture['frames']:captured[frame['key']]=frame['camera']
    allrows=m['exhibits']+m['reviewPlatforms']+[m['city']];first={};index=[]
    for n,row in enumerate(allrows,1):
        section=row['section'];name=row.get('name',row.get('russianLabel','Фрагмент исходного города'));identifier=row.get('id','');key=re.sub('[^a-z0-9_]','_',str(identifier or row['key']).lower())+f'_{n:04d}'
        b=row['bounds'];center=[(b[i]+b[i+3])/2 for i in range(3)];span=max(b[i+3]-b[i] for i in range(3));distance=max(4,span*1.1)
        view=[round(center[0],5),round(max(64.01,center[1]+max(1,span*.25)-1.62),5),round(center[2]-distance,5),0];view.append(round(-math.degrees(math.atan2(center[1]-view[1]-1.62,center[2]-view[2])),5))
        view=captured.get(row['key'],view)
        row['visitFunction']='dw_gallery:visit/'+key;row['camera']=view
        (visits/(key+'.mcfunction')).write_text('tp @s '+' '.join(map(str,view))+'\ntellraw @s '+json.dumps({'text':name+' | '+str(identifier)+' | '+section,'color':'gold'},ensure_ascii=False)+'\n',encoding='utf8',newline='\n')
        first.setdefault(section,row);index.append([section,identifier,name,row.get('registry',''),row['key'],row['platform'],row.get('root',''),'/function '+row['visitFunction']])
    for section,row in first.items():(fun/(section+'.mcfunction')).write_text('function '+row['visitFunction']+'\n',encoding='utf8',newline='\n')
    text='Секции: /function dw_gallery:v12, :new, :absent, :items, :assemblies, :doors, :city. Голубые: V12, зелёные: новые, жёлтые: нет на карте, синие: предметы, розовые: составные образцы, фиолетовые: кандидаты в двери, серые: город. Сборки ещё не объединены; дверям новые свойства не добавлены. Размер платформ: визуальная модель + 2 клетки; промежутки между платформами — 2 блока. /function dw_gallery:tool выдаёт инструмент BB. Полный список — GALLERY-INDEX.csv.'
    (fun/'help.mcfunction').write_text('tellraw @s '+json.dumps({'text':text,'color':'gold'},ensure_ascii=False)+'\n',encoding='utf8',newline='\n');(fun/'tool.mcfunction').write_text('give @s bloodborne_dw:bb_tool\n',encoding='utf8',newline='\n')
    with (world/'GALLERY-INDEX.csv').open('w',encoding='utf-8-sig',newline='') as f:
        writer=csv.writer(f);writer.writerow(['section','id','name','registry','key','platform','root','visitCommand']);writer.writerows(index)
    dump(a.manifest,m);dump(world/'gallery-manifest.json',m)
    (world/'README-RU.txt').write_text(readme_text(m,text),encoding='utf-8-sig');print(json.dumps({'status':'PASS','mainPlatforms':len(m['exhibits']),'reviewPlatforms':len(m['reviewPlatforms']),'cityOwners':len(m['city']['placedOwners']),'footprint':[m['widthBlocks'],m['depthBlocks']]}))
if __name__=='__main__':main()
