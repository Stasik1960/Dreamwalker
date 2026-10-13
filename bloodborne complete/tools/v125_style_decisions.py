"""Write the complete installed-appearance decision ledger; never edit models."""
from collections import Counter
from pathlib import Path
import hashlib,json,re,traceback
import numpy as np
from v125_style_inventory import ROOT,OUT,BASELINE,VANILLA,read
from v124_visual_audit import Visuals

STAIR_LOOK={
'90322':'Длинные серые прямоугольные кирпичи с глубокими тёмными швами.',
'90423':'Серые гладкие каменные панели с широким тёмным горизонтальным поясом.',
'90437':'Крупные неровные булыжные фрагменты с широкими тёмными трещинами.',
'90493':'Крупный угловой резной узор и рамки на тёмном камне.',
'90523':'Контрастные вертикальные серые каннелюры и полосы.',
'90550':'Повторяющийся овальный каменный орнамент на серой фасадной поверхности.',
'90692':'Приглушённые бурые каменные курсы с мелкими пятнами и швами.',
'90760':'Серые прямоугольные каменные панели с отчётливым рамочным обрамлением.',
'90804':'Тёмный зеленовато-серый камень с широкими швами и панельным рисунком.',
'90862':'Выветренные серо-бурые доски с узким повторяющимся продольным зерном и тёмными краями.',
'93004':'Серые кирпичи с широкими зелёными замшелыми швами.',
'93028':'Светло-серая каменная поверхность с тонкими кантами; вариант с дополнительной задней отделкой.',
'93081':'Крупные квадратные серые камни с широкими тёмными прямоугольными швами.',
'93094':'Плотная мелкая серая каменная крошка; нет крупных редких пятен обычного ванильного андезита.',
}
NOTES={
'90021':'Крупный светло-серый каменный корпус с рамками по краям граней; ширина2.19 и высота2 блока. Это архитектурная капитель/облицовка, а не обычный бетонный куб1×1.',
'90028':'Бурая резная рама: вертикальные рёбра, ступенчатая база, профильные балки и криволинейные подкосы.',
'90038':'Бурый мешок с неровными складками и связанным верхом; силуэт и отделка отличаются от ванильных ящиков/бочек.',
'90044':'Тёмная выветренная бочка с широкими светло-серыми металлическими обручами и тёмным верхом; иной материал и рисунок, чем у обычной ванильной бочки.',
'90046':'Составная группа старых бочек с металлическими обручами; отдельные наклоны и положение сохранены как виды.',
'90052':'Пирамида старых бочек/ящиков с массивными светлыми обручами, непохожая на обычный одиночный Minecraft-блок.',
'90055':'Низкий ящик с красными пузырьками крови и выступающими принадлежностями.',
'90057':'Слой разрозненных старых листов бумаги, книг и мусора; неровный край и бурые страницы.',
'90063':'Составная каменная готическая тумба с резными арочными панелями, угловыми опорами и чёрным открытым верхом.',
'90065':'Отдельные серые каменные обломки и небольшие плитки на полу, разложенные с промежутками.',
'90071':'Разрозненные выветренные серо-бурые доски с видимыми промежутками и перекрещивающимися деталями.',
'90078':'Свечная люстра с тонкими разветвлёнными подвесами и множеством свечей; это авторский светильник, а не одиночная ванильная свеча.',
'90084':'Широкие тёмные BB-звенья цепи: плоскость5px шириной против3px у ванильной цепи, заметно другой рисунок просветов.',
'90086':'Лежащая/наклонная цепь из широких тёмных BB-звеньев; иной рисунок звеньев и ширина, чем у стандартной Minecraft-цепи.',
'90088':'Тонкая неровная напольная деколь из вьющихся сухих растительных/мусорных фрагментов, несмотря на старое название цепи.',
'90094':'Кованая готическая секция ограды с завитками и фигурной верхней кромкой.',
'90100':'Тёмная зеленовато-серая кладка из вытянутых крупных камней с широкими чёрными швами.',
'90120':'Крупная круглая розетка с плотным симметричным каменным орнаментом и рамкой, включая диагональные напольные положения.',
'90133':'Серые пластины, осколки и неровные обломки на полу. Полностью прозрачный исторический вид90141 сохранён внутри группы для совместимости, не выдаётся за видимую модель.',
'90144':'Сухие тёмно-серые разветвлённые стебли с длинными отдельными выступами; иной рисунок просветов, чем у зелёной ванильной травы/папоротника.',
'90145':'Высокие сухие серые стебли с редкими боковыми веточками, а не обычная зелёная трава Minecraft.',
'90146':'Сухой тёмно-серый куст из нескольких расходящихся стеблей; контур не совпадает с vanilla grass/fern.',
'90149':'Сухая редкая серая растительность из нескольких тонких стеблей и коротких ответвлений.',
'90150':'Низкие отдельные сухие тёмные стебли; отсутствует узнаваемая зелёная ванильная листва.',
'90151':'Разреженная высокая сухая BB-растительность с раздельными ветвями и иной альфа-формой.',
'90157':'Тонкий высокий фигурный светильник со светлыми золочёными частями и криволинейной стойкой.',
'90162':'Вытянутый подвесной фонарь с тонкими кронштейнами, орнаментом и крупной белой центральной вставкой.',
'90166':'Серая каменная фигура на резном ступенчатом постаменте, включая наклонённые скульптурные позы.',
'90223':'Каменная крылатая фигура с вытянутым корпусом и раздельными боковыми крыльями.',
'90224':'Крупная серая крылатая фигура с широкими опущенными крыльями и собственным каменным основанием.',
'90226':'Каменная крылатая статуя с массивным корпусом и вытянутыми выступами.',
'90230':'Длинное поваленное выветренное бревно с неровными краями и отдельными выступами коры.',
'90231':'Длинное выветренное бревно со свисающим зелёно-бурым мхом; не обычный ровный log-куб.',
'90232':'Старое бревно с длинными свисающими петлями мха и неровной корой.',
'90236':'Составная резная каменная ограда и надгробные стойки с арочными/стрельчатыми вершинами, орнаментом и ступенчатыми основаниями.',
'90245':'Тонкая каменная стеновая панель с широкой рамкой, орнаментальной центральной вставкой и нижним резным поясом; видна с лицевой стороны на alternate176.',
'90262':'Старая тележка/мебель с тонкими ажурными колёсами/ножками и сложным бурым корпусом; нет силуэта обычной железной Minecraft-вагонетки.',
'90265':'Бурый резной деревянный профиль с глубокими промежутками и несколькими обводами по краям.',
'90279':'Серая каменная перекладина на массивных опорах; в группе также фигурный высокий каменный профиль. Малый обычный тёмно-дубовый участок18.75% не определяет внешний вид всей конструкции.',
'90307':'Серый готический шпиль с резными узлами и вытянутыми острыми частями; старое слово«лёд» не описывает установленную модель.',
'90319':'Серая архитектурная опора/карниз с широким плоским верхом и арочными боковыми подкосами; старое имя«Книги» не используется для стилевого решения.',
'90351':'Куб из буро-серой каменной кладки с тёмными верхними шипами; ванильная травяная верхушка/overlay занимают меньшую долю и не превращают его в обычный зелёный Minecraft-блок.',
'90462':'Высокий серый архитектурный профиль с вертикальной рифлёной отделкой и многополосным кантом; медного цвета в установленном виде нет.',
'90485':'Парные светильники на тонких длинных стойках с ажурными фигурными частями; небольшой vanilla flower_pot участок9.25% не определяет авторский силуэт.',
'90490':'Серо-бурый выветренный материал с выраженным широким верхним/боковым рамочным поясом; это приглушённая BB-отделка, а не яркая стандартная порода досок.',
'90513':'Буро-серая плотная каменная/земляная поверхность с крупным неоднородным рисунком; не стандартная коричневая земля Minecraft.',
'90519':'Крупное сухое разветвлённое растение высотой3 блока: длинные переплетённые бурые ветки, иной масштаб и контур, чем у маленького vanilla dead_bush.',
'90530':'Серая строительная плита с густой мелкой крошкой/зерном по всей поверхности. Зерно заметно плотнее и мельче крупных редких андезитовых пятен у снятых90269/90690.',
'90532':'Серый архитектурный кронштейн с длинной верхней панелью, боковой подпоркой и зубчатой нижней кромкой; при установке не выглядит грядкой, несмотря на название.',
'90556':'Тёмная BB-почва: чёрная верхушка и крупная буро-серая каменная кладка на боках. Доминирующие каменные грани заметно отличаются от обычного коричневого dirt; меньшие vanilla grass-участки не определяют городской финиш.',
'90558':'Приглушённая серая зернистая каменная отделка с орнаментальными/рамочными участками; имя«гравий» не означает неизменённый стандартный gravel.',
'90569':'Крупная авторская карета с ажурными колёсами и сложным кузовом; размер, фигура колёс и декоративный профиль не похожи на обычную ванильную вагонетку.',
'90579':'Длинная скамья с кованой спинкой, завитками и тонкими фигурными ножками.',
'90581':'Раскрытый бурый старый сундук/чемодан с широкими светлыми металлическими кантами, крышкой и обломками; не жёлтый стандартный Minecraft chest.',
'90589':'Серая крылатая фигура на резном постаменте и связанные скульптурные положения.',
'90595':'Вытянутый готический фонарь на фигурном подвесе с завитками и верхним шпилем. Отличается от снятого90941 пропорциями и орнаментальным корпусом.',
'90607':'Низкая серая кирпичная ограда с профильными деталями; установленная модель не является обычным прозрачным Minecraft-льдом.',
'90625':'Настил из отдельных выветренных серо-бурых досок с щелями и небольшими концами опор; нет яркого стандартного plank-материала.',
'90631':'Крупный тёмный готический подвесной фонарь с решётчатым корпусом, фигурным навершием и длинным нижним остриём; малая цветная светящаяся вставка сохраняется. Отличается от компактного стандартного90941.',
'90673':'Крупные переплетённые сухие бурые ветви с угловыми петлями и длинными выступами; не маленький vanilla dead_bush.',
'90687':'Густой пучок сухих бурых стволов/ветвей, заметно шире и выше обычной травы Minecraft.',
'90725':'Голубые остеклённые панели на широком сером резном постаменте; яркая вставка является частью BB-оконной/фонарной конструкции.',
'90744':'Серый профилированный архитектурный корпус с верхней площадкой и резным боковым поясом; обычного синего packed_ice-куба в установленном виде нет.',
'90755':'Крупное густое сплетение сухих бурых стеблей с длинными выступающими ветвями.',
'90841':'Фигурная свечная/фонарная стойка с симметричным светлым навершием, тонкими кронштейнами и тёмным низом; это отдельная BB-композиция.',
'90842':'Тонкая серая архитектурная полосатая пластина/профиль; нет светлого обычного Minecraft end_rod с типичной жёлтой пятой.',
'90869':'Составная сцена с окровавленным столом, креслом и аппаратом с красными сосудами; крупный сложный BB-предмет.',
'90870':'Длинный окровавленный стол на тонких фигурных ножках с неровными свисающими светлыми краями.',
'93098':'Редкие длинные сухие бурые ветви; установленный контур вытянут и разрежен, не похож на стандартный маленький куст Minecraft.',
'93141':'Широкий низкий раскидистый сухой BB-куст с длинными горизонтальными ветвями; другой масштаб и просветы, чем у vanilla dead_bush.',
'93164':'Тёмно-серая металлическая секция: широкие плоские перекладины, большие открытые промежутки и плотные соединительные узлы. Нет обычного цветного стекла из прежнего имени.',
'93166':'Буро-бронзовая/деревянная рамная ограда с массивными горизонтальными поясами и тёмными открытыми промежутками; не синее стекло.',
'93180':'Широкая серая каменная платформа на двух профильных опорах с открытым проходом и многополосными карнизами.',
'93181':'Серая профильная металлическая стойка/ограда с широкими перекладинами и открытыми промежутками; не обычное коричневое стекло.',
'93185':'Тёмные тонкие металлические прутья с утолщениями/завершениями и связанными вариантами ограды; авторский рисунок просветов и металл, а не бирюзовое стекло.',
'93195':'Металлическая BB-ограда на сером каменном цоколе с широкими промежутками, высокими стойками и разными узлами соединений.',
'93201':'Серая металлическая профильная стойка и связанная рамная ограда; старое голубое стеклянное имя не определяет стиль.',
'93203':'Тонкая серая профильная металлическая стойка с широкими поперечными узлами и связанными рамными секциями; не лаймовое стекло.',
'93207':'Сложная тёмно-зеленовато-серая наклонная кровельная/каменная композиция с широкими швами; не обычный маленький vanilla prismarine-куб.',
'93219':'Высокая узкая серая архитектурная стойка с малыми боковыми полосами; в установленном виде нет яркого пурпурного ванильного стекла.',
'93220':'Широкая каменная площадка на трёх резных опорах с открытыми просветами, ступенчатой базой и карнизом.',
}

def sample(image,uv):
    h,w=image.shape[:2];pixels=[]
    for a in (np.arange(8)+.5)/8:
        for b in (np.arange(8)+.5)/8:
            p=(1-a)*(1-b)*uv[0]+(1-a)*b*uv[1]+a*b*uv[2]+a*(1-b)*uv[3]
            pixels.append(image[int(p[1]*h/16)%h,int(p[0]*w/16)%w]/255)
    return np.array(pixels)
def geometry_note(row,visual):
    spec=read(ROOT/f"src/architecture/resources/bloodborne_dw/composite/{row['registryId'].split(':')[1]}.json")
    faces=[q for p in spec['variants'][0]['poses']['closed']['parts'] for q in visual.quads(p)]
    colour=np.zeros(3);total=0.;cutouts=0
    for vertices,uv,texture,tint in faces:
        pixels=sample(visual.image(texture),uv);alpha=pixels[:,3]
        weight=float(np.linalg.norm(np.cross(vertices[1]-vertices[0],vertices[2]-vertices[0]))+np.linalg.norm(np.cross(vertices[2]-vertices[0],vertices[3]-vertices[0])))/2
        colour+=np.sum(pixels[:,:3]*alpha[:,None],axis=0)*weight/len(pixels);total+=float(alpha.mean())*weight
        cutouts+=int(0<alpha.mean()<.97)
    colour=colour/max(total,1e-9)
    state=row['states'][0];b=state['visualBoundsBlocks'];dims=np.array(b[3:])-b[:3]
    x,y,z=dims;size='×'.join(f'{n:.2f}' for n in dims)
    rgb=colour.tolist();sat=float(colour.max()-colour.min());bright=float(colour.mean())
    if sat<.045:tone='тёмно-серый' if bright<.25 else 'светло-серый' if bright>.6 else 'серый'
    elif colour[0]>=colour[1]>=colour[2]:tone='приглушённый серо-бурый' if sat<.19 else 'бурый'
    elif colour[1]>=colour[0] and colour[1]>=colour[2]:tone='зеленовато-серый'
    elif colour[2]>colour[0]:tone='голубовато-серый'
    else:tone='приглушённый каменный'
    if y<.15:silhouette='тонкая напольная поверхность/деталь'
    elif min(x,z)<.15 and y>1:silhouette='высокая узкая плоская архитектурная секция'
    elif y>1.4 and max(x,z)<.75:silhouette='узкий высокий архитектурный профиль'
    elif len(faces)==6 and min(dims)>.1:silhouette='прямоугольный строительный корпус'
    elif cutouts and len(faces)<=12:silhouette='прорезная архитектурная/декоративная секция'
    else:silhouette='составной архитектурный или декоративный профиль'
    return {'silhouette':silhouette,'tone':tone,'dimensionsBlocks':dims.round(5).tolist(),
      'defaultNativeQuadCount':len(faces),'cutoutQuadCount':cutouts,'sampledUntintedRgbaMeanRgb':np.round(colour,5).tolist(),
      'description':f'{silhouette}; {tone} неоднородный рисунок; видимый габарит{size} блока.'}

def retain(row,shape):
    id=row['id']
    if id in STAIR_LOOK:
        return 'Рабочие ступени BB: '+STAIR_LOOK[id]+' Сохраняются по установленному материалу; обычная ступенчатая форма не отменяет видимую городскую отделку.'
    if id in NOTES:return NOTES[id]
    base=shape['description']
    if shape['cutoutQuadCount']:
        return base+' Сохранён прорезной BB-рисунок и профиль в приглушённой городской палитре: форму определяют отдельные рамки/перекладины/декоративные просветы, а не узнаваемая ванильная фурнитура, горшок или зелёное растение.'
    return base+' Сохранена BB-каменная/деревянная городская отделка с видимыми полосами, швами, рамочной фактурой либо плотной выветренной поверхностью. В установленном виде нет яркой стандартной Minecraft-породы или узнаваемого обычного механизма; простой прямоугольный носитель сам по себе не исключает авторский строительный материал.'

def main():
    inventory=read(OUT/'style-inventory.json');near=read(OUT/'style-near-surfaces.json');index=read(OUT/'style-native-index.json')['rows']
    metadata_path=ROOT/'src/architecture/resources/bloodborne_dw/imported-style-retirements.json';metadata=read(metadata_path)
    removals={r['numericId']:r for r in metadata['retirements']};assert len(removals)==21
    capture_path=ROOT/'build/runtime-client-v124-comparison-final-2/v124-client-comparison-output.json';capture=read(capture_path)
    screenshots=capture_path.parent/'screenshots';checked=set()
    for evidence in index.values():
        if evidence['fileName'] not in checked:
            assert hashlib.sha256((screenshots/evidence['fileName']).read_bytes()).hexdigest()==evidence['sha256']
            checked.add(evidence['fileName'])
    # A default back-face cannot supply an observed style claim for90245.
    alternates=[]
    for frame in capture['frames']:
        for position,pair in enumerate(frame['pairs']):
            if pair.get('kind')=='SOURCE_CATALOGUE_ALT_VIEW' and pair['oldId']=='90245':
                alternates.append({'fileName':frame['fileName'],'sha256':frame['sha256'],'row':position//4+1,'column':position%4+1,
                  'productionJarSha256':capture['productionJarSha256'],'kind':'SOURCE_CATALOGUE_ALT_VIEW'})
    assert len(alternates)==1
    visual=Visuals(vanilla_jar=VANILLA);near_by={r['id']:r for r in near['rows']};rows=[]
    for original in inventory['rows']:
        id=original['id'];shape=geometry_note(original,visual);native=dict(index[id]);native.pop('pair')
        if id=='90245':native['alternateVisibleFace']=alternates[0]
        retired=id in removals;reason=removals[id]['reason'] if retired else retain(original,shape)
        rows.append({'numericId':id,'registryId':original['registryId'],'catalogueLabelForReferenceOnly':original['name'],
          'decision':'RETIRE_CREATIVE_ONLY_PRESERVE_SAVES' if retired else 'KEEP_BB_CITY_CONTENT',
          'installedAppearanceAndReason':reason,'geometryObservation':shape,'nativeEvidence':native,
          'maxVanillaVisibleSurfaceFraction':original['maxVanillaSurfaceFraction'],
          'defaultVanillaVisibleSurfaceFraction':original['defaultVanillaSurfaceFraction'],
          'nearestSurfaceDiagnosticOnly':{'weightedPixelMae':near_by[id]['weightedNearestVanillaPixelMae'],
            'closestVanillaSurfaces':[r['closestVanillaTexture'] for r in near_by[id]['faces'][:2]]},
          'sourceMaterialEvidence':'style-inventory.json row '+id,'aliases':removals[id]['aliases'] if retired else [],
          'status':'PASS_INSTALLED_STYLE_DECISION_WITH_NATIVE_PIXELS'})
    assert len(rows)==572 and len({r['registryId'] for r in rows})==572
    retired_registrations={r for row in metadata['retirements'] for r in row['aliases']}
    result={'schema':'dreamwalker-v125-complete-installed-style-decisions-v1','baselineCommit':BASELINE,
      'status':'PASS_COMPLETE_572_TYPES','workingTypesBefore':572,'retiredWorkingTypes':21,'workingTypesAfter':551,
      'hiddenRegisteredMainAndAliases':len(retired_registrations),'protectedOriginalV12Rows':116,
      'nativeProductionJarSha256':capture['productionJarSha256'],'nativeCaptureSha256':hashlib.sha256(capture_path.read_bytes()).hexdigest(),
      'retirementMetadataSha256':hashlib.sha256(metadata_path.read_bytes()).hexdigest(),
      'sourceResourcePackSha256':inventory['sourceResourcePackSha256'],'vanillaJarSha256':inventory['vanillaJarSha256'],
      'method':'All572 NEW working types, including all all-state/base/alt source surface inventory and preserved legacy stair art. Sourcev15ZIP and decodedvanilla1.20.1 pixels are evidence; actual original native models decide style. Exact paths/names, carrier classes, small numeric image distances, and an affected flag never decide removal. All default native source panels are bound to actual raw PNG SHA;90245uses its visible front alternate. Candidate review also includes textured UV alpha silhouette comparison, covering copied atlas regions and gray/noise false positives.',
      'confirmedRetainedNearComparisons':[
        {'ids':['90556'],'reason':NOTES['90556']},
        {'ids':['90862','90625','90071'],'reason':'Muted, visibly weathered BB board grain and separate/broken board profiles; no unchanged bright standard Minecraft wood species.'},
        {'ids':['90084','90086'],'reason':NOTES['90084'],'diagnostic':'style-cutout-silhouettes.json'},
        {'ids':['90144','90145','90146','90149','90150','90151','90519','90673','90755','93141'],'reason':'Dry gray/brown long sparse/irregular branches, distinct UV-alpha silhouette and size from vanilla grass,fern,dead_bush.','diagnostic':'style-cutout-silhouettes.json'},
        {'ids':['90595','90631','90485'],'reason':'Long/ornate BB lamp body and shaped brackets; minor glow or pot input does not dominate their silhouette, unlike compact90941.'},
        {'ids':['90279'],'reason':NOTES['90279']},
        {'ids':['90530','93094'],'reason':'Dense fine authored stone aggregate, visibly smaller/more closely packed than coarse sparse vanilla-like andesite patches in removed90269/90690.'},
        {'ids':['90532'],'reason':NOTES['90532']},
        {'ids':['90307','90607','90744','90319'],'reason':'Installed sculpted spire/wall/cornice; misleading vanilla carrier label ice/books is ignored.'},
      ],'runtimeV125CreativeVerification':'NOT_RUN_HERE_ROOT_PERFORMS_FINAL_COMBINED_JAR_AND_CLIENT_TESTS','rows':rows}
    (OUT/'style-decisions.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    lines=['V12.5 — полный аудит установленного вида572 новых рабочих типов.',
      'Результат:21 скрытый тип;551 оставшийся рабочий NEW тип. Исходная V12:116 строк защищены по точному registry ID.',
      'Старые регистрации, номера и сохранённые экземпляры всех21 групп сохранены; Creative/Remaining стеки этих основных типов и aliases скрыты.',
      'Названия каталога оставлены для поиска. Решение основано на native-виде модели и материала; прежнее имя/носитель/путь не является аргументом.',
      'Сравнение: исходныйv15ZIP, декодированныеvanilla1.20.1RGBA, все source состояния/профили, настоящие native PNG V12.4. '+capture['productionJarSha256'],
      'Текущий V12.5 build/client QA выполняет root; здесь новый запуск клиента НЕ заявляется.',
      '', 'ПОЛНЫЙ ПЕРЕЧЕНЬ: ID | действие | видимое основание | native кадр/ячейка | SHA256', '']
    for row in rows:
        native=row['nativeEvidence'].get('alternateVisibleFace',row['nativeEvidence'])
        lines.append(row['numericId']+' | '+('УБРАТЬ ИЗ CREATIVE' if row['decision'].startswith('RETIRE') else 'СОХРАНИТЬ')+' | '+
          row['installedAppearanceAndReason']+' | '+native['fileName']+f" r{native['row']}c{native['column']} | "+native['sha256'])
    (OUT/'style-audit-RU.txt').write_text('\n'.join(lines)+'\n',encoding='utf-8')
if __name__=='__main__':
    try:main()
    except Exception:
        (OUT/'style-decisions-error.txt').write_text(traceback.format_exc(),encoding='utf-8');raise
