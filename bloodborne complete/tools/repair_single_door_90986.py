"""Give the specifically marked central leaf of 90986 an ordinary door action.

The closed scene keeps the original model reference.  OPEN partitions only the
lower central panel; all side windows, stonework and upper arch remain fixed.
The partitions use the pre-v12.7.3 source faces so automatically added overlapping
caps cannot enter these new models.  Texture PNGs are never changed.
"""
from pathlib import Path
from fractions import Fraction
import copy
import hashlib
import json
import subprocess

ROOT = Path(__file__).resolve().parents[1]
REPO = ROOT.parent
RES = ROOT / "src/architecture/resources"
MODEL_PATH = "assets/bloodborne_dw/models/remaining/source/minecraft/block/stairs/dark_prismarine_stairs_inner_right.json"
SOURCE_REF = "57c22e2a"
SOURCE_MODEL = "bloodborne_dw:remaining/source/minecraft/block/stairs/dark_prismarine_stairs_inner_right"
FRAME_MODEL = "bloodborne_dw:remaining/final_followups/door_90986_frame"
LEAF_MODEL = "bloodborne_dw:remaining/final_followups/door_90986_leaf"
SPEC_PATH = RES / "bloodborne_dw/composite/source_stairs_dark_prismarine_stairs_inner_right.json"
OUT = ROOT / "build/door-90986-repair"


def write(path, document):
    path.parent.mkdir(parents=True, exist_ok=True)
    old = path.read_bytes() if path.exists() else b""
    text = json.dumps(document, ensure_ascii=False, indent=2) + "\n"
    if b"\r\n" in old:
        text = text.replace("\n", "\r\n")
    path.write_bytes(text.encode("utf8"))


def main():
    relative = "bloodborne complete/src/architecture/resources/" + MODEL_PATH
    original_bytes = subprocess.check_output(["git", "show", SOURCE_REF + ":" + relative], cwd=REPO)
    original = json.loads(original_bytes)
    spec = json.loads(SPEC_PATH.read_text(encoding="utf8"))
    assert spec["id"] == "bloodborne_dw:source_stairs_dark_prismarine_stairs_inner_right"
    assert len(spec["variants"]) == 1 and len(original["elements"]) == 75
    variant = spec["variants"][0]
    closed_source = variant["poses"]["closed"]["parts"]
    assert closed_source == [{"model": SOURCE_MODEL, "offset": [0, 16.0, 0]}]
    # The red selection in the user's photograph follows atlas columns14..34
    # of the48-column lower panel. Convert the edges by the exact source UV map.
    left = float(Fraction(11, 12))
    right = float(Fraction(181, 12))
    frame = copy.deepcopy(original)
    leaf = copy.deepcopy(original)
    frame.pop("groups", None)
    leaf.pop("groups", None)
    frame["elements"] = []
    leaf["elements"] = []
    proof = []

    def clip(element, low, high):
        item = copy.deepcopy(element)
        old_low, old_high = element["from"][0], element["to"][0]
        item["from"][0], item["to"][0] = low, high
        assert element["rotation"]["angle"] == 0
        for side, face in item["faces"].items():
            assert side in ("north", "south") and not face.get("rotation", 0)
            u0, v0, u1, v1 = element["faces"][side]["uv"]
            if side == "north":
                a, b = (old_high - high) / (old_high - old_low), (old_high - low) / (old_high - old_low)
            else:
                a, b = (low - old_low) / (old_high - old_low), (high - old_low) / (old_high - old_low)
            face["uv"] = [u0 + (u1 - u0) * a, v0, u0 + (u1 - u0) * b, v1]
        return item

    for index, element in enumerate(original["elements"]):
        if index not in (0, 3):
            frame["elements"].append(copy.deepcopy(element))
            continue
        assert element["from"] == [-9, -16, 2]
        assert element["to"] == [25, 18 if index == 0 else 6, 14]
        assert set(element["faces"]) == {"north", "south"}
        sides = [clip(element, -9, left), clip(element, right, 25)]
        central = clip(element, left, right)
        frame["elements"].extend(sides)
        leaf["elements"].append(central)
        # Affine partitions tile the old surface exactly, with no texture
        # repainting, scale change, additional translation or omitted interval.
        for side in ("north", "south"):
            old_face = element["faces"][side]
            pieces = sides + [central]
            uv_intervals = sorted((p["faces"][side]["uv"][0], p["faces"][side]["uv"][2]) for p in pieces)
            assert abs(uv_intervals[0][0] - old_face["uv"][0]) < 1e-12
            assert abs(uv_intervals[-1][1] - old_face["uv"][2]) < 1e-12
            assert all(abs(uv_intervals[n][1] - uv_intervals[n+1][0]) < 1e-12 for n in range(2))
        proof.append({"sourceElement": index, "sourceBounds": [element["from"], element["to"]],
                      "leafBounds": [central["from"], central["to"]], "oldFacesAndUvTiledExactly": True})

    # Only the moving solid wooden leaf needs its newly exposed narrow edges.
    # Sample its own border pixels; the overlapping source overlay is retained
    # with its two old faces and receives no duplicate caps.
    panel = leaf["elements"][0]
    texture = panel["faces"]["north"]["texture"]
    for side, uv in {"west": [1.75, 10, 1.875, 16], "east": [4.125, 10, 4.25, 16],
                     "up": [1.75, 10, 4.25, 10.125], "down": [1.75, 15.875, 4.25, 16]}.items():
        panel["faces"][side] = {"uv": uv, "texture": texture}
    write(RES / "assets/bloodborne_dw/models/remaining/final_followups/door_90986_frame.json", frame)
    write(RES / "assets/bloodborne_dw/models/remaining/final_followups/door_90986_leaf.json", leaf)

    # A one-model-unit physical clearance on both sides keeps the real0.6
    # player free through the conservative1/16 diagonal raster and ordinary
    # axis-separated walking steps. The same fixed frame serves both poses.
    # CLOSED still spans the entire ordinary aperture and both wood planes.
    fixed = [
        {"from": [-16, 0, -3], "to": [-1, 51, 19]},
        {"from": [17, 0, -3], "to": [32, 51, 19]},
        {"from": [0, 34, -3], "to": [16, 51, 19]},
        {"from": [-36, 0, -9.5], "to": [-16, 48.34823587, 25.5]},
        {"from": [32, 0, -9.5], "to": [52, 48.34823587, 25.5]},
    ]
    closed_panel = {"from": [0, 0, 2], "to": [16, 34, 14]}
    open_panel = {"from": [-12, 0, 2], "to": [-1, 34, 18]}
    closed_geometry = copy.deepcopy(fixed) + [closed_panel]
    open_geometry = copy.deepcopy(fixed) + [open_panel]
    variant["poses"]["closed"] = {"parts": closed_source, "collision": closed_geometry,
                                   "selection": copy.deepcopy(closed_geometry)}
    # Source elements0/3 share coplanar faces. Preserve their source ordering
    # within the moving leaf; fixed side strips cannot overlap those faces.
    variant["poses"]["open"] = {
        "parts": [{"model": LEAF_MODEL, "offset": [0, 16.0, 0], "yaw": 90, "pivot": [left, 0, 2]},
                  {"model": FRAME_MODEL, "offset": [0, 16.0, 0]}],
        "collision": open_geometry, "selection": copy.deepcopy(open_geometry),
    }
    spec["openable"] = True
    spec["functionalDoorPhysics"] = True
    spec["simplePhysicalCollision"] = False
    spec["collisionPolicy"] = "User90986: one central hinged door leaf, stationary masonry/side windows/upper arch, one-model-unit aperture clearance for conservative diagonal raster and ordinary axis-separated player steps. CLOSED wooden panel retains full x0..16 aperture and original z2/z14 faces. Selection equals collision at all eight instance yaws; source closed model and placement preserved."
    write(SPEC_PATH, spec)
    report = {"status": "PASS_SOURCE_PARTITIONS_PENDING_NATIVE_DOOR_TEST", "numericId": "90986",
              "registryId": spec["id"], "sourceReference": SOURCE_REF,
              "sourceModelSha256": hashlib.sha256(original_bytes).hexdigest(),
              "closedSourceModelReferenceAndOffsetUnchanged": True,
              "originalModelAndAllPngUntouchedByThisTool": True,
              "movingLeafSourceElements": [0, 3], "stationarySourceElements": 73,
              "fixedLowerSideStrips": 4, "newLeafEdgeFaces": ["west", "east", "up", "down"],
              "partitions": proof, "visualHingeSourcePivot": [left, 0, 2], "leafYaw": 90,
              "physicalApertureClearanceUnits": 1,
              "physicalClearanceReason": "Native r2 yaw1 regression reproduced by SAT1/16 plus vanilla X/Z clipping. A single physical model unit on both fixed inner edges and OPEN leaf tip is the minimum passing all8yaws, both directions and both axis orders. CLOSED wood, models, UVs, PNGs and the32-step native passage test are unchanged.",
              "fixedPhysicsBoxes": fixed, "closedPanel": closed_panel, "openPanel": open_panel}
    write(OUT / "source-partition-proof.json", report)
    print(json.dumps({k: v for k, v in report.items() if k != "partitions"}, ensure_ascii=False))


if __name__ == "__main__":
    main()
