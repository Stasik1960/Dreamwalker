"""Read-only backing/membership evidence for source ladder candidate cells.

No conversion rule is produced. Adjacent vanilla ladders are evidence of a
compound visual/physics construction, not automatically consumable members.
"""
from __future__ import annotations

from collections import Counter
import hashlib
import json
from pathlib import Path
import struct
import zipfile
import zlib
from world_io import RegionFile, Tag, compound, section_blocks, block_state_key

ROOT = Path(__file__).resolve().parents[1]
FACING = {"north": (0, 0, -1), "east": (1, 0, 0), "south": (0, 0, 1), "west": (-1, 0, 0)}
OPPOSITE = {"north": "south", "south": "north", "east": "west", "west": "east"}


def source_ladder_opacity():
    path = Path("C:/Users/vakir/Downloads/bbmc_v15_resource (1).zip")
    texture = "assets/minecraft/textures/block/ladder.png"
    with zipfile.ZipFile(path) as archive:
        data = archive.read(texture)
        native_models = {name: name in archive.namelist() for name in ("assets/minecraft/blockstates/ladder.json", "assets/minecraft/models/block/ladder.json")}
    chunks = {}; cursor = 8
    while cursor < len(data):
        length = struct.unpack(">I", data[cursor:cursor + 4])[0]
        kind = data[cursor + 4:cursor + 8].decode("ascii")
        chunks[kind] = chunks.get(kind, b"") + data[cursor + 8:cursor + 8 + length]
        cursor += length + 12
    header = struct.unpack(">IIBBBBB", chunks["IHDR"])
    raw = zlib.decompress(chunks["IDAT"])
    # An exact source-specific proof, not a general PNG decoder. All decoded
    # filter and pixel bytes are zero; this proves every RGBA alpha is zero.
    transparent = header == (16, 16, 8, 6, 0, 0, 0) and len(raw) == 16 * (1 + 16 * 4) and not any(raw)
    return {"resource": texture, "sha256": hashlib.sha256(data).hexdigest(), "byte_length": len(data),
            "PNG_IHDR": list(header), "decoded_bytes": len(raw), "decoded_all_zero": not any(raw),
            "fully_transparent_RGBA": transparent, "native_blockstate_model_overrides": native_models,
            "interpretation": "Adjacent vanilla ladder supplies invisible native climbing; visible ladder artwork is the beehive model." if transparent else "Requires image inspection"}


def add(left, right):
    return tuple(left[index] + right[index] for index in range(3))


def states_from_fixture(path):
    states = {}
    with zipfile.ZipFile(path) as archive:
        for name in archive.namelist():
            if not name.startswith("region/") or not name.endswith(".mca"):
                continue
            for chunk in RegionFile(archive.read(name)).chunks():
                root = compound(chunk.nbt().root)
                cx, cz = root["xPos"].value, root["zPos"].value
                for section in root.get("sections", Tag(9, [], 10)).value:
                    block_data = section_blocks(section)
                    if block_data is None:
                        continue
                    palette, indices = block_data
                    sy = compound(section)["Y"].value
                    rows = []
                    for state in palette:
                        fields = compound(state)
                        rows.append((fields["Name"].value, {key: value.value for key, value in compound(fields.get("Properties", Tag(10, {}))).items()}, block_state_key(state)))
                    for index, palette_index in enumerate(indices):
                        pos = (cx * 16 + index % 16, sy * 16 + index // 256, cz * 16 + (index // 16) % 16)
                        if rows[palette_index][0] not in ("minecraft:air", "minecraft:cave_air", "minecraft:void_air"):
                            states[pos] = rows[palette_index]
    return states


def audit():
    fixture = ROOT / "build/prototype/Source-coordinate-fixture.zip"
    states = states_from_fixture(fixture)
    candidates = []
    for pos, (block, properties, key) in sorted(states.items()):
        if block != "minecraft:beehive" or properties.get("honey_level") != "1":
            continue
        facing = properties["facing"]
        support_side = OPPOSITE[facing]
        alleged_support = add(pos, FACING[support_side])
        support = states.get(alleged_support, ("minecraft:air", {}, "minecraft:air"))
        pair = support[0] == "minecraft:ladder" and support[1].get("facing") == support_side
        candidates.append({
            "dimension": "minecraft:overworld", "source_visual_cell": list(pos), "source_visual_state": key,
            "same_anchor_native_ladder_facing": facing, "same_anchor_required_backing": list(alleged_support),
            "backing_state": support[2], "backing_is_vanilla_ladder": support[0] == "minecraft:ladder",
            "adjacent_ladder_matches_source_plane": pair,
            "source_member_inference": "COMPOUND_CANDIDATE" if pair else "UNRESOLVED",
            "safe_same_anchor_native_placement": False if support[0] in ("minecraft:ladder", "minecraft:air") else "NEEDS_RUNTIME_SOLIDITY_CHECK",
            "consuming_adjacent_ladder_authorized_by_evidence": False,
        })
    full_audit = json.loads((ROOT / "reports/WORLD_AUDIT.json").read_text(encoding="utf-8"))
    full_count = sum(row["count"] for row in full_audit["states"] if row["state"].startswith("minecraft:beehive[") and "honey_level=1" in row["state"])
    counts = Counter(str(row["safe_same_anchor_native_placement"]) for row in candidates)
    result = {
        "schema": "dreamwalker-source-ladder-context-v1", "status": "SOURCE_ASSEMBLY_SUPPORT_CONTRACT_REQUIRED",
        "source_fixture": str(fixture), "fixture_candidates": len(candidates), "full_world_visual_candidate_cell_count": full_count,
        "fixture_covers_all_honey_level1_cells_by_count": full_count == len(candidates),
        "source_count_is_final_object_count": False,
        "backing_outcomes": dict(counts), "matching_adjacent_ladder_pairs": sum(row["adjacent_ladder_matches_source_plane"] for row in candidates),
        "source_vanilla_ladder_render": source_ladder_opacity(),
        "membership_rule": "No member consumed. Exact direction/position evidence recorded; association still needs a reviewed contract.",
        "runtime_new_placement": "Valid supported one-cell ladder prototype remains independent; source same-anchor replacement is not validated.",
        "candidates": candidates,
    }
    output = ROOT / "reports/LADDER_SOURCE_CONTEXT.json"
    output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({key: value for key, value in result.items() if key not in ("candidates", "source_fixture")}, ensure_ascii=False))


if __name__ == "__main__":
    audit()
