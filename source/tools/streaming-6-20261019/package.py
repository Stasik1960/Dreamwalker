import difflib,hashlib,json,shutil,subprocess,zipfile,traceback
from pathlib import Path
PROJECT=Path(__file__).absolute().parents[2];ROOT=PROJECT.parent
DELIVERY=ROOT/'deliverables/streaming-6-20261019';RESEARCH=ROOT/'research/dedicated-streaming-20261019'
TOOLS=Path(__file__).parent
def sha(path):return hashlib.file_digest(path.open('rb'),'sha256').hexdigest()
def write(path,value):path.write_text(json.dumps(value,indent=2)+'\n',encoding='utf-8')
def main():
    assert not (DELIVERY/'sources/source-files.json').exists(),'Preserve completed source package'
    DELIVERY.mkdir(exist_ok=True)
    for name in ['game','sources','server','evidence']:(DELIVERY/name).mkdir(exist_ok=True)
    base=ROOT/'deliverables/materials-20261019/game/dreamwalker-bb-materials.1.jar'
    jar=next((PROJECT/'build/libs').glob('dreamwalker-bb-*-materials.1-streaming.6.jar'))
    target=DELIVERY/'game/dreamwalker-bb-materials.1-streaming.6.jar';shutil.copyfile(jar,target)
    allowed={
      'dev/dreamwalker/bloodbornedw/composite/ShardedCompositeLedger.class',
      'dev/dreamwalker/bloodbornedw/composite/ImportedTypeMigration.class',
      'dev/dreamwalker/bloodbornedw/composite/ImportedCollisionMigration.class',
      'dev/dreamwalker/bloodbornedw/composite/ImportedCollisionMigration$1.class',
      'dev/dreamwalker/bloodbornedw/composite/ImportedCollisionMigration$2.class',
      'dev/dreamwalker/bloodbornedw/composite/ImportedTypeMigration$1.class',
      'fabric.mod.json'}
    with zipfile.ZipFile(base) as before,zipfile.ZipFile(target) as after:
        assert set(before.namelist())==set(after.namelist()),'Unexpected JAR members'
        changed=[name for name in before.namelist() if before.read(name)!=after.read(name)]
        assert set(changed)<=allowed,changed
        old=json.loads(before.read('fabric.mod.json'));new=json.loads(after.read('fabric.mod.json'));old['version']=new['version'];assert old==new
        assert not any('streamingqa' in n for n in after.namelist()),'QA leaked into production'
    evidence={'status':'PASS_ONLY_THREE_SERVER_CLASSES_AND_VERSION_CHANGED','baseCommit':subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip(),
       'baseJar':str(base),'baseSha256':sha(base),'jar':str(target),'sha256':sha(target),'changedMembers':changed,
       'resourcesClientLightingIdsAndRecipes':'byte-identical to materials.1','productionContainsQa':False}
    write(DELIVERY/'evidence/artifact-verification.json',evidence)
    tracked=[PROJECT/'gradle.properties',*[PROJECT/'src/architecture/java/dev/dreamwalker/bloodbornedw/composite'/name for name in ['ShardedCompositeLedger.java','ImportedTypeMigration.java','ImportedCollisionMigration.java']]]
    patch=subprocess.check_output(['git','diff','--binary','--no-ext-diff','HEAD','--',*[str(p) for p in tracked]],cwd=ROOT)
    test=PROJECT/'src/gametest/java/dev/dreamwalker/bloodbornedw/gametest/CompositeMembershipReadGameTests.java';rel=test.relative_to(ROOT).as_posix()
    patch+=(f'diff --git a/{rel} b/{rel}\nnew file mode 100644\n'+''.join(difflib.unified_diff([],test.read_text().splitlines(keepends=True),fromfile='/dev/null',tofile='b/'+rel))).encode()
    patchfile=DELIVERY/'sources/changes-from-materials.1.patch';patchfile.write_bytes(patch)
    check=subprocess.run(['git','apply','--reverse','--check',str(patchfile)],cwd=ROOT,capture_output=True,text=True)
    assert check.returncode==0,check.stderr
    toolnames=['build.py','qa.init.gradle','tests.init.gradle','dedicated.py','summarize.py','analyze.ps1','capture-profile.sh','package.py']
    sourcefiles=tracked+[test]+[TOOLS/name for name in toolnames]+list((PROJECT/'src/streamingqa').rglob('*.java'))+[PROJECT/'src/streamingqa/resources/fabric.mod.json']
    with zipfile.ZipFile(DELIVERY/'sources/changed-sources-and-qa-tools.zip','w',zipfile.ZIP_DEFLATED) as out:
        for p in sourcefiles:out.write(p,p.relative_to(ROOT).as_posix())
    write(DELIVERY/'sources/source-files.json',[{'path':p.relative_to(ROOT).as_posix(),'sha256':sha(p)} for p in sourcefiles])
    config=Path(r'C:\Users\vakir\Desktop\server.properties').read_text().replace('view-distance=12','view-distance=8').replace('simulation-distance=10','simulation-distance=6')
    (DELIVERY/'server/server.properties').write_text(config,encoding='utf-8')
    (DELIVERY/'server/jvm-args.txt').write_text('-Xms128M -Xmx9G\n')
    (DELIVERY/'server/start.sh.memory.patch').write_text('--- a/start.sh\n+++ b/start.sh\n@@ -9,1 +9,1 @@\n-java -Xms128M -Xmx12000M \\\n+java -Xms128M -Xmx9G \\\n')
    shutil.copyfile(TOOLS/'capture-profile.sh',DELIVERY/'server/capture-profile.sh')
    (DELIVERY/'server/README-RU.txt').write_text('Для общего лимита процесса/контейнера 12 ГБ: -Xms128M -Xmx9G, view-distance=8, simulation-distance=6.\nОстальные параметры запуска, javaagent, переменные авторизации и имя launcher сохраняются из вашего start.sh.\nФайлы рабочего стола этой задачей не изменены. Патч start.sh меняет только Xmx; токены в поставку не копировались.\nОдинаковые 9G / 8 / 6 обязательны для сравнения обеих версий. Уменьшение дальности не считается эффектом кода.\nЗапас около 3 ГиБ предназначен для native memory, метаданных, потоков, сжатия, ОС/служб контейнера; Xmx не ограничивает весь процесс.\nСостав серверных модов и реальный лимит хостинга ещё нужно сверить на удалённом сервере.\n\nОдна команда короткого профиля (Linux, из каталога сервера, подходящий JDK с jcmd):\nbash capture-profile.sh <PID_Java>\nПервые 90 секунд двигайтесь через те же плотные районы, последние 30 секунд дайте району выгрузиться.\nСохраните полученную папку dw-profile-*, лог сервера, число игроков и маршрут.\nЕсли native tracking не был включён при запуске, jcmd так и укажет; heap/RSS/JFR всё равно сохраняются.\n',encoding='utf-8')
    write(RESEARCH/'delivery-prepared.json',evidence)
if __name__=='__main__':
    try:main()
    except BaseException:
        (RESEARCH/'package-error.txt').write_text(traceback.format_exc(),encoding='utf-8');raise
