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


def build(destination):
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
    original=destination/'conversion-input-fixture'
    playable=destination/'Bloodborne-REPAIR-TEST-1'
    write_fixture(original,cells,floor=False,level_name='Bloodborne REPAIR TEST 1 - NOT FULL')
    shutil.copytree(original,playable)
    world=World(playable,defaults)
    items,_,_=candidates(world,selected)
    reject_overlaps(items)
    ledger=apply(world,items)
    assert len(ledger)==5, [(c.rule.number,c.reason) for c in items]
    report={'format':'bloodborne-logical-world-conversion-v2','sourceMode':'modded',
            'counts':{'converted':len(ledger)},'ledger':ledger}
    validate_ledger(World(original,defaults),report,selected)
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
    (destination/'conversion-report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
    lines=['# Промежуточный REPAIR TEST 1 — не beta.4 и не FULL', '',
           'Minecraft 1.20.1 / Fabric / Java 17. Установите тестовый JAR вместо прежнего Bloodborne JAR в отдельной копии сборки; Fabric API обязателен.',
           'Распакуйте папку Bloodborne-REPAIR-TEST-1 в saves. Мир творческий, команды включены. Полная карта и main не изменены; whole-world gates остаются FAIL.', '',
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
    (destination/'README-RU.md').write_text('\n'.join(lines)+'\n',encoding='utf-8')
    with zipfile.ZipFile(destination/'Bloodborne-REPAIR-TEST-1-world.zip','w',zipfile.ZIP_DEFLATED) as archive:
        for path in sorted(playable.rglob('*')):
            if path.is_file(): archive.write(path,path.relative_to(destination))
    print(json.dumps({k:report[k] for k in ('counts','helperAudit','secondPassChanges','wholeWorldGates')},ensure_ascii=False))


if __name__=='__main__':
    import argparse
    parser=argparse.ArgumentParser()
    parser.add_argument('output',type=Path)
    build(parser.parse_args().output)
