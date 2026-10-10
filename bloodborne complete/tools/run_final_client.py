"""Prepare an isolated ordinary client using the remapped JAR and offline QA identity.

No launcher account/configuration files are read. This defaults to preparation;
--launch opens a visible game window for the explicitly requested game check.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import time
import uuid
import zipfile
from run_final_server import digest, fetch

ROOT = Path(__file__).resolve().parents[1]
USER = Path.home()


def unattended_options_bytes(raw):
    """One reserved options key only; preserve all unrelated line contents."""
    if len(raw) > 1024 * 1024:
        raise ValueError("Bounded isolated options input required")
    text = raw.decode("utf-8", errors="strict")
    lines = text.splitlines(keepends=True)
    found = []
    for index, line in enumerate(lines):
        body = line.rstrip("\r\n")
        key, separator, value = body.partition(":")
        if key.strip() == "pauseOnLostFocus":
            if key != "pauseOnLostFocus" or not separator or value not in ("true", "false"):
                raise ValueError("Malformed reserved pauseOnLostFocus option")
            found.append((index, value, line[len(body):]))
    if len(found) > 1:
        raise ValueError("Duplicate reserved pauseOnLostFocus options are ambiguous")
    original = found[0][1] if found else None
    before_contents = [line.rstrip("\r\n") for i, line in enumerate(lines) if not found or i != found[0][0]]
    appended_separator = ""
    if found:
        index, _, ending = found[0]
        lines[index] = "pauseOnLostFocus:false" + ending
        after = "".join(lines).encode("utf-8")
        operation = "ALREADY_FALSE" if original == "false" else "REPLACE_SINGLE_VALUE"
        line_number = index + 1
    else:
        appended_separator = "" if not text or text.endswith(("\n", "\r")) else "\n"
        after = (text + appended_separator + "pauseOnLostFocus:false\n").encode("utf-8")
        operation, line_number = "APPEND_SINGLE_KEY", len(lines) + 1
    after_contents = [line for line in after.decode("utf-8").splitlines() if not line.startswith("pauseOnLostFocus:")]
    if before_contents != after_contents:
        raise ValueError("Unattended options changed unrelated graphics/options contents")
    return after, {"key": "pauseOnLostFocus", "originalExplicitValue": original,
                   "originalValue": original if original is not None else "ABSENT_IN_GENERATED_ISOLATED_OPTIONS",
                   "newValue": "false", "operation": operation, "lineNumber1Based": line_number,
                   "appendedLineSeparator": appended_separator,
                   "unrelatedLineContentsUnchanged": True,
                   "unrelatedLineContentsSha256": hashlib.sha256(json.dumps(before_contents, ensure_ascii=False, separators=(",", ":")).encode("utf-8")).hexdigest()}


def prepare_unattended_focus_pause(run_dir, build_root=None):
    """Keep a byte-exact generated baseline; write only the new isolated profile."""
    run_dir = Path(run_dir).resolve()
    build_root = Path(build_root if build_root is not None else ROOT / "build").resolve()
    if run_dir.parent != build_root or not run_dir.name.startswith("runtime-client-") or not run_dir.is_dir():
        raise ValueError("Unattended option writes require a new isolated runtime-client directory")
    options = run_dir / "options.txt"
    source = run_dir / "qa-inputs/options-before-unattended.txt"
    if options.is_symlink() or source.exists() or source.parent.is_symlink():
        raise ValueError("Refusing ambiguous options source or prior unattended preparation")
    before = options.read_bytes()
    after, delta = unattended_options_bytes(before)
    source.parent.mkdir(exist_ok=True)
    source.write_bytes(before)
    if source.read_bytes() != before:
        raise IOError("Generated original options snapshot changed while being preserved")
    options.write_bytes(after)
    if options.read_bytes() != after or source.read_bytes() != before:
        raise IOError("Isolated unattended options byte verification failed")
    return {"schema": "dw-isolated-unattended-focus-option-v1", "enabled": True,
            "reason": "Explicit unattended QA flag avoids automatic background focus pause; it does not bypass an actual GUI or change game/mod/graphics policy",
            "scope": "Only generated derived-profile options.txt; no existing user installation or launcher options read/written",
            "sourceOriginalOptions": str(source), "sourceOriginalOptionsSha256": hashlib.sha256(before).hexdigest(),
            "derivedOptions": str(options), "derivedBeforeSha256": hashlib.sha256(before).hexdigest(),
            "derivedAfterPreparationSha256": hashlib.sha256(after).hexdigest(),
            "preciseDelta": delta, "graphicsValueChanges": [],
            "writes": ["qa-inputs/options-before-unattended.txt (new preserved generated baseline)", "options.txt (one reserved focus key)"],
            "originalUserOptions": "NOT_READ_OR_MODIFIED", "shaderOptionValues": "NOT_MODIFIED_BY_THIS_FLAG"}


def applies(rules):
    allowed = not rules
    for rule in rules:
        operating_system = rule.get("os", {})
        if operating_system.get("name", "windows") != "windows":
            continue
        if operating_system.get("arch", "amd64") not in ("amd64", "x86_64"):
            continue
        if rule.get("features"):
            continue
        allowed = rule["action"] == "allow"
    return allowed


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--jar", type=Path, required=True)
    parser.add_argument("--java-home", type=Path, default=USER / "AppData/Roaming/.minecraft/runtime/java-runtime-gamma/windows/java-runtime-gamma")
    parser.add_argument("--loader", default="0.19.5")
    parser.add_argument("--profile", choices=("minimal", "full_client"), default="minimal")
    parser.add_argument("--cached-minimal", action="store_true", help="Exact cached project runtime dependencies, no user modset")
    parser.add_argument("--profile-manifest", type=Path, default=ROOT / "reports/MODSET_PROFILE.json")
    parser.add_argument("--modset", type=Path, default=Path("C:/Users/vakir/Limacina/project/dw/mods.zip"))
    parser.add_argument("--run-name")
    parser.add_argument("--report", type=Path, help="Separate report for baseline or retained historical attempts")
    parser.add_argument("--fixture", type=Path)
    parser.add_argument("--quick-play", action="store_true", help="Automatically open copied prototype-fixture; Minecraft 1.20.1 Quick Play")
    parser.add_argument("--server")
    parser.add_argument("--launch", action="store_true")
    parser.add_argument("--extra-mod", type=Path, action="append", default=[])
    parser.add_argument("--qa-input", type=Path, help="Explicit isolated saved-world QA marker for the optional client add-on")
    parser.add_argument("--wait", action="store_true", help="Wait for marked QA client normal save/exit")
    parser.add_argument("--timeout", type=int, default=360)
    parser.add_argument("--heap-gb", type=int, choices=range(2, 13), default=4, help="Maximum Java heap only for this isolated test profile")
    parser.add_argument("--width", type=int, choices=(1280,1920), default=1280)
    parser.add_argument("--height", type=int, choices=(720,1080), default=720)
    parser.add_argument("--gui-scale", type=int, choices=(0,2,3,4), default=0, help="Isolated GUI QA scale; 0 retains Minecraft Auto, never forces scale1")
    parser.add_argument("--shader-pack", type=Path, help="Copy and enable a supplied shader ZIP only in this isolated Iris profile")
    parser.add_argument("--shader-settings", type=Path, help="Exact optional supplied settings sidecar; requires --shader-pack")
    parser.add_argument("--resource-pack", type=Path, action="append", default=[], help="Byte-copy and enable an explicit ZIP only in this isolated client")
    parser.add_argument("--unattended-no-focus-pause", action="store_true", help="Default OFF: write only isolated pauseOnLostFocus:false, preserve generated baseline bytes and report exact delta")
    args = parser.parse_args()
    if args.qa_input and (not args.fixture or not args.quick_play or not args.extra_mod):
        raise ValueError("Client QA requires an explicit isolated saved-world fixture, Quick Play and separate add-on")
    if args.wait and (not args.launch or not args.qa_input):
        raise ValueError("Automatic wait is limited to an explicitly marked QA client")
    if args.shader_settings and not args.shader_pack:
        raise ValueError("Shader settings require the explicit shader ZIP")
    jar = args.jar.resolve()
    run_name = args.run_name or args.profile + "-" + digest(jar)[:12] + "-" + str(int(time.time()))
    import re
    if not re.fullmatch(r"[a-zA-Z0-9_-]+", run_name):
        raise ValueError("Unsafe run name")
    run_dir = ROOT / "build" / ("runtime-client-" + run_name)
    if run_dir.exists():
        raise ValueError("Refusing to overwrite a previous game test")
    run_dir.mkdir(parents=True)
    mods = run_dir / "mods"
    mods.mkdir()
    shutil.copyfile(jar, mods / jar.name)
    extra_records=[]
    for extra in args.extra_mod:
        extra=extra.resolve()
        if not extra.is_file() or extra.suffix.lower()!='.jar' or (mods/extra.name).exists():
            raise ValueError("Invalid or conflicting QA add-on: "+str(extra))
        shutil.copyfile(extra,mods/extra.name)
        extra_records.append({"path":str(extra),"sha256":digest(extra),"copied_name":extra.name})
    profile = json.loads(args.profile_manifest.read_text(encoding="utf8")) if not args.cached_minimal else None
    cached_mods=[]
    if args.cached_minimal:
        if args.profile!='minimal':raise ValueError('Cached project dependencies only support minimal profile')
        for group,name,version in [('net.fabricmc.fabric-api','fabric-api','0.92.9+1.20.1'),('software.bernie.geckolib','geckolib-fabric-1.20.1','4.4.9'),('com.eliotlash.mclib','mclib','20')]:
            source=next((p for p in sorted((USER/'.gradle/caches/modules-2/files-2.1'/group/name/version).glob('*/*.jar')) if not p.name.endswith(('-sources.jar','-javadoc.jar'))),None)
            if source is None:raise ValueError('Missing exact project dependency '+name+':'+version)
            shutil.copyfile(source,mods/source.name);cached_mods.append({'coordinate':group+':'+name+':'+version,'source':str(source),'sha256':digest(source)})
    else:
        mod_paths = profile["profiles"][args.profile]
        selected = {item["path"]: item for item in profile["selected"]}
        with zipfile.ZipFile(args.modset) as archive:
            for path in mod_paths:
                data = archive.read(path)
                if hashlib.sha256(data).hexdigest() != selected[path]["sha256"]:
                    raise ValueError("Modset SHA mismatch: " + path)
                (mods / Path(path).name).write_bytes(data)
    loom = USER / ".gradle/caches/fabric-loom/1.20.1"
    minecraft = json.loads((loom / "minecraft-info.json").read_text(encoding="utf8"))
    client = run_dir / "minecraft-client.jar"
    shutil.copyfile(loom / "minecraft-client.jar", client)
    loader_url = f"https://meta.fabricmc.net/v2/versions/loader/1.20.1/{args.loader}/profile/json"
    loader_bytes = fetch(loader_url)
    loader = json.loads(loader_bytes)
    (run_dir / "fabric-loader-profile.json").write_bytes(loader_bytes)
    libs, lib_records = [], []
    natives = run_dir / "natives"
    natives.mkdir()
    for library in minecraft["libraries"] + loader["libraries"]:
        if not applies(library.get("rules", [])):
            continue
        artifact = library.get("downloads", {}).get("artifact")
        coordinate = library["name"]
        if any(coordinate.endswith(suffix) for suffix in ("natives-windows-arm64", "natives-windows-x86")):
            continue
        if artifact:
            relative = Path(artifact["path"])
            url = artifact["url"]
        else:
            group, name, version = coordinate.split(":")
            relative = Path(group.replace(".", "/")) / name / version / (name + "-" + version + ".jar")
            url = library.get("url", "https://maven.fabricmc.net/") + relative.as_posix()
        destination = run_dir / "libraries" / relative
        destination.parent.mkdir(parents=True, exist_ok=True)
        candidates = [USER / "AppData/Roaming/.minecraft/libraries" / relative, USER / "Limacina/project/dw/libraries" / relative]
        if coordinate == "net.fabricmc:fabric-loader:0.19.5":
            candidates.insert(0, USER / "Limacina/project/dw/0.19.5.jar")
        group, name, version = coordinate.split(":")[:3]
        candidates += list((USER / ".gradle/caches/modules-2/files-2.1" / group / name / version).glob("*/*.jar"))
        source = next((path for path in candidates if path.is_file() and not path.name.endswith(("-sources.jar","-javadoc.jar")) and (not artifact or hashlib.sha1(path.read_bytes()).hexdigest() == artifact["sha1"])
                       and (not library.get("sha256") or digest(path) == library["sha256"])), None)
        if source:
            shutil.copyfile(source, destination)
            provenance = str(source)
        else:
            destination.write_bytes(fetch(url))
            provenance = url
        if artifact and hashlib.sha1(destination.read_bytes()).hexdigest() != artifact["sha1"]:
            raise ValueError("Minecraft library checksum mismatch: " + coordinate)
        if library.get("sha256") and digest(destination) != library["sha256"]:
            raise ValueError("Loader library checksum mismatch: " + coordinate)
        libs.append(str(destination))
        lib_records.append({"coordinate": coordinate, "sha256": digest(destination), "source": provenance})
        if coordinate.endswith("natives-windows"):
            with zipfile.ZipFile(destination) as native_jar:
                for entry in native_jar.namelist():
                    if entry.lower().endswith(".dll"):
                        (natives / Path(entry).name).write_bytes(native_jar.read(entry))
    index_id = minecraft["assetIndex"]["id"]
    index_candidates = [USER / "AppData/Roaming/.minecraft/assets/indexes" / (index_id + ".json"),
                        USER / ".gradle/caches/fabric-loom/assets/indexes" / ("1.20.1-" + index_id + ".json")]
    asset_index = next((path for path in index_candidates if path.exists() and
                        hashlib.sha1(path.read_bytes()).hexdigest() == minecraft["assetIndex"]["sha1"]), None)
    asset_provenance=None
    if asset_index is None:
        from ensure_client_assets import ensure_assets
        assets,asset_index,asset_provenance=ensure_assets(minecraft,ROOT,USER)
    else:
        assets = asset_index.parent.parent
    fixture_record=None
    if args.fixture:
        fixture=args.fixture.resolve()
        if not fixture.is_relative_to((ROOT/'build').resolve()):
            raise ValueError("Fixture must be a derived review world under this project's build folder")
        if not (args.fixture / "level.dat").is_file():
            raise ValueError("Fixture must be a prepared separate world directory")
        files=[]
        for original in sorted(fixture.rglob('*')):
            if not original.is_file() or original.name=='session.lock':continue
            relative=original.relative_to(fixture);destination=run_dir/'saves/prototype-fixture'/relative
            destination.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(original,destination)
            expected=digest(original)
            if digest(destination)!=expected:raise ValueError("Client fixture copy mismatch: "+str(relative))
            files.append({"path":relative.as_posix(),"sha256":expected,"bytes":original.stat().st_size})
        fixture_record={"source":str(fixture),"files":files,"omitted_files":["session.lock"],"copy_byte_verification":"PASS"}
    qa_record=None
    if args.qa_input:
        marker=args.qa_input.resolve();document=json.loads(marker.read_text(encoding='utf8'))
        if document.get('guard')!='ISOLATED_SAVED_REVIEW_CLIENT_ONLY' or document.get('worldName')!='prototype-fixture':
            raise ValueError("Client QA marker has no isolated saved-world guard")
        if document.get('productionJarSha256')!=digest(jar):raise ValueError("Client QA marker and production JAR differ")
        shutil.copyfile(marker,run_dir/('bloodborne-review-v10-client-input.json' if document.get('revision')=='V10' else 'bloodborne-review-client-input.json'))
        qa_record={"source":str(marker),"sha256":digest(marker)}
    # Options and explicit packs apply only to this new local QA profile.
    resource_packs=[]
    for pack in args.resource_pack:
        pack=pack.resolve()
        if not pack.is_file() or pack.suffix.lower()!='.zip':
            raise ValueError("Explicit resource pack must be a ZIP: "+str(pack))
        destination=run_dir/'resourcepacks'/pack.name
        if destination.exists():raise ValueError("Conflicting resource pack filename: "+pack.name)
        with zipfile.ZipFile(pack) as archive:
            metadata=json.loads(archive.read('pack.mcmeta'))
            if not isinstance(metadata.get('pack',{}).get('pack_format'),int):
                raise ValueError("Resource pack lacks valid pack metadata: "+str(pack))
        destination.parent.mkdir(parents=True,exist_ok=True)
        shutil.copyfile(pack,destination)
        expected=digest(pack)
        if digest(destination)!=expected:raise ValueError("Resource pack byte-copy mismatch")
        resource_packs.append({'source':str(pack),'sha256':expected,'copied_name':pack.name,'selected_name':'file/'+pack.name,'byte_copy':'PASS'})
    options="autoJump:false\nfullscreen:false\nrenderDistance:8\nsimulationDistance:5\ngamma:1.0\ntutorialStep:none\n"
    options+='guiScale:'+str(args.gui_scale)+'\n'
    # Fabric's always-enabled aggregate would otherwise be appended after an
    # external pack omitted from a fresh options list, hiding its ALT overrides.
    if resource_packs:options+='resourcePacks:'+json.dumps(['vanilla','fabric']+[row['selected_name'] for row in resource_packs],ensure_ascii=False)+'\n'
    (run_dir / "options.txt").write_text(options, encoding="utf8")
    shader_provenance=None
    if args.shader_pack:
        from prepare_shader_profile import prepare_shader_profile
        shader_provenance=prepare_shader_profile(run_dir,args.shader_pack,args.shader_settings)
    focus_provenance = prepare_unattended_focus_pause(run_dir) if args.unattended_no_focus_pause else None
    username = "DreamwalkerQA"
    offline_uuid = str(uuid.UUID(bytes=hashlib.md5(("OfflinePlayer:" + username).encode()).digest(), version=3))
    java = args.java_home / "bin/java.exe"
    classpath = os.pathsep.join(libs + [str(client)])
    command = [str(java), "-Xmx" + str(args.heap_gb) + "G", "-Djava.library.path=" + str(natives), "-cp", classpath,
               "net.fabricmc.loader.impl.launch.knot.KnotClient", "--username", username,
               "--uuid", offline_uuid, "--accessToken", "0", "--userType", "legacy", "--version", "1.20.1",
               "--gameDir", str(run_dir), "--assetsDir", str(assets), "--assetIndex", asset_index.stem,
               "--width", str(args.width), "--height", str(args.height)]
    if args.server:
        host, port = args.server.rsplit(":", 1)
        if host not in ("127.0.0.1", "localhost"):
            raise ValueError("Only the isolated local QA server can be connected automatically")
        command += ["--server", host, "--port", port]
    if args.quick_play:
        if not args.fixture or args.server:
            raise ValueError("Quick Play requires an isolated copied fixture and no server")
        command += ["--quickPlaySingleplayer", "prototype-fixture"]
    result = {"schema": "dreamwalker-final-jar-client-v1", "artifact": str(jar), "artifact_sha256": digest(jar),
              "minecraft": "1.20.1", "loader": args.loader, "profile": args.profile,
              "modset_profile_sha256": digest(args.profile_manifest) if profile else None, "run_directory": str(run_dir), "cachedProjectDependencies":cached_mods,
              "libraries": lib_records, "loader_metadata_url": loader_url,
              "loader_metadata_sha256": hashlib.sha256(loader_bytes).hexdigest(),
              "assets_read_only": str(assets), "asset_index_sha1": hashlib.sha1(asset_index.read_bytes()).hexdigest(),
              "isolated_asset_provenance":asset_provenance,
              "offline_username": username, "fixture": str(args.fixture) if args.fixture else None,
              "derived_world_copy":fixture_record,"extra_mods":extra_records,"qa_input":qa_record,
              "shader_profile":shader_provenance,
              "unattended_focus_pause":focus_provenance,
              "explicit_resource_packs":resource_packs,
              "status": "PREPARED", "command": command, "gameplay": "NOT_RUN", "client_visuals": "NOT_RUN"}
    (run_dir / "launch-command.json").write_text(json.dumps(command, indent=2) + "\n", encoding="utf8")
    if args.launch:
        stream = (run_dir / "launch-console.log").open("w", encoding="utf8")
        process = subprocess.Popen(command, cwd=run_dir, stdout=stream, stderr=subprocess.STDOUT,
                                   creationflags=subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0)
        stream.close()
        result["pid"] = process.pid
        result["status"] = "LAUNCHED_AWAITING_GAME_VERIFICATION"
        if args.wait:
            started=time.monotonic()
            try:process.wait(timeout=args.timeout)
            except subprocess.TimeoutExpired:
                result['termination']='QA normal-exit timeout; owned process terminated'
                process.terminate()
                try:process.wait(timeout=10)
                except subprocess.TimeoutExpired:process.kill();process.wait()
            result['elapsed_seconds']=round(time.monotonic()-started,3)
            result['exit_code']=process.returncode
            output=run_dir/'client-review-output.json'
            if output.is_file():
                result['client_review_output']={"path":str(output),"sha256":digest(output),"result":json.loads(output.read_text(encoding='utf8'))}
            log=(run_dir/'launch-console.log').read_text(encoding='utf8',errors='replace')
            result['console_sha256']=digest(run_dir/'launch-console.log')
            result['errors']=[line for line in log.splitlines() if '/ERROR]' in line or 'Exception' in line][:100]
            result['integrated_save_messages_present']=('Saving worlds' in log and 'All dimensions are saved' in log)
            saved_world=run_dir/'saves/prototype-fixture'
            result['saved_level_dat_sha256']=digest(saved_world/'level.dat') if (saved_world/'level.dat').is_file() else None
            normal=(process.returncode==0 and 'termination' not in result and output.is_file()
                    and result['client_review_output']['result'].get('status','').startswith('PASS_')
                    and result['integrated_save_messages_present'] and result['saved_level_dat_sha256'] is not None)
            result['status']='PASS_CLIENT_WORLD_LOAD_SAVE_NORMAL_EXIT' if normal else 'FAIL_CLIENT_REVIEW'
    if fixture_record:
        fixture=Path(fixture_record['source'])
        fixture_record['source_unchanged_after_run']=all(digest(fixture/row['path'])==row['sha256'] for row in fixture_record['files'])
        if not fixture_record['source_unchanged_after_run']:result['status']='FAIL_SOURCE_REVIEW_COPY_CHANGED'
    for row in resource_packs:
        row['source_unchanged_after_run']=digest(Path(row['source']))==row['sha256']
        if not row['source_unchanged_after_run']:result['status']='FAIL_SOURCE_RESOURCE_PACK_CHANGED'
    if focus_provenance:
        source = Path(focus_provenance['sourceOriginalOptions'])
        focus_provenance['source_original_options_unchanged_after_run'] = digest(source) == focus_provenance['sourceOriginalOptionsSha256']
        if not focus_provenance['source_original_options_unchanged_after_run']:
            result['status'] = 'FAIL_GENERATED_ORIGINAL_OPTIONS_CHANGED'
        focus_provenance['derivedOptionsAfterRunSha256'] = digest(run_dir / 'options.txt')
        _, final_option = unattended_options_bytes((run_dir / 'options.txt').read_bytes())
        focus_provenance['pauseOnLostFocusAfterRun'] = final_option['originalExplicitValue']
        if focus_provenance['pauseOnLostFocusAfterRun'] != 'false':
            result['status'] = 'FAIL_ISOLATED_UNATTENDED_OPTION_NOT_RETAINED'
        if shader_provenance:
            retained = [{'source': shader_provenance['shader_source'], 'expectedSha256': shader_provenance['shader_sha256']}]
            if shader_provenance.get('settings'):
                retained.append({'source': shader_provenance['settings']['source'], 'expectedSha256': shader_provenance['settings']['source_sha256']})
            for row in retained:
                row['actualSha256AfterRun'] = digest(Path(row['source']))
                row['unchangedAfterRun'] = row['actualSha256AfterRun'] == row['expectedSha256']
                if not row['unchangedAfterRun']: result['status'] = 'FAIL_ORIGINAL_SHADER_INPUT_CHANGED'
            focus_provenance['originalShaderInputsVerifiedAfterRun'] = retained
    report = args.report.resolve() if args.report else ROOT / "reports" / ("CLIENT_" + args.profile.upper() + ".json")
    report.parent.mkdir(parents=True, exist_ok=True)
    report.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf8")
    print(json.dumps({"status": result["status"], "run_directory": str(run_dir), "report": str(report), "pid": result.get("pid")}))
    if result['status'].startswith('FAIL'):raise SystemExit(1)


if __name__ == "__main__":
    main()
