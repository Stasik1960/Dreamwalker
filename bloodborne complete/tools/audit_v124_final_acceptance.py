"""Independent read-only gate after acceptance, before release packaging."""
import argparse
import hashlib
import json
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
DOC = ROOT / "docs/v12.4-dedup"
EXPECTED_SHA = "4c9e202cff6ae5acd17386a30547726948a0d0d9babf4e529c84ec5c51b0c640"


def require(condition, message):
    if not condition:
        raise AssertionError(message)


def sha(path):
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def read(path):
    return json.loads(path.read_text(encoding="utf-8-sig"))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=DOC / "final-independent-acceptance-audit.json")
    args = parser.parse_args()
    result = {"schema": "dreamwalker-v124-independent-final-acceptance-gate-v1", "status": "FAIL",
              "readOnlyValidation": True, "javaLaunched": False, "acceptanceModified": False}
    try:
        acceptance = read(DOC / "acceptance.json")
        jar = ROOT / "build/libs/dreamwalker-bb-fabric-1.20.1-0.1.0-v12.4-dedup.jar"
        require(sha(jar) == acceptance["productionJarSha256"] == EXPECTED_SHA and acceptance["status"] == "PASS",
                "Acceptance is not bound to actual frozen final4c9e JAR")
        for key in ("build", "gameTests", "nativeOldSaveMigration", "nativeSaveReopen", "nativeVisualReview",
                    "galleryServerReopen", "galleryClientOpenAndSave", "gallerySavedObjects", "preservation", "resources"):
            require(acceptance[key] == "PASS", "Acceptance phase is incomplete: " + key)
        evidence = {}
        for name, row in acceptance["evidence"].items():
            path = Path(row["path"])
            require(sha(path) == row["sha256"], "Accepted evidence bytes changed: " + name)
            evidence[name] = read(path)
        xml_path = ROOT / acceptance["gameTestXml"]
        xml = ET.parse(xml_path).getroot()
        require(len(xml.findall(".//testcase")) == acceptance["gameTestCount"] == 72 and not xml.findall(".//failure")
                and not xml.findall(".//error") and not xml.findall(".//skipped"), "Actual accepted XML does not contain72 passing cases")
        tables = read(DOC / "readable-report-roundtrip-final.json")
        require(tables["status"] == "PASS" and tables["productionJarSha256"] == EXPECTED_SHA
                and tables["mappedStateRows"] == 1281 and tables["mergedGroupRows"] == 219
                and tables["finiteSourceShiftVectors"] == 1281 and tables["yawStateSourceShiftMismatches"] == 0,
                "Readable state/group CSV roundtrip is incomplete")
        for name, digest in tables["bindings"].items():
            require(sha(DOC / name) == digest, "Readable table/native binding bytes changed: " + name)
        require(tables["actualGameTestXmlSha256"] == sha(xml_path), "Readable/native proof used different actual GameTests")
        require(tables["originalV12RowsUnchanged"] == 116 and tables["reservedIdsUnchanged"] == 1322,
                "Protected V12 rows/ID reservations are incomplete")
        counts = read(DOC / "type-counts.json")
        require(acceptance["counts"] == counts and counts["newWorkingTypesBefore"] == 1045
                and counts["newWorkingTypesAfter"] == counts["newCreativeStacksAfter"] == 572
                and counts["retiredWorkingIds"] == 473 and counts["reservedIdsAfter"] == 1322,
                "Final working catalogue counts differ")
        require(evidence["nativeSaveProfiles"]["status"] == "PASS" and evidence["nativeSaveProfiles"]["productionJarSha256"] == EXPECTED_SHA,
                "Independent saved-state native proof is incomplete")
        require(evidence["migration"]["verifiedFixtures"] == evidence["reopen"]["verifiedFixtures"] == 1281
                and evidence["migration"]["ordinaryInteractions"] == 1281, "Final genuine saved/item coverage is incomplete")
        visual = evidence["visual"]
        require(visual["status"] == "PASS" and visual["productionJarSha256"] == EXPECTED_SHA and visual["reviewedFrames"] == 176
                and visual["mappedOldStatesRendered"] == 1281 and visual["allFrozenNewStatesRendered"] == 1802
                and visual["newFunctionalStairStatesRendered"] == 200 and visual["retainedPairReviews"] == 91,
                "Complete observed native pixel coverage is incomplete")
        require(len(visual["sourceVisibilityExceptions"]) == 2 and all(row["visualVisibility"] == "NOT_APPLICABLE_FULLY_TRANSPARENT_SOURCE"
                for row in visual["sourceVisibilityExceptions"]), "Transparent sources were silently promoted to visible PASS")
        server, client, disk, gallery_pixels = (evidence[key] for key in ("gallery_server", "gallery_client", "gallery_disk", "gallery_review"))
        require(server["status"] == client["status"] == disk["status"] == gallery_pixels["status"] == "PASS"
                and server["artifact_sha256"] == client["productionJarSha256"] == disk["productionJarSha256"]
                == gallery_pixels["productionJarSha256"] == EXPECTED_SHA and not server["extraMods"], "Gallery phases do not bind production-only final JAR")
        require(client["verifiedSavedObjects"] == disk["verifiedExhibits"] == 675
                and client["nativeFramebufferImages"] == gallery_pixels["reviewedFrames"] == 48
                and disk["productionReopenCompared"] is True and disk["functionalCatalogue"]["status"] == "PASS",
                "Gallery675 stations/48 observed frames/saved roundtrip incomplete")
        world = Path(acceptance["galleryWorld"])
        manifest = read(world / "gallery-manifest.json")
        require(manifest["productionJarSha256"] == EXPECTED_SHA and len(manifest["exhibits"]) == 675
                and len(manifest["removedRedExhibits"]) == 60 and not any(row["section"] == "error" for row in manifest["exhibits"]),
                "Final delivery world manifest differs from gallery acceptance")
        current = manifest["currentFunctionalCatalogueProvenance"]
        require(current["sourceCommit"] == acceptance["sourceCommit"] and current["baselineCommit"] == acceptance["baselineCommit"]
                and current["removedPreviousRedStations"] == 60 and current["diagnosticStations"] == 0, "Current gallery provenance/policy differs")
        dependencies = [{key: row[key] for key in ("id", "version", "sha256")} for row in server["modset"]]
        require(dependencies == acceptance["verifiedDependencies"] and not any("qa" in row["id"].lower() for row in dependencies),
                "Accepted dependencies differ from actual production-only gallery server")
        references = evidence["referenceInputs"]
        require(references["status"] == "PASS" and references["sourceMapChanged"] is False and len(references["files"]) == 2,
                "Original source preservation report incomplete")
        reference_hashes = {"bbmc_v15_resource.zip": "29b31e3744af5794ae95ebe4c5474e3b70dfb08c143355bce815c08ceef09a36",
                            "bbmc_v16_map.zip": "39e83f2941b768fd2ae9f84d8f57dc76aec78bd926dc5e38eab3059251d2bb08"}
        for row in references["files"]:
            path = Path(row["file"])
            require(path.name in reference_hashes and row["sha256"] == sha(path) == reference_hashes[path.name]
                    and row["bytes"] == path.stat().st_size, "Supplied original resource pack/map bytes changed")
        require(acceptance["nativePendingChunkOrdering"] == "NOT_RUN_NO_UNLOADED_CROSS_CHUNK_SAVED_ASSEMBLY_AFTER_STARTUP"
                and acceptance["physicalMouseKeyboard"] == acceptance["frameRateBenchmark"] == acceptance["onlineAccountAuthentication"] == "NOT_RUN"
                and acceptance["authorCollisionExport"] == "NOT_SUPPLIED_NOT_APPLIED" and acceptance["originalMapChanged"] is False,
                "Acceptance omits or overstates actual testing limits")
        report = (DOC / "REPORT-RU.txt").read_text(encoding="utf-8-sig")
        require(EXPECTED_SHA in report and "72 серверных GameTests" in report and "1045 → 572" in report
                and "93223:5 → 93214:12" in report and "NOT_APPLICABLE_FULLY_TRANSPARENT_SOURCE" in report
                and acceptance["nativePendingChunkOrdering"] in report, "Human report differs from actual accepted results")
        result.update({"status": "PASS", "productionJarSha256": EXPECTED_SHA, "sourceCommit": acceptance["sourceCommit"],
            "acceptanceSha256": sha(DOC / "acceptance.json"), "acceptedEvidenceHashesVerified": len(evidence),
            "actualSuccessfulGameTestCases": 72, "gameTestXmlSha256": sha(xml_path), "mappedStateRows": 1281,
            "mergedGroups": 219, "finiteSourceShiftVectors": 1281, "originalV12RowsUnchanged": 116,
            "reservedIdsUnchanged": 1322, "workingTypesBefore": 1045, "workingTypesAfter": 572,
            "nativeObservedFrames": 176, "gallerySavedObjects": 675, "galleryObservedFrames": 48,
            "referenceArchiveByteHashesVerified": reference_hashes, "verifiedDependencies": dependencies,
            "sourceTransparencyExceptionsExplicit": 2, "remainingUnrunChecksExplicit": True})
    except Exception as error:
        result["failure"] = f"{type(error).__name__}: {error}"
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf8")
    print(json.dumps({"status": result["status"], "failure": result.get("failure")}))
    return 0 if result["status"] == "PASS" else 1


if __name__ == "__main__":
    raise SystemExit(main())
