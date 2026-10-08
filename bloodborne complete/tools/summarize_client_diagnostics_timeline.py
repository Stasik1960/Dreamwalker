"""Summarize a verified completed client session without loading Minecraft/worlds.

This is descriptive evidence. It does not attribute GPU duration, unique CPU cost,
or a causal FPS change to an object or to the recorder.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import math
from pathlib import Path
import zipfile


def identity(path: Path) -> dict:
    raw = path.read_bytes()
    return {"path": str(path.resolve()), "bytes": len(raw),
            "sha256": hashlib.sha256(raw).hexdigest()}


def read_zip(reference: dict, names: list[str]) -> dict:
    path = Path(reference["path"])
    actual = identity(path)
    if any(actual[key] != reference[key] for key in ("bytes", "sha256")):
        raise ValueError("Verified export bytes changed: " + path.name)
    with zipfile.ZipFile(path) as archive:
        return {name: json.loads(archive.read(name)) for name in names}


def quantile(values: list[int], fraction: float) -> int | None:
    return sorted(values)[max(0, math.ceil(len(values) * fraction) - 1)] if values else None


def grouped_frames(rows: list[dict], label: str) -> dict:
    count = sum(row["frames"]["measurements"] for row in rows)
    total = sum(row["frames"]["totalNs"] for row in rows)
    return {"label": label, "batch_indices": [row["batch"] for row in rows],
            "frames_observed": count, "presentation_total_ns": total,
            "presentation_mean_ns": total / count if count else "NOT_MEASURED",
            "reciprocal_mean_fps": count * 1_000_000_000 / total if total else "NOT_MEASURED",
            "scope": "Nonoverlapping active-session batch frame populations; this grouping is not a matched OFF/ON comparison."}


def summarize(proof_path: Path) -> dict:
    proof = json.loads(proof_path.read_text(encoding="utf-8"))
    if proof.get("status") != "PASS_LINKED_CURRENT_CLIENT_AND_INTEGRATED_SERVER_EXPORT_BYTES":
        raise ValueError("Requires an actual successful linked-export verifier report")
    if proof.get("client_timing_retention", {}).get("status") != "PASS_CURRENT_ACCEPTED_WIRE_AND_FULL_BOUNDED_LOCAL_SAMPLED_TIMINGS":
        raise ValueError("Requires actual new-budget wire/local timing retention proof")
    client = read_zip(proof["client_export"], ["runtime.json", "summary.json", "client-batches.json", "local-timings.json", "partial-window.json"])
    server = read_zip(proof["server_export"], ["environment.json", "session.json"])
    runtime, summary = client["runtime.json"], client["summary.json"]
    batches, local = client["client-batches.json"], client["local-timings.json"]
    session = proof["session"]
    sha = proof["production_jar_sha256"]
    if runtime.get("productionArtifactSha256") != sha or summary.get("session") != session or local.get("session") != session:
        raise ValueError("Actual session/artifact changed")
    if server["environment.json"].get("processId") != runtime["processId"] or any(row["memory"].get("jvmPid") != runtime["processId"] for row in batches):
        raise ValueError("Integrated client/server exports do not share the actual process PID")
    timeline = []
    flush_samples = []
    final_flush = summary["flushOverheadNs"]
    for i, batch in enumerate(batches):
        before = batch["diagnosticFlushOverheadNsCumulative"]
        after = batches[i + 1]["diagnosticFlushOverheadNsCumulative"] if i + 1 < len(batches) else final_flush
        if after < before:
            raise ValueError("Per-session flush counter decreased")
        flush_samples.append(after - before)
        budget = batch["wireBudget"]
        f = batch["frames"]
        row = {"batch": i + 1, "timestamp_utc": batch["timestampUtc"], "window_ns": batch["windowNs"],
               "frames": {key: f.get(key, "NOT_MEASURED") for key in ("measurements", "totalNs", "averageNs", "medianNs", "p95Ns", "p99Ns")},
               "wire_timing_groups": len(batch["timings"]), "local_timing_groups": budget["timings"]["availableRows"],
               "wire_sampled_invocations_retained": budget["timings"]["retainedSampledInvocations"],
               "wire_sampled_invocations_omitted": budget["timings"]["omittedSampledInvocations"],
               "flush_cumulative_ns_before_this_flush": before,
               "this_flush_elapsed_ns_from_next_counter_or_final_export": after - before,
               "gc_collector_count_delta": batch["memory"]["gcCountDelta"],
               "gc_collector_aggregate_ms_delta": batch["memory"]["gcAggregateMillisDelta"],
               "jfr_gc_phase_pause_events": batch["gcStopTheWorldPauseDurations"]["measurements"],
               "jfr_gc_phase_pause_total_ns": batch["gcStopTheWorldPauseDurations"]["totalNs"],
               "heap_used_bytes": batch["memory"]["heapUsedBytes"]}
        timeline.append(row)
    sections = [{key: row[key] for key in ("typeChunkSection", "measurements", "totalNs", "retainedSamples", "droppedSamples", "averageNs", "medianNs", "p95Ns", "p99Ns")}
                for row in local["sessionSampledTimings"]]
    return {
        "schema": "dreamwalker-completed-client-diagnostics-timeline-v1",
        "status": "DESCRIPTIVE_ACTUAL_COMPLETED_RUNTIME",
        "production_artifact_sha256": sha, "session_id": session, "evidence": identity(proof_path),
        "client_export": proof["client_export"], "integrated_server_export": proof["server_export"],
        "process": {"client_pid": runtime["processId"], "integrated_server_pid": server["environment.json"].get("processId"),
                    "scope": "Same JVM process: client/server heap, GC and process CPU must not be added together."},
        "matched_sequential_windows": proof["timings"],
        "active_batch_groups": [grouped_frames(timeline[1:10], "COLD_BATCHES_2_TO_10"), grouped_frames(timeline[-10:], "LATE_ACTIVE_LAST_10_BATCHES")],
        "batch_timeline": timeline,
        "own_batch_flush": {"scope": "Synchronous sendBatch elapsed wall intervals, including metadata access and serialization; NOT unique CPU or GPU.",
                            "all_count": len(flush_samples), "all_total_ns": final_flush,
                            "all_mean_ns": sum(flush_samples) / len(flush_samples), "p95_ns": quantile(flush_samples, .95),
                            "p99_ns": quantile(flush_samples, .99), "max_ns": max(flush_samples),
                            "sum_reconciles_final_per_session_counter": sum(flush_samples) == final_flush},
        "io_worker_cpu": {"ns": summary["ioThreadCpuNsProcessCumulative"],
                          "scope": "Actual IO worker thread CPU since process start; contains metadata/export/JFR close work. NOT per-session flush CPU."},
        "sample_retention": proof["client_timing_retention"], "session_sampled_sections": sections,
        "gc": {"scope": "Actual shared-PID deltas across client batch windows only. Collector-cycle duration and JFR pause-event intervals describe overlapping work and must not be added.",
               "collector_count_delta_sum": sum(row["gc_collector_count_delta"] for row in timeline),
               "collector_aggregate_ms_delta_sum": sum(row["gc_collector_aggregate_ms_delta"] for row in timeline),
               "jfr_phase_pause_events_sum": sum(row["jfr_gc_phase_pause_events"] for row in timeline),
               "jfr_phase_pause_elapsed_ns_sum": sum(row["jfr_gc_phase_pause_total_ns"] for row in timeline),
               "partial_tail_jfr_phase_pause_events": client["partial-window.json"]["gcStopTheWorldPauseDurations"]["measurements"],
               "partial_tail_jfr_phase_pause_elapsed_ns": client["partial-window.json"]["gcStopTheWorldPauseDurations"]["totalNs"]},
        "limitations": ["Cold sequential OFF/ON/OFF and later active recovery do not establish causal diagnostics overhead.",
                        "No GPU duration, all-mod network bytes, per-object unique CPU share or mass-city performance was measured.",
                        "Sampled/nested render and animation intervals overlap; window and session views describe the same invocations.",
                        "Zero raw thread-CPU deltas can reflect JVM clock granularity; positive frame-presentation intervals are a separate population."]}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--proof", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    result = summarize(args.proof)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"status": result["status"], "output": str(args.output),
                      "session": result["session_id"], "own_batch_flush": result["own_batch_flush"]}))


if __name__ == "__main__":
    main()
