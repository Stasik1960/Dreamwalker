"""Bounded Python diagnosis of the real-player diagonal passage regression.

Uses the production descriptor's 1/16 SAT occupancy and native axis-separated
AABB clipping. This diagnostic never substitutes for the native GameTests.
"""
from pathlib import Path
import copy
import json
import math
import traceback
from audit_door90986_city_cost import intersects

ROOT = Path(__file__).resolve().parents[1]
SPEC = ROOT / "src/architecture/resources/bloodborne_dw/composite/source_stairs_dark_prismarine_stairs_inner_right.json"
REPORT = ROOT / "build/door-90986-repair/passage-clearance.json"


def physical_rectangles(boxes, yaw):
    c, s = math.cos(yaw * math.pi / 4), math.sin(yaw * math.pi / 4)
    occupied = set()
    for box in boxes:
        # The test's feet .0625 and head1.8625 exclude the header.
        if box["from"][1] / 16 >= 1.8625 or box["to"][1] / 16 <= .0625:
            continue
        a, b = [[v / 16 for v in box[k]] for k in ("from", "to")]
        polygon = [(.5+c*(x-.5)-s*(z-.5), .5+s*(x-.5)+c*(z-.5))
                   for x, z in ((a[0],a[2]),(b[0],a[2]),(b[0],b[2]),(a[0],b[2]))]
        lo = [min(p[i] for p in polygon) for i in (0, 1)]
        hi = [max(p[i] for p in polygon) for i in (0, 1)]
        for iz in range(math.floor(lo[1]*16+1e-8), math.ceil(hi[1]*16-1e-8)):
            for ix in range(math.floor(lo[0]*16+1e-8), math.ceil(hi[0]*16-1e-8)):
                if yaw % 2 == 0 or intersects(polygon, ix/16, iz/16, 1/16):
                    occupied.add((ix, iz))
    # Union adjacent X voxels exactly; clipping either representation agrees.
    rectangles = []
    for z in sorted({z for _, z in occupied}):
        xs = sorted(x for x, zz in occupied if z == zz)
        first = previous = xs[0]
        for x in xs[1:]:
            if x != previous + 1:
                rectangles.append((first/16, z/16, (previous+1)/16, (z+1)/16))
                first = x
            previous = x
        rectangles.append((first/16, z/16, (previous+1)/16, (z+1)/16))
    return rectangles


def clipped(player, delta, axis, obstacle):
    # VoxelShape.calculateMaxDistance: separated axis is tested only when both
    # perpendicular intervals overlap, then clamp at the nearest forward face.
    other = 1-axis
    if player[other+2] <= obstacle[other]+1e-7 or player[other] >= obstacle[other+2]-1e-7:
        return delta
    if delta > 0 and player[axis+2] <= obstacle[axis]+1e-7:
        return min(delta, max(0, obstacle[axis]-player[axis+2]))
    if delta < 0 and player[axis] >= obstacle[axis+2]-1e-7:
        return max(delta, min(0, obstacle[axis+2]-player[axis]))
    return delta


def movement(boxes, yaw, direction, axis_order):
    c, s = math.cos(yaw*math.pi/4), math.sin(yaw*math.pi/4)
    normal = (-s*direction, c*direction)
    start = (.5-normal[0]*2, .5-normal[1]*2)
    end = (.5+normal[0]*2, .5+normal[1]*2)
    position = list(start)
    rectangles = physical_rectangles(boxes, yaw)
    contacts = []
    for i in range(32):
        player = [position[0]-.3, position[1]-.3, position[0]+.3, position[1]+.3]
        for axis in axis_order:
            wanted = normal[axis]/8
            accepted = wanted
            for obstacle in rectangles:
                accepted = clipped(player, accepted, axis, obstacle)
            if abs(wanted-accepted) > 1e-6:
                contacts.append({"step":i,"axis":"xz"[axis],"requested":wanted,"accepted":accepted})
            position[axis] += accepted
            player[axis] += accepted
            player[axis+2] += accepted
    error = sum((position[i]-end[i])**2 for i in range(2))
    return {"yaw":yaw,"direction":direction,"axisOrder":"".join("xz"[i] for i in axis_order),
            "pass":not contacts and error < 1e-8,"contacts":contacts,"actual":position,"expected":end}


def evaluate(boxes):
    rows = [movement(boxes, yaw, direction, order) for yaw in range(8)
            for direction in (1,-1) for order in ((0,1),(1,0))]
    return {"allPass":all(row["pass"] for row in rows),"failures":[r for r in rows if not r["pass"]],"cases":len(rows)}


def candidate(boxes, left, right, leaf_left):
    changed = copy.deepcopy(boxes)
    changed[0]["to"][0] = -left
    changed[1]["from"][0] = 16+right
    changed[5]["to"][0] = -leaf_left
    return changed


def main():
    document=json.loads(SPEC.read_text(encoding="utf8"))
    boxes=document["variants"][0]["poses"]["open"]["collision"]
    rows=[]
    for left,right,leaf in ((0,0,0),(0,1,0),(0,2,0),(1,1,0),(2,2,0),(1,1,1),(1,2,1),(2,1,2),(2,2,2)):
        result=evaluate(candidate(boxes,left,right,leaf))
        rows.append({"leftFixedClearanceUnits":left,"rightFixedClearanceUnits":right,
                     "openLeafTipClearanceUnits":leaf,**result})
    REPORT.parent.mkdir(parents=True,exist_ok=True)
    REPORT.write_text(json.dumps({"status":"PYTHON_DIAGNOSTIC_PENDING_NATIVE_CONFIRMATION",
        "sourceDescriptorSha256":__import__("hashlib").sha256(SPEC.read_bytes()).hexdigest(),
        "scope":"No JVM, geometry, PNG, source preparation or native test movement changed. Both X/Z orders and directions use identical 32 steps and 0.6 player width.",
        "candidates":rows},indent=2)+"\n",encoding="utf8")


if __name__=="__main__":
    try:main()
    except BaseException:
        REPORT.parent.mkdir(parents=True,exist_ok=True)
        REPORT.write_text(json.dumps({"status":"FAIL","error":traceback.format_exc()},indent=2)+"\n",encoding="utf8")
        raise
