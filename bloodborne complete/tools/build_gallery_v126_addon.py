"""Build the opt-in V12.6 gallery author/capture add-on, never production.

Run through CodexControl/Guard.ps1 -Mode Operation after the final production
JAR exists. Sources and resources for this isolated task live under tools/ and
fresh build output; previous gallery sources remain unchanged.
"""
from __future__ import annotations
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import zipfile
from build_gallery_v122_addon import capture_java, CAMERA_MIXIN

ROOT = Path(__file__).resolve().parents[1]


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf8', newline='\n')


def quoted(path):
    return str(path).replace('\\', '/').replace("'", "\\'")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--production-jar', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--java-home', type=Path, default=Path('C:/Users/vakir/AppData/Roaming/.minecraft/runtime/java-runtime-gamma/windows/java-runtime-gamma'))
    args = parser.parse_args()
    output = args.output.resolve()
    assert output.is_relative_to((ROOT / 'build').resolve()) and not output.exists(), 'Fresh project build output required'
    production = args.production_jar.resolve()
    assert production.is_file(), 'Exact final production JAR required'
    digest = sha(production)
    output.mkdir(parents=True)
    resources = output / 'resources'
    generated = output / 'generated-java/dev/dreamwalker/bloodbornedw/gallery'
    generated.mkdir(parents=True)
    capture = capture_java(ROOT).replace('FlatGalleryV122ClientCapture', 'FlatGalleryV126ClientCapture')
    anchor = 'for(String key:List.of("variant","profile","open"))'
    assert capture.count(anchor) == 1, 'Native capture root-state anchor changed'
    capture = capture.replace(anchor, 'for(String key:List.of("variant","profile","open","rotation","half","shape","trim","connections","height","art_bank","mount"))')
    # Existing capture controller operates only after its explicit capture
    # marker; its narrow GameRenderer hook never writes world blocks.
    (generated / 'FlatGalleryV126ClientCapture.java').write_text(capture, encoding='utf8', newline='\n')
    mixin = generated / 'capture/mixin/FlatGalleryV126CameraMixin.java'
    mixin.parent.mkdir(parents=True)
    mixin.write_text(CAMERA_MIXIN.replace('V122', 'V126'), encoding='utf8', newline='\n')
    metadata = {'schemaVersion': 1, 'id': 'dreamwalker_gallery_author', 'version': '1.2.7',
                'name': 'Dreamwalker V12.6 isolated gallery author and capture', 'environment': '*',
                'entrypoints': {'main': ['dev.dreamwalker.bloodbornedw.gallery.FlatGalleryV126Author'],
                               'client': ['dev.dreamwalker.bloodbornedw.gallery.FlatGalleryV126ClientCapture']},
                'mixins': [{'config': 'dreamwalker-gallery-v126-capture.mixins.json', 'environment': 'client'}],
                'depends': {'minecraft': '1.20.1', 'java': '>=17', 'fabricloader': '>=0.16.10',
                            'fabric-api': '*', 'bloodborne_dw': '*'},
                'description': 'Explicit fresh-test-world marker only. Saved gallery requires the production mod and its normal dependencies.'}
    write(resources / 'fabric.mod.json', metadata)
    write(resources / 'dreamwalker-gallery-v126-capture.mixins.json', {
        'required': True, 'minVersion': '0.8', 'package': 'dev.dreamwalker.bloodbornedw.gallery.capture.mixin',
        'compatibilityLevel': 'JAVA_17', 'client': ['FlatGalleryV126CameraMixin'], 'injectors': {'defaultRequire': 1}})
    init = output / 'gallery-v126.init.gradle'
    init.write_text('''// QA source set for a fresh V12.6 gallery. No production archive task.
gradle.projectsEvaluated {
    def project = gradle.rootProject
    project.sourceSets {
        flatgallery {
            java.srcDirs = ['AUTHOR_ARG', 'GENERATED_ARG']
            java.include '**/FlatGalleryV126Author.java', '**/FlatGalleryV126ClientCapture.java', '**/FlatGalleryV126CameraMixin.java'
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
        archiveBaseName = 'dreamwalker-flat-gallery-v126-author'
        archiveVersion = '1.2.7'
        archiveClassifier = 'dev'
    }
    def remapType = project.tasks.named('remapJar').get().getClass().superclass
    project.tasks.register('remapFlatGalleryQaJar', remapType) {
        dependsOn qaJar
        inputFile.set(qaJar.flatMap { it.archiveFile })
        classpath.from(project.sourceSets.main.output, project.sourceSets.main.compileClasspath)
        archiveBaseName = 'dreamwalker-flat-gallery-v126-author'
        archiveVersion = '1.2.7'
        archiveClassifier = ''
        addNestedDependencies = false
    }
}
'''.replace('AUTHOR_ARG', quoted(ROOT / 'tools/gallery-v126/java'))
        .replace('GENERATED_ARG', quoted(output / 'generated-java'))
        .replace('RESOURCE_ARG', quoted(resources)), encoding='utf8', newline='\n')
    command = [str(ROOT / 'gradlew.bat'), '--offline', '--no-daemon', '-I', str(init), 'remapFlatGalleryQaJar']
    report = {'schema': 'dreamwalker-flat-gallery-v126-qa-build', 'status': 'RUNNING',
              'productionJar': str(production), 'productionJarSha256': digest, 'command': command}
    write(output / 'build-report.json', report)
    environment = dict(os.environ)
    environment['JAVA_HOME'] = str(args.java_home.resolve())
    with (output / 'build.log').open('w', encoding='utf8', newline='\n') as stream:
        process = subprocess.run(command, cwd=ROOT, env=environment, stdout=stream, stderr=subprocess.STDOUT)
    jar = ROOT / 'build/libs/dreamwalker-flat-gallery-v126-author-1.2.7.jar'
    report.update(exitCode=process.returncode, productionJarUnchanged=sha(production) == digest)
    if process.returncode == 0 and jar.is_file():
        with zipfile.ZipFile(jar) as archive:
            entries = archive.namelist()
            assert archive.testzip() is None
            assert json.loads(archive.read('fabric.mod.json')) == metadata
            for name in ('FlatGalleryV126Author', 'FlatGalleryV126ClientCapture'):
                assert 'dev/dreamwalker/bloodbornedw/gallery/' + name + '.class' in entries
            assert not any(path.startswith('assets/') or path.startswith('bloodborne_dw/') for path in entries), 'Production resources leaked into the isolated add-on'
        report.update(addon=str(jar), addonSha256=sha(jar), bytes=jar.stat().st_size)
    report['status'] = 'PASS' if report.get('addon') and report['productionJarUnchanged'] and process.returncode == 0 else 'FAIL'
    write(output / 'build-report.json', report)
    if report['status'] != 'PASS':
        raise SystemExit(1)


if __name__ == '__main__':
    try:
        main()
    except Exception as failure:
        if '--output' in os.sys.argv:
            write(Path(os.sys.argv[os.sys.argv.index('--output') + 1]) / 'build-error.json', {'status': 'FAIL', 'failure': repr(failure)})
        raise
