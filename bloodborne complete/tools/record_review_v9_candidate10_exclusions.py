"""Preserve the observed reasons candidate10 is superseded; no game mutations."""
import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def record(path):
    data = path.read_bytes()
    return {"path": str(path), "bytes": len(data), "sha256": hashlib.sha256(data).hexdigest()}


def main():
    output = ROOT / "reports/REVIEW_V9_CANDIDATE10_EXCLUSIONS.json"
    if output.exists():
        raise ValueError("Never overwrite historical exclusions")
    runs = []
    for number, reason, pid in [
        (7, "Heap4GB reached99% with prolonged pauses; owned process stopped. No normal exit or successful complete client QA. Cause attribution to a particular mod is NOT_ESTABLISHED.", 95644),
        (8, "Owned isolated process stopped after discovery of corrupt human-readable ZIP summary in candidate10. Superseded artifact, not a completed compatibility or memory result.", 41088),
    ]:
        path = ROOT / f"reports/CLIENT_FULL_KAPPA_V9_RELEASE{number}.json"
        wrapper = json.loads(path.read_text(encoding="utf8"))
        log = Path(wrapper["run_directory"]) / "launch-console.log"
        text = log.read_text(encoding="utf8", errors="replace")
        runs.append({"wrapper": record(path), "console": record(log), "actualStatus": wrapper["status"], "actualExitCode": wrapper.get("exit_code"), "ownedProcessId": pid,
                     "heapArgument": [value for value in wrapper["command"] if value.startswith("-Xmx")], "reason": reason,
                     "memoryWarningSamples": [line for line in text.splitlines() if "99%" in line or "4082/4096" in line][:12],
                     "selectedArtifactSha256": wrapper["artifact_sha256"], "positiveReleaseProof": False})
    result = {"schema": "dreamwalker-v9-superseded-candidate-exclusions-v1", "status": "HISTORICAL_OBSERVATIONS_NOT_CURRENT_RELEASE_PROOF",
              "supersededArtifactSha256": runs[0]["selectedArtifactSha256"], "runs": runs,
              "productionDefect": "DwDiagnostics.summary() contains corrupted UTF8/Windows1251-looking Russian literals. Confirmed in actual server ZIP summary.md. Requires readable-summary correction, new SHA, and new actual runtime proofs.",
              "oldDiagnosticIndependentReport": "reports/REVIEW_V9_DIAGNOSTICS_INDEPENDENT_RELEASE7.json validated functional JSON/ZIP/world evidence but did not reject unreadable summary. Its old PASS does not satisfy the corrected human-summary gate.",
              "existingOriginalInstallationsAndWorldsModified": False, "manualVisualAcceptance": "PENDING_USER_REVIEW", "fullTaskStatus": "NOT_READY_FULL_TASK"}
    output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf8")
    print(json.dumps({"status": result["status"], "report": str(output)}))


if __name__ == "__main__":
    main()
