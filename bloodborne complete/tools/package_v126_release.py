"""Package only the accepted immutable production and final native reopened gallery."""
from pathlib import Path
import hashlib,json,shutil,subprocess,zipfile
from package_v124_release import ordinary_project_path,project_files,archive,sha,read
ROOT=Path(__file__).resolve().parents[1];REPO=ROOT.parent;DOC=ROOT/'docs/v12.6'
DEST=REPO/'releases/Dreamwalker-BB/v12.6-functional-assemblies';VERSION='0.1.0-v12.6-functional-assemblies'
JAR=ROOT/f'build/libs/dreamwalker-bb-fabric-1.20.1-{VERSION}.jar'

def main():
    a=read(DOC/'acceptance.json');digest=sha(JAR);assert a['status']=='PASS' and a['productionJarSha256']==digest
    for name,ev in a['evidence'].items():assert sha(Path(ev['path']))==ev['sha256'],name
    assert not a['currentUnresolvedTestFailures'] and a['gameTestCount']>=88 and a['counts']['nativeFramebufferImages']>=40
    world=Path(a['galleryWorld']).resolve();assert world.is_relative_to(ROOT/'build') and (world/'level.dat').is_file()
    manifest=read(world/'gallery-manifest.json');assert manifest['productionJarSha256']==digest and manifest['catalogue']['summary']['newWorkingTypes']==544
    assert not DEST.exists(),'Never overwrite a historical release';DEST.mkdir(parents=True)
    shutil.copyfile(JAR,DEST/JAR.name);assert sha(DEST/JAR.name)==digest
    sourcesjar=JAR.with_name(JAR.stem+'-sources.jar');assert sourcesjar.is_file();shutil.copyfile(sourcesjar,DEST/sourcesjar.name)
    paths=subprocess.check_output(['git','ls-files','-z','-co','--exclude-standard','--','bloodborne complete/'],cwd=REPO).decode('utf8').split('\0')
    sources={REPO/p for p in paths if p and not p.startswith('bloodborne complete/build/')}
    sources.update(project_files(DOC));sources.update(project_files(ROOT/'docs/v12.6-city-review'));sources.update(project_files(ROOT/'docs/v12.6-functional-assemblies'))
    sources={p for p in sources if p.is_file() and '/full-coverage-projections/' not in p.as_posix() and '/projections/' not in p.as_posix()}
    sr=archive(DEST/f'dreamwalker-bb-fabric-1.20.1-{VERSION}-full-source.zip',[(p,'bloodborne complete/'+p.relative_to(ROOT).as_posix()) for p in sorted(sources)])
    evidence=list(project_files(DOC))+list(project_files(ROOT/'docs/v12.6-city-review'))+list(project_files(ROOT/'docs/v12.6-functional-assemblies'))
    er=archive(DEST/'verification.zip',[(p,p.relative_to(ROOT/'docs').as_posix()) for p in sorted(evidence)])
    wr=archive(DEST/'Dreamwalker-BB-V12.6-Compact-Gallery.zip',[(p,'Dreamwalker-BB-V12.6-Compact-Gallery/'+p.relative_to(world).as_posix()) for p in sorted(project_files(world)) if p.name!='session.lock'])
    for name in ('REPORT-RU.txt','INSTALL-RU.txt','acceptance.json'):shutil.copyfile(DOC/name,DEST/name)
    shutil.copyfile(world/'GALLERY-INDEX.csv',DEST/'GALLERY-INDEX.csv')
    source_commit=subprocess.check_output(['git','rev-parse','HEAD'],cwd=REPO,text=True).strip()
    record={'schema':'dreamwalker-v126-release','status':'PASS','productionJarSha256':digest,'baselineCommit':a['baselineCommit'],'productionSourceCheckpoint':a['productionSourceCheckpoint'],'sourceArchiveContentCommit':source_commit,'counts':a['counts'],'sourceFiles':len(sr),'evidenceFiles':len(er),'worldFiles':len(wr),'galleryRequiresQaAddon':False,'originalMapChanged':False,'authorCollisionExport':'NOT_SUPPLIED_NOT_APPLIED','archiveEntriesVerifiedByCrcAndSha256':True,'files':[{'name':p.name,'bytes':p.stat().st_size,'sha256':sha(p)} for p in sorted(DEST.iterdir()) if p.is_file()]}
    (DEST/'release-manifest.json').write_text(json.dumps(record,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
    print(json.dumps({k:v for k,v in record.items() if k!='files'},ensure_ascii=False))
if __name__=='__main__':main()
