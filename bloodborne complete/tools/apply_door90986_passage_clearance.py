"""Apply the reviewed collision-only90986 clearance without regenerating art."""
from pathlib import Path
import copy
import hashlib
import json
from repair_single_door_90986 import ROOT, RES, SPEC_PATH, OUT, write


def main():
    model_files = [RES / "assets/bloodborne_dw/models/remaining/final_followups" / name
                   for name in ("door_90986_frame.json", "door_90986_leaf.json")]
    before = {str(path.relative_to(ROOT)): hashlib.sha256(path.read_bytes()).hexdigest()
              for path in model_files}
    spec = json.loads(SPEC_PATH.read_text(encoding="utf8"))
    assert spec["id"] == "bloodborne_dw:source_stairs_dark_prismarine_stairs_inner_right"
    poses = spec["variants"][0]["poses"]
    closed_panel = copy.deepcopy(poses["closed"]["collision"][5])
    assert closed_panel == {"from": [0,0,2], "to": [16,34,14]}
    for pose_name, pose in poses.items():
        assert pose_name in ("closed", "open")
        for field in ("collision", "selection"):
            boxes = pose[field]
            assert boxes[0]["to"][0] in (0,-1) and boxes[1]["from"][0] in (16,17)
            boxes[0]["to"][0] = -1
            boxes[1]["from"][0] = 17
            if pose_name == "open":
                assert boxes[5]["from"] == [-12,0,2] and boxes[5]["to"] in ([0,34,18],[-1,34,18])
                boxes[5]["to"][0] = -1
    assert poses["closed"]["collision"][5] == closed_panel
    assert poses["closed"]["collision"][:5] == poses["open"]["collision"][:5]
    assert all(p["collision"] == p["selection"] for p in poses.values())
    spec["collisionPolicy"] = "User90986: one central hinged door leaf, stationary masonry/side windows/upper arch, one-model-unit aperture clearance for conservative diagonal raster and ordinary axis-separated player steps. CLOSED wooden panel retains full x0..16 aperture and original z2/z14 faces. Selection equals collision at all eight instance yaws; source closed model and placement preserved."
    write(SPEC_PATH, spec)
    assert before == {str(path.relative_to(ROOT)): hashlib.sha256(path.read_bytes()).hexdigest()
                      for path in model_files}
    proof_path = OUT / "source-partition-proof.json"
    proof = json.loads(proof_path.read_text(encoding="utf8"))
    proof["fixedPhysicsBoxes"] = poses["closed"]["collision"][:5]
    proof["closedPanel"] = poses["closed"]["collision"][5]
    proof["openPanel"] = poses["open"]["collision"][5]
    proof["physicalApertureClearanceUnits"] = 1
    proof["physicalClearanceReason"] = "Native r2 yaw1 regression reproduced by SAT1/16 plus vanilla X/Z clipping. A single physical model unit on both fixed inner edges and OPEN leaf tip is the minimum passing all8yaws, both directions and both axis orders. CLOSED wood, models, UVs, PNGs and the32-step native passage test are unchanged."
    proof["derivedCapModelSha256UnchangedByCollisionRepair"] = before
    write(proof_path, proof)


if __name__ == "__main__":
    main()
