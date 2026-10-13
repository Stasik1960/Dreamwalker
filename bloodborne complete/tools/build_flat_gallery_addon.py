"""Build only the isolated flat-gallery QA add-on, never the production JAR.

Run through CodexControl/Guard.ps1 -Mode Operation. The temporary Gradle source
set and its metadata live under build/; existing Gradle/gallery files are read.
"""
from __future__ import annotations
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]

def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--production-jar', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--java-home', type=Path, default=Path('C:/Users/vakir/AppData/Roaming/.minecraft/runtime/java-runtime-gamma/windows/java-runtime-gamma'))
    args = parser.parse_args()
    output = args.output.resolve()
    assert output.is_relative_to((ROOT / 'build').resolve()) and not output.exists(), 'Fresh project build output required'
    assert args.production_jar.is_file(), 'Exact production JAR required'
    production = args.production_jar.resolve()
    digest = sha(production)
    output.mkdir(parents=True)
    resources = output / 'resources'
    metadata = {'schemaVersion': 1, 'id': 'dreamwalker_gallery_author', 'version': '1.0.0',
        'name': 'Dreamwalker flat gallery author and verifier', 'environment': '*',
        'entrypoints': {'main': ['dev.dreamwalker.bloodbornedw.gallery.FlatGalleryAuthor'],
                       'client': ['dev.dreamwalker.bloodbornedw.gallery.FlatGalleryClientCapture']},
        'depends': {'minecraft': '1.20.1', 'java': '>=17', 'fabricloader': '>=0.16.10',
                    'fabric-api': '*', 'bloodborne_dw': '*'},
        'description': 'Explicit-marker QA only. Saved gallery opens with production mod and dependencies.'}
    write(resources / 'fabric.mod.json', metadata)
    # Forward-slash absolute paths contain no shell expansion and are Groovy-quoted.
    resource_arg = str(resources).replace('\\', '/').replace("'", "\\'")
    init = output / 'flat-gallery.init.gradle'
    init.write_text('''// Generated opt-in QA source set. No production archive task is requested.
gradle.projectsEvaluated {
    def project = gradle.rootProject
    project.sourceSets {
        flatgallery {
            java.srcDirs = ['src/gallery/java']
            java.include '**/FlatGalleryAuthor.java', '**/FlatGalleryClientCapture.java'
            resources.srcDirs = ['RESOURCE_ARG']
            compileClasspath += project.sourceSets.main.output + project.sourceSets.main.compileClasspath
            runtimeClasspath += project.sourceSets.main.output + project.sourceSets.main.runtimeClasspath
        }
    }
    project.configurations.flatgalleryImplementation.extendsFrom(project.configurations.implementation)
    project.configurations.flatgalleryRuntimeOnly.extendsFrom(project.configurations.runtimeOnly)
    def qaJar = project.tasks.register('flatGalleryQaJar', Jar) {
        dependsOn project.tasks.named('flatgalleryClasses')
        from project.sourceSets.flatgallery.output
        archiveBaseName = 'dreamwalker-flat-gallery-author'
        archiveVersion = '1.0.0'
        archiveClassifier = 'dev'
    }
    def remapType = project.tasks.named('remapJar').get().getClass().superclass
    project.tasks.register('remapFlatGalleryQaJar', remapType) {
        dependsOn qaJar
        inputFile.set(qaJar.flatMap { it.archiveFile })
        classpath.from(project.sourceSets.main.output, project.sourceSets.main.compileClasspath)
        archiveBaseName = 'dreamwalker-flat-gallery-author'
        archiveVersion = '1.0.0'
        archiveClassifier = ''
        addNestedDependencies = false
    }
}
'''.replace('RESOURCE_ARG', resource_arg), encoding='utf-8')
    command = [str(ROOT / 'gradlew.bat'), '--offline', '--no-daemon', '-I', str(init), 'remapFlatGalleryQaJar']
    result = {'schema': 'dreamwalker-flat-gallery-qa-build-v1', 'status': 'RUNNING',
              'productionJar': str(production), 'productionJarSha256': digest, 'command': command}
    write(output / 'build-report.json', result)
    environment = dict(os.environ)
    environment['JAVA_HOME'] = str(args.java_home.resolve())
    with (output / 'build.log').open('w', encoding='utf-8') as stream:
        process = subprocess.run(command, cwd=ROOT, env=environment, stdout=stream, stderr=subprocess.STDOUT)
    jar = ROOT / 'build/libs/dreamwalker-flat-gallery-author-1.0.0.jar'
    result.update(exitCode=process.returncode, productionJarUnchanged=sha(production) == digest)
    if process.returncode == 0 and jar.is_file():
        with zipfile.ZipFile(jar) as archive:
            entries = archive.namelist()
            assert archive.testzip() is None
            assert json.loads(archive.read('fabric.mod.json')) == metadata
            assert 'dev/dreamwalker/bloodbornedw/gallery/FlatGalleryAuthor.class' in entries
            assert 'dev/dreamwalker/bloodbornedw/gallery/FlatGalleryClientCapture.class' in entries
            assert not any('/GalleryAuthor.class' in p or p.startswith('assets/') for p in entries), 'Production/old gallery files leaked'
        result.update(addon=str(jar), addonSha256=sha(jar), bytes=jar.stat().st_size)
    result['status'] = 'PASS' if result.get('addon') and result['productionJarUnchanged'] and process.returncode == 0 else 'FAIL'
    write(output / 'build-report.json', result)
    if result['status'] != 'PASS':
        raise SystemExit(1)

if __name__ == '__main__':
    try:
        main()
    except Exception as failure:
        if '--output' in os.sys.argv:
            folder = Path(os.sys.argv[os.sys.argv.index('--output') + 1])
            write(folder / 'build-error.json', {'status': 'FAIL', 'failure': repr(failure)})
        raise
