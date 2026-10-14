"""Check scoped gallery adapters/CLI expansion without starting any JVM."""
from pathlib import Path
import ast
import json
import subprocess
import sys
import traceback

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "build/v1274-gallery-tooling"
TOOLS = ("prepare_gallery_v1274_input.py", "verify_v1274_final_readiness.py", "verify_gallery_v1274.py",
         "finalize_gallery_v1274_world.py", "prepare_gallery_v1274_delivery.py", "build_gallery_v1274_addon.py",
         "run_gallery_v1274_flow.py", "prepare_v1274_followup_views.py")


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    checks = []
    for name in TOOLS:
        path = ROOT / "tools" / name
        ast.parse(path.read_text(encoding="utf8"), filename=str(path))
        result = subprocess.run([sys.executable, str(path), "--help"], cwd=ROOT,
                                stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
        (OUT / (path.stem + "-help.log")).write_bytes(result.stdout)
        assert result.returncode == 0, (name, result.stdout.decode("utf8", errors="replace"))
        checks.append({"tool": name, "syntax": "PASS", "nestedAdapterAndCli": "PASS"})
    report = {"status": "PASS_ALL_V1274_GALLERY_ADAPTERS_AND_CLI_WITHOUT_JVM",
              "jvmStarted": False, "worldsModified": False, "cityPreparationRecreated": False,
              "checks": checks}
    (OUT / "static-validation.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf8")


if __name__ == "__main__":
    try:
        main()
    except BaseException:
        OUT.mkdir(parents=True, exist_ok=True)
        (OUT / "static-validation.json").write_text(json.dumps({"status": "FAIL", "error": traceback.format_exc()}, indent=2) + "\n", encoding="utf8")
        raise
