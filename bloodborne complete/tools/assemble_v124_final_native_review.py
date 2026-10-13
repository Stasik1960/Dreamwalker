"""Bind four completed manual PNG reviews, retaining explicit source exceptions.

This transcribes observed judgments and exact image-byte carry evidence. It never
turns rendered bounds/model metadata or capture completion into a pixel verdict.
"""
import argparse
from collections import defaultdict
import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DOC = ROOT / "docs/v12.4-dedup"
EXPECTED_COUNTS = {"root": 50, "migration": 30, "visual": 45, "stairs": 52}
TRANSPARENT = {("bloodborne_dw:source_addon_floor_panels_empty", 0),
               ("bloodborne_dw:source_assembly_red_stained_glass_pane", 5)}
OPAQUE_ALTERNATES = {("bloodborne_dw:source_assembly_red_stained_glass_pane", 3),
                    ("bloodborne_dw:source_assembly_red_stained_glass_pane", 4)}


def require(condition, message):
    if not condition:
        raise AssertionError(message)


def read(path):
    return json.loads(path.read_text(encoding="utf-8-sig"))


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def state_key(row):
    return row["oldRegistryId"], int(row["oldVariant"])


def view_key(row):
    return (*state_key(row), row.get("viewYawDegrees", 35), row.get("viewPitchDegrees", 25))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--run", type=Path, required=True)
    for reviewer in EXPECTED_COUNTS:
        parser.add_argument("--" + reviewer + "-review", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    run = args.run.resolve()
    require(run.is_relative_to(ROOT / "build"), "Native capture is outside the controlled build directory")
    capture_path = run / "v124-client-comparison-output.json"
    capture = read(capture_path)
    require(capture["status"] == "CAPTURE_COMPLETE" and capture["captureComplete"] is True,
            "Native capture is incomplete")
    capture_sha = sha(capture_path)
    production_sha = sha(ROOT / "build/libs/dreamwalker-bb-fabric-1.20.1-0.1.0-v12.4-dedup.jar")
    require(capture["productionJarSha256"] == production_sha, "Capture is not bound to actual candidate JAR bytes")
    require(capture["pages"] == capture["capturedFrames"] == len(capture["frames"]) == 176,
            "Frozen final review requires all 176 native frames")
    captured = {frame["page"]: frame for frame in capture["frames"]}
    require(set(captured) == set(range(1, 177)), "Native captured page set is incomplete or duplicated")
    by_file = {frame["fileName"]: frame for frame in capture["frames"]}
    for frame in capture["frames"]:
        path = run / "screenshots" / frame["fileName"]
        require(sha(path) == frame["sha256"] and path.stat().st_size == frame["bytes"],
                f"Actual native PNG bytes differ: {frame['page']}")
    reviews, reports, coverage = {}, [], defaultdict(list)
    for reviewer, expected_count in EXPECTED_COUNTS.items():
        path = getattr(args, reviewer + "_review")
        report = read(path)
        reviews[reviewer] = report
        require(report["status"].startswith("PASS_"), f"{reviewer} review has not passed its assigned scope")
        require(report["productionJarSha256"] == production_sha and report["captureSha256"] == capture_sha,
                f"{reviewer} report belongs to a different JAR/capture")
        if report.get("rawGameDirectory"):
            require(Path(report["rawGameDirectory"]).resolve() == run, f"{reviewer} names a different actual native run")
        require(not report.get("visibleFailures") and not report.get("failures"), f"{reviewer} has visible failures")
        frames = report["frames"]
        require(report["reviewedFrames"] == len(frames) == expected_count, f"{reviewer} assigned frame count differs")
        require(len({frame["page"] for frame in frames}) == len(frames), f"{reviewer} repeats a page")
        for frame in frames:
            page = frame["page"]
            require(page in captured, f"{reviewer} has an unexpected page")
            actual = captured[page]
            require(frame["fileName"] == actual["fileName"] and frame["sha256"] == actual["sha256"],
                    f"{reviewer} observed PNG differs from actual native capture: page{page}")
            require(not frame.get("flags"), f"{reviewer} has unresolved frame flags: page{page}")
            if reviewer == "stairs":
                require(frame["status"] == "PASS_OBSERVED" and frame["pairsActuallyReviewed"] == len(actual["pairs"]),
                        f"Stair reviewer did not observe all captured pairs on page{page}")
                require(frame["reviewProvenance"] in {"ACTUAL_ORIGINAL_FINAL2_PNG_OPENED",
                        "EXACT_ORIGINAL_PNG_BYTES_OF_ACTUALLY_REVIEWED_C30_FRAME"}, "Unknown stair observation provenance")
            else:
                require(frame.get("manualVisualInspection") is True, f"{reviewer} frame is not an observed judgment")
                if reviewer != "visual":
                    require(frame["status"] == "PASS_OBSERVED_PIXELS", f"{reviewer} page is not an observed pixel PASS")
            coverage[page].append({"reviewer": reviewer, "reportSha256": sha(path),
                                   "scope": frame.get("scope", "Entire unmodified screenshot")})
        reports.append({"reviewer": reviewer, "path": str(path.resolve()), "sha256": sha(path),
                        "status": report["status"], "reviewedFrames": len(frames)})
    require(set(coverage) == set(captured), "Observed manual report union does not cover every native page")
    require(set(reviews["root"]["frames"][i]["page"] for i in range(50)) == set(range(1,26)) | set(range(56,81)),
            "Root scope differs")
    require({frame["page"] for frame in reviews["migration"]["frames"]} == set(range(26,56)), "Migration scope differs")
    require({frame["page"] for frame in reviews["visual"]["frames"]} == set(range(108,152)) | {176}, "Visual scope differs")
    require({frame["page"] for frame in reviews["stairs"]["frames"]} == set(range(81,108)) | set(range(152,177)), "Stair scope differs")
    stairs = reviews["stairs"]
    require(stairs["alternateOneSidedModelsVisible"] is True and stairs["alternateSourceModelsVisible"] is True
            and reviews["visual"]["alternateSourceModelsVisible"] is True, "Observed alternate surfaces are incomplete")
    views, models = stairs["alternateViewVisibility"], stairs["alternateModelVisibility"]
    exceptions, transparent_views = stairs["sourceVisibilityExceptions"], stairs["transparentViewVisibility"]
    require(len(views) == 4 and {state_key(row) for row in views} == OPAQUE_ALTERNATES, "Opaque alternate views differ")
    require(len(models) == 2 and {state_key(row) for row in models} == OPAQUE_ALTERNATES, "Opaque visible model judgments differ")
    require(len(exceptions) == 2 and {state_key(row) for row in exceptions} == TRANSPARENT, "Source transparency exceptions differ")
    require(len(transparent_views) == 4 and {state_key(row) for row in transparent_views} == TRANSPARENT,
            "Observed transparent alternate views differ")
    for row in views + models + transparent_views:
        frame = by_file[row["fileName"]]
        require(row["sha256"] == frame["sha256"], "Alternate observation PNG digest differs")
        require(any(actual.get("kind") == "MIGRATION_ALT_VIEW" and view_key(actual) == view_key(row)
                    for actual in frame["pairs"]), "Alternate observation is not an actually captured pose")
    for row in views:
        require(row["status"] in {"VISIBLE_SOURCE_MATCH", "CULLED_BACK_FACE"}, "Unresolved opaque alternate view")
    for row in models:
        require(row["status"] == "VISIBLE_SOURCE_MATCH" and any(view_key(view) == view_key(row)
                and view["status"] == "VISIBLE_SOURCE_MATCH" for view in views), "Opaque model lacks an observed visible view")
    for row in exceptions + transparent_views:
        require(row["status"] == "SOURCE_FULLY_TRANSPARENT_STATE_PRESERVED"
                and row["visualVisibility"] == "NOT_APPLICABLE_FULLY_TRANSPARENT_SOURCE", "Blank source visibility was declared PASS")
    proofs = stairs["sourceProofDigests"]
    proof_paths = {row["path"]: row for row in proofs}
    require({"one-sided-source-alpha-audit.json", "transparent-93223-v5-source-proof.json"} <= set(proof_paths),
            "Source alpha proofs missing from observed report")
    for proof in proofs:
        path = DOC / proof["path"]
        require(path.resolve().is_relative_to(DOC) and sha(path) == proof["sha256"], "Source proof bytes differ")
    alpha = read(DOC / "one-sided-source-alpha-audit.json")
    floor_faces = [row for row in alpha["faces"] if row["id"] == "90141" and row["variant"] == 0]
    require(not alpha["sourceFilesModified"] and floor_faces and all(row["pixels"] > 0 and row["nonzeroAlphaPixels"] == 0
            for row in floor_faces), "Floor source alpha exception is not independently supported")
    pane = read(DOC / "transparent-93223-v5-source-proof.json")
    require(pane["productionJarSha256"] == production_sha and pane["status"] == "PASS_SOURCE_FULLY_TRANSPARENT_STATE_PRESERVED",
            "Pane source alpha proof does not bind the candidate")
    require(pane["oldId"] == "93223" and pane["oldVariant"] == 5 and pane["mainId"] == "93214" and pane["variant"] == 12
            and pane["faceOccurrences"] == 22 and pane["alphaSamples"] == 1144 and pane["nonzeroAlphaSamples"] == 0
            and pane["sourceDescriptorMatchesFrozenBaseline"] and pane["canonicalPartsExactlyEqualSourceParts"]
            and not pane["sourceFilesModified"], "Pane source exception data differs")
    pending = []
    for row in reviews["root"]["rows"]:
        if row["status"].startswith("PASS"):
            continue
        require(row["status"] == "PENDING_ALTERNATE_VIEW" and state_key(row) in TRANSPARENT | OPAQUE_ALTERNATES,
                "Root has an unresolved observed failure or an unknown pending model")
        pending.append({**row, "resolution": "SOURCE_FULLY_TRANSPARENT_STATE_PRESERVED" if state_key(row) in TRANSPARENT
                        else "VISIBLE_SOURCE_MATCH_IN_REVIEWED_ALTERNATE_VIEW"})
    require({state_key(row) for row in pending} == TRANSPARENT | OPAQUE_ALTERNATES, "Expected default-view pending states differ")
    require(all(row["status"].startswith("PASS") for row in reviews["visual"]["rows"]), "Visual report has unresolved rows")
    output = {"schema": "dreamwalker-v124-complete-observed-native-pixel-review-v1", "status": "PASS",
        "productionJarSha256": production_sha, "captureSha256": capture_sha, "rawGameDirectory": str(run),
        "reviewedFrames": len(coverage), "capturedPairs": capture["pairCount"],
        "visibleFailures": [], "unresolvedDefaultViewStates": [], "resolvedDefaultViewStates": pending,
        "method": "Four assigned original-PNG observation reports, with explicit exact-byte carry where declared. No pixel judgment inferred from capture/model metadata.",
        "alternateOneSidedModelsVisible": stairs["alternateOneSidedModelsVisible"],
        "alternateSourceModelsVisible": reviews["visual"]["alternateSourceModelsVisible"],
        "alternateViewVisibility": views, "alternateModelVisibility": models,
        "transparentViewVisibility": transparent_views, "sourceVisibilityExceptions": exceptions,
        "sourceProofDigests": proofs, "alphaProofDigests": [proof_paths[name] for name in
            ("one-sided-source-alpha-audit.json", "transparent-93223-v5-source-proof.json")],
        "assignedReports": reports,
        "frames": [{"page": page, "fileName": captured[page]["fileName"], "sha256": captured[page]["sha256"],
                    "status": "OBSERVED_JUDGMENT_WITH_EXPLICIT_SOURCE_VISIBILITY_EXCEPTIONS", "reviewers": coverage[page]}
                   for page in sorted(coverage)],
        "scopeLimit": "Pixel observations and original-source alpha exceptions only; build, saves, gallery and physical input require separate evidence."}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(output, ensure_ascii=False, indent=2) + "\n", encoding="utf8")
    print(json.dumps({"status": output["status"], "reviewedFrames": len(coverage),
                      "resolvedDefaultViewStates": len(pending), "sourceVisibilityExceptions": len(exceptions)}))


if __name__ == "__main__":
    main()
