"""Compare the four steady cameras and straight diagonal of the Studio build."""
import argparse
import json
from pathlib import Path
import re
from statistics import median

GIB = 1 << 30


def load(path):
    return json.loads(path.read_text(encoding="utf-8"))


def percentile(values, quantile):
    index = (len(values) - 1) * quantile
    low = int(index)
    return values[low] + (values[min(low + 1, len(values) - 1)] - values[low]) * (index - low)


def measured(frames, memory, process, start, end):
    elapsed = [value / 1e6 for value in frames["rawIntervalsNs"]]
    ordered = sorted(elapsed)
    assert len(elapsed) == frames["samples"] and sum(frames["rawIntervalsNs"]) == frames["totalNs"]
    assert elapsed and end > start and memory
    aligned = [record for record in process if start <= record["epochMillis"] <= end]
    assert aligned, "No aligned process memory records"
    return {"fps": 1000 * len(elapsed) / sum(elapsed), "frames": len(elapsed), "p50Ms": percentile(ordered, .5),
            "p95Ms": percentile(ordered, .95), "p99Ms": percentile(ordered, .99), "maxMs": max(elapsed),
            **{f"over{limit}Ms": sum(value > limit for value in elapsed) for limit in (50, 100, 250, 1000)},
            "rssMedianGiB": median(row["workingSetBytes"] for row in aligned) / GIB,
            "rssPeakGiB": max(row["workingSetBytes"] for row in aligned) / GIB,
            "privatePeakGiB": max(row["privateBytes"] for row in aligned) / GIB,
            "heapPeakGiB": max(row["heapUsedBytes"] for row in memory) / GIB,
            "availablePhysicalMedianGiB": median(row["systemAvailablePhysicalBytes"] for row in aligned) / GIB,
            "seconds": (end - start) / 1000}


def run(path):
    report = load(path / "evidence/flight.json")
    recipe = load(path / "recipe.json")
    assert report["status"] == "PASS" and report["correctionCount"] == 0
    assert (path / "exit.txt").read_text().strip() == "0" and len(report["stationary"]) == 4
    assert report["productionSha256"] == recipe["productionSha256"] and report["qaSha256"] == recipe["qaSha256"]
    with (path / "process-memory.jsonl").open(encoding="utf-8") as source:
        process = [json.loads(line) for line in source if line.strip()]
    assert {row["pid"] for row in process} == {report["pid"]}
    summary = {}
    for record in report["stationary"]:
        summary[record["camera"]["name"]] = measured(record["frames"], record["memorySamples"], process,
                                                     record["startEpochMillis"], record["endEpochMillis"])
    summary["diagonal"] = measured(report["frames"], report["memorySamples"], process,
                                   report["flightStartEpochMillis"], report["flightEndEpochMillis"])
    summary["diagonal"]["worst"] = []
    first, last = report["plan"]["points"]
    ticks = report["plan"]["ticksPerLap"]
    indices = sorted(range(report["frames"]["samples"]), key=report["frames"]["rawIntervalsNs"].__getitem__, reverse=True)[:5]
    for index in indices:
        tick = report["frames"]["ticks"][index]
        f = min(1, tick / (ticks - 1))
        summary["diagonal"]["worst"].append({
            "ms": report["frames"]["rawIntervalsNs"][index] / 1e6,
            "seconds": report["frames"]["elapsedNs"][index] / 1e9,
            "xyz": [round(first[axis] * (1 - f) + last[axis] * f, 1) for axis in ("x", "y", "z")],
        })
    logs = (path / "console.log").read_text(encoding="utf-8", errors="replace")
    startup = re.findall(r"Total time to load game and open world was ([0-9.]+) seconds", logs)
    return report, recipe, summary, float(startup[-1]) if startup else None, process[0]["systemAvailablePhysicalBytes"] / GIB


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("baseline", type=Path)
    parser.add_argument("candidate", type=Path)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    before, base_recipe, base, base_start, base_free = run(args.baseline)
    after, candidate_recipe, candidate, candidate_start, candidate_free = run(args.candidate)
    for field in ("actualOptions", "glRenderer", "java", "planSha256"):
        assert before[field] == after[field], field
    excluded = {"dreamwalker.jar"}
    if before["qaSha256"] != after["qaSha256"]:
        assert after.get("restartsInterruptedStages") is True, "Benchmark instrumentation changed"
        excluded.add("benchmark.jar")
    mods = lambda recipe: {file: sha for file, sha in recipe["modHashes"].items() if file not in excluded}
    assert mods(base_recipe) == mods(candidate_recipe), "Nonproduction mod bytes differ"
    payload = {"status": "MEASURED", "settings": after["actualOptions"], "gpu": after["glRenderer"], "java": after["java"],
               "plan": after["plan"], "beforeSha256": before["productionSha256"], "afterSha256": after["productionSha256"],
               "startupSeconds": {"baseline": base_start, "candidate": candidate_start},
               "availableAtLaunchGiB": {"baseline": base_free, "candidate": candidate_free},
               "windowRetries": after.get("windowRetries", 0),
               "sameRecorder": before["qaSha256"] == after["qaSha256"],
               "baseline": base, "candidate": candidate}
    args.output.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")
    for key in base:
        print(f"{key:15} {base[key]['fps']:.2f} -> {candidate[key]['fps']:.2f} FPS, p95 "
              f"{base[key]['p95Ms']:.1f} -> {candidate[key]['p95Ms']:.1f} ms")


if __name__ == "__main__":
    main()
