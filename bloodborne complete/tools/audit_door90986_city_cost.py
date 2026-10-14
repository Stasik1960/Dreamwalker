"""Read-only owner-frequency/footprint cost of90986 against existing SQL usage.

No original-world scan, weighted selection, preparation rewrite or JVM is used.
Counts are owner contribution cells, not unique helpers or retained heap bytes.
"""
from pathlib import Path
import collections
import hashlib
import json
import math
import sqlite3
import traceback
import zipfile
from world_io import read_nbt, compound, decode_nbt
from verify_v1271_final_readiness import ordinary

ROOT = Path(__file__).resolve().parents[1]
REGISTRY = "bloodborne_dw:source_stairs_dark_prismarine_stairs_inner_right"
PREP = ROOT / "build/v1271-city-preparation"
OLD_JAR = Path(r"C:\Users\vakir\.codex\worktrees\3f07\DW\releases\Dreamwalker-BB\v12.7.3-final-checks\dreamwalker-bb-fabric-1.20.1-0.1.0-v12.7.3-final-checks.jar")
SPEC = ROOT / "src/architecture/resources/bloodborne_dw/composite/source_stairs_dark_prismarine_stairs_inner_right.json"
REPORT = ROOT / "build/door-90986-repair/city-cost.json"


def sha(path):
    h = hashlib.sha256()
    with path.open("rb") as f:
        for b in iter(lambda: f.read(1 << 20), b""):
            h.update(b)
    return h.hexdigest()


def cells_for_aabb(a, b):
    return {(x, y, z) for x in range(math.floor(a[0]+1e-8), math.ceil(b[0]-1e-8))
            for y in range(math.floor(a[1]+1e-8), math.ceil(b[1]-1e-8))
            for z in range(math.floor(a[2]+1e-8), math.ceil(b[2]-1e-8))}


def rotated_shift(local, yaw):
    c, s = math.cos(yaw*math.pi/4), math.sin(yaw*math.pi/4)
    return [local[0]*c-local[2]*s, local[1], local[0]*s+local[2]*c]


def intersects(polygon, x, z, step):
    rect = ((x,z),(x+step,z),(x+step,z+step),(x,z+step))
    axes = [(1,0),(0,1)]+[(-(polygon[(i+1)%4][1]-p[1]),polygon[(i+1)%4][0]-p[0]) for i,p in enumerate(polygon)]
    for ax, az in axes:
        p = [a*ax+b*az for a,b in polygon]
        q = [a*ax+b*az for a,b in rect]
        if max(p) <= min(q)+1e-9 or max(q) <= min(p)+1e-9:
            return False
    return True


def new_cells(boxes, yaw, local=(0,0,0), mount=0):
    result = {(0,0,0)}
    c, s = math.cos(yaw*math.pi/4), math.sin(yaw*math.pi/4)
    shift = rotated_shift(local,yaw)
    for box in boxes:
        a, b = [[v/16 for v in box[k]] for k in ("from","to")]
        polygon = [(.5+c*(x-.5)-s*(z-.5),.5+s*(x-.5)+c*(z-.5)) for x,z in ((a[0],a[2]),(b[0],a[2]),(b[0],b[2]),(a[0],b[2]))]
        x0,z0 = [min(p[i] for p in polygon) for i in (0,1)]
        x1,z1 = [max(p[i] for p in polygon) for i in (0,1)]
        if yaw%2==0:
            result |= cells_for_aabb((x0+shift[0],a[1]+mount+shift[1],z0+shift[2]),(x1+shift[0],b[1]+mount+shift[1],z1+shift[2]))
        else:
            grid=16
            for iz in range(math.floor(z0*grid),math.ceil(z1*grid)):
                for ix in range(math.floor(x0*grid),math.ceil(x1*grid)):
                    if intersects(polygon,ix/grid,iz/grid,1/grid):
                        result |= cells_for_aabb((ix/grid+shift[0],a[1]+mount+shift[1],iz/grid+shift[2]),((ix+1)/grid+shift[0],b[1]+mount+shift[1],(iz+1)/grid+shift[2]))
    return result


def old_rotations(archive):
    full=json.loads(archive.read("bloodborne_dw/imported-full-map-v127.json"))
    found=[r for r in full["objects"] if r["registryId"]==REGISTRY and r["variant"]==0]
    if found:
        assert len(found)==1
        return found[0]["rotations"],"ImportedFullMapCollisionV127"
    for version in ("v126","v125"):
        doc=json.loads(archive.read("bloodborne_dw/imported-collision-"+version+".json"))
        if REGISTRY in doc["objects"]:
            return doc["shapes"][doc["objects"][REGISTRY][0]["closedBase"]],"ImportedCollision"+version.upper()
    raise AssertionError("No previous exact collision provider")


def old_cells(rotations,yaw,local=(0,0,0),mount=0):
    shift=rotated_shift(local,yaw)
    rows=rotations[yaw]["collision"]
    boxes=[([r["from"][i]/16+shift[i]+(mount if i==1 else 0) for i in range(3)],
            [r["to"][i]/16+shift[i]+(mount if i==1 else 0) for i in range(3)]) for r in rows]
    if boxes:
        lo=[min(a[i] for a,b in boxes) for i in range(3)]
        hi=[max(b[i] for a,b in boxes) for i in range(3)]
        lo=[0 if -.5<v<0 else v for v in lo]
        hi=[1 if 1<v<1.5 else v for v in hi]
        boxes=[([max(a[i],lo[i]) for i in range(3)],[min(b[i],hi[i]) for i in range(3)]) for a,b in boxes]
    result={(0,0,0)}
    for a,b in boxes:
        result |= cells_for_aabb(a,b)
    return result


def main():
    db=ordinary(PREP/"source-plan.sqlite");usage_path=ordinary(PREP/"decision-frequency-proof.json")
    before=sha(db);usage=json.loads(usage_path.read_text(encoding="utf8"));assert usage["sourcePlanSha256AtAudit"]==before
    frequencies=usage["decisionCellCounts"]
    connection=sqlite3.connect(f"file:{db.absolute()}?mode=ro",uri=True)
    try:
        decisions=[(i,json.loads(data)) for i,data in connection.execute("SELECT id,data FROM decisions")]
    finally:
        connection.close()
    owners=[(i,r,int(frequencies.get(str(i),0))) for i,r in decisions if r.get("registryId")==REGISTRY and r.get("action")=="OWNER" and int(frequencies.get(str(i),0))]
    spec=json.loads(SPEC.read_text(encoding="utf8"));poses=spec["variants"][0]["poses"]
    with zipfile.ZipFile(OLD_JAR) as archive:
        rotations,provider=old_rotations(archive)
    fixture=read_nbt(ROOT/"src/gametest/resources/bloodborne_dw/door90986-v1273-saved-owner.nbt")
    payload=compound(compound(fixture.root)["rootBlockEntity"])["payload"]
    fields=compound(payload)
    gallery_shift=[tag.value for tag in fields["SourceShift"].value]
    gallery_mount=fields.get("VerticalOffset")
    gallery_mount=gallery_mount.value if gallery_mount else 0
    assert len(old_cells(rotations,0,gallery_shift,gallery_mount))==24,"Pure old-provider cell count must reproduce the actual native .3 fixture"
    all_yaws=[]
    for yaw in range(8):
        old=len(old_cells(rotations,yaw));closed=len(new_cells(poses["closed"]["collision"],yaw));opened=len(new_cells(poses["open"]["collision"],yaw))
        all_yaws.append({"yaw":yaw,"degrees":45*yaw,"previousV1273CellsZeroShift":old,"closedCellsZeroShift":closed,"openCellsZeroShift":opened,
            "closedDelta":closed-old,"openDelta":opened-old,
            "previousGalleryAnchorCells":len(old_cells(rotations,yaw,gallery_shift,gallery_mount)),
            "closedGalleryAnchorCells":len(new_cells(poses["closed"]["collision"],yaw,gallery_shift,gallery_mount)),
            "openGalleryAnchorCells":len(new_cells(poses["open"]["collision"],yaw,gallery_shift,gallery_mount))})
    rows=[];total_old=total_new=0
    for index,row,count in owners:
        yaw=int(row["properties"].get("rotation",0));p=row.get("payload",{});shift=row.get("sourceShift",p.get("SourceShift",[0,0,0]));mount=row.get("mountY",p.get("MountY",0))+p.get("VerticalOffset",0)
        previous=len(old_cells(rotations,yaw,shift,mount));current=len(new_cells(poses["closed"]["collision"],yaw,shift,mount))
        total_old+=count*previous;total_new+=count*current
        rows.append({"decisionId":index,"sourceState":row["sourceState"],"owners":count,"yaw":yaw,"sourceShift":shift,"mount":mount,"previousContributionCellsPerOwner":previous,"currentContributionCellsPerOwner":current})
    assert sha(db)==before
    report={"status":"PASS_READ_ONLY_PREPARED_SQL_AND_EXACT_FOOTPRINT_COST","registryId":REGISTRY,"ownerFrequency":sum(n for _,_,n in owners),"ownerDecisions":rows,
        "oldCollisionProvider":provider,"actualOldGalleryFixtureCells":24,"gallerySourceShift":gallery_shift,"galleryMount":gallery_mount,
        "eightYawFootprints":all_yaws,"weightedPreviousOwnerContributionCells":total_old,"weightedCurrentOwnerContributionCells":total_new,
        "weightedAdditionalOwnerContributionCells":total_new-total_old,
        "scope":"Exact owner contribution membership estimate. Shared cells, native carriers and helper coalescing prevent treating this as a unique BE count or heap-byte estimate. No native FPS/heap inference.",
        "nativeFixtureCrossCheckPassed":True,"sourcePlanSha256":before,"sourcePlanUnchanged":True,"sourceWorldScanned":False,"weightedChoicesRecomputed":False,"jvmStarted":False}
    REPORT.parent.mkdir(parents=True,exist_ok=True);REPORT.write_text(json.dumps(report,ensure_ascii=False,indent=2)+"\n",encoding="utf8")


if __name__=="__main__":
    try:main()
    except BaseException:
        REPORT.parent.mkdir(parents=True,exist_ok=True);REPORT.write_text(json.dumps({"status":"FAIL","error":traceback.format_exc()},indent=2)+"\n",encoding="utf8");raise
