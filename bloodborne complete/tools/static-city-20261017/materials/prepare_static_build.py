"""Prepare an isolated build using immutable restored resources as read inputs."""
import hashlib
import json
from pathlib import Path
import shutil
import sys


def dump(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def main():
    root = Path(sys.argv[1]).resolve()
    receipt = json.loads((root / ".handoff-transfer/RESTORE-RECEIPT.json").read_text(encoding="utf-8-sig"))
    if receipt["status"] != "VERIFIED":
        raise RuntimeError("Verified restoration is required")
    original = root / "project"
    work = root / ".handoff-local/work/source"
    if work.exists():
        raise RuntimeError("Build project must be new")
    work.mkdir(parents=True)
    for name in ("build.gradle", "settings.gradle", "gradle.properties", "gradlew", "gradlew.bat"):
        shutil.copyfile(original / name, work / name)
    shutil.copytree(original / "gradle", work / "gradle")
    for name in ("architecture/java", "rp/java", "rp/resources", "test/java"):
        if (original / "src" / name).exists():
            shutil.copytree(original / "src" / name, work / "src" / name)
    overlay = work / "resource-overlay"
    overlay.mkdir()
    text = (work / "build.gradle").read_text(encoding="utf-8")
    text = text.replace("resources.srcDirs = ['src/architecture/resources']",
        "resources.srcDirs = ['resource-overlay', '" + (original / "src/architecture/resources").as_posix() + "']")
    text += "\ntasks.withType(AbstractCopyTask).configureEach { duplicatesStrategy = DuplicatesStrategy.EXCLUDE }\n"
    (work / "build.gradle").write_text(text, encoding="utf-8")
    author = work / "static-author-src"
    shutil.copytree(original / "build/v1274-city-completion-20261015/static-author-source-full-neighbor-pending-v3", author)
    exporter = root / ".handoff-local/work/catalog-expansion/export-src"
    game_sha = "1805b3e922ad246b4f5222aaf83ae0527c458d1624cfaa12e02b139d00faa4e1"
    for kind, entry, addon_id in (("geometryExport", "dev.dreamwalker.bloodbornedw.composite.StaticArchitectureCatalogExpansionExport", "dreamwalker_static_geometry_export"),
                                  ("staticAuthor", "dev.dreamwalker.bloodbornedw.conversion.FullMapConversionAuthor", "dreamwalker_full_conversion_author")):
        resources = work / (kind + "-resources")
        dump(resources / "fabric.mod.json", {"schemaVersion": 1, "id": addon_id, "version": "1.0.0-portable",
            "name": "Dreamwalker portable " + kind, "environment": "server", "entrypoints": {"main": [entry]},
            "depends": {"minecraft": "1.20.1", "java": ">=17", "fabricloader": ">=0.16.10", "fabric-api": "*", "bloodborne_dw": "*"},
            **({"mixins": ["dreamwalker_conversion.mixins.json"]} if kind == "staticAuthor" else {})})
        if kind == "staticAuthor":
            shutil.copyfile(original / "src/conversion/resources/dreamwalker_conversion.mixins.json", resources / "dreamwalker_conversion.mixins.json")
            dump(resources / "dw-conversion-binding.json", {"schemaVersion": 1, "productionJarSha256": game_sha, "disabledWithoutExplicitMarker": True})
    def literal(path):
        return path.as_posix().replace("'", "\\'")
    init = """gradle.beforeProject { p -> p.plugins.withId('fabric-loom') {
 p.sourceSets {
  geometryExport { java.srcDirs=['EXPORT'];resources.srcDirs=['geometryExport-resources'];compileClasspath += p.sourceSets.main.output+p.sourceSets.main.compileClasspath }
  staticAuthor { java.srcDirs=['static-author-src'];resources.srcDirs=['staticAuthor-resources'];compileClasspath += p.sourceSets.main.output+p.sourceSets.main.compileClasspath }
 }
 p.loom.mixin.useLegacyMixinAp.set(false)
} }
gradle.projectsEvaluated {
 def p=gradle.rootProject
 ['geometryExport','staticAuthor'].each { kind ->
  p.configurations.getByName(kind+'Implementation').extendsFrom(p.configurations.implementation)
  def src=p.sourceSets.getByName(kind)
  def pack=p.tasks.register(kind+'Jar',Jar) { dependsOn p.tasks.named(kind+'Classes');from src.output;archiveBaseName='dreamwalker-'+kind;archiveVersion='1.0.0-portable';archiveClassifier='dev' }
  def remapType=p.tasks.named('remapJar').get().getClass().superclass
  p.tasks.register('remap'+kind.capitalize()+'Jar',remapType) {
   dependsOn pack;inputFile.set(pack.flatMap{it.archiveFile});classpath.from(p.sourceSets.main.output,p.sourceSets.main.compileClasspath)
   archiveBaseName='dreamwalker-'+kind;archiveVersion='1.0.0-portable';archiveClassifier='';addNestedDependencies=false;useMixinAP.set(false)
  }
 }
}
""".replace("EXPORT", literal(exporter))
    (work / "portable-static.init.gradle").write_text(init, encoding="utf-8")
    dump(work / "SOURCE-PROVENANCE.json", {"sourceCommit": receipt["commit"], "sourceRestoreReceipt": str(root / ".handoff-transfer/RESTORE-RECEIPT.json"),
        "sourceSnapshot": "Current .5 source, retaining OwnerDataV1 fix", "resourceInputs": str(original / "src/architecture/resources"),
        "resourceOverlay": str(overlay), "authorSource": "Preserved full-neighbor-pending-v3 copy; portable changes recorded separately",
        "gameplayVerified": False, "originalSourceModified": False})
    print(str(work))


if __name__ == "__main__":
    main()
