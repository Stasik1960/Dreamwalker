"""Publish a verified gallery-city upgrade; region payloads remain unchanged."""
from __future__ import annotations
import argparse, hashlib, io, json, zipfile, os, shutil, tempfile
from collections import Counter
from pathlib import Path
from publish_launch_bundle import public_world
from world_io import compound,decode_nbt,encode_nbt,Tag,TAG_STRING
from convert_logical_world import hash_tree

def package(world,report_path,verification_path,output):
    world,report_path,verification_path,output=(Path(p).resolve() for p in (world,report_path,verification_path,output))
    if output.exists() or world==output or world in output.parents:raise ValueError('release output must be new and outside world')
    report_bytes=report_path.read_bytes()
    report=json.loads(report_bytes);verification=json.loads(verification_path.read_bytes())
    if report['status']!='complete' or not verification['passed'] or report['followUpPasses'][-1]!=0:raise ValueError('completed migration and saved-world verification required')
    if verification.get('targetHashes')!=hash_tree(world):raise ValueError('verification is not bound to this saved world')
    if verification.get('migrationReportSha256')!=hashlib.sha256(report_bytes).hexdigest():raise ValueError('verification is not bound to this migration report')
    # Every operation unlocked later is explicitly reviewable, including cases
    # where an overlapping source candidate only became disputed in that pass.
    late=sum(report['followUpPasses'])
    follow_up_choices=[{**r,'reason':'storage_root_unlocked_by_prior_conversion_review_source_overlap'}
                       for r in (report['converted'][-late:] if late else [])]
    review_groups={**report,'followUpChoices':follow_up_choices}
    cases=report['rejected']+report['overlappingChoices']+report['ownerChoices']+report['documentChoices']+follow_up_choices
    manifest=json.loads((world/'gallery-manifest.json').read_bytes())
    manifest['cityCases']=cases;manifest['helperAudit']={'ok':True,'checked':verification['helpers']['checked'],'orphans':[]}
    manifest['cityUpgrade']['verification']=verification
    review=['# Проверка обновлённого города','','Отдельная Main-City не изменена. Площадки галереи сохранены; промежутки между ними — 3 блока.','',
            '| Тип | Объект | Причина | Переход |','|---|---|---|---|']
    for kind,key in [('остаток','rejected'),('пересекающийся шаблон','overlappingChoices'),('выбор модели','ownerChoices'),('пункт документа','documentChoices'),('повторный проход','followUpChoices')]:
        review.extend(f'| {kind} | {r["target"]} | {r["reason"]} | `{r["tp"]}` |' for r in review_groups[key])
    review_text='\n'.join(review)+'\n'
    overrides={'gallery-manifest.json':(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n').encode('utf8'),
               'Review-Cases.md':review_text.encode('utf8'),
               'Gallery-Index.md':('# Обновлённая городская часть\n\nГород отличается от отдельной Main-City. Текущие спорные места: Review-Cases.md. Полный список замен: City-Upgrade.md.\n\n'+(world/'Gallery-Index.md').read_text(encoding='utf8')).encode('utf8')}
    nbt=decode_nbt((world/'level.dat').read_bytes(),compressed='gzip')
    compound(compound(nbt.root)['Data'])['LevelName']=Tag(TAG_STRING,'Bloodborne Gallery - Updated City 2026-10-02')
    overrides['level.dat']=encode_nbt(nbt,compressed='gzip')
    root='Approval-Gallery-Updated-City';raw=io.BytesIO()
    with zipfile.ZipFile(raw,'w',zipfile.ZIP_DEFLATED,compresslevel=2) as archive:
        for path in sorted(world.rglob('*')):
            if path.is_file():
                rel=path.relative_to(world).as_posix();archive.writestr(root+'/'+rel,overrides.get(rel,path.read_bytes()))
    payload,publication=public_world(raw.getvalue())
    digest=hashlib.sha256(payload).hexdigest()
    counts=report['counts'];target_counts=Counter(r['target'] for r in report['converted'])
    notes=f'''# Город в галерее — обновление 02.10.2026

Заменено {counts['converted']} сборок; {counts['rejected']} полных исходных шаблонов остаются заблокированы.
Это число шаблонов правил, а не обязательно число уникальных предметов.
Неполные/неоднозначные остатки не считаются доказанными цельными объектами и сохранены.
Повторные проходы: {report['followUpPasses']}. Проверяемые решения перечислены в Review-Cases.md.

Скачать Approval-Gallery-Updated-City.zip, распаковать содержащуюся в нём папку в saves.
Название мира: Bloodborne Gallery - Updated City 2026-10-02.
Использовать прежний мод 2.1.0-repair-catalog.1 и зависимости из launch-base-2026-10-01.
При необходимости подключить прежний осенний ресурс-пак. Новый JAR для этой карты не требуется.
Первая площадка: /tp @s 16387 66 16. Площадки и промежутки в 3 блока сохранены.
Город расположен на исходных координатах; теперь он отличается от отдельной Main-City.
Многие замены сохраняют внешний вид, меняя сборку из деталей на единый объект.

Сохранённый мир проверен: {verification['stats']['yuushyaBlocks']} блоков Yuushya,
{verification['stats']['foreignBlocks']} сторонних блоков, {verification['stats']['foreignBlockEntities']} сторонних block entities;
все сохранены. Некорректных registry states и осиротевших helpers: 0.
Проверено {verification['helpers']['checked']} helper-carrier записей.
Штатный check/build и отдельные тесты конвертера пройдены; игрового просмотра этой копии ещё не было.
Публикация очищена стандартным public_world: {publication}.
'''
    with zipfile.ZipFile(io.BytesIO(payload)) as archive:
        if archive.testzip():raise ValueError('archive CRC failure')
        for path in world.glob('**/*.mca'):
            if archive.read(root+'/'+path.relative_to(world).as_posix())!=path.read_bytes():raise ValueError('region payload changed while packaging')
    if verification['targetHashes']!=hash_tree(world):raise ValueError('world changed while packaging')
    output.parent.mkdir(parents=True,exist_ok=True)
    staged=Path(tempfile.mkdtemp(prefix='gallery-package-',dir=output.parent))
    try:
        (staged/'Approval-Gallery-Updated-City.zip').write_bytes(payload)
        (staged/'Gallery-Upgrade-Report.json').write_bytes(report_bytes)
        (staged/'World-Verification.json').write_text(json.dumps(verification,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
        (staged/'Review-Cases.md').write_text(review_text,encoding='utf8')
        (staged/'City-Upgrade.md').write_bytes((world/'City-Upgrade.md').read_bytes())
        (staged/'SHA256.txt').write_text(digest+'  Approval-Gallery-Updated-City.zip\n',encoding='ascii')
        (staged/'README.md').write_text(notes,encoding='utf8')
        os.replace(staged,output)
    finally:
        if staged.exists():shutil.rmtree(staged)
    return {'sha256':digest,'counts':counts,'topTargets':target_counts.most_common(5),'publication':publication}

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('world',type=Path);p.add_argument('report',type=Path);p.add_argument('verification',type=Path);p.add_argument('output',type=Path)
    a=p.parse_args();print(json.dumps(package(a.world,a.report,a.verification,a.output)))
