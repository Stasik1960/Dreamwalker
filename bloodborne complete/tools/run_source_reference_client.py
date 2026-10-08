"""Prepare/run actual original Forge 1.18.2 in an isolated offline QA client.

Reads only supplied public game artifacts and asset/library caches. The source
world, original mods, launcher/account files and existing server report are not
modified. Physics is sampled by a QA-only Forge event mod through game APIs.
"""
import argparse
import concurrent.futures
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import platform
import re
import shutil
import subprocess
import time
import urllib.request
import uuid
import zipfile

from source_reference_names import names, client_names

ROOT = Path(__file__).resolve().parents[1]
USER = Path.home()
INSTALL = ROOT / "build/source-client-forge-1.18.2"
VANILLA = ROOT / "inputs/extracted/vanilla-1.18.2"
SOURCE = USER / "Downloads/bbmc_v16_map (1).zip"
PACK = USER / "Downloads/bbmc_v15_resource (1).zip"
MODS = [USER / "Downloads/Bloodborne_X_Minecraft_mod_6.0.jar",
        USER / "Downloads/geckolib-forge-1.18-3.0.57.jar"]


def sha(path, algorithm="sha256"):
    result = hashlib.new(algorithm)
    with Path(path).open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            result.update(chunk)
    return result.hexdigest()


def fetch(url):
    if not url.startswith(("https://libraries.minecraft.net/", "https://piston-data.mojang.com/",
                           "https://piston-meta.mojang.com/", "https://resources.download.minecraft.net/",
                           "https://maven.minecraftforge.net/")):
        raise ValueError("Unexpected artifact host: " + url)
    with urllib.request.urlopen(url, timeout=45) as response:
        return response.read()


def save(report, value):
    report.parent.mkdir(parents=True, exist_ok=True)
    report.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf8")


def applies(rules):
    allowed = not rules
    for rule in rules:
        system = rule.get("os", {})
        if system.get("name", "windows") != "windows":
            continue
        if system.get("arch", "amd64") not in ("amd64", "x86_64"):
            continue
        if "version" in system and not re.search(system["version"], platform.version()):
            continue
        if any({"has_custom_resolution": True, "is_demo_user": False}.get(key, False) != value
               for key, value in rule.get("features", {}).items()):
            continue
        allowed = rule["action"] == "allow"
    return allowed


def obtain(artifact, target, candidates):
    expected = artifact.get("sha1")
    target.parent.mkdir(parents=True, exist_ok=True)
    source = next((p for p in candidates if p.is_file() and (not expected or sha(p, "sha1") == expected)), None)
    if source:
        shutil.copyfile(source, target)
        origin = str(source)
    else:
        target.write_bytes(fetch(artifact["url"]))
        origin = artifact["url"]
    if expected and sha(target, "sha1") != expected:
        raise ValueError("Artifact SHA1 mismatch: " + str(target))
    if "size" in artifact and target.stat().st_size != artifact["size"]:
        raise ValueError("Artifact size mismatch: " + str(target))
    return {"path": str(target), "source": origin, "sha1": sha(target, "sha1"), "sha256": sha(target)}


def arguments(raw, substitutions):
    result = []
    for entry in raw:
        if isinstance(entry, str):
            selected = [entry]
        elif applies(entry.get("rules", [])):
            selected = entry["value"] if isinstance(entry["value"], list) else [entry["value"]]
        else:
            continue
        for value in selected:
            value = re.sub(r"\$\{([^}]+)\}", lambda match: substitutions[match.group(1)], value)
            result.append(value)
    return result


def prepare(args, result):
    run = Path(result["run_directory"])
    run.mkdir(parents=True)
    forge_path = INSTALL / "versions/1.18.2-forge-40.2.0/1.18.2-forge-40.2.0.json"
    forge = json.loads(forge_path.read_text(encoding="utf8"))
    vanilla = json.loads((VANILLA / "version.json").read_text(encoding="utf8"))
    assert forge["inheritsFrom"] == "1.18.2"
    result["metadata"] = {"forge_path": str(forge_path), "forge_sha256": sha(forge_path),
                          "vanilla_path": str(VANILLA / "version.json"), "vanilla_sha256": sha(VANILLA / "version.json")}
    libs = run / "libraries"
    natives = run / "natives"
    natives.mkdir()
    records, classpath, seen = [], [], set()
    for library in vanilla["libraries"] + forge["libraries"]:
        if not applies(library.get("rules", [])):
            continue
        for kind in ("artifact", "native"):
            if kind == "artifact":
                artifact = library.get("downloads", {}).get("artifact")
            else:
                native = library.get("natives", {}).get("windows", "").replace("${arch}", "64")
                artifact = library.get("downloads", {}).get("classifiers", {}).get(native)
            if not artifact or artifact["path"] in seen:
                continue
            seen.add(artifact["path"])
            relative = Path(artifact["path"])
            target = libs / relative
            record = obtain(artifact, target, [INSTALL / "libraries" / relative,
                                             USER / "AppData/Roaming/.minecraft/libraries" / relative,
                                             USER / "Limacina/project/dw/libraries" / relative])
            record.update(coordinate=library["name"], kind=kind)
            records.append(record)
            if kind == "artifact":
                classpath.append(str(target))
            else:
                with zipfile.ZipFile(target) as archive:
                    for entry in archive.namelist():
                        if entry.lower().endswith(".dll"):
                            (natives / Path(entry).name).write_bytes(archive.read(entry))
    # Forge installer products are located by FML through libraryDirectory,
    # independently of the public launcher profile's bootstrap classpath.
    products = ["net/minecraft/client/1.18.2-20220404.173914/client-1.18.2-20220404.173914-srg.jar",
                "net/minecraft/client/1.18.2-20220404.173914/client-1.18.2-20220404.173914-extra.jar"]
    products += [f"net/minecraftforge/{artifact}/1.18.2-40.2.0/{artifact}-1.18.2-40.2.0.jar"
                 for artifact in ("fmlcore", "javafmllanguage", "lowcodelanguage", "mclanguage")]
    products += [f"net/minecraftforge/forge/1.18.2-40.2.0/forge-1.18.2-40.2.0-{kind}.jar"
                 for kind in ("client", "universal")]
    for relative in products:
        source, target = INSTALL / "libraries" / relative, libs / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(source, target)
        records.append({"kind": "verified_installer_product", "path": str(target), "source": str(source),
                        "sha256": sha(target), "source_sha256": sha(source)})
    # The inherited vanilla artifact is exact; its filename agrees with the
    # stock Forge ignoreList so FML loads patched SRG classes from its products.
    client = run / "versions/1.18.2-forge-40.2.0/1.18.2-forge-40.2.0.jar"
    client_record = obtain(vanilla["downloads"]["client"], client, [VANILLA / "client.jar"])
    classpath.append(str(client))
    records.append(dict(client_record, kind="inherited_vanilla_client"))
    result["libraries"] = records
    assets = run / "assets"
    index_id = vanilla["assetIndex"]["id"]
    index = assets / "indexes" / (index_id + ".json")
    result["asset_index"] = obtain(vanilla["assetIndex"], index,
        [USER / "AppData/Roaming/.minecraft/assets/indexes" / (index_id + ".json")])
    objects = json.loads(index.read_text(encoding="utf8"))["objects"]
    hashes = {entry["hash"]: entry["size"] for entry in objects.values()}
    def asset(pair):
        digest, size = pair
        relative = Path("objects") / digest[:2] / digest
        record = obtain({"sha1": digest, "size": size,
                         "url": "https://resources.download.minecraft.net/" + digest[:2] + "/" + digest},
                        assets / relative, [USER / "AppData/Roaming/.minecraft/assets" / relative,
                                            USER / "Limacina/project/dw/assets" / relative])
        return record["source"].startswith("https://")
    with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
        downloads = sum(pool.map(asset, hashes.items()))
    result["assets"] = {"directory": str(assets), "objects_sha1_verified": len(hashes), "downloaded": downloads,
                        "copied_read_only_caches": len(hashes) - downloads,
                        "total_bytes": sum(hashes.values()), "source_index_sha1": sha(index, "sha1")}
    print(json.dumps({"phase": "libraries_and_assets_prepared", "libraries": len(records), "assets": len(hashes)}), flush=True)
    world = run / "saves/source-reference-world"
    entries = []
    with zipfile.ZipFile(SOURCE) as archive:
        for item in archive.infolist():
            if item.is_dir():
                continue
            name = PurePosixPath(item.filename)
            if name.is_absolute() or ".." in name.parts or ":" in item.filename:
                raise ValueError("Unsafe source ZIP entry")
            target = world.joinpath(*name.parts)
            target.parent.mkdir(parents=True, exist_ok=True)
            data = archive.read(item)
            target.write_bytes(data)
            digest = hashlib.sha256(data).hexdigest()
            if sha(target) != digest:
                raise ValueError("Source copy changed: " + item.filename)
            entries.append({"path": item.filename, "bytes": len(data), "sha256": digest})
    result["source_world"] = {"archive": str(SOURCE), "sha256": sha(SOURCE), "copy": str(world),
                              "files": entries, "before_launch_all_bytes_equal": True}
    mods = run / "mods"
    mods.mkdir()
    result["original_mods"] = []
    for original in MODS:
        target = mods / original.name
        shutil.copyfile(original, target)
        result["original_mods"].append({"source": str(original), "copy": str(target), "sha256": sha(target),
                                        "source_sha256": sha(original), "byte_identical": sha(target) == sha(original)})
    resource = run / "resourcepacks/source-bbmc-v15.zip"
    resource.parent.mkdir()
    shutil.copyfile(PACK, resource)
    result["source_pack"] = {"source": str(PACK), "copy": str(resource), "sha256": sha(resource),
                             "source_sha256": sha(PACK), "byte_identical": sha(resource) == sha(PACK),
                             "original_pack_format": 9, "target_vanilla_pack_format": 8,
                             "explicit_incompatible_pack_selection": True}
    (run / "options.txt").write_text('fullscreen:false\nrenderDistance:5\nsimulationDistance:5\nautoJump:false\n'
                                   'resourcePacks:["file/source-bbmc-v15.zip"]\n'
                                   'incompatibleResourcePacks:["file/source-bbmc-v15.zip"]\n', encoding="utf8")
    input_path = args.probe_input.resolve() if args.probe_input else ROOT / "build/source-reference-forge-1.18.2/probe-input.json"
    probe_input = json.loads(input_path.read_text(encoding="utf8"))
    probe_input["methods"] = names()
    probe_input["clientMethods"] = client_names(INSTALL)
    # The original window carrier straddles a chunk boundary. Record the adjacent
    # brick and the two source-proven custom AirBlock light cells explicitly.
    additional_cells = [
        {"purpose": "source_window_front_bricks", "pos": [-32, 37, -1121]},
        {"purpose": "source_technical_lamp_light_1", "pos": [228, 69, -932]},
        {"purpose": "source_technical_lamp_light_2", "pos": [229, 69, -931]},
    ]
    for sample in additional_cells:
        if not any(existing["pos"] == sample["pos"] for existing in probe_input["cells"]):
            probe_input["cells"].append(sample)
    save(run / "probe-input.json", probe_input)
    classes = run / "probe-classes"
    classes.mkdir()
    java_home = args.java_home.resolve()
    compile_classpath = os.pathsep.join(str(path) for path in sorted(set(libs.rglob("*.jar")) |
                                                                  set((INSTALL / "libraries").rglob("*.jar"))))
    java_source = args.probe_java.resolve() if args.probe_java else ROOT / "tools/java/SourcePhysicsProbe.java"
    compile_result = subprocess.run([str(java_home / "bin/javac.exe"), "--release", "17", "-proc:none", "-cp",
                                     compile_classpath, "-d", str(classes), str(java_source)],
                                    capture_output=True, text=True, timeout=120)
    (run / "probe-compile.log").write_text(compile_result.stdout + compile_result.stderr, encoding="utf8")
    if compile_result.returncode:
        raise RuntimeError("QA compile failed; inspect isolated probe-compile.log")
    qa = mods / "dreamwalker-source-client-physics-qa.jar"
    with zipfile.ZipFile(qa, "w", compression=zipfile.ZIP_DEFLATED) as archive:
        archive.writestr("pack.mcmeta", '{"pack":{"pack_format":8,"description":"Isolated source physics QA resources"}}')
        archive.writestr("META-INF/mods.toml", 'modLoader="javafml"\nloaderVersion="[40,)"\nlicense="All-Rights-Reserved"\n'
                         '[[mods]]\nmodId="dreamwalker_source_probe"\nversion="1.0.0"\n'
                         'displayName="Isolated source client physics QA"\n')
        for path in classes.rglob("*.class"):
            archive.write(path, path.relative_to(classes).as_posix())
    result["qa_probe"] = {"java_source": str(java_source), "java_source_sha256": sha(java_source),
                          "jar": str(qa), "sha256": sha(qa), "compile_exit_code": compile_result.returncode,
                          "client_methods": probe_input["clientMethods"], "input_sha256": sha(run / "probe-input.json")}
    user = "SourcePhysicsQA"
    offline_uuid = str(uuid.UUID(bytes=hashlib.md5(("OfflinePlayer:" + user).encode()).digest(), version=3))
    substitutions = {"auth_player_name": user, "version_name": forge["id"], "game_directory": str(run),
                     "assets_root": str(assets), "assets_index_name": index_id, "auth_uuid": offline_uuid,
                     "auth_access_token": "0", "clientid": "", "auth_xuid": "", "user_type": "legacy",
                     "version_type": "release", "resolution_width": "1280", "resolution_height": "720",
                     "natives_directory": str(natives), "launcher_name": "Dreamwalker-isolated-source-QA",
                     "launcher_version": "1", "classpath": os.pathsep.join(classpath),
                     "library_directory": str(libs), "classpath_separator": os.pathsep}
    logging_file = vanilla["logging"]["client"]["file"]
    logging_path = run / "logging" / logging_file["id"]
    result["logging_config"] = obtain(logging_file, logging_path,
        [USER / "AppData/Roaming/.minecraft/assets/log_configs" / logging_file["id"]])
    jvm = arguments(vanilla["arguments"]["jvm"] + forge["arguments"]["jvm"], substitutions)
    game = arguments(vanilla["arguments"]["game"] + forge["arguments"]["game"], substitutions)
    command = [str(java_home / "bin/java.exe"), "-Xmx3G", "-Ddreamwalker.source.autoOpen=true",
               "-Ddreamwalker.source.autoStop=true",
               "-Ddreamwalker.source.mode=integrated-client", "-Dlog4j.configurationFile=" + str(logging_path)]
    command += jvm + [forge["mainClass"]] + game
    save(run / "launch-command.json", command)
    result.update(status="PREPARED", command=command, offline_username=user, auth="offline QA literal token0",
                  minecraft="1.18.2", forge="40.2.0", original_mod="Bloodborne X Minecraft 6.0")
    return command


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--run-name", default="source-physics-" + str(int(time.time())))
    parser.add_argument("--java-home", type=Path, default=USER / "AppData/Roaming/.minecraft/runtime/java-runtime-gamma/windows/java-runtime-gamma")
    parser.add_argument("--report", type=Path, default=ROOT / "reports/SOURCE_REFERENCE_CLIENT.json")
    parser.add_argument("--physics-report", type=Path,
                        default=ROOT / "reports/SOURCE_PHYSICS_CLIENT_ACTUAL.json")
    parser.add_argument("--probe-input", type=Path)
    parser.add_argument("--probe-java", type=Path)
    parser.add_argument("--launch", action="store_true")
    parser.add_argument("--startup-timeout", type=int, default=300)
    args = parser.parse_args()
    if not re.fullmatch(r"[a-zA-Z0-9_-]+", args.run_name):
        raise ValueError("Unsafe run name")
    run = ROOT / "build" / ("source-reference-client-" + args.run_name)
    if run.exists():
        raise ValueError("Refusing to overwrite an existing source client test")
    report = args.report.resolve()
    previous = ROOT / "reports/SOURCE_REFERENCE.json"
    result = {"schema": "dreamwalker-original-forge-source-client-v1", "run_directory": str(run),
              "manual_gameplay": "NOT_RUN", "client_visuals": "NOT_RUN",
              "previous_dedicated_evidence": str(previous), "previous_dedicated_evidence_sha256": sha(previous),
              "original_inputs_modified": False}
    process = None
    try:
        command = prepare(args, result)
        save(report, result)
        if args.launch:
            log = run / "launch-console.log"
            with log.open("w", encoding="utf8") as stream:
                process = subprocess.Popen(command, cwd=run, stdout=stream, stderr=subprocess.STDOUT,
                    creationflags=subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0)
            result.update(status="LAUNCHED_AWAITING_PHYSICS", pid=process.pid)
            save(report, result)
            print(json.dumps({"phase": "source_client_launched", "pid": process.pid, "run_directory": str(run)}), flush=True)
            started = time.monotonic()
            output = run / "source-physics-output.json"
            while time.monotonic() - started < args.startup_timeout and process.poll() is None and not output.is_file():
                time.sleep(1)
            if output.is_file():
                measured = json.loads(output.read_text(encoding="utf8"))
                result["physics"] = measured
                result["physics_output_sha256"] = sha(output)
                save(args.physics_report.resolve(), measured)
                result["physics_report"] = str(args.physics_report.resolve())
                result["status"] = "MEASURED_ACTUAL_SOURCE_INTEGRATED_CLIENT" if measured["status"] == "MEASURED_ACTUAL_SOURCE_SERVER" else "FAIL_SOURCE_CLIENT_PROBE"
                # Give the QA's ordinary Minecraft.stop API time to flush the
                # copied world and exit. Forced-stop fallback stays explicit.
                try:
                    process.wait(timeout=40)
                except subprocess.TimeoutExpired:
                    pass
            else:
                result["status"] = "FAIL_SOURCE_CLIENT_START_OR_WORLD_OPEN"
            if process.poll() is None:
                # Only the exact Popen-owned new QA client is terminated. This
                # is a forced stop and is never reported as a saved shutdown.
                process.terminate()
                try:
                    process.wait(timeout=20)
                except subprocess.TimeoutExpired:
                    process.kill()
                    process.wait(timeout=10)
                result["shutdown"] = "FORCED_OWNED_QA_PID_AFTER_MEASUREMENT_OR_TIMEOUT"
            else:
                result["shutdown"] = "CLIENT_EXITED_ITSELF"
            result.update(exit_code=process.returncode, elapsed_seconds=round(time.monotonic() - started, 3),
                          launch_console=str(log), launch_console_sha256=sha(log))
            console = log.read_text(encoding="utf8", errors="replace")
            reloads=[line.strip() for line in console.splitlines() if "Reloading ResourceManager" in line]
            result["source_pack"]["resource_manager_reload_messages"]=reloads
            result["source_pack"]["resource_manager_loaded_original_pack"]=any("source-bbmc-v15.zip" in line for line in reloads)
            if process.returncode == 0 and "Dreamwalker source QA requested standard Minecraft.stop" in console:
                result["shutdown"] = "STANDARD_MINECRAFT_STOP_EXIT0"
            result["error_lines"] = sum("ERROR" in line for line in console.splitlines())
            result["standard_loadLevel_marker"] = "Dreamwalker source QA invoked standard loadLevel" in console
            result["originals_after_run"] = {"world_archive_sha256": sha(SOURCE), "pack_sha256": sha(PACK),
                "mods": [{"path": str(path), "sha256": sha(path)} for path in MODS],
                "dedicated_report_sha256": sha(previous)}
    except Exception as error:
        result.update(status="FAIL_SOURCE_CLIENT_PREPARATION", error=repr(error))
        if process is not None and process.poll() is None:
            process.terminate()
            process.wait(timeout=20)
            result["shutdown"] = "FORCED_OWNED_QA_PID_AFTER_FAILURE"
    save(report, result)
    print(json.dumps({"status": result["status"], "report": str(report), "run_directory": str(run),
                      "pid": result.get("pid"), "physics_status": result.get("physics", {}).get("status")}), flush=True)
    return 0 if result["status"] in ("PREPARED", "MEASURED_ACTUAL_SOURCE_INTEGRATED_CLIENT") else 1


if __name__ == "__main__":
    raise SystemExit(main())
