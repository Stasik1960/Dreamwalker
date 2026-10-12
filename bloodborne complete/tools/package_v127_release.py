"""Package an accepted V12.7 revision; never build, convert a world, or publish.

The acceptance file is produced by the final native checks, not by this script.
Archives and hashes are streamed to keep packaging memory bounded.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import stat
import subprocess
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[1]
REPO = ROOT.parent
DOC = ROOT / "docs/v12.7"
VERSION = "0.1.0-v12.7-final"
JAR = ROOT / f"build/libs/dreamwalker-bb-fabric-1.20.1-{VERSION}.jar"
DEST = REPO / "releases/Dreamwalker-BB/v12.7-final"
BLOCKBENCH = ROOT / "build/Dreamwalker-BB-V12.7-New-Blockbench-Assets.zip"
RUNTIME_PLAN = ROOT / "build/v127-runtime-conversion"
SOURCE_HASHES = {
    "map": "39e83f2941b768fd2ae9f84d8f57dc76aec78bd926dc5e38eab3059251d2bb08",
    "pack": "29b31e3744af5794ae95ebe4c5474e3b70dfb08c143355bce815c08ceef09a36",
}
INPUT_NAMES = {"map": "bbmc_v16_map.zip", "pack": "bbmc_v15_resource.zip"}
CHUNK = 1024 * 1024
REQUIRED_CHECKS = {
    "build", "gameTests", "nativeClient", "nativeServerSaveReopen", "resources",
    "manualPlacement", "worldEdit", "fullConversionPlan",
}


def read(path: Path):
    return json.loads(path.read_text(encoding="utf-8-sig"))


def sha(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as stream:
        for data in iter(lambda: stream.read(CHUNK), b""):
            h.update(data)
    return h.hexdigest()


def ordinary(path: Path) -> bool:
    """Reject links/reparse points, including any ancestor within the workspace."""
    absolute = path.absolute()
    for part in (absolute, *absolute.parents):
        info = part.lstat()
        if stat.S_ISLNK(info.st_mode) or getattr(info, "st_file_attributes", 0) & 0x400:
            return False
        if part == REPO:
            break
    return True


def files(directory: Path):
    assert ordinary(directory), f"Linked/reparsed directory rejected: {directory.name}"
    for current, directories, names in os.walk(directory, followlinks=False):
        base = Path(current)
        directories[:] = sorted(d for d in directories if ordinary(base / d))
        for name in sorted(names):
            path = base / name
            if ordinary(path) and path.is_file():
                yield path


def excluded(path: Path) -> bool:
    parts = set(path.parts)
    return bool(parts & {
        "build", ".gradle", ".git", "__pycache__", "projections",
        "full-coverage-projections", "node_modules", "exports",
    }) or path.name in {"session.lock", "local.properties"} or path.suffix == ".pyc"


def archive(destination: Path, entries: list[tuple[Path, str]]) -> dict:
    rows = []
    names = set()
    with zipfile.ZipFile(destination, "w", zipfile.ZIP_DEFLATED, compresslevel=6,
                         allowZip64=True) as out:
        for path, name in sorted(entries, key=lambda e: e[1]):
            assert ordinary(path) and path.is_file(), name
            assert name not in names and not name.startswith("/") and ".." not in Path(name).parts, name
            names.add(name)
            info = zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = (0o100644 << 16)
            h = hashlib.sha256()
            size = 0
            with path.open("rb") as src, out.open(info, "w", force_zip64=True) as target:
                for data in iter(lambda: src.read(CHUNK), b""):
                    target.write(data)
                    h.update(data)
                    size += len(data)
            rows.append({"name": name, "bytes": size, "sha256": h.hexdigest()})
        manifest = json.dumps({"schema": "dreamwalker-streamed-archive-contents-v1", "files": rows},
                              ensure_ascii=False, sort_keys=True, indent=2).encode("utf8") + b"\n"
        info = zipfile.ZipInfo("ARCHIVE-CONTENTS.json", (1980, 1, 1, 0, 0, 0))
        info.compress_type = zipfile.ZIP_DEFLATED
        out.writestr(info, manifest)
    with zipfile.ZipFile(destination) as check:
        assert check.testzip() is None, f"CRC failure: {destination.name}"
        for row in rows:
            h = hashlib.sha256()
            with check.open(row["name"]) as stream:
                for data in iter(lambda: stream.read(CHUNK), b""):
                    h.update(data)
            assert h.hexdigest() == row["sha256"], row["name"]
    return {"files": len(rows), "archiveCRC": "PASS", "entrySHA256": "PASS"}


def evidence_path(value: str) -> Path:
    path = Path(value)
    if not path.is_absolute():
        path = ROOT / path
    assert path.resolve().is_relative_to(ROOT), "Evidence outside this project is not portable"
    assert ordinary(path) and path.is_file(), str(path)
    return path


def validate(acceptance: Path, runtime_plan: Path, source_plan: Path | None = None) -> tuple[dict, dict]:
    assert acceptance.is_file(), "Final acceptance.json has not been produced yet"
    value = read(acceptance)
    assert value["status"] == "PASS", "A partial or failed revision cannot be packaged"
    assert not value.get("currentUnresolvedTestFailures"), "Unresolved native failures"
    digest = sha(JAR)
    assert value["productionJarSha256"] == digest, "Acceptance belongs to another JAR"
    assert value.get("productionSourceCheckpoint"), "Missing pre-build source checkpoint"
    assert REQUIRED_CHECKS.issubset(value["checks"]), "Missing final required check category"
    for name in REQUIRED_CHECKS:
        assert str(value["checks"][name]).startswith("PASS"), (name, value["checks"][name])
    for name, item in value["evidence"].items():
        path = evidence_path(item["path"])
        assert sha(path) == item["sha256"], f"Changed accepted evidence: {name}"
    assert value.get("authorCollisionExport") == "NOT_SUPPLIED_NOT_APPLIED"
    assert value.get("checks", {}).get("physicalMouseKeyboard") == "NOT_RUN"
    assert value.get("fullOriginalWorldConverted", False) is False
    assert JAR.with_name(JAR.stem + "-sources.jar").is_file()
    assert BLOCKBENCH.is_file(), "Missing final independent Blockbench archive"
    export = read(DOC / "visual-export/final-delivery-audit.json")
    assert str(export["status"]).startswith("PASS"), "Blockbench delivery not verified"
    assert sha(BLOCKBENCH) == export["archiveSha256"], "Accepted Blockbench archive changed"
    plan = read(runtime_plan / "manifest.json")
    assert plan["status"] == "PASS_COMPLETE_SOURCE_PLAN", "Full conversion source gaps remain"
    assert plan["knownVisibleGaps"] == 0
    assert plan["sourceHashes"] == SOURCE_HASHES
    assert plan.get("fullWorldConverted", False) is False
    for name, digest in plan["runtimeFiles"].items():
        path = runtime_plan / name
        assert path.parent.resolve() == runtime_plan.resolve() and sha(path) == digest, name
    assert {"sections.jsonl.gz", "owners.jsonl.gz", "whole-objects.jsonl.gz", "entities.jsonl.gz"}.issubset(plan["runtimeFiles"])
    if source_plan is not None:
        assert ordinary(source_plan) and sha(source_plan) == plan["sourcePlanSha256"], "Source SQLite plan changed"
    for kind, name in INPUT_NAMES.items():
        path = REPO / "Bloodborne-Blocks/reference-inputs/bbmc-v15-v16" / name
        assert sha(path) == SOURCE_HASHES[kind], f"Original {kind} archive changed"
    return value, plan


def report(acceptance: dict) -> str:
    counts = json.dumps(acceptance.get("counts", {}), ensure_ascii=False, sort_keys=True)
    lines = [
        "Dreamwalker BB — V12.7 final", "",
        "Minecraft 1.20.1 / Fabric / Java 17. Галерея в этой ревизии не создавалась.",
        f"Combined JAR: {JAR.name}",
        f"SHA-256: {acceptance['productionJarSha256']}",
        f"Commit исходников перед сборкой: {acceptance['productionSourceCheckpoint']}", "",
        "Добавлено: общий инструмент BASE/ALT и all; DW-барьер 93230; DW-свет 93231;",
        "разовое no-collide в руке; поддержка WorldEdit и пятизначных ID;",
        "дополнительное объединение клонов, краткие имена и новые Blockbench-проекты.",
        "Исходные ID, нумерация и основной вид V12 сохранены. Старые номера клонов",
        "остаются техническими переходниками и не выдаются за рабочие типы.", "",
        "Память: простые неподвижные модели полной конвертации представлены палитрой",
        "нативных блоков без UUID/BlockEntity на клетку; составные владельцы нового",
        "мира хранятся по чанкам с ограниченным кэшем; BASE/ALT обходит карту лениво.",
        "Снижение полного RAM города не вычисляется из одного размера NBT: измерения",
        "и их ограничения приведены в acceptance.json и verification.zip.", "",
        "Проверки: " + "; ".join(f"{k}={v}" for k, v in sorted(acceptance["checks"].items())),
        "Числа итоговой ревизии: " + counts,
        "Текущие неустранённые FAIL: отсутствуют в итоговой acceptance.json.",
        "NOT_RUN: физическая мышь/клавиатура; GUI Blockbench; полная конвертация",
        "оригинального мира. Автоматизированная установка использует настоящую",
        "серверную обработку предметов, а клиентские кадры — запущенный Minecraft.", "",
        "Авторский экспорт Collision Studio не предоставлен и не применялся.",
        "Контракт docs/COLLISION_IMPORT_HANDOFF.md",
        "и точная таблица состояний сохранены; новый импорт выполняется отдельно.",
        "Исходные дефекты и удаления с координатами находятся в verification.zip,",
        "v12.7/conversion/*.json и source-removal-coordinates.jsonl.gz.", "",
        "Полная карта подготовлена для следующей явной команды владельца.",
        "Исходный ZIP мира и ресурспака не изменён. При будущем импорте разрешение",
        "на пересечение действует только один раз и не сохраняется как привилегия.",
        "После разрушения конфликтного объекта обычная установка снова проверит",
        "коллизии. Подробная инструкция: HANDOFF-RU.md и INSTALL-RU.txt.",
    ]
    return "\n".join(lines) + "\n"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--acceptance", type=Path, default=DOC / "acceptance.json")
    parser.add_argument("--runtime-plan", type=Path, default=RUNTIME_PLAN)
    parser.add_argument("--source-plan", type=Path, default=ROOT / "build/v127-full-conversion-first.sqlite")
    parser.add_argument("--destination", type=Path, default=DEST)
    parser.add_argument("--validate-only", action="store_true")
    args = parser.parse_args()
    accepted, plan = validate(args.acceptance, args.runtime_plan, args.source_plan)
    content_commit = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=REPO, text=True).strip()
    if args.validate_only:
        print(json.dumps({"status": "PASS_PACKAGE_PREFLIGHT", "productionJarSha256": accepted["productionJarSha256"],
                          "sourceArchiveContentCommit": content_commit, "worldConversionAttempted": False}))
        return
    target = args.destination.absolute()
    assert target.is_relative_to(REPO / "releases/Dreamwalker-BB")
    assert not target.exists(), "Never overwrite a historical or partially created release"
    target.parent.mkdir(parents=True, exist_ok=True)
    # A sibling staging directory is retained on failure; no cleanup traverses links.
    stage = Path(tempfile.mkdtemp(prefix=".v12.7-package-", dir=target.parent))
    sources = subprocess.check_output(["git", "ls-files", "-z", "--", "bloodborne complete/"], cwd=REPO).decode("utf8").split("\0")
    source_paths = {REPO / name for name in sources if name}
    source_paths.update(files(DOC))
    source_paths = {path for path in source_paths if not excluded(path) and path.is_file() and ordinary(path)}
    for source in (JAR, JAR.with_name(JAR.stem + "-sources.jar"), BLOCKBENCH, args.acceptance):
        shutil.copyfile(source, stage / source.name)
        assert sha(source) == sha(stage / source.name), source.name
    audits = {}
    audits["fullSource"] = archive(stage / f"dreamwalker-bb-fabric-1.20.1-{VERSION}-full-source.zip",
                                  [(path, "bloodborne complete/" + path.relative_to(ROOT).as_posix()) for path in source_paths])
    evidence = {path for path in files(DOC) if not excluded(path)}
    for item in accepted["evidence"].values():
        evidence.add(evidence_path(item["path"]))
    audits["verification"] = archive(stage / "verification.zip", [(path, path.relative_to(ROOT).as_posix()) for path in evidence])
    audits["conversionPlan"] = archive(stage / "Dreamwalker-BB-V12.7-Original-Conversion-Plan.zip",
                                      [(path, "v127-runtime-conversion/" + path.relative_to(args.runtime_plan).as_posix()) for path in files(args.runtime_plan)]
                                      + [(args.source_plan, args.source_plan.name)])
    for name in ("HANDOFF-RU.md", "INSTALL-RU.txt"):
        shutil.copyfile(DOC / name, stage / name)
    (stage / "REPORT-RU.txt").write_text(report(accepted), encoding="utf-8-sig")
    references = {"schema": "dreamwalker-v127-original-inputs", "sourceArchivesIncluded": False,
                  "sourceArchivesModified": False, "inputs": []}
    for kind, name in INPUT_NAMES.items():
        path = f"Bloodborne-Blocks/reference-inputs/bbmc-v15-v16/{name}"
        references["inputs"].append({"kind": kind, "repositoryPath": path, "sha256": SOURCE_HASHES[kind],
                                    "immutableUrl": f"https://github.com/Stasik1960/Dreamwalker/raw/{content_commit}/{path}"})
    (stage / "ORIGINAL-INPUTS.json").write_text(json.dumps(references, ensure_ascii=False, indent=2) + "\n", encoding="utf8")
    record = {"schema": "dreamwalker-v127-release", "status": "PASS", "productionJarSha256": accepted["productionJarSha256"],
              "baselineCommit": accepted.get("baselineCommit", "0c985dc28e4a38900cce3055ddb5658a71ed95e5"),
              "productionSourceCheckpoint": accepted["productionSourceCheckpoint"], "sourceArchiveContentCommit": content_commit,
              "counts": accepted.get("counts", {}), "checks": accepted["checks"], "archives": audits,
              "originalMapChanged": False, "galleryCreated": False, "authorCollisionExport": "NOT_SUPPLIED_NOT_APPLIED",
              "sourceHashes": SOURCE_HASHES, "sourcePlanStatus": plan["status"],
              "files": [{"name": path.name, "bytes": path.stat().st_size, "sha256": sha(path)} for path in sorted(stage.iterdir()) if path.is_file()]}
    (stage / "release-manifest.json").write_text(json.dumps(record, ensure_ascii=False, indent=2) + "\n", encoding="utf8")
    sums = "".join(f"{sha(path)}  {path.name}\n" for path in sorted(stage.iterdir()) if path.is_file())
    (stage / "SHA256SUMS.txt").write_text(sums, encoding="ascii")
    stage.rename(target)
    print(json.dumps({"status": "PASS_RELEASE_PACKAGED", "destination": str(target), "archives": audits,
                      "productionJarSha256": accepted["productionJarSha256"], "publicationAttempted": False}))


if __name__ == "__main__":
    main()
