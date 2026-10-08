"""Independently audit a stopped shader client; resource PASS alone is insufficient."""
from __future__ import annotations
import argparse
from decimal import Decimal, InvalidOperation
import hashlib
import json
from pathlib import Path
import re
import zipfile

ROOT = Path(__file__).resolve().parents[1]
FATAL = (
    r"Falling back to normal rendering without shaders",
    r"Failed to create shader rendering pipeline",
    r"Shaders are disabled because",
    r"Failed to (?:load the shaderpack|initialize Iris configuration)",
    r"Could not load the shaderpack",
    r"ShaderCompileException|Shader compilation failed|Failed to compile shaders",
    r"(?:failed|error) (?:to |while )?(?:compile|compiling|link|linking) (?:shader|program)",
    r"ERROR:\s*\d+:\d+:",
    r"OutOfMemoryError|EXCEPTION_ACCESS_VIOLATION",
)


def digest(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def read(path):
    return json.loads(Path(path).read_text(encoding="utf8"))


def properties(path):
    """Read this supplied ASCII sidecar/config, rejecting unaccounted syntax."""
    result = {}
    for line in Path(path).read_bytes().decode("latin1").splitlines():
        line = line.strip()
        if not line or line.startswith(("#", "!")):
            continue
        match = re.fullmatch(r"([^\s:=]+)\s*[:=]\s*(.*)", line)
        if not match:
            raise ValueError("Unaccounted Java properties syntax: " + line)
        key, value = match.groups()
        for escaped in (" ", ":", "=", "#", "!"):
            value = value.replace("\\" + escaped, escaped)
        result[key] = value
    return result


def same_option(expected, actual):
    if expected == actual:
        return True
    if not isinstance(actual, str):
        return False
    try:
        return Decimal(expected) == Decimal(actual)
    except InvalidOperation:
        return False


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--client-report", type=Path, required=True)
    parser.add_argument("--artifact", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    wrapper = read(args.client_report)
    artifact_sha = digest(args.artifact)
    run = Path(wrapper["run_directory"])
    failures, checks = [], []

    def require(condition, message):
        checks.append({"check": message, "status": "PASS" if condition else "FAIL"})
        if not condition:
            failures.append(message)

    require(wrapper["artifact_sha256"] == artifact_sha, "Actual production artifact hash matches client wrapper")
    require(wrapper["profile"] == "full_client", "Supplied full-client profile selected")
    require(wrapper.get("status") == "PASS_CLIENT_WORLD_LOAD_SAVE_NORMAL_EXIT" and wrapper.get("exit_code") == 0
            and wrapper.get("integrated_save_messages_present") is True, "Ordinary client loaded, saved and exited normally")
    profile_path = ROOT / "reports/MODSET_PROFILE.json"
    manifest = read(profile_path)
    require(digest(profile_path) == wrapper["modset_profile_sha256"], "Exact selected mod profile manifest")
    selected = {row["path"]: row for row in manifest["selected"]}
    mods = []
    for path in manifest["profiles"]["full_client"]:
        row = selected[path]
        local = run / "mods" / Path(path).name
        actual = digest(local) if local.is_file() else None
        mods.append({"id": row["id"], "version": row["version"], "path": str(local), "sha256": actual})
        require(actual == row["sha256"], "Exact supplied mod bytes: " + row["id"])
    require(len(mods) == 109, "All 109 selected supplied top-level client mods present")
    shader = wrapper.get("shader_profile")
    require(shader is not None, "Explicit shader profile provenance present")
    if shader is None:
        raise ValueError("No shader profile exists; this ordinary client is not a shader test")
    require(read(run / "shader-profile.json") == shader, "Archived prepared shader profile equals wrapper")
    require(digest(shader["shader_source"]) == digest(shader["shader_copy"]) == shader["shader_sha256"],
            "Supplied shader ZIP remains byte-exact in isolated profile and original")
    iris = next(row for row in mods if row["id"] == "iris")
    require(iris["sha256"] == shader["iris"]["jar_sha256"], "Audited Iris bytecode belongs to actual supplied Iris JAR")
    with zipfile.ZipFile(iris["path"]) as archive:
        metadata = json.loads(archive.read("fabric.mod.json"))
        require(metadata["id"] == "iris" and metadata["version"] == iris["version"], "Actual Iris metadata version")
        for name, expected in shader["iris"]["class_sha256"].items():
            require(hashlib.sha256(archive.read(name)).hexdigest() == expected, "Actual audited Iris class bytes: " + name)
    for row in shader["iris"]["evidence"].values():
        require(digest(row["path"]) == row["sha256"], "Selected Iris configuration bytecode evidence unchanged")
    settings = shader["settings"]
    require(settings is not None and settings["static_option_audit"]["provided_setting_count"] == 42,
            "All 42 supplied settings staged and statically recognized")
    require(digest(settings["source"]) == settings["source_sha256"], "Original supplied settings file unchanged")
    require(Path(settings["copy"]).name == Path(shader["shader_copy"]).name + ".txt",
            "Actual staged settings use the exact selected ZIP sidecar filename")
    supplied = properties(settings["source"])
    configured = properties(shader["iris_config"])
    require(configured.get("enableShaders") == "true" and configured.get("shaderPack") == Path(shader["shader_copy"]).name,
            "Actual isolated Iris configuration retains selected ZIP and enabled shaders")
    marker_path = run / "bloodborne-review-client-input.json"
    marker = read(marker_path)
    require(marker.get("expectedShaderPackSha256") == shader["shader_sha256"]
            and marker.get("expectedShaderSettingsSha256") == settings["source_sha256"], "Explicit marker binds supplied shader and settings hashes")
    require(marker.get("expectedShaderOptions") == supplied and len(supplied) == 42, "Explicit marker requests exact 42 supplied setting values")
    result = wrapper.get("client_review_output")
    require(result is not None, "Live client QA output captured")
    client = {} if result is None else result["result"]
    if result:
        require(digest(result["path"]) == result["sha256"] and read(result["path"]) == client, "Raw live client QA output hash and content")
        require(client["markerSha256"] == digest(marker_path), "Actual live QA marker hash")
    live = client.get("irisRuntime", {})
    final = live.get("final", {})
    initial = live.get("initial", {})
    require(live.get("status") == "PASS_ACTIVE_IRIS_RENDERING_PIPELINE", "Live Iris reflection check passed")
    require(final.get("currentPackName") == marker.get("expectedShaderPack") and final.get("packPresent") is True
            and final.get("shadersEnabled") is True and final.get("fallback") is False, "Requested shader is live, enabled and not fallback")
    require(final.get("pipelineClass") == "net.irisshaders.iris.pipeline.IrisRenderingPipeline"
            and final.get("shaderMapPresent") is True and final.get("skipAllRendering") is False,
            "Actual Iris rendering pipeline and shader map are active")
    require(isinstance(initial.get("frameCounter"), int) and isinstance(final.get("frameCounter"), int)
            and final["frameCounter"] > initial["frameCounter"] and live.get("frameCounterAdvanced") is True,
            "Actual Iris rendered frame counter advanced after first ready tick")
    options = live.get("effectiveOptions", [])
    require(len(options) == 42 and {row["key"] for row in options} == set(supplied)
            and all(row["expected"] == supplied[row["key"]] and row["matches"] is True
                    and same_option(supplied[row["key"]], row["actual"]) for row in options),
            "All 42 registered runtime shader option values match supplied values")
    log_path = run / "launch-console.log"
    require(digest(log_path) == wrapper["console_sha256"], "Actual ordinary client console hash")
    lines = log_path.read_text(encoding="utf8", errors="replace").splitlines()
    fatal = [{"line": number, "text": line} for number, line in enumerate(lines, 1)
             if any(re.search(pattern, line, re.IGNORECASE) for pattern in FATAL)]
    require(not fatal, "No shader load/compile/link/disable/fallback or process fatal errors in full console")
    pipeline_lines = [{"line": number, "text": line} for number, line in enumerate(lines, 1) if "Creating pipeline for dimension" in line]
    pack_lines = [{"line": number, "text": line} for number, line in enumerate(lines, 1)
                  if marker["expectedShaderPack"] in line and "shaderpack" in line.lower()]
    require(bool(pipeline_lines) and bool(pack_lines), "Iris console independently confirms pack loading and dimension pipeline creation")
    interesting = [{"line": number, "text": line} for number, line in enumerate(lines, 1)
                   if re.search(r"Iris|shader|pipeline|OpenGL|renderer|vendor|profile", line, re.IGNORECASE)]
    report = {
        "schema": "dreamwalker-shader-runtime-independent-v1",
        "status": "PASS_ACTUAL_IRIS_SHADER_PIPELINE_OPTIONS_AND_NORMAL_SAVE_EXIT" if not failures else "FAIL_SHADER_RUNTIME_VERIFICATION",
        "artifact": str(args.artifact.resolve()), "artifact_sha256": artifact_sha,
        "client_report": str(args.client_report.resolve()), "client_report_sha256": digest(args.client_report),
        "run_directory": str(run), "console_sha256": wrapper["console_sha256"],
        "marker_sha256": digest(marker_path), "iris": iris, "selected_supplied_mod_count": len(mods),
        "shader_pack_sha256": shader["shader_sha256"], "source_settings_sha256": settings["source_sha256"],
        "staged_settings_sha256_after_game": digest(settings["copy"]),
        "staged_settings_bytes_identical_after_game": digest(settings["copy"]) == settings["copy_sha256"],
        "config_sha256_after_game": digest(shader["iris_config"]),
        "prepared_config_sha256": shader["iris_config_sha256"],
        "live_iris": live, "checks": checks, "failures": failures,
        "shader_fatal_log_lines": fatal, "pack_load_log_lines": pack_lines, "dimension_pipeline_log_lines": pipeline_lines,
        "shader_and_graphics_log_lines": interesting,
        "compiler_scope": "Live IrisRenderingPipeline with a shader map proves its constructor and exercised frame rendering succeeded; no logged shader compile/link failure. This does not prove every unvisited dimension/optional path compiled.",
        "shouldOverrideShaders_scope": "At END_CLIENT_TICK this flag may be false outside the world render phase; it is recorded, not asserted.",
        "manual_visual_acceptance": "NOT_RUN", "shader_shadow_art_and_lighting_fidelity": "PENDING_USER_REVIEW",
        "limits": ["Actual selected full-profile shader operation in this derived scene only",
                   "No screenshot/manual gameplay/Creative UI or source-shader visual equivalence is inferred from successful runtime checks",
                   "Iris may reserialize staged config/settings after load; original inputs and live effective option values are checked separately"]
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf8")
    print(json.dumps({"status": report["status"], "artifact_sha256": artifact_sha,
                      "supplied_mods": len(mods), "effective_settings": len(options), "failures": failures}))
    raise SystemExit(1 if failures else 0)


if __name__ == "__main__":
    main()
