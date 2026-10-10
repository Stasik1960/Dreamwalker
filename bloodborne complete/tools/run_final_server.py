"""Prepare and smoke-test a final remapped JAR in an isolated Fabric server.

Only build/runtime-server-* is written. No original world is loaded, no user
installation/configuration is changed, and the bind address is 127.0.0.1.
Legal acceptance is copied only from an explicitly supplied eula=true file.
Official Loader metadata supplies exact launcher libraries and checksums.
"""
from __future__ import annotations
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import socket
import subprocess
import time
import urllib.request
import uuid
import zipfile

ROOT = Path(__file__).resolve().parents[1]
USER = Path.home()


def digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def fetch(url: str) -> bytes:
    with urllib.request.urlopen(url, timeout=30) as response:
        return response.read()


def manifest_lines(text: str) -> bytes:
    result = []
    for line in text.splitlines():
        while len(line.encode("utf8")) > 70:
            result.append(line[:70])
            line = " " + line[70:]
        result.append(line)
    return ("\r\n".join(result) + "\r\n\r\n").encode("utf8")


def console_commands(path: Path) -> list[str]:
    if path.stat().st_size > 65536:
        raise ValueError("Console command fixture exceeds64KiB")
    commands = json.loads(path.read_text(encoding="utf8"))
    if not isinstance(commands, list) or len(commands) > 128 or any(
            not isinstance(command, str) or not command or len(command) > 2048
            or any(char in command for char in "\r\n\0") for command in commands):
        raise ValueError("Use at most128 explicit single-line console commands")
    return commands


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--jar", required=True, type=Path)
    parser.add_argument("--java-home", type=Path, default=USER / "AppData/Roaming/.minecraft/runtime/java-runtime-gamma/windows/java-runtime-gamma")
    parser.add_argument("--modset", type=Path, default=Path("C:/Users/vakir/Limacina/project/dw/mods.zip"))
    parser.add_argument("--profile", choices=("minimal", "full_server"), default="minimal")
    parser.add_argument("--cached-minimal", action="store_true", help="Use exact project dependency versions already cached locally; no user's modset")
    parser.add_argument("--gallery-input", type=Path, help="Explicit isolated full-catalogue gallery authoring marker")
    parser.add_argument("--baseline-input", type=Path, help="Explicit original-v10 internal complaint probe marker")
    parser.add_argument("--profile-manifest", type=Path, default=ROOT / "reports/MODSET_PROFILE.json")
    parser.add_argument("--loader", default="0.19.5")
    parser.add_argument("--accepted-eula-file", type=Path)
    parser.add_argument("--run-name")
    parser.add_argument("--prepare-only", action="store_true")
    parser.add_argument("--startup-timeout", type=int, default=180)
    parser.add_argument("--shutdown-timeout", type=int, default=40, help="Bounded time for queued QA authoring/save/shutdown after startup")
    parser.add_argument("--commands-file", type=Path)
    parser.add_argument("--after-hold-commands-file", type=Path, help="Explicit ordinary console commands after bounded hold, e.g. export after real60-second recording")
    parser.add_argument("--hold-seconds", type=int, default=0, help="Bounded ordinary client-test window; qa-normal-stop.request ends it early")
    parser.add_argument("--test-operator", action="store_true", help="OP4 only for the exact offline DreamwalkerQA UUID in this new localhost test server")
    parser.add_argument("--extra-mod", type=Path, action="append", default=[])
    parser.add_argument("--scene-input", type=Path, help="Optional QA-only fresh review scene add-on input copied into this isolated run")
    parser.add_argument("--scene-timeout", type=int, default=120, help="Bounded real ticking wait for delayed fresh scene authoring before normal save/stop")
    parser.add_argument("--source-input", type=Path, help="Optional QA-only bounded source-copy migration; requires --world-copy and its verified pre-load marker")
    parser.add_argument("--gameplay-input", type=Path, help="Optional V9 QA-only ordinary link/lamp stand author or persisted-restart marker")
    parser.add_argument("--gameplay-timeout", type=int, default=90, help="Bounded real server ticking time after startup to await the explicit V9 gameplay output")
    parser.add_argument("--diagnostics-input", type=Path, help="Optional QA-only default60 diagnostics/comparison/reenter marker on an existing derived scene")
    parser.add_argument("--diagnostics-timeout", type=int, default=100, help="Bounded real server ticking time to await diagnostics export and save proof")
    parser.add_argument("--alias-input", type=Path, help="Separate QA-only old-alias AUTHOR or actual saved-disk REENTER marker")
    parser.add_argument("--alias-timeout", type=int, default=60, help="Bounded real ticking time to await the separate compatibility fixture proof")
    parser.add_argument("--world-copy", type=Path, help="Reopen a saved derived review world under this project's build folder in a fresh isolated profile")
    parser.add_argument("--report", type=Path)
    parser.add_argument("--expect-rejection", help="Literal diagnostic expected in an intentionally incompatible profile")
    args = parser.parse_args()
    if args.after_hold_commands_file and (not 1 <= args.hold_seconds <= 3600 or any((args.gallery_input,args.baseline_input,args.scene_input,args.source_input,args.gameplay_input,args.diagnostics_input,args.alias_input))):
        raise ValueError("After-hold commands require one separate bounded ordinary server run")
    after_hold_commands = console_commands(args.after_hold_commands_file) if args.after_hold_commands_file else []
    if (args.scene_input or args.source_input or args.gameplay_input or args.diagnostics_input or args.alias_input) and args.report is None:
        raise ValueError("QA scene authoring requires an explicit separate --report path")
    if args.gameplay_input and args.source_input:
        raise ValueError("Ordinary V9 stand and bounded source migration use separate derived runs")
    if args.source_input and (not args.world_copy or args.scene_input):
        raise ValueError("Bounded source authoring requires its separate saved derived world copy")
    if args.diagnostics_input and (not args.world_copy or args.scene_input or args.source_input or args.gameplay_input):
        raise ValueError("Diagnostics QA requires its separate existing derived world copy; no simultaneous authors")
    if args.alias_input and (args.scene_input or args.source_input or args.gameplay_input or args.diagnostics_input or not args.extra_mod or not 1 <= args.alias_timeout <= 120):
        raise ValueError("Alias disk QA uses one separate add-on/marker and a bounded1..120second wait")
    jar = args.jar.resolve()
    java = args.java_home / "bin" / ("java.exe" if os.name == "nt" else "java")
    java_result = subprocess.run([str(java), "-version"], capture_output=True, text=True, check=True)
    java_version = java_result.stderr.strip()
    if not re.search(r'version "17\.', java_version):
        raise ValueError("This checkpoint requires an actual Java 17 runtime")
    artifact_sha = digest(jar)
    alias_document = None
    alias_input_record = None
    if args.alias_input:
        alias_input = args.alias_input.resolve()
        if alias_input.stat().st_size > 16384:
            raise ValueError("Alias marker exceeds16KiB")
        alias_document = json.loads(alias_input.read_text(encoding="utf8"))
        if (alias_document.get("schema") != "dw-v10-alias-disk-input-v1" or alias_document.get("revision") != "V10"
                or alias_document.get("guard") != "QA_ONLY_FRESH_ALIAS_AUTHOR_OR_DISTINCT_SAVED_REENTER"
                or alias_document.get("productionJarSha256") != artifact_sha or alias_document.get("mode") not in {"AUTHOR", "REENTER"}):
            raise ValueError("Alias marker schema/guard/mode/current production SHA differs")
        if (alias_document["mode"] == "REENTER") != bool(args.world_copy):
            raise ValueError("AUTHOR requires fresh flat world and REENTER requires its exact saved --world-copy")
    run_name = args.run_name or (args.profile + "-" + artifact_sha[:12] + "-" + str(int(time.time())))
    if not re.fullmatch(r"[a-zA-Z0-9_-]+", run_name):
        raise ValueError("Unsafe run name")
    run_dir = ROOT / "build" / ("runtime-server-" + run_name)
    if run_dir.exists():
        raise ValueError("Refusing to overwrite a previous test run: " + str(run_dir))
    run_dir.mkdir(parents=True)
    mods_dir = run_dir / "mods"
    mods_dir.mkdir()
    if alias_document:
        shutil.copyfile(alias_input, run_dir / "review-v10-alias-input.json")
        alias_input_record = {"source": str(alias_input), "sha256": digest(alias_input), "mode": alias_document["mode"]}
    world_copy_record = None
    if args.world_copy:
        source_world = args.world_copy.resolve()
        if not source_world.is_relative_to((ROOT / "build").resolve()) or not (source_world / "level.dat").is_file():
            raise ValueError("World copy must be a saved derived review world under this project's build folder")
        if args.scene_input:
            raise ValueError("Fresh scene authoring and saved-world reopen are separate runs")
        copied_files = []
        for path in sorted(source_world.rglob("*")):
            if not path.is_file() or path.name == "session.lock":
                continue
            relative = path.relative_to(source_world)
            destination = run_dir / "isolated-smoke-world" / relative
            destination.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(path, destination)
            source_sha = digest(path)
            if digest(destination) != source_sha:
                raise ValueError("World-copy byte verification failed: " + str(relative))
            copied_files.append({"path": relative.as_posix(), "bytes": path.stat().st_size, "sha256": source_sha})
        world_copy_record = {"source": str(source_world), "files": copied_files, "omitted_files": ["session.lock"], "copy_byte_verification": "PASS"}
    scene_input_record = None
    source_input_record = None
    if args.source_input:
        source_input = args.source_input.resolve()
        document = json.loads(source_input.read_text(encoding="utf8"))
        if document.get("authoringGuard") != "SOURCE_COPY_EXPLICIT_MEMBERS_ONLY" or document.get("expectedObjectCount") != 41 or document.get("sourceMemberCount") != 110:
            raise ValueError("Source migration is confined to the declared41 instances/110 source cells")
        marker_path = source_world / "dreamwalker-source-copy.json"
        marker = json.loads(marker_path.read_text(encoding="utf8"))
        if marker.get("occupiedBlockAuditStatus") != "PASS_ALL_OCCUPIED_BLOCK_PROVIDERS_RESOLVED" or marker.get("requiredTechnicalBlockIds") != ["bloodborne_dw:source_hunter_lamp_light"]:
            raise ValueError("Source migration requires the complete occupied-block/palette provider audit, including exact technical light9 cells")
        with zipfile.ZipFile(jar) as artifact:
            if "dev/dreamwalker/bloodbornedw/architecture/compat/SourceTechnicalLight.class" not in artifact.namelist():
                raise ValueError("Final production JAR lacks the proven source technical-light registry bridge")
        if any(marker.get(key) != document.get(key) for key in ("sourceFixtureSha256", "sourceArchiveSha256")):
            raise ValueError("Source input and verified pre-load marker disagree")
        original_snapshot = Path(marker["originalSnapshot"]).resolve()
        if not original_snapshot.is_relative_to((ROOT / "build").resolve()) or digest(original_snapshot) != document["sourceFixtureSha256"]:
            raise ValueError("Exact bounded-source provenance snapshot must remain under this project's build folder")
        already_saved = (source_world / "data/dreamwalker_review_source_migrations.dat").is_file()
        verified = all(digest(source_world / row["path"]) == row["sha256"] for row in marker["worldFiles"])
        if not verified and not already_saved:
            raise ValueError("Prepared source world changed before its first guarded Minecraft load")
        shutil.copyfile(source_input, run_dir / "source-review-input.json")
        source_input_record = {"source": str(source_input), "sha256": digest(source_input),
                               "source_fixture_sha256": document["sourceFixtureSha256"], "source_archive_sha256": document["sourceArchiveSha256"],
                               "preload_byte_integrity": "PASS" if verified else "SAVED_SOURCE_REVIEW_REQUIRES_RUNTIME_IDEMPOTENCE_VERIFICATION",
                               "original_snapshot": str(original_snapshot), "original_snapshot_sha256": digest(original_snapshot)}
    if args.scene_input:
        scene_input = args.scene_input.resolve()
        document = json.loads(scene_input.read_text(encoding="utf8"))
        if document.get("authoringGuard") != "FRESH_FLAT_ISOLATED_WORLD_ONLY":
            raise ValueError("Scene authoring is limited to an explicit fresh isolated flat fixture")
        shutil.copyfile(scene_input, run_dir / ("review-v10-scene-input.json" if document.get("revision")=="V10" else "review-scene-input.json"))
        scene_input_record = {"source": str(scene_input), "sha256": digest(scene_input), "scene_id": document["sceneId"]}
    gameplay_input_record = None
    diagnostics_input_record = None
    if args.diagnostics_input:
        diagnostics_input = args.diagnostics_input.resolve()
        diagnostics_document = json.loads(diagnostics_input.read_text(encoding="utf8"))
        if diagnostics_document.get("schemaVersion") != 1 or diagnostics_document.get("seconds") != 60 or diagnostics_document.get("mode") not in {"ENABLED", "DISABLED", "REENTER"}:
            raise ValueError("Diagnostics requires explicit schema1/default60/known mode marker")
        shutil.copyfile(diagnostics_input, run_dir / "review-diagnostics-input.json")
        diagnostics_input_record = {"source": str(diagnostics_input), "sha256": digest(diagnostics_input), "mode": diagnostics_document["mode"], "seconds": 60}
    if args.gameplay_input:
        gameplay_input = args.gameplay_input.resolve()
        gameplay_document = json.loads(gameplay_input.read_text(encoding="utf8"))
        if gameplay_document.get("schemaVersion") != 1 or gameplay_document.get("phase") not in {"AUTHOR", "REOPEN"}:
            raise ValueError("V9 gameplay requires explicit schema1 AUTHOR/REOPEN phase")
        if gameplay_document.get("productionGuard") != "QA_ONLY_SEPARATE_FRESH_OR_REOPENED_REVIEW_WORLD":
            raise ValueError("V9 gameplay marker requires the isolated QA-only guard")
        if gameplay_document["phase"] == "REOPEN" and not args.world_copy:
            raise ValueError("Real restart proof requires an exact saved derived --world-copy")
        if gameplay_document["phase"] == "AUTHOR" and args.world_copy:
            raise ValueError("New stand authoring cannot run on an existing saved world")
        if not 1 <= args.gameplay_timeout <= 300:
            raise ValueError("V9 gameplay timeout must stay between1 and300seconds")
        if not args.extra_mod:
            raise ValueError("Gameplay marker needs the separate QA add-on; production has no authoring")
        shutil.copyfile(gameplay_input, run_dir / "review-v9-gameplay-input.json")
        gameplay_input_record = {"source": str(gameplay_input), "sha256": digest(gameplay_input), "phase": gameplay_document["phase"]}
    shutil.copyfile(jar, mods_dir / jar.name)
    if args.gallery_input:
        gallery_input=args.gallery_input.resolve()
        gallery_document=json.loads(gallery_input.read_text(encoding="utf8"))
        if gallery_document.get("guard")!="FRESH_ISOLATED_CATALOGUE_GALLERY_ONLY" or gallery_document.get("productionJarSha256")!=artifact_sha or args.world_copy:
            raise ValueError("Gallery authoring requires a fresh isolated marker matching this exact production JAR")
        shutil.copyfile(gallery_input,run_dir/"catalogue-gallery-input.json")
    if args.baseline_input:
        baseline_input=args.baseline_input.resolve()
        baseline_document=json.loads(baseline_input.read_text(encoding="utf8"))
        if baseline_document.get("productionJarSha256")!=artifact_sha or args.world_copy or not args.extra_mod:
            raise ValueError("Original-v10 probe requires exact JAR, QA-only add-on and a fresh isolated world")
        shutil.copyfile(baseline_input,run_dir/"base-v10-probe-input.json")
    profile = json.loads(args.profile_manifest.read_text(encoding="utf8")) if not args.cached_minimal else None
    selected_paths = profile["profiles"][args.profile] if profile else []
    selected_records = {item["path"]: item for item in profile["selected"]} if profile else {}
    installed_mods = []
    if args.cached_minimal:
        if args.profile!="minimal":raise ValueError("Cached dependencies are only a minimal profile")
        for group,name,version in [("net.fabricmc.fabric-api","fabric-api","0.92.9+1.20.1"),("software.bernie.geckolib","geckolib-fabric-1.20.1","4.4.9"),("com.eliotlash.mclib","mclib","20")]:
            candidates=sorted((USER/".gradle/caches/modules-2/files-2.1"/group/name/version).glob("*/*.jar"))
            source=next((p for p in candidates if not p.name.endswith(("-sources.jar","-javadoc.jar"))),None)
            if source is None:raise ValueError("Missing exact cached runtime dependency: "+name+":"+version)
            shutil.copyfile(source,mods_dir/source.name)
            installed_mods.append({"path":str(source),"sha256":digest(source),"id":name,"version":version})
    else:
        with zipfile.ZipFile(args.modset) as modset:
            for path in selected_paths:
                data = modset.read(path)
                actual = hashlib.sha256(data).hexdigest()
                if selected_records[path]["sha256"] != actual:
                    raise ValueError("Modset profile SHA mismatch: " + path)
                destination = mods_dir / Path(path).name
                destination.write_bytes(data)
                installed_mods.append({"path": path, "sha256": actual, "id": selected_records[path]["id"], "version": selected_records[path]["version"]})
    for extra in args.extra_mod:
        extra = extra.resolve()
        destination = mods_dir / extra.name
        if destination.exists():
            raise ValueError("Extra mod filename collides with selected profile: " + extra.name)
        shutil.copyfile(extra, destination)
        with zipfile.ZipFile(extra) as archive:
            extra_metadata = json.loads(archive.read("fabric.mod.json"))
        installed_mods.append({"path": str(extra), "sha256": digest(extra), "id": extra_metadata["id"],
                               "version": extra_metadata["version"], "extra_mod": True})
    bundled = USER / ".gradle/caches/fabric-loom/1.20.1/minecraft-server.jar"
    shutil.copyfile(bundled, run_dir / "server.jar")
    metadata_url = f"https://meta.fabricmc.net/v2/versions/loader/1.20.1/{args.loader}/server/json"
    metadata_bytes = fetch(metadata_url)
    metadata = json.loads(metadata_bytes)
    (run_dir / "fabric-loader-profile.json").write_bytes(metadata_bytes)
    library_records = []
    for library in metadata["libraries"]:
        group, name, version = library["name"].split(":")
        relative = Path(group.replace(".", "/")) / name / version / (name + "-" + version + ".jar")
        target = run_dir / "libraries" / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        candidates = [USER / "AppData/Roaming/.minecraft/libraries" / relative,
                      USER / "Limacina/project/dw/libraries" / relative]
        if library["name"] == "net.fabricmc:fabric-loader:0.19.5":
            candidates.insert(0, USER / "Limacina/project/dw/0.19.5.jar")
        candidates += list((USER / ".gradle/caches/modules-2/files-2.1" / group / name / version).glob("*/*.jar"))
        expected = library.get("sha256")
        candidate = next((path for path in candidates if path.is_file() and not path.name.endswith(("-sources.jar", "-javadoc.jar")) and (expected is None or digest(path) == expected)), None)
        if candidate:
            shutil.copyfile(candidate, target)
            provenance = str(candidate)
        else:
            url = library.get("url", "https://maven.fabricmc.net/") + relative.as_posix()
            target.write_bytes(fetch(url))
            provenance = url
        actual = digest(target)
        if expected is not None and actual != expected:
            raise ValueError("Official library checksum mismatch: " + library["name"])
        library_records.append({"coordinate": library["name"], "path": "libraries/" + relative.as_posix(), "sha256": actual, "source": provenance})
    classpath = " ".join(item["path"] for item in library_records)
    with zipfile.ZipFile(run_dir / "fabric-server-launch.jar", "w", zipfile.ZIP_DEFLATED) as launcher:
        launcher.writestr("META-INF/MANIFEST.MF", manifest_lines("Manifest-Version: 1.0\nMain-Class: net.fabricmc.loader.impl.launch.server.FabricServerLauncher\nClass-Path: " + classpath))
    (run_dir / "fabric-server-launcher.properties").write_text("serverJar=server.jar\n", encoding="utf8")
    eula_evidence = None
    if args.accepted_eula_file:
        eula = args.accepted_eula_file.read_text(encoding="utf8")
        if not re.search(r"(?m)^\s*eula\s*=\s*true\s*$", eula):
            raise ValueError("Supplied EULA evidence does not contain accepted eula=true")
        shutil.copyfile(args.accepted_eula_file, run_dir / "eula.txt")
        eula_evidence = {"path": str(args.accepted_eula_file), "sha256": digest(args.accepted_eula_file)}
    with socket.socket() as sock:
        sock.bind(("127.0.0.1", 0))
        port = sock.getsockname()[1]
    properties = {"server-ip": "127.0.0.1", "server-port": str(port), "online-mode": "false",
                  "spawn-protection": "0",
                  "level-name": "isolated-smoke-world", "level-type": "minecraft:flat",
                  "generator-settings": json.dumps({"biome": "minecraft:plains", "layers": [
                      {"block": "minecraft:bedrock", "height": 1},
                      {"block": "minecraft:dirt", "height": 126},
                      {"block": "minecraft:grass_block", "height": 1}],
                      "lakes": False, "features": False, "structure_overrides": []}, separators=(",", ":")),
                  "generate-structures": "false", "view-distance": "2", "simulation-distance": "2",
                  "max-players": "2", "enable-query": "false", "enable-rcon": "false"}
    if args.scene_input and document.get("revision")=="V10":properties.update({"gamemode":"creative","force-gamemode":"true","op-permission-level":"4"})
    if alias_document:
        properties.update({"gamemode": "creative", "op-permission-level": "4"})
        if alias_document["mode"] == "AUTHOR":
            properties["generator-settings"] = json.dumps({"biome": "minecraft:plains", "layers": [
                {"block": "minecraft:bedrock", "height": 1}, {"block": "minecraft:dirt", "height": 62},
                {"block": "minecraft:grass_block", "height": 1}], "lakes": False, "features": False,
                "structure_overrides": []}, separators=(",", ":"))
    if args.gallery_input:
        properties.update({"gamemode":"creative","op-permission-level":"4","difficulty":"normal"})
        properties["generator-settings"]=json.dumps({"biome":"minecraft:plains","layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":126},{"block":"minecraft:grass_block","height":1}],"lakes":False,"features":False,"structure_overrides":[]},separators=(",",":"))
    saved_world_gamemode = None
    if args.world_copy:
        from world_io import read_nbt, compound
        saved_data = compound(compound(read_nbt(source_world / "level.dat").root)["Data"])
        saved_mode_id = int(saved_data["GameType"].value)
        mode_names = {0: "survival", 1: "creative", 2: "adventure", 3: "spectator"}
        if saved_mode_id not in mode_names:
            raise ValueError("Derived saved world has an invalid typed GameType")
        properties["gamemode"] = mode_names[saved_mode_id]
        saved_world_gamemode = {"source": "typed copied level.dat Data.GameType", "id": saved_mode_id,
                                "server_property": properties["gamemode"], "player_modes_forced": False}
    (run_dir / "server.properties").write_text("\n".join(key + "=" + value for key, value in properties.items()) + "\n", encoding="utf8")
    if args.test_operator:
        # A pre-login /op name lookup lowercases the offline name and can cache a different UUID.
        # Match the client harness's exact identity; never read/change a user's ops or account files.
        name='DreamwalkerQA'
        identity=str(uuid.UUID(bytes=hashlib.md5(('OfflinePlayer:'+name).encode()).digest(),version=3))
        (run_dir / 'ops.json').write_text(json.dumps([{'uuid':identity,'name':name,'level':4,'bypassesPlayerLimit':False}])+'\n',encoding='utf8')
    # Explicit library classpath lets Loader enumerate its own parent sources;
    # java -jar plus a hand-written manifest-only launcher did not expose that
    # correctly under Loader 0.17.2 in this environment.
    explicit_classpath = os.pathsep.join(str(run_dir / item["path"]) for item in library_records)
    command = [str(java), "-Xmx3G", "-cp", explicit_classpath,
               "net.fabricmc.loader.impl.launch.server.FabricServerLauncher", "nogui"]
    result = {"schema": "dreamwalker-final-jar-server-v1", "artifact": str(jar), "artifact_sha256": artifact_sha,
              "isolated_test_operator": {"name":"DreamwalkerQA","uuid":identity,"level":4,"scope":"New localhost offline test only"} if args.test_operator else None,
              "profile": args.profile, "modset_profile_sha256": digest(args.profile_manifest) if profile else None, "modset": installed_mods,
              "minecraft": "1.20.1", "loader": args.loader, "java_version": java_version,
              "vanilla_server_sha256": digest(bundled), "loader_metadata_source": metadata_url,
              "loader_metadata_sha256": hashlib.sha256(metadata_bytes).hexdigest(), "libraries": library_records,
              "eula_evidence": eula_evidence, "run_directory": str(run_dir), "server_bind": "127.0.0.1:" + str(port),
              "server_properties": properties,
              "review_scene_input": scene_input_record,
              "bounded_source_input": source_input_record,
              "ordinary_gameplay_input": gameplay_input_record,
              "diagnostics_input": diagnostics_input_record,
              "alias_input": alias_input_record,
              "derived_world_copy": world_copy_record,
              "saved_world_gamemode": saved_world_gamemode,
              "command": command, "status": "PREPARED" if args.prepare_only else "NOT_RUN",
              "client_visuals": "NOT_RUN", "gameplay": "NOT_RUN", "original_world_loaded": False,
              "note": "A new flat smoke world cannot prove city performance or entity gameplay."}
    report = args.report or ROOT / "reports" / ("SERVER_" + args.profile.upper() + ".json")
    if args.expect_rejection and args.report is None:
        raise ValueError("An expected negative test requires an explicit separate --report path")
    if not args.prepare_only:
        started = time.monotonic()
        console = run_dir / "launch-console.log"
        startup_ok = False
        creationflags = subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0
        with console.open("w", encoding="utf8") as stream:
            process = subprocess.Popen(command, cwd=run_dir, stdin=subprocess.PIPE, stdout=stream,
                                       stderr=subprocess.STDOUT, text=True, creationflags=creationflags)
            try:
                while process.poll() is None and time.monotonic() - started < args.startup_timeout:
                    text = console.read_text(encoding="utf8", errors="replace")
                    if re.search(r'Done \([\d.]+s\)!', text):
                        startup_ok = True
                        result["startup_seconds"] = round(time.monotonic() - started, 3)
                        commands = ["list", "help bbrp", "help bb", "help bloodborne"]
                        if args.commands_file:
                            commands += console_commands(args.commands_file)
                            result["commands_file"] = {"path": str(args.commands_file.resolve()), "sha256": digest(args.commands_file)}
                        result["commands"] = commands
                        for text_command in commands:
                            process.stdin.write(text_command + "\n")
                        process.stdin.flush()
                        if args.gallery_input:
                            gallery_deadline=time.monotonic()+args.scene_timeout
                            gallery_output=run_dir/"catalogue-gallery-output.json"
                            while process.poll() is None and not gallery_output.is_file() and time.monotonic()<gallery_deadline:time.sleep(1)
                            result["gallery_output_ready_before_stop"]=gallery_output.is_file()
                        elif args.baseline_input:
                            baseline_deadline=time.monotonic()+args.scene_timeout
                            baseline_output=run_dir/"base-v10-probe-output.json"
                            while process.poll() is None and not baseline_output.is_file() and time.monotonic()<baseline_deadline:time.sleep(1)
                            result["baseline_output_ready_before_stop"]=baseline_output.is_file()
                        elif args.scene_input:
                            scene_deadline = time.monotonic() + args.scene_timeout
                            awaited_scene = run_dir / ("review-v10-scene-output.json" if document.get("revision") == "V10" else "review-scene-output.json")
                            while process.poll() is None and not awaited_scene.is_file() and time.monotonic() < scene_deadline:
                                time.sleep(1)
                            result["scene_output_ready_before_stop"] = awaited_scene.is_file()
                            if not awaited_scene.is_file():
                                result["scene_timeout"] = "No completed scene authoring output within bounded real server ticking interval"
                        elif args.gameplay_input:
                            gameplay_deadline = time.monotonic() + args.gameplay_timeout
                            gameplay_output = run_dir / "review-v9-gameplay-output.json"
                            while process.poll() is None and not gameplay_output.is_file() and time.monotonic() < gameplay_deadline:
                                time.sleep(1)
                            result["ordinary_gameplay_output_ready_before_stop"] = gameplay_output.is_file()
                            if not gameplay_output.is_file():
                                result["ordinary_gameplay_timeout"] = "No completed gameplay proof within bounded real server ticking interval"
                        elif args.diagnostics_input:
                            diagnostics_deadline = time.monotonic() + args.diagnostics_timeout
                            diagnostics_output = run_dir / "review-diagnostics-output.json"
                            while process.poll() is None and not diagnostics_output.is_file() and time.monotonic() < diagnostics_deadline:
                                time.sleep(1)
                            result["diagnostics_output_ready_before_stop"] = diagnostics_output.is_file()
                            if not diagnostics_output.is_file():
                                result["diagnostics_timeout"] = "No completed default60/save/export proof within bounded real ticking interval"
                        elif alias_document:
                            alias_deadline = time.monotonic() + args.alias_timeout
                            alias_output = run_dir / "review-v10-alias-output.json"
                            while process.poll() is None and not alias_output.is_file() and time.monotonic() < alias_deadline:
                                time.sleep(1)
                            result["alias_output_ready_before_stop"] = alias_output.is_file()
                            if not alias_output.is_file():
                                result["alias_timeout"] = "No separate completed alias-disk proof within bounded real ticking interval"
                        elif args.hold_seconds:
                            if not 1<=args.hold_seconds<=3600:
                                raise ValueError("Client-test hold must be bounded to 1..3600 seconds")
                            hold_started=time.monotonic()
                            stop_request=run_dir/"qa-normal-stop.request"
                            while process.poll() is None and time.monotonic()-hold_started<args.hold_seconds and not stop_request.is_file():
                                time.sleep(1)
                            result["client_test_hold"]={"maximumSeconds":args.hold_seconds,"elapsedSeconds":round(time.monotonic()-hold_started,3),"earlyNormalStopRequested":stop_request.is_file()}
                            if after_hold_commands and process.poll() is None:
                                result["after_hold_commands"] = after_hold_commands
                                result["after_hold_commands_file"] = {"path":str(args.after_hold_commands_file.resolve()),"sha256":digest(args.after_hold_commands_file)}
                                for text_command in after_hold_commands:
                                    process.stdin.write(text_command+"\n")
                                process.stdin.flush()
                        else:
                            time.sleep(2)
                        process.stdin.write("stop\n")
                        process.stdin.flush()
                        break
                    time.sleep(1)
                try:
                    process.wait(timeout=args.shutdown_timeout if startup_ok else 5)
                except subprocess.TimeoutExpired:
                    result["termination"] = "startup/shutdown timeout; process terminated"
                    process.terminate()
                    process.wait(timeout=10)
            finally:
                if process.poll() is None:
                    process.kill()
                    process.wait()
            result["exit_code"] = process.returncode
        console_text = console.read_text(encoding="utf8", errors="replace")
        result["console_sha256"] = digest(console)
        result["elapsed_seconds"] = round(time.monotonic() - started, 3)
        result["status"] = "PASS" if startup_ok and result["exit_code"] == 0 and "Saving worlds" in console_text else "EULA_REQUIRED" if "agree to the EULA" in console_text else "FAIL"
        result["world_startup"] = "PASS" if startup_ok else "NOT_RUN"
        result["evidence"] = str(console)
        result["rp_initialization_count"] = console_text.count("Bloodborne RP initialized: standard Minecraft health; independent namespace")
        result["errors"] = [line for line in console_text.splitlines() if "/ERROR]" in line or "Exception" in line][:50]
        if world_copy_record:
            unchanged = all(digest(source_world / row["path"]) == row["sha256"] for row in world_copy_record["files"])
            result["derived_world_copy"]["source_unchanged_after_run"] = unchanged
            if not unchanged:
                result["status"] = "FAIL_SOURCE_REVIEW_COPY_CHANGED"
        if args.baseline_input:
            baseline_output=run_dir/"base-v10-probe-output.json"
            if baseline_output.is_file():
                baseline_result=json.loads(baseline_output.read_text(encoding="utf8"))
                result["baseline_probe"]={"path":str(baseline_output),"result":baseline_result,"sha256":digest(baseline_output)}
                if (baseline_result.get("status")!="EXPECTED_BASELINE_OBSERVATIONS_PRESENT"
                        or baseline_result.get("observedCount")!=6
                        or baseline_result.get("expectedObservationCount")!=6
                        or baseline_result.get("productionJarSha256")!=artifact_sha
                        or not all(row.get("expectedObservationPresent") is True for row in baseline_result.get("observations",[]))):
                    result["status"]="FAIL_BASELINE_PROBE"
            else:result["status"]="FAIL_BASELINE_NO_OUTPUT"
        if args.scene_input:
            scene_output = run_dir / ("review-v10-scene-output.json" if document.get("revision")=="V10" else "review-scene-output.json")
            if scene_output.is_file():
                scene_result = json.loads(scene_output.read_text(encoding="utf8"))
                result["review_scene_output"] = {"path": str(scene_output), "sha256": digest(scene_output),
                                                 "result": scene_result}
                if scene_result.get("status") != "PASS_SCENE_AUTHORED_REQUIRES_PRODUCTION_REOPEN":
                    result["status"] = "FAIL_SCENE_AUTHORING"
            else:
                result["status"] = "FAIL_SCENE_AUTHORING_NO_QA_OUTPUT"
        if args.gallery_input:
            gallery_output=run_dir/"catalogue-gallery-output.json"
            if gallery_output.is_file():
                gallery_result=json.loads(gallery_output.read_text(encoding="utf8"))
                result["gallery"]={"path":str(gallery_output),"sha256":digest(gallery_output),"result":gallery_result}
                if gallery_result.get("status")!="PASS_AUTHORED_REQUIRES_PRODUCTION_REOPEN":result["status"]="FAIL_GALLERY_AUTHORING"
            else:result["status"]="FAIL_GALLERY_NO_OUTPUT"
        if args.source_input:
            source_output = run_dir / "source-review-output.json"
            if source_output.is_file():
                source_result = json.loads(source_output.read_text(encoding="utf8"))
                result["bounded_source_output"] = {"path": str(source_output), "sha256": digest(source_output), "result": source_result}
                if not source_result.get("status", "").startswith("PASS_"):
                    result["status"] = "FAIL_BOUNDED_SOURCE_MIGRATION"
            else:
                result["status"] = "FAIL_BOUNDED_SOURCE_MIGRATION_NO_QA_OUTPUT"
        if args.gameplay_input:
            gameplay_output = run_dir / "review-v9-gameplay-output.json"
            if gameplay_output.is_file():
                gameplay_result = json.loads(gameplay_output.read_text(encoding="utf8"))
                result["ordinary_gameplay_output"] = {"path": str(gameplay_output), "sha256": digest(gameplay_output), "result": gameplay_result}
                expected = "PASS_V9_ORDINARY_GAMEPLAY_AUTHOR_REQUIRES_ACTUAL_RESTART" if gameplay_document["phase"] == "AUTHOR" else "PASS_V9_ORDINARY_GAMEPLAY_AFTER_ACTUAL_RESTART"
                if gameplay_result.get("status") != expected or not result.get("ordinary_gameplay_output_ready_before_stop"):
                    result["status"] = "FAIL_V9_ORDINARY_GAMEPLAY"
                else:
                    result["gameplay"] = expected
            else:
                result["status"] = "FAIL_V9_ORDINARY_GAMEPLAY_NO_QA_OUTPUT"
        if args.diagnostics_input:
            diagnostics_output = run_dir / "review-diagnostics-output.json"
            if diagnostics_output.is_file():
                diagnostics_result = json.loads(diagnostics_output.read_text(encoding="utf8"))
                result["diagnostics_review"] = {"path": str(diagnostics_output), "sha256": digest(diagnostics_output), "result": diagnostics_result}
                expected = "PASS_SERVER_DIAGNOSTICS_" + diagnostics_document["mode"]
                if diagnostics_result.get("status") != expected or diagnostics_result.get("checksPassed") is not True or diagnostics_result.get("actualSave") is not True or not result.get("diagnostics_output_ready_before_stop"):
                    result["status"] = "FAIL_SERVER_DIAGNOSTICS"
            else:
                result["status"] = "FAIL_SERVER_DIAGNOSTICS_NO_QA_OUTPUT"
        if alias_document:
            alias_output = run_dir / "review-v10-alias-output.json"
            if alias_output.is_file():
                alias_result = json.loads(alias_output.read_text(encoding="utf8"))
                result["alias_review"] = {"path": str(alias_output), "sha256": digest(alias_output), "result": alias_result}
                expected = ("PASS_ALIAS_AUTHOR_14_INSTANCES_REQUIRES_DISTINCT_DISK_REENTER" if alias_document["mode"] == "AUTHOR"
                            else "PASS_ALIAS_DISTINCT_DISK_REENTER_14_PRESERVED_AND_7_CANONICAL_OLD_ITEM_PROBES")
                if (alias_result.get("status") != expected or alias_result.get("productionJarSha256") != artifact_sha
                        or alias_result.get("persistedOldInstanceCount") != 14 or alias_result.get("persistedOldAliasTypeCount") != 7
                        or alias_result.get("savedOldInventoryStackCount") != 7
                        or alias_result.get("actualSave") is not True or alias_result.get("actualFinalSave") is not True
                        or alias_result.get("forcedFlagsRestored") is not True or not result.get("alias_output_ready_before_stop")
                        or (alias_document["mode"] == "REENTER" and len(alias_result.get("probes", [])) != 7)):
                    result["status"] = "FAIL_ALIAS_DISK_COMPATIBILITY"
            else:
                result["status"] = "FAIL_ALIAS_DISK_COMPATIBILITY_NO_QA_OUTPUT"
        if args.expect_rejection:
            result["raw_launch_status"] = result["status"]
            result["expected_rejection"] = args.expect_rejection
            result["diagnostic_present"] = args.expect_rejection in console_text
            duplicate_registration = bool(re.search(r"duplicate (?:key|registration)|Adding duplicate key", console_text, re.IGNORECASE))
            result["duplicate_registry_error"] = duplicate_registration
            result["rejected_before_rp_initialization"] = result["diagnostic_present"] and result["rp_initialization_count"] == 0
            rejected = not startup_ok and process.returncode != 0 and result["diagnostic_present"] and not duplicate_registration
            result["status"] = "PASS_INCOMPATIBLE_INSTALL_REJECTED" if rejected else "FAIL_NEGATIVE_TEST"
    report.parent.mkdir(parents=True, exist_ok=True)
    report.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf8")
    print(json.dumps({"status": result["status"], "artifact_sha256": artifact_sha, "run_directory": str(run_dir), "report": str(report)}))
    if result["status"].startswith("FAIL"):
        raise SystemExit(1)


if __name__ == "__main__":
    main()
