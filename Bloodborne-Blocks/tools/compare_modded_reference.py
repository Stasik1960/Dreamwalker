"""Compare converted MODDED ledger origins with the original vanilla oracle.

This is a read-only evidence tool.  It never converts the vanilla reference,
never changes either world and never proposes coordinate corrections.  Only
regions containing coordinates already present in the conversion ledger are
read from the verified ZIP.
"""
from __future__ import annotations

import argparse
import gzip
import hashlib
import json
import re
import zipfile
from pathlib import Path
from typing import Any

from convert_logical_world import DEFAULT_RESOURCES, add, as_tag_state
from logical_contract_v2 import direct_rules
from modded_world_adapter import compile_modded_rules
from world_io import TAG_LIST, RegionFile, Tag, block_state_key, compound, section_blocks


RAW_RULE = re.compile(r"Contract V2 raw rule (\d+)$")
ROOT = Path(__file__).resolve().parents[1]
SOURCE_INDEX = ROOT / "docs/source-assembly-carrier-index.json.gz"


def _sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def _reference_dimension_mapping(reference_sha256: str) -> tuple[dict[str, str], dict[str, Any] | None]:
    """Return the archived dimension move only for its exact source SHA."""
    if not SOURCE_INDEX.is_file():
        return {}, None
    raw = SOURCE_INDEX.read_bytes()
    evidence = json.loads(gzip.decompress(raw))
    source = evidence.get("source", {})
    if source.get("sha256") != reference_sha256:
        return {}, None
    dimensions = set()
    for member in evidence.get("regions", []):
        normalized = str(member).replace("\\", "/")
        marker = "/dimensions/"
        if marker not in "/" + normalized or "/region/" not in normalized:
            continue
        suffix = ("/" + normalized).split(marker, 1)[1].split("/region/", 1)[0].split("/")
        if len(suffix) >= 2:
            dimensions.add(suffix[0] + ":" + "/".join(suffix[1:]))
    if len(dimensions) != 1:
        raise ValueError("frozen source index does not prove one reference dimension")
    reference_dimension = next(iter(dimensions))
    return {"minecraft:overworld": reference_dimension}, {
        "path": str(SOURCE_INDEX),
        "sha256": hashlib.sha256(raw).hexdigest(),
        "referenceSourceSha256": source["sha256"],
        "basis": "all indexed source carrier regions belong to one archived custom dimension",
    }


def _region_relative(dimension: str, region_x: int, region_z: int) -> str:
    filename = f"r.{region_x}.{region_z}.mca"
    if dimension == "minecraft:overworld":
        return f"region/{filename}"
    if dimension == "minecraft:the_nether":
        return f"DIM-1/region/{filename}"
    if dimension == "minecraft:the_end":
        return f"DIM1/region/{filename}"
    if ":" not in dimension:
        raise ValueError(f"invalid dimension id: {dimension}")
    namespace, path = dimension.split(":", 1)
    return f"dimensions/{namespace}/{path}/region/{filename}"


class LazyReference:
    def __init__(self, archive: Path):
        self.archive = zipfile.ZipFile(archive)
        self.members: dict[str, list[str]] = {}
        for info in self.archive.infolist():
            normalized = info.filename.replace("\\", "/").strip("/")
            if "/region/r." in "/" + normalized and normalized.endswith(".mca"):
                for marker in ("dimensions/", "DIM-1/region/", "DIM1/region/", "region/"):
                    index = normalized.find(marker)
                    if index >= 0:
                        self.members.setdefault(normalized[index:], []).append(info.filename)
                        break
        self.regions: dict[str, RegionFile | None] = {}
        self.chunks: dict[tuple[str, int, int], Any | None] = {}

    def close(self) -> None:
        self.archive.close()

    def _region(self, relative: str) -> RegionFile | None:
        if relative not in self.regions:
            choices = self.members.get(relative, [])
            if len(choices) > 1:
                raise ValueError(f"reference ZIP has duplicate region path {relative}")
            self.regions[relative] = RegionFile(self.archive.read(choices[0])) if choices else None
        return self.regions[relative]

    def state(self, dimension: str, point: tuple[int, int, int]) -> tuple[str, str | None]:
        chunk_x, chunk_z = point[0] // 16, point[2] // 16
        key = (dimension, chunk_x, chunk_z)
        if key not in self.chunks:
            relative = _region_relative(dimension, chunk_x // 32, chunk_z // 32)
            region = self._region(relative)
            stored = None if region is None else region.get_chunk(chunk_x % 32, chunk_z % 32)
            self.chunks[key] = None if stored is None else compound(stored.nbt().root)
        root = self.chunks[key]
        if root is None:
            return "MISSING", None
        sections = root.get("sections")
        if sections is None:
            return "FOUND", "minecraft:air"
        if sections.type != TAG_LIST:
            return "MISSING", None
        section_y = point[1] // 16
        section = next((entry for entry in sections.value
                        if int(compound(entry).get("Y", Tag(3, -10**9)).value) == section_y), None)
        if section is None:
            return "FOUND", "minecraft:air"
        unpacked = section_blocks(section)
        if unpacked is None:
            return "FOUND", "minecraft:air"
        palette, indices = unpacked
        index = (point[1] & 15) * 256 + (point[2] & 15) * 16 + (point[0] & 15)
        return "FOUND", block_state_key(palette[indices[index]])


def compare(report: dict[str, Any], reference_zip: Path, resources: Path, inventory: Path,
            expected_reference_sha256: str) -> dict[str, Any]:
    reference_zip = reference_zip.resolve()
    actual_sha = _sha256_file(reference_zip)
    if actual_sha != expected_reference_sha256.lower():
        raise ValueError("original reference ZIP SHA-256 mismatch")
    dimension_mapping, dimension_evidence = _reference_dimension_mapping(actual_sha)
    compiled, _defaults, diagnostics = compile_modded_rules(resources, inventory)
    raw_rules, _raw_defaults = direct_rules(resources)
    declared_inventory = (report.get("mappingDiagnostics") or {}).get("inventorySha256")
    if declared_inventory and declared_inventory != diagnostics["inventorySha256"]:
        raise ValueError("conversion report used a different inventory")

    reader = LazyReference(reference_zip)
    rows = []
    try:
        for index, entry in enumerate(report.get("ledger", [])):
            number = entry.get("rule")
            if type(number) is not int or not 0 <= number < len(compiled):
                raise ValueError(f"ledger entry {index} has unknown compiled rule")
            compiled_rule = compiled[number]
            reference = entry.get("originalReference")
            if reference != compiled_rule.source_reference:
                raise ValueError(f"ledger entry {index} source reference differs from compiled rule")
            base = {
                "ledgerIndex": index,
                "dimension": entry["dimension"],
                "referenceDimension": dimension_mapping.get(entry["dimension"], entry["dimension"]),
                "origin": entry["origin"],
                "targetRoot": entry.get("targetRoot"),
                "conversionDelta": entry.get("delta"),
                "sourceReference": reference,
                "coordinateDecision": "UNCHANGED_REFERENCE_ONLY",
            }
            match = RAW_RULE.search(reference or "")
            if match is None:
                rows.append({**base, "status": "NOT_APPLICABLE", "reason": "no raw vanilla source rule",
                             "referenceChecks": []})
                continue
            raw_number = int(match.group(1))
            if not 0 <= raw_number < len(raw_rules) or raw_rules[raw_number].number != raw_number:
                raise ValueError(f"ledger entry {index} names unknown raw rule {raw_number}")
            raw_rule = raw_rules[raw_number]
            origin = tuple(int(value) for value in entry["origin"])
            checks = []
            for piece in (raw_rule.source,) + raw_rule.members:
                point = add(origin, piece.offset)
                found, actual = reader.state(base["referenceDimension"], point)
                expected = block_state_key(as_tag_state(piece.state))
                status = "MISSING" if found == "MISSING" else "MATCH" if actual == expected else "MISMATCH"
                checks.append({"position": list(point), "expected": expected, "actual": actual, "status": status})
            status = ("MISSING" if any(row["status"] == "MISSING" for row in checks) else
                      "MISMATCH" if any(row["status"] == "MISMATCH" for row in checks) else "MATCH")
            rows.append({**base, "rawRule": raw_number, "status": status, "referenceChecks": checks})
    finally:
        reader.close()

    statuses = {name: sum(row["status"] == name for row in rows)
                for name in ("MATCH", "MISMATCH", "MISSING", "NOT_APPLICABLE")}
    component_statuses = {name: sum(check["status"] == name for row in rows for check in row["referenceChecks"])
                          for name in ("MATCH", "MISMATCH", "MISSING")}
    return {
        "format": "bloodborne-modded-reference-comparison-v1",
        "reference": {"path": str(reference_zip), "sha256": actual_sha, "readOnly": True},
        "dimensionMapping": {"mappings": dimension_mapping, "evidence": dimension_evidence},
        "conversionInput": report.get("source"),
        "summary": {"entries": len(rows), **{name.lower(): count for name, count in statuses.items()},
                    "componentChecks": component_statuses,
                    "regionsLoaded": sum(region is not None for region in reader.regions.values()),
                    "missingRegions": sum(region is None for region in reader.regions.values()),
                    "chunksRequested": len(reader.chunks),
                    "coordinateCorrectionsApplied": 0},
        "entries": rows,
    }


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("report", type=Path)
    parser.add_argument("reference_zip", type=Path)
    parser.add_argument("--resources", type=Path, default=DEFAULT_RESOURCES)
    parser.add_argument("--inventory", type=Path, required=True)
    parser.add_argument("--expected-reference-sha256", required=True)
    parser.add_argument("--result", type=Path, required=True)
    args = parser.parse_args()
    result = compare(json.loads(args.report.read_text(encoding="utf-8")), args.reference_zip,
                     args.resources.resolve(), args.inventory.resolve(), args.expected_reference_sha256)
    args.result.parent.mkdir(parents=True, exist_ok=True)
    args.result.write_text(json.dumps(result, ensure_ascii=False, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(json.dumps(result["summary"], ensure_ascii=False))


if __name__ == "__main__":
    main()
