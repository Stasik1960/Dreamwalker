"""Build an isolated server diagnostic only; production/release JARs remain frozen."""
import hashlib,json,os,subprocess
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'build/v12-wall-audit-1'
assert not OUT.exists()
OUT.mkdir(parents=True)
production=ROOT/'build/libs/dreamwalker-bb-fabric-1.20.1-0.1.0-v12.2-simple-collisions.jar'
digest=hashlib.sha256(production.read_bytes()).hexdigest()
metadata={'schemaVersion':1,'id':'dreamwalker_wall_regression_audit','version':'1.0.0','environment':'server','entrypoints':{'main':['dev.dreamwalker.bloodbornedw.regressionqa.WallRegressionAudit']},'depends':{'minecraft':'1.20.1','fabricloader':'>=0.16.10','fabric-api':'*','bloodborne_dw':'*','java':'>=17'}}
resources=OUT/'resources';resources.mkdir();(resources/'fabric.mod.json').write_text(json.dumps(metadata),encoding='utf8')
init=OUT/'audit.init.gradle'
init.write_text('''gradle.projectsEvaluated {
 def p=gradle.rootProject
 p.sourceSets { wallaudit {
  java.srcDirs=['tools/v12-regression-audit']; resources.srcDirs=['RESOURCE']
  compileClasspath += p.sourceSets.main.output + p.sourceSets.main.compileClasspath
  runtimeClasspath += p.sourceSets.main.output + p.sourceSets.main.runtimeClasspath
 } }
 p.configurations.wallauditImplementation.extendsFrom(p.configurations.implementation)
 def qa=p.tasks.register('wallAuditJar', Jar) {
  dependsOn p.tasks.named('wallauditClasses'); from p.sourceSets.wallaudit.output
  archiveBaseName='dreamwalker-wall-regression-audit';archiveVersion='1.0.0';archiveClassifier='dev'
 }
 def remapType=p.tasks.named('remapJar').get().getClass().superclass
 p.tasks.register('remapWallAuditJar',remapType) {
  dependsOn qa;inputFile.set(qa.flatMap{it.archiveFile})
  classpath.from(p.sourceSets.main.output,p.sourceSets.main.compileClasspath)
  archiveBaseName='dreamwalker-wall-regression-audit';archiveVersion='1.0.0';archiveClassifier='';addNestedDependencies=false
 }
}'''.replace('RESOURCE',resources.as_posix()),encoding='utf8')
env=dict(os.environ);env['JAVA_HOME']='C:/Users/vakir/AppData/Roaming/.minecraft/runtime/java-runtime-gamma/windows/java-runtime-gamma'
command=[str(ROOT/'gradlew.bat'),'--offline','--no-daemon','-I',str(init),'remapWallAuditJar']
with (OUT/'build.log').open('w',encoding='utf8') as stream:code=subprocess.run(command,cwd=ROOT,env=env,stdout=stream,stderr=subprocess.STDOUT).returncode
unchanged=hashlib.sha256(production.read_bytes()).hexdigest()==digest
report={'status':'PASS' if code==0 and unchanged else 'FAIL','exitCode':code,'productionJarUnchanged':unchanged,'productionJarSha256':digest,'command':command}
(OUT/'build-report.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf8')
assert report['status']=='PASS',report
