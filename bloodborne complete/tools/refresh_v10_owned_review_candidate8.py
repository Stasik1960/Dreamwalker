"""One-time 7→8 guide/argv rebinding; refuse to overwrite later current edits.

No world/JAR/ZIP reads or runtime execution. Previous bytes remain in v10-history.
"""
import copy
import hashlib
import json
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
SHA='884ba57ce8ba0a64e3fdc5dd8730c3945a44d1a35f462cea39245301b2e69e75'


def preserve(path):
    raw=path.read_bytes();target=ROOT/'reports/v10-history'/f'{path.stem}-before-candidate8-{hashlib.sha256(raw).hexdigest()}{path.suffix}'
    target.parent.mkdir(parents=True,exist_ok=True)
    if target.exists():assert target.read_bytes()==raw
    else:target.write_bytes(raw)


def write(path,text):preserve(path);path.write_text(text,encoding='utf8')


def main():
    previous=json.loads((ROOT/'tools/package_review_v10_commands.json').read_text(encoding='utf8'))
    assert previous['production_sha256']=='2cf96fbe771bb44bba4e4e8f320ed65a8bc0a8ffe7a8149b07fbd9ec691592c1' and previous['native_tests']==131,'This one-time transition already ran, or the current source differs; no writes allowed.'
    freeze=json.loads((ROOT/'build/frozen-artifacts/v10-attempt-8/manifest.json').read_text(encoding='utf8'))
    assert freeze['sha256']==SHA and freeze['nativeTests']==133 and freeze['nativeFailures']==0
    for name in ['V10_SERVER_AUTHOR_MIN_ATTEMPT_7.json','V10_SERVER_PRODUCTION_ONLY_REOPEN_RELEASE5.json','V10_SERVER_FULL_OWNED_SCENE_REOPEN_RELEASE4.json']:
        row=json.loads((ROOT/'reports'/name).read_text(encoding='utf8'));assert row['status']=='PASS' and row['exit_code']==0 and row['artifact_sha256']==SHA
    path=ROOT/'docs/REVIEW_V10_INSTRUCTIONS.md';lines=path.read_text(encoding='utf8').splitlines()
    lines[4]=f'Текущий замороженный кандидат 8: `dreamwalker-bb-fabric-1.20.1-0.1.0-prototype.5.jar`, SHA256 `{SHA}`, 32 992 155 байт. [Native Attempt8](../reports/FIRST_SET_GAMETEST_V10_ATTEMPT_8.xml) прошёл 133/133 и 20 core checks. [Снимки геометрии](../reports/V10_ASYNC_SHAPE_SNAPSHOT_NATIVE_CANDIDATE8.json) убирают обращение рабочих потоков освещения к незавершённым FULL-чанкам и публикуют транзакцию после commit/rollback. [Побайтное сравнение ресурсов](../reports/V10_FROZEN_RESOURCE_DELTA_CANDIDATE7_TO8.json) подтвердило сохранение всех 965 ресурсных файлов, включая рисунки, UV и анимации. [Архитектурный аудит](../reports/V10_ARCHITECTURE_HEIGHT_AND_ART_CANDIDATE8.json) прошёл. [Author7](../reports/V10_SERVER_AUTHOR_MIN_ATTEMPT_7.json), [production-only reopen5](../reports/V10_SERVER_PRODUCTION_ONLY_REOPEN_RELEASE5.json) и [full dedicated owned-scene reopen4](../reports/V10_SERVER_FULL_OWNED_SCENE_REOPEN_RELEASE4.json) завершились штатно. Реальные клиентские AUTHOR/REENTER/full, средняя кнопка для всех 69 RP и семи old registry, отдельный диск-цикл aliases и общий диагностический proof этого SHA ещё ожидаются. Ручная оценка графики и удобства не заменяется автоматическими проверками.'
    lines[6]='Кандидаты 5–7 сохранены как история. MIN14 и REENTER3 кандидата 7 фактически прошли обычную постройку, дополнительные части, меню и сохранение, но полный клиент той версии завис при async освещении: [доказательство причины](../reports/V10_FULL_CLIENT3_LOADING_SHAPE_DEADLOCK_AUDIT.json). Эти PASS/FAIL не перенесены на новый SHA. Delivery-цепочка нового кандидата — pristine Author7 baseline → production-only reopen5. Конечный мир сохраняет три местных фонаря, удалённый D и три рычага без готовых GUI-правил/линий. Клиентские GUI-копии намеренно удаляют старые C/lever3 и служат отдельным доказательством сохранения правил. Окончательный архив ещё не создан.'
    lines[8]='При новом фактическом клиентском прогоне package gate требует обычные item/place/attack всех 18 архитектурных и 69 канонических RP-типов, native STONE с сохранением RP UUID, четыре дополнительных случая частей, реальную среднюю кнопку с сохранением настроек/имени и новым UUID при повторной установке, меню и штатный save/exit. Техническое создание старого alias-экземпляра не считается доказательством его установки пользователем. Метрики CPU/кадров и параллельная проверка совместимости не доказывают причинное влияние записи на FPS.'
    write(path,'\n'.join(lines)+'\n')
    path=ROOT/'docs/REVIEW_V10_ARCHITECTURE_HEIGHT_AND_GLASS.md';lines=path.read_text(encoding='utf8').splitlines()
    lines[4]=f'Текущий production-кандидат 8: `0.1.0-prototype.5`, SHA256 `{SHA}`. [Attempt8](../reports/FIRST_SET_GAMETEST_V10_ATTEMPT_8.xml) завершился 133/133 native и 20 core checks; четыре height/Creative проверки прошли. [Статический аудит](../reports/V10_ARCHITECTURE_HEIGHT_AND_ART_CANDIDATE8.json) связан с этим JAR/XML. [Все художественные ресурсы](../reports/V10_FROZEN_RESOURCE_DELTA_CANDIDATE7_TO8.json) совпали с кандидатом 7. Author7, pristine production-only reopen5 и full dedicated owned-scene reopen4 прошли; новые клиентские AUTHOR/REENTER/full и архив пока ожидаются. Кандидаты 5–7 остаются историей; текущий snapshot fix не меняет art/UV/физическую функцию монтажа.'
    text='\n'.join(lines)+'\n';text=text.replace('статический аудит кандидата 7','статический аудит кандидата 8').replace('V10_ARCHITECTURE_HEIGHT_AND_ART_CANDIDATE7.json','V10_ARCHITECTURE_HEIGHT_AND_ART_CANDIDATE8.json');write(path,text)
    path=ROOT/'docs/REVIEW_V10_DUPLICATE_SUMMARY.md';write(path,(ROOT/'docs/REVIEW_V10_DUPLICATE_SUMMARY_CANDIDATE8.md').read_text(encoding='utf8'))
    path=ROOT/'docs/REVIEW_V10_DUPLICATES.md';text=path.read_text(encoding='utf8').replace('2cf96fbe771bb44bba4e4e8f320ed65a8bc0a8ffe7a8149b07fbd9ec691592c1',SHA).replace('Native131','Native133').replace('CANDIDATE7','CANDIDATE8')
    text=text.replace('[Краткий архитектурный JSON](../reports/V10_ARCHITECTURE_DUPLICATE_SUMMARY_CANDIDATE8.json)','[Краткий общий JSON](../reports/V10_CONSTRUCTION_DUPLICATE_AUDIT_SUMMARY_CANDIDATE8.json)');write(path,text)
    path=ROOT/'docs/REVIEW_V10_CATALOGUE_SCOPE.md';text=path.read_text(encoding='utf8')
    text=text.replace('Полное обычное item/place/native-inside-RP/attack прохождение всех 69 RP-типов пока не доказано.','Кандидат 7 позднее прошёл все 69 типов в MIN14/REENTER3; это исторический результат того SHA, а не новый клиентский PASS кандидата 8.')
    text=text.replace('это предложение QA-позы, которому ещё нужны реальные ввод и BlockHitResult.','это предложение QA-позы впоследствии проверено реальным вводом кандидата 7; новый runtime кандидата 8 остаётся отдельным обязательным результатом.')
    text=text[:text.index('Кандидат 7 заморожен после исправления motion envelope:')]+f'Текущий кандидат 8 заморожен после исправления async shape reads: SHA256 `{SHA}`, 133/133 native +20 core. Author7/reopen5/full-owned-scene4 прошли штатно. Все 965 ресурсных файлов JAR совпали с кандидатом 7. Новый фактический клиентский proof всех 18/69 item/pick/place/attack, меню/сохранения, full Kappa и отдельных alias disk phases пока ожидается. Инвентаризация 118 TEMP и аудит 87 канонических строительных типов не завершают исходный каталог сборок или преобразование города.\n';write(path,text)
    path=ROOT/'tools/package_review_v10_commands.json';old=json.loads(path.read_text(encoding='utf8'));preserve(path);new=copy.deepcopy(old)
    mapping={
        'v10-attempt-7':'v10-attempt-8','FIRST_SET_GAMETEST_V10_ATTEMPT_7.xml':'FIRST_SET_GAMETEST_V10_ATTEMPT_8.xml',
        'V10_SERVER_AUTHOR_MIN_ATTEMPT_6.json':'V10_SERVER_AUTHOR_MIN_ATTEMPT_7.json',
        'V10_CLIENT_MIN_ATTEMPT_14.json':'V10_CLIENT_MIN_ATTEMPT_15.json',
        'V10_CLIENT_REENTER_RELEASE3_ATTEMPT_1.json':'V10_CLIENT_REENTER_RELEASE4_ATTEMPT_1.json',
        'V10_CLIENT_FULL_RELEASE3_ATTEMPT_1.json':'V10_CLIENT_FULL_RELEASE4_ATTEMPT_1.json',
        'V10_SERVER_PRODUCTION_ONLY_REOPEN_RELEASE4.json':'V10_SERVER_PRODUCTION_ONLY_REOPEN_RELEASE5.json',
        'runtime-server-v10-production-only-reopen-release4':'runtime-server-v10-production-only-reopen-release5',
        'Review-v10-pristine-scene-release3.zip':'Review-v10-pristine-scene-release4.zip',
        'REVIEW_V10_SCENE_ARCHIVE_RELEASE3.json':'REVIEW_V10_SCENE_ARCHIVE_RELEASE4.json',
        'REVIEW_V10_DIAGNOSTICS_RELEASE3.json':'REVIEW_V10_DIAGNOSTICS_RELEASE4.json',
        'V10_PRISTINE_BASELINE_REOPEN_INDEPENDENT_RELEASE4.json':'V10_PRISTINE_BASELINE_REOPEN_INDEPENDENT_RELEASE5.json',
        'V10_SERVER_FULL_RELEASE3.json':'V10_SERVER_FULL_OWNED_SCENE_REOPEN_RELEASE4.json',
        'DIAGNOSTICS_SERVER_DISABLED_V10_RELEASE3.json':'DIAGNOSTICS_SERVER_DISABLED_V10_RELEASE4.json',
        'DIAGNOSTICS_SERVER_ENABLED_V10_RELEASE3.json':'DIAGNOSTICS_SERVER_ENABLED_V10_RELEASE4.json',
        'DIAGNOSTICS_SERVER_REENTER_V10_RELEASE3.json':'DIAGNOSTICS_SERVER_REENTER_V10_RELEASE4.json',
        'DIAGNOSTICS_SERVER_TYPED_INDEPENDENT_V10_RELEASE3.json':'DIAGNOSTICS_SERVER_TYPED_INDEPENDENT_V10_RELEASE4.json',
        'V10_ARCHITECTURE_HEIGHT_AND_ART_CANDIDATE7.json':'V10_ARCHITECTURE_HEIGHT_AND_ART_CANDIDATE8.json',
        'V10_CONSTRUCTION_DUPLICATE_AUDIT_SUMMARY_CANDIDATE7.json':'V10_CONSTRUCTION_DUPLICATE_AUDIT_SUMMARY_CANDIDATE8.json',
        'docs/REVIEW_V10_ARCHITECTURE_DUPLICATE_AUDIT.md':'docs/REVIEW_V10_ARCHITECTURE_DUPLICATE_AUDIT_CANDIDATE8.md',
        'docs/REVIEW_V10_CROSS_MODULE_DUPLICATES.md':'docs/REVIEW_V10_CROSS_MODULE_DUPLICATES_CANDIDATE8.md',
        'V10_ALIAS_AUTHOR_RELEASE1.json':'V10_ALIAS_AUTHOR_RELEASE2.json',
        'V10_ALIAS_REENTER_RELEASE1.json':'V10_ALIAS_REENTER_RELEASE2.json',
        'V10_ALIAS_PRODUCTION_REOPEN_RELEASE1.json':'V10_ALIAS_PRODUCTION_REOPEN_RELEASE2.json',
        'V10_ALIAS_DISK_INDEPENDENT_RELEASE1.json':'V10_ALIAS_DISK_INDEPENDENT_RELEASE2.json',
        'build/delivery/v10-release3':'build/delivery/v10-release4'}
    def replace(value):
        if isinstance(value,str):
            for before,after in mapping.items():value=value.replace(before,after)
            return value
        if isinstance(value,list):return [replace(x) for x in value]
        if isinstance(value,dict):return {k:replace(v) for k,v in value.items()}
        return value
    new=replace(new)
    for key,value in old.items():
        if key.startswith('historical_'):new[key]=copy.deepcopy(value)
    new['historical_candidate7']={
        'sha256':old['production_sha256'],
        'preparedCommands':str((ROOT/'reports/v10-history'/f'{path.stem}-before-candidate8-{hashlib.sha256(path.read_bytes()).hexdigest()}{path.suffix}').relative_to(ROOT)),
        'actualCompleted':copy.deepcopy(old['actual_completed']),
        'fullClientFailure':'reports/V10_FULL_CLIENT3_LOADING_SHAPE_DEADLOCK_AUDIT.json',
        'reason':'Actual old minimal AUTHOR/REENTER passes remain valid history; the full client exposed a production async shape/chunk deadlock. No runtime result is transferred to candidate8.'}
    new['status']='CURRENT_CANDIDATE8_NATIVE133_STATIC_AND_THREE_ORDINARY_SERVERS_PASS_CLIENT_MIDDLE_ALIAS_DIAGNOSTICS_ARCHIVE_PENDING';new['production_sha256']=SHA;new['native_tests']=133
    new['actual_clients_pending']={'AUTHOR_MIN15':'reports/V10_CLIENT_MIN_ATTEMPT_15.json','REENTER4':'reports/V10_CLIENT_REENTER_RELEASE4_ATTEMPT_1.json','FULL_KAPPA4':'reports/V10_CLIENT_FULL_RELEASE4_ATTEMPT_1.json'}
    new['scope'][0]='Current8 native133/core20, regenerated architecture/RP/cross source audits and Author7/minimal production reopen5/full owned-scene reopen4 actually PASS. All current clients/middle-key proofs, separate alias disk2, combined diagnostics4 and delivery archive remain pending; no previous candidate runtime passes are reused.'
    new['scope'][1]='The pristine Author7 delivery baseline retains three local lamps, remote D and three levers. GUI AUTHOR/REENTER copies intentionally remove old C/lever3 and retain their own saved rule/policy/lamp snapshots; their graph is not imported into delivery.'
    new['scope'][2]='Candidate7 remains historical: actual MIN14/REENTER3 successes plus full3 async chunk deadlock failure. Candidate8 keeps all art/resources exact and changes only declared async snapshot executable classes.'
    new['actual_completed']={
        'native':'reports/FIRST_SET_GAMETEST_V10_ATTEMPT_8.xml',
        'static_art':'reports/V10_ARCHITECTURE_HEIGHT_AND_ART_CANDIDATE8.json',
        'frozen_resource_correspondence':'reports/V10_FROZEN_RESOURCE_DELTA_CANDIDATE7_TO8.json',
        'scene_author':'reports/V10_SERVER_AUTHOR_MIN_ATTEMPT_7.json',
        'production_only_reopen':'reports/V10_SERVER_PRODUCTION_ONLY_REOPEN_RELEASE5.json',
        'full_owned_scene_reopen':'reports/V10_SERVER_FULL_OWNED_SCENE_REOPEN_RELEASE4.json',
        'pristine_typed_saved_state':'reports/V10_PRISTINE_BASELINE_REOPEN_INDEPENDENT_RELEASE5.json',
        'full_typed_saved_state':'reports/V10_FULL_OWNED_SCENE_REOPEN_INDEPENDENT_RELEASE4.json'}
    new['duplicate_audit']['nativeAliasIdentity']='PASS_CURRENT133_NATIVE_IN_MEMORY_ONLY'
    new['duplicate_audit']['actualAdditionalAliasDiskProof']='PENDING_ACTUAL_CURRENT_ALIAS_RELEASE2_DISTINCT_AUTHOR_REENTER_PRODUCTION_REOPEN'
    new['mandatory_alias_disk_pending']['scope']='14 old registry instances(two each),7 persisted old inventory items to canonical fresh UUID, three distinct saved processes with production-only third reopen. These server/API disk proofs are required separately from actual native middle-key canonical69/alias7 client proofs; both remain pending for current8.'
    extras=['V10_SERVER_FULL_OWNED_SCENE_REOPEN_RELEASE4.json','V10_SERVER_PRODUCTION_ONLY_REOPEN_RELEASE5.json','V10_PRISTINE_BASELINE_REOPEN_INDEPENDENT_RELEASE5.json','V10_FULL_OWNED_SCENE_REOPEN_INDEPENDENT_RELEASE4.json','V10_SCENE_BEFORE_FIRST_ENTRY_SETTINGS_7.json','DIAGNOSTICS_SERVER_DISABLED_V10_RELEASE4.json','DIAGNOSTICS_SERVER_ENABLED_V10_RELEASE4.json','DIAGNOSTICS_SERVER_REENTER_V10_RELEASE4.json','DIAGNOSTICS_SERVER_TYPED_INDEPENDENT_V10_RELEASE4.json','V10_ARCHITECTURE_HEIGHT_AND_ART_CANDIDATE8.json','V10_ARCHITECTURE_DUPLICATE_AUDIT_CANDIDATE8.json','RP_TYPE_DUPLICATE_AUDIT_V10_CANDIDATE8_FINAL.json','V10_CROSS_MODULE_DUPLICATE_AUDIT_CANDIDATE8.json','V10_CONSTRUCTION_DUPLICATE_AUDIT_SUMMARY_CANDIDATE8.json','RP_INPUT_CORRESPONDENCE_V10_CANDIDATE8.json','V10_FROZEN_RESOURCE_DELTA_CANDIDATE7_TO8.json','V10_ASYNC_SHAPE_SNAPSHOT_NATIVE_CANDIDATE8.json','V10_CLIENT_MIN15_REENTER4_ROSTER_PARTS_DIAGNOSTICS_INDEPENDENT.json','V10_CLIENT_MIDDLE_PICK_CANDIDATE8_INDEPENDENT.json','V10_ALIAS_AUTHOR_RELEASE2.json','V10_ALIAS_REENTER_RELEASE2.json','V10_ALIAS_PRODUCTION_REOPEN_RELEASE2.json']
    new['planned_evidence_not_actual_pass']=['reports/'+name for name in extras if not (ROOT/'reports'/name).is_file()]
    for name,command in new['commands'].items():
        command['status']='PENDING_CURRENT_ACTUAL_ALL_CLIENTS_MIDDLE_KEY_ALIAS_DISK_DIAGNOSTICS_AND_ARCHIVE_BEFORE_ROOT_GO; DO_NOT_RUN'
        argv=command['argv']
        if 'tools/package_review_v10.py' in argv:
            kept=[];i=0
            while i<len(argv):
                if argv[i]=='--extra-evidence':i+=2
                else:kept.append(argv[i]);i+=1
            for evidence in extras:kept+=['--extra-evidence','reports/'+evidence]
            command['argv']=kept
    path.write_text(json.dumps(new,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
    print(json.dumps({'status':'OWNED_LIGHTWEIGHT_CURRENT8_GUIDES_ARGV_BOUND_RUNTIME_GATES_PENDING','sha256':SHA,'native':133,'historicalBytesPreserved':True},ensure_ascii=False))


if __name__=='__main__':main()
