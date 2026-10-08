"""Qualify early FULL failure without fabricating completed geometry/GUI/shader proof."""
from __future__ import annotations
import argparse
import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(path):
    path = Path(path)
    if not path.is_absolute(): path = ROOT / path
    raw = path.read_bytes()
    if len(raw) > 8 * 1024 * 1024: raise ValueError("Bounded early-client JSON required")
    return json.loads(raw.decode("utf-8-sig")), {"path": path.relative_to(ROOT).as_posix(), "sha256": hashlib.sha256(raw).hexdigest(), "bytes": len(raw)}


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--wrapper", required=True)
    p.add_argument("--artifact-sha", required=True)
    p.add_argument("--report", required=True)
    args = p.parse_args()
    output = ROOT / args.report
    if output.exists(): raise ValueError("Never overwrite historical actual evidence")
    wrapper, reference = read(args.wrapper)
    raw, raw_reference = read(wrapper["client_review_output"]["path"])
    checks = [wrapper["status"] == "FAIL_CLIENT_REVIEW", wrapper["exit_code"] == 0,
              wrapper["integrated_save_messages_present"], wrapper["artifact_sha256"] == raw["productionJarSha256"] == args.artifact_sha,
              raw == wrapper["client_review_output"]["result"], raw_reference["sha256"] == wrapper["client_review_output"]["sha256"],
              raw["status"] == "FAIL_ACTUAL_CLIENT_V10", raw["phase"] == 0,
              raw["failure"] == "Old static root attempted to emit while BER offset active",
              not raw["ordinaryRpCases"], not raw["ordinaryArchitectureCases"],
              "menusActual" not in raw, "diagnosticsActualClient" not in raw]
    if not all(checks): raise ValueError("Actual early-failure scope/binding differs")
    initial = raw["irisRuntime"]["initial"]
    if not (initial["currentPackName"] == "Kappa_v5.2.zip" and initial["packPresent"] and initial["shadersEnabled"]
            and not initial["fallback"] and initial["shaderMapPresent"]
            and initial["pipelineClass"] == "net.irisshaders.iris.pipeline.IrisRenderingPipeline"):
        raise ValueError("Initial observed active Iris pipeline identity differs")
    frames = raw["failureFrames"]
    if not any("$Proxy" in frame and ".pushTransform" in frame for frame in frames) or not any("continuity.client.model.EmissiveBakedModel.emitBlockQuads" in frame for frame in frames):
        raise ValueError("Actual Continuity render-context bookkeeping failure witness missing")
    report = {
        "schema": "dw-v10-full-stage0-failure-audit-v1",
        "status": "OBSERVED_CURRENT_FULL_SCENE_JOIN_AND_INITIAL_IRIS_PIPELINE_QA_STAGE0_FAILURE_WHOLE_CLIENT_FAIL",
        "artifactSha256": args.artifact_sha, "wrapper": reference, "raw": raw_reference,
        "wholeClientStatus": wrapper["status"], "rawClientStatus": raw["status"],
        "normalExitCode": wrapper["exit_code"], "elapsedSeconds": wrapper["elapsed_seconds"],
        "normalMinecraftShutdownSaveMessagesObserved": True, "wholeQaFinalSaveAssertions": "NOT_RUN",
        "firstActualJoinedPlayerSnapshot": raw["firstEntrySnapshot"],
        "initialIrisObserved": initial,
        "strictFinalShaderGetter42CounterProof": "NOT_RUN; initial active pipeline snapshot does not replace final expected-option and advanced-frame verification",
        "failureBoundary": {"phase": raw["phase"], "failure": raw["failure"], "frames": frames, "scope": "QA render-context proxy rejects Continuity pushTransform bookkeeping before geometry result is written. This early exception does not prove that the underlying offset model emitted a quad."},
        "ordinaryRpCompleted": 0, "ordinaryArchitectureCompleted": 0,
        "notCompleted": ["architecture baked-geometry result", "69+7 actual middle-key cases", "ordinary picked-item reinstallation", "237 actual GUI operations", "client diagnostics OFF/ON/OFF/export", "final whole-QA save", "manual visual acceptance"],
        "loadingObservationScope": "This exact current main8 FULL run reached a real joined integrated-server player and normal shutdown. This is narrower than whole ordinary client PASS and does not replace future FULL2 proof.",
        "wholeTask": "NOT_COMPLETE",
    }
    output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"status": report["status"], "report": str(output), "wholeClientStatus": report["wholeClientStatus"]}))


if __name__ == "__main__": main()
