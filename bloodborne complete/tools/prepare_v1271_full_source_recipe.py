"""Review and hash a complete source/preparation archive recipe, without Java.

Default writes a recipe only. --archive writes its reviewed ZIP after final
acceptance; never run compression during the native FPS measurements.
"""
import argparse,hashlib,json,platform,subprocess,traceback,zipfile
from xml.etree import ElementTree
from importlib.metadata import version
from pathlib import Path
from run_v11_server import files_without_links
from verify_v1271_final_readiness import ROOT,ordinary,read,sha,write

BASE=Path(r'C:\Users\vakir\.codex\worktrees\3f07\DW\bloodborne complete')
NAME='dreamwalker-bb-fabric-1.20.1-0.1.0-v12.7.1-city-fixes-full-source.zip'
DONOR=BASE/'build/runtime-server-v127-new-gallery-author2'

def archive(path,entries):
    """Deterministic member metadata; verify both source stability and ZIP bytes."""
    assert not path.exists(),'Never overwrite an existing source archive'
    records=[]
    with zipfile.ZipFile(path,'x',compression=zipfile.ZIP_DEFLATED,compresslevel=6,allowZip64=True) as z:
        for source,name in sorted(entries,key=lambda item:item[1]):
            ordinary(source);digest=sha(source)
            info=zipfile.ZipInfo(name,date_time=(1980,1,1,0,0,0));info.create_system=3
            info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16
            with source.open('rb') as stream,z.open(info,'w',force_zip64=True) as target:
                for chunk in iter(lambda:stream.read(1<<20),b''):target.write(chunk)
            assert sha(source)==digest,('Source changed during archive',source)
            records.append({'path':name,'bytes':source.stat().st_size,'sha256':digest})
    with zipfile.ZipFile(path) as z:
        assert z.testzip() is None
        for row in records:
            h=hashlib.sha256()
            with z.open(row['path']) as stream:
                for chunk in iter(lambda:stream.read(1<<20),b''):h.update(chunk)
            assert h.hexdigest()==row['sha256'],row['path']
    return {'sha256':sha(path),'bytes':path.stat().st_size,'memberCount':len(records),'members':records,'memberMetadata':'fixed1980-time/sorted-paths/unix0644'}

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--production-jar',type=Path,required=True)
    p.add_argument('--output',type=Path,default=ROOT/'build/final-city-fixes/full-source-package-recipe.json')
    p.add_argument('--destination',type=Path,default=ROOT.parent/'releases/Dreamwalker-BB/v12.7.1-city-fixes')
    p.add_argument('--acceptance',type=Path)
    p.add_argument('--gametest-xml',type=Path,help='Final actual XML; alternatively acceptance.gameTestXml')
    p.add_argument('--native-source-proof',type=Path,help='Final actual native proof; alternatively acceptance.nativeSourceModelProof')
    p.add_argument('--native-source-proof-production-sha256',help='Actual JAR SHA used by the native proof; alternatively acceptance.nativeSourceModelProofProductionJarSha256')
    p.add_argument('--native-proof-compatibility-report',type=Path,help='Explicit component byte-identity binding when native proof used another JAR')
    p.add_argument('--archive',action='store_true');a=p.parse_args()
    jar=ordinary(a.production_jar);prep=ROOT/'build/v1271-city-preparation';m=read(prep/'runtime/manifest.json')
    accepted=read(ordinary(a.acceptance)) if a.acceptance else {}
    if accepted:
        assert accepted['status']=='PASS' and accepted['productionJarSha256']==sha(jar) and not accepted.get('currentUnresolvedTestFailures')
    assert sha(prep/'source-plan.sqlite')==m['sourcePlanSha256'] and m['counts']['REMOVE']==0 and m['nativeFrozenOpticalContextAuditStatus']=='PASS'
    entries={}
    def add(path,relative):
        path=ordinary(path);assert path.is_file(),relative
        if relative in entries:
            assert entries[relative]==path,('Conflicting source archive member',relative)
            return
        entries[relative]=path
    for name in ('build.gradle','settings.gradle','gradle.properties','gradlew','gradlew.bat','README.md','README.txt','RELEASE-STATUS.md','.gitignore'):
        path=ROOT/name
        if path.is_file():add(path,'bloodborne complete/'+name)
    for directory in ('src','tools','gradle','docs'):
        for path in files_without_links(ROOT/directory):
            rel=path.relative_to(ROOT)
            if '__pycache__' in rel.parts or path.suffix=='.pyc':continue
            # Tool data JSON/SQL references stay present; huge diagnostic raster
            # projections belong to verification artifacts, not rebuild inputs.
            if directory=='docs' and path.suffix.lower() in ('.png','.jpg','.jpeg','.webp','.mp4','.pdf','.zip','.bbmodel'):continue
            add(path,'bloodborne complete/'+rel.as_posix())
    for path in (prep/'source-plan.sqlite',prep/'decision-frequency-proof.json'):
        add(path,'bloodborne complete/'+path.relative_to(ROOT).as_posix())
    for path in files_without_links(prep/'runtime'):add(path,'bloodborne complete/'+path.relative_to(ROOT).as_posix())
    # The actual native-client harness is generated under ignored build paths.
    # Keep it with evidence in the source bundle, separate from production src.
    qa=ROOT/'build/final-city-fixes'
    for directory in ('observer/src','observer/resources'):
        for path in files_without_links(qa/directory):add(path,'bloodborne complete/'+path.relative_to(ROOT).as_posix())
    for relative in ('observer.init.gradle','run_client.py','run_controlled.py','enhance_observer.py','source-fallback-probes.json','source-fallback-target-probes.json','planned-native-optical-context-mapping.json'):
        path=qa/relative;add(path,'bloodborne complete/'+path.relative_to(ROOT).as_posix())
    for relative in ('run_converter_validation.py','run_gallery_client.py'):
        path=qa/relative
        if path.is_file():add(path,'bloodborne complete/'+path.relative_to(ROOT).as_posix())
    xmlArg=a.gametest_xml or accepted.get('gameTestXml')
    assert xmlArg,'Specify final --gametest-xml or acceptance.gameTestXml'
    xml=ordinary(xmlArg);assert xml.is_relative_to(ROOT)
    if accepted.get('gameTestXmlSha256'):assert sha(xml)==accepted['gameTestXmlSha256']
    suite=ElementTree.parse(xml).getroot()
    testCount=len(list(suite.iter('testcase')))
    assert testCount>=148 and not list(suite.iter('failure')) and not list(suite.iter('error')),'Final native tests are incomplete'
    add(xml,'bloodborne complete/'+xml.relative_to(ROOT).as_posix())
    runName=xml.stem.removeprefix('TEST-')
    for path in (ROOT/('build/gametest-'+runName)/'world').glob('*proof.json'):
        add(path,'bloodborne complete/'+path.relative_to(ROOT).as_posix())
    sourceProbe=ROOT/'build/runtime-client-comparison-fresh-final-reference-r2/native-source-model-probes.json'
    add(sourceProbe,'bloodborne complete/'+sourceProbe.relative_to(ROOT).as_posix())
    proofArg=a.native_source_proof or accepted.get('nativeSourceModelProof')
    assert proofArg,'Specify final --native-source-proof or acceptance.nativeSourceModelProof'
    finalProbe=ordinary(proofArg);assert finalProbe.is_relative_to(ROOT)
    if accepted.get('nativeSourceModelProofSha256'):assert sha(finalProbe)==accepted['nativeSourceModelProofSha256']
    proof=read(finalProbe)
    assert finalProbe.is_file() and proof['status']=='PASS_NATIVE_FALLBACK_MODEL_REFERENCE_THEN_EXTERNAL_PACK_DISABLED' and proof['sourceStates']==156,'Final native source model proof is missing'
    add(finalProbe,'bloodborne complete/'+finalProbe.relative_to(ROOT).as_posix())
    compatibilityArg=a.native_proof_compatibility_report or accepted.get('nativeProofCompatibilityReport')
    proofJarSha=a.native_source_proof_production_sha256 or accepted.get('nativeSourceModelProofProductionJarSha256') or proof.get('productionJarSha256')
    compatibilityBinding=None;finalJarSha=sha(jar)
    if compatibilityArg:
        compatibilityPath=ordinary(compatibilityArg);assert compatibilityPath.is_relative_to(ROOT)
        compatible=read(compatibilityPath)
        assert compatible['status']=='PASS' and compatible['sourceStates']==156 and compatible['relevantDomainsUnchanged'] is True
        assert compatible['targetProductionJarSha256']==finalJarSha and compatible['nativeSourceModelProofSha256']==sha(finalProbe)
        if proofJarSha:assert compatible['sourceProductionJarSha256']==proofJarSha
        proofJarSha=compatible['sourceProductionJarSha256']
        compatibilityBinding={'path':str(compatibilityPath),'sha256':sha(compatibilityPath),'sourceProductionJarSha256':proofJarSha,'targetProductionJarSha256':finalJarSha,'sourceStates':156,'relevantDomainsUnchanged':True}
        add(compatibilityPath,'bloodborne complete/'+compatibilityPath.relative_to(ROOT).as_posix())
    if proofJarSha:
        assert len(proofJarSha)==64 and all(char in '0123456789abcdef' for char in proofJarSha)
        assert proofJarSha==finalJarSha or compatibilityBinding,'A native proof from another JAR requires explicit component byte-identity evidence'
    if a.archive:assert proofJarSha,'Identify the actual JAR used for the native source model proof'
    modelBinding={'proof':str(finalProbe),'proofSha256':sha(finalProbe),'proofProductionJarSha256':proofJarSha,
        'finalProductionJarSha256':finalJarSha,'sameJarNativeExecution':proofJarSha==finalJarSha,
        'mode':'COMPONENT_BYTE_IDENTITY' if proofJarSha!=finalJarSha and compatibilityBinding else 'DIRECT_JAR' if proofJarSha==finalJarSha else 'PROOF_ORIGIN_NOT_YET_BOUND',
        'compatibilityReport':compatibilityBinding,
        'scope':'156 source model resources/native source classes only. Client cache/unload and performance acceptance must run against the final JAR separately.'}
    resumeArg=accepted.get('nativeConversionResumeProof') or accepted.get('converterResumeProof')
    for field in ('buildReport','gameTestResult','nativePerformanceReport','nativeUnloadProbe','converterAuthorBuildReport','converterInputCopyReport','converterResumeProof','nativeConversionResumeProof','nativeConverterAuthorClassBinding','preparationReadiness','gallerySavedReopenProof'):
        if accepted.get(field):
            path=ordinary(accepted[field]);assert path.is_relative_to(ROOT)
            add(path,'bloodborne complete/'+path.relative_to(ROOT).as_posix())
    converterEvidence=None
    if accepted.get('converterAuthorBuildReport'):
        built=read(ordinary(accepted['converterAuthorBuildReport']))
        copied=read(ordinary(accepted['converterInputCopyReport']))
        assert resumeArg,'Native converter resume proof is required'
        resumed=read(ordinary(resumeArg))
        assert built['status']=='PASS_AUTHOR_BUILD' and built['productionJarSha256']==finalJarSha and built['productionJarUnchanged']
        authorJar=ordinary(built['addon']);assert authorJar.is_relative_to(ROOT) and sha(authorJar)==built['addonSha256']
        assert copied['status']=='PASS_LOCAL_CONVERTER_INPUTS_COPIED_AND_VERIFIED' and copied['productionJarSha256']==finalJarSha
        assert copied['authorJarSha256']==built['addonSha256'] and copied['authorBuildReportSha256']==sha(accepted['converterAuthorBuildReport'])
        assert resumed['status']=='PASS_NATIVE_CONVERTER_ALIAS_REPLAY_TYPED_NBT_BANK_AND_SHIFT_GUARDS' and resumed['productionJarSha256']==finalJarSha
        assert resumed['fixtureOnly'] and resumed['freshInstallations']==2 and resumed['exactReplays']==4 and resumed['durableCursorPhase0Line0Verified']
        assert not resumed['originalCityConverted'] and all(row['status']=='PASS' for row in resumed['cases'])
        add(authorJar,'bloodborne complete/'+authorJar.relative_to(ROOT).as_posix())
        authorInit=ordinary(built['initScript']);assert authorInit.is_relative_to(ROOT)
        add(authorInit,'bloodborne complete/'+authorInit.relative_to(ROOT).as_posix())
        for path in files_without_links(authorInit.parent/'resources'):add(path,'bloodborne complete/'+path.relative_to(ROOT).as_posix())
        classBinding=read(ordinary(accepted['nativeConverterAuthorClassBinding']))
        assert classBinding['status']=='PASS_NORMAL_AUTHOR_CLASSES_BYTE_IDENTICAL_TO_NATIVE_VERIFIED_AUTHOR'
        assert classBinding['productionJarSha256']==finalJarSha and classBinding['normalAuthorSha256']==built['addonSha256']
        assert classBinding['nativeConversionResumeProofSha256']==sha(resumeArg)
        assert classBinding['authorSourceSha256']==sha(ordinary(classBinding['authorSource']))
        with zipfile.ZipFile(authorJar) as z:
            assert len(classBinding['classMembers'])>=3
            for item in classBinding['classMembers']:assert hashlib.sha256(z.read(item['path'])).hexdigest()==item['sha256']
        converterEvidence={'normalAuthorJarSha256':built['addonSha256'],'authorBuildReportSha256':sha(accepted['converterAuthorBuildReport']),
            'inputCopyReportSha256':sha(accepted['converterInputCopyReport']),'nativeResumeProofSha256':sha(resumeArg),
            'nativeAuthorClassBindingSha256':sha(accepted['nativeConverterAuthorClassBinding']),
            'nativeResumeReloadScope':resumed['reloadScope'],'originalCityConverted':False}
    if a.archive:assert converterEvidence,'Final repaired converter build/copy/native resume evidence is required'
    if a.acceptance:
        acceptance=ordinary(a.acceptance);add(acceptance,'bloodborne complete/build/final-city-fixes/'+acceptance.name)
    dependencies={name:DONOR/'mods'/name for name in ('worldedit-mod-7.2.15.jar','fabric-api-0.92.9+1.20.1.jar','geckolib-fabric-1.20.1-4.4.9.jar')}
    for name,path in dependencies.items():add(path,'bloodborne complete/build/release-dependencies/'+name)
    eula=ordinary(DONOR/'eula.txt')
    assert any(line.strip()=='eula=true' for line in eula.read_text(encoding='utf-8-sig').splitlines()),'Existing accepted EULA evidence required'
    add(eula,'bloodborne complete/build/release-dependencies/accepted-eula-evidence.txt')
    bootstrap=BASE/'build/v127-server-bootstrap';b=read(bootstrap/'bootstrap.json');assert b['status']=='PASS_PREPARED_NATIVE_NOT_RUN'
    add(bootstrap/'bootstrap.json','bloodborne complete/build/v1271-server-bootstrap/bootstrap.json')
    add(bootstrap/'fabric-loader-profile.json','bloodborne complete/build/v1271-server-bootstrap/fabric-loader-profile.json')
    assert sha(bootstrap/'fabric-loader-profile.json')==b['officialProfileSha256']
    add(bootstrap/'minecraft-version.json','bloodborne complete/build/v1271-server-bootstrap/minecraft-version.json')
    add(bootstrap/b['launcher']['path'],'bloodborne complete/build/v1271-server-bootstrap/'+b['launcher']['path'])
    assert sha(bootstrap/b['launcher']['path'])==b['launcher']['sha256']
    for item in b['runtimeFiles']:
        path=bootstrap/item['sourcePath'];assert sha(path)==item['sha256'];add(path,'bloodborne complete/build/v1271-server-bootstrap/'+item['sourcePath'])
    # Include the downloaded server as well as its verified runtime cache layout
    # expected by the existing staging API, without rerunning bootstrap download.
    serverPath=bootstrap/b['server']['path']
    if 'bloodborne complete/build/v1271-server-bootstrap/'+b['server']['path'] not in entries:add(serverPath,'bloodborne complete/build/v1271-server-bootstrap/'+b['server']['path'])
    rows=[{'name':name,'source':str(path),'bytes':path.stat().st_size,'sha256':sha(path)} for name,path in sorted(entries.items())]
    tree=hashlib.sha256()
    for row in rows:tree.update((row['name']+'\0'+row['sha256']+'\n').encode('utf8'))
    head=subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip()
    branch=subprocess.check_output(['git','branch','--show-current'],cwd=ROOT,text=True).strip()
    record={'schemaVersion':1,'status':'PASS_REVIEWED_FULL_SOURCE_ARCHIVE_RECIPE_NOT_ARCHIVED','productionJar':str(jar),'productionJarSha256':sha(jar),
        'sourcePlanSha256':m['sourcePlanSha256'],'runtimeManifestSha256':sha(prep/'runtime/manifest.json'),'sourceTreeRecipeSha256':tree.hexdigest(),'gitBaselineHead':head,'gitBranch':branch,
        'sourceIdentityPolicy':'Filesystem entries and per-file SHA identify the exact working source, including untracked new files. Git baseline alone is not the source snapshot.',
        'archiveName':NAME,'files':rows,'fileCount':len(rows),'logicalBytes':sum(row['bytes'] for row in rows),'sourceArchivesIncluded':False,'preparedCityDataIncluded':True,
        'originalMapRescanned':False,'weightedChoicesRecomputed':False,'jvmLaunched':False,'worldConverted':False,
        'buildPins':{'javaRelease':17,'gradle':'8.8','loom':'1.6.12','minecraft':'1.20.1','yarn':'1.20.1+build.10:v2','loader':'0.16.10','fabricApi':'0.92.9+1.20.1','geckolib':'4.4.9','mclib':'20','worldeditCompileApi':'7.2.15','modVersion':'0.1.0-v12.7.1-city-fixes'},
        'reproducibilityClaim':'Inputs and tool versions are pinned; ZIP order/timestamps deterministic. Independent clean byte-identical Gradle rebuild was not run during this packaging recipe.',
        'sourceArchivePaths':m['sourceArchivePaths'],'sourceArchiveHashes':m['sourceHashes'],
        'acceptedEulaEvidence':{'existingFile':str(eula),'sha256':sha(eula),'archivePath':'bloodborne complete/build/release-dependencies/accepted-eula-evidence.txt','newTermsAccepted':False},
        'serverBootstrapIncluded':{'launcherSha256':b['launcher']['sha256'],'serverSha256':b['server']['sha256'],'runtimeFiles':len(b['runtimeFiles'])},
        'buildCacheIncluded':False,'offlineBuildRequiresExistingGradleCache':True,'nativeValidationHarnessIncluded':True,
        'toolRuntime':{'python':platform.python_version(),'numpy':version('numpy'),'Pillow':version('Pillow')},
        'nativeGameTestEvidence':{'xml':str(xml),'sha256':sha(xml),'tests':testCount,'runName':runName},'nativeSourceModelProofBinding':modelBinding,'finalClientModelProofIncluded':True,'converterEvidence':converterEvidence,
        'qaHarnessPathPolicy':'Saved QA init/config/scripts retain original local C paths; rebase QA-only paths after unpacking elsewhere. Production Gradle source build uses relative source paths.'}
    if a.archive:
        assert a.acceptance,'Final native acceptance is required before release compression'
        assert accepted['productionJarSha256']==record['productionJarSha256']
        dest=ordinary(a.destination);dest.mkdir(parents=True,exist_ok=True);target=dest/NAME
        releaseManifest=dest/'full-source-release-manifest.json'
        assert not target.exists() and not releaseManifest.exists(),'Never overwrite an existing source release'
        result=archive(target,[(path,name) for name,path in entries.items()]);record.update(status='PASS_FULL_SOURCE_AND_PREPARATION_ARCHIVED',archive=str(target),archiveSha256=sha(target),archiveVerification=result)
        write(releaseManifest,record)
    write(ordinary(a.output),record)

if __name__=='__main__':
    try:main()
    except BaseException:write(ROOT/'build/final-city-fixes/full-source-package-recipe-error.json',{'error':traceback.format_exc()});raise
