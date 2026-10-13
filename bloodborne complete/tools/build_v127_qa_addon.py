"""Root-run serial opt-in native function QA build; no gallery or production modifications."""
from pathlib import Path
import argparse
import hashlib
import json
import os
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]

def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def write(path, value):
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--production-jar', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    production = args.production_jar.resolve()
    digest = sha(production)
    output = args.output.resolve()
    assert output.is_relative_to(ROOT / 'build') and not output.exists()
    output.mkdir(parents=True)
    resources = output / 'resources'
    resources.mkdir()
    metadata = {'schemaVersion': 1, 'id': 'dreamwalker_gallery_author', 'version': '1.2.7-functions',
                'name': 'Dreamwalker isolated V12.7 function QA (no gallery)', 'environment': '*',
                'entrypoints': {'main': ['dev.dreamwalker.bloodbornedw.v127qa.NativeV127Fixture'],
                                'client': ['dev.dreamwalker.bloodbornedw.v127qa.NativeMultipartPredicateQa',
                                           'dev.dreamwalker.bloodbornedw.v127qa.NativeMultipartCoreQa',
                                           'dev.dreamwalker.bloodbornedw.v127qa.NativeV127ClientQa']},
                'depends': {'minecraft': '1.20.1', 'java': '>=17', 'fabricloader': '>=0.16.10', 'fabric-api': '*', 'bloodborne_dw': '*'}}
    write(resources / 'fabric.mod.json', metadata)
    # Existing runner validates this historical QA add-on id; it does not author any gallery.
    marker = {'productionJarSha256': digest, 'freshTestWorldOnly': True, 'v127NativeFunctions': True,
              'purpose': 'ISOLATED_FUNCTION_TEST_NOT_GALLERY', 'frames': [{'fileName': 'v127-function-proof.png', 'camera': [.5,64,-3.5,0,18]}]}
    write(output / 'client-capture-input.json', marker)
    init = output / 'v127qa.init.gradle'
    init.write_text('''gradle.projectsEvaluated {
 def p=gradle.rootProject
 p.sourceSets { v127qa {
  java.srcDirs=['src/v127qa/java'];resources.srcDirs=['RESOURCES']
  compileClasspath += p.sourceSets.main.output + p.sourceSets.main.compileClasspath
  runtimeClasspath += p.sourceSets.main.output + p.sourceSets.main.runtimeClasspath
 } }
 p.configurations.v127qaImplementation.extendsFrom(p.configurations.implementation)
 p.configurations.v127qaRuntimeOnly.extendsFrom(p.configurations.runtimeOnly)
 def qa=p.tasks.register('v127QaJar',Jar) {dependsOn p.tasks.named('v127qaClasses');from p.sourceSets.v127qa.output;archiveBaseName='dreamwalker-v127-native-functions-qa';archiveVersion='1.2.7';archiveClassifier='dev'}
 def remapType=p.tasks.named('remapJar').get().getClass().superclass
 p.tasks.register('remapV127QaJar',remapType) {dependsOn qa;inputFile.set(qa.flatMap{it.archiveFile});classpath.from(p.sourceSets.main.output,p.sourceSets.main.compileClasspath);archiveBaseName='dreamwalker-v127-native-functions-qa';archiveVersion='1.2.7';archiveClassifier='';addNestedDependencies=false}
}'''.replace('RESOURCES', resources.as_posix()), encoding='utf-8')
    command = [str(ROOT / 'gradlew.bat'), '--offline', '--no-daemon', '-I', str(init), 'remapV127QaJar']
    environment = dict(os.environ, JAVA_HOME='C:/Users/vakir/AppData/Roaming/.minecraft/runtime/java-runtime-gamma/windows/java-runtime-gamma')
    with (output / 'build.log').open('wb') as log:
        code = subprocess.run(command, cwd=ROOT, env=environment, stdout=log, stderr=subprocess.STDOUT).returncode
    addon = ROOT / 'build/libs/dreamwalker-v127-native-functions-qa-1.2.7.jar'
    report = {'status': 'PASS' if code == 0 and addon.is_file() and sha(production) == digest else 'FAIL',
              'productionJarSha256': digest, 'productionJarUnchanged': sha(production) == digest, 'exitCode': code}
    if report['status'] == 'PASS':
        with zipfile.ZipFile(addon) as archive:
            assert archive.testzip() is None and json.loads(archive.read('fabric.mod.json')) == metadata
        report.update(addon=str(addon), addonSha256=sha(addon), captureInput=str(output / 'client-capture-input.json'))
    write(output / 'build-report.json', report)
    assert report['status'] == 'PASS', report

if __name__ == '__main__':
    main()
