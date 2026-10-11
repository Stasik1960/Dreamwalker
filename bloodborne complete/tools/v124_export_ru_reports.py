"""Export readable V12.4 decisions from the applied manifest; no production writes."""
from pathlib import Path
from collections import defaultdict
import csv, hashlib, json, math, traceback

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'docs/v12.4-dedup'

STAIR_MATERIALS = {
 '90275':'Жёлтый бамбук с перекрёстным, клетчатым рисунком волокон',
 '90276':'Жёлтый бамбук с непрерывными параллельными волокнами',
 '90322':'Длинные прямоугольные серые кирпичи с тёмным раствором',
 '90409':'Розовые деревянные доски с заметными волокнами',
 '90423':'Широкие гладкие серые панели с тёмным горизонтальным швом',
 '90437':'Крупные неправильные булыжники с широкими тёмными щелями',
 '90493':'Резной камень с крупным угловатым прямоугольным орнаментом',
 '90523':'Сильные вертикальные серые борозды и полосы',
 '90550':'Повторяющийся овальный фасадный орнамент',
 '90686':'Коричневая древесина с горизонтальными волокнами',
 '90692':'Крапчатый коричневый камень, уложенный рядами',
 '90701':'Оранжево-коричневые прямоугольные кирпичи',
 '90760':'Квадратные и прямоугольные серые панели с явной рамкой по краям',
 '90804':'Тёмные зелёно-серые каменные плиты',
 '90862':'Длинные полосатые коричневые деревянные доски',
 '93004':'Серый кирпич с широкими зелёными полосами мха',
 '93028':'Светлые серые почти гладкие поверхности без кирпичных швов и квадратной рамки',
 '93081':'Крупные квадратные серые камни с широкими прямоугольными тёмными швами',
 '93094':'Непрерывная мелкозернистая серая поверхность без крупных щелей между булыжниками',
}

def load(name): return json.loads((OUT/name).read_text(encoding='utf-8'))
def compact(value): return json.dumps(value,ensure_ascii=False,separators=(',',':'),sort_keys=True)
def logical_pose(mapping,kind):
    value=int(mapping['newVariant']);props=mapping.get('properties',{})
    if kind=='artwork':return value+16*int(props.get('art_bank',0))
    if kind=='stairs' and str(props.get('trim','false')).lower()=='true':return value+10
    return value
def write_csv(name, headers, rows):
    with (OUT/name).open('w',encoding='utf-8-sig',newline='') as f:
        writer=csv.writer(f,delimiter=';');writer.writerow(headers);writer.writerows(rows)

def main():
    manifest=load('type-state-mapping.json');frozen=load('baseline-content.json')
    inventory=load('visual-candidates.json')['inventory']
    general=load('general-functional-groups.json');decisions=load('reviewed-global-decisions.json')
    counts=load('type-counts.json')
    oldrows={row['registryId']:row for row in frozen['debugCatalogue']['entries']}
    by_id={str(row['temporaryId']):row for row in oldrows.values()}
    sources={(row['id'],int(row['variant'])):row for row in inventory}
    group_by_id={row['primaryId']:row for row in manifest['groups']}
    canonical={oldrows[r]['temporaryId']:g['primaryId'] for g in manifest['groups'] for r in g['members']}
    canonical={str(k):v for k,v in canonical.items()}
    mappings=defaultdict(list)
    for row in manifest['mappings']: mappings[row['newId']].append(row)
    for group in manifest['groups']:
        group.setdefault('poseCount',max(logical_pose(m,group['kind']) for m in mappings[group['primaryId']])+1)
    general_groups={g['mainId']:g for g in general['groups']}
    def name(i): return by_id[str(i)]['name']
    def canon(i): return canonical.get(str(i),str(i))
    merge_rows=[]
    for group in manifest['groups']:
        primary=group['primaryId'];member_ids=[str(oldrows[r]['temporaryId']) for r in group['members']]
        kind=group['kind'];reason=[]
        if kind=='stairs':
            reason=[STAIR_MATERIALS[primary]+'. Направление, верх/низ, прямая форма, внутренние и внешние углы — состояния одного предмета.']
            if primary=='93028':reason.append('90980 сохранён как состояние с декоративной задней отделкой; отделка не оправдывает отдельный предмет.')
        elif kind=='ladder':reason=['Одна вертикальная приставная лестница. Высота одной/двух секций, направление и способы монтажа — состояния; подъём и наращивание сохранены.']
        else:
            for family in decisions['families']:
                if len(set(member_ids)&set(family['ids']))>1:reason.append(family['reason'])
            if not reason:
                edges=general_groups.get(primary,{}).get('evidenceEdges',[])
                if any(e['reason']=='MATCH_TRANSFORMED_VISIBLE_FACES_AND_UV_SAMPLED_PIXELS' for e in edges):
                    reason=['После совмещения положения совпадают видимые поверхности, их геометрия и текстуры; поворот и монтаж сохранены состояниями.']
                else:reason=['Визуально один функциональный объект после совмещения положения. Небольшие декоративные и позовые различия сохранены состояниями одного предмета.']
        state_properties=sorted({k for m in mappings[primary] for k in m.get('properties',{})})
        if kind=='artwork':state_properties=['variant']+(['art_bank'] if group['poseCount']>16 else [])+['rotation','mount_y','vertical_mount']
        else:state_properties=['variant','rotation','mount_y','vertical_mount']+state_properties
        merge_rows.append([primary,name(primary),kind,' | '.join(member_ids),
             ' | '.join(i+': '+name(i) for i in member_ids),group['poseCount'],
             ', '.join(state_properties),'ДА — один рабочий тип и один предмет',
             'Старые регистрации скрыты; номера зарезервированы',' '.join(reason)])
    write_csv('MERGED-GROUPS-RU.csv',
       ['Основной ID','Русское название','Тип','Исходные ID','Исходные названия',
        'Количество поз/состояний','Свойства состояния','Каталог и Creative','Совместимость','Основание объединения'],merge_rows)
    state_rows=[]
    for mapping in manifest['mappings']:
        group=group_by_id[mapping['newId']];pose=logical_pose(mapping,group['kind'])
        props={'variant':mapping['newVariant']}
        props.update(mapping.get('properties',{}))
        source=sources.get((mapping['oldId'],int(mapping['oldVariant'])),{})
        state_rows.append([mapping['oldId'],source.get('name',name(mapping['oldId'])),mapping['oldRegistryId'],mapping['oldVariant'],
             mapping['newId'],name(mapping['newId']),mapping['newRegistryId'],pose,compact(props),
             mapping['rotationDelta'],mapping['rotationDelta']*45,compact(mapping['sourceShift']),
             '(old.rotation + rotationDelta) mod 8',
             'old.SourceShift.rotate(-rotationDelta) + sourceShift; Shift.rotate использует поворот Minecraft -45° за шаг',
             'Сохраняются mount_y, vertical_mount, profile/open и NBT экземпляра: UUID, настройки, связи, события; старые ступени — connections=manual, новые автоматически соединяются'])
    write_csv('OLD-ID-STATES.csv',
       ['Старый ID','Исходный вариант — название','Старая регистрация','Старый variant',
        'Основной ID','Название рабочего типа','Новая регистрация','Логический номер позы',
        'Назначаемые свойства JSON','Добавка поворота — шаги 45°','Добавка поворота — градусы',
        'Добавка SourceShift — координаты нового типа','Формула rotation','Формула SourceShift','Сохраняемые данные'],state_rows)
    # Build concrete material/silhouette notes from the reviewed source evidence.
    lines=[
      'V12.4 — существенные различия сохранённых типов',
      'Исходный commit: '+manifest['baselineCommit'],
      f"Новые рабочие типы: {counts['newWorkingTypesBefore']} → {counts['newWorkingTypesAfter']}; Creative: {counts['newCreativeStacksBefore']} → {counts['newCreativeStacksAfter']}.",
      'Оригинальная V12 защищена и не участвует в объединении.',
      '',
      'Материал и рисунок поверхности (после одинакового положения):',
    ]
    for pair in decisions['significantRetainedDifferences']:
        ids=pair.get('ids',pair.get('pair',[]))
        if len(ids)!=2:raise ValueError(pair)
        if canon(ids[0])==canon(ids[1]):continue
        reason=pair.get('reason',pair.get('retainReason'))
        lines.append(f"{ids[0]} ({name(ids[0])}) / {ids[1]} ({name(ids[1])}): {reason}")
    lines+=['','Сохранённые 19 материалов функциональных ступеней:',
            'Каждая строка описывает рисунок всей строительной поверхности. Соседние формы и ориентации внутри строки объединены.']
    for i,material in STAIR_MATERIALS.items():lines.append(i+' ('+name(i)+'): '+material+'.')
    lines+=['','Объекты на общих атласах с заметно разными размерами и назначением:',
        'Размеры указаны по видимой модели в блоках после приведения к общему повороту. Это описание размера установленного объекта, а не причина на основе пути файла.',
        'У объединённых объектов ниже показан только основной ID. Одна общая текстура не превращает крупную арку с проходом, плоскую напольную плитку и свечу в один функциональный объект.']
    retained_atlas=[]
    for group in general['retainedMaterialGroups']:
        representatives={}
        for row in group['members']:
            i=canon(row['id'])
            representatives.setdefault(i,row)
        if len(representatives)<2:continue
        descriptions=[]
        for i,row in sorted(representatives.items()):
            dims=row['visibleDimensionsBlocks']
            descriptions.append(i+' «'+name(i)+'» — '+ ' × '.join(f'{v:g}' for v in dims)+' блока')
        retained_atlas.append({'members':list(representatives),'descriptions':descriptions})
        lines.append(' ; '.join(descriptions)+'.')
    lines+=['','Проверка охвата и границы доказательств:',
      'Инвентаризация охватывает все 1802 исходных состояния 1045 новых регистраций, включая не используемые на исходной карте.',
      'Сравнение учитывало фактические видимые грани, UV, прозрачность и содержимое текстур, восемь направлений, перенос и повороты 45°. Дополнительный проход не ограничивался общим атласом.',
      'Просмотрены full-review-sheet-00..35, selected-full-review-00..02, near-review-sheet-01..06 и материалы ступеней. Числовая близость проекций сама по себе не служит решением объединить.',
      'Диагностические проекции — программный анализ исходных поверхностей. Окончательная проверка нативных кадров до/после записывается отдельно; этот отчёт не объявляет её PASS.',
    ]
    (OUT/'RETAINED-DIFFERENCES-RU.txt').write_text('\n'.join(lines)+'\n',encoding='utf-8-sig')
    remaining=[]
    for row in general['retainedNearPairs']:
        a,b=row['a'],row['b']
        if canon(a['id'])==canon(b['id']):continue
        remaining.append({'a':a,'b':b,'canonicalA':canon(a['id']),'canonicalB':canon(b['id']),
            'diagnosticImage':row.get('diagnosticImage'),'pixelDifference':row['pixelDifference']})
    (OUT/'retained-near-pairs-to-document.json').write_text(json.dumps(remaining,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    report={'status':'REPORTS_WRITTEN_PENDING_NATIVE_REVIEW','groups':len(merge_rows),'mappings':len(state_rows),
      'sourceManifestSha256':hashlib.sha256((OUT/'type-state-mapping.json').read_bytes()).hexdigest(),
      'retainedSharedAtlasGroups':len(retained_atlas),'retainedNearRowsForAdditionalConcreteNotes':len(remaining)}
    (OUT/'readable-report-export.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
    assert len(merge_rows)==len(manifest['groups']) and len(state_rows)==len(manifest['mappings'])

if __name__=='__main__':
    try:main()
    except Exception:
        (OUT/'readable-report-export-error.txt').write_text(traceback.format_exc(),encoding='utf-8');raise
