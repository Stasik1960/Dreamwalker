"""Read-only CSV-to-final-JAR roundtrip and complete native evidence bindings."""
import argparse
import csv
import hashlib
import json
import math
from pathlib import Path
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[1]
DOC = ROOT / "docs/v12.4-dedup"
JAR = ROOT / "build/libs/dreamwalker-bb-fabric-1.20.1-0.1.0-v12.4-dedup.jar"


def require(condition, message):
    if not condition:
        raise AssertionError(message)


def read(path):
    return json.loads(path.read_text(encoding="utf-8-sig"))


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def csv_rows(path):
    with path.open(encoding="utf-8-sig", newline="") as stream:
        return list(csv.DictReader(stream, delimiter=";"))


def pose(mapping, kind):
    variant = int(mapping["newVariant"])
    properties = mapping.get("properties", {})
    if kind == "artwork":
        return variant + 16 * int(properties.get("art_bank", 0))
    if kind == "stairs" and str(properties.get("trim", "false")).lower() == "true":
        return variant + 10
    return variant


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--game-test-run", default="v124-dedup-final-5")
    parser.add_argument("--output", type=Path, default=DOC / "readable-report-roundtrip-final.json")
    args = parser.parse_args()
    output = {"schema": "dreamwalker-v124-readable-report-roundtrip-native-binding-v1", "status": "FAIL",
              "readOnlyValidation": True, "failureCount": 0, "failures": []}
    try:
        production_sha = sha(JAR)
        output["productionJarSha256"] = production_sha
        with zipfile.ZipFile(JAR) as jar:
            manifest = json.loads(jar.read("bloodborne_dw/imported-type-aliases.json"))
            catalogue = json.loads(jar.read("bloodborne_dw/debug_catalogue.json"))
            art = json.loads(jar.read("bloodborne_dw/imported-stair-legacy-art.json"))
        documentation = read(DOC / "type-state-mapping.json")
        require(documentation["groups"] == manifest["groups"] and documentation["mappings"] == manifest["mappings"],
                "Documentation mappings/groups differ from the actual combined JAR")
        groups = {str(group["primaryId"]): group for group in manifest["groups"]}
        mappings = {(str(row["oldId"]), int(row["oldVariant"])): row for row in manifest["mappings"]}
        require(len(mappings) == len(manifest["mappings"]) == 1281, "Duplicate or missing exact mapping keys")
        source = read(DOC / "baseline-content.json")
        baseline_rows = {row["registryId"]: row for row in source["debugCatalogue"]["entries"]}
        registered = {row["registryId"]: row for row in catalogue["entries"]}
        art_keys = {row["key"] for row in art["states"]}
        old_states = csv_rows(DOC / "OLD-ID-STATES.csv")
        require(len(old_states) == len(mappings), "Readable state row count differs from manifest")
        seen = set()
        vectors = 0
        artistic_keys = 0
        for row in old_states:
            key = row["Старый ID"], int(row["Старый variant"])
            require(key in mappings and key not in seen, f"Unknown/duplicate readable mapping: {key}")
            seen.add(key)
            mapping = mappings[key]
            require(row["Старая регистрация"] == mapping["oldRegistryId"] and row["Основной ID"] == str(mapping["newId"])
                    and row["Новая регистрация"] == mapping["newRegistryId"], f"Readable registry/primary differs: {key}")
            group = groups[str(mapping["newId"])]
            require(int(row["Логический номер позы"]) == pose(mapping, group["kind"]), f"Readable pose bank/trim differs: {key}")
            require(json.loads(row["Назначаемые свойства JSON"]) == {"variant": mapping["newVariant"], **mapping.get("properties", {})},
                    f"Readable variant/additional properties differ: {key}")
            delta = int(row["Добавка поворота — шаги 45°"])
            require(delta == mapping["rotationDelta"] and int(row["Добавка поворота — градусы"]) == delta * 45 and 0 <= delta <= 7,
                    f"Readable yaw mapping differs: {key}")
            vector = json.loads(row["Добавка SourceShift — координаты нового типа"])
            require(len(vector) == 3 and all(isinstance(value, (int, float)) and math.isfinite(value) for value in vector)
                    and vector == mapping["sourceShift"], f"Readable SourceShift differs/nonfinite: {key}")
            vectors += 1
            require(row["Формула rotation"] == "(old.rotation + rotationDelta) mod 8", f"Readable yaw formula differs: {key}")
            require("old.SourceShift.rotate(-rotationDelta) + sourceShift" in row["Формула SourceShift"],
                    f"Readable SourceShift formula differs: {key}")
            expected_art = mapping["oldRegistryId"] + "|" + str(mapping["oldVariant"])
            expected_art = expected_art if expected_art in art_keys else ""
            require(row["Ключ прежнего рисунка ступеней"] == expected_art, f"Readable authored stair key differs: {key}")
            artistic_keys += bool(expected_art)
        require(seen == set(mappings) and artistic_keys == len(art_keys) == 313, "Readable old-state/art-key coverage differs")
        merge_rows = csv_rows(DOC / "MERGED-GROUPS-RU.csv")
        require(len(merge_rows) == len(groups) == 219, "Readable merged groups are incomplete")
        seen_groups = set()
        for row in merge_rows:
            primary = row["Основной ID"]
            require(primary in groups and primary not in seen_groups, "Duplicate/unknown readable merged group")
            seen_groups.add(primary)
            group = groups[primary]
            members = [str(baseline_rows[registry]["temporaryId"]) for registry in group["members"]]
            require(row["Тип"] == group["kind"] and row["Исходные ID"].split(" | ") == members,
                    f"Readable merged group kind/members differ: {primary}")
            expected_pose_count = group.get("poseCount", max(pose(mapping, group["kind"]) for mapping in mappings.values()
                                                             if str(mapping["newId"]) == primary) + 1)
            require(int(row["Количество поз/состояний"]) == expected_pose_count, f"Readable pose count differs: {primary}")
            require(group["oneItem"] is True and row["Каталог и Creative"] == "ДА — один рабочий тип и один предмет",
                    f"Readable one-item policy differs: {primary}")
            require(row["Основание объединения"].strip(), f"Readable merge has no concrete reason: {primary}")
        original_equal = all(registered[row["registryId"]] == row for row in source["originalRows"])
        baseline_ids = [row["temporaryId"] for row in source["debugCatalogue"]["entries"]]
        current_ids = [row["temporaryId"] for row in catalogue["entries"]]
        require(original_equal and len(source["originalRows"]) == 116, "Original V12 catalogue rows changed")
        require(current_ids == baseline_ids and len(current_ids) == len(set(current_ids)) == 1322, "ID reservations/order changed")
        counts = read(DOC / "type-counts.json")
        require(counts["newWorkingTypesBefore"] == 1045 and counts["newWorkingTypesAfter"] == 572
                and counts["retiredWorkingIds"] == 473 and counts["mergedGroups"] == len(groups), "Working type counts differ")
        native = read(DOC / "native-visual-final.json")
        pixel = read(DOC / "native-final-pixel-review.json")
        save = read(DOC / "native-save-evidence-final.json")
        require(native["status"] == pixel["status"] == save["status"] == "PASS", "Final native evidence is not complete")
        require(native["productionJarSha256"] == pixel["productionJarSha256"] == save["productionJarSha256"] == production_sha,
                "Final native/save evidence is not bound to actual candidate bytes")
        require(native["captureSha256"] == pixel["captureSha256"] and native["reviewedFrames"] == pixel["reviewedFrames"] == 176
                and pixel["capturedPairs"] == 2102, "Final complete native capture bindings differ")
        require(native["mappedOldStatesRendered"] == 1281 and native["allFrozenNewStatesRendered"] == 1802
                and native["newFunctionalStairStatesRendered"] == 200 and native["retainedPairReviews"] == 91,
                "Final complete source/mapping/functional/reason review coverage differs")
        run = Path(pixel["rawGameDirectory"])
        require(sha(run / "v124-client-comparison-output.json") == pixel["captureSha256"], "Final actual capture bytes changed")
        require(native["clientLogSha256"] == sha(run / "launch-console.log"), "Final client log bytes changed")
        require(len(native["frames"]) == len(pixel["frames"]) == 176 and not pixel["visibleFailures"], "Final native image review is incomplete")
        for frame in native["frames"]:
            require(sha(run / "screenshots" / frame["fileName"]) == frame["sha256"], "Final native raw image bytes changed")
        exceptions = {(row["oldRegistryId"], row["oldVariant"], row["mainId"], row["variant"]) for row in native["sourceVisibilityExceptions"]}
        require(exceptions == {("bloodborne_dw:source_addon_floor_panels_empty", 0, "90133", 8),
                               ("bloodborne_dw:source_assembly_red_stained_glass_pane", 5, "93214", 12)},
                "Final confirmed transparent source exceptions differ")
        xml_path = ROOT / "build/test-results/gametest" / ("TEST-" + args.game_test_run + ".xml")
        xml = ET.parse(xml_path).getroot()
        test_count = len(xml.findall(".//testcase"))
        require(test_count == 72 and not xml.findall(".//failure") and not xml.findall(".//error") and not xml.findall(".//skipped"),
                "Actual frozen final GameTests are not all72 successful cases")
        output.update({"status": "PASS", "mappedStateRows": len(old_states), "mergedGroupRows": len(merge_rows),
            "finiteSourceShiftVectors": vectors, "yawStateSourceShiftMismatches": 0,
            "preservedAuthoredStairKeys": artistic_keys, "originalV12RowsUnchanged": 116, "reservedIdsUnchanged": 1322,
            "newWorkingTypesBefore": 1045, "newWorkingTypesAfter": 572, "actualSuccessfulGameTestCases": test_count,
            "actualGameTestXmlSha256": sha(xml_path), "nativeReviewedFrames": 176, "nativeCapturedPairs": 2102,
            "confirmedFullyTransparentSourceStates": len(exceptions),
            "bindings": {name: sha(DOC / name) for name in ("OLD-ID-STATES.csv", "MERGED-GROUPS-RU.csv", "type-state-mapping.json",
                "type-counts.json", "native-visual-final.json", "native-final-pixel-review.json", "native-save-evidence-final.json")},
            "scopeLimit": "Readable table/native evidence consistency only; gallery and release acceptance are separate later stages."})
    except Exception as error:
        output["failureCount"] = 1
        output["failures"] = [f"{type(error).__name__}: {error}"]
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(output, ensure_ascii=False, indent=2) + "\n", encoding="utf8")
    print(json.dumps({"status": output["status"], "failureCount": output["failureCount"], "failures": output["failures"]}))
    return 0 if output["status"] == "PASS" else 1


if __name__ == "__main__":
    raise SystemExit(main())
