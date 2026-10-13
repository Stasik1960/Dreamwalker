"""Independent read-only audit of finished release archives; never extract/run."""
import argparse
import hashlib
import json
from pathlib import Path, PurePosixPath
import stat
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[1]
DOC = ROOT / "docs/v12.4-dedup"
JAR_NAME = "dreamwalker-bb-fabric-1.20.1-0.1.0-v12.4-dedup.jar"


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


def zip_json(archive, name):
    return json.loads(archive.read(name).decode("utf-8-sig"))


def zip_entries(archive):
    rows = archive.infolist()
    names = [row.filename for row in rows]
    require(len(names) == len(set(names)), "Archive repeats a member name")
    for row in rows:
        path = PurePosixPath(row.filename)
        require(not path.is_absolute() and ".." not in path.parts and "\\" not in row.filename,
                "Archive has an unsafe member path")
        require(not stat.S_ISLNK(row.external_attr >> 16), "Archive has a symlink member")
    require(archive.testzip() is None, "Archive CRC verification failed")
    return names


def game_tests(xml_bytes):
    root = ET.fromstring(xml_bytes)
    require(len(root.findall(".//testcase")) == 72 and not root.findall(".//failure")
            and not root.findall(".//error") and not root.findall(".//skipped"), "Archive has incomplete/nonpassing final72 GameTests")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--release-dir", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    output = {"schema": "dreamwalker-v124-independent-finished-release-audit-v1", "status": "FAIL",
              "readOnlyValidation": True, "archivesExtracted": False, "javaLaunched": False}
    try:
        release = args.release_dir.resolve()
        require(release.is_relative_to(ROOT.parent / "releases"), "Finished release is outside its intended project destination")
        record = read(release / "release-manifest.json")
        acceptance = read(release / "acceptance.json")
        production_sha = sha(ROOT / "build/libs" / JAR_NAME)
        require(record["status"] == acceptance["status"] == "PASS"
                and record["productionJarSha256"] == acceptance["productionJarSha256"] == production_sha,
                "Release does not bind the actual final combined JAR")
        require(sha(release / JAR_NAME) == production_sha, "Delivered JAR differs from tested final JAR")
        files = {row["name"]: row for row in record["files"]}
        require(len(files) == len(record["files"]), "Release manifest repeats a file")
        require({path.name for path in release.iterdir() if path.is_file()} - {"release-manifest.json"} == set(files),
                "Actual release file set differs from its manifest")
        for name, row in files.items():
            path = release / name
            require(path.stat().st_size == row["bytes"] and sha(path) == row["sha256"], "Release file bytes differ: " + name)
        require(acceptance["gameTestCount"] == 72, "Acceptance substitutes a lower count for actual72 GameTests")
        for field in ("build", "gameTests", "nativeOldSaveMigration", "nativeSaveReopen", "nativeVisualReview",
                      "galleryServerReopen", "galleryClientOpenAndSave", "gallerySavedObjects", "preservation", "resources"):
            require(acceptance[field] == "PASS", "Acceptance phase is incomplete: " + field)
        require(acceptance["physicalMouseKeyboard"] == "NOT_RUN" and acceptance["authorCollisionExport"] == "NOT_SUPPLIED_NOT_APPLIED",
                "Unsupported physical-input/author-collision claims in acceptance")
        for name, evidence in acceptance["evidence"].items():
            require(sha(Path(evidence["path"])) == evidence["sha256"], "Actual accepted evidence bytes differ: " + name)
        dependencies = acceptance["verifiedDependencies"]
        require(any(row["id"] == "fabric-api" and row["version"] == "0.92.9+1.20.1" for row in dependencies),
                "Verified Fabric API version differs")
        require(any(row["id"].startswith("geckolib") and row["version"] == "4.4.9" for row in dependencies),
                "Verified GeckoLib version differs")
        require(not any("qa" in row["id"].lower() for row in dependencies), "QA addon declared as a production dependency")
        source_path = release / "dreamwalker-bb-fabric-1.20.1-0.1.0-v12.4-dedup-full-source.zip"
        with zipfile.ZipFile(source_path) as source, zipfile.ZipFile(release / "verification.zip") as verification:
            source_names, verification_names = zip_entries(source), zip_entries(verification)
            prefix = "bloodborne complete/"
            require(all(name.startswith(prefix) for name in source_names), "Source ZIP has an unexpected root")
            require(not any("/projections/" in name or "/full-coverage-projections/" in name for name in source_names),
                    "Source ZIP unexpectedly includes huge temporary projections")
            game_tests(verification.read("game-tests-final.xml"))
            require(source.read(prefix + "docs/v12.4-dedup/game-tests-final.xml") == verification.read("game-tests-final.xml"),
                    "Source/evidence final GameTest XML differ")
            require(verification.read("acceptance.json") == (release / "acceptance.json").read_bytes(),
                    "Archived and delivered acceptance differ")
            for name in ("REPORT-RU.txt", "INSTALL-RU.txt", "MERGED-GROUPS-RU.csv", "OLD-ID-STATES.csv", "RETAINED-DIFFERENCES-RU.txt"):
                content = (release / name).read_bytes()
                require(verification.read(name) == source.read(prefix + "docs/v12.4-dedup/" + name) == content,
                        "Readable source/evidence/release report differs: " + name)
            for name in ("native-save-evidence-final.json", "native-visual-final.json", "resource-audit.json", "reference-input-preservation.json"):
                evidence = zip_json(verification, name)
                require(evidence["status"] == "PASS", "Archived final evidence is incomplete: " + name)
                if "productionJarSha256" in evidence:
                    require(evidence["productionJarSha256"] == production_sha, "Archived evidence has a different JAR: " + name)
            roundtrip = zip_json(verification, "readable-report-roundtrip-final.json")
            require(roundtrip["status"] == "PASS" and roundtrip["productionJarSha256"] == production_sha
                    and roundtrip["mappedStateRows"] == 1281 and roundtrip["yawStateSourceShiftMismatches"] == 0,
                    "Archived readable table roundtrip is incomplete")
            for name,digest in roundtrip['bindings'].items():
                require(hashlib.sha256(verification.read(name)).hexdigest()==digest,
                        "Archived table/native bound evidence differs: "+name)
            frozen = zip_json(source, prefix + "docs/v12.4-dedup/baseline-content.json")
            source_catalogue = zip_json(source, prefix + "src/architecture/resources/bloodborne_dw/debug_catalogue.json")
            actual_rows = {row["registryId"]: row for row in source_catalogue["entries"]}
            require(len(frozen["originalRows"]) == 116 and all(actual_rows[row["registryId"]] == row for row in frozen["originalRows"]),
                    "Delivered sources changed original V12 catalogue rows")
            reserved = [row["temporaryId"] for row in source_catalogue["entries"]]
            require(reserved == [row["temporaryId"] for row in frozen["debugCatalogue"]["entries"]]
                    and len(reserved) == len(set(reserved)) == 1322, "Delivered ID reservations changed")
            with zipfile.ZipFile(release / JAR_NAME) as jar:
                for name in ("debug_catalogue.json", "imported-type-aliases.json", "imported-stair-legacy-art.json"):
                    require(source.read(prefix + "src/architecture/resources/bloodborne_dw/" + name) == jar.read("bloodborne_dw/" + name),
                            "Delivered source registry/type/art resource differs from tested JAR: " + name)
                source_set=set(source_names);asset_bytes_checked=0
                for name in jar.namelist():
                    if not name.startswith('assets/') or name.endswith('/'):continue
                    candidates=[entry for entry in source_names if '/resources/'+name in entry
                                and entry.endswith('/resources/'+name)]
                    require(candidates,"Delivered sources lack JAR asset: "+name)
                    actual=jar.read(name)
                    require(any(source.read(entry)==actual for entry in candidates),
                            "Delivered source asset differs from final JAR: "+name)
                    asset_bytes_checked+=1
            counts = zip_json(verification, "type-counts.json")
            require(counts == record["counts"] == acceptance["counts"] and counts["newWorkingTypesAfter"] == 572
                    and counts["newWorkingTypesBefore"] == 1045 and counts["retiredWorkingIds"] == 473,
                    "Delivered working type counts differ")
        with zipfile.ZipFile(release / "Dreamwalker-BB-V12.4-Flat-Gallery.zip") as world:
            world_names = zip_entries(world)
            prefix = "Dreamwalker-BB-V12.4-Flat-Gallery/"
            require(all(name.startswith(prefix) for name in world_names) and prefix + "level.dat" in world_names,
                    "Delivered gallery ZIP has invalid root/level")
            require(not any(name.endswith("/session.lock") or name.endswith(".jar") for name in world_names),
                    "Delivered gallery contains a runtime lock or addon JAR")
            manifest = zip_json(world, prefix + "gallery-manifest.json")
            require(manifest["productionJarSha256"] == production_sha and len(manifest["exhibits"]) == 675
                    and not any(row["section"] == "error" for row in manifest["exhibits"]), "Delivered gallery does not match final675 stations")
            current = manifest["currentFunctionalCatalogueProvenance"]
            require(current["sourceCommit"] == acceptance["sourceCommit"] and current["baselineCommit"] == acceptance["baselineCommit"]
                    and current["removedPreviousRedStations"] == 60 and current["diagnosticStations"] == 0,
                    "Gallery current provenance/diagnostic policy differs")
            source_world = Path(acceptance["galleryWorld"])
            for name in world_names:
                path = source_world / PurePosixPath(name).relative_to(prefix)
                require(hashlib.sha256(world.read(name)).hexdigest() == sha(path), "Delivered gallery file differs from the tested world")
        output.update({"status": "PASS", "productionJarSha256": production_sha, "sourceCommit": acceptance["sourceCommit"],
            "releaseFilesChecked": len(files), "acceptedEvidenceFilesChecked": len(acceptance["evidence"]),
            "actualSuccessfulGameTests": 72, "originalV12CatalogueRowsUnchanged": 116, "reservedIdsUnchanged": 1322,
            "workingTypesBefore": 1045, "workingTypesAfter": 572, "mappingRows": 1281, "galleryStations": 675,
            "verifiedDependencies": dependencies, "archivesByteAndCrcVerification": "PASS",
            "sourceAssetFilesComparedToActualJar":asset_bytes_checked,
            "releaseManifestSha256": sha(release / "release-manifest.json")})
    except Exception as error:
        output["failure"] = f"{type(error).__name__}: {error}"
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(output, ensure_ascii=False, indent=2) + "\n", encoding="utf8")
    print(json.dumps({"status": output["status"], "failure": output.get("failure")}))
    return 0 if output["status"] == "PASS" else 1


if __name__ == "__main__":
    raise SystemExit(main())
