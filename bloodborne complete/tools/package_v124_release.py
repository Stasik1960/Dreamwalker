"""Package the final bound mod and freshly reopened gallery, never historical releases."""
from pathlib import Path
import hashlib,json,shutil,stat,subprocess,zipfile
import xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[1];REPO=ROOT.parent;DOC=ROOT/'docs/v12.4-dedup'
DEST=REPO/'releases/Dreamwalker-BB/v12.4-dedup';VERSION='0.1.0-v12.4-dedup'
JAR=ROOT/f'build/libs/dreamwalker-bb-fabric-1.20.1-{VERSION}.jar'
def read(p):return json.loads(p.read_text(encoding='utf8'))
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def ordinary(p):
    st=p.lstat();assert not stat.S_ISLNK(st.st_mode) and not getattr(st,'st_file_attributes',0)&0x400,p
def ordinary_project_path(p):
    p=p.absolute();assert p.is_relative_to(ROOT),p
    ordinary(p)
    if p==ROOT:return
    for parent in p.parents:
        ordinary(parent)
        if parent==ROOT:break
def project_files(root):
    pending=[root]
    while pending:
        parent=pending.pop();ordinary_project_path(parent)
        for entry in parent.iterdir():
            ordinary_project_path(entry)
            if entry.is_dir():pending.append(entry)
            elif entry.is_file():yield entry
def archive(p,entries):
    seen=set();record=[]
    with zipfile.ZipFile(p,'w',zipfile.ZIP_DEFLATED,compresslevel=6) as z:
        for source,name in entries:
            ordinary_project_path(source);assert name not in seen;seen.add(name)
            z.write(source,name);record.append({'path':name,'bytes':source.stat().st_size,'sha256':sha(source)})
    with zipfile.ZipFile(p) as z:
        assert z.testzip() is None
        for row in record:assert hashlib.sha256(z.read(row['path'])).hexdigest()==row['sha256']
    return record
def main():
    ordinary_project_path(DOC/'acceptance.json');ordinary_project_path(JAR)
    acceptance=read(DOC/'acceptance.json');digest=sha(JAR)
    assert acceptance['status']=='PASS' and acceptance['productionJarSha256']==digest
    for name,evidence in acceptance['evidence'].items():
        path=Path(evidence['path']);ordinary_project_path(path)
        assert sha(path)==evidence['sha256'],('Acceptance evidence bytes changed before packaging',name,path)
    for key in ('build','gameTests','nativeOldSaveMigration','nativeSaveReopen','nativeVisualReview','galleryServerReopen','galleryClientOpenAndSave','gallerySavedObjects','preservation','resources'):
        assert acceptance[key]=='PASS',(key,acceptance[key])
    xml=ET.parse(ROOT/acceptance['gameTestXml']).getroot()
    assert len(xml.findall('.//testcase'))>=70 and not xml.findall('.//failure') and not xml.findall('.//error')
    world=Path(acceptance['galleryWorld']).resolve();assert world.is_relative_to(ROOT/'build') and (world/'level.dat').is_file()
    manifest=read(world/'gallery-manifest.json');assert manifest['productionJarSha256']==digest
    assert not DEST.exists(),'Never overwrite an existing release';DEST.mkdir(parents=True)
    jarout=DEST/JAR.name;shutil.copyfile(JAR,jarout);assert sha(jarout)==digest
    tracked=subprocess.check_output(['git','ls-files','-z','-co','--exclude-standard','--','bloodborne complete/'],cwd=REPO).decode('utf8').split('\0')
    sources={REPO/p for p in tracked if p and not p.startswith('bloodborne complete/build/')}
    sources.update(project_files(DOC))
    sources={p for p in sources if p.is_file() and '/full-coverage-projections/' not in p.as_posix() and '/projections/' not in p.as_posix()}
    sourcezip=DEST/f'dreamwalker-bb-fabric-1.20.1-{VERSION}-full-source.zip'
    sr=archive(sourcezip,[(p,'bloodborne complete/'+p.relative_to(ROOT).as_posix()) for p in sorted(sources)])
    evidencezip=DEST/'verification.zip'
    ev=archive(evidencezip,[(p,p.relative_to(DOC).as_posix()) for p in sorted(project_files(DOC)) if '/full-coverage-projections/' not in p.as_posix() and '/projections/' not in p.as_posix()])
    worldzip=DEST/'Dreamwalker-BB-V12.4-Flat-Gallery.zip'
    wr=archive(worldzip,[(p,'Dreamwalker-BB-V12.4-Flat-Gallery/'+p.relative_to(world).as_posix()) for p in sorted(project_files(world)) if p.name!='session.lock'])
    for name in ('REPORT-RU.txt','INSTALL-RU.txt','MERGED-GROUPS-RU.csv','OLD-ID-STATES.csv','RETAINED-DIFFERENCES-RU.txt','acceptance.json'):
        shutil.copyfile(DOC/name,DEST/name)
    record={'schema':'dreamwalker-v124-dedup-release-v1','status':'PASS','productionJarSha256':digest,'baselineCommit':acceptance['baselineCommit'],'sourceCommit':acceptance['sourceCommit'],'counts':read(DOC/'type-counts.json'),'sourceFiles':len(sr),'evidenceFiles':len(ev),'worldFiles':len(wr),'galleryRequiresQaAddon':False,'originalMapChanged':False,'physicalMouseKeyboard':'NOT_RUN','authorCollisionExport':'NOT_SUPPLIED_NOT_APPLIED','files':[{'name':p.name,'bytes':p.stat().st_size,'sha256':sha(p)} for p in sorted(DEST.iterdir()) if p.is_file()]}
    (DEST/'release-manifest.json').write_bytes((json.dumps(record,ensure_ascii=False,indent=2)+'\n').encode('utf8'))
    print(json.dumps({k:v for k,v in record.items() if k!='files'},ensure_ascii=False))
if __name__=='__main__':main()
