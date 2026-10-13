"""Build isolated collision QA helper, never a production archive.

Guard.ps1 -Mode Operation owns the Gradle/Java process. Serialize this build
against production/gallery builds because their shared compilation caches match.
"""
from __future__ import annotations
import argparse
import hashlib
import gzip
import json
import os
from pathlib import Path
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]

def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')

def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--production-jar', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    output = args.output.resolve(); production = args.production_jar.resolve()
    assert output.is_relative_to((ROOT / 'build').resolve()) and not output.exists(), 'Fresh build output required'
    output.mkdir(parents=True); digest = sha(production)
    qa_sources = {str(p.relative_to(ROOT)).replace('\\', '/'): sha(p) for p in sorted((ROOT / 'src/collisionqa/java').rglob('*.java'))}
    metadata = {'schemaVersion': 1, 'id': 'dreamwalker_collision_audit', 'version': '1.0.0',
        'name': 'Dreamwalker NEW simple collision audit', 'environment': 'server',
        'entrypoints': {'main': ['dev.dreamwalker.bloodbornedw.collisionqa.SimpleCollisionRuntimeAudit']},
        'depends': {'minecraft': '1.20.1', 'java': '>=17', 'fabricloader': '>=0.16.10', 'fabric-api': '*', 'bloodborne_dw': '*'}}
    resources = output / 'resources'; write(resources / 'fabric.mod.json', metadata)
    # Immutable full prior descriptors give the aiming witness an independent
    # artifact input, including original selection, flags and pose geometry.
    previous = ROOT.parent / 'releases/Dreamwalker-BB/v12.1-resource-transfer/dreamwalker-bb-fabric-1.20.1-0.1.0-v12.1-resource-transfer.jar'
    assert sha(previous) == '9797fb4bb4b6c4103b60b994fb0c4ad4f88c46bc7a19de42eb596e7f0c57e117'
    with zipfile.ZipFile(previous) as archive:
        baseline = {json.loads(archive.read(n))['id']: json.loads(archive.read(n)) for n in archive.namelist() if n.startswith('bloodborne_dw/composite/source_') and n.endswith('.json')}
    (resources / 'qa-original-source-descriptors.json.gz').write_bytes(gzip.compress(json.dumps(baseline, ensure_ascii=False, separators=(',', ':')).encode('utf-8'), mtime=0))
    resource_arg = str(resources).replace('\\', '/').replace("'", "\\'")
    init = output / 'collision-audit.init.gradle'
    init.write_text('''gradle.projectsEvaluated {
    def p = gradle.rootProject
    p.sourceSets {
        collisionqa {
            java.srcDirs = ['src/collisionqa/java']
            resources.srcDirs = ['RESOURCE_ARG']
            compileClasspath += p.sourceSets.main.output + p.sourceSets.main.compileClasspath
            runtimeClasspath += p.sourceSets.main.output + p.sourceSets.main.runtimeClasspath
        }
    }
    p.configurations.collisionqaImplementation.extendsFrom(p.configurations.implementation)
    p.configurations.collisionqaRuntimeOnly.extendsFrom(p.configurations.runtimeOnly)
    def qa = p.tasks.register('collisionAuditJar', Jar) {
        dependsOn p.tasks.named('collisionqaClasses')
        from p.sourceSets.collisionqa.output
        archiveBaseName = 'dreamwalker-collision-audit'
        archiveVersion = '1.0.0'
        archiveClassifier = 'dev'
    }
    def remapType = p.tasks.named('remapJar').get().getClass().superclass
    p.tasks.register('remapCollisionAuditJar', remapType) {
        dependsOn qa
        inputFile.set(qa.flatMap { it.archiveFile })
        classpath.from(p.sourceSets.main.output, p.sourceSets.main.compileClasspath)
        archiveBaseName = 'dreamwalker-collision-audit'
        archiveVersion = '1.0.0'
        archiveClassifier = ''
        addNestedDependencies = false
    }
}
'''.replace('RESOURCE_ARG', resource_arg), encoding='utf-8')
    command = [str(ROOT / 'gradlew.bat'), '--offline', '--no-daemon', '-I', str(init), 'remapCollisionAuditJar']
    result = {'schema': 'dreamwalker-v122-collision-qa-build-v1', 'status': 'RUNNING', 'productionJar': str(production), 'productionJarSha256': digest, 'qaSourceSha256': qa_sources, 'command': command}
    write(output / 'build-report.json', result)
    env = dict(os.environ); env['JAVA_HOME'] = 'C:/Users/vakir/AppData/Roaming/.minecraft/runtime/java-runtime-gamma/windows/java-runtime-gamma'
    with (output / 'build.log').open('w', encoding='utf-8') as stream:
        process = subprocess.run(command, cwd=ROOT, env=env, stdout=stream, stderr=subprocess.STDOUT)
    jar = ROOT / 'build/libs/dreamwalker-collision-audit-1.0.0.jar'
    result.update(exitCode=process.returncode, productionJarUnchanged=sha(production) == digest, qaSourcesUnchanged=qa_sources == {str(p.relative_to(ROOT)).replace('\\', '/'): sha(p) for p in sorted((ROOT / 'src/collisionqa/java').rglob('*.java'))})
    if process.returncode == 0 and jar.is_file():
        with zipfile.ZipFile(jar) as archive:
            assert archive.testzip() is None
            assert json.loads(archive.read('fabric.mod.json')) == metadata
            assert all(n.startswith('dev/dreamwalker/bloodbornedw/collisionqa/') or n.endswith('/') or n in {'fabric.mod.json', 'META-INF/MANIFEST.MF', 'qa-original-source-descriptors.json.gz'} for n in archive.namelist())
        result.update(addon=str(jar), addonSha256=sha(jar), bytes=jar.stat().st_size)
    result['status'] = 'PASS' if result.get('addon') and process.returncode == 0 and result['productionJarUnchanged'] and result['qaSourcesUnchanged'] else 'FAIL'
    write(output / 'build-report.json', result)
    if result['status'] != 'PASS': raise SystemExit(1)

if __name__ == '__main__':
    main()
