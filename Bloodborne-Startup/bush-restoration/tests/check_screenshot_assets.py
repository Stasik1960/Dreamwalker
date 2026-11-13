"""Check the packaged resources for the seven screenshot cases."""

import argparse
from copy import deepcopy
from pathlib import Path
import sys
from zipfile import ZipFile

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from generate_form_assets import read
from repair_screenshot_assets import (
    ASSETS, BALUSTRADE, COLLISION_125, COLLISION_127, COMPOSITES, GLASS,
    MODELS, PILASTER, PILASTER_DROP, SOURCE_MODELS, WALL, WALL_POSE,
)


def rows(table, source, pose=0):
    shape = table["objects"]["bloodborne_dw:" + source][pose]["closedBase"]
    return table["shapes"][shape]


def check_collision(base, patched):
    original = read(base, COLLISION_125)
    fitted = read(patched, COLLISION_125)
    assert fitted["shapes"][:len(original["shapes"])] == original["shapes"]
    changed = {name for name, value in original["objects"].items() if fitted["objects"][name] != value}
    assert changed == {"bloodborne_dw:" + source for source in (GLASS, BALUSTRADE, PILASTER)}
    for pose in (0, 1):
        for row in rows(fitted, GLASS, pose):
            assert all(b["from"][1] == 0 and b["to"][1] == 16
                       for b in row["collision"] + row["aim"])
    for previous, next_shape in zip(rows(original, BALUSTRADE), rows(fitted, BALUSTRADE), strict=True):
        assert next_shape["aim"] == previous["aim"]
        assert len(next_shape["collision"]) <= fitted["maxBoxesPerOrientation"]
        assert any(b["from"][1] == 12 and b["to"][1] == 29 for b in next_shape["collision"])
        assert any(b["from"][1] == 29 and b["to"][1] == 32 for b in next_shape["collision"])

    old_hidden = read(base, COLLISION_127)["objects"]
    new_hidden = read(patched, COLLISION_127)["objects"]
    changed = [after for before, after in zip(old_hidden, new_hidden, strict=True) if before != after]
    wall, = changed
    assert wall["registryId"] == "bloodborne_dw:" + WALL and wall["variant"] == WALL_POSE
    assert all(row["collision"] for row in wall["rotations"])
    assert wall["rotations"][0]["collision"] == [{"from": [5, 0, 0], "to": [11, 16, 16]}]

    for previous, next_shape in zip(rows(original, PILASTER), rows(fitted, PILASTER), strict=True):
        for key in ("collision", "aim"):
            for before, after in zip(previous[key], next_shape[key], strict=True):
                expected = deepcopy(before)
                expected["from"][1] = max(0, expected["from"][1] - PILASTER_DROP)
                expected["to"][1] -= PILASTER_DROP
                assert expected == after


def check_models(base, patched):
    path = COMPOSITES + PILASTER + ".json"
    pilaster = read(patched, path)
    pose, = pilaster["variants"]
    part, = pose["poses"]["closed"]["parts"]
    assert part["offset"] == [0, 16, 0]
    model = SOURCE_MODELS + "dark_oak_trapdoor_open.json"
    body = read(patched, model)["elements"]
    assert len(body) == len(read(base, model)["elements"]) == 7
    assert min(p["from"][1] for p in body) + part["offset"][1] == 0
    assert max(p["to"][1] for p in body) + part["offset"][1] == 44
    assert pose["visibleBounds"]["to"][1] == 44

    for path, y in ((SOURCE_MODELS + "bir_door_2.json", 0),
                    (MODELS + "v126/doors/93152/static_0_1_0.json", 16)):
        doc = read(patched, path)
        panes = [element for element in doc["elements"]
                 if any(doc["textures"].get(face["texture"][1:], "").endswith("/window_3")
                        for face in element["faces"].values())]
        assert len(panes) == 4
        for side in ("north", "south"):
            spans = [(e["from"][0], e["to"][0], e["from"][1], e["to"][1])
                     for e in panes if side in e["faces"]]
            assert spans == [(-5, 6, -12 + y, y), (10, 21, -12 + y, y)]

    for name in ("catalog.json", "meshes.bin", "visual_physics.json"):
        path = ASSETS + "native_grid/" + name
        assert base.getinfo(path).CRC == patched.getinfo(path).CRC


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("bush_restoration_base", type=Path)
    parser.add_argument("patched_distinct_forms", type=Path)
    args = parser.parse_args()
    with ZipFile(args.bush_restoration_base) as base, ZipFile(args.patched_distinct_forms) as patched:
        check_collision(base, patched)
        check_models(base, patched)
    print("PASS screenshot assets: glass, balustrade, hidden wall, pilaster, and door aperture")


if __name__ == "__main__":
    main()
