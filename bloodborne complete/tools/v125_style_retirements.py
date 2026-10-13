"""Generate reviewed NEW-only style retirement metadata; no model/collision edits."""
from pathlib import Path
import hashlib,json
from v125_style_inventory import ROOT,OUT,BASELINE,read
RES=ROOT/'src/architecture/resources/bloodborne_dw'
REASONS={
'90269':'Практически обычный зернистый серый камень Minecraft: куб и простые объёмные позы с серыми пятнами, без заметной готической резьбы или авторского силуэта. Небольшая переработка andesite-текстуры в v15 не меняет установленный вид обычного андезитового строительного блока.',
'90270':'Практически обычные серые андезитовые кубы, плиты и простые стеновые соединения Minecraft. Доминирует зернистая сероватая поверхность без заметной BB резьбы; варианты положения и простых соединений не меняют ванильную стилистику всей группы.',
'90275':'Обычная жёлтая бамбуковая мозаичная лестница Minecraft: стандартный ступенчатый корпус и неизменённый клетчатый бамбук на всей поверхности.',
'90276':'Обычная жёлтая бамбуковая лестница Minecraft: стандартные ступени и неизменённые параллельные бамбуковые доски, без готической отделки.',
'90278':'Узнаваемый обычный маяк Minecraft: яркий бирюзовый светящийся куб и стандартный силуэт. Замена нижнего обсидианового основания серой отделкой не меняет доминирующий вид маяка.',
'90409':'Обычные розовые вишнёвые ступени Minecraft: вся поверхность использует неизменённые cherry_planks; стандартный ступенчатый силуэт выбивается из серой городской архитектуры.',
'90533':'Узнаваемая обычная грядка Minecraft: низкий прямоугольный корпус и типичная коричневая влажная поверхность с параллельными бороздами. BB-каменная отделка боков не меняет основной вид обычной сельскохозяйственной грядки.',
'90557':'Обычный куб снежной травы Minecraft: белая полоса со светло-голубой кромкой и коричневая земля на большей части боков. Небольшая замена верхней/нижней поверхности не меняет узнаваемый снежный блок.',
'90577':'Узнаваемое ванильное точило Minecraft: крупный круглый каменный механизм, стандартные боковины/ось и деревянные подпорки. Изменение материала подпорок не меняет вид обычного точила.',
'90637':'Узнаваемый ванильный рычаг Minecraft с той же короткой деревянной рукоятью и малым прямоугольным каменным основанием. Другой рисунок основания и способы монтажа не создают готический городской объект.',
'90686':'Обычные красноватые мангровые ступени Minecraft: стандартная форма и неизменённые mangrove_planks на всех гранях.',
'90690':'Практически обычный серый пятнистый куб и плита Minecraft: редкие крупные серые пятна на ровных гранях, без мха и заметной BB резьбы в установленном виде. Название замшелого носителя не соответствует внешности; авторская замена bitmap сохраняет обычную ванильную стилистику простого камня.',
'90701':'Обычные ступени из грязевого кирпича Minecraft: стандартная геометрия и неизменённый крупный коричнево-оранжевый рисунок mud_bricks на всей поверхности.',
'90784':'Узнаваемая обычная азалия Minecraft в горшке: кубическая ярко-зелёная листва и стандартный терракотовый горшок; небольшие светящиеся отметки не меняют основной вид. Цветущий вариант относится к тому же типу.',
'90785':'Узнаваемый обычный бамбук Minecraft в стандартном горшке: сегментированный зелёный стебель с одиночным листом, обычный терракотовый корпус; небольшое свечение корпуса недостаточно для отдельного BB-объекта.',
'90786':'Обычный горшок с папоротником Minecraft по стандартной форме листьев и корпуса, даже при затемнённом окрашивании листьев. Не является самостоятельным каменным крестом; небольшое свечение горшка не меняет узнаваемый ванильный предмет.',
'90788':'Обычный мангровый росток Minecraft в стандартном горшке: типичные широкие зелёные листья и терракотовый корпус. Добавленные светящиеся отметки не меняют доминирующий ванильный вид.',
'90941':'Практически обычный фонарь Minecraft: неизменённые компактные пропорции квадратного корпуса, ступенчатой крышки и верхней ручки. Ровные белые окна и тёмно-серая рамка заменяют цвет, но не добавляют BB орнамента или вытянутой готической формы; узнаваемый стандартный силуэт сохраняется.',
'93112':'Обычный куб с узнаваемым ярким бирюзово-пурпурным торцом искажённого стебля Minecraft. Хотя боковые грани заменены серыми полосами, типичный цветной рисунок торца остаётся главным отличительным признаком и выбивается из городской палитры.',
'93121':'Практически обычный серый андезитовый куб Minecraft: зернистые пятна на всех гранях, без зелёной медной облицовки или заметного готического декора в установленном виде. Название и исходный медный носитель не соответствуют видимой модели и не служат основанием её сохранять.',
'93205':'Практически обычная серая пятнистая каменная стена Minecraft: простой пост и прямые/угловые стеновые соединения с той же крупнопятнистой поверхностью, что у90690. Заметной рамки или готической резьбы в native-модели нет; высота поста и форма соединения не меняют узнаваемый ванильный строительный вид.',
}
def main():
    inventory=read(OUT/'style-inventory.json');catalogue=read(RES/'debug_catalogue.json');types={r['id']:r for r in inventory['rows']}
    aliases=read(RES/'imported-type-aliases.json');groups={g['primaryRegistryId']:g['members'] for g in aliases['groups']}
    protected=read(ROOT/'docs/v12.4-dedup/baseline-content.json')['originalRows'];original={r['registryId'] for r in protected}
    capture_path=ROOT/'build/runtime-client-v124-comparison-final-2/v124-client-comparison-output.json';capture=read(capture_path)
    native={}
    for frame in capture['frames']:
        for row in frame['pairs']:
            if row['oldVariant']==0 and row.get('kind') in (None,'SOURCE_CATALOGUE_REVIEW'):
                native.setdefault(row['oldId'],{'fileName':frame['fileName'],'sha256':frame['sha256'],'kind':row.get('kind','MIGRATION'),'oldVariant':0,
                  'productionJarSha256':capture['productionJarSha256']})
    retirements=[]
    for id,reason in sorted(REASONS.items()):
        row=types[id];registry=row['registryId'];members=groups.get(registry,[registry])
        assert registry.startswith('bloodborne_dw:source_') and not original.intersection(members)
        retirements.append({'registryId':registry,'numericId':id,'name':row['name'],'reason':reason,'aliases':members,
          'evidence':{'nativeFrame':native[id],'maxVanillaVisibleSurfaceFraction':row['maxVanillaSurfaceFraction'],
            'defaultVanillaVisibleSurfaceFraction':row['defaultVanillaSurfaceFraction'],'sourceModels':row['sourceModels'],
            'sourceResourcePackSha256':inventory['sourceResourcePackSha256'],'decodedSurfaceInventory':'docs/v12.5-city-review/style-inventory.json'}})
    result={'schemaVersion':1,'baselineCommit':BASELINE,'scope':'POST_ORIGINAL_V12_ONLY',
      'status':'REVIEWED_ALL572_NATIVE_INSTALLED_STYLE','workingTypeCountBefore':572,
      'workingTypeCountAfterConfirmedRetirements':572-len(retirements),'registrationsAndNumericReservations':'PRESERVED_COMPATIBILITY_ONLY_NOT_OFFERED',
      'protectedOriginalRegistryIds':sorted(original),'retirements':retirements}
    (RES/'imported-style-retirements.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    (OUT/'style-retirement-confirmed.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
if __name__=='__main__':main()
