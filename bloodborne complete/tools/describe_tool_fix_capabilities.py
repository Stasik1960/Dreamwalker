#!/usr/bin/env python3
"""Produce per-ID facts from the preserved catalogue and actual implementation.

This inventory describes supported server handlers/resources. It never grants
gameplay PASS, and deliberately excludes visually identical BASE/ALT resources.
"""
import hashlib
import json
import re
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]


def main():
    catalogue_path=ROOT/'src/architecture/resources/bloodborne_dw/debug_catalogue.json'
    rp_path=ROOT/'src/rp/java/dev/dreamwalker/bloodbornerp/object/RpObjectEntity.java'
    compatibility_path=ROOT/'src/rp/java/dev/dreamwalker/bloodbornerp/object/RpObjectCompatibility.java'
    bridge_path=ROOT/'src/architecture/java/dev/dreamwalker/bloodbornedw/link/CompositeMechanismBridge.java'
    catalogue=json.loads(catalogue_path.read_text());source=rp_path.read_text()
    passages=set(re.findall(r'"([a-z0-9_]+)"',re.search(r'PASSAGES=java\.util\.Set\.of\((.*?)\);',source).group(1)))
    aliases_text=re.search(r'ALIASES\s*=\s*Map\.of\((.*?)\);',compatibility_path.read_text(),re.S).group(1)
    flat=re.findall(r'"([a-z0-9_]+)"',aliases_text);aliases=dict(zip(flat[::2],flat[1::2]))
    records=[];inputs=[catalogue_path,rp_path,compatibility_path,bridge_path]
    for entry in sorted(catalogue['entries'],key=lambda e:e['temporaryId']):
        registry=entry['registryId'];asset=registry.split(':',1)[1];kind=entry['kind'];number=entry['temporaryId']
        row={'id':number,'name':entry['name'],'registry':registry,'kind':kind,'offered':True,
             'operations':[],'rotationDegrees':None,'mountPlanes':[], 'visibleProfileChange':False,
             'mechanismSource':False,'mechanismTarget':False,'eventCommands':False,'dogs':False,'lampRoutes':False,'pulseOnly':False,'note':''}
        if kind=='architecture':
            row['operations']=['Выбор','Поворот','Высота','Осмотр'];row['rotationDegrees']=90 if number in {'90004','90010','90020'} else 45
            descriptor=ROOT/f'src/architecture/resources/bloodborne_dw/composite/{asset}.json'
            if descriptor.exists():
                data=json.loads(descriptor.read_text());inputs.append(descriptor)
                if data.get('openable'):row['operations'].append('Положение')
            if number in {'90004','90010','90020'}:
                row['mountPlanes']=['Вертикаль','Пол','Потолок'];row['operations'].append('Монтаж')
            if asset=='prototype_double_door':row['mechanismTarget']=True;row['eventCommands']=True
            row['note']='BASE/ALT читается совместимо; поставляемые ALT-модели наследуют BASE без визуальных изменений.'
        elif kind=='rp_object':
            canonical=aliases.get(asset,asset)
            row['operations']=['Выбор','Поворот','Высота','Осмотр'];row['rotationDegrees']=45
            row['offered']=asset not in aliases
            row['mechanismSource']=canonical.startswith('lever_')
            row['mechanismTarget']=canonical in passages|{'chest','ladder','wood_gate'}
            row['pulseOnly']=canonical=='wood_gate'
            row['eventCommands']=row['mechanismTarget'] and not row['pulseOnly']
            row['dogs']=canonical in {'cage_obj_1','cage_obj_2','cage_obj_3'}
            row['lampRoutes']=canonical=='hunterlamp'
            # RP stable open state is operated by gameplay/mechanisms. The tool
            # does not promise a decorative POSE action for these entities.
            row['ordinaryOpenClose']=row['eventCommands']
            if row['dogs']:row['operations'].append('Собака: скрыть/показать')
            if row['mechanismSource']:row['note']='Источник рычага: авторский импульс с задержкой 70 серверных тиков; не цель открытия.'
            if row['pulseOnly']:row['note']='Один авторский импульс; нет стойкого состояния открыто/закрыто и команд этих событий.'
            if canonical=='trapdoor':row['note']='Статический люк: не открывается, не цель рычага.'
            if asset in aliases:row['note']=f'Старый registry читается; самостоятельное предложение выведено. Канонический предмет: bloodborne_rp:{canonical}_placer.'
        elif kind=='technical_tool':
            row['offered']=number=='90009';row['operations']=['Инструмент настройки'] if row['offered'] else []
            row['note']='Единственный рабочий инструмент.' if row['offered'] else 'Выведенный номер. Скрытый registry-переходник 90008 → 90009 сохраняет настройки при чтении предмета.'
        else:
            row['note']='Предмет/живое существо: инструмент строительства не редактирует; ID сохранён.'
        records.append(row)
    sources={str(p.relative_to(ROOT)):hashlib.sha256(p.read_bytes()).hexdigest() for p in inputs}
    output={'schema':'dreamwalker-tool-fix-per-id-capabilities-v1','basis':'Actual server implementations and resource descriptors; no client acceptance implied',
            'gameplayStatus':'Не проверено','sourceSha256':sources,'records':records}
    (ROOT/'reports/TOOL_FIX_CAPABILITIES.json').write_text(json.dumps(output,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
    text=['# Возможности по пятизначным ID','',
        'Перечень основан на зарегистрированных типах, `RpObjectEntity`, мосте механизмов и дескрипторах моделей. Это перечень обработчиков, а не статус игровой проверки. Конкретные сценарии и фактические результаты — `TOOL_FIX_TESTS.md` и `reports/TOOL_FIX_TEST_MATRIX.json`.','',
        'У всех размещённых типов ниже есть выбор конкретного UUID, осмотр, поворот и точное смещение по высоте. Шаг высоты: 1/16, 1/8, 1/4, 1. Самостоятельный художественный вариант берётся отдельным предметом. Масштаб и сдвиг X/Z инструментом не добавляются.','',
        'BASE/ALT у существующих архитектурных ресурсов наследует одну и ту же модель без изменения пикселей. Сохранённый профиль совместим; видимую смену оформления этим флагом не обещают. Монтаж означает именно три плоскости стекла, а не произвольное изменение установки других моделей.','',
        '| ID | Название / registry | Угол | Особые возможности |','|---|---|---|---|']
    for row in records:
        if row['kind'] not in {'architecture','rp_object'} or not row['offered']:continue
        extra=[]
        if 'Положение' in row['operations']:extra.append('положение открыто/закрыто')
        if row.get('ordinaryOpenClose'):extra.append('обычное открытие/закрытие без инструмента')
        if row['mountPlanes']:extra.append('монтаж: вертикаль / пол / потолок')
        if row['dogs']:extra.append('скрыть/показать собаку')
        if row['mechanismSource']:extra.append('источник рычага; задержка 70 тиков')
        if row['mechanismTarget']:extra.append('цель рычага')
        if row['eventCommands']:extra.append('«Только рычагами»; события открытия/закрытия (OP4)')
        if row['lampRoutes']:extra.append('телепортные назначения / линии / имя / направление')
        if row['pulseOnly']:extra.append('только одиночный импульс')
        if row['id']=='91088':extra.append('статический люк')
        text.append(f"| {row['id']} | {row['name']} · `{row['registry']}` | {row['rotationDegrees']}° | {'; '.join(extra) or 'только общие операции'} |")
    text+=['','## Особые обработчики','',
        '| Типы | Что проверяется отдельно |','|---|---|',
        '| 90001 | Составная дверь: атомарные переходы геометрии, цель рычага и события. |',
        '| 90004 / 90010 / 90020 | Только 90°, три монтажные плоскости; независимые художественные ID. |',
        '| 90006 / 90018 / 90019 | Native лестница и исходная пара root/helper: подъём, опора, UUID после высоты. |',
        '| 90002 / 90011–90017 | Native ограда: поворот и высота; материал каждого типа остаётся прежним. |',
        '| 90007 | Положение деревянных панелей; мост рычагов не объявляет его активной целью. |',
        '| 91007 / 91008 / 91009 | Видимость полного дерева костей собаки, независимость двух экземпляров, состояние после загрузки. |',
        '| 91034 / 91035 / 91075 / 91083 | RP-проходы: переходы открытия, сохранение команд, блокировка ручного управления. |',
        '| 91015 (chest) | RP-сундук: активная цель открытия; не путать с native сундуком миграции предмета. |',
        '| 91069 | RP-лестница: анимация развёртывания, рабочая лестница/верхняя площадка. |',
        '| 91072 / 91073 | Рычаги: импульс задержан на 70 тиков, сброс позы; каждый вид проверяется. |',
        '| 91063 | Фонарь: UUID/имя/маршруты, адресация при движении/замене, безопасное прибытие и ожидание. |',
        '| 91086 | Ступени: физическая опора, высота и обычное удаление в Creative. |',
        '| 91094 | Импульсный wood_gate: один допустимый запуск; не стойкое открытие. |',
        '| 91088 | Статический trapdoor: пустые операции открытия не предлагаются. |','',
        'Рычаги выбираются как источники; перечень целей содержит только реальные активные обработчики. События требуют прав уровня 4, диагностика — оператора уровня 2; обычное редактирование — Creative или разрешённого оператора.','',
        '## Сохранённые номера вне размещённого каталога','',
        '| ID | Registry | Статус |','|---|---|---|']
    for row in records:
        if row['kind'] in {'architecture','rp_object'} and row['offered']:continue
        text.append(f"| {row['id']} | `{row['registry']}` | {row['note']} |")
    (ROOT/'docs/TOOL_FIX_CAPABILITIES.md').write_text('\n'.join(text)+'\n',encoding='utf8')
    print(json.dumps({'records':len(records),'canonicalPlacedTypes':sum(r['offered'] and r['kind'] in {'architecture','rp_object'} for r in records),'output':'docs/TOOL_FIX_CAPABILITIES.md'},ensure_ascii=False))


if __name__=='__main__':main()
