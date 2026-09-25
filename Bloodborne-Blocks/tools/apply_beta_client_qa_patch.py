"""Apply the narrow 2.1.0-beta.1 client-QA patch to the Agony checkpoint.

This compiler reads the current 49-family production payload in place.  It
does not restore an older palette, scan a world, or touch unrelated families.
"""
from __future__ import annotations

import argparse
import copy
import gzip
import hashlib
import json
from pathlib import Path

from apply_agony_patch import FACINGS, STATUES, bounds, box, key, props, publish_models, read, rotate, write
from sync_reviewed_geometry import profile


ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "src/main/resources"
LOGICAL = RES / "bloodborne_blocks/logical"
DOC = ROOT / "docs/beta-client-qa"
PATCHED = {"o_ladder_01", "o_ladder_03", "o_shuttered_window", "o_lantern", *STATUES}


def state_mesh(definition, family, state_key, polygons, meshes, *, collision=None,
               selection=None, footprint=None, offset=None):
    mesh_id = "beta_" + hashlib.sha256(
        json.dumps(polygons, sort_keys=True, separators=(",", ":")).encode()
    ).hexdigest()[:24]
    meshes[mesh_id] = {"polygons": polygons}
    definition["models"][state_key] = mesh_id
    state = family["states"][state_key]
    state["render_mesh"] = {"id": mesh_id, "bounds": bounds(polygons), "offset": offset or [0, 0, 0]}
    if collision is not None:
        state["collision_footprint"] = {"boxes": collision}
    if selection is not None:
        state["selection_footprint"] = {"boxes": selection}
    if footprint is not None:
        state["interaction_footprint"] = {"cells": footprint}


def split_to_cells(primitive, cells):
    result = []
    for x, y, z in sorted(tuple(int(value) for value in cell) for cell in cells):
        clipped = [max(primitive[0], x), max(primitive[1], y), max(primitive[2], z),
                   min(primitive[3], x + 1), min(primitive[4], y + 1), min(primitive[5], z + 1)]
        if all(clipped[index] < clipped[index + 3] for index in range(3)):
            result.append(clipped)
    return result


def reflect_ladder(polygons):
    reflected = []
    for polygon in polygons:
        vertices = [[vertex[0], vertex[1], round(1 - vertex[2], 6), *vertex[3:]]
                    for vertex in reversed(polygon["vertices"])]
        reflected.append({**polygon, "vertices": vertices})
    return reflected


def merge_patterns(*groups):
    result = []
    seen = set()
    for group in groups:
        for pattern in group or []:
            marker = json.dumps(pattern, sort_keys=True, separators=(",", ":"))
            if marker not in seen:
                seen.add(marker)
                result.append(copy.deepcopy(pattern))
    return result


def patch_ladders(definitions, families, meshes):
    landing = definitions["o_ladder_01"]
    landing_family = families["o_ladder_01"]
    landing_family["collision_policy"] = "DECK"
    landing_family["collision_justification"] = (
        "Nine cell-local slices cover the complete authored walkable deck; vertical rails remain render-only."
    )
    for state in landing_family["states"].values():
        cells = state["interaction_footprint"]["cells"]
        primitives = state["collision_footprint"]["boxes"]
        if len(primitives) != 1:
            raise ValueError("Agony landing baseline must contain exactly one deck primitive")
        state["collision_footprint"] = {"boxes": split_to_cells(primitives[0], cells)}

    ladder = definitions["o_ladder_03"]
    ladder_family = families["o_ladder_03"]
    north_key = key({**ladder["default"], "facing": "north", "visual": "base"})
    source = copy.deepcopy(meshes[ladder["models"][north_key]]["polygons"])
    reflected = reflect_ladder(source)
    ladder_family["collision_policy"] = "TWO_BOX"
    ladder_family["collision_justification"] = (
        "Two vertical cell-local slices form one continuous thin climbing plane on the inverted support side."
    )
    for state_key in list(ladder_family["states"]):
        yaw = FACINGS.index(props(state_key)["facing"]) * 90
        polygons = rotate(reflected, yaw)
        collision = [box([0, -1, 0, 1, 0, .125], yaw), box([0, 0, 0, 1, 1, .125], yaw)]
        state_mesh(ladder, ladder_family, state_key, polygons, meshes,
                   collision=collision, selection=[bounds(polygons)], footprint=[[0, -1, 0], [0, 0, 0]])


def patch_window(definitions, families, meshes):
    definition = definitions["o_shuttered_window"]
    family = families["o_shuttered_window"]
    old_definition, old_states = copy.deepcopy(definition), copy.deepcopy(family["states"])
    definition["properties"].pop("embedded", None)
    definition["default"].pop("embedded", None)
    definition.setdefault("placement_properties", {}).pop("embedded", None)
    definition["states"], definition["models"], family["states"] = {}, {}, {}
    family["collision_policy"] = "TWO_BOX"
    family["collision_justification"] = (
        "Two cell-local slices close the two-block-high window opening; shutters are render-only when open."
    )
    family["placement_notes"] = (
        "Ordinary adjacent-air placement. Never replaces or mutates the clicked background or neighboring blocks."
    )
    for facing in FACINGS:
        yaw = FACINGS.index(facing) * 90
        collision = [box([0, 0, 0, 1, 1, .125], yaw), box([0, 1, 0, 1, 2, .125], yaw)]
        for opened in ("false", "true"):
            for visual in ("base", "alt"):
                state_key = key({"facing": facing, "open": opened, "visual": visual})
                source_key = key({"embedded": "false", "facing": facing, "open": opened, "visual": visual})
                embedded_key = key({"embedded": "true", "facing": facing, "open": opened, "visual": visual})
                source_state = copy.deepcopy(old_states[source_key])
                source_state["migration_source_pattern"] = merge_patterns(
                    old_states[source_key].get("migration_source_pattern"),
                    old_states[embedded_key].get("migration_source_pattern"),
                )
                family["states"][state_key] = source_state
                definition["states"][state_key] = copy.deepcopy(old_definition["states"][source_key])
                polygons = copy.deepcopy(meshes[old_definition["models"][source_key]]["polygons"])
                state_mesh(definition, family, state_key, polygons, meshes,
                           collision=collision, selection=[bounds(polygons)], footprint=[[0, 0, 0], [0, 1, 0]])


def patch_lantern_art(definitions, families, meshes):
    lantern = definitions["o_lantern"]
    lantern_family = families["o_lantern"]
    old_models = copy.deepcopy(lantern["models"])
    for state_key in list(lantern_family["states"]):
        offset = copy.deepcopy(lantern_family["states"][state_key]["render_mesh"].get("offset", [0, 0, 0]))
        polygons = copy.deepcopy(meshes[old_models[state_key]]["polygons"])
        if props(state_key)["lit"] == "false":
            polygons = [polygon for polygon in polygons if not polygon["texture"].endswith("/flower_pot")]
        state_mesh(lantern, lantern_family, state_key, polygons, meshes, offset=offset)

    lamp_polygons = {
        mode: meshes[lantern["models"][key({"facing": "north", "lit": "true" if mode == "lit" else "false", "visual": "base"})]]["polygons"]
        for mode in ("unlit", "lit")
    }
    for ident in STATUES:
        definition = definitions[ident]
        family = families[ident]
        old_models = copy.deepcopy(definition["models"])
        hand = family["hand_lantern"]
        socket, scale, pivot = hand["position"], hand["scale"], hand["source_pivot"]
        for state_key in list(family["states"]):
            state_props = props(state_key)
            mode = state_props["hand_lantern"]
            if mode == "none":
                continue
            base_key = key({**state_props, "hand_lantern": "none"})
            base_state = family["states"][base_key]
            base = rotate(copy.deepcopy(meshes[old_models[base_key]]["polygons"]),
                          offset=base_state["render_mesh"].get("offset", [0, 0, 0]))
            mounted = []
            for polygon in lamp_polygons[mode]:
                vertices = [[socket[index] + (vertex[index] - pivot[index]) * scale for index in range(3)] + vertex[3:]
                            for vertex in polygon["vertices"]]
                mounted.append({**polygon, "vertices": vertices})
            polygons = base + rotate(mounted, FACINGS.index(state_props["facing"]) * 90)
            state_mesh(definition, family, state_key, polygons, meshes)


def apply():
    definitions_file = read(LOGICAL / "definitions.json")
    contracts_file = read(LOGICAL / "contracts-v2.json")
    geometry = read(LOGICAL / "geometry.json")
    meshes = read(LOGICAL / "meshes.json.gz")
    definitions = {row["id"]: row for row in definitions_file["blocks"]}
    families = {row["id"]: row for row in contracts_file["families"]}
    if set(definitions) != set(families) or len(definitions) != 49:
        raise ValueError("beta patch requires the 49-family Agony checkpoint")

    # Rehydrate only the explicit target families from the immutable beta
    # baseline, making this compiler deterministic and safe to rerun.
    baseline = read(DOC / "baseline-fingerprints.json.gz")["fingerprints"]["families"]
    for ident in PATCHED:
        core = baseline[ident]["core"]
        definitions[ident] = copy.deepcopy(core["definition"])
        families[ident] = copy.deepcopy(core["contract"])
        geometry["blocks"][ident] = copy.deepcopy(core["geometry"])
        for evidence in core["state_evidence"].values():
            meshes[evidence["mesh_id"]] = copy.deepcopy(evidence["mesh"])

    patch_ladders(definitions, families, meshes)
    patch_window(definitions, families, meshes)
    patch_lantern_art(definitions, families, meshes)

    for ident in sorted(PATCHED):
        geometry["blocks"][ident] = profile(families[ident])
        publish_models(definitions[ident], families[ident], meshes, definitions_file.get("emissive_textures", {}))

    definitions_file["blocks"] = [definitions[ident] for ident in sorted(definitions)]
    contracts_file["families"] = [families[ident] for ident in sorted(families)]
    used = {mesh for definition in definitions.values() for mesh in definition["models"].values()}
    write(LOGICAL / "definitions.json", definitions_file)
    write(LOGICAL / "contracts-v2.json", contracts_file)
    write(LOGICAL / "geometry.json", geometry)
    (LOGICAL / "meshes.json.gz").write_bytes(gzip.compress(
        json.dumps({mesh: meshes[mesh] for mesh in sorted(used)}, separators=(",", ":")).encode(), mtime=0
    ))
    slots = read(LOGICAL / "visual-slots.json")
    slots["families"] = [{"id": ident, "states": len(definition["states"]),
                           "base_states": len(definition["states"]) // 2}
                          for ident, definition in sorted(definitions.items())]
    write(LOGICAL / "visual-slots.json", slots)

    allowlist = {"families": sorted(PATCHED), "display_names": []}
    write(DOC / "allowlist.json", allowlist)
    agony_allowlist = read(ROOT / "docs/agony-patch/allowlist.json")
    agony_allowlist["families"] = sorted(set(agony_allowlist.get("families", [])) | PATCHED)
    write(ROOT / "docs/agony-patch/allowlist.json", agony_allowlist)
    write(DOC / "patch-report.json", {
        "schemaVersion": 1,
        "baseline_commit": "733c8323e455adb4ae811aa53dff69239ac2720f",
        "patched_families": sorted(PATCHED),
        "source_asset_evidence": {
            "o_lantern_unlit": "source resource pack assets/minecraft/models/block/lantern_0.json (structural polygons, no flame planes)",
            "o_lantern_lit": "source resource pack assets/minecraft/models/block/lantern.json (same structure plus flower_pot flame planes)",
        },
        "world_scan": False,
        "global_palette_reauthored": False,
    })
    return {"families": len(definitions), "states": sum(len(row["states"]) for row in definitions.values()),
            "meshes": len(used), "patched": sorted(PATCHED)}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apply", action="store_true", help="write the narrow patch")
    args = parser.parse_args()
    if not args.apply:
        raise SystemExit("refusing to write without --apply")
    print(json.dumps(apply(), ensure_ascii=False))


if __name__ == "__main__":
    main()
