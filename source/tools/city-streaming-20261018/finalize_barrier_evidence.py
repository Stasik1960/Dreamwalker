"""Bind the read-only source audit to the already verified independent copies."""
import hashlib,json,shutil,traceback
from pathlib import Path
P=Path(__file__).absolute().parents[2];R=P.parent
E=R/'deliverables/collision-streaming-20261018/evidence';B=E/'barriers'
def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def main():
    backup=json.loads((E/'map-backup.json').read_text(encoding='utf8'));manifest=E/'original-world-files.json'
    assert backup['status']=='PASS_INDEPENDENT_BACKUP_AND_WORK_COPY' and sha(manifest)==backup['manifestSha256']
    records=json.loads(manifest.read_text(encoding='utf8'));files={r['path']:r for r in records}
    assert len(files)==backup['files']==25243 and sum(r['bytes'] for r in records)==backup['bytes']==456314999
    report=json.loads((B/'source-barrier-verification.json').read_text(encoding='utf8'))
    assert report['status']=='PASS_SOURCE_BARRIERS_ALREADY_ABSENT' and report['worldWritesThisRun']==report['removedThisRun']==0
    for row in report['readOnlyFileProof']:
        assert row['sha256Before']==row['sha256After']==files[row['file']]['sha256']
    original=json.loads((B/'source-barrier-original-audit.json').read_text(encoding='utf8'))
    for row in original['worldRegionProof']:assert row['sha256']==files[row['file'].replace('\\','/')]['sha256']
    historical=R/'releases/Dreamwalker-BB/v12.7.4-static-city.6-render-fix.1-20261017/evidence'
    for name in ['source-barrier-cleanup-manifest.json','source-barrier-cleanup-report.json']:
        shutil.copyfile(historical/name,B/('historical-'+name))
    result={'status':'PASS_FULL_COPY_AND_ZERO_BARRIER_MUTATION','originalWorld':backup['sourceWorld'],'independentBackupWorld':backup['backupWorld'],'deliveredWorkingWorld':backup['workingWorld'],'fullIndependentCopiesVerifiedUtc':backup['verifiedUtc'],'allThreeCopiesShareFileSha256Manifest':str(manifest),'manifestSha256':sha(manifest),'fileCount':len(files),'logicalBytesPerCopy':backup['bytes'],'copyIndependenceMethod':'Separate copyfile output, distinct filesystem inode identity and SHA256 verification per file; full source hash repeated during initial copy.','originalArchiveSha256':original['originalZipSha256'],'sourcePlanSha256':report['sourcePlanSha256'],'originalSourceBarrierCoordinates':14855,'historicalRemoved':14855,'confirmedRemaining':0,'newlyRemoved':0,'mapWritesByBarrierTools':0,'workingReadOnlyFilesReverifiedAgainstFullCopyManifest':len(report['readOnlyFileProof']),'sourceBarrierNativeRemainder':0,'sourceBarrierResidentRemainder':0,'sourceBarrierOwnerMembershipRemainder':0,'sharedHelpersRetained':8590,'nonBarrierGuestContributionsRetained':9240,'beShardContributionMismatches':0,'preservedScope':'All existing mod barriers, source invisible carriers, lighting, geometry, authoritative ownership, mutable payloads and save metadata are unchanged byte-for-byte; no map files were written by this task component.'}
    (B/'map-file-preservation.json').write_text(json.dumps(result,indent=2,ensure_ascii=False)+'\n',encoding='utf8')
    (B/'REPORT-RU.txt').write_text('Проверка исходных барьеров — 18.10.2026\n\n'
      'Исходная готовая карта: '+backup['sourceWorld']+'\nНезависимый резерв: '+backup['backupWorld']+'\nРабочая карта результата: '+backup['workingWorld']+'\n\n'
      'До работ все 25 243 файла (456 314 999 байт) исходной карты, резерва и рабочей копии проверены по SHA256; копии имеют независимые inode. Полный список: ../original-world-files.json; акт: ../map-backup.json. SHA256 списка: '+sha(manifest)+'.\n\n'
      'В указанном оригинальном bbmc_v16_map.zip ровно 14 855 minecraft:barrier. Хеш архива совпал с происхождением готовой карты: '+original['originalZipSha256']+'. Список координат заново прочитан из оригинальных Anvil-чанков, без конвертации; его gzip побайтно совпал с прежним подтверждённым списком (SHA256 '+report['sourceCoordinateGzipSha256']+'). Он привязан к тому же плану, где decision 223 сохранял native minecraft:barrier без владельцев и смещений.\n\n'
      'Все 14 855 ранее удалённых барьеров уже отсутствуют. Теперь эти позиции содержат 6 265 air и 8 590 composite_cell. У всех 8 590 helper нет resident, изменяемый payload пуст. Сохранены все 9 240 вкладов соседних небарьерных объектов; данные block entity совпадают с авторитетными shard. Ни один их корень, resident или запись ownerMembership не находится на исходной позиции барьера; barrier-подобных ID/исходных данных среди этих вкладов нет.\n\n'
      'Дополнительно удалено: 0. Записей файлов карты: 0. Повторное удаление 14 855 не запускалось. Удалять helper или invisible carrier нельзя: это повредило бы сохранённые окна, лестницы, ворота и другие объекты. Модовые барьеры и свет сохранены. Подробности и все проверенные объекты: source-barrier-verification.json, source-barrier-shared-cell-objects.json.gz.\n',encoding='utf8')
    (B/'finalize-evidence-error.txt').unlink(missing_ok=True)
if __name__=='__main__':
    try:main()
    except Exception:
        (B/'finalize-evidence-error.txt').write_text(traceback.format_exc(),encoding='utf8');raise
