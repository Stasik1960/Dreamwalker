"""Five isolated, source-verified specimens; NOT a whole-world conversion."""
import hashlib
import json
import shutil
import zipfile
from dataclasses import replace
from pathlib import Path

from atomic_owner_groups import compile_groups, state
from check_logical_world import validate_ledger
from city_palette import audit_helpers, DEFAULT_CITY
from composite_world_oracle import EvidenceReader, key
from convert_logical_world import (World, DEFAULT_RESOURCES, Output, add, candidates,
                                   reject_overlaps, apply, hash_tree)
from modded_world_adapter import compile_modded_rules
from reviewed_migration_fixture import write_fixture
from source_variant_rng import guards_match
from world_io import compound

ROOT = Path(__file__).resolve().parents[1]
DIM = 'minecraft:overworld'
SHA = 'c517dfeb52c4d13bdbe90e02a93ac00416354eb89313a9d1377f24823af6d0e9'
CASES = [
    ('C001', 356, (-284,42,-71)),
    ('C474', 396, (-566,85,-165)),
    ('C618', 131, (-540,41,-33)),
    ('Connected fence', 'historical-owner-9ebfaabd9b29ac253a18', (-429,99,31)),
    ('Shared root/helper', 'historical-owner-00f791aa1871c8986556', (-373,24,-102)),
]


def build(destination,edition=1):
    if destination.exists():
        raise FileExistsError('Use a new output directory; existing test saves are preserved')
    source = ROOT/'reference-inputs/latest-modded-world.zip'
    assert hashlib.file_digest(source.open('rb'),'sha256').hexdigest() == SHA
    rules, defaults, _ = compile_modded_rules(DEFAULT_RESOURCES)
    rules, _ = compile_groups(rules, DEFAULT_RESOURCES)
    reader = EvidenceReader(source)
    cells = {(0,64,4): ('minecraft:air',{})}
    for x in range(-8,9):
        for z in range(-8,9): cells[x,63,z] = ('minecraft:quartz_block',{})
    selected = []
    specimens = []
    for number,(label, identity, origin) in enumerate(CASES):
        matches = [r for r in rules if (r.number == identity if isinstance(identity,int)
                                       else r.transaction_id == identity)]
        assert len(matches)==1, (label,len(matches))
        rule = matches[0]
        assert rule.accepts_origin(DIM,origin) and guards_match(rule.variant_guards,origin)
        assert not rule.preflight_error
        source_cells = {}
        for piece in (rule.source,)+rule.members+rule.required_context:
            pos = add(origin,piece.offset)
            assert reader.state(DIM,pos)[1] == key(piece.state), (label,pos)
            source_cells[pos] = (piece.state[0],dict(piece.state[1]))
        outputs = rule.outputs or (Output(rule.target,rule.root_offset,rule.shape),)
        physical = {add(add(origin,o.root_offset),p) for o in outputs for p in o.shape}
        occupied = set(source_cells)|physical
        low = min(p[1] for p in occupied)-1
        xmin,xmax = min(p[0] for p in occupied)-10,max(p[0] for p in occupied)+28
        zmin,zmax = min(p[2] for p in occupied)-10,max(p[2] for p in occupied)+10
        for x in range(xmin,xmax+1):
            for z in range(zmin,zmax+1): cells[x,low,z]=('minecraft:smooth_stone',{})
        # Pre-create every destination section/chunk without occupying it.
        for pos in physical: cells.setdefault(pos,('minecraft:air',{}))
        cells.update(source_cells)
        selected.append(replace(rule,number=number,allowed_origins=((DIM,origin),),
                                shared_physics=rule.atomic_owner_group))
        specimens.append({'case':label,'sourceRule':rule.number,'transaction':rule.transaction_id,
                          'origin':origin,'teleport':[origin[0]+.5,low+2,origin[2]-7.5],
                          'manualPad':[xmax-8,low+1,origin[2]],
                          'sourceCells':len(source_cells),'outputs':[
                              {'root':add(origin,o.root_offset),'state':key(o.target)} for o in outputs]})
    reader.close()
    manual_cells={};manual_helpers=[]
    window_stands=[]
    if edition>=2:
        # Clearly labelled constructed regression scenes; not historical evidence.
        for x in range(18,72):
            for z in range(-8,14):cells[x,63,z]=('minecraft:smooth_stone',{})
        window=('bloodborne_blocks:o_shuttered_window',{'facing':'north','open':'false','visual':'base'})
        wall='bloodborne_blocks:building_stone_brick_wall'
        manual_cells[(25,64,0)]=window
        manual_cells[(25,65,0)]=(wall,{'facing':'north','connection':'low_0'})
        manual_helpers.append(((25,65,0),(25,64,0),window[0]))
        for x,mask in [(42,2),(49,3),(56,7),(65,15)]:
            manual_cells[(x,64,0)]=(wall,{'facing':'north','connection':'low_'+str(mask)})
            for bit,(dx,dz) in enumerate(((0,-1),(1,0),(0,1),(-1,0))):
                if mask&(1<<bit):manual_cells[x+dx,64,dz]=(wall,{'facing':'north','connection':'low_'+str(1<<((bit+2)%4))})
        for x,facing in zip((42,47,52,57),('north','east','south','west')):
            manual_cells[x,64,8]=(wall,{'facing':facing,'connection':'low_0'})
        if edition>=3:
            # Separate ordinary backing and a window root/helper column. No shared wall cells.
            for x in range(80,134):
                for z in range(16,81):cells[x,63,z]=('minecraft:smooth_stone',{})
            for index,(facing,dx,dz,material) in enumerate((
                    ('north',0,-1,'lime_wool'),('east',1,0,'stone_bricks'),
                    ('south',0,1,'oak_planks'),('west',-1,0,'glass'))):
                for row,(visual,opened) in enumerate((('base','false'),('base','true'),('alt','false'),('alt','true'),('empty','false'),('no_backing','false'))):
                    root=(86+index*12,64,22+row*10)
                    backing=(root[0]-dx,root[1],root[2]-dz)
                    if visual!='no_backing':
                        for dy in range(-1,4):
                            for lateral in range(-2,3):
                                manual_cells[backing[0]+dz*lateral,64+dy,backing[2]-dx*lateral]=('minecraft:'+material,{})
                    if visual!='empty':
                        manual_cells[root]=('bloodborne_blocks:o_shuttered_window',{'facing':facing,'open':opened,'visual':'base' if visual=='no_backing' else visual})
                        helper=(root[0],root[1]+1,root[2])
                        manual_cells[helper]=('bloodborne_blocks:architecture_part',{})
                        manual_helpers.append((helper,root,'bloodborne_blocks:o_shuttered_window'))
                    window_stands.append({'root':list(root),'backing':list(backing),'facing':facing,'material':'minecraft:'+material,'visual':visual,'open':opened})
        # Preallocate chunk/sections, but add regression states only after ledger authorization.
        for pos in manual_cells:cells[pos]=('minecraft:air',{})
    original=destination/'conversion-input-fixture'
    playable=destination/f'Bloodborne-REPAIR-TEST-{edition}'
    write_fixture(original,cells,floor=False,level_name=f'Bloodborne REPAIR TEST {edition} - NOT FULL')
    shutil.copytree(original,playable)
    world=World(playable,defaults)
    items,_,_=candidates(world,selected)
    reject_overlaps(items)
    ledger=apply(world,items)
    assert len(ledger)==5, [(c.rule.number,c.reason) for c in items]
    report={'format':'bloodborne-logical-world-conversion-v2','sourceMode':'modded',
            'counts':{'converted':len(ledger)},'ledger':ledger}
    validate_ledger(World(original,defaults),report,selected)
    for pos,(name,props) in manual_cells.items():world.set(DIM,pos,(name,tuple(sorted(props.items()))))
    for pos,root,owner in manual_helpers:world.add_helper(DIM,pos,root,owner)
    world.save()
    again=World(playable,defaults)
    declared={'bloodborne_blocks:architecture_part'}
    for resource in (DEFAULT_RESOURCES.parent,DEFAULT_RESOURCES,DEFAULT_CITY):
        if not (resource/'definitions.json').is_file(): continue
        declared.update('bloodborne_blocks:'+b['id'] for b in json.loads((resource/'definitions.json').read_bytes())['blocks'])
    for chunk in again.chunks.values():
        for section in chunk.root()['sections'].value:
            blockstates=compound(section).get('block_states')
            if blockstates:
                for entry in compound(blockstates)['palette'].value:
                    name=compound(entry)['Name'].value
                    assert not name.startswith('bloodborne_blocks:') or name in declared, name
    expected=dict(cells)
    for entry in ledger:
        for change in entry['changes']:
            name,props=state(change['after'])
            expected[tuple(change['position'])]=(name,dict(props))
    expected.update(manual_cells)
    for pos,(name,props) in expected.items():
        actual=again.get(DIM,pos)
        assert actual and actual[0]==name and dict(actual[1])=={**defaults.get(name,{}),**props}, pos
    audit=audit_helpers(again,DEFAULT_CITY)
    assert audit['ok'],audit
    before=hash_tree(playable)
    items,_,_=candidates(again,selected)
    reject_overlaps(items)
    second=apply(again,items)
    assert not second
    again.save()
    assert before==hash_tree(playable)
    report.update({'scope':'ISOLATED TEST ONLY','moddedEvidenceSha256':SHA,
                   'specimens':specimens,'helperAudit':audit,'secondPassChanges':0,
                   'unknownModPaletteIds':0,
                   'secondPassByteIdentical':True,'wholeWorldGates':'FAIL / UNFINISHED',
                   'graphicalClientChecks':'NOT_RUN - user test required',
                   'note':'Synthetic platforms, source positions unchanged. No surrounding city copied.'})
    if edition>=2:report['constructedRegressions']={'windowRoot':[25,64,0],'sharedWallRoot':[25,65,0],
            'wallExamples':[[42,64,0],[49,64,0],[56,64,0],[65,64,0]],
            'historicalEvidence':False,'note':'New manual test arrangements, original five specimens preserved.'}
    if edition>=3:report['windowTest3Stands']=window_stands
    (destination/'conversion-report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
    lines=[f'# Промежуточный REPAIR TEST {edition} — не beta.4 и не FULL', '',
           'Minecraft 1.20.1 / Fabric / Java 17. Установите тестовый JAR вместо прежнего Bloodborne JAR в отдельной копии сборки; Fabric API обязателен.',
           f'Распакуйте папку Bloodborne-REPAIR-TEST-{edition} в saves. Мир творческий, команды включены. Полная карта и main не изменены; whole-world gates остаются FAIL.', '',
           'Это пять изолированных фрагментов из проверенных assemblies latest MODDED backup на исходных координатах. Площадки искусственные; окружающая архитектура не включена.',
           'C618 показан без соседней исторической панели. Это не доказательство сохранности всей городской сцены.', '',
           '## Переходы к образцам']
    for row in specimens:
        lines += ['',f"**{row['case']}**: `/tp @s {' '.join(map(str,row['teleport']))}`",
                  f"Корни: {', '.join(str(o['root']) for o in row['outputs'])}. Пустая площадка для установки: {row['manualPad']}."]
    lines += ['', '## Проверка в игре (пока НЕ подтверждена графическим клиентом)',
              'Для каждого образца:',
              '1. Осмотрите цельность, текстуры и положение; пройдите вокруг, проверьте столкновения.',
              '2. Средней кнопкой возьмите предмет с корневого блока. Установите на свободную часть площадки.',
              '3. Повторите установку, глядя с четырёх сторон: модель должна поворачиваться вокруг своего pivot без ухода в сторону.',
              '4. Поставьте обычные блоки рядом, затем попробуйте внутри физически занятой клетки: соседняя застройка должна сохраняться, пересечение должно отклоняться.',
              '5. Сломайте копию за корень и за доступную часть. Проверьте отсутствие висячих частей и сохранность соседа.',
              '6. Сделайте ещё копию, выйдите в меню, полностью перезапустите игру и откройте сохранение; повторите осмотр и разрушение.',
              '7. Для общей группы проверьте обе очередности разрушения двух объектов. Повторная установка этой пары предметами требует отдельной проверки: нижний корень ограды хранит гостевую физику верхнего объекта. Вставка нового корня в уже занятый helper пока отклоняется; это известное ограничение тестовой версии.',
              'У connected-ограды при обычной установке/обновлении соседей соединения пересчитываются. В изолированной конфликтующей паре исходные east/south-соединения могут исчезнуть без соответствующих соседей. Конвертированный снимок сохраняет исходные флаги, а ручная установка следует обычной логике соединений; это не должно удалять корни или гостевую физику.',
              'Общая helper-клетка не выбирает случайного владельца: для получения предмета/действия цельтесь именно в нужный корень.',
              '', 'Запишите результат по каждому пункту и приложите координаты/скриншот при сбое. Серверные GameTests и офлайн-конвертация не заменяют этот клиентский чек-лист.']
    if edition>=2:
        lines=[line for line in lines if 'Вставка нового корня' not in line]
        lines+=['','## Новые проверки REPAIR TEST 2',
          '`/tp @s 25.5 65 -5.5` — специально собранная регрессионная пара: окно (25,64,0), кирпичная ограда в общей верхней клетке (25,65,0). Это новый стенд, не восстановленная историческая сцена.',
          'Возьмите предмет стены, удалите только стену, поставьте её обратно с зажатым Shift кликом по верхней грани нижней части окна (обычный ПКМ открывает окно). Затем отдельно удалите/верните окно. Повторите несколько раз и после выхода/входа в мир. Второй объект должен оставаться целым.',
          '`/tp @s 49.5 65 -5.5` — рядом показаны пара, угол, Т и перекрёсток. Возьмите предмет с любой старой кирпичной секции из исходной сцены: это один предмет «Кирпичная ограда — соединяемая». Проверьте свободную установку и удаление соседей.',
          'Низкий перекрёсток использует существующий вариант без центрального столба. Низкий перекрёсток со столбом не поддержан; новых моделей нет. Под сплошным блоком сверху используется существующая высокая секция.',
          'Исходные шесть ID кирпичных секций остаются совместимыми и визуально неизменными. Их предмет для строительства теперь общий. Это не объединяет o_stone_railing и другие семейства.',
          'Важное различие: в неизменённой исходной сцене верхняя клетка окна (-428,101,32) изначально была helper, а соседние стены стояли при x=-427. Новая пара отдельно проверяет именно вставку корня в занятую helper-клетку.']
    if edition>=3:
        lines+=['','## Новые проверки REPAIR TEST 3',
          '`/tp @s 86 65 17` — начало стенда обычных стен. Столбцы x=86/98/110/122: north/east/south/west, соответственно лаймовая шерсть / каменный кирпич / дубовые доски / стекло. Нижние корни всех окон y=64; полный опорный блок — y=63.',
          'Ряды z=22/32/42/52: BASE закрыто / BASE открыто / ALT закрыто / ALT открыто. У каждой стены своё окно в соседнем столбце; стена не занимает root/helper окна. Подоконник должен касаться опорной плоскости, рама — наружной грани задника. Декоративные края не расширяют физическую маску 1×2.',
          'Ряд z=62 — четыре готовые стены без окон: возьмите окно из вкладки и нажмите на соответствующую наружную грань стены на y=64. Ряд z=72 — четыре окна без задника: заполните задний столбец обычными блоками на y=63..67, затем замените их другим материалом. Координаты всех корней/задников перечислены в conversion-report.json → windowTest3Stands.',
          'Удалите и верните задник, откройте/закройте ставни, снимите окно за нижнюю и верхнюю часть, снова установите. Соседняя стена и оставшиеся предметы должны сохраняться. Сплошная стена закономерно видна в просвете рамы. Проём оставляется вручную.',
          'Вкладка Bloodborne: сначала цельные/строительные объекты, затем исторические owner, native compatibility и технические city-секции с подсказками. Выберите ALT дерева с небазовым variant и несколько city variant=1/3/7, поставьте их на свободной площадке и сравните выбранный вариант. Повороты, открывание и служебные состояния отдельными предметами не перечислены.',
          'Сохраните мир, полностью закройте и запустите клиент, повторите проверку. Автоматические item/GameTests подтверждают серверное поведение и NBT, но графический клиент и его перезапуск здесь не запускались.']
    (destination/'README-RU.md').write_text('\n'.join(lines)+'\n',encoding='utf-8')
    with zipfile.ZipFile(destination/f'Bloodborne-REPAIR-TEST-{edition}-world.zip','w',zipfile.ZIP_DEFLATED) as archive:
        for path in sorted(playable.rglob('*')):
            if path.is_file(): archive.write(path,path.relative_to(destination))
    print(json.dumps({k:report[k] for k in ('counts','helperAudit','secondPassChanges','wholeWorldGates')},ensure_ascii=False))


if __name__=='__main__':
    import argparse
    parser=argparse.ArgumentParser()
    parser.add_argument('output',type=Path)
    parser.add_argument('--edition',type=int,choices=(1,2,3),default=1)
    args=parser.parse_args();build(args.output,args.edition)
