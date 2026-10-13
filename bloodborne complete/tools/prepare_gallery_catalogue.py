"""Prepare a disjoint, source-backed V12.1 gallery inventory without editing a world.

The release JAR is the authority for offered architecture variants. The frozen
catalogue also contains obsolete allocations, which are documented, not placed.
Missing or invalid author material becomes a clearly identified diagnostic
platform; this tool never creates replacement models or reinstates retired art.
"""
from __future__ import annotations

import csv
import hashlib
import json
import re
import subprocess
import traceback
import zipfile
from collections import Counter, defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
REPO = ROOT.parent
OUT = ROOT / "docs/gallery-v12.1"
BASELINE = "8b770a03de21554a21c91700a1c1c6fe86c894c4"
JAR = REPO / "releases/Dreamwalker-BB/v12.1-resource-transfer/dreamwalker-bb-fabric-1.20.1-0.1.0-v12.1-resource-transfer.jar"
GIT = "C:/Program Files/Git/cmd/git.exe"
RES = ROOT / "src/architecture/resources"
ABSENCE = ROOT / "docs/map-pack-absence"
TRANSFER = ROOT / "docs/remaining-transfer"


def document(path):
    return json.loads(path.read_text(encoding="utf-8-sig"))


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def git(*args):
    return subprocess.check_output([GIT, *args], cwd=REPO).decode("utf-8")


def retired(path):
    return path in {"builder_tool", "composite_builder", "wall_builder", "prototype_ladder_art_1", "prototype_ladder_art_2"} or bool(re.fullmatch("prototype_wall_skin_[23457]", path))


def csv_rows(name):
    with (ABSENCE / name).open(encoding="utf-8-sig", newline="") as stream:
        return list(csv.DictReader(stream))


def target_key(registry, variant):
    return f"{registry}[variant={variant}]"


def issue_list(row):
    value = row.get("sourceIssues", "")
    return json.loads(value) if value else []


def issue_russian(issue):
    kind = issue.get("type", issue.get("issue", ""))
    label = {"TEXTURE_ALIAS": "Неопределённая ссылка на текстуру", "MISSING_TEXTURE": "Отсутствует текстура", "PARENT": "Отсутствует или повреждён родитель модели", "INVALID_JSON": "Повреждён JSON модели", "MISSING_MODEL": "Отсутствует исходная модель"}.get(kind, "Дефект исходника")
    detail = issue.get("texture", issue.get("parent", issue.get("model", issue.get("file", ""))))
    return label + (": " + detail if detail else "")


def rp_russian(path, original):
    names = {
        "babycart": "Детская коляска", "boat": "Лодка", "cabriolet_closed": "Закрытый кабриолет", "cabriolet_open": "Открытый кабриолет", "carriage": "Карета", "chair": "Стул", "chandelier_large": "Большая люстра", "chandelier_small": "Малая люстра", "chest": "Сундук", "coffin_stand": "Подставка для гроба", "cross_beast": "Крест с чудовищем", "curtain_half": "Половина занавеса", "curtains": "Занавес", "curtainsmall": "Малый занавес", "door_empty": "Пустая дверная рама", "elevator": "Лифт", "furniture_books": "Мебель с книгами", "furniture_cabinet_pantry": "Шкаф кладовой", "furniture_pantry": "Мебель кладовой", "gate_empty": "Пустая рама ворот", "hearse": "Катафалк", "hook": "Крюк", "horse_carcass": "Туша лошади", "hunterlamp": "Фонарь охотника", "ladder": "Лестница RP", "lamp_npc": "Фонарь NPC", "long_table": "Длинный стол", "main_gate": "Главные ворота", "npc_window": "Окно NPC", "odeon_tomb": "Гробница Одеона", "ropes": "Верёвки", "small_gate": "Малые ворота", "smallcross": "Малый крест", "stairs": "Лестничный марш RP", "trapdoor": "Люк RP", "wheelchair": "Кресло-коляска", "wood_gate": "Деревянные ворота",
    }
    if path in names:
        return names[path]
    match = re.fullmatch(r"(cage_obj_|coffin_|coffins_|door_|furniture_|lever_|statue_|tree)(\d+)", path)
    if match:
        label = {"cage_obj_": "Клетка", "coffin_": "Гроб", "coffins_": "Группа гробов", "door_": "Дверь RP", "furniture_": "Мебель", "lever_": "Рычаг", "statue_": "Статуя RP", "tree": "Дерево RP"}[match[1]]
        return f"{label} · вариант {match[2]}"
    if path.startswith("dog_cage"):
        return "Клетка для собак" + (" · вариант " + path[len("dog_cage"):] if path != "dog_cage" else "")
    return "Декоративный объект RP · " + original


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    analysis_commit = git("rev-parse", "HEAD").strip()
    baseline_path = "bloodborne complete/src/architecture/resources/bloodborne_dw/debug_catalogue.json"
    baseline = json.loads(git("show", BASELINE + ":" + baseline_path))
    baseline_rows = {row["registryId"]: row for row in baseline["entries"]}
    candidate = document(TRANSFER / "source-candidates.json")
    candidates = {row["path"]: row for row in candidate["objects"]}
    all_models = {row["sourceModel"]: row for row in csv_rows("all-models.csv")}
    absent_rows = csv_rows("absent-objects-short.csv")
    absent_targets = {target for row in absent_rows for target in row["offeredTargets"].split(";") if target}
    absent_evidence = defaultdict(list)
    for row in absent_rows:
        for target in row["offeredTargets"].split(";"):
            if target:
                absent_evidence[target].append(row["sourceModel"])
    report = {
        "schemaVersion": 1,
        "status": "PASS",
        "baselineCommit": BASELINE,
        "analysisCommit": analysis_commit,
        "productionJar": str(JAR),
        "productionJarSha256": sha(JAR),
        "classificationPolicy": "error > absent > v12 > new; every actual registry/variant appears once; origin retains V12/NEW independently",
        "displayPolicy": "All offered complete art variants in BASE, rotation 0, initially closed. Open/closed, rotations and BASE/ALT are interactive states, not duplicate type exhibits. Canonical RP construction objects are included; mobs, eggs, weapons, tools and helper cells are not building objects.",
        "absencePolicy": "Exact original v16 terrain analysis. 115 source-art rows expand to 120 distinct offered variants. RP entity presence in the original map was outside that terrain-only audit and is not inferred.",
        "diagnosticPolicy": "57 source defect model/dependency paths get empty labelled diagnostic platforms. They are not fabricated or registered blocks. Three valid rendered jungle-trapdoor variants also sit in error because the original unused particle texture is absent.",
        "sections": [
            {"id": "v12", "title": "Блоки и декор исходной V12", "floor": "minecraft:light_blue_concrete"},
            {"id": "new", "title": "Новые перенесённые блоки", "floor": "minecraft:lime_concrete"},
            {"id": "absent", "title": "Объекты, отсутствующие на исходной карте", "floor": "minecraft:yellow_concrete"},
            {"id": "error", "title": "Ошибки и повреждения исходников", "floor": "minecraft:red_concrete"},
        ],
        "exhibits": [], "excluded": [],
        "evidence": {"mapAuditSummarySha256": sha(ABSENCE / "summary.json"), "absentObjectsShortSha256": sha(ABSENCE / "absent-objects-short.csv"), "sourceCandidatesSha256": sha(TRANSFER / "source-candidates.json"), "baselineCatalogueSha256": hashlib.sha256(git("show", BASELINE + ":" + baseline_path).encode("utf-8")).hexdigest()},
    }
    with zipfile.ZipFile(JAR) as jar:
        debug_bytes = jar.read("bloodborne_dw/debug_catalogue.json")
        remaining_bytes = jar.read("bloodborne_dw/remaining-catalogue.json")
        assert debug_bytes == (RES / "bloodborne_dw/debug_catalogue.json").read_bytes()
        assert remaining_bytes == (RES / "bloodborne_dw/remaining-catalogue.json").read_bytes()
        debug = json.loads(debug_bytes)
        remaining = json.loads(remaining_bytes)
        remaining_by_path = {row["path"]: row for row in remaining["objects"]}
        catalogue_rows = {row["registryId"]: row for row in debug["entries"]}
        rp_catalog = json.loads(jar.read("assets/bloodborne_rp/catalog.json"))
        for row in debug["entries"]:
            registry, kind = row["registryId"], row["kind"]
            path = registry.split(":", 1)[1]
            excluded_reason = ""
            if row.get("canonicalRegistryId") or retired(path):
                excluded_reason = "RETIRED_CANONICAL_ALIAS_NOT_RESTORED"
            elif kind not in {"architecture", "rp_object"}:
                excluded_reason = "NON_BUILDING_ITEM_MOB_OR_RESERVED_HELPER"
            elif kind == "architecture" and registry not in baseline_rows and path not in remaining_by_path:
                excluded_reason = "RESERVED_UNREGISTERED_PREFINAL_SOURCE_ALLOCATION"
            if excluded_reason:
                report["excluded"].append({"id": row["temporaryId"], "registry": registry, "kind": kind, "name": row["name"], "reason": excluded_reason, "canonicalRegistry": row.get("canonicalRegistryId", "")})
                continue
            origin = "V12" if registry in baseline_rows else "NEW"
            if origin == "V12":
                assert row == baseline_rows[registry], ("Changed protected baseline TEMP row", registry)
            if kind == "rp_object":
                assert path in rp_catalog
                asset = rp_catalog[path]
                for field in ("model", "texture"):
                    assert "assets/bloodborne_rp/" + asset[field] in jar.namelist(), (registry, field)
                report["exhibits"].append({"key": registry, "section": "v12", "origin": origin, "kind": kind, "id": row["temporaryId"], "name": rp_russian(path, row["name"]), "sourceName": row["name"], "registry": registry, "item": registry + "_placer", "variant": 0, "profile": "base", "open": False, "state": {}, "representation": "registered_rp_entity", "sourceModels": ["assets/bloodborne_rp/" + asset["model"]], "sourceParts": [{"model": "assets/bloodborne_rp/" + asset["model"], "texture": "assets/bloodborne_rp/" + asset["texture"], "scale": asset.get("scale", 1)}], "sourceIssues": [], "status": "PRESERVED_V12", "mapStatus": "NOT_ASSESSED_RP_ENTITIES"})
                continue
            if path in remaining_by_path:
                assert origin == "NEW"
                spec = json.loads(jar.read("bloodborne_dw/composite/" + path + ".json"))
                assert len(spec["variants"]) == len(remaining_by_path[path]["variants"])
                for variant in remaining_by_path[path]["variants"]:
                    v = variant["variant"]
                    plan = candidates[path]["variants"][v]
                    apps = [{"pose": pose, **application} for pose in ("closed", "open") for application in plan.get(pose, [])]
                    models = sorted({app["model"] for app in apps})
                    issues = [dict(model=model, **issue) for model in models for issue in issue_list(all_models[model])]
                    target = target_key(registry, v)
                    section = "error" if issues else "absent" if target in absent_targets else "new"
                    name = variant["name"]
                    if path.startswith("source_assembly_") and not name.startswith(row["name"]):
                        name = row["name"] + " · " + name
                    report["exhibits"].append({"key": target, "section": section, "origin": origin, "kind": "architecture", "id": row["temporaryId"], "name": name, "registry": registry, "item": registry, "variant": v, "profile": "base", "open": False, "state": {"variant": str(v), "profile": "base", "open": "false"}, "representation": "ordinary_block_item", "sourceModels": models, "sourceParts": apps, "sourceIssues": issues, "status": "VALID_RENDERED_SOURCE_PARTICLE_DEFECT" if issues else "FINISHED_IMPORTED", "mapStatus": "ABSENT_ORIGINAL_V16" if target in absent_targets else "NOT_PROVEN_ABSENT", "absenceEvidenceSourceModels": absent_evidence.get(target, []), "spec": "bloodborne_dw/composite/" + path + ".json", "openable": spec.get("openable", False)})
            else:
                assert origin == "V12"
                spec_path = "bloodborne_dw/composite/" + path + ".json"
                spec = json.loads(jar.read(spec_path)) if spec_path in jar.namelist() else None
                # Old window01 states 1/2 are deserialize-only aliases to the
                # separately offered window02/window03 types (GlazingTypes).
                assert spec is None or len(spec["variants"]) == 1 or path == "prototype_thin_window" and len(spec["variants"]) == 3
                source_parts = spec["variants"][0]["poses"]["closed"].get("parts", []) if spec else []
                report["exhibits"].append({"key": registry, "section": "v12", "origin": origin, "kind": kind, "id": row["temporaryId"], "name": row["name"], "registry": registry, "item": registry, "variant": 0, "profile": "base", "open": False, "state": {}, "representation": "ordinary_block_item", "sourceModels": sorted({app["model"] for app in source_parts}), "sourceParts": source_parts, "sourceIssues": [], "status": "PRESERVED_V12", "mapStatus": "NOT_PROVEN_ABSENT", "openable": spec.get("openable", False) if spec else False})
        # Existing source templates and absent source models are not new mod IDs.
        classified = {row["sourceModel"]: row for row in candidate["modelClassifications"]}
        diagnostic_models = {row["sourceModel"] for row in candidate["modelClassifications"] if row["status"].startswith("BLOCKED_SOURCE")}
        diagnostic_models.update(row["sourceModel"] for row in all_models.values() if any(issue.get("issue") == "INVALID_JSON" for issue in issue_list(row)))
        for model in sorted(diagnostic_models):
            source_row = all_models[model]
            classification = classified.get(model, {})
            issues = issue_list(source_row)
            if not issues:
                issues = [{"type": "MISSING_MODEL", "model": model, "detail": classification.get("missing", "Source defect confirmed by import audit")}]
            reason = "; ".join(dict.fromkeys(issue_russian(issue) for issue in issues))
            report["exhibits"].append({"key": "source-defect:" + model, "section": "error", "origin": "SOURCE_DEFECT", "kind": "diagnostic", "id": "", "name": "Ошибка исходника · " + model.removeprefix("minecraft:block/"), "registry": "", "item": "", "variant": 0, "profile": "base", "open": False, "state": {}, "representation": "empty_labelled_diagnostic_platform", "sourceModels": [model], "sourceParts": [], "sourceIssues": issues, "status": classification.get("status", "INVALID_SOURCE_JSON"), "sourceClassification": classification.get("kind", source_row["kind"]), "mapStatus": source_row["status"], "diagnosticReason": reason, "placeable": False})
    report["excluded"].extend([
        {"registry": "bloodborne_dw:composite_cell", "reason": "OWNED_TECHNICAL_CELL_NOT_AN_INDEPENDENT_OBJECT"},
        {"registry": "bloodborne_dw:source_ladder_backing", "reason": "TECHNICAL_SOURCE_LADDER_SUPPORT_NOT_AN_INDEPENDENT_OBJECT"},
        {"registry": "bloodborne_dw:bb_tool", "id": "90009", "reason": "NON_BUILDING_TOOL"},
        {"registry": "bloodborne_rp:*", "reason": "WEAPONS_SPAWN_EGGS_AND_OTHER_NON_PLACEABLE_RP_ITEMS_NOT_BUILDING_OBJECTS"},
    ])
    section_order = {section["id"]: index for index, section in enumerate(report["sections"])}
    report["exhibits"].sort(key=lambda row: (section_order[row["section"]], row["id"] or "99999", row["registry"], row["variant"], row["key"]))
    keys = [row["key"] for row in report["exhibits"]]
    assert len(keys) == len(set(keys)), "Duplicate exhibition target"
    real = [row for row in report["exhibits"] if row["kind"] != "diagnostic"]
    actual_targets = {row["key"] for row in real}
    assert absent_targets <= actual_targets, ("Absent source target omitted", sorted(absent_targets - actual_targets))
    assert len(real) == 1885
    assert len(absent_targets) == 120 and len(absent_rows) == 115
    counts = Counter(row["section"] for row in report["exhibits"])
    assert counts == {"v12": 80, "new": 1682, "absent": 120, "error": 60}, counts
    report["summary"] = {"totalPlatforms": len(keys), "realExhibits": len(real), "diagnosticPlatforms": len(keys) - len(real), "newRegistryTypes": len(remaining_by_path), "newOfferedVariants": sum(len(row["variants"]) for row in remaining_by_path.values()), "v12CanonicalArchitectureTypes": sum(row["kind"] == "architecture" and row["origin"] == "V12" for row in real), "v12CanonicalRpObjectTypes": sum(row["kind"] == "rp_object" for row in real), "absentSourceArtRows": len(absent_rows), "absentOfferedVariants": len(absent_targets), "sectionCounts": dict(counts), "kindCounts": dict(Counter(row["kind"] for row in report["exhibits"])), "originCounts": dict(Counter(row["origin"] for row in report["exhibits"])), "excludedCatalogueRows": len(report["excluded"]), "releaseResourceValidation": "PASS", "nativeRegistryValidation": "NOT_RUN_BY_CATALOGUE_TOOL", "worldPlacementAndReopenValidation": "NOT_RUN_BY_CATALOGUE_TOOL"}
    (OUT / "catalogue.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    try:
        main()
    except Exception:
        OUT.mkdir(parents=True, exist_ok=True)
        (OUT / "catalogue.json").write_text(json.dumps({"status": "FAIL", "traceback": traceback.format_exc()}, ensure_ascii=False, indent=2), encoding="utf-8")
        raise
