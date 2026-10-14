"""Package the verified Iris startup hotfix; preserve prior release/preparation."""
from pathlib import Path
import hashlib,json,shutil,stat,subprocess,xml.etree.ElementTree as ET,zipfile

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'build/crash-gallery-20261014'
PREVIOUS=ROOT.parent/'releases/Dreamwalker-BB/v12.7.1-city-fixes'
RELEASE=ROOT.parent/'releases/Dreamwalker-BB/v12.7.2-iris-fix'
DELIVERY=Path(r'C:\Users\vakir\.codex\worktrees\3f07\DW\releases\Dreamwalker-BB\v12.7.2-iris-fix')
VERSION='dreamwalker-bb-fabric-1.20.1-0.1.0-v12.7.2-iris-fix'

def read(p):return json.loads(p.read_text(encoding='utf-8-sig'))
def sha(p):
    h=hashlib.sha256()
    with p.open('rb') as f:
        for data in iter(lambda:f.read(1024*1024),b''):h.update(data)
    return h.hexdigest()
def ordinary(p):
    for q in [p,*p.parents]:
        if q.exists():assert not q.lstat().st_file_attributes & stat.FILE_ATTRIBUTE_REPARSE_POINT,str(q)
def write(p,v):p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(v,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
def copy(src,dst):ordinary(src);ordinary(dst);dst.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(src,dst);assert sha(src)==sha(dst)
def archive(path,entries):
    rows=[]
    with zipfile.ZipFile(path,'w',compression=zipfile.ZIP_DEFLATED,compresslevel=1,allowZip64=True) as output:
        for name,source in sorted(entries.items()):
            ordinary(source);info=zipfile.ZipInfo(name,(1980,1,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.create_system=3;info.external_attr=0o100644<<16
            h=hashlib.sha256();length=0
            with source.open('rb') as incoming,output.open(info,'w',force_zip64=True) as outgoing:
                for data in iter(lambda:incoming.read(1024*1024),b''):outgoing.write(data);h.update(data);length+=len(data)
            rows.append({'path':name,'bytes':length,'sha256':h.hexdigest()})
    with zipfile.ZipFile(path) as result:
        assert result.testzip() is None
        assert len(result.namelist())==len(rows)
        for row in rows:
            h=hashlib.sha256()
            with result.open(row['path']) as member:
                for data in iter(lambda:member.read(1024*1024),b''):h.update(data)
            assert h.hexdigest()==row['sha256'],row['path']
    return {'file':path.name,'bytes':path.stat().st_size,'sha256':sha(path),'members':rows}

def main():
    assert not RELEASE.exists() and not DELIVERY.exists()
    ordinary(RELEASE);ordinary(DELIVERY)
    assert DELIVERY.parent==Path(r'C:\Users\vakir\.codex\worktrees\3f07\DW\releases\Dreamwalker-BB')
    assert read(ROOT/'build/final-city-fixes/build-probe-iris-fix-result.json')['status']=='PASS'
    assert read(ROOT/'build/final-city-fixes/gametest-iris-fix-result.json')['status']=='PASS'
    native=read(OUT/'fixed/result.json');assert native['status']=='PASS_FIXED_JAR_IRIS_GALLERY_NATIVE_TOUR'
    assert read(OUT/'repror2/result.json')['status']=='PASS_PREVIOUS_JAR_IRIS_CRASH_REPRODUCED'
    component=read(OUT/'component-compatibility.json');assert component['allOtherMembersByteIdentical']
    jar=ROOT/'build/libs'/(VERSION+'.jar');assert sha(jar)==component['productionJarSha256']==native['productionJarSha256']
    xml=ROOT/'build/test-results/gametest/TEST-final-city-fixes-iris-fix.xml'
    cases=list(ET.parse(xml).getroot().iter('testcase'));assert len(cases)==148 and all(c.find('failure') is None and c.find('error') is None for c in cases)
    assert read(OUT/'author-build-result.json')['status']=='PASS'
    author=ROOT/'build/libs/dreamwalker-v127-full-conversion-author-1.2.7.jar'
    old_author=PREVIOUS/'Conversion'/author.name
    with zipfile.ZipFile(author) as new,zipfile.ZipFile(old_author) as old:
        assert json.loads(new.read('dw-conversion-binding.json'))['productionJarSha256']==sha(jar)
        names=[n for n in old.namelist() if n.startswith('dev/dreamwalker/bloodbornedw/conversion/FullMapConversionAuthor') and n.endswith('.class')]
        assert names and all(new.read(n)==old.read(n) for n in names)
    assert (ROOT/'build/v1271-city-preparation/source-plan.sqlite').stat().st_size==407773184
    RELEASE.mkdir();(RELEASE/'Verification').mkdir();(RELEASE/'Conversion').mkdir()
    copy(jar,RELEASE/jar.name);copy(ROOT/'build/libs'/(VERSION+'-sources.jar'),RELEASE/(VERSION+'-sources.jar'))
    copy(author,RELEASE/'Conversion'/author.name)
    copy(ROOT/'docs/v12.7.2/IRIS-CRASH-FIX-RU.txt',RELEASE/'README-RU.txt')
    evidence={
        'old-jar-iris-reproduction.json':OUT/'repror2/result.json',
        'fixed-jar-iris-gallery.json':OUT/'fixed/result.json',
        'component-compatibility.json':OUT/'component-compatibility.json',
        'native-gallery-tour.json':ROOT/'build/runtime-client-iris-gallery-fixed-20261014/final-gallery-visual-tour.json',
        'native-iris-probe.json':ROOT/'build/runtime-client-iris-gallery-fixed-20261014/iris-whole-optics-probe.json',
        xml.name:xml,
        'build-result.json':ROOT/'build/final-city-fixes/build-probe-iris-fix-result.json',
        'gametest-result.json':ROOT/'build/final-city-fixes/gametest-iris-fix-result.json',
        'conversion-author-build.json':ROOT/'build/v1272-conversion-author-iris-fix/build-report.json',
    }
    for name,path in evidence.items():copy(path,RELEASE/'Verification'/name)
    screenshots=ROOT/'build/runtime-client-iris-gallery-fixed-20261014/screenshots'
    pngs=sorted(screenshots.glob('*.png'));assert len(pngs)==6
    for png in pngs:copy(png,RELEASE/'Verification/screenshots'/png.name)
    pixel={'status':'PASS_SIX_NATIVE_IRIS_GALLERY_FRAMES_REVIEWED','productionJarSha256':sha(jar),'reviewer':'root, direct view_image of every PNG','frames':[{'file':p.name,'sha256':sha(p)} for p in pngs],'finding':'No uniform-black architecture or missing-texture checkerboards. Repaired door, complete columns and patterned source-grey masonry are visible. Native local floor-edge shading retained.','scope':'Six daylight views, shaders disabled; not the complete city or every shader pack'}
    write(RELEASE/'Verification/pixel-review.json',pixel)
    source_old=read(PREVIOUS/'full-source-release-manifest.json')
    entries={row['name']:Path(row['source']) for row in source_old['files']}
    for folder in [ROOT/'docs/v12.7.2',OUT/'compatibility-mods',ROOT/'build/v1272-conversion-author-iris-fix']:
        for p in folder.rglob('*'):
            ordinary(p)
            if p.is_file():entries['bloodborne complete/'+p.relative_to(ROOT).as_posix()]=p
    extra=[Path(__file__),jar,ROOT/'build/libs'/(VERSION+'-sources.jar'),author,
        ROOT/'build/final-city-fixes/observer/src/dev/dreamwalker/bloodbornedw/galleryqa/IrisWholeOpticsProbe.java',
        *OUT.glob('*.py'),OUT/'repror2/camera-input.json',OUT/'fixed/camera-input.json',*evidence.values(),*pngs]
    for p in extra:entries['bloodborne complete/'+p.relative_to(ROOT).as_posix()]=p
    entries['IRIS-CRASH-FIX-RU.txt']=ROOT/'docs/v12.7.2/IRIS-CRASH-FIX-RU.txt'
    write(OUT/'package-progress.json',{'status':'ARCHIVING_FULL_SOURCE','members':len(entries)})
    full=archive(RELEASE/(VERSION+'-full-source.zip'),entries)
    proof=archive(RELEASE/'Dreamwalker-BB-V12.7.2-Iris-Verification.zip',{p.relative_to(RELEASE/'Verification').as_posix():p for p in (RELEASE/'Verification').rglob('*') if p.is_file()})
    write(RELEASE/'full-source-release-manifest.json',{'status':'PASS_CURRENT_SOURCE_AND_REUSED_PREPARATION_ARCHIVED','productionJarSha256':sha(jar),'baselineSourceArchiveSha256':source_old['archiveSha256'],'preparedSourcePlanSha256':source_old['sourcePlanSha256'],'preparedRuntimeManifestSha256':source_old['runtimeManifestSha256'],'sourceIdentity':'Actual filesystem content and ZIP member SHA, including current hotfix files. Older proofs retain their historical original JAR SHA.','wholeCityConverted':False,'sourcePreparationRecreated':False,'archive':full})
    artifacts=[p for p in RELEASE.iterdir() if p.is_file()]
    manifest={'status':'PASS_IRIS_STARTUP_CRASH_FIXED_AND_GALLERY_REOPENED','productionJarSha256':sha(jar),'version':'0.1.0-v12.7.2-iris-fix','gameTestsPassed':148,'nativeGalleryFrames':6,'nativeGalleryNormalExit':True,'originalGalleryNeedsReinstallation':False,'existingGallery':str(DELIVERY.parent/'v12.7.1-city-fixes/World'),'altArtworkLoaded':0,'wholeCityConverted':False,'performanceCodeUnchanged':True,'fullUser278ModStackTested':False,'nativeLoader':'0.16.10','nativeJava':'21.0.10','shaderPackEnabled':False,'converterAuthorSha256':sha(author),'converterClassesByteIdenticalToPreviouslyNativeTested':True,'archives':[{'file':r['file'],'bytes':r['bytes'],'sha256':r['sha256']} for r in (full,proof)],'files':[{'file':p.name,'bytes':p.stat().st_size,'sha256':sha(p)} for p in artifacts]}
    write(RELEASE/'release-manifest.json',manifest)
    sums=[f'{sha(p)}  {p.relative_to(RELEASE).as_posix()}' for p in sorted(RELEASE.rglob('*')) if p.is_file()]
    (RELEASE/'SHA256SUMS.txt').write_text('\n'.join(sums)+'\n',encoding='utf8')
    DELIVERY.mkdir();count=0
    for p in RELEASE.rglob('*'):
        ordinary(p)
        if p.is_file():copy(p,DELIVERY/p.relative_to(RELEASE));count+=1
    write(OUT/'package-result.json',{'status':'PASS_ARCHIVES_AND_LOCAL_DELIVERY_BYTE_VERIFIED','delivery':str(DELIVERY),'files':count,'productionJarSha256':sha(jar),'sourceArchiveSha256':full['sha256'],'sourceArchiveBytes':full['bytes'],'sourceArchiveMembers':len(full['members']),'verificationArchiveSha256':proof['sha256']})

if __name__=='__main__':main()
