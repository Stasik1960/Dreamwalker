"""Build isolated compatibility/renderer QA; no QA classes enter the production JAR."""
from pathlib import Path
import argparse,hashlib,json,os,subprocess,zipfile,copy
ROOT=Path(__file__).resolve().parents[1];DOC=ROOT/'docs/v12.4-dedup'
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def write(p,data):p.write_bytes((json.dumps(data,ensure_ascii=False,indent=2)+'\n').encode('utf8'))
def main():
    p=argparse.ArgumentParser();p.add_argument('--production-jar',type=Path,required=True);p.add_argument('--output',type=Path,required=True);p.add_argument('--legacy-author',action='store_true');a=p.parse_args()
    output=a.output.resolve();assert output.is_relative_to(ROOT/'build') and not output.exists();output.mkdir(parents=True)
    resources=output/'resources';resources.mkdir();production=a.production_jar.resolve();digest=sha(production)
    manifest=json.loads((ROOT/'src/architecture/resources/bloodborne_dw/imported-type-aliases.json').read_text(encoding='utf8'))
    fixtures=[]
    for m in manifest['mappings']:
        row={'groupId':m['newId'],'oldId':m['oldId'],'oldRegistryId':m['oldRegistryId'],'oldVariant':m['oldVariant'],'mainId':m['newId'],'mainRegistryId':m['newRegistryId'],'variant':m['newVariant'],'yawDeltaSteps':m['rotationDelta'],'sourceShiftDelta':m['sourceShift'],'properties':m['properties']}
        fixtures.append(row)
    write(DOC/'final-native-comparison-fixtures.json',{'schemaVersion':1,'status':'PENDING_NATIVE_GAME_COMPARISON','fixtures':fixtures})
    write(resources/'v124-type-state-mapping.json',manifest)
    visualfixtures=copy.deepcopy(fixtures)
    retained_count=0;functional_count=0;unchanged_count=0;alternate_count=0;alternate_source_count=0
    baseline=json.loads((DOC/'baseline-content.json').read_text(encoding='utf8'))
    if not a.legacy_author:
        mapped={(row['oldRegistryId'],row['oldVariant']) for row in fixtures}
        numbers={row['registryId']:str(row['temporaryId']) for row in baseline['debugCatalogue']['entries']}
        for path,descriptor in baseline['descriptors'].items():
            registry='bloodborne_dw:'+path
            for variant in range(len(descriptor['variants'])):
                if (registry,variant) in mapped:continue
                number=numbers[registry]
                visualfixtures.append({'kind':'SOURCE_CATALOGUE_REVIEW','groupId':number,'oldId':number,'oldRegistryId':registry,'oldVariant':variant,'mainId':number,'mainRegistryId':registry,'variant':variant,'yawDeltaSteps':0,'sourceShiftDelta':[0,0,0],'properties':{}})
                unchanged_count+=1
    extra=DOC/'additional-native-review-fixtures.json'
    if extra.exists() and not a.legacy_author:
        reviewed=json.loads(extra.read_text(encoding='utf8'))['fixtures']
        assert all(r.get('kind')=='RETAINED_REVIEW' for r in reviewed)
        visualfixtures.extend(reviewed)
        retained_count=len(reviewed)
    functional=DOC/'additional-functional-stair-review-fixtures.json'
    if functional.exists() and not a.legacy_author:
        forms=json.loads(functional.read_text(encoding='utf8'))['fixtures']
        assert all(r.get('kind')=='FUNCTIONAL_NEW_STATE' for r in forms)
        visualfixtures.extend(forms);functional_count=len(forms)
    alternate=DOC/'additional-migration-review-fixtures.json'
    if alternate.exists() and not a.legacy_author:
        views=json.loads(alternate.read_text(encoding='utf8'))['fixtures']
        assert all(r.get('kind') in {'MIGRATION_ALT_VIEW','SOURCE_CATALOGUE_ALT_VIEW'} for r in views)
        visualfixtures.extend(views);alternate_count=sum(r.get('kind')=='MIGRATION_ALT_VIEW' for r in views);alternate_source_count=sum(r.get('kind')=='SOURCE_CATALOGUE_ALT_VIEW' for r in views)
    write(resources/'v124-visual-comparison.json',{'schemaVersion':1,'migrationPairCount':len(fixtures),'unchangedSourceStateReviewPairCount':unchanged_count,'retainedReviewPairCount':retained_count,'functionalStairReviewPairCount':functional_count,'alternateMigrationReviewPairCount':alternate_count,'alternateSourceReviewPairCount':alternate_source_count,'fixtures':visualfixtures})
    (resources/'v124-baseline-content.json').write_bytes((DOC/'baseline-content.json').read_bytes())
    write(resources/'v124-qa-input.json',{'productionJarSha256':digest,'baselineJarSha256':'34efa3a0f8a1506c97a754fb5c887db977ca489b3cc514c84748de07e3688054','mode':'legacy-author' if a.legacy_author else 'audit'})
    metadata={'schemaVersion':1,'id':'dreamwalker_v124_qa','version':'1.0.0','environment':'*','entrypoints':{'main':['dev.dreamwalker.bloodbornedw.v124qa.LegacyTypeSaveAuthor' if a.legacy_author else 'dev.dreamwalker.bloodbornedw.v124qa.TypeNativeAudit']},'depends':{'minecraft':'1.20.1','java':'>=17','fabricloader':'>=0.16.10','fabric-api':'*','bloodborne_dw':'*'}}
    if not a.legacy_author:metadata['entrypoints']['client']=['dev.dreamwalker.bloodbornedw.v124qa.TypeComparisonClientAudit']
    write(resources/'fabric.mod.json',metadata)
    addonname='dreamwalker-v124-legacy-author' if a.legacy_author else 'dreamwalker-v124-qa'
    includes="java.include '**/LegacyTypeSaveAuthor.java','**/V124QaEvidence.java'" if a.legacy_author else ''
    init=output/'v124qa.init.gradle';init.write_bytes(('''gradle.projectsEvaluated {
 def p=gradle.rootProject
 p.sourceSets { v124qa {
  java.srcDirs=['tools/v124-qa'];resources.srcDirs=['RESOURCES']
  QA_INCLUDES
  compileClasspath += p.sourceSets.main.output + p.sourceSets.main.compileClasspath
  runtimeClasspath += p.sourceSets.main.output + p.sourceSets.main.runtimeClasspath
 } }
 p.configurations.v124qaImplementation.extendsFrom(p.configurations.implementation)
 p.configurations.v124qaRuntimeOnly.extendsFrom(p.configurations.runtimeOnly)
 def qa=p.tasks.register('v124QaJar',Jar) {dependsOn p.tasks.named('v124qaClasses');from p.sourceSets.v124qa.output;archiveBaseName='ADDON';archiveVersion='1.0.0';archiveClassifier='dev'}
 def remapType=p.tasks.named('remapJar').get().getClass().superclass
 p.tasks.register('remapV124QaJar',remapType) {dependsOn qa;inputFile.set(qa.flatMap{it.archiveFile});classpath.from(p.sourceSets.main.output,p.sourceSets.main.compileClasspath);archiveBaseName='ADDON';archiveVersion='1.0.0';archiveClassifier='';addNestedDependencies=false}
}'''.replace('RESOURCES',resources.as_posix()).replace('ADDON',addonname).replace('QA_INCLUDES',includes)).encode('utf8'))
    cmd=[str(ROOT/'gradlew.bat'),'--offline','--no-daemon','-I',str(init),'remapV124QaJar'];env=dict(os.environ,JAVA_HOME='C:/Users/vakir/AppData/Roaming/.minecraft/runtime/java-runtime-gamma/windows/java-runtime-gamma')
    with (output/'build.log').open('wb') as log:code=subprocess.run(cmd,cwd=ROOT,env=env,stdout=log,stderr=subprocess.STDOUT).returncode
    addon=ROOT/'build/libs'/f'{addonname}-1.0.0.jar';unchanged=sha(production)==digest
    result={'status':'PASS' if code==0 and addon.is_file() and unchanged else 'FAIL','exitCode':code,'productionJarSha256':digest,'productionJarUnchanged':unchanged,'legacyAuthor':a.legacy_author}
    if result['status']=='PASS':
        with zipfile.ZipFile(addon) as z:assert z.testzip() is None and json.loads(z.read('fabric.mod.json'))==metadata
        result.update(addon=str(addon),addonSha256=sha(addon),bytes=addon.stat().st_size)
    write(output/'build-report.json',result);assert result['status']=='PASS'
if __name__=='__main__':main()
