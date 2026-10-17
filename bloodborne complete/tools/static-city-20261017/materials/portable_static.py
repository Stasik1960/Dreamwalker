"""Portable controller for one experimental, resumable city-static migration.

The controller only operates below .handoff-local/work/static-city.  ``prepare``
stages a pinned Fabric server, a static-only fourth-stream plan and a fresh
operation marker in the independent world.  ``pilot`` processes at most 128
components.  ``full`` resumes the same native checkpoint to completion.

This is data-migration tooling.  Its receipts deliberately keep
gameplayVerified=false and the unresolved edge caveat; a native PASS is not a
gameplay or memory-efficacy claim.
"""
from __future__ import annotations

import argparse
import gzip
import hashlib
import json
import os
import re
import shutil
import subprocess
import time
import uuid
import zipfile
from contextlib import contextmanager
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[2]
LOCAL = ROOT / ".handoff-local"
WORK = LOCAL / "work" / "static-city"
WORLD = WORK / "world"
RUNTIME = WORLD / ".dw-static-runtime"
PLAN = WORK / "plan"
PREPARED = WORK / "portable-static-prepared.json"
CP = "dw-static-architecture-checkpoint.json"
RESULT = "dw-static-architecture-result.json"
LEGACY = ("dw-conversion-checkpoint.json", "dw-conversion-result.json")
COUNT_KEYS = ("loadedWorldChunks", "holders", "readableHolders", "pendingHolders", "chunksToUnload", "unloadQueue")
SOURCE_GAME4_SHA256 = "1805b3e922ad246b4f5222aaf83ae0527c458d1624cfaa12e02b139d00faa4e1"
SOURCE_CATALOG_SHA256 = "8d50359d45015b84205bb33cf95031b6268dee2ef681e84f89c14601943b62bd"


def need(value: Any, message: str) -> None:
    if not value:
        raise RuntimeError(message)


def read(path: Path) -> Any:
    with path.open("r", encoding="utf-8-sig") as source:
        return json.load(source)


def atomic_json(path: Path, value: Any) -> None:
    pending = path.with_name(path.name + ".pending")
    need(not pending.exists(), f"Pending write already exists: {pending}")
    pending.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    os.replace(pending, path)


def sha(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(4 * 1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def pin(path: Path) -> dict[str, Any]:
    return {"path": str(path), "bytes": path.stat().st_size, "sha256": sha(path)}


def ordinary(path: Path, *, existing: bool = True) -> Path:
    path = path.absolute()
    probe = path if path.exists() else path.parent
    if existing:
        need(path.exists(), f"Missing path: {path}")
    for item in (probe, *probe.parents):
        if item.exists():
            attrs = getattr(item.lstat(), "st_file_attributes", 0)
            need(not (attrs & 1024), f"Reparse path is forbidden: {item}")
    return path


@contextmanager
def stopped_world(world: Path):
    import msvcrt

    lock = ordinary(world / "session.lock")
    with lock.open("r+b") as handle:
        handle.seek(0)
        msvcrt.locking(handle.fileno(), msvcrt.LK_NBLCK, 1)
        try:
            yield
        finally:
            handle.seek(0)
            msvcrt.locking(handle.fileno(), msvcrt.LK_UNLCK, 1)


def copy_pinned(source: Path, destination: Path, expected: str | None = None) -> dict[str, Any]:
    source = ordinary(source)
    digest = sha(source)
    if expected is not None:
        need(digest == expected, f"Pinned input changed: {source}")
    need(not destination.exists(), f"Refusing to replace staged file: {destination}")
    destination.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(source, destination)
    need(sha(destination) == digest, f"Staged copy differs: {destination}")
    return pin(destination)


def empty_gzip(path: Path) -> None:
    with path.open("xb") as raw:
        with gzip.GzipFile(fileobj=raw, mode="wb", filename="", mtime=0):
            pass


def mod_id(path: Path) -> str:
    with zipfile.ZipFile(path) as archive:
        return json.loads(archive.read("fabric.mod.json"))["id"]


def local_paths() -> dict[str, Any]:
    data = read(ordinary(LOCAL / "local-paths.json"))
    need(Path(data["packageRoot"]).resolve() == ROOT.resolve(), "local-paths belongs to another restore")
    return data


def selection_number(proof: dict[str, Any], plural: str, singular: str) -> int:
    for key in (plural, singular, singular + "Count"):
        if key in proof:
            value = proof[key]
            need(type(value) is int and value >= 0, f"Invalid selection count: {key}")
            return value
    raise RuntimeError(f"Selection proof is missing {plural}")


def fully_drained(document: dict[str, Any]) -> bool:
    lifecycle = document.get("chunkLifecycle", {})
    counts = lifecycle.get("totals", {})
    return (set(counts) == set(COUNT_KEYS)
            and all(type(counts[key]) is int and counts[key] == 0 for key in COUNT_KEYS)
            and lifecycle.get("heldDirectTicketChunks") == 0
            and lifecycle.get("loadInFlight") is False)


def prepare(args: argparse.Namespace) -> None:
    need(args.authorize_experimental_data_migration, "Explicit --authorize-experimental-data-migration is required")
    need(args.known_unresolved_edge_caveat.strip(), "The known unresolved edge caveat must be recorded")
    need(WORLD.is_dir(), f"Independent checkpoint world must already exist: {WORLD}")
    need(not PLAN.exists() and not RUNTIME.exists() and not PREPARED.exists(), "Static operation was already prepared")

    config = local_paths()
    source_game = ordinary(Path(config["paths"]["game4Jar"]))
    game6 = ordinary(args.game6)
    author = ordinary(args.author)
    generated = ordinary(args.generated_plan)
    selection_path = ordinary(generated / "selection-proof.json")
    catalog_path = ordinary(generated / "static-architecture-v1274.json")
    component_path = ordinary(generated / "static-architecture-components.jsonl.gz")
    selection = read(selection_path)
    source_catalog_sha = selection.get("sourceCatalogSha256", SOURCE_CATALOG_SHA256)
    operation_catalog_sha = selection.get("operationCatalogSha256", selection.get("catalogSha256", sha(catalog_path)))
    need(isinstance(source_catalog_sha, str) and len(source_catalog_sha) == 64, "Selection proof lacks sourceCatalogSha256")
    need(operation_catalog_sha == sha(catalog_path) and operation_catalog_sha != source_catalog_sha,
         "Expanded operation catalog binding is absent or unchanged")

    preparation = ordinary(Path(config["paths"]["preparation"]))
    original_runtime = ordinary(preparation / "runtime")
    original_manifest_path = ordinary(original_runtime / "manifest.json")
    original_manifest = read(original_manifest_path)
    source_plan = ordinary(Path(config["paths"]["sourcePlan"]))
    policy = ordinary(Path(config["paths"]["sourceStylePolicy"]))
    if "sourcePlanSha256" in selection:
        need(original_manifest["sourcePlanSha256"] == selection["sourcePlanSha256"], "Generated plan source-plan pin changed")
    if "sourceStylePolicySha256" in selection:
        need(original_manifest["sourceStylePolicySha256"] == selection["sourceStylePolicySha256"], "Generated plan policy pin changed")

    source_game_sha = SOURCE_GAME4_SHA256
    production_game_sha = sha(game6)
    author_sha = sha(author)
    need(source_game_sha != production_game_sha, "Source .4 and production .6 must be distinct")
    need(mod_id(game6) == "bloodborne_dw", "game6 is not bloodborne_dw")
    need(mod_id(author) == "dreamwalker_full_conversion_author", "Wrong bound author mod")
    with zipfile.ZipFile(author) as archive:
        binding = json.loads(archive.read("dw-conversion-binding.json"))
    need(binding["productionJarSha256"] == production_game_sha, "Author is not bound to game6")

    PLAN.mkdir(parents=True)
    for name in ("owners.jsonl.gz", "whole-objects.jsonl.gz", "entities.jsonl.gz"):
        empty_gzip(PLAN / name)
    copy_pinned(component_path, PLAN / component_path.name, selection.get("streamSha256"))

    with stopped_world(WORLD):
        legacy_cp_path = ordinary(WORLD / LEGACY[0])
        legacy_result_path = ordinary(WORLD / LEGACY[1])
        legacy_cp = read(legacy_cp_path)
        legacy_result = read(legacy_result_path)
        for receipt in (legacy_cp, legacy_result):
            need(receipt["status"] == "PASS_RUNTIME_CONVERTED_REOPEN_REQUIRED"
                 and receipt["phase"] == receipt["phaseCount"] == 3 and receipt["line"] == 0,
                 "Independent source world is not a completed phase-3 conversion")
            need(receipt["productionJarSha256"] == source_game_sha, "Independent source world was not converted with .4")
        need(legacy_cp["operationId"] == legacy_result["operationId"], "Legacy receipt operation mismatch")
        need(not (WORLD / CP).exists() and not (WORLD / RESULT).exists(), "Static checkpoint already exists")

        components = selection_number(selection, "plannedComponents", "plannedComponent")
        owners = selection_number(selection, "plannedOwners", "plannedOwner")
        cells = selection_number(selection, "plannedCells", "plannedCell")
        proof = {
            "schemaVersion": 1,
            "sourceCheckpointSha256": sha(legacy_cp_path),
            "sourceResultSha256": sha(legacy_result_path),
            "sourceOperationId": legacy_cp["operationId"],
            "sourceRuntimePlanPath": str(original_runtime),
            "sourceRuntimeManifestSha256": legacy_cp["runtimeManifestSha256"],
            "sourceConvertedRows": legacy_cp["committed"] + legacy_cp["alreadyPresent"],
            "sourceGameSha256": source_game_sha,
            "productionGameSha256": production_game_sha,
            "sourceSourcePlanSha256": original_manifest["sourcePlanSha256"],
            "sourceCatalogSha256": source_catalog_sha,
            "operationCatalogSha256": operation_catalog_sha,
            "operationComponentCount": components,
            "operationOwnerCount": owners,
            "operationCellCount": cells,
        }
        manifest = dict(original_manifest)
        manifest.update(
            fixtureOnly=False,
            staticArchitectureCatalogSha256=operation_catalog_sha,
            staticArchitecturePostConversion=proof,
            runtimeExecution="READY_EXPERIMENTAL_STATIC_DATA_MIGRATION_ONLY",
            runtimeFiles={name: sha(PLAN / name) for name in (
                "owners.jsonl.gz", "whole-objects.jsonl.gz", "entities.jsonl.gz",
                "static-architecture-components.jsonl.gz")},
        )
        atomic_json(PLAN / "manifest.json", manifest)
        plan_sha = sha(PLAN / "manifest.json")

        operation = str(uuid.uuid4())
        history = WORLD / "dw-conversion-history" / legacy_cp["operationId"]
        need(not history.exists(), "Legacy history already staged")
        history.mkdir(parents=True)
        history_pins = []
        for item in (legacy_cp_path, legacy_result_path, ordinary(WORLD / "dw-derived-copy.json")):
            history_pins.append(copy_pinned(item, history / item.name))

        protected = [
            str(ordinary(Path(config["paths"]["completedWorld"]))),
            str(ordinary(Path(config["paths"]["packedWorld"]))),
            str(ordinary(LOCAL / "checkpoints" / "completed-v4-20261017")),
        ]
        authorization = {
            "schemaVersion": 1,
            "status": "AUTHORIZED_EXPERIMENTAL_STATIC_DATA_MIGRATION",
            "allowCityStaticArchitecture": True,
            "explicitDataMigrationAuthorized": True,
            "gameplayVerified": False,
            "edgeAppearanceResolved": False,
            "knownUnresolvedEdgeCaveat": args.known_unresolved_edge_caveat,
            "operationId": operation,
            "targetWorldPath": str(WORLD),
            "runtimeManifestSha256": plan_sha,
            "authorJarSha256": author_sha,
            "sourceGameSha256": source_game_sha,
            "productionGameSha256": production_game_sha,
            "sourceCatalogSha256": source_catalog_sha,
            "operationCatalogSha256": operation_catalog_sha,
            "protectedWorldPaths": protected,
        }
        authorization_path = WORLD / "dw-static-data-migration-authorization.json"
        atomic_json(authorization_path, authorization)

        derived_path = WORLD / "dw-derived-copy.json"
        derived = read(derived_path)
        need(derived["status"] == "PASS_DERIVED_COPY_PREPARED"
             and derived["sourcePlanSha256"] == original_manifest["sourcePlanSha256"],
             "Derived source identity changed")
        derived.update(
            operationId=operation,
            targetRealPath=str(WORLD),
            targetWorldPath=str(WORLD),
            runtimeManifestSha256=plan_sha,
            staticArchitecturePostConversion=proof,
            previousConversionHistory=str(history),
        )
        atomic_json(derived_path, derived)

    bootstrap_dir = ordinary(args.bootstrap)
    bootstrap = read(ordinary(bootstrap_dir / "bootstrap.json"))
    need(bootstrap["status"] == "PASS_PREPARED_NATIVE_NOT_RUN" and bootstrap["minecraft"] == "1.20.1"
         and bootstrap["javaRequired"] == 17 and not bootstrap["javaLaunched"] and not bootstrap["worldsOpened"],
         "Bootstrap bundle is not the verified world-free 1.20.1/Java17 bundle")
    RUNTIME.mkdir(parents=True)
    staged_bootstrap = []
    for row in bootstrap["runtimeFiles"]:
        relative = Path(row["runtimePath"])
        need(not relative.is_absolute() and ".." not in relative.parts, "Bootstrap runtime path escapes")
        source_relative = Path(row.get("sourcePath", row["runtimePath"]))
        staged_bootstrap.append(copy_pinned(bootstrap_dir / source_relative, RUNTIME / relative, row["sha256"]))
    launcher_row = bootstrap["launcher"]
    launcher_pin = copy_pinned(bootstrap_dir / launcher_row["path"],
                               RUNTIME / "fabric-server-launch.jar", launcher_row["sha256"])

    mods = RUNTIME / "mods"
    mods.mkdir()
    fabric_api = ordinary(args.fabric_api)
    geckolib = ordinary(args.geckolib)
    expected_mods = ((game6, "bloodborne_dw"), (author, "dreamwalker_full_conversion_author"),
                     (fabric_api, "fabric-api"), (geckolib, "geckolib"))
    mod_pins = []
    for source, expected_id in expected_mods:
        need(mod_id(source) == expected_id, f"Unexpected mod ID for {source}")
        mod_pins.append(copy_pinned(source, mods / source.name))
    eula = ordinary(args.eula)
    need(re.search(r"(?m)^\s*eula\s*=\s*true\s*$", eula.read_text(encoding="utf-8-sig")), "Accepted EULA evidence missing")
    copy_pinned(eula, RUNTIME / "eula.txt")
    (RUNTIME / "server.properties").write_text(
        "server-ip=127.0.0.1\nserver-port=25596\nonline-mode=false\nmax-players=0\n"
        "enable-command-block=false\nspawn-protection=0\nview-distance=2\nsimulation-distance=2\n"
        "max-tick-time=600000\n", encoding="utf-8")

    marker = {
        "schemaVersion": 1,
        "mode": "CITY_STATIC_ARCHITECTURE_AFTER_COMPLETE",
        "explicitConversionAuthorized": True,
        "offlinePreparationStatus": "PASS_DERIVED_COPY_PREPARED",
        "operationId": operation,
        "targetWorldPath": str(WORLD),
        "runtimePlanPath": str(PLAN),
        "runtimeManifestSha256": plan_sha,
        "productionJarSha256": production_game_sha,
        "sourceGameSha256": source_game_sha,
        "productionGameSha256": production_game_sha,
        "sourceCatalogSha256": source_catalog_sha,
        "operationCatalogSha256": operation_catalog_sha,
        "sourcePlanPath": str(source_plan),
        "sourcePlanSha256": original_manifest["sourcePlanSha256"],
        "sourceStylePolicyPath": str(policy),
        "sourceStylePolicySha256": original_manifest["sourceStylePolicySha256"],
        "sourceStylePolicy": original_manifest["sourceStylePolicy"],
        "staticArchitectureSourceCheckpointSha256": proof["sourceCheckpointSha256"],
        "staticArchitectureDataMigrationAuthorizationPath": str(authorization_path),
        "staticArchitectureDataMigrationAuthorizationSha256": sha(authorization_path),
        "cityStaticAuthorJarSha256": author_sha,
    }
    atomic_json(RUNTIME / "dw-full-conversion-input.json", marker)
    receipt = {
        "schemaVersion": 1,
        "status": "PASS_PORTABLE_STATIC_PREPARED_NOT_RUN",
        "world": str(WORLD),
        "runtime": str(RUNTIME),
        "plan": str(PLAN),
        "operationId": operation,
        "marker": pin(RUNTIME / "dw-full-conversion-input.json"),
        "manifest": pin(PLAN / "manifest.json"),
        "authorization": pin(authorization_path),
        "selectionProof": pin(selection_path),
        "operationCatalog": pin(catalog_path),
        "componentStream": pin(PLAN / "static-architecture-components.jsonl.gz"),
        "sourceGameSha256": source_game_sha,
        "productionGame": pin(game6),
        "author": pin(author),
        "bootstrapManifestSha256": sha(bootstrap_dir / "bootstrap.json"),
        "bootstrapFiles": staged_bootstrap,
        "bootstrapLauncher": launcher_pin,
        "mods": mod_pins,
        "legacyControls": {name: sha(WORLD / name) for name in LEGACY},
        "history": history_pins,
        "gameplayVerified": False,
        "knownUnresolvedEdgeCaveat": args.known_unresolved_edge_caveat,
        "javaStarted": False,
        "worldRegionFilesWritten": False,
    }
    atomic_json(PREPARED, receipt)


def validate_prepared() -> tuple[dict[str, Any], dict[str, Any], Path]:
    receipt = read(ordinary(PREPARED))
    need(receipt["status"] == "PASS_PORTABLE_STATIC_PREPARED_NOT_RUN", "Portable preparation receipt is not usable")
    need(Path(receipt["world"]).resolve() == WORLD.resolve() and Path(receipt["runtime"]).resolve() == RUNTIME.resolve(),
         "Portable receipt paths changed")
    marker_path = ordinary(RUNTIME / "dw-full-conversion-input.json")
    manifest_path = ordinary(PLAN / "manifest.json")
    need(sha(marker_path) == receipt["marker"]["sha256"] and sha(manifest_path) == receipt["manifest"]["sha256"],
         "Marker or static manifest changed")
    marker = read(marker_path)
    manifest = read(manifest_path)
    for name, digest in manifest["runtimeFiles"].items():
        need(sha(ordinary(PLAN / name)) == digest, f"Static runtime stream changed: {name}")
    expected_mods = {Path(row["path"]).name: row["sha256"] for row in receipt["mods"]}
    need({item.name: sha(item) for item in (RUNTIME / "mods").glob("*.jar")} == expected_mods, "Isolated four-mod set changed")
    need(sha(ordinary(RUNTIME / "fabric-server-launch.jar")) == receipt["bootstrapLauncher"]["sha256"],
         "Pinned Fabric server launcher changed")
    for name, digest in receipt["legacyControls"].items():
        need(sha(ordinary(WORLD / name)) == digest, "Completed legacy control bytes changed")
    game = ordinary(Path(receipt["productionGame"]["path"]))
    need(sha(game) == receipt["productionGame"]["sha256"] == marker["productionGameSha256"], "Production game6 changed")
    return receipt, marker, game


def java_home() -> Path:
    configured = ordinary(Path(local_paths()["javaHome17"]))
    release = (configured / "release").read_text(encoding="utf-8-sig")
    need(re.search(r'(?m)^JAVA_VERSION="17(?:\.|\")', release), "Configured javaHome17 is not Java 17")
    return configured


def native(args: argparse.Namespace, full: bool) -> None:
    receipt, marker, game = validate_prepared()
    java = java_home()
    cp_path, result_path = WORLD / CP, WORLD / RESULT
    stop_path = WORLD / "dw-conversion-stop.request"
    need(not stop_path.exists(), "Existing native stop request must be reviewed before execution")
    with stopped_world(WORLD):
        need(cp_path.exists() == full, "pilot requires a fresh cursor; full requires the pilot cursor")
        before = read(cp_path) if full else {}
        if full:
            need(before["operationId"] == marker["operationId"]
                 and before["productionJarSha256"] == marker["productionGameSha256"]
                 and before["runtimeManifestSha256"] == marker["runtimeManifestSha256"],
                 "Existing static checkpoint belongs to another operation")
            need(fully_drained(before), "Full resume requires a fully drained durable checkpoint")
            current_sha = sha(cp_path)
            pilot_ok = False
            for pilot_receipt_path in WORK.glob("pilot-evidence*/result.json"):
                try:
                    pilot_receipt = read(pilot_receipt_path)
                except (OSError, ValueError, json.JSONDecodeError):
                    continue
                if (pilot_receipt.get("status") == "PASS_BOUNDED_EXPERIMENTAL_STATIC_PILOT_GAMEPLAY_UNVERIFIED"
                        and pilot_receipt.get("pilotCheckpointSha256") == current_sha
                        and pilot_receipt.get("operationId") == marker["operationId"]):
                    pilot_ok = True
                    break
            retained_ok = False
            if not pilot_ok:
                prior = []
                for candidate in WORK.glob("full-evidence*/result.json"):
                    try:
                        row = read(candidate)
                    except (OSError, ValueError, json.JSONDecodeError):
                        continue
                    if (row.get("status") == "STOPPED_FULL_REVIEW_AND_RESUME_DURABLE_CURSOR"
                            and row.get("retainedCheckpointSha256") == current_sha
                            and row.get("operationId") == marker["operationId"]):
                        prior.append((candidate.stat().st_mtime_ns, candidate))
                retained_ok = bool(prior)
            need(pilot_ok or retained_ok,
                 "Full resume requires the matching PASS pilot checkpoint or a retained drained full cursor")
        if result_path.exists():
            history = WORLD / "dw-static-run-history" / time.strftime("%Y%m%d-%H%M%S")
            history.mkdir(parents=True)
            os.replace(result_path, history / RESULT)

    evidence = WORK / ("full-evidence" if full else "pilot-evidence")
    if evidence.exists():
        suffix = 2
        while evidence.with_name(evidence.name + f"-{suffix}").exists():
            suffix += 1
        evidence = evidence.with_name(evidence.name + f"-{suffix}")
    evidence.mkdir(parents=True)
    max_rows = 0 if full else 128
    max_ticks = 0 if full else 12000
    checkpoint_rows = 1024 if full else 32
    flags = {
        "boundedChunks": "true", "maxTicks": str(max_ticks), "pauseBeforeRows": "false",
        "checkpointRows": str(checkpoint_rows), "maxWorkingChunks": "16", "maxLoadedChunks": "768",
        "maxHolders": "1024", "maxDrainTicks": "2400", "measurementHoldTicks": "0",
        "staticGrassMasonry": "false", "staticArchitecture": "true", "maxRows": str(max_rows),
    }
    launcher = ordinary(RUNTIME / "fabric-server-launch.jar")
    command = [
        str(java / "bin" / "java.exe"), "-Xmx10240M", "-XX:+ExitOnOutOfMemoryError",
        "-Ddw.conversion.productionJar=" + str(game),
        *["-Ddw.conversion." + key + "=" + value for key, value in flags.items()],
        "-jar", str(launcher), "nogui", "--universe", str(WORLD.parent), "--world", WORLD.name,
    ]
    report: dict[str, Any] = {
        "status": "PLANNED_NOT_STARTED", "mode": "full" if full else "pilot", "command": command,
        "operationId": marker["operationId"], "boundedProperties": flags, "heapMaxMiB": 10240,
        "timeoutSeconds": args.timeout_seconds, "forcedGc": False, "memorySampler": False,
        "gameplayVerified": False, "knownUnresolvedEdgeCaveat": receipt["knownUnresolvedEdgeCaveat"],
    }
    atomic_json(evidence / "result.json", report)
    env = dict(os.environ)
    need(not any(env.get(name) for name in ("JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS")), "Unexpected inherited JVM flags")
    process: subprocess.Popen[str] | None = None
    started = time.monotonic()
    stop_requested = False
    try:
        with (evidence / "console.log").open("x", encoding="utf-8", errors="replace") as console:
            process = subprocess.Popen(command, cwd=RUNTIME, env=env, stdin=subprocess.PIPE,
                                       stdout=console, stderr=subprocess.STDOUT, text=True)
            report.update(status="RUNNING", pid=process.pid)
            atomic_json(evidence / "result.json", report)
            while process.poll() is None:
                if time.monotonic() - started > args.timeout_seconds:
                    stop_path.write_text("Portable static controller coarse timeout; drain and retain durable cursor.\n", encoding="utf-8")
                    stop_requested = True
                    break
                time.sleep(1)
            if stop_requested:
                process.wait(timeout=180)
        need(process.returncode == 0 and not stop_requested, "Native process did not finish normally within the coarse timeout")
        console_text = (evidence / "console.log").read_text(encoding="utf-8", errors="replace")
        need("All dimensions are saved" in console_text and "OutOfMemoryError" not in console_text
             and "Crash report saved to" not in console_text, "Native save was not clean")
        with stopped_world(WORLD):
            checkpoint = read(ordinary(cp_path))
            result = read(ordinary(result_path))
            for key in ("operationId", "productionJarSha256", "runtimeManifestSha256", "targetWorldPath",
                        "phase", "line", "committed", "alreadyPresent", "architectureSkipped",
                        "architectureOwners", "architectureCells", "status", "originalRules",
                        "cityStaticArchitecture", "staticArchitecture", "staticGrassMasonry",
                        "boundedChunkLoading", "boundedChunkLimits"):
                need(checkpoint[key] == result[key], f"Static checkpoint/result mismatch: {key}")
            need(result["operationId"] == marker["operationId"]
                 and result["productionJarSha256"] == marker["productionGameSha256"]
                 and result["runtimeManifestSha256"] == marker["runtimeManifestSha256"], "Native result identity changed")
            for document in (checkpoint, result):
                limits = document["boundedChunkLimits"]
                expected = {"checkpointRows": checkpoint_rows, "maxWorkingChunks": 16,
                            "maxLoadedChunks": 768, "maxHolders": 1024, "maxDrainTicks": 2400}
                need(all(limits[key] == value for key, value in expected.items()), "Native bounded limits changed")
                need(fully_drained(document), "Native checkpoint/result was not fully drained")
            delta_fields = ("committed", "alreadyPresent", "architectureSkipped", "architectureOwners", "architectureCells")
            deltas = {key: result.get(key, 0) - before.get(key, 0) for key in delta_fields}
            delta = deltas["committed"] + deltas["alreadyPresent"] + deltas["architectureSkipped"]
            processed_after = result.get("committed", 0) + result.get("alreadyPresent", 0) + result.get("architectureSkipped", 0)
            proof = read(PLAN / "manifest.json")["staticArchitecturePostConversion"]
            if full:
                need(result["status"] == "PASS_RUNTIME_CONVERTED_REOPEN_REQUIRED"
                     and result["phase"] == result["phaseCount"] == 4 and result["line"] == 0,
                     "Full static stream did not complete")
                need(processed_after == proof["operationComponentCount"], "Full component accounting differs from plan")
                need(result["architectureOwners"] + result.get("architectureSkippedOwners", 0) == proof["operationOwnerCount"],
                     "Full owner accounting differs from plan")
                need(result["architectureCells"] + result.get("architectureSkippedCells", 0) == proof["operationCellCount"],
                     "Full cell accounting differs from plan")
                status = "PASS_COMPLETE_EXPERIMENTAL_STATIC_DATA_MIGRATION_GAMEPLAY_UNVERIFIED"
            else:
                need(result["status"] in ("PAUSED_OPERATIONAL_LIMIT", "PASS_RUNTIME_CONVERTED_REOPEN_REQUIRED")
                     and 0 < delta <= 128, "Pilot did not produce bounded durable progress")
                stream_completed = (result["status"] == "PASS_RUNTIME_CONVERTED_REOPEN_REQUIRED"
                                    and result["phase"] == result["phaseCount"] == 4 and result["line"] == 0)
                need(delta == 128 or stream_completed, "Pilot stopped before 128 processed components without completing the stream")
                need(deltas["committed"] > 0 and deltas["architectureOwners"] > 0 and deltas["architectureCells"] > 0,
                     "Pilot produced no newly committed static components/owners/cells")
                status = "PASS_BOUNDED_EXPERIMENTAL_STATIC_PILOT_GAMEPLAY_UNVERIFIED"
            report.update(status=status, checkpoint=checkpoint, nativeResult=result,
                          processedComponentDelta=delta, counterDeltas=deltas,
                          pilotCheckpointSha256=sha(cp_path) if not full else None,
                          console=pin(evidence / "console.log"),
                          gameplayVerified=False, ordinaryGameReopenRequired=True)
    except BaseException as failure:
        report.update(status=("STOPPED_FULL_REVIEW_AND_RESUME_DURABLE_CURSOR" if full
                              else "STOPPED_PILOT_REVIEW_REQUIRED"), failure=repr(failure))
        raise
    finally:
        if process is not None and process.poll() is None:
            if not stop_path.exists():
                stop_path.write_text("Portable static controller cleanup; drain and retain durable cursor.\n", encoding="utf-8")
            try:
                if process.stdin:
                    process.stdin.write("stop\n")
                    process.stdin.flush()
                process.wait(timeout=180)
            except BaseException:
                process.terminate()
                process.wait(timeout=30)
                report["forcedTermination"] = True
        if full and cp_path.is_file():
            try:
                retained = read(cp_path)
                if (retained.get("operationId") == marker["operationId"]
                        and retained.get("productionJarSha256") == marker["productionGameSha256"]
                        and retained.get("runtimeManifestSha256") == marker["runtimeManifestSha256"]
                        and fully_drained(retained)):
                    report["retainedCheckpointSha256"] = sha(cp_path)
                    report["retainedCheckpoint"] = retained
            except (OSError, ValueError, json.JSONDecodeError):
                pass
        report.update(exitCode=process.returncode if process else None, seconds=time.monotonic() - started)
        atomic_json(evidence / "result.json", report)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="action", required=True)
    p = sub.add_parser("prepare")
    p.add_argument("--generated-plan", type=Path, required=True)
    p.add_argument("--game6", type=Path, required=True)
    p.add_argument("--author", type=Path, required=True)
    defaults = ROOT / "project" / "build" / "v1274-converter-inputs-r1"
    p.add_argument("--bootstrap", type=Path, default=defaults / "bootstrap")
    p.add_argument("--fabric-api", type=Path, default=defaults / "dependencies" / "fabric-api-0.92.9+1.20.1.jar")
    p.add_argument("--geckolib", type=Path, default=defaults / "dependencies" / "geckolib-fabric-1.20.1-4.4.9.jar")
    p.add_argument("--eula", type=Path, default=defaults / "accepted-eula-evidence.txt")
    p.add_argument("--authorize-experimental-data-migration", action="store_true")
    p.add_argument("--known-unresolved-edge-caveat", required=True)
    for name in ("pilot", "full"):
        q = sub.add_parser(name)
        q.add_argument("--timeout-seconds", type=int, default=21600 if name == "full" else 1800)
    args = parser.parse_args()
    if args.action == "prepare":
        prepare(args)
    else:
        need(180 <= args.timeout_seconds <= 43200, "Timeout must be between 180 and 43200 seconds")
        native(args, args.action == "full")


if __name__ == "__main__":
    main()
