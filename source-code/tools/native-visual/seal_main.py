"""Seal the accepted base pair before optional shader work; original stays intact."""
import datetime,hashlib,json,os,shutil,traceback,zipfile
from pathlib import Path
P=Path(__file__).resolve().parents[2];T=P.parent.parent/'DW-visual-physics-20261024'
def read(p):return json.loads(p.read_text(encoding='utf-8-sig'))
def write(p,n):p.write_text(json.dumps(n,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
def sha(p):
    h=hashlib.sha256()
    with p.open('rb') as s:
        for b in iter(lambda:s.read(1<<20),b''):h.update(b)
    return h.hexdigest()
def ref(p):return {'path':str(p),'sha256':sha(p)}
def main():
    assert read(T/'runs/full-1/receipt.json')['status']==read(T/'runs/reopen-1/receipt.json')['status']=='PASS'
    assert read(T/'full-metrics.json')['coverage']['complete']
    assert read(T/'full-visual-review.json')['status']=='ACCEPTED_WITH_RECORDED_VISUAL_SCOPE'
    proof=read(T/'client-layer-change-proof.json');digest=proof['after']['sha256'];jar=Path(proof['after']['path']);assert sha(jar)==digest
    assert read(T/'runs/full-1/receipt.json')['productionSha256']==read(T/'runs/reopen-1/receipt.json')['productionSha256']==digest
    assert read(T/'server-checks.json')['status']=='PASS_TWO_REAL_SERVER_VMS_SAVE_REOPEN'
    assert read(T/'integration-checks.json')['status']=='PASS_HELPER_AND_WHOLE_INTEGRATION_WITH_SAVED_DROP_EVIDENCE'
    migration=read(T/'map-remap.json');assert migration['exactPhysicsAndOptics'] and migration['lightingAndBEDataUnchanged'] and migration['protectedAndWholeStatesUnchanged']
    stage=T/'delivery/base';world=stage/'bloodborne-native-grid-city';world.mkdir(parents=True,exist_ok=True)
    manifest={}
    for root,dirs,files in os.walk(T/'world',followlinks=False):
        for name in [*dirs,*files]:
            path=Path(root)/name
            assert not path.lstat().st_file_attributes&0x400,'Reparse point not followed: '+str(path)
        for name in files:
            source=Path(root)/name;relative=source.relative_to(T/'world');target=world/relative;expected=sha(source)
            target.parent.mkdir(parents=True,exist_ok=True)
            if not target.exists():shutil.copy2(source,target)
            assert sha(target)==expected,relative
            manifest[relative.as_posix()]=expected
    output=stage/'dreamwalker-bb-0.1.0-v12.7.4-visual-physics.1.jar'
    if not output.exists():shutil.copy2(jar,output)
    assert sha(output)==digest
    marker={'status':'READY_BASE_VISUAL_PHYSICS_V1','schema':1,'catalogSha256':read(T/'layout-report.json')['catalogSha256'],
        'runtimeSha256':digest,'lightDebtFromThisMigration':0,'exactPhysicsAndOpticsPreserved':True,
        'wholeObjectsAndV12Preserved':True,'fullRouteAndColdGraphicalReopenPassed':True,
        'note':'Use with the matching visual-physics mod on client and server. The older NATIVE-GRID-MIGRATION marker remains as predecessor history.'}
    write(world/'NATIVE-VISUAL-READY.json',marker)
    (stage/'INSTALL-RU.txt').write_text('Готовая базовая пара без шейдеров.\nКарта: bloodborne-native-grid-city.\nУстановите единственный Dreamwalker JAR visual-physics.1 из этой папки и на клиент, и на сервер, сохранив остальные моды вашей сборки. Старый Dreamwalker JAR убрать из активной папки mods. Используйте карту и мод вместе.\nСтарую native-grid карту новым модом без миграции не открывать; готовую новую карту повторно не конвертировать.\nСвет, точные коллизии, v12 и цельные конструкции сохранены; полный106-видовый маршрут и отдельное холодное открытие пройдены.\nИсходники: DW-native-grid-city-20261022/source; окончательная упаковка и обновление Desktop выполняются после дополнительного этапа Iris.\nОтчёт и решения: DW-visual-physics-20261024/REPORT-DRAFT-RU.txt и DECISIONS-RU.md.\n',encoding='utf-8')
    evidence=[T/n for n in ('map-remap.json','region-repack.json','server-checks.json','integration-checks.json','client-layer-change-proof.json','comparison.json','full-metrics.json','full-resources.json','full-visual-review.json','runs/full-1/receipt.json','runs/reopen-1/receipt.json')]
    result={'status':'MAIN_BASE_PAIR_READY_OPTIONAL_ADDONS_PENDING','utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),
        'world':str(world),'worldFilesBeforeReadyMarker':manifest,'jar':ref(output),'evidence':[ref(p) for p in evidence],
        'desktopOriginalNotReplaced':True,'pending':['Iris/lightweight shader research and compatibility test','Streaming scheduling analysis only','Final packaging and Desktop installation']}
    write(T/'main-ready.json',result)
    review=read(T/'full-visual-review.json');review['coldReopenPending']=False;review['coldReopenReceipt']=ref(T/'runs/reopen-1/receipt.json');write(T/'full-visual-review-after-reopen.json',review)
if __name__=='__main__':
    try:main()
    except BaseException:
        (T/'seal-main-error.txt').write_text(traceback.format_exc(),encoding='utf-8');raise
