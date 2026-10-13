"""Build isolated V12.3 native selection/icon audit; never assemble production archives."""
from __future__ import annotations
import argparse,hashlib,json,os,subprocess,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--production-jar',type=Path,required=True);parser.add_argument('--output',type=Path,required=True);args=parser.parse_args()
    production=args.production_jar.resolve();output=args.output.resolve();assert output.is_relative_to((ROOT/'build').resolve()) and not output.exists();output.mkdir(parents=True)
    digest=sha(production);sources={p.relative_to(ROOT).as_posix():sha(p) for p in sorted((ROOT/'tools/v123-qa').glob('*.java'))}
    metadata={'schemaVersion':1,'id':'dreamwalker_v123_qa','version':'1.0.0','environment':'*','entrypoints':{'main':['dev.dreamwalker.bloodbornedw.v123qa.OutlineNativeAudit'],'client':['dev.dreamwalker.bloodbornedw.v123qa.OutlineIconClientAudit']},'depends':{'minecraft':'1.20.1','java':'>=17','fabricloader':'>=0.16.10','fabric-api':'*','bloodborne_dw':'*'}}
    resources=output/'resources';resources.mkdir();(resources/'fabric.mod.json').write_text(json.dumps(metadata),encoding='utf8');(resources/'v123-qa-input.json').write_text(json.dumps({'productionJarSha256':digest}),encoding='utf8')
    init=output/'v123-qa.init.gradle';init.write_text('''gradle.projectsEvaluated {
 def p=gradle.rootProject
 p.sourceSets { v123qa {
  java.srcDirs=['tools/v123-qa'];resources.srcDirs=['RESOURCES']
  compileClasspath += p.sourceSets.main.output + p.sourceSets.main.compileClasspath
  runtimeClasspath += p.sourceSets.main.output + p.sourceSets.main.runtimeClasspath
 } }
 p.configurations.v123qaImplementation.extendsFrom(p.configurations.implementation)
 p.configurations.v123qaRuntimeOnly.extendsFrom(p.configurations.runtimeOnly)
 def qa=p.tasks.register('v123QaJar',Jar) {
  dependsOn p.tasks.named('v123qaClasses');from p.sourceSets.v123qa.output
  archiveBaseName='dreamwalker-v123-qa';archiveVersion='1.0.0';archiveClassifier='dev'
 }
 def remapType=p.tasks.named('remapJar').get().getClass().superclass
 p.tasks.register('remapV123QaJar',remapType) {
  dependsOn qa;inputFile.set(qa.flatMap{it.archiveFile});classpath.from(p.sourceSets.main.output,p.sourceSets.main.compileClasspath)
  archiveBaseName='dreamwalker-v123-qa';archiveVersion='1.0.0';archiveClassifier='';addNestedDependencies=false
 }
}'''.replace('RESOURCES',resources.as_posix()),encoding='utf8')
    command=[str(ROOT/'gradlew.bat'),'--offline','--no-daemon','-I',str(init),'remapV123QaJar'];env=dict(os.environ);env['JAVA_HOME']='C:/Users/vakir/AppData/Roaming/.minecraft/runtime/java-runtime-gamma/windows/java-runtime-gamma'
    result={'schema':'dreamwalker-v123-qa-build-v1','status':'RUNNING','productionJar':str(production),'productionJarSha256':digest,'qaSourceSha256':sources,'command':command};(output/'build-report.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf8')
    with (output/'build.log').open('w',encoding='utf8') as log:code=subprocess.run(command,cwd=ROOT,env=env,stdout=log,stderr=subprocess.STDOUT).returncode
    addon=ROOT/'build/libs/dreamwalker-v123-qa-1.0.0.jar';unchanged=sha(production)==digest
    result.update(exitCode=code,productionJarUnchanged=unchanged,status='PASS' if code==0 and unchanged and addon.is_file() else 'FAIL')
    if result['status']=='PASS':
        with zipfile.ZipFile(addon) as archive:assert archive.testzip() is None and json.loads(archive.read('fabric.mod.json'))==metadata
        result.update(addon=str(addon),addonSha256=sha(addon),bytes=addon.stat().st_size)
    (output/'build-report.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf8');assert result['status']=='PASS',result
if __name__=='__main__':main()
