#!/usr/bin/env python3
"""Make a new vanilla-loadable tool review world from the frozen V10 scene.

Never modifies input. Uses existing typed NBT helpers; no QA mod or package is
needed when the resulting world is opened. A vanilla datapack gives each new
player 90009 and Creative. Its isolated legacy samples are deliberately 90008.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import uuid
import zipfile
from pathlib import Path

from world_io import (Tag, NbtFile, RegionFile, compound, read_nbt, write_nbt,
    encode_nbt, section_blocks, pack_palette_indices, invalidate_chunk_lighting,
    TAG_BYTE, TAG_SHORT, TAG_INT, TAG_FLOAT, TAG_DOUBLE, TAG_STRING, TAG_LIST,
    TAG_COMPOUND, TAG_INT_ARRAY, TAG_LONG_ARRAY)


def C(**values): return Tag(TAG_COMPOUND, values)
def S(value): return Tag(TAG_STRING, value)
def I(value): return Tag(TAG_INT, value)
def B(value): return Tag(TAG_BYTE, value)
def D(value): return Tag(TAG_DOUBLE, value)
def L(values, kind): return Tag(TAG_LIST, values, kind)
def xyz(values): return L([D(v) for v in values], TAG_DOUBLE)
def uuid_tag(label):
    value = uuid.uuid5(uuid.NAMESPACE_URL, 'dreamwalker-tool-fix-fixture/' + label)
    return Tag(TAG_INT_ARRAY, [int.from_bytes(value.bytes[n:n+4], 'big', signed=True) for n in range(0, 16, 4)])


def stack(item, slot=None, *, legacy_action=None):
    fields = {'id': S(item), 'Count': B(1)}
    if slot is not None: fields['Slot'] = B(slot)
    if legacy_action is not None:
        fields['tag'] = C(BuilderAction=I(legacy_action), BuilderStep=D(.25),
            ToolFixtureMarker=S('legacy-compatible-nbt-must-survive'),
            display=C(Name=S(json.dumps({'text': 'Старый 90008 — проверка миграции'}, ensure_ascii=False))))
    return Tag(TAG_COMPOUND, fields)


class WorldEditor:
    def __init__(self, world): self.world, self.regions, self.chunks = world, {}, {}
    def chunk(self, kind, x, z):
        cx, cz = x // 16, z // 16
        key = kind, cx, cz
        if key not in self.chunks:
            path = self.world / kind / f'r.{cx//32}.{cz//32}.mca'
            region = self.regions.setdefault(path, RegionFile.open(path) if path.exists() else RegionFile())
            old = region.get_chunk(cx % 32, cz % 32)
            if old is None:
                if kind != 'entities': raise ValueError(f'Fixture cannot invent terrain chunk {cx},{cz}')
                doc = NbtFile('', C(DataVersion=I(3465), Position=Tag(TAG_INT_ARRAY,[cx,cz]), Entities=L([],TAG_COMPOUND)))
            else: doc = old.nbt()
            self.chunks[key] = doc
        return compound(self.chunks[key].root)
    def block(self, pos, name, props=None):
        x,y,z=pos; root=self.chunk('region',x,z)
        sections=root['sections'].value
        section=next((s for s in sections if compound(s)['Y'].value == y//16),None)
        if section is None:
            section=C(Y=B(y//16),block_states=C(palette=L([C(Name=S('minecraft:air'))],TAG_COMPOUND)))
            sections.append(section)
        states=compound(compound(section)['block_states'])
        palette,indices=section_blocks(section)
        fields={'Name':S(name)}
        if props: fields['Properties']=Tag(TAG_COMPOUND,{k:S(v) for k,v in props.items()})
        entry=Tag(TAG_COMPOUND,fields)
        if entry not in palette: palette.append(entry)
        indices[(y%16)*256+(z%16)*16+x%16]=palette.index(entry)
        states['data']=Tag(TAG_LONG_ARRAY,pack_palette_indices(indices,len(palette)))
        invalidate_chunk_lighting(root)
    def be(self,pos,tag):
        root=self.chunk('region',pos[0],pos[2]); entity_list=root.setdefault('block_entities',L([],TAG_COMPOUND));entity_list.list_type=TAG_COMPOUND;entities=entity_list.value
        entities[:]=[e for e in entities if tuple(compound(e).get(k,I(0)).value for k in ('x','y','z'))!=tuple(pos)]
        compound(tag).update({k:I(v) for k,v in zip(('x','y','z'),pos)})
        entities.append(tag)
    def sign(self,pos,lines):
        self.block(pos,'minecraft:oak_sign',{'rotation':'0','waterlogged':'false'})
        text=C(messages=L([S(json.dumps({'text':line},ensure_ascii=False)) for line in (lines+['']*4)[:4]],TAG_STRING),color=S('black'),has_glowing_text=B(0))
        self.be(pos,C(id=S('minecraft:sign'),front_text=text,back_text=text,is_waxed=B(1)))
    def entity(self,tag):
        p=[e.value for e in compound(tag)['Pos'].value]
        entity_list=self.chunk('entities',int(p[0]//1),int(p[2]//1)).setdefault('Entities',L([],TAG_COMPOUND));entity_list.list_type=TAG_COMPOUND;entity_list.value.append(tag)
    def save(self):
        for (kind,cx,cz),doc in self.chunks.items():
            path=self.world/kind/f'r.{cx//32}.{cz//32}.mca'
            validate_lists(doc.root, f'{kind}/{cx}/{cz}')
            self.regions[path].set_chunk(cx%32,cz%32,doc)
        for path,region in self.regions.items(): path.parent.mkdir(parents=True,exist_ok=True);region.save(path)


def validate_lists(tag, path):
    if tag.type == TAG_COMPOUND:
        for key, child in compound(tag).items():validate_lists(child,path+'/'+key)
    elif tag.type == TAG_LIST:
        if any(child.type != tag.list_type for child in tag.value):raise ValueError(f'Heterogeneous fixture NBT list: {path} declared {tag.list_type}, entries {[c.type for c in tag.value]}')
        for i, child in enumerate(tag.value):validate_lists(child,path+'/'+str(i))


def base_entity(label,registry,pos):
    return C(id=S(registry), UUID=uuid_tag(label), Pos=xyz(pos), Motion=xyz([0,0,0]),
        Rotation=L([Tag(TAG_FLOAT,90),Tag(TAG_FLOAT,0)],TAG_FLOAT), OnGround=B(1),
        CustomName=S(json.dumps({'text':label},ensure_ascii=False)))


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source',type=Path,required=True)
    parser.add_argument('--output',type=Path,required=True)
    args=parser.parse_args()
    output=args.output.resolve();output.mkdir(parents=True,exist_ok=True)
    world=output/'Tool-fix-review-scene'
    if world.exists(): raise SystemExit('Refusing to replace an existing fixture')
    source_hash=hashlib.sha256(args.source.read_bytes()).hexdigest()
    with zipfile.ZipFile(args.source) as archive:
        for info in archive.infolist():
            relative=Path(info.filename)
            if relative.is_absolute() or '..' in relative.parts: raise ValueError('Unsafe source archive')
            if len(relative.parts)<2 or info.is_dir(): continue
            target=world/Path(*relative.parts[1:]);target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(archive.read(info))
    for path in [world/'level.dat_old',world/'session.lock']:
        if path.exists():path.unlink()
    doc=read_nbt(world/'level.dat');data=compound(compound(doc.root)['Data'])
    data.update(LevelName=S('Dreamwalker · инструмент 90009'),GameType=I(1),allowCommands=B(1),SpawnX=I(0),SpawnY=I(64),SpawnZ=I(-24))
    player=compound(data.setdefault('Player',C()))
    player.update(Pos=xyz([.5,64,-24.5]),Rotation=L([Tag(TAG_FLOAT,0),Tag(TAG_FLOAT,0)],TAG_FLOAT),
        playerGameType=I(1),Dimension=S('minecraft:overworld'),Health=Tag(TAG_FLOAT,20),foodLevel=I(20),SelectedItemSlot=I(0),
        Inventory=L([stack('bloodborne_dw:composite_builder',0),stack('bloodborne_dw:builder_tool',1,legacy_action=2)],TAG_COMPOUND),
        abilities=C(invulnerable=B(1),flying=B(0),mayfly=B(1),instabuild=B(1),mayBuild=B(1),flySpeed=Tag(TAG_FLOAT,.05),walkSpeed=Tag(TAG_FLOAT,.1)))
    packs=compound(data.setdefault('DataPacks',C()))
    enabled=packs.setdefault('Enabled',L([S('vanilla')],TAG_STRING)).value
    if S('file/tool-fixture') not in enabled:enabled.append(S('file/tool-fixture'))
    write_nbt(world/'level.dat',doc)
    editor=WorldEditor(world)
    # Old catalogue offers the current tool; legacy items exist only in marked test fixtures.
    for path in sorted((world/'region').glob('*.mca')):
        region=RegionFile.open(path)
        for ch in region.chunks():
            root=editor.chunk('region',(int(path.stem.split('.')[1])*32+ch.x)*16,(int(path.stem.split('.')[2])*32+ch.z)*16)
            for be in root.get('block_entities',L([],TAG_COMPOUND)).value:
                for item in compound(be).get('Items',L([],TAG_COMPOUND)).value:
                    fields=compound(item)
                    if fields.get('id',S('')).value=='bloodborne_dw:builder_tool':fields['id']=S('bloodborne_dw:composite_builder')
    for x,label,item in [(0,'90009 · рабочий инструмент',stack('bloodborne_dw:composite_builder',0)),(4,'90008 · сундук миграции',stack('bloodborne_dw:builder_tool',0,legacy_action=9))]:
        editor.block((x,64,-28),'minecraft:chest',{'facing':'south','type':'single','waterlogged':'false'})
        editor.be((x,64,-28),C(id=S('minecraft:chest'),Items=L([item],TAG_COMPOUND)))
        editor.sign((x,64,-30),[label,'T01: предмет и NBT','Открыть / сохранить','Перезапустить мир'])
    dropped=base_entity('T01: выпавший 90008','minecraft:item',[8.5,64.2,-28.5])
    compound(dropped).update(Item=stack('bloodborne_dw:builder_tool',legacy_action=3),Age=Tag(TAG_SHORT,-32768),PickupDelay=Tag(TAG_SHORT,0),Health=Tag(TAG_SHORT,5))
    editor.entity(dropped);editor.sign((8,64,-30),['T01: выпавший 90008','Подобрать и проверить','Режим: положение','Шаг 1/4 сохранён'])
    added=[]
    for index,(asset,idnum) in enumerate([('cage_obj_1','91007'),('cage_obj_2','91008'),('cage_obj_3','91009')]):
        for copy in range(2):
            x=-24+index*16+copy*7;pos=[x,64,-40];label=f'{idnum} · клетка {copy+1}'
            entity=base_entity(label,'bloodborne_rp:'+asset,pos)
            compound(entity).update(Open=B(0),Scale=Tag(TAG_FLOAT,1),DogsVisible=B(1),VerticalOffset=D(0),RpBehaviourVersion=I(1),Links=L([],TAG_INT_ARRAY))
            editor.entity(entity);editor.sign((x,64,-44),[label,'T05: собака','Скрыть только эту','Проверить перезаход'])
            added.append({'typeId':idnum,'asset':asset,'position':pos,'uuid':str(uuid.uuid5(uuid.NAMESPACE_URL,'dreamwalker-tool-fix-fixture/'+label))})
    for label,asset,pos in [('91035 · третья цель','door_2',[16,64,-16]),('T02 · перекрытие A','chair',[24,64,-40]),('T02 · перекрытие B','chair',[24,64,-40]),('91088 · статический люк','trapdoor',[36,64,-40])]:
        entity=base_entity(label,'bloodborne_rp:'+asset,pos);compound(entity).update(Open=B(0),Scale=Tag(TAG_FLOAT,1),VerticalOffset=D(0),RpBehaviourVersion=I(1),Links=L([],TAG_INT_ARRAY));editor.entity(entity)
    editor.sign((16,64,-20),['91035 · третья цель','Рычаг → три двери','90001 + 91034 + 91035','T08 / T09 / T10'])
    editor.sign((24,64,-44),['T02 · перекрытие','Два стула в точке','Средняя кнопка мыши','Сравнить UUID'])
    editor.sign((36,64,-44),['91088 · статический','Не предлагать открытие','Сравнить с 91094','T12'])
    editor.sign((0,64,-22),['Инструмент 90009','ПКМ: настройки','Тесты: TOOL_FIX_TESTS','Стенд без QA-мода'])
    editor.save()
    pack=world/'datapacks/tool-fixture'
    def write(relative,value):
        path=pack/relative;path.parent.mkdir(parents=True,exist_ok=True);path.write_text(value,encoding='utf8')
    write('pack.mcmeta',json.dumps({'pack':{'pack_format':15,'description':'Первый вход: Creative и 90009. Только проверочный стенд.'}},ensure_ascii=False))
    write('data/minecraft/tags/functions/tick.json',json.dumps({'values':['tool_fixture:tick']}))
    write('data/tool_fixture/functions/tick.mcfunction','execute as @a[tag=!dw_tool_fixture_received] run function tool_fixture:first_join\n')
    write('data/tool_fixture/functions/first_join.mcfunction','gamemode creative @s\ngive @s bloodborne_dw:composite_builder\ntag @s add dw_tool_fixture_received\ntellraw @s {"text":"Стенд 90009: Creative включён, инструмент выдан. Старый 90008 для T01 — в подписанном сундуке у точки входа.","color":"gold"}\n')
    files={str(p.relative_to(world)):hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(world.rglob('*')) if p.is_file()}
    # This is an offline preparation result, deliberately not a gameplay PASS.
    manifest={'schema':'dreamwalker-tool-fix-fixture-v1','sourceArchive':args.source.name,'sourceSha256':source_hash,
        'worldFolder':world.name,'dataVersion':3465,'defaultCreative':True,'allowCommands':True,
        'firstJoinTool':'bloodborne_dw:composite_builder','qaModsRequired':False,'gameplayValidation':'Не проверено',
        'addedCages':added,'migrationFixtures':{'inventorySlot':1,'chest':[4,64,-28],'drop':[8.5,64.2,-28.5]},'files':files}
    (output/'TOOL_FIX_FIXTURE.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
    (world/'README_RU.txt').write_text('Dreamwalker: стенд инструмента 90009\nРаспакуйте папку Tool-fix-review-scene в saves. Используйте новый combined JAR и его обычные зависимости; QA-мод не нужен.\nCreative и читы уже включены. Встроенный vanilla datapack выдаёт 90009 каждому новому игроку.\nT01: старый предмет в слоте 2 одиночного игрока, сундуке 4 64 -28 и на земле 8.5 64.2 -28.5. Остальной каталог предлагает 90009.\nT05: клетки двух экземпляров каждого ID на линии z=-40. T02: два перекрывающихся стула 24 64 -40.\nT08: рычаг -32 64 -16; цели -8 64 -16 (90001), 8 64 -16 (91034), 16 64 -16 (91035).\nФонари A/B/C: 60/72/84 64 -16; дальний D: 800 64 64. Лестницы и архитектура сохранены из копии V10.\nПодробная матрица и ожидаемые результаты: docs/TOOL_FIX_TESTS.md.\nЭтот архив подготовлен офлайн: фактический первый вход, модели, клики и миграция проверяются отдельно.\n',encoding='utf8')
    archive_path=output/'Tool-fix-review-scene.zip'
    with zipfile.ZipFile(archive_path,'w',zipfile.ZIP_DEFLATED,compresslevel=9) as archive:
        for path in sorted(world.rglob('*')):
            if path.is_file():archive.write(path,str(path.relative_to(output)))
    print(json.dumps({'world':str(world),'archive':str(archive_path),'sha256':hashlib.sha256(archive_path.read_bytes()).hexdigest(),'status':'Prepared offline; gameplay pending'},ensure_ascii=False))


if __name__=='__main__': main()
