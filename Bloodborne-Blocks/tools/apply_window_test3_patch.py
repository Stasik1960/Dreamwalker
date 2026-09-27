"""Apply the bounded TEST3 shutter-window mounting correction.

The source mesh remains untouched.  This compiler adjusts only the generated
window contract, its physical mask, and the legacy per-cell geometry profile.
It is deliberately independent of city conversion and of all other families.
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path

from sync_reviewed_geometry import profile


ROOT = Path(__file__).resolve().parents[1]
LOGICAL = ROOT / "src/main/resources/bloodborne_blocks/logical"
WINDOW = "o_shuttered_window"
FACING_VECTORS = {
    "north": (0.0, -1.0), "east": (1.0, 0.0),
    "south": (0.0, 1.0), "west": (-1.0, 0.0),
}


def state_properties(key: str) -> dict[str, str]:
    return dict(part.split("=", 1) for part in key.split(","))


def translate(box: list[float], dx: float, dy: float, dz: float) -> list[float]:
    return [round(box[0] + dx, 6), round(box[1] + dy, 6), round(box[2] + dz, 6),
            round(box[3] + dx, 6), round(box[4] + dy, 6), round(box[5] + dz, 6)]


def full_collision_boxes() -> list[list[float]]:
    """The approved two-cell physical volume, independent of decorative mesh bounds."""
    return [[0.0, 0.0, 0.0, 1.0, 1.0, 1.0], [0.0, 1.0, 0.0, 1.0, 2.0, 1.0]]


def patch_family(family) -> None:
    """Apply only the explicit mounting fields, preserving all source identities."""
    if family['id'] != WINDOW:
        raise ValueError('window-only patch')
    # Raw bounds have independently been checked against the actual stationary
    # sill polygons. Measure AFTER the existing transform: the current candidate
    # already lands at y=0, so its family-wide delta is exactly zero.
    current_sill_planes = {round(state['render_mesh']['bounds'][1] + state['render_mesh']['offset'][1], 6)
                           for state in family['states'].values()}
    if len(current_sill_planes) != 1:
        raise ValueError('window poses disagree on the existing sill plane')
    delta_y = -next(iter(current_sill_planes))
    for key, state in family["states"].items():
        facing = state_properties(key)["facing"]
        axis_x, axis_z = FACING_VECTORS[facing]
        # The static frame's rearmost plane is .25 from its authored origin.
        # Moving it .75 opposite to the exposed face places that plane exactly
        # on the ordinary backing block's exposed face.  The sill's measured
        # lower edge is -.875, so the same fixed vertical offset lands it on
        # the full-block support plane.
        render_dx, render_dz = -.75 * axis_x, -.75 * axis_z
        render_y = round(state['render_mesh']['offset'][1] + delta_y, 6)
        state["render_mesh"]["offset"] = [render_dx, render_y, render_dz]
        # Selection and collision deliberately use the approved two-cell volume.
        # The decorative frame and 22.5-degree shutters remain render-only.
        state["selection_footprint"] = {"boxes": [[0.0, 0.0, 0.0, 1.0, 2.0, 1.0]]}
        state["collision_footprint"] = {"boxes": full_collision_boxes()}


def patch() -> dict[str, object]:
    contracts_path = LOGICAL / "contracts-v2.json"
    physical_path = LOGICAL / "physical-footprints.json"
    geometry_path = LOGICAL / "geometry.json"
    contracts = json.loads(contracts_path.read_text(encoding="utf-8"))
    physical = json.loads(physical_path.read_text(encoding="utf-8"))
    geometry = json.loads(geometry_path.read_text(encoding="utf-8"))
    family = next(row for row in contracts["families"] if row["id"] == WINDOW)
    patch_family(family)
    for key, state in family['states'].items():
        mask = physical['families'][WINDOW][key]
        if mask["cells"] != [[0, 0, 0], [0, 1, 0]]:
            raise ValueError(f"unexpected TEST2 window ownership for {key}: {mask['cells']}")
        mask["boxes"] = state['collision_footprint']['boxes']

    geometry["blocks"][WINDOW] = profile(family)
    contracts_path.write_text(json.dumps(contracts, separators=(",", ":"), sort_keys=True) + "\n", encoding="utf-8")
    physical_path.write_text(json.dumps(physical, separators=(",", ":"), sort_keys=True) + "\n", encoding="utf-8")
    geometry_path.write_text(json.dumps(geometry, separators=(",", ":"), sort_keys=True) + "\n", encoding="utf-8")
    return {"family": WINDOW, "states": len(family["states"]), "mesh_payload_changed": False,
            "render_offset": "-.75*facing horizontal, +.875Y", "physical_volume": "two full root/up cells"}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apply", action="store_true", help="write the bounded generated files")
    args = parser.parse_args()
    if not args.apply:
        raise SystemExit("refusing to write without --apply")
    print(json.dumps(patch(), ensure_ascii=False, sort_keys=True))


if __name__ == "__main__":
    main()
