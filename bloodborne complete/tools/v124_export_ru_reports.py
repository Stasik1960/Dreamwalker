"""Export readable V12.4 decisions from the applied manifest; no production writes."""
from pathlib import Path
from collections import defaultdict
import csv, hashlib, io, json, math, traceback

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
 '90686':'Красноватая мангровая древесина с горизонтальными волокнами, заметно краснее коричневых досок 90862',
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
def write_if_changed(path,content):
    if not path.exists() or path.read_bytes()!=content:path.write_bytes(content)
def logical_pose(mapping,kind):
    value=int(mapping['newVariant']);props=mapping.get('properties',{})
    if kind=='artwork':return value+16*int(props.get('art_bank',0))
    if kind=='stairs' and str(props.get('trim','false')).lower()=='true':return value+10
    return value
def write_csv(name, headers, rows):
    f=io.StringIO(newline='');writer=csv.writer(f,delimiter=';');writer.writerow(headers);writer.writerows(rows)
    write_if_changed(OUT/name,f.getvalue().encode('utf-8-sig'))

def main():
    manifest=load('type-state-mapping.json');frozen=load('baseline-content.json')
    inventory=load('visual-candidates.json')['inventory']
    general=load('general-functional-groups.json');decisions=load('reviewed-global-decisions.json')
    counts=load('type-counts.json')
    oldrows={row['registryId']:row for row in frozen['debugCatalogue']['entries']}
    by_id={str(row['temporaryId']):row for row in oldrows.values()}
    current=json.loads((ROOT/'src/architecture/resources/bloodborne_dw/debug_catalogue.json').read_text(encoding='utf-8'))
    current_by_id={str(row['temporaryId']):row for row in current['entries']}
    legacy_art=json.loads((ROOT/'src/architecture/resources/bloodborne_dw/imported-stair-legacy-art.json').read_text(encoding='utf-8'))
    legacy_keys={r['key'] for r in legacy_art['states']}
    sources={(row['id'],int(row['variant'])):row for row in inventory}
    group_by_id={row['primaryId']:row for row in manifest['groups']}
    canonical={oldrows[r]['temporaryId']:g['primaryId'] for g in manifest['groups'] for r in g['members']}
    canonical={str(k):v for k,v in canonical.items()}
    mappings=defaultdict(list)
    for row in manifest['mappings']: mappings[row['newId']].append(row)
    for group in manifest['groups']:
        group.setdefault('poseCount',max(logical_pose(m,group['kind']) for m in mappings[group['primaryId']])+1)
    general_groups={g['mainId']:g for g in general['groups']}
    def name(i): return current_by_id[str(i)]['name']
    def oldname(i): return by_id[str(i)]['name']
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
             ' | '.join(i+': '+oldname(i) for i in member_ids),group['poseCount'],
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
        art_key=mapping['oldRegistryId']+'|'+str(mapping['oldVariant'])
        state_rows.append([mapping['oldId'],source.get('name',oldname(mapping['oldId'])),mapping['oldRegistryId'],mapping['oldVariant'],
             mapping['newId'],name(mapping['newId']),mapping['newRegistryId'],pose,compact(props),
             mapping['rotationDelta'],mapping['rotationDelta']*45,compact(mapping['sourceShift']),
             '(old.rotation + rotationDelta) mod 8',
             'old.SourceShift.rotate(-rotationDelta) + sourceShift; Shift.rotate использует поворот Minecraft -45° за шаг',
             'Сохраняются mount_y, vertical_mount, profile/open и NBT экземпляра: UUID, настройки, связи, события; старые ступени — connections=manual, новые автоматически соединяются',
             art_key if art_key in legacy_keys else '',
             'Ключ подтверждённых исходных частей сохраняет прежние UV/вид; форма и физика соответствуют текущему состоянию ступеней' if art_key in legacy_keys else ''])
    write_csv('OLD-ID-STATES.csv',
       ['Старый ID','Исходный вариант — название','Старая регистрация','Старый variant',
        'Основной ID','Название рабочего типа','Новая регистрация','Логический номер позы',
        'Назначаемые свойства JSON','Добавка поворота — шаги 45°','Добавка поворота — градусы',
        'Добавка SourceShift — координаты нового типа','Формула rotation','Формула SourceShift','Сохраняемые данные','Ключ прежнего рисунка ступеней','Назначение ключа рисунка'],state_rows)
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
    lines+=['','Общие атласы проверялись как перечень кандидатов. Сам путь ресурса, размер файла или совпадение атласа не служили основанием сохранять отдельный тип. Конкретные подозрительные пары перечислены ниже.']
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
    concrete={}
    for r in decisions['significantRetainedDifferences']:
        a,b=r['ids'];concrete[frozenset((canon(a),canon(b)))]= {'ids':[canon(a),canon(b)],'sourceIds':[a,b],'reason':r['reason']}
    suspect=[];missing=[]
    extra=load('additional-native-review-fixtures.json')
    for r in extra['fixtures']:
        a,b=canon(r['oldId']),canon(r['mainId'])
        if a==b:continue
        key=tuple(sorted((a,b)));evidence=concrete.get(frozenset((a,b)))
        display_ids=evidence['ids'] if evidence else list(key)
        reason=evidence['reason'] if evidence else None
        source_note=''
        if evidence and evidence['sourceIds']!=display_ids:
            source_note=' Проверенные исходные ID: '+', '.join(old+' → '+new for old,new in zip(evidence['sourceIds'],display_ids))+'.'
        if a in STAIR_MATERIALS and b in STAIR_MATERIALS:
            display_ids=list(key)
            reason=STAIR_MATERIALS[key[0]]+'; у другого типа — '+STAIR_MATERIALS[key[1]]+'. Различие занимает всю поверхность ступеней и остаётся после одинакового положения.'
            source_note=''
        if any(x['comparisonKey']==list(key) for x in suspect):continue
        if not reason:missing.append({'ids':[a,b],'fixture':r})
        # Keep the displayed first/second object aligned with the concrete
        # observation. Sorting IDs for deduplication must not invert a reason
        # that describes the first object's material and the second's.
        suspect.append({'canonicalIds':display_ids,'comparisonKey':list(key),'reason':(reason+source_note) if reason else 'REVIEW_REQUIRED'})
    lines+=['','Все оставленные раздельными подозрительные группы из нативного сравнения:']
    for r in suspect:
        a,b=r['canonicalIds'];lines.append(a+' «'+name(a)+'» / '+b+' «'+name(b)+'»: '+r['reason'])
    if missing:raise ValueError('Missing concrete native retained rationale: '+compact(missing))
    lines+=['','Проверка охвата и границы доказательств:',
      'Инвентаризация охватывает все 1802 исходных состояния 1045 новых регистраций, включая не используемые на исходной карте.',
      'Сравнение учитывало фактические видимые грани, UV, прозрачность и содержимое текстур, восемь направлений, перенос и повороты 45°. Дополнительный проход не ограничивался общим атласом.',
      'Просмотрены full-review-sheet-00..35, selected-full-review-00..02, near-review-sheet-01..06 и материалы ступеней. Числовая близость проекций сама по себе не служит решением объединить.',
      'Нативный предварительный просмотр охватил все 1802 состояния, 91 подозрительную пару и 200 новых состояний ступеней. Каждый исходный объект просмотрен по настоящей запечённой модели Minecraft; окончательная проверка кадров до/после привязана к последнему JAR и записывается отдельно.',
    ]
    native_path=OUT/'native-visual-final.json';native_evidence=None
    if native_path.exists():
        native=load('native-visual-final.json');audit=load('resource-audit.json')
        if native.get('status')=='PASS' and audit.get('status')=='PASS' and native['productionJarSha256']==audit['productionJarSha256']:
            native_evidence={'fileName':native_path.name,'sha256':hashlib.sha256(native_path.read_bytes()).hexdigest(),
                'productionJarSha256':native['productionJarSha256'],'reviewedFrames':native['reviewedFrames']}
            lines.append('Итоговая нативная проверка — PASS: '+str(native['reviewedFrames'])+' кадров, SHA-256 JAR '+native['productionJarSha256']+'. Полные результаты и подтверждённые прозрачные исходные состояния: native-visual-final.json.')
    write_if_changed(OUT/'RETAINED-DIFFERENCES-RU.txt', ('\n'.join(lines)+'\n').encode('utf-8-sig'))
    remaining=[]
    for row in general['retainedNearPairs']:
        a,b=row['a'],row['b']
        if canon(a['id'])==canon(b['id']):continue
        remaining.append({'a':a,'b':b,'canonicalA':canon(a['id']),'canonicalB':canon(b['id']),
            'diagnosticImage':row.get('diagnosticImage'),'pixelDifference':row['pixelDifference']})
    write_if_changed(OUT/'retained-near-pairs-to-document.json',(json.dumps(remaining,ensure_ascii=False,indent=2)+'\n').encode('utf-8'))
    report={'status':'REPORTS_WRITTEN_WITH_BOUND_NATIVE_EVIDENCE' if native_evidence else 'REPORTS_WRITTEN_FINAL_JAR_NATIVE_REVIEW_PENDING','groups':len(merge_rows),'mappings':len(state_rows),
      'sourceManifestSha256':hashlib.sha256((OUT/'type-state-mapping.json').read_bytes()).hexdigest(),
      'retainedSharedAtlasGroups':len(retained_atlas),'concretelyExplainedNativeSuspectGroups':len(suspect),'missingConcreteReasons':missing,'retainedNearRowsForAdditionalConcreteNotes':len(remaining),'preservedLegacyStairArtworkKeys':len(legacy_keys)}
    if native_evidence:report['boundNativeEvidence']=native_evidence
    write_if_changed(OUT/'readable-report-export.json',(json.dumps(report,indent=2)+'\n').encode('utf-8'))
    assert len(merge_rows)==len(manifest['groups']) and len(state_rows)==len(manifest['mappings'])

if __name__=='__main__':
    try:main()
    except Exception:
        (OUT/'readable-report-export-error.txt').write_text(traceback.format_exc(),encoding='utf-8');raise
