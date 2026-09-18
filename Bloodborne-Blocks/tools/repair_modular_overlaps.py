#!/usr/bin/env python3
"""Repair only geometry-proven modular overlaps in an already converted world COPY.

The conservative default is a preview.  ``--apply`` writes eligible cells in the
supplied target world, persists the extended v2 palette once, and writes a full
resolved/unresolved report.  Foreign visible blocks, interactive furniture and
multi-cell retained models are never changed.
"""

from __future__ import annotations

import argparse
import collections
import copy
import json
from pathlib import Path

from build_modular_palette import ASSETS, RES, ROOT, Palette, statekey
from catalog_geometry import applications
from convert_modular_world import load_component, load_palette, set_section, tag_state, unpack_fast
from modular_mesh import alpha_patch, cells_for_app
from world_io import RegionFile, Tag, TAG_BYTE, TAG_COMPOUND, TAG_LIST, block_state_key, compound


NS = "bloodborne_blocks:"
AIR_NAMES = {"minecraft:air", "minecraft:cave_air", "minecraft:void_air"}
TECHNICAL = {"minecraft:barrier", "minecraft:light"}
INTERACTIVE_KINDS = {"door", "model_door", "trapdoor", "gate"}
ORIGIN = (0, 0, 0)


def parse_state(text: str) -> tuple[str, dict[str, str]]:
    if "[" not in text:
        return text, {}
    name, raw = text.split("[", 1)
    raw = raw.removesuffix("]")
    return name, dict(pair.split("=", 1) for pair in raw.split(",") if pair)


def resolved_geometry(geometry: dict, ident: str, key: str) -> dict:
    state = geometry["blocks"][ident]["states"][key]
    return geometry.get("profiles", {}).get(state.get("ref"), state)


def legacy_component(protected: str, definitions: dict, semantics: dict, geometry: dict) -> tuple[dict | None, str]:
    name, supplied = parse_state(protected)
    if not name.startswith(NS) or name == NS + "architecture_part":
        return None, "not_retained_legacy"
    ident = name.removeprefix(NS)
    definition = definitions.get(ident)
    semantic = semantics.get(ident)
    if definition is None or semantic is None:
        return None, "unknown_legacy_definition"
    if definition["kind"] in INTERACTIVE_KINDS or semantic["category"] in {"door", "bench", "container"}:
        return None, "interactive_or_container_legacy"
    state = {**definition["default"], **supplied}
    key = statekey(state)
    if key not in definition["states"]:
        return None, "unmapped_legacy_state"
    try:
        authored = resolved_geometry(geometry, ident, key)
    except KeyError:
        return None, "missing_legacy_geometry"
    geometry_cells = {tuple(map(int, cell.split(","))) for cell in authored.get("cells", {})}
    if not geometry_cells <= {ORIGIN}:
        return None, "multi_cell_legacy_geometry"

    blockstate = json.loads((ASSETS / "blockstates" / f"{ident}.json").read_text(encoding="utf-8-sig"))
    model_cells: dict[tuple[int, int, int], list] = collections.defaultdict(list)
    for choices in applications(blockstate, state):
        if not choices:
            continue
        app = choices[0]
        for cell, polygons in cells_for_app(json.dumps(app, sort_keys=True)).items():
            model_cells[tuple(map(int, cell))].extend(polygons)
    if set(model_cells) - {ORIGIN}:
        return None, "multi_cell_legacy_model"
    polygons = list(model_cells.get(ORIGIN, []))
    safe_native = definition["kind"] in {"stairs", "slab", "pane"} or semantic["category"] == "window"
    if polygons and not safe_native:
        return None, "legacy_type_not_safe_for_static_composition"
    cell = authored.get("cells", {}).get("0,0,0", {})
    collision = copy.deepcopy(cell.get("collision", []))
    luminance = int(definition["states"][key][2])
    return {"polygons": polygons, "collision": collision, "luminance": luminance,
            "semantic": semantic, "source": ident}, "safe_empty" if not polygons else "safe_origin_native"


def module_components(palette: Palette, incoming: list[dict]) -> list[dict]:
    components = []
    for piece in incoming:
        ident, facing = piece["id"], piece["facing"]
        if ident not in palette.blocks:
            raise ValueError(f"unknown incoming module {ident}")
        polygons, collision = load_component(palette, ident, facing)
        components.append({"id": ident, "facing": facing, "polygons": polygons,
                           "collision": collision, "definition": palette.blocks[ident]})
    return components


def strictly_inside(polygons: list[dict], epsilon: float = 1e-6) -> bool:
    vertices = [vertex for polygon in polygons for vertex in polygon["vertices"]]
    return bool(vertices) and all(epsilon < float(vertex[axis]) < 1 - epsilon for vertex in vertices for axis in range(3))


def opaque_dominant(components: list[dict]) -> tuple[list[dict], int]:
    """Drop enclosed geometry only when one opaque full cube proves occlusion."""
    candidates = [component for component in components
                  if component["definition"].get("full_cube") and component["definition"].get("layer") == "solid"
                  and all(all(value >= 250 for value in alpha_patch(polygon).flat) for polygon in component["polygons"])]
    if len(candidates) != 1:
        return components, 0
    dominant = candidates[0]
    others = [component for component in components if component is not dominant]
    if others and all(strictly_inside(component["polygons"]) for component in others):
        return [dominant], len(others)
    return components, 0


def compose(palette: Palette, incoming: list[dict], *, legacy: dict | None = None,
            light: int = 0, source: str) -> tuple[dict, int]:
    components = module_components(palette, incoming)
    components, occluded = opaque_dominant(components)
    polygons = list(legacy["polygons"]) if legacy else []
    collisions = list(legacy["collision"]) if legacy else []
    luminance = max(light, legacy["luminance"] if legacy else 0)
    for component in components:
        polygons.extend(component["polygons"]);collisions.extend(component["collision"])
        luminance = max(luminance, int(component["definition"]["states"]["facing=north"][2]))
    if not polygons:
        raise ValueError("refusing to create an empty overlap replacement")
    if legacy:
        semantic = legacy["semantic"]
    else:
        first = components[0]["definition"]
        category = "ladder" if any(component["definition"]["semantic"] == "ladder" for component in components) else first["semantic"]
        semantic = {"category": category, "ru": "Восстановленная составная секция"}
    result = palette.piece(polygons, collisions, semantic, source, False, luminance)
    return result, occluded


def state_text(result: dict) -> str:
    values = result.get("properties", {})
    suffix = "[" + ",".join(f"{key}={values[key]}" for key in sorted(values)) + "]" if values else ""
    return NS + result["id"] + suffix


def decision(conflict: dict, palette: Palette, definitions: dict, semantics: dict,
             geometry: dict, cache: dict) -> dict:
    protected = conflict["protectedBlock"]
    incoming_key = tuple(sorted((piece["id"], piece["facing"]) for piece in conflict["incomingPieces"]))
    key = protected, incoming_key
    if key in cache:
        return cache[key]
    name, properties = parse_state(protected)
    try:
        if name in TECHNICAL:
            light = int(properties.get("level", 15)) if name == "minecraft:light" else 0
            result, occluded = compose(palette, conflict["incomingPieces"], light=light,
                                       source="world_overlap_technical")
            value = {"result": result, "reason": "technical_cell_replaced", "occludedPieces": occluded}
        elif conflict["category"] == "composition_risk_retained_legacy":
            legacy, reason = legacy_component(protected, definitions, semantics, geometry)
            if legacy is None:
                value = {"reason": reason}
            else:
                result, occluded = compose(palette, conflict["incomingPieces"], legacy=legacy,
                                           source="world_overlap_legacy_" + legacy["source"])
                value = {"result": result, "reason": reason, "occludedPieces": occluded}
        elif conflict["category"] == "removable_orphan_helper_overlap" and name == NS + "architecture_part":
            result, occluded = compose(palette, conflict["incomingPieces"], source="world_overlap_orphan")
            value = {"result": result, "reason": "fill_removed_orphan_if_air", "requiresAir": True,
                     "occludedPieces": occluded}
        else:
            value = {"reason": "protected_visible_or_unverified"}
    except (KeyError, ValueError) as error:
        value = {"reason": "composition_error", "error": str(error)}
    cache[key] = value
    return value


def block_entity_positions(root: dict) -> set[tuple[int, int, int]]:
    result = set()
    for entity in root.get("block_entities", Tag(TAG_LIST, [], TAG_COMPOUND)).value:
        data = compound(entity)
        if all(axis in data for axis in ("x", "y", "z")):
            result.add((data["x"].value, data["y"].value, data["z"].value))
    return result


def repair(target: Path, conflicts_path: Path, report_path: Path, apply: bool) -> dict:
    target = target.resolve();conflicts_path = conflicts_path.resolve();report_path = report_path.resolve()
    if not target.is_dir() or not (target / "level.dat").is_file() or not (target / "region").is_dir():
        raise ValueError("target must be an existing copied world with level.dat and region/")
    source_report = json.loads(conflicts_path.read_text(encoding="utf-8"))
    if source_report.get("localCoordinates") is not True or not isinstance(source_report.get("conflicts"), list):
        raise ValueError("unexpected overlap conflict report format")
    definitions_data = json.loads((RES / "bloodborne_blocks/definitions.json").read_text(encoding="utf-8-sig"))
    definitions = {definition["id"]: definition for definition in definitions_data["blocks"]}
    semantics = json.loads((ROOT / "docs/semantic-catalog-v2.json").read_text(encoding="utf-8-sig"))
    if "blocks" in semantics:
        semantics = semantics["blocks"]
    geometry = json.loads((RES / "bloodborne_blocks/geometry.json").read_text(encoding="utf-8-sig"))
    palette = load_palette();decision_cache = {};planned = []
    by_region = collections.defaultdict(lambda: collections.defaultdict(list))
    for index, conflict in enumerate(source_report["conflicts"]):
        plan = decision(conflict, palette, definitions, semantics, geometry, decision_cache)
        if "result" not in plan:
            planned.append((index, conflict, plan));continue
        x, _, z = conflict["position"];cx, cz = x // 16, z // 16
        by_region[(cx // 32, cz // 32)][(cx, cz)].append((index, conflict, plan))

    resolved = [];unresolved = [None] * len(source_report["conflicts"])
    for index, conflict, plan in planned:
        unresolved[index] = {**conflict, "repairReason": plan["reason"], **({"error": plan["error"]} if "error" in plan else {})}
    changed_chunks = changed_regions = 0
    for (rx, rz), chunks in sorted(by_region.items()):
        path = target / "region" / f"r.{rx}.{rz}.mca"
        if not path.is_file():
            for entries in chunks.values():
                for index, conflict, plan in entries:unresolved[index] = {**conflict, "repairReason": "missing_target_region"}
            continue
        region = RegionFile.open(path);region_changed = False
        for (cx, cz), entries in sorted(chunks.items()):
            stored = region.get_chunk(cx % 32, cz % 32)
            if stored is None:
                for index, conflict, plan in entries:unresolved[index] = {**conflict, "repairReason": "missing_target_chunk"}
                continue
            nbt = stored.nbt();root = compound(nbt.root)
            if root.get("xPos").value != cx or root.get("zPos").value != cz:
                raise ValueError(f"chunk coordinate mismatch in {path.name}: expected {cx},{cz}")
            sections = {compound(section)["Y"].value: compound(section) for section in root.get("sections", Tag(TAG_LIST, [], TAG_COMPOUND)).value}
            loaded = {};entities = block_entity_positions(root);chunk_changed = False
            for index, conflict, plan in entries:
                x, y, z = conflict["position"];section = sections.get(y // 16)
                if section is None or "block_states" not in section:
                    unresolved[index] = {**conflict, "repairReason": "missing_target_section"};continue
                sy = y // 16
                if sy not in loaded:
                    states = compound(section["block_states"]);pal = list(states["palette"].value);array = unpack_fast(states)
                    loaded[sy] = states, pal, array, {block_state_key(entry): i for i, entry in enumerate(pal)}
                states, pal, array, indices = loaded[sy]
                local = (y % 16) * 256 + (z % 16) * 16 + x % 16
                current = pal[int(array[local])];current_text = block_state_key(current)
                expected = conflict["protectedBlock"]
                if plan.get("requiresAir"):
                    if compound(current)["Name"].value not in AIR_NAMES or (x, y, z) in entities:
                        unresolved[index] = {**conflict, "repairReason": "orphan_target_not_clean_air", "actualBlock": current_text};continue
                elif current_text != expected:
                    unresolved[index] = {**conflict, "repairReason": "target_state_mismatch", "actualBlock": current_text};continue
                result = plan["result"];new_text = state_text(result)
                if apply:
                    palette_index = indices.get(new_text)
                    if palette_index is None:
                        palette_index = len(pal);indices[new_text] = palette_index;pal.append(tag_state(result["id"], result["properties"]))
                    array[local] = palette_index;chunk_changed = True
                resolved.append({"position": conflict["position"], "oldState": current_text, "newState": new_text,
                                 "repairReason": plan["reason"], "occludedPieces": plan.get("occludedPieces", 0),
                                 "incomingPieces": conflict["incomingPieces"]})
            if apply and chunk_changed:
                for sy, (states, pal, array, _) in loaded.items():
                    set_section(states, pal, array)
                invalidate_chunk_lighting(root);root.pop("Heightmaps", None)
                region.set_chunk(stored.x, stored.z, nbt, timestamp=stored.timestamp);chunk_changed = region_changed = True;changed_chunks += 1
        if apply and region_changed:
            region.save(path);changed_regions += 1

    unresolved_rows = [row for row in unresolved if row is not None]
    if len(resolved) + len(unresolved_rows) != len(source_report["conflicts"]):
        raise AssertionError("every conflict must be resolved or unresolved")
    if apply:
        palette.save()
    counts = collections.Counter(row["repairReason"] for row in resolved + unresolved_rows)
    report = {"format": "bloodborne-world-overlap-repair-v2", "applied": apply,
              "target": str(target), "sourceConflictFile": str(conflicts_path),
              "counts": {"input": len(source_report["conflicts"]), "resolved": len(resolved),
                         "unresolved": len(unresolved_rows), "decisionCache": len(decision_cache),
                         "changedRegions": changed_regions, "changedChunks": changed_chunks,
                         **dict(sorted(counts.items()))},
              "safety": {"foreignVisibleStatesChanged": 0, "interactiveLegacyChanged": 0,
                         "multiCellLegacyChanged": 0, "paletteSavedOnce": bool(apply)},
              "resolved": resolved, "unresolved": unresolved_rows}
    report_path.parent.mkdir(parents=True, exist_ok=True)
    report_path.write_text(json.dumps(report, ensure_ascii=False, separators=(",", ":")) + "\n", encoding="utf-8")
    return report


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("target", type=Path, help="already converted world copy to inspect/repair")
    parser.add_argument("--conflicts", type=Path, default=ROOT / "docs/world-overlap-conflicts-v2.json")
    parser.add_argument("--report", type=Path, default=ROOT / "docs/world-overlap-repair-v2.json")
    parser.add_argument("--apply", action="store_true", help="write safe replacements and persist the palette")
    args = parser.parse_args();result = repair(args.target, args.conflicts, args.report, args.apply)
    print(json.dumps(result["counts"], ensure_ascii=False))


if __name__ == "__main__":
    main()
