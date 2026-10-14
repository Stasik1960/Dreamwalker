"""Scoped v12.7.4 gallery workflow; invoke each native phase under Guard.ps1.

prepare and prepare-author do not start a JVM. build-author, author and reopen
start controlled project JVMs and must be scheduled by the primary agent.
Every runtime/delivery path is new; previous release worlds are read-only.
"""
from pathlib import Path
import argparse
import hashlib
import json
import subprocess
import sys
import traceback
import zipfile
from verify_v1271_final_readiness import ordinary

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_JAR = ROOT / "build/libs/dreamwalker-bb-fabric-1.20.1-0.1.0-v12.7.4-placement-render-fixes.jar"
EULA = Path(r"C:\Users\vakir\.codex\worktrees\3f07\DW\bloodborne complete\build\runtime-server-v127-new-gallery-author2\eula.txt")
AUTHOR_NAME = "dreamwalker-v1274-gallery-author-1.2.7.4.jar"


def read(path):
    return json.loads(path.read_text(encoding="utf-8-sig"))


def sha(path):
    value = hashlib.sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(1 << 20), b""):
            value.update(block)
    return value.hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("mode", choices=("prepare", "prepare-author", "build-author", "author", "finish-author", "reopen"))
    parser.add_argument("--production-jar", type=Path, default=DEFAULT_JAR)
    parser.add_argument("--attempt", default="", help="Optional fresh suffix, for example -r2 (use --attempt=-r2)")
    parser.add_argument("--manual-decorative-optics", action="store_true", help="Mark only new manual source_ exhibits; enable after production optics passes native checks")
    args = parser.parse_args()
    suffix = args.attempt
    assert not suffix or suffix.startswith("-") and all(c.isalnum() or c == "-" for c in suffix)
    jar = ordinary(args.production_jar.absolute())
    assert jar.is_file()
    with zipfile.ZipFile(jar) as archive:
        meta = json.loads(archive.read("fabric.mod.json"))
        assert meta["id"] == "bloodborne_dw" and meta["version"] == "0.1.0-v12.7.4-placement-render-fixes"
    digest = sha(jar)
    stage = ordinary(ROOT / ("build/v1274-gallery" + suffix))
    addon_stage = ordinary(ROOT / ("build/v1274-gallery-addon" + suffix))
    delivery = ordinary(ROOT / ("build/v1274-gallery-delivery" + suffix) / "World")
    run_name = "v1274-gallery-author" + suffix
    reopen_name = "v1274-gallery-reopen" + suffix
    author_world = ROOT / ("build/runtime-server-" + run_name) / "isolated-smoke-world"
    reopen_world = ROOT / ("build/runtime-server-" + reopen_name) / "isolated-smoke-world"
    stage.mkdir(parents=True, exist_ok=True)

    def step(name, tool, *parameters):
        with (stage / (name + ".log")).open("wb") as log:
            code = subprocess.run([sys.executable, str(ROOT / "tools" / tool), *map(str, parameters)],
                                  cwd=ROOT, stdout=log, stderr=subprocess.STDOUT).returncode
        assert code == 0, (name, "process failed", str(stage / (name + ".log")))

    try:
        if args.mode == "prepare":
            assert not (stage / "input.json").exists(), "Fresh plan required; choose a new --attempt"
            step("readiness", "verify_v1274_final_readiness.py", "--production-jar", jar, "--report", stage / "readiness.json")
            assert read(stage / "readiness.json")["status"].startswith("PASS_READ_ONLY")
            step("input", "prepare_gallery_v1274_input.py", "--production-jar", jar, "--output", stage / "input.json", *(["--manual-decorative-optics"] if args.manual_decorative_optics else []))
            plan = read(stage / "input.json")
            assert plan["productionJarSha256"] == digest and len(plan["reviewPlatforms"]) == 24
            assert plan["galleryPreparation"]["sourcePreparationReused"] and not plan["galleryPreparation"]["sourceWorldRescanned"]
        elif args.mode in ("prepare-author", "build-author"):
            options = ["--production-jar", jar, "--output", addon_stage]
            if args.mode == "prepare-author":
                options.append("--prepare-only")
            step(args.mode, "build_gallery_v1274_addon.py", *options)
            report = read(addon_stage / "build-report.json")
            assert report["productionJarSha256"] == digest
            assert report["status"] == ("PASS_PREPARED_REQUIRES_BUILD" if args.mode == "prepare-author" else "PASS")
        elif args.mode in ("author", "finish-author"):
            assert read(stage / "input.json")["productionJarSha256"] == digest
            if args.mode == "author":
                assert read(addon_stage / "build-report.json")["status"] == "PASS"
                assert read(addon_stage / "build-report.json")["productionJarSha256"] == digest
                assert not author_world.parent.exists(), "Fresh author runtime required"
                step("author", "run_gallery_v1271_server_offline.py",
                     "--offline-preflight-report", stage / "bootstrap-author.json",
                     "--jar", jar, "--accepted-eula-file", EULA, "--run-name", run_name,
                     "--report", stage / "author-server.json", "--extra-mod", ROOT / "build/libs" / AUTHOR_NAME,
                     "--gallery-input", stage / "input.json", "--gallery-timeout", 300, "--startup-timeout", 600)
            manifest = author_world.parent / "catalogue-gallery-output.json"
            assert read(manifest)["status"] == "PASS_AUTHORED_REQUIRES_PRODUCTION_REOPEN"
            assert read(manifest)["productionJarSha256"] == digest
            assert not delivery.exists(), "Fresh delivery world required"
            step("finalize", "finalize_gallery_v1274_world.py", "--world", author_world, "--manifest", manifest)
            step("delivery", "prepare_gallery_v1274_delivery.py", "--world", author_world, "--output", delivery)
            step("before-reopen", "verify_gallery_v1274.py", "--world", delivery,
                 "--manifest", delivery / "gallery-manifest.json", "--production-jar", jar,
                 "--report", stage / "before-reopen.json")
            assert read(stage / "before-reopen.json")["singleCentralDoor90986"]["freshSavedOwnerAndHelperGeometryVerifiedByCurrentProductionSpec"]
            step("native-followup-views", "prepare_v1274_followup_views.py", "--gallery-input", stage / "input.json",
                 "--manifest", delivery / "gallery-manifest.json", "--production-jar", jar,
                 "--output", stage / "native-followup-views.json")
        elif args.mode == "reopen":
            assert read(stage / "before-reopen.json")["productionJarSha256"] == digest
            assert not reopen_world.parent.exists(), "Fresh production reopen runtime required"
            step("production-reopen", "run_gallery_v1271_server_offline.py",
                 "--offline-preflight-report", stage / "bootstrap-reopen.json",
                 "--jar", jar, "--accepted-eula-file", EULA, "--run-name", reopen_name,
                 "--report", stage / "production-reopen.json", "--world-copy", delivery, "--startup-timeout", 600)
            step("after-reopen", "verify_gallery_v1274.py", "--world", reopen_world,
                 "--manifest", reopen_world / "gallery-manifest.json", "--production-jar", jar,
                 "--report", stage / "after-reopen.json", "--compare", stage / "before-reopen.json")
            assert read(stage / "after-reopen.json")["productionReopenCompared"]
        assert sha(jar) == digest, "Production JAR changed during gallery operation"
        result = {"status": "PASS_" + args.mode.upper().replace("-", "_"), "productionJarSha256": digest,
                  "stage": str(stage), "deliveryWorld": str(delivery), "previousReleaseWorldsModified": False,
                  "cityPreparationRecreated": False}
        (stage / (args.mode + "-result.json")).write_text(json.dumps(result, indent=2) + "\n", encoding="utf8")
    except BaseException:
        (stage / (args.mode + "-error.txt")).write_text(traceback.format_exc(), encoding="utf8")
        raise


if __name__ == "__main__":
    main()
