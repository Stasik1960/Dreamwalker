"""Bind observed pages26–55; carry only byte-identical original native PNGs.

--freshly-reviewed is evidence transcription after actual view_image inspection,
not a request for this script to decide whether changed images look correct.
"""
import argparse
import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PAGES = set(range(26, 56))


def read(path):
    return json.loads(path.read_text(encoding="utf-8-sig"))


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--run", type=Path, required=True)
    parser.add_argument("--prior-review", type=Path, required=True)
    parser.add_argument("--freshly-reviewed", default="")
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    run = args.run.resolve()
    assert run.is_relative_to(ROOT / "build")
    capture_path = run / "v124-client-comparison-output.json"
    capture = read(capture_path)
    assert capture["status"] == "CAPTURE_COMPLETE" and capture["captureComplete"] is True
    actual_jar = ROOT / "build/libs/dreamwalker-bb-fabric-1.20.1-0.1.0-v12.4-dedup.jar"
    assert sha(actual_jar) == capture["productionJarSha256"]
    prior = read(args.prior_review)
    assert prior["status"] == "PASS_OBSERVED" and prior["unexpectedFailureCount"] == 0
    assert prior["pagesVisited"] == 30 and prior["pairsReviewed"] == 360
    observed = {page["page"]: page for page in prior["pages"]}
    assert set(observed) == PAGES
    fresh = {int(value) for value in args.freshly_reviewed.split(",") if value}
    assert fresh <= PAGES
    frames, pending = [], []
    for frame in capture["frames"]:
        page = frame["page"]
        if page not in PAGES:
            continue
        path = run / "screenshots" / frame["fileName"]
        assert sha(path) == frame["sha256"] and path.stat().st_size == frame["bytes"]
        assert path.read_bytes()[:8] == b"\x89PNG\r\n\x1a\n"
        assert len(frame["pairs"]) == observed[page]["pairsReviewed"] == 12
        assert all(row["nativeModelsPresent"] and row["sameCentreScaleView"] for row in frame["pairs"])
        identical = frame["sha256"] == observed[page]["sha256"]
        assert observed[page]["status"] == "PASS_OBSERVED" and not observed[page]["flags"]
        passed = identical or page in fresh
        if not passed:
            pending.append(page)
        frames.append({"page": page, "fileName": frame["fileName"], "sha256": frame["sha256"],
            "bytes": frame["bytes"], "width": frame["width"], "height": frame["height"],
            "status": "PASS_OBSERVED_PIXELS" if passed else "NOT_REVIEWED_CHANGED_PIXELS",
            "pairsReviewed": 12 if passed else 0,
            "manualVisualInspection": passed,
            "method": "Fresh full original PNG view_image inspection" if page in fresh else
                      "Exact PNG byte identity to previously observed original C30 image" if identical else
                      "Changed PNG requires fresh visual inspection",
            "priorReviewSha256": sha(args.prior_review), "priorObservedPngSha256": observed[page]["sha256"],
            "notes": observed[page]["notes"] if passed else "", "flags": []})
    assert {frame["page"] for frame in frames} == PAGES
    frames.sort(key=lambda frame: frame["page"])
    result = {"schema": "dreamwalker-v124-migration-range-native-review-v1",
        "status": "PASS_ASSIGNED_PIXELS" if not pending else "PENDING_CHANGED_IMAGE_REVIEW",
        "productionJarSha256": capture["productionJarSha256"], "captureSha256": sha(capture_path),
        "rawGameDirectory": str(run), "range": [26, 55], "reviewedFrames": 30 - len(pending),
        "pairsReviewed": sum(frame["pairsReviewed"] for frame in frames),
        "byteIdenticalFramesCarried": sum(frame["method"].startswith("Exact PNG") for frame in frames),
        "freshlyReviewedFrames": sorted(fresh), "pendingChangedFrames": pending,
        "visibleFailures": [], "unobservedPairCount": 12 * len(pending),
        "method": "Original native Minecraft PNGs only; changed images never inherit a pixel verdict.",
        "scope": "Assigned pages26–55; global/client/save acceptance remains separate.", "frames": frames}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf8")
    print(json.dumps({key: result[key] for key in ("status", "reviewedFrames", "pairsReviewed", "pendingChangedFrames")}))


if __name__ == "__main__":
    main()
