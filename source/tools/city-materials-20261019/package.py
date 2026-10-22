"""Package the final visually accepted, reopened, compared material correction."""
import hashlib
import json
import shutil
import subprocess
import zipfile
from pathlib import Path

PROJECT=Path(__file__).absolute().parents[2]
ROOT=PROJECT.parent
RESEARCH=ROOT/'research/city-materials-20261019'
OUT=ROOT/'deliverables/materials-20261019'
BASE='ece36054605f3fbd00b1ee90449b6749e0a977d7'
GIT=r'C:\Users\vakir\.cache\codex-runtimes\codex-primary-runtime\dependencies\native\git\cmd\git.exe'


def read(path):return json.loads(path.read_text(encoding='utf-8-sig'))
def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def git(*args):return subprocess.check_output([GIT,*args],cwd=ROOT)


def main():
    review=read(RESEARCH/'review.json')
    assert review['status']=='PASS_VISUAL_AND_NATIVE_REOPEN_REVIEW'
    built=read(RESEARCH/'built-jar.json')
    artifact=read(RESEARCH/'artifact-verification.json')
    comparison=read(RESEARCH/'comparison.json')
    negative=read(RESEARCH/'negative-visual-controls.json')
    assert artifact['status']=='PASS_ART_GEOMETRY_IDS_SAVE_RESOURCES_AND_OPTIMIZATIONS_UNCHANGED'
    assert negative['status']=='PASS_NO_NEW_BLACK_NEGATIVE_CONTROLS'
    assert read(RESEARCH/'trim-verification.json')['productionJarSha256']==built['sha256']
    assert built['sha256']==artifact['candidateSha256']==comparison['runs']['materials.1']['jarSha256']==review['productionJarSha256']
    for case in ['compare-base','compare-final2','reopen-final2']:
        driver=read(RESEARCH/case/'driver.json');launch=read(RESEARCH/case/'launch.json')
        assert driver['status']=='PASS_NATIVE_CAPTURE_SAVE_NORMAL_EXIT'
        assert launch['exitCode']==0 and launch['captureComplete'] and launch['normalSaveMessages']
    assert read(RESEARCH/'reopen-final2/launch.json')['artifact_sha256']==built['sha256']
    assert not git('diff','--name-only','HEAD','--',PROJECT.name).strip(),'Commit final source changes before packaging'
    OUT.mkdir(exist_ok=False)
    (OUT/'game').mkdir();(OUT/'sources').mkdir();(OUT/'evidence').mkdir();(OUT/'screenshots').mkdir()
    jar=OUT/'game/dreamwalker-bb-materials.1.jar'
    shutil.copyfile(Path(built['jar']),jar)
    assert sha(jar)==built['sha256']
    selected=[]
    prefix=PROJECT.name+'/'
    for value in git('ls-files','-z','--',PROJECT.name).split(b'\0'):
        if not value:continue
        name=value.decode();relative=name[len(prefix):]
        if relative.startswith(('src/','tools/','gradle/')) or relative in ['build.gradle','settings.gradle','gradle.properties','gradlew','gradlew.bat','.gitignore'] or relative.upper().startswith(('README','LICENSE')):
            selected.append(name)
    source=OUT/'sources/dreamwalker-bb-materials.1-source.zip'
    with zipfile.ZipFile(source,'w',compression=zipfile.ZIP_DEFLATED,compresslevel=6) as archive:
        for name in sorted(selected):
            path=ROOT/name
            assert path.is_file() and not path.is_symlink() and not getattr(path.stat(),'st_file_attributes',0)&1024
            archive.write(path,name)
    patch=OUT/'sources/changes-from-streaming.5.patch'
    patch.write_bytes(git('diff','--binary',BASE,'HEAD','--',PROJECT.name))
    for name in ['materials-summary.json','all-resources-summary.json','audit-interpretation.json','materials-audit.json',
                 'artifact-verification.json','preservation-before.json','build.log','build-receipt.json','built-jar.json',
                 'comparison.json','repeat-performance.json','visual-verification.json','trim-verification.json','ao-suspicion.json','negative-visual-controls.json','review.json','compare-base-reset.json','compare-final2-reset.json']:
        shutil.copyfile(RESEARCH/name,OUT/'evidence'/name)
    qa=RESEARCH/'dreamwalker-gallery-material-probe.jar'
    assert sha(qa)==comparison['runs']['streaming.5']['qaAddonSha256']==comparison['runs']['materials.1']['qaAddonSha256']
    shutil.copyfile(qa,OUT/'evidence/QA-ONLY-dreamwalker-gallery-material-probe.jar')
    surface=RESEARCH/'dreamwalker-gallery-surface-probe.jar'
    assert sha(surface)==read(RESEARCH/'reopen-final2/prepared.json')['extra_mods'][0]['sha256']==read(RESEARCH/'diagnose-trim-flat2/prepared.json')['extra_mods'][0]['sha256']
    shutil.copyfile(surface,OUT/'evidence/QA-ONLY-dreamwalker-gallery-surface-probe.jar')
    for case in ['compare-base','compare-final2','reopen-final2','final-ao-on','diagnose-trim-flat','diagnose-trim-flat2']:
        target=OUT/'evidence'/case;target.mkdir()
        for name in ['prepared.json','driver.json','launch.json','client-capture-input.json','client-capture-output.json','launch-console.log','summary.json','memory.jsonl','interruption.json','startup-thread.txt','host-state-at-fps.json']:
            path=RESEARCH/case/name
            if path.exists():shutil.copyfile(path,target/name)
        for path in (RESEARCH/case/'screenshots').glob('*.png'):
            shutil.copyfile(path,OUT/'screenshots'/path.name)
    for path in (RESEARCH/'comparisons').glob('*.png'):
        shutil.copyfile(path,OUT/'screenshots'/path.name)
    # The reviewed prose is written only after actual native and paired evidence.
    shutil.copyfile(RESEARCH/'FINAL-REPORT-RU.txt',OUT/'REPORT-RU.txt')
    manifest={'status':'PASS_BUILT_NATIVE_VISUAL_SAVE_REOPEN_AND_MATCHED_COMPARISON',
        'version':built['version'],'baseCommit':BASE,'commit':git('rev-parse','HEAD').decode().strip(),
        'branch':git('branch','--show-current').decode().strip(),
        'jar':{'path':str(jar.relative_to(OUT)),'sha256':sha(jar),'bytes':jar.stat().st_size},
        'source':{'path':str(source.relative_to(OUT)),'sha256':sha(source),'files':len(selected)},
        'patch':{'path':str(patch.relative_to(OUT)),'sha256':sha(patch)},
        'mapPreparationRequired':False,'map':'Use unchanged collision-streaming-20261018/map/world; no new world copy delivered.',
        'independentBackupPreserved':'../collision-streaming-20261018/backup/world',
        'confirmedRemainingDefects':review['confirmedRemainingDefects'],
        'unconfirmedSuspicions':review['unconfirmedSuspicions'],
        'performanceComparison':'evidence/comparison.json','report':'REPORT-RU.txt'}
    manifest['performanceObservations']=review['performanceObservations']
    manifest['performanceNonRegressionProven']=review['performanceNonRegressionProven']
    manifest['qaAddon']={'path':'evidence/QA-ONLY-dreamwalker-gallery-material-probe.jar','sha256':sha(qa),
        'purpose':'Frozen diagnostic test harness, not needed for normal play; do not install with the production JAR.'}
    manifest['surfaceQaAddon']={'path':'evidence/QA-ONLY-dreamwalker-gallery-surface-probe.jar','sha256':sha(surface),
        'purpose':'Enhanced visible-triangle diagnostics for residual trim/reopen; not used in the performance comparison.'}
    (OUT/'MANIFEST.json').write_text(json.dumps(manifest,indent=2)+'\n')
    print(json.dumps({'status':manifest['status'],'folder':str(OUT),'jarSha256':sha(jar)}))


if __name__=='__main__':main()
