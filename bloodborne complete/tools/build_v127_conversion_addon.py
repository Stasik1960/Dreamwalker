"""Build the explicitly enabled full-map author separately; no production mutation.

--prepare-only emits the opt-in Gradle init/resources without starting Java.
Root serializes all Java processes. JAVA_HOME17 (or JAVA_HOME) selects Java 17.
"""
import argparse
import json
import os
from pathlib import Path
import subprocess
import zipfile
from package_v127_release import ROOT, sha, ordinary


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--production-jar', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--runtime-directory', type=Path)
    parser.add_argument('--prepare-only', action='store_true')
    parser.add_argument('--offline', action='store_true')
    parser.add_argument('--worldedit-api-jar', type=Path, help='Optional pinned local compile-only WorldEdit API JAR')
    args = parser.parse_args()
    production = args.production_jar.resolve()
    digest = sha(production)
    assert ordinary(args.output), 'Reparsed add-on preparation destination rejected'
    output = args.output.resolve()
    assert output.is_relative_to(ROOT / 'build') and not output.exists()
    output.mkdir(parents=True)
    resources = output / 'resources'
    resources.mkdir()
    metadata = {'schemaVersion': 1, 'id': 'dreamwalker_full_conversion_author', 'version': '1.2.7',
                'name': 'Dreamwalker explicitly enabled full source conversion', 'environment': 'server',
                'entrypoints': {'main': ['dev.dreamwalker.bloodbornedw.conversion.FullMapConversionAuthor']},
                'depends': {'minecraft': '1.20.1', 'java': '>=17', 'fabricloader': '>=0.16.10',
                            'fabric-api': '*', 'bloodborne_dw': '*'}}
    (resources / 'fabric.mod.json').write_text(json.dumps(metadata, indent=2) + '\n', encoding='utf8')
    (resources / 'dw-conversion-binding.json').write_text(json.dumps({'schemaVersion': 1, 'productionJarSha256': digest,
                                                                    'disabledWithoutExplicitMarker': True}) + '\n', encoding='utf8')
    runtime = args.runtime_directory.absolute() if args.runtime_directory else ROOT / 'build/v127-conversion-disabled'
    def literal(path):
        return path.as_posix().replace("'", "\\'")
    init = output / 'v127conversion.init.gradle'
    init.write_text("""gradle.projectsEvaluated {
 def p=gradle.rootProject
 p.sourceSets { conversion {
  java.srcDirs=['src/conversion/java'];resources.srcDirs=['RESOURCES']
  compileClasspath += p.sourceSets.main.output + p.sourceSets.main.compileClasspath
  runtimeClasspath += p.sourceSets.main.output + p.sourceSets.main.runtimeClasspath
 } }
 p.configurations.conversionImplementation.extendsFrom(p.configurations.implementation)
 p.configurations.conversionRuntimeOnly.extendsFrom(p.configurations.runtimeOnly)
 p.loom.mods { dreamwalker_full_conversion_author { sourceSet p.sourceSets.conversion } }
 p.loom.runs { conversion {
  inherit server;name 'Explicit bounded source conversion';source p.sourceSets.conversion
  runDir 'RUNTIME';vmArg '-Xmx4G';vmArg '-Ddw.conversion.productionJar=PRODUCTION'
 } }
 def author=p.tasks.register('v127ConversionJar',Jar) {
  dependsOn p.tasks.named('conversionClasses');from p.sourceSets.conversion.output
  archiveBaseName='dreamwalker-v127-full-conversion-author';archiveVersion='1.2.7';archiveClassifier='dev'
 }
 def remapType=p.tasks.named('remapJar').get().getClass().superclass
 p.tasks.register('remapV127ConversionJar',remapType) {
  dependsOn author;inputFile.set(author.flatMap{it.archiveFile})
  classpath.from(p.sourceSets.main.output,p.sourceSets.main.compileClasspath)
  archiveBaseName='dreamwalker-v127-full-conversion-author';archiveVersion='1.2.7';archiveClassifier=''
  addNestedDependencies=false
 }
} 
""".replace('RESOURCES', literal(resources)).replace('RUNTIME', literal(runtime)).replace('PRODUCTION', literal(production)), encoding='utf8')
    report = {'status': 'PASS_PREPARED_NOT_COMPILED', 'productionJarSha256': digest,
              'initScript': str(init), 'runtimeDirectory': str(runtime), 'worldConversionAttempted': False}
    if not args.prepare_only:
        java_home = os.environ.get('JAVA_HOME17') or os.environ.get('JAVA_HOME')
        assert java_home, 'Set JAVA_HOME17 or JAVA_HOME to Java 17; this helper never guesses a device path'
        wrapper = ROOT / ('gradlew.bat' if os.name == 'nt' else 'gradlew')
        command = [str(wrapper), '--no-daemon', '-I', str(init), 'remapV127ConversionJar']
        if args.worldedit_api_jar:
            assert ordinary(args.worldedit_api_jar) and args.worldedit_api_jar.is_file()
            command.insert(1, '-Pworldedit_api_jar=' + str(args.worldedit_api_jar.absolute()))
        if args.offline:
            command.insert(1, '--offline')
        with (output / 'build.log').open('wb') as log:
            code = subprocess.run(command, cwd=ROOT, stdout=log, stderr=subprocess.STDOUT,
                                  env=dict(os.environ, JAVA_HOME=java_home)).returncode
        addon = ROOT / 'build/libs/dreamwalker-v127-full-conversion-author-1.2.7.jar'
        assert code == 0 and addon.is_file() and sha(production) == digest, 'Author build failed or production changed'
        with zipfile.ZipFile(addon) as archive:
            assert archive.testzip() is None and json.loads(archive.read('fabric.mod.json')) == metadata
        report.update(status='PASS_AUTHOR_BUILD', addon=str(addon), addonSha256=sha(addon), productionJarUnchanged=True)
    (output / 'build-report.json').write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf8')
    print(json.dumps(report))


if __name__ == '__main__':
    main()
