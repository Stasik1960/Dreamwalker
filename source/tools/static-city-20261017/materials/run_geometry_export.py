"""Export immutable native definitions, exiting from mod init before any world opens."""
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import sys
import zipfile


def load(path):
    return json.loads(path.read_text(encoding="utf-8-sig"))


def sha(path):
    with path.open("rb") as handle:
        return hashlib.file_digest(handle, "sha256").hexdigest()


def main():
    root = Path(sys.argv[1]).resolve()
    config = load(root / ".handoff-local/local-paths.json")
    receipt = load(root / ".handoff-transfer/RESTORE-RECEIPT.json")
    if receipt["status"] != "VERIFIED":
        raise RuntimeError("Verified restoration required")
    base = root / ".handoff-local/work/catalog-expansion"
    runtime = base / "native-runtime"
    if runtime.exists():
        raise RuntimeError("Exporter runtime must be new")
    addon = root / ".handoff-local/work/source/build/libs/dreamwalker-geometryExport-1.0.0-portable.jar"
    class_name = "dev/dreamwalker/bloodbornedw/composite/StaticArchitectureCatalogExpansionExport.class"
    with zipfile.ZipFile(addon) as compiled:
        class_bytes = compiled.read(class_name)
    bootstrap = root / "project/build/v1274-converter-inputs-r1/bootstrap"
    bootstrap_manifest = load(bootstrap / "bootstrap.json")
    runtime.mkdir()
    for row in bootstrap_manifest["runtimeFiles"]:
        source, target = bootstrap / row["sourcePath"], runtime / row["runtimePath"]
        if ".." in Path(row["runtimePath"]).parts or not target.is_relative_to(runtime):
            raise RuntimeError("Bootstrap path escaped runtime")
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(source, target)
    launcher = runtime / "fabric-server-launch.jar"
    if not launcher.exists():
        shutil.copyfile(bootstrap / "fabric-server-launch.jar", launcher)
    mods = runtime / "mods"
    mods.mkdir()
    original = Path(config["paths"]["game4Jar"])
    export_game = mods / "dreamwalker-v4-geometry-export-only.jar"
    with zipfile.ZipFile(original) as source, zipfile.ZipFile(export_game, "x", compression=zipfile.ZIP_DEFLATED) as target:
        metadata = json.loads(source.read("fabric.mod.json"))
        metadata["entrypoints"]["main"].append("dev.dreamwalker.bloodbornedw.composite.StaticArchitectureCatalogExpansionExport")
        for item in source.infolist():
            if item.filename != "fabric.mod.json":
                target.writestr(item, source.read(item.filename))
        target.writestr("fabric.mod.json", json.dumps(metadata, indent=2) + "\n")
        target.writestr(class_name, class_bytes)
    for name in ("fabric-api-0.92.9+1.20.1.jar", "geckolib-fabric-1.20.1-4.4.9.jar"):
        shutil.copyfile(root / "project/build/v1274-converter-inputs-r1/dependencies" / name, mods / name)
    output = base / "native-geometry.json"
    command = [str(Path(config["javaHome17"]) / "bin/java.exe"), "-Xmx2G", "-XX:ActiveProcessorCount=2",
               "-Ddw.staticGeometry.export=true", "-Ddw.staticGeometry.input=" + str(base / "request.json"),
               "-Ddw.staticGeometry.output=" + str(output), "-jar", str(launcher), "nogui"]
    with (base / "native-export.log").open("xb") as log:
        result = subprocess.run(command, cwd=runtime, stdout=log, stderr=subprocess.STDOUT, timeout=600)
    report = load(output) if output.exists() else {}
    outcome = {"exitCode": result.returncode, "nativeReportStatus": report.get("status"),
        "sourceRestoreReceiptReused": True, "sourceGameSha256": "1805b3e922ad246b4f5222aaf83ae0527c458d1624cfaa12e02b139d00faa4e1",
        "derivedExportOnlyJarSha256": sha(export_game), "exporterClassSha256": hashlib.sha256(class_bytes).hexdigest(),
        "changesToGameForExportOnly": ["One task-only initializer class", "Appended initializer after both original main entrypoints"],
        "worldDirectoryCreated": (runtime / "world").exists(), "command": command}
    (base / "EXPORT-RECEIPT.json").write_text(json.dumps(outcome, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    if result.returncode or report.get("status") != "PASS_NATIVE_PREPARED_FINITE_GEOMETRY_NOT_WORLD_ANALYSIS" or outcome["worldDirectoryCreated"]:
        raise RuntimeError("Native definition export failed; see native-export.log and EXPORT-RECEIPT.json")
    print(json.dumps({"status": report["status"], "poseCount": report["poseCount"], "excluded": report["excludedCount"], "worldCreated": False}))


if __name__ == "__main__":
    main()
