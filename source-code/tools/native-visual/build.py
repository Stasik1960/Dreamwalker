"""Controlled build with a fresh output directory; accepted build is untouched."""
import datetime,hashlib,json,os,shutil,subprocess,sys,time
from pathlib import Path
P=Path(__file__).resolve().parents[2];T=P.parent.parent/'DW-visual-physics-20261024'
JAVA=Path('C:/Users/vakir/AppData/Roaming/.minecraft/runtime/java-runtime-gamma/windows/java-runtime-gamma')
API=Path('C:/Users/vakir/.codex/worktrees/3f07/DW/bloodborne complete/build/v127-dependencies/worldedit-mod-7.2.15.jar')
assert API.is_file()
history=T/'runtime-history';history.mkdir(exist_ok=True)
for prior in (T/'build/libs').glob('*.jar'):
    digest=hashlib.sha256(prior.read_bytes()).hexdigest();saved=history/(digest+'.jar')
    if not saved.exists():shutil.copy2(prior,saved)
    assert hashlib.sha256(saved.read_bytes()).hexdigest()==digest
checkpoint=T/'checkpoint-before-map.json'
if checkpoint.exists():
    n=json.loads(checkpoint.read_text(encoding='utf-8'));sealed=history/(n['candidate']['sha256']+'.jar')
    assert sealed.is_file() and hashlib.sha256(sealed.read_bytes()).hexdigest()==n['candidate']['sha256']
    if Path(n['candidate']['path'])!=sealed:
        shutil.copy2(checkpoint,T/'checkpoint-before-map.original.json');n['candidate']['path']=str(sealed);checkpoint.write_text(json.dumps(n,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
stamp=datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%S')
log=T/('build-'+stamp+'.log');receipt=T/('build-'+stamp+'.json')
args=[str(P/'gradlew.bat'),'--offline','--no-daemon','--max-workers=2','-Dorg.gradle.jvmargs=-Xmx1G',
      '-Pworldedit_api_jar='+str(API),'-Pmod_version=0.1.0-v12.7.4-visual-physics.1','-Pnative_visual_build_dir='+str(T/'build'),*sys.argv[1:]]
started=time.monotonic()
with log.open('x',encoding='utf-8') as out:result=subprocess.run(args,cwd=P,env=dict(os.environ,JAVA_HOME=str(JAVA)),stdout=out,stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW)
report={'status':'PASS' if result.returncode==0 else 'FAIL','exitCode':result.returncode,'seconds':time.monotonic()-started,'args':args,'log':str(log)}
receipt.write_text(json.dumps(report,indent=2),encoding='utf-8');(T/'build-latest.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
sys.exit(result.returncode)
